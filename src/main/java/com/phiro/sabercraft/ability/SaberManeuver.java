package com.phiro.sabercraft.ability;

import org.jspecify.annotations.Nullable;

import net.minecraft.resources.Identifier;

import com.phiro.sabercraft.saber.SaberVariant;

/**
 * The evasive and lunging moves of the dodge key. Which one a press starts depends on the fighting
 * style and the direction (see {@link #of}).
 *
 * <p>This is shared code, so the server and every client agree on the timing: the server uses it
 * for the strike and the evasion window, the client of the moving player for the motion. The
 * animation of each move is in {@code moves.json} under the style's {@code dodge} entry and is
 * written to match these numbers.
 *
 * <p>The motion is a fixed speed per tick along the dodge direction instead of one big push, so
 * the body moves evenly through the whole animation. It is applied as normal player movement,
 * which means blocks stop it: a roll into a wall ends at the wall instead of sliding through it.
 */
public enum SaberManeuver {
	/** Forward roll that comes up in the fighting stance. */
	ROLL("roll", new float[] {0.42F, 0.55F, 0.6F, 0.58F, 0.5F, 0.4F, 0.3F, 0.18F, 0.08F, 0.0F, 0.0F, 0.0F}, 0.0F, -1, 0.0F, 1, 8),
	/** Roll to the side. */
	SIDE_ROLL("side_roll", new float[] {0.45F, 0.55F, 0.55F, 0.5F, 0.42F, 0.3F, 0.18F, 0.08F, 0.0F, 0.0F, 0.0F}, 0.0F, -1, 0.0F, 1, 7),
	/** Deep, controlled lunge with a stab at its end. */
	LUNGE("lunge", new float[] {0.85F, 0.8F, 0.6F, 0.35F, 0.15F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F}, 0.0F, 3, 0.5F, -1, -1),
	/** Quick turn on the spot while stepping forward, ending in a cut. */
	SPIN_STEP("spin_step", new float[] {0.45F, 0.5F, 0.48F, 0.38F, 0.25F, 0.12F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F}, 0.0F, 5, 0.45F, 0, 3),
	/** Short, controlled step to the side, the body stays upright. */
	SIDESTEP("sidestep", new float[] {0.55F, 0.5F, 0.35F, 0.18F, 0.05F, 0.0F, 0.0F}, 0.0F, -1, 0.0F, 0, 3),
	/** Hop backwards out of reach. */
	BACKSTEP("backstep", new float[] {0.55F, 0.45F, 0.32F, 0.18F, 0.06F, 0.0F, 0.0F, 0.0F}, 0.22F, -1, 0.0F, 0, 3),
	/** Backflip of the agile style. */
	BACKFLIP("backflip", new float[] {0.3F, 0.32F, 0.3F, 0.28F, 0.24F, 0.18F, 0.1F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F}, 0.48F, -1, 0.0F, 1, 8);

	private final String key;
	private final float[] speeds;
	private final float hop;
	private final int strikeTick;
	private final float damageShare;
	private final int evadeStart;
	private final int evadeEnd;

	SaberManeuver(String key, float[] speeds, float hop, int strikeTick, float damageShare, int evadeStart, int evadeEnd) {
		this.key = key;
		this.speeds = speeds;
		this.hop = hop;
		this.strikeTick = strikeTick;
		this.damageShare = damageShare;
		this.evadeStart = evadeStart;
		this.evadeEnd = evadeEnd;
	}

	/** The maneuver for a fighting style and a direction. */
	public static SaberManeuver of(@Nullable Identifier style, DodgeDirection direction) {
		boolean heavy = SaberVariant.HEAVY.equals(style) || SaberVariant.DEFENSIVE.equals(style)
				|| SaberVariant.DJEM_SO.equals(style) || SaberVariant.SORESU.equals(style);
		// Juyo is the agile style of the double staff: spin steps and back flips.
		boolean agile = SaberVariant.AGILE.equals(style) || SaberVariant.JUYO.equals(style);
		// The long double staff would cut into the ground in a shoulder roll, so it steps aside.
		boolean staff = SaberVariant.isStaffStyle(style);

		return switch (direction) {
			case FORWARD -> heavy ? LUNGE : agile ? SPIN_STEP : ROLL;
			case LEFT, RIGHT -> heavy || staff ? SIDESTEP : SIDE_ROLL;
			case BACK -> agile ? BACKFLIP : BACKSTEP;
		};
	}

	public String key() {
		return this.key;
	}

	/** Length of the motion in ticks. */
	public int duration() {
		return this.speeds.length;
	}

	/** Horizontal speed in blocks per tick at {@code tick} ticks after the start, 0 afterwards. */
	public float speed(int tick) {
		return tick >= 0 && tick < this.speeds.length ? this.speeds[tick] : 0.0F;
	}

	/** Upward speed given at the start, 0 for moves that stay on the ground. */
	public float hop() {
		return this.hop;
	}

	/** Tick of the strike, or -1 if the maneuver does not attack. */
	public int strikeTick() {
		return this.strikeTick;
	}

	/** Damage of the strike as a share of the attack damage. */
	public float damageShare() {
		return this.damageShare;
	}

	/** True while melee attacks miss the moving player, {@code age} in ticks since the start. */
	public boolean evades(long age) {
		return age >= this.evadeStart && age < this.evadeEnd;
	}
}
