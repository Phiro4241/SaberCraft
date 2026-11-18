package com.phiro.sabercraft.client.interior;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;

import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;

import com.phiro.sabercraft.SaberCraft;

/**
 * Phase 25: how the ships' thrown light is drawn ({@link MeshBuilder.Layer#LIGHT}): like the
 * glowing parts (the entity shader, unlit), but added to what is behind it (it brightens and tints
 * the surface it lies on, black adds nothing) and without writing depth, so it never hides a lamp,
 * a screen or a hologram behind or under it. Vanilla rendering only; a shader pack draws it as it
 * draws glowing entity parts.
 */
final class InteriorLight {
	static final RenderPipeline PIPELINE = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
			.withLocation(SaberCraft.id("pipeline/interior_light"))
			.withVertexShader("core/entity")
			.withFragmentShader("core/entity")
			.withShaderDefine("EMISSIVE")
			.withShaderDefine("NO_OVERLAY")
			.withShaderDefine("NO_CARDINAL_LIGHTING")
			.withBindGroupLayout(BindGroupLayouts.SAMPLER0)
			.withColorTargetState(new ColorTargetState(BlendFunction.ADDITIVE))
			.withVertexBinding(0, DefaultVertexFormat.ENTITY)
			.withPrimitiveTopology(PrimitiveTopology.QUADS)
			.withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
			.build());

	static final RenderType TYPE = RenderType.create("sabercraft_interior_light",
			RenderSetup.builder(PIPELINE).withTexture("Sampler0", InteriorScene.ATLAS).createRenderSetup());

	private InteriorLight() {
	}
}
