package com.phiro.sabercraft.client;

import java.util.ArrayDeque;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.client.story.StoryClient;

/**
 * Phase 28: the card of the one-time hints ({@link Hints}). They were Minecraft's own toasts, at
 * the top right, where the story's mission panel stands, and covered it. Now SaberCraft draws the
 * card itself, with the toast's look (its frame, the yellow title, the white text), at the
 * <b>left</b>: under the place of the comlink line at the top left (lower still while a long
 * comlink line shows), never under the mission panel (on a narrow screen the card's text is
 * wrapped so it ends before the panel), and above the chat. It slides in from the left edge,
 * stays ten seconds and slides out; while a screen is open (a dialog, a menu) it waits and its
 * time stands still. Several hints come one after the other.
 */
public final class HintCard implements HudElement {
	private static final Identifier BACKGROUND = Identifier.withDefaultNamespace("toast/system");
	/** As long as Minecraft's toast showed the hints. */
	static final long SHOW_MS = 10_000L;
	static final long SLIDE_MS = 350L;
	/** From the left edge. */
	static final int LEFT = 6;
	/**
	 * The card's top when no comlink line shows: below where a comlink line of up to three lines
	 * ends (its box reaches about 52), so it rarely has to move.
	 */
	static final int TOP = 58;
	/** Kept free between the card and the mission panel, the comlink line, the chat. */
	static final int GAP = 6;
	/** Room at the bottom for the hotbar, the hearts and a few lines of chat. */
	static final int BOTTOM = 96;

	/** A hint waiting or showing. */
	record Card(Component title, Component message) {
	}

	/** Where a card stands and its lines, for a screen of that size. */
	public record Layout(int x, int y, int width, int height, List<FormattedCharSequence> title, List<FormattedCharSequence> message) {
		public boolean overlaps(int x0, int y0, int x1, int y1) {
			return this.x < x1 && this.x + this.width > x0 && this.y < y1 && this.y + this.height > y0;
		}
	}

	private static final ArrayDeque<Card> QUEUE = new ArrayDeque<>();
	private static @Nullable Card current;
	/** How long the current card was on screen (screens open do not count), and the last frame's clock. */
	private static long shownFor;
	private static long lastFrame = -1L;
	/** Where the card is drawn now (it glides to a new place instead of jumping). */
	private static float drawnY = -1.0F;
	private static @Nullable Layout last;

	private HintCard() {
	}

	static void init() {
		HudElementRegistry.addLast(SaberCraft.id("hint_card"), new HintCard());
	}

	/** Shows a hint (after the one showing now, if any). The same hint twice in a row is shown once. */
	static void show(Component title, Component message) {
		Card card = new Card(title, message);

		if (card.equals(current) || QUEUE.contains(card)) {
			return;
		}

		QUEUE.add(card);
	}

