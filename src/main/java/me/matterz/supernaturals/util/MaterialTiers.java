package me.matterz.supernaturals.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.bukkit.Material;

/**
 * The families a material belongs to, so a rule written against one member covers them all.
 *
 * <p>Every rule in the plugin that picks a material by its quality - what a race may not hold,
 * what cannot hurt it, what an angel may not wear - is a rule about a <em>tier</em>, not about a
 * sword or a chestplate. Written out by hand, those rules drifted apart in the two ways a
 * hand-written list always does: a tier that arrived later was only half added, so diamond was
 * covered and its netherite equal was not, and a kind the game added was in no list at all, so a
 * spear was not covered by any of them. The result was a race that shrugged off a diamond sword
 * and was cut by the netherite one - the same tier - and by a diamond spear - the same rule.
 *
 * <p>So the kinds and the tiers are named once, here, and the rules ask this class instead.
 */
public final class MaterialTiers {

	/** The tool and weapon kinds a tier makes. */
	private static final List<String> WEAPON_KINDS =
			List.of("SWORD", "AXE", "PICKAXE", "SHOVEL", "HOE", "SPEAR");

	/** The armour pieces a tier makes. */
	private static final List<String> ARMOR_KINDS =
			List.of("HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS");

	/**
	 * Tiers that stand in for one another: a rule about one of them is a rule about all of them.
	 *
	 * <p>Netherite is diamond's upgrade rather than a material with rules of its own, so a rule
	 * that names one and not the other is not a rule - it is a way round the rule.
	 */
	private static final List<String> PAIRED_TIERS = List.of("DIAMOND", "NETHERITE");

	private MaterialTiers() {
	}

	/** The tier a material belongs to: what its name says before the first underscore. */
	public static String tierOf(Material material) {
		String name = material.name();
		int cut = name.indexOf('_');
		return cut < 0 ? name : name.substring(0, cut);
	}

	/** The kind of thing a material is: what its name says after the last underscore. */
	public static String kindOf(Material material) {
		String name = material.name();
		int cut = name.lastIndexOf('_');
		return cut < 0 ? "" : name.substring(cut + 1);
	}

	/**
	 * Every weapon and tool a tier makes, as far as this version of the game carries them.
	 *
	 * <p>Asked of the game rather than assumed: a tier with no spear in this version simply has no
	 * spear in the answer, so the same code is right on either side of an update.
	 */
	public static List<Material> weapons(String tier) {
		return ofKind(tier, WEAPON_KINDS);
	}

	/** Every armour piece a tier makes, as far as this version carries them. */
	public static List<Material> armour(String tier) {
		return ofKind(tier, ARMOR_KINDS);
	}

	private static List<Material> ofKind(String tier, List<String> kinds) {
		List<Material> out = new ArrayList<>();
		for (String kind : kinds) {
			Material material = Material.matchMaterial(tier + "_" + kind);
			if (material != null) {
				out.add(material);
			}
		}
		return out;
	}

	/** Whether a material is a weapon or tool of a tier that is paired with another. */
	public static boolean isPairedWeapon(Material material) {
		return material != null
				&& PAIRED_TIERS.contains(tierOf(material))
				&& WEAPON_KINDS.contains(kindOf(material));
	}

	/** Whether a material is a piece of armour of one of the named tiers. */
	public static boolean isArmourOfTier(Material material, String... tiers) {
		return material != null
				&& ARMOR_KINDS.contains(kindOf(material))
				&& List.of(tiers).contains(tierOf(material));
	}

	/**
	 * The tiers a list of materials names, with the tier that stands in for each of them.
	 *
	 * <p>This is the other way to read such a list, and the way a rule about a <em>substance</em>
	 * wants it: not "these twelve items are the rule" but "these materials are". A rule that reads
	 * its list this way cannot be argued with by changing the kind of the thing - a ghoul spared the
	 * diamond sword is spared the diamond spear, the diamond pickaxe, and whatever kind the game adds
	 * next - and naming diamond brings the netherite that stands in for it.
	 *
	 * <p>What a tier covers is settled by {@link #isToolOrWeaponOfTier}: tools and weapons, the two
	 * kinds whose whole purpose is to be swung. A diamond block is made of diamond and is not a
	 * gesture anybody would call a weapon, so it is out of scope - not refused, just not judged.
	 */
	public static Set<String> tiersOf(Collection<Material> materials) {
		Set<String> tiers = new LinkedHashSet<>();
		for (Material material : materials) {
			if (material == null || material.isAir()) {
				continue;
			}
			String tier = tierOf(material);
			if (tier.isEmpty()) {
				continue;
			}
			tiers.add(tier);
			if (PAIRED_TIERS.contains(tier)) {
				tiers.addAll(PAIRED_TIERS);
			}
		}
		return tiers;
	}

