package com.phiro.sabercraft.client.ship;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import com.phiro.sabercraft.client.ImmersionConfig;
import com.phiro.sabercraft.ship.ShipFeatures;
import com.phiro.sabercraft.ship.ShipMarker;
import com.phiro.sabercraft.ship.ShipType;

/**
 * Phase 14: the first-person view from the cockpit of a starship.
 *
 * <p>In the first-person view the camera is not at the pilot's (hidden) head on the ship's back but
 * where the pilot really sits: inside the X-Wing's canopy, in the ball of a TIE, in the Falcon's
 * cockpit tube on the right, on the bridge of the Star Destroyer ({@link ShipFeatures#cockpit()}).
 * It moves and pitches with the ship and leans with it in turns; the pilot still looks around with
 * the mouse. Seen from inside, the own model shows what lies in front (the nose, the wings, the
 * hull of the Falcon), and the frame of the cockpit is drawn around the view
 * ({@link Frame}). Only this client's camera changes: no other player sees a difference, and the
 * vanilla view keys work as always (F5 to the view behind the ship and back).
 */
public final class CockpitView {
	private CockpitView() {
	}

	/** Where the camera is and how it is turned, for the cockpit view. */
	public record Pose(Vec3 position, float yaw, float pitch, float roll) {
	}

	/**
	 * The camera of the cockpit view for this camera entity, or {@code null} when it does not apply
	 * (not a pilot of a starship). Only called for the first-person view.
	 */
	public static @Nullable Pose pose(@Nullable Entity viewer, float partialTicks) {
		Entity ship = viewer != null ? viewer.getVehicle() : null;
		ShipType type = ShipMarker.of(ship);

		if (ship == null || type == null || viewer != Minecraft.getInstance().player) {
			return null;
		}

		ShipFeatures features = ShipFeatures.of(type);
		float yaw = ship.getYRot(partialTicks);
		float pitch = ship.getXRot(partialTicks);
		float roll = ImmersionConfig.get().shipCockpitRoll ? ShipClient.visual(ship).roll(partialTicks) : 0.0F;
		double pivot = ShipModels.parts(type).isEmpty() ? 1.0 : ShipModels.parts(type).getFirst().y();
		Vec3 position = place(features.cockpit(), ship.getPosition(partialTicks), yaw, pitch, roll, pivot);
		float viewPitch = Mth.clamp(viewer.getViewXRot(partialTicks) + pitch, -90.0F, 90.0F);
		return new Pose(position, viewer.getViewYRot(partialTicks), viewPitch, roll);
	}

	/**
	 * A point in ship space in the world, turned, pitched and banked like the drawn model (see
	 * {@code DroidRenderer}): the bank turns around the ship's nose axis.
	 */
	static Vec3 place(Vec3 local, Vec3 origin, float yaw, float pitch, float roll, double pivot) {
		Vec3 forward = Vec3.directionFromRotation(pitch, yaw);
		Vec3 right = Vec3.directionFromRotation(0.0F, yaw + 90.0F);
		Vec3 up = right.cross(forward);
		double angle = Math.toRadians(roll);
		double cos = Math.cos(angle);
		double sin = Math.sin(angle);
		Vec3 bankedRight = right.scale(cos).subtract(up.scale(sin));
		Vec3 bankedUp = up.scale(cos).add(right.scale(sin));
		Vec3 centre = origin.add(0.0, pivot, 0.0);
		return centre.add(bankedRight.scale(local.x)).add(bankedUp.scale(local.y - pivot)).add(forward.scale(local.z));
	}

	/** Whether the cockpit view is shown now: a pilot in the first-person view. */
	public static boolean active(Minecraft client) {
		return ShipClient.piloting(client) != null && client.options.getCameraType().isFirstPerson();
	}

	/** The frame of the cockpit around the view: struts, the edges of the windows and the consoles. */
	static final class Frame implements HudElement {
		private static final int HULL = 0xF0181B21;
		private static final int EDGE = 0xFF3A414C;
		private static final int DARK = 0xF00C0E12;

		@Override
		public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker delta) {
			Minecraft client = Minecraft.getInstance();
			Entity ship = ShipClient.piloting(client);
			ShipType type = ShipMarker.of(ship);

			if (type == null || !active(client) || !ImmersionConfig.get().shipCockpitFrame || client.gui.hud.isHidden()) {
				return;
			}

			int w = graphics.guiWidth();
			int h = graphics.guiHeight();
			long blink = net.minecraft.util.Util.getMillis() / 500L;

			switch (ShipFeatures.of(type).frame()) {
				case "xwing" -> xwing(graphics, w, h, blink);
				case "tie" -> tie(graphics, w, h);
				case "falcon" -> falcon(graphics, w, h, blink);
				case "bridge" -> bridge(graphics, w, h, blink);
				default -> {
				}
			}
		}

