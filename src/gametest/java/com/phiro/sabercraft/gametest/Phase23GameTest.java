package com.phiro.sabercraft.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.character.Outfit;
import com.phiro.sabercraft.character.OutfitSync;
import com.phiro.sabercraft.client.character.OutfitClient;
import com.phiro.sabercraft.client.combat.CombatLibrary;
import com.phiro.sabercraft.client.combat.CombatStyle;
import com.phiro.sabercraft.client.combat.ThirdPersonPose;
import com.phiro.sabercraft.client.interior.WalkAudit;
import com.phiro.sabercraft.client.lang.SaberLanguage;
import com.phiro.sabercraft.client.story.StoryClient;
import com.phiro.sabercraft.droid.DroidEntity;
import com.phiro.sabercraft.droid.DroidType;
import com.phiro.sabercraft.droid.Droids;
import com.phiro.sabercraft.interior.DestroyerInterior;
import com.phiro.sabercraft.interior.InteriorPlan;
import com.phiro.sabercraft.interior.ShipInteriors;
import com.phiro.sabercraft.network.StoryActionPayload;
import com.phiro.sabercraft.story.Figure;
import com.phiro.sabercraft.story.Story;
import com.phiro.sabercraft.story.StorySession;
import com.phiro.sabercraft.story.StoryTesting;

/**
 * Phase 23: the story's look and its enemies, the reactor, the lift and the detention door, droids
 * in crawl ways. (The ISB officer's scene, the hit reactions and the duel with both phases, the
 * clashes, the blade lock and the disarming are played through in {@link StoryGameTest}.)
 *
 * <ul>
 * <li>Skins: in the story one's plain skin until the disguise, whatever one chose; the disguise is
 * the troopers' own armour; out of the story the choice is back; what the others are told matches.</li>
 * <li>The saber at rest: no style holds it over the head.</li>
 * <li>An officer who sees the player draws, calls for help and fights; when a fight begins, every
 * enemy still in an old chase or search fights, and the chaser from the reactor does not vanish.</li>
 * <li>Ways: through closed doors, not through locked ones, over several rooms and down steps; in a
 * fight troopers come round through a door (not into the wall) and do not crowd into one spot.</li>
 * <li>The reactor: every platform and bridge can be walked, nobody of the story stands in the air,
 * nothing collides where nothing is drawn; a fall is caught unhurt; the bridge creaks, breaks behind
 * the player, takes a trooper on it into the depth, and the pursuers stay on the other side.</li>
 * <li>The lift stands back in its own bay, the detention door has its own wall.</li>
 * <li>Droids never land in a crawl way or before its mouth, and wait outside while one crawls.</li>
 * </ul>
 */
