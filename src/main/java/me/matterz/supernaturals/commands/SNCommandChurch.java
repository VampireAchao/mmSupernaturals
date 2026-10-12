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

package me.matterz.supernaturals.commands;

import java.util.ArrayList;

import me.matterz.supernaturals.SupernaturalsPlugin;
import me.matterz.supernaturals.io.SNConfigHandler;

import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Manages where a church is.
 *
 * <p>A church used to be a single point in the config file, and an altar only answered within
 * ten blocks of it - so a server with two chapels could only ever have one that worked, and a
 * hall built in a world of its own could not be a church at all. Now there is a list, and this
 * is how it is kept: {@code add} registers where the person running it is standing, {@code
 * remove} forgets the nearest one, {@code list} reads them back and {@code clear} starts over.
 * {@code /sn setchurch} is still the one-liner for "there is one church and it is here".
 */
public class SNCommandChurch extends SNCommand {

	public SNCommandChurch() {
		super();
		requiredParameters = new ArrayList<String>();
		optionalParameters = new ArrayList<String>();
		optionalParameters.add("add|remove|list|clear");
		senderMustBePlayer = false;
		senderMustBeSupernatural = false;
		permissions = "supernatural.admin.command.church";
		helpNameAndParams = "church <add|remove|list|clear>";
		helpDescription = "Manages the churches an altar can belong to";
	}

	@Override
	public void perform() {
		String action = parameters.isEmpty() ? "list" : parameters.get(0).toLowerCase();
		switch (action) {
			case "add":
				add();
				break;
			case "remove":
				remove();
				break;
			case "clear":
				clear();
				break;
			case "list":
				list();
				break;
			default:
				sendMessage("Usage: /sn church <add|remove|list|clear>");
				break;
		}
	}

	private void list() {
		if (sender instanceof Player player
				&& !SupernaturalsPlugin.hasPermissions(player, permissions)) {
			sendMessage("You do not have permissions to use this command.");
			return;
		}
		if (SNConfigHandler.priestChurches.isEmpty()) {
			sendMessage("No churches are registered.");
			return;
		}
		sendMessage("Churches (" + SNConfigHandler.priestChurches.size() + "):");
		for (Location church : SNConfigHandler.priestChurches) {
			sendMessage("  " + SNConfigHandler.describeChurch(church));
		}
	}

	private void add() {
		if (!(sender instanceof Player)) {
			sendMessage("Only a player can register where a church is.");
			return;
		}
		Player player = (Player) sender;
		if (!SupernaturalsPlugin.hasPermissions(player, permissions)) {
			sendMessage("You do not have permissions to use this command.");
			return;
		}
		SNConfigHandler.addChurch(player.getLocation());
		sendMessage("Church registered at " + SNConfigHandler.describeChurch(player.getLocation())
				+ " (" + SNConfigHandler.priestChurches.size() + " in total)");
	}

	private void remove() {
		if (!(sender instanceof Player)) {
			sendMessage("Only a player can say which church to remove.");
			return;
		}
		Player player = (Player) sender;
		if (!SupernaturalsPlugin.hasPermissions(player, permissions)) {
			sendMessage("You do not have permissions to use this command.");
			return;
		}
		if (SNConfigHandler.removeChurchNear(player.getLocation())) {
			sendMessage("Church removed (" + SNConfigHandler.priestChurches.size() + " left)");
		} else {
			sendMessage("There is no church within " + SNConfigHandler.PRIEST_CHURCH_RADIUS
					+ " blocks of you.");
		}
	}

	private void clear() {
		if (sender instanceof Player && !SupernaturalsPlugin.hasPermissions((Player) sender, permissions)) {
			sendMessage("You do not have permissions to use this command.");
			return;
		}
		int removed = SNConfigHandler.priestChurches.size();
		SNConfigHandler.clearChurches();
		sendMessage("Removed " + removed + " churches.");
	}
}