	/** Leaving a world: nothing left over for the next. */
	static void clear() {
		QUEUE.clear();
		current = null;
		shownFor = 0L;
		lastFrame = -1L;
		drawnY = -1.0F;
		last = null;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft client = Minecraft.getInstance();
		long now = Util.getMillis();
		long step = lastFrame < 0L ? 0L : Mth.clamp(now - lastFrame, 0L, 200L);
		lastFrame = now;

		if (current == null) {
			current = QUEUE.poll();
			shownFor = 0L;
			drawnY = -1.0F;

			if (current == null) {
				last = null;
				return;
			}
		}

		// A screen open (a dialog, a menu, the inventory), a film, a mission's title card or the HUD
		// hidden: the card waits.
		if (client.player == null || client.gui.screen() != null || client.gui.hud.isHidden() || com.phiro.sabercraft.client.story.StoryCinema.active()
				|| StoryClient.titleShowing()) {
			last = null;
			return;
		}

		shownFor += step;

		if (shownFor >= SHOW_MS + 2 * SLIDE_MS) {
			current = null;
			last = null;
			return;
		}

		int width = graphics.guiWidth();
		int height = graphics.guiHeight();
		Layout layout = layout(client.font, current, width, height, StoryClient.objectivesLeftEdge(width), StoryClient.radioBottom(width));

		if (drawnY < 0.0F) {
			drawnY = layout.y();
		} else {
			drawnY += (layout.y() - drawnY) * Math.min(1.0F, step / 120.0F);
		}

		float in = Math.min(1.0F, shownFor / (float) SLIDE_MS);
		float out = Math.min(1.0F, (SHOW_MS + 2 * SLIDE_MS - shownFor) / (float) SLIDE_MS);
		float visible = Mth.clamp(Math.min(in, out), 0.0F, 1.0F);
		visible = visible * visible * (3.0F - 2.0F * visible);
		int x = Math.round(layout.x() - (1.0F - visible) * (layout.width() + LEFT + 2));
		int y = Math.round(drawnY);
		last = new Layout(x, y, layout.width(), layout.height(), layout.title(), layout.message());

		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BACKGROUND, x, y, layout.width(), layout.height());
		int ty = y + 7;

		for (FormattedCharSequence line : layout.title()) {
			graphics.text(client.font, line, x + 18, ty, 0xFFFFFF00, false);
			ty += 12;
		}

		for (FormattedCharSequence line : layout.message()) {
			graphics.text(client.font, line, x + 18, ty, 0xFFFFFFFF, false);
			ty += 12;
		}
	}

	/**
	 * Where the card stands on a screen {@code width} × {@code height} (GUI pixels): at the left,
	 * below the comlink line's place ({@code radioBottom}: where the comlink line's box ends now, 0
	 * without one), its right edge before {@code rightEdge} (the mission panel's left edge, or the
	 * screen's edge without one), above the hotbar and the chat where the screen is tall enough.
	 */
	public static Layout layout(Font font, Component title, Component message, int width, int height, int rightEdge, int radioBottom) {
		return layout(font, new Card(title, message), width, height, rightEdge, radioBottom);
	}

	static Layout layout(Font font, Card card, int width, int height, int rightEdge, int radioBottom) {
		// As Minecraft's toast: the text from 18 in, 12 apart, the card 30 wider than its widest line
		// and at least 160 wide; but never past the mission panel.
		int room = Math.max(120, rightEdge - GAP - LEFT);
		int textWidth = Mth.clamp(room - 30, 90, 200);
		List<FormattedCharSequence> title = font.split(card.title(), textWidth);
		List<FormattedCharSequence> message = font.split(card.message(), textWidth);
		int widest = 0;

		for (FormattedCharSequence line : title) {
			widest = Math.max(widest, font.width(line));
		}

		for (FormattedCharSequence line : message) {
			widest = Math.max(widest, font.width(line));
		}

		int cardWidth = Math.min(room, Math.max(Math.min(160, textWidth), widest) + 30);
		int cardHeight = 20 + (Math.max(1, title.size()) - 1) * 12 + Math.max(1, message.size()) * 12;
		int y = Math.max(TOP, radioBottom + GAP);
		// A short screen: up as far as the comlink line allows, rather than into the hotbar.
		y = Math.max(Math.min(y, height - BOTTOM - cardHeight), radioBottom + GAP);
		return new Layout(LEFT, Math.max(2, y), cardWidth, cardHeight, title, message);
	}

	/** For the tests: the card on screen now (where it was last drawn), or null. */
	public static @Nullable Layout shownForTest() {
		return last;
	}

	/** For the tests: hints waiting or showing. */
	public static int pendingForTest() {
		return QUEUE.size() + (current == null ? 0 : 1);
	}

	/** For the tests: shows a hint now (as {@link Hints} does). */
	public static void showForTest(Component title, Component message) {
		show(title, message);
	}

	/** For the tests: nothing showing. */
	public static void clearForTest() {
		clear();
	}
}
