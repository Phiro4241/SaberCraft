package com.phiro.sabercraft.saber;

import java.util.ArrayList;
import java.util.List;

import io.netty.buffer.ByteBuf;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;

import com.phiro.sabercraft.SaberCraft;

/**
 * Which hilt and which blade color one saber has.
 *
 * <p>The choice is stored on the item itself, in the vanilla {@code minecraft:custom_model_data}
 * component: {@code strings[0]} is the hilt, {@code strings[1]} the color. So every sword keeps its
 * own variant when it is moved, dropped, traded or put in a chest. The component is part of the
 * normal item sync, so every client sees the variant without any extra packets, and players
 * without SaberCraft simply see a netherite sword. The item model
 * ({@code assets/minecraft/items/netherite_sword.json}) picks the hilt from it, and the blade
 * renderer picks the color.
 *
 * <p>A sword without the component looks like before Phase 5: the standard hilt with a red blade.
 *
 * @param hilt  hilt id, one of {@link #HILTS}
 * @param color blade color id, one of {@link #COLORS}
 */
public record SaberVariant(Identifier hilt, Identifier color) {
	public static final Identifier STANDARD = SaberCraft.id("standard");
	public static final Identifier GUARDIAN = SaberCraft.id("guardian");
	public static final Identifier DARK_LORD = SaberCraft.id("dark_lord");
	public static final Identifier SENTINEL = SaberCraft.id("sentinel");
	public static final Identifier DOUBLE = SaberCraft.id("double");

	public static final Identifier RED = SaberCraft.id("red");
	public static final Identifier BLUE = SaberCraft.id("blue");
	public static final Identifier GREEN = SaberCraft.id("green");
	public static final Identifier PURPLE = SaberCraft.id("purple");
	public static final Identifier YELLOW = SaberCraft.id("yellow");
	public static final Identifier WHITE = SaberCraft.id("white");

	/** All hilts in menu order. The server only accepts these. */
	public static final List<Identifier> HILTS = List.of(STANDARD, GUARDIAN, DARK_LORD, SENTINEL, DOUBLE);
	/** All blade colors in menu order. The server only accepts these. */
	public static final List<Identifier> COLORS = List.of(RED, BLUE, GREEN, PURPLE, YELLOW, WHITE);

	/** The look of a sword that was never changed. */
	public static final SaberVariant DEFAULT = new SaberVariant(STANDARD, RED);

	public static final StreamCodec<ByteBuf, SaberVariant> STREAM_CODEC = StreamCodec.composite(
			Identifier.STREAM_CODEC, SaberVariant::hilt,
			Identifier.STREAM_CODEC, SaberVariant::color,
			SaberVariant::new
	);

	private static final int HILT_INDEX = 0;
	private static final int COLOR_INDEX = 1;

	/** True if both ids are known. Everything sent to the server is checked with this. */
	public boolean isKnown() {
		return HILTS.contains(this.hilt) && COLORS.contains(this.color);
	}

	/** Items that turn into a saber. */
	public static boolean canHold(ItemStack stack) {
		return stack.is(Items.NETHERITE_SWORD);
	}

	/** The hilt stored on the stack, or {@code null} if none is stored. */
	public static @Nullable Identifier hiltOf(ItemStack stack) {
		return read(stack, HILT_INDEX);
	}

	/** The blade color stored on the stack, or {@code null} if none is stored. */
	public static @Nullable Identifier colorOf(ItemStack stack) {
		return read(stack, COLOR_INDEX);
	}

	private static @Nullable Identifier read(ItemStack stack, int index) {
		CustomModelData data = stack.get(DataComponents.CUSTOM_MODEL_DATA);
		String value = data != null ? data.getString(index) : null;
		return value != null && !value.isEmpty() ? Identifier.tryParse(value) : null;
	}

	/**
	 * Stores this variant on the stack. Other values in the component (floats, flags, colors and
	 * further strings) are kept, in case a data pack uses them as well.
	 */
	public void applyTo(ItemStack stack) {
		CustomModelData old = stack.getOrDefault(DataComponents.CUSTOM_MODEL_DATA, CustomModelData.EMPTY);
		List<String> strings = new ArrayList<>(old.strings());

		while (strings.size() <= COLOR_INDEX) {
			strings.add("");
		}

		strings.set(HILT_INDEX, this.hilt.toString());
		strings.set(COLOR_INDEX, this.color.toString());
		stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(old.floats(), old.flags(), List.copyOf(strings), old.colors()));
	}
}
