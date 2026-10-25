package com.phiro.sabercraft.client.menu;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Util;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.blaster.BlasterType;
import com.phiro.sabercraft.blaster.Blasters;
import com.phiro.sabercraft.client.blaster.BlasterClient;
import com.phiro.sabercraft.item.ItemConversion;
import com.phiro.sabercraft.network.SelectBlasterPayload;

/**
 * The blaster area of the SaberCraft menu (Phase 11). On the left a gallery of every blaster type
 * as cards with icon and short name; on the right a large 3D model of the chosen blaster that turns
 * by itself and can be turned with the mouse, its full name, bars for damage, fire rate, range and
 * precision, and a short description. "Equip" makes the bow in the hand this blaster (or changes
 * the type of the blaster in the hand), "Back to bow" turns it back into the plain bow. Both are
 * requests to the server, which checks them like {@code /sabercraft blaster}. The gallery can be
 * browsed without a bow.
 */
public class BlasterMenuScreen extends SaberMenuScreen {
	private static final int CARD_WIDTH = 50;
	private static final int CARD_HEIGHT = 40;
	private static final int CARDS_PER_ROW = 3;
	private static final int PREVIEW_LEFT = 170;
	private static final int PREVIEW_WIDTH = 150;
	private static final int PREVIEW_HEIGHT = 74;

	private BlasterType chosen;
	private final List<Card> cards = new ArrayList<>();
	private @Nullable Button equip;
	private @Nullable Button revert;
	private float yaw = -60.0F;
	private float pitch = 12.0F;
	private long lastFrame = Util.getMillis();
	private boolean dragging;

	public BlasterMenuScreen() {
		super(Component.translatable("sabercraft.blaster.menu.title"));
		this.chosen = BlasterType.E11;
	}

	@Override
	protected Tab tab() {
		return Tab.BLASTER;
	}

	@Override
	protected void initPanel() {
		this.cards.clear();
		// Start with the blaster in hand, if there is one.
		InteractionHand hand = this.bowHand();
		BlasterType held = hand != null ? BlasterType.of(this.minecraft.player.getItemInHand(hand)) : null;

		if (held != null && this.cards.isEmpty() && this.chosen == BlasterType.E11) {
			this.chosen = held;
		}

		int index = 0;

		for (BlasterType type : BlasterType.ALL) {
			int x = this.left + 10 + index % CARDS_PER_ROW * (CARD_WIDTH + 3);
			int y = this.top + 22 + index / CARDS_PER_ROW * (CARD_HEIGHT + 3);
			Button card = new CardButton(x, y, name(type), b -> this.choose(type));
			card.setTooltip(Tooltip.create(name(type).copy().append("\n").append(description(type))));
			this.cards.add(new Card(type, this.addRenderableWidget(card)));
			index++;
		}

		int buttonsY = this.top + PANEL_HEIGHT - 50;
		this.equip = this.addRenderableWidget(Button.builder(Component.translatable("sabercraft.blaster.menu.equip"), b -> this.equip())
				.bounds(this.left + PREVIEW_LEFT, buttonsY, PREVIEW_WIDTH, 20)
				.build());
		this.revert = this.addRenderableWidget(Button.builder(Component.translatable("sabercraft.blaster.menu.revert"), b -> this.revert())
				.bounds(this.left + PREVIEW_LEFT, buttonsY + 22, PREVIEW_WIDTH, 20)
				.build());
		this.updateButtons();
	}

