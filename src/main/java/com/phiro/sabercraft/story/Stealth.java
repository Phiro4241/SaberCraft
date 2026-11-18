package com.phiro.sabercraft.story;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.interior.Crew;

/**
 * Phase 19: how the people in a story's ship see the player, and what they do about it.
 *
 * <p>Each one fills their own suspicion (0 to 1) while they see the player: in front of them
 * (about 110°, a little more close by), within their sight (troopers far, technicians less), with
 * nothing in between (walls, consoles, crates and closed doors are cover). Close by it fills fast,
 * far off slowly; sneaking halves how far one is seen, sprinting is noticed. In dark areas only a
 * lamp's cone shows the player from afar. Working, sitting, talking people pay less attention;
 * after an alarm everyone looks harder. The armour of a trooper hides the player from the troops
 * as long as they move like one, but not from officers close by (the security bureau looks
 * closest), not where nobody may go.
 *
 * <p>Four stages: calm; suspicious (stops, turns to look); alerted (goes to look where they saw
 * something); detected (raises the alarm and gives chase). Chasers run straight at the player and
 * stop at walls; one who gets within reach catches the player (back to the checkpoint). Out of
 * everyone's sight for ten seconds, the chase becomes a search, and after half a minute it is
 * calm again.
 *
 * <p>Phase 20: everyone finds their way round walls ({@link Paths}): chasers follow the player
 * round corners, searchers go where they last saw them and then look at two or three places
 * nearby, and all walk back to their posts (only one who finds no way at all slips back into
 * place, and only out of the player's sight). Soldiers shout and give chase; anyone else runs to
 * the nearest wall comm and raises the alarm there, which brings two troopers from the nearest
 * door. A chaser who reaches the player calls out ("Hands up!") a moment before the capture.
 * After an alarm the mission's crew stays wary (they see a little further) until it ends.
 */
public final class Stealth {
	/** How often each figure looks (ticks); figures take turns. */
	static final int LOOK_EVERY = 4;
	/** Field of view: full within this angle either side, a little to the side close by. */
	static final float FOV = 55.0F;
	static final float SIDE = 85.0F;
	static final double SIDE_RANGE = 5.0;
	/** Seen at once this close in front of someone (not in disguise). */
	static final double AT_ONCE = 2.0;
	/** Seen far in the dark only within a lamp's cone. */
	static final float LAMP_CONE = 24.0F;
	static final double LAMP_RANGE = 13.0;
	static final double DARK_RANGE = 3.5;
	/** Suspicion stages. */
	static final float SUSPICIOUS = 0.25F;
	static final float ALERTED = 0.6F;
	/** Walking and running speeds (blocks a second). */
	static final double WALK = 1.7;
	static final double RUN = 4.6;
	/** How near a chaser catches. */
	static final double CATCH = 1.3;
	/** Out of sight this long, a chase becomes a search; the search lasts this long. */
	static final int LOSE = 200;
	static final int SEARCH = 600;
	/** Phase 20: how often a way is found anew (ticks), how long the capture's call lasts, the siren's beat. */
	static final int REPATH = 10;
	static final int CORNERED = 16;
	static final int SIREN = 80;
	/** Phase 20: how much further the wary see, and a wall comm's reach (blocks). */
	static final double WARY = 1.15;
	static final double COMM_REACH = 26.0;
	/**
	 * Phase 21: the search after a chase is longer (the whole deck is searched); a soldier's call
	 * brings help after this long; of those who came, this many stay as a search patrol.
	 */
	static final int SEARCH_LONG = 900;
	static final int REINFORCE_DELAY = 60;
	static final int PATROLS = 2;
	/** Phase 21: a droid's noise is heard this far with nothing in between, this far through walls. */
	static final double HEAR = 7.5;
	static final double HEAR_WALL = 3.5;
	/** Phase 21: how long one stops and listens before going to see (ticks). */
	static final int LISTEN = 16;
	/** Phase 21: figures keep this far apart (blocks). */
	static final double APART = 0.7;

	/**
	 * Phase 21: everyone of the ship being moved this tick (so that a step can keep them apart).
	 * The server runs one story tick at a time.
	 */
	private static List<Figure> crowd = List.of();

	private Stealth() {
	}

	/** How far that kind of person sees (blocks). */
	public static double range(Figure figure) {
		if (figure.sight > 0.0) {
			return figure.sight;
		}

		return switch (figure.kind()) {
			case Crew.TROOPER -> 14.0;
			case Crew.GUARD -> 12.0;
			case Crew.OFFICER, Crew.OFFICER_BLACK, Crew.OFFICER_OLIVE -> 11.0;
			case Crew.ADMIRAL -> 10.0;
			default -> 9.0;
		};
	}

