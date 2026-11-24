package com.phiro.sabercraft.client.interior;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.client.ImmersionConfig;
import com.phiro.sabercraft.client.interior.MeshBuilder.Layer;
import com.phiro.sabercraft.client.render.ShaderCompat;
import com.phiro.sabercraft.interior.InteriorPlan;
import com.phiro.sabercraft.interior.ShipInteriors;
import com.phiro.sabercraft.network.InteriorPlotsPayload;

/**
 * Phase 15: draws the detailed starship interiors and the ships around them.
 *
 * <p>The rooms of an interior are real blocks (they give the walls, floors and light), but a
 * client with SaberCraft draws a model over them: panelled walls, consoles, pipes, screens,
 * seats, holograms, and the ship's outer hull around everything, so the windows look out over
 * the ship and a spectator outside sees a whole starship. The models are built once per plot from
 * the plan's {@link InteriorVisuals} (in plot space), cut into cells of {@value #CELL} blocks so
 * only the cells in view are drawn, and lit by the blocks around them. A few parts move every
 * frame (holograms, radar, the reactor).
 *
 * <p>The server sends which plots are built ({@link InteriorPlotsPayload}); nothing else is
 * needed. Without SaberCraft a player sees the plain block rooms.
 */
public final class InteriorScene {
	/** Cells of this many blocks are culled together. */
	static final int CELL = 32;
	/** Plots further than this (from their bounds) are not drawn. */
	static final double RANGE = 1100.0;

	/** Fog in the interiors dimension starts and ends this far away (so the ships are seen whole). */
	public static final float FOG_START = 1400.0F;
	public static final float FOG_END = 2400.0F;

	public static final Identifier ATLAS = SaberCraft.id("textures/interior/atlas.png");

	// Animations (see factor()).
	/** Lamps blinking on and off, each on its own rhythm. */
	public static final int ANIM_BLINK = 1;
	/** A hologram's flicker. */
	public static final int ANIM_FLICKER = 2;
	/** A slow breathing pulse (reactor, engines). */
	public static final int ANIM_PULSE = 3;
	/** Screens that change now and then. */
	public static final int ANIM_SCREEN = 4;
	/** A slow fade in and out (warning lights). */
	public static final int ANIM_SLOW = 5;
	/** Steady, a tiny shimmer (force fields). */
	public static final int ANIM_SHIMMER = 6;
	/** Phase 16: a lamp, dimmed with the ship's lights. */
	public static final int ANIM_LAMP = 7;

	private static final List<InteriorVisuals> VISUALS = List.of(new FalconVisuals(), new DestroyerVisuals());
	private static final Map<String, Integer> PLOTS = new HashMap<>();
	private static final Map<BlockPos, Model> MODELS = new HashMap<>();
	private static int relightCursor;
	private static int lastQuads;
	private static int lastCells;
	private static long buildNanos;

	private InteriorScene() {
	}

