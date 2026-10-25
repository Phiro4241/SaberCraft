package com.phiro.sabercraft.client.select;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.phiro.sabercraft.ability.HiltTraits;
import com.phiro.sabercraft.client.blade.BladeStyle;
import com.phiro.sabercraft.client.blade.BladeStyles;
import com.phiro.sabercraft.client.menu.SaberMenuScreen;
import com.phiro.sabercraft.client.saber.SaberHilt;
import com.phiro.sabercraft.client.saber.SaberHilts;
import com.phiro.sabercraft.saber.SaberVariant;

/**
 * Menu for picking the hilt, blade color and fighting style of the saber in the hand. Opened with the
 * "Customize saber" key (V by default) while holding a netherite sword; the first area of the
 * {@link SaberMenuScreen SaberCraft menu}.
 *
 * <p>Picking a hilt also picks its usual color (blue for the guardian hilt, red for the dark one,
 * ...), which can then be changed freely. The fighting style decides the attack moves and the
 * defense: flowing, heavy, steadfast, agile, Djem So or Soresu for single blades; the double-bladed
 * hilt fights in the staff style or in Juyo. "Apply" sends the choice to the server, see
 * {@link SaberSelection}.
 */
public class SaberCustomizeScreen extends SaberMenuScreen {
	private static final int COLOR_BUTTON_WIDTH = 60;
	private static final int ROW = 22;
	private static final int PREVIEW_SIZE = 32;
	/** Since Phase 9 the hilts are a grid of icons (13 hilts no longer fit as named buttons). */
	private static final int HILT_ICON = 22;
	private static final int HILTS_PER_ROW = 7;
	private static final int STYLE_BUTTON_WIDTH = 58;
	private static final int STYLE_ROW = 18;
	private static final int STYLES_PER_ROW = 3;

	private final InteractionHand hand;
	private Identifier hilt;
	private Identifier color;
	private Identifier style;
	private Component status = Component.empty();
	private final List<HiltButton> hiltButtons = new ArrayList<>();
	private final List<ColorButton> colorButtons = new ArrayList<>();
	private final List<StyleButton> styleButtons = new ArrayList<>();

	public SaberCustomizeScreen(InteractionHand hand, ItemStack stack) {
		super(Component.translatable("sabercraft.customize.title"));
		this.hand = hand;
		SaberHilt current = SaberHilts.get(SaberVariant.hiltOf(stack));
		Identifier storedColor = BladeStyles.colorOf(stack);
		this.hilt = current.id();
		this.color = storedColor != null ? storedColor : current.defaultColor();
		Identifier storedStyle = SaberVariant.styleOf(stack);
		this.style = SaberVariant.styleFits(this.hilt, storedStyle) ? storedStyle : SaberVariant.defaultStyle(this.hilt);
	}

	@Override
	protected Tab tab() {
		return Tab.SABER;
	}

