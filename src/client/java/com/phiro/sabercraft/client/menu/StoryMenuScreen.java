package com.phiro.sabercraft.client.menu;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import com.phiro.sabercraft.client.ImmersionConfig;
import com.phiro.sabercraft.client.lang.SaberLanguage;
import com.phiro.sabercraft.client.story.StoryClient;
import com.phiro.sabercraft.network.StoryActionPayload;
import com.phiro.sabercraft.network.StoryStatePayload;

/**
 * Phase 19: the story mode. What the save holds (the mission, what to do now), to go on, to start
 * again (asked twice), to play a mission one has reached again, the journal, to leave the story
 * (one's own things, place and droids come back). Below: SaberCraft's language (independent of
 * Minecraft's), how fast the texts appear, and the objectives' marker.
 *
 * <p>The server keeps the story; the page asks it for the state when it opens and builds itself
 * again when something that it shows changes.
 */
public class StoryMenuScreen extends SaberMenuScreen {
	private static final int TEXT = 0xFFD8DCE4;
	private static final int GREY = 0xFFA8A8B0;
	private static final int WARN = 0xFFE8B060;

	private boolean asked;
	private boolean journal;
	private boolean confirmRestart;
	private int chosen = -1;
	private int scroll;
	/** What the page was built for (built again when it changes). */
	private String shown = "";

	public StoryMenuScreen() {
		super(Component.translatable("sabercraft.menu.story.title"));
	}

	@Override
	protected Tab tab() {
		return Tab.STORY;
	}

	@Override
	protected void initPanel() {
		StoryStatePayload state = StoryClient.state();
		this.shown = signature(state);

		boolean server = ClientPlayNetworking.canSend(StoryActionPayload.TYPE);

		// Asks the server once what the save holds (it answers with the state).
		if (!this.asked && server) {
			this.asked = true;
			StoryClient.send(StoryActionPayload.STATE, "", 0);
		}

		if (this.journal) {
			this.addRenderableWidget(Button.builder(Component.translatable("sabercraft.menu.story.back"), b -> {
				this.journal = false;
				this.rebuildWidgets();
			}).bounds(this.left + PANEL_WIDTH / 2 - 50, this.top + PANEL_HEIGHT - 28, 100, 20).build());
			return;
		}

		boolean active = state != null && state.active();
		boolean saved = state != null && (state.saved() || active);

		Button go = this.button(0, 2, Component.translatable(saved ? "sabercraft.menu.story.continue" : "sabercraft.menu.story.start"), () -> {
			this.act(StoryActionPayload.CONTINUE, 0);
			return true;
		});
		go.active = server && !active;
		go.setTooltip(Tooltip.create(Component.translatable("sabercraft.menu.story.start.description")));

		Button restart = this.button(1, 2, Component.translatable(this.confirmRestart ? "sabercraft.menu.story.restart_sure" : "sabercraft.menu.story.restart"),
				() -> {
					if (!this.confirmRestart) {
						this.confirmRestart = true;
						this.rebuildWidgets();
						return true;
					}

					this.confirmRestart = false;
					this.act(StoryActionPayload.RESTART, 0);
					return true;
				});
		restart.active = server && saved;

		// The missions one has reached.
		int missions = state != null ? Math.max(1, state.missions()) : 6;
		int unlocked = state != null ? unlocked(state) : 0;
		List<Integer> reached = new ArrayList<>();

		for (int i = 0; i <= unlocked; i++) {
			reached.add(i);
		}

		if (this.chosen < 0 || this.chosen > unlocked) {
			this.chosen = state != null ? Mth.clamp(state.mission(), 0, unlocked) : 0;
		}

		// Only the mission's name on the button (with "Mission:" before it, the longer names scrolled).
		CycleButton<Integer> mission = this.addRenderableWidget(CycleButton.<Integer>builder(
				i -> Component.translatable("sabercraft.menu.story.mission_name", i + 1, missions, Component.translatable("sabercraft.story.mission.m" + (i + 1))), this.chosen)
				.withValues(reached)
				.displayOnlyValue()
				.withTooltip(v -> Tooltip.create(Component.translatable("sabercraft.menu.story.mission.description")))
				.create(this.column(0), this.row(3), COLUMN_WIDTH, 20, Component.translatable("sabercraft.menu.story.mission"), (button, v) -> this.chosen = v));
		mission.active = server && unlocked > 0;
		Button play = this.button(1, 3, Component.translatable("sabercraft.menu.story.play"), () -> {
			this.act(StoryActionPayload.MISSION, this.chosen);
			return true;
		});
		play.active = server && (saved || this.chosen == 0);
		play.setTooltip(Tooltip.create(Component.translatable("sabercraft.menu.story.play.description")));

		Button book = this.button(0, 4, Component.translatable("sabercraft.menu.story.journal"), () -> {
			this.journal = true;
			this.scroll = 0;
			this.rebuildWidgets();
			return true;
		});
		book.active = state != null && !state.journal().isEmpty();
		Button leave = this.button(1, 4, Component.translatable("sabercraft.menu.story.leave"), () -> {
			this.act(StoryActionPayload.LEAVE, 0);
			return true;
		});
		leave.active = active;
		leave.setTooltip(Tooltip.create(Component.translatable("sabercraft.menu.story.leave.description")));

		ImmersionConfig config = ImmersionConfig.get();
		this.cycle(0, 5, "sabercraft.option.language", SaberLanguage.CHOICES, SaberLanguage.setting(),
				code -> Component.translatable("sabercraft.menu.story.language." + code), code -> {
					SaberLanguage.set(code);
					this.rebuildWidgets();
				});
		this.cycle(1, 5, "sabercraft.option.story_text_speed", List.of(0, 1, 2), Mth.clamp(config.storyTextSpeed, 0, 2),
				v -> Component.translatable("sabercraft.menu.story.speed." + v), v -> {
					config.storyTextSpeed = v;
					ImmersionConfig.save();
				});
		this.toggle(0, 6, "sabercraft.option.story_marker", config.storyMarker, v -> {
			config.storyMarker = v;
			ImmersionConfig.save();
		});

		if (!config.shipInteriorDetail) {
			// The story's people and things are part of the detailed rooms.
			this.button(1, 6, Component.translatable("sabercraft.menu.story.detail_on"), () -> {
				config.shipInteriorDetail = true;
				ImmersionConfig.save();
				this.rebuildWidgets();
				return true;
			});
		}

		this.closeButton();
	}

