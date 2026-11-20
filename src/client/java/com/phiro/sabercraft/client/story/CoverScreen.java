package com.phiro.sabercraft.client.story;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import com.phiro.sabercraft.network.StoryActionPayload;

/**
 * Phase 28: the side way's cover in Mission 2, close up: the player unscrews it with the
 * technician's screwdriver. The view closes in on the cover (the game goes on behind it, a
 * little darker); the cover fills the middle: a grille plate in its frame, a screw in each corner.
 * The screws come out in turn (top left, top right, bottom right, bottom left, as one undoes a
 * plate crosswise): the next one is marked; holding the mouse button on it turns it out (its slot
 * turning, a click with each turn), then it drops out. With the fourth the plate tilts off. The
 * server is told then and checks again (still shut, the screwdriver, close to it); Escape or
 * walking away stops it (the screws already out stay out while the screen is open).
 *
 * <p>Everything is drawn here from plain shapes and the game's own font: no outside pictures.
 */
public final class CoverScreen extends Screen {
	/** The screws in their order: top left, top right, bottom right, bottom left (x, y as shares of the plate). */
	static final float[][] SCREWS = {{-1.0F, -1.0F}, {1.0F, -1.0F}, {1.0F, 1.0F}, {-1.0F, 1.0F}};
	/** Ticks of holding to turn a screw out, and of the plate tilting off at the end. */
	static final int TURN_TICKS = 16;
	static final int TILT_TICKS = 14;
	/** How far (blocks) the player may move from where they stood before it stops. */
	static final double LEAVE = 1.2;

	private int done;
	private float turned;
	private boolean holding;
	private int age;
	private int tilt = -1;
	private boolean sent;
	private float lastTurned;
	private final net.minecraft.world.phys.Vec3 start;
	private static int opened;
	private static int finished;

	private CoverScreen(net.minecraft.world.phys.Vec3 start) {
		super(Component.translatable("sabercraft.story.cover.title"));
		this.start = start;
	}

	/** The server says: the cover, with the screwdriver at hand. */
	static void open(Minecraft client) {
		if (client.player == null || client.gui.screen() instanceof CoverScreen) {
			return;
		}

		opened++;
		client.gui.setScreen(new CoverScreen(client.player.position()));
	}

	@Override
	public void tick() {
		this.age++;
		Minecraft client = this.minecraft;

		if (client == null || client.player == null || !StoryClient.active() || client.player.position().distanceTo(this.start) > LEAVE) {
			this.onClose();
			return;
		}

		if (this.tilt >= 0) {
			if (++this.tilt >= TILT_TICKS && !this.sent) {
				this.sent = true;
				finished++;
				StoryClient.send(StoryActionPayload.COVER, "", 0);
				this.onClose();
			}

			return;
		}

		this.lastTurned = this.turned;

		if (this.holding && this.done < SCREWS.length) {
			this.turned += 1.0F / TURN_TICKS;

			// A click with each quarter turn.
			if ((int) (this.turned * 4.0F) != (int) (this.lastTurned * 4.0F)) {
				StoryClient.sound(client, "story.latch", 0.18F, 1.6F + 0.1F * this.done, null);
			}

			if (this.turned >= 1.0F) {
				// Out: it drops, the next one is marked.
				this.done++;
				this.turned = 0.0F;
				this.lastTurned = 0.0F;
				this.holding = false;
				StoryClient.sound(client, "story.latch", 0.35F, 1.2F, null);

				if (this.done >= SCREWS.length) {
					this.tilt = 0;
					StoryClient.sound(client, "story.latch", 0.6F, 0.7F, null);
				}
			}
		} else if (!this.holding && this.turned > 0.0F) {
			// Let go halfway: the screw stays where it was turned to (no need to start again).
			this.lastTurned = this.turned;
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == 0 && this.tilt < 0 && this.done < SCREWS.length && this.onScrew(event.x(), event.y(), this.done)) {
			this.holding = true;
		}

		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (event.button() == 0) {
			this.holding = false;
		}

		return true;
	}

	@Override
	public void mouseMoved(double x, double y) {
		// The screwdriver slipped off the screw: it stops turning.
		if (this.holding && !this.onScrew(x, y, this.done)) {
			this.holding = false;
		}
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		// Space or Enter work the marked screw too (held: each press a quarter turn).
		if (event.isConfirmation() || event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE) {
			if (this.tilt < 0 && this.done < SCREWS.length) {
				this.turned = Math.min(0.999F, this.turned + 0.25F);
				this.holding = true;
			}

			return true;
		}

		return super.keyPressed(event);
	}

