package com.phiro.sabercraft.client.interior;

import static com.phiro.sabercraft.interior.DestroyerInterior.FIELD_X0;
import static com.phiro.sabercraft.interior.DestroyerInterior.FIELD_X1;
import static com.phiro.sabercraft.interior.DestroyerInterior.FIELD_Z0;
import static com.phiro.sabercraft.interior.DestroyerInterior.FIELD_Z1;
import static com.phiro.sabercraft.interior.DestroyerInterior.HANGAR;

import java.util.List;

import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;

import com.phiro.sabercraft.client.interior.MeshBuilder.Layer;
import com.phiro.sabercraft.client.ship.ShipModels;
import com.phiro.sabercraft.client.story.StoryClient;
import com.phiro.sabercraft.ship.ShipType;

/**
 * Phase 26: the Falcon waits for the escape under the hangar's force field, from the moment the
 * escape begins until one has gone aboard: seen from the hangar deck through the field and the
 * shaft below it (which way out there is, before and during the duel). It hovers just under the
 * hull's belly, rocking a little, its engines idling, its running lights blinking; drawn with the
 * Star Destroyer itself (the same pass, as far off as the ship is drawn), so that it is there
 * from the first moment and never pops in as one comes near.
 *
 * <p>Phase 27: only once the boss has flown off through the hangar's field and her film is over
 * (not during her flight, which would go through it); it then rises out of the dark below.
 */
public final class HangarFalcon {
	/** The flag the server sends while the Falcon waits there. */
	public static final String WAITING = "falcon.waiting";
	/** Where it hovers (plot space): its saucer under the middle of the field, its top below the belly. */
	static final float X = (FIELD_X0 + FIELD_X1 + 1) / 2.0F;
	static final float Z = (FIELD_Z0 + FIELD_Z1 + 1) / 2.0F - 0.75F;
	static final float Y = HANGAR - 9.6F;
	/** Its saucer's radius (as the films' engines: the glow along its back edge). */
	private static final float RADIUS = 5.06F;
	private static final Mesh GLOW = new Mesh();
	private static @Nullable List<ItemStackRenderState> falcon;
	private static int drawn;
	/**
	 * Phase 27: it comes only once the boss has flown off and her film is over (the server sends the
	 * flag from then on): it rises out of the dark below the hull to its place over a few seconds,
	 * never popping up (when it was first drawn, milliseconds of {@link net.minecraft.util.Util#getMillis}
	 * (the scene's {@code seconds} wrap each hour; it rose again then); -1: not drawn now). Not
	 * while a film still runs on this client, and not at all when it was there already as the
	 * story came in (a load at the escape): then it simply waits.
	 */
	private static long since = -1L;
	private static float below;
	/** Phase 27: how long it takes to come up (seconds), and from how far below (blocks). */
	static final float ARRIVE_SECONDS = 4.5F;
	static final float ARRIVE_DEPTH = 34.0F;
	private static int arrivals;
	/** Phase 27: the flag there within this long of coming into the story: it was there already. */
	static final long ARRIVE_LOADED_MILLIS = 1500L;

	/** For tests: how far below its place it was drawn last (blocks; 0 in its place). */
	public static float belowForTest() {
		return below;
	}

	private HangarFalcon() {
	}

