package com.phiro.sabercraft.gametest;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.ability.SaberAbilities;
import com.phiro.sabercraft.character.Outfit;
import com.phiro.sabercraft.character.OutfitSync;
import com.phiro.sabercraft.client.ability.SaberAbilityClient;
import com.phiro.sabercraft.client.character.CharacterConfig;
import com.phiro.sabercraft.client.character.OutfitClient;
import com.phiro.sabercraft.client.droid.DroidControl;
import com.phiro.sabercraft.client.interior.HangarFalcon;
import com.phiro.sabercraft.client.interior.InteriorClient;
import com.phiro.sabercraft.client.interior.MeshAudit;
import com.phiro.sabercraft.client.menu.CharacterMenuScreen;
import com.phiro.sabercraft.client.menu.SaberMenuScreen;
import com.phiro.sabercraft.client.music.StoryMusic;
import com.phiro.sabercraft.client.saber.SaberHandState;
import com.phiro.sabercraft.client.saber.SaberTracker;
import com.phiro.sabercraft.client.select.SaberCustomizeScreen;
import com.phiro.sabercraft.client.story.StoryCinema;
import com.phiro.sabercraft.client.story.StoryClient;
import com.phiro.sabercraft.droid.DroidEntity;
import com.phiro.sabercraft.droid.DroidType;
import com.phiro.sabercraft.droid.Droids;
import com.phiro.sabercraft.interior.Crew;
import com.phiro.sabercraft.interior.DestroyerInterior;
import com.phiro.sabercraft.interior.InteriorPlan;
import com.phiro.sabercraft.interior.InteriorSteps;
import com.phiro.sabercraft.interior.InteriorSystems;
import com.phiro.sabercraft.interior.ShipInteriors;
import com.phiro.sabercraft.interior.Vents;
import com.phiro.sabercraft.network.StoryActionPayload;
import com.phiro.sabercraft.saber.SaberVariant;
import com.phiro.sabercraft.story.Chapter1;
import com.phiro.sabercraft.story.Figure;
import com.phiro.sabercraft.story.Story;
import com.phiro.sabercraft.story.StorySession;
import com.phiro.sabercraft.story.StoryTesting;

/**
 * Phase 26: the story's polish after Phiro's playthrough of Phase 25, the parts a test can play
 * (how it looks and sounds in motion stays for the manual test, see {@code docs/development/phase-26-status.md}).
 *
 * <ol>
 * <li>A crawl way's hatch shut from close by with the use key held stays shut; let go and pressed
 * again, it opens (1).</li>
 * <li>The reactor's escape hatch opens with the troopers after one; other crawl ways do not (2).</li>
 * <li>Doors open for who comes towards them or stands in them, not for who walks past (3).</li>
 * <li>The cell: M-31 drives to the field's emitter, works on it, the field stutters and dies (4).</li>
 * <li>Kestrel beside the crawl way (not in it); spoken to off her seat she stands and talks (5).</li>
 * <li>The lab's guard keeps watch, stays at his post, sees one from the front, is overpowered from
 * behind and lies where he fell, also when the step goes on (6).</li>
 * <li>Droids keep off the story's pedestals and consoles (7).</li>
 * <li>The finale's troopers shoot more often, never two bolts at once (8).</li>
 * <li>The boss: more strength, the lunge, the ring's rush, the fierce end; the ring never in her
 * body (9).</li>
 * <li>The blade lock's scene from running, striking and blocking: set up the same each time, the
 * player held in place, facing her afterwards (10).</li>
 * <li>The Falcon under the hangar's field while one escapes, drawn from the start; the lift to the
 * bridge shut in the finale only (11).</li>
 * <li>Kestrel's own look back aboard the Falcon after the finale (12).</li>
 * <li>The music before the duel and its crossfades; the new sounds (13).</li>
 * <li>The random character and saber outside the story, taken over at once (14).</li>
 * <li>Neither in the story (15).</li>
 * </ol>
 */
public class Phase26GameTest implements FabricClientGameTest {
	private static final int H = DestroyerInterior.HANGAR + 1;
	private static final int R = DestroyerInterior.REACTOR_BOTTOM;
	private static final String SABER = "minecraft:netherite_sword[minecraft:custom_model_data={strings:[\"sabercraft:standard\",\"sabercraft:blue\"]}]";
	private static final List<String> NEW_SOUNDS = List.of("story.droid_work", "story.field_flicker", "story.ring_spin", "story.hack", "story.console",
			"story.latch", "story.vent_crawl", "ship.deck_step", "ship.corridor_air");

	private ClientGameTestContext context;
	private TestServerContext server;
	private UUID self;
	private final List<String> problems = new ArrayList<>();

	@Override
	public void runTest(ClientGameTestContext context) {
		this.context = context;
		context.runOnClient(client -> {
			client.options.renderDistance().set(10);
			client.options.setCameraType(CameraType.FIRST_PERSON);

			if (client.gui.hud.isHidden()) {
				client.gui.hud.toggle();
			}
		});

		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			this.run(context, singleplayer);
		}

