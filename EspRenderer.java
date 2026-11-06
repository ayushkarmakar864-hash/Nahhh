package com.donutbasefinder.esp;

import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;

import com.donutbasefinder.DonutBaseFinder;
import com.donutbasefinder.config.DonutConfig;

/**
 * Draws wireframe boxes through walls.
 *
 * <p>In 1.21.11 depth testing is not toggled with a global RenderSystem call any more; it is a
 * property of the {@link RenderPipeline}. So this class registers its own pipeline, derived from
 * vanilla's debug-filled-box pipeline, with {@link DepthTestFunction#NO_DEPTH_TEST}. That is the
 * pattern from Fabric's "Rendering in the World" documentation for 1.21.11, including the
 * buffer upload and draw code below.
 *
 * <p>A "wireframe" is built from 12 thin boxes (one per edge) in a single quad buffer, so line
 * width is exact and does not depend on driver support for wide lines.
 */
public final class EspRenderer {
	private static final RenderPipeline ESP_PIPELINE = RenderPipelines.register(
			RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
					.withLocation(Identifier.fromNamespaceAndPath(DonutBaseFinder.MOD_ID, "pipeline/esp_through_walls"))
					.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
					.build()
	);

	/** Smallest half-thickness of an edge, in blocks. */
	private static final double MIN_HALF_THICKNESS = 0.012;
	/**
	 * Half-thickness per block of distance at line scale 1.0. Growing the edges with distance keeps
	 * them about one screen pixel wide instead of vanishing when far away.
	 */
	private static final double HALF_THICKNESS_PER_BLOCK = 0.0006;
	private static final float FILL_ALPHA = 0.14f;

	private static final ByteBufferBuilder ALLOCATOR = new ByteBufferBuilder(RenderType.SMALL_BUFFER_SIZE);
	private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
	private static final Vector3f MODEL_OFFSET = new Vector3f();
	private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();

	private static BufferBuilder buffer;
	private static MappableRingBuffer vertexBuffer;
	private static boolean errorLogged = false;

	private EspRenderer() {
	}

	public static void register() {
		WorldRenderEvents.BEFORE_TRANSLUCENT.register(EspRenderer::onBeforeTranslucent);
	}

	// ------------------------------------------------------------------------------------------
	// Extraction: write the boxes into the buffer
	// ------------------------------------------------------------------------------------------

	private static void onBeforeTranslucent(WorldRenderContext context) {
		if (!DonutConfig.espEnabled) {
			return;
		}

		List<BlockScanner.Entry> entries = BlockScanner.snapshot();
		if (entries.isEmpty()) {
			return;
		}

		PoseStack matrices = context.matrices();
		if (matrices == null) {
			return;
		}

		Vec3 camera = context.worldState().cameraRenderState.pos;
		Matrix4fc pose = matrices.last().pose();
		double rangeSq = (double) DonutConfig.rangeBlocks * DonutConfig.rangeBlocks;

		try {
			for (BlockScanner.Entry entry : entries) {
				EspTarget target = entry.target();
				if (!target.isEnabled()) {
					continue;
				}

				BlockPos pos = entry.pos();
				double dx = pos.getX() + 0.5 - camera.x;
				double dy = pos.getY() + 0.5 - camera.y;
				double dz = pos.getZ() + 0.5 - camera.z;
				double distSq = dx * dx + dy * dy + dz * dz;

				if (distSq > rangeSq) {
					continue;
				}

				addWireBox(pose, pos, camera, target, Math.sqrt(distSq));
			}

			// buildOrThrow() fails on an empty buffer, so only draw if something was added.
			if (buffer != null) {
				drawBuffer(Minecraft.getInstance(), ESP_PIPELINE);
			}
		} catch (RuntimeException e) {
			buffer = null;
			if (!errorLogged) {
				errorLogged = true;
				DonutBaseFinder.LOGGER.error("ESP render failed (further errors suppressed)", e);
			}
		}
	}

