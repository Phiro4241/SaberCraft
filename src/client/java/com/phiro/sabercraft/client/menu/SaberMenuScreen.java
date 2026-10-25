package com.phiro.sabercraft.client.menu;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.DoubleFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.InteractionHand;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.client.ImmersionConfig;
import com.phiro.sabercraft.client.select.SaberCustomizeScreen;
import com.phiro.sabercraft.client.select.SaberSelection;

/**
 * The SaberCraft menu (key V): one panel with a column of areas on the left. Saber (hilt, blade
 * color, fighting style; needs a netherite sword in the hand), combat (abilities, keys, combat
 * animations), character (outfits), space (the sky), music, and graphics and effects. Every
 * change is saved right away; the saber is changed with "Apply" as before.
 */
public abstract class SaberMenuScreen extends Screen {
	public static final int PANEL_WIDTH = 330;
	public static final int PANEL_HEIGHT = 236;
	private static final int TAB_WIDTH = 74;
	private static final int GAP = 4;
	protected static final int ROW = 24;
	protected static final int COLUMN_WIDTH = 152;

	/** The areas of the menu. */
	public enum Tab {
		SABER, BLASTER, DROIDS, SHIPS, COMBAT, CHARACTER, SPACE, MUSIC, GRAPHICS;

		public Component label() {
			return Component.translatable("sabercraft.menu.tab." + this.name().toLowerCase(java.util.Locale.ROOT));
		}
	}

	/** How long a page takes to fade in, in milliseconds (Phase 11 G). */
	private static final float TRANSITION_MILLIS = 220.0F;

	protected int left;
	protected int top;
	/** When this page was opened; a resize builds it again but does not fade it in again. */
	private final long opened = Util.getMillis();
	private boolean swept;

	protected SaberMenuScreen(Component title) {
		super(title);
	}

	protected abstract Tab tab();

	/** Adds the widgets of the area; {@link #left} and {@link #top} are set. */
	protected abstract void initPanel();

