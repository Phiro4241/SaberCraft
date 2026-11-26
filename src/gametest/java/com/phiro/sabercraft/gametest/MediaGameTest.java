package com.phiro.sabercraft.gametest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;

import net.minecraft.client.CameraType;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.ability.AbilityConfig;
import com.phiro.sabercraft.character.Outfit;
import com.phiro.sabercraft.client.ImmersionConfig;
import com.phiro.sabercraft.client.ability.SaberAbilityClient;
import com.phiro.sabercraft.client.character.OutfitClient;
import com.phiro.sabercraft.client.menu.SaberMenuScreen;
import com.phiro.sabercraft.client.ship.ShipClient;
import com.phiro.sabercraft.client.sky.PlanetSky;
import com.phiro.sabercraft.client.story.StoryCinema;
import com.phiro.sabercraft.client.story.StoryClient;
import com.phiro.sabercraft.droid.DroidType;
import com.phiro.sabercraft.droid.Droids;
import com.phiro.sabercraft.interior.DestroyerInterior;
import com.phiro.sabercraft.interior.ShipInteriors;
import com.phiro.sabercraft.network.StoryActionPayload;
import com.phiro.sabercraft.network.StoryDialogPayload;
import com.phiro.sabercraft.ship.ShipEntity;
import com.phiro.sabercraft.ship.ShipType;
import com.phiro.sabercraft.ship.Starships;
import com.phiro.sabercraft.story.Story;
import com.phiro.sabercraft.story.StorySession;
import com.phiro.sabercraft.story.StoryTesting;

/**
 * Pictures for the project's pages (README, Modrinth, ModDB). It runs only when the environment
 * variable {@code SABERCRAFT_MEDIA} names a folder for them, so the normal test run and CI skip it
 * at once; {@code SABERCRAFT_MEDIA_ONLY=a,b} takes only some scenes.
 *
 * <p>Every picture is the game itself at 1920 x 1080: scenes are set up with the mod's own ships,
 * droids, sabers, skies and story, nothing is painted over. It checks nothing; a scene that does
 * not come about is logged and left out.
 */
public class MediaGameTest implements FabricClientGameTest {
	private static final int WIDTH = 1920;
	private static final int HEIGHT = 1080;
	private static final int H = DestroyerInterior.HANGAR + 1;
	private static final int R = DestroyerInterior.REACTOR_BOTTOM;

	private ClientGameTestContext context;
	private TestServerContext server;
	private UUID self;
	private Path dir;
	private Set<String> only = Set.of();
	private int taken;

	@Override
	public void runTest(ClientGameTestContext context) {
		String out = System.getenv("SABERCRAFT_MEDIA");

		if (out == null || out.isBlank()) {
			return;
		}

		this.context = context;
		this.dir = Path.of(out);
		String filter = System.getenv("SABERCRAFT_MEDIA_ONLY");

		if (filter != null && !filter.isBlank()) {
			this.only = Set.of(filter.split(","));
		}

		try {
			Files.createDirectories(this.dir);
		} catch (IOException error) {
			throw new UncheckedIOException(error);
		}

		context.getInput().resizeWindow(WIDTH, HEIGHT);
		context.runOnClient(client -> {
			client.options.renderDistance().set(12);
			client.options.setCameraType(CameraType.FIRST_PERSON);
		});

		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			context.waitTicks(200);
			this.server = singleplayer.getServer();
			this.server.runCommand("gamerule advance_time false");
			this.server.runCommand("gamerule advance_weather false");
			this.server.runCommand("gamerule spawn_mobs false");
			this.server.runCommand("weather clear");
			this.server.runCommand("time set day");
			this.server.runCommand("difficulty normal");
			this.server.runCommand("gamerule send_command_feedback false");
			this.server.runCommand("recipe give @a *");
			this.server.runCommand("kill @e[type=!player]");
			this.self = context.computeOnClient(client -> client.player.getUUID());
			this.server.runCommand("gamemode creative @a");
			this.home = this.on(s -> this.me(s).position());

			this.scene("blades", this::blades);
			this.scene("hero", this::hero);
			this.scene("combat", this::combat);
			this.scene("menus", this::menus);
			this.scene("blaster", this::blaster);
			this.scene("droids", this::droids);
			this.scene("fleet", this::fleet);
			this.scene("flight", this::flight);
			this.scene("destroyer", this::destroyer);
			this.scene("interiors", this::interiors);
			this.scene("planets", this::planets);
			this.scene("briefing", this::briefing);
			this.scene("story", this::story);
		}