	private static void addWireBox(Matrix4fc pose, BlockPos pos, Vec3 camera, EspTarget target, double distance) {
		if (buffer == null) {
			buffer = new BufferBuilder(ALLOCATOR, ESP_PIPELINE.getVertexFormatMode(), ESP_PIPELINE.getVertexFormat());
		}

		// Camera-relative coordinates computed in double precision, then narrowed to float. This
		// avoids the jitter you get from large absolute coordinates far from the origin.
		float x0 = (float) (pos.getX() + target.inset - camera.x);
		float y0 = (float) (pos.getY() - camera.y);
		float z0 = (float) (pos.getZ() + target.inset - camera.z);
		float x1 = (float) (pos.getX() + 1.0 - target.inset - camera.x);
		float y1 = (float) (pos.getY() + target.height - camera.y);
		float z1 = (float) (pos.getZ() + 1.0 - target.inset - camera.z);

		float h = (float) Math.max(MIN_HALF_THICKNESS, distance * HALF_THICKNESS_PER_BLOCK * DonutConfig.lineScale);

		float r = target.red;
		float g = target.green;
		float b = target.blue;

		// Four edges parallel to X.
		renderFilledBox(pose, buffer, x0 - h, y0 - h, z0 - h, x1 + h, y0 + h, z0 + h, r, g, b, 1f);
		renderFilledBox(pose, buffer, x0 - h, y0 - h, z1 - h, x1 + h, y0 + h, z1 + h, r, g, b, 1f);
		renderFilledBox(pose, buffer, x0 - h, y1 - h, z0 - h, x1 + h, y1 + h, z0 + h, r, g, b, 1f);
		renderFilledBox(pose, buffer, x0 - h, y1 - h, z1 - h, x1 + h, y1 + h, z1 + h, r, g, b, 1f);

		// Four edges parallel to Y.
		renderFilledBox(pose, buffer, x0 - h, y0 - h, z0 - h, x0 + h, y1 + h, z0 + h, r, g, b, 1f);
		renderFilledBox(pose, buffer, x1 - h, y0 - h, z0 - h, x1 + h, y1 + h, z0 + h, r, g, b, 1f);
		renderFilledBox(pose, buffer, x0 - h, y0 - h, z1 - h, x0 + h, y1 + h, z1 + h, r, g, b, 1f);
		renderFilledBox(pose, buffer, x1 - h, y0 - h, z1 - h, x1 + h, y1 + h, z1 + h, r, g, b, 1f);

		// Four edges parallel to Z.
		renderFilledBox(pose, buffer, x0 - h, y0 - h, z0 - h, x0 + h, y0 + h, z1 + h, r, g, b, 1f);
		renderFilledBox(pose, buffer, x1 - h, y0 - h, z0 - h, x1 + h, y0 + h, z1 + h, r, g, b, 1f);
		renderFilledBox(pose, buffer, x0 - h, y1 - h, z0 - h, x0 + h, y1 + h, z1 + h, r, g, b, 1f);
		renderFilledBox(pose, buffer, x1 - h, y1 - h, z0 - h, x1 + h, y1 + h, z1 + h, r, g, b, 1f);

		if (DonutConfig.fillBoxes) {
			renderFilledBox(pose, buffer, x0, y0, z0, x1, y1, z1, r, g, b, FILL_ALPHA);
		}
	}

	/** Same box writer as Fabric's documentation example: six faces, four vertices each. */
	private static void renderFilledBox(Matrix4fc positionMatrix, BufferBuilder buffer, float minX, float minY, float minZ, float maxX, float maxY, float maxZ, float red, float green, float blue, float alpha) {
		// Front Face
		buffer.addVertex(positionMatrix, minX, minY, maxZ).setColor(red, green, blue, alpha);
		buffer.addVertex(positionMatrix, maxX, minY, maxZ).setColor(red, green, blue, alpha);
		buffer.addVertex(positionMatrix, maxX, maxY, maxZ).setColor(red, green, blue, alpha);
		buffer.addVertex(positionMatrix, minX, maxY, maxZ).setColor(red, green, blue, alpha);

		// Back face
		buffer.addVertex(positionMatrix, maxX, minY, minZ).setColor(red, green, blue, alpha);
		buffer.addVertex(positionMatrix, minX, minY, minZ).setColor(red, green, blue, alpha);
		buffer.addVertex(positionMatrix, minX, maxY, minZ).setColor(red, green, blue, alpha);
		buffer.addVertex(positionMatrix, maxX, maxY, minZ).setColor(red, green, blue, alpha);

		// Left face
		buffer.addVertex(positionMatrix, minX, minY, minZ).setColor(red, green, blue, alpha);
		buffer.addVertex(positionMatrix, minX, minY, maxZ).setColor(red, green, blue, alpha);
		buffer.addVertex(positionMatrix, minX, maxY, maxZ).setColor(red, green, blue, alpha);
		buffer.addVertex(positionMatrix, minX, maxY, minZ).setColor(red, green, blue, alpha);

		// Right face
		buffer.addVertex(positionMatrix, maxX, minY, maxZ).setColor(red, green, blue, alpha);
		buffer.addVertex(positionMatrix, maxX, minY, minZ).setColor(red, green, blue, alpha);
		buffer.addVertex(positionMatrix, maxX, maxY, minZ).setColor(red, green, blue, alpha);
		buffer.addVertex(positionMatrix, maxX, maxY, maxZ).setColor(red, green, blue, alpha);

		// Top face
		buffer.addVertex(positionMatrix, minX, maxY, maxZ).setColor(red, green, blue, alpha);
		buffer.addVertex(positionMatrix, maxX, maxY, maxZ).setColor(red, green, blue, alpha);
		buffer.addVertex(positionMatrix, maxX, maxY, minZ).setColor(red, green, blue, alpha);
		buffer.addVertex(positionMatrix, minX, maxY, minZ).setColor(red, green, blue, alpha);

		// Bottom face
		buffer.addVertex(positionMatrix, minX, minY, minZ).setColor(red, green, blue, alpha);
		buffer.addVertex(positionMatrix, maxX, minY, minZ).setColor(red, green, blue, alpha);
		buffer.addVertex(positionMatrix, maxX, minY, maxZ).setColor(red, green, blue, alpha);
		buffer.addVertex(positionMatrix, minX, minY, maxZ).setColor(red, green, blue, alpha);
	}

