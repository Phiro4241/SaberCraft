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
import com.phiro.sabercraft.mixin.client.HudAccessor;
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
public final class StoryHud implements HudElement {
	static final int PANEL_WIDTH = 168;
	static final int TEXT = 0xFFE8EEF6;
	static final int DIM = 0xFF9AA6B4;
	static final int GOLD = 0xFFE8C46A;
	static final int[] STAGE_COLOURS = {0xFFFFFFFF, 0xFFF2D848, 0xFFF59A32, 0xFFF2382C};

	/** Phase 24: the lights failing for a moment (dark dips), the reactor's red at the screen's edges. */
	private static void reactor(GuiGraphicsExtractor graphics, float partial, int width, int height) {
		float dark = StoryClient.flicker(partial);

		if (dark > 0.01F) {
			graphics.fill(0, 0, width, height, Math.round(dark * 255.0F) << 24 | 0x05070A);
		}

		float red = StoryClient.reactorPulse(partial);

		if (red > 0.005F) {
			int edge = Math.max(8, Math.min(width, height) / 7);
			int steps = 6;

			for (int i = 0; i < steps; i++) {
				float k = 1.0F - i / (float) steps;
				int alpha = Math.round(red * k * k * 255.0F);
				int c = alpha << 24 | 0xC81810;
				int inset = i * edge / steps;
				int band = Math.max(1, edge / steps);
				graphics.fill(inset, inset, width - inset, inset + band, c);
				graphics.fill(inset, height - inset - band, width - inset, height - inset, c);
				graphics.fill(inset, inset + band, inset + band, height - inset - band, c);
				graphics.fill(width - inset - band, inset + band, width - inset, height - inset - band, c);
			}
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft client = Minecraft.getInstance();

		// Phase 20: a film shows only itself.
		if (client.player == null || client.level == null || StoryCinema.active()) {
			return;
		}

		float partial = delta.getGameTimeDeltaPartialTick(false);
		int width = graphics.guiWidth();
		int height = graphics.guiHeight();
		StoryStatePayload state = StoryClient.state();
		boolean hidden = client.gui.hud.isHidden();

		if (state != null && state.active()) {
			// Phase 24: the failing reactor: the lights flicker, the alarm's red pulses at the edges.
			reactor(graphics, partial, width, height);
		}

		if (state != null && state.active() && !hidden) {
			alarm(graphics, state, width, height);
			duel(graphics, client, width, height);
			scan(graphics, client, width, height);
			noise(graphics, client, width, height);
			checkpoint(graphics, client.font, width);

			if (ImmersionConfig.get().storyMarker) {
				marker(graphics, client, state, width, height);
				suspicion(graphics, client, partial, width, height);
			}

			objectives(graphics, client.font, state, width);
			droidTip(graphics, client, state, partial, width, height);
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

		// Phase 22: after the chapter no mission is shown any more (the last one's goal stood there),
		// only that chapter 1 is complete, quietly, and how to play it again.
		if (StoryClient.has("chapter.done")) {
			List<FormattedCharSequence> done = font.split(Component.translatable("sabercraft.story.hud.chapter_done_hint", com.phiro.sabercraft.client.SaberCraftClient.CUSTOMIZE_KEY.getTranslatedKeyMessage()), PANEL_WIDTH - 12);
			int h = 8 + 20 + done.size() * 10;
			graphics.fill(x - 4, y - 4, x + PANEL_WIDTH, y + h - 2, 0x58000000);
			graphics.fill(x - 4, y - 4, x - 2, y + h - 2, 0xC070D890);
			graphics.text(font, Component.translatable("sabercraft.story.hud.chapter_done"), x, y, GOLD);
			graphics.text(font, Component.translatable("sabercraft.story.hud.chapter_done_state"), x, y + 11, 0xFF90E0A8);
			y += 22;

			for (FormattedCharSequence line : done) {
				graphics.text(font, line, x, y, DIM);
				y += 10;
			}

			return;
		}

		List<FormattedCharSequence> main = font.split(Component.translatable(state.objective()), PANEL_WIDTH - 12);
		int lines = 1 + main.size();

		for (StoryStatePayload.Objective opt : state.optional()) {
			lines += font.split(Component.translatable(opt.key()), PANEL_WIDTH - 22).size();
		}

		int h = 8 + lines * 10 + (state.optional().isEmpty() ? 0 : 3) + (state.disguise() > 0 ? 11 : 0) + (state.alarm() > 0 || StoryClient.wary() ? 11 : 0);
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
			// A small box, filled when done (drawn, not a glyph: every font has it then).
			int bx = x + 1;
			int by = y + 1;
			int box = opt.done() ? 0xFF70D890 : DIM;
			graphics.fill(bx, by, bx + 6, by + 1, box);
			graphics.fill(bx, by + 5, bx + 6, by + 6, box);
			graphics.fill(bx, by, bx + 1, by + 6, box);
			graphics.fill(bx + 5, by, bx + 6, by + 6, box);

			if (opt.done()) {
				graphics.fill(bx + 2, by + 2, bx + 4, by + 4, box);
			}

			for (FormattedCharSequence line : font.split(Component.translatable(opt.key()), PANEL_WIDTH - 22)) {
				graphics.text(font, line, x + 11, y, opt.done() ? 0xFF7A8A7E : DIM);
				y += 10;
			}
		}

		if (state.disguise() > 0) {
			boolean burned = state.disguise() == 2;
			graphics.text(font, Component.translatable(burned ? "sabercraft.story.hud.disguise_burned" : "sabercraft.story.hud.disguise"), x, y + 1,
					burned ? 0xFFE87060 : 0xFFB8D8F0);
			y += 11;
		}

		// Phase 20: after one (ALARM), searching, the ship on alert; calm again, the crew wary.
		if (state.alarm() > 0) {
			String key = state.alarm() >= 3 ? "sabercraft.story.hud.alarm" : state.alarm() == 2 ? "sabercraft.story.hud.search" : "sabercraft.story.hud.alert";
			int colour = state.alarm() >= 3 ? 0xFFFF5040 : state.alarm() == 2 ? 0xFFF0A040 : 0xFFC8A070;
			graphics.text(font, Component.translatable(key), x, y + 1, colour);
		} else if (StoryClient.wary()) {
			graphics.text(font, Component.translatable("sabercraft.story.hud.wary"), x, y + 1, 0xFFC8B890);
		}
	}

	/** How wide the objectives' panel is now (0 when none is shown), so nothing is drawn under it. */
	static int objectivesWidth(StoryStatePayload state) {
		return state == null || !state.active() || state.objective().isEmpty() ? 0 : PANEL_WIDTH + 10;
	}

	/**
	 * Phase 28: where the objectives' panel ends at the bottom (GUI y), 0 when none is shown: the
	 * same height as {@link #objectives} draws it.
	 */
	static int objectivesBottom(Font font, StoryStatePayload state) {
		if (objectivesWidth(state) == 0) {
			return 0;
		}

		if (StoryClient.has("chapter.done")) {
			int done = font.split(Component.translatable("sabercraft.story.hud.chapter_done_hint", com.phiro.sabercraft.client.SaberCraftClient.CUSTOMIZE_KEY.getTranslatedKeyMessage()), PANEL_WIDTH - 12).size();
			return 6 + 8 + 20 + done * 10 - 2;
		}

		int lines = 1 + font.split(Component.translatable(state.objective()), PANEL_WIDTH - 12).size();

		for (StoryStatePayload.Objective opt : state.optional()) {
			lines += font.split(Component.translatable(opt.key()), PANEL_WIDTH - 22).size();
		}

		int h = 8 + lines * 10 + (state.optional().isEmpty() ? 0 : 3) + (state.disguise() > 0 ? 11 : 0) + (state.alarm() > 0 || StoryClient.wary() ? 11 : 0);
		return 6 + h - 2;
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

	// --- Phase 20: what M-31 can do, with a guard near ----------------------------------------------

	/**
	 * While M-31 is along and someone who watches is near (and nobody is after the player), a small
	 * line above the hotbar says what the droid key does; it is no longer shown once the noise was
	 * made a few times.
	 */
	private static void droidTip(GuiGraphicsExtractor graphics, Minecraft client, StoryStatePayload state, float partial, int width, int height) {
		var origin = StoryClient.figuresOrigin();

		if (origin == null || client.gui.screen() != null || !state.droids().contains("m31") || state.alarm() >= 3 || StoryClient.noisesMade() >= 3) {
			return;
		}

		Vec3 me = client.player.position();
		boolean near = false;

		for (StoryClient.Pose pose : StoryClient.figures(origin, partial)) {
			StoryFiguresPayload.Fig fig = pose.fig();

			if ((fig.flags() & StoryFiguresPayload.OBSERVER) != 0 && (fig.flags() & StoryFiguresPayload.HOSTILE) == 0 && !"down".equals(fig.pose())
					&& Math.hypot(pose.x() + origin.getX() - me.x, pose.z() + origin.getZ() - me.z) < 12.0 && Math.abs(pose.y() + origin.getY() - me.y) < 3.0) {
				near = true;
				break;
			}
		}

		if (!near) {
			return;
		}

		Component text = Component.translatable("sabercraft.story.hud.droid_tip", com.phiro.sabercraft.client.droid.DroidControl.ABILITY_KEY.getTranslatedKeyMessage());
		int w = client.font.width(text);
		int x = width - w - 8;
		int y = height - 34;
		graphics.fill(x - 4, y - 2, x + w + 4, y + 10, 0x70000000);
		graphics.text(client.font, text, x, y, 0xFFE8D8A8);
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
			// The eye: a small almond with a pupil, then the stage. Phase 20: above the crosshair
			// when a hint of several lines reaches up to it (with a large interface they overlapped).
			int ex = cx - (w + 12) / 2;
			int ey = cy + 26;

			if (ey + 11 > hintTop(client.font, height)) {
				ey = cy - 34;

				// Phase 21: and beside the crosshair when a long comlink line reaches down to that.
				if (ey < radioBottom(client.font, width) + 2) {
					ex = cx + 26;
					ey = cy - 4;
				}
			}
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

	/**
	 * Phase 23: where a hurt came from: a red arc round the crosshair on that side (ahead, to a
	 * side, behind), fading in a second.
	 */
	private static void hurtFrom(GuiGraphicsExtractor graphics, Minecraft client, float partial, int width, int height) {
		double[] from = StoryClient.hurtFrom();

		if (from == null || client.player == null || from[0] * from[0] + from[1] * from[1] < 1.0E-4) {
			return;
		}

		float yaw = client.player.getViewYRot(partial) * Mth.DEG_TO_RAD;
		double forward = -Mth.sin(yaw) * from[0] + Mth.cos(yaw) * from[1];
		double right = -Mth.cos(yaw) * from[0] - Mth.sin(yaw) * from[1];
		float angle = (float) Math.toDegrees(Math.atan2(right, forward));
		float fade = 1.0F - (float) (from[2] + partial) / 24.0F;
		int alpha = Math.round(Mth.clamp(fade, 0.0F, 1.0F) * 220.0F);

		if (alpha <= 8) {
			return;
		}

		int colour = alpha << 24 | 0xE0281C;

		for (int r = 0; r < 3; r++) {
			arc(graphics, width / 2, height / 2, 34 + r * 2, angle, 1.0F, colour);
		}
	}

	// --- The alarm ------------------------------------------------------------------------------------

	private static void alarm(GuiGraphicsExtractor graphics, StoryStatePayload state, int width, int height) {
		// Phase 20: the edges only while they search or chase (not for the ship on alert).
		if (state.alarm() <= 1) {
			return;
		}

		boolean full = state.alarm() >= 3;
		float pulse = 0.5F + 0.5F * Mth.sin(Util.getMillis() / (full ? 160.0F : 500.0F));
		int alpha = Math.round((full ? 70 : 30) * (0.5F + 0.5F * pulse));
		int edge = alpha << 24 | (full ? 0xD01010 : 0xD06010);
		int depth = height / 6;
		graphics.fillGradient(0, 0, width, depth, edge, 0x00000000);
		graphics.fillGradient(0, height - depth, width, height, 0x00000000, edge);
		// The words (ALARM, searching) are in the objectives' panel.
	}

	// --- Phase 21: the fight ----------------------------------------------------------------------

	/** Phase 23: the one mark on her bar: where the second phase begins (after the blade lock). */
	static final float[] PHASE_MARKS = {0.5F};

	/**
	 * The fight: his strength at the top (with the marks where he changes and where he breaks off),
	 * the Focus over the hotbar, a cold tint while it runs, red edges when hurt, the stun hits.
	 */
	private static void duel(GuiGraphicsExtractor graphics, Minecraft client, int width, int height) {
		float partial = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		// Phase 23: a clash flashes over the view for a moment.
		int flash = StoryClient.flash(partial);

		if (flash != 0) {
			graphics.fill(0, 0, width, height, flash);
		}

		hurtFrom(graphics, client, partial, width, height);
		int hurt = StoryClient.hurtAge();

		if (hurt < 12) {
			int alpha = Math.round((1.0F - hurt / 12.0F) * 120.0F);
			int edge = alpha << 24 | 0xC01008;
			int depth = height / 5;
			graphics.fillGradient(0, 0, width, depth, edge, 0x00000000);
			graphics.fillGradient(0, height - depth, width, height, 0x00000000, edge);
		}

		if (StoryClient.focusOn()) {
			// The Focus: the world goes still and cold at the edges.
			int age = StoryClient.focusAge();
			int alpha = Math.round(Math.min(1.0F, age / 6.0F) * 90.0F);
			int edge = alpha << 24 | 0x3A6CC8;
			int depth = height / 4;
			graphics.fillGradient(0, 0, width, depth, edge, 0x00000000);
			graphics.fillGradient(0, height - depth, width, height, 0x00000000, edge);
			graphics.fill(0, 0, width, height, Math.round(Math.min(1.0F, age / 6.0F) * 28.0F) << 24 | 0x203A70);
		}

		StoryFiguresPayload.Fig shadow = StoryClient.duelist();

		if (shadow != null) {
			// Left of the objectives' panel (top right), so the two do not overlap.
			int room = width - PANEL_WIDTH - 24;
			int barWidth = Math.min(182, room - 24);
			int centre = room / 2 + 6;
			int x = centre - barWidth / 2;
			// Under a comlink line or a shout at the top left (it covered his name).
			int y = Math.max(22, radioBottom(client.font, width) + 15);
			float health = shadow.health() / 255.0F;
			Component name = Component.translatable("sabercraft.story.name." + shadow.speaker());
			graphics.centeredText(client.font, name, centre, y - 11, 0xFFFF7A6A);
			graphics.fill(x - 1, y - 1, x + barWidth + 1, y + 6, 0xC0000000);
			graphics.fill(x, y, x + Math.round(barWidth * health), y + 5, 0xFFD8302A);
			graphics.fill(x, y, x + Math.round(barWidth * health), y + 1, 0xFFFF8A80);

			for (float mark : PHASE_MARKS) {
				int mx = x + Math.round(barWidth * mark);
				graphics.fill(mx, y - 2, mx + 1, y + 7, 0xFFE8EEF6);
			}

			// Phase 26: no words under her bar any more (her moves were named there): what she is
			// about to do is seen and heard (the wind-ups, the ring spinning up, its whirr).
		}

		if (StoryClient.meterShown()) {
			int meter = StoryClient.focusMeter();
			int barWidth = 60;
			int x = width / 2 - 91 - barWidth - 8;
			int y = height - 14;
			boolean full = meter >= 100;
			float pulse = full ? 0.5F + 0.5F * Mth.sin(Util.getMillis() / 180.0F) : 0.0F;
			graphics.fill(x - 1, y - 1, x + barWidth + 1, y + 5, 0xB0000000);
			graphics.fill(x, y, x + barWidth * Math.min(100, meter) / 100, y + 4, StoryClient.focusOn() ? 0xFFB8D8FF : full ? 0xFF4C8CFF : 0xFF2C5AA8);

			Component label = Component.translatable(full ? "sabercraft.story.duel.focus_ready" : "sabercraft.story.duel.focus",
					StoryClient.FOCUS_KEY.getTranslatedKeyMessage());
			graphics.text(client.font, label, x, y - 10, (full ? Math.round(160 + 95 * pulse) : 150) << 24 | 0xBCD4FF);
		}

		if (StoryClient.stuns() > 0 && StoryClient.stunAge() < 200) {
			// The stun hits: three marks under the crosshair (the third means caught). Phase 27:
			// as many as the difficulty allows (four on Easy, two on Hard).
			int cx = width / 2;
			int cy = height / 2 + 14;
			int marks = StoryClient.stunMax();

			for (int i = 0; i < marks; i++) {
				int sx = cx - (marks * 10 - 3) / 2 + i * 10;
				graphics.fill(sx, cy, sx + 7, cy + 3, i < StoryClient.stuns() ? 0xE05A9CFF : 0x60FFFFFF);
			}
		}
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

	// --- Phase 20: where M-31's noise went, a checkpoint saved ----------------------------------

	/** Rings spreading from where the droid made its noise (amber, like a sound on a scope). */
	private static void noise(GuiGraphicsExtractor graphics, Minecraft client, int width, int height) {
		float age = StoryClient.noiseAge();

		if (StoryClient.noise().isEmpty()) {
			return;
		}

		int alpha = Math.round((1.0F - age) * 230);

		for (Vec3 at : StoryClient.noise()) {
			float[] p = project(client, at.add(0.0, 0.3, 0.0), width, height);

			if (p == null) {
				continue;
			}

			int x = Math.round(p[0]);
			int y = Math.round(p[1]);

			for (int ring = 0; ring < 2; ring++) {
				float grow = (age * 2.0F + ring * 0.5F) % 1.0F;
				int r = 4 + Math.round(grow * 14);
				int a = Math.round(alpha * (1.0F - grow));
				ring(graphics, x, y, r, a << 24 | 0xF0B040);
			}

			diamond(graphics, x, y, 2, alpha << 24 | 0xFFD070);
		}
	}

	/** A diamond's outline (the noise's rings). */
	private static void ring(GuiGraphicsExtractor graphics, int x, int y, int r, int colour) {
		for (int i = -r; i <= r; i++) {
			int w = r - Math.abs(i);
			graphics.fill(x - w, y + i, x - w + 1, y + i + 1, colour);

			if (w > 0) {
				graphics.fill(x + w, y + i, x + w + 1, y + i + 1, colour);
			}
		}
	}

	/** "Checkpoint saved" for a moment, small, at the top in the middle. */
	private static void checkpoint(GuiGraphicsExtractor graphics, Font font, int width) {
		int age = StoryClient.checkpointAge();

		if (age < 0) {
			return;
		}

		float fade = Math.min(1.0F, Math.min(age / 5.0F, (StoryClient.CHECKPOINT_TICKS - age) / 12.0F));
		int alpha = Math.round(Mth.clamp(fade, 0.0F, 1.0F) * 255);

		if (alpha < 8) {
			return;
		}

		Component text = Component.translatable("sabercraft.story.hud.checkpoint");
		int[] box = checkpointBox(font, text, width, radioBottom(font, width), radioRight(width), StoryClient.objectivesLeftEdge(width), StoryClient.topRightReserved());
		int x = box[0] + 14;
		int y = box[1] + 3;
		graphics.fill(box[0], box[1], box[2], box[3], (alpha / 2) << 24);
		diamond(graphics, x - 8, y + 3, 3, alpha << 24 | 0x80D8A0);
		graphics.text(font, text, x, y, alpha << 24 | 0xD8F0E0);
	}

	/**
	 * Phase 28: where the "Checkpoint saved" box goes (x0, y0, x1, y1): at the top in the middle, and
	 * below the comlink line's box (top left) and the mission panel (top right) where it would touch
	 * one of them (the comlink line drew over it on narrow screens).
	 */
	public static int[] checkpointBox(Font font, Component text, int width, int radioBottom, int radioRight, int panelLeft, int panelBottom) {
		int w = font.width(text);
		int x = width / 2 - w / 2;
		int y = 22;
		boolean underRadio = radioBottom > 0 && x - 14 < radioRight;
		boolean underPanel = x + w + 5 > panelLeft - 2;

		if (underRadio || underPanel) {
			y = Math.max(underRadio ? radioBottom : 0, underPanel ? panelBottom : 0) + 6;
		}

		return new int[] {x - 14, y - 3, x + w + 5, y + 10};
	}

	/** Phase 28: the right edge of the comlink line's box (when one is shown). */
	public static int radioRight(int width) {
		return 8 + 32 + 10 + Mth.clamp(width - objectivesWidth(StoryClient.state()) - 8 - 32 - 18, 100, 220);
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

		Component text = promptText(client, target);
		int w = client.font.width(text);
		int x = width / 2 - w / 2;
		int y = height / 2 + 14;
		graphics.fill(x - 4, y - 2, x + w + 4, y + 10, 0x80000000);
		// Phase 31: a takedown while one stands: grey (crouch first).
		int colour = target.hack() ? 0xFFB0C8E8 : target.takedown() && !crouching(client) ? 0xFFA8A8A8 : TEXT;
		graphics.text(client.font, text, x, y, colour);
	}

	/** Phase 31: whether the player crouches (a takedown needs it). */
	private static boolean crouching(Minecraft client) {
		return client.player != null && (client.player.isShiftKeyDown() || client.player.isCrouching());
	}

	/**
	 * The words under the crosshair: speak to someone, use a thing. Phase 31: overpower someone
	 * (while one stands, it says to crouch first).
	 */
	private static Component promptText(Minecraft client, StoryInput.Target target) {
		Component key = client.options.keyUse.getTranslatedKeyMessage();

		if (target.takedown()) {
			Component label = words(target.label(), "sabercraft.story.point.generic");
			return crouching(client) ? Component.translatable("sabercraft.story.hud.use", key, label)
					: Component.translatable("sabercraft.story.hud.takedown_crouch", client.options.keyShift.getTranslatedKeyMessage(),
							Component.translatable("sabercraft.story.hud.use", key, label));
		}

		return target.figure() > 0 ? Component.translatable("sabercraft.story.hud.talk", key, StoryPortraits.name(target.speaker()))
				: Component.translatable(target.hack() ? "sabercraft.story.hud.use_hack" : "sabercraft.story.hud.use", key,
						words(target.label(), "sabercraft.story.point.generic"));
	}

	/** Phase 29: the keys already reported as having no words (each once). */
	private static final java.util.Set<String> WORDLESS = new java.util.HashSet<>();
	private static int wordless;

	/**
	 * Phase 29: the words of a key, never the key itself: a key the language has no words for (the
	 * cover and the toolbox of Phase 28 showed "sabercraft.story.point.vent_cover" under the
	 * crosshair) shows the words of {@code fallback} instead, and is logged once.
	 */
	public static Component words(String key, String fallback) {
		if (net.minecraft.locale.Language.getInstance().has(key)) {
			return Component.translatable(key);
		}

		wordless++;

		if (WORDLESS.add(key)) {
			com.phiro.sabercraft.SaberCraft.LOGGER.warn("Story: no words for '{}', shown as '{}'", key, fallback);
		}

		return Component.translatable(fallback);
	}

	/** Phase 29, for tests: how often words were missing (and the fallback shown). */
	public static int wordlessForTest() {
		return wordless;
	}

	/** Phase 29, for tests: the text under the crosshair now, or null. */
	public static @org.jspecify.annotations.Nullable String promptForTest(Minecraft client) {
		StoryInput.Target target = StoryInput.target(client, 1.0F);

		if (target == null || target.figure() > 0 && !target.takedown()) {
			return null;
		}

		return promptText(client, target).getString();
	}

	// --- Lines and hints -----------------------------------------------------------------------------

	/** Phase 21: the bottom edge of the comlink line's box at the top left, or 0 when none is shown. */
	static int radioBottom(Font font, int width) {
		StoryClient.Line line = StoryClient.radio();

		if (line == null || Minecraft.getInstance().gui.screen() instanceof CoverScreen) {
			return 0;
		}

		int age = StoryClient.radioAge();
		float fade = Math.min(1.0F, Math.min(age / 6.0F, (line.ticks() - age) / 10.0F));

		if (Math.round(Mth.clamp(fade, 0.0F, 1.0F) * 255) < 8) {
			return 0;
		}

		int room = width - objectivesWidth(StoryClient.state()) - 8 - 32 - 18;
		int lines = font.split(Component.translatable(line.key()), Mth.clamp(room, 100, 220)).size();
		return 8 + Math.max(32 + 4, 14 + lines * 10);
	}

	private static void radio(GuiGraphicsExtractor graphics, Font font) {
		StoryClient.Line line = StoryClient.radio();

		// Phase 28: not over the cover's screws (the line waits till that screen is closed).
		if (line == null || Minecraft.getInstance().gui.screen() instanceof CoverScreen) {
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
		// Left of the objectives' panel, never under it.
		int room = graphics.guiWidth() - objectivesWidth(StoryClient.state()) - x - size - 18;
		int textWidth = Mth.clamp(room, 100, 220);
		List<FormattedCharSequence> lines = font.split(Component.translatable(line.key()), textWidth);
		int h = Math.max(size + 4, 14 + lines.size() * 10);
		graphics.fill(x - 4, y - 4, x + size + 10 + textWidth, y + h, (alpha * 3 / 5) << 24);
		// Over the comlink a blue hologram; overheard in the room, the face itself.
		StoryPortraits.draw(graphics, line.who(), x, y, size, !line.heard(), alpha);
		int tx = x + size + 8;
		graphics.text(font, StoryPortraits.name(line.who()), tx, y, alpha << 24 | (line.heard() ? 0xD8C8A0 : 0x8CC0FF));
		int ty = y + 11;

		for (FormattedCharSequence text : lines) {
			graphics.text(font, text, tx, ty, alpha << 24 | 0xE8EEF6);
			ty += 10;
		}
	}

	/** Phase 20: where the hint's first line is now (or far below the screen without one). */
	private static int hintTop(Font font, int height) {
		String key = StoryClient.hint();
		return key == null ? Integer.MAX_VALUE : height - 72 - font.split(Component.translatable(key), 280).size() * 10 - 2;
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

	/**
	 * Phase 30: where a box centred on the screen, {@code boxHeight} tall, goes: the first of
	 * {@code tops} (its top edge, GUI y; at least one) at which it is on the screen and clear of the
	 * crosshair, of what the crosshair is on and the stun marks there (while either shows), of a
	 * hint over the hotbar and everything under it (while one shows), of the line over the hotbar
	 * (a room's name, a message; while it shows) and of a mission's title card (while one shows).
	 * When none is clear, the first that is clear of all but the line over the hotbar (it goes after
	 * 3 s), else of all but the title card (it fades after a few seconds; the hint and the prompt
	 * stay), else the first of {@code tops}. The progress bar (a download, a hack, the maintenance
	 * door's release) and the maintenance lift's panel go by it.
	 */
	public static int clearTop(Font font, int width, int height, int boxHeight, int... tops) {
		return clearTop(font, width, height, boxHeight, 0, 0, tops);
	}

	/** Phase 30: {@link #clearTop}, also clear of the box from {@code avoidTop} to {@code avoidBottom} (GUI y; none when both are 0). */
	private static int clearTop(Font font, int width, int height, int boxHeight, int avoidTop, int avoidBottom, int... tops) {
		Minecraft client = Minecraft.getInstance();
		boolean prompt = client.gui.screen() == null && StoryInput.target(client, 1.0F) != null || StoryClient.stuns() > 0 && StoryClient.stunAge() < 200;
		int hint = hintTop(font, height);
		boolean line = ((HudAccessor) client.gui.hud).sabercraft$overlayMessageTime() > 0;
		// (Its words from 72 over the screen's bottom; 2 more with the text background Minecraft draws behind them only when set so.)
		int lineTop = height - 72 - (client.options.getBackgroundColor(0.0F) != 0 ? 2 : 0);
		String title = StoryClient.title();
		int card = title != null ? titleTop(font, title, width, height) : 0;

		// (First clear of everything; then of all but the line over the hotbar; then of all but the title card.)
		for (int pass = 0; pass < 3; pass++) {
			boolean byLine = pass != 1;
			boolean byTitle = pass != 2;

			for (int top : tops) {
				int bottom = top + boxHeight;
				boolean clear = top >= 0 && bottom <= height
						// The crosshair.
						&& (bottom <= height / 2 - 8 || top >= height / 2 + 8)
						// What the crosshair is on ("Use: …", from 2 over its words to 10 under them) and the stun marks.
						&& (!prompt || bottom <= height / 2 + 12 || top >= height / 2 + 24)
						// A hint over the hotbar: its box (and a little room over it) and what is under it.
						&& bottom <= hint - 2
						// The line over the hotbar (Minecraft's: a room's name, a message).
						&& (!byLine || !line || bottom <= lineTop)
						// A mission's title card: its small line, its big words and the line under them.
						&& (!byTitle || title == null || bottom <= card || top >= card + 37)
						// Another box (the maintenance lift's panel).
						&& (avoidTop == avoidBottom || bottom <= avoidTop || top >= avoidBottom);

				if (clear) {
					return top;
				}
			}
		}

		return tops[0];
	}

	/** Phase 30: the maintenance lift's panel as last drawn (GUI y), until the progress bar has kept clear of it once; both 0: none. */
	private static int liftTop;
	private static int liftBottom;
	private static int progressFrames;

	/** Phase 30: the maintenance lift's panel is drawn there this frame (GUI y): the progress bar keeps clear of it. */
	public static void liftPanel(int top, int bottom) {
		liftTop = top;
		liftBottom = bottom;
	}

	/** Phase 30, for the tests: how many frames the progress bar was drawn (all, and while a mission's title card showed). */
	public static int[] progressFramesForTest() {
		return new int[] {progressFrames, progressUnderTitle};
	}

	private static int progressUnderTitle;

	/** Phase 30: the top of the progress bar's box ({@link #PROGRESS_BOX} high, GUI y), clear of the maintenance lift's panel from {@code avoidTop} to {@code avoidBottom} (both 0: none). */
	public static int progressTop(Font font, int width, int height, int avoidTop, int avoidBottom) {
		// Phase 30: its box where it always was, under the crosshair, when nothing else is there;
		// else above the crosshair; else right over the hint, while nothing shows under the
		// crosshair (on a small screen with a title and a hint at once). See clearTop.
		int box = PROGRESS_BOX;
		// (Phase 30: clear of the maintenance lift's panel too, while it shows; else just under it.)
		int[] tops = avoidBottom > avoidTop ? new int[] {height / 2 + 27, height / 2 - 10 - box, hintTop(font, height) - 2 - box, avoidBottom + 2}
				: new int[] {height / 2 + 27, height / 2 - 10 - box, hintTop(font, height) - 2 - box};
		return clearTop(font, width, height, box, avoidTop, avoidBottom, tops);
	}

	/** Phase 30: how high the progress bar's box is (its words and bar, 3 over them and 4 under). */
	public static final int PROGRESS_BOX = 21;

	private static void progress(GuiGraphicsExtractor graphics, Font font, int width, int height) {
		String key = StoryClient.progressKey();
		int avoidTop = liftTop;
		int avoidBottom = liftBottom;
		liftTop = 0;
		liftBottom = 0;

		// Phase 30: shown under a mission's title card too (the maintenance door's release, clicked
		// within the 5.5 s of Mission 5's title, ran unseen under it). Phase 20 kept it away from the
		// title only for the download's finished copy that stood under Mission 5's title: a bar from
		// before is over now when a title comes (StoryClient).
		if (key == null) {
			return;
		}

		int value = Mth.clamp(StoryClient.progressValue(), 0, 100);
		Component text = Component.translatable(key, value);
		int w = Math.max(140, font.width(text));
		int x = width / 2 - w / 2;
		int y = progressTop(font, width, height, avoidTop, avoidBottom) + 3;
		progressFrames++;

		if (StoryClient.title() != null) {
			progressUnderTitle++;
		}

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
		int y = titleTop(font, key, width, height);
		graphics.text(font, small, width / 2 - font.width(small) / 2, y, alpha << 24 | 0xC8B070);
		graphics.pose().pushMatrix();
		graphics.pose().translate(width / 2.0F, y + 14);
		graphics.pose().scale(2.0F, 2.0F);
		graphics.text(font, big, -font.width(big) / 2, 0, alpha << 24 | 0xF2ECDC);
		graphics.pose().popMatrix();
		int lw = Math.round(font.width(big) * fade);
		graphics.fill(width / 2 - lw, y + 36, width / 2 + lw, y + 37, alpha / 2 << 24 | 0xE8C46A);
	}

	/** Phase 30: where a mission's title card begins (GUI y); it reaches 37 further down (see title). */
	private static int titleTop(Font font, String key, int width, int height) {
		StoryStatePayload state = StoryClient.state();
		Component small = Component.translatable("sabercraft.story.hud.chapter_mission", state != null ? state.mission() + 1 : 1);
		Component big = Component.translatable(key);
		int y = height / 4;
		int half = Math.max(font.width(small), font.width(big) * 2) / 2 + 4;

		// Phase 28: on a narrow screen it reached under the mission panel at the top right: below it then.
		if (width / 2 + half > StoryClient.objectivesLeftEdge(width)) {
			y = Math.max(y, StoryClient.topRightReserved() + 2);
		}

		return y;
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

		if (age > END_TICKS) {
			StoryClient.dismissEnd();
			return;
		}

		float fade = Mth.clamp(Math.min(age / 30.0F, (END_TICKS - age) / 40.0F), 0.0F, 1.0F);
		int alpha = Math.round(fade * 255);
		graphics.fill(0, 0, width, height, Math.round(fade * 200) << 24);
		Component big = Component.translatable("sabercraft.story.hud.end_title");
		graphics.pose().pushMatrix();
		graphics.pose().translate(width / 2.0F, height / 3.0F);
		graphics.pose().scale(2.0F, 2.0F);
		graphics.text(font, big, -font.width(big) / 2, 0, alpha << 24 | 0xF2ECDC);
		graphics.pose().popMatrix();
		int y = height / 3 + 30;

		for (FormattedCharSequence line : font.split(Component.translatable("sabercraft.story.end", com.phiro.sabercraft.client.SaberCraftClient.CUSTOMIZE_KEY.getTranslatedKeyMessage()), 300)) {
			graphics.text(font, line, width / 2 - font.width(line) / 2, y, alpha << 24 | 0xC8C0B0);
			y += 11;
		}

		// Phase 32: who made it, a small credit in the mission cards' style (their gold, a gold rule
		// over it), in wide capitals, coming in a little after the text and fading out with it.
		float credit = Mth.clamp(Math.min((age - CREDIT_FROM) / 30.0F, (END_TICKS - age) / 40.0F), 0.0F, 1.0F);

		if (credit > 0.0F) {
			String text = Component.translatable("sabercraft.story.hud.end_credit").getString().toUpperCase(java.util.Locale.ROOT);
			int track = 2;
			int wide = 0;

			for (int i = 0; i < text.length(); i++) {
				wide += font.width(String.valueOf(text.charAt(i))) + (i < text.length() - 1 ? track : 0);
			}

			int a = Math.round(credit * 255);
			int top = y + 14;
			int rule = Math.round((wide / 2.0F + 6) * credit);
			graphics.fill(width / 2 - rule, top - 5, width / 2 + rule, top - 4, a / 2 << 24 | CREDIT_COLOUR);
			graphics.pose().pushMatrix();
			graphics.pose().translate(width / 2.0F, top);
			int x = -wide / 2;

			for (int i = 0; i < text.length(); i++) {
				String letter = String.valueOf(text.charAt(i));
				graphics.text(font, letter, x, 0, a << 24 | CREDIT_COLOUR);
				x += font.width(letter) + track;
			}

			graphics.pose().popMatrix();
			creditShown = true;
		}
	}

	/**
	 * Phase 32: how long the chapter's end shows (ticks; it was 360: a little shorter, still time
	 * to read it), from when the credit comes in, and its colour (the mission cards' gold).
	 */
	static final int END_TICKS = 300;
	static final int CREDIT_FROM = 60;
	static final int CREDIT_COLOUR = 0xE8C46A;
	private static boolean creditShown;

	/** Phase 32, for the tests: how long the chapter's end shows (ticks), and whether its credit has been drawn. */
	public static int endTicksForTest() {
		return END_TICKS;
	}

	public static boolean creditShownForTest() {
		return creditShown;
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
