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

	/**
	 * Phase 26: a hatch just shut by hand does not open again from a click that comes right after
	 * (the same click, repeated by the held key or sent twice): ticks it stays shut at least.
	 */
	static final int SHUT_TICKS = 6;

	/** Phase 26: when each hatch was last shut by hand (world position, game time). */
	private static final Map<BlockPos, Long> SHUT = new HashMap<>();

	/**
	 * Phase 27: a hatch opened with a click is a way in: for a few seconds, one goes down on all
	 * fours in front of it as when crouching there (no need to know the crouch key). Worked out
	 * on both sides: the server when it opens the hatch, the client when the click goes out
	 * ({@link #intend}). Per player, until the game tick. One map per side: in singleplayer the
	 * client's and the server's player share their entity id (and so count as equal), but not
	 * their tick count.
	 */
	private static final Map<Player, Long> INTENT_SERVER = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
	private static final Map<Player, Long> INTENT_CLIENT = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
	/** Phase 27: how long (ticks) a click on a hatch keeps one ready to crawl in. */
	public static final int INTENT_TICKS = 100;
	/**
	 * Phase 27: how near the opening (blocks, from its middle) one goes down after a click (one
	 * steps up to it), and how near walking up to an open one facing it (pressed to the wall in
	 * front of it: one cannot walk in standing, so one goes down).
	 */
	public static final double INTENT_REACH = 1.1;
	public static final double WALK_REACH = 0.9;
	/** Phase 27: how far (degrees) the view may be off the opening for walking into it. */
	static final double WALK_FACING = 50.0;

	/** Phase 27: the player means to crawl in (clicked a hatch): for {@link #INTENT_TICKS}. */
	public static void intend(Player player) {
		intents(player).put(player, (long) player.tickCount + INTENT_TICKS);
	}

	private static Map<Player, Long> intents(Player player) {
		return player.level().isClientSide() ? INTENT_CLIENT : INTENT_SERVER;
	}

	/** Phase 27: whether the player clicked a hatch a moment ago (and has not crawled in since). */
	public static boolean intends(Player player) {
		Map<Player, Long> intents = intents(player);
		Long until = intents.get(player);

		if (until == null) {
			return false;
		}

		if (player.tickCount > until || player.tickCount < until - INTENT_TICKS - 1) {
			intents.remove(player);
			return false;
		}

		return true;
	}

	/** Phase 27: crawling in a ship's crawl way goes a little faster than the game's crawl (0.3 of walking). */
	static final net.minecraft.resources.Identifier CRAWL_SPEED = net.minecraft.resources.Identifier.fromNamespaceAndPath("sabercraft", "crawl_way");
	public static final double CRAWL_BONUS = 0.12;

	/**
	 * Phase 27: on the server, every tick: crawling in a ship's interior (out of water), the
	 * crawl is faster by {@link #CRAWL_BONUS} (the game shares the player's speeds with their
	 * client); standing again, as before.
	 */
	public static void crawlSpeed(Player player) {
		if (player.level().isClientSide()) {
			return;
		}

		net.minecraft.world.entity.ai.attributes.AttributeInstance speed = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.SNEAKING_SPEED);

		if (speed == null) {
			return;
		}

		boolean crawling = player.getPose() == Pose.SWIMMING && !player.isInWater() && ShipInteriors.isInterior(player.level()) && !player.isSpectator();
		boolean has = speed.hasModifier(CRAWL_SPEED);

		if (crawling && !has) {
			speed.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(CRAWL_SPEED, CRAWL_BONUS,
					net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
		} else if (!crawling && has) {
			speed.removeModifier(CRAWL_SPEED);
		}
	}

	/** For the tests: whether the player crawls with the crawl way's bonus now. */
	public static boolean fastCrawlForTest(Player player) {
		net.minecraft.world.entity.ai.attributes.AttributeInstance speed = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.SNEAKING_SPEED);
		return speed != null && speed.hasModifier(CRAWL_SPEED);
	}

	/** A click on a closed hatch opens it. */
	static void open(ServerPlayer player, BlockPos origin, Hatch hatch) {
		ServerLevel level = player.level();
		BlockPos at = origin.offset(hatch.cell());

		if (!level.getBlockState(at).is(Blocks.BARRIER)) {
			return;
		}

		// Phase 27: the click also gets one down in front of it, ready to crawl in (not from
		// inside the way: one crawling opens it to get out).
		if (player.getPose() != Pose.SWIMMING) {
			intend(player);
		}

		Long shut = SHUT.get(at);

		if (shut != null && level.getGameTime() - shut < SHUT_TICKS && level.getGameTime() >= shut) {
			reopensRefused++;
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
		if (server.getTickCount() % 20 != 0 || OPEN.isEmpty() && SHUT.isEmpty()) {
			return;
		}

		ServerLevel level = server.getLevel(ShipInteriors.DIMENSION);

		if (level == null) {
			OPEN.clear();
			return;
		}

		long now = level.getGameTime();
		SHUT.values().removeIf(at -> now - at > SHUT_TICKS || now < at);
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
		SHUT.put(at.immutable(), level.getGameTime());

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

			// Phase 27: not around someone in it (another player of the plot crawling through).
			if (occupied(level, at)) {
				continue;
			}

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
		SHUT.clear();
	}

	private static int reopensRefused;

	/** Phase 26, for tests: how often a click right after shutting a hatch was not let open it again. */
	public static int reopensRefusedForTest() {
		return reopensRefused;
	}

	/** Phase 27: whether the player looks towards the opening (level), {@code dx, dz} from it to them. */
	private static boolean facing(Player player, double dx, double dz) {
		double length = Math.sqrt(dx * dx + dz * dz);

		if (length < 1.0E-3) {
			return true;
		}

		Vec3 look = Vec3.directionFromRotation(0.0F, player.getYRot());
		double cos = (-dx * look.x - dz * look.z) / length;
		return cos > Math.cos(Math.toRadians(WALK_FACING));
	}

	/**
	 * Whether a player should crawl now: crouching (or crawling already) right in front of an open
	 * opening of a crawl way, at its height. Runs on both sides with what both know. Phase 27: also
	 * after clicking a hatch, and walking up to an open one facing it.
	 */
	public static boolean crawl(Player player) {
		if (player.isSpectator() || player.isPassenger() || player.getAbilities().flying || !ShipInteriors.isInterior(player.level())) {
			return false;
		}

		boolean crouching = player.isShiftKeyDown();
		boolean crawling = player.getPose() == Pose.SWIMMING;
		// Phase 27: or a hatch clicked a moment ago (a single click is enough to get in), or
		// walking up to an open opening facing it: no crouch key needed.
		// (The click's reach holds whether already down or not, so one who went down at its edge
		// stays down; it ends once they are in the opening.)
		boolean intent = !crouching && intends(player);
		boolean walking = !crouching && !crawling && !intent;
		double reach = crouching ? MOUTH_REACH : intent ? INTENT_REACH : crawling ? KEEP_REACH : WALK_REACH;

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

			if (dx * dx + dz * dz < reach * reach && dy > -0.2 && dy < 0.6 && player.level().getBlockState(at).isAir() && (!walking || facing(player, dx, dz))) {
				if (intent && crawling && dx * dx + dz * dz < KEEP_REACH * KEEP_REACH) {
					// Crawled in: the click is used up (coming out, they stand up as usual).
					intents(player).remove(player);
				}

				return true;
			}
		}

		return false;
	}
}
