package com.phiro.sabercraft.client.effect;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.DustParticleOptions;
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
import com.phiro.sabercraft.client.render.HitMarks;
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
		// Phase 11: a glowing mark stays where the blade struck.
		HitMarks.add(target, at, style, 0.11F);
	}

	/**
	 * A finishing blow (Phase 11), sent once by the server: a big burst of sparks and a bright mark
	 * where the blade struck, the shock ring and flash, and the finisher sound.
	 */
	public static void onFinisher(LivingEntity attacker, LivingEntity target) {
		ClientLevel level = Minecraft.getInstance().level;

		if (level == null) {
			return;
		}

		BladeStyle style = style(attacker.getMainHandItem());
		Vec3 toAttacker = attacker.position().subtract(target.position());
		Vec3 flat = new Vec3(toAttacker.x, 0.0, toAttacker.z);
		flat = flat.lengthSqr() < 1.0E-4 ? attacker.getLookAngle().reverse() : flat.normalize();
		Vec3 at = target.position().add(flat.scale(target.getBbWidth() * 0.5)).add(0.0, target.getBbHeight() * 0.6, 0.0);

		if (amount() > 0) {
			// Upwards and around, not into the attacker's face.
			sparks(level, at, new Vec3(0.0, 1.0, 0.0), style, 14 * amount(), 0.6, true);
		}

		HitMarks.add(target, at, style, 0.16F);
		HitMarks.finisher(target, style);
		playAt(at, SaberSounds.FINISHER, 1.0F, attacker.getRandom());
	}

	/**
	 * Where the last block or parry met, and whether it was blade on blade. For the tests and for
	 * debugging; {@code null} before the first one.
	 */
	public record Clash(Vec3 at, boolean blades) {
	}

	private static @Nullable Clash lastClash;

	/**
	 * The stance of {@code entity} blocked or parried a hit. Phase 10: the sparks fly where the
	 * blades meet, between the blocker and the attacker, and the blade flashes up. When the
	 * attacker struck with a lit saber too, it is a real clash: both blades flash and the sparks
	 * come in both colors.
	 *
	 * @param attacker who struck, or {@code null} if unknown (a deflected projectile)
	 */
	public static void onBlock(LivingEntity entity, boolean parry, @Nullable Entity attacker) {
		ClientLevel level = Minecraft.getInstance().level;

		if (level == null) {
			return;
		}

		LivingEntity rival = attacker instanceof LivingEntity living && living != entity && hasLitSaber(living) ? living : null;
		flash(entity);

		if (rival != null) {
			flash(rival);
		}

		Vec3 at = contactPoint(entity, attacker);
		lastClash = new Clash(at, rival != null);

		if (amount() == 0 || isOwnFirstPerson(entity) && rival == null) {
			return;
		}

		Vec3 toward = attacker != null && attacker != entity ? flat(attacker.position().subtract(entity.position()), forward(entity)) : forward(entity);
		// The sparks burst out sideways from the point where the blades cross.
		Vec3 side = new Vec3(-toward.z, 0.0, toward.x);
		int count = (parry ? 14 : 7) * amount();
		sparks(level, at, side, style(entity.getMainHandItem()), count / 2, parry ? 0.6 : 0.4, true);
		sparks(level, at, side.reverse(), rival != null ? style(rival.getMainHandItem()) : style(entity.getMainHandItem()), count / 2, parry ? 0.6 : 0.4, true);

		if (rival != null) {
			// Blade on blade: a short, hot burst straight up out of the cross.
			sparks(level, at, new Vec3(0.0, 1.0, 0.0), style(rival.getMainHandItem()), 4 * amount(), 0.5, true);
			smoke(level, at, amount());
		}
	}

	/**
	 * The point where a blocked blow meets the blade: half way between the blocker and the
	 * attacker (at most an arm and a blade's length in front of the blocker), at the height where
	 * the blows meet. Without an attacker, in front of the blocker at chest height.
	 */
	public static Vec3 contactPoint(LivingEntity blocker, @Nullable Entity attacker) {
		Vec3 forward = forward(blocker);
		double height = blocker.getBbHeight();

		if (attacker == null || attacker == blocker) {
			return blocker.position().add(forward.scale(0.7)).add(0.0, height * 0.7, 0.0);
		}

		Vec3 offset = attacker.position().subtract(blocker.position());
		double distance = Math.sqrt(offset.x * offset.x + offset.z * offset.z);
		Vec3 toward = flat(offset, forward);
		double reach = Mth.clamp(distance * 0.5, 0.45, 0.9);
		double meet = (blocker.getEyeY() + attacker.getEyeY()) * 0.5 - 0.45;
		double y = Mth.clamp(meet, blocker.getY() + height * 0.35, blocker.getY() + height * 0.95);
		return new Vec3(blocker.getX() + toward.x * reach, y, blocker.getZ() + toward.z * reach);
	}

	/** The last block or parry seen, see {@link Clash}. */
	public static @Nullable Clash lastClash() {
		return lastClash;
	}

	private static Vec3 flat(Vec3 offset, Vec3 fallback) {
		Vec3 flat = new Vec3(offset.x, 0.0, offset.z);
		return flat.lengthSqr() < 1.0E-4 ? fallback : flat.normalize();
	}

	/** The blade in the main hand flashes up for a moment. */
	private static void flash(LivingEntity entity) {
		SaberHandState state = SaberTracker.INSTANCE.state(entity, InteractionHand.MAIN_HAND);

		if (state != null) {
			state.clash();
		}
	}

	/** A parried attacker staggers: little stars over its head for the moment it is off balance. */
	public static void onUnbalanced(LivingEntity entity) {
		ClientLevel level = Minecraft.getInstance().level;

		if (level == null || amount() == 0 || isOwnFirstPerson(entity)) {
			return;
		}

		double top = entity.getY() + entity.getBbHeight() + 0.2;

		for (int i = 0; i < 4 * amount(); i++) {
			double angle = i * Math.PI * 2.0 / (4 * amount());
			level.addParticle(ParticleTypes.CRIT, entity.getX() + Math.cos(angle) * 0.4, top, entity.getZ() + Math.sin(angle) * 0.4,
					-Math.sin(angle) * 0.1, 0.05, Math.cos(angle) * 0.1);
		}
	}

	private static final DustParticleOptions GRIP_DUST = new DustParticleOptions(0xD8E4FF, 0.7F);

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
			case JUMP -> {
				// A ring of air pressed into the ground at the take-off.
				for (int i = 0; i < 8 * amount(); i++) {
					double angle = i * Math.PI * 2.0 / (8 * amount());
					level.addParticle(ParticleTypes.POOF, entity.getX() + Math.cos(angle) * 0.4, entity.getY() + 0.05, entity.getZ() + Math.sin(angle) * 0.4,
							Math.cos(angle) * 0.18, 0.01, Math.sin(angle) * 0.18);
				}
			}
			case GUARD, SPIN, GRIP, PULL -> {
				// The stance and the spin are shown by the pose and the light trail; grip and pull
				// have their own effects at the target.
			}
		}
	}

	/** A heavy landing, also after a force jump: dust pressed out to the sides. */
	public static void onHeavyLanding(LivingEntity entity) {
		ClientLevel level = Minecraft.getInstance().level;

		if (level == null || amount() == 0) {
			return;
		}

		for (int i = 0; i < 6 * amount(); i++) {
			double angle = i * Math.PI * 2.0 / (6 * amount());
			level.addParticle(ParticleTypes.POOF, entity.getX() + Math.cos(angle) * 0.5, entity.getY() + 0.05, entity.getZ() + Math.sin(angle) * 0.5,
					Math.cos(angle) * 0.12, 0.0, Math.sin(angle) * 0.12);
		}
	}

	/** While a target is held by the force grip: a faint shimmer swirling around it. */
	public static void onGripTick(Entity target, int age) {
		ClientLevel level = Minecraft.getInstance().level;

		if (level == null || amount() == 0 || age % (3 - amount()) != 0) {
			return;
		}

		double angle = age * 0.7;
		double radius = target.getBbWidth() * 0.5 + 0.35;
		double y = target.getY() + target.getBbHeight() * (0.2 + 0.6 * ((age * 0.13) % 1.0));
		level.addParticle(GRIP_DUST, target.getX() + Math.cos(angle) * radius, y, target.getZ() + Math.sin(angle) * radius, 0.0, 0.01, 0.0);
		level.addParticle(GRIP_DUST, target.getX() - Math.cos(angle) * radius, y, target.getZ() - Math.sin(angle) * radius, 0.0, 0.01, 0.0);
	}

	/** The force pull: a short stream of air from the target towards the open hand. */
	public static void onPull(LivingEntity holder, Entity target) {
		ClientLevel level = Minecraft.getInstance().level;

		if (level == null || amount() == 0) {
			return;
		}

		Vec3 from = target.getBoundingBox().getCenter();
		Vec3 to = holder.getEyePosition().add(0.0, -0.4, 0.0);
		Vec3 step = to.subtract(from).scale(1.0 / (6 * amount()));

		for (int i = 0; i < 6 * amount(); i++) {
			Vec3 at = from.add(step.scale(i));
			level.addParticle(ParticleTypes.CLOUD, at.x, at.y, at.z, step.x * 0.4, step.y * 0.4, step.z * 0.4);
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
