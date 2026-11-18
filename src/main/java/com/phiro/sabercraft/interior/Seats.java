package com.phiro.sabercraft.interior;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerRotationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.phiro.sabercraft.interior.InteriorPlan.Seat;

/**
 * Phase 16: sitting down in the interiors. A click on a seat (a slim invisible barrier under the
 * drawn chair) puts the player on an invisible vanilla item display standing at the seat's
 * surface, turned the way the seat faces; sneaking stands them up again, in front of the seat
 * where there is room. Every seat holds one person (the server keeps who sits where), any number
 * of players can sit at once. Nothing new is registered, so players without the mod sit too.
 *
 * <p>Nobody gets stuck: a seat whose rider is gone (stood up, logged out, died, was teleported)
 * is removed the next tick and its rider put on their feet; a seat left over from before a
 * restart is taken over again by its rider or removed.
 */
public final class Seats {
	/** The tag of the invisible seats. */
	public static final String TAG = "sabercraft_seat";

	/** Who sits where. */
	private static final Map<UUID, Sitting> SITTING = new HashMap<>();
	/** Seats loaded from the world (after a restart, a rider logging in) to look at next tick. */
	private static final List<Display.ItemDisplay> LOADED = new ArrayList<>();
	private static int sat;

	/** A player sitting: on what, in which plot, on which seat. */
	record Sitting(UUID player, Display.ItemDisplay vehicle, BlockPos origin, InteriorPlan plan, Seat seat) {
	}

