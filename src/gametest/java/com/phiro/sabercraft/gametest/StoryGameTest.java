package com.phiro.sabercraft.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

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
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.client.droid.DroidControl;
import com.phiro.sabercraft.client.lang.SaberLanguage;
import com.phiro.sabercraft.client.menu.StoryMenuScreen;
import com.phiro.sabercraft.client.music.StoryMusic;
import com.phiro.sabercraft.client.story.DialogScreen;
import com.phiro.sabercraft.client.story.StoryClient;
import com.phiro.sabercraft.client.story.StoryInput;
import com.phiro.sabercraft.droid.DroidType;
import com.phiro.sabercraft.droid.Droids;
import com.phiro.sabercraft.interior.Crew;
import com.phiro.sabercraft.interior.DestroyerInterior;
import com.phiro.sabercraft.interior.FalconInterior;
import com.phiro.sabercraft.interior.InteriorPlan;
import com.phiro.sabercraft.interior.ShipInteriors;
import com.phiro.sabercraft.network.StoryActionPayload;
import com.phiro.sabercraft.network.StoryStatePayload;
import com.phiro.sabercraft.saber.SaberVariant;
import com.phiro.sabercraft.story.Figure;
import com.phiro.sabercraft.story.Story;
import com.phiro.sabercraft.story.StorySession;
import com.phiro.sabercraft.story.StoryTesting;

/**
 * Phase 19: the story mode, played through as a player would: into the story from its menu, every
 * mission and step with real right clicks on people and things, real key presses for the dialogs
 * and the droids, the saber's strokes in the finale, to the end and out again (one's own things
 * back, the reward). Then Mission 3 again with the other choice about Hale (the way through the
 * crawl way in Mission 5). Between them: both languages, the dark, the armour, being seen and
 * caught (back to the checkpoint), leaving and continuing.
 *
 * <p>Walking from place to place is done by teleporting (the ships' ways are tested elsewhere);
 * while it walks the story through, the test keeps the player unseen (as just after a capture),
 * except where seeing is what is tested.
 *
 * <p>Screenshots: {@code story_*}; short films as numbered frames {@code film_<name>_<n>}.
 */
public class StoryGameTest implements FabricClientGameTest {
	private static final int H = DestroyerInterior.HANGAR + 1;
	private static final int R = DestroyerInterior.REACTOR_BOTTOM;
	private static final int FF = FalconInterior.FLOOR + 1;
	private static final double REACH = 4.3;

	private ClientGameTestContext context;
	private TestServerContext server;
	private UUID self;
	/** What went wrong without stopping the run (checked at the end). */
	private final List<String> problems = new ArrayList<>();
	/** Every dialog line seen, in order ({@code m1_briefing.situation}). */
	private final List<String> lines = new ArrayList<>();

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
			context.waitTicks(100);
			this.server = singleplayer.getServer();
			this.server.runCommand("gamerule advance_time false");
			this.server.runCommand("gamerule spawn_mobs false");
			this.server.runCommand("time set day");
			this.server.runCommand("gamemode survival @a");
			this.server.runCommand("kill @e[type=!player]");
			this.server.runCommand("clear @a");
			this.server.runCommand("give @a minecraft:diamond 5");
			this.server.runCommand("give @a minecraft:bread 3");
			context.waitTicks(20);
			this.self = context.computeOnClient(client -> client.player.getUUID());
			Vec3 home = this.server.computeOnServer(s -> me(s).position());

