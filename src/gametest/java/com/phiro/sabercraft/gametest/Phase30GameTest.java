package com.phiro.sabercraft.gametest;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
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
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.ability.AbilityConfig;
import com.phiro.sabercraft.ability.SaberAbilities;
import com.phiro.sabercraft.client.ability.SaberAbilityClient;
import com.phiro.sabercraft.client.interior.LiftHud;
import com.phiro.sabercraft.client.interior.MeshAudit;
import com.phiro.sabercraft.client.story.StoryCinema;
import com.phiro.sabercraft.client.story.StoryClient;
import com.phiro.sabercraft.client.story.StoryHud;
import com.phiro.sabercraft.interior.DestroyerInterior;
import com.phiro.sabercraft.interior.InteriorPlan;
import com.phiro.sabercraft.interior.InteriorSystems;
import com.phiro.sabercraft.interior.ShipInteriors;
import com.phiro.sabercraft.mixin.client.HudAccessor;
import com.phiro.sabercraft.network.StoryActionPayload;
import com.phiro.sabercraft.story.Figure;
import com.phiro.sabercraft.story.Mission;
import com.phiro.sabercraft.story.Story;
import com.phiro.sabercraft.story.StoryDifficulty;
import com.phiro.sabercraft.story.StorySession;
import com.phiro.sabercraft.story.StoryTesting;

/**
 * Phase 30: the last small fixes after Phiro's playtest of Phase 29. The parts a test can play in
 * the real game (the feel stays for the manual test list in {@code docs/development/phase-30-status.md}):
 *
 * <ol>
 * <li>Her spin in the second phase: the red circle on the floor is exactly where it hits; one in
 * it is hit at once (also one standing in it as it begins), one outside it is not; the client draws
 * the circle only while it hits (1).</li>
 * <li>Her Inquisitor's saber with both blades: in no pose of her animations is a part of it in her
 * body as drawn (the torso as it leans, the head, the robe), pictures of the poses (2).</li>
 * <li>The finale's troopers shoot a little more often, still never two bolts at once (3).</li>
 * <li>Under the reactor's entrance landing no plain block shows (pictures from the floor) (4).</li>
 * <li>The reactor's lift: while it goes the HUD shows which way, a bar and the seconds left, and
 * its posts light up; on arrival "arrived"; moving about on it, nothing flashes up (5).</li>
 * <li>The maintenance door: one click on its panel (or the door) and it unlocks by itself, the
 * player walking away, its bar drawn also under the mission's title card; the marker, the
 * objective and the hint lead there; bolted only from behind it, then on into the shaft (6).</li>
 * </ol>
 */
public class Phase30GameTest implements FabricClientGameTest {
	private static final int H = DestroyerInterior.HANGAR + 1;
	private static final int R = DestroyerInterior.REACTOR_BOTTOM;
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
			this.problems.forEach(problem -> SaberCraft.LOGGER.error("PHASE30 PROBLEM: {}", problem));
			throw new AssertionError("Phase 30: " + this.problems.size() + " problem(s): " + String.join(" | ", this.problems));
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

