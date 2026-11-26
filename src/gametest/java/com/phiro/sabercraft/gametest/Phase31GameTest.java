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
import net.minecraft.client.KeyMapping;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.client.Hints;
import com.phiro.sabercraft.client.ImmersionConfig;
import com.phiro.sabercraft.client.interior.MeshAudit;
import com.phiro.sabercraft.client.story.DialogScreen;
import com.phiro.sabercraft.client.story.StoryBrightness;
import com.phiro.sabercraft.client.story.StoryCinema;
import com.phiro.sabercraft.client.story.StoryClient;
import com.phiro.sabercraft.client.story.StoryHud;
import com.phiro.sabercraft.client.story.StoryInput;
import com.phiro.sabercraft.client.story.StoryKeys;
import com.phiro.sabercraft.interior.Crew;
import com.phiro.sabercraft.interior.DestroyerInterior;
import com.phiro.sabercraft.interior.InteriorJump;
import com.phiro.sabercraft.interior.InteriorSystems;
import com.phiro.sabercraft.interior.ShipInteriors;
import com.phiro.sabercraft.network.StoryActionPayload;
import com.phiro.sabercraft.story.Figure;
import com.phiro.sabercraft.story.Story;
import com.phiro.sabercraft.story.StorySession;
import com.phiro.sabercraft.story.StoryTesting;

/**
 * Phase 31: the last fixes before Phiro's final test. What a test can play in the real game (the
 * feel stays for the manual list in {@code docs/development/phase-31-status.md}):
 *
 * <ol>
 * <li>The boss: caught in her second phase, the fight begun again is her first phase in every
 * respect (server and client), again and again (1, 2).</li>
 * <li>Films: only Space and Escape skip them; arrows, Enter, WASD and the mouse do nothing and
 * nothing of them reaches the game (3, 4).</li>
 * <li>No hyperspace for ships in the open world any more; the Falcon's jump of the story still
 * runs (5, 6).</li>
 * <li>The dialog's last line: Space (held) closes it and nothing else happens (no jump, no door,
 * no use); a fresh Space jumps as always; Left and Right page through the box's lines (7, 8, 9).</li>
 * <li>The story's brightness "Maximal": kept across a restart, lifts dark rooms without going
 * beyond white (10).</li>
 * <li>The prison: caught after breaking out of Hale's cell (at once, and after moving on through
 * the block), back in the cell as it was; its main door opens from inside too and closes again;
 * the break-out plays through to its end (11, 15, 16).</li>
 * <li>The takedown: the tip behind the detention block's guard; then any unaware stormtrooper
 * from behind, crouching; never one who is alerted, saw the player a moment ago, is watched by
 * another, nor anyone else than a stormtrooper (12, 13, 14).</li>
 * </ol>
 */
public class Phase31GameTest implements FabricClientGameTest {
	private static final int H = DestroyerInterior.HANGAR + 1;

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
			this.problems.forEach(problem -> SaberCraft.LOGGER.error("PHASE31 PROBLEM: {}", problem));
			throw new AssertionError("Phase 31: " + this.problems.size() + " problem(s): " + String.join(" | ", this.problems));
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

