package com.phiro.sabercraft;

import net.fabricmc.api.ModInitializer;

import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common entrypoint for SaberCraft. Runs on both the dedicated server and the client.
 *
 * <p>The saber itself (blade, animation, sounds) lives entirely on the client and works from the
 * held items and swings that vanilla already syncs, so nothing has to be registered here and the
 * server does not need the mod. Server-side features of later phases start here.
 */
public class SaberCraft implements ModInitializer {
	public static final String MOD_ID = "sabercraft";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("SaberCraft initialized");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
