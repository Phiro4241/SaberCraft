package com.phiro.sabercraft.gametest;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.UUID;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import net.minecraft.client.CameraType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.ability.AbilityConfig;
import com.phiro.sabercraft.ability.HiltTraits;
import com.phiro.sabercraft.ability.SaberAbilities;
import com.phiro.sabercraft.character.Outfit;
import com.phiro.sabercraft.character.OutfitSync;
import com.phiro.sabercraft.client.ability.SaberAbilityClient;
import com.phiro.sabercraft.client.character.CustomProfiles;
import com.phiro.sabercraft.client.character.OutfitClient;
import com.phiro.sabercraft.client.effect.SaberEffects;
import com.phiro.sabercraft.client.menu.CharacterMenuScreen;
import com.phiro.sabercraft.client.menu.SaberMenuScreen;
import com.phiro.sabercraft.client.render.SaberBladeSpecialRenderer;
import com.phiro.sabercraft.client.saber.Easing;
import com.phiro.sabercraft.client.saber.SaberAnimations;
import com.phiro.sabercraft.client.saber.SaberHandState;
import com.phiro.sabercraft.client.saber.SaberHilts;
import com.phiro.sabercraft.client.saber.SaberProfiles;
import com.phiro.sabercraft.client.saber.SaberTracker;
import com.phiro.sabercraft.saber.SaberVariant;

/**
 * Phase 10: saber types that also play differently, their own ignitions, clashes where the blades
 * meet, and character profiles of your own.
 *
 * <ul>
 *   <li>Hilts: the server changes damage, guard, parry window, knockback and dodge cooldown by hilt
 *       (measured with real hits), and {@code hilt_traits: false} switches it off.</li>
 *   <li>Ignitions: Kylo's crossguard stutters, the double blade ignites one blade after the other,
 *       Yoda's and Ahsoka's small hilts ignite quickly (screenshots during the ignition).</li>
 *   <li>Clash: a blocked blow sparks between the blocker and the attacker, both blades flash when
 *       the attacker has a lit saber too.</li>
 *   <li>Profiles: JSON files in {@code config/sabercraft/profiles/}, with a skin of their own or a
 *       base character, chosen in the menu, with their saber; broken files are skipped.</li>
 * </ul>
 *
 * <p>Screenshots: {@code build/run/clientGameTest/screenshots/types_*}.
 */
