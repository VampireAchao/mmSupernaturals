package me.matterz.supernaturals.it.harness;

import me.matterz.supernaturals.SuperNPlayer;
import me.matterz.supernaturals.io.SNConfigHandler;
import me.matterz.supernaturals.manager.SuperNManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Test-only companion plugin. It gives a scenario the two things the game itself
 * cannot hand out over a console command: a player to act as, and a way to make
 * that player do something.
 *
 * <p>It is never shipped - the IT build copies it into the scratch server only, and
 * it is compiled from {@code src/test}.
 *
 * <p>Two front ends, one implementation: {@code /mmp ...} for a human poking at a
 * running server, and a loopback line protocol for the tests. Everything is executed
 * on the server thread, because the Bukkit API is not thread safe.
 */
public final class HarnessPlugin extends JavaPlugin {

    /** Chosen away from the default 25565 so a dev server can stay up alongside. */
    public static final int DEFAULT_CONTROL_PORT = 25578;

    private static final long REQUEST_TIMEOUT_SECONDS = 30;

    /** Server-side player objects, so {@code quit} can remove exactly what was added. */
    private final Map<String, Object> actors = new LinkedHashMap<>();
    private ControlSocket socket;

    @Override
    public void onEnable() {
        // Everything the plugin explains to a player goes to that player's chat, and a fake
        // player has no chat. Its verbose logging is the only way a scenario can be told
        // which branch it took, so the harness switches it on. Set here rather than in
        // config.yml on purpose: writing that file ourselves would stop the plugin from
        // generating its defaults at all, leaving every configured material null.
        SNConfigHandler.debugMode = true;

        int port = Integer.getInteger("harness.port", DEFAULT_CONTROL_PORT);
        try {
            socket = new ControlSocket(this, port);
            socket.start();
        } catch (IOException e) {
            getLogger().severe("cannot listen on 127.0.0.1:" + port + " (" + e + ")");
        }
    }

    @Override
    public void onDisable() {
        if (socket != null) {
            socket.close();
        }
        for (Map.Entry<String, Object> actor : new ArrayList<>(actors.entrySet())) {
            try {
                Nms.disconnect(actor.getValue());
            } catch (RuntimeException e) {
                getLogger().warning("leaving " + actor.getKey() + " behind: " + e);
            }
        }
        actors.clear();
    }

