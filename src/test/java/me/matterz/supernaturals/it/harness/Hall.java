package me.matterz.supernaturals.it.harness;

import me.matterz.supernaturals.io.SNConfigHandler;

import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.Sign;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.Door;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The test hall: a lobby that offers the races, and one hall per race.
 *
 * <p>A race hall is a <b>row of sections along the corridor</b>, not a room with things
 * scattered in it. Every step of a race's own flow is one section: its number and its teaching
 * signs at the section's head, and its own chest, switch, target or arena right behind them. So a
 * section can be added, changed or removed on its own, and a player can walk straight to the one
 * thing they want to test instead of reading every sign in the room to find it. Two steps belong
 * to every race and are therefore appended to every hall by the same code: a stand-in to hit, and
 * a switch that hands out power.
 *
 * <p><b>Four worlds.</b> The vampire and the werewolf are the two races whose tests read the
 * clock, and the ghoul's is the one that reads the weather, so those three get a world each: the
 * plugin asks the world a player is standing in, and moving the sun in the shared overworld so
 * that one player can watch a vampire burn would move it for everybody. Everything else is in
 * the overworld.
 *
 * <p><b>Sealed by rule, not by material.</b> {@link HallGuard} refuses breaks, places, buckets,
 * flows, fire and explosions anywhere inside, puts its own furniture back if anything moves it
 * anyway, and keeps whatever a section let out from wandering off. None of it is bedrock,
 * because none of it has to be.
 */
public final class Hall {

    /** What pressing a switch does. */
    public enum Kind {

        /** From the pad below, back up into the lobby. */
        ENTER_LOBBY,

        /** Out of the hall, to the pad in the world underneath. */
        LEAVE_HALL,

        /** Into one race's hall. The trigger names which. */
        ENTER_AREA,

        /** Puts a player's race down without moving them. */
        RESET_HUMAN,

        /** Clears the props, restores humanity and goes back to the lobby. */
        EXIT_TEST,

        /** Hands the player power, so a section can be reached without grinding for it. */
        POWER,

        /**
         * Turns the operator's infinite power off and on again.
         *
         * <p>Every operator holds the permission that refuses every power loss, so a section about
         * losing power cannot be run by the person most likely to be running it. This is the switch
         * that puts the cost back.
         */
        INFINITE_POWER,

        /** Moves one hall's own world to midday. */
        DAY,

        /** Moves one hall's own world to midnight. */
        NIGHT,

        /** The ghoul's way in: a zombie the hall keeps, whose kill always turns the corpse. */
        PREPARE_GHOUL,

        /** The werewolf's way in: an angry wolf that only comes out at night. */
        PREPARE_WEREWOLF,

        /** Asks the witch hunter hall for an invitation. */
        INVITE_HUNTER,

        /** Carries the player up to the angel's ledge. */
        CLIMB,

        /** Walks the demon into the nether's lava. */
        DIP,

        /** Lets out one chicken that does not move, replacing any older test chicken. */
        SPAWN_CHICKEN,

        /** Calls out every monster this race is supposed to be at peace with. */
        SPAWN_MONSTERS,

        /** Takes every monster the hall let out back again. */
        CLEAR_MONSTERS,

        /** Puts a vampire's saved teleport point back where the hall wants it. */
        RESET_TELEPORT,

        /** Empties the player's hunger, so the next kill has something to restore. */
        CLEAR_HUNGER,

        /** Turns one hall's own weather on and off. */
        TOGGLE_RAIN,

        /** Makes one hall's columns read as a cold biome, for the demon's only way back. */
        COLD_BIOME,

        /** Walks the demon into the water it has to drown itself in. */
        DIP_WATER,

        /** Lets the hall swing at the player with the weapon a section is about. */
        TEST_HIT,

        /** Casts one of a priest's spells at the stand-in. The trigger names the item. */
        TEST_SPELL,

        /**
         * Uses the player's own item on a stand-in, with the damage applied by the server.
         *
         * <p>The detail is {@code MATERIAL[:DAMAGE]}, both optional. An event raised by hand is
         * read by the plugin and hits nobody - the rule looks tested while nothing loses health -
         * so a section about a skill that has to land on somebody swings for real.
         */
        USE_ON_NPC,

        /**
         * Puts out a stand-in who gets up again where he fell.
         *
         * <p>Several races turn the person they kill into one of their own, and a target that
         * stays dead can only be killed once. The detail is {@code RACE[:MATERIAL]}.
         */
        SPAWN_TARGET,

        /**
         * Sends one stand-in at another, for the things that only show when somebody else takes
         * the blow - the ghoul's bond splits damage with the person it is bound to.
         */
        NPC_HITS_NPC,

        /** Brings the stand-in over to the player, so a section about hitting it has a target. */
        SPAR,

        /** Takes a bite out of the player's health, so a section about healing has a wound. */
        HURT,

        /**
         * Ends the player's life outright, so a step about what dying does can be run as often as
         * it needs to be. A fake client cannot fall on its own sword.
         */
        SELF_KILL,

        /** Calls up one stand-in player per race named in the trigger, as test prey. */
        SPAWN_NPCS,

        /** Puts the player back at the near end of the hall they are already in. */
        RETURN_START,

        /** Carries the demon into the nether, where its conversion has to happen. */
        GO_NETHER,

        /** Brings the demon back to the overworld hall it came from. */
        RETURN_OVERWORLD,

        /** Stands the player on the section's open-air mark, where the sky can be read. */
        SUN_SPOT,

        /**
         * Stands the player under a roof the section built, for the other half of a sky rule.
         *
         * <p>Several rules read the sky and then answer differently: the ghoul regenerates under
         * cover and not in the rain, and the vampire burns in the sun unless something is over its
         * head. Without a mark of its own the covered case is "walk back into the previous
         * section", which is far enough that a reader who does not go cannot tell a rule that
         * answered from a rule that never ran.
         */
        ROOF_SPOT,

        /** Puts the player back to full health, so a step about damage starts from the top. */
        RESTORE_HEALTH,

        /** Swings what the player is holding at nothing, the way a bow mode is cycled. */
        LEFT_CLICK,

        /** Reads the hunter's bounty list back, and refreshes one. */
        BOUNTY,

        /** Drops the player from high above the hall's own roof, so falling can be felt. */
        FALL_TEST,

        /** Puts the player back on the floor, after a fall they may have survived. */
        BACK_DOWN,

        /** Sets the player alight, which several races are supposed to shrug off. */
        SET_FIRE,

        /** Lowers the player into the hall's own lava, where a demon is healed, not hurt. */
        DIP_LAVA,

        /** Reads the player's power back, for a step about power going up or down on its own. */
        POWER_READ,

        /** Lets out one mob of the type named in the trigger, for a step that needs a monster. */
        SPAWN_MOB,

        // -- the room grid --------------------------------------------------

        /**
         * Walks the player one room on, along the order the rooms were laid out in.
         *
         * <p>Relative, not "room seven": rooms are edited and inserted, and a switch that names
         * the room after it keeps working when the numbering changes.
         */
        ROOM_NEXT,

        /** Walks the player one room back, the way {@link #ROOM_NEXT} walks them on. */
        ROOM_PREV,

        /**
         * Puts the player in a named room of a named hall. The detail is {@code RACE:INDEX}.
         *
         * <p>Every hall's restore room is reachable from every other hall's, so a player who has
         * been turned into something else can go to the room that un-turns it.
         */
        GO_ROOM,

        /**
         * Leaves the whole test: back to the lobby, restored to a human, props taken back. Every
         * room has one, in the same corner, on the same block of red wool.
         */
        EXIT_ROOM,

        /**
         * Stands the player under one of the four roofs a sun step is about.
         *
         * <p>The detail is {@code open|glass|leaves|solid}. The plugin reads the sky light over
         * the player's eyes, and which of these answers "in the sun" is exactly the thing that
         * step exists to show - so the four of them are one click apart and each has the measured
         * answer on its own board.
         */
        SUN_MAT,

        // -- being changed by somebody else ---------------------------------

        /**
         * Sends a stand-in of the named race at the player to kill them.
         *
         * <p>The detail is {@code RACE[:MATERIAL]}. The material is not decoration: a race that
         * cannot use a weapon deals no damage with one, so a stand-in whose race bans swords has
         * to come empty-handed or its kill never lands and the rule it is demonstrating never
         * fires. {@code HallGuard} checks the material against that race's own ban list.
         */
        NPC_KILL_ME,

        /** Makes a stand-in of the named race cast one of its spells at the player. */
        NPC_CAST_ME,

        /** Hands the player the item a conversion needs them to be holding, then waits. */
        ARM_ME,

        /** Lets the vampire's infection altar, the enderborn's clay or the church do the work. */
        SELF_CONVERT,

        /**
         * Turns the chance of being turned into a certainty, for the room that teaches it.
         *
         * <p>Half these races turn the person they kill on a roll of thirty-five in a hundred, and
         * a room whose whole subject is "this is what being turned feels like" cannot leave a
         * player killing the same stand-in twenty times and still being human. The switch writes
         * the plugin's own chance setting - the roll is still the plugin's, only the odds move -
         * and it is put back the moment the player leaves the room, so no other step sees it.
         */
        SPREAD,

        /**
         * Gives a stand-in one race, to see what a rule answers for a race other than the
         * player's - a ghoul cannot bind a human, and an angel is one.
         */
        SPAWN_ALIGN,

        /** Drops the player down the tower a race is entered by, which has to kill them. */
        DROP_TOWER
    }

    /** A switch and, when it belongs to one race, whose. */
    public record Trigger(Kind kind, String race, String detail) {

        static Trigger of(Kind kind) {
            return new Trigger(kind, null, null);
        }

        static Trigger of(Kind kind, String race) {
            return new Trigger(kind, race, null);
        }

        static Trigger with(Kind kind, String race, String detail) {
            return new Trigger(kind, race, detail);
        }

        static Trigger to(String race) {
            return new Trigger(Kind.ENTER_AREA, race, null);
        }
    }

    /** One step of a race's flow, built into its own slot of that race's hall. */
    private interface Station {

        void build(World target, int y, int x, int z, int width);
    }

    /**
     * A step of a race's flow, built into a room of its own.
     *
     * <p>{@code cells} is how many cells of the grid the step takes: one for everything, four for
     * the steps that need a space no single room can hold. {@code headroom} overrides the hall's
     * own height for the steps that need more of it than the rest - a fall that has to kill, a
     * jump that has to clear seven blocks, a room with a dragon in it. Zero means "whatever the
     * hall and the cell count say", which is what nearly every step wants.
     */
    /**
     * A step: what it is called, the short boards it hangs on its wall, the long text its guide
     * tells, how many slots of the corridor it takes, whether its ceiling is left open (the ghoul's
     * rain step needs the sky), and what to build in it.
     *
     * <p>Two texts rather than one, because a wall and a person are not the same medium. A board is
     * read at arm's length and holds four lines: a goal and an expectation fit, the numbers and the
     * exceptions do not - and a wall of seventeen boards is not teaching, it is a wall. The guide
     * standing in the room says the rest, which is where the cooldowns, the permission nodes and
     * the "except when it is raining" belong.
     */
    private record Section(String title, List<String> lines, List<String> talk, int slots,
            boolean openSky, Station station, int cells, int headroom, boolean scene) {

        Section(String title, List<String> lines, List<String> talk, int slots, boolean openSky,
                Station station) {
            this(title, lines, talk, slots, openSky, station, 1, 0, false);
        }

        Section(String title, List<String> lines, List<String> talk, int slots, boolean openSky,
                Station station, int cells) {
            this(title, lines, talk, slots, openSky, station, cells, 0, false);
        }

        Section(String title, List<String> lines, List<String> talk, int slots, boolean openSky,
                Station station, int cells, int headroom) {
            this(title, lines, talk, slots, openSky, station, cells, headroom, false);
        }

        /**
         * The same, saying that this step's rule reads the hour, the weather or the ground it was
         * built on - so it owes the player a board saying which of those it is.
         */
        Section needsScene() {
            return new Section(title, lines, talk, slots, openSky, station, cells, headroom, true);
        }
    }

    /** The colours one hall is built from, so each race reads as itself. */
    private record Theme(Material wall, Material skirting, Material floor, Material trim,
            Material window, Material light) {
    }

    /** A rectangular region in one named world. */
    public record Box(String world, int x1, int y1, int z1, int x2, int y2, int z2) {

        public boolean holds(Location at) {
            return world.equals(at.getWorld().getName())
                    && at.getBlockX() >= x1 && at.getBlockX() <= x2
                    && at.getBlockY() >= y1 && at.getBlockY() <= y2
                    && at.getBlockZ() >= z1 && at.getBlockZ() <= z2;
        }
    }

    /** A block the hall placed for a player to use, and everything needed to put it back. */
    private record Fixture(Material type, BlockFace facing, List<String> lines, Trigger trigger) {
    }

    private static final int LOBBY_HALF = 12;

    /** How wide one section of a hall is, in blocks. */
    private static final int SLOT = 11;

    /** How deep a hall is, in blocks. */
    private static final int DEPTH = 15;

    /**
     * How much text fits on one line of a sign board, in units of one ASCII character.
     *
     * <p>The board's text area is about ninety pixels and the default font is six pixels a
     * character, so fifteen. A full-width character is nine pixels and counts as one and a half.
     */
    private static final double SIGN_WIDTH = 15.0;

    /**
     * How many lines a board holds, and how many boards one run of signs may use.
     *
     * <p>Ten is what fits: a section's own boards start two columns into its slot and a slot is
     * eleven wide, so the next section's boards begin thirteen columns along. Anything past ten
     * runs into them, and the later write wins - which loses a board silently, so
     * {@link #boardsDropped()} and {@link #signCollisions()} exist to make that loud instead.
     */
    private static final int LINES_PER_BOARD = 4;
    private static final int MAX_BOARDS = 10;

    /** Boards a switch's own label may use. Switches stand three columns apart. */
    private static final int SWITCH_BOARDS = 3;

    /**
     * A page break: the next line starts a fresh board.
     *
     * <p>A goal or an expectation is a sentence, and half of one on the end of a board and the
     * rest on the next is worse than a board that ends early. Every part of a section's teaching
     * gets its own board, so whichever one a person looks at says one thing.
     */
    private static final String BREAK = "\u0000";

    /** How far the halls sit from the world spawn, and from each other. */
    private static final int CELL = 320;

    /** Half-width of the pad in the world below that brings a player back up. */
    private static final int PAD_HALF = 2;

    /** How often a ceiling light is set into the grid, in blocks. */
    private static final int LIGHT_SPACING = 3;

    // -- the room grid ------------------------------------------------------
    //
    // A race hall is a grid of rooms, not a corridor. Every step is its own room with its own
    // walls, its own door to the next one and its own way back to the last, so a step can be
    // changed without anything else moving - and a room can be walked into, looked at and left
    // without passing through the four steps on either side of it.

    /** Half of a room's inner space: twenty-one blocks across, so nothing has to be squeezed. */
    private static final int CHAMBER_HALF = 10;

    /**
     * Centre-to-centre distance between two neighbouring rooms.
     *
     * <p>Twenty-one of inner space plus a two-block shared wall. Two rooms that touch therefore
     * merge into a 44-wide space with the wall between them taken out, which is what the
     * vampire's truce room is: 21 + 2 + 21.
     */
    private static final int CHAMBER_PITCH = 23;

    /** How wide the door cut through the wall between two neighbouring rooms is. */
    private static final int DOOR_W = 2;

    /** How many rooms a world holds before the row turns around and comes back. */
    private static final int COLS = 4;

    /** The four headroom tiers. See the plan's size table for where each number comes from. */
    private static final int CHAMBER_HEAD = 7;
    private static final int TALL_HEAD = 11;
    private static final int MOB_HEAD = 18;
    private static final int DROP_TOWER = 27;

    /**
     * Past this headroom the ceiling lights stop lighting the floor.
     *
     * <p>Light falls off a level a block, so a lamp seven blocks up leaves the floor at eight and
     * a lamp eleven up leaves it at four. A room taller than twelve gets a hanging layer instead,
     * at {@link #HANG_HEIGHT}, where it is five or six blocks off the floor and worth having.
     */
    private static final int HANG_ABOVE = 12;

    /** How high a hanging light sits above the floor it is lighting. */
    private static final int HANG_HEIGHT = 6;

    /** How often a hanging light is set into the grid, in blocks. */
    private static final int HANG_SPACING = 6;

    /**
     * Where a room's boards hang, relative to the floor.
     *
     * <p>The floor is the block the player stands on, so a board at two is across their chest and
     * a board at three is where their eyes are. Everything meant to be read or clicked lives in
     * that band, and {@link #BOARD_MAX} is the ceiling of it: past four a board is read by tipping
     * the head back, and at one - the top of the player's own feet - it is below the line of sight
     * of anybody standing in front of it. This hall put its teaching wall at one for a while and
     * it read like gravel. Both ends are now checked rather than remembered.
     */
    private static final int BOARD_Y = 2;
    private static final int BOARD_ROW2_Y = 3;

    /** The band a board may hang in, over the floor of its room. */
    private static final int BOARD_MIN = 2;
    private static final int BOARD_MAX = 4;

    /**
     * Where the one button left in the building sits: on the red wool of a room's exit.
     *
     * <p>Every other action is a board a player right-clicks, because a board is where the words
     * are and a switch is not - the action and its name are then the same object, which is what
     * stops a wall of switches from meaning "guess". The exit keeps its button because the red
     * wool is the one landmark a player learns once and then finds in every room without reading
     * anything.
     */
    private static final int EXIT_BUTTON_Y = 1;

    /** Where a tall room's second band of wall lights goes. */
    private static final int HIGH_BAND_Y = 12;

    /**
     * How bright a board or a switch has to be to be read.
     *
     * <p>A player standing anywhere in a room has to be able to read every board and see every
     * switch without carrying a torch: this is the rule that keeps a room from being lit well
     * enough to look at and not well enough to use.
     */
    private static final int LIGHT_MIN = 10;

    /**
     * How high a board may hang over the floor of its room.
     *
     * <p>A player stands on the floor with their eyes about one and a half blocks up; a board at
     * {@link #BOARD_MAX} is read by looking slightly up rather than by tipping the head back.
     * Anything taller, or anything down at {@link #BOARD_MIN} minus one, is scenery. The one
     * button left in the building hangs at {@link #EXIT_BUTTON_Y}, which is well inside reach.
     */
    private static final int BUTTON_REACH = 4;
    private static final int BOARD_REACH = BOARD_MAX;

    /** Boards one step's own teaching may use on its wall, and one scene board's may use. */
    private static final int TEACH_BOARDS = 4;
    private static final int SCENE_BOARDS = 4;

    /**
     * How a room's boards are laid out along the wall they hang on.
     *
     * <p>Four columns each: up to three boards for one action, and a column of wall between it and
     * the next. Five of them fit along a wall of the standard room, and a room with more than that
     * starts a second row.
     *
     * <p>Rows at two and three, and that is the whole point of the numbers. A player's eyes are
     * one and a half blocks up and their reach is a few; the band from two to four is the one they
     * can read and press without moving their head, with two and three the best of it. Rows at two
     * and four put the second row at the edge of that band, which is legal but is not the best
     * place to put the wall a player is meant to read. Nothing may sit at one: that is the top of
     * their feet. This has been got wrong twice; `mmp signage` now checks it rather than anybody
     * remembering it.
     */
    private static final int SWITCH_PITCH = 4;
    private static final int SWITCH_ROW_Y = 2;
    private static final int SWITCH_ROW_GAP = 1;

    private static final Material AIR = Material.AIR;
    private static final Material STELE = Material.CHISELED_STONE_BRICKS;
    private static final Material PAD_TRIM = Material.POLISHED_BLACKSTONE_BRICKS;

    /** The one colour that means "out". Everything else is plain, so this reads instantly. */
    private static final Material EXIT_WOOL = Material.RED_WOOL;

    /**
     * Monsters that cannot be summoned into a hall at all.
     *
     * <p>This used to hold the ender dragon, on the reasoning that a boss with a body made of
     * parts has nothing to say about a truce. It turns out the plugin's truce list names the
     * dragon and somebody wants to watch that hold, so it is summoned like everything else. The
     * set stays empty rather than going away, because something may yet turn out not to fit.
     */
    private static final Set<String> UNROOMABLE = Set.of();

    private static final Theme LOBBY = new Theme(Material.DEEPSLATE_TILES, Material.STONE_BRICKS,
            Material.SMOOTH_QUARTZ, Material.POLISHED_ANDESITE, Material.GLASS,
            Material.SEA_LANTERN);

    private static final Map<String, Theme> THEMES = new LinkedHashMap<>();

    static {
        THEMES.put("vampire", new Theme(Material.POLISHED_BLACKSTONE_BRICKS, Material.RED_NETHER_BRICKS,
                Material.BLACKSTONE, Material.NETHER_BRICKS, Material.RED_STAINED_GLASS,
                Material.SHROOMLIGHT));
        THEMES.put("ghoul", new Theme(Material.MOSSY_STONE_BRICKS, Material.MOSS_BLOCK,
                Material.MOSS_BLOCK, Material.MOSSY_COBBLESTONE, Material.GREEN_STAINED_GLASS,
                Material.SHROOMLIGHT));
        THEMES.put("werewolf", new Theme(Material.SPRUCE_PLANKS, Material.STRIPPED_SPRUCE_LOG,
                Material.DARK_OAK_PLANKS, Material.SPRUCE_LOG, Material.BROWN_STAINED_GLASS,
                Material.SEA_LANTERN));
        THEMES.put("demon", new Theme(Material.NETHER_BRICKS, Material.RED_NETHER_BRICKS,
                Material.NETHER_BRICKS, Material.POLISHED_BLACKSTONE_BRICKS,
                Material.ORANGE_STAINED_GLASS, Material.GLOWSTONE));
        THEMES.put("priest", new Theme(Material.QUARTZ_BRICKS, Material.CHISELED_QUARTZ_BLOCK,
                Material.SMOOTH_QUARTZ, Material.POLISHED_DIORITE, Material.WHITE_STAINED_GLASS,
                Material.SEA_LANTERN));
        THEMES.put("witchhunter", new Theme(Material.COBBLESTONE, Material.OAK_PLANKS,
                Material.COBBLESTONE, Material.SPRUCE_PLANKS, Material.GLASS,
                Material.SEA_LANTERN));
        THEMES.put("enderborn", new Theme(Material.PURPUR_BLOCK, Material.PURPUR_PILLAR,
                Material.END_STONE_BRICKS, Material.OBSIDIAN, Material.PURPLE_STAINED_GLASS,
                Material.SEA_LANTERN));
        // The angel's hall is the bright one: pale blocks and a window band, because the first
        // version was the darkest room in the building and nobody could test in it.
        THEMES.put("angel", new Theme(Material.SMOOTH_QUARTZ, Material.QUARTZ_BRICKS,
                Material.WHITE_CONCRETE, Material.GOLD_BLOCK, Material.WHITE_STAINED_GLASS,
                Material.SEA_LANTERN));
    }

    /** Where each race's hall is built in the grid around the spawn, and how tall it is. */
    private static final Map<String, int[]> CELLS = new LinkedHashMap<>();

    static {
        // column, row, headroom - the middle cell is the lobby itself. The vampire's hall is the
        // tall one: its truce section has to hold things like a giant and a ghast, and its sun
        // section has to reach the sky. The angel's is tall for the same reason - a fall that
        // cannot kill is not a fall a human can be turned by.
        CELLS.put("vampire", new int[] {0, 0, 34});
        CELLS.put("ghoul", new int[] {1, 0, 10});
        CELLS.put("werewolf", new int[] {2, 0, 6});
        CELLS.put("priest", new int[] {0, 1, 6});
        CELLS.put("witchhunter", new int[] {2, 1, 6});
        CELLS.put("demon", new int[] {0, 2, 8});
        CELLS.put("enderborn", new int[] {1, 2, 6});
        CELLS.put("angel", new int[] {2, 2, 34});
    }

    /**
     * Races whose hall needs a world of its own, because the plugin reads that world's state: the
     * clock for the vampire and the werewolf, the weather for the ghoul, and the <b>biome</b> for
     * the demon - its only way back to humanity is to drown in a cold one, and this server
     * build's {@code World#setBiome} silently does nothing (a write followed by a read returns
     * what was there before), so the cold has to come from the generator instead.
     */
    private static final Set<String> OWN_WORLD = Set.of("vampire", "werewolf", "ghoul", "demon");

    private final World world;
    private final World nether;
    private final int ox;
    private final int oy;
    private final int oz;

    private final Map<Location, Fixture> fixtures = new LinkedHashMap<>();

    /**
     * Boards a section wrote that there was no room left for, and boards written over by another
     * section. Both mean a person reading the wall is missing text, and neither shows up on its
     * own: the first is dropped, the second is overwritten and then quietly repaired back to
     * whichever section recorded it last. A run that reports zero of each had room for
     * everything it said.
     */
    private int boardsDropped;
    private int signCollisions;

    /**
     * Where boards were dropped, so a report can name the room rather than the total.
     *
     * <p>A dropped board is text nobody will ever read, and the total on its own says only that
     * something, somewhere, ran out of wall. The room is what is actionable.
     */
    private final List<String> droppedBoards = new ArrayList<>();

    /** The room being built right now, so a board written from inside a station can be filed. */
    private String currentRoom;

    /**
     * The switches the room being built has asked for, in the order it asked for them.
     *
     * <p>A switch belongs on a wall, and it used to hang on a stone post standing in the middle of
     * the floor, which reads as a button floating in the room: its back is turned to a block that is
     * part of nothing, what lights it is whatever lamp happens to be nearest, and the boards around
     * it give no clue which of them goes with which button. So a room's switches are not placed
     * where the step asked for them - they are collected here while the step is built, and laid out
     * along the far wall afterwards, in the order the step asked.
     *
     * <p>There used to be a second field beside this one holding the coordinates of that wall, and a
     * branch in {@code switchpoint} that built the post when no room was open. The two nether cells
     * have since called {@code laySwitchWall} like everybody else, so the branch was unreachable and
     * the field had nothing left to be read by. Both are gone.
     */
    private final List<PlannedSwitch> switchPlans = new ArrayList<>();

    /** One switch a room asked for, before the wall it will hang on has been worked out. */
    private record PlannedSwitch(Trigger trigger, List<String> lines) {
    }

    /** Rooms that wrote the board about their own hour, weather or ground - and those that owe one. */
    private final Set<String> scenesWritten = new HashSet<>();
    private final List<String> scenesMissing = new ArrayList<>();

    /** Rooms that got the way out, and the ones that were supposed to. */
    private final Set<String> roomsWithExit = new HashSet<>();
    private int roomsBuilt;

    /** How tall each room was built, so a report can say what is actually there. */
    private final Map<String, Integer> roomHeads = new LinkedHashMap<>();
    private final Map<Location, Map<Integer, ItemStack>> chests = new LinkedHashMap<>();
    private final Map<String, Location> points = new LinkedHashMap<>();

    /**
     * The tag every boat the hall put down wears.
     *
     * <p>On the boat rather than in a note kept here, because the two things asked of it - "may
     * not be broken" and "put it back" - are asked while it is out in the world, and because a
     * tag travels with the boat through a chunk save, a server restart and the hall being rebuilt
     * around it. It is also the only thing that tells a boat the hall put down apart from one a
     * player rowed in to keep.
     */
    static final String MOORED = "mm_hall_boat";

    /** A boat the hall put down, and the mark it was put down on. */
    private record Moored(String world, double x, double y, double z, EntityType type) {
    }

    /** The boats the hall put down. One, at the moment - the ghoul's pool. */
    private final List<Moored> moored = new ArrayList<>();

    /**
     * Each room's guide: where he stands, and what he says, keyed {@code race.index}.
     *
     * <p>A room's long teaching lives here rather than on its wall - see {@link Section} - and the
     * guide is the only thing that reads it. Keyed the same way as the room's own spawn and zone,
     * because it is the same room being described from three angles.
     */
    private final Map<String, Location> guideSpot = new HashMap<>();
    private final Map<String, List<String>> guideTalk = new HashMap<>();
    private final Map<String, Box> zones = new LinkedHashMap<>();