	public static void init() {
		LevelRenderEvents.COLLECT_SUBMITS.register(InteriorScene::submit);
		ClientPlayNetworking.registerGlobalReceiver(InteriorPlotsPayload.TYPE, (payload, context) -> {
			PLOTS.clear();

			for (InteriorPlotsPayload.Plot plot : payload.plots()) {
				PLOTS.put(plot.plan() + "/" + plot.index(), plot.version());
			}

			// Plots rebuilt from a newer plan get new models.
			MODELS.entrySet().removeIf(entry -> !PLOTS.containsKey(entry.getValue().key) || PLOTS.get(entry.getValue().key) != entry.getValue().version);
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			PLOTS.clear();
			MODELS.clear();
		});
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.level == null || !ShipInteriors.isInterior(client.level)) {
				MODELS.clear();
				InteriorSounds.stopAll();
			} else {
				InteriorSounds.tick(client, MODELS.values());
			}
		});
	}

	/** Whether the fog should be pushed back in this level (the interiors, with the detailed models on). */
	public static boolean clearFog(@Nullable ClientLevel level) {
		return level != null && ShipInteriors.isInterior(level) && (ImmersionConfig.get().shipInteriorDetail || com.phiro.sabercraft.client.story.StoryCinema.active());
	}

	/** The visuals of a plan, or {@code null}. */
	public static @Nullable InteriorVisuals visuals(String plan) {
		for (InteriorVisuals visuals : VISUALS) {
			if (visuals.plan().equals(plan)) {
				return visuals;
			}
		}

		return null;
	}

	// --- Models ----------------------------------------------------------------------------------

	/** One built plot: its cells. */
	static final class Model {
		final String key;
		final int version;
		final BlockPos origin;
		final InteriorVisuals visuals;
		final List<Cell> cells = new ArrayList<>();
		final EnumMap<Layer, Mesh> moving = new EnumMap<>(Layer.class);
		final AABB bounds;
		int quads;
		/** Phase 17: the step of the plot's dimmer (from its light blocks, looked at now and then). */
		int lightStep;
		/** Phase 17: how bright the rooms are drawn now (fades smoothly to the step's brightness). */
		float brightness = 1.0F;
		float brightnessAt = Float.NaN;
		/** Phase 17: the cells need their light looked up again (the lights came back on). */
		boolean relightAll;
		/** Phase 20: how bright a story's ship is on its emergency lights (fades in and out). */
		float storyDim = 1.0F;
		/** Phase 29: rays through its rooms for the film camera (built when first asked for). */
		@Nullable MeshRays rays;

		Model(String key, int version, BlockPos origin, InteriorVisuals visuals) {
			this.key = key;
			this.version = version;
			this.origin = origin;
			this.visuals = visuals;
			Map<Long, Cell> byKey = new HashMap<>();
			MeshBuilder builder = new MeshBuilder((layer, xyzuv, nx, ny, nz, colours, lightMode, fixedLight, anim, samples) -> {
				float cx = (xyzuv[0] + xyzuv[5] + xyzuv[10] + xyzuv[15]) * 0.25F;
				float cy = (xyzuv[1] + xyzuv[6] + xyzuv[11] + xyzuv[16]) * 0.25F;
				float cz = (xyzuv[2] + xyzuv[7] + xyzuv[12] + xyzuv[17]) * 0.25F;
				int kx = Mth.floor(cx / CELL);
				int ky = Mth.floor(cy / CELL);
				int kz = Mth.floor(cz / CELL);
				long k = BlockPos.asLong(kx, ky, kz);
				Cell cell = byKey.computeIfAbsent(k, unused -> new Cell());
				cell.mesh(layer).add(xyzuv, nx, ny, nz, colours, lightMode, fixedLight, anim, samples);
				cell.include(xyzuv);
			});
			long start = System.nanoTime();
			visuals.build(builder);
			buildNanos = System.nanoTime() - start;
			this.quads = builder.quads();
			this.cells.addAll(byKey.values());
			AABB all = null;

			for (Cell cell : this.cells) {
				cell.bounds = new AABB(cell.minX, cell.minY, cell.minZ, cell.maxX, cell.maxY, cell.maxZ).move(origin.getX(), origin.getY(), origin.getZ());
				all = all == null ? cell.bounds : all.minmax(cell.bounds);
			}

			this.bounds = all == null ? new AABB(origin) : all;
		}
	}

	static final class Cell {
		final EnumMap<Layer, Mesh> meshes = new EnumMap<>(Layer.class);
		float minX = Float.MAX_VALUE;
		float minY = Float.MAX_VALUE;
		float minZ = Float.MAX_VALUE;
		float maxX = -Float.MAX_VALUE;
		float maxY = -Float.MAX_VALUE;
		float maxZ = -Float.MAX_VALUE;
		AABB bounds;
		boolean lit;

		Mesh mesh(Layer layer) {
			return this.meshes.computeIfAbsent(layer, unused -> new Mesh());
		}

		void include(float[] xyzuv) {
			for (int i = 0; i < 4; i++) {
				this.minX = Math.min(this.minX, xyzuv[i * 5]);
				this.minY = Math.min(this.minY, xyzuv[i * 5 + 1]);
				this.minZ = Math.min(this.minZ, xyzuv[i * 5 + 2]);
				this.maxX = Math.max(this.maxX, xyzuv[i * 5]);
				this.maxY = Math.max(this.maxY, xyzuv[i * 5 + 1]);
				this.maxZ = Math.max(this.maxZ, xyzuv[i * 5 + 2]);
			}
		}
	}

	private static @Nullable Model model(String plan, int index, int version) {
		InteriorPlan interior = InteriorPlan.of(plan);
		InteriorVisuals visuals = visuals(plan);

		if (interior == null || visuals == null || visuals.version() != version) {
			// An interior from an older plan: its blocks do not match the models; plain blocks then.
			return null;
		}

		BlockPos origin = ShipInteriors.origin(interior, index);
		Model model = MODELS.get(origin);

		if (model == null) {
			try {
				model = new Model(plan + "/" + index, version, origin, visuals);
			} catch (RuntimeException error) {
				SaberCraft.LOGGER.error("Could not build the {} interior model", plan, error);
				return null;
			}

			SaberCraft.LOGGER.info("Built the {} interior model: {} quads in {} cells, {} ms", plan, model.quads, model.cells.size(),
					buildNanos / 1_000_000L);
			MODELS.put(origin, model);
		}

		return model;
	}

	// --- Drawing ---------------------------------------------------------------------------------

	private static void submit(LevelRenderContext context) {
		Minecraft client = Minecraft.getInstance();
		ClientLevel level = client.level;

		if (level == null || !ShipInteriors.isInterior(level) || !ImmersionConfig.get().shipInteriorDetail || PLOTS.isEmpty()
				|| com.phiro.sabercraft.client.story.StoryCinema.elsewhere()) {
			return;
		}

		boolean shadow = ShaderCompat.renderingShadowPass();
		Vec3 camera = context.levelState().cameraRenderState.pos;
		Frustum frustum = context.levelState().cameraRenderState.cullFrustum;
		PoseStack poseStack = context.poseStack();
		SubmitNodeCollector collector = context.submitNodeCollector();
		float seconds = (Util.getMillis() % 3_600_000L) / 1000.0F;
		int quads = 0;
		int cells = 0;
		// Phase 17: from inside a plot only its own ship is drawn: the plots lie a long way apart,
		// but the ships are drawn from far off, and one saw the Falcon (or another player's ship)
		// out in the distance through the Star Destroyer's bridge windows.
		BlockPos inside = ShipInteriors.originAt(camera.x, camera.z);

		for (Map.Entry<String, Integer> entry : PLOTS.entrySet()) {
			String[] parts = entry.getKey().split("/", 2);
			InteriorPlan plan = InteriorPlan.of(parts[0]);

			if (plan == null) {
				continue;
			}

			int index;

			try {
				index = Integer.parseInt(parts[1]);
			} catch (NumberFormatException error) {
				continue;
			}

			BlockPos origin = ShipInteriors.origin(plan, index);

			if (inside != null && !inside.equals(origin)) {
				continue;
			}

			InteriorVisuals visuals = visuals(parts[0]);

			// Quick range check before building anything.
			if (visuals == null || visuals.reach().move(origin.getX(), origin.getY(), origin.getZ()).inflate(RANGE).contains(camera) == false) {
				continue;
			}

			Model model = model(parts[0], index, entry.getValue());

			if (model == null) {
				continue;
			}

			if (level.getGameTime() % 5 == 0 || Float.isNaN(model.brightnessAt)) {
				int step = InteriorClient.lightStep(level, model.origin, plan);

				if (step == 0 && model.lightStep != 0) {
					model.relightAll = true;
				}

				model.lightStep = step;
			}

			// The models keep the light they had with the lamps on (the dimming is drawn as a
			// smooth fade instead of the light blocks' steps), so they are relit only at full light.
			if (model.lightStep == 0) {
				if (model.relightAll) {
					model.relightAll = false;

					for (Cell cell : model.cells) {
						lightCell(level, model, cell);
					}
				}

				relight(level, model);
			}

			float target = com.phiro.sabercraft.interior.ShipLights.surfaces(model.lightStep);

			if (Float.isNaN(model.brightnessAt)) {
				model.brightness = target;
			} else {
				float dt = Mth.clamp(seconds - model.brightnessAt, 0.0F, 0.25F);
				// About a second from one step to the next, eased.
				model.brightness += (target - model.brightness) * Math.min(1.0F, dt * 3.0F);

				if (Math.abs(target - model.brightness) < 0.002F) {
					model.brightness = target;
				}
			}

			// Phase 20: a story's ship on emergency lights (dim, warm, red lamps) or in an alarm (red, pulsing).
			float[] light = com.phiro.sabercraft.client.story.StoryClient.shipLight(model.origin, seconds);
			float storyTarget = light != null ? light[6] : 1.0F;
			float storyStep = Float.isNaN(model.brightnessAt) ? 1.0F : Math.min(1.0F, Mth.clamp(seconds - model.brightnessAt, 0.0F, 0.25F) * 2.0F);
			model.storyDim += (storyTarget - model.storyDim) * storyStep;
			model.brightnessAt = seconds;
			float dim = model.brightness * model.storyDim;

			// Phase 31: at the story's brightness "Maximal" its rooms are less dark (bright stays bright).
			if (com.phiro.sabercraft.client.story.StoryClient.storyPlot(model.origin)) {
				dim = com.phiro.sabercraft.client.story.StoryBrightness.shade(dim, com.phiro.sabercraft.client.story.StoryBrightness.level());
			}
			poseStack.pushPose();
			poseStack.translate(origin.getX() - camera.x, origin.getY() - camera.y, origin.getZ() - camera.z);

			for (Cell cell : model.cells) {
				if (!frustum.isVisible(cell.bounds)) {
					continue;
				}

				cells++;

				for (Map.Entry<Layer, Mesh> layer : cell.meshes.entrySet()) {
					Mesh mesh = layer.getValue();

					if (mesh.isEmpty() || shadow && layer.getKey() != Layer.SOLID) {
						continue;
					}

					quads += mesh.count;
					submitMesh(collector, poseStack, layer.getKey(), mesh, seconds, dim, light);
				}
			}

			// The moving parts, built anew every frame.
			Map<Layer, Mesh> moving = animate(level, model, seconds);

			for (Map.Entry<Layer, Mesh> layer : moving.entrySet()) {
				if (!layer.getValue().isEmpty() && !(shadow && layer.getKey() != Layer.SOLID)) {
					quads += layer.getValue().count;
					submitMesh(collector, poseStack, layer.getKey(), layer.getValue(), seconds, dim, light);
				}
			}

			// Phase 26: the Falcon waiting under the hangar during the story's escape.
			HangarFalcon.submit(poseStack, collector, level, origin, seconds, shadow);

			poseStack.popPose();
		}

		lastQuads = quads;
		lastCells = cells;
	}

	private static void submitMesh(SubmitNodeCollector collector, PoseStack poseStack, Layer layer, Mesh mesh, float seconds, float brightness, float @org.jspecify.annotations.Nullable [] tint) {
		boolean alpha = layer == Layer.GLASS || layer == Layer.HOLO;
		float glow = com.phiro.sabercraft.interior.ShipLights.glow(brightness);
		// A lamp is dimmed by the glow's share already: the rest makes it as dark as a lamp gets.
		float lamp = com.phiro.sabercraft.interior.ShipLights.lamps(brightness) / glow;
		collector.submitCustomGeometry(poseStack, renderType(layer), (pose, buffer) -> mesh.emit(pose, buffer,
				(anim, quad) -> anim == ANIM_LAMP ? lamp : factor(anim, quad, seconds), alpha, brightness, glow, tint));
	}

	private static RenderType renderType(Layer layer) {
		return switch (layer) {
			case SOLID -> RenderTypes.entityCutoutCull(ATLAS);
			case GLOW -> RenderTypes.eyes(ATLAS);
			// Phase 17: translucent like the banner patterns, without writing depth. The translucent
			// passes come in an order that changes from one game to the next (batched by render
			// type in a hash map): the glass, writing depth, hid the glowing and holographic parts
			// behind it whenever it came first (the hyperspace lines and rings seen through the
			// cockpit window were there in one game and gone in the next).
			case GLASS -> RenderTypes.bannerPattern(ATLAS);
			case HOLO -> RenderTypes.entityTranslucentEmissive(ATLAS);
			// Phase 25: added light (see InteriorLight).
			case LIGHT -> InteriorLight.TYPE;
		};
	}

	/**
	 * Builds the moving parts of a model anew. Each model keeps its own meshes: the geometry is
	 * emitted later in the frame, after every model has been animated.
	 */
	private static Map<Layer, Mesh> animate(ClientLevel level, Model model, float seconds) {
		Map<Layer, Mesh> moving = model.moving;

		for (Mesh mesh : moving.values()) {
			mesh.clear();
		}

		MeshBuilder builder = new MeshBuilder((layer, xyzuv, nx, ny, nz, colours, lightMode, fixedLight, anim, samples) -> {
			Mesh mesh = moving.computeIfAbsent(layer, unused -> new Mesh());
			mesh.add(xyzuv, nx, ny, nz, colours, lightMode, fixedLight, anim, samples);
		});
		model.visuals.animate(builder, seconds, new InteriorVisuals.PlotView(model.origin, level));

		for (Mesh mesh : moving.values()) {
			mesh.relight((x, y, z) -> light(level, model.origin, x, y, z));
		}

		return moving;
	}

	/** Looks up the light of the cells of a few model cells every frame, all of them when new. */
	private static void relight(ClientLevel level, Model model) {
		int budget = 3;

		for (Cell cell : model.cells) {
			if (!cell.lit) {
				lightCell(level, model, cell);
				cell.lit = true;
			}
		}

		for (int i = 0; i < budget && !model.cells.isEmpty(); i++) {
			relightCursor = (relightCursor + 1) % model.cells.size();
			lightCell(level, model, model.cells.get(relightCursor));
		}
	}

	private static void lightCell(ClientLevel level, Model model, Cell cell) {
		for (Mesh mesh : cell.meshes.values()) {
			mesh.relight((x, y, z) -> light(level, model.origin, x, y, z));
		}
	}

	/** The least block light the models get: the ships' rooms are dim, never black. */
	static final int MIN_LIGHT = 7;

	private static final BlockPos.MutableBlockPos LOOKUP = new BlockPos.MutableBlockPos();

	private static int light(ClientLevel level, BlockPos origin, int x, int y, int z) {
		LOOKUP.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);

		if (level.getBlockState(LOOKUP).canOcclude()) {
			return -1;
		}

		return LightCoordsUtil.pack(Math.max(MIN_LIGHT, level.getBrightness(LightLayer.BLOCK, LOOKUP)), level.getBrightness(LightLayer.SKY, LOOKUP));
	}

	/** How bright an animated quad is now. */
	static float factor(int anim, int quad, float seconds) {
		int seed = quad * 7919 + anim * 104729;

		return switch (anim) {
			case ANIM_BLINK -> {
				float period = 0.7F + (seed & 7) * 0.23F;
				float phase = ((seed >>> 3) & 15) / 16.0F;
				float t = (seconds / period + phase) % 1.0F;
				yield t < 0.55F ? 1.0F : 0.12F;
			}
			case ANIM_FLICKER -> 0.78F + 0.12F * Mth.sin(seconds * 9.0F + (seed & 31)) + 0.1F * Mth.sin(seconds * 23.0F + (seed & 15));
			case ANIM_PULSE -> 0.72F + 0.28F * Mth.sin(seconds * 1.7F);
			case ANIM_SCREEN -> {
				// Phase 22: a screen now and then changes its picture, dimming softly for a moment;
				// it used to drop to a third at once, which read as a broken, flickering terminal.
				float period = 1.6F + (seed & 3) * 0.7F;
				float steps = seconds / period;
				int step = Mth.floor(steps) + (seed & 63);

				if ((step * 2654435761L & 0xFF) >= 40) {
					yield 1.0F;
				}

				float t = steps - Mth.floor(steps);
				yield 1.0F - 0.25F * Mth.sin(t * Mth.PI);
			}
			case ANIM_SLOW -> 0.35F + 0.65F * (0.5F + 0.5F * Mth.sin(seconds * 1.2F + (seed & 7)));
			case ANIM_SHIMMER -> 0.85F + 0.15F * Mth.sin(seconds * 3.0F + (seed & 15) * 0.4F);
			default -> 1.0F;
		};
	}

	/**
	 * Phase 29: the rays through the drawn rooms round that point (world space), for the film
	 * camera; null where no detailed model is drawn there (the plain blocks then are the walls).
	 */
	public static @Nullable MeshRays rays(Vec3 point) {
		for (Model model : MODELS.values()) {
			if (!model.bounds.inflate(4.0).contains(point)) {
				continue;
			}

			if (model.rays == null) {
				model.rays = new MeshRays(model);
				SaberCraft.LOGGER.info("Built the rays of the {} interior: {} quads in {} ms", model.key, model.rays.quads(), model.rays.buildMillis());
			}

			return model.rays;
		}

		return null;
	}

	// --- For tests -----------------------------------------------------------------------------

	/** Phase 17: whether the model of the plot at that corner would be drawn from a camera there (for tests). */
	public static boolean drawnFrom(BlockPos origin, Vec3 camera) {
		BlockPos inside = ShipInteriors.originAt(camera.x, camera.z);
		return inside == null || inside.equals(origin);
	}

	/** Phase 17: how bright the rooms of the (first) plot of that plan are drawn now, for tests. */
	public static float brightnessForTest(String plan) {
		for (Model model : MODELS.values()) {
			if (model.key.startsWith(plan + "/")) {
				return model.brightness;
			}
		}

		return -1.0F;
	}

	/** Phase 17: how opaque the hyperspace shell round the Falcon was in the last frame it was built (0 = none), for tests. */
	public static float hyperspaceShellForTest() {
		return FalconJump.shellDrawn;
	}

	/** How many quads were drawn in the last frame. */
	public static int lastQuads() {
		return lastQuads;
	}

	/** How many cells were in view in the last frame. */
	public static int lastCells() {
		return lastCells;
	}

	/** How many machine and computer sounds play now (for tests). */
	public static int soundSpotsPlaying() {
		return InteriorSounds.playing();
	}

	/** Whether the client knows of a built plot of that plan. */
	public static boolean knowsPlot(String plan) {
		return PLOTS.keySet().stream().anyMatch(key -> key.startsWith(plan + "/"));
	}

	/** The model of the plot at that corner, built if needed (or {@code null}), for tests. */
	public static int modelQuads(String plan) {
		for (Model model : MODELS.values()) {
			if (model.key.startsWith(plan + "/")) {
				return model.quads;
			}
		}

		return 0;
	}

	/** Builds the model of a plan once and returns its quad count (for tests and timing). */
	public static int buildForTest(String plan) {
		InteriorVisuals visuals = visuals(plan);
		InteriorPlan interior = InteriorPlan.of(plan);
		return visuals == null || interior == null ? -1 : new Model(plan + "/test", visuals.version(), BlockPos.ZERO, visuals).quads;
	}

	/** Milliseconds the last model took to build. */
	public static long lastBuildMillis() {
		return buildNanos / 1_000_000L;
	}
}
