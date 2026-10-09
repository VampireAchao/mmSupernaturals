package me.matterz.supernaturals.it.harness;

import me.matterz.supernaturals.SuperNPlayer;
import me.matterz.supernaturals.SupernaturalsPlugin;
import me.matterz.supernaturals.io.SNConfigHandler;
import me.matterz.supernaturals.manager.SuperNManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.HeightMap;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.Sign;
import org.bukkit.block.data.Directional;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
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
import java.util.Locale;
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
    private Hall hall;
    private HallGuard guard;

    @Override
    public void onEnable() {
        // Everything the plugin explains to a player goes to that player's chat, and a fake
        // player has no chat. Its verbose logging is the only way a scenario can be told
        // which branch it took, so the harness switches it on. Set here rather than in
        // config.yml on purpose: writing that file ourselves would stop the plugin from
        // generating its defaults at all, leaving every configured material null.
        SNConfigHandler.debugMode = true;

        if (Boolean.getBoolean("harness.hall")) {
            buildHall();
        }

        int port = Integer.getInteger("harness.port", DEFAULT_CONTROL_PORT);
        try {
            socket = new ControlSocket(this, port);
            socket.start();
        } catch (IOException e) {
            getLogger().severe("cannot listen on 127.0.0.1:" + port + " (" + e + ")");
        }
    }

    /**
     * Puts the test hall above the world's spawn. The scenarios run without it - they place
     * what they need at known coordinates - so this is asked for, by the hand-run server.
     */
    private void buildHall() {
        World world = Bukkit.getWorlds().get(0);
        hall = Hall.build(world);
        guard = new HallGuard(this);
        getServer().getPluginManager().registerEvents(guard, this);
        // Every way out of the hall is a switch the hall placed, so the hall watches its own
        // furniture: whatever manages to move a button is back a moment later, every dummy is
        // back where it was put, and everything a section let out goes home. Anything that
        // changes a block inside the hall also repairs on the spot; this is the backstop.
        getServer().getScheduler().runTaskTimer(this, guard::tick, 40L, 40L);
        // The priest's altar only answers within ten blocks of the church in the plugin's
        // configuration, and writing that file ourselves would stop the plugin generating its
        // own defaults - so the location is pointed at the hall instead, a moment after both
        // plugins have finished loading.
        getServer().getScheduler().runTaskLater(this, this::pointTheChurchAtTheHall, 40L);
        // The guardian-angel spell is matched against a material <em>name</em> from the plugin's
        // own configuration, and the default - "WOOL" - is not a material any more, so the spell
        // can never fire as shipped. The hall points it at one that exists, the same way the
        // church location is pointed at the altar; the plugin's own file is left alone.
        if (Material.getMaterial(SNConfigHandler.priestSpellGuardianAngel) == null) {
            SNConfigHandler.priestSpellGuardianAngel = "WHITE_WOOL";
        }
        getLogger().info("test hall in the lobby at "
                + hall.lobbySpawn().getBlockX() + "," + hall.lobbySpawn().getBlockY() + ","
                + hall.lobbySpawn().getBlockZ() + " - joining puts you there");
    }

    private void pointTheChurchAtTheHall() {
        if (hall == null || hall.priestAltar() == null) {
            return;
        }
        SNConfigHandler.priestChurchWorld = hall.priestAltar().getWorld().getName();
        SNConfigHandler.priestChurchLocationX = hall.priestAltar().getBlockX();
        SNConfigHandler.priestChurchLocationY = hall.priestAltar().getBlockY();
        SNConfigHandler.priestChurchLocationZ = hall.priestAltar().getBlockZ();
        getLogger().info("priest church pointed at " + String.join(",",
                String.valueOf(SNConfigHandler.priestChurchLocationX),
                String.valueOf(SNConfigHandler.priestChurchLocationY),
                String.valueOf(SNConfigHandler.priestChurchLocationZ)));
    }

    /** The hall, or null on a server that was not asked for one. */
    Hall hallOrNull() {
        return hall;
    }

    /**
     * Brings a stand-in player up, for the few sections that need somebody to do the hitting: a
     * rule decided on the victim's side cannot be shown any other way than by hitting the victim.
     */
    Object spawnGhost(String name) {
        try {
            return Nms.spawn(name);
        } catch (RuntimeException alreadyThere) {
            return null;
        }
    }

    /** Takes the stand-in away again, so it is not left in the tab list between tests. */
    void dropGhost(Object actor) {
        if (actor != null) {
            Nms.disconnect(actor);
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
        // A command typed on the console is already running on the server thread. Scheduling
        // the work onto that thread and then waiting for it would have the thread wait for
        // itself, which trips the watchdog and takes the server down.
        if (Bukkit.isPrimaryThread()) {
            return handleSafely(request);
        }
        CompletableFuture<String> answer = new CompletableFuture<>();
        Bukkit.getScheduler().runTask(this, () -> answer.complete(handleSafely(request)));
        try {
            return answer.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            return "err no answer within " + REQUEST_TIMEOUT_SECONDS + "s: " + request;
        }
    }

    private String handleSafely(String request) {
        try {
            return handle(request);
        } catch (Throwable t) {
            return "err " + t;
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
            case "hall":
                return hall();
            case "surface":
                return "ok " + surfaceOf(rest);
            case "worldspawn":
                return "ok " + worldSpawnOf();
            case "chest":
                return chest(rest);
            case "chestuse":
                return chestUse(rest);
            case "sign":
                return signLine(rest);
            case "facing":
                return facing(rest);
            case "where":
                return where(rest);
            case "mine":
                return mine(rest);
            case "step":
                return step(rest);
            case "leftclick":
                return leftClick(rest);
            case "food":
                return "ok " + player(rest.trim()).getFoodLevel();
            case "power":
                return "ok " + (int) SuperNManager.get(player(rest.trim())).getPower();
            case "dummies":
                return dummies();
            case "buttons":
                return buttons(rest);
            case "section":
                return section(rest);
            case "mobs":
                return mobs(rest);
            case "weather":
                return weather(rest);
            case "repairs":
                return repairs();
            case "hitdummy":
                return hitDummy(rest);
            case "tppoint":
                return teleportPoint(rest);
            case "biome":
                return biome(rest);
            case "race":
                return race(rest);
            case "loot":
                return loot(rest);
            case "hold":
                return hold(rest);
            case "slay":
                return slay(rest);
            case "world":
                return "ok " + player(rest).getWorld().getName();
            case "worlds":
                return "ok " + String.join(" ", worldNames());
            case "time":
                return time(rest);
            case "difficulty":
                return difficulty(rest);
            case "targets":
                return targets(rest);
            case "diag":
                return diag(rest);
            case "slain":
                return slain(rest);
            case "bucket":
                return bucket(rest);
            case "bounds":
                return hall == null ? "err no hall on this server"
                        : "ok" + hall.describeBounds().replaceAll("\\R", " ");
            case "zombie":
                return zombie(rest);
            case "wear":
                return wear(rest);
            case "kill":
                return kill(rest);
            case "respawn":
                return respawn(rest);
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
        if (args.length < 2) {
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
        // A player who clicks a block is looking at it, and one trigger in the plugin asks
        // what the player is looking at rather than what was clicked - so aim first.
        aimAt(player, target);
        PlayerInteractEvent event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK,
                player.getInventory().getItemInMainHand(), target, BlockFace.UP);
        Bukkit.getPluginManager().callEvent(event);
        return "ok cancelled=" + event.isCancelled() + " block=" + target.getType();
    }

    /** Turns a player to face the middle of a block, the way a person about to click it is. */
    private static void aimAt(Player player, Block target) {
        Location from = player.getEyeLocation().clone();
        double dx = target.getX() + 0.5 - from.getX();
        double dy = target.getY() + 0.5 - from.getY();
        double dz = target.getZ() + 0.5 - from.getZ();
        double flat = Math.sqrt(dx * dx + dz * dz);
        Location aimed = player.getLocation();
        aimed.setYaw((float) Math.toDegrees(Math.atan2(-dx, dz)));
        aimed.setPitch((float) Math.toDegrees(-Math.atan2(dy, flat)));
        player.teleport(aimed);
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

    private Block blockAt(String rest) {
        String[] args = rest.split("\\s+");
        if (args.length < 4) {
            throw new IllegalArgumentException("usage: block <name|world|loc> <x> <y> <z>");
        }
        if (args.length == 4) {
            Player player = Bukkit.getPlayerExact(args[0]);
            if (player != null) {
                return player.getWorld().getBlockAt(intOf(args[1]), intOf(args[2]), intOf(args[3]));
            }
            World named = Bukkit.getWorld(args[0]);
            if (named != null) {
                return named.getBlockAt(intOf(args[1]), intOf(args[2]), intOf(args[3]));
            }
            throw new IllegalArgumentException("no such player or world: " + args[0]);
        }
        return Bukkit.getWorlds().get(0).getBlockAt(intOf(args[0]), intOf(args[1]), intOf(args[2]));
    }

    /**
     * The highest ground anywhere in a rectangle, ignoring leaves so a tree does not count as
     * a floor. This is what a scenario needs before it can lay blocks down in a world whose
     * surface is generated rather than known.
     */
    private int surfaceOf(String rest) {
        String[] args = rest.split("\\s+");
        if (args.length != 4) {
            throw new IllegalArgumentException("usage: surface <x1> <z1> <x2> <z2>");
        }
        World world = Bukkit.getWorlds().get(0);
        int highest = world.getMinHeight();
        for (int x = Math.min(intOf(args[0]), intOf(args[2]));
                x <= Math.max(intOf(args[0]), intOf(args[2])); x++) {
            for (int z = Math.min(intOf(args[1]), intOf(args[3]));
                    z <= Math.max(intOf(args[1]), intOf(args[3])); z++) {
                highest = Math.max(highest,
                        world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES));
            }
        }
        return highest;
    }

    /** Where the hall is, so the hand-run server can say so in its banner. */
    private String hall() {
        if (hall == null) {
            return "err no hall on this server (-Dharness.hall=true builds one)";
        }
        Location spawn = hall.lobbySpawn();
        return "ok " + spawn.getBlockX() + " " + spawn.getBlockY() + " " + spawn.getBlockZ();
    }

    /** What is in a chest right now, as {@code slot=MATERIALxCOUNT}, one per occupied slot. */
    private String chest(String rest) {
        String[] args = rest.split("\\s+");
        if (args.length != 3 && args.length != 4) {
            return "err usage: chest [world] <x> <y> <z>";
        }
        Block block = blockInWorld(args, 0);
        if (!(block.getState() instanceof Chest chest)) {
            return "err not a chest: " + block.getType();
        }
        return "ok " + describe(chest.getInventory());
    }

    /**
     * Opens a chest as a player, empties it the way a player carrying everything off would,
     * and closes it - which is the moment the hall puts its contents back. Reports what is in
     * there afterwards, so a scenario sees the refill for itself.
     */
    private String chestUse(String rest) {
        String[] args = rest.split("\\s+");
        if (args.length != 4 && args.length != 5) {
            return "err usage: chestuse <name> [world] <x> <y> <z>";
        }
        Player player = player(args[0]);
        Block block = blockInWorld(args, 1);
        if (!(block.getState() instanceof Chest chest)) {
            return "err not a chest: " + block.getType();
        }
        Inventory inventory = chest.getInventory();
        player.openInventory(inventory);
        inventory.clear();
        player.closeInventory();
        return "ok " + describe(inventory);
    }

    /** One line of a sign, so a scenario can check what a player would read. */
    private String signLine(String rest) {
        String[] args = rest.split("\\s+");
        if (args.length != 4 && args.length != 5) {
            return "err usage: sign [world] <x> <y> <z> <line>";
        }
        Block block = blockInWorld(args, 0);
        if (!(block.getState() instanceof Sign sign)) {
            return "err not a sign: " + block.getType();
        }
        return "ok " + sign.getLine(intOf(args[args.length - 1]));
    }

    /** Which way a directional block faces - a sign, a button, a chest - so it can be checked. */
    private String facing(String rest) {
        String[] args = rest.split("\\s+");
        if (args.length != 3 && args.length != 4) {
            return "err usage: facing [world] <x> <y> <z>";
        }
        Block block = blockInWorld(args, 0);
        if (!(block.getBlockData() instanceof Directional directional)) {
            return "err not directional: " + block.getType();
        }
        return "ok " + directional.getFacing();
    }

    private static Block blockInWorld(String[] args, int from) {
        int offset = looksLikeWorld(args[from]) ? 1 : 0;
        World world = offset == 1 ? Bukkit.getWorld(args[from]) : Bukkit.getWorlds().get(0);
        if (world == null) {
            throw new IllegalArgumentException("no such world: " + args[from]);
        }
        return world.getBlockAt(intOf(args[from + offset]), intOf(args[from + offset + 1]),
                intOf(args[from + offset + 2]));
    }

    /** Coordinates are whole numbers; anything else in front of them is a world name. */
    private static boolean looksLikeWorld(String token) {
        return !token.matches("-?\\d+");
    }

    /** Where a player is right now, so a scenario can tell that something moved them. */
    private String where(String rest) {
        Location at = player(rest).getLocation();
        return "ok " + at.getBlockX() + " " + at.getBlockY() + " " + at.getBlockZ();
    }

    /** Breaks a block as a player would, and reports whether anything stopped it. */
    private String mine(String rest) {
        String[] args = rest.split("\\s+");
        if (args.length != 4) {
            return "err usage: mine <name> <x> <y> <z>";
        }
        Block target = blockAt(rest);
        BlockBreakEvent event = new BlockBreakEvent(target, player(args[0]));
        Bukkit.getPluginManager().callEvent(event);
        return "ok cancelled=" + event.isCancelled() + " block=" + target.getType();
    }

    /**
     * Steps on a block as a player would, which is the event a pressure plate answers to -
     * the hall takes its steps on plates, so a scenario needs a way to take one.
     */
    private String step(String rest) {
        String[] args = rest.split("\\s+");
        if (args.length != 4) {
            return "err usage: step <name> <x> <y> <z>";
        }
        Player player = player(args[0]);
        Block target = blockAt(rest);
        PlayerInteractEvent event = new PlayerInteractEvent(player, Action.PHYSICAL,
                player.getInventory().getItemInMainHand(), target, BlockFace.UP);
        Bukkit.getPluginManager().callEvent(event);
        return "ok cancelled=" + event.isCancelled() + " block=" + target.getType();
    }

    /**
     * Puts armour on a player. A chest hands a person the pieces and they put them on; a fake
     * player cannot, and one of the demon's conditions is what it is wearing.
     */
    private String wear(String rest) {
        String[] args = rest.split("\\s+");
        if (args.length != 2) {
            return "err usage: wear <name> <material>";
        }
        Player player = player(args[0]);
        Material material = Material.matchMaterial(args[1]);
        if (material == null) {
            return "err not a material: " + args[1];
        }
        ItemStack stack = new ItemStack(material, 1);
        PlayerInventory inventory = player.getInventory();
        if (material.name().endsWith("_HELMET")) {
            inventory.setHelmet(stack);
        } else if (material.name().endsWith("_CHESTPLATE")) {
            inventory.setChestplate(stack);
        } else if (material.name().endsWith("_LEGGINGS")) {
            inventory.setLeggings(stack);
        } else if (material.name().endsWith("_BOOTS")) {
            inventory.setBoots(stack);
        } else {
            return "err not armour: " + args[1];
        }
        return "ok wearing " + material;
    }

    /**
     * Kills a player with a given damage cause. Half the races only turn on death, and a fake
     * player has no client to walk off a ledge or swim in lava - so the cause is set and the
     * last hit taken, which is the same thing the plugin does to itself when it wants a death.
     */
    private String kill(String rest) {
        String[] args = rest.split("\\s+");
        if (args.length != 2) {
            return "err usage: kill <name> <cause>";
        }
        Player player = player(args[0]);
        DamageCause cause;
        try {
            cause = DamageCause.valueOf(args[1].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return "err not a damage cause: " + args[1];
        }
        player.setLastDamageCause(new EntityDamageEvent(player, cause, 1000));
        player.setHealth(0);
        return "ok killed " + player.getName() + " by " + cause;
    }

    private Collection<String> worldNames() {
        List<String> names = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            names.add(world.getName());
        }
        return names;
    }

    /**
     * A world's clock, and whether the plugin would call it night. The vampire and the
     * werewolf areas are the reason this is worth asking: their switches are only doing their
     * job if this changes for their world and for no other.
     */
    private String time(String rest) {
        World world = Bukkit.getWorld(rest.trim());
        if (world == null) {
            return "err no such world: " + rest;
        }
        long time = world.getTime() % 24000L;
        return "ok world=" + world.getName() + " time=" + time
                + " night=" + (time < 0 || time > 12400);
    }

    /** A world's difficulty, which is per world - and on peaceful, nothing can be hurt. */
    private String difficulty(String rest) {
        World world = Bukkit.getWorld(rest.trim());
        if (world == null) {
            return "err no such world: " + rest;
        }
        return "ok world=" + world.getName() + " difficulty=" + world.getDifficulty();
    }

    /** Everything that is currently hunting a player - the check a truce has to pass. */
    private String targets(String rest) {
        Player player = player(rest.trim());
        List<String> hunters = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            for (LivingEntity entity : world.getLivingEntities()) {
                if (entity instanceof Mob mob && player.equals(mob.getTarget())) {
                    hunters.add(mob.getType().name());
                }
            }
        }
        return "ok " + (hunters.isEmpty() ? "none" : String.join(" ", hunters));
    }

    /**
     * Kills a player the way the plugin reads a death: with a stated cause and, when the cause
     * is another creature, with that creature really standing there as the damager.
     *
     * <p>A clientless player cannot be killed by a mob in any world but the one it joined in.
     * The server makes a player invulnerable while it waits for a client to acknowledge a
     * dimension change, and a fake client never sends that packet - so after being moved into
     * one of the hall's own worlds the player stays untouchable, and a wolf that is plainly
     * hunting them will never land a hit. This raises the same event that hit would have.
     */
    private String slain(String rest) {
        String[] args = rest.split("\\s+");
        if (args.length < 2) {
            return "err usage: slain <name> <cause> [entityType]";
        }
        Player player = player(args[0]);
        DamageCause cause;
        try {
            cause = DamageCause.valueOf(args[1].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return "err not a damage cause: " + args[1];
        }
        EntityDamageEvent event;
        if (args.length > 2) {
            EntityType type;
            try {
                type = EntityType.valueOf(args[2].toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException unknown) {
                return "err not an entity type: " + args[2];
            }
            Entity killer = player.getWorld().spawnEntity(player.getLocation().add(1, 0, 0), type);
            event = new EntityDamageByEntityEvent(killer, player, cause, 1000);
        } else {
            event = new EntityDamageEvent(player, cause, 1000);
        }
        player.setLastDamageCause(event);
        player.setHealth(0);
        return "ok " + player.getName() + " slain by " + cause
                + (args.length > 2 ? " from " + args[2] : "");
    }

    /**
     * Empties a water bucket on a block as a player would. Pouring water is not a block place
     * and does not raise one, which is exactly how a player got water into a shrine room.
     */
    private String bucket(String rest) {
        String[] args = rest.split("\\s+");
        if (args.length != 4) {
            return "err usage: bucket <name> <x> <y> <z>";
        }
        Player player = player(args[0]);
        Block target = blockAt(rest);
        // The full constructor: the short one leaves getBlock() null, which is a real trap and
        // cost a round of "the guard did not fire" when the guard was fine.
        PlayerBucketEmptyEvent event = new PlayerBucketEmptyEvent(player, target, target,
                BlockFace.UP, Material.WATER_BUCKET, new ItemStack(Material.WATER_BUCKET));
        Bukkit.getPluginManager().callEvent(event);
        return "ok cancelled=" + event.isCancelled()
                + " block=" + (event.getBlock() == null ? "null" : event.getBlock().getType())
                + " clicked=" + (event.getBlockClicked() == null
                        ? "null" : event.getBlockClicked().getType())
                + " at=" + target.getX() + "," + target.getY() + "," + target.getZ();
    }

    /**
     * Left-clicks the air as a player would, which is how several races use an item - a vampire
     * jumps and teleports with one, a werewolf calls a wolf and dashes with one, and a bowl is
     * left-clicked to brew the wolfbane. A fake player has no mouse, so a scenario has to say so.
     */
    private String leftClick(String rest) {
        Player player = player(rest.trim());
        PlayerInteractEvent event = new PlayerInteractEvent(player, Action.LEFT_CLICK_AIR,
                player.getInventory().getItemInMainHand(), null, BlockFace.UP);
        Bukkit.getPluginManager().callEvent(event);
        return "ok cancelled=" + event.isCancelled()
                + " held=" + player.getInventory().getItemInMainHand().getType();
    }

    /** The hall's dummies, so a scenario can tell that every hall has one and where it is. */
    private String dummies() {
        if (hall == null) {
            return "err no hall on this server";
        }
        List<String> found = new ArrayList<>();
        for (Map.Entry<String, org.bukkit.entity.ArmorStand> entry : hall.dummies().entrySet()) {
            Location at = entry.getValue().getLocation();
            Location anchor = hall.point("dummy." + entry.getKey());
            found.add(entry.getKey() + "@" + at.getBlockX() + "," + at.getBlockY() + ","
                    + at.getBlockZ() + " " + at.getWorld().getName()
                    + " anchor=" + (anchor == null ? "none"
                            : anchor.getBlockX() + "," + anchor.getBlockY() + "," + anchor.getBlockZ())
                    + " drift=" + (anchor == null ? "?" : String.format(Locale.ROOT, "%.3f",
                            at.distance(anchor))));
        }
        return "ok " + found.size() + " " + String.join(" | ", found);
    }

    /**
     * Every switch inside one race's hall, as {@code x,y,z KIND[ race/detail]} - one per line.
     * Counting blocks from a corner is how a test ends up pressing the wrong button and then
     * blaming the plugin; asking the hall is not.
     */
    private String buttons(String rest) {
        if (hall == null) {
            return "err no hall on this server";
        }
        String race = rest.trim();
        Hall.Box zone = hall.zone("hall." + race);
        if (zone == null && !"lobby".equals(race)) {
            return "err no such hall: " + race;
        }
        List<String> found = new ArrayList<>();
        for (Map.Entry<Location, Hall.Trigger> entry : hall.switches().entrySet()) {
            Location at = entry.getKey();
            if (zone != null ? !zone.holds(at) : insideAnyHall(at)) {
                continue;
            }
            Hall.Trigger trigger = entry.getValue();
            found.add(at.getBlockX() + "," + at.getBlockY() + "," + at.getBlockZ() + " "
                    + trigger.kind()
                    + (trigger.race() == null ? "" : "/" + trigger.race())
                    + (trigger.detail() == null ? "" : "/" + trigger.detail()));
        }
        return "ok " + found.size() + " " + String.join(" | ", found);
    }

    /** Whether a switch sits in one of the race halls rather than out in the lobby. */
    private boolean insideAnyHall(Location at) {
        for (String race : List.of("vampire", "ghoul", "werewolf", "priest", "witchhunter",
                "demon", "enderborn", "angel", "demon_nether")) {
            Hall.Box zone = hall.zone("hall." + race);
            if (zone != null && zone.holds(at)) {
                return true;
            }
        }
        return false;
    }

    /** Whether one world is raining right now, which is a thing the weather command will not say. */
    private String weather(String rest) {
        World world = Bukkit.getWorld(rest.trim());
        if (world == null) {
            return "err no such world: " + rest.trim();
        }
        return "ok " + world.getName() + " storm=" + world.hasStorm()
                + " thunder=" + world.isThundering();
    }

    /** Every living thing the server currently has loaded in one world, counted by type. */
    private String mobs(String rest) {
        World world = Bukkit.getWorld(rest.trim());
        if (world == null) {
            return "err no such world: " + rest.trim();
        }
        java.util.TreeMap<String, Integer> counts = new java.util.TreeMap<>();
        for (LivingEntity entity : world.getLivingEntities()) {
            if (entity instanceof org.bukkit.entity.ArmorStand) {
                continue;
            }
            counts.merge(entity.getType().name(), 1, Integer::sum);
        }
        List<String> parts = new ArrayList<>();
        counts.forEach((type, count) -> parts.add(type + "x" + count));
        return "ok " + parts.size() + " kinds: " + (parts.isEmpty() ? "(none)" : String.join(" ", parts));
    }

    /** The hall's own furniture that is not the way the hall built it. */
    private String repairs() {
        if (hall == null) {
            return "err no hall on this server";
        }
        List<String> wrong = hall.wrongFixtures();
        return "ok " + wrong.size() + " " + (wrong.isEmpty() ? "(all as built)" : String.join(" | ", wrong));
    }

    /** Where a numbered section of a hall starts, as {@code x y z}. */
    private String section(String rest) {        if (hall == null) {
            return "err no hall on this server";
        }
        String[] args = rest.split("\\s+");
        if (args.length != 2) {
            return "err usage: section <race> <index>";
        }
        Location at = hall.sectionAt(args[0], intOf(args[1]));
        if (at == null) {
            return "err no such section: " + rest.trim();
        }
        return "ok " + at.getBlockX() + " " + at.getBlockY() + " " + at.getBlockZ()
                + " " + at.getWorld().getName();
    }

    /**
     * Swings at the nearest dummy the way a player would, so a scenario can see what the hall
     * reports and that the dummy survives it: the damage a real attack deals, the number that
     * appears, and whether the stand moved or lost health.
     */
    private String hitDummy(String rest) {
        if (hall == null) {
            return "err no hall on this server";
        }
        Player player = player(rest.trim());
        org.bukkit.entity.ArmorStand closest = null;
        double best = Double.MAX_VALUE;
        for (org.bukkit.entity.ArmorStand stand : hall.dummies().values()) {
            // Halls live in four worlds, and a distance between two of them is not a distance.
            if (!stand.getWorld().equals(player.getWorld())) {
                continue;
            }
            double away = stand.getLocation().distanceSquared(player.getLocation());
            if (away < best) {
                best = away;
                closest = stand;
            }
        }
        if (closest == null) {
            return "err no dummy to hit";
        }
        Location before = closest.getLocation();
        double health = closest.getHealth();
        ItemStack held = player.getInventory().getItemInMainHand();
        double swing = held.getType() == Material.AIR ? 1.0 : 7.0;
        org.bukkit.event.entity.EntityDamageByEntityEvent event =
                new org.bukkit.event.entity.EntityDamageByEntityEvent(player, closest,
                        org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_ATTACK, swing);
        Bukkit.getPluginManager().callEvent(event);
        int numbers = 0;
        for (Entity nearby : closest.getNearbyEntities(3, 3, 3)) {
            if (nearby instanceof org.bukkit.entity.ArmorStand stand
                    && stand.isMarker() && stand.getCustomName() != null) {
                numbers++;
            }
        }
        return "ok held=" + held.getType() + " cancelled=" + event.isCancelled()
                + " damage=" + event.getDamage() + " moved=" + (before.distance(closest.getLocation()) > 0.01)
                + " healthWas=" + health + " healthNow=" + closest.getHealth()
                + " numbers=" + numbers;
    }

    /**
     * Where a player's saved vampire teleport point is, or that there is none. Worth asking:
     * the point survives a hall, so a section that resets it is only doing its job if this shows
     * the hall rather than wherever the player last set it.
     */
    private String teleportPoint(String rest) {
        SuperNPlayer snplayer = SuperNManager.get(player(rest.trim()));
        var data = SupernaturalsPlugin.instance.getDataHandler();
        if (!data.checkPlayer(snplayer)) {
            return "ok none";
        }
        Location at = data.getTeleport(snplayer);
        return "ok " + at.getBlockX() + "," + at.getBlockY() + "," + at.getBlockZ()
                + " " + at.getWorld().getName();
    }

    /**
     * The biome a hall's columns report at <b>y=0</b>, which is the value the plugin's
     * two-argument {@code getBiome} reads when it decides whether a demon froze to death.
     */
    private String biome(String rest) {
        String[] args = rest.split("\\s+");
        if (args.length == 4 && "set".equals(args[0])) {
            World target = Bukkit.getWorld(args[1]);
            if (target == null) {
                return "err no such world: " + args[1];
            }
            int setX = intOf(args[2]);
            int setZ = intOf(args[3]);
            target.getChunkAt(Math.floorDiv(setX, 16), Math.floorDiv(setZ, 16));
            target.setBiome(setX, 0, setZ, org.bukkit.block.Biome.SNOWY_PLAINS);
            return "ok y0=" + target.getBiome(setX, 0, setZ);
        }
        if (args.length != 3) {
            return "err usage: biome <world> <x> <z> | biome set <world> <x> <z>";
        }
        World world = Bukkit.getWorld(args[0]);
        if (world == null) {
            return "err no such world: " + args[0];
        }
        int x = intOf(args[1]);
        int z = intOf(args[2]);
        return "ok y0=" + world.getBiome(x, 0, z) + " here=" + world.getBiome(x, 200, z);
    }

    /**
     * Makes a player a race through the plugin's own conversion, which is what a section needs
     * when a rule is about what a race can take rather than about how it is reached. The admin
     * command would be the natural way; it cannot be used from a console while the harness has
     * the plugin's verbose logging on, because that path casts the sender to a player.
     */
    private String race(String rest) {
        String[] args = rest.split("\\s+");
        if (args.length != 2) {
            return "err usage: race <name> <race>";
        }
        Player player = player(args[0]);
        SuperNPlayer snplayer = SuperNManager.get(player);
        if ("human".equalsIgnoreCase(args[1])) {
            SuperNManager.cure(snplayer);
        } else {
            SuperNManager.convert(snplayer, args[1].toLowerCase(Locale.ROOT));
        }
        return "ok " + player.getName() + " is now " + snplayer.getType();
    }

    /**
     * Takes everything out of a chest, which is what a player does by hand when a recipe wants
     * twenty different flowers. A scenario cannot click twenty times, and the props are a loan
     * anyway - the hall clears the inventory on the way out.
     */
    private String loot(String rest) {
        String[] args = rest.split("\\s+");
        if (args.length != 5) {
            return "err usage: loot <name> <world> <x> <y> <z>";
        }
        Player player = player(args[0]);
        World world = Bukkit.getWorld(args[1]);
        if (world == null) {
            return "err no such world: " + args[1];
        }
        Block block = world.getBlockAt(intOf(args[2]), intOf(args[3]), intOf(args[4]));
        if (!(block.getState() instanceof Chest chest)) {
            return "err not a chest: " + block.getType();
        }
        int stacks = 0;
        for (ItemStack item : chest.getInventory().getContents()) {
            if (item != null && item.getType() != Material.AIR) {
                player.getInventory().addItem(item.clone());
                stacks++;
            }
        }
        player.updateInventory();
        return "ok took " + stacks + " stacks from " + block.getType();
    }

    /** Puts a material in the player's main hand, which is what several races react to. */
    private String hold(String rest) {
        String[] args = rest.split("\\s+");
        if (args.length != 2) {
            return "err usage: hold <name> <material>";
        }
        Player player = player(args[0]);
        Material material = Material.matchMaterial(args[1]);
        if (material == null) {
            return "err not a material: " + args[1];
        }
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType() != material) {
                continue;
            }
            if (slot < 9) {
                inventory.setHeldItemSlot(slot);
            } else {
                ItemStack inHand = inventory.getItem(inventory.getHeldItemSlot());
                inventory.setItem(slot, inHand);
                inventory.setItem(inventory.getHeldItemSlot(), item);
            }
            player.updateInventory();
            return "ok holding " + material + " (slot " + slot + ")";
        }
        return "err " + player.getName() + " has no " + material;
    }

    /**
     * Kills whatever mob is nearest to a player, as that player. Half the plugin's rules are
     * about what a race gets for a kill, and a fake player cannot swing - so the kill is
     * attributed the same way a real one is, with the player as the damager.
     */
    private String slay(String rest) {
        Player player = player(rest.trim());
        LivingEntity closest = null;
        double best = Double.MAX_VALUE;
        for (Entity nearby : player.getNearbyEntities(8, 8, 8)) {
            if (!(nearby instanceof LivingEntity living) || living instanceof Player) {
                continue;
            }
            double away = living.getLocation().distanceSquared(player.getLocation());
            if (away < best) {
                best = away;
                closest = living;
            }
        }
        if (closest == null) {
            return "err nothing living within eight blocks";
        }
        EntityType type = closest.getType();
        EntityDamageByEntityEvent hit = new EntityDamageByEntityEvent(player, closest,
                EntityDamageEvent.DamageCause.ENTITY_ATTACK, 1000);
        Bukkit.getPluginManager().callEvent(hit);
        // The plugin asks a corpse who killed it, and the answer is recorded when damage is
        // applied - which a hand-made event does not do. Say it out loud before killing it.
        closest.setLastDamageCause(hit);
        closest.setHealth(0);
        return "ok " + player.getName() + " killed the " + type;
    }

    /**
     * Everything about a player and whatever is standing around them, for the times when a
     * mob is plainly hunting somebody and yet the health bar never moves.
     */
    private String diag(String rest) {
        Player player = player(rest.trim());
        StringBuilder out = new StringBuilder("ok health=" + player.getHealth()
                + " invuln=" + player.isInvulnerable()
                + " noDamage=" + player.getNoDamageTicks()
                + " onGround=" + player.isOnGround()
                + " world=" + player.getWorld().getName()
                + " difficulty=" + player.getWorld().getDifficulty()
                + " at=" + player.getLocation().getBlockX() + "," + player.getLocation().getBlockY()
                + "," + player.getLocation().getBlockZ());
        for (Entity entity : player.getNearbyEntities(12, 12, 12)) {
            out.append(" | ").append(entity.getType())
                    .append(" d=").append(String.format(Locale.ROOT, "%.1f",
                            entity.getLocation().distance(player.getLocation())))
                    .append(" dead=").append(entity.isDead())
                    .append(" world=").append(entity.getWorld().getName());
            if (entity instanceof LivingEntity living) {
                out.append(" hp=").append(living.getHealth());
            }
            if (entity instanceof Mob mob) {
                out.append(" aware=").append(mob.isAware())
                        .append(" target=")
                        .append(mob.getTarget() == null ? "none" : mob.getTarget().getName());
            }
        }
        return out.toString();
    }

    /** A plain zombie beside a player, for asking whether a truce actually holds. */
    private String zombie(String rest) {
        String[] args = rest.split("\\s+");
        if (args.length < 1) {
            return "err usage: zombie <name> [distance]";
        }
        Player player = player(args[0]);
        double distance = args.length > 1 ? doubleOf(args[1]) : 2.0;
        Zombie spawned = player.getWorld().spawn(
                player.getLocation().add(distance, 0, 0), Zombie.class);
        spawned.setRemoveWhenFarAway(false);
        return "ok zombie " + spawned.getUniqueId();
    }

    /**
     * Takes the death screen away, which a real client does by itself. Half the races begin
     * with dying, and a fake player has no client to press the button - so a scenario that
     * wants to carry on after a death, or check where the respawn put somebody, has to ask
     * for it.
     */
    private String respawn(String rest) {
        Player player = player(rest.trim());
        player.spigot().respawn();
        Location at = player.getLocation();
        return "ok " + player.getName() + " respawned at " + at.getBlockX() + ","
                + at.getBlockY() + "," + at.getBlockZ() + " world=" + at.getWorld().getName()
                + " race=" + raceOf(rest.trim());
    }

    private static String describe(Inventory inventory) {
        List<String> items = new ArrayList<>();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack != null && stack.getType() != Material.AIR) {
                items.add(slot + "=" + stack.getType() + "x" + stack.getAmount());
            }
        }
        return items.isEmpty() ? "empty" : String.join(" ", items);
    }

    /**
     * Where a joining player is put. A generated world does not put them at the origin, so a
     * scenario that wants to be found has to be built where this says, not where it likes.
     */
    private String worldSpawnOf() {
        Location spawn = Bukkit.getWorlds().get(0).getSpawnLocation();
        return spawn.getBlockX() + " " + spawn.getBlockY() + " " + spawn.getBlockZ();
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