	@Override
	protected final void init() {
		int total = TAB_WIDTH + GAP + PANEL_WIDTH;
		int tabsLeft = Math.max(2, (this.width - total) / 2);
		this.left = tabsLeft + TAB_WIDTH + GAP;
		this.top = Math.max(4, (this.height - PANEL_HEIGHT) / 2);
		int y = this.top;

		for (Tab tab : Tab.values()) {
			Button button = Button.builder(tab.label(), b -> open(this.minecraft, tab)).bounds(tabsLeft, y, TAB_WIDTH, 20).build();
			button.active = tab != this.tab() && (tab != Tab.SABER || saberHand(this.minecraft) != null);

			if (tab == Tab.SABER && saberHand(this.minecraft) == null) {
				button.setTooltip(Tooltip.create(Component.translatable("sabercraft.customize.no_saber")));
			}

			this.addRenderableWidget(button);
			y += 22;
		}

		this.initPanel();

		if (!this.swept && ImmersionConfig.get().menuTransitions && this.minecraft != null) {
			this.swept = true;
			this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvent.createVariableRangeEvent(SaberCraft.id("ui.menu_sweep")), 1.0F, 0.35F));
		}
	}

	/** How far the page has faded in, 0 just opened, 1 done (always 1 with transitions off). */
	public float transition() {
		if (!ImmersionConfig.get().menuTransitions) {
			return 1.0F;
		}

		return Mth.clamp((Util.getMillis() - this.opened) / TRANSITION_MILLIS, 0.0F, 1.0F);
	}

	/** Opens an area of the menu. */
	public static void open(Minecraft client, Tab tab) {
		client.gui.setScreen(create(client, tab));
	}

	/** The area to start with: the saber when one is held, the character otherwise. */
	public static Screen create(Minecraft client, Tab tab) {
		InteractionHand hand = saberHand(client);

		if (tab == Tab.SABER && hand == null) {
			tab = Tab.CHARACTER;
		}

		return switch (tab) {
			case SABER -> new SaberCustomizeScreen(hand, client.player.getItemInHand(hand));
			case BLASTER -> new BlasterMenuScreen();
			case DROIDS -> new DroidMenuScreen();
			case SHIPS -> new ShipMenuScreen();
			case COMBAT -> new CombatMenuScreen();
			case CHARACTER -> new CharacterMenuScreen();
			case SPACE -> new SpaceMenuScreen();
			case MUSIC -> new MusicMenuScreen();
			case GRAPHICS -> new GraphicsMenuScreen();
		};
	}

	private static InteractionHand saberHand(Minecraft client) {
		return client == null || client.player == null ? null : SaberSelection.saberHand(client.player);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		graphics.fill(this.left, this.top, this.left + PANEL_WIDTH, this.top + PANEL_HEIGHT, 0xD0101014);
		graphics.outline(this.left, this.top, PANEL_WIDTH, PANEL_HEIGHT, 0xFF4A4A55);
		graphics.centeredText(this.font, this.title, this.left + PANEL_WIDTH / 2, this.top + 8, 0xFFFFFFFF);
		super.extractRenderState(graphics, mouseX, mouseY, a);
		this.extractPanel(graphics, mouseX, mouseY, a);
		this.extractTransition(graphics);
	}

	/**
	 * Phase 11 G: a new page comes up like a hologram, a scan line runs down the panel and the dark
	 * cover over the page clears behind it. Only the panel; the tabs stay as they are.
	 */
	private void extractTransition(GuiGraphicsExtractor graphics) {
		float done = this.transition();

		if (done >= 1.0F) {
			return;
		}

		float eased = done * done * (3.0F - 2.0F * done);
		int line = this.top + Math.round(eased * PANEL_HEIGHT);
		int fade = Math.round((1.0F - eased) * 0xE0);
		graphics.fill(this.left, line, this.left + PANEL_WIDTH, this.top + PANEL_HEIGHT, fade << 24 | 0x101014);
		int glow = Math.round((1.0F - done) * 0xC0);
		graphics.fill(this.left, Math.max(this.top, line - 1), this.left + PANEL_WIDTH, Math.min(this.top + PANEL_HEIGHT, line + 1),
				glow << 24 | 0x7FB2FF);
	}

	/** Draws over the widgets of the area. */
	protected void extractPanel(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	// Helpers for the settings areas: a grid of two columns below the title.

	protected int column(int column) {
		return this.left + 10 + column * (COLUMN_WIDTH + 6);
	}

	protected int row(int row) {
		return this.top + 26 + row * ROW;
	}

	protected CycleButton<Boolean> toggle(int column, int row, String key, boolean value, Consumer<Boolean> change) {
		return this.addRenderableWidget(CycleButton.onOffBuilder(value)
				.withTooltip(v -> Tooltip.create(Component.translatable(key + ".description")))
				.create(this.column(column), this.row(row), COLUMN_WIDTH, 20, Component.translatable(key), (button, v) -> change.accept(v)));
	}

	protected <T> CycleButton<T> cycle(int column, int row, String key, List<T> values, T value, Function<T, Component> names, Consumer<T> change) {
		return this.addRenderableWidget(CycleButton.builder(names, value)
				.withValues(values)
				.withTooltip(v -> Tooltip.create(Component.translatable(key + ".description")))
				.create(this.column(column), this.row(row), COLUMN_WIDTH, 20, Component.translatable(key), (button, v) -> change.accept(v)));
	}

	/** A slider from {@code min} to {@code max}, shown with {@code format}. */
	protected AbstractSliderButton slider(int column, int row, String key, float min, float max, float value, DoubleFunction<String> format,
			Consumer<Float> change) {
		Component name = Component.translatable(key);
		AbstractSliderButton slider = new AbstractSliderButton(this.column(column), this.row(row), COLUMN_WIDTH, 20, Component.empty(),
				(Mth.clamp(value, min, max) - min) / (max - min)) {
			{
				this.updateMessage();
			}

			@Override
			protected void updateMessage() {
				this.setMessage(Component.empty().append(name).append(": ").append(format.apply(min + this.value * (max - min))));
			}

			@Override
			protected void applyValue() {
				change.accept((float) (min + this.value * (max - min)));
			}
		};
		slider.setTooltip(Tooltip.create(Component.translatable(key + ".description")));
		return this.addRenderableWidget(slider);
	}

	protected static String percent(double value) {
		return Math.round(value * 100.0) + " %";
	}

	protected Button button(int column, int row, Component label, Supplier<Boolean> action) {
		return this.addRenderableWidget(Button.builder(label, b -> action.get()).bounds(this.column(column), this.row(row), COLUMN_WIDTH, 20).build());
	}

	/** A short text block (wrapped) at a row, in grey. */
	protected void note(GuiGraphicsExtractor graphics, Component text, int row) {
		graphics.textWithWordWrap(this.font, text, this.left + 10, this.row(row) + 4, PANEL_WIDTH - 20, 0xFFA8A8B0);
	}

	protected void closeButton() {
		this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> this.onClose())
				.bounds(this.left + PANEL_WIDTH / 2 - 50, this.top + PANEL_HEIGHT - 28, 100, 20)
				.build());
	}
}
