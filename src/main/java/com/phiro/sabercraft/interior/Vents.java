package com.phiro.sabercraft.interior;

import java.util.HashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import com.phiro.sabercraft.interior.InteriorPlan.Hatch;

/**
 * Phase 18: the crawl ways. A hatch opens with a click (its cell becomes air) and closes by itself
 * once it has stood open a while with nobody near it. Crouching right in front of an open hatch
 * (or another opening of a crawl way) puts one down on all fours, the game's own crawling pose,
 * worked out the same on the server and on the client (both run the pose each tick): once inside
 * the way, the low ceiling keeps one crawling, and one stands up again on leaving it.
 *
 * <p>Phase 20: the use key on an open hatch shuts it again; by itself a hatch only shuts when
 * nobody (no player, no droid) is in its opening. A story puts all hatches of its ships back as
 * they were at a checkpoint (shut).
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

	/** How far one reaches to shut an open hatch (blocks). */
	public static final double REACH = 3.5;

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

			if (now - entry.getValue() < OPEN_TICKS || !level.getEntitiesOfClass(Player.class, new AABB(at).inflate(2.0)).isEmpty() || occupied(level, at)) {
				return false;
			}

			level.setBlock(at, Blocks.BARRIER.defaultBlockState(), 3);
			level.playSound(null, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, ShipInteriors.sound("ship.door"), SoundSource.BLOCKS, 0.22F, 1.75F);
			return true;
		});
	}

	/** Phase 20: whether anyone (a player, a droid) is in a hatch's opening or just at it. */
	static boolean occupied(ServerLevel level, BlockPos at) {
		return !level.getEntitiesOfClass(LivingEntity.class, new AABB(at).inflate(0.3)).isEmpty();
	}

	/** Phase 20: shuts an open hatch at once (unless someone is in it). Returns whether it did. */
	static boolean close(ServerLevel level, BlockPos at, boolean sound) {
		if (!level.getBlockState(at).isAir() || occupied(level, at)) {
			return false;
		}

		level.setBlock(at, Blocks.BARRIER.defaultBlockState(), 3);
		OPEN.remove(at);

		if (sound) {
			level.playSound(null, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, ShipInteriors.sound("ship.door"), SoundSource.BLOCKS, 0.3F, 1.75F);
		}

		return true;
	}

	/** Phase 20: all hatches of the plot at that corner shut (a story's checkpoint); none stays open. */
	public static void closeAll(ServerLevel level, BlockPos origin) {
		InteriorPlan plan = ShipInteriors.planAt(origin.getX() + 1.0, origin.getZ() + 1.0);

		if (plan == null) {
			return;
		}

		for (Hatch hatch : plan.hatches()) {
			BlockPos at = origin.offset(hatch.cell());

			if (level.getBlockState(at).isAir()) {
				level.setBlock(at, Blocks.BARRIER.defaultBlockState(), 3);
			}

			OPEN.remove(at);
		}
	}

	/** Phase 20: the open hatch a player looks at (within reach, nothing solid nearer), or null. */
	public static @Nullable BlockPos looked(Player player) {
		if (!ShipInteriors.isInterior(player.level())) {
			return null;
		}

		InteriorPlan plan = ShipInteriors.planAt(player.getX(), player.getZ());
		BlockPos origin = ShipInteriors.originAt(player.getX(), player.getZ());

		if (plan == null || origin == null) {
			return null;
		}

		Vec3 eye = player.getEyePosition();
		Vec3 to = eye.add(player.getLookAngle().scale(REACH));
		BlockPos best = null;
		double bestDistance = REACH;

		for (Hatch hatch : plan.hatches()) {
			BlockPos at = origin.offset(hatch.cell());
			AABB box = new AABB(at);

			if (box.contains(eye) || !player.level().getBlockState(at).isAir()) {
				continue;
			}

			double distance = box.clip(eye, to).map(hit -> hit.distanceTo(eye)).orElse(Double.MAX_VALUE);

			if (distance < bestDistance) {
				bestDistance = distance;
				best = at;
			}
		}

		if (best == null) {
			return null;
		}

		HitResult hit = player.level().clip(new ClipContext(eye, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		return hit.getType() != HitResult.Type.MISS && hit.getLocation().distanceTo(eye) < bestDistance - 0.05 ? null : best;
	}

	/** Phase 20: the use key on an open hatch shuts it (unless someone is in it). Returns whether it did. */
	public static boolean closeLooked(ServerPlayer player) {
		BlockPos at = looked(player);
		return at != null && close(player.level(), at, true);
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
