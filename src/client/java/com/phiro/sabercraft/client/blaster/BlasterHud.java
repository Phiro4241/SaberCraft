package com.phiro.sabercraft.client.blaster;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import com.phiro.sabercraft.blaster.BlasterType;
import com.phiro.sabercraft.blaster.Blasters;
import com.phiro.sabercraft.client.combat.CombatConfig;

/**
 * The heat of the blaster in hand: a thin bar under the crosshair, only while the blaster is warm.
 * It turns from yellow to red, and flashes with a note while the blaster is overheated. Since Phase
 * 11 the arrows left are shown next to the crosshair.
 */
final class BlasterHud implements HudElement {
	private static final int WIDTH = 40;

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft client = Minecraft.getInstance();

		if (!CombatConfig.get().abilityHud || client.player == null || client.gui.hud.isHidden()
				|| BlasterType.firing(client.player.getMainHandItem()) == null && BlasterType.firing(client.player.getOffhandItem()) == null) {
			return;
		}

		float heat = BlasterClient.heat();
		int x = graphics.guiWidth() / 2 - WIDTH / 2;
		int y = graphics.guiHeight() / 2 + 10;

		// Phase 11: the arrows left, small under the crosshair, red when there are none.
		boolean bowBlaster = BlasterType.of(client.player.getMainHandItem()) != null || BlasterType.of(client.player.getOffhandItem()) != null;

		if (bowBlaster && BlasterClient.serverHasBlasters() && BlasterClient.needsAmmo(client.player)) {
			int arrows = Blasters.countAmmo(client.player.getInventory());
			Component ammo = Component.translatableWithFallback("sabercraft.hud.ammo", "Arrows: %s", arrows);
			graphics.text(client.font, ammo, graphics.guiWidth() / 2 + 12, graphics.guiHeight() / 2 - 4, arrows > 0 ? 0xC0E0E0E8 : 0xFFFF5040);
		}

		if (heat <= 0.5F && !BlasterClient.overheated()) {
			return;
		}

		float share = Math.clamp(heat / Blasters.MAX_HEAT, 0.0F, 1.0F);
		boolean hot = BlasterClient.overheated();
		boolean blink = hot && (System.currentTimeMillis() / 250L) % 2L == 0L;
		int red = 0xFF;
		int green = Math.round(210 * (1.0F - share));
		int color = 0xFF000000 | red << 16 | green << 8 | 0x30;
		graphics.fill(x - 1, y - 1, x + WIDTH + 1, y + 3, 0x90000000);
		graphics.fill(x, y, x + Math.round(WIDTH * share), y + 2, blink ? 0xFFFFFFFF : color);

		if (hot) {
			Component text = Component.translatable("sabercraft.hud.overheated");
			graphics.centeredText(client.font, text, graphics.guiWidth() / 2, y + 5, 0xFFFF6040);
		}
	}
}
