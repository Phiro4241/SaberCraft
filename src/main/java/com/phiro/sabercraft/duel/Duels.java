package com.phiro.sabercraft.duel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;

import com.phiro.sabercraft.ability.AbilityConfig;

/**
 * Lightsaber duels between two players (Phase 10), always by choice:
 *
 * <ol>
 *   <li>{@code /sabercraft duel <player>} sends a challenge. The other player sees it in the chat
 *       with buttons to accept or decline; it runs out after a minute. Nobody is ever put into a
 *       duel without accepting, and {@code /sabercraft duel ignore} turns challenges off for you.</li>
 *   <li>After {@code /sabercraft duel accept}, a countdown of three seconds starts. During the
 *       countdown the two cannot hurt each other.</li>
 *   <li>Best of three: a blow that would kill the opponent ends the round instead. Both are healed,
 *       the next countdown starts; whoever wins two rounds wins the duel.</li>
 *   <li>Either player can leave at any time ({@code /sabercraft duel leave}, giving the duel to the
 *       other). Logging out, dying to something else, changing the dimension or going more than
 *       64 blocks apart ends the duel without a winner.</li>
 * </ol>
 *
 * <p>The duel only changes what happens between these two players: it never makes anybody attack,
 * never lets anybody hurt players outside the duel, and leaves PvP rules of the server as they are
 * (on a server with PvP off, duelists cannot hurt each other either). Operators can switch duels
 * off with {@code duels_enabled} in {@code abilities.json}. Everything is server side and uses
 * vanilla chat and titles, so players without the mod can take part as well.
 */
public final class Duels {
	/** Ticks a challenge stays open. */
	static final int CHALLENGE_TICKS = 60 * 20;
	/** Ticks of the countdown before each round. */
	public static final int COUNTDOWN_TICKS = 60;
	/** Rounds a player has to win. */
	public static final int WINS_NEEDED = 2;
	/** How far apart the duelists may be, in blocks. */
	static final double MAX_DISTANCE = 64.0;

	/** Where a duel is. */
	public enum Phase {
		COUNTDOWN, FIGHT
	}

	/** An open challenge. */
	private record Challenge(UUID from, UUID to, long until) {
	}

	/** A running duel. */
	public static final class Duel {
		private final UUID first;
		private final UUID second;
		private Phase phase = Phase.COUNTDOWN;
		private long phaseStart;
		private int firstWins;
		private int secondWins;
		private int round = 1;

		Duel(UUID first, UUID second, long now) {
			this.first = first;
			this.second = second;
			this.phaseStart = now;
		}

		public boolean involves(UUID player) {
			return this.first.equals(player) || this.second.equals(player);
		}

		public UUID opponent(UUID player) {
			return this.first.equals(player) ? this.second : this.first;
		}

		public Phase phase() {
			return this.phase;
		}

		public int round() {
			return this.round;
		}

		public int wins(UUID player) {
			return this.first.equals(player) ? this.firstWins : this.secondWins;
		}
	}

	/** Why a duel ended without a winner, or how it ended. */
	public enum End {
		WON, LEFT, LOGGED_OUT, DIED, TOO_FAR, DISABLED
	}

	private static final List<Challenge> CHALLENGES = new ArrayList<>();
	private static final List<Duel> DUELS = new ArrayList<>();
	private static final Set<UUID> IGNORING = new HashSet<>();
	/** The last finished duels by player, for tests and for the status command. */
	private static final Map<UUID, End> LAST_END = new HashMap<>();

