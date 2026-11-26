package com.phiro.sabercraft.story;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.phiro.sabercraft.interior.Crew;
import com.phiro.sabercraft.interior.DestroyerInterior;
import com.phiro.sabercraft.interior.ShipInteriors;

/**
 * Phase 25: the short scenes of the story shot where it happens (the client's {@code SceneFilm})
 * and what goes with them, and the escape out of the reactor by the maintenance way.
 *
 * <p>A scene runs on a timer of its own ({@code scene}); while it runs nobody sees the player and
 * nobody fights (the story's {@code tmp.film}), and the player cannot move (the film's screen).
 * Each scene does what it shows at fixed moments, and does all of it at once when skipped, so the
 * story is the same either way, and never depends on where the player stood.
 *
 * <ul>
 * <li><b>The ISB officer</b> ({@code hale_in}): as soon as the card is taken, Hale comes into the
 * armory through its door (it opens for him, shuts and bolts behind him), stops before the player
 * and the talk begins. Nothing waits for the quartermaster or for the player to come back.
 * <li><b>The escape out of the reactor</b> ({@code service}): after the copy, with the alarm, the
 * way out is the maintenance walkway along the north-east wall to the maintenance door: its
 * emergency release is pulled by hand (a few seconds beside it), the panel inside bolts the door,
 * the hatch in the chamber leads into the ventilation. Crawling in starts the scene: the troopers
 * come to the bolted door, hammer on it, call it in and spread out to search the floor; the
 * player comes out in the hideout. The alarm goes on (a search, then the crew stays wary).
 * <li><b>The saber</b> ({@code saber}): the player steps up to the field, takes the saber from its
 * holder; the field dies, the reactor answers, the lab's lights flicker, sparks, the first tremor.
 * </ul>
 */
final class Scenes {
	static final double H = DestroyerInterior.HANGAR + 1;
	static final double R = DestroyerInterior.REACTOR_BOTTOM;
	/** The scenes' lengths (as the client's {@code SceneFilm}); the server waits a few ticks more. */
	static final int HALE_FILM = 130;
	static final int SERVICE_FILM = 150;
	static final int SABER_FILM = 200;
	static final int SLACK = 6;

	private static int started;
	private static int skipped;

	private Scenes() {
	}

	/** For the tests: how many scenes started and how many were skipped since the game started. */
	static int startedForTest() {
		return started;
	}

	static int skippedForTest() {
		return skipped;
	}

	/** A scene begins (the client shows it; the plot's corner goes with it). */
	static void start(StorySession s, String film, int ticks) {
		s.set("tmp.film");
		s.set("tmp.scene");
		s.set("tmp.scene." + film);
		s.timer("scene", ticks + SLACK);
		started++;
		Story.cue(s, Story.CUE_CINEMA, film, "", ticks, List.of(Vec3.atLowerCornerOf(s.destroyer)));
		s.dirty = true;
	}

	/** Whether that scene runs now. */
	static boolean running(StorySession s, String film) {
		return s.has("tmp.scene." + film);
	}

	/** How far that scene is (ticks since it began). */
	static int age(StorySession s, int ticks) {
		return ticks + SLACK - s.timers.getOrDefault("scene", 0);
	}

	/** Whether the scene's time is up. */
	static boolean over(StorySession s) {
		return !s.timer("scene");
	}

	/** The scene is over (played through or skipped). */
	static void end(StorySession s) {
		s.unset("tmp.film");
		s.unset("tmp.scene");
		s.flags.removeIf(flag -> flag.startsWith("tmp.scene."));
		s.timers.remove("scene");
		s.dirty = true;
	}

	/** The player skipped a scene: what it shows is done at once. */
	static void skip(StorySession s, String film) {
		if (!running(s, film)) {
			return;
		}

		skipped++;

		switch (film) {
			case "hale_in" -> haleDone(s);
			case "service" -> serviceDone(s);
			case "saber" -> saberDone(s);
			default -> end(s);
		}
	}