    /**
     * Where each race's rooms stand, in the order a player walks them.
     *
     * <p>The grid knows where every room is; this is the list, so "the next room" is a step along
     * the order the steps were written in rather than a guess from coordinates - which is what
     * makes inserting a room somewhere in the middle work without renumbering anything.
     */
    private final Map<String, List<Location>> roomSpawns = new LinkedHashMap<>();
    private final Map<String, Location> sparMarks = new LinkedHashMap<>();
    private final List<Box> boxes = new ArrayList<>();

    private Location lobbySpawn;
    private Location outsideSpawn;
    private final List<Location> priestAltars = new ArrayList<>();
    private Location netherLava;

    private Hall(World world, World nether, int ox, int oy, int oz) {
        this.world = world;
        this.nether = nether;
        this.ox = ox;
        this.oy = oy;
        this.oz = oz;
    }

    // -- building ----------------------------------------------------------

    /**
     * Builds the lobby above the world spawn, every race hall, and the pad on the ground below,
     * then moves the world spawn into the lobby so a joining player starts at the buttons.
     */
    public static Hall build(World world) {
        Location spawn = world.getSpawnLocation();
        int groundY = world.getHighestBlockYAt(spawn.getBlockX(), spawn.getBlockZ(),
                HeightMap.MOTION_BLOCKING_NO_LEAVES);
        Hall hall = new Hall(world, Bukkit.getWorld(world.getName() + "_nether"),
                spawn.getBlockX() - LOBBY_HALF, groundY + 40, spawn.getBlockZ() - LOBBY_HALF);
        hall.lobby();
        for (Map.Entry<String, int[]> cell : CELLS.entrySet()) {
            hall.raceHall(cell.getKey(), cell.getValue());
        }
        hall.netherDemonHall();
        hall.netherGhoulCell();
        hall.returnPad();
        hall.keepLoaded();
        world.setSpawnLocation(hall.lobbySpawn);
        world.setGameRule(GameRule.SPAWN_RADIUS, 0);
        return hall;
    }

    /**
     * Keeps the hall's own chunks loaded, in every world it has one in.
     *
     * <p>Blocks survive a chunk being unloaded; entities do not. A monster in a hall three hundred
     * blocks from wherever the player is gets put away with its chunk and comes back only when
     * the chunk does - and until then the hall sees an invalid stand and puts a new one down
     * every two seconds, for ever. Force-loading costs a few dozen chunks and makes a hall the
     * same whether or not anybody is standing in it.
     */
    private void keepLoaded() {
        Set<String> done = new HashSet<>();
        for (Box box : boxes) {
            World target = Bukkit.getWorld(box.world());
            if (target == null) {
                continue;
            }
            for (int cx = box.x1() >> 4; cx <= box.x2() >> 4; cx++) {
                for (int cz = box.z1() >> 4; cz <= box.z2() >> 4; cz++) {
                    if (done.add(box.world() + ":" + cx + ":" + cz)) {
                        target.setChunkForceLoaded(cx, cz, true);
                    }
                }
            }
        }
    }

    private void lobby() {
        room(world, ox, oy, oz, LOBBY_HALF * 2, LOBBY_HALF * 2, 5, LOBBY);
        stele();
        int y = oy + 1;
        int x = ox + 2;
        for (String race : CELLS.keySet()) {
            set(world, LOBBY.light(), x, y, oz - 1);
            set(world, LOBBY.light(), x, y + 1, oz - 1);
            signOn(world, x, y + 1, oz - 1, BlockFace.SOUTH, Trigger.to(race), title(race),
                    "§7右键这块牌进入");
            x += 2;
        }
        set(world, LOBBY.light(), x, y, oz - 1);
        set(world, LOBBY.light(), x, y + 1, oz - 1);
        signOn(world, x, y + 1, oz - 1, BlockFace.SOUTH, Trigger.of(Kind.LEAVE_HALL),
                "§a离开大厅", "§7右键这块牌去下面的世界");
        lobbySpawn = new Location(world, ox + LOBBY_HALF + 0.5, oy + 1, oz + LOBBY_HALF + 4.5);
    }

    private void stele() {
        int x = ox + LOBBY_HALF;
        int z = oz + LOBBY_HALF;
        set(world, STELE, x, oy + 1, z);
        set(world, STELE, x, oy + 2, z);
        set(world, LOBBY.light(), x, oy + 3, z);
        signOn(world, x, oy + 2, z, BlockFace.NORTH,
                "§e超自然玩家", "§f测试大厅", "§7先选一个种族");
        signOn(world, x, oy + 2, z, BlockFace.SOUTH,
                "§7每个种族一间大厅", "§7一间一个环节", "§7按告示牌做即可");
        signOn(world, x, oy + 2, z, BlockFace.WEST,
                "§7箱子关上就补满", "§7道具带不出去");
        signOn(world, x, oy + 2, z, BlockFace.EAST,
                "§7牌＝进出测试", "§7离开会恢复人类");
    }

    /**
     * One race's hall: a grid of sealed rooms, one step each, in a world of that race's own.
     *
     * <p>Rooms, not a corridor. A corridor made every step depend on the ones around it - the
     * walls, the light and the roof were one long build, so adding a step meant re-flowing the
     * whole hall, and a player who wanted step eleven walked past ten steps of text to reach it.
     * A room is self-contained: its own shell, its own light, its own switches, its own ways out.
     * Steps can be inserted, reordered or rewritten one at a time, and the grid can grow a row
     * without anything moving.
     *
     * <p>Nothing joins two rooms. The first build cut a two-block door through the wall each pair
     * of neighbours shared, which made the hall one continuous space with twenty-odd ways through
     * it: a player walking forward could not tell which room they were in, could not go back to one
     * particular room, and had to walk past the ten steps between where they were and where they
     * wanted to be. Every room is now entered from the entry room - which lists the whole hall on
     * one wall, a switch and a name per room - and left by one of three switches on its far wall:
     * back to the entry, one room back, one room on. The room after this one and the room before it
     * are named on their own switches, so a step is known before it is walked into.
     *
     * <p>A step that needs more room than a room has (the vampire's truce step lines up forty-one
     * monsters) takes a two-by-two block of the grid instead, built as one space with the walls
     * between those four cells taken out.
     */
    private void raceHall(String race, int[] cell) {
        World target = clockWorld(world, race);
        Theme theme = THEMES.get(race);
        int spawnX = world.getSpawnLocation().getBlockX();
        int spawnZ = world.getSpawnLocation().getBlockZ();
        int centreX = spawnX + (cell[0] - 1) * CELL;
        int centreZ = spawnZ + (cell[1] - 1) * CELL;
        int y = oy;
        int baseHead = cell[2];

        List<Section> sections = new ArrayList<>();
        sections.add(entrySection(race, sections));
        sections.addAll(sectionsFor(race, baseHead));
        List<Placement> plan = layOut(sections);

        // The entry room's two ways in point at rooms one and two by number, and numbers that have
        // to agree with an insert made in another method are numbers that will disagree sooner or
        // later - these did, for every hall at once. So the one thing the entry room claims about
        // the list is checked here, where both the claim and the list are in view: room two is the
        // room about being killed. Anything else is a warning on the console rather than a report
        // from a player who walked into the wrong room.
        if (sections.size() > 2 && !sections.get(2).title().contains("被杀")) {
            Bukkit.getLogger().warning("the entry room of " + race
                    + " points at room 2 for being killed, but room 2 is '"
                    + sections.get(2).title() + "' - the entry room's two ways in are mislabelled");
        }

        int rows = 0;
        for (Placement placement : plan) {
            rows = Math.max(rows, placement.row() + (placement.span() > 1 ? 2 : 1));
        }
        int gridX = centreX - COLS * CHAMBER_PITCH / 2;
        int gridZ = centreZ - rows * CHAMBER_PITCH / 2;

        // Where each room stands, worked out from the plan rather than from a guess about which
        // step follows which.
        List<int[]> origins = new ArrayList<>();
        for (Placement placement : plan) {
            // A room that takes four cells is the two of them with the shared wall between
            // taken out: 21 + 2 + 21 across, which is where the two 23s of pitch leave it.
            int side = placement.span() > 1 ? CHAMBER_HALF * 4 + 4 : CHAMBER_HALF * 2 + 1;
            int originX = gridX + placement.col() * CHAMBER_PITCH;
            int originZ = gridZ + placement.row() * CHAMBER_PITCH;
            origins.add(new int[] {originX, originZ, side});
        }

        // Phase one: the shells. Rooms are sealed - there is no door between two of them, because
        // a hall you can walk through is a hall you can walk through into the wrong room, and a
        // grid of twenty-odd identical doorways gives a player no way to tell which one they just
        // came out of. Every room is entered from the entry room and left the same way.
        for (int index = 0; index < plan.size(); index++) {
            int[] at = origins.get(index);
            Section section = sections.get(index);
            int head = headroomFor(section, baseHead);
            chamber(target, at[0], y, at[1], at[2], head, theme);
            points.put("room." + race + "." + index, landing(target, at[0], y, at[1], at[2]));
        }

        // Phase two: what each room is for.
        for (int index = 0; index < plan.size(); index++) {
            int[] at = origins.get(index);
            int x = at[0];
            int z = at[1];
            int side = at[2];
            Section section = sections.get(index);
            int head = headroomFor(section, baseHead);
            if (section.openSky()) {
                // An open room is the point of the weather steps: a roof, even a glass one, is
                // cover to anybody the plugin asks, and the void never rains at all.
                box(target, AIR, x, y + head + 1, z, x + side - 1, y + head + 1, z + side - 1);
            }
            String key = race + "." + index;
            currentRoom = key;
            switchPlans.clear();
            sectionHead(target, y, x, z, section.title(), section.lines(), TEACH_BOARDS);
            roomFurniture(target, race, index, y, x, z, side, sections, theme);
            section.station().build(target, y, x, z, side);
            laySwitchWall(target, y, x, z, side, side, theme);
            roomsBuilt++;
            roomHeads.put(key, head);
            if (section.scene() && !scenesWritten.contains(key)) {
                scenesMissing.add(key);
            }
            // The entry room used to list every other room on its far wall, one switch each, and
            // that is gone. The reason is the one the user gave: a wall of twenty switches is not
            // a welcome, it is homework. The entry room now offers the two ways into the race -
            // the shrine, or being killed by one - and the same three ways out every room has, and
            // the rest of the hall is walked rather than picked off a menu. Its own boards are hung
            // by laySwitchWall above, like every other room's.
            currentRoom = null;
            points.put("sec." + race + "." + index, new Location(target, x, y, z));
            // Where the room's guide stands, and what he says. The south-west corner of the room,
            // two blocks in: clear of the landing in the middle, of the boards on the far wall, of
            // the teaching wall behind him, of the red wool on the west wall, and of the way out on
            // the east - and facing the middle of the room, which is where the player arrives.
            guideTalk.put(race + "." + index, section.talk());
            guideSpot.put(race + "." + index, new Location(target, x + 2.5, y + 1, z + 2.5, 45f, 0f));
            zones.put("room." + race + "." + index, new Box(target.getName(), x - 1, y - 1, z - 1,
                    x + side, y + head + 1, z + side));
        }

        zones.put("hall." + race, new Box(target.getName(), gridX - 1, y - 1, gridZ - 1,
                gridX + COLS * CHAMBER_PITCH, y + MOB_HEAD + 1, gridZ + rows * CHAMBER_PITCH));
        List<Location> spawns = new ArrayList<>();
        for (int index = 0; index < origins.size(); index++) {
            int[] at = origins.get(index);
            // The entry room is entered by walking into a wall of text, so the player is put down
            // in front of it rather than at the far end of a forty-four block room.
            spawns.add(landing(target, at[0], y, at[1], at[2], index == 0));
        }
        roomSpawns.put(race, spawns);
        points.put("spawn." + race, roomSpawns.get(race).get(0));
    }

    /**
     * Where a player arriving in a room is put: in front of the near wall, in the open.
     *
     * <p>Standing room is not guaranteed by the coordinates. A step is free to build anything it
     * likes in its own room - an altar, a pool, a plinth - and did: four rooms had something solid
     * where the player was supposed to be put down, so arriving there meant being pushed out of a
     * block by the server, and the light reading for the spot was nothing at all, because light
     * does not go into a solid block. The spot is therefore searched for rather than assumed: the
     * middle of the near wall first, then outwards along it, then a row further in.
     */
    private static Location landing(World target, int x, int y, int z, int side) {
        return landing(target, x, y, z, side, false);
    }

    /** The same, starting from the far wall - for a room whose business is on that wall. */
    private static Location landing(World target, int x, int y, int z, int side, boolean fromFar) {
        int middle = x + side / 2;
        for (int rowStep = 0; rowStep < (side - 4) / 2; rowStep++) {
            int row = fromFar ? side - 3 - rowStep * 2 : 2 + rowStep * 2;
            for (int step = 0; step < side - 2; step++) {
                for (int direction : new int[] {1, -1}) {
                    int at = middle + step * direction;
                    if (at <= x || at >= x + side - 1) {
                        continue;
                    }
                    if (target.getBlockAt(at, y + 1, z + row).isPassable()
                            && target.getBlockAt(at, y + 2, z + row).isPassable()
                            && !target.getBlockAt(at, y, z + row).isPassable()) {
                        return new Location(target, at + 0.5, y + 1, z + row + 0.5);
                    }
                }
            }
        }
        return new Location(target, middle + 0.5, y + 1, z + 2.5);
    }

    /** One entry of the room plan: which cell of the grid, and how many cells it takes. */
    private record Placement(int col, int row, int span) {
    }

    /**
     * Lays the steps out on the grid, in reading order, and works out which of them take four
     * cells.
     *
     * <p>A step that says it needs four cells gets a free two-by-two block; everything else takes
     * the next free cell. The order the rooms sit in is only how they are numbered and how far
     * apart they are - nothing walks between them, so a room is reached by asking for it and not
     * by walking. It used to run serpentine, one row one way and the next row back, which is a way
     * of filling a grid and a way of getting lost in one.
     */
    private static List<Placement> layOut(List<Section> sections) {
        Set<Long> used = new HashSet<>();
        List<Placement> out = new ArrayList<>();
        int cursor = 0;
        for (Section section : sections) {
            int span = section.cells() >= 4 ? 2 : 1;
            while (true) {
                int row = cursor / COLS;
                int col = cursor % COLS;
                if (span == 1) {
                    if (used.add(cellKey(col, row))) {
                        out.add(new Placement(col, row, 1));
                        cursor++;
                        break;
                    }
                } else if (col + 1 < COLS && free(used, col, row) && free(used, col + 1, row)
                        && free(used, col, row + 1) && free(used, col + 1, row + 1)) {
                    for (int dc = 0; dc <= 1; dc++) {
                        for (int dr = 0; dr <= 1; dr++) {
                            used.add(cellKey(col + dc, row + dr));
                        }
                    }
                    out.add(new Placement(col, row, 2));
                    cursor += 2;
                    break;
                }
                cursor++;
            }
        }
        return out;
    }

    private static boolean free(Set<Long> used, int col, int row) {
        return !used.contains(cellKey(col, row));
    }

    private static long cellKey(int col, int row) {
        return ((long) col << 32) | (row & 0xffffffffL);
    }

    private int headroomFor(Section section, int baseHead) {
        if (section.headroom() > 0) {
            return section.headroom();
        }
        if (section.cells() >= 4) {
            return MOB_HEAD;
        }
        return Math.min(baseHead, CHAMBER_HEAD);
    }

    /**
     * A room: a shell, a floor, and light that actually reaches the floor.
     *
     * <p>The light is the part that is easy to get wrong. A lamp in the ceiling of a seven-high
     * room leaves the floor at eight; the same lamp in an eighteen-high room leaves it at
     * nothing at all - light falls off a level a block and a lamp is worth fifteen. So the height
     * decides the layout: low rooms are lit from above, tall ones are lit from inside by a band
     * of lamps in the walls, and anything past twelve gets a hanging layer as well, hung on
     * chains where a player can see what is lighting the room.
     */
    private void chamber(World target, int x, int y, int z, int side, int headroom, Theme theme) {
        int top = y + headroom;
        int farX = x + side - 1;
        int farZ = z + side - 1;

        box(target, theme.wall(), x - 1, y - 1, z - 1, farX + 1, top + 1, farZ + 1);
        box(target, AIR, x, y + 1, z, farX, top, farZ);
        box(target, theme.trim(), x, y, z, farX, y, farZ);
        box(target, theme.floor(), x + 1, y, z + 1, farX - 1, y, farZ - 1);
        edge(target, x, y, z, side, side, theme.skirting());
        edge(target, x, y + 1, z, side, side, theme.skirting());
        edge(target, x, top, z, side, side, theme.window());

        if (headroom <= HANG_ABOVE) {
            box(target, theme.wall(), x, top + 1, z, farX, top + 1, farZ);
            for (int dx = 1; dx < side; dx += LIGHT_SPACING) {
                for (int dz = 1; dz < side; dz += LIGHT_SPACING) {
                    set(target, theme.light(), x + dx, top + 1, z + dz);
                }
            }
        } else {
            box(target, theme.wall(), x, top + 1, z, farX, top + 1, farZ);
        }

        // In every room, at the height the boards hang. A ceiling lamp is a room's general
        // light and this is its reading light: a board two blocks from the nearest lamp in its
        // own row reads twelve, and a board lit only from eight blocks overhead reads six - the
        // same wall, one readable and one not. Every switch stands in this band, so every switch
        // has a lamp beside it wherever in the room it was put.
        for (int dx = 1; dx < side; dx += 2) {
            set(target, theme.light(), x + dx, y + 3, z - 1);
            set(target, theme.light(), x + dx, y + 3, farZ + 1);
        }
        for (int dz = 1; dz < side; dz += 2) {
            set(target, theme.light(), x - 1, y + 3, z + dz);
            set(target, theme.light(), farX + 1, y + 3, z + dz);
        }

        if (headroom > HANG_ABOVE) {
            int middleX = x + side / 2;
            int middleZ = z + side / 2;
            for (int dx = 2; dx < side - 1; dx += HANG_SPACING) {
                for (int dz = 2; dz < side - 1; dz += HANG_SPACING) {
                    int lx = x + dx;
                    int lz = z + dz;
                    // Keep the middle clear: what stands there is either a dragon hovering over
                    // the player's head or the shaft of a fall that has to kill.
                    if (Math.abs(lx - middleX) <= 4 && Math.abs(lz - middleZ) <= 4) {
                        continue;
                    }
                    set(target, theme.light(), lx, y + HANG_HEIGHT, lz);
                    for (int cy = y + HANG_HEIGHT + 1; cy < top + 1; cy++) {
                        set(target, Material.IRON_CHAIN, lx, cy, lz);
                    }
                }
            }
        }

        if (headroom > 20) {
            for (int dx = 1; dx < side; dx += 4) {
                set(target, theme.light(), x + dx, y + HIGH_BAND_Y, z - 1);
                set(target, theme.light(), x + dx, y + HIGH_BAND_Y, farZ + 1);
            }
            for (int dz = 1; dz < side; dz += 4) {
                set(target, theme.light(), x - 1, y + HIGH_BAND_Y, z + dz);
                set(target, theme.light(), farX + 1, y + HIGH_BAND_Y, z + dz);
            }
        }

        boxes.add(new Box(target.getName(), x - 1, y - 1, z - 1, farX + 1, top + 1, farZ + 1));
    }

    /**
     * Furniture every room has, in the same place in every room.
     *
     * <p>The way out is a block of red wool set into the west wall with a button on it, and a
     * two-line board over it. Red is the only thing in the building that is allowed to be a
     * colour of its own, so a player who has had enough of one step never has to read anything
     * to leave: the red block is the way out, wherever they are. The two navigation switches
     * hang on the east wall, which is otherwise empty.
     */
    private void roomFurniture(World target, String race, int index, int y, int x, int z, int side,
            List<Section> sections, Theme theme) {
        // Everything hangs one block inside the wall, not in it. A wall sign is held up by the
        // block behind it, so a sign placed in the wall's own layer has nothing to hold it - and
        // the repair loop then looks at it, finds it wrong, rebuilds it, and finds it wrong
        // again, once every two seconds, for ever. The wall's inner face is the outer column of
        // the room, which is where these go.
        if (currentRoom != null) {
            roomsWithExit.add(currentRoom);
        }
        set(target, EXIT_WOOL, x - 1, y + EXIT_BUTTON_Y, z + 1);
        button(target, x, y + EXIT_BUTTON_Y, z + 1, BlockFace.EAST, Trigger.of(Kind.EXIT_ROOM));
        shortSign(target, x, y + BOARD_Y, z + 1, BlockFace.EAST,
                "§c退出到初始大厅", "§7并恢复人类身份");

        // The far wall: where else a player can be. Boards, not switches, and over each one the
        // name of the room it goes to rather than the word "next" - the point of a step is to be
        // known before it is walked into, and "下一间" on its own tells a player nothing about
        // whether they want it.
        int farX = x + side - 1;
        if (index == 0) {
            // The entry room's own three, like every other room's, except that "back to the entry
            // room" would mean standing still - so it is written as what it does there, which is
            // put the player back on the mark they arrived on.
            navSwitch(target, theme, farX, y, z + 1,
                    Trigger.of(Kind.RETURN_START, race),
                    "§f回到大厅中央", "§7站回入口正中");
        } else {
            navSwitch(target, theme, farX, y, z + 1,
                    Trigger.of(Kind.RETURN_START, race),
                    "§f回入口大厅", "§7那一族所有房间的目录");
        }
        if (index > 0) {
            navSwitch(target, theme, farX, y, z + 4,
                    Trigger.of(Kind.ROOM_PREV, race),
                    "§f上一间", "§7" + sections.get(index - 1).title());
        }
        if (index + 1 < sections.size()) {
            navSwitch(target, theme, farX, y, z + 7,
                    Trigger.of(Kind.ROOM_NEXT, race),
                    "§f下一间", "§7" + sections.get(index + 1).title());
        }
    }

    /**
     * One of a room's three ways out: a board naming where it goes, with a lamp in the wall behind
     * it, and the board itself is what is clicked.
     *
     * <p>There used to be a switch under the board here. Two objects for one action, and the
     * player had to work out which of the pair they were meant to touch.
     */
    private void navSwitch(World target, Theme theme, int x, int y, int z, Trigger trigger,
            String... lines) {
        set(target, theme.light(), x + 1, y + BOARD_Y, z);
        shortSign(target, x, y + BOARD_Y, z, BlockFace.WEST, trigger, lines);
    }

    /**
     * The entry room's wall of doors is gone.
     *
     * <p>It listed every other room on one wall - a switch each, with the room's name and one line
     * about it - and it was the first thing a player met. What the user said about it is the whole
     * case against it: too many switches, too much text, and a jump to every room before knowing
     * whether any of them was worth going to. The entry room now offers the two ways into the race
     * and the three ways out, and everything else is reached by walking the hall, where each room's
     * boards name the one on either side of it.
     *
     * <p>What went with it is the one-line summary it used to print under each room's name. That
     * summary was derived from the room's own teaching rather than written twice - which was the
     * right instinct, and the wrong cost: it made every room's teaching text carry a second job.
     * The teaching now goes on the room's own boards and to the room's guide, and nowhere else.
     */

    /** A wall sign whose text is wrapped first, so nothing runs off the edge of the board. */
    private void shortSign(World target, int x, int y, int z, BlockFace facing, String... lines) {
        shortSign(target, x, y, z, facing, null, lines);
    }

    /** The same, on a board a player can click. */
    private void shortSign(World target, int x, int y, int z, BlockFace facing, Trigger trigger,
            String... lines) {
        List<String> wrapped = wrapAll(lines);
        sign(target, x, y, z, facing, trigger,
                wrapped.subList(0, Math.min(LINES_PER_BOARD, wrapped.size())).toArray(new String[0]));
    }

    /**
     * A room's own board about the state of the world it sits in: the hour, the weather, the
     * ground it was built on, and what is over the player's head.
     *
     * <p>These are the rules a player cannot see. A health bar that stops rising, a vampire who
     * catches fire at noon, a conversion that only works in the nether - in every one of those the
     * cause is somewhere a player is not looking, so it gets a board of its own rather than a
     * clause in the middle of a paragraph about something else. It hangs on the wall opposite the
     * teaching boards, which is otherwise empty, so it never has to fight one for room.
     *
     * <p>Written as four parts, and a part may be more than one line: what the room is, why it
     * matters, which switch changes it, and that the change is local to this hall's own world.
     */
    private void sceneBoard(World target, int y, int x, int z, int side, String... parts) {
        if (currentRoom != null) {
            scenesWritten.add(currentRoom);
        }
        List<String> wrapped = wrapAll(parts);
        int board = 0;
        for (int i = 0; i < wrapped.size() && board < SCENE_BOARDS; i += LINES_PER_BOARD) {
            List<String> page = wrapped.subList(i, Math.min(i + LINES_PER_BOARD, wrapped.size()));
            sign(target, x + 2 + board, y + BOARD_Y, z + side - 1, BlockFace.NORTH,
                    page.toArray(new String[0]));
            board++;
        }
        if (wrapped.size() > SCENE_BOARDS * LINES_PER_BOARD) {
            boardsDropped++;
            droppedBoards.add(target.getName() + " " + x + "," + (y + BOARD_Y) + "," + (z + side - 1)
                    + " allowed=" + SCENE_BOARDS + " scene-board text=" + wrapped.get(0));
        }
    }

