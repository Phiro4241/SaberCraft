package com.phiro.sabercraft.client.menu;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Util;

import com.phiro.sabercraft.SaberCraft;
import com.phiro.sabercraft.client.droid.DroidClient;
import com.phiro.sabercraft.client.droid.DroidModels;
import com.phiro.sabercraft.client.droid.DroidPose;
import com.phiro.sabercraft.client.droid.DroidPreview;
import com.phiro.sabercraft.droid.DroidType;
import com.phiro.sabercraft.network.DroidStatusPayload;

/**
 * The droid area of the SaberCraft menu (Phase 11). On the left a gallery of every droid as cards
 * (a green mark on the ones that are out); on the right the chosen droid large and animated (turn
 * it with the mouse, click it to hear it), its name, what its ability does and how it is doing.
 * The buttons call the droid (or call it back to you), tell it to stay or follow, use its ability
 * and send it home. All of them are requests the server checks, as {@code /sabercraft droid}.
 */
public class DroidMenuScreen extends SaberMenuScreen {
	private static final int CARD_WIDTH = 50;
	private static final int CARD_HEIGHT = 40;
	private static final int CARDS_PER_ROW = 3;
	private static final int PREVIEW_LEFT = 170;
	private static final int PREVIEW_WIDTH = 150;
	private static final int PREVIEW_HEIGHT = 92;
	private static final int BUTTON_WIDTH = 74;

	private static DroidType lastChosen = DroidType.R2D2;

	private DroidType chosen = lastChosen;
	private final List<Card> cards = new ArrayList<>();
	private @Nullable Button call;
	private @Nullable Button stay;
	private @Nullable Button ability;
	private @Nullable Button dismiss;
	private float yaw = 200.0F;
	private float pitch = 10.0F;
	private long lastFrame = Util.getMillis();
	private final long opened = Util.getMillis();
	private boolean dragging;
	private boolean dragged;
	private long talkStart = -10000L;

	public DroidMenuScreen() {
		super(Component.translatable("sabercraft.droid.menu.title"));
	}

	@Override
	protected Tab tab() {
		return Tab.DROIDS;
	}

	@Override
	protected void initPanel() {
		this.cards.clear();
		int index = 0;

		for (DroidType type : DroidType.values()) {
			int x = this.left + 10 + index % CARDS_PER_ROW * (CARD_WIDTH + 3);
			int y = this.top + 22 + index / CARDS_PER_ROW * (CARD_HEIGHT + 3);
			Button card = new CardButton(x, y, Component.translatable(type.translationKey()), b -> this.choose(type));
			card.setTooltip(Tooltip.create(Component.translatable(type.translationKey()).append("\n").append(description(type))));
			this.cards.add(new Card(type, this.addRenderableWidget(card)));
			index++;
		}

		int x = this.left + PREVIEW_LEFT;
		int y = this.top + PANEL_HEIGHT - 50;
		this.call = this.addRenderableWidget(Button.builder(Component.translatable("sabercraft.droid.menu.call"), b -> this.send("spawn"))
				.bounds(x, y, BUTTON_WIDTH, 20).build());
		this.ability = this.addRenderableWidget(Button.builder(Component.translatable("sabercraft.droid.menu.ability"), b -> this.send("ability"))
				.bounds(x + BUTTON_WIDTH + 2, y, BUTTON_WIDTH, 20).build());
		this.stay = this.addRenderableWidget(Button.builder(Component.translatable("sabercraft.droid.menu.stay"), b -> this.send("stay"))
				.bounds(x, y + 22, BUTTON_WIDTH, 20).build());
		this.dismiss = this.addRenderableWidget(Button.builder(Component.translatable("sabercraft.droid.menu.dismiss"), b -> this.send("dismiss"))
				.bounds(x + BUTTON_WIDTH + 2, y + 22, BUTTON_WIDTH, 20).build());
		this.updateButtons();
	}

	private void choose(DroidType type) {
		if (type != this.chosen) {
			this.chosen = type;
			lastChosen = type;
			this.talk();
		}

		this.updateButtons();
	}

