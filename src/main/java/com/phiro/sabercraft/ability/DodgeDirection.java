package com.phiro.sabercraft.ability;

import net.minecraft.world.phys.Vec3;

/**
 * The direction of an evasive move, taken from the movement keys held when the dodge key is
 * pressed: no key or forward is {@link #FORWARD}, a sideways key wins over forward and back.
 */
public enum DodgeDirection {
	FORWARD,
	LEFT,
	RIGHT,
	BACK;

	public static DodgeDirection byId(int id) {
		DodgeDirection[] values = values();
		return id >= 0 && id < values.length ? values[id] : FORWARD;
	}

	public static DodgeDirection fromInput(boolean forward, boolean back, boolean left, boolean right) {
		if (left != right) {
			return left ? LEFT : RIGHT;
		}

		return back && !forward ? BACK : FORWARD;
	}

	/**
	 * The horizontal unit vector of this direction for an entity looking along {@code yaw}
	 * (degrees, Minecraft convention).
	 */
	public Vec3 vector(float yaw) {
		Vec3 forward = Vec3.directionFromRotation(0.0F, yaw);
		Vec3 right = new Vec3(-forward.z, 0.0, forward.x);

		return switch (this) {
			case FORWARD -> forward;
			case BACK -> forward.scale(-1.0);
			case RIGHT -> right;
			case LEFT -> right.scale(-1.0);
		};
	}
}