	/** Runs the people of the ship the player is in, for one tick. */
	static void tick(StorySession s, ServerPlayer player, ServerLevel level) {
		String ship = s.here;
		long time = s.time();
		s.suspicion = 0.0F;
		s.watcher = null;

		if (ship == null) {
			return;
		}

		BlockPos origin = s.origin(ship);
		Vec3 corner = Vec3.atLowerCornerOf(origin);
		Vec3 me = player.position().subtract(corner);
		// (Phase 24: nor during a scene of the story shot where it is, the guard's takedown.)
		boolean watched = !s.fighting && s.grace <= 0 && !player.isSpectator() && !player.isCreative() && s.dialog == null && !s.has("tmp.takedown");
		crowd = s.figures;

		if (s.grace > 0) {
			s.grace--;
		}

		// Phase 20: reached by a chaser: "Hands up!", a moment, then caught.
		if (s.cornered > 0) {
			if (s.cornerer != null) {
				s.cornerer.speed = 0.0F;
				s.cornerer.turnTowards(me, 30.0F, true);
			}

			if (--s.cornered == 0) {
				Figure by = s.cornerer;
				s.cornerer = null;
				Story.capture(s, by);
				return;
			}
		}

		// A copy: a call for help brings new people in while they are looked at (they join next tick).
		for (Figure figure : new ArrayList<>(s.figures)) {
			if (!ship.equals(figure.ship) || !s.figures.contains(figure)) {
				continue;
			}

			// Phase 23: those in the fight are moved by it (Finale), not by what they see; the
			// beaten finish falling.
			if (figure.hostile && (figure.mode == Figure.FIGHT || figure.mode == Figure.DOWN)) {
				continue;
			}

			// What they see.
			// Phase 23: also on a scripted way (sent somewhere): nobody of the Empire walks past an
			// intruder as if he were not there.
			if (figure.observer && figure.mode != Figure.DOWN && figure.mode != Figure.HELD && (figure.mode != Figure.SCRIPTED || !figure.blind)
					&& (time + figure.id) % LOOK_EVERY == 0) {
				float rate = watched ? sees(s, figure, player, me, level, corner, ship) : 0.0F;

				if (rate > 0.0F) {
					figure.lastSeen = time;
					figure.seenAt = me;
					figure.suspicion = Math.min(1.0F, figure.suspicion + rate * LOOK_EVERY / 20.0F);
				} else if (time - figure.lastSeen > 40) {
					// Slowly forgetting (not while chasing: that ends by losing the player). Phase
					// 21: after an alarm in this mission more slowly.
					if (figure.mode != Figure.CHASE) {
						float forget = s.alarmed() || level(s) > 0 ? 0.06F : 0.1F;
						figure.suspicion = Math.max(0.0F, figure.suspicion - forget * LOOK_EVERY / 20.0F);
					}
				}

				react(s, figure, time);
			}

			move(s, figure, me, level, corner, time);

			if (figure.observer && figure.mode != Figure.DOWN && figure.suspicion > s.suspicion) {
				s.suspicion = figure.suspicion;
				s.watcher = figure;
			}

			if (figure.mode == Figure.CHASE && watched && s.cornered <= 0 && caught(figure, me)) {
				Story.cornered(s, figure);
			}
		}

		// Phase 20: the troopers who came for an alarm go back out of their door once it is calm
		// (Phase 21: those who stay as a search patrol are figures of their own).
		if (s.alarm <= 0 && s.figures.removeIf(figure -> figure.key.startsWith(REINFORCEMENT) && figure.mode == Figure.ROUTINE)) {
			s.dirty = true;
		}

		if (s.alarm > 0) {
			alarm(s, level, corner, ship, time);
		}

		crowd = List.of();
	}

	/**
	 * Phase 21: the alarm, step by step. While anyone hunting sees the player: the chase (sirens,
	 * troopers come). Out of everyone's sight for ten seconds: the search ("search the deck", no
	 * sirens): the chasers go to where the player was last seen and search the places round it,
	 * the troopers who came search too. After the search, nobody found: an announcement that the
	 * intruder is still aboard and the ship stays on alert. Then they go back to their posts, but
	 * not as before: the crew of the mission stays wary (each further alarm in the mission more
	 * so: they see further, forget more slowly, go to look sooner, aim faster), and two of the
	 * troopers who came stay as a search patrol where the player was last seen. Being seen again
	 * during the search starts the chase again.
	 */
	private static void alarm(StorySession s, ServerLevel level, Vec3 corner, String ship, long time) {
		boolean seen = s.huntSeen >= time - 1;

		// The siren, while they are after the player.
		if (!s.searching && time % SIREN == 0 && Story.DESTROYER.equals(ship)) {
			Story.cue(s, Story.CUE_SOUND, "", "story.siren", 0);
		}

		// With the saber lit it is a fight, not a search; and nobody searches the Falcon.
		if (s.fighting || !Story.DESTROYER.equals(ship)) {
			s.unseen = 0;
			s.searching = false;
			return;
		}

		// A soldier's call over the comlink: help comes from the nearest door.
		if (s.has("tmp.reinforce") && !s.timer("reinforce")) {
			s.unset("tmp.reinforce");
			Vec3 at = s.huntAt != null ? s.huntAt : s.local(ship);
			Story.reinforce(s, ship, at, at);
		}

		if (seen) {
			s.unseen = 0;
			s.alarm = Math.max(s.alarm, SEARCH_LONG + LOSE);

			if (s.searching) {
				s.searching = false;
				s.hint("sabercraft.story.hint.detected");
			}
		} else if (++s.unseen == LOSE) {
			// Lost: the search.
			s.searching = true;
			Vec3 last = s.huntAt;

			for (Figure figure : s.figures) {
				if (figure.mode == Figure.CHASE) {
					// Where the player was last seen, then three or four places round it.
					figure.mode = Figure.INVESTIGATE;
					figure.target = last != null ? last : figure.seenAt;
					figure.timer = 80;
					figure.searches = 3 + figure.id % 2;
					figure.windup = 0;
					figure.burst = 0;
				}
			}

			s.alarm = Math.min(s.alarm, SEARCH_LONG);
			s.hear("intercom", "sabercraft.story.shout.search");
			s.hint("sabercraft.story.hint.searching");
		} else if (s.searching && s.unseen % 240 == 0 && s.huntAt != null) {
			// Those who came and are done search again while the search lasts.
			for (Figure figure : s.figures) {
				if (figure.mode == Figure.RETURN && figure.key.startsWith(REINFORCEMENT)) {
					figure.mode = Figure.INVESTIGATE;
					figure.target = s.huntAt;
					figure.timer = 40;
					figure.searches = 2;
				}
			}
		}

		if (--s.alarm == 0) {
			s.unseen = 0;
			s.searching = false;
			s.dirty = true;
			int stay = (int) s.flags.stream().filter(flag -> flag.startsWith(patrolPrefix(s))).count();

			for (Figure figure : List.copyOf(s.figures)) {
				if (figure.mode == Figure.CHASE || figure.mode == Figure.INVESTIGATE || figure.mode == Figure.CALL) {
					figure.mode = Figure.RETURN;
					figure.called = false;
					figure.searches = 0;
					figure.windup = 0;
				}

				figure.suspicion = Math.min(figure.suspicion, 0.2F);

				// Two of those who came stay and patrol where the player was last seen.
				if (figure.key.startsWith(REINFORCEMENT) && stay < PATROLS && s.huntAt != null && figure.ship.equals(ship)
						&& stayAsPatrol(s, level, corner, figure, s.huntAt, stay) != null) {
					stay++;
				}
			}

			// Calm again, but the crew stays wary until the mission ends (and more so each time).
			raiseLevel(s);
			s.hear("intercom", "sabercraft.story.shout.search_over");
			s.hint("sabercraft.story.hint.calm");
		}
	}

