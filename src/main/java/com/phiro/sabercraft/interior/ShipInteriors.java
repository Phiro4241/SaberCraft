package com.phiro.sabercraft.interior;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.ability.AbilityConfig;
import com.phiro.sabercraft.ship.ShipEntity;
import com.phiro.sabercraft.ship.ShipFeatures;
import com.phiro.sabercraft.ship.ShipType;
import com.phiro.sabercraft.ship.Starships;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BoatItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Phase 14: walkable interiors of the big ships.
 *
 * <p>The rooms are real blocks in a dimension of their own, {@code sabercraft:ship_interiors} (a
 * void with the space sky, see {@code data/sabercraft/dimension}). Each owner gets one plot per
 * ship type with an interior, built once from its {@link InteriorPlan} the first time anyone boards
 * it, and kept from then on: chests keep what is put in them, and a new ship of the same type (after
 * one was shot down) has the same rooms. Plots are 1024 blocks apart: x by owner, z by plan, so the
 * plan of any spot can be told from its coordinates (the client uses that for the ambient sound).
 *
 * <p>Getting on: sneak and use the landed ship (Falcon), stand sneaking below the hangar of a
 * Star Destroyer (its tractor beam lifts you in), or the hangar menu. Getting off: stand a moment on
 * the ramp (Falcon) or the hangar exit (Star Destroyer). The lever at the helm puts the owner into
 * the pilot's seat. Who is aboard is never lost: logging out keeps you in the room, logging in
 * again finds you there, falling out of the rooms puts you back at the entry, and when the ship is
 * destroyed everyone aboard gets out next to the wreck in an escape pod.
 *
 * <p>Plots and the last known place of each ship are saved in {@code sabercraft/interiors.json} in
 * the world folder.
 */
public final class ShipInteriors {
	public static final ResourceKey<Level> DIMENSION = ResourceKey.create(Registries.DIMENSION, SaberCraft.id("ship_interiors"));
	/** Plots are this far apart. */
	public static final int SPACING = 1024;
	/** The floor height of every plot's corner. */
	public static final int BASE_Y = 64;
	/** Standing this many ticks on an exit or lift uses it. */
	static final int EXIT_TICKS = 15;
	static final int LIFT_TICKS = 10;
	/** Sneaking this many checks (half seconds) below a Star Destroyer's hangar lifts you in. */
	static final int TRACTOR_CHECKS = 3;

	/** Who is aboard which interior. Not saved: rebuilt from where players are. */
	private static final Map<UUID, Aboard> ABOARD = new HashMap<>();
	/** The plots: "owner/type" to its index along x. */
	private static final Map<String, Plot> PLOTS = new HashMap<>();
	/** The next free index per plan. */
	private static final Map<String, Integer> NEXT = new HashMap<>();
	/** Where each ship was last seen: "owner/type". */
	private static final Map<String, Place> PLACES = new HashMap<>();
	/** The ships in the world now, for the tractor beam. */
	private static final Map<String, WeakReference<ShipEntity>> SHIPS = new HashMap<>();
	private static final Map<UUID, Integer> TRACTOR = new HashMap<>();
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static boolean dirty;
	private static @Nullable Path file;

	/** A plot: its index along x and whether its rooms have been built (and from which plan version). */
	static final class Plot {
		final int index;
		int version;

		Plot(int index, int version) {
			this.index = index;
			this.version = version;
		}
	}

	/** Where a ship was. */
	record Place(String dimension, double x, double y, double z, float yaw) {
		Vec3 position() {
			return new Vec3(this.x, this.y, this.z);
		}
	}

	/** One player walking around in an interior. */
	static final class Aboard {
		final UUID owner;
		final String type;
		final InteriorPlan plan;
		final BlockPos origin;
		int zoneTicks;
		/** Exits and lifts only work once the player stepped off the one they arrived on. */
		boolean armed;
		@Nullable String room;

		Aboard(UUID owner, String type, InteriorPlan plan, BlockPos origin) {
			this.owner = owner;
			this.type = type;
			this.plan = plan;
			this.origin = origin;
		}

