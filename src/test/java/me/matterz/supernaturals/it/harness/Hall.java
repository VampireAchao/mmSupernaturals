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
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.Door;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.EulerAngle;

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
 * signs at the section's head, and its own chest, switch, dummy or arena right behind them. So a
 * section can be added, changed or removed on its own, and a player can walk straight to the one
 * thing they want to test instead of reading every sign in the room to find it. Two steps belong
 * to every race and are therefore appended to every hall by the same code: an immovable dummy to
 * hit, and a switch that hands out power.
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
        BOUNTY
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
     * The one monster that is not summoned into a hall. The arena is big and tall enough for
     * everything else the plugin lists - a giant, a warden, a ghast, a phantom - but an ender
     * dragon is a boss with its own dimension and its own multi-part body, and a copy of one
     * standing in a room tests nothing about a truce.
     */
    private static final Set<String> UNROOMABLE = Set.of("ENDER_DRAGON");

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
    private final Map<Location, Map<Integer, ItemStack>> chests = new LinkedHashMap<>();
    private final Map<String, Location> points = new LinkedHashMap<>();
    private final Map<String, Box> zones = new LinkedHashMap<>();
    private final Map<String, ArmorStand> dummies = new LinkedHashMap<>();
    private final List<Box> boxes = new ArrayList<>();

    private Location lobbySpawn;
    private Location outsideSpawn;
    private Location priestAltar;
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
        hall.returnPad();
        hall.keepLoaded();
        world.setSpawnLocation(hall.lobbySpawn);
        world.setGameRule(GameRule.SPAWN_RADIUS, 0);
        return hall;
    }

    /**
     * Keeps the hall's own chunks loaded, in every world it has one in.
     *
     * <p>Blocks survive a chunk being unloaded; entities do not. A dummy in a hall three hundred
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
     * The dummy and the power switch are appended to every hall's list of sections, so they are
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
            sectionHead(target, y, x, z0, section.title(), section.lines());
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
        room(nether, x0, y, z0, 5 * SLOT, DEPTH, 12, THEMES.get("demon"));
        sectionHead(nether, y, x0, z0, "§4① 转化 · 下界的火",
                List.of("§7先穿整套皮革", "§7死在岩浆或者火里"));
        int cx = x0 + 2 * SLOT + SLOT / 2;
        int cz = z0 + DEPTH / 2;
        box(nether, Material.POLISHED_BLACKSTONE_BRICKS, cx - 3, y, cz - 3, cx + 3, y, cz + 3);
        box(nether, Material.LAVA, cx - 1, y - 1, cz - 1, cx + 1, y + 2, cz + 1);
        points.put("nether.lava", new Location(nether, cx + 0.5, y + 1, cz + 0.5));
        netherLava = points.get("nether.lava");
        largeChest(nether, x0 + 2, y + 1, z0 + DEPTH - 2, leather());
        switchpoint(nether, y, x0 + 2, z0 + 3, Trigger.of(Kind.DIP),
                "§c跳进岩浆", "§7点了就下池子");
        sectionHead(nether, y, x0 + SLOT, z0, "§f② 假人 · 伤害测试",
                List.of("§7打它看伤害数字", "§7它不会被推走"));
        dummy(nether, "demon_nether", x0 + SLOT + 5, y, z0 + DEPTH / 2);
        switchpoint(nether, y, x0 + 3 * SLOT + 5, z0 + 3, Trigger.of(Kind.POWER, "demon_nether"),
                "§b能量 +1000", "§7点一下加一千");
        switchpoint(nether, y, x0 + 4 * SLOT + 2, z0 + 3, Trigger.of(Kind.RETURN_OVERWORLD),
                "§a回到主世界大厅", "§7恶魔大厅在另一头");
        entryStation(nether, "demon_nether", x0, y, z0);
        points.put("spawn.demon_nether",
                new Location(nether, x0 + 2.5, y + 1, z0 + DEPTH / 2.0 + 0.5));
        zones.put("hall.demon_nether", new Box(nether.getName(), x0 - 1, y - 1, z0 - 1,
                x0 + 5 * SLOT, y + 12, z0 + DEPTH));
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

    /** The switches at the near end of every hall: how to stop, and how to get out. */
    private void entryStation(World target, String race, int x0, int y, int z0) {
        wallSign(target, y, x0 + 4, z0, "§f入口 · 退出", "§7随时可以停下", "§7按钮就在对面墙上");
        switchpoint(target, y, x0 + 1, z0 + 3, Trigger.of(Kind.EXIT_TEST, race),
                "§a退出测试", "§7清道具 · 恢复人类 · 回大厅");
        switchpoint(target, y, x0 + 3, z0 + 3, Trigger.of(Kind.RESET_HUMAN, race),
                "§a恢复人类", "§7留在这里，只改种族");
    }

    /** The two steps every race has: a dummy to hit, and a switch that hands out power. */
    private Section dummySection(String race) {
        return section("§f假人 · 伤害测试", 1, (w, y, x, z, width) -> {
            dummy(w, race, x + SLOT / 2, y, z + DEPTH / 2);
            wallSign(w, y, x + 2, z, "§f打它，伤害数字飘在头上", "§7它不会被推动、打坏或穿戴");
        }, "§7用你的武器打它");
    }

    private Section powerSection(String race) {
        return section("§b能量 · +1000", 1, (w, y, x, z, width) -> switchpoint(w, y,
                x + SLOT / 2, z + 3, Trigger.of(Kind.POWER, race),
                "§b能量 +1000", "§7点一下加一千"), "§7不用先刷能量再测试");
    }

    // -- the sections ------------------------------------------------------

    /** The steps of one race's flow, in the order a player should meet them. */
    private List<Section> sectionsFor(String race) {
        List<Section> sections = new ArrayList<>();
        switch (race) {
            case "vampire" -> {
                sections.add(section("§c① 转化 · 成为吸血鬼", 1, (w, y, x, z, width) -> {
                    Shrines.vampire(new Blocks(w), x + 5, y, z + DEPTH / 2);
                    chest(w, x + 2, y + 1, z + DEPTH - 2, BlockFace.NORTH, centred(
                            new ItemStack(Material.MUSHROOM_STEW, 1),
                            new ItemStack(Material.BONE, 10),
                            new ItemStack(Material.GUNPOWDER, 10),
                            new ItemStack(Material.REDSTONE, 10)));
                }, "§7箱子里拿四样材料", "§7右键中间的金块"));
                sections.add(section("§b② 恢复人类 · 治愈祭坛", 1, (w, y, x, z, width) -> {
                    Shrines.cure(new Blocks(w), x + 5, y, z + DEPTH / 2);
                    chest(w, x + 2, y + 1, z + DEPTH - 2, BlockFace.NORTH, centred(
                            new ItemStack(Material.WATER_BUCKET, 1),
                            new ItemStack(Material.MILK_BUCKET, 1),
                            new ItemStack(Material.SUGAR, 20),
                            new ItemStack(Material.WHEAT, 20)));
                }, "§7箱子里拿四样材料", "§7右键中间的青金石块"));
                sections.add(section("§6③ 阳光与金头盔", 2, true, (w, y, x, z, width) -> {
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
                }, "§7这一格是露天的（头顶没屋顶）", "§7白天不戴金头盔会被点燃",
                        "§7戴上金头盔就不会", "§7只改本大厅的世界"));
                sections.add(section("§a④ 回复饱食度 · 食物清单", 2, (w, y, x, z, width) -> {
                    largeChest(w, x + 4, y + 1, z + DEPTH - 2, foodList());
                    switchpoint(w, y, x + 9, z + 3, Trigger.of(Kind.CLEAR_HUNGER),
                            "§7清空饱食度", "§7方便看杀怪回饱食");
                }, "§7吸血鬼不能吃普通食物", "§7拿箱子里的东西右键试试", "§7会提示必须喝血"));
                sections.add(section("§2⑤ 杀鸡回饱食度", 1, (w, y, x, z, width) ->
                        switchpoint(w, y, x + SLOT / 2, z + 3, Trigger.of(Kind.SPAWN_CHICKEN),
                                "§a放出一只鸡", "§7它不会动，杀掉看饱食度"),
                        "§7吸血鬼杀生物会回饱食度", "§7按钮先清掉旧的测试鸡", "§7再放一只新的"));
                sections.add(section("§5⑥ 休战 · 敌对怪物", 4, true, (w, y, x, z, width) -> {
                    box(w, Material.BLACKSTONE, x + 3, y, z + 5, x + width - 4, y, z + DEPTH - 3);
                    switchpoint(w, y, x + 4, z + 3, Trigger.of(Kind.SPAWN_MONSTERS, "vampire"),
                            "§c召唤全部敌对怪物", "§7看它们打不打你");
                    switchpoint(w, y, x + 8, z + 3, Trigger.of(Kind.CLEAR_MONSTERS, "vampire"),
                            "§a清空怪物", "§7离开测试也会自动清");
                }, "§7这一格又大又高（34 格）", "§7巨人 / 监守者 / 恶魂都放得下",
                        "§7未破约时它们不该主动攻击", "§7你打它一下就会破约"));
                sections.add(section("§d⑦ 道具 · 跑酷", 2, (w, y, x, z, width) -> {
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
                }, "§7红玫瑰左键＝超级跳", "§7红玫瑰右键＝存传送点", "§7书左键＝传回存点"));
                sections.add(placeholder("§7⑧ 待加环节", 1));
            }
            case "ghoul" -> {
                sections.add(section("§2① 转化 · 被僵尸杀死", 1, (w, y, x, z, width) ->
                        switchpoint(w, y, x + SLOT / 2, z + 3, Trigger.of(Kind.PREPARE_GHOUL),
                                "§a放出一只僵尸", "§7它杀死你就会转化"),
                        "§7站到它旁边等着", "§7转化率是 100%"));
                sections.add(section("§f② 教会净化 · 恢复人类", 2, (w, y, x, z, width) -> {
                    Shrines.priest(new Blocks(w), x + 5, y, z + DEPTH / 2);
                    priestAltar = new Location(w, x + 5.5, y + 1, z + DEPTH / 2.0 + 0.5);
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
                }, "§f钻石块＝教会祭坛（发光柱子下面）", "§7食尸鬼自己没有任何解药",
                        "§7所以净化只能靠教会：点右边的按钮", "§7净化后你会原地变回人类"));
                sections.add(section("§5③ 休战 · 僵尸系怪物", 4, true, (w, y, x, z, width) -> {
                    box(w, Material.MOSS_BLOCK, x + 3, y, z + 5, x + width - 4, y, z + DEPTH - 3);
                    switchpoint(w, y, x + 4, z + 3, Trigger.of(Kind.SPAWN_MONSTERS, "ghoul"),
                            "§c召唤僵尸系怪物", "§7看它们打不打你");
                    switchpoint(w, y, x + 8, z + 3, Trigger.of(Kind.CLEAR_MONSTERS, "ghoul"),
                            "§a清空怪物", "§7离开测试也会自动清");
                }, "§7僵尸 / 尸壳 / 溺尸 / 僵尸村民 / 僵尸猪人", "§7巨人（12 格高）也放得下",
                        "§7未破约时都不该主动攻击"));
                sections.add(section("§9④ 露天 · 阳光与下雨", 2, true, (w, y, x, z, width) -> {
                    // No roof over this section: the plugin asks the sky, and both tests are
                    // about the sky - the sun for a ghoul's regeneration, the rain for the
                    // rule that switches that regeneration off.
                    box(w, Material.MOSSY_STONE_BRICKS, x + 9, y, z + 5, x + 14, y, z + 9);
                    points.put("ghoul.sun", new Location(w, x + 11.5, y + 1, z + 7.5));
                    switchpoint(w, y, x + 3, z + 3, Trigger.of(Kind.SUN_SPOT, "ghoul"),
                            "§e站到露天台", "§7这一格头顶 9 格没屋顶");
                    switchpoint(w, y, x + 6, z + 3, Trigger.of(Kind.TOGGLE_RAIN, "ghoul"),
                            "§9切换下雨", "§7雨会真的落到你身上");
                }, "§7露天：阳光和雨都能进来", "§7下雨且没遮盖 = 不回血",
                        "§7站回有顶的格子里会回血", "§7只改本大厅的世界"));
                sections.add(section("§b⑤ 钻石剑 · 伤害为 0", 2, (w, y, x, z, width) -> {
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
                }, "§7免疫钻石剑与下界合金剑", "§7按钮会叫来一位陪练（真人模型）",
                        "§7他挥剑砍你，你看到的是真实掉血"));
                sections.add(placeholder("§7⑥ 待加环节", 1));
            }
            case "werewolf" -> {
                sections.add(section("§6① 转化 · 夜里被狼咬死", 1, (w, y, x, z, width) -> {
                    switchpoint(w, y, x + 3, z + 3, Trigger.of(Kind.NIGHT),
                            "§9夜晚", "§7先调成夜里");
                    switchpoint(w, y, x + 7, z + 3, Trigger.of(Kind.PREPARE_WEREWOLF),
                            "§a放出一只狼", "§7它咬死你就会转化");
                }, "§7只有夜里未驯服的狼才算", "§7白天能力不可用"));
                sections.add(section("§a② 狼毒解药 · 制作与使用", 2, (w, y, x, z, width) -> {
                    largeChest(w, x + 4, y + 1, z + DEPTH - 2, wereWolfbaneList());
                    switchpoint(w, y, x + 10, z + 3, Trigger.of(Kind.DAY),
                            "§e白天", "§7狼毒只能在白天做");
                }, "§7箱子里是完整配方", "§7白天手持碗左键 = 解毒", "§7材料会被扣掉，直接恢复人类"));
                sections.add(section("§e③ 夜间道具", 1, (w, y, x, z, width) -> {
                    chest(w, x + 2, y + 1, z + DEPTH - 2, BlockFace.NORTH, centred(
                            new ItemStack(Material.PORKCHOP, 8),
                            new ItemStack(Material.FEATHER, 8),
                            new ItemStack(Material.COOKED_BEEF, 16)));
                    switchpoint(w, y, x + 7, z + 3, Trigger.of(Kind.NIGHT),
                            "§9夜晚", "§7这些都要夜里用");
                }, "§7猪肉左键＝召狼并驯服", "§7羽毛左键＝向前冲刺", "§7食物右键＝回饱食度并加能量"));
                sections.add(section("§c④ 夜间武器限制", 1, (w, y, x, z, width) -> {
                    chest(w, x + 2, y + 1, z + DEPTH - 2, BlockFace.NORTH, centred(
                            new ItemStack(Material.BOW, 1),
                            new ItemStack(Material.IRON_SWORD, 1),
                            new ItemStack(Material.STONE_AXE, 1)));
                    dummy(w, "werewolf_night", x + 8, y, z + DEPTH / 2);
                    switchpoint(w, y, x + 4, z + 3, Trigger.of(Kind.NIGHT),
                            "§9夜晚", "§7再拿武器打假人");
                }, "§7夜里不能用弓和多数剑斧镐", "§7打假人应该是 0 伤害", "§7白天没有这个限制"));
                sections.add(placeholder("§7⑤ 待加环节", 1));
            }
            case "demon" -> {
                sections.add(section("§c① 火球与束缚", 1, (w, y, x, z, width) ->
                        chest(w, x + 2, y + 1, z + DEPTH - 2, BlockFace.NORTH, centred(
                                new ItemStack(Material.REDSTONE, 16),
                                new ItemStack(Material.INK_SAC, 8))),
                        "§7红石左键＝火球", "§7墨囊左键＝蜘蛛网束缚", "§7这是主世界维度的大厅"));
                sections.add(section("§b② 寒冷生物群系 · 溺死恢复人类", 2, (w, y, x, z, width) -> {
                    // The pool sits ON the floor and is walled on all four sides, with its own
                    // bottom: the first version dug one block below the room's floor, which in a
                    // hall that floats over nothing is a hole straight into the void.
                    box(w, Material.POLISHED_BLACKSTONE_BRICKS, x + 7, y, z + 4,
                            x + 13, y + 3, z + 10);
                    box(w, Material.WATER, x + 8, y + 1, z + 5, x + 12, y + 3, z + 9);
                    points.put("demon.pool", new Location(w, x + 10.5, y + 3, z + 7.5));
                    switchpoint(w, y, x + 3, z + 3, Trigger.of(Kind.COLD_BIOME, "demon"),
                            "§b查看生物群系", "§7确认插件认不认");
                    switchpoint(w, y, x + 3, z + 6, Trigger.of(Kind.DIP_WATER, "demon"),
                            "§9跳进水里", "§7带着雪球淹死自己");
                    largeChest(w, x + 2, y + 1, z + DEPTH - 2,
                            fill(new LinkedHashMap<>(), Material.SNOWBALL, 64, 2));
                }, "§7大厅生成时就在寒冷生物群系", "§7水池有底的，不会掉出去",
                        "§7背包里放 30 个雪球", "§7溺水死亡 = 恢复人类"));
                sections.add(section("§4③ 前往下界 · 岩浆转化", 2, (w, y, x, z, width) -> {
                    box(w, Material.POLISHED_BLACKSTONE_BRICKS, x + 8, y, z + 5,
                            x + 14, y, z + 10);
                    box(w, Material.LAVA, x + 9, y, z + 6, x + 13, y, z + 9);
                    chest(w, x + 2, y + 1, z + DEPTH - 2, BlockFace.NORTH, centred(
                            new ItemStack(Material.NETHERRACK, 8)));
                    switchpoint(w, y, x + 4, z + 3, Trigger.of(Kind.GO_NETHER, "demon"),
                            "§4进入下界大厅", "§7穿着皮革死在里面");
                }, "§7恶魔必须在下界的火里转化", "§7按钮把你送进下界大厅",
                        "§7那里有整套皮革和岩浆池"));
                sections.add(placeholder("§7④ 待加环节", 1));
            }
            case "priest" -> {
                sections.add(section("§f① 入教 · 教会祭坛", 1, (w, y, x, z, width) -> {
                    Shrines.priest(new Blocks(w), x + 5, y, z + DEPTH / 2);
                    priestAltar = new Location(w, x + 5.5, y + 1, z + DEPTH / 2.0 + 0.5);
                    chest(w, x + 2, y + 1, z + DEPTH - 2, BlockFace.NORTH, centred(
                            new ItemStack(Material.GOLD_INGOT, 4),
                            new ItemStack(Material.GLOWSTONE_DUST, 4),
                            new ItemStack(Material.REDSTONE, 8),
                            new ItemStack(Material.BREAD, 30)));
                }, "§7钻石块是教会祭坛", "§7拿四样材料右键它", "§7教会位置就设在这里"));
                sections.add(section("§6② 献祭 · 教会捐赠", 1, (w, y, x, z, width) ->
                        chest(w, x + 2, y + 1, z + DEPTH - 2, BlockFace.NORTH, donationList()),
                        "§7苹果 / 生鳕鱼 / 熟鳕鱼", "§7熟猪排 / 面包", "§7拿它们右键祭坛换能量"));
                sections.add(section("§8③ 逐出教会 · 恢复人类", 1, (w, y, x, z, width) ->
                        chest(w, x + 2, y + 1, z + DEPTH - 2, BlockFace.NORTH, centred(
                                new ItemStack(Material.COAL, 4))),
                        "§7手持煤炭右键祭坛", "§7会被逐出教会并变回人类"));
                sections.add(section("§b④ 法术 · 净化与治疗", 3, (w, y, x, z, width) -> {
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
                }, "§7用法术物品攻击目标", "§7陪练会被先变成异族", "§7这样每个法术都有对象"));
                sections.add(section("§5⑤ 法术 · 汲取与守护", 2, (w, y, x, z, width) -> {
                    spellBench(w, y, x, z, Material.BOOK, Material.WHITE_WOOL, Material.BOWL);
                    switchpoint(w, y, x + 5, z + 3, spell("BOOK"),
                            "§7书 · 汲取", "§7抽走对方 15% 能量");
                    switchpoint(w, y, x + 8, z + 3, spell("WHITE_WOOL"),
                            "§7羊毛 · 守护天使", "§7给人类设置守护关系");
                    switchpoint(w, y, x + 11, z + 3, spell("BOWL"),
                            "§7碗 · 远程捐赠", "§7背包里有可捐物就有效");
                }, "§7汲取 / 守护天使 / 远程捐赠", "§7远程捐赠一次只收一件，收益减半",
                        "§7箱子里的东西关箱就补满"));
                sections.add(placeholder("§7⑥ 待加环节", 1));
            }
            case "witchhunter" -> {
                sections.add(section("§7① 入会 · 铁门与告示牌", 1, (w, y, x, z, width) -> {
                    door(w, x + 6, y + 1, z + DEPTH / 2);
                    set(w, STELE, x + 7, y + 1, z + DEPTH / 2);
                    set(w, STELE, x + 7, y + 2, z + DEPTH / 2);
                    signOn(w, x + 7, y + 2, z + DEPTH / 2, BlockFace.EAST, "WitchHunter");
                    switchpoint(w, y, x + 2, z + 3, Trigger.of(Kind.INVITE_HUNTER),
                            "§a报名入会", "§7等约 10 秒再右击铁门");
                }, "§7铁门旁告示牌写着 WitchHunter", "§7入会要邀请", "§7杀死 3 种异族会收到邀请"));
                sections.add(section("§a② 弓箭模式 · 打假人", 3, (w, y, x, z, width) -> {
                    chest(w, x + 2, y + 1, z + DEPTH - 2, BlockFace.NORTH, centred(
                            new ItemStack(Material.BOW, 1),
                            new ItemStack(Material.ARROW, 32)));
                    chest(w, x + 4, y + 1, z + DEPTH - 2, BlockFace.NORTH, centred(
                            new ItemStack(Material.ARROW, 32)));
                    longDummy(w, "witchhunter_bow", x + 12, y, z + DEPTH / 2);                    switchpoint(w, y, x + 5, z + 3, Trigger.of(Kind.LEFT_CLICK),
                            "§a切换箭矢模式", "§7也可以自己空挥弓");
                    switchpoint(w, y, x + 8, z + 3, Trigger.of(Kind.SPAR),
                            "§f叫陪练过来", "§7拿他当靶子试特殊箭");
                }, "§7手持弓左键循环箭种", "§7normal → fire → triple → power → grapple",
                        "§7power 箭有 15 秒冷却"));
                sections.add(section("§6③ 悬赏", 1, (w, y, x, z, width) -> {
                    switchpoint(w, y, x + 4, z + 3, Trigger.of(Kind.BOUNTY),
                            "§6查看悬赏名单", "§7顺手刷新一个悬赏");
                    switchpoint(w, y, x + 7, z + 3, Trigger.of(Kind.POWER, "witchhunter"),
                            "§b能量 +1000", "§7够放特殊箭");
                }, "§7服务器最多保留 5 个悬赏目标", "§7杀掉悬赏目标额外 +8000 能量",
                        "§7按钮会报出当前名单"));
                sections.add(section("§7④ 护甲限制", 1, (w, y, x, z, width) ->
                        chest(w, x + 2, y + 1, z + DEPTH - 2, BlockFace.NORTH, centred(
                                new ItemStack(Material.LEATHER_CHESTPLATE, 1),
                                new ItemStack(Material.IRON_CHESTPLATE, 1),
                                new ItemStack(Material.DIAMOND_CHESTPLATE, 1))),
                        "§7只允许穿皮革护甲", "§7穿别的会被脱下来"));
                sections.add(placeholder("§7⑤ 待加环节", 1));
            }
            case "enderborn" -> {
                sections.add(section("§5① 转化 · 珍珠与粘土", 1, (w, y, x, z, width) -> {
                    layer(w, Material.CLAY, y, x + 4, z + DEPTH / 2 - 1,
                            x + 6, z + DEPTH / 2 + 1);
                    chest(w, x + 2, y + 1, z + DEPTH - 2, BlockFace.NORTH, centred(
                            new ItemStack(Material.ENDER_PEARL, 8)));
                }, "§7手持末影珍珠右键粘土", "§7材料在箱子里"));
                sections.add(section("§d② 末影珍珠 · 两种用法", 1, (w, y, x, z, width) ->
                        chest(w, x + 2, y + 1, z + DEPTH - 2, BlockFace.NORTH, centred(
                                new ItemStack(Material.ENDER_PEARL, 16))),
                        "§7右键＝不传送，换 +100 能量", "§7左键＝切换是否真的传送"));
                sections.add(placeholder("§7③ 待加环节", 1));
            }
            case "angel" -> {
                sections.add(section("§f① 转化 · 手持羽毛摔死", 3, (w, y, x, z, width) -> {
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
                }, "§7手持羽毛从 28 格高摔死", "§7羽毛在箱子里", "§7按钮送你上台"));
                sections.add(section("§e② 天使道具", 1, (w, y, x, z, width) ->
                        chest(w, x + 2, y + 1, z + DEPTH - 2, BlockFace.NORTH, centred(
                                new ItemStack(Material.DANDELION, 8),
                                new ItemStack(Material.FEATHER, 8),
                                new ItemStack(Material.PAPER, 8))),
                        "§7蒲公英左键＝超级跳", "§7羽毛攻击＝治疗目标", "§7纸攻击＝把异族变回人类"));
                sections.add(section("§b③ 召唤与护甲", 1, (w, y, x, z, width) ->
                        chest(w, x + 2, y + 1, z + DEPTH - 2, BlockFace.NORTH, centred(
                                new ItemStack(Material.BEEF, 8),
                                new ItemStack(Material.BONE, 8),
                                new ItemStack(Material.PORKCHOP, 8),
                                new ItemStack(Material.GOLDEN_CHESTPLATE, 1),
                                new ItemStack(Material.DIAMOND_CHESTPLATE, 1))),
                        "§7生牛肉 / 骨头 / 猪肉 对方块左键", "§7分别召唤牛 / 狼 / 猪",
                        "§7禁止金与钻石护甲"));
                sections.add(placeholder("§7④ 待加环节", 1));
            }
            default -> throw new IllegalStateException("no hall for " + race);
        }
        // Every race gets these three, built by the same code, at the end of its row.
        sections.add(dummySection(race));
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
        return section("§f→ 回到开头 / 离开测试", 2, (w, y, x, z, width) -> {
            switchpoint(w, y, x + 4, z + 3, Trigger.of(Kind.RETURN_START, race),
                    "§a回到开头", "§7回到这一族的入口，种族不变");
            switchpoint(w, y, x + 8, z + 3, Trigger.of(Kind.EXIT_TEST, race),
                    "§a离开测试", "§7清道具 · 恢复人类 · 回大厅");
        }, "§7走到这里就不用再折返回去了");
    }

    /** A section that is only a note in the row, so the next step has somewhere to go. */
    private Section placeholder(String title, int slots) {
        return section(title, slots, (w, y, x, z, width) -> {
            wallSign(w, y, x + 2, z, "§7留给下一个环节", "§7照抄一段 Section 就能加");
        }, "§7这里还是空的");
    }

    private static Section section(String title, int slots, Station station, String... lines) {
        return new Section(title, List.of(lines), slots, false, station);
    }

    private static Section section(String title, int slots, boolean openSky, Station station,
            String... lines) {
        return new Section(title, List.of(lines), slots, openSky, station);
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

    /** The dummies that must never move, by key. */
    public Map<String, ArmorStand> dummies() {
        return dummies;
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

    public Location priestAltar() {
        return priestAltar;
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
     * Puts every switch, sign, chest and dummy back the way it was built. The switches are the
     * way out of a hall, so a switch that can go missing is the same as no way out.
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
        for (Map.Entry<String, ArmorStand> entry : dummies.entrySet()) {
            ArmorStand stand = entry.getValue();
            Location anchor = points.get("dummy." + entry.getKey());
            if (anchor == null) {
                continue;
            }
            if (stand.isDead() || !stand.isValid()) {
                spawnDummy(entry.getKey(), anchor);
                repaired = true;
                continue;
            }
            if (stand.getLocation().distanceSquared(anchor) > 0.05) {
                stand.teleport(anchor);
                repaired = true;
            }
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

    /** The dummies and whether they are still alive, for the moments that need to know. */
    public String dummyReport() {
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, ArmorStand> entry : dummies.entrySet()) {
            ArmorStand stand = entry.getValue();
            out.append(' ').append(entry.getKey())
                    .append('=').append(stand.isValid() ? "ok" : "gone");
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

        box(target, theme.wall(), x, top + 1, z, farX, top + 1, farZ);
        for (int dx = 1; dx < length; dx += LIGHT_SPACING) {
            for (int dz = 1; dz < depth; dz += LIGHT_SPACING) {
                set(target, theme.light(), x + dx, top + 1, z + dz);
            }
        }
        boxes.add(new Box(target.getName(), x - 1, y - 1, z - 1, farX + 1, top + 1, farZ + 1));
    }

    /** A section's title and teaching lines, hung along the near wall in reading order. */
    private void sectionHead(World target, int y, int x, int z, String title, List<String> lines) {
        List<String> text = new ArrayList<>();
        text.add(title);
        text.addAll(lines);
        wallSign(target, y, x + 2, z, text.toArray(new String[0]));
    }

    /** A run of signs on a hall's near wall, three lines each, readable from inside. */
    private void wallSign(World target, int y, int x, int z, String... lines) {
        for (int sign = 0; sign * 3 < lines.length && sign < 4; sign++) {
            int from = sign * 3;
            int to = Math.min(lines.length, from + 3);
            signOn(target, x + sign, y + 2, z - 1, BlockFace.SOUTH,
                    List.of(lines).subList(from, to).toArray(new String[0]));
        }
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
        signOn(target, x, y + 2, z - 1, BlockFace.SOUTH, lines);
    }

    private void door(World target, int x, int y, int z) {
        set(target, Material.IRON_DOOR, x, y, z);
        set(target, Material.IRON_DOOR, x, y + 1, z);
        Block lower = target.getBlockAt(x, y, z);
        if (lower.getBlockData() instanceof Door data) {
            data.setFacing(BlockFace.NORTH);
            lower.setBlockData(data, false);
            Block upper = target.getBlockAt(x, y + 1, z);
            if (upper.getBlockData() instanceof Door top) {
                top.setFacing(BlockFace.NORTH);
                upper.setBlockData(top, false);
            }
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
     * The dummy a section is about: a stand that reports what hitting it does and cannot be
     * moved, knocked over, dressed or taken. {@link HallGuard} cancels the damage, so it is
     * never destroyed either - and if one is destroyed anyway, the hall puts a new one back on
     * the same mark and takes the dropped remains away.
     */
    private ArmorStand dummy(World target, String key, int x, int y, int z) {
        Location anchor = new Location(target, x + 0.5, y + 1, z + 0.5);
        points.put("dummy." + key, anchor);
        return spawnDummy(key, anchor);
    }

    /** Puts a fresh dummy on a mark, for the first one and for any that had to be replaced. */
    ArmorStand spawnDummy(String key, Location anchor) {
        ArmorStand stand = anchor.getWorld().spawn(anchor, ArmorStand.class, spawned -> {
            spawned.setGravity(false);
            spawned.setBasePlate(false);
            spawned.setArms(true);
            spawned.setInvulnerable(false);
            spawned.setPersistent(true);
            spawned.setRemoveWhenFarAway(false);
            spawned.setCanMove(false);
            spawned.setCustomName("§f测试假人");
            spawned.setCustomNameVisible(true);
            spawned.setRightArmPose(new EulerAngle(Math.toRadians(-90), 0, 0));
        });
        dummies.put(key, stand);
        return stand;
    }

    /** The same, from a mark, for a dummy that was destroyed and has to be put back. */
    ArmorStand replaceDummy(String key, Location anchor) {
        return spawnDummy(key, anchor);
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

    /** A dummy with a target wall behind it, for the bow modes that need something to hit. */
    private ArmorStand longDummy(World target, String key, int x, int y, int z) {
        box(target, Material.WHITE_CONCRETE, x - 1, y + 1, z - 2, x - 1, y + 3, z + 2);
        box(target, Material.RED_CONCRETE, x - 1, y + 2, z - 1, x - 1, y + 2, z + 1);
        return dummy(target, key, x, y, z);
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
