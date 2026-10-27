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

package me.matterz.supernaturals.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class Recipes {
	public Map<Material, Integer> materialQuantities = new HashMap<Material, Integer>();

	public void removeFromPlayer(Player player) {
		Inventory inventory = player.getInventory();
		for (Material material : materialQuantities.keySet()) {
			inventory.removeItem(new ItemStack(material, materialQuantities.get(material)));
		}
		player.updateInventory();
	}

	public boolean playerHasEnough(Player player) {
		Inventory inventory = player.getInventory();
		for (Material material : materialQuantities.keySet()) {
			if (getMaterialCountFromInventory(material, inventory) < materialQuantities.get(material)) {
				return false;
			}
		}
		return true;
	}

	public static int getMaterialCountFromInventory(Material material, Inventory inventory) {
		int count = 0;
		for (ItemStack stack : inventory.all(material).values()) {
			count += stack.getAmount();
		}
		return count;
	}

	public String getRecipeLine() {
		ArrayList<String> lines = new ArrayList<String>();
		for (Entry<Material, Integer> item : entriesSortedByValues(materialQuantities)) {
			lines.add("" + item.getValue() + " "
					+ TextUtil.getMaterialName(item.getKey()));
		}
		return TextUtil.implode(lines, ", ");
	}

	/**
	 * The entries of that map, smallest value first, ties in a fixed order.
	 *
	 * <p>A list rather than a sorted set: a set has to call equal values "the same entry" and
	 * drop one of them, and dodging that needs a comparator that lies about equality. A list
	 * has no such trap to step around. Written here rather than lifted out of an answer on a
	 * Q&A site, so that the licence on this file is the only licence in it.
	 */
	public static <K, V extends Comparable<? super V>> List<Map.Entry<K, V>> entriesSortedByValues(
			Map<K, V> map) {
		List<Map.Entry<K, V>> entries = new ArrayList<Map.Entry<K, V>>(map.entrySet());
		entries.sort(Comparator.<Map.Entry<K, V>, V>comparing(Entry::getValue)
				.thenComparing(entry -> String.valueOf(entry.getKey())));
		return entries;
	}
}
