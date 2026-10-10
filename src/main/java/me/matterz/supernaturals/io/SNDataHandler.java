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

package me.matterz.supernaturals.io;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Level;

import me.matterz.supernaturals.SuperNPlayer;
import me.matterz.supernaturals.SupernaturalsPlugin;
import me.matterz.supernaturals.util.Location;

import org.bukkit.World;
import org.bukkit.entity.Player;

public class SNDataHandler implements Serializable {

	/**
	 * Auto-Generated serialVersionUID
	 */
	private static final long serialVersionUID = 2266551481298554973L;

	private HashMap<SuperNPlayer, Location> teleportLocations = new HashMap<SuperNPlayer, Location>();
	private HashMap<SuperNPlayer, SuperNPlayer> angels = new HashMap<SuperNPlayer, SuperNPlayer>();
	private HashMap<SuperNPlayer, ArrayList<String>> hunterApps = new HashMap<SuperNPlayer, ArrayList<String>>();

	private static String path = "plugins/mmSupernaturals/storage.dat";

	// -------------------------------------------- //
	// Read/Write //
	// -------------------------------------------- //

	public void write() {
		try {
			ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(path));
			oos.writeObject(this);
			oos.flush();
			oos.close();
		} catch (Exception e) {
			SupernaturalsPlugin.log(Level.WARNING, "Storage Data could not be written!");
			e.printStackTrace();
		}
	}

	public static SNDataHandler read() {
		SNDataHandler handler = null;
		try {
			ObjectInputStream ois = new ObjectInputStream(new FileInputStream(path));
			handler = (SNDataHandler) ois.readObject();
			ois.close();
		} catch (Exception e) {
			if (SNConfigHandler.debugMode) {
				SupernaturalsPlugin.log(Level.WARNING, "Storage Data not found.");
			}
		}
		return handler;
	}

	// -------------------------------------------- //
	// Teleportation //
	// -------------------------------------------- //

	/**
	 * Records where this player is standing as their teleport point. Nothing is recorded for
	 * somebody the server does not have online - {@code getPlayer} answers with null then, and
	 * the old version dereferenced it straight away.
	 */
	public void addTeleport(SuperNPlayer player) {
		Player online = SupernaturalsPlugin.instance.getServer().getPlayer(player.getName());
		if (online == null) {
			return;
		}
		teleportLocations.put(player, new Location(online.getLocation()));
	}

	public boolean checkPlayer(SuperNPlayer player) {
		return getTeleport(player) != null;
	}

	/**
	 * The saved point as a Bukkit location, or null when there is none - or when the world it was
	 * saved in is not loaded any more.
	 *
	 * <p>Both of those nulls used to be a crash: the old version read the map, called
	 * {@code getWorld()} on whatever came back, and handed the result to {@code new Location(...)}
	 * without looking at any of it.
	 */
	public org.bukkit.Location getTeleport(SuperNPlayer player) {
		Location stored = teleportLocations.get(player);
		if (stored == null) {
			return null;
		}
		World world = stored.getWorld();
		if (world == null) {
			return null;
		}
		return new org.bukkit.Location(world, stored.getX(), stored.getY(), stored.getZ());
	}

	/** Forgets a point that can no longer be used, so the next check tells the truth. */
	public void removeTeleport(SuperNPlayer player) {
		teleportLocations.remove(player);
	}

	// -------------------------------------------- //
	// Guardian Angels //
	// -------------------------------------------- //

	public boolean hasAngel(SuperNPlayer snplayer) {
		if (angels.containsValue(snplayer)) {
			return true;
		}
		return false;
	}

	public void removeAngel(SuperNPlayer snplayer) {
		for (SuperNPlayer player : angels.keySet()) {
			if (angels.get(player).equals(snplayer)) {
				angels.remove(player);
			}
		}
	}

	public SuperNPlayer getAngelPlayer(SuperNPlayer snplayer) {
		return angels.get(snplayer);
	}

	public void addAngel(SuperNPlayer snplayer, SuperNPlayer sntarget) {
		angels.put(snplayer, sntarget);
	}

	// -------------------------------------------- //
	// WitchHunter Apps //
	// -------------------------------------------- //

	public ArrayList<String> getPlayerApp(SuperNPlayer player) {
		return hunterApps.get(player);
	}

	public void addPlayerApp(SuperNPlayer player, ArrayList<String> kills) {
		hunterApps.put(player, kills);
	}

	public boolean playerHasApp(SuperNPlayer player) {
		if (hunterApps.containsKey(player)) {
			return true;
		}
		return false;
	}

	public void removePlayerApp(SuperNPlayer player) {
		if (hunterApps.containsKey(player)) {
			hunterApps.remove(player);
		}
	}

}
