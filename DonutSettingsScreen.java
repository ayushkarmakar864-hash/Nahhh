package com.donutbasefinder.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import com.donutbasefinder.config.DonutConfig;

/**
 * Settings screen. Opened and closed by the F + U chord (see KeyHandler); Escape and the
 * Done button also close it. Every click saves the config immediately.
 */
public class DonutSettingsScreen extends Screen {
	private static final int BUTTON_WIDTH = 200;
	private static final int BUTTON_HEIGHT = 20;
	private static final int ROW_SPACING = 24;

	private Button masterButton;
	private Button chestButton;
	private Button shulkerButton;
	private Button spawnerButton;
	private Button fillButton;
	private Button rangeButton;
	private Button lineButton;

	public DonutSettingsScreen() {
		super(Component.translatable("screen.donutbasefinder.title"));
	}

	@Override
	protected void init() {
		int x = this.width / 2 - BUTTON_WIDTH / 2;
		int y = this.height / 2 - 100;

		masterButton = addRenderableWidget(Button.builder(Component.empty(), button -> {
			DonutConfig.espEnabled = !DonutConfig.espEnabled;
			changed();
		}).bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT).build());
		y += ROW_SPACING + 6;

		chestButton = addRenderableWidget(Button.builder(Component.empty(), button -> {
			DonutConfig.showChests = !DonutConfig.showChests;
			changed();
		}).bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT).build());
		y += ROW_SPACING;

		shulkerButton = addRenderableWidget(Button.builder(Component.empty(), button -> {
			DonutConfig.showShulkerBoxes = !DonutConfig.showShulkerBoxes;
			changed();
		}).bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT).build());
		y += ROW_SPACING;

		spawnerButton = addRenderableWidget(Button.builder(Component.empty(), button -> {
			DonutConfig.showSpawners = !DonutConfig.showSpawners;
			changed();
		}).bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT).build());
		y += ROW_SPACING + 6;

		fillButton = addRenderableWidget(Button.builder(Component.empty(), button -> {
			DonutConfig.fillBoxes = !DonutConfig.fillBoxes;
			changed();
		}).bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT).build());
		y += ROW_SPACING;

		rangeButton = addRenderableWidget(Button.builder(Component.empty(), button -> {
			DonutConfig.rangeBlocks = DonutConfig.nextRange(DonutConfig.rangeBlocks);
			changed();
		}).bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT).build());
		y += ROW_SPACING;

		lineButton = addRenderableWidget(Button.builder(Component.empty(), button -> {
			DonutConfig.lineScale = DonutConfig.nextLineScale(DonutConfig.lineScale);
			changed();
		}).bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT).build());
		y += ROW_SPACING + 6;

		addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
				.bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT)
				.build());

		refreshLabels();
	}

	private void changed() {
		DonutConfig.save();
		refreshLabels();
	}

	private void refreshLabels() {
		masterButton.setMessage(Component.literal("Block ESP: " + onOff(DonutConfig.espEnabled)));
		chestButton.setMessage(Component.literal("Chests (brown): " + onOff(DonutConfig.showChests)));
		shulkerButton.setMessage(Component.literal("Shulker boxes (purple): " + onOff(DonutConfig.showShulkerBoxes)));
		spawnerButton.setMessage(Component.literal("Spawners (red): " + onOff(DonutConfig.showSpawners)));
		fillButton.setMessage(Component.literal("Translucent fill: " + onOff(DonutConfig.fillBoxes)));
		rangeButton.setMessage(Component.literal("Range: " + DonutConfig.rangeBlocks + " blocks"));
		lineButton.setMessage(Component.literal("Line width: x" + DonutConfig.lineScale));
	}

	private static String onOff(boolean value) {
		return value ? "ON" : "OFF";
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		// Must call super so the background and the widgets are drawn.
		super.render(graphics, mouseX, mouseY, delta);

		String title = "Donut Base Finder";
		graphics.drawString(this.font, title, this.width / 2 - this.font.width(title) / 2, this.height / 2 - 124, 0xFFFFFFFF, true);

		String hint = "Hold F and press U to close";
		graphics.drawString(this.font, hint, this.width / 2 - this.font.width(hint) / 2, this.height / 2 - 112, 0xFFAAAAAA, false);
	}

	/** Let the world keep running behind the overlay (matters in singleplayer). */
	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
