package com.phiro.sabercraft.client.story;

import java.util.List;

import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.phys.Vec3;

import com.phiro.sabercraft.client.ImmersionConfig;
import com.phiro.sabercraft.network.StoryFiguresPayload;
import com.phiro.sabercraft.network.StoryStatePayload;

/**
 * Phase 19: what the story shows over the game, kept out of the way: the objectives at the top
 * right (the optional ones grey), a small marker towards the current objective, a thin arc round
 * the crosshair towards everyone who is noticing the player (it fills as they grow suspicious,
 * yellow, orange, red) with the stage below it, the alarm (a red, pulsing edge), the disguise,
 * what the crosshair is on ("Use: locker"), comlink lines at the top left with the speaker's face,
 * hints over the hotbar, a download's progress, the mission's title, a droid's scan (people seen
 * through walls), a fade to black (a capture), and the chapter's end.
 */
final class StoryHud implements HudElement {
	static final int PANEL_WIDTH = 168;
	static final int TEXT = 0xFFE8EEF6;
	static final int DIM = 0xFF9AA6B4;
	static final int GOLD = 0xFFE8C46A;
	static final int[] STAGE_COLOURS = {0xFFFFFFFF, 0xFFF2D848, 0xFFF59A32, 0xFFF2382C};

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft client = Minecraft.getInstance();

		if (client.player == null || client.level == null) {
			return;
		}

		float partial = delta.getGameTimeDeltaPartialTick(false);
		int width = graphics.guiWidth();
		int height = graphics.guiHeight();
		StoryStatePayload state = StoryClient.state();
		boolean hidden = client.gui.hud.isHidden();

		if (state != null && state.active() && !hidden) {
			alarm(graphics, state, width, height);
			scan(graphics, client, width, height);

			if (ImmersionConfig.get().storyMarker) {
				marker(graphics, client, state, width, height);
				suspicion(graphics, client, partial, width, height);
			}

			objectives(graphics, client.font, state, width);
			prompt(graphics, client, partial, width, height);
			progress(graphics, client.font, width, height);
			hint(graphics, client.font, width, height);
			radio(graphics, client.font);
		}

