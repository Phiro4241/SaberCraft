package com.phiro.sabercraft.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import com.phiro.sabercraft.interior.ProxyShapes;
import com.phiro.sabercraft.interior.ShipInteriors;

/**
 * Phase 16: in the ship interiors some invisible barriers collide with a shape instead of a full
 * block (the curved walls of the Falcon, slim seats), see {@link ProxyShapes}. On the server and
 * on modded clients alike, so movement agrees on both sides.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
abstract class BlockStateBaseMixin {
	@Inject(method = "getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;",
			at = @At("HEAD"), cancellable = true)
	private void sabercraft$shapedBarrier(BlockGetter level, BlockPos pos, CollisionContext context, CallbackInfoReturnable<VoxelShape> info) {
		this.sabercraft$shape(level, pos, info);
	}

	@Inject(method = "getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/phys/shapes/VoxelShape;",
			at = @At("HEAD"), cancellable = true)
	private void sabercraft$shapedBarrierNoContext(BlockGetter level, BlockPos pos, CallbackInfoReturnable<VoxelShape> info) {
		this.sabercraft$shape(level, pos, info);
	}

	/**
	 * Phase 18: the outline (what the crosshair hits) is the same shape: a full invisible block
	 * round a seat or a console took the click before (the couch, the hyperdrive lever). The slim
	 * columns where the crew stand have no outline at all (a click there is meant for them).
	 */
	@Inject(method = "getShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;",
			at = @At("HEAD"), cancellable = true)
	private void sabercraft$shapedOutline(BlockGetter level, BlockPos pos, CollisionContext context, CallbackInfoReturnable<VoxelShape> info) {
		this.sabercraft$shape(level, pos, info, true);
	}

	@Inject(method = "getShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/phys/shapes/VoxelShape;",
			at = @At("HEAD"), cancellable = true)
	private void sabercraft$shapedOutlineNoContext(BlockGetter level, BlockPos pos, CallbackInfoReturnable<VoxelShape> info) {
		this.sabercraft$shape(level, pos, info, true);
	}

	private void sabercraft$shape(BlockGetter level, BlockPos pos, CallbackInfoReturnable<VoxelShape> info) {
		this.sabercraft$shape(level, pos, info, false);
	}

	private void sabercraft$shape(BlockGetter level, BlockPos pos, CallbackInfoReturnable<VoxelShape> info, boolean outline) {
		if (level instanceof Level world && ((BlockState) (Object) this).is(Blocks.BARRIER) && ShipInteriors.isInterior(world)) {
			VoxelShape shape = ProxyShapes.at(pos, outline);

			if (shape != null) {
				info.setReturnValue(shape);
			}
		}
	}
}
