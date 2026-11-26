package com.phiro.sabercraft.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.client.Hints;
import com.phiro.sabercraft.client.ImmersionConfig;
import com.phiro.sabercraft.client.SaberCraftClient;
import com.phiro.sabercraft.client.interior.HangarFalcon;
import com.phiro.sabercraft.client.interior.InteriorClient;
import com.phiro.sabercraft.client.story.DialogScreen;
import com.phiro.sabercraft.client.story.StoryCinema;
import com.phiro.sabercraft.client.story.StoryClient;
import com.phiro.sabercraft.interior.Crew;
import com.phiro.sabercraft.interior.DestroyerInterior;
import com.phiro.sabercraft.interior.ShipInteriors;
import com.phiro.sabercraft.interior.Vents;
import com.phiro.sabercraft.network.StoryActionPayload;
import com.phiro.sabercraft.ship.ShipEntity;
import com.phiro.sabercraft.ship.Starships;
import com.phiro.sabercraft.story.Chapter1;
import com.phiro.sabercraft.story.Figure;
import com.phiro.sabercraft.story.Story;
import com.phiro.sabercraft.story.StoryDifficulty;
import com.phiro.sabercraft.story.StorySession;
import com.phiro.sabercraft.story.StoryTesting;

/**
 * Phase 27: the story understandable, friendly and reliable, after Phiro's playtest with two
 * testers. The parts a test can play in the real game (the feel stays for the manual test list in
 * {@code docs/development/phase-27-status.md}):
 *
 * <ol>
 * <li>The menu key is I (no longer V, Simple Voice Chat's), moved once for a player who had V (10).</li>
 * <li>The brightening of the story's darkness is a setting kept with the others, off by default (9).</li>
 * <li>The difficulty: chosen per player, kept in the save, more or fewer enemies (12).</li>
 * <li>Checkpoints: none in a hatch, one deep in a crawl way, the player back there crawling; an old
 * save's place in a hatch is not trusted; time to get one's bearings after a restore (1).</li>
 * <li>The marker leads on to the hideout from the crawl way (5).</li>
 * <li>Mission 2: the officer's round, the lure that works, the checkpoint with the stick, the marker
 * home, the second hatch in the hangar, one click and walking in, crawling faster (6, 7, 11).</li>
 * <li>A dialog closes when one is seen, and comes back after a film that only hid it (4, 8).</li>
 * <li>The detention block's people are there before its door opens (2).</li>
 * <li>The respawn by the cell console is not watched (1).</li>
 * <li>The bridge's lift is sealed off by a barrier one can see (16).</li>
 * <li>The lab's guard: his post west of the door, his round of looks, he sees, fires, hears M-31
 * and comes back, and is overpowered from behind without the player put into the console (15).</li>
 * <li>The Falcon waits in its place when the story is loaded at the escape (14).</li>
 * </ol>
 */
public class Phase27GameTest implements FabricClientGameTest {
	private static final int H = DestroyerInterior.HANGAR + 1;
	private static final int R = DestroyerInterior.REACTOR_BOTTOM;

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
			this.problems.forEach(problem -> SaberCraft.LOGGER.error("PHASE27 PROBLEM: {}", problem));
			throw new AssertionError("Phase 27: " + this.problems.size() + " problem(s): " + String.join(" | ", this.problems));
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

