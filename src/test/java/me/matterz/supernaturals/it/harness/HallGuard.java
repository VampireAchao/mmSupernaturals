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
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.entity.Zombie;
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
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
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
 * The behaviour of the {@link Hall}: what its switches do, what its dummies answer, and the rules
 * that keep a test a test - the props cannot be walked off with, the halls cannot be mined, built
 * in, poured on or blown up, and nothing hostile gets to interrupt whoever is trying a race.
 *
 * <p><b>The dummy is the point of a damage section.</b> It is an armour stand that cannot move,
 * cannot be dressed or taken, cannot be destroyed and cannot be knocked about; hitting it prints
 * the damage on the spot, after every modifier the plugin applies, because the number a player
 * wants to see is the number they would really deal.
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

    /** Marks everything a section let out, so a stray one can be recognised. */
    private static final String HALL_MOB = "mmhall";

    /**
     * The one monster that is not called up, for the same reason {@link Hall} leaves it out of
     * its own list: an ender dragon is a boss with a multi-part body, not a mob that can be
     * asked whether it respects a truce.
     */
    private static final Set<String> UNROOMABLE = Set.of("ENDER_DRAGON");

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
                if (restore(player)) {
                    note(player, "§a已恢复人类，可以留在本区继续试");
                }
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
            case DIP_WATER -> teleport(player, hall.point("demon.pool"), "§9下池子了，等着淹死");
            case SPAWN_CHICKEN -> spawnChicken(player);
            case SPAWN_MONSTERS -> spawnMonsters(player, trigger.race());
            case CLEAR_MONSTERS -> clearHallMobs(player, "§a已清空大厅里的怪物");
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
            case SUN_SPOT -> teleport(player, hall.point(trigger.race() + ".sun"),
                    "§6站到露天台上：这一格头顶没有屋顶");
            case RETURN_START -> teleport(player, hall.point("spawn." + trigger.race()),
                    "§a回到这一族的入口");
            case GO_NETHER -> teleport(player, hall.point("spawn.demon_nether"),
                    "§4这里是下界，穿好皮革再下去");
            case RETURN_OVERWORLD -> teleport(player, hall.point("spawn.demon"),
                    "§a回到主世界的恶魔大厅");
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
        clearHallMobs(player, null);
        player.getInventory().clear();
        restore(player);
        if (where != null) {
            player.teleport(where);
            player.setRespawnLocation(where, true);
        }
        note(player, what);
    }

    // -- what the hall lets out --------------------------------------------

    /**
     * The ghoul's way in is dying, and the plugin rolls dice for it in the wild. Here the hall
     * supplies the killer itself, so the kill is certain to be the last one this human takes.
     */
    private void prepareGhoul(Player player, Hall hall) {
        clearHallMobs(player, null);
        Location at = player.getLocation().add(1.5, 0, 1.5);
        Zombie zombie = player.getWorld().spawn(at, Zombie.class, spawned -> {
            spawned.setCustomName("大厅食尸鬼");
            spawned.setCustomNameVisible(true);
            spawned.setRemoveWhenFarAway(false);
            spawned.setPersistent(true);
            spawned.setTarget(player);
        });
        keepIn(hall, zombie, player.getLocation());
        note(player, "§2僵尸出现了，站着别动");
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
     * peace with - so the truce can be watched instead of taken on faith. The few that cannot be
     * kept in a room are named in the message rather than spawned.
     */
    private void spawnMonsters(Player player, String race) {
        clearHallMobs(player, null);
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
        if (message != null && player != null) {
            note(player, message);
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

    // -- the dummy ---------------------------------------------------------

    /**
     * Hitting a dummy reports what the hit did and then takes it back: the stand is a measuring
     * instrument, so it is never damaged, never dies and never moves - including from the
     * knockback the hit would have carried, which cancelling the event also removes.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDummyHit(EntityDamageEvent event) {
        ArmorStand dummy = dummyAt(event.getEntity());
        if (dummy == null) {
            return;
        }
        double damage = Math.max(0, event.getFinalDamage());
        // Cancel before anything else: an armour stand is destroyed by a single hit that gets
        // through and drops itself on the floor, which is exactly what a damage section cannot
        // afford. Reporting is done afterwards so a failure in the reporting cannot let the hit
        // land.
        event.setCancelled(true);
        dummy.setVelocity(new Vector(0, 0, 0));
        if (!dummy.isDead() && dummy.isValid()) {
            AttributeInstance max = dummy.getAttribute(Attribute.MAX_HEALTH);
            if (max != null && dummy.getHealth() < max.getValue()) {
                dummy.setHealth(max.getValue());
            }
        }
        if (event instanceof EntityDamageByEntityEvent byEntity) {
            try {
                showDamage(dummy, byEntity, damage);
            } catch (RuntimeException ignored) {
                // The number is a nicety; the stand surviving is the test.
            }
        }
    }

    /**
     * Last line of defence for a dummy. If one is destroyed anyway - a rule the guard does not
     * know about, an explosion that arrives before the cancel - the remains are cleared off the
     * floor and a fresh one is put straight back on the mark.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDummyDeath(EntityDeathEvent event) {
        ArmorStand dummy = dummyAt(event.getEntity());
        if (dummy == null) {
            return;
        }
        event.getDrops().clear();
        event.setDroppedExp(0);
    }

    private ArmorStand dummyAt(Entity entity) {
        Hall hall = plugin.hallOrNull();
        if (hall == null || !(entity instanceof ArmorStand stand)) {
            return null;
        }
        return hall.dummies().containsValue(stand) ? stand : null;
    }

    private void showDamage(ArmorStand dummy, EntityDamageByEntityEvent event, double damage) {
        String weapon = "徒手";
        if (event.getDamager() instanceof LivingEntity attacker
                && attacker.getEquipment() != null) {
            ItemStack item = attacker.getEquipment().getItemInMainHand();
            if (item.getType() != Material.AIR) {
                weapon = item.getType().name();
            }
        }
        floatNumber(dummy.getLocation().add(0, 1.4, 0), damage);
        if (event.getDamager() instanceof Player hitter) {
            hitter.sendMessage("§7" + weapon + " → §f" + trim(damage) + " §7点伤害");
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

    /** A dummy cannot be dressed, undressed or carried: the section is about hitting it. */
    @EventHandler
    public void onDummyManipulate(PlayerArmorStandManipulateEvent event) {
        if (dummyAt(event.getRightClicked()) != null) {
            event.setCancelled(true);
        }
    }

    // -- the stand-in ------------------------------------------------------

    /**
     * Puts the stand-in in front of the player, where it can be seen and aimed at. It only
     * exists while a section needs it, and it is left standing (not removed the moment the hit
     * lands) because half of these tests are "watch what happens to him".
     */
    private void bringSpar(Player player) {
        Player spar = sparring(player);
        if (spar == null) {
            return;
        }
        spar.teleport(spotInFrontOf(player));
        spar.setHealth(spar.getAttribute(Attribute.MAX_HEALTH).getValue());
        note(player, "§f陪练就位（他叫 " + SPAR + "）");
    }

    /** The stand-in, created if it is not up yet. Null when the server would not have it. */
    private Player sparring(Player player) {
        Player spar = Bukkit.getPlayerExact(SPAR);
        if (spar != null) {
            return spar;
        }
        Object ghost = plugin.spawnGhost(SPAR);
        if (ghost == null) {
            note(player, "§c陪练起不来，这个环节暂时不能用");
            return null;
        }
        spar = Bukkit.getPlayerExact(SPAR);
        if (spar == null) {
            plugin.dropGhost(ghost);
            note(player, "§c陪练起不来，这个环节暂时不能用");
        }
        return spar;
    }

    /** Two blocks in front of the player, so the stand-in is plainly in view. */
    private Location spotInFrontOf(Player player) {
        Location at = player.getLocation();
        Vector facing = at.getDirection().setY(0);
        if (facing.lengthSquared() < 0.01) {
            facing = new Vector(0, 0, 1);
        }
        facing.normalize().multiply(2);
        return at.clone().add(facing);
    }

    /**
     * Lets the stand-in hit the player with a weapon, for real.
     *
     * <p>The ghoul's immunity to a diamond sword is decided on the <b>victim's</b> side and only
     * when the attacker is a player, so the hit has to come from a player: the hall keeps one
     * standing in the section. The damage is applied rather than merely announced - the number
     * on the health bar is the test - and the player is put back to full afterwards so the
     * section can be run again without a death in between.
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
        spar.teleport(spotInFrontOf(player));
        spar.getInventory().setItemInMainHand(new ItemStack(weapon));
        spar.setHealth(spar.getAttribute(Attribute.MAX_HEALTH).getValue());

        // The event is what the plugin decides on - it reads the weapon, and for a ghoul it
        // answers zero for a diamond sword - and the health is moved by hand afterwards, because
        // `Player#damage` refuses on a player with no client: the server keeps such a player
        // invulnerable after a move between worlds until a real client answers a packet that
        // never comes. A real player is never in that state; the number they see is the same
        // one this reports.
        EntityDamageByEntityEvent event = new EntityDamageByEntityEvent(spar, player,
                EntityDamageEvent.DamageCause.ENTITY_ATTACK, BLADES.getOrDefault(weapon, 1.0));
        Bukkit.getPluginManager().callEvent(event);
        double lost = event.isCancelled() ? 0 : Math.max(0, event.getFinalDamage());
        double before = player.getHealth();
        if (lost > 0) {
            player.setHealth(Math.max(0, before - lost));
        }
        floatNumber(player.getLocation().add(0, 1.8, 0), lost);
        note(player, "§7" + weapon.name() + " 打在你身上：§f" + trim(lost) + " §7点伤害"
                + (lost <= 0 ? " §a（免疫）" : ""));
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
        spar.teleport(spotInFrontOf(player));
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
        Entity killer = damageSource(event.getEntity());
        if (killer != null && hallMobs.containsKey(killer.getUniqueId())) {
            killer.remove();
            hallMobs.remove(killer.getUniqueId());
            SuperNPlayer snplayer = SuperNManager.get(event.getEntity());
            if (snplayer.isHuman()) {
                SuperNManager.sendMessage(snplayer, "Your body dies... You feel a deep hatred for the living.");
                SuperNManager.convert(snplayer, "ghoul", SNConfigHandler.ghoulPowerStart);
            }
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
     * The backstop, twice a second: the hall's own furniture goes back, every dummy goes back to
     * where it was put, and everything a section let out goes back to its own section.
     */
    void tick() {
        Hall hall = plugin.hallOrNull();
        if (hall == null) {
            return;
        }
        List<String> before = hall.wrongFixtures();
        if (hall.repair()) {
            plugin.getLogger().info("put the hall's switches back: " + before
                    + " dummies=" + hall.dummyReport());
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
        for (Map.Entry<String, ArmorStand> entry : new HashMap<>(hall.dummies()).entrySet()) {
            // A dummy that was destroyed anyway - an armour stand drops itself when a hit gets
            // through - is replaced on its own mark, so a damage section never runs out of one.
            ArmorStand stand = entry.getValue();
            Location anchor = hall.point("dummy." + entry.getKey());
            if (anchor != null && (stand.isDead() || !stand.isValid())) {
                hall.replaceDummy(entry.getKey(), anchor);
            }
        }
        for (UUID id : new ArrayList<>(marks)) {
            if (Bukkit.getEntity(id) == null) {
                marks.remove(id);
            }
        }
    }
}
