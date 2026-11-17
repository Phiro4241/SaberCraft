package com.phiro.sabercraft.client.interior;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.joml.Vector3f;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import com.phiro.sabercraft.client.interior.MeshBuilder.Layer;
import com.phiro.sabercraft.client.story.StoryClient;
import com.phiro.sabercraft.interior.Crew;
import com.phiro.sabercraft.network.StoryFiguresPayload;

/**
 * Phase 19: the people of a story's ship, as the server moves them: the crew's figures with the
 * story's own faces (Kestrel, Mira, Hale, the prisoner, the Shadowblade), their heads turned to
 * where they look, a lamp's beam where one carries a lamp (the beam is where they see far in the
 * dark), marks over those who noticed something (a yellow or orange "?", a red "!"), the
 * Shadowblade's red blade raised before a blow, the beaten lying on the floor, and the two
 * droids before they join (M-31 caught in cables, R2-V7 clamped to the wall).
 */
final class StoryModels {
	/** The Shadowblade's blade. */
	static final int RED = 0xFFFF2A22;
	/** A lamp's beam: how far, how wide (degrees either side), its colour. */
	static final float BEAM_LENGTH = 12.0F;
	static final float BEAM_WIDE = 24.0F;
	static final float BEAM_CORE = 13.0F;
	static final int BEAM_COLOUR = 0xFFF6E8C8;

	/** Per figure: how far its saber is raised, its follow-through, its wind-up last seen. */
	private static final Map<Integer, float[]> SABERS = new HashMap<>();
	private static long lastFrame;

	private StoryModels() {
	}

	/** Whether this plot's people are the story's (then {@link #draw} draws them instead of the crew). */
	static boolean drawsCrew(InteriorVisuals.PlotView plot) {
		return StoryClient.storyPlot(plot.origin());
	}

	static void draw(MeshBuilder b, InteriorVisuals.PlotView plot, float partial) {
		long now = System.nanoTime();
		float dt = lastFrame == 0L ? 0.0F : Mth.clamp((now - lastFrame) / 1.0E9F, 0.0F, 0.1F);
		lastFrame = now;
		float t = (float) ((plot.level().getGameTime() + partial) / 20.0);
		Minecraft client = Minecraft.getInstance();
		float cameraYaw = client.gameRenderer.mainCamera().yRot();
		List<StoryClient.Pose> people = StoryClient.figures(plot.origin(), partial);

		for (StoryClient.Pose pose : people) {
			StoryFiguresPayload.Fig fig = pose.fig();

			switch (fig.kind()) {
				case "droid_mouse" -> mouse(b, pose, t);
				case "droid_r2" -> astromech(b, pose, t);
				default -> person(b, pose, t, dt);
			}

			if ((fig.flags() & StoryFiguresPayload.LAMP) != 0 && !"down".equals(fig.pose())) {
				beam(b, plot, pose);
			}

			marks(b, pose, cameraYaw, t);
		}

		b.layer(Layer.SOLID).worldLight().white().anim(0).twoSided(false);
	}

	// --- People ------------------------------------------------------------------------------------

	private static void person(MeshBuilder b, StoryClient.Pose pose, float t, float dt) {
		StoryFiguresPayload.Fig fig = pose.fig();
		boolean down = "down".equals(fig.pose());
		String kind = kind(fig);
		String crewPose = down ? Crew.STAND : fig.pose();
		Crew crew = new Crew(kind, crewPose, pose.x(), pose.y(), pose.z(), pose.yaw(), List.of(), 0.0, 0.0, "");
		int seed = fig.id() * 7 + 3;
		CrewModels.Look look = look(fig.speaker(), crew, seed);
		float turn = Mth.wrapDegrees(pose.head() - pose.yaw());
		// While nothing happens they look about by themselves.
		float head = Math.abs(turn) < 2.0F && fig.stage() == 0 ? Float.NaN : -turn;
		boolean shadow = "shadow".equals(kind);
		boolean saber = (fig.flags() & StoryFiguresPayload.SABER) != 0;
		// Phase 21: lit also before the fight (in the film), out once he flees.
		boolean lit = saber && (fig.flags() & (StoryFiguresPayload.HOSTILE | StoryFiguresPayload.LIT)) != 0;
		CrewModels.Gear gear = saber ? saber(fig, lit, dt, down, shadow) : rifle(fig, dt, down);
		CrewModels.figure(b, crew, look, pose.x(), pose.y(), pose.z(), pose.yaw(), pose.walk(), t, seed, head, gear);
	}

