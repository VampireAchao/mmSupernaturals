package me.matterz.supernaturals.it.harness;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * The skin every stand-in in the hall wears: drawn by this project, served by this project.
 *
 * <p>A stand-in is a real {@code ServerPlayer} built around a hand-made profile
 * ({@link Nms#spawn(String, org.bukkit.World)}), and that profile carried no properties
 * at all - so every one of them drew as the default Steve or Alex, whatever race the
 * room was about. A skin is a {@code textures} property on that profile, and it has to
 * be on it <em>before</em> the player is placed in the world: the profile goes out with
 * the player-info packet that {@code placeNewPlayer} sends, and nothing sends it again.
 *
 * <p>The textures used to be eleven real accounts', read out of Mojang's profile and
 * session APIs and frozen into this file. They worked, and they were other people's
 * artwork decorating a test server. They are gone, and what replaced them is not another
 * person's skin under a different name: each race gets two colours and a trim stripe,
 * painted as flat rectangles by {@link SkinArt} and served from a loopback port by
 * {@link SkinServer}. Nothing is downloaded, nothing is signed, and nothing here belongs
 * to anybody but this project.
 *
 * <p>A checkout may still supply its own - {@code harness-skins.properties}, ignored by
 * git, its shape shown by {@code harness-skins.properties.example}. That is for your own
 * texture, and it is optional: with no such file every stand-in wears a drawn one.
 */
final class Skins {

    /** The two colours a skin is drawn from: the cloth, and the trim. */
    private record Tone(int base, int accent) {
    }

    /**
     * One {@code textures} property: the base64 profile blob, and its signature.
     *
     * <p>The blob is built when a profile is built rather than when this class loads, because
     * it names the port the skin server picked - and a class can be loaded before anything has
     * started that server.
     */
    static final class Skin {

        private final String key;
        private final Tone tone;
        private final String supplied;
        private final String suppliedSignature;

        private Skin(String key, Tone tone, String supplied, String suppliedSignature) {
            this.key = key;
            this.tone = tone;
            this.supplied = supplied;
            this.suppliedSignature = suppliedSignature;
        }

        /**
         * The property value, or {@code null} when there is nothing to put on the profile.
         *
         * <p>{@code null} means "the default skin" to every caller - not "nobody", and not an
         * error. It is what a checkout gets when the skin server could not bind a port.
         */
        String value() {
            if (supplied != null) {
                return supplied;
            }
            String url = SkinServer.url(key, tone.base(), tone.accent());
            return url == null ? null : encode(key, url);
        }

        /**
         * The signature, or {@code null}.
         *
         * <p>Drawn skins are not signed and do not need to be: {@code enforce-secure-profile=false}
         * on the test server is what an unsigned, self-hosted texture needs instead.
         */
        String signature() {
            return suppliedSignature;
        }
    }

    private static final String DEFAULT_FILE = "harness-skins.properties";

    private static final Properties SUPPLIED = read();

    /** Two flat colours per race. Chosen here, so that nobody has to be asked for a licence. */
    private static final Map<String, Tone> TONES = Map.ofEntries(
            Map.entry("vampire", new Tone(0x5A0E17, 0xE7DBC6)),
            Map.entry("ghoul", new Tone(0x5E6B4A, 0xD9D6C3)),
            Map.entry("werewolf", new Tone(0x4A3524, 0xC9A15A)),
            Map.entry("demon", new Tone(0x3B0A0A, 0xE2591F)),
            Map.entry("enderborn", new Tone(0x1B1430, 0x8F6BE0)),
            Map.entry("priest", new Tone(0xE6E1D3, 0xC9A227)),
            Map.entry("witchhunter", new Tone(0x2E2419, 0x8A8F96)),
            Map.entry("angel", new Tone(0xCFE3F2, 0xF2D64B)),
            Map.entry("master", new Tone(0x39424A, 0xBFC6CC)),
            Map.entry("scribe", new Tone(0x4A4A4A, 0xD0C8B0)),
            Map.entry("human", new Tone(0x8A6B4F, 0x3C3C3C)));

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

    private static Skin skin(String key) {
        String supplied = SUPPLIED.getProperty(key + ".value", "").trim();
        String signature = SUPPLIED.getProperty(key + ".signature", "").trim();
        return new Skin(key, TONES.getOrDefault(key, TONES.get("human")),
                supplied.isEmpty() ? null : supplied,
                signature.isEmpty() ? null : signature);
    }

    /** The {@code textures} blob a client reads: a URL it fetches, and the model to draw it on. */
    private static String encode(String key, String url) {
        String json = "{"
                + "\"timestamp\":0,"
                + "\"profileId\":\"" + UUID.nameUUIDFromBytes(("mmSupernaturals:" + key)
                        .getBytes(StandardCharsets.UTF_8)) + "\","
                + "\"profileName\":\"" + key + "\","
                + "\"signatureRequired\":false,"
                + "\"textures\":{"
                + "\"SKIN\":{"
                + "\"url\":\"" + url + "\","
                + "\"metadata\":{\"model\":\"classic\"}"
                + "}"
                + "}"
                + "}";
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    /** The file a checkout may leave its own skins in, or an empty set of properties. */
    private static Properties read() {
        Properties supplied = new Properties();
        Path file = file();
        Logger log = Logger.getLogger("mmSupernaturals");
        if (!Files.isReadable(file)) {
            log.info("no skins supplied (" + file.toAbsolutePath() + "), so every stand-in wears one "
                    + "this project draws; copy harness-skins.properties.example to put your own on");
            return supplied;
        }
        try (Reader in = Files.newBufferedReader(file)) {
            supplied.load(in);
        } catch (IOException | IllegalArgumentException e) {
            log.warning("could not read " + file.toAbsolutePath() + ", so every stand-in wears one "
                    + "this project draws: " + e.getMessage());
        }
        return supplied;
    }

    /**
     * Where to look for a checkout's own skins.
     *
     * <p>A bare name means the project directory, not the working directory: this class runs
     * inside the server JVM, whose working directory is {@code target/it/server} - a directory
     * the fixture empties before every run, and the last place a file meant to survive would be
     * found. {@code -Dit.skins} takes an absolute path for anything else.
     */
    private static Path file() {
        Path named = Path.of(System.getProperty("it.skins", DEFAULT_FILE));
        if (named.isAbsolute()) {
            return named;
        }
        String project = System.getProperty("it.projectDir", System.getProperty("user.dir"));
        return Path.of(project).resolve(named);
    }

    /**
     * The skin named, as a race ("vampire") or as a role ("MASTER").
     *
     * <p>One name for both because a caller asking for a stand-in's skin is asking the same
     * question either way: what is this one playing. Anything not on the list is an ordinary
     * person, which is what "human" means too - so a stand-in whose race this file has never
     * heard of is still somebody rather than nobody.
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
