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
	/** The hidden room (Kestrel's hideout), the reactor's floor, the shaft (plot space). */
	static final AABB HIDDEN = DestroyerInterior.HIDDEN;
	static final AABB LOBBY_DARK = new AABB(36, H - 1, -16, 51, H + 5, 12);
	static final AABB CONSOLE_AREA = new AABB(49.5, R - 0.5, 22.5, 53.0, R + 2.5, 26.5);
	/** How long the reactor's data take to copy (ticks at the console). */
	static final int DOWNLOAD = 120;
	/** How long the astromech needs to open a lock (ticks). */
	static final int HACK = 60;

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

		Mission m1 = new Mission("m1", List.of(
				step("m1.briefing", F, FALCON_LOUNGE, 150.0F, false, at(F, 19.2, FF + 1.0, 24.8)),
				step("m1.cockpit", F, FALCON_LOUNGE, 150.0F, false, at(F, 42.5, FF + 1.0, 21.7)),
				step("m1.ramp", F, FALCON_COCKPIT, 0.0F, false, at(F, 35.5, 4.0, 40.5)),
				step("m1.droid", D, AIRLOCK, 180.0F, false, at(D, 2.6, H + 0.4, -6.0)),
				step("m1.hatch", D, new Vec3(3.5, H, -5.5), 270.0F, false, at(D, 10.5, H + 0.5, -8.6))),
				List.of(new Mission.Optional("pad", s -> true)));

		Mission m2 = new Mission("m2", List.of(
				step("m2.kestrel", D, HIDEOUT, 90.0F, false, kestrel),
				step("m2.terminal", D, HIDEOUT, 90.0F, false, at(D, 18.0, H + 1.1, -1.4)),
				step("m2.drop", D, HIDEOUT, 90.0F, false, at(D, 21.0, H + 0.5, -63.5)),
				step("m2.return", D, new Vec3(21.5, H, -61.5), 0.0F, false, kestrel)),
				List.of(new Mission.Optional("eavesdrop", s -> true)));

		Mission m3 = new Mission("m3", List.of(
				step("m3.plan", D, HIDEOUT, 90.0F, false, kestrel),
				step("m3.console", D, HIDEOUT, 90.0F, false, at(D, 43.5, H + 0.6, -8.5)),
				step("m3.armour", D, new Vec3(41.5, H, -10.5), 270.0F, false, at(D, 55.6, H + 1.0, -8.5)),
				step("m3.card", D, new Vec3(54.0, H, -8.5), 90.0F, false, at(D, 49.5, H + 0.95, -5.5)),
				step("m3.hale", D, new Vec3(50.5, H, -7.5), 90.0F, false, null),
				step("m3.return", D, new Vec3(50.5, H, -7.5), 90.0F, false, hideout)),
				List.of(new Mission.Optional("eavesdrop", s -> !s.has("know.password")), new Mission.Optional("armour2", s -> s.has("wear.armour"))));

		Mission m4 = new Mission("m4", List.of(
				step("m4.brief", D, HIDEOUT, 90.0F, false, kestrel),
				step("m4.checkpoint", D, HIDEOUT, 90.0F, false, at(D, 42.6, H + 1.0, -0.5)),
				step("m4.lift", D, new Vec3(47.5, H, -1.0), 0.0F, false, at(D, 31.0, H + 0.5, 25.0)),
				step("m4.tech", D, REACTOR_FLOOR, 270.0F, false, at(D, 51.05, R + 1.0, 24.5)),
				step("m4.download", D, REACTOR_FLOOR, 270.0F, true, at(D, 52.5, R + 0.6, 24.5))),
				List.of(new Mission.Optional("coolant", s -> s.has("m4.passed"))));

		Mission m5 = new Mission("m5", List.of(
				step("m5.escape", D, new Vec3(49.8, R, 24.5), 90.0F, false, hideout),
				step("m5.recording", D, HIDEOUT, 90.0F, false, at(D, 22.5, H + 1.2, -4.5)),
				step("m5.route", D, HIDEOUT, 90.0F, false, null),
				step("m5.cell", D, CELL_2, 0.0F, false, null),
				step("m5.r2", D, new Vec3(26.5, H, 6.5), 270.0F, false, at(D, 27.5, H + 0.6, 9.4)),
				step("m5.cell1", D, new Vec3(27.5, H, 5.5), 180.0F, false, at(D, 27.5, H + 1.0, 2.5)),
				step("m5.back", D, new Vec3(27.5, H, 5.5), 270.0F, false, hideout)),
				List.of(new Mission.Optional("prisoner", s -> s.has("droid.r2v7"))));

		Mission m6 = new Mission("m6", List.of(
				step("m6.plan", D, HIDEOUT, 90.0F, false, kestrel),
				step("m6.reactor", D, HIDEOUT, 90.0F, false, at(D, 31.0, H + 0.5, 25.0)),
				step("m6.door", D, REACTOR_FLOOR, 270.0F, false, at(D, 44.5, R + 1.0, 38.6)),
				step("m6.lab", D, new Vec3(44.5, R, 41.0), 0.0F, false, at(D, 39.5, R + 0.6, 46.5)),
				step("m6.saber", D, new Vec3(44.5, R, 41.0), 0.0F, false, at(D, 44.5, R + 1.0, 50.5)),
				step("m6.escape1", D, new Vec3(44.5, R, 47.5), 180.0F, false, at(D, 31.0, R + 0.5, 25.0)),
				step("m6.escape2", D, LIFT_TOP, 180.0F, false, at(D, 40.5, H + 1.0, -16.0)),
				step("m6.duel", D, new Vec3(40.5, H, -18.5), 180.0F, false, null),
				step("m6.falcon", D, new Vec3(40.5, H, -24.0), 180.0F, false, at(D, 40.5, H, -38.0)),
				step("m6.jump", F, FALCON_ENTRY, 90.0F, false, at(F, 42.5, FF + 1.0, 21.7)),
				step("m6.epilogue", F, FALCON_LOUNGE, 150.0F, false, null)),
				List.of());

		return List.of(m1, m2, m3, m4, m5, m6);
	}

	private static Mission.Step step(String id, String ship, Vec3 spawn, float yaw, boolean unseen, Mission.@Nullable Target target) {
		return new Mission.Step(id, ship, spawn, yaw, unseen, target);
	}

	private static Mission.Target at(String ship, double x, double y, double z) {
		return new Mission.Target(ship, new Vec3(x, y, z));
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
		int m = s.progress.mission;
		return m == 0 ? "mira" : m <= 3 ? "kestrel" : "dax";
	}

	static String captureLine(StorySession s) {
		return "sabercraft.story.radio.caught." + s.mission().id();
	}

	// --- People ------------------------------------------------------------------------------------

	@Override
	public List<Figure> figures(StorySession s) {
		List<Figure> list = new ArrayList<>();
		int m = s.progress.mission;
		String step = s.step().id();
		falcon(s, list, m, step);
		crew(s, list, m, step);
		people(s, list, m, step);
		return list;
	}

	/** The Falcon's people: Mira, Dax at the controls, Rorrak at the drive; at the end everyone in the lounge. */
	private static void falcon(StorySession s, List<Figure> list, int m, String step) {
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
				if (m == 3 && ("m4.tech".equals(step) || "m4.download".equals(step))) {
					continue;
				}
			} else if (bottom && Crew.OFFICER_OLIVE.equals(c.kind())) {
				if ("m4.download".equals(step)) {
					// Walks the south of the floor, back and forth (round the converters).
					routine = Crew.patrol(Crew.OFFICER_OLIVE, 0.7, 0.0, ring(150), ring(120), ring(90), ring(60), ring(90), ring(120));
				}
			} else if (bottom && Crew.TROOPER.equals(c.kind())) {
				// The guard by the lift went searching after the download.
				if (m >= 4) {
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

	/** A point on the reactor floor's walking ring (radius 8 round the core), at that angle (degrees). */
	private static Vec3 ring(double angle) {
		double a = Math.toRadians(angle);
		return new Vec3(DestroyerInterior.REACTOR_X + Math.cos(a) * 8.0, R, DestroyerInterior.REACTOR_Z + Math.sin(a) * 8.0);
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

				if ("m1.droid".equals(step) || "m1.hatch".equals(step)) {
					// A trooper with a lamp walks round the machinery: the maintenance room is dark.
					Figure trooper = guard("maint", Crew.patrol(Crew.TROOPER, 0.9, 0.0, new Vec3(3.5, H, -3.5), new Vec3(10.0, H, -3.5), new Vec3(10.0, H, 3.5),
							new Vec3(3.5, H, 3.5)));
					trooper.lamp = true;
					list.add(trooper);
				}
			}
			case 2 -> {
				// The quartermaster: at his desk, now and then to the racks by the door.
				Crew walk = Crew.patrol(Crew.OFFICER_OLIVE, 0.6, 0.0, new Vec3(48.0, H, -4.4), new Vec3(46.5, H, -4.4), new Vec3(46.0, H, -9.0),
						new Vec3(46.3, H, -12.4), new Vec3(46.0, H, -9.0), new Vec3(46.5, H, -4.4));
				Crew desk = Crew.at(Crew.OFFICER_OLIVE, Crew.STAND, 48.0, H, -4.4, 180.0F);
				Figure quartermaster = guard("quartermaster", "m3.hale".equals(step) ? desk : walk);
				quartermaster.speaker = "quartermaster";
				quartermaster.talks = true;
				list.add(quartermaster);

				if ("m3.hale".equals(step)) {
					Figure hale = person("hale", "hale", Crew.at(Crew.OFFICER_BLACK, Crew.STAND, 45.5, H, -13.0, 270.0F), D);
					hale.talks = false;
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
					Figure tech = guard("tech", Crew.at(Crew.TECH, Crew.WORK, 51.05, R, 24.5, 270.0F));
					tech.speaker = "tech";
					tech.talks = true;
					list.add(tech);
				}
			}
			case 4 -> {
				// After the download: lamps in the dark lobby.
				Figure lobby = guard("m5_lobby", Crew.patrol(Crew.TROOPER, 0.8, 0.0, new Vec3(37.5, H, -1.0), new Vec3(42.0, H, -1.0), new Vec3(42.0, H, 4.6),
						new Vec3(37.5, H, 4.6)));
				lobby.lamp = true;
				list.add(lobby);

				if ("m5.cell".equals(step) && s.has("trust.hale") && !s.has("tmp.trapped")) {
					// The trap: Hale waits in the detention block with two troopers.
					list.add(person("hale", "hale", Crew.at(Crew.OFFICER_BLACK, Crew.STAND, 33.5, H, 4.2, 270.0F), D));
					list.add(guard("trap1", Crew.at(Crew.TROOPER, Crew.STAND, 34.6, H, 3.2, 270.0F)));
					list.add(guard("trap2", Crew.at(Crew.TROOPER, Crew.STAND, 34.6, H, 5.6, 270.0F)));
				}

				if (step.equals("m5.cell") || step.equals("m5.r2") || step.equals("m5.cell1") || step.equals("m5.back")) {
					// The detention block: a guard walking along the cells, an officer by the bench.
					list.add(guard("det_guard", Crew.patrol(Crew.TROOPER, 0.8, 0.0, new Vec3(27.5, H, 4.3), new Vec3(34.6, H, 4.3))));
					Figure officer = guard("det_officer", Crew.at(Crew.OFFICER, Crew.STAND, 34.6, H, 7.5, 90.0F));
					list.add(officer);

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
				if (!s.has("saber.taken") && (step.equals("m6.reactor") || step.equals("m6.door"))) {
					list.add(guard("lab_guard", Crew.at(Crew.TROOPER, Crew.STAND, 44.5, R, 35.5, 180.0F)));
				}

				hostiles(s, list, step);

				if (step.equals("m6.duel") || step.equals("m6.falcon")) {
					Figure shadow = person("shadow", "shadow", Crew.at("shadow", Crew.STAND, 40.5, H, -28.5, 0.0F), D);
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

	/** The fighters of the finale, by section (fewer with the coolant sabotaged or the second armour given away). */
	private static void hostiles(StorySession s, List<Figure> list, String step) {
		switch (step) {
			case "m6.escape1" -> {
				list.add(fighter("h1", 37.0, R, 33.5, 180.0F));
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
		int m = s.progress.mission;
		String step = s.step().id();

		switch (m) {
			case 0 -> {
				if (step.equals("m1.droid") || step.equals("m1.hatch")) {
					list.add(point("sign", 8.6, H + 0.9, -8.1, 9.6, H + 1.8, -7.9, false));

					if (!s.has("opt.pad")) {
						list.add(point("pad", 11.3, H + 0.75, 0.2, 12.0, H + 1.1, 0.9, false));
					}
				}
			}
			case 1 -> {
				if (step.equals("m2.drop")) {
					list.add(point("drop", 20.0, H, -64.0, 22.0, H + 1.0, -63.0, false));
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
			case "drop" -> unwatched(s, "m2_container");
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

		if (level == null || ship == null) {
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

	@Override
	public boolean talk(StorySession s, Figure figure) {
		String step = s.step().id();
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

			if (step.equals("m5.cell") && s.has("tmp.trapped") && !s.has("cell2.open")) {
				s.set("cell2.open");
				field(s, 1, true);
				Story.cue(s, Story.CUE_SOUND, "", "story.field_off", 0);
				return "sabercraft.story.droid.m31_field";
			}

			// A noise where the player looks: those near go to see.
			Vec3 at = lookTarget(s, player, level, 14.0);
			int went = Stealth.distract(s, s.here, at, 8.0);
			Vec3 world = at.add(Vec3.atLowerCornerOf(s.origin(s.here)));
			level.playSound(null, world.x, world.y, world.z, ShipInteriors.sound("ship.crew_chirp"), SoundSource.NEUTRAL, 0.9F, 1.4F);
			return went > 0 ? "sabercraft.story.droid.m31_distract" : "sabercraft.story.droid.m31_noise";
		}

		if (droid.equals("astromech")) {
			// A lock within reach: the astromech opens it (it takes a moment, and it beeps).
			Mission.Point lock = null;

			for (Mission.Point point : this.points(s)) {
				if (point.hack() && point.ship().equals(s.here) && point.box().distanceToSqr(me.add(0.0, 1.0, 0.0)) < 36.0) {
					lock = point;
				}
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

	/** Where the player looks (plot space), at most that far. */
	private static Vec3 lookTarget(StorySession s, ServerPlayer player, ServerLevel level, double reach) {
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getLookAngle().scale(reach));
		HitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
		Vec3 at = hit.getType() == HitResult.Type.MISS ? end : hit.getLocation().subtract(player.getLookAngle().scale(0.3));
		return at.subtract(Vec3.atLowerCornerOf(s.origin(s.here)));
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
		int m = s.progress.mission;
		String id = step.id();

		// The ship's state at this point of the story.
		if (m >= 4) {
			s.set("ship.alert");
			s.set("armour.burned");
			s.alert = true;
			s.burned = true;
		}

		ship(s);
		kit(s);

		// Anyone who was leaving when this checkpoint was made is gone now.
		for (String flag : List.copyOf(s.flags)) {
			if (flag.startsWith("tmp.leave.")) {
				String key = flag.substring("tmp.leave.".length());
				s.unset(flag);
				s.set("gone." + key);
				s.figures.removeIf(figure -> figure.key.equals(key));
			}
		}

		// Asked again at every checkpoint (a client that came back has to hear it again).
		s.music = "-";
		music(s, m, id);

		switch (id) {
			case "m1.ramp" -> {
				if (!again) {
					s.talk("m1_rorrak", null);
				}
			}
			case "m3.hale" -> {
				Figure hale = s.figure("hale");

				if (hale != null) {
					Vec3 me = step.spawn();
					Vec3 door = new Vec3(45.5, H, -13.0);
					Vec3 front = me.add(door.subtract(me).normalize().scale(1.6));
					walk(hale, new Vec3(46.6, H, -11.2), new Vec3(front.x, H, front.z));
				}

				Figure quartermaster = s.figure("quartermaster");

				if (quartermaster != null && quartermaster.mode == Figure.ROUTINE) {
					quartermaster.mode = Figure.RETURN;
				}
			}
			case "m5.escape" -> {
				if (!again) {
					s.radio("vorr", "sabercraft.story.radio.vorr_alert");
					s.raiseAlarm(Stealth.SEARCH);
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
				} else if (!again && !s.has("tmp.trapped")) {
					Figure hale = s.figure("hale");
					s.talk("m5_trap", hale);
				}
			}
			case "m6.escape1", "m6.escape2", "m6.falcon" -> {
				s.fighting = true;
				Finale.arm(s);
			}
			case "m6.duel" -> {
				s.fighting = true;
				Figure shadow = s.figure("shadow");

				if (shadow != null) {
					shadow.mode = Figure.HELD;
					shadow.target = step.spawn();
				}
			}
			case "m6.epilogue" -> {
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
			int wanted = s.progress.mission == 4 ? 3 : 0;

			if (InteriorSystems.lightStep(level, s.destroyer, plan) != wanted) {
				InteriorSystems.setLightStep(level, s.destroyer, plan, wanted);
			}
		}

		field(s, 0, s.has("cell1.open"));
		field(s, 1, s.has("cell2.open"));
		field(s, 2, s.has("cell3.open"));
	}

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
		OutfitSync.override(player, s.disguised ? Outfit.STORMTROOPER.id() : null);
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
			case "m6.duel" -> s.has("tmp.duel") ? "music.story_duel" : "";
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

		// Those walking off: gone at the end of their way.
		for (Figure figure : List.copyOf(s.figures)) {
			if (figure.mode == Figure.HELD && s.has("tmp.leave." + figure.key)) {
				s.unset("tmp.leave." + figure.key);
				s.set("gone." + figure.key);
				s.figures.remove(figure);
			}
		}

		if (s.progress.mission == 1 || s.progress.mission == 2) {
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
			music(s, s.progress.mission, step);
		}

		switch (step) {
			case "m1.cockpit" -> {
				if (s.has("tmp.docking") && !s.timer("dock") && s.dialog == null) {
					s.unset("tmp.docking");
					s.complete();
				}
			}
			case "m1.droid" -> overhear(s);
			case "m1.hatch", "m3.return", "m5.escape", "m5.back" -> {
				if (s.inside(D, HIDDEN)) {
					s.complete();
				}
			}
			case "m3.hale" -> {
				Figure hale = s.figure("hale");

				if (hale == null) {
					s.complete();
				} else if (hale.mode == Figure.HELD && !s.has("tmp.hale_talked") && s.dialog == null) {
					s.set("tmp.hale_talked");
					s.talk("m3_hale", hale);
				} else if (hale.mode == Figure.HELD && s.has("tmp.hale_gone") && s.dialog == null) {
					s.figures.remove(hale);
					s.complete();
				}
			}
			case "m4.lift", "m6.reactor" -> {
				Vec3 me = s.local(D);

				if (D.equals(s.here) && me.y < R + 1.5 && shaft(me) < DestroyerInterior.REACTOR_R) {
					s.complete();
				}
			}
			case "m4.download" -> copy(s);
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
				}
			}
			case "m6.lab" -> {
				if (s.has("know.ilyra") && s.dialog == null) {
					s.complete();
				}
			}
			case "m6.escape1" -> {
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
			case "m6.duel" -> {
				Figure shadow = s.figure("shadow");
				Vec3 me = s.local(D);

				if (shadow != null && !shadow.hostile && !s.has("tmp.duel") && s.dialog == null && shadow.position().distanceTo(me) < 10.0) {
					s.talk("m6_shadow", shadow);
				}
			}
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

		if (s.fighting && !s.completing()) {
			Finale.tick(s, player, level);
		}
	}

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
			s.radio("trooper", "sabercraft.story.d.m1_trooper.start");
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
			s.radio(line % 2 == 0 ? "pilot_a" : "pilot_b", "sabercraft.story.radio.eavesdrop." + (line + 1));
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

			// The technician who was lied to watches the console and grows suspicious.
			if (tech != null && s.has("tech.watch") && tech.mode != Figure.SCRIPTED) {
				tech.suspicion = Math.min(1.0F, tech.suspicion + 0.012F);
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
		s.timer("dock", InteriorJump.length() + 20);
		InteriorJump.story(level, s.falcon);
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

	/** Mission 3: Hale goes as he came. */
	static void haleLeaves(StorySession s) {
		Figure hale = s.figure("hale");
		s.set("tmp.hale_gone");

		if (hale != null) {
			walk(hale, new Vec3(46.6, H, -11.2), new Vec3(45.2, H, -13.0));
		}
	}

	/** Mission 4: the technician did not like an answer. */
	static void techSuspects(StorySession s, float suspicion) {
		Figure tech = s.figure("tech");

		if (tech != null) {
			tech.suspicion = Math.max(tech.suspicion, suspicion);
		}
	}

	/** Mission 4: the password: the technician goes off to Harlo (by the lift). */
	static void techLeaves(StorySession s) {
		leaveTech(s);
		done(s, "m4.tech");
	}

	/** Mission 4: a bluff: the technician stays and watches. */
	static void techWatches(StorySession s) {
		s.set("tech.watch");
		techSuspects(s, 0.3F);
		done(s, "m4.tech");
	}

	/** Mission 4: refused: the technician raises the alarm and runs. */
	static void techAlarm(StorySession s) {
		s.raiseAlarm(Stealth.SEARCH + Stealth.LOSE);
		Story.cue(s, Story.CUE_SOUND, "", "story.alarm", 0);
		leaveTech(s);
		done(s, "m4.tech");
	}

	private static void leaveTech(StorySession s) {
		Figure tech = s.figure("tech");

		if (tech != null) {
			// Round the core (never through it), to the lift.
			walk(tech, new Vec3(48.4, R, 23.1), ring(-60), ring(-90), ring(-120), new Vec3(32.6, R, 23.1), new Vec3(29.4, R, 25.0));
			s.set("tmp.leave.tech");
		}
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

	/** Mission 5: Hale's trap: into cell 2; Hale and his troopers leave. */
	static void trapped(StorySession s) {
		s.set("tmp.trapped");
		Story.cue(s, Story.CUE_FADE, "", "sabercraft.story.hint.trapped", 30);
		Story.cue(s, Story.CUE_SOUND, "", "story.caught", 0);
		s.figures.removeIf(figure -> figure.key.equals("hale") || figure.key.equals("trap1") || figure.key.equals("trap2"));
		field(s, 1, false);
		Story.place(s, D, CELL_2, 0.0F);
		s.hint("sabercraft.story.hint.trapped");
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
		Story.cue(s, Story.CUE_SOUND, "", "story.alarm", 0);
		s.radio("kestrel", "sabercraft.story.radio.saber_taken");
		done(s, "m6.saber");
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

		switch (step.id()) {
			case "m5.route" -> {
				if (s.has("trust.hale")) {
					return at(D, DestroyerInterior.CX - 3.5, H + 1.0, 4.0);
				}

				return s.has("know.hale_trap") || s.has("hatch.f") ? at(D, 22.6, H + 0.5, -3.5) : at(D, 18.0, H + 1.1, -1.4);
			}
			case "m5.cell" -> {
				return null;
			}
			case "m5.cell1" -> {
				return s.has("cell1.open") ? at(D, 27.0, H + 1.0, 0.0) : step.target();
			}
			case "m6.reactor" -> {
				return s.local(D).y > H - 2 ? step.target() : null;
			}
			case "m6.duel" -> {
				Figure shadow = s.figure("shadow");
				return shadow != null ? new Mission.Target(D, shadow.position().add(0.0, 2.2, 0.0)) : null;
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

		int m = s.progress.mission;

		if (x == DestroyerInterior.CX + 4 && y == H && z == -14) {
			// The armory: the security desk opens it.
			return !s.has("armory.open");
		}

		if (x == DestroyerInterior.CX - 4 && y == H && z == 3) {
			// The detention block: open only for the one Hale expects.
			return !(s.has("trust.hale") && s.at("m5.route"));
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
	public boolean dark(StorySession s, String ship, double x, double y, double z) {
		if (!D.equals(ship)) {
			return false;
		}

		// The maintenance room and the airlock: always (a few dim lamps).
		if (x >= 1.0 && x <= 13.0 && z >= -9.0 && z <= 13.0 && y >= H - 1 && y <= H + 4) {
			return true;
		}

		// After the download the ship's lights are low: the lobby, the corridor, the gallery, the hidden room.
		return s.progress.mission == 4 && (LOBBY_DARK.contains(x, y, z) || HIDDEN.inflate(1.0).contains(x, y, z));
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