	private static float yaw(Vec3 from, Vec3 to) {
		return (float) Math.toDegrees(Math.atan2(-(to.x - from.x), to.z - from.z));
	}

	// --- The ISB officer comes into the armory -----------------------------------------------------

	/** Where the player stands when Hale comes in (by the quartermaster's desk), where Hale stops, where he waits outside. */
	static final Vec3 HALE_PLAYER = new Vec3(48.6, H, -7.6);
	static final Vec3 HALE_STOP = new Vec3(47.1, H, -9.3);
	static final Vec3 HALE_OUTSIDE = new Vec3(42.7, H, -12.7);
	/** The moment the door is bolted behind him. */
	static final int HALE_BOLT = 88;

	/**
	 * The card is taken: Hale comes in at once (the scene). The quartermaster has been paged away
	 * already (his desk is empty); the player stands by the desk, facing the door.
	 */
	static void haleBegin(StorySession s) {
		Figure hale = s.figure("hale");

		if (hale == null) {
			return;
		}

		// The quartermaster is not in the room any more (if he still was, the page took him).
		if (!s.has("qm.away")) {
			s.set("qm.away");
		}

		Figure quartermaster = s.figure("quartermaster");

		if (quartermaster != null && DestroyerInterior.ARMORY.inflate(3.0).contains(quartermaster.position().add(0.0, 0.5, 0.0))) {
			quartermaster.crew = Chapter1.QM_AWAY;
			quartermaster.snapToRoutine(s.time());
		}

		s.unset("tmp.qm_leaving");
		s.unset("tmp.rehome.quartermaster");
		// The player is in the armory with the card: it was opened for them (a file of this very
		// moment may not know it yet; without it Hale could not come in, nor anyone go out later).
		s.set("armory.open");
		// He comes in: the door opens for him alone, then shuts and is bolted (see Chapter1.locked).
		s.set("tmp.hale_in");
		s.set("tmp.hale_door");
		s.set("tmp.hale_entering");
		hale.x = HALE_OUTSIDE.x;
		hale.y = H;
		hale.z = HALE_OUTSIDE.z;
		hale.yaw = 270.0F;
		hale.headYaw = hale.yaw;
		hale.blind = true;
		Chapter1.walk(hale, new Vec3(43.8, H, -12.9), new Vec3(45.6, H, -12.9), new Vec3(46.5, H, -11.2), HALE_STOP);
		Story.place(s, Story.DESTROYER, HALE_PLAYER, yaw(HALE_PLAYER, HALE_STOP));
		start(s, "hale_in", HALE_FILM);
	}

	/** The scene runs: the door is bolted behind him; at its end the talk begins. Returns whether it runs. */
	static boolean haleTick(StorySession s) {
		if (!running(s, "hale_in")) {
			return false;
		}

		int t = age(s, HALE_FILM);

		if (t == HALE_BOLT) {
			s.unset("tmp.hale_entering");
			Vec3 door = new Vec3(DestroyerInterior.CX + 4.5, H + 1.0, -12.5).add(Vec3.atLowerCornerOf(s.destroyer));
			ServerLevel level = Chapter1.interiors(s);

			if (level != null) {
				level.playSound(null, door.x, door.y, door.z, ShipInteriors.sound("story.door_lock"), SoundSource.BLOCKS, 0.9F, 0.9F);
			}
		}

		if (over(s)) {
			haleDone(s);
		}

		return true;
	}

	/** The scene's end (or skipped): Hale before the player, the door bolted, the talk. */
	static void haleDone(StorySession s) {
		end(s);
		Figure hale = s.figure("hale");
		s.unset("tmp.hale_entering");
		s.set("tmp.hale_in");
		s.set("tmp.hale_alone");
		s.set("tmp.hale_door");

		if (hale == null) {
			return;
		}

		hale.x = HALE_STOP.x;
		hale.y = H;
		hale.z = HALE_STOP.z;
		hale.way = List.of();
		hale.wayIndex = 0;
		hale.mode = Figure.HELD;
		hale.speed = 0.0F;
		hale.target = HALE_PLAYER;
		hale.yaw = yaw(HALE_STOP, HALE_PLAYER);
		hale.headYaw = hale.yaw;
		Story.place(s, Story.DESTROYER, HALE_PLAYER, yaw(HALE_PLAYER, HALE_STOP));
		s.set("tmp.hale_talked");
		s.talk("m3_hale", hale);
	}

