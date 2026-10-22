/*
 * Supernatural Players Plugin for Bukkit
 * Copyright (C) 2011  Matt Walker <mmw167@gmail.com>
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 */

package me.matterz.supernaturals.manager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.logging.Level;

import me.matterz.supernaturals.SuperNPlayer;
import me.matterz.supernaturals.SupernaturalsPlugin;
import me.matterz.supernaturals.io.SNConfigHandler;
import me.matterz.supernaturals.util.ArrowUtil;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.Openable;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.Vector;

public class HunterManager extends HumanManager {

	public HunterManager() {
		super();
	}

	private HashMap<Arrow, String> arrowMap = new HashMap<Arrow, String>();
	private HashMap<SuperNPlayer, String> hunterMap = new HashMap<SuperNPlayer, String>();
	/**
	 * Which pull owns each player right now, by player id, and the number that names it.
	 *
	 * <p>A pull is a thing with an owner, not a flag on a player. Held as "this list contains
	 * him", it had two faults that only showed up when a hunter shot twice. A pull removed itself
	 * from the list two seconds after it ended, so a shot that landed inside that window - and any
	 * shot fired while a pull was still running - spent its power and then found itself refused,
	 * with nothing said. And a pull that ended without removing itself kept him flagged for the
	 * rest of the session, so every later shot was refused too. Naming the pull means a new shot
	 * takes the player over instead of being turned away, and a pull that has been taken over can
	 * see that it has, and go quietly.
	 */
	private final Map<UUID, Long> grappleOwners = new HashMap<UUID, Long>();
	private long nextGrappleToken = 1L;

	/** What each grapple arrow cost, so a pull that moves nobody can hand it back. */
	private final Map<Arrow, Integer> grappleSpent = new HashMap<Arrow, Integer>();
	/**
	 * When a hunter may fire a power arrow again, by player id.
	 *
	 * <p>A time rather than a list of players: the cooldown belongs to the shot, not to the
	 * object that took it. Held as a player it was removed by a scheduled task that could
	 * outlive the player - one who logged out before it ran left his entry behind for ever -
	 * and it made the ability un-shootable after a relogin, because the new player object
	 * is not the one in the list.
	 */
	private final Map<UUID, Long> powerReadyAt = new HashMap<UUID, Long>();
	private ArrayList<Location> hallDoors = new ArrayList<Location>();
	private ArrayList<SuperNPlayer> playerInvites = new ArrayList<SuperNPlayer>();
	private static ArrayList<SuperNPlayer> bountyList = new ArrayList<SuperNPlayer>();

	private String arrowType = "normal";

	// -------------------------------------------- //
	// Damage Events //
	// -------------------------------------------- //

	@Override
	public double victimEvent(EntityDamageEvent event, double damage) {
		if (event.getCause().equals(DamageCause.FALL)) {
			damage -= SNConfigHandler.hunterFallReduction;
			if (damage < 0) {
				damage = 0;
			}
		}
		return damage;
	}

	@Override
	public boolean shootArrow(Player shooter, EntityShootBowEvent event) {
		boolean cancelled = shoot(shooter);
		if (cancelled) {
			return true;
		}
		return false;
	}

	@Override
	public double damagerEvent(EntityDamageByEntityEvent event, double damage) {
		if (event.getDamager() instanceof Projectile) {
			Entity victim = event.getEntity();
			if (event.getDamager() instanceof Arrow) {
				Arrow arrow = (Arrow) event.getDamager();
				if (getArrowMap().containsKey(arrow)) {
					arrowType = getArrowType(arrow);
				} else {
					arrowType = "normal";
				}
			}
			if (arrowType.equalsIgnoreCase("power")) {
				damage += damage * SNConfigHandler.hunterPowerArrowDamage;
			} else if (arrowType.equalsIgnoreCase("fire")) {
				victim.setFireTicks(SNConfigHandler.hunterFireArrowFireTicks);
			}
			if (SNConfigHandler.debugMode) {
				SupernaturalsPlugin.log("Arrow event with " + damage
						+ " damage!");
			}
			return damage;
		} else {
			Player pDamager = (Player) event.getDamager();
			SuperNPlayer snDamager = SuperNManager.get(pDamager);
			ItemStack item = pDamager.getItemInHand();

			// Check Weapons and Modify Damage
			if (item != null) {
				if (SNConfigHandler.hunterWeapons.contains(item.getType())) {
					if (SNConfigHandler.debugMode) {
						SupernaturalsPlugin.log(pDamager.getName()
								+ " was not allowed to use "
								+ item.getType().toString());
					}
					SuperNManager.sendMessage(snDamager, "WitchHunters cannot use this weapon!");
					return 0;
				}
			}
			if (SNConfigHandler.debugMode) {
				SupernaturalsPlugin.log("Bow Event with " + damage + " damage");
			}
			return damage;
		}
	}

