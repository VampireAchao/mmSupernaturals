package me.matterz.supernaturals.it.harness;

/**
 * What a shrine is made of, in one place. The harness plugin builds them for a hand-run
 * server, and a scenario builds them through a different mechanism - server commands - so
 * the shape is written here and each side only supplies the way to put a block down.
 *
 * <p>It lives in this package because the harness is the only test code that is packaged
 * into the server; a plugin cannot reach a class next to {@code TestServer}.
 */
public final class Shrines {

    /** Half-width of the pad a shrine stands on, so the material count reaches 25. */
    public static final int PAD_RADIUS = 2;

    /** The two ways this codebase can put a block down: a Bukkit world, or a server command. */
    public interface Placer {

        void layer(String material, int y, int fromX, int fromZ, int toX, int toZ);

        void place(String material, int x, int y, int z);
    }

    private Shrines() {
    }

    /** The vampire shrine: obsidian to stand on, a gold block to click. */
    public static void vampire(Placer placer, int x, int y, int z) {
        placer.layer("OBSIDIAN", y, x - PAD_RADIUS, z - PAD_RADIUS, x + PAD_RADIUS, z + PAD_RADIUS);
        placer.place("GOLD_BLOCK", x, y, z);
    }

    /** The cure shrine: glowstone to stand on, lapis to click. */
    public static void cure(Placer placer, int x, int y, int z) {
        placer.layer("GLOWSTONE", y, x - PAD_RADIUS, z - PAD_RADIUS, x + PAD_RADIUS, z + PAD_RADIUS);
        placer.place("LAPIS_BLOCK", x, y, z);
    }

    /**
     * The priest's altar: a diamond block, with a ring of quartz under it so it reads as an
     * altar rather than a stray block. The plugin only ever looks at the diamond block.
     */
    public static void priest(Placer placer, int x, int y, int z) {
        placer.layer("QUARTZ_BLOCK", y, x - PAD_RADIUS, z - PAD_RADIUS, x + PAD_RADIUS, z + PAD_RADIUS);
        placer.place("DIAMOND_BLOCK", x, y, z);
    }
}