	// --- The escape out of the reactor -------------------------------------------------------------

	/**
	 * How long the emergency release takes (ticks). Phase 30: it runs by itself once begun (one
	 * right click on its panel): the player need not stay beside it, look at it, or click again.
	 * (It took 40 ticks and stopped whenever one stepped more than 1.9 blocks away from it.)
	 */
	static final int RELEASE = 28;
	/** Where the troopers who reach the bolted door stand, and the places round it they search after. */
	static final Vec3 DOOR_OUTSIDE = new Vec3(48.9, R, 16.2);
	static final List<Vec3> SEARCH = List.of(new Vec3(50.6, R, 21.4), new Vec3(52.0, R, 26.8), new Vec3(49.2, R, 28.6), new Vec3(48.8, R, 17.6));
	/** When the reinforcements come down the lift after the alarm (ticks). */
	static final int REINFORCE = 220;
	/** The east end of the north platform's guard's round (across the gap from the maintenance door). */
	static final Vec3 NORTH_GUARD = new Vec3(43.4, R, 13.4);
	private static int releases;
	private static int bolts;

	static int releasesForTest() {
		return releases;
	}

	static int boltsForTest() {
		return bolts;
	}

	/** Mission 5's escape begins (also again, at its checkpoint): the way lit, the hunt on. */
	static void escapeBegin(StorySession s, boolean again) {
		s.set("service.lit");
		s.unset("tmp.release");

		if (!again) {
			s.radio("kestrel", "sabercraft.story.radio.m5_service");
		} else {
			s.hint("sabercraft.story.hint.service_way");
		}

		// The reinforcements come down the lift, always at the same moment (not more after a capture).
		s.set("tmp.reinforce");
		s.timer("reinforce", REINFORCE);
	}

	/** Every tick of the escape: the release being pulled, the crawl way reached. */
	static void escapeTick(StorySession s) {
		if (running(s, "service")) {
			serviceTick(s);
			return;
		}

		if (s.has("tmp.release")) {
			// Phase 30: once begun it runs by itself, wherever the player is.
			int progress = s.counters.merge("release", 1, Integer::sum);

			if (progress % 2 == 0) {
				Story.cue(s, Story.CUE_HINT, "progress", "sabercraft.story.hint.service_release", progress * 100 / RELEASE);
			}

			if (progress >= RELEASE) {
				released(s);
			}
		}
		// Phase 32: no control text on the walkway any more (Phiro: no explaining texts); the
		// objective, Kestrel's radio, the marker and the panel's own prompt show the way.

		// In the crawl way: the scene.
		if (Story.DESTROYER.equals(s.here) && DestroyerInterior.SERVICE_VENT.inflate(0.0, 0.5, 0.0).contains(s.local(Story.DESTROYER).add(0.0, 0.3, 0.0))) {
			serviceBegin(s);
		}
	}

	/** The emergency release is clicked (or the shut door): Phase 30: it unlocks by itself in RELEASE ticks, wherever the player goes. */
	static void release(StorySession s) {
		if (s.has("service.open") || s.has("tmp.release")) {
			return;
		}

		s.set("tmp.release");
		s.counters.put("release", 0);
		Story.cue(s, Story.CUE_HINT, "progress", "sabercraft.story.hint.service_release", 0);
		Story.cue(s, Story.CUE_SOUND, "", "story.hack", 0);
		// Phase 30: it runs by itself now (the hint to click goes; the bar shows over it).
		// (Phase 32: no more words on how it works: Phiro wants no technical texts in the game.)
	}

