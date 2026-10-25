package com.phiro.sabercraft.gametest;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import com.mojang.authlib.GameProfile;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.entity.FakePlayer;

import net.minecraft.client.CameraType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.ability.AbilityConfig;
import com.phiro.sabercraft.duel.Duels;

/**
 * Phase 10: lightsaber duels, only by choice. The second duelist is a server-side player entity
 * ("Rival", a Fabric fake player), because a client test has one real player; the real player
 * answers through the real commands and the chat.
 *
 * <ul>
 *   <li>A challenge changes nothing until it is accepted; it shows up in the chat with buttons.
 *       {@code ignore} turns challenges off, and you cannot challenge yourself.</li>
 *   <li>Countdown: the two cannot hurt each other. Fight: a blow that would kill ends the round,
 *       best of three, both healed between rounds.</li>
 *   <li>Leaving gives the duel to the other; dying to something else, going too far apart, and
 *       switching duels off end it without a winner.</li>
 *   <li>Outside a duel the rival's blows are normal damage: the duel does not protect or change
 *       anything else.</li>
 * </ul>
 *
 * <p>Screenshots: {@code build/run/clientGameTest/screenshots/duel_*}.
 */
public class DuelGameTest implements FabricClientGameTest {
	private static final UUID RIVAL = UUID.fromString("4f1d6c2a-7b0e-4c1e-9a3b-5d2f8e6a1c01");
	/** The system messages the client got, as plain text. */
	private final List<String> chat = new CopyOnWriteArrayList<>();

	@Override
	public void runTest(ClientGameTestContext context) {
		context.runOnClient(client -> client.options.renderDistance().set(4));
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> this.chat.add(message.getString()));

		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			context.waitTicks(100);
			TestServerContext server = singleplayer.getServer();
			server.runCommand("gamerule advance_time false");
			server.runCommand("gamerule spawn_mobs false");
			server.runCommand("gamerule immediate_respawn true");
			server.runCommand("gamerule send_command_feedback false");
			server.runCommand("time set day");
			server.runCommand("difficulty normal");
			server.runCommand("gamemode survival @a");
			server.runCommand("tp @a 0 ~ 0 180 0");
			server.runCommand("item replace entity @a weapon.mainhand with minecraft:netherite_sword");
			context.waitTicks(20);
			UUID self = context.computeOnClient(client -> client.player.getUUID());

			// The rival: a player entity in the world, three blocks in front.
			server.runOnServer(s -> {
				ServerLevel level = s.overworld();
				ServerPlayer me = me(s, self);
				FakePlayer rival = new Rival(level, new GameProfile(RIVAL, "Rival"));
				rival.snapTo(me.getX(), me.getY(), me.getZ() - 3.0, 0.0F, 0.0F);
				level.addNewPlayer(rival);
			});
			context.waitTicks(10);