	@Override
	public void deathEvent(Player player) {
		super.deathEvent(player);
		SuperNPlayer snplayer = SuperNManager.get(player);
		SuperNManager.alterPower(snplayer, -SNConfigHandler.hunterDeathPowerPenalty, "You died!");
	}

	@Override
	public void killEvent(Player pDamager, SuperNPlayer damager, SuperNPlayer victim) {
		if (victim == null) {
			if (SNConfigHandler.hunterKillPowerCreatureGain > 0) {
				SuperNManager.alterPower(damager, SNConfigHandler.hunterKillPowerCreatureGain, "Creature death!");
			}
		} else {
			if (victim.getPower() > SNConfigHandler.hunterKillPowerPlayerGain) {
				SuperNManager.alterPower(damager, SNConfigHandler.hunterKillPowerPlayerGain, "Player killed!");
				if (HunterManager.checkBounty(victim)) {
					SuperNManager.alterPower(damager, SNConfigHandler.hunterBountyCompletion, "Bounty Fulfilled!");
					HunterManager.removeBounty(victim);
					HunterManager.addBounty();
				}
			} else {
				SuperNManager.sendMessage(damager, "You cannot gain power from a player with no power themselves.");
			}
		}
	}

	// -------------------------------------------- //
	// Interact //
	// -------------------------------------------- //

	@Override
	public boolean playerInteract(PlayerInteractEvent event) {

		Action action = event.getAction();
		Player player = event.getPlayer();
		SuperNPlayer snplayer = SuperNManager.get(player);

		if (action.equals(Action.LEFT_CLICK_AIR)
				|| action.equals(Action.LEFT_CLICK_BLOCK)) {
			if (player.getItemInHand() == null) {
				return false;
			}

			if (player.getItemInHand().getType().equals(Material.BOW)) {
				changeArrowType(snplayer);
				return true;
			}
		}
		return false;
	}

	// -------------------------------------------- //
	// Armor //
	// -------------------------------------------- //

	@Override
	public void armorCheck(Player player) {
		PlayerInventory inv = player.getInventory();
		ItemStack helmet = inv.getHelmet();
		ItemStack chest = inv.getChestplate();
		ItemStack leggings = inv.getLeggings();
		ItemStack boots = inv.getBoots();

		if (helmet != null) {
			if (!SNConfigHandler.hunterArmor.contains(helmet.getType())) {
				inv.setHelmet(null);
				dropItem(player, helmet);
			}
		}
		if (chest != null) {
			if (!SNConfigHandler.hunterArmor.contains(chest.getType())) {
				inv.setChestplate(null);
				dropItem(player, chest);
			}
		}
		if (leggings != null) {
			if (!SNConfigHandler.hunterArmor.contains(leggings.getType())) {
				inv.setLeggings(null);
				dropItem(player, leggings);
			}
		}
		if (boots != null) {
			if (!SNConfigHandler.hunterArmor.contains(boots.getType())) {
				inv.setBoots(null);
				dropItem(player, boots);
			}
		}
	}

	// -------------------------------------------- //
	// Bounties //
	// -------------------------------------------- //

	public static ArrayList<SuperNPlayer> getBountyList() {
		return bountyList;
	}

