package com.phiro.sabercraft.client.menu;

import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import com.phiro.sabercraft.client.ImmersionConfig;
import com.phiro.sabercraft.client.sky.PlanetPreset;

/**
 * The sky: the space sky (on or off, its detail, the End, shader packs) and since Phase 8 the Star
 * Wars planet the Overworld sky turns into; since Phase 9 also the horizon landscape and the planet
 * tint for shader packs. Only on this client.
 */
public class SpaceMenuScreen extends SaberMenuScreen {
	public SpaceMenuScreen() {
		super(Component.translatable("sabercraft.menu.space.title"));
	}

	@Override
	protected Tab tab() {
		return Tab.SPACE;
	}

	@Override
	protected void initPanel() {
		ImmersionConfig config = ImmersionConfig.get();
		this.toggle(0, 0, "sabercraft.option.space_sky", config.spaceSky, v -> {
			config.spaceSky = v;
			ImmersionConfig.save();
		});
		this.cycle(1, 0, "sabercraft.option.space_sky_quality", List.of(0, 1, 2), config.spaceSkyQuality,
				v -> Component.translatable("sabercraft.option.quality." + v), v -> {
					config.spaceSkyQuality = v;
					ImmersionConfig.save();
				});
		this.toggle(0, 1, "sabercraft.option.space_sky_in_end", config.spaceSkyInEnd, v -> {
			config.spaceSkyInEnd = v;
			ImmersionConfig.save();
		});
		this.toggle(1, 1, "sabercraft.option.space_sky_with_shaders", config.spaceSkyWithShaders, v -> {
			config.spaceSkyWithShaders = v;
			ImmersionConfig.save();
		});
		List<String> planets = new java.util.ArrayList<>();
		planets.add("");
		PlanetPreset.ALL.forEach(planet -> planets.add(planet.id()));
		this.cycle(0, 2, "sabercraft.option.planet", planets, planets.contains(config.planet) ? config.planet : "",
				v -> Component.translatable(v.isEmpty() ? "sabercraft.planet.none" : "sabercraft.planet." + v), v -> {
					config.planet = v;
					ImmersionConfig.save();
				});
		this.toggle(1, 2, "sabercraft.option.planet_particles", config.planetParticles, v -> {
			config.planetParticles = v;
			ImmersionConfig.save();
		});
		this.toggle(0, 3, "sabercraft.option.planet_horizon", config.planetHorizon, v -> {
			config.planetHorizon = v;
			ImmersionConfig.save();
		});
		this.toggle(1, 3, "sabercraft.option.planet_shader_tint", config.planetShaderTint, v -> {
			config.planetShaderTint = v;
			ImmersionConfig.save();
		});
		this.closeButton();
	}

	@Override
	protected void extractPanel(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		String planet = ImmersionConfig.get().planet;
		Component text = PlanetPreset.byId(planet).<Component>map(p -> Component.translatable("sabercraft.planet." + p.id() + ".description"))
				.orElse(Component.translatable("sabercraft.planet.none.description"));
		this.note(graphics, text, 4);
		this.note(graphics, Component.translatable("sabercraft.menu.space.note"), 6);
	}
}
