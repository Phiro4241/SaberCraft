package com.phiro.sabercraft.gametest;

import java.util.Optional;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import org.lwjgl.glfw.GLFW;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.entity.FakePlayer;

import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.ability.SaberAbility;
import com.phiro.sabercraft.advancement.SaberAdvancements;
import com.phiro.sabercraft.blaster.BlasterType;
import com.phiro.sabercraft.blaster.Blasters;
import com.phiro.sabercraft.character.Outfit;
import com.phiro.sabercraft.client.ability.SaberAbilityClient;
import com.phiro.sabercraft.duel.Duels;
import com.phiro.sabercraft.item.ItemConversion;
import com.phiro.sabercraft.network.ChooseOutfitPayload;

/**
 * Phase 11: the SaberCraft advancements come from the real events, once, and are still there after
 * the world was closed and opened again.
 *
 * <p>Joining gives the root; a lightsaber in hand "The Force Awakens"; a real right-click with a
 * blaster "The First Shot"; a bolt that hits a zombie 10 blocks away gives nothing, one 36 blocks
 * away "Sharpshooter"; the real spin key with two zombies around "Rotational Speed" and the first
 * of seven steps of "The Chosen One"; a duel against a second player "The Duel Begins", winning it
 * as Darth Vader "The Dark Lord". A second award of the same advancement changes nothing. Then
 * the world is saved, closed and opened again: everything is still there, and the half-done
 * "Chosen One" keeps its step. Screenshot of the advancement tab: {@code advancements_tab}.
 */
public class AdvancementGameTest implements FabricClientGameTest {
	private static final UUID RIVAL = UUID.fromString("5ab3c4af-0000-4000-8000-00000000d0e1");

	@Override
	public void runTest(ClientGameTestContext context) {
		modIcon();
		context.runOnClient(client -> client.options.renderDistance().set(5));
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

			check(has(server, self, SaberAdvancements.ROOT), "joining gives the SaberCraft root");
			check(!has(server, self, SaberAdvancements.FORCE_AWAKENS), "no other advancement yet");

			// A lightsaber in hand.
			server.runCommand("item replace entity @a weapon.mainhand with minecraft:netherite_sword");
			context.waitTicks(25);
			check(has(server, self, SaberAdvancements.FORCE_AWAKENS), "a lightsaber in hand: The Force Awakens");

			blasters(context, server, self);
			spin(context, server, self);
			duel(context, server, self);

			// A second award is no news: no second toast or chat line.
			check(!server.computeOnServer(s -> SaberAdvancements.award(me(s, self), SaberAdvancements.FIRST_SHOT)), "a second award changes nothing");

			context.setScreen(() -> new AdvancementsScreen(context.computeOnClient(client -> client.player.connection.getAdvancements())));
			context.waitTicks(10);
			context.takeScreenshot("advancements_tab");
			context.setScreen(() -> null);
		}