	@Override
	public boolean keyReleased(KeyEvent event) {
		if (event.isConfirmation() || event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE) {
			this.holding = false;
			return true;
		}

		return super.keyReleased(event);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public boolean isInGameUi() {
		return true;
	}

	// --- Where things are ----------------------------------------------------------------------------

	/** The plate's half size (GUI pixels): as large as fits, at most 110. */
	private int half() {
		return Math.max(40, Math.min(110, Math.min(this.width, this.height - 70) / 2 - 10));
	}

	private int centreX() {
		return this.width / 2;
	}

	private int centreY() {
		return this.height / 2 + 6;
	}

	/** Where a screw is (GUI pixels). */
	private int[] screw(int i) {
		int half = this.half();
		int inset = Math.round(half * 0.82F);
		return new int[] {this.centreX() + Math.round(SCREWS[i][0] * inset), this.centreY() + Math.round(SCREWS[i][1] * inset)};
	}

	private boolean onScrew(double x, double y, int i) {
		if (i < 0 || i >= SCREWS.length) {
			return false;
		}

		int[] at = this.screw(i);
		int reach = Math.max(9, this.half() / 7);
		return Math.abs(x - at[0]) <= reach && Math.abs(y - at[1]) <= reach;
	}

	// --- Drawing -------------------------------------------------------------------------------------

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		// No blur: the crawl way stays in sight round the edges, darker towards them.
		float in = Mth.clamp((this.age + a) / 6.0F, 0.0F, 1.0F);
		int dark = Math.round(in * 0xB0);
		graphics.fill(0, 0, this.width, this.height, (dark / 2) << 24);
		graphics.fillGradient(0, 0, this.width, this.height / 3, dark << 24, 0);
		graphics.fillGradient(0, this.height * 2 / 3, this.width, this.height, 0, dark << 24);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		int half = this.half();
		int cx = this.centreX();
		int cy = this.centreY();
		float zoom = Mth.clamp((this.age + a) / 6.0F, 0.0F, 1.0F);
		zoom = zoom * zoom * (3.0F - 2.0F * zoom);
		float tilt = this.tilt < 0 ? 0.0F : Mth.clamp((this.tilt + a) / TILT_TICKS, 0.0F, 1.0F);

		graphics.pose().pushMatrix();
		graphics.pose().translate(cx, cy);
		// Coming closer as it opens; at the end the plate tilts down and away.
		float scale = 0.82F + 0.18F * zoom;
		graphics.pose().scale(scale, scale * (1.0F - 0.55F * tilt * tilt));
		graphics.pose().translate(0.0F, half * 0.9F * tilt * tilt);

		// The opening behind it (dark, the shaft going on), seen as the plate goes.
		int frame = half + Math.max(6, half / 9);
		graphics.fill(-frame, -frame, frame, frame, 0xFF2A2C30);
		graphics.fill(-half, -half, half, half, 0xFF0B0C0E);

		if (tilt < 1.0F) {
			int alpha = Math.round((1.0F - tilt) * 255.0F) << 24;
			// The plate: dark metal, a lighter rim, the grille's slats.
			graphics.fill(-half, -half, half, half, alpha | 0x4A4E55);
			graphics.fill(-half, -half, half, -half + 3, alpha | 0x6A6F78);
			graphics.fill(-half, half - 3, half, half, alpha | 0x30333A);
			int slat = Math.max(4, half / 9);

			for (int y = -half + slat * 2; y < half - slat * 2; y += slat) {
				graphics.fill(-half + slat * 2, y, half - slat * 2, y + slat / 2, alpha | 0x1A1C20);
				graphics.fill(-half + slat * 2, y + slat / 2, half - slat * 2, y + slat / 2 + 1, alpha | 0x5E636B);
			}

			// A warning stripe along the top: maintenance access (yellow and black).
			int stripe = Math.max(3, half / 14);

			for (int x = -half + 4; x < half - 4; x += stripe * 2) {
				graphics.fill(x, -half + 5, Math.min(half - 4, x + stripe), -half + 5 + stripe, alpha | 0xE0B030);
				graphics.fill(Math.min(half - 4, x + stripe), -half + 5, Math.min(half - 4, x + stripe * 2), -half + 5 + stripe, alpha | 0x202020);
			}
		}

		graphics.pose().popMatrix();

		// The screws: out (gone, a dark hole), the marked one (pulsing ring), the rest.
		for (int i = 0; i < SCREWS.length; i++) {
			if (tilt > 0.0F) {
				break;
			}

			int[] at = this.screw(i);
			int x = cx + Math.round((at[0] - cx) * (0.82F + 0.18F * zoom));
			int y = cy + Math.round((at[1] - cy) * (0.82F + 0.18F * zoom));
			int r = Math.max(4, half / 12);

			if (i < this.done) {
				disc(graphics, x, y, r - 1, 0xFF101114);
				continue;
			}

			boolean next = i == this.done;
			float turned = next ? Mth.lerp(a, this.lastTurned, this.turned) : 0.0F;
			// A turning screw comes out towards the eye: a little bigger as it turns.
			int size = r + Math.round(turned * 2.0F);

			if (next) {
				float pulse = 0.5F + 0.5F * Mth.sin((this.age + a) * 0.35F);
				ring(graphics, x, y, size + 4 + Math.round(pulse * 2.0F), Math.round(140 + 100 * pulse) << 24 | 0x60D0FF);
			}

			disc(graphics, x, y, size, 0xFFB8BCC4);
			disc(graphics, x, y, size - 2, 0xFF8C9098);
			// The slot, turned as far as it has been.
			slot(graphics, x, y, size - 1, (float) (Math.PI / 4.0 + turned * Math.PI * 2.0 * 3.0));
		}

		// The screwdriver at the mouse (a shaft and a handle; tilted while it turns).
		if (this.tilt < 0) {
			graphics.pose().pushMatrix();
			graphics.pose().translate(mouseX, mouseY);
			graphics.pose().rotate((float) (-Math.PI / 4.0 + (this.holding ? Math.sin((this.age + a) * 1.2F) * 0.25F : 0.0F)));
			graphics.fill(-1, 0, 2, 22, 0xFFC8CCD2);
			graphics.fill(-4, 22, 5, 44, 0xFFC04020);
			graphics.fill(-4, 30, 5, 32, 0xFF202020);
			graphics.fill(-4, 36, 5, 38, 0xFF202020);
			graphics.pose().popMatrix();
		}

		// What to do (and how far), above, on a box of its own (wrapped on a narrow screen, over
		// whatever of the HUD lies there, as the comlink's line); Escape, below.
		Component what = this.done >= SCREWS.length ? Component.translatable("sabercraft.story.cover.off")
				: Component.translatable("sabercraft.story.cover.screw", Component.translatable("sabercraft.story.cover.at." + this.done), this.done + 1, SCREWS.length);
		java.util.List<net.minecraft.util.FormattedCharSequence> lines = this.font.split(what, Math.max(120, Math.min(this.width - 24, 300)));
		int boxWidth = this.font.width(this.title);

		for (net.minecraft.util.FormattedCharSequence line : lines) {
			boxWidth = Math.max(boxWidth, this.font.width(line));
		}

		int boxHeight = 12 + lines.size() * 11;
		int top = Math.max(4, cy - half - 10 - boxHeight);
		graphics.fill(cx - boxWidth / 2 - 7, top - 4, cx + boxWidth / 2 + 7, top + boxHeight + 2, 0xFF3A3D44);
		graphics.fill(cx - boxWidth / 2 - 6, top - 3, cx + boxWidth / 2 + 6, top + boxHeight + 1, 0xF0141619);
		graphics.centeredText(this.font, this.title, cx, top, 0xFFE8D8A0);

		for (int i = 0; i < lines.size(); i++) {
			graphics.centeredText(this.font, lines.get(i), cx, top + 12 + i * 11, 0xFFFFFFFF);
		}

		graphics.centeredText(this.font, Component.translatable("sabercraft.story.cover.leave"), cx, Math.min(this.height - 12, cy + half + 18), 0xFFA0A0A0);
	}

