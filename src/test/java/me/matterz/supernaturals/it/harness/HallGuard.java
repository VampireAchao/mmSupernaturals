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
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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

    /** The walk the stand-in is currently on, so a second section cannot start another. */
    private BukkitTask walkTask;

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
     * <p>A stand-in cannot normally be hit on this server build. It is a player whose client never
     * confirms anything, and the server keeps a flag on such a player that refuses damage - a flag
     * that comes straight back when it is cleared. There is exactly one exception inside the damage
     * path itself: a blow struck by a creative player is allowed past that flag. So the attacker is
     * borrowed as creative for the length of a single blow - changed and changed back inside one
     * tick, which is not something a client is told about - and the blow then travels the ordinary
     * route: the plugin's handlers, the health bar, the armour, the knockback.
     *
     * <p>If the health still does not move, the damage is taken off directly. That is the last
     * resort rather than the method: it is what a section shows when everything else refused, and
     * it is better than a switch that reports a spell landed while nobody lost anything.
     */
    private static void land(Player attacker, Player victim, double damage) {
        GameMode before = attacker.getGameMode();
        if (before != GameMode.CREATIVE) {
            attacker.setGameMode(GameMode.CREATIVE);
        }
        victim.damage(damage, attacker);
        if (attacker.getGameMode() != before) {
            attacker.setGameMode(before);
        }
    }

    /** Lifts the invulnerability the server puts on a player it has never heard from. */
    private void unfreezeStandIns() {
        for (Player npc : standIns()) {
            if (npc.isInvulnerable()) {
                npc.setInvulnerable(false);
                Nms.arrive(npc);
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
            case USE_ON_NPC -> useOnNpc(player, trigger.detail());
            case SPAWN_TARGET -> spawnTarget(player, trigger.detail());
            case NPC_HITS_NPC -> npcHitsNpc(player, trigger.detail());
            case SPAR -> bringSpar(player);
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
        }
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
        Location at = player.getLocation().add(2.5, 0, 0.5);
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
        taken.clear();
        int seat = 0;
        for (String one : wanted) {
            if (seat >= PREY_NAMES.size()) {
                break;
            }
            // "vampire" is one; "vampire:NETHERRACK" is one holding netherrack, which is what a
            // spell that needs both sides to be holding something asks for; "random" is one of
            // the supernatural races, for a section whose point is that any of them will do.
            String[] spec = one.trim().split(":", 2);
            String race = unroll(spec[0].trim());
            Material hand = spec.length > 1 ? Material.matchMaterial(spec[1].trim()) : null;
            if (race.isEmpty()) {
                continue;
            }
            Player npc = putPrey(player, seat, race, hand);
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

    /** "random" means one of the supernatural races; anything else is itself. */
    private static String unroll(String race) {
        if (race.equalsIgnoreCase("random")) {
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
        Object ghost = plugin.spawnGhost(name, player.getWorld());
        Player npc = Bukkit.getPlayerExact(name);
        if (ghost == null || npc == null) {
            plugin.dropGhost(name);
            return null;
        }
        prey.put(name, ghost);
        preyOwner.put(name, player.getName());
        SuperNManager.convert(SuperNManager.get(npc), race);
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
            note(player, "§c这一格还没有同伴：先点旁边那个「放出一个」按钮");
            return;
        }
        Player npc = here.get(0);
        String[] parts = spec == null || spec.isBlank() ? new String[0] : spec.trim().split(":", 2);
        Material item = parts.length > 0 ? Material.matchMaterial(parts[0].trim()) : null;
        double damage = parts.length > 1 ? Double.parseDouble(parts[1].trim()) : 1.0;
        if (item != null) {
            player.getInventory().setItemInMainHand(new ItemStack(item));
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
     */
    private void npcHitsNpc(Player player, String spec) {
        List<Player> here = preyOnline(player);
        if (here.isEmpty()) {
            note(player, "§c这一格还没有同伴：先点旁边那个「放出一个」按钮");
            return;
        }
        Player target = here.get(0);
        Player attacker = here.size() > 1 ? here.get(1) : sparring(player);
        if (attacker == null || attacker.equals(target)) {
            note(player, "§c需要两个人才看得到分担：再点一次「放出一个」放第二个");
            return;
        }
        double damage = spec == null || spec.isBlank() ? 6.0 : Double.parseDouble(spec.trim());
        attacker.teleport(spotFor(target));
        attacker.getInventory().setItemInMainHand(new ItemStack(Material.IRON_SWORD));
        Nms.pushEquipment(attacker);
        faceThe(attacker, target);
        attacker.swingMainHand();
        double before = target.getHealth();
        land(attacker, target, damage);
        note(player, "§7" + attacker.getName() + "砍了 " + target.getName() + " 一下："
                + trim(before) + " → " + trim(target.getHealth())
                + " §7（看你自己掉了多少血、能量少了多少）");
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
    private Location seatFor(Player player, int seat, List<Location> taken) {
        Location centre = player.getLocation();
        Vector facing = centre.getDirection().setY(0);
        if (facing.lengthSquared() < 0.01) {
            facing = new Vector(0, 0, 1);
        }
        facing.normalize();
        Vector right = new Vector(-facing.getZ(), 0, facing.getX());
        for (int attempt = 0; attempt < 9; attempt++) {
            double across = ((seat + attempt) % 3 - 1) * 1.8;
            double ahead = 3.0 + ((seat + attempt) / 3) * 2.2;
            Location at = centre.clone()
                    .add(facing.clone().multiply(ahead))
                    .add(right.clone().multiply(across));
            if (canStand(at) && clearOf(at, taken)) {
                return at;
            }
        }
        return spotFor(player);
    }

    /** True when nothing has already been put down within a block of that spot. */
    private static boolean clearOf(Location at, List<Location> taken) {
        for (Location other : taken) {
            if (other.getWorld().equals(at.getWorld())
                    && other.distanceSquared(at) < 1.0) {
                return false;
            }
        }
        return true;
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
        String shown = RACE_LABEL.getOrDefault(race, race);
        return shown + "§7(" + npc.getName() + (hand == null ? "" : " 拿 " + hand.name()) + ")";
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
     */
    private void bringSpar(Player player) {
        Player spar = stageSpar(player, new ItemStack(Material.IRON_SWORD));
        if (spar == null) {
            return;
        }
        humanise(spar);
        note(player, "§f陪练从 " + Math.round(spar.getLocation().distance(player.getLocation()))
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
        Location start = approachFrom(player);
        spar.teleport(facing(start, player.getLocation()));
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
        player.damage(raw, spar);
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
        if (spar == null || !spar.getWorld().equals(world)) {
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
        player.getInventory().setItemInMainHand(new ItemStack(item));
        note(player, "§f陪练（现在是 " + SuperNManager.get(spar).getType() + "）从 "
                + Math.round(spar.getLocation().distance(player.getLocation()))
                + " 格外走过来，走近了就打他一下施法");
        walkIn(spar, player, () -> castAt(player, spar, item));
    }

    /** One attack from the priest at the stand-in, which is how every spell fires. */
    /**
     * Lands the spell on the stand-in through the server's own damage path.
     *
     * <p>A priest's spells are triggered by attacking somebody with the item, and the handler that
     * decides what happens reads a damage event. Raising that event by hand makes the handler run
     * while nothing is actually hit - the target's health never moves, nothing is knocked back,
     * and a spell that turned out to be broken would still look as though it worked. This deals
     * real damage, so the same handler runs off the same kind of event a real blow produces.
     */
    private void castAt(Player player, Player spar, Material item) {
        faceThe(player, spar);
        player.swingMainHand();
        land(player, spar, 1.0);
        note(player, "§7" + item.name() + " 打在 " + spar.getName() + " 身上（"
                + SuperNManager.get(spar).getType() + "，血 " + trim(spar.getHealth())
                + "）：看聊天栏和对方种族变成了什么");
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
