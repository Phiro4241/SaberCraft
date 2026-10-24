package com.phiro.sabercraft.client;

import net.fabricmc.api.ClientModInitializer;

import net.minecraft.client.renderer.special.SpecialModelRenderers;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.client.blade.BladeStyles;
import com.phiro.sabercraft.client.render.SaberBladeSpecialRenderer;
import com.phiro.sabercraft.client.render.SaberRenderTypes;

/**
 * Client entrypoint for SaberCraft.
 *
 * <p>Phase 3 registers the energy blade renderer. The netherite sword's item model
 * ({@code assets/minecraft/items/netherite_sword.json}) uses it to draw a hilt with a glowing
 * blade whenever the sword is held. Animation, audio, keybindings and the config screen follow in
 * later phases.
 */
public class SaberCraftClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		SaberRenderTypes.init();
		SpecialModelRenderers.ID_MAPPER.put(SaberCraft.id("saber_blade"), SaberBladeSpecialRenderer.Unbaked.MAP_CODEC);

		SaberCraft.LOGGER.info("SaberCraft client initialized with {} blade style(s)", BladeStyles.all().size());
	}
}
