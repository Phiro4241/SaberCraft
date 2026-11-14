package com.phiro.sabercraft.client.interior;

import org.joml.Vector3f;

import com.phiro.sabercraft.client.interior.MeshBuilder.Layer;
import com.phiro.sabercraft.interior.InteriorPlan;
import com.phiro.sabercraft.interior.InteriorPlan.Door;

/**
 * Phase 16: the sliding doors, drawn every frame where they are: two leaves meeting in the
 * middle, each sliding into the wall beside the opening (where the wall's panels hide it). A lit
 * strip along the leaves' edges, red while the door is closed, green while it opens.
 */
final class DoorModels {
	private DoorModels() {
	}

	/** A door's leaves: thickness, the leaves' tile, the frame strip's tile. */
	static void draw(MeshBuilder b, InteriorVisuals.PlotView plot, InteriorPlan plan, int leafTile, int edgeTile) {
		for (Door door : plan.doors()) {
			float open = InteriorClient.door(plot, door);
			// Eased: quick start, soft stop.
			float t = 1.0F - (1.0F - open) * (1.0F - open);
			boolean ax = door.alongX();
			float a0 = ax ? door.z0() : door.x0();
			float a1 = ax ? door.z1() + 1 : door.x1() + 1;
			float y0 = door.y0();
			float y1 = door.y1() + 1;
			float half = (a1 - a0) / 2.0F;
			float plane = (float) door.plane();
			float th = 0.07F;

			for (int side = 0; side < 2; side++) {
				// The leaf from the side post (s0) to the middle (s1), pushed into the wall by t.
				float shift = t * (half - 0.04F);
				float s0 = side == 0 ? a0 - shift : a0 + half + shift;
				float s1 = side == 0 ? a0 + half - shift : a1 + shift;
				// Not past the wall's middle on the far side, so it never shows behind the next room.
				b.layer(Layer.SOLID).worldLight().white().anim(0).fit(leafTile);

				if (ax) {
					b.box(plane - th, y0, s0, plane + th, y1 - 0.01F, s1, MeshBuilder.ALL & ~MeshBuilder.UP & ~MeshBuilder.DOWN);
				} else {
					b.box(s0, y0, plane - th, s1, y1 - 0.01F, plane + th, MeshBuilder.ALL & ~MeshBuilder.UP & ~MeshBuilder.DOWN);
				}

				// The lit edge where the leaves meet.
				float edge = side == 0 ? s1 : s0;
				int colour = open > 0.02F ? 0xFF60E070 : 0xFFE04040;
				b.layer(Layer.GLOW).fullBright().colour(colour).fit(edgeTile, 0.0F, 0.42F, 1.0F, 0.58F);

				for (float face : new float[] {plane - th - 0.004F, plane + th + 0.004F}) {
					float e0 = side == 0 ? edge - 0.08F : edge;
					float e1 = side == 0 ? edge : edge + 0.08F;
					Vector3f facing = ax ? new Vector3f(face < plane ? -1 : 1, 0, 0) : new Vector3f(0, 0, face < plane ? -1 : 1);

					if (ax) {
						b.polyFacing(java.util.List.of(new Vector3f(face, y0 + 0.1F, e0), new Vector3f(face, y0 + 0.1F, e1), new Vector3f(face, y1 - 0.1F, e1),
								new Vector3f(face, y1 - 0.1F, e0)), facing);
					} else {
						b.polyFacing(java.util.List.of(new Vector3f(e0, y0 + 0.1F, face), new Vector3f(e1, y0 + 0.1F, face), new Vector3f(e1, y1 - 0.1F, face),
								new Vector3f(e0, y1 - 0.1F, face)), facing);
					}
				}

				b.white().layer(Layer.SOLID).worldLight();
			}
		}
	}
}
