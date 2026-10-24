package com.phiro.sabercraft.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import com.phiro.sabercraft.client.combat.CombatConfig;
import com.phiro.sabercraft.client.effect.SaberEffects;

/**
 * Hit sparks from the damage events the server sends to every nearby client, and hiding the
 * vanilla sweep particle of saber attacks, which would look like a sword slash next to the blade.
 */
@Mixin(ClientPacketListener.class)
abstract class ClientPacketListenerMixin {
	@Shadow
	private ClientLevel level;

	@Inject(method = "handleDamageEvent", at = @At("TAIL"))
	private void sabercraft$hitSparks(ClientboundDamageEventPacket packet, CallbackInfo ci) {
		if (this.level == null || !(this.level.getEntity(packet.entityId()) instanceof LivingEntity target)) {
			return;
		}

		DamageSource source = packet.getSource(this.level);

		// Only melee hits: the direct entity is the attacker itself, not an arrow.
		if (source.getEntity() instanceof LivingEntity attacker && source.getDirectEntity() == attacker
				&& attacker != target && SaberEffects.hasLitSaber(attacker)) {
			SaberEffects.onHit(target, attacker);
		}
	}

	@Inject(
			method = "handleParticleEvent",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V", shift = At.Shift.AFTER),
			cancellable = true
	)
	private void sabercraft$hideSweep(ClientboundLevelParticlesPacket packet, CallbackInfo ci) {
		if (this.level == null || !CombatConfig.get().hideVanillaSweep || packet.getParticle().getType() != ParticleTypes.SWEEP_ATTACK) {
			return;
		}

		// The sweep is spawned in front of the attacker, about one block away.
		for (Player player : this.level.players()) {
			if (player.distanceToSqr(packet.getX(), packet.getY(), packet.getZ()) < 9.0 && SaberEffects.hasLitSaber(player)) {
				ci.cancel();
				return;
			}
		}
	}
}
