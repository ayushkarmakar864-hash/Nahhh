package com.donutbasefinder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ClientModInitializer;

import com.donutbasefinder.config.DonutConfig;
import com.donutbasefinder.esp.BlockScanner;
import com.donutbasefinder.esp.EspRenderer;
import com.donutbasefinder.input.KeyHandler;

/**
 * Client entrypoint. Wires the four modules together:
 * <ul>
 *   <li>{@link DonutConfig}: persisted settings</li>
 *   <li>{@link KeyHandler}: the F + U chord that opens/closes the settings screen</li>
 *   <li>{@link BlockScanner}: finds chests / shulker boxes / spawners near the player</li>
 *   <li>{@link EspRenderer}: draws them through walls via WorldRenderEvents</li>
 * </ul>
 */
public class DonutBaseFinder implements ClientModInitializer {
	public static final String MOD_ID = "donutbasefinder";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitializeClient() {
		DonutConfig.load();
		KeyHandler.register();
		BlockScanner.register();
		EspRenderer.register();

		LOGGER.info("Donut Base Finder initialised. Hold F and press U to open the settings screen.");
	}
}
