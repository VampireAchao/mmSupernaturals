package me.matterz.supernaturals.io;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import me.matterz.supernaturals.SuperNPlayer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * What a save file is allowed to say about a player's power.
 *
 * <p>The save file is the one input the plugin does not produce: it is read straight onto the bean,
 * so it is also the one place a number that no rule in the plugin could ever have produced arrives.
 * The bound is {@code 0} to {@code 10000} - the ceiling every race is given on joining - and it is
 * enforced by {@link SuperNPlayer#setPower}, which is why these cases go through the loader rather
 * than through the bean.
 *
 * <p>There are two keys for one number in a save: {@code power}, which snakeyaml maps onto the
 * getter/setter pair and therefore through the bound, and {@code superPower}, which is the field
 * behind them and is written onto the bean directly. A file that disagrees with itself is
 * therefore not merely an odd file: it is the difference between a rule and no rule.
 */
class SaveFilePowerTest {

	@TempDir
	Path folder;

	/** Writes a one-player save with the two power keys the real file carries. */
	private List<SuperNPlayer> load(String power, String superPower) throws IOException {
		Path file = folder.resolve("data.yml");
		Files.write(file, ("- !<player> {name: Edited, playername: Edited, superType: vampire, "
				+ "power: " + power + ", superPower: " + superPower + "}\n")
				.getBytes(StandardCharsets.UTF_8));
		List<SuperNPlayer> players = SNPlayerHandler.load(file.toFile());
		assertEquals(1, players.size(), "the save was not read at all");
		return players;
	}

	/** A number nobody could have earned: the ceiling is ten thousand, whatever the race. */
	@Test
	void powerFromAHandEditedFileCannotExceedTheCeiling() throws IOException {
		SuperNPlayer player = load("10000.0", "999999999.0").get(0);

		assertEquals(10000, player.getPower(),
				"a save file must not be able to hand out more power than the ceiling");
	}

	/** A non-number is not "a very large number": it is a cost that can never be paid. */
	@Test
	void powerFromAHandEditedFileIsAlwaysANumber() throws IOException {
		SuperNPlayer player = load("10000.0", ".NaN").get(0);

		assertEquals(0, player.getPower(), "a non-number has to land inside the bounds, not stay one");
	}

	/** And the file can still say a number that is inside the bounds. */
	@Test
	void aPowerInsideTheBoundsIsReadAsWritten() throws IOException {
		SuperNPlayer player = load("4000.0", "4000.0").get(0);

		assertEquals(4000, player.getPower(), "an ordinary number must survive the round trip");
	}
}
