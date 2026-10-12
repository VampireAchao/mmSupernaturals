package me.matterz.supernaturals.manager;

import me.matterz.supernaturals.SuperNPlayer;
import me.matterz.supernaturals.SupernaturalsPlugin;
import me.matterz.supernaturals.io.SNConfigHandler;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Boat;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.Vector;

public class AngelManager extends ClassManager {

	public AngelManager() {
		super();
	}

	@Override
	public double damagerEvent(EntityDamageByEntityEvent event, double damage) {
		// This is the *attacker's* manager: the caller asks the class manager of the player who
		// dealt the blow, and only ever for a player. Reading the victim off the event and casting
		// it to Player threw a ClassCastException the moment the victim was anything else - which
		// is precisely the case this rule exists for - so "angels cannot use diamond swords on
		// animals" never actually refused anything.
		if (!(event.getDamager() instanceof Player player)) {
			return damage;
		}
		if (event.getEntity() instanceof Animals
				&& player.getItemInHand().getType().equals(Material.DIAMOND_SWORD)) {
			SuperNManager.sendMessage(SuperNManager.get(player),
					"Angels cannot use diamond swords on animals!");
			event.setCancelled(true);
			return 0;
		}
		return damage;
	}

	@Override
	public void spellEvent(EntityDamageByEntityEvent event, Player target) {
		Player player = (Player) event.getDamager();
		SuperNPlayer snplayer = SuperNManager.get(player);
		SuperNPlayer sntarget = SuperNManager.get(target);
		if (player.getItemInHand().getType().equals(Material.FEATHER)) {
			if (snplayer.getPower() > SNConfigHandler.angelHealPowerCost) {
				target.setHealth(target.getHealth()
						+ SNConfigHandler.angelHealHealthGain);
				SuperNManager.alterPower(snplayer, -SNConfigHandler.angelHealPowerCost, "Healing "
						+ target.getName());
				target.sendMessage(ChatColor.RED + "Healed by "
						+ player.getName());
			} else {
				SuperNManager.sendMessage(snplayer, "Not enough power to heal!");
			}
		}
		if (player.getItemInHand().getType().equals(Material.PAPER)) {
			if (snplayer.getPower() > SNConfigHandler.angelCurePowerCost) {
				if (sntarget.isSuper()) {
					SuperNManager.cure(sntarget);
					target.sendMessage(ChatColor.RED + "Cured by "
							+ player.getName());
					SuperNManager.alterPower(snplayer, -SNConfigHandler.angelCurePowerCost, "Cured "
							+ target.getName());
				} else {
					SuperNManager.sendMessage(snplayer, "Player is not a supernatural!");
				}
			} else {
				SuperNManager.sendMessage(snplayer, "Not enough power!");
			}
		}
	}

	@Override
	public boolean playerInteract(PlayerInteractEvent event) {
		Player player = event.getPlayer();
		Action action = event.getAction();
		Material itemInHandMaterial = player.getItemInHand().getType();
		SuperNPlayer snplayer = SuperNManager.get(player);
		if (!action.equals(Action.LEFT_CLICK_AIR)
				&& !action.equals(Action.LEFT_CLICK_BLOCK)) {
			return false;
		}
		if (itemInHandMaterial.equals(Material.DANDELION)) {
			if (snplayer.getPower() > SNConfigHandler.angelJumpPowerCost) {
				jump(player, SNConfigHandler.angelJumpDeltaSpeed);
			} else {
				SuperNManager.sendMessage(snplayer, "Not enough power to jump!");
			}
			return false;
		}
		// This is the branch that used to be unreachable. The summoning sat in its own
		// `if (LEFT_CLICK_BLOCK)` *below* the one above, which had already returned for both
		// click actions - so an angel could never call a cow, a wolf or a pig, whatever it was
		// holding, and every summon looked like a dead ability. Reaching it is the fix; the
		// calls themselves are the ones that were always here.
		//
		// Both kinds of swing summon now. A swing at a block puts the animal where the player
		// pointed; a swing at the air - which is what most of them are, since there is usually
		// nothing to point at - puts it on clear ground in front of them, the same way a
		// werewolf calls a wolf.
		if (!itemInHandMaterial.equals(Material.BEEF)
				&& !itemInHandMaterial.equals(Material.BONE)
				&& !itemInHandMaterial.equals(Material.PORKCHOP)) {
			return false;
		}
		if (snplayer.getPower() <= SNConfigHandler.angelSummonPowerCost) {
			SuperNManager.sendMessage(snplayer, "Not enough power to summon!");
			return false;
		}
		Location spot = summonSpot(player, event.getClickedBlock(), event.getBlockFace());
		if (itemInHandMaterial.equals(Material.BEEF)) {
			player.getWorld().spawnEntity(spot, EntityType.COW);
			event.setCancelled(true);
			SuperNManager.alterPower(snplayer, -SNConfigHandler.angelSummonPowerCost, "Summoned cow.");
			return true;
		}
		if (itemInHandMaterial.equals(Material.BONE)) {
			Wolf spawnedWolf = (Wolf) player.getWorld().spawnEntity(spot, EntityType.WOLF);
			spawnedWolf.setTamed(true);
			spawnedWolf.setOwner(player);
			spawnedWolf.setHealth(20);
			event.setCancelled(true);
			SuperNManager.alterPower(snplayer, -SNConfigHandler.angelSummonPowerCost, "Summoned wolf.");
			return true;
		}
		player.getWorld().spawnEntity(spot, EntityType.PIG);
		event.setCancelled(true);
		SuperNManager.alterPower(snplayer, -SNConfigHandler.angelSummonPowerCost, "Summoned pig.");
		return true;
	}

