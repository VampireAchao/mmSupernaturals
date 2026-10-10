package me.matterz.supernaturals.manager;

import java.util.HashMap;

import me.matterz.supernaturals.SuperNPlayer;
import me.matterz.supernaturals.SupernaturalsPlugin;
import me.matterz.supernaturals.io.SNConfigHandler;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public class EnderBornManager extends ClassManager {

	/**
	 * How often one of an enderborn's blows carries its attack bonus, and how often a killing
	 * blow turns the victim into one of them.
	 *
	 * <p>Both used to be written as {@code Math.random() == 0.35} and {@code == 0.10}: a double
	 * compared for <em>exact</em> equality with a number that is not even representable, so the
	 * rolls could never come up and neither the attack bonus nor the conversion ever happened.
	 */
	private static final double ATTACK_BONUS_CHANCE = 0.35;
	private static final double CONVERT_CHANCE = 0.10;

	/** Deaths it takes for an enderborn to be reborn as a human. */
	private static final int DEATHS_UNTIL_HUMAN = 5;

	public SupernaturalsPlugin plugin;
	public HashMap<SuperNPlayer, Boolean> teleMap = new HashMap<SuperNPlayer, Boolean>();
	public HashMap<SuperNPlayer, Integer> deathTimesMap = new HashMap<SuperNPlayer, Integer>();

	public EnderBornManager(SupernaturalsPlugin instance) {
		super();
		plugin = instance;
	}

	public boolean changeTele(SuperNPlayer snplayer) {
		if (!teleMap.containsKey(snplayer)) {
			teleMap.put(snplayer, true);
			return true;
		}
		if (!teleMap.get(snplayer)) {
			teleMap.put(snplayer, true);
			return true;
		} else {
			teleMap.put(snplayer, false);
			return false;
		}
	}

	public boolean willTele(SuperNPlayer snplayer) {
		if (!teleMap.containsKey(snplayer)) {
			return false;
		}
		return teleMap.get(snplayer);
	}

	@Override
	public void spellEvent(EntityDamageByEntityEvent event, Player target) {
		Player player = (Player) event.getDamager();
		SuperNPlayer snplayer = SuperNManager.get(player);
		ItemStack item = player.getItemInHand();
		Material itemMaterial = item.getType();
		ItemStack targetItem = target.getItemInHand();
		Material targetItemMaterial = targetItem.getType();
		SuperNPlayer sntarget = SuperNManager.get(target);
		if (itemMaterial.equals(Material.ENDER_PEARL)
				&& targetItemMaterial.equals(Material.ENDER_PEARL)) {
			SuperNManager.sendMessage(snplayer, "You have converted "
					+ ChatColor.WHITE + target.getName() + ChatColor.RED + "!");
			SuperNManager.sendMessage(sntarget, "An energy takes over your body...");
			SuperNManager.convert(sntarget, "enderborn");
			event.setCancelled(true);
		}
	}

	@Override
	public double damagerEvent(EntityDamageByEntityEvent event, double damage) {
		Entity damager = event.getDamager();
		Player pDamager = (Player) damager;
		SuperNPlayer snDamager = SuperNManager.get(pDamager);

		ItemStack item = pDamager.getItemInHand();
		if (item != null) {
			Material itemMaterial = item.getType();

			if (SNConfigHandler.enderWeapons.contains(itemMaterial)) {
				SuperNManager.sendMessage(snDamager, ChatColor.RED
						+ "EnderBorns cannot use "
						+ itemMaterial.toString().replace('_', ' '));
				return 0;
			}
			if (Math.random() < ATTACK_BONUS_CHANCE) {
				damage += damage
						* snDamager.scale(SNConfigHandler.enderDamageFactor);
				return damage;
			}
		}
		return damage;
	}

	@Override
	public double victimEvent(EntityDamageEvent event, double damage) {
		if (event instanceof EntityDamageByEntityEvent) {
			EntityDamageByEntityEvent edbeEvent = (EntityDamageByEntityEvent) event;
			Entity victim = edbeEvent.getEntity();
			if (victim instanceof Player) {
				Player pVictim = (Player) victim;
				SuperNPlayer snVictim = SuperNManager.get(pVictim);

				ItemStack item = pVictim.getItemInHand();
				if (item.getType() == Material.ENDER_PEARL
						&& snVictim.getPower() > SNConfigHandler.enderProtectPower) {
					damage -= damage
							* snVictim.scale(1 - SNConfigHandler.enderDamageReceivedFactor);
					SuperNManager.alterPower(snVictim, -SNConfigHandler.enderProtectPower, "Protected by Pearl!");
					return damage;
				}
			}
		}
		return damage;
	}

	@Override
	public void deathEvent(Player player) {
		SuperNPlayer snplayer = SuperNManager.get(player);
		SuperNManager.alterPower(snplayer, -SNConfigHandler.enderDeathPowerPenalty, "You died!");

		// One death, counted once. The old version seeded the counter on the first death and
		// then fell through to the "not five yet" branch, which advanced it a second time and
		// printed the countdown twice - "4 deaths" immediately followed by "3".
		int deaths = deathTimesMap.merge(snplayer, 1, Integer::sum);
		if (deaths >= DEATHS_UNTIL_HUMAN) {
			SuperNManager.sendMessage(snplayer, "You have been reborn as a human.");
			if (SNConfigHandler.debugMode) {
				SupernaturalsPlugin.log(snplayer.getName() + " died " + deaths
						+ " times and is human again.");
			}
			SuperNManager.cure(snplayer);
			deathTimesMap.remove(snplayer);
			return;
		}

		int left = DEATHS_UNTIL_HUMAN - deaths;
		SuperNManager.sendMessage(snplayer, "You have " + left
				+ (left == 1 ? " death" : " deaths") + " untill you are reborn as human.");
	}

	/** How many times this enderborn has died, without the reading changing the answer. */
	public int getDeathTimes(SuperNPlayer snplayer) {
		return deathTimesMap.getOrDefault(snplayer, 0);
	}

	public void killEvent(SuperNPlayer damager, SuperNPlayer victim) {
		if (victim == null) {
			SuperNManager.alterPower(damager, SNConfigHandler.enderKillPower, "Stole power");
		} else {
			SuperNManager.alterPower(victim, -SNConfigHandler.enderKillPower, damager.getName()
					+ " stole some of your power!");
			SuperNManager.alterPower(damager, SNConfigHandler.enderKillPower, "Stole power from "
					+ victim.getName());
			if (Math.random() < CONVERT_CHANCE) {
				SuperNManager.convert(victim, "enderborn");
			}
		}
	}

	@Override
	public boolean playerInteract(PlayerInteractEvent event) {
		Player player = event.getPlayer();
		Action action = event.getAction();
		SuperNPlayer snplayer = SuperNManager.get(player);

		ItemStack item = player.getItemInHand();
		Material itemMaterial = item.getType();
		if (action.equals(Action.RIGHT_CLICK_AIR)
				|| action.equals(Action.RIGHT_CLICK_BLOCK)) {
			if (itemMaterial.equals(Material.ENDER_PEARL)) {
				if (willTele(snplayer)) {
					return true;
				}
				SuperNManager.alterPower(snplayer, SNConfigHandler.enderPearlPower, "Taken from pearl.");
				if (item.getAmount() == 1) {
					player.setItemInHand(null);
				} else {
					item.setAmount(item.getAmount() - 1);
				}
				event.setCancelled(true);
				return true;
			}
		}
		if (action.equals(Action.LEFT_CLICK_AIR)
				|| action.equals(Action.LEFT_CLICK_BLOCK)) {
			if (itemMaterial.equals(Material.ENDER_PEARL)) {
				SuperNManager.sendMessage(snplayer, "EnderPearl teleportation set to: "
						+ changeTele(snplayer));
			}
		}
		return false;
	}

}