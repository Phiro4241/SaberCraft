package com.phiro.sabercraft.client.interior;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import com.phiro.sabercraft.interior.InteriorPlan;
import com.phiro.sabercraft.interior.InteriorPlan.Door;
import com.phiro.sabercraft.interior.InteriorPlan.Seat;
import com.phiro.sabercraft.interior.ProxyShapes;
import com.phiro.sabercraft.interior.ShipInteriors;
import com.phiro.sabercraft.interior.Turrets;
import com.phiro.sabercraft.network.InteriorActionPayload;
import com.phiro.sabercraft.network.InteriorJumpPayload;

/**
 * Phase 16: what this client knows about the interiors beyond their models: the seat the player
 * sits on (the attack key fires the guns from a gunner's seat), how far each door is open (from
 * its blocks, eased), where every gunner aims (from their rotation, so everyone sees the guns
 * turn), whether the lights are dimmed, and the hyperspace jumps running in a plot (Phase 31:
 * only the story's).
 */
public final class InteriorClient {
	/** How fast doors slide (share of the way per second). */
	static final float DOOR_SPEED = 3.0F;

	/** A jump in a plot: which stage, the game tick it started, how long it lasts. */
	public record Jump(byte stage, long start, int ticks) {
	}

	private static final Map<BlockPos, Jump> JUMPS = new HashMap<>();
	/**
	 * Phase 22: how far each door is open and when that was worked out. The time was kept in a
	 * float before: on a computer that has run for a day or more {@code System.nanoTime()} is
	 * beyond 10^14 and a float holds it only to some 10 to 70 milliseconds, so the time between
	 * two frames came out as 0, twice as long or even negative, and every door shook back and forth
	 * while it moved (tests, run right after the machine started, never saw it).
	 */
	private static final class DoorState {
		float open;
		long nanos;

		DoorState(float open, long nanos) {
			this.open = open;
			this.nanos = nanos;
		}
	}

	private static final Map<Long, DoorState> DOORS = new HashMap<>();
	private static final Map<String, List<long[]>> LIGHTS = new HashMap<>();
	private static int fired;

	/** The seat the player sits on, in which plot. */
	public record Sitting(BlockPos origin, InteriorPlan plan, Seat seat) {
	}

	private InteriorClient() {
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(InteriorJumpPayload.TYPE, (payload, context) -> {
			if (context.client().level != null) {
				JUMPS.put(payload.origin(), new Jump(payload.stage(), context.client().level.getGameTime(), payload.ticks()));
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			JUMPS.clear();
			DOORS.clear();
		});
		ClientTickEvents.START_CLIENT_TICK.register(InteriorClient::input);
		// No breaking the dome round a gunner while they fire (the server refuses it too).
		AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> level.isClientSide() && sitting(Minecraft.getInstance()) != null
				? InteractionResult.FAIL : InteractionResult.PASS);
	}

	/** The seat the local player sits on, or {@code null}. */
	public static @Nullable Sitting sitting(Minecraft client) {
		return client.player != null ? sitting(client.player) : null;
	}

	/** The seat a player sits on (as this client sees it), or {@code null}. */
	public static @Nullable Sitting sitting(Player player) {
		Entity vehicle = player.getVehicle();

		if (!(vehicle instanceof Display.ItemDisplay) || !ShipInteriors.isInterior(player.level())) {
			return null;
		}

		InteriorPlan plan = ShipInteriors.planAt(vehicle.getX(), vehicle.getZ());
		BlockPos origin = ShipInteriors.originAt(vehicle.getX(), vehicle.getZ());

		if (plan == null || origin == null) {
			return null;
		}

		for (Seat seat : plan.seats()) {
			if (seat.at().add(origin.getX(), origin.getY(), origin.getZ()).distanceToSqr(vehicle.position()) < 0.01) {
				return new Sitting(origin, plan, seat);
			}
		}

		return null;
	}

	/** Every tick: the trigger of the guns, while sitting at them. */
	private static void input(Minecraft client) {
		// Phase 26: the use key let go after it shut a hatch: the next press may open it again
		// (heldAfterHatch is only asked while the key is down, so it is forgotten here).
		if (shutHatch != null && !client.options.keyUse.isDown()) {
			shutHatch = null;
		}

		Sitting sitting = sitting(client);

		if (sitting == null || !Seat.GUNNER.equals(sitting.seat().role()) || client.gui.screen() != null) {
			return;
		}

		boolean clicked = false;

		while (client.options.keyAttack.consumeClick()) {
			clicked = true;
		}

		if ((clicked || client.options.keyAttack.isDown()) && ClientPlayNetworking.canSend(InteriorActionPayload.TYPE)) {
			ClientPlayNetworking.send(new InteriorActionPayload(InteriorActionPayload.FIRE));
			fired++;
		}
	}

