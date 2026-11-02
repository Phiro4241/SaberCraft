package com.phiro.sabercraft.client.ship;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.client.SaberCraftClient;
import com.phiro.sabercraft.network.HyperspacePayload;
import com.phiro.sabercraft.ship.ShipMarker;
import com.phiro.sabercraft.ship.ShipType;

/**
 * Phase 14: the hyperspace jump on this client. The key (X by default) asks the server for a jump
 * of the ship the player flies; the server checks everything and tells every client that sees the
 * ship (see {@link HyperspacePayload}), so all of them show the same:
 *
 * <ul>
 * <li><b>Charging</b> (1.5 s): light gathers around the ship; for the pilot the stars start to
 * stretch at the edges of the view.</li>
 * <li><b>The jump</b>: a streak of light where it was and where it comes out; the pilot sees the
 * tunnel: white lines rushing out from the middle, a flash, then the view clears.</li>
 * <li><b>Called off</b> (an obstacle appeared, the ship was hit): the stretching snaps back.</li>
 * </ul>
 */
public final class HyperspaceClient {
	public static final KeyMapping JUMP_KEY = new KeyMapping("key.sabercraft.hyperspace", InputConstants.Type.KEYSYM, InputConstants.KEY_X,
			SaberCraftClient.KEY_CATEGORY);

	private static int ownShip = -1;
	private static long chargeStart = -1L;
	private static int chargeTicks;
	private static long tunnelStart = -1L;
	private static int tunnelTicks;
	private static int jumpsSeen;
	private static @Nullable Vec3 direction;

	private HyperspaceClient() {
	}