			this.part("keys", this::keys);
			this.part("brightness", this::brightness);
			this.part("shipDamage", this::shipDamage);
			this.part("difficulty", this::difficulty);
			this.part("checkpoints", this::checkpoints);
			this.part("oldSave", this::oldSave);
			this.part("ventMarker", this::ventMarker);
			this.part("mission2", this::mission2);
			this.part("interrupt", this::interrupt);
			this.part("popin", this::popin);
			this.part("consoleSafe", this::consoleSafe);
			this.part("bridge", this::bridge);
			this.part("labGuard", this::labGuard);
			this.part("falcon", this::falcon);
		} catch (AssertionError | RuntimeException e) {
			this.problem("aborted: " + e);
			SaberCraft.LOGGER.error("PHASE27 aborted", e);
		} finally {
			context.getInput().releaseKey(options -> options.keyUp);
			context.getInput().releaseKey(options -> options.keyShift);

			if (this.server != null && this.on(s -> this.session(s) != null)) {
				this.leave();
			}
		}
	}

	/** Runs one part of the test (all of them, or only those named in PHASE27_ONLY, for working on one). */
	private void part(String name, Runnable part) {
		String only = System.getenv("PHASE27_ONLY");

		if (only == null || only.isBlank() || List.of(only.split(",")).contains(name)) {
			SaberCraft.LOGGER.info("PHASE27 part {}", name);
			part.run();
		}
	}

	// --- 10: the menu key ------------------------------------------------------------------------------------

	private void keys() {
		int defaultKey = this.context.computeOnClient(client -> SaberCraftClient.CUSTOMIZE_KEY.getDefaultKey().getValue());
		check(defaultKey == InputConstants.KEY_I, "the menu key's default is not I (" + defaultKey + ")");
		check(defaultKey != InputConstants.KEY_V, "the menu key is still V (Simple Voice Chat's key)");
		// A player whose key was V (the old default, kept in the game's options) gets the new one once.
		this.context.runOnClient(Hints::replayMoveForTest);
		check(this.context.computeOnClient(client -> Hints.movedForTest()), "the menu key on V is not moved off it");
		String now = this.context.computeOnClient(client -> SaberCraftClient.CUSTOMIZE_KEY.saveString());
		check("key.keyboard.i".equals(now), "the menu key is " + now + " after moving it off V");
		// Only once: a player who sets V again keeps it.
		this.context.runOnClient(client -> {
			SaberCraftClient.CUSTOMIZE_KEY.setKey(InputConstants.getKey("key.keyboard.v"));
			net.minecraft.client.KeyMapping.resetMapping();
		});
		this.context.runOnClient(Hints::moveAgainForTest);
		check("key.keyboard.v".equals(this.context.computeOnClient(client -> SaberCraftClient.CUSTOMIZE_KEY.saveString())),
				"the menu key the player set to V themselves is moved again");
		this.context.runOnClient(client -> {
			SaberCraftClient.CUSTOMIZE_KEY.setKey(SaberCraftClient.CUSTOMIZE_KEY.getDefaultKey());
			net.minecraft.client.KeyMapping.resetMapping();
		});
		// The hint names the key as it is bound now.
		String hint = this.context.computeOnClient(client -> Hints.keyName(SaberCraftClient.CUSTOMIZE_KEY).getString());
		check(hint.contains("I"), "the hint does not name the menu key (" + hint + ")");
	}

	// --- 9: the story's darkness, brighter if one likes ---------------------------------------------------------

	private void brightness() {
		check(this.context.computeOnClient(client -> new ImmersionConfig().storyBrightness) == 0, "the story's darkness is brightened by default");
		int was = this.context.computeOnClient(client -> ImmersionConfig.get().storyBrightness);
		this.context.runOnClient(client -> {
			ImmersionConfig.get().storyBrightness = 2;
			ImmersionConfig.save();
			ImmersionConfig.load();
		});
		check(this.context.computeOnClient(client -> ImmersionConfig.get().storyBrightness) == 2, "the brightness of the story is not kept");
		check(this.context.computeOnClient(client -> ImmersionConfig.get().storyLift()) > 0.5F, "the brightest setting lifts too little of the darkness");
		this.context.runOnClient(client -> {
			ImmersionConfig.get().storyBrightness = was;
			ImmersionConfig.save();
		});
	}

	// --- 3: a hard landing costs hull by how fast the ship came down ---------------------------------------------------

	private void shipDamage() {
		this.server.runOnServer(s -> Starships.command(this.me(s), "board", "xwing"));
		boolean aboard = false;

		for (int i = 0; i < 100 && !aboard; i++) {
			this.context.waitTick();
			aboard = this.on(s -> this.me(s).getVehicle() instanceof ShipEntity);
		}

		check(aboard, "not aboard the X-Wing for the landings");

		if (!aboard) {
			return;
		}

		// Dropped straight down onto the ground, faster each time (the X-Wing: safe up to 0.5 blocks a
		// tick, a wreck at 1.75).
		double[] speeds = {0.75, 1.1, 1.45};
		float[] lost = new float[speeds.length];
		double[] measured = new double[speeds.length];
		boolean resting = true;

		for (int i = 0; i < speeds.length; i++) {
			double speed = speeds[i];
			this.server.runOnServer(s -> {
				ShipEntity ship = (ShipEntity) this.me(s).getVehicle();
				ship.setHealth(ship.getMaxHealth());
				int ground = this.me(s).level().getHeight(Heightmap.Types.MOTION_BLOCKING, ship.getBlockX(), ship.getBlockZ());
				ship.setPos(ship.getX(), ground + 16.0, ship.getZ());
				ship.forceMotionForTest(new Vec3(0.0, -speed, 0.0));
			});
			boolean down = false;

			for (int t = 0; t < 60 && !down; t++) {
				this.context.waitTick();
				down = this.on(s -> ((ShipEntity) this.me(s).getVehicle()).onGround());
			}

			this.context.waitTicks(2);
			float landed = this.on(s -> ((ShipEntity) this.me(s).getVehicle()).getHealth());
			int index = i;
			this.server.runOnServer(s -> {
				ShipEntity ship = (ShipEntity) this.me(s).getVehicle();
				lost[index] = 1.0F - ship.getHealth() / ship.getMaxHealth();
				measured[index] = ShipEntity.lastImpact();
			});
			// Resting on the ground, still pushed down: no more damage (past the crash pause).
			this.context.waitTicks(30);
			resting &= this.on(s -> ((ShipEntity) this.me(s).getVehicle()).getHealth()) == landed;
			this.server.runOnServer(s -> ((ShipEntity) this.me(s).getVehicle()).forceMotionForTest(null));
			this.context.waitTicks(5);
		}

		SaberCraft.LOGGER.info("PHASE27 SHIP DAMAGE: landing at {} blocks/tick: impact {}, hull lost {}", speeds, measured, lost);
		check(resting, "a ship resting on the ground, pushed down, still takes damage");

		for (int i = 0; i < speeds.length; i++) {
			check(Math.abs(measured[i] - speeds[i]) < 0.1, "a landing at " + speeds[i] + " counts as " + measured[i]);
		}

		check(lost[0] > 0.05F && lost[0] < lost[1] && lost[1] < lost[2] && lost[2] < 1.0F, "the hull lost does not grow with the landing's speed");
		this.server.runOnServer(s -> {
			ShipEntity ship = (ShipEntity) this.me(s).getVehicle();
			this.me(s).stopRiding();

			if (ship != null) {
				ship.discard();
			}
		});
		this.context.waitTicks(10);
	}

	// --- 12: the difficulty ------------------------------------------------------------------------------------------

	private void difficulty() {
		this.enter(1, "m2.drop");
		this.unseen();
		int normal = this.on(s -> this.enemies(this.session(s)));
		int changes = Story.difficultyChangesForTest();
		this.choose(2);
		check(this.on(s -> this.session(s).difficulty()) == StoryDifficulty.HARD, "the difficulty is not Hard after choosing it");
		check(Story.difficultyChangesForTest() == changes + 1, "choosing a difficulty in a running story does not start its checkpoint again");
		int hard = this.on(s -> this.enemies(this.session(s)));
		// Kept in the player's file: also after leaving and coming back.
		this.leave();
		check("hard".equals(this.on(s -> StoryTesting.fileDifficulty(s, this.self))), "the difficulty is not kept in the save");
		this.enter(1, "m2.drop");
		check(this.on(s -> this.session(s).difficulty()) == StoryDifficulty.HARD, "the difficulty is lost when the story is continued");
		this.unseen();
		this.choose(0);
		int easy = this.on(s -> this.enemies(this.session(s)));
		SaberCraft.LOGGER.info("PHASE27 DIFFICULTY: enemies in the hangar mission: easy {}, normal {}, hard {}", easy, normal, hard);
		check(hard > normal, "Hard has no more enemies than Normal (" + hard + " / " + normal + ")");
		check(easy < normal, "Easy has no fewer enemies than Normal (" + easy + " / " + normal + ")");
		this.choose(1);
		check(this.on(s -> this.session(s).difficulty()) == StoryDifficulty.NORMAL, "the difficulty is not Normal again");
	}

	private void choose(int difficulty) {
		this.context.runOnClient(client -> StoryClient.send(StoryActionPayload.DIFFICULTY, "", difficulty));

		for (int i = 0; i < 40 && this.on(s -> this.session(s) != null && this.session(s).difficulty().ordinal() != difficulty); i++) {
			this.context.waitTick();
		}

		this.context.waitTicks(10);
		this.unseen();
	}

	private int enemies(StorySession s) {
		int count = 0;

		for (Figure figure : s.figures) {
			if (figure.observer && Story.DESTROYER.equals(figure.ship) && (Crew.TROOPER.equals(figure.kind()) || figure.hostile)) {
				count++;
			}
		}

		return count;
	}

	// --- 1: checkpoints in crawl ways, the bearings after one --------------------------------------------------------

	private void checkpoints() {
		this.enter(1, "m2.drop");
		this.unseen();
		// Close to the flight control's hatch, in the crawl way: no checkpoint there (one came back in the hatch).
		Vec3 nearHatch = new Vec3(10.5, H, -33.5);
		check(this.on(s -> StoryTesting.inVent(nearHatch)), "the test's place by the hatch is not in the crawl way");
		check(this.on(s -> StoryTesting.crawlAnchor(nearHatch)) == null, "a checkpoint would be kept right by a hatch");
		this.put(nearHatch, 180.0F, 10.0F);
		this.context.waitTicks(10);
		int before = this.on(s -> StoryTesting.checkpoints(this.session(s)));
		this.server.runOnServer(s -> StoryTesting.safeCheckpointNow(this.session(s)));
		Vec3 kept = this.on(s -> StoryTesting.checkpointPlace(this.session(s)));
		check(kept == null || kept.distanceTo(new Vec3(10.5, H, -34.5)) > 2.0, "a checkpoint was kept at the hatch (" + kept + ")");
		// Deep in the crawl way: one is kept there, on its middle.
		Vec3 deep = new Vec3(10.5, H, -22.5);
		this.put(deep, 0.0F, 10.0F);
		this.context.waitTicks(10);
		check(this.on(s -> this.me(s).getPose()) == Pose.SWIMMING, "put into the crawl way, the player is not on all fours");
		// (Something learned since the last checkpoint: only then is there anything to keep.)
		this.flags("know.phase27_test");
		this.server.runOnServer(s -> StoryTesting.safeCheckpointNow(this.session(s)));
		Vec3 anchor = this.on(s -> StoryTesting.checkpointPlace(this.session(s)));
		check(this.on(s -> StoryTesting.checkpoints(this.session(s))) > before && anchor != null && anchor.distanceTo(deep) < 0.6,
				"no checkpoint deep in the crawl way (" + anchor + ")");
		// Caught: back there, on all fours, in the clear.
		this.put(new Vec3(20.5, H, -40.0), 0.0F, 0.0F);
		int restores = this.on(s -> StoryTesting.restores(this.session(s)));
		this.server.runOnServer(s -> StoryTesting.capture(this.session(s)));

		for (int i = 0; i < 100 && this.on(s -> StoryTesting.restores(this.session(s))) == restores; i++) {
			this.context.waitTick();
		}

		this.context.waitTicks(10);
		Vec3 back = this.on(s -> this.session(s).local(Story.DESTROYER));
		check(anchor != null && back.distanceTo(anchor) < 0.8, "back at the checkpoint in the crawl way, the player is elsewhere (" + back + ")");
		check(this.on(s -> this.me(s).getPose()) == Pose.SWIMMING, "back in the crawl way, the player is not on all fours");
		check(this.on(s -> this.level(s).noCollision(this.me(s), this.me(s).getBoundingBox().deflate(0.01))), "back in the crawl way, the player is stuck");
		// Time to get one's bearings: nobody sees one while one stays where one was put...
		check(this.on(s -> StoryTesting.shield(this.session(s))) > 0, "no time to get one's bearings after the restore");
		this.context.waitTicks(80);
		check(this.on(s -> this.session(s).grace) > 0, "the bearings end while the player has not moved");
		// ...and it ends as one goes on.
		this.put(new Vec3(10.5, H, -17.5), 0.0F, 10.0F);
		this.context.waitTicks(30);
		check(this.on(s -> StoryTesting.shield(this.session(s))) == 0, "the bearings go on after the player crawled on");
	}

	/** Phase 27: an old save kept a place in a shut hatch: it is not trusted, the player comes back at the step's own place. */
	private void oldSave() {
		this.enter(1, "m2.drop");
		this.unseen();
		int untrusted = this.on(s -> StoryTesting.untrusted());
		this.server.runOnServer(s -> StoryTesting.keptPlace(this.session(s), Story.DESTROYER, new Vec3(10.5, H, -34.5), 0.0F));
		int restores = this.on(s -> StoryTesting.restores(this.session(s)));
		this.server.runOnServer(s -> StoryTesting.capture(this.session(s)));

		for (int i = 0; i < 100 && this.on(s -> StoryTesting.restores(this.session(s))) == restores; i++) {
			this.context.waitTick();
		}

		this.context.waitTicks(10);
		check(this.on(s -> StoryTesting.untrusted()) > untrusted, "the place in the hatch was trusted");
		Vec3 at = this.on(s -> this.session(s).local(Story.DESTROYER));
		check(Math.abs(at.z - -34.5) > 2.0 && this.on(s -> this.level(s).noCollision(this.me(s), this.me(s).getBoundingBox().deflate(0.01))),
				"the player came back in the hatch (" + at + ")");
	}

	// --- 5: the marker in the crawl way -------------------------------------------------------------------------------

	private void ventMarker() {
		this.enter(0, "m1.hatch");
		this.unseen();
		this.put(new Vec3(14.5, H, -9.5), 90.0F, 10.0F);
		this.context.waitTicks(10);
		Vec3 marker = this.markerLocal();
		Vec3 mouth = StoryTesting.hideoutMouth();
		check(marker != null && marker.distanceTo(mouth) < 0.6, "in the first crawl way the marker does not lead to the hideout (" + marker + ")");
	}

	// --- 6, 7, 11: Mission 2's way back -------------------------------------------------------------------------------

	private void mission2() {
		this.enter(1, "m2.drop");
		this.unseen();
		// The officer of flight control walks a round between the consoles.
		String officer = this.on(s -> {
			for (Figure figure : this.session(s).figures) {
				if (Crew.OFFICER_OLIVE.equals(figure.kind()) && "flight_control".equals(figure.crew.role())) {
					return figure.key;
				}
			}

			return null;
		});

		if (officer == null) {
			this.problem("no officer in flight control");
			return;
		}

		check(this.on(s -> this.session(s).figure(officer).crew.path().size()) >= 2, "the flight control officer has no round");
		check(this.on(s -> StoryTesting.hearing(this.session(s).figure(officer))) > 9.0, "the flight control officer does not hear further");
		double minZ = 0.0;
		double maxZ = -100.0;
		double maxDx = 0.0;

		for (int i = 0; i < 30; i++) {
			Vec3 at = this.on(s -> this.session(s).figure(officer).position());
			minZ = Math.min(minZ, at.z);
			maxZ = Math.max(maxZ, at.z);
			maxDx = Math.max(maxDx, Math.abs(at.x - 12.0));
			this.context.waitTicks(20);
		}

		SaberCraft.LOGGER.info("PHASE27 MISSION 2: the officer's round z {}..{}, off its line {}", minZ, maxZ, maxDx);
		check(maxZ - minZ > 6.0, "the flight control officer does not walk his round (z " + minZ + ".." + maxZ + ")");
		check(maxDx < 0.6, "the flight control officer leaves his round's line");
		// The lure: noise in the north of the room. The seated crew only look; the officer goes, and
		// searches on past it, away from the door.
		this.server.runOnServer(s -> this.session(s).figure(officer).snapToRoutine(this.session(s).time()));
		Object[] lure = this.on(s -> {
			StorySession session = this.session(s);
			int[] counts = StoryTesting.noise(session, new Vec3(12.5, H, -53.0));
			Figure figure = session.figure(officer);
			int seatedUp = 0;

			for (Figure f : session.figures) {
				if (f.seated() && f.mode != Figure.ROUTINE && f.mode != Figure.LOOK) {
					seatedUp++;
				}
			}

			return new Object[] {counts[0], figure.mode, seatedUp};
		});
		SaberCraft.LOGGER.info("PHASE27 MISSION 2: the lure: went {}, the officer's mode {}, seated crew up {}", lure[0], lure[1], lure[2]);
		check((int) lure[0] == 1 && (int) lure[1] == Figure.LOOK, "the flight control officer does not go to see M-31's noise");
		check((int) lure[2] == 0, "seated crew get up for M-31's noise");
		this.context.waitTicks(140);
		double z = this.on(s -> this.session(s).figure(officer).position().z);
		check(z < -49.0, "the lured officer is not far enough from the door (z " + String.format(Locale.ROOT, "%.1f", z) + ")");
		// How long the way past the door stays free: until he is back in the south half of his round.
		int away = 140;

		while (away < 900 && this.on(s -> this.session(s).figure(officer).position().z) < -46.0) {
			this.context.waitTicks(10);
			away += 10;
		}

		SaberCraft.LOGGER.info("PHASE27 MISSION 2: the lured officer kept away from the door's side for {} s", away / 20.0);
		check(away >= 160, "the lured officer is back at the door's side too soon (" + away / 20.0 + " s)");
		// A second noise at once: he only looks; a minute later he believes it again.
		int[] again = this.on(s -> StoryTesting.noise(this.session(s), this.session(s).figure(officer).position().add(1.5, 0.0, 0.0)));
		check(again[0] == 0, "the officer goes to see a second noise at once");
		this.server.runOnServer(s -> StoryTesting.distractedLongAgo(this.session(s).figure(officer)));
		int[] later = this.on(s -> StoryTesting.noise(this.session(s), this.session(s).figure(officer).position().add(1.5, 0.0, 0.0)));
		check(later[0] == 1, "a minute later the officer does not believe a noise again");

		// The stick taken: a checkpoint by the containers, with the stick.
		this.flags("item.stick");
		this.server.runOnServer(s -> StoryTesting.finishStep(this.session(s)));
		this.waitStep("m2.return", 60);
		Vec3 kept = this.on(s -> StoryTesting.checkpointPlace(this.session(s)));
		check(kept != null && kept.distanceTo(StoryTesting.crateSafe()) < 0.1, "no checkpoint by the containers with the stick (" + kept + ")");
		check(this.on(s -> StoryTesting.kept(this.session(s))).contains("item.stick"), "the checkpoint with the stick does not keep it");
		this.put(StoryTesting.crateSafe(), 180.0F, 0.0F);
		check(this.on(s -> this.level(s).noCollision(this.me(s), this.me(s).getBoundingBox().deflate(0.01))), "the checkpoint by the containers is in something");
		// The marker home: the door, the hatch, the crawl way.
		this.put(new Vec3(25.5, H, -58.0), 180.0F, 0.0F);
		Vec3 m = this.markerLocal();
		check(m != null && Math.abs(m.x - 17.5) < 0.8 && Math.abs(m.z - -38.5) < 1.0, "in the hangar the marker does not lead to flight control's door (" + m + ")");
		this.put(new Vec3(13.5, H, -39.5), 90.0F, 0.0F);
		m = this.markerLocal();
		check(m != null && Math.abs(m.x - 10.5) < 0.8 && Math.abs(m.z - -35.6) < 1.0, "in flight control the marker does not lead to the hatch (" + m + ")");
		// Caught on the way back: back by the containers, with the stick.
		int restores = this.on(s -> StoryTesting.restores(this.session(s)));
		this.server.runOnServer(s -> StoryTesting.capture(this.session(s)));

		for (int i = 0; i < 100 && this.on(s -> StoryTesting.restores(this.session(s))) == restores; i++) {
			this.context.waitTick();
		}

		this.context.waitTicks(10);
		check(this.on(s -> this.session(s).local(Story.DESTROYER).distanceTo(StoryTesting.crateSafe())) < 1.0, "caught on the way back, not back by the containers");
		check(this.on(s -> this.session(s).has("item.stick")), "caught on the way back, the stick is lost");
		this.server.runOnServer(s -> StoryTesting.unseen(this.session(s), 0));
		float worst = 0.0F;

		for (int i = 0; i < 12; i++) {
			this.context.waitTicks(10);
			worst = Math.max(worst, this.on(s -> StoryTesting.suspicion(this.session(s))));
		}

		check(worst < 0.3F, "right after coming back by the containers someone grows suspicious (" + worst + ")");
		this.unseen();

		// The second way: the hatch low in the hangar's west wall. (Phase 28: its cover off, as in
		// Phase 27; the cover, the screwdriver and the screws are Phase28GameTest's.)
		this.flags(StoryTesting.coverOpen());
		this.server.runOnServer(s -> StoryTesting.ventCover(this.session(s)));
		BlockPos hatch = DestroyerInterior.HANGAR_HATCH;
		check(this.on(s -> this.level(s).getBlockState(this.world(s, hatch)).is(Blocks.BARRIER)), "the hangar's hatch is not there (shut)");

		for (int x = 10; x <= 16; x++) {
			BlockPos cell = new BlockPos(x, H, -29);
			check(this.on(s -> this.level(s).getBlockState(this.world(s, cell)).getCollisionShape(this.level(s), this.world(s, cell)).isEmpty()),
					"the hangar's crawl way is blocked at x " + x);
		}

		check(this.on(s -> this.level(s).getBlockState(this.world(s, new BlockPos(18, H, -29))).isAir()), "something stands in front of the hangar's hatch");
		// One click on it and walking in.
		int intents = this.context.computeOnClient(client -> InteriorClient.intentsForTest());
		this.put(new Vec3(18.75, H, -28.5), 90.0F, 42.0F);
		this.context.getInput().pressKey(options -> options.keyUse);
		this.context.waitTicks(6);
		check(this.on(s -> this.level(s).getBlockState(this.world(s, hatch)).isAir()), "a click does not open the hangar's hatch");
		check(this.context.computeOnClient(client -> InteriorClient.intentsForTest()) > intents, "the click on the hatch is not taken as wanting in");
		this.context.runOnClient(client -> client.player.setXRot(5.0F));
		this.context.getInput().holdKeyFor(options -> options.keyUp, 30);
		this.context.waitTicks(4);
		Vec3 in = this.on(s -> this.session(s).local(Story.DESTROYER));
		check(in.x < 17.0 && this.on(s -> this.me(s).getPose()) == Pose.SWIMMING, "walking into the open hatch, the player does not crawl in (" + in + ")");
		check(this.on(s -> Vents.fastCrawlForTest(this.me(s))), "crawling in the crawl way is not faster");
		this.context.takeScreenshot("phase27_hangar_vent");
		// On to the way from flight control (and the marker to the hideout from there).
		double x0 = in.x;
		this.context.getInput().holdKeyFor(options -> options.keyUp, 40);
		this.context.waitTicks(2);
		Vec3 on = this.on(s -> this.session(s).local(Story.DESTROYER));
		SaberCraft.LOGGER.info("PHASE27 MISSION 2: crawled from x {} to x {} in 2 s", x0, on.x);
		check(x0 - on.x > 2.5, "crawling is too slow (" + String.format(Locale.ROOT, "%.1f", x0 - on.x) + " blocks in 2 s)");
		Vec3 home = this.markerLocal();
		check(home != null && home.distanceTo(StoryTesting.hideoutMouth()) < 0.6, "in the hangar's crawl way the marker does not lead to the hideout");
		this.put(new Vec3(25.5, H, -58.0), 180.0F, 0.0F);
		this.context.waitTicks(20);
		check(!this.on(s -> Vents.fastCrawlForTest(this.me(s))), "standing again, the crawl's bonus stays");
		// The new hatch from the hangar: low in the west wall beside the crate stack, marked as access.
		this.put(new Vec3(21.5, H, -28.5), 90.0F, 22.0F);
		this.context.waitTicks(10);
		this.context.takeScreenshot("phase27_hangar_west_wall");
	}

	// --- 4, 8: dialogs, films and being seen --------------------------------------------------------------------------

	private void interrupt() {
		this.enter(1, "m2.return");
		this.unseen();
		this.put(new Vec3(30.5, H, -45.0), 0.0F, 0.0F);
		// A film that only hid a dialog: afterwards the dialog is there again (the objective waited for it).
		this.server.runOnServer(s -> this.session(s).talk("kestrel_idle", null));
		this.waitScreen(true);
		this.context.runOnClient(client -> StoryCinema.startForTest("takedown"));
		this.context.waitTicks(10);
		this.context.runOnClient(client -> StoryCinema.skipForTest());
		this.context.waitTicks(5);
		check(this.on(s -> this.session(s).dialog != null), "the dialog is gone on the server after the film");
		check(this.context.computeOnClient(client -> client.gui.screen() instanceof DialogScreen), "the dialog does not come back after a film hid it");
		// A film the story begins ends an open dialog first (after Dax's jump the objective waited for it forever).
		int broken = this.on(s -> StorySession.interruptsForTest());
		this.server.runOnServer(s -> Story.cue(this.session(s), Story.CUE_CINEMA, "takedown", "", 40));
		this.context.waitTicks(3);
		check(this.on(s -> this.session(s).dialog == null), "a film beginning does not end the open dialog on the server");
		check(this.on(s -> StorySession.interruptsForTest()) == broken + 1, "a film beginning is not counted as breaking off the dialog");
		this.context.runOnClient(client -> StoryCinema.skipForTest());
		this.context.waitTicks(5);
		this.server.runOnServer(s -> this.session(s).talk("kestrel_idle", null));
		this.waitScreen(true);
		// Seen: the dialog closes, the player can run.
		String trooper = this.on(s -> {
			for (Figure figure : this.session(s).figures) {
				if (figure.observer && Crew.TROOPER.equals(figure.kind()) && figure.mode == Figure.ROUTINE && Math.abs(figure.y - H) < 1.0) {
					return figure.key;
				}
			}

			return null;
		});

		if (trooper == null) {
			this.problem("no trooper in the hangar to see the player");
			return;
		}

		this.server.runOnServer(s -> StoryTesting.detect(this.session(s), this.session(s).figure(trooper)));
		this.context.waitTicks(3);
		check(this.on(s -> this.session(s).dialog == null), "being seen does not close the dialog");
		this.waitScreen(false);
		check(!this.context.computeOnClient(client -> client.gui.screen() instanceof DialogScreen), "being seen does not close the dialog's screen");
		this.server.runOnServer(s -> StoryTesting.endAlarm(this.session(s)));
		this.unseen();
	}

	private void waitScreen(boolean dialog) {
		for (int i = 0; i < 30 && this.context.computeOnClient(client -> client.gui.screen() instanceof DialogScreen) != dialog; i++) {
			this.context.waitTick();
		}
	}

	// --- 2: the people behind the detention block's door ------------------------------------------------------------

	private void popin() {
		this.enter(4, "m5.route");
		this.unseen();
		check(this.on(s -> this.session(s).figure("det_guard") != null), "the detention block's guard is not there before its door opens");
		check(this.on(s -> this.session(s).figure("r2v7") != null), "R2-V7 is not in the detention block before its door opens");
		this.flags("trust.hale");
		this.server.runOnServer(s -> StoryTesting.refigure(this.session(s)));
		check(this.on(s -> this.session(s).figure("hale") != null), "Hale is not there before the detention block's door opens");
		check(this.on(s -> this.session(s).figure("trap1") != null), "Hale's officer is not there before the detention block's door opens");
		this.server.runOnServer(s -> this.session(s).unset("trust.hale"));
	}

	// --- 1: the respawn by the cell console is not watched -------------------------------------------------------------

	private void consoleSafe() {
		this.enter(4, "m5.cell1");
		this.context.waitTicks(10);
		this.put(StoryTesting.consoleSafe(), 180.0F, 0.0F);
		this.server.runOnServer(s -> StoryTesting.unseen(this.session(s), 0));
		float worst = 0.0F;

		for (int i = 0; i < 20; i++) {
			this.context.waitTicks(10);
			worst = Math.max(worst, this.on(s -> StoryTesting.suspicion(this.session(s))));
		}

		SaberCraft.LOGGER.info("PHASE27 CONSOLE SAFE: the most suspicion there {}", worst);
		check(worst < 0.3F && this.on(s -> this.session(s).alarm <= 0), "at the respawn by the cell console someone sees the player (" + worst + ")");
		this.unseen();
	}

	// --- 16: the bridge's lift sealed off ------------------------------------------------------------------------------

	private void bridge() {
		this.enter(3, "m4.brief");
		this.unseen();
		check(this.on(s -> StoryTesting.bridgeClosed(this.session(s))), "the bridge is open in the story");
		int barriers = this.on(s -> {
			int n = 0;

			for (int x = Chapter1.BRIDGE_BARRIER_X0; x <= Chapter1.BRIDGE_BARRIER_X1; x++) {
				for (int y = H; y <= H + 2; y++) {
					if (this.level(s).getBlockState(this.world(s, new BlockPos(x, y, Chapter1.BRIDGE_BARRIER_Z))).is(Blocks.BARRIER)) {
						n++;
					}
				}
			}

			return n;
		});
		check(barriers == (Chapter1.BRIDGE_BARRIER_X1 - Chapter1.BRIDGE_BARRIER_X0 + 1) * 3, "the bridge's barrier does not close the way (" + barriers + " blocks)");
		this.put(new Vec3(40.5, H, Chapter1.BRIDGE_BARRIER_Z - 2.5), 0.0F, 5.0F);
		this.context.getInput().holdKeyFor(options -> options.keyUp, 30);
		this.context.waitTicks(5);
		double z = this.on(s -> this.session(s).local(Story.DESTROYER).z);
		check(z < Chapter1.BRIDGE_BARRIER_Z, "the player walks through the bridge's barrier (z " + z + ")");
		this.context.takeScreenshot("phase27_bridge_barrier");
	}

	// --- 15: the lab's guard ----------------------------------------------------------------------------------------------

	private void labGuard() {
		this.enter(5, "m6.door");
		this.unseen();
		Figure guard = this.on(s -> this.session(s).figure("lab_guard"));

		if (guard == null) {
			this.problem("no guard at the lab's door");
			return;
		}

		Vec3 post = StoryTesting.labGuardPost();
		check(guard.position().distanceTo(post) < 0.4, "the lab's guard is not at his post (" + guard.position() + ")");
		check(post.x < DestroyerInterior.LAB_DOOR_X0, "the lab's guard does not stand west of the way to the door");
		check(this.on(s -> StoryTesting.sentry(this.session(s).figure("lab_guard"))), "the lab's guard is no sentry");
		// His round: he looks west, over the shaft and east in turn.
		java.util.Set<Integer> looks = new java.util.HashSet<>();

		for (int i = 0; i < 45; i++) {
			float yaw = this.on(s -> this.session(s).figure("lab_guard").yaw);

			for (int look = 0; look < StoryTesting.labLooks(); look++) {
				if (Math.abs(Mth.wrapDegrees(yaw - StoryTesting.labLookYaw(look))) < 3.0F) {
					looks.add(Math.round(StoryTesting.labLookYaw(look)));
				}
			}

			this.context.waitTicks(10);
		}

		check(looks.size() >= 3, "the lab's guard does not look round (looks " + looks + ")");
		// Screenshots for left and right: from the platform facing the door, from the door facing out.
		this.put(new Vec3(44.5, R, 34.2), 0.0F, 8.0F);
		this.context.waitTicks(4);
		this.context.takeScreenshot("phase27_lab_guard_facing_door");
		this.put(new Vec3(44.5, R, 38.0), 180.0F, 8.0F);
		this.context.waitTicks(4);
		this.context.takeScreenshot("phase27_lab_guard_from_door");

		// Seen for real: coming along the platform while he looks west.
		int shots = StoryTesting.stunShots();
		int wait = this.on(s -> StoryTesting.labLookIn(this.session(s), 0));
		this.put(new Vec3(36.0, R, 31.0), 270.0F, 0.0F);
		this.context.waitTicks(Math.max(0, wait - 8));
		this.put(new Vec3(37.6, R, 34.2), 270.0F, 0.0F);
		this.server.runOnServer(s -> StoryTesting.unseen(this.session(s), 0));
		boolean detected = false;

		for (int i = 0; i < 100 && !detected; i++) {
			this.context.waitTick();
			detected = this.on(s -> this.session(s).figure("lab_guard") != null && this.session(s).figure("lab_guard").stage >= Figure.DETECTED);
		}

		check(detected, "the lab's guard does not see the player coming along the platform while he looks west");
		boolean fired = false;

		for (int i = 0; i < 100 && !fired; i++) {
			this.context.waitTick();
			fired = StoryTesting.stunShots() > shots;
		}

		SaberCraft.LOGGER.info("PHASE27 LAB GUARD: detected {}, fired {}", detected, fired);
		check(fired, "the lab's guard does not fire at the player he saw");

		// M-31's noise: he goes to see, and comes back to his post and his round.
		this.enter(5, "m6.door");
		this.unseen();
		int[] noise = this.on(s -> StoryTesting.noise(this.session(s), new Vec3(37.5, R, 34.0)));
		check(noise[0] == 1, "the lab's guard does not go to see M-31's noise");
		double off = 0.0;
		double first = 0.0;

		// He goes to where the noise was and looks there a while; then he searches on past it.
		for (int i = 0; i < 26; i++) {
			this.context.waitTicks(10);
			String at = this.on(s -> {
				Figure lured = this.session(s).figure("lab_guard");
				return lured.position().distanceTo(post) + " " + lured.position() + " mode " + lured.mode;
			});
			double now = Double.parseDouble(at.substring(0, at.indexOf(' ')));
			off = Math.max(off, now);

			if (i < 10) {
				first = Math.max(first, now);
			}

			if (i % 3 == 2) {
				SaberCraft.LOGGER.info("PHASE27 LAB GUARD LURE: tick {}: off {}", (i + 1) * 10, at);
			}
		}

		SaberCraft.LOGGER.info("PHASE27 LAB GUARD LURE: off his post {} within 5 s, {} at most", String.format(Locale.ROOT, "%.1f", first), String.format(Locale.ROOT, "%.1f", off));
		check(first > 2.5, "M-31's noise does not draw the lab's guard off his post (" + String.format(Locale.ROOT, "%.1f", first) + ")");
		check(off > 3.5, "the lab's guard does not search on past M-31's noise (" + String.format(Locale.ROOT, "%.1f", off) + ")");
		boolean back = false;

		for (int i = 0; i < 60 && !back; i++) {
			this.context.waitTicks(10);
			back = this.on(s -> this.session(s).figure("lab_guard").mode == Figure.ROUTINE
					&& this.session(s).figure("lab_guard").position().distanceTo(post) < 0.6);
		}

		check(back, "the lab's guard does not come back to his post after the noise");
		float yaw0 = this.on(s -> this.session(s).figure("lab_guard").yaw);
		boolean turned = false;

		for (int i = 0; i < 30 && !turned; i++) {
			this.context.waitTicks(10);
			turned = Math.abs(Mth.wrapDegrees(this.on(s -> this.session(s).figure("lab_guard").yaw) - yaw0)) > 20.0F;
		}

		check(turned, "back at his post, the lab's guard does not keep watch");

		// Overpowered from behind while he looks east (his back to the console): the player stands in the clear.
		int east = this.on(s -> StoryTesting.labLookIn(this.session(s), 2));
		this.context.waitTicks(east + 2);
		int takedowns = this.on(s -> StoryTesting.takedowns());
		this.server.runOnServer(s -> {
			StorySession session = this.session(s);
			this.teleport(s, session, new Vec3(41.6, R, 36.6), 0.0F);
		});
		this.context.waitTicks(1);
		// (Phase 31: crouching, as one has to.)
		this.server.runOnServer(s -> StoryTesting.takedownCrouched(this.session(s)));
		check(this.on(s -> StoryTesting.takedowns()) == takedowns + 1, "the lab's guard cannot be overpowered from behind while he looks east");
		this.context.waitTicks(2);
		check(this.on(s -> this.level(s).noCollision(this.me(s), this.me(s).getBoundingBox().deflate(0.01))), "the takedown puts the player into something");

		for (int i = 0; i < 60 && this.on(s -> this.session(s).has("tmp.takedown")); i++) {
			this.context.waitTicks(2);
		}

		check(this.on(s -> this.session(s).figure("lab_guard").mode == Figure.DOWN), "the lab's guard is not down after the takedown");
		this.server.runOnServer(s -> this.session(s).unset(StoryTesting.labGuardDown()));
	}

	// --- 14: the Falcon waits when the story is loaded at the escape --------------------------------------------------

	private void falcon() {
		int arrivals = this.context.computeOnClient(client -> HangarFalcon.arrivalsForTest());
		this.enter(5, "m6.falcon");
		this.unseen();
		this.put(new Vec3(40.5, H, -20.5), 180.0F, 30.0F);
		this.context.waitTicks(40);
		check(this.context.computeOnClient(client -> HangarFalcon.arrivalsForTest()) == arrivals, "loaded at the escape, the Falcon comes up again");
		check(this.context.computeOnClient(client -> HangarFalcon.belowForTest()) < 0.5F, "loaded at the escape, the Falcon is not in its place");
		this.context.takeScreenshot("phase27_falcon_loaded");
	}

	// --- Helpers ------------------------------------------------------------------------------------------------------------

	/** Where the marker points now (plot space of the Star Destroyer), or null. */
	private @Nullable Vec3 markerLocal() {
		this.context.waitTicks(4);
		return this.on(s -> {
			StorySession session = this.session(s);
			Vec3 marker = StoryTesting.marker(session);
			return marker == null ? null : marker.subtract(Vec3.atLowerCornerOf(session.destroyer));
		});
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

	private void waitStep(String step, int ticks) {
		for (int i = 0; i < ticks; i++) {
			if (step.equals(this.on(s -> this.session(s) == null ? "" : StoryTesting.step(this.session(s))))) {
				return;
			}

			this.context.waitTick();
		}

		this.problem("the story is not at " + step + " (at " + this.on(s -> this.session(s) == null ? "-" : StoryTesting.step(this.session(s))) + ")");
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
		SaberCraft.LOGGER.error("PHASE27 CHECK FAILED: {}", problem);
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
