package com.phiro.sabercraft.story;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import com.phiro.sabercraft.droid.DroidEntity;
import com.phiro.sabercraft.droid.DroidType;
import com.phiro.sabercraft.droid.Droids;
import com.phiro.sabercraft.interior.ShipInteriors;

/**
 * Phase 21: the two droids of the story as companions, not shadows.
 *
 * <p><b>Where they keep.</b> Outside the story a droid follows at its owner's heels. In the story
 * each keeps its own place behind the player, a little to one side (M-31 close and low on the
 * left, quick to catch up; R2-V7 further back on the right, steady), measured from the way the
 * player is going, so they do not stand in front of the player or in a doorway the player turns
 * back to. They set off only when that place is a few steps away (not at every step), keep out of
 * walls and away from the people of the ship, follow in a line in a crawl way, and keep well back
 * while the saber fights. Told to wait (the order key), they wait.
 *
 * <p><b>M-31's lure.</b> The ability key sends M-31 to the place the player looks at (on the
 * floor, within reach, somewhere it can drive to: round walls, never through them). There it
 * rattles and squeaks for a few seconds. Only those who hear it react, each as {@link
 * Stealth#noise} says: the nearest goes to see (after listening a moment), the next only looks
 * over, and nobody falls for it a third time. Then M-31 comes back, and it needs a while before it
 * can do it again. It is the scout's tool for one guard in the way, not a key to every room;
 * R2-V7's scan shows who is about first.
 */
public final class Companions {
	/** Ticks M-31 may take to get there, how long it rattles there, and how long before it can go again. */
	static final int LURE_RUN = 160;
	static final int LURE_RATTLE = 70;
	static final int LURE_RECHARGE = 400;
	/** How far apart the two droids keep (blocks). */
	static final double APART = 1.2;
	/** How far M-31 is sent at most (blocks from the player). */
	static final double LURE_REACH = 12.0;

	/** Per droid: the spot it keeps to, and where its owner stood when it was chosen. */
	private static final Map<Integer, Vec3[]> SPOTS = new HashMap<>();
	/** Phase 22: when each kept place was last asked for (game time): one not asked for a while is chosen anew. */
	private static final Map<Integer, Long> CHOSEN = new HashMap<>();
	/** Phase 22: how long a place not asked for (the droid waited, or was away) stays kept (ticks). */
	static final int KEEP_TICKS = 100;

	private Companions() {
	}

	static void init() {
		DroidEntity.advisor = Companions::spot;
	}

	// --- Where they keep ---------------------------------------------------------------------------

