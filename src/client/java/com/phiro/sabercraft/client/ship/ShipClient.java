package com.phiro.sabercraft.client.ship;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.client.ImmersionConfig;
import com.phiro.sabercraft.network.ShipCommandPayload;
import com.phiro.sabercraft.network.ShipEnergyPayload;
import com.phiro.sabercraft.network.ShipFirePayload;
import com.phiro.sabercraft.network.ShipShotPayload;
import com.phiro.sabercraft.network.ShipStatusPayload;
import com.phiro.sabercraft.ship.ShipMarker;
import com.phiro.sabercraft.ship.ShipType;

/**
 * The client side of the starships (Phase 11): how each ship in sight moves its parts (banking in
 * turns, the S-foils opening off the ground), its engine sound, the muzzle flashes, and for the
 * pilot the trigger, the flight display and the camera behind the ship. The ship itself is moved
 * by the server only.
 */
public final class ShipClient {
	/** How far a ship banks into a turn, degrees per degree of turn per tick. */
	private static final float BANK = 7.0F;
	private static final float MAX_BANK = 45.0F;

	private static final Map<Integer, Visual> VISUALS = new HashMap<>();
	private static final Map<Integer, ShipEngineSound> ENGINES = new HashMap<>();
	private static @Nullable ShipStatusPayload status;
	private static long lastTriggerTick = -1L;
	private static long lastShotMillis;
	private static int shotsSeen;
	private static @Nullable CameraType cameraBefore;
	private static boolean wasPiloting;
	/** Phase 13: the energy of the ship the local player flies, as the server last told it. */
	private static @Nullable ShipEnergyPayload energy;
	private static int flybys;

	private ShipClient() {
	}

	/** What one ship looks like now and a tick ago, for smooth drawing. */
	public static final class Visual {
		float roll;
		float rollO;
		float foils;
		float foilsO;
		float speed;
		long flashUntil;
		int flashCannon = -1;
		boolean seen;
		/** Phase 13: change of speed over the last tick, blocks per tick. */
		float accel;
		/** Phase 13: the radar dish of the Falcon, degrees, and its landing legs, 0 in to 1 out. */
		float dish;
		float dishO;
		float legs;
		float legsO;
		/** Phase 13: distance to the camera last tick, and ticks until the next fly-by sound. */
		double cameraDistance = -1.0;
		int flybyCooldown;

		public float roll(float partialTicks) {
			return Mth.lerp(partialTicks, this.rollO, this.roll);
		}

		public float foils(float partialTicks) {
			return Mth.lerp(partialTicks, this.foilsO, this.foils);
		}

		/** Blocks per tick, as the client saw it last tick. */
		public float speed() {
			return this.speed;
		}

		/** Phase 13: blocks per tick gained (positive) or lost over the last tick. */
		public float accel() {
			return this.accel;
		}

		public float dish(float partialTicks) {
			return Mth.lerp(partialTicks, this.dishO, this.dish);
		}

		public float legs(float partialTicks) {
			return Mth.lerp(partialTicks, this.legsO, this.legs);
		}

