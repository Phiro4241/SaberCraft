package com.phiro.sabercraft.gametest;

import java.util.UUID;

import org.lwjgl.glfw.GLFW;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import net.minecraft.client.CameraType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.AABB;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.ability.SaberFinishers;
import com.phiro.sabercraft.advancement.SaberAdvancements;
import com.phiro.sabercraft.client.render.HitMarks;
import com.phiro.sabercraft.client.saber.SaberTracker;

/**
 * Phase 11: saber hit feedback and finishing blows, through real left-clicks of the player.
 *
 * <p>A hit leaves a glowing mark on the zombie (only on the client's side, the zombie itself is
 * unchanged) that is gone after a few seconds. The blow that kills is a finisher: the server
 * counts one, the client gets the ring and the flash once, the advancement comes, the single
 * player slow motion starts and ends with the normal tick rate back. A second kill right after
 * is no finisher (cooldown), a kill with a plain sword never is. On a real dedicated server each
 * client sees a finisher exactly once and there is no slow motion.
 *
 * <p>Screenshots: {@code hit_marks_*}, {@code finisher_*}.
 */
public class SaberHitGameTest implements FabricClientGameTest {
	private static final AABB AREA = new AABB(-30, -64, -30, 30, 320, 30);

	@Override
	public void runTest(ClientGameTestContext context) {
		context.runOnClient(client -> client.options.renderDistance().set(4));

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
			server.runCommand("item replace entity @a weapon.mainhand with minecraft:netherite_sword");
			context.waitTicks(40);
			UUID self = context.computeOnClient(client -> client.player.getUUID());
			check(context.computeOnClient(client -> {
				var state = SaberTracker.INSTANCE.state(client.player, InteractionHand.MAIN_HAND);
				return state != null && state.isActive();
			}), "the saber is lit");

			marks(context, server, self);
			finisher(context, server, self);
			noFinisher(context, server, self);
		}

