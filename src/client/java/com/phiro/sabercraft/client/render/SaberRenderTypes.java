package com.phiro.sabercraft.client.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;

import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.client.combat.CombatConfig;

/**
 * Render pipelines for the energy blade. Without a shader pack the blade uses the vanilla
 * {@code position_color} shader, which ignores world lighting, so the blade is always fully
 * bright, even in the dark. While an Iris shader pack is active it uses the variants further down.
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

	/** Plain white texture for the shader pipelines below, which multiply it with the vertex color. */
	public static final Identifier WHITE = SaberCraft.id("textures/misc/blade_white.png");

	/*
	 * The same three layers for use while an Iris shader pack is active. Iris swaps every pipeline
	 * for a program of the shader pack and only knows the vanilla pipelines and the ones mods hand
	 * to it. The pipelines above are unknown to it: Iris then draws them with the vanilla program
	 * into its own buffers, where the pack treats the blade as an unlit surface without normal,
	 * light or material data, and its lighting and fog wash the color out. These pipelines use the
	 * vanilla beacon beam shader and vertex format and are handed to Iris as beacon beams (see
	 * ShaderCompat), which every shader pack draws as a bright, self-lit beam. Without a shader
	 * pack they are not used, so the vanilla look stays exactly as it was.
	 */

	/** Solid core while a shader pack is active. */
	public static final RenderPipeline SHADER_CORE_PIPELINE = RenderPipelines.register(
			RenderPipeline.builder(RenderPipelines.BEACON_BEAM_SNIPPET)
					.withLocation(SaberCraft.id("pipeline/blade_core_shader"))
					.withColorTargetState(ColorTargetState.DEFAULT)
					.withDepthStencilState(DepthStencilState.DEFAULT)
					.withCull(true)
					.build()
	);

	/** Translucent colored glow while a shader pack is active. */
	public static final RenderPipeline SHADER_HAZE_PIPELINE = RenderPipelines.register(
			RenderPipeline.builder(RenderPipelines.BEACON_BEAM_SNIPPET)
					.withLocation(SaberCraft.id("pipeline/blade_haze_shader"))
					.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
					.withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
					.withCull(false)
					.build()
	);

	/** Additive bloom while a shader pack is active. */
	public static final RenderPipeline SHADER_BLOOM_PIPELINE = RenderPipelines.register(
			RenderPipeline.builder(RenderPipelines.BEACON_BEAM_SNIPPET)
					.withLocation(SaberCraft.id("pipeline/blade_bloom_shader"))
					.withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
					.withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
					.withCull(false)
					.build()
	);

	/*
	 * The first-person hand item is drawn by Iris in a pass of its own, before the clouds and the
	 * sky effects of the pack. Glow that writes no depth there gets painted over by the clouds
	 * behind it. These hand variants write depth for every layer, and skip the depth test so the
	 * layers still stack in their draw order (haze, core, bloom); nothing of the world can be in
	 * front of the hand anyway.
	 */

	public static final RenderPipeline SHADER_HAND_CORE_PIPELINE = RenderPipelines.register(
			RenderPipeline.builder(RenderPipelines.BEACON_BEAM_SNIPPET)
					.withLocation(SaberCraft.id("pipeline/blade_core_shader_hand"))
					.withColorTargetState(ColorTargetState.DEFAULT)
					.withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, true))
					.withCull(true)
					.build()
	);

	public static final RenderPipeline SHADER_HAND_HAZE_PIPELINE = RenderPipelines.register(
			RenderPipeline.builder(RenderPipelines.BEACON_BEAM_SNIPPET)
					.withLocation(SaberCraft.id("pipeline/blade_haze_shader_hand"))
					.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
					.withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, true))
					.withCull(false)
					.build()
	);

	public static final RenderPipeline SHADER_HAND_BLOOM_PIPELINE = RenderPipelines.register(
			RenderPipeline.builder(RenderPipelines.BEACON_BEAM_SNIPPET)
					.withLocation(SaberCraft.id("pipeline/blade_bloom_shader_hand"))
					.withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
					.withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, true))
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

	public static final RenderType SHADER_CORE = RenderType.create(
			"sabercraft_blade_core_shader", RenderSetup.builder(SHADER_CORE_PIPELINE).withTexture("Sampler0", WHITE).createRenderSetup()
	);

	public static final RenderType SHADER_HAZE = RenderType.create(
			"sabercraft_blade_haze_shader", RenderSetup.builder(SHADER_HAZE_PIPELINE).withTexture("Sampler0", WHITE).sortOnUpload().createRenderSetup()
	);

	public static final RenderType SHADER_BLOOM = RenderType.create(
			"sabercraft_blade_bloom_shader", RenderSetup.builder(SHADER_BLOOM_PIPELINE).withTexture("Sampler0", WHITE).createRenderSetup()
	);

	public static final RenderType SHADER_HAND_CORE = RenderType.create(
			"sabercraft_blade_core_shader_hand", RenderSetup.builder(SHADER_HAND_CORE_PIPELINE).withTexture("Sampler0", WHITE).createRenderSetup()
	);

	public static final RenderType SHADER_HAND_HAZE = RenderType.create(
			"sabercraft_blade_haze_shader_hand", RenderSetup.builder(SHADER_HAND_HAZE_PIPELINE).withTexture("Sampler0", WHITE).createRenderSetup()
	);

	public static final RenderType SHADER_HAND_BLOOM = RenderType.create(
			"sabercraft_blade_bloom_shader_hand", RenderSetup.builder(SHADER_HAND_BLOOM_PIPELINE).withTexture("Sampler0", WHITE).createRenderSetup()
	);

	private SaberRenderTypes() {
	}

	/** Forces class loading, so the pipelines are registered before the shaders are loaded. */
	public static void init() {
		ShaderCompat.registerBeamPipelines(SHADER_CORE_PIPELINE, SHADER_HAZE_PIPELINE, SHADER_BLOOM_PIPELINE,
				SHADER_HAND_CORE_PIPELINE, SHADER_HAND_HAZE_PIPELINE, SHADER_HAND_BLOOM_PIPELINE);
	}

	/** Whether the blade is drawn as a beam for the shader pack in this frame. */
	public static boolean beams() {
		return CombatConfig.get().shaderBlade && ShaderCompat.shaderPackInUse();
	}

	/**
	 * The core layer for this frame: the plain one without a shader pack, the shader pack variant
	 * while a pack is active, for the first-person hand item its hand variant.
	 */
	public static RenderType core(boolean firstPerson) {
		return !beams() ? BLADE_CORE : firstPerson ? SHADER_HAND_CORE : SHADER_CORE;
	}

	public static RenderType haze(boolean firstPerson) {
		return !beams() ? BLADE_HAZE : firstPerson ? SHADER_HAND_HAZE : SHADER_HAZE;
	}

	public static RenderType bloom(boolean firstPerson) {
		return !beams() ? BLADE_BLOOM : firstPerson ? SHADER_HAND_BLOOM : SHADER_BLOOM;
	}
}
