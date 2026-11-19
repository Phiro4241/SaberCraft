package com.phiro.sabercraft.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.client.droid.DroidControl;
import com.phiro.sabercraft.client.lang.SaberLanguage;
import com.phiro.sabercraft.client.story.StoryClient;
import com.phiro.sabercraft.droid.DroidEntity;
import com.phiro.sabercraft.droid.DroidType;
import com.phiro.sabercraft.droid.Droids;
import com.phiro.sabercraft.interior.Crew;
import com.phiro.sabercraft.interior.DestroyerInterior;
import com.phiro.sabercraft.interior.ShipInteriors;
import com.phiro.sabercraft.network.StoryActionPayload;
import com.phiro.sabercraft.story.Companions;
import com.phiro.sabercraft.story.Figure;
import com.phiro.sabercraft.story.Story;
import com.phiro.sabercraft.story.StorySession;
import com.phiro.sabercraft.story.StoryTesting;

/**
 * Phase 21: the enemies, the alarm and the droids, each looked at on its own in Mission 6 (both
 * droids along, the ship on alert); the duel is played in {@link StoryGameTest}.
 *
 * <ul>
 * <li>The droids keep their own places behind the player (not at the heels, not in front, not in
 * each other), also after the player has moved on ({@code phase21_droids_*}).</li>
 * <li>M-31's noise: the nearest who hears goes to see, the second time he only looks, the third
 * time he does not care; sent off once, M-31 cannot be sent again at once.</li>
 * <li>Seen: the trooper fires stun bolts from a distance (he does not just run at the player), and
 * the player is caught (three hits or seized) ({@code phase21_chase_*}).</li>
 * <li>Out of sight: they search the last known place, the alarm stays; when it ends the ship stays
 * wary and the reinforcements stay as patrols.</li>
 * </ul>
 */
public class Phase21GameTest implements FabricClientGameTest {
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
			context.waitTicks(100);
			this.server = singleplayer.getServer();
			this.server.runCommand("gamerule advance_time false");
			this.server.runCommand("gamerule spawn_mobs false");
			this.server.runCommand("time set day");
			this.server.runCommand("gamemode survival @a");
			context.waitTicks(20);
			this.self = context.computeOnClient(client -> client.player.getUUID());
			context.runOnClient(client -> SaberLanguage.set(SaberLanguage.GERMAN));
			this.server.runOnServer(s -> StoryTesting.unlockAll(s, this.self));
			// Mission 6: both droids, the ship on alert.
			context.runOnClient(client -> StoryClient.send(StoryActionPayload.MISSION, "", 5));
			context.waitTicks(80);
			check(this.on(s -> this.session(s) != null && StoryTesting.step(this.session(s)).startsWith("m6.")), "Mission 6 does not start: "
					+ this.on(s -> this.session(s) == null ? "-" : StoryTesting.step(this.session(s))));
			this.server.runOnServer(s -> StoryTesting.unseen(this.session(s), 1_000_000));

			this.droids();
			this.noise();
			this.chase();
			this.search();

