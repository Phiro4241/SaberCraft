package com.phiro.sabercraft.gametest;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.ability.AbilityConfig;
import com.phiro.sabercraft.ability.SaberAbilities;
import com.phiro.sabercraft.client.ability.SaberAbilityClient;
import com.phiro.sabercraft.client.interior.MeshAudit;
import com.phiro.sabercraft.client.interior.RingSound;
import com.phiro.sabercraft.client.story.StoryCinema;
import com.phiro.sabercraft.client.story.StoryClient;
import com.phiro.sabercraft.client.story.StoryHud;
import com.phiro.sabercraft.interior.DestroyerInterior;
import com.phiro.sabercraft.interior.InteriorPlan;
import com.phiro.sabercraft.interior.InteriorSystems;
import com.phiro.sabercraft.interior.ShipInteriors;
import com.phiro.sabercraft.network.StoryActionPayload;
import com.phiro.sabercraft.story.Figure;
import com.phiro.sabercraft.story.Story;
import com.phiro.sabercraft.story.StorySession;
import com.phiro.sabercraft.story.StoryTesting;

/**
 * Phase 32: the boss fight made sound again, and the last corrections before Phiro's final test.
 * What a test can play in the real game (the feel stays for the manual list in
 * {@code docs/phase-32-status.md}); the numbers are Phiro's 15 points:
 *
 * <ol>
 * <li>Her ring's whirr: never in her plain blade work, exactly one loop while the ring turns, gone
 * soon after it stops, at a film's start (back after it) and at a capture (1, 2).</li>
 * <li>The stamina bar: never drawn in the duel's films (the duel's start, the scene at half her
 * strength, her defeat), drawn again after them (3).</li>
 * <li>Her spin: hits wherever the player stands in its red circle (near her, far, at her front,
 * side and back), with real loss of health; also jumping, moving and running past her; a stroke
 * into it does not end it; staying in it is hit again, a guard held up in it lets some through;
 * outside the circle nothing (4, 5).</li>
 * <li>Her throw: let go at her right, high over her head or low, from the hand at her right; she is
 * open when she has caught it again (6, 7).</li>
 * <li>Her second phase: plain blade work and the ring's mode take turns, each a while; the client's
 * ring stands still in her blade work (8).</li>
 * <li>Her defence: a player who watches her lands strokes regularly, wears her guard down, and she
 * still attacks; a player hammering at her guard gets her riposte (9, 10).</li>
 * <li>The hangar in the duel: its doors stay shut (walked at, pressed against), its hatch does not
 * open (and an open one is shut), the toolbox and the vent cover are not offered, a player outside
 * is set back in; after her defeat and after leaving all is as ever (11, 12, 13).</li>
 * <li>Texts: none that tells of the game's workings (the maintenance door "by itself", a mouse
 * button), the text keys of both languages alike (14).</li>
 * <li>The chapter's end: shorter, still readable, with the credit "Erstellt von Phiro" coming in
 * after the text (15).</li>
 * </ol>
 */
