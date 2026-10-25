package com.phiro.sabercraft.advancement;

import java.util.Locale;

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.ability.SaberAbility;
import com.phiro.sabercraft.saber.SaberVariant;

/**
 * The SaberCraft advancements (Phase 11). The advancement files (data/sabercraft/advancement,
 * written by tools/gen_advancements.py) only have "impossible" criteria; this class awards them on
 * the server when the real thing happens: a saber in hand, a blaster shot, a long shot, a duel,
 * every saber ability, two enemies in one spin, a finishing blow, later droids and the X-Wing.
 *
 * <p>Minecraft's own advancement progress does the rest: a criterion is awarded once (a second
 * award does nothing, so there is no second toast or chat line), it is saved with the player and
 * survives a restart, and {@code /advancement revoke} works as for any advancement.
 */
public final class SaberAdvancements {
	public static final String ROOT = "root";
	public static final String FORCE_AWAKENS = "force_awakens";
	public static final String FIRST_SHOT = "first_shot";
	public static final String SHARPSHOOTER = "sharpshooter";
	public static final String DUEL_BEGINS = "duel_begins";
	public static final String DARK_LORD = "dark_lord";
	public static final String CHOSEN_ONE = "chosen_one";
	public static final String SPIN_SPEED = "spin_speed";
	public static final String FINISHER = "finisher";
	public static final String DROID_FRIEND = "droid_friend";
	public static final String LOYAL_COMPANION = "loyal_companion";

	/** A blaster hit from at least this far counts as a precision shot, in blocks. */
	public static final double SHARPSHOOTER_DISTANCE = 30.0;

	private SaberAdvancements() {
	}

	public static void init() {
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> award(handler.getPlayer(), ROOT, "joined"));
		ServerTickEvents.END_SERVER_TICK.register(SaberAdvancements::tick);
	}

	/** Once a second: a lightsaber in hand awakens the Force. */
	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % 20 != 0) {
			return;
		}

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!player.isSpectator() && (SaberVariant.canHold(player.getMainHandItem()) || SaberVariant.canHold(player.getOffhandItem()))) {
				award(player, FORCE_AWAKENS, "saber");
			}
		}
	}

	/** A saber ability was used: one step towards "The Chosen One". */
	public static void abilityUsed(ServerPlayer player, SaberAbility ability) {
		award(player, CHOSEN_ONE, ability.name().toLowerCase(Locale.ROOT));
	}

	/** Awards an advancement with a single criterion. */
	public static boolean award(ServerPlayer player, String name) {
		AdvancementHolder holder = holder(player, name);

		if (holder == null) {
			return false;
		}

		String criterion = holder.value().criteria().keySet().iterator().next();
		return player.getAdvancements().award(holder, criterion);
	}

	/**
	 * Awards one criterion of an advancement. Returns whether it was new; already done criteria and
	 * fake players (as from other mods) are left alone.
	 */
	public static boolean award(ServerPlayer player, String name, String criterion) {
		AdvancementHolder holder = holder(player, name);
		return holder != null && player.getAdvancements().award(holder, criterion);
	}

	/** Whether the player has the advancement, for tests and the menus. */
	public static boolean has(ServerPlayer player, String name) {
		AdvancementHolder holder = holder(player, name);
		return holder != null && player.getAdvancements().getOrStartProgress(holder).isDone();
	}

	/** The progress of an advancement, for tests. */
	public static AdvancementProgress progress(ServerPlayer player, String name) {
		AdvancementHolder holder = holder(player, name);
		return holder != null ? player.getAdvancements().getOrStartProgress(holder) : new AdvancementProgress();
	}

	private static AdvancementHolder holder(ServerPlayer player, String name) {
		MinecraftServer server = player.level().getServer();

		if (server == null || player.connection == null || player instanceof FakePlayer) {
			return null;
		}

		AdvancementHolder holder = server.getAdvancements().get(SaberCraft.id(name));

		if (holder == null) {
			SaberCraft.LOGGER.warn("Missing advancement sabercraft:{} (data pack removed?)", name);
		}

		return holder;
	}
}