    /** The demon's conversion hall stays where the plugin wants it: in the nether. */
    private void netherDemonHall() {
        if (nether == null) {
            Bukkit.getLogger().warning("no nether world, so the demon's conversion hall was not"
                    + " built (allow-nether must be true)");
            return;
        }
        int centreX = world.getSpawnLocation().getBlockX() - CELL;
        int centreZ = world.getSpawnLocation().getBlockZ() + CELL;
        int y = Math.max(oy, nether.getMinHeight() + 24);
        int x0 = centreX - 40;
        int z0 = centreZ - DEPTH / 2;
        room(nether, x0, y, z0, 6 * SLOT, DEPTH, 12, THEMES.get("demon"));
        sectionHead(nether, y, x0, z0, "§4① 转化 下界的火",
                brief("穿着整套皮革死在下界的火里，验证恶魔转化",
                        "三个条件同时成立才转化：死亡原因是岩浆 / 火 / 燃烧、死亡地点在下界、"
                                + "身上穿着整套皮革（或事先穿皮革走过下界传送门登记过申请）。"
                                + "皮革就是这里这个箱子里的四件——这是这个大厅建在下界的原因",
                        "死后成为恶魔；皮革是这一步的判定条件",
                        "从箱子里拿走整套皮革穿上", "点「跳进岩浆」"), SLOT);
        int cx = x0 + 2 * SLOT + SLOT / 2;
        int cz = z0 + DEPTH / 2;
        box(nether, Material.POLISHED_BLACKSTONE_BRICKS, cx - 3, y, cz - 3, cx + 3, y, cz + 3);
        box(nether, Material.LAVA, cx - 1, y - 1, cz - 1, cx + 1, y + 2, cz + 1);
        points.put("nether.lava", new Location(nether, cx + 0.5, y + 1, cz + 0.5));
        netherLava = points.get("nether.lava");
        largeChest(nether, x0 + 2, y + 1, z0 + DEPTH - 2, leather());
        switchpoint(nether, y, x0 + 2, z0 + 3, Trigger.of(Kind.DIP),
                "§c跳进岩浆", "§7点了就下池子");
        sectionHead(nether, y, x0 + SLOT, z0, "§f② 陪练 伤害测试",
                brief("在下界也能正常打伤害、拿能量",
                        "恶魔在下界不掉能量（每 5 秒的 -20 只在离开下界、又没站在火里时才扣），"
                                + "还免疫火焰、燃烧、爆炸和摔落伤害。这里的陪练是用来确认"
                                + "你在下界的伤害数值和主世界一致",
                        "陪练站在你面前，真实掉血；打不动就再右键一次那块牌把他刷新",
                        "点「放出陪练」", "用武器打他一下，再看伤害数字"), SLOT);
        sparMark(nether, "demon_nether", x0 + SLOT + 8, y, z0 + DEPTH / 2);
        switchpoint(nether, y, x0 + SLOT + 3, z0 + 3, Trigger.of(Kind.SPAR),
                "§f放出陪练", "§7他站在你面前，真的会掉血");
        sectionHead(nether, y, x0 + 5 * SLOT, z0, "§5③ 转化他人 地狱岩",
                brief("验证恶魔能把别人变成恶魔",
                        "手持地狱岩攻击另一名同样手持地狱岩的玩家，耗 2000 能量，"
                                + "会把对方直接转成恶魔。两边都得拿着地狱岩，缺一边就只是普通一击",
                        "打完那一下，对方的种族变成恶魔，聊天栏有提示",
                        "点「放出一个持地狱岩的异族」",
                        "拿箱子里的地狱岩打他一下",
                        "看他的种族是不是变成了 demon"), 4 * SLOT);
        chest(nether, x0 + 5 * SLOT + 2, y + 1, z0 + DEPTH - 2, BlockFace.NORTH, centred(
                new ItemStack(Material.NETHERRACK, 8)));
        switchpoint(nether, y, x0 + 5 * SLOT + 5, z0 + 3,
                Trigger.with(Kind.SPAWN_NPCS, "demon", "vampire:NETHERRACK"),
                "§a放出一个持地狱岩的异族", "§7他手上也得拿着地狱岩");
        switchpoint(nether, y, x0 + 5 * SLOT + 8, z0 + 3, Trigger.of(Kind.POWER, "demon_nether"),
                "§b能量 +1000", "§7点一下加一千");
        switchpoint(nether, y, x0 + 3 * SLOT + 5, z0 + 3, Trigger.of(Kind.POWER, "demon_nether"),
                "§b能量 +1000", "§7点一下加一千");
        switchpoint(nether, y, x0 + 4 * SLOT + 2, z0 + 3, Trigger.of(Kind.RETURN_OVERWORLD,
                "demon"),
                "§a回到主世界大厅", "§7恶魔大厅在另一头");
        wallSign(nether, y, x0, z0, "§f入口 · 退出", "§7随时可以停下");
        switchpoint(nether, y, x0 + 1, z0 + 3, Trigger.of(Kind.EXIT_TEST, "demon_nether"),
                "§a退出测试", "§7清道具 · 恢复人类 · 回大厅");
        switchpoint(nether, y, x0 + 3, z0 + 3, Trigger.of(Kind.RESET_HUMAN, "demon_nether"),
                "§a恢复人类", "§7留在这里，只改种族");
        laySwitchWall(nether, y, x0, z0, 6 * SLOT, DEPTH, THEMES.get("demon"));
        points.put("sec.demon_nether.0", new Location(nether, x0, y, z0));
        points.put("sec.demon_nether.1", new Location(nether, x0 + SLOT, y, z0));
        points.put("sec.demon_nether.2", new Location(nether, x0 + 5 * SLOT, y, z0));
        points.put("spawn.demon_nether",
                new Location(nether, x0 + 2.5, y + 1, z0 + DEPTH / 2.0 + 0.5));
        zones.put("hall.demon_nether", new Box(nether.getName(), x0 - 1, y - 1, z0 - 1,
                x0 + 6 * SLOT, y + 12, z0 + DEPTH));
    }

    /**
     * The ghoul's own cell in the nether, and the only place it can be entered.
     *
     * <p>The plugin's one way into a ghoul is being killed by a Zombie Pigman <em>in the
     * nether</em>: both halves are checked, and neither is a matter of taste. The hall used to
     * offer a plain zombie in an ordinary world, which meets neither condition - so its
     * conversion step could not convert anybody, however long a person stood there. This cell is
     * where the two halves of the condition are actually met.
     */
    private void netherGhoulCell() {
        if (nether == null) {
            Bukkit.getLogger().warning("no nether world, so the ghoul's conversion cell was"
                    + " not built (allow-nether must be true)");
            return;
        }
        int centreX = world.getSpawnLocation().getBlockX() - CELL;
        int centreZ = world.getSpawnLocation().getBlockZ() + CELL;
        int y = Math.max(oy, nether.getMinHeight() + 24);
        int x0 = centreX + 40;
        int z0 = centreZ - DEPTH / 2;
        room(nether, x0, y, z0, 3 * SLOT, DEPTH, 10, THEMES.get("ghoul"));
        sectionHead(nether, y, x0, z0, "§2① 转化 下界僵尸猪人",
                brief("在下界被僵尸猪人杀死，验证食尸鬼转化",
                        "插件只认这一条路：死在下界 + 凶手是僵尸猪人。主世界的普通僵尸杀你不算，"
                                + "大厅里放过的僵尸也没有用",
                        "你一定会变成食尸鬼；这里不掷概率，转化率是 100%",
                        "点「放出一只僵尸猪人」", "站着别动，等它打死你"), 3 * SLOT);
        switchpoint(nether, y, x0 + 2, z0 + 3, Trigger.of(Kind.PREPARE_GHOUL),
                "§2放出一只僵尸猪人", "§7它杀死你就会转化");
        switchpoint(nether, y, x0 + 5, z0 + 3, Trigger.of(Kind.RETURN_OVERWORLD, "ghoul"),
                "§a回到主世界大厅", "§7食尸鬼长廊在另一头");
        wallSign(nether, y, x0, z0, "§f入口 · 退出", "§7随时可以停下");
        switchpoint(nether, y, x0 + 1, z0 + 3, Trigger.of(Kind.EXIT_TEST, "ghoul_nether"),
                "§a退出测试", "§7清道具 · 恢复人类 · 回大厅");
        switchpoint(nether, y, x0 + 3, z0 + 3, Trigger.of(Kind.RESET_HUMAN, "ghoul_nether"),
                "§a恢复人类", "§7留在这里，只改种族");
        laySwitchWall(nether, y, x0, z0, 3 * SLOT, DEPTH, THEMES.get("ghoul"));
        points.put("sec.ghoul_nether.0", new Location(nether, x0, y, z0));
        points.put("spawn.ghoul_nether",
                new Location(nether, x0 + 1.5, y + 1, z0 + DEPTH / 2.0 + 0.5));
        zones.put("hall.ghoul_nether", new Box(nether.getName(), x0 - 1, y - 1, z0 - 1,
                x0 + 3 * SLOT, y + 10, z0 + DEPTH));
    }

    /** The demon's conversion outfit: it is the leather that makes the fire death count. */
    private static Map<Integer, ItemStack> leather() {
        Map<Integer, ItemStack> layout = new LinkedHashMap<>();
        layout.put(11, new ItemStack(Material.LEATHER_HELMET, 1));
        layout.put(12, new ItemStack(Material.LEATHER_CHESTPLATE, 1));
        layout.put(13, new ItemStack(Material.LEATHER_LEGGINGS, 1));
        layout.put(14, new ItemStack(Material.LEATHER_BOOTS, 1));
        return layout;
    }

    /**
     * The switches at the near end of every hall: how to stop, and how to get out.
     *
     * <p>The sign goes on the very first column of the wall. It used to sit four blocks in, which
     * was the start of the first section's own run of signs - and a section's signs now run to
     * three or four boards, so the entry board was sitting on top of the last of them and eating
     * whatever was written there.
     */
    /**
     * The two steps every race has: a stand-in to hit, and a switch that hands out power.
     *
     * <p>The stand-in used to be an armour stand. It answered a damage event and nothing else -
     * it had no health worth losing and could not be given a race - so a section whose whole
     * point was "watch what this does to a person" could only ever watch a number. {@link
     * Kind#SPAR} calls out the hall's real stand-in player instead: he is a player, he is human,
     * he loses health when he is hit, and the button puts him back to full.
     */
    /**
     * A room of a hall as the entry room's board would name it: that room's own title, without the
     * number the title carries for its own wall.
     *
     * <p>The entry room's two boards say what the two ways into a race are, and they used to say
     * "go to the conversion shrine" in all eight halls - and only one of the eight has a shrine.
     * A ghoul's way in is a corridor in the nether, a demon's is a pool of lava, a witch hunter's
     * is an enrolment, a werewolf's is a bite at night, an enderborn's is a pearl and some clay.
     * Naming the room the board opens cannot go stale when a room is rewritten, which is exactly
     * what the old wording had already done.
     */
    private static String opened(List<Section> sections, int index) {
        if (index < 0 || index >= sections.size()) {
            return "§f第 " + index + " 间";
        }
        return sections.get(index).title().replaceFirst("(§.)\\s*[①②③④⑤⑥⑦⑧⑨]\\s*", "$1");
    }

    /**
     * The room every hall opens with: what this hall is, and the way out of it.
     *
     * <p>This used to be furniture bolted onto the corridor after the fact, at the near end, which
     * is why it once wrote over the first step's signs. It is a room like every other room now,
     * which is what makes it impossible for it to land on top of anything.
     *
     * <p>It is handed the list of the hall's steps because its two ways in name them: the boards
     * carry the titles of the rooms they open, and those rooms do not exist yet when this is
     * built. The list is read when the board is written rather than when this is called, by which
     * time it is complete.
     */
    private Section entrySection(String race, List<Section> sections) {
        // A standard room, and that is the change: it used to be the largest in the hall - four
        // cells, forty-four across, eighteen high - because its far wall was a directory of every
        // other room. Size was a consequence of the wall, and the wall is gone.
        return section("§f入口 · " + title(race), 1, 1, CHAMBER_HEAD, (w, y, x, z, width) -> {
            chest(w, x + 2, y + 1, z + 1, BlockFace.SOUTH, centred(
                    new ItemStack(Material.BREAD, 8)));
            // Two ways into the race, and the three ways out every room has. No directory: the
            // rest of the hall is walked, room by room, each board naming the one after it.
            // Two ways in, and the numbers are not what they look like. Room one is the way in and
            // room two is the kill - not the other way round, which is how this was first written
            // and how it was wrong for every one of the eight halls. The kill room is spliced into
            // the step list at position one, and the entry room is put in front of that list
            // afterwards, so what is one over there is two in here. The check in raceHall says so
            // out loud rather than leaving it to a player to report.
            //
            // Neither board describes its room; each names it, in that room's own words. Seven of
            // the eight races have no shrine at all, and a board that offered one to all of them
            // sent people looking for something that was never built.
            switchpoint(w, y, x + 1, z + 3,
                    Trigger.with(Kind.GO_ROOM, race, race + ":1"),
                    opened(sections, 1), "§7第①间 · 变成这一族");
            switchpoint(w, y, x + 5, z + 3,
                    Trigger.with(Kind.GO_ROOM, race, race + ":2"),
                    opened(sections, 2), "§7第②间 · 被杀也转化");
            switchpoint(w, y, x + 9, z + 3, Trigger.of(Kind.RETURN_START, race),
                    "§f回大厅中央", "§7站回入口正中");
            switchpoint(w, y, x + 13, z + 3, Trigger.of(Kind.EXIT_ROOM),
                    "§c退出测试", "§7回大厅并恢复人类");
            switchpoint(w, y, x + 17, z + 3, Trigger.of(Kind.LEAVE_HALL),
                    "§a离开大厅", "§7去下面的世界");
        }, "这里是" + title(race) + "的测试大厅",
                "一族的能力拆成一间一间的房，一间只讲一件事。进门这间只放两条路，"
                        + "两块牌上写着它们各自去哪一间：一条是这一族自己的入门方式，"
                        + "一条是被这一族杀死。其余房间顺着每间房里的「下一间」一间一间走，"
                        + "每块牌上写着那一间讲什么",
                "点入口那两块牌中的一块，你就站在那一间房的正中间，聊天栏会说出它的名字",
                "进门先看这两块牌：一块是这一族的入门方式，一块是「被杀也转化」",
                "其余房间靠每间房里的「上一间 / 下一间」串着走，按顺序走完就是这一族的全部环节",
                "每间房进门左手边都有一格红色羊毛，上面是退出：按下去回初始大厅并恢复人类",
                "牌子上写着「离开大厅」的那一块，是去下面的玩法世界");
    }

    private Section sparSection(String race) {
        return section("§f陪练 伤害测试", 1, (w, y, x, z, width) -> {
            sparMark(w, race, x + SLOT / 2 + 4, y, z + DEPTH / 2);
            switchpoint(w, y, x + SLOT / 2, z + 3, Trigger.of(Kind.SPAR),
                    "§f放出陪练", "§7他站在你面前，真的会掉血");
        }, "用你的武器打陪练，看到的是真实伤害数字",
                "陪练是一位没有客户端的真人玩家：他会真的掉血、被击退、被闪白，"
                        + "头上飘出来的是插件加成之后的最终伤害。打空了他不会死——再右键一次那块牌就刷回满血",
                "陪练会掉血但不会死；打空了就再右键一次那块牌把他刷新到满血",
                "站好，点「放出陪练」", "用武器打他，数字飘在他头上");
    }

    private Section powerSection(String race) {
        return section("§b能量 +1000", 1, (w, y, x, z, width) -> switchpoint(w, y,
                x + SLOT / 2, z + 3, Trigger.of(Kind.POWER, race),
                "§b能量 +1000", "§7点一下加一千"), "把能量补满，不用先刷到够用",
                "点一下给 1000 能量。这是测试便利，不是种族能力——真实的能量来源是击杀和被动"
                        + "（比如吸血鬼杀生物 +100、杀玩家 +500；狼人杀生物 +20、吃食物 +100）",
                "点一下加 1000 能量", "每个种族的大厅里都有这一个");
    }

    /** A second switch a few blocks behind a section's own, so a costly step is never gated. */
    private void powerButton(World target, int y, int x, int z, String race) {
        switchpoint(target, y, x, z + 6, Trigger.of(Kind.POWER, race),
                "§b能量 +1000", "§7这一格要花能量");
    }

    /**
     * The fall, which most of these races have an opinion about and every one of them words
     * differently - the same drop, four different answers.
     *
     * <p>A hall is a closed box: its ceiling is solid, so a fall taken inside one is a fall of two
     * blocks onto a floor. The drop therefore happens outside, on the ground above the hall - a
     * stone pad is laid down first, so what is landed on is always a pad and never water, leaves
     * or a slope. The number on the health bar is the whole result, and it should not depend on
     * where the hall happens to stand.
     *
     * <p>A second switch is needed because a fall is as one-way as it sounds: the player is on a
     * roof thirteen blocks above the ground, and the way back down is another fall.
     */
    private Section fallSection(String race, int headroom) {
        String ability = switch (race) {
            case "vampire", "ghoul", "angel" ->
                    "免疫摔落伤害：FALL 伤害被直接取消，从任意高度落地都不掉血";
            case "demon" ->
                    "与火、爆炸一起免疫摔落伤害：Demon 的免疫清单里有 FALL";
            case "werewolf" ->
                    "摔落伤害减半：Were.DamageFactor.Fall = 0.5，十点摔落只掉五点";
            case "witchhunter" ->
                    "摔落伤害减免 3 点（下限 0）：WitchHunter.FallReduction = 3，"
                            + "矮处跳下来几乎不疼，高处照样疼";
            default ->
                    "这一族没有任何摔落减免——这里是整个大厅唯一的对照组，"
                            + "同样的高度、同样的落地，掉多少血就是多少";
        };
        String expect = switch (race) {
            case "vampire", "ghoul", "angel", "demon" -> "血条一点不动，摔下来什么事都没有";
            case "werewolf" -> "掉的血是同样高度的一半（十三格大约 10 点，你只掉 5 点）";
            case "witchhunter" -> "比同样的高度少掉 3 点血（十三格大约 10 点，你掉 7 点）";
            default -> "按高度正常掉血（十三格大约掉 10 点），这就是没有减免的样子";
        };
        return section("§6摔落 从高处落下", 2, (w, y, x, z, width) -> {
            int cx = x + 6;
            int cz = z + DEPTH / 2;
            // The drop happens inside a shaft that stands on the hall's own roof: four walls and
            // a lid around a three-by-three well, the landing pad at the bottom, and everything
            // the fall needs on the inside of it. This used to be a patch of stone laid flat on
            // the roof, which has no answer for a player who lands half a block wide of it or
            // takes a step afterwards: they end up on the ground outside a hall they cannot get
            // back into, with the switch that would have brought them back on a wall inside the
            // room they can no longer reach. A well is its own little room. There is nowhere to
            // fall to but the pad, and the way out is on the wall beside it.
            // The height is worked out from the room's own numbers, never asked of the world.
            // `getHighestBlockYAt` looks right and is not: the heightmap is rebuilt lazily, so
            // during the build it still describes whatever stood here before - the ground - and
            // the previous version of this step put its landing pad on the landscape outside the
            // hall, which is exactly where the player ended up, with the way back on a wall
            // inside a room they could no longer reach. The ceiling is at y + headroom + 1.
            int roof = y + headroom + 1;
            int floorY = roof + 1;      // where the player stands after the fall
            int drop = 13;              // ten points of damage for a race with no help
            int legTop = floorY + drop; // where they are put, to let go
            int lid = legTop + 2;

            for (int up = floorY; up <= lid; up++) {
                box(w, Material.STONE_BRICKS, cx - 2, up, cz - 2, cx + 2, up, cz + 2);
            }
            box(w, Material.AIR, cx - 1, floorY, cz - 1, cx + 1, legTop + 1, cz + 1);
            box(w, Material.STONE, cx - 1, floorY - 1, cz - 1, cx + 1, floorY - 1, cz + 1);
            // Fifteen blocks of shaft with no light in it is a hole you cannot find the switch in.
            set(w, Material.SEA_LANTERN, cx, lid, cz);
            set(w, Material.SEA_LANTERN, cx + 2, floorY + 2, cz + 2);
            set(w, Material.SEA_LANTERN, cx - 2, floorY + 2, cz - 2);

            points.put("fall." + race, new Location(w, cx + 0.5, legTop, cz + 0.5));
            points.put("fall.ground." + race, new Location(w, cx + 0.5, y + 1, cz + 0.5));
            switchpoint(w, y, x + 4, z + 3, Trigger.of(Kind.FALL_TEST, race),
                    "§6站到高处", "§7然后放开手落下来");
            // The way back sits inside the well, because that is where the fall leaves you.
            // The well is fifteen blocks of stone with one lamp at the top of it, which is a fine
            // place to fall down and a terrible place to find a switch in - so the switch inside it
            // gets a lamp of its own, in the wall it is mounted on.
            set(w, Material.SEA_LANTERN, cx + 2, floorY, cz);
            // The board that gets you out of the well is hung where boards belong - two over the
            // floor, not one - and carries its own action, so the switch that used to sit at the
            // player's feet with the board above it is gone. The lamp above it is what holds it up
            // and what lights it.
            set(w, Material.SEA_LANTERN, cx + 2, floorY + 2, cz);
            shortSign(w, cx + 1, floorY + 2, cz, BlockFace.WEST, Trigger.of(Kind.BACK_DOWN, race),
                    "§a回到地面", "§7右键这块牌回大厅");
        }, "用一次真实坠落，验这一族的摔落减免",
                ability,
                expect,
                "先确认自己是满血（上一节受过伤就点点「恢复人类」再回来）",
                "点「站到高处」——你会被送进大厅屋顶上的那口石井里",
                "放开手落到井底，看血条掉了多少",
                "右键井壁上那块「回到地面」的牌回大厅，继续后面的环节");
    }

    /**
     * The races that turn the human they finish into one of their own, by a roll.
     *
     * <p>Everything else either has no such rule or reaches its race another way - a demon turns
     * people with netherrack rather than with a kill - so these four are the ones that need a
     * target who can be killed more than once.
     */
    private static final Set<String> KILL_CONVERTS =
            Set.of("vampire", "ghoul", "werewolf", "enderborn");

    /**
     * What the race does to the person it kills.
     *
     * <p>Four races have a chance to turn a human they finish into one of their own, and it is a
     * roll - a third for three of them, a tenth for the enderborn - so a single kill proves
     * nothing either way. The stand-in this brings up therefore comes back where he fell, and the
     * section is about watching the race change rather than about the kill.
     */
    private Section convertSection(String race) {
        String chance = switch (race) {
            case "enderborn" -> "10%（代码里写死的 0.10，没有配置项）";
            default -> "35%（Supernatural.SpreadChance 默认 0.35）";
        };
        String extra = switch (race) {
            case "werewolf" -> "，而且狼人只在夜里才算——白天杀了也白杀，先点旁边的「夜晚」";
            default -> "";
        };
        return section("§5转化 杀死人类", 2, (w, y, x, z, width) -> {
            switchpoint(w, y, x + 3, z + 3,
                    Trigger.with(Kind.SPAWN_TARGET, race, "human"),
                    "§f放出一个人类", "§7他能被反复杀死");
            switchpoint(w, y, x + 7, z + 3,
                    Trigger.with(Kind.USE_ON_NPC, race, "DIAMOND_SWORD:100"),
                    "§c一击打死他", "§7看他的种族变不变");
            switchpoint(w, y, x + 11, z + 3,
                    Trigger.with(Kind.USE_ON_NPC, race, "DIAMOND_SWORD:100"),
                    "§c再打一次", "§7概率的东西要多试");
            powerButton(w, y, x + 15, z, race);
        }, "验证击杀人类时能不能把他也变成这一族",
                "这一族的感染是概率：" + chance + extra
                        + "。中奖时他当场变成这一族，聊天栏会告诉你一声，"
                        + "他头顶和 tab 里的标签也一起改掉。被转化的就是你杀的那个人，"
                        + "所以这一格放的是一个普通人类",
                "连着杀，直到他的标签从「人类」变成这一族。中奖之后他就一直是这一族了："
                        + "再杀不会变回去，也不会再有第二次转化；想重来就再点一次「放出一个人类」",
                "点「放出一个人类」，他就站在你面前，标签写着「人类」",
                "点「一击打死他」；他倒下，两 tick 后又在原地站起来",
                "每杀一次抬头看一眼他的标签：变成这一族就是中奖了",
                "没中就接着点「再打一次」；连着几次不中是正常的，这是概率不是必定",
                "想换一个从头试，再点一次「放出一个人类」，他会变回人类重新开始");
    }

    /**
     * The room where one of the player's own kind kills the player, and the player changes race.
     *
     * <p>Every conversion in this plugin that runs off a death is "a member of this race kills
     * somebody and that somebody becomes one of them", and the only honest way to show the half a
     * player can be on is to let a member of the race actually do it: the dice, the message and the
     * new race all come out of the path a real fight takes. The room stands directly behind the one
     * that made the player what they are, because a player asking "how else could I have ended up
     * as one of these" is standing in that room when they ask it.
     *
     * <p>The races on the list are the ones whose rules count the player as somebody worth turning.
     * That is not the same as the races with a conversion rule, and it is not the same as "everyone
     * except humans": a priest, a hunter and an angel are all counted as people by these rules, and
     * a vampire is not - so this room opens by putting the player back to human and says why.
     *
     * <p>The chances are the plugin's own and each switch names its own. The switch that makes it
     * certain is labelled as what it is: a teaching setting for this room, undone the moment the
     * player leaves it.
     */
    /**
     * Who a kill really turns the victim into, for the room about being killed into a race.
     *
     * <p>Four races turn the person they finish, and they are the four in {@link #KILL_CONVERTS}.
     * The demon turns people too, by a different rule - it needs its victim to be holding netherrack
     * when it lands the blow - and its room keeps the board that hands the netherrack over, so it
     * counts. The other three cannot spread by a kill at all: priest, witch hunter and angel are on
     * the human side of the plugin's own line, and nothing they do makes anybody one of them. What
     * can turn one of those three is a vampire or a ghoul finishing them, so that is who their room
     * summons - and it is the same rule, read the other way round: the room shows the kill that
     * would actually change the visitor.
     */
    private static List<String> killersFor(String race) {
        return switch (race) {
            case "priest", "witchhunter", "angel" -> List.of("vampire", "ghoul");
            default -> List.of(race);
        };
    }

    private Section turnedBySection(String race) {
        // Who this room summons, and the rule behind it: only a race whose kill really does the
        // turning. It used to offer five at once - a vampire, a ghoul, a werewolf, an enderborn and
        // a demon, four of them with conditions of their own - which is five experiments behind one
        // wall of switches. It is the room about the other way in, so it shows that one way.
        List<String> killers = killersFor(race);
        return section("§c被杀 转化", 2, 1, CHAMBER_HEAD, (w, y, x, z, width) -> {
            switchpoint(w, y, x + 1, z + 3, Trigger.of(Kind.RESET_HUMAN, race),
                    "§a先变回人类", "§7被杀死只对人类算");
            switchpoint(w, y, x + 5, z + 3, Trigger.of(Kind.SPREAD),
                    "§c必中开关", "§7本房概率改 100%");
            int column = x + 9;
            for (String killer : killers) {
                // What he holds, and what the roll is worth. A ghoul and a werewolf have no weapon
                // to hold - the plugin will not let those two carry one - and the numbers are the
                // plugin's own: the four kill-conversions at 35% except the enderborn's 10%, and
                // the demon's netherrack rule at 100%.
                String hand = switch (killer) {
                    case "ghoul", "werewolf" -> "";
                    default -> "IRON_SWORD";
                };
                String roll = switch (killer) {
                    case "enderborn" -> "10%";
                    case "demon" -> "100%";
                    default -> "35%";
                };
                switchpoint(w, y, column, z + 3,
                        Trigger.with(Kind.NPC_KILL_ME, race, killer + ":" + hand),
                        "§c被" + title(killer) + "杀死", "§7默认 " + roll + " 变成" + title(killer));
                column += SWITCH_PITCH;
            }
            if (killers.contains("werewolf")) {
                switchpoint(w, y, column, z + 3, Trigger.of(Kind.NIGHT),
                        "§9改成夜晚", "§7狼人只在夜里转化");
                column += SWITCH_PITCH;
            }
            if (killers.contains("demon")) {
                switchpoint(w, y, column, z + 3, Trigger.with(Kind.ARM_ME, race, "NETHERRACK"),
                        "§4手上拿地狱岩", "§7拿着它被打就 100%");
            }
        }, "验证「被杀死，就会变成杀死你的那一族」",
                "被杀死这一条只对「人类阵营」算数（人类、牧师、猎巫人、天使），"
                        + "所以已经从人类变过的人，别人杀你也不会再变一次——这一间因此从「先变回人类」开始。"
                        + "四个族的击杀转化是概率：吸血鬼、食尸鬼、狼人各 35%"
                        + "（Supernatural.SpreadChance 默认 0.35），末影族 10%"
                        + "（代码里写死的一个数，不读那条配置）。「必中开关」把这两个数"
                        + "一起改成 100%，末影族也不例外。"
                        + "恶魔不走概率，走条件：打你的人得是恶魔，而你死的那一刻手上要拿着地狱岩，这样是 100%。"
                        + "狼人还多一个条件：只在他杀你的那一刻是夜晚才算。",
                "你会死一次，然后变成那个种族的身份；聊天栏有提示，头顶和 tab 里的标签也跟着改。"
                        + "本厅的房间还是那些房间，想变回来就回还原房",
                "先点「先变回人类」——被杀死这条只对人类阵营算数",
                "不想等概率就再点「必中开关」，本房两个数（含末影族那个）一起改成 100%，"
                        + "离开这间自动恢复",
                "点「被本族杀死」：他从远处走过来，真的把你打死",
                "看他杀完那一刻你的种族；还不对就再点一次，概率的东西要多试",
                "这一间只放这一族——想试别的族，去那一族的大厅");
    }

    // -- the sections ------------------------------------------------------

