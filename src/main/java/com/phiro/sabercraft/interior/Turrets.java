package com.phiro.sabercraft.interior;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.ability.AbilityConfig;
import com.phiro.sabercraft.blaster.BlasterBolt;
import com.phiro.sabercraft.blaster.BlasterType;
import com.phiro.sabercraft.interior.InteriorPlan.Seat;
import com.phiro.sabercraft.ship.ShipEntity;
import com.phiro.sabercraft.ship.ShipFeatures;
import com.phiro.sabercraft.ship.ShipType;
import com.phiro.sabercraft.ship.Starships;

/**
 * Phase 16: the Falcon's gun turrets. Whoever sits in a gunner's seat aims with the view (the
 * guns on the dome turn with it, for everyone aboard) and fires with the attack key: the two
 * guns in turn, a bolt every {@value #INTERVAL} ticks. Long bursts heat the guns; at
 * {@value #OVERHEAT} they stop for {@value #COOL_DOWN} ticks. The bolts fly out of the guns of
 * the interior's hull (seen through the dome and from outside), and, if the ship is out in the
 * world, out of the same turret of the ship there too, in the same direction relative to its nose.
 * The upper turret cannot aim below the rim, the lower one not above it.
 */
public final class Turrets {
	static final int INTERVAL = 4;
	static final int OVERHEAT = 24;
	static final int COOL_DOWN = 60;
	/** The guns' pivot over the middle of the saucer (plot space), above and below. */
	public static final double PIVOT_X = FalconInterior.CX;
	public static final double PIVOT_Z = FalconInterior.CZ;
	public static final double TOP_Y = 14.2;
	public static final double BOTTOM_Y = 1.8;
	/** The guns stand this far to either side of the pivot, their muzzles this far along the aim. */
	public static final double SIDE = 2.4;
	public static final double BARREL = 5.4;
	/** How far the guns can aim past the rim (degrees). */
	public static final float RIM = 8.0F;

	private static final Map<UUID, Gun> GUNS = new HashMap<>();
	private static int shots;

	/** A gunner's state: when they last fired, how hot the guns are, which gun is next. */
	private static final class Gun {
		long last = -100L;
		int heat;
		long lockedUntil;
		int next;
	}

	private Turrets() {
	}

	/** Whether a gunner's seat is the upper turret's. */
	public static boolean upper(Seat seat) {
		return seat.cell().getY() > FalconInterior.FLOOR;
	}

	/** The pitch a gunner can aim at in that turret. */
	public static float clampPitch(boolean upper, float pitch) {
		return upper ? Mth.clamp(pitch, -88.0F, RIM) : Mth.clamp(pitch, -RIM, 88.0F);
	}

	/** Where a gun's muzzle is (plot space) for that aim; {@code gun} -1 left or 1 right. */
	public static Vec3 muzzle(boolean upper, float yaw, float pitch, int gun) {
		Vec3 aim = Vec3.directionFromRotation(clampPitch(upper, pitch), yaw);
		Vec3 right = Vec3.directionFromRotation(0.0F, yaw + 90.0F);
		return new Vec3(PIVOT_X, upper ? TOP_Y : BOTTOM_Y, PIVOT_Z).add(right.scale(gun * SIDE)).add(aim.scale(BARREL));
	}

	/** The gunner pulled the trigger. */
	public static void fire(ServerPlayer player) {
		Seat seat = Seats.seat(player);
		BlockPos origin = Seats.origin(player);

		if (seat == null || origin == null || !Seat.GUNNER.equals(seat.role()) || !AbilityConfig.get().shipsEnabled) {
			return;
		}

		ServerLevel level = (ServerLevel) player.level();
		long now = level.getGameTime();
		Gun gun = GUNS.computeIfAbsent(player.getUUID(), unused -> new Gun());

		if (now < gun.lockedUntil || now - gun.last < INTERVAL) {
			return;
		}

		// Cooling while not firing: one step of heat every two intervals.
		gun.heat = Math.max(0, gun.heat - (int) ((now - gun.last) / (INTERVAL * 2L)));
		gun.last = now;
		gun.heat++;
		int side = gun.next == 0 ? -1 : 1;
		gun.next = 1 - gun.next;

		if (gun.heat >= OVERHEAT) {
			gun.heat = 0;
			gun.lockedUntil = now + COOL_DOWN;
			player.sendOverlayMessage(Component.translatableWithFallback("sabercraft.interior.guns_hot", "The guns are overheated. Wait a moment."));
		}

		boolean upper = upper(seat);
		float yaw = player.getYRot();
		float pitch = clampPitch(upper, player.getXRot());
		Vec3 aim = Vec3.directionFromRotation(pitch, yaw);
		BlasterType type = BlasterType.byId(SaberCraft.id(ShipType.FALCON.laser())).orElse(BlasterType.XWING);
		Vec3 muzzle = muzzle(upper, yaw, pitch, side).add(origin.getX(), origin.getY(), origin.getZ());
		bolt(level, player, type, muzzle, aim);
		level.playSound(null, muzzle.x, muzzle.y, muzzle.z, ShipInteriors.sound("ship.falcon.laser"), SoundSource.PLAYERS, 0.7F,
				0.95F + level.getRandom().nextFloat() * 0.1F);
		shots++;

		// The ship out in the world fires the same turret, turned the same way relative to its nose.
		UUID owner = ShipInteriors.ownerOf(player);
		ShipEntity ship = owner != null ? Starships.shipOf(owner) : null;

		if (ship != null && ship.ship() == ShipType.FALCON && !ship.wrecked() && ship.level() instanceof ServerLevel shipLevel) {
			int cannon = upper ? 0 : 1;
			Vec3 local = ship.ship().cannons().get(cannon);
			Vec3 at = ShipFeatures.toWorld(local, ship.position(), ship.getYRot(), 0.0F, 0.0);
			// In the interior the nose points north (yaw 180).
			Vec3 way = Vec3.directionFromRotation(pitch, ship.getYRot() + yaw - 180.0F);
			bolt(shipLevel, ship, type, at.add(way.scale(1.5)), way);
			Starships.shot(ship, cannon);
			shipLevel.playSound(null, at.x, at.y, at.z, ShipInteriors.sound("ship.falcon.laser"), SoundSource.PLAYERS, 0.9F, 1.0F);
		}
	}

	private static void bolt(ServerLevel level, net.minecraft.world.entity.LivingEntity shooter, BlasterType type, Vec3 from, Vec3 way) {
		BlasterBolt bolt = new BlasterBolt(level, shooter, type, type.damage() * AbilityConfig.get().shipLaserDamage, false, type.knockback());
		bolt.setPos(from.x, from.y, from.z);
		bolt.shoot(way.x, way.y, way.z, type.speed(), type.spread() * 0.5F);
		level.addFreshEntity(bolt);
	}

	static void tick(MinecraftServer server) {
		if (server.getTickCount() % 200 == 0) {
			GUNS.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
		}
	}

	/** Only for tests: how many turret shots were fired since the server started. */
	public static int shotsForTest() {
		return shots;
	}
}