		Vec3 local(Vec3 world) {
			return world.subtract(this.origin.getX(), this.origin.getY(), this.origin.getZ());
		}

		Vec3 world(Vec3 local) {
			return local.add(this.origin.getX(), this.origin.getY(), this.origin.getZ());
		}
	}

	private ShipInteriors() {
	}

	public static void init() {
		ServerLifecycleEvents.SERVER_STARTED.register(ShipInteriors::load);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			save();
			ABOARD.clear();
			PLOTS.clear();
			NEXT.clear();
			PLACES.clear();
			SHIPS.clear();
			TRACTOR.clear();
			file = null;
		});
		ServerTickEvents.END_SERVER_TICK.register(ShipInteriors::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			ABOARD.remove(handler.getPlayer().getUUID());
			TRACTOR.remove(handler.getPlayer().getUUID());
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> ABOARD.remove(newPlayer.getUUID()));

		// The rooms are no building site (creative players may still change them).
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> !protectedFor(level, player));
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (level.isClientSide() || !isInterior(level) || !(player instanceof ServerPlayer serverPlayer)) {
				return InteractionResult.PASS;
			}

			Aboard aboard = ABOARD.get(player.getUUID());

			if (aboard != null && hit.getBlockPos().equals(aboard.origin.offset(aboard.plan.helm()))) {
				serverPlayer.sendOverlayMessage(helm(serverPlayer, aboard));
				return InteractionResult.SUCCESS;
			}

			return InteractionResult.PASS;
		});
		UseItemCallback.EVENT.register((player, level, hand) -> {
			ItemStack stack = player.getItemInHand(hand);

			if (protectedFor(level, player) && (stack.getItem() instanceof BucketItem || stack.getItem() instanceof BoatItem
					|| stack.getItem() instanceof SpawnEggItem)) {
				return InteractionResult.FAIL;
			}

			return InteractionResult.PASS;
		});
	}

	/** Whether that level is the interiors dimension (on either side). */
	public static boolean isInterior(Level level) {
		return level.dimension().equals(DIMENSION);
	}

	/** Whether a player may not build there: in an interior, unless in creative mode. */
	public static boolean protectedFor(Level level, @Nullable Player player) {
		return isInterior(level) && (player == null || !player.isCreative());
	}

	/** The plan of a spot in the interiors dimension (from its coordinates), or {@code null}. */
	public static @Nullable InteriorPlan planAt(double x, double z) {
		int row = Mth.floor(z / SPACING) - 1;
		int index = Mth.floor(x / SPACING) - 1;

		if (row < 0 || index < 0 || row >= InteriorPlan.ALL.size()) {
			return null;
		}

		InteriorPlan plan = InteriorPlan.ALL.get(row);
		BlockPos size = plan.size();
		double lx = x - (index + 1.0) * SPACING;
		double lz = z - (row + 1.0) * SPACING;
		return lx >= -8 && lz >= -8 && lx <= size.getX() + 8 && lz <= size.getZ() + 8 ? plan : null;
	}

	/** The corner of a plot. */
	public static BlockPos origin(InteriorPlan plan, int index) {
		return new BlockPos((index + 1) * SPACING, BASE_Y, (plan.row() + 1) * SPACING);
	}

	private static String key(UUID owner, String type) {
		return owner + "/" + type;
	}

	// --- Called from the ships ---------------------------------------------------------------------

	/** Whether the player walks around in the interior of their own ship of that type. */
	public static boolean isAboard(ServerPlayer player, ShipType type) {
		Aboard aboard = ABOARD.get(player.getUUID());
		return aboard != null && aboard.owner.equals(player.getUUID()) && aboard.type.equals(type.id());
	}

	/** How many players are aboard that ship's interior. */
	public static int aboardCount(UUID owner, ShipType type) {
		int count = 0;

		for (Aboard aboard : ABOARD.values()) {
			if (aboard.owner.equals(owner) && aboard.type.equals(type.id())) {
				count++;
			}
		}

		return count;
	}

	/** Where the ship of an owner is now (every half second while it is in the world). */
	public static void remember(UUID owner, ShipEntity ship) {
		if (ShipFeatures.of(ship.ship()).interior() == null) {
			return;
		}

		String key = key(owner, ship.ship().id());
		SHIPS.put(key, new WeakReference<>(ship));
		Place old = PLACES.get(key);
		Place now = new Place(ship.level().dimension().identifier().toString(), ship.getX(), ship.getY(), ship.getZ(), ship.getYRot());

		if (old == null || !old.dimension().equals(now.dimension()) || old.position().distanceToSqr(now.position()) > 4.0
				|| Math.abs(Mth.wrapDegrees(old.yaw() - now.yaw())) > 10.0F) {
			PLACES.put(key, now);
			dirty = true;
		}
	}

	/** Sneak-use on a landed ship: aboard it. */
	public static void boardFromOutside(ServerPlayer player, ShipEntity ship) {
		Component message = boardShip(player, ship, false);

		if (message != null) {
			player.sendOverlayMessage(message);
		}
	}

	/** The hangar's "interior" button: aboard the own ship, from close by or from its cockpit. */
	public static Component enterOwn(ServerPlayer player, ShipType type) {
		ShipEntity ship = Starships.ship(player);

		if (ShipFeatures.of(type).interior() == null) {
			return Component.translatableWithFallback("sabercraft.interior.none", "This ship has no rooms to walk around in.");
		}

		if (ship == null || ship.ship() != type || ship.level() != player.level() || player.getVehicle() != ship && ship.distanceToSqr(player) > 48.0 * 48.0) {
			return Component.translatableWithFallback("sabercraft.interior.too_far", "Your ship has to be close by.");
		}

		Component message = boardShip(player, ship, player.getVehicle() == ship);
		return message != null ? message : Component.translatableWithFallback("sabercraft.interior.welcome", "Welcome aboard.");
	}

	/** The ship was destroyed: everyone aboard gets out in an escape pod, next to the wreck. */
	public static void shipLost(MinecraftServer server, UUID owner, ShipType type, ServerLevel level, Vec3 pos) {
		String key = key(owner, type.id());
		PLACES.put(key, new Place(level.dimension().identifier().toString(), pos.x, pos.y, pos.z, 0.0F));
		SHIPS.remove(key);
		dirty = true;
		List<ServerPlayer> crew = new ArrayList<>();

		for (Map.Entry<UUID, Aboard> entry : ABOARD.entrySet()) {
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());

			if (player != null && entry.getValue().owner.equals(owner) && entry.getValue().type.equals(type.id())) {
				crew.add(player);
			}
		}

		int i = 0;

		for (ServerPlayer player : crew) {
			ABOARD.remove(player.getUUID());
			double angle = i++ * 2.4;
			double distance = ShipFeatures.of(type).hullBoxes().stream().mapToDouble(box -> Math.max(box.getXsize(), box.getZsize())).max().orElse(4.0) * 0.6
					+ 3.0;
			Vec3 spot = safeSpot(level, pos.add(Math.cos(angle) * distance, 1.0, Math.sin(angle) * distance));
			teleport(player, level, spot, player.getYRot());
			fairLanding(player, spot, level);
			player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 100, 4));
			player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 200));
			player.sendOverlayMessage(Component.translatableWithFallback("sabercraft.interior.escape_pod",
					"The ship is lost! An escape pod brought you down next to the wreck."));
		}
	}

	// --- Boarding and leaving ----------------------------------------------------------------------

	/** Boards the player into the ship's interior; returns the message, or null when all went well (the welcome is sent). */
	static @Nullable Component boardShip(ServerPlayer player, ShipEntity ship, boolean fromCockpit) {
		AbilityConfig config = AbilityConfig.get();
		InteriorPlan plan = InteriorPlan.of(ShipFeatures.of(ship.ship()).interior());
		ServerLevel interiors = player.level().getServer().getLevel(DIMENSION);

		if (plan == null) {
			return Component.translatableWithFallback("sabercraft.interior.none", "This ship has no rooms to walk around in.");
		}

		if (!config.shipInteriors || interiors == null) {
			return Component.translatableWithFallback("sabercraft.interior.disabled", "Ship interiors are turned off on this server.");
		}

		boolean owner = ship.owner().equals(player.getUUID());

		if (!owner && !config.shipInteriorGuests) {
			return Component.translatableWithFallback("sabercraft.interior.owner_only", "Only the owner may come aboard.");
		}

		if (ship.wrecked() || !ship.isAlive()) {
			return null;
		}

		if (!ship.ship().capital() && !ship.onGround()) {
			return Component.translatableWithFallback("sabercraft.interior.land_first", "Land first to walk around in the ship.");
		}

		if (ship.pilot() != null && ship.pilot() != player) {
			return Component.translatableWithFallback("sabercraft.interior.in_flight", "The ship is being flown. Ask the pilot to land.");
		}

		if (player.getVehicle() != null) {
			player.stopRiding();
		}

		remember(ship.owner(), ship);
		BlockPos origin = plot(interiors, ship.owner(), plan);
		Vec3 local = plan.entry();
		float yaw = plan.entryYaw();

		if (fromCockpit && !plan.lifts().isEmpty() && plan.id().equals("star_destroyer")) {
			// From the pilot's seat of a Star Destroyer straight onto its bridge.
			InteriorPlan.Lift bridge = plan.lifts().get(0);
			local = bridge.arrival();
			yaw = bridge.yaw();
		}

		Aboard aboard = new Aboard(ship.owner(), ship.ship().id(), plan, origin);
		ABOARD.put(player.getUUID(), aboard);
		teleport(player, interiors, aboard.world(local), yaw);
		player.connection.send(new ClientboundSoundPacket(sound("ship.ramp"), SoundSource.NEUTRAL, player.getX(), player.getY(), player.getZ(), 0.7F, 1.0F,
				player.getRandom().nextLong()));
		player.sendOverlayMessage(Component.translatableWithFallback("sabercraft.interior.welcome_ship", "Welcome aboard %s.",
				Component.translatableWithFallback(ship.ship().translationKey(), ship.ship().displayName())));
		return null;
	}

	/** Off the ship, next to it (or where it was last seen). */
	static void leave(ServerPlayer player, Aboard aboard) {
		MinecraftServer server = player.level().getServer();
		ABOARD.remove(player.getUUID());
		String key = key(aboard.owner, aboard.type);
		WeakReference<ShipEntity> reference = SHIPS.get(key);
		ShipEntity ship = reference != null ? reference.get() : null;
		ServerLevel level = null;
		Vec3 pos = null;
		float yaw = 0.0F;

		if (ship != null && !ship.isRemoved() && ship.level() instanceof ServerLevel shipLevel) {
			level = shipLevel;
			pos = ship.position();
			yaw = ship.getYRot();
		} else {
			Place place = PLACES.get(key);
			ServerPlayer owner = server.getPlayerList().getPlayer(aboard.owner);
			Starships.Parked parked = owner != null ? Starships.parked(owner) : null;

			if (parked != null && parked.ship().equals(aboard.type)) {
				place = new Place(parked.dimension(), parked.x(), parked.y(), parked.z(), parked.yaw());
			}

			if (place != null) {
				Identifier id = Identifier.tryParse(place.dimension());
				level = id != null ? server.getLevel(ResourceKey.create(Registries.DIMENSION, id)) : null;
				pos = place.position();
				yaw = place.yaw();
			}
		}

		if (level == null || pos == null || level.dimension().equals(DIMENSION)) {
			toSpawn(player);
			return;
		}

		ShipType type = ShipType.byId(aboard.type).orElse(ShipType.FALCON);
		Vec3 wanted = ShipFeatures.toWorld(exitSpot(aboard.plan), pos, yaw, 0.0F, 0.0);
		Vec3 spot = safeSpot(level, wanted);
		teleport(player, level, spot, yaw + 180.0F);
		fairLanding(player, spot, level);
		level.playSound(null, spot.x, spot.y, spot.z, sound("ship.ramp"), SoundSource.NEUTRAL, 0.6F, 1.1F);

		if (type.capital()) {
			player.sendOverlayMessage(Component.translatableWithFallback("sabercraft.interior.lowered", "The tractor beam lowers you down."));
		}
	}

	/** Where people get off, in ship space: behind the Falcon's ramp, below a Star Destroyer's hangar. */
	static Vec3 exitSpot(InteriorPlan plan) {
		return plan.id().equals("star_destroyer") ? new Vec3(0.0, -1.0, 2.0) : new Vec3(1.5, 0.2, -8.0);
	}

	/** The world spawn, when the ship cannot be found at all. */
	private static void toSpawn(ServerPlayer player) {
		ServerLevel overworld = player.level().getServer().overworld();
		BlockPos spawn = overworld.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
				overworld.getRespawnData().globalPos().pos());
		teleport(player, overworld, Vec3.atBottomCenterOf(spawn), player.getYRot());
		player.sendOverlayMessage(Component.translatableWithFallback("sabercraft.interior.no_ship", "Your ship could not be found. You are back at the spawn."));
	}

	/** The helm lever: the owner takes the controls. */
	static Component helm(ServerPlayer player, Aboard aboard) {
		if (!aboard.owner.equals(player.getUUID())) {
			ServerPlayer owner = player.level().getServer().getPlayerList().getPlayer(aboard.owner);
			return Component.translatableWithFallback("sabercraft.interior.helm_owner", "Only %s can fly this ship.",
					owner != null ? owner.getDisplayName() : Component.literal("?"));
		}

		ShipType type = ShipType.byId(aboard.type).orElse(null);
		ShipEntity ship = type != null ? Starships.ship(player) : null;

		if (ship == null || ship.ship() != type || ship.wrecked() || !(ship.level() instanceof ServerLevel shipLevel)) {
			return Component.translatableWithFallback("sabercraft.interior.helm_offline", "The ship does not answer. Leave by the ramp.");
		}

		ABOARD.remove(player.getUUID());
		teleport(player, shipLevel, ship.position().add(0.0, 1.0, 0.0), ship.getYRot());
		return Starships.takeHelm(player, ship);
	}

	// --- Every tick --------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		ServerLevel interiors = server.getLevel(DIMENSION);

		if (interiors != null) {
			// Players who got here some other way (logged in here, a command): find their plot.
			for (ServerPlayer player : interiors.players()) {
				if (!ABOARD.containsKey(player.getUUID())) {
					adopt(player);
				}
			}
		}

		ABOARD.entrySet().removeIf(entry -> {
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			return player == null || !isInterior(player.level());
		});

		for (Map.Entry<UUID, Aboard> entry : List.copyOf(ABOARD.entrySet())) {
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());

			if (player != null && player.isAlive()) {
				walk(player, entry.getValue());
			}
		}

		if (server.getTickCount() % 10 == 0) {
			tractor(server);
		}

		if (dirty && server.getTickCount() % 600 == 0) {
			save();
		}
	}

	/** A player in the interiors without a known plot: the plot their position is in, or off to the spawn. */
	private static void adopt(ServerPlayer player) {
		InteriorPlan plan = planAt(player.getX(), player.getZ());
		int index = Mth.floor(player.getX() / SPACING) - 1;

		if (plan != null) {
			for (Map.Entry<String, Plot> entry : PLOTS.entrySet()) {
				String[] parts = entry.getKey().split("/", 2);

				if (entry.getValue().index == index && parts.length == 2 && parts[1].equals(plan.id())) {
					try {
						Aboard aboard = new Aboard(UUID.fromString(parts[0]), parts[1], plan, origin(plan, index));
						ABOARD.put(player.getUUID(), aboard);
						return;
					} catch (IllegalArgumentException ignored) {
						break;
					}
				}
			}
		}

		if (!player.isCreative() && !player.isSpectator()) {
			toSpawn(player);
		}
	}

	/** Exits, lifts, room names and the safety net under the rooms. */
	private static void walk(ServerPlayer player, Aboard aboard) {
		Vec3 local = aboard.local(player.position());

		if (local.y < -6.0) {
			// Fell out (a creative player broke a floor, an ender pearl): back to the entry.
			teleport(player, (ServerLevel) player.level(), aboard.world(aboard.plan.entry()), aboard.plan.entryYaw());
			player.resetFallDistance();
			return;
		}

		AABB exit = null;
		InteriorPlan.Lift lift = null;

		for (AABB area : aboard.plan.exits()) {
			if (area.contains(local)) {
				exit = area;
			}
		}

		for (InteriorPlan.Lift candidate : aboard.plan.lifts()) {
			if (candidate.area().contains(local)) {
				lift = candidate;
			}
		}

		if (exit == null && lift == null) {
			aboard.armed = true;
			aboard.zoneTicks = 0;
		} else if (aboard.armed && !player.isSpectator()) {
			aboard.zoneTicks++;

			if (exit != null && aboard.zoneTicks >= EXIT_TICKS) {
				leave(player, aboard);
				return;
			}

			if (lift != null && aboard.zoneTicks >= LIFT_TICKS) {
				aboard.armed = false;
				aboard.zoneTicks = 0;
				teleport(player, (ServerLevel) player.level(), aboard.world(lift.arrival()), lift.yaw());
				player.connection.send(new ClientboundSoundPacket(sound("ship.lift"), SoundSource.NEUTRAL, player.getX(), player.getY(), player.getZ(), 0.6F,
						1.0F, player.getRandom().nextLong()));
				return;
			}
		}

		if (player.tickCount % 5 == 0) {
			InteriorPlan.Room room = aboard.plan.room(local);
			String id = room != null ? room.id() : null;

			if (id != null && !id.equals(aboard.room)) {
				player.sendOverlayMessage(roomName(aboard.plan, id));
			}

			aboard.room = id;
		}
	}

	/** The name of a room, for the overlay. */
	public static Component roomName(InteriorPlan plan, String room) {
		String fallback = room.substring(0, 1).toUpperCase(Locale.ROOT) + room.substring(1).replace('_', ' ');
		return Component.translatableWithFallback("sabercraft.interior.room." + plan.id() + "." + room, fallback);
	}

	/** Sneaking below a Star Destroyer's hangar: the tractor beam lifts you in. */
	private static void tractor(MinecraftServer server) {
		if (!AbilityConfig.get().shipInteriors) {
			return;
		}

		SHIPS.values().removeIf(reference -> reference.get() == null || reference.get().isRemoved());
		java.util.Set<UUID> beamed = new java.util.HashSet<>();

		for (WeakReference<ShipEntity> reference : List.copyOf(SHIPS.values())) {
			ShipEntity ship = reference.get();

			if (ship == null || !ship.ship().capital() || ship.wrecked() || ship.pilot() != null && ship.speed() > 0.05
					|| !(ship.level() instanceof ServerLevel level)) {
				continue;
			}

			Vec3 hangar = ShipFeatures.toWorld(new Vec3(0.0, 0.0, 2.0), ship.position(), ship.getYRot(), 0.0F, 0.0);

			for (ServerPlayer player : level.players()) {
				double dx = player.getX() - hangar.x;
				double dz = player.getZ() - hangar.z;
				double below = hangar.y - player.getY();

				if (!player.isShiftKeyDown() || player.getVehicle() != null || dx * dx + dz * dz > 7.0 * 7.0 || below < 0.5 || below > 40.0) {
					continue;
				}

				beamed.add(player.getUUID());
				int checks = TRACTOR.merge(player.getUUID(), 1, Integer::sum);
				level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0, player.getZ(), 6, 0.6, 1.2, 0.6, 0.02);

				if (checks >= TRACTOR_CHECKS) {
					TRACTOR.remove(player.getUUID());
					Component message = boardShip(player, ship, false);

					if (message != null) {
						player.sendOverlayMessage(message);
					}
				} else {
					player.sendOverlayMessage(Component.translatableWithFallback("sabercraft.interior.tractor", "Tractor beam locked on..."));
				}
			}
		}

		TRACTOR.keySet().retainAll(beamed);
	}

	// --- Plots -------------------------------------------------------------------------------------

	/** The corner of the owner's plot for that plan, built if it is new (or its plan is newer). */
	static BlockPos plot(ServerLevel interiors, UUID owner, InteriorPlan plan) {
		String key = key(owner, plan.id());
		Plot plot = PLOTS.get(key);

		if (plot == null) {
			int index = NEXT.getOrDefault(plan.id(), 0);
			NEXT.put(plan.id(), index + 1);
			plot = new Plot(index, 0);
			PLOTS.put(key, plot);
			dirty = true;
		}

		BlockPos origin = origin(plan, plot.index);

		if (plot.version == 0) {
			build(interiors, plan, origin);
			plot.version = plan.version();
			dirty = true;
			save();
		}

		return origin;
	}

	/** Builds a plan into its plot (loading its chunks). Returns how many blocks were set. */
	static int build(ServerLevel interiors, InteriorPlan plan, BlockPos origin) {
		BlockPos size = plan.size();

		for (int cx = (origin.getX() - 2) >> 4; cx <= (origin.getX() + size.getX() + 2) >> 4; cx++) {
			for (int cz = (origin.getZ() - 2) >> 4; cz <= (origin.getZ() + size.getZ() + 2) >> 4; cz++) {
				interiors.getChunk(cx, cz);
			}
		}

		InteriorBuilder builder = new InteriorBuilder(interiors, origin);
		plan.build(builder);
		return builder.placed();
	}

	// --- Helpers -----------------------------------------------------------------------------------

	static void teleport(ServerPlayer player, ServerLevel level, Vec3 pos, float yaw) {
		player.teleport(new TeleportTransition(level, pos, Vec3.ZERO, yaw, 0.0F, TeleportTransition.DO_NOTHING));
		player.resetFallDistance();
	}

	/** A free spot for a player near {@code wanted}: never inside blocks. */
	static Vec3 safeSpot(ServerLevel level, Vec3 wanted) {
		for (int ring = 0; ring <= 6; ring++) {
			for (int dy = 0; dy <= 6; dy++) {
				for (int step = 0; step < Math.max(1, ring * 8); step++) {
					double angle = step * Math.PI * 2.0 / Math.max(1, ring * 8);
					Vec3 spot = new Vec3(Math.floor(wanted.x + Math.cos(angle) * ring) + 0.5, Math.floor(wanted.y) + dy,
							Math.floor(wanted.z + Math.sin(angle) * ring) + 0.5);
					AABB body = new AABB(spot.x - 0.3, spot.y, spot.z - 0.3, spot.x + 0.3, spot.y + 1.8, spot.z + 0.3);

					if (level.isInWorldBounds(BlockPos.containing(spot)) && level.noCollision(body) && !level.containsAnyLiquid(body)) {
						return spot;
					}
				}
			}
		}

		return wanted;
	}

	/** Far above the ground (below a hovering Star Destroyer): float down instead of falling. */
	static void fairLanding(ServerPlayer player, Vec3 spot, ServerLevel level) {
		BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(spot));

		if (spot.y - ground.getY() > 3.0) {
			player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 600));
		}
	}

	static net.minecraft.core.Holder<net.minecraft.sounds.SoundEvent> sound(String name) {
		return net.minecraft.core.Holder.direct(net.minecraft.sounds.SoundEvent.createVariableRangeEvent(SaberCraft.id(name)));
	}

	// --- Saving ------------------------------------------------------------------------------------

	private static void load(MinecraftServer server) {
		PLOTS.clear();
		NEXT.clear();
		PLACES.clear();
		file = server.getWorldPath(LevelResource.ROOT).resolve("sabercraft").resolve("interiors.json");

		if (!Files.isRegularFile(file)) {
			return;
		}

		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			JsonObject root = GSON.fromJson(reader, JsonObject.class);

			if (root == null) {
				return;
			}

			JsonObject plots = root.has("plots") ? root.getAsJsonObject("plots") : new JsonObject();

			for (String key : plots.keySet()) {
				JsonObject plot = plots.getAsJsonObject(key);
				PLOTS.put(key, new Plot(plot.get("index").getAsInt(), plot.has("version") ? plot.get("version").getAsInt() : 0));
			}

			JsonObject next = root.has("next") ? root.getAsJsonObject("next") : new JsonObject();

			for (String key : next.keySet()) {
				NEXT.put(key, next.get(key).getAsInt());
			}

			// Never hand out an index twice, even if "next" was lost.
			for (Map.Entry<String, Plot> entry : PLOTS.entrySet()) {
				String plan = entry.getKey().substring(entry.getKey().indexOf('/') + 1);
				NEXT.merge(plan, entry.getValue().index + 1, Math::max);
			}

			JsonObject places = root.has("ships") ? root.getAsJsonObject("ships") : new JsonObject();

			for (String key : places.keySet()) {
				JsonObject place = places.getAsJsonObject(key);
				PLACES.put(key, new Place(place.get("dimension").getAsString(), place.get("x").getAsDouble(), place.get("y").getAsDouble(),
						place.get("z").getAsDouble(), place.get("yaw").getAsFloat()));
			}
		} catch (IOException | RuntimeException error) {
			SaberCraft.LOGGER.warn("Could not read the ship interiors from {}", file, error);
		}

		dirty = false;
	}

	/** Writes the plots and ship places (when something changed). */
	public static void save() {
		if (file == null || !dirty) {
			return;
		}

		JsonObject root = new JsonObject();
		JsonObject plots = new JsonObject();
		PLOTS.forEach((key, plot) -> {
			JsonObject value = new JsonObject();
			value.addProperty("index", plot.index);
			value.addProperty("version", plot.version);
			plots.add(key, value);
		});
		root.add("plots", plots);
		JsonObject next = new JsonObject();
		NEXT.forEach(next::addProperty);
		root.add("next", next);
		JsonObject places = new JsonObject();
		PLACES.forEach((key, place) -> {
			JsonObject value = new JsonObject();
			value.addProperty("dimension", place.dimension());
			value.addProperty("x", place.x());
			value.addProperty("y", place.y());
			value.addProperty("z", place.z());
			value.addProperty("yaw", place.yaw());
			places.add(key, value);
		});
		root.add("ships", places);

		try {
			Files.createDirectories(file.getParent());
			Path temp = file.resolveSibling("interiors.json.tmp");

			try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
				GSON.toJson(root, writer);
			}

			Files.move(temp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			dirty = false;
		} catch (IOException error) {
			SaberCraft.LOGGER.warn("Could not save the ship interiors to {}", file, error);
		}
	}

	// --- For the tests -----------------------------------------------------------------------------

	/** The corner of the owner's plot for that plan, building it if needed. */
	public static @Nullable BlockPos plotForTest(MinecraftServer server, UUID owner, String plan) {
		ServerLevel interiors = server.getLevel(DIMENSION);
		InteriorPlan interior = InteriorPlan.of(plan);
		return interiors != null && interior != null ? plot(interiors, owner, interior) : null;
	}

	/** The owner and type of the interior the player is in, or {@code null}. */
	public static @Nullable String aboardKey(ServerPlayer player) {
		Aboard aboard = ABOARD.get(player.getUUID());
		return aboard != null ? key(aboard.owner, aboard.type) : null;
	}

	/** Takes the player off the ship as if they had walked down the ramp. */
	public static boolean leaveForTest(ServerPlayer player) {
		Aboard aboard = ABOARD.get(player.getUUID());

		if (aboard == null) {
			return false;
		}

		leave(player, aboard);
		return true;
	}

	/** Uses the helm lever as the player. */
	public static @Nullable Component helmForTest(ServerPlayer player) {
		Aboard aboard = ABOARD.get(player.getUUID());
		return aboard != null ? helm(player, aboard) : null;
	}

	/** Runs the tractor beam check now. */
	public static void tractorForTest(MinecraftServer server) {
		tractor(server);
	}
}