    /** The steps of one race's flow, in the order a player should meet them. */
    private List<Section> sectionsFor(String race, int headroom) {
        List<Section> sections = new ArrayList<>();
        switch (race) {
            case "vampire" -> {
                sections.add(section("§c① 转化 成为吸血鬼", 1, (w, y, x, z, width) -> {
                    Shrines.vampire(new Blocks(w), x + 5, y, z + DEPTH / 2);
                    chest(w, x + 2, y + 1, z + DEPTH - 2, BlockFace.NORTH, centred(
                            new ItemStack(Material.MUSHROOM_STEW, 1),
                            new ItemStack(Material.BONE, 10),
                            new ItemStack(Material.GUNPOWDER, 10),
                            new ItemStack(Material.REDSTONE, 10)));
                }, "作为人类走到祭坛上转化为吸血鬼",
                        "感染祭坛的中心是一块金块，判定的是它周围半径 7 格的球体内要有 20 块黑曜石，"
                                + "怎么摆都行；材料是蘑菇煲 1、骨头 10、火药 10、红石 10，"
                                + "四样齐了右键金块。需要权限 supernatural.player.shrineuse.vampire",
                        "材料被收走，你变成吸血鬼：初始 10000 能量，聊天栏出现提示",
                        "在箱子里拿齐四样材料（关箱会自动补满）",
                        "站到黑曜石台子上，右键中间的金块"));
                sections.add(section("§b② 治愈 变回人类", 1, (w, y, x, z, width) -> {
                    Shrines.cure(new Blocks(w), x + 5, y, z + DEPTH / 2);
                    chest(w, x + 2, y + 1, z + DEPTH - 2, BlockFace.NORTH, centred(
                            new ItemStack(Material.WATER_BUCKET, 1),
                            new ItemStack(Material.MILK_BUCKET, 1),
                            new ItemStack(Material.SUGAR, 20),
                            new ItemStack(Material.WHEAT, 20)));
                }, "吸血鬼用治愈祭坛变回人类",
                        "治愈祭坛中心是青金石块，周围半径 7 格的球体内要 20 块萤石；材料是水桶 1、"
                                + "牛奶桶 1、糖 20、小麦 20，右键青金石块。这是吸血鬼唯一的解药，"
                                + "只有吸血鬼能用",
                        "材料被收走，你立刻恢复人类，本区种族被清空",
                        "先变成吸血鬼（回第①节）再回到这一格",
                        "拿齐四样材料，右键中间的青金石块"));
                sections.add(section("§6③ 阳光 与 金头盔", 2, true, (w, y, x, z, width) -> {
                    chest(w, x + 2, y + 1, z + DEPTH - 2, BlockFace.NORTH, centred(
                            new ItemStack(Material.GOLDEN_HELMET, 1)));
                    // The middle of this section has no roof at all: the plugin asks the sky how
                    // much light reaches a player's eyes, and a roof - even a glass one - answers
                    // "none". Nine blocks around the mark are left open so there is somewhere to
                    // stand that is honestly outdoors.
                    box(w, Material.SMOOTH_QUARTZ, x + 10, y, z + 5, x + 14, y, z + 9);
                    points.put("vampire.sun", new Location(w, x + 12.5, y + 1, z + 7.5));
                    switchpoint(w, y, x + 5, z + 3, Trigger.of(Kind.DAY),
                            "§e白天", "§7然后站到露天台上");
                    switchpoint(w, y, x + 8, z + 3, Trigger.of(Kind.NIGHT),
                            "§9夜晚", "§7夜里安全");
                    switchpoint(w, y, x + 11, z + 3, Trigger.of(Kind.SUN_SPOT, "vampire"),
                            "§6站到露天台", "§7这一格头顶 9 格没有屋顶");
                    switchpoint(w, y, x + 14, z + 3, Trigger.of(Kind.TOGGLE_RAIN, "vampire"),
                            "§9切换下雨", "§7雨也是遮挡的一种");
                    powerButton(w, y, x + 2, z, "vampire");
                }, "验证阳光燃烧，以及它会因为什么停下来",
                        "白天 + 头顶没有方块遮挡 + 没有戴金头盔时，吸血鬼会持续着火掉血。"
                                + "一共七种情况不会烧：夜晚、下雨、泡在水里、头顶有方块、戴金头盔、"
                                + "身处下界、有 supernatural.player.preventsundamage 权限",
                        "白天站在露天台上会着火；戴上金头盔、或改成夜晚、或让它下雨，都不再着火。"
                                + "只影响本大厅自己的世界",
                        "先点「白天」，再点「站到露天台」，看身上有没有起火",
                        "拿箱子里的金头盔戴上，重复一次对照",
                        "点「切换下雨」，露天淋着雨也不再着火"));
                sections.add(section("§a④ 食物 不回饱食度", 2, (w, y, x, z, width) -> {
                    largeChest(w, x + 4, y + 1, z + DEPTH - 2, foodList());
                    switchpoint(w, y, x + 9, z + 3, Trigger.of(Kind.CLEAR_HUNGER),
                            "§7清空饱食度", "§7方便看杀怪回饱食");
                }, "确认吸血鬼吃普通食物没有用",
                        "插件拦的是「吞下去的那一刻」：配置里列出的任何食物都不加饱食度、也不消耗物品，"
                                + "只提示「吸血鬼必须喝血」。箱子里的 41 样就是那份清单",
                        "吃东西的动作照常播放，但饱食度一动不动，食物也还在背包里",
                        "点「清空饱食度」让自己饿着",
                        "拿着箱子里的食物右键空气，看聊天栏提示",
                        "再看一眼饱食度：还是 0"));
                sections.add(section("§2⑤ 杀鸡 回饱食度", 1, (w, y, x, z, width) ->
                        switchpoint(w, y, x + SLOT / 2, z + 3, Trigger.of(Kind.SPAWN_CHICKEN),
                                "§a放出一只鸡", "§7它不会动，杀掉看饱食度"),
                        "吸血鬼真正补饱食度的方式是击杀生物",
                        "杀一只生物回 2 点饱食度、加 100 能量；杀一名玩家回 3 点、加 500 能量。"
                                + "这是替代吃饭的那条路",
                        "杀掉鸡以后饱食度上升 2 点，能量 +100",
                        "右键那块牌放出一只鸡（旧的会被清掉）", "把它杀掉，回头看饱食度"));
                sections.add(section("§5⑥ 休战 敌对怪物", 4, true, 4, (w, y, x, z, width) -> {
                    // The arena floor, across the room rather than the strip a small room's middle
                    // would be: the monsters are laid out in rings around the middle of the room,
                    // and a ring drawn on two different floors reads as two different rooms.
                    box(w, Material.BLACKSTONE, x + 2, y, z + 2, x + width - 3, y, z + width - 3);
                    switchpoint(w, y, x + 4, z + 3, Trigger.of(Kind.SPAWN_MONSTERS, "vampire"),
                            "§c召唤全部敌对怪物", "§7看它们打不打你");
                    switchpoint(w, y, x + 8, z + 3, Trigger.of(Kind.CLEAR_MONSTERS, "vampire"),
                            "§a清空怪物", "§7离开测试也会自动清");
                }, "验证休战契约：未破约时敌对怪物不该主动攻击吸血鬼",
                        "默认与苦力怕、骷髅、僵尸、末影人、蜘蛛停战：它们既不主动选你做目标，"
                                + "打出去的伤害也会被拒绝。你打它们任意一下就算破约，"
                                + "破约后 120 秒（2 分钟）自动恢复",
                        "全部怪物站在原地不追你；你打它们任意一下，它们立刻开始还手",
                        "点「召唤全部敌对怪物」，站着别动，观察它们是否追你",
                        "打其中一只一下，再站着不动，看它是否开始反击",
                        "点「清空怪物」收拾干净（离开测试也会清）"));
                sections.add(section("§d⑦ 道具 跳与传送", 2, (w, y, x, z, width) -> {
                    chest(w, x + 2, y + 1, z + DEPTH - 2, BlockFace.NORTH, centred(
                            new ItemStack(Material.POPPY, 8),
                            new ItemStack(Material.BOOK, 4)));
                    for (int step = 0; step < 4; step++) {
                        box(w, Material.POLISHED_BLACKSTONE, x + 7 + step * 2,
                                y + 1 + step * 3, z + 9, x + 7 + step * 2,
                                y + 1 + step * 3, z + 10);
                    }
                    switchpoint(w, y, x + 5, z + 3, Trigger.of(Kind.RESET_TELEPORT, "vampire"),
                            "§d重置传送点", "§7重设成大厅里这个位置");
                    powerButton(w, y, x + 8, z, "vampire");
                }, "验证传送道具与超级跳，并且传送点逃不出大厅",
                        "红玫瑰左键＝超级跳，耗 1000 能量；红玫瑰右键＝把脚下存成传送点，不花能量；"
                                + "书左键＝传回存点，耗 9000 能量并消耗 1 本书。"
                                + "传送点只在存下它的那个世界有效，跨世界会被拒绝",
                        "书左键会传回存点；点「重置传送点」后存点永远在大厅里，出不去",
                        "拿红玫瑰：左键超级跳一次，右键存一个传送点",
                        "拿书左键传回存点，再用「重置传送点」把它钉回大厅"));
                sections.add(section("§c⑧ 溺水 与 木剑", 2, (w, y, x, z, width) -> {
                    // The room's own floor is the pool's rim: the water sits level with it and has
                    // two layers of blackstone under it, because a hall in the void has no ground
                    // to dig into.
                    box(w, Material.POLISHED_BLACKSTONE_BRICKS, x + 7, y - 2, z + 6,
                            x + 15, y, z + 12);
                    box(w, Material.WATER, x + 9, y - 1, z + 8, x + 13, y, z + 10);
                    points.put("vampire.pool", new Location(w, x + 11.5, y, z + 9.5));
                    chest(w, x + 2, y + 1, z + DEPTH - 2, BlockFace.NORTH, centred(
                            new ItemStack(Material.WOODEN_SWORD, 1),
                            new ItemStack(Material.IRON_SWORD, 1)));
                    switchpoint(w, y, x + 2, z + 3, Trigger.of(Kind.SPAR),
                            "§f叫陪练过来", "§7他手上先拿铁剑，站到你面前");
                    switchpoint(w, y, x + 5, z + 3,
                            Trigger.with(Kind.TEST_HIT, "vampire", "IRON_SWORD"),
                            "§f挨一下铁剑", "§7他的剑会换成铁剑");
                    switchpoint(w, y, x + 8, z + 3,
                            Trigger.with(Kind.TEST_HIT, "vampire", "WOODEN_SWORD"),
                            "§b挨一下木剑", "§7他的剑会换成木剑，更疼");
                    switchpoint(w, y, x + 11, z + 3, Trigger.of(Kind.DIP_WATER, "vampire"),
                            "§9跳进水里", "§7等它先烧能量再掉血");
                    powerButton(w, y, x + 14, z, "vampire");
                }, "验证吸血鬼的两条被动：木制武器会打得更疼，溺水可以用能量顶",
                        "普通近战打过来只吃 80% 的伤害（配置 0.8 是减伤系数）；"
                                + "但如果武器是木制的（木剑、木棍、弓），伤害反而额外 +150%。"
                                + "溺水伤害每 1 点消耗 90 能量，能量不足 90 时照常掉血",
                        "木剑打你比铁剑更疼；站在水里能量持续下降，能量耗尽之后才开始真的掉血",
                        "点「叫陪练过来」——他手上拿的是铁剑，先看清这一点",
                        "点「挨一下木剑」：他的剑会当场换成木剑，再砍你一下",
                        "点「挨一下铁剑」换回铁剑对比：同一把陪练，两次掉血不一样",
                        "点「跳进水里」站着别动：先看能量掉，再看血开始掉"));
            }
            case "ghoul" -> {
                sections.add(section("§2① 转化 下界僵尸猪人", 1, (w, y, x, z, width) -> {
                    chest(w, x + 2, y + 1, z + DEPTH - 2, BlockFace.NORTH, centred(
                            new ItemStack(Material.ROTTEN_FLESH, 8)));
                    switchpoint(w, y, x + SLOT / 2, z + 3, Trigger.of(Kind.GO_NETHER, "ghoul"),
                            "§4进入下界转化室", "§7主世界的僵尸杀你没用");
                }, "食尸鬼只有一个入口：在下界被僵尸猪人杀死",
                        "插件把两个条件都写死了：死亡地点必须在下界，凶手必须是僵尸猪人。"
                                + "主世界的普通僵尸、或者在下界死于别的原因，都不会转化——"
                                + "大厅为此专门在下界建了一间小室",
                        "你一定会变成食尸鬼；这条路不掷概率，转化率是 100%",
                        "点「进入下界转化室」，那间屋子在另一头",
                        "在那边的格子里点「放出一只僵尸猪人」，站着别动等它打死你",
                        "死后用「回到主世界大厅」回来，再回到这一格继续"));
                sections.add(section("§f② 教会 净化回人类", 2, (w, y, x, z, width) -> {
                    Shrines.priest(new Blocks(w), x + 5, y, z + DEPTH / 2);
                    priestAltars.add(new Location(w, x + 5.5, y + 1, z + DEPTH / 2.0 + 0.5));
                    // A pillar over the altar, so the one thing that undoes a ghoul is the one
                    // thing in the hall that is impossible to walk past.
                    for (int up = 1; up <= 3; up++) {
                        set(w, Material.LIGHTNING_ROD, x + 5, y + up, z + DEPTH / 2 - 2);
                    }
                    set(w, Material.SEA_LANTERN, x + 5, y + 4, z + DEPTH / 2 - 2);
                    chest(w, x + 2, y + 1, z + DEPTH - 2, BlockFace.NORTH, centred(
                            new ItemStack(Material.GOLD_INGOT, 4),
                            new ItemStack(Material.GLOWSTONE_DUST, 4),
                            new ItemStack(Material.REDSTONE, 8),
                            new ItemStack(Material.BREAD, 30)));
                    switchpoint(w, y, x + 10, z + 3, Trigger.of(Kind.RESET_HUMAN, "ghoul"),
                            "§f净化 · 恢复人类", "§7走插件自己的治愈代码");
                }, "验证食尸鬼只能靠教会净化回人类",
                        "教会祭坛的中心是钻石块，位置由管理员设（本大厅已经把它全部登记成教会）。"
                                + "非牧师的超自然单位右键它会当场被圣光杀死；食尸鬼额外多一次机会："
                                + "以 25% 的概率（SpreadChance 0.35 减 0.1）直接恢复人类。"
                                + "食尸鬼自己没有任何解药",
                        "右键祭坛后你当场死一次，有机会直接变回人类；没变的话再右键旁边那块牌",
                        "先变成食尸鬼（回第①节）",
                        "走到发光柱子下的钻石块旁边，右键它，看聊天栏",
                        "没变回人类就点「净化 · 恢复人类」，那走的是插件自己的治愈代码"));
                sections.add(section("§b③ 道具 猪肉与骨头", 2, (w, y, x, z, width) -> {
                    chest(w, x + 2, y + 1, z + DEPTH - 2, BlockFace.NORTH, centred(
                            new ItemStack(Material.PORKCHOP, 8),
                            new ItemStack(Material.BONE, 8),
                            new ItemStack(Material.IRON_SWORD, 1)));
                    powerButton(w, y, x + 9, z, "ghoul");
                }, "把这一族的两样耗能道具发到手里",
                        "猪排左键召唤一只僵尸，耗 1000 能量；骨头左键攻击一个非人类的超自然玩家"
                                + "可以建立「不洁纽带」——那条能力有自己的环节，这里只是发道具。"
                                + "铁剑是给后面武器禁令那一节比对照用的",
                        "猪排能召出僵尸；骨头和铁剑留着后面几节用",
                        "先点一次「能量 +1000」",
                        "拿猪排左键空气，看有没有僵尸出现",
                        "骨头和铁剑收进背包，后面几节要用"));
                sections.add(section("§5④ 休战 僵尸系怪物", 4, true, 4, (w, y, x, z, width) -> {
                    // Forty-one monsters in rings around the middle of the room - exactly the step
                    // the vampire hall has, on exactly the same list, and it takes four cells for
                    // exactly the same reason: the outermost ring is sixteen blocks out, and a
                    // twenty-one wide room has eight to give. What that looked like was a room whose
                    // monsters were stacked in its walls, and, for the ones the clamp pushed past
                    // the wall entirely, monsters standing in the rooms next door before anybody
                    // had called them.
                    box(w, Material.MOSS_BLOCK, x + 2, y, z + 2, x + width - 3, y, z + width - 3);
                    switchpoint(w, y, x + 4, z + 3, Trigger.of(Kind.SPAWN_MONSTERS, "ghoul"),
                            "§c召唤僵尸系怪物", "§7看它们打不打你");
                    switchpoint(w, y, x + 8, z + 3, Trigger.of(Kind.CLEAR_MONSTERS, "ghoul"),
                            "§a清空怪物", "§7离开测试也会自动清");
                }, "验证食尸鬼与僵尸家族的休战契约",
                        "默认与僵尸、僵尸村民、溺尸、尸壳、僵尸猪人、巨人停战：它们既不主动选你"
                                + "做目标，打出去的伤害也会被拒绝。打任意一只一下就算破约，"
                                + "120 秒后自动恢复",
                        "它们都不主动攻击；你打任意一只立刻破约，它开始还手",
                        "点「召唤僵尸系怪物」，站着不动观察",
                        "打其中一只一下，看它是否立刻反击",
                        "打完点「清空怪物」收尾"));
                sections.add(section("§9⑤ 露天 阳光与下雨", 2, true, (w, y, x, z, width) -> {
                    // No roof over this section: the plugin asks the sky, and both tests are
                    // about the sky - the sun for a ghoul's regeneration, the rain for the
                    // rule that switches that regeneration off.
                    box(w, Material.MOSSY_STONE_BRICKS, x + 9, y, z + 5, x + 14, y, z + 9);
                    points.put("ghoul.sun", new Location(w, x + 11.5, y + 1, z + 7.5));
                    // ... and one covered porch, because "under a roof" is the other half of the
                    // rule and it used to mean walking back into a neighbouring section. Four
                    // blocks away is far enough that a reader who stays put cannot tell a rule
                    // that answered from a rule that never ran - which is most of why this step
                    // read as broken. Two switches, two marks, one health bar.
                    box(w, Material.MOSSY_STONE_BRICKS, x + 16, y + 3, z + 6, x + 18, y + 3, z + 8);
                    box(w, Material.MOSSY_STONE_BRICKS, x + 16, y + 1, z + 6, x + 16, y + 2, z + 6);
                    box(w, Material.MOSSY_STONE_BRICKS, x + 18, y + 1, z + 6, x + 18, y + 2, z + 6);
                    box(w, Material.MOSSY_STONE_BRICKS, x + 16, y + 1, z + 8, x + 16, y + 2, z + 8);
                    box(w, Material.MOSSY_STONE_BRICKS, x + 18, y + 1, z + 8, x + 18, y + 2, z + 8);
                    points.put("ghoul.roof", new Location(w, x + 17.5, y + 1, z + 7.5));
                    switchpoint(w, y, x + 3, z + 3, Trigger.of(Kind.SUN_SPOT, "ghoul"),
                            "§e站到露天台", "§7头顶完全没屋顶");
                    switchpoint(w, y, x + 6, z + 3, Trigger.of(Kind.ROOF_SPOT, "ghoul"),
                            "§a站到有顶处", "§7这四格在棚子底下");
                    switchpoint(w, y, x + 9, z + 3, Trigger.of(Kind.TOGGLE_RAIN, "ghoul"),
                            "§9切换下雨", "§7雨会真的落到你身上");
                    switchpoint(w, y, x + 12, z + 3, Trigger.of(Kind.HURT),
                            "§c先被打掉血", "§7不然没血可回，看不出回血");
                    powerButton(w, y, x + 2, z, "ghoul");
                }, "验证食尸鬼的回血条件：有顶、且不下雨才回血",
                        "回血每 10 秒 1 点，一次 1 点，所以要看血条上那条绿线慢慢涨。"
                                + "但雨在下、你头顶又没有遮挡时，这条路整个不生效——"
                                + "判定看的是天空，玻璃顶也算遮挡。它同时也免疫摔落伤害",
                        "露天淋雨时完全不回血；挪到有顶的地方，10 秒后开始每 10 秒回 1 点",
                        "先点「先被打掉血」，血量掉到一半",
                        "站到露天台→点「切换下雨」→等 20 秒，看血条不动",
                        "点「站到有顶处」再等 20 秒，看它开始回血（雨不用关）"));
                sections.add(section("§b⑥ 钻石剑 伤害 0", 2, (w, y, x, z, width) -> {
                    chest(w, x + 2, y + 1, z + DEPTH - 2, BlockFace.NORTH, centred(
                            new ItemStack(Material.DIAMOND_SWORD, 1),
                            new ItemStack(Material.IRON_SWORD, 1)));
                    switchpoint(w, y, x + 6, z + 3, Trigger.of(Kind.SPAR),
                            "§f叫陪练过来", "§7他手上先拿铁剑");
                    switchpoint(w, y, x + 9, z + 3,
                            Trigger.with(Kind.TEST_HIT, "ghoul", "DIAMOND_SWORD"),
                            "§b挨一下钻石剑", "§7他的剑换成钻石剑，你不掉血");
                    switchpoint(w, y, x + 12, z + 3,
                            Trigger.with(Kind.TEST_HIT, "ghoul", "IRON_SWORD"),
                            "§f挨一下铁剑", "§7他的剑换回铁剑，真的掉血");
                }, "验证食尸鬼对钻石剑 / 下界合金剑免疫",
                        "默认免疫名单是下界合金剑与钻石剑：打在身上 0 伤害。其他近战武器正常生效，"
                                + "而且你还有 35% 的减伤（配置 0.65 是减伤系数）",
                        "钻石剑打在身上是 0 伤害，铁剑是正常伤害",
                        "点「叫陪练过来」让他站到你面前",
                        "依次点「挨一下钻石剑」和「挨一下铁剑」对比"));
                sections.add(section("§7⑦ 武器禁令", 1, (w, y, x, z, width) -> {
                    chest(w, x + 2, y + 1, z + DEPTH - 2, BlockFace.NORTH, centred(
                            new ItemStack(Material.IRON_SWORD, 1),
                            new ItemStack(Material.GOLDEN_AXE, 1),
                            new ItemStack(Material.DIAMOND_PICKAXE, 1)));
                    switchpoint(w, y, x + 6, z + 3, Trigger.of(Kind.SPAR),
                            "§f放出陪练", "§7他会站在你面前给你打");
                    powerButton(w, y, x + 9, z, "ghoul");
                }, "验证食尸鬼不能用常规武器",
                        "默认禁用清单：弓、木棍，以及木 / 石 / 铁 / 金 / 钻 / 铜 / 下界合金的剑、斧、镐、"
                                + "锹、锄。拿着它们攻击，伤害直接被判成 0；空手不受限制",
                        "拿这三样武器打陪练，伤害数字全是 0",
                        "点「放出陪练」",
                        "拿箱子里的剑 / 斧 / 镐分别打他一下，看伤害数字",
                        "空手再打一下对照：正常伤害"));
                sections.add(section("§9⑧ 水里 持续掉血", 3, (w, y, x, z, width) -> {
                    box(w, Material.MOSSY_STONE_BRICKS, x + 7, y - 2, z + 6,
                            x + 15, y, z + 12);
                    box(w, Material.WATER, x + 9, y - 1, z + 8, x + 13, y, z + 10);
                    points.put("ghoul.pool", new Location(w, x + 11.5, y, z + 9.5));
                    // A boat floating on the pool. Sitting in one is the only thing that exempts a
                    // ghoul from the water, and an exemption nobody can try is an exemption nobody
                    // has tested - this keeps the line on the sign from being just a claim.
                    //
                    // Two blocks above the water, because a boat put down anywhere else does not
                    // end up on it. The pool is two deep - it fills y-1 and y - so the waterline is
                    // the top face of y, at y+1, and a boat dropped into the pool settles about half
                    // a block below that line, which is the position the game itself gives a boat
                    // here. A boat placed at y does not: it stays exactly at y, a whole block lower,
                    // with its hull short of the waterline from end to end, and no boat on this
                    // server floats back up out of the water on its own - so where it is put down is
                    // where it stays. That is the whole of "the boat is underwater and I cannot get
                    // in", measured on this pool: placed at y it sits at -19.0 under a -18.0
                    // waterline; dropped in from above it comes to rest at -18.48.
                    //
                    // Spawned by type, not by class: Boat is an abstract base on this server and
                    // asking for one by class throws while the plugin is still enabling.
                    //
                    // Tagged, and remembered, because this boat is the whole of the step it
                    // belongs to and a boat is not a block: nothing in repair() would put it back.
                    Location mark = new Location(w, x + 10.5, y + 2, z + 9.5);
                    Entity boat = w.spawnEntity(mark, EntityType.OAK_BOAT);
                    boat.setPersistent(true);
                    boat.addScoreboardTag(MOORED);
                    moored.add(new Moored(w.getName(), mark.getX(), mark.getY(), mark.getZ(),
                            EntityType.OAK_BOAT));
                    switchpoint(w, y, x + 2, z + 3, Trigger.of(Kind.DIP_WATER, "ghoul"),
                            "§9跳进水里", "§7站着别动，等它掉血");
                    switchpoint(w, y, x + 5, z + 3, Trigger.of(Kind.RESTORE_HEALTH),
                            "§a先回满血再看", "§7免得死在池子里");
                    powerButton(w, y, x + 8, z, "ghoul");
                }, "验证食尸鬼在水里会持续溶解，以及船里为什么不会",
                        "脚下方块是水时，每秒掉 4 点血（不是溺水，是碰到水就掉；判定每 1 秒跑一次、"
                                + "没有节流）。所以满血 20 点也只撑得住 5 秒——这一条比看上去凶得多。"
                                + "坐上池子里那条船就完全不掉血；有 "
                                + "supernatural.player.preventwaterdamage 权限也免疫",
                        "站在水里血条每秒掉 4 点，几秒钟就见底；上岸立刻停；坐进船里一点不掉",
                        "先点「先回满血再看」，半血进去根本来不及看",
                        "点「跳进水里」，数到 3 就自己上岸，看血条少了多少",
                        "右键池子里那条船坐上去（对着船点，不是对着水面——对着水面是又放一条），"
                                + "再泡 10 秒，确认血条一点不动",
                        "下船再泡一次，几秒就会被「溶解」死"));
                sections.add(section("§2⑨ 纽带 伤害分担", 3, (w, y, x, z, width) -> {
                    chest(w, x + 2, y + 1, z + DEPTH - 2, BlockFace.NORTH, centred(
                            new ItemStack(Material.BONE, 6),
                            new ItemStack(Material.IRON_SWORD, 1)));
                    // "super", not "random": a bond needs somebody the plugin counts as
                    // supernatural to attach to, and "random" drew from all eight - a run that
                    // rolled a priest, a witch hunter or an angel left the room with nothing
                    // bonded and nothing to see, which the board had already promised otherwise.
                    switchpoint(w, y, x + 6, z + 3,
                            Trigger.with(Kind.SPAWN_NPCS, "ghoul", "super,super"),
                            "§c放出两个同伴", "§7随机超自然种族");
                    switchpoint(w, y, x + 10, z + 3,
                            Trigger.with(Kind.USE_ON_NPC, "ghoul", "BONE"),
                            "§2用骨头建立纽带", "§7打其中一个");
                    switchpoint(w, y, x + 14, z + 3,
                            Trigger.with(Kind.NPC_HITS_NPC, "ghoul", "6"),
                            "§4让另一个同伴打他", "§7对比两边的血");
                    powerButton(w, y, x + 18, z, "ghoul");
                }, "看清「不洁纽带」到底替对方做了什么",
                        "拿骨头左键攻击一个非人类的超自然玩家就能建立纽带，消耗 1 根骨头；"
                                + "对人类不成立（会提示 form a bond with a human）。建立之后他每次挨打："
                                + "最终伤害先减半，另一半点数按你自己的 0.65 减伤算完，从你身上扣掉，"
                                + "并且每分担一次再扣你 50 点能量。双方必须同一世界、相距 ≤10 格，"
                                + "你能量要够；一个食尸鬼同时只能绑一个人",
                        "第二个同伴砍第一个的那一下：被打的只掉一半血，你自己同时掉一截，能量 −50",
                        "点「放出两个同伴」——他们是随机一个超自然种族，人类不行",
                        "点「用骨头建立纽带」，聊天栏出现羁绊提示",
                        "点「让另一个同伴打他」，对比被打的人掉了多少、你掉了多少",
                        "想自己动手也行：拿武器打其中一个，效果一样"));
            }
            case "werewolf" -> {
                sections.add(section("§6① 转化 夜里被狼咬死", 1, (w, y, x, z, width) -> {
                    // x+3 belongs to the entry station, which every hall writes last: a switch
                    // there is overwritten, button and sign both, and this section's "night"
                    // button was quietly replaced by the entry station's "back to human" one.
                    switchpoint(w, y, x + 4, z + 3, Trigger.of(Kind.NIGHT),
                            "§9夜晚", "§7先调成夜里");
                    switchpoint(w, y, x + 7, z + 3, Trigger.of(Kind.PREPARE_WEREWOLF),
                            "§a放出一只狼", "§7它咬死你就会转化");
                }, "让未驯服的狼在夜里咬死你，验证死亡转化",
                        "两个条件缺一不可：咬死你的是未驯服的野狼，而且当时是夜里"
                                + "（世界时间大于 12400 就是夜晚）。白天放不出狼，白天死也不算",
                        "你会变成狼人；白天脚本会直接拒绝放狼，并提示你先点「夜晚」",
                        "先点「夜晚」把本大厅调成夜里",
                        "再点「放出一只狼」，站着等它咬死你"));
                sections.add(section("§a② 解药 狼毒解药", 2, (w, y, x, z, width) -> {
                    largeChest(w, x + 4, y + 1, z + DEPTH - 2, wereWolfbaneList());
                    switchpoint(w, y, x + 10, z + 3, Trigger.of(Kind.DAY),
                            "§e白天", "§7狼毒只能在白天做");
                    powerButton(w, y, x + 6, z, "werewolf");
                }, "验证狼毒解药：只能在白天做，做完直接恢复人类",
                        "配方不止常见的那几样——默认是 20 种：蒲公英、虞美人、红蘑菇、棕蘑菇各 10 个，"
                                + "另加 15 种花（绒球葱、蓝花美耳草、兰花、闭眼绒球花、矢车菊、金蒲公英、"
                                + "铃兰、睁眼绒球花、橙色郁金香、滨菊、粉色郁金香、红色郁金香、火把花、"
                                + "白色郁金香、凋灵玫瑰）和 1 个碗，各 1 个。箱子里的东西是直接照插件配置生成的，"
                                + "所以永远不会和配方对不上。手持碗左键，材料齐了才生效，"
                                + "并需要 supernatural.player.wolfbane 权限。只能白天做——夜里左键不会被受理",
                        "材料被扣掉，人立刻变回人类",
                        "先把本大厅点成白天",
                        "拿走箱子里的全部材料，手持碗左键一次"));
                sections.add(section("§e③ 夜间道具", 1, (w, y, x, z, width) -> {
                    chest(w, x + 2, y + 1, z + DEPTH - 2, BlockFace.NORTH, centred(
                            new ItemStack(Material.PORKCHOP, 8),
                            new ItemStack(Material.FEATHER, 8),
                            new ItemStack(Material.COOKED_BEEF, 16)));
                    switchpoint(w, y, x + 6, z + 3, Trigger.of(Kind.NIGHT),
                            "§9夜晚", "§7这些都要夜里用");
                    switchpoint(w, y, x + 9, z + 3, Trigger.of(Kind.DAY),
                            "§e白天", "§7白天它们全都不可用");
                    powerButton(w, y, x + 12, z, "werewolf");
                }, "验证狼人的三件夜间道具，以及它们白天确实失效",
                        "猪排左键＝召唤一只狼并驯服它，耗 2000，最多同时控制 5 只；"
                                + "羽毛左键＝朝面朝方向冲刺，耗 400；吃食物右键＝回饱食度并 +100 能量，"
                                + "但面包例外。这三样只在夜里生效",
                        "夜里三样都能用；换成白天再点，它们要么没反应、要么直接提示不可用",
                        "先点「夜晚」，依次试猪排（左键）、羽毛（左键）、食物（右键）",
                        "再点「白天」，把同样三样再试一遍，对照"));
                sections.add(section("§c④ 夜间武器限制", 1, (w, y, x, z, width) -> {
                    chest(w, x + 2, y + 1, z + DEPTH - 2, BlockFace.NORTH, centred(
                            new ItemStack(Material.BOW, 1),
                            new ItemStack(Material.IRON_SWORD, 1),
                            new ItemStack(Material.STONE_AXE, 1)));
                    sparMark(w, "werewolf_night", x + 8, y, z + DEPTH / 2);
                    switchpoint(w, y, x + 4, z + 3, Trigger.of(Kind.NIGHT),
                            "§9夜晚", "§7再拿武器打陪练");
                    switchpoint(w, y, x + 6, z + 3, Trigger.of(Kind.SPAR),
                            "§f放出陪练", "§7他站在你面前给你打");
                    switchpoint(w, y, x + 10, z + 3, Trigger.of(Kind.DAY),
                            "§e白天", "§7对照：白天没有限制");
                    powerButton(w, y, x + 12, z, "werewolf");
                }, "验证狼人夜里不能用弓和多数剑斧镐",
                        "禁用的是木 / 石 / 铁 / 金 / 钻 / 铜 / 下界合金的剑、斧、镐、锹、锄，"
                                + "外加弓和木棍：夜里拿着它们近战，伤害是 0。"
                                + "狼人自己那 5 倍近战加成也只在夜里生效，白天两者一起消失",
                        "夜里拿这些武器打陪练是 0 伤害；改成白天，同样的武器是正常伤害",
                        "点「夜晚」再点「放出陪练」",
                        "拿箱子里的弓 / 铁剑 / 石斧分别打他一下，看伤害数字",
                        "点「白天」，拿铁剑再打一次，对照"));
                sections.add(section("§6⑤ 与狼停战", 3, true, (w, y, x, z, width) -> {
                    box(w, Material.SPRUCE_PLANKS, x + 3, y, z + 5, x + width - 4, y, z + DEPTH - 3);
                    switchpoint(w, y, x + 4, z + 3, Trigger.of(Kind.SPAWN_MONSTERS, "werewolf"),
                            "§c放出三只愤怒的野狼", "§7它们本该冲你来");
                    switchpoint(w, y, x + 8, z + 3, Trigger.of(Kind.CLEAR_MONSTERS, "werewolf"),
                            "§a清空怪物", "§7离开测试也会自动清");
                }, "验证狼人不会被狼咬伤",
                        "Were.WolfTruce 打开时，未驯服的狼不会把狼人选作目标，就算它已经扑上来，"
                                + "它打出的伤害也会被拒绝——休战是两道门。野狼自己那股怒气不写在"
                                + "「目标」这个字段里，所以大厅直接指定了目标：这三只狼是真的会来咬你的，"
                                + "要看的只有一件事——咬不咬得动",
                        "狼会真的扑上来咬你（不是站着看），但你一点血都不掉；"
                                + "把自己恢复成人类、再右键一次同一块牌，同样三只狼咬下去就会掉血",
                        "点「放出三只愤怒的野狼」，站着别动，让它们咬你，看血条",
                        "右键入口那排的「恢复人类」，再右键一次同一块牌，对照：这次会掉血",
                        "点「清空怪物」收尾"));
                sections.add(section("§6⑥ 夜晚 自动回血", 3, (w, y, x, z, width) -> {
                    switchpoint(w, y, x + 3, z + 3, Trigger.of(Kind.HURT),
                            "§c先打掉半血", "§7不然看不出回血");
                    switchpoint(w, y, x + 6, z + 3, Trigger.of(Kind.NIGHT),
                            "§9夜晚", "§7回血只在夜里");
                    switchpoint(w, y, x + 9, z + 3, Trigger.of(Kind.DAY),
                            "§e白天", "§7对照：白天不回血");
                    powerButton(w, y, x + 12, z, "werewolf");
                }, "验证狼人的自动回血只在夜里发生",
                        "Were.Time.HealthGained = 0.2/秒，每 5 秒结算一次，且只在夜里触发；"
                                + "白天同样掉着血也一动不动。这是狼人白天弱势的一部分——"
                                + "攻击加成、武器禁用、回血，三样都绑在时刻上",
                        "夜里半血会一格一格涨回来；把时间改成白天，同样的半血就停在那里",
                        "点「先打掉半血」",
                        "点「夜晚」，安静站着，看血条每 5 秒涨一点",
                        "点「白天」，再等半分钟，确认血条不再动"));
            }
            case "demon" -> {
                sections.add(section("§4① 下界 岩浆转化", 2, (w, y, x, z, width) -> {
                    chest(w, x + 2, y + 1, z + DEPTH - 2, BlockFace.NORTH, centred(
                            new ItemStack(Material.NETHERRACK, 8)));
                    switchpoint(w, y, x + 4, z + 3, Trigger.of(Kind.GO_NETHER, "demon"),
                            "§4进入下界大厅", "§7穿着皮革死在里面");
                    powerButton(w, y, x + 8, z, "demon");
                }, "验证恶魔必须死在下界的火里才能转化",
                        "三个条件同时成立才转化：①死亡原因是岩浆、火或燃烧；②死亡地点在下界；"
                                + "③身上穿着整套皮革（或者之前穿着皮革走过下界传送门登记过申请）。"
                                + "缺一条都不行——主世界的火再大也只是白死",
                        "死后成为恶魔；那块牌把你送进下界的转化厅，那里有整套皮革和岩浆池",
                        "点「进入下界大厅」",
                        "在那边从箱子里拿走整套皮革穿上",
                        "跳进岩浆（死后即转化），再用「回到主世界大厅」回来"));
                sections.add(section("§c② 火球与束缚", 1, (w, y, x, z, width) -> {
                    chest(w, x + 2, y + 1, z + DEPTH - 2, BlockFace.NORTH, centred(
                            new ItemStack(Material.REDSTONE, 16),
                            new ItemStack(Material.INK_SAC, 8)));
                    powerButton(w, y, x + 6, z, "demon");
                }, "验证恶魔的两个主世界能力：火球与蜘蛛网束缚",
                        "红石左键＝射出火球，耗 2000；只在允许 PvP 的区域可用，"
                                + "威力被压到不破坏地形，命中处 3 格内造成 10 点伤害并点燃。"
                                + "墨囊左键＝在你指的位置生成 3×3×3 的蜘蛛网，耗 1000，"
                                + "10 秒后插件自己清掉，不会留在地上",
                        "火球飞出去，落点有伤害和火；蜘蛛网出现在你指的地方，10 秒后消失",
                        "拿箱子里的红石左键一次",
                        "拿墨囊对着地面左键一次，看蜘蛛网的位置",
                        "等 10 秒再看一眼：网自己没了"));
                sections.add(section("§b③ 溺死 恢复人类", 2, (w, y, x, z, width) -> {
                    // The pool is sunk into the floor and has a bottom of its own. The first
                    // version was a raised basin three blocks high, which nobody could get into;
                    // the version before that was dug one block below the room's own floor,
                    // which in a hall that floats over nothing is a hole straight into the void.
                    // Two layers of blackstone under the water are that bottom, and the water's
                    // surface is level with the floor, so a person simply walks in.
                    box(w, Material.POLISHED_BLACKSTONE_BRICKS, x + 6, y - 2, z + 3,
                            x + 14, y, z + 11);
                    box(w, Material.WATER, x + 8, y - 1, z + 5, x + 12, y, z + 9);
                    points.put("demon.pool", new Location(w, x + 10.5, y, z + 7.5));
                    switchpoint(w, y, x + 3, z + 3, Trigger.of(Kind.COLD_BIOME, "demon"),
                            "§b查看生物群系", "§7确认插件认不认");
                    switchpoint(w, y, x + 6, z + 3, Trigger.of(Kind.DIP_WATER, "demon"),
                            "§9跳进水里", "§7带着雪球淹死自己");
                    largeChest(w, x + 2, y + 1, z + DEPTH - 2,
                            fill(new LinkedHashMap<>(), Material.SNOWBALL, 64, 2));
                }, "验证恶魔回人类的唯一路径",
                        "恶魔在寒冷生物群系（雪原、冰原、冷冻海洋、雪山等）溺死，"
                                + "并且背包里至少有 30 个雪球时，才会恢复人类。"
                                + "本大厅所在的世界建的时候就设成了雪原生物群系——插件读的是世界，"
                                + "不是哪块牌能改的",
                        "带够 30 个雪球时溺死会变成人类；水池是走进去的，不会掉出世界",
                        "先从箱子里拿走至少 30 个雪球",
                        "点「查看生物群系」确认是寒冷群系",
                        "点「跳进水里」等着淹死"));
                sections.add(section("§5④ 转化他人 地狱岩", 2, (w, y, x, z, width) -> {
                    chest(w, x + 2, y + 1, z + DEPTH - 2, BlockFace.NORTH, centred(
                            new ItemStack(Material.NETHERRACK, 8)));
                    switchpoint(w, y, x + 4, z + 3,
                            Trigger.with(Kind.SPAWN_NPCS, "demon", "vampire:NETHERRACK"),
                            "§a放出持地狱岩的异族", "§7他手上真的拿着");
                    switchpoint(w, y, x + 8, z + 3,
                            Trigger.with(Kind.USE_ON_NPC, "demon", "NETHERRACK"),
                            "§c用地狱岩打他", "§7一下就会转化");
                    powerButton(w, y, x + 12, z, "demon");
                }, "验证恶魔能把别人变成恶魔",
                        "手持地狱岩攻击另一名同样手持地狱岩的玩家，耗 2000 能量，会把对方直接"
                                + "转成恶魔。两边都得拿着地狱岩，缺一边就只是普通一击——"
                                + "所以你自己也得从箱子里拿一块。转化那一下被法术吃掉、不掉血；"
                                + "之后他已经是恶魔，再打就是普通一击（地狱岩很钝，只有 1 点）",
                        "打完那一下对方的种族变成 demon，头顶标签和聊天栏都会变",
                        "点「放出持地狱岩的异族」——他手上真的拿着地狱岩",
                        "从箱子拿一块地狱岩拿在手上",
                        "点「用地狱岩打他」（自己走过去打一下也行），看他的种族和标签",
                        "没变就再打一下：能量不够会提示，先点「能量 +1000」"));
                sections.add(section("§4⑤ 免疫 火与爆炸", 2, (w, y, x, z, width) -> {
                    box(w, Material.POLISHED_BLACKSTONE, x + 5, y, z + 5, x + 13, y, z + 11);
                    set(w, Material.FIRE, x + 7, y + 1, z + 7);
                    set(w, Material.FIRE, x + 9, y + 1, z + 7);
                    set(w, Material.FIRE, x + 11, y + 1, z + 7);
                    switchpoint(w, y, x + 3, z + 3, Trigger.of(Kind.SET_FIRE),
                            "§c把自己点着", "§7恶魔不该掉血");
                    powerButton(w, y, x + 6, z, "demon");
                }, "验证恶魔对火焰的免疫",
                        "Demon 的免疫清单是：火焰、火焰蔓延、方块爆炸、实体爆炸、摔落。"
                                + "站在火里、被点着，血和能量都不该动；摔落那一条在走廊末尾的"
                                + "公共「摔落」节里验（那一节写着恶魔也是免疫）",
                        "你会一直烧着（火苗看得见），但血和能量一点都不掉——"
                                + "免疫的是伤害，不是动画",
                        "走到那排火里站一会儿，或者点「把自己点着」",
                        "站够半分钟，确认血条一点没掉"));
                sections.add(section("§4⑥ 岩浆 回血回能", 3, (w, y, x, z, width) -> {
                    box(w, Material.POLISHED_BLACKSTONE_BRICKS, x + 7, y, z + 5,
                            x + 15, y, z + 11);
                    box(w, Material.LAVA, x + 8, y, z + 6, x + 14, y, z + 10);
                    points.put("lava.demon", new Location(w, x + 11.5, y, z + 8.5));
                    switchpoint(w, y, x + 2, z + 3, Trigger.of(Kind.HURT),
                            "§c先打掉半血", "§7不然看不出回血");
                    switchpoint(w, y, x + 5, z + 3, Trigger.of(Kind.DIP_LAVA, "demon"),
                            "§4跳进岩浆", "§7站着别动");
                    switchpoint(w, y, x + 8, z + 3, Trigger.of(Kind.POWER_READ),
                            "§b查看能量", "§7每 2 秒 +40");
                }, "验证恶魔在岩浆里不但不受伤，还回血回能",
                        "岩浆对恶魔不是伤害而是补给：每次结算回 1 点血、+40 能量（两次结算之间"
                                + "有约 2 秒冷却）。这是恶魔唯一不花钱的能量来源",
                        "站在岩浆里血条慢慢回满，能量持续上涨",
                        "点「先打掉半血」",
                        "点「跳进岩浆」站进去，别急着上来",
                        "每隔一会儿点一次「查看能量」，看它涨了多少"));
                sections.add(section("§4⑦ 脱战 持续掉能", 2, (w, y, x, z, width) -> {
                    switchpoint(w, y, x + 3, z + 3, Trigger.of(Kind.POWER_READ),
                            "§b查看能量", "§7记下这个数");
                    switchpoint(w, y, x + 6, z + 3, Trigger.of(Kind.POWER_READ),
                            "§b再查一次", "§7隔一分钟再点");
                    switchpoint(w, y, x + 9, z + 3, Trigger.of(Kind.INFINITE_POWER),
                            "§e无限能量 开/关", "§7OP 默认开着，测掉能前先关");
                    powerButton(w, y, x + 12, z, "demon");
                }, "验证恶魔离开下界与火源之后会一直掉能量",
                        "不在下界、脚下方块也不是火或岩浆时，每 5 秒 -20 能量"
                                + "（Demon.Power.Loss = 4，每秒结算）。这是恶魔最大的枷锁："
                                + "不在火旁边就一直在流失。大厅会替你临时关掉 OP 默认的"
                                + "「无限能量」权限（不关的话这一段什么都看不出来），"
                                + "想开回来就点「无限能量 开/关」",
                        "站在这里能量一路下降；跳进上一节的岩浆里则会涨——那是对照",
                        "点一次「查看能量」，把数字记住",
                        "原地等一分钟，再点一次，看掉了多少",
                        "想让它停下来，就回下界（①节有传送）或者站进岩浆"));
                sections.add(section("§4⑧ 护甲 只能裸装", 1, (w, y, x, z, width) ->
                        chest(w, x + 2, y + 1, z + DEPTH - 2, BlockFace.NORTH, centred(
                                new ItemStack(Material.LEATHER_CHESTPLATE, 1),
                                new ItemStack(Material.IRON_CHESTPLATE, 1),
                                new ItemStack(Material.DIAMOND_CHESTPLATE, 1))),
                        "验证恶魔不能穿任何护甲",
                        "护甲白名单里只有空气：皮革、铁、金、钻、锁链、铜、下界合金的头盔、胸甲、"
                                + "护腿、靴子，穿上都会被脱下来掉在地上。恶魔靠免疫和回血活着，不靠甲",
                        "三件胸甲一件都留不住，全被脱下来",
                        "从箱子里拿三件胸甲",
                        "依次穿上，看它们是不是都被脱掉"));
            }
            case "priest" -> {
                sections.add(section("§f① 入教 教会祭坛", 1, (w, y, x, z, width) -> {
                    Shrines.priest(new Blocks(w), x + 5, y, z + DEPTH / 2);
                    priestAltars.add(new Location(w, x + 5.5, y + 1, z + DEPTH / 2.0 + 0.5));
                    chest(w, x + 2, y + 1, z + DEPTH - 2, BlockFace.NORTH, centred(
                            new ItemStack(Material.GOLD_INGOT, 4),
                            new ItemStack(Material.GLOWSTONE_DUST, 4),
                            new ItemStack(Material.REDSTONE, 8),
                            new ItemStack(Material.BREAD, 30)));
                }, "作为人类捐齐材料加入教会，成为牧师",
                        "教会祭坛是钻石块，位置由管理员用 /sn setchurch 设。教会是一个列表，"
                                + "一台服务器可以有多座（/sn church add|remove|list|clear 管理）；"
                                + "插件认的是你右键的那块钻石块在不在一座教会附近，而不是你的站位。"
                                + "入教材料：金锭 4、萤石粉 4、红石 8、面包 30。"
                                + "需要权限 supernatural.player.shrineuse.priest",
                        "材料被收走，你变成牧师（初始 10000 能量），聊天栏出现提示",
                        "在箱子里拿齐四样材料（关箱自动补满）",
                        "右键中间的钻石块"));
                sections.add(section("§6② 献祭 教会捐赠", 1, (w, y, x, z, width) ->
                        chest(w, x + 2, y + 1, z + DEPTH - 2, BlockFace.NORTH, centred(
                                new ItemStack(Material.APPLE, 8),
                                new ItemStack(Material.COD, 8),
                                new ItemStack(Material.COOKED_COD, 8),
                                new ItemStack(Material.COOKED_PORKCHOP, 8),
                                new ItemStack(Material.BREAD, 8))),
                        "验证牧师向教会捐献换能量",
                        "牧师右键祭坛会把背包里所有可捐物一次收走，按单价换算能量："
                                + "苹果 9000、生鳕鱼 510、熟鳕鱼 500、熟猪排 210、面包 80。"
                                + "箱子里五种各 8 个，够你把每一条都试到",
                        "聊天栏列出收到的能量数值，对应物品全部被收走",
                        "成为牧师后再回到这一格",
                        "拿走箱子里的食物，右键钻石块，看聊天栏报的数值"));
                sections.add(section("§8③ 逐出 恢复人类", 1, (w, y, x, z, width) ->
                        chest(w, x + 2, y + 1, z + DEPTH - 2, BlockFace.NORTH, centred(
                                new ItemStack(Material.COAL, 4))),
                        "验证牧师手持煤炭右键祭坛会被逐出教会",
                        "牧师拿煤炭右键祭坛，等于自己退会：身份立刻清空、变回人类，"
                                + "能量归 0。这是牧师唯一不需要别人帮忙的退出方式",
                        "右键后立刻恢复人类，牧师身份清空",
                        "确保自己还是牧师",
                        "拿箱子里的煤炭右键钻石块"));
                sections.add(section("§b④ 法术 净化与治疗", 3, (w, y, x, z, width) -> {
                    spellBench(w, y, x, z, Material.FEATHER, Material.SUGAR, Material.FLINT,
                            Material.PAPER);
                    switchpoint(w, y, x + 2, z + 3, Trigger.of(Kind.SPAR),
                            "§f叫陪练过来", "§7法术都要打在他身上");
                    switchpoint(w, y, x + 5, z + 3, spell("FEATHER"),
                            "§7羽毛 · 放逐", "§7打他一下，送放逐点");
                    switchpoint(w, y, x + 8, z + 3, spell("SUGAR"),
                            "§7糖 · 驱魔", "§7打他一下，变回人类");
                    switchpoint(w, y, x + 11, z + 3, spell("FLINT"),
                            "§7燧石 · 治愈诅咒", "§7打他一下，都拿燧石");
                    switchpoint(w, y, x + 14, z + 3, spell("PAPER"),
                            "§7纸 · 治疗", "§7打他一下，回10血");
                    powerButton(w, y, x + 6, z, "priest");
                }, "验证牧师的四个单体法术都能触发",
                        "全部四个都用「拿物品攻击目标」触发，不是右键：羽毛＝放逐，耗 4000，"
                                + "把异族传送到管理员用 /sn setbanish 设的放逐点；糖＝驱魔，耗 9000，"
                                + "把异族变回人类；燧石＝治愈诅咒，耗 1000，要求对方手上也拿着燧石；"
                                + "纸＝治疗，耗 1000，给非超自然目标回 10 点血。"
                                + "另外牧师打超自然目标会点燃对方、伤害翻倍，打普通动物则完全无效（狼除外）",
                        "每个法术都在那个陪练身上真实生效：他先变成吸血鬼（一个真的超自然"
                                + "单位），法术按普通攻击的路径打上去，掉血、变身、传送都是真的，"
                                + "不是播一条消息。法术得你自己打——牌子只把物品发到你手里、"
                                + "把靶子叫到你面前，不会替你出手",
                        "点「叫陪练过来」，他会先变成异族，然后走过来站到你面前",
                        "拿手里的法术物品打他一下，看陪练的变化和聊天栏",
                        "四块牌逐个试；每点一次就重新发一件物品、把靶子叫回来"));
                sections.add(section("§5⑤ 法术 汲取与守护", 2, (w, y, x, z, width) -> {
                    spellBench(w, y, x, z, Material.BOOK, Material.WHITE_WOOL, Material.BOWL);
                    switchpoint(w, y, x + 5, z + 3, spell("BOOK"),
                            "§7书 · 汲取", "§7抽走对方 15% 能量");
                    switchpoint(w, y, x + 8, z + 3, spell("WHITE_WOOL"),
                            "§7羊毛 · 守护天使", "§7给人类设置守护关系");
                    switchpoint(w, y, x + 11, z + 3, spell("BOWL"),
                            "§7碗 · 远程捐赠", "§7背包里有可捐物就有效");
                    powerButton(w, y, x + 2, z, "priest");
                }, "验证汲取 / 守护天使 / 远程捐赠三件法术",
                        "书＝汲取，耗 1000，抽走对方当前能量的 15% 加到自己身上；"
                                + "羊毛＝守护天使，耗 5000，给一名人类挂上守护关系，"
                                + "他之后被异族打死时替他挡一命，每个牧师只能保一个；"
                                + "碗＝远程捐赠，不花能量，背包里有可捐物就一次收走一件，收益减半",
                        "汲取让陪练掉能量、你涨能量；守护天使把关系挂在陪练身上；"
                                + "远程捐赠按半价给你能量",
                        "先点「叫陪练过来」当靶子（守护天使要人类，陪练默认是人类）",
                        "依次用书 / 羊毛 / 碗打他，注意聊天栏的回报"));
                sections.add(section("§8⑥ 护甲 只能裸装", 1, (w, y, x, z, width) -> {
                    chest(w, x + 2, y + 1, z + DEPTH - 2, BlockFace.NORTH, centred(
                            new ItemStack(Material.LEATHER_CHESTPLATE, 1),
                            new ItemStack(Material.IRON_CHESTPLATE, 1),
                            new ItemStack(Material.DIAMOND_CHESTPLATE, 1)));
                    powerButton(w, y, x + 7, z, "priest");
                }, "验证牧师不能穿任何护甲",
                        "牧师的护甲白名单里只有空气：不分材质，任何头盔、胸甲、护腿、靴子"
                                + "在穿上后都会被立刻脱下并掉在地上。这不是「只禁高级护甲」，"
                                + "皮革一样会被脱",
                        "三件胸甲一件都留不住，都会被脱下来掉在地上",
                        "从箱子里拿三件胸甲",
                        "依次穿上，看每一件是不是都被立刻脱下"));
                sections.add(section("§8⑦ 动物 与 点火", 3, (w, y, x, z, width) -> {
                    chest(w, x + 2, y + 1, z + DEPTH - 2, BlockFace.NORTH, centred(
                            new ItemStack(Material.IRON_SWORD, 1),
                            new ItemStack(Material.PORKCHOP, 8),
                            // The two spells that act on a supernatural target, so that the vampire
                            // this room lets out can be used for what a priest is for. The boards
                            // here are about the sword and are read as a fight room, which they are
                            // - but a player who then reaches for a spell finds there is nothing in
                            // the chest to cast one with, and the spell room that does have the
                            // props is two doors back. Nobody walks two doors back for the prop of
                            // the room they are standing in.
                            new ItemStack(Material.FEATHER, 8),
                            new ItemStack(Material.SUGAR, 8)));
                    switchpoint(w, y, x + 5, z + 3, Trigger.with(Kind.SPAWN_MOB, null, "COW"),
                            "§a放出一头牛", "§7拿剑砍它，伤害是 0");
                    switchpoint(w, y, x + 8, z + 3,
                            Trigger.with(Kind.SPAWN_NPCS, "priest", "vampire"),
                            "§c放出一个吸血鬼", "§7砍他，或拿羽毛施法");
                    // And the power to do anything about him with. A priest who has just been made
                    // one has none, and every spell below its cost refuses with a sentence rather
                    // than a number - so with no way to top up here, the one thing a player would
                    // try on this room's vampire could only ever answer "not enough power".
                    powerButton(w, y, x + 11, z, "priest");
                }, "验证牧师的战斗规则：不伤无辜动物，对异族加重并点燃",
                        "对非敌对的动物（狼除外）伤害一律为 0，只提示 \"You cannot hurt innocent animals.\"；"
                                + "对超自然玩家则额外加一倍基础伤害，并把对方点燃 50 tick（恶魔不会被点燃）；"
                                + "对怪物也会点燃。注意这些判定都在插件认为的「PvP 区域」里生效——"
                                + "本测试服没有 WorldGuard，插件把一切都当作 PvP",
                        "牛挨多少下都不掉血；吸血鬼被打中会烧起来，伤害还翻倍",
                        "点「放出一头牛」，拿箱子里的铁剑砍它几下，看是否掉血",
                        "点「放出一个吸血鬼」，再砍他一下",
                        "也可以拿箱子里的羽毛（放逐）或糖（驱魔）打他——法术在这里一样生效，"
                                + "只是要花能量，箱子边那块牌补能量",
                        "看他身上有没有着火、聊天栏有没有提示"));
            }
            case "witchhunter" -> {
                sections.add(section("§7① 入会 杀三个异族", 3, (w, y, x, z, width) -> {
                    chest(w, x + 2, y + 1, z + DEPTH - 2, BlockFace.NORTH, centred(
                            new ItemStack(Material.IRON_SWORD, 1)));
                    switchpoint(w, y, x + 5, z + 3,
                            Trigger.with(Kind.SPAWN_NPCS, "witchhunter", "vampire,ghoul,werewolf"),
                            "§a放出三个异族", "§7吸血鬼 / 食尸鬼 / 狼人");
                }, "验证猎巫人的真实入会条件：以人类身份杀死三种不同异族",
                        "条件是以人类身份亲手杀死 3 种不同的超自然种族"
                                + "（同一种杀三次不算）。杀满之后约 10 秒，公会会发一张邀请给你，"
                                + "那才是正规入会——下一格才用得上它",
                        "杀满三种后聊天栏会收到公会邀请，这才是正规入会；"
                                + "本节放出来的三个靶子是吸血鬼、食尸鬼、狼人",
                        "先用铁剑（箱子里有）杀掉放出来的三个异族",
                        "杀满三种后等约 10 秒，看聊天栏的邀请，再去下一格开门"));
                sections.add(section("§7② 入会 铁门与告示牌", 1, (w, y, x, z, width) -> {
                    door(w, x + 6, y + 1, z + DEPTH / 2);
                    set(w, STELE, x + 7, y + 1, z + DEPTH / 2);
                    set(w, Material.SEA_LANTERN, x + 7, y + 2, z + DEPTH / 2);
                    // At the second block, which is where a board belongs - this one was left at
                    // the player's feet when every other board in the building was raised, and it
                    // is the one board here that has to be read: the lesson of the room is that
                    // the door reads the sign, so the sign has to be legible from standing. The
                    // door's own check looks through a five-by-five cube around it, so a board one
                    // block higher is read exactly the same.
                    signOn(w, x + 7, y + 2, z + DEPTH / 2, BlockFace.EAST, "WitchHunter");
                    switchpoint(w, y, x + 2, z + 3, Trigger.of(Kind.INVITE_HUNTER),
                            "§a直接发一张邀请", "§7跳过杀怪，图快的用这个");
                }, "验证铁门入会：门旁告示牌必须写着 WitchHunter",
                        "猎巫人大厅的入口是一扇铁门，判定靠旁边告示牌的任意一行含有文本 WitchHunter。"
                                + "右键铁门时：有邀请的人类、或者已经是猎巫人，才会开门并完成入会；"
                                + "其他人只会看到 WitchHunters Only!。门自己 1 秒后关上，"
                                + "开的时候上下两格一起动",
                        "有邀请时右键铁门会开门并把你变成猎巫人；没邀请只会提示 WitchHunters Only",
                        "（可选）右键左边那块牌直接拿一张邀请",
                        "右键铁门，门开一下再关上，人就入会了"));
                sections.add(section("§a③ 弓箭模式", 3, (w, y, x, z, width) -> {
                    chest(w, x + 2, y + 1, z + DEPTH - 2, BlockFace.NORTH, centred(
                            new ItemStack(Material.BOW, 1),
                            new ItemStack(Material.ARROW, 32)));
                    chest(w, x + 4, y + 1, z + DEPTH - 2, BlockFace.NORTH, centred(
                            new ItemStack(Material.ARROW, 32)));
                    longSparMark(w, "witchhunter_bow", x + 12, y, z + DEPTH / 2);
                    // A ledge to grapple onto: a grapple arrow pulls the shooter at whatever it
                    // hit, and there is nothing above the corridor to pull anybody towards.
                    box(w, Material.STONE_BRICKS, x + 13, y + 6, z + 3, x + 17, y + 6, z + 11);
                    box(w, Material.STONE_BRICKS, x + 13, y + 1, z + 12, x + 17, y + 6, z + 12);
                    points.put("witchhunter.ledge", new Location(w, x + 15.5, y + 7, z + 7.5));
                    switchpoint(w, y, x + 5, z + 3, Trigger.of(Kind.LEFT_CLICK),
                            "§a切换箭矢模式", "§7也可以自己空挥弓");
                    switchpoint(w, y, x + 8, z + 3, Trigger.of(Kind.SPAR),
                            "§f叫陪练过来", "§7拿他当靶子试特殊箭");
                    powerButton(w, y, x + 2, z, "witchhunter");
                }, "验证猎巫人的特殊箭矢：五种模式各是什么、各花多少",
                        "手持弓左键循环切换，顺序是 normal → fire → triple → power → grapple"
                                + " → normal："
                                + "①normal 普通箭，不花能量；"
                                + "②fire 火焰箭，100，命中后目标着火，落点还能点着可燃方块；"
                                + "③triple 三连箭，100，一次射出三支；"
                                + "④power 强化箭，1000，伤害额外 +200%，用完进入 15 秒冷却，"
                                + "冷却中射不出去；"
                                + "⑤grapple 抓钩箭，500 —— 要对着头顶那层石砖平台射，箭必须命中方块，"
                                + "命中后你会被拽向命中点；离得太近（3 格内）或者箭什么都没打到就不会拉。"
                                + "除 normal 以外，特殊箭只允许在 PvP 区域使用",
                        "左键一次换一种，聊天栏报出当前箭种；对着陪练射 power 箭伤害明显更高，"
                                + "紧接着再射一箭会被 15 秒冷却挡下；fire 箭会让陪练身上着火；"
                                + "triple 一箭射出三支；grapple 打中头顶那层石砖会把你整个人拽上去",
                        "点「切换箭矢模式」切到 grapple（聊天栏会报当前箭种）",
                        "站远一点，抬头对着那层石砖平台射一箭，看自己是不是被拽过去",
                        "再切回别的箭种，对着陪练射一箭看伤害",
                        "换 fire 再射一箭，看陪练身上着火",
                        "换 triple 射一箭，数一下是不是三支",
                        "切到 power 连射两次，第二次会因为冷却射不出去"
                                + "（注意：火只点得着人身，点不着方块——大厅里是禁止点燃的）"));
                sections.add(section("§6④ 悬赏", 1, (w, y, x, z, width) -> {
                    switchpoint(w, y, x + 4, z + 3, Trigger.of(Kind.BOUNTY),
                            "§6查看悬赏名单", "§7顺手刷新一个悬赏");
                    switchpoint(w, y, x + 7, z + 3, Trigger.of(Kind.POWER, "witchhunter"),
                            "§b能量 +1000", "§7够放特殊箭");
                }, "验证悬赏系统",
                        "服务器最多同时保留 5 个悬赏目标，只从在线的超自然玩家里挑，不够就自动补。"
                                + "杀掉悬赏目标额外 +8000 能量；顺带一提猎巫人的能量来源很窄："
                                + "杀玩家 +2000、杀生物 +0、死亡只扣 500、摔落伤害减 3 点",
                        "右键那块牌聊天栏报出当前名单；名单不满 5 个时会自动补一个并全服公告",
                        "点「查看悬赏名单」，聊天栏报出当前名单",
                        "名单不满时会自动补一个（全服公告）"));
                sections.add(section("§7⑤ 护甲限制", 1, (w, y, x, z, width) ->
                        chest(w, x + 2, y + 1, z + DEPTH - 2, BlockFace.NORTH, centred(
                                new ItemStack(Material.LEATHER_CHESTPLATE, 1),
                                new ItemStack(Material.IRON_CHESTPLATE, 1),
                                new ItemStack(Material.DIAMOND_CHESTPLATE, 1))),
                        "验证猎巫人只能穿皮革护甲",
                        "护甲白名单只有空气和皮革：铁、金、钻、锁链、铜、下界合金的头盔、胸甲、"
                                + "护腿、靴子都不能穿，穿上就被脱下来掉在地上",
                        "穿上皮革没事；穿铁或钻石的会被立刻脱下并掉落",
                        "从箱子里拿三件胸甲",
                        "依次穿上，看哪两件被脱下来"));
                sections.add(section("§7⑥ 武器限制", 1, (w, y, x, z, width) -> {
                    chest(w, x + 2, y + 1, z + DEPTH - 2, BlockFace.NORTH, centred(
                            new ItemStack(Material.IRON_SWORD, 1),
                            new ItemStack(Material.STONE_AXE, 1),
                            new ItemStack(Material.DIAMOND_PICKAXE, 1)));
                    switchpoint(w, y, x + 6, z + 3, Trigger.of(Kind.SPAR),
                            "§f放出陪练", "§7他会站在你面前给你打");
                    powerButton(w, y, x + 9, z, "witchhunter");
                }, "验证猎巫人除了弓以外的近战武器全被禁",
                        "禁用的是木 / 石 / 铁 / 金 / 钻 / 铜 / 下界合金的剑、斧、镐、锹、锄："
                                + "拿着它们近战，伤害直接判成 0。弓是允许的（那是猎巫人的主业），"
                                + "空手也不受限",
                        "拿这三样武器打陪练，伤害数字全是 0；空手是正常伤害",
                        "点「放出陪练」",
                        "拿箱子里的剑 / 斧 / 镐分别打他一下",
                        "空手再打一下对照"));
            }
            case "enderborn" -> {
                sections.add(section("§5① 转化 珍珠与粘土", 1, (w, y, x, z, width) -> {
                    layer(w, Material.CLAY, y, x + 4, z + DEPTH / 2 - 1,
                            x + 6, z + DEPTH / 2 + 1);
                    chest(w, x + 2, y + 1, z + DEPTH - 2, BlockFace.NORTH, centred(
                            new ItemStack(Material.ENDER_PEARL, 8)));
                }, "作为人类用末影珍珠右键粘土，验证末影族转化",
                        "手持末影珍珠右键粘土方块，消耗 1 颗珍珠，人就变成末影族。"
                                + "另一条路是被末影族用珍珠打（双方都得拿着珍珠），那个更快但更少见",
                        "珍珠右键粘土后你会变成末影族，聊天栏出现提示",
                        "从箱子里拿一颗末影珍珠",
                        "对着粘土方块右键"));
                sections.add(section("§d② 珍珠 两种用法", 1, (w, y, x, z, width) -> {
                    chest(w, x + 2, y + 1, z + DEPTH - 2, BlockFace.NORTH, centred(
                            new ItemStack(Material.ENDER_PEARL, 16)));
                    powerButton(w, y, x + 5, z, "enderborn");
                }, "验证末影族珍珠的两种用法",
                        "右键＝不传送，改换 100 能量并消耗 1 颗珍珠；左键＝切换「珍珠是否真的传送」"
                                + "这个开关（默认开着）。另外手持珍珠被攻击时，只要能量超过 500，"
                                + "就减伤一半并扣 500 能量。"
                                + "末影族的死亡代价很低：每死一次只扣 300 能量",
                        "右键能量 +100、珍珠少一颗；左键聊天栏报出开关状态；"
                                + "开关关掉时右键不再传送、改为换能量",
                        "先变成末影族，回到这一格",
                        "拿珍珠右键一次（加能量），再左键切换模式",
                        "再右键一次，看行为随开关变了"));
                sections.add(section("§d③ 武器限制", 1, (w, y, x, z, width) -> {
                    chest(w, x + 2, y + 1, z + DEPTH - 2, BlockFace.NORTH, centred(
                            new ItemStack(Material.DIAMOND_SWORD, 1),
                            new ItemStack(Material.DIAMOND_PICKAXE, 1),
                            new ItemStack(Material.IRON_SWORD, 1)));
                    switchpoint(w, y, x + 6, z + 3, Trigger.of(Kind.SPAR),
                            "§f放出陪练", "§7他会站在你面前给你打");
                    powerButton(w, y, x + 9, z, "enderborn");
                }, "验证末影族的武器禁令只针对高级材质",
                        "禁用清单是钻石 / 铜 / 下界合金的全套工具与剑（剑、镐、锹、斧、锄）："
                                + "近战伤害直接判成 0。木、石、铁没被禁——这条禁令挑材质，不挑类型",
                        "钻石剑和钻石镐打陪练都是 0 伤害；铁剑是正常伤害",
                        "点「放出陪练」",
                        "拿箱子里的钻石剑和钻石镐各打一下",
                        "拿铁剑再打一下对照"));
                sections.add(section("§5④ 死亡五次 恢复人类", 1, (w, y, x, z, width) -> {
                    switchpoint(w, y, x + SLOT / 2, z + 3, Trigger.of(Kind.SELF_KILL),
                            "§c死一次", "§7连着点到第五次");
                    powerButton(w, y, x + 5, z, "enderborn");
                }, "验证末影族的死亡次数上限：第 5 次死亡自动变回人类",
                        "每死一次扣 300 能量并累计一次；累计到第 5 次时自动恢复人类，"
                                + "计数器清零。这是末影族唯一的自救路径，不用找别人帮忙。"
                                + "（10% 概率把被击杀者转成末影族——这个概率写在代码里，"
                                + "不读任何配置项；也要先有人死给你）",
                        "每死一次聊天栏报出还剩几次；第 5 次之后你变回人类",
                        "先变成末影族，回到这一格",
                        "连点「死一次」，每次死亡后回到出生点再点下一次",
                        "数到第 5 次，看聊天栏和你的种族"));
                sections.add(section("§5⑤ 转化他人 珍珠对珍珠", 2, (w, y, x, z, width) -> {
                    chest(w, x + 2, y + 1, z + DEPTH - 2, BlockFace.NORTH, centred(
                            new ItemStack(Material.ENDER_PEARL, 8)));
                    switchpoint(w, y, x + 6, z + 3,
                            Trigger.with(Kind.SPAWN_NPCS, "enderborn", "vampire:ENDER_PEARL"),
                            "§c放出一个持珍珠的异族", "§7他也得拿着珍珠");
                }, "验证末影族的转化方式：两颗珍珠对撞",
                        "攻击一个同样手持末影珍珠的玩家，会把对方直接转成末影族——"
                                + "没有概率，一次就成，也不用对方同意。对方手上没有珍珠则不会发生，"
                                + "只是一记普通攻击。这是末影族唯一能扩大同族的手段",
                        "打中他一下，他立刻变成末影族，聊天栏有提示",
                        "从箱子里拿一颗末影珍珠，拿在手上",
                        "点「放出一个持珍珠的异族」，他会站在你旁边",
                        "用手打他一下（拿着珍珠也算攻击），看他的种族变成 enderborn"));
                sections.add(section("§5⑥ 持珍珠 减伤", 2, (w, y, x, z, width) -> {
                    chest(w, x + 2, y + 1, z + DEPTH - 2, BlockFace.NORTH, centred(
                            new ItemStack(Material.ENDER_PEARL, 8)));
                    switchpoint(w, y, x + 5, z + 3, Trigger.of(Kind.POWER_READ),
                            "§b查看能量", "§7挨打前后各看一次");
                    switchpoint(w, y, x + 8, z + 3,
                            Trigger.with(Kind.TEST_HIT, "enderborn", "IRON_SWORD"),
                            "§c挨一下铁剑", "§7手持珍珠时减半");
                }, "验证末影族手持珍珠时的护身",
                        "受到任何伤害时，只要手上拿着末影珍珠且能量超过 500，"
                                + "就立刻扣掉 500 能量、把这次伤害减半——拿能量换命。"
                                + "能量不足 500 时这一条不生效，全额挨打",
                        "拿着珍珠挨打，伤害数字是同样一刀的一半，同时能量掉 500；"
                                + "把珍珠收起来再挨一刀，就是全额伤害",
                        "点「查看能量」记住数字（要保证过 500）",
                        "拿一颗珍珠在手上，点「挨一下铁剑」，看掉血和能量",
                        "把珍珠收回背包，再点一次「挨一下铁剑」，对照"));
                sections.add(section("§5⑦ 击杀 转移能量", 2, (w, y, x, z, width) -> {
                    chest(w, x + 2, y + 1, z + DEPTH - 2, BlockFace.NORTH, centred(
                            new ItemStack(Material.IRON_SWORD, 1)));
                    switchpoint(w, y, x + 5, z + 3, Trigger.of(Kind.POWER_READ),
                            "§b查看能量", "§7杀他之前记住数");
                    switchpoint(w, y, x + 8, z + 3,
                            Trigger.with(Kind.SPAWN_NPCS, "enderborn", "werewolf"),
                            "§c放出一个狼人", "§7杀掉他看能量");
                }, "验证末影族从击杀里拿能量",
                        "末影族的能量来源只有两条：击杀生物 +50，击杀玩家则从对方身上"
                                + "「偷」50 给自己（对方同时 -50）。另外有一条 10% 的几率"
                                + "把被击杀的玩家转成末影族——这是概率，不保证出现。"
                                + "注意它不靠吃、不靠泡水，也不靠站在火里",
                        "杀掉那个狼人之后，你的能量比之前高 50",
                        "先点「查看能量」，把数字记住",
                        "点「放出一个狼人」，用剑杀掉他",
                        "再点一次「查看能量」，对比前后差了 50"));
            }
            case "angel" -> {
                sections.add(section("§f① 转化 羽毛摔死", 3, true, (w, y, x, z, width) -> {
                    // A human has twenty health and a fall costs one per block past the third,
                    // so a ledge has to be about twenty-five blocks up before the fall is a
                    // death at all. The first version was six up and simply broke an ankle.
                    int top = y + 28;
                    box(w, Material.SMOOTH_QUARTZ, x + 10, top, z + 5, x + 16, top, z + 9);
                    box(w, AIR, x + 12, top, z + 6, x + 14, top, z + 8);
                    points.put("angel.ledge", new Location(w, x + 16.5, top + 1, z + 9.5));
                    chest(w, x + 2, y + 1, z + DEPTH - 2, BlockFace.NORTH, centred(
                            new ItemStack(Material.FEATHER, 8)));
                    switchpoint(w, y, x + 4, z + 3, Trigger.of(Kind.CLIMB, "angel"),
                            "§b上到高台", "§7走到洞里跳下");
                }, "作为人类手持羽毛从高处摔死，验证天使转化",
                        "条件只有一个：以人类身份摔死，而且摔死时手上拿着羽毛。"
                                + "台子有 28 格高——人类 20 点血，掉落伤害从第 3 格起算每格 1 点，"
                                + "矮了根本摔不死。变成天使之后这条伤害会被完全免除，以后摔不死了",
                        "摔死的那一刻你会变成天使：掉落伤害被免除，但死亡本身照常触发",
                        "从箱子里拿一根羽毛拿在手上",
                        "点「上到高台」，走到中间的洞里跳下去"));
                sections.add(section("§e② 道具 跳 · 治疗 · 净化", 1, (w, y, x, z, width) -> {
                    chest(w, x + 2, y + 1, z + DEPTH - 2, BlockFace.NORTH, centred(
                            new ItemStack(Material.DANDELION, 8),
                            new ItemStack(Material.FEATHER, 8),
                            new ItemStack(Material.PAPER, 8)));
                    // He plays a vampire here, and that is not decoration. The angel's paper cures a
                    // supernatural being back to human, and the plugin refuses it on anybody else
                    // with "Player is not a supernatural!" - so against an ordinary person the
                    // section would look broken while working exactly as written. The step asks for
                    // a vampire, which is a race that can be cured.
                    switchpoint(w, y, x + 6, z + 3, Trigger.with(Kind.SPAR, "angel", "vampire"),
                            "§f叫陪练过来", "§7他是吸血鬼，纸才用得着");
                    powerButton(w, y, x + 9, z, "angel");
                }, "验证天使的超级跳、治疗与净化",
                        "蒲公英左键＝超级跳，往上窜一截，耗 1000；羽毛＝治疗，"
                                + "攻击目标（不是右键）给他回 5 点生命，耗 3000；"
                                + "纸＝净化，攻击目标把他变回人类，耗 6000——这一下"
                                + "只对超自然目标有效，打人类会说「Player is not a supernatural」。"
                                + "所以陪练在这一间扮的是吸血鬼。天使另外还免疫摔落伤害、在水里每 10 秒 +50 能量",
                        "蒲公英左键往上窜一截；拿羽毛打受伤的目标，他会回 5 点血；"
                                + "拿纸打吸血鬼，他变成人类",
                        "先点「叫陪练过来」——他这一间扮吸血鬼，别用打人类的思路看他",
                        "用剑把他打掉一点血，再拿羽毛打他一下，看他回血",
                        "拿纸打他一下，看他从 vampire 变成 human",
                        "最后拿蒲公英左键跳一次"));
                sections.add(section("§b③ 召唤 三种动物", 1, (w, y, x, z, width) -> {
                    chest(w, x + 2, y + 1, z + DEPTH - 2, BlockFace.NORTH, centred(
                            new ItemStack(Material.BEEF, 8),
                            new ItemStack(Material.BONE, 8),
                            new ItemStack(Material.PORKCHOP, 8)));
                    powerButton(w, y, x + 6, z, "angel");
                }, "验证天使的三种召唤",
                        "都按左键触发，各耗 5000 能量，还会吃掉手里那一块：生牛肉召牛、"
                                + "骨头召一只已驯服的狼（归你所有、满血）、猪肉召猪。"
                                + "左键空气时它们落在你身前 2 格，左键方块时落在你指的位置上方；"
                                + "落点会自己挑能站人、脚下有地的地方，不会再生成在方块内部憋死",
                        "三种动物出现在你身前或你指的地方，而且十几秒后还活着；"
                                + "每召一次手里少一块、能量少 5000",
                        "拿箱子里的三种肉，对着地面或空中左键一次",
                        "等十几秒再看一眼，召唤物必须还活着",
                        "两只手的物品栏都不一样的话，换一种肉再来一次"));
                sections.add(section("§6④ 护甲 与 钻石剑", 2, (w, y, x, z, width) -> {
                    chest(w, x + 2, y + 1, z + DEPTH - 2, BlockFace.NORTH, centred(
                            new ItemStack(Material.GOLDEN_HELMET, 1),
                            new ItemStack(Material.GOLDEN_CHESTPLATE, 1),
                            new ItemStack(Material.DIAMOND_LEGGINGS, 1),
                            new ItemStack(Material.DIAMOND_BOOTS, 1),
                            new ItemStack(Material.LEATHER_CHESTPLATE, 1),
                            new ItemStack(Material.IRON_CHESTPLATE, 1),
                            new ItemStack(Material.DIAMOND_SWORD, 1),
                            new ItemStack(Material.IRON_SWORD, 1)));
                    switchpoint(w, y, x + 6, z + 3, Trigger.of(Kind.SPAWN_CHICKEN),
                            "§a放出一只鸡", "§7拿它试钻石剑那条禁令");
                    powerButton(w, y, x + 9, z, "angel");
                }, "验证天使的护甲禁令，以及不能拿钻石剑砍动物",
                        "护甲：金与钻石的头盔、胸甲、护腿、靴子四件都会被脱下来，"
                                + "皮革、铁、锁链、铜、下界合金没有在禁用名单里（那条名单是硬编码的，"
                                + "不走配置文件）。武器：拿钻石剑攻击动物会被直接拦下、伤害 0，"
                                + "换别的武器就正常",
                        "金/钻四件穿上就被脱；皮革和铁胸甲留得住；"
                                + "拿钻石剑打鸡被拦，拿铁剑能打",
                        "从箱子里拿四件金色/钻石护甲依次穿上，看它们是不是都被脱下",
                        "再穿一次皮革胸甲和铁胸甲，看它们留不留",
                        "点「放出一只鸡」，先用钻石剑打它，再用铁剑打它"));
                sections.add(section("§e⑤ 净化 用纸", 1, (w, y, x, z, width) -> {
                    chest(w, x + 2, y + 1, z + DEPTH - 2, BlockFace.NORTH, centred(
                            new ItemStack(Material.PAPER, 8)));
                    switchpoint(w, y, x + 6, z + 3,
                            Trigger.with(Kind.SPAWN_NPCS, "angel", "vampire"),
                            "§a放出一个异族", "§7一个假人吸血鬼");
                    powerButton(w, y, x + 9, z, "angel");
                }, "验证天使把异族净化回人类的能力",
                        "纸＝净化，攻击目标触发，耗 6000，把超自然目标变回人类。"
                                + "对本来就是人类的目标只会提示，不消耗能量。"
                                + "这是天使唯一能给别人解咒的能力——天使自己没有解药，"
                                + "只能靠牧师驱魔、治愈诅咒或者管理员",
                        "打完那一下对方变成人类；如果他本来就是人类，只会收到提示",
                        "点「放出一个异族」，他会站在你面前",
                        "拿箱子里的纸打他，看聊天栏和种族变化"));
                sections.add(section("§b⑥ 水中 回能", 2, (w, y, x, z, width) -> {
                    box(w, Material.SMOOTH_QUARTZ, x + 7, y - 2, z + 6,
                            x + 15, y, z + 12);
                    box(w, Material.WATER, x + 9, y - 1, z + 8, x + 13, y, z + 10);
                    points.put("angel.pool", new Location(w, x + 11.5, y, z + 9.5));
                    switchpoint(w, y, x + 2, z + 3, Trigger.of(Kind.DIP_WATER, "angel"),
                            "§9跳进水里", "§7站着就能回能量");
                    powerButton(w, y, x + 5, z, "angel");
                }, "验证天使泡在水里会回能量",
                        "脚下方块是水时，每 10 秒 +50 能量——天使是唯一一个能在水里充电的种族，"
                                + "而且不花任何代价。顺带一提天使击杀怪物只有 +30 能量",
                        "站在水里能量持续上升，每 10 秒 50 点",
                        "点「跳进水里」",
                        "站着别动，隔一分钟再看能量涨了多少"));
                sections.add(section("§b⑦ 击杀 怪物回能", 2, (w, y, x, z, width) -> {
                    switchpoint(w, y, x + 3, z + 3, Trigger.of(Kind.POWER_READ),
                            "§b查看能量", "§7杀之前先记住数");
                    switchpoint(w, y, x + 6, z + 3, Trigger.with(Kind.SPAWN_MOB, null, "ZOMBIE"),
                            "§c放出一只僵尸", "§7它是怪物，不是动物");
                    switchpoint(w, y, x + 9, z + 3, Trigger.with(Kind.SPAWN_MOB, null, "CHICKEN"),
                            "§a放出一只鸡", "§7对照：杀动物不给能量");
                }, "验证天使从击杀怪物身上拿能量",
                        "击杀任何怪物（Monster，例如僵尸、骷髅、苦力怕）+30 能量；"
                                + "击杀动物（牛、鸡、羊）不给——插件判的是实体类型，不是「打死一个东西」。"
                                + "这是天使除泡水之外的唯一能量来源",
                        "杀掉僵尸能量 +30；杀掉鸡什么都不加",
                        "点「查看能量」，把数字记住",
                        "点「放出一只僵尸」，杀掉它，再点「查看能量」",
                        "点「放出一只鸡」杀掉，再查一次能量：这次没有变化"));
            }
            default -> throw new IllegalStateException("no hall for " + race);
        }
        // Every one of these races turns a person it kills into one of its own, and the room that
        // shows the rule from the receiving end goes directly behind the room that turned the
        // player into this race in the first place: they are the same rule seen from either side,
        // and that is where somebody looking for "how else could I have become one" will look.
        if (sections.size() > 1) {
            sections.add(1, turnedBySection(race));
        }

        // Every race gets these four, built by the same code, at the end of its row.
        sections.add(fallSection(race, headroom));
        if (KILL_CONVERTS.contains(race)) {
            sections.add(convertSection(race));
        }
        sections.add(sparSection(race));
        sections.add(powerSection(race));
        sections.add(endSection(race));
        return sections;
    }

