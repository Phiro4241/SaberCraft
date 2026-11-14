package com.phiro.sabercraft.client.interior;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import com.phiro.sabercraft.interior.InteriorBuilder;
import com.phiro.sabercraft.interior.InteriorPlan;

/**
 * Phase 15: the blocks of an interior plan, built in memory on the client (the same plan code the
 * server builds the plot from). The models are made to fit these blocks exactly, whether the
 * chunks of the plot are loaded yet or not.
 */
public final class VirtualPlot implements InteriorBuilder.Target {
	private static final BlockState AIR = Blocks.AIR.defaultBlockState();
	/** How a curved wall's barrier looks to the panelling: like the hull blocks around it. */
	private static final BlockState WALL = Blocks.DYED_TERRACOTTA.white().defaultBlockState();
	final Long2ObjectMap<BlockState> blocks = new Long2ObjectOpenHashMap<>();
	final it.unimi.dsi.fastutil.longs.LongSet walls = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();

	public static VirtualPlot of(InteriorPlan plan) {
		VirtualPlot plot = new VirtualPlot();
		plan.build(new InteriorBuilder(plot));
		return plot;
	}

	@Override
	public void set(int x, int y, int z, BlockState state) {
		this.walls.remove(BlockPos.asLong(x, y, z));

		if (state.isAir()) {
			this.blocks.remove(BlockPos.asLong(x, y, z));
		} else {
			this.blocks.put(BlockPos.asLong(x, y, z), state);
		}
	}

	/**
	 * Phase 16: a barrier that is part of a curved wall: panelled like a wall where it faces a
	 * square room, and the floor and ceiling run on under it (part of it is room).
	 */
	@Override
	public void shape(int x, int y, int z, net.minecraft.world.phys.shapes.VoxelShape shape, boolean wall) {
		if (wall) {
			long key = BlockPos.asLong(x, y, z);
			this.blocks.put(key, WALL);
			this.walls.add(key);
		}
	}

	/** Whether the cell is part of a curved wall (see {@link #shape}). */
	public boolean wall(int x, int y, int z) {
		return this.walls.contains(BlockPos.asLong(x, y, z));
	}

	@Override
	public BlockState get(int x, int y, int z) {
		BlockState state = this.blocks.get(BlockPos.asLong(x, y, z));
		return state == null ? AIR : state;
	}

	/** A full block that hides what is behind it (walls, floors, ceilings). */
	public boolean solid(int x, int y, int z) {
		BlockState state = this.get(x, y, z);
		return !state.isAir() && state.canOcclude();
	}

	/** Whether there is room to walk or see through (air, hidden light, barrier panes and proxies). */
	public boolean open(int x, int y, int z) {
		BlockState state = this.get(x, y, z);
		return state.isAir() || state.is(Blocks.LIGHT) || state.is(Blocks.BARRIER) || !state.canOcclude();
	}

	public boolean is(int x, int y, int z, net.minecraft.world.level.block.Block block) {
		return this.get(x, y, z).is(block);
	}
}
