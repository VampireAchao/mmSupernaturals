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

import me.matterz.supernaturals.SupernaturalsPlugin;
import me.matterz.supernaturals.io.SNConfigHandler;

import org.bukkit.Location;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public class ArrowUtil implements Runnable {

	private Player player;
	private Arrow arrow;
	private Location targetLocation;
	private long startTime;
	private double grappleDistance;

	public ArrowUtil(Player player, Arrow arrow) {
		this.player = player;
		this.arrow = arrow;
	}

	public ArrowUtil(Player player, Location targetLocation) {
		this.player = player;
		this.targetLocation = targetLocation;
		startTime = System.currentTimeMillis();
		grappleDistance = player.getLocation().distance(targetLocation);
		arrow = null;
	}

	public ArrowUtil(Player player, Location targetLocation, long startTime,
			double grappleDistance) {
		this.player = player;
		this.targetLocation = targetLocation;
		this.startTime = startTime;
		this.grappleDistance = grappleDistance;
		arrow = null;
	}

	@Override
	public void run() {
		if (arrow != null) {
			SupernaturalsPlugin.instance.getHunterManager().splitArrow(player, arrow);
			return;
		} else {
			double away = player.getLocation().distance(targetLocation);
			if (away < 3) {
				// Why it stopped, and not that it ticked: a pull that ends immediately and a pull
				// that never started look the same from outside the server, and the two are told
				// apart by the one number the ability has - how far away the thing it hit was.
				if (SNConfigHandler.debugMode) {
					SupernaturalsPlugin.log("grapple for " + player.getName() + " stopped: what it "
							+ "caught is only " + Math.round(away) + " blocks away");
				}
				SupernaturalsPlugin.instance.getHunterManager().stopGrappling(player);
				return;
			}

			long elapsed = System.currentTimeMillis() - startTime;
			if (startTime + 500 + grappleDistance * 70 < System.currentTimeMillis()) {
				if (SNConfigHandler.debugMode) {
					SupernaturalsPlugin.log("grapple for " + player.getName() + " ran out of time: "
							+ elapsed + "ms spent of " + Math.round(500 + grappleDistance * 70)
							+ "ms allowed, " + Math.round(away) + " blocks still to go");
				}
				SupernaturalsPlugin.instance.getHunterManager().stopGrappling(player);
				return;
			}

			Vector travelVector = targetLocation.toVector().subtract(player.getLocation().toVector()).normalize();

			// Always move slightly upwards to combat the evil force of gravity!
			travelVector = travelVector.setY(travelVector.getY() + 0.2).normalize();

			// set travel speed
			travelVector = travelVector.multiply(1.0);
			player.setVelocity(travelVector);
			// Pulled through the air and let go at the end of it: without this the fall damage
			// arrives the moment the grapple releases, and the ability reads as hurting the
			// person who used it.
			player.setFallDistance(0);
			ArrowUtil gh = new ArrowUtil(player, targetLocation, startTime, grappleDistance);
			SupernaturalsPlugin.instance.getServer().getScheduler().scheduleSyncDelayedTask(SupernaturalsPlugin.instance, gh);
		}
	}
}