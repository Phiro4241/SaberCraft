package com.phiro.sabercraft.client.menu;

import org.joml.Matrix3x2f;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.TextAlignment;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;

import net.fabricmc.loader.api.FabricLoader;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.client.ImmersionConfig;

/**
 * Phase 24: the title screen in SaberCraft's style.
 *
 * <p>The background is our own picture (drawn by {@code tools/make_title_background.py}, no game
 * or film files): a space scene that drifts slowly, with a few twinkling stars on top and dark
 * gradients so the logo and the buttons stay easy to read. It is also used behind the other menus
 * before a world is loaded (options, world list), blurred by the game as usual. The yellow splash
 * text becomes a random SaberCraft saying in a blade's colour, a small "SaberCraft" line with two
 * blades sits under the Minecraft logo (which stays as it is), and the title screen's buttons
 * get a dark panel with cut corners, steel edges and a blue glow when the mouse is on them.
 *
 * <p>Purely drawing: every button keeps its own function. {@code title_screen} in
 * {@code immersion_client.json} switches all of it off.
 */
public final class TitleStyle {
	public static final Identifier BACKGROUND = Identifier.fromNamespaceAndPath(SaberCraft.MOD_ID, "textures/gui/title/background.png");
	private static final int IMAGE_W = 1600;
	private static final int IMAGE_H = 900;
	/** Number of sayings, {@code sabercraft.title.splash.0} to {@code .N-1}. */
	public static final int SAYINGS = 24;
	/** The blades' colours the sayings take turns with: blue, green, red, purple. */
	private static final int[] SAYING_COLOURS = {0x9AD8FF, 0xA6FF94, 0xFF8A80, 0xD9A6FF};
	/** Twinkling stars, in the picture's pixels: x, y, phase, speed (dark parts of the sky only). */
	private static final float[][] TWINKLE = twinkle();
	private static int lastSaying = -1;
	private static String version;

	private TitleStyle() {
	}

	public static boolean enabled() {
		return ImmersionConfig.get().titleScreen;
	}

	/** Whether the buttons get SaberCraft's look: on the title screen only, so the other menus stay as they are. */
	public static boolean styledButtons() {
		return enabled() && Minecraft.getInstance().gui.screen() instanceof TitleScreen;
	}

	// --- Background ---------------------------------------------------------------------------------

