package me.matterz.supernaturals.it.harness;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * The skin every stand-in in the hall wears, read from a file this checkout supplies.
 *
 * <p>A stand-in is a real {@code ServerPlayer} built around a hand-made profile
 * ({@link Nms#spawn(String, org.bukkit.World)}), and that profile carried no properties
 * at all - so every one of them drew as the default Steve or Alex, whatever race the
 * room was about. A skin is a {@code textures} property on that profile, and it has to
 * be on it <em>before</em> the player is placed in the world: the profile goes out with
 * the player-info packet that {@code placeNewPlayer} sends, and nothing sends it again.
 *
 * <p>Those values used to be frozen into this file: eleven real accounts' signed
 * textures, so that a stand-in in the vampire room wore a vampire and not a Steve. They
 * were read by hand out of Mojang's profile and session APIs and committed to a public
 * repository - other people's skins, copied to decorate a test server. They are gone.
 *
 * <p>What this file reads instead is {@code harness-skins.properties} at the project
 * root: ignored by git, supplied per checkout, its shape shown by
 * {@code harness-skins.properties.example}. Nothing here requires it. With no such file
 * every stand-in draws in the default skin, one line in the log says why, and the case
 * that checks a skin reached a profile skips itself rather than failing - a plain face
 * is not a broken harness.
 */
public final class Skins {

    /** One {@code textures} property: the base64 profile blob, and its signature. */
    record Skin(String value, String signature) {
    }

    private static final String DEFAULT_FILE = "harness-skins.properties";

    private static final Properties SUPPLIED = read();

    /** The guide in the vampire room. */
    static final Skin VAMPIRE = skin("vampire");

    /** The guide in the ghoul room. */
    static final Skin GHOUL = skin("ghoul");

    /** The guide in the werewolf room. */
    static final Skin WEREWOLF = skin("werewolf");

    /** The guide in the demon room, both nether corridors included. */
    static final Skin DEMON = skin("demon");

    /** The guide in the enderborn room. */
    static final Skin ENDERBORN = skin("enderborn");

    /** The guide in the priest room. */
    static final Skin PRIEST = skin("priest");

    /** The guide in the witchhunter room. */
    static final Skin WITCHHUNTER = skin("witchhunter");

    /** The guide in the angel room. */
    static final Skin ANGEL = skin("angel");

    /** The sparring master in every room, and the sparring partner himself. */
    static final Skin MASTER = skin("master");

    /** The recorder / observer / examiner in each room. */
    static final Skin SCRIBE = skin("scribe");

    /** The target in each room: the one that gets killed, over and over. */
    static final Skin HUMAN = skin("human");

    /**
     * The file the skins come from: {@code harness-skins.properties} at the project root,
     * or whatever {@code -Dit.skins=} names.
     *
     * <p>Public because {@code SkinsIT} sits in another package and asks whether the file
     * is there before it claims anything about a skin.
     */
    public static Path file() {
        return Path.of(System.getProperty("it.skins", DEFAULT_FILE));
    }

    /** The skin supplied under that key, or {@code null} when this checkout supplied none. */
    private static Skin skin(String key) {
        String value = SUPPLIED.getProperty(key + ".value", "").trim();
        if (value.isEmpty()) {
            return null;
        }
        String signature = SUPPLIED.getProperty(key + ".signature", "").trim();
        return new Skin(value, signature.isEmpty() ? null : signature);
    }

    /** Whatever the file holds, or an empty set - this runs before the first room is built. */
    private static Properties read() {
        Properties supplied = new Properties();
        Path file = file();
        Logger log = Logger.getLogger("mmSupernaturals");
        if (!Files.isReadable(file)) {
            log.info("no skins supplied (" + file.toAbsolutePath() + "), so every stand-in draws in "
                    + "the default skin; copy harness-skins.properties.example to give them faces");
            return supplied;
        }
        try (Reader in = Files.newBufferedReader(file)) {
            supplied.load(in);
        } catch (IOException | IllegalArgumentException e) {
            log.warning("could not read " + file.toAbsolutePath() + ", so every stand-in draws in "
                    + "the default skin: " + e.getMessage());
        }
        return supplied;
    }

    /**
     * The skin named, as a race ("vampire") or as a role ("MASTER").
     *
     * <p>One name for both because a caller asking for a stand-in's skin is asking the same
     * question either way: what is this one playing. Anything not on the list is an ordinary
     * person, which is what "human" means too - so a stand-in whose race this file has never
     * heard of is still somebody rather than nobody.
     *
     * <p>{@code null} means "the default skin", not "nobody" and not an error: every caller
     * hands it straight to {@link Nms#spawn(String, org.bukkit.World, Skins.Skin)}, which has
     * always read {@code null} that way. With no {@code harness-skins.properties} in the
     * checkout every name is null, and the hall still runs.
     */
    static Skin of(String key) {
        if (key == null) {
            return HUMAN;
        }
        return switch (key.toLowerCase(Locale.ROOT)) {
            case "vampire" -> VAMPIRE;
            case "ghoul" -> GHOUL;
            case "werewolf" -> WEREWOLF;
            case "demon" -> DEMON;
            case "enderborn" -> ENDERBORN;
            case "priest" -> PRIEST;
            case "witchhunter" -> WITCHHUNTER;
            case "angel" -> ANGEL;
            case "master" -> MASTER;
            case "scribe" -> SCRIBE;
            default -> HUMAN;
        };
    }

    private Skins() {
    }
}