		dedicatedServer(context);
	}

	private static void marks(ClientGameTestContext context, TestServerContext server, UUID self) {
		summon(server, "husk", -2.0, 200);
		context.getInput().lookAt(180.0F, 10.0F);
		context.waitTicks(10);
		int before = context.computeOnClient(client -> HitMarks.added());
		hit(context);
		context.waitFor(client -> HitMarks.added() > before, 20);
		check(context.computeOnClient(client -> HitMarks.added()) > before, "a saber hit leaves a mark");
		check(context.computeOnClient(client -> HitMarks.marksOn(zombie(client.level))) >= 1, "the mark sits on the zombie");
		check(server.computeOnServer(s -> zombie(s.overworld()).getHealth()) < 200.0F, "the zombie was hit");
		context.waitTicks(15);
		hit(context);
		// Step aside so the marks are not behind the blade, and look at them.
		server.runCommand("tp @a 1.6 ~ 0.2");
		context.getInput().lookAt(150.0F, 25.0F);
		context.waitTicks(10);
		context.takeScreenshot("hit_marks_day");
		server.runCommand("time set midnight");
		context.waitTicks(3);
		context.takeScreenshot("hit_marks_night");
		server.runCommand("time set day");
		server.runCommand("tp @a 0 ~ 0 180 0");
		context.getInput().lookAt(180.0F, 10.0F);
		context.waitTicks(100);
		check(context.computeOnClient(client -> HitMarks.marksOn(zombie(client.level))) == 0, "after a few seconds the marks are gone");
		server.runCommand("kill @e[type=husk]");
		context.waitTicks(5);
	}

	private static void finisher(ClientGameTestContext context, TestServerContext server, UUID self) {
		summon(server, "husk", -2.0, 1);
		server.runCommand("time set midnight");
		context.waitTicks(25);
		int serverBefore = server.computeOnServer(s -> SaberFinishers.count());
		int slowBefore = server.computeOnServer(s -> SaberFinishers.slowMotions());
		int clientBefore = context.computeOnClient(client -> HitMarks.finishersSeen());
		check(!server.computeOnServer(s -> SaberAdvancements.has(me(s, self), SaberAdvancements.FINISHER)), "no finisher advancement yet");
		hit(context);
		check(server.computeOnServer(s -> SaberFinishers.count()) == serverBefore + 1, "the killing blow is one finisher on the server");
		check(server.computeOnServer(s -> SaberFinishers.slowMotions()) == slowBefore + 1, "single player: the slow motion starts");
		check(context.computeOnClient(client -> HitMarks.flash()) > 0.0F, "the screen flash runs");
		context.takeScreenshot("finisher_ring_night");
		check(context.computeOnClient(client -> HitMarks.finishersSeen()) == clientBefore + 1, "the client got the finisher exactly once");
		check(context.computeOnClient(client -> HitMarks.ringShowing()), "the finisher ring shows");
		check(server.computeOnServer(s -> SaberAdvancements.has(me(s, self), SaberAdvancements.FINISHER)), "the finisher advancement");
		context.waitTicks(30);
		check(server.computeOnServer(s -> s.tickRateManager().tickrate()) == 20.0F, "after the slow motion the game runs at 20 ticks again");
		check(context.computeOnClient(client -> client.player.isAlive() && !client.player.isUsingItem()), "the player kept control");

		// Right after: a second kill is no finisher (cooldown), and not every blow is one.
		SaberFinishersReset.reset(server);
		summon(server, "husk", -2.0, 1);
		context.waitTicks(10);
		hit(context);
		int first = server.computeOnServer(s -> SaberFinishers.count());
		// The first one has to be gone first (its death takes a second), the second comes in time.
		context.waitTicks(22);
		summon(server, "husk", -2.0, 1);
		context.waitTicks(3);
		hit(context);
		check(server.computeOnServer(s -> zombies(s).isEmpty()), "both zombies fell");
		check(server.computeOnServer(s -> SaberFinishers.count()) == first, "a second kill within two seconds is no finisher");
		server.runCommand("time set day");
		context.waitTicks(40);
	}

	private static void noFinisher(ClientGameTestContext context, TestServerContext server, UUID self) {
		SaberFinishersReset.reset(server);
		server.runCommand("item replace entity @a weapon.mainhand with minecraft:iron_sword");
		summon(server, "husk", -2.0, 1);
		context.waitTicks(15);
		int before = server.computeOnServer(s -> SaberFinishers.count());
		hit(context);
		check(server.computeOnServer(s -> zombies(s).isEmpty()), "the plain sword killed the zombie");
		check(server.computeOnServer(s -> SaberFinishers.count()) == before, "a plain sword never makes a finisher");
		server.runCommand("item replace entity @a weapon.mainhand with minecraft:netherite_sword");
		context.waitTicks(30);
	}

	private static void dedicatedServer(ClientGameTestContext context) {
		try (TestDedicatedServerContext server = context.worldBuilder().createServer()) {
			try (var connection = server.connect()) {
				connection.waitForChunksDownload();
				context.waitTicks(40);
				server.runCommand("gamerule spawn_mobs false");
				server.runCommand("gamemode survival @a");
				server.runCommand("kill @e[type=!player]");
				server.runCommand("item replace entity @a weapon.mainhand with minecraft:netherite_sword");
				context.waitTicks(40);
				var pos = context.computeOnClient(client -> client.player.position());
				server.runCommand("summon minecraft:husk " + pos.x + " " + pos.y + " " + (pos.z - 2.0)
						+ " {NoAI:1b,PersistenceRequired:1b,Silent:1b,Health:1f}");
				context.getInput().lookAt(180.0F, 10.0F);
				context.waitTicks(20);
				int before = context.computeOnClient(client -> HitMarks.finishersSeen());
				int slowBefore = server.computeOnServer(s -> SaberFinishers.slowMotions());
				hit(context);
				context.waitTicks(5);
				check(context.computeOnClient(client -> HitMarks.finishersSeen()) == before + 1, "dedicated server: the client sees the finisher exactly once");
				check(server.computeOnServer(s -> SaberFinishers.slowMotions()) == slowBefore && server.computeOnServer(s -> s.tickRateManager().tickrate()) == 20.0F,
						"dedicated server: no slow motion");
				context.takeScreenshot("finisher_dedicated_server");
			}
		}
	}

	private static void hit(ClientGameTestContext context) {
		// The zombie under the crosshair and the attack fully charged, like a player would wait.
		context.waitFor(client -> client.crosshairPickEntity instanceof Zombie zombie && zombie.isAlive()
				&& client.player.getAttackStrengthScale(0.0F) >= 1.0F, 100);
		context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitTicks(3);
	}

	private static void summon(TestServerContext server, String type, double z, int health) {
		server.runCommand("summon minecraft:" + type + " 0 ~ " + z + " {NoAI:1b,PersistenceRequired:1b,Silent:1b,attributes:[{id:\"minecraft:max_health\",base:"
				+ Math.max(20, health) + "},{id:\"minecraft:knockback_resistance\",base:1}],Health:" + health + "f}");
	}

	private static java.util.List<Zombie> zombies(MinecraftServer server) {
		return server.overworld().getEntitiesOfClass(Zombie.class, AREA, LivingEntity::isAlive);
	}

	private static LivingEntity zombie(net.minecraft.world.level.Level level) {
		return level.getEntitiesOfClass(Zombie.class, AREA, Entity::isAlive).getFirst();
	}

	private static ServerPlayer me(MinecraftServer server, UUID self) {
		return server.getPlayerList().getPlayer(self);
	}

	/** Forgets the finisher cooldowns on the server thread. */
	private static final class SaberFinishersReset {
		static void reset(TestServerContext server) {
			server.runOnServer(s -> SaberFinishers.reset());
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}

		SaberCraft.LOGGER.info("OK: {}", message);
	}
}
