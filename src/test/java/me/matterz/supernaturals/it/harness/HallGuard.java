package me.matterz.supernaturals.it.harness;

import me.matterz.supernaturals.SuperNPlayer;
import me.matterz.supernaturals.SupernaturalsPlugin;
import me.matterz.supernaturals.io.SNConfigHandler;
import me.matterz.supernaturals.manager.HunterManager;
import me.matterz.supernaturals.manager.SuperNManager;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Flying;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.PigZombie;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Vector;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The behaviour of the {@link Hall}: what its switches do, what its stand-in answers, and the rules
 * that keep a test a test - the props cannot be walked off with, the halls cannot be mined, built
 * in, poured on or blown up, and nothing hostile gets to interrupt whoever is trying a race.
 *
 * <p><b>The stand-in is the point of a damage section.</b> He is a player with no client behind
 * him: a real health bar, a real race, really able to be hit. Hitting him prints the damage on the
 * spot, after every modifier the plugin applies, because the number a player wants to see is the
 * number they would really deal - and he is put back to full rather than killed, so a section
 * always has somebody to hit. An armour stand stood here before him and could do none of it.
 *
 * <p><b>Anything a section lets out is the hall's responsibility.</b> Every mob a switch calls up
 * is remembered, sent back to its own section if it wanders, stopped from teleporting away, and
 * taken off the board when the player leaves, dies or asks - a section that calls out the
 * monsters a race is at peace with is only useful if they are all still there for the count.
 *
 * <p><b>The hall's own furniture is protected twice over.</b> A block inside the hall cannot be
 * broken, placed, burnt, blown up, spread into, changed by an entity, or poured on - a bucket is
 * not a block place and raises no {@link BlockPlaceEvent}, which is how a player once emptied the
 * vampire's cure bucket onto a shrine floor, washed the switches off their pillars and was left
 * with no way out. And because the switches *are* the way out, anything that changes a block
 * inside the hall also makes the hall put its fixtures back, on the spot.
 */
final class HallGuard implements Listener {

    /** The name of the stand-in a section uses when a test needs somebody to be hit by. */
    private static final String SPAR = "Sparring";

    /** How far off the stand-in starts walking in from, at most. */
    private static final int WALK_FROM = 6;

    /** How often he takes a step, in ticks, and how far each one carries him. */
    private static final long WALK_PERIOD = 2L;
    private static final double WALK_STEP = 0.4;

    /** Marks everything a section let out, so a stray one can be recognised. */
    private static final String HALL_MOB = "mmhall";

    /**
     * Monsters that are not called up at all.
     *
     * <p>This used to hold the ender dragon, for the same reason {@link Hall} left it out of its
     * own list: a boss with a multi-part body is not a mob that can be asked whether it respects
     * a truce. The truce list does name it, though, and the section is big enough to hold one, so
     * it is summoned like everything else and the set is left empty rather than removed.
     */
    private static final Set<String> UNROOMABLE = Set.of();

    /** How many monsters go on each ring of the truce room, before the next ring out is used. */
    private static final int PER_RING = 10;

    /** How much a sword does before the plugin has its say. */
    private static final Map<Material, Double> BLADES = Map.of(
            Material.WOODEN_SWORD, 4.0,
            Material.STONE_SWORD, 5.0,
            Material.IRON_SWORD, 6.0,
            Material.DIAMOND_SWORD, 7.0,
            Material.NETHERITE_SWORD, 8.0);

    private final HarnessPlugin plugin;

    /** Everything the hall let out, with the place it belongs, so it can be put back. */
    private final Map<UUID, Location> hallMobs = new HashMap<>();

    /**
     * The same mobs by reference. {@code Bukkit.getEntity} cannot see one whose chunk the
     * server has unloaded, and a monster nobody can look up is a monster nobody can take back.
     */
    private final Map<UUID, Entity> hallEntities = new HashMap<>();

    /** The one chicken a chicken section keeps, which lives until it is killed. */
    private final Set<UUID> chickens = new HashSet<>();

    /**
     * The room each thing the hall let out was let out in, so a room nobody is standing in can be
     * swept the moment it empties.
     *
     * <p>Recorded as the room it was summoned in, not as wherever the entity happens to be
     * standing now. A monster wanders, and one that has drifted into the next room would be
     * reclaimed by whoever is in <em>that</em> room - which is neither the room whose visitor
     * asked for it nor the room whose step is over. A null means it was let out somewhere that is
     * not a room at all, and such a thing is left to the switches.
     */
    private final Map<UUID, String> propRooms = new HashMap<>();

    /**
     * The same, for the hall's stand-in players: the test prey and the sparring partner.
     *
     * <p>A stand-in is a player as far as the server is concerned, so he is a resident of whatever
     * room he is standing in - and a room with a stand-in in it would never read as empty. He is
     * therefore kept out of the count of who is in a room, and swept by his own entry here.
     */
    private final Map<String, String> standInRooms = new HashMap<>();

    /** The floating numbers, so a sweep does not mistake them for something that escaped. */
    private final Set<UUID> marks = new HashSet<>();

    /** The names the hall's test prey answer to, in the order a section asks for them. */
    private static final List<String> PREY_NAMES = List.of("HallPrey1", "HallPrey2", "HallPrey3");

    /**
     * What each race is called on screen.
     *
     * <p>A stand-in's account name is fixed - it has to be, the account is what makes him a player -
     * and a section that puts three different races in front of somebody is useless if all three
     * answer to "HallPrey2". Every stand-in therefore joins a scoreboard team named for his race,
     * and the team's prefix is what shows above his head and beside his name in the tab list. That
     * is the one place a server may write next to a player's name, so it is the one used.
     */
    private static final Map<String, String> RACE_LABEL = Map.of(
            "vampire", "§c吸血鬼",
            "ghoul", "§2食尸鬼",
            "werewolf", "§6狼人",
            "demon", "§4恶魔",
            "priest", "§f牧师",
            "witchhunter", "§7猎巫人",
            "enderborn", "§5末影族",
            "angel", "§e天使",
            "human", "§a人类");

    /**
     * The stand-ins the hall has brought up as prey, by name, so they can be taken back out.
     *
     * <p>In the order they were put out: a section that sends one at another means the one that
     * was put out first, and a hash map's order is nobody's order in particular.
     */
    private final Map<String, Object> prey = new LinkedHashMap<>();

    /**
     * Prey that gets up again where it fell, by name.
     *
     * <p>Several races turn the person they kill into one of their own, and a target that stays
     * down can only be killed once - so the one section that is about a kill gives the stand-in
     * a mark to come back to.
     */
    private final Map<String, Location> revives = new HashMap<>();

    /**
     * What each stand-in was when he was put out, and who put him out.
     *
     * <p>A section about "kill him until he turns" has exactly one thing to look at: the race the
     * stand-in is wearing. His label is written once, when he is put out, so a race that changes
     * underneath it is a race nobody can see - and a roll that comes up looks identical to a roll
     * that never happens. Keeping what he started as means the change can be recognised, and
     * keeping who asked for him means the person who is waiting can be told.
     */
    private final Map<String, String> preyRace = new HashMap<>();
    private final Map<String, String> preyOwner = new HashMap<>();

    /** The seats taken during the current spawn, so two stand-ins never share one place. */
    private final List<Location> taken = new ArrayList<>();

    /** The races a stand-in can be when a section asks for "somebody supernatural". */
    private static final List<String> SUPERNATURAL = List.of(
            "vampire", "ghoul", "werewolf", "demon", "priest", "witchhunter", "enderborn", "angel");

    /**
     * The plugin's own conversion chance, while a room is holding it open at a hundred percent.
     *
     * <p>{@code null} means nothing is holding it: the number in the setting is the number the
     * plugin was configured with, and no room is responsible for putting anything back.
     */
    private Double spreadWas;

    /** The walk the stand-in is currently on, so a second section cannot start another. */
    private BukkitTask walkTask;

    /**
     * How long a player is left alone after a room kills them into a race.
     *
     * <p>Three seconds. Long enough to read the line in chat that says what they are now and to see
     * the room they are standing in, short enough that it is not a pause. Without it a player who
     * comes back on their own mark with a stand-in still swinging at them is killed again before
     * they can move, which is not a test of anything.
     */
    private static final int GRACE_TICKS = 60;

    /**
     * How long a player has to wait before a guide will tell him the same room again.
     *
     * <p>Half a second. One right-click is one answer, but asking twice on purpose - because the
     * first answer scrolled past - has to stay possible, so this is short and it is per player
     * rather than per guide.
     */
    private static final int TALK_GAP = 10;

    /** When each player was last told a room's teaching, so one click is one answer. */
    private final Map<String, Integer> toldAt = new HashMap<>();

    /**
     * Where a player fell, and what killed them, from the death to the moment they get up.
     *
     * <p>Both keys are names, and both are cleared the moment they are used. The place is a snapshot
     * rather than a live location because a location object follows whoever it belongs to, and by
     * the time this is read the player has been through a respawn.
     */
    private final Map<String, Location> fellAt = new HashMap<>();
    private final Map<String, String> killedBy = new HashMap<>();

    /**
     * Players inside their grace window, and the tick their protection runs out on.
     *
     * <p>Two things enforce this, deliberately. An invulnerability flag is what says it to the
     * engine, but {@link #land} clears that same flag on whoever it is about to hit - it has to, or
     * nothing in this building could be hit at all - so a stand-in that was still there could take
     * the protection off. The flag is the English; the cancelled damage event is the guarantee.
     */
    private final Map<String, Integer> protectedUntil = new HashMap<>();

    /** Ticks since the guard came up, for the grace window to be measured against. */
    private int ticks;

    HallGuard(HarnessPlugin plugin) {
        this.plugin = plugin;
        // A player the server has never heard from is left invulnerable: it is waiting for a
        // client to confirm it arrived, and a stand-in has no client to confirm anything. That
        // single flag is why every step where somebody is supposed to be hit did nothing in any
        // world but the one the server logs into - a hall's stand-in could be seen, walked up to
        // and swung at, and never lost a point of health. It is cleared every tick rather than
        // once, because whatever sets it can set it again.
        Bukkit.getScheduler().runTaskTimer(plugin, this::unfreezeStandIns, 0L, 1L);
    }

    /**
     * Lands one real blow from a player onto a stand-in.
     *
     * <p>A stand-in cannot normally be hit on this server build: it is a player whose client never
     * confirms anything, and the server keeps a flag on such a player that refuses damage. The flag
     * that matters is on the <em>victim</em>, which is why this clears three things on him and
     * nothing on the attacker. Two earlier readings of this were wrong and are worth not repeating:
     * the attacker's creative mode was said to be the exception that let a blow through, and it is
     * not - a hit from a creative attacker measured 20.0 to 20.0 - and the flag was said to come
     * back on its own, which it does not; what comes back is the arrival flag, and that is what
     * {@link #unfreezeStandIns} clears every tick.
     *
     * <p>Once the victim's three are cleared the blow travels the ordinary route: the plugin's
     * handlers, the health bar, the armour, the knockback.
     */
    private static void land(Player attacker, Player victim, double damage) {
        // What is in the way is on the victim, not on the attacker - measured both ways on the
        // console: a hit from a creative attacker went 20.0 -> 20.0, and the same hit after clearing
        // the victim's flag went 20.0 -> 17.0.
        Nms.arrive(victim);
        victim.setInvulnerable(false);
        victim.setNoDamageTicks(0);
        victim.damage(damage, attacker);
    }

    /** Lifts the invulnerability the server puts on a player it has never heard from. */
    private void unfreezeStandIns() {
        ticks++;
        for (Player npc : standIns()) {
            if (npc.isInvulnerable()) {
                npc.setInvulnerable(false);
                Nms.arrive(npc);
            }
        }
        for (Iterator<Map.Entry<String, Integer>> ends = protectedUntil.entrySet().iterator();
                ends.hasNext();) {
            Map.Entry<String, Integer> entry = ends.next();
            if (ticks < entry.getValue()) {
                continue;
            }
            ends.remove();
            Player player = Bukkit.getPlayerExact(entry.getKey());
            if (player != null) {
                player.setInvulnerable(false);
            }
        }
    }

    /** Every stand-in the hall has out: the sparring partner and each target it brought up. */
    private List<Player> standIns() {
        List<Player> all = new ArrayList<>();
        Player spar = Bukkit.getPlayerExact(SPAR);
        if (spar != null) {
            all.add(spar);
        }
        for (String name : new ArrayList<>(prey.keySet())) {
            Player npc = Bukkit.getPlayerExact(name);
            if (npc != null) {
                all.add(npc);
            }
        }
        return all;
    }

    // -- dying in a room ---------------------------------------------------

    /**
     * Notes who killed a player and where they fell, and does nothing else.
     *
     * <p>LOWEST, and deliberately a notepad rather than an action. The plugin's own conversion runs
     * on MONITOR and reads the killer off the damage event, so a stand-in taken out of the world
     * here - or anywhere before that - would leave the plugin unable to see who did it, and the
     * conversion the room exists to show would never happen. The stand-in is therefore taken away
     * two ticks later, from {@link #putBack}, once everything that reads this death has run.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onKilledByPrey(PlayerDeathEvent event) {
        Player dead = event.getEntity();
        if (prey.containsKey(dead.getName())) {
            return;
        }
        String killer = killerOf(dead);
        if (killer == null) {
            return;
        }
        fellAt.put(dead.getName(), dead.getLocation().clone());
        killedBy.put(dead.getName(), killer);
        Bukkit.getScheduler().runTaskLater(plugin, () -> putBack(dead.getName()), 2L);
    }

    /** The stand-in that landed the fatal blow, or {@code null} when a room did not do this. */
    private String killerOf(Player dead) {
        if (!(dead.getLastDamageCause() instanceof EntityDamageByEntityEvent hit)
                || hit.getDamager() == null) {
            return null;
        }
        return prey.containsKey(hit.getDamager().getName()) ? hit.getDamager().getName() : null;
    }

    /**
     * Takes the killer away and puts the player back on their feet where they fell.
     *
     * <p>Three things, in this order, and the order is the point. The stand-in goes first: his job
     * was to kill somebody into a race and that is done, and a stand-in still standing there is one
     * that would hit the player again the instant they got up. Then the player is put back on the
     * mark they fell on rather than at the hall's entrance - the room is what they were looking at
     * when they died, and coming back somewhere else makes dying read as being sent to your room.
     * Then full health and a grace window, because a blow that lands before they have seen the
     * screen is indistinguishable from the room being broken.
     *
     * <p>No respawn-location handler and no respawn event: this runs two ticks after the death and
     * does the respawn itself, so there is one place that decides where a player comes back. A
     * handler would be a second, which would also outlive the room that set it.
     */
    private void putBack(String name) {
        String killer = killedBy.remove(name);
        Location where = fellAt.remove(name);
        cancelWalk();
        if (killer != null) {
            clearPrey(killer);
        }
        Player player = Bukkit.getPlayerExact(name);
        if (player == null) {
            return;
        }
        if (killer != null) {
            note(player, "§7他杀完就退场了——你已经转化，想再试就再右键一次那块牌");
        }
        if (player.isDead()) {
            player.spigot().respawn();
        }
        if (where != null) {
            player.teleport(where);
            anchorTo(player, where);
        }
        resetVitals(player);
        protectedUntil.put(name, ticks + GRACE_TICKS);
        player.setInvulnerable(true);
        note(player, "§a你回到了倒下的地方，" + (GRACE_TICKS / 20) + " 秒内不会再受伤");
    }

    /** Where a player's grace window has got to, for the probe. */
    String graceReport(Player player) {
        Integer until = protectedUntil.get(player.getName());
        return player.getName()
                + " protected=" + (until == null ? "no" : (until - ticks) + "t")
                + " invulnerable=" + player.isInvulnerable();
    }

    /**
     * Refuses every blow while a player is inside their grace window.
     *
     * <p>This, and not the invulnerability flag, is what actually holds: {@link #land} clears that
     * flag on whoever it is about to hit - it has to, or nothing in this building could be hit at
     * all - so the flag on its own would be taken off by the first stand-in still in the room. It
     * only ever cancels damage, so it cannot make a room pass when it should have failed.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProtectedHit(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player
                && protectedUntil.containsKey(player.getName())) {
            event.setCancelled(true);
        }
    }

    // -- guides ------------------------------------------------------------

    /** A short code per race, so a guide's account name fits in the sixteen a player name gets. */
    private static final Map<String, String> RACE_CODE = Map.of(
            "vampire", "v", "ghoul", "g", "werewolf", "w", "demon", "d",
            "priest", "p", "witchhunter", "h", "enderborn", "e", "angel", "a");

    /**
     * The guides that are up, by account name, and the room each one belongs to.
     *
     * <p>One per room that has somebody in it, and no more. A hall of eleven rooms with a guide in
     * each would be a hundred-odd extra players on the server for a feature nobody is looking at -
     * so a guide exists while his room has a visitor and is gone the moment it does not.
     */
    private final Map<String, String> guides = new LinkedHashMap<>();

    /** The account name a room's guide answers to. */
    private static String guideName(String race, int index) {
        return "Guide_" + RACE_CODE.getOrDefault(race, "x") + "_" + String.format("%02d", index);
    }

