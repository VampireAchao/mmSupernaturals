package me.matterz.supernaturals.it.harness;

import me.matterz.supernaturals.SuperNPlayer;
import me.matterz.supernaturals.SupernaturalsPlugin;
import me.matterz.supernaturals.io.SNConfigHandler;
import me.matterz.supernaturals.manager.HunterManager;
import me.matterz.supernaturals.manager.SuperNManager;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
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
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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

    /** The floating numbers, so a sweep does not mistake them for something that escaped. */
    private final Set<UUID> marks = new HashSet<>();

    /** The names the hall's test prey answer to, in the order a section asks for them. */
    private static final List<String> PREY_NAMES = List.of("HallPrey1", "HallPrey2", "HallPrey3");

    /** The stand-ins the hall has brought up as prey, by name, so they can be taken back out. */
    private final Map<String, Object> prey = new HashMap<>();

    /** The walk the stand-in is currently on, so a second section cannot start another. */
    private BukkitTask walkTask;

    HallGuard(HarnessPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * A joining player starts at the lobby. The world spawn is already there and the spawn radius
     * is zero, so this is normally a no-op - it is here because a player who arrives outside the
     * hall has nowhere to go and no way to ask for directions. The stand-in is left where it is.
     */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Hall hall = plugin.hallOrNull();
        if (hall == null || SPAR.equals(event.getPlayer().getName())) {
            return;
        }
        if (!hall.inside(event.getPlayer().getLocation())) {
            event.getPlayer().teleport(hall.lobbySpawn());
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
        switch (trigger.kind()) {
            case ENTER_LOBBY -> backToHuman(player, hall, hall.lobbySpawn(), "§a回到测试大厅");
            case LEAVE_HALL -> backToHuman(player, hall, hall.outsideSpawn(),
                    "§a已离开测试大厅，踩返回垫的按钮可以回来");
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
            case SPAR -> bringSpar(player);
            case HURT -> hurt(player);
            case SELF_KILL -> selfKill(player);
            case SPAWN_NPCS -> spawnNpcs(player, trigger.detail());
            case SUN_SPOT -> teleport(player, hall.point(trigger.race() + ".sun"),
                    "§6站到露天台上：这一格头顶没有屋顶");
            case RETURN_START -> teleport(player, hall.point("spawn." + trigger.race()),
                    "§a回到这一族的入口");
            case GO_NETHER -> teleport(player,
                    hall.point("spawn." + trigger.race() + "_nether"),
                    "§4这里是下界：在这里死才算数");
            case RETURN_OVERWORLD -> teleport(player, hall.point("spawn." + trigger.race()),
                    "§a回到主世界的那条长廊");
            case LEFT_CLICK -> leftClick(player);
            case BOUNTY -> bounty(player);
        }
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
        note(player, what);
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
        world.setStorm(raining);
        world.setThundering(false);
        note(player, raining ? "§9这个大厅开始下雨了" : "§7这个大厅的雨停了");
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
        for (int index = 0; index < 3; index++) {
            double angle = index * 2.1;
            Location at = player.getLocation().clone()
                    .add(Math.cos(angle) * 4, 0, Math.sin(angle) * 4);
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
        Location at = player.getLocation().add(1.5, 0, 1.5);
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
        Location at = player.getLocation().add(1.5, 0, 1.5);
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
        }
        Location at = player.getLocation().add(1.5, 0, 1.5);
        Chicken chicken = player.getWorld().spawn(at, Chicken.class, spawned -> {
            spawned.setAware(false);
            spawned.setPersistent(true);
            spawned.setRemoveWhenFarAway(false);
            spawned.setCustomName("测试鸡");
            spawned.setCustomNameVisible(true);
        });
        chickens.add(chicken.getUniqueId());
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
        Hall hall = plugin.hallOrNull();
        Location centre = player.getLocation();
        List<String> refused = new ArrayList<>();
        int placed = 0;
        for (EntityType type : wanted) {
            if (type == null) {
                continue;
            }
            if (UNROOMABLE.contains(type.name())) {
                refused.add(type.name());
                continue;
            }
            if (type == EntityType.ENDER_DRAGON) {
                if (placeDragon(hall, race, centre)) {
                    placed++;
                } else {
                    refused.add(type.name());
                }
                continue;
            }
            double ring = 3 + (placed / 6) * 2;
            double angle = placed * 1.1;
            Location at = clamp(hall, race,
                    centre.clone().add(Math.cos(angle) * ring, 0, Math.sin(angle) * ring));
            try {
                Entity spawned = centre.getWorld().spawnEntity(at, type);
                spawned.setPersistent(true);
                keepIn(hall, spawned, centre);
                placed++;
            } catch (RuntimeException notPlaceable) {
                refused.add(type.name());
            }
        }
        note(player, "§c召唤了 " + placed + " 只怪物"
                + (refused.isEmpty() ? "" : "；室内放不下的：" + String.join("、", refused)));
    }

    /** A dragon, in the middle of the section with room to be a dragon in. */
    private boolean placeDragon(Hall hall, String race, Location centre) {
        Location at = clamp(hall, race, centre.clone()).add(0, 6, 0);
        try {
            EnderDragon dragon = centre.getWorld().spawn(at, EnderDragon.class);
            dragon.setPhase(EnderDragon.Phase.HOVER);
            dragon.setPersistent(true);
            keepIn(hall, dragon, centre);
            return true;
        } catch (RuntimeException | LinkageError notPlaceable) {
            plugin.getLogger().warning("could not summon the ender dragon: " + notPlaceable);
            return false;
        }
    }

    /** Keeps a spawn point inside its hall, however many neighbours are already standing there. */
    private Location clamp(Hall hall, String race, Location at) {
        Hall.Box zone = hall == null ? null : hall.zone("hall." + race);
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
        for (int index = 0; index < wanted.length && index < PREY_NAMES.size(); index++) {
            // "vampire" is one; "vampire:NETHERRACK" is one holding netherrack, which is what a
            // spell that needs both sides to be holding something asks for.
            String[] spec = wanted[index].trim().split(":", 2);
            String race = spec[0].trim();
            Material hand = spec.length > 1 ? Material.matchMaterial(spec[1].trim()) : null;
            if (race.isEmpty()) {
                continue;
            }
            String name = PREY_NAMES.get(index);
            Object ghost = plugin.spawnGhost(name, player.getWorld());
            Player npc = Bukkit.getPlayerExact(name);
            if (ghost == null || npc == null) {
                plugin.dropGhost(name);
                continue;
            }
            prey.put(name, ghost);
            SuperNManager.convert(SuperNManager.get(npc), race);
            npc.teleport(spotFor(player));
            npc.setHealth(npc.getAttribute(Attribute.MAX_HEALTH).getValue());
            npc.getInventory().setItemInMainHand(hand == null ? null : new ItemStack(hand));
            made.add(hand == null ? race : race + "(" + hand.name() + ")");
        }
        note(player, made.isEmpty() ? "§c没能放出异族靶子"
                : "§c放出了 " + made.size() + " 个异族：" + String.join("、", made)
                        + "，用武器杀掉他们");
    }

    /** Takes every stand-in the hall brought up as prey back out again. */
    private void clearPrey() {
        for (String name : new ArrayList<>(prey.keySet())) {
            clearPrey(name);
        }
    }

    /** Takes one stand-in out, if he is still on the books. */
    private void clearPrey(String name) {
        if (prey.remove(name) != null) {
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
        Bukkit.getScheduler().runTaskLater(plugin, () -> clearPrey(name), 20L);
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
     * Hitting the stand-in reports what the hit did and keeps him standing.
     *
     * <p>The damage is no longer cancelled: a target that cannot lose health can show a number
     * but cannot show what a section is really about - that the health bar moves, that a wolf in
     * wolf form loses nothing, that a ghoul's immunity is a zero rather than a statement. What is
     * still refused is his <em>death</em>: a hit that would finish him puts him back to full
     * instead, so a damage section has a target for as long as it wants one.
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
                // The number is a nicety; the stand-in staying up is the test.
            }
        }
        spar.setVelocity(new Vector(0, 0, 0));
        if (event.getFinalDamage() >= spar.getHealth()) {
            event.setCancelled(true);
            spar.setHealth(spar.getAttribute(Attribute.MAX_HEALTH).getValue());
            if (event instanceof EntityDamageByEntityEvent byEntity
                    && byEntity.getDamager() instanceof Player hitter) {
                hitter.sendMessage("§7陪练被打空了，已经刷新到满血");
            }
        }
    }

    /** If the stand-in does die anyway, he goes without dropping a player's worth of loot. */
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
     */
    private void bringSpar(Player player) {
        Player spar = sparring(player);
        if (spar == null) {
            return;
        }
        SuperNPlayer asPlayer = SuperNManager.get(spar);
        if (!asPlayer.isHuman()) {
            // A section that converted him - a priest's spell bench, the angel's cure step -
            // leaves him something other than a person; the stand-in is meant to be one.
            SuperNManager.cure(asPlayer);
        }
        spar.setHealth(spar.getAttribute(Attribute.MAX_HEALTH).getValue());
        spar.getInventory().setItemInMainHand(new ItemStack(Material.IRON_SWORD));
        // He walks in from a clear spot rather than appearing on top of the player, because a
        // target that materialises and then announces a hit in chat is not a fight - and that is
        // exactly what this looked like: the number arrived with nobody swinging.
        Location start = approachFrom(player);
        spar.teleport(facing(start, player.getLocation()));
        note(player, "§f陪练从 " + Math.round(start.distance(player.getLocation()))
                + " 格外走过来（他叫 " + SPAR + "），他会砍你一下");
        walkIn(spar, player);
    }

    /**
     * The stand-in closes the distance a step at a time, then strikes once and stops.
     *
     * <p>A player entity has no monster AI - there is nothing that walks a {@code ServerPlayer}
     * towards anybody - so he is moved a fraction of a block every other tick. Clients interpolate
     * between the position updates they are sent and swing the legs off the movement they see, so
     * this reads as a person walking over. One strike, and then he stays where he is: a stand-in
     * that keeps swinging is a fight, not a test.
     */
    private void walkIn(Player spar, Player player) {
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
                strike(spar, player);
                return;
            }
            Vector step = target.toVector().subtract(spar.getLocation().toVector());
            step.setY(0);
            if (step.lengthSquared() < 1.0e-6) {
                cancelWalk();
                strike(spar, player);
                return;
            }
            Location at = spar.getLocation();
            Location next = at.clone().add(step.normalize().multiply(WALK_STEP));
            // Same floor as the player: these halls are flat, and a stand-in that walks into a
            // step and gets stuck would be a worse test than one that slides along the floor.
            next.setY(target.getY());
            if (!canStand(next)) {
                // Something is in the way. Slide along it rather than walking into it - and if
                // there is no way past at all, strike from where he stands instead of burying
                // himself in a wall.
                Location alongX = new Location(next.getWorld(), next.getX(), next.getY(), at.getZ());
                Location alongZ = new Location(next.getWorld(), at.getX(), next.getY(), next.getZ());
                if (canStand(alongX)) {
                    next = alongX;
                } else if (canStand(alongZ)) {
                    next = alongZ;
                } else {
                    cancelWalk();
                    strike(spar, player);
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
    private void strike(Player spar, Player player) {
        ItemStack held = spar.getInventory().getItemInMainHand();
        double raw = BLADES.getOrDefault(held.getType(), 1.0);
        spar.teleport(facing(spotFor(player), player.getLocation()));
        spar.swingMainHand();
        double before = player.getHealth();
        player.damage(raw, spar);
        double lost = Math.max(0, before - player.getHealth());
        floatNumber(player.getLocation().add(0, 1.8, 0), lost);
        note(player, "§7陪练的剑落在你身上：§f" + trim(lost) + " §7点伤害（他只砍这一下）"
                + (lost <= 0 ? " §a（免疫）" : ""));
        giveBackSword(spar);
    }

    /** He puts the sword away when the test is over, so he is not left holding a weapon. */
    private void giveBackSword(Player spar) {
        if (spar.isOnline()) {
            spar.getInventory().setItemInMainHand(null);
        }
    }

    /** Stops a walk that is still going, so two sections cannot both be driving him. */
    private void cancelWalk() {
        if (walkTask != null) {
            walkTask.cancel();
            walkTask = null;
        }
    }

    /**
     * A clear spot a few blocks off to walk in from.
     *
     * <p>The player's own facing first, then the four sides, and the longest run of standable
     * floor wins - so he arrives along an open line instead of pushing out of a wall.
     */
    private Location approachFrom(Player player) {
        Location at = player.getLocation();
        List<Vector> ways = new ArrayList<>();
        Vector looking = at.getDirection().setY(0);
        if (looking.lengthSquared() > 0.01) {
            ways.add(looking.normalize());
        }
        for (BlockFace way : new BlockFace[] {BlockFace.NORTH, BlockFace.SOUTH,
                BlockFace.EAST, BlockFace.WEST}) {
            ways.add(new Vector(way.getModX(), 0, way.getModZ()));
        }
        Location best = at.clone();
        int bestClear = 0;
        for (Vector way : ways) {
            int clear = 0;
            for (int distance = 1; distance <= WALK_FROM; distance++) {
                Location spot = at.clone().add(way.clone().multiply(distance));
                if (!canStand(spot)) {
                    break;
                }
                clear = distance;
            }
            if (clear > bestClear) {
                bestClear = clear;
                best = at.clone().add(way.clone().multiply(clear));
            }
        }
        return best;
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
        if (spar != null && spar.getWorld().equals(world)) {
            return spar;
        }
        if (spar != null) {
            plugin.dropGhost(SPAR);
        }
        Object ghost = plugin.spawnGhost(SPAR, world);
        spar = Bukkit.getPlayerExact(SPAR);
        if (spar == null) {
            if (ghost != null) {
                plugin.dropGhost(SPAR);
            }
            note(player, "§c陪练起不来，这个环节暂时不能用");
            return null;
        }
        return spar;
    }

    /**
     * A place in front of the player that a person can actually stand in.
     *
     * <p>Two blocks along the player's facing was all this used to do, which put the stand-in
     * inside the wall whenever the player happened to be looking at one - and a stand-in inside a
     * wall is one nobody can see or hit. Nearer spots are tried next, then the player's own feet,
     * then the four sides.
     */
    private Location spotFor(Player player) {
        Location at = player.getLocation();
        Vector facing = at.getDirection().setY(0);
        if (facing.lengthSquared() < 0.01) {
            facing = new Vector(0, 0, 1);
        }
        facing.normalize();
        for (int distance = 2; distance >= 1; distance--) {
            Location candidate = at.clone().add(facing.clone().multiply(distance));
            if (canStand(candidate)) {
                return candidate;
            }
        }
        if (canStand(at)) {
            return at;
        }
        for (BlockFace side : new BlockFace[] {BlockFace.EAST, BlockFace.WEST,
                BlockFace.NORTH, BlockFace.SOUTH}) {
            Location candidate = at.clone().add(side.getModX(), 0, side.getModZ());
            if (canStand(candidate)) {
                return candidate;
            }
        }
        return at;
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
    private void testHit(Player player, String weaponName) {
        Material weapon = weaponName == null ? null : Material.matchMaterial(weaponName);
        if (weapon == null) {
            note(player, "§c这个环节没有配置武器");
            return;
        }
        Player spar = sparring(player);
        if (spar == null) {
            return;
        }
        spar.teleport(spotFor(player));
        spar.getInventory().setItemInMainHand(new ItemStack(weapon));
        spar.setHealth(spar.getAttribute(Attribute.MAX_HEALTH).getValue());
        spar.swingMainHand();
        double before = player.getHealth();
        player.damage(BLADES.getOrDefault(weapon, 1.0), spar);
        double lost = Math.max(0, before - player.getHealth());
        floatNumber(player.getLocation().add(0, 1.8, 0), lost);
        note(player, "§7" + weapon.name() + " 打在你身上：§f" + trim(lost) + " §7点伤害"
                + (lost <= 0 ? " §a（免疫）" : ""));
        giveBackSword(spar);
        player.setHealth(player.getAttribute(Attribute.MAX_HEALTH).getValue());
    }

    /**
     * Casts one of a priest's spells at the stand-in.
     *
     * <p>Every priest spell fires when the priest <b>attacks a target</b> with the item in hand.
     * Most act on a supernatural target; healing and Guardian Angel require a human. The spell's
     * own messages go to the caster, which is the player standing here.
     */
    private void testSpell(Player player, String itemName) {
        Material item = itemName == null ? null : Material.matchMaterial(itemName);
        if (item == null) {
            note(player, "§c这个环节没有配置法术物品");
            return;
        }
        Player spar = sparring(player);
        if (spar == null) {
            return;
        }
        spar.teleport(spotFor(player));
        spar.setHealth(spar.getAttribute(Attribute.MAX_HEALTH).getValue());
        boolean guardianAngel = item.name().equalsIgnoreCase(SNConfigHandler.priestSpellGuardianAngel);
        boolean humanOnly = Material.PAPER.equals(item) || guardianAngel;
        if (humanOnly) {
            SuperNPlayer target = SuperNManager.get(spar);
            if (!target.isHuman()) {
                SuperNManager.cure(target);
            }
        } else {
            SuperNManager.convert(SuperNManager.get(spar), "vampire",
                    SNConfigHandler.vampirePowerStart);
            spar.setHealth(6);
        }
        if (Material.FLINT.equals(item)) {
            spar.getInventory().setItemInMainHand(new ItemStack(item));
        }
        player.getInventory().setItemInMainHand(new ItemStack(item));
        EntityDamageByEntityEvent event = new EntityDamageByEntityEvent(player, spar,
                EntityDamageEvent.DamageCause.ENTITY_ATTACK, 1.0);
        Bukkit.getPluginManager().callEvent(event);
        note(player, "§7施法：" + item.name() + " → §f" + spar.getName()
                + " §7（" + SuperNManager.get(spar).getType() + "）");
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
