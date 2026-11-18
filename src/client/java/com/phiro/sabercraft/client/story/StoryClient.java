package com.phiro.sabercraft.client.story;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.network.StoryActionPayload;
import com.phiro.sabercraft.network.StoryCuePayload;
import com.phiro.sabercraft.network.StoryDialogPayload;
import com.phiro.sabercraft.network.StoryFiguresPayload;
import com.phiro.sabercraft.network.StoryStatePayload;

/**
 * Phase 19: what this client knows of the story: the state the server sent (mission, objectives,
 * marker, alarm, disguise, the things to use), the people near the player (two snapshots, drawn
 * in between), the open dialog, and what plays once (comlink lines, hints, a fade, the mission's
 * title, a droid's scan, sounds and music). The server decides everything; this only shows it.
 */
public final class StoryClient {
	// The cues (as Story.CUE_*).
	static final byte CUE_RADIO = 0;
	static final byte CUE_HINT = 1;
	static final byte CUE_FADE = 2;
	static final byte CUE_SOUND = 3;
	static final byte CUE_MUSIC = 4;
	static final byte CUE_SCAN = 5;
	static final byte CUE_TITLE = 6;
	static final byte CUE_END = 7;
	// Phase 20: where a droid's noise went, a checkpoint saved, a scene played.
	static final byte CUE_NOISE = 8;
	static final byte CUE_CINEMA = 9;
	static final byte CUE_CHECKPOINT = 10;
	/** Phase 21: the duel's moments (who: hit, hurt, blocked, parried, phase, meter, focus_on, focus_off, over). */
	static final byte CUE_DUEL = 11;

	/** Phase 21: the Focus (slowed time in a fight; learnt with Ilyra's saber). */
	public static final net.minecraft.client.KeyMapping FOCUS_KEY = new net.minecraft.client.KeyMapping("key.sabercraft.story_focus",
			com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM, com.mojang.blaze3d.platform.InputConstants.KEY_G,
			com.phiro.sabercraft.client.SaberCraftClient.KEY_CATEGORY);
	/** Phase 21: per figure, when (client ticks) it was last hit. */
	private static final Map<Integer, Long> HITS = new HashMap<>();
	/** Phase 21: the Focus meter (percent; -1 unknown), whether it runs, when the player was last hurt or blocked, stun hits. */
	private static int focusMeter = -1;
	private static boolean focusOn;
	private static int focusAge;
	private static int hurtAge = 1000;
	private static int blockAge = 1000;
	private static int stuns;
	private static int stunAge = 1000;
	private static int duelPhase = 1;
	private static int foci;
	private static int meterAge = 1000;
	/** Phase 23: the camera's jolt (how strong, how long, its age) and its zoom (how far in, how long, its age). */
	private static float shakeStrength;
	private static int shakeTicks = 1;
	private static int shakeAge = 1000;
	private static float zoomKick;
	private static int zoomTicks = 1;
	private static int zoomAge = 1000;
	/** Phase 23: the blade lock (how long it lasts, its age; the view closes in while it holds). */
	private static int lockTicks;
	private static int lockAge = 1000;
	/** Phase 23: the reactor's bridge groaning under the player (ticks it lasts, how long since it began). */
	private static int creakTicks = 1;
	private static int creakAge = 1000;
	/** Phase 23: a flash over the view (a clash), its colour and age. */
	private static int flashColour;
	private static int flashAge = 1000;
	/** Phase 23: where the last hurt came from (world direction, x and z), and when. */
	private static double hurtX;
	private static double hurtZ;
	private static int hurtFromAge = 1000;
	private static int lastHurtTime;
	/** Phase 23: her saber struck from her hand: where it flew from and to (world), when (client ticks). */
	private static @Nullable Vec3 hiltFrom;
	private static @Nullable Vec3 hiltTo;
	private static long hiltAt;
	private static float hiltYaw;
	/** Phase 23: how long the struck saber flies (ticks; as the server's). */
	static final int HILT_FLIGHT = 16;
	/** Phase 23: the camera moments seen (for tests). */
	private static int jolts;
static final int CHECKPOINT_TICKS = 60;

	/** How long a comlink line stays (ticks) per character, at least, at most. */
	static final int RADIO_PER_CHAR = 1;
	static final int RADIO_MIN = 70;
	static final int RADIO_MAX = 240;
	static final int HINT_TICKS = 70;
	static final int TITLE_TICKS = 110;