			this.challenges(context, server, self);
			this.bestOfThree(context, server, self);
			this.endings(context, server, self);
		}
	}

	private void challenges(ClientGameTestContext context, TestServerContext server, UUID self) {
		check(server.computeOnServer(s -> Duels.challenge(me(s, self), me(s, self))) == Duels.ChallengeResult.SELF, "you cannot challenge yourself");

		// Ignoring, with the real command.
		context.runOnClient(client -> client.player.connection.sendCommand("sabercraft duel ignore"));
		context.waitTicks(5);
		check(server.computeOnServer(s -> Duels.challenge(rival(s), me(s, self))) == Duels.ChallengeResult.IGNORING, "with ignore on nobody can challenge you");
		context.runOnClient(client -> client.player.connection.sendCommand("sabercraft duel ignore"));
		context.waitTicks(5);

		// A challenge: a message with buttons, and nothing else changes.
		check(server.computeOnServer(s -> Duels.challenge(rival(s), me(s, self))) == Duels.ChallengeResult.SENT, "the rival challenges you");
		context.waitTicks(5);
		check(context.computeOnClient(client -> this.chat.stream().anyMatch(line -> line.contains("Rival") && line.contains("[Accept]"))),
				"the challenge shows up in your chat");
		check(server.computeOnServer(s -> Duels.duelOf(self)).isEmpty(), "a challenge alone does not start a duel");
		check(server.computeOnServer(s -> Duels.challenge(rival(s), me(s, self))) == Duels.ChallengeResult.ALREADY_SENT, "no second challenge on top");
		context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
		context.takeScreenshot("duel_challenge");

		// Declining, with the real command; the next challenge is accepted.
		context.runOnClient(client -> client.player.connection.sendCommand("sabercraft duel decline"));
		context.waitTicks(5);
		check(!server.computeOnServer(s -> Duels.hasChallenge(RIVAL, self)), "declining removes the challenge");
		check(server.computeOnServer(s -> Duels.duelOf(self)).isEmpty(), "declining starts no duel");
	}

	private void bestOfThree(ClientGameTestContext context, TestServerContext server, UUID self) {
		check(server.computeOnServer(s -> Duels.challenge(rival(s), me(s, self))) == Duels.ChallengeResult.SENT, "the rival challenges you again");
		context.runOnClient(client -> client.player.connection.sendCommand("sabercraft duel accept"));
		context.waitTicks(3);
		check(server.computeOnServer(s -> Duels.duelOf(self).map(d -> d.phase() == Duels.Phase.COUNTDOWN && d.round() == 1).orElse(false)),
				"accepting starts round 1 with a countdown");
		context.waitTicks(4);
		context.takeScreenshot("duel_countdown");
		context.waitTicks(11);

		// During the countdown nobody gets hurt.
		float before = server.computeOnServer(s -> me(s, self).getHealth());
		server.runOnServer(s -> me(s, self).hurtServer(s.overworld(), s.overworld().damageSources().playerAttack(rival(s)), 6.0F));
		check(server.computeOnServer(s -> me(s, self).getHealth()) == before, "during the countdown the rival's blow does nothing");

		context.waitTicks(Duels.COUNTDOWN_TICKS);
		check(server.computeOnServer(s -> Duels.duelOf(self).map(d -> d.phase() == Duels.Phase.FIGHT).orElse(false)), "after the countdown: fight");
		context.takeScreenshot("duel_fight");

		// Round 1: a normal blow hurts; the deadly one ends the round instead of killing.
		server.runOnServer(s -> me(s, self).hurtServer(s.overworld(), s.overworld().damageSources().playerAttack(rival(s)), 3.0F));
		check(server.computeOnServer(s -> me(s, self).getHealth()) < before, "in the fight a blow hurts");
		server.runOnServer(s -> {
			ServerPlayer me = me(s, self);
			me.invulnerableTime = 0;
			me.hurtServer(s.overworld(), s.overworld().damageSources().playerAttack(rival(s)), 100.0F);
		});
		check(server.computeOnServer(s -> me(s, self).isAlive() && me(s, self).getHealth() == me(s, self).getMaxHealth()),
				"the deadly blow ends the round: you live, healed");
		check(server.computeOnServer(s -> Duels.duelOf(self).map(d -> d.wins(RIVAL) == 1 && d.round() == 2 && d.phase() == Duels.Phase.COUNTDOWN).orElse(false)),
				"round 1 goes to the rival, round 2 counts down");

		// Rounds 2 and 3 go to you.
		for (int round = 2; round <= 3; round++) {
			context.waitTicks(Duels.COUNTDOWN_TICKS + 2);
			server.runOnServer(s -> {
				ServerPlayer rival = rival(s);
				rival.invulnerableTime = 0;
				rival.hurtServer(s.overworld(), s.overworld().damageSources().playerAttack(me(s, self)), 100.0F);
			});
			check(server.computeOnServer(s -> rival(s).isAlive()), "round " + round + ": the rival is not killed either");
		}

		check(server.computeOnServer(s -> Duels.duelOf(self)).isEmpty(), "after two won rounds the duel is over");
		check(server.computeOnServer(s -> Duels.lastEnd(self)).orElse(null) == Duels.End.WON, "the duel was won");
		context.waitTicks(5);
		check(context.computeOnClient(client -> this.chat.stream().anyMatch(line -> line.contains("(1 : 2)"))),
				"the chat tells the score");
		context.takeScreenshot("duel_won");
		context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
	}

	private void endings(ClientGameTestContext context, TestServerContext server, UUID self) {
		// Leaving gives the duel to the other.
		this.startDuel(context, server, self);
		context.runOnClient(client -> client.player.connection.sendCommand("sabercraft duel leave"));
		context.waitTicks(3);
		check(server.computeOnServer(s -> Duels.duelOf(self)).isEmpty() && server.computeOnServer(s -> Duels.lastEnd(self)).orElse(null) == Duels.End.LEFT,
				"leaving ends the duel");

		// Too far apart.
		this.startDuel(context, server, self);
		server.runOnServer(s -> rival(s).teleportTo(0.0, rival(s).getY(), -100.0));
		context.waitTicks(3);
		check(server.computeOnServer(s -> Duels.lastEnd(self)).orElse(null) == Duels.End.TOO_FAR, "100 blocks apart ends the duel");
		server.runOnServer(s -> rival(s).teleportTo(me(s, self).getX(), me(s, self).getY(), me(s, self).getZ() - 3.0));

		// Switched off by the server.
		this.startDuel(context, server, self);
		server.runOnServer(s -> AbilityConfig.get().duelsEnabled = false);
		context.waitTicks(3);
		check(server.computeOnServer(s -> Duels.lastEnd(self)).orElse(null) == Duels.End.DISABLED, "switching duels off ends a running duel");
		check(server.computeOnServer(s -> Duels.challenge(rival(s), me(s, self))) == Duels.ChallengeResult.DISABLED, "and no new challenges");
		server.runOnServer(s -> AbilityConfig.get().duelsEnabled = true);

		// Dying to something else: a normal death, no winner.
		this.startDuel(context, server, self);
		context.waitTicks(Duels.COUNTDOWN_TICKS + 2);
		server.runOnServer(s -> {
			ServerPlayer me = me(s, self);
			me.invulnerableTime = 0;
			me.hurtServer(s.overworld(), s.overworld().damageSources().fellOutOfWorld(), 1000.0F);
		});
		context.waitTicks(10);
		check(server.computeOnServer(s -> Duels.lastEnd(self)).orElse(null) == Duels.End.DIED, "dying to something else ends the duel without a winner");
		check(server.computeOnServer(s -> Duels.duelOf(self)).isEmpty(), "no duel left");
		server.runCommand("tp @a 0 ~ 0 180 0");
		context.waitTicks(10);

		// Outside a duel the rival's blows are plain damage.
		float before = server.computeOnServer(s -> me(s, self).getHealth());
		server.runOnServer(s -> {
			ServerPlayer me = me(s, self);
			me.invulnerableTime = 0;
			me.hurtServer(s.overworld(), s.overworld().damageSources().playerAttack(rival(s)), 4.0F);
		});
		check(server.computeOnServer(s -> me(s, self).getHealth()) < before, "outside a duel nothing is held back or changed");
		server.runOnServer(s -> {
			me(s, self).setHealth(20.0F);
			rival(s).discard();
		});
	}

	private void startDuel(ClientGameTestContext context, TestServerContext server, UUID self) {
		check(server.computeOnServer(s -> Duels.challenge(rival(s), me(s, self))) == Duels.ChallengeResult.SENT, "a new challenge");
		context.runOnClient(client -> client.player.connection.sendCommand("sabercraft duel accept"));
		context.waitTicks(3);
		check(server.computeOnServer(s -> Duels.duelOf(self)).isPresent(), "a new duel");
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

	private static ServerPlayer me(MinecraftServer server, UUID self) {
		return server.getPlayerList().getPlayer(self);
	}

	private static ServerPlayer rival(MinecraftServer server) {
		return (ServerPlayer) server.overworld().getEntity(RIVAL);
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}

		SaberCraft.LOGGER.info("OK: {}", message);
	}
}
