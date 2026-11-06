package com.donutbasefinder.input;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;

import com.donutbasefinder.DonutBaseFinder;
import com.donutbasefinder.gui.DonutSettingsScreen;

/**
 * Registers the "toggle settings" key mapping and implements the F + U chord.
 *
 * <p>The mapping registered through {@link KeyBindingHelper} is the <b>U</b> key (rebindable in
 * Options &gt; Controls). <b>F</b> is the fixed modifier that must be held with it. Both keys are
 * read straight from GLFW every tick, which means the chord also works while the settings screen
 * is open (vanilla stops feeding key mappings while any screen is showing).
 */
public final class KeyHandler {
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
			Identifier.fromNamespaceAndPath(DonutBaseFinder.MOD_ID, "main")
	);

	private static KeyMapping toggleKey;
	private static boolean chordWasDown = false;

	private KeyHandler() {
	}

	public static void register() {
		toggleKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.donutbasefinder.toggle_menu", // translation key, see en_us.json
				InputConstants.Type.KEYSYM,
				InputConstants.KEY_U,
				CATEGORY
		));

		ClientTickEvents.END_CLIENT_TICK.register(KeyHandler::onEndClientTick);
	}

	private static void onEndClientTick(Minecraft client) {
		// Drain queued clicks so they do not pile up; the chord below is edge-detected instead.
		while (toggleKey.consumeClick()) {
			// intentionally empty
		}

		Window window = client.getWindow();
		boolean chordDown = InputConstants.isKeyDown(window, InputConstants.KEY_F) && isToggleKeyDown(window);

		// Only react in the world or while our own screen is open, never while typing in chat,
		// editing a sign, using an anvil rename field, and so on.
		Screen current = client.screen;
		boolean allowed = client.player != null
				&& (current == null || current instanceof DonutSettingsScreen);

		if (allowed && chordDown && !chordWasDown) {
			toggleScreen(client, current);
		}

		chordWasDown = chordDown;
	}

	private static boolean isToggleKeyDown(Window window) {
		InputConstants.Key bound = KeyBindingHelper.getBoundKeyOf(toggleKey);
		if (bound.getType() != InputConstants.Type.KEYSYM) {
			return false;
		}

		int code = bound.getValue();
		return code != GLFW.GLFW_KEY_UNKNOWN && InputConstants.isKeyDown(window, code);
	}

	private static void toggleScreen(Minecraft client, Screen current) {
		if (current instanceof DonutSettingsScreen) {
			client.setScreen(null);
		} else {
			client.setScreen(new DonutSettingsScreen());
		}
	}
}
