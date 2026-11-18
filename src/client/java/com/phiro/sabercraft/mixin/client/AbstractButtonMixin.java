package com.phiro.sabercraft.mixin.client;

import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import com.phiro.sabercraft.client.menu.TitleStyle;

/**
 * Phase 24: the title screen's buttons in SaberCraft's look (see {@link TitleStyle#button}). Only
 * the button's frame is drawn differently; its label, icon, sound and action are the vanilla ones.
 */
@Mixin(AbstractButton.class)
public abstract class AbstractButtonMixin extends AbstractWidget.WithInactiveMessage {
	@Shadow
	private @Nullable Supplier<Boolean> overrideRenderHighlightedSprite;
	@Unique
	private float sabercraft$hover;
	@Unique
	private long sabercraft$hoverAt;

	protected AbstractButtonMixin(int x, int y, int width, int height, Component message) {
		super(x, y, width, height, message);
	}

	@Inject(method = "extractDefaultSprite", at = @At("HEAD"), cancellable = true)
	private void sabercraft$titleButton(GuiGraphicsExtractor graphics, CallbackInfo info) {
		if (!TitleStyle.styledButtons()) {
			return;
		}
		boolean hovered = this.overrideRenderHighlightedSprite != null ? this.overrideRenderHighlightedSprite.get() : this.isHoveredOrFocused();
		long now = Util.getMillis();
		float step = this.sabercraft$hoverAt == 0L ? 1.0F : Math.min(1.0F, (now - this.sabercraft$hoverAt) / 140.0F);
		this.sabercraft$hoverAt = now;
		this.sabercraft$hover += ((hovered ? 1.0F : 0.0F) - this.sabercraft$hover) * step;
		TitleStyle.button(graphics, this.getX(), this.getY(), this.getWidth(), this.getHeight(), this.active, this.sabercraft$hover, this.alpha);
		info.cancel();
	}
}
