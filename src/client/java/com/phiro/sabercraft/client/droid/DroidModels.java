package com.phiro.sabercraft.client.droid;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import com.phiro.sabercraft.droid.DroidType;

/**
 * The droid models (Phase 11): every part is an item model of its own (see {@link DroidParts}),
 * drawn at its pivot and turned by {@link DroidPose}. Used by the droids in the world and by the
 * preview in the droid menu.
 */
public final class DroidModels {
	private static final Map<String, ItemStack> STACKS = new HashMap<>();

	private DroidModels() {
	}

	/** An item that shows one model (a part, or a droid's icon). */
	public static ItemStack stack(String model) {
		return STACKS.computeIfAbsent(model, id -> {
			ItemStack stack = new ItemStack(Items.IRON_NUGGET);
			stack.set(DataComponents.ITEM_MODEL, Identifier.parse(id));
			return stack;
		});
	}

	/** The icon of a droid: the whole droid in one model. */
	public static ItemStack icon(DroidType droid) {
		return stack("sabercraft:droid/" + droid.id() + "_icon");
	}

	public static List<DroidParts.Part> parts(DroidType droid) {
		return DroidParts.PARTS.getOrDefault(droid.id(), List.of());
	}

	/** Resolves the part models of a droid into {@code into} (reused if it has the right size). */
	public static List<ItemStackRenderState> resolve(DroidType droid, @Nullable List<ItemStackRenderState> into, @Nullable Level level,
			@Nullable Entity owner, int seed) {
		List<DroidParts.Part> parts = parts(droid);
		List<ItemStackRenderState> states = into != null && into.size() == parts.size() ? into : new ArrayList<>(parts.size());
		ItemModelResolver resolver = Minecraft.getInstance().getItemModelResolver();

		for (int i = 0; i < parts.size(); i++) {
			if (states.size() <= i) {
				states.add(new ItemStackRenderState());
			}

			resolver.updateForTopItem(states.get(i), stack(parts.get(i).model()), ItemDisplayContext.NONE, level, null, seed + i);
		}

		return states;
	}

	/** Draws a droid standing at the origin, its face to -z, in blocks. */
	public static void submit(PoseStack poseStack, SubmitNodeCollector collector, DroidType droid, DroidPose pose, List<ItemStackRenderState> states,
			int light, int outline) {
		List<DroidParts.Part> parts = parts(droid);
		poseStack.pushPose();
		pose.root(poseStack, droid);

		for (int i = 0; i < parts.size() && i < states.size(); i++) {
			DroidParts.Part part = parts.get(i);
			poseStack.pushPose();
			poseStack.translate(part.x(), part.y(), part.z());
			pose.part(poseStack, droid, part.name());
			// The item model is drawn around its middle, which is the part's pivot.
			states.get(i).submit(poseStack, collector, light, OverlayTexture.NO_OVERLAY, outline);
			poseStack.popPose();
		}

		poseStack.popPose();
	}
}
