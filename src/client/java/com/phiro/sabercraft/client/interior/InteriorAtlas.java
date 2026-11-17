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
	/** crew: an officer's face (variant 0), the cap is drawn over it */
	public static final int CREW_FACE_0 = 83;
	/** crew: an officer's face (variant 1), the cap is drawn over it */
	public static final int CREW_FACE_1 = 84;
	/** crew: an officer's face (variant 2), the cap is drawn over it */
	public static final int CREW_FACE_2 = 85;
	/** crew: an officer's face (variant 3), the cap is drawn over it */
	public static final int CREW_FACE_3 = 86;
	/** crew: sides and back of an officer's head (variant 0) */
	public static final int CREW_HEAD_0 = 87;
	/** crew: sides and back of an officer's head (variant 1) */
	public static final int CREW_HEAD_1 = 88;
	/** crew: sides and back of an officer's head (variant 2) */
	public static final int CREW_HEAD_2 = 89;
	/** crew: sides and back of an officer's head (variant 3) */
	public static final int CREW_HEAD_3 = 90;
	/** crew: the top of a head, light (tinted to the hair colour) */
	public static final int CREW_HAIR_TOP = 91;
	/** crew: an officer's tunic, light (tinted to the uniform): belt, rank plaque, code cylinders */
	public static final int CREW_UNIFORM_TUNIC = 92;
	/** crew: an officer's sleeve or trouser leg, light (tinted), black boot below */
	public static final int CREW_UNIFORM_LIMB = 93;
	/** crew: an officer's cap, light (tinted), with the rank disc */
	public static final int CREW_CAP = 94;
	/** crew: the top of a trooper's helmet (white dome, a ridge) */
	public static final int CREW_TROOPER_HELMET_TOP = 95;
	/** crew: technician's black helmet, sides, back and top */
	public static final int CREW_TECH_HELMET_SIDE = 96;
	/** crew: starfighter pilot's helmet, front (big lenses, breathing tubes) */
	public static final int CREW_PILOT_HELMET = 97;
	/** crew: pilot's helmet, sides, back and top (black, a grey emblem) */
	public static final int CREW_PILOT_HELMET_SIDE = 98;
	/** crew: pilot's black flight suit (chest box with switches, hoses, harness) */
	public static final int CREW_PILOT_BODY = 99;
	/** crew: pilot's sleeve or leg (black, a harness strap, boots) */
	public static final int CREW_PILOT_LIMB = 100;
	/** crew: honour guard's red helmet, front (a narrow black visor) */
	public static final int CREW_GUARD_HELMET = 101;
	/** crew: honour guard's red robe (folds), also its hood and sleeves */
	public static final int CREW_GUARD_ROBE = 102;
	/** crew: the Falcon's smuggler: a face with a crooked smile, brown hair */
	public static final int CREW_SMUGGLER_FACE = 103;
	/** crew: the smuggler's head, sides and back (brown hair) */
	public static final int CREW_SMUGGLER_HEAD = 104;
	/** crew: the smuggler: white shirt, black vest, holster belt */
	public static final int CREW_SMUGGLER_TORSO = 105;
	/** crew: the smuggler's sleeve (white shirt, the hand below) */
	public static final int CREW_SMUGGLER_ARM = 106;
	/** crew: the smuggler's trousers (dark blue, a red stripe, boots) */
	public static final int CREW_SMUGGLER_LEGS = 107;
	/** crew: the tall furry mechanic's face (fur, deep-set eyes, a dark nose) */
	public static final int CREW_WOOKIEE_FACE = 108;
	/** crew: brown fur (shaggy streaks) */
	public static final int CREW_WOOKIEE_FUR = 109;
	/** crew: brown fur with a bandolier across it */
	public static final int CREW_WOOKIEE_TORSO = 110;
	/** crew: a rebel passenger's face (short dark hair) */
	public static final int CREW_REBEL_FACE = 111;
	/** crew: the rebel's head, sides and back */
	public static final int CREW_REBEL_HEAD = 112;
	/** crew: an orange flight suit with a white vest and harness */
	public static final int CREW_REBEL_SUIT = 113;
	/** crew: the rebel's orange sleeve or leg, a black boot or glove below */
	public static final int CREW_REBEL_LIMB = 114;
	/** story: Kestrel, the spy: a narrow face, short auburn hair swept to one side, a scar on the cheek */
	public static final int STORY_KESTREL_FACE = 115;
	/** story: Kestrel's head, sides and back (short auburn hair) */
	public static final int STORY_KESTREL_HEAD = 116;
	/** story: Kestrel's grey work suit (an old Imperial technician's, the badge torn off), tool belt */
	public static final int STORY_KESTREL_SUIT = 117;
	/** story: Kestrel's sleeve or trouser leg (grey), a dark glove or boot below */
	public static final int STORY_KESTREL_LIMB = 118;
	/** story: Lt. Mira Sen: dark hair pulled back tight, dark eyes, a calm look */
	public static final int STORY_MIRA_FACE = 119;
	/** story: Mira's head, sides and back (black hair in a knot at the back) */
	public static final int STORY_MIRA_HEAD = 120;
	/** story: Lt. Corvan Hale of the security bureau: pale, sharp, pale blond hair combed back, grey eyes */
	public static final int STORY_HALE_FACE = 121;
	/** story: Hale's head, sides and back (pale blond hair, short) */
	public static final int STORY_HALE_HEAD = 122;
	/** story: Tamsin, the prisoner: long brown hair, a bruise, tired eyes */
	public static final int STORY_TAMSIN_FACE = 123;
	/** story: Tamsin's head, sides and back (long brown hair) */
	public static final int STORY_TAMSIN_HEAD = 124;
	/** story: a prisoner's overall (faded grey-orange), a number on the chest */
	public static final int STORY_PRISONER_SUIT = 125;
	/** story: the Shadowblade's mask: dark metal, a narrow red visor slit, cheek vents */
	public static final int STORY_SHADOW_MASK = 126;
	/** story: the Shadowblade's hood, sides, back and top (black cloth, folds) */
	public static final int STORY_SHADOW_HOOD = 127;
	/** story: the Shadowblade's robe: black, layered, a belt with a red-lit buckle */
	public static final int STORY_SHADOW_ROBE = 128;
	/** story: the Shadowblade's sleeve or leg (black cloth, wrapped), armoured glove or boot below */
	public static final int STORY_SHADOW_LIMB = 129;
	/** story: Ilyra Voss (as her hologram shows her): an older face, grey hair in a braid, a calm, sad look */
	public static final int STORY_ILYRA_FACE = 130;
	/** story: Ilyra's robe in the hologram: light, a wide sash */
	public static final int STORY_ILYRA_ROBE = 131;
	/** story: portrait of M-31, the mouse droid (seen from the front, a red sensor eye) */
	public static final int STORY_M31_PORTRAIT = 132;
	/** story: portrait of R2-V7, the astromech (its dome, a big eye, copper panels) */
	public static final int STORY_R2_PORTRAIT = 133;
	/** story: Funke's mark (the player's own lines): an orange spark on dark blue */
	public static final int STORY_FUNKE_PORTRAIT = 134;
	/** story: a terminal speaking (a screen with lines of text and a cursor) */
	public static final int STORY_TERMINAL_PORTRAIT = 135;
	/** story: Kestrel's sign: a falcon scratched into a dark metal panel */
	public static final int STORY_BIRD_SIGN = 136;
	/** story: old maintenance machinery: grimy panels, gauges, a warning stripe */
	public static final int STORY_MACHINE = 137;
	/** story: a locker door (grey, vents at the top, a handle, a number plate) */
	public static final int STORY_LOCKER = 138;
	/** story: an access card, level 2 (white, a red stripe, a chip) */
	public static final int STORY_KEYCARD = 139;
	/** story: the tracker's glow (glow layer): rings of light round a bright core, a sweeping line */
	public static final int STORY_TRACKER_GLOW = 140;
	/** story: a lamp's beam (holo layer): bright at the lamp (top), fading out along it, a little dust */
	public static final int STORY_BEAM = 141;
	/** story: the marks over someone who noticed: '?' on the left half, '!' on the right (white, tinted) */
	public static final int STORY_MARKS = 142;

	private InteriorAtlas() {
	}
}
