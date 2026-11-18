package com.phiro.sabercraft.story;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import com.phiro.sabercraft.character.Outfit;
import com.phiro.sabercraft.character.OutfitSync;
import com.phiro.sabercraft.interior.Crew;
import com.phiro.sabercraft.interior.DestroyerInterior;
import com.phiro.sabercraft.interior.FalconInterior;
import com.phiro.sabercraft.interior.InteriorJump;
import com.phiro.sabercraft.interior.InteriorPlan;
import com.phiro.sabercraft.interior.InteriorSystems;
import com.phiro.sabercraft.interior.ShipInteriors;
import com.phiro.sabercraft.saber.SaberVariant;

/**
 * Phase 19: Kapitel 1, "Nachtschatten". A rebel spy (the player, "Funke") is brought by the
 * Falcon to the Star Destroyer <i>Tenebris</i>, where Kestrel, who has hidden aboard for months,
 * found out what the Empire's project "Nachtschatten" is: not a ship, a hunt for children with the
 * Force, tracked by a crystal found in the ruins of a temple. Six missions: getting aboard, the
 * first contact and the dead drop, the armory and the disguise (and Hale, who offers a deal), the
 * reactor's data, the discovery (Kestrel is gone; Hale's offer was a trap, or it was not taken),
 * and the finale with the saber of a Jedi who died there, a duel against the Schattenklinge and
 * the escape.
 *
 * <p>The people and the places are in plot space of the player's own story ships. The rules of
 * seeing and chasing are {@link Stealth}'s; the fight at the end is {@link Finale}'s; the words
 * are in the language files; the conversations are {@link Chapter1Dialogs}.
 */
public final class Chapter1 implements Chapter {
	public static final String ID = "nightshade";
	public static final Chapter1 INSTANCE = new Chapter1();

	private static final String F = Story.FALCON;
	private static final String D = Story.DESTROYER;
	/** Where one stands on the hangar deck, on the reactor's floor, in the Falcon (plot space). */
	private static final int H = DestroyerInterior.HANGAR + 1;
	private static final int R = DestroyerInterior.REACTOR_BOTTOM;
	private static final int FF = FalconInterior.FLOOR + 1;

	// Places (plot space).
	static final Vec3 FALCON_LOUNGE = new Vec3(21.5, FF, 28.5);
	static final Vec3 FALCON_COCKPIT = new Vec3(43.5, FF, 25.5);
	static final Vec3 FALCON_ENTRY = new Vec3(35.5, FF, 31.5);
	static final Vec3 AIRLOCK = new Vec3(6.5, H, 10.0);
	static final Vec3 HIDEOUT = new Vec3(20.5, H, -4.5);
	static final Vec3 REACTOR_FLOOR = new Vec3(28.8, R, 25.0);
	static final Vec3 CELL_2 = new Vec3(31.5, H, 0.0);
	static final Vec3 LIFT_TOP = new Vec3(28.8, H, 25.0);
	/** Phase 20: the containers of the dead drop (their mark, the west end of each along x), the contact's is K-7. */
	static final String[][] CRATES = {{"c3", "20"}, {"f9", "23"}, {"k7", "26"}};
	/** Phase 20: by the stack of crates on the hangar's east side. */
	static final Vec3 HANGAR_CRATES = new Vec3(57.3, H, -27.9);
	/** Phase 20: safe places: the maintenance room's corner by the crates (behind the guard at Kestrel's hatch), the armory once its master is gone, the crawl way at the detention block's hatch. */
	static final Vec3 MAINT_SAFE = new Vec3(3.5, H, -5.5);
	static final Vec3 ARMORY_SAFE = new Vec3(50.5, H, -7.5);
	static final Vec3 LOCKERS_SAFE = new Vec3(54.2, H, -10.5);
	static final Vec3 VENT_DETENTION = new Vec3(24.5, H, 6.4);
	/** Phase 20: the reactor floor's wall comm (by the lift) and the caf dispenser beside it. */
	static final Vec3 REACTOR_COMM = new Vec3(30.4, R, 21.2);
	static final Vec3 CAF = new Vec3(30.9, R, 28.6);
	/** Phase 20: by the cell console where R2-V7 was (behind the detention block's guard). */
	static final Vec3 CONSOLE_SAFE = new Vec3(27.5, H, 7.6);
	/** Phase 20: the guard's post in front of Kestrel's hatch (Mission 1), facing into the room. */
	static final Vec3 HATCH_POST = new Vec3(10.5, H, -7.35);
	/** Phase 20: the detention block: in the corridor behind its guard (after the cell), and where M-31 waits by the bench. */
	static final Vec3 DETENTION_SAFE = new Vec3(31.5, H, 3.6);
	static final Vec3 BENCH = new Vec3(32.5, H, 8.4);
	/** Phase 20: where the quartermaster sees to the freight once called away (by the hangar's crates). */
	static final Crew QM_AWAY = Crew.at(Crew.OFFICER_OLIVE, Crew.WORK, HANGAR_CRATES.x, H, HANGAR_CRATES.z, 200.0F);
	/** The hidden room (Kestrel's hideout), the reactor's floor, the shaft (plot space). */
	static final AABB HIDDEN = DestroyerInterior.HIDDEN;
	static final AABB LOBBY_DARK = new AABB(36, H - 1, -16, 51, H + 5, 12);
	static final AABB CONSOLE_AREA = new AABB(49.5, R - 0.5, 22.5, 53.0, R + 2.5, 26.5);
	/** How long the reactor's data take to copy (ticks at the console). */
	static final int DOWNLOAD = 120;
	/** How long the astromech needs to open a lock (ticks). */
	static final int HACK = 60;
	/** Phase 20: how long one is in the cell before the hint how to get out (ticks). */
	static final int CELL_HINT = 160;

	private static final List<Mission> MISSIONS = build();