	private static @Nullable StoryStatePayload state;
	/** The two last snapshots of the people, and when (client ticks) the newer one came. */
	private static @Nullable StoryFiguresPayload previous;
	private static @Nullable StoryFiguresPayload current;
	private static long receivedAt;
	private static long clientTicks;
	/** How far each figure's legs have walked (for the steps). */
	private static final Map<Integer, Float> STRIDES = new HashMap<>();
	private static @Nullable StoryDialogPayload dialog;

	/** A comlink line (or one overheard nearby): who, the key, how long it shows. */
	public record Line(String who, String key, int ticks, boolean heard) {
	}

	private static final Deque<Line> RADIO = new ArrayDeque<>();
	private static @Nullable Line radioNow;
	private static int radioAge;
	private static @Nullable String hint;
	private static int hintAge;
	/** A progress bar's hint (a download, a hack): its key and value (0..100), and when it was last sent. */
	private static @Nullable String progressKey;
	private static int progressValue;
	private static int progressAge;
	/** A fade to black: its length, age and the words shown in the dark. */
	private static int fadeTicks;
	private static int fadeAge;
	private static String fadeText = "";
	private static @Nullable String title;
	private static int titleAge;
	private static List<Vec3> scan = List.of();
	private static int scanTicks;
	private static int scanAge;
	/** Phase 20: where M-31's noise went (a ring there for a moment), and the checkpoint's notice. */
	private static List<Vec3> noise = List.of();
	private static int noiseTicks;
	private static int noiseAge;
	private static int checkpointAge = -1;
	/** Phase 20: how often M-31 made his noise in this game (the tip near guards goes after a few). */
	private static int noisesMade;
	private static @Nullable String music;
	private static boolean ended;
	private static int endAge;
	private static int received;

	private StoryClient() {
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(StoryStatePayload.TYPE, (payload, context) -> state(payload));
		ClientPlayNetworking.registerGlobalReceiver(StoryFiguresPayload.TYPE, (payload, context) -> figures(payload));
		ClientPlayNetworking.registerGlobalReceiver(StoryDialogPayload.TYPE, (payload, context) -> dialog(context.client(), payload));
		ClientPlayNetworking.registerGlobalReceiver(StoryCuePayload.TYPE, (payload, context) -> cue(context.client(), payload));
		ClientTickEvents.END_CLIENT_TICK.register(StoryClient::tick);
		net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper.registerKeyMapping(FOCUS_KEY);
		StoryCinema.init();
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
		HudElementRegistry.addLast(SaberCraft.id("story"), new StoryHud());
		// Phase 22: no hunger, experience or armour in the story; a condition bar for the hearts.
		StoryVitals.init();
		ClientTickEvents.END_CLIENT_TICK.register(StoryVitals::tick);
	}

	static void reset() {
		state = null;
		previous = null;
		current = null;
		dialog = null;
		STRIDES.clear();
		RADIO.clear();
		radioNow = null;
		hint = null;
		progressKey = null;
		fadeTicks = 0;
		title = null;
		scan = List.of();
		noise = List.of();
		noiseTicks = 0;
		checkpointAge = -1;
		music = null;
		ended = false;
		HITS.clear();
		focusMeter = -1;
		focusOn = false;
		hurtAge = 1000;
		blockAge = 1000;
		stuns = 0;
		stunAge = 1000;
		duelPhase = 1;
		shakeAge = 1000;
		zoomAge = 1000;
		lockAge = 1000;
		creakAge = 1000;
		flashAge = 1000;
		hurtFromAge = 1000;
		hiltFrom = null;
		hiltTo = null;
	}

	// --- Receiving ---------------------------------------------------------------------------------

	private static void state(StoryStatePayload payload) {
		boolean was = active();
		state = payload;
		received++;

		if (!payload.active()) {
			previous = null;
			current = null;
			STRIDES.clear();
			music = null;

			if (was) {
				RADIO.clear();
				radioNow = null;
				progressKey = null;
				dialog = null;
			}
		}

		StoryMenus.refresh();
	}

	private static void figures(StoryFiguresPayload payload) {
		previous = current != null && current.origin().equals(payload.origin()) ? current : payload;
		current = payload;
		receivedAt = clientTicks;
	}

	private static void dialog(Minecraft client, StoryDialogPayload payload) {
		if (!payload.open()) {
			dialog = null;

			if (client.gui.screen() instanceof DialogScreen) {
				client.gui.setScreen(null);
			}

			return;
		}

		dialog = payload;
		DialogScreen.open(client, payload);
	}