	public static boolean checkBounty(SuperNPlayer snplayer) {
		if (bountyList.contains(snplayer)) {
			return true;
		}
		return false;
	}

	public static boolean removeBounty(SuperNPlayer snplayer) {
		if (bountyList.contains(snplayer)) {
			bountyList.remove(snplayer);
			return true;
		}
		return false;
	}

	public static void addBounty() {
		List<SuperNPlayer> targets = SuperNManager.getSupernaturals();
		boolean bountyFound = false;
		Random generator = new Random();
		int count = 0;

		while (!bountyFound) {
			int randomIndex = generator.nextInt(targets.size());
			SuperNPlayer sntarget = targets.get(randomIndex);

			if (!bountyList.contains(sntarget) && sntarget.isSuper()) {
				bountyList.add(sntarget);
				SupernaturalsPlugin.instance.getServer().broadcastMessage(ChatColor.WHITE
						+ sntarget.getName()
						+ ChatColor.RED
						+ " has been added to the WitchHunter target list!");
				bountyFound = true;
				if (SNConfigHandler.debugMode) {
					SupernaturalsPlugin.log("Bounty created on "
							+ sntarget.getName());
				}
				return;
			}
			count++;
			if (count > 50) {
				return;
			}
		}
	}

	public static void updateBounties() {
		List<SuperNPlayer> snplayers = new ArrayList<SuperNPlayer>();

		if (bountyList.isEmpty()) {
			return;
		}

		for (SuperNPlayer snplayer : bountyList) {
			if (!snplayer.isSuper()) {
				snplayers.add(snplayer);
			}
		}

		for (SuperNPlayer snplayer : snplayers) {
			removeBounty(snplayer);
			addBounty();
		}
	}

	public static void createBounties() {
		List<SuperNPlayer> targets = SuperNManager.getSupernaturals();
		if (targets.size() == 0) {
			SupernaturalsPlugin.log(Level.WARNING, "No targets found for WitchHunters!");
			return;
		}

		int numberFound = 0;
		Random generator = new Random();
		int count = 0;

		while (numberFound < SNConfigHandler.hunterMaxBounties) {
			int randomIndex = generator.nextInt(targets.size());
			SuperNPlayer sntarget = targets.get(randomIndex);
			if (!bountyList.contains(sntarget) && sntarget.isSuper()) {
				bountyList.add(sntarget);
				numberFound++;
				SupernaturalsPlugin.instance.getServer().broadcastMessage(ChatColor.WHITE
						+ sntarget.getName()
						+ ChatColor.RED
						+ " has been added to the WitchHunter target list!");
				if (SNConfigHandler.debugMode) {
					SupernaturalsPlugin.log("Bounty created on "
							+ sntarget.getName());
				}
			}
			count++;
			if (count > 100) {
				return;
			}
		}
	}

	// -------------------------------------------- //
	// Doors //
	// -------------------------------------------- //

	private void addDoorLocation(Location location) {
		if (!hallDoors.contains(location)) {
			hallDoors.add(location);
		}
	}

	private void removeDoorLocation(Location location) {
		hallDoors.remove(location);
	}

	public boolean doorIsOpening(Location location) {
		if (hallDoors.contains(location)) {
			return true;
		}
		return false;
	}

	public boolean doorEvent(Player player, Block block) {
		if (doorIsOpen(block)) {
			return true;
		}

		if (SNConfigHandler.debugMode) {
			SupernaturalsPlugin.log(player.getName()
					+ " activated a WitchHunters' Hall.");
		}

		SuperNPlayer snplayer = SuperNManager.get(player);
		boolean open = false;

		final Location loc = block.getLocation();
		Block other;
		Location newLoc;

		if (snplayer.isHuman()) {
			open = join(snplayer);
		}

		if (snplayer.isHunter() || snplayer.isHuman() && open) {
			// Both panels move together. Which one is the other half is asked of the block
			// data rather than worked out from which one was clicked - see {@link #otherHalf}.
			other = otherHalf(block);
			setDoorOpen(block, true);
			newLoc = other == null ? null : other.getLocation();
			if (other != null) {
				setDoorOpen(other, true);
			}

			addDoorLocation(loc);
			if (newLoc != null) {
				addDoorLocation(newLoc);
			}

			SupernaturalsPlugin.instance.getServer().getScheduler().scheduleSyncDelayedTask(SupernaturalsPlugin.instance, new Runnable() {
				@Override
				public void run() {
					closeDoor(loc);
				}
			}, 20);
			if (SNConfigHandler.debugMode) {
				SupernaturalsPlugin.log("WitchHunter door is set open.");
			}
			return true;
		}
		SuperNManager.sendMessage(snplayer, "WitchHunters Only!");
		return true;
	}

