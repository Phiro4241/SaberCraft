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

	/** Phase 31: a duel begun again: nothing of the sabers' poses before (the staff, the ring) is kept. */
	static void forgetSabers() {
		SABERS.clear();
		WHIRLS.clear();
	}
	private static long lastFrame;
	private static boolean staffAway;

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
		// Phase 32: her staff away (thrown): the ring in her hand is not there to be heard.
		staffAway = StoryClient.flyingStaff(plot.origin(), partial) != null;
		// Phase 26: the halos of her blades face the camera.
		Vec3 eye = client.gameRenderer.mainCamera().position();
		InquisitorSaber.EYE.set((float) (eye.x - plot.origin().getX()), (float) (eye.y - plot.origin().getY()), (float) (eye.z - plot.origin().getZ()));

		for (StoryClient.Pose pose : people) {
			StoryFiguresPayload.Fig fig = pose.fig();

			switch (fig.kind()) {
				case "droid_mouse" -> mouse(b, pose, t);
				case "droid_r2" -> astromech(b, pose, t);
				default -> person(b, pose, t, dt, plot.origin());
			}

			if ((fig.flags() & StoryFiguresPayload.LAMP) != 0 && !"down".equals(fig.pose())) {
				beam(b, plot, pose);
			}

			bladeLight(b, plot, pose);
			spinCircle(b, pose, t);

			marks(b, pose, cameraYaw, t);
		}

		// Phase 23: her saber, struck from her hand: flying, then lying where it fell.
		float[] hilt = StoryClient.looseHilt(plot.origin(), partial);

		if (hilt != null) {
			CrewModels.looseHilt(b, hilt[0], hilt[1], hilt[2], hilt[3], hilt[4], StoryClient.twinBlade());
		}

		// Phase 25: her saber staff, thrown: spinning through the air, both blades lit.
		float[] flying = StoryClient.flyingStaff(plot.origin(), partial);

		if (flying != null) {
			CrewModels.flyingStaff(b, flying[0], flying[1], flying[2], flying[3], flying[4], RED);
		}

		b.layer(Layer.SOLID).worldLight().white().anim(0).twoSided(false);
	}

	// --- People ------------------------------------------------------------------------------------

	private static void person(MeshBuilder b, StoryClient.Pose pose, float t, float dt, net.minecraft.core.BlockPos origin) {
		StoryFiguresPayload.Fig fig = pose.fig();
		boolean down = "down".equals(fig.pose());
		String kind = kind(fig, origin);
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
		CrewModels.Gear gear = saber ? saber(fig, lit, dt, down, shadow, pose.reactP()) : rifle(fig, dt, down, pose.reactP());
		float yaw = pose.yaw();

		if (saber && shadow) {
			// Phase 32: her spin: the spinning staff let go and guided round her by her outstretched
			// hand, twice round while its circle is live, she turning with it; its blades sweep the
			// whole red circle (where the server hits), to its rim.
			float[] state0 = SABERS.get(fig.id());
			float[] whirl = whirl(pose, lit && !down && state0 != null && state0[8] > 0.35F && (fig.flags() & StoryFiguresPayload.UNARMED) == 0, dt,
					origin);

			if (whirl != null) {
				yaw = whirl[0];
				head = 0.0F;

				if (whirl[4] > 0.3F) {
					gear = gear.whirling();
					CrewModels.flyingStaff(b, whirl[1], whirl[2], whirl[3], yaw, state0 == null ? 0.0F : state0[9] * Mth.RAD_TO_DEG, RED);
				}
			}
		}

		if (saber && shadow) {
			// Phase 32: her ring's whirr, exactly while it is drawn turning (both blades out, in her hand).
			float[] state = SABERS.get(fig.id());
			boolean turns = state != null && lit && !down && !staffAway && state[8] > 0.35F && (fig.flags() & StoryFiguresPayload.UNARMED) == 0;
			float facing = yaw * Mth.DEG_TO_RAD;
			RingSound.heard(fig.id(), turns ? state[10] : 0.0F, origin.getX() + pose.x() - Mth.sin(facing) * StoryFiguresPayload.DISC_AHEAD,
					origin.getY() + pose.y() + StoryFiguresPayload.DISC_HEIGHT, origin.getZ() + pose.z() + Mth.cos(facing) * StoryFiguresPayload.DISC_AHEAD);
		}
		CrewModels.figure(b, crew, look, pose.x(), pose.y(), pose.z(), yaw, pose.walk(), t, seed, head, gear);
	}

	/**
	 * Phase 32: her spin's whirl (per figure): how far round the staff has gone (radians from where
	 * she faced as it began), her yaw then (degrees), the game time it was last drawn live, how long
	 * since it ended (seconds; below 0 while it goes on), how far the staff is out (0..1).
	 */
	private static final Map<Integer, float[]> WHIRLS = new HashMap<>();
	/** Phase 32: how far round her the staff flies in her spin (its middle), so that its blades reach the red circle's rim (less the player's half width). */
	static final float WHIRL_RADIUS = (float) StoryFiguresPayload.SPIN_RADIUS - (InquisitorSaber.EMIT + InquisitorSaber.BLADE) - 0.3F;
	/** Phase 32: at what height (over her feet) it flies round her, and how many times round while the circle is live. */
	static final float WHIRL_HEIGHT = 1.15F;
	static final float WHIRL_TURNS = 2.0F;
	/** Phase 32: how fast it goes round (radians a second: its turns in the circle's live ticks), how quickly it flies out and comes back (seconds). */
	static final float WHIRL_SPEED = WHIRL_TURNS * Mth.TWO_PI / (StoryFiguresPayload.SPIN_LIVE_TICKS / 20.0F);
	static final float WHIRL_OUT = 0.08F;
	static final float WHIRL_BACK = 0.2F;
	private static int whirls;

	/**
	 * Phase 32: her spin's whirl now, or null: her yaw as drawn (she turns with the staff), where its
	 * middle is (plot space), how far it is out. Live while the server says her spin's circle is
	 * (its pace halved under the Focus, as hers); then it flies back to her hands, she turning back.
	 */
	private static float @org.jspecify.annotations.Nullable [] whirl(StoryClient.Pose pose, boolean can, float dt, net.minecraft.core.BlockPos origin) {
		StoryFiguresPayload.Fig fig = pose.fig();
		boolean live = can && (fig.flags() & StoryFiguresPayload.SPIN_LIVE) != 0;
		Minecraft client = Minecraft.getInstance();
		long now = client.level == null ? 0L : client.level.getGameTime();
		float[] w = WHIRLS.get(fig.id());

		if (live) {
			if (w == null || w[3] >= 0.0F || now - (long) w[2] > 4L) {
				w = new float[] {0.0F, pose.yaw(), now, -1.0F, 0.0F};
				WHIRLS.put(fig.id(), w);
				whirls++;
			}

			w[0] += dt * WHIRL_SPEED * (StoryClient.focusing() ? 0.5F : 1.0F);
			w[2] = now;
			w[4] = Math.min(1.0F, w[4] + dt / WHIRL_OUT);
		} else if (w != null) {
			w[3] = Math.max(0.0F, w[3]) + dt;
			w[4] = Math.max(0.0F, 1.0F - w[3] / WHIRL_BACK);

			if (w[3] >= WHIRL_BACK || !can) {
				WHIRLS.remove(fig.id());
				return null;
			}
		}

		if (w == null) {
			return null;
		}

		float round = w[1] + w[0] * Mth.RAD_TO_DEG;
		float yaw = live ? round : round + Mth.wrapDegrees(pose.yaw() - round) * Mth.clamp(w[3] / WHIRL_BACK, 0.0F, 1.0F);
		float out = w[4] * w[4] * (3.0F - 2.0F * w[4]);
		float r = 0.55F + (WHIRL_RADIUS - 0.55F) * out;
		float rad = yaw * Mth.DEG_TO_RAD;
		return new float[] {yaw, (float) pose.x() - Mth.sin(rad) * r, (float) pose.y() + WHIRL_HEIGHT, (float) pose.z() + Mth.cos(rad) * r, out};
	}

	/** Phase 32, for the tests: her spin's whirls drawn (begun), and how far round the one of that figure has gone now (radians; -1 without one). */
	public static float[] whirlForTest(int id) {
		float[] w = WHIRLS.get(id);
		return new float[] {whirls, w == null ? -1.0F : w[0], w == null ? 0.0F : w[4]};
	}

	/** Phase 32: the duel begun again: no whirl left over. */
	static void forgetWhirls() {
		WHIRLS.clear();
	}

	/**
	 * What kind of person to draw: Kestrel in the second armour in the finale, the prisoner in her
	 * overall. Phase 26: the armour only aboard the Star Destroyer (in the Falcon's cockpit after
	 * the escape she sat in it still): back aboard the Falcon she is herself again.
	 */
	private static String kind(StoryFiguresPayload.Fig fig, net.minecraft.core.BlockPos origin) {
		if ("kestrel".equals(fig.speaker()) && StoryClient.mission() == 5 && StoryClient.has("opt.armour2") && StoryClient.destroyerPlot(origin)
				&& !StoryClient.has(com.phiro.sabercraft.story.Chapter1.CHAPTER_DONE)) {
			return Crew.TROOPER;
		}

		if ("prisoner".equals(fig.speaker())) {
			return "prisoner";
		}

		return fig.kind();
	}

	/** Phase 26, for tests: what Kestrel is drawn as in the plot with that corner now (a trooper: in the second armour). */
	static String kestrelForTest(net.minecraft.core.BlockPos origin) {
		return kind(new StoryFiguresPayload.Fig(0, Crew.REBEL, Crew.STAND, "kestrel", 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0, 0, 0, 0, 0, 0), origin);
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
	private static CrewModels.Gear saber(StoryFiguresPayload.Fig fig, boolean lit, float dt, boolean down, boolean skirt, float reactP) {
		// raise, swing, the kind last seen, its progress, the kind swung, crouch, lean, hand, aim
		float[] state = SABERS.computeIfAbsent(fig.id(), id -> new float[15]);
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
		// (Phase 29: her slam's wind-up crouches her too, a little less than a leap's.)
		state[5] = approach(state[5], kind == CrewModels.CROUCH ? progress : fig.reactKind() == CrewModels.SLAM ? 0.7F * fig.reactProgress() : 0.0F, dt * 6.0F);
		state[6] = Math.max(approach(state[6], kind == CrewModels.STAGGER && lit ? 1.0F : 0.0F, dt * 5.0F), hit(fig.id()));
		state[7] = approach(state[7], kind == CrewModels.HAND ? progress : 0.0F, dt * 8.0F);
		state[2] = kind;
		state[3] = progress;

		if (blowKind || kind == CrewModels.STAGGER || kind == CrewModels.CROUCH) {
			state[14] = kind;
		}

		if (!lit) {
			state[0] = 0.0F;
			state[1] = 0.0F;
		}

		int attack = state[1] > 0.05F ? (int) state[4] : kind;

		// Phase 32: knocked aside or crouched and let go of: the blade comes back to guard from where
		// it was (low and out, over the shoulder), not first flicked up to a blow's wind-up.
		if (!blowKind && state[1] <= 0.05F && state[0] > 0.02F && kind != CrewModels.STAGGER && kind != CrewModels.CROUCH
				&& ((int) state[14] == CrewModels.STAGGER || (int) state[14] == CrewModels.CROUCH)) {
			attack = (int) state[14];
		}
		float spin = kind == CrewModels.SPIN ? progress : 0.0F;
		int react = fig.reactKind();

		if (react != 0) {
			// Phase 23: a hit, a clash, a parry, the lock: the body shows it, the blow's own lean gives way.
			state[6] = Math.max(state[6] * 0.5F, 0.0F);
		}

		// Phase 24: in the second phase of the duel her hilt is a staff: its second blade comes out (and goes with the first).
		// (Phase 25: slower, to be seen: she takes the staff in both hands, then the second blade comes out.)
		// (Phase 31: and only while the server has her in the second phase: a fight begun again shows one blade.)
		boolean twin = StoryClient.twinBlade() && (fig.flags() & StoryFiguresPayload.TWIN) != 0;
		state[8] = approach(state[8], lit && skirt && twin ? 1.0F : 0.0F, dt * 1.6F);
		ring(state, fig, lit && skirt, state[8] > 0.35F, kind, progress, spin, react, reactP, dt);
		float blur = Mth.clamp((Math.abs(state[10]) - InquisitorSaber.BLUR_FROM) / (InquisitorSaber.SPIN_FAST - InquisitorSaber.BLUR_FROM), 0.0F, 1.0F);
		// Phase 32: her ring's mode: the disc held up before her (where the server cuts with it); raised
		// as she switches into it, lowered as she leaves it.
		float discGoal = !lit || down || state[8] <= 0.35F ? 0.0F
				: (fig.flags() & StoryFiguresPayload.DISC) != 0 ? 1.0F : react == CrewModels.RING_UP ? Mth.clamp(reactP * 1.4F, 0.0F, 1.0F) : 0.0F;
		state[13] = approach(state[13], discGoal, dt * (discGoal > state[13] ? 5.0F : 3.5F));
		// (Phase 32: a throw at the legs is let go low: the hold's aim says so, see CrewModels.ringHold.)
		float throwLow = (fig.flags() & StoryFiguresPayload.THROW_LOW) != 0 && (react == CrewModels.THROW_WIND || react == CrewModels.THROWN) ? 1.0F : 0.0F;
		return new CrewModels.Gear((fig.flags() & StoryFiguresPayload.LAMP) != 0, RED, lit, state[0], state[1], down, skirt, attack, throwLow, state[5],
				state[6], state[7], spin, react, reactP, false, (fig.flags() & StoryFiguresPayload.UNARMED) != 0, state[8], skirt, state[9], blur,
				state[11], state[13]);
	}

	/**
	 * Phase 26: her Inquisitor's ring, each frame: how fast it spins (radians a second, [10]) and
	 * how far it has turned ([9]), and how much her blades flare ([11]). Still in the first phase
	 * (one blade: the ring is locked); with both blades it spins up as a spin winds up and through
	 * it, as the ring's rush spins up and through the rush, as she draws it back to throw it; it
	 * twitches when a stroke is turned aside; it winds down slowly afterwards and stops with the
	 * blades across again (the nearest half turn). The flare: when blades meet, in the lock, and
	 * once as the last quarter of her strength begins.
	 */
	/** Phase 28: how fast the ring turns while it keeps turning (a share of its fastest; Phase 32: in her ring's mode). */
	static final float RING_CRUISE = 0.55F;
	/**
	 * Phase 32: how fast the ring turns in her ring's mode while she strikes with a blade (radians a
	 * second: under the blur, the blades seen; still heard), and how fast it is slowed to that.
	 */
	static final float RING_BLOW = 4.5F;
	static final float RING_BRAKE = 45.0F;

	/** Phase 28, for the tests: how fast that figure's ring turns now (radians a second; 0 when unknown). */
	public static float ringSpeedForTest(int id) {
		float[] state = SABERS.get(id);
		return state == null ? 0.0F : state[10];
	}

	/** Phase 31, for the tests: how far that figure's staff shows its second blade now. */
	static float twinForTest(int id) {
		float[] state = SABERS.get(id);
		return state == null ? 0.0F : state[8];
	}

	private static void ring(float[] state, StoryFiguresPayload.Fig fig, boolean inquisitor, boolean both, int kind, float progress, float spin, int react,
			float reactP, float dt) {
		float goal = 0.0F;
		// Phase 28: the server says the ring keeps turning (a state that lasts through her moves,
		// no longer spun up and down with each): it turns steadily, faster for a ring move.
		// (Phase 32: her ring's mode, for a while now and then in the second phase, not for good.)
		boolean turning = (fig.flags() & StoryFiguresPayload.RING_SPIN) != 0;

		// Phase 32: a blade's blow in her ring's mode (wound up, or falling): the ring slows to a turn
		// the blades are seen in (under its blur), so that the blow is seen struck with a blade, where
		// it hits, and not with the disc; it spins up again afterwards.
		boolean blade = kind >= CrewModels.OVERHEAD && kind <= CrewModels.THRUST || state[1] > 0.05F;

		if (inquisitor && both) {
			if (turning) {
				goal = blade ? RING_BLOW : InquisitorSaber.SPIN_FAST * RING_CRUISE;
			}

			if (kind == CrewModels.SPIN) {
				// (Phase 30: at full speed by the end of the spin's first part, when its circle goes
				// live and hits: before, it got there only towards the end of the spin, its one blow.)
				float rise = (float) StoryFiguresPayload.SPIN_RISE_TICKS / (StoryFiguresPayload.SPIN_RISE_TICKS + StoryFiguresPayload.SPIN_LIVE_TICKS);
				goal = Math.max(goal, InquisitorSaber.SPIN_FAST * Math.max(0.35F, Math.min(1.0F, progress / rise)));
			} else if (react == CrewModels.RING) {
				goal = Math.max(goal, InquisitorSaber.SPIN_FAST * (reactP < CrewModels.RING_WIND ? 0.25F + 0.75F * reactP / CrewModels.RING_WIND : 1.0F));
			} else if (react == CrewModels.THROW_WIND) {
				goal = Math.max(goal, InquisitorSaber.SPIN_FAST * 0.85F * Mth.clamp(reactP * 1.3F, 0.0F, 1.0F));
			} else if (react == CrewModels.RING_UP) {
				// Phase 32: into her ring's mode: the ring spins up to its steady turning as she raises it.
				goal = Math.max(goal, InquisitorSaber.SPIN_FAST * RING_CRUISE * Mth.clamp(0.2F + reactP * 1.1F, 0.0F, 1.0F));
			}

			if (!turning && react == CrewModels.PARRY && (int) state[12] != CrewModels.PARRY) {
				// A stroke turned aside: the ring twitches round on its hub.
				state[10] += 9.0F;
			}

			if (StoryClient.ringFlared(fig.id())) {
				state[10] = Math.max(state[10], InquisitorSaber.SPIN_FAST);
			}
		}

		float speed = state[10];

		if (goal > speed) {
			speed = Math.min(goal, speed + 34.0F * dt);
		} else {
			speed = Math.max(goal, speed - (blade && turning ? RING_BRAKE : 16.0F) * dt);
		}

		state[10] = speed;
		float angle = state[9] + speed * dt;

		if (!inquisitor || !both) {
			// One blade: the ring is locked with it where it was made to stand.
			angle = approachAngle(angle, 0.0F, Mth.TWO_PI, dt * 6.0F);
		} else if (speed < 2.5F && goal == 0.0F) {
			// Coming to rest: the blades settle across again (whichever half turn is nearer).
			angle = approachAngle(angle, 0.0F, Mth.PI, dt * (3.0F - speed));
		}

		state[9] = angle % Mth.TWO_PI;
		float flare = react == CrewModels.CLASH ? 1.0F : react == CrewModels.PARRY ? 0.55F : react == CrewModels.LOCK ? 0.6F : 0.0F;

		if (StoryClient.ringFlared(fig.id())) {
			flare = 1.0F;
		}

		state[11] = Math.max(flare, state[11] - dt * 3.5F);
		state[12] = react;
	}

	/** Phase 26: turns an angle towards the nearest of goal + n * period, at most by step. */
	private static float approachAngle(float angle, float goal, float period, float step) {
		float off = (angle - goal) % period;

		if (off > period / 2.0F) {
			off -= period;
		} else if (off < -period / 2.0F) {
			off += period;
		}

		float move = Mth.clamp(-off, -Math.max(0.0F, step), Math.max(0.0F, step));
		return angle + move;
	}

	/**
	 * Phase 26: the light her lit blades throw on the floor round her: red, brighter while the ring
	 * spins (Phase 30: no longer wider, see {@link #SPIN_GLOW}) (as the mod's sabers light what is
	 * near them).
	 */
	private static void bladeLight(MeshBuilder b, InteriorVisuals.PlotView plot, StoryClient.Pose pose) {
		StoryFiguresPayload.Fig fig = pose.fig();

		if (!"shadow".equals(fig.kind()) || (fig.flags() & StoryFiguresPayload.SABER) == 0
				|| (fig.flags() & (StoryFiguresPayload.HOSTILE | StoryFiguresPayload.LIT)) == 0 || (fig.flags() & StoryFiguresPayload.UNARMED) != 0
				|| "down".equals(fig.pose())) {
			return;
		}

		float[] state = SABERS.get(fig.id());
		float blur = state == null ? 0.0F : Mth.clamp((Math.abs(state[10]) - InquisitorSaber.BLUR_FROM) / 12.0F, 0.0F, 1.0F);
		float flare = state == null ? 0.0F : state[11];
		float both = state != null && state[8] > 0.35F ? 1.0F : 0.0F;
		float yaw = pose.yaw() * Mth.DEG_TO_RAD;
		// A little before her, where her blades are.
		float x = (float) pose.x() - Mth.sin(yaw) * 0.45F;
		float z = (float) pose.z() + Mth.cos(yaw) * 0.45F;
		float k = 0.55F + 0.2F * both + 0.25F * blur + 0.3F * flare;
		int colour = Mesh.scale(0xFF7A140C, Mth.clamp(k, 0.0F, 1.3F), false);
		// (Phase 30: no longer wider while the ring spins: up to 5.1 blocks of red light round her read
		// as a danger circle in which nothing happened. Her spin's real circle is drawn by spinCircle.)
		StoryRooms.floorLight(b, plot, x, (float) pose.y() + 0.9F, z, 0.5F, SPIN_GLOW, colour, 0);
	}

	/**
	 * Phase 30: how far the light of her blades reaches on the floor: always as far as with one blade
	 * and the ring still (3.2, as before Phase 30), never further with both blades or the ring
	 * whirling (it was 3.2 + 0.9 + 1.0 before). It fades to nothing there (from a light 0.9 above the
	 * floor and 0.45 before her: about 3.1 blocks over the floor, so no further from her than her
	 * spin's circle, {@link StoryFiguresPayload#SPIN_RADIUS}, and faint long before), so it is not
	 * taken for it.
	 */
	static final float SPIN_GLOW = 3.2F;

	/**
	 * Phase 30: her spin's danger circle, while the server says its blades whirl round her: a bright
	 * red rim exactly {@link StoryFiguresPayload#SPIN_RADIUS} round her on the floor (where the
	 * server hits), a faint red over the floor inside it, and a sweep of light going round with the
	 * blades. Only then: before it (the ring spinning up) and after it nothing lies on the floor.
	 */
	private static void spinCircle(MeshBuilder b, StoryClient.Pose pose, float t) {
		StoryFiguresPayload.Fig fig = pose.fig();

		if ((fig.flags() & StoryFiguresPayload.SPIN_LIVE) == 0 || "down".equals(fig.pose())) {
			return;
		}

		spinCircles++;
		float x = (float) pose.x();
		float z = (float) pose.z();
		float y = (float) pose.y() + 0.035F;
		float r = (float) StoryFiguresPayload.SPIN_RADIUS;
		float pulse = 0.75F + 0.25F * Mth.sin(t * 18.0F);
		b.twoSided(true).anim(0);
		// The floor inside: a faint red, a little stronger towards the rim.
		b.layer(Layer.HOLO).fullBright().fit(InteriorAtlas.WHITE);
		b.colourAlpha(0x22000000 | 0xFF2418);
		b.ring(x, y, z, 0.0F, r * 0.6F, 48, true);
		b.colourAlpha(0x38000000 | 0xFF2418);
		b.ring(x, y + 0.002F, z, r * 0.6F, r - 0.14F, 48, true);
		// The rim: bright, pulsing; a thin dark line just outside it, so that its edge reads on any floor.
		b.layer(Layer.GLOW).fullBright().colour(Mesh.scale(0xFFFF3020, pulse, false)).fit(InteriorAtlas.WHITE);
		b.ring(x, y + 0.004F, z, r - 0.14F, r, 64, true);
		b.layer(Layer.HOLO).fullBright().colourAlpha(0x70000000 | 0x200404);
		b.ring(x, y + 0.003F, z, r, r + 0.05F, 64, true);
		// A sweep going round with the blades (two, as the ring has two). (Phase 32: one, under her
		// staff as it flies round her, trailing it.)
		b.layer(Layer.GLOW).fullBright().fit(InteriorAtlas.WHITE);
		float[] whirl = WHIRLS.get(fig.id());
		float turn = whirl == null ? t * 14.0F : (whirl[1] + whirl[0] * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD + Mth.HALF_PI;

		for (int blade = 0; blade < (whirl == null ? 2 : 1); blade++) {
			for (int i = 0; i < 6; i++) {
				float a0 = turn + blade * Mth.PI - i * 0.09F;
				float a1 = a0 - 0.09F;
				float k = 1.0F - i / 6.0F;
				b.colour(Mesh.scale(0xFFFF5030, 0.25F + 0.55F * k, false));
				org.joml.Vector3f c = new org.joml.Vector3f(x, y + 0.005F, z);
				org.joml.Vector3f p0 = new org.joml.Vector3f(x + Mth.cos(a0) * (r - 0.14F), y + 0.005F, z + Mth.sin(a0) * (r - 0.14F));
				org.joml.Vector3f p1 = new org.joml.Vector3f(x + Mth.cos(a1) * (r - 0.14F), y + 0.005F, z + Mth.sin(a1) * (r - 0.14F));
				b.polyFacing(List.of(c, p0, p1), new org.joml.Vector3f(0.0F, 1.0F, 0.0F));
			}
		}

		b.twoSided(false).layer(Layer.SOLID).worldLight().white().anim(0);
	}

	private static int spinCircles;

	/** Phase 30, for the tests: how many frames her spin's danger circle was drawn. */
	public static int spinCirclesForTest() {
		return spinCircles;
	}

	/**
	 * Phase 21: a trooper's rifle comes up to the shoulder while they aim (the wind-up of a shot).
	 * Phase 23: an officer's pistol drawn (and aimed the same way); the hits, the stumbling, the
	 * fall and the comlink.
	 */
	private static CrewModels.Gear rifle(StoryFiguresPayload.Fig fig, float dt, boolean down, float reactP) {
		float[] state = SABERS.computeIfAbsent(fig.id(), id -> new float[15]);
		state[8] = approach(state[8], fig.windup() > 0 && !down ? 1.0F : 0.0F, dt * 8.0F);
		int react = fig.reactKind();
		// The old jolt only where no reaction of the server shows the hit.
		state[6] = react == 0 ? hit(fig.id()) : 0.0F;
		return new CrewModels.Gear((fig.flags() & StoryFiguresPayload.LAMP) != 0, 0, false, 0.0F, 0.0F, down, false, 0, state[8], 0.0F, state[6], 0.0F, 0.0F,
				react, reactP, (fig.flags() & StoryFiguresPayload.ARMED) != 0, false);
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
