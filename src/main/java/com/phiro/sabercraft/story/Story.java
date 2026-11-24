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
import com.phiro.sabercraft.interior.DestroyerInterior;
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
	/**
	 * Phase 26: a short effect the client shows somewhere (who: its name, e.g. {@code field_flicker};
	 * value: how long, ticks; points: where, world space).
	 */
	public static final byte CUE_EFFECT = 12;
	/**
	 * Phase 27: the player has to act at once (seen, the alarm, hit, caught): the client closes any
	 * dialog, menu or inventory screen still open, so the controls are free.
	 */
	public static final byte CUE_INTERRUPT = 13;

	/** How far one reaches to use a thing or speak to someone (blocks). */
	static final double REACH = 4.5;
	/** How far people are sent to the client (blocks). */
	static final double SEND_RANGE = 72.0;

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
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			// Phase 27: a server stopping with players still in the story keeps how far they got
			// (as leaving does: only when all is calm, at the step's safe place).
			for (StorySession session : List.copyOf(SESSIONS.values())) {
				try {
					session.keepOnLeave();
				} catch (RuntimeException e) {
					SaberCraft.LOGGER.warn("Story: could not keep the progress of {} at shutdown", session.player, e);
				}
			}

			SESSIONS.clear();
			// (A difficulty picked before a first start belongs to this world's save only.)
			PENDING_DIFFICULTY.clear();
		});
		// Phase 27: hurt (a bolt, a blow, a fall): an open dialog ends, the player has to act. Not in
		// a fight: the boss's last words stay open while her duel begins.
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (entity instanceof ServerPlayer player && taken > 0.0F) {
				StorySession session = SESSIONS.get(player.getUUID());

				if (session != null && !session.fighting) {
					session.interrupt(false);
				}
			}
		});
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			if (entity instanceof ServerPlayer player && SESSIONS.containsKey(player.getUUID())) {
				// Nobody dies in the story: it counts as being caught.
				StorySession session = SESSIONS.get(player.getUUID());
				player.setHealth(player.getMaxHealth());

				// Phase 27: also in the grace after a capture (it was swallowed: healed where one fell),
				// but not again in the first moments after being put back.
				if (session.grace > 0 && session.stepTicks > 40) {
					session.grace = 0;
					session.shield = 0;
				}

				// Phase 31: caught at the start of the next tick (see StorySession.captureDue).
				if (session.grace <= 0) {
					session.captureDue = true;
				}

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
		// Phase 27: the difficulty chosen before the first start (only for a story not saved yet:
		// a saved one keeps its own).
		String pending = PENDING_DIFFICULTY.remove(player.getUUID());

		if (pending != null && running == null && !StoryStore.exists(server, player.getUUID())) {
			progress.difficulty = pending;
		}
		Chapter chapter = CHAPTERS.getOrDefault(progress.chapter, Chapter1.INSTANCE);
		int missions = chapter.missions().size();

		if (start == Start.MISSION && (mission < 0 || mission >= missions || mission > unlocked(progress, missions))) {
			return Component.translatable("sabercraft.story.locked", menuKey());
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

		// Phase 31: a file saved in the middle of the break-out from Hale's cell goes back into it.
		if (chapter instanceof Chapter1 && Chapter1.prisonSave(progress)) {
			SaberCraft.LOGGER.info("Story file of {} moved back into Hale's cell (break-out checkpoint of an older version)", player.getPlainTextName());
		}
		// Phase 22: a save that ended the chapter before Phase 22 (finished, still at the epilogue
		// after it was spoken) knows it is over too.
		if (start == Start.CONTINUE && progress.format < 3 && progress.finished && progress.mission == missions - 1 && chapter instanceof Chapter1
				&& "m6.epilogue".equals(progress.stepId) && progress.completed.contains(missions - 1) && !progress.flags.contains(Chapter1.CHAPTER_DONE)) {
			progress.flags.add(Chapter1.CHAPTER_DONE);
		}

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
			// Phase 31: a fight left behind leaves nothing on the player or the client.
			Finale.resetFight(session);
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

	/**
	 * Phase 26: the people of the story walking in the ship that has that plot corner (not those
	 * lying beaten): a stable key and where they stand (world space, the feet), for the doors to
	 * see who comes towards them.
	 */
	public static void figuresAt(BlockPos origin, FigureSink out) {
		StorySession session = at(origin);

		if (session == null) {
			return;
		}

		String ship = origin.equals(session.falcon) ? FALCON : DESTROYER;
		Vec3 corner = Vec3.atLowerCornerOf(origin);
		long time = session.time();

		for (Figure figure : session.figures) {
			if (ship.equals(figure.ship) && figure.mode != Figure.DOWN) {
				List<Vec3> intent = new ArrayList<>();

				for (Vec3 point : figure.intent(time)) {
					intent.add(point.add(corner));
				}

				out.accept(session.player + "/" + figure.id, figure.position().add(corner), intent, figure.saber && figure.hostile);
			}
		}
	}

	/**
	 * Phase 29: one of the story's people for the doors: a stable key, where they stand (world
	 * space, the feet), where they mean to walk next (world space, see {@link Figure#intent}), and
	 * whether they fight with a saber (the boss: never a door by her moves in a fight).
	 */
	@FunctionalInterface
	public interface FigureSink {
		void accept(String key, Vec3 at, List<Vec3> intent, boolean duelist);
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
	 * Phase 22: who of the story's people sits on a seat (its sitting place in world space), for
	 * the seat to stay theirs: a player cannot sit down in someone. {@code null} if nobody.
	 */
	public static @Nullable Component sitterAt(BlockPos origin, Vec3 at) {
		StorySession session = at(origin);

		if (session == null) {
			return null;
		}

		String ship = origin.equals(session.falcon) ? FALCON : DESTROYER;
		Vec3 corner = Vec3.atLowerCornerOf(origin);

		for (Figure figure : session.figures) {
			if (ship.equals(figure.ship) && figure.mode != Figure.DOWN && figure.onSeat()) {
				Vec3 where = figure.position().add(corner);

				if (Math.abs(where.x - at.x) < 0.6 && Math.abs(where.z - at.z) < 0.6 && Math.abs(where.y - at.y) < 1.2) {
					String name = figure.speaker.isEmpty() ? figure.kind() : figure.speaker;
					return Component.translatableWithFallback("sabercraft.story.name." + name, name);
				}
			}
		}

		return null;
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

	private static int liftsRefused;

	/**
	 * Phase 26: whether the story keeps a lift shut for this player now: the one up to the command
	 * deck (and the bridge) while the finale runs (see {@link Chapter1#bridgeClosed}). Down it
	 * always goes, so nobody who was up there is shut in. The player is told why, with a short buzz.
	 */
	public static boolean liftRefused(ServerPlayer player, Vec3 arrival) {
		StorySession session = SESSIONS.get(player.getUUID());

		if (session == null || !DESTROYER.equals(session.here) || !Chapter1.bridgeClosed(session) || arrival.y < DestroyerInterior.DECK - 4) {
			return false;
		}

		liftsRefused++;
		session.hint("sabercraft.story.hint.lift_locked");
		player.connection.send(new net.minecraft.network.protocol.game.ClientboundSoundPacket(ShipInteriors.sound("story.door_lock"),
				net.minecraft.sounds.SoundSource.NEUTRAL, player.getX(), player.getY(), player.getZ(), 0.6F, 0.8F, player.getRandom().nextLong()));
		return true;
	}

	/** For the tests: how often the lift to the bridge stayed shut. */
	public static int liftsRefusedForTest() {
		return liftsRefused;
	}

	/** Phase 29: whether a story fight runs in that plot now (the doors are stricter for players then). */
	public static boolean fightingAt(BlockPos origin) {
		StorySession session = at(origin);
		return session != null && session.fighting && origin.equals(session.here != null ? session.origin(session.here) : null);
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
		return hatchClosed(player, origin, cell, null);
	}

	/**
	 * Phase 24: as above, knowing which way the hatch's room lies: while someone is after the
	 * player, nobody slips into a crawl way before their eyes (out of one, yes).
	 */
	public static boolean hatchClosed(ServerPlayer player, BlockPos origin, BlockPos cell, net.minecraft.core.@Nullable Direction room) {
		StorySession session = at(origin);

		if (session == null) {
			return false;
		}

		String ship = origin.equals(session.falcon) ? FALCON : DESTROYER;
		String why = null;

		// Phase 26: a crawl way the story means as the way out of a chase (the reactor's maintenance
		// chamber) opens with the hunters behind one: that is what it is for.
		if (room != null && DESTROYER.equals(ship) && session.pursued() && session.player.equals(player.getUUID())
				&& !session.chapter.escapeHatch(session, ship, cell.getX(), cell.getY(), cell.getZ())) {
			Vec3 from = player.position().subtract(Vec3.atCenterOf(origin.offset(cell)));

			if (from.x * room.getStepX() + from.z * room.getStepZ() > 0.0) {
				why = HUNTED;
				hatchesRefused++;
			}
		}

		if (why == null) {
			why = session.chapter.hatchClosed(session, ship, cell.getX(), cell.getY(), cell.getZ());
		}

		if (why != null && session.player.equals(player.getUUID())) {
			session.hint(why);
		}

		return why != null;
	}

	/**
	 * Phase 24: the player of a story is about to drop something: the lightsaber is not let go (it
	 * goes back into their hands, or the inventory). Returns whether it was kept.
	 */
	public static boolean keepSaber(ServerPlayer player, net.minecraft.world.item.ItemStack stack) {
		StorySession session = SESSIONS.get(player.getUUID());

		if (session == null || stack.isEmpty() || !stack.is(net.minecraft.world.item.Items.NETHERITE_SWORD) || !player.isAlive()) {
			return false;
		}

		net.minecraft.world.entity.player.Inventory inventory = player.getInventory();

		if (inventory.getSelectedItem().isEmpty()) {
			inventory.setItem(inventory.getSelectedSlot(), stack.copy());
		} else if (!inventory.add(stack.copy())) {
			return false;
		}

		player.inventoryMenu.sendAllDataToRemote();

		if (player.containerMenu != player.inventoryMenu) {
			player.containerMenu.sendAllDataToRemote();
		}

		sabersKept++;

		if (!session.timer("saber_kept")) {
			session.timer("saber_kept", 60);
			session.hint("sabercraft.story.hint.saber_kept");
		}

		return true;
	}

	private static int sabersKept;

	/** For the tests: how often a story's lightsaber was not let go. */
	public static int sabersKeptForTest() {
		return sabersKept;
	}

	/** Phase 24: how far (blocks) a story droid may be left behind before it is brought back by itself. */
	static final double DROID_LEFT_BEHIND = 22.0;
	private static int droidsReturned;

	/**
	 * Phase 24: a story droid that is gone (lost with a checkpoint, discarded) or left far behind
	 * (stuck on a lift, behind a closed door) comes back to the player by itself, not only after a
	 * capture; one told to wait waits, one on its errand is left to it, and none is fetched into a
	 * crawl way.
	 */
	static void keepDroidsNear(StorySession session, ServerPlayer player) {
		if (session.here == null || !player.isAlive() || com.phiro.sabercraft.droid.DroidEntity.narrow(player) || session.has("tmp.film")) {
			return;
		}

		for (DroidType type : storyDroids(session)) {
			com.phiro.sabercraft.droid.DroidEntity droid = Droids.droid(player, type);

			if (droid != null && (droid.isStaying() || droid.errand() != com.phiro.sabercraft.droid.DroidEntity.ERRAND_NONE)) {
				continue;
			}

			// Phase 24 (Phase 20's cell): M-31 waits outside by the bench while the field is on.
			if (type == DroidType.MOUSE && session.at("m5.cell") && session.has("m5.trapped") && !session.has("cell2.open")) {
				continue;
			}

			if (droid == null || droid.isRemoved() || droid.level() != player.level() || droid.distanceTo(player) > DROID_LEFT_BEHIND
					|| com.phiro.sabercraft.droid.DroidEntity.onLift(droid.level(), droid.blockPosition())) {
				Droids.bring(player, type);
				droidsReturned++;
			}
		}
	}

	/** For the tests: how often a story droid was brought back by itself. */
	public static int droidsReturnedForTest() {
		return droidsReturned;
	}

	/** Phase 24: the word why a hatch or a door stays shut while someone is after the player. */
	public static final String HUNTED = "sabercraft.story.hint.hunted";
	private static int hatchesRefused;

	/** For the tests: how often a hatch stayed shut because someone was after the player. */
	public static int hatchesRefusedForTest() {
		return hatchesRefused;
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

			// Phase 31: struck down in the last tick: caught now, before anything else of this tick.
			if (session.captureDue) {
				session.captureDue = false;
				capture(session, null);
			}

			// Where they are.
			BlockPos here = ShipInteriors.originAt(player.getX(), player.getZ());
			session.here = player.level() == interiors && here != null ? here.equals(session.falcon) ? FALCON : here.equals(session.destroyer) ? DESTROYER : null
					: null;

			if (session.here == null && player.level() != interiors) {
				// Out of the interiors some other way (a command): the story ends for now. (Phase 31:
				// as leaving it does, with the session: how far one got kept, things given back.)
				leave(player);
				continue;
			}

			session.stepTicks++;
			session.timers.replaceAll((name, left) -> Math.max(0, left - 1));

			if (player.tickCount % 100 == 0) {
				player.getFoodData().setFoodLevel(20);
			}

			// Phase 27: the enemies of this player's story move at its difficulty's pace.
			Stealth.pace = session.difficulty().speed;
			bearings(session);

			if (interiors != null) {
				Stealth.tick(session, player, interiors);
			}

			if (SESSIONS.get(session.player) != session) {
				Stealth.pace = 1.0;
				continue;
			}

			session.chapter.tick(session);
			session.afterTick();
			Stealth.pace = 1.0;
			followMarker(session, server.getTickCount());

			if (server.getTickCount() % 40 == 0 && session.dialog == null) {
				keepDroidsNear(session, player);
			}

			if (server.getTickCount() % 2 == 0) {
				sendFigures(session, player);
			}

			if (session.dirty && server.getTickCount() % 4 == 0) {
				session.dirty = false;
				sendState(session);
			}

			// Phase 22: often, so that a droid brought anew never shows as the normal one.
			if (server.getTickCount() % 4 == 0) {
				nameDroids(session, player);
			}
		}
	}

	/**
	 * The story's droids carry their names. Phase 22: and their marks (every client knows from it
	 * that R2-V7 is the story's own astromech: its look, its name, its key labels).
	 */
	private static void nameDroids(StorySession session, ServerPlayer player) {
		for (DroidType type : storyDroids(session)) {
			DroidEntity droid = Droids.droid(player, type);
			String name = type == DroidType.MOUSE ? "M-31" : "R2-V7";
			String mark = type == DroidType.MOUSE ? "m31" : "r2v7";

			if (droid != null && (droid.getCustomName() == null || !droid.getCustomName().getString().equals(name))) {
				droid.setCustomName(Component.literal(name));
			}

			if (droid != null && !mark.equals(com.phiro.sabercraft.droid.DroidMarker.story(droid))) {
				droid.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, com.phiro.sabercraft.droid.DroidMarker.item(type, mark));
			}
		}
	}

	/** Phase 22: the name of the story's droid of that type (R2-V7, M-31), or null for another type. */
	public static @Nullable Component droidName(DroidType type) {
		return type == DroidType.MOUSE ? Component.literal("M-31") : type == DroidType.R2D2 ? Component.literal("R2-V7") : null;
	}

	/**
	 * Phase 22: refuses a normal SaberCraft function (saber, blaster, skin, ship) while the player
	 * is in a story, and tells them so. Returns whether it was refused.
	 */
	public static boolean refuse(ServerPlayer player) {
		if (!playing(player)) {
			return false;
		}

		player.sendOverlayMessage(lockedMessage());
		return true;
	}

	/** Phase 22: what the normal SaberCraft functions answer while a story runs. */
	public static Component lockedMessage() {
		return Component.translatableWithFallback("sabercraft.story.locked", "Not available during a story mission.", menuKey());
	}

	/** Phase 27: the key of the SaberCraft menu as the player has it bound (shown by their game). */
	public static Component menuKey() {
		return Component.keybind("key.sabercraft.customize");
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
			// (Phase 31: and where those overpowered lay: that was the mission before.)
			session.flags.removeIf(flag -> flag.startsWith("tmp.") || flag.startsWith(Chapter1.DOWN_PREFIX));
			progress.missionStart.put(session.mission, List.copyOf(session.flags));
			session.begin(false);
			cue(session, CUE_TITLE, "", session.mission().title(), 0);
		} else {
			// The end of the chapter (a checkpoint where one is, in the Falcon's lounge).
			progress.finished = true;
			// Phase 22: from now on everyone knows it is over (their lines, no objective), also
			// after leaving, a restart of the game or continuing the save.
			session.set(Chapter1.CHAPTER_DONE);
			session.rebuildFigures();
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

		// Phase 27: a kept place the chapter does not count among its safe ones (a save from before:
		// a spot in a hatch, on the cot, a place that moved) gives way to the step's safe place.
		if (own && !session.chapter.trusts(session, step, progress.placeShip, new Vec3(progress.placeX, progress.placeY, progress.placeZ))) {
			own = false;
			untrusted++;
		}

		String ship = own ? progress.placeShip : safe.ship();
		Vec3 at = own ? new Vec3(progress.placeX, progress.placeY, progress.placeZ) : safe.at();
		float yaw = own ? progress.placeYaw : safe.yaw();
		player.stopRiding();
		player.setDeltaMovement(Vec3.ZERO);
		player.fallDistance = 0.0F;
		place(session, ship, at, yaw);

		// Phase 27: no room even on all fours there (a wall, a shut hatch): the step's safe place.
		if (own && !fits(player)) {
			SaberCraft.LOGGER.warn("Story: no room at the checkpoint of {} ({} {}), put at its safe place", step.id(), ship, at);
			untrusted++;
			place(session, safe.ship(), safe.at(), safe.yaw());
		}

		player.setHealth(player.getMaxHealth());

		// In a crawl way: down on all fours at once (there is no room to stand).
		if (crawlway(player)) {
			player.setPose(net.minecraft.world.entity.Pose.SWIMMING);
		}
	}

	/** Phase 27: kept places refused or without room since the start (for the tests). */
	private static int untrusted;

	public static int untrustedForTest() {
		return untrusted;
	}

	/** Phase 27: whether there is room for the player where they are, at least on all fours. */
	static boolean fits(ServerPlayer player) {
		AABB crawling = player.getDimensions(net.minecraft.world.entity.Pose.SWIMMING).makeBoundingBox(player.position()).deflate(1.0E-7);
		return player.level().noCollision(player, crawling);
	}

	/**
	 * Phase 27: back at a checkpoint, the search patrols an alarm left (kept as flags) are not
	 * there where the player is put: whoever's way passes within a few blocks of that place is not
	 * placed this time (they walked to the spot again and again, seeing the player at once).
	 */
	static void clearRespawn(StorySession session) {
		ServerPlayer player = session.player();

		if (player == null || session.here == null) {
			return;
		}

		Vec3 me = session.local(session.here);
		session.figures.removeIf(figure -> figure.key.startsWith("patrol.") && session.here.equals(figure.ship) && passesNear(figure, me, 4.0));
	}

	private static boolean passesNear(Figure figure, Vec3 at, double reach) {
		if (Math.hypot(figure.crew.x() - at.x, figure.crew.z() - at.z) < reach) {
			return true;
		}

		for (Vec3 point : figure.crew.path()) {
			if (Math.hypot(point.x - at.x, point.z - at.z) < reach && Math.abs(point.y - at.y) < 2.5) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Phase 27: the bearings after a checkpoint: while the player stays near where they were put
	 * and does nothing there, nobody sees them (at most {@link StorySession#SHIELD} ticks). Moving
	 * off, using something, a takedown or a fight ends it.
	 */
	private static void bearings(StorySession session) {
		if (session.shield <= 0) {
			return;
		}

		session.shield--;
		Vec3 at = session.shieldAt;

		if (at == null || session.here == null || session.fighting || session.alarm > 0) {
			session.shield = 0;
			return;
		}

		Vec3 me = session.local(session.here);

		if (Math.hypot(me.x - at.x, me.z - at.z) > StorySession.SHIELD_REACH || Math.abs(me.y - at.y) > 2.0) {
			session.shield = 0;
			return;
		}

		session.grace = Math.max(session.grace, 2);
	}

	/** Phase 27: the player acts (uses something, talks, overpowers someone): their bearings end. */
	static void acted(StorySession session) {
		session.shield = 0;
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
		figure.pathReaches = true;
		figure.luredFrom = null;
		cue(session, CUE_SOUND, "", "story.detected", figure.id);
		// Phase 27: seen: no dialog or menu keeps the player standing there.
		session.interrupt(true);

		// Phase 21: where the player was seen (the squad goes there when they lose sight).
		session.huntSeen = session.time();
		session.huntAt = me;

		if (Combat.pistol(figure)) {
			// Phase 23: an officer defends himself: he shouts, steps back calling for help over his
			// comlink (help comes from the nearest door a few seconds later), then draws and
			// shoots like the troops. Calling is something he does as well, not all he does.
			session.hear(voice(figure), "sabercraft.story.shout.officer_halt");
			figure.mode = Figure.CHASE;
			figure.called = session.alarm > 0 || session.has("tmp.reinforce");
			figure.cooldown = 10 + figure.id % 8;

			if (!figure.called) {
				figure.react(Figure.COMLINK, Combat.COMLINK);
			}

			Stealth.rally(session, figure, me);

			if (session.alarm <= 0) {
				session.stuns = 0;
			}

			session.raiseAlarm(Stealth.SEARCH_LONG + Stealth.LOSE);
			session.searching = false;
			session.unseen = 0;
			session.hint("sabercraft.story.hint.detected_officer");
			return;
		}

		if (soldier(figure)) {
			// Phase 20: a soldier shouts, gives chase and has the alarm raised over his comlink.
			// Phase 21: the call brings two more troopers a few seconds later (once per alarm).
			session.hear(voice(figure), "sabercraft.story.shout.halt");
			figure.mode = Figure.CHASE;
			// (Phase 27: a sentry has his rifle ready: up with it at once.)
			figure.cooldown = figure.sentry ? 0 : 10 + figure.id % 10;
			Stealth.rally(session, figure, me);

			if (session.alarm <= 0) {
				session.stuns = 0;
				session.set("tmp.reinforce");
				session.timer("reinforce", reinforceDelay(session));
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

	/**
	 * Phase 23: an officer's call over the comlink went through: the alarm over the intercom, and
	 * two troopers from the nearest door a few seconds later (once per alarm).
	 */
	static void officerCalled(StorySession session, Figure officer) {
		session.hear(voice(officer), "sabercraft.story.shout.officer_call");
		cue(session, CUE_SOUND, "", "story.radio", officer.id);

		if (!session.has("tmp.reinforce") && session.figures.stream().noneMatch(figure -> figure.key.startsWith(Stealth.REINFORCEMENT))) {
			session.set("tmp.reinforce");
			session.timer("reinforce", reinforceDelay(session));
		}

		session.raiseAlarm(Stealth.SEARCH_LONG + Stealth.LOSE);
		officerCalls++;
	}

	private static int officerCalls;

	/** For the tests: how many officers called for help over the comlink since the game started. */
	public static int officerCallsForTest() {
		return officerCalls;
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
		figure.pathReaches = true;

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
		// Phase 27: how many come, and how many may be about at once, by the difficulty.
		StoryDifficulty difficulty = session.difficulty();

		if (door == null || already >= difficulty.reinforceMax) {
			return;
		}

		int come = (int) Math.min(difficulty.reinforce, difficulty.reinforceMax - already);

		for (int i = 0; i < come; i++) {
			double side = come == 1 ? 0.0 : i == 0 ? -0.6 : i == 1 ? 0.6 : 0.0;
			double back = i >= 2 ? 1.2 : 0.0;
			Vec3 away = door.subtract(me).multiply(1.0, 0.0, 1.0);
			away = away.lengthSqr() < 1.0E-4 ? Vec3.ZERO : away.normalize().scale(back);
			Figure template = new Figure(0, Stealth.REINFORCEMENT + session.figures.size() + "." + i,
					Crew.at(Crew.TROOPER, Crew.STAND, door.x + side + away.x, door.y, door.z + away.z, Crew.yaw(door, me)));
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
		// (Phase 27: a hit ends a dialog; menus stay, so the story's menu can still be used in a chase.)
		session.interrupt(false);
		cue(session, CUE_SOUND, "", "story.stun_hit", 0);
		StoryDifficulty level = session.difficulty();
		cue(session, CUE_HINT, "stun", level == StoryDifficulty.NORMAL ? "sabercraft.story.hint.stunned" : "sabercraft.story.hint.stunned_" + level.id,
				session.stuns);

		// Phase 27: Easy one more, Hard one fewer.
		if (session.stuns >= session.difficulty().stunHits) {
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
		session.interrupt(true);
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
		session.interrupt(true);
		session.grace = StorySession.GRACE;
		session.fighting = false;
		session.begin(true);
		session.radio(Chapter1.captureSpeaker(session), Chapter1.captureLine(session));
		sendState(session);
	}

	/** A section that had to be done unseen failed: back to its checkpoint, with a word why. */
	public static void fail(StorySession session, String hint) {
		session.interrupt(true);
		session.grace = StorySession.GRACE;
		cue(session, CUE_FADE, "", hint, 30);
		session.begin(true);
		session.hint(hint);
		sendState(session);
	}

	// --- Network -----------------------------------------------------------------------------------

	static void action(ServerPlayer player, StoryActionPayload payload) {
		StorySession session = SESSIONS.get(player.getUUID());

		// Phase 31: struck down in the last tick and not caught yet (that waits for the next tick):
		// caught now, before this input is handled. An input of the old fight (a stroke, the Focus,
		// a key in a scene) then counts no more: it would play out against the fight just ended.
		if (session != null && session.captureDue) {
			switch (payload.action()) {
				case StoryActionPayload.ANSWER, StoryActionPayload.USE, StoryActionPayload.TALK, StoryActionPayload.TAKEDOWN, StoryActionPayload.SKIP,
						StoryActionPayload.STRIKE, StoryActionPayload.COVER, StoryActionPayload.FOCUS -> {
					session.captureDue = false;
					capture(session, null);
					session.afterTick();
					return;
				}
				case StoryActionPayload.LEAVE -> {
					// Leaving right then: with the state of the checkpoint, not of the fight.
					session.captureDue = false;
					capture(session, null);
				}
				default -> {
				}
			}
		}

		switch (payload.action()) {
			case StoryActionPayload.CONTINUE -> message(player, enter(player, Start.CONTINUE, 0));
			case StoryActionPayload.RESTART -> {
				if (session != null) {
					// Phase 31: written first: entering keeps the way back (where one came from, the
					// game mode, one's own droids) and what is known for good only in memory until a
					// checkpoint; the new start reads the file and would take the story's ship, its
					// mode and its droids for them.
					save(session);
					SESSIONS.remove(player.getUUID());
					keepDifficulty(player, session);
				}

				message(player, enter(player, Start.RESTART, 0));
			}
			case StoryActionPayload.MISSION -> {
				if (session != null) {
					save(session);
					SESSIONS.remove(player.getUUID());
					keepDifficulty(player, session);
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
				} else if (ServerPlayNetworking.canSend(player, StoryDialogPayload.TYPE)) {
					// Phase 27: out of the story: the screen closes (nobody would answer it).
					ServerPlayNetworking.send(player, StoryDialogPayload.closed());
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
			case StoryActionPayload.TAKEDOWN -> {
				if (session != null) {
					takedown(session, player, payload.value());
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
					acted(session);
					chapter.strike(session, player);
					session.afterTick();
				}
			}
			case StoryActionPayload.DIFFICULTY -> difficulty(player, session, payload.value());
			case StoryActionPayload.COVER -> {
				if (session != null && session.chapter instanceof Chapter1) {
					Chapter1.coverUnscrewed(session, player);
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
	 * Phase 27: a player chose the story's difficulty in the menu. Kept with their save (each
	 * player their own). Out of the story it holds from the next start; with no save yet it waits
	 * for the story's start (a file is not written for it alone). In a running mission it takes
	 * effect at once from the last checkpoint: everyone is placed anew for it there (as after being
	 * caught, but not counted as caught), so no enemy appears or vanishes in the middle of a scene
	 * and nothing done before that checkpoint is lost.
	 */
	static void difficulty(ServerPlayer player, @Nullable StorySession session, int index) {
		StoryDifficulty chosen = StoryDifficulty.byIndex(index);
		MinecraftServer server = player.level().getServer();

		if (session == null) {
			if (!StoryStore.exists(server, player.getUUID())) {
				PENDING_DIFFICULTY.put(player.getUUID(), chosen.id);
				return;
			}

			StoryProgress progress = StoryStore.load(server, player.getUUID());

			if (!chosen.id.equals(progress.difficulty)) {
				progress.difficulty = chosen.id;
				StoryStore.save(server, player.getUUID(), progress);
			}

			sendState(player, progress, null);
			return;
		}

		if (chosen.id.equals(session.progress.difficulty)) {
			sendState(session);
			return;
		}

		session.progress.difficulty = chosen.id;
		save(session);
		difficultyChanges++;
		// (A dialog ends; screens stay: the change is sent as the menu closes or a tab of it opens,
		// and the tab just opened should not close again.)
		session.interrupt(false);
		session.grace = StorySession.GRACE;
		session.fighting = false;
		cue(session, CUE_FADE, "", "sabercraft.story.difficulty.changed." + chosen.id, 30);
		session.begin(true);
		session.hint("sabercraft.story.difficulty.changed." + chosen.id);
		sendState(session);
	}

	/**
	 * Phase 27: a story started again (or a mission chosen) before its first checkpoint was saved
	 * keeps the difficulty it ran at (with no file yet, the new start would read Normal).
	 */
	private static void keepDifficulty(ServerPlayer player, StorySession session) {
		if (!StoryStore.exists(player.level().getServer(), player.getUUID())) {
			PENDING_DIFFICULTY.putIfAbsent(player.getUUID(), session.progress.difficulty);
		}
	}

	/** Phase 27: a difficulty chosen before the story's first start (by player), until it starts. */
	private static final Map<UUID, String> PENDING_DIFFICULTY = new HashMap<>();
	private static int difficultyChanges;

	/** Phase 27: how long until help comes after a call (later on Easy, sooner on Hard). */
	static int reinforceDelay(StorySession session) {
		return session.difficulty().ticks(Stealth.REINFORCE_DELAY, session.difficulty().cooldown);
	}

	/** For the tests: how often a running story was set back to its checkpoint for a new difficulty. */
	public static int difficultyChangesForTest() {
		return difficultyChanges;
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
		if (session.dialog != null) {
			// Phase 27: a dialog is still open here, but the player's screen is not (they could not
			// click otherwise): its line comes back instead of nothing happening.
			sendDialog(session);
			return true;
		}

		if (session.here == null) {
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

				acted(session);
				session.chapter.use(session, point);
				return true;
			}
		}

		return false;
	}

	/** Speaks to someone, if they are there and within reach. */
	public static boolean talk(StorySession session, ServerPlayer player, int id) {
		if (session.dialog != null) {
			// Phase 27: as for use: the open line comes back.
			sendDialog(session);
			return true;
		}

		if (session.here == null) {
			return false;
		}

		for (Figure figure : session.figures) {
			if (figure.id == id && session.here.equals(figure.ship) && figure.talks && figure.mode != Figure.DOWN && session.chapter.talksNow(session, figure)) {
				AABB box = figure.box().move(Vec3.atLowerCornerOf(session.origin(figure.ship)));

				if (box.distanceToSqr(player.getEyePosition()) > REACH * REACH) {
					return false;
				}

				acted(session);
				return session.chapter.talk(session, figure);
			}
		}

		return false;
	}

	/**
	 * Phase 31: the player tries to overpower someone (the use key on them while the prompt says
	 * so), if they are there and within reach.
	 */
	public static boolean takedown(StorySession session, ServerPlayer player, int id) {
		if (session.dialog != null) {
			sendDialog(session);
			return true;
		}

		Figure figure = session.figureById(id);

		if (session.here == null || figure == null || !session.here.equals(figure.ship) || figure.mode == Figure.DOWN) {
			return false;
		}

		AABB box = figure.box().move(Vec3.atLowerCornerOf(session.origin(figure.ship)));

		if (box.distanceToSqr(player.getEyePosition()) > REACH * REACH) {
			return false;
		}

		acted(session);
		return session.chapter.takedown(session, figure);
	}

	static void sendState(StorySession session) {
		ServerPlayer player = session.player();

		if (player != null) {
			session.markerSent = marker(session);
			sendState(player, session.progress, session);
		}
	}

	/** Phase 27: where the marker points now (world space), or null. */
	static @Nullable Vec3 marker(StorySession session) {
		Mission.Target goal = session.chapter instanceof Chapter1 c1 ? c1.target(session) : session.step().target();
		return goal == null ? null : goal.at().add(Vec3.atLowerCornerOf(session.origin(goal.ship())));
	}

	/**
	 * Phase 27: the marker follows where it should point although nothing else changed (it hangs
	 * on where the player is: from the first crawl way on to the hideout, past the north hatch on
	 * to the containers, over a guard who moves): checked a few times a second, sent when it moved.
	 */
	private static void followMarker(StorySession session, int tick) {
		if (session.dirty || tick % 5 != 2) {
			return;
		}

		Vec3 now = marker(session);
		Vec3 sent = session.markerSent;

		if ((now == null) != (sent == null) || now != null && now.distanceToSqr(sent) > 0.36) {
			session.dirty = true;
			markerMoves++;
		}
	}

	private static int markerMoves;

	/** For the tests: how often the marker was sent again because it moved by itself. */
	public static int markerMovesForTest() {
		return markerMoves;
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

		// Phase 27: the difficulty (for the menu and the HUD).
		known.add("difficulty." + StoryDifficulty.of(saved ? progress.difficulty : PENDING_DIFFICULTY.getOrDefault(player.getUUID(), progress.difficulty)).id);

		if (session != null) {
			Mission.Step step = session.step();

			// Phase 26: the Falcon waits under the hangar's field while one escapes (drawn by the client).
			if (Chapter1.falconWaits(session)) {
				known.add(Chapter1.FALCON_WAITING);
			}

			// Phase 22: after the chapter no objective is left: the client shows that it is over.
			objective = session.has(Chapter1.CHAPTER_DONE) ? "sabercraft.story.obj.chapter_done"
					: session.chapter instanceof Chapter1 ? Chapter1.objective(session, step) : step.objective();

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
					| (f.talks && session.chapter.talksNow(session, f) ? StoryFiguresPayload.TALKS : 0) | (f.observer ? StoryFiguresPayload.OBSERVER : 0) | (f.lit ? StoryFiguresPayload.LIT : 0)
					| (Combat.pistol(f) && f.observer && (f.mode == Figure.CHASE || f.mode == Figure.FIGHT) && f.react != Figure.COMLINK ? StoryFiguresPayload.ARMED : 0)
					| (Finale.disarmed(f) ? StoryFiguresPayload.UNARMED : 0) | (f.ringOn && f.saber && f.lit ? StoryFiguresPayload.RING_SPIN : 0)
					| (Finale.spinLive(f) ? StoryFiguresPayload.SPIN_LIVE : 0) | (f.saber && Finale.phase(f) >= 2 ? StoryFiguresPayload.TWIN : 0)
					| (session.chapter.takedownReady(session, f, me) ? StoryFiguresPayload.TAKEDOWN : 0);
			list.add(new StoryFiguresPayload.Fig(f.id, f.kind(), f.pose(), f.speaker, (float) f.x, (float) f.y, (float) f.z, f.yaw, f.headYaw, f.speed, f.stage,
					Math.round(f.suspicion * 255.0F), flags, Math.round(Math.max(0.0F, f.health) * 255.0F), Finale.shown(f), f.reactShown()));
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

		// (Phase 27: nor one running to the wall comm or after the player: the call is not held up by the talk.)
		if (partner != null && partner.mode != Figure.SCRIPTED && partner.mode != Figure.FIGHT && partner.mode != Figure.DOWN && partner.mode != Figure.CALL
				&& partner.mode != Figure.CHASE) {
			// They stop and look at the player (unless a script sends them off, or they fight).
			partner.mode = Figure.HELD;
			partner.target = session.local(partner.ship);
			// Phase 26: and talk with them (standing: with their hands; on a seat: sitting).
			partner.talking = true;
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

		if (cue == CUE_CINEMA) {
			// Phase 27: a film begins: a dialog still open ends first (the film's screen took its place
			// on the client and nobody could answer it any more; the story then waited for it forever,
			// as after Dax's jump: the arrival film over, the objective stayed "talk to Dax").
			session.interrupt(false);
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