public class Phase32GameTest implements FabricClientGameTest {
	private static final int H = DestroyerInterior.HANGAR + 1;
	private static final String SABER = "minecraft:netherite_sword[minecraft:custom_model_data={strings:[\"sabercraft:standard\",\"sabercraft:blue\"]}]";

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
			this.problems.forEach(problem -> SaberCraft.LOGGER.error("PHASE32 PROBLEM: {}", problem));
			throw new AssertionError("Phase 32: " + this.problems.size() + " problem(s): " + String.join(" | ", this.problems));
		}
	}

	private void run(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
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
			this.part("end", this::end);
			this.part("hud", this::hud);
			this.part("sound", this::sound);
			this.part("spin", this::spin);
			this.part("throw", this::throwing);
			this.part("defence", this::defence);
			this.part("hangar", this::hangar);
		} catch (AssertionError | RuntimeException e) {
			this.problem("aborted: " + e);
			SaberCraft.LOGGER.error("PHASE32 aborted", e);
		} finally {
			context.getInput().releaseKey(options -> options.keyUp);
			context.getInput().releaseKey(options -> options.keyLeft);
			context.getInput().releaseKey(options -> options.keyJump);
			context.getInput().releaseKey(options -> options.keySprint);
			context.getInput().releaseKey(SaberAbilityClient.GUARD_KEY);
			context.getInput().releaseMouse(0);

			if (this.server != null && this.on(s -> this.session(s) != null)) {
				this.leave();
			}
		}
	}

	/** Runs one part of the test (all of them, or only those named in PHASE32_ONLY, for working on one). */
	private void part(String name, Runnable part) {
		String only = System.getenv("PHASE32_ONLY");

		if (only == null || only.isBlank() || List.of(only.split(",")).contains(name)) {
			SaberCraft.LOGGER.info("PHASE32 part {}", name);
			int before = this.problems.size();
			long start = System.currentTimeMillis();
			part.run();
			SaberCraft.LOGGER.info("PHASE32 part {} done in {} s, {} problem(s)", name, (System.currentTimeMillis() - start) / 1000, this.problems.size() - before);
		}
	}

	// --- 14: no texts about the game's workings ---------------------------------------------------------------

	/** Words that tell the player how something is made to work (the door "opens by itself"), or name a mouse button instead of the key bound. */
	private static final List<String> TECHNICAL = List.of("von selbst", "automatisch", "by itself", "automatically", "rechtsklick", "right-click", "right click");
	/**
	 * Words of the mod's making in any text (options, menus, the story): its phases, how it looked
	 * or worked before. The player is not told of changes to the game (Phiro, Phase 32).
	 */
	private static final java.util.regex.Pattern HISTORY = java.util.regex.Pattern.compile(
			"\\bphase \\d|wie bisher|\\bas before|ersten version|first version|alte aussehen|old look|vorher v\\b|jetzt frei|now free");
	/** Text keys of the story that are about controls by their nature (they name the key bound, or holding a mouse button for the screws). */
	private static final List<String> CONTROLS = List.of("sabercraft.story.cover.", "sabercraft.story.dialog.", "sabercraft.story.film.skip", "sabercraft.story.hint.vent");
	/** Text keys only the code taken out in Phase 32 used (the release's texts at the maintenance door, two dead ones of Phase 30). */
	private static final List<String> REMOVED = List.of("sabercraft.story.hint.service_running", "sabercraft.story.hint.service_click", "sabercraft.story.obj.m5.escape_releasing",
			"sabercraft.story.hint.service_release_paused", "sabercraft.interior.lift_hold");

	private void texts() {
		Map<String, JsonObject> langs = new java.util.TreeMap<>();

		for (String lang : List.of("de_de", "en_us")) {
			String text = this.context.computeOnClient(client -> read(client, "lang/" + lang + ".json"));

			if (text.isEmpty()) {
				this.problem("the " + lang + " texts cannot be read");
				return;
			}

			langs.put(lang, JsonParser.parseString(text).getAsJsonObject());
		}

		JsonObject de = langs.get("de_de");
		JsonObject en = langs.get("en_us");
		Set<String> onlyDe = new java.util.TreeSet<>(de.keySet());
		onlyDe.removeAll(en.keySet());
		Set<String> onlyEn = new java.util.TreeSet<>(en.keySet());
		onlyEn.removeAll(de.keySet());
		check(onlyDe.isEmpty() && onlyEn.isEmpty(), "the two languages' texts differ: only German " + onlyDe + ", only English " + onlyEn);
		List<String> technical = new ArrayList<>();

		for (Map.Entry<String, JsonObject> lang : langs.entrySet()) {
			for (String key : lang.getValue().keySet()) {
				String text = lang.getValue().get(key).getAsString().toLowerCase(Locale.ROOT);

				if (HISTORY.matcher(text).find()) {
					technical.add(lang.getKey() + " " + key + " (the mod's history)");
				}

				boolean story = key.startsWith("sabercraft.story.hint.") || key.startsWith("sabercraft.story.obj.") || key.startsWith("sabercraft.story.point.")
						|| key.startsWith("sabercraft.story.hud.") || key.startsWith("sabercraft.story.radio.") || key.startsWith("sabercraft.hint.")
						|| key.startsWith("sabercraft.interior.");

				if (!story || CONTROLS.stream().anyMatch(key::startsWith)) {
					continue;
				}

				String value = lang.getValue().get(key).getAsString().toLowerCase(Locale.ROOT);

				for (String word : TECHNICAL) {
					if (value.contains(word)) {
						technical.add(lang.getKey() + " " + key + " (" + word + ")");
					}
				}
			}

			for (String key : REMOVED) {
				check(!lang.getValue().has(key), "the text " + key + " is still there (" + lang.getKey() + ")");
			}
		}

		SaberCraft.LOGGER.info("PHASE32 TEXTS: {} keys each, technical wordings {}", de.size(), technical);
		check(technical.isEmpty(), "texts tell of the game's workings: " + technical);
		check(de.has("sabercraft.story.hud.end_credit") && "Erstellt von Phiro".equals(de.get("sabercraft.story.hud.end_credit").getAsString()),
				"the chapter's end has no credit \"Erstellt von Phiro\"");
		check(en.has("sabercraft.story.hud.end_credit"), "the chapter's end has no English credit");
		check(de.has("sabercraft.story.hint.service_release") && !de.get("sabercraft.story.hint.service_release").getAsString().contains("selbst"),
				"the maintenance door's bar still says it unlocks by itself");
	}

	// --- 15: the chapter's end ---------------------------------------------------------------------------------

	private void end() {
		int ticks = StoryHud.endTicksForTest();
		check(ticks >= 240 && ticks < 360, "the chapter's end shows " + ticks + " ticks (it was 360: a little shorter, still to be read)");
		// In the story, aboard the Falcon after the chapter (no film), its end as the server sends it.
		this.enter(5, "m6.jump");
		this.unseen();
		this.converse();
		this.context.waitTicks(10);
		this.server.runOnServer(s -> Story.cue(this.session(s), Story.CUE_END, "", "sabercraft.story.end", 0));
		int early = -2;
		int shown = -1;
		boolean shot = false;
		int over = -1;

		for (int i = 0; i < ticks + 40; i++) {
			this.context.waitTick();
			int age = this.context.computeOnClient(client -> StoryClient.endAgeForTest());
			boolean on = this.context.computeOnClient(client -> StoryClient.endedForTest());
			int credit = this.context.computeOnClient(client -> StoryHud.creditAgeForTest());

			if (on && age < 50 && credit >= 0) {
				early = credit;
			}

			if (credit >= 0 && shown < 0) {
				shown = credit;
			}

			if (on && age >= 130 && !shot) {
				shot = true;
				this.context.takeScreenshot("phase32_chapter_end");
			}

			if (!on && i > 10) {
				over = i;
				break;
			}
		}

		SaberCraft.LOGGER.info("PHASE32 END: {} ticks, the credit first drawn at age {}, the screen gone after {} ticks", ticks, shown, over);
		check(early < 0, "the credit shows before the chapter's text could be read (age " + early + ")");
		check(shown >= 50 && shown <= 100, "the credit \"Erstellt von Phiro\" is not drawn after the text (first at age " + shown + ")");
		check(over > ticks - 20 && over <= ticks + 20, "the chapter's end does not go after its time (gone after " + over + " of " + ticks + " ticks)");
		this.leave();
	}

	// --- 3: the stamina bar in the duel's films ----------------------------------------------------------------

	private void hud() {
		this.enter(5, "m6.duel");
		this.flags("saber.taken", "droid.m31", "droid.r2v7");
		this.server.runOnServer(s -> Story.updateDroids(this.session(s)));
		this.server.runCommand("effect give @a minecraft:resistance 100000 4 true");
		this.server.runCommand("item replace entity @a weapon.mainhand with " + SABER);
		this.put(new Vec3(40.5, H, -21.0), 180.0F, 0.0F);
		// The duel's film at its start (as the story plays it): no stamina drawn while it runs.
		String film = this.film(200);
		check("duel".equals(film), "the duel's film does not begin (" + film + ")");
		this.filmHud("duel");
		this.skipFilms(300);

		if (!this.on(s -> this.session(s).has("tmp.duel"))) {
			this.problem("the duel does not begin after its film (skipped)");
			return;
		}

		this.converse();
		this.context.waitTicks(10);
		this.drawnAgain("after the duel's film");
		// The scene at half her strength (the phase change): its film, no stamina; after it, again.
		this.server.runOnServer(s -> StoryTesting.halfStrength(this.session(s), this.her(s), this.me(s)));
		check("boss_mid".equals(this.film(40)), "the scene at half her strength does not play its film");
		this.filmHud("boss_mid");
		this.skip("boss_mid");
		this.context.waitTicks(20);
		this.drawnAgain("after the scene at half her strength");
	}

	/** While that film runs (some ticks of it), with the stamina low: no line of the ability HUD is drawn; and a screenshot. */
	private void filmHud(String film) {
		this.lowStamina();
		this.context.waitTicks(3);
		int drawn0 = this.context.computeOnClient(client -> SaberAbilityClient.hudLinesForTest());

		for (int i = 0; i < 30; i++) {
			if (i % 6 == 0) {
				this.lowStamina();
			}

			if (i == 15) {
				this.context.takeScreenshot("phase32_hud_film_" + film);
			}

			this.context.waitTick();
		}

		int drawn = this.context.computeOnClient(client -> SaberAbilityClient.hudLinesForTest()) - drawn0;
		boolean low = this.context.computeOnClient(client -> SaberAbilityClient.stamina() < SaberAbilityClient.maxStamina() - 1.0F);
		boolean running = film.equals(this.context.computeOnClient(client -> StoryCinema.filmForTest()));
		SaberCraft.LOGGER.info("PHASE32 HUD in the film {}: {} lines drawn (stamina low {}, the film still on {})", film, drawn, low, running);
		check(low && running, "the test could not hold the stamina low in the film " + film + " (low " + low + ", film on " + running + ")");
		check(drawn == 0, "the stamina bar is drawn " + drawn + " times in the film " + film);
	}

	/** With the stamina low and no film: the stamina bar is drawn. */
	private void drawnAgain(String when) {
		this.lowStamina();
		this.context.waitTicks(3);
		int drawn0 = this.context.computeOnClient(client -> SaberAbilityClient.hudLinesForTest());
		this.context.waitTicks(10);
		int drawn = this.context.computeOnClient(client -> SaberAbilityClient.hudLinesForTest()) - drawn0;
		check(drawn > 0, "the stamina bar is not drawn " + when);
		check(!this.context.computeOnClient(client -> client.gui.hud.isHidden()), "the HUD stays hidden " + when);
	}

	private void lowStamina() {
		this.server.runOnServer(s -> SaberAbilities.setStaminaForTests(this.me(s), AbilityConfig.get().staminaMax * 0.3F));
	}

	// --- 1, 2: her ring's whirr --------------------------------------------------------------------------------

	private void sound() {
		if (!this.secondPhase()) {
			return;
		}

		// Her plain blade work (the ring still): no whirr, except while her staff flies (a throw).
		// (Her ring's mode only when the test says: the order of the checks fixed.)
		this.server.runOnServer(s -> {
			this.before(s, 2.6);
			this.session(s).timer("ring_rest", 4000);
		});
		int wrong = this.bladeWork(160, "in her blade work");
		check(wrong == 0, "her ring's whirr plays " + wrong + " ticks in her plain blade work");
		// The ring's mode: exactly one loop.
		int started0 = this.context.computeOnClient(client -> RingSound.startedForTest());
		this.server.runOnServer(s -> {
			this.before(s, 2.6);
			StoryTesting.ringModeNow(this.session(s), this.her(s));
			this.session(s).timer("ring_mode", 4000);
		});
		int heard = -1;
		int most = 0;

		for (int i = 0; i < 120; i++) {
			this.context.waitTick();
			int playing = this.context.computeOnClient(client -> RingSound.playingForTest());
			most = Math.max(most, playing);

			if (playing > 0 && heard < 0) {
				heard = i;
			}
		}

		int started = this.context.computeOnClient(client -> RingSound.startedForTest()) - started0;
		SaberCraft.LOGGER.info("PHASE32 SOUND ring's mode: heard after {} ticks, at most {} loops, {} begun", heard, most, started);
		check(heard >= 0 && heard <= 30, "her ring's whirr is not heard as the ring's mode begins (" + heard + ")");
		check(most == 1, "her ring's whirr plays " + most + " loops at once");
		check(started <= 2, "her ring's whirr begins " + started + " times in one ring's mode");
		// A film begins: the whirr stops at once; after it (the ring still turning) it comes back.
		Vec3 point = this.on(s -> this.her(s).position().add(Vec3.atLowerCornerOf(this.session(s).destroyer)));
		this.server.runOnServer(s -> Story.cue(this.session(s), Story.CUE_CINEMA, "duel", "", 60, List.of(point)));
		this.context.waitTicks(3);
		check(this.context.computeOnClient(client -> StoryCinema.filmForTest()) != null, "the test's film does not begin");
		check(this.context.computeOnClient(client -> RingSound.playingForTest()) == 0, "her ring's whirr goes on in a film");
		this.context.runOnClient(client -> StoryCinema.skipForTest());
		this.server.runOnServer(s -> this.session(s).unset("tmp.film"));
		boolean back = false;

		for (int i = 0; i < 40 && !back; i++) {
			this.context.waitTick();
			back = this.context.computeOnClient(client -> RingSound.playingForTest()) == 1;
		}

		check(back, "her ring's whirr does not come back after the film (the ring still turning)");
		// The ring's mode ends: the whirr goes soon after the ring stops.
		check(this.on(s -> StoryTesting.ringOn(this.her(s))), "her ring's mode is over before the test ends it");
		this.server.runOnServer(s -> {
			StoryTesting.ringModeEnds(this.session(s));
			this.session(s).timer("ring_rest", 4000);
		});
		int stopped = -1;
		int quiet = -1;

		for (int i = 0; i < 160; i++) {
			this.context.waitTick();

			if (stopped < 0 && !this.on(s -> StoryTesting.ringOn(this.her(s)))) {
				stopped = i;
			}

			if (stopped >= 0 && quiet < 0 && this.context.computeOnClient(client -> RingSound.playingForTest()) == 0) {
				quiet = i;
				break;
			}
		}

		SaberCraft.LOGGER.info("PHASE32 SOUND ring's mode ended: the ring stopped after {} ticks, quiet after {}", stopped, quiet);
		check(stopped >= 0, "the ring's mode does not end");
		check(quiet >= 0 && quiet - stopped <= 40, "her ring's whirr goes on after the ring stopped (" + (quiet < 0 ? "never quiet" : quiet - stopped + " ticks") + ")");
		wrong = this.bladeWork(80, "after the ring's mode");
		check(wrong == 0, "her ring's whirr plays " + wrong + " ticks in her blade work after the ring's mode");
		// Caught in the ring's mode: the whirr stops and stays away (the duel begins anew: her first phase).
		this.server.runOnServer(s -> {
			this.before(s, 2.6);
			StoryTesting.ringModeNow(this.session(s), this.her(s));
			this.session(s).timer("ring_mode", 4000);
		});

		for (int i = 0; i < 40 && this.context.computeOnClient(client -> RingSound.playingForTest()) == 0; i++) {
			this.context.waitTick();
		}

		check(this.context.computeOnClient(client -> RingSound.playingForTest()) == 1, "her ring's whirr does not play before the capture");
		int captures = this.on(s -> Story.capturesForTest());
		this.server.runOnServer(s -> {
			ServerPlayer me = this.me(s);
			StoryTesting.lethal(me);
			me.hurtServer((ServerLevel) me.level(), me.damageSources().generic(), 40.0F);
		});
		int loud = 0;

		for (int i = 0; i < 100; i++) {
			this.context.waitTick();

			if (this.on(s -> Story.capturesForTest()) > captures && this.context.computeOnClient(client -> RingSound.playingForTest()) > 0) {
				loud++;
			}
		}

		check(this.on(s -> Story.capturesForTest()) > captures, "struck down in the ring's mode, the player is not caught");
		check(loud == 0, "her ring's whirr goes on " + loud + " ticks after the player was caught");
		this.server.runCommand("effect give @a minecraft:resistance 100000 4 true");
		this.leave();
		// Leaving the story in her ring's mode: the whirr stops at once (within 3 ticks: by itself,
		// her figure no longer drawn, it would fade out only after 4 ticks and more).
		if (!this.secondPhase()) {
			return;
		}

		this.server.runOnServer(s -> {
			this.before(s, 2.6);
			StoryTesting.ringModeNow(this.session(s), this.her(s));
			this.session(s).timer("ring_mode", 4000);
		});

		for (int i = 0; i < 40 && this.context.computeOnClient(client -> RingSound.playingForTest()) == 0; i++) {
			this.context.waitTick();
		}

		check(this.context.computeOnClient(client -> RingSound.playingForTest()) == 1, "her ring's whirr does not play before leaving");
		this.context.runOnClient(client -> StoryClient.send(StoryActionPayload.LEAVE, "", 0));
		this.context.waitTicks(3);
		check(this.context.computeOnClient(client -> RingSound.playingForTest()) == 0, "her ring's whirr goes on after leaving the story");
		this.context.waitTicks(27);
		check(this.on(s -> !Story.playing(this.me(s))), "still in the story after leaving");
		check(this.context.computeOnClient(client -> RingSound.playingForTest()) == 0, "her ring's whirr comes back after leaving the story");
	}

	/**
	 * Watches her fight with plain blade work for that many ticks (the player near her): ticks in
	 * which her whirr plays though her ring stands still and her staff is not thrown (a little after
	 * either is allowed for the whirr to fade out).
	 */
	private int bladeWork(int ticks, String what) {
		int wrong = 0;
		int calm = 100;
		float fastest = 0.0F;
		int id = this.on(s -> this.her(s).id);

		for (int i = 0; i < ticks; i++) {
			this.context.waitTick();
			boolean busy = this.on(s -> {
				Figure f = this.her(s);
				return f == null || StoryTesting.ringOn(f) || StoryTesting.staffOut(f) || "throw".equals(StoryTesting.act(f)) || "ring_up".equals(StoryTesting.act(f));
			});
			calm = busy ? 0 : calm + 1;

			if (calm > 30) {
				if (this.context.computeOnClient(client -> RingSound.playingForTest()) > 0) {
					wrong++;
				}

				fastest = Math.max(fastest, Math.abs((float) this.context.computeOnClient(client -> (Float) MeshAudit.ringSpeed(id))));
			}

			if (i % 40 == 39) {
				this.server.runOnServer(s -> {
					double apart = this.session(s).local(Story.DESTROYER).distanceTo(this.her(s).position());

					if (apart > 4.5 || apart < 1.0) {
						this.before(s, 2.6);
					}
				});
			}
		}

		SaberCraft.LOGGER.info("PHASE32 SOUND {}: {} ticks with a whirr, the ring at most {} rad/s", what, wrong, String.format(Locale.ROOT, "%.1f", fastest));
		check(fastest < 4.0F, "her ring turns fast (" + fastest + " rad/s) in her plain blade work (" + what + ")");
		return wrong;
	}

	// --- 4, 5: her spin hits in its whole circle ---------------------------------------------------------------

	private record Spin(int live, int firstHit, int hits, float lost, boolean liveAfterStroke) {
	}

	private void spin() {
		if (!this.secondPhase()) {
			return;
		}

		this.server.runOnServer(s -> {
			this.before(s, 3.0);
			StoryTesting.ringModeNow(this.session(s), this.her(s));
		});
		this.context.waitTicks(30);
		double reach = StoryTesting.spinNumbers()[0];
		this.server.runCommand("effect clear @a minecraft:resistance");
		// Standing anywhere in the circle (near her, half way, near its rim; before her, at her sides, behind her): hit at once, health lost.
		List<String> missed = new ArrayList<>();

		for (double d : new double[] {0.7, 1.6, 2.6, reach - 0.35}) {
			for (double angle : new double[] {0.0, 90.0, 180.0, 270.0}) {
				Spin spin = this.spinAt(d, angle, 0, false);

				if (spin.live() < 0 || spin.firstHit() < 0 || spin.firstHit() - spin.live() > 1 || spin.lost() <= 0.0F) {
					missed.add(String.format(Locale.ROOT, "%.1f at %.0f: %s", d, angle, spin));
				}
			}
		}

		SaberCraft.LOGGER.info("PHASE32 SPIN in the circle: missed {}", missed);
		check(missed.isEmpty(), "standing in her spin's circle the player is not hit at once: " + missed);
		// Kept in it (as one pressing in against the knock-back): hit again within the spin.
		Spin kept = this.spinAt(1.4, 45.0, 1, false);
		SaberCraft.LOGGER.info("PHASE32 SPIN kept in it: {}", kept);
		check(kept.hits() >= 2, "kept in her spin's circle the player is hit only " + kept.hits() + " times (staying in it is safe)");
		// Striking into it: it whirls on (and hits).
		Spin struck = this.spinAt(1.8, 0.0, 2, false);
		SaberCraft.LOGGER.info("PHASE32 SPIN struck into: {}", struck);
		check(struck.liveAfterStroke(), "a stroke into her spin ends it");
		check(struck.hits() >= 1, "striking into her spin the player is not hit");
		// Jumping in it: hit.
		Spin jumped = this.spinAt(1.6, 270.0, 3, false);
		SaberCraft.LOGGER.info("PHASE32 SPIN jumping: {}", jumped);
		check(jumped.hits() >= 1, "jumping in her spin's circle the player is not hit");
		// Running past her through its circle: hit.
		Spin passed = this.spinAt(reach + 1.0, 90.0, 4, false);
		SaberCraft.LOGGER.info("PHASE32 SPIN running past: {}", passed);
		check(passed.hits() >= 1, "running past her through her spin's circle the player is not hit");
		// Outside the circle: nothing.
		for (double d : new double[] {reach + 0.45, reach + 1.2}) {
			Spin outside = this.spinAt(d, 180.0, 0, false);
			check(outside.hits() == 0, "standing " + d + " from her, outside her spin's circle, it hits (" + outside + ")");
		}

		// The guard held up, kept in it: the first blow is held, the next lets some of it through.
		this.server.runOnServer(s -> {
			StoryTesting.freshGuard(this.me(s));
			SaberAbilities.setStaminaForTests(this.me(s), AbilityConfig.get().staminaMax);
		});

		if (this.raise()) {
			int chips0 = this.on(s -> this.session(s).counters.getOrDefault("spin_chip", 0));
			Spin guarded = this.spinAt(1.5, 0.0, 1, true);
			int chips = this.on(s -> this.session(s).counters.getOrDefault("spin_chip", 0)) - chips0;
			this.context.getInput().releaseKey(SaberAbilityClient.GUARD_KEY);
			SaberCraft.LOGGER.info("PHASE32 SPIN guarded and kept in it: {}, chips {}", guarded, chips);
			check(chips >= 1 && guarded.lost() > 0.0F, "a guard held up in her spin's circle keeps the player unhurt (chips " + chips + ", lost " + guarded.lost() + ")");
		}

		this.server.runCommand("effect give @a minecraft:resistance 100000 4 true");
		this.server.runOnServer(s -> this.me(s).setHealth(20.0F));
		this.leave();
	}

	/**
	 * One spin of hers, the player that far from her at that angle from where she faces (0 before
	 * her, 90 at her left, 180 behind her). How: 0 standing, 1 kept there (put back each tick), 2
	 * striking at her as it whirls, 3 jumping, 4 running past her sideways through the circle. With
	 * {@code guard} the guard key stays held. Health is kept up (counted as lost).
	 */
	private Spin spinAt(double distance, double angle, int how, boolean guard) {
		Vec3[] spot = new Vec3[1];
		this.server.runOnServer(s -> {
			Figure f = this.her(s);
			this.calm(s, 40.5, -32.0, 180.0F);
			this.session(s).timer("ring_mode", 4000);
			Vec3 look = f.look();
			Vec3 flat = new Vec3(look.x, 0.0, look.z).normalize();
			double a = Math.toRadians(angle);
			Vec3 dir = new Vec3(flat.x * Math.cos(a) + flat.z * Math.sin(a), 0.0, -flat.x * Math.sin(a) + flat.z * Math.cos(a));
			// (Running past: passing 1.5 from her, from that far to the side, along the circle's chord.)
			Vec3 across = new Vec3(-dir.z, 0.0, dir.x);
			Vec3 at = how == 4 ? f.position().add(dir.scale(1.5)).subtract(across.scale(distance)) : f.position().add(dir.scale(distance));
			spot[0] = at;
			this.teleport(s, this.session(s), at, how == 4 ? yaw(at, at.add(across)) : yaw(at, f.position()));
			this.me(s).setDeltaMovement(Vec3.ZERO);
			this.me(s).setHealth(20.0F);
		});
		this.context.waitTicks(3);
		int hurt0 = this.on(s -> this.session(s).counters.getOrDefault("spin_contact", 0));
		this.server.runOnServer(s -> {
			this.me(s).setHealth(20.0F);
			StoryTesting.spinNow(this.session(s), this.her(s));
		});
		int live = -1;
		int firstHit = -1;
		float lost = 0.0F;
		boolean liveAfterStroke = true;
		int end = -1;

		if (how == 4) {
			this.context.getInput().holdKey(options -> options.keyUp);
			this.context.getInput().holdKey(options -> options.keySprint);
		}

		for (int i = 0; i < 50; i++) {
			if (how == 3 && live < 0 && i == 5) {
				this.context.getInput().holdKeyFor(options -> options.keyJump, 3);
			}

			this.context.waitTick();
			boolean on = this.on(s -> StoryTesting.spinLive(this.her(s)));
			float health = this.on(s -> this.me(s).getHealth());

			if (health < 20.0F) {
				lost += 20.0F - health;
				this.server.runOnServer(s -> this.me(s).setHealth(20.0F));
			}

			if (on && live < 0) {
				live = i;
			}

			if (!on && live >= 0 && end < 0) {
				end = i;
			}

			int hurt = this.on(s -> this.session(s).counters.getOrDefault("spin_contact", 0)) - hurt0;

			if (firstHit < 0 && hurt > 0) {
				firstHit = i;
			}

			if (how == 1 && on) {
				Vec3 back = spot[0];
				this.server.runOnServer(s -> {
					this.teleport(s, this.session(s), back, yaw(back, this.her(s).position()));
					this.me(s).setDeltaMovement(Vec3.ZERO);
				});
			}

			if (how == 2 && live >= 0 && i == live + 2) {
				this.server.runOnServer(s -> {
					this.teleport(s, this.session(s), spot[0], yaw(spot[0], this.her(s).position()));
					StoryTesting.action(this.me(s), StoryActionPayload.STRIKE);
				});
			}

			if (how == 2 && live >= 0 && i == live + 3) {
				liveAfterStroke = on;
			}

			if (end >= 0 && i > end + 3) {
				break;
			}
		}

		if (how == 4) {
			this.context.getInput().releaseKey(options -> options.keyUp);
			this.context.getInput().releaseKey(options -> options.keySprint);
		}

		int hits = this.on(s -> this.session(s).counters.getOrDefault("spin_contact", 0)) - hurt0;
		this.server.runOnServer(s -> {
			this.me(s).setHealth(20.0F);
			StoryTesting.guard(this.her(s), 4000);
		});
		this.context.waitTicks(8);
		return new Spin(live, firstHit, hits, lost, liveAfterStroke);
	}

	// --- 6, 7: her throw ---------------------------------------------------------------------------------------

	private void throwing() {
		if (!this.secondPhase()) {
			return;
		}

		boolean[] seen = new boolean[2];
		int catches0 = this.on(s -> StoryTesting.catches());
		// Her release as drawn: the spinning staff clear of her body, in her hand, where the server
		// lets it fly (the low one was drawn a block out, its flat disc through her legs).
		float[][] release = this.context.computeOnClient(client -> MeshAudit.throwRelease());

		for (int low = 0; low <= 1; low++) {
			String which = low == 1 ? "low" : "high";
			SaberCraft.LOGGER.info("PHASE32 THROW RELEASE {}: {} poses touching her, hand at most {} short of the grip, drawn {} from the server's start",
					which, (int) release[low][0], String.format(Locale.ROOT, "%.2f", release[low][1]), String.format(Locale.ROOT, "%.2f", release[low][2]));
			check(release[low][0] == 0, "her " + which + " throw's release is drawn through her body (" + (int) release[low][0] + " poses)");
			check(release[low][1] <= 0.12F, "her " + which + " throw's release is drawn out of her hand (" + release[low][1] + ")");
			check(release[low][2] <= 0.25F, "her " + which + " throw's release is drawn " + release[low][2] + " from where it flies from");
		}

		for (int round = 0; round < 2; round++) {
			this.server.runOnServer(s -> {
				this.calm(s, 40.5, -34.0, 180.0F);
				StoryTesting.ringModeEnds(this.session(s));
				this.session(s).timer("ring_rest", 4000);
				this.before(s, 6.5);
				Figure f = this.her(s);
				float yaw = com.phiro.sabercraft.interior.Crew.yaw(f.position(), this.session(s).local(Story.DESTROYER));
				f.yaw = yaw;
				f.headYaw = yaw;
				StoryTesting.throwNow(this.session(s), f);
			});
			String start = null;

			for (int i = 0; i < 60 && start == null; i++) {
				this.context.waitTick();
				start = this.on(s -> {
					Figure f = this.her(s);

					if (!StoryTesting.staffOut(f) || f.throwFrom == null || f.throwHand == null) {
						return null;
					}

					Vec3 corner = Vec3.atLowerCornerOf(this.session(s).destroyer);
					Vec3 feet = f.position().add(corner);
					Vec3 look = f.look();
					Vec3 flat = new Vec3(look.x, 0.0, look.z).normalize();
					Vec3 right = new Vec3(-flat.z, 0.0, flat.x);
					Vec3 from = f.throwFrom.subtract(feet);
					Vec3 hand = f.throwHand.subtract(feet);
					boolean low = StoryTesting.throwLow(f);
					seen[low ? 1 : 0] = true;
					List<String> wrong = new ArrayList<>();

					if (from.dot(right) < 0.15) {
						wrong.add(String.format(Locale.ROOT, "let go %.2f to her right", from.dot(right)));
					}

					double height = low ? StoryTesting.throwLowHeight() : StoryTesting.throwHigh();

					if (Math.abs(from.y - height) > 0.2) {
						wrong.add(String.format(Locale.ROOT, "let go %.2f over her feet (%s throw, %.2f wanted)", from.y, low ? "low" : "high", height));
					}

					if (hand.dot(right) <= 0.0 || Math.hypot(hand.x, hand.z) > 0.9 || hand.y < 1.1 || hand.y > 1.7) {
						wrong.add(String.format(Locale.ROOT, "caught back in a hand at %.2f,%.2f,%.2f (right %.2f)", hand.x, hand.y, hand.z, hand.dot(right)));
					}

					return (low ? "low: " : "high: ") + (wrong.isEmpty() ? "ok" : String.join(", ", wrong));
				});
			}

			SaberCraft.LOGGER.info("PHASE32 THROW {}: {}", round, start);
			check(start != null, "her throw does not fly (round " + round + ")");
			check(start == null || start.endsWith("ok"), "her throw: " + start);
			this.context.takeScreenshot("phase32_throw_" + round);
			// Caught back: an opening.
			boolean open = false;

			for (int i = 0; i < 160 && !open; i++) {
				this.context.waitTick();
				open = this.on(s -> !StoryTesting.staffOut(this.her(s)) && "open".equals(StoryTesting.act(this.her(s))));
			}

			check(open, "she is not open as she catches her staff again (round " + round + ")");
			this.context.waitTicks(20);
		}

		check(this.on(s -> StoryTesting.catches()) - catches0 >= 2, "her staff is not caught back twice");
		check(seen[0] && seen[1], "her throws are not high and low in turn (high " + seen[0] + ", low " + seen[1] + ")");
		this.leave();
	}

	// --- 8, 9, 10: her second phase: two ways of fighting, a defence with limits ---------------------------

	private void defence() {
		if (!this.secondPhase()) {
			return;
		}

		// A player who watches her: heavy strokes at her guard now and then, into her openings at
		// once, at her side and her back as they come; she keeps fighting. Over it, her two ways of
		// fighting take turns.
		int strokes0 = this.on(s -> StoryTesting.strokes());
		int hits0 = this.on(s -> this.session(s).counters.getOrDefault("duel_hits", 0));
		int breaks0 = this.on(s -> this.session(s).counters.getOrDefault("boss_guard_broken", 0));
		int[] switches0 = StoryTesting.ringSwitches();
		Set<String> attacking = Set.of("windup", "slam", "push", "rush", "leap", "throw");
		List<int[]> stretches = new ArrayList<>();
		boolean ring = this.on(s -> StoryTesting.ringOn(this.her(s)));
		int since = 0;
		int attacks = 0;
		String was = "guard";
		int lastStroke = -100;
		int id = this.on(s -> this.her(s).id);
		float fastestStill = 0.0F;
		int stillSince = 100;
		int fastRun = 0;
		int longestFast = 0;

		for (int t = 0; t < 1100; t++) {
			this.context.waitTick();
			String now = this.on(s -> this.session(s).has("tmp.duel") ? StoryTesting.act(this.her(s)) : "over");

			if ("over".equals(now)) {
				this.problem("the duel ended while the player only tested her defence");
				break;
			}

			if (attacking.contains(now) && !now.equals(was)) {
				attacks++;
			}


			was = now;
			boolean on = this.on(s -> StoryTesting.ringOn(this.her(s)));

			if (on != ring) {
				stretches.add(new int[] {ring ? 1 : 0, t - since});
				ring = on;
				since = t;
			}

			// (Not while she spins the ring up into its mode: it turns then, to be seen.)
			boolean still = !on && !"ring_up".equals(now) && this.on(s -> !StoryTesting.staffOut(this.her(s)) && !"throw".equals(StoryTesting.act(this.her(s))));
			stillSince = still ? stillSince + 1 : 0;

			if (stillSince > 30) {
				// (A parried stroke twitches the ring round on its hub: a moment, then it is still again.
				// Turning is what lasts.)
				float speedNow = Math.abs((float) this.context.computeOnClient(client -> (Float) MeshAudit.ringSpeed(id)));
				fastRun = speedNow > 4.0F ? fastRun + 1 : 0;
				fastestStill = Math.max(fastestStill, speedNow);
				longestFast = Math.max(longestFast, fastRun);
			} else {
				fastRun = 0;
			}

			// (Her strength kept in her second phase, before her last quarter: each tick, or a few
			// quick hits into an opening took her into it, and its flare spun her ring.)
			{
				this.server.runOnServer(s -> {
					Figure f = this.her(s);

					if (f.health < 0.36F) {
						f.health = 0.42F;
					}

					this.me(s).setHealth(20.0F);
				});
			}

			boolean open = "open".equals(now) || "recoil".equals(now);
			boolean windup = "windup".equals(now);
			boolean heavyDue = t - lastStroke >= 18;

			if (open && t - lastStroke >= 6 || heavyDue && ("guard".equals(now) || windup) && !"over".equals(now)) {
				// Into an opening at once; at her guard (from before her or now and then her side) a full stroke; into her wind-up from her side or back.
				int side = open ? 0 : windup ? 2 : t / 18 % 4 == 3 ? 1 : 0;
				this.server.runOnServer(s -> {
					this.strikeFrom(s, side == 0 ? 0.0 : side == 1 ? 80.0 : 165.0, 2.2);
					StoryTesting.action(this.me(s), StoryActionPayload.STRIKE);
				});
				lastStroke = t;
			}

			if (t % 60 == 59) {
				this.server.runOnServer(s -> {
					double apart = this.session(s).local(Story.DESTROYER).distanceTo(this.her(s).position());

					if (apart > 5.0 || apart < 0.8) {
						this.before(s, 2.6);
					}
				});
			}
		}

		stretches.add(new int[] {ring ? 1 : 0, 1100 - since});
		int strokes = this.on(s -> StoryTesting.strokes()) - strokes0;
		int hits = this.on(s -> this.session(s).counters.getOrDefault("duel_hits", 0)) - hits0;
		int breaks = this.on(s -> this.session(s).counters.getOrDefault("boss_guard_broken", 0)) - breaks0;
		int[] switches = StoryTesting.ringSwitches();
		SaberCraft.LOGGER.info("PHASE32 DEFENCE watching her: {} strokes, {} hit her, her guard broken {} times, she attacked {} times; ring stretches (on, ticks) {}; "
				+ "switches {}/{}; the ring still at most {} rad/s, turning on for {} ticks at most", strokes, hits, breaks, attacks,
				stretches.stream().map(a -> (a[0] == 1 ? "on " : "off ") + a[1]).toList(), switches[0] - switches0[0], switches[1] - switches0[1],
				String.format(Locale.ROOT, "%.1f", fastestStill), longestFast);
		check(strokes >= 20, "the test made only " + strokes + " strokes");
		check(hits >= strokes / 5, "watching her and striking at the right moments, only " + hits + " of " + strokes + " strokes hit her (she blocks nearly everything)");
		check(breaks >= 1, "her guard is never worn down by strokes");
		check(attacks >= 8, "while defending she attacks only " + attacks + " times in 55 s");
		// The two ways take turns: ring stretches of a while, plain blade work between them of a while.
		List<int[]> whole = stretches.subList(1, Math.max(1, stretches.size() - 1));
		long ons = stretches.stream().filter(a -> a[0] == 1).count();
		check(ons >= 2, "her ring's mode comes only " + ons + " times in 55 s of her second phase");

		for (int[] stretch : whole) {
			if (stretch[0] == 1) {
				check(stretch[1] >= 120 && stretch[1] <= 420, "a stretch of her ring's mode lasts " + stretch[1] + " ticks");
			} else {
				check(stretch[1] >= 160, "her plain blade work between two ring's modes lasts only " + stretch[1] + " ticks");
			}
		}

		check(longestFast <= 15, "her ring turns (up to " + fastestStill + " rad/s, " + longestFast + " ticks running) in her plain blade work");

		// A player hammering at her guard (light strokes as fast as they go, from before her): she
		// answers with her riposte, which reaches the player; not every stroke is turned aside forever.
		this.server.runOnServer(s -> {
			StoryTesting.ringModeEnds(this.session(s));
			this.session(s).timer("ring_rest", 4000);
		});
		this.context.waitTicks(60);
		int ripostes0 = this.on(s -> StoryTesting.ripostes());
		int reached0 = this.on(s -> this.session(s).counters.getOrDefault("duel_hurt", 0) + this.session(s).counters.getOrDefault("blocked", 0)
				+ this.session(s).counters.getOrDefault("perfect", 0) + this.session(s).counters.getOrDefault("dodged", 0));
		int parried0 = this.on(s -> this.session(s).counters.getOrDefault("parried", 0));
		int mashes0 = this.on(s -> StoryTesting.strokes());
		int hitsMash0 = this.on(s -> this.session(s).counters.getOrDefault("duel_hits", 0));
		int beaten0 = this.on(s -> this.session(s).counters.getOrDefault("beaten_aside", 0));
		int clashes0 = this.on(s -> this.session(s).counters.getOrDefault("clashes", 0));
		int breaksMash0 = this.on(s -> this.session(s).counters.getOrDefault("boss_guard_broken", 0));

		for (int t = 0; t < 336; t += 7) {
			this.server.runOnServer(s -> {
				Figure f = this.her(s);

				if (f.health < 0.36F) {
					f.health = 0.42F;
				}

				this.me(s).setHealth(20.0F);
				this.strikeFrom(s, 0.0, 2.0);
				StoryTesting.action(this.me(s), StoryActionPayload.STRIKE);
			});
			this.context.waitTicks(7);
		}

		int ripostes = this.on(s -> StoryTesting.ripostes()) - ripostes0;
		int reached = this.on(s -> this.session(s).counters.getOrDefault("duel_hurt", 0) + this.session(s).counters.getOrDefault("blocked", 0)
				+ this.session(s).counters.getOrDefault("perfect", 0) + this.session(s).counters.getOrDefault("dodged", 0)) - reached0;
		int parried = this.on(s -> this.session(s).counters.getOrDefault("parried", 0)) - parried0;
		int mashes = this.on(s -> StoryTesting.strokes()) - mashes0;
		int hitsMash = this.on(s -> this.session(s).counters.getOrDefault("duel_hits", 0)) - hitsMash0;
		int beaten = this.on(s -> this.session(s).counters.getOrDefault("beaten_aside", 0)) - beaten0;
		int clashesMash = this.on(s -> this.session(s).counters.getOrDefault("clashes", 0)) - clashes0;
		int breaksMash = this.on(s -> this.session(s).counters.getOrDefault("boss_guard_broken", 0)) - breaksMash0;
		SaberCraft.LOGGER.info("PHASE32 DEFENCE hammering: {} strokes, {} parried, {} hit her, {} ripostes, {} of her blows reached the player, {} beaten aside by her blow, "
				+ "{} clashes, her guard broken {} times", mashes, parried, hitsMash, ripostes, reached, beaten, clashesMash, breaksMash);
		// Hammering is not the way through: her answers are not broken off by clashes, and her
		// guard is worn down more slowly than by a player who watches her (above).
		check(beaten >= 1, "hasty strokes into her wind-up are never beaten aside by her blow");
		check(clashesMash <= beaten, "hammering at her, her answers are clashed away " + clashesMash + " times (beaten aside " + beaten + ")");
		// Watching her pays: of a hammering player's strokes clearly fewer hit her, and her guard is
		// broken less often in the same time, than for the player who watched her above.
		double rateWatching = hits / (double) Math.max(1, strokes);
		double rateHammering = hitsMash / (double) Math.max(1, mashes);
		SaberCraft.LOGGER.info("PHASE32 DEFENCE watching pays: hits per stroke {} watching, {} hammering; guard broken every {} ticks watching, every {} hammering",
				String.format(Locale.ROOT, "%.2f", rateWatching), String.format(Locale.ROOT, "%.2f", rateHammering), breaks == 0 ? "-" : 1100 / breaks,
				breaksMash == 0 ? "-" : 336 / breaksMash);
		check(rateHammering < 0.85 * rateWatching, String.format(Locale.ROOT, "hammering at her pays as well as watching her (%.2f of the strokes hit her, %.2f watching)",
				rateHammering, rateWatching));
		check(breaksMash * 1100 < breaks * 336, "hammering at her breaks her guard as often as watching her (" + breaksMash + " in 336 ticks, " + breaks + " in 1100)");
		// Set back into the sealed hangar in her second phase: the duel goes on as it was on the
		// client too (her second blade, her phase), not drawn as at its start.
		this.server.runOnServer(s -> this.teleport(s, this.session(s), new Vec3(40.5, H, -12.5), 0.0F));
		boolean setBack = false;

		for (int i = 0; i < 80 && !setBack; i++) {
			this.context.waitTick();
			setBack = this.on(s -> StoryTesting.inHangar(this.session(s).local(Story.DESTROYER)));
		}

		this.context.waitTicks(30);
		check(setBack, "out of the hangar in her second phase, the player is not set back");
		check(this.context.computeOnClient(client -> StoryClient.duelPhaseForTest()) == 2, "set back into the hangar, the client draws her first phase again");
		check(this.context.computeOnClient(client -> MeshAudit.twinProgress(id)) > 0.35F, "set back into the hangar, her second blade is gone on the client");
		check(ripostes >= 1, "hammering at her guard she never answers with her riposte");
		check(reached >= 1, "hammering at her guard none of her blows reaches the player (her riposte is lost)");
		check(parried < mashes, "every one of " + mashes + " strokes hammered at her guard is turned aside");
		this.leave();
	}

	/** Puts the player that far from her at that angle from where she faces (0 before her, 80 at her side, 165 behind her), facing her. */
	private void strikeFrom(MinecraftServer s, double angle, double distance) {
		Figure f = this.her(s);
		Vec3 look = f.look();
		Vec3 flat = new Vec3(look.x, 0.0, look.z).normalize();
		double a = Math.toRadians(angle);
		Vec3 dir = new Vec3(flat.x * Math.cos(a) + flat.z * Math.sin(a), 0.0, -flat.x * Math.sin(a) + flat.z * Math.cos(a));
		Vec3 at = f.position().add(dir.scale(distance));

		if (!StoryTesting.inHangar(at)) {
			at = f.position().add(flat.scale(distance));
		}

		ServerPlayer player = this.me(s);
		Vec3 world = at.add(Vec3.atLowerCornerOf(this.session(s).destroyer));
		Vec3 aim = f.position().add(0.0, 1.0, 0.0);
		player.teleportTo(s.getLevel(ShipInteriors.DIMENSION), world.x, world.y, world.z, Set.of(), yaw(at, aim), pitch(at.add(0.0, 1.62, 0.0), aim), true);
		player.setDeltaMovement(Vec3.ZERO);
	}

	// --- 11, 12, 13: the hangar sealed in the duel ------------------------------------------------------------

	private void hangar() {
		if (!this.duel()) {
			return;
		}

		check(this.on(s -> StoryTesting.hangarSealed(this.session(s))), "the hangar is not sealed in the duel");
		check(this.context.computeOnClient(client -> StoryClient.has(StoryTesting.hangarSealedFlag())), "the client does not know the hangar is sealed (its lamps)");
		check(this.on(s -> StoryTesting.point(this.session(s), "toolbox") == null && StoryTesting.point(this.session(s), "vent_cover") == null),
				"the toolbox or the vent's cover can be used in the duel");
		InteriorPlan.Door main = DestroyerInterior.DOORS.get(3);
		InteriorPlan.Door flight = DestroyerInterior.DOORS.get(4);
		// Walking at the main door, and pressed against it, and jumping at it.
		this.walkAt(main, new Vec3(40.5, H, -19.0), 0.0F, "the hangar's main door");
		this.walkAt(main, new Vec3(39.5, H, -16.45), 0.0F, "the hangar's main door (pressed against it)");
		this.walkAt(flight, new Vec3(20.0, H, -38.5), 90.0F, "the flight control's door");
		this.server.runOnServer(s -> StoryTesting.guard(this.her(s), 4000));
		this.put(new Vec3(40.5, H, -19.0), 0.0F, 0.0F);
		this.context.takeScreenshot("phase32_hangar_door_sealed");
		// The hatch: a click does not open it (its cover off as one might have left it).
		this.flags(StoryTesting.coverOpen());
		this.server.runOnServer(s -> StoryTesting.ventCover(this.session(s)));
		BlockPos hatch = DestroyerInterior.HANGAR_HATCH;
		int refusals0 = this.on(s -> StoryTesting.sealed()[0]);
		this.put(new Vec3(18.75, H, -28.5), 90.0F, 42.0F);
		this.context.getInput().pressKey(options -> options.keyUse);
		this.context.waitTicks(8);
		check(this.on(s -> this.level(s).getBlockState(this.world(s, hatch)).is(Blocks.BARRIER)), "a click opens the hangar's hatch in the duel");
		check(this.on(s -> StoryTesting.sealed()[0]) > refusals0, "the click on the sealed hatch is not refused (as the game counts it)");
		this.put(new Vec3(19.5, H, -28.5), 90.0F, 20.0F);
		this.context.takeScreenshot("phase32_hangar_hatch_sealed");
		// A hatch found open is shut.
		this.put(new Vec3(24.5, H, -28.5), 90.0F, 0.0F);
		this.server.runOnServer(s -> this.level(s).setBlock(this.world(s, hatch), Blocks.AIR.defaultBlockState(), 3));
		this.context.waitTicks(30);
		check(this.on(s -> this.level(s).getBlockState(this.world(s, hatch)).is(Blocks.BARRIER)), "an open hatch of the hangar stays open in the duel");
		// Out in the corridor, or in the crawl way: set back into the hangar.
		for (Vec3 out : new Vec3[] {new Vec3(40.5, H, -12.5), new Vec3(13.5, H, -28.5)}) {
			int returns0 = this.on(s -> StoryTesting.sealed()[1]);
			this.server.runOnServer(s -> this.teleport(s, this.session(s), out, 0.0F));
			boolean back = false;

			for (int i = 0; i < 80 && !back; i++) {
				this.context.waitTick();
				back = this.on(s -> StoryTesting.inHangar(this.session(s).local(Story.DESTROYER)));
			}

			check(back, "out of the hangar in the duel (" + out + "), the player is not set back into it");
			check(this.on(s -> StoryTesting.sealed()[1]) > returns0, "the player set back is not counted (" + out + ")");
			this.skipFilms(40);
		}

		// Beaten: the doors as ever (open for the player), the flag gone.
		this.defeat();

		if (this.on(s -> this.session(s).has("tmp.duel"))) {
			this.problem("she is not beaten");
			return;
		}

		this.film(40);
		this.context.waitTicks(10);
		this.filmHud("duel_end");
		this.skip("duel_end");
		this.waitStep("m6.falcon", 160);
		this.context.waitTicks(10);

		// After her defeat (its film and any words after it): the stamina bar back.
		for (int i = 0; i < 40; i++) {
			String film = this.context.computeOnClient(client -> StoryCinema.filmForTest());

			if (film != null) {
				this.skip(film);
			} else if (this.on(s -> this.session(s).dialog != null)) {
				this.server.runOnServer(s -> StoryTesting.answer(this.session(s), 0));
				this.context.waitTicks(5);
			} else if (this.context.computeOnClient(client -> client.gui.screen() == null)) {
				break;
			} else {
				this.context.waitTicks(5);
			}
		}

		this.drawnAgain("after her defeat");
		check(!this.on(s -> StoryTesting.hangarSealed(this.session(s))), "the hangar stays sealed after her defeat");
		check(!this.context.computeOnClient(client -> StoryClient.has(StoryTesting.hangarSealedFlag())), "the client still shows the hangar sealed after her defeat");
		check(this.opens(main, new Vec3(40.5, H, -19.0), 0.0F), "after her defeat the hangar's main door does not open for the player");
		this.context.takeScreenshot("phase32_hangar_door_after");
		// Left during the duel: the doors as ever (the plot left alone, no story step keeps them).
		this.duel();
		BlockPos origin = this.on(s -> this.session(s).destroyer);
		check(this.on(s -> Story.locked(origin, main.x0(), main.y0(), main.z0())), "in the duel begun again the hangar's door is not locked");
		this.leave();
		check(!this.on(s -> Story.locked(origin, main.x0(), main.y0(), main.z0())), "after leaving the story in the duel the hangar's door stays locked");
	}

	/** Walks (and jumps) at that door from there for a while: it must stay shut, the player not through. */
	private void walkAt(InteriorPlan.Door door, Vec3 from, float yaw, String what) {
		this.server.runOnServer(s -> StoryTesting.guard(this.her(s), 4000));
		this.put(from, yaw, 0.0F);
		boolean opened = false;
		boolean through = false;
		this.context.getInput().holdKey(options -> options.keyUp);

		for (int i = 0; i < 40; i++) {
			if (i == 15) {
				this.context.getInput().holdKeyFor(options -> options.keyJump, 3);
			}

			this.context.waitTick();
			opened |= this.on(s -> InteriorSystems.doorOpenForTest(this.level(s), this.session(s).destroyer, door));
			through |= this.on(s -> {
				Vec3 me = this.session(s).local(Story.DESTROYER);
				return door.alongX() ? me.x < door.x0() + 0.8 : me.z > door.z0() + 0.2;
			});
		}

		this.context.getInput().releaseKey(options -> options.keyUp);
		this.context.waitTicks(3);
		check(!opened, "in the duel " + what + " opens");
		check(!through, "in the duel the player gets through " + what);
	}

	/** Whether that door opens for the player walking at it from there. */
	private boolean opens(InteriorPlan.Door door, Vec3 from, float yaw) {
		this.put(from, yaw, 0.0F);
		boolean opened = false;
		this.context.getInput().holdKey(options -> options.keyUp);

		for (int i = 0; i < 40 && !opened; i++) {
			this.context.waitTick();
			opened = this.on(s -> InteriorSystems.doorOpenForTest(this.level(s), this.session(s).destroyer, door));
		}

		this.context.getInput().releaseKey(options -> options.keyUp);
		this.context.waitTicks(3);
		return opened;
	}

	/** Beats her (strokes into her openings) until the duel is over. */
	private void defeat() {
		this.server.runOnServer(s -> StoryTesting.lastStrength(this.session(s), this.her(s), this.me(s)));
		this.context.waitTicks(5);

		for (int i = 0; i < 60 && this.on(s -> this.session(s).has("tmp.duel")); i++) {
			this.server.runOnServer(s -> {
				Figure shadow = this.session(s).figure("shadow");

				if (shadow != null && shadow.hostile && !"lock".equals(StoryTesting.action(shadow))) {
					this.before(s, 2.0);
					StoryTesting.open(shadow);
				}
			});
			this.context.waitTicks(2);
			this.context.getInput().pressKey(options -> options.keyAttack);
			this.context.waitTicks(8);
		}
	}

	// --- Helpers -------------------------------------------------------------------------------------------------

	/** The duel begun and her second phase reached (its scene skipped), the player near her. */
	private boolean secondPhase() {
		if (!this.duel()) {
			return false;
		}

		this.server.runOnServer(s -> StoryTesting.halfStrength(this.session(s), this.her(s), this.me(s)));
		this.film(40);
		this.context.waitTicks(5);
		this.skip("boss_mid");
		this.context.waitTicks(30);

		if (!this.on(s -> StoryTesting.phase(this.her(s)) == 2)) {
			this.problem("her second phase does not begin");
			return false;
		}

		return true;
	}

	/** The duel begun: the story at it, the films and her words skipped, the player with a saber and unhurt. */
	private boolean duel() {
		// (Her defeat in an earlier part is in the saved file: forgotten, she is there again.)
		this.enter(5, "m6.duel", "gone.shadow");
		this.flags("saber.taken", "droid.m31", "droid.r2v7");
		this.server.runOnServer(s -> Story.updateDroids(this.session(s)));
		this.server.runCommand("effect give @a minecraft:resistance 100000 4 true");
		this.server.runCommand("item replace entity @a weapon.mainhand with " + SABER);
		this.put(new Vec3(40.5, H, -21.0), 180.0F, 0.0F);
		this.skipFilms(300);

		if (!this.on(s -> this.session(s).has("tmp.duel"))) {
			this.problem("the duel does not begin after its film (skipped)");
			return false;
		}

		this.converse();
		this.context.waitTicks(10);
		return true;
	}

	private Figure her(MinecraftServer s) {
		return this.session(s).figure("shadow");
	}

	/** She stands there on guard for a long while (no attack of hers in the way), facing that way, nothing going on. */
	private void calm(MinecraftServer s, double x, double z, float yaw) {
		Figure f = this.her(s);
		StoryTesting.guard(f, 4000);
		f.react = 0;
		f.reactTicks = 0;
		f.x = x;
		f.z = z;
		f.yaw = yaw;
		f.headYaw = yaw;
		f.speed = 0.0F;
	}

	/** Puts the player that far before her (along the line between them), facing her. */
	private void before(MinecraftServer s, double distance) {
		StorySession session = this.session(s);
		Figure f = session.figure("shadow");
		Vec3 her = f.position();
		Vec3 me = session.local(Story.DESTROYER);
		Vec3 away = new Vec3(me.x - her.x, 0.0, me.z - her.z);
		away = away.lengthSqr() < 0.01 ? new Vec3(0.0, 0.0, 1.0) : away.normalize();
		Vec3 to = her.add(away.scale(distance));

		if (!StoryTesting.inHangar(to)) {
			to = her.add(0.0, 0.0, -distance);
		}

		this.teleport(s, session, to, yaw(to, her));
	}

	/** Raises the guard with its key (held); whether it is up. */
	private boolean raise() {
		this.context.getInput().holdKey(SaberAbilityClient.GUARD_KEY);

		for (int i = 0; i < 30; i++) {
			this.context.waitTick();

			if (this.on(s -> SaberAbilities.isGuarding(this.me(s)))) {
				return true;
			}
		}

		this.problem("the test cannot raise the guard in the duel");
		this.context.getInput().releaseKey(SaberAbilityClient.GUARD_KEY);
		return false;
	}

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

	/** The film the client plays (waiting up to that many ticks for one to begin). */
	private @Nullable String film(int ticks) {
		for (int i = 0; i < ticks; i++) {
			String film = this.context.computeOnClient(client -> StoryCinema.filmForTest());

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
			String film = this.context.computeOnClient(client -> StoryCinema.filmForTest());

			if (film != null) {
				this.context.runOnClient(client -> StoryClient.send(StoryActionPayload.SKIP, film, 0));
				this.context.runOnClient(client -> StoryCinema.skipForTest());
			} else if (this.on(s -> this.session(s) != null && this.session(s).has("tmp.duel"))) {
				return;
			} else if (this.on(s -> this.session(s) != null && this.session(s).dialog != null)) {
				this.server.runOnServer(s -> StoryTesting.answer(this.session(s), 0));
			}

			this.context.waitTicks(5);
		}
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

	private static float yaw(Vec3 from, Vec3 to) {
		return (float) Math.toDegrees(Math.atan2(-(to.x - from.x), to.z - from.z));
	}

	private static float pitch(Vec3 from, Vec3 to) {
		double flat = Math.hypot(to.x - from.x, to.z - from.z);
		return (float) -Math.toDegrees(Math.atan2(to.y - from.y, flat));
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

	/** Continues the story at that step (a file saved there), as soon as it is there. */
	private void enter(int mission, String step, String... forget) {
		if (this.on(s -> this.session(s) != null)) {
			this.leave();
		}

		if (forget.length > 0) {
			this.server.runOnServer(s -> StoryTesting.forget(s, this.self, forget));
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
		player.teleportTo(server.getLevel(ShipInteriors.DIMENSION), world.x, world.y, world.z, Set.of(), yaw, 0.0F, true);
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
		SaberCraft.LOGGER.error("PHASE32 CHECK FAILED: {}", problem);
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