    /**
     * The far end of every hall. A row of eight or nine steps is a long walk back, so the last
     * section is two switches: back to the near end without changing anything, or out of the
     * test altogether.
     */
    private Section endSection(String race) {
        return section("§f末尾 重来或离开", 2, (w, y, x, z, width) -> {
            switchpoint(w, y, x + 4, z + 3, Trigger.of(Kind.RETURN_START, race),
                    "§a回到开头", "§7回到这一族的入口，种族不变");
            switchpoint(w, y, x + 8, z + 3, Trigger.of(Kind.EXIT_TEST, race),
                    "§a离开测试", "§7清道具 · 恢复人类 · 回大厅");
        }, "走完这一族了，选一种走法",
                "每族最后固定这几间：陪练、+1000 能量、重来或离开。这几间八个种族共用，"
                        + "不属于某个技能；本族的技能测试都在它们前面",
                "「回到开头」只挪位置，种族不变；「离开测试」清空道具、恢复人类再回大厅",
                "想重做一遍点「回到开头」", "想去别的种族点「离开测试」");
    }

    /**
     * A step: a title, what it is for, what should happen, what to do in what order, how many
     * slots of the corridor it takes, whether its ceiling is left open (the ghoul's rain step
     * needs the sky), and what to build in it.
     */
    private static Section section(String title, int slots, Station station,
            String goal, String ability, String expect, String... steps) {
        return new Section(title, board(goal, expect), brief(goal, ability, expect, steps), slots,
                false, station);
    }

