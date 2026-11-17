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
			case CUE_DUEL -> duel(client, payload.who(), payload.value());
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

	/** Phase 21: a moment of the duel, for the HUD and the figures. */
	private static void duel(Minecraft client, String who, int value) {
		switch (who) {
			case "hit" -> HITS.put(value, clientTicks);
			case "hurt" -> hurtAge = 0;
			case "blocked" -> blockAge = 0;
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
			}
			default -> {
			}
		}
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

		while (FOCUS_KEY.consumeClick()) {
			if (active() && client.gui.screen() == null) {
				send(StoryActionPayload.FOCUS, "", 0);
			}
		}

		if (client.level == null) {
			return;
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
	public record Pose(StoryFiguresPayload.Fig fig, double x, double y, double z, float yaw, float head, float walk) {
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
			list.add(new Pose(fig, Mth.lerp(k, from.x(), fig.x()), Mth.lerp(k, from.y(), fig.y()), Mth.lerp(k, from.z(), fig.z()),
					Mth.rotLerp(k, from.yaw(), fig.yaw()), Mth.rotLerp(k, from.head(), fig.head()), fig.speed() > 0.05F ? stride : 0.0F));
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
