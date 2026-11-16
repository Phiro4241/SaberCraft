package com.phiro.sabercraft.client.story;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import com.phiro.sabercraft.client.interior.ArrivalFilm;
import com.phiro.sabercraft.network.StoryActionPayload;

/**
 * Phase 20: the story's films. The server starts one with a cue (for now only the Falcon's arrival
 * at the Tenebris, see {@link ArrivalFilm}) and waits for it; this client then shows it: the camera
 * leaves the player and follows the film's shots, the world's own drawing steps aside (the film is
 * drawn far from every ship's plot), black bars above and below, the lines of the crew as subtitles,
 * its sounds and music. Space or Escape skip it (the server is told, so the story goes on at once).
 *
 * <p>The player stays where they were meanwhile: a film moves only this client's camera.
 */
public final class StoryCinema {
	/** The film's lines: from, to (ticks), who speaks, the key of the words. */
	private record Line(int from, int to, String speaker, String key) {
	}

	private static final Line[] ARRIVAL_LINES = {
			new Line(150, 232, "dax", "sabercraft.story.film.arrival.dax1"),
			new Line(240, 300, "mira", "sabercraft.story.film.arrival.mira"),
			new Line(304, 350, "dax", "sabercraft.story.film.arrival.dax2"),
			new Line(400, 452, "dax", "sabercraft.story.film.arrival.dax3")};
	/** When the title shows (ticks). */
	private static final int TITLE_FROM = 128;
	private static final int TITLE_TO = 214;
	/** Skipping is possible after this long (a key still held from the dialog does not skip). */
	private static final int SKIP_AFTER = 20;

	private static @Nullable String film;
	private static int length;
	private static int age;
	/** Where the scene's origin is in the level (far from every plot). */
	private static Vec3 anchor = Vec3.ZERO;
	private static boolean hudWasHidden;
	private static int played;
	private static int skipped;
	private static @Nullable String lastSound;

	private StoryCinema() {
	}

