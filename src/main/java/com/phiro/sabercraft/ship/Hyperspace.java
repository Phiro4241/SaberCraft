package com.phiro.sabercraft.ship;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.phiro.sabercraft.ability.AbilityConfig;
import com.phiro.sabercraft.network.HyperspacePayload;

/**
 * Phase 14: the hyperspace jump of a starship, decided and run by the server alone.
 *
 * <p>The pilot presses the hyperspace key; the server checks everything before anything happens:
 * hyperspace is on, the ship has a hyperdrive, the pilot owns and flies it, it is in the air (not
 * landed, not gliding down without energy, not just crashed), it has enough energy, and its jump
 * is not cooling down. Then it looks for the target: straight ahead along the ship's heading, at
 * its height, as far as {@code hyperspace_distance} (shorter for some ships). The ship's box is
 * swept along the whole way in steps; the first block in the way, an unloaded chunk or the world
 * border ends the free way, and the target stays a safety margin before it. So no jump goes
 * through a wall, a mountain or a building. A free way shorter than {@link #MIN_DISTANCE} blocks
 * means no jump.
 *
 * <p>The jump itself: {@value #CHARGE_TICKS} ticks the drive spools up (the ship holds its course,
 * light gathers around it), then the way is checked once more and the ship is moved to the target
 * in one step, with its pilot. Everyone who sees the ship (and its pilot) gets the same stages
 * with the same positions ({@link HyperspacePayload}), so every client shows the same effect: the
 * streak and flash where it leaves, the flash where it arrives, and the tunnel of light for those
 * on board. The jump uses {@code hyperspace_energy_share} of the energy and the ship cannot jump
 * again for {@code hyperspace_cooldown_seconds}.
 */
public final class Hyperspace {
	/** Ticks the hyperdrive spools up before the jump. */
	public static final int CHARGE_TICKS = 30;
	/** Ticks the pilot sees the tunnel of light after the jump. */
	public static final int TUNNEL_TICKS = 24;
	/** The shortest jump worth making, blocks. */
	public static final double MIN_DISTANCE = 24.0;
	/** The target stays this far before the first obstacle, blocks (plus half the ship). */
	public static final double MARGIN = 6.0;
	/** Speed while the drive spools up and after the jump, share of the top speed. */
	static final double CRUISE = 0.5;

	/** When each player's ship can jump again (game time), kept while the server runs. */
	private static final Map<UUID, Long> READY_AT = new HashMap<>();
	private static int jumps;
	private static int aborted;

	/** A jump in progress: from where to where, and how many ticks it has been charging. */
	static final class Jump {
		final Vec3 from;
		final Vec3 to;
		int age;

		Jump(Vec3 from, Vec3 to) {
			this.from = from;
			this.to = to;
		}
	}

	private Hyperspace() {
	}

	/**
	 * The pilot asked for a jump. Returns the message for them: why it cannot jump, or that the
	 * drive spools up.
	 */
	public static Component request(ServerPlayer pilot) {
		AbilityConfig config = AbilityConfig.get();

		if (!(pilot.getVehicle() instanceof ShipEntity ship) || !ship.owner().equals(pilot.getUUID()) || !Starships.isCurrent(ship)) {
			return Component.translatableWithFallback("sabercraft.hyperspace.not_flying", "Hyperspace only while flying your own ship.");
		}

		ServerLevel level = (ServerLevel) ship.level();
		String problem = problem(ship, config, level.getGameTime());

		if (problem != null) {
			fail(ship, level);
			return message(problem, ship, level.getGameTime());
		}

		Vec3 target = target(ship, config);

		if (target == null) {
			fail(ship, level);
			return Component.translatableWithFallback("sabercraft.hyperspace.blocked", "The way ahead is blocked. Turn to open space.");
		}

		ship.jump = new Jump(ship.position(), target);
		ship.useEnergy(config.flightTicks(ship.ship()) * config.hyperspaceEnergyShare);
		READY_AT.put(ship.owner(), level.getGameTime() + config.hyperspaceCooldownSeconds * 20L);
		send(ship, HyperspacePayload.CHARGE, ship.position(), target, CHARGE_TICKS);
		level.playSound(null, ship.getX(), ship.getY(), ship.getZ(), ShipEntity.sound("ship.hyperspace_charge"), SoundSource.NEUTRAL,
				ship.ship().capital() ? 2.0F : 1.0F, 1.0F);
		return Component.translatableWithFallback("sabercraft.hyperspace.charging", "Hyperdrive charging... %s blocks", Math.round(target.distanceTo(ship.position())));
	}