    private static Section section(String title, int slots, boolean openSky, Station station,
            String goal, String ability, String expect, String... steps) {
        return new Section(title, board(goal, expect), brief(goal, ability, expect, steps), slots,
                openSky, station);
    }

    /**
     * The same, for the one step that needs both a bigger room and no roof over it.
     *
     * <p>The truce room is the only room in any hall that takes four cells, and it does because of
     * what it has to hold: forty-one monsters, laid out in rings around the middle of the room, and
     * among them an ender dragon sixteen blocks from nose to tail. In a room the size of the others
     * the outer rings are outside the walls, and the monsters that should be standing still in
     * front of the player are standing in the next room. It is also left open to the sky, because
     * the truce is the one rule here that is about who is looking at whom and nothing about the
     * weather - a roof over it would only be a ceiling in the way of the dragon.
     *
     * <p>The four cells are two cells each way, which is the hall's largest room: forty-four
     * across, a little over four times the floor of a standard one, with a ceiling to match.
     */
    private static Section section(String title, int slots, boolean openSky, int cells,
            Station station, String goal, String ability, String expect, String... steps) {
        return new Section(title, board(goal, expect), brief(goal, ability, expect, steps), slots,
                openSky, station, cells, 0);
    }

    /**
     * A step that takes more than one cell of the grid, or a ceiling that is not the standard
     * height.
     */
    private static Section section(String title, int slots, int cells, int headroom,
            Station station, String goal, String ability, String expect, String... steps) {
        return new Section(title, board(goal, expect), brief(goal, ability, expect, steps), slots,
                false, station, cells, headroom);
    }