	private void closeDoor(Location loc) {
		Block block = loc.getBlock();
		if (!doorIsOpen(block)) {
			return;
		}

		Block other = otherHalf(block);
		setDoorOpen(block, false);
		if (other != null) {
			setDoorOpen(other, false);
		}

		removeDoorLocation(loc);
		if (other != null) {
			removeDoorLocation(other.getLocation());
		}
	}

	/**
	 * The other panel of the door this block belongs to, or null when it has none.
	 *
	 * <p>A door is two blocks that must agree about which of them is the top one, and the answer
	 * is in the block data. It used to be read off a legacy material-data object instead, which
	 * is deprecated and, worse, silent about a door whose two blocks were both placed as the
	 * bottom half - which is exactly what a plain {@code setType(IRON_DOOR)} leaves behind. Such
	 * a door still shows two panels, and a click on the upper one used to open the upper one
	 * plus the air above it, so half the door moved. Falling back to "whichever neighbour is a
	 * door of the same material" pairs them up anyway.
	 */
	private static Block otherHalf(Block block) {
		if (!(block.getBlockData() instanceof org.bukkit.block.data.type.Door door)) {
			return null;
		}
		Block up = block.getRelative(BlockFace.UP);
		Block down = block.getRelative(BlockFace.DOWN);
		Block wanted = door.getHalf() == Bisected.Half.TOP ? down : up;
		if (sameDoor(wanted, block.getType())) {
			return wanted;
		}
		Block spare = wanted.equals(up) ? down : up;
		return sameDoor(spare, block.getType()) ? spare : null;
	}

	private static boolean sameDoor(Block block, Material material) {
		return block.getType().equals(material)
				&& block.getBlockData() instanceof org.bukkit.block.data.type.Door;
	}

	private static boolean doorIsOpen(Block block) {
		return block.getBlockData() instanceof Openable openable && openable.isOpen();
	}

	/**
	 * 1.21 起 Block#setTypeIdAndData(int, byte, boolean) 已被移除。
	 * 旧代码通过 (data ± 4) 直接翻转铁门方块数据里的 open 位，
	 * 这里用 BlockData API 表达同样的语义（只切换 open 状态，不动其他属性）。
	 */
	private static void setDoorOpen(Block block, boolean open) {
		org.bukkit.block.data.BlockData data = block.getBlockData();
		if (data instanceof org.bukkit.block.data.type.Door) {
			org.bukkit.block.data.type.Door doorData = (org.bukkit.block.data.type.Door) data;
			doorData.setOpen(open);
			block.setBlockData(doorData, false);
		}
	}

	// -------------------------------------------- //
	// Join Event //
	// -------------------------------------------- //

	public void invite(final SuperNPlayer snplayer) {
		SupernaturalsPlugin.instance.getServer().getScheduler().scheduleSyncDelayedTask(SupernaturalsPlugin.instance, new Runnable() {
			@Override
			public void run() {
				SuperNManager.sendMessage(snplayer, "You have been invited to join the WitchHunter society!");
				SuperNManager.sendMessage(snplayer, "If you wish to accept this invitation visit a WitchHunters' Hall");
				if (!playerInvites.contains(snplayer)) {
					playerInvites.add(snplayer);
				}
			}
		}, 200);
	}

