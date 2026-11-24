package com.phiro.sabercraft.client.story;

import org.joml.Vector3f;
import org.joml.Vector3fc;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.LightmapRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;

import com.phiro.sabercraft.client.ImmersionConfig;
import com.phiro.sabercraft.interior.ShipInteriors;

/**
 * Phase 31: the story's brightness at its highest level, "Maximal" ({@code story_brightness} 3).
 * Levels 0 to 2 (Phase 27) only lift the story's emergency lights and alarm; dark places with no
 * scripted dimming (the crawl ways, the maintenance room, the ship on its dimmed lights in
 * Mission 5) stayed as dark as they were. Maximal lifts those too, in two ways, and only while the
 * story runs in its ships:
 *
 * <ul>
 * <li>The world's light (the lightmap): the vanilla brightness curve at its brightest and a
 * raised floor for the darkest light, so a dark corner becomes dim instead of black. Full light
 * stays full light, nothing is overexposed.</li>
 * <li>The story's own dimming of its rooms (the dimmer, the emergency light): a curve that lifts
 * the dark end and leaves the bright end as it is.</li>
 * </ul>
 *
 * <p>The HUD, the dialogs and the films' black fades are drawn with a light of their own and are
 * not touched; the vanilla brightness slider works as before outside the story.
 */
public final class StoryBrightness {
	/** The floor of the darkest light at Maximal: dim, with the ships' cool colour. */
	static final float FLOOR_R = 0.16F;
	static final float FLOOR_G = 0.17F;
	static final float FLOOR_B = 0.20F;
	/** The curve on the story's dimming at Maximal (about 1/2.2: dark lifted, bright kept). */
	static final float SHADE_GAMMA = 0.45F;

	private StoryBrightness() {
	}

	/** The level chosen (0 as made .. 3 Maximal). */
	public static int level() {
		return Mth.clamp(ImmersionConfig.get().storyBrightness, 0, ImmersionConfig.STORY_BRIGHTNESS_MAX);
	}

	/** Whether the highest level is chosen. */
	public static boolean maximal() {
		return level() == ImmersionConfig.STORY_BRIGHTNESS_MAX;
	}

	/**
	 * The story's dimming of a room ({@code 1} full light) at that level: unchanged below Maximal
	 * and at full light; at Maximal lifted on a curve (0.17 becomes about 0.45, 0.5 about 0.73).
	 */
	public static float shade(float dim, int level) {
		if (level < ImmersionConfig.STORY_BRIGHTNESS_MAX || dim >= 1.0F) {
			return dim;
		}

		return (float) Math.pow(Mth.clamp(dim, 0.0F, 1.0F), SHADE_GAMMA);
	}

	/** Whether the world's light is lifted now: Maximal, in the story, in its ships. */
	public static boolean liftsWorld(Minecraft client) {
		return maximal() && client.level != null && ShipInteriors.isInterior(client.level) && StoryClient.active();
	}

	/** Called each time the game works out the world's light (see the lightmap mixin). */
	public static void lightmap(LightmapRenderState state) {
		if (liftsNow(Minecraft.getInstance())) {
			apply(state);
		}
	}

	/**
	 * Whether the world's light is lifted now: as {@link #liftsWorld}, but not under the Darkness
	 * effect, which keeps its own darkness (the story uses none, but a command might). Asked of the
	 * player, not of the light: the light's darkness pulses and is 0 half the time.
	 */
	public static boolean liftsNow(Minecraft client) {
		return liftsWorld(client) && !(client.player != null && client.player.hasEffect(MobEffects.DARKNESS));
	}

	/** Lifts that light: the brightest vanilla curve (never beyond it) and the raised floor. */
	public static void apply(LightmapRenderState state) {
		state.brightness = Math.max(state.brightness, 1.0F);
		Vector3fc ambient = state.ambientColor;
		float r = ambient == null ? 0.0F : ambient.x();
		float g = ambient == null ? 0.0F : ambient.y();
		float b = ambient == null ? 0.0F : ambient.z();
		state.ambientColor = new Vector3f(Math.max(r, FLOOR_R), Math.max(g, FLOOR_G), Math.max(b, FLOOR_B));
	}

	/**
	 * Only for tests: the red channel of the light the game draws with at that block light
	 * ({@code 0..15}) for that brightness ({@code 0..1}) and darkest light (as the game's
	 * lightmap shader works it out, in the ships: no sky, block light factor 1.4).
	 */
	public static float lightmapForTest(int blockLevel, float brightness, float ambientR) {
		float level = Mth.clamp(blockLevel, 0, 15) / 15.0F;
		float blockBrightness = level / (4.0F - 3.0F * level) * 1.4F;
		float mixFactor = (2.0F * level - 1.0F) * (2.0F * level - 1.0F);
		// The tint's red is 1.0 (#FFD88C), so the red of the block light's colour is 1.0 too.
		float color = Mth.clamp(ambientR + blockBrightness, 0.0F, 1.0F);
		float inverted = 1.0F - color;
		float notGamma = color <= 0.0F ? 0.0F : 1.0F - inverted * inverted * inverted * inverted;
		return Mth.lerp(brightness, color, notGamma);
	}
}
