package com.phiro.sabercraft;

import net.fabricmc.api.ModInitializer;

import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common entrypoint for SaberCraft. Runs on both the dedicated server and the client.
 *
 * <p>Phase 2 only sets up the mod skeleton. The saber state component, network payloads and
 * sound events are registered here in later phases, because the server owns the saber state.
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
