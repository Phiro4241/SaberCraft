package com.phiro.sabercraft.client.effect;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import com.phiro.sabercraft.ability.SaberAbility;
import com.phiro.sabercraft.client.blade.BladeStyle;
import com.phiro.sabercraft.client.blade.BladeStyles;
import com.phiro.sabercraft.client.combat.CombatConfig;
import com.phiro.sabercraft.client.combat.CombatMove;
import com.phiro.sabercraft.client.saber.SaberHandState;
import com.phiro.sabercraft.client.saber.SaberHilts;
import com.phiro.sabercraft.client.saber.SaberTracker;
import com.phiro.sabercraft.client.sound.SaberSounds;
import com.phiro.sabercraft.saber.SaberVariant;

/**
 * Particles and the short sounds of saber hits, blocks and abilities. Client only.
 *
 * <p>Sparks use the vanilla electric spark particle, tinted in the blade's colors, so no
 * particles need to be registered and the effects work on any server. How many particles appear
 * is set by {@code particles} in {@code combat_client.json} (0 = off, 1 = normal, 2 = more), and
 * Minecraft's own particle setting and limits still apply.
 */
public final class SaberEffects {
	/** How far the blade reaches from the eyes, for sparks on walls and the floor. */
	private static final double BLADE_REACH = 2.4;
	/** Ticks between two scorch sounds of the same holder, so a flurry against a wall stays calm. */
	private static final int SCORCH_COOLDOWN = 10;

	private static int lastScorchTick = -100;

	private SaberEffects() {
	}

	/** Called at the strike moment of a move of any saber holder in the client's world. */
	public static void onStrike(LivingEntity entity, InteractionHand hand, CombatMove move) {
		Minecraft client = Minecraft.getInstance();
		ClientLevel level = client.level;

		if (level == null || amount() == 0) {
			return;
		}

		// Does the blade reach a wall or the floor in front? Then it cuts sparks out of it.
		Vec3 eye = entity.getEyePosition();
		Vec3 look = entity.getLookAngle();
		// Low blows (overhead cuts, rising cuts) reach down to the floor in front of the feet.
		Vec3 aim = new Vec3(look.x, Math.min(look.y, -0.35), look.z).normalize();
		BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(aim.scale(BLADE_REACH)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity));

		if (hit.getType() != HitResult.Type.BLOCK) {
			return;
		}

		BladeStyle style = style(entity.getItemInHand(hand));
		Vec3 at = hit.getLocation();
		Vec3 normal = Vec3.atLowerCornerOf(hit.getDirection().getUnitVec3i());
		sparks(level, at, normal, style, 10 * amount(), 0.35, false);
		smoke(level, at, 1 + amount());

		int now = (int) level.getGameTime();

