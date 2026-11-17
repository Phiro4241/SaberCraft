package com.phiro.sabercraft.client.story;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import com.phiro.sabercraft.interior.Crew;
import com.phiro.sabercraft.network.StoryActionPayload;
import com.phiro.sabercraft.network.StoryFiguresPayload;
import com.phiro.sabercraft.network.StoryStatePayload;
import com.phiro.sabercraft.saber.SaberVariant;

/**
 * Phase 19: what the player's keys do in the story. The use key on a thing of the story (a panel,
 * a locker, a valve) uses it, on someone who speaks it speaks to them; the attack key with a saber
 * in the finale is a stroke the server weighs. What the crosshair is on is found here (for the
 * prompt under it and for the key) the same way, so what the prompt offers is what the key does.
 */
public final class StoryInput {
	/** As far as the server lets one reach (Story.REACH). */
	static final double REACH = 4.5;
	/** How much nearer a block in the way must be to hide what lies behind it. */
	static final double SLACK = 0.35;
	/** People: a desk, a console or crates up to a block in front of them do not hide them. */
	static final double PERSON_SLACK = 1.1;

	private static int used;
	private static int struck;

	private StoryInput() {
	}

	/**
	 * What the crosshair is on: someone ({@code figure} > 0, their {@code speaker}) or a thing
	 * ({@code id}, its words {@code label}; {@code hack} when only a droid opens it), and how far.
	 */
	public record Target(int figure, String speaker, String id, String label, boolean hack, double distance) {
	}

	static void tick(Minecraft client) {
		// Nothing to keep from tick to tick yet: the prompt and the keys ask target() themselves.
	}

	/** What the crosshair is on now, or {@code null}. */
	public static @Nullable Target target(Minecraft client, float partial) {
		StoryStatePayload state = StoryClient.state();

		if (state == null || !state.active() || client.player == null || client.level == null || client.player.isSpectator()) {
			return null;
		}

		Vec3 eye = client.player.getEyePosition(partial);
		Vec3 to = eye.add(client.player.getViewVector(partial).scale(REACH));
		Target best = null;
		AABB bestBox = null;
		double bestDistance = REACH;

		for (StoryStatePayload.Point point : state.points()) {
			double distance = hit(point.box(), eye, to);

			if (distance < bestDistance) {
				bestDistance = distance;
				bestBox = point.box();
				best = new Target(0, "", point.id(), point.label(), point.hack(), distance);
			}
		}

		BlockPos origin = StoryClient.figuresOrigin();

		if (origin != null) {
			for (StoryClient.Pose pose : StoryClient.figures(origin, partial)) {
				StoryFiguresPayload.Fig fig = pose.fig();

				if ((fig.flags() & StoryFiguresPayload.TALKS) == 0 || (fig.flags() & StoryFiguresPayload.HOSTILE) != 0 || "down".equals(fig.pose())) {
					continue;
				}

				AABB box = box(fig.kind(), fig.pose(), pose.x() + origin.getX(), pose.y() + origin.getY(), pose.z() + origin.getZ());
				double distance = hit(box, eye, to);

				if (distance < bestDistance) {
					bestDistance = distance;
					bestBox = box;
					best = new Target(fig.id(), fig.speaker(), "", "", false, distance);
				}
			}
		}

		if (best == null) {
			return null;
		}

		// A wall (or anything else solid) nearer than it hides it, but not the block it is built into,
		// nor (for people) a desk or a crate right in front of them.
		HitResult aimed = client.hitResult;
		double slack = best.figure() > 0 ? PERSON_SLACK : SLACK;

		if (aimed != null && aimed.getType() != HitResult.Type.MISS && aimed.getLocation().distanceTo(eye) < bestDistance - slack
				&& !bestBox.inflate(0.6).contains(aimed.getLocation())) {
			return null;
		}

		return best;
	}

