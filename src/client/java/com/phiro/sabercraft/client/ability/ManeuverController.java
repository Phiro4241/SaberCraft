package com.phiro.sabercraft.client.ability;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import com.phiro.sabercraft.ability.DodgeDirection;
import com.phiro.sabercraft.ability.SaberManeuver;
import com.phiro.sabercraft.client.combat.CombatState;
import com.phiro.sabercraft.client.saber.SaberTracker;
import com.phiro.sabercraft.saber.SaberVariant;

/**
 * Moves the local player through a dodge or lunge. Player movement runs on the client in
 * Minecraft, so this is where the motion of a {@link SaberManeuver} happens: a set speed along
 * the dodge direction each tick, applied through the normal movement code, so blocks, slopes and
 * ledges work as for walking. The server only decides whether the dodge may happen and how fast
 * it is ({@code dodge_speed}).
 *
 * <p>The motion stops early when the player runs into a wall, dies, leaves the world or puts the
 * saber away, and the animation then settles into the resting pose.
 */
public final class ManeuverController {
	private static @Nullable SaberManeuver maneuver;
	private static Vec3 direction = Vec3.ZERO;
	private static int age;
	private static int blockedTicks;
	private static @Nullable Level level;
	private static float speedScale = 1.0F;

	private ManeuverController() {
	}

	/** The server's dodge speed, sent on join. */
	static void setSpeedScale(float scale) {
		speedScale = scale;
	}

	static void reset() {
		maneuver = null;
		speedScale = 1.0F;
	}

	public static boolean isActive() {
		return maneuver != null;
	}

	/** Starts moving the local player. */
	static void start(LocalPlayer player, SaberManeuver next, DodgeDirection towards) {
		maneuver = next;
		direction = towards.vector(player.getYRot());
		age = 0;
		blockedTicks = 0;
		level = player.level();

		if (next.hop() > 0.0F && player.onGround()) {
			Vec3 motion = player.getDeltaMovement();
			player.setDeltaMovement(motion.x, Math.max(motion.y, next.hop()), motion.z);
		}

		push(player);
	}

	/** Runs at the start of every client tick, before the player moves. */
	static void tick(Minecraft client) {
		SaberManeuver current = maneuver;
		LocalPlayer player = client.player;

		if (current == null) {
			return;
		}

		if (player == null || !player.isAlive() || player.level() != level || !SaberVariant.canHold(player.getMainHandItem())) {
			finish(player, true);
			return;
		}

		if (client.isPaused()) {
			return;
		}

		// The last tick moved the player into something: the dodge ends there.
		if (age > 0 && current.speed(age - 1) > 0.15F && player.horizontalCollision) {
			blockedTicks++;
		}

		age++;

		if (blockedTicks >= 2) {
			finish(player, true);
			return;
		}

		if (age >= current.duration()) {
			finish(player, false);
			return;
		}

		push(player);
	}

	private static void push(LocalPlayer player) {
		SaberManeuver current = maneuver;

		if (current == null) {
			return;
		}

		float speed = current.speed(age) * speedScale;

		if (speed > 0.0F) {
			Vec3 motion = player.getDeltaMovement();
			player.setDeltaMovement(direction.x * speed, motion.y, direction.z * speed);
		}
	}

	private static void finish(@Nullable LocalPlayer player, boolean early) {
		maneuver = null;

		if (early && player != null) {
			CombatState combat = SaberTracker.INSTANCE.combat(player);

			if (combat != null) {
				combat.stop(SaberTracker.INSTANCE.time(0.0F));
			}
		}
	}
}
