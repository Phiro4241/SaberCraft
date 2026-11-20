package com.phiro.sabercraft.interior;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.phiro.sabercraft.ability.AbilityConfig;
import com.phiro.sabercraft.interior.InteriorPlan.Console;
import com.phiro.sabercraft.interior.InteriorPlan.Door;
import com.phiro.sabercraft.ship.Hyperspace;
import com.phiro.sabercraft.ship.ShipEntity;
import com.phiro.sabercraft.ship.ShipType;
import com.phiro.sabercraft.ship.Starships;

/**
 * Phase 16: what can be used aboard, besides the seats: sliding doors that open when someone
 * comes near and close behind them, the consoles that do something (the hyperdrive lever in the
 * Falcon's cockpit, the ship's state at the engineering station or the sensor table, the lights
 * at the engineering bank or the bridge officers' stations). All decided by the server, with
 * vanilla blocks only (a closed door is a row of barriers, dimmed lights are lower light
 * blocks), so every player sees and bumps into the same, with or without the mod.
 */
public final class InteriorSystems {
	/**
	 * How near (blocks, along the way through) someone coming towards a door opens it. Phase 26:
	 * 2.6 before; the doors opened rather early (a little less now, still in time for a run).
	 */
	static final double DOOR_REACH = 2.15;
	/**
	 * Phase 26: how near (blocks, along the way through) a player opens a door without coming
	 * towards it (standing in front of it, stepping sideways into it).
	 */
	static final double DOOR_NEAR = 0.9;
	/**
	 * Phase 26: the same for the people of a story: only right in the doorway. Walking past a door
	 * (a patrol along a corridor) no longer opens it; walking towards it does.
	 */
	static final double DOOR_NEAR_FIGURE = 0.6;
	/** Phase 26: blocks someone must come nearer to the door's plane in two ticks to count as coming towards it. */
	static final double DOOR_APPROACH = 0.025;
	/** Phase 26: ticks a door stays open at least once it opened (no snapping shut on someone's back, no flapping). */
	static final int DOOR_HOLD = 16;
	/**
	 * Phase 29: in a story fight a player opens a door only from this near (blocks, along the way
	 * through), and only coming towards it over two looks in a row (four ticks): stepping about in
	 * a fight beside a door no longer makes it open and shut over and over. Still in time for a run.
	 */
	static final double DOOR_REACH_FIGHT = 1.6;
	/**
	 * Phase 29: a door just shut does not open again for this long (ticks) for anyone not in the
	 * doorway or on their way through it: no flapping between open and shut.
	 */
	static final int DOOR_REST = 10;

