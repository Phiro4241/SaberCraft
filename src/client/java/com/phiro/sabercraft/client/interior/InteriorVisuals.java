package com.phiro.sabercraft.client.interior;

import net.minecraft.world.phys.AABB;

/**
 * Phase 15: the models of one interior plan: built once per plot in plot space ({@link #build}),
 * and the parts that move, built every frame ({@link #animate}). Must stay in step with the
 * plan's blocks: {@link #version()} is the plan version these models fit.
 */
public interface InteriorVisuals {
	/** The plan id ({@code falcon}, {@code star_destroyer}). */
	String plan();

	/** The plan version the models are made for. */
	int version();

	/** Everything that stands still. */
	void build(MeshBuilder b);

	/** Everything that moves, at that time in seconds. */
	default void animate(MeshBuilder b, float seconds) {
	}

	/** Roughly where the models are (plot space), to skip plots that are far away. */
	AABB reach();

	/** Machines and computers heard where they stand (plot space). */
	default java.util.List<SoundSpot> sounds() {
		return java.util.List.of();
	}

	/** A positional loop: the sound's id (without the namespace), where, how loud, heard how far. */
	record SoundSpot(String sound, double x, double y, double z, float volume, float range) {
	}
}