		SaberCraft.LOGGER.info("MEDIA: {} pictures in {}", this.taken, this.dir);
	}

	private Vec3 home;

	/** One scene: on its own, whatever the others did; afterwards everything as it was. */
	private void scene(String name, Runnable scene) {
		if (!this.only.isEmpty() && !this.only.contains(name)) {
			return;
		}

		SaberCraft.LOGGER.info("MEDIA scene {}", name);

		try {
			scene.run();
		} catch (Throwable error) {
			SaberCraft.LOGGER.error("MEDIA scene {} failed", name, error);
		} finally {
			try {
				this.server.runCommand("tick unfreeze");

				if (this.context.computeOnClient(client -> client.player.isDeadOrDying())) {
					this.context.runOnClient(client -> client.player.respawn());
					this.context.waitTicks(40);
				}

				this.context.getInput().releaseKey(SaberAbilityClient.GUARD_KEY);
				this.context.setScreen(() -> null);
				this.hud(false);
				this.context.runOnClient(client -> {
					client.options.setCameraType(CameraType.FIRST_PERSON);
					ImmersionConfig.get().planet = "";
					OutfitClient.choose("");
				});

				if (this.on(s -> this.session(s) != null)) {
					this.context.runOnClient(client -> StoryClient.send(StoryActionPayload.LEAVE, "", 0));
					this.context.waitTicks(40);
				}

				this.server.runCommand("gamemode creative @a");
				this.server.runCommand("kill @e[type=!player]");
				this.server.runCommand("clear @a");
				this.server.runCommand("effect clear @a");
				this.server.runCommand("time set day");
				this.server.runCommand(String.format(Locale.ROOT, "execute in minecraft:overworld run tp @a %.2f %.2f %.2f 0 0", this.home.x, this.home.y, this.home.z));
				this.context.waitTicks(20);
			} catch (Throwable error) {
				SaberCraft.LOGGER.error("MEDIA after scene {}", name, error);
			}
		}
	}

	// --- The sandbox -------------------------------------------------------------------------------------------

	/** Six colors on six hilts, on a dark platform under the space sky. */
	private void blades() {
		this.clearing(8);
		String[][] sabers = {{"dark_lord", "red"}, {"kenobi", "blue"}, {"skywalker", "green"}, {"guardian", "purple"}, {"sentinel", "yellow"}, {"anakin", "white"}};

		for (int i = 0; i < sabers.length; i++) {
			float x = -3.1F + i * 1.24F;
			this.server.runCommand("execute as @p at @s run summon minecraft:armor_stand ~" + x + " ~ ~4.6 {Rotation:[180f,0f],ShowArms:1b,NoBasePlate:1b,Invisible:1b,"
					+ "Pose:{RightArm:[-80f,0f,0f]},equipment:{mainhand:" + saberItem(sabers[i][0], sabers[i][1]) + "}}");
		}

		this.context.getInput().lookAt(0.0F, 4.0F);
		this.hud(true);
		this.server.runCommand("time set 18000");
		this.context.waitTicks(40);
		this.shot("blades_night");
		this.server.runCommand("time set 12900");
		this.context.waitTicks(20);
		this.shot("blades_dusk");
		this.context.runOnClient(client -> ImmersionConfig.get().planet = "naboo");
		this.waitSky();
		this.context.waitTicks(20);
		this.shot("blades_naboo");
	}

	/** A character with their saber up, against the evening sky of a planet. */
	private void hero() {
		this.clearing(6);
		this.pose(Outfit.VADER, "dark_lord", "red", "mustafar", 12600, -90.0F, "hero_vader");
		this.pose(Outfit.OBI_WAN, "kenobi", "blue", "tatooine", 12300, -90.0F, "hero_kenobi");
		this.pose(Outfit.AHSOKA, "tano", "white", "naboo", 23500, 90.0F, "hero_ahsoka");
	}

	private void pose(Outfit outfit, String hilt, String color, String planet, int time, float yaw, String name) {
		this.context.runOnClient(client -> {
			OutfitClient.choose(outfit.id().toString());
			ImmersionConfig.get().planet = planet;
		});
		this.server.runCommand("item replace entity @a weapon.mainhand with " + saberCommand(hilt, color));
		this.context.runOnClient(client -> client.player.getInventory().setSelectedSlot(0));
		this.server.runCommand("time set " + time);
		this.context.getInput().lookAt(yaw, -6.0F);
		this.context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
		this.hud(true);
		this.waitSky();
		this.context.waitTicks(40);
		this.shot(name);
		this.context.getInput().holdKey(SaberAbilityClient.GUARD_KEY);
		this.context.waitTicks(12);
		this.shot(name + "_guard");
		this.context.getInput().releaseKey(SaberAbilityClient.GUARD_KEY);
		this.context.waitTicks(10);
	}

	/** A swing and the spin attack with their trails, in front of Endor's forest and its gas giant. */
	private void combat() {
		this.clearing(8);
		this.context.runOnClient(client -> {
			OutfitClient.choose(Outfit.LUKE.id().toString());
			ImmersionConfig.get().planet = "endor";
		});
		this.server.runCommand("gamemode survival @a");
		this.server.runCommand("item replace entity @a weapon.mainhand with " + saberCommand("skywalker", "green"));
		this.context.runOnClient(client -> client.player.getInventory().setSelectedSlot(0));
		this.server.runCommand("time set 12700");
		this.context.getInput().lookAt(-90.0F, -4.0F);
		this.context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
		this.hud(true);
		this.waitSky();
		this.context.waitTicks(60);

		for (int i = 0; i < 3; i++) {
			this.context.getInput().pressKey(options -> options.keyAttack);
			this.context.waitTicks(2 + i);
			this.shot("combat_swing_" + i);
			this.context.waitTicks(20);
		}

		for (int round = 0; round < 2; round++) {
			this.context.getInput().pressKey(SaberAbilityClient.SPIN_KEY);
			this.context.waitTick();

			for (int i = 0; i < 4; i++) {
				this.shot("combat_spin_" + round + "_" + i);
			}

			this.context.waitTicks(90);
			this.context.getInput().lookAt(-60.0F, -4.0F);
		}
	}

	/** The SaberCraft menu (I) on its main tabs. */
	private void menus() {
		this.server.runCommand("item replace entity @a weapon.mainhand with " + saberCommand("kenobi", "blue"));
		this.context.runOnClient(client -> client.player.getInventory().setSelectedSlot(0));
		this.context.waitTicks(20);

		for (SaberMenuScreen.Tab tab : List.of(SaberMenuScreen.Tab.STORY, SaberMenuScreen.Tab.SABER, SaberMenuScreen.Tab.CHARACTER, SaberMenuScreen.Tab.SHIPS,
				SaberMenuScreen.Tab.DROIDS, SaberMenuScreen.Tab.BLASTER)) {
			this.context.runOnClient(client -> SaberMenuScreen.open(client, tab));
			this.context.getInput().setCursorPos(2.0, 2.0);
			this.context.waitTicks(20);
			this.shot("menu_" + tab.name().toLowerCase(Locale.ROOT));
			this.context.setScreen(() -> null);
			this.context.waitTicks(5);
		}
	}

	/** A blaster in the hand, bolts on their way, in Tatooine's evening light. */
	private void blaster() {
		this.clearing(14);
		this.context.runOnClient(client -> {
			OutfitClient.choose(Outfit.LUKE.id().toString());
			ImmersionConfig.get().planet = "tatooine";
		});
		this.server.runCommand("gamemode survival @a");
		this.server.runCommand("item replace entity @a weapon.mainhand with minecraft:bow");
		this.server.runCommand("give @a minecraft:arrow 64");
		this.context.runOnClient(client -> client.player.getInventory().setSelectedSlot(0));
		this.server.runCommand("execute as @p run sabercraft blaster dl44");
		this.server.runCommand("execute as @p at @s run summon minecraft:armor_stand ~-2 ~ ~14 {NoBasePlate:1b,ShowArms:1b}");
		this.server.runCommand("execute as @p at @s run summon minecraft:armor_stand ~2 ~ ~16 {NoBasePlate:1b,ShowArms:1b}");
		this.server.runCommand("time set 12300");
		this.context.getInput().lookAt(0.0F, 0.0F);
		this.context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
		this.hud(true);
		this.waitSky();
		this.context.waitTicks(50);
		this.shot("blaster_third_hold");

		for (int i = 0; i < 4; i++) {
			this.context.getInput().holdKeyFor(options -> options.keyUse, 2);

			for (int t = 0; t < 3; t++) {
				this.shot("blaster_third_" + i + "_" + t);
			}

			this.context.waitTicks(25);
		}

		this.context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
		this.context.getInput().lookAt(180.0F, 0.0F);
		this.context.waitTicks(20);

		for (int i = 0; i < 2; i++) {
			this.context.getInput().holdKeyFor(options -> options.keyUse, 2);
			this.shot("blaster_front_" + i);
			this.context.waitTicks(25);
		}
	}

	/** Droids around the player (the limit raised for the picture). */
	private void droids() {
		this.clearing(10);
		this.server.runCommand("gamemode survival @a");
		int limit = this.on(s -> AbilityConfig.get().droidLimit);
		this.server.runOnServer(s -> AbilityConfig.get().droidLimit = 8);

		try {
			for (DroidType type : DroidType.values()) {
				this.server.runOnServer(s -> Droids.command(this.me(s), "spawn", type.id()));
				this.context.waitTicks(10);
			}

			this.context.runOnClient(client -> ImmersionConfig.get().planet = "naboo");
			this.server.runCommand("time set 3000");
			this.context.waitTicks(60);
			Vec3 at = this.on(s -> this.me(s).position());
			// All eight in a group facing the camera: the tall ones behind, the small ones in front.
			Object[][] spots = {{DroidType.PROBE, -2.6, -1.6, 20.0}, {DroidType.K2SO, -1.2, -1.3, 10.0}, {DroidType.C3PO, 0.5, -1.0, -5.0},
					{DroidType.B1, 2.1, -1.4, -20.0}, {DroidType.CHOPPER, -1.9, 0.4, 25.0}, {DroidType.R2D2, -0.4, 0.7, 5.0},
					{DroidType.BB8, 1.1, 0.9, -15.0}, {DroidType.MOUSE, 2.3, 1.3, -35.0}};
			this.server.runOnServer(s -> {
				for (Object[] spot : spots) {
					Droids.place(this.me(s), (DroidType) spot[0], at.add((double) spot[1], 0.0, (double) spot[2]), (float) (double) spot[3], true);
				}
			});
			this.context.waitTicks(30);
			this.server.runCommand("tick freeze");
			this.server.runCommand("gamemode spectator @a");
			this.hud(true);
			this.waitSky();
			this.view(at.add(0.0, 1.5, 4.4), at.add(0.0, 0.8, -0.3));
			this.context.waitTicks(20);
			this.shot("droids_front");
			this.view(at.add(1.6, 1.0, 3.6), at.add(-0.2, 0.7, -0.3));
			this.context.waitTicks(20);
			this.shot("droids_low");
			this.view(at.add(-3.0, 2.4, 3.6), at.add(0.0, 0.5, -0.3));
			this.context.waitTicks(20);
			this.shot("droids_high");
			this.server.runCommand("tick unfreeze");
			this.server.runCommand("gamemode survival @a");

			for (DroidType type : DroidType.values()) {
				this.server.runOnServer(s -> Droids.command(this.me(s), "dismiss", type.id()));
				this.context.waitTicks(3);
			}
		} finally {
			this.server.runOnServer(s -> AbilityConfig.get().droidLimit = limit);
		}
	}

	/**
	 * All five ships at once: the X-Wing and both TIEs on the ground, the Falcon behind them, the
	 * Star Destroyer high above. Only one ship of a player is live, so the world stands still for
	 * the picture (a ship that is not its owner's own goes away with its next tick).
	 */
	private void fleet() {
		this.clearing(28);
		Vec3 at = this.on(s -> this.me(s).position());
		this.server.runCommand("tick freeze");
		this.server.runOnServer(s -> {
			ServerLevel level = this.me(s).level();
			this.park(level, ShipType.XWING, at.add(0.0, 0.0, 14.0), 160.0F, 0.0);
			this.park(level, ShipType.TIE_FIGHTER, at.add(-9.0, 0.0, 17.0), 200.0F, 0.0);
			this.park(level, ShipType.TIE_INTERCEPTOR, at.add(9.0, 0.0, 18.0), 150.0F, 0.0);
			this.park(level, ShipType.FALCON, at.add(-2.0, 0.0, 34.0), 135.0F, 0.0);
			this.park(level, ShipType.STAR_DESTROYER, at.add(30.0, 0.0, 120.0), 220.0F, 55.0);
		});
		this.server.runCommand("gamemode spectator @a");
		this.hud(true);

		for (int time : new int[] {1500, 12400}) {
			this.server.runCommand("time set " + time);
			this.view(at.add(-14.0, 6.0, -6.0), at.add(2.0, 3.0, 22.0));
			this.context.waitTicks(60);
			this.shot("fleet_" + time);
			this.view(at.add(10.0, 2.2, 4.0), at.add(-3.0, 2.5, 20.0));
			this.context.waitTicks(40);
			this.shot("fleet_low_" + time);
		}

		this.view(at.add(-8.0, 1.8, 6.0), at.add(0.0, 2.0, 14.0));
		this.context.waitTicks(30);
		this.shot("fleet_xwing_close");
		this.view(at.add(4.0, 3.0, 26.0), at.add(-2.0, 2.0, 34.0));
		this.context.waitTicks(30);
		this.shot("fleet_falcon_close");
		this.view(at.add(-30.0, 8.0, 60.0), at.add(30.0, 60.0, 120.0));
		this.context.waitTicks(40);
		this.shot("fleet_destroyer");
		this.server.runCommand("tick unfreeze");
	}

	/** A ship standing (or hanging) there on the ground under it, not a player's own (see fleet). */
	private void park(ServerLevel level, ShipType type, Vec3 spot, float yaw, double above) {
		int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(spot.x), (int) Math.floor(spot.z));
		ShipEntity ship = new ShipEntity(level, type, this.me(level.getServer()));
		ship.snapTo(spot.x, ground + above, spot.z, yaw, 0.0F);
		ship.setYHeadRot(yaw);
		ship.setYBodyRot(yaw);
		level.addFreshEntity(ship);
	}

	/** Flying, seen from the chase camera: each fighter and the Falcon over another planet. */
	private void flight() {
		this.clearing(6);
		this.server.runCommand("gamemode survival @a");
		String camera = this.context.computeOnClient(client -> ImmersionConfig.get().shipCamera);
		this.context.runOnClient(client -> ImmersionConfig.get().shipCamera = ImmersionConfig.CAMERA_CHASE);

		try {
			this.fly(ShipType.XWING, "endor", 12500, "flight_xwing", true);
			this.fly(ShipType.TIE_INTERCEPTOR, "mustafar", 12500, "flight_tie_interceptor", false);
			this.fly(ShipType.TIE_FIGHTER, "coruscant", 12300, "flight_tie_fighter", false);
			this.fly(ShipType.FALCON, "tatooine", 12300, "flight_falcon", false);
		} finally {
			this.context.runOnClient(client -> ImmersionConfig.get().shipCamera = camera);
		}
	}

	private void fly(ShipType type, String planet, int time, String name, boolean cockpit) {
		this.context.runOnClient(client -> ImmersionConfig.get().planet = planet);
		this.server.runCommand("time set " + time);
		this.server.runOnServer(s -> Starships.resetCooldowns());
		this.context.waitTicks(20);
		this.server.runOnServer(s -> Starships.command(this.me(s), "board", type.id()));

		for (int i = 0; i < 80 && this.context.computeOnClient(client -> ShipClient.piloting(client) == null); i++) {
			this.context.waitTick();
		}

		this.context.waitTicks(5);
		this.context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
		this.hud(true);
		this.context.getInput().lookAt(30.0F, 0.0F);
		this.context.getInput().holdKey(options -> options.keyUp);
		this.context.getInput().holdKeyFor(options -> options.keyJump, 40);
		this.context.waitTicks(30);
		this.waitSky();
		this.shot(name);
		this.context.getInput().holdKey(options -> options.keyAttack);
		this.context.waitTicks(3);
		this.shot(name + "_fire");
		this.context.waitTicks(3);
		this.shot(name + "_fire2");
		this.context.getInput().releaseKey(options -> options.keyAttack);
		this.context.getInput().holdKeyFor(options -> options.keyLeft, 10);
		this.context.waitTicks(4);
		this.shot(name + "_turn");

		if (cockpit) {
			this.context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
			this.server.runCommand("gamemode creative @a");
			this.hud(false);
			this.context.waitTicks(10);
			this.shot(name + "_cockpit");
			this.hud(true);
			this.server.runCommand("gamemode survival @a");
		}

		this.context.getInput().releaseKey(options -> options.keyUp);
		this.context.waitTicks(5);
		this.server.runCommand("gamemode creative @a");
		this.server.runOnServer(s -> Starships.command(this.me(s), "dismiss", type.id()));
		this.context.waitTicks(30);
		this.server.runCommand(String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f 0 0", this.home.x, this.home.y, this.home.z));
		this.context.waitTicks(30);
	}

	/** The Star Destroyer: flown a moment, then seen from far away and from below. */
	private void destroyer() {
		this.server.runCommand("gamemode survival @a");
		this.context.runOnClient(client -> ImmersionConfig.get().planet = "hoth");
		this.server.runCommand("time set 12300");
		this.server.runOnServer(s -> Starships.resetCooldowns());
		this.context.waitTicks(10);
		this.server.runOnServer(s -> Starships.command(this.me(s), "board", "star_destroyer"));

		for (int i = 0; i < 80 && this.context.computeOnClient(client -> ShipClient.piloting(client) == null); i++) {
			this.context.waitTick();
		}

		this.context.waitTicks(80);
		this.context.getInput().holdKey(options -> options.keyUp);
		this.context.waitTicks(40);
		this.shot("destroyer_flight");
		this.context.getInput().releaseKey(options -> options.keyUp);
		Vec3 at = this.on(s -> {
			ShipEntity ship = Starships.ship(this.me(s));
			return ship == null ? this.me(s).position() : ship.position();
		});
		this.context.getInput().holdKeyFor(options -> options.keyShift, 3);
		this.context.waitTicks(10);
		this.server.runCommand("gamemode spectator @a");
		this.hud(true);
		this.waitSky();
		this.view(at.add(-80.0, 10.0, 10.0), at.add(0.0, 4.0, 0.0));
		this.context.waitTicks(60);
		this.shot("destroyer_side");
		this.view(at.add(-45.0, 6.0, -40.0), at.add(0.0, 6.0, 0.0));
		this.context.waitTicks(40);
		this.shot("destroyer_quarter");
		this.view(at.add(-35.0, -14.0, 55.0), at.add(0.0, 2.0, 0.0));
		this.context.waitTicks(40);
		this.shot("destroyer_below");
		this.view(at.add(30.0, 25.0, -70.0), at.add(0.0, 0.0, 0.0));
		this.context.waitTicks(40);
		this.shot("destroyer_above");
		this.server.runCommand("gamemode creative @a");
		this.server.runOnServer(s -> Starships.command(this.me(s), "dismiss", "star_destroyer"));
		this.context.waitTicks(40);
	}

	/** Inside the Falcon and the Star Destroyer, walked as in the game. */
	private void interiors() {
		BlockPos falcon = this.on(s -> ShipInteriors.plotForTest(s, this.self, "falcon"));
		BlockPos destroyer = this.on(s -> ShipInteriors.plotForTest(s, this.self, "star_destroyer"));
		this.hud(true);
		this.inside(falcon, new Vec3(18.5, 7, 33.0), -150, 15, "interior_falcon_lounge");
		this.inside(falcon, new Vec3(44.0, 7, 28.3), 180, 8, "interior_falcon_cockpit");
		this.inside(falcon, new Vec3(43.5, 7, 22.6), 160, 15, "interior_falcon_cockpit_seats");
		this.inside(falcon, new Vec3(13.5, 7, 23.5), 110, 5, "interior_falcon_corridor");
		this.inside(falcon, new Vec3(6.5, 7, 36.5), -60, 5, "interior_falcon_galley");
		this.inside(destroyer, new Vec3(40.5, 151, 84.5), 90, 35, "interior_destroyer_bridge_pit");
		this.inside(destroyer, new Vec3(40.5, 151, 96.0), 180, 5, "interior_destroyer_bridge");
		this.inside(destroyer, new Vec3(48.5, 20, 9.5), 0, 0, "interior_destroyer_gallery");
		this.inside(destroyer, new Vec3(33.0, 20, 17.0), -120, 20, "interior_destroyer_reactor");
		this.inside(destroyer, new Vec3(DestroyerInterior.CX + 0.6, H, -0.5), -90, 8, "interior_destroyer_hangar");
	}

	private void inside(@Nullable BlockPos origin, Vec3 local, float yaw, float pitch, String name) {
		if (origin == null) {
			return;
		}

		this.server.runCommand(String.format(Locale.ROOT, "execute in sabercraft:ship_interiors run tp @a %.2f %.2f %.2f %.1f %.1f", origin.getX() + local.x,
				origin.getY() + local.y, origin.getZ() + local.z, yaw, pitch));
		this.context.waitTicks(50);
		this.shot(name);
	}

	/** Every planet's sky over the same land: by day, at sunset, and Tatooine's two suns. */
	private void planets() {
		this.hud(true);

		for (String planet : List.of("tatooine", "hoth", "endor", "mustafar", "naboo", "coruscant")) {
			this.context.runOnClient(client -> ImmersionConfig.get().planet = planet);
			this.server.runCommand("time set 12600");
			this.context.getInput().lookAt(90.0F, -10.0F);
			this.waitSky();
			this.context.waitTicks(30);
			this.shot("planet_" + planet + "_evening");
			this.server.runCommand("time set 6000");
			this.context.getInput().lookAt(150.0F, -14.0F);
			this.context.waitTicks(30);
			this.shot("planet_" + planet + "_day");
		}

		this.context.runOnClient(client -> ImmersionConfig.get().planet = "");
		this.server.runCommand("time set 18000");
		this.context.getInput().lookAt(30.0F, -35.0F);
		this.context.waitTicks(40);
		this.shot("space_sky_night");
	}

	// --- The story -----------------------------------------------------------------------------------------------

	private void story() {
		this.server.runCommand("gamemode survival @a");
		this.reactor();
		this.disguise();
		this.lab();
		this.finale();
		this.duel();
		this.escape();
	}

	/** Mira's briefing aboard the Falcon: a dialog that names the player. */
	private void briefing() {
		this.server.runCommand("gamemode survival @a");
		this.enter(0, "m1.briefing");
		this.unseen();
		this.hud(false);
		this.context.waitTicks(160);
		this.server.runOnServer(s -> this.session(s).talk("m1_briefing", null));
		StoryDialogPayload dialog = null;

		for (int i = 0; i < 40 && dialog == null; i++) {
			this.context.waitTick();
			StoryDialogPayload now = this.context.computeOnClient(client -> StoryClient.dialogForTest());
			dialog = now != null && now.open() ? now : null;
		}

		// The line types itself out: the whole of it.
		this.context.waitTicks(110);
		this.shot("story_briefing");
		this.converse();
		this.leave();
	}

	private void reactor() {
		this.enter(3, "m4.download");
		this.unseen();
		this.hud(true);
		this.put(new Vec3(48.0, R, 24.5), 90.0F, 10.0F);
		this.context.waitTicks(30);
		this.shot("story_reactor");
		this.put(new Vec3(40.5, R, 33.0), 180.0F, -20.0F);
		this.context.waitTicks(30);
		this.shot("story_reactor_up");
		this.leave();
	}

	/** In the troopers' own armour, among them. */
	private void disguise() {
		this.enter(3, "m4.brief");
		this.unseen();
		this.server.runOnServer(s -> StoryTesting.putOnArmour(this.session(s)));
		this.hud(true);
		this.context.waitTicks(20);
		this.put(new Vec3(DestroyerInterior.CX + 2.6, H, -0.5), 90.0F, 6.0F);
		this.context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
		this.context.waitTicks(30);
		this.shot("story_disguise");
		this.context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
		this.context.waitTicks(20);
		this.shot("story_disguise_back");
		this.put(new Vec3(36.5, H, -4.0), 180.0F, 4.0F);
		this.context.waitTicks(30);
		this.shot("story_disguise_hangar");
		this.put(new Vec3(36.5, H, -4.0), 0.0F, 4.0F);
		this.context.waitTicks(30);
		this.shot("story_disguise_hangar2");
		this.context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
		this.leave();
	}

	/** The lab under the reactor: the saber in its field. */
	private void lab() {
		this.enter(5, "m6.saber");
		this.unseen();
		this.hud(true);
		this.put(new Vec3(44.5, R, 47.5), 0.0F, 22.0F);
		this.context.waitTicks(40);
		this.shot("story_lab_near");
		this.put(new Vec3(44.5, R, 45.5), 0.0F, 12.0F);
		this.context.waitTicks(40);
		this.shot("story_lab");
		this.put(new Vec3(47.5, R, 43.0), 30.0F, 25.0F);
		this.context.waitTicks(30);
		this.shot("story_lab_side");
		this.leave();
	}

	/** The way to the hangar: troopers fire, the saber's guard turns their bolts. */
	private void finale() {
		this.enter(5, "m6.escape2");
		this.flags("saber.taken");
		this.server.runCommand("effect give @a minecraft:resistance 100000 4 true");
		this.server.runCommand("effect give @a minecraft:regeneration 100000 4 true");
		this.server.runCommand("item replace entity @a weapon.mainhand with " + saberCommand("skywalker", "blue"));
		this.put(new Vec3(36.5, H, -4.0), 180.0F, 2.0F);
		this.context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
		this.hud(true);
		this.context.waitTicks(10);
		this.context.getInput().holdKey(SaberAbilityClient.GUARD_KEY);

		for (int i = 0; i < 8; i++) {
			this.context.waitTicks(8);
			this.shot("story_finale_" + i);
		}

		this.context.getInput().releaseKey(SaberAbilityClient.GUARD_KEY);
		this.leave();
	}

	/** The duel against the Shadowblade, and her spinning ring saber at half her strength. */
	private void duel() {
		this.enter(5, "m6.duel");
		this.flags("saber.taken", "droid.m31", "droid.r2v7");
		this.server.runOnServer(s -> Story.updateDroids(this.session(s)));
		this.server.runCommand("effect give @a minecraft:resistance 100000 4 true");
		this.server.runCommand("effect give @a minecraft:regeneration 100000 4 true");
		this.server.runCommand("item replace entity @a weapon.mainhand with " + saberCommand("skywalker", "blue"));
		this.put(new Vec3(40.5, H, -21.0), 180.0F, 0.0F);
		String film = this.film(200);

		// Her film once (a frame from its middle), then the fight.
		if (film != null) {
			this.context.waitTicks(60);
			this.shot("story_duel_film");
		}

		this.skipFilms(300);
		this.converse();
		this.context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
		this.hud(true);

		for (int i = 0; i < 10; i++) {
			this.context.waitTicks(12);
			this.faceHer();
			this.shot("story_duel_" + i);
		}

		this.hud(false);
		this.server.runOnServer(s -> StoryTesting.halfStrength(this.session(s), this.session(s).figure("shadow"), this.me(s)));
		String mid = this.film(40);

		if (mid != null) {
			this.context.waitTicks(40);
			this.shot("story_duel_half");
			this.skip(mid);
		}

		this.converse();
		this.hud(true);
		this.server.runOnServer(s -> StoryTesting.spinNow(this.session(s), this.session(s).figure("shadow")));

		for (int i = 0; i < 10; i++) {
			this.context.waitTicks(3);
			this.faceHer();
			this.shot("story_duel_ring_" + i);
		}

		this.leave();
	}

	private void faceHer() {
		this.server.runOnServer(s -> {
			StorySession session = this.session(s);

			if (session == null || session.figure("shadow") == null) {
				return;
			}

			Vec3 her = session.figure("shadow").position().add(Vec3.atLowerCornerOf(session.destroyer)).add(0.0, 1.2, 0.0);
			this.me(s).lookAt(EntityAnchorArgument.Anchor.EYES, her);
		});
	}

	/** The escape, seen from outside: the Tenebris breaking apart behind the Falcon. */
	private void escape() {
		this.enter(5, "m6.jump");
		this.unseen();
		this.hud(false);
		this.server.runOnServer(s -> this.session(s).set("tmp.jump_go"));
		String film = this.film(60);

		if (!"escape".equals(film)) {
			SaberCraft.LOGGER.info("MEDIA: the escape film does not begin ({})", film);
			this.leave();
			return;
		}

		for (int age : new int[] {40, 90, 150, 200, 240, 270, 300, 340, 364}) {
			for (int i = 0; i < 200 && this.context.computeOnClient(client -> StoryCinema.active() && StoryCinema.ageForTest() < age); i++) {
				this.context.waitTick();
			}

			this.shot("story_escape_" + age);
		}

		this.leave();
	}

	// --- Helpers -------------------------------------------------------------------------------------------------

	/** A clear, level place round the player (so many blocks each way): grass under, air over. */
	private void clearing(int size) {
		this.server.runCommand(String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f 0 0", this.home.x, this.home.y, this.home.z));
		this.context.waitTicks(5);
		this.server.runCommand("execute as @p at @s run fill ~-" + size + " ~ ~-" + size + " ~" + size + " ~12 ~" + (size * 2) + " minecraft:air");
		this.server.runCommand("execute as @p at @s run fill ~-" + size + " ~-1 ~-" + size + " ~" + size + " ~-1 ~" + (size * 2) + " minecraft:grass_block");
		this.context.waitTicks(20);
	}

	/** The camera (a spectator) at that place, looking at that point (world space). */
	private void view(Vec3 from, Vec3 to) {
		Vec3 d = to.subtract(from);
		float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
		float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z)));
		this.server.runCommand(String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f %.1f %.1f", from.x, from.y, from.z, yaw, pitch));
		this.context.waitTicks(3);
	}

	private void waitSky() {
		for (int i = 0; i < 240 && !this.context.computeOnClient(client -> PlanetSky.ready()); i++) {
			this.context.waitTicks(5);
		}
	}

	private void hud(boolean hidden) {
		this.context.runOnClient(client -> {
			if (client.gui.hud.isHidden() != hidden) {
				client.gui.hud.toggle();
			}
		});
	}

	private void shot(String name) {
		this.context.runOnClient(client -> {
			client.gui.hud.getChat().clearMessages(false);
			client.gui.toastManager().clear();
		});
		this.context.takeScreenshot(TestScreenshotOptions.of(name).withDestinationDir(this.dir).disableCounterPrefix());
		this.taken++;
		SaberCraft.LOGGER.info("MEDIA shot {}", name);
	}

	private static String saberItem(String hilt, String color) {
		return "{id:\"minecraft:netherite_sword\",count:1,components:{\"minecraft:custom_model_data\":{strings:[\"sabercraft:" + hilt + "\",\"sabercraft:" + color + "\"]}}}";
	}

	private static String saberCommand(String hilt, String color) {
		return "minecraft:netherite_sword[minecraft:custom_model_data={strings:[\"sabercraft:" + hilt + "\",\"sabercraft:" + color + "\"]}]";
	}

	private void enter(int mission, String step) {
		if (this.on(s -> this.session(s) != null)) {
			this.leave();
		}

		this.server.runOnServer(s -> StoryTesting.fileAt(s, this.self, mission, step));
		this.context.runOnClient(client -> StoryClient.send(StoryActionPayload.CONTINUE, "", 0));

		for (int i = 0; i < 160; i++) {
			this.context.waitTick();

			if (step.equals(this.on(s -> this.session(s) == null ? "" : StoryTesting.step(this.session(s))))) {
				this.context.waitTicks(20);
				return;
			}
		}

		throw new AssertionError("the story does not continue at " + step);
	}

	private void leave() {
		this.context.runOnClient(client -> StoryClient.send(StoryActionPayload.LEAVE, "", 0));
		this.context.waitTicks(40);
	}

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

	private void put(Vec3 local, float yaw, float pitch) {
		BlockPos origin = this.on(s -> this.session(s).destroyer);
		this.server.runCommand(String.format(Locale.ROOT, "execute in sabercraft:ship_interiors run tp @a %.3f %.3f %.3f %.1f %.1f", origin.getX() + local.x,
				origin.getY() + local.y, origin.getZ() + local.z, yaw, pitch));
		this.context.waitTicks(8);
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
	}

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

	private void skip(String film) {
		this.context.runOnClient(client -> StoryClient.send(StoryActionPayload.SKIP, film, 0));
		this.context.runOnClient(client -> StoryCinema.skipForTest());
		this.context.waitTicks(5);
	}

	private void skipFilms(int ticks) {
		for (int i = 0; i < ticks / 5; i++) {
			String film = this.context.computeOnClient(client -> StoryCinema.filmForTest());

			if (film != null) {
				this.skip(film);
			} else if (this.on(s -> this.session(s) != null && this.session(s).has("tmp.duel"))) {
				return;
			} else if (this.on(s -> this.session(s) != null && this.session(s).dialog != null)) {
				this.server.runOnServer(s -> StoryTesting.answer(this.session(s), 0));
			}

			this.context.waitTicks(5);
		}
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
