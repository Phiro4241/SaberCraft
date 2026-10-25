package com.phiro.sabercraft.client.menu;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;

import com.phiro.sabercraft.client.ship.ShipClient;
import com.phiro.sabercraft.client.ship.ShipModels;
import com.phiro.sabercraft.client.ship.ShipPreview;
import com.phiro.sabercraft.network.ShipStatusPayload;
import com.phiro.sabercraft.ship.ShipType;

/**
 * The hangar of the SaberCraft menu (Phase 11). On the left every starship as a card: the X-Wing
 * can be flown, the others are shown as planned. On the right the chosen ship large and turning
 * (drag it with the mouse), what it is and how it flies. The buttons call the ship to you, climb
 * straight in, or send it back to the hangar. All of them are requests the server checks, as
 * {@code /sabercraft ship}.
 */
public class ShipMenuScreen extends SaberMenuScreen {
	private static final int CARD_WIDTH = 150;
	private static final int CARD_HEIGHT = 30;
	private static final int PREVIEW_LEFT = 170;
	private static final int PREVIEW_WIDTH = 150;
	private static final int PREVIEW_HEIGHT = 92;
	private static final int BUTTON_WIDTH = 49;

	private static ShipType lastChosen = ShipType.XWING;

	private ShipType chosen = lastChosen;
	private final List<Card> cards = new ArrayList<>();
	private @Nullable Button call;
	private @Nullable Button fly;
	private @Nullable Button dismiss;
	private float yaw = 210.0F;
	private float pitch = 18.0F;
	private long lastFrame = Util.getMillis();
	private final long opened = Util.getMillis();
	private boolean dragging;

	public ShipMenuScreen() {
		super(Component.translatable("sabercraft.ship.menu.title"));
	}

	@Override
	protected Tab tab() {
		return Tab.SHIPS;
	}

	@Override
	protected void initPanel() {
		this.cards.clear();
		int y = this.top + 22;

		for (ShipType type : ShipType.ALL) {
			Button card = new CardButton(this.left + 10, y, Component.translatable(type.translationKey()), b -> this.choose(type));
			card.setTooltip(Tooltip.create(description(type)));
			this.cards.add(new Card(type, this.addRenderableWidget(card)));
			y += CARD_HEIGHT + 3;
		}

		int x = this.left + PREVIEW_LEFT;
		int buttons = this.top + PANEL_HEIGHT - 28;
		this.call = this.addRenderableWidget(Button.builder(Component.translatable("sabercraft.ship.menu.call"), b -> this.send("call"))
				.bounds(x, buttons, BUTTON_WIDTH, 20).build());
		this.fly = this.addRenderableWidget(Button.builder(Component.translatable("sabercraft.ship.menu.fly"), b -> this.send("board"))
				.bounds(x + BUTTON_WIDTH + 1, buttons, BUTTON_WIDTH, 20).build());
		this.dismiss = this.addRenderableWidget(Button.builder(Component.translatable("sabercraft.ship.menu.dismiss"), b -> this.send("dismiss"))
				.bounds(x + 2 * (BUTTON_WIDTH + 1), buttons, BUTTON_WIDTH, 20).build());
		this.updateButtons();
	}

	private void choose(ShipType type) {
		this.chosen = type;
		lastChosen = type;
		this.updateButtons();
	}

	private void send(String action) {
		ShipClient.command(action, this.chosen);

		// Climbing in from the menu: straight to the cockpit.
		if (action.equals("board")) {
			this.onClose();
		}
	}

