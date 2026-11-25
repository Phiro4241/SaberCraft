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
import com.phiro.sabercraft.story.StaffThrow;

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
	/** Phase 26: a short effect somewhere (who: its name; value: how long, ticks; points: where). */
	static final byte CUE_EFFECT = 12;
	/** Phase 27: the player has to act at once: dialogs, menus and the inventory close. */
	static final byte CUE_INTERRUPT = 13;

	/** Phase 26: the effects running (name: start in client ticks, length, where). */
	private record Effect(long start, int ticks, List<Vec3> points) {
	}

	private static final Map<String, Effect> EFFECTS = new HashMap<>();
	private static int effectsSeen;

	/**
	 * Phase 26: how far an effect of that name is (0 just begun, 1 over; the partial tick counted),
	 * or -1 if none runs.
	 */
	public static float effect(String name, float partialTick) {
		Effect effect = EFFECTS.get(name);

		if (effect == null) {
			return -1.0F;
		}

		float p = (clientTicks - effect.start() + partialTick) / Math.max(1, effect.ticks());
		return p > 1.0F ? -1.0F : Math.max(0.0F, p);
	}

	/** Phase 26: where an effect runs (world space), empty if none. */
	public static List<Vec3> effectPoints(String name) {
		Effect effect = EFFECTS.get(name);
		return effect != null ? effect.points() : List.of();
	}

	/** Phase 26, for tests: how many effects were shown. */
	public static int effectsSeenForTest() {
		return effectsSeen;
	}

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
	/** Phase 24: how bad the reactor is (last heard), when; the lights' flicker. */
	private static int reactorLevel;
	private static long reactorAt = -10000L;
	private static int flickerAge = 1000;
	private static int flickerTicks = 1;
	private static int rumbles;
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
	/**
	 * Phase 25: her thrown saber staff: where it left her hand, its far end, the hand it comes back
	 * to (world), how long out and back (ticks), how long it has flown (half as fast under the
	 * Focus, as on the server); null while none flies.
	 */
	private static @Nullable Vec3 throwFrom;
	private static @Nullable Vec3 throwFar;
	private static @Nullable Vec3 throwHand;
	private static int throwOut;
	private static int throwBack;
	private static float throwAge;
	private static int throwsSeen;
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
	/** Phase 26: when (client ticks) a figure's Inquisitor ring last flared up (the last quarter of the duel). */
	private static final Map<Integer, Long> RING_FLARES = new HashMap<>();
	/** Phase 26: how long such a flare lasts (ticks). */
	static final int RING_FLARE_TICKS = 6;
	/** How far each figure's legs have walked (for the steps). */
	private static final Map<Integer, Float> STRIDES = new HashMap<>();
	private static @Nullable StoryDialogPayload dialog;

	/**
	 * A comlink line (or one overheard nearby): who, the key, how long it shows. Phase 24: and when
	 * it came (client ticks): overheard shouts are moments, gone if they could not be shown soon.
	 */
	public record Line(String who, String key, int ticks, boolean heard, long at) {
	}

	/** Phase 24: an overheard line not shown within this long (ticks) is no longer news: it is dropped. */
	static final int HEARD_STALE = 60;
	/** Phase 24: how long an overheard line shows at most, and when others wait. */
	static final int HEARD_MAX = 110;
	static final int HEARD_SHORT = 45;
	/** Phase 24: at most this many overheard lines wait (the oldest go first). */
	static final int HEARD_QUEUED = 2;
	/** Phase 24: lines dropped (stale, doubled, after a capture): for the tests. */
	private static int linesDropped;

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
		// Phase 31: the keys of the story's screens do nothing else while held.
		StoryKeys.init();
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
		HudElementRegistry.addLast(SaberCraft.id("story"), new StoryHud());
		// Phase 28: Minecraft's status effects (top right) under the mission panel while it shows.
		HudElementRegistry.replaceElement(net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements.MOB_EFFECTS, vanilla -> (graphics, delta) -> {
			int below = topRightReserved();

			if (below <= 0) {
				vanilla.extractRenderState(graphics, delta);
				return;
			}

			graphics.pose().pushMatrix();
			graphics.pose().translate(0.0F, below);
			vanilla.extractRenderState(graphics, delta);
			graphics.pose().popMatrix();
		});
		// Phase 22: no hunger, experience or armour in the story; a condition bar for the hearts.
		StoryVitals.init();
		ClientTickEvents.END_CLIENT_TICK.register(StoryVitals::tick);
	}

	/**
	 * Phase 28: where the story's mission panel at the top right begins (its left edge, GUI x), or
	 * {@code width} when none is shown: hints and toasts keep left of it.
	 */
	public static int objectivesLeftEdge(int width) {
		return width - StoryHud.objectivesWidth(shownState());
	}

	/** Phase 28: where the comlink line's box at the top left ends now (GUI y), 0 when none shows. */
	public static int radioBottom(int width) {
		return shownState() == null ? 0 : StoryHud.radioBottom(Minecraft.getInstance().font, width);
	}

	/**
	 * Phase 28: how far down Minecraft's own things at the top right (its toasts: achievements,
	 * recipes, tutorials; the status effects) move while the story's mission panel stands there,
	 * so they show below it instead of over it: the panel's bottom and a small gap, 0 without one.
	 */
	public static int topRightReserved() {
		StoryStatePayload shown = shownState();
		int bottom = shown == null ? 0 : StoryHud.objectivesBottom(Minecraft.getInstance().font, shown);
		return bottom <= 0 ? 0 : bottom + 4;
	}

	/** The story's state while its HUD is shown (in a story, no film), else null. */
	private static @Nullable StoryStatePayload shownState() {
		Minecraft client = Minecraft.getInstance();
		return state == null || !state.active() || client.player == null || client.level == null || StoryCinema.active() ? null : state;
	}

	static void reset() {
		state = null;
		previous = null;
		current = null;
		dialog = null;
		SEEN.clear();
		StoryKeys.reset();
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
		RING_FLARES.clear();
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
		clearDuelProps();
		EFFECTS.clear();
	}

	/**
	 * Phase 28: her saber lying on the floor after the duel and her thrown staff are drawn from
	 * what the duel's cues told this client; they were forgotten only on leaving the server, so
	 * after a restart, "play mission", a capture or the end of her scene the old saber still lay
	 * there in the same plot. Now forgotten whenever the story starts or ends, at every return to
	 * a checkpoint (the fade) and when her scene is over ("saber_gone").
	 */
	static void clearDuelProps() {
		hiltFrom = null;
		hiltTo = null;
		throwFrom = null;
	}

	/**
	 * Phase 31: nothing of a duel before is left on this client (a fight begun again after a
	 * capture, a restart, a new difficulty, leaving): her first phase (one blade), no Focus, no
	 * blade lock, no ring flaring, no hits, no props, no 50 % scene still showing, the sabers'
	 * poses forgotten. Before, the second phase stayed until she was beaten or the world was left,
	 * so a fight begun again after dying in it showed her staff and both blades from the start.
	 */
	static void resetDuel(Minecraft client) {
		duelPhase = 1;
		focusOn = false;
		focusMeter = -1;
		lockAge = 1000;
		lockTicks = 0;
		RING_FLARES.clear();
		HITS.clear();
		clearDuelProps();
		StoryCinema.stopIf(client, "boss_mid");
		com.phiro.sabercraft.client.interior.MeshAudit.forgetDuel();
		// Phase 32: her ring's whirr ends with the fight (a reset, a capture, the story left).
		com.phiro.sabercraft.client.interior.RingSound.stopAll();
		duelResets++;
	}

	private static int duelResets;

	/** Phase 31, for the tests: how often the duel was reset on this client. */
	public static int duelResetsForTest() {
		return duelResets;
	}

	// --- Receiving ---------------------------------------------------------------------------------

	private static void state(StoryStatePayload payload) {
		boolean was = active();
		StoryStatePayload before = state;
		state = payload;
		received++;

		// Phase 27: when this client came into the story (a start, a load, a continue): what is
		// there then was there already (the Falcon waiting does not rise into view again).
		if (!was && payload.active()) {
			enteredMillis = net.minecraft.util.Util.getMillis();
			resetDuel(Minecraft.getInstance());
		}

		// Phase 24: the section changed, or the alarm is over: the shouts still waiting are old news.
		// (Phase 30: a section is a mission's step, not each objective it shows: see sameStep.)
		if (before != null && payload.active() && (!sameStep(before, payload) || before.alarm() > 0 && payload.alarm() == 0)) {
			dropBefore(clientTicks, true);
		}

		if (!payload.active()) {
			previous = null;
			current = null;
			STRIDES.clear();
			music = null;
			resetDuel(Minecraft.getInstance());

			if (was) {
				RADIO.clear();
				radioNow = null;
				progressKey = null;
				dialog = null;
			}
		}

		StoryMenus.refresh();
	}

	/** Phase 30: the objective of the reactor's escape (Mission 5's first step), and the start of the ones it shows on the way. */
	private static final String ESCAPE_OBJECTIVE = "sabercraft.story.obj.m5.escape";
	private static final String ESCAPE_MOMENTS = ESCAPE_OBJECTIVE + "_";

	/**
	 * Phase 30: whether two states are in the same mission and step. The state says no step, only
	 * the objective shown; the escape out of the reactor shows a new one at each moment of the
	 * chase (unlock the door, it unlocks, bolt it, into the shaft), and with each the troopers'
	 * shouts were dropped: those count as the step's own objective.
	 */
	private static boolean sameStep(StoryStatePayload a, StoryStatePayload b) {
		return a.mission() == b.mission() && stepObjective(a.objective()).equals(stepObjective(b.objective()));
	}

	private static String stepObjective(String objective) {
		return objective.startsWith(ESCAPE_MOMENTS) ? ESCAPE_OBJECTIVE : objective;
	}

	private static void figures(StoryFiguresPayload payload) {
		previous = current != null && current.origin().equals(payload.origin()) ? current : payload;
		current = payload;
		receivedAt = clientTicks;
	}

	private static void dialog(Minecraft client, StoryDialogPayload payload) {
		if (!payload.open()) {
			dialog = null;
			SEEN.clear();

			if (client.gui.screen() instanceof DialogScreen) {
				client.gui.setScreen(null);
			}

			return;
		}

		// Phase 31: the lines of this box, to read again with the arrow keys (a new box starts afresh;
		// the same line sent again, after a wrong answer or a click on the speaker, is kept once).
		if (dialog == null) {
			SEEN.clear();
		}

		if (!SEEN.isEmpty() && SEEN.getLast().line().line().equals(payload.line())) {
			SEEN.set(SEEN.size() - 1, new Seen(payload, -1));
		} else {
			SEEN.add(new Seen(payload, -1));

			if (SEEN.size() > SEEN_MAX) {
				SEEN.removeFirst();
			}
		}

		dialog = payload;
		// Phase 25: a short scene still running here gives way to the talk it leads to.
		StoryCinema.sceneOver(client);
		DialogScreen.open(client, payload);
	}

	private static void cue(Minecraft client, StoryCuePayload payload) {
		switch (payload.cue()) {
			case CUE_RADIO -> {
				int length = client.font != null ? net.minecraft.network.chat.Component.translatable(payload.text()).getString().length() : 60;
				boolean heard = payload.value() == 1;

				if (!radio(new Line(payload.who(), payload.text(),
						heard ? Mth.clamp(length * RADIO_PER_CHAR + 30, RADIO_MIN - 20, HEARD_MAX) : Mth.clamp(length * RADIO_PER_CHAR + 40, RADIO_MIN, RADIO_MAX), heard,
						clientTicks))) {
					return;
				}

				// Overheard in the room: no comlink click, only the soft voice.
				if (heard) {
					sound(client, "story.blip", 0.12F, 0.8F, null);
				} else {
					sound(client, "story.radio", 0.55F, 1.0F, null);
				}
			}
			case CUE_DUEL -> duel(client, payload.who(), payload.value(), payload.points());
			case CUE_HINT -> {
				// Phase 31: a tip of the story goes on the card at the left (not over the hotbar).
				if ("tip".equals(payload.who())) {
					com.phiro.sabercraft.client.Hints.storyTip(client, payload.text());
					return;
				}

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
				// Phase 24: so are the shouts and lines still waiting from before it (no "Halt!" after
				// being caught); what is said with the black (the same moment) stays.
				dropBefore(clientTicks, false);
				// Phase 25: and a short scene aboard (caught in the middle of it: back at the checkpoint).
				StoryCinema.sceneOver(client);
				// Phase 26: and whatever effect ran (a field stuttering).
				EFFECTS.clear();
				// Phase 28: and her saber on the floor, her staff in flight (back at a checkpoint).
				// (Phase 31: and everything else of the duel: her second phase, the Focus, the lock.)
				// (Phase 32: not for the black moment of being set back into the sealed hangar: the
				// duel goes on as it was.)
				if (!com.phiro.sabercraft.story.Story.FADE_KEEPS_DUEL.equals(payload.who())) {
					resetDuel(client);
				}
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
				// Phase 28: a mission begins (a start, a restart, a mission played again): nothing
				// of an earlier duel is left lying in the ship (Phase 31: nor of the duel itself).
				resetDuel(client);
				// Phase 30: nor a progress bar from before it (the download's finished "100 %" stood
				// under Mission 5's title); the bar is no longer hidden under a title card, so one
				// begun under it (the maintenance door's release) shows.
				progressKey = null;
				progressValue = 0;
			}
			case CUE_NOISE -> {
				noise = List.copyOf(payload.points());
				noiseTicks = Math.max(20, payload.value());
				noiseAge = 0;
				noisesMade++;
			}
			case CUE_CHECKPOINT -> {
				checkpointAge = 0;
				// Phase 24: a new section: what was shouted before it is over.
				dropBefore(clientTicks, true);
			}
			case CUE_CINEMA -> StoryCinema.start(client, payload.who(), payload.value(), payload.points());
			case CUE_INTERRUPT -> interrupt(client);
			case CUE_EFFECT -> {
				EFFECTS.put(payload.who(), new Effect(clientTicks, Math.max(1, payload.value()), List.copyOf(payload.points())));
				effectsSeen++;

				// Phase 28: the side way's cover, with the screwdriver: its close-up (the four screws).
				if ("cover".equals(payload.who())) {
					CoverScreen.open(client);
				}
			}
			case CUE_END -> {
				ended = true;
				endAge = 0;
			}
			default -> {
			}
		}
	}

	/**
	 * Phase 27: seen, the alarm, hit or caught: whatever screen of the game's own is open closes (a
	 * dialog, the mod's menu, the inventory), so the player can act at once. The pause menu, the
	 * chat and a film are left alone.
	 */
	static void interrupt(Minecraft client) {
		interrupts++;
		net.minecraft.client.gui.screens.Screen screen = client.gui.screen();

		if (screen instanceof DialogScreen || screen instanceof CoverScreen || screen instanceof com.phiro.sabercraft.client.menu.SaberMenuScreen) {
			dialog = null;
			SEEN.clear();
			client.gui.setScreen(null);
		} else if (screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> && client.player != null) {
			client.player.closeContainer();
		}
	}

	private static int interrupts;

	/** For the tests: how often the story told this client to close its screens. */
	public static int interruptsForTest() {
		return interrupts;
	}

	/** Phase 31: a line of the open dialog box as it was shown, and the answer chosen there (-1: none). */
	record Seen(StoryDialogPayload line, int chosen) {
	}

	/** Phase 31: the lines of the open box so far (the last is the one the server waits on). */
	private static final java.util.List<Seen> SEEN = new java.util.ArrayList<>();
	private static final int SEEN_MAX = 64;

	static java.util.List<Seen> seen() {
		return SEEN;
	}

	/** Phase 31: the answer given to the last line (shown when it is read again). */
	static void chose(int answer) {
		if (!SEEN.isEmpty()) {
			SEEN.set(SEEN.size() - 1, new Seen(SEEN.getLast().line(), answer));
		}
	}

	/** Phase 31: the box was closed here (it gave up waiting): its lines are gone. */
	static void forgetSeen() {
		SEEN.clear();
	}

	/** Phase 27: the dialog the server has open (null: none), for a film that hid its screen. */
	static @Nullable StoryDialogPayload openDialog() {
		return dialog;
	}

	/**
	 * Phase 24: a line comes: not if the same is showing or waiting already; the story's comlink
	 * lines before the overheard ones; at most a few overheard ones waiting. Returns whether it is kept.
	 */
	private static boolean radio(Line line) {
		if (radioNow != null && radioNow.key().equals(line.key()) && radioNow.who().equals(line.who())) {
			linesDropped++;
			return false;
		}

		for (Line waiting : RADIO) {
			if (waiting.key().equals(line.key()) && waiting.who().equals(line.who())) {
				linesDropped++;
				return false;
			}
		}

		if (line.heard()) {
			long queued = RADIO.stream().filter(Line::heard).count();

			while (queued >= HEARD_QUEUED) {
				for (java.util.Iterator<Line> it = RADIO.iterator(); it.hasNext();) {
					if (it.next().heard()) {
						it.remove();
						linesDropped++;
						break;
					}
				}

				queued--;
			}

			RADIO.addLast(line);
			return true;
		}

		// The story's own lines go before the overheard ones still waiting.
		java.util.List<Line> heard = new java.util.ArrayList<>();

		while (!RADIO.isEmpty() && RADIO.peekLast().heard()) {
			heard.add(0, RADIO.pollLast());
		}

		RADIO.addLast(line);
		RADIO.addAll(heard);
		return true;
	}

	/**
	 * Phase 24: what came before that moment is over: after a capture's black every line still
	 * waiting (and an overheard one showing); at a checkpoint, a new section or the alarm's end only
	 * the shouts of the chase ({@code chaseOnly}).
	 */
	private static void dropBefore(long when, boolean chaseOnly) {
		int before = RADIO.size();
		RADIO.removeIf(line -> line.at() < when && (!chaseOnly || chase(line)));
		linesDropped += before - RADIO.size();

		if (radioNow != null && radioNow.at() < when && (chaseOnly ? chase(radioNow) : radioNow.heard())) {
			radioNow = null;
			linesDropped++;
		}
	}

	/** Phase 24: the crew's shouts of a chase ("Halt!", "Intruder!"): news only while it goes on. */
	private static final java.util.Set<String> CHASE_SHOUTS = java.util.Set.of("halt", "officer_halt", "intruder", "officer_call", "hands_up", "alarm_call",
			"mouse_again");

	private static boolean chase(Line line) {
		return line.heard() && line.key().startsWith("sabercraft.story.shout.") && CHASE_SHOUTS.contains(line.key().substring("sabercraft.story.shout.".length()));
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
			case "fierce" -> {
				// Phase 26: the last quarter of her strength: her ring spins up, the blades flare, the view jolts.
				RING_FLARES.put(value, clientTicks);
				shake(1.0F, 9);
				flash(0x00FFD0C0, 0);
			}
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
			case "rumble" -> {
				// Phase 24: the failing reactor shakes the ship (harder from section to section), the lights flicker.
				shake(0.3F + 0.22F * value, 10 + 4 * value);
				reactorLevel = value;
				reactorAt = clientTicks;
				flickerAge = 0;
				flickerTicks = 8 + 3 * value;
				rumbles++;
			}
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
			case "slam_wind" -> {
				// Phase 29: her slam winds up: a low rumble in the view, closing in a little (the warning).
				slamsSeen++;
				shake(0.25F, Math.max(6, value));
				zoom(0.025F, Math.max(6, value));
			}
			case "slam" -> {
				// Phase 29: ... and falls: the floor shakes hard, a warm flash.
				shake(2.0F, 14);
				zoom(0.05F, 12);
				flash(0x00FF9A6A, 0);
			}
			// Phase 28: her scene is over (her film ended or was skipped): her saber is gone with her.
			case "saber_gone" -> clearDuelProps();
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
			case "strained" -> {
				// Phase 28: blocked, but the guard shakes (little stamina left): a harder jolt, a warm flash.
				blockAge = 0;
				strains++;
				shake(0.9F, 7);
				flash(0x00FFB48C, 0);
			}
			case "guard_break" -> {
				// Phase 28: the guard broken (kept up too long): the blade knocked aside, the view jolts hard.
				blockAge = 0;
				guardBreaks++;
				shake(1.6F, 11);
				zoom(0.045F, 10);
				flash(0x00FF7A5A, 0);
			}
			case "phase" -> duelPhase = value;
			// Phase 31: the fight begins again (or is left): as a fresh one.
			case "reset" -> resetDuel(client);
			case "throw" -> {
				// Phase 25: her staff thrown at the player.
				if (points.size() >= 3) {
					throwFrom = points.get(0);
					throwFar = points.get(1);
					throwHand = points.get(2);
					throwOut = value / 1000;
					throwBack = value % 1000;
					throwAge = 0.0F;
					throwsSeen++;
				}
			}
			case "caught" -> throwFrom = null;
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
				throwFrom = null;
			}
			default -> {
			}
		}
	}

	/** Phase 26: whether this figure's Inquisitor ring flares up just now (it spins up, its blades blaze). */
	public static boolean ringFlared(int id) {
		Long at = RING_FLARES.get(id);
		return at != null && clientTicks - at < RING_FLARE_TICKS;
	}

	/** Phase 26: how many ring flares were seen (tests). */
	public static int ringFlaresForTest() {
		return RING_FLARES.size();
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

		// Phase 28: the side way's cover close up: the view comes in on it.
		float close = CoverScreen.closeness(partial);

		if (close > 0.0F) {
			scale -= 0.18F * close * close * (3.0F - 2.0F * close);
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

		// Phase 28: only in a running story (never left over in a plot after it).
		if (from == null || to == null || now == null || !now.origin().equals(origin) || !active()) {
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

	/**
	 * Phase 25: her thrown saber staff (plot space of that ship): {x, y, z, yaw, spin} while it
	 * flies, or null.
	 */
	public static float @Nullable [] flyingStaff(BlockPos origin, float partial) {
		Vec3 from = throwFrom;
		Vec3 far = throwFar;
		Vec3 hand = throwHand;

		if (from == null || far == null || hand == null) {
			return null;
		}

		float t = throwAge + partial * (focusOn ? 0.5F : 1.0F);

		if (t >= throwOut + throwBack) {
			return null;
		}

		// Phase 32: back into her hand where it is drawn now (as the server reckons it each tick).
		for (Pose pose : figures(origin, partial)) {
			if ("shadow".equals(pose.fig().kind())) {
				hand = StaffThrow.hand(new Vec3(origin.getX() + pose.x(), origin.getY() + pose.y(), origin.getZ() + pose.z()), pose.head());
				break;
			}
		}

		Vec3 at = StaffThrow.at(from, far, hand, throwOut, throwBack, t);
		float yaw = StaffThrow.yaw(from, far, hand, throwOut, throwBack, t);
		return new float[] {(float) (at.x - origin.getX()), (float) (at.y - origin.getY()), (float) (at.z - origin.getZ()), yaw, t * StaffThrow.SPIN};
	}

	/** Only for tests: how many throws of her staff were seen, and whether one flies now. */
	public static int throwsSeenForTest() {
		return throwsSeen;
	}

	public static boolean staffFlyingForTest() {
		return throwFrom != null && throwAge < throwOut + throwBack;
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

	/** Phase 28: guards seen shaking and broken in the duel (tests). */
	private static int strains;
	private static int guardBreaks;

	public static int[] guardStrainsForTest() {
		return new int[] {strains, guardBreaks};
	}

	/** Phase 29: her slams seen winding up (tests). */
	private static int slamsSeen;

	public static int slamsSeenForTest() {
		return slamsSeen;
	}

	/** Phase 28: whether a mission's title card shows now (the hint cards wait until it is gone). */
	public static boolean titleShowing() {
		return title != null && shownState() != null;
	}

	/** Phase 28, for the tests: the story's hint over the hotbar now (its key), or null. */
	public static @Nullable String hintForTest() {
		return hint;
	}

	/** Phase 30, for the tests: the progress bar's words now (its key), or null. */
	public static @Nullable String progressKeyForTest() {
		return progressKey;
	}

	/** Phase 28: how long the comlink line now shown has been up (-1 without one), and how many wait. */
	public static int[] radioAgeForTest() {
		return new int[] {radioNow == null ? -1 : radioAge, RADIO.size()};
	}

	static boolean focusOn() {
		return focusOn;
	}

	/** Phase 32: whether the Focus slows the fight now (her spin's whirl is drawn at its pace). */
	public static boolean focusing() {
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

	/**
	 * Phase 24: how dark the flickering lights make it now (0 none .. 1): a few hard dips while the
	 * ship shudders; and how strongly the alarm's red pulses at the screen's edge (the reactor critical).
	 */
	static float flicker(float partial) {
		if (flickerAge >= flickerTicks) {
			return 0.0F;
		}

		float age = flickerAge + partial;
		// Out, on, out, a stutter, on.
		float wave = Mth.sin(age * 2.3F) * Mth.sin(age * 0.9F + 1.0F);
		float fall = 1.0F - age / flickerTicks;
		// Phase 27: shallower dips with the brighter story setting.
		float lift = com.phiro.sabercraft.client.ImmersionConfig.get().storyLift();
		return Mth.clamp(wave * 0.75F * fall + (age < 2.0F ? 0.35F : 0.0F), 0.0F, 0.7F) * (1.0F - lift * 0.7F);
	}

	static float reactorPulse(float partial) {
		if (reactorLevel < 2 || clientTicks - reactorAt > 400L) {
			return 0.0F;
		}

		float t = clientTicks + partial;
		return (0.5F + 0.5F * Mth.sin(t * 0.21F)) * (0.08F + 0.04F * reactorLevel);
	}

	/** Only for tests: how often the ship shuddered, how bad the reactor is. */
	public static int rumblesForTest() {
		return rumbles;
	}

	public static int reactorLevelForTest() {
		return reactorLevel;
	}

	/** Phase 24: whether her saber is a staff now (the second phase of the duel: its second blade). */
	public static boolean twinBlade() {
		return duelPhase >= 2;
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

		if (throwFrom != null) {
			throwAge += focusOn ? 0.5F : 1.0F;

			if (throwAge >= throwOut + throwBack + 4) {
				throwFrom = null;
			}
		}
		focusAge++;
		hurtAge++;
		blockAge++;
		stunAge++;
		meterAge++;
		shakeAge++;
		zoomAge++;
		lockAge++;
		creakAge++;
		flickerAge++;
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

		// Phase 24: an overheard shout that could not be shown soon is no longer news.
		int waiting = RADIO.size();
		RADIO.removeIf(line -> line.heard() && clientTicks - line.at() > HEARD_STALE);
		linesDropped += waiting - RADIO.size();

		// Phase 28: while the cover's screws are turned (that screen fills the middle and its top), a
		// comlink line waits: one shown goes on afterwards, a new one comes after it.
		boolean holdRadio = Minecraft.getInstance().gui.screen() instanceof CoverScreen;

		if (radioNow != null && !holdRadio) {
			radioAge++;
			// Phase 24: an overheard shout makes room soon for what waits (the story's lines first).
			boolean others = !RADIO.isEmpty();
			int most = radioNow.heard() && others ? Math.min(radioNow.ticks(), HEARD_SHORT) : radioNow.ticks();

			if (radioAge >= most) {
				radioNow = null;
			}
		}

		if (radioNow == null && !RADIO.isEmpty() && !holdRadio) {
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
	/** Phase 27: when this client came into the story (milliseconds of {@link net.minecraft.util.Util#getMillis}). */
	private static long enteredMillis = -100_000L;

	public static long enteredMillis() {
		return enteredMillis;
	}

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

	/** Phase 26: whether that plot is this player's story Star Destroyer. */
	public static boolean destroyerPlot(BlockPos origin) {
		StoryStatePayload s = state;
		return s != null && s.active() && origin.equals(s.destroyer());
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

		// Phase 27: the setting that brightens the story's darkening (less dark, the colours kept).
		float lift = com.phiro.sabercraft.client.ImmersionConfig.get().storyLift();

		if (alarm >= 2) {
			float speed = alarm >= 3 ? 1.1F : 0.45F;
			float pulse = 0.5F + 0.5F * Mth.sin(seconds * speed * Mth.TWO_PI);
			float depth = (alarm >= 3 ? 0.55F : 0.3F) * (1.0F - lift * 0.6F);
			light[1] *= 1.0F - depth * pulse * 0.6F;
			light[2] *= 1.0F - depth * pulse * 0.7F;
			light[4] *= 1.0F - depth * pulse;
			light[5] *= 1.0F - depth * pulse;
			light[3] = Math.min(1.25F, light[3] * (1.0F + 0.25F * pulse));
		}

		if (lift > 0.0F) {
			// The surfaces' colours a little towards white (the warm, red tint stays), their
			// brightness and the lamps' further.
			for (int i : new int[] {0, 1, 2}) {
				light[i] += (1.0F - light[i]) * lift * 0.5F;
			}

			for (int i : new int[] {4, 5}) {
				light[i] += (1.0F - light[i]) * lift * 0.6F;
			}

			light[6] += (1.0F - light[6]) * lift;
		}

		return light;
	}

	/** Phase 27: the story's difficulty as the server keeps it (0 easy, 1 normal, 2 hard). */
	public static int difficulty() {
		return has("difficulty.easy") ? 0 : has("difficulty.hard") ? 2 : 1;
	}

	/** Phase 27: how many stun bolts catch the player at this difficulty (as the server's StoryDifficulty). */
	static int stunMax() {
		return switch (difficulty()) {
			case 0 -> 4;
			case 2 -> 2;
			default -> 3;
		};
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

	/**
	 * Phase 24: how wary the people near the player are (for the music): the highest stage of anyone
	 * watching within that many blocks (0 calm, 1 suspicious, 2 alerted, 3 detected).
	 */
	public static int wariness(double range) {
		StoryFiguresPayload now = current;
		Minecraft client = Minecraft.getInstance();

		if (now == null || client.player == null || !active()) {
			return 0;
		}

		int most = 0;
		double x = client.player.getX() - now.origin().getX();
		double y = client.player.getY() - now.origin().getY();
		double z = client.player.getZ() - now.origin().getZ();

		for (StoryFiguresPayload.Fig fig : now.figures()) {
			if ((fig.flags() & StoryFiguresPayload.OBSERVER) != 0 && !"down".equals(fig.pose()) && Math.abs(fig.y() - y) < 6.0
					&& (fig.x() - x) * (fig.x() - x) + (fig.z() - z) * (fig.z() - z) < range * range) {
				most = Math.max(most, fig.stage());
			}
		}

		return most;
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

	/** Phase 32, for the tests: whether the chapter's end shows, and since how many ticks. */
	public static boolean endedForTest() {
		return ended;
	}

	public static int endAgeForTest() {
		return endAge;
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

	/** Phase 24: for the tests: lines dropped (doubled, stale, from before a capture) and lines waiting. */
	public static int linesDroppedForTest() {
		return linesDropped;
	}

	public static int linesWaitingForTest() {
		return RADIO.size() + (radioNow != null ? 1 : 0);
	}

	/** Phase 24: for the tests: a line as if the server had sent it (heard: overheard). */
	public static void lineForTest(String who, String key, boolean heard) {
		radio(new Line(who, key, heard ? HEARD_MAX : RADIO_MAX, heard, clientTicks));
	}

	public static @Nullable Line radioForTest() {
		return radioNow != null ? radioNow : RADIO.peek();
	}

	public static int figuresForTest() {
		StoryFiguresPayload now = current;
		return now != null ? now.figures().size() : 0;
	}
}
