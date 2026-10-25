package com.phiro.sabercraft.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import com.phiro.sabercraft.blaster.BlasterType;
import com.phiro.sabercraft.client.blaster.BlasterClient;
import com.phiro.sabercraft.client.combat.FirstPersonPose;
import com.phiro.sabercraft.client.combat.SaberCombat;
import com.phiro.sabercraft.client.saber.SaberProfiles;

/**
 * First person: a held saber is placed by the fighting animation instead of vanilla's item
 * position and swing (see {@link SaberCombat#firstPerson}). Everything else, like eating, bows,
 * maps or an empty hand, stays vanilla.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {
	@Shadow
	public abstract void renderItem(
			LivingEntity mob, ItemStack itemStack, ItemDisplayContext type, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords
	);

	@Inject(method = "submitArmWithItem", at = @At("HEAD"), cancellable = true)
	private void sabercraft$submitSaber(
			AbstractClientPlayer player,
			float frameInterp,
			float xRot,
			InteractionHand hand,
			float attack,
			ItemStack itemStack,
			float inverseArmHeight,
			PoseStack poseStack,
			SubmitNodeCollector submitNodeCollector,
			int lightCoords,
			CallbackInfo ci
	) {
		if (player.isScoping() || !SaberProfiles.isSaber(itemStack) || player.isAutoSpinAttack()) {
			return;
		}

		if (player.isUsingItem() && player.getUseItemRemainingTicks() > 0 && player.getUsedItemHand() == hand) {
			return;
		}

		FirstPersonPose pose = SaberCombat.firstPerson(player, hand, frameInterp);

		if (pose == null) {
			return;
		}

		HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
		boolean right = arm == HumanoidArm.RIGHT;
		poseStack.pushPose();
		pose.apply(poseStack, right, inverseArmHeight);
		this.renderItem(
				player, itemStack, right ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
				poseStack, submitNodeCollector, lightCoords
		);
		poseStack.popPose();
		ci.cancel();
	}

	/**
	 * Vanilla lowers the held item after every attack until the attack strength has recovered. A
	 * saber stays up, its own moves show the attack. Swapping items still lowers it.
	 */
	@Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getItemSwapScale(F)F"))
	private float sabercraft$keepSaberRaised(LocalPlayer player, float a) {
		float scale = player.getItemSwapScale(a);
		return SaberCombat.firstPerson(player, InteractionHand.MAIN_HAND, 1.0F) != null && SaberProfiles.isSaber(player.getMainHandItem()) ? 1.0F : scale;
	}

	/**
	 * Phase 10: a short kick back and up of the blaster after each shot; Phase 11: turned aside to
	 * vent while overheated. Balanced by the pop below.
	 */
	@Inject(method = "submitArmWithItem", at = @At("HEAD"))
	private void sabercraft$blasterRecoilPush(
			AbstractClientPlayer player, float frameInterp, float xRot, InteractionHand hand, float attack, ItemStack itemStack,
			float inverseArmHeight, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, CallbackInfo ci
	) {
		if (BlasterType.of(itemStack) != null) {
			float kick = BlasterClient.recoil(hand);
			// Phase 11: while overheated the blaster is turned aside and down to vent.
			float vent = BlasterClient.venting();
			vent = vent * vent * (3.0F - 2.0F * vent);
			boolean right = (hand == InteractionHand.MAIN_HAND) == (player.getMainArm() == HumanoidArm.RIGHT);
			poseStack.pushPose();
			poseStack.translate(0.0F, 0.02F * kick - 0.1F * vent, 0.09F * kick);
			poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(7.0F * kick - 18.0F * vent));
			poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees((right ? 32.0F : -32.0F) * vent));
		}
	}

	@Inject(method = "submitArmWithItem", at = @At("RETURN"))
	private void sabercraft$blasterRecoilPop(
			AbstractClientPlayer player, float frameInterp, float xRot, InteractionHand hand, float attack, ItemStack itemStack,
			float inverseArmHeight, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, CallbackInfo ci
	) {
		if (BlasterType.of(itemStack) != null) {
			poseStack.popPose();
		}
	}
}
