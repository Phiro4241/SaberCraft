package com.phiro.sabercraft.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.LogoRenderer;
import net.minecraft.client.gui.components.SplashRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

import com.phiro.sabercraft.client.menu.TitleStyle;

/**
 * Phase 24: SaberCraft's touches on the title screen: a SaberCraft saying instead of the yellow
 * splash text, a "SaberCraft" line with two blades under the Minecraft logo (the logo itself stays)
 * and the mod's version above Minecraft's. The background and the buttons come through
 * {@link ScreenMixin} and {@link AbstractButtonMixin}. All of it only while {@code title_screen}
 * is on; the vanilla calls run as always.
 */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
	@Unique
	private int sabercraft$saying = -1;

	protected TitleScreenMixin(Component title) {
		super(title);
	}

	@Redirect(method = "extractRenderState", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/components/SplashRenderer;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;ILnet/minecraft/client/gui/Font;F)V"))
	private void sabercraft$saying(SplashRenderer splash, GuiGraphicsExtractor graphics, int screenWidth, Font font, float alpha) {
		if (!TitleStyle.enabled()) {
			splash.extractRenderState(graphics, screenWidth, font, alpha);
			return;
		}
		if (this.sabercraft$saying < 0) {
			this.sabercraft$saying = TitleStyle.pickSaying();
		}
		TitleStyle.splash(graphics, screenWidth, font, alpha, this.sabercraft$saying);
	}

	@Redirect(method = "extractRenderState", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/components/LogoRenderer;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IF)V"))
	private void sabercraft$subtitle(LogoRenderer logo, GuiGraphicsExtractor graphics, int width, float alpha) {
		logo.extractRenderState(graphics, width, alpha);
		if (TitleStyle.enabled()) {
			TitleStyle.subtitle(graphics, this.font, width, alpha);
		}
	}

	@Redirect(method = "extractRenderState", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V"))
	private void sabercraft$version(GuiGraphicsExtractor graphics, Font font, String text, int x, int y, int colour) {
		graphics.text(font, text, x, y, colour);
		if (TitleStyle.enabled()) {
			TitleStyle.version(graphics, font, this.height, ((colour >>> 24) & 0xFF) / 255.0F);
		}
	}
}