	/** The place a story droid keeps to near its player (world space), or null outside the story. */
	static DroidEntity.@Nullable Spot spot(DroidEntity droid, ServerPlayer owner) {
		StorySession s = Story.session(owner.getUUID());

		if (s == null || s.here == null || !(droid.level() instanceof ServerLevel level)) {
			return null;
		}

		boolean mouse = droid.droid() == DroidType.MOUSE;
		boolean crawl = owner.getPose() == Pose.SWIMMING;
		double back = crawl ? 1.6 : mouse ? 2.2 : 3.2;
		double side = crawl ? 0.0 : mouse ? -1.1 : 1.4;

		if (s.fighting) {
			// Out of the way of blades and bolts.
			back = mouse ? 5.0 : 6.5;
			side *= 2.0;
		}

		Vec3 here = owner.position();
		Vec3[] kept = SPOTS.get(droid.getId());
		// The other droid's place: they keep apart.
		DroidEntity other = Droids.droid(owner, mouse ? DroidType.R2D2 : DroidType.MOUSE);
		Vec3[] otherKept = other != null ? SPOTS.get(other.getId()) : null;
		Vec3 taken = otherKept != null ? otherKept[0] : other != null ? other.position() : null;

		// The same spot while the player stands about (looking round does not send them running).
		// Phase 22: only a place asked for a moment ago (one kept from long before, while the
		// droid waited or was elsewhere, could lie the other way, in front of the player).
		long now = droid.level().getGameTime();
		boolean fresh = now - CHOSEN.getOrDefault(droid.getId(), Long.MIN_VALUE / 2) < KEEP_TICKS;

		if (kept != null && fresh && kept[1].distanceTo(here) < 1.2 && (taken == null || kept[0].distanceTo(taken) > APART)
				&& droid.level().noCollision(droid, droid.getDimensions(droid.getPose()).makeBoundingBox(kept[0]))
				&& !DroidEntity.keptClear(level, kept[0].x, kept[0].y + 0.05, kept[0].z, droid.getBbWidth() / 2.0)) {
			CHOSEN.put(droid.getId(), now);
			return new DroidEntity.Spot(kept[0], crawl ? 2.2 : mouse ? 3.2 : 4.2, crawl ? 0.8 : 1.0);
		}

		// The way the player goes (or looks, standing).
		Vec3 moving = owner.getDeltaMovement().multiply(1.0, 0.0, 1.0);
		Vec3 forward = moving.lengthSqr() > 0.002 ? moving.normalize() : Vec3.directionFromRotation(0.0F, owner.getYRot()).multiply(1.0, 0.0, 1.0).normalize();
		Vec3 right = new Vec3(-forward.z, 0.0, forward.x);
		Vec3 corner = Vec3.atLowerCornerOf(s.origin(s.here));
		Vec3 best = null;

		for (double[] offset : new double[][] {{back, side}, {back, -side}, {back * 0.7, side * 0.6}, {back * 0.7, -side * 0.6}, {back * 0.7, 0.0},
				{1.4, side * 0.8}, {1.4, -side * 0.8}, {1.4, 0.0}}) {
			Vec3 at = here.subtract(forward.scale(offset[0])).add(right.scale(offset[1]));
			Vec3 floor = floor(level, at, here.y);

			// Phase 24: never on a turbolift's pad; the bigger R2-V7 also never in a narrow passage or
			// by a crawl way's mouth (it stood in the way of one coming out).
			net.minecraft.core.BlockPos cell = floor != null ? net.minecraft.core.BlockPos.containing(floor.add(0.0, 0.05, 0.0)) : null;

			// Phase 26: and never on a prop of the story (the floor found under a spot can be the top
			// of the saber's pedestal), nor right at it.
			if (floor != null && (taken == null || floor.distanceTo(taken) > APART) && fits(droid, floor) && clearTo(level, here, floor)
					&& !DroidEntity.keptClear(level, floor.x, floor.y + 0.05, floor.z, droid.getBbWidth() / 2.0)
					&& !nearSomeone(s, floor.subtract(corner)) && !DroidEntity.onLift(level, cell) && (mouse || crawl || !droid.inTheWay(cell))) {
				best = floor;
				break;
			}
		}

		if (best == null) {
			SPOTS.remove(droid.getId());
			CHOSEN.remove(droid.getId());
			return null;
		}

		SPOTS.put(droid.getId(), new Vec3[] {best, here});
		CHOSEN.put(droid.getId(), now);
		return new DroidEntity.Spot(best, crawl ? 2.2 : mouse ? 3.2 : 4.2, crawl ? 0.8 : 1.0);
	}

