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
import org.bukkit.block.data.type.Door;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
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
import org.bukkit.potion.PotionEffect;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

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

    /** The stand-ins brought up for a hall section, by handle name, so they can be taken back. */
    private final Map<String, Object> ghosts = new LinkedHashMap<>();
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
        // furniture: whatever manages to move a button is back a moment later, and everything a
        // section let out goes home. Anything that changes a block inside the hall also repairs
        // on the spot; this is the backstop.
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

    /**
     * Registers every altar the hall built as a church.
     *
     * <p>Every one of them, not just one: the ghoul's altar is in the ghoul's own world and the
     * priest's is in the overworld, and a church used to be a single point in the config. A
     * hall whose altar is not a church is a hall where the one thing that undoes a ghoul does
     * nothing at all.
     */
    private void pointTheChurchAtTheHall() {
        if (hall == null || hall.priestAltars().isEmpty()) {
            return;
        }
        SNConfigHandler.clearChurches();
        for (Location altar : hall.priestAltars()) {
            SNConfigHandler.addChurch(altar);
        }
        getLogger().info("priest churches pointed at " + String.join(", ",
                hall.priestAltars().stream().map(altar -> altar.getWorld().getName() + " "
                        + altar.getBlockX() + "," + altar.getBlockY() + "," + altar.getBlockZ())
                        .toList()));
    }

    /** The hall, or null on a server that was not asked for one. */
    Hall hallOrNull() {
        return hall;
    }

    /**
     * Brings a stand-in up in the world it is needed in, and remembers its handle so it can be
     * taken back out. Any older one of the same name goes first - two players cannot share a name.
     */
    Object spawnGhost(String name, World world) {
        return spawnGhost(name, world, null);
    }

    /**
     * The same, in a particular skin.
     *
     * <p>The skin is not decoration here: a stand-in who is playing a race, or a teacher who is
     * playing a job, is read before he is listened to. It has to be handed over at the moment he
     * is built - see {@link Nms#spawn(String, World, Skins.Skin)}.
     */
    Object spawnGhost(String name, World world, Skins.Skin skin) {
        dropGhost(name);
        try {
            Object actor = Nms.spawn(name, world, skin);
            // Out of transit before anybody tries to hit him: a stand-in nobody can hurt makes
            // every section that asks for a hit look broken.
            Nms.arrive(actor);
            ghosts.put(name, actor);
            return actor;
        } catch (RuntimeException alreadyThere) {
            return null;
        }
    }

    /**
     * Takes a stand-in away again, so it is not left in the tab list between tests. One that was
     * never brought up - a section that failed before it could - is simply nothing to do.
     */
    void dropGhost(String name) {
        Object actor = ghosts.remove(name);
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
        for (Map.Entry<String, Object> ghost : new ArrayList<>(ghosts.entrySet())) {
            try {
                Nms.disconnect(ghost.getValue());
            } catch (RuntimeException e) {
                getLogger().warning("leaving " + ghost.getKey() + " behind: " + e);
            }
        }
        ghosts.clear();
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
                return spawn(rest.trim());
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
            case "door":
                return doorAt(rest);
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
            case "signage":
                return signage();
            case "light":
                return light();
            case "heights":
                return heights();
            case "ring":
                return ring(rest);
            case "size":
                return sizeOf(rest);
            case "sun":
                return sun(rest);
            case "equip":
                return equip(rest);
            case "facing":
                return facing(rest);
            case "where":
                return where(rest);
            case "near":
                return near(rest);
            case "fall":
                return fall(rest);
            case "hurt":
                return hurt(rest);
            case "mine":
                return mine(rest);
            case "step":
                return step(rest);
            case "leftclick":
                return leftClick(rest);
            case "strike":
                return strike(rest);
            case "whack":
                return whack(rest);
            case "transit":
                return transit(rest);
            case "execute":
                return execute(rest);
            case "food":
                return "ok " + player(rest.trim()).getFoodLevel();
            case "power":
                return "ok " + (int) SuperNManager.get(player(rest.trim())).getPower();
            case "spar":
            case "dummies":
                return dummies();
            case "buttons":
                return buttons(rest);
            case "section":
                return section(rest);
            case "point":
                return point(rest);
            case "mobs":
                return mobs(rest);
            case "weather":
                return weather(rest);
            case "repairs":
                return repairs();
            case "hitspar":
            case "hitdummy":
                return hitDummy(rest);
            case "tppoint":
                return teleportPoint(rest);
            case "biome":
                return biome(rest);
            case "rain":
                return rain(rest);
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
            case "worn":
                return worn(rest);
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
            case "effects":
                return effects(rest.trim());
            case "stop":
                Bukkit.getScheduler().runTask(this, Bukkit::shutdown);
                return "ok stopping";
            default:
                return "err unknown request: " + verb;
        }
    }

    // -- requests ----------------------------------------------------------

    /**
     * Brings a stand-in player up.
     *
     * <p>Named without a world on purpose. {@code Nms.spawn(name, world)} does take one, and the
     * player still arrives at the default world's spawn: the login places them there whatever
     * level the server player was built against. Since a clientless player is invulnerable in
     * every world but the one it joined, "which world did this stand-in join in" cannot be chosen
     * - so anything that needs one monster to hurt one person has to be measured in the world the
     * stand-in already joined, or through the event the plugin decides on.
     */
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

    /**
     * A block that is supposed to be a door, read back the way anything that opens one has to:
     * which half each of the three cells claims to be, and whether each is open. A fixture whose
     * two blocks are both the bottom half looks like a door from the outside and is not one, and
     * a click on the upper block then moves only that one.
     */
    private String doorAt(String rest) {
        Block block = blockAt(rest);
        StringBuilder out = new StringBuilder();
        for (int offset = -1; offset <= 1; offset++) {
            Block at = block.getRelative(0, offset, 0);
            String detail = " half=- hinge=- open=-";
            if (at.getBlockData() instanceof Door door) {
                detail = " half=" + door.getHalf()
                        + " hinge=" + door.getHinge()
                        + " open=" + door.isOpen();
            }
            out.append(' ').append(at.getY()).append(':').append(at.getType()).append(detail).append(';');
        }
        return "ok" + out;
    }

    /**
     * Whether every section's teaching fitted on the wall.
     *
     * <p>A board that did not fit is dropped and a board two sections both wanted is written
     * twice; both leave a person reading a wall that is missing something, and neither shows up
     * by looking at one sign at a time. Zero and zero means the halls have room to say what they
     * say.
     */
    /**
     * A light reading for everything a player reads or presses, plus the middle of every room.
     *
     * <p>Counted rather than eyeballed: "it is a bit dark in here" is not something anybody can
     * act on, and the difference between a board at 9 and a board at 10 is the difference between
     * readable and not.
     */
    private String light() {
        if (hall == null) {
            return "err no hall on this server";
        }
        List<String> report = hall.lightReport();
        return "ok " + String.join("\n  ", report);
    }

    /** How tall each room actually was built, for checking against the plan's size table. */
    private String heights() {
        if (hall == null) {
            return "err no hall on this server";
        }
        List<String> report = hall.heightReport();
        return "ok rooms=" + report.size() + "\n  " + String.join("\n  ", report);
    }

    /**
     * Replays the ring the hall's monsters are laid out on, without summoning anything.
     *
     * <p>The layout grows a pair of blocks of radius every six monsters, so the question "does
     * the room hold all of them" has a number for an answer: the outermost ring plus half the
     * widest monster on it. Printing where each one would stand turns "one of them did not
     * appear" into a reading - and a monster that will not fit is otherwise only visible as a
     * count that came up short.
     */
    private String ring(String race) {
        String which = race == null || race.isBlank() ? "vampire" : race.trim();
        List<?> wanted = "ghoul".equals(which) ? SNConfigHandler.ghoulTruce
                : SNConfigHandler.vampireTruce;
        StringBuilder out = new StringBuilder("ok race=" + which + " count=" + wanted.size());
        double farthest = 0;
        for (int placed = 0; placed < wanted.size(); placed++) {
            double radius = 3 + (placed / 6) * 2;
            double angle = placed * 1.1;
            farthest = Math.max(farthest, radius);
            out.append("\n  ").append(placed).append(" ").append(wanted.get(placed))
                    .append(" r=").append((int) radius)
                    .append(" at ").append(Math.round(Math.cos(angle) * radius))
                    .append(",").append(Math.round(Math.sin(angle) * radius));
        }
        out.append("\n  farthest ring=").append((int) farthest)
                .append(" so the floor needs a radius of at least ").append((int) farthest + 1);
        return out.toString();
    }

    /**
     * Measures one or more entity types, by putting one down and reading it back.
     *
     * <p>Half the hall's numbers are about things fitting, and the sizes behind those numbers are
     * remembered rather than known. An entity can be asked how big it is without being left
     * standing in the building.
     */
    private String sizeOf(String rest) {
        if (rest == null || rest.isBlank()) {
            return "err usage: size <ENTITY>...";
        }
        World target = Bukkit.getWorlds().get(0);
        StringBuilder out = new StringBuilder("ok world=" + target.getName());
        for (String one : rest.trim().split("\\s+")) {
            EntityType type = EntityType.fromName(one);
            if (type == null) {
                out.append("\n  ").append(one).append(" unknown");
                continue;
            }
            Location at = target.getSpawnLocation().clone().add(0, 60, 0);
            try {
                Entity spawned = target.spawnEntity(at, type);
                out.append("\n  ").append(type).append(" width=")
                        .append(String.format("%.2f", spawned.getWidth()))
                        .append(" height=").append(String.format("%.2f", spawned.getHeight()));
                spawned.remove();
            } catch (RuntimeException | LinkageError notPlaceable) {
                out.append("\n  ").append(type).append(" cannot be spawned: ")
                        .append(notPlaceable.getMessage());
            }
        }
        return out.toString();
    }

    /**
     * What the plugin itself thinks about the sun over a player's head.
     *
     * <p>The answer is not the obvious one. The plugin reads the sky light over the player's eyes,
     * which is a different question from "is there a block above me" - so whether glass counts as
     * shelter and whether leaves do is a thing to measure, not to reason about. This asks the
     * plugin's own method, so the reading is the one the rule will use.
     */
    private String sun(String rest) {
        Player player = player(rest);
        if (player == null) {
            return "err no such player";
        }
        SuperNPlayer snplayer = SuperNManager.get(player);
        int eyeSky = player.getEyeLocation().getBlock().getLightFromSky();
        Material overhead = player.getEyeLocation().getBlock().getType();
        boolean sunlit = SupernaturalsPlugin.instance.getVampireManager().standsInSunlight(player);
        return "ok sunlit=" + sunlit + " eyeSky=" + eyeSky + " eyeBlock=" + overhead
                + " blockAbove=" + player.getLocation().add(0, 3, 0).getBlock().getType()
                + " night=" + SuperNManager.worldTimeIsNight(player)
                + " storm=" + player.getWorld().hasStorm()
                + " race=" + (snplayer == null ? "?" : snplayer.getType());
    }

    /**
     * What a stand-in is wearing and holding, and whether any of it is something his race may not
     * have.
     *
     * <p>A race that cannot use a weapon deals nothing with one, and a piece of armour a race may
     * not wear is taken off and dropped by the plugin the moment it is worn. Both turn a room into
     * one that quietly does not work, so both are checked against the plugin's own lists rather
     * than trusted.
     */
    private String equip(String rest) {
        Player player = player(rest);
        if (player == null) {
            return "err no such player";
        }
        SuperNPlayer snplayer = SuperNManager.get(player);
        String race = snplayer == null ? "?" : snplayer.getType();
        PlayerInventory inv = player.getInventory();
        StringBuilder out = new StringBuilder("ok " + player.getName() + " race=" + race);
        out.append("\n  hand=").append(inv.getItemInMainHand().getType())
                .append(legalWeapon(race, inv.getItemInMainHand().getType()));
        out.append("\n  helmet=").append(worn(race, inv.getHelmet()));
        out.append("\n  chest=").append(worn(race, inv.getChestplate()));
        out.append("\n  legs=").append(worn(race, inv.getLeggings()));
        out.append("\n  boots=").append(worn(race, inv.getBoots()));
        return out.toString();
    }

    private String legalWeapon(String race, Material item) {
        if (item == null || item.isAir()) {
            return " (empty-handed)";
        }
        return HallGuard.bannedWeapons(race).contains(item)
                ? " ILLEGAL for " + race + " - it deals no damage"
                : " legal";
    }

    private String worn(String race, ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return "none";
        }
        List<Material> allowed = HallGuard.allowedArmor(race);
        if (allowed == null) {
            return stack.getType() + " (race wears anything)";
        }
        return allowed.contains(stack.getType())
                ? stack.getType() + " legal"
                : stack.getType() + " ILLEGAL for " + race + " - the plugin will take it off";
    }

    private String signage() {
        if (hall == null) {
            return "err no hall on this server";
        }
        StringBuilder out = new StringBuilder("ok dropped=" + hall.boardsDropped()
                + " collisions=" + hall.signCollisions()
                + " rooms=" + hall.roomTotal()
                + " missingExit=" + hall.exitsMissing().size()
                + " missingScene=" + hall.scenesMissing().size()
                + " unsupported=" + hall.unsupportedSwitches().size()
                + " outOfReach=" + hall.outOfReach().size());
        if (hall.boardsDropped() > 0) {
            out.append("\n  dropped where: ")
                    .append(String.join(" | ", hall.droppedBoardReport()));
        }
        if (!hall.unsupportedSwitches().isEmpty()) {
            out.append("\n  hanging on nothing: ")
                    .append(String.join(" | ", hall.unsupportedSwitches()));
        }
        if (!hall.outOfReach().isEmpty()) {
            out.append("\n  too high to use: ").append(String.join(" | ", hall.outOfReach()));
        }
        if (!hall.exitsMissing().isEmpty()) {
            out.append("\n  no way out of: ").append(String.join(", ", hall.exitsMissing()));
        }
        if (!hall.scenesMissing().isEmpty()) {
            out.append("\n  owes a scene board: ").append(String.join(", ", hall.scenesMissing()));
        }
        return out.toString();
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

    /**
     * What is standing near a player, where, and how much health it has left.
     *
     * <p>A world's mob counts say nothing about the one a section just conjured - a flat world
     * spawns its own - and "did it suffocate" is a question about one entity's health, so the
     * answer has to name the entity.
     */
    /**
     * Runs one damage event of a named kind through the plugin, and reports what it decided.
     *
     * <p>The server hands out a great deal of damage on its own - lava, fire, drowning, cactus -
     * and it only does so to a player it believes is being moved by a client. A stand-in is not,
     * so half the passive rules about those damage kinds cannot be reached by arranging the world
     * and waiting. The events can be raised directly, which is what this does and what {@link
     * #fall} does for the one kind with arithmetic worth copying.
     */
    private String hurt(String rest) {
        String[] args = rest.trim().split("\\s+");
        if (args.length < 2) {
            return "err usage: hurt <name> <cause> [amount]";
        }
        Player player = player(args[0]);
        DamageCause cause;
        try {
            cause = DamageCause.valueOf(args[1].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return "err not a damage cause: " + args[1];
        }
        double amount = args.length > 2 ? Double.parseDouble(args[2]) : 1;
        double before = player.getHealth();
        double powerBefore = SuperNManager.get(player).getPower();
        EntityDamageEvent event = new EntityDamageEvent(player, cause, amount);
        Bukkit.getPluginManager().callEvent(event);
        if (!event.isCancelled() && event.getDamage() > 0) {
            player.setHealth(Math.max(0, before - event.getDamage()));
        }
        return "ok cause=" + cause + " raw=" + amount + " cancelled=" + event.isCancelled()
                + " damage=" + event.getDamage() + " health=" + before + "->" + player.getHealth()
                + " power=" + (int) powerBefore + "->" + (int) SuperNManager.get(player).getPower()
                + " race=" + SuperNManager.get(player).getType();
    }

    /**
     * Runs a fall of a given height through the plugin's own path, and reports what it decided.
     *
     * <p>A player with no client does not fall: the server moves players from the client's input,
     * so gravity never runs for a stand-in and a section about fall damage cannot be driven by
     * teleporting one into the air and waiting. What the plugin actually answers is the damage
     * event, and that can be raised directly - which is what this does, with the vanilla
     * arithmetic (one point a block past the third) for the height asked for.
     */
    private String fall(String rest) {
        String[] args = rest.trim().split("\\s+");
        if (args.length < 2) {
            return "err usage: fall <name> <blocks>";
        }
        Player player = player(args[0]);
        int blocks = Integer.parseInt(args[1].trim());
        double damage = Math.max(0, blocks - 3);
        EntityDamageEvent event = new EntityDamageEvent(player, DamageCause.FALL, damage);
        Bukkit.getPluginManager().callEvent(event);
        return "ok blocks=" + blocks + " raw=" + damage + " cancelled=" + event.isCancelled()
                + " damage=" + event.getDamage() + " race=" + SuperNManager.get(player).getType();
    }

    private String near(String rest) {
        String[] args = rest.trim().split("\\s+");
        Player player = player(args[0]);
        double radius = args.length > 1 ? Double.parseDouble(args[1]) : 8;
        List<String> found = new ArrayList<>();
        for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (!(entity instanceof LivingEntity living)
                    || living.getUniqueId().equals(player.getUniqueId())) {
                continue;
            }
            // A stand-in is a player, and for one of those the interesting part is not its name but
            // what race it was given and whether a player could tell: the race is the plugin's own
            // record, the label is what the scoreboard writes next to the name.
            String who;
            if (living instanceof Player standIn) {
                who = "player:" + standIn.getName()
                        + " race=" + SuperNManager.get(standIn).getType()
                        + " label=" + labelOf(standIn.getName());
            } else {
                who = living.getType().toString();
            }
            found.add(who + "@" + String.format(Locale.ROOT, "%.1f,%.1f,%.1f",
                    living.getLocation().getX(), living.getLocation().getY(),
                    living.getLocation().getZ()) + " hp=" + living.getHealth());
        }
        return "ok " + found.size() + ": " + String.join(" | ", found);
    }

    /** What the scoreboard currently shows beside a name, if anything. */
    private static String labelOf(String name) {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        for (Team team : board.getTeams()) {
            if (team.hasEntry(name)) {
                return "'" + team.getPrefix() + "'";
            }
        }
        return "none";
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
     * What one player is holding, and what the players watching him were told about it.
     *
     * <p>Those two are not the same thing for a stand-in, and the gap is invisible from a
     * console: the item on his inventory is what the plugin reads and what a blow is worth, and
     * the announcement is what a client draws. A step that hands him a sword can therefore be
     * right about the rule and wrong about the picture at the same time, which is the shape of
     * every "he isn't holding it" report. This makes the announcement and prints both sides, so
     * the two can be compared without a client.
     */
    private String worn(String rest) {
        Player player = player(rest.trim());
        return "ok " + player.getName() + " " + Nms.pushEquipment(player);
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

    /**
     * Everything a section could have left on a player: their health, their hunger, whether they
     * are on fire, and every effect still running. Leaving a test is supposed to clear all of it,
     * and none of it can be read back off a fake player any other way.
     */
    private String effects(String name) {
        Player player = player(name);
        List<String> running = new ArrayList<>();
        for (PotionEffect effect : player.getActivePotionEffects()) {
            running.add(effect.getType().getKey().getKey() + "x" + effect.getAmplifier()
                    + "(" + effect.getDuration() + ")");
        }
        return "ok health=" + player.getHealth() + " food=" + player.getFoodLevel()
                + " saturation=" + player.getSaturation() + " fire=" + player.getFireTicks()
                + " effects=" + (running.isEmpty() ? "none" : String.join(" ", running));
    }

    /** A world's difficulty, which is per world - and on peaceful, nothing can be hurt. */    private String difficulty(String rest) {
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
     * Left-clicks as a player would, which is how several races use an item - a vampire jumps and
     * teleports with one, a werewolf calls a wolf and dashes with one, and an angel leaps with a
     * dandelion. A fake player has no mouse, so a scenario has to say so. Given coordinates the
     * click lands on that block instead of the air, which is the half of the trick that only
     * fires when a player is looking at something.
     */
    private String leftClick(String rest) {
        String[] args = rest.split("\\s+");
        Player player = player(args[0]);
        if (args.length >= 4) {
            Block target = blockAt(rest);
            aimAt(player, target);
            PlayerInteractEvent event = new PlayerInteractEvent(player, Action.LEFT_CLICK_BLOCK,
                    player.getInventory().getItemInMainHand(), target, BlockFace.UP);
            Bukkit.getPluginManager().callEvent(event);
            return "ok cancelled=" + event.isCancelled() + " block=" + target.getType()
                    + " held=" + player.getInventory().getItemInMainHand().getType();
        }
        PlayerInteractEvent event = new PlayerInteractEvent(player, Action.LEFT_CLICK_AIR,
                player.getInventory().getItemInMainHand(), null, BlockFace.UP);
        Bukkit.getPluginManager().callEvent(event);
        return "ok cancelled=" + event.isCancelled()
                + " held=" + player.getInventory().getItemInMainHand().getType();
    }

    /**
     * Kills one player as another, the way a real fight would end.
     *
     * <p>A fake player cannot swing, and the prey a witch hunter section puts out are players
     * rather than monsters - {@code slay} skips anything player-shaped by design. The last damage
     * cause is set explicitly because that is what the plugin reads to decide who killed whom.
     */
    private String execute(String rest) {
        String[] args = rest.split("\\s+");
        if (args.length != 2) {
            return "err usage: execute <killer> <victim>";
        }
        Player killer = player(args[0]);
        Player victim = player(args[1]);
        EntityDamageByEntityEvent hit = new EntityDamageByEntityEvent(killer, victim,
                DamageCause.ENTITY_ATTACK, 1000);
        Bukkit.getPluginManager().callEvent(hit);
        victim.setLastDamageCause(hit);
        victim.setHealth(0);
        return "ok " + killer.getName() + " killed " + victim.getName()
                + " (" + SuperNManager.get(victim).getType() + ")";
    }

    /**
     * Has one player attack another with whatever is in hand.
     *
     * <p>Every one of the plugin's spells fires on a player hitting a player - that is how a
     * priest exorcises and how an angel purifies - and a fake player cannot swing, so the hit is
     * raised here the same way the server would raise it.
     */
    private String strike(String rest) {
        String[] args = rest.split("\\s+");
        if (args.length != 2) {
            return "err usage: strike <attacker> <victim>";
        }
        Player attacker = player(args[0]);
        Player victim = player(args[1]);
        EntityDamageByEntityEvent event = new EntityDamageByEntityEvent(attacker, victim,
                DamageCause.ENTITY_ATTACK, 1.0);
        Bukkit.getPluginManager().callEvent(event);
        return "ok cancelled=" + event.isCancelled() + " victim=" + victim.getName()
                + " race=" + SuperNManager.get(victim).getType()
                + " held=" + attacker.getInventory().getItemInMainHand().getType();
    }

    /** What is holding a stand-in invulnerable, for working out which field has to be cleared. */
    private String transit(String rest) {
        Player player = player(rest.trim());
        String before = Nms.transit(player);
        Nms.arrive(player);
        return "ok " + before + " || after arrive: isInvulnerable=" + player.isInvulnerable()
                + " fields=" + Nms.transit(player);
    }

    /**
     * Attacks with the server's own attack path - the one a real left-click ends up in. Raising
     * an event by hand is not the same thing: the plugin reads it, so a rule that only reads
     * damage looks satisfied, but nothing is hit and nobody loses health.
     *
     * <p>Whether a stand-in can be hit at all is the question this answers, and it is the one
     * every step that asks a player to use something on somebody depends on.
     */
    private String whack(String rest) {
        String[] args = rest.trim().split("\\s+");
        if (args.length != 2) {
            return "err usage: whack <attacker> <victim>";
        }
        Player attacker = player(args[0]);
        Player victim = player(args[1]);
        if (attacker.getWorld() != victim.getWorld()) {
            return "err different worlds";
        }
        double before = victim.getHealth();
        boolean invulnerable = victim.isInvulnerable();
        int noDamageTicks = victim.getNoDamageTicks();
        float cooldown = attacker.getAttackCooldown();
        attacker.attack(victim);
        double afterHit = victim.getHealth();
        // Then the same blow applied directly, so "the attack did nothing" can be told apart
        // from "this stand-in cannot be hurt at all" - and, when it is hurt, from "the flag the
        // server leaves on a player it has never heard from is the thing in the way".
        victim.setInvulnerable(false);
        Nms.arrive(victim);
        victim.damage(2.0, attacker);
        double afterDirect = victim.getHealth();
        return "ok held=" + attacker.getInventory().getItemInMainHand().getType()
                + " invulnerable=" + invulnerable + " noDamageTicks=" + noDamageTicks
                + " cooldown=" + String.format(Locale.ROOT, "%.2f", cooldown)
                + " attack=" + before + "->" + afterHit
                + " direct=" + afterHit + "->" + afterDirect
                + " nowInvulnerable=" + victim.isInvulnerable()
                + " race=" + SuperNManager.get(victim).getType();
    }

    /** The hall's stand-in and the marks every damage section asks him to stand on. */
    private String dummies() {
        if (hall == null) {
            return "err no hall on this server";
        }
        Player spar = Bukkit.getPlayerExact("Sparring");
        String where = spar == null ? "not up"
                : spar.getWorld().getName() + "@" + spar.getLocation().getBlockX() + ","
                        + spar.getLocation().getBlockY() + "," + spar.getLocation().getBlockZ()
                        + " hp=" + spar.getHealth() + " race=" + SuperNManager.get(spar).getType();
        return "ok spar=" + where + " marks=" + hall.sparMarks().size() + hall.sparReport();
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
    /**
     * A named place a section put down, by key - the same map the hall's own switches are wired to.
     * Asking for one is how a scenario checks where a section actually put something, rather than
     * working it out from the section's origin and the arithmetic that was supposed to happen.
     */
    private String point(String rest) {
        if (hall == null) {
            return "err no hall on this server";
        }
        Location at = hall.point(rest.trim());
        if (at == null) {
            return "err no such point: " + rest.trim();
        }
        return "ok " + at.getBlockX() + " " + at.getBlockY() + " " + at.getBlockZ()
                + " " + at.getWorld().getName();
    }

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
     * Swings at the stand-in the way a player would, so a scenario can see what the hall reports
     * and that he loses health: the damage a real attack deals, the number that appears, and
     * whether the stand-in went down and was refreshed.
     */
    private String hitDummy(String rest) {
        if (hall == null) {
            return "err no hall on this server";
        }
        Player player = player(rest.trim());
        Player spar = Bukkit.getPlayerExact("Sparring");
        if (spar == null || !spar.getWorld().equals(player.getWorld())) {
            return "err the stand-in is not up in this world";
        }
        double before = spar.getHealth();
        ItemStack held = player.getInventory().getItemInMainHand();
        double swing = held.getType() == Material.AIR ? 1.0 : 7.0;
        // The real path: the server runs the hit and the plugin decides what it is worth, exactly
        // as it would for a client that swung. Raising the event and moving the health bar by hand
        // said nothing about whether the stand-in can be hurt at all.
        spar.damage(swing, player);
        return "ok held=" + held.getType() + " damage=" + swing + " healthWas=" + before
                + " healthNow=" + spar.getHealth() + " race=" + SuperNManager.get(spar).getType();
    }

    /**
     * Where a player's saved vampire teleport point is, or that there is none. Worth asking:
     * the point survives a hall, so a section that resets it is only doing its job if this shows
     * the hall rather than wherever the player last set it.
     */
    private String teleportPoint(String rest) {
        SuperNPlayer snplayer = SuperNManager.get(player(rest.trim()));
        var data = SupernaturalsPlugin.instance.getDataHandler();
        // The saved point, not the question of whether one was saved: a point whose world is not
        // loaded has been saved and cannot be read back, and this asks for the reading.
        Location at = data.getTeleport(snplayer);
        if (at == null) {
            return data.checkPlayer(snplayer) ? "ok set-in-an-unloaded-world" : "ok none";
        }
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
     * What the weather over one column would actually look like.
     *
     * <p>Three things have to agree before a tester sees rain: the world's storm flag, an unbroken
     * column, and a biome that precipitates. A section can only set the first, and the client draws
     * nothing in a biome whose precipitation is none - so a rain switch in the wrong biome is a
     * switch whose only visible effect is on rules nobody can see it causing. This prints the flag
     * and the biome's own answer side by side.
     */
    private String rain(String rest) {
        String[] args = rest.trim().split("\\s+");
        if (args.length != 3) {
            return "err usage: rain <world> <x> <z>";
        }
        World world = Bukkit.getWorld(args[0]);
        if (world == null) {
            return "err no such world: " + args[0];
        }
        return "ok storm=" + world.hasStorm()
                + " " + Nms.precipitation(world, intOf(args[1]), intOf(args[2]));
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
        return "ok " + player.getName() + " killed the " + type
                + " cancelled=" + hit.isCancelled() + " damage=" + hit.getDamage();
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
