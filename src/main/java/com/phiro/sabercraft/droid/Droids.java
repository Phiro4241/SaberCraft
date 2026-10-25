package com.phiro.sabercraft.droid;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.serialization.Codec;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.ability.AbilityConfig;
import com.phiro.sabercraft.advancement.SaberAdvancements;
import com.phiro.sabercraft.network.DroidCommandPayload;
import com.phiro.sabercraft.network.DroidEventPayload;
import com.phiro.sabercraft.network.DroidStatusPayload;

/**
 * The droids of all players, on the server (Phase 11).
 *
 * <p>Which droids a player has called is saved with the player (an attachment that also survives
 * death), never the droids themselves. Twice a second every online player's droids are checked: a
 * droid that is missing (its chunk unloaded, the player just joined, it fell out of the world) or
 * in another dimension than its owner is put back next to them. A droid entity this class does not
 * know (any more) removes itself. So every player has each kind of droid at most once, and droids
 * never pile up in the world.
 *
 * <p>Everything a client asks for is checked here: the kind exists, droids are enabled, the limit
 * per player holds, only the owner commands a droid, and a player can send one command every
 * {@value #COMMAND_INTERVAL} ticks.
 */
public final class Droids {
	public static final byte EVENT_INTERACT = 1;
	public static final byte EVENT_ABILITY = 2;
	public static final byte EVENT_ARRIVE = 3;

	/** Ticks between two commands of the same player. */
	static final int COMMAND_INTERVAL = 4;
	/** A droid told to stay follows again when its owner is this far away (squared). */
	static final double STAY_LIMIT_SQR = 96.0 * 96.0;
	/** Abilities only work when the droid is this close to its owner (squared). */
	static final double ABILITY_RANGE_SQR = 32.0 * 32.0;

	/** The droids each player has called, by {@link DroidType#id()}. */
	public static final AttachmentType<List<String>> CREW = AttachmentRegistry.create(SaberCraft.id("droids"),
			builder -> builder.persistent(Codec.STRING.listOf()).copyOnDeath());

	private static final Map<UUID, EnumMap<DroidType, DroidEntity>> LIVE = new HashMap<>();
	private static final Map<UUID, EnumMap<DroidType, Long>> READY_AT = new HashMap<>();
	private static final Map<UUID, Long> LAST_COMMAND = new HashMap<>();

	private Droids() {
	}