	private static void cue(Minecraft client, StoryCuePayload payload) {
		switch (payload.cue()) {
			case CUE_RADIO -> {
				int length = client.font != null ? net.minecraft.network.chat.Component.translatable(payload.text()).getString().length() : 60;
				boolean heard = payload.value() == 1;
				RADIO.add(new Line(payload.who(), payload.text(), Mth.clamp(length * RADIO_PER_CHAR + 40, RADIO_MIN, RADIO_MAX), heard));

				// Overheard in the room: no comlink click, only the soft voice.
				if (heard) {
					sound(client, "story.blip", 0.12F, 0.8F, null);
				} else {
					sound(client, "story.radio", 0.55F, 1.0F, null);
				}
			}
			case CUE_DUEL -> duel(client, payload.who(), payload.value(), payload.points());
			case CUE_HINT -> {
				if ("stun".equals(payload.who())) {
					stuns = payload.value();
					stunAge = 0;
				}

				if ("progress".equals(payload.who())) {
					progressKey = payload.text();
					progressValue = payload.value();
					progressAge = 0;
				} else {
					hint = payload.text();
					hintAge = 0;
				}
			}
			case CUE_FADE -> {
				fadeTicks = Math.max(10, payload.value()) * 2;
				fadeAge = 0;
				fadeText = payload.text();
				// What was said before the black ("detected! hide!") is over after it.
				hint = null;
			}
			case CUE_SOUND -> sound(client, payload.text(), 0.9F, 1.0F, figure(payload.value()));
			case CUE_MUSIC -> music = payload.text().isEmpty() ? null : payload.text();
			case CUE_SCAN -> {
				scan = List.copyOf(payload.points());
				scanTicks = Math.max(20, payload.value());
				scanAge = 0;
				sound(client, "story.scan", 0.7F, 1.0F, null);
			}
			case CUE_TITLE -> {
				title = payload.text();
				titleAge = 0;
			}
			case CUE_NOISE -> {
				noise = List.copyOf(payload.points());
				noiseTicks = Math.max(20, payload.value());
				noiseAge = 0;
				noisesMade++;
			}
			case CUE_CHECKPOINT -> checkpointAge = 0;
			case CUE_CINEMA -> StoryCinema.start(client, payload.who(), payload.value());
			case CUE_END -> {
				ended = true;
				endAge = 0;
			}
			default -> {
			}
		}
	}

	/**
	 * Phase 21: a moment of the duel, for the HUD and the figures. Phase 23: each moment is also
	 * felt: the camera jolts with a hit, more with a heavy one, hard with a clash (and the view
	 * flashes and closes in a little), it trembles while the blades are locked, and the struck
	 * saber's flight is drawn. (Hits on troopers come the same way.)
	 */
	private static void duel(Minecraft client, String who, int value, List<Vec3> points) {
		switch (who) {
			case "hit" -> {
				HITS.put(value, clientTicks);
				shake(0.35F, 5);
			}
			case "hit_heavy" -> {
				HITS.put(value, clientTicks);
				shake(0.9F, 8);
				zoom(0.035F, 8);
			}
			case "parried" -> shake(0.45F, 5);
			case "clash" -> {
				shake(1.6F, 11);
				zoom(0.06F, 10);
				flash(0x00FFF2E0, 0);
			}
			case "perfect" -> {
				blockAge = 0;
				shake(0.8F, 7);
				flash(0x00C8DCFF, 0);
			}
			case "lock" -> {
				lockTicks = Math.max(10, value);
				lockAge = 0;
				shake(1.2F, 9);
				flash(0x00FFE0D0, 0);
			}
			case "lock_push" -> shake(0.55F, 5);
			case "creak" -> {
				// Phase 23: the reactor's bridge groans: it trembles (drawn, see StoryRooms), so does the view.
				creakTicks = Math.max(20, value);
				creakAge = 0;
				shake(0.5F, 14);
			}
			case "quake" -> {
				// Phase 23: ... and breaks: a hard jolt, a flash of the sparks.
				creakAge = 1000;
				shake(2.4F, Math.max(10, value));
				zoom(0.05F, 12);
				flash(0x00FFE4C8, 0);
			}
			case "lock_end" -> {
				lockAge = 1000;
				shake(2.2F, 15);
				zoom(0.08F, 14);
				flash(0x00FFF4E8, 0);
			}
			case "dodged" -> zoom(-0.03F, 8);
			case "disarm" -> {
				shake(1.8F, 12);
				zoom(0.07F, 16);
				flash(0x00FFF0E0, 0);

				if (points.size() >= 2) {
					hiltFrom = points.get(0);
					hiltTo = points.get(1);
					hiltAt = clientTicks;
					hiltYaw = (value * 53 % 360);
				}
			}
			case "hurt" -> {
				hurtAge = 0;
				shake(1.1F, 8);

				if (!points.isEmpty() && client.player != null) {
					hurtX = points.get(0).x - client.player.getX();
					hurtZ = points.get(0).z - client.player.getZ();
					hurtFromAge = 0;
				}
			}
			case "blocked" -> {
				blockAge = 0;
				shake(0.5F, 5);
			}
			case "phase" -> duelPhase = value;
			case "meter" -> {
				meterAge = 0;
				focusMeter = value % 1000;
				focusOn = value >= 1000;
			}
			case "focus_on" -> {
				focusOn = true;
				focusAge = 0;
				foci++;
			}
			case "focus_off" -> focusOn = false;
			case "over" -> {
				focusOn = false;
				duelPhase = 1;
				lockAge = 1000;
			}
			default -> {
			}
		}
	}

