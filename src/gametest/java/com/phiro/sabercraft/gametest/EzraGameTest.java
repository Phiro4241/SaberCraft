package com.phiro.sabercraft.gametest;

import java.util.UUID;

import org.lwjgl.glfw.GLFW;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import net.minecraft.client.CameraType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.blaster.BlasterBolt;
import com.phiro.sabercraft.blaster.BlasterType;
import com.phiro.sabercraft.blaster.Blasters;
import com.phiro.sabercraft.character.Outfit;
import com.phiro.sabercraft.character.OutfitSync;
import com.phiro.sabercraft.client.ability.SaberAbilityClient;
import com.phiro.sabercraft.client.blaster.BlasterBoltRenderer;
import com.phiro.sabercraft.client.character.OutfitClient;
import com.phiro.sabercraft.client.combat.CombatState;
import com.phiro.sabercraft.client.menu.SaberMenuScreen;
import com.phiro.sabercraft.client.saber.SaberProfiles;
import com.phiro.sabercraft.client.saber.SaberTracker;
import com.phiro.sabercraft.saber.SaberVariant;

/**
 * Phase 10: Ezra Bridger. Choosing him puts on his look and gives the saber in hand his hilt, the
 * blue blade and his improvised style. The stun blaster in his hilt fires on right-click, with the
 * blade lit: a real bolt on the server, drawn as an Ezra bolt on the client, the target slowed,
 * the sword not worn down, and every client plays the shot from the hilt. An attack still swings
 * the saber as before.
 *
 * <p>Screenshots: {@code build/run/clientGameTest/screenshots/ezra_*}.
 */
public class EzraGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		context.runOnClient(client -> client.options.renderDistance().set(4));

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
			server.runCommand("item replace entity @a weapon.mainhand with minecraft:netherite_sword");
			context.waitTicks(20);
			UUID self = context.computeOnClient(client -> client.player.getUUID());

			// The character, picked in the menu: his look, and his saber on the sword in hand.
			context.runOnClient(client -> SaberMenuScreen.open(client, SaberMenuScreen.Tab.CHARACTER));
			context.waitTicks(5);
			context.takeScreenshot("ezra_menu");
			context.clickScreenButton(Outfit.EZRA.translationKey());
			context.waitTicks(10);
			context.runOnClient(client -> client.gui.setScreen(null));
			context.waitTicks(5);
			check(server.computeOnServer(s -> OutfitSync.outfitOf(self)).map(Outfit.EZRA.id()::equals).orElse(false), "the server knows Ezra is chosen");
			ItemStack saber = server.computeOnServer(s -> held(s, self).copy());
			check(SaberVariant.EZRA.equals(SaberVariant.hiltOf(saber)), "Ezra's hilt on the saber");
			check(SaberVariant.BLUE.equals(SaberVariant.colorOf(saber)), "the blue blade");
			check(SaberVariant.IMPROVISED.equals(SaberVariant.styleOf(saber)), "the improvised style");
			check(context.computeOnClient(client -> OutfitClient.look(client.player.getUUID()) != null), "the client shows Ezra's skin");
			check(context.computeOnClient(client -> SaberProfiles.isSaber(client.player.getMainHandItem())), "the client sees a saber");
			check(Outfit.CHARACTERS.contains(Outfit.EZRA), "Ezra is in the character menu");
			context.waitTicks(40);
			context.takeScreenshot("ezra_first_person");
			context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
			context.waitTicks(10);
			context.takeScreenshot("ezra_front");
			context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			context.waitTicks(10);
			context.takeScreenshot("ezra_back");
			context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
			context.waitTicks(5);

			// The stun blaster in the hilt.
			server.runCommand("summon minecraft:zombie 0 ~ -12 {NoAI:1b,PersistenceRequired:1b,Silent:1b,attributes:[{id:\"minecraft:max_health\",base:100}],Health:100f}");
			context.getInput().lookAt(180.0F, 2.0F);
			context.waitTicks(10);
			float healthBefore = server.computeOnServer(s -> zombie(s).getHealth());
			int wearBefore = saber.getDamageValue();
			context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
			context.waitTicks(1);
			int bolts = server.computeOnServer(s -> s.overworld().getEntitiesOfClass(BlasterBolt.class, new AABB(-30, -64, -30, 30, 320, 30)).size());
			check(bolts == 1, "the right-click fired the stun blaster in the hilt (" + bolts + " bolt)");
			check(server.computeOnServer(s -> Blasters.heat(self)) > 0.0F, "the hilt blaster heats up like a blaster");
			context.waitTicks(1);
			boolean seen = context.computeOnClient(client -> {
				for (var entity : client.level.entitiesForRendering()) {
					if (entity instanceof Snowball snowball && BlasterBoltRenderer.typeOf(snowball) == BlasterType.EZRA) {
						return true;
					}
				}
				return false;
			});
			check(seen, "the client draws an Ezra bolt");
			check(context.computeOnClient(client -> {
				CombatState combat = SaberTracker.INSTANCE.combat(client.player);
				return combat != null && combat.move() != null && SaberAbilityClient.MOVE_HILT_SHOT.equals(combat.move().id());
			}), "the shot from the hilt is played");
			check(context.computeOnClient(client -> !client.player.swinging), "the shot does not swing the saber");
			check(context.computeOnClient(client -> {
				var state = SaberTracker.INSTANCE.state(client.player, net.minecraft.world.InteractionHand.MAIN_HAND);
				return state != null && state.isActive();
			}), "the blade stays lit");
			context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
			context.waitTicks(1);
			context.takeScreenshot("ezra_hilt_shot");
			context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
			context.waitTicks(15);
			check(server.computeOnServer(s -> zombie(s).getHealth()) < healthBefore, "the stun bolt hit the zombie");
			check(server.computeOnServer(s -> zombie(s).hasEffect(MobEffects.SLOWNESS)), "the hit stuns (slowness)");
			check(server.computeOnServer(s -> held(s, self).getDamageValue()) == wearBefore, "the hilt blaster does not wear the sword down");

			// An attack is still a sword attack with a swing.
			context.waitTicks(20);
			context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
			context.waitTicks(2);
			check(context.computeOnClient(client -> {
				CombatState combat = SaberTracker.INSTANCE.combat(client.player);
				return combat != null && combat.move() != null && combat.move().id().getPath().startsWith("imp_");
			}), "an attack plays a move of the improvised style");
			context.waitTicks(30);

			// The sprint and jump moves of the style.
			context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
			context.waitTicks(5);
			context.takeScreenshot("ezra_style");
			context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
			context.runOnClient(client -> OutfitClient.choose(""));
			context.waitTicks(10);
		}
	}

	private static ServerPlayer player(MinecraftServer server, UUID self) {
		return server.getPlayerList().getPlayer(self);
	}

	private static ItemStack held(MinecraftServer server, UUID self) {
		return player(server, self).getMainHandItem();
	}

	private static LivingEntity zombie(MinecraftServer server) {
		return server.overworld().getEntitiesOfClass(Zombie.class, new AABB(-30, -64, -30, 30, 320, 30), LivingEntity::isAlive).getFirst();
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}

		SaberCraft.LOGGER.info("OK: {}", message);
	}
}
