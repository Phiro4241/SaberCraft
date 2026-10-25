package com.phiro.sabercraft.client.render;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import org.joml.Quaternionf;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.client.blade.BladeStyle;
import com.phiro.sabercraft.client.combat.CombatConfig;

/**
 * Saber hit feedback that stays a moment (Phase 11), all drawn by each client for what it sees,
 * never stored on the entity and never touching its skin or textures:
 *
 * <ul>
 * <li>Hit marks: where a blade struck, a small glowing spot in the blade color sits on the target
 * and moves with it, bright at first and then a fading ember, gone after a few seconds. At most a
 * handful per target; a stylized glow, nothing cut or bleeding.</li>
 * <li>The finisher: a ring of light in the blade color that sweeps out from the defeated target,
 * and a short flash of the screen in that color for everyone close by.</li>
 * </ul>
 *
 * Every client gets each hit and each finisher exactly once from the server (the damage event, the
 * finisher message), so there are no doubles in multiplayer.
 */
public final class HitMarks {
	/** How long a mark glows, in milliseconds. */
	static final long MARK_MILLIS = 4000L;
	/** The bright first part of a mark. */
	private static final long FRESH_MILLIS = 350L;
	private static final int MAX_MARKS = 6;
	private static final long RING_MILLIS = 550L;
	private static final long FLASH_MILLIS = 280L;
	/** Viewers closer than this see the screen flash of a finisher. */
	private static final double FLASH_DISTANCE = 24.0;

	/** A mark in the body frame of the target: x right, y up from the feet, z forward. */
	private record Mark(float x, float y, float z, BladeStyle style, long start, float size) {
	}

	private record Ring(int targetId, Vec3 fallback, BladeStyle style, long start) {
	}

	private static final Map<Integer, ArrayDeque<Mark>> MARKS = new HashMap<>();
	private static final List<Ring> RINGS = new ArrayList<>();
	private static long flashStart = Long.MIN_VALUE / 2;
	private static int flashRgb;
	private static int added;
	private static int finishersSeen;

	private HitMarks() {
	}