	/** Phase 23: the camera jolts (a stronger jolt wins over a weaker one still running). */
	/** Phase 23: how hard the reactor's bridge trembles now (0 still, 1 just began to groan). */
	public static float creak() {
		return creakAge < creakTicks ? 1.0F - (float) creakAge / creakTicks : 0.0F;
	}

	static void shake(float strength, int ticks) {
		if (shakeAge < shakeTicks && shakeStrength * (1.0F - (float) shakeAge / shakeTicks) > strength) {
			return;
		}

		shakeStrength = strength;
		shakeTicks = Math.max(1, ticks);
		shakeAge = 0;
		jolts++;
	}

	/** Phase 23: the view closes in for a moment (negative: it opens a little). */
	private static void zoom(float kick, int ticks) {
		zoomKick = kick;
		zoomTicks = Math.max(1, ticks);
		zoomAge = 0;
	}

	private static void flash(int colour, int age) {
		flashColour = colour;
		flashAge = age;
	}

	/**
	 * Phase 23: the jolt of the camera now (first person): {dip, pitch, roll} in blocks and
	 * degrees, or false when nothing shakes. A blade lock trembles as long as it holds.
	 */
	public static boolean camera(float partial, float[] out) {
		if (!active()) {
			return false;
		}

		float t = shakeAge + partial;
		float strength = 0.0F;

		if (shakeAge < shakeTicks) {
			float left = 1.0F - t / shakeTicks;
			strength = shakeStrength * Math.max(0.0F, left) * Math.max(0.0F, left);
		}

		if (lockAge < lockTicks) {
			strength = Math.max(strength, 0.28F);
		}

		if (strength <= 0.001F) {
			return false;
		}

		float time = (clientTicks + partial);
		out[0] = -0.012F * strength * Mth.sin(time * 2.9F);
		out[1] = strength * 0.9F * Mth.sin(time * 3.7F + 1.3F);
		out[2] = strength * 0.7F * Mth.cos(time * 3.1F);
		return true;
	}

	/** Phase 23: how the field of view is scaled now (a clash closes in, the blade lock holds it closer). */
	public static float fovScale(float partial) {
		if (!active()) {
			return 1.0F;
		}

		float scale = 1.0F;

		if (zoomAge < zoomTicks) {
			float left = Math.max(0.0F, 1.0F - (zoomAge + partial) / zoomTicks);
			scale -= zoomKick * left * left;
		}

		if (lockAge < lockTicks + 10) {
			float in = Math.min(1.0F, (lockAge + partial) / 8.0F);
			float out = lockAge < lockTicks ? 1.0F : Math.max(0.0F, 1.0F - (lockAge - lockTicks + partial) / 10.0F);
			scale -= 0.08F * in * out;
		}

		return scale;
	}

	/** Phase 23: the flash over the view now (ARGB), or 0. */
	static int flash(float partial) {
		float age = flashAge + partial;

		if (age >= 5.0F) {
			return 0;
		}

		int alpha = Math.round((1.0F - age / 5.0F) * 110.0F);
		return alpha << 24 | flashColour & 0xFFFFFF;
	}

