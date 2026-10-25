package com.phiro.sabercraft.client.droid;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

import net.minecraft.util.Util;
import net.minecraft.world.entity.EntityTypes;

import com.phiro.sabercraft.droid.DroidType;
import com.phiro.sabercraft.network.DroidCommandPayload;
import com.phiro.sabercraft.network.DroidEventPayload;
import com.phiro.sabercraft.network.DroidStatusPayload;

/**
 * The client side of the droids (Phase 11): their renderer, what the server says about the
 * player's own droids (for the menu), the animations it starts, and the menu's commands.
 */
public final class DroidClient {
	private static final Map<Integer, Event> EVENTS = new HashMap<>();
	private static @Nullable DroidStatusPayload status;
	private static long statusTime;
	private static int eventsSeen;

	private DroidClient() {
	}

	/** An animation of a droid: what, and when it started. */
	public record Event(byte event, long start) {
		long duration() {
			return switch (this.event) {
				case DroidPose.TALK -> 1200L;
				case DroidPose.ABILITY -> 1400L;
				default -> 600L;
			};
		}

		public float progress() {
			return (Util.getMillis() - this.start) / (float) this.duration();
		}
	}

	public static void init() {
		EntityRendererRegistry.register(EntityTypes.PIG, DroidRenderer::new);
		ClientPlayNetworking.registerGlobalReceiver(DroidStatusPayload.TYPE, (payload, context) -> {
			status = payload;
			statusTime = Util.getMillis();
		});
		ClientPlayNetworking.registerGlobalReceiver(DroidEventPayload.TYPE, (payload, context) -> {
			EVENTS.put(payload.entityId(), new Event(payload.event(), Util.getMillis()));
			eventsSeen++;

			if (EVENTS.size() > 64) {
				EVENTS.values().removeIf(event -> event.progress() > 1.0F);
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			status = null;
			EVENTS.clear();
		});
	}

	/** The running animation of a droid, or {@code null}. */
	static @Nullable Event event(int entityId) {
		Event event = EVENTS.get(entityId);
		return event != null && event.progress() <= 1.0F ? event : null;
	}

	/** Whether the server has droids at all (it has SaberCraft). */
	public static boolean serverSupports() {
		return ClientPlayNetworking.canSend(DroidCommandPayload.TYPE);
	}

	/** Sends a command of the droid menu to the server, which checks it. */
	public static void command(String action, DroidType droid) {
		if (serverSupports()) {
			ClientPlayNetworking.send(new DroidCommandPayload(action, droid.id()));
		}
	}

	/** What the server last said about the player's droids, or {@code null} before it said anything. */
	public static @Nullable DroidStatusPayload status() {
		return status;
	}

	/** This player's droid of that kind, as the server last said, or {@code null}. */
	public static DroidStatusPayload.@Nullable Entry entry(DroidType droid) {
		if (status == null) {
			return null;
		}

		for (DroidStatusPayload.Entry entry : status.droids()) {
			if (entry.droid().equals(droid.id())) {
				return entry;
			}
		}

		return null;
	}

	/** Seconds until the ability of this droid is ready again, counted down since the server said it. */
	public static int cooldownSeconds(DroidStatusPayload.Entry entry) {
		long left = entry.cooldown() * 50L - (Util.getMillis() - statusTime);
		return left > 0L ? (int) ((left + 999L) / 1000L) : 0;
	}

	/** How many droids this player has out, as the server last said. */
	public static int count() {
		return status != null ? status.droids().size() : 0;
	}

	/** Only for tests: the kinds of droids out. */
	public static List<String> kinds() {
		return status != null ? status.droids().stream().map(DroidStatusPayload.Entry::droid).toList() : List.of();
	}

	/** Only for tests: how many droid animations this client was told to play. */
	public static int eventsSeen() {
		return eventsSeen;
	}
}
