package com.phiro.sabercraft.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.phiro.sabercraft.SaberCraft;

/**
 * Phase 16: a player sitting in a ship's interior asks for something: the hyperspace jump (from a
 * cockpit seat) or a shot of the turret guns (from a gunner's seat). Carries only what: the server
 * knows where they sit, aims along their view and keeps the cooldowns. Phase 18: or speaking to
 * one of the crew. Phase 20: or shutting an open hatch.
 */
public record InteriorActionPayload(byte action) implements CustomPacketPayload {
	public static final byte HYPERSPACE = 0;
	public static final byte FIRE = 1;
	/** Phase 18: speaking to the one of the crew looked at (the server looks along the player's view). */
	public static final byte TALK = 2;
	/** Phase 20: shutting the open hatch of a crawl way looked at (the server looks along the player's view). */
	public static final byte HATCH = 3;
	public static final CustomPacketPayload.Type<InteriorActionPayload> TYPE = new CustomPacketPayload.Type<>(SaberCraft.id("interior_action"));
	public static final StreamCodec<ByteBuf, InteriorActionPayload> CODEC = ByteBufCodecs.BYTE.map(InteriorActionPayload::new, InteriorActionPayload::action);

	@Override
	public CustomPacketPayload.Type<InteriorActionPayload> type() {
		return TYPE;
	}
}