		/** The X-Wing: the canopy struts rising from the corners and the dashboard with its lights. */
		private static void xwing(GuiGraphicsExtractor g, int w, int h, long blink) {
			int dash = (int) (h * 0.84);
			strut(g, (int) (w * 0.12), dash, (int) (w * 0.33), 0, 7);
			strut(g, (int) (w * 0.88), dash, (int) (w * 0.67), 0, 7);
			g.fill(0, 0, w, 6, HULL);
			g.fill(0, dash, w, h, HULL);
			g.fill(0, dash, w, dash + 1, EDGE);
			// Targeting display in the middle, lights left and right.
			g.fill(w / 2 - 22, dash + 4, w / 2 + 22, h - 2, DARK);
			g.outline(w / 2 - 22, dash + 4, 44, h - dash - 6, EDGE);
			g.fill(w / 2 - 1, dash + 8, w / 2 + 1, h - 6, 0xC040C060);
			g.fill(w / 2 - 12, (dash + h) / 2, w / 2 + 12, (dash + h) / 2 + 1, 0xC040C060);
			lights(g, (int) (w * 0.2), dash + 6, 6, blink, 0xFFE05030, 0xFF40C0F0);
			lights(g, (int) (w * 0.66), dash + 6, 6, blink + 1, 0xFF40E070, 0xFFE0C040);
		}

		/** The TIE: the round window with its eight spokes, dark all around. */
		private static void tie(GuiGraphicsExtractor g, int w, int h) {
			int cx = w / 2;
			int cy = h / 2;
			int r = (int) (Math.min(w, h) * 0.62);

			// Outside the octagon: the dark ball.
			for (int y = 0; y < h; y += 2) {
				int dy = Math.abs(y - cy);
				int half = dy > r ? -1 : (int) Math.min(r, r * 1.42 - dy);

				if (half < 0) {
					g.fill(0, y, w, y + 2, HULL);
				} else {
					g.fill(0, y, Math.max(0, cx - half), y + 2, HULL);
					g.fill(Math.min(w, cx + half), y, w, y + 2, HULL);
				}
			}

			for (int i = 0; i < 8; i++) {
				double angle = i * Math.PI / 4.0 + Math.PI / 8.0;
				strut(g, cx + (int) (Math.cos(angle) * r * 0.36), cy + (int) (Math.sin(angle) * r * 0.36), cx + (int) (Math.cos(angle) * r * 1.1),
						cy + (int) (Math.sin(angle) * r * 1.1), 3);
			}
		}

		/** The Falcon: the curved panes of the cockpit window and the consoles below. */
		private static void falcon(GuiGraphicsExtractor g, int w, int h, long blink) {
			int dash = (int) (h * 0.8);
			g.fill(0, 0, w, (int) (h * 0.07), HULL);
			strut(g, 0, (int) (h * 0.07), w, (int) (h * 0.07), 3);
			strut(g, (int) (w * 0.3), dash, (int) (w * 0.4), (int) (h * 0.07), 5);
			strut(g, (int) (w * 0.7), dash, (int) (w * 0.6), (int) (h * 0.07), 5);
			strut(g, 0, (int) (h * 0.38), (int) (w * 0.35), (int) (h * 0.3), 4);
			strut(g, w, (int) (h * 0.38), (int) (w * 0.65), (int) (h * 0.3), 4);
			strut(g, (int) (w * 0.35), (int) (h * 0.3), (int) (w * 0.65), (int) (h * 0.3), 3);
			g.fill(0, dash, w, h, HULL);
			g.fill(0, dash, w, dash + 1, EDGE);

			for (int i = 0; i < 4; i++) {
				lights(g, (int) (w * (0.08 + i * 0.24)), dash + 6, 5, blink + i, 0xFFE0A040, 0xFF60D0F0);
			}

			g.fill(w / 2 - 30, dash + 16, w / 2 + 30, h - 3, DARK);
			g.outline(w / 2 - 30, dash + 16, 60, h - dash - 19, EDGE);
		}

		/** The bridge: the tall window with its frames and the crew consoles in front. */
		private static void bridge(GuiGraphicsExtractor g, int w, int h, long blink) {
			int dash = (int) (h * 0.86);
			g.fill(0, 0, w, 8, HULL);

			for (int i = 1; i < 6; i++) {
				int x = w * i / 6;
				g.fill(x - 2, 8, x + 2, dash, HULL);
				g.fill(x - 2, 8, x - 1, dash, EDGE);
			}

			g.fill(0, (int) (h * 0.55), w, (int) (h * 0.55) + 3, HULL);
			g.fill(0, dash, w, h, HULL);
			g.fill(0, dash, w, dash + 1, EDGE);

			for (int i = 0; i < 6; i++) {
				lights(g, w * i / 6 + 10, dash + 5, 4, blink + i, 0xFF50D060, 0xFFE04040);
			}
		}

		/** A thick straight strut from one point to another. */
		private static void strut(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int thickness) {
			double length = Math.hypot(x1 - x0, y1 - y0);
			int steps = Math.max(1, (int) (length / 2.0));
			int half = thickness / 2;

			for (int i = 0; i <= steps; i++) {
				double t = i / (double) steps;
				int x = (int) Math.round(x0 + (x1 - x0) * t);
				int y = (int) Math.round(y0 + (y1 - y0) * t);
				g.fill(x - half, y - half, x - half + thickness, y - half + thickness, HULL);
			}
		}

		/** A row of small blinking lamps. */
		private static void lights(GuiGraphicsExtractor g, int x, int y, int count, long blink, int a, int b) {
			for (int i = 0; i < count; i++) {
				boolean on = ((blink + i * 3L) % 5L) != 0L;
				g.fill(x + i * 7, y, x + i * 7 + 4, y + 3, on ? (i % 2 == 0 ? a : b) : 0xFF30343A);
			}
		}
	}
}