			this.part("spin", this::spin);
			this.part("saberPoses", this::saberPoses);
			this.part("shots", this::shots);
			this.part("landing", this::landing);
			this.part("lift", this::lift);
			this.part("release", this::release);
		} catch (AssertionError | RuntimeException e) {
			this.problem("aborted: " + e);
			SaberCraft.LOGGER.error("PHASE30 aborted", e);
		} finally {
			context.getInput().releaseKey(options -> options.keyUp);
			context.getInput().releaseKey(SaberAbilityClient.GUARD_KEY);
			context.getInput().releaseMouse(0);
			context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));

			if (this.server != null && this.on(s -> this.session(s) != null)) {
				this.leave();
			}
		}
	}

	/** Runs one part of the test (all of them, or only those named in PHASE30_ONLY, for working on one). */
	private void part(String name, Runnable part) {
		String only = System.getenv("PHASE30_ONLY");

		if (only == null || only.isBlank() || List.of(only.split(",")).contains(name)) {
			SaberCraft.LOGGER.info("PHASE30 part {}", name);
			int before = this.problems.size();
			long start = System.currentTimeMillis();
			part.run();
			SaberCraft.LOGGER.info("PHASE30 part {} done in {} s, {} problem(s)", name, (System.currentTimeMillis() - start) / 1000, this.problems.size() - before);
		}
	}

	// --- 1: her spin's circle -------------------------------------------------------------------------------------

	/** What one spin did: when (ticks after it began) the server's circle went live and ended, the first hit, the hits, the client's circle frames before, during and after. */
	private record Spin(int live, int end, int firstHit, int hits, int contacts, int dodged, int framesBefore, int framesDuring, int framesAfter) {
	}

	private void spin() {
		if (!this.duel()) {
			return;
		}

		// The second phase, both blades out.
		this.server.runOnServer(s -> StoryTesting.halfStrength(this.session(s), this.her(s), this.me(s)));
		this.film(30);
		this.context.waitTicks(5);
		this.skip("boss_mid");
		this.context.waitTicks(40);
		check(this.on(s -> StoryTesting.phase(this.her(s)) == 2), "the second phase does not begin");
		double[] n = StoryTesting.spinNumbers();
		double reach = n[0];

		// Standing in the circle as it begins (close, in the middle, near its edge): hit at once when it goes live.
		// (Not right at the edge: she shifts a little as her blades rise, up to 0.15 seen.)
		for (double d : new double[] {1.6, 2.6, reach - 0.3}) {
			Spin spin = this.spinAt(d, -1, 0.0, false);
			SaberCraft.LOGGER.info("PHASE30 SPIN at {} blocks: {}", String.format(Locale.ROOT, "%.2f", d), spin);
			check(spin.live() >= 0, "her spin never hits (at " + d + ")");
			check(spin.firstHit() >= 0 && spin.firstHit() - spin.live() <= 1, "standing " + d + " blocks from her, in her spin's circle as it begins, the first hit comes "
					+ (spin.firstHit() < 0 ? "never" : spin.firstHit() - spin.live() + " ticks after the circle") + " (" + spin + ")");
			check(spin.framesBefore() == 0, "the client draws her spin's circle before it hits (" + spin + ")");
			check(spin.framesDuring() > 0, "the client does not draw her spin's circle while it hits (" + spin + ")");
			check(spin.framesAfter() <= 2, "the client still draws her spin's circle after it (" + spin + ")");
		}

		// Outside the circle (the player's middle beyond its edge, also after her shift): never hit.
		for (double d : new double[] {reach + 0.25, reach + 1.2}) {
			Spin spin = this.spinAt(d, -1, 0.0, false);
			SaberCraft.LOGGER.info("PHASE30 SPIN outside at {} blocks: {}", String.format(Locale.ROOT, "%.2f", d), spin);
			check(spin.hits() == 0 && spin.contacts() == 0, "standing " + d + " blocks from her, outside her spin's circle, it hits (" + spin + ")");
		}

		// Stepping into it while it whirls: hit at once.
		Spin into = this.spinAt(reach + 1.0, 4, 2.4, false);
		SaberCraft.LOGGER.info("PHASE30 SPIN stepped in: {}", into);
		check(into.firstHit() >= 0 && into.firstHit() - (into.live() + 4) <= 1, "stepping into her spin's circle while it whirls, the hit comes late or never (" + into + ")");

		// The dash under way as the circle appears, in it (the server's side of a roll): dodged, once,
		// and no hit then; staying in it, the blades reach the player again SPIN_REHIT of her ticks later.
		Spin dodged = this.spinAt(2.6, -1, 0.0, true);
		SaberCraft.LOGGER.info("PHASE30 SPIN dodged as it appears: {}", dodged);
		check(dodged.dodged() == 1, "a dash under way in her spin's circle as it appears is not counted as dodged once (" + dodged + ")");
		check(dodged.firstHit() < 0 || dodged.firstHit() - dodged.live() >= 8, "a dash under way in her spin's circle as it appears does not dodge it (" + dodged + ")");
		// Rolled into it from outside: it only passes through (no dodge counted), and once the dash is
		// over the circle hits.
		Spin rolled = this.spinAt(reach + 1.2, 2, 2.4, true);
		SaberCraft.LOGGER.info("PHASE30 SPIN rolled in from outside: {}", rolled);
		check(rolled.dodged() == 0, "a dash rolled into her spin's circle from outside counts as dodged (" + rolled + ")");
		check(rolled.firstHit() >= 0 && rolled.firstHit() - (rolled.live() + 2) >= 4, "a dash rolled into her spin's circle from outside is hit while it lasts, or never after it (" + rolled + ")");

		// The guard held before her: blocked (no hurt).
		this.server.runOnServer(s -> {
			this.standoff(s, 2.2);
			StoryTesting.freshGuard(this.me(s));
			this.session(s).timers.remove("guard_broken");
			SaberAbilities.setStaminaForTests(this.me(s), AbilityConfig.get().staminaMax);
		});
		this.context.waitTicks(4);

		if (!this.raise()) {
			return;
		}

		this.context.waitTicks(10);
		int hurt0 = this.on(s -> this.session(s).counters.getOrDefault("spin_hurt", 0));
		int contact0 = this.on(s -> this.session(s).counters.getOrDefault("spin_contact", 0));
		int blocked0 = this.on(s -> this.session(s).counters.getOrDefault("blocked", 0) + this.session(s).counters.getOrDefault("perfect", 0));
		// (She is put back before the player first: she does not stand still while the guard comes up.)
		this.server.runOnServer(s -> {
			this.standoff(s, 2.2);
			StoryTesting.spinNow(this.session(s), this.her(s));
		});
		this.context.waitTicks((int) (n[1] + n[2]) + 4);
		boolean guarding = this.on(s -> SaberAbilities.isGuarding(this.me(s)));
		this.context.getInput().releaseKey(SaberAbilityClient.GUARD_KEY);
		int hurt1 = this.on(s -> this.session(s).counters.getOrDefault("spin_hurt", 0));
		int blocked1 = this.on(s -> this.session(s).counters.getOrDefault("blocked", 0) + this.session(s).counters.getOrDefault("perfect", 0));
		int contact1 = this.on(s -> this.session(s).counters.getOrDefault("spin_contact", 0));
		SaberCraft.LOGGER.info("PHASE30 SPIN guarded: hurt {}, blocked {}, contacts {}, guarding {}", hurt1 - hurt0, blocked1 - blocked0, contact1 - contact0, guarding);
		check(blocked1 > blocked0, "her spin is not blocked by the guard held before her");
		check(hurt1 == hurt0, "her spin hurts through the guard held before her");
		this.context.takeScreenshot("phase30_spin_after");
	}

	/**
	 * One spin of hers: the player that far before her (guard down), she spins; at {@code moveAt}
	 * ticks after it goes live (or never: -1) the player is put {@code moveTo} from her. With
	 * {@code evade} the player dodges (the server's side of a roll): as they are put there, or
	 * without a move a little before the circle appears, so that the roll is under way then.
	 */
	private Spin spinAt(double distance, int moveAt, double moveTo, boolean evade) {
		this.server.runOnServer(s -> this.standoff(s, distance));
		this.context.waitTicks(3);
		this.server.runOnServer(s -> this.standoff(s, distance));
		this.context.waitTicks(1);
		int hurt0 = this.on(s -> this.session(s).counters.getOrDefault("spin_hurt", 0));
		int contact0 = this.on(s -> this.session(s).counters.getOrDefault("spin_contact", 0));
		int dodged0 = this.on(s -> this.session(s).counters.getOrDefault("dodged", 0));
		int frames0 = this.context.computeOnClient(client -> MeshAudit.spinCircles());
		this.server.runOnServer(s -> StoryTesting.spinNow(this.session(s), this.her(s)));
		int live = -1;
		int end = -1;
		int firstHit = -1;
		int before = 0;
		int during = 0;
		int after = 0;
		boolean shot = false;

		for (int i = 0; i < 50; i++) {
			this.context.waitTick();
			boolean on = this.on(s -> StoryTesting.spinLive(this.her(s)));
			int frames = this.context.computeOnClient(client -> MeshAudit.spinCircles());
			int drawn = frames - frames0;
			frames0 = frames;

			if (on && live < 0) {
				live = i;
				double apart = this.on(s -> Math.hypot(this.session(s).local(Story.DESTROYER).x - this.her(s).x, this.session(s).local(Story.DESTROYER).z - this.her(s).z));
				SaberCraft.LOGGER.info("PHASE30 SPIN live: the player {} blocks from her", String.format(Locale.ROOT, "%.2f", apart));
			}

			if (!on && live >= 0 && end < 0) {
				end = i;
			}

			// (The client hears of the circle a tick or two after the server: frames count to the part they show.)
			if (live < 0) {
				before += drawn;
			} else if (end < 0 || i <= end + 2) {
				during += drawn;
			} else {
				after += drawn;
			}

			if (live >= 0 && !shot && i >= live + 3 && distance < 3.0 && moveAt < 0) {
				shot = true;
				this.context.takeScreenshot("phase30_spin_" + String.format(Locale.ROOT, "%.1f", distance));
			}

			if (firstHit < 0 && this.on(s -> this.session(s).counters.getOrDefault("spin_hurt", 0)) > hurt0) {
				firstHit = i;
			}

			if (moveAt >= 0 && live >= 0 && i == live + moveAt) {
				// (Only the player moves: she goes on spinning.)
				this.server.runOnServer(s -> {
					Figure f = this.her(s);
					Vec3 to = new Vec3(f.x, f.y, f.z - moveTo);
					this.teleport(s, this.session(s), to, 0.0F);

					if (evade) {
						SaberAbilities.evadeForTests(this.me(s));
					}
				});
			}

			// (The roll's evading moment lasts 7 ticks: begun 4 ticks into the spin-up of 8, it is under way as the circle appears.)
			if (evade && moveAt < 0 && i == 3) {
				this.server.runOnServer(s -> SaberAbilities.evadeForTests(this.me(s)));
			}

			if (end >= 0 && i > end + 6) {
				break;
			}
		}

		int hits = this.on(s -> this.session(s).counters.getOrDefault("spin_hurt", 0)) - hurt0;
		int contacts = this.on(s -> this.session(s).counters.getOrDefault("spin_contact", 0)) - contact0;
		int dodged = this.on(s -> this.session(s).counters.getOrDefault("dodged", 0)) - dodged0;
		// (Calm again before the next.)
		this.server.runOnServer(s -> StoryTesting.guard(this.her(s), 4000));
		this.context.waitTicks(10);
		return new Spin(live, end, firstHit, hits, contacts, dodged, before, during, after);
	}

	// --- 2: her saber with both blades, never in her body ------------------------------------------------------

	private void saberPoses() {
		if (!this.duel()) {
			return;
		}

		this.server.runOnServer(s -> StoryTesting.halfStrength(this.session(s), this.her(s), this.me(s)));
		this.film(30);
		this.context.waitTicks(5);
		this.skip("boss_mid");
		this.context.waitTicks(40);
		this.context.runOnClient(client -> client.gui.hud.toggle());
		this.server.runOnServer(s -> this.before(s, 3.0));
		int shots = 0;

		for (int t = 0; t < 600 && shots < 48; t += 4) {
			this.context.waitTicks(4);
			String act = this.on(s -> this.session(s).has("tmp.duel") ? StoryTesting.act(this.her(s)) : "over");

			if ("over".equals(act)) {
				break;
			}

			if (t == 200 || t == 400) {
				this.server.runOnServer(s -> StoryTesting.spinNow(this.session(s), this.her(s)));
			}

			if (!"guard".equals(act) || t % 32 == 0) {
				// Before her, looking at her chest (she faces the player).
				this.server.runOnServer(s -> {
					Figure f = this.her(s);
					Vec3 me = this.session(s).local(Story.DESTROYER);
					Vec3 away = new Vec3(me.x - f.x, 0.0, me.z - f.z);
					away = away.lengthSqr() < 0.01 ? new Vec3(0.0, 0.0, -1.0) : away.normalize();
					Vec3 to = f.position().add(away.scale(3.0));
					this.teleport(s, this.session(s), to, yaw(to, f.position()));
					this.me(s).setXRot(8.0F);
				});
				this.context.waitTick();
				this.context.takeScreenshot("phase30_pose_" + shots++ + "_" + act);
			}
		}

		this.context.runOnClient(client -> client.gui.hud.toggle());
		this.context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
		List<String> lines = this.context.computeOnClient(client -> MeshAudit.ringInBody());
		SaberCraft.LOGGER.info("PHASE30 RING IN BODY (the clearing's own margin): {}", lines.get(0));
		check(lines.size() == 1, "her saber stays in her body after it is kept clear: " + lines.subList(1, Math.min(lines.size(), 6)));
		List<String> drawn = this.context.computeOnClient(client -> MeshAudit.ringInDrawnBody());
		// (Its first line counts the poses and those touching, its second how often the clearing keeps her hands off the grip.)
		SaberCraft.LOGGER.info("PHASE30 RING IN DRAWN BODY: {}", drawn.get(0));
		SaberCraft.LOGGER.info("PHASE30 RING HANDS: {}", drawn.get(1));
		// (Phase 29's holding and clearing on the same poses, measured outside the game: 59891 / 22426 / 60012. Her hands are to reach the grip about as
		// often as then with the ring still, and far more often with it spinning.)
		java.util.regex.Matcher hands = java.util.regex.Pattern.compile("first phase (\\d+) of \\d+, second phase (\\d+) of \\d+ with the ring still, (\\d+) of").matcher(drawn.get(1));
		check(hands.find() && Integer.parseInt(hands.group(1)) <= 66000 && Integer.parseInt(hands.group(2)) <= 27000 && Integer.parseInt(hands.group(3)) <= 30000,
				"her hands fall short of the grip far more often than in Phase 29: " + drawn.get(1));
		drawn.stream().skip(2).limit(10).forEach(line -> SaberCraft.LOGGER.info("PHASE30 RING TOUCHES: {}", line));
		check(drawn.size() == 2, "her saber touches her body as drawn: " + drawn.subList(2, Math.min(drawn.size(), 7)));
	}

	// --- 3: the finale's troopers ------------------------------------------------------------------------------

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

		for (int i = 0; i < 400; i++) {
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

		SaberCraft.LOGGER.info("PHASE30 SHOTS: {} in 20 s at ticks {} (least gap {}; between shots {}..{}, a second shot after {}, between troopers {})", last - first, at,
				least, timing[0], timing[0] + timing[1] - 1, timing[2], timing[3]);
		// (Phase 33: they shoot a little more often again: every 16 to 27 ticks.)
		check(timing[0] == 16 && timing[1] == 12, "the shot cooldown is not the new one (SHOT_MIN 16, SHOT_RANDOM 12): " + timing[0] + " + " + timing[1]);
		check(last - first >= 5, "the finale's troopers hardly shoot (" + (last - first) + " bolts in 20 seconds)");
		check(together == 0, "two bolts fired in the same moment (" + together + ")");
		check(at.size() < 2 || least >= timing[3] - 1, "two bolts closer together than the fair gap (" + least + " ticks)");
	}

	// --- 4: under the reactor's entrance landing -----------------------------------------------------------------

	private void landing() {
		this.enter(3, "m4.lift");
		this.unseen();
		float[][] views = {{40.0F, R, 13.0F, -90.0F, -40.0F}, {42.5F, R, 13.5F, -90.0F, -55.0F}, {44.0F, R, 15.0F, -60.0F, -50.0F}, {48.6F, R, 18.5F, 180.0F, -45.0F},
				{48.6F, R, 16.0F, 180.0F, -60.0F}, {46.0F, R, 19.0F, -150.0F, -45.0F}, {50.5F, R, 21.0F, 160.0F, -40.0F}, {38.5F, R, 17.0F, -120.0F, -35.0F}};

		for (int i = 0; i < views.length; i++) {
			float[] v = views[i];
			this.put(new Vec3(v[0], v[1], v[2]), v[3], v[4]);
			this.context.waitTicks(6);
			this.context.takeScreenshot("phase30_landing_" + i);
		}

		// No block's own face (blackstone) shows in the shaft in front of its round wall.
		List<String> bare = this.context.computeOnClient(client -> MeshAudit.bareInShaft());

		for (String line : bare) {
			SaberCraft.LOGGER.info("PHASE30 BARE IN SHAFT: {}", line);
		}

		check(bare.size() == 1, "blocks show their own faces in the reactor shaft: " + bare);
	}

	// --- 5: the reactor's lift ---------------------------------------------------------------------------------

	private void lift() {
		this.enter(5, "m6.reactor");
		this.unseen();
		List<InteriorPlan.Lift> lifts = InteriorPlan.of("star_destroyer").lifts();
		InteriorPlan.Lift down = lifts.get(2);
		InteriorPlan.Lift up = lifts.get(3);
		int rides = ShipInteriors.liftRidesForTest();
		int shown0 = this.context.computeOnClient(client -> LiftHud.shownForTest());
		int lit0 = this.context.computeOnClient(client -> LiftHud.litForTest());
		// Down from the deck level: off it first (so that it wakes for one stepping on), then onto it, standing still.
		Vec3 top = down.area().getCenter();
		this.put(new Vec3(top.x - 2.0, down.area().minY, top.z), -90.0F, 20.0F);
		this.context.waitTicks(5);
		this.server.runOnServer(s -> this.teleport(s, this.session(s), new Vec3(top.x, down.area().minY, top.z), -90.0F));
		List<int[]> seen = new ArrayList<>();
		boolean shot = false;

		for (int i = 0; i < down.ticks() + 30 && ShipInteriors.liftRidesForTest() == rides; i++) {
			this.context.waitTick();
			int[] state = this.context.computeOnClient(client -> LiftHud.stateForTest());
			seen.add(state);

			if (!shot && state[0] >= down.ticks() / 2) {
				shot = true;
				this.context.takeScreenshot("phase30_lift_down");
				this.context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
				this.context.waitTick();
				this.context.takeScreenshot("phase30_lift_down_posts");
				this.context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
			}
		}

		this.context.waitTicks(2);
		int[] arrived = this.context.computeOnClient(client -> LiftHud.stateForTest());
		this.context.takeScreenshot("phase30_lift_arrived");
		int going = 0;
		int rising = 0;
		boolean wentDown = false;

		for (int k = 0; k < seen.size(); k++) {
			if (seen.get(k)[0] >= 0) {
				going++;
				wentDown |= seen.get(k)[2] == 1;

				if (k > 0 && seen.get(k)[0] > seen.get(k - 1)[0]) {
					rising++;
				}
			}
		}

		Vec3 at = this.on(s -> this.session(s).local(Story.DESTROYER));
		int shown = this.context.computeOnClient(client -> LiftHud.shownForTest()) - shown0;
		SaberCraft.LOGGER.info("PHASE30 LIFT: ride shown {} ticks ({} rising), down {}, panel frames {}, arrived {}, at {}", going, rising, wentDown, shown,
				java.util.Arrays.toString(arrived), at);
		check(ShipInteriors.liftRidesForTest() == rides + 1 && Math.abs(at.y - down.arrival().y) < 0.6, "the reactor's lift does not go down (" + at + ")");
		check(going >= down.ticks() - 6 && rising >= down.ticks() - 8, "the client does not see the lift's ride go on (" + going + " ticks, " + rising + " rising)");
		check(wentDown, "the lift's panel does not say it goes down");
		check(shown >= going / 2, "the lift's panel is not drawn while it goes (" + shown + " frames)");
		check(arrived[3] >= 0, "the lift's panel does not show the arrival");
		int lit = this.context.computeOnClient(client -> LiftHud.litForTest()) - lit0;
		check(lit >= going / 2, "the light on the lift's posts is not drawn while it goes (" + lit + " frames)");
		// The seconds left, written as the mod's language writes them (a comma in German).
		String seconds = this.context.computeOnClient(client -> LiftHud.secondsText(0.94F));
		boolean german = this.context.computeOnClient(client -> com.phiro.sabercraft.client.lang.SaberLanguage.GERMAN.equals(com.phiro.sabercraft.client.lang.SaberLanguage.effective()));
		check(seconds.equals(german ? "0,9" : "0.9"), "the lift's seconds are written \"" + seconds + "\" (German: " + german + ")");

		// Moving on it (fighting): the ride is not shown going on, and it ends when one steps off.
		this.context.waitTicks(up.cooldown() + 10);
		Vec3 bottom = up.area().getCenter();
		Vec3 a = new Vec3(bottom.x - 0.35, up.area().minY, bottom.z - 0.3);
		Vec3 b = new Vec3(bottom.x + 0.35, up.area().minY, bottom.z + 0.3);
		int most = -1;
		int shownMoving = this.context.computeOnClient(client -> LiftHud.shownForTest());

		for (int i = 0; i < 40; i++) {
			Vec3 to = i % 4 < 2 ? b : a;
			this.server.runOnServer(s -> this.teleport(s, this.session(s), to, 0.0F));
			this.context.waitTick();
			most = Math.max(most, this.context.computeOnClient(client -> LiftHud.stateForTest())[0]);
		}

		shownMoving = this.context.computeOnClient(client -> LiftHud.shownForTest()) - shownMoving;
		this.put(new Vec3(bottom.x - 2.5, up.area().minY, bottom.z), 90.0F, 0.0F);
		this.context.waitTicks(10);
		int[] off = this.context.computeOnClient(client -> LiftHud.stateForTest());
		SaberCraft.LOGGER.info("PHASE30 LIFT: moving on it the ride went at most to {} (panel frames {}); stepped off: {}", most, shownMoving, java.util.Arrays.toString(off));
		check(most < up.ticks() / 2, "moving on the lift, its ride is shown going on (" + most + ")");
		check(shownMoving == 0, "moving on the lift, its panel flashes up (" + shownMoving + " frames)");
		check(off[0] < 0, "stepped off the lift, its ride is still shown");
		// The panel's place and the progress bar's keep clear of the line over the hotbar (Minecraft's:
		// a room's name, a message) while it shows, on a screen 240 high (the test's). First wait
		// until nothing else is there (no hint, no title card, no such line), so that the line alone
		// is what moves a box.
		for (int i = 0; i < 100 && !this.context.computeOnClient(client -> StoryClient.hintForTest() == null && !StoryClient.titleShowing()
				&& ((HudAccessor) client.gui.hud).sabercraft$overlayMessageTime() == 0); i++) {
			this.context.waitTick();
		}

		int[] place = this.context.computeOnClient(client -> {
			int height = 240;
			int width = client.getWindow().getGuiScaledWidth();
			boolean quiet = StoryClient.hintForTest() == null && !StoryClient.titleShowing() && ((HudAccessor) client.gui.hud).sabercraft$overlayMessageTime() == 0;
			// (A box 38 high, first tried under the crosshair, where it would cover the line.)
			int boxBefore = StoryHud.clearTop(client.font, width, height, 38, height / 2 + 26, height / 2 - 12 - 38);
			int barBefore = StoryHud.progressTop(client.font, width, height, 0, 0);
			client.gui.hud.setOverlayMessage(net.minecraft.network.chat.Component.literal("Reaktor"), false);
			int box = StoryHud.clearTop(client.font, width, height, 38, height / 2 + 26, height / 2 - 12 - 38);
			int bar = StoryHud.progressTop(client.font, width, height, 0, 0);
			int panel = LiftHud.panelTop(client.font, width, height);
			int words = height - 72 - (client.options.getBackgroundColor(0.0F) != 0 ? 2 : 0);
			return new int[] {quiet ? 1 : 0, boxBefore, box, barBefore, bar, panel, words};
		});
		SaberCraft.LOGGER.info("PHASE30 LIFT: the line over the hotbar from {} (screen 240 high, quiet {}): a box under the crosshair at {} without it, at {} with it;"
				+ " the progress bar at {} and {}; the lift's panel at {}", place[6], place[0] == 1, place[1], place[2], place[3], place[4], place[5]);
		check(place[0] == 1, "the screen did not get quiet for the check of the line over the hotbar (a hint, a title card or a message stayed)");
		check(place[1] == 240 / 2 + 26, "without the line over the hotbar a box does not go under the crosshair (" + place[1] + ")");
		check(place[2] + 38 <= place[6], "with a room's name over the hotbar a box still covers it (" + place[2] + ")");
		check(place[4] + StoryHud.PROGRESS_BOX <= place[6], "with a room's name over the hotbar the progress bar covers it (" + place[4] + ")");
		check(place[3] == place[4], "the progress bar moves for a room's name it does not reach (" + place[3] + ", then " + place[4] + ")");
		check(place[5] + LiftHud.PANEL_HEIGHT <= place[6], "with a room's name over the hotbar the lift's panel covers it (" + place[5] + ")");
	}

	// --- 6: the maintenance door's release -------------------------------------------------------------------------

	private void release() {
		this.enter(4, "m5.escape");
		this.unseen();
		// The objective and the marker lead to the door's release.
		String objective = this.on(s -> StoryTesting.objective(this.session(s)));
		Vec3 marker = this.on(s -> { Vec3 m = StoryTesting.marker(this.session(s)); return m == null ? null : m.subtract(Vec3.atLowerCornerOf(this.session(s).destroyer)); });
		SaberCraft.LOGGER.info("PHASE30 RELEASE: objective {}, marker {}", objective, marker);
		check("sabercraft.story.obj.m5.escape_release".equals(objective), "the objective does not say to unlock the maintenance door (" + objective + ")");
		check(marker != null && marker.distanceTo(DestroyerInterior.SERVICE_RELEASE.getCenter()) < 1.2, "the marker does not point at the door's release (" + marker + ")");
		check(this.on(s -> StoryTesting.point(this.session(s), "service_door") != null), "the shut door itself cannot be used");
		// Phase 32: no control text on the walkway any more (Phiro: no explaining texts in the game);
		// walking up to the door shows no hint, the objective and the panel's prompt lead.
		this.put(new Vec3(48.6, R, 16.0), 180.0F, 5.0F);
		this.context.waitTicks(10);
		String walkHint = this.context.computeOnClient(client -> StoryClient.hintForTest());
		// (The mission's own hint of the way, from the escape's start, may still show: it is no control text.)
		check(walkHint == null || "sabercraft.story.hint.service_way".equals(walkHint) || !walkHint.contains("service"),
				"walking up to the locked door still shows a control text (" + walkHint + ")");
		this.context.takeScreenshot("phase30_release_door");
		// One click on the panel (as the client sends it), then walk away and look elsewhere.
		this.put(new Vec3(48.6, R, 13.0), 90.0F, 10.0F);
		// (While the mission's title card still shows: the release's bar is drawn under it too.)
		boolean title = this.context.computeOnClient(client -> StoryClient.titleShowing());
		int[] frames0 = this.context.computeOnClient(client -> StoryHud.progressFramesForTest());
		int releases = this.on(s -> StoryTesting.releases());
		this.context.runOnClient(client -> StoryClient.send(StoryActionPayload.USE, "service_release", 0));
		int clicked = this.on(MinecraftServer::getTickCount);
		this.context.waitTicks(2);
		check(this.on(s -> this.session(s).has("tmp.release")), "one click on the release does not begin it");
		// Phase 32: no "it unlocks by itself" hint or objective on the click (only the bar runs).
		check("sabercraft.story.obj.m5.escape_release".equals(this.on(s -> StoryTesting.objective(this.session(s)))), "the objective changed while the door unlocks");
		String clickHint = this.context.computeOnClient(client -> StoryClient.hintForTest());
		check(clickHint == null || !clickHint.contains("service_running"), "the click still tells the player the door unlocks by itself (" + clickHint + ")");
		this.context.waitTicks(4);
		String bar = this.context.computeOnClient(client -> StoryClient.progressKeyForTest());
		int[] frames1 = this.context.computeOnClient(client -> StoryHud.progressFramesForTest());
		SaberCraft.LOGGER.info("PHASE30 RELEASE: clicked with the title card showing {}, the bar {}, drawn {} frames ({} under the title card)", title, bar,
				frames1[0] - frames0[0], frames1[1] - frames0[1]);
		check(title, "the mission's title card was gone before the click (the test needs it there)");
		check("sabercraft.story.hint.service_release".equals(bar), "the release's bar is not told to the client (" + bar + ")");
		check(frames1[1] > frames0[1], "the release's bar is not drawn while the mission's title card shows");
		this.put(new Vec3(49.0, R, 19.5), 0.0F, -10.0F);

		for (int i = 0; i < 80 && !this.on(s -> this.session(s).has("service.open")); i++) {
			if (i == 4) {
				this.context.takeScreenshot("phase30_release_running");
			}

			this.context.waitTick();
		}

		// (Counted in server ticks from the click: the player was moved away meanwhile.)
		int ticks = this.on(MinecraftServer::getTickCount) - clicked;
		SaberCraft.LOGGER.info("PHASE30 RELEASE: open {} server ticks after the click, the player away from it (it takes {})", ticks, StoryTesting.releaseTicks());
		check(this.on(s -> this.session(s).has("service.open")), "the release does not finish by itself with the player away from it");
		check(ticks >= StoryTesting.releaseTicks() - 2 && ticks <= StoryTesting.releaseTicks() + 6 && StoryTesting.releaseTicks() < 40,
				"the release does not take its time (" + ticks + " ticks, it takes " + StoryTesting.releaseTicks() + ")");
		check(this.on(s -> StoryTesting.releases()) == releases + 1, "the release was not counted once");
		check("sabercraft.story.obj.m5.escape_bolt".equals(this.on(s -> StoryTesting.objective(this.session(s)))), "after the release the objective does not say to bolt the door");
		marker = this.on(s -> { Vec3 m = StoryTesting.marker(this.session(s)); return m == null ? null : m.subtract(Vec3.atLowerCornerOf(this.session(s).destroyer)); });
		check(marker != null && marker.distanceTo(DestroyerInterior.SERVICE_LOCK.getCenter()) < 1.2, "after the release the marker does not point at the bolt panel (" + marker + ")");
		// Unlocked, the door opens like any door for the player coming to it, with no second click.
		InteriorPlan.Door service = DestroyerInterior.DOORS.get(DestroyerInterior.DOOR_SERVICE);
		this.put(new Vec3(48.9, R, 11.2), 180.0F, 5.0F);
		boolean opened = false;

		for (int i = 0; i < 20 && !opened; i++) {
			this.context.waitTick();
			opened = this.on(s -> InteriorSystems.doorOpenForTest(this.level(s), this.session(s).destroyer, service));
		}

		check(opened, "after the release the door does not open for the player coming to it");

		// The bolt panel reached through the open doorway from outside: it does not bolt the door
		// (that would shut the player out of the crawl way); from behind the door it does, and the
		// objective leads into the shaft.
		this.put(new Vec3(49.0, R, 11.6), 180.0F, 10.0F);
		this.context.runOnClient(client -> StoryClient.send(StoryActionPayload.USE, "service_lock", 0));
		this.context.waitTicks(3);
		check(!this.on(s -> this.session(s).has("service.locked")), "the door is bolted from outside (the player shut out of the crawl way)");
		this.put(new Vec3(49.0, R, 8.0), 90.0F, 10.0F);
		this.context.runOnClient(client -> StoryClient.send(StoryActionPayload.USE, "service_lock", 0));
		this.context.waitTicks(3);
		String inside = this.on(s -> StoryTesting.objective(this.session(s)));
		check(this.on(s -> this.session(s).has("service.locked")), "the door cannot be bolted from behind it");
		check("sabercraft.story.obj.m5.escape_shaft".equals(inside), "bolted from behind the door, the objective does not lead into the shaft (" + inside + ")");

		// Again, by a click on the door itself.
		this.enter(4, "m5.escape");
		this.unseen();
		this.put(new Vec3(48.8, R, 12.4), 180.0F, 0.0F);
		this.context.runOnClient(client -> StoryClient.send(StoryActionPayload.USE, "service_door", 0));
		this.context.waitTicks(2);
		check(this.on(s -> this.session(s).has("tmp.release")), "a click on the door does not begin the release");
	}


	// --- Helpers ------------------------------------------------------------------------------------------------------------

	/** She stands calm in the middle of the hangar, the player that far north of her facing her, she facing them. */
	private void standoff(MinecraftServer s, double distance) {
		this.calm(s, 40.5, -30.0, 180.0F);
		Figure f = this.her(s);
		Vec3 to = new Vec3(f.x, f.y, f.z - distance);
		this.teleport(s, this.session(s), to, 0.0F);
		this.me(s).setDeltaMovement(Vec3.ZERO);
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

	/** Her: the Schattenklinge. */
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

	/** On guard, quiet, the player that far before her (as they stood), she facing them and they her. */
	private void faced(MinecraftServer s, double distance) {
		Figure f = this.her(s);
		StoryTesting.guard(f, 4000);
		f.react = 0;
		f.reactTicks = 0;
		f.speed = 0.0F;
		this.before(s, distance);
		float yaw = com.phiro.sabercraft.interior.Crew.yaw(f.position(), this.session(s).local(Story.DESTROYER));
		f.yaw = yaw;
		f.headYaw = yaw;
	}

	/** Puts the player that far behind her (where she looks away from), facing her. */
	private void behind(MinecraftServer s, double distance) {
		Figure f = this.her(s);
		Vec3 look = f.look();
		Vec3 flat = new Vec3(look.x, 0.0, look.z).normalize();
		Vec3 to = f.position().subtract(flat.scale(distance));
		this.teleport(s, this.session(s), to, yaw(to, f.position()));
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

	/** A stroke with the attack key (the crosshair on her). */
	private void strike() {
		this.context.getInput().pressKey(options -> options.keyAttack);
		this.context.waitTicks(3);
	}

	/** The duel begun: the story at it, the films and her words skipped, the player with a saber and unhurt. */
	private boolean duel() {
		this.enter(5, "m6.duel");
		// (Both droids with the player, as at this point of the story: the test's file came from other parts.)
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
			} else if (this.on(s -> this.session(s).has("tmp.duel"))) {
				return;
			} else if (this.on(s -> this.session(s).dialog != null)) {
				this.server.runOnServer(s -> StoryTesting.answer(this.session(s), 0));
			}

			this.context.waitTicks(5);
		}
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

	/** Whether nothing is right under the player's feet (by the world, not by the on-ground flag their game sent). */
	private boolean airborne(MinecraftServer s) {
		ServerPlayer player = this.me(s);
		net.minecraft.world.phys.AABB box = player.getBoundingBox();
		return player.level().noCollision(player, new net.minecraft.world.phys.AABB(box.minX, box.minY - 0.1, box.minZ, box.maxX, box.minY, box.maxZ));
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
		SaberCraft.LOGGER.error("PHASE30 CHECK FAILED: {}", problem);
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