    /**
     * Brings the guides up to date: one in every room somebody is standing in, none in the rest.
     *
     * <p>Called from the slow sweep rather than every tick. A guide is a whole player as far as the
     * server is concerned - the account, the connection, the player-info packet - so building one is
     * not a thing to do sixty times a second; two seconds is faster than anybody walks into a room.
     */
    private void refreshGuides(Hall hall) {
        Set<String> wanted = new HashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (guides.containsKey(player.getName())) {
                continue;
            }
            String room = hall.roomAt(player.getLocation());
            if (room != null) {
                wanted.add(room);
            }
        }
        for (Iterator<Map.Entry<String, String>> up = guides.entrySet().iterator(); up.hasNext();) {
            Map.Entry<String, String> entry = up.next();
            if (wanted.contains(entry.getValue())) {
                continue;
            }
            up.remove();
            unstamp(entry.getKey());
            plugin.dropGhost(entry.getKey());
        }
        for (String room : wanted) {
            if (!guides.containsValue(room)) {
                spawnGuide(hall, room);
            }
        }
    }

    /**
     * Puts one room's guide up: a real player in creative, wearing the scribe's clothes.
     *
     * <p>Not the race's own skin. The stand-ins a section brings up wear the race they are playing -
     * that is the point of them - so a guide wearing one would be read as the thing being tested.
     * The scribe is nobody's race, which is what somebody whose only job is to talk should look
     * like.
     *
     * <p>Creative and invulnerable, and not in the tab list: he is furniture that answers, and a
     * visitor should not be able to kill him, shove him or mistake him for a target. {@code
     * unlistPlayer} is per-observer, so it is done for everybody on the server and again for anybody
     * who logs in later.
     */
    private void spawnGuide(Hall hall, String room) {
        int dot = room.lastIndexOf('.');
        if (dot < 0) {
            return;
        }
        String race = room.substring(0, dot);
        int index;
        try {
            index = Integer.parseInt(room.substring(dot + 1));
        } catch (NumberFormatException notARoom) {
            return;
        }
        Location spot = hall.guideSpot(race, index);
        if (spot == null || spot.getWorld() == null) {
            return;
        }
        String name = guideName(race, index);
        Object ghost = plugin.spawnGhost(name, spot.getWorld(), Skins.SCRIBE);
        Player guide = Bukkit.getPlayerExact(name);
        if (ghost == null || guide == null) {
            plugin.dropGhost(name);
            return;
        }
        guide.setGameMode(GameMode.CREATIVE);
        guide.setInvulnerable(true);
        guide.teleport(spot);
        labelGuide(guide, race);
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!viewer.equals(guide)) {
                viewer.unlistPlayer(guide);
            }
        }
        guides.put(name, room);
    }

    /** A guide's name says what he is, in the one place a server may write next to a name. */
    private void labelGuide(Player guide, String race) {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = board.getTeam("mmg_" + race);
        if (team == null) {
            team = board.registerNewTeam("mmg_" + race);
        }
        team.setPrefix("§7导览 §f" + RACE_LABEL.getOrDefault(race, race) + " §r");
        for (Team other : board.getTeams()) {
            if (other.hasEntry(guide.getName())) {
                other.removeEntry(guide.getName());
            }
        }
        team.addEntry(guide.getName());
    }

    /**
     * Right-clicking a guide gets the room's long teaching, in chat.
     *
     * <p>This is where the cooldowns, the permission nodes and the exceptions live. The board says
     * what to do and what should happen - four lines, read at arm's length - and the rest is said
     * by somebody standing in the room, which is the only medium in the building that can hold a
     * sentence and be asked again.
     */
    @EventHandler
    public void onGuideTapped(PlayerInteractEntityEvent event) {
        // One right-click arrives as two events, one per hand, and a player holding the button down
        // produces more. Saying the whole room's teaching twice in a row is not a second answer, it
        // is the same answer in a louder voice - so the off hand is ignored and the same player
        // cannot be told again for half a second.
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        String room = guides.get(event.getRightClicked().getName());
        if (room == null) {
            return;
        }
        event.setCancelled(true);
        Player listener = event.getPlayer();
        Integer lastTold = toldAt.get(listener.getName());
        if (lastTold != null && ticks - lastTold < TALK_GAP) {
            return;
        }
        toldAt.put(listener.getName(), ticks);
        Hall hall = plugin.hallOrNull();
        if (hall == null) {
            return;
        }
        int dot = room.lastIndexOf('.');
        if (dot < 0) {
            return;
        }
        sayGuide(listener, hall.guideTalk(room.substring(0, dot),
                Integer.parseInt(room.substring(dot + 1))));
    }

    /** Reads a room's long teaching out, a line at a time. */
    private void sayGuide(Player player, List<String> talk) {
        for (String line : talk) {
            player.sendMessage(line);
        }
    }

    /** What the guides look like from outside, for the probe. */
    String guideReport(String race) {
        List<String> found = new ArrayList<>();
        for (Map.Entry<String, String> entry : guides.entrySet()) {
            if (!race.isEmpty() && !entry.getValue().startsWith(race + ".")) {
                continue;
            }
            Player guide = Bukkit.getPlayerExact(entry.getKey());
            boolean listed = false;
            if (guide != null) {
                for (Player viewer : Bukkit.getOnlinePlayers()) {
                    if (viewer.isListed(guide)) {
                        listed = true;
                        break;
                    }
                }
            }
            found.add(entry.getKey() + "=" + entry.getValue()
                    + (guide == null ? " missing"
                            : " mode=" + guide.getGameMode() + " listed=" + listed
                                    + " invulnerable=" + guide.isInvulnerable()));
        }
        return "ok guides=" + found.size() + " " + String.join(" | ", found);
    }

    /** Right-clicks a guide's text out, for the probe, and says how many lines there were. */
    String talkReport(String rest) {
        String[] parts = rest.split("\\s+", 2);
        String name = parts.length > 1 ? parts[1] : parts[0];
        Player listener = Bukkit.getPlayerExact(parts[0]);
        String room = guides.get(name);
        Hall hall = plugin.hallOrNull();
        if (room == null || hall == null) {
            return "err no such guide: " + name;
        }
        int dot = room.lastIndexOf('.');
        if (dot < 0) {
            return "err no room for: " + name;
        }
        List<String> talk = hall.guideTalk(room.substring(0, dot),
                Integer.parseInt(room.substring(dot + 1)));
        if (listener != null) {
            sayGuide(listener, talk);
        }
        return "ok " + name + " room=" + room + " lines=" + talk.size()
                + (listener == null ? " (nothing to say it to)" : "");
    }

    /**
     * A joining player starts at the lobby. The world spawn is already there and the spawn radius
     * is zero, so this is normally a no-op - it is here because a player who arrives outside the
     * hall has nowhere to go and no way to ask for directions. The stand-in is left where it is.
     */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Hall hall = plugin.hallOrNull();
        if (hall == null) {
            return;
        }
        Player joining = event.getPlayer();
        if (guides.containsKey(joining.getName())) {
            // One of our own guides coming up. He is placed by spawnGuide a moment from now, and
            // sending him to the lobby first would only mean moving him twice.
            return;
        }
        for (String name : guides.keySet()) {
            Player guide = Bukkit.getPlayerExact(name);
            if (guide != null) {
                joining.unlistPlayer(guide);
            }
        }
        if (SPAR.equals(joining.getName())) {
            return;
        }
        if (!hall.inside(joining.getLocation())) {
            joining.teleport(hall.lobbySpawn());
        }
    }

    // -- switches ----------------------------------------------------------

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Hall hall = plugin.hallOrNull();
        if (hall == null || event.getClickedBlock() == null
                || event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block block = event.getClickedBlock();
        Hall.Trigger trigger = hall.triggerAt(block.getLocation());
        if (trigger == null) {
            if (block.getState() instanceof org.bukkit.block.Sign && hall.inside(block.getLocation())) {
                event.setCancelled(true);
            }
            return;
        }
        // The switch has done its job; whoever used it is moved by what follows.
        event.setCancelled(true);
        use(event.getPlayer(), hall, trigger, block);
    }

    private void use(Player player, Hall hall, Hall.Trigger trigger, Block block) {
        // A player who has just been killed into a race is not to be touched for three seconds, and
        // that has to include the boards: without this the first thing a player can do is press
        // "be killed by one" again and be dead before the grace window they were given has run out,
        // which is the same bug wearing a different hat.
        if (protectedUntil.containsKey(player.getName())) {
            note(player, "§7刚复活，等无敌结束再动");
            return;
        }
        switch (trigger.kind()) {
            case ENTER_LOBBY -> backToHuman(player, hall, hall.lobbySpawn(), "§a回到测试大厅");
            case LEAVE_HALL -> backToHuman(player, hall, hall.outsideSpawn(),
                    "§a已离开测试大厅，右键返回垫上那块牌可以回来");
            case ENTER_AREA -> enter(player, hall, trigger.race());
            case RESET_HUMAN -> {
                restore(player);
                resetVitals(player);
                note(player, "§a已恢复人类，状态和血量也一并清好了，可以留在本区继续试");
            }
            case EXIT_TEST -> backToHuman(player, hall, hall.lobbySpawn(),
                    "§a已回到大厅，种族已恢复人类");
            case POWER -> {
                SuperNManager.alterPower(SuperNManager.get(player), 1000, "Test hall");
                note(player, "§b能量 +1000");
            }
            case DAY -> setClock(player, block.getWorld(), 6000L, "§e这个大厅改成白天了");
            case NIGHT -> setClock(player, block.getWorld(), 18000L, "§9这个大厅改成夜晚了");
            case PREPARE_GHOUL -> prepareGhoul(player, hall);
            case PREPARE_WEREWOLF -> prepareWerewolf(player, hall);
            case INVITE_HUNTER -> {
                SupernaturalsPlugin.instance.getHunterManager().invite(SuperNManager.get(player));
                note(player, "§7已向猎巫人公会报名，约 10 秒后收到邀请，再右键铁门");
            }
            case CLIMB -> teleport(player, hall.point("angel.ledge"), "§b上到高台了，走进中间的洞跳下去");
            case DIP -> teleport(player, hall.netherLava(), "§c下池子了");
            case DIP_WATER -> teleport(player, hall.point(trigger.race() + ".pool"),
                    "§9下池子了，站着别动");
            case SPAWN_CHICKEN -> spawnChicken(player);
            case SPAWN_MONSTERS -> spawnMonsters(player, trigger.race());
            case CLEAR_MONSTERS -> {
                clearHallMobs(player, "§a已清空大厅里的怪物");
                clearPrey();
            }
            case RESET_TELEPORT -> resetTeleport(player);
            case CLEAR_HUNGER -> {
                player.setFoodLevel(0);
                note(player, "§7饱食度已清空，去杀掉那只鸡看看");
            }
            case TOGGLE_RAIN -> toggleRain(player);
            case COLD_BIOME -> coldBiome(player, trigger.race());
            case TEST_HIT -> testHit(player, trigger.detail());
            case TEST_SPELL -> testSpell(player, trigger.detail());
            case USE_ON_NPC -> useOnNpc(player, trigger.detail());
            case SPAWN_TARGET -> spawnTarget(player, trigger.detail());
            case NPC_HITS_NPC -> npcHitsNpc(player, trigger.detail());
            case SPAR -> bringSpar(player, trigger.detail());
            case HURT -> hurt(player);
            case SELF_KILL -> selfKill(player);
            case SPAWN_NPCS -> spawnNpcs(player, trigger.detail());
            case SUN_SPOT -> teleport(player, hall.point(trigger.race() + ".sun"),
                    "§6站到露天台上：这一格头顶没有屋顶");
            case ROOF_SPOT -> teleport(player, hall.point(trigger.race() + ".roof"),
                    "§a站到有顶处：这一格头顶有方块，雨落在棚子上");
            case RESTORE_HEALTH -> restoreHealth(player);
            case RETURN_START -> teleport(player, hall.point("spawn." + trigger.race()),
                    "§a回到这一族的入口");
            case GO_NETHER -> teleport(player,
                    hall.point("spawn." + trigger.race() + "_nether"),
                    "§4这里是下界：在这里死才算数");
            case RETURN_OVERWORLD -> teleport(player, hall.point("spawn." + trigger.race()),
                    "§a回到主世界的那条长廊");
            case LEFT_CLICK -> leftClick(player);
            case BOUNTY -> bounty(player);
            case FALL_TEST -> teleport(player, hall.point("fall." + trigger.race()),
                    "§6放开手：掉到井底，再点井壁上的「回到地面」回大厅");
            case BACK_DOWN -> teleport(player, hall.point("fall.ground." + trigger.race()),
                    "§a回到大厅地面了");
            case SET_FIRE -> setFire(player);
            case DIP_LAVA -> teleport(player, hall.point("lava." + trigger.race()),
                    "§4下池子了：恶魔在岩浆里回血回能，别的种族会被烧、还会掉进去");
            case POWER_READ -> readPower(player);
            case SPAWN_MOB -> spawnMob(player, trigger.detail());
            case ROOM_NEXT -> walkRoom(player, hall, trigger.race(), 1);
            case ROOM_PREV -> walkRoom(player, hall, trigger.race(), -1);
            case EXIT_ROOM -> backToHuman(player, hall, hall.lobbySpawn(),
                    "§a已退出测试：道具已清空，种族已恢复人类，回到大厅");
            case GO_ROOM -> goRoom(player, hall, trigger.detail());
            case SUN_MAT -> standUnder(player, hall, trigger.race(), trigger.detail());
            case ARM_ME -> armMe(player, trigger.detail());
            case SELF_CONVERT -> selfConvert(player, trigger.detail());
            case SPREAD -> toggleSpread(player);
            case SPAWN_ALIGN -> spawnAlign(player, trigger.detail());
            case NPC_KILL_ME -> npcKillMe(player, trigger.detail());
            case NPC_CAST_ME -> npcCastMe(player, trigger.detail());
            case DROP_TOWER -> dropTower(player, hall, trigger.race());
        }
    }

    // -- walking the grid ---------------------------------------------------

    /**
     * Walks the player one room on or back.
     *
     * <p>Where the player is comes from the room they are standing in, not from a number they were
     * told: the rooms of a serpentine row run one way and the next row runs back, and a room that
     * takes four cells is still one step. A player standing between two rooms (in a doorway) is
     * taken to have left the one they were in, and a player past either end is told so rather than
     * moved somewhere arbitrary.
     */
    private void walkRoom(Player player, Hall hall, String race, int delta) {
        if (hall == null || race == null) {
            return;
        }
        restoreSpread();
        int count = hall.roomCount(race);
        int here = hall.roomIndexAt(race, player.getLocation());
        if (count == 0 || here < 0) {
            note(player, "§7你现在不在任何一间房里——按「回入口大厅」再挑一间");
            return;
        }
        int next = here + delta;
        if (next < 0) {
            note(player, "§7这已经是第一间了");
            return;
        }
        if (next >= count) {
            note(player, "§7这已经是最后一间了");
            return;
        }
        Location at = hall.roomSpawn(race, next);
        teleport(player, at, delta > 0 ? "§a下一间" : "§a上一间");
    }

    /** Sends the player to a named room of a named hall. The detail is {@code RACE:INDEX}. */
    private void goRoom(Player player, Hall hall, String detail) {
        if (hall == null || detail == null) {
            return;
        }
        restoreSpread();
        String[] parts = detail.split(":", 2);
        if (parts.length < 2) {
            return;
        }
        int index;
        try {
            index = Integer.parseInt(parts[1].trim());
        } catch (NumberFormatException notANumber) {
            return;
        }
        Location at = hall.roomSpawn(parts[0].trim(), index);
        if (at == null) {
            note(player, "§c那间房不存在");
            return;
        }
        teleport(player, at, "§a到了 " + Hall.title(parts[0].trim()) + " 的第 " + index + " 间");
    }

    /**
     * Stands the player under one of the four roofs a step about the sun is about.
     *
     * <p>The four are one click apart on purpose. Whether glass counts as cover and whether leaves
     * do is the whole question, and a player who has to walk somewhere else between the two
     * answers cannot compare them.
     */
    private void standUnder(Player player, Hall hall, String race, String mat) {
        Location at = hall == null || race == null ? null
                : hall.point(race + ".sun." + (mat == null ? "open" : mat));
        if (at == null) {
            note(player, "§c这一格还没建好");
            return;
        }
        String what = switch (mat == null ? "open" : mat) {
            case "glass" -> "§b头顶是玻璃";
            case "leaves" -> "§a头顶是树叶";
            case "solid" -> "§7头顶是石头";
            default -> "§6头顶什么都没有，完全露天";
        };
        teleport(player, at, what);
    }

    /** Puts the player on the release platform of a race's entry tower. */
    private void dropTower(Player player, Hall hall, String race) {
        Location at = hall == null || race == null ? null : hall.point(race + ".tower");
        if (at == null) {
            note(player, "§c入族塔还没建好");
            return;
        }
        teleport(player, at, "§6站到塔顶了：手上要拿着羽毛，然后按「放手」");
    }

    // -- being changed by somebody else -------------------------------------

    /**
     * Puts the item a conversion needs into the player's hand.
     *
     * <p>Two of the conversions need the person being converted to be holding something - a
     * demon's blow only heats a body that is holding netherrack, and an enderborn's only takes
     * hold of a body holding a pearl. Which is the sort of detail a room can describe all day and
     * a player will still not have in their hand.
     */
    private void armMe(Player player, String material) {
        Material item = material == null ? null : Material.matchMaterial(material);
        if (item == null) {
            note(player, "§c这个环节没有配置物品");
            return;
        }
        player.getInventory().setItemInMainHand(new ItemStack(item, 1));
        note(player, "§7已把 " + item.name() + " 放到你手上");
    }

    /**
     * Hands the player what one of the self-service conversions wants, and says what to do with
     * it. The interaction itself has to be the player's - the plugin reads a right-click on a
     * block, and a right-click a section performs is not one.
     */
    private void selfConvert(Player player, String detail) {
        if (detail == null || detail.isBlank()) {
            return;
        }
        for (String one : detail.split(",")) {
            armMe(player, one.trim());
        }
        note(player, "§7拿着它右键旁边的方块，转换就发生在插件自己的判定里");
    }

    /**
     * Puts one stand-in out as a named race, to see what a rule answers for somebody other than
     * the player.
     *
     * <p>A ghoul cannot bind a human, and an angel counts as one; a priest's cure takes a
     * supernatural target and refuses the same four. The interesting half of those rules is the
     * refusal, so the room has to be able to put the refused target in front of the player.
     */
    private void spawnAlign(Player player, String race) {
        spawnNpcs(player, race);
    }

    /**
     * Sends a stand-in of a named race at the player until the player is dead.
     *
     * <p>This is the "I am the one being turned" half of every race's conversion. The plugin turns
     * whoever a race kills, so the only honest way to show it is for a member of that race to
     * actually kill the player: the dice, the message and the new race all come out of the same
     * path a real fight would take.
     *
     * <p>What he is holding matters more than it looks. Each race has its own list of weapons it
     * cannot use, and a race that cannot use a weapon deals nothing with one - so a stand-in whose
     * race bans swords has to arrive empty-handed or the kill never lands and the section looks
     * broken for a reason nobody can see. The check below is against the plugin's own list, so it
     * stays right when the list changes.
     */
    private void npcKillMe(Player player, String detail) {
        String[] parts = detail == null ? new String[0] : detail.trim().split(":", 2);
        String race = parts.length > 0 ? parts[0].trim() : "";
        Material wanted = parts.length > 1 ? Material.matchMaterial(parts[1].trim()) : null;
        if (race.isEmpty()) {
            return;
        }
        Material hand = legalWeapon(race, wanted, player);
        Player npc = putPrey(player, 0, race, hand);
        if (npc == null) {
            note(player, "§c他起不来，这个环节暂时不能用");
            return;
        }
        fullPower(npc);
        revives.put(npc.getName(), npc.getLocation().clone());
        Bukkit.getScheduler().runTaskLater(plugin, () -> hunt(player, npc), 30L);
    }

    /**
     * Sends a stand-in of a named race over to cast one of its spells on the player.
     *
     * <p>Every spell the plugin has is hung on a damage event - the manager for whoever dealt the
     * blow is asked what that blow does - so "cast at the player" and "hit the player" are the
     * same action. A stand-in that walks up and swings is therefore casting; one that stands
     * still is not. He is topped up first because a cast costs power and the power is spent for
     * real: exorcising costs nine thousand of a possible ten, so a stand-in that has cast once
     * cannot cast again until somebody fills him up.
     */
    private void npcCastMe(Player player, String detail) {
        String[] parts = detail == null ? new String[0] : detail.trim().split(":", 2);
        String race = parts.length > 0 ? parts[0].trim() : "";
        Material item = parts.length > 1 ? Material.matchMaterial(parts[1].trim()) : null;
        if (race.isEmpty()) {
            return;
        }
        Player npc = putPrey(player, 0, race, item);
        if (npc == null) {
            note(player, "§c他起不来，这个环节暂时不能用");
            return;
        }
        fullPower(npc);
        Bukkit.getScheduler().runTaskLater(plugin, () -> hunt(player, npc), 30L);
    }

    /** Walks a stand-in over to the player and swings until something changes. */
    private void hunt(Player player, Player npc) {
        if (!npc.isOnline() || !player.isOnline()) {
            return;
        }
        walkIn(npc, player, () -> {
            if (!player.isOnline()) {
                return;
            }
            strike(npc, player);
            // A blow worth two is a demonstration, not a fight: what these rooms are about is
            // being killed, and a stand-in that lands a hit every two seconds takes half a
            // minute over it. The rest of the job is done by the same swing, faster.
            for (int i = 0; i < 12 && player.isOnline() && !player.isDead(); i++) {
                strike(npc, player);
            }
        });
    }

    /**
     * Makes the chance of being turned certain, for the room whose whole subject is being turned.
     *
     * <p>The roll stays the plugin's - this writes the number the roll is compared against, which
     * the plugin reads at the moment of the kill, so the kill, the message and the new race all
     * still come out of the plugin's own path. It is a toggle as well as a switch because a player
     * who has just watched the certain version should be able to watch the real one without
     * leaving the room, and it is put back automatically on the way out either way.
     */
    private void toggleSpread(Player player) {
        if (spreadWas == null) {
            spreadWas = SNConfigHandler.spreadChance;
            SNConfigHandler.spreadChance = 1.0;
            note(player, "§c这一间的转化已改成必定成功（原值 " + percent(spreadWas)
                    + "，离开这间房会自动恢复）");
            return;
        }
        SNConfigHandler.spreadChance = spreadWas;
        double restored = spreadWas;
        spreadWas = null;
        note(player, "§a这一间的转化已恢复成原来的概率（" + percent(restored) + "）");
    }

    /** A probability as the percentage it is configured as: 0.35 reads "35%", not "0.4". */
    private static String percent(double chance) {
        return Math.round(chance * 100) + "%";
    }

    /**
     * Puts the plugin's conversion chance back to what it was configured as.
     *
     * <p>Called everywhere a player leaves the room that borrowed it. A teaching setting that
     * outlived the room would be a lie the next room tells, and the rooms after this one are about
     * the real chance.
     */
    private void restoreSpread() {
        if (spreadWas != null) {
            SNConfigHandler.spreadChance = spreadWas;
            spreadWas = null;
        }
    }

    /**
     * Fills a stand-in's power up, because spells are paid for out of it.
     *
     * <p>The ceiling is ten thousand whatever is asked for - the setter clamps - and a priest's
     * exorcism costs nine of it, so "full power" is not a one-off gift at spawn: a second casting
     * needs a second fill.
     */
    private void fullPower(Player npc) {
        SuperNPlayer snplayer = SuperNManager.get(npc);
        if (snplayer != null) {
            snplayer.setPower(10000);
        }
    }

    /**
     * The weapon a stand-in of this race may swing, or nothing.
     *
     * <p>Each race has a list of things it cannot use, taken from the plugin's own configuration
     * rather than copied - a copy is right until the day the list changes, and this one has to be
     * right every day. A race whose list is empty or unset can hold anything.
     */
    private Material legalWeapon(String race, Material wanted, Player player) {
        if (wanted == null || wanted.isAir()) {
            return null;
        }
        List<Material> banned = bannedWeapons(race);
        if (banned.contains(wanted)) {
            note(player, "§e" + Hall.title(race) + " 用不了 " + wanted.name()
                    + "（在它的禁用名单里），所以只能空手出手");
            return null;
        }
        return wanted;
    }

    /** What a race may not swing, straight out of the plugin's own configuration. */
    static List<Material> bannedWeapons(String race) {
        return switch (race) {
            case "ghoul" -> SNConfigHandler.ghoulWeapons;
            case "werewolf" -> SNConfigHandler.wereWeapons;
            case "witchhunter" -> SNConfigHandler.hunterWeapons;
            case "enderborn" -> SNConfigHandler.enderWeapons;
            case "vampire" -> SNConfigHandler.vampireWeapons;
            case "demon" -> SNConfigHandler.demonWeapons;
            case "priest" -> SNConfigHandler.priestWeapons;
            default -> List.of();
        };
    }

    /** What a race may wear, or {@code null} when it may wear anything the API can name. */
    static List<Material> allowedArmor(String race) {
        return switch (race) {
            case "vampire" -> SNConfigHandler.vampireArmor;
            case "ghoul" -> SNConfigHandler.ghoulArmor;
            case "werewolf" -> SNConfigHandler.wereArmor;
            case "demon" -> SNConfigHandler.demonArmor;
            case "priest" -> SNConfigHandler.priestArmor;
            case "witchhunter" -> SNConfigHandler.hunterArmor;
            default -> null;
        };
    }

    /**
     * Lets out one mob of a named type, for a step whose answer depends on what kind of creature it
     * is - an angel gains power from a monster and nothing from an animal, so a section about that
     * has to put a monster in front of them, not a chicken.
     */
    private void spawnMob(Player player, String typeName) {
        EntityType type = typeName == null ? null : EntityType.fromName(typeName);
        if (type == null) {
            note(player, "§c这个环节没有配置怪物类型");
            return;
        }
        clearHallMobs(player, null);
        // Through the same four questions every other summon answers. This one used to drop its
        // monster two and a half blocks along x from the player without asking anything - which is
        // inside the wall whenever he is reading the board that called for it, and reading the
        // board is the only way this board is ever used.
        Location at = summonSpot(player, List.of(), SUMMON_FAR);
        try {
            Entity spawned = player.getWorld().spawnEntity(at, type);
            spawned.setPersistent(true);
            keepIn(plugin.hallOrNull(), spawned, player.getLocation());
            note(player, "§c放出了一只 " + type.name() + "，杀掉它看能量");
        } catch (RuntimeException notPlaceable) {
            note(player, "§c这里放不下 " + type.name());
        }
    }

    /** Sets the player alight, which is what several races claim not to care about. */
    private void setFire(Player player) {
        player.setFireTicks(200);
        note(player, "§c你被点着了——免疫火焰的种族不会掉血，也不该真的烧起来");
    }

    /** Reads a player's power back, because a step about loss or gain has to be measurable. */
    private void readPower(Player player) {
        SuperNPlayer snplayer = SuperNManager.get(player);
        note(player, "§b当前能量 §f" + ((int) snplayer.getPower())
                + "§7（种族 " + snplayer.getType() + "）");
    }

    /** Swings whatever is in hand at nothing, which is how a hunter cycles its arrow mode. */
    private void leftClick(Player player) {
        PlayerInteractEvent event = new PlayerInteractEvent(player, Action.LEFT_CLICK_AIR,
                player.getInventory().getItemInMainHand(), null, null);
        Bukkit.getPluginManager().callEvent(event);
        note(player, event.isCancelled()
                ? "§7切换被拦下了，检查手里是不是拿着弓"
                : "§7已空挥一次（手持弓才会切换箭种）");
    }

    /** Reads the hunter's bounty list back, and gets one added if there is room. */
    private void bounty(Player player) {
        HunterManager.addBounty();
        List<String> names = new ArrayList<>();
        for (SuperNPlayer target : HunterManager.getBountyList()) {
            names.add(target.getName());
        }
        note(player, "§6当前悬赏（最多 5 个）：§f"
                + (names.isEmpty() ? "（暂时没有）" : String.join("、", names)));
    }

    private void teleport(Player player, Location where, String what) {
        if (where == null) {
            note(player, "§c这个环节的目标点没有建起来");
            return;
        }
        player.teleport(where);
        anchorTo(player, where);
        note(player, what);
    }

    /**
     * Moves a player's respawn point to the room they are standing in.
     *
     * <p>Dying in the fifth room and opening their eyes at the hall's door made every death read as
     * being sent back to the start: the anchor was set once, when the player walked into the hall,
     * and nothing they did afterwards moved it. It moves with them now, room by room - and it is
     * the room's own landing rather than the spot they were standing on, because a room may well
     * kill somebody somewhere nobody can stand: a pool, a shaft, the top of a fall. The landing is
     * the one place in a room that is always safe.
     *
     * <p>Silent about anywhere that is not a room, on purpose. The lobby, the return pad and the
     * world outside the hall each have an anchor of their own, set when the player was sent there,
     * and a teleport within one of those must not move it.
     */
    private void anchorTo(Player player, Location where) {
        Hall hall = plugin.hallOrNull();
        if (hall == null || where == null) {
            return;
        }
        String room = hall.roomAt(where);
        if (room == null) {
            return;
        }
        int dot = room.lastIndexOf('.');
        if (dot < 0) {
            return;
        }
        int index;
        try {
            index = Integer.parseInt(room.substring(dot + 1));
        } catch (NumberFormatException notARoom) {
            return;
        }
        Location landing = hall.roomSpawn(room.substring(0, dot), index);
        if (landing != null) {
            player.setRespawnLocation(landing, true);
        }
    }

    /** Where a player is, and where they would come back if they died there, for the probe. */
    String anchorReport(Player player) {
        Location anchor = player.getRespawnLocation();
        Hall hall = plugin.hallOrNull();
        String room = hall == null
                ? "(no hall on this server)"
                : String.valueOf(hall.roomAt(player.getLocation()));
        return player.getName() + " room=" + room + " respawns at "
                + (anchor == null
                        ? "the world's own spawn - no anchor has been set"
                        : anchor.getWorld().getName() + " " + anchor.getBlockX() + ","
                                + anchor.getBlockY() + "," + anchor.getBlockZ());
    }

    /**
     * Tells the player, and the console with them. Everything a section explains goes to a
     * player's chat, and a fake player has no chat - so without this a scenario driving the hall
     * from a console can see that a switch fired but not what it decided.
     */
    private void note(Player player, String message) {
        player.sendMessage(message);
        plugin.getLogger().info(player.getName() + " ← " + message);
    }

    /**
     * Entering a hall starts a test from a human, and gives a vampire's saved teleport point back
     * to the hall: the book teleports wherever the marker was last set, so a marker set outside
     * would be a way straight out of a sealed test.
     */
    private void enter(Player player, Hall hall, String race) {
        Location where = hall.point("spawn." + race);
        if (where == null) {
            note(player, "§c这个大厅没有建起来");
            return;
        }
        backToHuman(player, hall, where, "§e进入测试大厅：§f" + race);
        if ("vampire".equals(race)) {
            // Walking in resets the book's marker, every time, and it is written a tick later:
            // the marker is read back off the player, and a teleport taken in the same tick is
            // not always visible to getLocation() yet. A marker left where it was set - outside
            // the hall - is a way to teleport straight back out of a sealed test.
            Bukkit.getScheduler().runTask(plugin, () -> {
                SupernaturalsPlugin.instance.getDataHandler().addTeleport(SuperNManager.get(player));
                note(player, "§7传送点已重置到大厅里");
            });
        }
    }

    private void resetTeleport(Player player) {
        SupernaturalsPlugin.instance.getDataHandler().addTeleport(SuperNManager.get(player));
        note(player, "§d传送点已重设在脚下");
    }

    private void setClock(Player player, World world, long time, String what) {
        world.setTime(time);
        note(player, what);
    }

    private void toggleRain(Player player) {
        World world = player.getWorld();
        boolean raining = !world.hasStorm();
        // The duration is the point. Setting the flag alone leaves a storm that is over on the
        // next tick - the sky clears before anybody can look up - which is indistinguishable
        // from a switch that does nothing at all.
        world.setStorm(raining);
        world.setThundering(false);
        if (raining) {
            world.setWeatherDuration(20 * 60 * 5);
        } else {
            world.setClearWeatherDuration(20 * 60 * 5);
        }
        note(player, raining ? "§9这个大厅开始下雨了（下 5 分钟）" : "§7这个大厅的雨停了");
        if (!raining) {
            return;
        }
        if (!openToSky(player)) {
            note(player, "§7不过你头顶有屋顶，雨落不到你身上 —— 点旁边的「站到露天台」");
        } else if (SuperNManager.get(player).isGhoul()) {
            // The one place where "it is raining" is not just scenery. A ghoul that is standing in
            // the rain stops regenerating, and since this switch is right next to the regeneration
            // step, a reader who flips it watches their health bar stop moving with nothing on
            // screen to explain it. So the switch says what it just did to the rule.
            note(player, "§7你在露天淋着雨：食尸鬼的回血现在被关掉了，"
                    + "点「站到有顶处」就恢复（不用关雨）");
        }
    }

    /**
     * Whether the column the player stands in reaches the sky. Rain falls only where it can, so a
     * person who toggles the weather on from under a ceiling sees nothing and reports a broken
     * switch.
     */
    private static boolean openToSky(Player player) {
        Location at = player.getLocation();
        World world = at.getWorld();
        for (int y = at.getBlockY() + 1; y < world.getMaxHeight(); y++) {
            if (!world.getBlockAt(at.getBlockX(), y, at.getBlockZ()).getType().isAir()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Reports the biome the plugin will read for this hall, and tries to set it cold as well.
     *
     * <p>The demon's only way back to humanity is drowning in a cold biome, and the plugin asks
     * its world for the biome at <b>y=0</b> - the two-argument {@code getBiome} forwards to the
     * three-argument one with a zero. On this server build {@code setBiome} does nothing at all
     * (writing and reading straight back returns the old value), so the hall is generated cold
     * instead and this switch is there to confirm it. On a server where the write works, it will
     * simply have done the job by the time it says so.
     */
    private void coldBiome(Player player, String race) {
        Hall hall = plugin.hallOrNull();
        Hall.Box zone = hall == null ? null : hall.zone("hall." + race);
        if (zone == null) {
            note(player, "§c找不到这个大厅的范围");
            return;
        }
        World world = player.getWorld();
        int x = zone.x1() + 2;
        int z = zone.z1() + 7;
        for (int step = zone.x1(); step <= zone.x2(); step++) {
            for (int depth = zone.z1(); depth <= zone.z2(); depth++) {
                // A write to a chunk nobody has loaded is a write that never happened.
                world.getChunkAt(Math.floorDiv(step, 16), Math.floorDiv(depth, 16));
                world.setBiome(step, 0, depth, Biome.SNOWY_PLAINS);
            }
        }
        Biome actual = world.getBiome(x, 0, z);
        boolean cold = actual == Biome.SNOWY_PLAINS || actual == Biome.SNOWY_TAIGA
                || actual == Biome.TAIGA || actual == Biome.SNOWY_SLOPES;
        note(player, "§b插件会读到的生物群系：§f" + actual
                + (cold ? " §a✓ 可以按寒冷溺水恢复人类" : " §c✗ 不够冷，插件不会认"));
    }

    private boolean restore(Player player) {
        SuperNPlayer snplayer = SuperNManager.get(player);
        if (snplayer.isHuman()) {
            note(player, "§7你本来就是人类");
            return false;
        }
        SuperNManager.cure(snplayer);
        return true;
    }

    /** Empties the props, takes the hall's monsters back, restores humanity and moves the player. */
    private void backToHuman(Player player, Hall hall, Location where, String what) {
        restoreSpread();
        cancelWalk();
        clearHallMobs(player, null);
        clearPrey();
        player.getInventory().clear();
        restore(player);
        resetVitals(player);
        if (where != null) {
            player.teleport(where);
            player.setRespawnLocation(where, true);
        }
        note(player, what);
    }

    /**
     * Puts a player back the way they walked in: no effects, not on fire, health and hunger full.
     *
     * <p>Leaving a test used to keep whatever the test had done to them - poisoned by the truce
     * section, half a health bar, no food, still burning - and that followed them into the next
     * race, where it looked like the next race's fault.
     */
    private void resetVitals(Player player) {
        for (PotionEffect effect : new ArrayList<>(player.getActivePotionEffects())) {
            player.removePotionEffect(effect.getType());
        }
        player.setFireTicks(0);
        AttributeInstance max = player.getAttribute(Attribute.MAX_HEALTH);
        player.setHealth(max == null ? 20 : max.getValue());
        player.setFoodLevel(20);
        player.setSaturation(20f);
        player.setExhaustion(0f);
        player.setFallDistance(0f);
    }

    /**
     * Takes a bite out of the player, so a section about healing or regeneration has a wound to
     * work on. Without it the only way to test a heal is to go and get hurt somewhere else, and
     * a player at twenty health looks exactly like a heal that does not work.
     */
    private void hurt(Player player) {
        AttributeInstance max = player.getAttribute(Attribute.MAX_HEALTH);
        double full = max == null ? 20 : max.getValue();
        player.setHealth(Math.max(1, full / 2));
        note(player, "§c已把你打到半血，现在可以看回血了");
    }

    /**
     * Puts the player back on a full bar.
     *
     * <p>The mirror of {@link #hurt}, and the one two steps needed without having it: a step about
     * damage over time wants a full bar to start from, and the switch that said "top yourself up
     * first" was wired to the wound it was meant to undo.
     */
    private void restoreHealth(Player player) {
        AttributeInstance max = player.getAttribute(Attribute.MAX_HEALTH);
        player.setHealth(max == null ? 20 : max.getValue());
        note(player, "§a血已经补满，可以开始了");
    }

    /**
     * Ends the player's life, for a step about what dying does - of which there are now several,
     * and one of them has to be done five times over. A fake client cannot be relied on to lose a
     * fight it is not having, and no killer is invented here: the death is handed to the plugin
     * as what it is, a death with nobody's name on it.
     */
    private void selfKill(Player player) {
        player.setHealth(0);
        note(player, "§7你已经死了一次，回到出生点后可以继续");
    }

    // -- what the hall lets out --------------------------------------------

    /**
     * The wolves a werewolf is supposed to be at peace with.
     *
     * <p>A wolf that is not provoked leaves everybody alone, so a pen of calm wolves would prove
     * nothing. These are angry and pointed at the player: the only reason they will not arrive is
     * the truce itself, which is the thing the section is asking about. The plugin's truce is a
     * switch rather than a list, so there is no config list to read here.
     */
    private void spawnAngryWolves(Player player) {
        Hall hall = plugin.hallOrNull();
        List<Wolf> wolves = new ArrayList<>();
        List<Location> taken = new ArrayList<>();
        // Three places, apart from the player and from each other. The candidates come back nearest
        // first and every one handed out is struck off, which is what stops the second wolf landing
        // inside the first - the old version drew three fixed angles at a fixed radius and did not
        // check any of them against the ones already placed.
        for (Location at : summonCandidates(player, taken, SUMMON_FAR)) {
            if (wolves.size() >= 3) {
                break;
            }
            taken.add(at);
            try {
                Wolf wolf = player.getWorld().spawn(at, Wolf.class, spawned -> {
                    spawned.setAngry(true);
                    spawned.setPersistent(true);
                    spawned.setRemoveWhenFarAway(false);
                    // A target is supplied on purpose. A wild wolf's anger lives in its own anger
                    // target rather than in the target every monster is asked about, so a wolf
                    // left to make up its own mind simply stands there - and the section would
                    // then report "the truce works" without ever having put it to the test.
                    // With the target set, the wolf really comes, and what stops it is the
                    // plugin's refusal of its damage.
                    spawned.setTarget(player);
                });
                keepIn(hall, wolf, player.getLocation());
                wolves.add(wolf);
            } catch (RuntimeException notPlaceable) {
                // nowhere to stand; the count below says so
            }
        }
        note(player, "§c放出了 " + wolves.size() + " 只愤怒的野狼"
                + "——它们会真的扑上来，咬不咬得动才是这一节要看的");
    }

    /**
     * The ghoul's way in, which is narrower than it looks: the plugin converts a corpse only when
     * a Zombie Pigman did the killing <em>and</em> the death happened in the nether. A plain
     * zombie, or the same pigman anywhere else, produces nothing at all - so this button belongs
     * to the nether cell, and what it lets out is a pigman.
     */
    private void prepareGhoul(Player player, Hall hall) {
        clearHallMobs(player, null);
        Location at = summonSpot(player, List.of(), SUMMON_FAR);
        PigZombie pigman = player.getWorld().spawn(at, PigZombie.class, spawned -> {
            spawned.setCustomName("大厅僵尸猪人");
            spawned.setCustomNameVisible(true);
            spawned.setRemoveWhenFarAway(false);
            spawned.setPersistent(true);
            // A zombified piglin is neutral until it is wronged, and no fake client can hit one -
            // so the hall does the wronging itself, or the pigman would stand there all day
            // without ever qualifying as anybody's killer.
            spawned.setTarget(player);
            spawned.setAngry(true);
        });
        keepIn(hall, pigman, player.getLocation());
        note(player, "§2僵尸猪人出现了，站着别动");
    }

    /**
     * The werewolf's way in is a wild wolf at night. This hall has a clock of its own, so the
     * night is somebody's deliberate choice - and the wolf is only a threat because it is.
     */
    private void prepareWerewolf(Player player, Hall hall) {
        if (!SuperNManager.worldTimeIsNight(player)) {
            note(player, "§c现在是白天，先点「夜晚」再来放狼");
            return;
        }
        Location at = summonSpot(player, List.of(), SUMMON_FAR);
        Wolf wolf = player.getWorld().spawn(at, Wolf.class, spawned -> {
            spawned.setAngry(true);
            spawned.setPersistent(true);
            spawned.setRemoveWhenFarAway(false);
            spawned.setTarget(player);
        });
        keepIn(hall, wolf, player.getLocation());
        note(player, "§6狼来了");
    }

    /**
     * One chicken, in place of any older one. It is let out with its awareness turned off, so it
     * stands exactly where it was put: the step is about killing something, not about chasing it.
     */
    private void spawnChicken(Player player) {
        for (UUID id : new ArrayList<>(chickens)) {
            Entity chicken = Bukkit.getEntity(id);
            if (chicken != null) {
                chicken.remove();
            }
            chickens.remove(id);
            propRooms.remove(id);
        }
        Location at = summonSpot(player, List.of(), SUMMON_FAR);
        Chicken chicken = player.getWorld().spawn(at, Chicken.class, spawned -> {
            spawned.setAware(false);
            spawned.setPersistent(true);
            spawned.setRemoveWhenFarAway(false);
            spawned.setCustomName("测试鸡");
            spawned.setCustomNameVisible(true);
        });
        chickens.add(chicken.getUniqueId());
        Hall hall = plugin.hallOrNull();
        propRooms.put(chicken.getUniqueId(), hall == null ? null : hall.roomAt(player.getLocation()));
        note(player, "§2鸡来了，它不会动");
    }

    /**
     * Calls out every monster the plugin lists for this race - the ones it is supposed to be at
     * peace with - so the truce can be watched instead of taken on faith.
     *
     * <p>A dragon is the one that needs different treatment: it is sixteen blocks from nose to
     * tail, so the ring the small monsters go on would bury it in a wall. It goes in the middle
     * of the section, where there is room, and is left hovering rather than circling.
     */
    private void spawnMonsters(Player player, String race) {
        clearHallMobs(player, null);
        if ("werewolf".equals(race)) {
            spawnAngryWolves(player);
            return;
        }
        List<EntityType> wanted = new ArrayList<>(
                "ghoul".equals(race) ? SNConfigHandler.ghoulTruce : SNConfigHandler.vampireTruce);
        Hall.Box pen = pen(player, race);
        // Around the middle of the room, not around the player. A board is read from the far end of
        // the room or from the doorway, and a ring drawn where the player happens to be standing
        // puts most of it in a wall - which is how a room about "watch whether they come at you"
        // ended up showing a handful of monsters and a lot of nothing.
        Location centre = middleOf(pen, player.getLocation());
        // Everywhere a walk from the player reaches, inside this room. The ring is the shape the
        // step wants; this is what decides whether a point on it is somewhere a monster can stand
        // and still come at him from. A ring drawn through a pillar, or through the wall of a room
        // too narrow to hold it, lands monsters in both - and the board's own words are that they
        // come at you.
        List<Location> reach = walkable(player, pen);
        List<String> refused = new ArrayList<>();
        int stranded = 0;
        int placed = 0;
        double furthest = 0;
        for (EntityType type : wanted) {
            if (type == null) {
                continue;
            }
            if (UNROOMABLE.contains(type.name())) {
                refused.add(type.name());
                continue;
            }
            if (type == EntityType.ENDER_DRAGON) {
                if (placeDragon(pen, centre)) {
                    placed++;
                } else {
                    refused.add(type.name());
                }
                continue;
            }
            // Rings of ten, six blocks out and three and a half further out each turn: forty
            // monsters come to four rings, the outermost sixteen blocks from the middle, which
            // leaves a few blocks of floor between the outside of the ring and the wall. That
            // margin is the point - a ring drawn wide enough to touch the wall is a ring whose
            // outermost monsters are inside it, and the room was built at four cells for exactly
            // this: in any other room the outer rings are past the walls, and monsters that should
            // be standing in front of the player are standing in the next room.
            int ring = placed / PER_RING;
            double angle = (placed % PER_RING) * (2 * Math.PI / PER_RING) + ring * 0.35;
            double out = 6 + ring * 3.5;
            Location at = nearestReachable(reach, clampIn(pen,
                    centre.clone().add(Math.cos(angle) * out, 0, Math.sin(angle) * out)));
            if (at == null) {
                // A place on this ring that a monster could stand on, and nothing close enough to
                // it to call the point home. Left out and counted, rather than dropped in a wall.
                stranded++;
                continue;
            }
            try {
                Entity spawned = centre.getWorld().spawnEntity(at, type);
                spawned.setPersistent(true);
                keepIn(plugin.hallOrNull(), spawned, centre);
                placed++;
                furthest = Math.max(furthest, at.distance(centre));
            } catch (RuntimeException notPlaceable) {
                refused.add(type.name());
            }
        }
        // How far out the furthest one went, said out loud. "Forty-one of them, all inside the
        // room" is the thing this note exists to be able to claim, and a count on its own cannot
        // claim it - forty monsters clamped to the hall's own edge is also a count of forty.
        note(player, "§c召唤了 " + placed + " 只怪物，最远的离房间中心 " + Math.round(furthest) + " 格"
                + (stranded == 0 ? "" : "；" + stranded + " 个环位走不到你，已跳过")
                + (refused.isEmpty() ? "" : "；室内放不下的：" + String.join("、", refused)));
    }

    /** How far a point on a shape may be moved to land on floor a walk can reach. */
    private static final double RING_SLACK = 6.0;

    /**
     * The reachable block nearest a wanted place, or {@code null} when the nearest is too far away.
     *
     * <p>Six blocks of slack, because a ring is a shape drawn on the floor of a room and a monster
     * is a thing that has to stand on it: where the shape and the floor disagree - a pillar, a lip,
     * the corner of a room built a cell too small - it is the ring that is worth moving. Past six
     * the shape is gone, and the caller is better off knowing that than having a monster in the
     * wrong place.
     */
    private static Location nearestReachable(List<Location> reach, Location wanted) {
        Location best = null;
        double shortest = RING_SLACK;
        for (Location at : reach) {
            double away = at.distance(wanted);
            if (away < shortest) {
                shortest = away;
                best = at;
            }
        }
        return best == null ? null : middleOfBlock(best);
    }

    /** A dragon, in the middle of the room with room to be a dragon in. */
    private boolean placeDragon(Hall.Box pen, Location centre) {
        Location at = clampIn(pen, centre.clone()).add(0, 6, 0);
        try {
            EnderDragon dragon = centre.getWorld().spawn(at, EnderDragon.class);
            dragon.setPhase(EnderDragon.Phase.HOVER);
            dragon.setPersistent(true);
            keepIn(plugin.hallOrNull(), dragon, centre);
            return true;
        } catch (RuntimeException | LinkageError notPlaceable) {
            plugin.getLogger().warning("could not summon the ender dragon: " + notPlaceable);
            return false;
        }
    }

    /**
     * The box a section's monsters have to stay inside: the room the player is testing in, or the
     * race's whole hall when they are not standing in one.
     *
     * <p>It was the hall for everything, which is the other half of how forty monsters ended up in
     * the neighbouring rooms. The rings a small room cannot hold were clamped to the hall's own
     * edge - a boundary several rooms away from the room they were drawn in.
     */
    private Hall.Box pen(Player player, String race) {
        Hall hall = plugin.hallOrNull();
        if (hall == null) {
            return null;
        }
        String room = roomOf(player);
        Hall.Box inRoom = room == null ? null : hall.zone("room." + room);
        return inRoom != null ? inRoom : hall.zone("hall." + race);
    }

    /** The middle of a box, at the height of the player standing in it. */
    private static Location middleOf(Hall.Box zone, Location fallback) {
        if (zone == null) {
            return fallback;
        }
        return new Location(fallback.getWorld(),
                (zone.x1() + zone.x2()) / 2.0, fallback.getY(), (zone.z1() + zone.z2()) / 2.0);
    }

    /** Keeps a point inside the box a section is working in, however many are already there. */
    private static Location clampIn(Hall.Box zone, Location at) {
        if (zone == null) {
            return at;
        }
        return new Location(at.getWorld(),
                Math.clamp(at.getX(), zone.x1() + 1.5, zone.x2() - 0.5),
                at.getY(),
                Math.clamp(at.getZ(), zone.z1() + 1.5, zone.z2() - 0.5));
    }

    /** Remembers a mob as the hall's, and where it belongs. */
    private void keepIn(Hall hall, Entity entity, Location home) {
        if (hall != null) {
            entity.addScoreboardTag(HALL_MOB);
            hallMobs.put(entity.getUniqueId(), home.clone());
            hallEntities.put(entity.getUniqueId(), entity);
            propRooms.put(entity.getUniqueId(), hall.roomAt(home));
        }
    }

    /**
     * Takes back everything the hall let out, chickens included.
     *
     * <p>The mobs are held by reference as well as by id on purpose. {@code Bukkit.getEntity(id)}
     * only looks at <b>loaded</b> entities, so a monster that wandered into a chunk the server
     * had put away came back as null - and the first version dropped it from the books at that
     * point, leaving a zombified piglin standing in the hall that "clear monsters" could no
     * longer see. One that is genuinely gone is simply forgotten.
     */
    private void clearHallMobs(Player player, String message) {
        removeAll(hallMobs.keySet(), hallEntities);
        for (UUID id : new ArrayList<>(chickens)) {
            Entity chicken = Bukkit.getEntity(id);
            if (chicken != null) {
                chicken.remove();
            }
            chickens.remove(id);
        }
        // Then a sweep of the halls themselves. Anything the book-keeping missed - a piglin that
        // became a zombified piglin under a new id, the vexes an evoker called in - is still
        // standing in a hall, and that is enough to find it by.
        Hall hall = plugin.hallOrNull();
        if (hall != null) {
            for (Entity entity : hall.livingInHalls()) {
                hallMobs.remove(entity.getUniqueId());
                hallEntities.remove(entity.getUniqueId());
                removeSpectres(entity);
                entity.remove();
            }
        }
        // The rooms are empty of all of it by now, so the ledger for "which room was this let out
        // in" goes with them. The stand-ins keep their own, because prey are deliberately not
        // cleared here.
        propRooms.clear();
        // Prey are deliberately *not* cleared here. Taking the hall's monsters back happens on
        // every death, and a section's prey are players: clearing them from here meant killing one
        // of the three a witch hunter section puts out took the other two with it.
        if (message != null && player != null) {
            note(player, message);
        }
    }
    // -- test prey ---------------------------------------------------------

    /**
     * Calls up one stand-in player per race named, as prey.
     *
     * <p>A witch hunter is made by killing three different kinds of supernatural, and a hall that
     * only hands out an invitation skips the one rule worth watching. These are real players with
     * no clients: they can be attacked and killed exactly like anybody else, and the plugin's own
     * book-keeping does the rest - which is the point, because that book-keeping is the thing
     * being tested.
     */
    private void spawnNpcs(Player player, String races) {
        clearPrey();
        if (races == null || races.isBlank()) {
            return;
        }
        String[] wanted = races.split(",");
        List<String> made = new ArrayList<>();
        taken.clear();
        int seat = 0;
        for (String one : wanted) {
            if (seat >= PREY_NAMES.size()) {
                break;
            }
            // "vampire" is one; "vampire:NETHERRACK" is one holding netherrack, which is what a
            // spell that needs both sides to be holding something asks for. "random" is one of the
            // eight races, for a section whose point is that any of them will do; "super" is one
            // of the races the plugin itself calls supernatural, for a section whose rule turns
            // the rest away - the ghoul's bond will not attach to a priest, a witch hunter or an
            // angel, and a section that rolled one of those would do nothing at all while saying
            // nothing about why.
            String[] spec = one.trim().split(":", 2);
            String asked = spec[0].trim();
            Material hand = spec.length > 1 ? Material.matchMaterial(spec[1].trim()) : null;
            Player npc = null;
            // A named race is asked for once and taken as it comes. "super" is rolled again when
            // the plugin would not have it - a third of the rolls get thrown away, which is not a
            // reason for a section to come up with nobody.
            int tries = asked.equalsIgnoreCase("super") ? 12 : 1;
            for (int attempt = 0; attempt < tries && npc == null; attempt++) {
                String race = unroll(asked);
                if (race.isEmpty()) {
                    break;
                }
                Player stand = putPrey(player, seat, race, hand);
                if (stand == null) {
                    continue;
                }
                if (asked.equalsIgnoreCase("super") && !SuperNManager.get(stand).isSuper()) {
                    clearPrey(stand.getName());
                    continue;
                }
                npc = stand;
            }
            if (npc == null) {
                continue;
            }
            made.add(describe(npc, SuperNManager.get(npc).getType(), hand));
            seat++;
        }
        note(player, made.isEmpty() ? "§c没能放出异族靶子"
                : "§c放出了 " + made.size() + " 个：" + String.join("、", made)
                        + "（头上和 tab 里有种族名）");
    }

    /**
     * "random" is one of the eight races and "super" one of the ones the plugin calls
     * supernatural; anything else is itself.
     *
     * <p>The two names roll the same dice and differ only in who accepts the result. Which races
     * count as supernatural is asked of the plugin ({@link SuperNPlayer#isSuper()}) rather than
     * written down here, because the line has moved before: the four that report as people are
     * the ones whose race the plugin will not bond, cure or spread through.
     */
    private static String unroll(String race) {
        if (race.equalsIgnoreCase("random") || race.equalsIgnoreCase("super")) {
            return SUPERNATURAL.get(ThreadLocalRandom.current().nextInt(SUPERNATURAL.size()));
        }
        return race;
    }

    /**
     * Puts one stand-in up, in the seat asked for, as the race asked for.
     *
     * <p>Shared by every section that needs somebody to hit: the seating, the race, what he holds
     * and the label over his head are the same wherever he is used, and a section that rolled its
     * own would be the one that forgets one of them.
     */
    private Player putPrey(Player player, int seat, String race, Material hand) {
        String name = PREY_NAMES.get(seat);
        // Wearing the race he is about to become, and chosen before he exists: a skin is a
        // property of the profile the server player is built from, and nothing re-sends it
        // afterwards. A stand-in who is playing a race is read before he is listened to, and the
        // label over his head now says the same thing his face does.
        Object ghost = plugin.spawnGhost(name, player.getWorld(), Skins.of(race));
        Player npc = Bukkit.getPlayerExact(name);
        if (ghost == null || npc == null) {
            plugin.dropGhost(name);
            return null;
        }
        prey.put(name, ghost);
        preyOwner.put(name, player.getName());
        // Which room he was put out in, so that walking out of it takes him with it. Taken from the
        // player rather than from the seat: he is placed next to a board that may be read from a
        // corner, and the corner is the one spot in a room that is still the room.
        standInRooms.put(name, roomOf(player));
        // Guarded, and the reason is what an unguarded call did: a rule of the plugin's own threw
        // in the middle of this method, and the exception carried straight out through the section
        // and the click handler - so the seating, the teleport and the label below never ran, and
        // the stand-in was left standing at the world's own spawn, in another world, while every
        // board in the room went on saying there was nobody here. A section that loses its stand-in
        // to somebody else's bug has to say so rather than go quiet; he is placed and dressed
        // either way, and the console carries the cause.
        try {
            SuperNManager.convert(SuperNManager.get(npc), race);
        } catch (RuntimeException failed) {
            Bukkit.getLogger().warning("the stand-in " + name + " was put out, but turning him into "
                    + race + " threw: " + failed.getClass().getName()
                    + (failed.getMessage() == null ? "" : ": " + failed.getMessage()));
        }
        Location spot = seatFor(player, seat, taken);
        taken.add(spot);
        // Dressed before he is moved, not after. What is in his hand when a watcher starts
        // tracking him is part of the first thing that watcher is told about him, and being
        // teleported into somebody's view is where that happens - a sword put in his hand a tick
        // later has to be announced separately, and only the call below does that.
        npc.getInventory().setItemInMainHand(hand == null ? null : new ItemStack(hand));
        npc.teleport(spot);
        npc.setHealth(npc.getAttribute(Attribute.MAX_HEALTH).getValue());
        Nms.pushEquipment(npc);
        String as = SuperNManager.get(npc).getType();
        labelRace(npc, as);
        preyRace.put(name, as);
        return npc;
    }

    /**
     * Puts out one stand-in who will get up again where he falls.
     *
     * <p>A kill that turns somebody into something else can only be watched once on a target that
     * stays dead, and a vampire, a ghoul, a werewolf and an enderborn all work that way. This one
     * dies like anybody else - the plugin's own death handling runs, dice and all - and is put
     * back on his mark a moment later, so the next attempt has somewhere to happen. Human unless
     * the detail says otherwise, because a person is what these races turn.
     */
    private void spawnTarget(Player player, String spec) {
        clearPrey();
        taken.clear();
        String[] parts = spec == null || spec.isBlank() ? new String[0] : spec.trim().split(":", 2);
        String race = parts.length > 0 && !parts[0].isBlank() ? parts[0].trim() : "human";
        Material hand = parts.length > 1 ? Material.matchMaterial(parts[1].trim()) : null;
        Player npc = putPrey(player, 0, race, hand);
        if (npc == null) {
            note(player, "§c靶子起不来，这个环节暂时不能用");
            return;
        }
        revives.put(npc.getName(), npc.getLocation().clone());
        note(player, "§c放出了一个" + describe(npc, SuperNManager.get(npc).getType(), hand)
                + "：他可以被杀死，死后会在原地复活，所以能反复试");
    }

    /**
     * Swings the player's own item at the nearest stand-in, with the server applying the damage.
     *
     * <p>An event raised by hand is read by the plugin and hits nobody: the rule looks tested
     * while no health moves and nothing is knocked back. This goes through the server's damage
     * path instead - the same handlers, the same health bar, the same armour - and the arm goes
     * back, which is what a client draws. The detail is {@code MATERIAL[:DAMAGE]}; with no
     * material the player swings whatever is already in their hand.
     */
    private void useOnNpc(Player player, String spec) {
        // The first one, not the nearest: the switch that sends somebody at a target also hits the
        // first, and a section where "hit him" and "send somebody at him" land on different people
        // shows the tester a split that was not the one they set up.
        List<Player> here = preyOnline(player);
        if (here.isEmpty()) {
            note(player, "§c这一格还没有同伴：先右键旁边那块「放出一个」的牌");
            return;
        }
        Player npc = here.get(0);
        String[] parts = spec == null || spec.isBlank() ? new String[0] : spec.trim().split(":", 2);
        Material asked = parts.length > 0 ? Material.matchMaterial(parts[0].trim()) : null;
        double damage = parts.length > 1 ? Double.parseDouble(parts[1].trim()) : 1.0;
        // Asked of the player's own race before it is put in his hand. The kill room hands out a
        // diamond sword and a hundred damage so that one blow finishes the target - and a ghoul, a
        // witch hunter and an enderborn are all forbidden that sword, which the plugin answers by
        // zeroing the blow. Two of the four halls that have that room could therefore never kill
        // anything in it: the board promised "one blow and he is dead" and the target kept all
        // twenty of his health, for ever. An empty hand is legal for every race and still carries
        // this race's own attack bonus, so a race that may not hold what was asked swings nothing.
        Material item = legalWeapon(SuperNManager.get(player).getType(), asked, player);
        if (asked != null) {
            player.getInventory().setItemInMainHand(item == null ? null : new ItemStack(item));
        }
        if (!npc.getWorld().equals(player.getWorld())
                || npc.getLocation().distance(player.getLocation()) > 4) {
            npc.teleport(spotFor(player));
        }
        faceThe(player, npc);
        player.swingMainHand();
        land(player, npc, damage);
        note(player, "§7你拿" + (item == null ? "手里的东西" : item.name()) + "打在 "
                + npc.getName() + " 身上"
                + (npc.isDead() ? "§c（他倒了，稍后会在原地复活）"
                        : "§7（他还剩 " + trim(npc.getHealth()) + " 点血）"));
    }

    /**
     * Sends one stand-in at another and lets him land one blow.
     *
     * <p>Some rules only show when a second person takes the hit. The ghoul's bond is the clearest
     * one: it halves what its target is about to lose and moves the other half onto the ghoul,
     * who also pays power for the privilege - and none of that is visible until somebody other
     * than the player is being hit. The blow is the server's, so the ordinary handler runs and
     * the split happens exactly as it would between two people.
     *
     * <p>What the attacker swings is chosen by his own race rather than by the section, because
     * the plugin refuses a blow struck with a weapon the race may not hold - and a section that
     * handed out a sword regardless looked like a room that did nothing.
     */
    private void npcHitsNpc(Player player, String spec) {
        List<Player> here = preyOnline(player);
        if (here.isEmpty()) {
            note(player, "§c这一格还没有同伴：先右键旁边那块「放出一个」的牌");
            return;
        }
        Player target = here.get(0);
        Player attacker = here.size() > 1 ? here.get(1) : sparring(player);
        if (attacker == null || attacker.equals(target)) {
            note(player, "§c需要两个人才看得到分担：再点一次「放出一个」放第二个");
            return;
        }
        double damage = spec == null || spec.isBlank() ? 6.0 : Double.parseDouble(spec.trim());
        String race = SuperNManager.get(attacker).getType();
        attacker.teleport(spotFor(target));
        // Handed over only if his race may hold it. A ghoul, a witch hunter and a werewolf at
        // night all ban the sword, and the plugin answers a banned weapon by zeroing the blow -
        // so the section used to report "20.0 -> 20.0" and read as broken rather than as the rule
        // it was showing. An empty hand is legal for every race and still takes that race's attack
        // bonus, so falling back to it loses nothing but the sword.
        Material hand = legalWeapon(race, Material.IRON_SWORD, player);
        attacker.getInventory().setItemInMainHand(hand == null ? null : new ItemStack(hand));
        Nms.pushEquipment(attacker);
        faceThe(attacker, target);
        attacker.swingMainHand();
        double before = target.getHealth();
        land(attacker, target, damage);
        note(player, "§7" + describe(attacker, race, hand) + " 砍了 " + target.getName() + " 一下："
                + trim(before) + " → " + trim(target.getHealth())
                + " §7（看你自己掉了多少血、能量少了多少）");
    }

    /**
     * Brings two stand-ins up as the races named and has the second swing at the first, so
     * "does the blow land" can be asked and answered with the numbers rather than by eye.
     *
     * <p>Worth having because the answer is not just a number: a race that may not hold the
     * weapon it was handed deals nothing at all, and that cannot be told apart from a target that
     * was never really hit unless whoever reports it also says who swung and with what. One race
     * name does both - which is what the ghoul's own room wants.
     */
    String npcHitReport(String rest) {
        String[] parts = rest == null ? new String[0] : rest.trim().split("\\s+");
        Player tester = parts.length > 0 && !parts[0].isBlank()
                ? Bukkit.getPlayerExact(parts[0])
                : null;
        if (tester == null) {
            return "err no such player: " + (parts.length > 0 ? parts[0] : "");
        }
        String first = parts.length > 1 && !parts[1].isBlank() ? parts[1] : "vampire";
        String second = parts.length > 2 && !parts[2].isBlank() ? parts[2] : first;
        spawnNpcs(tester, first + "," + second);
        List<Player> here = preyOnline(tester);
        if (here.size() < 2) {
            return "err only " + here.size() + " stand-in(s) came up for " + first + "," + second;
        }
        Player target = here.get(0);
        Player attacker = here.get(1);
        double before = target.getHealth();
        npcHitsNpc(tester, "6");
        double after = target.getHealth();
        return "ok attacker=" + SuperNManager.get(attacker).getType()
                + " hand=" + attacker.getInventory().getItemInMainHand().getType()
                + " victim=" + SuperNManager.get(target).getType()
                + " " + trim(before) + "->" + trim(after)
                + " dealt=" + trim(before - after);
    }

    /** The living stand-ins this player's section brought up, in the world they are standing in. */
    private List<Player> preyOnline(Player player) {
        List<Player> found = new ArrayList<>();
        for (String name : prey.keySet()) {
            Player npc = Bukkit.getPlayerExact(name);
            if (npc != null && npc.isOnline() && !npc.isDead()
                    && npc.getWorld().equals(player.getWorld())) {
                found.add(npc);
            }
        }
        return found;
    }

    private Player nearestPrey(Player player) {
        Player best = null;
        double shortest = Double.MAX_VALUE;
        for (Player npc : preyOnline(player)) {
            double away = npc.getLocation().distanceSquared(player.getLocation());
            if (away < shortest) {
                shortest = away;
                best = npc;
            }
        }
        return best;
    }

    /**
     * A place for the nth stand-in to stand, clear of the ones already placed.
     *
     * <p>Every one of them used to be teleported to the same spot in front of the player, so three
     * different races arrived inside each other and there was nobody to tell apart. They now take
     * an arc: left, centre and right in front, and further back beyond the third - each position
     * checked for a floor and two clear blocks, and checked against the stand-ins already put
     * down, so a spot that is taken is not handed out twice.
     */
    /**
     * Where a stand-in stands so that he can reach the player who asked for him.
     *
     * <p>Not in front of the player, which is what this did and what was wrong with it. A player
     * asks for a stand-in by right-clicking a board, and a board is on a wall they are facing - so
     * the space in front of them is the far side of that wall. The stand-in arrived outside the
     * room, out of reach, and could not walk to the person he was supposed to attack: the section
     * looked broken rather than misplaced. Nothing caught it either, because "is there floor and
     * room for a body here" is true on both sides of a wall.
     *
     * <p>So the candidates are tried from the player's back first, then his sides, then his front,
     * and <em>every one of them has to be inside the room the player is standing in</em>. That last
     * condition is the real fix: the search can now fail, and failing means falling through to
     * {@link #spotFor}, which is a short walk from the middle of the room. In front of the player is
     * still allowed, because across a room it is the natural place for somebody to walk in from -
     * it is just no longer preferred over a place he can actually get to.
     */
    private Location seatFor(Player player, int seat, List<Location> taken) {
        // The same four questions every other summon answers, with the seat simply deciding which
        // of the places that passed is asked for: they come back nearest first and the ones already
        // handed out are passed in as taken, so the second stand-in is given the next place rather
        // than the first one again. What was here walked a fixed arc of its own and asked about
        // floor, room and overlap - but never whether the player could see the seat, nor whether a
        // stand-in could walk from it to him, which is the whole of what a stand-in is for.
        List<Location> spots = summonCandidates(player, taken, SUMMON_FAR);
        if (spots.isEmpty()) {
            return spotFor(player);
        }
        return spots.get(Math.min(seat, spots.size() - 1));
    }

    /** A horizontal unit vector, with a fallback when a player is looking straight up or down. */
    private static Vector flat(Vector direction) {
        Vector flat = direction.clone().setY(0);
        if (flat.lengthSquared() < 0.01) {
            return new Vector(0, 0, 1);
        }
        return flat.normalize();
    }

    /** Which room a player is standing in, or {@code null} when they are not in one. */
    private String roomOf(Player player) {
        Hall hall = plugin.hallOrNull();
        return hall == null ? null : hall.roomAt(player.getLocation());
    }

    /**
     * Whether a spot is inside the room being worked in.
     *
     * <p>Always true when there is no room to speak of - outside a hall there is nothing to be
     * inside of - but inside one this is what stops a stand-in being put down behind a wall.
     */
    private boolean insideRoom(String room, Location at) {
        Hall hall = plugin.hallOrNull();
        if (hall == null || room == null) {
            return true;
        }
        Hall.Box zone = hall.zone("room." + room);
        return zone == null || zone.holds(at);
    }

    // -- where a board may put what it calls up ----------------------------

    /**
     * How close to the player a summon may land, and how far away.
     *
     * <p>Nearer than this and the thing is inside him, which the server resolves by pushing it into
     * the nearest block that will take it - a wall, in a room whose boards are all on walls.
     * Further than this and it is behind something, or across the room facing away: a step that
     * says "watch this one" has to put it somewhere watching is possible from.
     */
    private static final double SUMMON_NEAR = 1.5;
    private static final double SUMMON_FAR = 6.0;

    /** How many blocks the walk search will look at before it gives up. */
    private static final int WALK_BUDGET = 4096;

    /**
     * The places a board's summon is allowed to stand.
     *
     * <p>Four things have to be true of one, and every board that calls something up now asks for
     * all four in one place. Before this each board decided for itself, and the ones that decided
     * nothing said so out loud: the general "let one out" board put its monster at the player's
     * position moved two and a half blocks along x, which is inside the wall whenever the player is
     * reading a board - the only time that board is ever used - and the truce step drew a ring
     * sixteen blocks out inside a room eight blocks wide, which stacked monsters in the wall and,
     * for the ones the clamp pushed past it, in the rooms next door.
     *
     * <p>The four, cheapest first:
     *
     * <ul>
     *   <li><b>Inside the room.</b> The player's own room, not the hall: rooms are separate builds
     *       with no way between them, so something standing in the next one is something the person
     *       who asked for it will never see. A player who is not in a room at all - the lobby, a
     *       corridor - gets no restriction, because there is no room to get wrong.
     *   <li><b>On floor, not in a block.</b> Feet on something solid and two blocks of air, which
     *       is what {@link #canStand} has always meant.
     *   <li><b>Walkable to the player.</b> A search over standing blocks outwards from the player,
     *       bounded by the room. A monster that cannot path to him is a monster that stands in a
     *       corner while the step promises it will come at him, and nothing about looking at the
     *       spawn point says so - only asking whether a route exists does.
     *   <li><b>In the player's line of sight.</b> Sampled along the line from his eyes, refusing an
     *       occluding block. Being in the room is not the same thing: the truce room has pillars
     *       in it, and a ring drawn through one puts monsters behind it.
     * </ul>
     */
    private List<Location> summonCandidates(Player player, List<Location> taken, double far) {
        Hall hall = plugin.hallOrNull();
        if (hall == null) {
            return List.of();
        }
        String room = roomOf(player);
        Hall.Box inside = room == null ? null : hall.zone("room." + room);
        List<Location> found = new ArrayList<>();
        for (Location at : walkable(player, inside)) {
            Location spot = middleOfBlock(at);
            if (seatTaken(taken, spot)) {
                continue;
            }
            double away = spot.distance(player.getLocation());
            if (away < SUMMON_NEAR || away > far) {
                continue;
            }
            if (!seeFrom(player, spot)) {
                continue;
            }
            found.add(spot);
        }
        found.sort(Comparator.comparingDouble(at -> at.distance(player.getLocation())));
        return found;
    }

    /**
     * The nearest place a board's summon may stand, or the old search when nothing passed.
     *
     * <p>The fallback is not a loophole, it is the one case the four questions cannot answer:
     * a player standing somewhere he cannot stand - in the pool, on a fence, mid-jump - has no
     * walk to search and would otherwise be unable to use a board at all. That is rare, and the
     * weaker search underneath still asks for floor inside the room.
     */
    private Location summonSpot(Player player, List<Location> taken, double far) {
        List<Location> spots = summonCandidates(player, taken, far);
        return spots.isEmpty() ? freeSpot(player) : spots.get(0);
    }

    /**
     * Everywhere a creature could stand and walk, starting from the player.
     *
     * <p>A walk over blocks rather than a pathfind: the question is not "which way is shortest" but
     * "is there a way at all", and a room is a flat box of a few hundred blocks - enumerating the
     * whole of it is cheaper than answering that for any single place in it would be. A step up or
     * down counts as well as a step across, since a room may have a raised floor or a lip, and
     * refusing those would rule out places a player can plainly walk to.
     *
     * <p>Includes the player's own block, so that a caller wanting "somewhere that is not under
     * him" filters on distance rather than on the search.
     */
    private List<Location> walkable(Player player, Hall.Box inside) {
        List<Location> found = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        Location start = player.getLocation().getBlock().getLocation();
        if (!canStand(start)) {
            // Nowhere to start from, so there is no honest answer to "what can reach him" - and
            // inventing one would hand out monsters behind walls with the search's blessing.
            return found;
        }
        Deque<Location> open = new ArrayDeque<>();
        seen.add(blockKey(start));
        open.add(start);
        while (!open.isEmpty() && found.size() < WALK_BUDGET) {
            Location at = open.poll();
            found.add(at);
            for (int[] step : WALK_STEPS) {
                Location next = at.clone().add(step[0], step[1], step[2]);
                if (inside != null && !inside.holds(next)) {
                    continue;
                }
                if (!canStand(next) || !seen.add(blockKey(next))) {
                    continue;
                }
                open.add(next);
            }
        }
        return found;
    }

    /** Across, and one block up or down: what a walk over a flat room with a lip in it looks like. */
    private static final int[][] WALK_STEPS = {
        {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1},
        {1, 1, 0}, {-1, 1, 0}, {0, 1, 1}, {0, 1, -1},
        {1, -1, 0}, {-1, -1, 0}, {0, -1, 1}, {0, -1, -1}};

    /**
     * Whether the player can see a place: no occluding block between his eyes and it.
     *
     * <p>Sampled every quarter block rather than ray-traced, because the question is about blocks
     * and a quarter block is well inside the width of one: a line that slips through the seam
     * between two blocks is not a line a player would call seeing something. Both ends are trimmed
     * - the eyes are inside the player's own head, and the far end is inside whatever the summon is
     * standing on.
     */
    private static boolean seeFrom(Player player, Location at) {
        Location eye = player.getEyeLocation();
        Location middle = at.clone().add(0, 0.9, 0);
        if (eye.getWorld() == null || !eye.getWorld().equals(middle.getWorld())) {
            return false;
        }
        Vector along = middle.toVector().subtract(eye.toVector());
        double length = along.length();
        if (length < 0.5) {
            return true;
        }
        along.multiply(1.0 / length);
        for (double walked = 0.6; walked < length - 0.4; walked += 0.25) {
            Block between = eye.clone().add(along.clone().multiply(walked)).getBlock();
            if (between.getBlockData().isOccluding()) {
                return false;
            }
        }
        return true;
    }

    /** Whether one of the places already handed out is this block. */
    private static boolean seatTaken(List<Location> taken, Location at) {
        for (Location one : taken) {
            if (sameBlock(one, at)) {
                return true;
            }
        }
        return false;
    }

    /** A block as one number, for the walk search's books. */
    private static long blockKey(Location at) {
        return ((long) (at.getBlockX() & 0x3FFFFFF) << 38)
                | ((long) (at.getBlockY() & 0xFFF) << 26)
                | (at.getBlockZ() & 0x3FFFFFF);
    }

    /**
     * Somewhere near the player that a small body fits in and that is inside their own room.
     *
     * <p>The hall spawns things beside the player - a chicken to kill, a pigman, a wolf - and every
     * one of them was put at the player's own position moved a block and a half on both axes. That
     * is fine in the middle of a room and it is inside the wall when the player is standing at one,
     * which is exactly where a player reading a board stands. A chicken in a block cannot be walked
     * to or hit, and killing it is the whole of what that step is for.
     *
     * <p><b>The player's own block is never a candidate, and that is the fix rather than a
     * detail.</b> It used to be the last resort, and a chicken put down where somebody is standing
     * is a chicken inside him: the server resolves that by pushing it out into the nearest block
     * willing to take it, which in a room whose boards are all on walls is the wall. The board in
     * the vampire hall read "let one out, it will not move, kill it for food" and the chicken
     * suffocated a second after it appeared - reported, reproduced, and this is what it was.
     *
     * <p>Behind the player first, because the board they are reading is on the wall in front of
     * them. Then their sides, then in front; two blocks out before one, so a body is not dropped
     * against somebody's shoulder. If none of those is floor, the whole ring around the player is
     * walked, so that a room whose only free floor is beside him still gets him out of the player's
     * own block rather than into it.
     */
    private Location freeSpot(Player player) {
        String room = roomOf(player);
        Location centre = player.getLocation();
        Vector back = flat(centre.getDirection()).multiply(-1);
        Vector side = new Vector(-back.getZ(), 0, back.getX());
        List<Vector> ways = List.of(back, side, side.clone().multiply(-1),
                back.clone().multiply(-1));
        for (double away : new double[] {2.5, 1.5, 3.5, 4.5}) {
            for (Vector way : ways) {
                Location at = middleOfBlock(centre.clone().add(way.clone().multiply(away)));
                if (!sameBlock(at, centre) && insideRoom(room, at) && canStand(at)) {
                    return at;
                }
            }
        }
        for (int radius = 1; radius <= 4; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        // The ring itself, not the square it encloses: nearer blocks were tried
                        // on the way out, and re-testing them here would only reorder the search.
                        continue;
                    }
                    Location at = middleOfBlock(centre.clone().add(dx, 0, dz));
                    if (insideRoom(room, at) && canStand(at)) {
                        return at;
                    }
                }
            }
        }
        return centre;
    }

    /** A place snapped to the middle of its block, which is where a body actually stands. */
    private static Location middleOfBlock(Location at) {
        Location middle = at.clone();
        middle.setX(at.getBlockX() + 0.5);
        middle.setZ(at.getBlockZ() + 0.5);
        return middle;
    }

    /** Whether two places are the same block of the same world. */
    private static boolean sameBlock(Location a, Location b) {
        return a.getWorld() != null && a.getWorld().equals(b.getWorld())
                && a.getBlockX() == b.getBlockX() && a.getBlockZ() == b.getBlockZ();
    }

    /**
     * Writes the stand-in's race beside his name, where everybody can see it.
     *
     * <p>His account name cannot be changed, so it is the scoreboard that does the talking: one
     * team per race, whose prefix is shown above his head and in the tab list. A section that hands
     * the player three races has to let them see which is which before they can use them.
     */
    private void labelRace(Player npc, String race) {
        String label = RACE_LABEL.getOrDefault(race, "§7" + race);
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = board.getTeam("mmr_" + race);
        if (team == null) {
            team = board.registerNewTeam("mmr_" + race);
            team.setPrefix(label + "§r ");
        }
        for (Team other : new ArrayList<>(board.getTeams())) {
            if (!other.equals(team) && other.hasEntry(npc.getName())) {
                other.removeEntry(npc.getName());
            }
        }
        team.addEntry(npc.getName());
    }

    /** What one stand-in is, for the message that says who turned up. */
    private String describe(Player npc, String race, Material hand) {
        String shown = race == null ? "?" : RACE_LABEL.getOrDefault(race, race);
        return shown + "§7(" + npc.getName() + (hand == null ? " 空手" : " 拿 " + hand.name()) + ")";
    }

    /**
     * Takes a stand-in's race label off him.
     *
     * <p>A team entry outlives the player: the account name goes back on the board the next time
     * somebody plays under it, and a stale prefix would have a human wearing "vampire". The label
     * is removed with him, not with the team - the team is the label, and it is worth keeping.
     */
    private static void unstamp(String name) {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        for (Team team : new ArrayList<>(board.getTeams())) {
            if (team.hasEntry(name)) {
                team.removeEntry(name);
            }
        }
    }

    /** Takes every stand-in the hall brought up as prey back out again. */
    private void clearPrey() {
        for (String name : new ArrayList<>(prey.keySet())) {
            clearPrey(name);
        }
    }

    /** Takes one stand-in out, if he is still on the books. */
    private void clearPrey(String name) {
        revives.remove(name);
        preyRace.remove(name);
        preyOwner.remove(name);
        standInRooms.remove(name);
        if (prey.remove(name) != null) {
            unstamp(name);
            plugin.dropGhost(name);
        }
    }

    /**
     * Prey that has been killed is taken out of the world.
     *
     * <p>A player who dies stays dead until they respawn, and a fake client never presses the
     * button - so without this the hall slowly fills with bodies that are still, technically,
     * online. The kill itself has already been counted by the plugin by this point. He stays on
     * the books until the moment he is actually taken out, so the death handler can still tell
     * that this death was a section being used rather than a test ending.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreyDeath(PlayerDeathEvent event) {
        String name = event.getEntity().getName();
        if (!prey.containsKey(name)) {
            return;
        }
        // Nothing he was carrying hits the floor. A stand-in killed while holding a pearl, a
        // sword and a label drops all of it, and the next person through the room picks it up -
        // which turns a demonstration into a loot run and puts items the hall did not hand out
        // into a player's inventory.
        event.getDrops().clear();
        event.setDroppedExp(0);
        Location home = revives.get(name);
        if (home != null) {
            // One of the targets that is meant to be killed again. Nothing puts a player back on
            // their feet but the respawn button on a client, and there is no client - so it is
            // pressed here, and he is put back on his mark with it. By this point the plugin has
            // already run its own death handling, which is the point of the section.
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Player again = Bukkit.getPlayerExact(name);
                if (again == null || !again.isOnline()) {
                    return;
                }
                again.spigot().respawn();
                again.teleport(home);
                again.setHealth(again.getAttribute(Attribute.MAX_HEALTH).getValue());
                again.setFoodLevel(20);
                again.setFireTicks(0);
                turnedOver(name, again);
            }, 2L);
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> clearPrey(name), 20L);
    }

    /**
     * Says so when a stand-in who was killed comes back as something else.
     *
     * <p>Four races turn the human they finish into one of their own, on a roll - and a roll is
     * invisible. The victim is a player with no client, so the message the plugin sends him goes
     * nowhere, and the label he wears is the one written when the section put him out: a stand-in
     * who has just been turned still reads as "human" over his head, in the tab list, and in the
     * death message of the next kill. Somebody killing him twenty times therefore sees twenty
     * identical humans and concludes the rule is broken, when what actually happened is that it
     * worked and nothing said so.
     *
     * <p>So the label is rewritten from what the plugin now says he is, and the person who put him
     * out is told when that is not what he started as. Nothing is rolled or decided here - the
     * plugin has already done both, on its own death handling - this only stops the result being
     * the one thing in the hall that nobody can see.
     */
    private void turnedOver(String name, Player again) {
        String now = SuperNManager.get(again).getType();
        String was = preyRace.get(name);
        labelRace(again, now);
        preyRace.put(name, now);
        if (was == null || was.equalsIgnoreCase(now)) {
            return;
        }
        String who = preyOwner.get(name);
        Player owner = who == null ? null : Bukkit.getPlayerExact(who);
        if (owner != null) {
            note(owner, "§a" + name + " 转化成功了：他现在是 "
                    + RACE_LABEL.getOrDefault(now, now)
                    + "§a（连着杀是为了等这一下；想再试一次，点「放出一个人类」会重新放一个）");
        }
    }

    /** Takes a shulker's bullet or a lingering effect with the entity that owned it. */
    private void removeSpectres(Entity entity) {
        for (Entity nearby : entity.getNearbyEntities(3, 3, 3)) {
            if (nearby instanceof org.bukkit.entity.Projectile) {
                nearby.remove();
            }
        }
    }

    /** Removes a set of entities through their remembered references, not through their ids. */
    private static void removeAll(Set<UUID> ids, Map<UUID, Entity> byId) {
        for (UUID id : new ArrayList<>(ids)) {
            Entity entity = byId.remove(id);
            if (entity == null) {
                entity = Bukkit.getEntity(id);
            }
            if (entity != null && entity.isValid()) {
                entity.remove();
            }
            ids.remove(id);
        }
    }

    /** A monster that teleports is a monster that leaves the test; endermen love doing this. */
    @EventHandler
    public void onMonsterTeleport(EntityTeleportEvent event) {
        if (hallMobs.containsKey(event.getEntity().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    // -- the stand-in ------------------------------------------------------

    /**
     * Hitting the stand-in reports what the hit did and keeps him on his feet while it lands.
     *
     * <p>Nothing is refused any more, not even the blow that finishes him. A target that cannot
     * die cannot show the thing half these sections are about - that a ghoul's immunity is a zero
     * rather than a statement, that a race which shrugs off a sword really does, that a kill is a
     * kill - and a stand-in who is topped up every time he is about to fall cannot be used to
     * test anything that begins with somebody dying. He dies like anybody else and stays down;
     * pressing the section's switch again is what puts a fresh one on his mark.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onSparHit(EntityDamageEvent event) {
        Player spar = sparAt(event.getEntity());
        if (spar == null) {
            return;
        }
        double damage = Math.max(0, event.getFinalDamage());
        if (event instanceof EntityDamageByEntityEvent byEntity) {
            try {
                showDamage(spar, byEntity, damage);
            } catch (RuntimeException ignored) {
                // The number is a nicety; the stand-in taking the hit is the test.
            }
        }
        // Held still, so a hit reports a damage number instead of launching him down the hall.
        spar.setVelocity(new Vector(0, 0, 0));
    }

    /** If the stand-in does die, he goes without dropping a player's worth of loot. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onSparDeath(EntityDeathEvent event) {
        if (sparAt(event.getEntity()) != null) {
            event.getDrops().clear();
            event.setDroppedExp(0);
        }
    }

    /** Whether an entity is the hall's stand-in. */
    private static Player sparAt(Entity entity) {
        return entity instanceof Player player && SPAR.equals(player.getName()) ? player : null;
    }

    private void showDamage(Entity target, EntityDamageByEntityEvent event, double damage) {
        String weapon = "徒手";
        if (event.getDamager() instanceof LivingEntity attacker
                && attacker.getEquipment() != null) {
            ItemStack item = attacker.getEquipment().getItemInMainHand();
            if (item.getType() != Material.AIR) {
                weapon = item.getType().name();
            }
        }
        floatNumber(target.getLocation().add(0, 1.4, 0), damage);
        if (event.getDamager() instanceof Player hitter) {
            note(hitter, "§7" + weapon + " → §f" + trim(damage) + " §7点伤害");
        }
    }

    /** A number that hangs in the air where the hit landed, and fades on its own. */
    private void floatNumber(Location at, double damage) {
        String text = damage <= 0 ? "§e0" : "§c-" + trim(damage);
        ArmorStand mark = at.getWorld().spawn(at, ArmorStand.class, spawned -> {
            spawned.setMarker(true);
            spawned.setSmall(true);
            spawned.setInvisible(true);
            spawned.setGravity(false);
            spawned.setInvulnerable(true);
            spawned.setCustomName(text);
            spawned.setCustomNameVisible(true);
            spawned.setPersistent(true);
        });
        marks.add(mark.getUniqueId());
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            marks.remove(mark.getUniqueId());
            mark.remove();
        }, 30L);
    }

    private static String trim(double damage) {
        return damage == Math.floor(damage)
                ? String.valueOf((int) damage)
                : String.format(Locale.ROOT, "%.1f", damage);
    }

    // -- the stand-in ------------------------------------------------------

    /**
     * Puts the stand-in in front of the player, where he can be seen and aimed at, and has him
     * take one swing.
     *
     * <p>The swing is the part that was missing: a target that never hits back tests nothing
     * about a fight, and half these sections are "let him hit you first, then watch". He swings
     * once and then stops, because a stand-in that keeps swinging is a fight, not a test.
     *
     * <p>What race he plays comes from the section, because it decides whether the section works at
     * all. Most of these steps are about damage - swing at him, read the number - and for those he
     * is a person, which is what a player is. A few are about an ability that only applies to a
     * supernatural target, and for those a person makes the ability refuse: the angel's paper
     * answers "Player is not a supernatural!" and the section reads as broken while working
     * perfectly. So a step that needs a target says which one, and everything else gets a person.
     */
    private void bringSpar(Player player, String race) {
        Player spar = stageSpar(player, new ItemStack(Material.IRON_SWORD));
        if (spar == null) {
            return;
        }
        SuperNPlayer asPlayer = SuperNManager.get(spar);
        String plays = race == null || race.isBlank() ? "human" : race;
        if ("human".equalsIgnoreCase(plays)) {
            humanise(spar);
        } else if (!plays.equalsIgnoreCase(asPlayer.getType())) {
            SuperNManager.convert(asPlayer, plays, SNConfigHandler.vampirePowerStart);
            spar.setHealth(spar.getAttribute(Attribute.MAX_HEALTH).getValue());
            Nms.pushEquipment(spar);
        }
        labelRace(spar, SuperNManager.get(spar).getType());
        note(player, "§f陪练（现在是 " + SuperNManager.get(spar).getType() + "）从 "
                + Math.round(spar.getLocation().distance(player.getLocation()))
                + " 格外走过来（他叫 " + SPAR + "），他会砍你一下");
        walkIn(spar, player, () -> strike(spar, player));
    }

    /**
     * Gets the stand-in ready and puts him down a few blocks away, facing the player.
     *
     * <p>He is put where the walk starts, not next to the player: what follows is a walk, and a
     * stand-in that appears on top of somebody and then announces a hit in chat is not a fight -
     * which is what every section that skipped this step looked like.
     *
     * <p>Not a note is written here: every caller has something different to say about what he is
     * about to do, and two notes for one switch is noise.
     */
    private Player stageSpar(Player player, ItemStack holds) {
        Player spar = sparring(player);
        if (spar == null) {
            return null;
        }
        spar.setHealth(spar.getAttribute(Attribute.MAX_HEALTH).getValue());
        spar.getInventory().setItemInMainHand(holds);
        // Into his hand and out to the people watching, in that order and both needed: the item on
        // his own inventory is what the plugin reads and what a swing is worth, and the push is
        // what the person running the test can actually see. Without the second one the section
        // says "the stand-in holds a wooden sword" and shows an iron one.
        Nms.pushEquipment(spar);
        // The same search as everything else a board calls up: floor in this room, a walk from it to
        // the player, and in his line of sight. The far end of it on purpose, because what follows
        // is a walk in - every other caller takes the nearest place, and this one takes the last.
        List<Location> starts = summonCandidates(player, List.of(), WALK_FROM);
        Location start = starts.isEmpty() ? freeSpot(player) : starts.get(starts.size() - 1);
        spar.teleport(facing(start, player.getLocation()));
        // Down on the books as this room's, the same as any other stand-in: a room he was left
        // standing in after its visitor walked out is a room still holding a person.
        standInRooms.put(SPAR, roomOf(player));
        return spar;
    }

    /** Puts the stand-in back to being a person; a spell may have left him something else. */
    private static void humanise(Player spar) {
        SuperNPlayer asPlayer = SuperNManager.get(spar);
        if (!asPlayer.isHuman()) {
            SuperNManager.cure(asPlayer);
        }
    }

    /**
     * The stand-in closes the distance a step at a time, then does whatever the section is about.
     *
     * <p>A player entity has no monster AI - there is nothing that walks a {@code ServerPlayer}
     * towards anybody - so he is moved a fraction of a block every other tick. Clients interpolate
     * between the position updates they are sent and swing the legs off the movement they see, so
     * this reads as a person walking over. One action when he arrives, and then he stays where he
     * is: a stand-in that keeps swinging is a fight, not a test.
     */
    private void walkIn(Player spar, Player player, Runnable onArrival) {
        cancelWalk();
        final double reach = 2.4;
        final int[] steps = {40};
        walkTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (steps[0]-- <= 0 || !spar.isOnline() || !spar.isValid() || !player.isValid()
                    || !spar.getWorld().equals(player.getWorld())) {
                cancelWalk();
                giveBackSword(spar);
                return;
            }
            Location target = player.getLocation();
            double away = spar.getLocation().distance(target);
            if (away > 12) {
                // The player has gone somewhere else entirely; walking after them is not useful.
                cancelWalk();
                giveBackSword(spar);
                return;
            }
            if (away <= reach) {
                cancelWalk();
                onArrival.run();
                return;
            }
            Vector step = target.toVector().subtract(spar.getLocation().toVector());
            step.setY(0);
            if (step.lengthSquared() < 1.0e-6) {
                cancelWalk();
                onArrival.run();
                return;
            }
            Location at = spar.getLocation();
            Location next = at.clone().add(step.normalize().multiply(WALK_STEP));
            // Same floor as the player: these halls are flat, and a stand-in that walks into a
            // step and gets stuck would be a worse test than one that slides along the floor.
            next.setY(target.getY());
            if (!canStand(next)) {
                // Something is in the way. Slide along it rather than walking into it - and if
                // there is no way past at all, do the thing from where he stands instead of
                // burying himself in a wall.
                Location alongX = new Location(next.getWorld(), next.getX(), next.getY(), at.getZ());
                Location alongZ = new Location(next.getWorld(), at.getX(), next.getY(), next.getZ());
                if (canStand(alongX)) {
                    next = alongX;
                } else if (canStand(alongZ)) {
                    next = alongZ;
                } else {
                    cancelWalk();
                    onArrival.run();
                    return;
                }
            }
            spar.teleport(facing(next, target));
        }, 0L, WALK_PERIOD);
    }

    /**
     * One strike, through the server's own combat path.
     *
     * <p>The arm goes back, then the target is damaged <em>by this player</em>: the server runs
     * its normal hit - the flash, the knockback, the sound, the invulnerability window - and the
     * plugin's own handlers decide what the blow is worth. The number reported is the health that
     * actually left the bar, so an immunity reads as a zero because there was nothing there to
     * read. Raising the event by hand and moving the health bar afterwards, which is what this
     * did before, produced a number with nothing behind it.
     */
    /**
     * One real blow from the stand-in: the arm goes back, the victim is damaged by him, and the
     * health that actually left the bar is what gets reported.
     *
     * <p>No teleport: he is already inside the three-block reach a player has, and moving him at
     * the moment of the swing is what made a lunge look like a pop-in. He only turns to face.
     *
     * <p>The damage is left where it landed - the point of these sections is comparing what two
     * weapons do, and a tester who is healed to full the instant they are hit cannot see the
     * difference. It is clipped so it can never take the last two points: a section that kills the
     * person running it has ended the test instead of showing them something, and the same button
     * is meant to be pressed over and over.
     */
    private void strike(Player spar, Player player) {
        ItemStack held = spar.getInventory().getItemInMainHand();
        double raw = BLADES.getOrDefault(held.getType(), 1.0);
        boolean clipped = player.getHealth() - raw < 2;
        if (clipped) {
            raw = Math.max(1.0, player.getHealth() - 2);
        }
        faceThe(spar, player);
        spar.swingMainHand();
        double before = player.getHealth();
        // Through `land`, not straight at the health bar. A player with no client behind them is
        // refused damage by the server, and the one thing that gets past it is a blow from somebody
        // in creative - so the blow is thrown the same way every other hit in this building is
        // thrown, and a stand-in standing in for the tester loses health for real.
        land(spar, player, raw);
        double lost = Math.max(0, before - player.getHealth());
        floatNumber(player.getLocation().add(0, 1.8, 0), lost);
        note(player, "§7陪练的" + bladeName(held.getType()) + "落在你身上：§f" + trim(lost)
                + " §7点伤害（他只砍这一下，血就这么少了）"
                + (lost <= 0 ? " §a（免疫）" : "")
                + (clipped ? " §7（你血不多了，这一下只算到剩 2 点）" : ""));
        // The weapon stays in his hand. A section that asks what a particular weapon does to you
        // is unreadable if the weapon is taken away the moment the blow lands: the tester looks
        // at him again and sees an empty hand, and concludes he never had it.
    }

    /** Turns him to look at the player without moving him. */
    private static void faceThe(Player spar, Player player) {
        Location at = spar.getLocation();
        Vector to = player.getLocation().toVector().subtract(at.toVector());
        to.setY(0);
        if (to.lengthSquared() > 1.0e-6) {
            Location look = at.clone().setDirection(to);
            spar.setRotation(look.getYaw(), look.getPitch());
        }
    }

    /** What a weapon is called in the messages a tester reads. */
    private static String bladeName(Material type) {
        return switch (type) {
            case WOODEN_SWORD -> "木剑";
            case STONE_SWORD -> "石剑";
            case IRON_SWORD -> "铁剑";
            case GOLDEN_SWORD -> "金剑";
            case DIAMOND_SWORD -> "钻石剑";
            case NETHERITE_SWORD -> "下界合金剑";
            case BOW -> "弓";
            default -> "剑";
        };
    }

    /** He puts the sword away when the test is over, so he is not left holding a weapon. */
    private void giveBackSword(Player spar) {
        if (spar.isOnline()) {
            spar.getInventory().setItemInMainHand(null);
            Nms.pushEquipment(spar);
        }
    }

    /** Stops a walk that is still going, so two sections cannot both be driving him. */
    private void cancelWalk() {
        if (walkTask != null) {
            walkTask.cancel();
            walkTask = null;
        }
    }

    /** A spot with its yaw turned towards somebody, so he walks in looking where he is going. */
    private static Location facing(Location at, Location target) {
        Location turned = at.clone();
        Vector look = target.toVector().subtract(at.toVector());
        look.setY(0);
        if (look.lengthSquared() > 1.0e-6) {
            turned.setDirection(look);
        }
        return turned;
    }

    /**
     * The stand-in for the world the player is in, brought up if he is not there already.
     *
     * <p>One per world, deliberately. The server leaves a player with no client invulnerable
     * after it moves them between worlds - it is waiting for a client to acknowledge a dimension
     * change that never comes - and an invulnerable stand-in is no stand-in at all. Being brought
     * up inside the world he is needed in avoids the move entirely.
     */
    private Player sparring(Player player) {
        World world = player.getWorld();
        Player spar = Bukkit.getPlayerExact(SPAR);
        if (spar == null || !spar.getWorld().equals(world)) {
            if (spar != null) {
                plugin.dropGhost(SPAR);
            }
            Object ghost = plugin.spawnGhost(SPAR, world, Skins.MASTER);
            spar = Bukkit.getPlayerExact(SPAR);
            if (spar == null) {
                if (ghost != null) {
                    plugin.dropGhost(SPAR);
                }
                note(player, "§c陪练起不来，这个环节暂时不能用");
                return null;
            }
        }
        if (spar.isDead()) {
            // He died where a section needed somebody to die, and nothing puts a stand-in back on
            // his feet: respawning is a button on a client, and there is no client. Pressing the
            // section's switch is that button - and a fresh press is also a fresh state, which is
            // what a section that changes his race or his health is asking for.
            spar.spigot().respawn();
            spar.teleport(player.getLocation());
        }
        return spar;
    }

    /** How far from the player a stand-in being brought round to his side may stand. */
    private static final double SPOT_FAR = 4.0;

    /**
     * A place beside the player that a stand-in can be sent to - and be hit from.
     *
     * <p>Held to the same four answers as a summon, because it is the same question: a stand-in
     * moved in by a board that means "now hit him" has to be somewhere the player can see him and
     * somewhere he could have walked from. What was here asked about floor, the room, and which way
     * the player was facing, and the second of those had a hole in it - a player reading a board is
     * standing against a wall, and "in front of him" is the far side of it.
     *
     * <p>Four blocks rather than the six a summon gets: this is the version that has to be in reach
     * of the player's arm, and every caller is about to hit something.
     */
    private Location spotFor(Player player) {
        List<Location> spots = summonCandidates(player, List.of(), SPOT_FAR);
        return spots.isEmpty() ? freeSpot(player) : spots.get(0);
    }

    /** Somewhere with two blocks of air and something solid underneath. */
    private static boolean canStand(Location at) {
        return at.getBlock().isPassable()
                && at.clone().add(0, 1, 0).getBlock().isPassable()
                && !at.clone().add(0, -1, 0).getBlock().isPassable();
    }

    /**
     * Lets the stand-in hit the player with a weapon, for real.
     *
     * <p>The ghoul's immunity to a diamond sword is decided on the <b>victim's</b> side and only
     * when the attacker is a player, so the hit has to come from a player: the hall keeps one
     * standing in the section. He walks over and swings like anybody else - the arm goes back, the
     * server runs its ordinary hit - and the number reported is the health that actually left the
     * bar, so an immunity reads as a zero because there was nothing to read. The player is put
     * back to full afterwards so the section can be run again without a death in between.
     */
    /**
     * Has the stand-in walk in and hit the player with one particular weapon.
     *
     * <p>This used to put him down next to the player and take the swing from there, with no walk
     * at all: the number appeared, but nobody had moved - which is what made these two switches
     * look broken. He walks in now and swings when he gets there, the same way the shared spar step
     * does, so what the player sees first is somebody coming at them.
     *
     * <p>The blow is the server's own - the arm goes back and {@code player.damage} runs - so the
     * number on the health bar is the plugin's answer, not a figure this code decided on.
     */
    private void testHit(Player player, String weaponName) {
        Material weapon = weaponName == null ? null : Material.matchMaterial(weaponName);
        if (weapon == null) {
            note(player, "§c这个环节没有配置武器");
            return;
        }
        Player spar = stageSpar(player, new ItemStack(weapon));
        if (spar == null) {
            return;
        }
        humanise(spar);
        note(player, "§f陪练拿着" + bladeName(weapon) + "从 "
                + Math.round(spar.getLocation().distance(player.getLocation()))
                + " 格外走过来，他会砍你这一下");
        walkIn(spar, player, () -> strike(spar, player));
    }

    /**
     * Sets a priest's spell up: the item into the player's hand, a stand-in to use it on, and the
     * walk in.
     *
     * <p><b>The spell itself is the player's to cast, and this deliberately does not cast it.</b>
     * Every priest spell fires when the priest <b>attacks a target</b> with the item in hand, and
     * for a while this board did that attack on the player's behalf the moment the stand-in
     * arrived: the board said "hold the feather and hit him", the player pressed the board, and the
     * one thing being tested - that a swing with this item in hand does this - was the one thing
     * the player never did. That is the one part of it you cannot work out by reading, either: a
     * feather is a piece of litter in an inventory, and only a blow struck with it says otherwise.
     * So this hands over the item, brings the stand-in in, and stops.
     *
     * <p>Eight of the item rather than one. A spell consumes what it was cast with, so a single
     * feather made this a board a player could use exactly once and then had to press again for -
     * which is a worse version of the same mistake, since the point is to try it and see.
     */
    private void testSpell(Player player, String itemName) {
        Material item = itemName == null ? null : Material.matchMaterial(itemName);
        if (item == null) {
            note(player, "§c这个环节没有配置法术物品");
            return;
        }
        Player spar = stageSpar(player, null);
        if (spar == null) {
            return;
        }
        boolean guardianAngel = item.name().equalsIgnoreCase(SNConfigHandler.priestSpellGuardianAngel);
        boolean humanOnly = Material.PAPER.equals(item) || guardianAngel;
        if (humanOnly) {
            humanise(spar);
        } else {
            SuperNManager.convert(SuperNManager.get(spar), "vampire",
                    SNConfigHandler.vampirePowerStart);
            spar.setHealth(6);
        }
        if (Material.FLINT.equals(item)) {
            spar.getInventory().setItemInMainHand(new ItemStack(item));
            Nms.pushEquipment(spar);
        }
        player.getInventory().setItemInMainHand(new ItemStack(item, SPELL_STACK));
        note(player, "§f陪练（现在是 " + SuperNManager.get(spar).getType() + "）从 "
                + Math.round(spar.getLocation().distance(player.getLocation()))
                + " 格外走过来；等他站定，拿手里的 " + item.name() + " 打他一下施法");
        walkIn(spar, player, () -> note(player, "§7陪练站好了——拿手里的 " + item.name()
                + " 打他一下（牌子只发道具，不替你出手）"));
    }

    /** How many of a spell's item a board hands out, so a spell can be tried more than once. */
    private static final int SPELL_STACK = 8;

    /**
     * The priest's swing, made on the player's behalf, for the probe.
     *
     * <p>Nothing in the hall calls this any more. A board hands over the item and brings a target
     * in, and the swing is the player's; this is the same swing made by the harness, so that a
     * scenario can assert what a spell does without a keyboard.
     *
     * <p>It goes through the server's own attack rather than raising a damage event by hand. A
     * priest's spells hang off a damage event, so a hand-raised one runs the handler while nothing
     * is hit - health never moves, nothing is knocked back, and a spell that was broken outright
     * still looks as though it worked. Real damage means the same handler, off the same kind of
     * event a player's own blow produces.
     */
    private void castAt(Player player, Player spar, Material item) {
        faceThe(player, spar);
        player.swingMainHand();
        land(player, spar, 1.0);
        note(player, "§7" + item.name() + " 打在 " + spar.getName() + " 身上（"
                + SuperNManager.get(spar).getType() + "，血 " + trim(spar.getHealth())
                + "）：看聊天栏和对方种族变成了什么");
    }

    /**
     * Casts one of the priest's spells at the stand-in the way the player's own blow would, for
     * the probe.
     *
     * <p>The counterpart of {@code mmp whack}, which needs both sides named: this one finds the
     * stand-in, puts the item into the caster's hand, and reports where the two of them ended up.
     * The last part is what a banish is worth asserting - the whole of its effect is a change of
     * place, and nothing else about it is observable from the caster's side.
     */
    String spellReport(String rest) {
        String[] args = rest.trim().split("\\s+");
        Player player = args.length > 0 ? Bukkit.getPlayerExact(args[0]) : null;
        Material item = args.length > 1 ? Material.matchMaterial(args[1]) : null;
        if (player == null) {
            return "err no such player: " + (args.length > 0 ? args[0] : "(none)");
        }
        if (item == null) {
            return "err usage: spell <player> <material>";
        }
        Player spar = Bukkit.getPlayerExact(SPAR);
        if (spar == null) {
            return "err no stand-in up - use the board that brings one in first";
        }
        player.getInventory().setItemInMainHand(new ItemStack(item, SPELL_STACK));
        Location before = spar.getLocation().clone();
        castAt(player, spar, item);
        Location after = spar.getLocation();
        return "ok " + player.getName() + " cast " + item.name() + " at " + spar.getName()
                + " race=" + SuperNManager.get(spar).getType()
                + " from=" + before.getBlockX() + "," + before.getBlockY() + "," + before.getBlockZ()
                + " to=" + after.getBlockX() + "," + after.getBlockY() + "," + after.getBlockZ()
                + " world=" + (after.getWorld() == null ? "?" : after.getWorld().getName());
    }

    // -- keeping the tests honest ------------------------------------------

    /**
     * A human killed by the hall's own zombie gets up a ghoul, without rolling for it. The plugin
     * hands a ghoul its truce with the undead, but the zombie that killed this player had already
     * locked on - a truce only stops a mob *choosing* a target - so the truce is applied here,
     * which is also what clears the target of whatever is still chewing.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        if (prey.containsKey(event.getEntity().getName())) {
            // The hall's own prey dying is a section being used, not a test ending: nothing about
            // races or truces applies to them, and the section cleans them up itself.
            return;
        }
        Entity killer = damageSource(event.getEntity());
        if (killer != null && hallMobs.containsKey(killer.getUniqueId())) {
            // The monster that did the killing is spent. What the death is worth is the plugin's
            // call, and only the plugin's: this used to hand out a ghoul for any hall monster's
            // kill, which made two things look true that are not - that a plain zombie can turn
            // somebody, and that the werewolf hall's wolf cannot do worse than it does.
            killer.remove();
            hallMobs.remove(killer.getUniqueId());
        }
        // A test that ended in death ends with the section cleaned up behind it.
        clearHallMobs(event.getEntity(), null);
        SuperNPlayer after = SuperNManager.get(event.getEntity());
        if (!after.isHuman()) {
            SuperNManager.truceRestore(after);
        }
    }

    private Entity damageSource(Player player) {
        if (!(player.getLastDamageCause() instanceof EntityDamageByEntityEvent damage)) {
            return null;
        }
        Entity damager = damage.getDamager();
        return damager instanceof LivingEntity ? damager : null;
    }

    @EventHandler
    public void onChestClosed(InventoryCloseEvent event) {
        Hall hall = plugin.hallOrNull();
        if (hall != null) {
            hall.refill(event.getInventory());
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        if (guard(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        if (guard(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    /**
     * A bucket is not a block place. Emptying one fires its own event and no
     * {@link BlockPlaceEvent}, which is how a player got water into a shrine room and washed the
     * room's way out off the wall.
     */
    @EventHandler
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (guard(bucketTarget(event))) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§7测试大厅里不能倒水");
        }
    }

    /** And filling one would take the demon's pool away, which is its whole test. */
    @EventHandler
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (guard(bucketTarget(event))) {
            event.setCancelled(true);
        }
    }

    /**
     * Where a bucket would land. The event carries two blocks - the one clicked and the one the
     * fluid would go into - and which of them is filled in depends on which constructor fired the
     * event: the short one leaves {@code getBlock()} null.
     */
    private static Location bucketTarget(PlayerBucketEvent event) {
        Block destination = event.getBlock() != null ? event.getBlock() : event.getBlockClicked();
        return destination == null ? null : destination.getLocation();
    }

    /** Water and lava run, and what they run into is a button on a pillar. */
    @EventHandler
    public void onFlow(BlockFromToEvent event) {
        if (guard(event.getToBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBurn(BlockBurnEvent event) {
        if (guard(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onSpread(BlockSpreadEvent event) {
        if (guard(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onIgnite(BlockIgniteEvent event) {
        if (guard(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    /**
     * A boat the hall put down does not break.
     *
     * <p>The boat in the ghoul's pool is the whole of one step: "a ghoul standing in water
     * dissolves, a ghoul sitting in a boat does not" is an exemption that cannot be tried without
     * one, and a board that tells a player to get into a boat is a board that gets the boat hit -
     * by the same sword they were just told to swing, sooner or later, and by accident. A boat
     * comes apart after a few dozen hits and drops itself as an item, so the room ends up with its
     * one prop lying on the floor and a step nobody can run.
     *
     * <p>Nothing about the step is about breaking boats, so they are simply not breakable. Every
     * blow aimed at one is refused, and that is deliberately the whole of the block rather than an
     * attack check: the same call is the collision damage a boat takes for being rowed into a
     * wall, which is the other way a tester loses this boat without meaning to.
     *
     * <p><b>This listens for {@code VehicleDamageEvent}, and it has to.</b> The first attempt
     * listened for {@code EntityDamageEvent}, which reads like the obvious event and is never
     * fired for a vehicle at all: the hierarchy is {@code VehicleDamageEvent extends VehicleEvent},
     * so a boat's damage is not a kind of entity damage as far as the event system is concerned,
     * and the handler was dead code that looked right. What caught it was {@code mmp boats} hitting
     * a second, untagged boat alongside the hall's: the control came apart and the hall's boat came
     * apart with it.
     */
    @EventHandler
    public void onBoatHit(VehicleDamageEvent event) {
        if (event.getVehicle().getScoreboardTags().contains(Hall.MOORED)) {
            event.setCancelled(true);
            boatBlowsRefused++;
        }
    }

    /**
     * How many blows at a hall boat have been refused so far.
     *
     * <p>Counted so that "a hall boat does not break" can be told apart from "nothing was ever
     * aimed at it": a smash that reports the boat still there and no blow refused has proved
     * nothing at all.
     */
    int boatBlowsRefused() {
        return boatBlowsRefused;
    }

    private int boatBlowsRefused;

    /** An enderman in a truce section does not get to take the section with it. */
    @EventHandler
    public void onChange(EntityChangeBlockEvent event) {
        if (guard(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent event) {
        if (guard(event.getBlock().getLocation())) {
            event.setCancelled(true);
            return;
        }
        preserve(event.blockList());
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        if (guard(event.getLocation())) {
            event.setCancelled(true);
            return;
        }
        preserve(event.blockList());
    }

    private void preserve(List<Block> blocks) {
        blocks.removeIf(block -> blocked(block.getLocation()));
    }

    @EventHandler
    public void onSpawn(CreatureSpawnEvent event) {
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.NATURAL
                && blocked(event.getLocation())) {
            event.setCancelled(true);
        }
    }

    private boolean blocked(Location at) {
        Hall hall = plugin.hallOrNull();
        return hall != null && hall.inside(at);
    }

    /** Refuses a change inside the hall, and puts back whatever was changed anyway. */
    private boolean guard(Location at) {
        Hall hall = plugin.hallOrNull();
        if (hall == null || at == null || !hall.inside(at)) {
            return false;
        }
        hall.repair();
        return true;
    }

    /**
     * The fast half of the hall's housekeeping: the guides come and go with their rooms, and
     * whatever a room let out is taken back when the room empties.
     *
     * <p>Half a second where the furniture sweep is two. A room that empties is a step that is
     * over, and the person who walked out of it is the person who would otherwise be looking at
     * what it left behind - a ring of forty monsters standing in a room nobody is in, a chicken
     * on the floor of a room somebody finished with an hour ago.
     */
    void sweep() {
        Hall hall = plugin.hallOrNull();
        if (hall == null) {
            return;
        }
        refreshGuides(hall);
        reclaimEmptyRooms(hall);
    }

    /**
     * Takes back everything that was let out in a room nobody is standing in any more.
     *
     * <p>The guides already came and went with their rooms, and the furniture is put back twice a
     * second - but what a section <em>let out</em> was only ever taken back when a switch was
     * pressed. Walking out of the room instead of pressing one left it all standing: the monsters
     * are persistent, the sweep re-anchors them to their marks, and a room that is empty of people
     * is a room whose step is over. That is the rule now, and it is applied to monsters, to the
     * chicken, and to the stand-in players alike.
     *
     * <p>The hall's own furniture is not a resident and does not count. A guide is a player
     * standing in the room he explains, and a stand-in is a player in the room whose step put him
     * there; counting either would mean no room ever read as empty, so both are excluded from the
     * count and swept by their own entries in the books.
     */
    private void reclaimEmptyRooms(Hall hall) {
        Set<String> occupied = occupiedRooms(hall);
        for (Map.Entry<UUID, String> entry : new ArrayList<>(propRooms.entrySet())) {
            String room = entry.getValue();
            if (room == null || occupied.contains(room)) {
                continue;
            }
            take(entry.getKey());
        }
        for (Map.Entry<String, String> entry : new ArrayList<>(standInRooms.entrySet())) {
            String room = entry.getValue();
            if (room == null || occupied.contains(room)) {
                continue;
            }
            takeStandIn(entry.getKey());
        }
        // The sparring partner is one per world, not one per room, so his room emptying is not the
        // only way he is spent: a player who leaves the world altogether leaves him behind, and a
        // player who comes back is served by a fresh one built where he is rather than by a
        // stand-in teleported across a dimension, which is the move that leaves a player with no
        // client invulnerable and useless as a target.
        Player spar = Bukkit.getPlayerExact(SPAR);
        if (spar != null && !visitorsIn(spar.getWorld())) {
            standInRooms.remove(SPAR);
            unstamp(SPAR);
            plugin.dropGhost(SPAR);
        }
    }

    /** The rooms somebody running a test is actually standing in. */
    private Set<String> occupiedRooms(Hall hall) {
        Set<String> occupied = new HashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (furniture(player.getName())) {
                continue;
            }
            String room = hall.roomAt(player.getLocation());
            if (room != null) {
                occupied.add(room);
            }
        }
        return occupied;
    }

    /** Whether a name belongs to the hall rather than to somebody running a test. */
    private boolean furniture(String name) {
        return SPAR.equals(name) || guides.containsKey(name)
                || prey.containsKey(name) || standInRooms.containsKey(name);
    }

    /** Whether anybody at all is running a test in a world. */
    private boolean visitorsIn(World world) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().equals(world) && !furniture(player.getName())) {
                return true;
            }
        }
        return false;
    }

    /** Takes one thing the hall let out back out of the world, and off the books. */
    private void take(UUID id) {
        propRooms.remove(id);
        hallMobs.remove(id);
        chickens.remove(id);
        Entity entity = hallEntities.remove(id);
        if (entity == null) {
            entity = Bukkit.getEntity(id);
        }
        if (entity != null) {
            removeSpectres(entity);
            entity.remove();
        }
    }

    /** Takes one of the hall's stand-in players back out, whether he is prey or the partner. */
    private void takeStandIn(String name) {
        standInRooms.remove(name);
        if (prey.containsKey(name)) {
            clearPrey(name);
            return;
        }
        if (SPAR.equals(name)) {
            unstamp(name);
            plugin.dropGhost(name);
        }
    }

    /**
     * What the hall is still holding in rooms nobody is in, for the probe.
     *
     * <p>An empty hall and a hall whose leavings were swept are the same picture from outside, and
     * "the room is clean" is exactly the claim that needed to be checkable rather than asserted:
     * a room's own step is over when nothing of it is left standing.
     */
    String leftoverReport() {
        Hall hall = plugin.hallOrNull();
        Map<String, Integer> byRoom = new TreeMap<>();
        for (String room : propRooms.values()) {
            byRoom.merge(String.valueOf(room), 1, Integer::sum);
        }
        List<String> occupied = new ArrayList<>();
        if (hall != null) {
            occupied.addAll(occupiedRooms(hall));
        }
        Collections.sort(occupied);
        List<String> standing = new ArrayList<>();
        standInRooms.forEach((name, room) -> standing.add(name + "@" + room));
        Collections.sort(standing);
        return "ok props=" + propRooms.size() + " byRoom=" + byRoom
                + " standins=" + standing + " occupied=" + occupied
                + " boarded=" + hallMobs.size() + " chickens=" + chickens.size();
    }

    /**
     * What a board would be allowed to call up here, for the probe.
     *
     * <p>The four questions are the kind that can be answered wrongly without looking wrong: "there
     * is floor over there" and "he can see the floor over there" stop being the same statement the
     * moment a room has a pillar in it, and "he can walk to it" is a third statement again. This
     * prints the counts rather than a verdict - how far a walk from the player reaches, how many
     * places pass all four, and, of the blocks around him that are inside his room, how many are
     * full of wall, how many he cannot see, and how many are simply too far.
     */
    String summonReport(String rest) {
        Hall hall = plugin.hallOrNull();
        String name = rest.trim().split("\\s+")[0];
        Player player = Bukkit.getPlayerExact(name);
        if (player == null) {
            return "err no such player: " + name;
        }
        if (hall == null) {
            return "err no hall on this server";
        }
        String room = roomOf(player);
        Hall.Box inside = room == null ? null : hall.zone("room." + room);
        List<Location> spots = summonCandidates(player, List.of(), SUMMON_FAR);
        int nearby = 0;
        int inWall = 0;
        int hidden = 0;
        int tooFar = 0;
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                Location at = middleOfBlock(player.getLocation().clone().add(dx, 0, dz));
                if (inside != null && !inside.holds(at)) {
                    continue;
                }
                nearby++;
                if (!canStand(at)) {
                    inWall++;
                } else if (!seeFrom(player, at)) {
                    hidden++;
                } else if (at.distance(player.getLocation()) > SUMMON_FAR) {
                    tooFar++;
                }
            }
        }
        return "ok " + player.getName() + " room=" + room
                + " reachable=" + walkable(player, inside).size()
                + " candidates=" + spots.size()
                + " nearby=" + nearby + " inWall=" + inWall
                + " hidden=" + hidden + " tooFar=" + tooFar
                + (spots.isEmpty() ? ""
                        : " nearest=" + trim(spots.get(0).distance(player.getLocation())));
    }

    /**
     * Every living thing in a world and which room it is standing in, for the probe.
     *
     * <p>This is the half of "inside the room" that the books cannot answer. A summon's room is
     * recorded where it was called up, which says where it was meant to be and nothing about where
     * it went: a monster pushed through a wall by a clamp is filed under the room it was called in
     * and standing in the one next door. Reading the world back is the only way to tell those two
     * apart - and a monster whose feet or head are inside a block is the other thing a ring drawn
     * in a room too small for it produces.
     */
    String strayReport(String rest) {
        Hall hall = plugin.hallOrNull();
        World world = Bukkit.getWorld(rest.trim());
        if (hall == null) {
            return "err no hall on this server";
        }
        if (world == null) {
            return "err no such world: " + rest.trim();
        }
        Map<String, Integer> byRoom = new TreeMap<>();
        List<String> stuck = new ArrayList<>();
        int loose = 0;
        for (LivingEntity entity : world.getLivingEntities()) {
            if (entity instanceof Player || entity instanceof Flying) {
                // Guidance stands in a room on purpose, and a ghast is not a thing that stands.
                continue;
            }
            String room = hall.roomAt(entity.getLocation());
            if (room == null) {
                loose++;
            } else {
                byRoom.merge(room, 1, Integer::sum);
            }
            Location at = entity.getLocation();
            if (!at.getBlock().getType().isAir()
                    || !at.clone().add(0, 1, 0).getBlock().getType().isAir()) {
                // Named, not just counted. "One of them is in a block" is a thing a fight between
                // forty monsters produces on its own - a silverfish burrows, an explosion throws -
                // and being able to tell that apart from "the ring was drawn through a wall" is the
                // whole difference between a summon that works and one that merely looks quiet.
                stuck.add(entity.getType().name() + "@" + at.getBlockX() + "," + at.getBlockY()
                        + "," + at.getBlockZ());
            }
        }
        return "ok " + world.getName() + " byRoom=" + byRoom
                + " outsideRooms=" + loose + " inBlocks=" + stuck.size()
                + (stuck.isEmpty() ? "" : " " + String.join(" ", stuck.subList(0, Math.min(6, stuck.size()))));
    }

    /**
     * The backstop, twice a second: the hall's own furniture goes back, and everything a section
     * let out goes back to its own section.
     */
    void tick() {
        Hall hall = plugin.hallOrNull();
        if (hall == null) {
            return;
        }
        List<String> before = hall.wrongFixtures();
        if (hall.repair()) {
            plugin.getLogger().info("put the hall's switches back: " + before);
        }
        if (hall.restoreBoats()) {
            plugin.getLogger().info("put a hall boat back on its mark");
        }
        for (Map.Entry<UUID, Location> entry : new HashMap<>(hallMobs).entrySet()) {
            Entity entity = hallEntities.get(entry.getKey());
            if (entity == null) {
                entity = Bukkit.getEntity(entry.getKey());
                if (entity != null) {
                    hallEntities.put(entry.getKey(), entity);
                }
            }
            if (entity != null && (entity.isDead() || !entity.isValid())) {
                hallMobs.remove(entry.getKey());
                hallEntities.remove(entry.getKey());
                continue;
            }
            if (entity != null && entity.getLocation().distanceSquared(entry.getValue()) > 400) {
                entity.teleport(entry.getValue());
            }
        }
        for (UUID id : new ArrayList<>(marks)) {
            if (Bukkit.getEntity(id) == null) {
                marks.remove(id);
            }
        }
    }
}
