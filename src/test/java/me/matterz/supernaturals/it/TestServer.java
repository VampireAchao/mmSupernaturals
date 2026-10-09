package me.matterz.supernaturals.it;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;

/**
 * The scratch Paper server the scenarios run against, and the only thing that starts a
 * server at all - there is no separate run configuration to keep in step with it.
 *
 * <p>Everything it needs is under {@code target/}: the server jar it launches (downloaded on
 * first run), the world it generates, Paper's own config, and the plugin jar {@code mvn
 * package} writes. A fresh clone therefore needs nothing seeded by hand, and {@code mvn
 * clean} is the only thing that resets the lot - at the price of downloading Paper again.
 * The world comes from a seeded flat generator, so any two machines produce the same one,
 * and scenarios share one such server per test JVM.
 *
 * <p>It serves on {@link #GAME_PORT}, the vanilla default, so a client joins with a plain
 * {@code localhost} rather than a port someone has to remember. The price is that anything
 * else on this machine already using that port has to be stopped first.
 *
 * <h2>Testing by hand</h2>
 * {@link #main} starts the server, streams its console to this terminal and keeps it up, so
 * a real client can join and the plugin can be driven by typing commands here:
 * <pre>
 *   mvn -Pit package exec:java
 * </pre>
 * {@code package} is not decoration: it is what builds the plugin the server loads.
 * {@code exec:java} alone has no jar to load and refuses before starting anything, which is
 * the first thing a fresh clone runs into. The entry point is a main method rather than an
 * IDE run configuration on purpose: it lives in version control, so losing an IDE's settings
 * cannot lose the ability to start a server.
 *
 * <p>Run it from the IDE's Debug action and the plugin is debuggable too: the server runs
 * in its own JVM, so this process being debugged does not cover the plugin, and the main
 * method opens a JDWP listener in the server's JVM for a second debugger to attach to.
 * Set {@code -Dit.debugSuspend=true} to hold the server before its first line of code, for
 * breakpoints in {@code onEnable}.
 *
 * <p>Every run starts from a rebuilt server directory. The world, Paper's config files, its
 * ops/whitelist/usercache files, the logs and every plugin's data directory are deleted
 * first, so no scenario can pass on leftovers; only the three cached artifacts that are
 * expensive to obtain again (the downloaded vanilla jar, the patched server jar, and the
 * resolved libraries) survive.
 *
 * <p>Switches, all off for the scenarios:
 * <ul>
 *   <li>{@code -Dit.keepServer=true} leaves the server running when the tests finish.</li>
 *   <li>{@code -Dit.debug=true} opens a JDWP port as well (5005, or
 *       {@code -Dit.debugPort}), so a remote debugger can attach and breakpoints in the
 *       plugin work while a scenario runs.</li>
 *   <li>{@code -Dit.keepState=true} keeps the world and the plugin's data between runs,
 *       for iterating on one world by hand.</li>
 * </ul>
 */
public final class TestServer implements AutoCloseable {

    /**
     * Where the server jar lives: in the fixture's own directory, so there is exactly one
     * copy of it. Downloaded here if this checkout has never run.
     */
    private static final String PAPER_JAR = "target/it/server/paper.jar";

    /** Where {@code mvn package} leaves the plugin under test - Maven's own output. */
    private static final String PLUGIN_JAR = "target/mmSupernaturals.jar";

    /** The one seed the flat test world uses, so its terrain is identical on every machine. */
    private static final String FLAT_SEED = "mmSupernaturals";

    /** The vanilla default, so a client joins with a plain {@code localhost}. */
    public static final int GAME_PORT = Integer.getInteger("it.gamePort", 25565);

    /**
     * The harness's control socket, not a game port: nothing outside this fixture ever
     * connects to it. One above {@link #GAME_PORT} only so the pair is easy to remember.
     *
     * <p>Both ports can be moved with {@code -Dit.gamePort} / {@code -Dit.controlPort}, which
     * is how two of these - or the ITs while a hand-run one is up - share a machine.
     */
    public static final int CONTROL_PORT = Integer.getInteger("it.controlPort", 25566);

    private static final int DEFAULT_DEBUG_PORT = 5005;