	public boolean join(SuperNPlayer snplayer) {
		if (playerInvites.contains(snplayer)) {
			SuperNManager.sendMessage(snplayer, "Welcome to the WitchHunter society!");
			SuperNManager.convert(snplayer, "witchhunter", SNConfigHandler.hunterPowerStart);
			return true;
		}
		return false;
	}

	// -------------------------------------------- //
	// Arrow Management //
	// -------------------------------------------- //

	public boolean changeArrowType(SuperNPlayer snplayer) {
		String currentType = hunterMap.get(snplayer);
		if (currentType == null) {
			currentType = "normal";
		}

		String nextType = "normal";

		for (int i = 0; i < SNConfigHandler.hunterArrowTypes.size(); i++) {
			if (SNConfigHandler.hunterArrowTypes.get(i).equalsIgnoreCase(currentType)) {
				int newI = i + 1;
				if (newI >= SNConfigHandler.hunterArrowTypes.size()) {
					newI = 0;
				}
				nextType = SNConfigHandler.hunterArrowTypes.get(newI);
				break;
			}
		}

		hunterMap.put(snplayer, nextType);
		SuperNManager.sendMessage(snplayer, "Changed to arrow type: "
				+ ChatColor.WHITE + nextType);
		if (SNConfigHandler.debugMode) {
			SupernaturalsPlugin.log(snplayer.getName()
					+ " changed to arrow type: " + nextType);
		}
		return true;
	}

	public String getArrowType(Arrow arrow) {
		return arrowMap.get(arrow);
	}

	public HashMap<Arrow, String> getArrowMap() {
		return arrowMap;
	}

	public void removeArrow(final Arrow arrow) {
		SupernaturalsPlugin.instance.getServer().getScheduler().scheduleSyncDelayedTask(SupernaturalsPlugin.instance, new Runnable() {
			@Override
			public void run() {
				arrowMap.remove(arrow);
			}
		}, 20);
	}

	// -------------------------------------------- //
	// Attacks //
	// -------------------------------------------- //

