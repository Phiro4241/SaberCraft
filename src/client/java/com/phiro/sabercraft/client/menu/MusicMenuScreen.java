package com.phiro.sabercraft.client.menu;

import java.util.List;
import java.util.Optional;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

import com.phiro.sabercraft.client.ImmersionConfig;
import com.phiro.sabercraft.client.music.SaberMusic;
import com.phiro.sabercraft.client.music.StarWarsMusic;
import com.phiro.sabercraft.client.music.UserMusic;

/**
 * The music, only on this client. Since Phase 8 there are two modes with separate settings: the
 * SaberCraft music (on or off, its own volume, crossfade length, your own files, all as before),
 * and the Star Wars mode, which plays music files you provide, with its own volume, crossfade,
 * switches for fights and exploration, and a list of the tracks found and the files missing.
 */
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
		this.cycle(0, 0, "sabercraft.option.music_mode", List.of(ImmersionConfig.MODE_SABERCRAFT, ImmersionConfig.MODE_STARWARS), config.musicMode,
				v -> Component.translatable("sabercraft.music.mode." + v), v -> {
					config.musicMode = v;
					ImmersionConfig.save();
					this.rebuildWidgets();
				});
		this.toggle(1, 0, "sabercraft.option.music", config.music, v -> {
			config.music = v;
			ImmersionConfig.save();
		});

		if (config.starWarsMode()) {
			this.initStarWars(config);
		} else {
			this.initSaberCraft(config);
		}

		this.closeButton();
	}

	private void initSaberCraft(ImmersionConfig config) {
		this.slider(0, 1, "sabercraft.option.music_volume", 0.0F, 1.0F, config.musicVolume, SaberMenuScreen::percent, v -> {
			config.musicVolume = v;
			ImmersionConfig.save();
		});
		this.slider(1, 1, "sabercraft.option.music_crossfade", 0.5F, 10.0F, config.musicCrossfadeSeconds,
				v -> String.format(java.util.Locale.ROOT, "%.1f s", v), v -> {
					config.musicCrossfadeSeconds = v;
					ImmersionConfig.save();
				});
		this.toggle(0, 2, "sabercraft.option.music_user_files", config.musicUserFiles, v -> {
			config.musicUserFiles = v;
			ImmersionConfig.save();
		});
		this.button(1, 2, Component.translatable("sabercraft.menu.music.folder"), () -> {
			UserMusic.scan();
			Util.getPlatform().openPath(UserMusic.folder());
			return true;
		});
	}

	private void initStarWars(ImmersionConfig config) {
		this.slider(0, 1, "sabercraft.option.starwars_volume", 0.0F, 1.0F, config.starWarsVolume, SaberMenuScreen::percent, v -> {
			config.starWarsVolume = v;
			ImmersionConfig.save();
		});
		this.toggle(1, 1, "sabercraft.option.starwars_crossfade", config.starWarsCrossfade, v -> {
			config.starWarsCrossfade = v;
			ImmersionConfig.save();
		});
		this.toggle(0, 2, "sabercraft.option.starwars_combat", config.starWarsCombat, v -> {
			config.starWarsCombat = v;
			ImmersionConfig.save();
		});
		this.toggle(1, 2, "sabercraft.option.starwars_exploration", config.starWarsExploration, v -> {
			config.starWarsExploration = v;
			ImmersionConfig.save();
		});
		this.toggle(0, 3, "sabercraft.option.starwars_character_themes", config.starWarsCharacterThemes, v -> {
			config.starWarsCharacterThemes = v;
			ImmersionConfig.save();
		});
		int half = (COLUMN_WIDTH - 4) / 2;
		this.button(1, 3, Component.translatable("sabercraft.menu.music.starwars.folder"), () -> {
			StarWarsMusic.scan();
			Util.getPlatform().openPath(StarWarsMusic.folder());
			return true;
		}).setWidth(half);
		this.addRenderableWidget(net.minecraft.client.gui.components.Button.builder(Component.translatable("sabercraft.menu.music.starwars.reload"), b -> {
					StarWarsMusic.scan();
					this.rebuildWidgets();
				})
				.bounds(this.column(1) + half + 4, this.row(3), half, 20)
				.tooltip(Tooltip.create(Component.translatable("sabercraft.menu.music.starwars.reload.description")))
				.build());
	}

	@Override
	protected void extractPanel(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		if (ImmersionConfig.get().starWarsMode()) {
			this.extractStarWars(graphics);
			return;
		}

		Identifier piece = SaberMusic.currentPiece();
		SaberMusic.Mood mood = SaberMusic.mood();
		Component now = piece == null || mood == null
				? Component.translatable("sabercraft.menu.music.silent")
				: Component.translatable("sabercraft.menu.music.now", Component.translatable("sabercraft.music.mood." + mood.folder()),
						piece.getPath().startsWith("user_music/") ? Component.translatable("sabercraft.music.own")
								: Component.translatable("sabercraft.music.piece." + piece.getPath().substring("music.".length())));
		graphics.text(this.font, now, this.left + 10, this.row(3) + 6, 0xFFFFD060);
		this.note(graphics, Component.translatable("sabercraft.menu.music.note"), 4);
	}

	/** What plays now, how many tracks each situation has (or that they are missing), and problems. */
	private void extractStarWars(GuiGraphicsExtractor graphics) {
		int y = this.row(4) + 2;
		Optional<StarWarsMusic.Track> track = StarWarsMusic.currentTrack();
		StarWarsMusic.Situation situation = StarWarsMusic.situation();
		Component now = track.isEmpty() || situation == null
				? Component.translatable(StarWarsMusic.total() == 0 ? "sabercraft.menu.music.starwars.empty" : "sabercraft.menu.music.silent")
				: Component.translatable("sabercraft.menu.music.now", Component.translatable("sabercraft.music.situation." + situation.folder()),
						Component.literal(track.get().name()));
		graphics.text(this.font, now, this.left + 10, y, StarWarsMusic.total() == 0 ? 0xFFFF7060 : 0xFFFFD060);
		y += 11;
		int index = 0;

		for (StarWarsMusic.Situation each : StarWarsMusic.Situation.values()) {
			List<StarWarsMusic.Track> tracks = StarWarsMusic.tracks(each);
			Component name = Component.translatable("sabercraft.music.situation." + each.folder());
			Component line = tracks.isEmpty()
					? Component.translatable("sabercraft.menu.music.starwars.missing", name, each.folder() + "/")
					: Component.translatable("sabercraft.menu.music.starwars.count", name, tracks.size(), tracks.getFirst().name());
			int x = this.left + 10 + index % 2 * (PANEL_WIDTH / 2);
			graphics.text(this.font, this.font.substrByWidth(line, PANEL_WIDTH / 2 - 14).getString(), x, y + index / 2 * 10,
					tracks.isEmpty() ? 0xFFB07070 : 0xFF90D090);
			index++;
		}

		graphics.textWithWordWrap(this.font, Component.translatable("sabercraft.menu.music.starwars.note"), this.left + 10, this.top + 178,
				PANEL_WIDTH - 20, 0xFFA8A8B0);
		y += 30;
		List<String> problems = StarWarsMusic.problems();

		if (!problems.isEmpty()) {
			String text = String.join(", ", problems);
			graphics.text(this.font, this.font.substrByWidth(Component.translatable("sabercraft.menu.music.starwars.problems", text), PANEL_WIDTH - 20)
					.getString(), this.left + 10, y, 0xFFFF9060);
		}
	}
}