	/** A filled round dot (rows of fills). */
	private static void disc(GuiGraphicsExtractor graphics, int x, int y, int r, int colour) {
		for (int dy = -r; dy <= r; dy++) {
			int dx = (int) Math.floor(Math.sqrt(r * r - dy * dy));
			graphics.fill(x - dx, y + dy, x + dx + 1, y + dy + 1, colour);
		}
	}

	/** A thin ring. */
	private static void ring(GuiGraphicsExtractor graphics, int x, int y, int r, int colour) {
		for (int i = 0; i < 48; i++) {
			double angle = i * Math.PI * 2.0 / 48.0;
			int px = x + (int) Math.round(Math.cos(angle) * r);
			int py = y + (int) Math.round(Math.sin(angle) * r);
			graphics.fill(px, py, px + 1, py + 1, colour);
		}
	}

	/** The screw's slot: a dark bar across its head at that angle. */
	private static void slot(GuiGraphicsExtractor graphics, int x, int y, int r, float angle) {
		graphics.pose().pushMatrix();
		graphics.pose().translate(x + 0.5F, y + 0.5F);
		graphics.pose().rotate(angle);
		graphics.fill(-r, -1, r, 1, 0xFF2A2C30);
		graphics.pose().popMatrix();
	}

	@Override
	public void removed() {
		super.removed();
		this.holding = false;
	}

	/** How close the view has come in (0..1) while the close-up is open (for the field of view). */
	static float closeness(float partial) {
		return Minecraft.getInstance().gui.screen() instanceof CoverScreen screen ? Mth.clamp((screen.age + partial) / 6.0F, 0.0F, 1.0F) : 0.0F;
	}

	/** For the tests: close-ups opened, finished. */
	public static int[] countsForTest() {
		return new int[] {opened, finished};
	}

	/** For the tests: the screws out in the open close-up (-1: none open). */
	public static int screwsForTest() {
		return Minecraft.getInstance().gui.screen() instanceof CoverScreen screen ? screen.done : -1;
	}

	/** For the tests: where the marked screw is on screen (GUI pixels), or null. */
	public static int @org.jspecify.annotations.Nullable [] nextScrewForTest() {
		if (Minecraft.getInstance().gui.screen() instanceof CoverScreen screen && screen.done < SCREWS.length) {
			return screen.screw(screen.done);
		}

		return null;
	}
}