	private Chapter1() {
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public List<Mission> missions() {
		return MISSIONS;
	}

	@Override
	public @Nullable Dialog dialog(String id) {
		return Chapter1Dialogs.get(id);
	}

	// --- The missions ------------------------------------------------------------------------------

	private static List<Mission> build() {
		Mission.Target kestrel = at(D, 15.0, H + 1.0, -5.5);
		Mission.Target hideout = at(D, 18.5, H + 0.5, -4.5);

		// Phase 20: each step's place is its safe place (where nobody sees the player); steps marked
		// "cp" make a checkpoint as they begin, the others are played again from the one before
		// (or from one made during them: milestones, the hideout, the crawl ways).
		Mission m1 = new Mission("m1", List.of(
				cp("m1.briefing", F, FALCON_LOUNGE, 150.0F, at(F, 19.2, FF + 1.0, 24.8)),
				cp("m1.cockpit", F, FALCON_LOUNGE, 150.0F, at(F, 42.5, FF + 1.0, 21.7)),
				cp("m1.ramp", F, FALCON_COCKPIT, 0.0F, at(F, 35.5, 4.0, 40.5)),
				cp("m1.droid", D, AIRLOCK, 180.0F, at(D, 2.6, H + 0.4, -6.0)),
				cp("m1.distract", D, MAINT_SAFE, 90.0F, at(D, 10.5, H + 1.9, -7.4)),
				step("m1.hatch", D, MAINT_SAFE, 90.0F, at(D, 10.5, H + 0.5, -8.6))),
				List.of(new Mission.Optional("pad", s -> true)));

		Mission m2 = new Mission("m2", List.of(
				cp("m2.kestrel", D, HIDEOUT, 90.0F, kestrel),
				cp("m2.terminal", D, HIDEOUT, 90.0F, at(D, 18.0, H + 1.1, -1.4)),
				cp("m2.drop", D, HIDEOUT, 90.0F, at(D, 10.5, H + 0.5, -34.0)),
				step("m2.return", D, HIDEOUT, 90.0F, kestrel)),
				List.of(new Mission.Optional("eavesdrop", s -> true)));

		Mission m3 = new Mission("m3", List.of(
				cp("m3.plan", D, HIDEOUT, 90.0F, kestrel),
				cp("m3.console", D, HIDEOUT, 90.0F, at(D, 43.5, H + 0.6, -8.5)),
				step("m3.armour", D, HIDEOUT, 90.0F, at(D, 55.6, H + 1.0, -8.5)),
				step("m3.card", D, HIDEOUT, 90.0F, at(D, 49.5, H + 0.95, -5.5)),
				step("m3.hale", D, HIDEOUT, 90.0F, null),
				cp("m3.return", D, ARMORY_SAFE, 90.0F, hideout)),
				List.of(new Mission.Optional("eavesdrop", s -> !s.has("know.password")), new Mission.Optional("armour2", s -> s.has("wear.armour"))));

		Mission m4 = new Mission("m4", List.of(
				cp("m4.brief", D, HIDEOUT, 90.0F, kestrel),
				cp("m4.checkpoint", D, HIDEOUT, 90.0F, at(D, 42.6, H + 1.0, -0.5)),
				cp("m4.lift", D, new Vec3(47.5, H, -1.0), 0.0F, at(D, 31.0, H + 0.5, 25.0)),
				cp("m4.tech", D, REACTOR_FLOOR, 270.0F, at(D, 51.05, R + 1.0, 24.5)),
				step("m4.download", D, REACTOR_FLOOR, 270.0F, at(D, 52.5, R + 0.6, 24.5))),
				List.of(new Mission.Optional("coolant", s -> s.has("m4.passed"))));

		Mission m5 = new Mission("m5", List.of(
				cp("m5.escape", D, new Vec3(49.8, R, 24.5), 90.0F, hideout),
				cp("m5.recording", D, HIDEOUT, 90.0F, at(D, 22.5, H + 1.2, -4.5)),
				cp("m5.route", D, HIDEOUT, 90.0F, null),
				step("m5.cell", D, HIDEOUT, 90.0F, null),
				cp("m5.r2", D, VENT_DETENTION, 270.0F, at(D, 27.5, H + 0.6, 9.4)),
				cp("m5.cell1", D, CONSOLE_SAFE, 0.0F, at(D, 27.5, H + 1.0, 2.5)),
				cp("m5.back", D, new Vec3(27.3, H, 5.5), 90.0F, hideout)),
				List.of(new Mission.Optional("prisoner", s -> s.has("droid.r2v7"))));

		Mission m6 = new Mission("m6", List.of(
				cp("m6.plan", D, HIDEOUT, 90.0F, kestrel),
				cp("m6.reactor", D, HIDEOUT, 90.0F, at(D, 31.0, H + 0.5, 25.0)),
				cp("m6.door", D, REACTOR_FLOOR, 270.0F, at(D, 44.5, R + 1.0, 38.6)),
				cp("m6.lab", D, new Vec3(44.5, R, 41.0), 0.0F, at(D, 39.5, R + 0.6, 46.5)),
				cp("m6.saber", D, new Vec3(44.5, R, 41.0), 0.0F, at(D, 44.5, R + 1.0, 50.5)),
				cp("m6.escape1", D, new Vec3(44.5, R, 47.5), 180.0F, at(D, 31.0, R + 0.5, 25.0)),
				cp("m6.escape2", D, LIFT_TOP, 180.0F, at(D, 40.5, H + 1.0, -16.0)),
				cp("m6.duel", D, new Vec3(40.5, H, -18.5), 180.0F, null),
				cp("m6.falcon", D, new Vec3(40.5, H, -24.0), 180.0F, at(D, 40.5, H, -38.0)),
				cp("m6.jump", F, FALCON_ENTRY, 90.0F, at(F, 42.5, FF + 1.0, 21.7)),
				cp("m6.epilogue", F, FALCON_LOUNGE, 150.0F, null)),
				List.of());

		return List.of(m1, m2, m3, m4, m5, m6);
	}

	/** A step that makes a checkpoint as it begins. */
	private static Mission.Step cp(String id, String ship, Vec3 spawn, float yaw, Mission.@Nullable Target target) {
		return new Mission.Step(id, ship, spawn, yaw, false, true, target);
	}

	/** A step played again from the checkpoint before it. */
	private static Mission.Step step(String id, String ship, Vec3 spawn, float yaw, Mission.@Nullable Target target) {
		return new Mission.Step(id, ship, spawn, yaw, false, false, target);
	}

	private static Mission.Target at(String ship, double x, double y, double z) {
		return new Mission.Target(ship, new Vec3(x, y, z));
	}

	/** Phase 20: the steps of Phase 19 (a file of Phase 19 has only the step's number). */
	private static final List<List<String>> PHASE19 = List.of(
			List.of("m1.briefing", "m1.cockpit", "m1.ramp", "m1.droid", "m1.distract"),
			List.of("m2.kestrel", "m2.terminal", "m2.drop", "m2.return"),
			List.of("m3.plan", "m3.console", "m3.armour", "m3.card", "m3.hale", "m3.return"),
			List.of("m4.brief", "m4.checkpoint", "m4.lift", "m4.tech", "m4.download"),
			List.of("m5.escape", "m5.recording", "m5.route", "m5.route", "m5.r2", "m5.cell1", "m5.back"),
			List.of("m6.plan", "m6.reactor", "m6.door", "m6.lab", "m6.saber", "m6.escape1", "m6.escape2", "m6.duel", "m6.falcon", "m6.jump", "m6.epilogue"));

	/**
	 * Phase 20: the step a file of Phase 19 meant by its number. Two are played a little differently
	 * now: at Kestrel's hatch one first lures the guard away (the step in front of it), and Hale's
	 * trap begins at the way to the detention block (as the checkpoint is made in the cell now).
	 */
	static String phase19Step(int mission, int step) {
		if (mission < 0 || mission >= PHASE19.size()) {
			return "";
		}

		List<String> steps = PHASE19.get(mission);
		return steps.get(Math.max(0, Math.min(step, steps.size() - 1)));
	}

	/** Phase 20: a step's id that is no longer used, and the one in its place. */
	static String renamedStep(String id) {
		return id;
	}

	/** What is known at the start of a mission played again (when its own start was never saved). */
	static List<String> defaults(int mission) {
		List<String> list = new ArrayList<>();

		if (mission >= 1) {
			list.add("droid.m31");
		}

		if (mission >= 2) {
			list.add("item.stick");
			list.add("know.drop");
		}

		if (mission >= 3) {
			list.addAll(List.of("armory.open", "wear.armour", "item.card", "choice.refuse_hale"));
		}

		if (mission >= 4) {
			list.addAll(List.of("m4.passed", "know.download"));
		}

		if (mission >= 5) {
			list.addAll(List.of("droid.r2v7", "hatch.f", "kestrel.free", "know.hale_trap"));
		}

		return list;
	}

	/** The reward for the whole chapter: Ilyra's saber (given when one leaves the story after its end). */
	public static ItemStack reward() {
		ItemStack stack = new ItemStack(Items.NETHERITE_SWORD);
		new SaberVariant(SaberVariant.SENTINEL, SaberVariant.GREEN, SaberVariant.SORESU).applyTo(stack);
		stack.set(DataComponents.CUSTOM_NAME, Component.translatable("sabercraft.story.item.ilyra_saber"));
		return stack;
	}

	/** Who speaks over the comlink after a capture, and what. */
	static String captureSpeaker(StorySession s) {
		int m = s.mission;
		return m == 0 ? "mira" : m <= 3 ? "kestrel" : "dax";
	}

	static String captureLine(StorySession s) {
		return "sabercraft.story.radio.caught." + s.mission().id();
	}

	// --- People ------------------------------------------------------------------------------------

	@Override
	public List<Figure> figures(StorySession s) {
		List<Figure> list = new ArrayList<>();
		int m = s.mission;
		String step = s.step().id();
		falcon(s, list, m, step);
		crew(s, list, m, step);
		people(s, list, m, step);
		// Phase 21: the search patrols left by alarms of this mission.
		Stealth.patrols(s, list);
		return list;
	}

	/** The Falcon's people: Mira, Dax at the controls, Rorrak at the drive; at the end everyone in the lounge. */
	private static void falcon(StorySession s, List<Figure> list, int m, String step) {
		if (s.has(CHAPTER_DONE)) {
			// Phase 22: after the chapter, on the way to the fleet: Dax and Kestrel fly (sitting in
			// the pilot's and the co-pilot's seats), Mira and Tamsin in the lounge, Rorrak at his
			// drive.
			list.add(person("dax", "dax", Crew.at(Crew.SMUGGLER, Crew.SIT, 42.5, FF, 21.7, 180.0F), F));
			list.add(person("kestrel", "kestrel", Crew.at(Crew.REBEL, Crew.SIT, 45.5, FF, 21.7, 180.0F), F));
			list.add(person("mira", "mira", Crew.at(Crew.REBEL, Crew.TALK, 19.2, FF, 24.8, -35.0F), F));
			list.add(person("rorrak", "rorrak",
					Crew.at(Crew.WOOKIEE, Crew.WORK, FalconInterior.DRIVE_X - FalconInterior.DRIVE_HOUSING - 0.85, FF, FalconInterior.DRIVE_Z, 270.0F), F));

			if (s.has("opt.prisoner")) {
				list.add(person("prisoner", "prisoner", Crew.at(Crew.OFFICER, Crew.STAND, 23.2, FF, 33.6, 150.0F), F));
			}

			return;
		}

		if ("m6.epilogue".equals(step)) {
			list.add(person("kestrel", "kestrel", Crew.at(Crew.REBEL, Crew.TALK, 21.0, FF, 26.2, 0.0F), F));
			list.add(person("mira", "mira", Crew.at(Crew.REBEL, Crew.TALK, 19.2, FF, 24.8, -35.0F), F));
			list.add(person("dax", "dax", Crew.at(Crew.SMUGGLER, Crew.STAND, 23.8, FF, 26.4, 120.0F), F));
			list.add(person("rorrak", "rorrak", Crew.at(Crew.WOOKIEE, Crew.STAND, 24.6, FF, 30.0, 100.0F), F));

			if (s.has("opt.prisoner")) {
				list.add(person("prisoner", "prisoner", Crew.at(Crew.OFFICER, Crew.STAND, 23.2, FF, 33.6, 150.0F), F));
			}

			return;
		}

		list.add(person("mira", "mira", Crew.at(Crew.REBEL, Crew.TALK, 19.2, FF, 24.8, -35.0F), F));
		list.add(person("dax", "dax", Crew.at(Crew.SMUGGLER, Crew.SIT, 42.5, FF, 21.7, 180.0F), F));
		list.add(person("rorrak", "rorrak",
				Crew.at(Crew.WOOKIEE, Crew.WORK, FalconInterior.DRIVE_X - FalconInterior.DRIVE_HOUSING - 0.85, FF, FalconInterior.DRIVE_Z, 270.0F), F));

		if ("m6.jump".equals(step)) {
			list.add(person("kestrel", "kestrel", Crew.at(Crew.REBEL, Crew.SIT, 45.5, FF, 21.7, 180.0F), F));
		}
	}

	/** The Star Destroyer's crew (the plan's), as the story needs them now. */
	private static void crew(StorySession s, List<Figure> list, int m, String step) {
		boolean fight = s.has("saber.taken") && m == 5;

		for (int i = 0; i < DestroyerInterior.CREW.size(); i++) {
			Crew c = DestroyerInterior.CREW.get(i);

			if ("spy".equals(c.role())) {
				// Kestrel is a person of the story.
				continue;
			}

			boolean deck = c.y() < 100.0;
			boolean reactor = "reactor".equals(c.role());
			boolean bottom = reactor && c.y() < H - 1;
			Crew routine = c;

			if (fight && deck) {
				// The fight clears the decks: the crew ran, the troops come as fighters (Finale).
				continue;
			}

			if (reactor && Crew.TROOPER.equals(c.kind()) && !bottom) {
				// The guard at the gallery: in Mission 4 one of the story (one speaks to him), gone in 6.
				if ("m4.checkpoint".equals(step) || m == 5) {
					continue;
				}
			} else if (bottom && Crew.TECH.equals(c.kind())) {
				// The technician at the main console: in Mission 4 one of the story.
				if (m == 3 && ("m4.tech".equals(step) || "m4.download".equals(step)) || m == 4 && "m5.escape".equals(step) && !s.has("gone.tech")) {
					continue;
				}
			} else if (bottom && Crew.OFFICER_OLIVE.equals(c.kind())) {
				if ("m4.download".equals(step)) {
					// Walks the south of the floor, back and forth (Phase 23: the lab's platform,
					// along its edge over the open shaft).
					routine = Crew.patrol(Crew.OFFICER_OLIVE, 0.7, 0.0, new Vec3(38.5, R, 33.8), new Vec3(42.5, R, 33.6), new Vec3(47.0, R, 34.0),
							new Vec3(42.5, R, 33.6));
				}
			} else if (bottom && Crew.TROOPER.equals(c.kind())) {
				// The guard by the lift went searching after the download (Phase 20: one sees him go,
				// to the console first; later in Mission 5 he searches elsewhere).
				if (m >= 4 && !(m == 4 && "m5.escape".equals(step))) {
					continue;
				}
			}

			Figure figure = new Figure(0, "crew:" + i, routine);
			figure.ship = D;
			figure.observer = true;
			figure.talks = false;
			// After the download the lights are low: the troops carry lamps.
			figure.lamp = m == 4 && Crew.TROOPER.equals(c.kind());
			list.add(figure);
		}
	}

	/** The people of the story aboard the Star Destroyer. */
	private static void people(StorySession s, List<Figure> list, int m, String step) {
		// Kestrel: in the hidden room (Missions 2 to 4, and 6), in cell 1 (Mission 5).
		if (m >= 1 && m <= 3 || m == 5 && ("m6.plan".equals(step) || "m6.reactor".equals(step))) {
			list.add(person("kestrel", "kestrel", Crew.at(Crew.REBEL, Crew.SIT, 15.0, H, -5.5, 0.0F), D));
		} else if (m == 4 && ("m5.cell".equals(step) || "m5.r2".equals(step) || "m5.cell1".equals(step))) {
			list.add(person("kestrel", "kestrel", Crew.at(Crew.REBEL, Crew.STAND, 27.0, H, 0.0, 0.0F), D));
		} else if ("m5.back".equals(step)) {
			list.add(person("kestrel", "kestrel", Crew.at(Crew.REBEL, Crew.STAND, 26.3, H, 6.5, 270.0F), D));
		}

		switch (m) {
			case 0 -> {
				if ("m1.droid".equals(step)) {
					Figure mouse = person("m31", "m31", Crew.at("droid_mouse", Crew.STAND, 2.6, H, -6.0, 90.0F), D);
					list.add(mouse);
				}

				if ("m1.droid".equals(step)) {
					// A trooper with a lamp walks round the machinery: the maintenance room is dark.
					Figure trooper = guard("maint", Crew.patrol(Crew.TROOPER, 0.9, 0.0, new Vec3(3.5, H, -3.5), new Vec3(10.0, H, -3.5), new Vec3(10.0, H, 3.5),
							new Vec3(3.5, H, 3.5)));
					trooper.lamp = true;
					list.add(trooper);
				} else if ("m1.distract".equals(step) || "m1.hatch".equals(step)) {
					// Phase 20: M-31 rattled loose behind the crates; the trooper heard it and now
					// stands guard in front of the hatch, facing the room (one lures him away).
					Figure trooper = guard("maint", Crew.at(Crew.TROOPER, Crew.STAND, HATCH_POST.x, H, HATCH_POST.z, 0.0F));
					trooper.lamp = true;
					list.add(trooper);
				}
			}
			case 2 -> {
				// The quartermaster: at his desk, now and then to the racks by the door.
				Crew walk = Crew.patrol(Crew.OFFICER_OLIVE, 0.6, 0.0, new Vec3(48.0, H, -4.4), new Vec3(46.5, H, -4.4), new Vec3(46.0, H, -9.0),
						new Vec3(46.3, H, -12.4), new Vec3(46.0, H, -9.0), new Vec3(46.5, H, -4.4));
				// Phase 20: called away (a page from the security desk, or Hale sends him), he sees to
				// the freight at the hangar's crates for the rest of the mission.
				Figure quartermaster = guard("quartermaster", s.has("qm.away") ? QM_AWAY : walk);
				quartermaster.speaker = "quartermaster";
				quartermaster.talks = true;
				list.add(quartermaster);

				if ("m3.hale".equals(step)) {
					// Phase 23: Hale waits in the corridor outside until the armory is empty, then
					// comes in himself (through the door).
					Figure hale = person("hale", "hale", Crew.at(Crew.OFFICER_BLACK, Crew.STAND, HALE_WAITS.x, H, HALE_WAITS.z, 210.0F), D);
					hale.talks = false;
					// He knows whom he comes to see: on his way in he does not stop at the sight of them.
					hale.blind = true;
					list.add(hale);
				}
			}
			case 3 -> {
				if ("m4.checkpoint".equals(step)) {
					Figure checkpoint = guard("checkpoint", Crew.at(Crew.TROOPER, Crew.STAND, DestroyerInterior.CX + 2.6, H, DestroyerInterior.LOBBY_Z0 + 1.5, 90.0F));
					checkpoint.speaker = "guard";
					checkpoint.talks = true;
					list.add(checkpoint);
				}

				if (("m4.tech".equals(step) || "m4.download".equals(step)) && !s.has("gone.tech")) {
					list.add(tech(s, step));
				}
			}
			case 4 -> {
				// Phase 20: the technician stays on the reactor floor (he no longer vanishes when the
				// download is done), at the console, the caf dispenser or the wall comm.
				if ("m5.escape".equals(step) && !s.has("gone.tech")) {
					list.add(tech(s, step));
				}

				// After the download: lamps in the dark lobby.
				Figure lobby = guard("m5_lobby", Crew.patrol(Crew.TROOPER, 0.8, 0.0, new Vec3(37.5, H, -1.0), new Vec3(42.0, H, -1.0), new Vec3(42.0, H, 4.6),
						new Vec3(37.5, H, 4.6)));
				lobby.lamp = true;
				list.add(lobby);

				if ("m5.cell".equals(step) && s.has("trust.hale") && !s.has("m5.trapped")) {
					// The trap: Hale waits in the detention block with two troopers (Phase 20: they
					// leave once the player is locked in).
					list.add(person("hale", "hale", Crew.at(Crew.OFFICER_BLACK, Crew.STAND, 33.5, H, 4.2, 270.0F), D));
					list.add(guard("trap1", Crew.at(Crew.TROOPER, Crew.STAND, 34.6, H, 3.2, 270.0F)));
				}

				if (step.equals("m5.cell") || step.equals("m5.r2") || step.equals("m5.cell1") || step.equals("m5.back")) {
					// The detention block: Phase 20: one guard, at the entrance, watching the door
					// (his back to the cells: who would come out of them?). The officer went with Hale.
					list.add(guard("det_guard", Crew.at(Crew.TROOPER, Crew.STAND, 34.7, H, 6.7, 270.0F)));

					if (!s.has("droid.r2v7")) {
						Figure r2 = person("r2v7", "r2v7", Crew.at("droid_r2", Crew.STAND, 27.5, H + 0.2, 9.45, 180.0F), D);
						list.add(r2);
					}

					if (!s.has("gone.prisoner")) {
						list.add(person("prisoner", "prisoner", Crew.at(Crew.OFFICER, Crew.STAND, 34.9, H, -0.2, 0.0F), D));
					}
				}
			}
			case 5 -> {
				// Phase 23: once he hunts the player (seen in the reactor) he does not vanish as they
				// go on into the lab: he comes after them, and when the saber is taken he fights.
				Figure guard = s.figure("lab_guard");
				boolean hunts = guard != null && !guard.hostile && guard.mode != Figure.ROUTINE && guard.mode != Figure.DOWN
						&& (step.equals("m6.lab") || step.equals("m6.saber"));

				if (!s.has("saber.taken") && (step.equals("m6.reactor") || step.equals("m6.door") || hunts)) {
					list.add(guard("lab_guard", Crew.at(Crew.TROOPER, Crew.STAND, 44.5, R, 35.5, 180.0F)));
				}

				hostiles(s, list, step);

				// Phase 21: he waits with his back to the player (the film turns him round); after the duel he is gone.
				if (step.equals("m6.duel") && !s.has("gone.shadow")) {
					Figure shadow = person("shadow", "shadow", Crew.at("shadow", Crew.STAND, 40.5, H, -28.5, 180.0F), D);
					shadow.talks = false;
					shadow.saber = true;
					shadow.health = 1.0F;
					list.add(shadow);
				}
			}
			default -> {
			}
		}
	}

	/**
	 * Mission 4 (and the start of 5): the technician of the main console. After a bluff he steps
	 * back from it and watches (so that the console is free, and he sees what one does there); with
	 * the password he takes his caf by the lift; after raising the alarm he stays by the wall comm.
	 */
	private static Figure tech(StorySession s, String step) {
		Crew routine;

		if (s.has("tech.break")) {
			routine = Crew.at(Crew.TECH, Crew.STAND, CAF.x, R, CAF.z, 160.0F);
		} else if (s.has("tech.called")) {
			routine = Crew.at(Crew.TECH, Crew.STAND, REACTOR_COMM.x, R, REACTOR_COMM.z, 60.0F);
		} else if (s.has("tech.watch") && !"m4.tech".equals(step)) {
			routine = Crew.at(Crew.TECH, Crew.STAND, 50.0, R, 21.8, 317.0F);
		} else {
			routine = Crew.at(Crew.TECH, Crew.WORK, 51.05, R, 24.5, 270.0F);
		}

		Figure tech = guard("tech", routine);
		tech.speaker = "tech";
		tech.talks = true;
		return tech;
	}

	/** The fighters of the finale, by section (fewer with the coolant sabotaged or the second armour given away). */
	private static void hostiles(StorySession s, List<Figure> list, String step) {
		switch (step) {
			case "m6.escape1" -> {
				// (Phase 23: by the console, not at the end of the bridge to the lift.)
				list.add(fighter("h1", 40.0, R, 34.4, 180.0F));
				list.add(fighter("h2", 48.5, R, 28.5, 120.0F));
				list.add(fighter("lab_guard", 44.5, R, 35.5, 0.0F));

				if (!s.has("opt.coolant")) {
					list.add(fighter("h3", 31.0, R, 21.0, 300.0F));
				}
			}
			case "m6.escape2" -> {
				list.add(fighter("h4", 48.0, H, 9.0, 0.0F));
				list.add(fighter("h5", 40.5, H, 1.0, 0.0F));

				if (!s.has("opt.armour2")) {
					list.add(fighter("h6", 40.5, H, -9.0, 0.0F));
				}
			}
			case "m6.falcon" -> {
				list.add(fighter("h7", 37.5, H, -18.0, 180.0F));
				list.add(fighter("h8", 43.5, H, -18.0, 180.0F));
			}
			default -> {
			}
		}
	}

	private static Figure person(String key, String speaker, Crew crew, String ship) {
		Figure figure = new Figure(0, key, crew);
		figure.ship = ship;
		figure.speaker = speaker;
		figure.observer = false;
		figure.talks = true;
		return figure;
	}

	private static Figure guard(String key, Crew crew) {
		Figure figure = new Figure(0, key, crew);
		figure.ship = D;
		figure.observer = true;
		figure.talks = false;
		return figure;
	}

	private static Figure fighter(String key, double x, double y, double z, float yaw) {
		Figure figure = new Figure(0, key, Crew.at(Crew.TROOPER, Crew.STAND, x, y, z, yaw));
		figure.ship = D;
		figure.hostile = true;
		figure.talks = false;
		figure.health = 1.0F;
		return figure;
	}

	// --- Things to use -----------------------------------------------------------------------------

	@Override
	public List<Mission.Point> points(StorySession s) {
		List<Mission.Point> list = new ArrayList<>();
		int m = s.mission;
		String step = s.step().id();

		switch (m) {
			case 0 -> {
				if (step.equals("m1.droid") || step.equals("m1.distract") || step.equals("m1.hatch")) {
					list.add(point("sign", 8.6, H + 0.9, -8.1, 9.6, H + 1.8, -7.9, false));

					if (!s.has("opt.pad")) {
						list.add(point("pad", 11.3, H + 0.75, 0.2, 12.0, H + 1.1, 0.9, false));
					}
				}
			}
			case 1 -> {
				if (step.equals("m2.drop")) {
					// Phase 20: three containers along the hangar's south wall; the freight list on
					// Kestrel's terminal says which one the contact used.
					for (String[] crate : CRATES) {
						double x = Double.parseDouble(crate[1]);
						list.add(point("crate_" + crate[0], x, H, -64.0, x + 2.0, H + 1.0, -63.0, false));
					}
				}
			}
			case 2 -> {
				if (step.equals("m3.console")) {
					list.add(point("security", 43.1, H, -9.5, 44.0, H + 1.2, -7.5, false));
				}

				if (step.equals("m3.armour")) {
					list.add(point("locker", 55.3, H, -9.0, 56.0, H + 2.0, -8.0, false));
				}

				if (s.has("wear.armour") && !s.has("opt.armour2")) {
					list.add(point("locker2", 55.3, H, -6.0, 56.0, H + 2.0, -5.0, false));
				}

				if (step.equals("m3.card")) {
					list.add(point("card", 49.0, H + 0.8, -6.0, 50.0, H + 1.1, -5.0, false));
				}
			}
			case 3 -> {
				if (step.equals("m4.download")) {
					list.add(point("console", 52.0, R, 24.0, 53.0, R + 1.2, 25.0, false));
				}

				if (s.has("m4.passed") && !s.has("opt.coolant") && !step.equals("m4.checkpoint")) {
					list.add(point("coolant", 48.8, R + 0.5, 32.8, 49.6, R + 1.5, 33.6, false));
				}
			}
			case 4 -> {
				if (step.equals("m5.recording")) {
					list.add(point("recorder", 22.1, H + 1.0, -4.9, 22.9, H + 1.4, -4.1, false));
				}

				if (step.equals("m5.cell1") && !s.has("cell1.open")) {
					list.add(point("cell1", 26.0, H, 2.0, 29.0, H + 3.0, 3.0, true));
				}

				if (s.has("droid.r2v7") && !s.has("cell3.open")) {
					list.add(point("cell3", 34.0, H, 2.0, 36.0, H + 3.0, 3.0, true));
				}
			}
			case 5 -> {
				if (step.equals("m6.door") && !s.has("lab.open")) {
					list.add(point("lab_door", 43.0, R, 38.5, 46.0, R + 3.0, 39.5, true));
				}

				if (step.equals("m6.lab")) {
					list.add(point("holo", 39.0, R, 46.0, 40.0, R + 1.0, 47.0, false));
				}

				if (step.equals("m6.saber")) {
					list.add(point("saber", 44.0, R, 50.0, 45.0, R + 2.0, 51.0, false));
				}
			}
			default -> {
			}
		}

		// Kestrel's terminal in the hidden room (from Mission 2 on).
		if (m >= 1) {
			list.add(point("terminal", 17.0, H + 0.8, -1.7, 19.0, H + 1.5, -1.0, false));
		}

		return list;
	}

	private static Mission.Point point(String id, double x0, double y0, double z0, double x1, double y1, double z1, boolean hack) {
		return new Mission.Point(id, D, new AABB(x0, y0, z0, x1, y1, z1), hack);
	}

	@Override
	public void use(StorySession s, Mission.Point point) {
		switch (point.id()) {
			case "pad" -> s.talk("m1_pad", null);
			case "sign" -> s.talk("m1_sign", null);
			case "terminal" -> s.talk("terminal", null);
			case "crate_k7" -> unwatched(s, "m2_container");
			case "crate_c3", "crate_f9" -> {
				if (watched(s)) {
					s.hint("sabercraft.story.hint.watched");
					Story.cue(s, Story.CUE_SOUND, "", "story.suspicious", 0);
				} else {
					s.talk("m2_" + point.id(), null);
				}
			}
			case "security" -> unwatched(s, "m3_console");
			case "locker" -> unwatched(s, "m3_locker");
			case "locker2" -> unwatched(s, "m3_locker2");
			case "card" -> unwatched(s, "m3_card");
			case "coolant" -> unwatched(s, "m4_coolant");
			case "console" -> s.talk("m4_console", null);
			case "recorder" -> s.talk("m5_recording", null);
			case "holo" -> s.talk("m6_ilyra", null);
			case "saber" -> s.talk("m6_saber", null);
			default -> {
			}
		}
	}

	/** Opens a dialog only while nobody watches the player (otherwise: not now). */
	private static void unwatched(StorySession s, String dialog) {
		if (watched(s)) {
			s.hint("sabercraft.story.hint.watched");
			Story.cue(s, Story.CUE_SOUND, "", "story.suspicious", 0);
			return;
		}

		s.talk(dialog, null);
	}

	/**
	 * Whether anyone watching for intruders looks at the player now (close by, in front of them,
	 * nothing in between), or is already suspicious: then one cannot do anything secret.
	 */
	static boolean watched(StorySession s) {
		ServerLevel level = interiors(s);
		String ship = s.here;

		if (level == null || ship == null || s.grace > 0) {
			// Just after a capture nobody looks yet (as in Stealth).
			return false;
		}

		Vec3 corner = Vec3.atLowerCornerOf(s.origin(ship));
		Vec3 me = s.local(ship);

		for (Figure figure : s.figures) {
			if (!figure.observer || !ship.equals(figure.ship) || figure.mode == Figure.DOWN || figure.mode == Figure.HELD) {
				continue;
			}

			if (figure.stage >= Figure.SUSPICIOUS) {
				return true;
			}

			Vec3 eye = figure.eye();
			Vec3 chest = me.add(0.0, 0.9, 0.0);
			Vec3 to = chest.subtract(eye);
			double distance = to.length();
			boolean dark = s.chapter.dark(s, ship, me.x, me.y, me.z) && !Stealth.lit(s, ship, me, level, corner);

			if (distance > (dark ? Stealth.DARK_RANGE : 7.0)) {
				continue;
			}

			Vec3 flat = new Vec3(to.x, 0.0, to.z);
			double angle = flat.lengthSqr() < 1.0E-4 ? 0.0 : Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, flat.normalize().dot(figure.look())))));

			if (angle > (distance < 3.0 ? Stealth.SIDE : Stealth.FOV)) {
				continue;
			}

			if (Stealth.clear(level, corner.add(eye), corner.add(chest))) {
				return true;
			}
		}

		return false;
	}

	// --- Speaking to people ------------------------------------------------------------------------

	/**
	 * Phase 22: set when the chapter is over (the epilogue spoken). From then on the people of
	 * the Falcon know what happened: their own lines, no objective, no marker. A new run or a
	 * mission played again starts without it (their flags are those of that mission's start).
	 */
	public static final String CHAPTER_DONE = "chapter.done";

	/** Phase 22: what the Falcon's people say after the chapter (null: they have nothing to say). */
	static @Nullable String afterwards(StorySession s, Figure figure) {
		return switch (figure.key) {
			case "dax" -> "after_dax";
			case "kestrel" -> "after_kestrel";
			case "mira" -> "after_mira";
			case "rorrak" -> "after_rorrak";
			case "prisoner" -> "after_tamsin";
			default -> null;
		};
	}

	@Override
	public boolean talk(StorySession s, Figure figure) {
		String step = s.step().id();

		if (s.has(CHAPTER_DONE)) {
			String after = afterwards(s, figure);

			if (after == null) {
				return false;
			}

			s.talk(after, figure);
			return s.dialog != null;
		}

		String dialog = switch (figure.key) {
			case "mira" -> step.equals("m1.briefing") ? "m1_briefing" : "m1_mira_wait";
			case "dax" -> step.equals("m1.cockpit") ? "m1_dax" : step.equals("m6.jump") ? "falcon_crew" : "dax_idle";
			case "rorrak" -> "rorrak_idle";
			case "m31" -> step.equals("m1.droid") ? "m1_m31" : null;
			case "r2v7" -> step.equals("m5.r2") ? "m5_r2" : null;
			case "kestrel" -> switch (step) {
				case "m2.kestrel" -> "m2_kestrel";
				case "m2.return" -> s.has("item.stick") ? "m2_return" : "kestrel_idle";
				case "m3.plan" -> "m3_kestrel";
				case "m4.brief" -> "m4_kestrel";
				case "m5.cell1" -> s.has("cell1.open") ? "m5_kestrel" : null;
				case "m6.plan" -> "m6_plan";
				default -> "kestrel_idle";
			};
			case "checkpoint" -> s.has("m4.passed") ? "checkpoint_closed" : "m4_checkpoint";
			case "quartermaster" -> s.disguised ? "quartermaster" : null;
			case "tech" -> step.equals("m4.tech") ? "m4_tech" : "tech_idle";
			case "prisoner" -> s.has("cell3.open") && !s.has("opt.prisoner") ? "m5_prisoner" : null;
			default -> null;
		};

		if (dialog == null) {
			return false;
		}

		if (step.equals("m6.jump") && figure.key.equals("dax")) {
			s.set("tmp.jump_go");
		}

		s.talk(dialog, figure);
		return s.dialog != null;
	}

	// --- Droids ------------------------------------------------------------------------------------

	@Override
	public @Nullable String droid(StorySession s, String droid) {
		ServerPlayer player = s.player();
		ServerLevel level = interiors(s);

		if (player == null || level == null || s.here == null) {
			return null;
		}

		String step = s.step().id();
		Vec3 me = s.local(s.here);

		if (droid.equals("mouse")) {
			// Special jobs first: the hatch bolted from the other side, the force field of the cell.
			if (step.equals("m5.route") && !s.has("trust.hale") && me.distanceTo(Vec3.atCenterOf(DestroyerInterior.HIDEOUT_HATCH)) < 4.5) {
				s.set("hatch.f");
				s.hint("sabercraft.story.hint.hatch_free");
				return "sabercraft.story.droid.m31_hatch";
			}

			if (step.equals("m5.cell") && s.has("m5.trapped") && !s.has("cell2.open")) {
				// Phase 20: it waits outside by the bench (never in the cell), however often one was
				// caught: the checkpoint in the cell has it there again.
				s.set("cell2.open");
				field(s, 1, true);
				Story.cue(s, Story.CUE_SOUND, "", "story.field_off", 0);
				com.phiro.sabercraft.droid.DroidEntity mouse = com.phiro.sabercraft.droid.Droids.droid(player, com.phiro.sabercraft.droid.DroidType.MOUSE);

				if (mouse != null) {
					mouse.setStaying(false);
				}

				return "sabercraft.story.droid.m31_field";
			}

			// Phase 21: M-31 drives to where the player looks and rattles there; only those who hear
			// it react (Companions, Stealth.noise).
			return Companions.lure(s, player, level);
		}

		if (droid.equals("astromech")) {
			// A lock within reach: the astromech opens it (it takes a moment, and it beeps).
			// Phase 22: the one the player looks at, never simply the last one in reach (in the
			// detention block Kestrel's cell and the prisoner's both were, and the key opened
			// the other one first, so it seemed as if a second door opened by itself).
			Mission.Point lock = aimedLock(s, player, me);

			if (lock == null && lockNear(s, me)) {
				return "sabercraft.story.droid.r2_aim";
			}

			if (lock != null) {
				if (s.timer("hack")) {
					return "sabercraft.story.droid.r2_busy";
				}

				s.timer("hack", HACK);
				s.set("tmp.hack." + lock.id());
				Story.cue(s, Story.CUE_HINT, "progress", "sabercraft.story.hint.hacking", 0);

				// Someone close by hears it beep.
				for (Figure figure : s.figures) {
					if (figure.observer && s.here.equals(figure.ship) && figure.position().distanceTo(lock.box().getCenter()) < 7.0
							&& (figure.mode == Figure.ROUTINE || figure.mode == Figure.RETURN)) {
						figure.mode = Figure.LOOK;
						figure.target = lock.box().getCenter();
						figure.timer = 80;
						figure.suspicion = Math.max(figure.suspicion, Stealth.SUSPICIOUS);
					}
				}

				return "sabercraft.story.droid.r2_hack";
			}

			// Otherwise a scan: who is about (shown a few seconds).
			List<Vec3> seen = new ArrayList<>();
			Vec3 corner = Vec3.atLowerCornerOf(s.origin(s.here));

			for (Figure figure : s.figures) {
				if ((figure.observer || figure.hostile) && s.here.equals(figure.ship) && figure.mode != Figure.DOWN && figure.position().distanceTo(me) < 28.0) {
					seen.add(figure.position().add(corner));
				}
			}

			s.scanned = List.copyOf(seen);
			s.scanUntil = s.time() + 120;
			Story.cue(s, Story.CUE_SCAN, "", "", 120, seen);
			return "sabercraft.story.droid.r2_scan";
		}

		return null;
	}

	/**
	 * Phase 22: the lock (a point only a droid opens) the player looks at: the view from the eyes
	 * passes through its box within 6 blocks; if it passes none, a lock whose middle is within 12
	 * degrees of the view. The nearest such. {@code null} if none is looked at.
	 */
	static Mission.@Nullable Point aimedLock(StorySession s, ServerPlayer player, Vec3 me) {
		Vec3 corner = Vec3.atLowerCornerOf(s.origin(s.here));
		Vec3 eye = player.getEyePosition().subtract(corner);
		Vec3 look = player.getLookAngle();
		Vec3 far = eye.add(look.scale(6.0));
		Mission.Point best = null;
		double bestDistance = Double.MAX_VALUE;
		Mission.Point near = null;
		double nearAngle = Math.cos(Math.toRadians(12.0));

		for (Mission.Point point : lockPoints(s)) {
			if (!point.hack() || !point.ship().equals(s.here) || point.box().distanceToSqr(me.add(0.0, 1.0, 0.0)) >= 36.0) {
				continue;
			}

			AABB box = point.box();
			java.util.Optional<Vec3> hit = box.contains(eye) ? java.util.Optional.of(eye) : box.clip(eye, far);

			if (hit.isPresent()) {
				double distance = hit.get().distanceTo(eye);

				if (distance < bestDistance) {
					bestDistance = distance;
					best = point;
				}
			} else {
				Vec3 to = box.getCenter().subtract(eye).normalize();
				double cos = to.dot(look);

				if (cos > nearAngle) {
					nearAngle = cos;
					near = point;
				}
			}
		}

		return best != null ? best : near;
	}

	/** Phase 22: whether any lock is within the astromech's reach (then it asks to be shown which). */
	private static boolean lockNear(StorySession s, Vec3 me) {
		for (Mission.Point point : lockPoints(s)) {
			if (point.hack() && point.ship().equals(s.here) && point.box().distanceToSqr(me.add(0.0, 1.0, 0.0)) < 36.0) {
				return true;
			}
		}

		return false;
	}

	private static List<Mission.Point> lockPoints(StorySession s) {
		return s.chapter.points(s);
	}

	/** A lock the astromech worked on is open. */
	private static void hacked(StorySession s, String id) {
		switch (id) {
			case "cell1" -> {
				s.set("cell1.open");
				field(s, 0, true);
				s.hint("sabercraft.story.hint.cell_open");
			}
			case "cell3" -> {
				s.set("cell3.open");
				field(s, 2, true);
				s.hint("sabercraft.story.hint.cell_open");
			}
			case "lab_door" -> {
				s.set("lab.open");
				done(s, "m6.door");
			}
			default -> {
			}
		}

		Story.cue(s, Story.CUE_SOUND, "", "story.field_off", 0);
		s.dirty = true;
	}

	// --- Steps beginning ---------------------------------------------------------------------------

	@Override
	public void begin(StorySession s, Mission.Step step, boolean again) {
		int m = s.mission;
		String id = step.id();

		// Phase 20: the journal tells what the droids can do (also for files from before).
		if (s.has("droid.m31") && !s.journal.contains("droid_m31")) {
			s.journal.add("droid_m31");
		}

		if (s.has("droid.r2v7") && !s.journal.contains("droid_r2v7")) {
			s.journal.add("droid_r2v7");
		}

		// Phase 21: with Ilyra's saber comes the Focus (also for files from before).
		if (s.has("saber.taken") && !s.has("know.focus")) {
			learnFocus(s, false);
		}

		// The ship's state at this point of the story.
		if (m >= 4) {
			s.set("ship.alert");
			s.set("armour.burned");
			s.alert = true;
			s.burned = true;
		}

		if (id.equals("m6.jump") || id.equals("m6.epilogue")) {
			// Back in the Falcon: out of the armour.
			s.unset("wear.armour");
			s.disguised = false;
		}

		ship(s);
		kit(s);

		// Asked again at every checkpoint (a client that came back has to hear it again).
		s.music = "-";
		music(s, m, id);

		switch (id) {
			case "m1.ramp" -> {
				if (!again) {
					s.talk("m1_rorrak", null);
				}
			}
			case "m1.distract" -> {
				Figure guard = s.figure("maint");

				if (!again) {
					// He walks over from his round to the hatch; Kestrel explains over the comlink.
					if (guard != null && guard.mode == Figure.ROUTINE) {
						guard.mode = Figure.RETURN;
					}

					s.radio("mira", "sabercraft.story.radio.m1_distract");
				}

				s.timer("droid_hint", again ? 40 : 200);
			}
			case "m4.download" -> {
				// After a bluff the technician walks from the console to where he watches it.
				Figure tech = s.figure("tech");

				if (tech != null && s.has("tech.watch") && tech.mode == Figure.ROUTINE) {
					tech.mode = Figure.RETURN;
				}
			}
			case "m6.plan" -> {
				// Kestrel waited at the crawl way's far hatch and came through behind the player: she
				// climbs out of the hideout's hatch and goes to her cot (she was left standing by the
				// detention block once, and the player had to go back for her).
				Figure kestrel = s.figure("kestrel");

				if (kestrel != null && !HIDDEN.contains(kestrel.position().add(0.0, 0.5, 0.0))) {
					kestrel.x = 22.3;
					kestrel.y = H;
					kestrel.z = -3.5;
					kestrel.mode = Figure.RETURN;
					kestrel.target = null;
					kestrel.way = List.of();
					kestrel.wayIndex = 0;
				}
			}
			case "m3.hale" -> {
				// Phase 23: the armory is to be empty when Hale comes: if the quartermaster is still
				// here, the security desk pages him to the hangar now (Hale's doing); Hale waits
				// outside until he is well away (haleScene), then comes in alone.
				if (!s.has("qm.away")) {
					pageQuartermaster(s);
				}

				Figure hale = s.figure("hale");

				if (hale != null) {
					hale.mode = Figure.HELD;
					hale.target = null;
				}
			}
			case "m5.escape" -> {
				// Phase 20: the alarm is on whenever one is here (also after a capture). The copy set
				// off the reactor's safety shutdown: emergency lights from now until the saber.
				if (!again) {
					s.hear("intercom", "sabercraft.story.shout.emergency");
					s.radio("vorr", "sabercraft.story.radio.vorr_alert");
				}

				s.raiseAlarm(Stealth.SEARCH);

				// The guard by the lift runs over to the console (and searches round it).
				for (Figure figure : s.figures) {
					if (figure.key.startsWith("crew:") && Crew.TROOPER.equals(figure.kind()) && figure.y < H - 1 && figure.mode == Figure.ROUTINE) {
						figure.mode = Figure.INVESTIGATE;
						figure.target = new Vec3(50.2, R, 24.5);
						figure.timer = 60;
						figure.searches = 3;
						figure.suspicion = Math.max(figure.suspicion, Stealth.SUSPICIOUS);
					}
				}
			}
			case "m5.route" -> {
				if (!again) {
					if (s.has("trust.hale")) {
						s.radio("hale", "sabercraft.story.radio.hale_offer");
					} else {
						s.hint("sabercraft.story.hint.terminal");
					}
				}
			}
			case "m5.cell" -> {
				if (!s.has("trust.hale")) {
					// Without Hale's trap one comes in through the crawl way.
					s.complete();
				} else if (!s.has("m5.trapped")) {
					// Phase 20: the trap runs whenever one walks into it (the checkpoint after it is
					// in the cell, so a capture never skips it or leaves it half done).
					s.talk("m5_trap", s.figure("hale"));
				} else if (!s.has("cell2.open")) {
					// Back in the cell: the hint again after a few seconds.
					s.timer("cell_hint", CELL_HINT);
				}
			}
			case "m6.escape1", "m6.escape2", "m6.falcon" -> {
				s.fighting = true;
				Finale.arm(s);
				// Phase 23: who still hunted the player from before (a chase in the reactor, a
				// search) fights now too, at once.
				Finale.engageAll(s);

				if (id.equals("m6.escape1") && !again) {
					// Phase 20: the main lights back on: they want to see whom they shoot at.
					s.hear("intercom", "sabercraft.story.shout.lights_on");
				}

				if (id.equals("m6.escape1")) {
					// Phase 23: the bridge whole again (ship() put it back), nothing of its fall begun.
					s.unset("tmp.bridge_creak");
					s.unset("tmp.bridge_down");
				}
			}
			case "m6.duel" -> {
				s.fighting = true;
				s.unset("tmp.film");
				s.unset("tmp.duel_end");
				// Phase 23: the duel is between the two of them: troopers still about from the
				// escape (drawn in, or come as help) fall back.
				s.figures.removeIf(figure -> figure.engaged || figure.key.startsWith(Stealth.REINFORCEMENT));
				Figure shadow = s.figure("shadow");

				if (shadow != null) {
					shadow.mode = Figure.HELD;
					shadow.target = null;
					shadow.lit = false;
				}
			}
			case "m6.epilogue" -> {
				if (s.has(CHAPTER_DONE)) {
					// Phase 22: continuing after the chapter: one is aboard on the way to the fleet;
					// the epilogue is not spoken again and nobody leaves his seat to watch one.
					Story.place(s, F, FALCON_LOUNGE, 150.0F);
					return;
				}

				if (!again) {
					Story.cue(s, Story.CUE_FADE, "", "", 30);
				}

				Story.place(s, F, FALCON_LOUNGE, 150.0F);
				Vec3 me = FALCON_LOUNGE;

				for (Figure figure : s.figures) {
					if (F.equals(figure.ship)) {
						figure.mode = Figure.HELD;
						figure.target = me;
					}
				}

				s.talk("m6_epilogue", s.figure("kestrel"));
			}
			default -> {
			}
		}
	}

	/** The ship as the story has it now: the lights, the force fields of the cells. */
	private static void ship(StorySession s) {
		ServerLevel level = interiors(s);

		if (level == null) {
			return;
		}

		InteriorPlan plan = ShipInteriors.planAt(s.destroyer.getX() + 1.0, s.destroyer.getZ() + 1.0);

		if (plan != null) {
			int wanted = s.mission == 4 ? 3 : 0;

			if (InteriorSystems.lightStep(level, s.destroyer, plan) != wanted) {
				InteriorSystems.setLightStep(level, s.destroyer, plan, wanted);
			}
		}

		field(s, 0, s.has("cell1.open"));
		field(s, 1, s.has("cell2.open"));
		field(s, 2, s.has("cell3.open"));

		// Phase 23: the reactor's long bridge stands until it breaks behind the player in the
		// escape, and is down from then on (a checkpoint before that finds it whole again).
		boolean down = s.mission == 5 && BRIDGE_DOWN.contains(s.step().id());

		if (DestroyerInterior.collapseStands(level, s.destroyer) == down) {
			DestroyerInterior.setCollapse(level, s.destroyer, !down);
		}
	}

	/**
	 * Phase 23, the escape over the reactor: the long bridge from the lab's platform to the lift's
	 * groans under the fire as one runs over it (a crack, sparks, the view trembles) and breaks
	 * behind one once one is across, off it on the lift's platform: a snap, its pieces fall into the
	 * depth, whoever was on it falls with it; the chasers are left on the far side, cut off (they
	 * come to the broken edge and shoot from there). Never under the player.
	 */
	private static void bridge(StorySession s, ServerLevel level) {
		Vec3 corner = Vec3.atLowerCornerOf(s.destroyer);

		if (s.has("tmp.bridge_down")) {
			// Those who fell with it, down into the depth.
			for (Figure figure : List.copyOf(s.figures)) {
				if (figure.drop > 0.0) {
					figure.y -= figure.drop;
					figure.drop = Math.min(1.2, figure.drop + 0.06);

					if (figure.y < DestroyerInterior.PIT_FLOOR) {
						s.set("gone." + figure.key);
						s.figures.remove(figure);
					}
				}
			}

			return;
		}

		Vec3 me = s.local(D);

		if (!D.equals(s.here)) {
			return;
		}

		boolean on = DestroyerInterior.onCollapse(me.x, me.y, me.z);

		if (on && !s.has("tmp.bridge_creak")) {
			s.set("tmp.bridge_creak");
			Vec3 mid = corner.add(34.0, R - 0.3, 32.0);
			level.playSound(null, mid.x, mid.y, mid.z, ShipInteriors.sound("story.bridge_creak"), net.minecraft.sounds.SoundSource.BLOCKS, 1.3F, 1.0F);

			for (Vec3 at : List.of(new Vec3(33.0, R - 0.4, 29.0), new Vec3(37.0, R - 0.4, 33.0), new Vec3(35.0, R - 0.4, 33.0))) {
				Vec3 w = corner.add(at);
				level.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK, w.x, w.y, w.z, 16, 0.3, 0.05, 0.3, 0.25);
			}

			Story.cue(s, Story.CUE_DUEL, "creak", "", 40);
			s.hint("sabercraft.story.hint.bridge_creak");
			return;
		}

		DestroyerInterior.Gantry west = DestroyerInterior.GANTRIES.getFirst();

		if (!s.has("tmp.bridge_creak") || on || !west.on(me.x, me.y, me.z)) {
			return;
		}

		// Across: it goes.
		s.set("tmp.bridge_down");
		DestroyerInterior.setCollapse(level, s.destroyer, false);
		Vec3 snap = corner.add(34.0, R - 0.3, 29.5);
		Vec3 mid = corner.add(34.0, R - 0.3, 32.5);
		level.playSound(null, snap.x, snap.y, snap.z, ShipInteriors.sound("story.bridge_snap"), net.minecraft.sounds.SoundSource.BLOCKS, 1.6F, 1.0F);
		level.playSound(null, mid.x, mid.y - 2.0, mid.z, ShipInteriors.sound("story.bridge_fall"), net.minecraft.sounds.SoundSource.BLOCKS, 1.5F, 1.0F);

		for (DestroyerInterior.Gantry g : DestroyerInterior.COLLAPSE) {
			for (int x = g.x0(); x <= g.x1(); x++) {
				for (int z = g.z0(); z <= g.z1(); z++) {
					Vec3 w = corner.add(x + 0.5, g.y() - 0.4, z + 0.5);
					level.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK, w.x, w.y, w.z, 10, 0.4, 0.1, 0.4, 0.35);
					level.sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE, w.x, w.y - 0.3, w.z, 3, 0.3, 0.1, 0.3, 0.02);
				}
			}
		}

		for (Vec3 at : List.of(new Vec3(34.0, R - 0.5, 29.0), new Vec3(37.0, R - 0.5, 34.0))) {
			Vec3 w = corner.add(at);
			level.sendParticles(net.minecraft.core.particles.ParticleTypes.LAVA, w.x, w.y, w.z, 12, 0.4, 0.1, 0.4, 0.2);
			level.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION, w.x, w.y, w.z, 1, 0.0, 0.0, 0.0, 0.0);
		}

		Story.cue(s, Story.CUE_DUEL, "quake", "", 18);
		Figure shouter = null;

		for (Figure figure : s.figures) {
			if (!D.equals(figure.ship) || figure.mode == Figure.DOWN) {
				continue;
			}

			if (DestroyerInterior.onCollapse(figure.x, figure.y, figure.z)) {
				// On it: down with it.
				figure.mode = Figure.DOWN;
				figure.react(Figure.FALL, 40);
				figure.drop = 0.08;
				figure.speed = 0.0F;
			} else if (figure.hostile && shouter == null && figure.y < H - 1) {
				shouter = figure;
			}
		}

		if (shouter != null) {
			s.hear("trooper", "sabercraft.story.shout.bridge_gone");
		}

		s.radio("kestrel", "sabercraft.story.radio.bridge_down");
	}

	/** Phase 23: the steps after the reactor's bridge broke (it stays down). */
	static final java.util.Set<String> BRIDGE_DOWN = java.util.Set.of("m6.escape2", "m6.duel", "m6.falcon", "m6.jump", "m6.epilogue");

	/** Switches a cell's force field off (open) or on. */
	static void field(StorySession s, int cell, boolean open) {
		ServerLevel level = interiors(s);

		if (level == null) {
			return;
		}

		int[] c = DestroyerInterior.CELLS[cell];

		for (int x = c[0]; x <= c[1]; x++) {
			for (int y = H; y <= H + 2; y++) {
				BlockPos pos = s.destroyer.offset(x, y, DestroyerInterior.CELL_FRONT);
				boolean is = level.getBlockState(pos).is(Blocks.BARRIER);

				if (open == is) {
					level.setBlock(pos, open ? Blocks.AIR.defaultBlockState() : Blocks.BARRIER.defaultBlockState(), 3);
				}
			}
		}
	}

	/** What the player carries and wears: the saber once taken, the armour while worn. */
	private static void kit(StorySession s) {
		ServerPlayer player = s.player();

		if (player == null) {
			return;
		}

		player.getInventory().clearContent();

		if (s.has("saber.taken")) {
			player.getInventory().setItem(player.getInventory().getSelectedSlot(), reward());
		}

		player.containerMenu.broadcastChanges();
		// Phase 23: the plain skin until the disguise (no chosen character in the story).
		OutfitSync.override(player, s.disguised ? Outfit.STORMTROOPER.id() : Outfit.PLAIN);
	}

	/**
	 * The music the story asks for (the client plays it instead of its own choice): sneaking on the
	 * Star Destroyer, calm in the Falcon and in the hideout, the alarm piece while escaping, the
	 * fight to the hangar, the duel, the jump. The client lays the alarm piece over a calm one
	 * while an alarm is on.
	 */
	static void music(StorySession s, int m, String step) {
		String track = switch (step) {
			case "m1.briefing", "m1.cockpit", "m1.ramp", "m6.epilogue" -> "music.story_hideout";
			case "m5.escape" -> "music.story_alarm";
			case "m6.escape1", "m6.escape2", "m6.falcon" -> "music.combat_duel";
			// Silence while she speaks; the duel's music when the blades are lit.
			case "m6.duel" -> s.has("tmp.duel") || s.has("tmp.film") && !s.has("tmp.duel_end") ? "music.story_duel" : "";
			case "m6.jump" -> "music.event_rise";
			default -> D.equals(s.here) && s.inside(D, HIDDEN) ? "music.story_hideout" : "music.story_stealth";
		};

		if (!track.equals(s.music)) {
			s.music = track;
			Story.cue(s, Story.CUE_MUSIC, "", track, 0);
		}
	}

	// --- Every tick --------------------------------------------------------------------------------

	@Override
	public void tick(StorySession s) {
		ServerPlayer player = s.player();
		ServerLevel level = interiors(s);

		if (player == null || level == null) {
			return;
		}

		// A step said in a dialog: done once the dialog is closed.
		if (s.pending != null && s.dialog == null) {
			if (s.at(s.pending)) {
				s.complete();
			}

			s.pending = null;
			return;
		}

		// The astromech finished a lock (while it works, the bar fills).
		if (!s.timer("hack")) {
			for (String flag : List.copyOf(s.flags)) {
				if (flag.startsWith("tmp.hack.")) {
					s.unset(flag);
					Story.cue(s, Story.CUE_HINT, "progress", "sabercraft.story.hint.hacking", 100);
					hacked(s, flag.substring("tmp.hack.".length()));
				}
			}
		} else if (s.stepTicks % 10 == 0) {
			int left = s.timers.getOrDefault("hack", 0);
			Story.cue(s, Story.CUE_HINT, "progress", "sabercraft.story.hint.hacking", Math.max(0, HACK - left) * 100 / HACK);
		}

		if (s.has("tmp.qm_leaving")) {
			Figure quartermaster = s.figure("quartermaster");

			if (quartermaster == null || quartermaster.mode != Figure.HELD) {
				s.unset("tmp.qm_leaving");
			} else if (s.dialog == null && --quartermaster.timer <= 0) {
				s.unset("tmp.qm_leaving");
				quartermasterGoes(s);
			}
		}

		// Those walking off: gone at the end of their way (Phase 20: out of a door, into a lift).
		// Those sent somewhere: at the end of their way, that is their place from now on.
		for (Figure figure : List.copyOf(s.figures)) {
			if (figure.mode == Figure.HELD && s.has("tmp.leave." + figure.key)) {
				s.unset("tmp.leave." + figure.key);
				s.set("gone." + figure.key);
				s.figures.remove(figure);
			} else if (figure.mode != Figure.SCRIPTED && s.has("tmp.rehome." + figure.key) && s.partner != figure
					&& !(figure.key.equals("quartermaster") && s.has("tmp.qm_leaving"))) {
				// (Phase 23: also when they broke off the way, having seen the player: they keep
				// looking or chasing, and go back to the new place afterwards.)
				s.unset("tmp.rehome." + figure.key);
				rehome(s, figure);
			}
		}

		safeCheckpoint(s, player);
		Companions.tick(s);

		if (s.mission == 1 || s.mission == 2) {
			eavesdrop(s);
		}

		// Tamsin, out of her cell, goes her own way.
		if (s.has("opt.prisoner") && !s.has("tmp.leave.prisoner") && !s.has("gone.prisoner") && s.dialog == null) {
			Figure prisoner = s.figure("prisoner");

			if (prisoner != null && D.equals(prisoner.ship)) {
				walk(prisoner, new Vec3(34.9, H, 3.6), new Vec3(34.9, H, 6.0));
				s.set("tmp.leave.prisoner");
			}
		}

		String step = s.step().id();

		if (s.stepTicks % 20 == 0) {
			music(s, s.mission, step);
		}

		switch (step) {
			case "m1.cockpit" -> {
				// Phase 20: out of the tunnel of light, the film of the arrival; on when it ends.
				if (s.has("tmp.docking") && !s.has("tmp.film") && !s.timer("film_in")) {
					s.set("tmp.film");
					s.timer("dock", ARRIVAL_FILM + 10);
					Story.cue(s, Story.CUE_CINEMA, "arrival", "", ARRIVAL_FILM);
				} else if (s.has("tmp.film") && !s.timer("dock") && s.dialog == null) {
					s.unset("tmp.docking");
					s.unset("tmp.film");
					s.complete();
				}
			}
			case "m1.droid" -> overhear(s);
			case "m1.distract" -> {
				if (fired(s, "droid_hint")) {
					s.hint("sabercraft.story.hint.droid_distract");
				}

				// Past the guard some other way (he turned to look at something): on all the same.
				if (s.inside(D, HIDDEN) || inVent(s.local(D)) && D.equals(s.here)) {
					s.complete();
				}
			}
			case "m1.hatch", "m3.return", "m5.escape", "m5.back" -> {
				if (s.inside(D, HIDDEN)) {
					s.complete();
				}
			}
			case "m3.hale" -> haleScene(s);
			case "m4.lift", "m6.reactor" -> {
				Vec3 me = s.local(D);

				// (Phase 23: standing there: one dropping from the deck level is caught and set back.)
				if (D.equals(s.here) && me.y < R + 0.6 && me.y > R - 0.5 && shaft(me) < DestroyerInterior.REACTOR_R) {
					s.complete();
				}
			}
			case "m4.download" -> {
				if (s.has("tmp.watch_hint") && s.dialog == null) {
					// After the bluff: Kestrel on how to get the copy done under his eyes.
					s.unset("tmp.watch_hint");
					s.radio("kestrel", "sabercraft.story.radio.m4_watch");
				}

				copy(s);
			}
			case "m5.route" -> {
				if (s.inside(D, DestroyerInterior.DETENTION)
						|| !s.has("trust.hale") && s.inside(D, new AABB(24.0, H - 0.5, 3.0, 25.0, H + 2.0, 7.0))) {
					s.complete();
				}
			}
			case "m5.cell" -> {
				Vec3 me = s.local(D);

				if (s.has("cell2.open") && me.z > DestroyerInterior.CELL_FRONT + 0.6) {
					s.complete();
				} else if (s.has("m5.trapped") && !s.has("cell2.open")) {
					// Phase 20: once Hale and his trooper are out, the checkpoint in the cell (as often
					// as one is caught later, one is back here, the field on, M-31 outside).
					if (s.figure("hale") == null && s.figure("trap1") == null && s.unsaved() && s.dialog == null) {
						s.checkpoint(D, CELL_2, 0.0F);
					}

					if (fired(s, "cell_hint")) {
						s.hint("sabercraft.story.hint.cell_droid");
					}
				}
			}
			case "m6.lab" -> {
				if (s.has("know.ilyra") && s.dialog == null) {
					s.complete();
				}
			}
			case "m6.escape1" -> {
				bridge(s, level);
				Vec3 me = s.local(D);

				if (D.equals(s.here) && me.y > H - 0.5 && shaft(me) < DestroyerInterior.REACTOR_R + 0.5) {
					s.complete();
				}
			}
			case "m6.escape2" -> {
				Vec3 me = s.local(D);

				if (D.equals(s.here) && me.z < DestroyerInterior.HANGAR_Z1 - 0.5 && me.y > H - 1.0 && me.y < H + 3.0) {
					s.complete();
				}
			}
			case "m6.duel" -> duelTick(s, player);
			case "m6.jump" -> {
				if (s.has("tmp.jump_go") && s.dialog == null && !s.has("tmp.jumping")) {
					s.set("tmp.jumping");
					s.timer("jump", InteriorJump.length());
					InteriorJump.story(level, s.falcon);
				} else if (s.has("tmp.jumping") && !s.timer("jump")) {
					s.unset("tmp.jumping");
					s.unset("tmp.jump_go");
					s.complete();
				}
			}
			default -> {
			}
		}

		// Nobody fights while someone speaks (the player cannot move then).
		if (s.fighting && !s.completing() && s.dialog == null && !s.has("tmp.film")) {
			Finale.tick(s, player, level);
		}
	}

	/**
	 * Phase 20, Mission 3: Hale in the armory. Phase 23: the room is empty when he comes: he waits
	 * in the corridor until the quartermaster (paged to the hangar) is out of the room and well
	 * away, then he comes in himself, through the door, up to the player; he shuts and bolts the
	 * door behind him ("so that nobody disturbs us"), they talk (both answers as before, nobody
	 * passes), he unbolts it and goes as he came.
	 */
	private static void haleScene(StorySession s) {
		Figure hale = s.figure("hale");

		if (hale == null) {
			s.complete();
			return;
		}

		Vec3 me = s.local(D);

		if (s.dialog != null) {
			return;
		}

		if (!s.has("tmp.hale_in")) {
			Figure quartermaster = s.figure("quartermaster");

			if (quartermaster == null || quartermaster.position().distanceTo(ARMORY_DOOR) > HALE_CLEAR
					&& !DestroyerInterior.ARMORY.contains(quartermaster.position().add(0.0, 0.5, 0.0))) {
				// The room is empty: he comes in, up to the player (a step short of them).
				s.set("tmp.hale_in");
				Vec3 front = me.distanceTo(ARMORY_DOOR) > 2.5 ? me.add(ARMORY_DOOR.subtract(me).normalize().scale(1.6)) : new Vec3(47.6, H, -10.0);
				walk(hale, new Vec3(43.0, H, -13.0), new Vec3(45.6, H, -12.9), new Vec3(46.6, H, -11.2), new Vec3(front.x, H, front.z));
			}

			return;
		}

		if (hale.mode != Figure.HELD) {
			return;
		}

		if (!s.has("tmp.hale_alone")) {
			// In: he shuts the door behind him and bolts it.
			s.set("tmp.hale_alone");
			s.set("tmp.hale_door");
			Story.cue(s, Story.CUE_SOUND, "", "story.door_lock", 0);
			hale.target = me;
			return;
		}

		if (!s.has("tmp.hale_talked")) {
			if (hale.position().distanceTo(me) < 7.0) {
				s.set("tmp.hale_talked");
				s.talk("m3_hale", hale);
			} else {
				// The player stepped away: he comes over.
				hale.target = me;
				walk(hale, me.add(hale.position().subtract(me).normalize().scale(1.6)));
			}
		} else if (s.has("tmp.hale_gone")) {
			// He unbolts the door and goes as he came; then the way is free.
			if (!s.has("tmp.hale_walk")) {
				s.set("tmp.hale_walk");
				s.unset("tmp.hale_door");
				walk(hale, new Vec3(46.6, H, -11.2), new Vec3(45.6, H, -12.9), new Vec3(43.0, H, -13.0), HALE_WAITS);
			} else {
				s.figures.remove(hale);
				s.complete();
			}
		}
	}

	/** Phase 23: where Hale waits in the corridor (and goes again), the armory's door, how far the quartermaster is to be gone. */
	// (Beside the armory's door, north of it: out of the paged quartermaster's way to the hangar,
	// who stood stuck in front of him otherwise.)
	static final Vec3 HALE_WAITS = new Vec3(42.6, H, -10.6);
	static final Vec3 ARMORY_DOOR = new Vec3(45.5, H, -13.0);
	static final double HALE_CLEAR = 9.0;

	/** Phase 20, Mission 3: the quartermaster is called away (a page, or Hale sends him): to the freight at the hangar's crates. */
	static void sendQuartermaster(StorySession s) {
		if (s.has("qm.away")) {
			return;
		}

		s.set("qm.away");
		Figure quartermaster = s.figure("quartermaster");

		if (quartermaster != null) {
			// Phase 23: he finishes what he was doing first (a few seconds; he looks round as he
			// goes, so one has time to get out of his way), then goes.
			quartermaster.mode = Figure.HELD;
			quartermaster.speed = 0.0F;
			quartermaster.target = null;
			quartermaster.timer = QM_DELAY;
			s.set("tmp.qm_leaving");
			s.set("tmp.rehome.quartermaster");
			s.hint("sabercraft.story.hint.qm_leaving");
		}
	}

	/** Phase 23: how long the paged quartermaster takes before he goes (ticks). */
	static final int QM_DELAY = 60;

	/** Phase 23: the paged quartermaster sets off: out of the armory, to the hangar's crates. */
	private static void quartermasterGoes(StorySession s) {
		Figure quartermaster = s.figure("quartermaster");

		if (quartermaster != null && quartermaster.mode == Figure.HELD) {
			walk(quartermaster, new Vec3(46.2, H, -12.6), new Vec3(43.0, H, -13.0), new Vec3(40.6, H, -15.0), new Vec3(40.6, H, -18.5), new Vec3(50.0, H, -23.5),
					new Vec3(HANGAR_CRATES.x, H, HANGAR_CRATES.z));
		}
	}

	/** Phase 20, Mission 2: a wrong container: only freight, and the lid clatters (those near turn to look). */
	static void wrongCrate(StorySession s, String mark) {
		s.set("m2.checked." + mark);
		Vec3 at = null;

		for (String[] crate : CRATES) {
			if (crate[0].equals(mark)) {
				at = new Vec3(Double.parseDouble(crate[1]) + 1.0, H + 0.5, -63.5);
			}
		}

		if (at == null) {
			return;
		}

		Story.cue(s, Story.CUE_SOUND, "", "story.crate_lid", 0);

		for (Figure figure : s.figures) {
			if (figure.observer && D.equals(figure.ship) && figure.position().distanceTo(at) < 10.0
					&& (figure.mode == Figure.ROUTINE || figure.mode == Figure.RETURN)) {
				figure.mode = Figure.LOOK;
				figure.target = at;
				figure.timer = 60;
				figure.suspicion = Math.max(figure.suspicion, Stealth.SUSPICIOUS);
			}
		}
	}

	/** Phase 20, Mission 3: the page from the security desk. */
	static void pageQuartermaster(StorySession s) {
		s.hear("intercom", "sabercraft.story.radio.page_quartermaster");
		sendQuartermaster(s);
	}

	/** Phase 20: a timer that has just run out (once; then it is forgotten). */
	static boolean fired(StorySession s, String name) {
		Integer left = s.timers.get(name);

		if (left != null && left <= 0) {
			s.timers.remove(name);
			return true;
		}

		return false;
	}

	/** Phase 20: whether a point (plot space, the feet) is in one of the Star Destroyer's crawl ways. */
	static boolean inVent(Vec3 at) {
		Vec3 body = at.add(0.0, 0.3, 0.0);

		for (AABB vent : DestroyerInterior.VENTS) {
			if (vent.contains(body)) {
				return true;
			}
		}

		return false;
	}

	/** Phase 20: someone sent somewhere takes up the routine the story has for them there. */
	private static void rehome(StorySession s, Figure figure) {
		for (Figure wanted : s.chapter.figures(s)) {
			if (wanted.key.equals(figure.key)) {
				figure.crew = wanted.crew;

				// Phase 23: one who broke off the way to look or chase goes on with that first.
				if (figure.mode == Figure.HELD || figure.mode == Figure.ROUTINE) {
					figure.mode = Figure.RETURN;
					figure.target = null;
				}

				return;
			}
		}
	}

	/**
	 * Phase 20: in a safe place (the hideout, a crawl way) with nobody after the player, nobody
	 * suspicious and nothing under way, a checkpoint is made where the player is, if anything kept
	 * by checkpoints changed since the last one.
	 */
	private static void safeCheckpoint(StorySession s, ServerPlayer player) {
		if (s.stepTicks % 20 != 7 || !D.equals(s.here) || s.dialog != null || s.pending != null || s.completing() || s.alarm > 0 || s.fighting
				|| s.suspicion >= Stealth.SUSPICIOUS || !s.unsaved() || NO_SAFE_CHECKPOINT.contains(s.step().id())) {
			return;
		}

		for (int left : s.timers.values()) {
			if (left > 0) {
				return;
			}
		}

		for (Figure figure : s.figures) {
			if (figure.mode == Figure.CHASE || figure.mode == Figure.INVESTIGATE || figure.mode == Figure.SCRIPTED || figure.stage >= Figure.ALERTED) {
				return;
			}
		}

		Vec3 me = s.local(D);

		if (!HIDDEN.contains(me.add(0.0, 0.5, 0.0)) && !inVent(me) || !player.onGround() && !inVent(me)) {
			return;
		}

		s.checkpoint(D, new Vec3(me.x, Math.floor(me.y + 0.01), me.z), player.getYRot());
	}

	/** Phase 20: steps where a safe place makes no checkpoint (scenes, the cell, the finale). */
	private static final java.util.Set<String> NO_SAFE_CHECKPOINT = java.util.Set.of("m1.briefing", "m1.cockpit", "m1.ramp", "m5.cell", "m6.door", "m6.lab",
			"m6.saber", "m6.escape1", "m6.escape2", "m6.duel", "m6.falcon", "m6.jump", "m6.epilogue");

	/** How far from the reactor's axis (plot space). */
	private static double shaft(Vec3 at) {
		return Math.hypot(at.x - DestroyerInterior.REACTOR_X, at.z - DestroyerInterior.REACTOR_Z);
	}

	/** Mission 1: near the trooper in the dark, unseen, one hears him grumble (once). */
	private static void overhear(StorySession s) {
		Figure trooper = s.figure("maint");

		if (trooper == null || s.has("tmp.overheard") || s.suspicion >= Stealth.SUSPICIOUS) {
			return;
		}

		if (trooper.position().distanceTo(s.local(D)) < 6.0) {
			s.set("tmp.overheard");
			s.hear("trooper", "sabercraft.story.d.m1_trooper.start");
		}
	}

	/** Missions 2 and 3: unseen near the two pilots, one hears what they say (the password, Hale). */
	private static void eavesdrop(StorySession s) {
		if (s.has("opt.eavesdrop") || !D.equals(s.here)) {
			return;
		}

		Vec3 pilots = new Vec3(46.1, H, -25.4);
		int heard = s.counters.getOrDefault("listen", 0);
		int line = s.counters.getOrDefault("listen_line", 0);

		if (pilots.distanceTo(s.local(D)) < 5.0 && s.suspicion < Stealth.SUSPICIOUS && s.alarm <= 0 && s.dialog == null) {
			heard++;
		} else {
			heard = Math.max(0, heard - 1);
		}

		s.counters.put("listen", heard);
		int[] at = {1, 70, 140, 210};

		if (line < at.length && heard >= at[line]) {
			s.hear(line % 2 == 0 ? "pilot_a" : "pilot_b", "sabercraft.story.radio.eavesdrop." + (line + 1));
			s.counters.put("listen_line", line + 1);
		} else if (line == at.length && heard >= 240) {
			s.set("opt.eavesdrop");
			s.set("know.password");
			s.set("know.hale");
			s.note("eavesdrop");
			s.hint("sabercraft.story.hint.eavesdrop");
			s.counters.put("listen_line", line + 1);
		}
	}

	/** Mission 4: copying the reactor's data, only while one stands at the console. */
	private static void copy(StorySession s) {
		if (!s.has("tmp.download")) {
			return;
		}

		boolean near = s.inside(D, CONSOLE_AREA);
		int progress = s.counters.getOrDefault("download", 0);
		Figure tech = s.figure("tech");

		if (near) {
			progress++;
			s.counters.put("download", progress);

			// The technician who was lied to watches the console and grows suspicious (not while a
			// noise of M-31's took him away, not while one reads a dialog, not in the moment of grace
			// after a checkpoint, and slowly enough that stepping back helps).
			if (tech != null && s.has("tech.watch") && s.dialog == null && s.grace <= 0
					&& (tech.mode == Figure.ROUTINE || tech.mode == Figure.LOOK || tech.mode == Figure.RETURN)) {
				tech.suspicion = Math.min(1.0F, tech.suspicion + 0.008F);
				tech.lastSeen = s.time();
				tech.seenAt = s.local(D);
			}

			if (progress % 10 == 0) {
				Story.cue(s, Story.CUE_HINT, "progress", "sabercraft.story.hint.download", progress * 100 / DOWNLOAD);
			}
		} else if (s.stepTicks % 60 == 0) {
			s.hint("sabercraft.story.hint.download_paused");
		}

		if (progress == DOWNLOAD / 2 && !s.has("tmp.inspect")) {
			// Halfway, the officer comes to look at the console himself.
			s.set("tmp.inspect");

			for (Figure figure : s.figures) {
				if (figure.key.startsWith("crew:") && Crew.OFFICER_OLIVE.equals(figure.kind()) && figure.y < H - 1) {
					figure.mode = Figure.INVESTIGATE;
					figure.target = new Vec3(49.2, R, 24.5);
					figure.timer = 140;
				}
			}

			s.radio("kestrel", "sabercraft.story.radio.m4_officer");
		}

		if (progress >= DOWNLOAD) {
			s.unset("tmp.download");
			s.note("reactor_data");
			s.set("know.download");
			Story.cue(s, Story.CUE_HINT, "progress", "sabercraft.story.hint.download", 100);
			s.radio("kestrel", "sabercraft.story.radio.m4_cut");
			s.complete();
		}
	}

	// --- What the dialogs do -----------------------------------------------------------------------

	/** A step said in a dialog is done (once the dialog is closed), if it is the step now. */
	static void done(StorySession s, String step) {
		if (s.at(step)) {
			s.pending = step;
		}
	}

	/** Mission 1: Dax takes the Falcon out of hyperspace and docks at the old airlock. */
	static void dock(StorySession s) {
		ServerLevel level = interiors(s);

		if (level == null || s.has("tmp.docking")) {
			return;
		}

		s.set("tmp.docking");
		s.timer("film_in", InteriorJump.tunnelEnd());
		InteriorJump.story(level, s.falcon);
	}

	/** Phase 20: how long the film of the arrival is (as the client's {@code ArrivalFilm}). */
	static final int ARRIVAL_FILM = 460;

	/** Phase 20: the player skipped a film: the story goes on at once. */
	void skipFilm(StorySession s, String film) {
		if ("arrival".equals(film) && s.at("m1.cockpit") && s.has("tmp.film")) {
			s.timer("dock", 1);
		}

		if (("duel".equals(film) || "duel_end".equals(film)) && s.at("m6.duel") && s.has("tmp.film")) {
			s.timer("film", 1);
		}
	}

	/** Phase 21: how long the film before the duel and the one of his flight are (as the client's {@code DuelFilm}). */
	static final int DUEL_FILM = 220;
	static final int DUEL_END_FILM = 170;
	/** Where the player stands in the film before the duel (facing him), and his way out (to the flight control door). */
	static final Vec3 DUEL_PLAYER = new Vec3(40.5, H, -21.0);

	/** Phase 21: the duel's step: the film (once; after that his words), the fight, his flight. */
	private static void duelTick(StorySession s, ServerPlayer player) {
		Figure shadow = s.figure("shadow");
		Vec3 me = s.local(D);

		if (s.has("tmp.duel_end")) {
			int left = s.timers.getOrDefault("film", 0);
			int t = DUEL_END_FILM - left;
			ServerLevel level = interiors(s);

			// Phase 23: her saber lands, sputters and dies (it is broken: it stays where it fell).
			if (fired(s, "saber_lands") && level != null && s.dropped != null) {
				Vec3 at = s.dropped.add(Vec3.atLowerCornerOf(s.destroyer));
				level.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK, at.x, at.y + 0.1, at.z, 24, 0.15, 0.05, 0.15, 0.4);
				level.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE, at.x, at.y + 0.1, at.z, 8, 0.1, 0.05, 0.1, 0.01);
				level.playSound(null, at.x, at.y, at.z, ShipInteriors.sound("story.saber_drop"), net.minecraft.sounds.SoundSource.HOSTILE, 1.0F, 1.0F);
			} else if (level != null && s.dropped != null && t > Finale.SABER_FLIGHT && t < 90 && t % 9 == 0) {
				Vec3 at = s.dropped.add(Vec3.atLowerCornerOf(s.destroyer));
				level.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK, at.x, at.y + 0.1, at.z, 3, 0.06, 0.02, 0.06, 0.2);
			}

			if (shadow != null) {
				// Her stagger as the saber flies off, and the stare at her empty hand, run their course.
				if (level != null && shadow.reactTicks > 0 && t < 96) {
					Hits.tick(shadow, level, Vec3.atLowerCornerOf(s.destroyer));
				}

				if (t == 62) {
					// She looks from her empty hand to the player: she has lost.
					s.hear("shadow", "sabercraft.story.shout.shadow_defeat");
				} else if (t == 96) {
					shadow.react = 0;
					shadow.reactTicks = 0;
					shadow.scriptSpeed = 6.5;
					walk(shadow, new Vec3(32.0, H, -31.0), new Vec3(22.0, H, -38.0), new Vec3(18.6, H, -39.0), new Vec3(15.5, H, -39.0));
					s.set("tmp.leave.shadow");
				}
			}


			if (!s.timer("film")) {
				s.unset("tmp.film");
				s.unset("tmp.duel_end");
				s.unset("tmp.leave.shadow");
				s.set("gone.shadow");
				s.figures.removeIf(figure -> figure.key.equals("shadow"));

				// His last words are in the film; Dax calls when it is over (seen with the HUD again).
				s.radio("dax", "sabercraft.story.radio.dax_arrives");
				Story.cue(s, Story.CUE_MUSIC, "", "music.event_rise", 0);

				s.complete();
			}

			return;
		}

		if (shadow == null || shadow.hostile || s.has("tmp.duel") || s.dialog != null) {
			return;
		}

		if (s.has("tmp.film")) {
			int left = s.timers.getOrDefault("film", 0);
			int t = DUEL_FILM - left;

			if (t == 70) {
				// He turns round.
				shadow.target = null;
			}

			if (t >= 70) {
				shadow.turnTowards(me, 6.0F, true);
			}

			if (t == 140) {
				shadow.lit = true;
				Story.cue(s, Story.CUE_SOUND, "", "saber.ignite", shadow.id);
			}

			if (!s.timer("film")) {
				s.unset("tmp.film");
				shadow.yaw = Crew.yaw(shadow.position(), me);
				Finale.duelStarts(s);
			}

			return;
		}

		if (shadow.position().distanceTo(me) < 10.0) {
			if (!s.has("seen.duel_film")) {
				// The film before the duel, once (saved at once: after a defeat his words only).
				s.set("seen.duel_film");
				s.progress.flags.add("seen.duel_film");
				s.set("tmp.film");
				s.timer("film", DUEL_FILM);
				Story.place(s, D, DUEL_PLAYER, 180.0F);
				Story.cue(s, Story.CUE_CINEMA, "duel", "", DUEL_FILM);
			} else {
				shadow.yaw = Crew.yaw(shadow.position(), me);
				s.talk("m6_shadow", shadow);
			}
		}
	}

	/** Phase 21: he breaks off ({@link Finale}): the film of his flight, then the Falcon. Phase 23: disarmed first. */
	static void duelEnds(StorySession s, Figure shadow) {
		s.set("tmp.film");
		s.set("tmp.duel_end");
		s.timer("film", DUEL_END_FILM);
		shadow.mode = Figure.HELD;
		Story.cue(s, Story.CUE_CINEMA, "duel_end", "", DUEL_END_FILM);
	}

	/** Mission 1: M-31, stuck behind the crates, comes with the player. */
	static void freeMouse(StorySession s) {
		s.set("droid.m31");
		s.figures.removeIf(figure -> figure.key.equals("m31"));
		Story.updateDroids(s);
		done(s, "m1.droid");
	}

	/** Mission 3: the armour from the locker (a disguise from now on). */
	static void putOnArmour(StorySession s) {
		s.set("wear.armour");
		s.disguised = true;
		ServerPlayer player = s.player();

		if (player != null) {
			OutfitSync.override(player, Outfit.STORMTROOPER.id());
		}

		s.hint("sabercraft.story.hint.disguise");
		done(s, "m3.armour");
	}

	/** Mission 3: Hale will go as he came (once he has said his last word). */
	static void haleLeaves(StorySession s) {
		s.set("tmp.hale_gone");
	}

	/** Mission 4: the technician did not like an answer. */
	static void techSuspects(StorySession s, float suspicion) {
		Figure tech = s.figure("tech");

		if (tech != null) {
			tech.suspicion = Math.max(tech.suspicion, suspicion);
		}
	}

	/**
	 * Mission 4: the password: the technician goes for a caf (Phase 20: to the dispenser by the
	 * lift, where he stays, his back to the console; before, he left the reactor and was gone).
	 */
	static void techLeaves(StorySession s) {
		s.set("tech.break");
		Figure tech = s.figure("tech");

		if (tech != null) {
			rehome(s, tech);
		}

		done(s, "m4.tech");
	}

	/** Mission 4: a bluff: the technician stays and watches. */
	static void techWatches(StorySession s) {
		s.set("tech.watch");
		techSuspects(s, 0.3F);
		s.set("tmp.watch_hint");
		done(s, "m4.tech");
	}

	/**
	 * Mission 4: refused: the technician shouts for security and runs to the wall comm by the lift
	 * (Phase 20: the alarm goes up there and troopers come down the lift; he stays by the comm).
	 */
	static void techAlarm(StorySession s) {
		Figure tech = s.figure("tech");

		if (tech != null) {
			tech.seenAt = s.local(D);
			Story.runToComm(s, tech);
		} else {
			s.raiseAlarm(Stealth.SEARCH + Stealth.LOSE);
		}

		done(s, "m4.tech");
	}

	/** Mission 4: the console: the copy begins (it needs the player beside it). */
	static void download(StorySession s) {
		if (!s.at("m4.download") || s.has("tmp.download") || s.has("know.download")) {
			return;
		}

		s.set("tmp.download");
		s.counters.put("download", 0);
		Story.cue(s, Story.CUE_HINT, "progress", "sabercraft.story.hint.download", 0);
	}

	/**
	 * Mission 5: Hale's trap: into cell 2. Phase 20: Hale and his trooper walk out (the guard at
	 * the entrance stays), M-31 slipped away and waits under the bench outside (never in the cell);
	 * once they are out, the checkpoint is made in the cell.
	 */
	static void trapped(StorySession s) {
		s.set("m5.trapped");
		Story.cue(s, Story.CUE_FADE, "", "sabercraft.story.hint.trapped", 30);
		Story.cue(s, Story.CUE_SOUND, "", "story.caught", 0);
		field(s, 1, false);
		Story.place(s, D, CELL_2, 0.0F);

		for (String key : List.of("hale", "trap1")) {
			Figure figure = s.figure(key);

			if (figure != null) {
				figure.x = key.equals("hale") ? 32.6 : 33.4;
				figure.z = key.equals("hale") ? 3.6 : 4.8;
				// Phase 22: through the middle of the door (two wide now, z 4 to 6).
				walk(figure, new Vec3(35.0, H, 4.8), new Vec3(37.6, H, 4.8), new Vec3(39.4, H, 1.6));
				// Phase 23: they leave the prisoner behind them (nobody raises the alarm about him).
				figure.blind = true;
				s.set("tmp.leave." + key);
			}
		}

		mouseOutside(s);
		s.timer("cell_hint", CELL_HINT);
		s.hint("sabercraft.story.hint.trapped");
	}

	/** Phase 20: in the cell, M-31 waits outside by the bench (and does not come in after the player). */
	private static void mouseOutside(StorySession s) {
		ServerPlayer player = s.player();

		if (player != null && s.has("droid.m31")) {
			com.phiro.sabercraft.droid.Droids.place(player, com.phiro.sabercraft.droid.DroidType.MOUSE, BENCH.add(Vec3.atLowerCornerOf(s.destroyer)), 180.0F, true);
		}
	}

	/** Mission 5: R2-V7 off its restraint: it comes along. */
	static void freeAstromech(StorySession s) {
		s.set("droid.r2v7");
		s.figures.removeIf(figure -> figure.key.equals("r2v7"));
		Story.updateDroids(s);
		done(s, "m5.r2");
	}

	/** Mission 5: Kestrel is out; she goes to the crawl way (and opens it). */
	static void kestrelFree(StorySession s) {
		s.set("kestrel.free");
		s.set("hatch.f");
		Figure kestrel = s.figure("kestrel");

		if (kestrel != null) {
			walk(kestrel, new Vec3(27.0, H, 3.6), new Vec3(26.3, H, 6.5));
		}

		done(s, "m5.cell1");
	}

	/** Mission 6: Ilyra's saber from the field: the fight begins. */
	static void takeSaber(StorySession s) {
		ServerPlayer player = s.player();
		s.set("saber.taken");

		if (player != null) {
			player.getInventory().setItem(player.getInventory().getSelectedSlot(), reward());
			player.containerMenu.broadcastChanges();
		}

		s.fighting = true;
		s.raiseAlarm(Stealth.SEARCH + Stealth.LOSE);
		// Phase 23: the chasers from the reactor (and anyone searching) fight from this moment.
		Finale.engageAll(s);
		Story.cue(s, Story.CUE_SOUND, "", "story.alarm", 0);
		s.radio("kestrel", "sabercraft.story.radio.saber_taken");
		learnFocus(s, true);
		done(s, "m6.saber");
	}

	/**
	 * Phase 21: the Focus, the one Force skill of the story: Ilyra's saber remembers. The journal
	 * explains it; the first time also a hint.
	 */
	static void learnFocus(StorySession s, boolean now) {
		s.set("know.focus");
		s.focus = 1.0F;

		if (!s.journal.contains("focus")) {
			s.journal.add("focus");
		}

		if (now) {
			s.radio("kestrel", "sabercraft.story.radio.focus");
			s.hint("sabercraft.story.hint.focus_learned");
		}
	}

	/** Sends a figure along a way once (then it holds there). */
	static void walk(Figure figure, Vec3... way) {
		figure.way = List.of(way);
		figure.wayIndex = 0;
		figure.mode = Figure.SCRIPTED;
		figure.target = null;
	}

	// --- Places, locks, the dark -------------------------------------------------------------------

	/** The player stepped into a ship's exit (the Falcon's ramp, the hangar's force field). */
	void exit(StorySession s) {
		if (s.at("m1.ramp") && F.equals(s.here)) {
			Story.place(s, D, AIRLOCK, 180.0F);
			s.complete();
		} else if (s.at("m6.falcon") && D.equals(s.here)) {
			Story.cue(s, Story.CUE_FADE, "", "", 20);
			Story.place(s, F, FALCON_ENTRY, 90.0F);
			s.complete();
		} else {
			s.hint(F.equals(s.here) ? "sabercraft.story.hint.stay_falcon" : "sabercraft.story.hint.no_way");
		}
	}

	/** Where the marker points now (some steps lead on to several places). */
	Mission.@Nullable Target target(StorySession s) {
		Mission.Step step = s.step();

		// Phase 22: after the chapter there is nowhere to go.
		if (s.has(CHAPTER_DONE)) {
			return null;
		}

		switch (step.id()) {
			case "m5.route" -> {
				if (s.has("trust.hale")) {
					return at(D, DestroyerInterior.CX - 3.5, H + 1.0, DestroyerInterior.DETENTION_DOOR_Z0 + 1.0);
				}

				return s.has("know.hale_trap") || s.has("hatch.f") ? at(D, 22.6, H + 0.5, -3.5) : at(D, 18.0, H + 1.1, -1.4);
			}
			case "m5.cell" -> {
				return null;
			}
			case "m1.distract" -> {
				// Over the guard while he stands at the hatch.
				Figure guard = s.figure("maint");
				return guard != null ? new Mission.Target(D, guard.position().add(0.0, 2.3, 0.0)) : step.target();
			}
			case "m2.drop" -> {
				// The way to the hangar: through the crawl way to flight control; there, the containers.
				Vec3 me = s.local(D);
				return me.z > -33.0 && me.x < 37.0 ? step.target() : at(D, 24.0, H + 1.4, -63.5);
			}
			case "m5.cell1" -> {
				return s.has("cell1.open") ? at(D, 27.0, H + 1.0, 0.0) : step.target();
			}
			case "m6.reactor" -> {
				return s.local(D).y > H - 2 ? step.target() : null;
			}
			case "m6.duel" -> {
				// Once the blades are lit she is right there (and moving): no marker over her.
				Figure shadow = s.figure("shadow");
				return shadow != null && !s.has("tmp.duel") ? new Mission.Target(D, shadow.position().add(0.0, 2.2, 0.0)) : null;
			}
			default -> {
				return step.target();
			}
		}
	}

	@Override
	public boolean locked(StorySession s, String ship, int x, int y, int z) {
		if (!D.equals(ship)) {
			return false;
		}

		int m = s.mission;

		if (x == DestroyerInterior.CX + 4 && y == H && z == -14) {
			// The armory: the security desk opens it (Phase 20: Hale bolts it while he talks).
			return !s.has("armory.open") || s.has("tmp.hale_door");
		}

		if (x == DestroyerInterior.CX - 4 && y == H && z == DestroyerInterior.DETENTION_DOOR_Z0) {
			// The detention block: open only for the one Hale expects (Phase 20: and for Hale and
			// his trooper leaving after the trap).
			return !(s.has("trust.hale") && s.at("m5.route")) && !(s.at("m5.cell") && (s.has("tmp.leave.hale") || s.has("tmp.leave.trap1")));
		}

		if (x == DestroyerInterior.LAB_DOOR_X0 && y == R && z == DestroyerInterior.LAB_WALL) {
			return !s.has("lab.open");
		}

		if (x == DestroyerInterior.CX + 4 && y == H && z == DestroyerInterior.LOBBY_Z0) {
			// The gallery to the reactor: the checkpoint lets one through in Mission 4.
			return m >= 1 && m <= 3 && !s.has("m4.passed");
		}

		BlockPos cell = new BlockPos(x, y, z);

		if (cell.equals(DestroyerInterior.HIDEOUT_HATCH) || cell.equals(DestroyerInterior.DETENTION_HATCH)) {
			// Kestrel's way to the detention block: bolted on the far side.
			return !s.has("hatch.f");
		}

		return false;
	}

	@Override
	public @Nullable String hatchClosed(StorySession s, String ship, int x, int y, int z) {
		if (D.equals(ship) && DestroyerInterior.MAINTENANCE_HATCH.equals(new BlockPos(x, y, z)) && (s.at("m1.distract") || s.at("m1.hatch"))) {
			// Phase 20: the guard stands right in front of it.
			Figure guard = s.figure("maint");

			if (guard != null && guard.position().distanceTo(Vec3.atBottomCenterOf(DestroyerInterior.MAINTENANCE_HATCH).add(0.0, 0.0, 1.0)) < 2.2) {
				return "sabercraft.story.hint.hatch_guarded";
			}
		}

		return Chapter.super.hatchClosed(s, ship, x, y, z);
	}

	// --- Checkpoints (Phase 20) --------------------------------------------------------------------

	@Override
	public boolean checkpoint(StorySession s, Mission.Step step) {
		// Out of Hale's cell the checkpoint stays the cell's; through the crawl way it is at its hatch.
		if (step.id().equals("m5.r2")) {
			return !s.has("trust.hale");
		}

		return step.checkpoint();
	}

	@Override
	public Mission.Place place(StorySession s, Mission.Step step) {
		switch (step.id()) {
			case "m3.card" -> {
				// The armour on: by the lockers once the quartermaster is gone, else in the hideout.
				if (s.has("qm.away")) {
					return new Mission.Place(D, LOCKERS_SAFE, 90.0F);
				}
			}
			case "m3.hale" -> {
				return new Mission.Place(D, ARMORY_SAFE, 90.0F);
			}
			case "m5.cell" -> {
				if (s.has("m5.trapped")) {
					return new Mission.Place(D, CELL_2, 0.0F);
				}
			}
			case "m5.r2" -> {
				// Out of the cell (a file of Phase 19): in the corridor, behind the guard.
				if (!s.has("hatch.f")) {
					return new Mission.Place(D, DETENTION_SAFE, 0.0F);
				}
			}
			default -> {
			}
		}

		return Chapter.super.place(s, step);
	}

	@Override
	public void settle(StorySession s) {
		// Anyone walking off counts as gone from now on.
		for (String flag : List.copyOf(s.flags)) {
			if (flag.startsWith("tmp.leave.")) {
				String key = flag.substring("tmp.leave.".length());
				s.unset(flag);
				s.set("gone." + key);
				s.figures.removeIf(figure -> figure.key.equals(key));
			}
		}

		// Anyone sent somewhere is there.
		for (String flag : List.copyOf(s.flags)) {
			if (flag.startsWith("tmp.rehome.")) {
				Figure figure = s.figure(flag.substring("tmp.rehome.".length()));
				s.unset(flag);

				if (figure != null) {
					rehome(s, figure);
					figure.snapToRoutine(s.time());
				}
			}
		}
	}

	@Override
	public void droids(StorySession s) {
		Story.gatherDroids(s);

		if (s.at("m5.cell") && s.has("m5.trapped") && !s.has("cell2.open")) {
			mouseOutside(s);
		}
	}

	@Override
	public boolean detected(StorySession s, Figure figure) {
		// Phase 20, Mission 4: in armour on the reactor floor an officer who grows suspicious does not
		// sound the alarm at once: he calls the player over and asks questions (once; a second time,
		// or a wrong answer, and he runs for the wall comm).
		if (s.mission == 3 && s.disguised && D.equals(figure.ship) && officer(figure) && figure.y < H - 1 && s.dialog == null) {
			if (s.has("tmp.cleared." + figure.key)) {
				figure.suspicion = 0.3F;
				figure.stage = Figure.SUSPICIOUS;
				return true;
			}

			if (!s.has("tmp.questioned." + figure.key)) {
				s.set("tmp.questioned." + figure.key);
				s.talk("m4_officer", figure);
				figure.mode = Figure.HELD;
				figure.target = s.local(D);
				figure.speed = 0.0F;
				return true;
			}
		}

		return false;
	}

	private static boolean officer(Figure figure) {
		String kind = figure.kind();
		return Crew.OFFICER.equals(kind) || Crew.OFFICER_OLIVE.equals(kind);
	}

	/** Phase 20, Mission 4: the officer is satisfied with the answer: back to his round, no more questions. */
	static void officerSatisfied(StorySession s) {
		Figure officer = s.partner;

		if (officer != null) {
			s.set("tmp.cleared." + officer.key);
			officer.suspicion = 0.0F;
			officer.stage = Figure.CALM;
		}
	}

	/** Phase 20, Mission 4: a wrong answer: the officer runs for the wall comm. */
	static void officerAlarm(StorySession s) {
		Figure officer = s.partner;

		if (officer != null) {
			officer.seenAt = s.local(D);
			s.hear("officer_reactor", "sabercraft.story.shout.intruder");
			Story.runToComm(s, officer);
		}
	}

	@Override
	public List<Vec3> comms(StorySession s, String ship) {
		return D.equals(ship) ? COMMS : List.of();
	}

	@Override
	public List<Vec3> entries(StorySession s, String ship) {
		return D.equals(ship) ? ENTRIES : List.of();
	}

	/** Phase 20: from the copy (the reactor's safety shutdown) until the saber is taken. */
	@Override
	public boolean emergency(StorySession s) {
		return s.mission >= 4 && s.has("know.download") && !s.has("saber.taken");
	}

	/** Phase 20: the wall comms (where one stands to use them): corridor, lobby, armory, detention, hangar, reactor. */
	static final List<Vec3> COMMS = List.of(new Vec3(37.8, H, -6.0), new Vec3(45.6, H, 4.6), new Vec3(46.0, H, -4.6), new Vec3(35.0, H, 8.6),
			new Vec3(36.5, H, -18.5), REACTOR_COMM, new Vec3(50.9, R, 29.0), new Vec3(31.0, H, 21.5));

	/** Phase 20: where troopers come in after an alarm: the lifts, the hangar's door, the corridor by the detention block and the armory. */
	static final List<Vec3> ENTRIES = List.of(new Vec3(40.5, H, DestroyerInterior.LIFT_Z0 + 1.5), new Vec3(40.5, H, -15.0), new Vec3(28.8, R, 25.0), new Vec3(28.8, H, 25.0),
			new Vec3(38.0, H, 4.0), new Vec3(43.0, H, -13.0));

	@Override
	public boolean dark(StorySession s, String ship, double x, double y, double z) {
		if (!D.equals(ship)) {
			return false;
		}

		// The maintenance room and the airlock: always (a few dim lamps).
		if (x >= 1.0 && x <= 13.0 && z >= -9.0 && z <= 13.0 && y >= H - 1 && y <= H + 4) {
			return true;
		}

		// After the download the ship's lights are low: the lobby, the corridor, the gallery, the hidden room.
		return s.mission == 4 && (LOBBY_DARK.contains(x, y, z) || HIDDEN.inflate(1.0).contains(x, y, z));
	}

	@Override
	public boolean restricted(StorySession s, String ship, double x, double y, double z) {
		if (!D.equals(ship)) {
			return false;
		}

		if (y > 140.0 || DestroyerInterior.LAB.inflate(0.5).contains(x, y, z)) {
			return true;
		}

		if (y < H - 1 && Math.hypot(x - DestroyerInterior.REACTOR_X, z - DestroyerInterior.REACTOR_Z) < DestroyerInterior.REACTOR_R + 1.0) {
			return !s.has("m4.passed");
		}

		return DestroyerInterior.DETENTION.contains(x, y, z) && !s.has("item.card");
	}

	/** In the finale: the player struck with the saber. */
	void strike(StorySession s, ServerPlayer player) {
		Finale.strike(s, player);
	}

	static @Nullable ServerLevel interiors(StorySession s) {
		return s.server.getLevel(ShipInteriors.DIMENSION);
	}
}
