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
import me.matterz.supernaturals.manager.HunterManager;

import org.bukkit.Location;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public class ArrowUtil implements Runnable {

	/** How far short of what it caught a pull lets go, in blocks. */
	private static final double STOPS_SHORT = 3;

	private Player player;
	private Arrow arrow;
	private Location targetLocation;
	private long startTime;
	private double grappleDistance;

	/**
	 * Which pull this task is, and what the shot that started it cost.
	 *
	 * <p>A pull is named by a number rather than by being "the pull for this player", because a
	 * player can shoot again while one is still running and the two then have to be told apart.
	 * The only thing a task in flight knows is what it was told when it was made, so without a
	 * name the older pull cannot recognise itself as replaced: it keeps moving a player the newer
	 * one has already aimed somewhere else, and both of them pull at once. That is one block per
	 * tick per pull, so two pulls are two blocks a tick and a ride that reads as a teleport.
	 */
	private long token;

	/** What the shot cost, so a pull that moves nobody can hand it back. */
	private int spentPower;

	/** Whether this pull has moved its player at least once. */
	private boolean moved;

	public ArrowUtil(Player player, Arrow arrow) {
		this.player = player;
		this.arrow = arrow;
	}

	public ArrowUtil(Player player, Location targetLocation, long token, int spentPower) {
		this.player = player;
		this.targetLocation = targetLocation;
		this.token = token;
		this.spentPower = spentPower;
		this.startTime = System.currentTimeMillis();
		this.grappleDistance = player.getLocation().distance(targetLocation);
		this.arrow = null;
	}

	/** The next tick of a pull that is already running: same token, same debt, one more step. */
	private ArrowUtil(Player player, Location targetLocation, long startTime, double grappleDistance,
			long token, int spentPower, boolean moved) {
		this.player = player;
		this.targetLocation = targetLocation;
		this.startTime = startTime;
		this.grappleDistance = grappleDistance;
		this.token = token;
		this.spentPower = spentPower;
		this.moved = moved;
		this.arrow = null;
	}

	@Override
	public void run() {
		HunterManager hunter = SupernaturalsPlugin.instance.getHunterManager();
		if (arrow != null) {
			hunter.splitArrow(player, arrow);
			return;
		}

		// Whose pull is this, still? One that has been replaced by a later shot has nothing left
		// to do: it must not move him (that is the second block per tick), must not clear his
		// speed, and must not call the end of the pull that replaced it. The one thing it does
		// still owe him is the power, if it never got him anywhere.
		if (!hunter.ownsGrapple(player, token)) {
			hunter.refundGrapple(player, spentPower, moved, "a later shot took over the pull");
			return;
		}

		if (!player.isOnline()) {
			// There is nobody to move. Power is left alone: he is not here to spend it either.
			hunter.stopGrappling(player, token);
			return;
		}

		// Location#distance throws for two places in different worlds rather than answering, and a
		// pull is a player and a target that can be parted by a teleport or a portal. That is the
		// one case the ability has nothing to say about, so it ends here instead of throwing out
		// of a repeating task.
		if (player.getWorld() != null && targetLocation.getWorld() != null
				&& !player.getWorld().equals(targetLocation.getWorld())) {
			debug("what it caught is in " + targetLocation.getWorld().getName()
					+ ", and he is not");
			hunter.refundGrapple(player, spentPower, moved, "what it caught is in another world");
			hunter.stopGrappling(player, token);
			return;
		}

		double away = player.getLocation().distance(targetLocation);
		if (away < STOPS_SHORT) {
			// Why it stopped, and not that it ticked: a pull that ends immediately and a pull that
			// never started look the same from outside the server, and the two are told apart by
			// the one number the ability has - how far away the thing it hit was.
			debug("what it caught is only " + Math.round(away) + " blocks away");
			hunter.refundGrapple(player, spentPower, moved,
					"it caught something only " + Math.round(away) + " blocks away");
			hunter.stopGrappling(player, token);
			return;
		}

		long elapsed = System.currentTimeMillis() - startTime;
		if (startTime + 500 + grappleDistance * 70 < System.currentTimeMillis()) {
			debug("ran out of time: " + elapsed + "ms spent of "
					+ Math.round(500 + grappleDistance * 70) + "ms allowed, "
					+ Math.round(away) + " blocks still to go");
			hunter.refundGrapple(player, spentPower, moved, "it ran out of time");
			hunter.stopGrappling(player, token);
			return;
		}

		// The pull is a move, not a push.
		//
		// Velocity was the whole of this once, and it never moved anybody: a player's position is
		// the client's to report, so a speed set on the server is overwritten by the next movement
		// packet - and by the server's own ground physics - before the motion update it sends can
		// mean anything. It also cannot be checked here at all: a stand-in with no client keeps the
		// speed in a field and stands still, so "the pull ran" and "the pull did nothing" look
		// identical from inside the hall. That is why this read as a working ability for as long as
		// it has.
		//
		// A teleport is the server's own statement about where somebody is, and both sides of the
		// wire accept it. One block a tick, which is the speed the old multiplier asked for and the
		// speed the time budget above is sized for.
		Vector travelVector = targetLocation.toVector()
				.subtract(player.getLocation().toVector()).normalize();

		// Always move slightly upwards to combat the evil force of gravity!
		travelVector = travelVector.setY(travelVector.getY() + 0.2).normalize();

		Location from = player.getLocation().clone();
		Location next = from.clone().add(travelVector);
		// A teleport keeps the place it is given and nothing else, so the way he is looking has to
		// be carried across - otherwise the pull turns the player round every tick.
		next.setYaw(player.getLocation().getYaw());
		next.setPitch(player.getLocation().getPitch());
		if (!player.teleport(next)) {
			// Something else refused the move - a region plugin, a world border. Asking again next
			// tick would be refused again, and the pull has nowhere to put him.
			debug("the move to " + next.getBlockX() + "," + next.getBlockY() + ","
					+ next.getBlockZ() + " was refused");
			hunter.refundGrapple(player, spentPower, moved, "the move was refused");
			hunter.stopGrappling(player, token);
			return;
		}
		// A little speed the same way, so the ride reads as one pull rather than as a row of
		// one-block steps, and so the client's own prediction agrees with where it has just been
		// put. It is not what moves him.
		if (SNConfigHandler.debugMode) {
			// One line a step, with the tick it happened on: a step taken twice in one tick and a
			// step twice as long read the same from a position sampled once a tick.
			SupernaturalsPlugin.log("grapple for " + player.getName() + " token " + token
					+ " tick " + player.getServer().getCurrentTick()
					+ " step " + where(from) + " -> " + where(next));
		}
		player.setVelocity(travelVector.clone().multiply(0.4));
		// Pulled through the air and let go at the end of it: without this the fall damage arrives
		// the moment the grapple releases, and the ability reads as hurting the person who used it.
		player.setFallDistance(0);
		renew(hunter, true);
	}

	/**
	 * One more tick of this same pull - a tick later, not as soon as the scheduler will have it.
	 *
	 * <p>Delay zero does not mean "next tick" on this server: a task scheduled without a delay
	 * from inside a task is picked up in the same tick, so this used to run its whole chain before
	 * the tick ended. A fifteen block pull therefore arrived in one tick, as a teleport, while
	 * the board said one block a tick - and the time budget was the only thing that ever stopped
	 * it. One tick is the speed the ability is written to move at, and the speed the ride reads
	 * as a ride at.
	 */
	private void renew(HunterManager hunter, boolean hasMoved) {
		ArrowUtil next = new ArrowUtil(player, targetLocation, startTime, grappleDistance, token,
				spentPower, hasMoved);
		SupernaturalsPlugin.instance.getServer().getScheduler()
				.scheduleSyncDelayedTask(SupernaturalsPlugin.instance, next, 1L);
	}

	/** A place as three whole numbers, for a probe reply that has to stay readable. */
	private static String where(Location at) {
		return at.getBlockX() + "," + at.getBlockY() + "," + at.getBlockZ();
	}

	/** One line per pull, and only when somebody asked to read them. */
	private void debug(String what) {
		if (SNConfigHandler.debugMode) {
			SupernaturalsPlugin.log("grapple for " + player.getName() + " stopped: " + what);
		}
	}
}
