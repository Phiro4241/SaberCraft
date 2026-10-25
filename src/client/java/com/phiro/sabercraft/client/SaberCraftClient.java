package com.phiro.sabercraft.client;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemModels;
import net.minecraft.client.renderer.special.SpecialModelRenderers;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.InteractionHand;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.client.ability.SaberAbilityClient;
import com.phiro.sabercraft.client.blade.BladeStyleConfig;
import com.phiro.sabercraft.client.blade.BladeStyles;
import com.phiro.sabercraft.client.blaster.BlasterClient;
import com.phiro.sabercraft.client.character.OutfitClient;
import com.phiro.sabercraft.client.combat.CombatConfig;
import com.phiro.sabercraft.client.combat.CombatLibrary;
import com.phiro.sabercraft.client.droid.DroidClient;
import com.phiro.sabercraft.client.droid.DroidPreview;
import com.phiro.sabercraft.client.menu.ModelPreview;
import com.phiro.sabercraft.client.menu.SaberMenuScreen;
import com.phiro.sabercraft.client.render.HitMarks;
import com.phiro.sabercraft.client.music.PlanetAmbience;
import com.phiro.sabercraft.client.music.SaberMusic;
import com.phiro.sabercraft.client.music.StarWarsMusic;
import com.phiro.sabercraft.client.music.UserMusic;
import com.phiro.sabercraft.client.render.SaberBladeItemModel;
import com.phiro.sabercraft.client.render.SaberBladeSpecialRenderer;
import com.phiro.sabercraft.client.render.SaberRenderTypes;
import com.phiro.sabercraft.client.saber.HolsterController;
import com.phiro.sabercraft.client.saber.SaberAnimations;
import com.phiro.sabercraft.client.saber.SaberHilts;
import com.phiro.sabercraft.client.saber.SaberTracker;
import com.phiro.sabercraft.client.select.SaberCustomizeScreen;
import com.phiro.sabercraft.client.select.SaberSelection;
import com.phiro.sabercraft.client.ship.ShipClient;
import com.phiro.sabercraft.client.ship.ShipPreview;
import com.phiro.sabercraft.client.sky.PlanetSky;

/**
 * Client entrypoint for SaberCraft.
 *
 * <p>Registers the energy blade: the netherite sword's item model
 * ({@code assets/minecraft/items/netherite_sword.json}) draws a hilt plus the animated blade
 * whenever the sword is held. Phase 4 adds the ignite and retract animation and the sounds, both
 * driven by {@link SaberTracker} once per tick. Phase 5 adds hilts and colors per sword, chosen in
 * the {@link SaberCustomizeScreen} (key V), and the per-color settings file. Phase 6 adds the
 * fighting moves ({@link CombatLibrary}), light trails and sparks, and the abilities
 * ({@link SaberAbilityClient}). The config screen follows later.
 */
public class SaberCraftClient implements ClientModInitializer {
	public static final KeyMapping.Category KEY_CATEGORY = KeyMapping.Category.register(SaberCraft.id("main"));
	public static final KeyMapping CUSTOMIZE_KEY = new KeyMapping(
			"key.sabercraft.customize", InputConstants.Type.KEYSYM, InputConstants.KEY_V, KEY_CATEGORY
	);

	@Override
	public void onInitializeClient() {
		BladeStyleConfig.load();
		ImmersionConfig.load();
		CombatConfig.load();
		ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(SaberCraft.id("combat_moves"), CombatLibrary.INSTANCE);
		SaberRenderTypes.init();
		ItemModels.ID_MAPPER.put(SaberCraft.id("saber_blade"), SaberBladeItemModel.Unbaked.MAP_CODEC);
		SpecialModelRenderers.ID_MAPPER.put(SaberCraft.id("saber_blade"), SaberBladeSpecialRenderer.Unbaked.MAP_CODEC);

		SaberTracker.INSTANCE.setHolsterCheck(HolsterController.INSTANCE::isHolstering);
		// Before Minecraft sends the selected hotbar slot to the server.
		ClientTickEvents.START_CLIENT_TICK.register(HolsterController.INSTANCE::tick);
		// After all entities ticked, so held items and swings are up to date.
		ClientTickEvents.END_CLIENT_TICK.register(SaberTracker.INSTANCE::tick);

		KeyMappingHelper.registerKeyMapping(CUSTOMIZE_KEY);
		SaberAbilityClient.init();
		// Phase 7: the optional music; your own files are read again on every join.
		ClientTickEvents.END_CLIENT_TICK.register(SaberMusic::tick);
		ClientTickEvents.END_CLIENT_TICK.register(PlanetSky::tick);
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			UserMusic.scan();
			StarWarsMusic.scan();
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			SaberMusic.reset();
			PlanetAmbience.reset();
		});
		// Phase 10: the sound of the chosen planet.
		ClientTickEvents.END_CLIENT_TICK.register(PlanetAmbience::tick);
		// Phase 7: outfits and characters; since Phase 10 they replace the skin itself.
		OutfitClient.init();
		// Phase 10: blasters.
		BlasterClient.init();
		ModelPreview.init();
		DroidClient.init();
		DroidPreview.init();
		ShipClient.init();
		ShipPreview.init();
		HitMarks.init();
		ClientTickEvents.END_CLIENT_TICK.register(SaberCraftClient::handleKeys);

		SaberCraft.LOGGER.info(
				"SaberCraft client initialized with {} blade style(s), {} hilt(s) and {} animation(s)",
				BladeStyles.all().size(),
				SaberHilts.all().size(),
				SaberAnimations.all().size()
		);
	}

	private static void handleKeys(Minecraft client) {
		while (CUSTOMIZE_KEY.consumeClick()) {
			if (client.player == null || client.gui.screen() != null) {
				continue;
			}

			// The SaberCraft menu: the saber area with a sword in hand, the blasters with a bow,
			// the character otherwise.
			boolean bow = client.player.getMainHandItem().is(net.minecraft.world.item.Items.BOW)
					|| client.player.getOffhandItem().is(net.minecraft.world.item.Items.BOW);
			SaberMenuScreen.open(client, bow ? SaberMenuScreen.Tab.BLASTER : SaberMenuScreen.Tab.SABER);
		}
	}
}