		/** The cannon that just fired, or -1. */
		public int flashing() {
			return Util.getMillis() < this.flashUntil ? this.flashCannon : -1;
		}
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(ShipStatusPayload.TYPE, (payload, context) -> status = payload);
		ClientPlayNetworking.registerGlobalReceiver(ShipShotPayload.TYPE, (payload, context) -> onShot(context.client(), payload));
		ClientPlayNetworking.registerGlobalReceiver(ShipEnergyPayload.TYPE, (payload, context) -> energy = payload);
		ClientTickEvents.END_CLIENT_TICK.register(ShipClient::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			status = null;
			energy = null;
			VISUALS.clear();
			ENGINES.values().forEach(ShipEngineSound::finish);
			ENGINES.clear();
			wasPiloting = false;
			cameraBefore = null;
		});
		HudElementRegistry.addLast(SaberCraft.id("ship_hud"), new ShipHud());
	}

	/** The look of a ship, created the first time it is drawn. */
	public static Visual visual(Entity ship) {
		Visual visual = VISUALS.computeIfAbsent(ship.getId(), id -> new Visual());

		if (!visual.seen) {
			visual.seen = true;

			// The server sends the position every tick; following it in one step instead of three
			// keeps the ship (and the pilot's view) close to where it really is at speed.
			if (ship instanceof LivingEntity living) {
				living.getInterpolation().setInterpolationLength(1);
			}

			visual.foils = visual.foilsO = landed(ship) ? 0.0F : 1.0F;
			visual.legs = visual.legsO = visual.foils < 0.5F ? 1.0F : 0.0F;
		}

		return visual;
	}

	/** Standing on (or just above) the ground: the S-foils close. */
	private static boolean landed(Entity ship) {
		return ship.onGround() || !ship.level().noCollision(ship, ship.getBoundingBox().move(0.0, -0.3, 0.0));
	}

	private static void tick(Minecraft client) {
		ClientLevel level = client.level;

		if (level == null) {
			return;
		}

		for (Iterator<Map.Entry<Integer, Visual>> it = VISUALS.entrySet().iterator(); it.hasNext();) {
			Map.Entry<Integer, Visual> entry = it.next();
			Entity ship = level.getEntity(entry.getKey());

			if (ship == null || ship.isRemoved() || ShipMarker.of(ship) == null) {
				it.remove();
				ShipEngineSound engine = ENGINES.remove(entry.getKey());

				if (engine != null) {
					engine.finish();
				}

				continue;
			}

			Visual visual = entry.getValue();
			ShipType type = ShipMarker.of(ship);
			float turn = Mth.wrapDegrees(ship.getYRot() - ship.yRotO);
			float bank = type.handling().bank();
			visual.rollO = visual.roll;
			visual.roll += (Mth.clamp(turn * BANK * bank, -MAX_BANK * bank, MAX_BANK * bank) - visual.roll) * 0.2F;
			visual.foilsO = visual.foils;
			float target = landed(ship) ? 0.0F : 1.0F;
			visual.foils += Mth.clamp(target - visual.foils, -0.06F, 0.06F);
			float speed = (float) ship.position().subtract(ship.xo, ship.yo, ship.zo).length();
			visual.accel = speed - visual.speed;
			visual.speed = speed;
			// Phase 13: the Falcon's dish turns slowly; its legs come out close to the ground.
			visual.dishO = visual.dish;
			visual.dish = (visual.dish + 2.5F) % 360.0F;
			visual.dishO = visual.dish < visual.dishO ? visual.dishO - 360.0F : visual.dishO;
			visual.legsO = visual.legs;
			boolean low = landed(ship) || !level.noCollision(ship, ship.getBoundingBox().move(0.0, -2.5, 0.0));
			visual.legs += Mth.clamp((low && speed < 0.5F ? 1.0F : 0.0F) - visual.legs, -0.05F, 0.05F);
			flyby(client, level, ship, type, visual);

			if ((visual.foilsO < 0.5F) != (visual.foils < 0.5F) && ShipMarker.of(ship) != null && ShipMarker.of(ship).foils()) {
				level.playLocalSound(ship.getX(), ship.getY(), ship.getZ(), ShipEngineSound.event("ship.foils"), SoundSource.NEUTRAL,
						0.7F, 1.0F, false);
			}

			ShipEngineSound engine = ENGINES.get(entry.getKey());

			if ((engine == null || engine.isStopped()) && ship.position().distanceTo(client.gameRenderer.mainCamera().position()) < 64.0 + type.handling().modelRadius()) {
				engine = new ShipEngineSound(ship, ShipMarker.of(ship));
				ENGINES.put(entry.getKey(), engine);
				client.getSoundManager().play(engine);
			}
		}

		turnWithShip(client);
		camera(client);
	}

	/**
	 * The pilot turns with the ship, as in a boat: the view keeps pointing over the nose. Only the
	 * new rotation is changed, so the view turns smoothly between ticks.
	 */
	private static void turnWithShip(Minecraft client) {
		Entity ship = piloting(client);

		if (ship != null && client.player != null) {
			float turn = Mth.wrapDegrees(ship.getYRot() - ship.yRotO);
			client.player.setYRot(client.player.getYRot() + turn);
			client.player.setYHeadRot(client.player.getYHeadRot() + turn);
		}
	}

	/** Climbing in puts the camera behind the ship; climbing out puts it back. */
	private static void camera(Minecraft client) {
		boolean flying = piloting(client) != null;

		if (flying && !wasPiloting && ImmersionConfig.get().shipThirdPerson) {
			cameraBefore = client.options.getCameraType();
			client.options.setCameraType(CameraType.THIRD_PERSON_BACK);
		} else if (!flying && wasPiloting && cameraBefore != null) {
			client.options.setCameraType(cameraBefore);
			cameraBefore = null;
		}

		wasPiloting = flying;
	}

	/**
	 * Phase 13: a ship that rushes past the camera close and fast makes a short whoosh, once per
	 * pass. Not for the ship the local player flies.
	 */
	private static void flyby(Minecraft client, ClientLevel level, Entity ship, ShipType type, Visual visual) {
		if (visual.flybyCooldown > 0) {
			visual.flybyCooldown--;
		}

		double distance = ship.position().distanceTo(client.gameRenderer.mainCamera().position());
		double before = visual.cameraDistance;
		visual.cameraDistance = distance;

		if (before < 0.0 || client.player != null && client.player.getVehicle() == ship || visual.flybyCooldown > 0) {
			return;
		}

		double close = 8.0 + type.handling().modelRadius();

		// Closest point of the pass: it came nearer and now moves away, fast.
		if (distance > before && before < close && visual.speed > type.maxSpeed() * 0.45) {
			visual.flybyCooldown = 40;
			flybys++;
			float volume = (float) Math.min(1.0, 0.5 + visual.speed / type.maxSpeed() * 0.4);
			level.playLocalSound(ship.getX(), ship.getY(), ship.getZ(), ShipEngineSound.event(type.capital() ? "ship.flyby_heavy" : "ship.flyby"),
					SoundSource.NEUTRAL, volume, 0.9F + level.getRandom().nextFloat() * 0.2F, false);
		}
	}

	/**
	 * Phase 13: whether an entity is a player sitting in a starship who is not drawn (see
	 * {@code EntityRenderDispatcherMixin}).
	 */
	public static boolean hidesPilot(Entity entity) {
		return entity instanceof Player && ImmersionConfig.get().shipHidePilot && entity.getVehicle() != null
				&& ShipMarker.of(entity.getVehicle()) != null;
	}

	/**
	 * Phase 13: how far the camera stays behind a pilot's ship. The game caps its camera distance
	 * attribute at 32 blocks; a ship that needs more (the Star Destroyer) gets its own distance.
	 */
	public static float cameraDistance(@Nullable Entity entity, float distance) {
		Entity vehicle = entity != null ? entity.getVehicle() : null;
		ShipType type = vehicle != null ? ShipMarker.of(vehicle) : null;
		return type == null ? distance : Math.max(distance, type.handling().camera());
	}

	/** Phase 13: the energy of the ship the local player flies, or {@code null} if not known. */
	public static @Nullable ShipEnergyPayload energy(Entity ship) {
		ShipEnergyPayload known = energy;
		return known != null && known.entityId() == ship.getId() ? known : null;
	}

	/** Only for tests: how many fly-by sounds played. */
	public static int flybys() {
		return flybys;
	}

	/** The ship the local player flies, or {@code null}. */
	public static @Nullable Entity piloting(Minecraft client) {
		Entity vehicle = client.player != null ? client.player.getVehicle() : null;
		return vehicle != null && ShipMarker.of(vehicle) != null ? vehicle : null;
	}

	/**
	 * The attack key while flying: fires the lasers instead of hitting or digging. Sends at most
	 * one request per tick; the server keeps the rate of fire. Returns whether it took the key.
	 */
	public static boolean trigger(Minecraft client) {
		if (piloting(client) == null) {
			return false;
		}

		long tick = client.level != null ? client.level.getGameTime() : 0L;

		if (tick != lastTriggerTick && ClientPlayNetworking.canSend(ShipFirePayload.TYPE)) {
			lastTriggerTick = tick;
			ClientPlayNetworking.send(ShipFirePayload.INSTANCE);
		}

		return true;
	}

	private static void onShot(Minecraft client, ShipShotPayload payload) {
		ClientLevel level = client.level;
		Entity ship = level != null ? level.getEntity(payload.entityId()) : null;
		ShipType type = ShipMarker.of(ship);
		shotsSeen++;

		if (ship == null || type == null || payload.cannon() < 0 || payload.cannon() >= type.cannons().size()) {
			return;
		}

		Visual visual = visual(ship);
		visual.flashCannon = payload.cannon();
		visual.flashUntil = Util.getMillis() + 90L;

		if (client.player != null && client.player.getVehicle() == ship) {
			lastShotMillis = Util.getMillis();
		}

		// The muzzle flash: a burst of light in the bolt's color (red X-Wing, green TIE) and sparks.
		Vec3 muzzle = cannon(ship, type, payload.cannon(), 1.0F);
		boolean green = !type.laser().equals("xwing");
		level.addParticle(new DustParticleOptions(green ? 0x30FF50 : 0xFF3020, 2.5F), muzzle.x, muzzle.y, muzzle.z, 0.0, 0.0, 0.0);
		level.addParticle(new DustParticleOptions(green ? 0xB0FFC0 : 0xFFB0A0, 1.2F), muzzle.x, muzzle.y, muzzle.z, 0.0, 0.0, 0.0);

		for (int i = 0; i < 4; i++) {
			level.addParticle(ParticleTypes.ELECTRIC_SPARK, muzzle.x, muzzle.y, muzzle.z, (level.getRandom().nextFloat() - 0.5) * 0.3,
					(level.getRandom().nextFloat() - 0.5) * 0.3, (level.getRandom().nextFloat() - 0.5) * 0.3);
		}
	}

	/** Where a cannon of the ship is in the world, as the server computes it. */
	public static Vec3 cannon(Entity ship, ShipType type, int index, float partialTicks) {
		Vec3 local = type.cannons().get(index);
		float yaw = ship.getYRot(partialTicks);
		float pitch = ship.getXRot(partialTicks);
		Vec3 forward = Vec3.directionFromRotation(pitch, yaw);
		Vec3 right = Vec3.directionFromRotation(0.0F, yaw + 90.0F);
		Vec3 up = right.cross(forward);
		return ship.getPosition(partialTicks).add(right.scale(local.x)).add(up.scale(local.y)).add(forward.scale(local.z));
	}

	/** Sends a button of the hangar to the server, which checks it. */
	public static void command(String action, ShipType ship) {
		if (serverSupports()) {
			ClientPlayNetworking.send(new ShipCommandPayload(action, ship.id()));
		}
	}

	/** Whether the server has starships at all (it has SaberCraft). */
	public static boolean serverSupports() {
		return ClientPlayNetworking.canSend(ShipCommandPayload.TYPE);
	}

	/** What the server last said about the player's ship, or {@code null}. */
	public static @Nullable ShipStatusPayload status() {
		return status;
	}

	/** Milliseconds since the local pilot's lasers last fired. */
	static long sinceShot() {
		return Util.getMillis() - lastShotMillis;
	}

	/** Only for tests: how many laser shots this client was told about. */
	public static int shotsSeen() {
		return shotsSeen;
	}

	/** Only for tests: the volume of a ship's engine now, or -1 if it plays none. */
	public static float engineVolume(Entity ship) {
		ShipEngineSound engine = ENGINES.get(ship.getId());
		return engine != null && !engine.isStopped() ? engine.currentVolume() : -1.0F;
	}

	/** Only for tests: how many ship engines are playing. */
	public static int enginesPlaying() {
		return (int) ENGINES.values().stream().filter(engine -> !engine.isStopped()).count();
	}
}
