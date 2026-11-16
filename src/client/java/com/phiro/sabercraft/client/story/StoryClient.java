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

	/** A comlink line: who, the key, how long it shows, how long it has shown. */
	public record Line(String who, String key, int ticks) {
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
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
		HudElementRegistry.addLast(SaberCraft.id("story"), new StoryHud());
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
		music = null;
		ended = false;
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
				RADIO.add(new Line(payload.who(), payload.text(), Mth.clamp(length * RADIO_PER_CHAR + 40, RADIO_MIN, RADIO_MAX)));
				sound(client, "story.radio", 0.55F, 1.0F, null);
			}
			case CUE_HINT -> {
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
			case CUE_END -> {
				ended = true;
				endAge = 0;
			}
			default -> {
			}
		}
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
