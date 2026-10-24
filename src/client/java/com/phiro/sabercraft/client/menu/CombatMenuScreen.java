package com.phiro.sabercraft.client.menu;

import java.util.List;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.network.chat.Component;

import com.phiro.sabercraft.client.SaberCraftClient;
import com.phiro.sabercraft.client.ability.SaberAbilityClient;
import com.phiro.sabercraft.client.combat.CombatConfig;

/**
 * Combat, powers and keys: the keys of all moves and powers at a glance (changed in the vanilla
 * key settings, button below), and the client options of the combat animations. What a power
 * does, its cooldown and stamina cost are decided by the server.
 */
public class CombatMenuScreen extends SaberMenuScreen {
	private static final List<KeyMapping> KEYS = List.of(SaberAbilityClient.GUARD_KEY, SaberAbilityClient.SPIN_KEY, SaberAbilityClient.DASH_KEY,
			SaberAbilityClient.PUSH_KEY, SaberAbilityClient.GRIP_KEY, SaberAbilityClient.PULL_KEY, SaberAbilityClient.JUMP_KEY,
			SaberCraftClient.CUSTOMIZE_KEY);

	public CombatMenuScreen() {
		super(Component.translatable("sabercraft.menu.combat.title"));
	}

	@Override
	protected Tab tab() {
		return Tab.COMBAT;
	}

	@Override
	protected void initPanel() {
		CombatConfig config = CombatConfig.get();
		this.toggle(0, 4, "sabercraft.option.combat_animations", config.combatAnimations, v -> {
			config.combatAnimations = v;
			CombatConfig.save();
		});
		this.toggle(1, 4, "sabercraft.option.ready_stance", config.readyStance, v -> {
			config.readyStance = v;
			CombatConfig.save();
		});
		this.toggle(0, 5, "sabercraft.option.ability_hud", config.abilityHud, v -> {
			config.abilityHud = v;
			CombatConfig.save();
		});
		this.toggle(1, 5, "sabercraft.option.landing_animation", config.landingAnimation, v -> {
			config.landingAnimation = v;
			CombatConfig.save();
		});
		this.button(0, 7, Component.translatable("sabercraft.menu.combat.keys"), () -> {
			this.minecraft.gui.setScreen(new KeyBindsScreen(this, this.minecraft.options));
			return true;
		});
		this.button(1, 7, Component.translatable("gui.done"), () -> {
			this.onClose();
			return true;
		});
	}

	@Override
	protected void extractPanel(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		// The keys in two columns: name left, key right.
		for (int i = 0; i < KEYS.size(); i++) {
			KeyMapping key = KEYS.get(i);
			int x = this.column(i % 2);
			int y = this.row(0) + i / 2 * 12;
			graphics.text(this.font, Component.translatable(key.getName()), x, y, 0xFFD0D0D8);
			Component bound = key.getTranslatedKeyMessage();
			graphics.text(this.font, bound, x + COLUMN_WIDTH - this.font.width(bound) - 4, y, 0xFFFFD060);
		}

		this.note(graphics, Component.translatable("sabercraft.menu.combat.note"), 2);
	}
}