    /** Where the debug agent listens, and where a debugger has to look for it. */
    private static final String LOOPBACK = "127.0.0.1";
    private static final String PAPER_BUILDS = "https://fill.papermc.io/v3/projects/paper/versions/";
    private static final String PAPER_DOWNLOAD = "https://papermc.io/downloads/paper";
    private static final Duration STARTUP_TIMEOUT = Duration.ofMinutes(3);
    private static final String HARNESS_CLASSES = "me/matterz/supernaturals/it/harness";

    /**
     * The only things kept between runs: the vanilla jar Paper downloads, the jar it patches
     * from that one, and the libraries it resolves - all of them tens of megabytes, and all
     * of them derived from the server jar rather than from anything a scenario does.
     *
     * <p>Everything else under the test server's directory is deleted before a run: the
     * world (and with it every player's data, stats and advancements), all of Paper's config
     * files, ops/whitelist/usercache, the logs, and every plugin's data directory. A test
     * that passed on leftovers from the previous run would be a bug that shows up in CI, or
     * never.
     */
    private static final Set<String> CACHED = Set.of("paper.jar", "plugins", "versions", "libraries", "cache");

    private static TestServer shared;

    /** Set once a JDWP port has been picked, so the banner can name the port in use. */
    private static volatile int resolvedDebugPort = -1;

    /** The seed the world was built from, decided once so the banner cannot contradict it. */
    private static volatile String resolvedSeed;

    /** Whether {@link #reset} left the previous run's world in place, {@code -Dit.keepState}. */
    private static volatile boolean keptWorld;

    /** The one server for this JVM, started on first use. */
    public static synchronized TestServer shared() {
        if (shared == null) {
            shared = start();
            Runtime.getRuntime().addShutdownHook(new Thread(shared::close, "it-server"));
        }
        return shared;
    }

    /**
     * Starts the server, streams its console to this terminal, and holds it open for testing
     * by hand: join with a real client on {@link #GAME_PORT}, walk to the altars the
     * scenarios use, type server commands here, and attach a debugger to the plugin on the
     * port in the banner. Stops with Ctrl+C, saving the world.
     *
     * <p>The world is generated terrain by default - a fresh one every run - because that is
     * what the plugin's races want to be tried in. {@code -Dit.flat=true} asks for the flat
     * world the scenarios themselves run in instead.
     */
    public static void main(String[] args) throws Exception {
        boolean debugging = isDebuggingThisJvm();
        System.setProperty("it.keepServer", "true");
        System.setProperty("it.interactive", "true");
        // Under a debugger, make the plugin debuggable as well; -Dit.debug overrides either way.
        System.setProperty("it.debug", System.getProperty("it.debug", Boolean.toString(debugging)));

        if (Boolean.getBoolean("it.debug") && Boolean.getBoolean("it.debugSuspend")) {
            System.out.println("the server is held until a debugger attaches to "
                    + LOOPBACK + ":" + debugPort());
        }

        boolean flat = flatWorld();
        TestServer server = start();
        Runtime.getRuntime().addShutdownHook(new Thread(server::shutdown, "it-server"));
        server.forwardConsole();
        String hallAt = server.hallLobby();

        System.out.println();
        System.out.println("test server is up - Ctrl+C stops it (the world is saved)");
        System.out.println("  join          : localhost:" + GAME_PORT + "   offline mode, any name");
        System.out.println("  console       : type a server command here, e.g.  op YourName");
        if (hallAt != null) {
            System.out.println("  hall          : you spawn in the lobby at " + hallAt);
            System.out.println("                  eight race buttons on the lobby wall, plus leaving."
                    + " Each race hall is");
            System.out.println("                  a corridor of numbered sections, read left to"
                    + " right; every row ends");
            System.out.println("                  with a damage dummy, a +1000 power switch, and"
                    + " switches back to");
            System.out.println("                  the start or out of the test");
        }
        System.out.println("  world         : " + (flat
                ? "flat test world, -Dit.flat=false for real terrain"
                : keptWorld
                        ? "kept from the last run, -Dit.flat=true for the test world"
                        : "generated, seed " + resolvedSeed + ", -Dit.seed=<n> to reuse it"));
        if (resolvedDebugPort > 0) {
            System.out.println("  plugin debug  : attach a remote JVM debugger to "
                    + LOOPBACK + ":" + resolvedDebugPort);
            if (debugging) {
                System.out.println("                  (the plugin runs in the server JVM, so this is a"
                        + " second debugger)");
            }
        }
        System.out.println("  files         : " + server.directory());
        System.out.println("  state         : rebuilt on start; -Dit.keepState=true keeps it");
        System.out.println();

        // Typing "stop" here ends the server; this ends the launcher with it rather than
        // sitting on a join with nothing left to do.
        int exit = server.awaitExit();
        System.out.println("the server has stopped (exit " + exit + ")");
        System.exit(exit);
    }

