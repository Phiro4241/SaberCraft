package com.phiro.sabercraft.client.select;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.phiro.sabercraft.client.blade.BladeStyle;
import com.phiro.sabercraft.client.blade.BladeStyles;
import com.phiro.sabercraft.client.saber.SaberHilt;
import com.phiro.sabercraft.client.saber.SaberHilts;
import com.phiro.sabercraft.saber.SaberVariant;

/**
 * Menu for picking the hilt, blade color and fighting style of the saber in the hand. Opened with the
 * "Customize saber" key (V by default) while holding a netherite sword.
 *
 * <p>Picking a hilt also picks its usual color (blue for the guardian hilt, red for the dark one,
 * ...), which can then be changed freely. The fighting style decides the attack moves: flowing
 * (light, fast cuts) or heavy (slow, powerful blows) for single blades; the double-bladed hilt
 * always fights in its own staff style. "Apply" sends the choice to the server, see
 * {@link SaberSelection}.
 */
public class SaberCustomizeScreen extends Screen {
	private static final int PANEL_WIDTH = 330;
	private static final int PANEL_HEIGHT = 232;
	private static final int HILT_BUTTON_WIDTH = 112;
	private static final int COLOR_BUTTON_WIDTH = 60;
	private static final int ROW = 22;
	private static final int PREVIEW_SIZE = 40;

	private final InteractionHand hand;
	private Identifier hilt;
	private Identifier color;
	private Identifier style;
	private Component status = Component.empty();
	private final List<HiltButton> hiltButtons = new ArrayList<>();
	private final List<ColorButton> colorButtons = new ArrayList<>();
	private final List<StyleButton> styleButtons = new ArrayList<>();
	private int left;
	private int top;

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
	protected void init() {
		this.left = (this.width - PANEL_WIDTH) / 2;
		this.top = Math.max(4, (this.height - PANEL_HEIGHT) / 2);
		this.hiltButtons.clear();
		this.colorButtons.clear();
		this.styleButtons.clear();

		int y = this.top + 34;

		for (SaberHilt option : SaberHilts.all().values()) {
			Button button = Button.builder(option.displayName(), b -> this.selectHilt(option))
					.bounds(this.left + 30, y, HILT_BUTTON_WIDTH, 20)
					.build();
			this.hiltButtons.add(new HiltButton(option, this.addRenderableWidget(button)));
			y += ROW;
		}

		int colorLeft = this.left + PANEL_WIDTH - 10 - 2 * COLOR_BUTTON_WIDTH - 4;
		int index = 0;

		for (Identifier id : SaberVariant.COLORS) {
			int x = colorLeft + index % 2 * (COLOR_BUTTON_WIDTH + 4);
			int rowY = this.top + 34 + index / 2 * ROW;
			Button button = Button.builder(colorName(id).copy().withColor(textColor(id)), b -> this.selectColor(id))
					.bounds(x, rowY, COLOR_BUTTON_WIDTH, 20)
					.build();
			this.colorButtons.add(new ColorButton(id, this.addRenderableWidget(button)));
			index++;
		}

		// Fighting style: two buttons for single blades, one for the staff, in the same place.
		int styleY = this.top + 34 + 3 * ROW + 2 + PREVIEW_SIZE + 30;

		for (Identifier id : SaberVariant.STYLES) {
			boolean staff = SaberVariant.STAFF.equals(id);
			int x = staff ? colorLeft : colorLeft + SaberVariant.SINGLE_STYLES.indexOf(id) * (COLOR_BUTTON_WIDTH + 4);
			int width = staff ? 2 * COLOR_BUTTON_WIDTH + 4 : COLOR_BUTTON_WIDTH;
			Button button = Button.builder(styleName(id), b -> this.style = id)
					.bounds(x, styleY, width, 20)
					.tooltip(Tooltip.create(Component.translatable("sabercraft.style." + id.getPath() + ".description")))
					.build();
			this.styleButtons.add(new StyleButton(id, this.addRenderableWidget(button)));
		}

		this.updateStyleButtons();

		int buttonsY = this.top + PANEL_HEIGHT - 28;
		this.addRenderableWidget(Button.builder(Component.translatable("sabercraft.customize.apply"), b -> this.apply())
				.bounds(this.width / 2 - 104, buttonsY, 100, 20)
				.build());
		this.addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> this.onClose())
				.bounds(this.width / 2 + 4, buttonsY, 100, 20)
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

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		graphics.fill(this.left, this.top, this.left + PANEL_WIDTH, this.top + PANEL_HEIGHT, 0xD0101014);
		graphics.outline(this.left, this.top, PANEL_WIDTH, PANEL_HEIGHT, 0xFF4A4A55);
		graphics.centeredText(this.font, this.title, this.width / 2, this.top + 8, 0xFFFFFFFF);
		graphics.text(this.font, Component.translatable("sabercraft.customize.hilt"), this.left + 10, this.top + 22, 0xFFA0A0A8);
		int colorLeft = this.left + PANEL_WIDTH - 10 - 2 * COLOR_BUTTON_WIDTH - 4;
		graphics.text(this.font, Component.translatable("sabercraft.customize.color"), colorLeft, this.top + 22, 0xFFA0A0A8);

		super.extractRenderState(graphics, mouseX, mouseY, a);

		// Small icon of each hilt in the chosen color, and a frame around the current choices.
		for (HiltButton entry : this.hiltButtons) {
			Button button = entry.button();
			graphics.fakeItem(previewStack(entry.hilt().id(), this.color), this.left + 10, button.getY() + 2);

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

		int styleY = this.top + 34 + 3 * ROW + 2 + PREVIEW_SIZE + 30;
		graphics.text(this.font, Component.translatable("sabercraft.customize.style"), colorLeft, styleY - 11, 0xFFA0A0A8);
		this.extractPreview(graphics, colorLeft);

		// Name and description of the chosen hilt below the hilt list.
		SaberHilt chosen = SaberHilts.get(this.hilt);
		Component line = chosen.displayName().copy().append(" · ").append(colorName(this.color)).append(" · ").append(styleName(this.style));
		int textTop = this.top + 34 + SaberHilts.all().size() * ROW + 6;
		graphics.text(this.font, line, this.left + 10, textTop, 0xFFFFFFFF);
		// A problem (no server support, no saber) replaces the description, so nothing overlaps.
		boolean problem = !this.status.getString().isEmpty();
		graphics.textWithWordWrap(
				this.font, problem ? this.status : chosen.description(), this.left + 10, textTop + 12, colorLeft - this.left - 20,
				problem ? 0xFFFF6060 : 0xFFB0B0B8
		);
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

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private record HiltButton(SaberHilt hilt, Button button) {
	}

	private record ColorButton(Identifier color, Button button) {
	}

	private record StyleButton(Identifier style, Button button) {
	}
}