	/** The droid in the preview says something. */
	private void talk() {
		this.talkStart = Util.getMillis();
		this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(
				SoundEvent.createVariableRangeEvent(SaberCraft.id("droid." + this.chosen.id() + ".interact")), 1.0F, 0.6F));
	}

	private void send(String action) {
		DroidClient.command(action, this.chosen);
	}

	private void updateButtons() {
		DroidStatusPayload status = DroidClient.status();
		boolean supported = DroidClient.serverSupports() && status != null && status.enabled();
		DroidStatusPayload.Entry entry = DroidClient.entry(this.chosen);
		boolean full = status != null && entry == null && status.droids().size() >= status.limit();

		if (this.call != null) {
			this.call.active = supported && !full;
			this.call.setMessage(Component.translatable(entry != null ? "sabercraft.droid.menu.recall" : "sabercraft.droid.menu.call"));
		}

		if (this.stay != null) {
			this.stay.active = supported && entry != null && entry.entityId() >= 0;
			this.stay.setMessage(Component.translatable(entry != null && entry.staying() ? "sabercraft.droid.menu.follow" : "sabercraft.droid.menu.stay"));
		}

		if (this.ability != null) {
			this.ability.active = supported && entry != null && DroidClient.cooldownSeconds(entry) == 0;
			this.ability.setTooltip(Tooltip.create(Component.translatable("sabercraft.droid." + this.chosen.id() + ".ability")));
		}

		if (this.dismiss != null) {
			this.dismiss.active = DroidClient.serverSupports() && entry != null;
		}
	}

	@Override
	public void tick() {
		this.updateButtons();
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (this.inPreview(event.x(), event.y())) {
			this.dragging = true;
			this.dragged = false;
			return true;
		}

		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (this.dragging) {
			this.yaw += (float) dx * 2.0F;
			this.pitch = Math.clamp(this.pitch + (float) dy, -40.0F, 50.0F);
			this.dragged |= Math.abs(dx) + Math.abs(dy) > 0.5;
			return true;
		}

		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (this.dragging && !this.dragged) {
			this.talk();
		}

		this.dragging = false;
		return super.mouseReleased(event);
	}

	private boolean inPreview(double x, double y) {
		int x0 = this.left + PREVIEW_LEFT;
		int y0 = this.top + 22;
		return x >= x0 && x < x0 + PREVIEW_WIDTH && y >= y0 && y < y0 + PREVIEW_HEIGHT;
	}

	/** The preview walks on the spot every few seconds, so its moving parts show. */
	private DroidPose previewPose(long now) {
		float seconds = (now - this.opened) / 1000.0F;
		float cycle = seconds % 8.0F;
		float speed = cycle < 4.0F ? 0.0F : Math.min(1.0F, Math.min(cycle - 4.0F, 8.0F - cycle) * 2.0F) * 0.8F;
		float talk = (now - this.talkStart) / 1200.0F;
		return new DroidPose(seconds * 2.4F, speed, (float) Math.sin(seconds * 0.7) * 25.0F, 0.0F, seconds * 20.0F,
				talk < 1.0F ? DroidPose.TALK : 0, Math.max(0.0F, talk));
	}

	@Override
	protected void extractPanel(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		long now = Util.getMillis();
		float seconds = Math.min(0.1F, (now - this.lastFrame) / 1000.0F);
		this.lastFrame = now;

		if (!this.dragging) {
			this.yaw += seconds * 20.0F;
		}

		// Gallery: icon and name on each card, green when the droid is out, a frame around the chosen one.
		for (Card card : this.cards) {
			Button button = card.button();
			int iconX = button.getX() + (CARD_WIDTH - 24) / 2;
			int iconY = button.getY() + 3;
			graphics.pose().pushMatrix();
			graphics.pose().translate(iconX, iconY);
			graphics.pose().scale(1.5F, 1.5F);
			graphics.fakeItem(DroidModels.icon(card.type()), 0, 0);
			graphics.pose().popMatrix();
			graphics.centeredText(this.font, Component.translatable(card.type().translationKey() + ".short"), button.getX() + CARD_WIDTH / 2, button.getY() + CARD_HEIGHT - 11,
					0xFFE8E8F0);

			if (DroidClient.entry(card.type()) != null) {
				graphics.fill(button.getX() + CARD_WIDTH - 7, button.getY() + 3, button.getX() + CARD_WIDTH - 3, button.getY() + 7, 0xFF52D273);
			}

			if (card.type() == this.chosen) {
				graphics.outline(button.getX() - 1, button.getY() - 1, CARD_WIDTH + 2, CARD_HEIGHT + 2, 0xFF7FB2FF);
			}
		}

		// The big droid.
		int x0 = this.left + PREVIEW_LEFT;
		int y0 = this.top + 22;
		graphics.fill(x0, y0, x0 + PREVIEW_WIDTH, y0 + PREVIEW_HEIGHT, 0xFF15161C);
		graphics.outline(x0, y0, PREVIEW_WIDTH, PREVIEW_HEIGHT, 0xFF3A3B46);
		DroidPreview.draw(graphics, this.chosen, this.previewPose(now), this.yaw, this.pitch, x0 + 2, y0 + 2, x0 + PREVIEW_WIDTH - 2, y0 + PREVIEW_HEIGHT - 2);

		// Name, ability and state.
		int y = y0 + PREVIEW_HEIGHT + 4;
		graphics.text(this.font, Component.translatable(this.chosen.translationKey()), x0, y, 0xFFFFFFFF);
		y += 11;
		graphics.textWithWordWrap(this.font, Component.translatable("sabercraft.droid." + this.chosen.id() + ".ability"), x0, y, PREVIEW_WIDTH, 0xFFB8C8E0);
		graphics.textWithWordWrap(this.font, this.state(), x0, this.top + PANEL_HEIGHT - 62, PREVIEW_WIDTH, 0xFFD8C890);

		// Under the gallery: what the droid is like, and how to talk to droids in the world.
		int noteY = this.top + 22 + 3 * (CARD_HEIGHT + 3) + 2;
		graphics.textWithWordWrap(this.font, description(this.chosen), this.left + 10, noteY, PREVIEW_LEFT - 16, 0xFFB0B0B8);
		graphics.textWithWordWrap(this.font, this.hint(), this.left + 10, this.top + PANEL_HEIGHT - 40, PREVIEW_LEFT - 16, 0xFF8890A0);
	}

	private Component state() {
		DroidStatusPayload status = DroidClient.status();

		if (!DroidClient.serverSupports() || status == null) {
			return Component.translatable("sabercraft.droid.menu.unsupported");
		}

		if (!status.enabled()) {
			return Component.translatable("sabercraft.droid.disabled");
		}

		DroidStatusPayload.Entry entry = DroidClient.entry(this.chosen);

		if (entry == null) {
			return status.droids().size() >= status.limit() ? Component.translatable("sabercraft.droid.menu.full", status.limit())
					: Component.translatable("sabercraft.droid.menu.home", status.droids().size(), status.limit());
		}

		Component mode = Component.translatable(entry.staying() ? "sabercraft.droid.menu.state.staying" : "sabercraft.droid.menu.state.following");
		int cooldown = DroidClient.cooldownSeconds(entry);
		return cooldown > 0 ? Component.translatable("sabercraft.droid.menu.state.cooldown", mode, cooldown) : mode;
	}

	private Component hint() {
		return Component.translatable("sabercraft.droid.menu.hint");
	}

	static Component description(DroidType type) {
		return Component.translatable("sabercraft.droid." + type.id() + ".description");
	}

	/** Whether the call button can be pressed, for tests. */
	public boolean callActive() {
		return this.call != null && this.call.active;
	}

	/** Whether the ability button can be pressed, for tests. */
	public boolean abilityActive() {
		return this.ability != null && this.ability.active;
	}

	/** The droid shown, for tests. */
	public DroidType chosen() {
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

	private record Card(DroidType type, Button button) {
	}
}