    private final Path directory;
    private final Process process;
    private final ControlClient control;
    private final Path console;
    private volatile boolean stopped;

    private TestServer(Path directory, Process process, ControlClient control, Path console) {
        this.directory = directory;
        this.process = process;
        this.control = control;
        this.console = console;
    }

    // -- lifecycle ---------------------------------------------------------

    private static TestServer start() {
        Path project = projectDirectory();
        Path work = project.resolve("target/it");
        Path server = work.resolve("server");
        Path console = server.resolve("console.log");
        boolean interactive = Boolean.getBoolean("it.interactive");
        try {
            Files.createDirectories(server.resolve("plugins"));
            reset(server);
            install(project, work, server);
            writeConfiguration(server);

            ProcessBuilder builder = new ProcessBuilder(command())
                    .directory(server.toFile())
                    .redirectErrorStream(true);
            if (interactive) {
                // A hand-run server shows its console here. The log is created up front -
                // a startup failure reads it - and filled as the output streams past.
                Files.writeString(console, "");
            } else {
                builder.redirectOutput(console.toFile());
            }

            Process process = builder.start();
            if (interactive) {
                teeConsole(process.getInputStream(), console);
            }
            return new TestServer(server, process, awaitControl(process, console), console);
        } catch (IOException e) {
            throw new IllegalStateException("could not start the test server under " + server, e);
        }
    }

    private static List<String> command() {
        List<String> command = new ArrayList<>(List.of(
                javaBinary(), "-Xms1G", "-Xmx1G", "-Dharness.port=" + CONTROL_PORT));
        if (Boolean.getBoolean("it.interactive")) {
            // A hand-run server gets the test hall. The scenarios place what they need at
            // known coordinates and would only be in its way.
            command.add("-Dharness.hall=true");
        }
        if (Boolean.getBoolean("it.debug")) {
            // The plugin lives in the server JVM, so the listener goes on that JVM rather
            // than on this one; that is what makes a debugger able to stop in the plugin.
            String suspend = Boolean.getBoolean("it.debugSuspend") ? "y" : "n";
            command.add("-agentlib:jdwp=transport=dt_socket,server=y,suspend=" + suspend
                    + ",address=" + LOOPBACK + ":" + debugPort());
        }
        command.add("-jar");
        command.add("paper.jar");
        command.add("--nogui");
        return command;
    }

    /**
     * Puts the server directory back to "never run before", except for {@link #CACHED}.
     * {@code -Dit.keepState=true} also keeps the world and the plugin's data, for the
     * workflow of iterating on one world by hand.
     */
    private static void reset(Path server) throws IOException {
        if (!Files.isDirectory(server)) {
            return;
        }
        boolean keepState = Boolean.getBoolean("it.keepState");
        keptWorld = keepState && Files.isDirectory(server.resolve("world"));
        try (Stream<Path> entries = Files.list(server)) {
            for (Path entry : entries.toList()) {
                String name = entry.getFileName().toString();
                if (CACHED.contains(name) || (keepState && name.equals("world"))) {
                    continue;
                }
                deleteRecursively(entry);
            }
        }
        Path plugins = server.resolve("plugins");
        try (Stream<Path> entries = Files.list(plugins)) {
            for (Path entry : entries.toList()) {
                String name = entry.getFileName().toString();
                // The jars are put back by install(); everything else in here is state.
                if (name.endsWith(".jar") || (keepState && name.equals("mmSupernaturals"))) {
                    continue;
                }
                deleteRecursively(entry);
            }
        }
    }

