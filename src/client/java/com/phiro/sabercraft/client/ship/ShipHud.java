package com.phiro.sabercraft.client.ship;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import com.phiro.sabercraft.client.ImmersionConfig;
import com.phiro.sabercraft.ship.ShipMarker;
import com.phiro.sabercraft.ship.ShipType;

/**
 * The flight display while flying a starship (Phase 11): a small panel at the top left with
 * the speed (bar and blocks per second), the height, the pitch of the nose and the lasers, and the
 * controls in one line.
 */
final class ShipHud implements HudElement {
	private static final int WIDTH = 150;

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft client = Minecraft.getInstance();
		Entity ship = ShipClient.piloting(client);
		ShipType type = ShipMarker.of(ship);

		if (ship == null || type == null || !ImmersionConfig.get().shipHud || client.gui.hud.isHidden()) {
			return;
		}

		ShipClient.Visual visual = ShipClient.visual(ship);
		float speed = visual.speed();
		float share = (float) Math.min(1.0, speed / type.maxSpeed());
		int x = 6;
		int y = 6;
		graphics.fill(x - 3, y - 3, x + WIDTH + 3, y + 56, 0x90000000);
		graphics.outline(x - 3, y - 3, WIDTH + 6, 59, 0xFF3A5A7A);
		graphics.text(client.font, Component.translatable(type.translationKey()), x, y, 0xFFE8F0FF);

		// Speed bar: blue when cruising, white near top speed.
		y += 11;
		graphics.fill(x, y, x + WIDTH, y + 4, 0xFF20242C);
		graphics.fill(x, y, x + Math.round(WIDTH * share), y + 4, share > 0.9F ? 0xFFE8F4FF : 0xFF4A9AE8);
		y += 7;
		graphics.text(client.font, Component.translatableWithFallback("sabercraft.ship.hud.speed", "Speed %s m/s", Math.round(speed * 20.0F)), x, y,
				0xFFB8D0E8);
		graphics.text(client.font, Component.translatableWithFallback("sabercraft.ship.hud.height", "Height %s", Math.round(ship.getY())), x + 84, y,
				0xFFB8D0E8);
		y += 10;
		long since = ShipClient.sinceShot();
		boolean firing = since < type.interval() * 50L + 60L;
		Component lasers = Component.translatableWithFallback(firing ? "sabercraft.ship.hud.firing" : "sabercraft.ship.hud.ready",
				firing ? "Lasers firing" : "Lasers ready");
		graphics.text(client.font, lasers, x, y, firing ? 0xFFFF7060 : 0xFF70E090);
		int pitch = Math.round(-ship.getXRot());
		graphics.text(client.font, Component.translatableWithFallback("sabercraft.ship.hud.pitch", "Nose %s°", pitch), x + 84, y, 0xFFB8D0E8);

		// Phase 12: the hull, green to red.
		y += 10;
		float hull = ship instanceof LivingEntity living ? Math.clamp(living.getHealth() / type.hull(), 0.0F, 1.0F) : 1.0F;
		int hullColor = hull > 0.6F ? 0xFF70E090 : hull > 0.3F ? 0xFFE8C050 : 0xFFFF5040;
		graphics.text(client.font, Component.translatableWithFallback("sabercraft.ship.hud.hull", "Hull %s %%", Math.round(hull * 100.0F)), x, y,
				hullColor);
		graphics.fill(x + 84, y + 2, x + WIDTH, y + 6, 0xFF20242C);
		graphics.fill(x + 84, y + 2, x + 84 + Math.round((WIDTH - 84) * hull), y + 6, hullColor);

		// The controls, small and dim, until the pilot has flown for a while.
		if (ship.tickCount < 20 * 60 || speed < 0.05F) {
			Component keys = Component.translatableWithFallback("sabercraft.ship.hud.keys",
					"W/S throttle · A/D turn · Space/Sprint nose · Attack fire · Sneak exit");
			graphics.text(client.font, keys, x - 2, 68, 0xA0C0C8D0);
		}
	}
}
