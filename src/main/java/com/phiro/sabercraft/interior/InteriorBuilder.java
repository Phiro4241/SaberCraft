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
	private static final Map<String, BlockState> STATES = new HashMap<>();
	private static final java.util.Set<String> UNKNOWN = new java.util.TreeSet<>();
	/** No neighbour updates, no drops, but sent to clients and lit. */
	private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;

	private final ServerLevel level;
	private final BlockPos origin;
	private int placed;

	InteriorBuilder(ServerLevel level, BlockPos origin) {
		this.level = level;
		this.origin = origin;
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
		this.level.setBlock(this.origin.offset(x, y, z), state, FLAGS);
		this.placed++;
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
		return this.level.getBlockState(this.origin.offset(x, y, z)).isAir();
	}

	public BlockState get(int x, int y, int z) {
		return this.level.getBlockState(this.origin.offset(x, y, z));
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