	/** Phase 23: where the last hurt came from (world x and z, from the player), how long ago, or null. */
	static double @Nullable [] hurtFrom() {
		return hurtFromAge < 24 ? new double[] {hurtX, hurtZ, hurtFromAge} : null;
	}

	/** Phase 23, for the tests: whether the HUD shows where the last hurt came from now. */
	public static boolean hurtFromForTest() {
		return hurtFrom() != null;
	}

	/**
	 * Phase 23: her struck saber (plot space of that ship): {x, y, z, yaw, pitch} while it flies
	 * (spinning, in an arc) and after it lies on the floor; null if there is none.
	 */
	public static float @Nullable [] looseHilt(BlockPos origin, float partial) {
		Vec3 from = hiltFrom;
		Vec3 to = hiltTo;
		StoryFiguresPayload now = current;

		if (from == null || to == null || now == null || !now.origin().equals(origin)) {
			return null;
		}

		float k = Math.min(1.0F, (clientTicks - hiltAt + partial) / HILT_FLIGHT);
		double x = Mth.lerp(k, from.x, to.x) - origin.getX();
		double z = Mth.lerp(k, from.z, to.z) - origin.getZ();
		// Up in an arc, then down onto the floor (it lies a little above it, being round).
		double y = Mth.lerp(k, from.y, to.y + 0.04) + 1.1 * 4.0 * k * (1.0 - k) - origin.getY();
		float pitch = k < 1.0F ? k * 900.0F : 90.0F;
		return new float[] {(float) x, (float) y, (float) z, hiltYaw + k * 160.0F, pitch};
	}

	/** Phase 23: where the struck saber flew from and lands (world), for the film of the end; null if none. */
	static @Nullable Vec3 hiltLanding() {
		return hiltTo;
	}

	static @Nullable Vec3 hiltStart() {
		return hiltFrom;
	}

	/** Only for tests: how often the camera jolted, whether the struck saber is drawn. */
	public static int joltsForTest() {
		return jolts;
	}

	public static int duelPhaseForTest() {
		return duelPhase;
	}

	public static boolean looseHiltForTest() {
		return hiltFrom != null;
	}

	/** Phase 21: the jolt of a hit on that figure (1 just hit, 0 after a quarter second). */
	public static float hitJolt(int id) {
		Long at = HITS.get(id);

		if (at == null) {
			return 0.0F;
		}

		float age = clientTicks - at + Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
		// Phase 22: a little longer (8 ticks), so that a trooper is seen to reel from each hit.
		return age >= 8.0F ? 0.0F : 1.0F - age / 8.0F;
	}

	static int focusMeter() {
		return focusMeter;
	}

	static boolean focusOn() {
		return focusOn;
	}

	/** Phase 21: whether the meter came lately (only while fighting). */
	static boolean meterShown() {
		return meterAge < 20 && focusMeter >= 0;
	}

	/** Phase 21: the one with a saber who fights (the Schattenklinge), or null. */
	static StoryFiguresPayload.@Nullable Fig duelist() {
		StoryFiguresPayload now = current;

		if (now == null) {
			return null;
		}

		for (StoryFiguresPayload.Fig fig : now.figures()) {
			if ((fig.flags() & StoryFiguresPayload.SABER) != 0 && (fig.flags() & StoryFiguresPayload.HOSTILE) != 0) {
				return fig;
			}
		}

		return null;
	}

	static int focusAge() {
		return focusAge;
	}

	static int hurtAge() {
		return hurtAge;
	}

	static int blockAge() {
		return blockAge;
	}

	static int stuns() {
		return stuns;
	}

	static int stunAge() {
		return stunAge;
	}

	static int duelPhase() {
		return duelPhase;
	}

	/** Only for tests: how often the Focus began here, and its meter. */
	public static int fociForTest() {
		return foci;
	}

	public static int focusMeterForTest() {
		return focusMeter;
	}

	/** Only for tests: what the duel HUD names (the duelist's attack kind), or -1 with no duelist. */
	public static int duelKindForTest() {
		StoryFiguresPayload.Fig duelist = duelist();
		return duelist == null ? -1 : duelist.windup() >> 5;
	}

	/** Plays a sound of the mod (at a figure, or at the player). */
	static void sound(Minecraft client, String name, float volume, float pitch, @Nullable Vec3 at) {
		if (client.player == null) {
			return;
		}

		SoundEvent event = SoundEvent.createVariableRangeEvent(SaberCraft.id(name));
		RandomSource random = RandomSource.create();

		if (at != null) {
			client.getSoundManager().play(new SimpleSoundInstance(event, SoundSource.NEUTRAL, volume, pitch, random, at.x, at.y, at.z));
		} else {
			client.getSoundManager().play(SimpleSoundInstance.forUI(event, pitch, volume));
		}
	}