	private Duels() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(Duels::tick);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(Duels::allowDamage);
		ServerLivingEntityEvents.ALLOW_DEATH.register(Duels::allowDeath);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> leftServer(server, handler.getPlayer().getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			CHALLENGES.clear();
			DUELS.clear();
			IGNORING.clear();
			LAST_END.clear();
		});
	}

	// Challenges ---------------------------------------------------------------------------------

	/** The outcome of a challenge, for the command feedback. */
	public enum ChallengeResult {
		SENT, DISABLED, SELF, IGNORING, BUSY, TARGET_BUSY, ALREADY_SENT
	}

	public static ChallengeResult challenge(ServerPlayer from, ServerPlayer to) {
		long now = from.level().getGameTime();

		if (!AbilityConfig.get().duelsEnabled) {
			return ChallengeResult.DISABLED;
		} else if (from.getUUID().equals(to.getUUID())) {
			return ChallengeResult.SELF;
		} else if (IGNORING.contains(to.getUUID())) {
			return ChallengeResult.IGNORING;
		} else if (duelOf(from.getUUID()).isPresent()) {
			return ChallengeResult.BUSY;
		} else if (duelOf(to.getUUID()).isPresent()) {
			return ChallengeResult.TARGET_BUSY;
		}

		CHALLENGES.removeIf(c -> c.until() < now);

		if (CHALLENGES.stream().anyMatch(c -> c.from().equals(from.getUUID()) && c.to().equals(to.getUUID()))) {
			return ChallengeResult.ALREADY_SENT;
		}

		CHALLENGES.add(new Challenge(from.getUUID(), to.getUUID(), now + CHALLENGE_TICKS));
		MutableComponent accept = Component.translatableWithFallback("sabercraft.duel.button.accept", "[Accept]")
				.withStyle(Style.EMPTY.withColor(ChatFormatting.GREEN).withBold(true)
						.withClickEvent(new ClickEvent.RunCommand("/sabercraft duel accept " + from.getGameProfile().name()))
						.withHoverEvent(new HoverEvent.ShowText(Component.translatableWithFallback("sabercraft.duel.button.accept.hover",
								"Accept the duel: best of three, starts after a countdown."))));
		MutableComponent decline = Component.translatableWithFallback("sabercraft.duel.button.decline", "[Decline]")
				.withStyle(Style.EMPTY.withColor(ChatFormatting.RED)
						.withClickEvent(new ClickEvent.RunCommand("/sabercraft duel decline " + from.getGameProfile().name())));
		to.sendSystemMessage(Component.translatableWithFallback("sabercraft.duel.challenged",
				"%s challenges you to a lightsaber duel (best of three). It is up to you: ", from.getDisplayName())
				.append(accept).append(" ").append(decline));
		return ChallengeResult.SENT;
	}

	/** Accepts the challenge from {@code from} (or the newest one when {@code from} is null). */
	public static boolean accept(MinecraftServer server, ServerPlayer player, @Nullable UUID from) {
		Optional<Challenge> challenge = open(player, from);

		if (challenge.isEmpty() || !AbilityConfig.get().duelsEnabled) {
			return false;
		}

		ServerPlayer challenger = player(server, challenge.get().from());
		CHALLENGES.remove(challenge.get());

		if (challenger == null || duelOf(challenger.getUUID()).isPresent() || duelOf(player.getUUID()).isPresent()) {
			return false;
		}

		// Other challenges of the two are void now.
		CHALLENGES.removeIf(c -> c.from().equals(player.getUUID()) || c.to().equals(player.getUUID())
				|| c.from().equals(challenger.getUUID()) || c.to().equals(challenger.getUUID()));
		Duel duel = new Duel(challenger.getUUID(), player.getUUID(), player.level().getGameTime());
		DUELS.add(duel);
		startRound(server, duel);
		return true;
	}

	public static boolean decline(MinecraftServer server, ServerPlayer player, @Nullable UUID from) {
		Optional<Challenge> challenge = open(player, from);

		if (challenge.isEmpty()) {
			return false;
		}

		CHALLENGES.remove(challenge.get());
		ServerPlayer challenger = player(server, challenge.get().from());

		if (challenger != null) {
			challenger.sendSystemMessage(Component.translatableWithFallback("sabercraft.duel.declined", "%s declined the duel.", player.getDisplayName()));
		}

		return true;
	}

	/** Turns receiving challenges off or on for a player; returns true when they are now ignored. */
	public static boolean toggleIgnore(ServerPlayer player) {
		if (IGNORING.remove(player.getUUID())) {
			return false;
		}

		IGNORING.add(player.getUUID());
		CHALLENGES.removeIf(c -> c.to().equals(player.getUUID()));
		return true;
	}

	private static Optional<Challenge> open(ServerPlayer player, @Nullable UUID from) {
		long now = player.level().getGameTime();
		CHALLENGES.removeIf(c -> c.until() < now);
		return CHALLENGES.stream()
				.filter(c -> c.to().equals(player.getUUID()) && (from == null || c.from().equals(from)))
				.reduce((a, b) -> b);
	}

	public static boolean hasChallenge(UUID from, UUID to) {
		return CHALLENGES.stream().anyMatch(c -> c.from().equals(from) && c.to().equals(to));
	}

	// Running duels ------------------------------------------------------------------------------

	public static Optional<Duel> duelOf(UUID player) {
		return DUELS.stream().filter(duel -> duel.involves(player)).findFirst();
	}

	/** Leaves a duel: the opponent wins it. */
	public static boolean leave(MinecraftServer server, ServerPlayer player) {
		Optional<Duel> duel = duelOf(player.getUUID());

		if (duel.isEmpty()) {
			return false;
		}

		UUID opponent = duel.get().opponent(player.getUUID());
		message(server, duel.get(), Component.translatableWithFallback("sabercraft.duel.left", "%s left the duel.", player.getDisplayName()));
		finish(server, duel.get(), opponent, End.LEFT);
		return true;
	}

	private static void tick(MinecraftServer server) {
		if (DUELS.isEmpty()) {
			return;
		}

		for (Duel duel : List.copyOf(DUELS)) {
			ServerPlayer first = player(server, duel.first);
			ServerPlayer second = player(server, duel.second);

			if (!AbilityConfig.get().duelsEnabled) {
				finish(server, duel, null, End.DISABLED);
				continue;
			}

			if (first == null || second == null) {
				finish(server, duel, null, End.LOGGED_OUT);
				continue;
			}

			if (first.level() != second.level() || first.distanceTo(second) > MAX_DISTANCE) {
				message(server, duel, Component.translatableWithFallback("sabercraft.duel.too_far", "The duel ends: you are too far apart."));
				finish(server, duel, null, End.TOO_FAR);
				continue;
			}

			long elapsed = first.level().getGameTime() - duel.phaseStart;

			if (duel.phase == Phase.COUNTDOWN) {
				// "3" was shown when the round started; then "2" and "1".
				if (elapsed > 0 && elapsed % 20 == 0 && elapsed < COUNTDOWN_TICKS) {
					countdown(first, second, duel, (int) ((COUNTDOWN_TICKS - elapsed) / 20));
				} else if (elapsed >= COUNTDOWN_TICKS) {
					duel.phase = Phase.FIGHT;
					duel.phaseStart = first.level().getGameTime();
					title(first, second, Component.translatableWithFallback("sabercraft.duel.fight", "Fight!").withStyle(ChatFormatting.RED),
							Component.empty());
				}
			}
		}
	}

	/** During the countdown the two cannot hurt each other. */
	private static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (!(entity instanceof ServerPlayer player)) {
			return true;
		}

		Optional<Duel> duel = duelOf(player.getUUID());
		Entity attacker = attacker(source);
		return duel.isEmpty() || attacker == null || !duel.get().opponent(player.getUUID()).equals(attacker.getUUID())
				|| duel.get().phase == Phase.FIGHT;
	}

	/**
	 * A blow of the opponent that would kill ends the round instead. Death by anything else ends
	 * the duel without a winner, and the player dies as usual.
	 */
	private static boolean allowDeath(LivingEntity entity, DamageSource source, float amount) {
		if (!(entity instanceof ServerPlayer player)) {
			return true;
		}

		Optional<Duel> found = duelOf(player.getUUID());

		if (found.isEmpty()) {
			return true;
		}

		Duel duel = found.get();
		MinecraftServer server = player.level().getServer();
		Entity attacker = attacker(source);
		UUID opponent = duel.opponent(player.getUUID());

		if (attacker == null || !attacker.getUUID().equals(opponent) || duel.phase != Phase.FIGHT) {
			message(server, duel, Component.translatableWithFallback("sabercraft.duel.died", "%s fell to something else; the duel ends.", player.getDisplayName()));
			finish(server, duel, null, End.DIED);
			return true;
		}

		player.setHealth(player.getMaxHealth());
		roundWon(server, duel, opponent);
		return false;
	}

	private static @Nullable Entity attacker(DamageSource source) {
		Entity entity = source.getEntity();

		if (entity == null && source.getDirectEntity() instanceof Projectile projectile) {
			entity = projectile.getOwner();
		}

		return entity;
	}

	private static void roundWon(MinecraftServer server, Duel duel, UUID winner) {
		if (duel.first.equals(winner)) {
			duel.firstWins++;
		} else {
			duel.secondWins++;
		}

		ServerPlayer winnerPlayer = player(server, winner);
		Component name = winnerPlayer != null ? winnerPlayer.getDisplayName() : Component.literal("?");
		message(server, duel, Component.translatableWithFallback("sabercraft.duel.round_won", "Round %s goes to %s (%s : %s).",
				duel.round, name, duel.firstWins, duel.secondWins));

		if (duel.wins(winner) >= WINS_NEEDED) {
			finish(server, duel, winner, End.WON);
			return;
		}

		duel.round++;
		startRound(server, duel);
	}

	private static void startRound(MinecraftServer server, Duel duel) {
		ServerPlayer first = player(server, duel.first);
		ServerPlayer second = player(server, duel.second);

		for (ServerPlayer player : new ServerPlayer[] {first, second}) {
			if (player != null) {
				heal(player);
			}
		}

		duel.phase = Phase.COUNTDOWN;
		duel.phaseStart = first != null ? first.level().getGameTime() : 0L;

		if (first != null && second != null) {
			countdown(first, second, duel, COUNTDOWN_TICKS / 20);
		}

		if (duel.round == 1) {
			message(server, duel, Component.translatableWithFallback("sabercraft.duel.started",
					"The duel begins: best of three. /sabercraft duel leave gives up."));
		}
	}

	private static void finish(MinecraftServer server, Duel duel, @Nullable UUID winner, End end) {
		DUELS.remove(duel);
		LAST_END.put(duel.first, end);
		LAST_END.put(duel.second, end);
		ServerPlayer first = player(server, duel.first);
		ServerPlayer second = player(server, duel.second);

		if (winner != null) {
			ServerPlayer winnerPlayer = player(server, winner);
			Component name = winnerPlayer != null ? winnerPlayer.getDisplayName() : Component.literal("?");
			message(server, duel, Component.translatableWithFallback("sabercraft.duel.won", "%s wins the duel!", name).withStyle(ChatFormatting.GOLD));

			if (first != null && second != null) {
				title(first, second, Component.translatableWithFallback("sabercraft.duel.winner", "%s wins", name).withStyle(ChatFormatting.GOLD),
						Component.literal(duel.firstWins + " : " + duel.secondWins));
			}
		}

		for (ServerPlayer player : new ServerPlayer[] {first, second}) {
			if (player != null && player.isAlive() && end != End.DIED) {
				heal(player);
			}
		}
	}

	private static void leftServer(MinecraftServer server, UUID player) {
		CHALLENGES.removeIf(c -> c.from().equals(player) || c.to().equals(player));
		duelOf(player).ifPresent(duel -> finish(server, duel, null, End.LOGGED_OUT));
	}

	/**
	 * A player by id: one who is logged in, or another player entity in a world (some mods use
	 * those for machines and tests).
	 */
	private static @Nullable ServerPlayer player(MinecraftServer server, UUID id) {
		ServerPlayer player = server.getPlayerList().getPlayer(id);

		if (player != null) {
			return player;
		}

		for (ServerLevel level : server.getAllLevels()) {
			if (level.getEntity(id) instanceof ServerPlayer other && other.isAlive()) {
				return other;
			}
		}

		return null;
	}

	private static void heal(ServerPlayer player) {
		player.setHealth(player.getMaxHealth());
		player.clearFire();
		player.removeAllEffects();
	}

	private static void message(@Nullable MinecraftServer server, Duel duel, Component text) {
		if (server == null) {
			return;
		}

		for (UUID id : new UUID[] {duel.first, duel.second}) {
			ServerPlayer player = player(server, id);

			if (player != null) {
				player.sendSystemMessage(text);
			}
		}
	}

	private static void countdown(ServerPlayer first, ServerPlayer second, Duel duel, int seconds) {
		title(first, second, Component.literal(String.valueOf(seconds)).withStyle(ChatFormatting.GOLD),
				Component.translatableWithFallback("sabercraft.duel.round", "Round %s", duel.round));
	}

	private static void title(ServerPlayer first, ServerPlayer second, Component title, Component subtitle) {
		for (ServerPlayer player : new ServerPlayer[] {first, second}) {
			player.connection.send(new ClientboundSetTitlesAnimationPacket(0, 20, 5));
			player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
			player.connection.send(new ClientboundSetTitleTextPacket(title));
		}
	}

	/** How the last duel of a player ended, for tests. */
	public static Optional<End> lastEnd(UUID player) {
		return Optional.ofNullable(LAST_END.get(player));
	}
}