	private void updateButtons() {
		ShipStatusPayload status = ShipClient.status();
		boolean usable = ShipClient.serverSupports() && status != null && status.enabled() && this.chosen.available();
		boolean out = status != null && status.ship().equals(this.chosen.id());

		if (this.call != null) {
			this.call.active = usable;
			this.call.setTooltip(Tooltip.create(Component.translatable("sabercraft.ship.menu.call.tooltip")));
		}

		if (this.fly != null) {
			this.fly.active = usable;
			this.fly.setTooltip(Tooltip.create(Component.translatable("sabercraft.ship.menu.fly.tooltip")));
		}

		if (this.dismiss != null) {
			this.dismiss.active = ShipClient.serverSupports() && out;
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
			return true;
		}

		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (this.dragging) {
			this.yaw += (float) dx * 2.0F;
			this.pitch = Math.clamp(this.pitch + (float) dy, -40.0F, 60.0F);
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
			this.yaw += seconds * 15.0F;
		}

		ShipStatusPayload status = ShipClient.status();

		for (Card card : this.cards) {
			Button button = card.button();
			ItemStack icon = ShipModels.icon(card.type());

			if (icon != null) {
				graphics.fakeItem(icon, button.getX() + 6, button.getY() + 7);
			}

			int color = card.type().available() ? 0xFFE8E8F0 : 0xFF8A8A96;
			graphics.text(this.font, Component.translatable(card.type().translationKey()), button.getX() + 28, button.getY() + 6, color);
			Component note = !card.type().available() ? Component.translatable("sabercraft.ship.menu.planned")
					: status != null && status.ship().equals(card.type().id()) ? Component.translatable("sabercraft.ship.menu.out")
					: Component.translatable("sabercraft.ship.menu.ready");
			graphics.text(this.font, note, button.getX() + 28, button.getY() + 17, card.type().available() ? 0xFF70C890 : 0xFFB09060);

			if (card.type() == this.chosen) {
				graphics.outline(button.getX() - 1, button.getY() - 1, CARD_WIDTH + 2, CARD_HEIGHT + 2, 0xFF7FB2FF);
			}
		}

		// The big ship, its S-foils opening and closing.
		int x0 = this.left + PREVIEW_LEFT;
		int y0 = this.top + 22;
		graphics.fill(x0, y0, x0 + PREVIEW_WIDTH, y0 + PREVIEW_HEIGHT, 0xFF0B0D16);
		graphics.outline(x0, y0, PREVIEW_WIDTH, PREVIEW_HEIGHT, 0xFF3A3B46);

		if (this.chosen.available()) {
			float cycle = ((now - this.opened) / 1000.0F) % 8.0F;
			float foils = Mth.clamp(cycle < 4.0F ? cycle - 1.0F : 7.0F - cycle, 0.0F, 1.0F);
			ShipPreview.draw(graphics, this.chosen, foils, this.yaw, this.pitch, x0 + 2, y0 + 2, x0 + PREVIEW_WIDTH - 2, y0 + PREVIEW_HEIGHT - 2);
		} else {
			graphics.centeredText(this.font, Component.translatable("sabercraft.ship.menu.planned"), x0 + PREVIEW_WIDTH / 2, y0 + PREVIEW_HEIGHT / 2 - 4,
					0xFFB09060);
		}

		int y = y0 + PREVIEW_HEIGHT + 4;
		graphics.text(this.font, Component.translatable(this.chosen.translationKey()), x0, y, 0xFFFFFFFF);
		graphics.textWithWordWrap(this.font, description(this.chosen), x0, y + 11, PREVIEW_WIDTH, 0xFFB8C8E0);
		graphics.textWithWordWrap(this.font, this.state(status), this.left + 10, this.top + 22 + ShipType.ALL.size() * (CARD_HEIGHT + 3) + 4,
				PREVIEW_LEFT - 16, 0xFFD8C890);
	}

	private Component state(@Nullable ShipStatusPayload status) {
		if (!ShipClient.serverSupports() || status == null) {
			return Component.translatable("sabercraft.ship.menu.unsupported");
		}

		if (!status.enabled()) {
			return Component.translatable("sabercraft.ship.disabled");
		}

		return Component.translatable("sabercraft.ship.menu.hint");
	}

	static Component description(ShipType type) {
		return Component.translatable("ship.sabercraft." + type.id() + ".description");
	}

	/** Whether the call button can be pressed, for tests. */
	public boolean callActive() {
		return this.call != null && this.call.active;
	}

	/** Chooses a ship as a click on its card would, for tests. */
	public void chooseForTest(ShipType type) {
		this.choose(type);
	}

	/** A card of the hangar: only its frame is drawn by the button, icon and text come on top. */
	private static final class CardButton extends Button {
		private CardButton(int x, int y, Component message, OnPress onPress) {
			super(x, y, CARD_WIDTH, CARD_HEIGHT, message, onPress, DEFAULT_NARRATION);
		}

		@Override
		protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
			this.extractDefaultSprite(graphics);
		}
	}

	private record Card(ShipType type, Button button) {
	}
}
