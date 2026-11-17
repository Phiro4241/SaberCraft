package com.phiro.sabercraft.interior;

import java.util.HashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

import com.phiro.sabercraft.interior.InteriorPlan.Hatch;

/**
 * Phase 18: the crawl ways. A hatch opens with a click (its cell becomes air) and closes by itself
 * once it has stood open a while with nobody near it. Crouching right in front of an open hatch
 * (or another opening of a crawl way) puts one down on all fours, the game's own crawling pose,
 * worked out the same on the server and on the client (both run the pose each tick): once inside
 * the way, the low ceiling keeps one crawling, and one stands up again on leaving it.
 */
public final class Vents {
	private Vents() {
	}

	/** How near (blocks, from the middle of the opening, level) one must be to get down. */
	public static final double MOUTH_REACH = 1.45;
	/**
	 * How near one who crawls already (no longer crouching) stays down: just in front of the
	 * opening, so that one coming out stands up as soon as there is room.
	 */
	public static final double KEEP_REACH = 0.95;
	/** Ticks a hatch stays open at least. */
	static final int OPEN_TICKS = 160;

	/** When each open hatch was opened (world position, game time). */
	private static final Map<BlockPos, Long> OPEN = new HashMap<>();

	/** The hatch of a plan at that cell (plot space), or {@code null}. */
	static @Nullable Hatch hatch(InteriorPlan plan, BlockPos local) {
		for (Hatch hatch : plan.hatches()) {
			if (hatch.cell().equals(local)) {
				return hatch;
			}
		}

		return null;
	}

	/** A click on a closed hatch opens it. */
	static void open(ServerPlayer player, BlockPos origin, Hatch hatch) {
		ServerLevel level = player.level();
		BlockPos at = origin.offset(hatch.cell());

		if (!level.getBlockState(at).is(Blocks.BARRIER)) {
			return;
		}

		level.setBlock(at, Blocks.AIR.defaultBlockState(), 3);
		OPEN.put(at.immutable(), level.getGameTime());
		level.playSound(null, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, ShipInteriors.sound("ship.door"), SoundSource.BLOCKS, 0.3F, 1.6F);
	}

	/** Every second: open hatches nobody is near close again. */
	static void tick(MinecraftServer server) {
		if (server.getTickCount() % 20 != 0 || OPEN.isEmpty()) {
			return;
		}

		ServerLevel level = server.getLevel(ShipInteriors.DIMENSION);

		if (level == null) {
			OPEN.clear();
			return;
		}

		long now = level.getGameTime();
		OPEN.entrySet().removeIf(entry -> {
			BlockPos at = entry.getKey();

			if (!level.getBlockState(at).isAir()) {
				return true;
			}

			if (now - entry.getValue() < OPEN_TICKS || !level.getEntitiesOfClass(Player.class, new AABB(at).inflate(2.0)).isEmpty()) {
				return false;
			}

			level.setBlock(at, Blocks.BARRIER.defaultBlockState(), 3);
			level.playSound(null, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, ShipInteriors.sound("ship.door"), SoundSource.BLOCKS, 0.22F, 1.75F);
			return true;
		});
	}

	/** Forgets the open hatches (a plot rebuilt or the server stopping). */
	static void reset() {
		OPEN.clear();
	}

	/**
	 * Whether a player should crawl now: crouching (or crawling already) right in front of an open
	 * opening of a crawl way, at its height. Runs on both sides with what both know.
	 */
	public static boolean crawl(Player player) {
		if (player.isSpectator() || player.isPassenger() || player.getAbilities().flying || !ShipInteriors.isInterior(player.level())) {
			return false;
		}

		boolean crouching = player.isShiftKeyDown();

		if (!crouching && player.getPose() != Pose.SWIMMING) {
			return false;
		}

		double reach = crouching ? MOUTH_REACH : KEEP_REACH;

		InteriorPlan plan = ShipInteriors.planAt(player.getX(), player.getZ());
		BlockPos origin = ShipInteriors.originAt(player.getX(), player.getZ());

		if (plan == null || origin == null) {
			return false;
		}

		for (BlockPos mouth : plan.crawlMouths()) {
			BlockPos at = origin.offset(mouth);
			double dx = player.getX() - (at.getX() + 0.5);
			double dz = player.getZ() - (at.getZ() + 0.5);
			double dy = player.getY() - at.getY();

			if (dx * dx + dz * dz < reach * reach && dy > -0.2 && dy < 0.6 && player.level().getBlockState(at).isAir()) {
				return true;
			}
		}

		return false;
	}
}
