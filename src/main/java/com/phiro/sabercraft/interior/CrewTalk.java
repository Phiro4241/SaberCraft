package com.phiro.sabercraft.interior;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Phase 18: speaking to the crew. A player right-clicks someone of a ship's crew (the client
 * finds them along its view and asks the server, which looks again along the player's view at
 * the same game time): they answer with a short line in the chat, for that player only, and a
 * radio click (helmets) or a chirp (everyone else) where they stand. Each says lines of their
 * role (the room they are in, their job), one after the other from a place of their own, so the
 * same line does not come twice in a row; a few seconds pass before the same one answers again.
 * Now and then someone near a player remarks on something by themselves (rarely, never twice in
 * a row the same). Text only: no voices (the sounds are made by {@code tools/synth_sounds.py}).
 */
public final class CrewTalk {
	private CrewTalk() {
	}

	/** How far one can speak to someone (blocks from the eyes). */
	public static final double REACH = 4.5;
	/** Ticks before the same one answers the same player again, and between any two answers. */
	static final int COOLDOWN = 80;
	static final int ANY_COOLDOWN = 20;
	/** Ticks between spontaneous remarks to a player, at least (and up to twice that). */
	static final int REMARK_EVERY = 1800;
	static final double REMARK_REACH = 7.0;

	/**
	 * How many lines each role has ({@code sabercraft.crew.line.<role>.<n>}, from 1). A crew member
	 * without a role speaks by kind.
	 */
	static final Map<String, Integer> LINES = Map.ofEntries(Map.entry("trooper", 5), Map.entry("officer", 4), Map.entry("tech", 4),
			Map.entry("watch", 4), Map.entry("bridge", 4), Map.entry("bridge_guard", 4), Map.entry("pit", 4), Map.entry("patrol", 5),
			Map.entry("sensor", 3), Map.entry("conference", 3), Map.entry("hangar", 4), Map.entry("flight_control", 3), Map.entry("reactor", 4),
			Map.entry("admiral", 4), Map.entry("security", 4), Map.entry("army", 3), Map.entry("pilot", 4), Map.entry("guard", 3),
			Map.entry("maintenance", 3), Map.entry("spy", 4), Map.entry("smuggler", 6), Map.entry("wookiee", 4), Map.entry("rebel", 4));
	/** Spontaneous remarks by ship ({@code sabercraft.crew.remark.<plan>.<n>}). */
	static final Map<String, Integer> REMARKS = Map.of("star_destroyer", 8, "falcon", 6);

	/** Someone of a crew hit along a line: their place in the plan's list and how far along. */
	public record Hit(int index, Crew crew, double distance) {
	}

	/** The crew member first hit along from..to (plot space) at that game time, boxes grown by {@code grow}. */
	public static @Nullable Hit hit(List<Crew> crew, Vec3 from, Vec3 to, double ticks, double grow) {
		Hit best = null;

		for (int i = 0; i < crew.size(); i++) {
			AABB box = crew.get(i).box(ticks).inflate(grow);
			Vec3 at = box.contains(from) ? from : box.clip(from, to).orElse(null);

			if (at != null) {
				double d = at.distanceTo(from);

				if (best == null || d < best.distance()) {
					best = new Hit(i, crew.get(i), d);
				}
			}
		}

		return best;
	}

	private static final Map<UUID, Long> LAST = new HashMap<>();
	private static final Map<String, Long> LAST_BY_ONE = new HashMap<>();
	private static final Map<String, Integer> NEXT_LINE = new HashMap<>();
	private static final Map<UUID, Long> NEXT_REMARK = new HashMap<>();
	private static final Map<UUID, Integer> LAST_REMARK = new HashMap<>();

