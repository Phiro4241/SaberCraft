package com.phiro.sabercraft.client.saber;

import com.phiro.sabercraft.client.sound.SaberSoundSet;

/**
 * Everything the client needs to animate and play one kind of saber. The look of the blade
 * itself (color, length, glow) comes from the item model, see {@code BladeStyle}.
 */
public record SaberProfile(SaberAnimation animation, SaberSoundSet sounds) {
}
