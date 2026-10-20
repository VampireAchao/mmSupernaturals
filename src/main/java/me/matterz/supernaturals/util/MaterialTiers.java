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
	 * A weapon list with every kind of every tier it names, and every tier that stands in for one it
	 * names.
	 *
	 * <p>This is what makes an existing configuration obey the rule without being rewritten: a list
	 * that names a diamond sword, written before any of this existed, means the diamond tier, and
	 * therefore the netherite tier, in all six kinds. Nothing in the file has to be touched, and a
	 * server that was configured years ago is corrected by the same code as a fresh one.
	 */
	public static List<Material> completeWeapons(Collection<Material> listed) {
		return complete(listed, WEAPON_KINDS);
	}

	/** The same, for a list of armour. */
	public static List<Material> completeArmour(Collection<Material> listed) {
		return complete(listed, ARMOR_KINDS);
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
