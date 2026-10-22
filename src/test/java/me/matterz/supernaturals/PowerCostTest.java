package me.matterz.supernaturals;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import me.matterz.supernaturals.manager.SuperNManager;

import org.junit.jupiter.api.Test;

/**
 * The boundary a power cost is charged against.
 *
 * <p>A price of five thousand has to be payable by five thousand. Every ability used to ask
 * {@code power > cost}, so the one number a bar could not buy was the number of a full bar, and an
 * ability that costs everything was the one thing it could not do. The hall can watch a summon take
 * its cost, but it cannot stand a player on the boundary without this arithmetic being right first,
 * which is why the boundary is asked about here instead.
 */
class PowerCostTest {

	private static SuperNPlayer withPower(double power) {
		SuperNPlayer player = new SuperNPlayer("Cost");
		player.setPower(power);
		return player;
	}

	@Test
	void aCostIsPayableByExactlyThatMuch() {
		assertTrue(SuperNManager.canPay(withPower(5000), 5000),
				"five thousand pays for five thousand");
		assertTrue(SuperNManager.canPay(withPower(1000), 1000),
				"and a thousand for a thousand");
	}

	@Test
	void aCostIsNotPayableByLessThanThatMuch() {
		assertFalse(SuperNManager.canPay(withPower(4999.99), 5000),
				"a hundredth short is short");
		assertFalse(SuperNManager.canPay(withPower(999), 1000),
				"one short is short");
		assertFalse(SuperNManager.canPay(withPower(0), 5000),
				"an empty bar buys nothing that costs anything");
	}

	/** A free ability is payable by anybody: nobody is refused a thing that costs nothing. */
	@Test
	void aFreeCostIsPayableByNothing() {
		assertTrue(SuperNManager.canPay(withPower(0), 0), "a cost of nothing is always met");
	}

	/** And there is no player to charge, there is nobody who can pay. */
	@Test
	void nobodyCannotPayAnything() {
		assertFalse(SuperNManager.canPay(null, 0), "no player is not a player with an empty bar");
	}
}
