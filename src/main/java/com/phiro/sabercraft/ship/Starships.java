package com.phiro.sabercraft.ship;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.ability.AbilityConfig;
import com.phiro.sabercraft.network.ShipCommandPayload;
import com.phiro.sabercraft.network.ShipFirePayload;
import com.phiro.sabercraft.network.ShipShotPayload;
import com.phiro.sabercraft.network.ShipStatusPayload;

/**
 * The starships of all players, on the server (Phase 11).
 *
 * <p>Each player has at most one ship out. Like the droids (see
 * {@link com.phiro.sabercraft.droid.Droids}), the ship entity is never saved with the world; where
 * it is parked is saved with its owner (an attachment that survives death). Twice a second every
 * online player's ship is checked: when the owner comes within {@value #SPAWN_DISTANCE} blocks of
 * the parked spot in that dimension, the ship appears there; when they go far away (and nobody
 * flies it), it disappears and stays parked. A ship entity this class does not know (any more)
 * removes itself. So a ship never exists twice and ships never pile up in the world.
 *
 * <p>A player who logs out in the cockpit is put back into it when they log in again: vanilla
 * would save the ship with the player and load it as a plain pig, so the mod leaves it out of the
 * player data (see {@code ServerPlayerMixin}) and spawns the real ship instead.
 *
 * <p>Everything a client asks for is checked here: the ship exists and can be flown, ships are
 * enabled, only the owner flies and fires their ship, and a player can send one command every
 * {@value #COMMAND_INTERVAL} ticks. The rate of fire is kept by the ship itself.
 */
public final class Starships {
	/** Ticks between two commands of the same player. */
	static final int COMMAND_INTERVAL = 4;
	/** The ship appears when its owner is this close to where it is parked. */
	static final int SPAWN_DISTANCE = 160;
	/** A parked ship disappears (and stays parked) when its owner is this far away. */
	static final int DESPAWN_DISTANCE = 192;
	/** How close the owner has to be to climb in from the hangar. */
	static final double BOARD_RANGE_SQR = 24.0 * 24.0;

