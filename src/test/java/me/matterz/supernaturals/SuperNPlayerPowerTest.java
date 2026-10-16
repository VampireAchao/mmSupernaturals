package me.matterz.supernaturals;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The bounds a player's power is held between, which is the one rule every cost in the plugin
 * stands on: a price is only a price if the number it is taken from cannot go below it.
 *
 * <p>These are plain-Java cases. {@link SuperNPlayer} is the bean the save file is read straight
 * onto, so the two ways in - a cost that is too big and a save file that is not a number - can be
 * asked about without a server, and they are the two the test hall cannot show: a hall can watch a
 * cost being taken, but it cannot hand a player a number no arithmetic answers.
 */
class SuperNPlayerPowerTest {

	private static final double CEILING = 10000.0;
	private static final double FLOOR = 0.0;

	@Test
	void powerIsHeldBetweenItsBounds() {
		SuperNPlayer player = new SuperNPlayer("Bounds");

		player.setPower(123.5);
		assertEquals(123.5, player.getPower(), "an ordinary number is left alone");

		player.setPower(1e9);
		assertEquals(CEILING, player.getPower(), "nothing may hold more than the ceiling");

		player.setPower(-5);
		assertEquals(FLOOR, player.getPower(), "nothing may hold less than nothing");
	}

	/**
	 * A save file that says "not a number" arrives as power that is never less than any price,
	 * which is every cost in the plugin waived at once - and a non-number plus a cost is still a
	 * non-number, so there would be no way back out of it.
	 */
	@Test
	void powerIsNotAllowedToBeANonNumber() {
		SuperNPlayer player = new SuperNPlayer("NotANumber");

		player.setPower(Double.NaN);
		assertEquals(FLOOR, player.getPower(), "a non-number has to land inside the bounds");

		// Whatever the plugin then does to it - a cost, a drain, a gain - it stays a number.
		player.setPower(player.getPower() - 3000);
		assertEquals(FLOOR, player.getPower(), "a cost taken from the floor stays at the floor");

		player.setPower(Double.POSITIVE_INFINITY);
		assertEquals(CEILING, player.getPower(), "and an infinity is only as much as the ceiling");
	}

	/**
	 * The same rule, asked directly: a caller that passes its own bounds - the ceiling is a
	 * setting, and this is what a setting is clamped by - gets the same answer for a non-number.
	 */
	@Test
	void aNonNumberComesBackAsTheLowerBound() {
		SuperNPlayer player = new SuperNPlayer("Custom");

		double held = player.limitDouble(Double.NaN, 7, 9);
		assertEquals(7, held, "a non-number comes back as the lower bound, not as itself");
		assertTrue(held >= 7 && held <= 9, "which is inside the bounds it was given");
	}
}
