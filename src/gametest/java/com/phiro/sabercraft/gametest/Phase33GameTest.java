package com.phiro.sabercraft.gametest;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
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
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.blaster.BlasterBolt;
import com.phiro.sabercraft.client.lang.SaberLanguage;
import com.phiro.sabercraft.client.music.StoryMusic;
import com.phiro.sabercraft.client.story.StoryCinema;
import com.phiro.sabercraft.client.story.StoryClient;
import com.phiro.sabercraft.interior.DestroyerInterior;
import com.phiro.sabercraft.interior.ShipInteriors;
import com.phiro.sabercraft.network.StoryActionPayload;
import com.phiro.sabercraft.network.StoryDialogPayload;
import com.phiro.sabercraft.story.Figure;
import com.phiro.sabercraft.story.Story;
import com.phiro.sabercraft.story.StorySession;
import com.phiro.sabercraft.story.StoryTesting;

/**
 * Phase 33: the last gameplay changes before the release. What a test can play in the real game
 * (the feel stays for the manual list in {@code docs/development/phase-33-status.md}):
 *
 * <ol>
 * <li>Names: wherever the story speaks to the player or names them, their own Minecraft name (the
 * texts' mark filled in on each client, so every player reads their own); "Funke" nowhere left as
 * the player's name, in both languages.</li>
 * <li>Music: the room of the saber has its own piece the moment one steps in, crossfading from the
 * reactor's; taking the saber its own film music, then the escape's; Kestrel's capture and the
 * way home their own; never two pieces left playing after a change.</li>
 * <li>The guards at their posts on the way to the hangar: one who sees the player before him fires
 * quickly; one with the player behind him (not too near) does not see them.</li>
 * <li>The whole way from the saber to the hangar run through as a player runs it (no cover, no
 * guard): the player takes fire and real damage, but not every bolt hits, the troopers fire soon
 * after first seeing them, and no bolt is fired without a free line to the player.</li>
 * <li>Cover: behind a wall no bolt is fired at the player while no trooper sees them.</li>
 * </ol>
 */
public class Phase33GameTest implements FabricClientGameTest {
	private static final int H = DestroyerInterior.HANGAR + 1;
	private static final int R = DestroyerInterior.REACTOR_BOTTOM;
	/** A player running (blocks a tick; sprinting is about 0.28). */
	private static final double RUN = 0.26;
	/** The text keys that name the player (the mark {@code {player}} in both languages). */
	private static final List<String> NAMED = List.of("sabercraft.story.d.m1_briefing.start", "sabercraft.story.d.m1_briefing.ready",
			"sabercraft.story.d.m5_recording.start", "sabercraft.story.d.m5_kestrel.start", "sabercraft.story.d.m6_epilogue.signature", "sabercraft.story.name.funke",
			"sabercraft.story.radio.caught.m4", "sabercraft.story.radio.m4_cut", "sabercraft.story.radio.m1_distract", "sabercraft.story.radio.focus",
			"sabercraft.story.d.after_dax.hale_trusted", "sabercraft.story.d.after_kestrel.start", "sabercraft.story.d.after_kestrel.shadow",
			"sabercraft.story.film.duel_end.dax");
	/** The new pieces of music. */
	private static final List<String> PIECES = List.of("music.story_relic", "music.story_relic_take", "music.story_reactor", "music.story_captured",
			"music.story_epilogue");

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
			this.problems.forEach(problem -> SaberCraft.LOGGER.error("PHASE33 PROBLEM: {}", problem));
			throw new AssertionError("Phase 33: " + this.problems.size() + " problem(s): " + String.join(" | ", this.problems));
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