	/** Phase 21: how wary the crew of this mission is: 0 calm, 1 after one alarm, 2 after two, 3 after more. */
	static int level(StorySession s) {
		String m = s.mission().id();
		return s.has("wary3." + m) ? 3 : s.has("wary2." + m) ? 2 : s.has("wary." + m) ? 1 : 0;
	}

	private static void raiseLevel(StorySession s) {
		String m = s.mission().id();

		if (!s.has("wary." + m)) {
			s.set("wary." + m);
		} else if (!s.has("wary2." + m)) {
			s.set("wary2." + m);
		} else {
			s.set("wary3." + m);
		}
	}

	/** Phase 21: the flags of this mission's search patrols begin so ({@code sp:<mission>:<n>:<door x:y:z>:<place x:y:z>}). */
	static String patrolPrefix(StorySession s) {
		return "sp:" + s.mission().id() + ":";
	}

	/**
	 * Phase 21: one of the troopers who came stays: from now on (in this mission, also after a
	 * checkpoint) a trooper walks between the door they came in by and where the player was last
	 * seen. Returns the new patrol, or null if there is no way.
	 */
	private static @Nullable Figure stayAsPatrol(StorySession s, ServerLevel level, Vec3 corner, Figure came, Vec3 at, int index) {
		Vec3 door = new Vec3(came.crew.x(), came.crew.y(), came.crew.z());
		Figure template = patrol(s, level, corner, came.ship, door, at, index);

		if (template == null) {
			// No way from the door they came in by: between where they stand now and that place.
			door = came.position();
			template = patrol(s, level, corner, came.ship, door, at, index);
		}

		if (template == null) {
			SaberCraft.LOGGER.debug("Story: no way for a search patrol from {} to {}", door, at);
			return null;
		}

		Figure patrol = s.add(template);
		s.set(String.format(java.util.Locale.ROOT, "%s%d:%.1f:%.1f:%.1f:%.1f:%.1f:%.1f", patrolPrefix(s), index, door.x, door.y, door.z, at.x, at.y, at.z));
		patrol.x = came.x;
		patrol.y = came.y;
		patrol.z = came.z;
		patrol.yaw = came.yaw;
		patrol.headYaw = came.headYaw;
		patrol.mode = Figure.RETURN;
		s.figures.remove(came);
		return patrol;
	}

	/**
	 * Phase 21: a search patrol between a door and a place, there and back on a way round walls (a
	 * template: the session numbers it), or null when there is no way.
	 */
	static @Nullable Figure patrol(StorySession s, ServerLevel level, Vec3 corner, String ship, Vec3 door, Vec3 at, int index) {
		List<Vec3> way = Paths.find(level, corner, door, at, 1.8);

		if (way.isEmpty() && door.distanceTo(at) > 0.8 && door.distanceTo(at) < 3.0 && clear(level, corner.add(door).add(0.0, 1.0, 0.0), corner.add(at).add(0.0, 1.0, 0.0))) {
			// Close by already: a short beat between the two.
			way = List.of(at);
		}

		if (way.isEmpty() || way.get(way.size() - 1).distanceTo(at) > 3.0) {
			return null;
		}

		// There and back on the same way (a patrol goes round its path as a loop).
		List<Vec3> loop = new ArrayList<>();
		loop.add(door);
		loop.addAll(way);

		for (int i = way.size() - 2; i >= 0; i--) {
			loop.add(way.get(i));
		}

		if (loop.size() < 2) {
			return null;
		}

		Figure patrol = new Figure(0, "patrol." + s.mission().id() + "." + index,
				Crew.patrol(Crew.TROOPER, 1.3, index * 7.0, loop.toArray(Vec3[]::new)).as("search"));
		patrol.ship = ship;
		patrol.observer = true;
		patrol.talks = false;
		patrol.lamp = s.chapter.dark(s, ship, at.x, at.y, at.z) || s.chapter.dark(s, ship, door.x, door.y, door.z);
		return patrol;
	}