			languages();
			this.playThrough(home);
			this.otherWay();
		} finally {
			context.runOnClient(client -> {
				SaberLanguage.set(SaberLanguage.AUTO);
				client.options.setCameraType(CameraType.FIRST_PERSON);
			});
		}

		if (!this.problems.isEmpty()) {
			this.problems.forEach(problem -> SaberCraft.LOGGER.error("STORY PROBLEM: {}", problem));
			throw new AssertionError("Story: " + this.problems.size() + " problem(s): " + String.join(" | ", this.problems));
		}
	}

	// --- Languages -------------------------------------------------------------------------------

	/** Every text in German and English: the mod's files alike, every dialog line and answer there. */
	private void languages() {
		List<String> missing = this.context.computeOnClient(client -> SaberLanguage.missingForTest());
		check(missing.isEmpty(), "texts only in one language: " + missing.stream().limit(10).toList());
		List<String> keys = new ArrayList<>(StoryTesting.dialogKeys());
		keys.addAll(StoryTesting.otherKeys());
		List<String> absent = this.context.computeOnClient(client -> keys.stream()
				.filter(key -> !SaberLanguage.hasForTest(SaberLanguage.GERMAN, key) || !SaberLanguage.hasForTest(SaberLanguage.ENGLISH, key)).distinct().toList());
		check(absent.isEmpty(), "story keys without a text: " + absent.stream().limit(12).toList());
		SaberCraft.LOGGER.info("STORY: {} dialog and story keys in both languages", keys.size());
	}

	// --- The whole chapter -----------------------------------------------------------------------

	private void playThrough(Vec3 home) {
		ClientGameTestContext context = this.context;
		context.runOnClient(client -> SaberLanguage.set(SaberLanguage.GERMAN));

		// The menu: no story yet; "Start" puts one into it.
		context.setScreen(StoryMenuScreen::new);
		context.waitTicks(20);
		context.takeScreenshot("story_menu_start");

		if (!context.tryClickScreenButton("sabercraft.menu.story.start")) {
			this.problem("the story menu has no start button to click");
			context.runOnClient(client -> StoryClient.send(StoryActionPayload.CONTINUE, "", 0));
		}

		context.waitTicks(40);
		this.waitStep("m1.briefing", 100);
		this.server.runOnServer(s -> {
			ServerPlayer player = me(s);
			check(player.level().dimension().equals(ShipInteriors.DIMENSION), "not in the ships after entering the story");
			check(player.gameMode.getGameModeForPlayer() == GameType.ADVENTURE, "not in adventure mode in the story");
			check(player.getInventory().countItem(Items.DIAMOND) == 0 && player.getInventory().countItem(Items.BREAD) == 0, "own things not put away");
		});
		boolean active = context.computeOnClient(client -> StoryClient.active() && StoryClient.figuresForTest() > 0);
		check(active, "the client does not know the story is on (or sees nobody)");
		context.waitTicks(30);
		this.checkMusic("music.story_hideout", "the Falcon");
		context.takeScreenshot("story_m1_falcon_title");
		this.unseen();

		// --- Mission 1 -----------------------------------------------------------------------------
		this.talkTo("mira", true);
		context.waitFor(client -> client.gui.screen() instanceof DialogScreen, 40);
		this.film("dialog", 24, 2);
		context.runOnClient(client -> {
			if (client.gui.screen() instanceof DialogScreen dialog) {
				dialog.finishForTest();
			}
		});
		context.waitTicks(4);
		context.takeScreenshot("story_dialog_de");
		String german = context.computeOnClient(client -> client.gui.screen() instanceof DialogScreen dialog ? dialog.wordsForTest() : "");
		context.runOnClient(client -> SaberLanguage.set(SaberLanguage.ENGLISH));
		context.waitTicks(4);
		context.runOnClient(client -> {
			if (client.gui.screen() instanceof DialogScreen dialog) {
				dialog.finishForTest();
			}
		});
		context.waitTicks(4);
		context.takeScreenshot("story_dialog_en");
		String english = context.computeOnClient(client -> client.gui.screen() instanceof DialogScreen dialog ? dialog.wordsForTest() : "");
		check(!german.isEmpty() && !english.isEmpty() && !german.equals(english), "the dialog is not in both languages: '" + german + "' / '" + english + "'");
		context.runOnClient(client -> SaberLanguage.set(SaberLanguage.GERMAN));
		context.waitTicks(4);
		// Real keys: on to the next line (Enter), then the first answer (1).
		this.finishTyping();
		context.getInput().pressKey(GLFW.GLFW_KEY_ENTER);
		context.waitTicks(6);
		this.finishTyping();
		context.takeScreenshot("story_dialog_answers");
		context.getInput().pressKey(GLFW.GLFW_KEY_1);
		context.waitTicks(6);
		String line = this.line();
		check(line.equals("m1_briefing.nightshade"), "the keys did not choose the first answer (now " + line + ")");
		this.converse("ready");
		this.waitStep("m1.cockpit", 40);

		this.talkTo("dax", true);
		this.converse("joke", "go");
		this.waitStep("m1.ramp", 900);
		this.converse();
		this.exitShip("falcon", new Vec3(35.5, 4.0, 40.5));
		this.waitStep("m1.droid", 80);
		check(this.on(s -> Story.DESTROYER.equals(session(s).here)), "the Falcon's ramp did not lead into the Star Destroyer");
		context.waitTicks(30);
		this.checkMusic("music.story_stealth", "the maintenance room");
		this.lampPicture();
		this.darkness();
		this.use("pad", H);
		this.converse();
		this.use("sign", H);
		this.converse();
		this.talkTo("m31", true);
		this.converse("free");
		this.waitStep("m1.hatch", 40);
		check(this.on(s -> Droids.crew(me(s)).contains(DroidType.MOUSE)), "M-31 does not come along");
		this.place(Story.DESTROYER, new Vec3(20.5, H, -4.5), 90.0F, 0.0F);
		this.waitStep("m2.kestrel", 60);
		context.waitTicks(30);
		this.checkMusic("music.story_hideout", "the hidden room");
		context.takeScreenshot("story_hideout");

		// --- Mission 2 -----------------------------------------------------------------------------
		this.talkTo("kestrel", true);
		this.converse("nightshade", "task");
		this.waitStep("m2.terminal", 40);
		this.use("terminal", H);
		this.converse("map", "off");
		check(this.on(s -> session(s).journal.contains("terminal_map")), "the terminal's map is not in the journal");
		this.seenAndCaught();
		this.use("terminal", H);
		this.converse("drop", "off");
		this.waitStep("m2.drop", 40);
		this.eavesdrop();
		this.distraction();
		this.use("drop", H);
		this.converse();
		this.waitStep("m2.return", 40);
		this.leaveAndContinue(home);
		this.talkTo("kestrel", true);
		this.converse("howmany");
		this.waitStep("m3.plan", 40);

		// --- Mission 3 -----------------------------------------------------------------------------
		this.talkTo("kestrel", true);
		this.converse("caught", "go");
		this.waitStep("m3.console", 40);
		BlockPos armory = new BlockPos(DestroyerInterior.CX + 4, H, -14);
		check(this.on(s -> Story.locked(session(s).destroyer, armory.getX(), armory.getY(), armory.getZ())), "the armory is open before the console");
		this.use("security", H);
		this.converse();
		this.waitStep("m3.armour", 40);
		check(!this.on(s -> Story.locked(session(s).destroyer, armory.getX(), armory.getY(), armory.getZ())), "the armory stays locked after the console");
		this.doorOpens(armory, new Vec3(armory.getX() + 0.5, H, armory.getZ() + 2.5), "the armory");
		this.use("locker", H);
		this.converse();
		this.waitStep("m3.card", 40);
		check(this.on(s -> session(s).disguised), "the armour does not disguise");
		this.use("locker2", H);
		this.converse();
		this.disguise();
		this.use("card", H);
		this.converse();
		this.waitStep("m3.hale", 40);
		this.waitDialog(400, "Hale does not come to speak");
		this.lookAt("hale");
		this.finishTyping();
		context.waitTicks(2);
		context.takeScreenshot("story_hale");
		this.converse("knows", "why", "trust");
		this.waitStep("m3.return", 400);
		check(this.on(s -> session(s).has("trust.hale") && session(s).has("know.cellcode")), "trusting Hale is not remembered");
		this.place(Story.DESTROYER, new Vec3(20.5, H, -4.5), 90.0F, 0.0F);
		this.waitStep("m4.brief", 60);

		// --- Mission 4 -----------------------------------------------------------------------------
		this.talkTo("kestrel", true);
		this.converse("questions", "go");
		check(this.lines.contains("m4_kestrel.start") && this.lines.contains("m4_kestrel.trusted"), "Kestrel's first line or the trusted line was skipped");
		this.waitStep("m4.checkpoint", 40);
		this.talkTo("checkpoint", true);
		this.converse("card");
		this.waitStep("m4.lift", 40);
		this.place(Story.DESTROYER, new Vec3(28.8, R, 25.0), 90.0F, 0.0F);
		this.waitStep("m4.tech", 60);
		this.talkTo("tech", true);
		this.converse("sick", "password");
		this.waitStep("m4.download", 40);
		this.use("coolant", R);
		this.converse();
		check(this.on(s -> session(s).has("opt.coolant")), "the coolant valve was not turned");
		this.place(Story.DESTROYER, new Vec3(50.6, R, 24.5), 270.0F, 20.0F);
		this.useHere("console");
		this.converse();
		this.waitStep("m5.escape", 300);

		// --- Mission 5 -----------------------------------------------------------------------------
		context.waitTicks(30);
		check(this.on(s -> session(s).alarm > 0), "no alarm after the download");
		this.checkMusic("music.story_alarm", "the alarm");
		this.place(Story.DESTROYER, new Vec3(40.5, H, 3.0), 180.0F, 0.0F);
		context.waitTicks(30);
		context.takeScreenshot("story_alarm");
		this.place(Story.DESTROYER, new Vec3(20.5, H, -4.5), 90.0F, 0.0F);
		this.waitStep("m5.recording", 60);
		this.use("recorder", H);
		this.converse();
		this.waitStep("m5.route", 40);
		this.place(Story.DESTROYER, new Vec3(31.5, H, 6.5), 180.0F, 0.0F);
		this.waitStep("m5.cell", 60);
		this.waitDialog(60, "Hale's trap does not spring");
		this.converse();
		context.waitTicks(10);
		check(this.on(s -> session(s).has("tmp.trapped")), "the trap did not put the player in the cell");
		check(this.on(s -> s.getLevel(ShipInteriors.DIMENSION).getBlockState(session(s).destroyer.offset(31, H, DestroyerInterior.CELL_FRONT)).is(Blocks.BARRIER)),
				"cell 2 has no force field");
		// After the fade: looking out through the force field (the cells' fronts face south).
		context.runOnClient(client -> {
			client.player.setYRot(0.0F);
			client.player.setXRot(5.0F);
		});
		context.waitTicks(90);
		context.takeScreenshot("story_cell");
		this.droid(DroidType.MOUSE, s -> session(s).has("cell2.open"), "M-31 does not switch the force field off");
		check(this.on(s -> s.getLevel(ShipInteriors.DIMENSION).getBlockState(session(s).destroyer.offset(31, H, DestroyerInterior.CELL_FRONT)).isAir()),
				"cell 2's force field is still there");
		this.place(Story.DESTROYER, new Vec3(31.5, H, 4.0), 180.0F, 0.0F);
		this.waitStep("m5.r2", 40);
		this.talkTo("r2v7", true);
		this.converse();
		this.waitStep("m5.cell1", 40);
		check(this.on(s -> Droids.crew(me(s)).contains(DroidType.R2D2)), "R2-V7 does not come along");
		this.place(Story.DESTROYER, new Vec3(35.0, H, 4.6), 180.0F, 10.0F);
		this.droid(DroidType.R2D2, s -> session(s).has("cell3.open"), "R2-V7 does not open the prisoner's cell");
		this.talkTo("prisoner", true);
		this.converse();
		this.place(Story.DESTROYER, new Vec3(27.5, H, 4.6), 180.0F, 10.0F);
		this.droid(DroidType.R2D2, s -> session(s).has("cell1.open"), "R2-V7 does not open Kestrel's cell");
		this.talkTo("kestrel", true);
		this.converse();
		check(this.lines.contains("m5_kestrel.start") && this.lines.contains("m5_kestrel.trusted"), "Kestrel's lines in the cell were skipped");
		this.waitStep("m5.back", 40);
		this.place(Story.DESTROYER, new Vec3(20.5, H, -4.5), 90.0F, 0.0F);
		this.waitStep("m6.plan", 60);

		// --- Mission 6 -----------------------------------------------------------------------------
		// Kestrel came through the crawl way behind the player: in the hideout, on her cot (she was
		// once left standing by the detention block).
		for (int i = 0; i < 40 && this.on(s -> session(s).figure("kestrel").mode != Figure.ROUTINE); i++) {
			context.waitTicks(5);
		}

		check(this.on(s -> session(s).figure("kestrel").mode == Figure.ROUTINE
				&& DestroyerInterior.HIDDEN.contains(session(s).figure("kestrel").position().add(0.0, 0.5, 0.0))), "Kestrel does not come back into the hideout");
		this.talkTo("kestrel", true);
		this.converse("go");
		check(this.lines.contains("m6_plan.pickup") && this.lines.contains("m6_plan.escort"), "the pickup or escort line of the plan was skipped");
		this.waitStep("m6.reactor", 40);
		this.place(Story.DESTROYER, new Vec3(28.8, R, 25.0), 90.0F, 0.0F);
		this.waitStep("m6.door", 60);
		this.place(Story.DESTROYER, new Vec3(44.5, R, 37.2), 0.0F, 10.0F);
		this.droid(DroidType.R2D2, s -> session(s).has("lab.open"), "R2-V7 does not open the lab");
		this.waitStep("m6.lab", 60);
		this.use("holo", R);
		this.converse();
		this.waitStep("m6.saber", 60);
		this.place(Story.DESTROYER, new Vec3(44.5, R, 47.0), 0.0F, 10.0F);
		context.takeScreenshot("story_lab");
		this.use("saber", R);
		this.converse("take");
		this.waitStep("m6.escape1", 40);
		check(this.on(s -> SaberVariant.canHold(me(s).getMainHandItem())), "Ilyra's saber is not in the hand");
		this.server.runCommand("effect give @a minecraft:resistance 100000 4 true");
		this.server.runCommand("effect give @a minecraft:instant_health 1 4 true");
		context.waitTicks(20);
		this.strikeTrooper();
		this.place(Story.DESTROYER, new Vec3(28.8, H, 25.0), 180.0F, 0.0F);
		this.waitStep("m6.escape2", 60);
		this.place(Story.DESTROYER, new Vec3(40.5, H, -17.5), 180.0F, 0.0F);
		this.waitStep("m6.duel", 60);
		this.duel();
		this.waitStep("m6.falcon", 40);
		this.exitShip("star_destroyer", new Vec3(40.5, H, -38.0));
		this.waitStep("m6.jump", 80);
		this.talkTo("dax", true);
		this.converse();
		// In the tunnel (the jump charges for 30 ticks).
		context.waitTicks(85);
		context.takeScreenshot("story_jump");
		this.waitStep("m6.epilogue", 900);
		this.waitDialog(60, "the epilogue does not begin");
		this.finishTyping();
		context.waitTicks(2);
		context.takeScreenshot("story_epilogue");
		this.converse();
		check(this.lines.contains("m6_epilogue.dax") && this.lines.contains("m6_epilogue.tamsin"), "Dax's or Tamsin's line of the epilogue was skipped");
		context.waitTicks(20);
		check(this.on(s -> session(s).progress.finished), "the chapter is not finished after the epilogue");
		context.waitTicks(10);
		StoryStatePayload state = context.computeOnClient(client -> StoryClient.state());
		check(state != null && state.finished() && state.completed().size() == 6, "the client does not know the chapter is done: " + state);
		context.setScreen(StoryMenuScreen::new);
		context.waitTicks(20);
		context.takeScreenshot("story_menu_finished");

		// --- Out again: one's own things back, and the saber -------------------------------------------
		context.runOnClient(client -> StoryClient.send(StoryActionPayload.LEAVE, "", 0));
		context.waitTicks(40);
		this.server.runOnServer(s -> {
			ServerPlayer player = me(s);
			check(!Story.playing(player), "still in the story after leaving");
			check(!player.level().dimension().equals(ShipInteriors.DIMENSION) && player.position().distanceTo(home) < 2.0,
					"not back where one was: " + player.position() + " (was " + home + ")");
			check(player.gameMode.getGameModeForPlayer() == GameType.SURVIVAL, "the game mode did not come back");
			check(player.getInventory().countItem(Items.DIAMOND) == 5 && player.getInventory().countItem(Items.BREAD) == 3, "one's own things did not come back");
			boolean saber = false;

			for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
				saber |= SaberVariant.canHold(player.getInventory().getItem(i));
			}

			check(saber, "Ilyra's saber was not given at the end");
			check(Droids.crew(player).isEmpty(), "the story's droids stayed with the player");
		});
		this.server.runCommand("effect clear @a");
		SaberCraft.LOGGER.info("STORY: played through, {} lines, {} captures", this.lines.size(), this.server.computeOnServer(s -> Story.capturesForTest()));
	}

	/** Mission 3 once more, refusing Hale: Mission 5 then goes through the crawl way (M-31 at the bolted hatch). */
	private void otherWay() {
		ClientGameTestContext context = this.context;
		context.runOnClient(client -> StoryClient.send(StoryActionPayload.MISSION, "", 2));
		context.waitTicks(40);
		this.waitStep("m3.plan", 60);
		check(this.on(s -> !session(s).has("trust.hale") && session(s).has("droid.m31") && session(s).has("know.password")),
				"Mission 3 played again does not start with what was known then");
		this.unseen();
		this.talkTo("kestrel", false);
		this.converse("go");
		this.waitStep("m3.console", 40);
		this.use("security", H);
		this.converse();
		this.use("locker", H);
		this.converse();
		this.waitStep("m3.card", 40);
		this.use("card", H);
		this.converse();
		this.waitStep("m3.hale", 40);
		this.waitDialog(400, "Hale does not come to speak (again)");
		this.converse("want", "refuse");
		this.waitStep("m3.return", 400);
		check(this.on(s -> session(s).has("choice.refuse_hale") && !session(s).has("trust.hale")), "refusing Hale is not remembered");
		this.place(Story.DESTROYER, new Vec3(20.5, H, -4.5), 90.0F, 0.0F);
		this.waitStep("m4.brief", 60);
		this.talkTo("kestrel", false);
		this.converse("go");
		check(this.lines.contains("m4_kestrel.refused"), "Kestrel does not speak of the refusal");
		this.waitStep("m4.checkpoint", 40);
		this.talkTo("checkpoint", false);
		this.converse("card");
		this.waitStep("m4.lift", 40);
		this.place(Story.DESTROYER, new Vec3(28.8, R, 25.0), 90.0F, 0.0F);
		this.waitStep("m4.tech", 60);
		this.talkTo("tech", false);
		this.converse("who", "changed");
		this.waitStep("m4.download", 40);
		check(this.on(s -> session(s).has("tech.watch")), "the bluff does not leave the technician watching");
		this.place(Story.DESTROYER, new Vec3(50.6, R, 24.5), 270.0F, 20.0F);
		this.useHere("console");
		this.converse();
		// The technician watches the console: at it, his suspicion rises (for a moment without the
		// test's cloak of unseen)...
		float watching = this.on(s -> session(s).figure("tech").suspicion);
		this.server.runOnServer(s -> StoryTesting.unseen(session(s), 0));
		context.waitTicks(15);
		float watched = this.on(s -> session(s).figure("tech").suspicion);
		this.unseen();
		check(watched > watching + 0.05F, "the technician who was lied to does not grow suspicious at the console (" + watching + " -> " + watched + ")");
		// ... and M-31's noise sends him away for a while.
		this.place(Story.DESTROYER, new Vec3(50.6, R, 24.5), 135.0F, 25.0F);
		this.droid(DroidType.MOUSE, s -> {
			Figure tech = session(s).figure("tech");
			return tech == null || tech.mode == Figure.INVESTIGATE;
		}, "M-31's noise does not send the technician away");
		this.place(Story.DESTROYER, new Vec3(50.6, R, 24.5), 270.0F, 20.0F);
		this.waitStep("m5.escape", 400);
		this.place(Story.DESTROYER, new Vec3(20.5, H, -4.5), 90.0F, 0.0F);
		this.waitStep("m5.recording", 60);
		this.use("recorder", H);
		this.converse();
		this.waitStep("m5.route", 40);
		// The terminal tells of the trap; the hatch to the crawl way is bolted from the other side.
		this.use("terminal", H);
		this.converse("hale", "off");
		check(this.on(s -> session(s).has("know.hale_trap")), "the terminal does not tell of Hale's trap");
		BlockPos hatch = DestroyerInterior.HIDEOUT_HATCH;
		check(this.on(s -> Story.locked(session(s).destroyer, hatch.getX(), hatch.getY(), hatch.getZ())), "the crawl way's hatch is not bolted");
		this.place(Story.DESTROYER, new Vec3(21.5, H, -3.5), 270.0F, 10.0F);
		this.droid(DroidType.MOUSE, s -> session(s).has("hatch.f"), "M-31 does not open the bolted hatch");
		check(!this.on(s -> Story.locked(session(s).destroyer, hatch.getX(), hatch.getY(), hatch.getZ())), "the hatch stays bolted");
		this.place(Story.DESTROYER, new Vec3(24.5, H, 5.0), 90.0F, 0.0F);
		this.waitStep("m5.r2", 80);
		check(this.on(s -> !session(s).has("tmp.trapped")), "the crawl way still led into Hale's trap");
		this.talkTo("r2v7", false);
		this.converse();
		this.waitStep("m5.cell1", 40);
		this.place(Story.DESTROYER, new Vec3(27.5, H, 4.6), 180.0F, 10.0F);
		this.droid(DroidType.R2D2, s -> session(s).has("cell1.open"), "R2-V7 does not open Kestrel's cell (crawl way)");
		this.talkTo("kestrel", false);
		this.converse();
		check(this.lines.contains("m5_kestrel.refused"), "Kestrel's line for the crawl way was skipped");
		this.waitStep("m5.back", 40);
		context.runOnClient(client -> StoryClient.send(StoryActionPayload.LEAVE, "", 0));
		context.waitTicks(40);
		check(!this.on(s -> Story.playing(me(s))), "still in the story after leaving (second time)");
	}

	// --- Parts tested on the way ---------------------------------------------------------------------

	/** The maintenance room: the trooper's lamp in the dark, from behind the machinery. */
	private void lampPicture() {
		Vec3 at = this.on(s -> {
			Figure trooper = session(s).figure("maint");
			return trooper == null ? null : trooper.position();
		});

		if (at == null) {
			this.problem("no trooper with a lamp in the maintenance room");
			return;
		}

		this.place(Story.DESTROYER, new Vec3(3.5, H, -6.5), aimYaw(new Vec3(3.5, H + 1.6, -6.5), at.add(0.0, 1.0, 0.0)), 10.0F);
		this.context.waitTicks(20);
		this.context.takeScreenshot("story_maintenance_lamp");
	}

	/**
	 * The dark: in the maintenance room someone sees far only in the lamp's cone; in the light,
	 * in front of them, further; never through a wall, never from behind.
	 */
	private void darkness() {
		this.server.runOnServer(s -> {
			StorySession session = session(s);
			Figure trooper = session.figure("maint");

			if (trooper == null) {
				return;
			}

			// At the room's east end, looking west along it (south of the machinery).
			trooper.mode = Figure.HELD;
			trooper.target = null;
			trooper.x = 11.0;
			trooper.z = -5.5;
			trooper.y = H;
			trooper.yaw = 90.0F;
			trooper.headYaw = 90.0F;
		});
		this.context.waitTicks(4);
		// 5.5 blocks in front, in the cone of his lamp: seen. 5 blocks, 38 degrees aside: the dark hides.
		this.place(Story.DESTROYER, new Vec3(5.5, H, -5.5), 270.0F, 0.0F);
		float inCone = this.on(s -> StoryTesting.sees(session(s), session(s).figure("maint")));
		Vec3 aside = new Vec3(11.0 - Math.cos(Math.toRadians(38.0)) * 5.0, H, -5.5 + Math.sin(Math.toRadians(38.0)) * 5.0);
		this.place(Story.DESTROYER, aside, 270.0F, 0.0F);
		float outOfCone = this.on(s -> StoryTesting.sees(session(s), session(s).figure("maint")));
		this.place(Story.DESTROYER, new Vec3(11.6, H, -7.3), 270.0F, 0.0F);
		float behind = this.on(s -> StoryTesting.sees(session(s), session(s).figure("maint")));
		SaberCraft.LOGGER.info("STORY: seeing in the dark: in the lamp {}, aside {}, behind {}", inCone, outOfCone, behind);
		check(inCone > 0.0F, "the lamp does not show the player in its cone");
		check(outOfCone == 0.0F, "the trooper sees in the dark outside his lamp");
		check(behind == 0.0F, "the trooper sees behind him");
		this.server.runOnServer(s -> {
			Figure trooper = session(s).figure("maint");

			if (trooper != null) {
				trooper.mode = Figure.RETURN;
			}
		});
	}

	/** Mission 2: unseen near the two pilots for a while, one hears the password. */
	private void eavesdrop() {
		Vec3 pilots = new Vec3(46.1, H + 1.0, -25.4);
		Vec3 stand = this.on(s -> standNear(s, Story.DESTROYER, pilots, H, 2.0, 4.6));

		if (stand == null) {
			this.problem("no place to stand near the pilots");
			return;
		}

		this.place(Story.DESTROYER, stand, aimYaw(stand.add(0.0, 1.62, 0.0), pilots), 0.0F);
		boolean heard = false;

		for (int i = 0; i < 40 && !heard; i++) {
			this.context.waitTicks(10);
			heard = this.on(s -> session(s).has("know.password"));
		}

		check(heard, "listening to the pilots does not give the password");
	}

	/** M-31's noise: someone near where the player looks goes to see (Mission 2, the lobby). */
	private void distraction() {
		Figure near = this.on(s -> {
			StorySession session = session(s);
			Figure best = null;

			for (Figure figure : session.figures) {
				if (figure.observer && Story.DESTROYER.equals(figure.ship) && figure.mode == Figure.ROUTINE && figure.y > H - 1 && figure.y < H + 1
						&& (best == null || figure.position().distanceTo(new Vec3(40.5, H, -8.0)) < best.position().distanceTo(new Vec3(40.5, H, -8.0)))) {
					best = figure;
				}
			}

			return best;
		});

		if (near == null) {
			this.problem("nobody on the hangar deck to distract");
			return;
		}

		Vec3 target = near.position();
		Vec3 stand = this.on(s -> standNear(s, Story.DESTROYER, target, H, 4.0, 7.0));

		if (stand == null) {
			this.problem("no place to stand near " + near.key + " for the distraction");
			return;
		}

		this.place(Story.DESTROYER, stand, aimYaw(stand.add(0.0, 1.62, 0.0), target.add(0.0, 0.3, 0.0)), 20.0F);
		this.droid(DroidType.MOUSE, s -> {
			Figure figure = session(s).figure(near.key);
			return figure != null && (figure.mode == Figure.INVESTIGATE || figure.mode == Figure.LOOK);
		}, "M-31's noise does not draw anyone");
	}

	/**
	 * Seen and caught: in front of a trooper in the light the suspicion rises (the HUD shows it),
	 * he gives chase, catches the player, and the step begins again from its checkpoint (what was
	 * learnt since is forgotten, the player back at its start).
	 */
	private void seenAndCaught() {
		int before = this.on(s -> Story.capturesForTest());
		Object[] spot = this.on(s -> {
			StorySession session = session(s);

			for (double distance : new double[] {8.0, 6.5, 5.0}) {
				for (Figure figure : session.figures) {
					if (!figure.observer || !Story.DESTROYER.equals(figure.ship) || !Crew.TROOPER.equals(figure.kind()) || figure.mode != Figure.ROUTINE
							|| !figure.crew.path().isEmpty() || figure.y < H - 1 || figure.y > H + 1) {
						continue;
					}

					Vec3 at = figure.position().add(figure.look().scale(distance));

					if (free(s, Story.DESTROYER, at) && clear(s, Story.DESTROYER, figure.eye(), at.add(0.0, 1.5, 0.0))) {
						return new Object[] {figure.key, at, figure.eye()};
					}
				}
			}

			return null;
		});

		if (spot == null) {
			this.problem("no trooper on the deck to be seen by");
			return;
		}

		String key = (String) spot[0];
		Vec3 at = (Vec3) spot[1];
		Vec3 eye = (Vec3) spot[2];
		this.place(Story.DESTROYER, at, aimYaw(at.add(0.0, 1.62, 0.0), eye), 0.0F);
		this.server.runOnServer(s -> StoryTesting.unseen(session(s), 0));
		this.film("seen", 40, 3);
		int stage = this.on(s -> session(s).figure(key) != null ? session(s).figure(key).stage : -1);
		boolean caught = false;

		for (int i = 0; i < 60 && !caught; i++) {
			this.context.waitTicks(5);
			caught = this.on(s -> Story.capturesForTest()) > before;
		}

		check(caught, "the trooper " + key + " does not catch the player standing in front of him (stage " + stage + ")");
		this.context.waitTicks(20);
		check(this.step().equals("m2.terminal"), "after the capture the step is " + this.step());
		check(!this.on(s -> session(s).journal.contains("terminal_map")), "what was learnt after the checkpoint was kept after the capture");
		check(this.on(s -> session(s).inside(Story.DESTROYER, DestroyerInterior.HIDDEN)), "after the capture the player is not back at the checkpoint");
		check(this.on(s -> session(s).alarm == 0), "the alarm stays after the capture");
		this.unseen();
	}

	/** The armour: the troops do not see it; a sprinting trooper is noticed; the security bureau looks closely. */
	private void disguise() {
		Object[] spot = this.on(s -> {
			StorySession session = session(s);

			for (Figure figure : session.figures) {
				if (!figure.observer || !Crew.TROOPER.equals(figure.kind()) || figure.y < H - 1 || figure.y > H + 1) {
					continue;
				}

				Vec3 at = figure.position().add(figure.look().scale(5.0));

				if (free(s, Story.DESTROYER, at) && clear(s, Story.DESTROYER, figure.eye(), at.add(0.0, 1.5, 0.0))) {
					return new Object[] {figure.key, at, figure.eye()};
				}
			}

			return null;
		});

		if (spot == null) {
			this.problem("no trooper to test the armour in front of");
			return;
		}

		String key = (String) spot[0];
		Vec3 at = (Vec3) spot[1];
		this.place(Story.DESTROYER, at, aimYaw(at.add(0.0, 1.62, 0.0), (Vec3) spot[2]), 0.0F);
		float walking = this.on(s -> StoryTesting.sees(session(s), session(s).figure(key)));
		float sprinting = this.on(s -> {
			me(s).setSprinting(true);
			float rate = StoryTesting.sees(session(s), session(s).figure(key));
			me(s).setSprinting(false);
			return rate;
		});
		SaberCraft.LOGGER.info("STORY: the armour before {}: walking {}, sprinting {}", key, walking, sprinting);
		check(walking == 0.0F, "a trooper sees through the armour");
		check(sprinting > 0.0F, "a sprinting 'trooper' is not noticed");
		this.context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
		this.context.waitTicks(20);
		this.context.takeScreenshot("story_disguise");
		this.context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
	}

	/** A door of the plot opens when one stands at it. */
	private void doorOpens(BlockPos cell, Vec3 stand, String what) {
		this.place(Story.DESTROYER, stand, 180.0F, 0.0F);
		this.context.waitTicks(20);
		check(this.on(s -> s.getLevel(ShipInteriors.DIMENSION).getBlockState(session(s).destroyer.offset(cell)).isAir()), what + "'s door does not open");
	}

	/** Leaving in the middle of the story and coming back: the same step, the same things known. */
	private void leaveAndContinue(Vec3 home) {
		List<String> flags = this.on(s -> List.copyOf(session(s).progress.flags));
		this.context.runOnClient(client -> StoryClient.send(StoryActionPayload.LEAVE, "", 0));
		this.context.waitTicks(40);
		this.server.runOnServer(s -> {
			ServerPlayer player = me(s);
			check(!Story.playing(player), "still in the story after leaving midway");
			check(player.getInventory().countItem(Items.DIAMOND) == 5, "one's own things are not back after leaving midway");
			check(player.position().distanceTo(home) < 2.0, "not back home after leaving midway");
		});
		this.context.setScreen(StoryMenuScreen::new);
		this.context.waitTicks(20);
		this.context.takeScreenshot("story_menu_saved");

		if (!this.context.tryClickScreenButton("sabercraft.menu.story.continue")) {
			this.problem("the story menu has no continue button to click");
			this.context.runOnClient(client -> StoryClient.send(StoryActionPayload.CONTINUE, "", 0));
		}

		this.context.waitTicks(40);
		this.waitStep("m2.return", 60);
		check(this.on(s -> session(s).flags.containsAll(flags)), "what was known is lost after continuing");
		check(this.on(s -> Droids.crew(me(s)).contains(DroidType.MOUSE)), "M-31 is not back after continuing");
		this.unseen();
	}

	/** The finale: the saber strikes a trooper (twice, and he is down). */
	private void strikeTrooper() {
		Figure trooper = this.on(s -> StoryTesting.fighter(session(s), session(s).local(Story.DESTROYER)));

		if (trooper == null) {
			this.problem("no fighter in the reactor after taking the saber");
			return;
		}

		String key = trooper.key;

		for (int stroke = 0; stroke < 2; stroke++) {
			this.faceFigure(key, 1.8);
			float before = this.on(s -> session(s).figure(key).health);
			int struck = this.context.computeOnClient(client -> StoryInput.struckForTest());
			this.context.getInput().pressKey(options -> options.keyAttack);
			this.context.waitTicks(10);
			float after = this.on(s -> session(s).figure(key).health);
			int sent = this.context.computeOnClient(client -> StoryInput.struckForTest());
			check(sent > struck, "the attack key with the saber sends no stroke");
			check(after < before, "the stroke does not hit " + key + " (" + before + " -> " + after + ")");
		}

		check(this.on(s -> session(s).figure(key).mode == Figure.DOWN), key + " is not down after two strokes");
		this.context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
		this.context.waitTicks(20);
		this.context.takeScreenshot("story_trooper_down");
		this.context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
	}

	/** The duel: she speaks, lights her saber; a stroke into her guard is parried, open she is hit, and at the end she breaks off. */
	private void duel() {
		this.place(Story.DESTROYER, new Vec3(40.5, H, -21.0), 180.0F, 0.0F);
		this.waitDialog(80, "the Shadowblade does not speak");
		this.converse();
		this.context.waitTicks(20);
		check(this.on(s -> session(s).has("tmp.duel")), "the duel does not begin");
		this.checkMusic("music.story_duel", "the duel");
		this.faceFigure("shadow", 2.2);
		this.server.runOnServer(s -> {
			Figure shadow = session(s).figure("shadow");
			shadow.timer = 0;
			shadow.windup = 0;
			shadow.cooldown = 40;
		});
		float before = this.on(s -> session(s).figure("shadow").health);
		this.context.getInput().pressKey(options -> options.keyAttack);
		this.context.waitTicks(8);
		check(this.on(s -> session(s).figure("shadow").health) == before, "a stroke into her guard is not parried");
		// Filmed from behind and a little aside, so that both blades are seen (not her behind the player).
		this.faceFigure("shadow", 3.0);
		this.context.runOnClient(client -> {
			client.player.setYRot(client.player.getYRot() - 28.0F);
			client.player.setXRot(8.0F);
			client.options.setCameraType(CameraType.THIRD_PERSON_BACK);
		});
		this.film("duel", 30, 2);
		this.context.takeScreenshot("story_duel");
		this.context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));

		for (int i = 0; i < 14 && this.step().equals("m6.duel"); i++) {
			this.faceFigure("shadow", 2.0);
			this.server.runOnServer(s -> {
				Figure shadow = session(s).figure("shadow");

				if (shadow != null) {
					StoryTesting.open(shadow);
				}
			});
			this.context.getInput().pressKey(options -> options.keyAttack);
			this.context.waitTicks(8);
		}

		check(!this.step().equals("m6.duel"), "the Shadowblade does not break off");
	}

	// --- Doing things ----------------------------------------------------------------------------------

	/** Walks up to someone of the story and speaks to them (with a right click, if {@code click}). */
	private void talkTo(String key, boolean click) {
		Object[] who = this.on(s -> {
			Figure figure = session(s).figure(key);
			return figure == null ? null : new Object[] {figure.id, figure.ship, figure.box()};
		});

		if (who == null) {
			throw new AssertionError("Nobody called " + key + " in step " + this.step() + "; problems so far: " + this.problems);
		}

		int id = (int) who[0];
		String ship = (String) who[1];
		AABB box = (AABB) who[2];
		Vec3 aim = new Vec3(box.getCenter().x, box.maxY - 0.35, box.getCenter().z);
		Vec3 stand = this.on(s -> standNear(s, ship, aim, Math.floor(box.minY + 0.01), 1.3, REACH));

		if (stand == null) {
			throw new AssertionError("No place to stand near " + key + " in step " + this.step());
		}

		this.place(ship, stand, aimYaw(stand.add(0.0, 1.62, 0.0), aim), aimPitch(stand.add(0.0, 1.62, 0.0), aim));
		this.context.waitTicks(4);

		if (click) {
			this.context.getInput().pressKey(options -> options.keyUse);
			this.context.waitTicks(6);

			if (this.line().isEmpty()) {
				String target = this.context.computeOnClient(client -> {
					StoryInput.Target t = StoryInput.target(client, 1.0F);
					return t == null ? "nothing" : t.id() + "/" + t.figure();
				});
				SaberCraft.LOGGER.warn("STORY: click on {} failed: {}", key, this.context.computeOnClient(StoryInput::explainForTest));
				this.problem("a right click on " + key + " opened no dialog (the crosshair found " + target + ")");
			}
		}

		if (this.line().isEmpty()) {
			this.context.runOnClient(client -> StoryClient.send(StoryActionPayload.TALK, "", id));
			this.context.waitTicks(6);
		}

		if (this.line().isEmpty()) {
			throw new AssertionError("Speaking to " + key + " opened no dialog in step " + this.step() + "; problems so far: " + this.problems);
		}
	}

	/** Walks up to a thing and uses it with a right click. */
	private void use(String id, double floor) {
		Vec3 point = this.on(s -> {
			Vec3 world = StoryTesting.point(session(s), id);
			return world == null ? null : world.subtract(Vec3.atLowerCornerOf(session(s).destroyer));
		});

		if (point == null) {
			throw new AssertionError("Nothing called " + id + " to use in step " + this.step() + "; problems so far: " + this.problems);
		}

		Vec3 stand = this.on(s -> standNear(s, Story.DESTROYER, point, floor, 1.0, REACH));

		if (stand == null) {
			throw new AssertionError("No place to stand near " + id);
		}

		this.place(Story.DESTROYER, stand, aimYaw(stand.add(0.0, 1.62, 0.0), point), aimPitch(stand.add(0.0, 1.62, 0.0), point));
		this.useHere(id);
	}

	/** Uses what the crosshair is on (a right click); sends the use itself if the click found nothing. */
	private void useHere(String id) {
		this.context.waitTicks(4);
		int used = this.context.computeOnClient(client -> StoryInput.usedForTest());
		this.context.getInput().pressKey(options -> options.keyUse);
		this.context.waitTicks(6);

		if (this.context.computeOnClient(client -> StoryInput.usedForTest()) == used) {
			String target = this.context.computeOnClient(client -> {
				StoryInput.Target t = StoryInput.target(client, 1.0F);
				return t == null ? "nothing" : t.id() + "/" + t.figure();
			});
			SaberCraft.LOGGER.warn("STORY: click on {} failed: {}", id, this.context.computeOnClient(StoryInput::explainForTest));
			this.problem("a right click on " + id + " used nothing (the crosshair found " + target + ")");
			this.context.runOnClient(client -> StoryClient.send(StoryActionPayload.USE, id, 0));
			this.context.waitTicks(6);
		}
	}

	/** A droid's ability with its key (the story chooses which droid), until the condition holds. */
	private void droid(DroidType type, Function<MinecraftServer, Boolean> done, String failure) {
		this.context.waitTicks(30);

		for (int attempt = 0; attempt < 3; attempt++) {
			this.context.getInput().pressKey(DroidControl.ABILITY_KEY);

			for (int i = 0; i < 20; i++) {
				this.context.waitTicks(5);

				if (this.on(done)) {
					return;
				}
			}

			// Too far (it was still coming): call it, then once more.
			this.server.runOnServer(s -> {
				var droid = Droids.droid(me(s), type);

				if (droid != null) {
					droid.teleportToOwner();
				}
			});
			this.context.waitTicks(10);
		}

		this.problem(failure);
	}

	/** Goes through the open dialog: answers by preference (each once), else the first; on where there are none. */
	private void converse(String... prefer) {
		List<String> used = new ArrayList<>();

		for (int i = 0; i < 80; i++) {
			String line = this.line();

			if (line.isEmpty()) {
				return;
			}

			if (this.lines.isEmpty() || !this.lines.get(this.lines.size() - 1).equals(line)) {
				this.lines.add(line);
			}

			List<String> answers = this.on(s -> StoryTesting.answers(session(s)));
			int index = -1;

			if (!answers.isEmpty()) {
				index = 0;

				for (String wanted : prefer) {
					if (answers.contains(wanted) && !used.contains(wanted)) {
						index = answers.indexOf(wanted);
						used.add(wanted);
						break;
					}
				}
			}

			int choice = index;
			this.context.runOnClient(client -> StoryClient.send(StoryActionPayload.ANSWER, "", choice));
			this.context.waitTicks(3);
		}

		throw new AssertionError("A dialog does not end: " + this.line());
	}

	private void finishTyping() {
		this.context.runOnClient(client -> {
			if (client.gui.screen() instanceof DialogScreen dialog) {
				dialog.finishForTest();
			}
		});
		this.context.waitTicks(2);
	}

	private void waitDialog(int ticks, String failure) {
		for (int i = 0; i < ticks / 5; i++) {
			if (!this.line().isEmpty()) {
				return;
			}

			this.context.waitTicks(5);
		}

		throw new AssertionError(failure + " (step " + this.step() + "); problems so far: " + this.problems);
	}

	/** Steps into a ship's exit (the Falcon's ramp, the hangar's force field) nearest that point. */
	private void exitShip(String plan, Vec3 near) {
		AABB best = null;

		for (AABB area : InteriorPlan.of(plan).exits()) {
			if (best == null || area.getCenter().distanceTo(near) < best.getCenter().distanceTo(near)) {
				best = area;
			}
		}

		Vec3 centre = best.getCenter();
		String ship = plan.equals("falcon") ? Story.FALCON : Story.DESTROYER;
		this.place(ship, new Vec3(centre.x, best.minY, centre.z), 0.0F, 0.0F);
		this.context.waitTicks(30);
	}

	/** Puts the player at a place of a ship of their story (plot space), looking that way. */
	private void place(String ship, Vec3 local, float yaw, float pitch) {
		BlockPos origin = this.on(s -> session(s).origin(ship));
		this.server.runCommand(String.format(Locale.ROOT, "execute in sabercraft:ship_interiors run tp @a %.3f %.3f %.3f %.1f %.1f", origin.getX() + local.x,
				origin.getY() + local.y, origin.getZ() + local.z, yaw, pitch));
		this.context.waitTicks(8);
	}

	/** Turns the camera to someone of the story, without moving (an open dialog stays open). */
	private void lookAt(String key) {
		Vec3 head = this.on(s -> {
			StorySession session = session(s);
			Figure figure = session.figure(key);

			if (figure == null) {
				return null;
			}

			// At the waist: the face then shows above the dialog's panel.
			AABB box = figure.box();
			return new Vec3(box.getCenter().x, box.minY + 0.7, box.getCenter().z).add(Vec3.atLowerCornerOf(session.origin(figure.ship)));
		});

		if (head == null) {
			this.problem("nobody called " + key + " to look at");
			return;
		}

		this.context.runOnClient(client -> {
			Vec3 eye = client.player.getEyePosition();
			client.player.setYRot(aimYaw(eye, head));
			client.player.setXRot(aimPitch(eye, head));
		});
		this.context.waitTicks(2);
	}

	/** Stands in front of a figure (that far), facing them. */
	private void faceFigure(String key, double distance) {
		Object[] at = this.on(s -> {
			StorySession session = session(s);
			Figure figure = session.figure(key);

			if (figure == null) {
				return null;
			}

			Vec3 me = session.local(Story.DESTROYER);
			Vec3 away = new Vec3(me.x - figure.x, 0.0, me.z - figure.z);
			away = away.lengthSqr() < 1.0E-3 ? figure.look() : away.normalize();
			Vec3 stand = figure.position().add(away.scale(distance));
			return new Object[] {stand, figure.box().getCenter()};
		});

		if (at == null) {
			this.problem("nobody called " + key + " to face");
			return;
		}

		Vec3 stand = (Vec3) at[0];
		Vec3 centre = (Vec3) at[1];
		this.place(Story.DESTROYER, stand, aimYaw(stand.add(0.0, 1.62, 0.0), centre), aimPitch(stand.add(0.0, 1.62, 0.0), centre));
	}

	/** Keeps the player unseen while the test walks the story (as after a capture, for long). */
	private void unseen() {
		this.server.runOnServer(s -> StoryTesting.unseen(session(s), 1_000_000));
	}

	private void checkMusic(String piece, String where) {
		String playing = null;

		for (int i = 0; i < 12; i++) {
			playing = this.context.computeOnClient(client -> StoryMusic.playing());

			if (piece.equals(playing)) {
				return;
			}

			this.context.waitTicks(10);
		}

		this.problem("the music in " + where + " is " + playing + ", not " + piece);
	}

	/** A short film: numbered screenshots a few ticks apart (put together afterwards). */
	private void film(String name, int frames, int every) {
		for (int i = 0; i < frames; i++) {
			this.context.takeScreenshot(String.format(Locale.ROOT, "film_%s_%03d", name, i));

			if (every > 2) {
				this.context.waitTicks(every - 2);
			}
		}
	}

	// --- Looking at the state ------------------------------------------------------------------------

	private String step() {
		return this.on(s -> {
			StorySession session = session(s);
			return session == null ? "-" : StoryTesting.step(session);
		});
	}

	private String line() {
		return this.on(s -> {
			StorySession session = session(s);
			return session == null ? "" : StoryTesting.line(session);
		});
	}

	private void waitStep(String step, int ticks) {
		String now = this.step();

		for (int i = 0; i < ticks / 5 && !now.equals(step); i++) {
			this.context.waitTicks(5);
			now = this.step();
		}

		if (!now.equals(step)) {
			String line = this.line();
			String state = this.on(s -> {
				StorySession session = session(s);
				return "known " + session.flags + ", counters " + session.counters + ", at " + session.local(Story.DESTROYER) + ", grace " + session.grace;
			});
			throw new AssertionError("Step " + step + " not reached (now " + now + ", dialog '" + line + "'; " + state + "); problems so far: " + this.problems);
		}

		SaberCraft.LOGGER.info("STORY: step {}", step);
	}

	private <T> T on(Function<MinecraftServer, T> function) {
		return this.server.computeOnServer(function::apply);
	}

	private ServerPlayer me(MinecraftServer server) {
		return server.getPlayerList().getPlayer(this.self);
	}

	private @Nullable StorySession session(MinecraftServer server) {
		ServerPlayer player = this.me(server);
		return player == null ? null : Story.sessionForTest(player).orElse(null);
	}

	// --- Places ----------------------------------------------------------------------------------------

	/**
	 * A place to stand (plot space, on the floor at that height) from which one reaches a point and
	 * sees it: free for feet and head, a floor below, nearest first.
	 */
	private @Nullable Vec3 standNear(MinecraftServer s, String ship, Vec3 point, double floor, double min, double reach) {
		for (double r = min; r <= reach + 0.01; r += 0.35) {
			for (int a = 0; a < 360; a += 15) {
				Vec3 at = new Vec3(point.x + Math.cos(Math.toRadians(a)) * r, floor, point.z + Math.sin(Math.toRadians(a)) * r);

				if (at.add(0.0, 1.62, 0.0).distanceTo(point) > reach || !free(s, ship, at)) {
					continue;
				}

				if (clear(s, ship, at.add(0.0, 1.62, 0.0), point)) {
					return at;
				}
			}
		}

		return null;
	}

	/**
	 * Whether one can stand there (plot space): the whole body (0.6 wide) free, a floor below. (Only
	 * the centre was checked once: half in a wall, the client pushed the player out sideways, and the
	 * aim went past a sign or a droid.)
	 */
	private boolean free(MinecraftServer s, String ship, Vec3 at) {
		ServerLevel level = s.getLevel(ShipInteriors.DIMENSION);
		BlockPos origin = session(s).origin(ship);
		Vec3 world = at.add(Vec3.atLowerCornerOf(origin));
		AABB body = new AABB(world.x - 0.3, world.y + 0.05, world.z - 0.3, world.x + 0.3, world.y + 1.8, world.z + 0.3);
		BlockPos below = BlockPos.containing(world.x, world.y - 0.05, world.z);
		return level.noCollision(null, body) && !level.getBlockState(below).getCollisionShape(level, below).isEmpty();
	}

	/** Whether nothing that collides is between two points (plot space), or only the thing looked at. */
	private boolean clear(MinecraftServer s, String ship, Vec3 from, Vec3 to) {
		ServerLevel level = s.getLevel(ShipInteriors.DIMENSION);
		Vec3 corner = Vec3.atLowerCornerOf(session(s).origin(ship));
		HitResult hit = level.clip(new ClipContext(from.add(corner), to.add(corner), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
		return hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceTo(to.add(corner)) < 0.9;
	}

	private static float aimYaw(Vec3 eye, Vec3 target) {
		return (float) Math.toDegrees(Math.atan2(-(target.x - eye.x), target.z - eye.z));
	}

	private static float aimPitch(Vec3 eye, Vec3 target) {
		double dy = target.y - eye.y;
		return (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(target.x - eye.x, target.z - eye.z)));
	}

	private void check(boolean condition, String message) {
		if (!condition) {
			this.problem(message);
		}
	}

	private void problem(String message) {
		SaberCraft.LOGGER.error("STORY CHECK FAILED: {}", message);
		this.problems.add(message);
	}

	@SuppressWarnings("unused")
	private static Minecraft client() {
		return Minecraft.getInstance();
	}
}