	/** What kind of person to draw: Kestrel in the second armour in the finale, the prisoner in her overall. */
	private static String kind(StoryFiguresPayload.Fig fig) {
		if ("kestrel".equals(fig.speaker()) && StoryClient.mission() == 5 && StoryClient.has("opt.armour2")) {
			return Crew.TROOPER;
		}

		if ("prisoner".equals(fig.speaker())) {
			return "prisoner";
		}

		return fig.kind();
	}

	/** The faces of the story's own people; everyone else looks like the crew. */
	private static CrewModels.Look look(String speaker, Crew crew, int seed) {
		if (Crew.TROOPER.equals(crew.kind())) {
			return CrewModels.look(crew, seed);
		}

		return switch (speaker) {
			case "kestrel" -> new CrewModels.Look(InteriorAtlas.STORY_KESTREL_FACE, InteriorAtlas.STORY_KESTREL_HEAD, InteriorAtlas.CREW_HAIR_TOP,
					InteriorAtlas.STORY_KESTREL_HEAD, InteriorAtlas.STORY_KESTREL_SUIT, InteriorAtlas.STORY_KESTREL_LIMB, InteriorAtlas.STORY_KESTREL_LIMB, 0,
					0xFF80422A, false);
			case "mira" -> new CrewModels.Look(InteriorAtlas.STORY_MIRA_FACE, InteriorAtlas.STORY_MIRA_HEAD, InteriorAtlas.CREW_HAIR_TOP,
					InteriorAtlas.STORY_MIRA_HEAD, InteriorAtlas.CREW_REBEL_SUIT, InteriorAtlas.CREW_REBEL_LIMB, InteriorAtlas.CREW_REBEL_LIMB, 0, 0xFF1E1A16,
					false);
			case "hale" -> new CrewModels.Look(InteriorAtlas.STORY_HALE_FACE, InteriorAtlas.STORY_HALE_HEAD, InteriorAtlas.CREW_HAIR_TOP,
					InteriorAtlas.STORY_HALE_HEAD, InteriorAtlas.CREW_UNIFORM_TUNIC, InteriorAtlas.CREW_UNIFORM_LIMB, InteriorAtlas.CREW_UNIFORM_LIMB, 0xFF34343A,
					0xFFD0BE8C, true);
			case "prisoner" -> new CrewModels.Look(InteriorAtlas.STORY_TAMSIN_FACE, InteriorAtlas.STORY_TAMSIN_HEAD, InteriorAtlas.CREW_HAIR_TOP,
					InteriorAtlas.STORY_TAMSIN_HEAD, InteriorAtlas.STORY_PRISONER_SUIT, InteriorAtlas.STORY_PRISONER_SUIT, InteriorAtlas.STORY_PRISONER_SUIT, 0,
					0xFF603C24, false);
			case "shadow" -> new CrewModels.Look(InteriorAtlas.STORY_SHADOW_MASK, InteriorAtlas.STORY_SHADOW_HOOD, InteriorAtlas.STORY_SHADOW_HOOD,
					InteriorAtlas.STORY_SHADOW_HOOD, InteriorAtlas.STORY_SHADOW_ROBE, InteriorAtlas.STORY_SHADOW_LIMB, InteriorAtlas.STORY_SHADOW_LIMB, 0, 0,
					false);
			default -> CrewModels.look(crew, seed);
		};
	}