	/**
	 * A player asks to speak to whoever they look at. Returns whether someone of the crew was
	 * there (and answered, or was still busy answering).
	 */
	public static boolean talk(ServerPlayer player) {
		ServerLevel level = player.level();

		if (!ShipInteriors.isInterior(level) || player.isSpectator()) {
			return false;
		}

		InteriorPlan plan = ShipInteriors.planAt(player.getX(), player.getZ());
		BlockPos origin = ShipInteriors.originAt(player.getX(), player.getZ());

		if (plan == null || origin == null || plan.crew().isEmpty()) {
			return false;
		}

		// Phase 19: in a story's ship the people are the story's (spoken to through the story).
		if (com.phiro.sabercraft.story.Story.at(origin) != null) {
			return false;
		}

		Vec3 corner = Vec3.atLowerCornerOf(origin);
		Vec3 eye = player.getEyePosition();
		Vec3 to = eye.add(player.getViewVector(1.0F).scale(REACH));
		// A little more room than the client gave (it saw them a tick or two earlier).
		Hit hit = hit(plan.crew(), eye.subtract(corner), to.subtract(corner), level.getGameTime(), 0.2);

		if (hit == null) {
			return false;
		}

		// Not through a wall: what the eyes meet first must not be much nearer than them.
		BlockHitResult wall = level.clip(new ClipContext(eye, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));

		if (wall.getType() == HitResult.Type.BLOCK && wall.getLocation().distanceTo(eye) < hit.distance() - 0.7) {
			return false;
		}

		answer(player, plan, origin, hit.index(), hit.crew());
		return true;
	}

	/** The role whose lines one speaks. */
	public static String role(Crew crew) {
		if (!crew.role().isEmpty() && LINES.containsKey(crew.role())) {
			return crew.role();
		}

		if (crew.officer()) {
			return "officer";
		}

		return LINES.containsKey(crew.kind()) ? crew.kind() : "trooper";
	}

	/** What one is called in the chat ({@code sabercraft.crew.name.<kind>}). */
	public static Component name(Crew crew) {
		return Component.translatable("sabercraft.crew.name." + crew.kind());
	}

	/** The next line of one of the crew for a player: a fresh place in their lines, then on in turn. */
	static int nextLine(ServerPlayer player, String plan, int index, String role) {
		int count = LINES.getOrDefault(role, 1);
		String key = player.getUUID() + "/" + plan + "/" + index;
		Integer next = NEXT_LINE.get(key);
		int line = next != null ? next : Math.floorMod(player.getRandom().nextInt(), count);
		NEXT_LINE.put(key, (line + 1) % count);
		return line + 1;
	}

	private static void answer(ServerPlayer player, InteriorPlan plan, BlockPos origin, int index, Crew crew) {
		long now = player.level().getGameTime();
		String key = player.getUUID() + "/" + plan.id() + "/" + index;
		Long last = LAST.get(player.getUUID());
		Long lastHere = LAST_BY_ONE.get(key);

		if (last != null && now - last < ANY_COOLDOWN || lastHere != null && now - lastHere < COOLDOWN) {
			return;
		}

		LAST.put(player.getUUID(), now);
		LAST_BY_ONE.put(key, now);
		String role = role(crew);
		int line = nextLine(player, plan.id(), index, role);
		say(player, crew, Component.translatable("sabercraft.crew.line." + role + "." + line));
		sound(player.level(), origin, crew, now);
		// Spoken to: no remark by itself for a while.
		NEXT_REMARK.put(player.getUUID(), now + REMARK_EVERY);
		said++;
	}

	private static void say(ServerPlayer player, Crew crew, Component line) {
		player.sendSystemMessage(Component.translatable("sabercraft.crew.says", name(crew).copy().withStyle(ChatFormatting.GOLD), line.copy().withStyle(ChatFormatting.GRAY)));
	}

	private static void sound(ServerLevel level, BlockPos origin, Crew crew, long now) {
		double[] at = crew.where(now);
		boolean radio = crew.helmeted();
		level.playSound(null, origin.getX() + at[0], origin.getY() + at[1] + 1.6, origin.getZ() + at[2],
				ShipInteriors.sound(radio ? "ship.crew_comm" : "ship.crew_chirp"), SoundSource.NEUTRAL, radio ? 0.45F : 0.3F,
				0.95F + level.getRandom().nextFloat() * 0.1F);
	}