	public boolean shoot(final Player player) {

		final SuperNPlayer snplayer = SuperNManager.get(player);

		if (!player.getInventory().contains(Material.ARROW)) {
			// Said out loud. Every other way shooting can fail here says something - no power, wrong
			// zone, still recovering - and this one said nothing at all: a bow that draws and looses
			// and produces no arrow, no message and no arrow in the air, which is what "the ability
			// is broken" looks like from the player's side. It is also the failure that has nothing
			// to do with the ability.
			SuperNManager.sendMessage(snplayer, "You have no arrows to shoot.");
			return false;
		}

		if (!SupernaturalsPlugin.instance.getPvP(player)) {
			String arrowType = hunterMap.get(snplayer);
			if (arrowType == null) {
				hunterMap.put(snplayer, "normal");
				return false;
			}
			if (!arrowType.equalsIgnoreCase("normal")) {
				SuperNManager.sendMessage(snplayer, "You cannot use special arrows in non-PvP areas.");
			}
			return false;
		}

		String arrowType = hunterMap.get(snplayer);
		if (arrowType == null) {
			arrowType = "normal";
		}

		if (SNConfigHandler.debugMode) {
			SupernaturalsPlugin.log(snplayer.getName() + " is firing "
					+ arrowType + " arrows.");
		}

		if (arrowType.equalsIgnoreCase("fire")) {
			if (SuperNManager.canPay(snplayer, SNConfigHandler.hunterPowerArrowFire)) {
				SuperNManager.alterPower(snplayer, -SNConfigHandler.hunterPowerArrowFire, "Fire Arrow!");
				Arrow arrow = player.launchProjectile(Arrow.class);
				arrowMap.put(arrow, arrowType);
				arrow.setFireTicks(SNConfigHandler.hunterFireArrowFireTicks);
				return true;
			} else {
				SuperNManager.sendMessage(snplayer, "Not enough power to shoot Fire Arrows!");
				SuperNManager.sendMessage(snplayer, "Switching to normal arrows.");
				hunterMap.put(snplayer, "normal");
				return false;
			}
		} else if (arrowType.equalsIgnoreCase("triple")) {
			if (SuperNManager.canPay(snplayer, SNConfigHandler.hunterPowerArrowTriple)) {
				SuperNManager.alterPower(snplayer, -SNConfigHandler.hunterPowerArrowTriple, "Triple Arrow!");
				final Arrow arrow = player.launchProjectile(Arrow.class);
				arrowMap.put(arrow, arrowType);
				SupernaturalsPlugin.instance.getServer().getScheduler().scheduleSyncDelayedTask(SupernaturalsPlugin.instance, new Runnable() {
					@Override
					public void run() {
						splitArrow(player, arrow);
					}
				}, 4);
				return true;
			} else {
				SuperNManager.sendMessage(snplayer, "Not enough power to shoot Triple Arrows!");
				SuperNManager.sendMessage(snplayer, "Switching to normal arrows.");
				hunterMap.put(snplayer, "normal");
				return false;
			}
		} else if (arrowType.equalsIgnoreCase("power")) {
			// The cooldown is this arrow's, and only this arrow's. It used to be checked before
			// the type was even read, so a hunter who fired one power shot could not shoot a
			// normal, fire, triple or grapple arrow for fifteen seconds - the other four arrows
			// inherited a recovery that was never theirs, and the boards that hand them out
			// looked broken for the whole quarter of a minute after somebody tried power.
			if (powerRecovering(player)) {
				SuperNManager.sendMessage(snplayer, "You are still recovering from Power Shot ("
						+ powerRecoverySeconds(player) + "s). Switch arrow type to shoot something else.");
				return true;
			}
			if (SuperNManager.canPay(snplayer, SNConfigHandler.hunterPowerArrowPower)) {
				SuperNManager.alterPower(snplayer, -SNConfigHandler.hunterPowerArrowPower, "Power Arrow!");
				Arrow arrow = player.launchProjectile(Arrow.class);
				arrowMap.put(arrow, arrowType);
				powerReadyAt.put(player.getUniqueId(),
						System.currentTimeMillis() + SNConfigHandler.hunterCooldown);
				if (SNConfigHandler.debugMode) {
					SupernaturalsPlugin.log(snplayer.getName() + " is drained for "
							+ SNConfigHandler.hunterCooldown + "ms.");
				}
				return true;
			} else {
				SuperNManager.sendMessage(snplayer, "Not enough power to shoot Power Arrows!");
				SuperNManager.sendMessage(snplayer, "Switching to normal arrows.");
				hunterMap.put(snplayer, "normal");
				return false;
			}
		} else if (arrowType.equalsIgnoreCase("grapple")) {
			if (SuperNManager.canPay(snplayer, SNConfigHandler.hunterPowerArrowGrapple)) {
				// What it actually cost, measured rather than assumed: with free power nothing is
				// taken, and a refund has to give back what was taken.
				double before = snplayer.getPower();
				SuperNManager.alterPower(snplayer, -SNConfigHandler.hunterPowerArrowGrapple, "Grapple Arrow!");
				Arrow arrow = player.launchProjectile(Arrow.class);
				int spent = (int) Math.round(before - snplayer.getPower());
				arrowMap.put(arrow, arrowType);
				grappleSpent.put(arrow, Integer.valueOf(spent));
				confirmItFlew(player, arrow, arrow.getLocation().clone());
				return true;
			} else {
				SuperNManager.sendMessage(snplayer, "Not enough power to shoot Grapple Arrow!");
				SuperNManager.sendMessage(snplayer, "Switching to normal arrows.");
				hunterMap.put(snplayer, "normal");
				return false;
			}
		} else {
			return false;
		}
	}

	public void splitArrow(final Player player, final Arrow arrow) {
		if (SNConfigHandler.debugMode) {
			SupernaturalsPlugin.log(player.getName() + "'s triple arrow event.");
		}
		player.launchProjectile(Arrow.class);
		String arrowType = arrowMap.get(arrow);
		if (arrowType.equals("triple")) {
			arrowMap.put(arrow, "double");
			SupernaturalsPlugin.instance.getServer().getScheduler().scheduleSyncDelayedTask(SupernaturalsPlugin.instance, new Runnable() {
				@Override
				public void run() {
					splitArrow(player, arrow);
				}
			}, 4);
		} else {
			arrowMap.remove(arrow);
		}
	}

