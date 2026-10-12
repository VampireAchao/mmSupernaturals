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
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
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
        SPAWN_MOB
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
     * A step: a title, the lines that teach it, how many slots of the corridor it takes, whether
     * its ceiling is left open (the ghoul's rain step needs the sky), and what to build in it.
     */
    private record Section(String title, List<String> lines, int slots, boolean openSky,
            Station station) {
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

    private static final Material AIR = Material.AIR;
    private static final Material STELE = Material.CHISELED_STONE_BRICKS;
    private static final Material PAD_TRIM = Material.POLISHED_BLACKSTONE_BRICKS;

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
    private final Map<Location, Map<Integer, ItemStack>> chests = new LinkedHashMap<>();
    private final Map<String, Location> points = new LinkedHashMap<>();
    private final Map<String, Box> zones = new LinkedHashMap<>();
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
            button(world, x, y, oz, BlockFace.SOUTH, Trigger.to(race));
            signOn(world, x, y + 1, oz - 1, BlockFace.SOUTH, title(race), "§7点击下方按钮进入");
            x += 2;
        }
        button(world, x, y, oz, BlockFace.SOUTH, Trigger.of(Kind.LEAVE_HALL));
        signOn(world, x, y + 1, oz - 1, BlockFace.SOUTH, "§a离开大厅", "§7点击下方按钮去下面的世界");
        lobbySpawn = new Location(world, ox + LOBBY_HALF + 0.5, oy + 1, oz + LOBBY_HALF + 4.5);
    }

    private void stele() {
        int x = ox + LOBBY_HALF;
        int z = oz + LOBBY_HALF;
        set(world, STELE, x, oy + 1, z);
        set(world, STELE, x, oy + 2, z);
        signOn(world, x, oy + 2, z, BlockFace.NORTH,
                "§e超自然玩家", "§f测试大厅", "§7先选一个种族");
        signOn(world, x, oy + 2, z, BlockFace.SOUTH,
                "§7每个种族一条长廊", "§7每格是一个环节", "§7按告示牌做即可");
        signOn(world, x, oy + 2, z, BlockFace.WEST,
                "§7箱子关上就补满", "§7道具带不出去");
        signOn(world, x, oy + 2, z, BlockFace.EAST,
                "§7按钮＝进出测试", "§7离开会恢复人类");
    }

    /**
     * One race's hall: a corridor of sections in a row, with an entry station at the near end.
     * The stand-in and the power switch are appended to every hall's list of sections, so they are
     * part of the row rather than bolted onto the end of the room.
     */
    private void raceHall(String race, int[] cell) {
        World target = OWN_WORLD.contains(race) ? clockWorld(world, race) : world;
        int centreX = world.getSpawnLocation().getBlockX() + (cell[0] - 1) * CELL;
        int centreZ = world.getSpawnLocation().getBlockZ() + (cell[1] - 1) * CELL;
        int y = oy;
        int x0 = centreX - 40;
        int z0 = centreZ - DEPTH / 2;
        int headroom = cell[2];

        List<Section> sections = sectionsFor(race);
        int length = 0;
        for (Section section : sections) {
            length += section.slots() * SLOT;
        }
        room(target, x0, y, z0, length, DEPTH, headroom, THEMES.get(race));
        int x = x0;
        int index = 0;
        for (Section section : sections) {
            int width = section.slots() * SLOT;
            if (section.openSky()) {
                // An open section is the point of the ghoul's rain step: a roof, even a glass
                // one, counts as cover to anybody the plugin asks.
                box(target, AIR, x, y + headroom + 1, z0, x + width - 1, y + headroom + 1,
                        z0 + DEPTH - 1);
            }
            sectionHead(target, y, x, z0, section.title(), section.lines(), width);
            section.station().build(target, y, x, z0, width);
            points.put("sec." + race + "." + index, new Location(target, x, y, z0));
            index++;
            x += width;
        }

        entryStation(target, race, x0, y, z0);
        zones.put("hall." + race, new Box(target.getName(), x0 - 1, y - 1, z0 - 1,
                x0 + length, y + headroom + 1, z0 + DEPTH));
        points.put("spawn." + race,
                new Location(target, x0 + 2.5, y + 1, z0 + DEPTH / 2.0 + 0.5));
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
                        "恶魔在下界不掉能量（每 5 秒的 −4 只在离开下界、又没站在火里时才扣），"
                                + "还免疫火焰、燃烧、爆炸和摔落伤害。这里的陪练是用来确认"
                                + "你在下界的伤害数值和主世界一致",
                        "陪练站在你面前，真实掉血；打不动就点按钮把他刷新",
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
        entryStation(nether, "demon_nether", x0, y, z0);
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
        entryStation(nether, "ghoul_nether", x0, y, z0);
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
    private void entryStation(World target, String race, int x0, int y, int z0) {
        wallSign(target, y, x0, z0, "§f入口 · 退出", "§7随时可以停下", "§7按钮就在对面墙上");
        switchpoint(target, y, x0 + 1, z0 + 3, Trigger.of(Kind.EXIT_TEST, race),
                "§a退出测试", "§7清道具 · 恢复人类 · 回大厅");
        switchpoint(target, y, x0 + 3, z0 + 3, Trigger.of(Kind.RESET_HUMAN, race),
                "§a恢复人类", "§7留在这里，只改种族");
    }

    /**
     * The two steps every race has: a stand-in to hit, and a switch that hands out power.
     *
     * <p>The stand-in used to be an armour stand. It answered a damage event and nothing else -
     * it had no health worth losing and could not be given a race - so a section whose whole
     * point was "watch what this does to a person" could only ever watch a number. {@link
     * Kind#SPAR} calls out the hall's real stand-in player instead: he is a player, he is human,
     * he loses health when he is hit, and the button puts him back to full.
     */
    private Section sparSection(String race) {
        return section("§f陪练 伤害测试", 1, (w, y, x, z, width) -> {
            sparMark(w, race, x + SLOT / 2 + 4, y, z + DEPTH / 2);
            switchpoint(w, y, x + SLOT / 2, z + 3, Trigger.of(Kind.SPAR),
                    "§f放出陪练", "§7他站在你面前，真的会掉血");
        }, "用你的武器打陪练，看到的是真实伤害数字",
                "陪练是一位没有客户端的真人玩家：他会真的掉血、被击退、被闪白，"
                        + "头上飘出来的是插件加成之后的最终伤害。打空了他不会死——再点一次按钮就刷回满血",
                "陪练会掉血但不会死；打空了就再点一次按钮把他刷新到满血",
                "站好，点「放出陪练」", "用武器打他，数字飘在他头上");
    }

    private Section powerSection(String race) {
        return section("§b能量 +1000", 1, (w, y, x, z, width) -> switchpoint(w, y,
                x + SLOT / 2, z + 3, Trigger.of(Kind.POWER, race),
                "§b能量 +1000", "§7点一下加一千"), "把能量补满，不用先刷到够用",
                "点一下给 1000 能量。这是测试便利，不是种族能力——真实的能量来源是击杀和被动"
                        + "（比如吸血鬼杀生物 +100、杀玩家 +500；狼人杀生物 +20、吃食物 +100）",
                "点一下加 1000 能量", "每个种族的走廊末尾都有这一个");
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
    private Section fallSection(String race) {
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
            int top = w.getHighestBlockYAt(x + 6, z + DEPTH / 2) + 1;
            box(w, Material.STONE, x + 5, top - 1, z + DEPTH / 2 - 1,
                    x + 7, top - 1, z + DEPTH / 2 + 1);
            points.put("fall." + race, new Location(w, x + 6.5, top + 13, z + DEPTH / 2.0 + 0.5));
            points.put("fall.ground." + race,
                    new Location(w, x + 6.5, y + 1, z + DEPTH / 2.0 + 0.5));
            switchpoint(w, y, x + 4, z + 3, Trigger.of(Kind.FALL_TEST, race),
                    "§6站到高处", "§7然后放开手落下来");
            switchpoint(w, y, x + 8, z + 3, Trigger.of(Kind.BACK_DOWN, race),
                    "§a回到地面", "§7摔完了再点这个");
        }, "用一次真实坠落，验这一族的摔落减免",
                ability,
                expect,
                "先确认自己是满血（上一节受过伤就点点「恢复人类」再回来）",
                "点「站到高处」——你会被送到大厅外面地面上方十三格",
                "放开手落到那块石板上，看血条掉了多少",
                "点「回到地面」继续后面的环节");
    }

    // -- the sections ------------------------------------------------------

    /** The steps of one race's flow, in the order a player should meet them. */
    private List<Section> sectionsFor(String race) {
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
                        "点按钮放出一只鸡（旧的会被清掉）", "把它杀掉，回头看饱食度"));
                sections.add(section("§5⑥ 休战 敌对怪物", 4, true, (w, y, x, z, width) -> {
                    box(w, Material.BLACKSTONE, x + 3, y, z + 5, x + width - 4, y, z + DEPTH - 3);
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
                            "§f叫陪练过来", "§7他会站在你面前");
                    switchpoint(w, y, x + 5, z + 3,
                            Trigger.with(Kind.TEST_HIT, "vampire", "IRON_SWORD"),
                            "§f挨一下铁剑", "§7基准伤害");
                    switchpoint(w, y, x + 8, z + 3,
                            Trigger.with(Kind.TEST_HIT, "vampire", "WOODEN_SWORD"),
                            "§b挨一下木剑", "§7木剑反而更疼");
                    switchpoint(w, y, x + 11, z + 3, Trigger.of(Kind.DIP_WATER, "vampire"),
                            "§9跳进水里", "§7等它先烧能量再掉血");
                    powerButton(w, y, x + 14, z, "vampire");
                }, "验证吸血鬼的两条被动：木制武器会打得更疼，溺水可以用能量顶",
                        "普通近战打过来只吃 80% 的伤害（配置 0.8 是减伤系数）；"
                                + "但如果武器是木制的（木剑、木棍、弓），伤害反而额外 +150%。"
                                + "溺水伤害每 1 点消耗 90 能量，能量不足 90 时照常掉血",
                        "木剑打你比铁剑更疼；站在水里能量持续下降，能量耗尽之后才开始真的掉血",
                        "点「叫陪练过来」",
                        "点「挨一下铁剑」和「挨一下木剑」，对比两次掉血",
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
                        "右键祭坛后你当场死一次，有机会直接变回人类；没变的话用旁边那个按钮",
                        "先变成食尸鬼（回第①节）",
                        "走到发光柱子下的钻石块旁边，右键它，看聊天栏",
                        "没变回人类就点「净化 · 恢复人类」，那走的是插件自己的治愈代码"));
                sections.add(section("§b③ 道具 召唤与羁绊", 2, (w, y, x, z, width) -> {
                    chest(w, x + 2, y + 1, z + DEPTH - 2, BlockFace.NORTH, centred(
                            new ItemStack(Material.PORKCHOP, 8),
                            new ItemStack(Material.BONE, 8),
                            new ItemStack(Material.IRON_SWORD, 1)));
                    switchpoint(w, y, x + 5, z + 3, Trigger.of(Kind.SPAR),
                            "§f叫陪练过来", "§7羁绊要有对象才能建立");
                    powerButton(w, y, x + 9, z, "ghoul");
                }, "验证食尸鬼的两个耗能能力：召唤僵尸与不洁纽带",
                        "猪排左键召唤一只僵尸，耗 1000 能量。骨头攻击一个非人类的超自然玩家建立"
                                + "不洁纽带：他之后受伤时，一半伤害转给你、你替他吃，每次分担扣你 50 能量。"
                                + "纽带要求双方在同一世界、相距不超过 10 格，而且你只能同时绑一个人",
                        "猪排召出一只僵尸；拿骨头打陪练会在聊天栏出现羁绊提示",
                        "点「叫陪练过来」，站到他面前",
                        "拿骨头打他一下，建起纽带，看聊天栏",
                        "换一件武器再打他，看你自己是不是也跟着掉血"));
                sections.add(section("§5④ 休战 僵尸系怪物", 4, true, (w, y, x, z, width) -> {
                    box(w, Material.MOSS_BLOCK, x + 3, y, z + 5, x + width - 4, y, z + DEPTH - 3);
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
                    switchpoint(w, y, x + 3, z + 3, Trigger.of(Kind.SUN_SPOT, "ghoul"),
                            "§e站到露天台", "§7这一格头顶 9 格没屋顶");
                    switchpoint(w, y, x + 6, z + 3, Trigger.of(Kind.TOGGLE_RAIN, "ghoul"),
                            "§9切换下雨", "§7雨会真的落到你身上");
                    switchpoint(w, y, x + 9, z + 3, Trigger.of(Kind.HURT),
                            "§c先被打掉血", "§7不然没血可回，看不出回血");
                    powerButton(w, y, x + 2, z, "ghoul");
                }, "验证食尸鬼的回血条件：有顶、且不下雨才回血",
                        "回血每 10 秒 1 点。但雨在下、你头顶又没有遮挡时，这条路整个不生效——"
                                + "判定看的是天空，玻璃顶也算遮挡。它同时也免疫摔落伤害",
                        "露天淋雨时完全不回血；回到有顶的格子里每 10 秒回 1 点",
                        "先点「先被打掉血」，血量掉到一半",
                        "站到露天台点「切换下雨」等一会，看血量不动",
                        "走回有顶的格子再等一会，看它开始回血"));
                sections.add(section("§b⑥ 钻石剑 伤害 0", 2, (w, y, x, z, width) -> {
                    chest(w, x + 2, y + 1, z + DEPTH - 2, BlockFace.NORTH, centred(
                            new ItemStack(Material.DIAMOND_SWORD, 1),
                            new ItemStack(Material.IRON_SWORD, 1)));
                    switchpoint(w, y, x + 6, z + 3, Trigger.of(Kind.SPAR),
                            "§f叫陪练过来", "§7他会站在你面前");
                    switchpoint(w, y, x + 9, z + 3,
                            Trigger.with(Kind.TEST_HIT, "ghoul", "DIAMOND_SWORD"),
                            "§b挨一下钻石剑", "§7你一滴血都不会掉");
                    switchpoint(w, y, x + 12, z + 3,
                            Trigger.with(Kind.TEST_HIT, "ghoul", "IRON_SWORD"),
                            "§f挨一下铁剑", "§7对照：真的会掉血");
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
                    switchpoint(w, y, x + 2, z + 3, Trigger.of(Kind.DIP_WATER, "ghoul"),
                            "§9跳进水里", "§7站着别动，等 10 秒一次");
                    switchpoint(w, y, x + 5, z + 3, Trigger.of(Kind.HURT),
                            "§c先回满血再看", "§7免得死在池子里");
                    powerButton(w, y, x + 8, z, "ghoul");
                }, "验证食尸鬼在水里会持续溶解",
                        "脚下方块是水时，**每秒**掉 4 点血（不是溺水，是碰到水就掉；判定每 1 秒跑一次、"
                                + "没有节流）。所以满血 20 点也只撑得住 5 秒——这一条比看上去凶得多。"
                                + "坐在船里不算；有 supernatural.player.preventwaterdamage 权限则免疫",
                        "站在水里血条每秒掉 4 点，几秒钟就见底；上岸之后立刻停止",
                        "先点「先回满血再看」，半血进去根本来不及看",
                        "点「跳进水里」，数到 3 就自己上岸，看血条少了多少",
                        "再跳一次数满 5 秒，确认会被溺死（这就是「溶解」）"));
                sections.add(section("§2⑨ 纽带 伤害分担", 3, (w, y, x, z, width) -> {
                    chest(w, x + 2, y + 1, z + DEPTH - 2, BlockFace.NORTH, centred(
                            new ItemStack(Material.BONE, 6),
                            new ItemStack(Material.IRON_SWORD, 1)));
                    switchpoint(w, y, x + 6, z + 3,
                            Trigger.with(Kind.SPAWN_NPCS, "ghoul", "vampire"),
                            "§c放出一个吸血鬼", "§7他不还手，随便打");
                    switchpoint(w, y, x + 10, z + 3, Trigger.of(Kind.POWER_READ),
                            "§b查看能量", "§7每次触发扣 50");
                }, "验证「不洁纽带」到底替对方做了什么",
                        "手持骨头攻击一个超自然玩家就会建立纽带；普通人类不行，会提示 "
                                + "\"You cannot form a bond with a human.\"。建立之后，对方每次挨打，"
                                + "伤害先减半，另一半按食尸鬼自己的 0.65 减伤算完从你身上扣——"
                                + "你替他挨一半，每次触发再扣你 50 能量。同世界、距离 10 格以内才维持得住；"
                                + "一个食尸鬼同时只能绑一个对象",
                        "绑上之后再打那个吸血鬼：他掉一半血，你自己的血条也跟着掉一截。"
                                + "这就是纽带的全部作用——它不是吸血，也不是共享，是分担",
                        "从箱子里拿骨头，点「放出一个吸血鬼」",
                        "手持骨头打他一下，聊天栏出现建立纽带的提示",
                        "换成箱子里的铁剑再打他一下：他掉血，你的血也同时掉",
                        "点「查看能量」，看得见一次 50 的消耗"));
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
                        "配方不止常见的那几样——默认是 **20 种**：蒲公英、虞美人、红蘑菇、棕蘑菇各 10 个，"
                                + "另加 15 种花（绒球葱、蓝花美耳草、兰花、闭眼绒球花、矢车菊、金蒲公英、"
                                + "铃兰、睁眼绒球花、橙色郁金香、滨菊、粉色郁金香、红色郁金香、火把花、"
                                + "白色郁金香、凋灵玫瑰）和 1 个碗，各 1 个。箱子里的东西是**直接照插件配置生成的**，"
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
                                + "把自己恢复成人类、再点一次同样的按钮，同样三只狼咬下去就会掉血",
                        "点「放出三只愤怒的野狼」，站着别动，让它们咬你，看血条",
                        "点入口那排的「恢复人类」，再点一次同样的按钮，对照：这次会掉血",
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
                        "死后成为恶魔；按钮把你送进下界的转化厅，那里有整套皮革和岩浆池",
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
                                + "不是按钮，所以这一步改不了",
                        "带够 30 个雪球时溺死会变成人类；水池是走进去的，不会掉出世界",
                        "先从箱子里拿走至少 30 个雪球",
                        "点「查看生物群系」确认是寒冷群系",
                        "点「跳进水里」等着淹死"));
                sections.add(section("§5④ 转化他人 地狱岩", 2, (w, y, x, z, width) -> {
                    chest(w, x + 2, y + 1, z + DEPTH - 2, BlockFace.NORTH, centred(
                            new ItemStack(Material.NETHERRACK, 8)));
                    switchpoint(w, y, x + 5, z + 3,
                            Trigger.with(Kind.SPAWN_NPCS, "demon", "vampire:NETHERRACK"),
                            "§a放出一个持地狱岩的异族", "§7他手上也得拿着地狱岩");
                    powerButton(w, y, x + 9, z, "demon");
                }, "验证恶魔能把别人变成恶魔",
                        "手持地狱岩攻击另一名同样手持地狱岩的玩家，耗 2000 能量，"
                                + "会把对方直接转成恶魔。两边都得拿着地狱岩，缺一边就只是普通一击",
                        "打完那一下，对方的种族变成恶魔，聊天栏有提示",
                        "点「放出一个持地狱岩的异族」",
                        "拿箱子里的地狱岩打他一下",
                        "看他的种族是不是变成了 demon"));
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
                    powerButton(w, y, x + 9, z, "demon");
                }, "验证恶魔离开下界与火源之后会一直掉能量",
                        "不在下界、脚下方块也不是火或岩浆时，每 5 秒 -20 能量"
                                + "（Demon.Power.Loss = 4，每秒结算）。这是恶魔最大的枷锁："
                                + "不在火旁边就一直在流失",
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
                            "§7羽毛 · 放逐", "§7把异族送到放逐点");
                    switchpoint(w, y, x + 8, z + 3, spell("SUGAR"),
                            "§7糖 · 驱魔", "§7把异族恢复成人类");
                    switchpoint(w, y, x + 11, z + 3, spell("FLINT"),
                            "§7燧石 · 治愈诅咒", "§7对方也要拿着燧石");
                    switchpoint(w, y, x + 14, z + 3, spell("PAPER"),
                            "§7纸 · 治疗", "§7给人类回 10 点血");
                    powerButton(w, y, x + 6, z, "priest");
                }, "验证牧师的四个单体法术都能触发",
                        "全部四个都用「拿物品攻击目标」触发，不是右键：羽毛＝放逐，耗 4000，"
                                + "把异族传送到管理员用 /sn setbanish 设的放逐点；糖＝驱魔，耗 9000，"
                                + "把异族变回人类；燧石＝治愈诅咒，耗 1000，要求对方手上也拿着燧石；"
                                + "纸＝治疗，耗 1000，给非超自然目标回 10 点血。"
                                + "另外牧师打超自然目标会点燃对方、伤害翻倍，打普通动物则完全无效（狼除外）",
                        "每个法术都在陪练身上生效：放逐把他传走、驱魔和治愈诅咒把他变回人类、"
                                + "纸给他加 10 点血",
                        "点「叫陪练过来」，他会先变成异族",
                        "手持箱子里的法术物品打他一下，看陪练的变化和聊天栏",
                        "四件物品逐个试，每个法术的按钮只放一次"));
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
                            new ItemStack(Material.PORKCHOP, 8)));
                    switchpoint(w, y, x + 5, z + 3, Trigger.with(Kind.SPAWN_MOB, null, "COW"),
                            "§a放出一头牛", "§7拿剑砍它，伤害是 0");
                    switchpoint(w, y, x + 8, z + 3,
                            Trigger.with(Kind.SPAWN_NPCS, "priest", "vampire"),
                            "§c放出一个吸血鬼", "§7再拿剑砍他");
                }, "验证牧师的战斗规则：不伤无辜动物，对异族加重并点燃",
                        "对非敌对的动物（狼除外）伤害一律为 0，只提示 \"You cannot hurt innocent animals.\"；"
                                + "对超自然玩家则额外加一倍基础伤害，并把对方点燃 50 tick（恶魔不会被点燃）；"
                                + "对怪物也会点燃。注意这些判定都在插件认为的「PvP 区域」里生效——"
                                + "本测试服没有 WorldGuard，插件把一切都当作 PvP",
                        "牛挨多少下都不掉血；吸血鬼被打中会烧起来，伤害还翻倍",
                        "点「放出一头牛」，拿箱子里的铁剑砍它几下，看是否掉血",
                        "点「放出一个吸血鬼」，再砍他一下",
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
                    set(w, STELE, x + 7, y + 2, z + DEPTH / 2);
                    signOn(w, x + 7, y + 2, z + DEPTH / 2, BlockFace.EAST, "WitchHunter");
                    switchpoint(w, y, x + 2, z + 3, Trigger.of(Kind.INVITE_HUNTER),
                            "§a直接发一张邀请", "§7跳过杀怪，图快的用这个");
                }, "验证铁门入会：门旁告示牌必须写着 WitchHunter",
                        "猎巫人大厅的入口是一扇铁门，判定靠旁边告示牌的任意一行含有文本 WitchHunter。"
                                + "右键铁门时：有邀请的人类、或者已经是猎巫人，才会开门并完成入会；"
                                + "其他人只会看到 WitchHunters Only!。门自己 1 秒后关上，"
                                + "开的时候上下两格一起动",
                        "有邀请时右键铁门会开门并把你变成猎巫人；没邀请只会提示 WitchHunters Only",
                        "（可选）点左边按钮直接拿一张邀请",
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
                                + "⑤grapple 抓钩箭，500，命中方块后把你拉向命中点。"
                                + "除 normal 以外，特殊箭只允许在 PvP 区域使用",
                        "左键一次换一种，聊天栏报出当前箭种；对着陪练射 power 箭伤害明显更高，"
                                + "紧接着再射一箭会被 15 秒冷却挡下；fire 箭会让陪练身上着火；"
                                + "triple 一箭射出三支；grapple 打中那层石砖会把你自己拽上去",
                        "点「切换箭矢模式」选一种，聊天栏会报当前箭种",
                        "点「叫陪练过来」，对着他射一箭看效果",
                        "换 fire 再射一箭，看陪练身上着火",
                        "换 triple 射一箭，数一下是不是三支",
                        "换 grapple 对着头顶那层石砖射，你会被拉上去",
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
                        "点按钮聊天栏报出当前名单；名单不满 5 个时会自动补一个并全服公告",
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
                                + "（配置里 10% 概率把被击杀者转成末影族，也要先有人死给你）",
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
                sections.add(section("§e② 道具 跳与治疗", 1, (w, y, x, z, width) -> {
                    chest(w, x + 2, y + 1, z + DEPTH - 2, BlockFace.NORTH, centred(
                            new ItemStack(Material.DANDELION, 8),
                            new ItemStack(Material.FEATHER, 8),
                            new ItemStack(Material.PAPER, 8)));
                    switchpoint(w, y, x + 6, z + 3, Trigger.of(Kind.SPAR),
                            "§f叫陪练过来", "§7治疗要有伤口才看得出来");
                    powerButton(w, y, x + 9, z, "angel");
                }, "验证天使的超级跳与治疗法术",
                        "蒲公英左键＝超级跳，往上窜一截，耗 1000；羽毛＝治疗，"
                                + "攻击目标（不是右键）给他回 5 点生命，耗 3000。"
                                + "天使另外还免疫摔落伤害、在水里每 10 秒 +50 能量",
                        "蒲公英左键往上窜一截；拿羽毛打受伤的目标，他会回 5 点血",
                        "先点「叫陪练过来」，用剑把他打掉一点血",
                        "拿羽毛打他一下，看他回血；再拿蒲公英左键跳一次"));
                sections.add(section("§b③ 召唤 三种动物", 1, (w, y, x, z, width) -> {
                    chest(w, x + 2, y + 1, z + DEPTH - 2, BlockFace.NORTH, centred(
                            new ItemStack(Material.BEEF, 8),
                            new ItemStack(Material.BONE, 8),
                            new ItemStack(Material.PORKCHOP, 8)));
                    powerButton(w, y, x + 6, z, "angel");
                }, "验证天使的三种召唤",
                        "都按左键触发，各耗 5000 能量：生牛肉召牛、骨头召一只已驯服的狼"
                                + "（归你所有、满血）、猪肉召猪。左键空气时它们落在你身前 2 格，"
                                + "左键方块时落在你指的位置上方；落点会自己挑能站人、脚下有地的地方，"
                                + "不会再生成在方块内部憋死",
                        "三种动物出现在你身前或你指的地方，而且十几秒后还活着",
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
        // Every race gets these four, built by the same code, at the end of its row.
        sections.add(fallSection(race));
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
                "每族走廊末尾固定三格：陪练、+1000 能量、重来或离开。这三格八个种族共用，"
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
        return new Section(title, brief(goal, ability, expect, steps), slots, false, station);
    }

    private static Section section(String title, int slots, boolean openSky, Station station,
            String goal, String ability, String expect, String... steps) {
        return new Section(title, brief(goal, ability, expect, steps), slots, openSky, station);
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
                wrong.add(block.getX() + "," + block.getY() + "," + block.getZ()
                        + " wanted=" + entry.getValue().type() + " got=" + block.getType());
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
                if (entity instanceof LivingEntity && !(entity instanceof Player)
                        && !(entity instanceof ArmorStand)) {
                    found.add(entity);
                }
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
        String biome = "demon".equals(race) ? "minecraft:snowy_plains" : "minecraft:the_void";
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
        if (headroom > 8) {
            for (int dx = 1; dx < length; dx += 4) {
                set(target, theme.light(), x + dx, y + 3, z - 1);
                set(target, theme.light(), x + dx, y + 3, farZ + 1);
            }
            for (int dz = 1; dz < depth; dz += 4) {
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
        signBoard(target, x, y + 2, z - 1, BlockFace.SOUTH, maxBoards, wrapAll(lines));
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
        List<String> board = new ArrayList<>();
        int placed = 0;
        for (String line : lines) {
            if (BREAK.equals(line)) {
                if (!board.isEmpty()) {
                    writeBoard(target, supportX, supportY, supportZ, side, maxBoards,
                            placed++, board);
                    board.clear();
                }
                continue;
            }
            if (board.size() == LINES_PER_BOARD) {
                writeBoard(target, supportX, supportY, supportZ, side, maxBoards, placed++, board);
                board.clear();
            }
            board.add(line);
        }
        if (!board.isEmpty() && placed < maxBoards) {
            writeBoard(target, supportX, supportY, supportZ, side, maxBoards, placed, board);
        }
    }

    private void writeBoard(World target, int supportX, int supportY, int supportZ, BlockFace side,
            int maxBoards, int board, List<String> lines) {
        if (board >= maxBoards) {
            boardsDropped++;
            return;
        }
        signOn(target, supportX + board, supportY, supportZ, side,
                lines.toArray(new String[0]));
    }

    /** Boards a section had nowhere left to write. Zero means every word fitted. */
    public int boardsDropped() {
        return boardsDropped;
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
        sign(target, supportX + side.getModX(), supportY, supportZ + side.getModZ(), side, lines);
    }

    private void sign(World target, int x, int y, int z, BlockFace facing, String... lines) {
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
        place(block, Material.OAK_WALL_SIGN, facing, List.of(lines));
        fixtures.put(blockKey(block.getLocation()),
                new Fixture(Material.OAK_WALL_SIGN, facing, List.of(lines), null));
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
        set(target, STELE, x, y + 1, z - 1);
        set(target, STELE, x, y + 2, z - 1);
        button(target, x, y + 1, z, BlockFace.SOUTH, trigger);
        // Three boards, because that is how much wall a switch has: the next one along is three
        // columns away and puts its own sign at its own column.
        signBoard(target, x, y + 2, z - 1, BlockFace.SOUTH, SWITCH_BOARDS, wrapAll(lines));
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

    private void set(World target, Material material, int x, int y, int z) {
        target.getBlockAt(x, y, z).setType(material, false);
    }

    private static Location blockKey(Location at) {
        return at.getBlock().getLocation();
    }

    /** What a race is called on a sign. */
    private static String title(String race) {
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
        button(world, px + PAD_HALF - 1, padY + 1, pz + PAD_HALF, BlockFace.WEST,
                Trigger.of(Kind.ENTER_LOBBY));
        signOn(world, px + PAD_HALF, padY + 2, pz + PAD_HALF, BlockFace.WEST,
                "§a回到测试大厅", "§7点下方按钮");
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