	/** The release is pulled: the door opens (for anyone near it, as doors do). */
	private static void released(StorySession s) {
		s.unset("tmp.release");
		s.set("service.open");
		releases++;
		Story.cue(s, Story.CUE_HINT, "progress", "sabercraft.story.hint.service_release", 100);
		Story.cue(s, Story.CUE_SOUND, "", "story.door_lock", 0);
		s.hint("sabercraft.story.hint.service_open");
	}

	/** The panel inside bolts the door (not with someone in the doorway). */
	static void bolt(StorySession s) {
		if (!s.has("service.open") || s.has("service.locked")) {
			return;
		}

		// Phase 30: only from behind the door. Its panel can be reached through the open doorway, and
		// bolted from outside the door shut the player out of the crawl way, their way out.
		if (!Chapter1.behindServiceDoor(s)) {
			s.hint("sabercraft.story.hint.service_bolt_inside");
			return;
		}

		AABB doorway = new AABB(48.0, R, DestroyerInterior.SERVICE_DOOR_Z - 0.4, 50.0, R + 2.0, DestroyerInterior.SERVICE_DOOR_Z + 1.4);

		for (Figure figure : s.figures) {
			if (Story.DESTROYER.equals(figure.ship) && doorway.contains(figure.position().add(0.0, 0.5, 0.0))) {
				s.hint("sabercraft.story.hint.service_blocked");
				return;
			}
		}

		bolted(s, true);
	}

	/** The door is bolted: those after the player come to it, find it shut, and search the floor. */
	private static void bolted(StorySession s, boolean byHand) {
		s.set("service.locked");
		bolts++;
		Story.cue(s, Story.CUE_SOUND, "", "story.door_lock", 0);
		s.hint(byHand ? "sabercraft.story.hint.service_bolted" : "sabercraft.story.hint.service_shut");

		// Lost them: they search where the player vanished (the alarm goes on as a search).
		s.huntAt = DOOR_OUTSIDE;
		s.huntSeen = Math.min(s.huntSeen, s.time() - Stealth.LOSE);
		s.searching = true;
		s.unseen = Math.max(s.unseen, Stealth.LOSE);
		s.alarm = Math.max(s.alarm, Stealth.SEARCH_LONG);
		int i = 0;

		for (Figure figure : s.figures) {
			if (!Story.DESTROYER.equals(figure.ship) || figure.y > R + 3.0 || figure.mode == Figure.DOWN || figure.hostile) {
				continue;
			}

			if (figure.mode == Figure.CHASE || figure.mode == Figure.INVESTIGATE || figure.key.startsWith(Stealth.REINFORCEMENT)) {
				figure.mode = Figure.INVESTIGATE;
				figure.target = i == 0 ? DOOR_OUTSIDE : SEARCH.get(i % SEARCH.size());
				figure.timer = 60;
				figure.searches = 3;
				figure.windup = 0;
				figure.burst = 0;
				i++;
			}
		}

		s.dirty = true;
	}

	/**
	 * Back in the hideout the old way (through the crawl ways): they lost the player too. Those
	 * after the player search where they last saw them; the alarm goes on as a search.
	 */
	static void lostInHideout(StorySession s) {
		Vec3 last = s.huntAt;
		s.huntSeen = Math.min(s.huntSeen, s.time() - Stealth.LOSE);
		s.searching = true;
		s.unseen = Math.max(s.unseen, Stealth.LOSE);
		s.alarm = Math.max(s.alarm, Stealth.SEARCH_LONG);

		for (Figure figure : s.figures) {
			if (Story.DESTROYER.equals(figure.ship) && figure.mode == Figure.CHASE && !figure.hostile) {
				figure.mode = Figure.INVESTIGATE;
				figure.target = last != null ? last : figure.position();
				figure.timer = 60;
				figure.searches = 3;
				figure.windup = 0;
				figure.burst = 0;
			}
		}

		s.dirty = true;
	}