	/** Sends a choice and closes the menu (the server answers with a message if it cannot). */
	private void act(byte action, int value) {
		StoryClient.send(action, "", value);
		this.onClose();
	}

	/** The last mission one may choose (as the server reckons it). */
	static int unlocked(StoryStatePayload state) {
		int next = 0;

		while (state.completed().contains(next)) {
			next++;
		}

		return Math.min(Math.max(0, state.missions() - 1), Math.max(next, state.mission()));
	}

	private static String signature(StoryStatePayload state) {
		if (state == null) {
			return "none";
		}

		return state.active() + "/" + state.saved() + "/" + state.mission() + "/" + state.completed() + "/" + state.finished() + "/" + state.objective() + "/"
				+ state.journal().size() + "/" + state.captures();
	}

	/** The state came: built again if it shows something else now. */
	public void refresh() {
		if (!signature(StoryClient.state()).equals(this.shown)) {
			this.rebuildWidgets();
		}
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (this.journal) {
			this.scroll = Math.max(0, this.scroll - (int) Math.signum(scrollY) * 20);
			return true;
		}

		return super.mouseScrolled(x, y, scrollX, scrollY);
	}

	@Override
	protected void extractPanel(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		StoryStatePayload state = StoryClient.state();

		if (this.journal) {
			this.extractJournal(graphics, state);
			return;
		}

		int x = this.left + 10;
		int y = this.row(0);
		int width = PANEL_WIDTH - 20;
		List<Component> lines = new ArrayList<>();
		List<Integer> colours = new ArrayList<>();

		if (!ClientPlayNetworking.canSend(StoryActionPayload.TYPE)) {
			lines.add(Component.translatable("sabercraft.menu.story.no_server"));
			colours.add(WARN);
		} else if (state == null) {
			lines.add(Component.translatable("sabercraft.menu.story.loading"));
			colours.add(GREY);
		} else if (state.active()) {
			lines.add(Component.translatable("sabercraft.menu.story.now", state.mission() + 1, state.missions(), Component.translatable(state.title())));
			colours.add(TEXT);

			if (!state.objective().isEmpty()) {
				lines.add(Component.translatable("sabercraft.menu.story.objective", Component.translatable(state.objective())));
				colours.add(0xFFB8E0B8);
			}
		} else if (state.saved()) {
			lines.add(Component.translatable(state.finished() ? "sabercraft.menu.story.finished" : "sabercraft.menu.story.saved", state.mission() + 1,
					state.missions(), Component.translatable(state.title())));
			colours.add(TEXT);
			lines.add(Component.translatable("sabercraft.menu.story.things"));
			colours.add(GREY);
		} else {
			lines.add(Component.translatable("sabercraft.menu.story.intro"));
			colours.add(TEXT);
			lines.add(Component.translatable("sabercraft.menu.story.things"));
			colours.add(GREY);
		}

		if (!ImmersionConfig.get().shipInteriorDetail) {
			lines.add(Component.translatable("sabercraft.menu.story.detail_off"));
			colours.add(WARN);
		}

		// The two rows above the buttons hold it; what does not fit is cut.
		int bottom = this.row(2) - 3;

		for (int i = 0; i < lines.size(); i++) {
			for (FormattedCharSequence part : this.font.split(lines.get(i), width)) {
				if (y + 9 > bottom) {
					return;
				}

				graphics.text(this.font, part, x, y, colours.get(i));
				y += 10;
			}

			y += 2;
		}
	}

	private void extractJournal(GuiGraphicsExtractor graphics, StoryStatePayload state) {
		int x = this.left + 12;
		int top = this.top + 24;
		int bottom = this.top + PANEL_HEIGHT - 34;
		int width = PANEL_WIDTH - 24;
		graphics.text(this.font, Component.translatable("sabercraft.menu.story.journal"), x, top, 0xFFE8C878);
		int y = top + 14 - this.scroll;
		List<String> entries = state != null ? state.journal() : List.of();

		if (entries.isEmpty()) {
			graphics.text(this.font, Component.translatable("sabercraft.menu.story.journal_empty"), x, y, GREY);
			return;
		}

		graphics.enableScissor(this.left + 4, top + 12, this.left + PANEL_WIDTH - 4, bottom);

		// The newest first.
		for (int i = entries.size() - 1; i >= 0; i--) {
			graphics.fill(x, y + 3, x + 3, y + 6, 0xFFE8C878);

			for (FormattedCharSequence part : this.font.split(Component.translatable("sabercraft.story.journal." + entries.get(i)), width - 10)) {
				graphics.text(this.font, part, x + 8, y, TEXT);
				y += 10;
			}

			y += 6;
		}

		graphics.disableScissor();
		int total = y + this.scroll - (top + 14);
		this.scroll = Mth.clamp(this.scroll, 0, Math.max(0, total - (bottom - top - 14)));
	}
}
