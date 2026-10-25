package com.phiro.sabercraft.client.menu;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.InteractionHand;

import com.phiro.sabercraft.character.Outfit;
import com.phiro.sabercraft.client.blade.BladeStyles;
import com.phiro.sabercraft.client.character.CharacterConfig;
import com.phiro.sabercraft.client.character.CustomProfiles;
import com.phiro.sabercraft.client.character.OutfitClient;
import com.phiro.sabercraft.client.saber.SaberHilts;
import com.phiro.sabercraft.client.select.SaberSelection;
import com.phiro.sabercraft.saber.SaberVariant;

/**
 * The character: your own skin, a film character, an original outfit over your skin, or an outfit
 * file of your own, with a preview of your character. Built-in outfits and characters are shown to
 * other players with the mod; your own files only to you. Each outfit suggests a saber, which can
 * be taken over for the sword in hand; picking a film character does that right away, and hilt,
 * color and style can still be changed afterwards in the saber menu.
 *
 * <p>Since Phase 9 the characters are a grid of cards: each card shows the name, the blade color
 * and the fighting style, and the column on the right shows the profile of the chosen one (side
 * of the Force, hilt, color, style) under the preview.
 */
public class CharacterMenuScreen extends SaberMenuScreen {
	private static final int CARD_WIDTH = 95;
	private static final int CARD_HEIGHT = 22;
	private static final int CARD_GAP = 2;
	private static final int GRID_WIDTH = 2 * CARD_WIDTH + CARD_GAP;
	/** Which list is shown: the film characters (Phase 8) or the original outfits. Kept while the game runs. */
	private static boolean showCharacters = true;
	private final List<Choice> choices = new ArrayList<>();
	private Component status = Component.empty();
	private Button suggestButton;
	private @Nullable AbstractWidget sizeButton;

	private record Choice(String value, AbstractWidget button) {
	}

	public CharacterMenuScreen() {
		super(Component.translatable("sabercraft.menu.character.title"));
	}

	@Override
	protected Tab tab() {
		return Tab.CHARACTER;
	}

