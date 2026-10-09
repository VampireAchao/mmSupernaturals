package me.matterz.supernaturals.it;

import org.bukkit.Material;

/**
 * A player in the game with nobody at the keyboard. Every method asks the server to do
 * something on the actor's behalf, which is why the ones that stand in for a person -
 * {@link #interact} in particular - exist at all.
 */
public final class Actor {

    private final ControlClient control;
    private final String name;

    Actor(ControlClient control, String name) {
        this.control = control;
        this.name = name;
    }

    public String name() {
        return name;
    }

    /** The race the plugin currently has on file for this player. */
    public String race() {
        return control.call("type " + name);
    }

    /**
     * Grants a permission. Fails loudly if it does not take effect - a scenario that
     * silently runs without the permission it asked for would look like a plugin bug.
     */
    public Actor grant(String node) {
        String response = control.call("perm " + name + " " + node + " true");
        if (!response.contains("effective=true")) {
            throw new IllegalStateException("granting " + node + " to " + name + " did nothing: " + response);
        }
        return this;
    }

    public Actor revoke(String node) {
        control.call("perm " + name + " " + node + " false");
        return this;
    }

    public Actor give(String material, int count) {
        control.call("give " + name + " " + material + " " + count);
        return this;
    }

    /** Puts a full stack of each material in the actor's inventory. */
    public Actor carrying(String... materials) {
        for (String material : materials) {
            give(material, 64);
        }
        return this;
    }

    public Actor teleport(double x, double y, double z) {
        control.call("tp " + name + " " + x + " " + y + " " + z);
        return this;
    }

    /** Right-clicks a block, the way a player would, and reports what the click became. */
    public Interaction interact(int x, int y, int z) {
        return Interaction.parse(control.call("interact " + name + " " + x + " " + y + " " + z));
    }

    public int fireTicks() {
        return Integer.parseInt(control.call("fireticks " + name));
    }

    public double health() {
        return Double.parseDouble(control.call("health " + name));
    }

    /** Reads a block through the server, for asserting that the world really changed. */
    public Material blockAt(int x, int y, int z) {
        return Material.matchMaterial(control.call("block " + name + " " + x + " " + y + " " + z));
    }

    /** How much of a material the actor is carrying. */
    public int count(String material) {
        return Integer.parseInt(control.call("count " + name + " " + material));
    }

    /** Disconnects the actor. */
    public void leave() {
        control.call("quit " + name);
    }

    /**
     * What came back from a right-click: whether something had already cancelled it
     * (in which case the plugin never saw it), and the block it landed on.
     */
    public record Interaction(boolean cancelled, Material block) {

        static Interaction parse(String response) {
            String[] fields = response.split("\\s+");
            return new Interaction(
                    Boolean.parseBoolean(fields[0].substring("cancelled=".length())),
                    Material.matchMaterial(fields[1].substring("block=".length())));
        }

        @Override
        public String toString() {
            return "click on " + block + (cancelled ? " (already cancelled)" : "");
        }
    }
}