public class Phase23GameTest implements FabricClientGameTest {
	private static final int H = DestroyerInterior.HANGAR + 1;
	private static final int R = DestroyerInterior.REACTOR_BOTTOM;
	/** How often a walk line was met (its "xN"). */
	private static final Pattern TIMES = Pattern.compile("\\) x(\\d+)");
	private static final Pattern CELL = Pattern.compile("^(?:in a wall|at the eyes|invisible) (-?\\d+),(-?\\d+),(-?\\d+) .*");

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
			this.problems.forEach(problem -> SaberCraft.LOGGER.error("PHASE23 PROBLEM: {}", problem));
			throw new AssertionError("Phase 23: " + this.problems.size() + " problem(s): " + String.join(" | ", this.problems));
		}
	}

	private void run(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		try {
			context.waitTicks(100);
			this.server = singleplayer.getServer();
			this.server.runCommand("gamerule advance_time false");
			this.server.runCommand("gamerule spawn_mobs false");
			this.server.runCommand("time set day");
			this.server.runCommand("gamemode creative @a");
			context.waitTicks(20);
			this.self = context.computeOnClient(client -> client.player.getUUID());

			this.idle();
			BlockPos destroyer = this.on(s -> ShipInteriors.plotForTest(s, this.self, "star_destroyer"));
			this.walkAudit(destroyer);
			this.layout(destroyer);
			this.reactorPictures(destroyer);
			this.server.runCommand("gamemode survival @a");
			this.pitCatch(destroyer);
			this.droids(destroyer);
			context.runOnClient(client -> SaberLanguage.set(SaberLanguage.GERMAN));
			this.skins();
			this.officer();
			this.engage();
			this.ways();
			this.collapse();
		} finally {
			context.runOnClient(client -> {
				SaberLanguage.set(SaberLanguage.AUTO);
				OutfitClient.choose("");
			});
		}
	}

	// --- 2: the saber at rest ------------------------------------------------------------------

	/** No fighting style holds the saber over the head while standing (the arms at most a little above level). */
	private void idle() {
		List<String> raised = this.context.computeOnClient(client -> {
			List<String> list = new ArrayList<>();

			for (Map.Entry<Identifier, CombatStyle> entry : CombatLibrary.INSTANCE.styles().entrySet()) {
				ThirdPersonPose idle = entry.getValue().idleThirdPerson();

				if (idle.mainArm[0] < -100.0F || idle.offArm[0] < -100.0F) {
					list.add(entry.getKey() + " (arms " + idle.mainArm[0] + " / " + idle.offArm[0] + ")");
				}
			}

			return list;
		});
		int styles = this.context.computeOnClient(client -> CombatLibrary.INSTANCE.styles().size());
		SaberCraft.LOGGER.info("IDLE: {} styles, raised: {}", styles, raised);
		check(styles >= 10, "the fighting styles are not loaded (" + styles + ")");
		check(raised.isEmpty(), "at rest the saber is held over the head: " + raised);
	}

	// --- 8, 9, 13: walking the reactor, the lobby, the lift and the detention door ------------------

	/**
	 * The player's walk over the reactor's platforms and bridges and through the lobby: nothing
	 * collides there that is not drawn, and no drawn wall lets one through.
	 */
	private void walkAudit(BlockPos destroyer) {
		WalkAudit.Result walk = Phase19GameTest.walkReport(this.context, this.server, this.self, destroyer, "star_destroyer",
				List.of(new Vec3(30.5, R, 24.5), new Vec3(44.5, R, 35.0), new Vec3(46.5, H, 15.5), new Vec3(40.5, H, 2.5)));
		int reactor = 0;

		for (String line : walk.lines()) {
			Matcher m = CELL.matcher(line);

			if (!m.matches()) {
				continue;
			}

			int x = Integer.parseInt(m.group(1));
			int y = Integer.parseInt(m.group(2));
			int z = Integer.parseInt(m.group(3));
			boolean inShaft = Math.hypot(x + 0.5 - DestroyerInterior.REACTOR_X, z + 0.5 - DestroyerInterior.REACTOR_Z) < DestroyerInterior.REACTOR_R
					&& y >= DestroyerInterior.PIT_FLOOR && y <= H + 3;
			boolean lobby = x >= DestroyerInterior.CX - 5 && x <= DestroyerInterior.CX + 5 && z >= DestroyerInterior.LOBBY_Z0
					&& z <= DestroyerInterior.LIFT_Z1 + 1 && y >= H && y <= H + 3;

			Matcher times = TIMES.matcher(line);
			int seen = times.find() ? Integer.parseInt(times.group(1)) : 1;

			// (In the lobby the door frames stand a hand's breadth out of the walls as everywhere in
			// the ship: brushing them is not counted there, only what is at the eyes or invisible.)
			boolean body = line.startsWith("in a wall");

			if ((inShaft || lobby && !body) && seen >= 3) {
				SaberCraft.LOGGER.info("P23WALK {}", line);

				if (reactor++ < 12) {
					this.problem((inShaft ? "the reactor: " : "the lobby: ") + line);
				}
			}
		}

		SaberCraft.LOGGER.info("P23WALK {} problems in the reactor and the lobby, {} cells walked", reactor, walk.nodes());
	}

	/** The lift in its own bay behind the lobby, the detention door in its own wall, apart from it (pictures). */
	private void layout(BlockPos destroyer) {
		InteriorPlan.Lift lift = InteriorPlan.of("star_destroyer").lifts().get(0);
		AABB area = lift.area();
		check(area.minZ >= DestroyerInterior.LOBBY_Z1 + 1, "the lift does not stand back behind the lobby (from z " + area.minZ + ")");
		// The detention door: in the lobby's west wall, its far edge at least a block before the lift's bay.
		check(DestroyerInterior.DETENTION_DOOR_Z0 + 2 <= area.minZ - 1, "the detention door is right beside the lift (door to z "
				+ (DestroyerInterior.DETENTION_DOOR_Z0 + 1) + ", lift from " + area.minZ + ")");
		check(area.minX > DestroyerInterior.CX - 4 + 1, "the lift reaches to the detention door's wall");
		// The lift lets one out into the lobby facing the corridor (not back into its bay).
		InteriorPlan.Lift down = InteriorPlan.of("star_destroyer").lifts().get(1);
		check(down.arrival().z < area.minZ - 1.0 && !area.contains(down.arrival()), "the lift from the bridge lets one out in its own bay (" + down.arrival() + ")");
		Phase16GameTest.look(this.context, this.server, destroyer, new Vec3(40.5, H, -1.5), 0.0F, 8.0F, "phase23_lobby_lift_bay");
		Phase16GameTest.look(this.context, this.server, destroyer, new Vec3(41.5, H, 1.5), 45.0F, 5.0F, "phase23_lobby_detention_door");
		Phase16GameTest.look(this.context, this.server, destroyer, new Vec3(40.5, H, DestroyerInterior.LIFT_Z0 - 0.5), 180.0F, 5.0F, "phase23_lift_looking_out");
	}

	/** The new reactor, from its platforms, bridges and the gallery (pictures). */
	private void reactorPictures(BlockPos destroyer) {
		Object[][] views = {{new Vec3(30.5, R, 24.5), -60.0F, 15.0F, "from_lift_platform"}, {new Vec3(33.5, R, 31.0), -10.0F, 35.0F, "down_from_bridge"},
				{new Vec3(44.5, R, 35.0), 150.0F, -5.0F, "from_lab_platform"}, {new Vec3(50.5, R, 24.5), 90.0F, -10.0F, "from_east_platform"},
				{new Vec3(46.5, H, 15.5), 135.0F, 40.0F, "from_gallery_down"}, {new Vec3(33.5, H, 19.0), -30.0F, 25.0F, "deck_bridge"},
				{new Vec3(40.5, R, 12.5), 0.0F, -35.0F, "core_up"}};

		for (Object[] view : views) {
			Phase16GameTest.look(this.context, this.server, destroyer, (Vec3) view[0], (float) view[1], (float) view[2], "phase23_reactor_" + view[3]);
		}
	}

	/** Walking off a platform into the shaft: caught and set back on the platform, unhurt, in a moment. */
	private void pitCatch(BlockPos destroyer) {
		Phase16GameTest.tp(this.context, this.server, destroyer, new Vec3(33.0, R, 24.5), -90.0F, 0.0F);
		this.context.waitTicks(20);
		float health = this.on(s -> this.me(s).getHealth());
		this.context.getInput().holdKeyFor(options -> options.keyUp, 14);
		boolean fell = false;
		boolean back = false;

		for (int i = 0; i < 80 && !back; i++) {
			this.context.waitTick();
			Vec3 at = this.on(s -> this.me(s).position().subtract(Vec3.atLowerCornerOf(destroyer)));
			fell |= at.y < R - 0.5;
			back = fell && Math.abs(at.y - R) < 0.3 && DestroyerInterior.GANTRIES.stream().anyMatch(g -> g.on(at.x, at.y, at.z));
		}

		this.context.waitTicks(10);
		Vec3 at = this.on(s -> this.me(s).position().subtract(Vec3.atLowerCornerOf(destroyer)));
		check(fell, "walking off the lift's platform the player does not fall (at " + at + ")");
		check(back, "a fall into the shaft is not caught and set back onto a platform (at " + at + ")");
		check(this.on(s -> this.me(s).getHealth()) >= health, "the fall into the shaft hurts (" + health + " -> " + this.on(s -> this.me(s).getHealth()) + ")");
		check(this.on(s -> this.me(s).isAlive()), "the fall into the shaft kills");
	}

	// --- 11, 17: droids and crawl ways -------------------------------------------------------------

	/**
	 * A droid while the player crawls through the vent from the hangar corridor: it does not jump
	 * into the vent or before its mouth; out of the vent, it comes, and lands behind or beside the player.
	 */
	private void droids(BlockPos destroyer) {
		Vec3 corner = Vec3.atLowerCornerOf(destroyer);
		AABB vent = DestroyerInterior.VENTS.get(0).inflate(0.2);
		// The cells right before the vent's mouth in the corridor (its hatch faces east).
		AABB mouth = new AABB(DestroyerInterior.CX - 3, H, -11.5, DestroyerInterior.CX - 1, H + 2, -7.5);
		Phase16GameTest.tp(this.context, this.server, destroyer, new Vec3(30.5, H, -9.5), -90.0F, 0.0F);
		this.context.waitTicks(10);
		check(this.on(s -> DroidEntity.narrow(this.me(s))), "in the vent the player does not crawl (pose " + this.on(s -> this.me(s).getPose()) + ")");
		this.server.runOnServer(s -> {
			Droids.replaceCrew(this.me(s), List.of(DroidType.R2D2));
			Droids.place(this.me(s), DroidType.R2D2, corner.add(40.5, H, 4.5), 0.0F, false);
		});
		boolean inVent = false;
		boolean atMouth = false;

		for (int i = 0; i < 6; i++) {
			this.context.waitTicks(10);
			Vec3 droid = this.droidAt(corner);
			inVent |= droid != null && vent.contains(droid);
			atMouth |= droid != null && mouth.contains(droid);
		}

		check(this.on(s -> Droids.droid(this.me(s), DroidType.R2D2) != null), "no droid came");
		check(!inVent, "while the player crawls, the droid jumps into the vent");
		check(!this.on(s -> {
			DroidEntity droid = Droids.droid(this.me(s), DroidType.R2D2);
			return droid != null && droid.teleportToOwner();
		}), "while the player crawls, the droid can be put beside them in the vent");

		// Right before the way out.
		Phase16GameTest.tp(this.context, this.server, destroyer, new Vec3(35.5, H, -9.5), -90.0F, 0.0F);

		for (int i = 0; i < 6; i++) {
			this.context.waitTicks(10);
			Vec3 droid = this.droidAt(corner);
			inVent |= droid != null && vent.contains(droid);
			atMouth |= droid != null && mouth.contains(droid);
		}

		check(!inVent, "near the vent's way out the droid jumps into the vent");
		check(!atMouth, "near the vent's way out the droid stands before its mouth (in the way and the view)");

		// Out: it comes after (walking or jumping), never before the player or the vent's mouth.
		Phase16GameTest.tp(this.context, this.server, destroyer, new Vec3(DestroyerInterior.CX + 0.5, H, -9.5), -90.0F, 0.0F);
		this.context.waitTicks(10);
		check(!this.on(s -> DroidEntity.narrow(this.me(s))), "out of the vent the player still crawls");
		List<String> wrong = this.on(s -> {
			List<String> list = new ArrayList<>();
			DroidEntity droid = Droids.droid(this.me(s), DroidType.R2D2);
			ServerPlayer me = this.me(s);

			if (droid == null) {
				return List.of("no droid");
			}

			Vec3 look = Vec3.directionFromRotation(0.0F, me.getYRot());

			for (int i = 0; i < 12; i++) {
				if (!droid.teleportToOwner()) {
					list.add("no place found");
					continue;
				}

				Vec3 to = droid.position().subtract(me.position());
				Vec3 local = droid.position().subtract(corner);
				double front = new Vec3(to.x, 0.0, to.z).normalize().dot(look);

				if (front > 0.55) {
					list.add(String.format(Locale.ROOT, "in front (%.2f) at %.1f,%.1f,%.1f", front, local.x, local.y, local.z));
				} else if (vent.contains(local) || mouth.contains(local)) {
					list.add(String.format(Locale.ROOT, "in the vent or at its mouth at %.1f,%.1f,%.1f", local.x, local.y, local.z));
				} else if (me.level().clip(new net.minecraft.world.level.ClipContext(me.position().add(0.0, 0.6, 0.0), droid.position().add(0.0, 0.6, 0.0),
						net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, me)).getType()
						!= net.minecraft.world.phys.HitResult.Type.MISS) {
					// (Behind a wall: in the armory beside the corridor, locked, it could never come out.)
					list.add(String.format(Locale.ROOT, "behind a wall at %.1f,%.1f,%.1f", local.x, local.y, local.z));
				}
			}

			return list;
		});
		check(wrong.isEmpty(), "a droid jumping to the player lands badly: " + wrong);
		this.context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
		this.context.waitTicks(20);
		this.context.takeScreenshot("phase23_droid_out_of_vent");
		this.context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
		this.server.runOnServer(s -> Droids.replaceCrew(this.me(s), List.of()));
		this.context.waitTicks(10);
	}

	private @Nullable Vec3 droidAt(Vec3 corner) {
		return this.on(s -> {
			DroidEntity droid = Droids.droid(this.me(s), DroidType.R2D2);
			return droid == null ? null : droid.position().subtract(corner);
		});
	}

	// --- 1-4: skins in the story ---------------------------------------------------------------------

	/**
	 * Tests 1 to 4: the story without and with a chosen SaberCraft skin (the plain Minecraft skin
	 * either way), the stolen armour (the troopers' own, to everyone), leaving (the choice is back).
	 */
	private void skins() {
		// 1: nothing chosen.
		this.context.runOnClient(client -> OutfitClient.choose(""));
		this.context.waitTicks(5);
		this.start(0);
		this.plainInStory("no skin chosen");
		this.leave();
		check(this.on(s -> OutfitSync.overrideOf(this.self).isEmpty()), "after the story (nothing chosen) the story's skin stays");
		check(this.context.computeOnClient(client -> OutfitClient.look(this.self) == null), "after the story (nothing chosen) the player is not plain");

		// 2: Vader chosen before.
		this.context.runOnClient(client -> OutfitClient.choose(Outfit.VADER.id().toString()));
		this.context.waitTicks(10);
		check(this.on(s -> OutfitSync.outfitOf(this.self)).map(Outfit.VADER.id()::equals).orElse(false), "the server does not know Vader is chosen");
		Identifier vaderTexture = this.context.computeOnClient(client -> OutfitClient.look(this.self) == null ? null : OutfitClient.look(this.self).texture());
		this.start(2);
		this.plainInStory("Vader chosen");
		check(this.on(s -> OutfitSync.outfitOf(this.self)).map(Outfit.VADER.id()::equals).orElse(false), "the story forgets the chosen skin (it must come back after)");

		// 3: the armour from the locker: the troopers' own armour, for everyone.
		this.server.runOnServer(s -> StoryTesting.putOnArmour(this.session(s)));
		this.context.waitTicks(10);
		check(this.on(s -> OutfitSync.overrideOf(this.self)).map(Outfit.STORMTROOPER.id()::equals).orElse(false), "the armour does not dress the player as a trooper");
		check(Outfit.STORMTROOPER.id().equals(this.context.computeOnClient(client -> OutfitClient.otherOutfit(this.self))),
				"the others are not told the player wears the armour");
		Identifier armour = this.context.computeOnClient(client -> OutfitClient.look(this.self) == null ? null : OutfitClient.look(this.self).texture());
		check(armour != null && armour.getPath().equals("textures/outfit/stormtrooper.png"), "the player does not see themself in the armour (" + armour + ")");
		int[] size = this.context.computeOnClient(client -> {
			try (var stream = client.getResourceManager().open(SaberCraft.id("textures/outfit/stormtrooper.png"));
					var image = com.mojang.blaze3d.platform.NativeImage.read(stream)) {
				return new int[] {image.getWidth(), image.getHeight()};
			} catch (java.io.IOException error) {
				return new int[] {0, 0};
			}
		});
		// The troopers' own tiles at their full detail (8 times a skin): not the old, plainer armour.
		check(size[0] == 512 && size[1] == 512, "the disguise is not the troopers' armour (" + size[0] + " x " + size[1] + ")");
		this.context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
		this.context.waitTicks(20);
		this.context.takeScreenshot("phase23_disguise_front");
		this.context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
		this.context.waitTicks(10);
		this.context.takeScreenshot("phase23_disguise_back");
		this.context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));

		// 4: out of the story: Vader again, for the player and the others.
		this.leave();
		check(this.on(s -> OutfitSync.overrideOf(this.self).isEmpty()), "after the story the armour stays on");
		check(Outfit.VADER.id().equals(this.context.computeOnClient(client -> OutfitClient.otherOutfit(this.self))), "after the story the others do not see Vader again");
		Identifier after = this.context.computeOnClient(client -> OutfitClient.look(this.self) == null ? null : OutfitClient.look(this.self).texture());
		check(vaderTexture != null && vaderTexture.equals(after), "after the story the chosen skin is not back (" + after + ", chosen " + vaderTexture + ")");
		this.context.runOnClient(client -> OutfitClient.choose(""));
		this.context.waitTicks(5);
	}

	private void plainInStory(String what) {
		check(this.on(s -> OutfitSync.overrideOf(this.self)).map(Outfit.PLAIN::equals).orElse(false), "in the story (" + what + ") the player is not plain: "
				+ this.on(s -> OutfitSync.overrideOf(this.self)));
		check(this.context.computeOnClient(client -> OutfitClient.look(this.self) == null), "in the story (" + what + ") the player sees a skin of SaberCraft on themself");
		check(Outfit.PLAIN.equals(this.context.computeOnClient(client -> OutfitClient.otherOutfit(this.self))),
				"in the story (" + what + ") the others are not told the player is plain");
	}

	// --- 4 (point), 6 (point): the officer ---------------------------------------------------------------

	/**
	 * Test 7: the quartermaster (an officer) sees the player in the armory: he shouts, calls for help
	 * over his comlink (troopers come), draws his pistol and shoots, and keeps moving.
	 */
	private void officer() {
		this.start(2);
		this.server.runOnServer(s -> StoryTesting.unseen(this.session(s), 1_000_000));
		Vec3 qm = this.on(s -> this.session(s).figure("quartermaster") == null ? null : this.session(s).figure("quartermaster").position());

		if (qm == null) {
			this.problem("no quartermaster in Mission 3");
			return;
		}

		int calls = this.on(s -> StoryTesting.officerCalls());
		int shots = this.on(s -> StoryTesting.stunShots());
		this.put(new Vec3(53.0, H, -11.5), 90.0F, 0.0F);
		this.server.runOnServer(s -> {
			StoryTesting.detect(this.session(s), this.session(s).figure("quartermaster"));
			// (Out of sight only until he has seen the player: in the grace nobody shoots.)
			StoryTesting.unseen(this.session(s), 0);
		});
		this.context.waitTicks(2);
		int mode = this.on(s -> this.session(s).figure("quartermaster").mode);
		boolean comlink = this.on(s -> StoryTesting.react(this.session(s).figure("quartermaster")) == Figure.COMLINK);
		Vec3 from = this.on(s -> this.session(s).figure("quartermaster").position());
		check(mode == Figure.CHASE, "the officer does not come for the player (mode " + mode + ")");
		check(comlink, "the officer does not call over his comlink");
		boolean called = false;
		boolean shot = false;
		boolean help = false;
		double moved = 0.0;

		for (int i = 0; i < 200 && !(called && shot && help && moved > 1.0); i++) {
			this.context.waitTick();
			called |= this.on(s -> StoryTesting.officerCalls()) > calls;
			shot |= this.on(s -> StoryTesting.stunShots()) > shots;
			help |= this.on(s -> this.session(s).figures.stream().anyMatch(f -> f.key.startsWith(StoryTesting.reinforcementPrefix())));
			Vec3 now = this.on(s -> this.session(s).figure("quartermaster") == null ? from : this.session(s).figure("quartermaster").position());
			moved = Math.max(moved, now.distanceTo(from));

			if (i == 30) {
				this.context.takeScreenshot("phase23_officer_calls");
			}
		}

		check(called, "the officer's call over the comlink does not go through");
		check(help, "no troopers come at the officer's call");
		check(shot, "the officer does not shoot (he only calls for help)");
		check(moved > 1.0, "the officer stands where he was (moved " + fmt(moved) + " blocks)");
		this.context.takeScreenshot("phase23_officer_fights");

		// Hurt from somewhere: the HUD shows where it came from.
		this.server.runOnServer(s -> {
			ServerPlayer me = this.me(s);
			ServerLevel level = (ServerLevel) me.level();
			var type = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE)
					.getOrThrow(net.minecraft.world.damagesource.DamageTypes.ARROW);
			me.hurtServer(level, new net.minecraft.world.damagesource.DamageSource(type, me.position().add(4.0, 1.0, 0.0)), 1.0F);
		});
		boolean shown = false;

		for (int i = 0; i < 10 && !shown; i++) {
			this.context.waitTick();
			shown = this.context.computeOnClient(client -> StoryClient.hurtFromForTest());
		}

		check(shown, "a hit on the player does not show where it came from");
		this.leave();
	}

	// --- 3 (point): everyone fights -------------------------------------------------------------------

	/**
	 * Tests 5 and 6: seen in the reactor, into the lab while they hunt, the saber taken: everyone
	 * who hunted (the lab's guard, the troopers who came at his call) fights at once, nobody stays
	 * behind in a chase; the guard did not vanish when the player went into the lab; they shoot.
	 */
	private void engage() {
		this.startAt(5, "m6.door");

		if (this.on(s -> this.session(s) == null || this.session(s).figure("lab_guard") == null)) {
			this.problem("Mission 6 at the lab door: no guard there");
			return;
		}

		this.server.runCommand("effect give @a minecraft:resistance 100000 4 true");
		this.server.runOnServer(s -> StoryTesting.unseen(this.session(s), 1_000_000));
		this.server.runOnServer(s -> this.session(s).unset("lab.open"));
		this.put(new Vec3(44.5, R, 48.5), 180.0F, 0.0F);
		this.server.runOnServer(s -> StoryTesting.detect(this.session(s), this.session(s).figure("lab_guard")));
		boolean help = false;

		for (int i = 0; i < 200 && !help; i++) {
			this.context.waitTick();
			help = this.on(s -> this.session(s).figures.stream().anyMatch(f -> f.key.startsWith(StoryTesting.reinforcementPrefix())));
		}

		check(help, "no troopers come at the guard's call");
		this.context.waitTicks(20);
		this.logFigures("before the saber");
		this.server.runOnServer(s -> StoryTesting.finishStep(this.session(s)));
		this.waitStep("m6.lab", 40);
		this.server.runOnServer(s -> StoryTesting.finishStep(this.session(s)));
		this.waitStep("m6.saber", 40);
		check(this.on(s -> this.session(s).figure("lab_guard") != null), "the guard who hunted the player vanishes when they go into the lab");
		List<String> hunting = this.on(s -> this.session(s).figures.stream().filter(f -> !f.hostile && f.observer && f.mode != Figure.DOWN && StoryTesting.combatant(f)
				&& (f.mode == Figure.CHASE || f.mode == Figure.INVESTIGATE || f.mode == Figure.CALL || f.key.startsWith(StoryTesting.reinforcementPrefix())))
				.map(f -> f.key).toList());
		SaberCraft.LOGGER.info("ENGAGE: hunting before the saber: {}", hunting);
		check(!hunting.isEmpty(), "nobody hunts the player before the saber is taken");
		this.server.runOnServer(s -> {
			this.session(s).set("lab.open");
			StoryTesting.takeSaber(this.session(s));
		});
		this.context.waitTicks(3);
		this.logFigures("after the saber");

		for (String key : hunting) {
			String state = this.on(s -> {
				Figure f = this.session(s).figure(key);
				return f == null ? "gone" : f.hostile && f.mode == Figure.FIGHT && StoryTesting.engaged(f) ? "" : "mode " + f.mode + " hostile " + f.hostile;
			});
			check(state.isEmpty(), key + " does not fight when the saber is taken (" + state + ")");
		}

		List<String> behind = this.on(s -> this.session(s).figures.stream()
				.filter(f -> f.observer && !f.hostile && f.mode != Figure.DOWN && StoryTesting.combatant(f) && (f.mode == Figure.CHASE || f.mode == Figure.INVESTIGATE || f.mode == Figure.CALL))
				.map(f -> f.key + " mode " + f.mode).toList());
		check(behind.isEmpty(), "still in an old chase or search in the fight: " + behind);
		int shots = this.on(s -> StoryTesting.fightShots());
		boolean shot = false;
		boolean inLab = false;

		for (int i = 0; i < 300 && !(shot && inLab); i++) {
			this.context.waitTick();
			shot |= this.on(s -> StoryTesting.fightShots()) > shots;
			inLab |= this.on(s -> this.session(s).figures.stream().anyMatch(f -> f.hostile && !f.saber && DestroyerInterior.LAB.contains(f.position().add(0.0, 0.5, 0.0))));
		}

		check(shot, "after the saber is taken nobody shoots");
		check(inLab, "after the saber is taken nobody comes through the lab's door");
		this.context.takeScreenshot("phase23_fight_in_lab");
		this.leave();
	}

	// --- 5 (point): ways ---------------------------------------------------------------------------------

	/**
	 * Tests 8 and 9: the ways the crew finds (through a closed door, not through a locked one, over
	 * several rooms and down the bridge's steps); in a fight, troopers in the corridor come round
	 * through the armory's door to the player in the armory, and do not crowd into one spot.
	 */
	private void ways() {
		this.startAt(5, "m6.escape2");

		if (this.on(s -> this.session(s) == null)) {
			this.problem("Mission 6 does not continue at the escape's second part");
			return;
		}

		this.server.runCommand("effect give @a minecraft:resistance 100000 4 true");
		Vec3 corridor = new Vec3(40.5, H, -9.0);
		Vec3 armory = new Vec3(52.5, H, -8.5);
		this.server.runOnServer(s -> this.session(s).set("armory.open"));
		List<Vec3> open = this.on(s -> StoryTesting.way(this.session(s), corridor, armory));
		check(reaches(open), "no way from the corridor through the armory's (closed) door: " + open);
		check(open.stream().anyMatch(p -> p.x > 43.4 && p.x < 45.0 && p.z > -14.6 && p.z < -11.4), "the way into the armory does not lead through its door: " + open);
		this.server.runOnServer(s -> this.session(s).unset("armory.open"));
		List<Vec3> locked = this.on(s -> StoryTesting.way(this.session(s), corridor, armory));
		check(!reaches(locked), "a way leads through the locked armory door: " + locked);
		this.server.runOnServer(s -> this.session(s).set("armory.open"));
		List<Vec3> rooms = this.on(s -> StoryTesting.way(this.session(s), new Vec3(40.5, H, -30.5), new Vec3(40.5, H, 3.5)));
		check(reaches(rooms), "no way from the hangar to the lobby (through the hangar's door and the corridor): " + rooms.size() + " points");
		List<Vec3> down = this.on(s -> StoryTesting.way(this.session(s), new Vec3(40.5, DestroyerInterior.DECK + 1, 79.5),
				new Vec3(27.5, DestroyerInterior.PIT + 1, 77.5)));
		check(reaches(down), "no way from the bridge's walkway down the steps into the crew pit: " + down);

		// A fight: the player in the armory, the troopers in the corridor and the lobby. (Phase 25:
		// at this step they hold their posts until they notice the player; here they have: off
		// their posts, in the fight.)
		this.server.runOnServer(s -> {
			for (Figure f : this.session(s).figures) {
				if (f.hostile) {
					f.post = false;
					f.x = 40.5 + (f.id % 3 - 1) * 1.2;
					f.z = -6.0 + f.id % 2;
					f.y = H;
				}
			}
		});
		this.put(armory, 90.0F, 0.0F);
		boolean came = false;
		List<String> trail = new ArrayList<>();

		for (int i = 0; i < 400 && !came; i++) {
			this.context.waitTick();
			came = this.on(s -> this.session(s).figures.stream().anyMatch(f -> f.hostile && f.mode != Figure.DOWN && DestroyerInterior.ARMORY.contains(f.position().add(0.0, 0.5, 0.0))));

			if (i % 40 == 0) {
				trail.add(this.on(s -> this.session(s).figures.stream().filter(f -> f.hostile).map(f -> f.key + "@" + fmt(f.x) + "," + fmt(f.z)).toList().toString()));
			}
		}

		SaberCraft.LOGGER.info("WAYS: the fight to the armory: {}", trail);
		check(came, "in a fight the troopers do not come round through the armory's door: " + trail);
		this.context.waitTicks(60);
		this.context.takeScreenshot("phase23_troopers_through_door");
		List<Vec3> where = this.on(s -> this.session(s).figures.stream().filter(f -> f.hostile && f.mode != Figure.DOWN).map(Figure::position).toList());
		double closest = Double.MAX_VALUE;

		for (int a = 0; a < where.size(); a++) {
			for (int b = a + 1; b < where.size(); b++) {
				closest = Math.min(closest, where.get(a).distanceTo(where.get(b)));
			}
		}

		check(where.size() < 2 || closest > 0.7, "the troopers crowd into one spot (" + fmt(closest) + " blocks apart)");
		this.leave();
	}

	private static boolean reaches(List<Vec3> way) {
		return !way.isEmpty() && way.get(way.size() - 1).x > 0.5;
	}

	// --- 8 (point), 12 (test): the bridge that breaks ------------------------------------------------

	/**
	 * Tests 11 and 12: the escape over the reactor: the bridge from the lab's platform to the lift's
	 * creaks under the player (a warning), breaks behind them once they are over (never under
	 * them), a trooper on it falls into the depth, the pursuers stay on their side (no way over)
	 * and shoot from its edge; at the escape's checkpoint the bridge is whole again, after it gone.
	 */
	private void collapse() {
		this.startAt(5, "m6.escape1");

		if (this.on(s -> this.session(s) == null)) {
			this.problem("Mission 6 does not continue at the escape");
			return;
		}

		this.server.runCommand("effect give @a minecraft:resistance 100000 4 true");
		this.server.runCommand("effect give @a minecraft:instant_health 1 4 true");
		check(this.on(s -> DestroyerInterior.collapseStands(s.getLevel(ShipInteriors.DIMENSION), this.session(s).destroyer)), "at the escape the bridge is not there");

		// Nobody of the story stands in the air (on the reactor's level: on a platform, in the lab or its way).
		List<String> air = this.on(s -> this.session(s).figures.stream().filter(f -> Story.DESTROYER.equals(f.ship) && f.y < H - 1 && !onSteel(f.position()))
				.map(f -> f.key + " at " + fmt(f.x) + "," + fmt(f.y) + "," + fmt(f.z)).toList());
		check(air.isEmpty(), "in the escape someone stands in the air over the shaft: " + air);
		// Only the pursuer on the lab's side and the one who will be on the bridge.
		this.server.runOnServer(s -> this.session(s).figures.removeIf(f -> f.hostile && !f.key.equals("h1") && !f.key.equals("lab_guard")));
		Vec3 corner = this.on(s -> Vec3.atLowerCornerOf(this.session(s).destroyer));
		check(reaches(this.on(s -> StoryTesting.way(this.session(s), new Vec3(44.5, R, 35.0), new Vec3(30.5, R, 24.5)))),
				"no way over the bridge from the lab's platform to the lift's before it breaks");

		// On the bridge: it creaks (a warning, the camera trembles), it holds.
		this.put(new Vec3(33.5, R, 33.5), 180.0F, 10.0F);
		boolean creaked = false;

		for (int i = 0; i < 20 && !creaked; i++) {
			this.context.waitTick();
			creaked = this.on(s -> this.session(s).has("tmp.bridge_creak"));
		}

		check(creaked, "stepping on the bridge in the escape, it does not creak");
		check(this.context.computeOnClient(client -> StoryClient.creak()) > 0.0F, "the client does not show the bridge creaking");
		this.context.takeScreenshot("phase23_bridge_creak");
		this.context.waitTicks(20);
		check(!this.on(s -> this.session(s).has("tmp.bridge_down")), "the bridge breaks with the player on it");

		// Over on the lift's platform, a trooper on the bridge behind: it breaks.
		this.server.runOnServer(s -> {
			Figure guard = this.session(s).figure("lab_guard");

			if (guard != null) {
				guard.x = 33.5;
				guard.y = R;
				guard.z = 31.0;
			}

			Figure h1 = this.session(s).figure("h1");

			if (h1 != null) {
				h1.x = 42.5;
				h1.y = R;
				h1.z = 34.5;
			}

			ServerPlayer me = this.me(s);
			me.teleportTo(s.getLevel(ShipInteriors.DIMENSION), corner.x + 30.5, corner.y + R, corner.z + 26.5, java.util.Set.of(), -30.0F, 15.0F, false);
		});
		boolean down = false;

		for (int i = 0; i < 20 && !down; i++) {
			this.context.waitTick();
			down = this.on(s -> this.session(s).has("tmp.bridge_down"));
		}

		check(down, "the bridge does not break behind the player");
		check(!this.on(s -> DestroyerInterior.collapseStands(s.getLevel(ShipInteriors.DIMENSION), this.session(s).destroyer)),
				"the broken bridge still carries (its blocks are there)");
		this.context.waitTicks(4);
		this.context.takeScreenshot("phase23_bridge_collapse_1");
		int guardMode = this.on(s -> this.session(s).figure("lab_guard") == null ? -1 : this.session(s).figure("lab_guard").mode);
		check(guardMode == Figure.DOWN || guardMode == -1, "the trooper on the bridge does not fall with it (mode " + guardMode + ")");
		this.context.waitTicks(12);
		this.context.takeScreenshot("phase23_bridge_collapse_2");
		boolean gone = false;

		for (int i = 0; i < 80 && !gone; i++) {
			this.context.waitTick();
			gone = this.on(s -> this.session(s).figure("lab_guard") == null);
		}

		check(gone, "the trooper who fell with the bridge is still there");
		check(this.on(s -> this.me(s).isAlive() && this.me(s).getY() - corner.y > R - 0.5), "the player falls with the bridge");

		// The pursuer: no way over; he stays on his side, at its edge, and does not fall.
		check(!reaches(this.on(s -> StoryTesting.way(this.session(s), new Vec3(44.5, R, 35.0), new Vec3(30.5, R, 24.5)))),
				"after the bridge broke a way still leads from the lab's platform to the lift's");
		this.context.waitTicks(100);
		Vec3 h1 = this.on(s -> this.session(s).figure("h1") == null ? null : this.session(s).figure("h1").position());

		if (h1 == null) {
			this.problem("the pursuer on the lab's side is gone");
		} else {
			check(Math.abs(h1.y - R) < 0.6 && onSteel(h1), "the pursuer walks into the depth (at " + h1 + ")");
			check(!DestroyerInterior.GANTRIES.get(0).contains(h1.x, h1.z), "the pursuer comes over to the lift's platform (at " + h1 + ")");
		}

		this.context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
		this.context.waitTicks(10);
		this.context.takeScreenshot("phase23_bridge_gone");
		this.context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
		this.leave();

		// At the escape's checkpoint it is whole again; after it, gone.
		this.startAt(5, "m6.escape1");
		check(this.on(s -> this.session(s) != null && DestroyerInterior.collapseStands(s.getLevel(ShipInteriors.DIMENSION), this.session(s).destroyer)),
				"back at the escape's checkpoint the bridge is not whole again");
		this.leave();
		this.startAt(5, "m6.escape2");
		check(this.on(s -> this.session(s) != null && !DestroyerInterior.collapseStands(s.getLevel(ShipInteriors.DIMENSION), this.session(s).destroyer)),
				"after the escape over the reactor the bridge is whole again");
		this.leave();
	}

	/** Whether that place (plot space) is on the reactor's steel, in the lab or on the way to it. */
	private static boolean onSteel(Vec3 at) {
		if (DestroyerInterior.LAB.inflate(0.0, 0.5, 0.0).contains(at.add(0.0, 0.5, 0.0))) {
			return true;
		}

		if (at.x >= DestroyerInterior.LAB_DOOR_X0 - 1 && at.x <= DestroyerInterior.LAB_DOOR_X0 + 4 && at.z >= 33.0 && at.z <= DestroyerInterior.LAB_WALL + 1.0) {
			return true;
		}

		return DestroyerInterior.GANTRIES.stream().anyMatch(g -> g.on(at.x, at.y, at.z));
	}

	// --- Helpers ---------------------------------------------------------------------------------

	/** Starts that mission anew (out of the story first). */
	private void start(int mission) {
		if (this.on(s -> this.session(s) != null)) {
			this.leave();
		}

		this.server.runOnServer(s -> StoryTesting.unlockAll(s, this.self));
		this.context.runOnClient(client -> StoryClient.send(StoryActionPayload.MISSION, "", mission));
		this.context.waitTicks(80);
		check(this.on(s -> this.session(s) != null), "Mission " + (mission + 1) + " does not start");
	}

	/** Continues the story at that step (a file saved there). */
	private void startAt(int mission, String step) {
		if (this.on(s -> this.session(s) != null)) {
			this.leave();
		}

		this.server.runOnServer(s -> StoryTesting.fileAt(s, this.self, mission, step));
		this.context.runOnClient(client -> StoryClient.send(StoryActionPayload.CONTINUE, "", 0));
		this.context.waitTicks(60);
		this.waitStep(step, 60);
	}

	private void leave() {
		this.context.runOnClient(client -> StoryClient.send(StoryActionPayload.LEAVE, "", 0));
		this.context.waitTicks(40);
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

	/** Puts the player at a place of the Star Destroyer of the story (plot space). */
	private void put(Vec3 local, float yaw, float pitch) {
		BlockPos origin = this.on(s -> this.session(s).destroyer);
		this.server.runCommand(String.format(Locale.ROOT, "execute in sabercraft:ship_interiors run tp @a %.3f %.3f %.3f %.1f %.1f", origin.getX() + local.x,
				origin.getY() + local.y, origin.getZ() + local.z, yaw, pitch));
		this.context.waitTicks(8);
	}

	private void logFigures(String when) {
		List<String> lines = this.on(s -> this.session(s).figures.stream()
				.map(f -> String.format(Locale.ROOT, "%s mode %d hostile %b engaged %b at %.1f,%.1f,%.1f", f.key, f.mode, f.hostile, StoryTesting.engaged(f), f.x, f.y, f.z))
				.toList());
		SaberCraft.LOGGER.info("FIGURES {}: {}", when, lines);
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
		SaberCraft.LOGGER.error("PHASE23 CHECK FAILED: {}", problem);
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