	/** Phase 21: the search patrols this mission left (after a checkpoint: for the figures of the story). */
	static void patrols(StorySession s, List<Figure> list) {
		ServerLevel level = Chapter1.interiors(s);

		if (level == null) {
			return;
		}

		String prefix = patrolPrefix(s);

		for (String flag : List.copyOf(s.flags)) {
			if (!flag.startsWith(prefix)) {
				continue;
			}

			String[] parts = flag.substring(prefix.length()).split(":");

			if (parts.length != 7) {
				continue;
			}

			try {
				int index = Integer.parseInt(parts[0]);
				Vec3 door = new Vec3(Double.parseDouble(parts[1]), Double.parseDouble(parts[2]), Double.parseDouble(parts[3]));
				Vec3 at = new Vec3(Double.parseDouble(parts[4]), Double.parseDouble(parts[5]), Double.parseDouble(parts[6]));
				Figure patrol = patrol(s, level, Vec3.atLowerCornerOf(s.destroyer), Story.DESTROYER, door, at, index);

				if (patrol != null) {
					list.add(patrol);
				}
			} catch (NumberFormatException e) {
				// A broken flag: no patrol.
			}
		}
	}

/** How fast that figure's suspicion fills now (a second), 0 when they do not see the player. */
	static float sees(StorySession s, Figure figure, ServerPlayer player, Vec3 me, ServerLevel level, Vec3 corner, String ship) {
		Vec3 eye = figure.eye();
		Vec3 chest = me.add(0.0, player.getPose() == Pose.SWIMMING ? 0.3 : 0.9, 0.0);
		Vec3 to = chest.subtract(eye);
		double distance = to.length();
		double range = range(figure);
		float factor = 1.0F;

		// Busy people notice less; after an alarm, everyone more.
		String pose = figure.pose();

		if (figure.mode == Figure.ROUTINE) {
			factor *= Crew.WORK.equals(pose) ? 0.7F : Crew.SIT.equals(pose) ? 0.6F : Crew.TALK.equals(pose) ? 0.75F : 1.0F;
		} else {
			factor *= 1.2F;
		}

		int wary = level(s);

		if (s.alarmed()) {
			range *= 1.35;
		} else if (wary > 0) {
			// Phase 21: each alarm of the mission a little more.
			range *= WARY + 0.07 * (wary - 1);
		}

		// Phase 20: by the emergency lights one is seen less far.
		if (s.chapter.emergency(s)) {
			range *= 0.85;
		}

		// How the player moves.
		boolean crawling = player.getPose() == Pose.SWIMMING;
		boolean sneaking = player.isShiftKeyDown() || player.isCrouching();

		if (crawling) {
			range *= 0.4;
		} else if (sneaking) {
			range *= 0.5;
		} else if (player.isSprinting()) {
			range *= 1.25;
		}

		// The dark: far only in a lamp's light.
		boolean dark = s.chapter.dark(s, ship, me.x, me.y, me.z);

		if (dark && !lit(s, ship, me, level, corner)) {
			range = Math.min(range, DARK_RANGE);
		}

		if (distance > range) {
			return 0.0F;
		}

		// In front of them?
		Vec3 flat = new Vec3(to.x, 0.0, to.z);
		float angle = flat.lengthSqr() < 1.0E-4 ? 0.0F : (float) Math.toDegrees(Math.acos(Mth.clamp(flat.normalize().dot(figure.look()), -1.0, 1.0)));

		if (angle > SIDE || angle > FOV && distance > SIDE_RANGE) {
			return 0.0F;
		}

		// Nothing in between (eyes or chest).
		if (!clear(level, corner.add(eye), corner.add(me.add(0.0, player.getEyeHeight(), 0.0))) && !clear(level, corner.add(eye), corner.add(chest))) {
			return 0.0F;
		}

		// The armour: hides from the troops while one moves like them, less from officers.
		boolean conspicuous = sneaking || crawling || player.isSprinting() || !player.onGround();

		if (s.disguised && !s.chapter.restricted(s, ship, me.x, me.y, me.z)) {
			float hidden = disguise(figure, distance, conspicuous);

			if (s.burned) {
				hidden = Math.max(hidden, 0.8F);
			}

			factor *= hidden;

			if (factor <= 0.0F) {
				return 0.0F;
			}
		} else if (distance < AT_ONCE && angle <= FOV) {
			return 20.0F;
		}

		if (angle > FOV) {
			factor *= 0.3F;
		}

		double near = 1.0 - distance / range;
		return (float) ((0.2 + 1.6 * Math.pow(near, 1.5)) * factor);
	}

	/** How much the armour leaves of being seen by that kind of person (0 = not at all). */
	static float disguise(Figure figure, double distance, boolean conspicuous) {
		return switch (figure.kind()) {
			// The troops: not at all, unless one does what troopers do not.
			case Crew.TROOPER, Crew.GUARD, Crew.TECH, Crew.PILOT -> conspicuous ? 0.6F : 0.0F;
			// The security bureau looks closely.
			case Crew.OFFICER_BLACK -> distance < 4.0 ? 1.0F : conspicuous ? 0.7F : 0.2F;
			// Other officers: close by, they look twice.
			default -> distance < 4.0 ? 0.5F : conspicuous ? 0.5F : 0.1F;
		};
	}