	/**
	 * Phase 18: the use key on someone of the crew (nearer than the block or creature in the
	 * crosshair): asks the server to let them answer. Returns whether it did (the key then does
	 * nothing else).
	 */
	public static boolean talk(Minecraft client) {
		if (client.player == null || client.level == null || client.gui.screen() != null || client.player.isSpectator()
				|| !ShipInteriors.isInterior(client.level) || !ClientPlayNetworking.canSend(InteriorActionPayload.TYPE)) {
			return false;
		}

		InteriorPlan plan = ShipInteriors.planAt(client.player.getX(), client.player.getZ());
		BlockPos origin = ShipInteriors.originAt(client.player.getX(), client.player.getZ());

		// Phase 19: in a story's ship its people are the story's (StoryInput speaks to them).
		if (plan == null || origin == null || plan.crew().isEmpty() || com.phiro.sabercraft.client.story.StoryClient.storyPlot(origin)) {
			return false;
		}

		float partial = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		net.minecraft.world.phys.Vec3 corner = net.minecraft.world.phys.Vec3.atLowerCornerOf(origin);
		net.minecraft.world.phys.Vec3 eye = client.player.getEyePosition(partial);
		net.minecraft.world.phys.Vec3 to = eye.add(client.player.getViewVector(partial).scale(com.phiro.sabercraft.interior.CrewTalk.REACH));
		com.phiro.sabercraft.interior.CrewTalk.Hit hit = com.phiro.sabercraft.interior.CrewTalk.hit(plan.crew(), eye.subtract(corner), to.subtract(corner),
				client.level.getGameTime() + partial, 0.04);

		if (hit == null) {
			return false;
		}

		net.minecraft.world.phys.HitResult aimed = client.hitResult;

		if (aimed != null && aimed.getType() != net.minecraft.world.phys.HitResult.Type.MISS && aimed.getLocation().distanceTo(eye) < hit.distance() - 0.35) {
			return false;
		}

		ClientPlayNetworking.send(new InteriorActionPayload(InteriorActionPayload.TALK));
		client.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
		talked++;
		return true;
	}

	private static int talked;
	private static int hatches;

	/**
	 * Phase 20: the use key on an open hatch of a crawl way (nothing solid nearer): asks the server
	 * to shut it. Returns whether it did (the key then does nothing else).
	 */
	public static boolean hatch(Minecraft client) {
		if (client.player == null || client.level == null || client.gui.screen() != null || client.player.isSpectator()
				|| !ShipInteriors.isInterior(client.level) || !ClientPlayNetworking.canSend(InteriorActionPayload.TYPE)
				|| com.phiro.sabercraft.interior.Vents.looked(client.player) == null) {
			return false;
		}

		shutHatch = com.phiro.sabercraft.interior.Vents.looked(client.player);
		ClientPlayNetworking.send(new InteriorActionPayload(InteriorActionPayload.HATCH));
		client.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
		hatches++;
		return true;
	}

	/**
	 * Phase 27: the use key on a shut hatch (it goes to the server as a click on the block, which
	 * opens it): the player is ready to crawl in, here as on the server, so that both put them down
	 * on all fours in front of it. Never takes the key.
	 */
	public static boolean hatchIntent(Minecraft client) {
		if (client.player == null || client.level == null || client.player.getPose() == net.minecraft.world.entity.Pose.SWIMMING
				|| !ShipInteriors.isInterior(client.level) || !(client.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit)
				|| hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) {
			return false;
		}

		BlockPos at = hit.getBlockPos();
		BlockPos origin = ShipInteriors.originAt(at.getX() + 0.5, at.getZ() + 0.5);
		com.phiro.sabercraft.interior.InteriorPlan plan = ShipInteriors.planAt(at.getX() + 0.5, at.getZ() + 0.5);

		if (origin == null || plan == null || !client.level.getBlockState(at).is(net.minecraft.world.level.block.Blocks.BARRIER)) {
			return false;
		}

		for (com.phiro.sabercraft.interior.InteriorPlan.Hatch hatch : plan.hatches()) {
			if (origin.offset(hatch.cell()).equals(at)) {
				com.phiro.sabercraft.interior.Vents.intend(client.player);
				intents++;
				return false;
			}
		}

		return false;
	}

	private static int intents;

	/**
	 * Phase 27: whether the player looks at a hatch of a crawl way within reach, shut (the block
	 * aimed at) or open ({@link com.phiro.sabercraft.interior.Vents#looked}).
	 */
	public static boolean aimsAtHatch(Minecraft client) {
		if (client.player == null || client.level == null || !ShipInteriors.isInterior(client.level)) {
			return false;
		}

		if (com.phiro.sabercraft.interior.Vents.looked(client.player) != null) {
			return true;
		}

		if (!(client.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit) || hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK
				|| hit.getLocation().distanceTo(client.player.getEyePosition()) > com.phiro.sabercraft.interior.Vents.REACH) {
			return false;
		}

		BlockPos at = hit.getBlockPos();
		BlockPos origin = ShipInteriors.originAt(at.getX() + 0.5, at.getZ() + 0.5);
		com.phiro.sabercraft.interior.InteriorPlan plan = ShipInteriors.planAt(at.getX() + 0.5, at.getZ() + 0.5);

		if (origin == null || plan == null) {
			return false;
		}

		for (com.phiro.sabercraft.interior.InteriorPlan.Hatch hatch : plan.hatches()) {
			if (origin.offset(hatch.cell()).equals(at)) {
				return true;
			}
		}

		return false;
	}

