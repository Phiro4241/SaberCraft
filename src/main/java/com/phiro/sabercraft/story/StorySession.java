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
 */
public final class StorySession {
	public final UUID player;
	public final MinecraftServer server;
	public final StoryProgress progress;
	public final Chapter chapter;
	/** The corners of the player's own story ships. */
	public final BlockPos falcon;
	public final BlockPos destroyer;

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
	/** Wearing the armour (a disguise), and whether it is known to the enemy (then it helps little). */
	public boolean disguised;
	public boolean burned;
	/** After a capture: a few seconds before anyone can see the player again. */
	public int grace;

	/** The open dialog and the line in it, or null. */
	public @Nullable Dialog dialog;
	public @Nullable String line;
	/** The dialog's speaker (a figure held while they talk), or null. */
	public @Nullable Figure partner;
	/** What the partner was doing before the dialog (they go back to it after). */
	private int partnerWas = Figure.ROUTINE;

	/** A step to complete as soon as the open dialog is closed (said in a dialog, done after it), or null. */
	public @Nullable String pending;

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
	/** The finale: the player's lightsaber is out (the stealth rules no longer apply). */
	public boolean fighting;

	StorySession(UUID player, MinecraftServer server, StoryProgress progress, Chapter chapter, BlockPos falcon, BlockPos destroyer) {
		this.player = player;
		this.server = server;
		this.progress = progress;
		this.chapter = chapter;
		this.falcon = falcon;
		this.destroyer = destroyer;
		this.flags.addAll(progress.flags);
		this.journal.addAll(progress.journal);
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
		return missions.get(Math.min(this.progress.mission, missions.size() - 1));
	}

	public Mission.Step step() {
		Mission mission = this.mission();
		return mission.steps().get(Math.min(this.progress.step, mission.steps().size() - 1));
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

	/** Starts the current step: commits what is known (a checkpoint), sets up the ship, puts the player there if asked. */
	void begin(boolean again, boolean teleport) {
		Mission.Step step = this.step();
		this.stepId = step.id();
		this.stepTicks = 0;
		this.timers.clear();
		this.counters.clear();
		this.alarm = 0;
		this.unseen = 0;
		this.dialog = null;
		this.line = null;
		this.partner = null;
		this.pending = null;
		this.scanned = List.of();
		this.fighting = false;

		if (again) {
			// Back to how things were when the step began.
			this.flags.clear();
			this.flags.addAll(this.progress.flags);
			this.journal.clear();
			this.journal.addAll(this.progress.journal);
			this.rebuildFigures();
		} else {
			this.progress.flags.clear();
			this.progress.flags.addAll(this.flags);
			this.progress.journal.clear();
			this.progress.journal.addAll(this.journal);
			this.syncFigures();
		}

		this.disguised = this.flags.contains("wear.armour");
		this.burned = this.flags.contains("armour.burned");
		this.alert = this.flags.contains("ship.alert");
		Story.save(this);

		if (teleport) {
			Story.place(this, step.ship(), step.spawn(), step.yaw());
		}

		this.chapter.begin(this, step, again);
		this.dirty = true;
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

	/** Keeps those still there as they are, adds who comes, removes who goes. */
	void syncFigures() {
		long time = this.time();
		List<Figure> wanted = this.chapter.figures(this);
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
				kept.hostile = figure.hostile;
				kept.saber = figure.saber;
				kept.sight = figure.sight;
				kept.ship = figure.ship;
				this.figures.add(kept);
			} else {
				this.figures.add(this.numbered(figure, time));
			}
		}
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
		figure.snapToRoutine(time);
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

		if (figure != null && figure.mode == Figure.HELD && this.partnerWas != Figure.HELD) {
			figure.mode = this.partnerWas == Figure.SCRIPTED || this.partnerWas == Figure.FIGHT ? this.partnerWas : Figure.RETURN;
			figure.target = null;
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
			return;
		}

		List<Dialog.Answer> shown = line.shown(this);

		if (shown.isEmpty()) {
			this.show(line.next(this));
			return;
		}

		if (index < 0 || index >= shown.size()) {
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

	/** A line over the comlink while playing (a subtitle; nothing stops). */
	public void radio(String speaker, String key) {
		Story.cue(this, Story.CUE_RADIO, speaker, key, 0);
	}

	/** Something said nearby that the player overhears (shown like the comlink, but not as a hologram). */
	public void hear(String speaker, String key) {
		Story.cue(this, Story.CUE_RADIO, speaker, key, 1);
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
		if (this.alarm <= 0) {
			Story.cue(this, Story.CUE_SOUND, "", "story.alarm", 0);
		}

		this.alarm = Math.max(this.alarm, ticks);
		this.dirty = true;
	}

	public boolean alarmed() {
		return this.alarm > 0 || this.alert;
	}

	/** Whether the step changed since the session began it (a script finished it). */
	boolean stepChanged() {
		return !this.stepId.equals(this.step().id());
	}
}
