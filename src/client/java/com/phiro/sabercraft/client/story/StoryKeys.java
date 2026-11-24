package com.phiro.sabercraft.client.story;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;

/**
 * Phase 31: one gate for the keys of the story's own screens (the films, the dialog box, the
 * close-up of the cover's screws), so that a key used there does nothing else.
 *
 * <p>A key pressed in such a screen and still held stays the screen's until it is let go:
 *
 * <ul>
 * <li>it does not repeat in the screen (holding Space neither skips a film after a dialog nor
 * runs through the lines of a box);</li>
 * <li>when the screen closes while it is held (the box's last line, an interruption, a skipped
 * or finished film), the game does not take it as held: no jump after the last line, no pause
 * menu after Escape, no hotbar slot after an answer's digit. Minecraft would otherwise read the
 * keys held anew when the mouse is caught again ({@link KeyMapping#setAll}).</li>
 * </ul>
 *
 * <p>Nothing is delayed and nothing else changes: the next fresh press works as always (a jump
 * outside a dialog, Escape for the pause menu). Keys other than the story's (walking, sneaking)
 * stay down for the game; pressed in a film or a box, only their repeats are kept from it until
 * they are let go (no inventory, dropped item or chat as the screen closes).
 */
public final class StoryKeys {
	/** The keys used in the story's screens and still held since. */
	private static final IntSet HELD = new IntOpenHashSet();
	/**
	 * Other keys pressed in a film or a dialog box and still held since (E, Q, F, T...): they do not
	 * repeat into the game once the screen is gone (the inventory, a dropped item, the chat). Unlike
	 * {@link #HELD} they stay down for the game (walking on as the film ends).
	 */
	private static final IntSet QUIET = new IntOpenHashSet();
	private static int swallowed;

	private StoryKeys() {
	}

	public static void init() {
		ClientTickEvents.START_CLIENT_TICK.register(StoryKeys::tick);
	}

	/** The keys the story's screens use: Space, Escape, Enter, Tab, the arrows and the digits. */
	static boolean storyKey(int key) {
		return switch (key) {
			case GLFW.GLFW_KEY_SPACE, GLFW.GLFW_KEY_ESCAPE, GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_TAB, GLFW.GLFW_KEY_RIGHT,
					GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_UP -> true;
			default -> key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_9;
		};
	}

	/** The keys that skip a film: only Space and Escape (no other key, no mouse button). */
	public static boolean skipsFilm(KeyEvent event) {
		return event.key() == GLFW.GLFW_KEY_SPACE || event.isEscape();
	}

	/**
	 * Every key event of the game window, before Minecraft handles it. Returns whether it is kept
	 * from the game (a story key still held since a story screen used it).
	 */
	public static boolean onKey(Minecraft client, long window, int action, KeyEvent event) {
		if (window != client.getWindow().handle()) {
			return false;
		}

		int key = event.key();

		if (action == GLFW.GLFW_RELEASE) {
			// Let go: it is free again (and Minecraft sees it go up, as always).
			HELD.remove(key);
			QUIET.remove(key);
			return false;
		}

		Screen screen = client.gui.screen();

		if (HELD.contains(key)) {
			// The screw close-up turns while its key is held (each repeat a quarter turn).
			if (screen instanceof CoverScreen && action == GLFW.GLFW_REPEAT) {
				return false;
			}

			swallowed++;
			return true;
		}

		if (QUIET.contains(key) && action == GLFW.GLFW_REPEAT) {
			// Held since a film or a box: its repeats do not reach the game (each one a click there).
			swallowed++;
			return true;
		}

		boolean film = StoryCinema.isFilmScreen(screen);
		boolean box = screen instanceof DialogScreen;

		if ((film || box) && action == GLFW.GLFW_REPEAT) {
			// A key held down never skips or goes on by itself.
			swallowed++;
			return true;
		}

		if ((film || box || screen instanceof CoverScreen) && action == GLFW.GLFW_PRESS && storyKey(key)) {
			// The press still goes to the screen; from now on the key is the screen's until let go.
			HELD.add(key);
		} else if ((film || box) && action == GLFW.GLFW_PRESS) {
			QUIET.add(key);
		}

		return false;
	}

	/** After Minecraft read the keys held anew (a screen closed): the story's keys stay up. */
	public static void afterSetAll() {
		release();
	}

	private static void tick(Minecraft client) {
		if (!QUIET.isEmpty()) {
			QUIET.removeIf(key -> !InputConstants.isKeyDown(client.getWindow(), key));
		}

		if (HELD.isEmpty()) {
			return;
		}

		// A key let go while the window was not listening (a lost release) is free again.
		HELD.removeIf(key -> !InputConstants.isKeyDown(client.getWindow(), key));
		release();
	}

	private static void release() {
		for (int key : HELD) {
			KeyMapping.set(InputConstants.Type.KEYSYM.getOrCreate(key), false);
		}
	}

	/** Leaving the world: nothing held any more. */
	static void reset() {
		HELD.clear();
		QUIET.clear();
	}

	// --- For tests -----------------------------------------------------------------------------------

	/** Whether that key is held since a story screen used it. */
	public static boolean heldForTest(int key) {
		return HELD.contains(key);
	}

	/** Phase 31: whether that other key is held since a film or a box (its repeats are kept from the game). */
	public static boolean quietForTest(int key) {
		return QUIET.contains(key);
	}

	/** How many key events the gate kept from the game since it started. */
	public static int swallowedForTest() {
		return swallowed;
	}
}
