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

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;

import me.matterz.supernaturals.SupernaturalsPlugin;
import me.matterz.supernaturals.util.Recipes;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.Configuration;
import org.bukkit.entity.EntityType;

public class SNConfigHandler {

	public static SupernaturalsPlugin plugin;

	// Config variables
	public static Configuration config;
	public static boolean debugMode;
	public static boolean vampireKillSpreadCurse;
	public static boolean ghoulKillSpreadCurse;
	public static boolean ghoulRightClickSummon;
	public static boolean wereKillSpreadCurse;
	public static boolean vampireBurnInSunlight;
	public static boolean vampireBurnMessageEnabled;
	public static boolean wolfTruce;
	public static boolean enableColors;
	public static boolean multiworld;
	public static boolean spanish;
	public static boolean enableLoginMessage;
	public static boolean enableJoinCommand;
	public static double spreadChance;
	public static double vampireDamageFactor;
	public static double ghoulDamageFactor;
	public static double woodFactor;
	public static double vampireDamageReceivedFactor;
	public static double ghoulDamageReceivedFactor;
	public static double jumpDeltaSpeed;
	public static double angelJumpDeltaSpeed;
	public static double dashDeltaSpeed;
	public static double ghoulHealthGained;
	public static double wereHealthGained;
	public static double vampireAltarInfectMaterialRadius;
	public static double vampireAltarCureMaterialRadius;
	public static double vampireTimePowerGained;
	public static double vampireTimeHealthGained;
	public static double vampireHealthCost;
	public static double wereDamageFall;
	public static double wereDamageFactor;
	public static double priestDamageFactorAttackSuper;
	public static double priestDamageFactorAttackHuman;
	public static double enderDamageReceivedFactor;
	public static double priestDrainFactor;
	public static double hunterPowerArrowDamage;
	public static double enderDamageFactor;
	public static int jumpBloodCost;
	public static int dashBloodCost;
	public static int truceBreakTime;
	public static int vampireAltarInfectMaterialSurroundCount;
	public static int vampireAltarCureMaterialSurroundCount;
	public static int vampirePowerStart;
	public static int ghoulPowerStart;
	public static int ghoulDamageWater;
	public static int werePowerStart;
	public static int vampireDeathPowerPenalty;
	public static int ghoulDeathPowerPenalty;
	public static int wereDeathPowerPenalty;
	public static int priestDeathPowerPenalty;
	public static int vampireKillPowerCreatureGain;
	public static int ghoulKillPowerCreatureGain;
	public static int wereKillPowerCreatureGain;
	public static int vampireKillPowerPlayerGain;
	public static int ghoulKillPowerPlayerGain;
	public static int wereKillPowerPlayerGain;
	public static int angelHealHealthGain;
	public static int angelHealPowerCost;
	public static int angelSummonPowerCost;
	public static int angelCurePowerCost;
	public static int angelJumpPowerCost;
	public static int angelSwimPowerGain;
	public static int angelKillMonsterPowerGain;
	public static int vampireCombustFireTicks;
	public static int vampireDrowningCost;
	public static int vampireTeleportCost;
	public static int vampireHungerRegainPlayer;
	public static int vampireHungerRegainMob;
	public static int priestPowerBanish;
	public static int priestPowerHeal;
	public static int priestPowerCure;
	public static int priestPowerExorcise;
	public static int priestPowerDrain;
	public static int priestPowerGuardianAngel;
	public static int priestHealAmount;
	public static int priestPowerStart;
	public static int priestFireTicks;
	public static int werePowerSummonCost;
	public static int werePowerFood;
	public static int ghoulPowerSummonCost;
	public static int ghoulPowerBond;
	public static int demonHealing;
	public static int demonDeathPowerPenalty;
	public static int demonPowerFireball;
	public static int demonPowerSnare;
	public static int demonSnareDuration;
	public static int demonPowerGain;
	public static int demonPowerLoss;
	public static int demonPowerStart;
	public static int demonKillPowerCreatureGain;
	public static int demonKillPowerPlayerGain;
	public static int demonSnowballAmount;
	public static int demonFireballDamage;
	public static int demonFireTicks;
	public static int demonConvertPower;
	public static int hunterDeathPowerPenalty;
	public static int hunterPowerArrowFire;
	public static int hunterPowerArrowTriple;
	public static int hunterPowerArrowGrapple;
	public static int hunterPowerArrowPower;
	public static int hunterCooldown;
	public static int hunterKillPowerPlayerGain;
	public static int hunterKillPowerCreatureGain;
	public static int hunterFallReduction;
	public static int hunterFireArrowFireTicks;
	public static int hunterPowerStart;
	public static int hunterMaxBounties;
	public static int hunterBountyCompletion;
	public static int enderProtectPower;
	public static int enderDeathPowerPenalty;
	public static int enderKillPower;
	public static int enderPearlPower;
	public static String vampireAltarInfectMaterial;
	public static String vampireAltarCureMaterial;
	public static String vampireAltarInfectMaterialSurround;
	public static String vampireAltarCureMaterialSurround;
	public static String vampireHelmet;
	public static String priestAltarMaterial;
	public static String priestSpellGuardianAngel;
	public static String wolfMaterial;
	public static String wolfbaneMaterial;
	public static String ghoulMaterial;
	public static String ghoulBondMaterial;
	public static String vampireMaterial;
	public static String vampireTeleportMaterial;
	public static String jumpMaterial;
	public static String dashMaterial;
	public static String demonMaterial;
	public static String demonSnareMaterial;
	public static String hunterHallMessage;
	public static Location priestChurchLocation;
	public static Location priestBanishLocation;
	public static List<String> supernaturalTypes = new ArrayList<String>();
	public static List<String> hunterArrowTypes = new ArrayList<String>();

	public static List<Material> woodMaterials = new ArrayList<Material>();
	public static List<EntityType> vampireTruce = new ArrayList<EntityType>();
	public static List<Material> foodMaterials = new ArrayList<Material>();
	public static List<Material> ghoulWeapons = new ArrayList<Material>();
	public static List<Material> demonWeapons = new ArrayList<Material>();
	public static List<Material> priestWeapons = new ArrayList<Material>();
	public static List<Material> vampireWeapons = new ArrayList<Material>();
	public static List<Material> hunterWeapons = new ArrayList<Material>();
	public static List<Material> wereWeapons = new ArrayList<Material>();
	public static List<Material> ghoulWeaponImmunity = new ArrayList<Material>();
	public static List<EntityType> ghoulTruce = new ArrayList<EntityType>();
	public static List<Material> priestSpellMaterials = new ArrayList<Material>();
	public static HashMap<Material, Integer> priestDonationMap = new HashMap<Material, Integer>();
	public static List<Material> burnableBlocks = new ArrayList<Material>();
	public static List<Material> hunterArmor = new ArrayList<Material>();
	public static List<Material> ghoulArmor = new ArrayList<Material>();
	public static List<Material> demonArmor = new ArrayList<Material>();
	public static List<Material> priestArmor = new ArrayList<Material>();
	public static List<Material> vampireArmor = new ArrayList<Material>();
	public static List<Material> wereArmor = new ArrayList<Material>();
	public static List<Material> enderWeapons = new ArrayList<Material>();

	public static String priestChurchWorld;
	public static int priestChurchLocationX;
	public static int priestChurchLocationY;
	public static int priestChurchLocationZ;
	public static String priestBanishWorld;
	public static int priestBanishLocationX;
	public static int priestBanishLocationY;
	public static int priestBanishLocationZ;

	private static List<String> ghoulWeaponsString = new ArrayList<String>();
	private static List<String> demonWeaponsString = new ArrayList<String>();
	private static List<String> priestWeaponsString = new ArrayList<String>();
	private static List<String> hunterWeaponsString = new ArrayList<String>();
	private static List<String> vampireWeaponsString = new ArrayList<String>();
	private static List<String> wereWeaponsString = new ArrayList<String>();
	private static List<String> ghoulWeaponImmunityString = new ArrayList<String>();
	private static List<String> enderWeaponsString = new ArrayList<String>();
	private static List<String> woodMaterialsString = new ArrayList<String>();
	private static List<String> vampireTruceString = new ArrayList<String>();
	private static List<String> foodMaterialsString = new ArrayList<String>();
	private static List<String> ghoulTruceString = new ArrayList<String>();
	private static List<String> vampireAltarInfectMaterialsString = new ArrayList<String>();
	private static List<String> vampireAltarCureMaterialsString = new ArrayList<String>();
	private static List<Integer> vampireAltarCureQuantities = new ArrayList<Integer>();
	private static List<Integer> vampireAltarInfectQuantities = new ArrayList<Integer>();
	private static List<String> priestMaterialsString = new ArrayList<String>();
	private static List<String> priestAltarMaterialsString = new ArrayList<String>();
	private static List<Integer> priestAltarQuantities = new ArrayList<Integer>();
	private static List<String> priestDonationMaterialsString = new ArrayList<String>();
	private static List<String> wereWolfbaneMaterialsString = new ArrayList<String>();
	private static List<Integer> wereWolfbaneQuantities = new ArrayList<Integer>();
	private static List<Integer> priestDonationRewards = new ArrayList<Integer>();
	private static List<String> burnableBlocksString = new ArrayList<String>();
	private static List<String> hunterArmorString = new ArrayList<String>();
	private static List<String> ghoulArmorString = new ArrayList<String>();
	private static List<String> demonArmorString = new ArrayList<String>();
	private static List<String> priestArmorString = new ArrayList<String>();
	private static List<String> vampireArmorString = new ArrayList<String>();
	private static List<String> wereArmorString = new ArrayList<String>();

