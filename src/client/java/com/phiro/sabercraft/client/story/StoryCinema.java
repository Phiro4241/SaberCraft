package com.phiro.sabercraft.client.story;

import java.util.List;
import java.util.Set;

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
import com.phiro.sabercraft.client.interior.EscapeFilm;
import com.phiro.sabercraft.network.StoryActionPayload;

/**
 * Phase 20: the story's films. The server starts one with a cue (for now only the Falcon's arrival
 * at the Tenebris, see {@link ArrivalFilm}) and waits for it; this client then shows it: the camera
 * leaves the player and follows the film's shots, the world's own drawing steps aside (the film is
 * drawn far from every ship's plot), black bars above and below, the lines of the crew as subtitles,
 * its sounds and music. Space or Escape skip it (the server is told, so the story goes on at once).
 *
 * <p>The player stays where they were meanwhile: a film moves only this client's camera.
 *
 * <p>Phase 21: films shot where the story is ({@link DuelFilm}: before the duel, his flight). The
 * world is drawn as always then (and the player too, seen from outside).
 *
 * <p>Phase 24: more films: the guard overpowered in the detention ({@code takedown}); the blade
 * lock at half her strength ({@code boss_mid}: the camera leaves the player's eyes, circles the
 * two, she ignites the second blade of her staff, and comes back to the player's eyes); her flight
 * now in a TIE fighter (drawn out, with a look from outside as it leaves the ship); and the end of
 * the Tenebris from outside ({@code escape}, {@link EscapeFilm}).
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
	private static final Line[] DUEL_LINES = {
			new Line(22, 100, "shadow", "sabercraft.story.d.m6_shadow.start"),
			new Line(104, 172, "shadow", "sabercraft.story.d.m6_shadow.threat")};
	private static final Line[] DUEL_END_LINES = {
			new Line(24, 92, "shadow", "sabercraft.story.radio.shadow_retreat"),
			new Line(318, 344, "dax", "sabercraft.story.film.duel_end.dax")};
	private static final Line[] BOSS_LINES = {
			new Line(22, 80, "shadow", "sabercraft.story.shout.shadow_lock"),
			new Line(106, 146, "shadow", "sabercraft.story.shout.shadow_phase2")};
	private static final Line[] ESCAPE_LINES = {
			new Line(18, 80, "dax", "sabercraft.story.film.escape.dax1"),
			new Line(112, 176, "kestrel", "sabercraft.story.film.escape.kestrel"),
			new Line(286, 326, "mira", "sabercraft.story.film.escape.mira"),
			new Line(332, 362, "dax", "sabercraft.story.film.escape.dax2")};
	private static final Line[] NO_LINES = {};
	/** Phase 24: the films there are. */
	private static final Set<String> FILMS = Set.of("arrival", "duel", "duel_end", "takedown", "boss_mid", "escape");
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
	/** Phase 21: a film where the story is: the line from the player to him when it began, and where he stood. */
	private static @Nullable Vec3 axis;
	private static Vec3 shadowStart = Vec3.ZERO;
	/** Phase 24: the film's point from the server (the guard, her TIE), or null. */
	private static @Nullable Vec3 focus;

	private StoryCinema() {
	}

	static void init() {
		LevelRenderEvents.COLLECT_SUBMITS.register(context -> {
			if (film != null && elsewhere()) {
				Vec3 camera = context.levelState().cameraRenderState.pos;
				float t = time(partial());

				switch (film) {
					case "escape" -> EscapeFilm.submit(context.poseStack(), context.submitNodeCollector(), anchor.subtract(camera), t);
					case "duel_end" -> EscapeFilm.submitTie(context.poseStack(), context.submitNodeCollector(), anchor.subtract(camera), t - DuelFilm.TIE_OUT);
					default -> ArrivalFilm.submit(context.poseStack(), context.submitNodeCollector(), anchor.subtract(camera), t);
				}
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(StoryCinema::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> stop(client, false));
	}

	/** The server starts a film. */
	static void start(Minecraft client, String name, int ticks, List<Vec3> points) {
		if (client.player == null || !FILMS.contains(name)) {
			return;
		}

		film = name;
		int most = switch (name) {
			case "duel" -> DuelFilm.LENGTH;
			case "duel_end" -> DuelFilm.END_LENGTH;
			case "takedown" -> DuelFilm.TAKEDOWN_LENGTH;
			case "boss_mid" -> DuelFilm.BOSS_LENGTH;
			case "escape" -> EscapeFilm.LENGTH;
			default -> ArrivalFilm.LENGTH;
		};
		length = Math.max(20, Math.min(ticks, most));
		focus = points.isEmpty() ? null : points.getFirst();
		axis = null;
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
		if ("duel".equals(film)) {
			switch (tick) {
				case 0 -> play(client, "story.alarm", 0.25F, 0.8F);
				case 70 -> play(client, "story.door_lock", 0.4F, 0.6F);
				case 176 -> play(client, "saber.swing", 0.6F, 0.7F);
				default -> {
				}
			}

			return;
		}

		if ("duel_end".equals(film)) {
			// Phase 23: the clash and the saber's landing are heard in the world (from the server).
			// Phase 24: her TIE: the hatch, the engines, out through the field; the look from outside.
			switch (tick) {
				case 182 -> play(client, "story.door_lock", 0.7F, 1.3F);
				case 186 -> play(client, "ship.tie_fighter.engine", 0.7F, 1.0F);
				case 204 -> play(client, "ship.tie_fighter.thrust", 0.8F, 1.0F);
				case 238 -> play(client, "ship.tie_fighter.boost", 0.9F, 1.0F);
				case DuelFilm.TIE_OUT + 4 -> play(client, "ship.flyby", 0.8F, 1.1F);
				case DuelFilm.TIE_OUT + 22 -> play(client, "ship.tie_fighter.boost", 0.7F, 1.05F);
				case DuelFilm.TIE_OUT + 30 -> play(client, "ship.small_blast", 0.45F, 0.8F);
				case DuelFilm.TIE_BACK + 2 -> play(client, "story.blip", 0.5F, 1.3F);
				default -> {
				}
			}

			return;
		}

		if ("escape".equals(film)) {
			switch (tick) {
				case 0 -> play(client, "ship.falcon.boost", 0.9F, 1.0F);
				case 14 -> play(client, "ship.falcon.thrust", 0.9F, 0.9F);
				case 16, 46, 80 -> play(client, "ship.small_blast", 0.55F, 0.9F);
				case 30, 62, 94 -> play(client, "ship.explode", 0.45F, 0.85F);
				case 100 -> play(client, "ship.flyby", 0.8F, 0.9F);
				case 130, 170, 214 -> play(client, "ship.explode", 0.6F, 0.75F);
				case 150, 190, 240, 250 -> play(client, "ship.explode_big", 0.55F, 0.8F);
				case EscapeFilm.CORE -> {
					play(client, "ship.explode_big", 1.0F, 0.55F);
					play(client, "ship.explode", 1.0F, 0.5F);
				}
				case EscapeFilm.CORE + 6 -> play(client, "ship.explode_big", 0.9F, 0.45F);
				case 330 -> play(client, "ship.hyperspace_charge", 0.9F, 1.0F);
				case EscapeFilm.JUMP -> play(client, "ship.hyperspace_jump", 1.0F, 1.0F);
				case 374 -> play(client, "ship.flyby_heavy", 0.8F, 0.6F);
				default -> {
				}
			}

			return;
		}

		if (!"arrival".equals(film)) {
			// (The takedown's and the blade lock's sounds come from the world.)
			return;
		}

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

	/**
	 * Phase 21: whether the film runs far from the story (the arrival): the ships' own drawing steps
	 * aside then. Phase 24: also the end of the Tenebris, and the look from outside in her flight.
	 */
	public static boolean elsewhere() {
		if (film == null) {
			return false;
		}

		return switch (film) {
			case "arrival", "escape" -> true;
			case "duel_end" -> age >= DuelFilm.TIE_OUT && age < DuelFilm.TIE_BACK;
			default -> false;
		};
	}

	/** Phase 24: how far that film is (ticks, with the frame's part), or -1 when it does not run. */
	public static float time(String name) {
		return name.equals(film) ? time(partial()) : -1.0F;
	}

	/** Phase 21: whether the film runs where the story is (the camera sees the player from outside). */
	public static boolean inWorld() {
		return film != null && !elsewhere();
	}

	/** The camera of the film now (in the level), or null without a film. */
	public static ArrivalFilm.@Nullable Shot camera(float partial) {
		if (film == null) {
			return null;
		}

		if (!elsewhere()) {
			Minecraft client = Minecraft.getInstance();

			if (client.player == null) {
				return null;
			}

			Vec3 player = client.player.getPosition(partial);

			if ("takedown".equals(film)) {
				Vec3 guard = focus != null ? new Vec3(focus.x, player.y, focus.z) : player.add(Vec3.directionFromRotation(0.0F, client.player.getYRot()));

				if (axis == null) {
					Vec3 to = guard.subtract(player);
					to = new Vec3(to.x, 0.0, to.z);
					axis = to.lengthSqr() > 1.0E-4 ? to.normalize() : new Vec3(0.0, 0.0, -1.0);
				}

				return DuelFilm.takedown(time(partial), player, guard, axis);
			}

			Vec3 shadow = StoryClient.saberFigure(partial);

			if (axis == null) {
				Vec3 to = shadow != null ? shadow.subtract(player) : Vec3.directionFromRotation(0.0F, client.player.getYRot());
				to = new Vec3(to.x, 0.0, to.z);
				axis = to.lengthSqr() > 1.0E-4 ? to.normalize() : new Vec3(0.0, 0.0, -1.0);
				shadowStart = shadow != null ? shadow : player.add(axis.scale(7.0));
			}

			ArrivalFilm.Shot eye = new ArrivalFilm.Shot(client.player.getEyePosition(partial), client.player.getViewYRot(partial), client.player.getViewXRot(partial));
			return DuelFilm.camera(film, time(partial), player, shadowStart, shadow != null ? shadow : shadowStart, axis, eye, focus);
		}

		float t = time(partial);
		ArrivalFilm.Shot shot = switch (film) {
			case "escape" -> EscapeFilm.camera(t);
			case "duel_end" -> EscapeFilm.tieCamera(t - DuelFilm.TIE_OUT);
			default -> ArrivalFilm.camera(t);
		};
		return new ArrivalFilm.Shot(anchor.add(shot.position()), shot.yaw(), shot.pitch());
	}

	/** The film's music (Phase 24: null for a short scene that keeps the piece playing). */
	public static @Nullable String music() {
		return switch (film != null ? film : "") {
			case "duel", "boss_mid" -> "music.story_duel";
			case "duel_end" -> "music.event_rise";
			case "escape" -> "music.story_finale";
			case "takedown" -> null;
			default -> "music.flight_imperial";
		};
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
		start(Minecraft.getInstance(), name, 10000, List.of());
	}

	/** Only for tests: starts a film with its point (the guard, her TIE) as the server would. */
	public static void startForTest(String name, Vec3 point) {
		start(Minecraft.getInstance(), name, 10000, List.of(point));
	}

	/** Only for tests: how long the film runs. */
	public static int lengthForTest() {
		return film != null ? length : -1;
	}

	/** Only for tests: which film runs, or null. */
	public static @Nullable String filmForTest() {
		return film;
	}

	/** Only for tests: jumps to a moment of the film. */
	public static void seekForTest(int tick) {
		age = Math.max(0, Math.min(length - 1, tick));
	}

	/** Only for tests: skips the film as the player would. */
	public static void skipForTest() {
		stop(Minecraft.getInstance(), true);
	}

	/** The film's lines. */
	private static Line[] lines() {
		return switch (film != null ? film : "") {
			case "arrival" -> ARRIVAL_LINES;
			case "duel" -> DUEL_LINES;
			case "duel_end" -> DUEL_END_LINES;
			case "boss_mid" -> BOSS_LINES;
			case "escape" -> ESCAPE_LINES;
			default -> NO_LINES;
		};
	}

	/** The film's screen: bars, subtitles, the title, the hint to skip; keys skip. */
	private static final class FilmScreen extends Screen {
		FilmScreen() {
			super(Component.empty());
		}

		@Override
		protected void init() {
			// No mouse pointer over the film.
			org.lwjgl.glfw.GLFW.glfwSetInputMode(Minecraft.getInstance().getWindow().handle(), org.lwjgl.glfw.GLFW.GLFW_CURSOR,
					org.lwjgl.glfw.GLFW.GLFW_CURSOR_HIDDEN);
		}

		@Override
		public void removed() {
			org.lwjgl.glfw.GLFW.glfwSetInputMode(Minecraft.getInstance().getWindow().handle(), org.lwjgl.glfw.GLFW.GLFW_CURSOR,
					org.lwjgl.glfw.GLFW.GLFW_CURSOR_NORMAL);
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
			boolean arrival = "arrival".equals(film);
			boolean escape = "escape".equals(film);
			// Phase 24: the short scenes go back to the player's eyes without black.
			boolean scene = "takedown".equals(film) || "boss_mid".equals(film);
			int flash = arrival && t < 8.0F ? Math.round((1.0F - t / 8.0F) * 230.0F) : 0;

			if (escape && t >= EscapeFilm.CORE && t < EscapeFilm.CORE + 22) {
				// Phase 24: the core goes: white.
				flash = Math.round((1.0F - (t - EscapeFilm.CORE) / 22.0F) * 235.0F);
			} else if (escape && t >= EscapeFilm.JUMP && t < EscapeFilm.JUMP + 8) {
				flash = Math.round((1.0F - (t - EscapeFilm.JUMP) / 8.0F) * 120.0F);
			} else if ("duel_end".equals(film) && t >= DuelFilm.TIE_OUT && t < DuelFilm.TIE_OUT + 3) {
				flash = 0;
			}

			if (flash > 0) {
				graphics.fill(0, 0, this.width, this.height, flash << 24 | 0xF2F6FF);
			}

			int fade = arrival || escape ? 20 : 10;
			int black = !scene && t > length - fade ? Math.round(Mth.clamp((t - (length - fade)) / fade, 0.0F, 1.0F) * 255.0F) : 0;

			// Phase 21: the duel's film comes out of black (Phase 24: so does the end of the Tenebris).
			if (!arrival && !scene && t < 8.0F) {
				black = Math.max(black, Math.round((1.0F - t / 8.0F) * 255.0F));
			}

			// Phase 24: her flight cuts outside and back through a moment of black.
			if ("duel_end".equals(film)) {
				for (int cut : new int[] {DuelFilm.TIE_OUT, DuelFilm.TIE_BACK}) {
					if (Math.abs(t - cut) < 4.0F) {
						black = Math.max(black, Math.round((1.0F - Math.abs(t - cut) / 4.0F) * 255.0F));
					}
				}
			}

			if (black > 0) {
				graphics.fill(0, 0, this.width, this.height, black << 24);
			}

			graphics.fill(0, 0, this.width, bar, 0xFF000000);
			graphics.fill(0, this.height - bar, this.width, this.height, 0xFF000000);

			// The title over the hull.
			int titleFrom = arrival ? TITLE_FROM : "duel".equals(film) ? 150 : escape ? 286 : Integer.MAX_VALUE;
			int titleTo = arrival ? TITLE_TO : escape ? 322 : 206;

			if (t >= titleFrom && t < titleTo) {
				float in = Mth.clamp(Math.min(t - titleFrom, titleTo - t) / 14.0F, 0.0F, 1.0F);
				int alpha = Math.max(4, Math.round(in * 255.0F));
				Component title = Component.translatable("sabercraft.story.film." + film + ".title");
				Component under = Component.translatable("sabercraft.story.film." + film + ".subtitle");
				var pose = graphics.pose();
				pose.pushMatrix();
				pose.translate(this.width / 2.0F, bar + 16.0F);
				pose.scale(2.4F, 2.4F);
				graphics.centeredText(this.font, title, 0, 0, alpha << 24 | 0xE8ECF2);
				pose.popMatrix();
				graphics.centeredText(this.font, under, this.width / 2, bar + 42, alpha << 24 | 0x9AA6B8);
			}

			// The crew's lines, in the lower bar.
			for (Line line : lines()) {
				if (t >= line.from() && t < line.to()) {
					float in = Mth.clamp(Math.min(t - line.from(), line.to() - t) / 6.0F, 0.0F, 1.0F);
					int alpha = Math.max(4, Math.round(in * 255.0F));
					Component name = Component.translatable("sabercraft.story.name." + line.speaker());
					Component words = Component.translatable(line.key());
					// Phase 21: long lines in two (they ran off the screen's edges).
					java.util.List<net.minecraft.util.FormattedCharSequence> rows = this.font.split(words, Math.max(120, this.width - 60));
					int count = Math.min(2, rows.size());
					// Centred in the lower bar; a line in two rises over the picture's edge rather than off the screen.
					int y = Math.min(this.height - bar + Math.max(2, (bar - 10 - 10 * count) / 2), this.height - 3 - (10 + 10 * count));
					graphics.centeredText(this.font, name, this.width / 2, y, alpha << 24 | ("mira".equals(line.speaker()) ? 0x8CC0FF : "shadow".equals(line.speaker()) ? 0xFF6A5C : 0xE8C878));

					for (int r = 0; r < count; r++) {
						graphics.centeredText(this.font, rows.get(r), this.width / 2, y + 11 + r * 10, alpha << 24 | 0xECEFF4);
					}
				}
			}

			// How to skip, for the first seconds.
			// (Phase 24: also in the short scenes, which are skipped the same way.)
			if (t >= SKIP_AFTER && t < 140.0F) {
				int alpha = Math.round(Mth.clamp(Math.min(t - SKIP_AFTER, 140.0F - t) / 10.0F, 0.0F, 1.0F) * 150.0F);
				Component skip = Component.translatable("sabercraft.story.film.skip");
				graphics.text(this.font, skip, this.width - this.font.width(skip) - 8, bar / 2 - 4, Math.max(4, alpha) << 24 | 0xB8C0CC);
			}
		}
	}
}