	private InteriorSystems() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(InteriorSystems::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> InteriorJump.reset());
	}

	private static void tick(MinecraftServer server) {
		ServerLevel interiors = server.getLevel(ShipInteriors.DIMENSION);

		if (interiors == null) {
			return;
		}

		if (server.getTickCount() % 2 == 0) {
			for (Map.Entry<BlockPos, InteriorPlan> plot : ShipInteriors.plotsInUse().entrySet()) {
				doors(interiors, plot.getKey(), plot.getValue(), false);
			}

			doorsSeen();
		}

		InteriorJump.tick(server, interiors);
		Turrets.tick(server);
		Vents.tick(server);
	}

	// --- Doors -------------------------------------------------------------------------------------

	/** Phase 17: how loud a door is opening and closing. */
	public static final float DOOR_OPEN_VOLUME = 0.55F;
	public static final float DOOR_CLOSE_VOLUME = 0.32F;

	/** Phase 26: where everyone near the doors stood when they were last looked at (two ticks ago). */
	private static final Map<String, Vec3> WAS = new HashMap<>();
	/** Phase 26: the positions seen this time (they become {@link #WAS} after all plots). */
	private static final Map<String, Vec3> NOW = new HashMap<>();
	/** Phase 29: where everyone stood one look before that (four ticks ago). */
	private static final Map<String, Vec3> WAS2 = new HashMap<>();
	/** Phase 26: until when (game time) each open door stays open at least (its first cell, world space). */
	private static final Map<Long, Long> HELD_OPEN = new HashMap<>();
	/** Phase 29: until when (game time) each door just shut stays shut for anyone only coming near. */
	private static final Map<Long, Long> RESTING = new HashMap<>();
	/** Phase 26: how often a door opened or shut (with its sound), for tests. */
	private static int doorMoves;

	/** Opens the doors someone is near, closes the others (or opens all of them, for tests). */
	static void doors(ServerLevel level, BlockPos origin, InteriorPlan plan, boolean allOpen) {
		// Phase 26: everyone who might come to a door here, with where they were before.
		List<Mover> movers = allOpen ? List.of() : movers(level, origin);
		long now = level.getGameTime();
		// Phase 29: a story fight in this ship: players open doors only on their way through.
		boolean fight = !allOpen && com.phiro.sabercraft.story.Story.fightingAt(origin);

		for (Door door : plan.doors()) {
			long key = origin.offset(door.x0(), door.y0(), door.z0()).asLong();
			boolean isOpen = level.getBlockState(origin.offset(door.x0(), door.y0(), door.z0())).isAir();
			Long rest = RESTING.get(key);
			boolean resting = !isOpen && rest != null && now < rest;
			boolean wanted = allOpen || wanted(origin, door, movers, isOpen, fight, resting);
			Long hold = HELD_OPEN.get(key);

			if (!wanted && isOpen && hold != null && now < hold) {
				// Phase 26: just opened: it stays open a moment (someone in the doorway, or about to be).
				wanted = true;
			}

			// Phase 19: a story can keep a door shut (until a card, a console, a droid opens it).
			boolean open = allOpen || wanted && !com.phiro.sabercraft.story.Story.locked(origin, door.x0(), door.y0(), door.z0());

			if (open && !isOpen) {
				HELD_OPEN.put(key, now + DOOR_HOLD);
				RESTING.remove(key);
			} else if (!open) {
				HELD_OPEN.remove(key);

				if (isOpen) {
					// Phase 29: just shut: it rests a moment (see DOOR_REST).
					RESTING.put(key, now + DOOR_REST);
				}
			}

			boolean changed = false;

			for (int x = door.x0(); x <= door.x1(); x++) {
				for (int y = door.y0(); y <= door.y1(); y++) {
					for (int z = door.z0(); z <= door.z1(); z++) {
						BlockPos pos = origin.offset(x, y, z);
						BlockState state = level.getBlockState(pos);

						if (open && state.is(Blocks.BARRIER)) {
							level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
							changed = true;
						} else if (!open && state.isAir()) {
							level.setBlock(pos, Blocks.BARRIER.defaultBlockState(), 2);
							changed = true;
						}
					}
				}
			}

			if (changed && !allOpen) {
				doorMoves++;
				Vec3 middle = middle(origin, door);
				// Phase 17: closing a little quieter than opening.
				level.playSound(null, middle.x, middle.y, middle.z, ShipInteriors.sound("ship.door"), SoundSource.BLOCKS, open ? DOOR_OPEN_VOLUME : DOOR_CLOSE_VOLUME,
						open ? 1.0F : 0.85F);
			}
		}
	}

	/**
	 * Phase 26: someone who may come to a door: a player or one of a story's people, where they
	 * stand now and where they stood two ticks before ({@code null}: just came).
	 */
	record Mover(boolean player, Vec3 at, @Nullable Vec3 was, @Nullable Vec3 older, List<Vec3> intent) {
		Mover(boolean player, Vec3 at, @Nullable Vec3 was) {
			this(player, at, was, null, List.of());
		}
	}

	/** Phase 26: everyone in a plot who may open its doors (players not spectating, the story's people). */
	private static List<Mover> movers(ServerLevel level, BlockPos origin) {
		List<Mover> list = new ArrayList<>();
		// (A plot reaches east of its corner, and north and south of it: the Star Destroyer's hangar
		// deck lies at negative z.)
		int half = ShipInteriors.SPACING / 2;
		AABB plot = new AABB(origin.getX() - 16, level.getMinY(), origin.getZ() - half, origin.getX() + ShipInteriors.SPACING - 16, level.getMaxY(),
				origin.getZ() + half);

		for (net.minecraft.world.entity.player.Player player : level.getEntitiesOfClass(net.minecraft.world.entity.player.Player.class, plot,
				player -> !player.isSpectator())) {
			String key = player.getStringUUID();
			Vec3 at = player.position();
			NOW.put(key, at);
			list.add(new Mover(true, at, WAS.get(key), WAS2.get(key), List.of()));
		}

		// Phase 20: the people of a story walk through doors too (chasing, sent somewhere). Phase
		// 29: with where they mean to go (a door opens for them only on their way).
		com.phiro.sabercraft.story.Story.figuresAt(origin, (key, at, intent, duelist) -> {
			NOW.put(key, at);
			list.add(new Mover(false, at, WAS.get(key), WAS2.get(key), intent));
		});
		return list;
	}

	/**
	 * Whether someone wants through that door now (on either side, before its opening: not off to
	 * the side of it). Phase 26: only who comes towards it, or stands right in it; a player in front
	 * of an open door keeps it open while not walking away. One walking past it (along the wall, a
	 * patrol in the corridor) or away from it does not open it.
	 *
	 * <p>Phase 29: the people of the story open a door only when the way they mean to walk leads
	 * through it within {@link #DOOR_REACH} (or they stand in the doorway): coming near it is not
	 * enough any more (a patrol turning before a door at the end of its beat, the boss fighting
	 * beside one opened it). Players as before, but in a story fight only from
	 * {@link #DOOR_REACH_FIGHT} and coming towards it over two looks in a row; and a door just shut
	 * rests a moment ({@link #DOOR_REST}) for anyone not in the doorway.
	 */
	static boolean wanted(BlockPos origin, Door door, List<Mover> movers, boolean isOpen) {
		return wanted(origin, door, movers, isOpen, false, false);
	}

	static boolean wanted(BlockPos origin, Door door, List<Mover> movers, boolean isOpen, boolean fight, boolean resting) {
		AABB box = new AABB(origin.getX() + door.x0(), origin.getY() + door.y0() - 0.5, origin.getZ() + door.z0(), origin.getX() + door.x1() + 1,
				origin.getY() + door.y1() + 1, origin.getZ() + door.z1() + 1)
				.inflate(door.alongX() ? DOOR_REACH : 0.3, 0.0, door.alongX() ? 0.3 : DOOR_REACH);
		double plane = (door.alongX() ? origin.getX() : origin.getZ()) + door.plane();

		for (Mover mover : movers) {
			if (!mover.player()) {
				// Phase 29: the story's people: in the doorway, or on their way through it.
				if (box.contains(mover.at().add(0.0, 0.5, 0.0))
						&& Math.abs((door.alongX() ? mover.at().x : mover.at().z) - plane) <= DOOR_NEAR_FIGURE) {
					return true;
				}

				if (through(origin, door, plane, mover.at(), mover.intent())) {
					return true;
				}

				continue;
			}

			if (!box.contains(mover.at().add(0.0, 0.5, 0.0))) {
				continue;
			}

			double along = (door.alongX() ? mover.at().x : mover.at().z) - plane;
			double distance = Math.abs(along);

			if (distance <= DOOR_NEAR) {
				return true;
			}

			if (resting || fight && distance > DOOR_REACH_FIGHT) {
				continue;
			}

			Vec3 was = mover.was();

			if (was == null || was.distanceTo(mover.at()) > 1.5) {
				// Just came (a player stepping out of a lift, put there): as if walking up to it.
				if (!fight) {
					return true;
				}

				continue;
			}

			double before = Math.abs((door.alongX() ? was.x : was.z) - plane);
			double closer = before - distance;

			if (fight && !isOpen) {
				// Phase 29: in a fight only one who keeps coming (two looks in a row).
				Vec3 older = mover.older();

				if (older == null || older.distanceTo(was) > 1.5 || Math.abs((door.alongX() ? older.x : older.z) - plane) - before <= DOOR_APPROACH) {
					continue;
				}
			}

			if (closer > DOOR_APPROACH || isOpen && closer > -DOOR_APPROACH) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Phase 29: whether a way from {@code at} along {@code intent} (world space) goes through the
	 * door's opening within {@link #DOOR_REACH} of walking.
	 */
	static boolean through(BlockPos origin, Door door, double plane, Vec3 at, List<Vec3> intent) {
		if (intent.isEmpty()) {
			return false;
		}

		double lo = (door.alongX() ? origin.getZ() + door.z0() : origin.getX() + door.x0()) - 0.3;
		double hi = (door.alongX() ? origin.getZ() + door.z1() : origin.getX() + door.x1()) + 1.3;
		double bottom = origin.getY() + door.y0() - 1.6;
		double top = origin.getY() + door.y1() + 1.0;
		double walked = 0.0;
		Vec3 a = at;

		for (Vec3 b : intent) {
			double da = (door.alongX() ? a.x : a.z) - plane;
			double db = (door.alongX() ? b.x : b.z) - plane;
			double length = a.distanceTo(b);

			if (da != db && (da <= 0.0) != (db <= 0.0) || db == 0.0) {
				double t = da == db ? 1.0 : da / (da - db);
				Vec3 c = a.lerp(b, Math.max(0.0, Math.min(1.0, t)));
				double side = door.alongX() ? c.z : c.x;

				if (walked + length * t <= DOOR_REACH && side >= lo && side <= hi && c.y >= bottom && c.y <= top) {
					return true;
				}
			}

			walked += length;

			if (walked > DOOR_REACH) {
				return false;
			}

			a = b;
		}

		return false;
	}

	/** Phase 26: after all plots: what was seen now is what was seen before, next time. */
	private static void doorsSeen() {
		WAS2.clear();
		WAS2.putAll(WAS);
		WAS.clear();
		WAS.putAll(NOW);
		NOW.clear();
	}

	/** Phase 26, for tests: how often a door opened or shut so far. */
	public static int doorMovesForTest() {
		return doorMoves;
	}

	/**
	 * Phase 26, for tests: whether one person (a player or one of a story's people) at {@code at},
	 * two ticks before at {@code was} (null: just came), wants through that door of a plot whose
	 * corner is at the world's origin.
	 */
	public static boolean wantedForTest(Door door, boolean player, Vec3 at, @Nullable Vec3 was, boolean isOpen) {
		return wanted(BlockPos.ZERO, door, List.of(new Mover(player, at, was)), isOpen);
	}

	/**
	 * Phase 29, for tests: the same with where one of the story's people means to walk next
	 * ({@code intent}), one look before ({@code older}), and whether a story fight runs there.
	 */
	public static boolean wantedForTest(Door door, boolean player, Vec3 at, @Nullable Vec3 was, @Nullable Vec3 older, List<Vec3> intent, boolean isOpen,
			boolean fight) {
		return wanted(BlockPos.ZERO, door, List.of(new Mover(player, at, was, older, intent)), isOpen, fight, false);
	}

	/** Phase 26, for tests: the doors of a plot looked at now (as every second tick). */
	public static void doorsForTest(ServerLevel level, BlockPos origin, InteriorPlan plan) {
		doors(level, origin, plan, false);
		doorsSeen();
	}

	static Vec3 middle(BlockPos origin, Door door) {
		return new Vec3(origin.getX() + (door.x0() + door.x1() + 1) / 2.0, origin.getY() + door.y0() + 1.0, origin.getZ() + (door.z0() + door.z1() + 1) / 2.0);
	}

	/** Only for tests: opens every door of a plot now (they close again when nobody is near). */
	public static void openDoorsForTest(ServerLevel level, BlockPos origin, InteriorPlan plan) {
		doors(level, origin, plan, true);
	}

	/** Only for tests: a click on a block of the interiors (a seat or a console). */
	public static void useForTest(ServerPlayer player, BlockPos pos) {
		use(player, pos, Vec3.atCenterOf(pos));
	}

	/** Only for tests: what the ship's systems report. */
	public static Component statusForTest(ServerPlayer player, InteriorPlan plan) {
		return status(player, plan);
	}

	/** Only for tests: whether every cell of a door of a plot is open. */
	public static boolean doorOpenForTest(ServerLevel level, BlockPos origin, Door door) {
		return level.getBlockState(origin.offset(door.x0(), door.y0(), door.z0())).isAir();
	}

	// --- Seats and consoles ------------------------------------------------------------------------

	/** Whether a click on that block (in the interiors) is for a seat or a console (on both sides). */
	public static boolean handles(Level level, BlockPos pos, Vec3 hit) {
		BlockPos origin = ShipInteriors.originAt(pos.getX() + 0.5, pos.getZ() + 0.5);
		InteriorPlan plan = ShipInteriors.planAt(pos.getX() + 0.5, pos.getZ() + 0.5);

		if (origin == null || plan == null) {
			return false;
		}

		BlockPos local = pos.subtract(origin);
		return console(plan, local) != null || Vents.hatch(plan, local) != null || plan.seatFor(local, hit.subtract(origin.getX(), origin.getY(), origin.getZ())) != null;
	}

	static @Nullable Console console(InteriorPlan plan, BlockPos local) {
		for (Console console : plan.consoles()) {
			if (console.cell().equals(local)) {
				return console;
			}
		}

		return null;
	}

	/** A click on a seat or a console. */
	static void use(ServerPlayer player, BlockPos pos, Vec3 hit) {
		BlockPos origin = ShipInteriors.originAt(pos.getX() + 0.5, pos.getZ() + 0.5);
		InteriorPlan plan = ShipInteriors.planAt(pos.getX() + 0.5, pos.getZ() + 0.5);

		if (origin == null || plan == null) {
			return;
		}

		BlockPos local = pos.subtract(origin);
		Console console = console(plan, local);
		InteriorPlan.Hatch hatch = Vents.hatch(plan, local);

		if (hatch != null) {
			// Phase 19: a story can keep a hatch shut (bolted from the other side; Phase 20: or a
			// guard stands right in front of it).
			if (com.phiro.sabercraft.story.Story.hatchClosed(player, origin, hatch.cell(), hatch.room())) {
				return;
			}

			Vents.open(player, origin, hatch);
			return;
		}

		if (console == null) {
			Seats.use(player, origin, plan, local, hit);
			return;
		}

		if (Console.STORAGE.equals(console.action())) {
			// Phase 24: in a story's ship the containers stay shut (nothing of the story is put away in them).
			if (com.phiro.sabercraft.story.Story.at(origin) != null) {
				return;
			}

			// Phase 18: the chest under the container.
			if (player.level().getBlockEntity(pos.below()) instanceof net.minecraft.world.MenuProvider store) {
				player.openMenu(store);
			}

			return;
		}

		player.sendOverlayMessage(switch (console.action()) {
			case Console.HYPERSPACE -> InteriorJump.request(player);
			case Console.LIGHTS -> lights(player, (ServerLevel) player.level(), origin, plan);
			default -> status(player, plan);
		});
	}

	// --- Lights ------------------------------------------------------------------------------------

	/** The light blocks of a plan as built: where, and how bright. */
	private static final Map<String, List<long[]>> LIGHTS = new HashMap<>();

	private static List<long[]> lightsOf(InteriorPlan plan) {
		return LIGHTS.computeIfAbsent(plan.id(), unused -> {
			List<long[]> list = new ArrayList<>();

			for (Long2ObjectMap.Entry<BlockState> entry : ProxyShapes.blocks(plan).long2ObjectEntrySet()) {
				if (entry.getValue().is(Blocks.LIGHT)) {
					list.add(new long[] {entry.getLongKey(), entry.getValue().getValue(LightBlock.LEVEL)});
				}
			}

			return list;
		});
	}

	/** The brightest light block of a plan as built (it tells the dimmer's step), or {@code null}. */
	private static long @org.jspecify.annotations.Nullable [] marker(InteriorPlan plan) {
		long[] best = null;

		for (long[] light : lightsOf(plan)) {
			if (best == null || light[1] > best[1]) {
				best = light;
			}
		}

		return best;
	}

	/** Phase 17: the step of a plot's dimmer now (0 = full light), from its blocks. */
	public static int lightStep(ServerLevel level, BlockPos origin, InteriorPlan plan) {
		long[] marker = marker(plan);

		if (marker == null) {
			return 0;
		}

		BlockState state = level.getBlockState(origin.offset(BlockPos.of(marker[0])));
		return state.is(Blocks.LIGHT) ? ShipLights.stepOf((int) marker[1], state.getValue(LightBlock.LEVEL)) : 0;
	}

	/** Whether a plot's lights are dimmed now (any step below full). */
	public static boolean dimmed(ServerLevel level, BlockPos origin, InteriorPlan plan) {
		return lightStep(level, origin, plan) > 0;
	}

	/** Phase 17: sets a plot's dimmer to a step (0 = full light). */
	public static void setLightStep(ServerLevel level, BlockPos origin, InteriorPlan plan, int step) {
		for (long[] light : lightsOf(plan)) {
			BlockPos pos = origin.offset(BlockPos.of(light[0]));

			if (level.getBlockState(pos).is(Blocks.LIGHT)) {
				level.setBlock(pos, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, ShipLights.level((int) light[1], step)), 2);
			}
		}
	}

	/**
	 * The light switch (Phase 17: a dimmer): each use one step darker, from the darkest back to
	 * full light; sneaking, one step brighter.
	 */
	static Component lights(ServerPlayer player, ServerLevel level, BlockPos origin, InteriorPlan plan) {
		int step = lightStep(level, origin, plan);
		int next = player.isShiftKeyDown() ? Math.max(0, step - 1) : (step + 1) % ShipLights.steps();
		setLightStep(level, origin, plan, next);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), ShipInteriors.sound(next > step ? "ship.power_down" : "ship.power_up"),
				SoundSource.BLOCKS, 0.5F, 1.2F);
		int percent = Math.round(ShipLights.surfaces(next) * 100.0F);
		return next == 0 ? Component.translatableWithFallback("sabercraft.interior.lights_on", "Lights on.")
				: Component.translatableWithFallback("sabercraft.interior.lights_level", "Lights: %s %%", percent);
	}

	// --- The ship's state --------------------------------------------------------------------------

	/** What the ship's systems report: hull, energy, hyperdrive (of the owner's ship, wherever it is). */
	static Component status(ServerPlayer player, InteriorPlan plan) {
		UUID owner = ShipInteriors.ownerOf(player);
		ShipEntity ship = owner != null ? Starships.shipOf(owner) : null;
		ShipType type = ShipType.byId(plan.id()).orElse(null);
		Component name = type != null ? Component.translatableWithFallback(type.translationKey(), type.displayName()) : Component.literal(plan.id());

		if (ship == null || ship.ship() != type) {
			return Component.translatableWithFallback("sabercraft.interior.status_none", "%s: no contact with the ship's systems.", name);
		}

		AbilityConfig config = AbilityConfig.get();
		int hull = Mth.ceil(100.0F * ship.getHealth() / Math.max(1.0F, ship.getMaxHealth()));
		float energy = ship.energy();
		Component power = !config.shipEnergy || energy < 0.0F ? Component.translatableWithFallback("sabercraft.interior.status_full", "full")
				: Component.literal(Math.round(100.0F * Mth.clamp(energy / Math.max(1, config.flightTicks(type)), 0.0F, 1.0F)) + " %");
		int cooldown = owner != null ? Hyperspace.cooldownSeconds(owner, player.level().getGameTime()) : 0;
		Component drive = cooldown > 0 ? Component.translatableWithFallback("sabercraft.interior.status_cooling", "cooling down (%s s)", cooldown)
				: Component.translatableWithFallback("sabercraft.interior.status_ready", "ready");
		return Component.translatableWithFallback("sabercraft.interior.status", "%s: hull %s %%, energy %s, hyperdrive %s", name, hull, power, drive);
	}
}