    private static void install(Path project, Path work, Path server) throws IOException {
        ensurePaper(project);
        Files.copy(requirePluginJar(project), server.resolve("plugins/mmSupernaturals.jar"),
                StandardCopyOption.REPLACE_EXISTING);
        Files.copy(buildHarnessJar(project, work), server.resolve("plugins/mmHarness.jar"),
                StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * The jar the launch command names, already in place or fetched now. Nothing is copied:
     * it is downloaded straight to where the server runs from, so the fixture holds one copy.
     */
    private static void ensurePaper(Path project) {
        Path jar = project.resolve(PAPER_JAR);
        if (Files.isRegularFile(jar)) {
            return;
        }
        try {
            downloadPaper(jar);
        } catch (Exception e) {
            throw new IllegalStateException(fixThis(
                    "Paper server jar is missing and could not be downloaded.",
                    jar,
                    "Paper " + minecraftVersion() + " (this build pins " + paperVersion() + " or newer)",
                    PAPER_DOWNLOAD,
                    e))
                    ;
        }
    }

    /**
     * Fetches the newest build of the pinned Minecraft version, straight to {@code target},
     * where the launch command will name it. Only reached when there is none to use, so a
     * checkout that has run before never touches the network.
     */
    private static void downloadPaper(Path target) throws IOException, InterruptedException {
        System.out.println("no server jar yet, downloading Paper " + minecraftVersion() + " ...");
        try (HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build()) {
            String builds = PAPER_BUILDS + minecraftVersion() + "/builds";
            JsonArray releases = JsonParser.parseString(get(http, builds)).getAsJsonArray();
            JsonObject download = releases.get(0).getAsJsonObject()
                    .getAsJsonObject("downloads")
                    .getAsJsonObject("server:default");
            String name = download.get("name").getAsString();
            String url = download.get("url").getAsString();

            Files.createDirectories(target.getParent());
            Path partial = target.resolveSibling(target.getFileName() + ".part");
            HttpResponse<Path> response = http.send(
                    HttpRequest.newBuilder(URI.create(url))
                            .header("User-Agent", "mmSupernaturals-it").build(),
                    HttpResponse.BodyHandlers.ofFile(partial));
            if (response.statusCode() != 200) {
                Files.deleteIfExists(partial);
                throw new IOException(name + " answered HTTP " + response.statusCode());
            }
            Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING);
            System.out.println("  saved " + name + " to " + target);
        }
    }

    private static String get(HttpClient http, String url) throws IOException, InterruptedException {
        HttpResponse<String> response = http.send(
                HttpRequest.newBuilder(URI.create(url))
                        .header("User-Agent", "mmSupernaturals-it").build(),
                HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException(url + " answered HTTP " + response.statusCode());
        }
        return response.body();
    }

    /** The plugin under test, built by {@code mvn package} into {@code target/}. */
    private static Path requirePluginJar(Path project) {
        Path jar = project.resolve(PLUGIN_JAR);
        if (!Files.isRegularFile(jar)) {
            throw new IllegalStateException(fixThis(
                    "The plugin under test has not been built.",
                    jar,
                    "run `mvn -Pit package exec:java` - package is what builds it",
                    null,
                    null));
        }
        return jar;
    }

    /** One shape for every "a file is missing" message, so the fix is always in the same place. */
    private static String fixThis(String problem, Path expected, String what, String download,
            Exception cause) {
        String lines = "  expected at : " + expected + System.lineSeparator()
                + "  needed      : " + what;
        if (download != null) {
            lines += System.lineSeparator() + "  download    : " + download;
        }
        if (cause != null) {
            lines += System.lineSeparator() + "  reason      : " + cause;
        }
        return problem + System.lineSeparator() + System.lineSeparator()
                + lines + System.lineSeparator();
    }

    /**
     * Packs the harness from {@code target/test-classes} plus its descriptor. Building it
     * here instead of with a Maven plugin keeps the packaging of the actual artifact
     * completely untouched.
     */
    private static Path buildHarnessJar(Path project, Path work) throws IOException {
        Path classes = project.resolve("target/test-classes");
        Path descriptor = project.resolve("src/test/resources/harness/plugin.yml");
        if (!Files.isDirectory(classes) || !Files.isRegularFile(descriptor)) {
            throw new IllegalStateException(fixThis(
                    "The harness classes are not compiled yet.",
                    classes,
                    "run `mvn test-compile` first",
                    null,
                    null));
        }
        Path jar = work.resolve("mmHarness.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            out.putNextEntry(new JarEntry("plugin.yml"));
            out.write(Files.readAllBytes(descriptor));
            out.closeEntry();
            try (Stream<Path> files = Files.walk(classes.resolve(HARNESS_CLASSES))) {
                for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                    out.putNextEntry(new JarEntry(classes.relativize(file).toString().replace('\\', '/')));
                    out.write(Files.readAllBytes(file));
                    out.closeEntry();
                }
            }
        }
        return jar;
    }