	private void choose(BlasterType type) {
		if (type != this.chosen) {
			this.chosen = type;
			this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvent.createVariableRangeEvent(SaberCraft.id("ui.blaster_select")), 1.0F, 0.5F));
		}

		this.updateButtons();
	}

	/** The hand with a bow (converted or not): the main hand first. */
	private @Nullable InteractionHand bowHand() {
		if (this.minecraft == null || this.minecraft.player == null) {
			return null;
		}

		for (InteractionHand hand : InteractionHand.values()) {
			if (this.minecraft.player.getItemInHand(hand).is(Items.BOW)) {
				return hand;
			}
		}

		return null;
	}

	private boolean serverSupports() {
		return BlasterClient.serverHasBlasters() && ClientPlayNetworking.canSend(SelectBlasterPayload.TYPE);
	}

	private void updateButtons() {
		InteractionHand hand = this.bowHand();
		BlasterType held = hand != null ? BlasterType.of(this.minecraft.player.getItemInHand(hand)) : null;

		if (this.equip != null) {
			this.equip.active = hand != null && held != this.chosen && this.serverSupports();
		}

		if (this.revert != null) {
			this.revert.active = held != null && this.serverSupports();
		}
	}

	private void equip() {
		InteractionHand hand = this.bowHand();

		if (hand != null && this.serverSupports()) {
			ClientPlayNetworking.send(new SelectBlasterPayload(hand, this.chosen.id().getPath()));
		}
	}

	private void revert() {
		InteractionHand hand = this.bowHand();

		if (hand != null && this.serverSupports()) {
			ClientPlayNetworking.send(new SelectBlasterPayload(hand, ""));
		}
	}

	@Override
	public void tick() {
		// The server's answer changes the item in hand; the buttons follow it.
		this.updateButtons();
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (this.inPreview(event.x(), event.y())) {
			this.dragging = true;
			return true;
		}

		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (this.dragging) {
			this.yaw += (float) dx * 2.0F;
			this.pitch = Math.clamp(this.pitch + (float) dy, -60.0F, 60.0F);
			return true;
		}

		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		this.dragging = false;
		return super.mouseReleased(event);
	}

	private boolean inPreview(double x, double y) {
		int x0 = this.left + PREVIEW_LEFT;
		int y0 = this.top + 22;
		return x >= x0 && x < x0 + PREVIEW_WIDTH && y >= y0 && y < y0 + PREVIEW_HEIGHT;
	}

	@Override
	protected void extractPanel(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		long now = Util.getMillis();
		float seconds = Math.min(0.1F, (now - this.lastFrame) / 1000.0F);
		this.lastFrame = now;

		if (!this.dragging) {
			this.yaw += seconds * 30.0F;
		}

		// Gallery: icon and short name on each card, a frame around the chosen one.
		for (Card card : this.cards) {
			Button button = card.button();
			int iconX = button.getX() + (CARD_WIDTH - 24) / 2;
			int iconY = button.getY() + 3;
			graphics.pose().pushMatrix();
			graphics.pose().translate(iconX, iconY);
			graphics.pose().scale(1.5F, 1.5F);
			graphics.fakeItem(stack(card.type()), 0, 0);
			graphics.pose().popMatrix();
			Component shortName = Component.translatable(card.type().translationKey() + ".short");
			graphics.centeredText(this.font, shortName, button.getX() + CARD_WIDTH / 2, button.getY() + CARD_HEIGHT - 11, 0xFFE8E8F0);

			if (card.type() == this.chosen) {
				graphics.outline(button.getX() - 1, button.getY() - 1, CARD_WIDTH + 2, CARD_HEIGHT + 2, 0xFF000000 | Blasters.boltRgb(card.type()));
			}
		}

		// The big turning model.
		int x0 = this.left + PREVIEW_LEFT;
		int y0 = this.top + 22;
		graphics.fill(x0, y0, x0 + PREVIEW_WIDTH, y0 + PREVIEW_HEIGHT, 0xFF15161C);
		graphics.outline(x0, y0, PREVIEW_WIDTH, PREVIEW_HEIGHT, 0xFF3A3B46);
		ModelPreview.draw(graphics, stack(this.chosen), this.yaw, this.pitch, x0 + 2, y0 + 2, x0 + PREVIEW_WIDTH - 2, y0 + PREVIEW_HEIGHT - 2);

		// Name, values and description.
		int y = y0 + PREVIEW_HEIGHT + 4;
		graphics.text(this.font, name(this.chosen), x0, y, 0xFFFFFFFF);
		y += 11;
		float maxDamage = 0.0F;
		float maxRate = 0.0F;
		float maxRange = 0.0F;

		for (BlasterType type : BlasterType.ALL) {
			maxDamage = Math.max(maxDamage, type.damage() * type.bolts());
			maxRate = Math.max(maxRate, type.shotsPerSecond());
			maxRange = Math.max(maxRange, type.range());
		}

		y = this.bar(graphics, "damage", this.chosen.damage() * this.chosen.bolts() / maxDamage, x0, y);
		y = this.bar(graphics, "rate", this.chosen.shotsPerSecond() / maxRate, x0, y);
		y = this.bar(graphics, "range", this.chosen.range() / maxRange, x0, y);
		y = this.bar(graphics, "precision", 1.0F - Math.min(1.0F, this.chosen.spread() / 2.5F) * 0.9F, x0, y);

		// Under the gallery: what is in the hand, the arrows, or why the buttons are off.
		int noteY = this.top + 22 + 3 * (CARD_HEIGHT + 3) + 2;
		graphics.textWithWordWrap(this.font, description(this.chosen), this.left + 10, noteY, PREVIEW_LEFT - 16, 0xFFB0B0B8);
		Component status = this.status();
		graphics.textWithWordWrap(this.font, status, this.left + 10, this.top + PANEL_HEIGHT - 30, PREVIEW_LEFT - 16, 0xFFD8C890);
	}

	/** One labelled bar: 0..1. Returns the y of the next line. */
	private int bar(GuiGraphicsExtractor graphics, String key, float value, int x, int y) {
		graphics.text(this.font, Component.translatable("sabercraft.blaster.menu.stat." + key), x, y, 0xFFA0A0A8);
		int barX = x + 62;
		int width = PREVIEW_WIDTH - 62;
		graphics.fill(barX, y + 2, barX + width, y + 6, 0xFF2A2B33);
		graphics.fill(barX, y + 2, barX + Math.round(width * Math.clamp(value, 0.05F, 1.0F)), y + 6, 0xFF000000 | Blasters.boltRgb(this.chosen));
		return y + 10;
	}

	private Component status() {
		if (!this.serverSupports()) {
			return Component.translatable("sabercraft.blaster.menu.unsupported");
		}

		InteractionHand hand = this.bowHand();

		if (hand == null) {
			return Component.translatable("sabercraft.blaster.menu.no_bow");
		}

		ItemStack stack = this.minecraft.player.getItemInHand(hand);
		BlasterType held = BlasterType.of(stack);
		Component inHand = held != null ? name(held) : Component.translatable("sabercraft.blaster.menu.plain_bow");
		Component line = Component.translatable("sabercraft.blaster.menu.in_hand", inHand);

		if (BlasterClient.needsAmmo(this.minecraft.player)) {
			line = line.copy().append("\n").append(Component.translatable("sabercraft.hud.ammo", Blasters.countAmmo(this.minecraft.player.getInventory())));
		}

		return line;
	}

	/** A bow showing this blaster, for icons and the preview. */
	static ItemStack stack(BlasterType type) {
		ItemStack stack = new ItemStack(Items.BOW);
		ItemConversion.toBlaster(stack, type);
		return stack;
	}

	static Component name(BlasterType type) {
		return Component.translatable(type.translationKey());
	}

	static Component description(BlasterType type) {
		return Component.translatable(type.translationKey() + ".description");
	}

	/** The type shown in the preview, for tests. */
	public BlasterType chosen() {
		return this.chosen;
	}

	/** A card of the gallery: only its frame is drawn by the button, icon and name come on top. */
	private static final class CardButton extends Button {
		private CardButton(int x, int y, Component message, OnPress onPress) {
			super(x, y, CARD_WIDTH, CARD_HEIGHT, message, onPress, DEFAULT_NARRATION);
		}

		@Override
		protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
			this.extractDefaultSprite(graphics);
		}
	}

	private record Card(BlasterType type, Button button) {
	}
}
