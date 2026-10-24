package com.phiro.sabercraft.client.menu;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

import com.phiro.sabercraft.client.ImmersionConfig;
import com.phiro.sabercraft.client.music.SaberMusic;
import com.phiro.sabercraft.client.music.UserMusic;

/** The music: on or off, its own volume, crossfade length, your own files. Only on this client. */
public class MusicMenuScreen extends SaberMenuScreen {
	public MusicMenuScreen() {
		super(Component.translatable("sabercraft.menu.music.title"));
	}

	@Override
	protected Tab tab() {
		return Tab.MUSIC;
	}

	@Override
	protected void initPanel() {
		ImmersionConfig config = ImmersionConfig.get();
		this.toggle(0, 0, "sabercraft.option.music", config.music, v -> {
			config.music = v;
			ImmersionConfig.save();
		});
		this.slider(1, 0, "sabercraft.option.music_volume", 0.0F, 1.0F, config.musicVolume, SaberMenuScreen::percent, v -> {
			config.musicVolume = v;
			ImmersionConfig.save();
		});
		this.slider(0, 1, "sabercraft.option.music_crossfade", 0.5F, 10.0F, config.musicCrossfadeSeconds,
				v -> String.format(java.util.Locale.ROOT, "%.1f s", v), v -> {
					config.musicCrossfadeSeconds = v;
					ImmersionConfig.save();
				});
		this.toggle(1, 1, "sabercraft.option.music_user_files", config.musicUserFiles, v -> {
			config.musicUserFiles = v;
			ImmersionConfig.save();
		});
		this.button(0, 2, Component.translatable("sabercraft.menu.music.folder"), () -> {
			UserMusic.scan();
			Util.getPlatform().openPath(UserMusic.folder());
			return true;
		});
		this.closeButton();
	}

	@Override
	protected void extractPanel(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		Identifier piece = SaberMusic.currentPiece();
		SaberMusic.Mood mood = SaberMusic.mood();
		Component now = piece == null || mood == null
				? Component.translatable("sabercraft.menu.music.silent")
				: Component.translatable("sabercraft.menu.music.now", Component.translatable("sabercraft.music.mood." + mood.folder()),
						piece.getPath().startsWith("user_music/") ? Component.translatable("sabercraft.music.own")
								: Component.translatable("sabercraft.music.piece." + piece.getPath().substring("music.".length())));
		graphics.text(this.font, now, this.column(1), this.row(2) + 6, 0xFFFFD060);
		this.note(graphics, Component.translatable("sabercraft.menu.music.note"), 3);
	}
}