		title(graphics, client.font, partial, width, height);
		fade(graphics, client.font, partial, width, height);
		end(graphics, client.font, width, height);
	}

	// --- The objectives ----------------------------------------------------------------------------

	private static void objectives(GuiGraphicsExtractor graphics, Font font, StoryStatePayload state, int width) {
		if (state.objective().isEmpty() || Minecraft.getInstance().gui.screen() != null) {
			return;
		}

		int x = width - PANEL_WIDTH - 6;
		int y = 6;
		List<FormattedCharSequence> main = font.split(Component.translatable(state.objective()), PANEL_WIDTH - 12);
		int lines = 1 + main.size();

		for (StoryStatePayload.Objective opt : state.optional()) {
			lines += font.split(Component.translatable(opt.key()), PANEL_WIDTH - 22).size();
		}

		int h = 8 + lines * 10 + (state.optional().isEmpty() ? 0 : 3) + (state.disguise() > 0 ? 11 : 0);
		graphics.fill(x - 4, y - 4, x + PANEL_WIDTH, y + h - 2, 0x78000000);
		graphics.fill(x - 4, y - 4, x - 2, y + h - 2, 0xC0E8C46A);
		graphics.text(font, Component.translatable("sabercraft.story.hud.mission", state.mission() + 1, Component.translatable(state.title())), x, y, GOLD);
		y += 11;

		for (FormattedCharSequence line : main) {
			graphics.text(font, line, x, y, TEXT);
			y += 10;
		}

		if (!state.optional().isEmpty()) {
			y += 3;
		}

		for (StoryStatePayload.Objective opt : state.optional()) {
			graphics.text(font, opt.done() ? "✓" : "○", x + 1, y, opt.done() ? 0xFF70D890 : DIM);

			for (FormattedCharSequence line : font.split(Component.translatable(opt.key()), PANEL_WIDTH - 22)) {
				graphics.text(font, line, x + 11, y, opt.done() ? 0xFF7A8A7E : DIM);
				y += 10;
			}
		}

		if (state.disguise() > 0) {
			boolean burned = state.disguise() == 2;
			graphics.text(font, Component.translatable(burned ? "sabercraft.story.hud.disguise_burned" : "sabercraft.story.hud.disguise"), x, y + 1,
					burned ? 0xFFE87060 : 0xFFB8D8F0);
		}
	}

	// --- The marker towards the objective ------------------------------------------------------------

	private static void marker(GuiGraphicsExtractor graphics, Minecraft client, StoryStatePayload state, int width, int height) {
		if (!state.target() || client.gui.screen() != null) {
			return;
		}

		Vec3 at = state.marker();
		float[] p = project(client, at.add(0.0, 0.6, 0.0), width, height);
		double distance = client.player.getEyePosition().distanceTo(at);

		if (distance < 2.5) {
			return;
		}

		int colour = 0xD0E8C46A;
		boolean inside = p != null && p[0] > 12 && p[0] < width - 12 && p[1] > 12 && p[1] < height - 12;

		if (inside) {
			int x = Math.round(p[0]);
			int y = Math.round(p[1]);
			diamond(graphics, x, y, 4, colour);
			String metres = Math.round(distance) + " m";
			graphics.text(client.font, metres, x - client.font.width(metres) / 2, y + 7, 0xC0E8E0C8);
			return;
		}

		// Off screen: a small arrow at the edge, towards where it is.
		Vec3 eye = client.player.getEyePosition();
		Vec3 to = at.subtract(eye);
		float yaw = (float) Math.toDegrees(Math.atan2(-to.x, to.z));
		float rel = Mth.wrapDegrees(yaw - client.gameRenderer.mainCamera().yRot()) * Mth.DEG_TO_RAD;
		int cx = width / 2;
		int cy = height / 2;
		int x = Math.round(cx + Mth.sin(rel) * (height / 2.0F - 26));
		int y = Math.round(cy - Mth.cos(rel) * (height / 2.0F - 26));
		diamond(graphics, x, y, 3, 0xA0E8C46A);
	}

	// --- Who is noticing ----------------------------------------------------------------------------

	private static void suspicion(GuiGraphicsExtractor graphics, Minecraft client, float partial, int width, int height) {
		var origin = StoryClient.figuresOrigin();

		if (origin == null || client.gui.screen() != null) {
			return;
		}

		int cx = width / 2;
		int cy = height / 2;
		int worst = 0;
		float camera = client.gameRenderer.mainCamera().yRot();
		Vec3 me = client.player.position();

		for (StoryClient.Pose pose : StoryClient.figures(origin, partial)) {
			StoryFiguresPayload.Fig fig = pose.fig();

			if ((fig.flags() & StoryFiguresPayload.OBSERVER) == 0 || (fig.flags() & StoryFiguresPayload.HOSTILE) != 0 || fig.suspicion() <= 0
					|| "down".equals(fig.pose())) {
				continue;
			}

			worst = Math.max(worst, Math.max(fig.stage(), 1));
			double dx = pose.x() + origin.getX() - me.x;
			double dz = pose.z() + origin.getZ() - me.z;
			float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
			float rel = Mth.wrapDegrees(yaw - camera);
			arc(graphics, cx, cy, 19, rel, fig.suspicion() / 255.0F, STAGE_COLOURS[Mth.clamp(Math.max(fig.stage(), 1), 1, 3)]);
		}

		if (worst > 0) {
			Component stage = Component.translatable("sabercraft.story.hud.stage." + worst);
			int colour = STAGE_COLOURS[worst];
			int w = client.font.width(stage);
			// The eye: a small almond with a pupil, then the stage.
			int ex = cx - (w + 12) / 2;
			int ey = cy + 26;
			graphics.fill(ex, ey + 3, ex + 9, ey + 5, colour);
			graphics.fill(ex + 1, ey + 2, ex + 8, ey + 6, colour & 0x80FFFFFF);
			graphics.fill(ex + 3, ey + 2, ex + 6, ey + 6, 0xFF101010);
			graphics.text(client.font, stage, ex + 12, ey, colour);
		}
	}

	/** An arc round the crosshair, centred on {@code angle} (0 = ahead, 90 = to the right), filled from its middle. */
	private static void arc(GuiGraphicsExtractor graphics, int cx, int cy, int radius, float angle, float fill, int colour) {
		int steps = 16;
		float span = 34.0F;

		for (int i = 0; i <= steps; i++) {
			float t = i / (float) steps;
			float a = (angle - span / 2.0F + span * t) * Mth.DEG_TO_RAD;
			int x = Math.round(cx + Mth.sin(a) * radius);
			int y = Math.round(cy - Mth.cos(a) * radius);
			boolean lit = Math.abs(t - 0.5F) * 2.0F <= fill;
			graphics.fill(x - 1, y - 1, x + 1, y + 1, lit ? colour : 0x50FFFFFF);
		}
	}

	// --- The alarm ------------------------------------------------------------------------------------

	private static void alarm(GuiGraphicsExtractor graphics, StoryStatePayload state, int width, int height) {
		if (state.alarm() <= 0) {
			return;
		}

		boolean full = state.alarm() >= 2;
		float pulse = 0.5F + 0.5F * Mth.sin(Util.getMillis() / (full ? 160.0F : 500.0F));
		int alpha = Math.round((full ? 70 : 30) * (0.5F + 0.5F * pulse));
		int edge = alpha << 24 | (full ? 0xD01010 : 0xD06010);
		int depth = height / 6;
		graphics.fillGradient(0, 0, width, depth, edge, 0x00000000);
		graphics.fillGradient(0, height - depth, width, height, 0x00000000, edge);
		Component label = Component.translatable(full ? "sabercraft.story.hud.alarm" : "sabercraft.story.hud.search");
		Font font = Minecraft.getInstance().font;
		int w = font.width(label);
		graphics.fill(width / 2 - w / 2 - 6, 4, width / 2 + w / 2 + 6, 17, 0x90000000);
		graphics.text(font, label, width / 2 - w / 2, 7, full ? 0xFFFF5040 : 0xFFF0A040);
	}

	// --- A droid's scan ----------------------------------------------------------------------------

	private static void scan(GuiGraphicsExtractor graphics, Minecraft client, int width, int height) {
		float left = StoryClient.scanLeft();

		if (left <= 0.0F) {
			return;
		}

		int alpha = Math.round(Math.min(1.0F, left * 3.0F) * 220);

		for (Vec3 at : StoryClient.scan()) {
			float[] p = project(client, at.add(0.0, 1.0, 0.0), width, height);

			if (p != null) {
				int x = Math.round(p[0]);
				int y = Math.round(p[1]);
				int size = 3 + Math.round(2 * (0.5F + 0.5F * Mth.sin(Util.getMillis() / 120.0F)));
				diamond(graphics, x, y, size, alpha << 24 | 0x60E0FF);
				graphics.fill(x, y - 12, x + 1, y - size, alpha / 2 << 24 | 0x60E0FF);
			}
		}
	}

	// --- What the crosshair is on ------------------------------------------------------------------

	private static void prompt(GuiGraphicsExtractor graphics, Minecraft client, float partial, int width, int height) {
		if (client.gui.screen() != null) {
			return;
		}

		StoryInput.Target target = StoryInput.target(client, partial);

		if (target == null) {
			return;
		}

		Component key = client.options.keyUse.getTranslatedKeyMessage();
		Component text = target.figure() > 0 ? Component.translatable("sabercraft.story.hud.talk", key, StoryPortraits.name(target.speaker()))
				: Component.translatable(target.hack() ? "sabercraft.story.hud.use_hack" : "sabercraft.story.hud.use", key,
						Component.translatable(target.label()));
		int w = client.font.width(text);
		int x = width / 2 - w / 2;
		int y = height / 2 + 14;
		graphics.fill(x - 4, y - 2, x + w + 4, y + 10, 0x80000000);
		graphics.text(client.font, text, x, y, target.hack() ? 0xFFB0C8E8 : TEXT);
	}

	// --- Lines and hints -----------------------------------------------------------------------------

	private static void radio(GuiGraphicsExtractor graphics, Font font) {
		StoryClient.Line line = StoryClient.radio();

		if (line == null) {
			return;
		}

		int age = StoryClient.radioAge();
		float fade = Math.min(1.0F, Math.min(age / 6.0F, (line.ticks() - age) / 10.0F));
		int alpha = Math.round(Mth.clamp(fade, 0.0F, 1.0F) * 255);

		if (alpha < 8) {
			return;
		}

		int x = 8;
		int y = 8;
		int size = 32;
		int textWidth = 220;
		List<FormattedCharSequence> lines = font.split(Component.translatable(line.key()), textWidth);
		int h = Math.max(size + 4, 14 + lines.size() * 10);
		graphics.fill(x - 4, y - 4, x + size + 10 + textWidth, y + h, (alpha * 3 / 5) << 24);
		StoryPortraits.draw(graphics, line.who(), x, y, size, true, alpha);
		int tx = x + size + 8;
		graphics.text(font, StoryPortraits.name(line.who()), tx, y, alpha << 24 | 0x8CC0FF);
		int ty = y + 11;

		for (FormattedCharSequence text : lines) {
			graphics.text(font, text, tx, ty, alpha << 24 | 0xE8EEF6);
			ty += 10;
		}
	}

	private static void hint(GuiGraphicsExtractor graphics, Font font, int width, int height) {
		String key = StoryClient.hint();

		if (key == null) {
			return;
		}

		int age = StoryClient.hintAge();
		float fade = Math.min(1.0F, Math.min(age / 4.0F, (StoryClient.HINT_TICKS - age) / 12.0F));
		int alpha = Math.round(Mth.clamp(fade, 0.0F, 1.0F) * 255);

		if (alpha < 8) {
			return;
		}

		List<FormattedCharSequence> lines = font.split(Component.translatable(key), 280);
		int y = height - 72 - lines.size() * 10;

		for (FormattedCharSequence line : lines) {
			int w = font.width(line);
			graphics.fill(width / 2 - w / 2 - 4, y - 2, width / 2 + w / 2 + 4, y + 9, (alpha / 2) << 24);
			graphics.text(font, line, width / 2 - w / 2, y, alpha << 24 | 0xF0E6C8);
			y += 11;
		}
	}

	private static void progress(GuiGraphicsExtractor graphics, Font font, int width, int height) {
		String key = StoryClient.progressKey();

		if (key == null) {
			return;
		}

		int value = Mth.clamp(StoryClient.progressValue(), 0, 100);
		Component text = Component.translatable(key, value);
		int w = 140;
		int x = width / 2 - w / 2;
		int y = height / 2 + 30;
		graphics.fill(x - 4, y - 3, x + w + 4, y + 18, 0x90000000);
		graphics.text(font, text, width / 2 - font.width(text) / 2, y, 0xFFB8E0F0);
		graphics.fill(x, y + 11, x + w, y + 14, 0xFF20303A);
		graphics.fill(x, y + 11, x + w * value / 100, y + 14, 0xFF50C8F0);
	}

	// --- Title, fade, end ----------------------------------------------------------------------------

	private static void title(GuiGraphicsExtractor graphics, Font font, float partial, int width, int height) {
		String key = StoryClient.title();

		if (key == null) {
			return;
		}

		float age = StoryClient.titleAge() + partial;
		float fade = Mth.clamp(Math.min(age / 12.0F, (StoryClient.TITLE_TICKS - age) / 20.0F), 0.0F, 1.0F);
		int alpha = Math.round(fade * 255);

		if (alpha < 8) {
			return;
		}

		StoryStatePayload state = StoryClient.state();
		Component small = Component.translatable("sabercraft.story.hud.chapter_mission", state != null ? state.mission() + 1 : 1);
		Component big = Component.translatable(key);
		int y = height / 4;
		graphics.text(font, small, width / 2 - font.width(small) / 2, y, alpha << 24 | 0xC8B070);
		graphics.pose().pushMatrix();
		graphics.pose().translate(width / 2.0F, y + 14);
		graphics.pose().scale(2.0F, 2.0F);
		graphics.text(font, big, -font.width(big) / 2, 0, alpha << 24 | 0xF2ECDC);
		graphics.pose().popMatrix();
		int lw = Math.round(font.width(big) * fade);
		graphics.fill(width / 2 - lw, y + 36, width / 2 + lw, y + 37, alpha / 2 << 24 | 0xE8C46A);
	}

	private static void fade(GuiGraphicsExtractor graphics, Font font, float partial, int width, int height) {
		float dark = StoryClient.fade(partial);

		if (dark <= 0.0F) {
			return;
		}

		graphics.fill(0, 0, width, height, Math.round(dark * 255) << 24);
		String key = StoryClient.fadeText();

		if (!key.isEmpty() && dark > 0.6F) {
			int alpha = Math.round((dark - 0.6F) / 0.4F * 255);
			List<FormattedCharSequence> lines = font.split(Component.translatable(key), 300);
			int y = height / 2 - lines.size() * 5;

			for (FormattedCharSequence line : lines) {
				graphics.text(font, line, width / 2 - font.width(line) / 2, y, alpha << 24 | 0xE8E0D0);
				y += 11;
			}
		}
	}

	private static void end(GuiGraphicsExtractor graphics, Font font, int width, int height) {
		if (!StoryClient.ended()) {
			return;
		}

		int age = StoryClient.endAge();

		if (age > 360) {
			StoryClient.dismissEnd();
			return;
		}

		float fade = Mth.clamp(Math.min(age / 30.0F, (360 - age) / 40.0F), 0.0F, 1.0F);
		int alpha = Math.round(fade * 255);
		graphics.fill(0, 0, width, height, Math.round(fade * 200) << 24);
		Component big = Component.translatable("sabercraft.story.hud.end_title");
		graphics.pose().pushMatrix();
		graphics.pose().translate(width / 2.0F, height / 3.0F);
		graphics.pose().scale(2.0F, 2.0F);
		graphics.text(font, big, -font.width(big) / 2, 0, alpha << 24 | 0xF2ECDC);
		graphics.pose().popMatrix();
		int y = height / 3 + 30;

		for (FormattedCharSequence line : font.split(Component.translatable("sabercraft.story.end"), 300)) {
			graphics.text(font, line, width / 2 - font.width(line) / 2, y, alpha << 24 | 0xC8C0B0);
			y += 11;
		}
	}

	// --- Helpers -------------------------------------------------------------------------------------

	static void diamond(GuiGraphicsExtractor graphics, int x, int y, int r, int colour) {
		for (int i = -r; i <= r; i++) {
			int w = r - Math.abs(i);
			graphics.fill(x - w, y + i, x + w + 1, y + i + 1, colour);
		}
	}

	/** Where a point in the world is on the screen (GUI pixels), or {@code null} behind the camera. */
	static float @Nullable [] project(Minecraft client, Vec3 world, int width, int height) {
		var camera = client.gameRenderer.mainCamera();
		Matrix4f matrix = camera.getViewRotationProjectionMatrix(new Matrix4f());
		Vec3 rel = world.subtract(camera.position());
		Vector4f clip = matrix.transform(new Vector4f((float) rel.x, (float) rel.y, (float) rel.z, 1.0F));

		if (clip.w <= 0.05F) {
			return null;
		}

		return new float[] {(clip.x / clip.w + 1.0F) / 2.0F * width, (1.0F - clip.y / clip.w) / 2.0F * height};
	}
}
