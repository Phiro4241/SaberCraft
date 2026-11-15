package com.phiro.sabercraft.interior;

import java.util.HashMap;
import java.util.Map;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Phase 14: places the blocks of an interior plan into its plot, in plot space (x east, y up, z
 * south from the plot's corner). Only vanilla blocks, so players without SaberCraft see the same
 * rooms. Blocks are set without neighbour updates (levers, lamps and torches stay as placed) and
 * without drops; the whole plan is built once, the first time anyone boards that ship.
 *
 * <p>States are written like in commands ({@code "minecraft:oak_stairs[facing=north]"}, the
 * namespace may be left out) and parsed once.
 */
public final class InteriorBuilder {
	private static final Map<String, BlockState> STATES = new java.util.concurrent.ConcurrentHashMap<>();
	private static final java.util.Set<String> UNKNOWN = new java.util.TreeSet<>();
	/** No neighbour updates, no drops, but sent to clients and lit. */
	private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;

	/** Phase 15: where the blocks go: the interiors dimension, or a copy in memory (the client builds its models from one). */
	public interface Target {
		void set(int x, int y, int z, BlockState state);

		BlockState get(int x, int y, int z);

		/**
		 * Phase 16: the cell just set (a barrier) collides with this shape instead of a full block;
		 * {@code wall} if it is part of a curved wall (the client panels it like a wall).
		 */
		default void shape(int x, int y, int z, net.minecraft.world.phys.shapes.VoxelShape shape, boolean wall) {
		}

		/**
		 * Phase 18: the cell just shaped only collides: the crosshair goes through it (someone of the
		 * crew stands there, and a click is meant for them).
		 */
		default void ghost(int x, int y, int z) {
		}
	}

	private final Target target;
	private int placed;
	/** Phase 17: the shapes given to barriers in this build (so that later pieces can add to them). */
	private final it.unimi.dsi.fastutil.longs.Long2ObjectMap<net.minecraft.world.phys.shapes.VoxelShape> shapes = new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>();
	private final it.unimi.dsi.fastutil.longs.LongSet wallCells = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();

	InteriorBuilder(ServerLevel level, BlockPos origin) {
		this(new Target() {
			@Override
			public void set(int x, int y, int z, BlockState state) {
				level.setBlock(origin.offset(x, y, z), state, FLAGS);
			}

			@Override
			public BlockState get(int x, int y, int z) {
				return level.getBlockState(origin.offset(x, y, z));
			}
		});
	}

	public InteriorBuilder(Target target) {
		this.target = target;
	}

	/** A block state from its command form; unknown names become air (and are logged by the test). */
	public static BlockState state(String text) {
		return STATES.computeIfAbsent(text, key -> {
			String full = key.contains(":") && key.indexOf(':') < (key.contains("[") ? key.indexOf('[') : key.length()) ? key : "minecraft:" + key;

			try {
				return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, full, false).blockState();
			} catch (CommandSyntaxException error) {
				// Never break boarding over one wrong block: air instead, logged and listed for the tests.
				UNKNOWN.add(key);
				com.phiro.sabercraft.SaberCraft.LOGGER.warn("Unknown block state in an interior plan: {}", key);
				return Blocks.AIR.defaultBlockState();
			}
		});
	}

	public void set(int x, int y, int z, String state) {
		this.set(x, y, z, state(state));
	}

	public void set(int x, int y, int z, BlockState state) {
		long key = net.minecraft.core.BlockPos.asLong(x, y, z);
		this.shapes.remove(key);
		this.wallCells.remove(key);
		this.target.set(x, y, z, state);
		this.placed++;
	}

	/**
	 * Phase 16: an invisible barrier that collides with {@code shape} (in the cell's own space)
	 * instead of a full block; {@code wall} marks part of a curved wall.
	 */
	public void shaped(int x, int y, int z, net.minecraft.world.phys.shapes.VoxelShape shape, boolean wall) {
		this.set(x, y, z, Blocks.BARRIER.defaultBlockState());
		this.target.shape(x, y, z, shape, wall);
		long key = net.minecraft.core.BlockPos.asLong(x, y, z);
		this.shapes.put(key, shape);

		if (wall) {
			this.wallCells.add(key);
		}
	}

	/** Phase 18: a shaped barrier that only collides (the crosshair passes through it). */
	public void ghost(int x, int y, int z, net.minecraft.world.phys.shapes.VoxelShape shape) {
		this.shaped(x, y, z, shape, false);
		this.target.ghost(x, y, z);
	}

	/**
	 * Phase 17: gives a cell a shape; where a shaped barrier is already (a curved wall running
	 * through the cell), both shapes together.
	 */
	private void addShape(int x, int y, int z, net.minecraft.world.phys.shapes.VoxelShape shape) {
		long key = net.minecraft.core.BlockPos.asLong(x, y, z);
		net.minecraft.world.phys.shapes.VoxelShape old = this.get(x, y, z).is(Blocks.BARRIER) ? this.shapes.get(key) : null;

		if (old != null) {
			this.shaped(x, y, z, net.minecraft.world.phys.shapes.Shapes.joinUnoptimized(old, shape, net.minecraft.world.phys.shapes.BooleanOp.OR), this.wallCells.contains(key));
		} else {
			this.shaped(x, y, z, shape, false);
		}
	}

	/** Whether the cell holds a shaped barrier (see {@link #shaped}). */
	private boolean isShaped(int x, int y, int z) {
		return this.get(x, y, z).is(Blocks.BARRIER) && this.shapes.containsKey(net.minecraft.core.BlockPos.asLong(x, y, z));
	}

	/** Phase 16: where a room with curved walls is open (plot space, a point at row y). */
	public interface OpenTest {
		boolean open(double x, double z, int y);
	}

	/**
	 * Phase 16: turns the cells a curved wall runs through into barriers shaped like the wall (see
	 * {@link #shaped}): a block there would stick out of the drawn curve on one side (its corners
	 * showed through it and flickered, people bumped into nothing or walked into the drawing).
	 * Only cells that hold air or one of the {@code shell} blocks, between the corners given, whose
	 * nearest and farthest corner from (cx, cz) lie on both sides of one of the {@code radii}, and
	 * that are not {@code kept}.
	 */
	public void roundShell(double cx, double cz, double[] radii, int x0, int y0, int z0, int x1, int y1, int z1, java.util.Set<Block> shell,
			OpenTest open, OpenTest kept) {
		for (int y = y0; y <= y1; y++) {
			int row = y;

			for (int x = x0; x <= x1; x++) {
				for (int z = z0; z <= z1; z++) {
					BlockState here = this.get(x, y, z);

					if (!here.isAir() && !shell.contains(here.getBlock()) || kept.open(x + 0.5, z + 0.5, y)) {
						continue;
					}

					double near = Math.hypot(Math.max(0.0, Math.max(x - cx, cx - x - 1)), Math.max(0.0, Math.max(z - cz, cz - z - 1)));
					double far = Math.hypot(Math.max(Math.abs(x - cx), Math.abs(x + 1 - cx)), Math.max(Math.abs(z - cz), Math.abs(z + 1 - cz)));
					boolean crossed = false;

					for (double r : radii) {
						crossed |= near < r && far > r;
					}

					if (!crossed) {
						continue;
					}

					ProxyShapes.Solid solid = (px, pz) -> !open.open(px, pz, row);

					if (ProxyShapes.mixed(x, z, 16, solid)) {
						this.shaped(x, y, z, ProxyShapes.columns(x, z, 16, solid), true);
					}
				}
			}
		}
	}

	/**
	 * Phase 17: something solid that is not a whole number of blocks (a round machine, a curved
	 * couch, the edge of a dome): every cell from (x0, z0) to (x1, z1) in row y that it reaches
	 * collides with exactly its shape there, {@code height} of the row high (columns of 1/16
	 * block; a cell it fills wholly and to the top is a plain barrier). Only cells that are air
	 * or one of the {@code over} blocks are touched.
	 */
	public void solid(int x0, int z0, int x1, int z1, int y, ProxyShapes.Solid solid, double height, java.util.Set<Block> over) {
		int res = 16;

		for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
			for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
				BlockState here = this.get(x, y, z);
				boolean shaped = this.isShaped(x, y, z);

				if (!here.isAir() && !over.contains(here.getBlock()) && !shaped) {
					continue;
				}

				boolean any = false;
				boolean all = true;

				for (int i = 0; i < res; i++) {
					for (int j = 0; j < res; j++) {
						boolean s = solid.at(x + (i + 0.5) / res, z + (j + 0.5) / res);
						any |= s;
						all &= s;
					}
				}

				if (!any) {
					continue;
				}

				if (all && height >= 1.0) {
					this.set(x, y, z, Blocks.BARRIER.defaultBlockState());
				} else {
					this.addShape(x, y, z, ProxyShapes.columns(x, z, res, solid, height));
				}
			}
		}
	}

	/**
	 * Phase 17: like {@link #solid}, with a solid span per column (see {@link ProxyShapes.Span},
	 * shares of row y): cells where no column has a span are left alone.
	 */
	public void spans(int x0, int z0, int x1, int z1, int y, ProxyShapes.Span span, java.util.Set<Block> over) {
		int res = 16;

		for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
			for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
				BlockState here = this.get(x, y, z);

				if (!here.isAir() && !over.contains(here.getBlock()) && !this.isShaped(x, y, z)) {
					continue;
				}

				net.minecraft.world.phys.shapes.VoxelShape shape = ProxyShapes.spans(x, z, res, span);

				if (shape.isEmpty()) {
					continue;
				}

				this.addShape(x, y, z, shape);
			}
		}
	}

	/** Fills the box between two corners (both included). */
	public void box(int x0, int y0, int z0, int x1, int y1, int z1, String state) {
		BlockState block = state(state);

		for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
			for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
				for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
					this.set(x, y, z, block);
				}
			}
		}
	}

	/** Air in the box between two corners. */
	public void clear(int x0, int y0, int z0, int x1, int y1, int z1) {
		this.box(x0, y0, z0, x1, y1, z1, "air");
	}

	/** A closed room: walls, floor and ceiling of {@code shell}, air inside. */
	public void room(int x0, int y0, int z0, int x1, int y1, int z1, String shell) {
		this.box(x0, y0, z0, x1, y1, z1, shell);
		this.clear(x0 + 1, y0 + 1, z0 + 1, x1 - 1, y1 - 1, z1 - 1);
	}

	/** A disc of radius {@code r} around (cx, cz) at height y (cells whose centre is inside). */
	public void disc(double cx, int y, double cz, double r, String state) {
		this.ring(cx, y, cz, -1.0, r, state);
	}

	/** The cells between two radii around (cx, cz) at height y. */
	public void ring(double cx, int y, double cz, double inner, double outer, String state) {
		BlockState block = state(state);

		for (int x = Mth.floor(cx - outer - 1); x <= Mth.ceil(cx + outer + 1); x++) {
			for (int z = Mth.floor(cz - outer - 1); z <= Mth.ceil(cz + outer + 1); z++) {
				double d = Math.sqrt((x + 0.5 - cx) * (x + 0.5 - cx) + (z + 0.5 - cz) * (z + 0.5 - cz));

				if (d <= outer && d > inner) {
					this.set(x, y, z, block);
				}
			}
		}
	}

	/** A ring wall from y0 to y1. */
	public void ringWall(double cx, int y0, int y1, double cz, double inner, double outer, String state) {
		for (int y = y0; y <= y1; y++) {
			this.ring(cx, y, cz, inner, outer, state);
		}
	}

	/** A light source that cannot be seen (the vanilla light block), level 0..15. */
	public void light(int x, int y, int z, int level) {
		this.set(x, y, z, Blocks.LIGHT.defaultBlockState().setValue(net.minecraft.world.level.block.LightBlock.LEVEL, Mth.clamp(level, 0, 15)));
	}

	/** Whether the block there is air (in plot space). */
	public boolean isAir(int x, int y, int z) {
		return this.target.get(x, y, z).isAir();
	}

	public BlockState get(int x, int y, int z) {
		return this.target.get(x, y, z);
	}

	/** The block states of the plans built so far that could not be read (should be none). */
	public static java.util.Set<String> unknown() {
		return java.util.Set.copyOf(UNKNOWN);
	}

	/** How many blocks were set. */
	public int placed() {
		return this.placed;
	}
}
