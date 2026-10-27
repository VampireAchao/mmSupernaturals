package me.matterz.supernaturals.it;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLConnection;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
 * <p>They also hold the line on where a skin may come from. The hall used to dress its
 * stand-ins in eleven real accounts' textures, copied out of Mojang's API into this repository;
 * it now draws its own and serves them from a loopback port. That is a claim about provenance,
 * so it is asserted rather than described.
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
        assertNotEquals(skinUrl(vampire), skinUrl(werewolf),
                "two races were handed the same skin: " + vampire + " / " + werewolf);
    }

    @Test
    @DisplayName("the skin is a PNG this project serves, and nothing Mojang hosts")
    void theSkinIsDrawnHere() throws IOException {
        String report = server().probe("ghost " + VAMPIRE + " vampire");
        String url = skinUrl(report);

        // Whatever a checkout does, one thing is not allowed: a texture inside Mojang's own
        // texture service, which is somebody's artwork wearing a UUID. That was the old design.
        assertFalse(url.contains("textures.minecraft.net"), "a skin borrowed from Mojang: " + url);

        // The drawn skins are served from loopback, so the URL has to be one this machine can
        // actually answer. A checkout that supplied its own file points somewhere else, and that
        // is its business - the assertion above is the one that still applies to it.
        if (!url.startsWith("http://127.0.0.1:")) {
            return;
        }
        assertTrue(report.contains("signature=no"),
                "a drawn skin is self-hosted, so there is no Mojang signature on it: " + report);
        byte[] png = fetch(url);
        assertTrue(isPng(png), "the hall advertised a skin that is not a PNG: " + url);
        assertEquals(64, readInt(png, 16), "a skin sheet is 64 wide: " + url);
        assertEquals(64, readInt(png, 20), "a skin sheet is 64 tall: " + url);
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

    /** That URL's bytes, on the understanding that it is this machine answering. */
    private static byte[] fetch(String url) throws IOException {
        URLConnection connection = URI.create(url).toURL().openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        try (InputStream in = connection.getInputStream()) {
            return in.readAllBytes();
        }
    }

    private static boolean isPng(byte[] bytes) {
        byte[] magic = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
        if (bytes.length < 24) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if (bytes[i] != magic[i]) {
                return false;
            }
        }
        return true;
    }

    /** A PNG's width or height: the first two numbers in its IHDR, big-endian. */
    private static int readInt(byte[] bytes, int at) {
        return ((bytes[at] & 0xFF) << 24) | ((bytes[at + 1] & 0xFF) << 16)
                | ((bytes[at + 2] & 0xFF) << 8) | (bytes[at + 3] & 0xFF);
    }
}