    /**
     * Writes the properties the fixture needs, and chooses what kind of world it is:
     * {@link #flatWorld()} decides, because a scenario asserts blocks by coordinate and
     * needs the fixed flat one, while a developer trying a race wants terrain.
     */
    private static void writeConfiguration(Path server) throws IOException {
        boolean flat = flatWorld();
        String layers = "{\"layers\":[{\"block\":\"minecraft:bedrock\",\"height\":1},"
                + "{\"block\":\"minecraft:stone\",\"height\":3},"
                + "{\"block\":\"minecraft:dirt\",\"height\":1},"
                + "{\"block\":\"minecraft:grass_block\",\"height\":1}],"
                + "\"biome\":\"minecraft:plains\"}";
        Files.writeString(server.resolve("eula.txt"), "eula=true\n");
        Files.writeString(server.resolve("server.properties"), String.join("\n",
                "online-mode=false",
                "enforce-secure-profile=false",
                "spawn-protection=0",
                "server-port=" + GAME_PORT,
                // Paper 26.3 defaults a fresh config to white-list=true, which turned a
                // joining developer away with "not whitelisted" while the log said nothing
                // about where that came from. Pinned false, like the rest of this list.
                "white-list=false",
                "enforce-whitelist=false",
                "level-name=world",
                "level-type=" + (flat ? "minecraft:flat" : "minecraft:normal"),
                "level-seed=" + worldSeed(flat),
                "generator-settings=" + (flat ? layers : ""),
                "gamemode=survival",
                "difficulty=normal",
                // The demon area is the nether, so the fixture needs one to exist.
                "allow-nether=true",
                "max-players=20",
                "view-distance=6",
                "simulation-distance=4",
                "enable-command-block=true") + "\n");
    }

    /**
     * True when the fixed flat test world is wanted rather than generated terrain. The
     * scenarios always want it - they assert blocks by absolute coordinate - and a hand-run
     * server does not, unless asked.
     */
    private static boolean flatWorld() {
        return !Boolean.getBoolean("it.interactive") || Boolean.getBoolean("it.flat");
    }

    /** The flat world is seeded once forever; a hand-run world gets a new seed each run. */
    private static String worldSeed(boolean flat) {
        if (flat) {
            return FLAT_SEED;
        }
        // Rolled once: writeConfiguration and the banner both ask, and they have to agree.
        if (resolvedSeed == null) {
            resolvedSeed = System.getProperty("it.seed",
                    Long.toString(ThreadLocalRandom.current().nextLong()));
        }
        return resolvedSeed;
    }

