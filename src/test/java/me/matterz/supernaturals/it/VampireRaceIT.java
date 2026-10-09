package me.matterz.supernaturals.it;

import org.bukkit.Material;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * The vampire, in two cases: getting in and getting back out.
 *
 * <p>They are ordered and share one actor on purpose. "Leaving" only means anything
 * once somebody is in the race, so the second case states the state it needs and the
 * first case is what puts it there - the same shape every other race follows.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class VampireRaceIT extends ServerTest {

    private static final int ALTAR_X = 8;
    private static final int ALTAR_Z = 8;
    private static final int CURE_X = 24;

    private Actor actor;

    @BeforeAll
    void walkIn() {
        actor = server().spawnActor("VampireActor");
        actor.grant("supernatural.world.enabled");
    }

    @AfterAll
    void walkOut() {
        actor.leave();
    }

    @Test
    @Order(1)
    @DisplayName("joining: the vampire altar turns the actor into a vampire")
    void joinsTheRace() {
        assertEquals("human", actor.race(), "the actor has to start out human");
        vampireAltar(ALTAR_X, ALTAR_Z);
        assertEquals(Material.GOLD_BLOCK, actor.blockAt(ALTAR_X, GROUND_Y, ALTAR_Z),
                "the shrine was not built where the actor is about to click");
        actor.grant("supernatural.player.shrineuse.vampire");
        actor.give("MUSHROOM_STEW", 4)
                .give("BONE", 32)
                .give("GUNPOWDER", 32)
                .give("REDSTONE", 32);
        assertEquals(4, actor.count("MUSHROOM_STEW"), "the ingredients never reached the actor");
        actor.teleport(ALTAR_X + 0.5, GROUND_Y + 1, ALTAR_Z + 0.5);

        Actor.Interaction click = actor.interact(ALTAR_X, GROUND_Y, ALTAR_Z);

        assertFalse(click.cancelled(), () -> "the click never reached the plugin: " + click);
        assertEquals("vampire", actor.race(), "the altar was clicked but nothing happened");
    }

    @Test
    @Order(2)
    @DisplayName("leaving: the cure altar turns the vampire back into a human")
    void leavesTheRace() {
        assertEquals("vampire", actor.race(), "case 1 has to run first - it is the precondition");

        cureAltar(CURE_X, ALTAR_Z);
        assertEquals(Material.LAPIS_BLOCK, actor.blockAt(CURE_X, GROUND_Y, ALTAR_Z));
        actor.give("WATER_BUCKET", 1)
                .give("MILK_BUCKET", 1)
                .give("SUGAR", 32)
                .give("WHEAT", 32);
        actor.teleport(CURE_X + 0.5, GROUND_Y + 1, ALTAR_Z + 0.5);

        Actor.Interaction click = actor.interact(CURE_X, GROUND_Y, ALTAR_Z);

        assertFalse(click.cancelled(), () -> "the click never reached the plugin: " + click);
        assertEquals("human", actor.race(), "the cure altar was clicked but nothing happened");
    }
}
