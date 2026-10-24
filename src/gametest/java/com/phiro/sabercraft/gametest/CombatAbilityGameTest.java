package com.phiro.sabercraft.gametest;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEventListener;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.ability.SaberAbilities;
import com.phiro.sabercraft.ability.SaberAbility;
import com.phiro.sabercraft.client.ability.SaberAbilityClient;
import com.phiro.sabercraft.client.combat.CombatState;
import com.phiro.sabercraft.client.saber.SaberTracker;
import com.phiro.sabercraft.client.select.SaberCustomizeScreen;
import com.phiro.sabercraft.saber.SaberVariant;

/**
 * Checks the Phase 6 combat features in a real client with a real (integrated or dedicated)
 * server:
 *
 * <ul>
 *   <li>light trails in all six colors, sparks on hits and on walls (screenshots),</li>
 *   <li>spin attack, force push and lunge: damage, motion and cooldowns decided by the server,</li>
 *   <li>defensive stance: parry right after raising it, partial block later, slowdown,</li>
 *   <li>the blade whirl of the double-bladed staff,</li>
 *   <li>switching weapons during a move, dying while guarding, survival and creative,</li>
 *   <li>the fighting style row of the V menu,</li>
 *   <li>the abilities over a real network connection to a dedicated server.</li>
 * </ul>
 *
 * <p>Screenshots: {@code build/run/clientGameTest/screenshots/combat_*}.
 */
public class CombatAbilityGameTest implements FabricClientGameTest {
	private static final String[] COLORS = {"red", "blue", "green", "purple", "yellow", "white"};

	private final SoundLog sounds = new SoundLog();
	private boolean audio;

	@Override
	public void runTest(ClientGameTestContext context) {
		context.runOnClient(client -> {
			client.options.renderDistance().set(4);
			client.getSoundManager().addListener(this.sounds);
		});

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
			// Animals from world generation would get in the way of the pig checks.
			server.runCommand("kill @e[type=!minecraft:player]");
			server.runCommand("tp @a 0 ~ 0 -90 5");
			give(server, "guardian", "blue", "flowing");
			server.runCommand("item replace entity @a hotbar.1 with minecraft:apple");
			select(context, 0);
			context.waitTicks(30);
			// Is there an audio device? Then playing a sound reaches the listener.
			this.sounds.clear();
			context.runOnClient(client -> client.getSoundManager().play(
					net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(com.phiro.sabercraft.client.sound.SaberSounds.GUARD, 1.0F)));
			context.waitTicks(2);
			this.audio = this.sounds.count("saber.guard") > 0;
			SaberCraft.LOGGER.info("Audio device available for sound checks: {}", this.audio);

			this.trails(context, server);
			this.hitSparks(context, server);
			this.wallSparks(context, server);
			this.spin(context, server);
			this.push(context, server);
			this.dash(context, server);
			this.guard(context, server);
			this.switchDuringMove(context);
			this.deathWhileGuarding(context, server);
			this.creative(context, server);
			this.staffWhirl(context, server);
			this.customizeMenu(context, server);
		}

