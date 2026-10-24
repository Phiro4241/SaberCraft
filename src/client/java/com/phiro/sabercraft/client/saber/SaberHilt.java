package com.phiro.sabercraft.client.saber;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * One hilt design. The model itself lives in {@code assets/sabercraft/models/item/hilt/}; this is
 * what the client needs besides: how the saber behaves and sounds, and which color it gets when
 * the player picks the hilt without choosing a color.
 *
 * @param id           hilt id, stored on the item (see {@code SaberVariant})
 * @param profile      animation and sounds
 * @param defaultColor blade color suggested in the selection menu
 * @param blades       number of blades, 2 for the double-bladed hilt
 */
public record SaberHilt(Identifier id, SaberProfile profile, Identifier defaultColor, int blades) {
	public Component displayName() {
		return Component.translatable("sabercraft.hilt." + this.id.getPath());
	}

	public Component description() {
		return Component.translatable("sabercraft.hilt." + this.id.getPath() + ".description");
	}
}
