package com.phiro.sabercraft.story;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Phase 19: one player playing the story now: where their story ships are, who is in them and
 * what they are doing, the mission and step, what is known (since the last checkpoint), the alarm,
 * the open dialog. The chapter's scripts change the story through this.
 *
 * <p>Phase 20: the session plays its own mission and step; the progress holds the last checkpoint
 * (the state at one moment and a safe place). A checkpoint is made by {@link #checkpoint}; after
 * a capture, a relog or a restart {@link #begin} with {@code restore} puts everything back as it
 * was then: what is known, the journal, the people at their posts, the ship (lights, force
 * fields, hatches), the droids, and the player at the safe place.
 */
public final class StorySession {
	public final UUID player;
	public final MinecraftServer server;
	public final StoryProgress progress;
	public final Chapter chapter;
	/** The corners of the player's own story ships. */
	public final BlockPos falcon;
	public final BlockPos destroyer;

	/** Phase 20: the mission and the step being played now (the progress has the last checkpoint's). */
	public int mission;
	public int stepIndex;
	/** Phase 20: checkpoints made in this session (tests count them), and the state at the last one. */
	public int checkpoints;
	private String saved = "";
	/** Phase 20: restored to a checkpoint this many times in this session (tests count them). */
	public int restores;

	/** Everyone in both ships. */
	public final List<Figure> figures = new ArrayList<>();
	private int nextId = 1;
	/** What is known and done (committed to the progress at each checkpoint). */
	public final Set<String> flags = new LinkedHashSet<>();
	public final List<String> journal = new ArrayList<>();

	/** The ship the player is in now ({@link Story#FALCON}, {@link Story#DESTROYER}), or null. */
	public @Nullable String here;
	/** Ticks since the step began. */
	public int stepTicks;
	/** Timers of the chapter's scripts, by name (ticks left). */
	public final Map<String, Integer> timers = new HashMap<>();
	/** The music last asked of the client (sent again only when it changes). */
	public String music = "";
	/** Counters of the chapter's scripts, by name (a download's progress, how long one listened). */
	public final Map<String, Integer> counters = new HashMap<>();

	/** The alarm: ticks left (0 = none); and whether the ship stays on alert for the rest of the mission. */
	public int alarm;
	public boolean alert;
	/** Ticks since anyone last saw the player during an alarm. */
	public int unseen;
	/** Phase 20: a chaser reached the player ("Hands up!"): ticks until the capture, and who. */
	public int cornered;
	public @Nullable Figure cornerer;
	/** Wearing the armour (a disguise), and whether it is known to the enemy (then it helps little). */
	public boolean disguised;
	public boolean burned;
	/** After a capture: a few seconds before anyone can see the player again. */
	public int grace;
	/**
	 * Phase 31: the player was struck down (a lethal hit): they are caught at the start of the next
	 * tick, not in the middle of the code that hit them (which then went on with the old fight
	 * against the restored checkpoint: a knock-back after the teleport, the old fight's cues).
	 */
	boolean captureDue;
	/**
	 * Phase 27: back at a checkpoint, the player gets their bearings: while they stay near where
	 * they were put (and do nothing there), nobody sees them, for at most {@link #SHIELD} ticks.
	 * Not kept: only for this restore.
	 */
	public int shield;
	public @Nullable Vec3 shieldAt;
	/** Phase 27: when (the player's ticks) the barrier before the bridge's lift last told why it stands there. */
	int bridgeHint = -100_000;
	/**
	 * Phase 21: in a chase, when anyone hunting the player last saw them (game time) and where
	 * (plot space): they tell each other over the comlink, so the others come there too.
	 */
	public long huntSeen = -1000L;
	public @Nullable Vec3 huntAt;
	/** Phase 21: stun bolts that hit the player in this chase (three, and they are caught). */
	public int stuns;
	/** Phase 21: the search after a chase was announced (sirens off, "search the deck"). */
	public boolean searching;
	/** Phase 21: the Focus (the duel's slowed time): how full it is (0..1) and how long it still lasts (ticks). */
	public float focus;
	public int focusTicks;

	/** The open dialog and the line in it, or null. */
	public @Nullable Dialog dialog;
	public @Nullable String line;
	/** The dialog's speaker (a figure held while they talk), or null. */
	public @Nullable Figure partner;
	/** What the partner was doing before the dialog (they go back to it after). */
	private int partnerWas = Figure.ROUTINE;

	/** A step to complete as soon as the open dialog is closed (said in a dialog, done after it), or null. */
	public @Nullable String pending;
	/** Phase 27: where the marker pointed when the state was last sent (world space), or null. */
	@Nullable Vec3 markerSent;

	/** What the client should be told again: the state, the dialog. */
	public boolean dirty = true;
	/** Whether the step is to be completed after this tick (set by scripts). */
	private boolean advance;
	/** The step's id, to notice it changed. */
	private String stepId = "";
	/** The highest suspicion of anyone now, for the HUD (0..1), and who. */
	public float suspicion;
	public @Nullable Figure watcher;
	/** Positions shown by a droid's scan (plot space), and until when (game time). */
	public List<Vec3> scanned = List.of();
	public long scanUntil;
	/** Phase 20: what the droid's message of the chapter names (who went to look), or none. */
	public Object[] droidArgs = new Object[0];
	/** The finale: the player's lightsaber is out (the stealth rules no longer apply). */
	public boolean fighting;
	/** Phase 23: when the player last struck with the saber (story time): a pause makes the next a heavy stroke. */
	long lastStroke = -1000L;
	/** Phase 26: when a trooper of the finale last fired (two never fire within a few ticks of each other). */
	long lastVolley = -1000L;
	/** Phase 26: where the player is held during the boss's blade lock scene (world space), or null. */
	@org.jspecify.annotations.Nullable Vec3 sceneAt;
	/** Phase 23: where the Schattenklinge's saber lies after the duel (plot space), or null. */
	@Nullable Vec3 dropped;

	StorySession(UUID player, MinecraftServer server, StoryProgress progress, Chapter chapter, BlockPos falcon, BlockPos destroyer) {
		this.player = player;
		this.server = server;
		this.progress = progress;
		this.chapter = chapter;
		this.falcon = falcon;
		this.destroyer = destroyer;
		this.mission = progress.mission;
		this.stepIndex = progress.step;
		this.flags.addAll(progress.flags);
		this.journal.addAll(progress.journal);
		this.saved = this.state();
	}

	public @Nullable ServerPlayer player() {
		return this.server.getPlayerList().getPlayer(this.player);
	}

	public long time() {
		return this.server.overworld().getGameTime();
	}

	// --- Missions and steps ------------------------------------------------------------------------

	public Mission mission() {
		List<Mission> missions = this.chapter.missions();
		return missions.get(Math.max(0, Math.min(this.mission, missions.size() - 1)));
	}

	public Mission.Step step() {
		Mission mission = this.mission();
		return mission.steps().get(Math.max(0, Math.min(this.stepIndex, mission.steps().size() - 1)));
	}

	/** Whether the current step is that one. */
	public boolean at(String step) {
		return this.step().id().equals(step);
	}

	/** Whether the mission is that one ({@code m1}..). */
	public boolean in(String mission) {
		return this.mission().id().equals(mission);
	}

	/** Ends the current step (after this tick): on to the next, or the mission is done. */
	public void complete() {
		this.advance = true;
	}

	/** Whether the step was completed this tick (scripts ask before doing more). */
	public boolean completing() {
		return this.advance;
	}

	/** Called by the story after each tick: goes on to the next step if one was completed. */
	void afterTick() {
		if (this.advance) {
			this.advance = false;
			Story.advance(this);
		}
	}

	/** Ticks nobody sees the player after being put back at a checkpoint. */
	static final int GRACE = 100;
	/** Phase 27: how long the bearings last at most (ticks), and how far one may move meanwhile (blocks). */
	static final int SHIELD = 400;
	static final double SHIELD_REACH = 3.0;

	/**
	 * Starts the current step. Going on ({@code restore} false): the people stay where they are,
	 * and if the step has a checkpoint of its own, one is made. Back to the last checkpoint
	 * ({@code restore} true: after a capture, a relog, a restart): its mission, step, knowledge and
	 * journal, everyone at their post, the ship as the story has it then, the player at its safe
	 * place with the droids, and a few seconds before anyone can see them.
	 */
	void begin(boolean restore) {
		if (restore) {
			this.mission = this.progress.mission;
			this.stepIndex = this.progress.step;
		}

		Mission.Step step = this.step();
		this.stepId = step.id();
		this.stepTicks = 0;
		this.timers.clear();
		this.counters.clear();

		// Phase 20: an alarm goes on into the next step (a script may finish one while they search);
		// a checkpoint ends it.
		if (restore) {
			this.alarm = 0;
			this.unseen = 0;
			// Phase 24: nobody is after one back at a checkpoint (and the shouts start afresh).
			this.searching = false;
			this.huntSeen = -1000L;
			this.huntAt = null;
			this.shouts.clear();
			this.lastShout = -1000L;
		}

		this.cornered = 0;
		this.cornerer = null;
		this.stuns = 0;
		this.focusTicks = 0;
		// Phase 27: a dialog still open ends here for the player too (it was dropped silently, and
		// the player's screen stayed open with nobody answering it).
		boolean talking = this.dialog != null;
		this.release();
		this.dialog = null;
		this.line = null;
		this.partner = null;
		this.pending = null;

		if (talking) {
			Story.sendDialog(this);
		}
		this.scanned = List.of();
		this.dropped = null;
		this.lastStroke = -1000L;
		// Phase 23: whether a fight went on into this step (those drawn into it stay while it lasts).
		boolean fought = this.fighting;
		this.fighting = false;

		List<Figure> carried = List.of();

		if (restore) {
			this.flags.clear();
			this.flags.addAll(this.progress.flags);
			this.journal.clear();
			this.journal.addAll(this.progress.journal);
			this.rebuildFigures();
		} else {
			carried = this.syncFigures(fought);
		}

		this.disguised = this.flags.contains("wear.armour");
		this.burned = this.flags.contains("armour.burned");
		this.alert = this.flags.contains("ship.alert");

		if (restore) {
			this.restores++;
			// Phase 27: the hatches shut first, then the player is put there (the other way round,
			// a place in a hatch's opening became a wall around them, and the game pushed them out
			// into the corridor beyond, in front of its patrol).
			Story.resetShip(this);
			Story.placeAtCheckpoint(this, step);
			Story.clearRespawn(this);
			// Phase 31: nothing of a fight before is carried over (the boss in her first phase again,
			// the player's stamina and guard afresh, the client back to one blade).
			this.captureDue = false;
			Finale.resetFight(this);
		}

		this.chapter.begin(this, step, restore);

		// Phase 23: no fight in this step after all: who stayed only for the fight goes now.
		if (!this.fighting && !carried.isEmpty()) {
			this.figures.removeAll(carried);
		}

		if (restore) {
			this.grace = Math.max(this.grace, GRACE);
			// Phase 27: time to get one's bearings where one was put (not in a staged chase or fight).
			boolean shielded = !this.chapter.scriptedRespawn(this, step);
			this.shield = shielded ? SHIELD : 0;
			this.shieldAt = shielded ? this.local(this.here) : null;
			this.chapter.droids(this);
			this.saved = this.state();
		} else if (this.chapter.checkpoint(this, step)) {
			Mission.Place place = this.chapter.place(this, step);
			this.checkpoint(place.ship(), place.at(), place.yaw());
		}

		this.dirty = true;
	}

	/**
	 * Phase 20: makes a checkpoint now: the mission and step, what is known (not the scripts'
	 * passing marks, {@code tmp.*}), the journal, and the safe place the player is put back at.
	 * Those walking off are gone from now on (the chapter settles them first).
	 */
	public void checkpoint(String ship, Vec3 place, float yaw) {
		this.chapter.settle(this);
		this.progress.mission = this.mission;
		this.progress.step = this.stepIndex;
		this.progress.stepId = this.step().id();
		this.progress.flags.clear();

		for (String flag : this.flags) {
			if (!flag.startsWith("tmp.")) {
				this.progress.flags.add(flag);
			}
		}

		this.progress.journal.clear();
		this.progress.journal.addAll(this.journal);
		this.progress.placeShip = ship;
		this.progress.placeX = place.x;
		this.progress.placeY = place.y;
		this.progress.placeZ = place.z;
		this.progress.placeYaw = yaw;
		this.progress.format = StoryProgress.FORMAT;
		Story.save(this);
		this.checkpoints++;
		this.saved = this.state();
		// A short notice: from here a capture brings one back.
		Story.cue(this, Story.CUE_CHECKPOINT, "", "", 0);
	}

	/** Phase 20: what a checkpoint keeps (to tell whether a new one would change anything). */
	String state() {
		StringBuilder out = new StringBuilder().append(this.mission).append('/').append(this.stepIndex).append('/');

		for (String flag : new java.util.TreeSet<>(this.flags)) {
			if (!flag.startsWith("tmp.")) {
				out.append(flag).append(',');
			}
		}

		return out.append('/').append(String.join(",", this.journal)).toString();
	}

	/** Phase 20: whether something kept by checkpoints changed since the last one. */
	public boolean unsaved() {
		return !this.saved.equals(this.state());
	}

	/**
	 * Phase 20: leaving the story while all is calm keeps how far one got (at the step's safe
	 * place, as a checkpoint); during an alarm, a chase or a fight the last checkpoint stays (so
	 * leaving is no way out of being caught).
	 */
	void keepOnLeave() {
		// Phase 31: (and not while breaking out of Hale's cell: its checkpoint is the cell's.)
		if (this.progress.finished || this.alarm > 0 || this.fighting || this.completing() || this.chapter.holdsCheckpoint(this)) {
			return;
		}

		for (Figure figure : this.figures) {
			if (figure.mode == Figure.CHASE || figure.stage >= Figure.ALERTED) {
				return;
			}
		}

		if (this.unsaved()) {
			Mission.Place place = this.chapter.place(this, this.step());
			this.checkpoint(place.ship(), place.at(), place.yaw());
		}
	}

	// --- What is known -----------------------------------------------------------------------------

	public boolean has(String flag) {
		return this.flags.contains(flag);
	}

	public void set(String flag) {
		if (this.flags.add(flag)) {
			this.dirty = true;
		}
	}

	public void unset(String flag) {
		if (this.flags.remove(flag)) {
			this.dirty = true;
		}
	}

	/** Adds a datapad or recording to the journal (once). */
	public void note(String entry) {
		if (!this.journal.contains(entry)) {
			this.journal.add(entry);
			this.dirty = true;
		}
	}

	/** A timer: whether it is running. */
	public boolean timer(String name) {
		return this.timers.getOrDefault(name, 0) > 0;
	}

	public void timer(String name, int ticks) {
		this.timers.put(name, ticks);
	}

	// --- People ------------------------------------------------------------------------------------

	/** Someone by their story name (or {@code crew:<n>}), or null. */
	/** Phase 22: someone by their number (as the client and the bolts know them), or {@code null}. */
	public @Nullable Figure figureById(int id) {
		for (Figure figure : this.figures) {
			if (figure.id == id) {
				return figure;
			}
		}

		return null;
	}

	public @Nullable Figure figure(String key) {
		for (Figure figure : this.figures) {
			if (figure.key.equals(key)) {
				return figure;
			}
		}

		return null;
	}

	/** Puts everyone where the chapter has them now (after a capture: everything as at the step's start). */
	void rebuildFigures() {
		this.figures.clear();
		long time = this.time();

		for (Figure figure : this.chapter.figures(this)) {
			this.figures.add(this.numbered(figure, time));
		}
	}

	/**
	 * Phase 20: someone who comes (reinforcements for an alarm): numbered and added; they stay
	 * while the steps go on, and leave by their door once it is calm (a checkpoint has them gone).
	 */
	Figure add(Figure template) {
		Figure figure = this.numbered(template, this.time());
		this.figures.add(figure);
		this.dirty = true;
		return figure;
	}

	/** Phase 20: whether the player could see that one now (in front, near enough, nothing in between). */
	boolean seenBy(Figure figure) {
		ServerPlayer player = this.player();
		net.minecraft.server.level.ServerLevel level = this.server.getLevel(com.phiro.sabercraft.interior.ShipInteriors.DIMENSION);

		if (player == null || level == null || this.here == null || !this.here.equals(figure.ship)) {
			return false;
		}

		Vec3 eye = player.getEyePosition();
		Vec3 them = figure.eye().add(Vec3.atLowerCornerOf(this.origin(figure.ship)));
		Vec3 to = them.subtract(eye);

		if (to.length() > 32.0 || to.normalize().dot(player.getLookAngle()) < 0.3) {
			return false;
		}

		return Stealth.clear(level, eye, them);
	}

	/**
	 * Keeps those still there as they are, adds who comes, removes who goes. Phase 23: with
	 * {@code fought} (a fight went on into the new step) those drawn into it stay as fighters;
	 * returns those who stayed only for that.
	 */
	List<Figure> syncFigures(boolean fought) {
		long time = this.time();
		List<Figure> wanted = this.chapter.figures(this);
		List<Figure> carried = new ArrayList<>();
		Map<String, Figure> now = new HashMap<>();

		for (Figure figure : this.figures) {
			now.put(figure.key, figure);
		}

		this.figures.clear();

		for (Figure figure : wanted) {
			Figure kept = now.get(figure.key);

			if (kept != null) {
				// Their routine (and who they are) may have changed with the step.
				kept.crew = figure.crew;
				kept.speaker = figure.speaker;
				kept.observer = figure.observer;
				kept.lamp = figure.lamp;
				kept.talks = figure.talks;
				// Phase 23: one drawn into the fight stays a fighter while it lasts.
				kept.hostile = figure.hostile || kept.engaged && kept.hostile && fought && kept.mode != Figure.DOWN;
				kept.saber = figure.saber;
				kept.sight = figure.sight;
				kept.ship = figure.ship;
				// Phase 27: how far they hear, and whether they are a sentry, may change with the step too.
				kept.hearing = figure.hearing;
				kept.sentry = figure.sentry;
				this.figures.add(kept);
			} else {
				this.figures.add(this.numbered(figure, time));
			}
		}

		// Phase 20: reinforcements stay until they went back out of their door. Phase 23: so do
		// those drawn into a fight (the crew of the deck a step clears away included) while it lasts.
		for (Figure figure : now.values()) {
			if (this.figures.contains(figure)) {
				continue;
			}

			if (figure.key.startsWith(Stealth.REINFORCEMENT)) {
				this.figures.add(figure);
			} else if (figure.engaged && figure.hostile && fought && figure.mode != Figure.DOWN) {
				this.figures.add(figure);
				carried.add(figure);
			}
		}

		return carried;
	}

	private Figure numbered(Figure template, long time) {
		Figure figure = new Figure(this.nextId++, template.key, template.crew);
		figure.ship = template.ship;
		figure.speaker = template.speaker;
		figure.observer = template.observer;
		figure.lamp = template.lamp;
		figure.talks = template.talks;
		figure.hostile = template.hostile;
		figure.saber = template.saber;
		figure.health = template.health;
		figure.sight = template.sight;
		// Phase 24: a guard at his post holds it; one already overpowered lies where he fell.
		figure.post = template.post;
		// Phase 27: how far they hear, and whether they are a sentry (the lab's guard).
		figure.hearing = template.hearing;
		figure.sentry = template.sentry;
		figure.snapToRoutine(time);

		if (template.mode == Figure.DOWN) {
			figure.mode = Figure.DOWN;
		}

		return figure;
	}

	// --- Talking -----------------------------------------------------------------------------------

	/** Opens a dialog at its first line (with that figure as the one talking, if any). */
	public void talk(String dialog, @Nullable Figure partner) {
		Dialog d = this.chapter.dialog(dialog);

		if (d == null) {
			return;
		}

		if (partner != this.partner) {
			this.release();
			this.partnerWas = partner != null ? partner.mode : Figure.ROUTINE;
		}

		this.dialog = d;
		this.partner = partner;
		this.show(d.first);
	}

	/** The dialog's partner goes back to what they did (unless a script gave them something else to do). */
	private void release() {
		Figure figure = this.partner;

		if (figure != null) {
			figure.talking = false;
		}

		if (figure != null && figure.mode == Figure.HELD && this.partnerWas != Figure.HELD) {
			figure.mode = this.partnerWas == Figure.SCRIPTED || this.partnerWas == Figure.FIGHT ? this.partnerWas : Figure.RETURN;
			figure.target = null;

			// Phase 22: someone on their seat just goes on sitting (no walk back to it).
			if (figure.mode == Figure.RETURN && figure.onSeat()) {
				figure.mode = Figure.ROUTINE;
			}
		}

		this.partner = null;
	}

	/** Shows a line of the open dialog (running what it does), or closes it. */
	void show(String node) {
		Dialog d = this.dialog;
		Dialog.Node line = d != null && !node.isEmpty() ? d.node(node) : null;

		if (line == null) {
			this.release();
			this.dialog = null;
			this.line = null;
			Story.sendDialog(this);
			return;
		}

		this.line = node;

		if (line.effect() != null) {
			line.effect().accept(this);
		}

		// The effect may have opened another dialog.
		if (this.dialog == d && node.equals(this.line)) {
			Story.sendDialog(this);
		}
	}

	/** Goes on in the open dialog at another line (a dialog's own choice, by what is known). */
	public void jump(String node) {
		this.show(node);
	}

	/** The player picked an answer (or went on, -1, on a line without answers). */
	void answer(int index) {
		Dialog d = this.dialog;
		Dialog.Node line = d != null && this.line != null ? d.node(this.line) : null;

		if (d == null || line == null) {
			// Phase 27: no dialog open here (it ended meanwhile): the player's screen closes too,
			// instead of waiting for an answer that never comes.
			Story.sendDialog(this);
			return;
		}

		List<Dialog.Answer> shown = line.shown(this);

		if (shown.isEmpty()) {
			this.show(line.next(this));
			return;
		}

		if (index < 0 || index >= shown.size()) {
			// Phase 27: an answer that is not there (the line changed meanwhile): the line again,
			// so the player's screen does not wait for nothing.
			Story.sendDialog(this);
			return;
		}

		Dialog.Answer answer = shown.get(index);

		if (answer.effect() != null) {
			answer.effect().accept(this);
		}

		if (this.dialog == d) {
			this.show(answer.next());
		}
	}

	/**
	 * Phase 27: the player has to act at once (seen, the alarm goes up, hit, caught, a film
	 * begins): an open dialog ends (as if the player had walked off), and the client closes any
	 * interaction screen still open, so the controls are free. Returns whether a dialog was open.
	 */
	public boolean interrupt(boolean screens) {
		boolean open = this.dialog != null;

		if (open) {
			interrupts++;
			this.show("");
		}

		if (screens) {
			Story.cue(this, Story.CUE_INTERRUPT, "", "", 0);
		}

		return open;
	}

	private static int interrupts;

	/** For the tests: how many dialogs were broken off by the story since the game started. */
	public static int interruptsForTest() {
		return interrupts;
	}

	/** A line over the comlink while playing (a subtitle; nothing stops). */
	public void radio(String speaker, String key) {
		Story.cue(this, Story.CUE_RADIO, speaker, key, 0);
	}

	/** Something said nearby that the player overhears (shown like the comlink, but not as a hologram). */
	public void hear(String speaker, String key) {
		if (key.startsWith(SHOUT) && !this.shoutHeard(key)) {
			return;
		}

		Story.cue(this, Story.CUE_RADIO, speaker, key, 1);
	}

	/** Phase 24: the shouts of the crew and the ship (overheard, top left). */
	static final String SHOUT = "sabercraft.story.shout.";
	/**
	 * Phase 24: the shouts that matter on their own (the ship's announcements, a chaser at hand, the
	 * Schattenklinge): never held back for another's, only the same one is not repeated at once.
	 */
	static final java.util.Set<String> IMPORTANT_SHOUTS = java.util.Set.of(SHOUT + "search", SHOUT + "search_over", SHOUT + "emergency", SHOUT + "lights_on",
			SHOUT + "alarm_call", SHOUT + "hands_up", SHOUT + "shadow_lock", SHOUT + "shadow_phase2", SHOUT + "shadow_defeat", SHOUT + "reactor_critical",
			SHOUT + "evacuate");
	/** Phase 24: how long the same shout is not heard again (ticks): one "Halt!" for a squad, not five. */
	static final int SHOUT_REPEAT = 200;
	/** Phase 24: how long after one of the crew's shouts the next is heard at the earliest (ticks). */
	static final int SHOUT_GAP = 40;
	/** Phase 24: when each shout was last heard (story time), and any of the crew's. */
	private final Map<String, Long> shouts = new HashMap<>();
	private long lastShout = -1000L;
	/** Phase 24: shouts held back so far (for the tests). */
	public int shoutsHeldBack;

	/**
	 * Phase 24: whether a shout is heard now. Not every trooper's "Halt!" makes a line: the same
	 * shout once in ten seconds, and the crew's at most one in two seconds (the important ones, the
	 * ship's announcements, always, but not twice at once).
	 */
	private boolean shoutHeard(String key) {
		long now = this.time();
		boolean important = IMPORTANT_SHOUTS.contains(key);
		Long last = this.shouts.get(key);

		if (last != null && now - last < (important ? SHOUT_GAP : SHOUT_REPEAT) || !important && now - this.lastShout < SHOUT_GAP) {
			this.shoutsHeldBack++;
			return false;
		}

		this.shouts.put(key, now);

		if (!important) {
			this.lastShout = now;
		}

		return true;
	}

	/**
	 * Phase 24: someone is after the player right now: an alarm (or a fight) and the player seen by
	 * those hunting them within the last ten seconds (not while they search, not when an alarm
	 * merely goes on with nobody having seen them).
	 */
	public boolean pursued() {
		return (this.alarm > 0 || this.fighting) && !this.searching && this.time() - this.huntSeen < Stealth.LOSE;
	}

	/** Phase 24: no time for small talk: an alarm (a chase or a search), a fight, a chaser at hand. */
	public boolean danger() {
		return (this.alarm > 0 || this.fighting || this.cornered > 0) && Story.DESTROYER.equals(this.here);
	}

	/** A short message over the hotbar. */
	public void hint(String key) {
		Story.cue(this, Story.CUE_HINT, "", key, 0);
	}

	// --- Places ------------------------------------------------------------------------------------

	public BlockPos origin(String ship) {
		return Story.FALCON.equals(ship) ? this.falcon : this.destroyer;
	}

	/** The player's position in the plot of that ship. */
	public Vec3 local(String ship) {
		ServerPlayer player = this.player();
		return player == null ? Vec3.ZERO : player.position().subtract(Vec3.atLowerCornerOf(this.origin(ship)));
	}

	/** Whether the player is in that ship within a box (plot space). */
	public boolean inside(String ship, net.minecraft.world.phys.AABB box) {
		return ship.equals(this.here) && box.contains(this.local(ship));
	}

	/** Raises the alarm (red lights, everyone looks harder and longer). */
	public void raiseAlarm(int ticks) {
		// Phase 27: searches last longer on Hard, shorter on Easy.
		ticks = this.difficulty().ticks(ticks, this.difficulty().search);

		if (this.alarm <= 0) {
			Story.cue(this, Story.CUE_SOUND, "", "story.alarm", 0);
			// Phase 27: the alarm goes up: no dialog or menu keeps the player standing there.
			this.interrupt(true);
		}

		this.alarm = Math.max(this.alarm, ticks);
		this.dirty = true;
	}

	public boolean alarmed() {
		return this.alarm > 0 || this.alert;
	}

	/** Phase 27: the story's difficulty for this player (kept in the save). */
	public StoryDifficulty difficulty() {
		return StoryDifficulty.of(this.progress.difficulty);
	}

	/** Whether the step changed since the session began it (a script finished it). */
	boolean stepChanged() {
		return !this.stepId.equals(this.step().id());
	}
}