	private Seats() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(Seats::tick);
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Display.ItemDisplay display && display.entityTags().contains(TAG) && !known(display)) {
				LOADED.add(display);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			SITTING.clear();
			LOADED.clear();
		});
	}

	private static boolean known(Entity vehicle) {
		for (Sitting sitting : SITTING.values()) {
			if (sitting.vehicle == vehicle) {
				return true;
			}
		}

		return false;
	}

	/** The seat a player sits on, or {@code null}. */
	public static @Nullable Seat seat(ServerPlayer player) {
		Sitting sitting = SITTING.get(player.getUUID());
		return sitting != null && player.getVehicle() == sitting.vehicle ? sitting.seat : null;
	}

	/** The plot corner of the seat a player sits on, or {@code null}. */
	public static @Nullable BlockPos origin(ServerPlayer player) {
		Sitting sitting = SITTING.get(player.getUUID());
		return sitting != null && player.getVehicle() == sitting.vehicle ? sitting.origin : null;
	}

	/** Who sits on that seat of that plot, or {@code null}. */
	public static @Nullable UUID sitter(BlockPos origin, Seat seat) {
		for (Sitting sitting : SITTING.values()) {
			if (sitting.origin.equals(origin) && sitting.seat.equals(seat)) {
				return sitting.player;
			}
		}

		return null;
	}

	/**
	 * A click on a cell of a plot: sits the player down if it is a seat. Returns whether the click
	 * was on a seat (taken or not).
	 */
	static boolean use(ServerPlayer player, BlockPos origin, InteriorPlan plan, BlockPos local, Vec3 hit) {
		Seat seat = plan.seatFor(local, hit.subtract(origin.getX(), origin.getY(), origin.getZ()));

		if (seat == null) {
			return false;
		}

		player.sendOverlayMessage(sit(player, origin, plan, seat));
		return true;
	}

	/** Sits a player down on a seat; returns what to tell them. */
	public static Component sit(ServerPlayer player, BlockPos origin, InteriorPlan plan, Seat seat) {
		if (player.isSpectator() || player.isSleeping()) {
			return Component.empty();
		}

		if (player.isPassenger()) {
			return Component.translatableWithFallback("sabercraft.seat.stand_first", "Stand up first (sneak).");
		}

		UUID other = sitter(origin, seat);

		if (other != null) {
			ServerPlayer taken = player.level().getServer().getPlayerList().getPlayer(other);
			return Component.translatableWithFallback("sabercraft.seat.taken", "%s is sitting there.",
					taken != null ? taken.getDisplayName() : Component.literal("?"));
		}

		ServerLevel level = (ServerLevel) player.level();
		Vec3 at = seat.at().add(origin.getX(), origin.getY(), origin.getZ());
		// Phase 22: a seat one of the story's people sits on stays theirs.
		Component sitter = com.phiro.sabercraft.story.Story.sitterAt(origin, at);

		if (sitter != null) {
			return Component.translatableWithFallback("sabercraft.seat.taken", "%s is sitting there.", sitter);
		}
		Display.ItemDisplay vehicle = new Display.ItemDisplay(EntityTypes.ITEM_DISPLAY, level);
		vehicle.setPos(at.x, at.y, at.z);
		vehicle.setYRot(seat.yaw());
		vehicle.addTag(TAG);
		vehicle.setInvulnerable(true);
		Sitting sitting = new Sitting(player.getUUID(), vehicle, origin, plan, seat);
		SITTING.put(player.getUUID(), sitting);

		if (!level.addFreshEntity(vehicle) || !player.startRiding(vehicle, true, true)) {
			SITTING.remove(player.getUUID());
			vehicle.discard();
			return Component.empty();
		}

		face(player, seat.yaw());
		sat++;
		return switch (seat.role()) {
			case Seat.PILOT, Seat.COPILOT -> Component.translatableWithFallback("sabercraft.seat.cockpit",
					"Sneak to stand up. Hyperspace: the hyperspace key or the lever by the co-pilot.");
			case Seat.GUNNER -> Component.translatableWithFallback("sabercraft.seat.gunner", "Aim with the mouse, attack to fire. Sneak to stand up.");
			default -> Component.translatableWithFallback("sabercraft.seat.sit", "Sneak to stand up.");
		};
	}

	/** Turns a player (and their head) to a yaw, looking straight ahead. */
	private static void face(ServerPlayer player, float yaw) {
		player.setYRot(yaw);
		player.setYHeadRot(yaw);
		player.setYBodyRot(yaw);
		player.setXRot(0.0F);
		player.connection.send(new ClientboundPlayerRotationPacket(yaw, false, 0.0F, false));
	}

	private static void tick(MinecraftServer server) {
		for (Display.ItemDisplay display : List.copyOf(LOADED)) {
			LOADED.remove(display);
			adopt(display);
		}

		for (Sitting sitting : List.copyOf(SITTING.values())) {
			ServerPlayer player = server.getPlayerList().getPlayer(sitting.player);

			if (player != null && player.getVehicle() == sitting.vehicle && !sitting.vehicle.isRemoved()) {
				continue;
			}

			SITTING.remove(sitting.player);

			if (!sitting.vehicle.isRemoved()) {
				sitting.vehicle.ejectPassengers();
				sitting.vehicle.discard();
			}

			if (player != null && player.isAlive() && !player.isPassenger() && player.level() == sitting.vehicle.level()) {
				standUp(player, sitting);
			}
		}
	}

	/** A seat from the world (a restart, a rider logging in): its rider sits on, or it goes. */
	private static void adopt(Display.ItemDisplay display) {
		if (display.isRemoved()) {
			return;
		}

		if (display.level() instanceof ServerLevel level && ShipInteriors.isInterior(level)
				&& display.getFirstPassenger() instanceof ServerPlayer player && !SITTING.containsKey(player.getUUID())) {
			InteriorPlan plan = ShipInteriors.planAt(display.getX(), display.getZ());

			if (plan != null) {
				int index = Mth.floor(display.getX() / ShipInteriors.SPACING) - 1;
				BlockPos origin = ShipInteriors.origin(plan, index);

				for (Seat seat : plan.seats()) {
					if (seat.at().add(origin.getX(), origin.getY(), origin.getZ()).distanceToSqr(display.position()) < 0.01 && sitter(origin, seat) == null) {
						SITTING.put(player.getUUID(), new Sitting(player.getUUID(), display, origin, plan, seat));
						return;
					}
				}
			}
		}

		display.ejectPassengers();
		display.discard();
	}

	/** Puts someone who left a seat on their feet in front of it (or beside it, where there is room). */
	private static void standUp(ServerPlayer player, Sitting sitting) {
		Seat seat = sitting.seat;
		BlockPos cell = sitting.origin.offset(seat.cell());
		Vec3 forward = Vec3.directionFromRotation(0.0F, seat.yaw());

		// Phase 17: first close round the seat itself, in front first (the furniture round a seat
		// collides as drawn now, so the middle of the next cell is often in the couch or the table).
		Vec3 at = seat.at().add(sitting.origin.getX(), sitting.origin.getY(), sitting.origin.getZ());

		for (float turn : new float[] {0.0F, 45.0F, -45.0F, 90.0F, -90.0F, 135.0F, -135.0F, 180.0F}) {
			Vec3 way = Vec3.directionFromRotation(0.0F, seat.yaw() + turn);

			for (double distance : new double[] {0.8, 1.05, 1.3, 1.55}) {
				Vec3 feet = new Vec3(at.x + way.x * distance, cell.getY(), at.z + way.z * distance);

				if (canStand(player, feet)) {
					player.teleportTo(feet.x, feet.y, feet.z);
					return;
				}
			}
		}

		List<BlockPos> spots = new ArrayList<>();

		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (dx != 0 || dz != 0) {
					spots.add(cell.offset(dx, 0, dz));
				}
			}
		}

		// In front first, then the sides, behind last.
		spots.sort((a, b) -> Double.compare(-(a.getX() - cell.getX()) * forward.x - (a.getZ() - cell.getZ()) * forward.z,
				-(b.getX() - cell.getX()) * forward.x - (b.getZ() - cell.getZ()) * forward.z));

		for (BlockPos spot : spots) {
			Vec3 feet = new Vec3(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);

			if (canStand(player, feet)) {
				player.teleportTo(feet.x, feet.y, feet.z);
				return;
			}
		}

		// No room anywhere: on the seat itself.
		player.teleportTo(cell.getX() + 0.5, cell.getY() + 0.6, cell.getZ() + 0.5);
	}

	/** Whether a player standing with the feet there is in nothing and stands on something. */
	private static boolean canStand(ServerPlayer player, Vec3 feet) {
		AABB box = player.getDimensions(Pose.STANDING).makeBoundingBox(feet);
		return player.level().noCollision(player, box) && !player.level().noCollision(player, box.move(0.0, -0.6, 0.0));
	}

	/** Only for tests: how many times someone sat down since the server started. */
	public static int satForTest() {
		return sat;
	}

	/** Only for tests: how many seats are taken now. */
	public static int takenForTest() {
		return SITTING.size();
	}
}