	/** Whether a lamp's cone falls on the player (with nothing in between). */
	static boolean lit(StorySession s, String ship, Vec3 me, ServerLevel level, Vec3 corner) {
		for (Figure figure : s.figures) {
			if (!figure.lamp || !ship.equals(figure.ship) || figure.mode == Figure.DOWN) {
				continue;
			}

			Vec3 eye = figure.eye();
			Vec3 to = me.add(0.0, 0.9, 0.0).subtract(eye);

			if (to.length() > LAMP_RANGE) {
				continue;
			}

			Vec3 flat = new Vec3(to.x, 0.0, to.z);

			if (flat.lengthSqr() < 1.0E-4) {
				return true;
			}

			float angle = (float) Math.toDegrees(Math.acos(Mth.clamp(flat.normalize().dot(figure.look()), -1.0, 1.0)));

			if (angle <= LAMP_CONE && clear(level, corner.add(eye), corner.add(me.add(0.0, 0.9, 0.0)))) {
				return true;
			}
		}

		return false;
	}

	/** Whether nothing that collides is between two points (world space). */
	static boolean clear(ServerLevel level, Vec3 from, Vec3 to) {
		HitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
		return hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceToSqr(to) < 0.04;
	}

	/** What a figure does about their suspicion. */
	static void react(StorySession s, Figure figure, long time) {
		float sus = figure.suspicion;
		int before = figure.stage;
		figure.stage = sus >= 1.0F ? Figure.DETECTED : sus >= ALERTED ? Figure.ALERTED : sus >= SUSPICIOUS ? Figure.SUSPICIOUS : Figure.CALM;

		if (figure.stage != before) {
			s.dirty = true;
		}

		if (figure.mode == Figure.SCRIPTED && figure.observer && !figure.blind && figure.stage >= Figure.SUSPICIOUS && figure.seenAt != null) {
			// Phase 23: on their way somewhere, they saw something: they stop and look (the way is
			// given up; where they were sent stays their place, see Chapter1.rehome).
			figure.way = List.of();
			figure.wayIndex = 0;
			figure.mode = Figure.LOOK;
			figure.target = figure.seenAt;
			figure.timer = 60;
			figure.speed = 0.0F;
			Story.cue(s, Story.CUE_SOUND, "", "story.suspicious", figure.id);
			s.dirty = true;
		}

		if (figure.mode == Figure.CHASE || figure.mode == Figure.HELD || figure.mode == Figure.SCRIPTED || figure.mode == Figure.DOWN
				|| figure.mode == Figure.CALL || figure.mode == Figure.FIGHT || figure.hostile) {
			return;
		}

		if (figure.stage == Figure.DETECTED) {
			if (!s.chapter.detected(s, figure)) {
				Story.detected(s, figure);
			}
		} else if (figure.stage == Figure.ALERTED && figure.seenAt != null && time - figure.lastSeen < 10) {
			figure.mode = Figure.INVESTIGATE;
			figure.target = figure.seenAt;
			figure.timer = 100;
		} else if (figure.stage == Figure.SUSPICIOUS && figure.mode == Figure.ROUTINE && figure.seenAt != null) {
			// Phase 21: after two alarms in a mission nobody just looks: they go and see at once.
			if (level(s) >= 2 && time - figure.lastSeen < 10) {
				figure.mode = Figure.INVESTIGATE;
				figure.target = figure.seenAt;
				figure.timer = 80;
			} else {
				figure.mode = Figure.LOOK;
				figure.target = figure.seenAt;
				figure.timer = 60;
			}

			Story.cue(s, Story.CUE_SOUND, "", "story.suspicious", figure.id);
		} else if (figure.stage == Figure.CALM && (figure.mode == Figure.LOOK && figure.lure == null || figure.mode == Figure.INVESTIGATE && figure.timer <= 0)) {
			figure.mode = Figure.RETURN;
		}
	}