	public static Set<Material> transparent = new HashSet<Material>();

	public static Recipes vampireAltarInfectRecipe = new Recipes();
	public static Recipes vampireAltarCureRecipe = new Recipes();
	public static Recipes priestAltarRecipe = new Recipes();
	public static Recipes wereWolfbaneRecipe = new Recipes();

	static {
		transparent.add(Material.WATER);
		transparent.add(Material.AIR);
	}

	public SNConfigHandler(SupernaturalsPlugin instance) {
		SNConfigHandler.plugin = instance;
	}

	public static void getConfiguration() {
		config = plugin.getConfig();
		loadValues(config);
	}

	public static void loadValues(Configuration config) {
		File configFile = new File(plugin.getDataFolder(), "config.yml");
		if (SNVersionHandler.readVersion() != plugin.getDescription().getVersion() && configFile.exists()) {
			saveConfig();
			SNVersionHandler.writeVersion();
		}
		if (!configFile.exists()) {
			config.set("Spanish", false);
			config.set("DebugMode", false);
			config.set("MultiWorld", false);
			config.set("EnableChatColors", true);
			config.set("EnableLoginMessage", true);
			config.set("EnableJoinCommand", false);
			config.set("Supernatural.Truce.BreakTime", 120000);
			config.set("Supernatural.SpreadChance", 0.35);

			config.set("Vampire.Materials.Jump", "POPPY");

			config.set("Vampire.Power.Start", 10000);
			config.set("Vampire.Kill.SpreadCurse", true);
			config.set("Vampire.Time.PowerGained", 15);
			config.set("Vampire.Power.Kill.CreatureGain", 100);
			config.set("Vampire.Power.Kill.PlayerGain", 500);
			config.set("Vampire.Power.DeathPenalty", 10000);
			config.set("Vampire.DamageFactor.AttackBonus", 0.3);
			config.set("Vampire.DamageFactor.DefenseBonus", 0.8);
			config.set("Vampire.DamageFactor.Wood", 1.5);
			config.set("Vampire.Burn.InSunlight", true);
			config.set("Vampire.Burn.MessageEnabled", true);
			config.set("Vampire.Burn.FireTicks", 3);

			config.set("Vampire.JumpDelta", 1.2);
			config.set("Vampire.Power.JumpCost", 1000);
			config.set("Vampire.Time.HealthGained", 0.5);
			config.set("Vampire.Power.HealingCost", 60);
			config.set("Vampire.Power.DrowningCost", 90);
			config.set("Vampire.Power.TeleportCost", 9000);
			config.set("Vampire.TeleportMarker.Material", "POPPY");
			config.set("Vampire.Spell.Material", "BOOK");
			config.set("Vampire.Burn.HelmetProtection", "GOLDEN_HELMET");
			config.set("Vampire.GainHunger.Player", 3);
			config.set("Vampire.GainHunger.Mob", 2);

			config.set("Vampire.Altar.Infect.Material", "GOLD_BLOCK");
			config.set("Vampire.Altar.Infect.Surrounding.Material", "OBSIDIAN");
			config.set("Vampire.Altar.Infect.Surrounding.Radius", 7D);
			config.set("Vampire.Altar.Infect.Surrounding.Count", 20);

			config.set("Vampire.Altar.Cure.Material", "LAPIS_BLOCK");
			config.set("Vampire.Altar.Cure.Surrounding.Material", "GLOWSTONE");
			config.set("Vampire.Altar.Cure.Surrounding.Radius", 7D);
			config.set("Vampire.Altar.Cure.Surrounding.Count", 20);

			config.set("Priest.Church.World", "world");
			config.set("Priest.Church.Location.X", 0);
			config.set("Priest.Church.Location.Y", 80);
			config.set("Priest.Church.Location.Z", 0);
			config.set("Priest.Banish.World", "world");
			config.set("Priest.Banish.Location.X", 0);
			config.set("Priest.Banish.Location.Y", 80);
			config.set("Priest.Banish.Location.Z", 0);

			config.set("Priest.Power.StartingAmount", 10000);
			config.set("Priest.Power.DeathPenalty", 2000);
			config.set("Priest.DamageFactor.AttackBonusSuper", 1.0);
			config.set("Priest.DamageFactor.AttackBonusHuman", 0);
			config.set("Priest.Power.Banish", 4000);
			config.set("Priest.Power.HealOther", 1000);
			config.set("Priest.Spell.HealAmount", 10);
			config.set("Priest.Power.Exorcise", 9000);
			config.set("Priest.Power.Cure", 1000);
			config.set("Priest.Power.Drain", 1000);
			config.set("Priest.Power.GuardianAngel", 5000);
			config.set("Priest.Spell.DrainFactor", 0.15);
			config.set("Priest.DamageFactor.FireTicks", 50);
			config.set("Priest.Church.AltarMaterial", "DIAMOND_BLOCK");
			// WOOL stopped being a material when the wool items were split by colour, and the
			// spell is matched by name, so a default of "WOOL" left guardian angel permanently
			// uncastable on any server that had not edited it by hand.
			config.set("Priest.Spell.Material.GuardianAngel", "WHITE_WOOL");

			config.set("Ghoul.Power.Start", 5000);
			config.set("Ghoul.Kill.SpreadCurse", true);
			config.set("Ghoul.Power.Kill.CreatureGain", 200);
			config.set("Ghoul.Power.Kill.PlayerGain", 1000);
			config.set("Ghoul.Power.DeathPenalty", 2000);
			config.set("Ghoul.DamageFactor.DefenseBonus", 0.65);
			config.set("Ghoul.DamageFactor.AttackBonus", 2);
			config.set("Ghoul.WaterDamage", 4);
			config.set("Ghoul.Time.HealthGained", 0.1);
			config.set("Ghoul.Summon.Material", "PORKCHOP");
			config.set("Ghoul.UnholyBond.Material", "BONE");
			config.set("Ghoul.Power.Summon", 1000);
			config.set("Ghoul.Power.UnholyBond", 50);
			config.set("Ghoul.RightClickSummon", false);

			config.set("Were.DashDelta", 4);
			config.set("Were.Power.Dash", 400);

			config.set("Were.Power.Start", 5000);
			config.set("Were.Kill.SpreadCurse", true);
			config.set("Were.Power.Kill.CreatureGain", 20);
			config.set("Were.Power.Kill.PlayerGain", 100);
			config.set("Were.Power.Food", 100);
			config.set("Were.Power.DeathPenalty", 2000);
			config.set("Were.DamageFactor.Fall", 0.5);
			config.set("Were.DamageFactor.AttackBonus", 5);
			config.set("Were.Time.HealthGained", 0.2);
			config.set("Were.Material.Summon", "PORKCHOP");
			config.set("Were.Power.Summon", 2000);
			config.set("Were.WolfTruce", true);
			config.set("Were.Material.Dash", "FEATHER");
			config.set("Were.Wolfbane.Trigger", "BOWL");

			config.set("Demon.Power.Start", 10000);
			config.set("Demon.Power.DeathPenalty", 10000);
			config.set("Demon.Power.CreatureKill", 20);
			config.set("Demon.Power.PlayerKill", 100);
			config.set("Demon.Power.Gain", 40);
			config.set("Demon.Power.Loss", 4);
			config.set("Demon.Power.Fireball", 2000);
			config.set("Demon.Power.Convert", 2000);
			config.set("Demon.Healing", 1);
			config.set("Demon.Fireball.Material", "REDSTONE");
			config.set("Demon.Fireball.Damage", 10);
			config.set("Demon.Power.Snare", 1000);
			config.set("Demon.Snare.Duration", 10000);
			config.set("Demon.Snare.Material", "INK_SAC");
			config.set("Demon.SnowballAmount", 30);
			config.set("Demon.DamageFactor.FireTicks", 50);

			config.set("WitchHunter.Power.StartingPower", 10000);
			config.set("WitchHunter.Power.DeathPenalty", 500);
			config.set("WitchHunter.Power.PlayerKill", 2000);
			config.set("WitchHunter.Power.CreatureKill", 0);
			config.set("WitchHunter.Bounty.CompletionBonus", 8000);
			config.set("WitchHunter.Power.ArrowFire", 100);
			config.set("WitchHunter.Power.ArrowTriple", 100);
			config.set("WitchHunter.Power.ArrowGrapple", 500);
			config.set("WitchHunter.Power.ArrowPower", 1000);
			config.set("WitchHunter.ArrowPower.DamageFactor", 2.0);
			config.set("WitchHunter.PowerArrow.Cooldown", 15000);
			config.set("WitchHunter.FallReduction", 3);
			config.set("WitchHunter.FireArrow.FireTicks", 100);
			config.set("WitchHunter.Hall.Message", "WitchHunter");
			config.set("WitchHunter.Bounty.MaxNumber", 5);

			config.set("EnderBorn.DamageFactor.AttackBonus", 0.3);
			config.set("EnderBorn.DamageFactor.DefenseBonus", 0.5);
			config.set("EnderBorn.Power.Protect", 500);
			config.set("EnderBorn.Power.DeathPenalty", 300);
			config.set("EnderBorn.Power.Kill", 50);
			config.set("EnderBorn.Power.EnderPearl", 100);

			config.set("Angel.Power.Heal.HealthGain", 5);
			config.set("Angel.Power.Heal.PowerCost", 3000);
			config.set("Angel.Power.Summon.PowerCost", 5000);
			config.set("Angel.Power.Cure.PowerCost", 6000);
			config.set("Angel.Power.Jump.PowerCost", 1000);
			config.set("Angel.JumpDelta", 1.2);
			config.set("Angel.Power.Kill.MonsterGain", 30);
			config.set("Angel.Power.Swim.PowerGain", 50);

			if (supernaturalTypes.size() == 0) {
				supernaturalTypes.add("human");
				supernaturalTypes.add("vampire");
				supernaturalTypes.add("werewolf");
				supernaturalTypes.add("ghoul");
				supernaturalTypes.add("priest");
				supernaturalTypes.add("demon");
				supernaturalTypes.add("witchhunter");
				supernaturalTypes.add("enderborn");
				supernaturalTypes.add("angel");
				config.set("Supernatural.Types", supernaturalTypes);
			}

			if (woodMaterialsString.size() == 0) {
				woodMaterialsString.add("STICK");
				woodMaterialsString.add("WOODEN_AXE");
				woodMaterialsString.add("WOODEN_HOE");
				woodMaterialsString.add("WOODEN_PICKAXE");
				woodMaterialsString.add("WOODEN_SHOVEL");
				woodMaterialsString.add("WOODEN_SWORD");
				woodMaterialsString.add("BOW");
				config.set("Material.Wooden", woodMaterialsString);
			}

			if (foodMaterialsString.size() == 0) {
				foodMaterialsString.add("APPLE");
				foodMaterialsString.add("BREAD");
				foodMaterialsString.add("COOKED_COD");
				foodMaterialsString.add("COOKED_PORKCHOP");
				foodMaterialsString.add("GOLDEN_APPLE");
				foodMaterialsString.add("MUSHROOM_STEW");
				foodMaterialsString.add("COD");
				foodMaterialsString.add("PORKCHOP");
				foodMaterialsString.add("CAKE");
				foodMaterialsString.add("COOKIE");
				foodMaterialsString.add("COOKED_BEEF");
				foodMaterialsString.add("COOKED_CHICKEN");
				// 补齐至现代 MC 全部可食用物品（isEdible 全集）
				foodMaterialsString.add("BAKED_POTATO");
				foodMaterialsString.add("BEEF");
				foodMaterialsString.add("BEETROOT");
				foodMaterialsString.add("BEETROOT_SOUP");
				foodMaterialsString.add("CARROT");
				foodMaterialsString.add("CHICKEN");
				foodMaterialsString.add("CHORUS_FRUIT");
				foodMaterialsString.add("COOKED_MUTTON");
				foodMaterialsString.add("COOKED_RABBIT");
				foodMaterialsString.add("COOKED_SALMON");
				foodMaterialsString.add("DRIED_KELP");
				foodMaterialsString.add("ENCHANTED_GOLDEN_APPLE");
				foodMaterialsString.add("GLOW_BERRIES");
				foodMaterialsString.add("GOLDEN_CARROT");
				foodMaterialsString.add("HONEY_BOTTLE");
				foodMaterialsString.add("MELON_SLICE");
				foodMaterialsString.add("MUTTON");
				foodMaterialsString.add("POISONOUS_POTATO");
				foodMaterialsString.add("POTATO");
				foodMaterialsString.add("PUFFERFISH");
				foodMaterialsString.add("PUMPKIN_PIE");
				foodMaterialsString.add("RABBIT");
				foodMaterialsString.add("RABBIT_STEW");
				foodMaterialsString.add("SALMON");
				foodMaterialsString.add("SPIDER_EYE");
				foodMaterialsString.add("SUSPICIOUS_STEW");
				foodMaterialsString.add("SWEET_BERRIES");
				foodMaterialsString.add("TROPICAL_FISH");
				foodMaterialsString.add("ROTTEN_FLESH");
				config.set("Material.Food", foodMaterialsString);
			}

			if (enderWeaponsString.size() == 0) {
				enderWeaponsString.add("DIAMOND_SWORD");
				enderWeaponsString.add("DIAMOND_PICKAXE");
				enderWeaponsString.add("DIAMOND_AXE");
				enderWeaponsString.add("DIAMOND_HOE");
				// 补齐 1.16+ 下界合金 / 1.21.9+ 铜制
				enderWeaponsString.add("COPPER_SWORD");
				enderWeaponsString.add("COPPER_PICKAXE");
				enderWeaponsString.add("COPPER_SHOVEL");
				enderWeaponsString.add("COPPER_AXE");
				enderWeaponsString.add("COPPER_HOE");
				enderWeaponsString.add("NETHERITE_SWORD");
				enderWeaponsString.add("NETHERITE_PICKAXE");
				enderWeaponsString.add("NETHERITE_SHOVEL");
				enderWeaponsString.add("NETHERITE_AXE");
				enderWeaponsString.add("NETHERITE_HOE");
				enderWeaponsString.add("DIAMOND_SHOVEL");
				config.set("EnderBorn.Weapon.Restrictions", enderWeaponsString);
			}

			if (burnableBlocksString.size() == 0) {
				burnableBlocksString.add("GRASS_BLOCK");
				burnableBlocksString.add("OAK_LEAVES");
				burnableBlocksString.add("AIR");
				burnableBlocksString.add("WHEAT_SEEDS");
				burnableBlocksString.add("OAK_LOG");
				burnableBlocksString.add("BOOKSHELF");
				config.set("BurnableBlocks", burnableBlocksString);
			}

			// Bukkit 的 Enemy 接口即"敌对生物"全集，其中 Ghast/Phantom/EnderDragon 等
			// 并不是 Creature，所以这里必须写全，否则它们不会进入休战契约
			if (vampireTruceString.size() == 0) {
				vampireTruceString.add("BLAZE");
				vampireTruceString.add("BOGGED");
				vampireTruceString.add("BREEZE");
				vampireTruceString.add("CAVE_SPIDER");
				vampireTruceString.add("CREAKING");
				vampireTruceString.add("CREEPER");
				vampireTruceString.add("DROWNED");
				vampireTruceString.add("ELDER_GUARDIAN");
				vampireTruceString.add("ENDER_DRAGON");
				vampireTruceString.add("ENDERMAN");
				vampireTruceString.add("ENDERMITE");
				vampireTruceString.add("EVOKER");
				vampireTruceString.add("GHAST");
				vampireTruceString.add("GIANT");
				vampireTruceString.add("GUARDIAN");
				vampireTruceString.add("HOGLIN");
				vampireTruceString.add("HUSK");
				vampireTruceString.add("ILLUSIONER");
				vampireTruceString.add("MAGMA_CUBE");
				vampireTruceString.add("PARCHED");
				vampireTruceString.add("PHANTOM");
				vampireTruceString.add("PIGLIN");
				vampireTruceString.add("PIGLIN_BRUTE");
				vampireTruceString.add("PILLAGER");
				vampireTruceString.add("RAVAGER");
				vampireTruceString.add("SHULKER");
				vampireTruceString.add("SILVERFISH");
				vampireTruceString.add("SKELETON");
				vampireTruceString.add("SLIME");
				vampireTruceString.add("SPIDER");
				vampireTruceString.add("STRAY");
				vampireTruceString.add("VEX");
				vampireTruceString.add("VINDICATOR");
				vampireTruceString.add("WARDEN");
				vampireTruceString.add("WITCH");
				vampireTruceString.add("WITHER");
				vampireTruceString.add("WITHER_SKELETON");
				vampireTruceString.add("ZOGLIN");
				vampireTruceString.add("ZOMBIE");
				vampireTruceString.add("ZOMBIE_VILLAGER");
				vampireTruceString.add("ZOMBIFIED_PIGLIN");
				config.set("Vampire.Truce.Creatures", vampireTruceString);
			}

			if (vampireAltarInfectMaterialsString.size() == 0) {
				vampireAltarInfectMaterialsString.add("MUSHROOM_STEW");
				vampireAltarInfectMaterialsString.add("BONE");
				vampireAltarInfectMaterialsString.add("GUNPOWDER");
				vampireAltarInfectMaterialsString.add("REDSTONE");
				config.set("Vampire.Altar.Infect.Recipe.Materials", vampireAltarInfectMaterialsString);
			}

			if (vampireAltarInfectQuantities.size() == 0) {
				vampireAltarInfectQuantities.add(1);
				vampireAltarInfectQuantities.add(10);
				vampireAltarInfectQuantities.add(10);
				vampireAltarInfectQuantities.add(10);
				config.set("Vampire.Altar.Infect.Recipe.Quantities", vampireAltarInfectQuantities);
			}

			if (vampireAltarCureMaterialsString.size() == 0) {
				vampireAltarCureMaterialsString.add("WATER_BUCKET");
				vampireAltarCureMaterialsString.add("MILK_BUCKET");
				vampireAltarCureMaterialsString.add("SUGAR");
				vampireAltarCureMaterialsString.add("WHEAT");
				config.set("Vampire.Altar.Cure.Recipe.Materials", vampireAltarCureMaterialsString);
			}

			if (vampireAltarCureQuantities.size() == 0) {
				vampireAltarCureQuantities.add(1);
				vampireAltarCureQuantities.add(1);
				vampireAltarCureQuantities.add(20);
				vampireAltarCureQuantities.add(20);
				config.set("Vampire.Altar.Cure.Recipe.Quantities", vampireAltarCureQuantities);
			}

			if (priestAltarMaterialsString.size() == 0) {
				priestAltarMaterialsString.add("GOLD_INGOT");
				priestAltarMaterialsString.add("GLOWSTONE_DUST");
				priestAltarMaterialsString.add("REDSTONE");
				priestAltarMaterialsString.add("BREAD");
				config.set("Priest.Church.Recipe.Materials", priestAltarMaterialsString);
			}

			if (priestAltarQuantities.size() == 0) {
				priestAltarQuantities.add(4);
				priestAltarQuantities.add(4);
				priestAltarQuantities.add(8);
				priestAltarQuantities.add(30);
				config.set("Priest.Church.Recipe.Quantities", priestAltarQuantities);
			}

			if (priestMaterialsString.size() == 0) {
				priestMaterialsString.add("FEATHER"); // Banish
				priestMaterialsString.add("SUGAR"); // Exorcise
				priestMaterialsString.add("FLINT"); // Cure
				priestMaterialsString.add("PAPER"); // Heal
				priestMaterialsString.add("BOOK"); // Drain Power
				config.set("Priest.Spell.Material", priestMaterialsString);
			}

			if (priestDonationMaterialsString.size() == 0) {
				priestDonationMaterialsString.add("APPLE");
				priestDonationMaterialsString.add("COD");
				priestDonationMaterialsString.add("COOKED_COD");
				priestDonationMaterialsString.add("COOKED_PORKCHOP");
				priestDonationMaterialsString.add("BREAD");
				config.set("Priest.Church.Donation.Materials", priestDonationMaterialsString);
			}

			if (priestDonationRewards.size() == 0) {
				priestDonationRewards.add(9000);
				priestDonationRewards.add(510);
				priestDonationRewards.add(500);
				priestDonationRewards.add(210);
				priestDonationRewards.add(80);
				config.set("Priest.Church.Donation.Rewards", priestDonationRewards);
			}

			if (ghoulWeaponsString.size() == 0) {
				ghoulWeaponsString.add("BOW");
				ghoulWeaponsString.add("STICK");
				ghoulWeaponsString.add("WOODEN_SWORD");
				ghoulWeaponsString.add("WOODEN_PICKAXE");
				ghoulWeaponsString.add("WOODEN_SHOVEL");
				ghoulWeaponsString.add("WOODEN_AXE");
				ghoulWeaponsString.add("WOODEN_HOE");
				ghoulWeaponsString.add("STONE_SWORD");
				ghoulWeaponsString.add("STONE_PICKAXE");
				ghoulWeaponsString.add("STONE_SHOVEL");
				ghoulWeaponsString.add("STONE_AXE");
				ghoulWeaponsString.add("STONE_HOE");
				ghoulWeaponsString.add("IRON_SWORD");
				ghoulWeaponsString.add("IRON_PICKAXE");
				ghoulWeaponsString.add("IRON_SHOVEL");
				ghoulWeaponsString.add("IRON_AXE");
				ghoulWeaponsString.add("IRON_HOE");
				ghoulWeaponsString.add("GOLDEN_SWORD");
				ghoulWeaponsString.add("GOLDEN_PICKAXE");
				ghoulWeaponsString.add("GOLDEN_SHOVEL");
				ghoulWeaponsString.add("GOLDEN_AXE");
				ghoulWeaponsString.add("GOLDEN_HOE");
				ghoulWeaponsString.add("DIAMOND_SWORD");
				ghoulWeaponsString.add("DIAMOND_PICKAXE");
				ghoulWeaponsString.add("DIAMOND_SHOVEL");
				ghoulWeaponsString.add("DIAMOND_AXE");
				// 补齐 1.16+ 下界合金 / 1.21.9+ 铜制
				ghoulWeaponsString.add("COPPER_SWORD");
				ghoulWeaponsString.add("COPPER_PICKAXE");
				ghoulWeaponsString.add("COPPER_SHOVEL");
				ghoulWeaponsString.add("COPPER_AXE");
				ghoulWeaponsString.add("COPPER_HOE");
				ghoulWeaponsString.add("NETHERITE_SWORD");
				ghoulWeaponsString.add("NETHERITE_PICKAXE");
				ghoulWeaponsString.add("NETHERITE_SHOVEL");
				ghoulWeaponsString.add("NETHERITE_AXE");
				ghoulWeaponsString.add("NETHERITE_HOE");
				ghoulWeaponsString.add("DIAMOND_HOE");
				config.set("Ghoul.Weapon.Restrictions", ghoulWeaponsString);
			}

			if (demonWeaponsString.size() == 0) {
				demonWeaponsString.add("none");
				config.set("Demon.Weapon.Restrictions", demonWeaponsString);
			}

			if (hunterWeaponsString.size() == 0) {
				hunterWeaponsString.add("WOODEN_SWORD");
				hunterWeaponsString.add("WOODEN_PICKAXE");
				hunterWeaponsString.add("WOODEN_SHOVEL");
				hunterWeaponsString.add("WOODEN_AXE");
				hunterWeaponsString.add("WOODEN_HOE");
				hunterWeaponsString.add("STONE_SWORD");
				hunterWeaponsString.add("STONE_PICKAXE");
				hunterWeaponsString.add("STONE_SHOVEL");
				hunterWeaponsString.add("STONE_AXE");
				hunterWeaponsString.add("STONE_HOE");
				hunterWeaponsString.add("IRON_SWORD");
				hunterWeaponsString.add("IRON_PICKAXE");
				hunterWeaponsString.add("IRON_SHOVEL");
				hunterWeaponsString.add("IRON_AXE");
				hunterWeaponsString.add("IRON_HOE");
				hunterWeaponsString.add("GOLDEN_SWORD");
				hunterWeaponsString.add("GOLDEN_PICKAXE");
				hunterWeaponsString.add("GOLDEN_SHOVEL");
				hunterWeaponsString.add("GOLDEN_AXE");
				hunterWeaponsString.add("GOLDEN_HOE");
				hunterWeaponsString.add("DIAMOND_SWORD");
				hunterWeaponsString.add("DIAMOND_PICKAXE");
				hunterWeaponsString.add("DIAMOND_SHOVEL");
				hunterWeaponsString.add("DIAMOND_AXE");
				// 补齐 1.16+ 下界合金 / 1.21.9+ 铜制
				hunterWeaponsString.add("COPPER_SWORD");
				hunterWeaponsString.add("COPPER_PICKAXE");
				hunterWeaponsString.add("COPPER_SHOVEL");
				hunterWeaponsString.add("COPPER_AXE");
				hunterWeaponsString.add("COPPER_HOE");
				hunterWeaponsString.add("NETHERITE_SWORD");
				hunterWeaponsString.add("NETHERITE_PICKAXE");
				hunterWeaponsString.add("NETHERITE_SHOVEL");
				hunterWeaponsString.add("NETHERITE_AXE");
				hunterWeaponsString.add("NETHERITE_HOE");
				hunterWeaponsString.add("DIAMOND_HOE");
				config.set("WitchHunter.Weapon.Restrictions", hunterWeaponsString);
			}

			if (priestWeaponsString.size() == 0) {
				priestWeaponsString.add("none");
				config.set("Priest.Weapon.Restrictions", priestWeaponsString);
			}

			if (vampireWeaponsString.size() == 0) {
				vampireWeaponsString.add("none");
				config.set("Vampire.Weapon.Restrictions", vampireWeaponsString);
			}

			if (wereWeaponsString.size() == 0) {
				wereWeaponsString.add("BOW");
				wereWeaponsString.add("STICK");
				wereWeaponsString.add("WOODEN_SWORD");
				wereWeaponsString.add("WOODEN_PICKAXE");
				wereWeaponsString.add("WOODEN_SHOVEL");
				wereWeaponsString.add("WOODEN_AXE");
				wereWeaponsString.add("WOODEN_HOE");
				wereWeaponsString.add("STONE_SWORD");
				wereWeaponsString.add("STONE_PICKAXE");
				wereWeaponsString.add("STONE_SHOVEL");
				wereWeaponsString.add("STONE_AXE");
				wereWeaponsString.add("STONE_HOE");
				wereWeaponsString.add("IRON_SWORD");
				wereWeaponsString.add("IRON_PICKAXE");
				wereWeaponsString.add("IRON_SHOVEL");
				wereWeaponsString.add("IRON_AXE");
				wereWeaponsString.add("IRON_HOE");
				wereWeaponsString.add("GOLDEN_SWORD");
				wereWeaponsString.add("GOLDEN_PICKAXE");
				wereWeaponsString.add("GOLDEN_SHOVEL");
				wereWeaponsString.add("GOLDEN_AXE");
				wereWeaponsString.add("GOLDEN_HOE");
				wereWeaponsString.add("DIAMOND_SWORD");
				wereWeaponsString.add("DIAMOND_PICKAXE");
				wereWeaponsString.add("DIAMOND_SHOVEL");
				wereWeaponsString.add("DIAMOND_AXE");
				// 补齐 1.16+ 下界合金 / 1.21.9+ 铜制
				wereWeaponsString.add("COPPER_SWORD");
				wereWeaponsString.add("COPPER_PICKAXE");
				wereWeaponsString.add("COPPER_SHOVEL");
				wereWeaponsString.add("COPPER_AXE");
				wereWeaponsString.add("COPPER_HOE");
				wereWeaponsString.add("NETHERITE_SWORD");
				wereWeaponsString.add("NETHERITE_PICKAXE");
				wereWeaponsString.add("NETHERITE_SHOVEL");
				wereWeaponsString.add("NETHERITE_AXE");
				wereWeaponsString.add("NETHERITE_HOE");
				wereWeaponsString.add("DIAMOND_HOE");
				config.set("Were.Weapon.Restrictions", wereWeaponsString);
			}

			if (ghoulWeaponImmunityString.size() == 0) {
				// 补齐下界合金剑
				ghoulWeaponImmunityString.add("NETHERITE_SWORD");
				ghoulWeaponImmunityString.add("DIAMOND_SWORD");
				config.set("Ghoul.Immunity", ghoulWeaponImmunityString);
			}

				// 僵尸家族（含巨型僵尸）
			if (ghoulTruceString.size() == 0) {
				ghoulTruceString.add("ZOMBIE");
				ghoulTruceString.add("ZOMBIE_VILLAGER");
				ghoulTruceString.add("DROWNED");
				ghoulTruceString.add("HUSK");
				ghoulTruceString.add("ZOMBIFIED_PIGLIN");
				ghoulTruceString.add("GIANT");
				config.set("Ghoul.Truce.Creatures", ghoulTruceString);
			}

			if (wereWolfbaneMaterialsString.size() == 0) {
				wereWolfbaneMaterialsString.add("DANDELION");
				wereWolfbaneMaterialsString.add("POPPY");
				wereWolfbaneMaterialsString.add("RED_MUSHROOM");
				wereWolfbaneMaterialsString.add("BROWN_MUSHROOM");
				// 补齐官方 small_flowers 标签内全部花
				wereWolfbaneMaterialsString.add("ALLIUM");
				wereWolfbaneMaterialsString.add("AZURE_BLUET");
				wereWolfbaneMaterialsString.add("BLUE_ORCHID");
				wereWolfbaneMaterialsString.add("CLOSED_EYEBLOSSOM");
				wereWolfbaneMaterialsString.add("CORNFLOWER");
				wereWolfbaneMaterialsString.add("GOLDEN_DANDELION");
				wereWolfbaneMaterialsString.add("LILY_OF_THE_VALLEY");
				wereWolfbaneMaterialsString.add("OPEN_EYEBLOSSOM");
				wereWolfbaneMaterialsString.add("ORANGE_TULIP");
				wereWolfbaneMaterialsString.add("OXEYE_DAISY");
				wereWolfbaneMaterialsString.add("PINK_TULIP");
				wereWolfbaneMaterialsString.add("RED_TULIP");
				wereWolfbaneMaterialsString.add("TORCHFLOWER");
				wereWolfbaneMaterialsString.add("WHITE_TULIP");
				wereWolfbaneMaterialsString.add("WITHER_ROSE");
				wereWolfbaneMaterialsString.add("BOWL");
				config.set("Were.Wolfbane.Materials", wereWolfbaneMaterialsString);
			}

			if (wereWolfbaneQuantities.size() == 0) {
				// 必须与 Were.Wolfbane.Materials 逐位对应：前 4 种（蒲公英、虞美人、
				// 红/棕蘑菇）各 10，其余（各种花 + 最后那个碗）各 1。
				// 长度不一致时 loadValues 会对每个越界下标抛一次 IndexOutOfBounds 并打堆栈。
				for (int i = 0; i < wereWolfbaneMaterialsString.size(); i++) {
					wereWolfbaneQuantities.add(i < 4 ? 10 : 1);
				}
				config.set("Were.Wolfbane.Quantities", wereWolfbaneQuantities);
			}

			if (hunterArmorString.size() == 0) {
				hunterArmorString.add("AIR");
				hunterArmorString.add("LEATHER_HELMET");
				hunterArmorString.add("LEATHER_CHESTPLATE");
				hunterArmorString.add("LEATHER_LEGGINGS");
				hunterArmorString.add("LEATHER_BOOTS");
				config.set("WitchHunter.Armor", hunterArmorString);
			}

			if (ghoulArmorString.size() == 0) {
				ghoulArmorString.add("AIR");
				ghoulArmorString.add("LEATHER_HELMET");
				ghoulArmorString.add("LEATHER_CHESTPLATE");
				ghoulArmorString.add("LEATHER_LEGGINGS");
				ghoulArmorString.add("LEATHER_BOOTS");
				ghoulArmorString.add("IRON_HELMET");
				ghoulArmorString.add("IRON_CHESTPLATE");
				ghoulArmorString.add("IRON_LEGGINGS");
				ghoulArmorString.add("IRON_BOOTS");
				ghoulArmorString.add("GOLDEN_HELMET");
				ghoulArmorString.add("GOLDEN_CHESTPLATE");
				ghoulArmorString.add("GOLDEN_LEGGINGS");
				ghoulArmorString.add("GOLDEN_BOOTS");
				ghoulArmorString.add("DIAMOND_HELMET");
				ghoulArmorString.add("DIAMOND_CHESTPLATE");
				ghoulArmorString.add("DIAMOND_LEGGINGS");
				ghoulArmorString.add("DIAMOND_BOOTS");
				ghoulArmorString.add("CHAINMAIL_HELMET");
				ghoulArmorString.add("CHAINMAIL_CHESTPLATE");
				ghoulArmorString.add("CHAINMAIL_LEGGINGS");
				// 补齐下界合金 / 铜制 / 海龟壳
				ghoulArmorString.add("COPPER_HELMET");
				ghoulArmorString.add("COPPER_CHESTPLATE");
				ghoulArmorString.add("COPPER_LEGGINGS");
				ghoulArmorString.add("COPPER_BOOTS");
				ghoulArmorString.add("NETHERITE_HELMET");
				ghoulArmorString.add("NETHERITE_CHESTPLATE");
				ghoulArmorString.add("NETHERITE_LEGGINGS");
				ghoulArmorString.add("NETHERITE_BOOTS");
				ghoulArmorString.add("TURTLE_HELMET");
				ghoulArmorString.add("CHAINMAIL_BOOTS");
				config.set("Ghoul.Armor", ghoulArmorString);
			}

			if (demonArmorString.size() == 0) {
				demonArmorString.add("AIR");
				config.set("Demon.Armor", demonArmorString);
			}

			if (priestArmorString.size() == 0) {
				priestArmorString.add("AIR");
				config.set("Priest.Armor", priestArmorString);
			}

			if (vampireArmorString.size() == 0) {
				vampireArmorString.add("AIR");
				vampireArmorString.add("LEATHER_HELMET");
				vampireArmorString.add("LEATHER_CHESTPLATE");
				vampireArmorString.add("LEATHER_LEGGINGS");
				vampireArmorString.add("LEATHER_BOOTS");
				vampireArmorString.add("IRON_HELMET");
				vampireArmorString.add("IRON_CHESTPLATE");
				vampireArmorString.add("IRON_LEGGINGS");
				vampireArmorString.add("IRON_BOOTS");
				vampireArmorString.add("GOLDEN_HELMET");
				vampireArmorString.add("GOLDEN_CHESTPLATE");
				vampireArmorString.add("GOLDEN_LEGGINGS");
				vampireArmorString.add("GOLDEN_BOOTS");
				vampireArmorString.add("DIAMOND_HELMET");
				vampireArmorString.add("DIAMOND_CHESTPLATE");
				vampireArmorString.add("DIAMOND_LEGGINGS");
				vampireArmorString.add("DIAMOND_BOOTS");
				vampireArmorString.add("CHAINMAIL_HELMET");
				vampireArmorString.add("CHAINMAIL_CHESTPLATE");
				vampireArmorString.add("CHAINMAIL_LEGGINGS");
				// 补齐下界合金 / 铜制 / 海龟壳
				vampireArmorString.add("COPPER_HELMET");
				vampireArmorString.add("COPPER_CHESTPLATE");
				vampireArmorString.add("COPPER_LEGGINGS");
				vampireArmorString.add("COPPER_BOOTS");
				vampireArmorString.add("NETHERITE_HELMET");
				vampireArmorString.add("NETHERITE_CHESTPLATE");
				vampireArmorString.add("NETHERITE_LEGGINGS");
				vampireArmorString.add("NETHERITE_BOOTS");
				vampireArmorString.add("TURTLE_HELMET");
				vampireArmorString.add("CHAINMAIL_BOOTS");
				config.set("Vampire.Armor", vampireArmorString);
			}

			if (wereArmorString.size() == 0) {
				wereArmorString.add("AIR");
				wereArmorString.add("LEATHER_HELMET");
				wereArmorString.add("LEATHER_CHESTPLATE");
				wereArmorString.add("LEATHER_LEGGINGS");
				wereArmorString.add("LEATHER_BOOTS");
				wereArmorString.add("IRON_HELMET");
				wereArmorString.add("IRON_CHESTPLATE");
				wereArmorString.add("IRON_LEGGINGS");
				wereArmorString.add("IRON_BOOTS");
				wereArmorString.add("GOLDEN_HELMET");
				wereArmorString.add("GOLDEN_CHESTPLATE");
				wereArmorString.add("GOLDEN_LEGGINGS");
				wereArmorString.add("GOLDEN_BOOTS");
				wereArmorString.add("DIAMOND_HELMET");
				wereArmorString.add("DIAMOND_CHESTPLATE");
				wereArmorString.add("DIAMOND_LEGGINGS");
				wereArmorString.add("DIAMOND_BOOTS");
				wereArmorString.add("CHAINMAIL_HELMET");
				wereArmorString.add("CHAINMAIL_CHESTPLATE");
				wereArmorString.add("CHAINMAIL_LEGGINGS");
				// 补齐下界合金 / 铜制 / 海龟壳
				wereArmorString.add("COPPER_HELMET");
				wereArmorString.add("COPPER_CHESTPLATE");
				wereArmorString.add("COPPER_LEGGINGS");
				wereArmorString.add("COPPER_BOOTS");
				wereArmorString.add("NETHERITE_HELMET");
				wereArmorString.add("NETHERITE_CHESTPLATE");
				wereArmorString.add("NETHERITE_LEGGINGS");
				wereArmorString.add("NETHERITE_BOOTS");
				wereArmorString.add("TURTLE_HELMET");
				wereArmorString.add("CHAINMAIL_BOOTS");
				config.set("Were.Armor", wereArmorString);
			}

			if (hunterArrowTypes.size() == 0) {
				hunterArrowTypes.add("fire");
				hunterArrowTypes.add("triple");
				hunterArrowTypes.add("power");
				hunterArrowTypes.add("grapple");
				hunterArrowTypes.add("normal");
				config.set("WitchHunter.ArrowTypes", hunterArrowTypes);
			}

			saveConfig();
		}

		debugMode = config.getBoolean("DebugMode", false);
		multiworld = config.getBoolean("MultiWorld", false);
		enableColors = config.getBoolean("EnableChatColors", true);
		truceBreakTime = config.getInt("Supernatural.Truce.BreakTime", 120000);
		supernaturalTypes = config.getStringList("Supernatural.Types");
		spreadChance = config.getDouble("Supernatural.SpreadChance", 0.35);
		enableLoginMessage = config.getBoolean("EnableLoginMessage");
		spanish = config.getBoolean("Spanish");
		enableJoinCommand = config.getBoolean("EnableJoinCommand");

		woodMaterialsString = config.getStringList("Material.Wooden");
		foodMaterialsString = config.getStringList("Material.Food");

		jumpMaterial = config.getString("Vampire.Materials.Jump", "POPPY");

		vampirePowerStart = config.getInt("Vampire.Power.Start", 10000);
		vampireKillSpreadCurse = config.getBoolean("Vampire.Kill.SpreadCurse", true);
		vampireTimePowerGained = config.getDouble("Vampire.Time.PowerGained", 15);
		vampireKillPowerCreatureGain = config.getInt("Vampire.Power.Kill.CreatureGain", 100);
		vampireKillPowerPlayerGain = config.getInt("Vampire.Power.Kill.PlayerGain", 500);
		vampireDeathPowerPenalty = config.getInt("Vampire.Power.DeathPenalty", 10000);
		vampireDamageFactor = config.getDouble("Vampire.DamageFactor.AttackBonus", 0.3);
		vampireDamageReceivedFactor = config.getDouble("Vampire.DamageFactor.DefenseBonus", 0.8);
		woodFactor = config.getDouble("Vampire.DamageFactor.Wood", 1.5);
		vampireBurnInSunlight = config.getBoolean("Vampire.Burn.InSunlight", true);
		vampireBurnMessageEnabled = config.getBoolean("Vampire.Burn.MessageEnabled", true);
		vampireCombustFireTicks = config.getInt("Vampire.Burn.FireTicks", 3);

		jumpDeltaSpeed = config.getDouble("Vampire.JumpDelta", 1.2);
		jumpBloodCost = config.getInt("Vampire.Power.JumpCost", 1000);
		vampireTimeHealthGained = config.getDouble("Vampire.Time.HealthGained", 0.5);
		vampireHealthCost = config.getDouble("Vampire.Power.HealingCost", 60);
		vampireDrowningCost = config.getInt("Vampire.Power.DrowningCost", 90);
		vampireTeleportCost = config.getInt("Vampire.Power.TeleportCost", 9000);
		vampireTeleportMaterial = config.getString("Vampire.TeleportMarker.Material", "POPPY");
		vampireTruceString = config.getStringList("Vampire.Truce.Creatures");
		vampireMaterial = config.getString("Vampire.Spell.Material", "BOOK");
		vampireHelmet = config.getString("Vampire.Burn.HelmetProtection", "GOLDEN_HELMET");
		vampireWeaponsString = config.getStringList("Vampire.Weapon.Restrictions");
		vampireArmorString = config.getStringList("Vampire.Armor");
		vampireHungerRegainPlayer = config.getInt("Vampire.GainHunger.Player");
		vampireHungerRegainMob = config.getInt("Vampire.GainHunger.Mob");

		vampireAltarInfectMaterial = config.getString("Vampire.Altar.Infect.Material", "GOLD_BLOCK");
		vampireAltarInfectMaterialSurround = config.getString("Vampire.Altar.Infect.Surrounding.Material", "OBSIDIAN");
		vampireAltarInfectMaterialRadius = config.getDouble("Vampire.Altar.Infect.Surrounding.Radius", 7D);
		vampireAltarInfectMaterialSurroundCount = config.getInt("Vampire.Altar.Infect.Surrounding.Count", 20);
		vampireAltarInfectMaterialsString = config.getStringList("Vampire.Altar.Infect.Recipe.Materials");
		vampireAltarInfectQuantities = config.getIntegerList("Vampire.Altar.Infect.Recipe.Quantities");

		vampireAltarCureMaterial = config.getString("Vampire.Altar.Cure.Material", "LAPIS_BLOCK");
		vampireAltarCureMaterialSurround = config.getString("Vampire.Altar.Cure.Surrounding.Material", "GLOWSTONE");
		vampireAltarCureMaterialRadius = config.getDouble("Vampire.Altar.Cure.Surrounding.Radius", 7D);
		vampireAltarCureMaterialSurroundCount = config.getInt("Vampire.Altar.Cure.Surrounding.Count", 20);
		vampireAltarCureMaterialsString = config.getStringList("Vampire.Altar.Cure.Recipe.Materials");
		vampireAltarCureQuantities = config.getIntegerList("Vampire.Altar.Cure.Recipe.Quantities");

		priestChurchWorld = config.getString("Priest.Church.World", "world");
		priestChurchLocationX = config.getInt("Priest.Church.Location.X", 0);
		priestChurchLocationY = config.getInt("Priest.Church.Location.Y", 80);
		priestChurchLocationZ = config.getInt("Priest.Church.Location.Z", 0);
		priestBanishWorld = config.getString("Priest.Banish.World", "world");
		priestBanishLocationX = config.getInt("Priest.Banish.Location.X", 0);
		priestBanishLocationY = config.getInt("Priest.Banish.Location.Y", 80);
		priestBanishLocationZ = config.getInt("Priest.Banish.Location.Z", 0);

		priestPowerStart = config.getInt("Priest.Power.StartingAmount", 10000);
		priestDeathPowerPenalty = config.getInt("Priest.Power.DeathPenalty", 2000);
		priestDamageFactorAttackSuper = config.getDouble("Priest.DamageFactor.AttackBonusSuper", 1.0);
		priestDamageFactorAttackHuman = config.getDouble("Priest.DamageFactor.AttackBonusHuman", 0);
		priestPowerBanish = config.getInt("Priest.Power.Banish", 4000);
		priestPowerHeal = config.getInt("Priest.Power.HealOther", 1000);
		priestHealAmount = config.getInt("Priest.Spell.HealAmount", 10);
		priestPowerExorcise = config.getInt("Priest.Power.Exorcise", 9000);
		priestPowerCure = config.getInt("Priest.Power.Cure", 1000);
		priestPowerDrain = config.getInt("Priest.Power.Drain", 1000);
		priestPowerGuardianAngel = config.getInt("Priest.Power.GuardianAngel", 5000);
		priestDrainFactor = config.getDouble("Priest.Spell.DrainFactor", 0.15);
		priestFireTicks = config.getInt("Priest.DamageFactor.FireTicks", 50);
		priestAltarMaterial = config.getString("Priest.Church.AltarMaterial", "DIAMOND_BLOCK");
		priestMaterialsString = config.getStringList("Priest.Spell.Material");
		// WOOL stopped existing when the wool items were split by colour, and INK_SACK is INK_SAC
		// here, so both spells were impossible to cast on any server that had the old defaults.
		priestSpellGuardianAngel = usableMaterial("Priest.Spell.Material.GuardianAngel",
				config.getString("Priest.Spell.Material.GuardianAngel", "WHITE_WOOL"),
				"WHITE_WOOL");
		priestAltarMaterialsString = config.getStringList("Priest.Church.Recipe.Materials");
		priestAltarQuantities = config.getIntegerList("Priest.Church.Recipe.Quantities");
		priestDonationMaterialsString = config.getStringList("Priest.Church.Donation.Materials");
		priestDonationRewards = config.getIntegerList("Priest.Church.Donation.Rewards");
		priestArmorString = config.getStringList("Priest.Armor");
		priestWeaponsString = config.getStringList("Priest.Weapon.Restrictions");

		ghoulPowerStart = config.getInt("Ghoul.Power.Start", 5000);
		ghoulKillSpreadCurse = config.getBoolean("Ghoul.Kill.SpreadCurse", true);
		ghoulKillPowerCreatureGain = config.getInt("Ghoul.Power.Kill.CreatureGain", 200);
		ghoulKillPowerPlayerGain = config.getInt("Ghoul.Power.Kill.PlayerGain", 1000);
		ghoulDeathPowerPenalty = config.getInt("Ghoul.Power.DeathPenalty", 2000);
		ghoulDamageReceivedFactor = config.getDouble("Ghoul.DamageFactor.DefenseBonus", 0.65);
		ghoulWeaponsString = config.getStringList("Ghoul.Weapon.Restrictions");
		ghoulTruceString = config.getStringList("Ghoul.Truce.Creatures");
		ghoulDamageFactor = config.getDouble("Ghoul.DamageFactor.AttackBonus", 2);
		ghoulDamageWater = config.getInt("Ghoul.WaterDamage", 4);
		ghoulHealthGained = config.getDouble("Ghoul.Time.HealthGained", 0.1);
		ghoulMaterial = config.getString("Ghoul.Summon.Material", "PORKCHOP");
		ghoulBondMaterial = config.getString("Ghoul.UnholyBond.Material", "BONE");
		ghoulPowerSummonCost = config.getInt("Ghoul.Power.Summon", 1000);
		ghoulPowerBond = config.getInt("Ghoul.Power.UnholyBond", 50);
		ghoulWeaponImmunityString = config.getStringList("Ghoul.Immunity");
		ghoulArmorString = config.getStringList("Ghoul.Armor");
		ghoulRightClickSummon = config.getBoolean("Ghoul.RightClickSummon");

		dashDeltaSpeed = config.getDouble("Were.DashDelta", 4);
		dashBloodCost = config.getInt("Were.Power.Dash", 400);

		werePowerStart = config.getInt("Were.Power.Start", 5000);
		wereKillSpreadCurse = config.getBoolean("Were.Kill.SpreadCurse", true);
		wereKillPowerCreatureGain = config.getInt("Were.Power.Kill.CreatureGain", 20);
		wereKillPowerPlayerGain = config.getInt("Were.Power.Kill.PlayerGain", 100);
		werePowerFood = config.getInt("Were.Power.Food", 100);
		wereDeathPowerPenalty = config.getInt("Were.Power.DeathPenalty", 2000);
		wereDamageFall = config.getDouble("Were.DamageFactor.Fall", 0.5);
		wereDamageFactor = config.getDouble("Were.DamageFactor.AttackBonus", 5);
		wereHealthGained = config.getDouble("Were.Time.HealthGained", 0.2);
		wolfMaterial = config.getString("Were.Material.Summon", "PORKCHOP");
		werePowerSummonCost = config.getInt("Were.Power.Summon", 2000);
		wolfTruce = config.getBoolean("Were.WolfTruce", true);
		dashMaterial = config.getString("Were.Material.Dash", "FEATHER");
		wolfbaneMaterial = config.getString("Were.Wolfbane.Trigger", "BOWL");
		wereWolfbaneMaterialsString = config.getStringList("Were.Wolfbane.Materials");
		wereWolfbaneQuantities = config.getIntegerList("Were.Wolfbane.Quantities");
		wereArmorString = config.getStringList("Were.Armor");
		wereWeaponsString = config.getStringList("Were.Weapon.Restrictions");

		demonPowerStart = config.getInt("Demon.Power.Start", 10000);
		demonDeathPowerPenalty = config.getInt("Demon.Power.DeathPenalty", 10000);
		demonKillPowerCreatureGain = config.getInt("Demon.Power.CreatureKill", 20);
		demonKillPowerPlayerGain = config.getInt("Demon.Power.PlayerKill", 100);
		demonPowerGain = config.getInt("Demon.Power.Gain", 40);
		demonPowerLoss = config.getInt("Demon.Power.Loss", 4);
		demonPowerFireball = config.getInt("Demon.Power.Fireball", 2000);
		demonHealing = config.getInt("Demon.Healing", 1);
		demonMaterial = config.getString("Demon.Fireball.Material", "REDSTONE");
		demonFireballDamage = config.getInt("Demon.Fireball.Damage", 10);
		demonPowerSnare = config.getInt("Demon.Power.Snare", 1000);
		demonSnareDuration = config.getInt("Demon.Snare.Duration", 10000);
		demonSnareMaterial = usableMaterial("Demon.Snare.Material",
				config.getString("Demon.Snare.Material", "INK_SAC"), "INK_SAC");
		demonSnowballAmount = config.getInt("Demon.SnowballAmount", 30);
		demonArmorString = config.getStringList("Demon.Armor");
		demonWeaponsString = config.getStringList("Demon.Weapon.Restrictions");
		demonFireTicks = config.getInt("Demon.DamageFactor.FireTicks", 50);
		demonConvertPower = config.getInt("Demon.Power.Convert", 2000);

		hunterPowerStart = config.getInt("WitchHunter.Power.StartingPower", 10000);
		hunterDeathPowerPenalty = config.getInt("WitchHunter.Power.DeathPenalty", 500);
		hunterKillPowerPlayerGain = config.getInt("WitchHunter.Power.PlayerKill", 2000);
		hunterKillPowerCreatureGain = config.getInt("WitchHunter.Power.CreatureKill", 0);
		hunterBountyCompletion = config.getInt("WitchHunter.Bounty.CompletionBonus", 8000);
		hunterPowerArrowFire = config.getInt("WitchHunter.Power.ArrowFire", 100);
		hunterPowerArrowTriple = config.getInt("WitchHunter.Power.ArrowTriple", 100);
		hunterPowerArrowGrapple = config.getInt("WitchHunter.Power.ArrowGrapple", 500);
		hunterPowerArrowPower = config.getInt("WitchHunter.Power.ArrowPower", 1000);
		hunterPowerArrowDamage = config.getDouble("WitchHunter.ArrowPower.DamageFactor", 2.0);
		hunterCooldown = config.getInt("WitchHunter.PowerArrow.Cooldown", 15000);
		hunterArmorString = config.getStringList("WitchHunter.Armor");
		hunterFallReduction = config.getInt("WitchHunter.FallReduction", 3);
		hunterFireArrowFireTicks = config.getInt("WitchHunter.FireArrow.FireTicks", 100);
		hunterArrowTypes = config.getStringList("WitchHunter.ArrowTypes");
		hunterHallMessage = config.getString("WitchHunter.Hall.Message", "WitchHunter");
		hunterMaxBounties = config.getInt("WitchHunter.Bounty.MaxNumber", 5);
		hunterWeaponsString = config.getStringList("WitchHunter.Weapon.Restrictions");

		enderDamageFactor = config.getDouble("EnderBorn.DamageFactor.AttackBonus");
		enderDamageReceivedFactor = config.getDouble("EnderBorn.DamageFactor.DefenseBonus");
		enderProtectPower = config.getInt("EnderBorn.Power.Protect");
		enderDeathPowerPenalty = config.getInt("EnderBorn.Power.DeathPenalty");
		enderKillPower = config.getInt("EnderBorn.Power.Kill");
		enderPearlPower = config.getInt("EnderBorn.Power.EnderPearl");
		enderWeaponsString = config.getStringList("EnderBorn.Weapon.Restrictions");

		angelHealHealthGain = config.getInt("Angel.Power.Heal.HealthGain");
		angelHealPowerCost = config.getInt("Angel.Power.Heal.PowerCost", 3000);
		angelSummonPowerCost = config.getInt("Angel.Power.Summon.PowerCost", 5000);
		angelCurePowerCost = config.getInt("Angel.Power.Cure.PowerCost", 6000);
		angelJumpPowerCost = config.getInt("Angel.Power.Jump.PowerCost", 1000);
		angelSwimPowerGain = config.getInt("Angel.Power.Swim.PowerGain", 50);
		angelJumpDeltaSpeed = config.getDouble("Angel.JumpDelta", 1.2);
		angelKillMonsterPowerGain = config.getInt("Angel.Power.Kill.MonsterGain", 30);

		for (String wood : woodMaterialsString) {
			woodMaterials.add(Material.getMaterial(wood));
		}

		for (String food : foodMaterialsString) {
			foodMaterials.add(Material.getMaterial(food));
		}

		for (String block : burnableBlocksString) {
			burnableBlocks.add(Material.getMaterial(block));
		}

		for (String creature : vampireTruceString) {
			EntityType cType = EntityType.valueOf(creature);
			if (cType != null) {
				vampireTruce.add(cType);
			}
		}

		for (String material : priestMaterialsString) {
			priestSpellMaterials.add(Material.getMaterial(material));
		}

		for (String creature : ghoulTruceString) {
			EntityType cType = EntityType.valueOf(creature);
			if (cType != null) {
				ghoulTruce.add(cType);
			}
		}

		for (String weapon : ghoulWeaponsString) {
			ghoulWeapons.add(Material.getMaterial(weapon));
		}

		for (String weapon : demonWeaponsString) {
			demonWeapons.add(Material.getMaterial(weapon));
		}

		for (String weapon : hunterWeaponsString) {
			hunterWeapons.add(Material.getMaterial(weapon));
		}

		for (String weapon : priestWeaponsString) {
			priestWeapons.add(Material.getMaterial(weapon));
		}

		for (String weapon : vampireWeaponsString) {
			vampireWeapons.add(Material.getMaterial(weapon));
		}

		for (String weapon : wereWeaponsString) {
			wereWeapons.add(Material.getMaterial(weapon));
		}

		for (String weapon : ghoulWeaponImmunityString) {
			ghoulWeaponImmunity.add(Material.getMaterial(weapon));
		}

		for (String armor : hunterArmorString) {
			hunterArmor.add(Material.getMaterial(armor));
		}

		for (String weapon : enderWeaponsString) {
			enderWeapons.add(Material.getMaterial(weapon));
		}

		for (String armor : ghoulArmorString) {
			ghoulArmor.add(Material.getMaterial(armor));
		}

		for (String armor : demonArmorString) {
			demonArmor.add(Material.getMaterial(armor));
		}

		for (String armor : priestArmorString) {
			priestArmor.add(Material.getMaterial(armor));
		}

		for (String armor : vampireArmorString) {
			vampireArmor.add(Material.getMaterial(armor));
		}

		for (String armor : wereArmorString) {
			wereArmor.add(Material.getMaterial(armor));
		}

		for (int i = 0; i < vampireAltarInfectMaterialsString.size(); i++) {
			Material material = Material.getMaterial(vampireAltarInfectMaterialsString.get(i));
			int quantity = 1;
			try {
				quantity = vampireAltarInfectQuantities.get(i);
			} catch (Exception e) {
				e.printStackTrace();
				SupernaturalsPlugin.log("Invalid Vampire Infect Altar Quantities!");
			}
			vampireAltarInfectRecipe.materialQuantities.put(material, quantity);
		}

		for (int i = 0; i < vampireAltarCureMaterialsString.size(); i++) {
			Material material = Material.getMaterial(vampireAltarCureMaterialsString.get(i));
			int quantity = 1;
			try {
				quantity = vampireAltarCureQuantities.get(i);
			} catch (Exception e) {
				e.printStackTrace();
				SupernaturalsPlugin.log("Invalid Vampire Cure Altar Quantities!");
			}
			vampireAltarCureRecipe.materialQuantities.put(material, quantity);
		}

		for (int i = 0; i < priestAltarMaterialsString.size(); i++) {
			Material material = Material.getMaterial(priestAltarMaterialsString.get(i));
			int quantity = 1;
			try {
				quantity = priestAltarQuantities.get(i);
			} catch (Exception e) {
				e.printStackTrace();
				SupernaturalsPlugin.log("Invalid Priest Altar Quantities!");
			}
			priestAltarRecipe.materialQuantities.put(material, quantity);
		}

		for (int i = 0; i < wereWolfbaneMaterialsString.size(); i++) {
			Material material = Material.getMaterial(wereWolfbaneMaterialsString.get(i));
			// 数量表比材料表短时按 1 补，不要抛异常打堆栈 ——
			// 老服务器的 config.yml 里可能还留着上一次生成的短表，删除重建才能修，
			// 这里直接容忍，避免每次启动刷一屏 IndexOutOfBoundsException。
			int quantity = (i < wereWolfbaneQuantities.size()) ? wereWolfbaneQuantities.get(i) : 1;
			wereWolfbaneRecipe.materialQuantities.put(material, quantity);
		}

		for (int i = 0; i < priestDonationMaterialsString.size(); i++) {
			Material material = Material.getMaterial(priestDonationMaterialsString.get(i));
			int reward = 1;
			try {
				reward = priestDonationRewards.get(i);
			} catch (Exception e) {
				e.printStackTrace();
				SupernaturalsPlugin.log("Invalid priest donation reward!");
			}
			priestDonationMap.put(material, reward);
		}

		priestChurchLocation = new Location(plugin.getServer().getWorld(priestChurchWorld), priestChurchLocationX, priestChurchLocationY, priestChurchLocationZ);
		priestBanishLocation = new Location(plugin.getServer().getWorld(priestBanishWorld), priestBanishLocationX, priestBanishLocationY, priestBanishLocationZ);

		warnAboutUnknownMaterials(config);
	}

