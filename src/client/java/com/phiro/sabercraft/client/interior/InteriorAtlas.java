package com.phiro.sabercraft.client.interior;

/**
 * The tiles of the interior atlas ({@code textures/interior/atlas.png}), written by
 * {@code tools/gen_interior_textures.py}: do not edit by hand. A tile is {@value #TILE} pixels in a
 * grid of {@value #GRID} x {@value #GRID}.
 */
public final class InteriorAtlas {
	public static final int TILE = 64;
	public static final int GRID = 16;

	/** Star Destroyer plating: big plates */
	public static final int IMP_HULL_A = 0;
	/** Star Destroyer plating: narrow strips and hatches */
	public static final int IMP_HULL_B = 1;
	/** Star Destroyer plating: greebles (pipes, boxes, vents) */
	public static final int IMP_HULL_C = 2;
	/** Star Destroyer trench: dense dark machinery */
	public static final int IMP_HULL_TRENCH = 3;
	/** Star Destroyer: shadowed recesses */
	public static final int IMP_HULL_DARK = 4;
	/** Star Destroyer: hull with rows of small lit windows (base) */
	public static final int IMP_HULL_WINDOWS = 5;
	/** glow: the lit windows */
	public static final int IMP_HULL_WINDOWS_GLOW = 6;
	/** glow: the engine nozzles (bright core, blue rim) */
	public static final int IMP_ENGINE_GLOW = 7;
	/** Star Destroyer: inside of an engine nozzle */
	public static final int IMP_ENGINE_BELL = 8;
	/** Star Destroyer: shield generator dome */
	public static final int IMP_DOME = 9;
	/** Imperial wall: framed dark panels, one block a tile */
	public static final int IMP_WALL = 10;
	/** Imperial wall: a vent grille */
	public static final int IMP_WALL_VENT = 11;
	/** Imperial wall with a light strip (base) */
	public static final int IMP_WALL_STRIP = 12;
	/** glow: the light strip */
	public static final int IMP_WALL_STRIP_GLOW = 13;
	/** Imperial wall: tall ribbed panel (upper wall) */
	public static final int IMP_WALL_TALL = 14;
	/** Imperial floor: polished black tiles */
	public static final int IMP_FLOOR = 15;
	/** Imperial floor: grating */
	public static final int IMP_FLOOR_GRATE = 16;
	/** Imperial floor: the walkway with guide lines */
	public static final int IMP_FLOOR_WALKWAY = 17;
	/** Imperial ceiling: coffers */
	public static final int IMP_CEILING = 18;
	/** Imperial ceiling: a light panel (base) */
	public static final int IMP_CEILING_LIGHT = 19;
	/** glow: the ceiling light */
	public static final int IMP_CEILING_LIGHT_GLOW = 20;
	/** Imperial dark metal for frames, rails and pillars */
	public static final int IMP_METAL = 21;
	/** Imperial light grey metal */
	public static final int IMP_METAL_LIGHT = 22;
	/** Imperial console face: buttons, switches, small screens (base) */
	public static final int IMP_CONSOLE = 23;
	/** glow: console lights and screens */
	public static final int IMP_CONSOLE_GLOW = 24;
	/** Imperial console top: a slanted desk with a keypad */
	public static final int IMP_CONSOLE_TOP = 25;
	/** glow: the desk screen */
	public static final int IMP_CONSOLE_TOP_GLOW = 26;
	/** Imperial wall screen frame (base) */
	public static final int IMP_SCREEN = 27;
	/** glow: a radar screen */
	public static final int IMP_SCREEN_RADAR_GLOW = 28;
	/** glow: a tactical display (ship outlines, grid) */
	public static final int IMP_SCREEN_TACTICAL_GLOW = 29;
	/** glow: lines of readouts */
	public static final int IMP_SCREEN_TEXT_GLOW = 30;
	/** a hologram: blue scan lines (translucent) */
	public static final int IMP_HOLO = 31;
	/** a window pane: faint reflections (translucent) */
	public static final int IMP_GLASS = 32;
	/** hazard stripes */
	public static final int IMP_HAZARD = 33;
	/** Imperial door: two halves with a seam */
	public static final int IMP_DOOR = 34;
	/** Imperial pipe: banded metal, along u */
	public static final int IMP_PIPE = 35;
	/** glow: the reactor core, swirling white-blue */
	public static final int IMP_REACTOR_GLOW = 36;
	/** glow: a hangar force field */
	public static final int IMP_FORCE_FIELD_GLOW = 37;
	/** near black (shadow gaps, backs) */
	public static final int IMP_BLACK = 38;
	/** Imperial seat: black padding */
	public static final int IMP_CHAIR = 39;
	/** Imperial crate: grey with markings */
	public static final int IMP_CRATE = 40;
	/** TIE fighter wing panel: black with a fine grid */
	public static final int IMP_TIE_PANEL = 41;
	/** freighter hull plating: worn off-white plates */
	public static final int FAL_HULL_A = 42;
	/** freighter hull: plates with a red-brown repair patch */
	public static final int FAL_HULL_B = 43;
	/** freighter hull: exposed machinery and pipes */
	public static final int FAL_HULL_GREEBLE = 44;
	/** freighter hull: radial vent slots */
	public static final int FAL_HULL_VENTS = 45;
	/** glow: the sublight engine band (blue-white) */
	public static final int FAL_ENGINE_GLOW = 46;
	/** freighter cockpit glass: dark with a sheen (translucent) */
	public static final int FAL_COCKPIT_GLASS = 47;
	/** radar dish: light grey with ribs */
	public static final int FAL_DISH = 48;
	/** freighter wall padding: quilted off-white cushions */
	public static final int FAL_WALL_PAD = 49;
	/** freighter wall: grey structural rib with bolts */
	public static final int FAL_WALL_RIB = 50;
	/** freighter wall: panels, conduits and a junction box */
	public static final int FAL_WALL_TECH = 51;
	/** freighter floor: metal grating over dark gaps */
	public static final int FAL_FLOOR = 52;
	/** freighter floor: worn deck plate */
	public static final int FAL_FLOOR_PLATE = 53;
	/** freighter ceiling: ribs and conduits */
	public static final int FAL_CEILING = 54;
	/** freighter ceiling light panel (base) */
	public static final int FAL_CEILING_LIGHT = 55;
	/** glow: the warm ceiling light */
	public static final int FAL_CEILING_LIGHT_GLOW = 56;
	/** freighter metal: grey, scuffed */
	public static final int FAL_METAL = 57;
	/** freighter dark metal */
	public static final int FAL_METAL_DARK = 58;
	/** cockpit panel: rows of switches and gauges (base) */
	public static final int FAL_CONSOLE = 59;
	/** glow: indicator lamps and a green scope */
	public static final int FAL_CONSOLE_GLOW = 60;
	/** glow: a targeting/readout screen (amber) */
	public static final int FAL_SCREEN_GLOW = 61;
	/** lounge couch: worn leather-like cushions */
	public static final int FAL_COUCH = 62;
	/** the round game table top: dark with a ring pattern */
	public static final int FAL_TABLE = 63;
	/** glow: green-blue hologram light */
	public static final int FAL_HOLO_GLOW = 64;
	/** freighter pipe: copper-brown banded */
	public static final int FAL_PIPE = 65;
	/** freighter pipe: grey banded */
	public static final int FAL_PIPE_GREY = 66;
	/** freighter machinery block: panels, gauges, warning marks */
	public static final int FAL_MACHINE = 67;
	/** glow: the hyperdrive core, pulsing blue */
	public static final int FAL_HYPERDRIVE_GLOW = 68;
	/** freighter cargo crate: olive, stencilled */
	public static final int FAL_CRATE = 69;
	/** floor hatch: round-cornered plate with handles */
	public static final int FAL_HATCH = 70;
	/** crew bunk mattress: grey-blue fabric */
	public static final int FAL_BUNK = 71;
	/** plain white (tinted by vertex colour) */
	public static final int WHITE = 72;
	/** crew: trooper helmet, front (eyes, breathing grille) */
	public static final int CREW_TROOPER_HELMET = 73;
	/** crew: trooper helmet, sides and back */
	public static final int CREW_TROOPER_HELMET_SIDE = 74;
	/** crew: trooper chest and back (plates, black under-suit, belt) */
	public static final int CREW_TROOPER_TORSO = 75;
	/** crew: trooper arm or leg (white plates, black joints) */
	public static final int CREW_TROOPER_LIMB = 76;
	/** crew: officer's face under the cap */
	public static final int CREW_OFFICER_FACE = 77;
	/** crew: officer's head, sides and back (cap, short hair) */
	public static final int CREW_OFFICER_HEAD = 78;
	/** crew: officer's tunic (grey, belt, rank plaque) */
	public static final int CREW_OFFICER_TUNIC = 79;
	/** crew: officer's sleeve or trouser leg (black boot below) */
	public static final int CREW_OFFICER_LIMB = 80;
	/** crew: technician's black helmet, front (visor) */
	public static final int CREW_TECH_HELMET = 81;
	/** crew: technician's black suit (chest box, belt) */
	public static final int CREW_TECH_BODY = 82;

	private InteriorAtlas() {
	}
}
