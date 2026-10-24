package com.phiro.sabercraft.client.render;

import java.lang.reflect.Method;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.pipeline.RenderPipeline;

import com.phiro.sabercraft.SaberCraft;

/**
 * Talks to Iris, when it is installed, through its public API ({@code net.irisshaders.iris.api.v0}).
 * Iris is optional, so everything goes through reflection and does nothing without it.
 *
 * <p>Iris draws every render pipeline with a program of the active shader pack. Pipelines of
 * mods are unknown to it unless the mod hands them over with {@code IrisApi.assignPipeline};
 * otherwise Iris logs "Missing program ... in override list" and draws them with the vanilla
 * shader into its buffers, where the pack then lights and fogs them like an unlit surface. That
 * is what made the blade almost transparent with shaders before Phase 8.
 */
public final class ShaderCompat {
	private static @Nullable Object api;
	private static @Nullable Method packInUse;
	private static @Nullable Method shadowPass;
	private static boolean looked;

	private ShaderCompat() {
	}

	/** Whether an Iris shader pack is in use right now. */
	public static boolean shaderPackInUse() {
		return call(packInUse);
	}

	/** Whether Iris is drawing the shadow map right now. A light saber casts no shadow. */
	public static boolean renderingShadowPass() {
		return call(shadowPass);
	}

	/**
	 * Hands the blade pipelines to Iris as beacon beams: shader packs draw those self-lit, with
	 * their own glow, and leave them out of the lighting. In the shadow pass they map to the beacon
	 * shadow program, but the blade is not drawn there at all (see {@link #renderingShadowPass}).
	 */
	public static void registerBeamPipelines(RenderPipeline... pipelines) {
		Object instance = api();

		if (instance == null) {
			return;
		}

		try {
			Class<?> apiType = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
			Class<?> program = Class.forName("net.irisshaders.iris.api.v0.IrisProgram");
			Class<?> shadowProgram = Class.forName("net.irisshaders.iris.api.v0.IrisShadowProgram");
			Method assign = apiType.getMethod("assignPipeline", RenderPipeline.class, program);
			Method assignShadow = apiType.getMethod("assignPipelineShadow", RenderPipeline.class, shadowProgram);
			Object beam = enumValue(program, "BEACON_BEAM");
			Object shadowEntities = enumValue(shadowProgram, "SHADOW_ENTITIES");

			for (RenderPipeline pipeline : pipelines) {
				assign.invoke(instance, pipeline, beam);
				assignShadow.invoke(instance, pipeline, shadowEntities);
			}

			SaberCraft.LOGGER.info("Handed {} blade pipelines to Iris as beacon beams", pipelines.length);
		} catch (ReflectiveOperationException | LinkageError | RuntimeException error) {
			SaberCraft.LOGGER.error("Could not hand the blade pipelines to Iris; the blade may look washed out with shaders", error);
		}
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static Object enumValue(Class<?> type, String name) {
		return Enum.valueOf((Class) type, name);
	}

	private static boolean call(@Nullable Method method) {
		Object instance = api();

		if (instance == null || method == null) {
			return false;
		}

		try {
			return (boolean) method.invoke(instance);
		} catch (ReflectiveOperationException | LinkageError error) {
			return false;
		}
	}

	private static @Nullable Object api() {
		if (!looked) {
			looked = true;

			try {
				Class<?> type = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
				api = type.getMethod("getInstance").invoke(null);
				packInUse = type.getMethod("isShaderPackInUse");
				shadowPass = type.getMethod("isRenderingShadowPass");
			} catch (ReflectiveOperationException | LinkageError error) {
				api = null;
			}
		}

		return api;
	}
}
