package com.phiro.sabercraft.story;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.character.OutfitSync;
import com.phiro.sabercraft.droid.DroidEntity;
import com.phiro.sabercraft.droid.DroidType;
import com.phiro.sabercraft.droid.Droids;
import com.phiro.sabercraft.interior.Crew;
import com.phiro.sabercraft.interior.ShipInteriors;
import com.phiro.sabercraft.network.StoryActionPayload;
import com.phiro.sabercraft.network.StoryCuePayload;
import com.phiro.sabercraft.network.StoryDialogPayload;
import com.phiro.sabercraft.network.StoryFiguresPayload;
import com.phiro.sabercraft.network.StoryStatePayload;

/**
 * Phase 19: the story mode. A player enters it from the SaberCraft menu (or with
 * {@code /sabercraft story}); their own things, droids and game mode are put aside and given back
 * when they leave. They play in their own copies of the Falcon and a Star Destroyer (plots of
 * their own in the ship interiors), so nobody else's ship or story is touched. The progress is
 * saved at every checkpoint; one can leave at any time and continue later, start again or play a
 * finished mission again.
 *
 * <p>Nothing new is registered: the people are drawn by the client from what the server sends,
 * the disguise is an outfit, the droids are the mod's droids, a vanilla client simply sees the
 * rooms.
 */
public final class Story {
	public static final String FALCON = "falcon";
	public static final String DESTROYER = "star_destroyer";

	// Cues (StoryCuePayload).
	public static final byte CUE_RADIO = 0;
	public static final byte CUE_HINT = 1;
	public static final byte CUE_FADE = 2;
	public static final byte CUE_SOUND = 3;
	public static final byte CUE_MUSIC = 4;
	public static final byte CUE_SCAN = 5;
	public static final byte CUE_TITLE = 6;
	public static final byte CUE_END = 7;
	/** Phase 20: where a droid's noise was (a mark for a moment), the cinematic arrival, a checkpoint made. */
	public static final byte CUE_NOISE = 8;
	public static final byte CUE_CINEMA = 9;
	public static final byte CUE_CHECKPOINT = 10;
	/** Phase 21: the duel's moments for the HUD (who: hit, hurt, blocked, parried, phase, meter, focus_on/off, over). */
	public static final byte CUE_DUEL = 11;

	/** How far one reaches to use a thing or speak to someone (blocks). */
	static final double REACH = 4.5;
	/** How far people are sent to the client (blocks). */
	static final double SEND_RANGE = 48.0;

	private static final Map<String, Chapter> CHAPTERS = Map.of(Chapter1.ID, Chapter1.INSTANCE);
	private static final Map<UUID, StorySession> SESSIONS = new HashMap<>();
	private static int captures;

	private Story() {
	}

