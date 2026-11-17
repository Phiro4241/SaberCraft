package com.phiro.sabercraft.client.story;

import net.minecraft.client.Minecraft;

import com.phiro.sabercraft.client.menu.StoryMenuScreen;

/** Phase 19: tells an open story page of the menu that the state changed. */
final class StoryMenus {
	private StoryMenus() {
	}

	static void refresh() {
		Minecraft client = Minecraft.getInstance();

		if (client != null && client.gui.screen() instanceof StoryMenuScreen screen) {
			screen.refresh();
		}
	}
}