	private static void tick(Minecraft client) {
		clientTicks++;
		focusAge++;
		hurtAge++;
		blockAge++;
		stunAge++;
		meterAge++;
		shakeAge++;
		zoomAge++;
		lockAge++;
		creakAge++;
		flashAge++;
		hurtFromAge++;

		while (FOCUS_KEY.consumeClick()) {
			if (active() && client.gui.screen() == null) {
				send(StoryActionPayload.FOCUS, "", 0);
			}
		}

		if (client.level == null) {
			return;
		}

		// Phase 23: a hurt from something with a place (a bolt, a blow): the HUD shows from where.
		int hurtTime = client.player != null ? client.player.hurtTime : 0;
		boolean newHurt = hurtTime > lastHurtTime;
		lastHurtTime = hurtTime;

		if (newHurt && active() && hurtFromAge > 0) {
			net.minecraft.world.damagesource.DamageSource source = client.player.getLastDamageSource();
			Vec3 from = source != null ? source.getSourcePosition() : null;

			if (from != null) {
				hurtX = from.x - client.player.getX();
				hurtZ = from.z - client.player.getZ();
				hurtFromAge = 0;
			}
		}

		// The legs of everyone walking.
		if (current != null) {
			for (StoryFiguresPayload.Fig fig : current.figures()) {
				STRIDES.merge(fig.id(), fig.speed() / 20.0F * 3.4F, Float::sum);
			}
		}

		if (radioNow != null && ++radioAge >= radioNow.ticks()) {
			radioNow = null;
		}

		if (radioNow == null && !RADIO.isEmpty()) {
			radioNow = RADIO.poll();
			radioAge = 0;
		}

		if (hint != null && ++hintAge > HINT_TICKS) {
			hint = null;
		}

		if (progressKey != null && ++progressAge > 40) {
			progressKey = null;
		}

		if (fadeTicks > 0 && ++fadeAge > fadeTicks) {
			fadeTicks = 0;
		}

		if (title != null && ++titleAge > TITLE_TICKS) {
			title = null;
		}

		if (scanTicks > 0 && ++scanAge > scanTicks) {
			scanTicks = 0;
			scan = List.of();
		}

		if (noiseTicks > 0 && ++noiseAge > noiseTicks) {
			noiseTicks = 0;
			noise = List.of();
		}

		if (checkpointAge >= 0 && ++checkpointAge > CHECKPOINT_TICKS) {
			checkpointAge = -1;
		}

		if (ended) {
			endAge++;
		}

		StoryInput.tick(client);
	}

	// --- What the rest of the client asks --------------------------------------------------------

	/** Whether this player is playing the story now. */
	public static boolean active() {
		return state != null && state.active();
	}

	public static @Nullable StoryStatePayload state() {
		return state;
	}

	/** Whether that plot is one of this player's story ships (its crew is the story's people then). */
	public static boolean storyPlot(BlockPos origin) {
		StoryStatePayload s = state;
		return s != null && s.active() && (origin.equals(s.falcon()) || origin.equals(s.destroyer()));
	}

	/** Phase 20: whether the crew of the mission now is wary (after an alarm, until the mission ends). */
	static boolean wary() {
		StoryStatePayload s = state;
		return s != null && s.active() && s.known().contains("wary.m" + (s.mission() + 1));
	}

	/**
	 * Phase 20: how a story's ship is lit now, for its rooms: null as always; else the factors for
	 * the surfaces lit by the world (red, green, blue) and for the glowing ones (lamps, screens),
	 * then how bright the surfaces are. The emergency lights (from the copy until the saber): dim
	 * and warm, the lamps red. The alarm: the lamps pulse red while they are after the player
	 * (slower and paler while they search).
	 */
	public static float @Nullable [] shipLight(BlockPos origin, float seconds) {
		StoryStatePayload s = state;

		if (s == null || !s.active() || !origin.equals(s.destroyer())) {
			return null;
		}

		boolean emergency = s.mission() >= 4 && s.known().contains("know.download") && !s.known().contains("saber.taken");
		int alarm = s.alarm();

		if (!emergency && alarm < 2) {
			return null;
		}

		float[] light = {1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F};

		if (emergency) {
			light[0] = 1.0F;
			light[1] = 0.66F;
			light[2] = 0.5F;
			light[3] = 1.0F;
			light[4] = 0.34F;
			light[5] = 0.28F;
			light[6] = 0.5F;
		}

		if (alarm >= 2) {
			float speed = alarm >= 3 ? 1.1F : 0.45F;
			float pulse = 0.5F + 0.5F * Mth.sin(seconds * speed * Mth.TWO_PI);
			float depth = alarm >= 3 ? 0.55F : 0.3F;
			light[1] *= 1.0F - depth * pulse * 0.6F;
			light[2] *= 1.0F - depth * pulse * 0.7F;
			light[4] *= 1.0F - depth * pulse;
			light[5] *= 1.0F - depth * pulse;
			light[3] = Math.min(1.25F, light[3] * (1.0F + 0.25F * pulse));
		}

		return light;
	}

