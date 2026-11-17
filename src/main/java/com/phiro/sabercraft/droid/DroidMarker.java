package com.phiro.sabercraft.droid;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;

import com.phiro.sabercraft.SaberCraft;

/**
 * How a droid is recognized on both sides. For the network a droid is a plain vanilla pig that
 * wears a marked item on its head ({@code strings: ["sabercraft:droid", <type>]}), the same idea
 * as the blaster bolts that are snowballs: no new entity type, so players without SaberCraft can
 * still join (they see a pig called "R2-D2"); clients with SaberCraft draw the droid instead.
 */
public final class DroidMarker {
	public static final String MARKER = SaberCraft.id("droid").toString();

	private DroidMarker() {
	}

	/** The head item that marks a pig as a droid of that type. */
	public static ItemStack item(DroidType type) {
		ItemStack stack = new ItemStack(Items.IRON_NUGGET);
		stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(), List.of(MARKER, type.id()), List.of()));
		return stack;
	}

	/**
	 * Phase 22: the head item of a droid of the story (R2-V7, M-31): the type, and a third string
	 * naming the story's own droid, so that every client knows it is not the player's R2-D2.
	 */
	public static ItemStack item(DroidType type, @Nullable String story) {
		if (story == null || story.isEmpty()) {
			return item(type);
		}

		ItemStack stack = new ItemStack(Items.IRON_NUGGET);
		stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(), List.of(MARKER, type.id(), story), List.of()));
		return stack;
	}

	/** Phase 22: the story's own droid an entity is ({@code r2v7}, {@code m31}), or {@code null}. */
	public static @Nullable String story(Entity entity) {
		if (!(entity instanceof Pig pig)) {
			return null;
		}

		CustomModelData data = pig.getItemBySlot(EquipmentSlot.HEAD).get(DataComponents.CUSTOM_MODEL_DATA);

		if (data == null || data.strings().size() < 3 || !MARKER.equals(data.strings().get(0))) {
			return null;
		}

		return data.strings().get(2);
	}

	/** The droid type of an entity, or {@code null} if it is no droid. */
	public static @Nullable DroidType of(Entity entity) {
		if (!(entity instanceof Pig pig)) {
			return null;
		}

		CustomModelData data = pig.getItemBySlot(EquipmentSlot.HEAD).get(DataComponents.CUSTOM_MODEL_DATA);

		if (data == null || data.strings().size() < 2 || !MARKER.equals(data.strings().get(0))) {
			return null;
		}

		return DroidType.byId(data.strings().get(1)).orElse(null);
	}
}
