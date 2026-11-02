package com.phiro.sabercraft.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.item.ItemStack;

import com.phiro.sabercraft.blaster.BlasterType;
import com.phiro.sabercraft.client.ImmersionConfig;
import com.phiro.sabercraft.client.character.OutfitClient;

/**
 * Small characters (Yoda, Phase 9) are drawn smaller; the hitbox, the camera and the name tag stay
 * where they are. Since Phase 10 the skin itself is replaced (see {@link AbstractClientPlayerMixin}),
 * so nothing about the skin layers is changed here any more. Blasters are aimed with both hands.
 */
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {
	@Inject(method = "scale(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V", at = @At("TAIL"))
	private void sabercraft$characterSize(AvatarRenderState state, PoseStack poseStack, CallbackInfo info) {
		OutfitClient.Look look = OutfitClient.lookOf(state);

		if (look != null && look.scale() != 1.0F) {
			poseStack.scale(look.scale(), look.scale(), look.scale());
		}
	}

	/**
	 * Phase 13: a player with a SaberCraft character or outfit is drawn without armor (unless the
	 * viewer turned {@code character_hide_armor} off), so the character's look and cape stay visible.
	 * Only this frame's drawing data loses the armor pieces: the player still wears them, they still
	 * protect, and the inventory is untouched. An elytra stays, as it is no armor. Each client
	 * decides this for every player it sees from the character it knows for them, so it works the
	 * same in multiplayer.
	 */
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
			at = @At("TAIL"))
	private void sabercraft$hideArmorOfCharacters(Avatar avatar, AvatarRenderState state, float partialTicks, CallbackInfo info) {
		OutfitClient.Look look = OutfitClient.look(avatar.getUUID());

		// A character's cape belongs to the costume: it shows even when the player turned the
		// game's own "Cape" skin part off (SaberCraft's "capes" option switches it off).
		if (look != null && look.cape() != null) {
			state.showCape = true;
		}

		if (!ImmersionConfig.get().characterHideArmor || look == null) {
			return;
		}

		state.headEquipment = ItemStack.EMPTY;
		state.legsEquipment = ItemStack.EMPTY;
		state.feetEquipment = ItemStack.EMPTY;

		if (!state.chestEquipment.has(DataComponents.GLIDER)) {
			state.chestEquipment = ItemStack.EMPTY;
		}
	}

	/** Phase 10: blasters are held with both hands and aimed, like a loaded crossbow. */
	@Inject(method = "getArmPose(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/client/model/HumanoidModel$ArmPose;",
			at = @At("RETURN"), cancellable = true)
	private static void sabercraft$aimBlaster(Avatar avatar, ItemStack stack, InteractionHand hand, CallbackInfoReturnable<HumanoidModel.ArmPose> info) {
		BlasterType type = BlasterType.of(stack);

		if (type != null && !avatar.swinging) {
			info.setReturnValue(HumanoidModel.ArmPose.CROSSBOW_HOLD);
		}
	}
}