	/**
	 * Where a player's ship is parked.
	 *
	 * @param ship      its {@link ShipType#id()}
	 * @param dimension the level it is in
	 * @param piloted   whether its owner sat in it (when they logged out)
	 */
	public record Parked(String ship, String dimension, double x, double y, double z, float yaw, boolean piloted) {
		public static final Codec<Parked> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.STRING.fieldOf("ship").forGetter(Parked::ship),
				Codec.STRING.fieldOf("dimension").forGetter(Parked::dimension),
				Codec.DOUBLE.fieldOf("x").forGetter(Parked::x),
				Codec.DOUBLE.fieldOf("y").forGetter(Parked::y),
				Codec.DOUBLE.fieldOf("z").forGetter(Parked::z),
				Codec.FLOAT.fieldOf("yaw").forGetter(Parked::yaw),
				Codec.BOOL.optionalFieldOf("piloted", false).forGetter(Parked::piloted)
		).apply(instance, Parked::new));

		Vec3 position() {
			return new Vec3(this.x, this.y, this.z);
		}
	}

	public static final AttachmentType<Parked> PARKED = AttachmentRegistry.create(SaberCraft.id("ship"),
			builder -> builder.persistent(Parked.CODEC).copyOnDeath());

	private static final Map<UUID, ShipEntity> LIVE = new HashMap<>();
	private static final Map<UUID, Long> LAST_COMMAND = new HashMap<>();

	private Starships() {
	}

	public static void init() {
		ServerPlayNetworking.registerGlobalReceiver(ShipCommandPayload.TYPE, (payload, context) -> command(context.player(), payload.action(), payload.ship()));
		ServerPlayNetworking.registerGlobalReceiver(ShipFirePayload.TYPE, (payload, context) -> fire(context.player()));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 10 == 0) {
				tick(server);
			}
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			rejoin(handler.getPlayer());
			sendStatus(handler.getPlayer());
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			ServerPlayer player = handler.getPlayer();
			ShipEntity ship = LIVE.remove(player.getUUID());
			LAST_COMMAND.remove(player.getUUID());

			if (ship != null && !ship.isRemoved()) {
				park(player, ship);
				// Not in the middle of the player leaving the level: on the next tick.
				server.execute(() -> {
					if (!ship.isRemoved()) {
						ship.discard();
					}
				});
			}
		});
		// A pig marked as a ship that is no ship of this class (a left-over, a summoned copy).
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Pig pig && !(pig instanceof ShipEntity) && ShipMarker.of(pig) != null) {
				pig.discard();
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			LIVE.values().forEach(ShipEntity::discard);
			LIVE.clear();
			LAST_COMMAND.clear();
		});
	}

	/** The ship of a player, if it is in the world now. */
	public static @Nullable ShipEntity ship(ServerPlayer player) {
		ShipEntity ship = LIVE.get(player.getUUID());
		return ship != null && !ship.isRemoved() ? ship : null;
	}

	/** Where the player's ship is parked, if they have one out. */
	public static @Nullable Parked parked(ServerPlayer player) {
		return player.getAttached(PARKED);
	}

	/** Whether this ship is the one its owner has out (and not an old copy). */
	static boolean isCurrent(ShipEntity ship) {
		return LIVE.get(ship.owner()) == ship;
	}

	private static String dimension(ServerLevel level) {
		return level.dimension().identifier().toString();
	}

	private static void tick(MinecraftServer server) {
		boolean enabled = AbilityConfig.get().shipsEnabled;

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Parked parked = parked(player);
			ShipEntity ship = ship(player);

			if (ship == null) {
				// Gone with its chunk, or never there: forget the old entity.
				LIVE.remove(player.getUUID());
			}

			if (!enabled || parked == null || ShipType.byId(parked.ship()).filter(ShipType::available).isEmpty()) {
				if (ship != null) {
					ship.ejectPassengers();
					ship.discard();
					LIVE.remove(player.getUUID());
					sendStatus(player);
				}

				continue;
			}

			if (ship != null) {
				boolean flying = ship.pilot() == player;

				if (!flying && (ship.level() != player.level() || ship.distanceToSqr(player) > (double) DESPAWN_DISTANCE * DESPAWN_DISTANCE)) {
					// Far away: it stays parked where it is and disappears until the owner is back.
					park(player, ship);
					ship.ejectPassengers();
					ship.discard();
					LIVE.remove(player.getUUID());
					sendStatus(player);
				} else {
					park(player, ship);
				}
			} else if (player.isAlive() && !player.isSpectator()
					&& parked.dimension().equals(dimension(player.level()))
					&& player.position().distanceToSqr(parked.position()) < (double) SPAWN_DISTANCE * SPAWN_DISTANCE
					&& player.level().isLoaded(BlockPos.containing(parked.position()))) {
				if (spawn(player, ShipType.byId(parked.ship()).get(), parked.position(), parked.yaw()) != null) {
					sendStatus(player);
				}
			}
		}
	}

	/** Saves where the ship is now with its owner. */
	private static void park(ServerPlayer player, ShipEntity ship) {
		Parked old = parked(player);
		boolean piloted = ship.pilot() == player;
		Parked now = new Parked(ship.ship().id(), dimension((ServerLevel) ship.level()), ship.getX(), ship.getY(), ship.getZ(), ship.getYRot(), piloted);

		if (old == null || old.piloted() != piloted || !old.dimension().equals(now.dimension()) || old.position().distanceToSqr(now.position()) > 0.25
				|| Math.abs(Mth.wrapDegrees(old.yaw() - now.yaw())) > 5.0F) {
			player.setAttached(PARKED, now);
		}
	}

	/** Puts the player's ship into the world at that spot. */
	private static @Nullable ShipEntity spawn(ServerPlayer player, ShipType type, Vec3 pos, float yaw) {
		ServerLevel level = player.level();
		ShipEntity ship = new ShipEntity(level, type, player);
		ship.snapTo(pos.x, pos.y, pos.z, yaw, 0.0F);
		ship.setYHeadRot(yaw);
		ship.setYBodyRot(yaw);
		LIVE.put(player.getUUID(), ship);

		if (!level.addFreshEntity(ship)) {
			LIVE.remove(player.getUUID());
			return null;
		}

		return ship;
	}

	/** Logged out in the cockpit: back into the ship, where it was. */
	private static void rejoin(ServerPlayer player) {
		Parked parked = parked(player);

		if (parked == null || !parked.piloted() || !AbilityConfig.get().shipsEnabled || !parked.dimension().equals(dimension(player.level()))) {
			return;
		}

		ShipType type = ShipType.byId(parked.ship()).filter(ShipType::available).orElse(null);
		ShipEntity ship = type != null && ship(player) == null ? spawn(player, type, parked.position(), parked.yaw()) : null;

		if (ship != null) {
			player.startRiding(ship, true, true);
			park(player, ship);
		}
	}

	/** A ship died (only /kill and the void can do that): its owner has to call it again. */
	static void died(ShipEntity ship) {
		if (!isCurrent(ship) || ship.level().getServer() == null) {
			return;
		}

		LIVE.remove(ship.owner());
		ServerPlayer owner = ship.level().getServer().getPlayerList().getPlayer(ship.owner());

		if (owner != null) {
			owner.removeAttached(PARKED);
			sendStatus(owner);
		}
	}

	/**
	 * A command from the hangar or {@code /sabercraft ship}. Returns the message for the player, or
	 * {@code null} if the command was ignored (too fast, unknown).
	 */
	public static @Nullable Component command(ServerPlayer player, String action, String id) {
		long now = player.level().getGameTime();
		Long last = LAST_COMMAND.get(player.getUUID());

		if (last != null && now - last < COMMAND_INTERVAL && now >= last) {
			return null;
		}

		ShipType type = ShipType.byId(id).orElse(null);

		if (type == null || !player.isAlive() || player.isSpectator()) {
			return null;
		}

		LAST_COMMAND.put(player.getUUID(), now);
		Component message = run(player, action, type);

		if (message != null) {
			player.sendOverlayMessage(message);
		}

		sendStatus(player);
		return message;
	}

	private static @Nullable Component run(ServerPlayer player, String action, ShipType type) {
		Component name = Component.translatableWithFallback(type.translationKey(), "X-Wing");

		if (!AbilityConfig.get().shipsEnabled && !action.equals("dismiss")) {
			return Component.translatableWithFallback("sabercraft.ship.disabled", "Starships are turned off on this server.");
		}

		if (!type.available()) {
			return Component.translatableWithFallback("sabercraft.ship.planned", "%s is still being built. It comes in a later version.", name);
		}

		switch (action) {
			case "call" -> {
				return call(player, type, name);
			}
			case "board" -> {
				ShipEntity ship = ship(player);

				if (ship == null || ship.level() != player.level() || ship.distanceToSqr(player) > BOARD_RANGE_SQR) {
					Component called = call(player, type, name);

					if (ship(player) == null) {
						return called;
					}

					ship = ship(player);
				}

				return board(player, ship);
			}
			case "dismiss" -> {
				ShipEntity ship = ship(player);
				Parked parked = parked(player);

				if (ship == null && parked == null) {
					return null;
				}

				if (ship != null) {
					ship.ejectPassengers();
					ship.level().playSound(null, ship.getX(), ship.getY(), ship.getZ(), ShipEntity.sound("ship.foils"), SoundSource.NEUTRAL, 0.8F, 0.7F);
					ship.discard();
					LIVE.remove(player.getUUID());
				}

				player.removeAttached(PARKED);
				return Component.translatableWithFallback("sabercraft.ship.dismissed", "%s flew back to the hangar.", name);
			}
			default -> {
				return null;
			}
		}
	}

	/** Brings the ship (or a new one) down a few blocks in front of the player. */
	private static Component call(ServerPlayer player, ShipType type, Component name) {
		Vec3 spot = landingSpot(player, type);

		if (spot == null) {
			return Component.translatableWithFallback("sabercraft.ship.no_room", "There is no room here for %s.", name);
		}

		float yaw = player.getYRot();
		ShipEntity ship = ship(player);

		if (ship != null && ship.ship() != type) {
			ship.ejectPassengers();
			ship.discard();
			LIVE.remove(player.getUUID());
			ship = null;
		}

		if (ship != null && ship.level() == player.level()) {
			if (ship.pilot() != null) {
				return Component.translatableWithFallback("sabercraft.ship.in_use", "Someone is flying %s.", name);
			}

			ship.snapTo(spot.x, spot.y, spot.z, yaw, 0.0F);
			ship.stop();
		} else {
			if (ship != null) {
				ship.discard();
				LIVE.remove(player.getUUID());
			}

			ship = spawn(player, type, spot, yaw);

			if (ship == null) {
				return Component.translatableWithFallback("sabercraft.ship.no_room", "There is no room here for %s.", name);
			}
		}

		park(player, ship);
		ship.level().playSound(null, ship.getX(), ship.getY(), ship.getZ(), ShipEntity.sound("ship.foils"), SoundSource.NEUTRAL, 1.0F, 1.0F);
		return Component.translatableWithFallback("sabercraft.ship.called", "%s is ready. Right-click it to climb in.", name);
	}

	/** A free spot in front of the player, big enough for the ship, or {@code null}. */
	private static @Nullable Vec3 landingSpot(ServerPlayer player, ShipType type) {
		ServerLevel level = player.level();
		Vec3 forward = Vec3.directionFromRotation(0.0F, player.getYRot());
		Vec3 side = new Vec3(-forward.z, 0.0, forward.x);
		double half = type.width() / 2.0;

		for (double distance : new double[] {6.0, 5.0, 8.0, 4.0, 10.0}) {
			for (double sideways : new double[] {0.0, 3.0, -3.0}) {
				for (int up = 0; up <= 4; up++) {
					Vec3 spot = player.position().add(forward.scale(distance)).add(side.scale(sideways)).add(0.0, up, 0.0);
					AABB box = new AABB(spot.x - half, spot.y, spot.z - half, spot.x + half, spot.y + type.height(), spot.z + half);

					if (level.isLoaded(BlockPos.containing(spot)) && level.getWorldBorder().isWithinBounds(box) && level.noCollision(box)) {
						return spot;
					}
				}
			}
		}

		return null;
	}

	/** The owner climbs into their ship. Called from a right-click on the ship and the hangar. */
	static Component board(ServerPlayer player, ShipEntity ship) {
		Component name = Component.translatableWithFallback(ship.ship().translationKey(), "X-Wing");

		if (!ship.owner().equals(player.getUUID())) {
			ServerPlayer owner = player.level().getServer().getPlayerList().getPlayer(ship.owner());
			Component message = Component.translatableWithFallback("sabercraft.ship.not_yours", "This %s belongs to %s.", name,
					owner != null ? owner.getDisplayName() : Component.literal("?"));
			player.sendOverlayMessage(message);
			return message;
		}

		if (!AbilityConfig.get().shipsEnabled) {
			Component message = Component.translatableWithFallback("sabercraft.ship.disabled", "Starships are turned off on this server.");
			player.sendOverlayMessage(message);
			return message;
		}

		if (ship.pilot() == player) {
			return Component.translatableWithFallback("sabercraft.ship.boarded", "You sit in %s.", name);
		}

		if (player.isPassenger()) {
			player.stopRiding();
		}

		if (!player.startRiding(ship, true, true)) {
			return Component.translatableWithFallback("sabercraft.ship.in_use", "Someone is flying %s.", name);
		}

		park(player, ship);
		sendStatus(player);
		Component message = Component.translatableWithFallback("sabercraft.ship.controls",
				"W/S: throttle, A/D: turn, Space/Sprint: nose up/down, Attack: lasers, Sneak: climb out");
		player.sendOverlayMessage(message);
		return message;
	}

	/** The pilot pulled the trigger: only in their own ship, at the ship's rate of fire. */
	private static void fire(ServerPlayer player) {
		if (AbilityConfig.get().shipsEnabled && player.getVehicle() instanceof ShipEntity ship && ship.owner().equals(player.getUUID())
				&& isCurrent(ship) && player.isAlive()) {
			ship.fire(player);
		}
	}

	/** Tells everyone who sees the ship that a cannon fired, for its muzzle flash. */
	static void shot(ShipEntity ship, int cannon) {
		ShipShotPayload payload = new ShipShotPayload(ship.getId(), (byte) cannon);

		for (ServerPlayer viewer : PlayerLookup.tracking(ship)) {
			if (ServerPlayNetworking.canSend(viewer, ShipShotPayload.TYPE)) {
				ServerPlayNetworking.send(viewer, payload);
			}
		}

		if (ship.pilot() instanceof ServerPlayer pilot && !PlayerLookup.tracking(ship).contains(pilot)
				&& ServerPlayNetworking.canSend(pilot, ShipShotPayload.TYPE)) {
			ServerPlayNetworking.send(pilot, payload);
		}
	}

	/** Sends a player the state of their ship, for their hangar. */
	public static void sendStatus(ServerPlayer player) {
		if (!ServerPlayNetworking.canSend(player, ShipStatusPayload.TYPE)) {
			return;
		}

		Parked parked = parked(player);
		ShipEntity ship = ship(player);
		ServerPlayNetworking.send(player, new ShipStatusPayload(AbilityConfig.get().shipsEnabled, parked != null ? parked.ship() : "",
				ship != null ? ship.getId() : -1));
	}

	/** Only for tests: how many ship entities are in the world for all players. */
	public static int liveCount() {
		return (int) LIVE.values().stream().filter(ship -> !ship.isRemoved()).count();
	}

	/** Only for tests: forgets the command rate limit. */
	public static void resetCooldowns() {
		LAST_COMMAND.clear();
	}
}
