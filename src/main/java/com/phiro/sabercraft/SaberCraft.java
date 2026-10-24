package com.phiro.sabercraft;

import net.fabricmc.api.ModInitializer;

import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.phiro.sabercraft.network.SaberNetworking;

/**
 * Common entrypoint for SaberCraft. Runs on both the dedicated server and the client.
 *
 * <p>The saber itself (blade, animation, sounds) lives entirely on the client and works from the
 * held items and swings that vanilla already syncs. The hilt and color of each saber are stored in
 * a vanilla item component (see {@link com.phiro.sabercraft.saber.SaberVariant}), so the server
 * does not need the mod to show them. The only server part is the packet that changes the variant
 * of a held saber in survival, registered here.
 */
public class SaberCraft implements ModInitializer {
	public static final String MOD_ID = "sabercraft";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		SaberNetworking.init();
		LOGGER.info("SaberCraft initialized");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
