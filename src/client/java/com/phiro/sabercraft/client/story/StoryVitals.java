package com.phiro.sabercraft.client.story;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * Phase 22: the HUD of the story. Hunger, experience and armour mean nothing in the story (one
 * never eats, never levels, the disguise is not armour), so while a story runs they are not drawn;
 * the hearts give way to a slim "condition" bar in the style of the story's panels: its segments
 * fill amber, they turn red when little is left, and a lighter band shows for a moment what a hit
 * took. Outside the story every one of these is drawn as Minecraft draws it, unchanged.
 */
public final class StoryVitals {
	/** The bar: as wide as the hearts' row, where it stands. */
	static final int WIDTH = 81;
	static final int HEIGHT = 5;
	static final int SEGMENTS = 10;

	/** What a hit took, fading (share of the bar), and the health seen last. */
	private static float shown = -1.0F;
	private static float lag = -1.0F;
	private static int hurtAge = 100;
	private static int drawn;

	private StoryVitals() {
	}

	static void init() {
		HudElementRegistry.replaceElement(VanillaHudElements.FOOD_BAR, StoryVitals::outsideOnly);
		HudElementRegistry.replaceElement(VanillaHudElements.EXPERIENCE_LEVEL, StoryVitals::outsideOnly);
		HudElementRegistry.replaceElement(VanillaHudElements.INFO_BAR, StoryVitals::outsideOnly);
		HudElementRegistry.replaceElement(VanillaHudElements.ARMOR_BAR, StoryVitals::outsideOnly);
		HudElementRegistry.replaceElement(VanillaHudElements.HEALTH_BAR, vanilla -> (graphics, delta) -> {
			if (hidesVanilla()) {
				condition(graphics, delta);
			} else {
				vanilla.extractRenderState(graphics, delta);
			}
		});
	}

	private static HudElement outsideOnly(HudElement vanilla) {
		return (graphics, delta) -> {
			if (!hidesVanilla()) {
				vanilla.extractRenderState(graphics, delta);
			}
		};
	}

	/** Whether the story's HUD stands in for Minecraft's now (only while a story runs). */
	public static boolean hidesVanilla() {
		return StoryClient.active();
	}

	/** Once a tick: what a hit took fades out slowly. */
	static void tick(Minecraft client) {
		Player player = client.player;

		if (player == null || !StoryClient.active()) {
			shown = -1.0F;
			lag = -1.0F;
			return;
		}

		float now = Mth.clamp(player.getHealth() / Math.max(1.0F, player.getMaxHealth()), 0.0F, 1.0F);

		if (shown < 0.0F) {
			shown = now;
			lag = now;
		}

		if (now < shown - 0.001F) {
			hurtAge = 0;
		} else {
			hurtAge++;
		}

		shown = now;

		// The lighter band holds a moment, then follows.
		if (hurtAge > 10) {
			lag = Math.max(now, lag - 0.02F);
		}

		lag = Math.max(lag, now);
	}

	private static void condition(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft client = Minecraft.getInstance();
		Player player = client.player;

		if (player == null || client.gameMode == null || !client.gameMode.canHurtPlayer()) {
			return;
		}

		float now = Mth.clamp(player.getHealth() / Math.max(1.0F, player.getMaxHealth()), 0.0F, 1.0F);
		float trail = Math.max(now, lag < 0.0F ? now : lag);
		int x = graphics.guiWidth() / 2 - 91;
		int y = graphics.guiHeight() - 37;
		boolean low = now <= 0.3F;
		// Low: the frame pulses slowly.
		int pulse = low ? (int) (90 + 80 * (0.5F + 0.5F * Mth.sin((client.level != null ? client.level.getGameTime() : 0) * 0.25F))) : 0;
		int frame = low ? pulse << 24 | 0xE04030 : 0xA0E8C46A;
		// The panel behind it, as the story's panels (dark, a gold edge on the left).
		graphics.fill(x - 2, y - 2, x + WIDTH + 2, y + HEIGHT + 2, 0x78000000);
		graphics.fill(x - 2, y - 2, x - 1, y + HEIGHT + 2, frame);
		int fill = Math.round(WIDTH * now);
		int band = Math.round(WIDTH * trail);
		int colour = low ? 0xFFE0503A : now <= 0.6F ? 0xFFE8A04A : 0xFFE8C46A;

		if (band > fill) {
			graphics.fill(x + fill, y, x + band, y + HEIGHT, 0xC0F4E6C8);
		}

		graphics.fill(x, y, x + fill, y + HEIGHT, colour);
		// A lighter line on top: it reads as a lit strip, not a flat block.
		graphics.fill(x, y, x + fill, y + 1, 0x60FFFFFF);

		// The segments: thin gaps, one per two hearts' worth.
		for (int i = 1; i < SEGMENTS; i++) {
			int sx = x + Math.round(WIDTH * i / (float) SEGMENTS);
			graphics.fill(sx, y, sx + 1, y + HEIGHT, 0x90000000);
		}

		// Its name, small, right of it (where the hunger bar was), only while hurt.
		if (now < 0.999F) {
			Component label = Component.translatable("sabercraft.story.hud.condition");
			int lx = graphics.guiWidth() / 2 + 91 - client.font.width(label);
			graphics.text(client.font, label, lx, y - 2, low ? 0xFFE87060 : 0xFF9AA6B4);
		}

		drawn++;
	}

	/** For tests: how often the story's bar was drawn instead of the hearts. */
	public static int drawnForTest() {
		return drawn;
	}

	/** For tests: the share of health the bar shows. */
	public static float shownForTest() {
		return shown;
	}
}