	@Override
	protected void initPanel() {
		this.hiltButtons.clear();
		this.colorButtons.clear();
		this.styleButtons.clear();

		// Hilts as a grid of icon buttons; the name of each is its tooltip and is written below.
		int index = 0;

		for (SaberHilt option : SaberHilts.all().values()) {
			int x = this.left + 10 + index % HILTS_PER_ROW * (HILT_ICON + 3);
			Button button = new IconButton(x, this.top + 32 + index / HILTS_PER_ROW * (HILT_ICON + 2), option.displayName(), b -> this.selectHilt(option));
			button.setTooltip(Tooltip.create(option.displayName().copy().append("\n").append(option.description())
					.append("\n").append(traitsLine(option.id()))));
			this.hiltButtons.add(new HiltButton(option, this.addRenderableWidget(button)));
			index++;
		}

		int colorLeft = this.left + PANEL_WIDTH - 10 - 2 * COLOR_BUTTON_WIDTH - 4;
		index = 0;

		for (Identifier id : SaberVariant.COLORS) {
			int x = colorLeft + index % 2 * (COLOR_BUTTON_WIDTH + 4);
			int rowY = this.top + 34 + index / 2 * ROW;
			Button button = Button.builder(colorName(id).copy().withColor(textColor(id)), b -> this.selectColor(id))
					.bounds(x, rowY, COLOR_BUTTON_WIDTH, 20)
					.build();
			this.colorButtons.add(new ColorButton(id, this.addRenderableWidget(button)));
			index++;
		}

		// Fighting style: three columns under the hilt; the staff styles take the first places.
		int styleY = this.styleTop();

		for (Identifier id : SaberVariant.STYLES) {
			int slot = SaberVariant.isStaffStyle(id) ? SaberVariant.DOUBLE_STYLES.indexOf(id) : SaberVariant.SINGLE_STYLES.indexOf(id);
			int x = this.left + 10 + slot % STYLES_PER_ROW * (STYLE_BUTTON_WIDTH + 3);
			int buttonY = styleY + slot / STYLES_PER_ROW * STYLE_ROW;
			Button button = Button.builder(styleName(id), b -> this.style = id)
					.bounds(x, buttonY, STYLE_BUTTON_WIDTH, STYLE_ROW - 2)
					.tooltip(Tooltip.create(styleName(id).copy().append("\n").append(Component.translatable("sabercraft.style." + id.getPath() + ".description"))))
					.build();
			this.styleButtons.add(new StyleButton(id, this.addRenderableWidget(button)));
		}

		this.updateStyleButtons();

		// Apply and cancel at the bottom of the color column.
		int columnWidth = 2 * COLOR_BUTTON_WIDTH + 4;
		this.addRenderableWidget(Button.builder(Component.translatable("sabercraft.customize.apply"), b -> this.apply())
				.bounds(colorLeft, this.top + PANEL_HEIGHT - 50, columnWidth, 20)
				.build());
		this.addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> this.onClose())
				.bounds(colorLeft, this.top + PANEL_HEIGHT - 27, columnWidth, 20)
				.build());

		if (this.minecraft != null && !SaberSelection.canChange(this.minecraft)) {
			this.status = Component.translatable("sabercraft.customize.unsupported");
		}
	}

	private void selectHilt(SaberHilt option) {
		if (!option.id().equals(this.hilt)) {
			this.hilt = option.id();
			this.color = option.defaultColor();

			if (!SaberVariant.styleFits(this.hilt, this.style)) {
				this.style = SaberVariant.defaultStyle(this.hilt);
			}

			this.updateStyleButtons();
		}
	}

	private void updateStyleButtons() {
		for (StyleButton entry : this.styleButtons) {
			entry.button().visible = SaberVariant.styleFits(this.hilt, entry.style());
		}
	}

	private void selectColor(Identifier id) {
		this.color = id;
	}

	private void apply() {
		if (this.minecraft == null) {
			return;
		}

		SaberSelection.Result result = SaberSelection.apply(this.minecraft, this.hand, new SaberVariant(this.hilt, this.color, this.style));

		switch (result) {
			case SENT, CREATIVE -> this.onClose();
			case UNSUPPORTED -> this.status = Component.translatable("sabercraft.customize.unsupported");
			case NO_SABER -> this.status = Component.translatable("sabercraft.customize.no_saber");
		}
	}

	@Override
	public void tick() {
		// The sword left the hand (dropped, died, swapped): nothing to customize any more.
		if (this.minecraft != null && (this.minecraft.player == null || !SaberVariant.canHold(this.minecraft.player.getItemInHand(this.hand)))) {
			this.onClose();
		}
	}

	/** Top of the fighting style buttons, below the name and description of the hilt. */
	private int styleTop() {
		int hiltRows = (SaberHilts.all().size() + HILTS_PER_ROW - 1) / HILTS_PER_ROW;
		return this.top + 32 + hiltRows * (HILT_ICON + 2) + 70;
	}

	@Override
	protected void extractPanel(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		graphics.text(this.font, Component.translatable("sabercraft.customize.hilt"), this.left + 10, this.top + 22, 0xFFA0A0A8);
		int colorLeft = this.left + PANEL_WIDTH - 10 - 2 * COLOR_BUTTON_WIDTH - 4;
		graphics.text(this.font, Component.translatable("sabercraft.customize.color"), colorLeft, this.top + 22, 0xFFA0A0A8);

		// Icon of each hilt in the chosen color, and a frame around the current choices.
		for (HiltButton entry : this.hiltButtons) {
			Button button = entry.button();
			graphics.fakeItem(previewStack(entry.hilt().id(), this.color), button.getX() + (HILT_ICON - 16) / 2, button.getY() + (HILT_ICON - 16) / 2);

			if (entry.hilt().id().equals(this.hilt)) {
				graphics.outline(button.getX() - 1, button.getY() - 1, button.getWidth() + 2, button.getHeight() + 2, 0xFFFFFFFF);
			}
		}

		for (ColorButton entry : this.colorButtons) {
			if (entry.color().equals(this.color)) {
				Button button = entry.button();
				graphics.outline(button.getX() - 1, button.getY() - 1, button.getWidth() + 2, button.getHeight() + 2, 0xFF000000 | textColor(entry.color()));
			}
		}

		for (StyleButton entry : this.styleButtons) {
			Button button = entry.button();

			if (button.visible && entry.style().equals(this.style)) {
				graphics.outline(button.getX() - 1, button.getY() - 1, button.getWidth() + 2, button.getHeight() + 2, 0xFFFFFFFF);
			}
		}

		graphics.text(this.font, Component.translatable("sabercraft.customize.style"), this.left + 10, this.styleTop() - 11, 0xFFA0A0A8);
		this.extractPreview(graphics, colorLeft);

		// Name and description of the chosen hilt below the hilt grid.
		SaberHilt chosen = SaberHilts.get(this.hilt);
		Component line = chosen.displayName().copy().append(" · ").append(colorName(this.color)).append(" · ").append(styleName(this.style));
		int hiltRows = (SaberHilts.all().size() + HILTS_PER_ROW - 1) / HILTS_PER_ROW;
		int textTop = this.top + 32 + hiltRows * (HILT_ICON + 2) + 4;
		graphics.text(this.font, line, this.left + 10, textTop, 0xFFFFFFFF);
		// A problem (no server support, no saber) replaces the description, so nothing overlaps.
		boolean problem = !this.status.getString().isEmpty();
		graphics.textWithWordWrap(
				this.font, problem ? this.status : chosen.description().copy().append(" ").append(traitsLine(chosen.id())), this.left + 10, textTop + 12,
				colorLeft - this.left - 20,
				problem ? 0xFFFF6060 : 0xFFB0B0B8
		);
	}

	/** The fighting edge of a hilt in one line, for example "Damage +8 % · Parry +1" (Phase 10). */
	public static Component traitsLine(Identifier hilt) {
		HiltTraits traits = HiltTraits.of(hilt);
		List<Component> parts = new ArrayList<>();

		if (traits.damage() != 1.0F) {
			parts.add(Component.translatable("sabercraft.hilt_traits.damage", percent(traits.damage() - 1.0F)));
		}

		if (traits.blockBonus() != 0.0F) {
			parts.add(Component.translatable("sabercraft.hilt_traits.block", percent(traits.blockBonus())));
		}

		if (traits.parryBonus() != 0) {
			parts.add(Component.translatable("sabercraft.hilt_traits.parry", String.format(Locale.ROOT, "%+d", traits.parryBonus())));
		}

		if (traits.knockback() != 0.0F) {
			parts.add(Component.translatable("sabercraft.hilt_traits.knockback", String.format(Locale.ROOT, "%+.2f", traits.knockback())));
		}

		if (traits.dodgeCooldown() != 1.0F) {
			parts.add(Component.translatable("sabercraft.hilt_traits.dodge", percent(traits.dodgeCooldown() - 1.0F)));
		}

		if (parts.isEmpty()) {
			return Component.translatable("sabercraft.hilt_traits.none");
		}

		MutableComponent line = Component.empty();

		for (int i = 0; i < parts.size(); i++) {
			line.append(i == 0 ? Component.empty() : Component.literal(" · ")).append(parts.get(i));
		}

		return line;
	}

	private static String percent(float share) {
		return String.format(Locale.ROOT, "%+d %%", Math.round(share * 100.0F));
	}

	/**
	 * Large hilt icon with a short glowing blade sample of the chosen color underneath, drawn
	 * with the same core, bloom and halo colors as the real blade.
	 */
	private void extractPreview(GuiGraphicsExtractor graphics, int colorLeft) {
		int columnWidth = 2 * COLOR_BUTTON_WIDTH + 4;
		int y = this.top + 34 + 3 * ROW + 2;
		int iconSize = PREVIEW_SIZE;
		graphics.pose().pushMatrix();
		graphics.pose().translate(colorLeft + (columnWidth - iconSize) / 2.0F, y);
		graphics.pose().scale(iconSize / 16.0F, iconSize / 16.0F);
		graphics.fakeItem(previewStack(this.hilt, this.color), 0, 0);
		graphics.pose().popMatrix();

		BladeStyle style = BladeStyles.get(this.color);
		int bladeLeft = colorLeft + 4;
		int bladeRight = colorLeft + columnWidth - 4;
		int bladeY = y + iconSize + 8;
		graphics.fill(bladeLeft, bladeY - 7, bladeRight, bladeY + 8, 0x30000000 | style.glowColor());
		graphics.fill(bladeLeft, bladeY - 4, bladeRight, bladeY + 5, 0x60000000 | style.glowColor());
		graphics.fill(bladeLeft, bladeY - 2, bladeRight, bladeY + 3, 0xC0000000 | style.bloomColor());
		graphics.fill(bladeLeft + 1, bladeY - 1, bladeRight - 1, bladeY + 2, 0xFF000000 | style.coreColor());
	}

	private static ItemStack previewStack(Identifier hilt, Identifier color) {
		ItemStack stack = new ItemStack(Items.NETHERITE_SWORD);
		new SaberVariant(hilt, color).applyTo(stack);
		return stack;
	}

	private static Component styleName(Identifier style) {
		return Component.translatable("sabercraft.style." + style.getPath());
	}

	private static Component colorName(Identifier color) {
		return Component.translatable("sabercraft.color." + color.getPath());
	}

	/** Button text color: the blade color, lightened so it stays readable on dark buttons. */
	private static int textColor(Identifier color) {
		return BladeStyle.mix(BladeStyles.get(color).glowColor(), 0xFFFFFF, 0.35F);
	}

	/** A square button that shows only its frame; the hilt icon is drawn over it. The name stays its message. */
	private static final class IconButton extends Button {
		private IconButton(int x, int y, Component message, OnPress onPress) {
			super(x, y, HILT_ICON, HILT_ICON, message, onPress, DEFAULT_NARRATION);
		}

		@Override
		protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
			this.extractDefaultSprite(graphics);
		}
	}

	private record HiltButton(SaberHilt hilt, Button button) {
	}

	private record ColorButton(Identifier color, Button button) {
	}

	private record StyleButton(Identifier style, Button button) {
	}
}
