package me.matterz.supernaturals.it;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The skins the hall's stand-ins wear.
 *
 * <p>A skin is a property the profile has to carry <em>before</em> the player is placed in the
 * world, and nothing on the server prints it - so a stand-in that came up without one looks
 * exactly like a stand-in that came up with one, from inside the server. These cases are what
 * tells those two apart: they read the profile back.
 *
 * <p>The stand-ins are brought up by asking the harness directly. The ones a player meets are
 * built by the hall, and the hall only exists on a hand-run server, so a scenario has no other
 * way to hold one.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SkinsIT extends ServerTest {

    private static final String VAMPIRE = "SkinProbeVampire";
    private static final String WEREWOLF = "SkinProbeWerewolf";

    @AfterAll
    void takeThemOut() {
        server().probe("drop " + VAMPIRE);
        server().probe("drop " + WEREWOLF);
    }

    @Test
    @DisplayName("a stand-in is built wearing the skin of the race he is playing")
    void wearsTheRace() {
        String vampire = server().probe("ghost " + VAMPIRE + " vampire");
        String werewolf = server().probe("ghost " + WEREWOLF + " werewolf");

        assertTrue(vampire.contains("textures=yes"), "no skin reached the vampire: " + vampire);
        assertTrue(werewolf.contains("textures=yes"), "no skin reached the werewolf: " + werewolf);
        assertTrue(vampire.contains("textures.minecraft.net/texture/"),
                "the value on the profile is not a texture: " + vampire);
        assertTrue(vampire.contains("signature=yes"),
                "the hall's textures are the signed ones, so this one lost its signature: " + vampire);
        assertNotEquals(skinUrl(vampire), skinUrl(werewolf),
                "two races were handed the same skin: " + vampire + " / " + werewolf);
    }

    @Test
    @DisplayName("a player the harness brings up for itself is left in the default skin")
    void theActorItselfIsNotDressed() {
        // The other half of the claim: not "every profile gets a skin", but "the ones that are
        // dressed are the ones that were meant to be". An actor is the tester standing in for a
        // person, and a tester wearing a vampire's face would be a reading of the room, not of
        // the change.
        Actor actor = server().spawnActor("SkinProbeActor");
        try {
            String worn = server().probe("skin " + actor.name());
            assertTrue(worn.contains("textures=no"), "the actor was dressed as something: " + worn);
        } finally {
            actor.leave();
        }
    }

    /** The texture URL out of one of the reports above, so two of them can be compared. */
    private static String skinUrl(String report) {
        int at = report.indexOf("url=");
        return at < 0 ? report : report.substring(at + "url=".length()).split("\\s+")[0];
    }
}
