package com.phiro.sabercraft.client.saber;

import org.jspecify.annotations.Nullable;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.phiro.sabercraft.client.sound.SaberSoundSet;

/**
 * Decides which items are sabers and how they behave. In Phase 4 every netherite sword is a saber
 * with the default animation and sounds. Later phases can pick a profile per item here, for
 * example from a data component, without touching the tracker or the renderer.
 */
public final class SaberProfiles {
	public static final SaberProfile DEFAULT = new SaberProfile(SaberAnimations.DEFAULT, SaberSoundSet.DEFAULT);

	private SaberProfiles() {
	}

	/** Returns the profile of the stack, or {@code null} if the stack is not a saber. */
	public static @Nullable SaberProfile of(ItemStack stack) {
		return stack.is(Items.NETHERITE_SWORD) ? DEFAULT : null;
	}

	public static boolean isSaber(ItemStack stack) {
		return of(stack) != null;
	}
}
