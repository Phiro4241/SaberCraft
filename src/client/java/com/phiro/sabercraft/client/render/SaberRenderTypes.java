package com.phiro.sabercraft.client.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;

import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;

import com.phiro.sabercraft.SaberCraft;

/**
 * Render pipelines for the energy blade. Both use the vanilla {@code position_color} shader, which
 * ignores world lighting, so the blade is always fully bright, even in the dark.
 *
 * <p>Minecraft 26.2 uses a reversed depth buffer, so the depth test is {@code GREATER_THAN_OR_EQUAL}.
 */
public final class SaberRenderTypes {
	/** Solid, unlit core. Writes depth so it hides the glow behind it correctly. */
	public static final RenderPipeline BLADE_CORE_PIPELINE = RenderPipelines.register(
			RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
					.withLocation(SaberCraft.id("pipeline/blade_core"))
					.withColorTargetState(ColorTargetState.DEFAULT)
					.withDepthStencilState(DepthStencilState.DEFAULT)
					.withCull(true)
					.build()
	);

	/**
	 * Additive bloom right around the core. Colors are added on top of what is behind, so the area
	 * next to the core gets brighter than its surroundings, like an overexposed film frame.
	 */
	public static final RenderPipeline BLADE_BLOOM_PIPELINE = RenderPipelines.register(
			RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
					.withLocation(SaberCraft.id("pipeline/blade_bloom"))
					.withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
					.withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
					.withCull(false)
					.build()
	);

	/**
	 * Translucent colored haze for the outer glow. Unlike additive blending it keeps the blade
	 * color on bright backgrounds: additive red on green grass would turn yellow.
	 *
	 * <p>Neither glow layer writes depth, so overlapping glow geometry never cuts itself off. All
	 * haze geometry has the same color, so the draw order does not change the result.
	 */
	public static final RenderPipeline BLADE_HAZE_PIPELINE = RenderPipelines.register(
			RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
					.withLocation(SaberCraft.id("pipeline/blade_haze"))
					.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
					.withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
					.withCull(false)
					.build()
	);

	public static final RenderType BLADE_CORE = RenderType.create(
			"sabercraft_blade_core", RenderSetup.builder(BLADE_CORE_PIPELINE).createRenderSetup()
	);

	public static final RenderType BLADE_BLOOM = RenderType.create(
			"sabercraft_blade_bloom", RenderSetup.builder(BLADE_BLOOM_PIPELINE).createRenderSetup()
	);

	public static final RenderType BLADE_HAZE = RenderType.create(
			"sabercraft_blade_haze", RenderSetup.builder(BLADE_HAZE_PIPELINE).createRenderSetup()
	);

	private SaberRenderTypes() {
	}

	/** Forces class loading, so the pipelines are registered before the shaders are loaded. */
	public static void init() {
	}
}
