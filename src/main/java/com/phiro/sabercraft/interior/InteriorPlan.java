package com.phiro.sabercraft.interior;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Phase 14: the plan of a ship's interior: its rooms, how to build them, where people come aboard,
 * where they get off, the turbolifts and the helm. Everything is in plot space (x east, y up, z
 * south from the plot's corner, the ship's nose to the north). A new ship with an interior needs
 * one subclass and an entry in {@link #of}.
 */
public abstract class InteriorPlan {
	/** A named room: its name is shown when someone walks in, and the tests check every room can be reached. */
	public record Room(String id, AABB area) {
		public Vec3 centre() {
			return this.area.getCenter();
		}
	}

	/** A turbolift: standing in {@code area} a moment takes you to {@code arrival}, looking to {@code yaw}. */
	public record Lift(AABB area, Vec3 arrival, float yaw) {
	}

	/** The plan id ({@code falcon}, {@code star_destroyer}). */
	public abstract String id();

	/** Raised when the plan changes so much that existing interiors should be rebuilt (not done automatically). */
	public abstract int version();

	/** The size of the plot the plan uses (from the plot corner), for loading its chunks. */
	public abstract BlockPos size();

	/** Builds the whole interior. */
	public abstract void build(InteriorBuilder builder);

	/** Where people come aboard, and where they look. */
	public abstract Vec3 entry();

	public abstract float entryYaw();

	/** Standing in one of these a moment takes you off the ship. */
	public abstract List<AABB> exits();

	public abstract List<Lift> lifts();

	/** The lever that hands the ship's controls to its owner. */
	public abstract BlockPos helm();

	public abstract List<Room> rooms();

	/** The looping sound of the interior ({@code ship.interior_falcon}). */
	public abstract String ambient();

	/** The room a point is in, or {@code null}. */
	public @Nullable Room room(Vec3 local) {
		for (Room room : this.rooms()) {
			if (room.area().contains(local)) {
				return room;
			}
		}

		return null;
	}

	public static final List<InteriorPlan> ALL = List.of(new FalconInterior(), new DestroyerInterior());

	/** The plan with that id, or {@code null}. */
	public static @Nullable InteriorPlan of(@Nullable String id) {
		for (InteriorPlan plan : ALL) {
			if (plan.id().equals(id)) {
				return plan;
			}
		}

		return null;
	}

	/** The row of plots this plan's interiors are in. */
	public int row() {
		return ALL.indexOf(this);
	}
}