	/** Whether the power arrow's own cooldown is still running. */
	private boolean powerRecovering(Player player) {
		Long ready = powerReadyAt.get(player.getUniqueId());
		if (ready == null) {
			return false;
		}
		if (System.currentTimeMillis() >= ready) {
			powerReadyAt.remove(player.getUniqueId());
			return false;
		}
		return true;
	}

	/** Whole seconds left of that cooldown, rounded up so it never reads 0 while still refusing. */
	private int powerRecoverySeconds(Player player) {
		Long ready = powerReadyAt.get(player.getUniqueId());
		if (ready == null) {
			return 0;
		}
		long left = ready - System.currentTimeMillis();
		return (int) Math.max(0, (left + 999) / 1000);
	}

	public boolean isGrappling(Player player) {
		return player != null && grappleOwners.containsKey(player.getUniqueId());
	}

	/**
	 * Whether the pull named by this token is still the one that owns the player.
	 *
	 * <p>The one question a task in flight has to ask before every step, and the reason two pulls
	 * can never both move somebody. A pull that has been taken over answers false here and does
	 * nothing more: no step, no speed, no fall distance, and no ending of the pull that replaced
	 * it.
	 */
	public boolean ownsGrapple(Player player, long token) {
		if (player == null) {
			return false;
		}
		Long current = grappleOwners.get(player.getUniqueId());
		return current != null && current.longValue() == token;
	}

	public void startGrappling(Player player, Location targetLocation) {
		startGrappling(player, targetLocation, 0);
	}

	/**
	 * Starts a pull, or takes the player over from one that is still running.
	 *
	 * <p>Taking over rather than refusing, because the shot was already paid for: a hunter who
	 * fires while a pull is in flight has bought another pull, and the only two honest answers are
	 * to give him one or to give the power back. This gives him one. The pull it replaces finds
	 * out at its next tick, by its token, and steps aside without touching him.
	 *
	 * @param spentPower what the shot cost, so a pull that moves him nowhere can refund it
	 */
	public void startGrappling(Player player, Location targetLocation, int spentPower) {
		if (player == null || targetLocation == null) {
			return;
		}
		long token = nextGrappleToken++;
		grappleOwners.put(player.getUniqueId(), token);
		// A tick later, not "as soon as possible": a task scheduled with no delay from inside a
		// task runs in the same tick on this server, and a pull whose steps all happen in one tick
		// is a teleport - see ArrowUtil#renew.
		ArrowUtil gh = new ArrowUtil(player, targetLocation, token, spentPower);
		SupernaturalsPlugin.instance.getServer().getScheduler()
				.scheduleSyncDelayedTask(SupernaturalsPlugin.instance, gh, 1L);
	}

	/**
	 * Ends a pull - but only the pull that still owns him.
	 *
	 * <p>A pull that was taken over must not call this: it would end the pull that replaced it and
	 * zero the speed the newer one just set. Ownership is cleared here and now, which is what makes
	 * the next shot able to start a pull of its own: the two-second delay this used to have was the
	 * window in which every shot was silently refused.
	 */
	public void stopGrappling(Player player, long token) {
		if (!ownsGrapple(player, token)) {
			return;
		}
		grappleOwners.remove(player.getUniqueId());
		if (!player.isOnline()) {
			return;
		}
		player.setVelocity(new Vector(0, 0, 0));
		player.setFallDistance(0);
		softenLanding(player, 60);
	}

