package com.donutbasefinder.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.renderer.GameRenderer;

import com.donutbasefinder.esp.EspRenderer;

/**
 * Releases the ESP's GPU buffers when the game renderer shuts down. This is the cleanup hook
 * Fabric's own custom-render-pipeline example uses.
 */
@Mixin(GameRenderer.class)
public class GameRendererMixin {
	@Inject(method = "close", at = @At("RETURN"))
	private void donutbasefinder$onGameRendererClose(CallbackInfo ci) {
		EspRenderer.close();
	}
}
