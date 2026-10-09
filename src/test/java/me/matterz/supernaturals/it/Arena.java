package me.matterz.supernaturals.it;

import me.matterz.supernaturals.it.harness.Shrines;

import java.util.Locale;

/**
 * Where the shrines stand in the flat test world, and the two block commands a scenario
 * builds them with. The shapes themselves come from {@link Shrines}, which the test hall
 * builds from too, so a shrine cannot change for one and stay old for the other.
 *
 * <p>Coordinates are absolute on purpose: a case that says "the vampire altar at 8,8" is
 * easier to read and to debug than one that says "the block the other method built".
 */
public final class Arena {

    /** The vampire shrine: a gold block on obsidian, enough of it to count as a shrine. */
    public static final int VAMPIRE_X = 8;
    public static final int VAMPIRE_Z = 8;

    /** The cure shrine: a lapis block on glowstone. */
    public static final int CURE_X = 24;
    public static final int CURE_Z = 8;

    /**
     * Ground level of the flat test world - the grass, which is the block a shrine's centre
     * replaces.
     */
    public static final int FLAT_GROUND_Y = -59;

    /** How far from the origin the arena reaches; its chunks are forced loaded. */
    public static final int REACH = 48;

    private Arena() {
    }

    /**
     * Keeps the arena's chunks loaded. Nothing is online right after boot, and an unloaded
     * chunk refuses every block command.
     */
    public static void forceload(TestServer server, int centerX, int centerZ) {
        server.run("forceload add " + (centerX - REACH) + " " + (centerZ - REACH)
                + " " + (centerX + REACH) + " " + (centerZ + REACH));
    }

    /** The vampire shrine at {@code x,z}: obsidian to stand on, gold to click. */
    public static void vampireAltar(TestServer server, int x, int y, int z) {
        Shrines.vampire(commands(server), x, y, z);
    }

    /** The cure shrine at {@code x,z}: glowstone to stand on, lapis to click. */
    public static void cureAltar(TestServer server, int x, int y, int z) {
        Shrines.cure(commands(server), x, y, z);
    }

    /** One layer of one material. */
    public static void layer(TestServer server, String material, int y,
            int fromX, int fromZ, int toX, int toZ) {
        fill(server, material, fromX, y, fromZ, toX, y, toZ);
    }

    /** One block where it is told. */
    public static void place(TestServer server, String material, int x, int y, int z) {
        server.run("setblock " + x + " " + y + " " + z + " " + block(material));
    }

    /** A box of one material. */
    public static void fill(TestServer server, String material,
            int fromX, int fromY, int fromZ, int toX, int toY, int toZ) {
        server.run("fill " + fromX + " " + fromY + " " + fromZ
                + " " + toX + " " + toY + " " + toZ + " " + block(material));
    }

    /** A shrine placed the way a scenario can: through the server's own commands. */
    private static Shrines.Placer commands(TestServer server) {
        return new Shrines.Placer() {

            @Override
            public void layer(String material, int y, int fromX, int fromZ, int toX, int toZ) {
                fill(server, material, fromX, y, fromZ, toX, y, toZ);
            }

            @Override
            public void place(String material, int x, int y, int z) {
                Arena.place(server, material, x, y, z);
            }
        };
    }

    /** Bukkit names the materials in upper case; a command wants {@code minecraft:lower_case}. */
    private static String block(String material) {
        return "minecraft:" + material.toLowerCase(Locale.ROOT);
    }
}