	/** Whether a flag (what is known, done, decided) is set. */
	public static boolean has(String flag) {
		StoryStatePayload s = state;
		return s != null && s.known().contains(flag);
	}

	/** The mission (0 = the first) or -1 outside the story. */
	public static int mission() {
		StoryStatePayload s = state;
		return s != null && s.active() ? s.mission() : -1;
	}

	/** The step's objective key, or empty. */
	public static String objective() {
		StoryStatePayload s = state;
		return s != null && s.active() ? s.objective() : "";
	}

	/** Whether a thing of that id is there to be used now. */
	public static boolean point(String id) {
		StoryStatePayload s = state;

		if (s == null || !s.active()) {
			return false;
		}

		for (StoryStatePayload.Point p : s.points()) {
			if (p.id().equals(id)) {
				return true;
			}
		}

		return false;
	}

	/** One person, between the last two snapshots (plot space): where, which way, how far their legs walked. */
	public record Pose(StoryFiguresPayload.Fig fig, double x, double y, double z, float yaw, float head, float walk, float reactP) {
	}

	/** Everyone near the player in that plot, as drawn now. */
	public static List<Pose> figures(BlockPos origin, float partial) {
		StoryFiguresPayload now = current;
		StoryFiguresPayload before = previous;

		if (now == null || !now.origin().equals(origin) || !storyPlot(origin)) {
			return List.of();
		}

		float span = Math.max(1.0F, now.time() - (before != null ? before.time() : now.time() - 2));
		float t = Mth.clamp((clientTicks - receivedAt + partial) / span, 0.0F, 1.0F);
		Map<Integer, StoryFiguresPayload.Fig> old = new HashMap<>();

		if (before != null && before != now) {
			for (StoryFiguresPayload.Fig fig : before.figures()) {
				old.put(fig.id(), fig);
			}
		}

		List<Pose> list = new ArrayList<>();

		for (StoryFiguresPayload.Fig fig : now.figures()) {
			StoryFiguresPayload.Fig from = old.getOrDefault(fig.id(), fig);
			double jump = Math.abs(fig.x() - from.x()) + Math.abs(fig.z() - from.z()) + Math.abs(fig.y() - from.y());
			// Someone put somewhere else (a checkpoint, a scene): there at once.
			float k = jump > 4.0 ? 1.0F : t;
			float stride = STRIDES.getOrDefault(fig.id(), 0.0F) + partial * fig.speed() / 20.0F * 3.4F;
			// Phase 23: a reaction (a hit, a fall, a clash) runs smoothly between the snapshots.
			float reactP = from.reactKind() == fig.reactKind() && fig.reactProgress() >= from.reactProgress()
					? Mth.lerp(t, from.reactProgress(), fig.reactProgress()) : fig.reactProgress();
			list.add(new Pose(fig, Mth.lerp(k, from.x(), fig.x()), Mth.lerp(k, from.y(), fig.y()), Mth.lerp(k, from.z(), fig.z()),
					Mth.rotLerp(k, from.yaw(), fig.yaw()), Mth.rotLerp(k, from.head(), fig.head()), fig.speed() > 0.05F ? stride : 0.0F, reactP));
		}

		return list;
	}

	/** Where a figure is now (world space), or {@code null}. */
	static @Nullable Vec3 figure(int id) {
		StoryFiguresPayload now = current;

		if (now == null || id <= 0) {
			return null;
		}

		for (StoryFiguresPayload.Fig fig : now.figures()) {
			if (fig.id() == id) {
				return new Vec3(fig.x() + now.origin().getX(), fig.y() + now.origin().getY() + 1.5, fig.z() + now.origin().getZ());
			}
		}

		return null;
	}