			this.part("names", this::names);
			this.part("music", this::music);
			this.part("post", this::post);
			this.part("path", this::path);
			this.part("cover", this::cover);
		} catch (AssertionError | RuntimeException e) {
			this.problem("aborted: " + e);
			SaberCraft.LOGGER.error("PHASE33 aborted", e);
		} finally {
			if (this.server != null) {
				this.server.runOnServer(s -> {
					if (this.me(s) != null) {
						this.me(s).getAttribute(Attributes.MAX_HEALTH).setBaseValue(20.0);
					}
				});
				this.server.runCommand("gamerule natural_health_regeneration true");

				if (this.on(s -> this.session(s) != null)) {
					this.leave();
				}
			}
		}
	}

	/** Runs one part of the test (all of them, or only those named in PHASE33_ONLY, for working on one). */
	private void part(String name, Runnable part) {
		String only = System.getenv("PHASE33_ONLY");

		if (only == null || only.isBlank() || List.of(only.split(",")).contains(name)) {
			SaberCraft.LOGGER.info("PHASE33 part {}", name);
			int before = this.problems.size();
			long start = System.currentTimeMillis();
			part.run();
			SaberCraft.LOGGER.info("PHASE33 part {} done in {} s, {} problem(s)", name, (System.currentTimeMillis() - start) / 1000, this.problems.size() - before);
		}
	}

	// --- 1: the player's own name ------------------------------------------------------------------------------

	private void names() {
		String name = this.context.computeOnClient(client -> client.player.getGameProfile().name());
		check(name.equals(this.context.computeOnClient(client -> SaberLanguage.playerName())), "the name put into the texts is not the player's Minecraft name");
		String setting = this.context.computeOnClient(client -> SaberLanguage.setting());
		// "Funke" (and "Spark") as a name: not the sparks of a blade ("Funken", "sparks").
		Pattern funke = Pattern.compile("\\bFunke\\b|\\bSpark\\b");

		for (String code : List.of(SaberLanguage.ENGLISH, SaberLanguage.GERMAN, SaberLanguage.AUTO)) {
			this.context.runOnClient(client -> SaberLanguage.set(code));
			this.context.waitTick();

			for (String key : NAMED) {
				String text = this.context.computeOnClient(client -> Component.translatable(key).getString());
				check(text.contains(name) && !text.contains(SaberLanguage.PLAYER), "the text " + key + " (" + code + ") does not name the player: " + text);
			}

			String speaker = this.context.computeOnClient(client -> Component.translatable("sabercraft.story.name.funke").getString());
			check(name.equals(speaker), "the name over the player's own lines is \"" + speaker + "\" (" + code + "), not their name");
		}

		this.context.runOnClient(client -> SaberLanguage.set(setting));

		for (String lang : List.of("de_de", "en_us")) {
			String raw = this.context.computeOnClient(client -> read(client, "lang/" + lang + ".json"));

			if (raw.isEmpty()) {
				this.problem("the " + lang + " texts cannot be read");
				continue;
			}

			JsonObject texts = JsonParser.parseString(raw).getAsJsonObject();
			List<String> left = texts.keySet().stream().filter(key -> funke.matcher(texts.get(key).getAsString()).find()).toList();
			check(left.isEmpty(), "texts still call the player Funke/Spark (" + lang + "): " + left);
			long marked = texts.keySet().stream().filter(key -> texts.get(key).getAsString().contains(SaberLanguage.PLAYER)).count();
			check(marked == NAMED.size(), "the " + lang + " texts name the player in " + marked + " places, not " + NAMED.size());
			SaberCraft.LOGGER.info("PHASE33 NAMES: {} texts of {} name the player", marked, lang);
		}

		// Each client fills in its own player: two players each read their own name.
		String line = "{player}! Was immer an dieser Klinge hängt, ich spür's bis hier.";
		check(SaberLanguage.named(line, "Max").startsWith("Max! ") && SaberLanguage.named(line, "Lena").startsWith("Lena! "), "the mark is not filled with each player's name");
		check(SaberLanguage.named("Funken sprühen.", "Max").equals("Funken sprühen."), "a text without the mark is changed");

		// In the game: Kestrel's first words aboard the Falcon, and a radio call.
		this.enter(0, "m1.briefing");
		this.unseen();
		this.server.runOnServer(s -> this.session(s).talk("m1_briefing", null));
		StoryDialogPayload dialog = null;

		for (int i = 0; i < 40 && dialog == null; i++) {
			this.context.waitTick();
			StoryDialogPayload now = this.context.computeOnClient(client -> StoryClient.dialogForTest());
			dialog = now != null && now.open() ? now : null;
		}

		if (dialog == null) {
			this.problem("Kestrel's briefing does not open");
		} else {
			String key = dialog.text();
			String said = this.context.computeOnClient(client -> Component.translatable(key).getString());
			SaberCraft.LOGGER.info("PHASE33 NAMES: {} says \"{}\"", dialog.speaker(), said);
			check(said.contains(name), "Kestrel's first words do not name the player: " + said);
			this.context.waitTicks(10);
			this.context.takeScreenshot("phase33_name_dialog");
		}

		this.context.runOnClient(client -> StoryClient.lineForTest("kestrel", "sabercraft.story.radio.focus", false));
		this.context.waitTicks(10);
		StoryClient.Line radio = this.context.computeOnClient(client -> StoryClient.radioForTest());
		check(radio != null && this.context.computeOnClient(client -> Component.translatable(radio.key()).getString()).startsWith(name), "the radio call does not name the player");
		this.context.takeScreenshot("phase33_name_radio");
	}

	// --- 2: the music --------------------------------------------------------------------------------------------

	private void music() {
		for (String piece : PIECES) {
			check(this.context.computeOnClient(client -> client.getSoundManager().getSoundEvent(SaberCraft.id(piece)) != null), "the piece " + piece + " is not in sounds.json");
			String file = "sounds/music/" + piece.substring("music.".length()) + ".ogg";
			check(this.context.computeOnClient(client -> client.getResourceManager().getResource(SaberCraft.id(file)).isPresent()), "the piece " + file + " is missing");
		}

		// Mission 4 at the reactor: its own piece.
		this.enter(3, "m4.tech");
		this.unseen();
		String m4 = this.musicNow("music.story_reactor", 160);
		check("music.story_reactor".equals(m4), "at the reactor in Mission 4 the music is " + m4);

		// Down at the reactor in Mission 6, on the way to the lab: its piece too.
		this.enter(5, "m6.door");
		this.unseen();
		this.put(new Vec3(44.5, R, 37.2), 0.0F, 0.0F);
		String reactor = this.musicNow("music.story_reactor", 160);
		check("music.story_reactor".equals(reactor), "at the reactor the music is " + reactor + ", not its own piece");
		this.context.waitTicks(80);

		// Into the room of the saber: its own piece at once, crossfading (no cut, nothing left over).
		float[] before = this.context.computeOnClient(client -> StoryMusic.changesForTest());
		this.teleport(new Vec3(44.5, R, 44.0), 0.0F);
		int took = -1;
		boolean fading = false;

		for (int i = 0; i < 80; i++) {
			this.context.waitTick();

			if ("music.story_relic".equals(this.context.computeOnClient(client -> StoryMusic.playing()))) {
				took = i;
				fading = this.context.computeOnClient(client -> StoryMusic.fadingForTest()) > 0;
				break;
			}
		}

		float[] after = this.context.computeOnClient(client -> StoryMusic.changesForTest());
		SaberCraft.LOGGER.info("PHASE33 MUSIC: into the lab after {} ticks, {} change(s), fade {} ticks, old piece fading {}", took, after[0] - before[0], after[1], fading);
		check(took >= 0 && took <= 30, "stepping into the room of the saber does not bring its music within 1.5 s (" + took + ")");
		check(after[0] - before[0] == 1, "the music changes " + (after[0] - before[0]) + " times stepping into the lab");
		check(after[1] >= 30.0F, "the lab's music cuts in (" + after[1] + " ticks)");
		check(fading, "the reactor's piece stops at once instead of fading out");
		this.context.waitTicks(140);
		this.single("in the lab");
		check("music.story_relic".equals(this.context.computeOnClient(client -> StoryMusic.playing())), "the lab's piece does not hold");
		this.context.takeScreenshot("phase33_music_lab");
		// Out again and in again: the reactor's, then the lab's.
		this.teleport(new Vec3(44.5, R, 37.2), 180.0F);
		check("music.story_reactor".equals(this.musicNow("music.story_reactor", 60)), "leaving the lab the reactor's piece does not come back");
		this.teleport(new Vec3(44.5, R, 44.0), 0.0F);
		check("music.story_relic".equals(this.musicNow("music.story_relic", 60)), "back in the lab its piece does not come back");

		// Taking the saber: the film's own music, then the escape's.
		this.enter(5, "m6.saber");
		this.unseen();
		this.server.runCommand("effect give @a minecraft:resistance 100000 4 true");
		check("music.story_relic".equals(this.musicNow("music.story_relic", 80)), "before the saber the lab's piece does not play");
		this.take();
		check("saber".equals(this.film(40)), "taking the saber does not play its scene");
		String film = this.musicNow("music.story_relic_take", 40);
		check("music.story_relic_take".equals(film), "the saber's scene plays " + film + ", not its own music");
		this.context.waitTicks(40);
		this.context.takeScreenshot("phase33_music_saber_film");

		for (int i = 0; i < 260 && this.context.computeOnClient(client -> StoryCinema.active()); i++) {
			this.context.waitTick();
		}

		String escape = this.musicNow("music.story_escape", 120);
		check("music.story_escape".equals(escape), "after the saber's scene the music is " + escape + ", not the escape's");
		this.context.waitTicks(120);
		this.single("after the saber's scene");
		// In the lab once the saber is taken: the escape's, not the relic's again.
		this.teleport(new Vec3(44.5, R, 46.0), 0.0F);
		this.context.waitTicks(30);
		check("music.story_escape".equals(this.context.computeOnClient(client -> StoryMusic.playing())), "the lab's piece comes back after the saber was taken");

		// Kestrel's capture (in the hideout), the way home.
		this.enter(4, "m5.recording");
		this.unseen();
		String captured = this.musicNow("music.story_captured", 160);
		check("music.story_captured".equals(captured), "after Kestrel's capture the music in the hideout is " + captured);
		this.converse();
		this.enter(5, "m6.epilogue");
		String home = this.musicNow("music.story_epilogue", 160);
		check("music.story_epilogue".equals(home), "on the way home the music is " + home);
		this.context.waitTicks(100);
		this.single("on the way home");
	}

	/** Phase 33: after a change, once its fade is over, only one of the story's pieces is heard (and none is left on the list). */
	private void single(String where) {
		int audible = this.context.computeOnClient(client -> StoryMusic.audibleForTest());
		List<String> fading = this.context.computeOnClient(client -> StoryMusic.fadingNamesForTest());
		SaberCraft.LOGGER.info("PHASE33 MUSIC: {}: {} piece(s) heard, fading {}", where, audible, fading);
		check(audible == 1, where + ": " + audible + " of the story's pieces are heard (fading " + fading + ")");
		check(fading.isEmpty(), where + ": pieces left fading " + fading);
	}

	// --- 3: the guards at their posts --------------------------------------------------------------------------

	private void post() {
		// Before a guard: he fires soon.
		this.enter(5, "m6.escape2");
		this.server.runCommand("effect give @a minecraft:resistance 100000 4 true");
		this.context.waitTicks(30);
		Vec3 spot = this.on(s -> this.spot(s, this.session(s).figure("h4"), true));

		if (spot == null) {
			this.problem("no place before the corridor's guard to stand in");
		} else {
			int shots = this.on(s -> StoryTesting.fightShots());
			Vec3 guard = this.on(s -> this.session(s).figure("h4").position());
			this.teleport(spot, yaw(spot, guard));
			int fired = -1;

			for (int i = 0; i < 80 && fired < 0; i++) {
				this.context.waitTick();

				if (this.on(s -> StoryTesting.fightShots()) > shots) {
					fired = i;
				}
			}

			SaberCraft.LOGGER.info("PHASE33 POST: before the guard at {} blocks: first bolt after {} ticks", String.format(Locale.ROOT, "%.1f", spot.distanceTo(guard)), fired);
			check(fired >= 0 && fired <= 20, "the guard before the player fires after " + fired + " ticks (at most a second wanted)");
			check(this.on(s -> !this.session(s).figure("h4").post), "the guard who saw the player stays at his post");
		}

		// Behind a guard (a few steps off): he does not see them.
		this.enter(5, "m6.escape2");
		this.server.runCommand("effect give @a minecraft:resistance 100000 4 true");
		this.context.waitTicks(30);
		String behind = null;
		Vec3 back = null;

		for (String key : List.of("h6", "h5", "h4b", "h4")) {
			Vec3 at = this.on(s -> this.spot(s, this.session(s).figure(key), false));

			if (at != null) {
				behind = key;
				back = at;
				break;
			}
		}

		if (back == null) {
			this.problem("no place behind a guard at his post to stand in");
			return;
		}

		String key = behind;
		int shots = this.on(s -> StoryTesting.fightShots());
		Vec3 guard = this.on(s -> this.session(s).figure(key).position());
		this.teleport(back, yaw(back, guard));
		this.context.waitTicks(40);
		boolean still = this.on(s -> this.session(s).figure(key).post);
		int fired = this.on(s -> StoryTesting.fightShots()) - shots;
		SaberCraft.LOGGER.info("PHASE33 POST: behind {} at {} blocks: still at his post {}, bolts {}", key, String.format(Locale.ROOT, "%.1f", back.distanceTo(guard)), still, fired);
		check(still || fired > 0, "the guard sees the player behind him");
	}

	/**
	 * A free place before (or behind) a guard at his post, 7 to 12 blocks off, where the player can
	 * stand (floor under, room to stand) and, before him, sees him.
	 */
	private @Nullable Vec3 spot(MinecraftServer s, @Nullable Figure figure, boolean before) {
		if (figure == null || !figure.post) {
			return null;
		}

		ServerLevel level = this.level(s);
		Vec3 corner = Vec3.atLowerCornerOf(this.session(s).destroyer);
		Vec3 look = new Vec3(figure.look().x, 0.0, figure.look().z).normalize().scale(before ? 1.0 : -1.0);

		for (double d : before ? new double[] {9.0, 8.0, 10.0, 11.0, 12.0, 7.0} : new double[] {7.0, 8.0, 6.5, 9.0}) {
			Vec3 at = figure.position().add(look.scale(d));
			Vec3 world = corner.add(at);
			AABB body = new AABB(world.x - 0.3, world.y + 0.01, world.z - 0.3, world.x + 0.3, world.y + 1.8, world.z + 0.3);
			AABB floor = new AABB(world.x - 0.3, world.y - 0.3, world.z - 0.3, world.x + 0.3, world.y - 0.01, world.z + 0.3);

			if (!level.noCollision(body) || level.noCollision(floor)) {
				continue;
			}

			boolean seen = StoryTesting.clear(level, corner.add(figure.eye()), world.add(0.0, 1.0, 0.0));

			if (!before || seen) {
				return at;
			}
		}

		return null;
	}

	// --- 4: the way from the saber to the hangar -----------------------------------------------------------------

	/** What happened on the way (counted by the test, tick by tick). */
	private static final class Run {
		int ticks;
		int shots;
		int hits;
		float damage;
		int firstSeen = -1;
		int firstBolt = -1;
		int bolts;
		int blindBolts;
		final List<String> blocked = new ArrayList<>();
		final List<String> sources = new ArrayList<>();
		final Set<Integer> known = new HashSet<>();
		float health;
		boolean seenBefore;
		@Nullable Vec3 lastChest;
		String step = "";
		int restores;
		/** Blaster damage only (a scrape on the lab's furniture while the test runs through it does not count). */
		float blaster;
		/** Bolts in flight: where each was last, and how far from the player it was fired. */
		final java.util.Map<Integer, Vec3> flying = new java.util.HashMap<>();
		final java.util.Map<Integer, Double> firedFrom = new java.util.HashMap<>();
		/** Bolts fired from near (under {@link #FAR} blocks) and far, and how many of them hit. */
		int near;
		int nearHits;
		int far;
		int farHits;
	}

	/** Bolts fired from this far (blocks) or further count as far ones. */
	private static final double FAR = 8.0;

	/** One tick of the server as the test sees it. */
	private record Seen(float health, int shots, boolean seen, int fresh, List<String> blocked, String step, @Nullable String source, int restores, Vec3 chest,
			java.util.Map<Integer, Vec3> flying, java.util.Map<Integer, Double> fired) {
	}

	private void path() {
		this.server.runCommand("gamerule natural_health_regeneration false");
		this.enter(5, "m6.escape1");
		this.settle();
		this.server.runCommand("effect clear @a");
		// (Enough health to see the whole way through; what it costs is counted, a player has 20.)
		this.server.runOnServer(s -> {
			this.me(s).getAttribute(Attributes.MAX_HEALTH).setBaseValue(200.0);
			this.me(s).setHealth(200.0F);
		});
		this.context.waitTick();
		int heldBack = this.on(s -> StoryTesting.heldBack());
		int misses = this.on(s -> StoryTesting.misses());
		SaberCraft.LOGGER.info("PHASE33 PATH: health {} of {}", this.on(s -> this.me(s).getHealth()), this.on(s -> this.me(s).getMaxHealth()));
		// Out of the lab through its door (it opens as the player comes), then the way round the shaft to the lift.
		// (Round the tracker in the lab's middle, from the saber's field to the door.)
		List<Vec3> down = this.route(new Vec3(41.5, R, 48.5), new Vec3(41.5, R, 42.5), new Vec3(44.0, R, 41.0), new Vec3(44.0, R, 36.0), new Vec3(31.0, R, 25.0));

		if (down.isEmpty()) {
			this.problem("no way from the lab to the reactor's lift");
			return;
		}

		Run run = new Run();
		this.observe(run);
		// To the lab's door, a moment there while it opens, then on.
		int door = 0;

		while (door < down.size() - 1 && down.get(door).distanceTo(new Vec3(44.0, R, 41.0)) > 0.3) {
			door++;
		}

		this.walk(run, down.subList(0, door + 1), "m6.escape1");

		for (int i = 0; i < 12; i++) {
			this.context.waitTick();
			this.observe(run);
		}

		this.walk(run, down.subList(door, down.size()), "m6.escape1");
		SaberCraft.LOGGER.info("PHASE33 PATH: at the lift after {} ticks: {} bolts, {} hits, {} damage ({} by bolts)", run.ticks, run.shots, run.hits, run.damage, run.blaster);
		float toLift = run.blaster;
		// On the lift's pad: standing still a moment takes one up (under fire meanwhile).
		Vec3 pad = down.get(down.size() - 1);
		this.walk(run, List.of(pad, new Vec3(31.0, R, 25.0)), "m6.escape1");
		int waited = 0;

		for (; waited < 100 && "m6.escape1".equals(run.step) && run.restores == 0; waited++) {
			this.context.waitTick();
			this.observe(run);
			// A hit's push off the pad: back onto it, as a player would step back.
			this.server.runOnServer(s -> {
				Vec3 local = this.me(s).position().subtract(Vec3.atLowerCornerOf(this.session(s).destroyer));

				if (local.y < H - 1.0 && Math.hypot(local.x - 31.0, local.z - 25.0) > 0.6) {
					this.teleport(s, this.session(s), new Vec3(31.0, R, 25.0), 0.0F);
				}
			});
		}

		SaberCraft.LOGGER.info("PHASE33 PATH: the lift took the player up after {} ticks on its pad ({} hits so far)", waited, run.hits);
		check(waited <= 50, "the lift does not take the player up under fire (" + waited + " ticks on its pad)");
		this.waitStep("m6.escape2", 40);
		// (Into the hangar, past its door's line: the duel begins clear of the door, 1.2 blocks in.)
		List<Vec3> hangar = this.route(new Vec3(28.8, H, 25.0), new Vec3(40.5, H, -19.0));

		if (hangar.isEmpty()) {
			hangar = this.route(new Vec3(28.8, H, 25.0), new Vec3(47.5, H, 14.0), new Vec3(40.5, H, -19.0));
		}

		if (hangar.isEmpty()) {
			this.problem("no way from the lift to the hangar");
			return;
		}

		int before = run.hits;
		this.walk(run, hangar, "m6.escape2");

		for (int i = 0; i < 60 && !"m6.duel".equals(run.step); i++) {
			this.context.waitTick();
			this.observe(run);
		}

		float rate = run.shots == 0 ? 0.0F : run.hits / (float) run.shots;
		int held = this.on(s -> StoryTesting.heldBack()) - heldBack;
		SaberCraft.LOGGER.info("PHASE33 PATH: {} ticks to the hangar ({}), {} bolts, {} hits ({} on the hangar deck), {} damage, {}% hit, first seen at {}, first bolt at {}, "
				+ "{} held back (blocked muzzle), damage by {}", run.ticks, run.step, run.shots, run.hits, run.hits - before, run.damage, Math.round(rate * 100.0F), run.firstSeen,
				run.firstBolt, held, run.sources.stream().distinct().toList());
		check("m6.duel".equals(run.step), "the run does not reach the hangar (at " + run.step + ")");
		check(run.restores == 0, "the player is caught on the way");
		check(run.shots >= 8, "the troopers hardly fire on the way (" + run.shots + " bolts)");
		int missed = this.on(s -> StoryTesting.misses()) - misses;
		SaberCraft.LOGGER.info("PHASE33 PATH: by bolts {} damage ({} to the lift), near bolts {} with {} hits, far bolts (from {} blocks) {} with {} hits, {} meant to miss",
				run.blaster, toLift, run.near, run.nearHits, FAR, run.far, run.farHits, missed);
		// The bounds leave room for chance: four runs in a row cost 9 to 39 health by bolts (4 to 14
		// hits of about 22 bolts); these checks fail only on runs far off that.
		check(run.hits >= 2 && run.blaster >= 6.0F, "the way is not dangerous: " + run.hits + " hits, " + run.blaster + " damage");
		// Running from the lab to the lift without stopping, without the saber's guard: not caught there.
		check(toLift < 20.0F, "the way from the lab to the lift costs " + toLift + " (a full health bar is 20)");
		check(rate <= 0.7F, "the troopers hit almost every time (" + run.hits + " of " + run.shots + ")");
		// (About a quarter of them on average, more while the player waits on the lift; up to seven in
		// ten counts as chance.)
		check(run.far < 4 || run.farHits * 10 <= run.far * 7, "from afar the troopers hit too often (" + run.farHits + " of " + run.far + ")");
		check(run.firstSeen >= 0 && run.firstBolt >= 0 && run.firstBolt - run.firstSeen <= 30, "the first bolt comes " + (run.firstBolt - run.firstSeen) + " ticks after the player is first seen");
		check(run.blocked.isEmpty(), "bolts fired without a free line to the player: " + run.blocked.subList(0, Math.min(run.blocked.size(), 5)));
		check(run.blindBolts == 0, run.blindBolts + " bolts fired while no trooper saw the player");
		this.server.runOnServer(s -> this.me(s).getAttribute(Attributes.MAX_HEALTH).setBaseValue(20.0));
		this.server.runCommand("gamerule natural_health_regeneration true");
	}

	/** Lets a film or a dialog at a step's beginning pass (skipped as the player would). */
	private void settle() {
		for (int i = 0; i < 20; i++) {
			String film = this.context.computeOnClient(client -> StoryCinema.filmForTest());

			if (film != null) {
				this.skip(film);
			} else if (this.on(s -> this.session(s) != null && this.session(s).dialog != null)) {
				this.server.runOnServer(s -> StoryTesting.answer(this.session(s), 0));
			} else {
				return;
			}

			this.context.waitTicks(2);
		}
	}

	/** The way someone walks from one place to another (plot space), without its last marker; empty when it does not get there. */
	private List<Vec3> way(Vec3 from, Vec3 to) {
		List<Vec3> way = this.on(s -> StoryTesting.way(this.session(s), from, to));

		if (way.size() < 2 || way.get(way.size() - 1).x != 1.0) {
			SaberCraft.LOGGER.info("PHASE33 WAY: none from {} to {} ({})", from, to, way);
			return List.of();
		}

		List<Vec3> points = new ArrayList<>(way.subList(0, way.size() - 1));
		points.add(0, from);
		SaberCraft.LOGGER.info("PHASE33 WAY: {} points from {} to {}", points.size(), from, to);
		return points;
	}

	/**
	 * A way through these stops: the first two legs straight (through the lab's door, which opens as
	 * the player comes), the others as someone walks them. Empty when a leg does not get there.
	 */
	private List<Vec3> route(Vec3... stops) {
		List<Vec3> points = new ArrayList<>();
		points.add(stops[0]);

		for (int i = 1; i < stops.length; i++) {
			if (stops.length > 3 && i < stops.length - 1) {
				points.add(stops[i]);
				continue;
			}

			List<Vec3> leg = this.way(stops[i - 1], stops[i]);

			if (leg.isEmpty()) {
				return List.of();
			}

			points.addAll(leg.subList(1, leg.size()));
		}

		return points;
	}

	private void walk(Run run, List<Vec3> points, String step) {
		this.walk(run, points, step, RUN);
	}

	/** Runs along the points, a step a tick, looking where one goes; stops when the step is over (or the player is caught). */
	private void walk(Run run, List<Vec3> points, String step, double speed) {
		Vec3 at = points.get(0);

		for (int k = 1; k < points.size(); k++) {
			Vec3 to = points.get(k);

			while (at.distanceTo(to) > 1.0E-3) {
				Vec3 d = to.subtract(at);
				double length = d.length();
				at = length <= speed ? to : at.add(d.scale(speed / length));
				Vec3 now = at;
				float yaw = Math.hypot(d.x, d.z) > 1.0E-3 ? yaw(at, to) : 0.0F;
				this.server.runOnServer(s -> this.teleport(s, this.session(s), now, yaw));
				this.context.waitTick();
				this.observe(run);

				if (!step.equals(run.step) || run.restores > 0) {
					return;
				}
			}
		}
	}

	/** Counts what this tick brought: bolts (each checked for a free line from where it was fired), hits, who sees the player. */
	private void observe(Run run) {
		Vec3 last = run.lastChest;
		Seen seen = this.on(s -> {
			ServerPlayer player = this.me(s);
			StorySession session = this.session(s);
			ServerLevel level = this.level(s);
			Vec3 corner = Vec3.atLowerCornerOf(session.destroyer);
			Vec3 chest = player.position().add(0.0, 1.1, 0.0);
			boolean sees = false;

			for (Figure figure : session.figures) {
				if (figure.hostile && !figure.saber && figure.mode != Figure.DOWN && Story.DESTROYER.equals(figure.ship)) {
					Vec3 eye = corner.add(figure.eye());

					if (eye.distanceTo(chest) < 26.0 && StoryTesting.clear(level, eye, chest)) {
						sees = true;
					}
				}
			}

			int fresh = 0;
			List<String> blocked = new ArrayList<>();
			java.util.Map<Integer, Vec3> flying = new java.util.HashMap<>();
			java.util.Map<Integer, Double> fired = new java.util.HashMap<>();

			for (BlasterBolt bolt : level.getEntitiesOfClass(BlasterBolt.class, player.getBoundingBox().inflate(40.0), b -> b.getOwner() == null)) {
				flying.put(bolt.getId(), bolt.position());

				if (run.known.add(bolt.getId())) {
					fresh++;
					Vec3 from = bolt.position().subtract(bolt.getDeltaMovement().scale(Math.max(0, bolt.tickCount)));
					fired.put(bolt.getId(), from.distanceTo(chest));

					if (!StoryTesting.clear(level, from, chest) && (last == null || !StoryTesting.clear(level, from, last))) {
						Vec3 plot = from.subtract(corner);
						blocked.add(String.format(Locale.ROOT, "%.1f,%.1f,%.1f", plot.x, plot.y, plot.z));
					}
				}
			}

			DamageSource source = player.getLastDamageSource();
			return new Seen(player.getHealth(), StoryTesting.fightShots(), sees, fresh, blocked, StoryTesting.step(session), source != null ? source.getMsgId() : null,
					StoryTesting.restores(session), chest, flying, fired);
		});

		if (run.ticks == 0 && run.health == 0.0F) {
			run.health = seen.health();
			run.shots = 0;
			run.known.clear();
			run.seenBefore = seen.seen();
			run.lastChest = seen.chest();
			run.step = seen.step();
			this.baseShots = seen.shots();
			this.baseRestores = seen.restores();
			return;
		}

		run.ticks++;
		boolean hurt = seen.health() < run.health - 0.01F;
		boolean byBolt = hurt && "sabercraft.blaster".equals(seen.source());
		// Which bolt hit: of those gone since the last tick, the one that was nearest the player.
		Integer hitBy = null;
		double nearest = 3.0;

		for (java.util.Map.Entry<Integer, Vec3> entry : run.flying.entrySet()) {
			if (!seen.flying().containsKey(entry.getKey())) {
				double d = entry.getValue().distanceTo(seen.chest());

				if (byBolt && d < nearest) {
					nearest = d;
					hitBy = entry.getKey();
				}
			}
		}

		for (java.util.Map.Entry<Integer, Double> entry : seen.fired().entrySet()) {
			run.firedFrom.put(entry.getKey(), entry.getValue());

			if (entry.getValue() >= FAR) {
				run.far++;
			} else {
				run.near++;
			}
		}

		if (hitBy != null && run.firedFrom.containsKey(hitBy)) {
			if (run.firedFrom.get(hitBy) >= FAR) {
				run.farHits++;
			} else {
				run.nearHits++;
			}
		}

		run.flying.clear();
		run.flying.putAll(seen.flying());

		if (hurt) {
			run.hits++;
			run.damage += run.health - seen.health();

			if (byBolt) {
				run.blaster += run.health - seen.health();
			}

			SaberCraft.LOGGER.info("PHASE33 HIT: tick {} {} by {} at {} (step {}){}", run.ticks, String.format(Locale.ROOT, "%.1f", run.health - seen.health()), seen.source(),
					seen.chest(), seen.step(), hitBy != null && run.firedFrom.containsKey(hitBy) ? String.format(Locale.ROOT, ", fired from %.1f blocks", run.firedFrom.get(hitBy)) : "");

			if (seen.source() != null) {
				run.sources.add(seen.source());
			}
		}

		run.health = seen.health();
		int shots = seen.shots() - this.baseShots;

		if (shots > run.shots && run.firstBolt < 0) {
			run.firstBolt = run.ticks;
		}

		if (shots > run.shots && !run.seenBefore && !seen.seen()) {
			run.blindBolts += shots - run.shots;
		}

		run.shots = shots;
		run.bolts += seen.fresh();
		run.blocked.addAll(seen.blocked());

		if (seen.seen() && run.firstSeen < 0) {
			run.firstSeen = run.ticks;
		}

		run.seenBefore = seen.seen();
		run.lastChest = seen.chest();
		run.step = seen.step();
		run.restores = seen.restores() - this.baseRestores;
	}

	private int baseShots;
	private int baseRestores;

	// --- 5: behind cover -----------------------------------------------------------------------------------------

	private void cover() {
		this.enter(5, "m6.escape1");
		this.settle();
		this.server.runCommand("effect give @a minecraft:resistance 100000 4 true");
		// Out of the lab into the passage before the shaft: the troopers there see the player and fire.
		this.teleport(new Vec3(44.0, R, 37.0), 180.0F);
		Run open = new Run();
		this.observe(open);

		for (int i = 0; i < 80 && open.firstBolt < 0; i++) {
			this.context.waitTick();
			this.observe(open);
		}

		SaberCraft.LOGGER.info("PHASE33 COVER: in the passage seen after {} ticks, first bolt after {}", open.firstSeen, open.firstBolt);
		check(open.firstBolt >= 0, "nobody fires at the player in the passage before the shaft");
		check(open.firstSeen >= 0 && open.firstBolt - open.firstSeen <= 20, "the first bolt comes " + (open.firstBolt - open.firstSeen) + " ticks after being seen");
		// Back into the lab, beside its door: behind its wall.
		this.teleport(new Vec3(39.5, R, 42.0), 0.0F);
		Run hidden = new Run();
		hidden.known.addAll(open.known);
		this.observe(hidden);
		hidden.known.addAll(open.known);
		int unseen = 0;

		for (int i = 0; i < 200; i++) {
			this.context.waitTick();
			this.observe(hidden);

			if (hidden.firstSeen < 0) {
				unseen++;
			}
		}

		SaberCraft.LOGGER.info("PHASE33 COVER: behind the lab's wall unseen for {} ticks; then {} bolts ({} after being seen again: first after {} ticks), {} hits; {} fired blind, {} without a free line",
				unseen, hidden.shots, hidden.firstSeen >= 0 ? "seen" : "never", hidden.firstSeen >= 0 && hidden.firstBolt >= 0 ? hidden.firstBolt - hidden.firstSeen : -1, hidden.hits,
				hidden.blindBolts, hidden.blocked.size());
		check(unseen >= 10, "behind the lab's wall the player is seen at once (after " + unseen + " ticks)");
		check(hidden.firstBolt < 0 || hidden.firstSeen >= 0 && hidden.firstBolt > hidden.firstSeen, "a bolt is fired at the player behind the wall before any trooper sees them");
		check(hidden.firstBolt < 0 || hidden.firstBolt - hidden.firstSeen <= 20, "round the corner the first bolt comes " + (hidden.firstBolt - hidden.firstSeen) + " ticks after being seen");
		check(hidden.blindBolts == 0, hidden.blindBolts + " bolts fired at the player behind cover while no trooper saw them");
		check(hidden.blocked.isEmpty() && open.blocked.isEmpty(), "bolts fired through a wall: " + hidden.blocked + open.blocked);
	}

	// --- Helpers -----------------------------------------------------------------------------------------------

	/** "Take it" in the saber's talk (as the player answers). */
	private void take() {
		this.server.runOnServer(s -> this.session(s).talk("m6_saber", null));
		this.context.waitTicks(5);
		List<String> answers = this.on(s -> StoryTesting.answers(this.session(s)));

		if (!answers.contains("take")) {
			this.problem("the saber's talk has no answer to take it (" + answers + ")");
			return;
		}

		this.server.runOnServer(s -> StoryTesting.answer(this.session(s), answers.indexOf("take")));
		this.context.waitTicks(2);
	}

	/** The story's piece once it is that one (waiting at most that many ticks), else the one playing then. */
	private @Nullable String musicNow(String piece, int ticks) {
		String playing = null;

		for (int i = 0; i < ticks; i += 5) {
			playing = this.context.computeOnClient(client -> StoryMusic.playing());

			if (piece.equals(playing)) {
				return playing;
			}

			this.context.waitTicks(5);
		}

		SaberCraft.LOGGER.info("PHASE33 MUSIC: wanted {}, playing {} (the story chose {})", piece, playing, this.on(s -> this.session(s) == null ? "-" : StoryTesting.music(this.session(s))));
		return playing;
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

	/** Nobody sees the player (the test goes where it likes). */
	private void unseen() {
		this.server.runOnServer(s -> StoryTesting.unseen(this.session(s), 100000));
	}

	/** Puts the player at a place of the Star Destroyer of the story (plot space), and waits a moment. */
	private void put(Vec3 local, float yaw, float pitch) {
		BlockPos origin = this.on(s -> this.session(s).destroyer);
		this.server.runCommand(String.format(Locale.ROOT, "execute in sabercraft:ship_interiors run tp @a %.3f %.3f %.3f %.1f %.1f", origin.getX() + local.x,
				origin.getY() + local.y, origin.getZ() + local.z, yaw, pitch));
		this.context.waitTicks(8);
	}

	/** Puts the player there at once (plot space). */
	private void teleport(Vec3 local, float yaw) {
		this.server.runOnServer(s -> this.teleport(s, this.session(s), local, yaw));
	}

	private void teleport(MinecraftServer server, StorySession session, Vec3 local, float yaw) {
		ServerPlayer player = this.me(server);
		Vec3 world = local.add(Vec3.atLowerCornerOf(session.destroyer));
		player.teleportTo(server.getLevel(ShipInteriors.DIMENSION), world.x, world.y, world.z, Set.of(), yaw, 0.0F, true);
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
		SaberCraft.LOGGER.error("PHASE33 CHECK FAILED: {}", problem);
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