	// ------------------------------------------------------------------------------------------
	// Drawing: upload the buffer and run a render pass with the no-depth-test pipeline
	// ------------------------------------------------------------------------------------------

	private static void drawBuffer(Minecraft client, RenderPipeline pipeline) {
		// Build the buffer
		MeshData builtBuffer = buffer.buildOrThrow();
		MeshData.DrawState drawParameters = builtBuffer.drawState();
		VertexFormat format = drawParameters.format();

		GpuBuffer vertices = upload(drawParameters, format, builtBuffer);

		draw(client, pipeline, builtBuffer, drawParameters, vertices, format);

		// Rotate the vertex buffer so we are less likely to use buffers that the GPU is using
		vertexBuffer.rotate();
		buffer = null;
	}

	private static GpuBuffer upload(MeshData.DrawState drawParameters, VertexFormat format, MeshData builtBuffer) {
		// Calculate the size needed for the vertex buffer
		int vertexBufferSize = drawParameters.vertexCount() * format.getVertexSize();

		// Initialize or resize the vertex buffer as needed
		if (vertexBuffer == null || vertexBuffer.size() < vertexBufferSize) {
			if (vertexBuffer != null) {
				vertexBuffer.close();
			}

			vertexBuffer = new MappableRingBuffer(() -> DonutBaseFinder.MOD_ID + " esp vertex buffer", GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_MAP_WRITE, vertexBufferSize);
		}

		// Copy vertex data into the vertex buffer
		CommandEncoder commandEncoder = RenderSystem.getDevice().createCommandEncoder();

		try (GpuBuffer.MappedView mappedView = commandEncoder.mapBuffer(vertexBuffer.currentBuffer().slice(0, builtBuffer.vertexBuffer().remaining()), false, true)) {
			MemoryUtil.memCopy(builtBuffer.vertexBuffer(), mappedView.data());
		}

		return vertexBuffer.currentBuffer();
	}

	private static void draw(Minecraft client, RenderPipeline pipeline, MeshData builtBuffer, MeshData.DrawState drawParameters, GpuBuffer vertices, VertexFormat format) {
		GpuBuffer indices;
		VertexFormat.IndexType indexType;

		if (pipeline.getVertexFormatMode() == VertexFormat.Mode.QUADS) {
			// Sort the quads if there is translucency
			builtBuffer.sortQuads(ALLOCATOR, RenderSystem.getProjectionType().vertexSorting());
			// Upload the index buffer
			indices = pipeline.getVertexFormat().uploadImmediateIndexBuffer(builtBuffer.indexBuffer());
			indexType = builtBuffer.drawState().indexType();
		} else {
			// Use the general shape index buffer for non-quad draw modes
			RenderSystem.AutoStorageIndexBuffer shapeIndexBuffer = RenderSystem.getSequentialBuffer(pipeline.getVertexFormatMode());
			indices = shapeIndexBuffer.getBuffer(drawParameters.indexCount());
			indexType = shapeIndexBuffer.type();
		}

		// Actually execute the draw
		GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
				.writeTransform(RenderSystem.getModelViewMatrix(), COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX);
		try (RenderPass renderPass = RenderSystem.getDevice()
				.createCommandEncoder()
				.createRenderPass(() -> DonutBaseFinder.MOD_ID + " esp render pass", client.getMainRenderTarget().getColorTextureView(), OptionalInt.empty(), client.getMainRenderTarget().getDepthTextureView(), OptionalDouble.empty())) {
			renderPass.setPipeline(pipeline);

			RenderSystem.bindDefaultUniforms(renderPass);
			renderPass.setUniform("DynamicTransforms", dynamicTransforms);

			renderPass.setVertexBuffer(0, vertices);
			renderPass.setIndexBuffer(indices, indexType);

			// Base vertex 0: the data was copied to the start of the vertex buffer.
			renderPass.drawIndexed(0, 0, drawParameters.indexCount(), 1);
		}

		builtBuffer.close();
	}

	/** Releases GPU memory. Called from GameRendererMixin when the game renderer closes. */
	public static void close() {
		ALLOCATOR.close();

		if (vertexBuffer != null) {
			vertexBuffer.close();
			vertexBuffer = null;
		}
	}
}
