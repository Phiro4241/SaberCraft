package com.phiro.sabercraft.story;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import com.phiro.sabercraft.character.Outfit;
import com.phiro.sabercraft.character.OutfitSync;
import com.phiro.sabercraft.droid.DroidType;
import com.phiro.sabercraft.droid.Droids;
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
	static final int H = DestroyerInterior.HANGAR + 1;
	private static final int R = DestroyerInterior.REACTOR_BOTTOM;
	private static final int FF = FalconInterior.FLOOR + 1;

	// Places (plot space).
	static final Vec3 FALCON_LOUNGE = new Vec3(21.5, FF, 28.5);
	static final Vec3 FALCON_COCKPIT = new Vec3(43.5, FF, 25.5);
	static final Vec3 FALCON_ENTRY = new Vec3(35.5, FF, 31.5);
	static final Vec3 AIRLOCK = new Vec3(6.5, H, 10.0);
	/**
	 * Phase 27: the airlock's checkpoint: a little farther from its inner door (at 10.0 the player
	 * was put within its reach, and the door to the maintenance room opened at once).
	 */
	static final Vec3 AIRLOCK_SAFE = new Vec3(6.5, H, 10.7);
	static final Vec3 HIDEOUT = new Vec3(20.5, H, -4.5);
	static final Vec3 REACTOR_FLOOR = new Vec3(28.8, R, 25.0);
	/**
	 * Phase 24: where the lab's guard stands (east of its door), and how near the droid's beeping at
	 * its lock is heard. (Phase 25: Phase 24's place, 48.4/35.6, was past the shaft's curved wall:
	 * he stood inside it, unseen, and the door looked unguarded. Now on the platform by the way to
	 * the door, his back to it: one comes along the platform from the west behind him, or M-31
	 * lures him off; from the bridges in the north and east he sees one coming.)
	 */
	static final Vec3 LAB_GUARD = new Vec3(41.8, R, 35.4);
	/**
	 * Phase 26: the lab guard's watch. Phase 27: Phase 26's post east of the way to the door (46.2,
	 * 34.0) had the only way there and the door always behind him: he never saw anyone, and read as
	 * a statue. Now he stands west of the way to the door, where one comes along the platform, and
	 * looks in turn along the platform to the west (who comes over the bridges?), over the shaft and
	 * to the east bridge, then back: while he looks east, one gets by behind him (or M-31 draws him
	 * off, or one overpowers him from behind). Yaws and how long he holds each look (ticks).
	 */
	static final float LAB_GUARD_WAY_YAW = 105.0F;
	static final float LAB_GUARD_SHAFT_YAW = 160.0F;
	static final float LAB_GUARD_BRIDGE_YAW = 218.0F;
	static final float[][] LAB_WATCH = {{LAB_GUARD_WAY_YAW, 80.0F}, {LAB_GUARD_SHAFT_YAW, 45.0F}, {LAB_GUARD_BRIDGE_YAW, 140.0F}, {LAB_GUARD_SHAFT_YAW, 45.0F}};
	/** Phase 27: how far the lab's guard hears M-31 (a little further than most: he listens on watch). */
	static final double LAB_GUARD_HEARING = 9.0;
	/** Phase 26: the lab guard overpowered (kept with the checkpoints after it, as the detention's guard). */
	static final String LAB_GUARD_DOWN = "lab_guard.down";
	static final double LAB_DOOR_HEARD = 4.5;
	static final Vec3 CELL_2 = new Vec3(31.5, H, 0.0);
	static final Vec3 LIFT_TOP = new Vec3(28.8, H, 25.0);
	/** Phase 20: the containers of the dead drop (their mark, the west end of each along x), the contact's is K-7. */
	static final String[][] CRATES = {{"c3", "20"}, {"f9", "23"}, {"k7", "26"}};

	static {
		// Phase 29: every thing one can use in this chapter (its words: sabercraft.story.point.<id>).
		Mission.Point.declare("sign", "pad", "security", "locker", "locker2", "card", "console", "coolant", "recorder", "cell1", "cell3", "lab_door",
				"holo", "saber", "terminal", "crate_c3", "crate_f9", "crate_k7", "takedown", "service_release", "service_lock", "vent_cover", "toolbox", "service_door");
	}

	/** Phase 30: the maintenance door's face on the walkway's side (a click on it begins the release, as one on the release does). */
	static final AABB SERVICE_DOOR_FACE = new AABB(48.0, R, DestroyerInterior.SERVICE_DOOR_Z + 0.6, 50.0, R + 2.2, DestroyerInterior.SERVICE_DOOR_Z + 1.05);
	/**
	 * Phase 30: behind the maintenance door (plot space): its chamber, the hatch in the chamber's
	 * north wall and the crawl way beyond it (where the way on leads once the door is bolted).
	 */
	static final AABB SERVICE_INSIDE = DestroyerInterior.SERVICE_ROOM.minmax(DestroyerInterior.SERVICE_VENT);
	/** Phase 20: by the stack of crates on the hangar's east side. */
	static final Vec3 HANGAR_CRATES = new Vec3(57.3, H, -27.9);
	/**
	 * Phase 27: the checkpoint with the stick: the hangar's south-west corner beside the
	 * containers, out of the patrols' way; and the flight control officer's round between the
	 * consoles (he stood still, facing the door and the hatch: no way past him but a lure).
	 */
	static final Vec3 CRATE_SAFE = new Vec3(18.6, H, -64.3);
	static final Vec3 FLIGHT_BEAT_SOUTH = new Vec3(12.0, H, -43.0);
	static final Vec3 FLIGHT_BEAT_NORTH = new Vec3(12.0, H, -55.0);
	static final double FLIGHT_BEAT_SPEED = 0.6;
	/** Phase 27: how far the flight control officer hears M-31 (from the door, a lure reaches him). */
	static final double FLIGHT_HEARING = 10.0;
	/**
	 * Phase 28: the technician on the reactor's catwalk (the deck level, by the bridge to the lift)
	 * in the last mission. He stood at his console facing the bridge, the only way to the lift, and
	 * heard a lure only from on the bridge (and then went a step and stood again). Now he works a
	 * round at his post: at the console (facing the bridge), then turned to the readings on the
	 * shaft wall behind him (his back to the bridge: one gets by), and back ({@code CATWALK_WATCH}:
	 * yaws and ticks held); he hears further, and a lure takes him off his post a while (over the
	 * walkways, see {@code Stealth.lureSpot}). A patrol of his own would only block the one bridge.
	 */
	static final Vec3 CATWALK_POST = new Vec3(DestroyerInterior.CATWALK_DESK.getX() + 0.5, H, DestroyerInterior.CATWALK_DESK.getZ() - 0.95);
	static final float[][] CATWALK_WATCH = {{0.0F, 170.0F}, {172.0F, 140.0F}};
	static final int CATWALK_TURN = 22;
	static final double CATWALK_HEARING = 10.5;
	/** Phase 28: the gallery's landing and its way in: where the player first sees him (a hint, once). */
	static final AABB CATWALK_SEEN = new AABB(43.5, H - 0.5, 9.5, 50.5, H + 2.0, 17.9);
	/**
	 * Phase 28: Mission 2's side way (Phase 27's crawl way from the hangar's west wall) is shut at
	 * its west end, where it meets the crawl way from flight control, by a cover screwed on from
	 * the hangar's side: on the way in one finds it shut (screwed tight from the other side). In a
	 * toolbox by the containers lies a technician's screwdriver; with it the cover comes off from
	 * the hangar's side (four screws), and stays off. Both are kept at once (a capture, death, the
	 * next mission keep them); neither is needed: the way through flight control stays open, and
	 * the toolbox is there in every mission until the screwdriver is taken.
	 */
	public static final BlockPos VENT_COVER = new BlockPos(11, H, -29);
	public static final BlockPos TOOLBOX = new BlockPos(29, H, -64);
	public static final String COVER_OPEN = "vent.cover_open";
	public static final String DRIVER = "item.driver";
	/** Phase 28: from how far (the eye to the cover's face) the screws can be reached. */
	static final double COVER_REACH = 2.6;
	/** Phase 20: safe places: the maintenance room's corner by the crates (behind the guard at Kestrel's hatch), the armory once its master is gone, the crawl way at the detention block's hatch. */
	static final Vec3 MAINT_SAFE = new Vec3(3.5, H, -5.5);
	static final Vec3 ARMORY_SAFE = new Vec3(50.5, H, -7.5);
	static final Vec3 LOCKERS_SAFE = new Vec3(54.2, H, -10.5);
	static final Vec3 VENT_DETENTION = new Vec3(24.5, H, 6.4);
	/** Phase 20: the reactor floor's wall comm (by the lift) and the caf dispenser beside it. */
	static final Vec3 REACTOR_COMM = new Vec3(30.4, R, 21.2);
	static final Vec3 CAF = new Vec3(30.9, R, 28.6);
	/**
	 * Phase 20: by the cell console where R2-V7 was. Phase 27: behind the pillar on the block's west
	 * side (at 27.5/7.6 the guard, since Phase 24 at his post by the door, looked straight at it
	 * from his room look and caught the player within moments).
	 */
	static final Vec3 CONSOLE_SAFE = new Vec3(27.3, H, 5.5);
	/** Phase 20: the guard's post in front of Kestrel's hatch (Mission 1), facing into the room. */
	static final Vec3 HATCH_POST = new Vec3(10.5, H, -7.35);
	/**
	 * Phase 20: the detention block out of the cell (a file of Phase 19), and where M-31 waits by the
	 * bench. Phase 27: behind the pillar as {@link #CONSOLE_SAFE} (in the corridor at 31.5/3.6 the
	 * guard saw the player from both of his looks).
	 */
	static final Vec3 DETENTION_SAFE = new Vec3(27.3, H, 5.5);
	static final Vec3 BENCH = new Vec3(32.5, H, 8.4);
	/**
	 * Phase 26: where Kestrel waits for the player at the crawl way out of the detention block:
	 * beside its hatch, north of it, against the wall (she stood right in front of it, half in the
	 * way in, at 26.3/6.5).
	 */
	static final Vec3 KESTREL_AT_VENT = new Vec3(26.45, H, 4.55);
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
				cp("m1.droid", D, AIRLOCK_SAFE, 180.0F, at(D, 2.6, H + 0.4, -6.0)),
				cp("m1.distract", D, MAINT_SAFE, 90.0F, at(D, 10.5, H + 1.9, -7.4)),
				step("m1.hatch", D, MAINT_SAFE, 90.0F, at(D, 10.5, H + 0.5, -8.6))),
				List.of(new Mission.Optional("pad", s -> true)));

		Mission m2 = new Mission("m2", List.of(
				cp("m2.kestrel", D, HIDEOUT, 90.0F, kestrel),
				cp("m2.terminal", D, HIDEOUT, 90.0F, at(D, 18.0, H + 1.1, -1.4)),
				cp("m2.drop", D, HIDEOUT, 90.0F, at(D, 10.5, H + 0.5, -34.0)),
				// Phase 27: a checkpoint by the containers with the stick (caught on the way back, one
				// had to find the right container all over again).
				cp("m2.return", D, CRATE_SAFE, 180.0F, kestrel)),
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
				cp("m5.cell1", D, CONSOLE_SAFE, 180.0F, at(D, 27.5, H + 1.0, 2.5)),
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
			list.addAll(List.of("droid.r2v7", "hatch.f", "kestrel.free", "know.hale_trap", KNOW_TAKEDOWN));
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
		// Phase 31: caught breaking out of Hale's cell: back in it (said so, with what to do).
		if (s.has("m5.trapped") && s.at("m5.cell")) {
			return "sabercraft.story.radio.caught.m5_cell";
		}

		return "sabercraft.story.radio.caught." + s.mission().id();
	}

	/**
	 * Phase 31: the steps of the break-out from Hale's cell (trusting him: the trap). From the trap
	 * until the hideout ends Mission 5 the cell's checkpoint is the only one: caught anywhere on the
	 * way (in the block, its lobby, Kestrel's crawl way), one is put back into the cell with
	 * everything as it was then (the field on, R2-V7 restrained, Kestrel in her cell, the guard at
	 * his post, the door locked). Before, freeing R2-V7 or Kestrel made checkpoints of their own
	 * behind the pillar in the block, so being caught after it put one there, not in the cell.
	 */
	static final java.util.Set<String> PRISON_STEPS = java.util.Set.of("m5.r2", "m5.cell1", "m5.back");

	/** Phase 31: whether the player is breaking out of Hale's cell now (out of it, or about to be). */
	static boolean prisonBreak(StorySession s) {
		return s.has("m5.trapped") && (s.at("m5.cell") && s.has("cell2.open") || PRISON_STEPS.contains(s.step().id()));
	}

	@Override
	public boolean holdsCheckpoint(StorySession s) {
		return prisonBreak(s);
	}

	/**
	 * Phase 31: a file saved by an older version in the middle of the break-out (at the checkpoint
	 * made on freeing R2-V7 or Kestrel, behind the pillar) goes back into the cell, as the break-out
	 * now does: the cell's step and place, and nothing of the break-out done yet.
	 */
	static boolean prisonSave(StoryProgress progress) {
		if (progress.mission != 4 || !progress.flags.contains("m5.trapped") || !PRISON_STEPS.contains(progress.stepId)) {
			return false;
		}

		Mission mission = INSTANCE.missions().get(4);
		int cell = mission.indexOf("m5.cell");

		if (cell < 0) {
			return false;
		}

		progress.step = cell;
		progress.stepId = "m5.cell";
		progress.flags.removeAll(java.util.List.of("cell2.open", "droid.r2v7", "cell1.open", "cell3.open", "kestrel.free", "hatch.f", GUARD_DOWN,
				"opt.prisoner", "gone.prisoner"));
		progress.placeShip = D;
		progress.placeX = CELL_2.x;
		progress.placeY = CELL_2.y;
		progress.placeZ = CELL_2.z;
		progress.placeYaw = 0.0F;
		return true;
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
		// Phase 27: fewer or more enemies by the difficulty.
		byDifficulty(s, list);
		// Phase 31: those overpowered lie where they fell.
		downs(s, list);
		return list;
	}

	/**
	 * Phase 27: the difficulty's enemies. The figures are made at a checkpoint or for a new step,
	 * so nobody appears or vanishes in the middle of a scene. Easy: the hangar's patrol walks
	 * alone (its second trooper stays away) and two of the finale's fighters are missing. Hard: a
	 * second patrol walks each patrol route of the hangar deck half a round behind the first (the
	 * routes are walkable, so they never stand in a wall), and the finale's sections have a
	 * fighter more beside some of the others (only where there is room).
	 */
	private static void byDifficulty(StorySession s, List<Figure> list) {
		int more = s.difficulty().enemies;

		if (more == 0) {
			return;
		}

		if (more < 0) {
			list.removeIf(figure -> EASY_AWAY.contains(figure.key) || figure.key.startsWith("crew:") && pairedPatrol(figure));
			return;
		}

		List<Figure> twins = new ArrayList<>();

		for (Figure figure : list) {
			Crew c = figure.crew;

			if (figure.key.startsWith("crew:") && Crew.TROOPER.equals(c.kind()) && "patrol".equals(c.role()) && c.path().size() > 1 && c.y() < 100.0) {
				Crew twin = new Crew(c.kind(), c.pose(), c.x(), c.y(), c.z(), c.yaw(), c.path(), c.speed(), c.phase() + loop(c.path()) / 2.0, c.role());
				Figure extra = new Figure(0, figure.key + ":hard", twin);
				extra.ship = figure.ship;
				extra.observer = figure.observer;
				extra.talks = false;
				extra.lamp = figure.lamp;
				twins.add(extra);
			}
		}

		for (Figure figure : list) {
			if (figure.hostile && HARD_TWINS.contains(figure.key)) {
				Vec3 at = room(s, figure.ship, figure.position());

				if (at != null) {
					Figure extra = fighter(figure.key + ":hard", at.x, at.y, at.z, figure.yaw);
					extra.post = figure.post;
					twins.add(extra);
				}
			}
		}

		list.addAll(twins);
	}

	/** Phase 27: on Easy these stay away (two of the finale's fighters). */
	static final Set<String> EASY_AWAY = Set.of("h2", "h8");
	/** Phase 27: on Hard these have a fighter beside them. */
	static final Set<String> HARD_TWINS = Set.of("h1", "h3", "h5", "h7");

	/** Phase 27: the second of a patrol walking in a pair (the same route a step behind another). */
	private static boolean pairedPatrol(Figure figure) {
		Crew c = figure.crew;

		if (!"patrol".equals(c.role()) || c.path().size() < 2 || c.phase() <= 0.0) {
			return false;
		}

		for (Crew other : DestroyerInterior.CREW) {
			if (other != c && other.path().equals(c.path()) && other.phase() < c.phase() && c.phase() - other.phase() < 3.0) {
				return true;
			}
		}

		return false;
	}

	/** Phase 27: how long a closed route is (blocks). */
	static double loop(List<Vec3> path) {
		double total = 0.0;

		for (int i = 0; i < path.size(); i++) {
			total += path.get(i).distanceTo(path.get((i + 1) % path.size()));
		}

		return total;
	}

	/** Phase 27: a free spot a step beside that place (feet and head in the air, floor below), or null. */
	private static @Nullable Vec3 room(StorySession s, String ship, Vec3 near) {
		ServerLevel level = interiors(s);

		if (level == null) {
			return null;
		}

		BlockPos origin = s.origin(ship);

		for (Vec3 offset : List.of(new Vec3(1.3, 0.0, 0.0), new Vec3(-1.3, 0.0, 0.0), new Vec3(0.0, 0.0, 1.3), new Vec3(0.0, 0.0, -1.3))) {
			Vec3 at = near.add(offset);
			BlockPos feet = origin.offset(BlockPos.containing(at.x, at.y + 0.1, at.z));

			if (level.getBlockState(feet).getCollisionShape(level, feet).isEmpty() && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()
					&& !level.getBlockState(feet.below()).getCollisionShape(level, feet.below()).isEmpty()) {
				return at;
			}
		}

		return null;
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
				if (m == 5 && !fight) {
					// Phase 24: on the way to the lab he oversees the technician on the east platform
					// (at his console by the lab's walkway, with the guard there and the droid's beeping,
					// the way in was too hard): the crossing to the lab is one guard to get past.
					routine = Crew.at(Crew.OFFICER_OLIVE, Crew.STAND, 50.2, R, 22.4, 300.0F);
				} else if ("m4.download".equals(step)) {
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

			// Phase 27: in Mission 2 the flight control officer walks a round between the consoles
			// (going north, the door and the crawl way's hatch are behind him), and hears further.
			boolean flight = m == 1 && Crew.OFFICER_OLIVE.equals(c.kind()) && "flight_control".equals(c.role());

			if (flight) {
				routine = Crew.patrol(Crew.OFFICER_OLIVE, FLIGHT_BEAT_SPEED, 0.0, FLIGHT_BEAT_SOUTH, FLIGHT_BEAT_NORTH).as("flight_control");
			}

			Figure figure = new Figure(0, "crew:" + i, routine);
			figure.ship = D;
			figure.observer = true;
			figure.talks = false;

			if (flight) {
				figure.hearing = FLIGHT_HEARING;
			}

			// Phase 28: the catwalk's technician in the last mission listens further (a lure from the gallery reaches him).
			if (m == 5 && reactor && !bottom && Crew.TECH.equals(c.kind())) {
				figure.hearing = CATWALK_HEARING;
			}
			// After the download the lights are low: the troops carry lamps.
			figure.lamp = m == 4 && Crew.TROOPER.equals(c.kind());
			list.add(figure);
		}
	}

	/** The people of the story aboard the Star Destroyer. */
	private static void people(StorySession s, List<Figure> list, int m, String step) {
		// Kestrel: in the hidden room (Missions 2 to 4, and 6), in cell 1 (Mission 5). Phase 27: in
		// Mission 1 too (she waits there for the player: one crawling in found her appearing out of
		// nothing as they came out of the way), and in cell 1 before the detention block's door opens.
		if (m >= 0 && m <= 3 || m == 5 && ("m6.plan".equals(step) || "m6.reactor".equals(step))) {
			list.add(person("kestrel", "kestrel", Crew.at(Crew.REBEL, Crew.SIT, 15.0, H, -5.5, 0.0F), D));
		} else if (m == 4 && ("m5.route".equals(step) || "m5.cell".equals(step) || "m5.r2".equals(step) || "m5.cell1".equals(step))) {
			list.add(person("kestrel", "kestrel", Crew.at(Crew.REBEL, Crew.STAND, 27.0, H, 0.0, 0.0F), D));
		} else if ("m5.back".equals(step)) {
			list.add(person("kestrel", "kestrel", Crew.at(Crew.REBEL, Crew.STAND, KESTREL_AT_VENT.x, H, KESTREL_AT_VENT.z, 315.0F), D));
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
					// (Phase 25: in the alarm he keeps to his comm and calls no more troops: the same
					// number come each time, also after a capture.)
					Figure tech = tech(s, step);
					tech.observer = false;
					list.add(tech);
				}

				if ("m5.escape".equals(step)) {
					// Phase 25: a guard walks the north platform, across the gap from the maintenance
					// door: on his way east he sees the walkway and the door, on his way back he has his
					// back to them. He cannot get over to the door (the gap), only see and shoot; M-31
					// can lure him off to the north bridge, or one waits until he turns.
					Figure north = guard("svc_guard", Crew.patrol(Crew.TROOPER, 0.55, 0.0, Scenes.NORTH_GUARD, new Vec3(37.8, R, 13.4)));
					north.lamp = true;
					list.add(north);
				}

				// After the download: lamps in the dark lobby.
				Figure lobby = guard("m5_lobby", Crew.patrol(Crew.TROOPER, 0.8, 0.0, new Vec3(37.5, H, -1.0), new Vec3(42.0, H, -1.0), new Vec3(42.0, H, 4.6),
						new Vec3(37.5, H, 4.6)));
				lobby.lamp = true;
				list.add(lobby);

				// Phase 27: the detention block's people are there before its door opens (they appeared
				// on the threshold, the step going on as the player stepped in: as in the hangar before
				// Phase 25). On the way there they wait: nobody sees through the door yet, nobody talks.
				boolean route = "m5.route".equals(step);

				if ((route || "m5.cell".equals(step)) && s.has("trust.hale") && !s.has("m5.trapped")) {
					// The trap: Hale waits in the detention block with two troopers (Phase 20: they
					// leave once the player is locked in).
					Figure hale = person("hale", "hale", Crew.at(Crew.OFFICER_BLACK, Crew.STAND, 33.5, H, 4.2, 270.0F), D);
					Figure trap = guard("trap1", Crew.at(Crew.TROOPER, Crew.STAND, 34.6, H, 3.2, 270.0F));

					if (route) {
						hale.talks = false;
						trap.observer = false;
					}

					list.add(hale);
					list.add(trap);
				}

				if (route || step.equals("m5.cell") || step.equals("m5.r2") || step.equals("m5.cell1") || step.equals("m5.back")) {
					// The detention block: Phase 20: one guard, at the entrance, watching the door
					// (his back to the cells: who would come out of them?). The officer went with Hale.
					// Phase 24: by the east end, between the door and the bench, where he sees the door,
					// the corridor, the cells and the crawl way's hatch by turning his head (he stood with
					// his face to the wall beside the door). Overpowered, he lies where he fell.
					Figure guard = guard("det_guard", Crew.at(Crew.TROOPER, Crew.STAND, GUARD_POST.x, H, GUARD_POST.z, GUARD_DOOR_YAW));

					if (route) {
						guard.observer = false;
					}

					if (s.has(GUARD_DOWN)) {
						guard.mode = Figure.DOWN;
						guard.observer = false;
					}

					list.add(guard);

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
				// Phase 26: nor does he when he is on his watch: he guards the lab's door for as long
				// as the saber lies in the lab (he went out of the world as the door opened behind
				// him, the step going on to the lab). Overpowered, he lies where he fell.
				if (!s.has("saber.taken") && (step.equals("m6.reactor") || step.equals("m6.door") || step.equals("m6.lab") || step.equals("m6.saber"))) {
					// Phase 24: a few steps east of the lab's door (he stood right before it), watching
					// the shaft and the east walkway: one gets past him with care, or lures him off.
					Figure guard = guard("lab_guard", Crew.at(Crew.TROOPER, Crew.STAND, LAB_GUARD.x, R, LAB_GUARD.z, LAB_GUARD_WAY_YAW));
					// Phase 27: a sentry: he hears M-31 a little further, and seeing one close he fires first.
					guard.sentry = true;
					guard.hearing = LAB_GUARD_HEARING;

					if (s.has(LAB_GUARD_DOWN)) {
						guard.mode = Figure.DOWN;
						guard.observer = false;
					}

					list.add(guard);
				}

				hostiles(s, list, step);

				// Phase 21: he waits with his back to the player (the film turns him round); after the duel he is gone.
				// Phase 25: she is in the hangar already while the player fights their way there (no
				// figure appears out of nothing when the hangar's door opens); she does not fight before the duel.
				if ((step.equals("m6.duel") || step.equals("m6.escape2")) && !s.has("gone.shadow")) {
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
				// (Phase 26: unless he was overpowered before: then he still lies where he fell.)
				if (s.has(LAB_GUARD_DOWN)) {
					Figure down = guard("lab_guard", Crew.at(Crew.TROOPER, Crew.STAND, LAB_GUARD.x, R, LAB_GUARD.z, LAB_GUARD_WAY_YAW));
					down.mode = Figure.DOWN;
					down.observer = false;
					list.add(down);
				} else {
					list.add(fighter("lab_guard", 44.5, R, 35.5, 0.0F));
				}

				if (!s.has("opt.coolant")) {
					list.add(fighter("h3", 31.0, R, 21.0, 300.0F));
				}

				// Phase 24: the corridor out of the reactor is guarded already (they wait at their posts
				// up there, unseen from below, and are not set down out of nowhere when one comes up).
				corridorGuards(list);
			}
			case "m6.escape2" -> {
				corridorGuards(list);
				// Phase 25: the lobby's and the hangar corridor's troopers hold their posts too (they
				// ran at the player from the start, wherever the player was): one in the lobby facing
				// the corridor one comes out of, one before the hangar's door facing the lobby. With
				// the corridor's two that is four at most, met one or two at a time.
				Figure lobby = fighter("h5", 40.5, H, 1.0, 270.0F);
				lobby.post = true;
				list.add(lobby);

				if (!s.has("opt.armour2")) {
					Figure door = fighter("h6", 40.5, H, -9.0, 0.0F);
					door.post = true;
					list.add(door);
				}
			}
			case "m6.falcon" -> {
				// Phase 27: in the corridor behind the hangar's door: they come running in through it
				// after the boss is gone (they stood in the hangar at once, there from nothing).
				list.add(fighter("h7", 39.0, H, -13.0, 180.0F));
				list.add(fighter("h8", 42.0, H, -13.0, 180.0F));
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

	/**
	 * Phase 24: two troopers guard the narrow corridor from the reactor to the lobby (one by its
	 * far end, one half way, a little apart, both facing the way one comes): they hold their posts
	 * until they see the player (see {@link Finale}), shout, and fight.
	 */
	private static void corridorGuards(List<Figure> list) {
		for (Vec3 at : CORRIDOR_GUARDS) {
			Figure guard = fighter(at == CORRIDOR_GUARDS.get(0) ? "h4" : "h4b", at.x, H, at.z, 0.0F);
			guard.post = true;
			list.add(guard);
		}
	}

	/** Phase 24: the corridor's guards' posts (plot space). */
	static final List<Vec3> CORRIDOR_GUARDS = List.of(new Vec3(48.0, H, 9.0), new Vec3(47.3, H, 4.2));

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
					// (Phase 26: where the desk stands now, half a block further north.)
					list.add(point("security", 43.1, H, DestroyerInterior.SECURITY_DESK_Z - 1.0, 44.0, H + 1.2, DestroyerInterior.SECURITY_DESK_Z + 1.0, false));
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
				// Phase 25: the maintenance door's emergency release, and the panel inside that bolts it.
				if (step.equals("m5.escape") && !s.has("tmp.film")) {
					if (!s.has("service.open") && !s.has("tmp.release")) {
						list.add(new Mission.Point("service_release", D, DestroyerInterior.SERVICE_RELEASE, false));
						// Phase 30: a click on the shut door itself begins the release too.
						list.add(new Mission.Point("service_door", D, SERVICE_DOOR_FACE, false));
					} else if (s.has("service.open") && !s.has("service.locked")) {
						list.add(new Mission.Point("service_lock", D, DestroyerInterior.SERVICE_LOCK, false));
					}
				}

				if (step.equals("m5.recording")) {
					list.add(point("recorder", 22.1, H + 1.0, -4.9, 22.9, H + 1.4, -4.1, false));
				}

				if (step.equals("m5.cell1") && !s.has("cell1.open")) {
					list.add(point("cell1", 26.0, H, 2.0, 29.0, H + 3.0, 3.0, true));
				}

				if (s.has("droid.r2v7") && !s.has("cell3.open")) {
					list.add(point("cell3", 34.0, H, 2.0, 36.0, H + 3.0, 3.0, true));
				}

				// (Phase 24: the guard, from close behind: Phase 31: under the crosshair as anyone
				// who can be overpowered, see takedownReady.)
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

			// Phase 28: the side way's cover (while shut) and the toolbox (while the screwdriver is in it).
			if (!s.has(COVER_OPEN)) {
				list.add(point("vent_cover", VENT_COVER.getX(), H, VENT_COVER.getZ(), VENT_COVER.getX() + 1.0, H + 1.0, VENT_COVER.getZ() + 1.0, false));
			}

			if (!s.has(DRIVER)) {
				list.add(point("toolbox", TOOLBOX.getX() + 0.1, H, TOOLBOX.getZ() + 0.15, TOOLBOX.getX() + 0.9, H + 0.75, TOOLBOX.getZ() + 0.85, false));
			}
		}

		return list;
	}

	private static Mission.Point point(String id, double x0, double y0, double z0, double x1, double y1, double z1, boolean hack) {
		return new Mission.Point(id, D, new AABB(x0, y0, z0, x1, y1, z1), hack);
	}

	@Override
	public void use(StorySession s, Mission.Point point) {
		// Phase 26: what is worked has its own little sound: a console's keys, a locker's latch (not
		// when someone watches: then nothing is done, see unwatched).
		String sound = switch (point.id()) {
			case "security", "console", "terminal", "recorder", "holo" -> "story.console";
			case "locker", "locker2" -> "story.latch";
			default -> null;
		};

		if (sound != null && !(UNWATCHED.contains(point.id()) && watched(s))) {
			Story.cue(s, Story.CUE_SOUND, "", sound, 0);
			mechanical++;
		}

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
			case "service_release", "service_door" -> Scenes.release(s);
			case "service_lock" -> Scenes.bolt(s);
			case "toolbox" -> unwatched(s, "m2_toolbox");
			case "vent_cover" -> coverUsed(s);
			default -> {
			}
		}
	}

	/** Phase 26: the points that only work while nobody watches (see unwatched). */
	private static final java.util.Set<String> UNWATCHED = java.util.Set.of("security", "locker", "locker2", "card", "coolant", "crate_k7");
	private static int mechanical;

	/** For the tests: how many console or latch sounds were played on using a point. */
	public static int mechanicalForTest() {
		return mechanical;
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

	/**
	 * Phase 24: the talks one has with people in passing (no step waits for them): not while the
	 * troopers are after one or shoot (the reactor's technician saying he is busy, in a firefight).
	 */
	static final Set<String> SMALL_TALK = Set.of("m1_mira_wait", "dax_idle", "rorrak_idle", "kestrel_idle", "checkpoint_closed", "quartermaster", "tech_idle");

	@Override
	public boolean talksNow(StorySession s, Figure figure) {
		if (!s.danger()) {
			return true;
		}

		String dialog = this.dialogFor(s, figure);
		return dialog == null || !SMALL_TALK.contains(dialog);
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

		String dialog = this.dialogFor(s, figure);

		if (dialog == null || !this.talksNow(s, figure)) {
			return false;
		}

		if (step.equals("m6.jump") && figure.key.equals("dax")) {
			s.set("tmp.jump_go");
		}

		s.talk(dialog, figure);
		return s.dialog != null;
	}

	/** The dialog speaking to someone opens now (during the chapter), or null. */
	@Nullable String dialogFor(StorySession s, Figure figure) {
		String step = s.step().id();

		if (s.has(CHAPTER_DONE)) {
			return afterwards(s, figure);
		}

		return switch (figure.key) {
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
				// caught: the checkpoint in the cell has it there again. Phase 26: it drives to the
				// field's emitter and works on it a moment; the field stutters, then dies (CellField).
				return CellField.start(s, player);
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
				// Phase 26: heard where the droid is: its arm whirring out, the latch, the data chirps.
				com.phiro.sabercraft.droid.DroidEntity r2 = Droids.droid(player, DroidType.R2D2);
				Vec3 from = r2 != null && r2.level() == level ? r2.position() : player.position();
				level.playSound(null, from.x, from.y + 0.5, from.z, ShipInteriors.sound("story.droid_work"), SoundSource.NEUTRAL, 0.7F,
						0.95F + level.getRandom().nextFloat() * 0.1F);

				// Someone close by hears it beep (Phase 24: the lab's door is a quiet lock: only right by it).
				double heard = lock.id().equals("lab_door") ? LAB_DOOR_HEARD : 7.0;

				for (Figure figure : s.figures) {
					if (figure.observer && s.here.equals(figure.ship) && figure.position().distanceTo(lock.box().getCenter()) < heard
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

		// Phase 27: the bridge is no place of the story: its lift's bay is fenced off.
		sealBridge(s);

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
				// Phase 25: Hale comes in at once, as a short scene (the door opens for him, shuts and
				// is bolted behind him, he stops before the player, the talk begins). The quartermaster
				// was paged out before (Phase 23); nothing waits for him or for where the player is.
				Figure hale = s.figure("hale");

				if (hale != null) {
					Scenes.haleBegin(s);
				} else if (!s.has("qm.away")) {
					pageQuartermaster(s);
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
				// Phase 25: he knows where the intruder is: he hunts the player (a chase from the start,
				// he comes round over the bridges); the way out is the maintenance walkway.
				Vec3 me = s.local(D);
				s.huntAt = me;
				s.huntSeen = s.time();

				for (Figure figure : s.figures) {
					if (figure.key.startsWith("crew:") && Crew.TROOPER.equals(figure.kind()) && figure.y < H - 1 && figure.mode == Figure.ROUTINE) {
						figure.mode = Figure.CHASE;
						figure.target = me;
						figure.seenAt = me;
						figure.suspicion = 1.0F;
						figure.stage = Figure.DETECTED;
						figure.cooldown = 30;
					}
				}

				s.raiseAlarm(Stealth.SEARCH_LONG + Stealth.LOSE);
				Scenes.escapeBegin(s, again);
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
					// Phase 24: it is Hale's scene: nobody who still searched follows into it.
					clearStage(s, DestroyerInterior.DETENTION.inflate(6.0));
					s.talk("m5_trap", s.figure("hale"));
				} else if (!s.has("cell2.open")) {
					// Back in the cell: the hint again after a few seconds.
					s.timer("cell_hint", CELL_HINT);
				}
			}
			case "m6.escape1", "m6.escape2", "m6.falcon" -> {
				s.fighting = true;
				Finale.arm(s);

				// Phase 28: after the duel the droids come along again (they waited behind the door).
				if (id.equals("m6.falcon")) {
					droidsBack(s);
				}
				// Phase 23: who still hunted the player from before (a chase in the reactor, a
				// search) fights now too, at once.
				Finale.engageAll(s);

				if (id.equals("m6.escape1") && !again) {
					// Phase 20: the main lights back on: they want to see whom they shoot at.
					s.hear("intercom", "sabercraft.story.shout.lights_on");
				}

				// Phase 24: the intercom on the reactor (once the lights are announced), then: evacuate.
				if (id.equals("m6.escape1")) {
					s.timer("intercom_reactor", 90);
				} else if (id.equals("m6.escape2")) {
					s.timer("intercom_evacuate", 50);
					// Phase 25: her TIE stands in the hangar before its door opens (it was set down
					// only when the duel began, in plain sight).
					s.set("tie.parked");
				}

				s.timer("shudder", 60);

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
				// Phase 24: her TIE stands in the hangar (she escapes in it).
				s.set("tie.parked");
				s.unset(Finale.BOSS_SCENE);
				s.unset(Finale.BOSS_DONE);
				s.unset("tmp.boss_reveal");
				s.timers.remove("boss_mid");
				// Phase 23: the duel is between the two of them: troopers still about from the
				// escape (drawn in, or come as help) fall back.
				s.figures.removeIf(figure -> figure.engaged || figure.key.startsWith(Stealth.REINFORCEMENT));
				Figure shadow = s.figure("shadow");

				if (shadow != null) {
					shadow.mode = Figure.HELD;
					shadow.target = null;
					shadow.lit = false;
				}

				// Phase 28: the droids wait in the corridor behind the hangar's door while the two
				// fight (one followed the player round the arena and through the films' shots).
				droidsAside(s);
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

	/**
	 * Phase 26: while one escapes (from the saber taken until one is aboard), the Falcon waits
	 * under the hangar's force field: the client draws it there (seen through the field), so that
	 * the way out is plain before and during the duel. Not a flag of its own: it follows the step
	 * (so a checkpoint or an earlier step never shows it).
	 */
	static final String FALCON_WAITING = "falcon.waiting";
	/**
	 * Phase 27: only after the duel (the boss flew off through the field, her film is over, the
	 * game goes on): before, her TIE would fly through it, and it was there too early. The client
	 * lets it rise out of the dark below then (HangarFalcon), so it never pops up.
	 */
	static final java.util.Set<String> FALCON_WAITS = java.util.Set.of("m6.falcon");

	static boolean falconWaits(StorySession s) {
		return s.chapter instanceof Chapter1 && !s.has(CHAPTER_DONE) && FALCON_WAITS.contains(s.step().id()) && !s.has("tmp.film");
	}

	/**
	 * Phase 26: the steps of the finale aboard the Star Destroyer: the bridge is sealed off then,
	 * its lift does not go up (Story.liftRefused). Phase 27: through the whole story (the story has
	 * nothing up there, and players got lost on the bridge): a security barrier stands across the
	 * lift's bay in the lobby ({@link #sealBridge}), and the lift refuses as before.
	 */
	static boolean bridgeClosed(StorySession s) {
		return s.chapter instanceof Chapter1;
	}

	/**
	 * Phase 27: the security barrier in front of the turbolift's bay (plot space, the lobby's last
	 * row before the bay's mouth): a force field between two pylons, which collides like a wall (the
	 * client draws it while its cells stand, as the cells' fields).
	 */
	public static final int BRIDGE_BARRIER_X0 = DestroyerInterior.CX - 2;
	public static final int BRIDGE_BARRIER_X1 = DestroyerInterior.CX + 2;
	public static final int BRIDGE_BARRIER_Z = DestroyerInterior.LIFT_Z0 - 1;
	/** Phase 27: in front of the barrier, where the player is told why it stands there. */
	static final AABB BRIDGE_BARRIER_NEAR = new AABB(BRIDGE_BARRIER_X0 - 0.5, H, BRIDGE_BARRIER_Z - 1.4, BRIDGE_BARRIER_X1 + 1.5, H + 2, BRIDGE_BARRIER_Z);

	/** Phase 27: puts up the barrier in front of the lift to the bridge (if it is not there yet). */
	static void sealBridge(StorySession s) {
		ServerLevel level = interiors(s);

		if (level == null || !bridgeClosed(s)) {
			return;
		}

		for (int x = BRIDGE_BARRIER_X0; x <= BRIDGE_BARRIER_X1; x++) {
			for (int y = H; y <= H + 2; y++) {
				BlockPos pos = s.destroyer.offset(x, y, BRIDGE_BARRIER_Z);

				if (level.getBlockState(pos).isAir()) {
					level.setBlock(pos, Blocks.BARRIER.defaultBlockState(), 3);
				}
			}
		}
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
	 * fight to the hangar, the duel, the jump. The client lays the danger's moods over a calm one
	 * (Phase 24: tension, the chase, the search, the calming down after it).
	 */
	static void music(StorySession s, int m, String step) {
		String track = switch (step) {
			case "m1.briefing", "m1.cockpit", "m6.epilogue" -> "music.story_hideout";
			// Phase 24: after the arrival the mission begins: aboard the Star Destroyer, wide awake.
			case "m1.ramp", "m1.droid" -> "music.story_infiltration";
			case "m5.escape" -> "music.story_alarm";
			// Phase 24: the run through the failing ship has its own piece.
			case "m6.escape1", "m6.escape2" -> "music.story_escape";
			// Phase 28 (Phiro): the duel won, but still aboard an enemy Star Destroyer that is breaking
			// apart: the run to the Falcon keeps the escape's driving piece (no triumph yet).
			case "m6.falcon" -> "music.story_escape";
			// The duel's music when the blades are lit. Phase 26: while she speaks a quiet, tense bed
			// (the client keeps it low) instead of silence: the escape's piece crossfades into it and
			// it into the duel's, nothing drops away or cuts in. Phase 28: after her defeat and during
			// her flight the tense bed again (it was the triumphant piece).
			case "m6.duel" -> s.has("tmp.duel_end") || s.has("gone.shadow") ? "music.story_tension"
					: s.has("tmp.duel") || s.has("tmp.film") ? "music.story_duel" : "music.story_tension";
			// Phase 25: aboard, but not away yet: tense until the jump (the escape film has its own
			// piece, rising into D major as the Falcon jumps). Phase 28: only once the Falcon is
			// really away, in hyperspace, the triumphant piece; the epilogue in the lounge is calm.
			case "m6.jump" -> s.has("tmp.jumping") ? "music.story_triumph" : "music.story_launch";
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

		// Phase 28: the side way's cover and the toolbox in the player's own ship (as kept).
		if (s.stepTicks % 20 == 5) {
			ventCover(s);
		}

		// Phase 31: the takedown's scene, whoever it is about.
		takedownScene(s, player, level);

		// Phase 24: the detention block's guard keeps watch (and can be overpowered). Phase 26: so
		// does the lab's guard. Phase 31: behind him, out of the cell, the takedown's tip.
		if (s.mission == 4) {
			guardTick(s, player, level);
			takedownTip(s, player);
		} else if (s.mission == 5) {
			labGuardTick(s, player, level);
			catwalkTick(s, player);
		}

		// Phase 24: from the moment the saber is taken the reactor fails, worse and worse.
		if (s.mission == 5 && s.has("saber.taken")) {
			instability(s, player, level);
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

		if (s.stepTicks % 20 == 7) {
			safeCheckpoint(s, player);
		}

		// Phase 27: at the barrier before the lift to the bridge: why it stands there (now and then).
		if (s.stepTicks % 10 == 3 && s.dialog == null && s.inside(D, BRIDGE_BARRIER_NEAR) && player.tickCount - s.bridgeHint > 600) {
			s.hint("sabercraft.story.hint.bridge_sealed");
			s.bridgeHint = player.tickCount;
		}
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
			case "m2.return" -> {
				// Phase 27: the stick taken and the container shut: Kestrel names the second way back.
				if (s.has("tmp.side_hatch") && s.dialog == null) {
					s.unset("tmp.side_hatch");
					// Phase 28: the side way's cover: open already, the screwdriver taken, or neither.
					s.radio("kestrel", s.has(COVER_OPEN) ? "sabercraft.story.radio.m2_side_hatch_open"
							: s.has(DRIVER) ? "sabercraft.story.radio.m2_side_hatch_tool" : "sabercraft.story.radio.m2_side_hatch");
				}
			}
			case "m1.distract" -> {
				if (fired(s, "droid_hint")) {
					s.hint("sabercraft.story.hint.droid_distract");
				}

				// Past the guard some other way (he turned to look at something): on all the same.
				if (s.inside(D, HIDDEN) || inVent(s.local(D)) && D.equals(s.here)) {
					s.complete();
				}
			}
			case "m1.hatch", "m3.return", "m5.back" -> {
				if (s.inside(D, HIDDEN)) {
					s.complete();
				}
			}
			case "m5.escape" -> {
				// Phase 25: the maintenance way (its release, the crawl way's scene); the old way
				// back to the hideout counts as well.
				Scenes.escapeTick(s);

				if (s.at("m5.escape") && !s.has("tmp.film") && s.inside(D, HIDDEN)) {
					if (!s.has("service.locked")) {
						// The old way: they lost the player all the same (the search goes on).
						Scenes.lostInHideout(s);
					}

					s.complete();
				}
			}
			case "m3.hale" -> {
				if (!Scenes.haleTick(s)) {
					haleScene(s);
				}
			}
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
				// Phase 28: M-31 through the grille (sent by a click on the hatch): a click, it is open.
				if (s.has("tmp.m31_unbolt") && !s.timer("m31_unbolt")) {
					s.unset("tmp.m31_unbolt");

					if (!s.has("hatch.f")) {
						s.set("hatch.f");
						s.hint("sabercraft.story.hint.hatch_free");
						Story.cue(s, Story.CUE_SOUND, "", "story.latch", 0);
					}
				}

				// Phase 24: Hale's trap (or the crawl way's end) only once nobody is after the player.
				if (!s.pursued() && (s.inside(D, DestroyerInterior.DETENTION)
						|| !s.has("trust.hale") && s.inside(D, new AABB(24.0, H - 0.5, 3.0, 25.0, H + 2.0, 7.0)))) {
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
					// (Phase 26: not while M-31 is at the field: it would be saved half done.)
					if (s.figure("hale") == null && s.figure("trap1") == null && s.unsaved() && s.dialog == null && !CellField.running(s)) {
						s.checkpoint(D, CELL_2, 0.0F);
					}

					// Phase 26: the hint says no more than that M-31 is out there (one finds out by
					// oneself what it can do).
					if (fired(s, "cell_hint") && !CellField.running(s)) {
						s.hint("sabercraft.story.hint.cell_droid");
					}

					CellField.tick(s, level);
				}
			}
			case "m6.lab" -> {
				if (s.has("know.ilyra") && s.dialog == null) {
					s.complete();
				}
			}
			case "m6.saber" -> Scenes.saberTick(s);
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
					// Phase 24: the escape seen from outside (the Tenebris goes up behind the Falcon);
					// the jump inside the Falcon begins as it jumps in the film, and goes on after it.
					s.set("tmp.jumping");
					s.set("tmp.film");
					s.timer("escape_film", ESCAPE_FILM);
					s.timer("jump", ESCAPE_JUMP_AT + InteriorJump.length());
					Story.cue(s, Story.CUE_CINEMA, "escape", "", ESCAPE_FILM);
					escapes++;
				} else if (s.has("tmp.jumping")) {
					int left = s.timers.getOrDefault("jump", 0);

					if (!s.has("tmp.interior_jump") && left <= InteriorJump.length()) {
						s.set("tmp.interior_jump");
						InteriorJump.story(level, s.falcon);
					}

					if (s.has("tmp.film") && !s.timer("escape_film")) {
						s.unset("tmp.film");
					}

					if (!s.timer("jump")) {
						s.unset("tmp.jumping");
						s.unset("tmp.jump_go");
						s.unset("tmp.interior_jump");
						s.unset("tmp.film");
						s.complete();
					}
				}
			}
			default -> {
			}
		}

		// Phase 24: the blade lock's scene (the fight waits meanwhile).
		if (s.has(Finale.BOSS_SCENE)) {
			Finale.bossScene(s, player, level);
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
	 * suspicious and nothing under way, a checkpoint is made, if anything kept by checkpoints
	 * changed since the last one. Phase 27: never at the very spot the player is: in the hideout at
	 * its own safe place, in a crawl way only well inside it (not in or by a hatch, whose opening is
	 * shut again at a restore), in the middle of the way.
	 */
	private static void safeCheckpoint(StorySession s, ServerPlayer player) {
		if (!D.equals(s.here) || s.dialog != null || s.pending != null || s.completing() || s.alarm > 0 || s.fighting
				|| s.suspicion >= Stealth.SUSPICIOUS || !s.unsaved() || NO_SAFE_CHECKPOINT.contains(s.step().id()) || prisonBreak(s)) {
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

		if (HIDDEN.contains(me.add(0.0, 0.5, 0.0))) {
			if (player.onGround()) {
				s.checkpoint(D, HIDEOUT, 90.0F);
			}

			return;
		}

		Vec3 crawl = crawlAnchor(me);

		if (crawl != null) {
			s.checkpoint(D, crawl, player.getYRot());
		}
	}

	static void safeCheckpointForTest(StorySession s) {
		ServerPlayer player = s.player();

		if (player != null) {
			safeCheckpoint(s, player);
		}
	}

	/** Phase 27: how near a hatch (blocks, to its opening's middle) a crawl way is no place to be put back at. */
	static final double HATCH_CLEAR = 2.5;

	/**
	 * Phase 27: where in a crawl way one may be put back (plot space, the feet): in the middle of
	 * the way across, on its floor, well away from every hatch; null when that point is not such a
	 * place.
	 */
	static @Nullable Vec3 crawlAnchor(Vec3 at) {
		Vec3 body = at.add(0.0, 0.3, 0.0);

		for (AABB vent : DestroyerInterior.VENTS) {
			if (!vent.contains(body)) {
				continue;
			}

			double x = vent.getXsize() <= 1.01 ? vent.minX + 0.5 : Mth.clamp(at.x, vent.minX + 0.5, vent.maxX - 0.5);
			double z = vent.getZsize() <= 1.01 ? vent.minZ + 0.5 : Mth.clamp(at.z, vent.minZ + 0.5, vent.maxZ - 0.5);
			Vec3 spot = new Vec3(x, vent.minY, z);

			for (com.phiro.sabercraft.interior.InteriorPlan.Hatch hatch : DestroyerInterior.HATCHES) {
				if (Math.hypot(hatch.cell().getX() + 0.5 - x, hatch.cell().getZ() + 0.5 - z) < HATCH_CLEAR && Math.abs(hatch.cell().getY() - spot.y) < 1.5) {
					return null;
				}
			}

			return spot;
		}

		return null;
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
		// Phase 25: the short scenes.
		Scenes.skip(s, film);

		if ("arrival".equals(film) && s.at("m1.cockpit") && s.has("tmp.film")) {
			s.timer("dock", 1);
		}

		if (("duel".equals(film) || "duel_end".equals(film)) && s.at("m6.duel") && s.has("tmp.film")) {
			s.timer("film", 1);
		}

		if ("takedown".equals(film) && s.has("tmp.takedown")) {
			// Phase 24: skipped: he is down at once (the story goes on as after the whole scene).
			s.timer("takedown", 1);
		}

		if ("boss_mid".equals(film) && s.has(Finale.BOSS_SCENE)) {
			// Phase 24: skipped: the burst, her second blade, the second phase at once.
			ServerPlayer player = s.player();
			Figure shadow = s.figure("shadow");

			if (player != null) {
				Finale.endBossScene(s, shadow, player);
			}
		}

		if ("escape".equals(film) && s.at("m6.jump") && s.has("tmp.jumping")) {
			// Phase 24: skipped: the jump inside the Falcon at once (then the epilogue, as after the film).
			s.timers.remove("escape_film");
			s.unset("tmp.film");
			s.timer("jump", Math.min(s.timers.getOrDefault("jump", 0), InteriorJump.length()));
		}
	}

	/** Phase 24: the end of the Tenebris from outside (as the client's {@code EscapeFilm}), and when the Falcon jumps in it. */
	static final int ESCAPE_FILM = 440;
	static final int ESCAPE_JUMP_AT = 330;
	private static int escapes;

	static int escapesForTest() {
		return escapes;
	}

	/** Phase 21: how long the film before the duel and the one of his flight are (as the client's {@code DuelFilm}). */
	static final int DUEL_FILM = 220;
	static final int DUEL_END_FILM = 346;
	/** Phase 24: her flight's moments (as the client's {@code DuelFilm}): she leaps up into the TIE, is in it. */
	static final int BOARD = 172;
	static final int IN_TIE = 184;
	/** Where the player stands in the film before the duel (facing him), and his way out (to the flight control door). */
	static final Vec3 DUEL_PLAYER = new Vec3(40.5, H, -21.0);

	/** Phase 21: the duel's step: the film (once; after that his words), the fight, his flight. */
	private static void duelTick(StorySession s, ServerPlayer player) {
		Figure shadow = s.figure("shadow");
		Vec3 me = s.local(D);

		// Phase 28: a droid called back by the player in the middle of the duel goes back to its place.
		if (s.stepTicks % 40 == 7 && droidsWait(s)) {
			droidsAside(s);
		}

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
					// Phase 24: to the TIE fighter parked by the field (she ran out of the door before).
					// (Round the south wing to the front of the ball, between the wings.)
					walk(shadow, new Vec3(30.0, H, -33.0), new Vec3(29.2, H, -41.0), DestroyerInterior.ESCAPE_TIE_FOOT);
					s.set("tmp.leave.shadow");
				} else if (t >= BOARD && t < IN_TIE) {
					// Up into its ball in one leap.
					Vec3 foot = DestroyerInterior.ESCAPE_TIE_FOOT;
					Vec3 hatch = DestroyerInterior.ESCAPE_TIE.add(0.0, 0.9, 0.0);

					if (t == BOARD) {
						shadow.mode = Figure.HELD;
						shadow.way = List.of();
						shadow.x = foot.x;
						shadow.z = foot.z;
						shadow.y = foot.y;
					}

					float u = (t - BOARD + 1) / (float) (IN_TIE - BOARD);
					shadow.x = Mth.lerp(u, foot.x, hatch.x);
					shadow.z = Mth.lerp(u, foot.z, hatch.z);
					shadow.y = foot.y + (hatch.y - foot.y) * u + Math.sin(u * Math.PI) * 0.8;
					shadow.speed = 0.0F;
				} else if (t == IN_TIE) {
					s.figures.removeIf(figure -> figure.key.equals("shadow"));
				}
			}


			if (!s.timer("film")) {
				s.unset("tmp.film");
				s.unset("tmp.duel_end");
				s.unset("tmp.leave.shadow");
				s.set("gone.shadow");
				// Phase 24: her TIE is gone with her.
				s.unset("tie.parked");
				s.figures.removeIf(figure -> figure.key.equals("shadow"));

				// His last words are in the film; Dax calls when it is over (seen with the HUD again).
				s.radio("dax", "sabercraft.story.radio.dax_arrives");
				// Phase 28: her saber went with her scene (it lay on the floor until the film ended;
				// it was never forgotten on the client and lay there again after a restart).
				Story.cue(s, Story.CUE_DUEL, "saber_gone", "", 0);
				// (Phase 28: no triumph here any more: the next step, the run to the Falcon, asks for
				// the escape's piece; the triumph comes once the Falcon has jumped.)

				s.complete();
			}

			return;
		}

		// Phase 24: nobody walks through the parked TIE's wings.
		tieWings(s, player);

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
		// Phase 24: her TIE is where the film shows it (the client draws it from this point).
		s.set("tie.parked");
		Vec3 tie = DestroyerInterior.ESCAPE_TIE.add(Vec3.atLowerCornerOf(s.destroyer));
		Story.cue(s, Story.CUE_CINEMA, "duel_end", "", DUEL_END_FILM, List.of(tie));
	}

	/**
	 * Phase 24: the parked TIE's wings stand on the hangar floor (drawn only): whoever walks into
	 * one is set back out on the side they came from. Under the ball between them one can walk.
	 */
	private static void tieWings(StorySession s, ServerPlayer player) {
		if (!s.has("tie.parked") || !D.equals(s.here) || s.has("tmp.film")) {
			return;
		}

		Vec3 me = s.local(D);
		Vec3 tie = DestroyerInterior.ESCAPE_TIE;

		// (It faces east, over the field: its wings stand north and south of the ball, across z.)
		if (me.y > tie.y + 3.3 || me.y < H - 0.5 || Math.abs(me.x - tie.x) > TIE_WING_HALF + 0.3) {
			return;
		}

		for (int side = -1; side <= 1; side += 2) {
			double wing = tie.z + side * TIE_WING_X;
			double off = me.z - wing;

			if (Math.abs(off) < TIE_WING_GAP) {
				double out = wing + (off >= 0.0 ? TIE_WING_GAP : -TIE_WING_GAP);
				Vec3 corner = Vec3.atLowerCornerOf(s.destroyer);
				player.connection.teleport(player.getX(), player.getY(), corner.z + out, player.getYRot(), player.getXRot());
				wingsBlocked++;
				return;
			}
		}
	}

	/** Phase 24: where the parked TIE's wings stand (from its middle, across z), how far they reach along x, how close one gets. */
	static final double TIE_WING_X = 3.08;
	static final double TIE_WING_HALF = 3.3;
	static final double TIE_WING_GAP = 0.45;
	private static int wingsBlocked;

	static int wingsBlockedForTest() {
		return wingsBlocked;
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
		// Phase 31: the cell's checkpoint at once (it waited for Hale to be out, and M-31 sent out
		// quickly kept it from being made at all: a capture then went back before the trap).
		s.checkpoint(D, CELL_2, 0.0F);

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

	// --- The detention block's guard (Phase 24) -----------------------------------------------------

	/** Phase 24: where the detention block's guard stands, and the two ways he faces: the door, the room. */
	static final Vec3 GUARD_POST = new Vec3(34.5, H, 7.6);
	static final float GUARD_DOOR_YAW = 200.0F;
	static final float GUARD_ROOM_YAW = 108.0F;
	/** Phase 24: his watch (ticks): the door this long, a turn, the room this long, a turn back. */
	static final int GUARD_DOOR_TICKS = 170;
	static final int GUARD_ROOM_TICKS = 80;
	static final int GUARD_TURN_TICKS = 20;
	/** Phase 24: overpowered (a mark kept with the checkpoints after it). */
	static final String GUARD_DOWN = "det_guard.down";
	/**
	 * Phase 24: how near behind him one has to be, and how long the scene is (ticks, as the client's
	 * film). Phase 31: on the same floor, a little nearer (2.4 before), and only from behind: within
	 * {@link #TAKEDOWN_SEEN} of his look he sees it coming, between that and {@link #TAKEDOWN_BACK}
	 * one is too far round to his side.
	 */
	static final double TAKEDOWN_REACH = 2.0;
	static final double TAKEDOWN_STEP = 0.6;
	static final double TAKEDOWN_BACK = 110.0;
	static final double TAKEDOWN_SEEN = 85.0;
	static final int TAKEDOWN_FILM = 76;
	/** Phase 31: he must not have seen the player for this long (ticks), nor anyone else just now (ticks). */
	static final int TAKEDOWN_FRESH = 40;
	static final int TAKEDOWN_WITNESS = 8;
	/** Phase 31: after one, a short breath before the next (ticks). */
	static final int TAKEDOWN_COOLDOWN = 40;
	/**
	 * Phase 31: known how (the prison guard's tip shown, or one done): from then on every unaware
	 * stormtrooper can be overpowered, not only the two guards of the story.
	 */
	static final String KNOW_TAKEDOWN = "know.takedown";
	/** Phase 31: where one overpowered lies ({@code down/<mission>/<key>/x/y/z/yaw}), kept with the checkpoints. */
	static final String DOWN_PREFIX = "down/";
	/**
	 * Phase 31: troopers the story needs standing: the lure's lesson in Mission 1, the trooper of
	 * Hale's trap, the checkpoint's (he talks), the service way's.
	 */
	static final Set<String> TAKEDOWN_NEVER = Set.of("maint", "trap1", "checkpoint", "svc_guard");
	/** Phase 31: how near (and how far round behind him) one is for the prison guard's tip. */
	static final double TIP_RANGE = 8.0;
	static final double TIP_BACK = 100.0;
	private static int takedowns;
	private static int takedownTips;

	/** For the tests: how often the guard was overpowered since the game started. */
	public static int takedownsForTest() {
		return takedowns;
	}

	/** Phase 31, for the tests: how often the takedown's tip was given. */
	static int takedownTipsForTest() {
		return takedownTips;
	}

	/**
	 * Phase 24: whether the guard can be overpowered now (on duty, not hunting the player, the
	 * player out of the cell). Phase 26: the lab's guard too, while the saber lies in the lab.
	 * Phase 31: any stormtrooper who has not noticed the player (see {@link #takedownable}).
	 */
	static boolean takedownOpen(StorySession s, Figure guard) {
		return takedownable(s, guard);
	}

	/** Phase 31: whether the player knows how (the tip, or one done; in the last mission always). */
	static boolean takedownKnown(StorySession s) {
		return s.mission >= 5 || s.has(KNOW_TAKEDOWN) || s.has(GUARD_DOWN);
	}

	/**
	 * Phase 31: whether one is someone who can be overpowered at all, now: a stormtrooper on watch
	 * or on his round (no officer, technician, pilot, droid, no one the story needs standing, no
	 * one who fights or talks, nobody come as help), the story calm (no alarm, no fight, no dialog,
	 * no scene). The detention block's and the lab's guards keep their windows of the story; the
	 * others once the player knows how.
	 */
	static boolean takedownWho(StorySession s, Figure f) {
		if (!D.equals(f.ship) || !D.equals(s.here) || !Crew.TROOPER.equals(f.kind()) || !f.observer || f.hostile || f.engaged || f.talks || f.blind
				|| f.talking || f == s.partner || f.key.startsWith(Stealth.REINFORCEMENT) || TAKEDOWN_NEVER.contains(f.key)) {
			return false;
		}

		if (s.alarm > 0 || s.fighting || s.cornered > 0 || s.dialog != null || s.has("tmp.film") || s.has("tmp.scene") || s.has("tmp.takedown")
				|| s.timer("takedown_cd")) {
			return false;
		}

		return switch (f.key) {
			case "det_guard" -> (s.at("m5.cell") && s.has("m5.trapped") && s.has("cell2.open") || s.at("m5.r2") || s.at("m5.cell1") || s.at("m5.back"))
					&& !s.has(GUARD_DOWN);
			case "lab_guard" -> s.mission == 5 && !s.has("saber.taken") && !s.has(LAB_GUARD_DOWN)
					&& (s.at("m6.reactor") || s.at("m6.door") || s.at("m6.lab") || s.at("m6.saber"));
			default -> takedownKnown(s);
		};
	}

	/**
	 * Phase 31: whether he is unaware enough: on his routine, looking round, gone to look at a noise
	 * (a lure) or on his way back, not alerted, and he has not seen the player for a while.
	 */
	static boolean takedownCalm(StorySession s, Figure f) {
		boolean mode = f.mode == Figure.ROUTINE || f.mode == Figure.LOOK || f.mode == Figure.INVESTIGATE || f.mode == Figure.RETURN;
		return mode && f.stage < Figure.ALERTED && s.time() - f.lastSeen >= TAKEDOWN_FRESH;
	}

	/** Phase 31: whether one can be overpowered now (by whom one is and how unaware), from close behind. */
	static boolean takedownable(StorySession s, Figure f) {
		return takedownWho(s, f) && takedownCalm(s, f);
	}

	/** Phase 31: how far round from his look the player is (degrees; 180 right behind him). */
	static double behind(Figure f, Vec3 me) {
		Vec3 to = new Vec3(me.x - f.x, 0.0, me.z - f.z);
		double distance = to.length();
		return distance < 1.0E-3 ? 180.0 : Math.toDegrees(Math.acos(Mth.clamp(to.normalize().dot(f.look()), -1.0, 1.0)));
	}

	/** Phase 31: whether the player is near enough (same floor, within reach, nothing between them). */
	private static boolean takedownNear(StorySession s, ServerPlayer player, Figure f, Vec3 me) {
		ServerLevel level = interiors(s);

		if (level == null || Math.hypot(me.x - f.x, me.z - f.z) > TAKEDOWN_REACH || Math.abs(me.y - f.y) > TAKEDOWN_STEP) {
			return false;
		}

		Vec3 corner = Vec3.atLowerCornerOf(s.destroyer);
		Vec3 eye = player.getEyePosition();
		return Stealth.clear(level, eye, corner.add(f.eye())) || Stealth.clear(level, eye, corner.add(f.x, f.y + 1.1, f.z));
	}

	/**
	 * Phase 31: whether overpowering him would work from where the player stands now (plot space;
	 * the prompt under the crosshair shows only then): near, behind him, he unaware. Crouching is
	 * asked for at the click (the prompt says so while one stands).
	 */
	@Override
	public boolean takedownReady(StorySession s, Figure f, Vec3 me) {
		if (!D.equals(s.here) || Math.abs(me.x - f.x) > TAKEDOWN_REACH || Math.abs(me.z - f.z) > TAKEDOWN_REACH || Math.abs(me.y - f.y) > TAKEDOWN_STEP) {
			return false;
		}

		ServerPlayer player = s.player();
		return player != null && takedownable(s, f) && behind(f, me) >= TAKEDOWN_BACK && takedownNear(s, player, f, me);
	}

	@Override
	public boolean takedown(StorySession session, Figure figure) {
		return overpower(session, figure);
	}

	/** Phase 31: whether someone else (on watch) sees the player right now. */
	private static boolean witnessed(StorySession s, Figure f) {
		long now = s.time();

		for (Figure o : s.figures) {
			if (o != f && o.observer && o.mode != Figure.DOWN && D.equals(o.ship) && now - o.lastSeen <= TAKEDOWN_WITNESS) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Phase 24: the guard's watch. At his post he looks to the door most of the time (who comes
	 * in?), now and then he turns and looks over the corridor, the cells and the crawl way's hatch;
	 * what he sees and hears he reacts to as anyone (a noise: he goes to look, then back to his
	 * post). And the scene when he is overpowered.
	 */
	private static void guardTick(StorySession s, ServerPlayer player, ServerLevel level) {
		Figure guard = s.figure("det_guard");

		if (guard == null) {
			return;
		}

		if (guard.mode != Figure.ROUTINE || s.partner == guard) {
			return;
		}

		int cycle = GUARD_DOOR_TICKS + GUARD_ROOM_TICKS + 2 * GUARD_TURN_TICKS;
		long t = (s.time() + guard.id * 37L) % cycle;
		float yaw;

		if (t < GUARD_DOOR_TICKS) {
			yaw = GUARD_DOOR_YAW;
		} else if (t < GUARD_DOOR_TICKS + GUARD_TURN_TICKS) {
			yaw = Mth.rotLerp(smooth((t - GUARD_DOOR_TICKS) / (float) GUARD_TURN_TICKS), GUARD_DOOR_YAW, GUARD_ROOM_YAW);
		} else if (t < GUARD_DOOR_TICKS + GUARD_TURN_TICKS + GUARD_ROOM_TICKS) {
			yaw = GUARD_ROOM_YAW;
		} else {
			yaw = Mth.rotLerp(smooth((t - GUARD_DOOR_TICKS - GUARD_TURN_TICKS - GUARD_ROOM_TICKS) / (float) GUARD_TURN_TICKS), GUARD_ROOM_YAW, GUARD_DOOR_YAW);
		}

		guard.yaw = yaw;
		// His head moves a little within the look (he is watching, not a statue).
		guard.headYaw = yaw + 12.0F * Mth.sin((s.time() + guard.id) / 23.0F);
	}

	private static float smooth(float u) {
		float x = Mth.clamp(u, 0.0F, 1.0F);
		return x * x * (3.0F - 2.0F * x);
	}

	/** Phase 26: how long the lab guard takes to turn from one look to the next (ticks). */
	static final int LAB_TURN_TICKS = 24;

	/**
	 * Phase 26: the lab's guard keeps watch over the reactor's platform; his head moves within the
	 * look. Phase 27: from his post west of the way to the door he looks in turn along the platform
	 * to the west, over the shaft, to the east bridge and back over the shaft ({@link #LAB_WATCH}):
	 * coming along the platform, one waits for him to look east. What he sees and hears he reacts
	 * to as anyone (Stealth: he goes to look, raises the alarm, fires); from close behind he can be
	 * overpowered, the same scene as the detention's guard.
	 */
	private static void labGuardTick(StorySession s, ServerPlayer player, ServerLevel level) {
		Figure guard = s.figure("lab_guard");

		if (guard == null) {
			return;
		}

		if (guard.mode != Figure.ROUTINE || guard.hostile || s.partner == guard || guard.position().distanceTo(LAB_GUARD) > 0.6) {
			return;
		}

		float yaw = labWatchYaw(s.time() + guard.id * 29L);
		guard.yaw = yaw;
		guard.headYaw = yaw + 12.0F * Mth.sin((s.time() + guard.id) / 21.0F);
	}

	/** Phase 27: the length of the lab guard's round of looks (ticks). */
	static int labWatchCycle() {
		return watchCycle(LAB_WATCH, LAB_TURN_TICKS);
	}

	/** Phase 27: where the lab's guard looks at that time of his round (each look held, smooth turns between). */
	static float labWatchYaw(long time) {
		return watchYaw(LAB_WATCH, LAB_TURN_TICKS, time);
	}

	/** Phase 28 (from Phase 27's lab guard): the length of a round of looks (ticks). */
	static int watchCycle(float[][] watch, int turn) {
		int cycle = 0;

		for (float[] look : watch) {
			cycle += (int) look[1] + turn;
		}

		return cycle;
	}

	/** Phase 28 (from Phase 27's lab guard): where one looks at that time of a round (each look held, smooth turns between). */
	static float watchYaw(float[][] watch, int turn, long time) {
		long t = Math.floorMod(time, (long) watchCycle(watch, turn));

		for (int i = 0; i < watch.length; i++) {
			float[] look = watch[i];
			float[] next = watch[(i + 1) % watch.length];

			if (t < look[1]) {
				return look[0];
			}

			t -= (long) look[1];

			if (t < turn) {
				return Mth.rotLerp(smooth(t / (float) turn), look[0], next[0]);
			}

			t -= turn;
		}

		return watch[0][0];
	}

	/** Phase 28: the key of the catwalk's technician (the reactor's crew member on the deck level at the console by the bridge). */
	static String catwalkKey() {
		for (int i = 0; i < DestroyerInterior.CREW.size(); i++) {
			Crew c = DestroyerInterior.CREW.get(i);

			if ("reactor".equals(c.role()) && Crew.TECH.equals(c.kind()) && c.y() >= H - 1 && c.y() < 100.0) {
				return "crew:" + i;
			}
		}

		return "crew:none";
	}

	/**
	 * Phase 28: the catwalk's technician works his round at his post (see {@link #CATWALK_WATCH});
	 * once lured off (or searching, or after the player) he is left to the stealth rules, and when
	 * he is back at his post he takes up his round again. The first time the player comes onto
	 * the gallery's landing while he is at work, a hint says how to get past him.
	 */
	private static void catwalkTick(StorySession s, ServerPlayer player) {
		Figure tech = s.figure(catwalkKey());

		if (tech == null || tech.mode != Figure.ROUTINE || tech.hostile || s.partner == tech || tech.position().distanceTo(CATWALK_POST) > 0.6) {
			return;
		}

		float yaw = watchYaw(CATWALK_WATCH, CATWALK_TURN, s.time() + tech.id * 31L);
		tech.yaw = yaw;
		tech.headYaw = yaw + 10.0F * Mth.sin((s.time() + tech.id) / 23.0F);

		if (!s.has("tmp.hint_catwalk") && !s.has("saber.taken") && CATWALK_SEEN.contains(s.local(D)) && s.alarm == 0) {
			s.set("tmp.hint_catwalk");
			s.hint("sabercraft.story.hint.catwalk_tech");
		}
	}

	/** Phase 28, for the tests: the time of the technician's round when he holds that look (index into {@link #CATWALK_WATCH}). */
	static long catwalkWatchAt(long now, int look, int techId) {
		long offset = 0L;

		for (int i = 0; i < look; i++) {
			offset += (long) CATWALK_WATCH[i][1] + CATWALK_TURN;
		}

		long cycle = watchCycle(CATWALK_WATCH, CATWALK_TURN);
		long t = Math.floorMod(now + techId * 31L, cycle);
		return now + Math.floorMod(offset + 4L - t, cycle);
	}

	/** For the tests: the time of the guard's round when he holds that look (index into {@link #LAB_WATCH}), from that time on. */
	static long labWatchAt(long now, int look, int guardId) {
		long offset = 0L;

		for (int i = 0; i < look; i++) {
			offset += (long) LAB_WATCH[i][1] + LAB_TURN_TICKS;
		}

		long cycle = labWatchCycle();
		long t = Math.floorMod(now + guardId * 29L, cycle);
		// The first tick of that look a few ticks in (the turn into it done).
		long wanted = offset + 4L;
		return now + Math.floorMod(wanted - t, cycle);
	}

	/**
	 * Phase 24: the player overpowers the guard: from close behind, while he looks away. From the
	 * front or the side he sees it coming and raises the alarm; from too far one is told to come
	 * closer. Then a short scene seen from outside: a grab, a blow, he goes down (and stays down).
	 * Phase 31: whoever stands nearest within reach that can be overpowered (the tests' way in).
	 */
	static void takedown(StorySession s) {
		ServerPlayer player = s.player();

		if (player == null || !D.equals(s.here)) {
			return;
		}

		Vec3 me = s.local(D);
		Figure best = null;
		double bestDistance = Story.REACH;

		for (Figure f : s.figures) {
			double distance = Math.hypot(f.x - me.x, f.z - me.z);

			if (distance < bestDistance && takedownWho(s, f)) {
				best = f;
				bestDistance = distance;
			}
		}

		if (best != null) {
			overpower(s, best);
		}
	}

	/**
	 * Phase 31: the player tries to overpower him (the use key on him). Only a stormtrooper who has
	 * not noticed them (see {@link #takedownable}), from close behind, crouching (or he hears them
	 * coming), with nobody else looking: then the scene. From the front he sees it coming and the
	 * alarm goes up (as in Phase 24); otherwise a short hint says why not, and nothing happens.
	 * Returns whether the scene began.
	 */
	static boolean overpower(StorySession s, Figure f) {
		ServerPlayer player = s.player();

		if (player == null || !takedownWho(s, f)) {
			if (player != null && f.observer && (f.stage >= Figure.ALERTED || f.mode == Figure.CHASE || f.hostile) && Crew.TROOPER.equals(f.kind())) {
				s.hint("sabercraft.story.hint.takedown_alert");
			}

			return false;
		}

		Vec3 me = s.local(D);

		if (!takedownNear(s, player, f, me)) {
			s.hint("sabercraft.story.hint.takedown_near");
			return false;
		}

		double angle = behind(f, me);

		if (angle <= TAKEDOWN_SEEN) {
			// He sees it coming.
			f.suspicion = 1.0F;
			f.stage = Figure.DETECTED;
			f.seenAt = me;
			s.hint("sabercraft.story.hint.takedown_seen");
			Story.detected(s, f);
			return false;
		}

		if (!takedownCalm(s, f)) {
			// He is on his guard already (he saw the player a moment ago, or is alerted).
			s.hint("sabercraft.story.hint.takedown_alert");
			return false;
		}

		if (angle < TAKEDOWN_BACK) {
			s.hint("sabercraft.story.hint.takedown_near");
			return false;
		}

		if (!player.isShiftKeyDown() && !player.isCrouching()) {
			s.hint("sabercraft.story.hint.takedown_crouch");
			return false;
		}

		if (witnessed(s, f)) {
			s.hint("sabercraft.story.hint.takedown_witness");
			return false;
		}

		takedowns++;
		double distance = Math.hypot(me.x - f.x, me.z - f.z);
		Vec3 to = new Vec3(me.x - f.x, 0.0, me.z - f.z);
		s.set("tmp.film");
		s.set("tmp.takedown");
		s.set("tmp.takedown." + f.key);
		s.timer("takedown", TAKEDOWN_FILM);
		f.mode = Figure.SCRIPTED;
		f.blind = true;
		f.speed = 0.0F;
		f.way = List.of();
		// The player right behind him, facing him. Phase 27: unless something stands there (the
		// lab guard's back towards the console): then on the side they came from, or where they are.
		Vec3 corner0 = Vec3.atLowerCornerOf(s.destroyer);
		Vec3 back = f.position().subtract(f.look().scale(0.9));

		if (!room(player, back.add(corner0))) {
			Vec3 side = distance < 1.0E-3 ? null : f.position().add(to.normalize().scale(0.9));
			back = side != null && room(player, side.add(corner0)) ? side : me;
		}

		Story.place(s, D, back, f.yaw);
		Vec3 world = f.position().add(corner0);
		Story.cue(s, Story.CUE_CINEMA, "takedown", "", TAKEDOWN_FILM, List.of(world));
		// Phase 31: from now on the player knows how (kept at once: a capture does not take it back).
		keepNow(s, KNOW_TAKEDOWN);
		s.dirty = true;
		return true;
	}

	/** Phase 27: whether the player fits standing at that place (world space). */
	private static boolean room(ServerPlayer player, Vec3 at) {
		return player.level().noCollision(player, player.getDimensions(net.minecraft.world.entity.Pose.STANDING).makeBoundingBox(at).deflate(0.02));
	}

	/**
	 * Phase 31: the takedown's scene runs (whoever it is about; it was the two guards' ticks that
	 * ran it, so only those two could be overpowered). Returns whether it does.
	 */
	private static boolean takedownScene(StorySession s, ServerPlayer player, ServerLevel level) {
		if (!s.has("tmp.takedown")) {
			return false;
		}

		Figure target = null;

		for (String flag : s.flags) {
			if (flag.startsWith("tmp.takedown.")) {
				target = s.figure(flag.substring("tmp.takedown.".length()));
				break;
			}
		}

		if (target == null) {
			// (The step went on and he with it: nothing to show any more.)
			endTakedown(s, null);
			return false;
		}

		takedownTick(s, player, level, target);
		return true;
	}

	private static void takedownTick(StorySession s, ServerPlayer player, ServerLevel level, Figure guard) {
		int left = s.timers.getOrDefault("takedown", 0);
		int t = TAKEDOWN_FILM - left;
		Vec3 corner = Vec3.atLowerCornerOf(s.destroyer);
		Vec3 at = guard.position().add(corner).add(0.0, 1.2, 0.0);

		if (guard.mode != Figure.DOWN) {
			guard.speed = 0.0F;
		}

		if (t == 14) {
			// The grab: an arm round his helmet, a jolt.
			player.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
			guard.react(Figure.STUMBLE, 12);
			level.playSound(null, at.x, at.y, at.z, ShipInteriors.sound("story.armour_hit"), net.minecraft.sounds.SoundSource.HOSTILE, 0.7F, 0.8F);
		} else if (t == 26) {
			// The blow: he goes down.
			player.swing(net.minecraft.world.InteractionHand.OFF_HAND, true);
			guardDown(s, guard);
			level.playSound(null, at.x, at.y, at.z, ShipInteriors.sound("story.armour_hit"), net.minecraft.sounds.SoundSource.HOSTILE, 0.9F, 0.65F);
			level.playSound(null, at.x, at.y - 1.0, at.z, ShipInteriors.sound("story.body_fall"), net.minecraft.sounds.SoundSource.HOSTILE, 0.9F, 0.9F);
		}

		if (guard.reactTicks > 0) {
			Hits.tick(guard, level, corner);
		}

		if (left <= 0 || !s.timer("takedown")) {
			// The end (or skipped): he is down for good, the player has the room.
			if (guard.mode != Figure.DOWN) {
				guardDown(s, guard);
				guard.reactTicks = 0;
			}

			endTakedown(s, guard);
			s.hint("sabercraft.story.hint.takedown_done");
		}
	}

	/** Phase 31: the scene's marks gone; a short breath before the next. */
	private static void endTakedown(StorySession s, @Nullable Figure guard) {
		s.flags.removeIf(flag -> flag.startsWith("tmp.takedown"));
		s.unset("tmp.film");
		s.timers.remove("takedown");

		if (guard != null) {
			s.timer("takedown_cd", TAKEDOWN_COOLDOWN);
		}

		s.dirty = true;
	}

	/**
	 * Phase 24: the guard lies where he fell: no longer on watch (and it is remembered with the
	 * checkpoints). Phase 31: anyone overpowered: where he lies is kept too ({@link #DOWN_PREFIX}),
	 * so that he lies there still after being caught (back at the checkpoint after it) or as the
	 * steps go on, not back on his round (nor up again at the start of it).
	 */
	private static void guardDown(StorySession s, Figure guard) {
		guard.mode = Figure.DOWN;
		guard.observer = false;
		guard.lamp = false;
		guard.speed = 0.0F;
		guard.suspicion = 0.0F;
		guard.stage = Figure.CALM;
		guard.react(Figure.FALL, 16);

		if ("lab_guard".equals(guard.key)) {
			s.set(LAB_GUARD_DOWN);
		} else if ("det_guard".equals(guard.key)) {
			s.set(GUARD_DOWN);
		}

		String mine = DOWN_PREFIX + s.mission().id() + "/" + guard.key + "/";
		s.flags.removeIf(flag -> flag.startsWith(mine));
		s.set(mine + String.format(java.util.Locale.ROOT, "%.2f/%.2f/%.2f/%.0f", guard.x, guard.y, guard.z, guard.yaw));
	}

	/**
	 * Phase 31: those overpowered in this mission lie where they fell (as kept): their routine is
	 * that place, lying, no longer on watch.
	 */
	private static void downs(StorySession s, List<Figure> list) {
		String mine = DOWN_PREFIX + s.mission().id() + "/";

		for (String flag : s.flags) {
			if (!flag.startsWith(mine)) {
				continue;
			}

			String[] parts = flag.substring(mine.length()).split("/");

			if (parts.length != 5) {
				continue;
			}

			try {
				double x = Double.parseDouble(parts[1]);
				double y = Double.parseDouble(parts[2]);
				double z = Double.parseDouble(parts[3]);
				float yaw = Float.parseFloat(parts[4]);

				for (Figure f : list) {
					if (f.key.equals(parts[0])) {
						f.crew = Crew.at(f.kind(), Crew.STAND, x, y, z, yaw).as(f.crew.role());
						f.mode = Figure.DOWN;
						f.observer = false;
						f.lamp = false;
					}
				}
			} catch (NumberFormatException e) {
				// (A mark spoilt by hand: he stands as at the start.)
			}
		}
	}

	/**
	 * Phase 31: the takedown's tip, once per game: the first time the player is out of the cell
	 * (or in from the crawl way), calm, a few steps behind the detention block's guard while he
	 * looks to the door, a card at the left says what one can do (crouch, from behind, the use key)
	 * and when not (once one has been noticed). Done at once, it needs no tip any more.
	 */
	private static void takedownTip(StorySession s, ServerPlayer player) {
		if (s.has(KNOW_TAKEDOWN) || s.stepTicks < 40 || s.stepTicks % 5 != 0 || !D.equals(s.here)) {
			return;
		}

		Figure guard = s.figure("det_guard");

		if (guard == null || guard.mode != Figure.ROUTINE || guard.stage != Figure.CALM || !takedownable(s, guard)) {
			return;
		}

		if (s.grace > 0 || s.suspicion >= Stealth.SUSPICIOUS || CellField.running(s) || s.timer("hack") || !player.onGround()) {
			return;
		}

		Vec3 me = s.local(D);

		if (!DestroyerInterior.DETENTION.contains(me.add(0.0, 0.5, 0.0)) || me.z <= DestroyerInterior.CELL_FRONT + 0.6 || inVent(me)) {
			return;
		}

		if (Math.hypot(me.x - guard.x, me.z - guard.z) > TIP_RANGE || Math.abs(me.y - guard.y) > 1.0 || behind(guard, me) < TIP_BACK) {
			return;
		}

		keepNow(s, KNOW_TAKEDOWN);
		takedownTips++;
		Story.cue(s, Story.CUE_HINT, "tip", "sabercraft.story.tip.takedown", 0);
	}

	/**
	 * Phase 24: a scene of the story begins: the alarm stands down, those who came as help go, and
	 * whoever still hunted the player near the scene goes back to their post (none of them crowd
	 * into a room the story needs for itself).
	 */
	static void clearStage(StorySession s, AABB near) {
		s.figures.removeIf(figure -> figure.key.startsWith(Stealth.REINFORCEMENT));

		for (Figure figure : s.figures) {
			if (D.equals(figure.ship) && !figure.hostile && (figure.mode == Figure.CHASE || figure.mode == Figure.INVESTIGATE || figure.mode == Figure.CALL
					|| figure.mode == Figure.LOOK && near.contains(figure.position()))) {
				figure.mode = Figure.RETURN;
				figure.called = false;
				figure.searches = 0;
				figure.windup = 0;
				figure.burst = 0;
				figure.suspicion = 0.0F;
				figure.stage = Figure.CALM;
				figure.path = List.of();
				figure.pathGoal = null;
			}
		}

		s.alarm = 0;
		s.unseen = 0;
		s.searching = false;
		s.cornered = 0;
		s.cornerer = null;
		s.dirty = true;
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
			walk(kestrel, new Vec3(27.0, H, 3.6), KESTREL_AT_VENT);
		}

		done(s, "m5.cell1");
	}

	/** Mission 6: Ilyra's saber from the field: the fight begins. */
	static void takeSaber(StorySession s) {
		giveSaber(s);
		saberSurge(s);
		saberFight(s);
	}

	/** Phase 25 (from {@link #takeSaber}): the saber in the player's hand. */
	static void giveSaber(StorySession s) {
		ServerPlayer player = s.player();
		s.set("saber.taken");

		if (player != null) {
			player.getInventory().setItem(player.getInventory().getSelectedSlot(), reward());
			player.containerMenu.broadcastChanges();
		}
	}

	/**
	 * Phase 25 (from {@link #takeSaber}): the containment field drew on the reactor's core (Phase
	 * 24): torn open, it overloads it. A blast out of the field's emitter, the core's arcs.
	 */
	static void saberSurge(StorySession s) {
		s.set("tmp.saber_surge");
		ServerLevel level = interiors(s);

		if (level != null) {
			Vec3 field = Vec3.atCenterOf(DestroyerInterior.SABER_FIELD).add(Vec3.atLowerCornerOf(s.destroyer));
			level.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION, field.x, field.y + 0.6, field.z, 1, 0.0, 0.0, 0.0, 0.0);
			level.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK, field.x, field.y + 0.8, field.z, 50, 0.4, 0.5, 0.4, 0.5);
			level.sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE, field.x, field.y + 1.0, field.z, 10, 0.3, 0.4, 0.3, 0.03);
			level.playSound(null, field.x, field.y, field.z, ShipInteriors.sound("story.reactor_arc"), SoundSource.BLOCKS, 1.4F, 0.8F);
			level.playSound(null, field.x, field.y, field.z, ShipInteriors.sound("ship.small_blast"), SoundSource.BLOCKS, 1.1F, 0.9F);
		}

		Story.cue(s, Story.CUE_SOUND, "", "story.alarm", 0);
	}

	/** Phase 25 (from {@link #takeSaber}): the fight begins (the alarm, everyone after the player fights), the ship shudders. */
	static void saberFight(StorySession s) {
		s.fighting = true;
		s.raiseAlarm(Stealth.SEARCH + Stealth.LOSE);
		// Phase 23: the chasers from the reactor (and anyone searching) fight from this moment.
		Finale.engageAll(s);
		Story.cue(s, Story.CUE_DUEL, "quake", "", 22);
		s.timer("intercom_reactor", 70);
		s.radio("kestrel", "sabercraft.story.radio.saber_taken");
		learnFocus(s, true);
		done(s, "m6.saber");
	}

	// --- Phase 24: the reactor fails --------------------------------------------------------------

	/** How bad it is by step (0: nothing yet). */
	static int instabilityLevel(StorySession s) {
		if (s.mission != 5 || !s.has("saber.taken")) {
			return 0;
		}

		return switch (s.step().id()) {
			case "m6.saber", "m6.escape1" -> 1;
			case "m6.escape2" -> 2;
			case "m6.duel" -> 3;
			case "m6.falcon", "m6.jump" -> 4;
			default -> 0;
		};
	}

	/** How long between two shudders (ticks) by how bad it is. */
	private static final int[] SHUDDER_EVERY = {0, 170, 130, 100, 70};
	/** Where it burns and sparks along the way out (plot space), by step: the reactor, the corridor, the hangar. */
	private static final List<Vec3> DAMAGE = List.of(new Vec3(DestroyerInterior.REACTOR_X - 2.0, R + 6.0, DestroyerInterior.REACTOR_Z + 1.0),
			new Vec3(DestroyerInterior.REACTOR_X + 2.5, R + 11.0, DestroyerInterior.REACTOR_Z - 1.5), new Vec3(48.0, H + 3.2, 6.0),
			new Vec3(47.5, H + 3.2, 0.5), new Vec3(40.5, H + 3.4, -6.0), new Vec3(38.5, H + 3.4, -12.0), new Vec3(35.0, H + 5.0, -22.0),
			new Vec3(47.0, H + 5.0, -31.0));
	private static int shudders;

	static int shuddersForTest() {
		return shudders;
	}

	/**
	 * Phase 24: the reactor, overloaded when the saber was torn out of its field, fails more and
	 * more on the way out: the ship shudders now and then (sooner and harder from section to
	 * section), sparks, smoke and small blasts break out near the player (never hurting), the lights
	 * flicker, the intercom warns and calls to evacuate, sirens; where it is worst it burns on.
	 * Nothing of it happens during a film or a dialog (it waits); in the Falcon only the booms and
	 * the shaking are felt.
	 */
	private void instability(StorySession s, ServerPlayer player, ServerLevel level) {
		int stage = instabilityLevel(s);

		if (fired(s, "intercom_reactor")) {
			s.hear("intercom", "sabercraft.story.shout.reactor_critical");
		}

		if (fired(s, "intercom_evacuate")) {
			s.hear("intercom", "sabercraft.story.shout.evacuate");
		}

		if (stage == 0 || s.has("tmp.film") || s.dialog != null || s.completing()) {
			return;
		}

		boolean aboard = D.equals(s.here);
		Vec3 corner = Vec3.atLowerCornerOf(s.origin(s.here != null ? s.here : D));

		// The fires where it is worst, on all the time near the player.
		if (aboard && s.stepTicks % 8 == 0) {
			for (Vec3 spot : DAMAGE) {
				Vec3 at = spot.add(corner);

				if (at.distanceToSqr(player.position()) < 24.0 * 24.0) {
					level.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 3 + stage, 0.25, 0.1, 0.25, 0.2);
					level.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE, at.x, at.y, at.z, 2, 0.2, 0.1, 0.2, 0.01);
				}
			}
		}

		if (aboard && stage >= 2 && s.stepTicks % 140 == 0) {
			Story.cue(s, Story.CUE_SOUND, "", "story.siren", 0);
		}

		if (s.timer("shudder")) {
			return;
		}

		s.timer("shudder", SHUDDER_EVERY[stage] + player.getRandom().nextInt(40));
		shudders++;
		Story.cue(s, Story.CUE_DUEL, "rumble", "", stage);

		if (!aboard) {
			// In the Falcon: the Tenebris shakes it where it lies, a boom through the hull.
			Story.cue(s, Story.CUE_SOUND, "", "ship.explode", 0);
			return;
		}

		// A blast somewhere near (where one sees it), sparks, smoke, the core's arcs.
		Vec3 eye = player.getEyePosition();
		net.minecraft.util.RandomSource random = player.getRandom();

		for (int attempt = 0; attempt < 5; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			double reach = 4.0 + random.nextDouble() * 8.0;
			Vec3 at = eye.add(Math.cos(angle) * reach, -0.5 + random.nextDouble() * 2.5, Math.sin(angle) * reach);

			if (!Stealth.clear(level, eye, at)) {
				continue;
			}

			level.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
			level.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 16 + stage * 6, 0.3, 0.3, 0.3, 0.35);
			level.sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, 4 + stage, 0.4, 0.3, 0.4, 0.02);
			level.sendParticles(net.minecraft.core.particles.ParticleTypes.LAVA, at.x, at.y, at.z, 2 + stage, 0.2, 0.2, 0.2, 0.0);
			level.playSound(null, at.x, at.y, at.z, ShipInteriors.sound(stage >= 3 ? "ship.explode" : "ship.small_blast"), SoundSource.BLOCKS,
					0.45F + 0.12F * stage, 0.8F + random.nextFloat() * 0.3F);
			break;
		}

		if (shudders % 2 == 0) {
			level.playSound(null, eye.x, eye.y + 3.0, eye.z, ShipInteriors.sound("story.reactor_arc"), SoundSource.BLOCKS, 0.4F + 0.1F * stage, 0.6F);
		}
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

	// --- Phase 28: the side way's cover --------------------------------------------------------------

	/**
	 * Phase 28: the cover (a barrier in its cell while shut) and the toolbox (a barrier always: the
	 * box stays, open and empty once the screwdriver is taken) in the player's own ship, as kept.
	 * Never shut onto someone in the cover's cell.
	 */
	static void ventCover(StorySession s) {
		ServerLevel level = interiors(s);

		if (level == null) {
			return;
		}

		BlockPos cover = s.destroyer.offset(VENT_COVER);
		boolean shut = !s.has(COVER_OPEN);
		boolean barrier = level.getBlockState(cover).is(Blocks.BARRIER);

		if (shut && level.getBlockState(cover).isAir() && level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, new AABB(cover)).isEmpty()) {
			level.setBlock(cover, Blocks.BARRIER.defaultBlockState(), 3);
		} else if (!shut && barrier) {
			level.setBlock(cover, Blocks.AIR.defaultBlockState(), 3);
		}

		BlockPos box = s.destroyer.offset(TOOLBOX);

		if (level.getBlockState(box).isAir() && level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, new AABB(box)).isEmpty()) {
			level.setBlock(box, Blocks.BARRIER.defaultBlockState(), 3);
		}
	}

	/** Phase 28: a flag kept at once (with the last checkpoint too): a capture or death does not take it back. */
	static void keepNow(StorySession s, String flag) {
		s.set(flag);

		if (!s.progress.flags.contains(flag)) {
			s.progress.flags.add(flag);
		}

		s.dirty = true;
	}

	/** Phase 28: the screwdriver taken from the toolbox (the dialog's end). */
	static void takeDriver(StorySession s) {
		keepNow(s, DRIVER);

		if (!s.journal.contains("driver")) {
			s.journal.add("driver");
			keepJournal(s, "driver");
		}

		Story.cue(s, Story.CUE_SOUND, "", "story.latch", 0);
		drivers++;
	}

	/** Phase 28: a journal entry kept at once (as the flag it belongs to). */
	private static void keepJournal(StorySession s, String entry) {
		if (!s.progress.journal.contains(entry)) {
			s.progress.journal.add(entry);
		}
	}

	/**
	 * Phase 28: the cover used. From the crawl way from flight control (its back): screwed tight
	 * from the other side (no more). From the hangar's side without the screwdriver: the four
	 * screws want a tool. With it: the close-up of the cover opens on the client (the four screws,
	 * {@link #coverUnscrewed} when done).
	 */
	static void coverUsed(StorySession s) {
		Vec3 me = s.local(D);

		if (s.has(COVER_OPEN)) {
			return;
		}

		if (me.x < VENT_COVER.getX() + 0.5) {
			s.hint("sabercraft.story.hint.cover_locked");
			Story.cue(s, Story.CUE_SOUND, "", "story.latch", 0);
			return;
		}

		if (!s.has(DRIVER)) {
			s.hint("sabercraft.story.hint.cover_tool");
			return;
		}

		coverStarts++;
		Story.cue(s, Story.CUE_EFFECT, "cover", "", 200,
				List.of(Vec3.atLowerCornerOf(s.origin(D)).add(VENT_COVER.getX() + 1.0, H + 0.5, VENT_COVER.getZ() + 0.5)));
	}

	/**
	 * Phase 28: the client says the four screws are out. Checked again here: the cover still shut,
	 * the screwdriver kept, the player in the side way on the hangar's side, close to it. Then the
	 * cover comes off (and stays off, kept at once).
	 */
	public static boolean coverUnscrewed(StorySession s, ServerPlayer player) {
		if (s.has(COVER_OPEN) || !s.has(DRIVER) || !D.equals(s.here)) {
			return false;
		}

		Vec3 me = s.local(D);
		Vec3 face = new Vec3(VENT_COVER.getX() + 1.0, H + 0.5, VENT_COVER.getZ() + 0.5);

		if (me.x < VENT_COVER.getX() + 0.9 || me.distanceTo(face) > COVER_REACH + 0.6 || Math.abs(me.z - face.z) > 1.2) {
			return false;
		}

		keepNow(s, COVER_OPEN);
		ventCover(s);
		Story.cue(s, Story.CUE_SOUND, "", "story.latch", 0);
		Story.cue(s, Story.CUE_EFFECT, "cover_off", "", 30, List.of(Vec3.atLowerCornerOf(s.origin(D)).add(face)));
		s.hint("sabercraft.story.hint.cover_open");
		coversOpened++;
		return true;
	}

	private static int drivers;
	private static int coverStarts;
	private static int coversOpened;

	/** Phase 28, for the tests: screwdrivers taken, close-ups begun, covers opened. */
	public static int[] coverForTest() {
		return new int[] {drivers, coverStarts, coversOpened};
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

				// Phase 28: in Kestrel's crawl way (the hatch unbolted), on to its end at the detention
				// block (the marker pointed back at the hatch behind the player).
				if (s.has("hatch.f") && inKestrelWay(s)) {
					return KESTREL_WAY_END;
				}

				// Phase 28: the hatch is the way once the intercept is read: one click on it and M-31
				// unbolts it from the other side (hatchClosed); the objective says so.
				return s.has("know.hale_trap") || s.has("hatch.f") ? at(D, 22.6, H + 0.5, -3.5) : at(D, 18.0, H + 1.1, -1.4);
			}
			case "m5.cell" -> {
				return null;
			}
			case "m5.escape" -> {
				// Phase 30: the marker leads to the maintenance door's release (it pointed at the
				// hideout, far beyond, and nothing showed that a door had to be unlocked on the way),
				// then into the chamber behind the door, to the panel that bolts it, into the shaft.
				if (s.has("tmp.film")) {
					return step.target();
				}

				if (!s.has("service.open")) {
					net.minecraft.world.phys.Vec3 c = DestroyerInterior.SERVICE_RELEASE.getCenter();
					return at(D, c.x + 0.1, c.y + 0.5, c.z);
				}

				if (!s.has("service.locked")) {
					net.minecraft.world.phys.Vec3 c = DestroyerInterior.SERVICE_LOCK.getCenter();
					return at(D, c.x - 0.1, c.y + 0.5, c.z);
				}

				// Phase 30: bolted with the player outside (by hand only from behind it since Phase
				// 30; kept for any other way): the crawl way is behind the shut door, so the old way
				// to the hideout.
				if (!behindServiceDoor(s)) {
					return step.target();
				}

				net.minecraft.world.phys.Vec3 v = DestroyerInterior.SERVICE_VENT.getCenter();
				return at(D, v.x, R + 0.5, v.z + 1.0);
			}
			case "m1.distract" -> {
				// Over the guard while he stands at the hatch. (Phase 27: once in the crawl way, Kestrel's hideout.)
				if (inCrawlWay(s)) {
					return HIDEOUT_MOUTH;
				}

				Figure guard = s.figure("maint");
				return guard != null ? new Mission.Target(D, guard.position().add(0.0, 2.3, 0.0)) : step.target();
			}
			case "m1.hatch" -> {
				// Phase 27: the hatch until one is in the crawl way, then the way on to Kestrel's
				// hideout (the marker stayed at the hatch behind the player, in the shaft).
				return inCrawlWay(s) ? HIDEOUT_MOUTH : step.target();
			}
			case "m2.drop" -> {
				// The way to the hangar: through the crawl way to flight control; there, the containers.
				Vec3 me = s.local(D);
				return me.z > -33.0 && me.x < 37.0 ? step.target() : at(D, 24.0, H + 1.4, -63.5);
			}
			case "m2.return" -> {
				// Phase 27: the way back: to flight control's door, there to the crawl way's hatch,
				// in the crawl way on to the hideout (the marker pointed at Kestrel through the walls);
				// in the hideout, Kestrel herself.
				Vec3 me = s.local(D);

				// Phase 28: in the side way while its cover is shut: to the cover with the
				// screwdriver, else back out of the hatch (it leads nowhere then).
				if (!s.has(COVER_OPEN) && DestroyerInterior.HANGAR_VENT.inflate(0.0, 0.5, 0.3).contains(me) && me.x >= VENT_COVER.getX() + 0.5) {
					return s.has(DRIVER) ? at(D, VENT_COVER.getX() + 1.0, H + 0.5, VENT_COVER.getZ() + 0.5)
							: at(D, DestroyerInterior.HANGAR_HATCH.getX() + 1.0, H + 0.5, DestroyerInterior.HANGAR_HATCH.getZ() + 0.5);
				}

				if (inCrawlWay(s) && !s.inside(D, HIDDEN)) {
					return HIDEOUT_MOUTH;
				}

				if (me.x < DestroyerInterior.HANGAR_X0 && me.z < -34.5 && me.z > -59.5) {
					return at(D, 10.5, H + 0.6, -35.6);
				}

				return me.x >= DestroyerInterior.HANGAR_X0 && me.z < DestroyerInterior.HANGAR_Z1 ? at(D, DestroyerInterior.HANGAR_X0 + 0.5, H + 1.4, -38.5)
						: step.target();
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

	/** Phase 27: the opening from the crawl way into Kestrel's hideout (where the marker leads from the first crawl way on). */
	static final Mission.Target HIDEOUT_MOUTH = at(D, DestroyerInterior.HIDDEN_MOUTH.getX() + 0.5, H + 0.5, DestroyerInterior.HIDDEN_MOUTH.getZ() + 0.5);

	/** Phase 28: Kestrel's way from the hideout to the detention block (plot space), and its end there. */
	static final AABB KESTREL_WAY = new AABB(23.5, H - 0.5, -4.6, 25.6, H + 1.6, 7.2);
	static final Mission.Target KESTREL_WAY_END = at(D, DestroyerInterior.DETENTION_HATCH.getX() + 0.5, H + 0.5, DestroyerInterior.DETENTION_HATCH.getZ() + 0.5);

	static boolean inKestrelWay(StorySession s) {
		return D.equals(s.here) && KESTREL_WAY.contains(s.local(D).add(0.0, 0.3, 0.0));
	}

	/**
	 * Phase 30: whether the player is behind the maintenance door ({@link #SERVICE_INSIDE}). Its
	 * panel can be reached through the open doorway from outside, but bolts the door only from here
	 * (Scenes.bolt): bolted from outside, the door shut the player out of the crawl way. Should it
	 * be shut with them outside all the same, the escape's objective and marker are the step's own
	 * again (the old way to the hideout, which still ends the escape).
	 */
	static boolean behindServiceDoor(StorySession s) {
		return D.equals(s.here) && SERVICE_INSIDE.contains(s.local(D).add(0.0, 0.3, 0.0));
	}

	/**
	 * Phase 28: the objective shown for a step, where the step's own does not say enough: without
	 * Hale's trust, once the intercept is read, the way into the detention block is Kestrel's hatch
	 * in the hideout, which M-31 unbolts (Phiro found the main door shut and the marker on a hatch
	 * that would not open, with nothing telling him either was meant so).
	 */
	public static String objective(StorySession s, Mission.Step step) {
		// Phase 30: the escape out of the reactor says what to do at each moment: unlock the
		// maintenance door (one click on the panel beside it), wait while it unlocks, go through and
		// bolt it, into the shaft.
		if (step.id().equals("m5.escape") && !s.has("tmp.film")) {
			if (s.has("service.locked")) {
				// Phase 30: bolted with the player outside (see target): the step's own objective
				// as in Phase 29 (its marker the old way to the hideout); the shaft only from behind the door.
				return behindServiceDoor(s) ? "sabercraft.story.obj.m5.escape_shaft" : step.objective();
			}

			if (s.has("service.open")) {
				return "sabercraft.story.obj.m5.escape_bolt";
			}

			return s.has("tmp.release") ? "sabercraft.story.obj.m5.escape_releasing" : "sabercraft.story.obj.m5.escape_release";
		}

		if (step.id().equals("m5.route") && !s.has("trust.hale") && (s.has("know.hale_trap") || s.has("hatch.f"))) {
			return s.has("hatch.f") ? "sabercraft.story.obj.m5.route_crawl" : "sabercraft.story.obj.m5.route_hatch";
		}

		return step.objective();
	}

	/** Phase 27: whether the player is in a crawl way of the Star Destroyer (or already in the hideout). */
	static boolean inCrawlWay(StorySession s) {
		if (!D.equals(s.here)) {
			return false;
		}

		Vec3 me = s.local(D);
		return inVent(me) || HIDDEN.contains(me.add(0.0, 0.5, 0.0));
	}

	@Override
	public boolean locked(StorySession s, String ship, int x, int y, int z) {
		if (!D.equals(ship)) {
			return false;
		}

		int m = s.mission;

		if (x == DestroyerInterior.CX + 4 && y == H && z == -14) {
			// The armory: the security desk opens it (Phase 20: Hale bolts it while he talks).
			// (Phase 25: it opens for Hale as he comes in, whatever else holds it.)
			if (s.has("tmp.hale_entering")) {
				return false;
			}

			return !s.has("armory.open") || s.has("tmp.hale_door");
		}

		if (x == DestroyerInterior.CX - 4 && y == H && z == DestroyerInterior.DETENTION_DOOR_Z0) {
			// Phase 31: never shut onto the player standing in its opening.
			if (inDetentionDoorway(s)) {
				return false;
			}

			// The detention block: open only for the one Hale expects (Phase 20: and for Hale and
			// his trooper leaving after the trap).
			boolean entrance = s.has("trust.hale") && s.at("m5.route");
			boolean haleOut = s.at("m5.cell") && (s.has("tmp.leave.hale") || s.has("tmp.leave.trap1"));
			// Phase 31: and from inside for the player at it (out of the cell, not in Hale's scene),
			// and back in during the break-out from Hale's cell; it shuts again once they walk on.
			boolean exit = !(s.at("m5.cell") && !s.has("cell2.open")) && detentionExit(s);
			boolean shut = !entrance && !haleOut && !exit;

			if (shut && s.at("m5.route") && !s.has("trust.hale")) {
				// Phase 28: without Hale's trust this door is his trap (the intercept says so): it
				// stays shut, and the player at it is told where the way is (it said nothing).
				refusedDoor(s, new Vec3(x, y, z + 1.0), "sabercraft.story.hint.detention_main");
			}

			if (!shut && !haleOut && s.pursued()) {
				// Phase 24: not with troopers on one's heels: Hale's trap (and the room it needs) is
				// not for a chase; the door opens once they lost the player (Phase 31: either way).
				refusedDoor(s, new Vec3(x, y, z + 1.0));
				return true;
			}

			return shut;
		}

		if (x == 48 && y == R && z == DestroyerInterior.SERVICE_DOOR_Z) {
			// Phase 25: the maintenance door: its release pulled by hand, then bolted from inside.
			return !s.has("service.open") || s.has("service.locked");
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

	/** Phase 24: the player at a door the chase keeps shut is told why (now and then, not every tick). */
	private static void refusedDoor(StorySession s, Vec3 door) {
		refusedDoor(s, door, Story.HUNTED);
	}

	/** Phase 28: as above, with the reason given. */
	private static void refusedDoor(StorySession s, Vec3 door, String why) {
		if (D.equals(s.here) && s.local(D).distanceTo(door) < 3.2 && !s.timer("door_hint")) {
			s.timer("door_hint", Story.HUNTED.equals(why) ? 60 : 160);
			s.hint(why);
		}
	}

	@Override
	public boolean escapeHatch(StorySession s, String ship, int x, int y, int z) {
		// Phase 26: the crawl way out of the reactor's maintenance chamber is the planned escape
		// after the copy (Phase 25): it opens with the troopers on one's heels, alarm or not.
		return D.equals(ship) && s.at("m5.escape") && DestroyerInterior.SERVICE_HATCH.equals(new BlockPos(x, y, z));
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

		// Phase 28: Kestrel's hatch, bolted on the far side, while one looks for a way into the
		// detention block without Hale: a click sends M-31 through its grille to unbolt it (only the
		// droid key did, and nothing said so); without him near, the player is told to fetch him.
		if (D.equals(ship) && DestroyerInterior.HIDEOUT_HATCH.equals(new BlockPos(x, y, z)) && s.at("m5.route") && !s.has("trust.hale") && !s.has("hatch.f")) {
			if (s.has("tmp.m31_unbolt")) {
				return "sabercraft.story.hint.hatch_m31_busy";
			}

			ServerPlayer player = s.player();
			com.phiro.sabercraft.droid.DroidEntity mouse = player != null && s.has("droid.m31")
					? com.phiro.sabercraft.droid.Droids.droid(player, com.phiro.sabercraft.droid.DroidType.MOUSE) : null;

			if (mouse != null && mouse.level() == player.level() && mouse.distanceTo(player) < M31_UNBOLT_REACH) {
				s.set("tmp.m31_unbolt");
				s.timer("m31_unbolt", M31_UNBOLT_TICKS);
				mouse.playVoice("ability", 1.0F);
				return "sabercraft.story.hint.hatch_m31_go";
			}

			return "sabercraft.story.hint.hatch_m31";
		}

		return Chapter.super.hatchClosed(s, ship, x, y, z);
	}

	/** Phase 28: how near M-31 must be to go through the hatch's grille, and how long he takes. */
	static final double M31_UNBOLT_REACH = 14.0;
	static final int M31_UNBOLT_TICKS = 34;

	/** Phase 31: the detention block's main door's plane (plot x), the block west of it, the lobby east. */
	static final double DETENTION_PLANE = DestroyerInterior.CX - 4 + 0.5;

	/**
	 * Phase 31: whether the player stands at the detention block's main door to go through it: inside
	 * the block by it (or in its opening), or, breaking out of Hale's cell, just outside it too (so
	 * one who went out before freeing R2-V7 or Kestrel can get back in: the hatches stay bolted).
	 */
	static boolean detentionExit(StorySession s) {
		if (!D.equals(s.here)) {
			return false;
		}

		Vec3 me = s.local(D);
		double z0 = DestroyerInterior.DETENTION_DOOR_Z0;

		if (me.z < z0 - 0.6 || me.z > z0 + 2.6 || Math.abs(me.y - H) > 1.0) {
			return false;
		}

		if (me.x >= DETENTION_PLANE - 3.0 && me.x <= DETENTION_PLANE + 0.85) {
			return true;
		}

		return prisonBreak(s) && me.x > DETENTION_PLANE && me.x <= DETENTION_PLANE + 3.0;
	}

	/** Phase 31: whether the player stands in the open main door of the detention block (it must not shut on them). */
	static boolean inDetentionDoorway(StorySession s) {
		ServerLevel level = interiors(s);

		if (level == null || !D.equals(s.here)
				|| !level.getBlockState(s.destroyer.offset(DestroyerInterior.CX - 4, H, DestroyerInterior.DETENTION_DOOR_Z0)).isAir()) {
			return false;
		}

		Vec3 me = s.local(D);
		AABB doorway = new AABB(DestroyerInterior.CX - 4, H, DestroyerInterior.DETENTION_DOOR_Z0, DestroyerInterior.CX - 3, H + 3,
				DestroyerInterior.DETENTION_DOOR_Z0 + 2);
		return doorway.intersects(new AABB(me.x - 0.3, me.y, me.z - 0.3, me.x + 0.3, me.y + 1.8, me.z + 0.3));
	}

	// --- Checkpoints (Phase 20) --------------------------------------------------------------------

	@Override
	public boolean checkpoint(StorySession s, Mission.Step step) {
		// Out of Hale's cell the checkpoint stays the cell's; through the crawl way it is at its hatch.
		// (Phase 31: all the way out of the prison, freeing R2-V7 and Kestrel included.)
		if (s.has("m5.trapped") && PRISON_STEPS.contains(step.id())) {
			return false;
		}

		if (step.id().equals("m5.r2")) {
			return !s.has("trust.hale");
		}

		return step.checkpoint();
	}

	/**
	 * Phase 27: a kept place counts if it is the step's safe place, the hideout's (where the
	 * hideout makes checkpoints), the second cell's after the trap, or a place well inside a crawl
	 * way. Anything else (a save from before Phase 27: in a hatch's opening, on the cot; or a safe
	 * place that has since moved) is not used again.
	 */
	@Override
	public boolean trusts(StorySession s, Mission.Step step, String ship, Vec3 at) {
		Mission.Place own = this.place(s, step);

		// A save from before keeps the airlock's old checkpoint, within the inner door's reach
		// (moved only 0.7 blocks, so the tolerance below would still take it).
		if (D.equals(ship) && "m1.droid".equals(step.id()) && at.distanceTo(AIRLOCK) < 0.05) {
			return false;
		}

		if (own.ship().equals(ship) && own.at().distanceTo(at) < 1.0) {
			return true;
		}

		if (!D.equals(ship)) {
			return false;
		}

		if (at.distanceTo(HIDEOUT) < 0.6 && s.mission >= 1 && !NO_SAFE_CHECKPOINT.contains(step.id())) {
			return true;
		}

		if (at.distanceTo(CELL_2) < 1.0 && s.has("m5.trapped")) {
			return true;
		}

		Vec3 crawl = NO_SAFE_CHECKPOINT.contains(step.id()) ? null : crawlAnchor(at);
		return crawl != null && crawl.distanceTo(at) < 0.05;
	}

	/** Phase 27: the steps that begin in a staged chase or fight (no time for bearings there). */
	@Override
	public boolean scriptedRespawn(StorySession s, Mission.Step step) {
		return SCRIPTED_RESPAWN.contains(step.id());
	}

	static final java.util.Set<String> SCRIPTED_RESPAWN = java.util.Set.of("m5.escape", "m6.escape1", "m6.escape2", "m6.duel", "m6.falcon");

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
				// Out of the cell (a file of Phase 19): in the block, behind the pillar (Phase 27).
				if (!s.has("hatch.f")) {
					return new Mission.Place(D, DETENTION_SAFE, 180.0F);
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

		// Phase 28: back at the duel's checkpoint the droids wait behind the hangar's door again.
		if (droidsWait(s)) {
			droidsAside(s);
		}
	}

	/**
	 * Phase 28 (Phiro): during the duel the droids neither follow the player round the arena nor
	 * stand in the films' shots: they wait in the corridor behind the hangar's door, one by each
	 * wall, facing the hangar (plot space), and come along again once she has fled.
	 */
	static final Vec3 DUEL_WAIT_M31 = new Vec3(37.75, H, -12.6);
	static final Vec3 DUEL_WAIT_R2 = new Vec3(42.3, H, -12.9);

	static boolean droidsWait(StorySession s) {
		return s.at("m6.duel") && !s.has("gone.shadow");
	}

	/** Phase 28: where that droid waits during the duel (plot space). */
	public static Vec3 duelWait(com.phiro.sabercraft.droid.DroidType type) {
		return type == com.phiro.sabercraft.droid.DroidType.MOUSE ? DUEL_WAIT_M31 : DUEL_WAIT_R2;
	}

	/** Phase 28: puts the story's droids at their places behind the hangar's door, staying there. */
	static void droidsAside(StorySession s) {
		ServerPlayer player = s.player();

		if (player == null) {
			return;
		}

		Vec3 corner = Vec3.atLowerCornerOf(s.destroyer);

		for (com.phiro.sabercraft.droid.DroidType type : Story.storyDroids(s)) {
			Vec3 at = duelWait(type).add(corner);
			com.phiro.sabercraft.droid.DroidEntity droid = com.phiro.sabercraft.droid.Droids.droid(player, type);

			if (droid != null && droid.isStaying() && droid.level() == player.level() && droid.position().distanceTo(at) < 1.0) {
				continue;
			}

			com.phiro.sabercraft.droid.Droids.place(player, type, at, 180.0F, true);
		}
	}

	/** Phase 28: the droids follow the player again after the duel. */
	static void droidsBack(StorySession s) {
		ServerPlayer player = s.player();

		if (player == null) {
			return;
		}

		for (com.phiro.sabercraft.droid.DroidType type : Story.storyDroids(s)) {
			com.phiro.sabercraft.droid.DroidEntity droid = com.phiro.sabercraft.droid.Droids.droid(player, type);

			if (droid != null && droid.isStaying()) {
				droid.setStaying(false);
			}
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
		// Phase 27: not out of the lift behind the barrier (it is locked down; nobody comes that way).
		return D.equals(ship) ? bridgeClosed(s) ? ENTRIES.subList(1, ENTRIES.size()) : ENTRIES : List.of();
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
