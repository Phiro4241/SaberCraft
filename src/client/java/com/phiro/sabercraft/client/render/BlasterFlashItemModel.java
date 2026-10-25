package com.phiro.sabercraft.client.render;

import java.util.Optional;
import java.util.function.Consumer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Transformation;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.ModelRenderProperties;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.client.resources.model.ResolvedModel;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import com.phiro.sabercraft.client.blade.BladeStyle;
import com.phiro.sabercraft.client.blade.BladeStyles;
import com.phiro.sabercraft.client.blaster.BlasterClient;

/**
 * Item model {@code "type": "sabercraft:blaster_flash"} (Phase 11): the muzzle flash of a held
 * blaster. Like the saber blade it knows who holds the item and in which hand, and asks
 * {@link BlasterClient} when that hand last fired. For a moment after each shot it draws a bright
 * star with a short cone of light along the barrel at the muzzle, in the color of the bolts, with
 * the same render types as the blades, so it glows in the dark and with shader packs. It follows
 * the display transform of the blaster model, so it sits at the muzzle in first and third person.
 *
 * <pre>{@code {"type": "sabercraft:blaster_flash", "base": "sabercraft:item/blaster/dl44", "color": "sabercraft:red", "muzzle": [0.5, 0.625, -0.11]}}</pre>
 *
 * The muzzle is in model space (model units / 16), the barrel points along -Z.
 */
public final class BlasterFlashItemModel implements ItemModel {
	private final Renderer renderer;
	private final ModelRenderProperties properties;
	private final Matrix4fc transformation;
	private final Vector3fc[] extents;

	BlasterFlashItemModel(Renderer renderer, ModelRenderProperties properties, Matrix4fc transformation) {
		this.renderer = renderer;
		this.properties = properties;
		this.transformation = transformation;
		Vector3fc muzzle = renderer.muzzle;
		this.extents = new Vector3fc[] {new Vector3f(muzzle), new Vector3f(muzzle.x(), muzzle.y(), muzzle.z() - Renderer.LENGTH)};
	}

	@Override
	public void update(ItemStackRenderState output, ItemStack item, ItemModelResolver resolver, ItemDisplayContext displayContext,
			@Nullable ClientLevel level, @Nullable ItemOwner owner, int seed) {
		float flash = flash(displayContext, owner);
		output.appendModelIdentityElement(this);

		if (flash <= 0.0F) {
			return;
		}

		output.appendModelIdentityElement(flash);
		output.setAnimated();
		ItemStackRenderState.LayerRenderState layer = output.newLayer();
		layer.setExtents(() -> this.extents);
		layer.setLocalTransform(this.transformation);
		layer.setupSpecialModel(this.renderer, new Flash(flash, displayContext.firstPerson()));
		// The display transform of the blaster model, so the flash sits at its muzzle.
		this.properties.applyToLayer(layer, displayContext);
	}

	/** How bright the flash of this hand is right now, 0 = none. */
	private static float flash(ItemDisplayContext displayContext, @Nullable ItemOwner owner) {
		LivingEntity entity = owner != null ? owner.asLivingEntity() : null;

		if (entity == null || !switch (displayContext) {
			case FIRST_PERSON_LEFT_HAND, FIRST_PERSON_RIGHT_HAND, THIRD_PERSON_LEFT_HAND, THIRD_PERSON_RIGHT_HAND -> true;
			default -> false;
		}) {
			return 0.0F;
		}

		HumanoidArm arm = displayContext.leftHand() ? HumanoidArm.LEFT : HumanoidArm.RIGHT;
		InteractionHand hand = arm == entity.getMainArm() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
		return BlasterClient.flash(entity.getId(), hand);
	}

	/** The flash of one frame. */
	public record Flash(float strength, boolean firstPerson) {
	}

	/** Draws the flash. */
	static final class Renderer implements SpecialModelRenderer<Flash> {
		static final float LENGTH = 0.55F;
		private final Identifier color;
		private final Vector3fc muzzle;

		Renderer(Identifier color, Vector3fc muzzle) {
			this.color = color;
			this.muzzle = muzzle;
		}

		@Override
		public void submit(@Nullable Flash flash, PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, int overlayCoords,
				boolean hasFoil, int outlineColor) {
			if (flash == null || flash.strength() <= 0.0F || ShaderCompat.renderingShadowPass()) {
				return;
			}

			BladeStyle style = BladeStyles.get(this.color);
			float s = flash.strength();
			boolean shader = SaberRenderTypes.beams();
			poseStack.pushPose();
			poseStack.translate(this.muzzle.x(), this.muzzle.y(), this.muzzle.z());
			if (flash.firstPerson()) {
				// The first-person blaster is drawn small and far to the side; a larger flash reads as
				// big as the one other players see.
				poseStack.scale(1.8F, 1.8F, 1.8F);
			}

			// The cone is longest at the very start and shrinks as the flash fades.
			float length = LENGTH * (0.45F + 0.55F * s);
			collector.order(0).submitCustomGeometry(poseStack, SaberRenderTypes.haze(flash.firstPerson()),
					(pose, buffer) -> burst(pose, buffer, style.glowColor(), 0.75F * s * (shader ? 1.6F : 1.0F), length, 0.17F * (0.6F + 0.4F * s)));
			collector.order(1).submitCustomGeometry(poseStack, SaberRenderTypes.haze(flash.firstPerson()),
					(pose, buffer) -> burst(pose, buffer, style.coreColor(), 0.95F * s, length * 0.55F, 0.07F));
			collector.order(2).submitCustomGeometry(poseStack, SaberRenderTypes.bloom(flash.firstPerson()),
					(pose, buffer) -> burst(pose, buffer, style.bloomColor(), 0.7F * s, length * 0.8F, 0.12F));
			poseStack.popPose();
		}

