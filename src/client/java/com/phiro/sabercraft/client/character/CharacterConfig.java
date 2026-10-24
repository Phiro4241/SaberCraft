package com.phiro.sabercraft.client.character;

import com.google.gson.annotations.SerializedName;

import com.phiro.sabercraft.config.JsonConfig;

/**
 * The character choice of this player, in {@code config/sabercraft/character_client.json}. It is
 * kept on this computer and sent to each server again on join.
 */
public final class CharacterConfig {
	private static final String NAME = "character_client";
	private static CharacterConfig current = new CharacterConfig();

	/**
	 * The outfit: empty for your plain skin, the id of a built-in outfit ({@code sabercraft:shadow}),
	 * or {@code file:<name>.png} for a file of your own in {@code config/sabercraft/outfits/}.
	 */
	@SerializedName("outfit")
	public String outfit = "";
	/** Show the outfits other players chose. Off: everyone else looks like their plain skin. */
	@SerializedName("show_other_outfits")
	public boolean showOtherOutfits = true;
	/** Draw small characters (Yoda) at their size (Phase 9). Only the picture changes, not the hitbox. */
	@SerializedName("character_size")
	public boolean characterSize = true;

	public static CharacterConfig get() {
		return current;
	}

	public static void load() {
		CharacterConfig config = JsonConfig.load(NAME, CharacterConfig.class, CharacterConfig::new);
		if (config.outfit == null) {
			config.outfit = "";
		}
		current = config;
	}

	public static void save() {
		JsonConfig.save(NAME, current);
	}
}
