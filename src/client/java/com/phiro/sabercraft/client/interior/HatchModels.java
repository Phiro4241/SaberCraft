package com.phiro.sabercraft.client.interior;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

import com.phiro.sabercraft.client.interior.MeshBuilder.Layer;
import com.phiro.sabercraft.interior.InteriorPlan;
import com.phiro.sabercraft.interior.InteriorPlan.Hatch;

/**
 * Phase 18: the hatches of the crawl ways, drawn as the level has them: closed a grille flush with
 * the wall (the wall's own vent pattern, a thin frame, a latch), open the grille swung up into the
 * room above the dark opening.
 *
 * <p>Phase 20: shut, the grille and a plate behind it fill the whole opening and the frame is drawn
 * from every side, so nothing shows through at the edges, from the room or from the crawl way.
 */
final class HatchModels {
	private HatchModels() {
	}

	static void draw(MeshBuilder b, InteriorVisuals.PlotView plot, InteriorPlan plan) {
		b.layer(Layer.SOLID).worldLight().white().anim(0).twoSided(false);

		for (Hatch hatch : plan.hatches()) {
			BlockPos cell = hatch.cell();
			boolean closed = plot.level().getBlockState(plot.origin().offset(cell)).is(Blocks.BARRIER);
			float angle = switch (hatch.room()) {
				case EAST -> Props.east();
				case NORTH -> Props.north();
				case WEST -> Props.west();
				default -> Props.south();
			};
			Props.at(b, cell.getX() + 0.5, cell.getY(), cell.getZ() + 0.5, angle);

			// The frame round the opening, standing a hair out of the wall (Phase 20: drawn from
			// all sides, so that from inside the crawl way no edge shows the room through it).
			b.tile(InteriorAtlas.IMP_METAL, 1.0F);
			b.box(-0.5F, 0.0F, 0.44F, -0.42F, 1.0F, 0.53F);
			b.box(0.42F, 0.0F, 0.44F, 0.5F, 1.0F, 0.53F);
			b.box(-0.42F, 0.92F, 0.44F, 0.42F, 1.0F, 0.53F);

			if (closed) {
				// Phase 20: the grille fills the whole opening (under the frame too), with a plate
				// behind it; both are drawn from both sides: shut, it is tight.
				b.fit(InteriorAtlas.IMP_WALL_VENT);
				b.box(-0.47F, 0.0F, 0.47F, 0.47F, 0.95F, 0.5F, MeshBuilder.SOUTH | MeshBuilder.NORTH);
				b.fit(InteriorAtlas.IMP_BLACK);
				b.box(-0.47F, 0.0F, 0.44F, 0.47F, 0.95F, 0.455F, MeshBuilder.SOUTH | MeshBuilder.NORTH);
				// The latch.
				b.box(-0.06F, 0.74F, 0.5F, 0.06F, 0.82F, 0.54F, MeshBuilder.ALL & ~MeshBuilder.NORTH);
			} else {
				// Swung up on its hinge at the top, into the room.
				b.push().translate(0.0F, 0.92F, 0.5F).rotateX(-100.0F);
				b.fit(InteriorAtlas.IMP_WALL_VENT);
				b.box(-0.42F, -0.92F, 0.0F, 0.42F, 0.0F, 0.03F);
				b.pop();
			}

			b.pop();
		}
	}
}
