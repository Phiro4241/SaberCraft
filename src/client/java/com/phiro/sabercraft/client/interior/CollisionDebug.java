package com.phiro.sabercraft.client.interior;

import com.mojang.brigadier.arguments.BoolArgumentType;

import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import com.phiro.sabercraft.interior.InteriorPlan;
import com.phiro.sabercraft.interior.ShipInteriors;

/**
 * Phase 18: shows the real collision of the ships' interiors round the player: every invisible
 * barrier as boxes (red: a whole block, orange: shaped like a wall or a piece of furniture, green:
 * a seat or a console one can click, yellow: a closed door). Switched with the client command
 * {@code /sabercraft_collision} (or {@code /sabercraft_collision true|false}); it is off by
 * default and only works aboard a ship. For finding invisible walls by eye.
 */
public final class CollisionDebug {
	/** How far round the player (blocks) the shapes are shown. */
	static final int RADIUS = 6;
	/** At most this many boxes a tick (round walls are made of many columns). */
	static final int MAX_BOXES = 3000;
	private static boolean enabled;
	private static int shown;

	private CollisionDebug() {
	}

	public static void init() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registry) -> dispatcher.register(ClientCommands.literal("sabercraft_collision")
				.executes(context -> {
					set(!enabled);
					context.getSource().sendFeedback(message());
					return 1;
				}).then(ClientCommands.argument("on", BoolArgumentType.bool()).executes(context -> {
					set(BoolArgumentType.getBool(context, "on"));
					context.getSource().sendFeedback(message());
					return 1;
				}))));
		ClientTickEvents.END_CLIENT_TICK.register(CollisionDebug::tick);
		// The outline under the crosshair: only round what one can use (a seat, a console), not
		// round the invisible walls and furniture (unless the collision is shown).
		net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.BEFORE_BLOCK_OUTLINE.register((context, outline) -> {
			Minecraft client = Minecraft.getInstance();
			return enabled || client.level == null || !ShipInteriors.isInterior(client.level) || !client.level.getBlockState(outline.pos()).is(Blocks.BARRIER)
					|| usableAt(outline.pos());
		});
	}

	/** Whether a block of the interiors (world space) is a seat or a console. */
	static boolean usableAt(BlockPos pos) {
		InteriorPlan plan = ShipInteriors.planAt(pos.getX() + 0.5, pos.getZ() + 0.5);
		BlockPos origin = ShipInteriors.originAt(pos.getX() + 0.5, pos.getZ() + 0.5);
		return plan != null && origin != null && (usable(plan, pos.subtract(origin)) || plan.seatFor(pos.subtract(origin), Vec3.atCenterOf(pos.subtract(origin))) != null);
	}

	private static Component message() {
		return enabled ? Component.translatableWithFallback("sabercraft.collision_debug.on",
				"Ship collision shown: red whole blocks, orange shaped, green seats and consoles, yellow closed doors.")
				: Component.translatableWithFallback("sabercraft.collision_debug.off", "Ship collision hidden.");
	}

	public static void set(boolean on) {
		enabled = on;
	}

	public static boolean enabled() {
		return enabled;
	}

	/** Only for tests: how many boxes were shown in the last tick. */
	public static int shownForTest() {
		return shown;
	}

	private static void tick(Minecraft client) {
		shown = 0;

		if (!enabled || client.level == null || client.player == null || !ShipInteriors.isInterior(client.level)) {
			return;
		}

		BlockPos centre = client.player.blockPosition();
		InteriorPlan plan = ShipInteriors.planAt(centre.getX() + 0.5, centre.getZ() + 0.5);
		BlockPos origin = ShipInteriors.originAt(centre.getX() + 0.5, centre.getZ() + 0.5);

		try (Gizmos.TemporaryCollection ignored = client.collectPerTickGizmos()) {
			BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

			for (int dx = -RADIUS; dx <= RADIUS; dx++) {
				for (int dy = -3; dy <= 4; dy++) {
					for (int dz = -RADIUS; dz <= RADIUS; dz++) {
						pos.set(centre.getX() + dx, centre.getY() + dy, centre.getZ() + dz);
						BlockState state = client.level.getBlockState(pos);

						if (!state.is(Blocks.BARRIER)) {
							continue;
						}

						VoxelShape shape = state.getCollisionShape(client.level, pos, CollisionContext.empty());
						boolean full = net.minecraft.world.level.block.Block.isShapeFullBlock(shape);
						int colour = full ? 0xFFFF3030 : 0xFFFF9020;

						if (plan != null && origin != null) {
							BlockPos local = pos.subtract(origin);

							if (usable(plan, local)) {
								colour = 0xFF30FF60;
							} else if (door(plan, local)) {
								colour = 0xFFFFE030;
							}
						}

						for (AABB box : shape.toAabbs()) {
							if (shown++ >= MAX_BOXES) {
								return;
							}

							Gizmos.cuboid(box.move(pos), GizmoStyle.strokeAndFill(colour, 1.5F, colour & 0x00FFFFFF | 0x28000000));
						}
					}
				}
			}
		}
	}

	private static boolean usable(InteriorPlan plan, BlockPos local) {
		for (InteriorPlan.Console console : plan.consoles()) {
			if (console.cell().equals(local)) {
				return true;
			}
		}

		for (InteriorPlan.Seat seat : plan.seats()) {
			if (seat.cell().equals(local) || seat.cell().above().equals(local)) {
				return true;
			}
		}

		return false;
	}

	private static boolean door(InteriorPlan plan, BlockPos local) {
		for (InteriorPlan.Door door : plan.doors()) {
			if (door.contains(local.getX(), local.getY(), local.getZ())) {
				return true;
			}
		}

		return false;
	}
}
