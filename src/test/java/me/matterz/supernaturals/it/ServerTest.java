package me.matterz.supernaturals.it;

import org.junit.jupiter.api.BeforeAll;

/**
 * Base class for the scenarios. It opens the shared server and knows the geometry of
 * the flat test world, so a scenario can talk about "the vampire altar at 8,8" instead
 * of about the forty blocks that make one.
 */
public abstract class ServerTest {

    /**
     * The top-most solid layer of the flat world (bedrock, three stone, dirt, grass);
     * actors stand on the block above it.
     */
    protected static final int GROUND_Y = -59;

    @BeforeAll
    static void keepTheWorldLoaded() {
        // Nothing is online right after boot, and an unloaded chunk refuses every
        // block command - force-loading the arena makes the cases independent of
        // where the actors happen to be standing.
        server().run("forceload add -48 -48 48 48");
    }

    protected static TestServer server() {
        return TestServer.shared();
    }

    /** Fills one layer of the world at ground level. */
    protected static void layer(String material, int fromX, int fromZ, int toX, int toZ) {
        server().run("fill " + fromX + " " + GROUND_Y + " " + fromZ
                + " " + toX + " " + GROUND_Y + " " + toZ + " minecraft:" + material);
    }

    protected static void place(String material, int x, int y, int z) {
        server().run("setblock " + x + " " + y + " " + z + " minecraft:" + material);
    }

    /** The vampire shrine: a gold block to click, with enough obsidian around it. */
    protected static void vampireAltar(int x, int z) {
        layer("obsidian", x - 2, z - 2, x + 2, z + 2);
        place("gold_block", x, GROUND_Y, z);
    }

    /** The cure shrine: a lapis block to click, with enough glowstone around it. */
    protected static void cureAltar(int x, int z) {
        layer("glowstone", x - 2, z - 2, x + 2, z + 2);
        place("lapis_block", x, GROUND_Y, z);
    }
}
