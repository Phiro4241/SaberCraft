package com.phiro.sabercraft.client.sky;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.concurrent.CompletableFuture;

import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Util;
import net.minecraft.world.level.Level;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.client.ImmersionConfig;
import com.phiro.sabercraft.client.render.ShaderCompat;

/**
 * The optional space sky (Phase 7): a cube around the camera with a painted picture of stars,
 * nebulae, galaxies, planets and moons, one picture per dimension ({@link SpaceSkyPreset}).
 *
 * <p>It is drawn by the vanilla sky pass, right where the vanilla stars would be, with the
 * vanilla {@code END_SKY} pipeline (textured, translucent): no own shader, no change to the
 * terrain or the fog, so it works with Sodium. It turns with the stars, fades in at dusk and out
 * at dawn and in the rain, and the vanilla stars are left out while it is shown. With the option
 * off nothing of this runs and the sky is the vanilla one. While an Iris shader pack is active it
 * stays off unless the player asks for it, as shader packs draw their own sky.
 *
 * <p>The picture is painted on a background thread when a dimension is entered or the quality
 * changes, and uploaded to the GPU once it is ready; until then the vanilla stars are shown.
 */
public final class SpaceSky {
	/** Pixels per cube side for the quality settings low, medium, high. */
	private static final int[] SIZES = {256, 512, 1024, 1536};
	private static final float DISTANCE = 100.0F;

	private static @Nullable GpuBuffer cube;
	private static @Nullable DynamicTexture texture;
	private static @Nullable String shownKey;
	private static @Nullable String pendingKey;
	private static @Nullable CompletableFuture<int[]> pending;
	private static int pendingSize;

	private SpaceSky() {
	}

	/** Whether the space sky takes the place of the vanilla stars in this frame. */
	public static boolean active() {
		ImmersionConfig config = ImmersionConfig.get();
		Minecraft client = Minecraft.getInstance();

		if (!config.spaceSky || client.level == null) {
			return false;
		} else if (!config.spaceSkyWithShaders && ShaderCompat.shaderPackInUse()) {
			return false;
		}

		prepare(client.level.dimension(), config.spaceSkyQuality);
		return texture != null && pending == null;
	}

	/**
	 * Draws the sky over the night sky of the Overworld (and other dimensions with a normal sky),
	 * with the pose of the vanilla stars, so it turns like them.
	 */
	public static void renderNight(RenderTarget target, Matrix4f starPose, float rainBrightness, float starBrightness) {
		float visibility = Math.min(1.0F, starBrightness * 2.0F) * rainBrightness;
		// The picture is laid out for midnight, when the stars are turned half around.
		draw(target, new Matrix4f(starPose).rotateX((float) Math.PI), visibility);
	}

	/** Draws the sky over the vanilla End sky. */
	public static void renderEnd(RenderTarget target) {
		if (!ImmersionConfig.get().spaceSkyInEnd) {
			return;
		}

		// The End has no day; the sky turns very slowly.
		Minecraft client = Minecraft.getInstance();
		float turn = client.level == null ? 0.0F : (client.level.getGameTime() % 24000L) / 24000.0F * (float) (Math.PI * 2.0);
		draw(target, new Matrix4f().rotationY(turn), 0.85F);
	}

	private static void draw(RenderTarget target, Matrix4f pose, float visibility) {
		if (visibility <= 0.01F || texture == null) {
			return;
		}

		if (cube == null) {
			cube = createCube(cubeSize, "SaberCraft space sky cube");
		}

		drawCube(target, pose, cube, texture, visibility, "SaberCraft space sky");
	}