	/** Moves a figure a tick along whatever they do. */
	static void move(StorySession s, Figure figure, Vec3 me, ServerLevel level, Vec3 corner, long time) {
		switch (figure.mode) {
			case Figure.ROUTINE -> {
				double[] at = figure.routine(time);
				figure.x = at[0];
				figure.y = at[1];
				figure.z = at[2];
				figure.yaw = (float) at[3];
				figure.headYaw = figure.yaw;
				figure.speed = figure.crew.path().isEmpty() ? 0.0F : (float) figure.crew.speed();
			}
			case Figure.LOOK -> {
				figure.speed = 0.0F;

				if (figure.target != null) {
					figure.turnTowards(figure.target, 9.0F, false);
				}

				// Phase 21: a noise heard: they listen a moment, then go to see (a step short of it).
				if (figure.timer > 0) {
					figure.timer--;
				}

				if (figure.lure != null && figure.timer <= 0) {
					Vec3 lure = figure.lure;
					Vec3 to = lure.subtract(figure.position()).multiply(1.0, 0.0, 1.0);
					figure.lure = null;
					figure.mode = Figure.INVESTIGATE;
					figure.target = to.length() > 1.6 ? lure.subtract(to.normalize().scale(1.2)) : figure.position();
					figure.timer = 60;
					figure.searches = 1;
					s.dirty = true;
				}
			}
			case Figure.INVESTIGATE -> {
				if (figure.target == null || figure.position().distanceTo(figure.target) < 1.2
						|| !go(figure, Combat.apart(s, figure, figure.target, level, corner), s.searching ? WALK * 1.4 : WALK, level, corner, time)) {
					// There (or no way on): look round a while; then the next place to search, or back.
					figure.speed = 0.0F;
					figure.headYaw = figure.yaw + Mth.sin(time * 0.08F + figure.id) * 60.0F;

					if (--figure.timer <= 0 && figure.suspicion < ALERTED) {
						Vec3 next = figure.searches > 0 && figure.target != null ? searchPoint(figure, figure.target, level, corner, time) : null;

						if (next != null) {
							figure.searches--;
							figure.target = next;
							figure.timer = 70;
						} else {
							figure.searches = 0;
							figure.mode = Figure.RETURN;
						}
					}
				}
			}
			case Figure.CHASE -> {
				ServerPlayer player = s.player();

				if (player != null && Combat.armed(figure)) {
					// Phase 21: troopers fight: they keep their distance and shoot (Combat). Phase
					// 23: officers too, with their pistols.
					Combat.chase(s, figure, player, me, level, corner, time);
					break;
				}

				// Others run at the player (Phase 21: out of sight to where the player was last seen).
				boolean sees = s.grace <= 0 && Combat.sees(figure, me, level, corner, horizontal(figure.position(), me));

				if (sees) {
					figure.lastSeen = time;
					figure.seenAt = me;
					s.huntSeen = time;
					s.huntAt = me;
				}

				Vec3 goal = sees || time - s.huntSeen < Combat.TRACK || s.huntAt == null ? me : s.huntAt;
				figure.target = me;

				if (s.cornerer == figure) {
					figure.speed = 0.0F;
				} else if (!go(figure, horizontal(figure.position(), goal) > 3.0 ? Combat.apart(s, figure, goal, level, corner) : goal, RUN, level, corner, time)) {
					figure.speed = 0.0F;
					figure.turnTowards(me, 20.0F, true);
				}
			}
			case Figure.CALL -> {
				// Phase 20: to the wall comm, then the alarm; then they stay by it and watch.
				if (!figure.called && figure.target != null && figure.position().distanceTo(figure.target) > 1.0 && figure.timer-- > 0
						&& go(figure, figure.target, RUN, level, corner, time)) {
					break;
				}

				figure.speed = 0.0F;

				if (!figure.called) {
					figure.called = true;
					Story.alarmCalled(s, figure);
				}

				if (figure.seenAt != null) {
					figure.turnTowards(figure.seenAt, 12.0F, true);
				}
			}
			case Figure.RETURN -> {
				double[] at = figure.routine(time);
				Vec3 home = new Vec3(at[0], at[1], at[2]);

				double distance = figure.position().distanceTo(home);

				if (figure.closestTo == null || figure.closestTo.distanceTo(home) > 0.5) {
					figure.closestTo = home;
					figure.closest = distance;
					figure.stuck = 0;
				}

				// Close by and no nearer (their place is a seat, a bunk, a console in the way): they
				// sit down or step into place.
				// Phase 22: someone whose place is a seat sits down on it from a step away (the
				// chair itself is in the way; they stood in it, upright, while they were watched).
				if (distance < 0.35 || distance < 1.3 && figure.stuck > 8 || figure.seated() && distance < 1.6) {
					settle(figure, at);
				} else {
					go(figure, home, WALK, level, corner, time);

					// Phase 20: getting no nearer for three seconds (no way back at all, or round in
					// circles at a wall): they slip back into place, only where nobody sees it.
					if (distance < figure.closest - 0.3) {
						figure.closest = distance;
						figure.stuck = 0;
					} else if (++figure.stuck > 60 && !s.seenBy(figure)) {
						settle(figure, at);
					}
				}
			}
			case Figure.SCRIPTED -> {
				if (figure.wayIndex >= figure.way.size()) {
					figure.speed = 0.0F;
					figure.mode = Figure.HELD;
				} else {
					Vec3 next = figure.way.get(figure.wayIndex);

					if (figure.position().distanceTo(next) < 0.25) {
						figure.wayIndex++;
					} else if (!step(figure, next, figure.scriptSpeed > 0.0 ? figure.scriptSpeed : WALK, level, corner)) {
						// Scripted ways go where blocks allow; if not, they are put there.
						figure.x = next.x;
						figure.y = next.y;
						figure.z = next.z;
						figure.wayIndex++;
					}
				}
			}
			case Figure.HELD -> {
				figure.speed = 0.0F;

				if (figure.target != null) {
					figure.turnTowards(figure.target, 12.0F, true);
				}
			}
			default -> figure.speed = 0.0F;
		}
	}

	/**
	 * Back in their place: on the spot of their routine at once, in the same tick, so that nobody
	 * (the client, a test reading their box) sees them in their routine's pose somewhere short of it.
	 */
	private static void settle(Figure figure, double[] at) {
		figure.mode = Figure.ROUTINE;
		figure.x = at[0];
		figure.y = at[1];
		figure.z = at[2];
		figure.yaw = (float) at[3];
		figure.headYaw = figure.yaw;
		figure.speed = figure.crew.path().isEmpty() ? 0.0F : (float) figure.crew.speed();
		figure.stuck = 0;
		figure.closestTo = null;
	}

	/** Phase 20: the reinforcements' keys begin so. */
	static final String REINFORCEMENT = "reinf.";

	/** Phase 20: the flag that the crew of this mission is wary (after an alarm, until the mission ends). */
	static String wary(StorySession s) {
		return "wary." + s.mission().id();
	}

