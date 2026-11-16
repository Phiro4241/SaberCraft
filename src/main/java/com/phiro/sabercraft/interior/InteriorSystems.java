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
	/** How near (blocks, along the way through) someone opens a door. */
	static final double DOOR_REACH = 2.6;

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
		}

		InteriorJump.tick(server, interiors);
		Turrets.tick(server);
		Vents.tick(server);
	}

	// --- Doors -------------------------------------------------------------------------------------

	/** Phase 17: how loud a door is opening and closing. */
	public static final float DOOR_OPEN_VOLUME = 0.55F;
	public static final float DOOR_CLOSE_VOLUME = 0.32F;

	/** Opens the doors someone is near, closes the others (or opens all of them, for tests). */
	static void doors(ServerLevel level, BlockPos origin, InteriorPlan plan, boolean allOpen) {
		for (Door door : plan.doors()) {
			// Phase 19: a story can keep a door shut (until a card, a console, a droid opens it).
			boolean open = allOpen || near(level, origin, door) && !com.phiro.sabercraft.story.Story.locked(origin, door.x0(), door.y0(), door.z0());
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
				Vec3 middle = middle(origin, door);
				// Phase 17: closing a little quieter than opening.
				level.playSound(null, middle.x, middle.y, middle.z, ShipInteriors.sound("ship.door"), SoundSource.BLOCKS, open ? DOOR_OPEN_VOLUME : DOOR_CLOSE_VOLUME,
						open ? 1.0F : 0.85F);
			}
		}
	}

	/** Whether anyone (not a spectator) stands within reach of the door, on either side. */
	private static boolean near(ServerLevel level, BlockPos origin, Door door) {
		AABB box = new AABB(origin.getX() + door.x0(), origin.getY() + door.y0() - 0.5, origin.getZ() + door.z0(), origin.getX() + door.x1() + 1,
				origin.getY() + door.y1() + 1, origin.getZ() + door.z1() + 1)
				.inflate(door.alongX() ? DOOR_REACH : 0.3, 0.0, door.alongX() ? 0.3 : DOOR_REACH);
		return !level.getEntitiesOfClass(net.minecraft.world.entity.player.Player.class, box, player -> !player.isSpectator()).isEmpty();
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
			// Phase 19: a story can keep a hatch shut (bolted from the other side).
			if (com.phiro.sabercraft.story.Story.locked(origin, hatch.cell().getX(), hatch.cell().getY(), hatch.cell().getZ())) {
				com.phiro.sabercraft.story.Story.lockedHatch(player);
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