    /**
     * The two lines a step hangs on its wall: what to do, and what should happen.
     *
     * <p>Not the whole teaching - see {@link Section}. These two are the ones that survive being
     * read once while standing in front of the board, and they are the two a player needs before
     * touching anything. Everything else is the guide's to say.
     */
    private static List<String> board(String goal, String expect) {
        List<String> lines = new ArrayList<>();
        lines.add("§e" + goal);
        lines.add(BREAK);
        lines.add("§b" + expect);
        return lines;
    }

    /**
     * The lines a section teaches, always in the same order and always labelled: the goal, what
     * the ability is and what it costs, what a right answer looks like, then the steps.
     *
     * <p>Written for somebody who has never seen the plugin. The {@code 技能} board says what the
     * thing does, what triggers it, what it costs and what restricts it - the numbers included -
     * because a tester who has to read the source to find out a cooldown is being sent to the
     * source, not taught. An unlabelled paragraph told a person what the step was about but not
     * what they were supposed to do with it.
     */
    private static List<String> brief(String goal, String ability, String expect, String... steps) {
        List<String> lines = new ArrayList<>();
        lines.add("§e目标 §f" + goal);
        lines.add(BREAK);
        lines.add("§d技能 §f" + ability);
        lines.add(BREAK);
        lines.add("§b预期 §f" + expect);
        for (int index = 0; index < steps.length; index++) {
            lines.add(BREAK);
            lines.add("§7" + (index + 1) + ". " + steps[index]);
        }
        return lines;
    }

    // -- what the plugin asks ----------------------------------------------

    public Trigger triggerAt(Location at) {
        Fixture fixture = fixtures.get(blockKey(at));
        return fixture == null ? null : fixture.trigger();
    }

    public boolean inside(Location at) {
        return boxes.stream().anyMatch(box -> box.holds(at));
    }

    /** A named place a section put down, so a switch's behaviour does not repeat the maths. */
    public Location point(String key) {
        return points.get(key);
    }

    /** A named volume a section claims, for things that have to know where they are. */
    public Box zone(String key) {
        return zones.get(key);
    }

    /** How many rooms a race's hall has. */
    public int roomCount(String race) {
        List<Location> rooms = roomSpawns.get(race);
        return rooms == null ? 0 : rooms.size();
    }

    /** Where the guide of one room stands, or {@code null} if there is no such room. */
    public Location guideSpot(String race, int index) {
        return guideSpot.get(race + "." + index);
    }

    /**
     * Which room a place is in, as {@code race.index}, or {@code null} when it is in none.
     *
     * <p>The same lookup the reach checks do, turned outwards: a guide is brought up for the room
     * somebody is standing in, so "which room is this" has to be answerable from a location.
     */
    public String roomAt(Location at) {
        for (Map.Entry<String, Box> zone : zones.entrySet()) {
            if (zone.getKey().startsWith("room.") && zone.getValue().holds(at)) {
                return zone.getKey().substring("room.".length());
            }
        }
        return null;
    }

    /** What the guide of one room says, as a list of lines to be read out in order. */
    public List<String> guideTalk(String race, int index) {
        List<String> talk = guideTalk.get(race + "." + index);
        return talk == null ? List.of() : talk;
    }

    /** Where one room of one hall starts, or {@code null} if there is no such room. */
    public Location roomSpawn(String race, int index) {
        List<Location> rooms = roomSpawns.get(race);
        if (rooms == null || index < 0 || index >= rooms.size()) {
            return null;
        }
        return rooms.get(index);
    }

    /**
     * Which room of a race's hall a player is standing in, or {@code -1}.
     *
     * <p>"The next room" is a step along the order the steps were written in, and that order is
     * not the same as the order the rooms sit in - a serpentine row runs one way and the next row
     * runs back. So the switch asks where the player is and walks the list, rather than adding one
     * to a coordinate and hoping.
     */
    public int roomIndexAt(String race, Location at) {
        int count = roomCount(race);
        for (int index = 0; index < count; index++) {
            Box box = zones.get("room." + race + "." + index);
            if (box != null && box.holds(at)) {
                return index;
            }
        }
        return -1;
    }

    /**
     * Where each section wants the stand-in to stand, by key.
     *
     * <p>These are places, not entities. The stand-in is one player - a real {@code ServerPlayer}
     * with no client behind it - and there is exactly one of him, so he cannot be pinned down at
     * every mark at once. He is called to whichever one the player is standing at.
     */
    public Map<String, Location> sparMarks() {
        return sparMarks;
    }

    /**
     * Every switch the hall placed, with what it does. Reading this is how a test - or a person
     * with a console - finds a section's button without counting blocks from a corner and
     * getting it wrong, which is a mistake that has been paid for more than once.
     */
    public Map<Location, Trigger> switches() {
        Map<Location, Trigger> found = new LinkedHashMap<>();
        fixtures.forEach((at, fixture) -> {
            if (fixture.trigger() != null) {
                found.put(at, fixture.trigger());
            }
        });
        return found;
    }

    /** Where a section's head is, so a caller can tell one section from another. */
    public Location sectionAt(String race, int index) {
        return points.get("sec." + race + "." + index);
    }

    public Location lobbySpawn() {
        return lobbySpawn;
    }

    public Location outsideSpawn() {
        return outsideSpawn;
    }

    /**
     * Every priest altar the hall built. A church used to be one point in the config, so a hall
     * that built two altars could only ever have one of them answer; the harness registers all
     * of these as churches.
     */
    public List<Location> priestAltars() {
        return priestAltars;
    }

    public Location netherLava() {
        return netherLava;
    }

    public boolean refill(Inventory inventory) {
        Location at = inventory.getHolder() instanceof Chest chest
                ? chest.getLocation()
                : inventory.getLocation();
        Map<Integer, ItemStack> layout = at == null ? null : chests.get(blockKey(at));
        if (layout == null) {
            return false;
        }
        inventory.clear();
        layout.forEach(inventory::setItem);
        return true;
    }

    /**
     * Puts every switch, sign and chest back the way it was built. The switches are the way out
     * of a hall, so a switch that can go missing is the same as no way out. The stand-in is not
     * furniture: he is a player, he is put wherever a section asks for him, and there is nothing
     * of his to put back.
     */
    public boolean repair() {
        boolean repaired = false;
        for (Map.Entry<Location, Fixture> entry : fixtures.entrySet()) {
            Fixture fixture = entry.getValue();
            Block block = entry.getKey().getBlock();
            if (asBuilt(block, fixture)) {
                continue;
            }
            place(block, fixture.type(), fixture.facing(), fixture.lines());
            if (block.getState() instanceof Chest chest) {
                refill(chest.getInventory());
            }
            repaired = true;
        }
        return repaired;
    }

    /**
     * Puts a hall's boats back on their marks.
     *
     * <p>A boat is furniture of a room that cannot be run without it - the one in the ghoul's pool
     * is there so that "a boat is how you stay dry" can be sat in rather than read - and a boat is
     * not a block, so none of the repair above touches it. It cannot be broken any more: the guard
     * refuses every blow aimed at it. It can still be <em>lost</em>, though, and rowing it out of
     * the pool is the obvious way - the board tells the player to get in it, and getting in it is
     * also how a boat is moved. A room whose one prop is three rooms away is a dead room.
     *
     * <p>Only ever looked for in a chunk that is already loaded. An unloaded chunk still holds its
     * entities, so a search there would answer "gone" and put a second boat in the pool every two
     * seconds - and by the time anybody looked, the pool would be full of them.
     */
    public boolean restoreBoats() {
        boolean put = false;
        for (Moored spot : moored) {
            World target = Bukkit.getWorld(spot.world());
            if (target == null
                    || !target.isChunkLoaded(((int) Math.floor(spot.x())) >> 4,
                            ((int) Math.floor(spot.z())) >> 4)) {
                continue;
            }
            Location home = new Location(target, spot.x(), spot.y(), spot.z());
            List<Entity> here = new ArrayList<>();
            for (Entity entity : target.getNearbyEntities(home, 16, 16, 16)) {
                if (entity.getType() == spot.type() && entity.getScoreboardTags().contains(MOORED)) {
                    here.add(entity);
                }
            }
            if (here.isEmpty()) {
                Entity fresh = target.spawnEntity(home, spot.type());
                fresh.setPersistent(true);
                fresh.addScoreboardTag(MOORED);
                put = true;
                continue;
            }
            for (Entity boat : here) {
                // Never while somebody is in it: a boat is moved by the person sitting in it, and
                // yanking it out from under them is how a step becomes a puzzle. They get out, and
                // the next sweep brings it home.
                if (boat.getPassengers().isEmpty() && boat.getLocation().distanceSquared(home) > 16) {
                    boat.teleport(home);
                    put = true;
                }
            }
        }
        return put;
    }

    /** Where the boats the hall put down belong, for a probe that wants to hit one. */
    public List<Location> boatSpots() {
        List<Location> spots = new ArrayList<>();
        for (Moored spot : moored) {
            World target = Bukkit.getWorld(spot.world());
            if (target != null) {
                spots.add(new Location(target, spot.x(), spot.y(), spot.z()));
            }
        }
        return spots;
    }

    private static boolean asBuilt(Block block, Fixture fixture) {        if (block.getType() != fixture.type()) {
            return false;
        }
        if (block.getBlockData() instanceof Directional directional && fixture.facing() != null
                && directional.getFacing() != fixture.facing()) {
            return false;
        }
        if (fixture.lines() == null) {
            return true;
        }
        if (!(block.getState() instanceof Sign sign)) {
            return false;
        }
        for (int line = 0; line < fixture.lines().size() && line < 4; line++) {
            if (!fixture.lines().get(line).equals(sign.getLine(line))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Every fixture that does not look the way it was built, as {@code x,y,z wanted/got}. A hall
     * whose furniture needs putting back every two seconds is a hall that is being changed by
     * something, and this is how the something is found instead of guessed at.
     */
    public List<String> wrongFixtures() {
        List<String> wrong = new ArrayList<>();
        for (Map.Entry<Location, Fixture> entry : fixtures.entrySet()) {
            Block block = entry.getKey().getBlock();
            if (!asBuilt(block, entry.getValue())) {
                String detail = "";
                if (entry.getValue().lines() != null && block.getState() instanceof Sign sign) {
                    for (int line = 0; line < 4; line++) {
                        String want = line < entry.getValue().lines().size()
                                ? entry.getValue().lines().get(line) : "";
                        if (!want.equals(sign.getLine(line))) {
                            detail = " line" + line + " wanted=[" + want + "] got=["
                                    + sign.getLine(line) + "]";
                            break;
                        }
                    }
                }
                wrong.add(block.getWorld().getName() + " " + block.getX() + "," + block.getY() + ","
                        + block.getZ() + " wanted=" + entry.getValue().type() + " facing="
                        + entry.getValue().facing() + " got=" + block.getType() + " facing="
                        + (block.getBlockData() instanceof Directional directional
                                ? directional.getFacing() : "-") + detail);
            }
        }
        return wrong;
    }

    /** Every place a section has asked the stand-in to stand, so a report can name them. */
    public String sparReport() {
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, Location> entry : sparMarks.entrySet()) {
            Location at = entry.getValue();
            out.append(' ').append(entry.getKey()).append('@')
                    .append(at.getBlockX()).append(',').append(at.getBlockY()).append(',')
                    .append(at.getBlockZ());
        }
        return out.toString();
    }

    /**
     * Every living thing standing inside one of the halls.
     *
     * <p>Remembering the mobs a section called up is not enough to take them all back. Some of
     * them turn into a <em>different entity</em> - a piglin that stays in the overworld is
     * replaced by a zombified piglin with a new id - and some of them bring friends, which is
     * how an evoker's vexes appeared in a hall the button had already cleared once. What all of
     * them still have in common is where they are standing.
     */
    public List<Entity> livingInHalls() {
        List<Entity> found = new ArrayList<>();
        for (Map.Entry<String, Box> entry : zones.entrySet()) {
            if (!entry.getKey().startsWith("hall.")) {
                continue;
            }
            Box box = entry.getValue();
            World target = Bukkit.getWorld(box.world());
            if (target == null) {
                continue;
            }
            Location middle = new Location(target,
                    (box.x1() + box.x2()) / 2.0, (box.y1() + box.y2()) / 2.0,
                    (box.z1() + box.z2()) / 2.0);
            double rx = (box.x2() - box.x1()) / 2.0 + 2;
            double ry = (box.y2() - box.y1()) / 2.0 + 4;
            double rz = (box.z2() - box.z1()) / 2.0 + 2;
            for (Entity entity : target.getNearbyEntities(middle, rx, ry, rz)) {
                if (!(entity instanceof LivingEntity living) || entity instanceof Player
                        || entity instanceof ArmorStand) {
                    continue;
                }
                // Something a player owns - the wolf a werewolf called, the one an angel tamed -
                // belongs to them, not to the hall. Clearing it along with the hall's own
                // monsters both loses their pet and, because the plugin only hears about a
                // tamed animal dying, leaves it counted against their control limit for good.
                if (living instanceof Tameable tameable && tameable.isTamed()
                        && tameable.getOwner() instanceof Player) {
                    continue;
                }
                found.add(living);
            }
        }
        return found;
    }

    /** The volumes the hall protects, one per line - worth asking rather than guessing. */
    public String describeBounds() {        StringBuilder out = new StringBuilder();
        for (Box box : boxes) {
            out.append(System.lineSeparator()).append("  ").append(box.world())
                    .append(' ').append(box.x1()).append(',').append(box.y1()).append(',').append(box.z1())
                    .append(" .. ").append(box.x2()).append(',').append(box.y2()).append(',').append(box.z2());
        }
        return out.toString();
    }

    // -- worlds ------------------------------------------------------------

    /**
     * A world of one race's own, generated empty and kept for as long as the server runs. The
     * plugin reads the clock and the weather of the world a player is standing in, so a hall
     * whose test needs rain or midnight gets a world where that costs nobody else anything.
     */
    private static World clockWorld(World base, String race) {
        String name = base.getName() + "_mmtest_" + race;
        World existing = Bukkit.getWorld(name);
        if (existing != null) {
            return existing;
        }
        // A hall whose step turns the weather on has to stand in a biome that has weather. Every
        // hall but the demon's used to be the_void, and the void never rains - the client draws
        // nothing there whatever the world's storm flag says - so "切换下雨" looked like a switch
        // wired to nothing. Worse, the flag it did set was the one the ghoul's regeneration reads:
        // the healing stopped on schedule, for a rain that was not on the screen.
        boolean weatherHall = "ghoul".equals(race) || "vampire".equals(race);
        String biome = "demon".equals(race) ? "minecraft:snowy_plains"
                : weatherHall ? "minecraft:plains" : "minecraft:the_void";
        World created = new WorldCreator(name)
                .environment(World.Environment.NORMAL)
                .type(WorldType.FLAT)
                .generatorSettings("{\"layers\":[],\"biome\":\"" + biome + "\"}")
                .seed(0L)
                .generateStructures(false)
                .createWorld();
        if (created == null) {
            throw new IllegalStateException("could not create the test world " + name);
        }
        created.setGameRule(GameRule.SPAWN_RADIUS, 0);
        created.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        // A world made at run time comes up on peaceful, and on peaceful nothing can hurt a
        // player - so a wolf that is meant to kill somebody would only walk up and stare.
        created.setDifficulty(Difficulty.NORMAL);
        return created;
    }

    // -- blocks ------------------------------------------------------------

    /**
     * One room. A solid shell, a themed floor, a skirting, a band of windows, and a ceiling with
     * lights on a tight grid - the lights matter: a hall nobody can see in is a hall nobody can
     * test in.
     */
    private void room(World target, int x, int y, int z, int length, int depth, int headroom,
            Theme theme) {
        int top = y + headroom;
        int farX = x + length - 1;
        int farZ = z + depth - 1;

        box(target, theme.wall(), x - 1, y - 1, z - 1, farX + 1, top + 1, farZ + 1);
        box(target, AIR, x, y + 1, z, farX, top, farZ);

        box(target, theme.trim(), x, y, z, farX, y, farZ);
        box(target, theme.floor(), x + 1, y, z + 1, farX - 1, y, farZ - 1);

        edge(target, x, y, z, length, depth, theme.skirting());
        edge(target, x, y + 1, z, length, depth, theme.skirting());
        edge(target, x, top, z, length, depth, theme.window());

        // A tall hall cannot be lit from its own ceiling. Light falls off about a level a block,
        // so a lamp thirty blocks overhead leaves the floor as dark as no lamp at all - which is
        // exactly what the angel's hall was, and the vampire's was not much better. Rooms with
        // headroom to spare get their lamps set into the walls instead, at a height that reaches
        // the floor: same material, same grid, just somewhere useful.
        // Every two blocks, not every four. Light falls off a level a block, so a switch two
        // blocks from the nearest lamp reads twelve and one four away reads eight - and eight is
        // a board nobody can read. The two halls that still use this builder are the nether's,
        // where the rooms are corridors and the boards sit right under this band.
        if (headroom > 8) {
            for (int dx = 1; dx < length; dx += 2) {
                set(target, theme.light(), x + dx, y + 3, z - 1);
                set(target, theme.light(), x + dx, y + 3, farZ + 1);
            }
            for (int dz = 1; dz < depth; dz += 2) {
                set(target, theme.light(), x - 1, y + 3, z + dz);
                set(target, theme.light(), farX + 1, y + 3, z + dz);
            }
        }
        // A hall as tall as the vampire's or the angel's - thirty-four blocks - is two rooms
        // stacked up, and one band of lamps at knee height leaves the top half in the dark.
        if (headroom > 20) {
            for (int dx = 1; dx < length; dx += 4) {
                set(target, theme.light(), x + dx, y + 12, z - 1);
                set(target, theme.light(), x + dx, y + 12, farZ + 1);
            }
        }

        box(target, theme.wall(), x, top + 1, z, farX, top + 1, farZ);
        for (int dx = 1; dx < length; dx += LIGHT_SPACING) {
            for (int dz = 1; dz < depth; dz += LIGHT_SPACING) {
                set(target, theme.light(), x + dx, top + 1, z + dz);
            }
        }
        boxes.add(new Box(target.getName(), x - 1, y - 1, z - 1, farX + 1, top + 1, farZ + 1));
    }

    /** A section's title and teaching lines, hung along the near wall in reading order. */
    private void sectionHead(World target, int y, int x, int z, String title, List<String> lines,
            int maxBoards) {
        List<String> text = new ArrayList<>();
        text.add(title);
        text.addAll(lines);
        wallSign(target, y, x + 2, z, maxBoards, text.toArray(new String[0]));
    }

    /**
     * A run of signs on a hall's near wall, four lines each, readable from inside.
     *
     * <p>Every line is wrapped first. A sign board's text area is about ninety pixels and the
     * default font is six pixels a character - nine for a full-width one - so a line much past
     * fifteen letters (or ten characters of Chinese) runs off the board and over whatever is
     * beside it. A section's goal is a sentence, not a label: without wrapping, the boards next
     * to each other become one unreadable smear, and the parts that matter are the parts that
     * get lost.
     */
    private void wallSign(World target, int y, int x, int z, String... lines) {
        wallSign(target, y, x, z, MAX_BOARDS, lines);
    }

    /** The same, for a run of signs that owns more or less wall than the default. */
    private void wallSign(World target, int y, int x, int z, int maxBoards, String... lines) {
        signBoard(target, x, y + BOARD_Y, z - 1, BlockFace.SOUTH, maxBoards, wrapAll(lines));
    }

    /**
     * Writes wrapped lines out, four to a board, with further boards along the same wall.
     *
     * <p>How many boards a run may use is not a constant: it is how much wall the section owns. A
     * section takes a whole number of slots and a slot is {@link #SLOT} columns, so a three-slot
     * section has columns to spare while a one-slot section has nine - and a cap that ignores that
     * either wastes the wide sections' room or lets a narrow one write over its neighbour. The
     * caller passes the width it owns, which is always what the section was sized for.
     */
    private void signBoard(World target, int supportX, int supportY, int supportZ, BlockFace side,
            int maxBoards, List<String> lines) {
        signBoard(target, supportX, supportY, supportZ, side, maxBoards, null, lines);
    }

    /**
     * The same, with every board it writes carrying the same trigger.
     *
     * <p>An action whose name needs more than one board is one action, so all of its boards have
     * to do the same thing - otherwise a player reads the second half of a sentence and finds
     * that half of it is dead. Clicking any of them is clicking the action.
     */
    private void signBoard(World target, int supportX, int supportY, int supportZ, BlockFace side,
            int maxBoards, Trigger trigger, List<String> lines) {
        List<String> board = new ArrayList<>();
        int placed = 0;
        for (String line : lines) {
            if (BREAK.equals(line)) {
                if (!board.isEmpty()) {
                    writeBoard(target, supportX, supportY, supportZ, side, maxBoards, placed++, board,
                            trigger);
                    board.clear();
                }
                continue;
            }
            if (board.size() == LINES_PER_BOARD) {
                writeBoard(target, supportX, supportY, supportZ, side, maxBoards, placed++, board,
                        trigger);
                board.clear();
            }
            board.add(line);
        }
        if (!board.isEmpty() && placed < maxBoards) {
            writeBoard(target, supportX, supportY, supportZ, side, maxBoards, placed, board, trigger);
        }
    }

    private void writeBoard(World target, int supportX, int supportY, int supportZ, BlockFace side,
            int maxBoards, int board, List<String> lines, Trigger trigger) {
        if (board >= maxBoards) {
            boardsDropped++;
            droppedBoards.add(target.getName() + " " + (supportX + board) + "," + supportY + ","
                    + supportZ + " allowed=" + maxBoards + " text=" + lines.get(0));
            return;
        }
        signOn(target, supportX + board, supportY, supportZ, side, trigger,
                lines.toArray(new String[0]));
    }

    /** Boards a section had nowhere left to write. Zero means every word fitted. */
    public int boardsDropped() {
        return boardsDropped;
    }

    /**
     * The clickable things that are not boards, which is meant to be only the exits.
     *
     * <p>Every action in the hall is a board a player right-clicks; the exits keep their buttons
     * because the red wool under them is the landmark. So this is the list that says whether that
     * is still true - and it is a list of locations rather than a number, because "one per room" is
     * only a good answer if you can see which rooms are in it.
     */
    public List<Location> buttonFixtures() {
        List<Location> out = new ArrayList<>();
        for (Map.Entry<Location, Fixture> entry : fixtures.entrySet()) {
            if (entry.getValue().type() != Material.OAK_WALL_SIGN && entry.getValue().trigger() != null) {
                out.add(entry.getKey());
            }
        }
        return out;
    }

    /**
     * A light reading for everything a player has to read or press, and for the middle of every
     * room.
     *
     * <p>Two different questions with two different answers. A board or a switch is read from
     * arm's length, so it is the one thing that has to be bright: mounting a lamp directly over
     * every switch is what makes that true by construction. The floor and the middle of a room are
     * looked at from across the room, and only have to be bright enough to walk and fight in.
     * Reporting them together, with the ones that fail named, is what turns "it feels dark in
     * here" into a room number.
     */
    public List<String> lightReport() {
        List<String> out = new ArrayList<>();
        int worstSign = 15;
        int worstButton = 15;
        int worstFloor = 15;
        for (Map.Entry<Location, Fixture> entry : fixtures.entrySet()) {
            Fixture fixture = entry.getValue();
            Block block = entry.getKey().getBlock();
            if (!block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4)) {
                continue;
            }
            int level = block.getLightFromBlocks();
            if (fixture.type() == Material.OAK_WALL_SIGN) {
                worstSign = Math.min(worstSign, level);
                if (level < LIGHT_MIN) {
                    out.add("sign " + place(block) + " light=" + level + " text=" + entry.getValue()
                            .lines().get(0));
                }
            } else if (fixture.trigger() != null) {
                worstButton = Math.min(worstButton, level);
                if (level < LIGHT_MIN) {
                    out.add("button " + place(block) + " light=" + level + " kind="
                            + fixture.trigger().kind());
                }
            }
        }
        for (Map.Entry<String, List<Location>> race : roomSpawns.entrySet()) {
            for (int index = 0; index < race.getValue().size(); index++) {
                String key = race.getKey() + "." + index;
                Box box = zones.get("room." + key);
                if (box == null) {
                    continue;
                }
                World target = Bukkit.getWorld(box.world());
                if (target == null) {
                    continue;
                }
                // Not the geometric centre: half the rooms have something standing in the
                // middle of them, and what matters is the spot a player arrives on.
                Location landing = race.getValue().get(index);
                int middleX = landing.getBlockX();
                int middleZ = landing.getBlockZ();
                // The middle of a room is often occupied - an altar, a pool, a plinth - and an
                // opaque block reports no light at all because light does not pass into it. What
                // is being asked is whether a player can see where they are going, so the reading
                // is taken at the first block above the floor that somebody could stand in.
                int level = 0;
                for (int dy = 2; dy <= 6; dy++) {
                    Block at = target.getBlockAt(middleX, box.y1() + dy, middleZ);
                    if (at.isPassable()) {
                        level = at.getLightFromBlocks();
                        break;
                    }
                }
                worstFloor = Math.min(worstFloor, level);
                if (level < 4) {
                    out.add("floor " + key + " light=" + level + " at " + middleX + ","
                            + (box.y1() + 2) + "," + middleZ);
                }
            }
        }
        out.add(0, "boards_min=" + worstSign + " buttons_min=" + worstButton
                + " floor_min=" + worstFloor);
        return out;
    }

    /** Where every room is and how tall it is, so a report can be checked against a tape measure. */
    public List<String> heightReport() {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : roomHeads.entrySet()) {
            out.add(entry.getKey() + " headroom=" + entry.getValue());
        }
        return out;
    }