	/** Why the ship cannot jump now, as a message key, or {@code null}. */
	static @Nullable String problem(ShipEntity ship, AbilityConfig config, long now) {
		if (!config.hyperspaceEnabled || !config.shipsEnabled) {
			return "disabled";
		} else if (!ShipFeatures.of(ship.ship()).hyperspace()) {
			return "no_drive";
		} else if (ship.jump != null) {
			return "busy";
		} else if (ship.wrecked() || ship.getHealth() < ship.getMaxHealth() * 0.1F) {
			return "damaged";
		} else if (ship.depleted() || ship.justCrashed()) {
			return "falling";
		} else if (ship.onGround() || !ship.ship().capital() && !ship.level().noCollision(ship, ship.getBoundingBox().move(0.0, -2.0, 0.0))) {
			return "landed";
		} else if (config.shipEnergy && ship.energy() >= 0.0F && ship.energy() < config.flightTicks(ship.ship()) * config.hyperspaceEnergyShare) {
			return "energy";
		} else if (now < READY_AT.getOrDefault(ship.owner(), 0L)) {
			return "cooldown";
		}

		return null;
	}

	private static Component message(String problem, ShipEntity ship, long now) {
		return switch (problem) {
			case "disabled" -> Component.translatableWithFallback("sabercraft.hyperspace.disabled", "Hyperspace is turned off on this server.");
			case "no_drive" -> Component.translatableWithFallback("sabercraft.hyperspace.no_drive", "This ship has no hyperdrive.");
			case "busy" -> Component.translatableWithFallback("sabercraft.hyperspace.busy", "The hyperdrive is already running.");
			case "damaged" -> Component.translatableWithFallback("sabercraft.hyperspace.damaged", "The hull is too damaged for a jump.");
			case "falling" -> Component.translatableWithFallback("sabercraft.hyperspace.falling", "Not while the ship is going down.");
			case "landed" -> Component.translatableWithFallback("sabercraft.hyperspace.landed", "Take off first: hyperspace only in flight.");
			case "energy" -> Component.translatableWithFallback("sabercraft.hyperspace.energy", "Not enough energy for a jump.");
			default -> Component.translatableWithFallback("sabercraft.hyperspace.cooldown", "The hyperdrive is cooling down: %s s",
					(READY_AT.getOrDefault(ship.owner(), 0L) - now + 19L) / 20L);
		};
	}

	/** Seconds until a player's ship can jump again, 0 if it can. */
	public static int cooldownSeconds(UUID owner, long now) {
		long left = READY_AT.getOrDefault(owner, 0L) - now;
		return left <= 0L ? 0 : (int) ((left + 19L) / 20L);
	}

	/**
	 * The target of a jump: straight ahead along the heading, at the ship's height, as far as the
	 * way is free (see the class comment), or {@code null} if that is too short.
	 */
	static @Nullable Vec3 target(ShipEntity ship, AbilityConfig config) {
		double range = config.hyperspaceDistance * ShipFeatures.of(ship.ship()).jumpScale();
		Vec3 forward = Vec3.directionFromRotation(0.0F, ship.getYRot());
		double free = freeWay(ship, forward, range);
		double half = ship.getBbWidth() * 0.5 + (ship.ship().capital() ? ship.ship().handling().modelRadius() * 0.5 : 0.0);
		double distance = Math.min(range, free - MARGIN - half);

		if (distance < MIN_DISTANCE) {
			return null;
		}

		return ship.position().add(forward.scale(distance));
	}