	public static void init() {
		KeyMappingHelper.registerKeyMapping(JUMP_KEY);
		ClientPlayNetworking.registerGlobalReceiver(HyperspacePayload.TYPE, (payload, context) -> receive(context.client(), payload));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (JUMP_KEY.consumeClick()) {
				request(client);
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			chargeStart = -1L;
			tunnelStart = -1L;
			ownShip = -1;
		});
		HudElementRegistry.addLast(SaberCraft.id("hyperspace"), new Overlay());
	}

	/** The key: the server decides whether the jump can go. */
	static void request(Minecraft client) {
		Entity ship = ShipClient.piloting(client);
		ShipType type = ShipMarker.of(ship);

		if (type != null) {
			ShipClient.command("hyperspace", type);
		}
	}

	static void receive(Minecraft client, HyperspacePayload payload) {
		ClientLevel level = client.level;

		if (level == null) {
			return;
		}

		Entity ship = level.getEntity(payload.entityId());
		boolean own = ship != null && client.player != null && client.player.getVehicle() == ship;
		long now = level.getGameTime();
		Vec3 way = payload.to().subtract(payload.from());
		direction = way.lengthSqr() > 1.0E-4 ? way.normalize() : null;

		switch (payload.stage()) {
			case HyperspacePayload.CHARGE -> {
				if (own) {
					ownShip = payload.entityId();
					chargeStart = now;
					chargeTicks = Math.max(1, payload.ticks());
				}

				gather(level, payload.from(), ship);
			}
			case HyperspacePayload.JUMP -> {
				jumpsSeen++;

				if (own || payload.entityId() == ownShip) {
					chargeStart = -1L;
					tunnelStart = now;
					tunnelTicks = Math.max(1, payload.ticks());
				}

				streak(level, payload.from(), direction, 1.0);
			}
			case HyperspacePayload.ARRIVE -> streak(level, payload.to(), direction, -1.0);
			case HyperspacePayload.ABORT -> {
				if (payload.entityId() == ownShip) {
					chargeStart = -1L;
				}
			}
			default -> {
			}
		}
	}

	/** Light gathering around a ship that charges its hyperdrive. */
	private static void gather(ClientLevel level, Vec3 at, @Nullable Entity ship) {
		RandomSource random = level.getRandom();
		double size = ship != null ? Math.max(2.0, ship.getBbWidth()) : 3.0;

		for (int i = 0; i < 24; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			double r = size * (0.8 + random.nextDouble() * 0.6);
			Vec3 p = at.add(Math.cos(angle) * r, 0.5 + random.nextDouble() * size * 0.5, Math.sin(angle) * r);
			Vec3 in = at.add(0.0, 1.0, 0.0).subtract(p).scale(0.08);
			level.addParticle(ParticleTypes.END_ROD, p.x, p.y, p.z, in.x, in.y, in.z);
		}
	}

	/** A line of light along the way of the jump: behind the ship at the start, ahead of it at the arrival. */
	private static void streak(ClientLevel level, Vec3 at, @Nullable Vec3 way, double sign) {
		if (way == null) {
			return;
		}

		for (int i = 0; i < 40; i++) {
			Vec3 p = at.add(way.scale(sign * i * 0.8)).add(0.0, 1.0, 0.0);
			level.addParticle(ParticleTypes.END_ROD, p.x, p.y, p.z, way.x * sign * 0.6, way.y * sign * 0.6, way.z * sign * 0.6);
		}
	}

	/** How far the charge is, 0..1, or -1 when the own ship is not charging. */
	public static float charge(float partialTicks) {
		Minecraft client = Minecraft.getInstance();

		if (chargeStart < 0L || client.level == null) {
			return -1.0F;
		}

		float age = client.level.getGameTime() - chargeStart + partialTicks;

		if (age > chargeTicks + 10) {
			chargeStart = -1L;
			return -1.0F;
		}

		return Mth.clamp(age / chargeTicks, 0.0F, 1.0F);
	}

	/** How far the tunnel is, 0..1, or -1 when the own ship is not in it. */
	public static float tunnel(float partialTicks) {
		Minecraft client = Minecraft.getInstance();

		if (tunnelStart < 0L || client.level == null) {
			return -1.0F;
		}

		float age = client.level.getGameTime() - tunnelStart + partialTicks;

		if (age > tunnelTicks) {
			tunnelStart = -1L;
			return -1.0F;
		}

		return Mth.clamp(age / tunnelTicks, 0.0F, 1.0F);
	}

	/** Only for tests: how many jumps this client was told about. */
	public static int jumpsSeen() {
		return jumpsSeen;
	}

	/** The pilot's view: stretching stars while charging, the tunnel and the flash. */
	static final class Overlay implements HudElement {
		@Override
		public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker delta) {
			Minecraft client = Minecraft.getInstance();
			float partial = delta.getGameTimeDeltaPartialTick(false);
			float charge = charge(partial);
			float tunnel = tunnel(partial);

			if (charge < 0.0F && tunnel < 0.0F) {
				return;
			}

			int width = graphics.guiWidth();
			int height = graphics.guiHeight();
			int cx = width / 2;
			int cy = height / 2;
			RandomSource random = RandomSource.create(7L);
			float stretch = tunnel >= 0.0F ? 1.0F : charge * 0.4F;
			int lines = tunnel >= 0.0F ? 140 : 70;
			float time = client.level != null ? (client.level.getGameTime() + partial) : 0.0F;

			if (tunnel >= 0.0F) {
				// A blue-black tunnel behind the streaks, fading as the ship comes out.
				int dark = (int) (200 * (1.0F - tunnel) * Math.min(1.0F, tunnel * 6.0F + 0.4F));
				graphics.fill(0, 0, width, height, dark << 24 | 0x040818);
			}

			for (int i = 0; i < lines; i++) {
				double angle = random.nextDouble() * Math.PI * 2.0;
				float speed = 0.5F + random.nextFloat();
				float phase = (random.nextFloat() + time * 0.06F * speed * (tunnel >= 0.0F ? 3.0F : 1.0F)) % 1.0F;
				float near = 0.12F + phase * 0.9F;
				float far = near + 0.05F + stretch * (0.15F + phase * 0.35F);
				double max = Math.hypot(cx, cy);
				int alpha = (int) (Mth.clamp(phase * 1.5F, 0.0F, 1.0F) * (tunnel >= 0.0F ? 230 : 160 * charge));
				int color = alpha << 24 | (random.nextFloat() < 0.3F ? 0xB8D8FF : 0xFFFFFF);
				line(graphics, cx + Math.cos(angle) * max * near, cy + Math.sin(angle) * max * near, cx + Math.cos(angle) * max * far,
						cy + Math.sin(angle) * max * far, color);
			}

			if (tunnel >= 0.0F && tunnel < 0.25F) {
				// The flash of the jump.
				int flash = (int) (255 * (1.0F - tunnel / 0.25F));
				graphics.fill(0, 0, width, height, flash << 24 | 0xE8F0FF);
			}
		}

		/** A thin line of small squares (the HUD has no line primitive). */
		private static void line(GuiGraphicsExtractor graphics, double x0, double y0, double x1, double y1, int color) {
			double length = Math.hypot(x1 - x0, y1 - y0);
			int steps = Math.max(1, (int) (length / 2.0));

			for (int i = 0; i <= steps; i++) {
				double t = i / (double) steps;
				int x = (int) Math.round(x0 + (x1 - x0) * t);
				int y = (int) Math.round(y0 + (y1 - y0) * t);
				graphics.fill(x, y, x + 1, y + 1, color);
			}
		}
	}
}
