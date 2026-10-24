package com.phiro.sabercraft.client;

import net.fabricmc.api.ClientModInitializer;

import com.phiro.sabercraft.SaberCraft;

/**
 * Client entrypoint for SaberCraft. Rendering, animation, audio, keybindings and the config
 * screen are set up here in later phases.
 */
public class SaberCraftClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		SaberCraft.LOGGER.info("SaberCraft client initialized");
	}
}
