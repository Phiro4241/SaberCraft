package com.phiro.sabercraft.client.menu;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import com.phiro.sabercraft.client.select.SaberSelection;
import com.phiro.sabercraft.saber.SaberVariant;

/**
 * Phase 12: the saber area of the menu while a plain sword is held. Only chosen swords are
 * lightsabers; here the sword in the hand is chosen with one button. It gets a hilt that fits its
 * material and a blade color, which the saber area that opens next changes. The sword stays the
 * same item (material, durability, enchantments, name), see
 * {@link com.phiro.sabercraft.item.ItemConversion}.
 */
public class SaberConvertScreen extends SaberMenuScreen {
	private final InteractionHand hand;
	private Component status = Component.empty();

	public SaberConvertScreen(InteractionHand hand) {
		super(Component.translatable("sabercraft.menu.convert.title"));
		this.hand = hand;
	}

	@Override
	protected Tab tab() {
		return Tab.SABER;
	}

	@Override
	protected void initPanel() {
		this.button(0, 5, Component.translatable("sabercraft.menu.convert.button"), () -> {
			SaberSelection.Result result = SaberSelection.convert(this.minecraft, this.hand, true);

			if (result == SaberSelection.Result.UNSUPPORTED) {
				this.status = Component.translatable("sabercraft.customize.unsupported");
			}

			return true;
		});
		this.button(1, 5, Component.translatable("gui.cancel"), () -> {
			this.onClose();
			return true;
		});
	}

	@Override
	public void tick() {
		if (this.minecraft == null || this.minecraft.player == null) {
			return;
		}

		ItemStack stack = this.minecraft.player.getItemInHand(this.hand);

		// Chosen: on to the hilt, color and style. Gone from the hand: nothing to choose.
		if (SaberVariant.canHold(stack)) {
			open(this.minecraft, Tab.SABER);
		} else if (!SaberVariant.isSword(stack)) {
			this.onClose();
		}
	}

	@Override
	protected void extractPanel(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		if (this.minecraft == null || this.minecraft.player == null) {
			return;
		}

		ItemStack stack = this.minecraft.player.getItemInHand(this.hand);
		graphics.pose().pushMatrix();
		graphics.pose().translate(this.left + 14.0F, this.row(0));
		graphics.pose().scale(2.0F, 2.0F);
		graphics.fakeItem(stack, 0, 0);
		graphics.pose().popMatrix();
		graphics.text(this.font, stack.getHoverName(), this.left + 54, this.row(0) + 4, 0xFFFFFFFF);
		graphics.text(this.font, Component.translatable("sabercraft.menu.convert.plain"), this.left + 54, this.row(0) + 18, 0xFFA8A8B0);
		boolean problem = !this.status.getString().isEmpty();
		graphics.textWithWordWrap(this.font, problem ? this.status : Component.translatable("sabercraft.menu.convert.note"), this.left + 10,
				this.row(2), PANEL_WIDTH - 20, problem ? 0xFFFF6060 : 0xFFA8A8B0);
	}
}