    /** The races whose halls exist, in the order they were built. */
    public List<String> races() {
        return List.copyOf(roomSpawns.keySet());
    }

    private static String place(Block block) {
        return block.getWorld().getName() + " " + block.getX() + "," + block.getY() + ","
                + block.getZ();
    }

    /**
     * Switches with nothing behind them.
     *
     * <p>A switch is mounted on a block, and the game pops it off the wall when that block goes -
     * so a switch with a hole behind it is the one piece of furniture in the building that can be
     * there in the morning and gone in the afternoon, and the only way to find one is to look
     * behind every switch in the building. This is what the complaint about buttons floating in
     * the air looked like from the console: a switch whose back is to nothing.
     */
    public List<String> unsupportedSwitches() {
        List<String> out = new ArrayList<>();
        for (Map.Entry<Location, Fixture> entry : fixtures.entrySet()) {
            Fixture fixture = entry.getValue();
            if (fixture.trigger() == null || fixture.facing() == null) {
                continue;
            }
            Block block = entry.getKey().getBlock();
            if (!block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4)) {
                continue;
            }
            Block behind = block.getRelative(fixture.facing().getOppositeFace());
            if (!behind.getType().isSolid()) {
                out.add(place(block) + " kind=" + fixture.trigger().kind() + " behind="
                        + behind.getType());
            }
        }
        return out;
    }

    /**
     * Switches and boards hung higher up a wall than anybody can use.
     *
     * <p>A player's reach is a few blocks and their eyes are one and a half up, so a switch five or
     * six blocks over the floor cannot be pressed from the floor at all, and a board that high is
     * read with the head tipped back. This has happened twice in this building, and both times it
     * looked fine from across the room - so it is a number now, printed by `mmp signage`, rather
     * than something anybody has to remember to look at.
     */
    public List<String> outOfReach() {
        List<String> out = new ArrayList<>();
        for (Map.Entry<Location, Fixture> entry : fixtures.entrySet()) {
            Fixture fixture = entry.getValue();
            boolean board = fixture.type() == Material.OAK_WALL_SIGN;
            if (!board && fixture.trigger() == null) {
                continue;
            }
            Location at = entry.getKey();
            Integer floor = floorUnder(at);
            if (floor == null) {
                continue;
            }
            int height = at.getBlockY() - floor;
            int limit = board ? BOARD_REACH : BUTTON_REACH;
            if (height > limit) {
                out.add((board ? "sign " : "button ") + place(at.getBlock()) + " height=" + height
                        + " limit=" + limit + " kind="
                        + (fixture.trigger() == null ? "board" : fixture.trigger().kind()));
            }
        }
        return out;
    }

    /**
     * Boards hanging outside the band a player can actually read.
     *
     * <p>Two to four blocks over the floor of their room is the band: two is across the chest, three
     * is where the eyes are, and four is read by looking up a little. Anything above that is read by
     * tipping the head back, and anything below is not read at all - so this is the rule that says
     * a board is a board rather than scenery, and it is checked here rather than remembered.
     *
     * <p>This hall has got it wrong twice, in opposite directions both times: first a second row
     * nine blocks up that nobody could reach, then a teaching wall at one block, which is the top
     * of the player's own feet and below the line of sight of anybody standing in front of it.
     */
    public List<String> signsOffIdeal() {
        List<String> out = new ArrayList<>();
        for (Map.Entry<Location, Fixture> entry : fixtures.entrySet()) {
            if (entry.getValue().type() != Material.OAK_WALL_SIGN) {
                continue;
            }
            Location at = entry.getKey();
            Integer floor = floorUnder(at);
            if (floor == null) {
                continue;
            }
            int height = at.getBlockY() - floor;
            if (height < BOARD_MIN || height > BOARD_MAX) {
                out.add("sign " + place(at.getBlock()) + " height=" + height
                        + " band=" + BOARD_MIN + ".." + BOARD_MAX + " kind="
                        + (entry.getValue().trigger() == null
                                ? "plain" : entry.getValue().trigger().kind()));
            }
        }
        return out;
    }

    /**
     * Boards hanging at the player's own feet.
     *
     * <p>Reported apart from {@link #signsOffIdeal()} because it is the mistake that was made, and
     * because "one block over the floor" sounds harmless right up until somebody stands in front of
     * it. A board there is below the line of sight of a standing player: they have to walk backwards
     * and look down to read it.
     */
    public List<String> signsAtKnee() {
        List<String> out = new ArrayList<>();
        for (String line : signsOffIdeal()) {
            if (line.contains(" height=" + (BOARD_MIN - 1) + " ")) {
                out.add(line);
            }
        }
        return out;
    }

    /** The floor of the room a block stands in, or {@code null} when it is not in a room. */
    private Integer floorUnder(Location at) {
        for (Map.Entry<String, Box> zone : zones.entrySet()) {
            if (zone.getKey().startsWith("room.") && zone.getValue().holds(at)) {
                return zone.getValue().y1() + 1;
            }
        }
        return null;
    }

    /** How many rooms the halls are made of. */
    public int roomTotal() {
        return roomsBuilt;
    }

    /**
     * Rooms that owe a board about their own hour, weather or ground and did not write one.
     *
     * <p>These are the rules a player cannot see: the cause is in the sky, the clock or a biome
     * they have no way of looking at. A room like that with no board is not under-decorated, it is
     * unanswerable.
     */
    public List<String> scenesMissing() {
        return List.copyOf(scenesMissing);
    }

    /** Rooms with no way out, which should never be anything - the check exists to keep it so. */
    public List<String> exitsMissing() {
        List<String> missing = new ArrayList<>();
        for (String key : roomSpawns.keySet()) {
            for (int index = 0; index < roomCount(key); index++) {
                if (!roomsWithExit.contains(key + "." + index)) {
                    missing.add(key + "." + index);
                }
            }
        }
        return missing;
    }

    /** Which boards were dropped, and where. Empty means every word fitted. */
    public List<String> droppedBoardReport() {
        return List.copyOf(droppedBoards);
    }

    /** Boards two sections both wrote. Zero means no section is reading over another's shoulder. */
    public int signCollisions() {
        return signCollisions;
    }

    private static List<String> wrapAll(String... lines) {
        List<String> out = new ArrayList<>();
        for (String line : lines) {
            out.addAll(wrap(line));
        }
        return out;
    }

    /**
     * Splits one line to what fits on a board, in units of one ASCII character.
     *
     * <p>A colour code takes no room and is carried onto the next line, so a wrapped line does
     * not come back in the default colour halfway through.
     */
    private static List<String> wrap(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        String colour = "";
        double width = 0;
        for (int index = 0; index < line.length(); index++) {
            char c = line.charAt(index);
            if (c == '\u00a7' && index + 1 < line.length()) {
                colour = line.substring(index, index + 2);
                current.append(colour);
                index++;
                continue;
            }
            double size = c >= 0x2E80 ? 1.5 : (c == ' ' ? 0.5 : 1.0);
            if (width + size > SIGN_WIDTH && current.length() > 0 && !isClosing(c)) {
                out.add(current.toString());
                current.setLength(0);
                current.append(colour);
                width = 0;
            }
            current.append(c);
            width += size;
        }
        if (current.length() > 0) {
            out.add(current.toString());
        }
        return out;
    }

    /**
     * Punctuation that may not open a line. A wrapped sentence reads wrong with a lone "）" or
     * "，" sitting at the start of the next board, so one of these is allowed to overhang the
     * line it belongs to instead.
     */
    private static boolean isClosing(char c) {
        return "）)】」』》〉、。，,．.！!？?：:；;…".indexOf(c) >= 0;
    }

    private void edge(World target, int x, int y, int z, int length, int depth, Material material) {
        int farX = x + length - 1;
        int farZ = z + depth - 1;
        box(target, material, x - 1, y, z - 1, farX + 1, y, z - 1);
        box(target, material, x - 1, y, farZ + 1, farX + 1, y, farZ + 1);
        box(target, material, x - 1, y, z, x - 1, y, farZ);
        box(target, material, farX + 1, y, z, farX + 1, y, farZ);
    }

    private void layer(World target, Material material, int y, int fromX, int fromZ, int toX, int toZ) {
        box(target, material, fromX, y, fromZ, toX, y, toZ);
    }

    private void signOn(World target, int supportX, int supportY, int supportZ, BlockFace side,
            String... lines) {
        signOn(target, supportX, supportY, supportZ, side, (Trigger) null, lines);
    }

    private void signOn(World target, int supportX, int supportY, int supportZ, BlockFace side,
            Trigger trigger, String... lines) {
        sign(target, supportX + side.getModX(), supportY, supportZ + side.getModZ(), side, trigger,
                lines);
    }

    private void sign(World target, int x, int y, int z, BlockFace facing, String... lines) {
        sign(target, x, y, z, facing, (Trigger) null, lines);
    }

    /**
     * The same, on a board that is itself the action.
     *
     * <p>A board is where the words are and a switch is not, so a room that keeps them apart asks
     * the player to work out which switch belongs to which sentence - and a wall of switches with
     * a wall of boards over them is a guessing game with a right answer nobody can see. Making the
     * board the switch means the name and the action are one object.
     *
     * <p>Nothing about the clicking changes: {@code HallGuard.onInteract} looks the block up in
     * {@link #triggerAt} and does not care what it is made of, so a board triggers exactly the way
     * a button did. The one button left in the building is the exit, and it is a button because
     * the red wool under it is the landmark.
     */
    private void sign(World target, int x, int y, int z, BlockFace facing, Trigger trigger,
            String... lines) {
        Block block = target.getBlockAt(x, y, z);
        Fixture already = fixtures.get(blockKey(block.getLocation()));
        if (already != null && already.lines() != null
                && !already.lines().equals(List.of(lines))) {
            // Two sections writing the same board means whoever builds last wins and the other
            // section's text is gone. It is silent otherwise, and it has happened before.
            signCollisions++;
            Bukkit.getLogger().warning("two sections both wrote the sign at "
                    + block.getLocation().getWorld().getName() + " "
                    + block.getLocation().getBlockX() + "," + block.getLocation().getBlockY()
                    + "," + block.getLocation().getBlockZ()
                    + " - one of them loses its text; was ["
                    + String.join(" | ", already.lines()) + "] now ["
                    + String.join(" | ", lines) + "]");
        }
        List<String> text = tidy(List.of(lines));
        place(block, Material.OAK_WALL_SIGN, facing, text);
        fixtures.put(blockKey(block.getLocation()),
                new Fixture(Material.OAK_WALL_SIGN, facing, text, trigger));
    }

    /**
     * The same text with a colour code that repeats itself removed.
     *
     * <p>A board is built by joining pieces, and two of them can each carry a colour: "§7" then a
     * title that starts "§c" is "§7§c..." - two instructions in a row, and the server keeps the
     * second. The same goes for the same code twice. Either way the text that comes back off the
     * wall is not the text that was handed to it, which is invisible until something compares the
     * two - and the hall does, twice a second, to put back anything that was changed. A board like
     * that is rewritten for as long as the server is up. The last colour is the only one that
     * counts, so the last colour is the only one kept.
     */
    private static List<String> tidy(List<String> lines) {
        List<String> out = new ArrayList<>();
        for (String line : lines) {
            List<String> tokens = new ArrayList<>();
            char colour = 0;
            Set<Character> styles = new HashSet<>();
            for (int index = 0; index < line.length(); index++) {
                char c = line.charAt(index);
                if (c != '\u00a7' || index + 1 >= line.length()) {
                    tokens.add(String.valueOf(c));
                    continue;
                }
                char code = Character.toLowerCase(line.charAt(index + 1));
                index++;
                if (code == 'r') {
                    colour = 0;
                    styles.clear();
                } else if (code >= '0' && code <= '9' || code >= 'a' && code <= 'f') {
                    if (code == colour) {
                        continue;
                    }
                    // A colour written straight after another colour replaces it: the server drops
                    // the earlier one when it parses the text into a component, so keeping both is
                    // exactly what made the board on the wall differ from the board that was
                    // written down - and a board that differs is one the repair pass rewrites, for
                    // as long as the server is up.
                    while (!tokens.isEmpty() && isColour(tokens.get(tokens.size() - 1))) {
                        tokens.remove(tokens.size() - 1);
                    }
                    colour = code;
                    styles.clear();
                } else if (!styles.add(code)) {
                    continue;
                }
                tokens.add("\u00a7" + line.charAt(index));
            }
            out.add(String.join("", tokens));
        }
        return out;
    }

    private static boolean isColour(String token) {
        if (token.length() != 2 || token.charAt(0) != '\u00a7') {
            return false;
        }
        char code = Character.toLowerCase(token.charAt(1));
        return code >= '0' && code <= '9' || code >= 'a' && code <= 'f';
    }

    /**
     * A switch on the side of the block behind it. A button has to be mounted on something - the
     * game only lets one carry a horizontal facing, so a "floor button" is not a thing this API
     * can express, and asking for one throws while the block data is being set.
     */
    private void button(World target, int x, int y, int z, BlockFace facing, Trigger trigger) {
        if (facing.getModY() != 0) {
            throw new IllegalArgumentException("a button has to point sideways, not " + facing);
        }
        Block block = target.getBlockAt(x, y, z);
        place(block, Material.OAK_BUTTON, facing, null);
        fixtures.put(blockKey(block.getLocation()),
                new Fixture(Material.OAK_BUTTON, facing, null, trigger));
    }

    /** A switch with the sign that explains it standing over it. */
    private void switchpoint(World target, int y, int x, int z, Trigger trigger, String... lines) {
        // Collected, never placed here. A step declares what it offers while its contents are
        // being built, and the whole lot is hung on the far wall in one go afterwards - which is
        // what lets the wall be laid out by how many there are, instead of by coordinates every
        // step would have to keep in step with its neighbours. Where in the room the step asked
        // for it is therefore not used: a board is a board wherever it was written, and the wall
        // reads left to right in the order it asked.
        //
        // There used to be a second path here for the two nether cells, which built corridors and
        // so were said to have no wall to hang on. They have had one since they started calling
        // laySwitchWall, so the path was unreachable - and it built a stone post with a button on
        // it, which is the one shape this hall is trying to get rid of.
        switchPlans.add(new PlannedSwitch(trigger, wrapAll(lines)));
    }

    /**
     * Hangs the switches the room asked for along its far wall, in the order it asked for them.
     *
     * <p>The wall is the same wall in every room, so a player who has learnt one room has learnt
     * them all: switches at waist height in a row, their boards over them, and a lamp in the wall
     * behind each one. That last part is not decoration - a board is read from arm's length and has
     * to be lit like it, and putting the lamp behind the switch makes every switch and every board
     * in the building bright enough to read by construction rather than by luck.
     */
    private void laySwitchWall(World target, int y, int x, int z, int length, int side,
            Theme theme) {
        if (switchPlans.isEmpty()) {
            return;
        }
        int wallZ = z + side;
        // How many fit is a question about how much wall there is, and the wall a room's switches
        // hang on runs along the room's length - which for a room of the grid is its width, and for
        // the two nether cells is the corridor they are.
        int perRow = switchesPerRow(length);
        for (int index = 0; index < switchPlans.size(); index++) {
            PlannedSwitch planned = switchPlans.get(index);
            int bx = x + 1 + (index % perRow) * SWITCH_PITCH;
            int by = y + SWITCH_ROW_Y + (index / perRow) * SWITCH_ROW_GAP;
            // The lamp is the board's own support block first and its light second, so the board
            // is held up and lit by the wall it hangs on rather than by whatever else is nearby.
            set(target, theme.light(), bx, by, wallZ);
            signBoard(target, bx, by, wallZ, BlockFace.NORTH, SWITCH_BOARDS, planned.trigger(),
                    planned.lines());
        }
        switchPlans.clear();
    }

    /** How many switches fit along one wall of a room this wide, boards included. */
    private static int switchesPerRow(int side) {
        return Math.max(1, (side - 5) / SWITCH_PITCH + 1);
    }

    /**
     * A door, as two blocks rather than as two blocks that happen to be made of door.
     *
     * <p>A door is one structure: the lower block carries {@code half=lower} and the upper one
     * {@code half=upper}, and everything that reads a door - vanilla, and the plugin under test -
     * trusts that. Placing two blocks with {@code setType(IRON_DOOR)} leaves both of them at the
     * material's default, which is the bottom half, so the upper block was a bottom half standing
     * on the lower one. A click on it still opened a panel, but the "other half" the plugin
     * looked for was the air above it, and only one of the two moved.
     *
     * <p>Deliberately not registered in {@link #fixtures}: a {@link Fixture} remembers a
     * material, a facing and a sign's lines, and nothing about which half a block is, so the
     * repair pass would rebuild the door from the default state and undo exactly this fix.
     */
    private void door(World target, int x, int y, int z) {
        doorHalf(target.getBlockAt(x, y, z), Bisected.Half.BOTTOM);
        doorHalf(target.getBlockAt(x, y + 1, z), Bisected.Half.TOP);
    }

    private static void doorHalf(Block block, Bisected.Half half) {
        block.setType(Material.IRON_DOOR, false);
        if (block.getBlockData() instanceof Door data) {
            data.setFacing(BlockFace.NORTH);
            data.setHinge(Door.Hinge.LEFT);
            data.setHalf(half);
            data.setOpen(false);
            block.setBlockData(data, false);
        }
    }

    private void chest(World target, int x, int y, int z, BlockFace facing,
            Map<Integer, ItemStack> layout) {
        Block block = target.getBlockAt(x, y, z);
        place(block, Material.CHEST, facing, null);
        chests.put(blockKey(block.getLocation()), layout);
        fixtures.put(blockKey(block.getLocation()), new Fixture(Material.CHEST, facing, null, null));
        if (block.getState() instanceof Chest chest) {
            chest.getInventory().clear();
            layout.forEach(chest.getInventory()::setItem);
        }
    }

    /**
     * A chest with six rows: two chests side by side, with the layout split across them. They are
     * kept as two containers rather than one merged one on purpose - a merged chest is only made
     * when the game gets a block update, and a hall built without physics does not give it one,
     * so a six-row layout written into the first half would simply not fit.
     */
    private void largeChest(World target, int x, int y, int z, Map<Integer, ItemStack> layout) {
        Map<Integer, ItemStack> first = half(layout, 0);
        Map<Integer, ItemStack> second = half(layout, 27);
        chest(target, x, y, z, BlockFace.NORTH, first);
        if (!second.isEmpty()) {
            chest(target, x + 1, y, z, BlockFace.NORTH, second);
        }
    }

    /** The rows one chest of a pair holds, renumbered from its own first row. */
    private static Map<Integer, ItemStack> half(Map<Integer, ItemStack> layout, int from) {
        Map<Integer, ItemStack> split = new LinkedHashMap<>();
        layout.forEach((slot, item) -> {
            if (slot >= from && slot < from + 27) {
                split.put(slot - from, item);
            }
        });
        return split;
    }

    /**
     * Where a section wants the stand-in to stand.
     *
     * <p>Nothing is placed here. The stand-in is a player that is called to the mark when the
     * section's switch is pressed, so a hall that is only ever walked through costs one imaginary
     * player rather than one per section - and a section that wants him to be hit, healed,
     * converted or killed is working with a real player's health bar rather than an armour stand
     * that has to be talked out of dying on the first hit.
     */
    private void sparMark(World target, String key, int x, int y, int z) {
        sparMarks.put(key, new Location(target, x + 0.5, y + 1, z + 0.5));
    }

    /** Puts one fixture down: its type, the way it faces, and the words on it if it is a sign. */
    private static void place(Block block, Material type, BlockFace facing, List<String> lines) {
        block.setType(type, false);
        if (block.getBlockData() instanceof Directional directional) {
            directional.setFacing(facing);
            block.setBlockData(directional, false);
        }
        if (lines != null && block.getState() instanceof Sign sign) {
            for (int line = 0; line < lines.size() && line < 4; line++) {
                sign.setLine(line, lines.get(line));
            }
            sign.update(true, false);
        }
    }

    /** A chest layout with the items centred in the middle row, in the order given. */
    private static Map<Integer, ItemStack> centred(ItemStack... items) {
        int first = items.length > 9 ? 0 : 9 + (9 - items.length) / 2;
        Map<Integer, ItemStack> layout = new LinkedHashMap<>();
        for (int index = 0; index < items.length; index++) {
            layout.put(first + index, items[index]);
        }
        return layout;
    }

    /** A chest of one item: {@code count} stacks of {@code stackSize}. */
    private static Map<Integer, ItemStack> fill(Map<Integer, ItemStack> layout, Material material,
            int stackSize, int count) {
        for (int index = 0; index < count; index++) {
            layout.put(9 + index, new ItemStack(material, stackSize));
        }
        return layout;
    }

    /**
     * The plugin's own food list, so the food step tests what the plugin actually refuses rather
     * than a list written out here that can drift away from it.
     */
    private static Map<Integer, ItemStack> foodList() {
        Map<Integer, ItemStack> layout = new LinkedHashMap<>();
        int slot = 0;
        for (Material material : SNConfigHandler.foodMaterials) {
            if (material != null && slot < 54) {
                layout.put(slot++, new ItemStack(material, 1));
            }
        }
        return layout;
    }

    /** The wolfbane recipe, read from the plugin so the chest cannot be out of date. */
    private static Map<Integer, ItemStack> wereWolfbaneList() {
        Map<Integer, ItemStack> layout = new LinkedHashMap<>();
        int slot = 0;
        for (Map.Entry<Material, Integer> entry
                : SNConfigHandler.wereWolfbaneRecipe.materialQuantities.entrySet()) {
            if (entry.getKey() != null && slot < 54) {
                layout.put(slot++, new ItemStack(entry.getKey(), Math.min(64, entry.getValue())));
            }
        }
        return layout;
    }

    /** What the church accepts for power, read from the plugin. */
    private static Map<Integer, ItemStack> donationList() {
        Map<Integer, ItemStack> layout = new LinkedHashMap<>();
        int slot = 0;
        for (Material material : SNConfigHandler.priestDonationMap.keySet()) {
            if (material != null && slot < 27) {
                layout.put(slot++, new ItemStack(material, 8));
            }
        }
        return layout;
    }

    /** A chest of the items a spell section casts with, so nothing has to be fetched first. */
    private void spellBench(World target, int y, int x, int z, Material... materials) {
        chest(target, x + 2, y + 1, z + DEPTH - 2, BlockFace.NORTH, centred(
                java.util.Arrays.stream(materials)
                        .map(material -> new ItemStack(material, 8))
                        .toArray(ItemStack[]::new)));
    }

    /** A spell switch: the item the priest holds while attacking the stand-in. */
    private static Trigger spell(String material) {
        return Trigger.with(Kind.TEST_SPELL, "priest", material);
    }

    /** A bow target: the stand-in's mark, with a wall of colour behind it to aim at. */
    private void longSparMark(World target, String key, int x, int y, int z) {
        box(target, Material.WHITE_CONCRETE, x - 1, y + 1, z - 2, x - 1, y + 3, z + 2);
        box(target, Material.RED_CONCRETE, x - 1, y + 2, z - 1, x - 1, y + 2, z + 1);
        sparMark(target, key, x, y, z);
    }

    private void box(World target, Material material, int x1, int y1, int z1,
            int x2, int y2, int z2) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                    set(target, material, x, y, z);
                }
            }
        }
    }

    /**
     * Everything the hall placed in one room that a player can touch, and the text on it.
     *
     * <p>A board is what a player is told to do, and this is the only way to read one without
     * walking to the room. It earns its place because a board can be wrong in a way nothing else
     * notices: it can name a room that does not exist, offer a shrine the race does not have, or
     * promise a number the plugin does not use - and every one of those three has happened here.
     */
    public List<String> fixturesIn(String race, int index) {
        Box zone = zones.get("room." + race + "." + index);
        Location landing = roomSpawn(race, index);
        if (zone == null || landing == null) {
            return List.of();
        }
        String world = landing.getWorld().getName();
        List<String> found = new ArrayList<>();
        for (Map.Entry<Location, Fixture> entry : fixtures.entrySet()) {
            Location at = entry.getKey();
            if (!world.equals(at.getWorld().getName()) || !zone.holds(at)) {
                continue;
            }
            List<String> lines = entry.getValue().lines();
            found.add(at.getBlockX() + "," + at.getBlockY() + "," + at.getBlockZ()
                    + " " + entry.getValue().type().name()
                    + " " + (lines == null ? "(no text)" : String.join(" / ", lines)));
        }
        return found;
    }

    private void set(World target, Material material, int x, int y, int z) {
        target.getBlockAt(x, y, z).setType(material, false);
    }

    private static Location blockKey(Location at) {
        return at.getBlock().getLocation();
    }

    /** What a race is called on a sign. */
    static String title(String race) {
        return switch (race) {
            case "vampire" -> "§c吸血鬼";
            case "ghoul" -> "§2食尸鬼";
            case "werewolf" -> "§6狼人";
            case "demon" -> "§4恶魔";
            case "priest" -> "§f牧师";
            case "witchhunter" -> "§7猎巫人";
            case "enderborn" -> "§5末影族";
            case "angel" -> "§b天使";
            default -> race;
        };
    }

    private void returnPad() {
        int px = lobbySpawn.getBlockX();
        int pz = lobbySpawn.getBlockZ();
        int padY = groundBelow(px, pz);
        box(world, PAD_TRIM, px - PAD_HALF, padY, pz - PAD_HALF, px + PAD_HALF, padY, pz + PAD_HALF);
        box(world, Material.SMOOTH_QUARTZ, px - PAD_HALF + 1, padY, pz - PAD_HALF + 1,
                px + PAD_HALF - 1, padY, pz + PAD_HALF - 1);
        set(world, STELE, px + PAD_HALF, padY + 1, pz + PAD_HALF);
        set(world, STELE, px + PAD_HALF, padY + 2, pz + PAD_HALF);
        signOn(world, px + PAD_HALF, padY + 2, pz + PAD_HALF, BlockFace.WEST,
                Trigger.of(Kind.ENTER_LOBBY),
                "§a回到测试大厅", "§7右键这块牌");
        set(world, Material.SEA_LANTERN, px + PAD_HALF, padY + 3, pz + PAD_HALF);
        boxes.add(new Box(world.getName(), px - PAD_HALF, padY, pz - PAD_HALF,
                px + PAD_HALF + 1, padY + 3, pz + PAD_HALF + 1));
        outsideSpawn = new Location(world, px + 0.5, padY + 1, pz + 0.5);
    }

    private int groundBelow(int x, int z) {
        for (int y = oy - 2; y > world.getMinHeight(); y--) {
            if (!world.getBlockAt(x, y, z).isEmpty()) {
                return y;
            }
        }
        return oy - 2;
    }

    /** The hall placing blocks through the API, for {@link Shrines}. */
    private final class Blocks implements Shrines.Placer {

        private final World target;

        Blocks(World target) {
            this.target = target;
        }

        @Override
        public void layer(String material, int y, int fromX, int fromZ, int toX, int toZ) {
            box(target, Material.valueOf(material), fromX, y, fromZ, toX, y, toZ);
        }

        @Override
        public void place(String material, int x, int y, int z) {
            set(target, Material.valueOf(material), x, y, z);
        }
    }
}
