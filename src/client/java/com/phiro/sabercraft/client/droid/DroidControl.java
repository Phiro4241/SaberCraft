package com.phiro.sabercraft.client.droid;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.client.SaberCraftClient;
import com.phiro.sabercraft.client.ability.SaberAbilityClient;
import com.phiro.sabercraft.client.ship.ShipClient;
import com.phiro.sabercraft.droid.DroidMarker;
import com.phiro.sabercraft.droid.DroidType;
import com.phiro.sabercraft.droid.Droids;
import com.phiro.sabercraft.network.DroidStatusPayload;
import com.phiro.sabercraft.network.DroidTargetPayload;

/**
 * Phase 12: commanding a droid by looking at it. Look at one of your droids (up to
 * {@link Droids#TARGET_RANGE} blocks, not through walls) and press
 *
 * <ul>
 * <li>{@link #ABILITY_KEY} (N by default): its own ability, for example R2-D2 repairs, BB-8 scans,
 * B1 fires;</li>
 * <li>{@link #ORDER_KEY} (U by default): wait here, or follow again.</li>
 * </ul>
 *
 * <p>Both keys are in the SaberCraft group of the key settings. A small hint under the crosshair
 * names the droid looked at, its ability and the keys ({@link DroidTargetHud}). The droid menu
 * keeps working as before.
 *
 * <p>So that nothing is triggered by accident, a key press only counts when no screen is open,
 * the player is not flying a ship, not using an item (eating, drawing a bow or blaster, blocking),
 * not holding the attack, use or guard key, and is looking at their own droid. Holding the key
 * fires once. The server checks everything again.
 */
public final class DroidControl {
	public static final KeyMapping ABILITY_KEY = new KeyMapping("key.sabercraft.droid_ability", InputConstants.Type.KEYSYM, InputConstants.KEY_N,
			SaberCraftClient.KEY_CATEGORY);
	public static final KeyMapping ORDER_KEY = new KeyMapping("key.sabercraft.droid_order", InputConstants.Type.KEYSYM, InputConstants.KEY_U,
			SaberCraftClient.KEY_CATEGORY);
	/** Milliseconds between two commands by key, against double presses. */
	private static final long DEBOUNCE_MILLIS = 250L;
	/** Milliseconds between two "look at a droid" hints. */
	private static final long HINT_MILLIS = 1500L;

	private static @Nullable Entity target;
	private static long lastSent;
	private static long lastHint;
	private static int sent;

	private DroidControl() {
	}

	public static void init() {
		KeyMappingHelper.registerKeyMapping(ABILITY_KEY);
		KeyMappingHelper.registerKeyMapping(ORDER_KEY);
		ClientTickEvents.END_CLIENT_TICK.register(DroidControl::tick);
		HudElementRegistry.addLast(SaberCraft.id("droid_target"), new DroidTargetHud());
	}

	private static void tick(Minecraft client) {
		LocalPlayer player = client.player;
		target = player != null && client.level != null ? pick(client, player) : null;
		boolean ability = false;
		boolean order = false;

		while (ABILITY_KEY.consumeClick()) {
			ability = true;
		}

		while (ORDER_KEY.consumeClick()) {
			order = true;
		}

		if (ability || order) {
			press(client, ability ? "ability" : "stay");
		}
	}

	/** A key was pressed: sends the command for the droid looked at, if nothing speaks against it. */
	static boolean press(Minecraft client, String action) {
		LocalPlayer player = client.player;

		if (player == null || !canCommand(client, player)) {
			return false;
		}

		long now = Util.getMillis();
		Entity droid = target;

		if (droid == null || !isOwn(droid)) {
			if (now - lastHint > HINT_MILLIS && DroidClient.count() > 0) {
				lastHint = now;
				player.sendOverlayMessage(Component.translatable(droid == null ? "sabercraft.droid.key.look" : "sabercraft.droid.key.not_yours"));
			}

			return false;
		}

		if (now - lastSent < DEBOUNCE_MILLIS || !ClientPlayNetworking.canSend(DroidTargetPayload.TYPE)) {
			return false;
		}

		lastSent = now;
		sent++;
		ClientPlayNetworking.send(new DroidTargetPayload(droid.getId(), action));
		return true;
	}

	/**
	 * Whether a droid key may do something now: in the world, no screen, not in a ship, not busy
	 * with an item or another key.
	 */
	static boolean canCommand(Minecraft client, LocalPlayer player) {
		return client.gui.screen() == null
				&& player.isAlive()
				&& !player.isSpectator()
				&& ShipClient.piloting(client) == null
				&& !player.isUsingItem()
				&& !client.options.keyAttack.isDown()
				&& !client.options.keyUse.isDown()
				&& !SaberAbilityClient.GUARD_KEY.isDown();
	}

	/** The droid the player looks at, up to the target range and not through blocks, or {@code null}. */
	private static @Nullable Entity pick(Minecraft client, LocalPlayer player) {
		Entity camera = client.getCameraEntity() != null ? client.getCameraEntity() : player;
		Vec3 eye = camera.getEyePosition(1.0F);
		Vec3 look = camera.getViewVector(1.0F);
		double range = Droids.TARGET_RANGE;
		Vec3 end = eye.add(look.scale(range));
		HitResult block = client.level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, camera));
		double limit = block.getType() == HitResult.Type.MISS ? range : block.getLocation().distanceTo(eye);
		AABB box = camera.getBoundingBox().expandTowards(look.scale(limit)).inflate(1.0);
		// A little wider than the droid itself: small droids (the mouse droid) are easy to aim at.
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(camera, eye, eye.add(look.scale(limit)), box,
				entity -> DroidMarker.of(entity) != null && entity.isAlive(), limit * limit);
		return hit != null ? hit.getEntity() : null;
	}

	/** Whether the droid is one of this player's, as the server last said. */
	static boolean isOwn(Entity droid) {
		DroidStatusPayload status = DroidClient.status();

		if (status == null) {
			return false;
		}

		for (DroidStatusPayload.Entry entry : status.droids()) {
			if (entry.entityId() == droid.getId()) {
				return true;
			}
		}

		return false;
	}

	/** The droid looked at now, or {@code null}. */
	public static @Nullable Entity target() {
		return target;
	}

	/** The kind of the droid looked at now, or {@code null}. */
	public static @Nullable DroidType targetType() {
		return target != null ? DroidMarker.of(target) : null;
	}

	/** Only for tests: how many commands were sent by key. */
	public static int sent() {
		return sent;
	}

	/** Only for tests: as if the key was pressed now. */
	public static boolean pressForTest(Minecraft client, String action) {
		lastSent = 0L;
		target = client.player != null && client.level != null ? pick(client, client.player) : null;
		return press(client, action);
	}
}
