package com.phiro.sabercraft.client.interior;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.client.interior.MeshBuilder.Layer;
import com.phiro.sabercraft.client.lang.SaberLanguage;
import com.phiro.sabercraft.client.story.StoryHud;
import com.phiro.sabercraft.interior.DestroyerInterior;
import com.phiro.sabercraft.network.LiftPayload;

/**
 * Phase 30: the reactor's maintenance lift on its way. Phiro stood on it waiting and could not
 * tell that it was going: a word in the action bar was all. Now, while it goes:
 *
 * <ul>
 * <li>on the HUD, a panel in the look of the ship's consoles (dark, a thin cyan frame, amber
 * lamps): which way it goes and where to, chevrons running that way, a bar filling in segments
 * and the seconds left; at the end "arrived" a moment;</li>
 * <li>on the lift itself, bands of light running down (or up) its four posts and its pad's ring
 * pulsing, faster as the ride goes on.</li>
 * </ul>
 * The server says how far along the ride is ({@link LiftPayload}); stepping off or moving ends it
 * as before. Phase 30: both are drawn for the rider only, on the rider's own screen (the server
 * tells only the one who stands on the lift); others nearby see the lift as it always was.
 */
public final class LiftHud implements HudElement {
	/** How long (client ticks) "arrived" stays. */
	static final int ARRIVED_TICKS = 24;
	/**
	 * How long (client ticks) without word from the server a ride still shows (a lost packet).
	 * Phase 30: 30, not 6: the server tells every tick while the ride counts and says when it is
	 * over (stepped off, moved, arrived), so a hitch of the server or the network (over 0.3 s) no
	 * longer hides the panel in the middle of a ride.
	 */
	static final int STALE_TICKS = 30;
	/**
	 * Phase 30: from how far along the ride (ticks) it is shown: one moving about on the lift (in a
	 * fight) holds it at its first ticks, and the panel no longer flashes up at each step.
	 */
	static final int SHOW_FROM = 3;

	/** Phase 30: how high the panel is (GUI pixels). */
	public static final int PANEL_HEIGHT = 38;

	/**
	 * Phase 30: where the panel's top goes (GUI y): above the crosshair; else under what the
	 * crosshair is on ("Use: …"). Clear of both, of a hint over the hotbar, of the line over the
	 * hotbar (a room's name, while it shows) and of a mission's title, by the same rule as the
	 * story's progress bar (it covered the prompt, the stun marks and a hint on a small screen).
	 */
	public static int panelTop(Font font, int width, int height) {
		return StoryHud.clearTop(font, width, height, PANEL_HEIGHT, height / 2 - 12 - PANEL_HEIGHT, height / 2 + 26);
	}

	private static int ticks = -1;
	/**
	 * Phase 30: the ride's own clock on the client (ticks), counted on with the client's ticks and
	 * set to the server's word only when the two part by more than a tick: the server's word comes
	 * between two of the client's ticks at no fixed moment, and taken as it came the bar, the
	 * seconds and the lights went back a tick at each of the client's ticks and on again with it.
	 */
	private static int clock = -1;
	private static int total = 1;
	private static boolean down;
	private static int arrivedAge = -1;
	private static int quiet;
	private static long clientTicks;
	private static int shown;
	private static int lit;
	/** Phase 30: the player the ride is shown for (a new one, respawned or into another world: no ride goes on). */
	private static @Nullable LocalPlayer rider;