public class SaberTypesGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		context.runOnClient(client -> client.options.renderDistance().set(4));
		this.animations(context);

		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			context.waitTicks(100);
			TestServerContext server = singleplayer.getServer();
			server.runCommand("gamerule advance_time false");
			server.runCommand("gamerule spawn_mobs false");
			server.runCommand("gamerule send_command_feedback false");
			server.runCommand("time set day");
			server.runCommand("difficulty normal");
			server.runCommand("gamemode survival @a");
			server.runCommand("tp @a 0 ~ 0 180 0");
			server.runCommand("kill @e[type=!player]");
			server.runCommand("clear @a");
			context.waitTicks(20);
			UUID self = context.computeOnClient(client -> client.player.getUUID());

			this.traits(context, server, self);
			this.ignitions(context, server);
			this.clash(context, server);
			this.profiles(context, server, self);
		}
	}

	private void animations(ClientGameTestContext context) {
		check(SaberHilts.get(SaberVariant.CROSSGUARD).profile() == SaberProfiles.UNSTABLE, "Kylo's crossguard ignites unstable");
		check(SaberHilts.get(SaberVariant.DOUBLE).profile().animation() == SaberAnimations.DOUBLE, "the double blade has its own ignition");
		check(SaberHilts.get(SaberVariant.YODA).profile().animation() == SaberAnimations.QUICK, "Yoda's small hilt ignites quickly");
		check(SaberHilts.get(SaberVariant.TANO).profile().animation() == SaberAnimations.QUICK, "Ahsoka's hilt ignites quickly");
		check(SaberHilts.get(SaberVariant.DARK_LORD).profile() == SaberProfiles.DARK, "Vader's hilt keeps its heavy ignition");
		check(SaberHilts.get(SaberVariant.GUARDIAN).profile() == SaberProfiles.DEFAULT, "the other hilts keep the default ignition");
		check(SaberAnimations.QUICK.igniteTicks() < SaberAnimations.DEFAULT.igniteTicks()
				&& SaberAnimations.UNSTABLE.igniteTicks() > SaberAnimations.DEFAULT.igniteTicks(), "quick is quicker, unstable slower than the default");

		// The stutter curve: from 0 to 1, never back, with stalls on the way.
		float last = 0.0F;
		int stalls = 0;
		boolean monotonic = true;

		for (int i = 1; i <= 100; i++) {
			float value = Easing.STUTTER.apply(i / 100.0F);
			monotonic &= value >= last - 1.0E-6F;
			stalls += value - last < 1.0E-6F ? 1 : 0;
			last = value;
		}

		check(monotonic, "the stutter curve never goes back");

		check(Easing.STUTTER.apply(0.0F) == 0.0F && Math.abs(last - 1.0F) < 1.0E-4F, "the stutter curve runs from 0 to 1");
		check(stalls >= 15, "the stutter curve stalls on the way (" + stalls + " steps)");
		check(Math.abs(Easing.STUTTER.apply(Easing.STUTTER.inverse(0.6F)) - 0.6F) < 0.01F, "the stutter curve inverts, so a retract can start half way");

		// The ignite windows of the double blade: one after the other, and back the other way round.
		check(near(SaberBladeSpecialRenderer.windowed(0.3F, 0.0F, 0.6F), 0.5F) && SaberBladeSpecialRenderer.windowed(0.3F, 0.4F, 1.0F) == 0.0F,
				"at 30 % only the first blade is out, half way");
		check(SaberBladeSpecialRenderer.windowed(0.7F, 0.0F, 0.6F) == 1.0F && near(SaberBladeSpecialRenderer.windowed(0.7F, 0.4F, 1.0F), 0.5F),
				"at 70 % the first blade is out and the second half way");
		check(SaberBladeSpecialRenderer.windowed(0.5F, 0.0F, 1.0F) == 0.5F, "a blade without a window follows the saber");
	}

	private void traits(ClientGameTestContext context, TestServerContext server, UUID self) {
		check(HiltTraits.of(SaberVariant.STANDARD) == HiltTraits.NEUTRAL, "the standard hilt has no combat edge");
		check(HiltTraits.of(SaberVariant.CROSSGUARD).damage() > 1.0F, "the crossguard hits harder");
		check(HiltTraits.of(SaberVariant.YODA).damage() < 1.0F && HiltTraits.of(SaberVariant.YODA).dodgeCooldown() < 1.0F,
				"Yoda's short blade hits softer but dodges sooner");
		check(HiltTraits.of(SaberVariant.KENOBI).blockBonus() > 0.0F, "Obi-Wan's hilt guards better");
		check(HiltTraits.of(SaberVariant.DOUBLE).blockBonus() > 0.0F, "two blades cover more");
		check(SaberAbilities.parryBonus(saber(SaberVariant.DOOKU, SaberVariant.MAKASHI)) > SaberAbilities.parryBonus(saber(SaberVariant.STANDARD, SaberVariant.MAKASHI)),
				"Dooku's hilt widens the parry window on top of Makashi");
		check(context.computeOnClient(client -> SaberMenuScreenAccess.traits(SaberVariant.CROSSGUARD)).contains("+12"),
				"the hilt menu tells the crossguard's damage bonus");

		// Real hits: the same blow with the crossguard and with the standard hilt.
		server.runCommand("summon minecraft:zombie 0 ~ -2 {NoAI:1b,PersistenceRequired:1b,Silent:1b,attributes:[{id:\"minecraft:max_health\",base:200},"
				+ "{id:\"minecraft:knockback_resistance\",base:1}],Health:200f}");
		context.waitTicks(5);
		float crossguard = hit(context, server, self, SaberVariant.CROSSGUARD);
		float standard = hit(context, server, self, SaberVariant.STANDARD);
		check(Math.abs(crossguard / standard - HiltTraits.of(SaberVariant.CROSSGUARD).damage()) < 0.02F,
				"a crossguard blow does " + crossguard + " instead of " + standard + " damage");
		server.runOnServer(s -> AbilityConfig.get().hiltTraits = false);
		float switchedOff = hit(context, server, self, SaberVariant.CROSSGUARD);
		server.runOnServer(s -> AbilityConfig.get().hiltTraits = true);
		check(Math.abs(switchedOff - standard) < 0.01F, "with hilt_traits off the crossguard hits like the standard hilt: " + switchedOff);
		server.runCommand("kill @e[type=minecraft:zombie]");
		// Let the poof of the zombie and the advancement toast pass before the screenshots.
		context.waitTicks(120);
		context.runOnClient(client -> client.gui.hud.getChat().clearMessages(false));
	}

	/** Puts a saber with the hilt into the hand, waits for a full swing and hits the zombie once. */
	private static float hit(ClientGameTestContext context, TestServerContext server, UUID self, net.minecraft.resources.Identifier hilt) {
		server.runOnServer(s -> {
			player(s, self).setItemInHand(InteractionHand.MAIN_HAND, saber(hilt, SaberVariant.defaultStyle(hilt)));
			zombie(s).setHealth(200.0F);
		});
		context.waitTicks(40);
		return server.computeOnServer(s -> {
			ServerPlayer player = player(s, self);
			LivingEntity zombie = zombie(s);
			float before = zombie.getHealth();
			zombie.invulnerableTime = 0;
			player.attack(zombie);
			return before - zombie.getHealth();
		});
	}

	private void ignitions(ClientGameTestContext context, TestServerContext server) {
		context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
		String[][] hilts = {{"double", "staff"}, {"crossguard", "unbridled"}, {"yoda", "ataru"}};

		for (String[] entry : hilts) {
			server.runCommand("item replace entity @a hotbar.1 with minecraft:netherite_sword[minecraft:custom_model_data={strings:[\"sabercraft:" + entry[0]
					+ "\",\"sabercraft:red\",\"sabercraft:" + entry[1] + "\"]}]");
			server.runCommand("item replace entity @a hotbar.0 with minecraft:air");
			context.runOnClient(client -> client.player.getInventory().setSelectedSlot(0));
			context.waitTicks(20);
			context.runOnClient(client -> client.player.getInventory().setSelectedSlot(1));
			var animation = SaberHilts.get(SaberCraft.id(entry[0])).profile().animation();
			// Half way through the ignition, and a quarter later.
			context.waitTicks(animation.igniteDelay() + animation.igniteTicks() / 2);
			context.takeScreenshot("types_ignite_" + entry[0] + "_early");
			float early = context.computeOnClient(client -> SaberTracker.INSTANCE.state(client.player, InteractionHand.MAIN_HAND).extension(1.0F));
			context.waitTicks(Math.max(1, animation.igniteTicks() / 4));
			context.takeScreenshot("types_ignite_" + entry[0] + "_late");
			context.waitTicks(20);
			float full = context.computeOnClient(client -> SaberTracker.INSTANCE.state(client.player, InteractionHand.MAIN_HAND).extension(1.0F));
			check(early > 0.0F && early < 1.0F && full == 1.0F, entry[0] + " ignites over time (" + early + " -> " + full + ")");
		}

		context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
	}

	private void clash(ClientGameTestContext context, TestServerContext server) {
		server.runCommand("item replace entity @a hotbar.1 with minecraft:netherite_sword[minecraft:custom_model_data={strings:[\"sabercraft:kenobi\",\"sabercraft:blue\",\"sabercraft:soresu\"]}]");
		server.runCommand("tp @a 0 ~ 0 180 0");
		// Kylo as a husk (it does not burn in the sun), a step and a half in front, his blade lit.
		server.runCommand("summon minecraft:husk 0 ~ -1.6 {NoAI:1b,PersistenceRequired:1b,Silent:1b,Rotation:[0f,0f],equipment:{mainhand:{id:\"minecraft:netherite_sword\",count:1,"
				+ "components:{\"minecraft:custom_model_data\":{strings:[\"sabercraft:crossguard\",\"sabercraft:red\"]}}}}}");
		context.waitTicks(40);
		context.getInput().holdKey(SaberAbilityClient.GUARD_KEY);
		context.waitTicks(25);
		server.runCommand("damage @p 4 minecraft:mob_attack by @e[type=minecraft:husk,limit=1]");
		context.waitTicks(1);
		SaberEffects.Clash clash = context.computeOnClient(client -> SaberEffects.lastClash());
		Vec3 self = context.computeOnClient(client -> client.player.position());
		check(clash != null && clash.blades(), "a blow of a lit saber on the guard is a blade-on-blade clash");
		check(clash.at().z < self.z - 0.4 && clash.at().z > self.z - 1.2 && Math.abs(clash.at().x - self.x) < 0.2,
				"the sparks are between the two, where the blades meet: " + clash.at() + " (player at " + self + ")");
		check(clash.at().y > self.y + 0.6 && clash.at().y < self.y + 1.8, "the sparks are at the height of the blades: " + clash.at().y);
		check(context.computeOnClient(client -> SaberTracker.INSTANCE.state(client.player, InteractionHand.MAIN_HAND).isClashing()), "the own blade flashes up");
		check(context.computeOnClient(client -> {
			for (var entity : client.level.entitiesForRendering()) {
				if (entity instanceof Zombie zombie) {
					SaberHandState state = SaberTracker.INSTANCE.state(zombie, InteractionHand.MAIN_HAND);
					return state != null && state.isClashing();
				}
			}

			return false;
		}), "the attacker's blade flashes up too");
		context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
		context.takeScreenshot("types_clash");
		context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));

		// A blow without a saber is blocked, but it is no clash of blades.
		server.runCommand("kill @e[type=minecraft:husk]");
		server.runCommand("summon minecraft:pig 0 ~ -2");
		context.waitTicks(30);
		server.runCommand("damage @p 4 minecraft:mob_attack by @e[type=minecraft:pig,limit=1]");
		context.waitTicks(1);
		SaberEffects.Clash block = context.computeOnClient(client -> SaberEffects.lastClash());
		check(block != clash && !block.blades(), "a pig's blow is blocked, but no clash of blades");
		context.getInput().releaseKey(SaberAbilityClient.GUARD_KEY);
		server.runCommand("kill @e[type=minecraft:pig]");
		server.runCommand("effect clear @a");
		context.waitTicks(10);
	}

	private void profiles(ClientGameTestContext context, TestServerContext server, UUID self) {
		context.runOnClient(client -> {
			Path folder = CustomProfiles.folder();

			try {
				Files.createDirectories(folder);

				try (InputStream in = SaberCraft.class.getResourceAsStream("/assets/sabercraft/textures/outfit/scout.png")) {
					Files.copy(in, folder.resolve("knight.png"), StandardCopyOption.REPLACE_EXISTING);
				}

				Files.writeString(folder.resolve("knight.json"), """
						{"name": "Grey Knight", "skin": "knight.png", "hilt": "sabercraft:crossguard", "color": "sabercraft:purple",
						 "style": "sabercraft:unbridled", "side": "grey"}""");
				Files.writeString(folder.resolve("master.json"), """
						{"name": "Old Master", "base": "obi_wan", "side": "light"}""");
				Files.writeString(folder.resolve("broken.json"), "{ this is not json");
				Files.writeString(folder.resolve("wrong_saber.json"), """
						{"name": "Wrong", "hilt": "sabercraft:double", "style": "sabercraft:flowing"}""");
				Files.writeString(folder.resolve("escape.json"), """
						{"name": "Escape", "skin": "../outfits/../../options.txt"}""");
			} catch (IOException error) {
				throw new UncheckedIOException(error);
			}
		});

		check(context.computeOnClient(client -> CustomProfiles.reload().size()) == 4, "four profiles read, the broken file skipped");
		check(context.computeOnClient(client -> CustomProfiles.byKey("wrong_saber").orElseThrow().saber() == null),
				"a saber that does not fit together is left out");
		check(context.computeOnClient(client -> CustomProfiles.byKey("escape").orElseThrow().skin() == null),
				"a skin outside the profile folder is not read");
		check(context.computeOnClient(client -> CustomProfiles.byKey("knight").orElseThrow().side() == Outfit.Side.GREY), "the side is read");

		context.runOnClient(client -> client.player.getInventory().setSelectedSlot(0));
		server.runCommand("item replace entity @a hotbar.0 with minecraft:netherite_sword");
		context.waitTicks(10);
		context.runOnClient(client -> SaberMenuScreen.open(client, SaberMenuScreen.Tab.CHARACTER));
		context.waitTicks(3);
		context.clickScreenButton("sabercraft.menu.character.outfits");
		context.waitTicks(3);
		context.runOnClient(client -> ((CharacterMenuScreen) client.gui.screen()).chooseProfile(CustomProfiles.byKey("knight").orElseThrow()));
		context.waitTicks(10);
		// Open the menu again, so the profile button shows the chosen profile, with the mouse aside.
		context.runOnClient(client -> SaberMenuScreen.open(client, SaberMenuScreen.Tab.CHARACTER));
		context.getInput().setCursorPos(2.0, 2.0);
		context.waitTicks(3);
		context.takeScreenshot("types_profile_menu");
		String held = server.computeOnServer(s -> {
			ItemStack stack = player(s, self).getMainHandItem();
			return SaberVariant.hiltOf(stack) + " " + SaberVariant.colorOf(stack) + " " + SaberVariant.styleOf(stack);
		});
		check(held.equals("sabercraft:crossguard sabercraft:purple sabercraft:unbridled"), "the profile brings its saber along: " + held);
		check(context.computeOnClient(client -> OutfitClient.look(self).texture().getPath().startsWith("profile_skin/")), "you are drawn with the profile's skin");
		check(server.computeOnServer(s -> OutfitSync.outfitOf(self)).isEmpty(), "a profile without a base character is not sent to the server");

		context.runOnClient(client -> ((CharacterMenuScreen) client.gui.screen()).chooseProfile(CustomProfiles.byKey("master").orElseThrow()));
		context.waitTicks(10);
		check(server.computeOnServer(s -> OutfitSync.outfitOf(self)).equals(Optional.of(Outfit.OBI_WAN.id())), "the others see the base character");
		check(context.computeOnClient(client -> OutfitClient.look(self).texture().equals(OutfitClient.lookOf(Outfit.OBI_WAN).texture())),
				"a profile with a base character looks like it");
		check(server.computeOnServer(s -> SaberVariant.hiltOf(player(s, self).getMainHandItem())).equals(SaberVariant.CROSSGUARD),
				"a profile without a saber leaves the saber as it is");
		context.runOnClient(client -> client.gui.setScreen(null));
		context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
		context.waitTicks(5);
		context.takeScreenshot("types_profile_master");

		// Clean up: the plain skin, the characters list, no files left.
		context.runOnClient(client -> {
			client.options.setCameraType(CameraType.FIRST_PERSON);
			OutfitClient.choose("");
			SaberMenuScreen.open(client, SaberMenuScreen.Tab.CHARACTER);
		});
		context.waitTicks(3);
		context.clickScreenButton("sabercraft.menu.character.characters");
		context.runOnClient(client -> {
			client.gui.setScreen(null);

			try (var files = Files.list(CustomProfiles.folder())) {
				for (Path file : files.toList()) {
					Files.delete(file);
				}
			} catch (IOException error) {
				throw new UncheckedIOException(error);
			}

			CustomProfiles.reload();
		});
		context.waitTicks(10);
	}

	private static ItemStack saber(net.minecraft.resources.Identifier hilt, net.minecraft.resources.Identifier style) {
		ItemStack stack = new ItemStack(net.minecraft.world.item.Items.NETHERITE_SWORD);
		new SaberVariant(hilt, SaberVariant.RED, style).applyTo(stack);
		return stack;
	}

	private static ServerPlayer player(MinecraftServer server, UUID self) {
		return server.getPlayerList().getPlayer(self);
	}

	private static LivingEntity zombie(MinecraftServer server) {
		return server.overworld().getEntitiesOfClass(Zombie.class, new AABB(-30, -64, -30, 30, 320, 30), LivingEntity::isAlive).getFirst();
	}

	/** The hilt menu line, read without opening the menu. */
	private static final class SaberMenuScreenAccess {
		static String traits(net.minecraft.resources.Identifier hilt) {
			return com.phiro.sabercraft.client.select.SaberCustomizeScreen.traitsLine(hilt).getString();
		}
	}

	private static boolean near(float value, float expected) {
		return Math.abs(value - expected) < 1.0E-4F;
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}

		SaberCraft.LOGGER.info("OK: {}", message);
	}
}