	/** The floor under a point (at most a block up or two down), or null. */
	private static @Nullable Vec3 floor(ServerLevel level, Vec3 at, double y) {
		Vec3 top = new Vec3(at.x, y + 1.0, at.z);
		Vec3 bottom = new Vec3(at.x, y - 2.0, at.z);
		HitResult hit = level.clip(new ClipContext(top, bottom, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
		return hit.getType() == HitResult.Type.MISS ? null : hit.getLocation();
	}

	private static boolean fits(DroidEntity droid, Vec3 at) {
		AABB box = droid.getDimensions(droid.getPose()).makeBoundingBox(at).move(0.0, 0.02, 0.0);
		return droid.level().noCollision(droid, box);
	}

	/** Whether nothing is between the player and the spot (so it is not behind a wall). */
	private static boolean clearTo(ServerLevel level, Vec3 player, Vec3 at) {
		Vec3 from = player.add(0.0, 0.4, 0.0);
		Vec3 to = at.add(0.0, 0.3, 0.0);
		HitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
		return hit.getType() == HitResult.Type.MISS;
	}

	/** Whether someone of the ship stands there (plot space): the droids keep a step away from people. */
	private static boolean nearSomeone(StorySession s, Vec3 local) {
		for (Figure figure : s.figures) {
			if (figure.mode != Figure.DOWN && Math.hypot(figure.x - local.x, figure.z - local.z) < 1.0 && Math.abs(figure.y - local.y) < 1.5) {
				return true;
			}
		}

		return false;
	}

	// --- M-31's lure -------------------------------------------------------------------------------

	/** The ability key with M-31 (no special job here): sends it off. Returns the message's key. */
	static String lure(StorySession s, ServerPlayer player, ServerLevel level) {
		DroidEntity mouse = Droids.droid(player, DroidType.MOUSE);

		if (mouse == null || s.here == null) {
			return "sabercraft.story.droid.nothing";
		}

		if (s.has("tmp.lure")) {
			return "sabercraft.story.droid.m31_busy";
		}

		if (s.timer("lure")) {
			int left = s.timers.getOrDefault("lure", 0);
			s.droidArgs = new Object[] {Mth.ceil(left / 20.0F)};
			return "sabercraft.story.droid.m31_recharge";
		}

		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getLookAngle().scale(LURE_REACH));
		HitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
		Vec3 look = hit.getType() == HitResult.Type.MISS ? end : hit.getLocation().subtract(player.getLookAngle().scale(0.4));
		Vec3 at = floor(level, look, look.y);

		if (at == null || at.distanceTo(player.position()) < 1.5 || DroidEntity.keptClear(level, at.x, at.y + 0.05, at.z, 0.25)) {
			return "sabercraft.story.droid.m31_where";
		}

		if (!mouse.startErrand(at, LURE_RUN)) {
			return "sabercraft.story.droid.m31_noway";
		}

		s.set("tmp.lure");
		s.unset("tmp.lure_there");
		s.timer("lure_run", LURE_RUN + 20);
		mouse.playVoice("ability", 0.6F);
		lures++;
		return "sabercraft.story.droid.m31_go";
	}

	/** Every tick: M-31 on its way, rattling there, coming back. */
	static void tick(StorySession s) {
		if (!s.has("tmp.lure")) {
			return;
		}

		ServerPlayer player = s.player();
		DroidEntity mouse = player != null ? Droids.droid(player, DroidType.MOUSE) : null;
		ServerLevel level = Chapter1.interiors(s);

		if (mouse == null || level == null || s.here == null) {
			s.unset("tmp.lure");
			s.unset("tmp.lure_there");
			return;
		}

		int errand = mouse.errand();

		if (!s.has("tmp.lure_there")) {
			if (errand == DroidEntity.ERRAND_THERE) {
				s.set("tmp.lure_there");
				s.timer("lure_rattle", LURE_RATTLE);
				s.timer("lure", LURE_RECHARGE);
				Vec3 corner = Vec3.atLowerCornerOf(s.origin(s.here));
				Vec3 at = mouse.position().subtract(corner);
				rattle(level, mouse);
				Story.cue(s, Story.CUE_NOISE, "", "", LURE_RATTLE, List.of(mouse.position()));
				Stealth.Noise noise = Stealth.noise(s, s.here, at, level, corner);
				heard(s, noise);
			} else if (errand == DroidEntity.ERRAND_FAILED || errand == DroidEntity.ERRAND_NONE || !s.timer("lure_run")) {
				com.phiro.sabercraft.SaberCraft.LOGGER.debug("Story: M-31 did not get there (errand {}, run left {}, at {})", errand, s.timers.getOrDefault("lure_run", 0),
						mouse.position());
				mouse.endErrand();
				s.unset("tmp.lure");
				s.hint("sabercraft.story.hint.m31_noway");
			}

			return;
		}

		if (s.timer("lure_rattle")) {
			if (s.timers.getOrDefault("lure_rattle", 0) % 18 == 0) {
				rattle(level, mouse);
			}

			return;
		}

		mouse.endErrand();
		s.unset("tmp.lure");
		s.unset("tmp.lure_there");
	}

	/** The rattle and squeak of a mouse droid bumping about. */
	private static void rattle(ServerLevel level, DroidEntity mouse) {
		level.playSound(null, mouse.getX(), mouse.getY() + 0.2, mouse.getZ(), ShipInteriors.sound("story.m31_rattle"), SoundSource.NEUTRAL, 0.9F,
				0.95F + level.getRandom().nextFloat() * 0.15F);
	}

	/** What the player is told of who noticed M-31 (and the first mission's step done when the guard goes). */
	private static void heard(StorySession s, Stealth.Noise noise) {
		if (!noise.went().isEmpty()) {
			s.hint(noise.went().get(0).distracted > 1 ? "sabercraft.story.hint.m31_lure_look" : "sabercraft.story.hint.m31_lure_one");
		} else if (!noise.looked().isEmpty()) {
			s.hint("sabercraft.story.hint.m31_lure_look");
		} else if (!noise.ignored().isEmpty()) {
			s.hint("sabercraft.story.hint.m31_lure_ignored");
		} else {
			s.hint("sabercraft.story.hint.m31_lure_none");
		}

		for (Figure figure : noise.looked()) {
			if (figure.distracted >= 2) {
				// The second time: a grumble, they do not go.
				s.hear(Story.voice(figure), "sabercraft.story.shout.mouse_again");
				break;
			}
		}

		if (s.at("m1.distract") && noise.went().stream().anyMatch(figure -> figure.key.equals("maint"))) {
			// The exercise: he goes, the hatch is free.
			s.radio("mira", "sabercraft.story.radio.m1_go");
			s.complete();
		}
	}

	private static int lures;

	/** For the tests: how often M-31 was sent off since the game started. */
	public static int luresForTest() {
		return lures;
	}
}
