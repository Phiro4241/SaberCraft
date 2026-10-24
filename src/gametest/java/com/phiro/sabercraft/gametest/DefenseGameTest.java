package com.phiro.sabercraft.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.pig.Pig;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.ability.AbilityConfig;
import com.phiro.sabercraft.ability.SaberAbilities;
import com.phiro.sabercraft.ability.SaberAbility;
import com.phiro.sabercraft.client.ability.SaberAbilityClient;
import com.phiro.sabercraft.client.combat.CombatMove;
import com.phiro.sabercraft.client.combat.CombatState;
import com.phiro.sabercraft.client.saber.SaberTracker;
import com.phiro.sabercraft.client.select.SaberCustomizeScreen;
import com.phiro.sabercraft.saber.SaberVariant;

/**
 * Checks the Phase 7 defense in a real client: parry with counter and an attacker thrown off
 * balance, the parry lockout against tapping the key, stamina for blocks and dodges, the broken
 * stance, the reaction animations and the two new fighting styles with their menu buttons.
 *
 * <p>Screenshots: {@code build/run/clientGameTest/screenshots/defense_*}.
 */
public class DefenseGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		context.runOnClient(client -> client.options.renderDistance().set(4));

		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			context.waitTicks(200);
			TestServerContext server = singleplayer.getServer();
			server.runCommand("gamerule advance_time false");
			server.runCommand("gamerule spawn_mobs false");
			server.runCommand("gamerule keep_inventory true");
			server.runCommand("gamerule immediate_respawn true");
			server.runCommand("difficulty easy");
			server.runCommand("time set day");
			server.runCommand("gamemode survival @a");
			server.runCommand("kill @e[type=!minecraft:player]");
			server.runCommand("tp @a 0 ~ 0 -90 5");
			give(server, "guardian", "blue", "flowing");
			context.runOnClient(client -> client.player.getInventory().setSelectedSlot(0));
			context.waitTicks(30);

			this.parryAndCounter(context, server);
			this.lockout(context, server);
			this.guardBreak(context, server);
			this.dodgeStamina(context, server);
			this.styles(context, server);
			this.menu(context, server);
		}
	}

	private void parryAndCounter(ClientGameTestContext context, TestServerContext server) {
		// A long counter window, so the full-strength attack below still falls into it.
		server.runOnServer(s -> AbilityConfig.get().counterTicks = 100);
		summonPig(server);
		reset(context, server);
		context.waitTicks(25);

		// A normal hit for comparison.
		context.runOnClient(client -> client.gameMode.attack(client.player, clientPig(client)));
		context.waitTicks(2);
		float normal = 100.0F - pigHealth(server);
		server.runOnServer(s -> pig(s).setHealth(100.0F));
		server.runCommand("tp @e[type=minecraft:pig] 2.5 ~ 0.5");
		context.waitTicks(25);

		context.getInput().holdKey(SaberAbilityClient.GUARD_KEY);
		context.waitTicks(2);
		server.runCommand("damage @p 4 minecraft:mob_attack by @e[type=minecraft:pig,limit=1]");
		context.waitTicks(1);
		check(server.computeOnServer(s -> player(s).getHealth()) == 20.0F, "the hit right after raising the stance is parried");
		check(server.computeOnServer(s -> pig(s).hasEffect(MobEffects.SLOWNESS) && pig(s).hasEffect(MobEffects.WEAKNESS)),
				"the parried attacker is off balance");
		check(server.computeOnServer(s -> SaberAbilities.hasCounter(player(s))), "a parry opens a counter");
		check("sabercraft:parry".equals(move(context)), "the parry plays its animation: " + move(context));
		context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
		context.takeScreenshot("defense_parry_tp");
		context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
		context.takeScreenshot("defense_parry_fp");
		context.getInput().releaseKey(SaberAbilityClient.GUARD_KEY);
		context.waitTicks(25);

		server.runOnServer(s -> pig(s).setHealth(100.0F));
		// Both knockbacks pushed the pig away: back into reach.
		server.runCommand("tp @e[type=minecraft:pig] 2.5 ~ 0.5");
		context.waitTicks(3);
		context.runOnClient(client -> client.gameMode.attack(client.player, clientPig(client)));
		context.waitTicks(2);
		float counter = 100.0F - pigHealth(server);
		check(counter > normal * 1.3F, "the counter hits harder: " + counter + " against " + normal);
		check(!server.computeOnServer(s -> SaberAbilities.hasCounter(player(s))), "the counter ends with the hit");
		server.runOnServer(s -> AbilityConfig.load());
		server.runCommand("kill @e[type=minecraft:pig]");
	}

	private void lockout(ClientGameTestContext context, TestServerContext server) {
		summonPig(server);
		reset(context, server);
		server.runOnServer(s -> AbilityConfig.get().guardCooldown = 0);
		context.waitTicks(25);
		context.getInput().holdKey(SaberAbilityClient.GUARD_KEY);
		context.waitTicks(3);
		context.getInput().releaseKey(SaberAbilityClient.GUARD_KEY);
		context.waitTicks(2);
		// Raised again right away: no parry window this time.
		context.getInput().holdKey(SaberAbilityClient.GUARD_KEY);
		context.waitTicks(2);
		check(server.computeOnServer(s -> SaberAbilities.isGuarding(player(s))), "the stance is up again");
		server.runCommand("damage @p 4 minecraft:mob_attack by @e[type=minecraft:pig,limit=1]");
		context.waitTicks(1);
		float health = server.computeOnServer(s -> player(s).getHealth());
		check(health < 20.0F && health > 17.0F, "tapping the key again quickly only blocks: " + health);
		check("sabercraft:guard_hit".equals(move(context)), "a block plays the block animation: " + move(context));
		context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
		context.takeScreenshot("defense_block_tp");
		context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
		context.getInput().releaseKey(SaberAbilityClient.GUARD_KEY);
		server.runOnServer(s -> {
			AbilityConfig.load();
			player(s).setHealth(20.0F);
		});
		server.runCommand("kill @e[type=minecraft:pig]");
		context.waitTicks(25);
	}

	private void guardBreak(ClientGameTestContext context, TestServerContext server) {
		summonPig(server);
		reset(context, server);
		context.waitTicks(25);
		context.getInput().holdKey(SaberAbilityClient.GUARD_KEY);
		context.waitTicks(15);
		server.runOnServer(s -> SaberAbilities.setStaminaForTests(player(s), 3.0F));
		context.waitTicks(2);
		context.takeScreenshot("defense_low_stamina_hud");
		server.runCommand("damage @p 4 minecraft:mob_attack by @e[type=minecraft:pig,limit=1]");
		context.waitTicks(1);
		float health = server.computeOnServer(s -> player(s).getHealth());
		check(health == 17.0F, "without stamina the hit breaks through: " + health);
		check(!server.computeOnServer(s -> SaberAbilities.isGuarding(player(s))), "the stance breaks");
		check("sabercraft:guard_break".equals(move(context)), "the broken stance plays its animation: " + move(context));
		check(!context.computeOnClient(client -> SaberAbilityClient.isGuarding()), "the client lowers the stance");
		context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
		context.takeScreenshot("defense_guard_break_tp");
		context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
		context.takeScreenshot("defense_guard_break_fp");
		int cooldown = context.computeOnClient(client -> {
			SaberAbilityClient.Cooldown left = SaberAbilityClient.cooldown(SaberAbility.GUARD);
			return left != null ? left.left() : 0;
		});
		check(cooldown > 30, "a broken stance takes longer to come back: " + cooldown);
		context.getInput().releaseKey(SaberAbilityClient.GUARD_KEY);
		server.runOnServer(s -> player(s).setHealth(20.0F));
		server.runCommand("kill @e[type=minecraft:pig]");
		context.waitTicks(25);
	}

	private void dodgeStamina(ClientGameTestContext context, TestServerContext server) {
		reset(context, server);
		server.runCommand("tp @a 0 ~ 0 -90 5");
		context.waitTicks(5);
		server.runOnServer(s -> SaberAbilities.setStaminaForTests(player(s), 5.0F));
		context.waitTicks(2);
		double before = server.computeOnServer(s -> player(s).getX());
		context.getInput().pressKey(SaberAbilityClient.DASH_KEY);
		context.waitTicks(15);
		double after = server.computeOnServer(s -> player(s).getX());
		check(Math.abs(after - before) < 0.1, "no dodge without stamina: " + before + " -> " + after);
		context.waitTicks(60);
		float stamina = context.computeOnClient(client -> SaberAbilityClient.stamina());
		check(stamina > 40.0F, "stamina comes back after a rest: " + stamina);
		context.getInput().pressKey(SaberAbilityClient.DASH_KEY);
		context.waitTicks(15);
		check(server.computeOnServer(s -> player(s).getX()) - after > 1.0, "with stamina back the dodge works again");
		check(context.computeOnClient(client -> SaberAbilityClient.stamina()) < stamina, "the dodge costs stamina");
	}

	private void styles(ClientGameTestContext context, TestServerContext server) {
		// Steadfast: the parry window is still open 8 ticks after raising the stance.
		give(server, "sentinel", "green", "defensive");
		server.runCommand("tp @a 0 ~ 0 -90 5");
		summonPig(server);
		reset(context, server);
		context.waitTicks(30);
		context.getInput().holdKey(SaberAbilityClient.GUARD_KEY);
		context.waitTicks(8);
		server.runCommand("damage @p 4 minecraft:mob_attack by @e[type=minecraft:pig,limit=1]");
		context.waitTicks(1);
		check(server.computeOnServer(s -> player(s).getHealth()) == 20.0F, "the steadfast style has a longer parry window");
		context.getInput().releaseKey(SaberAbilityClient.GUARD_KEY);
		context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
		context.waitTicks(25);
		context.takeScreenshot("defense_style_defensive_idle_tp");
		context.runOnClient(client -> client.player.swing(InteractionHand.MAIN_HAND));
		context.waitTicks(3);
		context.takeScreenshot("defense_style_defensive_attack_tp");
		context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
		context.waitTicks(20);
		context.takeScreenshot("defense_style_defensive_idle_fp");
		server.runCommand("kill @e[type=minecraft:pig]");

		// Agile: half the dodge cooldown.
		give(server, "standard", "yellow", "agile");
		reset(context, server);
		context.waitTicks(30);
		context.getInput().pressKey(SaberAbilityClient.DASH_KEY);
		context.waitTicks(3);
		int cooldown = context.computeOnClient(client -> SaberAbilityClient.cooldown(SaberAbility.DASH).total());
		check(cooldown == Math.round(AbilityConfig.get().dashCooldown * 0.5F), "the agile style dodges more often: " + cooldown);
		check("sabercraft:dodge_spin_step".equals(move(context)), "the agile style turns forward: " + move(context));
		context.waitTicks(20);
		context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
		context.waitTicks(10);
		context.takeScreenshot("defense_style_agile_idle_tp");
		context.runOnClient(client -> client.player.swing(InteractionHand.MAIN_HAND));
		context.waitTicks(3);
		context.takeScreenshot("defense_style_agile_attack_tp");
		context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
		context.waitTicks(20);
		context.takeScreenshot("defense_style_agile_idle_fp");
	}

	private void menu(ClientGameTestContext context, TestServerContext server) {
		give(server, "guardian", "blue", "flowing");
		context.waitTicks(10);
		context.setScreen(() -> new SaberCustomizeScreen(InteractionHand.MAIN_HAND, Minecraft.getInstance().player.getMainHandItem()));
		context.waitTicks(2);
		context.clickScreenButton("sabercraft.style.defensive");
		context.waitTicks(1);
		context.takeScreenshot("defense_menu_styles");
		context.clickScreenButton("sabercraft.customize.apply");
		context.waitTicks(5);
		Identifier style = server.computeOnServer(s -> SaberVariant.styleOf(player(s).getMainHandItem()));
		check(SaberVariant.DEFENSIVE.equals(style), "the V menu picks the steadfast style: " + style);
	}

	private static String move(ClientGameTestContext context) {
		return context.computeOnClient(client -> {
			CombatState state = SaberTracker.INSTANCE.combat(client.player);
			CombatMove move = state != null ? state.move() : null;
			return move != null ? move.id().toString() : "none";
		});
	}

	private static void reset(ClientGameTestContext context, TestServerContext server) {
		server.runOnServer(s -> {
			SaberAbilities.reset(null);
			player(s).setHealth(20.0F);
		});
		context.runOnClient(client -> SaberAbilityClient.resetForTests());
	}

	private static void give(TestServerContext server, String hilt, String color, String style) {
		server.runCommand("item replace entity @a hotbar.0 with minecraft:netherite_sword[minecraft:custom_model_data={strings:[\"sabercraft:"
				+ hilt + "\",\"sabercraft:" + color + "\",\"sabercraft:" + style + "\"]}]");
	}

	private static void summonPig(TestServerContext server) {
		server.runCommand("summon minecraft:pig 2.5 ~ 0.5 {PersistenceRequired:1b,Silent:1b,Health:100f,"
				+ "attributes:[{id:\"minecraft:movement_speed\",base:0.0},{id:\"minecraft:max_health\",base:100.0}]}");
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static Pig pig(MinecraftServer server) {
		return server.overworld().getEntities(EntityTypes.PIG, pig -> pig.isAlive()).getFirst();
	}

	private static float pigHealth(TestServerContext server) {
		return server.computeOnServer(s -> pig(s).getHealth());
	}

	private static Pig clientPig(Minecraft client) {
		for (var entity : client.level.entitiesForRendering()) {
			if (entity instanceof Pig pig && pig.isAlive()) {
				return pig;
			}
		}

		throw new AssertionError("No pig on the client");
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}

		SaberCraft.LOGGER.info("OK: {}", message);
	}
}