			this.part("hyperspace", this::hyperspace);
			this.part("brightness", this::brightness);
			this.part("films", this::films);
			this.part("dialog", this::dialog);
			this.part("boss", this::boss);
			this.part("tip", this::tip);
			this.part("takedown", this::takedown);
			this.part("refuse", this::refuse);
			this.part("prison", this::prison);
			this.part("falcon", this::falcon);
			this.part("restart", this::restart);
		} catch (AssertionError | RuntimeException e) {
			this.problem("aborted: " + e);
			SaberCraft.LOGGER.error("PHASE31 aborted", e);
		} finally {
			context.getInput().releaseKey(GLFW.GLFW_KEY_SPACE);
			context.getInput().releaseKey(GLFW.GLFW_KEY_ENTER);
			context.getInput().releaseKey(GLFW.GLFW_KEY_E);
			context.getInput().releaseKey(options -> options.keyShift);
			context.getInput().releaseMouse(0);

			if (this.server != null && this.on(s -> this.session(s) != null)) {
				this.leave();
			}

			if (this.server != null) {
				// The takedown learnt here is not kept for the tests after this one (same world file).
				this.server.runOnServer(s -> StoryTesting.forgetTakedowns(s, this.self));
			}
		}
	}

	/** Runs one part of the test (all of them, or only those named in PHASE31_ONLY, for working on one). */
	private void part(String name, Runnable part) {
		String only = System.getenv("PHASE31_ONLY");

		if (only == null || only.isBlank() || List.of(only.split(",")).contains(name)) {
			SaberCraft.LOGGER.info("PHASE31 part {}", name);
			int before = this.problems.size();
			long start = System.currentTimeMillis();
			part.run();
			SaberCraft.LOGGER.info("PHASE31 part {} done in {} s, {} problem(s)", name, (System.currentTimeMillis() - start) / 1000, this.problems.size() - before);
		}
	}

	// --- 5: no hyperspace in the open world -----------------------------------------------------------------------

	private void hyperspace() {
		boolean key = this.context.computeOnClient(client -> {
			for (KeyMapping mapping : client.options.keyMappings) {
				if (mapping.getName().toLowerCase(Locale.ROOT).contains("hyperspace")) {
					return true;
				}
			}

			return false;
		});
		check(!key, "a key for the hyperspace jump is still there");
		// (The mod's sounds are not in the sound event registry: the client's sound list is what counts.)
		check(this.context.computeOnClient(client -> client.getSoundManager().getSoundEvent(SaberCraft.id("ship.hyperspace_fail")) == null),
				"the open world's sound of a failed jump is still there");
		check(this.context.computeOnClient(client -> client.getSoundManager().getSoundEvent(SaberCraft.id("ship.hyperspace_jump")) != null),
				"the story's jump sound is gone");
		boolean gone;

		try {
			Class.forName("com.phiro.sabercraft.ship.Hyperspace");
			gone = false;
		} catch (ClassNotFoundException e) {
			gone = true;
		}

		check(gone, "the open world's hyperspace code is still there");

		for (java.lang.reflect.Method method : com.phiro.sabercraft.ship.ShipEntity.class.getDeclaredMethods()) {
			check(!method.getName().toLowerCase(Locale.ROOT).contains("hyper"), "the ship still has " + method.getName());
		}
	}

	// --- 10: the story's brightness, Maximal ----------------------------------------------------------------------

	private void brightness() {
		int before = this.context.computeOnClient(client -> ImmersionConfig.get().storyBrightness);
		check(new ImmersionConfig().storyBrightness != ImmersionConfig.STORY_BRIGHTNESS_MAX, "Maximal is the default brightness");

		try {
			// Chosen, saved, read again as after a restart: still Maximal.
			int kept = this.context.computeOnClient(client -> {
				ImmersionConfig.get().storyBrightness = ImmersionConfig.STORY_BRIGHTNESS_MAX;
				ImmersionConfig.save();
				ImmersionConfig.get().storyBrightness = 0;
				ImmersionConfig.load();
				return ImmersionConfig.get().storyBrightness;
			});
			check(kept == ImmersionConfig.STORY_BRIGHTNESS_MAX, "Maximal is not kept across a restart (read back " + kept + ")");
			check(this.context.computeOnClient(client -> StoryBrightness.maximal()), "Maximal is not in effect after reading the settings");
			// Dark rooms lifted, bright ones left; below Maximal nothing changes.
			float dark = StoryBrightness.shade(0.17F, ImmersionConfig.STORY_BRIGHTNESS_MAX);
			check(dark > 0.35F && dark < 0.6F, "Maximal does not lift a dark room fairly (0.17 -> " + dark + ")");
			check(StoryBrightness.shade(1.0F, ImmersionConfig.STORY_BRIGHTNESS_MAX) == 1.0F, "Maximal changes a room in full light");
			check(StoryBrightness.shade(0.17F, 2) == 0.17F, "the level below Maximal changed");
			float night = StoryBrightness.lightmapForTest(0, 0.5F, 0.0F);
			float lifted = StoryBrightness.lightmapForTest(0, 1.0F, 0.16F);
			float lamp = StoryBrightness.lightmapForTest(15, 1.0F, 0.16F);
			check(lifted > night + 0.1F, "Maximal does not lift the darkest light (" + night + " -> " + lifted + ")");
			check(lamp <= 1.0F && !Float.isNaN(lamp), "Maximal goes beyond white (" + lamp + ")");
			// The menu's cycle has it (and goes round to the first level after it).
			check(this.context.computeOnClient(client -> Component.translatable("sabercraft.menu.story.brightness.3").getString())
					.matches("(?i)maximal|maximum"), "the menu has no words for Maximal");
		} finally {
			this.context.runOnClient(client -> {
				ImmersionConfig.get().storyBrightness = before;
				ImmersionConfig.save();
			});
		}
	}

	// --- 3, 4: films skip only with Space and Escape ------------------------------------------------------------

	private void films() {
		this.enter(4, "m5.cell1");
		this.unseen();
		this.put(new Vec3(27.5, H, 5.5), 180.0F, 0.0F);
		// (Part 10 in the story: Maximal lifts the light in the story's ship, not under Darkness.)
		int was = this.context.computeOnClient(client -> ImmersionConfig.get().storyBrightness);

		try {
			this.context.runOnClient(client -> ImmersionConfig.get().storyBrightness = ImmersionConfig.STORY_BRIGHTNESS_MAX);
			check(this.context.computeOnClient(StoryBrightness::liftsNow), "Maximal does not lift the light in the story's ship");
			this.server.runCommand("effect give @a minecraft:darkness 20 0 true");
			this.context.waitTicks(5);
			check(this.context.computeOnClient(client -> !StoryBrightness.liftsNow(client)), "Maximal lifts the light under the Darkness effect");
			this.server.runCommand("effect clear @a minecraft:darkness");
			this.context.waitTicks(5);
			check(this.context.computeOnClient(StoryBrightness::liftsNow), "Maximal does not lift the light again after the Darkness effect");
		} finally {
			this.context.runOnClient(client -> ImmersionConfig.get().storyBrightness = was);
		}

		for (int key : new int[] {GLFW.GLFW_KEY_SPACE, GLFW.GLFW_KEY_ESCAPE}) {
			String name = key == GLFW.GLFW_KEY_SPACE ? "Space" : "Escape";
			int skipped = this.context.computeOnClient(client -> StoryCinema.skippedForTest());
			// (The duel's film: long enough for all the keys, 11 s.)
			this.server.runOnServer(s -> Story.cue(this.session(s), Story.CUE_CINEMA, "duel", "", 400,
					List.of(new Vec3(30.5, H + 1.0, 4.5).add(Vec3.atLowerCornerOf(this.session(s).destroyer)))));

			if (this.film(40) == null) {
				this.problem("the test's film does not begin");
				return;
			}

			this.context.waitTicks(30);
			Vec3 at = this.on(s -> this.me(s).position());
			int slot = this.context.computeOnClient(client -> client.player.getInventory().getSelectedSlot());

			// None of these skip it, and none reaches the game.
			for (int other : new int[] {GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_W,
					GLFW.GLFW_KEY_A, GLFW.GLFW_KEY_S, GLFW.GLFW_KEY_D, GLFW.GLFW_KEY_E, GLFW.GLFW_KEY_3}) {
				this.context.getInput().pressKey(other);
				this.context.waitTicks(2);
				check(this.context.computeOnClient(client -> StoryCinema.filmForTest() != null), "the key " + other + " skips a film (" + name + " round)");
			}

			this.context.getInput().holdKeyFor(GLFW.GLFW_KEY_W, 10);
			this.context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
			this.context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
			this.context.getInput().scroll(2.0);
			this.context.waitTicks(3);
			check(this.context.computeOnClient(client -> StoryCinema.filmForTest() != null), "the mouse skips a film (" + name + " round)");
			check(this.context.computeOnClient(client -> StoryCinema.skippedForTest()) == skipped, "a film was skipped by another key than Space or Escape");
			check(this.on(s -> this.me(s).position().distanceTo(at)) < 0.05, "keys pressed during a film move the player");
			check(this.context.computeOnClient(client -> client.player.getInventory().getSelectedSlot()) == slot, "keys pressed during a film change the hotbar");
			if (key == GLFW.GLFW_KEY_SPACE) {
				// Another key held as the film ends (the inventory's): its repeats are kept from the game.
				this.context.getInput().holdKey(GLFW.GLFW_KEY_E);
				this.context.waitTick();
				check(this.context.computeOnClient(client -> StoryKeys.quietForTest(GLFW.GLFW_KEY_E)), "a key held in a film is not kept from repeating into the game");
			}

			// Now the right key: it skips, and nothing else happens (no jump, no pause menu).
			double y = this.on(s -> this.me(s).getY());
			this.context.getInput().pressKey(key);
			this.context.waitTicks(3);
			check(this.context.computeOnClient(client -> StoryCinema.filmForTest() == null), name + " does not skip a film");
			check(this.context.computeOnClient(client -> StoryCinema.skippedForTest()) == skipped + 1, name + " does not count as a skip");
			double top = y;

			for (int i = 0; i < 10; i++) {
				this.context.waitTick();
				top = Math.max(top, this.on(s -> this.me(s).getY()));
			}

			check(top - y < 0.1, name + " skipping a film also makes the player jump (" + String.format(Locale.ROOT, "%.2f", top - y) + ")");
			check(this.context.computeOnClient(client -> client.gui.screen() == null), name + " skipping a film also opens a screen ("
					+ this.context.computeOnClient(client -> String.valueOf(client.gui.screen())) + ")");

			if (key == GLFW.GLFW_KEY_SPACE) {
				this.context.getInput().releaseKey(GLFW.GLFW_KEY_E);
				this.context.waitTick();
				check(this.context.computeOnClient(client -> !StoryKeys.quietForTest(GLFW.GLFW_KEY_E)), "a key let go after a film is still kept from the game");
			}
		}
	}

	// --- 7, 8, 9: the dialog's last line, and paging with the arrows -----------------------------------------------

	private void dialog() {
		this.enter(4, "m5.cell1");
		this.unseen();
		// Before cell 1's front (a thing to use, under the crosshair), on the floor.
		this.put(new Vec3(27.5, H, 4.2), 180.0F, 5.0F);
		this.context.waitTicks(5);
		String aimed = this.context.computeOnClient(client -> {
			StoryInput.Target target = StoryInput.target(client, 1.0F);
			return target == null ? "" : target.id();
		});
		check("cell1".equals(aimed), "the test does not aim at cell 1's front (" + aimed + ")");

		// Paging: three lines, no answers.
		this.server.runOnServer(s -> this.session(s).talk("after_mira", null));
		this.waitBox(1);
		String first = this.serverLine();
		this.typed();
		this.context.getInput().pressKey(GLFW.GLFW_KEY_RIGHT);
		this.waitLine(first);
		String second = this.serverLine();
		check(!second.isEmpty() && !second.equals(first), "Right does not go on to the next line");
		this.waitBox(2);
		this.typed();
		this.context.getInput().pressKey(GLFW.GLFW_KEY_LEFT);
		this.context.waitTicks(2);
		check(this.reviewing(), "Left does not show the line before");
		check(this.viewedLine().endsWith(first.substring(first.lastIndexOf('.') + 1)), "Left shows another line than the one before (" + this.viewedLine() + ", " + first
				+ ")");
		check(this.serverLine().equals(second), "Left changes the story's line");
		this.context.getInput().pressKey(GLFW.GLFW_KEY_RIGHT);
		this.context.waitTicks(2);
		check(!this.reviewing(), "Right does not go back to the current line");
		check(this.serverLine().equals(second), "Right from an earlier line skips the current one");
		this.context.getInput().pressKey(GLFW.GLFW_KEY_LEFT);
		this.context.waitTicks(2);
		this.context.getInput().pressKey(GLFW.GLFW_KEY_SPACE);
		this.context.waitTicks(2);
		check(!this.reviewing() && this.serverLine().equals(second), "Space on an earlier line does not just go back to the current one");
		this.context.getInput().pressKey(GLFW.GLFW_KEY_RIGHT);
		this.waitLine(second);
		this.waitBox(3);
		this.typed();
		this.context.takeScreenshot("phase31_dialog_last_line");

		// The last line, closed with Space held: no jump, nothing used, no door moved.
		int used = this.context.computeOnClient(client -> StoryInput.usedForTest());
		boolean doors = this.doorsNear();
		double y = this.on(s -> this.me(s).getY());
		this.context.getInput().holdKey(GLFW.GLFW_KEY_SPACE);
		this.waitClosed();
		this.regrab();
		double top = y;

		for (int i = 0; i < 15; i++) {
			this.context.waitTick();
			top = Math.max(top, this.on(s -> this.me(s).getY()));
		}

		check(top - y < 0.1, "Space held from the dialog's last line makes the player jump (" + String.format(Locale.ROOT, "%.2f", top - y) + ")");
		check(!this.context.computeOnClient(client -> client.options.keyJump.isDown()), "Space held from the dialog's last line counts as the jump key held");
		check(this.context.computeOnClient(client -> StoryInput.usedForTest()) == used, "closing the dialog's last line uses the thing under the crosshair");
		check(this.doorsNear() == doors, "closing the dialog's last line moves a door");
		this.context.getInput().releaseKey(GLFW.GLFW_KEY_SPACE);
		this.context.waitTicks(3);
		check(!this.context.computeOnClient(client -> StoryKeys.heldForTest(GLFW.GLFW_KEY_SPACE)), "Space stays the dialog's after it was let go");
		// A fresh press jumps as always.
		this.context.getInput().holdKeyFor(GLFW.GLFW_KEY_SPACE, 4);
		top = y;

		for (int i = 0; i < 12; i++) {
			this.context.waitTick();
			top = Math.max(top, this.on(s -> this.me(s).getY()));
		}

		check(top - y > 0.5, "Space after the dialog does not jump (" + String.format(Locale.ROOT, "%.2f", top - y) + ")");
		this.context.waitTicks(10);

		// The same with Enter and with a click: nothing used, no jump.
		for (int way = 0; way < 2; way++) {
			String how = way == 0 ? "Enter" : "a click";
			this.server.runOnServer(s -> this.session(s).talk("kestrel_idle", null));
			this.waitBox(1);
			this.typed();
			int before = this.context.computeOnClient(client -> StoryInput.usedForTest());

			if (way == 0) {
				this.context.getInput().holdKey(GLFW.GLFW_KEY_ENTER);
			} else {
				this.context.getInput().setCursorPos(20.0, 20.0);
				this.context.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
			}

			this.waitClosed();
			this.regrab();
			this.context.waitTicks(10);
			check(this.context.computeOnClient(client -> StoryInput.usedForTest()) == before, how + " on the last line uses the thing under the crosshair");
			check(!this.context.computeOnClient(client -> client.options.keyJump.isDown()), how + " on the last line counts as the jump key held");

			if (way == 0) {
				this.context.getInput().releaseKey(GLFW.GLFW_KEY_ENTER);
			} else {
				this.context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
			}

			this.context.waitTicks(3);
		}
	}

	/** Lets the line in the box be fully shown (as a key would). */
	private void typed() {
		this.context.runOnClient(client -> {
			if (client.gui.screen() instanceof DialogScreen box) {
				box.finishForTest();
			}
		});
		this.context.waitTick();
	}

	private boolean reviewing() {
		return this.context.computeOnClient(client -> client.gui.screen() instanceof DialogScreen box && box.reviewingForTest());
	}

	private String viewedLine() {
		return this.context.computeOnClient(client -> client.gui.screen() instanceof DialogScreen box ? box.viewedLineForTest() : "");
	}

	private String serverLine() {
		return this.on(s -> StoryTesting.line(this.session(s)));
	}

	/** Waits for the box to have shown that many lines. */
	private void waitBox(int lines) {
		for (int i = 0; i < 40; i++) {
			if (this.context.computeOnClient(client -> client.gui.screen() instanceof DialogScreen && DialogScreen.seenForTest() >= lines)) {
				return;
			}

			this.context.waitTick();
		}

		this.problem("the dialog box does not show line " + lines);
	}

	private void waitLine(String was) {
		for (int i = 0; i < 20 && this.serverLine().equals(was); i++) {
			this.context.waitTick();
		}
	}

	private void waitClosed() {
		for (int i = 0; i < 30; i++) {
			if (this.on(s -> this.session(s).dialog == null) && this.context.computeOnClient(client -> client.gui.screen() == null)) {
				return;
			}

			this.context.waitTick();
		}

		this.problem("the dialog does not close on its last line");
	}

	/**
	 * What Minecraft does as the mouse is caught again after a screen (it reads the keys held anew);
	 * the test's window may not have the focus, when Minecraft leaves that out.
	 */
	private void regrab() {
		this.context.runOnClient(client -> {
			SaberCraft.LOGGER.info("PHASE31 window active: {}", client.isWindowActive());
			KeyMapping.setAll();
		});
	}

	/** Which of the Star Destroyer's doors near the player are open (a bit each). */
	private boolean doorsNear() {
		return this.on(s -> {
			StorySession session = this.session(s);
			Vec3 me = session.local(Story.DESTROYER);
			boolean any = false;

			for (var door : DestroyerInterior.DOORS) {
				if (new Vec3(door.x0(), door.y0(), door.z0()).distanceTo(me) < 10.0) {
					any |= InteriorSystems.doorOpenForTest(this.level(s), session.destroyer, door);
				}
			}

			return any;
		});
	}

	// --- 1, 2: the boss caught in her second phase, the fight begun again ---------------------------------------------

	private void boss() {
		if (!this.duel()) {
			return;
		}

		int resets = this.on(s -> StoryTesting.fightResets());
		List<String> fresh = this.on(s -> StoryTesting.fightLeftovers(this.session(s), this.me(s), true));
		check(fresh.isEmpty(), "a fresh duel has leftovers already: " + fresh);

		for (int round = 1; round <= 2; round++) {
			int number = round;
			// Her second phase, both blades, a while of fighting.
			this.server.runOnServer(s -> StoryTesting.halfStrength(this.session(s), this.her(s), this.me(s)));
			this.film(30);
			this.context.waitTicks(5);
			this.skip("boss_mid");
			this.context.waitTicks(30);

			if (!this.on(s -> StoryTesting.phase(this.her(s)) == 2)) {
				this.problem("her second phase does not begin (round " + number + ")");
				return;
			}

			check(this.context.computeOnClient(client -> StoryClient.duelPhaseForTest()) == 2, "the client is not in her second phase (round " + number + ")");
			int id = this.on(s -> this.her(s).id);
			check(this.context.computeOnClient(client -> MeshAudit.twinProgress(id)) > 0.5F, "her staff's second blade is not out in her second phase (round " + number + ")");
			this.context.waitTicks(round == 1 ? 60 : 160);
			// Caught: down in the fight. In the second round a stroke of the player arrives before the
			// next tick: it is not played out against the old fight, the capture comes first.
			int captures = this.on(s -> Story.capturesForTest());
			String early = this.on(s -> {
				ServerPlayer me = this.me(s);
				StoryTesting.lethal(me);
				me.hurtServer((ServerLevel) me.level(), me.damageSources().generic(), 40.0F);

				if (number == 1) {
					return "";
				}

				if (!StoryTesting.captureDue(this.session(s))) {
					return "struck down, no capture is due";
				}

				StoryTesting.action(me, StoryActionPayload.STRIKE);
				return StoryTesting.captureDue(this.session(s)) || Story.capturesForTest() == captures ? "a stroke right after being struck down is handled before the capture" : "";
			});
			check(early.isEmpty(), early + " (round " + number + ")");

			for (int i = 0; i < 60 && this.on(s -> Story.capturesForTest()) == captures; i++) {
				this.context.waitTick();
			}

			check(this.on(s -> Story.capturesForTest()) > captures, "killed in her second phase, the player is not put back (round " + number + ")");
			this.context.waitTicks(10);
			check(this.on(s -> this.me(s).isAlive() && this.me(s).getHealth() > 10.0F), "the player is not alive and well after being put back (round " + number + ")");
			this.server.runCommand("effect give @a minecraft:resistance 100000 4 true");
			this.skipFilms(300);

			if (!this.on(s -> this.session(s).has("tmp.duel"))) {
				this.problem("the duel does not begin again after being caught (round " + number + ")");
				return;
			}

			this.converse();
			this.context.waitTicks(5);
			List<String> left = this.on(s -> StoryTesting.fightLeftovers(this.session(s), this.me(s), true));
			check(left.isEmpty(), "the duel begun again is not as at its start (round " + number + "): " + left);
			check(this.on(s -> StoryTesting.fightResets()) > resets, "the fight was not reset (round " + number + ")");
			check(this.context.computeOnClient(client -> StoryClient.duelPhaseForTest()) == 1, "the client still shows her second phase (round " + number + ")");
			int again = this.on(s -> this.her(s).id);
			this.context.waitTicks(10);
			check(this.context.computeOnClient(client -> MeshAudit.twinProgress(again)) < 0.05F, "her second blade shows in the duel begun again (round " + number + ")");
			// Her first phase behaves as it should: her moves of the first phase only, for a while.
			this.context.waitTicks(80);
			check(this.on(s -> StoryTesting.phase(this.her(s)) == 1 && !this.session(s).has("tmp.boss_fierce")), "her second phase comes back by itself (round " + number + ")");
			this.context.takeScreenshot("phase31_boss_again_" + number);
		}
	}

	/** The duel begun: the story at it, the films and her words skipped, the player with a saber and unhurt. */
	private boolean duel() {
		this.enter(5, "m6.duel");
		this.flags("saber.taken", "droid.m31", "droid.r2v7");
		this.server.runOnServer(s -> Story.updateDroids(this.session(s)));
		this.server.runCommand("effect give @a minecraft:resistance 100000 4 true");
		this.server.runCommand("item replace entity @a weapon.mainhand with "
				+ "minecraft:netherite_sword[minecraft:custom_model_data={strings:[\"sabercraft:standard\",\"sabercraft:blue\"]}]");
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

	// --- 12: the takedown's tip in the prison ----------------------------------------------------------------------

	private void tip() {
		this.forgetTakedowns();
		this.enter(4, "m5.r2");
		this.server.runOnServer(s -> StoryTesting.unseen(this.session(s), 0));
		this.context.waitTicks(40);
		int tips = this.on(s -> StoryTesting.takedownTips());
		int shown = this.context.computeOnClient(client -> Hints.storyTipsForTest());
		check(!this.on(s -> this.session(s).has(StoryTesting.knowTakedown())), "the takedown is known before its tip");
		// Behind the guard (behind him in both his looks, and as he turns), a few steps away.
		Vec3 spot = this.on(s -> {
			Figure guard = this.session(s).figure("det_guard");

			for (Vec3 at : List.of(new Vec3(35.4, H, 9.4), new Vec3(35.2, H, 9.5), new Vec3(35.5, H, 9.0), new Vec3(35.0, H, 9.6))) {
				if (guard != null && this.free(s, at)) {
					return at;
				}
			}

			return null;
		});

		if (spot == null) {
			this.problem("no free place behind the detention block's guard for the tip");
			return;
		}

		// Not while a dialog is open.
		this.server.runOnServer(s -> this.session(s).talk("kestrel_idle", null));
		this.put(spot, 180.0F, 10.0F);
		this.context.waitTicks(15);
		check(this.on(s -> StoryTesting.takedownTips()) == tips, "the takedown's tip comes while a dialog is open");
		this.converse();
		this.server.runOnServer(s -> StoryTesting.unseen(this.session(s), 0));

		for (int i = 0; i < 40 && this.on(s -> StoryTesting.takedownTips()) == tips; i++) {
			this.context.waitTick();
		}

		check(this.on(s -> StoryTesting.takedownTips()) == tips + 1, "no takedown tip behind the detention block's guard (seen: "
				+ this.on(s -> String.format(Locale.ROOT, "%.2f", StoryTesting.suspicion(this.session(s)))) + ")");
		check(this.on(s -> this.session(s).has(StoryTesting.knowTakedown()) && this.session(s).progress.flags.contains(StoryTesting.knowTakedown())),
				"the takedown is not known (and kept) after its tip");
		this.context.waitTicks(4);
		check(this.context.computeOnClient(client -> Hints.storyTipsForTest()) == shown + 1, "the client does not show the takedown's tip");
		String words = this.context.computeOnClient(client -> Hints.lastTipForTest());
		check(!words.contains("sabercraft.") && words.length() > 40, "the tip's words are missing (" + words + ")");
		this.context.waitTicks(10);
		this.context.takeScreenshot("phase31_takedown_tip");
		// Once only.
		this.context.waitTicks(30);
		check(this.on(s -> StoryTesting.takedownTips()) == tips + 1, "the takedown's tip comes again");
	}

	// --- 13: the takedown on another unaware stormtrooper ------------------------------------------------------------

	private void takedown() {
		this.forgetTakedowns();
		this.enter(4, "m5.route");
		this.unseen();
		// Before the tip (and before any takedown), nobody can be overpowered here.
		check(this.on(s -> this.session(s).figures.stream().noneMatch(f -> StoryTesting.takedownable(this.session(s), f))),
				"a stormtrooper can be overpowered before the takedown is known");
		this.flags(StoryTesting.knowTakedown());
		// The lobby's patrol: held where he is for the test (so the client's aim stays on him).
		Vec3 back = this.on(s -> {
			Figure f = this.session(s).figure("m5_lobby");

			if (f == null) {
				return null;
			}

			return this.holdWithRoomBehind(s, f, 1.4);
		});

		if (back == null) {
			this.problem("no lobby patrol or no free place behind him");
			return;
		}

		Vec3 him = this.on(s -> this.session(s).figure("m5_lobby").position());
		check(this.on(s -> StoryTesting.takedownable(this.session(s), this.session(s).figure("m5_lobby"))), "the lobby's patrol cannot be overpowered once it is known");
		this.put(back, yaw(back, him), pitch(back.add(0.0, 1.62, 0.0), him.add(0.0, 1.0, 0.0)));
		this.context.waitTicks(6);
		check(this.on(s -> StoryTesting.takedownReady(this.session(s), this.session(s).figure("m5_lobby"))), "close behind him, he cannot be overpowered");
		int id = this.on(s -> this.session(s).figure("m5_lobby").id);
		check(this.context.computeOnClient(client -> {
			StoryInput.Target target = StoryInput.target(client, 1.0F);
			return target != null && target.takedown() && target.figure() == id;
		}), "under the crosshair he is not one to overpower (" + this.context.computeOnClient(client -> StoryInput.explainForTest(client)) + ")");
		String takedownWords = this.context.computeOnClient(client -> Component.translatable("sabercraft.story.point.takedown").getString());
		String standing = this.context.computeOnClient(client -> StoryHud.promptForTest(client));
		check(standing != null && standing.contains(takedownWords) && standing.contains(this.context.computeOnClient(client ->
				client.options.keyShift.getTranslatedKeyMessage().getString())), "standing behind him, the prompt does not say to crouch (" + standing + ")");
		// Standing: refused (he hears one coming), nothing else happens.
		int takedowns = this.on(s -> StoryTesting.takedowns());
		this.context.runOnClient(client -> StoryInput.use(client));
		this.context.waitTicks(4);
		check(this.on(s -> StoryTesting.takedowns()) == takedowns, "standing, the takedown works");
		check("sabercraft.story.hint.takedown_crouch".equals(this.context.computeOnClient(client -> StoryClient.hintForTest())),
				"standing, no hint to crouch (" + this.context.computeOnClient(client -> StoryClient.hintForTest()) + ")");
		check(this.on(s -> this.session(s).alarm == 0 && this.session(s).figure("m5_lobby").stage < Figure.ALERTED), "a refused takedown raised the alarm");
		// Crouching: the prompt, the use key, the scene.
		this.context.getInput().holdKey(options -> options.keyShift);
		this.context.waitTicks(6);
		String crouched = this.context.computeOnClient(client -> StoryHud.promptForTest(client));
		check(crouched != null && crouched.contains(takedownWords), "crouching behind him, no takedown prompt (" + crouched + ")");
		this.context.takeScreenshot("phase31_takedown_prompt");
		this.context.runOnClient(client -> StoryInput.use(client));
		this.context.waitTicks(3);
		this.context.getInput().releaseKey(options -> options.keyShift);
		check(this.on(s -> StoryTesting.takedowns()) == takedowns + 1, "crouching close behind him, the takedown does not work");
		check("takedown".equals(this.film(20)), "the takedown's scene does not play");
		this.context.waitTicks(30);
		this.context.takeScreenshot("phase31_takedown_scene");
		this.skip("takedown");
		this.context.waitTicks(5);
		Vec3 lies = this.on(s -> this.session(s).figure("m5_lobby").position());
		check(this.on(s -> this.session(s).figure("m5_lobby").mode == Figure.DOWN), "the patrol is not down after the scene");
		check(this.on(s -> this.session(s).alarm == 0), "the takedown raised the alarm");
		check(this.on(s -> this.session(s).flags.stream().anyMatch(flag -> flag.startsWith(StoryTesting.downPrefix() + "m5/m5_lobby/"))),
				"where he lies is not remembered");
		// Kept as the figures are made again (a step, a checkpoint): he lies where he fell.
		this.server.runOnServer(s -> {
			StorySession session = this.session(s);
			session.checkpoint(Story.DESTROYER, session.local(Story.DESTROYER), 0.0F);
		});
		this.server.runOnServer(s -> StoryTesting.capture(this.session(s)));
		this.context.waitTicks(30);
		this.unseen();
		check(this.on(s -> {
			Figure f = this.session(s).figure("m5_lobby");
			return f != null && f.mode == Figure.DOWN && f.position().distanceTo(lies) < 0.3;
		}), "after being put back at a checkpoint the patrol is up again or elsewhere");
		this.context.waitTicks(60);
		check(this.on(s -> {
			Figure f = this.session(s).figure("m5_lobby");
			return f != null && f.mode == Figure.DOWN && f.position().distanceTo(lies) < 0.3 && !f.observer;
		}), "the patrol gets up again or moves after the takedown");
	}

	// --- 14: not the alerted, not with a witness, not anyone else ----------------------------------------------------

	private void refuse() {
		this.forgetTakedowns();
		this.enter(4, "m5.route");
		this.unseen();
		this.flags(StoryTesting.knowTakedown());
		String why = this.on(s -> {
			StorySession session = this.session(s);
			Figure f = session.figure("m5_lobby");

			if (f == null) {
				return "no lobby patrol";
			}

			Vec3 back = this.holdWithRoomBehind(s, f, 1.3);

			if (back == null) {
				return "no place behind him";
			}

			this.teleport(s, session, back, yaw(back, f.position()));
			List<String> wrong = new ArrayList<>();
			int count = StoryTesting.takedowns();
			// Alerted (he looks for the player): no.
			f.stage = Figure.ALERTED;
			f.suspicion = 0.7F;
			f.mode = Figure.INVESTIGATE;

			if (StoryTesting.takedownReady(session, f) || StoryTesting.takedown(session, f) || StoryTesting.takedowns() != count) {
				wrong.add("alerted");
			}

			// Calm again, but he saw the player a moment ago: no.
			f.stage = Figure.CALM;
			f.suspicion = 0.0F;
			f.mode = Figure.ROUTINE;
			f.lastSeen = session.time() - 5;

			if (StoryTesting.takedownReady(session, f) || StoryTesting.takedown(session, f)) {
				wrong.add("just seen");
			}

			// A while ago: yes (the prompt) ...
			f.lastSeen = session.time() - 200;

			if (!StoryTesting.takedownReady(session, f)) {
				wrong.add("unaware but not ready");
			}

			// ... unless someone else sees the player right now.
			Figure other = session.figures.stream().filter(o -> o != f && o.observer && o.mode != Figure.DOWN).findFirst().orElse(null);

			if (other != null) {
				long was = other.lastSeen;
				other.lastSeen = session.time();
				StoryTesting.crouch(this.me(s), true);

				if (StoryTesting.takedown(session, f)) {
					wrong.add("witnessed");
				}

				StoryTesting.crouch(this.me(s), false);
				other.lastSeen = was;
			}

			// During an alarm: no.
			session.alarm = 100;

			if (StoryTesting.takedownReady(session, f)) {
				wrong.add("alarm");
			}

			session.alarm = 0;
			return wrong.isEmpty() ? null : "refused wrongly: " + wrong;
		});
		check(why == null, "the takedown's rules: " + why);
		this.context.waitTicks(5);

		// Only stormtroopers who are not needed standing, not fighting, not talking.
		this.enter(5, "m6.door");
		this.unseen();
		this.everyoneSuitable("m6.door");
		check(this.on(s -> {
			Figure tech = this.session(s).figure(StoryTesting.catwalkKey());
			return tech == null || !StoryTesting.takedownable(this.session(s), tech);
		}), "the catwalk's technician can be overpowered");
		this.enter(5, "m6.escape1");
		this.context.waitTicks(10);
		check(this.on(s -> this.session(s).figures.stream().noneMatch(f -> StoryTesting.takedownable(this.session(s), f))), "a fighter of the finale can be overpowered");
		// Before Mission 5, without the tip: nobody.
		this.forgetTakedowns();
		this.enter(3, "m4.lift");
		this.unseen();
		check(this.on(s -> this.session(s).figures.stream().noneMatch(f -> StoryTesting.takedownable(this.session(s), f))),
				"someone can be overpowered in Mission 4 (before the takedown is known)");
	}

	private void everyoneSuitable(String where) {
		String wrong = this.on(s -> {
			StorySession session = this.session(s);

			for (Figure f : session.figures) {
				if (StoryTesting.takedownable(session, f) && (!Crew.TROOPER.equals(f.kind()) || f.hostile || f.talks || List.of("maint", "trap1", "checkpoint", "svc_guard")
						.contains(f.key))) {
					return f.key + " (" + f.kind() + ")";
				}
			}

			return null;
		});
		check(wrong == null, "at " + where + " someone not suitable can be overpowered: " + wrong);
	}

	// --- 11, 15, 16: the prison ---------------------------------------------------------------------------------------

	private void prison() {
		this.forgetTakedowns();
		this.enter(4, "m5.route", "m5.trapped", "cell2.open", "droid.r2v7", "cell1.open", "cell3.open", "kestrel.free", "hatch.f", "opt.prisoner", "gone.prisoner",
				"trust.hale");
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
			this.problem("Hale's trap does not put the player in the cell (cannot test the prison)");
			return;
		}

		for (int i = 0; i < 60 && this.on(s -> this.session(s).figure("hale") != null || this.session(s).figure("trap1") != null); i++) {
			this.context.waitTicks(5);
		}

		check(this.on(s -> StoryTesting.atCheckpoint(this.session(s)) && this.session(s).progress.flags.contains("m5.trapped")), "no checkpoint in Hale's cell");
		int checkpoints = this.on(s -> StoryTesting.checkpoints(this.session(s)));

		// Out of the cell, caught at once: back in it, as it was.
		this.server.runOnServer(s -> StoryTesting.openCell(this.session(s)));
		this.put(new Vec3(31.5, H, 4.4), 180.0F, 0.0F);
		check(this.on(s -> StoryTesting.prisonBreak(this.session(s))), "out of the cell, the break-out is not under way");

		this.holdPatrol();
		// The main door: from inside it opens, and shuts behind one.
		this.door(new Vec3(34.6, H, 5.0), 270.0F, true, "from inside");
		this.put(new Vec3(37.6, H, 5.0), 270.0F, 0.0F);
		this.put(new Vec3(41.0, H, 1.0), 270.0F, 0.0F);
		this.context.waitTicks(40);
		check(!this.detentionDoor(), "the detention block's door stays open behind the player");
		this.door(new Vec3(37.4, H, 5.0), 90.0F, true, "from outside (back in)");
		this.put(new Vec3(31.5, H, 4.4), 180.0F, 0.0F);
		this.context.waitTicks(40);
		check(!this.detentionDoor(), "the detention block's door stays open once the player is back inside");

		// Seen by the guard: caught, back in the cell.
		this.caught("seen right after the break-out", true);
		check(this.on(s -> StoryTesting.checkpoints(this.session(s))) == checkpoints, "a checkpoint was made during the break-out");

		// Out again, on through the block (R2-V7 freed, on to Kestrel's cell), then caught: back in the cell.
		this.server.runOnServer(s -> StoryTesting.openCell(this.session(s)));
		this.put(new Vec3(31.5, H, 4.4), 180.0F, 0.0F);
		this.server.runOnServer(s -> StoryTesting.finishStep(this.session(s)));
		this.waitStep("m5.r2", 40);
		this.server.runOnServer(s -> {
			this.session(s).set("droid.r2v7");
			StoryTesting.finishStep(this.session(s));
		});
		this.waitStep("m5.cell1", 40);
		this.context.waitTicks(20);
		check(this.on(s -> StoryTesting.checkpoints(this.session(s))) == checkpoints, "freeing R2-V7 made a checkpoint outside the cell");
		this.caught("after freeing R2-V7", false);
		check(this.on(s -> !this.session(s).has("droid.r2v7")), "R2-V7 counts as freed after being put back into the cell");

		// The whole break-out once more, to its end: the steps go on, and after it a checkpoint again.
		this.server.runOnServer(s -> StoryTesting.openCell(this.session(s)));
		this.put(new Vec3(31.5, H, 4.4), 180.0F, 0.0F);
		String[] steps = {"m5.r2", "m5.cell1", "m5.back"};

		for (String step : steps) {
			this.server.runOnServer(s -> StoryTesting.finishStep(this.session(s)));
			this.waitStep(step, 40);
		}

		this.server.runOnServer(s -> StoryTesting.finishStep(this.session(s)));
		this.context.waitTicks(20);
		String after = this.on(s -> StoryTesting.step(this.session(s)));
		check(!after.equals("m5.back") && !after.startsWith("m5.cell") && !after.equals("m5.r2"), "the break-out does not end (at " + after + ")");
		check(this.on(s -> !StoryTesting.prisonBreak(this.session(s))), "the cell's checkpoint is still held after the break-out (" + after + ")");
		this.context.takeScreenshot("phase31_prison_done");
		// From the lobby the door does not open for the player, not even right at its face (only from
		// inside, and back in during the break-out).
		this.holdPatrol();
		this.put(new Vec3(41.0, H, 1.0), 270.0F, 0.0F);
		this.context.waitTicks(40);
		this.door(new Vec3(37.3, H, 5.0), 90.0F, false, "from the lobby after the break-out (" + after + ")");
	}

	/** The lobby's patrol held off at the far side of the lobby (he would open the door himself). */
	private void holdPatrol() {
		this.server.runOnServer(s -> {
			Figure f = this.session(s).figure("m5_lobby");

			if (f != null) {
				f.crew = Crew.at(Crew.TROOPER, Crew.STAND, 41.5, H, -1.0, 0.0F);
				f.x = 41.5;
				f.z = -1.0;
				f.way = List.of();
			}
		});
	}

	/** Puts the player before the detention block's main door (facing it) and checks it opens (or not). */
	private void door(Vec3 at, float yaw, boolean opens, String how) {
		this.put(at, yaw, 0.0F);
		boolean open = false;

		for (int i = 0; i < 40 && !open; i++) {
			this.context.waitTick();
			open = this.detentionDoor();
		}

		check(open == opens, "the detention block's door " + (opens ? "does not open" : "opens") + " " + how);

		if (open) {
			this.context.takeScreenshot("phase31_detention_door_" + how.replace(' ', '_').replace("(", "").replace(")", ""));
		}
	}

	private boolean detentionDoor() {
		return this.on(s -> InteriorSystems.doorOpenForTest(this.level(s), this.session(s).destroyer, DestroyerInterior.DOORS.get(DestroyerInterior.DOOR_DETENTION)));
	}

	/** Caught (by the guard's eyes, or as the story's capture), and checks the cell as it was after the trap. */
	private void caught(String when, boolean seen) {
		int captures = this.on(s -> Story.capturesForTest());

		if (seen) {
			// Right before the guard, in his look: he sees the player, the chase, the capture.
			this.server.runOnServer(s -> {
				StorySession session = this.session(s);
				StoryTesting.unseen(session, 0);
				Figure guard = session.figure("det_guard");
				Vec3 front = guard.position().add(guard.look().scale(1.6));
				this.teleport(s, session, front, yaw(front, guard.position()));
			});

			for (int i = 0; i < 400 && this.on(s -> Story.capturesForTest()) == captures; i++) {
				this.context.waitTick();
			}
		} else {
			this.server.runOnServer(s -> StoryTesting.capture(this.session(s)));
		}

		this.context.waitTicks(30);
		check(this.on(s -> Story.capturesForTest()) > captures, "caught " + when + ": no capture");
		check("m5.cell".equals(this.on(s -> StoryTesting.step(this.session(s)))), "caught " + when + ": the step is " + this.on(s -> StoryTesting.step(this.session(s))));
		check(this.on(s -> this.session(s).has("m5.trapped") && !this.session(s).has("cell2.open")), "caught " + when + ": the trap is not as it was");
		check(this.on(s -> this.session(s).local(Story.DESTROYER).z < DestroyerInterior.CELL_FRONT), "caught " + when + ": not back in the cell");
		check(this.on(s -> this.level(s).getBlockState(this.session(s).destroyer.offset(31, H, DestroyerInterior.CELL_FRONT)).is(Blocks.BARRIER)),
				"caught " + when + ": the cell's field is off");
		check(this.on(s -> {
			Figure guard = this.session(s).figure("det_guard");
			return guard != null && guard.mode != Figure.DOWN && guard.mode != Figure.CHASE && guard.position().distanceTo(new Vec3(34.5, H, 7.6)) < 1.5;
		}), "caught " + when + ": the guard is not at his post");
		check(this.on(s -> this.session(s).alarm == 0), "caught " + when + ": the alarm still runs");
		check(this.on(s -> this.session(s).mission == 4 && this.session(s).has("droid.m31")), "caught " + when + ": progress from before the prison is lost");
		this.unseen();
	}

	// --- 6: the Falcon's jump of the story --------------------------------------------------------------------------

	private void falcon() {
		if (this.on(s -> this.session(s) == null)) {
			this.enter(5, "m6.door");
		}

		BlockPos falcon = this.on(s -> this.session(s).falcon);
		int jumps = this.on(s -> InteriorJump.jumpsForTest());
		check(this.on(s -> InteriorJump.story(s.getLevel(ShipInteriors.DIMENSION), falcon)), "the story's jump of the Falcon does not start");
		check(this.on(s -> InteriorJump.jumpsForTest()) == jumps + 1, "the story's jump is not counted");
		check(this.on(s -> InteriorJump.running(falcon)), "the story's jump does not run");
		check(!this.on(s -> InteriorJump.story(s.getLevel(ShipInteriors.DIMENSION), falcon)), "a second jump starts while one runs");

		for (int i = 0; i < 260 && this.on(s -> InteriorJump.running(falcon)); i++) {
			this.context.waitTick();
		}

		check(!this.on(s -> InteriorJump.running(falcon)), "the story's jump does not end");
	}

	// --- Restart right after entering, and out by a command: the way back stays --------------------------------

	private void restart() {
		if (this.on(s -> this.session(s) != null)) {
			this.leave();
		}

		Vec3 home = this.on(s -> this.me(s).position());
		// Entered (nothing written since: no checkpoint yet), then Restart at once from the menu.
		this.enter(5, "m6.door");
		this.context.runOnClient(client -> StoryClient.send(StoryActionPayload.RESTART, "", 0));
		boolean restarted = false;

		for (int i = 0; i < 200 && !restarted; i++) {
			this.context.waitTick();
			restarted = this.on(s -> this.session(s) != null && StoryTesting.step(this.session(s)).startsWith("m1."));
		}

		check(restarted, "the story does not start anew after Restart");
		this.leave();
		check(this.on(s -> this.me(s).gameMode.getGameModeForPlayer() == GameType.SURVIVAL), "restarting right after entering loses the game mode (now "
				+ this.on(s -> this.me(s).gameMode.getGameModeForPlayer().getName()) + ")");
		check(this.on(s -> this.me(s).level().dimension() == net.minecraft.world.level.Level.OVERWORLD && this.me(s).position().distanceTo(home) < 2.0),
				"restarting right after entering loses the way back (now at " + this.on(s -> this.me(s).position()) + ", came from " + home + ")");

		// Out of the story's ship by a command: the story ends, and things are given back.
		this.enter(5, "m6.door");
		this.server.runCommand(String.format(Locale.ROOT, "execute in minecraft:overworld run tp @a %.2f %.2f %.2f", home.x, home.y, home.z));

		for (int i = 0; i < 40 && this.on(s -> Story.playing(this.me(s))); i++) {
			this.context.waitTick();
		}

		this.context.waitTicks(5);
		check(this.on(s -> !Story.playing(this.me(s))), "out of the story's ship by a command, still in the story");
		check(this.on(s -> this.me(s).gameMode.getGameModeForPlayer() == GameType.SURVIVAL), "out of the story's ship by a command, the game mode does not come back");
		check(this.on(s -> !StoryTesting.fileActive(s, this.self)), "out of the story's ship by a command, the save still says the story runs");
	}

	// --- Helpers ------------------------------------------------------------------------------------------------------

	/**
	 * Holds the patrol standing where he is for the test and gives a place behind him. He walks a
	 * beat round the lobby: caught just after turning at one of its corners, his back is to the wall
	 * and there is no room behind him (seen on CI, where the test reached him at another moment of
	 * his walk). Then he is turned on the spot, a quarter at a time, until there is.
	 */
	private @Nullable Vec3 holdWithRoomBehind(MinecraftServer s, @Nullable Figure f, double distance) {
		if (f == null) {
			return null;
		}

		f.speed = 0.0F;
		f.way = List.of();
		float yaw = f.yaw;

		for (int turn = 0; turn < 4; turn++) {
			float facing = yaw + turn * 90.0F;
			f.yaw = facing;
			f.headYaw = facing;
			f.crew = Crew.at(Crew.TROOPER, Crew.STAND, f.x, f.y, f.z, facing);
			Vec3 back = this.behindOf(s, f, distance);

			if (back != null) {
				return back;
			}
		}

		return null;
	}

	/** A free place behind one of the story's people (that far, or a little nearer or to the side), or null. */
	private @Nullable Vec3 behindOf(MinecraftServer s, @Nullable Figure f, double distance) {
		if (f == null) {
			return null;
		}

		Vec3 look = f.look();
		Vec3 flat = new Vec3(look.x, 0.0, look.z).normalize();

		for (double d : new double[] {distance, distance - 0.3, distance - 0.5}) {
			for (double turn : new double[] {0.0, 25.0, -25.0}) {
				double a = Math.toRadians(turn);
				Vec3 dir = new Vec3(flat.x * Math.cos(a) - flat.z * Math.sin(a), 0.0, flat.x * Math.sin(a) + flat.z * Math.cos(a));
				Vec3 at = f.position().subtract(dir.scale(d));

				if (this.free(s, at)) {
					return at;
				}
			}
		}

		return null;
	}

	/** Whether the player fits standing at that place (plot space), with floor under it. */
	private boolean free(MinecraftServer s, Vec3 local) {
		Vec3 world = local.add(Vec3.atLowerCornerOf(this.session(s).destroyer));
		return this.level(s).noCollision(new AABB(world.x - 0.3, world.y, world.z - 0.3, world.x + 0.3, world.y + 1.8, world.z + 0.3))
				&& !this.level(s).noCollision(new AABB(world.x - 0.3, world.y - 0.2, world.z - 0.3, world.x + 0.3, world.y - 0.01, world.z + 0.3));
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

	/**
	 * The takedown as never learnt, in the saved file: out of the story first (leaving saves the
	 * session's flags, which would bring it back).
	 */
	private void forgetTakedowns() {
		if (this.on(s -> this.session(s) != null)) {
			this.leave();
		}

		this.server.runOnServer(s -> StoryTesting.forgetTakedowns(s, this.self));
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
		player.teleportTo(server.getLevel(ShipInteriors.DIMENSION), world.x, world.y, world.z, java.util.Set.of(), yaw, 0.0F, true);
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
		SaberCraft.LOGGER.error("PHASE31 CHECK FAILED: {}", problem);
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