	@Override
	protected void initPanel() {
		this.choices.clear();
		OutfitClient.reloadCharacterSkins();
		CustomProfiles.reload();
		int half = GRID_WIDTH / 2 - 1;
		Button charactersTab = this.addRenderableWidget(Button.builder(Component.translatable("sabercraft.menu.character.characters"), b -> this.showList(true))
				.bounds(this.left + 10, this.top + 20, half, 20)
				.tooltip(Tooltip.create(Component.translatable("sabercraft.menu.character.characters.description")))
				.build());
		Button outfitsTab = this.addRenderableWidget(Button.builder(Component.translatable("sabercraft.menu.character.outfits"), b -> this.showList(false))
				.bounds(this.left + 12 + half, this.top + 20, half, 20)
				.tooltip(Tooltip.create(Component.translatable("sabercraft.menu.character.outfits.description")))
				.build());
		charactersTab.active = !showCharacters;
		outfitsTab.active = showCharacters;

		int slot = 0;
		this.addCard(null, "", Component.translatable("sabercraft.outfit.none"), Component.translatable("sabercraft.outfit.none.description"), slot++);

		for (Outfit outfit : showCharacters ? Outfit.CHARACTERS : Outfit.OUTFITS) {
			this.addCard(outfit, outfit.id().toString(), Component.translatable(outfit.translationKey()),
					Component.translatable(outfit.translationKey() + ".description"), slot++);
		}

		if (!showCharacters) {
			this.addFileChoice(this.cardX(slot), this.cardY(slot));
			slot++;
			this.addProfileChoice(this.cardX(slot), this.cardY(slot));
		}

		int right = this.rightX();
		int rightWidth = this.rightWidth();
		this.suggestButton = this.addRenderableWidget(Button.builder(Component.translatable("sabercraft.menu.character.take_saber"), b -> this.takeSaber())
				.bounds(right, this.top + 184, rightWidth, 20)
				.tooltip(Tooltip.create(Component.translatable("sabercraft.menu.character.take_saber.description")))
				.build());
		// Only for small characters (Yoda): drawn at their size or at the normal size.
		this.sizeButton = this.addRenderableWidget(CycleButton.onOffBuilder(CharacterConfig.get().characterSize)
				.withTooltip(v -> Tooltip.create(Component.translatable("sabercraft.option.character_size.description")))
				.create(right, this.top + 162, rightWidth, 20, Component.translatable("sabercraft.option.character_size"), (button, v) -> {
					CharacterConfig.get().characterSize = v;
					CharacterConfig.save();
				}));

		int bottom = this.top + PANEL_HEIGHT - 26;
		this.addRenderableWidget(CycleButton.onOffBuilder(CharacterConfig.get().showOtherOutfits)
				.withTooltip(v -> Tooltip.create(Component.translatable("sabercraft.option.show_other_outfits.description")))
				.create(this.left + 10, bottom, 150, 20, Component.translatable("sabercraft.option.show_other_outfits"), (button, v) -> {
					CharacterConfig.get().showOtherOutfits = v;
					CharacterConfig.save();
				}));
		// The folder for your own files: character skins or outfit files, matching the list.
		this.addRenderableWidget(Button.builder(Component.translatable("sabercraft.menu.character.folder"),
						b -> Util.getPlatform().openPath(showCharacters ? OutfitClient.characterFolder() : OutfitClient.folder()))
				.bounds(this.left + 166, bottom, 76, 20)
				.tooltip(Tooltip.create(Component.translatable(showCharacters ? "sabercraft.menu.character.folder.characters" : "sabercraft.outfit.file.description")))
				.build());
		this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> this.onClose())
				.bounds(this.left + 246, bottom, 74, 20)
				.build());
	}

	private int cardX(int slot) {
		return this.left + 10 + slot % 2 * (CARD_WIDTH + CARD_GAP);
	}

	private int cardY(int slot) {
		return this.top + 44 + slot / 2 * (CARD_HEIGHT + CARD_GAP);
	}

	private int rightX() {
		return this.left + 10 + GRID_WIDTH + 8;
	}

	private int rightWidth() {
		return this.left + PANEL_WIDTH - 10 - this.rightX();
	}

	private void showList(boolean characters) {
		showCharacters = characters;
		this.status = Component.empty();
		this.rebuildWidgets();
	}

	/** Files of your own: one button that steps through them. */
	private void addFileChoice(int x, int y) {
		List<String> files = OutfitClient.ownFiles();

		if (files.isEmpty()) {
			Button none = this.addRenderableWidget(Button.builder(Component.translatable("sabercraft.outfit.file.none"), b -> {})
					.bounds(x, y, CARD_WIDTH, CARD_HEIGHT)
					.tooltip(Tooltip.create(Component.translatable("sabercraft.outfit.file.description")))
					.build());
			none.active = false;
		} else {
			String current = CharacterConfig.get().outfit;
			String start = current.startsWith(OutfitClient.FILE_PREFIX) ? current.substring(OutfitClient.FILE_PREFIX.length()) : files.getFirst();
			CycleButton<String> fileButton = this.addRenderableWidget(CycleButton.builder((String name) -> Component.literal(name), files.contains(start) ? start : files.getFirst())
					.withValues(files)
					.withTooltip(v -> Tooltip.create(Component.translatable("sabercraft.outfit.file.description")))
					.displayOnlyValue()
					.create(x, y, CARD_WIDTH, CARD_HEIGHT, Component.translatable("sabercraft.outfit.file"),
							(button, name) -> OutfitClient.choose(OutfitClient.FILE_PREFIX + name)));
			this.choices.add(new Choice(OutfitClient.FILE_PREFIX, fileButton));
		}
	}

	/**
	 * Character profiles of your own (Phase 10, {@code config/sabercraft/profiles/*.json}): one
	 * button that steps through them. Choosing one also brings its saber along, like a character.
	 */
	private void addProfileChoice(int x, int y) {
		List<CustomProfiles.Profile> profiles = CustomProfiles.all();

		if (profiles.isEmpty()) {
			Button none = this.addRenderableWidget(Button.builder(Component.translatable("sabercraft.profile.none"), b -> {})
					.bounds(x, y, CARD_WIDTH, CARD_HEIGHT)
					.tooltip(Tooltip.create(Component.translatable("sabercraft.profile.description")))
					.build());
			none.active = false;
			return;
		}

		String current = CharacterConfig.get().outfit;
		CustomProfiles.Profile start = OutfitClient.profile(current).orElse(profiles.getFirst());
		CycleButton<CustomProfiles.Profile> button = this.addRenderableWidget(
				CycleButton.builder((CustomProfiles.Profile profile) -> Component.literal(profile.name()), start)
						.withValues(profiles)
						.withTooltip(v -> Tooltip.create(Component.translatable("sabercraft.profile.description")))
						.displayOnlyValue()
						.create(x, y, CARD_WIDTH, CARD_HEIGHT, Component.translatable("sabercraft.profile"), (b, profile) -> this.chooseProfile(profile)));
		this.choices.add(new Choice(OutfitClient.PROFILE_PREFIX, button));
	}

	/** Chooses a profile of your own, for the button and for tests. */
	public void chooseProfile(CustomProfiles.Profile profile) {
		OutfitClient.choose(OutfitClient.PROFILE_PREFIX + profile.key());
		this.status = Component.empty();

		if (profile.saber() != null && this.minecraft.player != null && SaberSelection.saberHand(this.minecraft.player) != null) {
			this.takeSaber();
		}
	}

	private void addCard(@Nullable Outfit outfit, String value, Component name, Component description, int slot) {
		Button button = this.addRenderableWidget(new Card(this.cardX(slot), this.cardY(slot), name, outfit, b -> {
			OutfitClient.choose(value);
			this.status = Component.empty();

			// A film character brings its saber along, when one is in hand.
			if (this.chosen().filter(Outfit::character).isPresent() && this.minecraft.player != null
					&& SaberSelection.saberHand(this.minecraft.player) != null) {
				this.takeSaber();
			}
		}));
		button.setTooltip(Tooltip.create(description));
		this.choices.add(new Choice(value, button));
	}

	private Optional<Outfit> chosen() {
		return OutfitClient.builtIn(CharacterConfig.get().outfit);
	}

	/** The saber of the chosen character, outfit or profile of your own. */
	private Optional<SaberVariant> chosenSaber() {
		String current = CharacterConfig.get().outfit;
		return this.chosen().map(Outfit::suggested).or(() -> OutfitClient.profile(current).map(CustomProfiles.Profile::saber));
	}

	private void takeSaber() {
		Optional<SaberVariant> saber = this.chosenSaber();
		InteractionHand hand = this.minecraft.player == null ? null : SaberSelection.saberHand(this.minecraft.player);

		if (saber.isEmpty() || hand == null) {
			this.status = Component.translatable("sabercraft.customize.no_saber");
			return;
		}

		this.status = switch (SaberSelection.apply(this.minecraft, hand, saber.get())) {
			case SENT, CREATIVE -> Component.translatable("sabercraft.menu.character.saber_taken");
			case UNSUPPORTED -> Component.translatable("sabercraft.customize.unsupported");
			case NO_SABER -> Component.translatable("sabercraft.customize.no_saber");
		};
	}

	@Override
	public void tick() {
		Optional<Outfit> outfit = this.chosen();
		this.suggestButton.active = this.chosenSaber().isPresent() && this.minecraft.player != null && SaberSelection.saberHand(this.minecraft.player) != null;

		if (this.sizeButton != null) {
			this.sizeButton.visible = outfit.isPresent() && outfit.get().scale() != 1.0F;
		}
	}

	@Override
	protected void extractPanel(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		String current = CharacterConfig.get().outfit;

		for (Choice choice : this.choices) {
			boolean selected = choice.value().equals(OutfitClient.FILE_PREFIX) || choice.value().equals(OutfitClient.PROFILE_PREFIX)
					? current.startsWith(choice.value()) : choice.value().equals(current);

			if (selected) {
				AbstractWidget button = choice.button();
				graphics.outline(button.getX() - 1, button.getY() - 1, button.getWidth() + 2, button.getHeight() + 2, 0xFFFFFFFF);
			}
		}

		// Your character, turning towards the mouse like in the inventory.
		int right = this.rightX();
		int rightWidth = this.rightWidth();
		int previewTop = this.top + 20;
		int previewBottom = this.top + 104;
		graphics.fill(right, previewTop, right + rightWidth, previewBottom, 0x60000000);

		if (this.minecraft.player != null) {
			InventoryScreen.extractEntityInInventoryFollowsMouse(graphics, right, previewTop, right + rightWidth, previewBottom, 36, 0.0625F,
					mouseX, mouseY, this.minecraft.player);
		}

		// The profile of the chosen character or outfit.
		Optional<Outfit> outfit = this.chosen();
		Optional<CustomProfiles.Profile> profile = OutfitClient.profile(current);
		Component name = outfit.<Component>map(o -> Component.translatable(o.translationKey()))
				.or(() -> profile.map(p -> Component.literal(p.name())))
				.orElse(current.startsWith(OutfitClient.FILE_PREFIX) ? Component.literal(current.substring(OutfitClient.FILE_PREFIX.length()))
						: Component.translatable("sabercraft.outfit.none"));
		graphics.centeredText(this.font, name, right + rightWidth / 2, previewBottom + 4, 0xFFFFFFFF);
		int y = previewBottom + 16;

		if (outfit.isPresent()) {
			Outfit chosen = outfit.get();
			SaberVariant saber = chosen.suggested();

			if (chosen.side() != Outfit.Side.NONE) {
				graphics.text(this.font, Component.translatable(chosen.side().translationKey()), right, y, sideColor(chosen.side()));
				y += 11;
			}

			graphics.text(this.font, Component.translatable("sabercraft.menu.character.hilt", SaberHilts.get(saber.hilt()).displayName()), right, y, 0xFFB0B0B8);
			y += 11;
			graphics.fill(right, y + 1, right + 7, y + 8, 0xFF000000 | BladeStyles.get(saber.color()).glowColor());
			graphics.text(this.font, Component.translatable("sabercraft.color." + saber.color().getPath()), right + 10, y, 0xFFB0B0B8);
			y += 11;

			if (chosen.scale() == 1.0F) {
				graphics.text(this.font, Component.translatable("sabercraft.menu.character.style",
						Component.translatable("sabercraft.style." + saber.style().getPath())), right, y, 0xFFB0B0B8);
			}

			if (chosen.character() && OutfitClient.hasOwnSkin(chosen)) {
				graphics.textWithWordWrap(this.font, Component.translatable("sabercraft.menu.character.own_skin", chosen.id().getPath() + ".png"),
						this.left + 10, this.top + 189, this.rightX() - this.left - 16, 0xFF80D080);
			}
		} else if (profile.isPresent()) {
			// A profile of your own (Phase 10): its side and saber, like a character.
			CustomProfiles.Profile chosen = profile.get();

			if (chosen.side() != Outfit.Side.NONE) {
				graphics.text(this.font, Component.translatable(chosen.side().translationKey()), right, y, sideColor(chosen.side()));
				y += 11;
			}

			if (chosen.saber() != null) {
				SaberVariant saber = chosen.saber();
				graphics.text(this.font, Component.translatable("sabercraft.menu.character.hilt", SaberHilts.get(saber.hilt()).displayName()), right, y, 0xFFB0B0B8);
				y += 11;
				graphics.fill(right, y + 1, right + 7, y + 8, 0xFF000000 | BladeStyles.get(saber.color()).glowColor());
				graphics.text(this.font, Component.translatable("sabercraft.color." + saber.color().getPath()), right + 10, y, 0xFFB0B0B8);
				y += 11;
				graphics.text(this.font, Component.translatable("sabercraft.menu.character.style",
						Component.translatable("sabercraft.style." + saber.style().getPath())), right, y, 0xFFB0B0B8);
			} else {
				graphics.textWithWordWrap(this.font, Component.translatable("sabercraft.profile.no_saber"), right, y, rightWidth, 0xFFB0B0B8);
			}
		} else {
			graphics.textWithWordWrap(this.font, Component.translatable(current.startsWith(OutfitClient.FILE_PREFIX)
					? "sabercraft.outfit.file.description" : "sabercraft.outfit.none.description"), right, y, rightWidth, 0xFFB0B0B8);
		}

		if (!this.status.getString().isEmpty()) {
			// Under the cards only, so it never runs into the buttons of the right column.
			graphics.textWithWordWrap(this.font, this.status, this.left + 10, this.top + 189, this.rightX() - this.left - 16, 0xFFFFD060);
		}
	}

	private static int sideColor(Outfit.Side side) {
		return switch (side) {
			case LIGHT -> 0xFF80B8FF;
			case DARK -> 0xFFFF6A5A;
			case GREY -> 0xFFD8D8D8;
			case NONE -> 0xFFB0B0B8;
		};
	}

	/**
	 * One card of the grid: the name, and below it the blade color as a small swatch with the
	 * fighting style. The name stays the button's message (narration, tests).
	 */
	private static final class Card extends Button {
		private final @Nullable Outfit outfit;

		private Card(int x, int y, Component name, @Nullable Outfit outfit, OnPress onPress) {
			super(x, y, CARD_WIDTH, CARD_HEIGHT, name, onPress, DEFAULT_NARRATION);
			this.outfit = outfit;
		}

		@Override
		protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
			this.extractDefaultSprite(graphics);
			Font font = Minecraft.getInstance().font;
			int x = this.getX() + 4;
			String name = font.plainSubstrByWidth(this.getMessage().getString(), this.getWidth() - 8);

			if (this.outfit == null) {
				graphics.text(font, name, x, this.getY() + (this.getHeight() - 8) / 2, 0xFFFFFFFF);
				return;
			}

			graphics.text(font, name, x, this.getY() + 2, 0xFFFFFFFF);
			SaberVariant saber = this.outfit.suggested();
			int swatch = 0xFF000000 | BladeStyles.get(saber.color()).glowColor();
			graphics.fill(x, this.getY() + 13, x + 6, this.getY() + 19, swatch);
			String style = font.plainSubstrByWidth(Component.translatable("sabercraft.style." + saber.style().getPath()).getString(), this.getWidth() - 18);
			graphics.text(font, style, x + 9, this.getY() + 12, 0xFFB8B8C0);
		}
	}
}