	public static void init() {
		Companions.init();
		ServerTickEvents.END_SERVER_TICK.register(Story::tick);
		ServerPlayNetworking.registerGlobalReceiver(StoryActionPayload.TYPE, (payload, context) -> action(context.player(), payload));
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> joined(handler.getPlayer())));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			StorySession session = SESSIONS.remove(handler.getPlayer().getUUID());

			if (session != null) {
				// Saved at the last checkpoint (Phase 20: or, all calm, how far one got); the next
				// login continues there.
				session.keepOnLeave();
				OutfitSync.override(handler.getPlayer(), null);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> SESSIONS.clear());
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			if (entity instanceof ServerPlayer player && SESSIONS.containsKey(player.getUUID())) {
				// Nobody dies in the story: it counts as being caught.
				player.setHealth(player.getMaxHealth());
				capture(SESSIONS.get(player.getUUID()), null);
				return false;
			}

			return true;
		});
	}

	// --- Who is in the story -----------------------------------------------------------------------

	public static @Nullable StorySession session(UUID player) {
		return SESSIONS.get(player);
	}

	public static boolean playing(Player player) {
		return SESSIONS.containsKey(player.getUUID());
	}

	/** The owner of a player's story ships (a name of its own, so they are never their real ships). */
	public static UUID storyOwner(UUID player) {
		return UUID.nameUUIDFromBytes(("sabercraft:story:" + player).getBytes(StandardCharsets.UTF_8));
	}

	/** The session whose story ship has that plot corner, or null. */
	public static @Nullable StorySession at(BlockPos origin) {
		for (StorySession session : SESSIONS.values()) {
			if (session.falcon.equals(origin) || session.destroyer.equals(origin)) {
				return session;
			}
		}

		return null;
	}

	// --- Entering and leaving ----------------------------------------------------------------------

	/** How to enter: go on where one left off, start the chapter anew, or play a mission again. */
	public enum Start {
		CONTINUE, RESTART, MISSION
	}

	/** Puts a player into the story. Returns a message (why not), or null when they are in. */
	public static @Nullable Component enter(ServerPlayer player, Start start, int mission) {
		MinecraftServer server = player.level().getServer();
		ServerLevel interiors = server.getLevel(ShipInteriors.DIMENSION);

		if (interiors == null) {
			return Component.translatable("sabercraft.story.no_interiors");
		}

		if (player.isSpectator() || !player.isAlive()) {
			return Component.translatable("sabercraft.story.not_now");
		}

		StorySession running = SESSIONS.get(player.getUUID());
		StoryProgress progress = running != null ? running.progress : StoryStore.load(server, player.getUUID());
		Chapter chapter = CHAPTERS.getOrDefault(progress.chapter, Chapter1.INSTANCE);
		int missions = chapter.missions().size();

		if (start == Start.MISSION && (mission < 0 || mission >= missions || mission > unlocked(progress, missions))) {
			return Component.translatable("sabercraft.story.locked");
		}

		if (!progress.active) {
			// Away with one's own things (into the stash), remember where one was.
			if (!StoryStore.hasStash(server, player.getUUID()) && !StoryStore.stash(player)) {
				return Component.translatable("sabercraft.story.stash_failed");
			}

			progress.stashed = true;
			progress.returnDimension = player.level().dimension().identifier().toString();
			progress.returnX = player.getX();
			progress.returnY = player.getY();
			progress.returnZ = player.getZ();
			progress.returnYaw = player.getYRot();
			progress.returnPitch = player.getXRot();
			progress.returnMode = player.gameMode.getGameModeForPlayer().getName();
			progress.ownDroids = Droids.crew(player).stream().map(DroidType::id).toList();
			progress.active = true;
		}

		switch (start) {
			case RESTART -> {
				progress.mission = 0;
				progress.step = 0;
				progress.stepId = "";
				progress.placeShip = "";
				progress.flags.clear();
				progress.journal.clear();
			}
			case MISSION -> {
				progress.mission = mission;
				progress.step = 0;
				progress.stepId = "";
				progress.placeShip = "";
				List<String> before = progress.missionStart.get(mission);
				progress.flags.clear();
				progress.flags.addAll(before != null ? before : Chapter1.defaults(mission));
				progress.journal.removeIf(entry -> false);
			}
			case CONTINUE -> {
				// Where one left off (after the chapter's end: in the Falcon, at the epilogue).
			}
		}

		progress.mission = Math.max(0, Math.min(progress.mission, missions - 1));
		resolveStep(chapter, progress);
		progress.missionStart.putIfAbsent(progress.mission, List.copyOf(progress.flags));
		UUID owner = storyOwner(player.getUUID());
		BlockPos falcon = ShipInteriors.storyPlot(server, owner, FALCON);
		BlockPos destroyer = ShipInteriors.storyPlot(server, owner, DESTROYER);
		StorySession session = new StorySession(player.getUUID(), server, progress, chapter, falcon, destroyer);
		SESSIONS.put(player.getUUID(), session);

		player.setGameMode(GameType.ADVENTURE);
		player.setHealth(player.getMaxHealth());
		player.getFoodData().setFoodLevel(20);
		Droids.replaceCrew(player, storyDroids(session));
		session.rebuildFigures();
		session.begin(true);
		cue(session, CUE_TITLE, "", chapter.missions().get(progress.mission).title(), 0);
		sendState(session);
		SaberCraft.LOGGER.info("{} entered the story ({}, mission {}, step {})", player.getPlainTextName(), start, progress.mission + 1, progress.step + 1);
		return null;
	}

	/**
	 * Phase 20: the step of a saved checkpoint by its id (a mission may have got a step more since
	 * the file was written). A file of Phase 19 has only the number: it is read as a step of Phase
	 * 19's missions, and a step that is gone now becomes the one that took its place.
	 */
	static void resolveStep(Chapter chapter, StoryProgress progress) {
		Mission mission = chapter.missions().get(progress.mission);
		String id = progress.stepId;

		if ((id == null || id.isEmpty()) && progress.format < StoryProgress.FORMAT && chapter instanceof Chapter1) {
			id = Chapter1.phase19Step(progress.mission, progress.step);
			// Its place was the step's start then; now it is the step's safe place.
			progress.placeShip = "";
		}

		int index = id == null || id.isEmpty() ? -1 : mission.indexOf(id);

		if (index < 0 && id != null && !id.isEmpty() && chapter instanceof Chapter1) {
			index = mission.indexOf(Chapter1.renamedStep(id));
		}

		if (index >= 0) {
			progress.step = index;
		}

		progress.step = Math.max(0, Math.min(progress.step, mission.steps().size() - 1));
		progress.stepId = mission.steps().get(progress.step).id();
	}

	/** The highest mission one may start (the first not yet finished). */
	static int unlocked(StoryProgress progress, int missions) {
		int next = 0;

		while (progress.completed.contains(next)) {
			next++;
		}

		return Math.min(missions - 1, Math.max(next, progress.mission));
	}

	/** Takes a player out of the story: their things, droids, game mode and place back. */
	public static void leave(ServerPlayer player) {
		StorySession session = SESSIONS.get(player.getUUID());

		if (session != null) {
			// Phase 20: calm, how far one got is kept (in a chase the last checkpoint stays).
			session.keepOnLeave();
		}

		SESSIONS.remove(player.getUUID());
		MinecraftServer server = player.level().getServer();
		StoryProgress progress = session != null ? session.progress : StoryStore.load(server, player.getUUID());

		if (!progress.active) {
			return;
		}

		player.closeContainer();
		StoryStore.unstash(player);

		if (progress.finished && !progress.flags.contains("reward.given")) {
			progress.flags.add("reward.given");
			player.getInventory().add(Chapter1.reward());
		}

		OutfitSync.override(player, null);
		List<DroidType> own = new ArrayList<>();

		for (String id : progress.ownDroids) {
			DroidType.byId(id).ifPresent(own::add);
		}

		Droids.replaceCrew(player, own);
		GameType mode = GameType.byName(progress.returnMode, GameType.SURVIVAL);
		player.setGameMode(mode);
		progress.active = false;
		progress.stashed = false;
		StoryStore.save(server, player.getUUID(), progress);

		Identifier id = Identifier.tryParse(progress.returnDimension);
		ServerLevel level = id != null ? server.getLevel(ResourceKey.create(Registries.DIMENSION, id)) : null;

		if (level == null || level.dimension().equals(ShipInteriors.DIMENSION)) {
			level = server.overworld();
			BlockPos spawn = level.getRespawnData().globalPos().pos();
			player.teleportTo(level, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, java.util.Set.of(), progress.returnYaw, 0.0F, true);
		} else {
			player.teleportTo(level, progress.returnX, progress.returnY, progress.returnZ, java.util.Set.of(), progress.returnYaw, progress.returnPitch, true);
		}

		ServerPlayNetworking.send(player, StoryDialogPayload.closed());
		sendState(player, progress, null);
		SaberCraft.LOGGER.info("{} left the story", player.getPlainTextName());
	}

	/** On login: back into the story where one left off (or, if the story is gone, things back). */
	private static void joined(ServerPlayer player) {
		StoryProgress progress = StoryStore.load(player.level().getServer(), player.getUUID());

		if (progress.active) {
			Component refused = enter(player, Start.CONTINUE, 0);

			if (refused != null) {
				leave(player);
			}
		} else if (ServerPlayNetworking.canSend(player, StoryStatePayload.TYPE)) {
			sendState(player, progress, null);
		}
	}

	/** The droids the story gives: those recruited. */
	static List<DroidType> storyDroids(StorySession session) {
		List<DroidType> list = new ArrayList<>();

		if (session.has("droid.m31")) {
			list.add(DroidType.MOUSE);
		}

		if (session.has("droid.r2v7")) {
			list.add(DroidType.R2D2);
		}

		return list;
	}

	/** After something changed which droids are with the player. */
	public static void updateDroids(StorySession session) {
		ServerPlayer player = session.player();

		if (player != null) {
			Droids.replaceCrew(player, storyDroids(session));
		}
	}

	// --- Places ------------------------------------------------------------------------------------

	/** Puts the player in a ship of their story at a place (plot space). */
	public static void place(StorySession session, String ship, Vec3 local, float yaw) {
		ServerPlayer player = session.player();

		if (player == null) {
			return;
		}

		UUID owner = storyOwner(session.player);
		boolean otherShip = !ship.equals(session.here);
		ShipInteriors.storyBoard(player, owner, ship, local, yaw);
		session.here = ship;

		if (otherShip) {
			long time = session.time();

			for (Figure figure : session.figures) {
				if (ship.equals(figure.ship) && figure.mode != Figure.HELD && figure.mode != Figure.DOWN && figure.mode != Figure.SCRIPTED) {
					figure.snapToRoutine(time);
				}
			}
		}

		session.dirty = true;
	}

	/** Phase 20: whether one of the people of the story whose ship has that plot corner is in a box (world space): doors open for them. */
	public static boolean figureNear(BlockPos origin, AABB box) {
		StorySession session = at(origin);

		if (session == null) {
			return false;
		}

		String ship = origin.equals(session.falcon) ? FALCON : DESTROYER;
		Vec3 corner = Vec3.atLowerCornerOf(origin);

		for (Figure figure : session.figures) {
			if (ship.equals(figure.ship) && figure.mode != Figure.DOWN && box.contains(figure.position().add(corner).add(0.0, 0.5, 0.0))) {
				return true;
			}
		}

		return false;
	}

	/**
	 * A player of a story stepped into an exit of a story ship (the Falcon's ramp, below the
	 * hangar). Returns whether the story took care of it (always, for story ships).
	 */
	public static boolean exit(ServerPlayer player) {
		StorySession session = SESSIONS.get(player.getUUID());

		if (session == null) {
			return false;
		}

		if (session.chapter instanceof Chapter1 chapter) {
			chapter.exit(session);
		}

		return true;
	}

	/** Whether that door of a plot is locked by a story. */
	public static boolean locked(BlockPos origin, int x, int y, int z) {
		StorySession session = at(origin);

		if (session == null) {
			return false;
		}

		String ship = origin.equals(session.falcon) ? FALCON : DESTROYER;
		return session.chapter.locked(session, ship, x, y, z);
	}

	/**
	 * Phase 20: whether the story keeps that hatch of a plot shut now (bolted from the other side,
	 * a guard standing in front of it); the player who tried is told why.
	 */
	public static boolean hatchClosed(ServerPlayer player, BlockPos origin, BlockPos cell) {
		StorySession session = at(origin);

		if (session == null) {
			return false;
		}

		String ship = origin.equals(session.falcon) ? FALCON : DESTROYER;
		String why = session.chapter.hatchClosed(session, ship, cell.getX(), cell.getY(), cell.getZ());

		if (why != null && session.player.equals(player.getUUID())) {
			session.hint(why);
		}

		return why != null;
	}

	/** A droid's ability in the story; returns the message, or null for the normal ability. */
	public static @Nullable Component droidAbility(ServerPlayer player, DroidType type) {
		StorySession session = SESSIONS.get(player.getUUID());

		if (session == null) {
			return null;
		}

		session.droidArgs = new Object[0];
		String key = session.chapter.droid(session, type == DroidType.MOUSE ? "mouse" : type == DroidType.R2D2 ? "astromech" : type.id());
		return key != null ? Component.translatable(key, session.droidArgs) : Component.translatable("sabercraft.story.droid.nothing");
	}

	// --- Every tick --------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		ServerLevel interiors = server.getLevel(ShipInteriors.DIMENSION);

		for (StorySession session : List.copyOf(SESSIONS.values())) {
			ServerPlayer player = session.player();

			if (player == null) {
				SESSIONS.remove(session.player);
				continue;
			}

			// Where they are.
			BlockPos here = ShipInteriors.originAt(player.getX(), player.getZ());
			session.here = player.level() == interiors && here != null ? here.equals(session.falcon) ? FALCON : here.equals(session.destroyer) ? DESTROYER : null
					: null;

			if (session.here == null && player.level() != interiors) {
				// Out of the interiors some other way (a command): the story ends for now.
				SESSIONS.remove(session.player);
				leave(player);
				continue;
			}

			session.stepTicks++;
			session.timers.replaceAll((name, left) -> Math.max(0, left - 1));

			if (player.tickCount % 100 == 0) {
				player.getFoodData().setFoodLevel(20);
			}

			if (interiors != null) {
				Stealth.tick(session, player, interiors);
			}

			if (SESSIONS.get(session.player) != session) {
				continue;
			}

			session.chapter.tick(session);
			session.afterTick();

			if (server.getTickCount() % 2 == 0) {
				sendFigures(session, player);
			}

			if (session.dirty && server.getTickCount() % 4 == 0) {
				session.dirty = false;
				sendState(session);
			}

			if (server.getTickCount() % 20 == 0) {
				nameDroids(session, player);
			}
		}
	}

	/** The story's droids carry their names. */
	private static void nameDroids(StorySession session, ServerPlayer player) {
		for (DroidType type : storyDroids(session)) {
			DroidEntity droid = Droids.droid(player, type);
			String name = type == DroidType.MOUSE ? "M-31" : "R2-V7";

			if (droid != null && (droid.getCustomName() == null || !droid.getCustomName().getString().equals(name))) {
				droid.setCustomName(Component.literal(name));
			}
		}
	}

	/** On to the next step, the next mission, or the chapter's end. */
	static void advance(StorySession session) {
		StoryProgress progress = session.progress;
		Mission mission = session.mission();

		if (session.stepIndex + 1 < mission.steps().size()) {
			session.stepIndex++;
			session.begin(false);
			cue(session, CUE_SOUND, "", "story.objective", 0);
			return;
		}

		// The mission is done.
		progress.completed.add(session.mission);
		cue(session, CUE_SOUND, "", "story.mission_done", 0);

		if (session.mission + 1 < session.chapter.missions().size()) {
			session.mission++;
			session.stepIndex = 0;
			session.flags.removeIf(flag -> flag.startsWith("tmp."));
			progress.missionStart.put(session.mission, List.copyOf(session.flags));
			session.begin(false);
			cue(session, CUE_TITLE, "", session.mission().title(), 0);
		} else {
			// The end of the chapter (a checkpoint where one is, in the Falcon's lounge).
			progress.finished = true;
			Mission.Place place = session.chapter.place(session, session.step());
			session.checkpoint(place.ship(), place.at(), place.yaw());
			session.dirty = true;
			cue(session, CUE_END, "", "sabercraft.story.end", 0);
		}
	}

	/** Phase 20: puts the player at the checkpoint's safe place (or the step's, for a file without one). */
	static void placeAtCheckpoint(StorySession session, Mission.Step step) {
		StoryProgress progress = session.progress;
		ServerPlayer player = session.player();

		if (player == null) {
			return;
		}

		boolean own = !progress.placeShip.isEmpty() && progress.step == session.stepIndex && progress.mission == session.mission;
		Mission.Place safe = session.chapter.place(session, step);
		String ship = own ? progress.placeShip : safe.ship();
		Vec3 at = own ? new Vec3(progress.placeX, progress.placeY, progress.placeZ) : safe.at();
		float yaw = own ? progress.placeYaw : safe.yaw();
		player.stopRiding();
		player.setDeltaMovement(Vec3.ZERO);
		player.fallDistance = 0.0F;
		place(session, ship, at, yaw);
		player.setHealth(player.getMaxHealth());

		// In a crawl way: down on all fours at once (there is no room to stand).
		if (crawlway(player)) {
			player.setPose(net.minecraft.world.entity.Pose.SWIMMING);
		}
	}

	/** Whether there is no room to stand where the player is (a crawl way). */
	private static boolean crawlway(ServerPlayer player) {
		AABB standing = player.getDimensions(net.minecraft.world.entity.Pose.STANDING).makeBoundingBox(player.position()).deflate(1.0E-7);
		return !player.level().noCollision(player, standing);
	}

	/** Phase 20: the ship as at the checkpoint: the hatches of the story's ships shut (the chapter sets the rest). */
	static void resetShip(StorySession session) {
		ServerLevel level = session.server.getLevel(ShipInteriors.DIMENSION);

		if (level != null) {
			com.phiro.sabercraft.interior.Vents.closeAll(level, session.destroyer);
			com.phiro.sabercraft.interior.Vents.closeAll(level, session.falcon);
		}
	}

	/** Phase 20: the story's droids follow the player again, next to them. */
	public static void gatherDroids(StorySession session) {
		ServerPlayer player = session.player();

		if (player == null) {
			return;
		}

		Droids.replaceCrew(player, storyDroids(session));

		for (DroidType type : storyDroids(session)) {
			Droids.bring(player, type);
		}
	}

	/** Saves the progress (at a checkpoint). */
	static void save(StorySession session) {
		StoryStore.save(session.server, session.player, session.progress);
	}

	// --- Being seen and caught ---------------------------------------------------------------------

	/** Someone detected the player: the alarm goes up and they give chase (or, where one must stay unseen, the section fails). */
	static void detected(StorySession session, Figure figure) {
		ServerPlayer player = session.player();

		if (player == null) {
			return;
		}

		if (session.step().unseen()) {
			cue(session, CUE_SOUND, "", "story.detected", figure.id);
			fail(session, "sabercraft.story.hint.seen");
			return;
		}

		Vec3 me = session.local(figure.ship);
		figure.path = List.of();
		figure.pathGoal = null;
		cue(session, CUE_SOUND, "", "story.detected", figure.id);

		// Phase 21: where the player was seen (the squad goes there when they lose sight).
		session.huntSeen = session.time();
		session.huntAt = me;

		if (soldier(figure)) {
			// Phase 20: a soldier shouts, gives chase and has the alarm raised over his comlink.
			// Phase 21: the call brings two more troopers a few seconds later (once per alarm).
			session.hear(voice(figure), "sabercraft.story.shout.halt");
			figure.mode = Figure.CHASE;
			figure.cooldown = 10 + figure.id % 10;
			Stealth.rally(session, figure, me);

			if (session.alarm <= 0) {
				session.stuns = 0;
				session.set("tmp.reinforce");
				session.timer("reinforce", Stealth.REINFORCE_DELAY);
			}

			session.raiseAlarm(Stealth.SEARCH_LONG + Stealth.LOSE);
			session.searching = false;
			session.unseen = 0;
			session.hint("sabercraft.story.hint.detected");
			return;
		}

		// Phase 20: anyone else calls out and runs to the nearest wall comm: the alarm goes up when
		// they get there (or at once, if troopers close by heard the call and come running).
		session.hear(voice(figure), "sabercraft.story.shout.intruder");
		int joined = Stealth.rally(session, figure, me);
		runToComm(session, figure);

		if (joined > 0) {
			session.raiseAlarm(Stealth.SEARCH_LONG + Stealth.LOSE);
			session.searching = false;
			session.unseen = 0;
		}
	}

	/** Phase 20: one who is no soldier runs to the nearest wall comm to raise the alarm there (at once, with none in reach). */
	static void runToComm(StorySession session, Figure figure) {
		Vec3 comm = nearest(session.chapter.comms(session, figure.ship), figure.position(), Stealth.COMM_REACH, null);
		figure.suspicion = 1.0F;
		figure.stage = Figure.DETECTED;
		figure.mode = Figure.CALL;
		figure.called = false;
		figure.path = List.of();
		figure.pathGoal = null;

		if (figure.seenAt == null) {
			figure.seenAt = session.local(figure.ship);
		}

		if (comm == null) {
			figure.target = figure.position();
			figure.timer = 0;
		} else {
			figure.target = comm;
			figure.timer = 200;
			session.hint("sabercraft.story.hint.detected_call");
		}
	}

	/** For the tests: how often a chaser reached a player ("Hands up!") since the game started. */
	private static int cornerings;

	public static int corneringsForTest() {
		return cornerings;
	}

	/** For the tests: whether that one gives chase when they see the player (or runs for a wall comm). */
	public static boolean soldierForTest(Figure figure) {
		return soldier(figure);
	}

	/** Phase 20: whether that one gives chase (the troops), or runs for a wall comm. */
	static boolean soldier(Figure figure) {
		return Crew.TROOPER.equals(figure.kind()) || Crew.GUARD.equals(figure.kind());
	}

	/** Phase 20: whose name an overheard shout shows (the story's name for them, else their kind). */
	static String voice(Figure figure) {
		if (!figure.speaker.isEmpty()) {
			return figure.speaker;
		}

		return switch (figure.kind()) {
			case Crew.TROOPER, Crew.GUARD -> "trooper";
			case Crew.TECH -> "crew_tech";
			case Crew.PILOT -> "pilot";
			default -> "officer";
		};
	}

	/** Phase 20: the point of those nearest to a place within reach (not one within {@code away} of another place), or null. */
	static @Nullable Vec3 nearest(List<Vec3> points, Vec3 to, double reach, @Nullable Vec3 away) {
		Vec3 best = null;

		for (Vec3 point : points) {
			if (point.distanceTo(to) < reach && (away == null || point.distanceTo(away) > 9.0) && (best == null || point.distanceTo(to) < best.distanceTo(to))) {
				best = point;
			}
		}

		return best;
	}

	/**
	 * Phase 20: a call from a wall comm: the alarm, over the intercom, and two troopers from the
	 * door nearest the caller (not one right by the player); they come running to where the
	 * player was seen. At most four of them about at once.
	 */
	static void alarmCalled(StorySession session, Figure caller) {
		session.raiseAlarm(Stealth.SEARCH_LONG + Stealth.LOSE);
		session.searching = false;
		session.unseen = 0;
		session.hear("intercom", "sabercraft.story.shout.alarm_call");

		if (!caller.key.startsWith("crew:")) {
			// The story's people remember it (their place is by the comm then).
			session.set(caller.key + ".called");
		}

		reinforce(session, caller.ship, caller.position(), caller.seenAt);
	}

	/**
	 * Phase 21 (from Phase 20's call at a wall comm): two troopers from the door nearest a place
	 * (not one right by the player), running to where the player was seen; at most four of them
	 * about at once.
	 */
	static void reinforce(StorySession session, String ship, Vec3 near, @Nullable Vec3 seenAt) {
		long already = session.figures.stream().filter(figure -> figure.key.startsWith(Stealth.REINFORCEMENT)).count();
		Vec3 me = session.local(ship);
		List<Vec3> doors = new ArrayList<>();

		for (Vec3 entry : session.chapter.entries(session, ship)) {
			if (Math.abs(entry.y - near.y) < 3.0) {
				doors.add(entry);
			}
		}

		Vec3 door = nearest(doors, near, 45.0, me);

		if (door == null || already >= 4) {
			return;
		}

		for (int i = 0; i < 2; i++) {
			double side = i == 0 ? -0.6 : 0.6;
			Figure template = new Figure(0, Stealth.REINFORCEMENT + session.figures.size() + "." + i,
					Crew.at(Crew.TROOPER, Crew.STAND, door.x + side, door.y, door.z, Crew.yaw(door, me)));
			template.ship = ship;
			template.observer = true;
			template.talks = false;
			template.lamp = session.chapter.dark(session, ship, door.x, door.y, door.z);
			Figure trooper = session.add(template);
			trooper.mode = Figure.CHASE;
			trooper.suspicion = 1.0F;
			trooper.stage = Figure.DETECTED;
			trooper.seenAt = seenAt;
			trooper.cooldown = 20 + i * 12;
		}

		session.hint("sabercraft.story.hint.reinforcements");
	}

	/**
	 * Phase 21: a trooper's stun bolt hit the player. The hit is felt (a jolt, the screen flashes,
	 * slowed for a moment, the HUD counts it); the third in a chase and the player is caught, as
	 * if a chaser had reached them.
	 */
	public static void stunned(ServerPlayer player) {
		StorySession session = SESSIONS.get(player.getUUID());

		if (session == null || session.fighting || session.grace > 0 || session.cornered > 0) {
			return;
		}

		session.stuns++;
		stunHits++;
		cue(session, CUE_SOUND, "", "story.stun_hit", 0);
		cue(session, CUE_HINT, "stun", "sabercraft.story.hint.stunned", session.stuns);

		if (session.stuns >= Combat.STUN_HITS) {
			Figure by = Combat.nearestChaser(session, session.local(session.here != null ? session.here : DESTROYER));

			if (by != null) {
				cornered(session, by);
			} else {
				capture(session, null);
			}
		}
	}

	private static int stunHits;

	/** For the tests: how many stun bolts hit a player in a story since the game started. */
	public static int stunHitsForTest() {
		return stunHits;
	}

	/** Phase 20: a chaser reached the player: "Hands up!", the player slowed, a moment later caught. */
	static void cornered(StorySession session, Figure figure) {
		ServerPlayer player = session.player();

		if (session.grace > 0 || session.cornered > 0 || player == null) {
			return;
		}

		session.cornered = Stealth.CORNERED;
		session.cornerer = figure;
		cornerings++;
		figure.speed = 0.0F;
		session.hear(voice(figure), "sabercraft.story.shout.hands_up");
		player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOWNESS, Stealth.CORNERED + 6, 5, false, false));
	}

	/** Caught: a moment of black, back to the step's checkpoint, everything as it was then. */
	public static void capture(StorySession session, @Nullable Figure by) {
		if (session.grace > 0) {
			return;
		}

		captures++;
		session.progress.captures++;

		// Phase 21: back at the checkpoint unhurt (stun bolts and blows took some health).
		ServerPlayer caught = session.player();

		if (caught != null) {
			caught.setHealth(caught.getMaxHealth());
			caught.removeAllEffects();
		}
		cue(session, CUE_FADE, "", "sabercraft.story.caught", 30);
		cue(session, CUE_SOUND, "", "story.caught", 0);
		session.grace = StorySession.GRACE;
		session.fighting = false;
		session.begin(true);
		session.radio(Chapter1.captureSpeaker(session), Chapter1.captureLine(session));
		sendState(session);
	}

	/** A section that had to be done unseen failed: back to its checkpoint, with a word why. */
	public static void fail(StorySession session, String hint) {
		session.grace = StorySession.GRACE;
		cue(session, CUE_FADE, "", hint, 30);
		session.begin(true);
		session.hint(hint);
		sendState(session);
	}

	// --- Network -----------------------------------------------------------------------------------

	private static void action(ServerPlayer player, StoryActionPayload payload) {
		StorySession session = SESSIONS.get(player.getUUID());

		switch (payload.action()) {
			case StoryActionPayload.CONTINUE -> message(player, enter(player, Start.CONTINUE, 0));
			case StoryActionPayload.RESTART -> {
				if (session != null) {
					SESSIONS.remove(player.getUUID());
				}

				message(player, enter(player, Start.RESTART, 0));
			}
			case StoryActionPayload.MISSION -> {
				if (session != null) {
					SESSIONS.remove(player.getUUID());
				}

				message(player, enter(player, Start.MISSION, payload.value()));
			}
			case StoryActionPayload.LEAVE -> leave(player);
			case StoryActionPayload.STATE -> {
				if (session != null) {
					sendState(session);
				} else {
					sendState(player, StoryStore.load(player.level().getServer(), player.getUUID()), null);
				}
			}
			case StoryActionPayload.ANSWER -> {
				if (session != null) {
					session.answer(payload.value());
					session.afterTick();
				}
			}
			case StoryActionPayload.USE -> {
				if (session != null) {
					use(session, player, payload.text());
					session.afterTick();
				}
			}
			case StoryActionPayload.TALK -> {
				if (session != null) {
					talk(session, player, payload.value());
					session.afterTick();
				}
			}
			case StoryActionPayload.SKIP -> {
				if (session != null && session.chapter instanceof Chapter1 chapter) {
					chapter.skipFilm(session, payload.text());
				}
			}
			case StoryActionPayload.STRIKE -> {
				if (session != null && session.chapter instanceof Chapter1 chapter) {
					chapter.strike(session, player);
					session.afterTick();
				}
			}
			case StoryActionPayload.FOCUS -> {
				if (session != null) {
					String hint = Finale.focus(session);

					if (hint != null) {
						session.hint(hint);
					}

					session.afterTick();
				}
			}
			default -> {
			}
		}
	}

	/**
	 * Phase 21: which saber abilities work in the story. The people of the story are not entities,
	 * so the Force powers aimed at entities (push, grip, pull) would do nothing; in the story the
	 * guard and the dash work, and the Focus has its own key. Outside the story: everything.
	 */
	public static boolean allows(ServerPlayer player, String ability) {
		StorySession session = SESSIONS.get(player.getUUID());

		if (session == null) {
			return true;
		}

		boolean allowed = "guard".equals(ability) || "dash".equals(ability);

		if (!allowed && !session.timer("force_hint")) {
			session.timer("force_hint", 100);
			session.hint("sabercraft.story.hint.force_locked");
		}

		return allowed;
	}

	private static void message(ServerPlayer player, @Nullable Component message) {
		if (message != null) {
			player.sendSystemMessage(message);
		}
	}

	/** Uses a thing, if it is there to be used and within reach. */
	public static boolean use(StorySession session, ServerPlayer player, String id) {
		if (session.dialog != null || session.here == null) {
			return false;
		}

		for (Mission.Point point : session.chapter.points(session)) {
			if (point.id().equals(id) && point.ship().equals(session.here)) {
				AABB box = point.box().move(Vec3.atLowerCornerOf(session.origin(point.ship())));

				if (box.distanceToSqr(player.getEyePosition()) > REACH * REACH) {
					return false;
				}

				if (point.hack()) {
					session.hint("sabercraft.story.hint.locked_hack");
					return true;
				}

				session.chapter.use(session, point);
				return true;
			}
		}

		return false;
	}

	/** Speaks to someone, if they are there and within reach. */
	public static boolean talk(StorySession session, ServerPlayer player, int id) {
		if (session.dialog != null || session.here == null) {
			return false;
		}

		for (Figure figure : session.figures) {
			if (figure.id == id && session.here.equals(figure.ship) && figure.talks && figure.mode != Figure.DOWN) {
				AABB box = figure.box().move(Vec3.atLowerCornerOf(session.origin(figure.ship)));

				if (box.distanceToSqr(player.getEyePosition()) > REACH * REACH) {
					return false;
				}

				return session.chapter.talk(session, figure);
			}
		}

		return false;
	}

	static void sendState(StorySession session) {
		ServerPlayer player = session.player();

		if (player != null) {
			sendState(player, session.progress, session);
		}
	}

	private static void sendState(ServerPlayer player, StoryProgress progress, @Nullable StorySession session) {
		if (!ServerPlayNetworking.canSend(player, StoryStatePayload.TYPE)) {
			return;
		}

		Chapter chapter = CHAPTERS.getOrDefault(progress.chapter, Chapter1.INSTANCE);
		boolean saved = StoryStore.exists(player.level().getServer(), player.getUUID());
		int missionIndex = Math.max(0, Math.min(session != null ? session.mission : progress.mission, chapter.missions().size() - 1));
		Mission mission = chapter.missions().get(missionIndex);
		List<StoryStatePayload.Objective> optional = new ArrayList<>();
		List<StoryStatePayload.Point> points = new ArrayList<>();
		String objective = "";
		boolean target = false;
		Vec3 marker = Vec3.ZERO;
		int alarm = 0;
		int disguise = 0;
		List<String> known = new ArrayList<>();
		List<String> droids = new ArrayList<>();
		BlockPos falcon = BlockPos.ZERO;
		BlockPos destroyer = BlockPos.ZERO;
		java.util.Collection<String> flags = session != null ? session.flags : progress.flags;
		List<String> journal = session != null ? session.journal : progress.journal;

		for (String flag : flags) {
			// What is known, decided and done: the journal shows the first, the client draws by the
			// rest (the saber taken, a card on the desk, a valve turned). Scripts' passing marks stay.
			if (!flag.startsWith("tmp.")) {
				known.add(flag);
			}
		}

		if (session != null) {
			Mission.Step step = session.step();
			objective = step.objective();

			for (Mission.Optional opt : mission.optional()) {
				if (opt.shown().test(session) || session.has(opt.flag())) {
					optional.add(new StoryStatePayload.Objective(opt.objective(), session.has(opt.flag())));
				}
			}

			Mission.Target goal = session.chapter instanceof Chapter1 c1 ? c1.target(session) : step.target();

			if (goal != null) {
				target = true;
				marker = goal.at().add(Vec3.atLowerCornerOf(session.origin(goal.ship())));
			}

			for (Mission.Point point : session.chapter.points(session)) {
				points.add(new StoryStatePayload.Point(point.id(), point.label(), point.hack(), point.box().move(Vec3.atLowerCornerOf(session.origin(point.ship())))));
			}

			// The alarm and the disguise are the Star Destroyer's (in the Falcon one is safe).
			// Phase 20: 3 they are after the player, 2 they search, 1 the ship is on alert (nobody after one).
			alarm = !DESTROYER.equals(session.here) ? 0 : session.alarm > 0 ? session.unseen >= Stealth.LOSE ? 2 : 3 : session.alert ? 1 : 0;
			disguise = !session.disguised || !DESTROYER.equals(session.here) ? 0 : session.burned ? 2 : 1;
			falcon = session.falcon;
			destroyer = session.destroyer;

			for (DroidType type : storyDroids(session)) {
				droids.add(type == DroidType.MOUSE ? "m31" : "r2v7");
			}
		}

		ServerPlayNetworking.send(player, new StoryStatePayload(session != null, saved, chapter.id(), missionIndex, chapter.missions().size(),
				List.copyOf(progress.completed), progress.finished, mission.title(), objective, optional, target, marker, alarm, disguise, points, falcon,
				destroyer, List.copyOf(journal), known, progress.captures, droids));
	}

	private static void sendFigures(StorySession session, ServerPlayer player) {
		if (session.here == null || !ServerPlayNetworking.canSend(player, StoryFiguresPayload.TYPE)) {
			return;
		}

		BlockPos origin = session.origin(session.here);
		Vec3 me = session.local(session.here);
		List<StoryFiguresPayload.Fig> list = new ArrayList<>();

		for (Figure f : session.figures) {
			if (!session.here.equals(f.ship) || f.position().distanceToSqr(me) > SEND_RANGE * SEND_RANGE) {
				continue;
			}

			int flags = (f.lamp ? StoryFiguresPayload.LAMP : 0) | (f.saber ? StoryFiguresPayload.SABER : 0) | (f.hostile ? StoryFiguresPayload.HOSTILE : 0)
					| (f.talks ? StoryFiguresPayload.TALKS : 0) | (f.observer ? StoryFiguresPayload.OBSERVER : 0) | (f.lit ? StoryFiguresPayload.LIT : 0);
			list.add(new StoryFiguresPayload.Fig(f.id, f.kind(), f.pose(), f.speaker, (float) f.x, (float) f.y, (float) f.z, f.yaw, f.headYaw, f.speed, f.stage,
					Math.round(f.suspicion * 255.0F), flags,Math.round(Math.max(0.0F, f.health) * 255.0F), Finale.shown(f)));
		}

		ServerPlayNetworking.send(player, new StoryFiguresPayload(origin, player.level().getGameTime(), list));
	}

	static void sendDialog(StorySession session) {
		ServerPlayer player = session.player();

		if (player == null || !ServerPlayNetworking.canSend(player, StoryDialogPayload.TYPE)) {
			return;
		}

		Dialog dialog = session.dialog;
		Dialog.Node line = dialog != null && session.line != null ? dialog.node(session.line) : null;

		if (dialog == null || line == null) {
			ServerPlayNetworking.send(player, StoryDialogPayload.closed());
			return;
		}

		List<String> answers = new ArrayList<>();

		for (Dialog.Answer answer : line.shown(session)) {
			answers.add(answer.key(dialog, line));
		}

		Figure partner = session.partner;

		if (partner != null && partner.mode != Figure.SCRIPTED && partner.mode != Figure.FIGHT && partner.mode != Figure.DOWN) {
			// They stop and look at the player (unless a script sends them off, or they fight).
			partner.mode = Figure.HELD;
			partner.target = session.local(partner.ship);
		}

		ServerPlayNetworking.send(player, new StoryDialogPayload(true, line.speaker(), line.radio(), line.key(dialog), answers, dialog.id + "." + line.id()));
	}

	/** Sends a cue (a comlink line, a fade, a sound, ...). */
	public static void cue(StorySession session, byte cue, String who, String text, int value) {
		cue(session, cue, who, text, value, List.of());
	}

	public static void cue(StorySession session, byte cue, String who, String text, int value, List<Vec3> points) {
		ServerPlayer player = session.player();

		if (player == null) {
			return;
		}

		if (cue == CUE_SOUND && !ServerPlayNetworking.canSend(player, StoryCuePayload.TYPE)) {
			// Without the mod's client: the sound at least.
			player.connection.send(new ClientboundSoundPacket(ShipInteriors.sound(text), SoundSource.NEUTRAL, player.getX(), player.getY(), player.getZ(), 0.8F,
					1.0F, player.getRandom().nextLong()));
			return;
		}

		if (ServerPlayNetworking.canSend(player, StoryCuePayload.TYPE)) {
			ServerPlayNetworking.send(player, new StoryCuePayload(cue, who, text, value, points));
		} else if (cue == CUE_RADIO || cue == CUE_HINT) {
			player.sendSystemMessage(Component.translatable(text));
		}
	}

	// --- For tests ---------------------------------------------------------------------------------

	public static int capturesForTest() {
		return captures;
	}

	public static Optional<StorySession> sessionForTest(ServerPlayer player) {
		return Optional.ofNullable(SESSIONS.get(player.getUUID()));
	}

	public static void forgetForTest(ServerPlayer player) {
		SESSIONS.remove(player.getUUID());
		StoryStore.delete(player.level().getServer(), player.getUUID());
	}

	/** Whether a level is the one the story ships are in. */
	public static boolean inInteriors(Level level) {
		return ShipInteriors.isInterior(level);
	}
}
