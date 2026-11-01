package com.phiro.sabercraft.client.menu;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import com.phiro.sabercraft.client.ImmersionConfig;

/**
 * Phase 12: the volumes of SaberCraft's own sounds, each on top of Minecraft's sliders: the
 * lightsabers (and on top of that their steady hum), the droids, and the starships. The music and
 * the planet sounds keep their sliders in their own areas. Every change is heard at once (see
 * {@link com.phiro.sabercraft.client.sound.SoundVolumes}) and saved right away.
 */
public class SoundMenuScreen extends SaberMenuScreen {
	public SoundMenuScreen() {
		super(Component.translatable("sabercraft.menu.sound.title"));
	}

	@Override
	protected Tab tab() {
		return Tab.SOUND;
	}

	@Override
	protected void initPanel() {
		ImmersionConfig config = ImmersionConfig.get();
		this.slider(0, 0, "sabercraft.option.saber_volume", 0.0F, 1.0F, config.saberVolume, SaberMenuScreen::percent, v -> {
			config.saberVolume = v;
			ImmersionConfig.save();
		});
		this.slider(1, 0, "sabercraft.option.saber_hum_volume", 0.0F, 1.0F, config.saberHumVolume, SaberMenuScreen::percent, v -> {
			config.saberHumVolume = v;
			ImmersionConfig.save();
		});
		this.toggle(0, 1, "sabercraft.option.saber_hum_idle_quiet", config.saberHumIdleQuiet, v -> {
			config.saberHumIdleQuiet = v;
			ImmersionConfig.save();
		});
		this.slider(1, 1, "sabercraft.option.droid_volume", 0.0F, 1.0F, config.droidVolume, SaberMenuScreen::percent, v -> {
			config.droidVolume = v;
			ImmersionConfig.save();
		});
		this.toggle(0, 2, "sabercraft.option.droid_chatter", config.droidChatter, v -> {
			config.droidChatter = v;
			ImmersionConfig.save();
		});
		this.slider(1, 2, "sabercraft.option.ship_volume", 0.0F, 1.0F, config.shipVolume, SaberMenuScreen::percent, v -> {
			config.shipVolume = v;
			ImmersionConfig.save();
		});
		this.closeButton();
	}

	@Override
	protected void extractPanel(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		this.note(graphics, Component.translatable("sabercraft.menu.sound.note"), 3);
	}
}