	/** Every second: now and then someone near a player says something by themselves. */
	static void tick(MinecraftServer server) {
		ServerLevel level = server.getLevel(ShipInteriors.DIMENSION);

		if (level == null || level.getGameTime() % 20 != 0) {
			return;
		}

		long now = level.getGameTime();

		for (ServerPlayer player : level.players()) {
			if (player.isSpectator()) {
				continue;
			}

			Long next = NEXT_REMARK.get(player.getUUID());

			if (next == null) {
				NEXT_REMARK.put(player.getUUID(), now + REMARK_EVERY / 2 + player.getRandom().nextInt(REMARK_EVERY));
				continue;
			}

			if (now < next) {
				continue;
			}

			NEXT_REMARK.put(player.getUUID(), now + REMARK_EVERY + player.getRandom().nextInt(REMARK_EVERY));
			remark(player, now);
		}
	}

	/** Someone near the player (in sight, a few blocks) remarks on something. Returns whether anyone did. */
	public static boolean remark(ServerPlayer player, long now) {
		InteriorPlan plan = ShipInteriors.planAt(player.getX(), player.getZ());
		BlockPos origin = ShipInteriors.originAt(player.getX(), player.getZ());

		if (plan == null || origin == null || !REMARKS.containsKey(plan.id())) {
			return false;
		}

		// Phase 24: in a story's ship nobody chats while an alarm is on or they shoot.
		com.phiro.sabercraft.story.StorySession story = com.phiro.sabercraft.story.Story.at(origin);

		if (story != null && story.danger()) {
			return false;
		}

		Vec3 eye = player.getEyePosition();
		Crew nearest = null;
		double best = REMARK_REACH;

		for (Crew crew : plan.crew()) {
			if (Crew.GUARD.equals(crew.kind())) {
				// The honour guards do not chat.
				continue;
			}

			double[] at = crew.where(now);
			Vec3 mouth = new Vec3(origin.getX() + at[0], origin.getY() + at[1] + crew.height() - 0.2, origin.getZ() + at[2]);
			double d = mouth.distanceTo(eye);

			if (d < best && Math.abs(mouth.y - eye.y) < 2.5) {
				BlockHitResult wall = player.level().clip(new ClipContext(eye, mouth, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));

				if (wall.getType() != HitResult.Type.BLOCK || wall.getLocation().distanceTo(mouth) < 0.9) {
					nearest = crew;
					best = d;
				}
			}
		}

		if (nearest == null) {
			return false;
		}

		int count = REMARKS.get(plan.id());
		int last = LAST_REMARK.getOrDefault(player.getUUID(), -1);
		int line = Math.floorMod(player.getRandom().nextInt(), count);

		if (line == last) {
			line = (line + 1) % count;
		}

		LAST_REMARK.put(player.getUUID(), line);
		say(player, nearest, Component.translatable("sabercraft.crew.remark." + plan.id() + "." + (line + 1)));
		sound(player.level(), origin, nearest, now);
		said++;
		return true;
	}

	/** Forgets a player who left. */
	static void forget(UUID player) {
		LAST.remove(player);
		NEXT_REMARK.remove(player);
		LAST_REMARK.remove(player);
		String prefix = player + "/";
		LAST_BY_ONE.keySet().removeIf(key -> key.startsWith(prefix));
		NEXT_LINE.keySet().removeIf(key -> key.startsWith(prefix));
	}

	/** For tests: forgets every cooldown. */
	public static void forgetForTest() {
		LAST.clear();
		LAST_BY_ONE.clear();
	}

	private static int said;

	/** For tests: how many answers and remarks were given. */
	public static int saidForTest() {
		return said;
	}
}