		// Saved with the world, there again after opening it.
		try (TestSingleplayerContext singleplayer = save.open()) {
			context.waitTicks(60);
			TestServerContext server = singleplayer.getServer();

			for (String name : new String[] {SaberAdvancements.ROOT, SaberAdvancements.FORCE_AWAKENS, SaberAdvancements.FIRST_SHOT,
					SaberAdvancements.SHARPSHOOTER, SaberAdvancements.SPIN_SPEED, SaberAdvancements.DUEL_BEGINS, SaberAdvancements.DARK_LORD}) {
				check(has(server, self, name), name + " is still there after closing and opening the world");
			}

			check(server.computeOnServer(s -> {
				var progress = SaberAdvancements.progress(me(s, self), SaberAdvancements.CHOSEN_ONE);
				return !progress.isDone() && progress.getCriterion("spin").isDone();
			}), "the started Chosen One keeps its step after the restart");

			// The other six abilities complete it.
			server.computeOnServer(s -> {
				for (SaberAbility ability : SaberAbility.values()) {
					SaberAdvancements.abilityUsed(me(s, self), ability);
				}

				return null;
			});
			check(has(server, self, SaberAdvancements.CHOSEN_ONE), "all seven abilities: The Chosen One");
			// Lets the advancement chat message arrive before the world closes. On CI a message still
			// on its way while the connection closed left the world closing forever.
			context.waitTicks(20);
		}
	}

	/** The mod icon from fabric.mod.json is in the jar and a clean square picture. */
	private static void modIcon() {
		var mod = net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer(SaberCraft.MOD_ID).orElseThrow();
		String path = mod.getMetadata().getIconPath(512).orElseThrow();
		var file = mod.findPath(path).orElseThrow();

		try (var in = java.nio.file.Files.newInputStream(file)) {
			var image = com.mojang.blaze3d.platform.NativeImage.read(in);
			check(image.getWidth() == 512 && image.getHeight() == 512, "the mod icon " + path + " loads, 512 x 512");
			image.close();
		} catch (java.io.IOException e) {
			throw new AssertionError("the mod icon does not load", e);
		}
	}

	private static void blasters(ClientGameTestContext context, TestServerContext server, UUID self) {
		server.computeOnServer(s -> {
			ServerPlayer player = me(s, self);
			ItemStack bow = new ItemStack(Items.BOW);
			ItemConversion.toBlaster(bow, BlasterType.DC15A);
			player.getInventory().setItem(player.getInventory().getSelectedSlot(), bow);
			player.getInventory().setItem(8, new ItemStack(Items.ARROW, 16));
			return null;
		});
		server.runCommand("summon minecraft:zombie 0 ~ -10 {NoAI:1b,PersistenceRequired:1b,Silent:1b,attributes:[{id:\"minecraft:max_health\",base:200}],Health:200f}");
		context.getInput().lookAt(180.0F, 0.0F);
		context.waitTicks(10);
		check(!has(server, self, SaberAdvancements.FIRST_SHOT), "no shot yet");
		context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
		context.waitTicks(10);
		check(has(server, self, SaberAdvancements.FIRST_SHOT), "a real blaster shot: The First Shot");
		check(server.computeOnServer(s -> zombie(s).getHealth()) < 200.0F, "the zombie 10 blocks away was hit");
		check(!has(server, self, SaberAdvancements.SHARPSHOOTER), "a hit from 10 blocks is no precision shot");

		server.runCommand("kill @e[type=zombie]");
		server.runCommand("summon minecraft:zombie 0 ~ -36 {NoAI:1b,PersistenceRequired:1b,Silent:1b,attributes:[{id:\"minecraft:max_health\",base:200}],Health:200f}");
		context.waitTicks(30);

		for (int shot = 0; shot < 4 && !has(server, self, SaberAdvancements.SHARPSHOOTER); shot++) {
			server.computeOnServer(s -> {
				Blasters.cool(self);
				me(s, self).getCooldowns().removeCooldown(me(s, self).getCooldowns().getCooldownGroup(me(s, self).getMainHandItem()));
				return null;
			});
			context.waitTicks(2);
			context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
			context.waitTicks(20);
		}

		check(server.computeOnServer(s -> zombie(s).getHealth()) < 200.0F, "the zombie 36 blocks away was hit");
		check(has(server, self, SaberAdvancements.SHARPSHOOTER), "a hit from 36 blocks: Sharpshooter");
		server.runCommand("kill @e[type=zombie]");
	}

	private static void spin(ClientGameTestContext context, TestServerContext server, UUID self) {
		server.runCommand("item replace entity @a weapon.mainhand with minecraft:netherite_sword");
		server.runCommand("summon minecraft:zombie 1.5 ~ 0 {NoAI:1b,PersistenceRequired:1b,Silent:1b}");
		server.runCommand("summon minecraft:zombie -1.5 ~ 0 {NoAI:1b,PersistenceRequired:1b,Silent:1b}");
		context.waitTicks(30);
		context.getInput().pressKey(SaberAbilityClient.SPIN_KEY);
		context.waitTicks(30);
		check(has(server, self, SaberAdvancements.SPIN_SPEED), "the real spin key hits both zombies: Rotational Speed");
		check(server.computeOnServer(s -> SaberAdvancements.progress(me(s, self), SaberAdvancements.CHOSEN_ONE).getCriterion("spin").isDone()),
				"the spin is one step of The Chosen One");
		check(!has(server, self, SaberAdvancements.CHOSEN_ONE), "one ability is not all seven");
		server.runCommand("kill @e[type=zombie]");
	}

	private static void duel(ClientGameTestContext context, TestServerContext server, UUID self) {
		// As Darth Vader, through the same message the character menu sends.
		context.runOnClient(client -> ClientPlayNetworking.send(new ChooseOutfitPayload(Optional.of(Outfit.VADER.id()))));
		server.runOnServer(s -> {
			ServerLevel level = s.overworld();
			FakePlayer rival = new Rival(level, new GameProfile(RIVAL, "Rival"));
			rival.snapTo(0.0, me(s, self).getY(), -3.0, 0.0F, 0.0F);
			level.addNewPlayer(rival);
		});
		context.waitTicks(10);
		check(server.computeOnServer(s -> Duels.challenge(rival(s), me(s, self))) == Duels.ChallengeResult.SENT, "the rival challenges you");
		context.runOnClient(client -> client.player.connection.sendCommand("sabercraft duel accept"));
		context.waitTicks(5);
		check(has(server, self, SaberAdvancements.DUEL_BEGINS), "the duel starts: The Duel Begins");
		check(!has(server, self, SaberAdvancements.DARK_LORD), "not yet won");

		for (int round = 1; round <= 2; round++) {
			context.waitTicks(Duels.COUNTDOWN_TICKS + 2);
			server.runOnServer(s -> {
				ServerPlayer rival = rival(s);
				rival.invulnerableTime = 0;
				rival.hurtServer(s.overworld(), s.overworld().damageSources().playerAttack(me(s, self)), 100.0F);
			});
		}

		context.waitTicks(3);
		check(server.computeOnServer(s -> Duels.lastEnd(self)).orElse(null) == Duels.End.WON, "the duel was won");
		check(has(server, self, SaberAdvancements.DARK_LORD), "won as Darth Vader: The Dark Lord");
		server.runOnServer(s -> rival(s).discard());
		context.runOnClient(client -> ClientPlayNetworking.send(new ChooseOutfitPayload(Optional.empty())));
		context.waitTicks(15);
	}

	/** Fabric's fake players cannot be hurt; the rival can, like a real player. */
	private static final class Rival extends FakePlayer {
		Rival(ServerLevel level, GameProfile profile) {
			super(level, profile);
		}

		@Override
		public boolean isInvulnerableTo(ServerLevel level, net.minecraft.world.damagesource.DamageSource source) {
			return false;
		}
	}

	private static boolean has(TestServerContext server, UUID self, String name) {
		return server.computeOnServer(s -> SaberAdvancements.has(me(s, self), name));
	}

	private static ServerPlayer me(MinecraftServer server, UUID self) {
		return server.getPlayerList().getPlayer(self);
	}

	private static ServerPlayer rival(MinecraftServer server) {
		return (ServerPlayer) server.overworld().getEntity(RIVAL);
	}

	private static net.minecraft.world.entity.LivingEntity zombie(MinecraftServer server) {
		return server.overworld().getEntitiesOfClass(net.minecraft.world.entity.monster.zombie.Zombie.class,
				new net.minecraft.world.phys.AABB(-60, -64, -60, 60, 320, 60), net.minecraft.world.entity.LivingEntity::isAlive).getFirst();
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}

		SaberCraft.LOGGER.info("OK: {}", message);
	}
}