	static void init() {
		LevelRenderEvents.COLLECT_SUBMITS.register(context -> {
			if (film != null) {
				Vec3 camera = context.levelState().cameraRenderState.pos;
				ArrivalFilm.submit(context.poseStack(), context.submitNodeCollector(), anchor.subtract(camera), time(partial()));
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(StoryCinema::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> stop(client, false));
	}

	/** The server starts a film. */
	static void start(Minecraft client, String name, int ticks) {
		if (client.player == null || !"arrival".equals(name)) {
			return;
		}

		film = name;
		length = Math.max(20, Math.min(ticks, ArrivalFilm.LENGTH));
		age = 0;
		played++;
		// Half way between the plots, which lie 1024 blocks apart: nothing else is near.
		double x = Math.floor(client.player.getX() / 1024.0) * 1024.0 + 512.0;
		double z = Math.floor(client.player.getZ() / 1024.0) * 1024.0 + 512.0;
		anchor = new Vec3(x, 140.0, z);
		hudWasHidden = client.gui.hud.isHidden();

		if (!hudWasHidden) {
			client.gui.hud.toggle();
		}

		client.gui.setScreen(new FilmScreen());
		sound(client, 0);
	}

	/** The film ends (played through, skipped, or the player left). */
	static void stop(Minecraft client, boolean skip) {
		if (film == null) {
			return;
		}

		if (skip) {
			skipped++;
			StoryClient.send(StoryActionPayload.SKIP, film, 0);
		}

		film = null;

		if (client.gui.hud.isHidden() != hudWasHidden) {
			client.gui.hud.toggle();
		}

		if (client.gui.screen() instanceof FilmScreen) {
			client.gui.setScreen(null);
		}
	}

	private static void tick(Minecraft client) {
		if (film == null) {
			return;
		}

		if (client.player == null || client.level == null) {
			stop(client, false);
			return;
		}

		age++;
		sound(client, age);

		if (age >= length) {
			stop(client, false);
		} else if (!(client.gui.screen() instanceof FilmScreen) && client.gui.screen() == null) {
			// Something closed it (a dialog cannot open meanwhile): back to the film.
			client.gui.setScreen(new FilmScreen());
		}
	}

	/** The film's sounds, each at its moment. */
	private static void sound(Minecraft client, int tick) {
		switch (tick) {
			case 0 -> play(client, "ship.hyperspace_arrive", 1.0F, 1.0F);
			case 4 -> play(client, "ship.flyby", 0.9F, 0.85F);
			case 118 -> play(client, "ship.star_destroyer.engine", 0.8F, 0.7F);
			case 150 -> play(client, "ship.flyby_heavy", 0.35F, 0.6F);
			case 244 -> play(client, "ship.falcon.brake", 0.7F, 1.0F);
			case 282 -> play(client, "ship.falcon.thrust", 0.5F, 0.9F);
			case 330 -> play(client, "ship.falcon.brake", 0.55F, 0.9F);
			case 360 -> play(client, "story.door_lock", 0.5F, 0.6F);
			case 390 -> {
				play(client, "story.door_lock", 0.9F, 1.0F);
				play(client, "ship.land", 0.6F, 1.2F);
			}
			case 398 -> play(client, "story.blip", 0.4F, 1.4F);
			case 412 -> play(client, "ship.power_down", 0.45F, 1.0F);
			default -> {
			}
		}
	}

	private static void play(Minecraft client, String name, float volume, float pitch) {
		lastSound = name;
		StoryClient.sound(client, name, volume, pitch, null);
	}

	private static float partial() {
		return Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
	}

	private static float time(float partial) {
		return Math.min(length, age + partial);
	}

	/** Whether a film runs now. */
	public static boolean active() {
		return film != null;
	}

	/** The camera of the film now (in the level), or null without a film. */
	public static ArrivalFilm.@Nullable Shot camera(float partial) {
		if (film == null) {
			return null;
		}

		ArrivalFilm.Shot shot = ArrivalFilm.camera(time(partial));
		return new ArrivalFilm.Shot(anchor.add(shot.position()), shot.yaw(), shot.pitch());
	}

	/** The film's music. */
	public static String music() {
		return "music.flight_imperial";
	}

	/** Only for tests: how many films started and how many were skipped, how far the film is, its shot. */
	public static int playedForTest() {
		return played;
	}

	public static int skippedForTest() {
		return skipped;
	}

	public static int ageForTest() {
		return film != null ? age : -1;
	}

	public static @Nullable String lastSoundForTest() {
		return lastSound;
	}

	/** Only for tests: starts a film without the server (for its pictures). */
	public static void startForTest(String name) {
		start(Minecraft.getInstance(), name, ArrivalFilm.LENGTH);
	}

	/** Only for tests: jumps to a moment of the film. */
	public static void seekForTest(int tick) {
		age = Math.max(0, Math.min(length - 1, tick));
	}

	/** Only for tests: skips the film as the player would. */
	public static void skipForTest() {
		stop(Minecraft.getInstance(), true);
	}

	/** The film's screen: bars, subtitles, the title, the hint to skip; keys skip. */
	private static final class FilmScreen extends Screen {
		FilmScreen() {
			super(Component.empty());
		}

		@Override
		public boolean keyPressed(KeyEvent event) {
			if (age >= SKIP_AFTER && (event.isSelection() || event.isEscape() || event.isConfirmation())) {
				stop(Minecraft.getInstance(), true);
			}

			return true;
		}

		@Override
		public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
			return true;
		}

		@Override
		public boolean shouldCloseOnEsc() {
			return false;
		}

		@Override
		public boolean isPauseScreen() {
			return false;
		}

		@Override
		public boolean isInGameUi() {
			return true;
		}

		@Override
		public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		}

		@Override
		public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
			float t = time(a);
			int bar = Math.max(18, this.height / 9);
			// Out of hyperspace in a white flash, into black at the end; the bars all the time.
			int flash = t < 8.0F ? Math.round((1.0F - t / 8.0F) * 230.0F) : 0;

			if (flash > 0) {
				graphics.fill(0, 0, this.width, this.height, flash << 24 | 0xF2F6FF);
			}

			int black = t > length - 20 ? Math.round(Mth.clamp((t - (length - 20)) / 20.0F, 0.0F, 1.0F) * 255.0F) : 0;

			if (black > 0) {
				graphics.fill(0, 0, this.width, this.height, black << 24);
			}

			graphics.fill(0, 0, this.width, bar, 0xFF000000);
			graphics.fill(0, this.height - bar, this.width, this.height, 0xFF000000);

			// The title over the hull.
			if (t >= TITLE_FROM && t < TITLE_TO) {
				float in = Mth.clamp(Math.min(t - TITLE_FROM, TITLE_TO - t) / 14.0F, 0.0F, 1.0F);
				int alpha = Math.max(4, Math.round(in * 255.0F));
				Component title = Component.translatable("sabercraft.story.film.arrival.title");
				Component under = Component.translatable("sabercraft.story.film.arrival.subtitle");
				var pose = graphics.pose();
				pose.pushMatrix();
				pose.translate(this.width / 2.0F, bar + 16.0F);
				pose.scale(2.4F, 2.4F);
				graphics.centeredText(this.font, title, 0, 0, alpha << 24 | 0xE8ECF2);
				pose.popMatrix();
				graphics.centeredText(this.font, under, this.width / 2, bar + 42, alpha << 24 | 0x9AA6B8);
			}

			// The crew's lines, in the lower bar.
			for (Line line : ARRIVAL_LINES) {
				if (t >= line.from() && t < line.to()) {
					float in = Mth.clamp(Math.min(t - line.from(), line.to() - t) / 6.0F, 0.0F, 1.0F);
					int alpha = Math.max(4, Math.round(in * 255.0F));
					Component name = Component.translatable("sabercraft.story.name." + line.speaker());
					Component words = Component.translatable(line.key());
					int y = this.height - bar + Math.max(2, (bar - 20) / 2);
					graphics.centeredText(this.font, name, this.width / 2, y, alpha << 24 | ("mira".equals(line.speaker()) ? 0x8CC0FF : 0xE8C878));
					graphics.centeredText(this.font, words, this.width / 2, y + 11, alpha << 24 | 0xECEFF4);
				}
			}

			// How to skip, for the first seconds.
			if (t >= SKIP_AFTER && t < 140.0F) {
				int alpha = Math.round(Mth.clamp(Math.min(t - SKIP_AFTER, 140.0F - t) / 10.0F, 0.0F, 1.0F) * 150.0F);
				Component skip = Component.translatable("sabercraft.story.film.skip");
				graphics.text(this.font, skip, this.width - this.font.width(skip) - 8, bar / 2 - 4, Math.max(4, alpha) << 24 | 0xB8C0CC);
			}
		}
	}
}