	/** Why the crosshair finds what it finds (for tests): the nearest box along the view, and what the view hits first. */
	public static String explainForTest(Minecraft client) {
		StoryStatePayload state = StoryClient.state();

		if (state == null || client.player == null) {
			return "no state";
		}

		Vec3 eye = client.player.getEyePosition();
		Vec3 to = eye.add(client.player.getViewVector(1.0F).scale(REACH));
		StringBuilder out = new StringBuilder("eye " + eye + " view " + client.player.getViewVector(1.0F));

		for (StoryStatePayload.Point point : state.points()) {
			out.append("; point ").append(point.id()).append(" ").append(point.box()).append(" at ").append(hit(point.box(), eye, to));
		}

		BlockPos origin = StoryClient.figuresOrigin();

		if (origin != null) {
			for (StoryClient.Pose pose : StoryClient.figures(origin, 1.0F)) {
				StoryFiguresPayload.Fig fig = pose.fig();
				AABB box = box(fig.kind(), fig.pose(), pose.x() + origin.getX(), pose.y() + origin.getY(), pose.z() + origin.getZ());
				double distance = hit(box, eye, to);

				if (distance < REACH + 2.0 || box.getCenter().distanceTo(eye) < 5.0) {
					out.append("; ").append(fig.speaker().isEmpty() ? fig.kind() : fig.speaker()).append(" flags ").append(fig.flags()).append(" ").append(box)
							.append(" at ").append(distance);
				}
			}
		}

		HitResult aimed = client.hitResult;

		if (aimed instanceof net.minecraft.world.phys.BlockHitResult block && aimed.getType() != HitResult.Type.MISS) {
			out.append("; aimed block ").append(block.getBlockPos()).append(" ").append(client.level.getBlockState(block.getBlockPos())).append(" at ")
					.append(aimed.getLocation().distanceTo(eye));
		} else if (aimed != null) {
			out.append("; aimed ").append(aimed.getType());
		}

		return out.toString();
	}

	/** Someone's box to aim at (world space): lower when sitting, small for droids. */
	static AABB box(String kind, String pose, double x, double y, double z) {
		boolean sit = Crew.SIT.equals(pose);
		double half = sit ? 0.4 : 0.32;
		double top = switch (kind) {
			case "droid_mouse" -> 0.45;
			case "droid_r2" -> 1.15;
			case Crew.WOOKIEE -> sit ? 1.6 : 2.25;
			default -> sit ? 1.45 : 1.8;
		};

		if (kind.startsWith("droid")) {
			half = 0.36;
		}

		return new AABB(x - half, y, z - half, x + half, y + top, z + half);
	}

	/** How far along the ray it is struck (0 if the eye is inside), or more than the reach. */
	private static double hit(AABB box, Vec3 eye, Vec3 to) {
		if (box.contains(eye)) {
			return 0.0;
		}

		Optional<Vec3> at = box.clip(eye, to);
		return at.isPresent() ? at.get().distanceTo(eye) : Double.MAX_VALUE;
	}

	/** The use key: uses or speaks to what the crosshair is on. Returns whether it did (the key does nothing else then). */
	public static boolean use(Minecraft client) {
		if (client.player == null || client.gui.screen() != null || !StoryClient.active()) {
			return false;
		}

		Target target = target(client, client.getDeltaTracker().getGameTimeDeltaPartialTick(false));

		if (target == null) {
			return false;
		}

		boolean sent = target.figure() > 0 ? StoryClient.send(StoryActionPayload.TALK, "", target.figure())
				: StoryClient.send(StoryActionPayload.USE, target.id(), 0);

		if (sent) {
			client.player.swing(InteractionHand.MAIN_HAND);
			used++;
		}

		return sent;
	}

	/**
	 * The attack key: with a saber in hand while someone hostile is near, the server is told of the
	 * stroke (it finds whom it reaches). The swing itself goes on as always.
	 */
	public static void attack(Minecraft client) {
		if (client.player == null || client.gui.screen() != null || !StoryClient.active() || !SaberVariant.canHold(client.player.getMainHandItem())) {
			return;
		}

		BlockPos origin = StoryClient.figuresOrigin();

		if (origin == null) {
			return;
		}

		for (StoryClient.Pose pose : StoryClient.figures(origin, 1.0F)) {
			StoryFiguresPayload.Fig fig = pose.fig();

			if ((fig.flags() & StoryFiguresPayload.HOSTILE) != 0 && !"down".equals(fig.pose())) {
				Vec3 at = new Vec3(pose.x() + origin.getX(), pose.y() + origin.getY() + 1.0, pose.z() + origin.getZ());

				if (at.distanceTo(client.player.getEyePosition()) < 6.0) {
					if (StoryClient.send(StoryActionPayload.STRIKE, "", 0)) {
						struck++;
					}

					return;
				}
			}
		}
	}

	// --- For tests ---------------------------------------------------------------------------------

	public static int usedForTest() {
		return used;
	}

	public static int struckForTest() {
		return struck;
	}
}