    private static ControlClient awaitControl(Process process, Path console) throws IOException {
        // A suspended server is deliberately waiting for a debugger, so waiting for it is
        // not the same thing as waiting for a slow startup.
        Duration timeout = Boolean.getBoolean("it.debugSuspend")
                ? Duration.ofMinutes(15)
                : STARTUP_TIMEOUT;
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (!process.isAlive()) {
                throw new IllegalStateException("the test server exited during startup (exit "
                        + process.exitValue() + "):\n" + tail(console));
            }
            try {
                ControlClient client = new ControlClient(CONTROL_PORT, 2_000);
                if ("pong".equals(client.call("ping"))) {
                    return client;
                }
                client.close();
            } catch (IOException | IllegalStateException e) {
                // still booting
            }
            sleep();
        }
        throw new IllegalStateException("no answer on port " + CONTROL_PORT + " within "
                + timeout + ":\n" + tail(console));
    }

    /**
     * Copies the server's output to this terminal while writing the same lines to the log,
     * so a hand-run server looks like a normal one and the log still has everything for
     * startup failures. Reads to end-of-stream, which arrives when the server exits.
     */
    private static void teeConsole(InputStream from, Path console) {
        Thread tee = new Thread(() -> {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(from));
                    BufferedWriter out = Files.newBufferedWriter(console,
                            StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                String line;
                while ((line = in.readLine()) != null) {
                    System.out.println(line);
                    out.write(line);
                    out.newLine();
                    out.flush();
                }
            } catch (IOException e) {
                // the server exited, or the terminal went away; the log has what we saw
            }
        }, "it-server-output");
        tee.setDaemon(true);
        tee.start();
    }

    private static void sleep() {
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Sends this process's input to the server's console. Typed input has to be pumped by
     * hand because the server's stdin is a pipe rather than this terminal - and leaving it
     * as a pipe is deliberate: a server whose stdin reaches end-of-input hangs during
     * startup, while a pipe nobody writes to simply waits.
     */
    private void forwardConsole() {
        Thread pump = new Thread(() -> {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(System.in))) {
                PrintWriter out = new PrintWriter(
                        new OutputStreamWriter(process.getOutputStream()), true);
                String line;
                while ((line = in.readLine()) != null) {
                    out.println(line);
                }
            } catch (IOException e) {
                // input closed; the server keeps running, its console just goes quiet
            }
        }, "it-server-console");
        pump.setDaemon(true);
        pump.start();
    }

    // -- what scenarios use ------------------------------------------------

    /** Adds a player to the game and returns a handle the scenario acts through. */
    public Actor spawnActor(String name) {
        control.call("spawn " + name);
        return new Actor(control, name);
    }

    /** Runs a server command, the same way the console would. */
    public String run(String command) {
        return control.call("run " + command);
    }

    public Path directory() {
        return directory;
    }

    public Path consoleLog() {
        return console;
    }

    /**
     * Where the hall's lobby is, as {@code x y z}. The hall is the harness's, not this
     * class's, so this only asks it - and answers null on a server that has none.
     */
    private String hallLobby() {
        try {
            return control.call("hall");
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Blocks until the server process exits; its exit code. The hand-run mode uses this. */
    public int awaitExit() throws InterruptedException {
        return process.waitFor();
    }

    /** Asks for the server to be kept: {@code -Dit.keepServer}, or the main method. */
    @Override
    public synchronized void close() {
        if (!Boolean.getBoolean("it.keepServer")) {
            shutdown();
        }
    }

    /** Stops the server and waits for it to save. Safe to call more than once. */
    private synchronized void shutdown() {
        if (stopped) {
            return;
        }
        stopped = true;
        try {
            control.call("stop");
            if (!process.waitFor(60, TimeUnit.SECONDS)) {
                process.destroy();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroy();
        } catch (RuntimeException e) {
            process.destroy();
        } finally {
            control.close();
        }
    }

    // -- small things ------------------------------------------------------

    private static Path projectDirectory() {
        return Path.of(System.getProperty("it.projectDir", System.getProperty("user.dir")));
    }

    /** A position written the way a server command wants it, minus the decimals. */
    private static String at(int x, int y, int z) {
        return x + "," + y + "," + z;
    }

    /** True when this JVM itself was launched by a debugger, e.g. the IDE's Debug action. */
    private static boolean isDebuggingThisJvm() {
        return ManagementFactory.getRuntimeMXBean().getInputArguments().stream()
                .anyMatch(argument -> argument.contains("jdwp"));
    }

    /**
     * The port the server JVM's debugger listens on: {@code -Dit.debugPort} (5005) if free,
     * else the next free one up, so a debug session left over from last time cannot stop the
     * server starting - a JDWP agent that cannot bind takes the whole JVM down with it.
     *
     * <p>The probe asks for exactly what the agent will ask for, {@value #LOOPBACK} rather
     * than the loopback name: on macOS that name resolves to {@code ::1}, which a debugger on
     * {@code 127.0.0.1} cannot reach, and probing it would miss a port that is taken.
     */
    private static synchronized int debugPort() {
        if (resolvedDebugPort > 0) {
            return resolvedDebugPort;
        }
        int preferred = Integer.getInteger("it.debugPort", DEFAULT_DEBUG_PORT);
        for (int port = preferred; port < preferred + 20; port++) {
            try (ServerSocket probe = new ServerSocket(port, 1, InetAddress.getByName(LOOPBACK))) {
                resolvedDebugPort = port;
                return port;
            } catch (IOException taken) {
                // bound by something else; try the next one
            }
        }
        throw new IllegalStateException(
                "no free debug port in " + preferred + ".." + (preferred + 19));
    }

    /** e.g. {@code 26.3.build.159-beta} -> {@code 26.3}. */
    private static String minecraftVersion() {
        return paperVersion().split("\\.build\\.")[0];
    }

    private static String paperVersion() {
        return System.getProperty("it.paperVersion", "26.3.build.159-beta");
    }

    private static String javaBinary() {
        return Path.of(System.getProperty("java.home"), "bin", "java").toString();
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static String tail(Path console) {
        try {
            var lines = Files.readAllLines(console);
            return String.join("\n", lines.subList(Math.max(0, lines.size() - 40), lines.size()));
        } catch (IOException e) {
            return "(no console output: " + e + ")";
        }
    }
}
