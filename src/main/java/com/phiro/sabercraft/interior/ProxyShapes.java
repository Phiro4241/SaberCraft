package com.phiro.sabercraft.interior;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Phase 16: the collision shapes of the interiors' invisible barriers that are not a full block:
 * the curved walls of the Falcon (the barrier stops exactly where the drawn wall is), slim seats.
 * They come from the plan alone, so the server and every modded client agree on them; a mixin
 * hands them to the game's collision code ({@code BlockStateBaseMixin}). Players without the mod
 * collide with full blocks there (a little earlier, never through a wall).
 */
public final class ProxyShapes {
	private static final Map<String, Recorder> BUILT = new ConcurrentHashMap<>();

	private ProxyShapes() {
	}

	/** The shapes of a plan, by cell in plot space (built from the plan once). */
	public static Long2ObjectMap<VoxelShape> of(InteriorPlan plan) {
		return built(plan).shapes;
	}

	/** Phase 16: the blocks of a plan as built (plot space; no air). */
	public static Long2ObjectMap<BlockState> blocks(InteriorPlan plan) {
		return built(plan).blocks;
	}

	private static Recorder built(InteriorPlan plan) {
		return BUILT.computeIfAbsent(plan.id(), unused -> {
			Recorder recorder = new Recorder();
			plan.build(new InteriorBuilder(recorder));
			return recorder;
		});
	}

	/** The shape of the barrier at a position of the interiors dimension, or {@code null} for a full block. */
	public static @Nullable VoxelShape at(BlockPos pos) {
		InteriorPlan plan = ShipInteriors.planAt(pos.getX() + 0.5, pos.getZ() + 0.5);

		if (plan == null) {
			return null;
		}

		int index = Math.floorDiv(pos.getX(), ShipInteriors.SPACING) - 1;
		BlockPos origin = ShipInteriors.origin(plan, index);
		return of(plan).get(BlockPos.asLong(pos.getX() - origin.getX(), pos.getY() - origin.getY(), pos.getZ() - origin.getZ()));
	}

	/** A test on a point of the plot (plot space, x and z); true where something solid is. */
	public interface Solid {
		boolean at(double x, double z);
	}

	/**
	 * The shape of the cell (x, z), a full block high, made of columns 1/{@code res} of a block wide
	 * that are solid where {@code solid} says so at their middle. Empty if nothing is solid.
	 */
	public static VoxelShape columns(int x, int z, int res, Solid solid) {
		boolean[][] columns = new boolean[res][res];

		for (int i = 0; i < res; i++) {
			for (int j = 0; j < res; j++) {
				columns[i][j] = solid.at(x + (i + 0.5) / res, z + (j + 0.5) / res);
			}
		}

		VoxelShape shape = Shapes.empty();
		int i = 0;

		while (i < res) {
			// Columns that are the same as their neighbours are joined into one box.
			int i1 = i;

			while (i1 + 1 < res && java.util.Arrays.equals(columns[i], columns[i1 + 1])) {
				i1++;
			}

			int j = 0;

			while (j < res) {
				if (!columns[i][j]) {
					j++;
					continue;
				}

				int j1 = j;

				while (j1 + 1 < res && columns[i][j1 + 1]) {
					j1++;
				}

				shape = Shapes.or(shape, Shapes.box((double) i / res, 0.0, (double) j / res, (i1 + 1.0) / res, 1.0, (j1 + 1.0) / res));
				j = j1 + 1;
			}

			i = i1 + 1;
		}

		return shape.optimize();
	}

	/** Whether the cell is solid at some sample and open at another (it straddles a curved wall). */
	public static boolean mixed(int x, int z, int res, Solid solid) {
		boolean any = false;
		boolean all = true;

		for (int i = 0; i < res; i++) {
			for (int j = 0; j < res; j++) {
				boolean s = solid.at(x + (i + 0.5) / res, z + (j + 0.5) / res);
				any |= s;
				all &= s;
			}
		}

		return any && !all;
	}

	/** Builds a plan in memory and keeps the shapes. */
	private static final class Recorder implements InteriorBuilder.Target {
		final Long2ObjectMap<BlockState> blocks = new Long2ObjectOpenHashMap<>();
		final Long2ObjectMap<VoxelShape> shapes = new Long2ObjectOpenHashMap<>();

		@Override
		public void set(int x, int y, int z, BlockState state) {
			long key = BlockPos.asLong(x, y, z);
			// Whatever is put there later replaces the shape too.
			this.shapes.remove(key);

			if (state.isAir()) {
				this.blocks.remove(key);
			} else {
				this.blocks.put(key, state);
			}
		}

		@Override
		public BlockState get(int x, int y, int z) {
			BlockState state = this.blocks.get(BlockPos.asLong(x, y, z));
			return state == null ? Blocks.AIR.defaultBlockState() : state;
		}

		@Override
		public void shape(int x, int y, int z, VoxelShape shape, boolean wall) {
			this.shapes.put(BlockPos.asLong(x, y, z), shape);
		}
	}
}
