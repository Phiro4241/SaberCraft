package com.phiro.sabercraft.gametest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.ability.AbilityConfig;
import com.phiro.sabercraft.blaster.ImpactSounds;
import com.phiro.sabercraft.character.Outfit;
import com.phiro.sabercraft.client.ImmersionConfig;
import com.phiro.sabercraft.client.character.OutfitClient;
import com.phiro.sabercraft.client.combat.CombatConfig;
import com.phiro.sabercraft.client.combat.CombatLibrary;
import com.phiro.sabercraft.client.combat.CombatMove;
import com.phiro.sabercraft.client.combat.CombatState;
import com.phiro.sabercraft.client.combat.CombatStyle;
import com.phiro.sabercraft.client.menu.SaberMenuScreen;
import com.phiro.sabercraft.client.ship.ShipClient;
import com.phiro.sabercraft.config.JsonConfig;
import com.phiro.sabercraft.droid.DroidBalance;
import com.phiro.sabercraft.droid.DroidType;
import com.phiro.sabercraft.droid.Droids;
import com.phiro.sabercraft.network.ShipEnergyPayload;
import com.phiro.sabercraft.ship.ShipEntity;
import com.phiro.sabercraft.ship.ShipType;
import com.phiro.sabercraft.ship.Starships;

/**
 * Phase 13: ships, sounds, attack variety, skins, armor, droids and flight energy.
 *
 * <p>Ships: the pilot is not drawn while flying and is back right after climbing out, after a
 * wreck and after dying in the cockpit. Landings never hurt the pilot; a gentle one costs no hull,
 * a hard one some, a very hard one wrecks the ship and the pilot falls out gently. Flight energy
 * runs down, the engines cut out and the ship glides down without harm, the HUD knows it, and on
 * the ground it charges until the engines start again. The Millennium Falcon flies, the Star
 * Destroyer keeps its height above the ground and never dips its nose. Impact sounds of many bolts
 * at one spot are thinned out.
 *
 * <p>Moves: crits play the signature move only now and then, never twice in a row, and otherwise
 * several other moves; no move repeats right after itself. Skins: Vader and the other cloaked
 * characters wear a real cape; a character's skin shows over armor, which still protects.
 * Droids: the limit is 2 by default (and old files with 4 are moved once), each droid has its own
 * numbers, combat droids share a short cooldown and hit softer together.
 *
 * <p>Screenshots: {@code phase13_*}.
 */
public class Phase13GameTest implements FabricClientGameTest {
	private static final AABB AREA = new AABB(-400, -64, -400, 400, 400, 400);
	private static final String SABER = "minecraft:netherite_sword[minecraft:custom_model_data={strings:[\"sabercraft:standard\",\"sabercraft:red\"]}]";

	@Override
	public void runTest(ClientGameTestContext context) {
		context.runOnClient(client -> client.options.renderDistance().set(6));
		moves();
		configMigration();

		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
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
			UUID self = context.computeOnClient(client -> client.player.getUUID());

			falcon(context, server, self);
			energy(context, server, self);
			impacts(context, server, self);
			deathInCockpit(context, server, self);
			starDestroyer(context, server, self);
			impactLimiter(server);
			capeAndArmor(context, server, self);
			droids(context, server, self);
		}