	private LiftHud() {
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(LiftPayload.TYPE, (payload, context) -> {
			quiet = 0;
			total = Math.max(1, payload.total());
			down = payload.down();

			if (payload.ticks() >= payload.total()) {
				ticks = -1;
				arrivedAge = 0;
			} else {
				if (payload.ticks() < 0 || ticks < 0 || Math.abs(payload.ticks() - clock) > 1) {
					clock = payload.ticks();
				}

				ticks = payload.ticks();

				if (ticks >= 0) {
					arrivedAge = -1;
				}
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			clientTicks++;

			// Phase 30: out of the ship (another world) or respawned, the server says nothing more of
			// a ride; with the longer STALE_TICKS its panel would stay a while there.
			if (client.player != rider) {
				rider = client.player;
				clear();
			}

			if (ticks >= 0 && ++quiet > STALE_TICKS) {
				ticks = -1;
			}

			if (ticks >= 0 && clock < total) {
				clock++;
			}

			if (arrivedAge >= 0 && ++arrivedAge > ARRIVED_TICKS) {
				arrivedAge = -1;
			}
		});
		HudElementRegistry.addLast(SaberCraft.id("lift"), new LiftHud());
	}

	static void clear() {
		ticks = -1;
		clock = -1;
		arrivedAge = -1;
		quiet = 0;
	}

	/** Phase 30, for the tests: the ride as the client has it ({ticks, total, down 1/0, arrived age}). */
	public static int[] stateForTest() {
		return new int[] {ticks, total, down ? 1 : 0, arrivedAge};
	}

	/** Phase 30, for the tests: how many frames the panel was drawn. */
	public static int shownForTest() {
		return shown;
	}

	/** Phase 30, for the tests: how many frames the light on the lift's posts was drawn. */
	public static int litForTest() {
		return lit;
	}

	/** Whether a ride is shown (Phase 30: once it has gone {@link #SHOW_FROM} ticks). */
	private static boolean riding() {
		return ticks >= SHOW_FROM;
	}

	/** How far along the ride is (0..1), or -1 when none shown. */
	static float progress(float partial) {
		return !riding() ? -1.0F : Mth.clamp((clock + partial) / total, 0.0F, 1.0F);
	}

	/**
	 * Phase 30: the seconds left with one decimal, written as in the language the mod's words are
	 * shown in: "0,9" in German, "0.9" in English (and in any other). Public for the tests.
	 */
	public static String secondsText(float seconds) {
		String text = String.format(java.util.Locale.ROOT, "%.1f", seconds);
		return SaberLanguage.GERMAN.equals(SaberLanguage.effective()) ? text.replace('.', ',') : text;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft client = Minecraft.getInstance();

		if (client.player == null || client.gui.hud.isHidden() || !riding() && arrivedAge < 0 || client.gui.screen() != null) {
			return;
		}

		shown++;
		float partial = delta.getGameTimeDeltaPartialTick(false);
		float p = arrivedAge >= 0 ? 1.0F : progress(partial);
		Font font = client.font;
		int width = graphics.guiWidth();
		int height = graphics.guiHeight();
		Component title = Component.translatable(arrivedAge >= 0 ? "sabercraft.interior.lift_arrived" : down ? "sabercraft.interior.lift_down" : "sabercraft.interior.lift_up");
		Component small;

		if (arrivedAge >= 0) {
			small = Component.translatable(down ? "sabercraft.interior.lift_at_bottom" : "sabercraft.interior.lift_at_top");
		} else {
			float left = Math.max(0.0F, (total - clock - partial) / 20.0F);
			small = Component.translatable("sabercraft.interior.lift_wait", secondsText(left));
		}

		int w = Math.max(176, 34 + Math.max(font.width(title), font.width(small)) + 14);
		int h = PANEL_HEIGHT;
		int x = width / 2 - w / 2;
		int y = panelTop(font, width, height);
		float fade = arrivedAge >= 0 ? Mth.clamp(1.0F - (arrivedAge - ARRIVED_TICKS * 0.5F) / (ARRIVED_TICKS * 0.5F), 0.0F, 1.0F) : 1.0F;
		int a = Math.round(fade * 255);

		if (a < 8) {
			return;
		}

		// (The story's progress bar keeps clear of it.)
		StoryHud.liftPanel(y, y + h);

		// The panel: dark, a thin cyan frame, a brighter top edge, small amber lamps in the corners.
		graphics.fill(x, y, x + w, y + h, alpha(0xD0060A10, fade));
		frame(graphics, x, y, w, h, alpha(0xFF2E6E8A, fade));
		graphics.fill(x + 1, y + 1, x + w - 1, y + 2, alpha(0xFF5FC8F0, fade));
		boolean blink = (clientTicks / 6) % 2 == 0;
		int lamp = arrivedAge >= 0 ? 0xFF50E070 : blink ? 0xFFF0A030 : 0xFF6A4410;
		graphics.fill(x + 4, y + 5, x + 8, y + 9, alpha(lamp, fade));
		graphics.fill(x + w - 8, y + 5, x + w - 4, y + 9, alpha(lamp, fade));

		// The words: which way and where to; under them "stay on it" and the seconds left.
		graphics.text(font, title, x + 34, y + 4, alpha(0xFFB8E0F0, fade));
		graphics.text(font, small, x + 34, y + 14, alpha(0xFF7FA8B8, fade));

		// The chevrons: three, running the way it goes (on arrival they stand still and glow green).
		float run = arrivedAge >= 0 ? 0.0F : (clientTicks + partial) * 0.12F % 1.0F;

		for (int i = 0; i < 3; i++) {
			float k = (i + run) / 3.0F;
			int cy = down ? y + 5 + Math.round(k * 24.0F) : y + 29 - Math.round(k * 24.0F);
			float glow = arrivedAge >= 0 ? 1.0F : Mth.sin(k * Mth.PI);
			int colour = arrivedAge >= 0 ? 0xFF50E070 : lerpColour(0xFF1E4A5A, 0xFF7FE0FF, glow);
			chevron(graphics, x + 18, cy, down, alpha(colour, fade * Math.max(0.25F, glow)));
		}

		// The bar: ten segments filling, the newest one pulsing.
		int bx = x + 34;
		int by = y + 26;
		int segments = 10;
		int seg = (w - 34 - 10) / segments;

		for (int i = 0; i < segments; i++) {
			float fill = Mth.clamp(p * segments - i, 0.0F, 1.0F);
			int sx = bx + i * seg;
			graphics.fill(sx, by, sx + seg - 2, by + 6, alpha(0xFF14232C, fade));

			if (fill > 0.0F) {
				int colour = arrivedAge >= 0 ? 0xFF50E070 : fill < 1.0F ? lerpColour(0xFF2A7A9A, 0xFF9FF0FF, 0.5F + 0.5F * Mth.sin((clientTicks + partial) * 0.9F))
						: 0xFF50C8F0;
				graphics.fill(sx, by, sx + Math.round((seg - 2) * fill), by + 6, alpha(colour, fade));
			}
		}
	}

	/** A chevron of rectangles (7 rows), pointing down or up, its middle at x. */
	private static void chevron(GuiGraphicsExtractor graphics, int x, int y, boolean down, int colour) {
		for (int row = 0; row < 4; row++) {
			int r = down ? row : 3 - row;
			int half = 7 - r * 2;
			graphics.fill(x - half, y + row, x - half + 3, y + row + 1, colour);
			graphics.fill(x + half - 3, y + row, x + half, y + row + 1, colour);
		}

		graphics.fill(x - 1, y + (down ? 3 : 0), x + 1, y + (down ? 4 : 1), colour);
	}

	private static void frame(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int colour) {
		graphics.fill(x, y, x + w, y + 1, colour);
		graphics.fill(x, y + h - 1, x + w, y + h, colour);
		graphics.fill(x, y, x + 1, y + h, colour);
		graphics.fill(x + w - 1, y, x + w, y + h, colour);
	}

	private static int alpha(int colour, float k) {
		int a = Math.round((colour >>> 24) * Mth.clamp(k, 0.0F, 1.0F));
		return a << 24 | colour & 0xFFFFFF;
	}

	private static int lerpColour(int from, int to, float k) {
		k = Mth.clamp(k, 0.0F, 1.0F);
		int r = Math.round(Mth.lerp(k, from >> 16 & 255, to >> 16 & 255));
		int g = Math.round(Mth.lerp(k, from >> 8 & 255, to >> 8 & 255));
		int b = Math.round(Mth.lerp(k, from & 255, to & 255));
		return 0xFF000000 | r << 16 | g << 8 | b;
	}

	/**
	 * Every frame, in a Star Destroyer's plot with the player on its reactor's lift while it goes:
	 * bands of light running down (or up) the lift's four posts, its pad's ring pulsing, faster as
	 * the ride goes on. Phase 30: drawn for the rider only (only the rider's client is told of the
	 * ride), along the posts as they stand: from the reactor's floor up to under the deck level,
	 * timed by the ride itself ({@code seconds}, the scene's clock, is no longer used for them).
	 */
	static void lights(MeshBuilder b, float seconds, InteriorVisuals.PlotView plot) {
		Minecraft client = Minecraft.getInstance();
		float p = progress(0.0F);

		if (p < 0.0F || client.player == null) {
			return;
		}

		double lx = client.player.getX() - plot.origin().getX();
		double ly = client.player.getY() - plot.origin().getY();
		double lz = client.player.getZ() - plot.origin().getZ();
		net.minecraft.world.phys.AABB lift = DestroyerInterior.REACTOR_LIFT;

		if (lx < lift.minX - 0.5 || lx > lift.maxX + 0.5 || lz < lift.minZ - 0.5 || lz > lift.maxZ + 0.5) {
			return;
		}

		float floor = (float) Math.floor(ly + 0.01);
		// Phase 30: the bands run the posts' whole height as they stand (plot space, as the posts in
		// DestroyerRooms.reactorWorks), not 2.6 up from the player's feet (on the upper pad, in the
		// air over it, where no post is); as close together and as fast as over those 2.6.
		float postBottom = DestroyerInterior.REACTOR_BOTTOM;
		float run = DestroyerInterior.HANGAR - 0.01F - postBottom - 0.3F;
		int bands = Math.round(run / (2.3F / 3.0F));
		// Phase 30: how far they have run and the ring's pulse, from the ride's own time (seconds
		// since it began), growing faster as before (from 1.4 to 4 runs of 2.3 a second, the pulse
		// from 6 to 16 a second). Taken from the clock (up to an hour) times the speed, they leapt to
		// anywhere on the posts with every tick as the speed grew, and the ring flickered.
		float partial = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		float ride = total / 20.0F;
		float t = Math.min(clock + partial, total) / 20.0F;
		float runs = (1.4F * t + 1.3F * t * t / ride) * 2.3F / run;
		float pp = (float) DestroyerInterior.LIFT_POST + 0.012F;
		lit++;
		b.layer(Layer.GLOW).fullBright().anim(0).fit(InteriorAtlas.IMP_CEILING_LIGHT_GLOW).twoSided(false);

		for (float[] post : new float[][] {{(float) lift.minX, (float) lift.minZ}, {(float) lift.maxX, (float) lift.minZ}, {(float) lift.minX, (float) lift.maxZ},
				{(float) lift.maxX, (float) lift.maxZ}}) {
			for (int band = 0; band < bands; band++) {
				float phase = (runs + band / (float) bands) % 1.0F;
				float k = down ? 1.0F - phase : phase;
				float y0 = postBottom + 0.1F + k * run;
				float glow = Mth.sin(phase * Mth.PI);
				b.colour(lerpColour(0xFF20485A, 0xFF8FE8FF, glow));
				b.box(post[0] - pp, y0, post[1] - pp, post[0] + pp, y0 + 0.16F, post[1] + pp, MeshBuilder.ALL & ~MeshBuilder.UP & ~MeshBuilder.DOWN);
			}
		}

		float pulse = 0.5F + 0.5F * Mth.sin(6.0F * t + 5.0F * t * t / ride);
		b.colour(lerpColour(0xFF2A5A8A, 0xFF9FE0FF, pulse)).fit(InteriorAtlas.IMP_WALL_STRIP_GLOW, 0.0F, 0.42F, 1.0F, 0.58F);
		b.ring((float) lift.getCenter().x, floor + 0.05F, (float) lift.getCenter().z, 0.6F, 0.8F, 20, true);
		b.white().layer(Layer.SOLID).worldLight().anim(0);
	}
}
