package com.phiro.sabercraft.ship;

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
 * How a starship is recognized on both sides: like the droids (see
 * {@link com.phiro.sabercraft.droid.DroidMarker}), a vanilla pig wearing a marked head item
 * ({@code strings: ["sabercraft:ship", <type>]}). No new entity type, so players without
 * SaberCraft can still join; they see a flying pig.
 */
public final class ShipMarker {
	public static final String MARKER = SaberCraft.id("ship").toString();

	private ShipMarker() {
	}

	public static ItemStack item(ShipType type) {
		ItemStack stack = new ItemStack(Items.IRON_NUGGET);
		stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(), List.of(MARKER, type.id()), List.of()));
		return stack;
	}

	/** The ship type of an entity, or {@code null} if it is no ship. */
	public static @Nullable ShipType of(@Nullable Entity entity) {
		if (!(entity instanceof Pig pig)) {
			return null;
		}

		CustomModelData data = pig.getItemBySlot(EquipmentSlot.HEAD).get(DataComponents.CUSTOM_MODEL_DATA);

		if (data == null || data.strings().size() < 2 || !MARKER.equals(data.strings().get(0))) {
			return null;
		}

		return ShipType.byId(data.strings().get(1)).orElse(null);
	}
}