	/**
	 * Gives back what a shot cost when the pull it bought moved the player nowhere.
	 *
	 * <p>The other half of "a shot that was paid for does something": if there was nothing to pull
	 * him to - the arrow landed two blocks away, the move was refused, he was in another world
	 * before the first step - the power goes back and he is told why, instead of the shot
	 * disappearing with the power. Nothing is given back once the pull has moved him: a ride that
	 * ended early still happened.
	 */
	public void refundGrapple(Player player, int spentPower, boolean moved, String why) {
		if (moved || spentPower <= 0 || player == null || !player.isOnline()) {
			return;
		}
		SuperNPlayer snplayer = SuperNManager.get(player);
		SuperNManager.alterPower(snplayer, spentPower, "Grapple Arrow refunded!");
		SuperNManager.sendMessage(snplayer, "Nothing to pull you to (" + why + ") - "
				+ spentPower + " power returned.");
	}

	/**
	 * Checks, one tick after the shot, that the arrow actually left the bow.
	 *
	 * <p>An arrow loosed into the block in front of the hunter - a wall he is standing against, the
	 * underside of a ledge, the block his own head is in - is inside a block from the moment it
	 * exists, and an arrow that is inside a block is never reported as having hit one. So the hit
	 * that should have started the pull never arrives: the shot keeps its power, moves nobody, and
	 * leaves the arrow in the map for good. That is the report this is about, in its purest form.
	 *
	 * <p>The spent-power entry is the proof a shot was never handled, because the hit takes it out
	 * before it does anything else. One tick later a real arrow is either in the air or has already
	 * reported its hit, so anything still holding its entry and not flying caught nothing, and is
	 * paid back like a pull that could not move him.
	 */
	private void confirmItFlew(final Player player, final Arrow arrow, final Location launch) {
		SupernaturalsPlugin.instance.getServer().getScheduler().scheduleSyncDelayedTask(
				SupernaturalsPlugin.instance, new Runnable() {
					@Override
					public void run() {
						Integer spent = grappleSpent.get(arrow);
						if (spent == null) {
							return;
						}
						// Two ticks, and three ways to be a shot that never flew: gone, inside a
						// block, or still standing where it was loosed from. The last one does not
						// depend on when the server decides to call an arrow "in a block", which is
						// a tick later than it looks.
						boolean flew = arrow.isValid() && !arrow.isInBlock()
								&& arrow.getLocation().distanceSquared(launch) >= 1.0D;
						if (flew) {
							return;
						}
						arrowMap.remove(arrow);
						grappleSpent.remove(arrow);
						refundGrapple(player, spent.intValue(), false,
								"the arrow never left the block it was fired into");
						if (arrow.isValid()) {
							arrow.remove();
						}
					}
				}, 2L);
	}

	/**
	 * What this arrow's shot cost, taken out of the record.
	 *
	 * <p>Asked once, by whoever handles the hit. The number is what was actually charged, not what
	 * the setting says: an admin with free power is charged nothing, and giving that back as if it
	 * had been paid would mint power.
	 */
	public int takeGrappleCost(Arrow arrow) {
		Integer spent = grappleSpent.remove(arrow);
		return spent == null ? SNConfigHandler.hunterPowerArrowGrapple : spent.intValue();
	}

	/**
	 * Keeps a player who has just been pulled out of the air from paying for it on the way down.
	 *
	 * <p>The pull ends three blocks short of what it caught, which for a ledge is three blocks of
	 * air, so the last part of the ride is a fall. Zeroing the distance each tick during the pull
	 * was not enough: the moment it releases, the fall starts counting again and the ability reads
	 * as hurting the person who used it. This keeps it at zero until he is back on the ground, or
	 * for three seconds, whichever comes first.
	 */
	private void softenLanding(final Player player, final int ticksLeft) {
		if (ticksLeft <= 0 || player == null || !player.isOnline()) {
			return;
		}
		player.setFallDistance(0);
		if (player.isOnGround()) {
			return;
		}
		// One tick at a time: without a delay this counts itself down inside a single tick and the
		// guard is over before the fall it was guarding against has begun.
		SupernaturalsPlugin.instance.getServer().getScheduler().scheduleSyncDelayedTask(
				SupernaturalsPlugin.instance, new Runnable() {
					@Override
					public void run() {
						softenLanding(player, ticksLeft - 1);
					}
				}, 1L);
	}
}
