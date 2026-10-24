package com.phiro.sabercraft.client.character;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.PlayerModelType;

import com.phiro.sabercraft.SaberCraft;

/**
 * Draws a player's outfit as a second, slightly larger body over the skin, like armor. It moves
 * with every pose and SaberCraft animation of the player, as it uses the same model.
 */
public class OutfitLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	public static final ModelLayerLocation WIDE = new ModelLayerLocation(SaberCraft.id("outfit"), "main");
	public static final ModelLayerLocation SLIM = new ModelLayerLocation(SaberCraft.id("outfit_slim"), "main");
	/** Just over the skin; the outer parts (hood, robe) another 0.25 out, inside armor. */
	private static final CubeDeformation INFLATE = new CubeDeformation(0.05F);

	private final PlayerModel wide;
	private final PlayerModel slim;

	public OutfitLayer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer, EntityRendererProvider.Context context) {
		super(renderer);
		this.wide = new OutfitModel(context.bakeLayer(WIDE), false);
		this.slim = new OutfitModel(context.bakeLayer(SLIM), true);
	}

	/**
	 * The player model for outfits: its outer parts (hood, robe, plates) are always drawn, even
	 * though the skin's own outer layers are switched off under an outfit.
	 */
	private static final class OutfitModel extends PlayerModel {
		OutfitModel(ModelPart root, boolean slim) {
			super(root, slim);
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			boolean show = !state.isSpectator;
			this.hat.visible = show;
			this.jacket.visible = show;
			this.leftSleeve.visible = show;
			this.rightSleeve.visible = show;
			this.leftPants.visible = show;
			this.rightPants.visible = show;
		}
	}

	public static LayerDefinition createLayer(boolean slim) {
		return LayerDefinition.create(PlayerModel.createMesh(INFLATE, slim), 64, 64);
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, AvatarRenderState state, float yRot, float xRot) {
		if (state.isInvisible) {
			return;
		}

		OutfitClient.Look look = lookOf(state);

		if (look == null) {
			return;
		}

		PlayerModel model = state.skin.model() == PlayerModelType.SLIM ? this.slim : this.wide;
		int overlay = LivingEntityRenderer.getOverlayCoords(state, 0.0F);
		collector.submitModel(model, state, poseStack, RenderTypes.armorCutoutNoCull(look.texture()), lightCoords, overlay, state.outlineColor, null);
	}

	/** The look for a render state, found through the entity id it was made from. */
	public static OutfitClient.Look lookOf(AvatarRenderState state) {
		Minecraft client = Minecraft.getInstance();
		Entity entity = client.level == null ? null : client.level.getEntity(state.id);
		return entity == null ? null : OutfitClient.look(entity.getUUID());
	}
}
