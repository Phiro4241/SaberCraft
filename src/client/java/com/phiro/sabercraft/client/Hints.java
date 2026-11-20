package com.phiro.sabercraft.client;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;

/**
 * Phase 27: hints shown once (kept in the immersion settings, {@link ImmersionConfig#hintsShown}),
 * with the keys as they are bound now: how to open the SaberCraft menu, at the first start in a
 * world. Also the one move of the menu key off V (Simple Voice Chat opens its menu on V): a player
 * whose key was V gets the new one once, and is told so.
 */
public final class Hints {
	/** The hints' names in the settings. */
	public static final String MENU_KEY = "menu_key";
	public static final String MENU_KEY_MOVED = "menu_key_moved";
	/** How to get into a crawl way (the first hatch looked at), and how to go on in it (the first crawl). */
	public static final String VENT = "vent";
	public static final String VENT_INSIDE = "vent_inside";
	/** Ticks one looks at a hatch before its hint comes, and before the short reminder comes. */
	private static final int VENT_LOOK = 12;
	private static final int VENT_REMIND = 100;
	/** Ticks between reminders. */
	private static final int VENT_REMIND_EVERY = 3600;
	/** Ten seconds: long enough to read while the world comes in. */
	private static final SystemToast.SystemToastId TOAST = new SystemToast.SystemToastId(10000L);
	/** How long after joining (ticks) the hint comes, once the world is there. */
	private static final int AFTER_JOIN = 60;
	/** The key the menu had before Phase 27. */
	private static final String OLD_KEY = "key.keyboard.v";

	private static int inWorld = -1;
	private static int ventLook;
	private static int crawled;
	private static long reminded = -VENT_REMIND_EVERY;
	/** Counts client ticks in a world; unlike the player's tickCount it does not start again on a dimension change. */
	private static long clock;
	private static boolean migrated;
	private static boolean moved;
	private static int toasts;

	private Hints() {
	}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(Hints::tick);
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> inWorld = 0);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> inWorld = -1);
	}

	private static void tick(Minecraft client) {
		if (!migrated) {
			migrated = true;
			moveMenuKey(client);
		}

		if (inWorld < 0 || client.player == null) {
			return;
		}

		vents(client);

		if (inWorld < AFTER_JOIN) {
			inWorld++;
			return;
		}

		if (client.gui.screen() != null) {
			return;
		}

		inWorld = Integer.MAX_VALUE;
		KeyMapping key = SaberCraftClient.CUSTOMIZE_KEY;

		if (key.isUnbound()) {
			return;
		}

		if (moved && ImmersionConfig.markShown(MENU_KEY_MOVED)) {
			ImmersionConfig.markShown(MENU_KEY);
			toast(client, Component.translatable("sabercraft.hint.menu_key_moved", keyName(key)));
		} else if (ImmersionConfig.markShown(MENU_KEY)) {
			toast(client, Component.translatable("sabercraft.hint.menu_key", keyName(key)));
		}
	}

	/**
	 * Once: the menu key bound to V (the old default, kept in the game's options) moves to the new
	 * default. A key the player chose themselves (anything but V) stays.
	 */
	static void moveMenuKey(Minecraft client) {
		ImmersionConfig config = ImmersionConfig.get();

		if (config.menuKeyMoved) {
			return;
		}

		config.menuKeyMoved = true;
		KeyMapping key = SaberCraftClient.CUSTOMIZE_KEY;

		if (OLD_KEY.equals(key.saveString()) && !key.isDefault()) {
			key.setKey(key.getDefaultKey());
			KeyMapping.resetMapping();

			if (client.options != null) {
				client.options.save();
			}

			moved = true;
		}

		ImmersionConfig.save();
	}

	/**
	 * Phase 27: the crawl ways. Looking at a hatch the first time: how one gets in (with the keys
	 * as bound now); the first time on all fours in one: how to go on and shut the hatch behind.
	 * Later, looking at a shut hatch a long while without using it: a short reminder in the action
	 * bar (at most every three minutes).
	 */
	private static void vents(Minecraft client) {
		if (client.gui.screen() != null || client.player.isSpectator()) {
			ventLook = 0;
			return;
		}

		clock++;
		net.minecraft.client.Options options = client.options;
		boolean crawling = client.player.getPose() == net.minecraft.world.entity.Pose.SWIMMING && !client.player.isInWater()
				&& com.phiro.sabercraft.interior.ShipInteriors.isInterior(client.level);
		crawled = crawling ? crawled + 1 : 0;

		if (crawled == 20 && ImmersionConfig.markShown(VENT_INSIDE)) {
			toast(client, Component.translatable("sabercraft.hint.vent_inside", keyName(options.keyUp), keyName(options.keyUse)));
		}

		boolean looking = !crawling && com.phiro.sabercraft.client.interior.InteriorClient.aimsAtHatch(client);
		ventLook = looking ? ventLook + 1 : 0;

		if (ventLook == VENT_LOOK && ImmersionConfig.markShown(VENT)) {
			toast(client, Component.translatable("sabercraft.hint.vent", keyName(options.keyUse), keyName(options.keyShift)));
			reminded = clock;
		} else if (ventLook == VENT_REMIND && clock - reminded > VENT_REMIND_EVERY
				&& com.phiro.sabercraft.interior.Vents.looked(client.player) == null) {
			reminded = clock;
			reminders++;
			client.gui.hud.setOverlayMessage(Component.translatable("sabercraft.hint.vent_short", keyName(options.keyUse)), false);
		}
	}

	private static int reminders;

	/** For the tests: the short reminders shown at a hatch. */
	public static int remindersForTest() {
		return reminders;
	}

	/** The key's name in brackets, as the hint shows it ("[I]"). */
	public static Component keyName(KeyMapping key) {
		return Component.literal("[").append(key.getTranslatedKeyMessage()).append("]");
	}

	private static void toast(Minecraft client, Component message) {
		toasts++;
		SystemToast.addOrUpdate(client.gui.toastManager(), TOAST, Component.translatable("sabercraft.hint.title"), message);
	}

	/** For the tests: toasts shown, and whether the key was moved off V at this start. */
	public static int toastsForTest() {
		return toasts;
	}

	public static boolean movedForTest() {
		return moved;
	}

	/** For the tests: the move off V tried again (it happens only once). */
	public static void moveAgainForTest(Minecraft client) {
		moveMenuKey(client);
	}

	/** For the tests: as at the first start (the key on V, not moved yet). */
	public static void replayMoveForTest(Minecraft client) {
		ImmersionConfig.get().menuKeyMoved = false;
		SaberCraftClient.CUSTOMIZE_KEY.setKey(InputConstants.getKey(OLD_KEY));
		KeyMapping.resetMapping();
		moveMenuKey(client);
	}
}