		if (!this.problems.isEmpty()) {
			this.problems.forEach(problem -> SaberCraft.LOGGER.error("PHASE26 PROBLEM: {}", problem));
			throw new AssertionError("Phase 26: " + this.problems.size() + " problem(s): " + String.join(" | ", this.problems));
		}
	}

	private void run(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		String outfit = context.computeOnClient(client -> CharacterConfig.get().outfit);

		try {
			context.waitTicks(100);
			this.server = singleplayer.getServer();
			this.server.runCommand("gamerule advance_time false");
			this.server.runCommand("gamerule spawn_mobs false");
			this.server.runCommand("time set day");
			this.server.runCommand("gamemode survival @a");
			context.waitTicks(20);
			this.self = context.computeOnClient(client -> client.player.getUUID());

			this.part("texts", this::texts);
			this.part("randomCharacter", this::randomCharacter);
			this.part("randomSaber", this::randomSaber);
			this.part("ringPoses", this::ringPoses);
			this.part("doorLogic", this::doorLogic);
			this.part("hatch", this::hatch);
			this.part("escapeHatch", this::escapeHatch);
			this.part("cell", this::cell);
			this.part("kestrel", this::kestrel);
			this.part("labGuard", this::labGuard);
			this.part("droids", this::droids);
			this.part("lift", this::lift);
			this.part("shots", this::shots);
			this.part("falcon", this::falcon);
			this.part("boss", this::boss);
			this.part("half", () -> this.halfScene("run"));
			this.part("half", () -> this.halfScene("strike"));
			this.part("half", () -> this.halfScene("block"));
			this.part("kestrelArmour", this::kestrelArmour);
			this.part("restrictions", this::restrictions);
		} catch (AssertionError | RuntimeException e) {
			this.problem("aborted: " + e);
			SaberCraft.LOGGER.error("PHASE26 aborted", e);
		} finally {
			context.getInput().releaseKey(options -> options.keyUp);
			context.getInput().releaseKey(options -> options.keySprint);
			context.getInput().releaseKey(SaberAbilityClient.GUARD_KEY);

			if (this.server != null && this.on(s -> this.session(s) != null)) {
				this.leave();
			}

			context.runOnClient(client -> OutfitClient.choose(outfit));
		}
	}

	/** Runs one part of the test (all of them, or only those named in PHASE26_ONLY, for working on one). */
	private void part(String name, Runnable part) {
		String only = System.getenv("PHASE26_ONLY");

		if (only == null || only.isBlank() || List.of(only.split(",")).contains(name)) {
			part.run();
		}
	}

	// --- 3, 13: no solution in the cell's texts, no attack texts in the duel; the new sounds --------------------

	private void texts() {
		String de = this.context.computeOnClient(client -> read(client, "lang/de_de.json"));
		String en = this.context.computeOnClient(client -> read(client, "lang/en_us.json"));
		check(!de.isEmpty() && !en.isEmpty(), "the language files cannot be read");
		check(!de.toLowerCase(Locale.ROOT).contains("durchbei"), "the German texts still have M-31 bite through a cable");
		check(!de.contains("beißt das Kabel des Kraftfelds"), "the cell's hint still gives the solution away");
		check(!de.contains("\"sabercraft.story.hint.duel_") && !en.contains("\"sabercraft.story.hint.duel_"), "the duel still announces her attacks in texts");
		check(!de.contains("\"sabercraft.story.duel.blow") && !de.contains("\"sabercraft.story.duel.force") && !de.contains("\"sabercraft.story.duel.open\""),
				"her moves are still named under her strength bar");
		check(de.contains("\"sabercraft.story.hint.lift_locked\"") && en.contains("\"sabercraft.story.hint.lift_locked\""), "no text for the bridge's locked lift");
		List<String> missing = this.context.computeOnClient(client -> NEW_SOUNDS.stream()
				.filter(id -> client.getSoundManager().getSoundEvent(SaberCraft.id(id)) == null).toList());
		check(missing.isEmpty(), "sounds not in sounds.json: " + missing);
	}

	private static String read(Minecraft client, String path) {
		return client.getResourceManager().getResource(SaberCraft.id(path)).map(resource -> {
			try (BufferedReader reader = resource.openAsReader()) {
				return reader.lines().collect(Collectors.joining("\n"));
			} catch (java.io.IOException e) {
				return "";
			}
		}).orElse("");
	}

	// --- 14: the random character and saber, outside the story -------------------------------------------------

	private void randomCharacter() {
		this.server.runCommand("item replace entity @a weapon.mainhand with minecraft:air");
		this.context.waitTicks(3);
		this.context.setScreen(CharacterMenuScreen::new);
		this.context.waitTicks(3);
		String last = this.context.computeOnClient(client -> CharacterConfig.get().outfit);

		for (int i = 0; i < 4; i++) {
			int seed = 2600 + i;
			String picked = this.context.computeOnClient(client -> {
				Outfit pick = client.gui.screen() instanceof CharacterMenuScreen menu ? menu.randomPick(RandomSource.create(seed)) : null;
				return pick == null ? "" : pick.id().toString();
			});

			if (picked.isEmpty()) {
				this.problem("the random character picks nothing (outside the story)");
				break;
			}

			check(!picked.equals(last), "the random character is the one worn already (" + picked + ")");
			check(Outfit.CHARACTERS.stream().anyMatch(o -> o.id().toString().equals(picked)) || Outfit.OUTFITS.stream().anyMatch(o -> o.id().toString().equals(picked)),
					"the random character is not one of the menu's (" + picked + ")");
			check(picked.equals(this.context.computeOnClient(client -> CharacterConfig.get().outfit)), "the random character is not put on at once (" + picked + ")");
			// The server takes it at once, or (pressed again within half a second) as soon as it may.
			String known = this.serverOutfit(picked, 30);
			check(picked.equals(known), "the server does not know the random character " + picked + " (" + known + ")");
			last = picked;
		}

		// Pressed twice quickly: the second one is what the server ends up with (it was dropped before).
		String twice = this.context.computeOnClient(client -> {
			Outfit first = client.gui.screen() instanceof CharacterMenuScreen menu ? menu.randomPick(RandomSource.create(77)) : null;
			Outfit second = client.gui.screen() instanceof CharacterMenuScreen menu ? menu.randomPick(RandomSource.create(78)) : null;
			return first == null || second == null ? "" : second.id().toString();
		});
		String known = this.serverOutfit(twice, 40);
		check(!twice.isEmpty() && twice.equals(known), "two quick random picks leave the server on the first one (" + twice + " / " + known + ")");

		check(this.context.computeOnClient(client -> client.gui.screen() instanceof CharacterMenuScreen), "the character menu closes after a random pick");
		this.context.takeScreenshot("phase26_random_character");

		// With a saber in hand a film character brings their saber along (as a card clicked).
		this.context.setScreen(() -> null);
		this.server.runCommand("item replace entity @a weapon.mainhand with " + SABER);
		this.context.waitTicks(10);
		this.context.setScreen(CharacterMenuScreen::new);
		this.context.waitTicks(3);

		for (int i = 0; i < 6; i++) {
			int seed = 2700 + i;
			String[] pick = this.context.computeOnClient(client -> {
				Outfit outfit = client.gui.screen() instanceof CharacterMenuScreen menu ? menu.randomPick(RandomSource.create(seed)) : null;
				return outfit == null ? null : new String[] {outfit.id().toString(), String.valueOf(outfit.character()), outfit.suggested().hilt().toString(),
						outfit.suggested().color().toString()};
			});

			if (pick == null || !"true".equals(pick[1])) {
				continue;
			}

			this.context.waitTicks(6);
			ItemStack held = this.on(s -> this.me(s).getMainHandItem().copy());
			Identifier hilt = SaberVariant.hiltOf(held);
			Identifier color = SaberVariant.colorOf(held);
			check(pick[2].equals(String.valueOf(hilt)) && pick[3].equals(String.valueOf(color)),
					"the random character " + pick[0] + " does not bring their saber along (" + hilt + ", " + color + ")");
			break;
		}

		this.context.setScreen(() -> null);
		this.context.waitTicks(2);
	}

	/** The outfit the server knows for the player, waiting up to that many ticks for that one. */
	private String serverOutfit(String wanted, int ticks) {
		String known = "";

		for (int i = 0; i <= ticks; i++) {
			known = this.on(s -> OutfitSync.outfitOf(this.self).map(Identifier::toString).orElse(""));

			if (known.equals(wanted)) {
				return known;
			}

			this.context.waitTick();
		}

		return known;
	}

	private void randomSaber() {
		this.server.runCommand("item replace entity @a weapon.mainhand with " + SABER);
		this.context.waitTicks(10);
		this.context.setScreen(() -> new SaberCustomizeScreen(InteractionHand.MAIN_HAND, Minecraft.getInstance().player.getMainHandItem()));
		this.context.waitTicks(3);
		int randoms = this.context.computeOnClient(client -> SaberCustomizeScreen.randomsForTest());
		SaberVariant last = this.context.computeOnClient(client -> client.gui.screen() instanceof SaberCustomizeScreen menu ? menu.choiceForTest() : null);

		for (int i = 0; i < 6; i++) {
			int seed = 2800 + i;
			SaberVariant pick = this.context.computeOnClient(client -> client.gui.screen() instanceof SaberCustomizeScreen menu ? menu.randomSaber(RandomSource.create(seed))
					: null);

			if (pick == null) {
				this.problem("the random saber picks nothing (outside the story)");
				break;
			}

			check(!pick.equals(last), "the random saber is the one chosen already (" + pick + ")");
			check(pick.isKnown() && SaberVariant.COLORS.contains(pick.color()), "the random saber is not a known one (" + pick + ")");
			check(SaberVariant.styleFits(pick.hilt(), pick.style()), "the random saber's style does not fit its hilt (" + pick + ")");
			this.context.waitTicks(5);
			ItemStack held = this.on(s -> this.me(s).getMainHandItem().copy());
			SaberVariant now = new SaberVariant(SaberVariant.hiltOf(held), SaberVariant.colorOf(held), SaberVariant.styleOf(held));
			check(pick.equals(now), "the random saber is not taken over at once: chosen " + pick + ", held " + now);
			check(this.context.computeOnClient(client -> client.gui.screen() instanceof SaberCustomizeScreen), "the saber menu closes after a random saber");
			last = pick;
		}

		check(this.context.computeOnClient(client -> SaberCustomizeScreen.randomsForTest()) == randoms + 6, "not every random saber was counted as taken over");
		this.context.takeScreenshot("phase26_random_saber");
		this.context.setScreen(() -> null);
		this.context.waitTicks(2);
	}

	// --- 9: her Inquisitor's ring never in her body ------------------------------------------------------------

	private void ringPoses() {
		List<String> lines = this.context.computeOnClient(client -> MeshAudit.ringInBody());
		lines.forEach(line -> SaberCraft.LOGGER.info("PHASE26 RING {}", line));
		check(lines.get(0).contains(", 0 with"), "her ring saber goes through her body: " + lines.get(0) + (lines.size() > 1 ? " e.g. " + lines.get(1) : ""));
	}

	// --- 3: who opens a door ----------------------------------------------------------------------------------

	private void doorLogic() {
		// A door in a wall at z 10 (its plane at 10.5), three blocks wide (x 0 to 2): the way through runs along z.
		InteriorPlan.Door door = InteriorPlan.Door.wall(false, 10, 0, 2, 0, 2);
		List<String> wrong = new ArrayList<>();
		this.door(wrong, "a player walking past", door, true, new Vec3(1.0, 0.0, 12.1), new Vec3(0.6, 0.0, 12.1), false, false);
		this.door(wrong, "a patrol walking past close by", door, false, new Vec3(1.0, 0.0, 11.4), new Vec3(0.6, 0.0, 11.4), false, false);
		this.door(wrong, "a patrol walking past an open door", door, false, new Vec3(1.0, 0.0, 11.4), new Vec3(0.6, 0.0, 11.4), true, false);
		this.door(wrong, "a player walking towards it", door, true, new Vec3(1.5, 0.0, 12.4), new Vec3(1.5, 0.0, 12.6), false, true);
		// (Phase 29: the story's people open a door only on their way through it, see Phase29GameTest; coming near is not enough.)
		this.door(wrong, "a trooper only walking towards it", door, false, new Vec3(1.5, 0.0, 12.4), new Vec3(1.5, 0.0, 12.6), false, false);
		this.door(wrong, "a trooper on his way through it", door, false, new Vec3(1.5, 0.0, 12.4), new Vec3(1.5, 0.0, 12.6), false, true,
				List.of(new Vec3(1.5, 0.0, 11.0), new Vec3(1.5, 0.0, 9.0)));
		this.door(wrong, "a trooper turning before it", door, false, new Vec3(1.5, 0.0, 12.0), new Vec3(1.5, 0.0, 12.4), false, false,
				List.of(new Vec3(1.5, 0.0, 11.8), new Vec3(1.5, 0.0, 12.6), new Vec3(1.5, 0.0, 14.0)));
		this.door(wrong, "a player in the doorway", door, true, new Vec3(1.5, 0.0, 10.9), new Vec3(1.5, 0.0, 10.9), false, true);
		this.door(wrong, "a trooper standing a step before it", door, false, new Vec3(1.5, 0.0, 11.3), new Vec3(1.5, 0.0, 11.3), false, false);
		this.door(wrong, "a player standing before the open door", door, true, new Vec3(1.5, 0.0, 12.0), new Vec3(1.5, 0.0, 12.0), true, true);
		this.door(wrong, "a player standing before the shut door", door, true, new Vec3(1.5, 0.0, 12.0), new Vec3(1.5, 0.0, 12.0), false, false);
		this.door(wrong, "a player walking away", door, true, new Vec3(1.5, 0.0, 11.9), new Vec3(1.5, 0.0, 11.6), true, false);
		this.door(wrong, "someone off to the side", door, true, new Vec3(4.2, 0.0, 11.2), new Vec3(4.2, 0.0, 11.6), false, false);
		this.door(wrong, "a player stepping out of a lift before it", door, true, new Vec3(1.5, 0.0, 12.0), null, false, true);
		this.door(wrong, "a figure just put before it", door, false, new Vec3(1.5, 0.0, 12.0), null, false, false);
		check(wrong.isEmpty(), "doors decide wrongly: " + wrong);
	}

	private void door(List<String> wrong, String who, InteriorPlan.Door door, boolean player, Vec3 at, @Nullable Vec3 was, boolean open, boolean expected) {
		if (InteriorSystems.wantedForTest(door, player, at, was, open) != expected) {
			wrong.add(who + (expected ? " does not open it" : " opens it"));
		}
	}

	/** Phase 29: the same for one of the story's people with where they mean to walk next. */
	private void door(List<String> wrong, String who, InteriorPlan.Door door, boolean player, Vec3 at, @Nullable Vec3 was, boolean open, boolean expected,
			List<Vec3> intent) {
		if (InteriorSystems.wantedForTest(door, player, at, was, null, intent, open, false) != expected) {
			wrong.add(who + (expected ? " does not open it" : " opens it"));
		}
	}

	// --- 1: the hatch shut from close by stays shut; the steps aboard ----------------------------------------------

	private void hatch() {
		this.enter(1, "m2.return");
		this.unseen();
		BlockPos cell = DestroyerInterior.HATCHES.get(0).cell();
		double hx = cell.getX();
		double hz = cell.getZ() + 0.5;
		// Steps on the deck plates: the deck's own step sounds.
		int[] counts = this.on(s -> InteriorSteps.countsForTest());
		this.put(new Vec3(hx + 6.0, H, hz), 90.0F, 0.0F);
		this.context.getInput().holdKeyFor(options -> options.keyUp, 20);
		check(this.on(s -> InteriorSteps.countsForTest())[0] > counts[0], "no deck steps are heard walking aboard");
		// Open from the corridor (aimed at the hatch's middle).
		Vec3 eye = new Vec3(hx + 1.9, H + 1.62, hz);
		float down = (float) Math.toDegrees(Math.atan2(eye.y - (H + 0.5), eye.x - (hx + 0.5)));
		this.put(new Vec3(hx + 1.9, H, hz), 90.0F, down);
		this.context.getInput().pressKey(options -> options.keyUse);
		this.context.waitTicks(8);

		if (!this.hatchOpen(cell)) {
			this.problem("the use key does not open the hatch from the corridor");
			this.server.runOnServer(s -> InteriorSystems.useForTest(this.me(s), this.world(s, cell)));
			this.context.waitTicks(5);
		}

		this.clientSees(cell, true);
		// Shut from close by, the key held down a while (as one does): it stays shut.
		int shut = this.context.computeOnClient(client -> InteriorClient.hatchesForTest());
		int refused = this.on(s -> Vents.reopensRefusedForTest());
		this.context.getInput().holdKeyFor(options -> options.keyUse, 24);
		this.context.waitTicks(4);
		boolean stays = !this.hatchOpen(cell);
		check(stays, "the hatch opens again while the use key is still held after shutting it");
		check(this.context.computeOnClient(client -> InteriorClient.hatchesForTest()) == shut + 1, "the held key did not shut the hatch once");
		SaberCraft.LOGGER.info("PHASE26 HATCH: shut and held: stays shut {}, reopens refused by the server {}", stays,
				this.on(s -> Vents.reopensRefusedForTest()) - refused);
		this.context.takeScreenshot("phase26_hatch_held_shut");
		// Let go, pressed again: it opens as before.
		this.clientSees(cell, false);
		this.context.waitTicks(10);
		this.context.getInput().pressKey(options -> options.keyUse);
		this.context.waitTicks(8);
		check(this.hatchOpen(cell), "the hatch does not open again on a new press of the use key");

		if (!this.hatchOpen(cell)) {
			this.server.runOnServer(s -> InteriorSystems.useForTest(this.me(s), this.world(s, cell)));
			this.context.waitTicks(5);
		}

		// In, crawling: a knee on sheet metal.
		this.put(new Vec3(hx + 1.55, H, hz), 90.0F, 0.0F);
		this.context.getInput().holdKey(options -> options.keyShift);
		this.context.getInput().holdKeyFor(options -> options.keyUp, 30);
		this.context.getInput().releaseKey(options -> options.keyShift);
		this.context.getInput().holdKeyFor(options -> options.keyUp, 25);
		boolean crawling = this.on(s -> this.me(s).getPose() == net.minecraft.world.entity.Pose.SWIMMING);
		check(!crawling || this.on(s -> InteriorSteps.countsForTest())[1] > counts[1], "crawling in the vent sounds like walking on stone");
		SaberCraft.LOGGER.info("PHASE26 STEPS: deck steps {}, crawls {} (crawling {})", this.on(s -> InteriorSteps.countsForTest())[0] - counts[0],
				this.on(s -> InteriorSteps.countsForTest())[1] - counts[1], crawling);
		check(crawling, "one does not crawl into the vent (the test could not hear the crawl)");
		this.put(new Vec3(hx + 4.0, H, hz), 90.0F, 0.0F);
	}

	// --- 2: the escape vent with the troopers after one ----------------------------------------------------------

	private void escapeHatch() {
		this.enter(4, "m5.escape");
		this.server.runOnServer(s -> StoryTesting.unseen(this.session(s), 0));
		this.server.runOnServer(s -> {
			StorySession session = this.session(s);
			Figure seer = session.figures.stream().filter(f -> f.observer && f.mode != Figure.DOWN).findFirst().orElse(null);

			if (seer != null) {
				StoryTesting.detect(session, seer);
			}
		});
		this.context.waitTicks(3);
		check(this.on(s -> StoryTesting.pursued(this.session(s))), "being seen does not count as being hunted (m5.escape)");
		BlockPos hatch = DestroyerInterior.SERVICE_HATCH;
		this.put(new Vec3(hatch.getX() + 0.5, R, hatch.getZ() + 1.2), 180.0F, 0.0F);
		int refused = this.on(s -> Story.hatchesRefusedForTest());
		boolean shut = this.on(s -> Story.hatchClosed(this.me(s), this.session(s).destroyer, hatch, Direction.SOUTH));
		check(!shut, "the reactor's escape vent stays shut while the troopers are after the player");
		check(this.on(s -> Story.hatchesRefusedForTest()) == refused, "the escape vent was refused for the chase");
		// Opened for real (the click's way): it opens.
		this.server.runOnServer(s -> InteriorSystems.useForTest(this.me(s), this.world(s, hatch)));
		this.context.waitTicks(4);
		check(this.hatchOpen(hatch), "the escape vent does not open with the troopers after one");
		this.context.takeScreenshot("phase26_escape_vent");
		// Any other crawl way stays shut before the hunters' eyes (Phase 24, unchanged).
		BlockPos other = DestroyerInterior.HIDEOUT_HATCH;
		this.server.runOnServer(s -> {
			StorySession session = this.session(s);
			Figure seer = session.figures.stream().filter(f -> f.observer && f.mode != Figure.DOWN).findFirst().orElse(null);

			if (seer != null) {
				StoryTesting.detect(session, seer);
			}
		});
		this.put(new Vec3(other.getX() + 0.5, H, other.getZ() + 1.6), 180.0F, 0.0F);

		if (this.on(s -> StoryTesting.pursued(this.session(s)))) {
			check(this.on(s -> Story.hatchClosed(this.me(s), this.session(s).destroyer, other, Direction.SOUTH)), "another crawl way opens while the player is hunted");
		}
	}

	// --- 4: M-31 takes the cell's field down ---------------------------------------------------------------------

	private void cell() {
		this.enter(4, "m5.route");
		this.unseen();
		this.flags("trust.hale", "droid.m31");
		this.server.runOnServer(s -> Story.updateDroids(this.session(s)));
		this.context.waitTicks(20);
		this.put(new Vec3(31.5, H, 6.5), 180.0F, 0.0F);
		this.waitStep("m5.cell", 80);

		for (int i = 0; i < 24 && this.on(s -> StoryTesting.line(this.session(s))).isEmpty(); i++) {
			this.context.waitTicks(5);
		}

		this.converse();
		this.context.waitTicks(10);

		if (!this.on(s -> this.session(s).has("m5.trapped"))) {
			this.problem("Hale's trap does not put the player in the cell (cannot test M-31 at the field)");
			return;
		}

		BlockPos front = new BlockPos(31, H, DestroyerInterior.CELL_FRONT);
		check(this.on(s -> this.level(s).getBlockState(this.world(s, front)).is(Blocks.BARRIER)), "cell 2 has no force field");
		int[] before = this.on(s -> StoryTesting.cellField());
		int effects = this.context.computeOnClient(client -> StoryClient.effectsSeenForTest());
		this.context.waitTicks(30);

		// The droid key (the story tells M-31 what to do here); again, with M-31 called, if it was still coming.
		for (int attempt = 0; attempt < 3 && this.on(s -> StoryTesting.cellFieldStage(this.session(s))) == 0 && !this.on(s -> this.session(s).has("cell2.open")); attempt++) {
			this.context.getInput().pressKey(DroidControl.ABILITY_KEY);
			this.context.waitTicks(10);

			if (this.on(s -> StoryTesting.cellFieldStage(this.session(s))) == 0) {
				this.server.runOnServer(s -> {
					DroidEntity droid = Droids.droid(this.me(s), DroidType.MOUSE);

					if (droid != null) {
						droid.teleportToOwner();
					}
				});
				this.context.waitTicks(10);
			}
		}

		check(this.on(s -> StoryTesting.cellField())[0] == before[0] + 1, "the droid key does not set M-31 to work on the field");
		check(this.on(s -> this.level(s).getBlockState(this.world(s, front)).is(Blocks.BARRIER)), "the field goes off at once with the droid key (no scene)");
		boolean working = false;
		int workTicks = 0;
		double atPanel = -1.0;

		for (int i = 0; i < 260 && !this.on(s -> this.session(s).has("cell2.open")); i++) {
			this.context.waitTick();
			int stage = this.on(s -> StoryTesting.cellFieldStage(this.session(s)));

			if (stage == 2) {
				if (!working) {
					working = true;
					atPanel = this.on(s -> {
						DroidEntity droid = Droids.droid(this.me(s), DroidType.MOUSE);
						return droid == null ? -1.0 : droid.position().distanceTo(StoryTesting.cellFieldPanel().add(Vec3.atLowerCornerOf(this.session(s).destroyer)));
					});
				}

				workTicks++;

				if (workTicks == 30) {
					this.context.takeScreenshot("phase26_cell_m31_at_work");
				}
			}
		}

		int work = this.on(s -> StoryTesting.cellFieldWork());
		SaberCraft.LOGGER.info("PHASE26 CELL: M-31 at work {} ticks, {} blocks from the emitter's panel", workTicks, String.format(Locale.ROOT, "%.2f", atPanel));
		check(working, "M-31 is never seen at work on the field");
		check(atPanel >= 0.0 && atPanel < 1.7, "M-31 does not work at the field's emitter (" + atPanel + " blocks away)");
		// (Counted from the client's side, a few ticks can slip.)
		check(workTicks >= work * 3 / 4, "the field dies too soon after M-31 sets to work (" + workTicks + " of " + work + " ticks)");
		check(this.on(s -> this.session(s).has("cell2.open")), "the field never goes off");
		check(this.on(s -> StoryTesting.cellField())[1] == before[1] + 1, "the field's end is not counted");
		check(this.on(s -> this.level(s).getBlockState(this.world(s, front)).isAir()), "cell 2's force field is still there");
		check(this.context.computeOnClient(client -> StoryClient.effectsSeenForTest()) > effects, "the client does not see the field stutter");
		this.context.waitTicks(5);
		this.context.takeScreenshot("phase26_cell_open");
	}

	// --- 5: Kestrel at the crawl way, her poses -------------------------------------------------------------------

	private void kestrel() {
		this.enter(4, "m5.back");
		this.unseen();
		this.context.waitTicks(30);
		Vec3 at = this.on(s -> this.session(s).figure("kestrel") == null ? null : this.session(s).figure("kestrel").position());

		if (at == null) {
			this.problem("Kestrel is not at the crawl way (m5.back)");
		} else {
			check(at.distanceTo(StoryTesting.kestrelAtVent()) < 0.5, "Kestrel is not where she waits beside the crawl way (" + at + ")");
			BlockPos hatch = DestroyerInterior.DETENTION_HATCH;
			AABB way = new AABB(hatch.getX(), H, hatch.getZ(), hatch.getX() + 2.6, H + 1.0, hatch.getZ() + 1.0);
			AABB her = new AABB(at.x - 0.3, at.y, at.z - 0.3, at.x + 0.3, at.y + 1.8, at.z + 0.3);
			check(!her.intersects(way), "Kestrel stands in the way into the crawl way (" + at + ")");
			check(this.on(s -> this.level(s).noCollision(her.move(Vec3.atLowerCornerOf(this.session(s).destroyer)).deflate(0.05))), "Kestrel stands in a wall (" + at + ")");
		}

		this.put(new Vec3(28.6, H, 5.6), 110.0F, 8.0F);
		this.context.waitTicks(4);
		this.context.takeScreenshot("phase26_kestrel_at_vent");

		// In the hideout: on her seat she sits (also talking); spoken to off it she stands and talks.
		this.enter(3, "m4.brief");
		this.unseen();

		for (int i = 0; i < 30 && !this.on(s -> this.session(s).figure("kestrel") != null && this.session(s).figure("kestrel").atPlace()); i++) {
			this.context.waitTicks(5);
		}

		String poses = this.on(s -> {
			Figure k = this.session(s).figure("kestrel");

			if (k == null) {
				return "none";
			}

			double x = k.x;
			double z = k.z;
			int mode = k.mode;
			float speed = k.speed;
			String sitting = k.pose();
			k.mode = Figure.HELD;
			k.talking = true;
			String sittingTalking = k.pose();
			k.x = 17.2;
			k.z = -3.2;
			k.speed = 0.0F;
			String away = k.pose();
			k.talking = false;
			String awayQuiet = k.pose();
			k.x = x;
			k.z = z;
			k.mode = mode;
			k.speed = speed;
			return sitting + "/" + sittingTalking + "/" + away + "/" + awayQuiet;
		});
		SaberCraft.LOGGER.info("PHASE26 KESTREL poses (seat, seat talking, off it talking, off it quiet): {}", poses);
		check((Crew.SIT + "/" + Crew.SIT + "/" + Crew.TALK + "/" + Crew.STAND).equals(poses), "Kestrel's poses are wrong: " + poses);
		// Her terminal: the keys are heard.
		int mechanical = this.on(s -> Chapter1.mechanicalForTest());
		this.put(new Vec3(18.0, H, 0.3), 180.0F, 30.0F);
		this.server.runOnServer(s -> Story.use(this.session(s), this.me(s), "terminal"));
		check(this.on(s -> Chapter1.mechanicalForTest()) == mechanical + 1, "using Kestrel's terminal makes no console sound");
		this.close();
	}

	// --- 6: the lab's guard ----------------------------------------------------------------------------------

	private void labGuard() {
		this.enter(5, "m6.lab");
		this.unseen();
		this.context.waitTicks(10);
		Vec3 post = this.on(s -> this.session(s).figure("lab_guard") == null ? null : this.session(s).figure("lab_guard").position());

		if (post == null) {
			this.problem("no guard at the lab (m6.lab)");
			return;
		}

		check(post.distanceTo(StoryTesting.labGuardPost()) < 0.8, "the lab's guard is not at his post (" + post + ")");
		float yaw0 = this.on(s -> this.session(s).figure("lab_guard").yaw);
		boolean turned = false;

		for (int i = 0; i < 30 && !turned; i++) {
			this.context.waitTicks(10);
			turned = this.on(s -> this.session(s).figure("lab_guard") != null && Math.abs(Mth.wrapDegrees(this.session(s).figure("lab_guard").yaw - yaw0)) > 20.0F);
		}

		check(turned, "the lab's guard does not keep watch (never turns)");
		// The step goes on (into the lab): he stays.
		this.server.runOnServer(s -> StoryTesting.finishStep(this.session(s)));
		this.waitStep("m6.saber", 40);
		this.context.waitTicks(20);
		check(this.on(s -> this.session(s).figure("lab_guard") != null), "the lab's guard vanishes as the step goes on");
		// From the front: he sees it coming.
		this.server.runOnServer(s -> {
			StorySession session = this.session(s);
			Figure guard = session.figure("lab_guard");
			this.teleport(s, session, guard.position().add(guard.look().scale(1.4)), guard.yaw + 180.0F);
			StoryTesting.takedown(session);
		});
		check(this.on(s -> this.session(s).figure("lab_guard").mode != Figure.DOWN), "the lab's guard is overpowered from the front");
		check(this.on(s -> this.session(s).figure("lab_guard").stage >= Figure.DETECTED), "the lab's guard does not see the player coming from the front");

		// Again from behind (a fresh start).
		this.enter(5, "m6.saber");
		this.unseen();
		this.context.waitTicks(20);
		int takedowns = this.on(s -> StoryTesting.takedowns());
		check(this.on(s -> this.session(s).figure("lab_guard") != null && StoryTesting.takedownOpen(this.session(s), this.session(s).figure("lab_guard"))),
				"the lab's guard cannot be overpowered (m6.saber)");
		this.server.runOnServer(s -> {
			StorySession session = this.session(s);
			Figure guard = session.figure("lab_guard");

			if (guard != null) {
				this.teleport(s, session, guard.position().subtract(guard.look().scale(1.0)), guard.yaw);
				// (Phase 31: crouching, as one has to.)
				StoryTesting.takedownCrouched(session);
			}
		});
		check(this.on(s -> StoryTesting.takedowns()) == takedowns + 1, "the lab's guard cannot be overpowered from behind");
		check("takedown".equals(this.film(10)), "the takedown's scene does not play at the lab");
		this.context.waitTicks(20);
		this.context.takeScreenshot("phase26_lab_takedown");

		for (int i = 0; i < 60 && this.on(s -> this.session(s).has("tmp.takedown")); i++) {
			this.context.waitTicks(2);
		}

		check(this.on(s -> this.session(s).figure("lab_guard") != null && this.session(s).figure("lab_guard").mode == Figure.DOWN), "the lab's guard is not down after the scene");
		check(this.on(s -> this.session(s).has(StoryTesting.labGuardDown())), "the lab's guard being down is not remembered");
		this.context.waitTicks(100);
		check(this.on(s -> this.session(s).figure("lab_guard") != null && this.session(s).figure("lab_guard").mode == Figure.DOWN),
				"the lab's guard is gone or up again a while after the takedown");
		this.context.takeScreenshot("phase26_lab_guard_down");
		// The saber taken, the escape: he still lies there (not up again as a fighter).
		this.server.runOnServer(s -> StoryTesting.finishStep(this.session(s)));
		this.waitStep("m6.escape1", 60);
		this.skipFilms(60);
		check(this.on(s -> this.session(s).figure("lab_guard") != null && this.session(s).figure("lab_guard").mode == Figure.DOWN),
				"the overpowered lab guard is up again in the escape");
	}

	// --- 7: droids keep off pedestals and consoles --------------------------------------------------------------

	private void droids() {
		this.enter(5, "m6.saber");
		this.unseen();
		this.flags("droid.m31", "droid.r2v7", "lab.open");
		this.server.runOnServer(s -> Story.updateDroids(this.session(s)));
		this.context.waitTicks(30);
		BlockPos holder = DestroyerInterior.SABER_FIELD;
		check(this.on(s -> DroidEntity.keptClear(this.level(s), this.world(s, holder).getX() + 0.5, this.world(s, holder).getY() + 1.05, this.world(s, holder).getZ() + 0.5,
				0.2)), "the saber's holder is not kept clear of droids");
		// (The open floor east of the holder; in front of it stands the tracker, kept clear too.)
		check(!this.on(s -> DroidEntity.keptClear(this.level(s), this.world(s, holder).getX() + 5.5, this.world(s, holder).getY() + 0.05, this.world(s, holder).getZ() + 0.5,
				0.2)), "the lab's open floor beside the holder is kept clear of droids");
		this.put(new Vec3(48.5, R, 50.5), 90.0F, 20.0F);
		int left = this.on(s -> DroidEntity.propsLeftForTest());

		for (DroidType type : List.of(DroidType.MOUSE, DroidType.R2D2)) {
			// Put right onto the holder (as one walked onto it), told to stay there.
			this.server.runOnServer(s -> Droids.place(this.me(s), type, Vec3.atBottomCenterOf(this.world(s, holder)).add(0.0, 1.0, 0.0), 0.0F, true));
		}

		this.context.waitTicks(100);
		List<String> on = this.on(s -> {
			List<String> list = new ArrayList<>();

			for (DroidType type : List.of(DroidType.MOUSE, DroidType.R2D2)) {
				DroidEntity droid = Droids.droid(this.me(s), type);

				if (droid != null && DroidEntity.keptClear(this.level(s), droid.getX(), droid.getY() + 0.05, droid.getZ(), droid.getBbWidth() / 2.0)) {
					list.add(type + " at " + droid.position().subtract(Vec3.atLowerCornerOf(this.session(s).destroyer)));
				}
			}

			return list;
		});
		SaberCraft.LOGGER.info("PHASE26 DROIDS: put off a prop {} times; still on one: {}", this.on(s -> DroidEntity.propsLeftForTest()) - left, on);
		check(on.isEmpty(), "droids stay on the saber's holder: " + on);
		this.context.takeScreenshot("phase26_droids_off_holder");
	}

	// --- 11: the lift to the bridge in the finale -----------------------------------------------------------------

	private void lift() {
		int x = DestroyerInterior.CX;
		this.enter(5, "m6.plan");
		this.unseen();
		check(this.on(s -> StoryTesting.bridgeClosed(this.session(s))), "the bridge is not sealed off in the finale");
		int refused = this.on(s -> Story.liftsRefusedForTest());
		this.put(new Vec3(x + 0.5, H, DestroyerInterior.LIFT_Z0 - 3.0), 0.0F, 0.0F);
		this.put(new Vec3(x + 0.5, H, DestroyerInterior.LIFT_Z0 + 1.5), 0.0F, 0.0F);
		this.context.waitTicks(30);
		double y = this.on(s -> this.session(s).local(Story.DESTROYER).y);
		check(y < DestroyerInterior.DECK - 4, "the lift takes the player up to the bridge in the finale (y " + y + ")");
		check(this.on(s -> Story.liftsRefusedForTest()) == refused + 1, "the lift to the bridge is not refused once in the finale");
		this.context.takeScreenshot("phase26_lift_locked");
		// Phase 27: an earlier mission: sealed off as well (the whole story keeps off the bridge),
		// behind the barrier before the lift's bay (Phase27GameTest walks into it).
		this.enter(3, "m4.brief");
		this.unseen();
		check(this.on(s -> StoryTesting.bridgeClosed(this.session(s))), "the bridge is open in mission 4 (Phase 27: sealed through the whole story)");
		int before = this.on(s -> Story.liftsRefusedForTest());
		this.put(new Vec3(x + 0.5, H, DestroyerInterior.LIFT_Z0 - 3.0), 0.0F, 0.0F);
		this.put(new Vec3(x + 0.5, H, DestroyerInterior.LIFT_Z0 + 1.5), 0.0F, 0.0F);
		this.context.waitTicks(30);
		double up = this.on(s -> this.session(s).local(Story.DESTROYER).y);
		check(up < DestroyerInterior.DECK - 4, "the lift takes the player up to the bridge in mission 4 (y " + up + ")");
		check(this.on(s -> Story.liftsRefusedForTest()) == before + 1, "the lift to the bridge is not refused once in mission 4");
	}

	// --- 8: the finale's troopers shoot more often, never two bolts at once ------------------------------------------

	private void shots() {
		this.enter(5, "m6.escape1");
		this.server.runCommand("effect give @a minecraft:resistance 100000 4 true");
		this.skipFilms(40);
		this.put(new Vec3(48.0, R, 24.5), 0.0F, 0.0F);
		this.server.runOnServer(s -> {
			StorySession session = this.session(s);

			for (Figure figure : session.figures) {
				if (figure.hostile && !figure.saber && figure.mode != Figure.DOWN) {
					StoryTesting.detect(session, figure);
				}
			}
		});
		int[] timing = StoryTesting.shotTiming();
		int last = this.on(s -> StoryTesting.fightShots());
		int first = last;
		List<Integer> at = new ArrayList<>();
		int together = 0;

		for (int i = 0; i < 320; i++) {
			this.context.waitTick();
			int now = this.on(s -> StoryTesting.fightShots());

			if (now > last) {
				together += now - last - 1;

				for (int k = last; k < now; k++) {
					at.add(i);
				}

				last = now;
			}
		}

		int least = Integer.MAX_VALUE;

		for (int k = 1; k < at.size(); k++) {
			least = Math.min(least, at.get(k) - at.get(k - 1));
		}

		SaberCraft.LOGGER.info("PHASE26 SHOTS: {} in 16 s at ticks {} (least gap {}; between shots {}..{}, a second shot after {}, between troopers {})", last - first, at,
				least, timing[0], timing[0] + timing[1], timing[2], timing[3]);
		check(last - first >= 3, "the finale's troopers hardly shoot (" + (last - first) + " bolts in 16 seconds)");
		check(together == 0, "two bolts fired in the same moment (" + together + ")");
		check(at.size() < 2 || least >= timing[3] - 1, "two bolts closer together than the fair gap (" + least + " ticks)");
		// (Phase 30: a little more often again, 18 + up to 17: the fair minimum is 16 now.)
		check(timing[0] >= 16 && timing[0] + timing[1] <= 50, "the shot cooldown is not between the old one and a fair minimum: " + timing[0] + ".." + (timing[0] + timing[1]));
	}

	// --- 11: the Falcon under the hangar's field -------------------------------------------------------------------

	private void falcon() {
		this.enter(4, "m5.escape");
		this.unseen();
		this.context.waitTicks(10);
		check(!this.context.computeOnClient(client -> StoryClient.has(HangarFalcon.WAITING)), "the Falcon waits under the hangar before the finale");
		int before = this.context.computeOnClient(client -> HangarFalcon.drawnForTest());
		this.put(new Vec3(40.5, H, -33.5), 180.0F, 55.0F);
		this.context.waitTicks(10);
		check(this.context.computeOnClient(client -> HangarFalcon.drawnForTest()) == before, "the Falcon is drawn under the hangar before the finale");

		// Phase 27: not in the escape any more (her TIE flew through it, and it came too early): only
		// once she has flown off and her film is over (see Phase27GameTest for the duel's end).
		this.enter(5, "m6.escape1");
		this.unseen();
		this.server.runCommand("effect give @a minecraft:resistance 100000 4 true");
		this.skipFilms(40);
		check(!this.on(s -> StoryTesting.falconWaits(this.session(s))), "the Falcon waits in the escape already (Phase 27: only after the boss's flight)");
		this.enter(5, "m6.falcon");
		this.unseen();
		this.skipFilms(40);
		check(this.on(s -> StoryTesting.falconWaits(this.session(s))), "the Falcon does not wait after the duel");

		for (int i = 0; i < 20 && !this.context.computeOnClient(client -> StoryClient.has(HangarFalcon.WAITING)); i++) {
			this.context.waitTick();
		}

		int drawn = this.context.computeOnClient(client -> HangarFalcon.drawnForTest());
		this.context.waitTicks(10);
		check(this.context.computeOnClient(client -> HangarFalcon.drawnForTest()) > drawn, "the Falcon is not drawn before one comes to the hangar (it would pop up)");
		this.put(new Vec3(40.5, H, -33.5), 180.0F, 55.0F);
		// Phase 27: it rises from below first (a few seconds), then hovers in its place.
		this.context.waitTicks(100);
		this.context.takeScreenshot("phase26_falcon_under_field");
		this.put(new Vec3(36.0, H, -32.0), 200.0F, 35.0F);
		this.context.waitTicks(6);
		this.context.takeScreenshot("phase26_falcon_under_field_side");
	}

	// --- 9, 13: the boss: strength, lunge, the ring's rush, the fierce end; the music before the duel -----------------

	private void boss() {
		check(StoryTesting.hit() < 0.06F, "she is not stronger: a light hit takes " + StoryTesting.hit() + " of her strength");
		this.enter(5, "m6.duel");
		this.flags("saber.taken");
		this.server.runCommand("effect give @a minecraft:resistance 100000 4 true");
		this.server.runCommand("item replace entity @a weapon.mainhand with " + SABER);
		// Before the duel: the tense bed under her words (no silence).
		this.music("music.story_tension", "before the duel");
		float[] changes = this.context.computeOnClient(client -> StoryMusic.changesForTest());
		this.put(new Vec3(40.5, H, -21.0), 180.0F, 0.0F);
		// The change to the duel's music crossfades (the old piece fades, the new one fades in).
		boolean fading = false;

		for (int i = 0; i < 200 && this.context.computeOnClient(client -> StoryMusic.changesForTest())[0] == changes[0]; i++) {
			this.context.waitTick();
		}

		float[] after = this.context.computeOnClient(client -> StoryMusic.changesForTest());

		if (after[0] > changes[0]) {
			fading = this.context.computeOnClient(client -> StoryMusic.fadingForTest()) > 0;
			check(after[1] >= 30.0F, "the music changes to the duel too fast (" + after[1] + " ticks)");
			check(fading, "the music before the duel stops at once instead of fading out");
		} else {
			this.problem("the music does not change when the duel begins (" + this.context.computeOnClient(client -> StoryMusic.playing()) + ")");
		}

		SaberCraft.LOGGER.info("PHASE26 MUSIC: before the duel {} -> {}, fade {} ticks, old piece fading {}", "music.story_tension",
				this.context.computeOnClient(client -> StoryMusic.playing()), after[1], fading);
		this.skipFilms(300);

		if (!this.on(s -> this.session(s).has("tmp.duel"))) {
			this.problem("the duel does not begin after its film (skipped)");
			return;
		}

		this.converse();

		// The lunge: a few steps before her, she comes in fast with a thrust.
		int[] rushes = this.on(s -> StoryTesting.rushes());
		// (Ready to go in, as when a combo's pause is over and the player keeps away.)
		this.server.runOnServer(s -> {
			this.before(s, 4.6);
			StoryTesting.guard(this.session(s).figure("shadow"), 0);
		});
		boolean lunged = false;

		for (int i = 0; i < 80 && !lunged; i++) {
			this.context.waitTick();
			lunged = this.on(s -> StoryTesting.rushes())[3] > rushes[3];
		}

		check(lunged, "she does not lunge from a few steps away in the first phase");
		// Half her strength: the scene, skipped; the second phase.
		this.server.runOnServer(s -> StoryTesting.halfStrength(this.session(s), this.session(s).figure("shadow"), this.me(s)));
		this.film(20);
		this.context.waitTicks(10);
		this.skip("boss_mid");
		check(this.on(s -> StoryTesting.phase(this.session(s).figure("shadow")) == 2), "the second phase does not begin");
		this.context.waitTicks(30);
		// The ring's rush: it spins up, she drives at the player behind it, then stands open.
		this.server.runOnServer(s -> {
			this.before(s, 6.0);
			StoryTesting.rushNow(this.session(s), this.session(s).figure("shadow"));
		});
		check(this.on(s -> StoryTesting.rushes())[0] == rushes[0] + 1, "she does not rush with the ring");
		this.context.waitTicks(14);
		this.facing();
		this.context.takeScreenshot("phase26_ring_rush_windup");
		boolean over = false;

		for (int i = 0; i < 80 && !over; i++) {
			this.context.waitTick();
			over = !this.on(s -> StoryTesting.rushing(this.session(s).figure("shadow")));

			if (i == 12) {
				this.facing();
				this.context.takeScreenshot("phase26_ring_rush");
			}
		}

		check(over, "her rush with the ring does not end");
		int[] done = this.on(s -> StoryTesting.rushes());
		SaberCraft.LOGGER.info("PHASE26 RUSH: begun {}, blocked {}, hits {}, lunges {}", done[0], done[1], done[2], done[3]);
		// Her last strength: fiercer, the ring flares.
		int flares = this.context.computeOnClient(client -> StoryClient.ringFlaresForTest());
		// (A stroke from behind takes her below the last quarter, as in the fight.)
		this.server.runOnServer(s -> StoryTesting.lastStrength(this.session(s), this.session(s).figure("shadow"), this.me(s)));
		boolean fierce = false;

		for (int i = 0; i < 40 && !fierce; i++) {
			this.context.waitTick();
			fierce = this.on(s -> this.session(s).has("tmp.boss_fierce"));
		}

		check(fierce, "she does not turn fierce at her last strength");
		this.context.waitTicks(3);
		check(this.context.computeOnClient(client -> StoryClient.ringFlaresForTest()) > flares, "the client does not see her ring flare as she turns fierce");
		this.facing();
		this.context.takeScreenshot("phase26_boss_fierce");
	}

	/** Puts the player that far in front of her, facing her. */
	private void before(MinecraftServer s, double distance) {
		StorySession session = this.session(s);
		Figure f = session.figure("shadow");
		Vec3 her = f.position();
		Vec3 me = session.local(Story.DESTROYER);
		Vec3 away = new Vec3(me.x - her.x, 0.0, me.z - her.z);
		away = away.lengthSqr() < 0.01 ? new Vec3(0.0, 0.0, 1.0) : away.normalize();
		Vec3 to = her.add(away.scale(distance));
		this.teleport(s, session, to, (float) Math.toDegrees(Math.atan2(away.x, -away.z)));
	}

	// --- 10: the blade lock's scene, whatever the player does at half her strength ----------------------------------------

	private void halfScene(String how) {
		this.enter(5, "m6.duel");
		this.flags("saber.taken");
		this.server.runCommand("effect give @a minecraft:resistance 100000 4 true");
		this.server.runCommand("item replace entity @a weapon.mainhand with " + SABER);
		this.put(new Vec3(40.5, H, -21.0), 180.0F, 0.0F);
		this.skipFilms(300);

		if (!this.on(s -> this.session(s).has("tmp.duel"))) {
			this.problem("the duel does not begin after its film (skipped, " + how + ")");
			return;
		}

		// Her last words (the duel has begun with them): read and closed, as a player does.
		this.converse();

		this.context.waitTicks(10);
		boolean guarding = false;

		switch (how) {
			case "run" -> {
				// Running past her: from her side, along her.
				this.server.runOnServer(s -> {
					StorySession session = this.session(s);
					Figure f = session.figure("shadow");
					Vec3 side = new Vec3(f.x - 2.0, f.y, f.z + 3.0);
					this.teleport(s, session, side, 180.0F);
				});
				this.context.getInput().holdKey(options -> options.keySprint);
				this.context.getInput().holdKey(options -> options.keyUp);
				this.context.waitTicks(6);
			}
			case "strike" -> {
				this.server.runOnServer(s -> this.before(s, 2.2));
				this.context.waitTicks(2);
				this.context.getInput().pressKey(options -> options.keyAttack);
			}
			default -> {
				this.server.runOnServer(s -> this.before(s, 3.0));
				this.context.waitTicks(20);
				this.context.getInput().holdKey(SaberAbilityClient.GUARD_KEY);

				for (int i = 0; i < 30 && !guarding; i++) {
					this.context.waitTick();
					guarding = this.on(s -> SaberAbilities.isGuarding(this.me(s)));
				}

				if (!guarding) {
					String state = this.context.computeOnClient(client -> {
						SaberHandState hand = SaberTracker.INSTANCE.state(client.player, InteractionHand.MAIN_HAND);
						return "saber " + (hand == null ? "none" : hand.isActive() + " " + hand.extension(1.0F)) + ", screen " + client.gui.screen()
								+ ", client guarding " + SaberAbilityClient.isGuarding() + ", stamina " + SaberAbilityClient.stamina();
					});
					SaberCraft.LOGGER.error("PHASE26 guard: {}", state);
				}

				check(guarding, "the test cannot raise the guard before the scene (" + how + ")");
			}
		}

		int[] holds = this.on(s -> StoryTesting.sceneHolds());
		this.server.runOnServer(s -> StoryTesting.halfStrength(this.session(s), this.session(s).figure("shadow"), this.me(s)));
		this.context.waitTicks(3);
		check(this.on(s -> StoryTesting.sceneHolds())[0] == holds[0] + 1, "the scene does not set the two up (" + how + ")");
		check("boss_mid".equals(this.film(20)), "the blade lock's scene does not begin (" + how + ")");
		double apart = this.on(s -> {
			Figure f = this.session(s).figure("shadow");
			Vec3 me = this.session(s).local(Story.DESTROYER);
			return Math.hypot(me.x - f.x, me.z - f.z);
		});
		double look = this.lookAtHer();
		double hers = this.on(s -> {
			Figure f = this.session(s).figure("shadow");
			Vec3 me = this.session(s).local(Story.DESTROYER);
			Vec3 to = new Vec3(me.x - f.x, 0.0, me.z - f.z).normalize();
			Vec3 her = f.look();
			return Math.toDegrees(Math.acos(Mth.clamp(to.dot(new Vec3(her.x, 0.0, her.z).normalize()), -1.0, 1.0)));
		});
		check(apart > StoryTesting.lockDistance() - 0.3 && apart < StoryTesting.lockDistance() + 0.4, "the scene does not set the two at the lock's distance (" + how + ": "
				+ String.format(Locale.ROOT, "%.2f", apart) + ")");
		check(look < 25.0, "the player does not face her as the scene begins (" + how + ": " + Math.round(look) + "°)");
		check(hers < 25.0, "she does not face the player as the scene begins (" + how + ": " + Math.round(hers) + "°)");
		check(!this.on(s -> SaberAbilities.isGuarding(this.me(s)) || this.me(s).isUsingItem()), "the guard stays up in the scene (" + how + ")");
		// Held in place while the scene runs (the keys still down).
		Vec3 p0 = this.on(s -> this.me(s).position());
		this.context.waitTicks(20);
		Vec3 p1 = this.on(s -> this.me(s).position());
		check(p0.distanceTo(p1) < 0.3, "the player moves in the scene (" + how + ": " + String.format(Locale.ROOT, "%.2f", p0.distanceTo(p1)) + " blocks)");
		this.context.takeScreenshot("phase26_half_" + how);
		this.context.getInput().releaseKey(options -> options.keyUp);
		this.context.getInput().releaseKey(options -> options.keySprint);
		this.context.getInput().releaseKey(SaberAbilityClient.GUARD_KEY);
		SaberCraft.LOGGER.info("PHASE26 HALF ({}): apart {}, player looks {}°, she looks {}°, guard before {}, held {} times", how, String.format(Locale.ROOT, "%.2f", apart),
				Math.round(look), Math.round(hers), guarding, this.on(s -> StoryTesting.sceneHolds())[1] - holds[1]);
		this.skip("boss_mid");
		// (As the camera comes back: right after it she fights on and moves round the player.)
		this.context.waitTicks(2);
		double afterwards = this.lookAtHer();
		this.context.waitTicks(8);
		check(this.on(s -> StoryTesting.phase(this.session(s).figure("shadow")) == 2), "the second phase does not begin after the scene (" + how + ")");
		check(afterwards < 35.0, "the player does not face her after the scene (" + how + ": " + Math.round(afterwards) + "°)");
		SaberCraft.LOGGER.info("PHASE26 HALF ({}) after the scene: the player looks {}° off her", how, Math.round(afterwards));
	}

	/** How far (degrees) the player's view on the client is turned away from her. */
	private double lookAtHer() {
		Vec3 her = this.on(s -> this.session(s).figure("shadow").position().add(Vec3.atLowerCornerOf(this.session(s).destroyer)));
		return this.context.computeOnClient(client -> {
			Vec3 to = new Vec3(her.x - client.player.getX(), 0.0, her.z - client.player.getZ());
			Vec3 look = client.player.getLookAngle();
			look = new Vec3(look.x, 0.0, look.z);
			return to.lengthSqr() < 1.0E-4 || look.lengthSqr() < 1.0E-4 ? 0.0 : Math.toDegrees(Math.acos(Mth.clamp(to.normalize().dot(look.normalize()), -1.0, 1.0)));
		});
	}

	/** Turns the player to her (for the pictures). */
	private void facing() {
		this.server.runOnServer(s -> {
			StorySession session = this.session(s);
			Figure f = session.figure("shadow");
			Vec3 me = session.local(Story.DESTROYER);
			float yaw = (float) Math.toDegrees(Math.atan2(-(f.x - me.x), f.z - me.z));
			this.teleport(s, session, me, yaw);
		});
		this.context.waitTicks(2);
	}

	// --- 12: Kestrel after the finale --------------------------------------------------------------------------------

	private void kestrelArmour() {
		this.enter(5, "m6.falcon");
		this.unseen();
		this.flags("opt.armour2");
		this.context.waitTicks(10);
		BlockPos destroyer = this.on(s -> this.session(s).destroyer);
		BlockPos falcon = this.on(s -> this.session(s).falcon);
		String aboardDestroyer = this.context.computeOnClient(client -> MeshAudit.kestrelLook(destroyer));
		String aboardFalcon = this.context.computeOnClient(client -> MeshAudit.kestrelLook(falcon));
		check(Crew.TROOPER.equals(aboardDestroyer), "Kestrel is not in the second armour aboard the Star Destroyer in the finale (" + aboardDestroyer + ")");
		check(!Crew.TROOPER.equals(aboardFalcon), "Kestrel is still in the trooper's armour aboard the Falcon after the finale (" + aboardFalcon + ")");
		this.flags(Chapter1.CHAPTER_DONE);
		this.context.waitTicks(10);
		String done = this.context.computeOnClient(client -> MeshAudit.kestrelLook(destroyer));
		check(!Crew.TROOPER.equals(done), "Kestrel is still in the trooper's armour after the chapter (" + done + ")");
	}

	// --- 15: in the story: no random character, no random saber -------------------------------------------------------

	private void restrictions() {
		this.enter(1, "m2.return");
		this.unseen();
		this.server.runCommand("item replace entity @a weapon.mainhand with " + SABER);
		this.context.waitTicks(10);
		check(this.context.computeOnClient(client -> SaberMenuScreen.lockedInStory(SaberMenuScreen.Tab.CHARACTER) && SaberMenuScreen.lockedInStory(SaberMenuScreen.Tab.SABER)),
				"the character and saber areas are open in the story");
		check(this.context.computeOnClient(client -> !SaberMenuScreen.lockedInStory(SaberMenuScreen.Tab.MUSIC)), "the music area is locked in the story");
		String outfit = this.context.computeOnClient(client -> CharacterConfig.get().outfit);
		ItemStack held = this.on(s -> this.me(s).getMainHandItem().copy());
		this.context.setScreen(CharacterMenuScreen::new);
		this.context.waitTicks(2);
		String picked = this.context.computeOnClient(client -> client.gui.screen() instanceof CharacterMenuScreen menu && menu.randomPick(RandomSource.create(1)) != null ? "picked" : "");
		this.context.setScreen(() -> new SaberCustomizeScreen(InteractionHand.MAIN_HAND, Minecraft.getInstance().player.getMainHandItem()));
		this.context.waitTicks(2);
		String saber = this.context.computeOnClient(client -> client.gui.screen() instanceof SaberCustomizeScreen menu && menu.randomSaber(RandomSource.create(2)) != null ? "picked" : "");
		this.context.waitTicks(5);
		this.context.setScreen(() -> null);
		check(picked.isEmpty(), "the random character works in the story");
		check(saber.isEmpty(), "the random saber works in the story");
		check(outfit.equals(this.context.computeOnClient(client -> CharacterConfig.get().outfit)), "the character changed in the story");
		ItemStack now = this.on(s -> this.me(s).getMainHandItem().copy());
		check(String.valueOf(SaberVariant.hiltOf(held)).equals(String.valueOf(SaberVariant.hiltOf(now)))
				&& String.valueOf(SaberVariant.colorOf(held)).equals(String.valueOf(SaberVariant.colorOf(now))), "the saber changed in the story");
	}

	// --- Helpers -------------------------------------------------------------------------------------------------

	/** Continues the story at that step (a file saved there), as soon as it is there. */
	private void enter(int mission, String step) {
		if (this.on(s -> this.session(s) != null)) {
			this.leave();
		}

		this.server.runOnServer(s -> StoryTesting.fileAt(s, this.self, mission, step));
		this.context.runOnClient(client -> StoryClient.send(StoryActionPayload.CONTINUE, "", 0));

		for (int i = 0; i < 160; i++) {
			this.context.waitTick();

			if (step.equals(this.on(s -> this.session(s) == null ? "" : StoryTesting.step(this.session(s))))) {
				this.context.waitTicks(2);
				return;
			}
		}

		throw new AssertionError("the story does not continue at " + step);
	}

	private void leave() {
		this.context.runOnClient(client -> StoryClient.send(StoryActionPayload.LEAVE, "", 0));
		this.context.waitTicks(30);
		check(this.on(s -> !Story.playing(this.me(s))), "still in the story after leaving");
	}

	private void waitStep(String step, int ticks) {
		for (int i = 0; i < ticks; i++) {
			if (step.equals(this.on(s -> this.session(s) == null ? "" : StoryTesting.step(this.session(s))))) {
				return;
			}

			this.context.waitTick();
		}

		this.problem("the story is not at " + step + " (at " + this.on(s -> this.session(s) == null ? "-" : StoryTesting.step(this.session(s))) + ")");
	}

	/** Answers every line of the open dialog (the first answer) until it ends. */
	private void converse() {
		for (int i = 0; i < 80; i++) {
			if (this.on(s -> this.session(s) == null || StoryTesting.line(this.session(s)).isEmpty())) {
				return;
			}

			int answers = this.on(s -> StoryTesting.answers(this.session(s)).size());
			this.context.runOnClient(client -> StoryClient.send(StoryActionPayload.ANSWER, "", answers > 0 ? 0 : -1));
			this.context.waitTicks(3);
		}

		this.problem("a dialog does not end: " + this.on(s -> StoryTesting.line(this.session(s))));
	}

	/** Closes a menu-like dialog (a terminal): its last answer each time, until it ends. */
	private void close() {
		for (int i = 0; i < 20; i++) {
			if (this.on(s -> this.session(s) == null || StoryTesting.line(this.session(s)).isEmpty())) {
				return;
			}

			int answers = this.on(s -> StoryTesting.answers(this.session(s)).size());
			this.context.runOnClient(client -> StoryClient.send(StoryActionPayload.ANSWER, "", answers > 0 ? answers - 1 : -1));
			this.context.waitTicks(3);
		}

		this.problem("a menu does not close: " + this.on(s -> StoryTesting.line(this.session(s))));
	}

	/** Nobody sees the player (the test goes where it likes). */
	private void unseen() {
		this.server.runOnServer(s -> StoryTesting.unseen(this.session(s), 100000));
	}

	private void flags(String... flags) {
		this.server.runOnServer(s -> {
			for (String flag : flags) {
				this.session(s).set(flag);
			}
		});
	}

	/** The music the client plays (waiting a few seconds for that piece). */
	private void music(String piece, String where) {
		String playing = null;

		for (int i = 0; i < 16; i++) {
			playing = this.context.computeOnClient(client -> StoryMusic.playing());

			if (piece.equals(playing)) {
				return;
			}

			this.context.waitTicks(10);
		}

		this.problem("the music " + where + " is " + playing + ", not " + piece);
	}

	/** The film the client plays (waiting up to that many ticks for one to begin). */
	private @Nullable String film(int ticks) {
		for (int i = 0; i < ticks; i++) {
			String film = this.context.computeOnClient(client -> StoryCinema.active() ? StoryCinema.filmForTest() : null);

			if (film != null) {
				return film;
			}

			this.context.waitTick();
		}

		return null;
	}

	/** Skips that film as the player does (the key: the server is told, the client stops it). */
	private void skip(String film) {
		this.context.runOnClient(client -> StoryClient.send(StoryActionPayload.SKIP, film, 0));
		this.context.runOnClient(client -> StoryCinema.skipForTest());
		this.context.waitTicks(5);
	}

	/** Skips every film the story plays until the duel begins, at most that many ticks. */
	private void skipFilms(int ticks) {
		for (int i = 0; i < ticks / 5; i++) {
			String film = this.context.computeOnClient(client -> StoryCinema.active() ? StoryCinema.filmForTest() : null);

			if (film != null) {
				this.context.runOnClient(client -> StoryClient.send(StoryActionPayload.SKIP, film, 0));
				this.context.runOnClient(client -> StoryCinema.skipForTest());
			} else if (this.on(s -> this.session(s).has("tmp.duel"))) {
				return;
			} else if (this.on(s -> this.session(s).dialog != null)) {
				this.server.runOnServer(s -> StoryTesting.answer(this.session(s), 0));
			}

			this.context.waitTicks(5);
		}
	}

	/** Puts the player at a place of the Star Destroyer of the story (plot space). */
	private void put(Vec3 local, float yaw, float pitch) {
		BlockPos origin = this.on(s -> this.session(s).destroyer);
		this.server.runCommand(String.format(Locale.ROOT, "execute in sabercraft:ship_interiors run tp @a %.3f %.3f %.3f %.1f %.1f", origin.getX() + local.x,
				origin.getY() + local.y, origin.getZ() + local.z, yaw, pitch));
		this.context.waitTicks(8);
	}

	/** Puts the player there at once, on the server (plot space). */
	private void teleport(MinecraftServer server, StorySession session, Vec3 local, float yaw) {
		ServerPlayer player = this.me(server);
		Vec3 world = local.add(Vec3.atLowerCornerOf(session.destroyer));
		player.teleportTo(server.getLevel(ShipInteriors.DIMENSION), world.x, world.y, world.z, java.util.Set.of(), yaw, 0.0F, true);
	}

	/** Waits (a second at most) until the client's world shows the hatch open or shut. */
	private void clientSees(BlockPos cell, boolean open) {
		BlockPos at = this.on(s -> this.world(s, cell));

		for (int i = 0; i < 20 && this.context.computeOnClient(client -> client.level.getBlockState(at).isAir()) != open; i++) {
			this.context.waitTicks(1);
		}
	}

	private boolean hatchOpen(BlockPos cell) {
		return this.on(s -> this.level(s).getBlockState(this.world(s, cell)).isAir());
	}

	private BlockPos world(MinecraftServer s, BlockPos local) {
		return this.session(s).destroyer.offset(local);
	}

	private ServerLevel level(MinecraftServer s) {
		return s.getLevel(ShipInteriors.DIMENSION);
	}

	private void check(boolean ok, String problem) {
		if (!ok) {
			this.problem(problem);
		}
	}

	private void problem(String problem) {
		SaberCraft.LOGGER.error("PHASE26 CHECK FAILED: {}", problem);
		this.problems.add(problem);
	}

	private @Nullable StorySession session(MinecraftServer server) {
		ServerPlayer player = this.me(server);
		return player == null ? null : Story.sessionForTest(player).orElse(null);
	}

	private <T> T on(Function<MinecraftServer, T> what) {
		return this.server.computeOnServer(what::apply);
	}

	private @Nullable ServerPlayer me(MinecraftServer server) {
		return server.getPlayerList().getPlayer(this.self);
	}
}
