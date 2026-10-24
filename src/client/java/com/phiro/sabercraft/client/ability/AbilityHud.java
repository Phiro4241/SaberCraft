package com.phiro.sabercraft.client.ability;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.HumanoidArm;

import com.phiro.sabercraft.ability.SaberAbility;
import com.phiro.sabercraft.client.combat.CombatConfig;
import com.phiro.sabercraft.saber.SaberVariant;

/**
 * Small cooldown display next to the hotbar: one line per ability that is cooling down, with its
 * key and a bar that runs out. Nothing is shown while every ability is ready, so the HUD stays
 * clean.
 */
final class AbilityHud implements HudElement {
	private static final int BAR_WIDTH = 30;
	private static final int LINE = 9;

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft client = Minecraft.getInstance();

		if (!CombatConfig.get().abilityHud || client.player == null
				|| !SaberVariant.canHold(client.player.getMainHandItem())) {
			return;
		}

		Font font = client.font;
		// Beside the hotbar, on the side without the off-hand slot.
		boolean right = client.player.getMainArm() == HumanoidArm.RIGHT;
		int y = graphics.guiHeight() - 12;
		float partial = delta.getGameTimeDeltaPartialTick(false);

		for (SaberAbility ability : SaberAbility.values()) {
			SaberAbilityClient.Cooldown cooldown = SaberAbilityClient.cooldown(ability);
			boolean active = ability == SaberAbility.GUARD && SaberAbilityClient.isGuarding();

			if (!active && (cooldown == null || cooldown.left() <= 0)) {
				continue;
			}

			Component key = SaberAbilityClient.keyOf(ability).getTranslatedKeyMessage();
			int labelWidth = Math.min(font.width(key), 40);
			int width = labelWidth + 3 + BAR_WIDTH;
			int x = right ? graphics.guiWidth() / 2 + 96 : graphics.guiWidth() / 2 - 96 - width;
			float share = active ? 1.0F : Math.max(0.0F, (cooldown.left() - partial)) / cooldown.total();
			int color = active ? 0xFF7FD8FF : 0xFFE0E0E0;

			graphics.text(font, key, x, y - 1, color, true);
			int barX = x + labelWidth + 3;
			graphics.fill(barX, y + 2, barX + BAR_WIDTH, y + 5, 0x90000000);
			graphics.fill(barX, y + 2, barX + Math.round(BAR_WIDTH * share), y + 5, active ? 0xFF4FB4FF : 0xFFB0B0B0);
			y -= LINE;
		}
	}
}
