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

	/**
	 * Phase 16: a doorway through a curved wall (cells, corners included): the client draws the wall
	 * open exactly between its sides and lines it.
	 */
	public record Doorway(int x0, int y0, int z0, int x1, int y1, int z1, boolean alongX) {
		public boolean contains(int x, int y, int z) {
			return x >= this.x0 && x <= this.x1 && y >= this.y0 && y <= this.y1 && z >= this.z0 && z <= this.z1;
		}
	}

	/**
	 * Phase 16: a place to sit. {@code cell} is the block that is clicked (an invisible barrier,
	 * slim for chairs), {@code at} the middle of the seat's surface, {@code yaw} where the sitter
	 * looks (Minecraft yaw), {@code role} what the seat is for ({@link #CHAIR}, {@link #PILOT},
	 * {@link #COPILOT}, {@link #GUNNER}, {@link #COUCH}).
	 */
	public record Seat(BlockPos cell, Vec3 at, float yaw, String role) {
		public static final String CHAIR = "chair";
		public static final String PILOT = "pilot";
		public static final String COPILOT = "copilot";
		public static final String GUNNER = "gunner";
		public static final String COUCH = "couch";

		/** A chair standing in the middle of its cell, its seat {@code height} over the floor. */
		public static Seat chair(int x, int y, int z, double height, float yaw, String role) {
			return new Seat(new BlockPos(x, y, z), new Vec3(x + 0.5, y + height, z + 0.5), yaw, role);
		}

		/** Whether the seat is in the cockpit (from there the hyperdrive can be started). */
		public boolean cockpit() {
			return PILOT.equals(this.role) || COPILOT.equals(this.role);
		}
	}

	/** Phase 16: the seats of the plan. */
	public List<Seat> seats() {
		return List.of();
	}

	/**
	 * Phase 16: the seat taken by clicking a cell (plot space): the seat of that cell, or
	 * {@code null}. Plans can widen this (a couch: the nearest place on it).
	 */
	public @Nullable Seat seatFor(BlockPos cell, Vec3 hit) {
		for (Seat seat : this.seats()) {
			if (seat.cell().equals(cell)) {
				return seat;
			}
		}

		return null;
	}

	/**
	 * Phase 16: a sliding door: the cells (one thick, corners included) that close, the way people
	 * pass through it ({@code alongX}: along x, the door standing in a plane of constant x), and
	 * where its leaves stand along that way. The server opens it when someone comes near and
	 * closes it behind them (the cells are barriers while it is closed); the client draws the
	 * leaves sliding from where they are.
	 */
	public record Door(int x0, int y0, int z0, int x1, int y1, int z1, boolean alongX, double plane) {
		public boolean contains(int x, int y, int z) {
			return x >= this.x0 && x <= this.x1 && y >= this.y0 && y <= this.y1 && z >= this.z0 && z <= this.z1;
		}

		/** A door closing one cell wide slice of a wall: at x (alongX) or z, across from a0 to a1, y0 to y1. */
		public static Door wall(boolean alongX, int at, int a0, int a1, int y0, int y1) {
			return alongX ? new Door(at, y0, a0, at, y1, a1, true, at + 0.5) : new Door(a0, y0, at, a1, y1, at, false, at + 0.5);
		}
	}

	/** Phase 16: the plan's sliding doors. */
	public List<Door> doors() {
		return List.of();
	}

	/**
	 * Phase 16: something to use on a console: {@link #HYPERSPACE} (the hyperdrive lever, from a
	 * cockpit seat), {@link #STATUS} (the ship's state), {@link #LIGHTS} (normal or dimmed lights).
	 */
	public record Console(BlockPos cell, String action) {
		public static final String HYPERSPACE = "hyperspace";
		public static final String STATUS = "status";
		public static final String LIGHTS = "lights";
	}

	/** Phase 16: the consoles that do something. */
	public List<Console> consoles() {
		return List.of();
	}

	/** Phase 16: the doorways through the plan's curved walls. */
	public List<Doorway> doorways() {
		return List.of();
	}

	/** Phase 16: whether a point (plot space, row y) is inside a room with curved walls, up to its drawn walls. */
	public boolean openRound(double x, double z, int y) {
		return false;
	}

	/** Whether a cell is in one of the doorways through curved walls. */
	public boolean inDoorway(int x, int y, int z) {
		for (Doorway door : this.doorways()) {
			if (door.contains(x, y, z)) {
				return true;
			}
		}

		return false;
	}

	/** The plan id ({@code falcon}, {@code star_destroyer}). */
	public abstract String id();

	/** Raised when the plan changes so much that existing interiors should be rebuilt (not done automatically). */
	public abstract int version();

	/** The size of the plot the plan uses (from the plot corner), for loading its chunks. */
	public abstract BlockPos size();

	/**
	 * Phase 15: the boxes (in plot space, corners included) that hold the plan's blocks: for loading
	 * their chunks, telling the plan from a position and finding its containers. By default one box
	 * from the plot corner to {@link #size()}.
	 */
	public List<net.minecraft.world.level.levelgen.structure.BoundingBox> volumes() {
		BlockPos size = this.size();
		return List.of(new net.minecraft.world.level.levelgen.structure.BoundingBox(0, 0, 0, size.getX(), size.getY(), size.getZ()));
	}

	/** Phase 15: the boxes cleared before an older interior is rebuilt (the current and every older plan). */
	public List<net.minecraft.world.level.levelgen.structure.BoundingBox> clearVolumes() {
		return this.volumes();
	}

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
