package com.phiro.sabercraft.interior;

import java.util.HashMap;
import java.util.Map;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import com.phiro.sabercraft.network.InteriorJumpPayload;

/**
 * Phase 16: the hyperspace jump inside the Falcon. Everyone in that interior gets the same three
 * stages: the drive spools up ({@value #CHARGE} ticks, the stars stretch), a few seconds in
 * hyperspace ({@value #TUNNEL} ticks, the tunnel of light round the ship), the way back
 * ({@value #ARRIVE} ticks).
 *
 * <p>Phase 31: only the story makes this jump (the Falcon arriving at the Star Destroyer and
 * leaving it at the end, see {@link #story}). The hyperspace of ships in the open world is gone,
 * and with it the jump a player started from the cockpit (the hyperspace key, the lever).
 *
 * <p>The ship standing in the world stays where it is: the interior is a place of its own, the
 * jump is a journey for those aboard.
 */
public final class InteriorJump {
	static final int CHARGE = 30;
	static final int TUNNEL = 110;
	static final int ARRIVE = 24;

	/** The jumps running, by plot corner: game time they started. */
	private static final Map<BlockPos, Long> RUNNING = new HashMap<>();
	private static int jumps;

	private InteriorJump() {
	}

	/**
	 * Phase 19: a jump the story makes (the Falcon arriving at the Star Destroyer, leaving it at the
	 * end): no seat, no cooldown, no options asked. Returns whether it started.
	 */
	public static boolean story(ServerLevel level, BlockPos origin) {
		InteriorPlan plan = ShipInteriors.planAt(origin.getX() + 1.0, origin.getZ() + 1.0);

		if (plan == null || RUNNING.containsKey(origin)) {
			return false;
		}

		RUNNING.put(origin, level.getGameTime());
		jumps++;
		send(level.getServer(), origin, InteriorJumpPayload.CHARGE, CHARGE);
		sound(level, origin, plan, "ship.hyperspace_charge", 1.0F);
		return true;
	}

	/** Phase 20: when, in a jump, the tunnel of light ends and the ship falls out of hyperspace (ticks). */
	public static int tunnelEnd() {
		return CHARGE + TUNNEL;
	}

	/** Phase 19: how long a jump takes from the first charge to the arrival (ticks). */
	public static int length() {
		return CHARGE + TUNNEL + ARRIVE;
	}

	static void tick(MinecraftServer server, ServerLevel interiors) {
		if (RUNNING.isEmpty()) {
			return;
		}

		long now = interiors.getGameTime();

		for (Map.Entry<BlockPos, Long> entry : Map.copyOf(RUNNING).entrySet()) {
			BlockPos origin = entry.getKey();
			long age = now - entry.getValue();
			InteriorPlan plan = ShipInteriors.planAt(origin.getX() + 1.0, origin.getZ() + 1.0);

			if (plan == null) {
				RUNNING.remove(origin);
			} else if (age == CHARGE) {
				send(server, origin, InteriorJumpPayload.TUNNEL, TUNNEL);
				sound(interiors, origin, plan, "ship.hyperspace_jump", 1.2F);
			} else if (age == CHARGE + TUNNEL) {
				send(server, origin, InteriorJumpPayload.ARRIVE, ARRIVE);
				sound(interiors, origin, plan, "ship.hyperspace_arrive", 1.0F);
			} else if (age >= CHARGE + TUNNEL + ARRIVE) {
				RUNNING.remove(origin);
			}
		}
	}

	/** Tells everyone in that interior about a stage (the late-comers see the rest of the jump). */
	private static void send(MinecraftServer server, BlockPos origin, byte stage, int ticks) {
		InteriorJumpPayload payload = new InteriorJumpPayload(origin, stage, (short) ticks);

		for (ServerPlayer player : ShipInteriors.playersIn(server, origin)) {
			if (ServerPlayNetworking.canSend(player, InteriorJumpPayload.TYPE)) {
				ServerPlayNetworking.send(player, payload);
			}
		}
	}

	/** A sound heard all through the interior (from the cockpit, loud enough to fill the ship). */
	private static void sound(ServerLevel level, BlockPos origin, InteriorPlan plan, String name, float volume) {
		Vec3 at = Vec3.atCenterOf(origin.offset(plan.helm()));

		for (ServerPlayer player : ShipInteriors.playersIn(level.getServer(), origin)) {
			// Each one hears it close by: a jump shakes the whole ship.
			Vec3 near = player.position().add(at.subtract(player.position()).normalize().scale(Math.min(4.0, player.position().distanceTo(at))));
			player.connection.send(new net.minecraft.network.protocol.game.ClientboundSoundPacket(ShipInteriors.sound(name), SoundSource.NEUTRAL, near.x,
					near.y, near.z, volume, 1.0F, player.getRandom().nextLong()));
		}
	}

	/** Whether a jump runs in that plot now. */
	public static boolean running(BlockPos origin) {
		return RUNNING.containsKey(origin);
	}

	static void reset() {
		RUNNING.clear();
	}

	/** Only for tests: how many jumps the story started in interiors since the server started. */
	public static int jumpsForTest() {
		return jumps;
	}
}
