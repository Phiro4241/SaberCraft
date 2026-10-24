package com.phiro.sabercraft.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import net.minecraft.client.renderer.item.ItemModels;
import net.minecraft.client.renderer.special.SpecialModelRenderers;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.client.blade.BladeStyles;
import com.phiro.sabercraft.client.render.SaberBladeItemModel;
import com.phiro.sabercraft.client.render.SaberBladeSpecialRenderer;
import com.phiro.sabercraft.client.render.SaberRenderTypes;
import com.phiro.sabercraft.client.saber.HolsterController;
import com.phiro.sabercraft.client.saber.SaberAnimations;
import com.phiro.sabercraft.client.saber.SaberTracker;

/**
 * Client entrypoint for SaberCraft.
 *
 * <p>Registers the energy blade: the netherite sword's item model
 * ({@code assets/minecraft/items/netherite_sword.json}) draws a hilt plus the animated blade
 * whenever the sword is held. Phase 4 adds the ignite and retract animation and the sounds, both
 * driven by {@link SaberTracker} once per tick. Keybindings and the config screen follow later.
 */
public class SaberCraftClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		SaberRenderTypes.init();
		ItemModels.ID_MAPPER.put(SaberCraft.id("saber_blade"), SaberBladeItemModel.Unbaked.MAP_CODEC);
		SpecialModelRenderers.ID_MAPPER.put(SaberCraft.id("saber_blade"), SaberBladeSpecialRenderer.Unbaked.MAP_CODEC);

		SaberTracker.INSTANCE.setHolsterCheck(HolsterController.INSTANCE::isHolstering);
		// Before Minecraft sends the selected hotbar slot to the server.
		ClientTickEvents.START_CLIENT_TICK.register(HolsterController.INSTANCE::tick);
		// After all entities ticked, so held items and swings are up to date.
		ClientTickEvents.END_CLIENT_TICK.register(SaberTracker.INSTANCE::tick);

		SaberCraft.LOGGER.info(
				"SaberCraft client initialized with {} blade style(s) and {} animation(s)",
				BladeStyles.all().size(),
				SaberAnimations.all().size()
		);
	}
}
