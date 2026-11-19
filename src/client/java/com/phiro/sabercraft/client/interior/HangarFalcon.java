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

	private HangarFalcon() {
	}

	/** Draws it if it waits in this plot now (the pose stack at the plot's origin). */
	static void submit(PoseStack poseStack, SubmitNodeCollector collector, ClientLevel level, BlockPos origin, float seconds, boolean shadow) {
		if (!StoryClient.has(WAITING) || !StoryClient.destroyerPlot(origin)) {
			return;
		}

		ShipType freighter = ShipType.byId("falcon").orElse(null);

		if (freighter == null) {
			return;
		}

		falcon = ShipModels.resolve(freighter, falcon, level, null, 1977);
		poseStack.pushPose();
		poseStack.translate(X, Y + Mth.sin(seconds * 0.8F) * 0.12F, Z);
		poseStack.translate(0.0F, ArrivalFilm.FALCON_MIDDLE, 0.0F);
		poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.sin(seconds * 0.55F) * 1.2F));
		poseStack.mulPose(Axis.XP.rotationDegrees(Mth.sin(seconds * 0.43F + 1.0F) * 0.8F));
		poseStack.translate(0.0F, -ArrivalFilm.FALCON_MIDDLE, 0.0F);
		ShipModels.submit(poseStack, collector, freighter, ShipModels.Pose.FLYING, falcon, LightCoordsUtil.pack(12, 0), 0);

		if (!shadow) {
			GLOW.clear();
			MeshBuilder b = ArrivalFilm.builder(GLOW);
			b.layer(Layer.GLOW).fullBright().twoSided(true).fit(InteriorAtlas.WHITE);
			lights(b, seconds);
			ArrivalFilm.submit(collector, poseStack, Layer.GLOW, GLOW);
		}

		poseStack.popPose();
		drawn++;
	}

	/** The engines idling along the back of the saucer (a slow breath), and two blinking running lights. */
	private static void lights(MeshBuilder b, float seconds) {
		float power = 0.42F + 0.06F * Mth.sin(seconds * 2.1F);
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

	/** For the tests: how often it was drawn. */
	public static int drawnForTest() {
		return drawn;
	}
}
