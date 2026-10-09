package me.matterz.supernaturals.it;

import org.junit.jupiter.api.BeforeAll;

/**
 * Base class for the scenarios. It opens the shared server and knows the geometry of
 * the flat test world, so a scenario can talk about "the vampire altar at 8,8" instead
 * of about the forty blocks that make one.
 *
 * <p>The geometry itself lives in {@link Arena}, which a hand-run server builds from too.
 */
public abstract class ServerTest {

    /**
     * The top-most solid layer of the flat world (bedrock, three stone, dirt, grass);
     * actors stand on the block above it.
     */
    protected static final int GROUND_Y = Arena.FLAT_GROUND_Y;

    @BeforeAll
    static void keepTheWorldLoaded() {
        // Force-loading the arena makes the cases independent of where the actors happen
        // to be standing. The origin is the middle of the flat world's arena.
        Arena.forceload(server(), 0, 0);
    }

    protected static TestServer server() {
        return TestServer.shared();
    }

    /** Fills one layer of the world at ground level. */
    protected static void layer(String material, int fromX, int fromZ, int toX, int toZ) {
        Arena.layer(server(), material, GROUND_Y, fromX, fromZ, toX, toZ);
    }

    protected static void place(String material, int x, int y, int z) {
        Arena.place(server(), material, x, y, z);
    }

    /** The vampire shrine: a gold block to click, with enough obsidian around it. */
    protected static void vampireAltar(int x, int z) {
        Arena.vampireAltar(server(), x, GROUND_Y, z);
    }

    /** The cure shrine: a lapis block to click, with enough glowstone around it. */
    protected static void cureAltar(int x, int z) {
        Arena.cureAltar(server(), x, GROUND_Y, z);
    }
}
