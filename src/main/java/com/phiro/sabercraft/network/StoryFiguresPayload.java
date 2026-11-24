package com.phiro.sabercraft.network;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.phiro.sabercraft.SaberCraft;

/**
 * Phase 19: the people in a story ship near the player, a few times a second (the client draws
 * them in between). Positions are in plot space, from the plot's corner {@code origin}.
 */
public record StoryFiguresPayload(BlockPos origin, long time, List<Fig> figures) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<StoryFiguresPayload> TYPE = new CustomPacketPayload.Type<>(SaberCraft.id("story_figures"));
	public static final StreamCodec<FriendlyByteBuf, StoryFiguresPayload> CODEC = CustomPacketPayload.codec(StoryFiguresPayload::write, StoryFiguresPayload::new);

	/** Flags of a figure. */
	public static final int LAMP = 1;
	public static final int SABER = 2;
	public static final int HOSTILE = 4;
	public static final int TALKS = 8;
	public static final int OBSERVER = 16;
	/** Phase 21: the saber is lit (also before the fight, in a scene). */
	public static final int LIT = 32;
	/** Phase 23: an officer with his pistol drawn. */
	public static final int ARMED = 64;
	/** Phase 23: a saber fighter whose saber was struck from the hand (none to draw). */
	public static final int UNARMED = 128;
	/** Phase 28: her Inquisitor's ring keeps turning (a state that lasts, not one move's spin). */
	public static final int RING_SPIN = 256;
	/**
	 * Phase 30: her spin's blades whirl round her now: the red circle on the floor, {@link #SPIN_RADIUS}
	 * round her, in which the player is hit at once (the server hits by the same number, the player's
	 * middle within it: no further than the circle drawn).
	 */
	public static final int SPIN_LIVE = 512;
	/**
	 * Phase 31: she is in the second phase of the duel by her strength (the server's word): her
	 * staff's second blade may show. A fight begun again has her at full strength, so a client that
	 * still remembered the second phase cannot draw it.
	 */
	public static final int TWIN = 1024;
	public static final double SPIN_RADIUS = 3.5;
	/**
	 * Phase 30: her spin's two parts (ticks of her wind-up): the ring spinning up round the grip
	 * (the warning), then its blades whirling round her (the circle live). The server times the spin
	 * by them; the client spins the ring up to full speed within the first.
	 */
	public static final int SPIN_RISE_TICKS = 8;
	public static final int SPIN_LIVE_TICKS = 16;

	/**
	 * One person: their number, kind and pose (as {@code Crew}), name ({@code speaker}), where and
	 * which way they and their head face, how fast they walk, their stage and suspicion (0..255),
	 * flags, health (0..255) and a blow being wound up (Phase 21: the kind in the top three bits, how far along in the bottom five).
	 * Phase 23: what the body does beyond walking ({@code react}: a hit, a stumble, a fall, a call,
	 * a clash; the kind times 256 plus how far along, 0..255; 0 = nothing).
	 */
	public record Fig(int id, String kind, String pose, String speaker, float x, float y, float z, float yaw, float head, float speed, int stage, int suspicion,
			int flags, int health, int windup, int react) {
		/** Phase 23: the kind of the reaction (0 = none). */
		public int reactKind() {
			return this.react >> 8;
		}

		/** Phase 23: how far along the reaction is (0..1). */
		public float reactProgress() {
			return (this.react & 255) / 255.0F;
		}

		static Fig read(FriendlyByteBuf buf) {
			return new Fig(buf.readVarInt(), buf.readUtf(32), buf.readUtf(16), buf.readUtf(32), buf.readFloat(), buf.readFloat(), buf.readFloat(),
					buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readByte(), buf.readUnsignedByte(), buf.readVarInt(), buf.readUnsignedByte(),
					buf.readUnsignedByte(), buf.readVarInt());
		}

		void write(FriendlyByteBuf buf) {
			buf.writeVarInt(this.id);
			buf.writeUtf(this.kind, 32);
			buf.writeUtf(this.pose, 16);
			buf.writeUtf(this.speaker, 32);
			buf.writeFloat(this.x);
			buf.writeFloat(this.y);
			buf.writeFloat(this.z);
			buf.writeFloat(this.yaw);
			buf.writeFloat(this.head);
			buf.writeFloat(this.speed);
			buf.writeByte(this.stage);
			buf.writeByte(this.suspicion);
			// (Phase 28: more than eight flags: a VarInt.)
			buf.writeVarInt(this.flags);
			buf.writeByte(this.health);
			buf.writeByte(this.windup);
			buf.writeVarInt(this.react);
		}
	}

	private StoryFiguresPayload(FriendlyByteBuf buf) {
		this(buf.readBlockPos(), buf.readLong(), buf.readList(Fig::read));
	}

	private void write(FriendlyByteBuf buf) {
		buf.writeBlockPos(this.origin);
		buf.writeLong(this.time);
		buf.writeCollection(this.figures, (b, f) -> f.write(b));
	}

	@Override
	public CustomPacketPayload.Type<StoryFiguresPayload> type() {
		return TYPE;
	}
}