	@Override
	public double victimEvent(EntityDamageEvent event, double damage) {
		if (event.getCause().equals(DamageCause.FALL)) {
			event.setCancelled(true);
			return 0;
		}
		return damage;
	}

	/**
	 * Where a summoned animal should stand.
	 *
	 * <p>An animal conjured at the block a player aimed at used to appear at that block's own
	 * corner - which is inside the block - and suffocated where it stood. It belongs in the open
	 * cell on the far side of the face that was struck, or on top of the block. A swing at the
	 * air has no block at all, so it goes straight to clear ground in front of the player.
	 */
	private static Location summonSpot(Player player, Block target, BlockFace face) {
		if (target != null) {
			if (face != null && !face.equals(BlockFace.SELF) && face.getModY() == 0) {
				Location beside = roomAt(target.getRelative(face));
				if (beside != null) {
					return beside;
				}
			}
			Location above = roomAt(target.getRelative(BlockFace.UP));
			if (above != null) {
				return above;
			}
		}
		Location front = inFront(player);
		return front != null ? front : player.getLocation();
	}

	/** Clear floor in front of the player, two blocks out and then one, or null if both are walled. */
	private static Location inFront(Player player) {
		Vector facing = player.getLocation().getDirection().setY(0);
		if (facing.lengthSquared() < 1.0E-6) {
			facing = new Vector(0, 0, 1);
		}
		facing.normalize();
		for (int distance = 2; distance >= 1; distance--) {
			Location ahead = player.getLocation().clone().add(facing.clone().multiply(distance));
			Location spot = roomAt(ahead.getBlock());
			if (spot != null) {
				return spot;
			}
		}
		return null;
	}

	/** The middle of a cell an animal can stand in: two passable cells with floor under them. */
	private static Location roomAt(Block block) {
		if (!block.isPassable() || !block.getRelative(BlockFace.UP).isPassable()) {
			return null;
		}
		if (block.getRelative(BlockFace.DOWN).isPassable()) {
			// Something to stand on, or the animal would drop down a hole the moment it
			// appeared. Drowning it inside a wall was the bug; dropping it into a mine shaft
			// would be the same bug wearing a hat.
			return null;
		}
		return block.getLocation().add(0.5, 0, 0.5);
	}

	public static boolean jump(Player player, double deltaSpeed) {
		SuperNPlayer snplayer = SuperNManager.get(player);

		if (snplayer.getPower() < SNConfigHandler.angelJumpPowerCost) {
			SuperNManager.sendMessage(snplayer, "Not enough Power to jump.");
			return false;
		} else {
			SuperNManager.alterPower(snplayer, -SNConfigHandler.angelJumpPowerCost, "SuperJump!");
			if (SNConfigHandler.debugMode) {
				SupernaturalsPlugin.log(snplayer.getName() + " used jump!");
			}
		}

		Vector vjadd;
		vjadd = new Vector(0, 1, 0);
		vjadd.multiply(deltaSpeed);

		player.setVelocity(player.getVelocity().add(vjadd));
		return true;
	}

	@Override
	public void armorCheck(Player player) {
		PlayerInventory inv = player.getInventory();
		ItemStack helmet = inv.getHelmet();
		ItemStack chest = inv.getChestplate();
		ItemStack leggings = inv.getLeggings();
		ItemStack boots = inv.getBoots();

		if (helmet != null) {
			if (helmet.getType().equals(Material.DIAMOND_HELMET)
					|| helmet.getType().equals(Material.GOLDEN_HELMET)) {
				inv.setHelmet(null);
				dropItem(player, helmet);
			}
		}
		if (chest != null) {
			if (chest.getType().equals(Material.DIAMOND_CHESTPLATE)
					|| chest.getType().equals(Material.GOLDEN_CHESTPLATE)) {
				inv.setChestplate(null);
				dropItem(player, chest);
			}
		}
		if (leggings != null) {
			if (leggings.getType().equals(Material.DIAMOND_LEGGINGS)
					|| leggings.getType().equals(Material.GOLDEN_LEGGINGS)) {
				inv.setLeggings(null);
				dropItem(player, leggings);
			}
		}
		if (boots != null) {
			if (boots.getType().equals(Material.DIAMOND_BOOTS)
					|| boots.getType().equals(Material.GOLDEN_BOOTS)) {
				inv.setBoots(null);
				dropItem(player, boots);
			}
		}
	}

	public void waterAdvanceTime(Player player) {
		if (player.isDead()) {
			return;
		}

		if (player.isInsideVehicle()) {
			if (player.getVehicle() instanceof Boat) {
				return;
			}
		}

		SuperNPlayer snplayer = SuperNManager.get(player);

		Material material = player.getLocation().getBlock().getType();

		if (material == Material.WATER) {
			SuperNManager.alterPower(snplayer, SNConfigHandler.angelSwimPowerGain, "Swimming in water");
		}
	}

}