	/** For tests: how often the use key on a shut hatch got the player ready to crawl in. */
	public static int intentsForTest() {
		return intents;
	}

	/** Phase 26: the hatch the use key just shut, while the key is still held (else null). */
	private static @Nullable BlockPos shutHatch;

	/**
	 * Phase 26: whether the use key, still held since it shut a hatch, points at that hatch (now a
	 * closed block): it does nothing then. Released once, the key opens the hatch again as before.
	 */
	public static boolean heldAfterHatch(Minecraft client) {
		BlockPos shut = shutHatch;

		if (shut == null) {
			return false;
		}

		if (client.player == null || !client.options.keyUse.isDown()) {
			shutHatch = null;
			return false;
		}

		return client.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit && hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK
				&& hit.getBlockPos().equals(shut);
	}

	/** For tests: how often the use key shut a hatch. */
	public static int hatchesForTest() {
		return hatches;
	}

	/** For tests: how often the use key spoke to the crew. */
	public static int talkedForTest() {
		return talked;
	}

	// --- Jumps -------------------------------------------------------------------------------------

	/** The jump running in a plot, or {@code null}. */
	public static @Nullable Jump jump(BlockPos origin, ClientLevel level) {
		Jump jump = JUMPS.get(origin);

		if (jump != null && level.getGameTime() > jump.start() + jump.ticks() + 2) {
			JUMPS.remove(origin);
			return null;
		}

		return jump;
	}

	/** How far a jump's stage is, 0 to 1 (with the frame's partial tick). */
	public static float progress(Jump jump, ClientLevel level, float partial) {
		return Mth.clamp((level.getGameTime() - jump.start() + partial) / Math.max(1, jump.ticks()), 0.0F, 1.0F);
	}

	// --- Doors -------------------------------------------------------------------------------------

	/** How far a door is open now, 0 to 1: sliding towards what its blocks say. */
	public static float door(InteriorVisuals.PlotView plot, Door door) {
		BlockPos cell = plot.origin().offset(door.x0(), door.y0(), door.z0());
		boolean open = !plot.level().getBlockState(cell).is(Blocks.BARRIER);
		long now = System.nanoTime();
		DoorState state = DOORS.computeIfAbsent(cell.asLong(), unused -> new DoorState(open ? 1.0F : 0.0F, now));
		// Exact to the nanosecond (a long), never backwards, at most a quarter second at once.
		float seconds = Mth.clamp((now - state.nanos) / 1.0E9F, 0.0F, 0.25F);
		state.nanos = now;
		state.open = Mth.clamp(state.open + (open ? 1 : -1) * DOOR_SPEED * seconds, 0.0F, 1.0F);
		return state.open;
	}

	// --- Guns --------------------------------------------------------------------------------------

	/** Where the gunner of a turret aims (yaw, pitch), or {@code null} if nobody sits there. */
	public static float @Nullable [] aim(InteriorVisuals.PlotView plot, boolean upper, float partial) {
		for (Seat seat : InteriorPlan.of("falcon").seats()) {
			if (!Seat.GUNNER.equals(seat.role()) || Turrets.upper(seat) != upper) {
				continue;
			}

			Vec3 at = seat.at().add(plot.origin().getX(), plot.origin().getY(), plot.origin().getZ());

			for (Player player : plot.level().players()) {
				if (player.getVehicle() != null && player.getVehicle().position().distanceToSqr(at) < 0.01) {
					return new float[] {player.getViewYRot(partial), Turrets.clampPitch(upper, player.getViewXRot(partial))};
				}
			}
		}

		return null;
	}

	// --- Lights ------------------------------------------------------------------------------------

	/** Whether a plot's lights are dimmed (any step of the dimmer below full). */
	public static boolean dimmed(ClientLevel level, BlockPos origin, InteriorPlan plan) {
		return lightStep(level, origin, plan) > 0;
	}

	/** Phase 17: the step of a plot's dimmer (0 = full light), from its brightest lamp. */
	public static int lightStep(ClientLevel level, BlockPos origin, InteriorPlan plan) {
		List<long[]> lights = LIGHTS.computeIfAbsent(plan.id(), unused -> {
			long[] best = null;

			for (Long2ObjectMap.Entry<BlockState> entry : ProxyShapes.blocks(plan).long2ObjectEntrySet()) {
				if (entry.getValue().is(Blocks.LIGHT) && (best == null || entry.getValue().getValue(LightBlock.LEVEL) > best[1])) {
					best = new long[] {entry.getLongKey(), entry.getValue().getValue(LightBlock.LEVEL)};
				}
			}

			return best == null ? List.of() : List.of(best);
		});

		for (long[] light : lights) {
			BlockState state = level.getBlockState(origin.offset(BlockPos.of(light[0])));

			if (state.is(Blocks.LIGHT)) {
				return com.phiro.sabercraft.interior.ShipLights.stepOf((int) light[1], state.getValue(LightBlock.LEVEL));
			}
		}

		return 0;
	}

	/** Only for tests: how many shots this client asked for. */
	public static int firedForTest() {
		return fired;
	}
}