	/**
	 * Phase 20: one tick along a way to that point, round walls: the way is found anew every half
	 * second (staggered, so not all at once) or when the goal moved; close by and with nothing in
	 * between they go straight. Returns whether they moved.
	 */
	static boolean go(Figure figure, Vec3 goal, double speed, ServerLevel level, Vec3 corner, long time) {
		if (horizontal(figure.position(), goal) < 0.2 && Math.abs(figure.y - goal.y) < 1.2) {
			figure.speed = 0.0F;
			return false;
		}

		// Phase 23: a way that does not reach is looked for again only now and then (or when the
		// goal moved), not every few ticks while they wait at its end.
		boolean due = (time + figure.id) % REPATH == 0 || figure.pathGoal == null || figure.pathGoal.distanceTo(goal) > 2.0
				|| figure.pathReaches && figure.pathIndex >= figure.path.size() && horizontal(figure.position(), goal) > 1.0;

		if (due && time - figure.pathAt >= 3) {
			figure.pathAt = time;
			figure.pathGoal = goal;
			figure.pathIndex = 0;
			double height = figure.crew.height();

			if (horizontal(figure.position(), goal) < 12.0 && Paths.straight(level, corner, figure.position(), goal, height)) {
				figure.path = List.of(goal);
				figure.pathReaches = true;
			} else {
				Paths.Way way = Paths.way(level, corner, figure.position(), goal, height);
				figure.path = way.points();
				figure.pathReaches = way.reaches();
			}
		}

		// Along the way: the next point (passed when close), else straight at the goal.
		while (figure.pathIndex < figure.path.size() && horizontal(figure.position(), figure.path.get(figure.pathIndex)) < 0.3) {
			figure.pathIndex++;
		}

		if (figure.pathIndex >= figure.path.size() && !figure.pathReaches) {
			// Phase 23: no way there (a door kept shut, a gap): they wait where they came nearest and
			// watch, instead of walking on into the wall.
			figure.speed = 0.0F;
			figure.turnTowards(goal, 10.0F, true);
			return false;
		}

		Vec3 next = figure.pathIndex < figure.path.size() ? figure.path.get(figure.pathIndex) : goal;

		if (step(figure, next, speed, level, corner)) {
			return true;
		}

		// Blocked (a door still shut, someone in the way): a new way next tick.
		figure.pathGoal = null;
		return false;
	}

	private static double horizontal(Vec3 a, Vec3 b) {
		return Math.hypot(a.x - b.x, a.z - b.z);
	}

	/** Phase 20: another place to search near where they lost the player (one they can walk to), or null. */
	private static @Nullable Vec3 searchPoint(Figure figure, Vec3 around, ServerLevel level, Vec3 corner, long time) {
		for (int tries = 0; tries < 8; tries++) {
			double angle = (time * 0.37 + figure.id * 1.9 + tries * 2.3) % (Math.PI * 2.0);
			double distance = 3.5 + (tries * 1.3 + figure.id) % 3.0;
			double x = around.x + Math.cos(angle) * distance;
			double z = around.z + Math.sin(angle) * distance;
			Double y = Paths.stand(level, corner, x, around.y, z, figure.crew.height());

			if (y != null && clear(level, corner.add(around.x, around.y + 1.2, around.z), corner.add(x, y + 1.2, z))) {
				return new Vec3(x, y, z);
			}
		}

		return null;
	}

	/**
	 * One tick's step towards a point at that speed, round nothing (straight on; sliding along a
	 * wall; up a step). Returns whether they moved.
	 */
	static boolean step(Figure figure, Vec3 goal, double speed, ServerLevel level, Vec3 corner) {
		Vec3 to = new Vec3(goal.x - figure.x, 0.0, goal.z - figure.z);
		double length = to.length();

		if (length < 1.0E-3) {
			figure.speed = 0.0F;
			return false;
		}

		double reach = Math.min(length, speed / 20.0);
		Vec3 dir = to.scale(1.0 / length);
		// Phase 23: last, a little step aside (left, then right), so two who meet in a corridor get
		// past each other instead of both standing.
		double[][] tries = {{dir.x * reach, dir.z * reach}, {Math.signum(dir.x) * reach, 0.0}, {0.0, Math.signum(dir.z) * reach},
				{(dir.x * 0.4 - dir.z) * reach * 0.7, (dir.z * 0.4 + dir.x) * reach * 0.7}, {(dir.x * 0.4 + dir.z) * reach * 0.7, (dir.z * 0.4 - dir.x) * reach * 0.7}};

		for (double[] t : tries) {
			if (Math.abs(t[0]) < 1.0E-4 && Math.abs(t[1]) < 1.0E-4) {
				continue;
			}

			for (double up : new double[] {0.0, 0.55}) {
				double nx = figure.x + t[0];
				double nz = figure.z + t[1];
				double ny = figure.y + up;

				if (free(level, corner, nx, ny, nz, figure.crew.height()) && !crowded(figure, nx, ny, nz)) {
					// Down onto the floor (a step, a ledge: at most a block).
					for (int i = 0; i < 10 && free(level, corner, nx, ny - 0.1, nz, figure.crew.height()) && ny > figure.y - 1.0; i++) {
						ny -= 0.1;
					}

					// Phase 23: no step over an edge with nothing under it (a bridge without a
					// railing, the open reactor shaft): they stop at the edge instead of walking
					// on into the air and sinking.
					if (free(level, corner, nx, ny - 0.1, nz, figure.crew.height())) {
						continue;
					}

					figure.x = nx;
					figure.y = ny;
					figure.z = nz;
					float yaw = Crew.yaw(Vec3.ZERO, new Vec3(t[0], 0.0, t[1]));
					figure.yaw = Crew.lerpYaw(figure.yaw, yaw, 0.35F);
					figure.headYaw = figure.mode == Figure.CHASE ? Crew.yaw(figure.position(), goal) : figure.yaw;
					figure.speed = (float) speed;
					return true;
				}
			}
		}

		figure.speed = 0.0F;
		return false;
	}

