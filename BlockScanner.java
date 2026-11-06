package com.donutbasefinder.esp;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.block.entity.BlockEntity;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import com.donutbasefinder.config.DonutConfig;

/**
 * Looks through the loaded chunks around the player a couple of times per second and keeps an
 * immutable snapshot of the highlighted block entities. The renderer only reads the snapshot, so
 * no chunk scanning happens inside the render loop.
 */
public final class BlockScanner {
	/** A highlighted block. {@code distSq} is measured from the player at scan time. */
	public record Entry(BlockPos pos, EspTarget target, double distSq) {
	}

	private static final int SCAN_INTERVAL_TICKS = 10;
	/** Hard cap on how many boxes are drawn per frame (nearest first). */
	private static final int MAX_ENTRIES = 1024;

	private static volatile List<Entry> snapshot = List.of();
	private static ClientLevel lastLevel;
	private static int tickCounter = 0;

	private BlockScanner() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(BlockScanner::onEndClientTick);
	}

	/** The latest scan result. Safe to read from the render thread. */
	public static List<Entry> snapshot() {
		return snapshot;
	}

	private static void onEndClientTick(Minecraft client) {
		ClientLevel level = client.level;
		LocalPlayer player = client.player;

		if (level == null || player == null || !DonutConfig.espEnabled) {
			snapshot = List.of();
			lastLevel = null;
			tickCounter = 0;
			return;
		}

		if (level != lastLevel) {
			// Joined a world or changed dimension: drop stale results and scan right away.
			snapshot = List.of();
			lastLevel = level;
			tickCounter = 0;
		}

		if (tickCounter++ % SCAN_INTERVAL_TICKS != 0) {
			return;
		}

		scan(level, player);
	}

	private static void scan(ClientLevel level, LocalPlayer player) {
		int range = DonutConfig.rangeBlocks;
		double rangeSq = (double) range * range;
		int chunkRadius = (range >> 4) + 1;
		int playerChunkX = player.getBlockX() >> 4;
		int playerChunkZ = player.getBlockZ() >> 4;
		double px = player.getX();
		double py = player.getY();
		double pz = player.getZ();

		List<Entry> found = new ArrayList<>();

		for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
			for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
				// For chunks that are not loaded the client returns an empty chunk, which has no block entities.
				LevelChunk chunk = level.getChunk(playerChunkX + dx, playerChunkZ + dz);

				for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
					if (blockEntity.isRemoved()) {
						continue;
					}

					EspTarget target = EspTarget.of(blockEntity);
					if (target == null) {
						continue;
					}

					BlockPos pos = blockEntity.getBlockPos();
					double ox = pos.getX() + 0.5 - px;
					double oy = pos.getY() + 0.5 - py;
					double oz = pos.getZ() + 0.5 - pz;
					double distSq = ox * ox + oy * oy + oz * oz;

					if (distSq <= rangeSq) {
						found.add(new Entry(pos, target, distSq));
					}
				}
			}
		}

		found.sort(Comparator.comparingDouble(Entry::distSq));
		if (found.size() > MAX_ENTRIES) {
			found = new ArrayList<>(found.subList(0, MAX_ENTRIES));
		}

		snapshot = List.copyOf(found);
	}
}