	public static void init() {
		LevelRenderEvents.COLLECT_SUBMITS.register(context -> submit(context.poseStack(), context.submitNodeCollector()));
		ClientTickEvents.END_CLIENT_TICK.register(client -> cleanup(client));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			MARKS.clear();
			RINGS.clear();
		});
		HudElementRegistry.addLast(SaberCraft.id("finisher_flash"), (graphics, delta) -> {
			float t = (Util.getMillis() - flashStart) / (float) FLASH_MILLIS;

			if (t < 0.0F || t >= 1.0F || !CombatConfig.get().finisherEffects) {
				return;
			}

			float alpha = 0.42F * (1.0F - t) * (1.0F - t);
			graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), Mth.clamp(Math.round(alpha * 255.0F), 0, 255) << 24 | flashRgb & 0xFFFFFF);
		});
	}

	/** A blade struck {@code target} at {@code at} (world space). */
	public static void add(LivingEntity target, Vec3 at, BladeStyle style, float size) {
		if (!CombatConfig.get().hitMarks) {
			return;
		}

		Vec3 offset = at.subtract(target.position());
		float yaw = target.yBodyRot * Mth.DEG_TO_RAD;
		float cos = Mth.cos(yaw);
		float sin = Mth.sin(yaw);
		// World to body frame: undo the body turn (Minecraft yaw 0 looks along +Z).
		float x = (float) (offset.x * cos + offset.z * sin);
		float z = (float) (-offset.x * sin + offset.z * cos);
		ArrayDeque<Mark> marks = MARKS.computeIfAbsent(target.getId(), id -> new ArrayDeque<>());

		if (marks.size() >= MAX_MARKS) {
			marks.removeFirst();
		}

		marks.addLast(new Mark(x, (float) offset.y, z, style, Util.getMillis(), size));
		added++;
	}

	/** A finisher on {@code target}: the ring and, for viewers close by, the screen flash. */
	public static void finisher(LivingEntity target, BladeStyle style) {
		finishersSeen++;

		if (!CombatConfig.get().finisherEffects) {
			return;
		}

		long now = Util.getMillis();
		RINGS.add(new Ring(target.getId(), target.position().add(0.0, target.getBbHeight() * 0.55, 0.0), style, now));
		Minecraft client = Minecraft.getInstance();

		if (client.player != null && client.player.distanceTo(target) <= FLASH_DISTANCE) {
			flashStart = now;
			flashRgb = style.glowColor();
		}
	}

	/** How many marks a target carries right now, for tests. */
	public static int marksOn(Entity entity) {
		ArrayDeque<Mark> marks = MARKS.get(entity.getId());
		return marks == null ? 0 : marks.size();
	}

	/** How many marks were ever added, for tests. */
	public static int added() {
		return added;
	}

	/** How many finishers this client was told about, for tests (each one must come once). */
	public static int finishersSeen() {
		return finishersSeen;
	}

	/** Whether a finisher ring is showing, for tests. */
	public static boolean ringShowing() {
		return !RINGS.isEmpty();
	}

	/** How strong the finisher flash is right now, 0..1, for tests. */
	public static float flash() {
		float t = (Util.getMillis() - flashStart) / (float) FLASH_MILLIS;
		return t < 0.0F || t >= 1.0F ? 0.0F : 1.0F - t;
	}

	private static void cleanup(Minecraft client) {
		long now = Util.getMillis();
		Iterator<Map.Entry<Integer, ArrayDeque<Mark>>> entries = MARKS.entrySet().iterator();

		while (entries.hasNext()) {
			Map.Entry<Integer, ArrayDeque<Mark>> entry = entries.next();
			entry.getValue().removeIf(mark -> now - mark.start() > MARK_MILLIS);
			Entity entity = client.level != null ? client.level.getEntity(entry.getKey()) : null;

			if (entry.getValue().isEmpty() || entity == null || entity.isRemoved()) {
				entries.remove();
			}
		}

		RINGS.removeIf(ring -> now - ring.start() > RING_MILLIS);
	}

	private static void submit(PoseStack poseStack, SubmitNodeCollector collector) {
		Minecraft client = Minecraft.getInstance();

		if (client.level == null || MARKS.isEmpty() && RINGS.isEmpty() || ShaderCompat.renderingShadowPass()) {
			return;
		}

		Vec3 camera = client.gameRenderer.mainCamera().position();
		Quaternionf facing = client.gameRenderer.mainCamera().rotation();
		float partial = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		long now = Util.getMillis();

		for (Map.Entry<Integer, ArrayDeque<Mark>> entry : MARKS.entrySet()) {
			Entity entity = client.level.getEntity(entry.getKey());

			if (!(entity instanceof LivingEntity living) || living.isInvisible()
					|| living == client.player && client.options.getCameraType() == CameraType.FIRST_PERSON) {
				continue;
			}

			Vec3 base = living.getPosition(partial);
			float yaw = Mth.rotLerp(partial, living.yBodyRotO, living.yBodyRot) * Mth.DEG_TO_RAD;
			float cos = Mth.cos(yaw);
			float sin = Mth.sin(yaw);

			for (Mark mark : entry.getValue()) {
				float age = now - mark.start();
				// Body frame back to the world.
				double wx = mark.x() * cos - mark.z() * sin;
				double wz = mark.x() * sin + mark.z() * cos;
				Vec3 at = base.add(wx, mark.y(), wz);
				float fresh = Mth.clamp(1.0F - age / FRESH_MILLIS, 0.0F, 1.0F);
				float fade = Mth.clamp(1.0F - (age - FRESH_MILLIS) / (MARK_MILLIS - FRESH_MILLIS), 0.0F, 1.0F);
				float strength = Math.max(fresh, 0.55F * fade * fade);
				float radius = mark.size() * (0.6F + 0.4F * fresh);
				poseStack.pushPose();
				poseStack.translate(at.x - camera.x, at.y - camera.y, at.z - camera.z);
				poseStack.mulPose(facing);
				BladeStyle style = mark.style();
				collector.order(0).submitCustomGeometry(poseStack, SaberRenderTypes.haze(false),
						(pose, buffer) -> disc(pose, buffer, style.glowColor(), strength * 0.8F, radius * 1.8F));
				collector.order(1).submitCustomGeometry(poseStack, SaberRenderTypes.bloom(false),
						(pose, buffer) -> disc(pose, buffer, fresh > 0.0F ? style.coreColor() : style.bloomColor(), strength, radius * 0.6F));
				poseStack.popPose();
			}
		}

		for (Ring ring : RINGS) {
			Entity target = client.level.getEntity(ring.targetId());
			Vec3 at = target != null ? target.getPosition(partial).add(0.0, target.getBbHeight() * 0.55, 0.0) : ring.fallback();
			float t = Mth.clamp((now - ring.start()) / (float) RING_MILLIS, 0.0F, 1.0F);
			float eased = 1.0F - (1.0F - t) * (1.0F - t) * (1.0F - t);
			float radius = 0.3F + 2.2F * eased;
			float alpha = (1.0F - t) * (1.0F - t);
			poseStack.pushPose();
			poseStack.translate(at.x - camera.x, at.y - camera.y, at.z - camera.z);
			poseStack.mulPose(facing);
			BladeStyle style = ring.style();
			collector.order(0).submitCustomGeometry(poseStack, SaberRenderTypes.haze(false),
					(pose, buffer) -> ring(pose, buffer, style.glowColor(), alpha * 0.9F, radius, 0.35F + 0.25F * (1.0F - t)));
			collector.order(1).submitCustomGeometry(poseStack, SaberRenderTypes.bloom(false),
					(pose, buffer) -> ring(pose, buffer, style.coreColor(), alpha, radius, 0.08F));
			// A burst in the middle for the first moment.
			if (t < 0.35F) {
				float burst = 1.0F - t / 0.35F;
				collector.order(2).submitCustomGeometry(poseStack, SaberRenderTypes.bloom(false),
						(pose, buffer) -> disc(pose, buffer, style.coreColor(), burst, 0.6F * burst + 0.2F));
			}

			poseStack.popPose();
		}
	}

	/** A soft disc facing the camera: bright in the middle, clear at the rim. */
	private static void disc(PoseStack.Pose pose, VertexConsumer buffer, int rgb, float alpha, float radius) {
		int bright = argb(alpha, rgb);
		int clear = argb(0.0F, rgb);
		int segments = 12;

		for (int i = 0; i < segments; i += 2) {
			float a0 = i * Mth.TWO_PI / segments;
			float a1 = (i + 1) * Mth.TWO_PI / segments;
			float a2 = (i + 2) * Mth.TWO_PI / segments;
			BladeMesh.vertex(pose, buffer, 0.0F, 0.0F, 0.0F, bright, 0.0F, 0.0F, 1.0F);
			BladeMesh.vertex(pose, buffer, Mth.cos(a0) * radius, Mth.sin(a0) * radius, 0.0F, clear, 0.0F, 0.0F, 1.0F);
			BladeMesh.vertex(pose, buffer, Mth.cos(a1) * radius, Mth.sin(a1) * radius, 0.0F, clear, 0.0F, 0.0F, 1.0F);
			BladeMesh.vertex(pose, buffer, Mth.cos(a2) * radius, Mth.sin(a2) * radius, 0.0F, clear, 0.0F, 0.0F, 1.0F);
		}
	}

	/** A ring facing the camera, brightest on its line and clear to both sides. */
	private static void ring(PoseStack.Pose pose, VertexConsumer buffer, int rgb, float alpha, float radius, float width) {
		int bright = argb(alpha, rgb);
		int clear = argb(0.0F, rgb);
		int segments = 32;

		for (int i = 0; i < segments; i++) {
			float a0 = i * Mth.TWO_PI / segments;
			float a1 = (i + 1) * Mth.TWO_PI / segments;
			float c0 = Mth.cos(a0);
			float s0 = Mth.sin(a0);
			float c1 = Mth.cos(a1);
			float s1 = Mth.sin(a1);
			float inner = Math.max(0.0F, radius - width);
			float outer = radius + width * 0.5F;
			// Inside half: clear at the inner edge, bright on the line.
			BladeMesh.vertex(pose, buffer, c0 * inner, s0 * inner, 0.0F, clear, 0.0F, 0.0F, 1.0F);
			BladeMesh.vertex(pose, buffer, c0 * radius, s0 * radius, 0.0F, bright, 0.0F, 0.0F, 1.0F);
			BladeMesh.vertex(pose, buffer, c1 * radius, s1 * radius, 0.0F, bright, 0.0F, 0.0F, 1.0F);
			BladeMesh.vertex(pose, buffer, c1 * inner, s1 * inner, 0.0F, clear, 0.0F, 0.0F, 1.0F);
			// Outside half.
			BladeMesh.vertex(pose, buffer, c0 * radius, s0 * radius, 0.0F, bright, 0.0F, 0.0F, 1.0F);
			BladeMesh.vertex(pose, buffer, c0 * outer, s0 * outer, 0.0F, clear, 0.0F, 0.0F, 1.0F);
			BladeMesh.vertex(pose, buffer, c1 * outer, s1 * outer, 0.0F, clear, 0.0F, 0.0F, 1.0F);
			BladeMesh.vertex(pose, buffer, c1 * radius, s1 * radius, 0.0F, bright, 0.0F, 0.0F, 1.0F);
		}
	}

	private static int argb(float alpha, int rgb) {
		return Mth.clamp(Math.round(alpha * 255.0F), 0, 255) << 24 | rgb & 0xFFFFFF;
	}
}