	/**
	 * Whether a material is a tool or a weapon - the two kinds a tier rule is about.
	 *
	 * <p>A sword, an axe, a pickaxe, a shovel, a hoe, a spear. Not a block, not a gem, not an ore,
	 * not armour: a race is judged on what it can be hit with and what it can hit with, and the
	 * difference between an iron ingot and an iron sword is not a material question.
	 */
	public static boolean isToolOrWeapon(Material material) {
		return material != null && !material.isAir() && WEAPON_KINDS.contains(kindOf(material));
	}

	/**
	 * Whether an item is a tool or a weapon made of one of these tiers.
	 *
	 * <p>The judgement a damage rule wants: the tier decides whether the substance can hurt, and the
	 * kind decides whether this is a thing the rule speaks about at all. Nothing here decides whether
	 * an item may be held, picked up or crafted - every tool and weapon stays obtainable, and the
	 * only thing a race's list changes is whether the swing lands.
	 */
	public static boolean isToolOrWeaponOfTier(Material material, Collection<String> tiers) {
		return isToolOrWeapon(material) && tiers.contains(tierOf(material));
	}

	/**
	 * A forbidden-weapon list, widened to every kind of every tier it names and to the tier that
	 * stands in for each of them.
	 *
	 * <p>This is what makes an existing configuration obey the rule without being rewritten: a list
	 * that names a diamond sword, written before any of this existed, means the diamond tier, and
	 * therefore the netherite tier, in all six kinds. Nothing in the file has to be touched, and a
	 * server that was configured years ago is corrected by the same code as a fresh one.
	 *
	 * <p>Widening is right here because the list says what a race may <em>not</em> hold: every item
	 * it grows by is an item the rule was already meant to cover.
	 */
	public static List<Material> completeWeapons(Collection<Material> listed) {
		return complete(listed, WEAPON_KINDS);
	}

	/**
	 * An allowed-armour list, with the tier of every piece paired - and nothing else added.
	 *
	 * <p>Armour lists are the other way round: they say what a race <em>may</em> wear, and
	 * everything else is taken off, so inventing a kind would hand out a slot the server never
	 * handed out. What must not differ is the tier: a list that allows a diamond chestplate has to
	 * allow the netherite one, or the upgrade is a way round the list. So each piece brings its own
	 * counterpart and no other kind follows it.
	 */
	public static List<Material> completeArmour(Collection<Material> listed) {
		Set<Material> out = new LinkedHashSet<>();
		for (Material material : listed) {
			if (material == null) {
				continue;
			}
			out.add(material);
			if (!ARMOR_KINDS.contains(kindOf(material)) || !PAIRED_TIERS.contains(tierOf(material))) {
				continue;
			}
			for (String tier : PAIRED_TIERS) {
				Material counterpart = Material.matchMaterial(tier + "_" + kindOf(material));
				if (counterpart != null) {
					out.add(counterpart);
				}
			}
		}
		return new ArrayList<>(out);
	}

	private static List<Material> complete(Collection<Material> listed, List<String> kinds) {
		Set<Material> out = new LinkedHashSet<>();
		Set<String> tiers = new LinkedHashSet<>();
		for (Material material : listed) {
			if (material == null) {
				continue;
			}
			out.add(material);
			if (kinds.contains(kindOf(material))) {
				tiers.add(tierOf(material));
			}
		}
		for (String tier : new ArrayList<>(tiers)) {
			if (PAIRED_TIERS.contains(tier)) {
				tiers.addAll(PAIRED_TIERS);
			}
		}
		for (String tier : tiers) {
			out.addAll(ofKind(tier, kinds));
		}
		return new ArrayList<>(out);
	}
}