	/**
	 * The blade of someone fighting (Phase 21: the server sends the blow's kind and how far it is
	 * wound up): raised (or drawn back, or pulled in) as the wind-up goes on, swept through when
	 * it comes, then back on guard. Also: staggering, crouching for a leap, the hand out for a push,
	 * a spin, and the jolt of a hit.
	 */
	private static CrewModels.Gear saber(StoryFiguresPayload.Fig fig, boolean lit, float dt, boolean down, boolean skirt) {
		// raise, swing, the kind last seen, its progress, the kind swung, crouch, lean, hand, aim
		float[] state = SABERS.computeIfAbsent(fig.id(), id -> new float[9]);
		int kind = fig.windup() >> 5;
		float progress = (fig.windup() & 31) / 31.0F;
		int lastKind = (int) state[2];
		boolean blowKind = kind >= CrewModels.OVERHEAD && kind <= CrewModels.THRUST;

		if (lastKind >= CrewModels.OVERHEAD && lastKind <= CrewModels.THRUST && (kind != lastKind || progress < state[3] - 0.25F)) {
			// The blow fell.
			state[0] = 0.0F;
			state[1] = 1.0F;
			state[4] = lastKind;
		}

		float raiseGoal = blowKind ? progress : kind == CrewModels.STAGGER || kind == CrewModels.CROUCH ? 1.0F : 0.0F;
		state[0] = approach(state[0], raiseGoal, dt * 9.0F);
		state[1] = Math.max(0.0F, state[1] - dt * 4.0F);
		state[5] = approach(state[5], kind == CrewModels.CROUCH ? progress : 0.0F, dt * 6.0F);
		state[6] = Math.max(approach(state[6], kind == CrewModels.STAGGER && lit ? 1.0F : 0.0F, dt * 5.0F), hit(fig.id()));
		state[7] = approach(state[7], kind == CrewModels.HAND ? progress : 0.0F, dt * 8.0F);
		state[2] = kind;
		state[3] = progress;

		if (!lit) {
			state[0] = 0.0F;
			state[1] = 0.0F;
		}

		int attack = state[1] > 0.05F ? (int) state[4] : kind;
		float spin = kind == CrewModels.SPIN ? progress : 0.0F;
		return new CrewModels.Gear((fig.flags() & StoryFiguresPayload.LAMP) != 0, RED, lit, state[0], state[1], down, skirt, attack, 0.0F, state[5],
				state[6], state[7], spin);
	}

	/** Phase 21: a trooper's rifle comes up to the shoulder while they aim (the wind-up of a shot). */
	private static CrewModels.Gear rifle(StoryFiguresPayload.Fig fig, float dt, boolean down) {
		float[] state = SABERS.computeIfAbsent(fig.id(), id -> new float[9]);
		state[8] = approach(state[8], fig.windup() > 0 && !down ? 1.0F : 0.0F, dt * 8.0F);
		state[6] = hit(fig.id());
		return new CrewModels.Gear((fig.flags() & StoryFiguresPayload.LAMP) != 0, 0, false, 0.0F, 0.0F, down, false, 0, state[8], 0.0F, state[6], 0.0F, 0.0F);
	}

	private static float approach(float value, float goal, float step) {
		return value < goal ? Math.min(goal, value + step) : Math.max(goal, value - step);
	}

	/** Phase 21: the jolt of a hit (1 just hit, fading in a quarter second). */
	private static float hit(int id) {
		return com.phiro.sabercraft.client.story.StoryClient.hitJolt(id);
	}

	// --- What they see and notice ------------------------------------------------------------------

