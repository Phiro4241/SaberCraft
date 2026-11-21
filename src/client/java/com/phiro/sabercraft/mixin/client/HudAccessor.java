package com.phiro.sabercraft.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.gui.Hud;

/**
 * Phase 30: how long the line over the hotbar (a room's name, a ship's message) still shows, so the
 * story's boxes on the screen (the maintenance lift's panel, a progress bar) keep clear of it.
 */
@Mixin(Hud.class)
public interface HudAccessor {
	@Accessor("overlayMessageTime")
	int sabercraft$overlayMessageTime();
}