	/** How far the ship's box can move along {@code direction} without hitting anything, up to {@code range}. */
	static double freeWay(ShipEntity ship, Vec3 direction, double range) {
		ServerLevel level = (ServerLevel) ship.level();
		AABB box = ship.getBoundingBox();

		// A capital ship's model reaches far beyond its box: sweep a box as long and wide as its middle.
		if (ship.ship().capital()) {
			double reach = ship.ship().handling().modelRadius() * 0.5;
			box = box.inflate(reach, 0.0, reach).expandTowards(0.0, ship.ship().height(), 0.0);
		}

		double step = Math.max(0.5, Math.min(2.0, ship.getBbWidth() * 0.5));

		for (double d = step; d <= range + 1.0E-6; d += step) {
			AABB moved = box.move(direction.scale(d));
			BlockPos centre = BlockPos.containing(moved.getCenter());

			if (!level.isLoaded(centre) || !level.isLoaded(BlockPos.containing(moved.minX, moved.minY, moved.minZ))
					|| !level.isLoaded(BlockPos.containing(moved.maxX, moved.maxY, moved.maxZ))
					|| !level.getWorldBorder().isWithinBounds(moved) || !level.noCollision(ship, moved)
					|| moved.minY < level.getMinY() || moved.maxY > level.getMaxY() + 64) {
				return d - step;
			}
		}

		return range;
	}

	/** Runs a jump in progress, every tick of the ship. */
	static void tick(ShipEntity ship) {
		Jump jump = ship.jump;

		if (jump == null) {
			return;
		}

		ServerLevel level = (ServerLevel) ship.level();
		jump.age++;
		ShipType type = ship.ship();
		double cruise = type.maxSpeed() * AbilityConfig.get().shipSpeed * CRUISE;

		if (ship.pilot() == null || ship.wrecked()) {
			abort(ship, level);
			return;
		}

		if (jump.age < CHARGE_TICKS) {
			ship.holdForJump(cruise);

			// Light gathering around the ship.
			if (jump.age % 3 == 0) {
				double r = type.width() * 0.6;
				level.sendParticles(ParticleTypes.END_ROD, ship.getX(), ship.getY() + type.height() * 0.5, ship.getZ(), 4, r, type.height() * 0.4, r,
						0.02);
			}

			return;
		}

		// Checked again right before the jump: the way may have changed while it charged.
		Vec3 forward = jump.to.subtract(ship.position()).horizontal();
		double distance = forward.length();
		double half = ship.getBbWidth() * 0.5 + (type.capital() ? type.handling().modelRadius() * 0.5 : 0.0);

		if (distance < 1.0 || freeWay(ship, forward.normalize(), distance) < distance) {
			// The ship moved a little while charging; a way blocked now calls the jump off.
			Vec3 again = target(ship, AbilityConfig.get());

			if (again == null || again.distanceTo(ship.position()) < MIN_DISTANCE || freeWay(ship, again.subtract(ship.position()).normalize(),
					again.distanceTo(ship.position())) < again.distanceTo(ship.position()) - half) {
				abort(ship, level);

				if (ship.pilot() != null) {
					ship.pilot().sendOverlayMessage(Component.translatableWithFallback("sabercraft.hyperspace.aborted",
							"Jump called off: something is in the way."));
				}

				return;
			}

			jump = new Jump(jump.from, again);
		}

		Vec3 from = ship.position();
		Vec3 to = new Vec3(jump.to.x, from.y, jump.to.z);
		ship.jump = null;
		jumps++;

		// Where it leaves: a flash, a streak of light along the way and the boom.
		Vec3 dir = to.subtract(from).normalize();
		double mid = type.height() * 0.5;

		for (int i = 0; i < 24; i++) {
			Vec3 at = from.add(dir.scale(i * 1.5)).add(0.0, mid, 0.0);
			level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 2, 0.2, 0.2, 0.2, 0.0);
		}