		this.dedicatedServer(context);
	}

	private void trails(ClientGameTestContext context, TestServerContext server) {
		for (String color : COLORS) {
			give(server, "guardian", color, "flowing");
			context.waitTicks(10);
			context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
			context.runOnClient(client -> client.player.swing(InteractionHand.MAIN_HAND));
			// Mid-strike of the first combo move, when the trail is longest.
			context.waitTicks(4);
			context.takeScreenshot("combat_trail_fp_" + color);
			context.waitTicks(20);
		}

		context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));

		for (String color : new String[] {"red", "green"}) {
			give(server, "dark_lord", color, "heavy");
			context.waitTicks(10);
			context.runOnClient(client -> client.player.swing(InteractionHand.MAIN_HAND));
			context.waitTicks(5);
			context.takeScreenshot("combat_trail_tp_" + color);
			context.waitTicks(25);
		}

		context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
		give(server, "guardian", "blue", "flowing");
		context.waitTicks(10);
	}

	private void hitSparks(ClientGameTestContext context, TestServerContext server) {
		summonPig(server, 2.2, 0.0);
		context.waitTicks(10);
		this.sounds.clear();
		context.runOnClient(client -> {
			Pig pig = clientPig(client);
			client.gameMode.attack(client.player, pig);
			client.player.swing(InteractionHand.MAIN_HAND);
		});
		context.waitTicks(2);
		context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
		context.takeScreenshot("combat_hit_sparks");
		context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
		float health = pigHealth(server);
		check(health < 10.0F, "a saber hit hurts the pig: " + health);
		context.waitTicks(5);
		this.checkSound(this.sounds.count("saber.hit") >= 1, "a hit plays the hit sound: " + this.sounds);
		server.runCommand("kill @e[type=minecraft:pig]");
		context.waitTicks(30);
	}

	private void wallSparks(ClientGameTestContext context, TestServerContext server) {
		server.runCommand("fill 2 ~ -2 2 ~2 2 minecraft:stone");
		context.waitTicks(5);
		context.getInput().lookAt(-90.0F, 30.0F);
		this.sounds.clear();
		context.runOnClient(client -> client.player.swing(InteractionHand.MAIN_HAND));
		// Particles appear one tick after the strike.
		context.waitTicks(5);
		context.takeScreenshot("combat_wall_sparks");
		context.waitTicks(10);
		this.checkSound(this.sounds.count("saber.scorch") == 1, "a blow against a wall scorches it once: " + this.sounds);
		server.runCommand("fill 2 ~ -2 2 ~2 2 minecraft:air");
		context.getInput().lookAt(-90.0F, 5.0F);
		context.waitTicks(25);
	}

	private void spin(ClientGameTestContext context, TestServerContext server) {
		summonPig(server, 1.8, 0.0);
		summonPig(server, -1.8, 0.0);
		context.waitTicks(25);
		this.sounds.clear();
		context.getInput().pressKey(SaberAbilityClient.SPIN_KEY);
		context.waitTicks(3);
		context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
		context.takeScreenshot("combat_spin_tp");
		context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
		context.waitTicks(6);
		List<Float> health = pigHealths(server);
		String where = server.computeOnServer(s -> {
			StringBuilder text = new StringBuilder("player " + player(s).position());

			for (Pig pig : s.overworld().getEntities(EntityTypes.PIG, pig -> pig.isAlive())) {
				text.append(", pig ").append(pig.position());
			}

			return text.toString();
		});
		check(health.size() == 2 && health.stream().allMatch(h -> h < 10.0F), "the spin hits pigs in front and behind: " + health + " " + where);
		int cooldown = cooldown(context, SaberAbility.SPIN);
		check(cooldown > 60, "the spin starts its cooldown: " + cooldown);
		this.checkSound(this.sounds.count("saber.spin") == 1, "the spin plays one spin sound: " + this.sounds);

		// Pressed again during the cooldown: nothing happens.
		context.waitTicks(25);
		server.runCommand("effect give @e[type=minecraft:pig] minecraft:instant_health 1 5");
		context.waitTicks(5);
		List<Float> healed = pigHealths(server);
		context.getInput().pressKey(SaberAbilityClient.SPIN_KEY);
		context.waitTicks(10);
		check(pigHealths(server).equals(healed), "no second spin during the cooldown: " + healed + " -> " + pigHealths(server));
		server.runCommand("kill @e[type=minecraft:pig]");
		context.waitTicks(10);
	}

	private void push(ClientGameTestContext context, TestServerContext server) {
		summonPig(server, 2.5, 0.0);
		context.waitTicks(25);
		double before = server.computeOnServer(s -> pig(s).getX());
		this.sounds.clear();
		context.getInput().pressKey(SaberAbilityClient.PUSH_KEY);
		context.waitTicks(2);
		context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
		context.takeScreenshot("combat_push_tp");
		context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
		context.waitTicks(10);
		double after = server.computeOnServer(s -> pig(s).getX());
		check(after - before > 1.5, "the push throws the pig back: " + before + " -> " + after);
		check(pigHealth(server) == 10.0F, "the push does no damage");
		check(cooldown(context, SaberAbility.PUSH) > 100, "the push starts its cooldown");
		this.checkSound(this.sounds.count("saber.push") == 1, "the push plays its sound: " + this.sounds);
		server.runCommand("kill @e[type=minecraft:pig]");
		context.waitTicks(10);
	}

	private void dash(ClientGameTestContext context, TestServerContext server) {
		server.runCommand("tp @a 0 ~ 0 -90 5");
		context.waitTicks(5);
		double before = server.computeOnServer(s -> player(s).getX());
		context.getInput().pressKey(SaberAbilityClient.DASH_KEY);
		context.waitTicks(2);
		context.takeScreenshot("combat_dash_fp");
		context.waitTicks(10);
		double after = server.computeOnServer(s -> player(s).getX());
		check(after - before > 1.5, "the lunge moves the player forward: " + before + " -> " + after);
		check(cooldown(context, SaberAbility.DASH) > 50, "the lunge starts its cooldown");
		server.runCommand("tp @a 0 ~ 0 -90 5");
		context.waitTicks(10);
	}

	private void guard(ClientGameTestContext context, TestServerContext server) {
		summonPig(server, 2.5, 0.0);
		heal(server);
		context.waitTicks(25);

		// Right after raising the stance a frontal hit is parried completely.
		context.getInput().holdKey(SaberAbilityClient.GUARD_KEY);
		context.waitTicks(2);
		check(server.computeOnServer(s -> SaberAbilities.isGuarding(player(s))), "the server knows about the stance");
		check(server.computeOnServer(s -> player(s).getAttribute(Attributes.MOVEMENT_SPEED).hasModifier(SaberCraft.id("guard_slowdown"))),
				"the stance slows the player down");
		this.sounds.clear();
		server.runCommand("damage @p 4 minecraft:mob_attack by @e[type=minecraft:pig,limit=1]");
		context.waitTicks(2);
		float afterParry = server.computeOnServer(s -> player(s).getHealth());
		check(afterParry == 20.0F, "a hit right after raising the stance is parried: " + afterParry);
		context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
		context.takeScreenshot("combat_guard_parry_tp");
		context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
		context.takeScreenshot("combat_guard_fp");
		this.checkSound(this.sounds.count("saber.parry") == 1, "a parry plays the parry sound: " + this.sounds);

		// Later in the stance only part of the hit is blocked.
		context.waitTicks(25);
		server.runCommand("damage @p 4 minecraft:mob_attack by @e[type=minecraft:pig,limit=1]");
		context.waitTicks(2);
		float afterBlock = server.computeOnServer(s -> player(s).getHealth());
		check(afterBlock > 17.5F && afterBlock < 20.0F, "a later hit is blocked partly: " + afterBlock);
		this.checkSound(this.sounds.count("saber.clash") == 1, "a block plays the clash sound: " + this.sounds);

		// Normal attacks are not possible while guarding.
		check(context.computeOnClient(client -> SaberAbilityClient.isGuarding()), "the client knows about the stance");

		context.getInput().releaseKey(SaberAbilityClient.GUARD_KEY);
		context.waitTicks(3);
		check(!server.computeOnServer(s -> SaberAbilities.isGuarding(player(s))), "releasing the key lowers the stance");
		check(!server.computeOnServer(s -> player(s).getAttribute(Attributes.MOVEMENT_SPEED).hasModifier(SaberCraft.id("guard_slowdown"))),
				"the slowdown ends with the stance");

		// Without the stance the full hit lands.
		heal(server);
		context.waitTicks(25);
		server.runCommand("damage @p 4 minecraft:mob_attack by @e[type=minecraft:pig,limit=1]");
		context.waitTicks(2);
		float unguarded = server.computeOnServer(s -> player(s).getHealth());
		// "damage 4" from a mob is 3 on easy difficulty.
		check(unguarded == 17.0F, "without the stance the hit lands fully: " + unguarded);

		// A hit from behind is never blocked.
		heal(server);
		context.waitTicks(25);
		context.getInput().holdKey(SaberAbilityClient.GUARD_KEY);
		context.waitTicks(2);
		context.getInput().lookAt(90.0F, 5.0F);
		context.waitTicks(10);
		server.runCommand("damage @p 4 minecraft:mob_attack by @e[type=minecraft:pig,limit=1]");
		context.waitTicks(2);
		float fromBehind = server.computeOnServer(s -> player(s).getHealth());
		check(fromBehind == 17.0F, "the stance does not block hits from behind: " + fromBehind);
		context.getInput().releaseKey(SaberAbilityClient.GUARD_KEY);
		context.getInput().lookAt(-90.0F, 5.0F);
		heal(server);
		server.runCommand("kill @e[type=minecraft:pig]");
		context.waitTicks(30);
	}

	private void switchDuringMove(ClientGameTestContext context) {
		context.runOnClient(client -> client.player.swing(InteractionHand.MAIN_HAND));
		context.waitTicks(2);
		select(context, 1);
		context.waitTicks(20);
		context.takeScreenshot("combat_switched_to_apple");
		boolean noMove = context.computeOnClient(client -> {
			CombatState state = SaberTracker.INSTANCE.combat(client.player);
			return state == null || state.move() == null;
		});
		check(noMove, "a move ends when the saber is put away mid-swing");
		select(context, 0);
		context.waitTicks(30);
		context.runOnClient(client -> client.player.swing(InteractionHand.MAIN_HAND));
		context.waitTicks(3);
		check(context.computeOnClient(client -> SaberTracker.INSTANCE.combat(client.player).move() != null), "attacks work again after switching back");
		context.waitTicks(20);
	}

	private void deathWhileGuarding(ClientGameTestContext context, TestServerContext server) {
		server.runOnServer(s -> SaberAbilities.reset(null));
		context.getInput().holdKey(SaberAbilityClient.GUARD_KEY);
		context.waitTicks(3);
		check(server.computeOnServer(s -> SaberAbilities.isGuarding(player(s))), "guarding before death");
		server.runCommand("kill @a");
		context.waitTicks(20);
		context.getInput().releaseKey(SaberAbilityClient.GUARD_KEY);
		context.waitTicks(20);
		check(!server.computeOnServer(s -> SaberAbilities.isGuarding(player(s))), "no stance after respawning");
		check(!server.computeOnServer(s -> player(s).getAttribute(Attributes.MOVEMENT_SPEED).hasModifier(SaberCraft.id("guard_slowdown"))),
				"no slowdown after respawning");
		check(context.computeOnClient(client -> client.player.isAlive()), "the player respawned");
		server.runCommand("tp @a 0 ~ 0 -90 5");
		select(context, 0);
		context.waitTicks(40);
		context.takeScreenshot("combat_after_respawn");
		check(context.computeOnClient(client -> {
			CombatState state = SaberTracker.INSTANCE.combat(client.player);
			return state != null && !state.isGuarding();
		}), "the respawned player is not stuck in the stance");
	}

	private void creative(ClientGameTestContext context, TestServerContext server) {
		server.runCommand("gamemode creative @a");
		server.runOnServer(s -> SaberAbilities.reset(null));
		context.runOnClient(client -> resetClientCooldowns());
		summonPig(server, 2.0, 0.0);
		context.waitTicks(25);
		context.getInput().pressKey(SaberAbilityClient.SPIN_KEY);
		context.waitTicks(10);
		check(pigHealth(server) < 10.0F, "the spin also works in creative mode");
		server.runCommand("kill @e[type=minecraft:pig]");
		server.runCommand("gamemode survival @a");
		context.waitTicks(10);
	}

	private void staffWhirl(ClientGameTestContext context, TestServerContext server) {
		give(server, "double", "purple", "staff");
		server.runOnServer(s -> SaberAbilities.reset(null));
		context.runOnClient(client -> resetClientCooldowns());
		summonPig(server, 2.2, 0.0);
		context.waitTicks(30);
		context.takeScreenshot("combat_staff_idle_fp");
		context.getInput().pressKey(SaberAbilityClient.SPIN_KEY);

		for (int tick : new int[] {3, 4, 4}) {
			context.waitTicks(tick);
			context.takeScreenshot("combat_staff_whirl_fp_" + tick);
		}

		context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
		context.waitTicks(3);
		context.takeScreenshot("combat_staff_whirl_tp");
		context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
		context.waitTicks(15);
		float health = pigHealth(server);
		check(health < 10.0F, "the whirl hits the pig in front: " + health);
		check(cooldown(context, SaberAbility.SPIN) > 100, "the whirl has the longer cooldown");
		server.runCommand("kill @e[type=minecraft:pig]");

		// Attacks with the staff use the staff moves, both blades stay visible in first person.
		context.waitTicks(10);
		context.runOnClient(client -> client.player.swing(InteractionHand.MAIN_HAND));
		context.waitTicks(4);
		context.takeScreenshot("combat_staff_attack_fp");
		context.waitTicks(20);
	}

	private void customizeMenu(ClientGameTestContext context, TestServerContext server) {
		give(server, "guardian", "green", "flowing");
		context.waitTicks(10);
		context.setScreen(() -> new SaberCustomizeScreen(InteractionHand.MAIN_HAND, Minecraft.getInstance().player.getMainHandItem()));
		context.waitTicks(2);
		context.clickScreenButton("sabercraft.style.heavy");
		context.waitTicks(1);
		context.takeScreenshot("combat_menu_style");
		context.clickScreenButton("sabercraft.customize.apply");
		context.waitTicks(5);
		Identifier style = server.computeOnServer(s -> SaberVariant.styleOf(player(s).getMainHandItem()));
		check(SaberVariant.HEAVY.equals(style), "the V menu changes the fighting style: " + style);
		check(server.computeOnServer(s -> SaberVariant.GUARDIAN.equals(SaberVariant.hiltOf(player(s).getMainHandItem()))), "the hilt stays");

		context.setScreen(() -> new SaberCustomizeScreen(InteractionHand.MAIN_HAND, Minecraft.getInstance().player.getMainHandItem()));
		context.waitTicks(2);
		context.clickScreenButton("sabercraft.hilt.double");
		context.waitTicks(1);
		context.takeScreenshot("combat_menu_staff");
		context.clickScreenButton("sabercraft.customize.apply");
		context.waitTicks(5);
		style = server.computeOnServer(s -> SaberVariant.styleOf(player(s).getMainHandItem()));
		check(SaberVariant.STAFF.equals(style), "the double hilt switches to the staff style: " + style);
	}

	private void dedicatedServer(ClientGameTestContext context) {
		try (TestDedicatedServerContext server = context.worldBuilder().createServer()) {
			try (var connection = server.connect()) {
				connection.waitForChunksDownload();
				context.waitTicks(40);
				server.runCommand("gamerule spawn_mobs false");
				server.runCommand("difficulty easy");
				server.runCommand("kill @e[type=!minecraft:player]");
				server.runCommand("tp @a 0 ~ 0 -90 5");
				give(server, "dark_lord", "red", "heavy");
				select(context, 0);
				connection.waitForServerboundPackets();
				context.waitTicks(40);
				summonPig(server, 2.2, 0.0);
				context.waitTicks(25);

				context.getInput().pressKey(SaberAbilityClient.SPIN_KEY);
				connection.waitForServerboundPackets();
				context.waitTicks(12);
				check(pigHealth(server) < 10.0F, "the spin hurts on a dedicated server");
				check(cooldown(context, SaberAbility.SPIN) > 60, "the dedicated server sends the cooldown");

				double before = server.computeOnServer(s -> pig(s).getX());
				context.getInput().pressKey(SaberAbilityClient.PUSH_KEY);
				connection.waitForServerboundPackets();
				context.waitTicks(12);
				double after = server.computeOnServer(s -> pig(s).getX());
				check(after - before > 1.0, "the push works on a dedicated server: " + before + " -> " + after);
				// Once it has landed, the client sees the pig where the server put it.
				for (int i = 0; i < 8; i++) {
					context.waitTicks(5);
					double serverX = server.computeOnServer(s -> pig(s).getX());
					double seenX = context.computeOnClient(client -> clientPig(client).getX());
					SaberCraft.LOGGER.info("pig after push: server {} client {}", serverX, seenX);
				}

				after = server.computeOnServer(s -> pig(s).getX());
				double clientX = context.computeOnClient(client -> clientPig(client).getX());
				check(Math.abs(clientX - after) < 0.3, "the client sees the pushed pig in the same place: " + clientX + " / " + after);

				context.getInput().holdKey(SaberAbilityClient.GUARD_KEY);
				connection.waitForServerboundPackets();
				context.waitTicks(3);
				check(server.computeOnServer(s -> SaberAbilities.isGuarding(player(s))), "the stance works on a dedicated server");
				context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
				context.takeScreenshot("combat_dedicated_guard_tp");
				context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
				context.getInput().releaseKey(SaberAbilityClient.GUARD_KEY);
				connection.waitForServerboundPackets();
				context.waitTicks(3);
				check(!server.computeOnServer(s -> SaberAbilities.isGuarding(player(s))), "the stance ends on a dedicated server");
			}
		}
	}

	private static void give(TestServerContext server, String hilt, String color, String style) {
		server.runCommand("item replace entity @a hotbar.0 with minecraft:netherite_sword[minecraft:custom_model_data={strings:[\"sabercraft:"
				+ hilt + "\",\"sabercraft:" + color + "\",\"sabercraft:" + style + "\"]}]");
	}

	private static void summonPig(TestServerContext server, double x, double z) {
		// With AI, so knockback and pushes move it, but without walking speed, so it stays put.
		server.runCommand("summon minecraft:pig " + x + " ~ " + z + " {PersistenceRequired:1b,Silent:1b,attributes:[{id:\"minecraft:movement_speed\",base:0.0}]}");
	}

	private static void heal(TestServerContext server) {
		server.runOnServer(s -> player(s).setHealth(20.0F));
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

	private static List<Float> pigHealths(TestServerContext server) {
		return server.computeOnServer(s -> {
			List<Float> health = new ArrayList<>();

			for (Pig pig : s.overworld().getEntities(EntityTypes.PIG, pig -> pig.isAlive())) {
				health.add(pig.getHealth());
			}

			return health;
		});
	}

	private static Pig clientPig(Minecraft client) {
		for (var entity : client.level.entitiesForRendering()) {
			if (entity instanceof Pig pig && pig.isAlive()) {
				return pig;
			}
		}

		throw new AssertionError("No pig on the client");
	}

	private static int cooldown(ClientGameTestContext context, SaberAbility ability) {
		return context.computeOnClient(client -> {
			SaberAbilityClient.Cooldown cooldown = SaberAbilityClient.cooldown(ability);
			return cooldown != null ? cooldown.left() : 0;
		});
	}

	private static void resetClientCooldowns() {
		SaberAbilityClient.resetForTests();
	}

	private static void select(ClientGameTestContext context, int slot) {
		context.runOnClient(client -> client.player.getInventory().setSelectedSlot(slot));
	}

	/**
	 * Sound checks only work if the client has an audio device. Without one (as on a build
	 * server) Minecraft plays nothing and the checks are skipped, which the log says.
	 */
	private void checkSound(boolean condition, String message) {
		if (!this.audio) {
			SaberCraft.LOGGER.info("SKIPPED (no audio device): {}", message);
			return;
		}

		check(condition, message);
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}

		SaberCraft.LOGGER.info("OK: {}", message);
	}

	/** Records which SaberCraft sounds the sound engine was asked to play. */
	private static final class SoundLog implements SoundEventListener {
		private final List<String> played = new ArrayList<>();

		@Override
		public void onPlaySound(SoundInstance sound, WeighedSoundEvents soundEvent, float range) {
			if (sound.getIdentifier().getNamespace().equals(SaberCraft.MOD_ID)) {
				this.played.add(sound.getIdentifier().getPath());
			}
		}

		int count(String path) {
			return (int) this.played.stream().filter(path::equals).count();
		}

		void clear() {
			this.played.clear();
		}

		@Override
		public String toString() {
			return this.played.toString();
		}
	}
}
