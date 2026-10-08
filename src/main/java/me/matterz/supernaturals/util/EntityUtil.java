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

import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;

public class EntityUtil {

	/**
	 * Resolves the Bukkit type of an entity.
	 * <p>
	 * This used to guess the type from the CraftBukkit class name, which only worked for
	 * {@code Creature}s and silently returned {@code null} for hostile mobs that are not
	 * creatures (Ghast, Phantom, EnderDragon, ...). Those mobs could therefore never match
	 * the truce lists, so a vampire with an intact truce still got attacked by them.
	 *
	 * @param entity the entity to inspect, may be {@code null}
	 * @return the entity type, or {@code null} when there is no entity
	 */
	public static EntityType entityTypeFromEntity(Entity entity) {
		if (entity == null) {
			return null;
		}

		return entity.getType();
	}
}
