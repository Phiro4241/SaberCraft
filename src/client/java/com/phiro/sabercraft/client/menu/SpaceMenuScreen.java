package com.phiro.sabercraft.client.menu;

import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import com.phiro.sabercraft.client.ImmersionConfig;

/** The space sky: on or off, its detail, the End, and shader packs. Only on this client. */
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
		this.closeButton();
	}

	@Override
	protected void extractPanel(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		this.note(graphics, Component.translatable("sabercraft.menu.space.note"), 3);
	}
}
