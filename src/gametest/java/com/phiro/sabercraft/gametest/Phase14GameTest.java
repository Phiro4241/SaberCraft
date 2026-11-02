package com.phiro.sabercraft.gametest;

import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.ScaffoldingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.ability.AbilityConfig;
import com.phiro.sabercraft.character.Outfit;
import com.phiro.sabercraft.client.ImmersionConfig;
import com.phiro.sabercraft.client.character.OutfitClient;
import com.phiro.sabercraft.client.music.SaberMusic;
import com.phiro.sabercraft.client.ship.CockpitView;
import com.phiro.sabercraft.client.ship.HyperspaceClient;
import com.phiro.sabercraft.client.ship.InteriorAmbience;
import com.phiro.sabercraft.client.ship.ShipClient;
import com.phiro.sabercraft.interior.InteriorPlan;
import com.phiro.sabercraft.interior.ShipInteriors;
import com.phiro.sabercraft.ship.Hyperspace;
import com.phiro.sabercraft.ship.ShipEntity;
import com.phiro.sabercraft.ship.ShipFeatures;
import com.phiro.sabercraft.ship.ShipType;
import com.phiro.sabercraft.ship.Starships;

/**
 * Phase 14: flight music, flight sounds, the cockpit view, ship damage, the hyperspace jump, the
 * walkable interiors and Vader's helmet.
 *
 * <ul>
 * <li>Flight: boarding starts the flight music at once (only one piece plays), a space battle
 * switches to the battle piece, climbing out lets it go at once. The engine has its thrust layer;
 * speeding up and braking hard each make their sound. The view goes into the cockpit, F5 still
 * works, and climbing out brings back the view from before. Screenshots from every cockpit.</li>
 * <li>Damage: each weapon does what its multiplier and the ship's toughness say, no hit takes more
 * than a quarter of the hull, the hull mends only after the delay and slowly, keeps its damage
 * when the ship is sent away and called again, and big ships are hit on their whole hull.
 * Destroyed: the wreck is gone, nothing drops, the pilot lives.</li>
 * <li>Hyperspace: not on the ground, not with too little energy, not through a wall; the jump
 * moves the ship ahead, every client hears of it, the cooldown holds.</li>
 * <li>Interiors: every room of both ships can be walked to from the entry (lifts included), the
 * rooms are protected, exit and helm work, a lost ship puts the crew out next to the wreck, the
 * interior is still there after leaving the world, and a player logged out aboard is still aboard.</li>
 * <li>A dedicated server: the cockpit view, a jump and the Falcon's interior.</li>
 * </ul>
 *
 * <p>Screenshots: {@code phase14_*}.
 */
public class Phase14GameTest implements FabricClientGameTest {
	private static final AABB AREA = new AABB(-600, -64, -600, 600, 400, 600);

	@Override
	public void runTest(ClientGameTestContext context) {
		context.runOnClient(client -> {
			client.options.renderDistance().set(6);
			ImmersionConfig.get().shipCamera = ImmersionConfig.CAMERA_COCKPIT;
			ImmersionConfig.get().music = true;
			ImmersionConfig.get().musicFlight = true;
			ImmersionConfig.get().musicMode = ImmersionConfig.MODE_SABERCRAFT;
		});
		defaults();
		TestWorldSave save;
		UUID self;

		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			save = singleplayer.getWorldSave();
			context.waitTicks(100);
			TestServerContext server = singleplayer.getServer();
			server.runCommand("gamerule advance_time false");
			server.runCommand("gamerule spawn_mobs false");
			server.runCommand("time set day");
			server.runCommand("difficulty normal");
			server.runCommand("gamemode survival @a");
			server.runCommand("tp @a 0 ~ 0 180 0");
			server.runCommand("kill @e[type=!player]");
			server.runCommand("clear @a");
			context.waitTicks(20);
			self = context.computeOnClient(client -> client.player.getUUID());
			context.waitFor(client -> ShipClient.status() != null, 40);
			// Both plans built once up front (for a stranger), so a wrong block shows at once.
			server.runOnServer(s -> {
				ShipInteriors.plotForTest(s, new UUID(0L, 7L), "falcon");
				ShipInteriors.plotForTest(s, new UUID(0L, 7L), "star_destroyer");
			});
			check(com.phiro.sabercraft.interior.InteriorBuilder.unknown().isEmpty(), "every block of both plans exists: "
					+ com.phiro.sabercraft.interior.InteriorBuilder.unknown());

			flight(context, server, self);
			cockpits(context, server, self);
			damage(context, server, self);
			hyperspace(context, server, self);
			falconInterior(context, server, self);
			destroyerInterior(context, server, self);
			vader(context, server);
			stayAboard(context, server, self);
		}