    // -- the two front ends ------------------------------------------------

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        sender.sendMessage(onRequest(String.join(" ", args)));
        return true;
    }

    /** Runs one request on the server thread and returns a single-line response. */
    String onRequest(String request) {
        CompletableFuture<String> answer = new CompletableFuture<>();
        Bukkit.getScheduler().runTask(this, () -> {
            try {
                answer.complete(handle(request));
            } catch (Throwable t) {
                answer.complete("err " + t);
            }
        });
        try {
            return answer.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            return "err no answer within " + REQUEST_TIMEOUT_SECONDS + "s: " + request;
        }
    }

    private String handle(String request) {
        String verb = request.trim();
        String rest = "";
        int separator = verb.indexOf(' ');
        if (separator >= 0) {
            rest = verb.substring(separator + 1).trim();
            verb = verb.substring(0, separator);
        }
        switch (verb) {
            case "ping":
                return "ok pong";
            case "spawn":
                return spawn(rest);
            case "quit":
                return quit(rest);
            case "online":
                return "ok " + String.join(" ", onlineNames());
            case "type":
                return "ok " + raceOf(rest);
            case "run":
                return run(rest);
            case "give":
                return give(rest);
            case "perm":
                return perm(rest);
            case "tp":
                return teleport(rest);
            case "interact":
                return interact(rest);
            case "block":
                return "ok " + blockAt(rest).getType();
            case "count":
                return count(rest);
            case "fireticks":
                return "ok " + player(rest).getFireTicks();
            case "health":
                return "ok " + player(rest).getHealth();
            case "stop":
                Bukkit.getScheduler().runTask(this, Bukkit::shutdown);
                return "ok stopping";
            default:
                return "err unknown request: " + verb;
        }
    }

    // -- requests ----------------------------------------------------------

    private String spawn(String name) {
        if (name.isEmpty()) {
            return "err usage: spawn <name>";
        }
        if (Bukkit.getPlayerExact(name) != null) {
            return "err " + name + " is already online";
        }
        actors.put(name, Nms.spawn(name));

        Player player = Bukkit.getPlayerExact(name);
        if (player == null) {
            return "err " + name + " joined but the server does not list them";
        }
        return "ok " + name + " uuid=" + player.getUniqueId()
                + " online=" + Bukkit.getOnlinePlayers().size()
                + " world=" + player.getWorld().getName();
    }

    private String quit(String name) {
        Object actor = actors.remove(name);
        if (actor == null) {
            return "err " + name + " is not a harness actor";
        }
        Nms.disconnect(actor);
        return "ok " + name + " left, online=" + Bukkit.getOnlinePlayers().size();
    }

    private String raceOf(String name) {
        SuperNPlayer player = SuperNManager.get(player(name));
        return player == null ? "unknown" : player.getType();
    }

    private String run(String command) {
        if (command.isEmpty()) {
            return "err usage: run <server command>";
        }
        boolean handled = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        return "ok handled=" + handled;
    }

    private String give(String rest) {
        String[] args = rest.split("\\s+");
        if (args.length < 3) {
            return "err usage: give <name> <material> [count]";
        }
        Player player = player(args[0]);
        Material material = Material.matchMaterial(args[1]);
        if (material == null) {
            return "err unknown material: " + args[1];
        }
        int count = args.length > 2 ? intOf(args[2]) : 1;
        // Respect the stack limit: a stack of four mushroom stews is not a thing the game
        // can hold, and the inventory quietly keeps only one.
        int stackSize = Math.max(1, material.getMaxStackSize());
        for (int left = count; left > 0; left -= stackSize) {
            player.getInventory().addItem(new ItemStack(material, Math.min(stackSize, left)));
        }
        return "ok gave " + count + " " + material + " to " + player.getName();
    }

    private String perm(String rest) {
        String[] args = rest.split("\\s+");
        if (args.length < 2) {
            return "err usage: perm <name> <node> [true|false]";
        }
        Player player = player(args[0]);
        boolean value = args.length < 3 || Boolean.parseBoolean(args[2]);
        player.addAttachment(this, args[1], value);
        player.recalculatePermissions();
        return "ok " + args[1] + "=" + value + " effective=" + player.hasPermission(args[1]);
    }

    private String teleport(String rest) {
        String[] args = rest.split("\\s+");
        if (args.length < 4) {
            return "err usage: tp <name> <x> <y> <z>";
        }
        Player player = player(args[0]);
        Location target = new Location(player.getWorld(),
                doubleOf(args[1]), doubleOf(args[2]), doubleOf(args[3]));
        player.teleport(target);
        Location at = player.getLocation();
        return "ok " + player.getName() + " at " + at.getBlockX() + "," + at.getBlockY() + "," + at.getBlockZ();
    }

    /**
     * Fires a real right-click on a block on behalf of an actor. A fake player has no
     * client, so every interaction a scenario needs has to be raised here; that is
     * also the reason a race room in the game needs a programmatic entry point for
     * anything a player would normally do by hand.
     */
    private String interact(String rest) {
        String[] args = rest.split("\\s+");
        if (args.length < 4) {
            return "err usage: interact <name> <x> <y> <z>";
        }
        Player player = player(args[0]);
        Block target = blockAt(rest);
        PlayerInteractEvent event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK,
                player.getInventory().getItemInMainHand(), target, BlockFace.UP);
        Bukkit.getPluginManager().callEvent(event);
        return "ok cancelled=" + event.isCancelled() + " block=" + target.getType();
    }

    /** How much of a material the actor is carrying, so a scenario can check its own setup. */
    private String count(String rest) {
        String[] args = rest.split("\\s+");
        if (args.length < 2) {
            return "err usage: count <name> <material>";
        }
        Player player = player(args[0]);
        Material material = Material.matchMaterial(args[1]);
        if (material == null) {
            return "err unknown material: " + args[1];
        }
        int total = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && stack.getType() == material) {
                total += stack.getAmount();
            }
        }
        return "ok " + total;
    }

    private Block blockAt(String rest) {        String[] args = rest.split("\\s+");
        if (args.length < 4) {
            throw new IllegalArgumentException("usage: <name|loc> <x> <y> <z>");
        }
        if (args.length == 4) {
            Player player = player(args[0]);
            return player.getWorld().getBlockAt(intOf(args[1]), intOf(args[2]), intOf(args[3]));
        }
        return Bukkit.getWorlds().get(0).getBlockAt(intOf(args[0]), intOf(args[1]), intOf(args[2]));
    }

    // -- helpers -----------------------------------------------------------

    private Collection<String> onlineNames() {
        List<String> names = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            names.add(player.getName());
        }
        return names;
    }

    private Player player(String name) {
        Player player = Bukkit.getPlayerExact(name);
        if (player == null) {
            throw new IllegalArgumentException(name + " is not online");
        }
        return player;
    }

    private static int intOf(String value) {
        return (int) doubleOf(value);
    }

    private static double doubleOf(String value) {
        return Double.parseDouble(value);
    }

    // -- control channel ---------------------------------------------------

    /**
     * One request per line, one response per line, {@code ok ...} or {@code err ...}.
     * Bound to loopback: this exists to let a test process drive the server, and it
     * hands out operator-level control, so it must not be reachable from anywhere else.
     */
    private static final class ControlSocket implements Closeable {

        private final HarnessPlugin plugin;
        private final ServerSocket server;
        private volatile boolean closed;

        ControlSocket(HarnessPlugin plugin, int port) throws IOException {
            this.plugin = plugin;
            this.server = new ServerSocket();
            this.server.setReuseAddress(true);
            this.server.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), port));
        }

        void start() {
            Thread thread = new Thread(this::acceptLoop, "mmHarness-control");
            thread.setDaemon(true);
            thread.start();
            plugin.getLogger().info("control channel on 127.0.0.1:" + server.getLocalPort());
        }

        private void acceptLoop() {
            while (!closed) {
                try (Socket client = server.accept()) {
                    client.setTcpNoDelay(true);
                    serve(client);
                } catch (IOException e) {
                    if (!closed) {
                        plugin.getLogger().warning("control channel: " + e);
                    }
                }
            }
        }

        private void serve(Socket client) throws IOException {
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
            PrintWriter out = new PrintWriter(
                    new OutputStreamWriter(client.getOutputStream(), StandardCharsets.UTF_8), true);
            String line;
            while ((line = in.readLine()) != null) {
                out.println(plugin.onRequest(line));
                if (line.trim().equals("stop")) {
                    return;
                }
            }
        }

        @Override
        public void close() {
            closed = true;
            try {
                server.close();
            } catch (IOException ignored) {
                // shutting down anyway
            }
        }
    }
}