			context.runOnClient(client -> StoryClient.send(StoryActionPayload.LEAVE, "", 0));
			context.waitTicks(40);
		} finally {
			context.runOnClient(client -> SaberLanguage.set(SaberLanguage.AUTO));
		}

		if (!this.problems.isEmpty()) {
			this.problems.forEach(problem -> SaberCraft.LOGGER.error("PHASE21 PROBLEM: {}", problem));
			throw new AssertionError("Phase 21: " + this.problems.size() + " problem(s): " + String.join(" | ", this.problems));
		}
	}

	// --- The droids ------------------------------------------------------------------------------

	/** Both droids keep their own places behind the player, apart, also after the player moved on. */
	private void droids() {
		Vec3[] stands = {new Vec3(40.5, H, -10.0), new Vec3(40.5, H, -25.0)};
		float[] yaws = {180.0F, 90.0F};

		for (int i = 0; i < stands.length; i++) {
			Vec3 stand = stands[i];
			this.place(stand, yaws[i], 20.0F);

			if (i == 0) {
				this.server.runOnServer(s -> {
					for (DroidType type : new DroidType[] {DroidType.MOUSE, DroidType.R2D2}) {
						DroidEntity droid = Droids.droid(this.me(s), type);

						if (droid != null) {
							droid.teleportToOwner();
						}
					}
				});
			}

			this.context.waitTicks(i == 0 ? 100 : 180);
			String where = "at " + stand;
			Object[] seen = this.on(s -> {
				ServerPlayer player = this.me(s);
				DroidEntity mouse = Droids.droid(player, DroidType.MOUSE);
				DroidEntity r2 = Droids.droid(player, DroidType.R2D2);

				if (mouse == null || r2 == null) {
					return null;
				}

				Vec3 look = Vec3.directionFromRotation(0.0F, player.getYRot());
				Vec3 me = player.position();
				double frontMouse = mouse.position().subtract(me).normalize().dot(look);
				double frontR2 = r2.position().subtract(me).normalize().dot(look);
				Vec3 mouseSpot = StoryTesting.droidSpot(mouse, player);
				Vec3 r2Spot = StoryTesting.droidSpot(r2, player);
				SaberCraft.LOGGER.info("PHASE21: player {}, M-31 {} (spot {}), R2-V7 {} (spot {})", me, mouse.position(), mouseSpot, r2.position(), r2Spot);
				return new Object[] {mouse.position().distanceTo(me), r2.position().distanceTo(me), mouse.position().distanceTo(r2.position()), frontMouse,
						frontR2, mouseSpot != null};
			});

			if (seen == null) {
				this.problem("the droids are not along in Mission 6");
				return;
			}

			double mouseAway = (double) seen[0];
			double r2Away = (double) seen[1];
			double apart = (double) seen[2];
			SaberCraft.LOGGER.info("PHASE21: droids {}: M-31 {} away, R2-V7 {} away, {} apart, in front {} / {}", where, fmt(mouseAway), fmt(r2Away), fmt(apart),
					fmt((double) seen[3]), fmt((double) seen[4]));
			check((boolean) seen[5], "no place for the droids to keep " + where);
			check(mouseAway > 1.0 && mouseAway < 5.0 && r2Away > 1.4 && r2Away < 6.0, "the droids are not at their places near the player " + where);
			check(apart > 0.8, "the droids stand in each other " + where);
			check((double) seen[3] < 0.3 && (double) seen[4] < 0.3, "a droid stands in front of the player " + where);
			// Seen from in front and above, so that the places behind the player are in the picture.
			this.context.runOnClient(client -> {
				client.player.setXRot(-30.0F);
				client.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
			});
			this.context.waitTicks(4);
			this.context.takeScreenshot("phase21_droids_" + i);
			this.context.runOnClient(client -> {
				client.player.setXRot(10.0F);
				client.options.setCameraType(CameraType.FIRST_PERSON);
			});
		}
	}

	// --- M-31's noise ----------------------------------------------------------------------------

	/** Who hears it goes to see; the second time they only look, the third time they do not care; M-31 needs a rest. */
	private void noise() {
		String key = this.on(s -> {
			for (Figure figure : this.session(s).figures) {
				// (Phase 27: someone standing: one on a seat only looks over, he does not get up.)
				if (figure.observer && Story.DESTROYER.equals(figure.ship) && figure.mode == Figure.ROUTINE && Math.abs(figure.y - H) < 1.0 && !figure.seated()) {
					return figure.key;
				}
			}

			return null;
		});

		if (key == null) {
			this.problem("nobody on the deck to hear M-31");
			return;
		}

		int[][] heard = new int[3][];

		for (int i = 0; i < 3; i++) {
			int round = i;
			heard[i] = this.on(s -> {
				StorySession session = this.session(s);
				Figure figure = session.figure(key);
				figure.snapToRoutine(session.time());
				int[] counts = StoryTesting.noise(session, figure.position().add(2.0, 0.0, 1.0));
				return new int[] {counts[0], counts[1], counts[2], figure.mode, figure.distracted, round};
			});
			SaberCraft.LOGGER.info("PHASE21: noise {} by {}: went {}, looked {}, ignored {}, mode {}, drawn {} times", i + 1, key, heard[i][0], heard[i][1],
					heard[i][2], heard[i][3], heard[i][4]);
		}

		check(heard[0][0] >= 1 && heard[0][3] == Figure.LOOK, "the nearest who hears M-31 does not go to see (mode " + heard[0][3] + ")");
		check(heard[1][4] == 2 && heard[1][3] == Figure.LOOK && heard[1][0] == 0 || heard[1][0] > 0 && heard[1][3] != Figure.LOOK,
				"the second time " + key + " goes to see again");
		check(heard[2][2] >= 1 && heard[2][3] == Figure.ROUTINE, "the third time " + key + " still reacts to M-31");
		this.server.runOnServer(s -> this.session(s).figure(key).snapToRoutine(this.session(s).time()));

		// Sent off once, M-31 cannot be sent again at once.
		this.place(new Vec3(40.5, H, -10.0), 180.0F, 35.0F);
		this.server.runOnServer(s -> {
			DroidEntity mouse = Droids.droid(this.me(s), DroidType.MOUSE);

			if (mouse != null) {
				mouse.teleportToOwner();
			}

			this.session(s).timers.remove("lure");
		});
		this.context.waitTicks(20);
		int before = Companions.luresForTest();
		this.context.getInput().pressKey(DroidControl.ABILITY_KEY);
		this.context.waitTicks(10);
		this.context.getInput().pressKey(DroidControl.ABILITY_KEY);
		this.context.waitTicks(10);
		int sent = Companions.luresForTest() - before;
		check(sent == 1, "M-31 was sent off " + sent + " times by two quick presses (once expected)");

		for (int i = 0; i < 40 && this.on(s -> this.session(s).has("tmp.lure")); i++) {
			this.context.waitTicks(5);
		}

		check(!this.on(s -> this.session(s).has("tmp.lure")), "M-31 does not come back from the noise");
		check(this.on(s -> this.session(s).timer("lure")), "M-31 can be sent again right after a noise (no rest)");
	}

	// --- Seen ------------------------------------------------------------------------------------

	/** Seen by a trooper: he shoots stun bolts from a distance, and the player is caught in the end. */
	private void chase() {
		int captures = this.on(s -> Story.capturesForTest());
		int shots = this.on(s -> StoryTesting.stunShots());
		Object[] spot = this.spotInFront(9.0);

		if (spot == null) {
			this.problem("no trooper on the deck to be seen by");
			return;
		}

		String key = (String) spot[0];
		Vec3 at = (Vec3) spot[1];
		Vec3 eye = (Vec3) spot[2];
		this.place(at, aimYaw(at.add(0.0, 1.62, 0.0), eye), 0.0F);
		this.server.runOnServer(s -> {
			StorySession session = this.session(s);
			StoryTesting.unseen(session, 0);
			StoryTesting.detect(session, session.figure(key));
		});
		double closest = Double.MAX_VALUE;
		boolean shot = false;
		boolean caught = false;

		for (int i = 0; i < 80 && !caught; i++) {
			this.context.waitTicks(5);
			double distance = this.on(s -> {
				Figure figure = this.session(s).figure(key);
				return figure == null ? Double.MAX_VALUE : figure.position().distanceTo(this.session(s).local(Story.DESTROYER));
			});
			closest = Math.min(closest, distance);

			if (!shot && this.on(s -> StoryTesting.stunShots()) > shots) {
				shot = true;
				SaberCraft.LOGGER.info("PHASE21: the first stun bolt at {} blocks", fmt(distance));
				this.context.takeScreenshot("phase21_chase_shot");
				check(distance > 3.0, "the trooper ran up to the player before shooting (" + fmt(distance) + " blocks)");
			}

			caught = this.on(s -> Story.capturesForTest()) > captures;
		}

		check(shot, "the trooper who saw the player never shoots");
		check(caught, "the player standing in the open is not caught");
		SaberCraft.LOGGER.info("PHASE21: chase: closest {} blocks, {} stun hits so far, caught {}", fmt(closest), this.on(s -> Story.stunHitsForTest()), caught);
		this.context.waitTicks(30);
		this.server.runOnServer(s -> StoryTesting.unseen(this.session(s), 1_000_000));
	}

	// --- Out of sight ----------------------------------------------------------------------------

	/** Seen, then hidden: they search the last place; the alarm stays meanwhile; afterwards the ship stays wary, with patrols. */
	private void search() {
		Object[] spot = this.spotInFront(9.0);

		if (spot == null) {
			this.problem("no trooper on the deck for the search");
			return;
		}

		String key = (String) spot[0];
		Vec3 at = (Vec3) spot[1];
		Vec3 eye = (Vec3) spot[2];
		int waryBefore = this.on(s -> StoryTesting.wary(this.session(s)));
		this.place(at, aimYaw(at.add(0.0, 1.62, 0.0), eye), 0.0F);
		this.server.runOnServer(s -> {
			StorySession session = this.session(s);
			StoryTesting.unseen(session, 0);
			StoryTesting.detect(session, session.figure(key));
		});
		this.context.waitTicks(4);
		// Gone: into the hideout behind its hidden door (nobody sees in there).
		Vec3 hide = new Vec3(DestroyerInterior.HIDDEN.getCenter().x, H, DestroyerInterior.HIDDEN.getCenter().z);
		this.place(hide, 90.0F, 0.0F);
		this.server.runOnServer(s -> StoryTesting.unseen(this.session(s), 1_000_000));
		boolean searching = false;

		for (int i = 0; i < 60 && !searching; i++) {
			this.context.waitTicks(5);
			searching = this.on(s -> StoryTesting.searching(this.session(s)));
		}

		check(searching, "out of sight they do not search");
		check(this.on(s -> this.session(s).alarm > 0), "the alarm ends as soon as the player is out of sight");
		int searchers = this.on(s -> {
			int count = 0;

			for (Figure figure : this.session(s).figures) {
				if (figure.mode == Figure.INVESTIGATE || figure.mode == Figure.CHASE) {
					count++;
				}
			}

			return count;
		});
		check(searchers > 0, "nobody goes to search the last known place");
		this.context.waitTicks(80);
		int reinforcements = this.on(s -> {
			int count = 0;

			for (Figure figure : this.session(s).figures) {
				if (figure.key.startsWith(StoryTesting.reinforcementPrefix())) {
					count++;
				}
			}

			return count;
		});
		SaberCraft.LOGGER.info("PHASE21: search: {} searching, {} reinforcements", searchers, reinforcements);
		check(reinforcements > 0, "no reinforcements come after the alarm");

		// The alarm ends: the ship stays wary, the reinforcements stay as patrols.
		this.server.runOnServer(s -> StoryTesting.endAlarm(this.session(s)));

		for (int i = 0; i < 80 && this.on(s -> this.session(s).alarm > 0); i++) {
			this.context.waitTicks(5);
		}

		this.context.waitTicks(10);
		int wary = this.on(s -> StoryTesting.wary(this.session(s)));
		int patrols = this.on(s -> StoryTesting.patrols(this.session(s)));
		SaberCraft.LOGGER.info("PHASE21: after the search: wary {} (was {}), {} patrols", wary, waryBefore, patrols);
		check(this.on(s -> this.session(s).alarm == 0), "the alarm does not end");
		check(wary > waryBefore, "after the search the ship is not more wary than before");
		check(patrols > 0, "no search patrol stays after the alarm");
	}

	// --- Helpers ---------------------------------------------------------------------------------

	/** A trooper on the deck standing still, and a free place in front of him (in his sight) that far away. */
	private Object @Nullable [] spotInFront(double want) {
		return this.on(s -> {
			StorySession session = this.session(s);

			for (double distance : new double[] {want, want - 1.5, want - 3.0}) {
				for (Figure figure : session.figures) {
					if (!figure.observer || !Story.DESTROYER.equals(figure.ship) || !Crew.TROOPER.equals(figure.kind()) || figure.mode != Figure.ROUTINE
							|| Math.abs(figure.y - H) > 1.0) {
						continue;
					}

					Vec3 at = figure.position().add(figure.look().scale(distance));

					if (this.free(s, at) && this.clear(s, figure.eye(), at.add(0.0, 1.5, 0.0))) {
						return new Object[] {figure.key, at, figure.eye()};
					}
				}
			}

			return null;
		});
	}

	private void place(Vec3 local, float yaw, float pitch) {
		BlockPos origin = this.on(s -> this.session(s).origin(Story.DESTROYER));
		this.server.runCommand(String.format(Locale.ROOT, "execute in sabercraft:ship_interiors run tp @a %.3f %.3f %.3f %.1f %.1f", origin.getX() + local.x,
				origin.getY() + local.y, origin.getZ() + local.z, yaw, pitch));
		this.context.waitTicks(8);
	}

	private boolean free(MinecraftServer s, Vec3 at) {
		ServerLevel level = s.getLevel(ShipInteriors.DIMENSION);
		Vec3 world = at.add(Vec3.atLowerCornerOf(this.session(s).origin(Story.DESTROYER)));
		AABB body = new AABB(world.x - 0.3, world.y + 0.05, world.z - 0.3, world.x + 0.3, world.y + 1.8, world.z + 0.3);
		BlockPos below = BlockPos.containing(world.x, world.y - 0.05, world.z);
		return level.noCollision(null, body) && !level.getBlockState(below).getCollisionShape(level, below).isEmpty();
	}

	private boolean clear(MinecraftServer s, Vec3 from, Vec3 to) {
		ServerLevel level = s.getLevel(ShipInteriors.DIMENSION);
		Vec3 corner = Vec3.atLowerCornerOf(this.session(s).origin(Story.DESTROYER));
		HitResult hit = level.clip(new ClipContext(from.add(corner), to.add(corner), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
		return hit.getType() == HitResult.Type.MISS;
	}

	private static float aimYaw(Vec3 eye, Vec3 target) {
		return (float) Math.toDegrees(Math.atan2(-(target.x - eye.x), target.z - eye.z));
	}

	private static String fmt(double value) {
		return String.format(Locale.ROOT, "%.2f", value);
	}

	private void check(boolean ok, String problem) {
		if (!ok) {
			this.problem(problem);
		}
	}

	private void problem(String problem) {
		SaberCraft.LOGGER.error("PHASE21 CHECK FAILED: {}", problem);
		this.problems.add(problem);
	}

	private <T> T on(Function<MinecraftServer, T> what) {
		return this.server.computeOnServer(what::apply);
	}

	private @Nullable ServerPlayer me(MinecraftServer server) {
		return server.getPlayerList().getPlayer(this.self);
	}

	private @Nullable StorySession session(MinecraftServer server) {
		ServerPlayer player = this.me(server);
		return player == null ? null : Story.sessionForTest(player).orElse(null);
	}
}