		/**
		 * A star of four petals across the barrel and a cross of two tapering planes along it. All
		 * quads are two-sided (the glow pipelines do not cull).
		 */
		private static void burst(PoseStack.Pose pose, VertexConsumer buffer, int rgb, float alpha, float length, float radius) {
			int bright = argb(alpha, rgb);
			int clear = argb(0.0F, rgb);

			// Petals across the barrel, turned 45 degrees between the two crosses.
			for (int i = 0; i < 8; i++) {
				float angle = i * Mth.PI / 4.0F;
				float r = i % 2 == 0 ? radius : radius * 0.6F;
				float cx = Mth.cos(angle) * r;
				float cy = Mth.sin(angle) * r;
				float sx = -Mth.sin(angle) * radius * 0.22F;
				float sy = Mth.cos(angle) * radius * 0.22F;
				BladeMesh.vertex(pose, buffer, sx, sy, 0.0F, bright, 0.0F, 0.0F, -1.0F);
				BladeMesh.vertex(pose, buffer, cx, cy, -radius * 0.2F, clear, 0.0F, 0.0F, -1.0F);
				BladeMesh.vertex(pose, buffer, -sx, -sy, 0.0F, bright, 0.0F, 0.0F, -1.0F);
				BladeMesh.vertex(pose, buffer, 0.0F, 0.0F, 0.0F, bright, 0.0F, 0.0F, -1.0F);
			}

			// The cone of light along -Z.
			float w = radius * 0.5F;
			BladeMesh.vertex(pose, buffer, -w, 0.0F, 0.0F, bright, 0.0F, 1.0F, 0.0F);
			BladeMesh.vertex(pose, buffer, w, 0.0F, 0.0F, bright, 0.0F, 1.0F, 0.0F);
			BladeMesh.vertex(pose, buffer, w * 0.2F, 0.0F, -length, clear, 0.0F, 1.0F, 0.0F);
			BladeMesh.vertex(pose, buffer, -w * 0.2F, 0.0F, -length, clear, 0.0F, 1.0F, 0.0F);
			BladeMesh.vertex(pose, buffer, 0.0F, -w, 0.0F, bright, 1.0F, 0.0F, 0.0F);
			BladeMesh.vertex(pose, buffer, 0.0F, w, 0.0F, bright, 1.0F, 0.0F, 0.0F);
			BladeMesh.vertex(pose, buffer, 0.0F, w * 0.2F, -length, clear, 1.0F, 0.0F, 0.0F);
			BladeMesh.vertex(pose, buffer, 0.0F, -w * 0.2F, -length, clear, 1.0F, 0.0F, 0.0F);
		}

		private static int argb(float alpha, int rgb) {
			return Mth.clamp(Math.round(alpha * 255.0F), 0, 255) << 24 | rgb & 0xFFFFFF;
		}

		@Override
		public void getExtents(Consumer<Vector3fc> output) {
			output.accept(new Vector3f(this.muzzle));
			output.accept(new Vector3f(this.muzzle.x(), this.muzzle.y(), this.muzzle.z() - LENGTH));
		}

		@Override
		public @Nullable Flash extractArgument(ItemStack stack) {
			return null;
		}
	}

	public record Unbaked(Identifier base, Identifier color, Vector3fc muzzle, Optional<Transformation> transformation) implements ItemModel.Unbaked {
		public static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(
				instance -> instance.group(
								Identifier.CODEC.fieldOf("base").forGetter(Unbaked::base),
								Identifier.CODEC.optionalFieldOf("color", BladeStyles.RED.id()).forGetter(Unbaked::color),
								ExtraCodecs.VECTOR3F.fieldOf("muzzle").forGetter(Unbaked::muzzle),
								Transformation.EXTENDED_CODEC.optionalFieldOf("transformation").forGetter(Unbaked::transformation)
						)
						.apply(instance, Unbaked::new)
		);

		@Override
		public void resolveDependencies(ResolvableModel.Resolver resolver) {
			resolver.markDependency(this.base);
		}

		@Override
		public ItemModel bake(ItemModel.BakingContext context, Matrix4fc transformation) {
			ModelBaker baker = context.blockModelBaker();
			ResolvedModel model = baker.getModel(this.base);
			ModelRenderProperties properties = ModelRenderProperties.fromResolvedModel(baker, model, model.getTopTextureSlots());
			return new BlasterFlashItemModel(new Renderer(this.color, this.muzzle), properties, Transformation.compose(transformation, this.transformation));
		}

		@Override
		public MapCodec<Unbaked> type() {
			return MAP_CODEC;
		}
	}
}
