package com.phiro.sabercraft.item;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;

import com.phiro.sabercraft.blaster.BlasterType;
import com.phiro.sabercraft.saber.SaberVariant;

/**
 * Phase 10: turning vanilla swords into sabers and bows into blasters, and back.
 *
 * <p>A converted item stays the very same item stack: the same item (so the same material,
 * damage and durability), the same enchantments, name, lore and every other component. Nothing is
 * created or removed, so nothing can be duplicated or lost. Only the vanilla
 * {@code minecraft:custom_model_data} changes, which the game syncs to every client anyway and
 * which the item models of SaberCraft read: {@code strings[0]} holds the hilt or the blaster
 * type.
 *
 * <p>Whatever model data the item had before (a data pack may use it too) is remembered in
 * {@code minecraft:custom_data} under {@value #KEY} and put back exactly when the item is turned
 * back, together with removing that key again.
 */
public final class ItemConversion {
	/** Where the model data from before the conversion is kept. */
	static final String KEY = "sabercraft_original_model_data";
	private static final String NONE = "none";

	public enum Result {
		/** The item was converted or turned back. */
		DONE,
		/** The saber got a new hilt or color (it already was one). */
		CHANGED,
		/** The item in hand is not a sword (or bow) that can be converted. */
		WRONG_ITEM,
		/** It already is a saber (or blaster), and nothing new was asked for. */
		ALREADY,
		/** It is not converted, so there is nothing to turn back. */
		NOT_CONVERTED,
		/** An unknown hilt, color or blaster type was asked for. */
		UNKNOWN_VARIANT
	}

	private ItemConversion() {
	}

	/**
	 * Makes a saber of the sword: hilt and color as given, or (null) a hilt that fits the material
	 * and a blue blade (red for netherite, as before). An existing saber gets the new hilt or color.
	 */
	public static Result toSaber(ItemStack stack, Identifier color, Identifier hilt) {
		if (!SaberVariant.isSword(stack)) {
			return Result.WRONG_ITEM;
		}

		boolean saber = SaberVariant.canHold(stack);

		if (saber && color == null && hilt == null) {
			return Result.ALREADY;
		}

		Identifier newHilt = hilt != null ? hilt : saber && SaberVariant.hiltOf(stack) != null ? SaberVariant.hiltOf(stack) : SaberVariant.hiltForMaterial(stack);
		Identifier newColor = color != null ? color : saber && SaberVariant.colorOf(stack) != null ? SaberVariant.colorOf(stack)
				: stack.is(Items.NETHERITE_SWORD) ? SaberVariant.RED : SaberVariant.BLUE;
		Identifier style = saber && newHilt.equals(SaberVariant.hiltOf(stack)) ? SaberVariant.styleOf(stack) : SaberVariant.defaultStyle(newHilt);
		SaberVariant variant = new SaberVariant(newHilt, newColor, style);

		if (!variant.isKnown()) {
			return Result.UNKNOWN_VARIANT;
		}

		if (!saber) {
			remember(stack);
		}

		variant.applyTo(stack);
		return saber ? Result.CHANGED : Result.DONE;
	}

	/** Turns a saber back into the plain sword it was. */
	public static Result fromSaber(ItemStack stack) {
		if (!SaberVariant.isSword(stack)) {
			return Result.WRONG_ITEM;
		}

		if (!SaberVariant.canHold(stack)) {
			return Result.NOT_CONVERTED;
		}

		restore(stack);

		// A netherite sword is a saber unless it says otherwise.
		if (SaberVariant.canHold(stack)) {
			CustomModelData old = stack.getOrDefault(DataComponents.CUSTOM_MODEL_DATA, CustomModelData.EMPTY);
			List<String> strings = new ArrayList<>(old.strings());

			if (strings.isEmpty()) {
				strings.add(SaberVariant.PLAIN.toString());
			} else {
				strings.set(0, SaberVariant.PLAIN.toString());
			}

			stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(old.floats(), old.flags(), List.copyOf(strings), old.colors()));
		}

