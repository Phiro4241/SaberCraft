package com.phiro.sabercraft.character;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import com.phiro.sabercraft.network.ChooseOutfitPayload;
import com.phiro.sabercraft.network.OutfitSyncPayload;

/**
 * Server side of the outfits: remembers which built-in outfit each player chose and tells every
 * player with the mod. Only known outfits are taken, and a player can change it at most every
 * half second. The choice lives as long as the player is online; the client sends it again on
 * every join (it is saved on the client). Players without the mod just see the normal skins.
 */
public final class OutfitSync {
	private static final Map<UUID, Identifier> OUTFITS = new HashMap<>();
	private static final Map<UUID, Long> LAST_CHANGE = new HashMap<>();

	private OutfitSync() {
	}

	public static void init() {
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> clear());
		ServerPlayNetworking.registerGlobalReceiver(ChooseOutfitPayload.TYPE, (payload, context) -> choose(context.player(), payload.outfit()));
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			if (ServerPlayNetworking.canSend(handler.getPlayer(), OutfitSyncPayload.TYPE)) {
				List<OutfitSyncPayload.Entry> entries = new ArrayList<>();
				OUTFITS.forEach((player, outfit) -> entries.add(new OutfitSyncPayload.Entry(player, Optional.of(outfit))));
				ServerPlayNetworking.send(handler.getPlayer(), new OutfitSyncPayload(true, entries));
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			UUID id = handler.getPlayer().getUUID();
			LAST_CHANGE.remove(id);

			if (OUTFITS.remove(id) != null) {
				broadcast(server, new OutfitSyncPayload.Entry(id, Optional.empty()));
			}
		});
	}

	static void choose(ServerPlayer player, Optional<Identifier> wanted) {
		Optional<Outfit> outfit = wanted.flatMap(Outfit::byId);

		if (wanted.isPresent() && outfit.isEmpty()) {
			return;
		}

		long now = player.level().getGameTime();
		Long last = LAST_CHANGE.get(player.getUUID());

		if (last != null && now - last < 10L && now >= last) {
			return;
		}

		LAST_CHANGE.put(player.getUUID(), now);
		Identifier before = outfit.isPresent() ? OUTFITS.put(player.getUUID(), outfit.get().id()) : OUTFITS.remove(player.getUUID());

		if (!Objects.equals(before, outfit.map(Outfit::id).orElse(null))) {
			broadcast(player.level().getServer(), new OutfitSyncPayload.Entry(player.getUUID(), outfit.map(Outfit::id)));
		}
	}

	private static void broadcast(MinecraftServer server, OutfitSyncPayload.Entry entry) {
		OutfitSyncPayload payload = new OutfitSyncPayload(false, List.of(entry));

		for (ServerPlayer player : PlayerLookup.all(server)) {
			if (ServerPlayNetworking.canSend(player, OutfitSyncPayload.TYPE)) {
				ServerPlayNetworking.send(player, payload);
			}
		}
	}

	/** The outfit a player chose, for tests. */
	public static Optional<Identifier> outfitOf(UUID player) {
		return Optional.ofNullable(OUTFITS.get(player));
	}

	/** Forgets all choices, when the server stops. */
	public static void clear() {
		OUTFITS.clear();
		LAST_CHANGE.clear();
	}
}