		if (now - lastScorchTick >= SCORCH_COOLDOWN) {
			lastScorchTick = now;
			playAt(at, SaberSounds.SCORCH, 0.55F, entity.getRandom());
		}
	}

	/**
	 * A saber holder hurt {@code target} with a melee hit. Called from the damage event the server
	 * sends to every client nearby, so everybody sees the sparks.
	 */
	public static void onHit(LivingEntity target, LivingEntity attacker) {
		ClientLevel level = Minecraft.getInstance().level;

		if (level == null) {
			return;
		}

		BladeStyle style = style(attacker.getMainHandItem());
		// Where the blade meets the target: on its surface, facing the attacker, at chest height.
		Vec3 toAttacker = attacker.position().subtract(target.position());
		Vec3 flat = new Vec3(toAttacker.x, 0.0, toAttacker.z);
		flat = flat.lengthSqr() < 1.0E-4 ? attacker.getLookAngle().reverse() : flat.normalize();
		double height = Mth.clamp(attacker.getEyeY() - 0.4 - target.getY(), target.getBbHeight() * 0.3, target.getBbHeight() * 0.85);
		Vec3 at = target.position().add(flat.scale(target.getBbWidth() * 0.5)).add(0.0, height, 0.0);

		if (amount() > 0) {
			sparks(level, at, flat, style, 12 * amount(), 0.45, true);
		}

		playAt(at, SaberSounds.HIT, 0.7F, attacker.getRandom());
	}

	/** The stance of {@code entity} blocked or parried a hit: sparks where the blades meet. */
	public static void onBlock(LivingEntity entity, boolean parry) {
		ClientLevel level = Minecraft.getInstance().level;

		if (level == null || amount() == 0 || isOwnFirstPerson(entity)) {
			return;
		}

		Vec3 forward = forward(entity);
		Vec3 at = entity.position().add(forward.scale(0.7)).add(0.0, entity.getBbHeight() * 0.7, 0.0);
		sparks(level, at, forward, style(entity.getMainHandItem()), (parry ? 14 : 7) * amount(), parry ? 0.6 : 0.4, true);
	}

	/** Visual part of an ability. The sounds are played by the ability client. */
	public static void onAbility(LivingEntity entity, SaberAbility ability) {
		ClientLevel level = Minecraft.getInstance().level;

		if (level == null || amount() == 0) {
			return;
		}

		RandomSource random = entity.getRandom();
		Vec3 forward = forward(entity);

		switch (ability) {
			case PUSH -> {
				// A burst of air in a cone in front of the open hand.
				Vec3 origin = entity.getEyePosition().add(forward.scale(0.8)).add(0.0, -0.3, 0.0);

				for (int i = 0; i < 10 * amount(); i++) {
					Vec3 direction = forward.add((random.nextDouble() - 0.5) * 0.8, (random.nextDouble() - 0.5) * 0.5, (random.nextDouble() - 0.5) * 0.8).normalize();
					double speed = 0.35 + random.nextDouble() * 0.3;
					level.addParticle(ParticleTypes.CLOUD, origin.x, origin.y, origin.z, direction.x * speed, direction.y * speed, direction.z * speed);
				}
			}
			case DASH -> {
				// Dust kicked up at the feet.
				for (int i = 0; i < 5 * amount(); i++) {
					double x = entity.getX() + (random.nextDouble() - 0.5) * 0.6;
					double z = entity.getZ() + (random.nextDouble() - 0.5) * 0.6;
					level.addParticle(ParticleTypes.POOF, x, entity.getY() + 0.1, z, -forward.x * 0.05, 0.02, -forward.z * 0.05);
				}
			}
			case GUARD, SPIN -> {
				// The stance and the spin are shown by the pose and the light trail.
			}
		}
	}

	/** A few sparks at the emitter when a blade ignites. Not in your own first-person view. */
	public static void onIgnite(LivingEntity entity, InteractionHand hand) {
		ClientLevel level = Minecraft.getInstance().level;

		if (level == null || amount() == 0 || isOwnFirstPerson(entity)) {
			return;
		}

		HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? entity.getMainArm() : entity.getMainArm().getOpposite();
		float side = arm == HumanoidArm.RIGHT ? -1.0F : 1.0F;
		float yaw = entity.yBodyRot * Mth.DEG_TO_RAD;
		Vec3 right = new Vec3(Mth.cos(yaw), 0.0, Mth.sin(yaw)).scale(side * 0.35);
		Vec3 at = entity.position().add(right).add(forward(entity).scale(0.35)).add(0.0, entity.getBbHeight() * 0.45, 0.0);
		sparks(level, at, new Vec3(0.0, 1.0, 0.0), style(entity.getItemInHand(hand)), 3 * amount(), 0.2, false);
	}

	/**
	 * Colored sparks flying away from {@code at}, spread around {@code normal}.
	 *
	 * @param bright mix in the white core color, for hits and clashes
	 */
	private static void sparks(ClientLevel level, Vec3 at, Vec3 normal, BladeStyle style, int count, double speed, boolean bright) {
		Minecraft client = Minecraft.getInstance();
		RandomSource random = level.getRandom();

		for (int i = 0; i < count; i++) {
			Vec3 direction = normal.add(random.nextGaussian() * 0.6, random.nextGaussian() * 0.6 + 0.2, random.nextGaussian() * 0.6).normalize();
			double velocity = speed * (0.5 + random.nextDouble());
			// The spark provider slows its particles down to a quarter, so start them faster.
			Particle particle = client.particleEngine.createParticle(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z,
					direction.x * velocity * 4.0, direction.y * velocity * 4.0, direction.z * velocity * 4.0);

			if (particle instanceof SingleQuadParticle quad) {
				int rgb = bright && random.nextInt(3) == 0 ? style.coreColor() : random.nextBoolean() ? style.bloomColor() : style.glowColor();
				quad.setColor((rgb >> 16 & 0xFF) / 255.0F, (rgb >> 8 & 0xFF) / 255.0F, (rgb & 0xFF) / 255.0F);
				quad.setLifetime(4 + random.nextInt(5));
				quad.scale(1.4F + random.nextFloat() * 0.8F);
			}
		}
	}

	private static void smoke(ClientLevel level, Vec3 at, int count) {
		RandomSource random = level.getRandom();

		for (int i = 0; i < count; i++) {
			level.addParticle(ParticleTypes.SMOKE, at.x, at.y, at.z, (random.nextDouble() - 0.5) * 0.02, 0.03, (random.nextDouble() - 0.5) * 0.02);
		}
	}

	private static Vec3 forward(Entity entity) {
		float yaw = entity.getYRot() * Mth.DEG_TO_RAD;
		return new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
	}

	private static boolean isOwnFirstPerson(Entity entity) {
		Minecraft client = Minecraft.getInstance();
		return entity == client.player && client.options.getCameraType() == CameraType.FIRST_PERSON;
	}

	/** The blade colors of a saber: its stored color, or the default color of its hilt. */
	public static BladeStyle style(ItemStack stack) {
		Identifier color = BladeStyles.colorOf(stack);

		if (color == null) {
			color = SaberHilts.get(SaberVariant.hiltOf(stack)).defaultColor();
		}

		return BladeStyles.get(color);
	}

	/** Whether {@code entity} holds a lit saber in its main hand. */
	public static boolean hasLitSaber(LivingEntity entity) {
		SaberHandState state = SaberTracker.INSTANCE.state(entity, InteractionHand.MAIN_HAND);
		return state != null && state.isActive() && state.extension(1.0F) > 0.5F;
	}

	private static int amount() {
		return Mth.clamp(CombatConfig.get().particles, 0, 2);
	}

	private static void playAt(Vec3 at, SoundEvent sound, float volume, RandomSource random) {
		Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(
				sound, SoundSource.PLAYERS, volume, 0.92F + random.nextFloat() * 0.16F, random, at.x, at.y, at.z));
	}
}