	public static void init() {
		ServerPlayNetworking.registerGlobalReceiver(DroidCommandPayload.TYPE, (payload, context) -> command(context.player(), payload.action(), payload.droid()));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 10 == 0) {
				tick(server);
			}
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sendStatus(handler.getPlayer()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			UUID id = handler.getPlayer().getUUID();
			EnumMap<DroidType, DroidEntity> live = LIVE.remove(id);
			LAST_COMMAND.remove(id);

			// Not in the middle of the player leaving the level: on the next tick.
			if (live != null) {
				server.execute(() -> live.values().forEach(DroidEntity::discard));
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			LIVE.values().forEach(live -> live.values().forEach(DroidEntity::discard));
			LIVE.clear();
			READY_AT.clear();
			LAST_COMMAND.clear();
		});
	}

	/** The kinds of droids a player has called, in the order they were called. */
	public static List<DroidType> crew(ServerPlayer player) {
		List<DroidType> crew = new ArrayList<>();

		for (String id : player.getAttachedOrElse(CREW, List.of())) {
			DroidType.byId(id).filter(type -> !crew.contains(type)).ifPresent(crew::add);
		}

		return crew;
	}

	private static void setCrew(ServerPlayer player, List<DroidType> crew) {
		if (crew.isEmpty()) {
			player.removeAttached(CREW);
		} else {
			player.setAttached(CREW, crew.stream().map(DroidType::id).toList());
		}
	}

	/** The droid of that kind of the player, if it is in the world now. */
	public static @Nullable DroidEntity droid(ServerPlayer player, DroidType type) {
		EnumMap<DroidType, DroidEntity> live = LIVE.get(player.getUUID());
		DroidEntity droid = live != null ? live.get(type) : null;
		return droid != null && !droid.isRemoved() ? droid : null;
	}

	/** Whether this droid is the one its owner has called (and not an old copy). */
	static boolean isCurrent(DroidEntity droid) {
		EnumMap<DroidType, DroidEntity> live = LIVE.get(droid.owner());
		return live != null && live.get(droid.droid()) == droid;
	}

	private static void tick(MinecraftServer server) {
		boolean enabled = AbilityConfig.get().droidsEnabled;

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			List<DroidType> crew = enabled ? crew(player) : List.of();
			EnumMap<DroidType, DroidEntity> live = LIVE.get(player.getUUID());

			// Dismissed (or disabled): away with them.
			if (live != null) {
				for (Iterator<Map.Entry<DroidType, DroidEntity>> it = live.entrySet().iterator(); it.hasNext();) {
					Map.Entry<DroidType, DroidEntity> entry = it.next();

					if (!crew.contains(entry.getKey())) {
						entry.getValue().discard();
						it.remove();
					}
				}
			}

			if (!player.isAlive() || player.isSpectator()) {
				continue;
			}

			boolean changed = false;

			for (DroidType type : crew) {
				DroidEntity droid = droid(player, type);

				if (droid == null || droid.level() != player.level()) {
					if (droid != null) {
						droid.discard();
					}

					changed |= spawn(player, type, droid != null && droid.isStaying()) != null;
				} else if (droid.distanceToSqr(player) > (droid.isStaying() ? STAY_LIMIT_SQR : DroidEntity.TELEPORT_DISTANCE_SQR * 4.0)) {
					// Left far behind (a /tp, an ender pearl): back to the owner.
					droid.setStaying(false);
					changed |= droid.teleportToOwner();
				}
			}

			if (changed) {
				sendStatus(player);
			}
		}
	}

	/** Puts a new droid of that kind next to the player. */
	private static @Nullable DroidEntity spawn(ServerPlayer player, DroidType type, boolean arrived) {
		ServerLevel level = player.level();
		DroidEntity droid = new DroidEntity(level, type, player);
		droid.snapTo(player.getX(), player.getY(), player.getZ(), player.getYRot() + 180.0F, 0.0F);
		droid.setYHeadRot(player.getYRot() + 180.0F);
		droid.setYBodyRot(player.getYRot() + 180.0F);
		LIVE.computeIfAbsent(player.getUUID(), id -> new EnumMap<>(DroidType.class)).put(type, droid);
		droid.teleportToOwner();

		if (!level.addFreshEntity(droid)) {
			LIVE.get(player.getUUID()).remove(type);
			return null;
		}

		if (!arrived) {
			event(droid, EVENT_ARRIVE);
			droid.playVoice("interact", 1.0F);
		}

		return droid;
	}

	/** A droid died (only /kill and the void can do that): its owner has to call it again. */
	static void died(DroidEntity droid) {
		if (!isCurrent(droid) || droid.level().getServer() == null) {
			return;
		}

		LIVE.get(droid.owner()).remove(droid.droid());
		ServerPlayer owner = droid.level().getServer().getPlayerList().getPlayer(droid.owner());

		if (owner != null) {
			List<DroidType> crew = new ArrayList<>(crew(owner));
			crew.remove(droid.droid());
			setCrew(owner, crew);
			sendStatus(owner);
		}
	}

	/**
	 * A command from the droid menu or {@code /sabercraft droid}. Returns the message for the
	 * player, or {@code null} if the command was ignored (too fast, unknown).
	 */
	public static @Nullable Component command(ServerPlayer player, String action, String id) {
		long now = player.level().getGameTime();
		Long last = LAST_COMMAND.get(player.getUUID());

		if (last != null && now - last < COMMAND_INTERVAL && now >= last) {
			return null;
		}

		DroidType type = DroidType.byId(id).orElse(null);

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

	private static @Nullable Component run(ServerPlayer player, String action, DroidType type) {
		AbilityConfig config = AbilityConfig.get();

		if (!config.droidsEnabled && !action.equals("dismiss")) {
			return Component.translatableWithFallback("sabercraft.droid.disabled", "Droids are turned off on this server.");
		}

		List<DroidType> crew = new ArrayList<>(crew(player));
		Component name = Component.translatableWithFallback(type.translationKey(), type.displayName());

		switch (action) {
			case "spawn" -> {
				if (crew.contains(type)) {
					return recall(player, type);
				}

				if (crew.size() >= config.droidLimit) {
					return Component.translatableWithFallback("sabercraft.droid.limit", "You can only have %s droids out at once. Send one home first.",
							config.droidLimit);
				}

				crew.add(type);
				setCrew(player, crew);

				if (spawn(player, type, false) == null) {
					crew.remove(type);
					setCrew(player, crew);
					return Component.translatableWithFallback("sabercraft.droid.no_room", "There is no room here for %s.", name);
				}

				SaberAdvancements.award(player, SaberAdvancements.DROID_FRIEND);
				return Component.translatableWithFallback("sabercraft.droid.spawned", "%s is with you.", name);
			}
			case "recall" -> {
				return crew.contains(type) ? recall(player, type) : null;
			}
			case "stay" -> {
				return crew.contains(type) ? toggleStay(player, type) : null;
			}
			case "ability" -> {
				return crew.contains(type) ? ability(player, type) : null;
			}
			case "dismiss" -> {
				if (!crew.remove(type)) {
					return null;
				}

				setCrew(player, crew);
				DroidEntity droid = droid(player, type);

				if (droid != null) {
					droid.level().playSound(null, droid.getX(), droid.getY(), droid.getZ(), DroidEntity.sound(type, "interact"), SoundSource.NEUTRAL, 0.8F, 0.8F);
					droid.discard();
					LIVE.get(player.getUUID()).remove(type);
				}

				return Component.translatableWithFallback("sabercraft.droid.dismissed", "%s went home.", name);
			}
			default -> {
				return null;
			}
		}
	}

	private static Component recall(ServerPlayer player, DroidType type) {
		DroidEntity droid = droid(player, type);
		Component name = Component.translatableWithFallback(type.translationKey(), type.displayName());

		if (droid == null || droid.level() != player.level()) {
			if (droid != null) {
				droid.discard();
			}

			droid = spawn(player, type, false);
		} else {
			droid.setStaying(false);
			droid.teleportToOwner();
			event(droid, EVENT_ARRIVE);
			droid.playVoice("interact", 1.0F);
		}

		return droid != null ? Component.translatableWithFallback("sabercraft.droid.recalled", "%s comes to you.", name)
				: Component.translatableWithFallback("sabercraft.droid.no_room", "There is no room here for %s.", name);
	}

	/** Stay or follow: from the menu, or by sneaking and right-clicking the droid. */
	static @Nullable Component toggleStay(ServerPlayer player, DroidType type) {
		DroidEntity droid = droid(player, type);

		if (droid == null) {
			return null;
		}

		droid.setStaying(!droid.isStaying());
		droid.playVoice("interact", 0.8F);
		event(droid, EVENT_INTERACT);
		Component name = Component.translatableWithFallback(type.translationKey(), type.displayName());
		Component message = droid.isStaying() ? Component.translatableWithFallback("sabercraft.droid.staying", "%s waits here.", name)
				: Component.translatableWithFallback("sabercraft.droid.following", "%s follows you.", name);
		player.sendOverlayMessage(message);
		sendStatus(player);
		return message;
	}

	private static @Nullable Component ability(ServerPlayer player, DroidType type) {
		DroidEntity droid = droid(player, type);
		Component name = Component.translatableWithFallback(type.translationKey(), type.displayName());

		if (droid == null || droid.level() != player.level() || droid.distanceToSqr(player) > ABILITY_RANGE_SQR) {
			return Component.translatableWithFallback("sabercraft.droid.too_far", "%s is too far away.", name);
		}

		long now = player.level().getGameTime();
		long ready = READY_AT.computeIfAbsent(player.getUUID(), id -> new EnumMap<>(DroidType.class)).getOrDefault(type, 0L);

		if (now < ready) {
			return Component.translatableWithFallback("sabercraft.droid.cooldown", "%s needs %s more seconds.", name, (ready - now + 19) / 20);
		}

		DroidAbilities.Result result = DroidAbilities.use(player, droid);

		if (result.used()) {
			READY_AT.get(player.getUUID()).put(type, now + cooldown(type));
			droid.playVoice("ability", 1.0F);
			event(droid, EVENT_ABILITY);
			SaberAdvancements.award(player, SaberAdvancements.LOYAL_COMPANION, type.id());
		}

		return result.message();
	}

	/** The full cooldown of a droid's ability, in ticks. */
	public static int cooldown(DroidType type) {
		return Math.max(1, Math.round(type.cooldown() * AbilityConfig.get().droidCooldown));
	}

	/** Tells everyone who sees the droid what it does, so its model shows it. */
	static void event(DroidEntity droid, byte event) {
		DroidEventPayload payload = new DroidEventPayload(droid.getId(), event);

		for (ServerPlayer viewer : PlayerLookup.tracking(droid)) {
			if (ServerPlayNetworking.canSend(viewer, DroidEventPayload.TYPE)) {
				ServerPlayNetworking.send(viewer, payload);
			}
		}
	}

	/** Sends a player the state of their droids, for their droid menu. */
	public static void sendStatus(ServerPlayer player) {
		if (!ServerPlayNetworking.canSend(player, DroidStatusPayload.TYPE)) {
			return;
		}

		AbilityConfig config = AbilityConfig.get();
		long now = player.level().getGameTime();
		EnumMap<DroidType, Long> ready = READY_AT.get(player.getUUID());
		List<DroidStatusPayload.Entry> entries = new ArrayList<>();

		for (DroidType type : crew(player)) {
			DroidEntity droid = droid(player, type);
			long left = ready != null ? Math.max(0L, ready.getOrDefault(type, 0L) - now) : 0L;
			entries.add(new DroidStatusPayload.Entry(type.id(), droid != null ? droid.getId() : -1, droid != null && droid.isStaying(),
					(int) Math.min(left, 72000L), cooldown(type)));
		}

		ServerPlayNetworking.send(player, new DroidStatusPayload(config.droidsEnabled, config.droidLimit, entries));
	}

	/** Only for tests: how many droid entities are in the world for all players. */
	public static int liveCount() {
		return LIVE.values().stream().mapToInt(live -> (int) live.values().stream().filter(droid -> !droid.isRemoved()).count()).sum();
	}

	/** Only for tests: forgets the cooldowns and the command rate limit. */
	public static void resetCooldowns() {
		READY_AT.clear();
		LAST_COMMAND.clear();
	}
}
