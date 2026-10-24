package com.phiro.sabercraft.client.render;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.phys.Vec3;

import com.phiro.sabercraft.client.blade.BladeStyle;
import com.phiro.sabercraft.client.combat.CombatConfig;
import com.phiro.sabercraft.client.saber.SaberTracker;

/**
 * The light trail a blade leaves behind during an attack.
 *
 * <p>Every frame the blade's base and tip are remembered. In third person they are kept in world
 * space, so the trail stays where the blade was while the holder moves on. In first person the
 * hand is drawn in view space, so the trail is kept there too and sweeps across the screen with
 * the blade. The last ~0.1 seconds (two game ticks) of positions are drawn as a fading ribbon in the blade's glow
 * color, with the translucent haze pipeline so the color also holds on bright backgrounds.
 *
 * <p>The ribbon is drawn together with the blade, so it costs nothing while no one is attacking.
 */
public final class BladeTrails {
	/** How long a position stays in the trail, in ticks, at trail strength 1 (about 0.1 s). */
	private static final float TRAIL_TICKS = 2.2F;
	private static final int MAX_SAMPLES = 24;
	/** Trails not updated for this long are forgotten. */
	private static final long FORGET_MILLIS = 1000L;

	private static final Map<Key, Trail> TRAILS = new HashMap<>();
	private static long lastCleanup;

	private BladeTrails() {
	}

	/** One blade of one holder in one view. {@code blade} tells the two blades of a staff apart. */
	private record Key(int entityId, int hand, boolean firstPerson, Object blade) {
	}

	private record Sample(float time, double bx, double by, double bz, double tx, double ty, double tz, float strength) {
	}

	private static final class Trail {
		final ArrayDeque<Sample> samples = new ArrayDeque<>();
		long updated;
	}

	/**
	 * Records the current blade position and draws the trail behind it. The pose stack must be at
	 * the blade's base, with the blade pointing along +Y.
	 */
	public static void submit(
			BladeRenderState.Swing swing, Object blade, PoseStack poseStack, net.minecraft.client.renderer.SubmitNodeCollector collector,
			BladeStyle style, float length
	) {
		CombatConfig config = CombatConfig.get();
		Minecraft client = Minecraft.getInstance();

		// Entity previews in screens (inventory) render the player as well, at a GUI position.
		if (!config.bladeTrails || config.trailStrength <= 0.0F || client.gui.screen() != null) {
			return;
		}

		// Game time, not wall time: the trail has the same length at any frame rate and stops
		// while the game is paused.
		long millis = Util.getMillis();
		float now = SaberTracker.INSTANCE.time(client.getDeltaTracker().getGameTimeDeltaPartialTick(true));
		Key key = new Key(swing.entityId(), swing.hand(), swing.firstPerson(), blade);
		Trail trail = TRAILS.computeIfAbsent(key, k -> new Trail());
		trail.updated = millis;

		Matrix4f pose = new Matrix4f(poseStack.last().pose());
		Vec3 camera = swing.firstPerson() ? Vec3.ZERO : client.gameRenderer.mainCamera().position();
		// Only the outer part of the blade leaves a trail, like a long exposure of a glowing rod:
		// it moves fastest and a ribbon right down to the hilt would look like a solid sheet.
		Vector3f base = pose.transformPosition(0.0F, length * 0.4F, 0.0F, new Vector3f());
		Vector3f tip = pose.transformPosition(0.0F, length, 0.0F, new Vector3f());
		Sample current = new Sample(now, base.x + camera.x, base.y + camera.y, base.z + camera.z, tip.x + camera.x, tip.y + camera.y, tip.z + camera.z, swing.strength());

		Sample last = trail.samples.peekLast();

		// Frames are not always in time order: a screenshot is rendered at the start of the tick.
		// Drop what lies ahead of this frame, the rest of the trail still fits.
		while (last != null && last.time > now) {
			trail.samples.removeLast();
			last = trail.samples.peekLast();
		}

		if (last == null || last.time != now) {
			trail.samples.addLast(current);
		}

		float keep = Math.max(0.6F, TRAIL_TICKS * Math.min(config.trailStrength, 2.0F));

		while (!trail.samples.isEmpty() && (now - trail.samples.peekFirst().time > keep || trail.samples.size() > MAX_SAMPLES)) {
			trail.samples.removeFirst();
		}

		cleanup(millis);

		if (trail.samples.size() < 2) {
			return;
		}

		// Back from world (or view) space into the space of this pose.
		Matrix4f toLocal = new Matrix4f(pose).invert();
		int count = trail.samples.size();
		float[] points = new float[count * 6];
		float[] alphas = new float[count];
		int i = 0;
		Vector3f scratch = new Vector3f();

		for (Sample sample : trail.samples) {
			toLocal.transformPosition((float) (sample.bx - camera.x), (float) (sample.by - camera.y), (float) (sample.bz - camera.z), scratch);
			points[i * 6] = scratch.x;
			points[i * 6 + 1] = scratch.y;
			points[i * 6 + 2] = scratch.z;
			toLocal.transformPosition((float) (sample.tx - camera.x), (float) (sample.ty - camera.y), (float) (sample.tz - camera.z), scratch);
			points[i * 6 + 3] = scratch.x;
			points[i * 6 + 4] = scratch.y;
			points[i * 6 + 5] = scratch.z;
			float age = (now - sample.time) / keep;
			alphas[i] = (1.0F - age) * (1.0F - age) * sample.strength;
			i++;
		}

		float opacity = 0.34F * Mth.clamp(config.trailStrength, 0.0F, 1.5F) * Math.min(style.glowStrength(), 1.5F);
		int glow = style.glowColor();
		int bloom = style.bloomColor();

		collector.order(0).submitCustomGeometry(poseStack, SaberRenderTypes.BLADE_HAZE, (p, buffer) -> ribbon(p, buffer, points, alphas, glow, bloom, opacity));
	}

