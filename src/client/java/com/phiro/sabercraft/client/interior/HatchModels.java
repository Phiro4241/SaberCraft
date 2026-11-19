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
 *
 * <p>Phase 27: a hatch one can get through is marked as maintenance access, as on any ship: a
 * yellow and black frame round it, a pull handle on the grille and a small amber lamp above it
 * (green while it stands open). The walls' own vents (only the pattern) have none of that.
 */
final class HatchModels {
	private HatchModels() {
	}

	/** Phase 27: the access lamp, shut (amber, slowly pulsing) and open (green). */
	static final int LAMP_SHUT = 0xFFE8A040;
	static final int LAMP_OPEN = 0xFF60E080;

	static void draw(MeshBuilder b, InteriorVisuals.PlotView plot, InteriorPlan plan) {
		for (Hatch hatch : plan.hatches()) {
			b.layer(Layer.SOLID).worldLight().white().anim(0).twoSided(false);
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
			// Phase 27: maintenance access: a yellow and black border round the frame, on the wall
			// (room side only), and the lamp above it.
			b.tile(InteriorAtlas.IMP_HAZARD, 0.5F);
			b.box(-0.62F, 0.0F, 0.5F, -0.5F, 1.12F, 0.515F, MeshBuilder.ALL & ~MeshBuilder.NORTH & ~MeshBuilder.EAST & ~MeshBuilder.DOWN);
			b.box(0.5F, 0.0F, 0.5F, 0.62F, 1.12F, 0.515F, MeshBuilder.ALL & ~MeshBuilder.NORTH & ~MeshBuilder.WEST & ~MeshBuilder.DOWN);
			b.box(-0.5F, 1.0F, 0.5F, 0.5F, 1.12F, 0.515F, MeshBuilder.SOUTH | MeshBuilder.UP);
			b.tile(InteriorAtlas.IMP_METAL, 1.0F);
			b.box(-0.07F, 1.15F, 0.5F, 0.07F, 1.25F, 0.55F, MeshBuilder.ALL & ~MeshBuilder.NORTH);

			if (closed) {
				// Phase 20: the grille fills the whole opening (under the frame too), with a plate
				// behind it; both are drawn from both sides: shut, it is tight.
				b.fit(InteriorAtlas.IMP_WALL_VENT);
				b.box(-0.47F, 0.0F, 0.47F, 0.47F, 0.95F, 0.5F, MeshBuilder.SOUTH | MeshBuilder.NORTH);
				// Phase 22: the plate stands a hair inside the frame; its back lay in the plane of the
				// frame's back, and where the two overlap the edges flickered (seen from the crawl way).
				b.fit(InteriorAtlas.IMP_BLACK);
				b.box(-0.47F, 0.0F, 0.448F, 0.47F, 0.95F, 0.46F, MeshBuilder.SOUTH | MeshBuilder.NORTH);
				// The latch. Phase 27: a pull handle across it (one sees at once that it opens).
				b.box(-0.06F, 0.74F, 0.5F, 0.06F, 0.82F, 0.54F, MeshBuilder.ALL & ~MeshBuilder.NORTH);
				b.tile(InteriorAtlas.IMP_METAL_LIGHT, 1.0F);
				b.box(-0.2F, 0.62F, 0.535F, 0.2F, 0.67F, 0.575F, MeshBuilder.ALL & ~MeshBuilder.NORTH);
				b.box(-0.2F, 0.62F, 0.5F, -0.16F, 0.67F, 0.535F, MeshBuilder.ALL & ~MeshBuilder.NORTH);
				b.box(0.16F, 0.62F, 0.5F, 0.2F, 0.67F, 0.535F, MeshBuilder.ALL & ~MeshBuilder.NORTH);
			} else {
				// Swung up on its hinge at the top, into the room.
				b.push().translate(0.0F, 0.92F, 0.5F).rotateX(-100.0F);
				b.fit(InteriorAtlas.IMP_WALL_VENT);
				// Phase 22: a hair narrower than the opening: its sides lay in the ends of the frame's top.
				b.box(-0.41F, -0.92F, 0.0F, 0.41F, 0.0F, 0.03F);
				b.pop();
			}

			// Phase 27: the lamp's light (its glass a hair in front of its housing).
			b.layer(Layer.GLOW).fullBright().anim(closed ? InteriorScene.ANIM_SLOW : 0).colour(closed ? LAMP_SHUT : LAMP_OPEN)
					.fit(InteriorAtlas.IMP_WALL_STRIP_GLOW, 0.0F, 0.42F, 1.0F, 0.58F);
			b.box(-0.05F, 1.17F, 0.55F, 0.05F, 1.23F, 0.56F, MeshBuilder.SOUTH);
			b.pop();
		}
	}
}
