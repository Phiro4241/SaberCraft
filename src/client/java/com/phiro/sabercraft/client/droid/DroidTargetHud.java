package com.phiro.sabercraft.client.droid;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

import com.phiro.sabercraft.client.ImmersionConfig;
import com.phiro.sabercraft.droid.DroidMarker;
import com.phiro.sabercraft.droid.DroidType;
import com.phiro.sabercraft.network.DroidStatusPayload;

/**
 * Phase 12: a small hint under the crosshair while the player looks at one of their droids: its
 * name, its ability with the key (and the seconds until it is ready again), and the key for wait
 * or follow. Off with {@code droid_target_hud}. Nothing is shown for other players' droids.
 */
final class DroidTargetHud implements HudElement {
	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft client = Minecraft.getInstance();
		Entity droid = DroidControl.target();

		if (droid == null || client.player == null || !ImmersionConfig.get().droidTargetHud || client.gui.hud.isHidden() || client.gui.screen() != null
				|| !DroidControl.isOwn(droid)) {
			return;
		}

		DroidType type = DroidMarker.of(droid);
		DroidStatusPayload.Entry entry = type != null ? DroidClient.entry(type) : null;

		if (type == null || entry == null) {
			return;
		}

		int cooldown = DroidClient.cooldownSeconds(entry);
		Component ability = Component.translatable("sabercraft.droid.key.ability", DroidControl.ABILITY_KEY.getTranslatedKeyMessage(),
				Component.translatable("sabercraft.droid." + type.id() + ".ability_name"));

		if (cooldown > 0) {
			ability = Component.translatable("sabercraft.droid.key.cooldown", ability, cooldown);
		}

		Component order = Component.translatable(entry.staying() ? "sabercraft.droid.key.follow" : "sabercraft.droid.key.stay",
				DroidControl.ORDER_KEY.getTranslatedKeyMessage());
		int x = graphics.guiWidth() / 2;
		int y = graphics.guiHeight() / 2 + 12;
		Component name = Component.translatable(type.translationKey());
		int width = Math.max(client.font.width(name), Math.max(client.font.width(ability), client.font.width(order))) + 8;
		graphics.fill(x - width / 2, y - 2, x + width / 2, y + 31, 0x70000000);
		graphics.centeredText(client.font, name, x, y, 0xFFE8F0FF);
		graphics.centeredText(client.font, ability, x, y + 10, cooldown > 0 ? 0xFF9098A0 : 0xFFFFD060);
		graphics.centeredText(client.font, order, x, y + 20, 0xFFB8C8D8);
	}
}