	/** A lamp's beam: two cones of light along where they look, a little down, stopped by walls. */
	private static void beam(MeshBuilder b, InteriorVisuals.PlotView plot, StoryClient.Pose pose) {
		float yaw = pose.yaw() * Mth.DEG_TO_RAD;
		float[] at = CrewModels.LAMP_AT;
		float lx = (float) pose.x() + at[0] * Mth.cos(yaw) - at[2] * Mth.sin(yaw);
		float ly = (float) pose.y() + at[1];
		float lz = (float) pose.z() + at[0] * Mth.sin(yaw) + at[2] * Mth.cos(yaw);
		float head = pose.head() * Mth.DEG_TO_RAD;
		Vector3f dir = new Vector3f(-Mth.sin(head), -0.16F, Mth.cos(head)).normalize();
		float length = BEAM_LENGTH;
		Vec3 from = new Vec3(lx + plot.origin().getX(), ly + plot.origin().getY(), lz + plot.origin().getZ());
		Vec3 to = from.add(dir.x * length, dir.y * length, dir.z * length);
		HitResult hit = plot.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, net.minecraft.world.phys.shapes.CollisionContext.empty()));

		if (hit.getType() != HitResult.Type.MISS) {
			length = Math.max(0.6F, (float) hit.getLocation().distanceTo(from) + 0.3F);
		}

		Vector3f lamp = new Vector3f(lx, ly, lz);
		b.layer(Layer.HOLO).fullBright().anim(InteriorScene.ANIM_FLICKER).twoSided(true);

		for (int i = 0; i < 2; i++) {
			float spread = (i == 0 ? BEAM_WIDE : BEAM_CORE) * Mth.DEG_TO_RAD;
			float radius = (float) Math.tan(spread) * length;
			Vector3f far = new Vector3f(dir).mul(length).add(lamp);
			b.colourAlpha((i == 0 ? 0x1C000000 : 0x2C000000) | BEAM_COLOUR & 0xFFFFFF).fit(InteriorAtlas.STORY_BEAM);
			b.cylinder(far, new Vector3f(dir).mul(-length), radius, 0.03F, 18, 1.0F, false, false);
		}

		b.layer(Layer.SOLID).worldLight().white().anim(0).twoSided(false);
	}

	/** Over someone who noticed: "?" (yellow, then orange) or "!" (red), always facing the camera. */
	private static void marks(MeshBuilder b, StoryClient.Pose pose, float cameraYaw, float t) {
		StoryFiguresPayload.Fig fig = pose.fig();
		int stage = fig.stage();

		// Phase 22: a wounded trooper in a fight shows how much he has left (a slim bar over his
		// head, turned to the camera), so every hit can be seen to count.
		if ((fig.flags() & StoryFiguresPayload.HOSTILE) != 0 && (fig.flags() & StoryFiguresPayload.SABER) == 0 && !"down".equals(fig.pose())
				&& fig.health() < 250) {
			healthBar(b, pose, cameraYaw, fig.health() / 255.0F);
		}

		if ((fig.flags() & StoryFiguresPayload.OBSERVER) == 0 || (fig.flags() & StoryFiguresPayload.HOSTILE) != 0 || stage <= 0 || "down".equals(fig.pose())) {
			return;
		}

		int colour = stage == 1 ? 0xFFF2D848 : stage == 2 ? 0xFFF59A32 : 0xFFF2382C;
		float width = 0.22F;
		float height = 0.44F;
		float top = (float) pose.y() + ("sit".equals(fig.pose()) ? 1.55F : 2.0F) + 0.5F + Mth.sin(t * 4.0F + fig.id()) * 0.04F;
		float yaw = cameraYaw * Mth.DEG_TO_RAD;
		// The camera's right (MC yaw: forward is (-sin, 0, cos)).
		float rx = -Mth.cos(yaw);
		float rz = -Mth.sin(yaw);
		float ox = (float) pose.x() - rx * width / 2.0F;
		float oz = (float) pose.z() - rz * width / 2.0F;
		b.layer(Layer.HOLO).fullBright().anim(0).twoSided(true).colourAlpha(colour);
		b.fit(InteriorAtlas.STORY_MARKS, stage >= 3 ? 0.5F : 0.0F, 0.0F, stage >= 3 ? 1.0F : 0.5F, 1.0F);
		b.rect(ox, top, oz, rx * width, 0.0F, rz * width, 0.0F, -height, 0.0F);
		b.layer(Layer.SOLID).worldLight().white().twoSided(false);
	}

	/** Phase 22: the slim bar over a wounded trooper: dark behind, red for what is left. */
	private static void healthBar(MeshBuilder b, StoryClient.Pose pose, float cameraYaw, float health) {
		float width = 0.62F;
		float height = 0.06F;
		float top = (float) pose.y() + 2.18F;
		float yaw = cameraYaw * Mth.DEG_TO_RAD;
		float rx = -Mth.cos(yaw);
		float rz = -Mth.sin(yaw);
		// A hair towards the camera for the fill, so the two never fight over the same pixels.
		float fx = Mth.sin(yaw) * 0.01F;
		float fz = -Mth.cos(yaw) * 0.01F;
		float ox = (float) pose.x() - rx * width / 2.0F;
		float oz = (float) pose.z() - rz * width / 2.0F;
		b.layer(Layer.HOLO).fullBright().anim(0).twoSided(true).colourAlpha(0xB0101014);
		b.fit(InteriorAtlas.IMP_CEILING_LIGHT_GLOW);
		b.rect(ox - rx * 0.02F, top + 0.02F, oz - rz * 0.02F, rx * (width + 0.04F), 0.0F, rz * (width + 0.04F), 0.0F, -(height + 0.04F), 0.0F);
		float fill = Math.max(0.02F, Math.min(1.0F, health));
		b.colourAlpha(health > 0.5F ? 0xF0E8A04A : 0xF0E0503A);
		b.rect(ox + fx, top, oz + fz, rx * width * fill, 0.0F, rz * width * fill, 0.0F, -height, 0.0F);
		b.layer(Layer.SOLID).worldLight().white().twoSided(false);
	}

	// --- The droids before they join ---------------------------------------------------------------

	/** M-31: a mouse droid, tipped over in a tangle of cable, its eye blinking for help. */
	private static void mouse(MeshBuilder b, StoryClient.Pose pose, float t) {
		b.layer(Layer.SOLID).worldLight().white().anim(0).twoSided(false);
		b.push().translate(pose.x(), pose.y(), pose.z()).rotateY(-pose.yaw()).rotateZ(16.0F);
		b.fit(InteriorAtlas.IMP_BLACK);
		b.box(-0.15F, 0.06F, -0.26F, 0.15F, 0.27F, 0.24F);
		b.tile(InteriorAtlas.IMP_METAL, 1.0F);
		b.box(-0.12F, 0.27F, -0.21F, 0.12F, 0.3F, 0.17F);
		b.box(-0.13F, 0.1F, 0.24F, 0.13F, 0.22F, 0.27F);

		for (int side = -1; side <= 1; side += 2) {
			b.fit(InteriorAtlas.IMP_BLACK);
			b.box(side * 0.17F - 0.03F, 0.0F, -0.2F, side * 0.17F + 0.03F, 0.1F, -0.1F);
			b.box(side * 0.17F - 0.03F, 0.0F, 0.1F, side * 0.17F + 0.03F, 0.1F, 0.2F);
		}

		b.layer(Layer.GLOW).fullBright().anim(InteriorScene.ANIM_BLINK).colour(0xFFFF3A2A).fit(InteriorAtlas.IMP_CEILING_LIGHT_GLOW);
		b.box(-0.03F, 0.14F, 0.27F, 0.03F, 0.19F, 0.275F, MeshBuilder.SOUTH);
		b.layer(Layer.SOLID).worldLight().white().anim(0);
		b.pop();
	}

	/** R2-V7: an astromech clamped to the wall by a restraining bolt, its lights flickering. */
	private static void astromech(MeshBuilder b, StoryClient.Pose pose, float t) {
		b.layer(Layer.SOLID).worldLight().white().anim(0).twoSided(false);
		b.push().translate(pose.x(), pose.y(), pose.z()).rotateY(-pose.yaw());
		// The legs at the sides, the middle foot.
		b.tile(InteriorAtlas.IMP_METAL_LIGHT, 1.0F);

		for (int side = -1; side <= 1; side += 2) {
			b.box(side * 0.36F - 0.05F, 0.12F, -0.07F, side * 0.36F + 0.05F, 0.72F, 0.09F);
			b.box(side * 0.36F - 0.07F, 0.0F, -0.13F, side * 0.36F + 0.07F, 0.12F, 0.15F);
			b.box(side * 0.29F - 0.04F, 0.6F, -0.05F, side * 0.29F + 0.0F, 0.68F, 0.05F);
		}

		b.box(-0.08F, 0.0F, -0.1F, 0.08F, 0.2F, 0.1F);
		// The body: white, copper panels.
		b.colour(0xFFE6E8EC).fit(InteriorAtlas.WHITE);
		b.cylinder(new Vector3f(0.0F, 0.16F, 0.0F), new Vector3f(0.0F, 0.58F, 0.0F), 0.25F, 0.25F, 16, 1.0F, true, false);
		b.colour(0xFFB0602F);
		b.box(-0.1F, 0.42F, 0.2F, 0.1F, 0.58F, 0.255F);
		b.box(-0.14F, 0.24F, 0.2F, -0.03F, 0.36F, 0.25F);
		b.box(0.03F, 0.24F, 0.2F, 0.14F, 0.36F, 0.25F);
		// The dome.
		b.white().tile(InteriorAtlas.IMP_METAL_LIGHT, 1.0F);
		b.dome(new Vector3f(0.0F, 0.74F, 0.0F), 0.25F, 16, 5, 0.0F, 90.0F);
		b.colour(0xFFB0602F).fit(InteriorAtlas.WHITE);
		b.box(-0.07F, 0.82F, 0.17F, 0.07F, 0.9F, 0.23F);
		b.white();
		b.layer(Layer.GLOW).fullBright().anim(InteriorScene.ANIM_FLICKER).colour(0xFF5A78FF).fit(InteriorAtlas.IMP_CEILING_LIGHT_GLOW);
		b.box(-0.035F, 0.84F, 0.23F, 0.035F, 0.88F, 0.235F, MeshBuilder.SOUTH);
		b.anim(InteriorScene.ANIM_BLINK).colour(0xFFFF4030);
		b.box(0.1F, 0.76F, 0.2F, 0.13F, 0.79F, 0.205F, MeshBuilder.SOUTH);
		b.layer(Layer.SOLID).worldLight().white().anim(0);
		b.pop();
	}
}