	public static void saveConfig() {
		plugin.saveConfig();
	}

	public static void reloadConfig() {
		if (SNConfigHandler.debugMode) {
			SupernaturalsPlugin.log("Reloaded configuration file");
		}
		plugin.reloadConfig();
		loadValues(config);
	}

	public static Configuration getConfig() {
		return config;
	}

	/**
	 * Whether an item is the one a setting names.
	 *
	 * <p>Abilities used to compare the item's <b>name</b> against the configured string, so a
	 * setting whose value no longer exists in this version simply stopped matching: an ability
	 * would quietly become impossible to use, with nothing anywhere to say why. Comparing the
	 * resolved materials means a name is either right, or reported at load.
	 */
	public static boolean isItem(Material item, String configuredName) {
		return item != null && configuredName != null
				&& item == Material.matchMaterial(configuredName);
	}

	/** Settings a substitution was already made for, so the sweep does not report them twice. */
	private static final Set<String> substitutedMaterials = new HashSet<>();

	/**
	 * A material setting that is guaranteed to name something this version has.
	 *
	 * <p>A name that does not resolve cannot be a deliberate choice - the ability that reads it
	 * simply would not work - and an existing {@code config.yml} keeps whatever was written into
	 * it the first time, so changing a default alone would never reach a server that already had
	 * one. The fallback is used, and the substitution is logged.
	 */
	private static String usableMaterial(String setting, String value, String fallback) {
		if (value != null && Material.matchMaterial(value) != null) {
			return value;
		}
		substitutedMaterials.add(setting);
		SupernaturalsPlugin.log(Level.WARNING, setting + " is set to \"" + value
				+ "\", which is not a material in this version - using \"" + fallback
				+ "\" instead.");
		return fallback;
	}

	/**
	 * Every setting whose name says it holds a material, checked once, at load. A Minecraft
	 * update that renames an item does not break an ability loudly - it makes it impossible to
	 * use - so this is the only thing that will ever say so.
	 */
	private static void warnAboutUnknownMaterials(Configuration config) {
		for (String key : config.getKeys(true)) {
			Object value = config.get(key);
			if (!(value instanceof String name) || name.isEmpty()
					|| !key.toLowerCase(Locale.ROOT).contains("material")
					|| substitutedMaterials.contains(key)) {
				continue;
			}
			if (Material.matchMaterial(name) == null) {
				SupernaturalsPlugin.log(Level.WARNING, key + " is set to \"" + name
						+ "\", which is not a material in this version - the ability that uses"
						+ " it cannot be used.");
			}
		}
	}

}