	/** In the crawl way: the scene (the door is bolted behind the player if they did not). */
	private static void serviceBegin(StorySession s) {
		if (s.has("tmp.film") || !s.at("m5.escape")) {
			return;
		}

		if (!s.has("service.locked")) {
			bolted(s, false);
		}

		// Two of those after the player come running along the walkway to the door (the scene shows
		// them); with nobody near, two of the reinforcements are there already.
		List<Figure> come = new ArrayList<>();

		for (Figure figure : s.figures) {
			if (come.size() < 2 && Story.DESTROYER.equals(figure.ship) && !figure.hostile && figure.mode != Figure.DOWN && figure.y < R + 3.0
					&& (figure.mode == Figure.INVESTIGATE || figure.mode == Figure.CHASE || figure.key.startsWith(Stealth.REINFORCEMENT))
					&& Crew.TROOPER.equals(figure.kind())) {
				come.add(figure);
			}
		}

		for (int i = come.size(); i < 2; i++) {
			Figure template = new Figure(0, Stealth.REINFORCEMENT + "svc." + s.figures.size() + "." + i,
					Crew.at(Crew.TROOPER, Crew.STAND, DestroyerInterior.REACTOR_LIFT_ARRIVAL.x, R, DestroyerInterior.REACTOR_LIFT_ARRIVAL.z, 90.0F));
			template.ship = Story.DESTROYER;
			template.observer = true;
			template.talks = false;
			template.lamp = true;
			come.add(s.add(template));
		}

		for (int i = 0; i < come.size(); i++) {
			Figure trooper = come.get(i);
			trooper.x = i == 0 ? 50.4 : 49.2;
			trooper.y = R;
			trooper.z = i == 0 ? 22.6 : 23.4;
			trooper.blind = true;
			trooper.windup = 0;
			trooper.burst = 0;
			Chapter1.walk(trooper, new Vec3(49.6, R, 19.4), i == 0 ? new Vec3(49.3, R, 15.6) : new Vec3(48.5, R, 16.6));
			s.set("tmp.svc." + trooper.key);
		}

		start(s, "service", SERVICE_FILM);
	}

	/** The scene's moments: the hammering on the door, the call, the search; the player out of it. */
	private static void serviceTick(StorySession s) {
		int t = age(s, SERVICE_FILM);
		ServerLevel level = Chapter1.interiors(s);
		Vec3 corner = Vec3.atLowerCornerOf(s.destroyer);
		Vec3 door = new Vec3(49.0, R + 1.2, DestroyerInterior.SERVICE_DOOR_Z + 1.0).add(corner);

		if (level != null && (t == 62 || t == 70 || t == 78)) {
			level.playSound(null, door.x, door.y, door.z, ShipInteriors.sound("story.armour_hit"), SoundSource.BLOCKS, 0.9F, 0.6F + t * 0.002F);
		}

		if (t == 100) {
			// They spread out over the floor (the search goes on after the scene).
			int i = 0;

			for (Figure figure : s.figures) {
				if (s.has("tmp.svc." + figure.key)) {
					figure.blind = false;
					figure.mode = Figure.INVESTIGATE;
					figure.target = SEARCH.get(i++ % SEARCH.size());
					figure.timer = 50;
					figure.searches = 3;
				}
			}
		}

		if (t == 122) {
			// Out of the crawl way: in the hideout (behind the scene's black).
			Story.place(s, Story.DESTROYER, Chapter1.HIDEOUT, 90.0F);
		}

		if (over(s)) {
			serviceDone(s);
		}
	}

	/** The scene's end (or skipped): the player in the hideout, the troopers searching, the escape done. */
	private static void serviceDone(StorySession s) {
		end(s);

		for (Figure figure : s.figures) {
			if (s.has("tmp.svc." + figure.key)) {
				s.unset("tmp.svc." + figure.key);
				figure.blind = false;

				if (figure.mode == Figure.SCRIPTED || figure.mode == Figure.HELD) {
					figure.mode = Figure.INVESTIGATE;
					figure.target = DOOR_OUTSIDE;
					figure.timer = 50;
					figure.searches = 3;
				}
			}
		}

		if (!Chapter1.HIDDEN.contains(s.local(Story.DESTROYER).add(0.0, 0.5, 0.0))) {
			Story.place(s, Story.DESTROYER, Chapter1.HIDEOUT, 90.0F);
		}

		s.radio("kestrel", "sabercraft.story.radio.m5_service_out");

		if (s.at("m5.escape")) {
			s.complete();
		}
	}

