package com.phiro.sabercraft.client.story;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.client.interior.InteriorAtlas;

/**
 * Phase 19: the faces in dialogs and over the comlink, from the tiles the ships' people are
 * drawn with (the same face one meets in the ship). Over the comlink a face is blue, with lines
 * running through it.
 */
final class StoryPortraits {
	static final Identifier ATLAS = SaberCraft.id("textures/interior/atlas.png");
	private static final int SIZE = InteriorAtlas.TILE * InteriorAtlas.GRID;

	private StoryPortraits() {
	}

	/** The tile of someone's face. */
	static int tile(String speaker) {
		return switch (speaker) {
			case "kestrel" -> InteriorAtlas.STORY_KESTREL_FACE;
			case "mira" -> InteriorAtlas.STORY_MIRA_FACE;
			case "dax" -> InteriorAtlas.CREW_SMUGGLER_FACE;
			case "rorrak" -> InteriorAtlas.CREW_WOOKIEE_FACE;
			case "hale" -> InteriorAtlas.STORY_HALE_FACE;
			case "prisoner" -> InteriorAtlas.STORY_TAMSIN_FACE;
			case "shadow" -> InteriorAtlas.STORY_SHADOW_MASK;
			case "ilyra" -> InteriorAtlas.STORY_ILYRA_FACE;
			case "m31" -> InteriorAtlas.STORY_M31_PORTRAIT;
			case "r2v7" -> InteriorAtlas.STORY_R2_PORTRAIT;
			case "funke" -> InteriorAtlas.STORY_FUNKE_PORTRAIT;
			case "terminal" -> InteriorAtlas.STORY_TERMINAL_PORTRAIT;
			case "trooper", "guard" -> InteriorAtlas.CREW_TROOPER_HELMET;
			case "tech" -> InteriorAtlas.CREW_TECH_HELMET;
			case "pilot_a", "pilot_b" -> InteriorAtlas.CREW_PILOT_HELMET;
			case "quartermaster" -> InteriorAtlas.CREW_FACE_1;
			case "vorr" -> InteriorAtlas.CREW_FACE_3;
			default -> InteriorAtlas.CREW_FACE_0;
		};
	}

	/** Someone's name in the story's language. */
	static Component name(String speaker) {
		return Component.translatable("sabercraft.story.name." + speaker);
	}

	/** A portrait in a frame; over the comlink blue with scan lines. */
	static void draw(GuiGraphicsExtractor graphics, String speaker, int x, int y, int size, boolean radio, int alpha) {
		int tile = tile(speaker);
		int u = tile % InteriorAtlas.GRID * InteriorAtlas.TILE;
		int v = tile / InteriorAtlas.GRID * InteriorAtlas.TILE;
		int a = Math.max(0, Math.min(255, alpha));
		boolean droid = "m31".equals(speaker) || "r2v7".equals(speaker) || "terminal".equals(speaker) || "funke".equals(speaker);
		graphics.fill(x - 2, y - 2, x + size + 2, y + size + 2, a * 3 / 4 << 24 | 0x0A1018);
		graphics.outline(x - 2, y - 2, size + 4, size + 4, a << 24 | (radio ? 0x4A7AB8 : 0x5A6A80));
		int tint = radio && !droid ? 0x8CB8FF : 0xFFFFFF;
		graphics.blit(RenderPipelines.GUI_TEXTURED, ATLAS, x, y, u, v, size, size, InteriorAtlas.TILE, InteriorAtlas.TILE, SIZE, SIZE, a << 24 | tint);

		if (radio) {
			// Lines running down the picture, a flicker now and then.
			long now = Util.getMillis();
			int offset = (int) (now / 60L % 4L);

			for (int line = offset; line < size; line += 4) {
				graphics.fill(x, y + line, x + size, y + line + 1, a / 4 << 24 | 0x0A2040);
			}

			if (now / 90L % 23L == 0L) {
				graphics.fill(x, y, x + size, y + size, a / 5 << 24 | 0x80C0FF);
			}
		}
	}
}
