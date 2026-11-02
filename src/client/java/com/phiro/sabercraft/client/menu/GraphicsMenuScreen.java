package com.phiro.sabercraft.client.menu;

import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import com.phiro.sabercraft.client.ImmersionConfig;
import com.phiro.sabercraft.client.combat.CombatConfig;

/** Graphics and effects: light trails, sparks and particles, camera motion, sky detail, blaster and starship view. */
public class GraphicsMenuScreen extends SaberMenuScreen {
	public GraphicsMenuScreen() {
		super(Component.translatable("sabercraft.menu.graphics.title"));
	}

	@Override
	protected Tab tab() {
		return Tab.GRAPHICS;
	}

	@Override
	protected void initPanel() {
		CombatConfig combat = CombatConfig.get();
		this.toggle(0, 0, "sabercraft.option.blade_trails", combat.bladeTrails, v -> {
			combat.bladeTrails = v;
			CombatConfig.save();
		});
		this.slider(1, 0, "sabercraft.option.trail_strength", 0.0F, 2.0F, combat.trailStrength, SaberMenuScreen::percent, v -> {
			combat.trailStrength = v;
			CombatConfig.save();
		});
		this.cycle(0, 1, "sabercraft.option.particles", List.of(0, 1, 2), combat.particles,
				v -> Component.translatable("sabercraft.option.particles." + v), v -> {
					combat.particles = v;
					CombatConfig.save();
				});
		this.toggle(1, 1, "sabercraft.option.hide_vanilla_sweep", combat.hideVanillaSweep, v -> {
			combat.hideVanillaSweep = v;
			CombatConfig.save();
		});
		this.slider(0, 2, "sabercraft.option.camera_motion", 0.0F, 1.0F, combat.cameraMotion, SaberMenuScreen::percent, v -> {
			combat.cameraMotion = v;
			CombatConfig.save();
		});
		ImmersionConfig immersion = ImmersionConfig.get();
		this.cycle(1, 2, "sabercraft.option.space_sky_quality", List.of(0, 1, 2, 3), immersion.spaceSkyQuality,
				v -> Component.translatable("sabercraft.option.quality." + v), v -> {
					immersion.spaceSkyQuality = v;
					ImmersionConfig.save();
				});
		this.toggle(0, 3, "sabercraft.option.shader_blade", combat.shaderBlade, v -> {
			combat.shaderBlade = v;
			CombatConfig.save();
		});
		// Phase 11: drawing blasters, menu transitions and the starship view.
		this.toggle(1, 3, "sabercraft.option.blaster_draw", immersion.blasterDraw, v -> {
			immersion.blasterDraw = v;
			ImmersionConfig.save();
		});
		this.toggle(0, 4, "sabercraft.option.menu_transitions", immersion.menuTransitions, v -> {
			immersion.menuTransitions = v;
			ImmersionConfig.save();
		});
		// Phase 14: the cockpit view, the view behind the ship, or no change.
		this.cycle(1, 4, "sabercraft.option.ship_camera",
				List.of(ImmersionConfig.CAMERA_COCKPIT, ImmersionConfig.CAMERA_CHASE, ImmersionConfig.CAMERA_KEEP), immersion.shipCamera,
				v -> Component.translatable("sabercraft.option.ship_camera." + v), v -> {
					immersion.shipCamera = v;
					immersion.shipThirdPerson = !ImmersionConfig.CAMERA_KEEP.equals(v);
					ImmersionConfig.save();
				});
		this.toggle(0, 5, "sabercraft.option.ship_hud", immersion.shipHud, v -> {
			immersion.shipHud = v;
			ImmersionConfig.save();
		});
		// Phase 12: the hint while looking at a droid.
		this.toggle(1, 5, "sabercraft.option.droid_target_hud", immersion.droidTargetHud, v -> {
			immersion.droidTargetHud = v;
			ImmersionConfig.save();
		});
		// Phase 13: pilots hidden in ships, characters drawn without their armor.
		this.toggle(0, 6, "sabercraft.option.ship_hide_pilot", immersion.shipHidePilot, v -> {
			immersion.shipHidePilot = v;
			ImmersionConfig.save();
		});
		this.toggle(1, 6, "sabercraft.option.character_hide_armor", immersion.characterHideArmor, v -> {
			immersion.characterHideArmor = v;
			ImmersionConfig.save();
		});
		this.closeButton();
	}

	@Override
	protected void extractPanel(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		// Phase 13: row 6 holds options now; the hint stays as the tooltip of the shader option.
	}
}