		dedicatedServer(context);
	}

	// Moves (client data only, no world needed).

	/** Signature moves are rare and never back to back; no move follows itself. */
	private static void moves() {
		CombatConfig config = new CombatConfig();
		CombatStyle ezra = CombatLibrary.INSTANCE.styles().get(SaberCraft.id("improvised"));
		check(ezra != null && ezra.jump() != null, "Ezra's improvised style is loaded");
		check(ezra.critMoves().size() >= 2, "Ezra has several crit moves besides the turn (" + ezra.critMoves().size() + ")");

		int signatures = 0;
		int total = 0;

		for (int entity = 1; entity <= 20; entity++) {
			List<CombatMove> crits = CombatState.simulateForTest(ezra, true, 30, entity, config);

			for (int i = 0; i < crits.size(); i++) {
				total++;

				if (crits.get(i) == ezra.jump()) {
					signatures++;
				}

				if (i > 0 && crits.get(i) == crits.get(i - 1)) {
					throw new AssertionError("a crit move twice in a row: " + crits.get(i).id());
				}
			}
		}

		check(true, "no crit move twice in a row in " + total + " crits");
		float share = signatures / (float) total;
		check(share > 0.05F && share < 0.4F, "Ezra's 180° turn comes on some crits, not most (" + Math.round(share * 100) + " %)");

		for (CombatStyle style : CombatLibrary.INSTANCE.styles().values()) {
			List<CombatMove> normal = CombatState.simulateForTest(style, false, 40, 7, config);
			java.util.Set<CombatMove> seen = new java.util.HashSet<>(normal);

			for (int i = 1; i < normal.size(); i++) {
				if (normal.get(i) == normal.get(i - 1) && style.combo().size() > 1) {
					throw new AssertionError(style.id() + ": a normal swing twice in a row");
				}
			}

			check(seen.size() >= Math.min(3, style.combo().size()), style.id() + ": several different normal moves (" + seen.size() + ")");

			if (style.jump() != null) {
				List<CombatMove> crits = CombatState.simulateForTest(style, true, 40, 11, config);
				check(crits.stream().distinct().count() >= 2 || style.critMoves().isEmpty() && style.combo().size() < 2,
						style.id() + ": crits are not always the same move");
			}
		}

		config.signatureMoves = 0.0F;
		check(CombatState.simulateForTest(ezra, true, 60, 3, config).stream().noneMatch(move -> move == ezra.jump()),
				"signature moves at 0: the turn never comes");
		config.signatureMoves = 1.0F;
		config.moveVariety = false;
		check(CombatState.simulateForTest(ezra, true, 10, 3, config).stream().allMatch(move -> move == ezra.jump()),
				"variety off: every crit is the turn, as before Phase 13");
	}

	/** An abilities.json from before Phase 13 that still holds the old droid limit 4 moves to 2, once. */
	private static void configMigration() {
		check(new AbilityConfig().droidLimit == 2, "the built-in droid limit is 2");
		Path path = JsonConfig.path("abilities");

		try {
			byte[] original = Files.exists(path) ? Files.readAllBytes(path) : null;

			try {
				Files.createDirectories(path.getParent());
				Files.writeString(path, "{\"droid_limit\": 4, \"droid_cooldown\": 1.5}");
				AbilityConfig.load();
				check(AbilityConfig.get().droidLimit == 2, "an old file with the old default 4 is moved to 2");
				check(AbilityConfig.get().droidCooldown == 1.5F, "other settings of the old file stay");
				check(AbilityConfig.get().configVersion == AbilityConfig.CONFIG_VERSION, "and the file now has the current version");
				check(Files.readString(path).contains("\"config_version\": " + AbilityConfig.CONFIG_VERSION), "which is written to the file");
				check(AbilityConfig.get().balance(DroidType.B1).cooldownSeconds == DroidBalance.of(DroidType.B1).cooldownSeconds,
						"droids missing in the file get their built-in numbers");

				// An admin who sets 4 again keeps it.
				Files.writeString(path, "{\"droid_limit\": 4, \"config_version\": " + AbilityConfig.CONFIG_VERSION
						+ ", \"droid_balance\": {\"b1\": {\"cooldown_seconds\": 30, \"range\": 8, \"damage\": 1, \"strength\": 2}}}");
				AbilityConfig.load();
				check(AbilityConfig.get().droidLimit == 4, "a limit of 4 chosen after Phase 13 stays 4");
				check(AbilityConfig.get().balance(DroidType.B1).cooldownSeconds == 30.0F && Droids.cooldown(DroidType.B1) == 600,
						"a droid's own cooldown from the file is used");
				check(AbilityConfig.get().balance(DroidType.R2D2).strength == DroidBalance.of(DroidType.R2D2).strength,
						"the droids not in the file keep their numbers");
			} finally {
				if (original != null) {
					Files.write(path, original);
				} else {
					Files.deleteIfExists(path);
				}

				AbilityConfig.load();
			}
		} catch (java.io.IOException error) {
			throw new java.io.UncheckedIOException(error);
		}
	}

	// Ships.

	/** The Falcon: from the hangar, flown with the real keys, its pilot never drawn. */
	private static void falcon(ClientGameTestContext context, TestServerContext server, UUID self) {
		context.waitFor(client -> ShipClient.status() != null, 40);
		context.runOnClient(client -> SaberMenuScreen.open(client, SaberMenuScreen.Tab.SHIPS));
		context.waitTicks(5);
		context.clickScreenButton(ShipType.FALCON.translationKey());
		context.waitTicks(3);
		context.getInput().setCursorPos(2.0, 2.0);
		context.waitTicks(20);
		context.takeScreenshot("phase13_hangar_falcon");
		context.setScreen(() -> null);

		float health = server.computeOnServer(s -> me(s, self).getHealth());
		server.computeOnServer(s -> Starships.command(me(s, self), "board", "falcon"));
		until(context, client -> ShipClient.piloting(client) != null, 80, "into the Falcon's cockpit");
		check(server.computeOnServer(s -> ship(s).ship()) == ShipType.FALCON, "it is the Falcon");
		check(server.computeOnServer(s -> ship(s).getMaxHealth()) == ShipType.FALCON.hull(), "with its own hull");

		// The pilot is not drawn, by any client (the check runs on the entity the others see too).
		check(context.computeOnClient(client -> ShipClient.hidesPilot(client.player)), "the pilot is hidden while flying");
		check(context.computeOnClient(client -> !client.getEntityRenderDispatcher().shouldRender(client.player,
				new net.minecraft.client.renderer.culling.Frustum(new org.joml.Matrix4f(), new org.joml.Matrix4f()), 0.0, 0.0, 0.0)),
				"the renderer skips the pilot");

		context.getInput().holdKeyFor(options -> options.keyJump, 40);
		context.getInput().holdKey(options -> options.keyUp);
		context.waitTicks(50);
		double top = server.computeOnServer(s -> ship(s).speed());
		check(top > 1.0, "the Falcon speeds up (" + top + ")");
		context.takeScreenshot("phase13_falcon_flight");
		context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
		context.waitTicks(3);
		context.takeScreenshot("phase13_falcon_cockpit_no_hands");
		context.getInput().releaseKey(options -> options.keyUp);
		check(context.computeOnClient(client -> ShipClient.engineVolume(ShipClient.piloting(client))) <= 0.6F, "the engine stays below its cap");
		check(server.computeOnServer(s -> me(s, self).getHealth()) == health, "flying costs the pilot no health");
		context.getInput().holdKeyFor(options -> options.keyDown, 60);
	}

	/** Energy runs out: the engines cut out, a gentle glide down without harm, then it charges. */
	private static void energy(ClientGameTestContext context, TestServerContext server, UUID self) {
		context.getInput().holdKeyFor(options -> options.keyJump, 40);
		double height = server.computeOnServer(s -> ship(s).getY() - s.overworld().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
				ship(s).getBlockX(), ship(s).getBlockZ()));
		check(height > 6.0, "the Falcon is up in the air (" + height + " blocks)");
		float hull = server.computeOnServer(s -> ship(s).getHealth());
		float health = server.computeOnServer(s -> me(s, self).getHealth());
		float before = server.computeOnServer(s -> ship(s).energy());
		context.getInput().holdKeyFor(options -> options.keyUp, 20);
		check(server.computeOnServer(s -> ship(s).energy()) < before, "flying uses energy");
		until(context, client -> {
			Entity ship = ShipClient.piloting(client);
			return ship != null && ShipClient.energy(ship) != null;
		}, 20, "the HUD gets the energy");
		context.takeScreenshot("phase13_hud_energy");

		server.runOnServer(s -> ship(s).setEnergyForTest(0.0F));
		context.waitTicks(3);
		check(server.computeOnServer(s -> ship(s).depleted()), "empty: the engines cut out");
		until(context, client -> ShipClient.energy(ShipClient.piloting(client)).state() == ShipEnergyPayload.EMPTY, 20,
				"the HUD shows the empty energy");
		context.takeScreenshot("phase13_hud_energy_empty");

		for (int i = 0; i < 400 && !server.computeOnServer(s -> ship(s).onGround()); i++) {
			context.waitTick();
		}

		check(server.computeOnServer(s -> ship(s).onGround()), "the empty ship glides down to the ground");
		check(server.computeOnServer(s -> ship(s).getHealth()) == hull, "the glide down costs no hull");
		check(server.computeOnServer(s -> me(s, self).getHealth()) == health, "and the pilot takes no fall damage");
		check(context.computeOnClient(client -> ShipClient.piloting(client) != null), "the pilot is still in the cockpit");

		// On the ground it charges; quickly here, so the test does not wait a minute.
		server.runOnServer(s -> AbilityConfig.get().shipRechargeSeconds.put(ShipType.FALCON.id(), 4));
		for (int i = 0; i < 200 && server.computeOnServer(s -> ship(s).depleted()); i++) {
			context.waitTick();
		}

		check(!server.computeOnServer(s -> ship(s).depleted()), "on the ground it charges until the engines start again");
		server.runOnServer(s -> AbilityConfig.get().shipRechargeSeconds.put(ShipType.FALCON.id(), ShipType.FALCON.handling().recharge()));
	}

	/** Soft, hard and wrecking impacts: the hull, never the pilot. */
	private static void impacts(ClientGameTestContext context, TestServerContext server, UUID self) {
		float full = server.computeOnServer(s -> ship(s).getMaxHealth());
		server.runOnServer(s -> ship(s).setHealth(full));
		float health = server.computeOnServer(s -> me(s, self).getHealth());
		int crashes = server.computeOnServer(s -> ShipEntity.crashes());

		server.runOnServer(s -> ship(s).impactForTest(ShipType.FALCON.handling().safeImpact() * 0.8, true));
		check(server.computeOnServer(s -> ship(s).getHealth()) == full, "a gentle landing costs no hull");
		check(server.computeOnServer(s -> ShipEntity.crashes()) == crashes, "and is no crash");

		server.runOnServer(s -> ship(s).impactForTest(0.75, true));
		float hard = server.computeOnServer(s -> ship(s).getHealth());
		check(hard < full && hard > full * 0.5F, "a hard landing costs some hull (" + full + " -> " + hard + ")");
		context.waitTicks(2);
		context.takeScreenshot("phase13_hard_landing");

		server.runOnServer(s -> ship(s).setHealth(full));
		server.runOnServer(s -> ship(s).impactForTest(1.3, false));
		float crash = server.computeOnServer(s -> ship(s).getHealth());
		check(crash < hard, "a harder crash costs more (" + crash + " < " + hard + ")");
		check(server.computeOnServer(s -> me(s, self).getHealth()) == health, "the pilot is not hurt by impacts");

		int explosions = ShipEntity.explosions();
		server.runOnServer(s -> ship(s).impactForTest(ShipType.FALCON.handling().wreckImpact() + 0.2, false));
		context.waitTicks(3);
		check(ShipEntity.explosions() == explosions + 1, "a very hard impact wrecks the ship");
		check(context.computeOnClient(client -> client.player.getVehicle() == null), "the pilot is thrown out");
		check(server.computeOnServer(s -> me(s, self).isAlive()) && server.computeOnServer(s -> me(s, self).hasEffect(MobEffects.SLOW_FALLING)),
				"alive, and falling gently");
		check(context.computeOnClient(client -> !ShipClient.hidesPilot(client.player)), "and visible again right away");
		context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
		context.waitTicks(10);
		context.takeScreenshot("phase13_wreck");
		context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
		server.runOnServer(s -> Starships.resetCooldowns());
		context.waitTicks(80);
	}

	/** Dying in the cockpit: the player respawns visible and on foot. */
	private static void deathInCockpit(ClientGameTestContext context, TestServerContext server, UUID self) {
		server.runCommand("tp @a 0 ~ 0 180 0");
		context.waitTicks(10);
		server.computeOnServer(s -> Starships.command(me(s, self), "board", "xwing"));
		until(context, client -> ShipClient.piloting(client) != null, 80, "into an X-Wing");
		check(context.computeOnClient(client -> ShipClient.hidesPilot(client.player)), "hidden in the X-Wing");
		server.runOnServer(s -> me(s, self).kill(s.overworld()));
		context.waitTicks(10);
		context.runOnClient(client -> client.player.respawn());
		until(context, client -> client.player != null && client.player.isAlive() && client.player.getVehicle() == null, 100, "respawned on foot");
		context.setScreen(() -> null);
		check(context.computeOnClient(client -> !ShipClient.hidesPilot(client.player)), "after dying in the cockpit the player is drawn again");
		server.runOnServer(s -> Starships.resetCooldowns());
		server.computeOnServer(s -> Starships.command(me(s, self), "dismiss", "xwing"));
		context.waitTicks(20);
	}

	/** The Star Destroyer keeps its height above the ground and never dips its nose. */
	private static void starDestroyer(ClientGameTestContext context, TestServerContext server, UUID self) {
		server.runCommand("tp @a 0 ~ 0 180 0");
		server.runOnServer(s -> Starships.resetCooldowns());
		context.waitTicks(10);
		server.computeOnServer(s -> Starships.command(me(s, self), "board", "star_destroyer"));
		until(context, client -> ShipClient.piloting(client) != null, 80, "onto the Star Destroyer's bridge");
		check(server.computeOnServer(s -> ship(s).ship()) == ShipType.STAR_DESTROYER, "it is the Star Destroyer");
		context.waitTicks(80);
		double clearance = server.computeOnServer(Phase13GameTest::clearance);
		check(clearance >= ShipType.STAR_DESTROYER.handling().hoverHeight() - 2.0, "it hovers high above the ground (" + clearance + ")");
		context.getInput().holdKey(options -> options.keyUp);
		context.getInput().holdKey(options -> options.keySprint);
		float maxPitch = 0.0F;

		for (int i = 0; i < 60; i++) {
			context.waitTick();
			float pitch = server.computeOnServer(s -> ship(s).getXRot());
			maxPitch = Math.max(maxPitch, Math.abs(pitch));
		}

		context.getInput().releaseKey(options -> options.keySprint);
		context.takeScreenshot("phase13_star_destroyer_flight");
		context.getInput().releaseKey(options -> options.keyUp);
		check(maxPitch < 0.5F, "the Star Destroyer never dips its nose (" + maxPitch + "°)");
		double speed = server.computeOnServer(s -> ship(s).speed());
		check(speed > 0.1 && speed <= ShipType.STAR_DESTROYER.maxSpeed() + 0.01, "it cruises slowly (" + speed + ")");
		check(server.computeOnServer(Phase13GameTest::clearance) >= ShipType.STAR_DESTROYER.handling().hoverHeight() - 3.0,
				"and stays well above the ground");
		context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
		context.getInput().holdKeyFor(options -> options.keyShift, 3);
		until(context, client -> client.player.getVehicle() == null, 20, "climbing out of the Star Destroyer");
		check(context.computeOnClient(client -> !ShipClient.hidesPilot(client.player)), "visible again right after climbing out");
		check(server.computeOnServer(s -> me(s, self).hasEffect(MobEffects.SLOW_FALLING)), "high up the player glides down");
		// A look at the whole ship from the side, from far away.
		Vec3 at = server.computeOnServer(s -> ship(s).position());
		server.runCommand("gamemode spectator @a");
		server.runCommand(String.format(java.util.Locale.ROOT, "tp @a %.1f %.1f %.1f -90 8", at.x - 80.0, at.y + 12.0, at.z));
		context.waitTicks(60);
		context.takeScreenshot("phase13_star_destroyer_outside");
		server.runCommand(String.format(java.util.Locale.ROOT, "tp @a %.1f %.1f %.1f -90 8", at.x - 30.0, at.y - 4.0, at.z + 50.0));
		server.computeOnServer(s -> {
			me(s, self).lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, ship(s).position().add(0.0, 4.0, 0.0));
			return null;
		});
		context.waitTicks(30);
		context.takeScreenshot("phase13_star_destroyer_close");
		server.runCommand("gamemode survival @a");
		server.computeOnServer(s -> Starships.command(me(s, self), "dismiss", "star_destroyer"));
		server.runCommand("tp @a 0 ~ 0 180 0");
		context.waitTicks(60);
	}

	/** Many bolts hitting one spot: one impact sound, not a pile of them. */
	private static void impactLimiter(TestServerContext server) {
		server.runOnServer(s -> {
			Vec3 at = new Vec3(5.0, -60.0, 5.0);
			int before = ImpactSounds.skipped();
			check(ImpactSounds.allow(s.overworld(), at), "the first impact sound plays");
			check(!ImpactSounds.allow(s.overworld(), at.add(1.0, 0.0, 0.0)), "a second one right next to it, at once, is skipped");
			check(ImpactSounds.allow(s.overworld(), at.add(20.0, 0.0, 0.0)), "one far away plays");
			check(ImpactSounds.skipped() == before + 1, "one was skipped");
		});
	}

	// Skins and armor.

	/** Vader wears a cape; a character's skin shows over armor, which still protects. */
	private static void capeAndArmor(ClientGameTestContext context, TestServerContext server, UUID self) {
		context.runOnClient(client -> OutfitClient.choose(Outfit.VADER.id().toString()));
		context.waitTicks(10);
		check(context.computeOnClient(client -> client.player.getSkin().cape() != null), "Vader has a cape");
		check(context.computeOnClient(client -> OutfitClient.lookOf(Outfit.OBI_WAN).cape() != null), "Obi-Wan has his cloak");
		check(context.computeOnClient(client -> OutfitClient.lookOf(Outfit.LUKE).cape() == null), "Luke has no cape");
		server.runCommand("item replace entity @a weapon.mainhand with " + SABER);
		server.runCommand("tp @a 0 ~ 0 0 0");
		context.waitTicks(10);
		check(context.computeOnClient(client -> render(client).skin.cape() != null), "the drawn skin has the cape");
		check(context.computeOnClient(client -> render(client).showCape),
				"the cape is drawn, even without the game's own cape part");
		context.runOnClient(client -> {
			client.options.setCameraType(CameraType.THIRD_PERSON_BACK);
			client.options.fov().set(30);
		});
		context.waitTicks(20);
		context.takeScreenshot("phase13_vader_cape_back");
		context.runOnClient(client -> client.options.fov().set(70));
		context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
		context.waitTicks(10);
		context.takeScreenshot("phase13_vader_front");

		server.runCommand("item replace entity @a armor.head with minecraft:diamond_helmet");
		server.runCommand("item replace entity @a armor.chest with minecraft:diamond_chestplate");
		server.runCommand("item replace entity @a armor.legs with minecraft:diamond_leggings");
		server.runCommand("item replace entity @a armor.feet with minecraft:diamond_boots");
		context.waitTicks(10);
		check(server.computeOnServer(s -> me(s, self).getArmorValue()) == 20, "the diamond armor protects as always");
		check(context.computeOnClient(Phase13GameTest::armorDrawn) == 0, "as Vader, no armor piece is drawn over the skin");
		context.takeScreenshot("phase13_vader_with_armor");

		context.runOnClient(client -> ImmersionConfig.get().characterHideArmor = false);
		check(context.computeOnClient(Phase13GameTest::armorDrawn) == 4, "with the option off the armor is drawn again");
		context.runOnClient(client -> ImmersionConfig.get().characterHideArmor = true);
		context.runOnClient(client -> OutfitClient.choose(""));
		context.waitTicks(5);
		check(context.computeOnClient(Phase13GameTest::armorDrawn) == 4, "without a character the armor is drawn as usual");
		server.runCommand("item replace entity @a armor.chest with minecraft:elytra");
		context.runOnClient(client -> OutfitClient.choose(Outfit.VADER.id().toString()));
		context.waitTicks(5);
		check(context.computeOnClient(client -> render(client).chestEquipment.is(net.minecraft.world.item.Items.ELYTRA)),
				"an elytra stays visible, it is no armor");
		context.runOnClient(client -> {
			client.options.setCameraType(CameraType.FIRST_PERSON);
			OutfitClient.choose("");
		});
		server.runCommand("clear @a");
		context.waitTicks(5);
	}

	private static AvatarRenderState render(Minecraft client) {
		return (AvatarRenderState) client.getEntityRenderDispatcher().getRenderer(client.player).createRenderState(client.player, 1.0F);
	}

	private static int armorDrawn(Minecraft client) {
		AvatarRenderState state = render(client);
		return (state.headEquipment.isEmpty() ? 0 : 1) + (state.chestEquipment.isEmpty() ? 0 : 1)
				+ (state.legsEquipment.isEmpty() ? 0 : 1) + (state.feetEquipment.isEmpty() ? 0 : 1);
	}

	// Droids.

	/** Two droids at most by default; combat droids share a cooldown and hit softer together. */
	private static void droids(ClientGameTestContext context, TestServerContext server, UUID self) {
		server.runOnServer(s -> {
			AbilityConfig.get().droidLimit = 2;
			Droids.resetCooldowns();
		});
		server.runCommand("tp @a 0 ~ 0 180 0");
		context.waitTicks(10);
		server.computeOnServer(s -> Droids.command(me(s, self), "spawn", DroidType.CHOPPER.id()));
		context.waitTicks(5);
		server.runOnServer(s -> Droids.resetCooldowns());
		float alone = server.computeOnServer(s -> Droids.combatDamage(me(s, self), DroidType.CHOPPER));
		server.computeOnServer(s -> Droids.command(me(s, self), "spawn", DroidType.K2SO.id()));
		context.waitTicks(5);
		server.runOnServer(s -> Droids.resetCooldowns());
		check(server.computeOnServer(s -> Droids.liveCount()) == 2, "two droids come");
		server.computeOnServer(s -> Droids.command(me(s, self), "spawn", DroidType.R2D2.id()));
		context.waitTicks(5);
		check(server.computeOnServer(s -> Droids.liveCount()) == 2, "a third one is refused by default");

		float together = server.computeOnServer(s -> Droids.combatDamage(me(s, self), DroidType.CHOPPER));
		check(alone == DroidBalance.of(DroidType.CHOPPER).damage && together < alone,
				"two combat droids together hit softer each (" + alone + " -> " + together + ")");

		// Chopper zaps a husk, K-2SO has to wait for the shared cooldown.
		var chopper = server.computeOnServer(s -> Droids.droid(me(s, self), DroidType.CHOPPER).position());
		server.runCommand(String.format("summon minecraft:husk %.2f %.2f %.2f {NoAI:1b,PersistenceRequired:1b,Silent:1b,Health:20f}",
				chopper.x + 1.5, chopper.y, chopper.z));
		context.waitTicks(5);
		server.computeOnServer(s -> Droids.command(me(s, self), "ability", DroidType.CHOPPER.id()));
		check(server.computeOnServer(s -> s.overworld().getEntitiesOfClass(net.minecraft.world.entity.monster.zombie.Husk.class, AREA, Entity::isAlive)
				.getFirst().getHealth()) < 20.0F, "Chopper's zap hurts");
		// A short wait for the command rate limit, well within the shared cooldown.
		context.waitTicks(12);
		String shared = server.computeOnServer(s -> String.valueOf(Droids.command(me(s, self), "ability", DroidType.K2SO.id())));
		check(shared.contains("combat_cooldown"), "right after, K-2SO waits for the shared combat cooldown: " + shared);
		server.runCommand("kill @e[type=husk]");
		server.computeOnServer(s -> Droids.command(me(s, self), "dismiss", DroidType.CHOPPER.id()));
		server.computeOnServer(s -> Droids.command(me(s, self), "dismiss", DroidType.K2SO.id()));
		context.waitTicks(10);
		check(DroidType.R2D2.role() == DroidType.Role.SUPPORT && DroidType.C3PO.role() == DroidType.Role.INFO
				&& DroidType.B1.role() == DroidType.Role.COMBAT, "every droid has its role");
	}

	/** On a dedicated server another client's view is the same: the pilot hidden, then back. */
	private static void dedicatedServer(ClientGameTestContext context) {
		try (TestDedicatedServerContext server = context.worldBuilder().createServer()) {
			try (var connection = server.connect()) {
				connection.waitForChunksDownload();
				context.waitTicks(40);
				server.runCommand("gamerule spawn_mobs false");
				context.waitFor(client -> ShipClient.status() != null, 40);
				context.runOnClient(client -> ShipClient.command("board", ShipType.FALCON));
				until(context, client -> ShipClient.piloting(client) != null, 80, "dedicated server: into the Falcon");
				check(context.computeOnClient(client -> ShipClient.hidesPilot(client.player)), "dedicated server: the pilot is hidden");
				check(server.computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().getVehicle() instanceof ShipEntity),
						"dedicated server: the server has the pilot in the ship");
				context.getInput().holdKeyFor(options -> options.keyJump, 20);
				context.waitTicks(10);
				context.takeScreenshot("phase13_dedicated_falcon");
				context.getInput().holdKeyFor(options -> options.keyShift, 3);
				until(context, client -> client.player.getVehicle() == null, 20, "dedicated server: climbing out");
				check(context.computeOnClient(client -> !ShipClient.hidesPilot(client.player)), "dedicated server: visible again");
			}
		}
	}

	// Helpers.

	private static double clearance(MinecraftServer server) {
		ShipEntity ship = ship(server);
		return ship.getY() - server.overworld().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
				ship.getBlockX(), ship.getBlockZ());
	}

	private static ShipEntity ship(MinecraftServer server) {
		return server.overworld().getEntitiesOfClass(ShipEntity.class, AREA, Entity::isAlive).getFirst();
	}

	private static ServerPlayer me(MinecraftServer server, UUID self) {
		return server.getPlayerList().getPlayer(self);
	}

	private static void until(ClientGameTestContext context, java.util.function.Predicate<Minecraft> condition, int ticks, String message) {
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
