package com.donutbasefinder.esp;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;

import com.donutbasefinder.config.DonutConfig;

/** The block entity kinds the ESP highlights, with their colour and box shape. */
public enum EspTarget {
	/** Brown. Covers normal and trapped chests (a trapped chest is a ChestBlockEntity subclass). */
	CHEST(0.60f, 0.32f, 0.10f, 0.0625, 0.875),
	/** Purple. */
	SHULKER_BOX(0.62f, 0.20f, 0.85f, 0.0, 1.0),
	/** Red. Monster spawners. */
	SPAWNER(1.00f, 0.12f, 0.12f, 0.0, 1.0);

	public final float red;
	public final float green;
	public final float blue;
	/** Horizontal inset from the block edge, in blocks (a chest model is 14/16 wide). */
	public final double inset;
	/** Box height in blocks (a chest model is 14/16 tall). */
	public final double height;

	EspTarget(float red, float green, float blue, double inset, double height) {
		this.red = red;
		this.green = green;
		this.blue = blue;
		this.inset = inset;
		this.height = height;
	}

	public boolean isEnabled() {
		return switch (this) {
			case CHEST -> DonutConfig.showChests;
			case SHULKER_BOX -> DonutConfig.showShulkerBoxes;
			case SPAWNER -> DonutConfig.showSpawners;
		};
	}

	/** Returns the target for a block entity, or null if it is not one we highlight. */
	public static EspTarget of(BlockEntity blockEntity) {
		if (blockEntity instanceof ChestBlockEntity) {
			return CHEST;
		}
		if (blockEntity instanceof ShulkerBoxBlockEntity) {
			return SHULKER_BOX;
		}
		if (blockEntity instanceof SpawnerBlockEntity) {
			return SPAWNER;
		}
		return null;
	}
}