	/** Phase 21: where the one with a saber is now (feet, world space), or {@code null}. */
	static @Nullable Vec3 saberFigure(float partial) {
		StoryFiguresPayload now = current;

		if (now == null) {
			return null;
		}

		for (Pose pose : figures(now.origin(), partial)) {
			if ((pose.fig().flags() & StoryFiguresPayload.SABER) != 0) {
				return new Vec3(pose.x() + now.origin().getX(), pose.y() + now.origin().getY(), pose.z() + now.origin().getZ());
			}
		}

		return null;
	}

	/** The plot the figures are in, or {@code null}. */
	static @Nullable BlockPos figuresOrigin() {
		StoryFiguresPayload now = current;
		return now != null ? now.origin() : null;
	}

	static @Nullable StoryDialogPayload dialog() {
		return dialog;
	}

	static @Nullable Line radio() {
		return radioNow;
	}

	static int radioAge() {
		return radioAge;
	}

	static @Nullable String hint() {
		return hint;
	}

	static int hintAge() {
		return hintAge;
	}

	static @Nullable String progressKey() {
		return progressKey;
	}

	static int progressValue() {
		return progressValue;
	}

	/** The fade's darkness now (0..1) and its words. */
	static float fade(float partial) {
		if (fadeTicks <= 0) {
			return 0.0F;
		}

		float age = fadeAge + partial;
		float edge = Math.min(14.0F, fadeTicks / 3.0F);
		return Mth.clamp(Math.min(age / edge, (fadeTicks - age) / edge), 0.0F, 1.0F);
	}

	static String fadeText() {
		return fadeText;
	}

	static @Nullable String title() {
		return title;
	}

	static int titleAge() {
		return titleAge;
	}

	static List<Vec3> scan() {
		return scan;
	}

	static float scanLeft() {
		return scanTicks <= 0 ? 0.0F : 1.0F - scanAge / (float) scanTicks;
	}

	static List<Vec3> noise() {
		return noise;
	}

	/** How far the noise's ring has gone (0 at the start, 1 at the end; 0 when there is none). */
	static float noiseAge() {
		return noiseTicks <= 0 ? 0.0F : noiseAge / (float) noiseTicks;
	}

	static int noisesMade() {
		return noisesMade;
	}

	/** Ticks since a checkpoint was saved, or -1. */
	static int checkpointAge() {
		return checkpointAge;
	}

	/** For the tests: whether a noise mark or the checkpoint's notice shows now. */
	public static boolean noiseShownForTest() {
		return noiseTicks > 0 && !noise.isEmpty();
	}

	public static boolean checkpointShownForTest() {
		return checkpointAge >= 0;
	}

	public static @Nullable String music() {
		return active() ? music : null;
	}

	static boolean ended() {
		return ended;
	}

	static int endAge() {
		return endAge;
	}

	static void dismissEnd() {
		ended = false;
	}

	/** Sends an action to the server (if it has the story). */
	public static boolean send(byte action, String text, int value) {
		if (!ClientPlayNetworking.canSend(StoryActionPayload.TYPE)) {
			return false;
		}

		ClientPlayNetworking.send(new StoryActionPayload(action, text, value));
		return true;
	}

	/** Whether a lock only a droid opens is that near (world space). */
	public static boolean lockNear(Vec3 at, double distance) {
		StoryStatePayload s = state;

		if (s == null || !s.active()) {
			return false;
		}

		for (StoryStatePayload.Point p : s.points()) {
			if (p.hack() && p.box().distanceToSqr(at) < distance * distance) {
				return true;
			}
		}

		return false;
	}

	/** The box of a thing to use (world space), if it is there. */
	static @Nullable AABB pointBox(String id) {
		StoryStatePayload s = state;

		if (s == null) {
			return null;
		}

		for (StoryStatePayload.Point p : s.points()) {
			if (p.id().equals(id)) {
				return p.box();
			}
		}

		return null;
	}

	// --- For tests ---------------------------------------------------------------------------------

	public static int receivedForTest() {
		return received;
	}

	public static @Nullable StoryDialogPayload dialogForTest() {
		return dialog;
	}

	public static @Nullable Line radioForTest() {
		return radioNow != null ? radioNow : RADIO.peek();
	}

	public static int figuresForTest() {
		StoryFiguresPayload now = current;
		return now != null ? now.figures().size() : 0;
	}
}