	/** Draws the background over the whole screen, in place of the panorama. */
	public static void background(GuiGraphicsExtractor graphics, int width, int height) {
		double seconds = Util.getMillis() / 1000.0;
		// Cover the screen, a little larger, so the slow drift never shows an edge.
		float zoom = 1.07F + 0.02F * (float) Math.sin(seconds * 0.021);
		float scale = Math.max((float) width / IMAGE_W, (float) height / IMAGE_H) * zoom;
		float spareX = Math.max(0.0F, IMAGE_W * scale - width) * 0.5F;
		float spareY = Math.max(0.0F, IMAGE_H * scale - height) * 0.5F;
		float driftX = spareX * 0.8F * (float) Math.sin(seconds * 0.027);
		float driftY = spareY * 0.6F * (float) Math.sin(seconds * 0.019 + 1.3);
		graphics.fill(0, 0, width, height, 0xFF04060A);
		graphics.pose().pushMatrix();
		graphics.pose().translate(width * 0.5F + driftX, height * 0.5F + driftY);
		graphics.pose().scale(scale, scale);
		graphics.pose().translate(-IMAGE_W * 0.5F, -IMAGE_H * 0.5F);
		graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, 0, 0, 0.0F, 0.0F, IMAGE_W, IMAGE_H, IMAGE_W, IMAGE_H);
		// Twinkling: small crosses that brighten and fade, each at its own pace.
		for (float[] star : TWINKLE) {
			float glow = (float) Math.sin(seconds * star[3] + star[2]);
			if (glow <= 0.15F) {
				continue;
			}
			int alpha = Mth.clamp(Math.round((glow - 0.15F) / 0.85F * 200.0F), 0, 255);
			int x = Math.round(star[0]);
			int y = Math.round(star[1]);
			graphics.fill(x - 1, y - 1, x + 2, y + 2, alpha << 24 | 0xE8F2FF);
			graphics.fill(x - 4, y, x + 5, y + 1, (alpha / 3) << 24 | 0xCFE4FF);
			graphics.fill(x, y - 4, x + 1, y + 5, (alpha / 3) << 24 | 0xCFE4FF);
		}
		graphics.pose().popMatrix();
		// Readability: darker at the top (logo) and the bottom (texts, buttons), gently.
		int top = Math.max(1, height / 3);
		graphics.fillGradient(0, 0, width, top, 0x8A02040A, 0x0002040A);
		graphics.fillGradient(0, height - height / 3, width, height, 0x0002040A, 0x9002040A);
	}

	private static float[][] twinkle() {
		RandomSource random = RandomSource.create(2477L);
		float[][] stars = new float[34][];
		int found = 0;
		for (int tries = 0; tries < 2000 && found < stars.length; tries++) {
			float x = 20 + random.nextFloat() * (IMAGE_W - 40);
			float y = 20 + random.nextFloat() * (IMAGE_H * 0.85F);
			// Not on the planet (its disc at the lower left) and not on the ship (to the right).
			float px = (x - 216.7F) / 816.7F;
			float py = (y - 1266.7F) / 816.7F;
			if (px * px + py * py < 1.06F) {
				continue;
			}
			if (x > 880 && y > 150 && y < 560 && (y > 560 - (x - 880) * 0.62F)) {
				continue;
			}
			stars[found++] = new float[] {x, y, random.nextFloat() * 6.28F, 0.6F + random.nextFloat() * 1.6F};
		}
		return java.util.Arrays.copyOf(stars, found);
	}

	// --- The saying next to the logo ---------------------------------------------------------------

	/** Picks a saying for a new title screen, never the same one twice in a row. */
	public static int pickSaying() {
		RandomSource random = RandomSource.create();
		int pick = random.nextInt(SAYINGS);
		if (pick == lastSaying) {
			pick = (pick + 1 + random.nextInt(SAYINGS - 1)) % SAYINGS;
		}
		lastSaying = pick;
		return pick;
	}

	public static Component saying(int index) {
		return Component.translatable("sabercraft.title.splash." + index).withColor(SAYING_COLOURS[index % SAYING_COLOURS.length]);
	}

	/** Draws the saying where the splash text sits, with its gentle pulse and slant (as the vanilla one). */
	public static void splash(GuiGraphicsExtractor graphics, int screenWidth, Font font, float alpha, int index) {
		Component text = saying(index);
		int textWidth = font.width(text);
		ActiveTextCollector renderer = graphics.textRenderer();
		float phase = 1.8F - Mth.abs(Mth.sin((float) (Util.getMillis() % 1000L) / 1000.0F * (float) (Math.PI * 2)) * 0.1F);
		float textScale = phase * 100.0F / (textWidth + 32);
		Matrix3x2f transform = new Matrix3x2f(renderer.defaultParameters().pose())
				.translate(screenWidth / 2.0F + 123.0F, 69.0F)
				.rotate((float) (-Math.PI / 9))
				.scale(textScale);
		ActiveTextCollector.Parameters parameters = renderer.defaultParameters().withOpacity(alpha).withPose(transform);
		renderer.accept(TextAlignment.LEFT, -textWidth / 2, -8, parameters, text);
	}

	// --- Under the logo and the version line ------------------------------------------------------------

	/** "SABERCRAFT" under the Minecraft logo and its edition line, between a blue and a red blade. */
	public static void subtitle(GuiGraphicsExtractor graphics, Font font, int width, float alpha) {
		if (alpha <= 0.02F) {
			return;
		}
		Component name = Component.translatable("sabercraft.title.subtitle");
		int textWidth = font.width(name);
		int centre = width / 2;
		int y = 84;
		graphics.text(font, name, centre - textWidth / 2, y, ARGB.color(alpha, 0xE6F4FF), true);
		float pulse = 0.85F + 0.15F * (float) Math.sin(Util.getMillis() / 300.0);
		int blade = 46;
		int gap = 6;
		blade(graphics, centre - textWidth / 2 - gap - blade, centre - textWidth / 2 - gap, y + 4, 0x4FA8FF, alpha * pulse);
		blade(graphics, centre + textWidth / 2 + gap, centre + textWidth / 2 + gap + blade, y + 4, 0xFF4A3A, alpha * pulse);
	}

	private static void blade(GuiGraphicsExtractor graphics, int x0, int x1, int y, int colour, float alpha) {
		graphics.fill(x0, y - 2, x1, y + 3, ARGB.color(alpha * 0.22F, colour));
		graphics.fill(x0, y - 1, x1, y + 2, ARGB.color(alpha * 0.55F, colour));
		graphics.fill(x0, y, x1, y + 1, ARGB.color(alpha, 0xFFFFFF));
	}

	/** The mod's version, above Minecraft's, in the blades' blue. */
	public static void version(GuiGraphicsExtractor graphics, Font font, int height, float alpha) {
		if (version == null) {
			version = FabricLoader.getInstance().getModContainer(SaberCraft.MOD_ID)
					.map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("");
		}
		graphics.text(font, Component.translatable("sabercraft.title.version", version), 2, height - 20, ARGB.color(alpha, 0x9AD8FF), true);
	}

	// --- Buttons ------------------------------------------------------------------------------------------

	/**
	 * A title screen button: a dark panel with its upper left and lower right corners cut, a steel
	 * edge, a small lit bar at its left and a few marks at its right; on the mouse, the edge and the
	 * bar glow blue and a soft light runs across. {@code hover} goes from 0 to 1 as the mouse
	 * comes on, so the glow fades in and out.
	 */
	public static void button(GuiGraphicsExtractor graphics, int x, int y, int w, int h, boolean active, float hover, float alpha) {
		if (alpha <= 0.01F) {
			return;
		}
		int cut = Math.min(4, h / 4);
		float lit = active ? hover : 0.0F;
		int panelTop = ARGB.color(alpha * (0.80F + 0.08F * lit), mix(0x121A24, 0x15283A, lit));
		int panelBottom = ARGB.color(alpha * (0.86F + 0.06F * lit), mix(0x070A10, 0x0A1622, lit));
		// The panel, row by row near the cut corners.
		for (int i = 0; i < cut; i++) {
			float t = (float) i / h;
			graphics.fill(x + cut - i, y + i, x + w, y + i + 1, ARGB.srgbLerp(t, panelTop, panelBottom));
			float tb = (float) (h - 1 - i) / h;
			graphics.fill(x, y + h - 1 - i, x + w - cut + i, y + h - i, ARGB.srgbLerp(tb, panelTop, panelBottom));
		}
		graphics.fillGradient(x, y + cut, x + w, y + h - cut, ARGB.srgbLerp((float) cut / h, panelTop, panelBottom),
				ARGB.srgbLerp((float) (h - cut) / h, panelTop, panelBottom));
		// The edge: steel, blue while the mouse is on it; dim when the button is off.
		int edgeColour = active ? mix(0x6F8296, 0x7FD8FF, lit) : 0x3A424C;
		int edge = ARGB.color(alpha * (active ? 0.75F + 0.25F * lit : 0.6F), edgeColour);
		graphics.fill(x + cut, y, x + w, y + 1, edge);
		graphics.fill(x, y + h - 1, x + w - cut, y + h, edge);
		graphics.fill(x, y + cut, x + 1, y + h, edge);
		graphics.fill(x + w - 1, y, x + w, y + h - cut, edge);
		for (int i = 1; i < cut; i++) {
			graphics.fill(x + cut - i, y + i, x + cut - i + 1, y + i + 1, edge);
			graphics.fill(x + w - cut + i - 1, y + h - 1 - i, x + w - cut + i, y + h - i, edge);
		}
		// A faint highlight just inside the upper edge.
		graphics.fill(x + cut + 1, y + 1, x + w - 1, y + 2, ARGB.color(alpha * (0.10F + 0.12F * lit), 0xCFE8FF));
		if (w >= 40) {
			// The bar at the left: grey, lit blue on the mouse (a blade in miniature).
			int barColour = active ? mix(0x56677A, 0x8FE3FF, lit) : 0x2E353E;
			graphics.fill(x + 3, y + cut + 1, x + 5, y + h - cut - 1, ARGB.color(alpha * (active ? 0.8F + 0.2F * lit : 0.6F), barColour));
			if (lit > 0.02F) {
				graphics.fill(x + 2, y + cut, x + 6, y + h - cut, ARGB.color(alpha * 0.25F * lit, 0x7FD8FF));
			}
			// Three small marks at the right, and a thin line along the lower edge.
			int marks = ARGB.color(alpha * (active ? 0.45F + 0.4F * lit : 0.25F), active ? mix(0x8796A8, 0xBFEFFF, lit) : 0x4A525C);
			for (int i = 0; i < 3; i++) {
				graphics.fill(x + w - 9 + i * 2, y + 3, x + w - 8 + i * 2, y + 5, marks);
			}
			int lineEnd = x + w - cut - 2;
			int lineStart = lineEnd - Math.round((w - 16) * (0.25F + 0.35F * lit));
			graphics.fill(lineStart, y + h - 3, lineEnd, y + h - 2, ARGB.color(alpha * (0.25F + 0.45F * lit), active ? 0x7FD8FF : 0x3A424C));
		}
		if (lit > 0.02F && w >= 24) {
			// A soft light running across while the mouse stays on it.
			float sweep = (float) ((Util.getMillis() % 1800L) / 1800.0);
			int centre = x + Math.round((w + 40) * sweep) - 20;
			for (int i = -10; i <= 10; i += 2) {
				int sx = centre + i;
				if (sx < x + 2 || sx + 2 > x + w - 2) {
					continue;
				}
				float fall = 1.0F - Math.abs(i) / 11.0F;
				graphics.fill(sx, y + 2, sx + 2, y + h - 2, ARGB.color(alpha * lit * 0.10F * fall, 0xBFEFFF));
			}
		}
	}

	private static int mix(int from, int to, float t) {
		return ARGB.srgbLerp(Mth.clamp(t, 0.0F, 1.0F), from | 0xFF000000, to | 0xFF000000) & 0xFFFFFF;
	}

	/** For the tests: the saying picked last (-1 before the first title screen). */
	public static int lastSayingForTest() {
		return lastSaying;
	}

	/** For the tests: the twinkling stars in use. */
	public static int twinkleCountForTest() {
		return TWINKLE.length;
	}
}