		level.sendParticles(ParticleTypes.EXPLOSION, from.x, from.y + mid, from.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.playSound(null, from.x, from.y, from.z, ShipEntity.sound("ship.hyperspace_jump"), SoundSource.NEUTRAL, type.capital() ? 3.0F : 1.5F,
				type.capital() ? 0.75F : 1.0F);
		send(ship, HyperspacePayload.JUMP, from, to, TUNNEL_TICKS);
		ship.arrive(to, cruise);

		// Where it arrives: a flash and a soft thump, for those who wait there.
		level.sendParticles(ParticleTypes.EXPLOSION, to.x, to.y + mid, to.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.sendParticles(ParticleTypes.END_ROD, to.x, to.y + mid, to.z, 20, type.width() * 0.4, 0.4, type.width() * 0.4, 0.15);
		level.playSound(null, to.x, to.y, to.z, ShipEntity.sound("ship.hyperspace_arrive"), SoundSource.NEUTRAL, type.capital() ? 2.5F : 1.2F,
				1.0F);
		send(ship, HyperspacePayload.ARRIVE, from, to, TUNNEL_TICKS);
	}

	private static void abort(ShipEntity ship, ServerLevel level) {
		Jump jump = ship.jump;
		ship.jump = null;
		aborted++;
		// Half the wait when nothing happened.
		READY_AT.put(ship.owner(), level.getGameTime() + AbilityConfig.get().hyperspaceCooldownSeconds * 10L);
		fail(ship, level);

		if (jump != null) {
			send(ship, HyperspacePayload.ABORT, jump.from, jump.to, 0);
		}
	}

	private static void fail(ShipEntity ship, ServerLevel level) {
		level.playSound(null, ship.getX(), ship.getY(), ship.getZ(), ShipEntity.sound("ship.hyperspace_fail"), SoundSource.NEUTRAL, 0.8F, 1.0F);
	}

	/** Tells everyone who sees the ship, and its pilot, about a stage of the jump. */
	private static void send(ShipEntity ship, byte stage, Vec3 from, Vec3 to, int ticks) {
		HyperspacePayload payload = new HyperspacePayload(ship.getId(), stage, from, to, (short) Mth.clamp(ticks, 0, Short.MAX_VALUE));
		java.util.Set<ServerPlayer> told = new java.util.HashSet<>(PlayerLookup.tracking(ship));

		if (ship.pilot() != null) {
			told.add(ship.pilot());
		}

		// Players near the target see the arrival even if they cannot see the ship yet.
		if (stage != HyperspacePayload.CHARGE) {
			told.addAll(PlayerLookup.around((ServerLevel) ship.level(), to, 128.0));
		}

		for (ServerPlayer viewer : told) {
			if (ServerPlayNetworking.canSend(viewer, HyperspacePayload.TYPE)) {
				ServerPlayNetworking.send(viewer, payload);
			}
		}
	}

	/** Forgets the cooldowns (server stops, tests). */
	public static void reset() {
		READY_AT.clear();
	}

	/** Only for tests: how many jumps were made and how many were called off. */
	public static int jumps() {
		return jumps;
	}

	public static int aborted() {
		return aborted;
	}

	/** Only for tests: the target a jump of this ship would have now, or {@code null}. */
	public static @Nullable Vec3 targetForTest(ShipEntity ship) {
		return target(ship, AbilityConfig.get());
	}

	/** Only for tests: whether the ship is charging its hyperdrive now. */
	public static boolean charging(ShipEntity ship) {
		return ship.jump != null;
	}
}