	/** Draws it if it waits in this plot now (the pose stack at the plot's origin). */
	static void submit(PoseStack poseStack, SubmitNodeCollector collector, ClientLevel level, BlockPos origin, float seconds, boolean shadow) {
		if (!StoryClient.has(WAITING)) {
			since = -1L;
			return;
		}

		if (!StoryClient.destroyerPlot(origin)) {
			return;
		}

		long now = net.minecraft.util.Util.getMillis();

		if (since < 0L) {
			// Phase 27: the boss's film still on screen here: it comes after it.
			if (com.phiro.sabercraft.client.story.StoryCinema.active()) {
				return;
			}

			boolean there = now - StoryClient.enteredMillis() < ARRIVE_LOADED_MILLIS;
			since = there ? now - (long) (ARRIVE_SECONDS * 1000.0F) : now;

			if (!there) {
				arrivals++;
			}
		}

		// 0 far below, 1 in its place (easing in: fast from the dark, slowing to a hover).
		float come = Mth.clamp((now - since) / (ARRIVE_SECONDS * 1000.0F), 0.0F, 1.0F);
		float ease = 1.0F - (1.0F - come) * (1.0F - come) * (1.0F - come);
		below = ARRIVE_DEPTH * (1.0F - ease);

		ShipType freighter = ShipType.byId("falcon").orElse(null);

		if (freighter == null) {
			return;
		}

		falcon = ShipModels.resolve(freighter, falcon, level, null, 1977);
		poseStack.pushPose();
		poseStack.translate(X, Y + Mth.sin(seconds * 0.8F) * 0.12F - below, Z);
		poseStack.translate(0.0F, ArrivalFilm.FALCON_MIDDLE, 0.0F);
		poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.sin(seconds * 0.55F) * 1.2F));
		// Coming up nose high, levelling out as it slows.
		poseStack.mulPose(Axis.XP.rotationDegrees(Mth.sin(seconds * 0.43F + 1.0F) * 0.8F - 9.0F * (1.0F - ease)));
		poseStack.translate(0.0F, -ArrivalFilm.FALCON_MIDDLE, 0.0F);
		ShipModels.submit(poseStack, collector, freighter, ShipModels.Pose.FLYING, falcon, LightCoordsUtil.pack(12, 0), 0);

		if (!shadow) {
			GLOW.clear();
			MeshBuilder b = ArrivalFilm.builder(GLOW);
			b.layer(Layer.GLOW).fullBright().twoSided(true).fit(InteriorAtlas.WHITE);
			lights(b, seconds, 1.0F - ease);
			ArrivalFilm.submit(collector, poseStack, Layer.GLOW, GLOW);
		}

		poseStack.popPose();
		drawn++;
	}

	/** The engines idling along the back of the saucer (a slow breath), and two blinking running lights. */
	private static void lights(MeshBuilder b, float seconds, float thrust) {
		// Phase 27: the engines bright while it climbs, idling once it hovers.
		float power = Math.min(1.0F, 0.42F + 0.06F * Mth.sin(seconds * 2.1F) + 0.5F * thrust);
		int colour = ArrivalFilm.argb(1.0F, 0.55F * power, 0.78F * power, 1.0F * power);
		float y = ArrivalFilm.FALCON_MIDDLE;

		for (float angle = -54.0F; angle <= 54.0F; angle += 9.0F) {
			float a0 = (angle - 4.5F) * Mth.DEG_TO_RAD;
			float a1 = (angle + 4.5F) * Mth.DEG_TO_RAD;
			Vector3f p0 = new Vector3f(RADIUS * Mth.sin(a0), y - 0.2F, 0.75F + RADIUS * Mth.cos(a0));
			Vector3f p1 = new Vector3f(RADIUS * Mth.sin(a1), y - 0.2F, 0.75F + RADIUS * Mth.cos(a1));
			b.colour(colour).rect(p0, new Vector3f(p1).sub(p0), new Vector3f(0.0F, 0.42F, 0.0F));
		}

		// Running lights on the rim, port red, starboard green, blinking in turn (seen from above).
		boolean on = (seconds % 1.6F) < 0.25F;
		boolean on2 = ((seconds + 0.8F) % 1.6F) < 0.25F;

		if (on) {
			b.colour(0xFFFF3020).box(-RADIUS - 0.05F, y + 0.25F, 0.55F, -RADIUS + 0.25F, y + 0.45F, 0.95F);
		}

		if (on2) {
			b.colour(0xFF30FF60).box(RADIUS - 0.25F, y + 0.25F, 0.55F, RADIUS + 0.05F, y + 0.45F, 0.95F);
		}

		b.white();
	}

	/** Phase 27: for the tests: how often it came up (was drawn after not being drawn). */
	public static int arrivalsForTest() {
		return arrivals;
	}

	/** For the tests: how often it was drawn. */
	public static int drawnForTest() {
		return drawn;
	}
}