	// --- The saber ---------------------------------------------------------------------------------

	/** Where the player stands to take it (before the field), and the moments of the scene. */
	static final Vec3 SABER_PLAYER = new Vec3(44.5, R, 48.7);
	static final int SABER_TAKE = 62;
	static final int SABER_SURGE = 84;
	static final int SABER_CORE = 136;
	/** Where the lab sparks as its lights fail (plot space). */
	private static final List<Vec3> LAB_SPARKS = List.of(new Vec3(38.2, R + 2.6, 41.4), new Vec3(50.8, R + 2.4, 43.0), new Vec3(41.0, R + 3.2, 50.6),
			new Vec3(48.4, R + 2.2, 51.2), new Vec3(44.5, R + 3.4, 45.0));

	/** "Take it": the scene (the saber is in the hand when it ends, or at once when skipped). */
	static void saberBegin(StorySession s) {
		Story.place(s, Story.DESTROYER, SABER_PLAYER, 0.0F);
		start(s, "saber", SABER_FILM);
	}

	/** The scene's moments. Returns whether it runs. */
	static boolean saberTick(StorySession s) {
		if (!running(s, "saber")) {
			return false;
		}

		int t = age(s, SABER_FILM);
		ServerLevel level = Chapter1.interiors(s);
		ServerPlayer player = s.player();
		Vec3 corner = Vec3.atLowerCornerOf(s.destroyer);

		if (t == 40 && player != null) {
			player.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
		}

		if (t == SABER_TAKE) {
			Chapter1.giveSaber(s);
		}

		if (t == SABER_SURGE) {
			Chapter1.saberSurge(s);
		}

		if (level != null && t > SABER_SURGE && t < SABER_CORE + 30 && t % 9 == 0) {
			// The lab's panels spark as the power surges through them.
			Vec3 at = LAB_SPARKS.get((t / 9) % LAB_SPARKS.size()).add(corner);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 14, 0.25, 0.2, 0.25, 0.3);
			level.sendParticles(ParticleTypes.SMOKE, at.x, at.y, at.z, 3, 0.15, 0.1, 0.15, 0.01);

			if (t % 27 == 0) {
				level.playSound(null, at.x, at.y, at.z, ShipInteriors.sound("story.reactor_arc"), SoundSource.BLOCKS, 0.7F, 1.2F);
			}
		}

		if (t == SABER_CORE && level != null) {
			// The core flares (seen from the door), the first tremor.
			Vec3 core = new Vec3(DestroyerInterior.REACTOR_X, R + 3.0, DestroyerInterior.REACTOR_Z).add(corner);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, core.x, core.y, core.z, 60, 1.6, 2.5, 1.6, 0.6);
			level.sendParticles(net.minecraft.core.particles.ColorParticleOption.create(ParticleTypes.FLASH, 0xFFFFE6C8), core.x, core.y, core.z, 1, 0.0, 0.0, 0.0, 0.0);
			level.playSound(null, core.x, core.y, core.z, ShipInteriors.sound("story.reactor_arc"), SoundSource.BLOCKS, 2.0F, 0.6F);
			Story.cue(s, Story.CUE_DUEL, "quake", "", 26);
		}

		if (over(s)) {
			saberDone(s);
		}

		return true;
	}

	/** The scene's end (or skipped): the saber in hand, the fight begins. */
	static void saberDone(StorySession s) {
		end(s);

		if (!s.has("saber.taken")) {
			Chapter1.giveSaber(s);
		}

		if (!s.has("tmp.saber_surge")) {
			Chapter1.saberSurge(s);
		}

		Chapter1.saberFight(s);
	}
}