	/**
	 * Phase 21: whether that step would bring them too close to someone else (they keep apart and
	 * do not stand in each other). A step that takes them further apart is always fine, so two
	 * who came to stand close can still part.
	 */
	static boolean crowded(Figure figure, double x, double y, double z) {
		for (Figure other : crowd) {
			if (other == figure || other.mode == Figure.DOWN || !other.ship.equals(figure.ship) || Math.abs(other.y - y) > 1.5) {
				continue;
			}

			double now = Math.hypot(other.x - figure.x, other.z - figure.z);
			double then = Math.hypot(other.x - x, other.z - z);

			if (then < APART && then < now) {
				return true;
			}
		}

		return false;
	}

	/** Phase 21: for {@link Finale}: the figures to keep apart this tick (empty after it). */
	static void crowd(List<Figure> figures) {
		crowd = figures;
	}

	static boolean free(ServerLevel level, Vec3 corner, double x, double y, double z, double height) {
		AABB box = new AABB(x - 0.28, y + 0.01, z - 0.28, x + 0.28, y + Math.min(height, 1.8), z + 0.28).move(corner);
		return level.noBlockCollision(null, box);
	}

	/** Whether a chaser is close enough to catch the player. */
	static boolean caught(Figure figure, Vec3 me) {
		double dx = me.x - figure.x;
		double dz = me.z - figure.z;
		return dx * dx + dz * dz < CATCH * CATCH && Math.abs(me.y - figure.y) < 1.5;
	}

	/** Those who could join a chase: observers near the one who saw, or near the player. Returns how many troopers joined. */
	static int rally(StorySession s, Figure first, Vec3 me) {
		int joined = 0;

		for (Figure figure : s.figures) {
			if (figure == first || !figure.observer || !first.ship.equals(figure.ship) || figure.mode == Figure.DOWN || figure.mode == Figure.HELD
					|| figure.mode == Figure.SCRIPTED || figure.mode == Figure.CHASE || figure.mode == Figure.CALL) {
				continue;
			}

			if (figure.position().distanceTo(first.position()) < 12.0 || figure.position().distanceTo(me) < 10.0) {
				if (Combat.armed(figure) && joined < 3) {
					figure.mode = Figure.CHASE;
					figure.suspicion = 1.0F;
					figure.stage = Figure.DETECTED;
					joined++;
				} else {
					figure.mode = Figure.LOOK;
					figure.target = me;
					figure.timer = 80;
					figure.suspicion = Math.max(figure.suspicion, ALERTED);
				}
			}
		}

		return joined;
	}

	/**
	 * Phase 21: a droid's noise at a point (plot space), as those near would notice it. Only someone
	 * who hears it (close with nothing in between, or very close through a wall) and is not busy
	 * (chasing, held by a scene) reacts. The nearest stops, listens a moment and then goes to see
	 * (a step short of it: looks round there and at one more place, then back to the post). The
	 * next one or two only turn to look. Whoever was drawn by a noise before believes it less: the
	 * second time they only look over, after that they do not care. Everyone else carries on.
	 */
	static Noise noise(StorySession s, String ship, Vec3 at, ServerLevel level, Vec3 corner) {
		List<Figure> heard = new ArrayList<>();

		for (Figure figure : s.figures) {
			if (!figure.observer || !ship.equals(figure.ship) || figure.mode == Figure.CHASE || figure.mode == Figure.DOWN || figure.mode == Figure.HELD
					|| figure.mode == Figure.SCRIPTED || figure.mode == Figure.CALL || figure.hostile) {
				continue;
			}

			Vec3 ear = figure.eye();
			Vec3 sound = at.add(0.0, 0.3, 0.0);
			double distance = ear.distanceTo(sound);

			if (distance > HEAR || Math.abs(figure.y - at.y) > 2.5) {
				continue;
			}

			if (distance <= HEAR_WALL || clear(level, corner.add(ear), corner.add(sound))) {
				heard.add(figure);
			}
		}

		heard.sort(java.util.Comparator.comparingDouble(figure -> figure.position().distanceTo(at)));
		List<Figure> went = new ArrayList<>();
		List<Figure> looked = new ArrayList<>();
		List<Figure> ignored = new ArrayList<>();

		for (Figure figure : heard) {
			if (figure.distracted >= 2) {
				ignored.add(figure);
				continue;
			}

			boolean go = went.isEmpty() && figure.distracted == 0;

			if (!go && looked.size() >= 2) {
				continue;
			}

			figure.distracted++;
			figure.mode = Figure.LOOK;
			figure.target = at;
			figure.timer = go ? LISTEN : 30;
			figure.lure = go ? at : null;
			figure.suspicion = Math.max(figure.suspicion, go ? 0.45F : 0.4F);
			(go ? went : looked).add(figure);
		}

		s.dirty = true;
		return new Noise(went, looked, ignored);
	}

	/** Phase 21: who went to see a noise, who only looked over, who did not care any more. */
	record Noise(List<Figure> went, List<Figure> looked, List<Figure> ignored) {
	}

	/** The figure nearest a point within reach, of those matching. */
	static @Nullable Figure nearest(StorySession s, String ship, Vec3 at, double reach) {
		Figure best = null;
		double bestD = reach;

		for (Figure figure : s.figures) {
			double d = figure.position().distanceTo(at);

			if (ship.equals(figure.ship) && d < bestD && figure.mode != Figure.DOWN) {
				best = figure;
				bestD = d;
			}
		}

		return best;
	}
}
