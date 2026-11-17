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
 * sits on (the attack key fires the guns from a gunner's seat, the hyperspace key starts the
 * jump from a cockpit seat), how far each door is open (from its blocks, eased), where every
 * gunner aims (from their rotation, so everyone sees the guns turn), whether the lights are
 * dimmed, and the hyperspace jumps running in a plot.
 */
public final class InteriorClient {
	/** How fast doors slide (share of the way per second). */
	static final float DOOR_SPEED = 3.0F;

	/** A jump in a plot: which stage, the game tick it started, how long it lasts. */
	public record Jump(byte stage, long start, int ticks) {
	}

	private static final Map<BlockPos, Jump> JUMPS = new HashMap<>();
	private static final Map<Long, float[]> DOORS = new HashMap<>();
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

		if (plan == null || origin == null || plan.crew().isEmpty()) {
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

	/** For tests: how often the use key spoke to the crew. */
	public static int talkedForTest() {
		return talked;
	}

	/** The hyperspace key: from a cockpit seat it asks for the jump. Returns whether it did. */
	public static boolean hyperspaceKey(Minecraft client) {
		Sitting sitting = sitting(client);

		if (sitting == null || !sitting.seat().cockpit() || !ClientPlayNetworking.canSend(InteriorActionPayload.TYPE)) {
			return false;
		}

		ClientPlayNetworking.send(new InteriorActionPayload(InteriorActionPayload.HYPERSPACE));
		return true;
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
		float[] state = DOORS.computeIfAbsent(cell.asLong(), unused -> new float[] {open ? 1.0F : 0.0F, System.nanoTime()});
		long now = System.nanoTime();
		float seconds = Math.min(0.25F, (now - (long) state[1]) / 1.0E9F);
		state[1] = now;
		state[0] = Mth.clamp(state[0] + (open ? 1 : -1) * DOOR_SPEED * seconds, 0.0F, 1.0F);
		return state[0];
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