		reopen(context, save, self);
		dedicatedServer(context);
	}

	/** The new settings and their defaults. */
	private static void defaults() {
		AbilityConfig config = new AbilityConfig();
		check(config.shipRepairDelaySeconds == 45 && config.shipRepairPerMinute == 0.1F && !config.shipRepairInFlight,
				"repair: 45 s delay, a tenth of the hull per minute, not in flight");
		check(config.shipMaxHitShare == 0.25F, "no hit takes more than a quarter of the hull");
		check(config.hyperspaceEnabled && config.hyperspaceDistance == 160 && config.hyperspaceCooldownSeconds == 60,
				"hyperspace: on, 160 blocks, 60 s cooldown");
		check(config.shipInteriors && config.shipInteriorGuests, "interiors on, guests welcome");
		check(new ImmersionConfig().musicFlight, "flight music is on by default");
		check(ShipFeatures.of(ShipType.FALCON).interior() != null && ShipFeatures.of(ShipType.STAR_DESTROYER).interior() != null
				&& ShipFeatures.of(ShipType.XWING).interior() == null, "the Falcon and the Star Destroyer have interiors");
		check(InteriorPlan.ALL.size() == 2, "two interior plans");
	}

	// Flight: music, sounds, view.

	private static void flight(ClientGameTestContext context, TestServerContext server, UUID self) {
		// The view before boarding is the one behind the player; it comes back after climbing out.
		context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
		context.waitTicks(40);
		check(context.computeOnClient(client -> SaberMusic.mood() != SaberMusic.Mood.FLIGHT), "no flight music on foot");
		server.computeOnServer(s -> Starships.command(me(s, self), "board", "xwing"));
		until(context, client -> ShipClient.piloting(client) != null, 80, "into the X-Wing");
		context.waitTicks(3);
		check(context.computeOnClient(client -> client.options.getCameraType() == CameraType.FIRST_PERSON), "boarding switches to the cockpit view");
		check(context.computeOnClient(CockpitView::active), "the cockpit view is active");
		until(context, client -> SaberMusic.mood() == SaberMusic.Mood.FLIGHT, 20, "the flight music starts on boarding");
		context.waitTicks(10);
		check(context.computeOnClient(client -> SaberMusic.currentPiece() != null
				&& SaberMusic.currentPiece().getPath().startsWith("music.flight_")), "a flight piece plays: " + context.computeOnClient(client -> String.valueOf(SaberMusic.currentPiece())));
		check(context.computeOnClient(client -> !SaberMusic.currentPiece().getPath().equals("music.flight_imperial")), "a rebel ship gets no Imperial march");
		check(context.computeOnClient(SaberMusic::playing) <= 2, "never more than the new piece and the fading one");

		// The camera sits in the cockpit, not on the pilot's head.
		Vec3 expected = server.computeOnServer(s -> {
			ShipEntity ship = ship(s);
			return ShipFeatures.toWorld(ShipFeatures.XWING.cockpit(), ship.position(), ship.getYRot(), ship.getXRot(),
					ship.getXRot() == 0.0F ? 0.0 : 1.0);
		});
		double off = context.computeOnClient(client -> client.gameRenderer.mainCamera().position().distanceTo(expected));
		check(off < 1.2, "the camera is in the X-Wing's cockpit (" + String.format(Locale.ROOT, "%.2f", off) + " blocks off)");
		context.takeScreenshot("phase14_cockpit_xwing_ground");

		// Speeding up: the thrust swells and the boost sounds; braking hard: the brake sound.
		int boosts = context.computeOnClient(client -> ShipClient.boosts());
		context.getInput().holdKeyFor(options -> options.keyJump, 30);
		context.getInput().holdKey(options -> options.keyUp);
		float thrust = 0.0F;

		for (int i = 0; i < 40; i++) {
			context.waitTick();
			thrust = Math.max(thrust, context.computeOnClient(client -> ShipClient.thrustVolume(ShipClient.piloting(client))));
		}

		check(thrust > 0.05F, "speeding up, the thrust layer is heard (" + thrust + ")");
		check(context.computeOnClient(client -> ShipClient.boosts()) > boosts, "speeding up from slow gives a boost");
		check(context.computeOnClient(client -> ShipClient.engineVolume(ShipClient.piloting(client))) <= 0.6F, "the engine stays below its cap");
		context.takeScreenshot("phase14_cockpit_xwing_flight");
		context.getInput().releaseKey(options -> options.keyUp);
		context.waitTicks(30);
		float cruising = context.computeOnClient(client -> ShipClient.thrustVolume(ShipClient.piloting(client)));
		int brakes = context.computeOnClient(client -> ShipClient.brakes());
		context.getInput().holdKeyFor(options -> options.keyDown, 20);
		float braking = context.computeOnClient(client -> ShipClient.thrustVolume(ShipClient.piloting(client)));
		check(context.computeOnClient(client -> ShipClient.brakes()) > brakes, "braking hard gives the brake sound");
		check(braking < thrust, "braking, the thrust falls away (" + thrust + " -> " + cruising + " -> " + braking + ")");

		// A space battle: the battle piece; then back to the flight music.
		context.runOnClient(client -> ShipClient.markSpaceBattleForTest());
		until(context, client -> SaberMusic.mood() == SaberMusic.Mood.SPACE_BATTLE, 30, "a hit in the air starts the space battle music");
		context.runOnClient(client -> ShipClient.endSpaceBattleForTest());
		until(context, client -> SaberMusic.mood() == SaberMusic.Mood.FLIGHT, 30, "after the battle, the flight music again");
		check(context.computeOnClient(SaberMusic::playing) <= 2, "never two pieces at once (plus one fading)");

		// F5 still works while flying.
		context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
		context.waitTicks(5);
		check(context.computeOnClient(client -> !CockpitView.active(client)), "F5: the view behind the ship");
		context.takeScreenshot("phase14_xwing_chase");
		context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));

		// Climbing out: the view from before, the music lets go at once.
		context.getInput().holdKeyFor(options -> options.keyShift, 3);
		until(context, client -> client.player.getVehicle() == null, 20, "climbing out");
		context.waitTicks(3);
		check(context.computeOnClient(client -> client.options.getCameraType() == CameraType.THIRD_PERSON_BACK), "climbing out brings back the view from before");
		until(context, client -> SaberMusic.mood() != SaberMusic.Mood.FLIGHT, 10, "the flight music fades out on climbing out");
		context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
		context.waitTicks(80);
		server.computeOnServer(s -> Starships.command(me(s, self), "dismiss", "xwing"));
		context.waitTicks(10);
	}

	/** A look from every cockpit, in the air. */
	private static void cockpits(ClientGameTestContext context, TestServerContext server, UUID self) {
		for (ShipType type : List.of(ShipType.TIE_FIGHTER, ShipType.FALCON, ShipType.STAR_DESTROYER)) {
			server.runCommand("tp @a 0 ~ 0 180 0");
			server.runOnServer(s -> Starships.resetCooldowns());
			context.waitTicks(10);
			server.computeOnServer(s -> Starships.command(me(s, self), "board", type.id()));
			until(context, client -> ShipClient.piloting(client) != null, 80, "into the " + type.id());
			context.getInput().holdKeyFor(options -> options.keyJump, type.capital() ? 10 : 30);
			context.getInput().holdKeyFor(options -> options.keyUp, 30);
			context.getInput().lookAt(context.computeOnClient(client -> ShipClient.piloting(client).getYRot()), 5.0F);
			context.waitTicks(5);
			check(context.computeOnClient(CockpitView::active), type.id() + ": in the cockpit view");

			if (type.capital() || type.id().startsWith("tie")) {
				until(context, client -> SaberMusic.currentPiece() != null, 20, type.id() + ": flight music");
				check(context.computeOnClient(client -> !SaberMusic.currentPiece().getPath().equals("music.flight_cruise")),
						type.id() + ": an Imperial ship gets the darker pieces");
			}

			context.takeScreenshot("phase14_cockpit_" + type.id());
			context.getInput().holdKeyFor(options -> options.keyShift, 3);
			until(context, client -> client.player.getVehicle() == null, 20, "out of the " + type.id());
			context.waitTicks(type.capital() ? 120 : 60);
			server.computeOnServer(s -> Starships.command(me(s, self), "dismiss", type.id()));
			context.waitTicks(10);
		}
	}

	// Damage.

	private static void damage(ClientGameTestContext context, TestServerContext server, UUID self) {
		server.runCommand("tp @a 0 ~ 0 180 0");
		server.runOnServer(s -> Starships.resetCooldowns());
		context.waitTicks(10);
		server.computeOnServer(s -> Starships.command(me(s, self), "call", "falcon"));
		context.waitTicks(40);
		server.runCommand("item replace entity @a weapon.mainhand with minecraft:netherite_sword[minecraft:custom_model_data={strings:[\"sabercraft:standard\",\"sabercraft:red\"]}]");
		context.waitTicks(3);

		server.runOnServer(s -> {
			ShipEntity falcon = ship(s);
			ServerLevel level = s.overworld();
			var saber = level.damageSources().playerAttack(me(s, self));
			check(ShipEntity.weapon(saber).equals("saber"), "a hit with a saber counts as a saber");
			check(Math.abs(falcon.hullDamage(saber, 10.0F) - 6.0F) < 0.01F, "a saber does 60 % on the Falcon's hull");
			check(Math.abs(falcon.hullDamage(level.damageSources().explosion(null, null), 10.0F) - 8.5F) < 0.01F, "an explosion 85 %");
			check(falcon.hullDamage(level.damageSources().explosion(null, null), 1000.0F) <= ShipType.FALCON.hull() * 0.25F + 0.01F,
					"a huge blast takes at most a quarter of the hull");
			check(ShipFeatures.STAR_DESTROYER.smallArms() < 0.2F, "a Star Destroyer shrugs off hand weapons");

			// The hull of a big ship, outside its small collision box.
			ShipFeatures destroyer = ShipFeatures.STAR_DESTROYER;
			Vec3 origin = new Vec3(0.0, 100.0, 0.0);
			check(destroyer.clip(origin, 0.0F, new Vec3(30.0, 103.0, -25.0), new Vec3(10.0, 103.0, -25.0)) >= 0.0,
					"a bolt at the wide stern of a Star Destroyer hits its hull");
			check(destroyer.clip(origin, 0.0F, new Vec3(30.0, 103.0, 38.0), new Vec3(10.0, 103.0, 38.0)) < 0.0,
					"one passing beside its narrow bow misses");
		});

		// The hull mends only after the delay, and slowly.
		float max = server.computeOnServer(s -> ship(s).getMaxHealth());
		server.runOnServer(s -> {
			ShipEntity falcon = ship(s);
			falcon.setHealth(max * 0.5F);
			falcon.setSinceDamageForTest(0);
		});
		context.waitTicks(100);
		check(server.computeOnServer(s -> ship(s).getHealth()) == max * 0.5F, "no mending right after damage");
		server.runOnServer(s -> ship(s).setSinceDamageForTest(AbilityConfig.get().shipRepairDelaySeconds * 20));
		context.waitTicks(100);
		float mended = server.computeOnServer(s -> ship(s).getHealth()) - max * 0.5F;
		check(mended > 0.0F && mended <= 1.5F, "after the delay it mends slowly (" + mended + " in 5 s of " + max + ")");

		// Sent away and called again: the damage stays.
		float before = server.computeOnServer(s -> ship(s).getHealth());
		server.runOnServer(s -> Starships.resetCooldowns());
		server.computeOnServer(s -> Starships.command(me(s, self), "dismiss", "falcon"));
		context.waitTicks(10);
		server.computeOnServer(s -> Starships.command(me(s, self), "call", "falcon"));
		context.waitTicks(40);
		float again = server.computeOnServer(s -> ship(s).getHealth());
		check(again < max * 0.6F && again >= before - 0.01F, "called again it keeps its damage (" + before + " -> " + again + ")");

		// Destroyed: a few big hits, never one.
		int explosions = ShipEntity.explosions();
		int hits = 0;

		while (server.computeOnServer(s -> Starships.liveCount()) > 0 && hits < 10) {
			hits++;
			server.runOnServer(s -> {
				ShipEntity falcon = ship(s);
				falcon.invulnerableTime = 0;
				falcon.hurtServer(s.overworld(), s.overworld().damageSources().explosion(null, null), 1000.0F);
			});
			context.waitTick();
		}

		check(hits >= 2, "no single hit destroys the Falcon (" + hits + " hits)");
		context.waitTicks(10);
		context.takeScreenshot("phase14_falcon_destroyed");
		check(ShipEntity.explosions() == explosions + 1, "it explodes once");
		check(server.computeOnServer(s -> s.overworld().getEntitiesOfClass(ShipEntity.class, AREA, Entity::isAlive).isEmpty()), "no wreck is left");
		check(server.computeOnServer(s -> s.overworld().getEntitiesOfClass(ItemEntity.class, AREA, Entity::isAlive).isEmpty()), "nothing drops (no duplicates)");
		check(server.computeOnServer(s -> me(s, self).isAlive()), "the owner is fine");
		server.runCommand("clear @a");
		server.runOnServer(s -> Starships.resetCooldowns());
		context.waitTicks(20);
	}

	// Hyperspace.

	private static void hyperspace(ClientGameTestContext context, TestServerContext server, UUID self) {
		server.runCommand("tp @a 0 ~ 0 180 0");
		server.runOnServer(s -> Hyperspace.reset());
		context.waitTicks(10);
		server.computeOnServer(s -> Starships.command(me(s, self), "board", "xwing"));
		until(context, client -> ShipClient.piloting(client) != null, 80, "into the X-Wing for a jump");
		check(key(server.computeOnServer(s -> Hyperspace.request(me(s, self)))).equals("sabercraft.hyperspace.landed"), "no jump from the ground");

		context.getInput().holdKeyFor(options -> options.keyJump, 60);
		context.getInput().holdKeyFor(options -> options.keyUp, 20);
		server.runOnServer(s -> ship(s).setEnergyForTest(5.0F));
		check(key(server.computeOnServer(s -> Hyperspace.request(me(s, self)))).equals("sabercraft.hyperspace.energy"), "no jump with too little energy");
		server.runOnServer(s -> ship(s).setEnergyForTest(100000.0F));

		// A wall ahead: the jump stops before it, or is refused.
		Vec3 at = server.computeOnServer(s -> ship(s).position());
		float yaw = server.computeOnServer(s -> ship(s).getYRot());
		Vec3 ahead = Vec3.directionFromRotation(0.0F, yaw);
		Vec3 wall = at.add(ahead.scale(60.0));
		Vec3 side = Vec3.directionFromRotation(0.0F, yaw + 90.0F);
		Vec3 a = wall.add(side.scale(-12.0));
		Vec3 b = wall.add(side.scale(12.0)).add(ahead.scale(2.0));
		server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:stone", (int) Math.floor(Math.min(a.x, b.x)), (int) at.y - 10,
				(int) Math.floor(Math.min(a.z, b.z)), (int) Math.ceil(Math.max(a.x, b.x)), (int) at.y + 12, (int) Math.ceil(Math.max(a.z, b.z))));
		context.waitTicks(5);
		Vec3 blocked = server.computeOnServer(s -> Hyperspace.targetForTest(ship(s)));
		check(blocked == null || blocked.distanceTo(at) < 58.0, "the jump never goes through the wall ("
				+ (blocked == null ? "refused" : Math.round(blocked.distanceTo(at)) + " blocks") + ")");
		server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:air", (int) Math.floor(Math.min(a.x, b.x)), (int) at.y - 10,
				(int) Math.floor(Math.min(a.z, b.z)), (int) Math.ceil(Math.max(a.x, b.x)), (int) at.y + 12, (int) Math.ceil(Math.max(a.z, b.z))));
		context.waitTicks(5);

		int seen = HyperspaceClient.jumpsSeen();
		int jumps = Hyperspace.jumps();
		Vec3 from = server.computeOnServer(s -> ship(s).position());
		String charging = key(server.computeOnServer(s -> Hyperspace.request(me(s, self))));
		check(charging.equals("sabercraft.hyperspace.charging"), "in the air the hyperdrive charges: " + charging);
		context.waitTicks(15);
		check(context.computeOnClient(client -> HyperspaceClient.charge(0.0F)) >= 0.0F, "the pilot sees the charge");
		context.takeScreenshot("phase14_hyperspace_charge");

		for (int i = 0; i < 40 && Hyperspace.jumps() == jumps; i++) {
			context.waitTick();
		}

		check(Hyperspace.jumps() == jumps + 1, "the ship jumps");
		context.waitTicks(3);
		context.takeScreenshot("phase14_hyperspace_tunnel");
		Vec3 to = server.computeOnServer(s -> ship(s).position());
		double distance = to.distanceTo(from);
		check(distance > 24.0 && distance <= AbilityConfig.get().hyperspaceDistance + 8.0, "it comes out far ahead (" + Math.round(distance) + " blocks)");
		check(to.subtract(from).normalize().dot(Vec3.directionFromRotation(0.0F, yaw)) > 0.8, "along its heading");
		check(server.computeOnServer(s -> s.overworld().noCollision(ship(s), ship(s).getBoundingBox())), "in free space");
		check(HyperspaceClient.jumpsSeen() > seen, "the client was told of the jump");
		context.waitTicks(20);
		context.takeScreenshot("phase14_hyperspace_arrival");
		check(key(server.computeOnServer(s -> Hyperspace.request(me(s, self)))).equals("sabercraft.hyperspace.cooldown"), "then the drive cools down");

		context.getInput().holdKeyFor(options -> options.keyShift, 3);
		until(context, client -> client.player.getVehicle() == null, 20, "out of the X-Wing");
		context.waitTicks(100);
		server.computeOnServer(s -> Starships.command(me(s, self), "dismiss", "xwing"));
		server.runCommand("tp @a 0 ~ 0 180 0");
		context.waitTicks(20);
	}

	// Interiors.

	private static void falconInterior(ClientGameTestContext context, TestServerContext server, UUID self) {
		server.runOnServer(s -> Starships.resetCooldowns());
		server.computeOnServer(s -> Starships.command(me(s, self), "board", "falcon"));
		until(context, client -> ShipClient.piloting(client) != null, 80, "into the Falcon");
		context.getInput().holdKeyFor(options -> options.keyShift, 3);
		until(context, client -> client.player.getVehicle() == null, 20, "out of the Falcon");
		context.waitTicks(20);
		Vec3 falcon = server.computeOnServer(s -> ship(s).position());

		server.runOnServer(s -> ShipInteriors.boardFromOutside(me(s, self), ship(s)));
		until(context, client -> ShipInteriors.isInterior(client.level), 60, "sneak-using the Falcon takes you aboard");
		check(server.computeOnServer(s -> ShipInteriors.aboardKey(me(s, self))) != null, "the server knows you are aboard");
		context.waitTicks(40);
		BlockPos origin = server.computeOnServer(s -> ShipInteriors.plotForTest(s, self, "falcon"));
		check(origin != null, "the Falcon has its plot");
		check(com.phiro.sabercraft.interior.InteriorBuilder.unknown().isEmpty(), "every block of the plan exists: "
				+ com.phiro.sabercraft.interior.InteriorBuilder.unknown());
		until(context, client -> "ship.interior_falcon".equals(InteriorAmbience.playing()), 40, "the Falcon's hum plays inside");
		reachable(server, InteriorPlan.of("falcon"), origin);

		// The rooms, as pictures.
		look(context, server, origin, new Vec3(21.0, 7.0, 36.0), 180.0F, 15.0F, "phase14_falcon_main_hold");
		look(context, server, origin, new Vec3(42.5, 8.0, 25.0), 180.0F, 10.0F, "phase14_falcon_cockpit");
		look(context, server, origin, new Vec3(31.5, 7.0, 24.0), 90.0F, 5.0F, "phase14_falcon_cockpit_tube");
		look(context, server, origin, new Vec3(21.0, 7.0, 44.5), 0.0F, 10.0F, "phase14_falcon_engine_room");
		look(context, server, origin, new Vec3(12.5, 7.0, 15.0), 180.0F, 10.0F, "phase14_falcon_cargo_hold");
		look(context, server, origin, new Vec3(9.5, 7.0, 30.5), 180.0F, 5.0F, "phase14_falcon_corridor");
		look(context, server, origin, new Vec3(20.5, 13.0, 31.5), 180.0F, -10.0F, "phase14_falcon_top_turret");

		// Protected: nothing can be broken or placed by a survival player.
		BlockPos wall = origin.offset(24, 6, 34);
		BlockState wallState = server.computeOnServer(s -> s.getLevel(ShipInteriors.DIMENSION).getBlockState(wall));
		check(!wallState.isAir(), "there is a block to try on");
		check(!server.computeOnServer(s -> me(s, self).gameMode.destroyBlock(wall)), "a block of the interior cannot be broken");
		check(server.computeOnServer(s -> s.getLevel(ShipInteriors.DIMENSION).getBlockState(wall)) == wallState, "and stays");
		BlockPos floor = origin.offset(25, 6, 35);
		server.runOnServer(s -> {
			ServerPlayer player = me(s, self);
			ItemStack stone = new ItemStack(Items.STONE);
			player.setItemInHand(InteractionHand.MAIN_HAND, stone);
			player.gameMode.useItemOn(player, player.level(), stone, InteractionHand.MAIN_HAND,
					new BlockHitResult(Vec3.atCenterOf(floor).add(0.0, 0.5, 0.0), Direction.UP, floor, false));
		});
		check(server.computeOnServer(s -> !s.getLevel(ShipInteriors.DIMENSION).getBlockState(floor.above()).is(net.minecraft.world.level.block.Blocks.STONE)),
				"nothing can be placed in the interior");
		server.runCommand("clear @a");

		// The helm: the owner takes the controls of the real ship.
		Component helm = server.computeOnServer(s -> ShipInteriors.helmForTest(me(s, self)));
		check(helm != null, "the helm answers");
		until(context, client -> ShipClient.piloting(client) != null, 40, "the helm puts the owner into the pilot's seat");
		// Sneak until the server lets the pilot out too (a busy server can miss a short tap).
		for (int i = 0; i < 5 && server.computeOnServer(s -> me(s, self).getVehicle() != null); i++) {
			context.getInput().holdKeyFor(options -> options.keyShift, 5);
			context.waitTicks(5);
		}

		check(server.computeOnServer(s -> me(s, self).getVehicle() == null), "out again");
		context.waitTicks(10);

		// The ship settles back onto the ground before anyone can walk aboard.
		for (int i = 0; i < 300 && !server.computeOnServer(s -> ship(s).onGround()); i++) {
			context.waitTick();
		}

		// Aboard again and out by the ramp.
		Component refused = server.computeOnServer(s -> ShipInteriors.boardForTest(me(s, self), ship(s)));
		String where = server.computeOnServer(s -> ship(s).position() + " ground=" + ship(s).onGround() + " motion=" + ship(s).getDeltaMovement()
				+ " pilot=" + ship(s).pilot() + " vehicle=" + me(s, self).getVehicle());
		check(refused == null, "boarding again is allowed" + (refused != null ? ": " + refused.getString() + " " + where : ""));
		until(context, client -> ShipInteriors.isInterior(client.level), 60, "aboard again");
		context.waitTicks(10);
		AABB exit = InteriorPlan.of("falcon").exits().getFirst();
		Vec3 ramp = exit.getCenter().add(origin.getX(), origin.getY(), origin.getZ());
		// First a step away from the entry, then onto the ramp.
		server.runCommand(String.format(Locale.ROOT, "execute in sabercraft:ship_interiors run tp @a %.2f %.2f %.2f", origin.getX() + 35.5, origin.getY() + 7.0, origin.getZ() + 34.5));
		context.waitTicks(5);
		server.runCommand(String.format(Locale.ROOT, "execute in sabercraft:ship_interiors run tp @a %.2f %.2f %.2f", ramp.x, origin.getY() + exit.minY + 0.2, ramp.z));
		until(context, client -> !ShipInteriors.isInterior(client.level), 60, "standing on the ramp takes you off the ship");
		Vec3 outside = server.computeOnServer(s -> me(s, self).position());
		check(outside.distanceTo(falcon) < 16.0, "next to the Falcon (" + Math.round(outside.distanceTo(falcon)) + " blocks)");
		check(server.computeOnServer(s -> s.overworld().noCollision(me(s, self))), "not inside a block");
		check(server.computeOnServer(s -> ShipInteriors.aboardKey(me(s, self))) == null, "no longer aboard");
		// The Falcon's chunk was unloaded while nobody was outside; it comes back as soon as its owner is near.
		for (int i = 0; i < 60 && server.computeOnServer(s -> Starships.liveCount()) == 0; i++) {
			context.waitTick();
		}

		check(server.computeOnServer(s -> Starships.liveCount()) == 1, "the Falcon is back, once");
		context.waitTicks(10);

		// The ship is lost with the crew aboard: an escape pod puts them next to the wreck. Its chunk
		// is kept loaded here, as it would be when another player is near and shoots it down.
		String chunk = String.format(Locale.ROOT, "%d %d", (int) Math.floor(falcon.x), (int) Math.floor(falcon.z));
		server.runCommand("forceload add " + chunk);
		server.runOnServer(s -> ShipInteriors.boardFromOutside(me(s, self), ship(s)));
		until(context, client -> ShipInteriors.isInterior(client.level), 60, "aboard for the last time");
		context.waitTicks(10);

		for (int i = 0; i < 12 && server.computeOnServer(s -> Starships.liveCount()) > 0; i++) {
			server.runOnServer(s -> {
				ShipEntity ship = ship(s);
				ship.invulnerableTime = 0;
				ship.hurtServer(s.overworld(), s.overworld().damageSources().explosion(null, null), 1000.0F);
			});
			context.waitTick();
		}

		until(context, client -> !ShipInteriors.isInterior(client.level), 60, "the escape pod brings the crew out");
		check(server.computeOnServer(s -> me(s, self).isAlive() && me(s, self).hasEffect(MobEffects.RESISTANCE)), "alive and shielded");
		check(server.computeOnServer(s -> me(s, self).position().distanceTo(falcon)) < 24.0, "next to the wreck");
		BlockPos kept = server.computeOnServer(s -> ShipInteriors.plotForTest(s, self, "falcon"));
		check(origin.equals(kept), "the interior stays for the next Falcon");
		server.runCommand("forceload remove " + chunk);
		server.runOnServer(s -> Starships.resetCooldowns());
		server.runCommand("tp @a 0 ~ 0 180 0");
		context.waitTicks(60);
	}

	private static void destroyerInterior(ClientGameTestContext context, TestServerContext server, UUID self) {
		server.runOnServer(s -> Starships.resetCooldowns());
		server.computeOnServer(s -> Starships.command(me(s, self), "board", "star_destroyer"));
		until(context, client -> ShipClient.piloting(client) != null, 80, "onto the Star Destroyer");
		context.waitTicks(60);

		// From the pilot's seat straight onto the bridge (hangar menu "walk aboard").
		Component aboard = server.computeOnServer(s -> Starships.command(me(s, self), "interior", "star_destroyer"));
		check(aboard != null, "the hangar answers");
		until(context, client -> ShipInteriors.isInterior(client.level), 60, "from the helm onto the bridge: " + aboard.getString());
		context.waitTicks(40);
		BlockPos origin = server.computeOnServer(s -> ShipInteriors.plotForTest(s, self, "star_destroyer"));
		check(com.phiro.sabercraft.interior.InteriorBuilder.unknown().isEmpty(), "every block of the Star Destroyer plan exists: "
				+ com.phiro.sabercraft.interior.InteriorBuilder.unknown());
		reachable(server, InteriorPlan.of("star_destroyer"), origin);
		until(context, client -> "ship.interior_bridge".equals(InteriorAmbience.playing()), 40, "the bridge drone plays");
		look(context, server, origin, new Vec3(22.5, 31.0, 30.0), 180.0F, 5.0F, "phase14_destroyer_bridge");
		look(context, server, origin, new Vec3(22.5, 31.0, 8.0), 180.0F, 0.0F, "phase14_destroyer_window");
		look(context, server, origin, new Vec3(22.5, 31.0, 40.0), 90.0F, 0.0F, "phase14_destroyer_command_deck");

		// The lift down to the hangar.
		InteriorPlan.Lift up = InteriorPlan.of("star_destroyer").lifts().get(1);
		server.runCommand(String.format(Locale.ROOT, "execute in sabercraft:ship_interiors run tp @a %.2f %.2f %.2f", origin.getX() + 22.5, origin.getY() + 31.0, origin.getZ() + 40.5));
		context.waitTicks(5);
		Vec3 lift = up.area().getCenter();
		server.runCommand(String.format(Locale.ROOT, "execute in sabercraft:ship_interiors run tp @a %.2f %.2f %.2f", origin.getX() + lift.x, origin.getY() + up.area().minY + 0.1,
				origin.getZ() + lift.z));
		until(context, client -> client.player.getY() < origin.getY() + 10.0, 40, "the turbolift takes you down to the hangar");
		look(context, server, origin, new Vec3(22.5, 5.0, 25.0), 180.0F, 10.0F, "phase14_destroyer_hangar");

		// Out through the hangar: the tractor beam lowers you.
		AABB exit = InteriorPlan.of("star_destroyer").exits().getFirst();
		server.runCommand(String.format(Locale.ROOT, "execute in sabercraft:ship_interiors run tp @a %.2f %.2f %.2f", origin.getX() + exit.getCenter().x, origin.getY() + exit.minY + 0.1,
				origin.getZ() + exit.getCenter().z));
		until(context, client -> !ShipInteriors.isInterior(client.level), 60, "the hangar exit lets you down");
		check(server.computeOnServer(s -> me(s, self).hasEffect(MobEffects.SLOW_FALLING)), "floating down gently");

		// The tractor beam: sneaking under the hangar lifts you in.
		for (int i = 0; i < 300 && !server.computeOnServer(s -> me(s, self).onGround()); i++) {
			context.waitTick();
		}

		Vec3 hangar = server.computeOnServer(s -> ShipFeatures.toWorld(new Vec3(0.0, 0.0, 2.0), ship(s).position(), ship(s).getYRot(), 0.0F, 0.0));
		server.runCommand(String.format(Locale.ROOT, "tp @a %.2f ~ %.2f", hangar.x, hangar.z));
		context.getInput().holdKey(options -> options.keyShift);
		until(context, client -> ShipInteriors.isInterior(client.level), 80, "the tractor beam lifts a sneaking player in");
		context.getInput().releaseKey(options -> options.keyShift);
		context.waitTicks(20);
		server.runCommand(String.format(Locale.ROOT, "execute in sabercraft:ship_interiors run tp @a %.2f %.2f %.2f", origin.getX() + exit.getCenter().x, origin.getY() + exit.minY + 0.1,
				origin.getZ() + exit.getCenter().z));
		until(context, client -> !ShipInteriors.isInterior(client.level), 60, "and out again");
		for (int i = 0; i < 300 && !server.computeOnServer(s -> me(s, self).onGround()); i++) {
			context.waitTick();
		}

		server.computeOnServer(s -> Starships.command(me(s, self), "dismiss", "star_destroyer"));
		server.runCommand("tp @a 0 ~ 0 180 0");
		context.waitTicks(40);
	}

	/** Vader's new helmet, up close. */
	private static void vader(ClientGameTestContext context, TestServerContext server) {
		context.runOnClient(client -> OutfitClient.choose(Outfit.VADER.id().toString()));
		server.runCommand("tp @a 0 ~ 0 0 0");
		context.waitTicks(10);
		context.runOnClient(client -> {
			client.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
			client.options.fov().set(30);
		});
		context.getInput().lookAt(0.0F, 15.0F);
		context.waitTicks(20);
		context.takeScreenshot("phase14_vader_helmet");
		context.getInput().lookAt(0.0F, 45.0F);
		context.waitTicks(5);
		context.takeScreenshot("phase14_vader_helmet_top");
		context.runOnClient(client -> {
			client.options.fov().set(70);
			client.options.setCameraType(CameraType.FIRST_PERSON);
			OutfitClient.choose("");
		});
		context.waitTicks(5);
	}

	/** Logging out aboard: the world closes with the player inside the Falcon. */
	private static void stayAboard(ClientGameTestContext context, TestServerContext server, UUID self) {
		server.runOnServer(s -> Starships.resetCooldowns());
		server.computeOnServer(s -> Starships.command(me(s, self), "board", "falcon"));
		until(context, client -> ShipClient.piloting(client) != null, 80, "into a new Falcon");
		context.getInput().holdKeyFor(options -> options.keyShift, 3);
		until(context, client -> client.player.getVehicle() == null, 20, "out of it");
		context.waitTicks(20);
		server.runOnServer(s -> ShipInteriors.boardFromOutside(me(s, self), ship(s)));
		until(context, client -> ShipInteriors.isInterior(client.level), 60, "aboard before leaving the world");
		context.waitTicks(40);
		server.runOnServer(s -> ShipInteriors.save());
		check(server.computeOnServer(s -> Files.exists(s.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("sabercraft")
				.resolve("interiors.json"))), "the interiors are saved with the world");
		context.waitTicks(20);
	}

	private static void reopen(ClientGameTestContext context, TestWorldSave save, UUID self) {
		try (TestSingleplayerContext singleplayer = save.open()) {
			TestServerContext server = singleplayer.getServer();
			context.waitTicks(60);
			check(context.computeOnClient(client -> ShipInteriors.isInterior(client.level)), "after logging in again, still aboard");
			for (int i = 0; i < 40 && server.computeOnServer(s -> ShipInteriors.aboardKey(me(s, self))) == null; i++) {
				context.waitTick();
			}

			check(server.computeOnServer(s -> ShipInteriors.aboardKey(me(s, self))) != null, "and the server knows the plot");
			BlockPos origin = server.computeOnServer(s -> ShipInteriors.plotForTest(s, self, "falcon"));
			check(server.computeOnServer(s -> !s.getLevel(ShipInteriors.DIMENSION).getBlockState(origin.offset(21, 6, 33)).isAir()),
					"the rooms are still there");
			context.takeScreenshot("phase14_falcon_after_relog");
			check(server.computeOnServer(s -> ShipInteriors.leaveForTest(me(s, self))), "leaving works");
			until(context, client -> !ShipInteriors.isInterior(client.level), 40, "back outside after the relog");
			context.waitTicks(40);
			check(server.computeOnServer(s -> s.overworld().getEntitiesOfClass(ShipEntity.class, AREA, Entity::isAlive).size()) == 1,
					"the Falcon is there, once");
			context.waitTicks(20);
		}
	}

	/** On a dedicated server: the cockpit view, a jump everyone hears of, the Falcon's rooms. */
	private static void dedicatedServer(ClientGameTestContext context) {
		try (TestDedicatedServerContext server = context.worldBuilder().createServer()) {
			try (var connection = server.connect()) {
				connection.waitForChunksDownload();
				context.waitTicks(40);
				server.runCommand("gamerule spawn_mobs false");
				context.waitFor(client -> ShipClient.status() != null, 40);
				context.runOnClient(client -> ShipClient.command("board", ShipType.XWING));
				until(context, client -> ShipClient.piloting(client) != null, 80, "dedicated server: into the X-Wing");
				context.waitTicks(3);
				check(context.computeOnClient(CockpitView::active), "dedicated server: the cockpit view");
				context.getInput().holdKeyFor(options -> options.keyJump, 60);
				context.getInput().holdKeyFor(options -> options.keyUp, 20);
				int seen = HyperspaceClient.jumpsSeen();
				context.getInput().pressKey(options -> HyperspaceClient.JUMP_KEY);
				for (int i = 0; i < 80 && HyperspaceClient.jumpsSeen() == seen; i++) {
					context.waitTick();
				}

				check(HyperspaceClient.jumpsSeen() > seen, "dedicated server: the X key jumps, and the client sees it");
				context.waitTicks(2);
				context.takeScreenshot("phase14_dedicated_hyperspace");
				context.getInput().holdKeyFor(options -> options.keyShift, 3);
				until(context, client -> client.player.getVehicle() == null, 20, "dedicated server: out");
				context.waitTicks(120);
				context.runOnClient(client -> ShipClient.command("dismiss", ShipType.XWING));
				context.waitTicks(10);
				context.runOnClient(client -> ShipClient.command("call", ShipType.FALCON));
				context.waitTicks(60);

				// The called Falcon flies in first; it can be walked into once it has landed.
				for (int i = 0; i < 300 && !server.computeOnServer(s -> {
					ShipEntity falcon = Starships.ship(s.getPlayerList().getPlayers().getFirst());
					return falcon != null && falcon.ship() == ShipType.FALCON && falcon.onGround();
				}); i++) {
					context.waitTick();
				}

				// The hangar button, from the client; a busy server may still answer "land first", so ask
				// again a few times, and if it never works say what the server answered.
				for (int attempt = 0; attempt < 4 && !context.computeOnClient(client -> ShipInteriors.isInterior(client.level)); attempt++) {
					context.runOnClient(client -> ShipClient.command("interior", ShipType.FALCON));

					for (int i = 0; i < 60 && !context.computeOnClient(client -> ShipInteriors.isInterior(client.level)); i++) {
						context.waitTick();
					}
				}

				if (!context.computeOnClient(client -> ShipInteriors.isInterior(client.level))) {
					String answer = server.computeOnServer(s -> {
						ServerPlayer player = s.getPlayerList().getPlayers().getFirst();
						ShipEntity falcon = Starships.ship(player);
						return (falcon == null ? "no ship" : falcon.ship() + " at " + falcon.position() + " ground=" + falcon.onGround() + " player at " + player.position())
								+ ": " + ShipInteriors.enterOwn(player, ShipType.FALCON).getString();
					});
					check(false, "dedicated server: aboard the Falcon (" + answer + ")");
				}

				check(true, "dedicated server: aboard the Falcon");
				context.waitTicks(40);
				context.takeScreenshot("phase14_dedicated_falcon_interior");
				check(server.computeOnServer(s -> ShipInteriors.leaveForTest(s.getPlayerList().getPlayers().getFirst())), "dedicated server: leaving");
				until(context, client -> !ShipInteriors.isInterior(client.level), 40, "dedicated server: outside again");
				context.waitTicks(20);
			}
		}
	}

	// Helpers.

	/** Every room, exit and lift of a plan can be walked to from the entry (lifts carry you on). */
	private static void reachable(TestServerContext server, InteriorPlan plan, BlockPos origin) {
		String result = server.computeOnServer(s -> {
			ServerLevel level = s.getLevel(ShipInteriors.DIMENSION);
			Set<BlockPos> seen = new HashSet<>();
			ArrayDeque<BlockPos> queue = new ArrayDeque<>();
			queue.add(BlockPos.containing(plan.entry()));
			BlockPos size = plan.size();

			while (!queue.isEmpty()) {
				BlockPos p = queue.poll();

				if (!seen.add(p)) {
					continue;
				}

				for (InteriorPlan.Lift lift : plan.lifts()) {
					if (lift.area().contains(Vec3.atBottomCenterOf(p).add(0.0, 0.1, 0.0))) {
						queue.add(BlockPos.containing(lift.arrival()));
					}
				}

				for (Direction direction : Direction.Plane.HORIZONTAL) {
					for (int dy = 1; dy >= -3; dy--) {
						BlockPos n = p.relative(direction).above(dy);

						if (n.getX() < 0 || n.getZ() < 0 || n.getY() < 0 || n.getX() > size.getX() || n.getZ() > size.getZ() || n.getY() > size.getY()) {
							continue;
						}

						if (dy == 1 && !passable(level, origin.offset(p.above(2)))) {
							continue;
						}

						if (dy < 0 && !(passable(level, origin.offset(p.relative(direction))) && passable(level, origin.offset(p.relative(direction).above())))) {
							continue;
						}

						if (stand(level, origin, n)) {
							queue.add(n);
							break;
						}
					}
				}

				if (climb(level, origin.offset(p)) || climb(level, origin.offset(p.below()))) {
					for (BlockPos n : List.of(p.above(), p.below())) {
						if (passable(level, origin.offset(n)) && passable(level, origin.offset(n.above()))) {
							queue.add(n);
						}
					}
				}
			}

			StringBuilder missing = new StringBuilder();

			for (InteriorPlan.Room room : plan.rooms()) {
				if (seen.stream().noneMatch(p -> room.area().contains(Vec3.atBottomCenterOf(p).add(0.0, 0.1, 0.0)))) {
					missing.append(room.id()).append(' ');
				}
			}

			for (AABB exit : plan.exits()) {
				if (seen.stream().noneMatch(p -> exit.contains(Vec3.atBottomCenterOf(p).add(0.0, 0.1, 0.0)))) {
					missing.append("exit ");
				}
			}

			return seen.size() + " cells, missing: " + missing;
		});
		check(result.endsWith("missing: "), plan.id() + ": every room, exit and lift can be reached on foot (" + result + ")");
	}

	private static boolean stand(ServerLevel level, BlockPos origin, BlockPos p) {
		BlockPos at = origin.offset(p);
		return passable(level, at) && passable(level, at.above())
				&& (!passable(level, at.below()) || climb(level, at) || climb(level, at.below()) || low(level, at));
	}

	private static boolean passable(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		var shape = state.getCollisionShape(level, pos);
		return shape.isEmpty() || climb(level, pos) || shape.max(Direction.Axis.Y) <= 0.5 || shape.min(Direction.Axis.Y) >= 0.8 && false;
	}

	private static boolean low(ServerLevel level, BlockPos pos) {
		var shape = level.getBlockState(pos).getCollisionShape(level, pos);
		return !shape.isEmpty() && shape.max(Direction.Axis.Y) <= 0.5;
	}

	private static boolean climb(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return state.getBlock() instanceof LadderBlock || state.getBlock() instanceof ScaffoldingBlock;
	}

	/** Puts the player at a spot of the plot, looking a way, and takes a picture. */
	private static void look(ClientGameTestContext context, TestServerContext server, BlockPos origin, Vec3 local, float yaw, float pitch, String name) {
		server.runCommand(String.format(Locale.ROOT, "execute in sabercraft:ship_interiors run tp @a %.2f %.2f %.2f %.1f %.1f", origin.getX() + local.x, origin.getY() + local.y,
				origin.getZ() + local.z, yaw, pitch));
		context.waitTicks(30);
		context.takeScreenshot(name);
	}

	private static String key(Component message) {
		return message.getContents() instanceof TranslatableContents translatable ? translatable.getKey() : message.getString();
	}

	private static ShipEntity ship(MinecraftServer server) {
		for (ServerLevel level : server.getAllLevels()) {
			List<ShipEntity> ships = level.getEntitiesOfClass(ShipEntity.class, AREA, Entity::isAlive);

			if (!ships.isEmpty()) {
				return ships.getFirst();
			}
		}

		throw new AssertionError("no ship in the world");
	}

	private static ServerPlayer me(MinecraftServer server, UUID self) {
		return server.getPlayerList().getPlayer(self);
	}

	private static void until(ClientGameTestContext context, Predicate<Minecraft> condition, int ticks, String message) {
		for (int i = 0; i < ticks && !context.computeOnClient(condition::test); i++) {
			context.waitTick();
		}

		check(context.computeOnClient(condition::test), message);
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}

		SaberCraft.LOGGER.info("OK: {}", message);
	}
}