		return Result.DONE;
	}

	/**
	 * Makes a blaster of a bow: the given type, or (null) an E-11. A blaster gets the new type.
	 */
	public static Result toBlaster(ItemStack stack, BlasterType type) {
		if (!stack.is(Items.BOW)) {
			return Result.WRONG_ITEM;
		}

		BlasterType current = BlasterType.of(stack);

		if (current != null && (type == null || type == current)) {
			return Result.ALREADY;
		}

		if (current == null) {
			remember(stack);
		}

		BlasterType chosen = type != null ? type : BlasterType.E11;
		CustomModelData old = stack.getOrDefault(DataComponents.CUSTOM_MODEL_DATA, CustomModelData.EMPTY);
		List<String> strings = new ArrayList<>(old.strings());

		if (strings.isEmpty()) {
			strings.add(chosen.id().toString());
		} else {
			strings.set(0, chosen.id().toString());
		}

		stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(old.floats(), old.flags(), List.copyOf(strings), old.colors()));
		return current == null ? Result.DONE : Result.CHANGED;
	}

	/** Turns a blaster back into the plain bow it was. */
	public static Result fromBlaster(ItemStack stack) {
		if (!stack.is(Items.BOW)) {
			return Result.WRONG_ITEM;
		}

		if (BlasterType.of(stack) == null) {
			return Result.NOT_CONVERTED;
		}

		restore(stack);
		return Result.DONE;
	}

	/** Remembers the model data from before, unless it already is remembered. */
	static void remember(ItemStack stack) {
		CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);

		if (data.copyTag().contains(KEY)) {
			return;
		}

		CustomModelData old = stack.get(DataComponents.CUSTOM_MODEL_DATA);
		Tag saved = old == null ? null : CustomModelData.CODEC.encodeStart(NbtOps.INSTANCE, old).result().orElse(null);
		CompoundTag entry = new CompoundTag();

		if (saved != null) {
			entry.put("data", saved);
		} else {
			entry.putString(NONE, NONE);
		}

		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.put(KEY, entry));
	}

	/** Puts the model data from before back, and forgets it. Without a remembered one: none. */
	static void restore(ItemStack stack) {
		CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
		Optional<CompoundTag> entry = data.copyTag().getCompound(KEY);
		Optional<CustomModelData> old = entry.map(tag -> tag.get("data"))
				.flatMap(tag -> CustomModelData.CODEC.parse(NbtOps.INSTANCE, tag).result());

		if (old.isPresent()) {
			stack.set(DataComponents.CUSTOM_MODEL_DATA, old.get());
		} else if (entry.isPresent()) {
			stack.remove(DataComponents.CUSTOM_MODEL_DATA);
		} else {
			// Changed before Phase 10 (a netherite saber): only SaberCraft's own strings go.
			stripOwnStrings(stack);
		}

		if (entry.isPresent()) {
			CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.remove(KEY));

			if (stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).isEmpty()) {
				stack.remove(DataComponents.CUSTOM_DATA);
			}
		}
	}

	/** Clears the hilt, color and style strings, and drops the component if nothing else is in it. */
	private static void stripOwnStrings(ItemStack stack) {
		CustomModelData old = stack.get(DataComponents.CUSTOM_MODEL_DATA);

		if (old == null) {
			return;
		}

		List<String> strings = new ArrayList<>(old.strings());

		for (int i = 0; i < Math.min(3, strings.size()); i++) {
			strings.set(i, "");
		}

		while (!strings.isEmpty() && strings.getLast().isEmpty()) {
			strings.removeLast();
		}

		if (strings.isEmpty() && old.floats().isEmpty() && old.flags().isEmpty() && old.colors().isEmpty()) {
			stack.remove(DataComponents.CUSTOM_MODEL_DATA);
		} else {
			stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(old.floats(), old.flags(), List.copyOf(strings), old.colors()));
		}
	}
}
