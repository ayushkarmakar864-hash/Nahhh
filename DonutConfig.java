package com.donutbasefinder.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loader.api.FabricLoader;

import com.donutbasefinder.DonutBaseFinder;

/**
 * Settings, stored as JSON in {@code config/donutbasefinder.json}.
 * Fields are static so the renderer can read them every frame without allocation.
 */
public final class DonutConfig {
	/** Allowed values for the range button in the settings screen, in blocks. */
	public static final int[] RANGE_STEPS = {32, 64, 96, 128, 192, 256};
	/** Allowed values for the line-width button in the settings screen. */
	public static final float[] LINE_SCALE_STEPS = {1.0f, 1.5f, 2.0f, 3.0f};

	public static boolean espEnabled = true;
	public static boolean showChests = true;
	public static boolean showShulkerBoxes = true;
	public static boolean showSpawners = true;
	public static boolean fillBoxes = true;
	public static int rangeBlocks = 128;
	public static float lineScale = 1.0f;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private DonutConfig() {
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve(DonutBaseFinder.MOD_ID + ".json");
	}

	/** Plain data holder so Gson never touches the static fields directly. */
	private static final class Data {
		boolean espEnabled = true;
		boolean showChests = true;
		boolean showShulkerBoxes = true;
		boolean showSpawners = true;
		boolean fillBoxes = true;
		int rangeBlocks = 128;
		float lineScale = 1.0f;
	}

	public static void load() {
		Path file = path();
		if (!Files.exists(file)) {
			save();
			return;
		}

		try (Reader reader = Files.newBufferedReader(file)) {
			Data data = GSON.fromJson(reader, Data.class);
			if (data == null) {
				return;
			}

			espEnabled = data.espEnabled;
			showChests = data.showChests;
			showShulkerBoxes = data.showShulkerBoxes;
			showSpawners = data.showSpawners;
			fillBoxes = data.fillBoxes;
			rangeBlocks = Math.max(16, Math.min(512, data.rangeBlocks));
			lineScale = Math.max(0.5f, Math.min(4.0f, data.lineScale));
		} catch (IOException | RuntimeException e) {
			DonutBaseFinder.LOGGER.warn("Could not read {}, keeping defaults", file, e);
		}
	}

	public static void save() {
		Data data = new Data();
		data.espEnabled = espEnabled;
		data.showChests = showChests;
		data.showShulkerBoxes = showShulkerBoxes;
		data.showSpawners = showSpawners;
		data.fillBoxes = fillBoxes;
		data.rangeBlocks = rangeBlocks;
		data.lineScale = lineScale;

		Path file = path();
		try {
			Files.createDirectories(file.getParent());
			try (Writer writer = Files.newBufferedWriter(file)) {
				GSON.toJson(data, writer);
			}
		} catch (IOException e) {
			DonutBaseFinder.LOGGER.warn("Could not write {}", file, e);
		}
	}

	/** Returns the next range in {@link #RANGE_STEPS}, wrapping around. */
	public static int nextRange(int current) {
		for (int step : RANGE_STEPS) {
			if (step > current) {
				return step;
			}
		}
		return RANGE_STEPS[0];
	}

	/** Returns the next line scale in {@link #LINE_SCALE_STEPS}, wrapping around. */
	public static float nextLineScale(float current) {
		for (float step : LINE_SCALE_STEPS) {
			if (step > current + 0.001f) {
				return step;
			}
		}
		return LINE_SCALE_STEPS[0];
	}
}