	private static void ribbon(PoseStack.Pose pose, VertexConsumer buffer, float[] points, float[] alphas, int glow, int bloom, float opacity) {
		int count = alphas.length;

		// Oldest sample first. The newest end of the ribbon is the brightest, and the tip edge is
		// brighter than the base edge, like a long exposure of a glowing rod.
		for (int i = 0; i + 1 < count; i++) {
			float a0 = Mth.clamp(alphas[i], 0.0F, 1.0F) * opacity;
			float a1 = Mth.clamp(alphas[i + 1], 0.0F, 1.0F) * opacity;
			int o0 = i * 6;
			int o1 = (i + 1) * 6;
			vertex(pose, buffer, points, o0, argb(a0 * 0.25F, glow));
			vertex(pose, buffer, points, o0 + 3, argb(a0, bloom));
			vertex(pose, buffer, points, o1 + 3, argb(a1, bloom));
			vertex(pose, buffer, points, o1, argb(a1 * 0.25F, glow));
		}
	}

	private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, float[] points, int offset, int argb) {
		buffer.addVertex(pose, points[offset], points[offset + 1], points[offset + 2]).setColor(argb);
	}

	private static int argb(float alpha, int rgb) {
		return Mth.clamp(Math.round(alpha * 255.0F), 0, 255) << 24 | rgb & 0xFFFFFF;
	}

	private static void cleanup(long now) {
		if (now - lastCleanup < FORGET_MILLIS) {
			return;
		}

		lastCleanup = now;
		Iterator<Trail> iterator = TRAILS.values().iterator();

		while (iterator.hasNext()) {
			if (now - iterator.next().updated > FORGET_MILLIS) {
				iterator.remove();
			}
		}
	}

	/** Number of trails in memory, for tests. */
	public static int count() {
		return TRAILS.size();
	}
}
