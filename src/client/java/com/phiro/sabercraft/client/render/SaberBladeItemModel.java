package com.phiro.sabercraft.client.render;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import com.google.common.base.Suppliers;
import com.mojang.math.Transformation;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import org.joml.Matrix4fc;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.ModelRenderProperties;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.client.resources.model.ResolvedModel;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import com.phiro.sabercraft.client.saber.SaberTracker;

/**
 * Item model {@code "type": "sabercraft:saber_blade"}: the animated energy blade.
 *
 * <p>It works like vanilla's {@code minecraft:special} model, but it also knows who holds the item.
 * Minecraft calls {@link #update} every frame for each held item, with the entity as owner and the
 * hand as display context. The model looks up how far the blade of that entity's hand is out and
 * passes it to the {@link SaberBladeSpecialRenderer}. So the blade animates in first person,
 * third person, on mobs and in the inventory player preview, all through the normal item rendering
 * of 26.2.
 *
 * <p>Example:
 * <pre>{@code {"type": "sabercraft:saber_blade", "base": "sabercraft:item/saber_hilt", "style": "sabercraft:red"}}</pre>
 * {@code base} is the model whose display transforms the blade follows. The optional fields
 * {@code style}, {@code emitter} and {@code length} work as in {@link SaberBladeSpecialRenderer}.
 */
public final class SaberBladeItemModel implements ItemModel {
	private final SaberBladeSpecialRenderer renderer;
	private final ModelRenderProperties properties;
	private final Supplier<Vector3fc[]> extents;
	private final Matrix4fc transformation;

	public SaberBladeItemModel(SaberBladeSpecialRenderer renderer, ModelRenderProperties properties, Matrix4fc transformation) {
		this.renderer = renderer;
		this.properties = properties;
		this.transformation = transformation;
		// Bounds of the fully extended blade, so culling and GUI sizing never cut off the blade.
		this.extents = Suppliers.memoize(() -> {
			Set<Vector3fc> points = new HashSet<>();
			renderer.getExtents(points::add);
			return points.toArray(new Vector3fc[0]);
		});
	}

	@Override
	public void update(
			ItemStackRenderState output,
			ItemStack item,
			ItemModelResolver resolver,
			ItemDisplayContext displayContext,
			@Nullable ClientLevel level,
			@Nullable ItemOwner owner,
			int seed
	) {
		BladeRenderState blade = bladeState(displayContext, owner);

		output.appendModelIdentityElement(this);
		output.appendModelIdentityElement(blade);

		if (blade.isAnimating()) {
			output.setAnimated();
		}

		ItemStackRenderState.LayerRenderState layer = output.newLayer();
		layer.setExtents(this.extents);
		layer.setLocalTransform(this.transformation);
		layer.setupSpecialModel(this.renderer, blade);
		this.properties.applyToLayer(layer, displayContext);
	}

	/**
	 * Held items are animated per entity and hand. Anything without a living owner, for example an
	 * item display entity in a map, shows the full blade.
	 */
	private static BladeRenderState bladeState(ItemDisplayContext displayContext, @Nullable ItemOwner owner) {
		LivingEntity entity = owner != null ? owner.asLivingEntity() : null;

		if (entity == null || !isHand(displayContext)) {
			return BladeRenderState.FULL;
		}

		HumanoidArm arm = displayContext.leftHand() ? HumanoidArm.LEFT : HumanoidArm.RIGHT;
		InteractionHand hand = arm == entity.getMainArm() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
		float partialTick = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
		return SaberTracker.INSTANCE.renderState(entity, hand, partialTick);
	}

	private static boolean isHand(ItemDisplayContext displayContext) {
		return switch (displayContext) {
			case FIRST_PERSON_LEFT_HAND, FIRST_PERSON_RIGHT_HAND, THIRD_PERSON_LEFT_HAND, THIRD_PERSON_RIGHT_HAND -> true;
			default -> false;
		};
	}

	public record Unbaked(
			Identifier base,
			Optional<Transformation> transformation,
			SaberBladeSpecialRenderer.Unbaked blade
	) implements ItemModel.Unbaked {
		public static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(
				instance -> instance.group(
								Identifier.CODEC.fieldOf("base").forGetter(Unbaked::base),
								Transformation.EXTENDED_CODEC.optionalFieldOf("transformation").forGetter(Unbaked::transformation),
								SaberBladeSpecialRenderer.Unbaked.MAP_CODEC.forGetter(Unbaked::blade)
						)
						.apply(instance, Unbaked::new)
		);

		@Override
		public void resolveDependencies(ResolvableModel.Resolver resolver) {
			resolver.markDependency(this.base);
		}

		@Override
		public ItemModel bake(ItemModel.BakingContext context, Matrix4fc transformation) {
			Matrix4fc modelTransform = Transformation.compose(transformation, this.transformation);
			ModelBaker baker = context.blockModelBaker();
			ResolvedModel model = baker.getModel(this.base);
			ModelRenderProperties properties = ModelRenderProperties.fromResolvedModel(baker, model, model.getTopTextureSlots());
			return new SaberBladeItemModel(this.blade.bake(context), properties, modelTransform);
		}

		@Override
		public MapCodec<Unbaked> type() {
			return MAP_CODEC;
		}
	}
}