	/**
	 * Draws a sky cube ({@link #createCube}) with a 3 x 2 tile picture, with the vanilla
	 * {@code END_SKY} pipeline (textured, translucent), at the given opacity.
	 */
	static void drawCube(RenderTarget target, Matrix4f pose, GpuBuffer vertices, DynamicTexture picture, float visibility, String label) {
		RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
		int count = 6 * 2 * 6;
		GpuBuffer indexBuffer = indices.getBuffer(count);
		Matrix4fStack modelView = RenderSystem.getModelViewStack();
		modelView.pushMatrix();
		modelView.mul(pose);
		GpuBufferSlice transforms = RenderSystem.getDynamicUniforms()
				.writeTransform(new Matrix4f(modelView), new Vector4f(1.0F, 1.0F, 1.0F, visibility));

		try (RenderPass pass = RenderSystem.getDevice()
				.createCommandEncoder()
				.createRenderPass(() -> label, target.getColorTextureView(), Optional.empty(), target.getDepthTextureView(),
						OptionalDouble.empty())) {
			pass.setPipeline(RenderPipelines.END_SKY);
			RenderSystem.bindDefaultUniforms(pass);
			pass.setUniform("DynamicTransforms", transforms);
			pass.bindTexture("Sampler0", picture.getTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
			pass.setVertexBuffer(0, vertices.slice());
			pass.setIndexBuffer(indexBuffer, indices.type());
			pass.drawIndexed(count, 1, 0, 0, 0);
		}

		modelView.popMatrix();
	}

	/** Starts painting the picture of a dimension, or uploads it once it is painted. */
	private static void prepare(ResourceKey<Level> dimension, int quality) {
		int size = SIZES[Math.clamp(quality, 0, SIZES.length - 1)];
		// On a Star Wars planet the night sky of the Overworld is that planet's.
		SpaceSkyPreset preset = PlanetSky.nightSky(dimension).orElseGet(() -> SpaceSkyPreset.of(dimension));
		String key = dimension.identifier() + "/" + preset.name() + "@" + size;

		if (pending != null) {
			if (!pending.isDone()) {
				return;
			}

			int[] pixels = pending.getNow(null);
			pending = null;

			if (pixels != null) {
				upload(pixels, pendingSize);
				shownKey = pendingKey;
			}
		}

		if (!key.equals(shownKey) && !key.equals(pendingKey)) {
			pendingKey = key;
			pendingSize = size;
			long start = System.nanoTime();
			pending = CompletableFuture.supplyAsync(() -> {
				int[] pixels = SpaceSkyPainter.paint(preset, size);
				SaberCraft.LOGGER.info("Painted the space sky '{}' at {} px in {} ms", preset.name(), size, (System.nanoTime() - start) / 1_000_000L);
				return pixels;
			}, Util.backgroundExecutor()).exceptionally(error -> {
				SaberCraft.LOGGER.error("Could not paint the space sky", error);
				return null;
			});
		} else if (key.equals(shownKey)) {
			pendingKey = null;
		}
	}

	private static void upload(int[] pixels, int size) {
		if (texture != null) {
			texture.close();
		}

		texture = texture(pixels, size, "SaberCraft space sky");
		cubeSize = size;
		if (cube != null) {
			cube.close();
			cube = null;
		}
	}

	private static int cubeSize = 512;

	/** A texture from ARGB pixels laid out as 3 x 2 tiles of {@code size} pixels. */
	static DynamicTexture texture(int[] pixels, int size, String label) {
		NativeImage image = new NativeImage(size * 3, size * 2, false);

		for (int y = 0; y < size * 2; y++) {
			for (int x = 0; x < size * 3; x++) {
				image.setPixel(x, y, pixels[y * size * 3 + x]);
			}
		}

		return new DynamicTexture(() -> label, image);
	}

	/** The six faces, each twice (both windings), with UVs into the 3 x 2 tiles of a picture. */
	static GpuBuffer createCube(int size, String label) {
		// Half a pixel in from the tile edges, so the faces do not pick up their neighbours.
		float inset = 0.5F / size;
		float[][] corners = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};

		try (ByteBufferBuilder bytes = ByteBufferBuilder.exactlySized(48 * DefaultVertexFormat.POSITION_TEX_COLOR.getVertexSize())) {
			BufferBuilder builder = new BufferBuilder(bytes, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
			Vector3f point = new Vector3f();

			for (int face = 0; face < 6; face++) {
				float[][] f = SpaceSkyPainter.FACES[face];
				float tileU = face % 3;
				float tileV = face / 3;

				for (int winding = 0; winding < 2; winding++) {
					for (int i = 0; i < 4; i++) {
						float[] corner = corners[winding == 0 ? i : 3 - i];
						float s = corner[0];
						float t = corner[1];
						float a = 2.0F * s - 1.0F;
						float b = 2.0F * t - 1.0F;
						point.set(f[0][0] + a * f[1][0] + b * f[2][0], f[0][1] + a * f[1][1] + b * f[2][1], f[0][2] + a * f[1][2] + b * f[2][2])
								.mul(DISTANCE);
						float u = (tileU + inset + s * (1.0F - 2.0F * inset)) / 3.0F;
						float v = (tileV + inset + t * (1.0F - 2.0F * inset)) / 2.0F;
						builder.addVertex(point.x, point.y, point.z).setUv(u, v).setColor(-1);
					}
				}
			}

			try (MeshData mesh = builder.buildOrThrow()) {
				return RenderSystem.getDevice().createBuffer(() -> label, 40, mesh.vertexBuffer());
			}
		}
	}
}
