package com.phiro.sabercraft.client.saber;

import org.jspecify.annotations.Nullable;

import net.minecraft.world.item.ItemStack;

import com.phiro.sabercraft.client.sound.SaberSoundSet;
import com.phiro.sabercraft.saber.SaberVariant;

/**
 * Decides which items are sabers and how they behave. Every netherite sword is a saber; its hilt
 * (stored on the item, see {@link SaberVariant}) decides the animation and the sounds.
 */
public final class SaberProfiles {
	/** Default animation and sounds, used by the standard, guardian and sentinel hilts. */
	public static final SaberProfile DEFAULT = new SaberProfile(SaberAnimations.DEFAULT, SaberSoundSet.DEFAULT);
	/** The dark hilt: a slower, heavier ignition and a deeper, rougher hum. */
	public static final SaberProfile DARK = new SaberProfile(SaberAnimations.HEAVY, SaberSoundSet.DARK);
	/** The double-bladed hilt: default timing, a fuller hum because two blades are lit. */
	public static final SaberProfile DOUBLE = new SaberProfile(SaberAnimations.DEFAULT, SaberSoundSet.DOUBLE);

	private SaberProfiles() {
	}

	/** Returns the profile of the stack, or {@code null} if the stack is not a saber. */
	public static @Nullable SaberProfile of(ItemStack stack) {
		return SaberVariant.canHold(stack) ? SaberHilts.get(SaberVariant.hiltOf(stack)).profile() : null;
	}

	public static boolean isSaber(ItemStack stack) {
		return of(stack) != null;
	}
}
