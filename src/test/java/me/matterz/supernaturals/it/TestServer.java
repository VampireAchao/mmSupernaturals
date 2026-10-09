package me.matterz.supernaturals.it;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;

/**
 * The scratch Paper server the scenarios run against, and the only thing that starts a
 * server at all - there is no separate run configuration to keep in step with it.
 *
 * <p>It is built under {@code target/it}, on its own ports, from a generated flat world
 * with a fixed seed, so nothing here touches the {@code server/} directory a developer
 * plays on and any two machines produce the same world. Started once per test JVM.
 *
 * <h2>Testing by hand</h2>
 * {@link #main} starts the server and keeps it up, so a real client can join and the
 * plugin can be driven with the harness:
 * <pre>
 *   mvn -Pit test-compile exec:java
 * </pre>
 * The entry point is a main method rather than an IDE run configuration on purpose: it
 * lives in version control, so losing an IDE's settings cannot lose the ability to
 * start a server.
 *
 * <p>Every run starts from a rebuilt server directory. The world, Paper's config files, its
 * ops/whitelist/usercache files, the logs and every plugin's data directory are deleted
 * first, so no scenario can pass on leftovers; only the three cached artifacts that are
 * expensive to obtain again (the downloaded vanilla jar, the patched server jar, and the
 * resolved libraries) survive.
 *
 * <p>Two switches, both off for the scenarios:
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

    /** Where the server jar is expected, relative to the project root. */
    private static final String PAPER_JAR = "server/paper.jar";

    /** Where the built plugin is expected, relative to the project root. */
    private static final String PLUGIN_JAR = "server/plugins/mmSupernaturals.jar";

    public static final int GAME_PORT = 25577;
    public static final int CONTROL_PORT = 25578;

    private static final int DEFAULT_DEBUG_PORT = 5005;
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

    /** The one server for this JVM, started on first use. */
    public static synchronized TestServer shared() {
        if (shared == null) {
            shared = start();
            Runtime.getRuntime().addShutdownHook(new Thread(shared::close, "it-server"));
        }
        return shared;
    }

    /**
     * Starts the server and holds it open for testing by hand: join with a real client on
     * {@link #GAME_PORT}, drive the harness from the console, attach a debugger to the
     * plugin on {@link #DEFAULT_DEBUG_PORT}. Stops with Ctrl+C, saving the world.
     */
    public static void main(String[] args) throws Exception {
        System.setProperty("it.keepServer", "true");
        System.setProperty("it.debug", System.getProperty("it.debug", "true"));

        TestServer server = start();
        Runtime.getRuntime().addShutdownHook(new Thread(server::shutdown, "it-server"));
        server.forwardConsole();

        System.out.println();
        System.out.println("test server is up - Ctrl+C stops it (the world is saved)");
        System.out.println("  join          : localhost:" + GAME_PORT + "   offline mode, any name");
        System.out.println("  server console: type a command here, e.g.  mmp spawn Someone");
        System.out.println("  debugger      : attach a remote JVM debugger to localhost:"
                + Integer.getInteger("it.debugPort", DEFAULT_DEBUG_PORT));
        System.out.println("  files         : " + server.directory());
        System.out.println("  state         : rebuilt on start; -Dit.keepState=true keeps it");
        System.out.println();

        Thread.currentThread().join();
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
        try {
            Files.createDirectories(server.resolve("plugins"));
            reset(server);
            install(project, work, server);
            writeConfiguration(server);

            Process process = new ProcessBuilder(command())
                    .directory(server.toFile())
                    .redirectErrorStream(true)
                    .redirectOutput(console.toFile())
                    .start();

            return new TestServer(server, process, awaitControl(process, console), console);
        } catch (IOException e) {
            throw new IllegalStateException("could not start the test server under " + server, e);
        }
    }

    private static List<String> command() {
        List<String> command = new ArrayList<>(List.of(
                javaBinary(), "-Xms1G", "-Xmx1G", "-Dharness.port=" + CONTROL_PORT));
        if (Boolean.getBoolean("it.debug")) {
            int port = Integer.getInteger("it.debugPort", DEFAULT_DEBUG_PORT);
            command.add("-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:" + port);
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
        Path paper = requirePaper(project);
        Path paperInPlace = server.resolve("paper.jar");
        if (!Files.isRegularFile(paperInPlace) || Files.size(paperInPlace) != Files.size(paper)) {
            Files.copy(paper, paperInPlace, StandardCopyOption.REPLACE_EXISTING);
        }

        Files.copy(requirePluginJar(project), server.resolve("plugins/mmSupernaturals.jar"),
                StandardCopyOption.REPLACE_EXISTING);
        Files.copy(buildHarnessJar(project, work), server.resolve("plugins/mmHarness.jar"),
                StandardCopyOption.REPLACE_EXISTING);
    }

    /** An existing server jar, or a freshly downloaded one; a message with the fix if neither works. */
    private static Path requirePaper(Path project) {
        Path jar = project.resolve(PAPER_JAR);
        if (Files.isRegularFile(jar)) {
            return jar;
        }
        try {
            return downloadPaper(jar);
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
     * Fetches the newest build of the pinned Minecraft version. Only reached when there is
     * no jar to use, so a checkout with one already in place never touches the network.
     */
    private static Path downloadPaper(Path target) throws IOException, InterruptedException {
        System.out.println("paper.jar is missing, downloading Paper " + minecraftVersion() + " ...");
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
            return target;
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

    /** The plugin under test, built by {@code mvn package} into the local server's folder. */
    private static Path requirePluginJar(Path project) {
        Path jar = project.resolve(PLUGIN_JAR);
        if (!Files.isRegularFile(jar)) {
            throw new IllegalStateException(fixThis(
                    "The plugin under test has not been built.",
                    jar,
                    "run `mvn package` first, it writes the jar here",
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

    /** A flat world with a fixed seed, so terrain is identical on every machine. */
    private static void writeConfiguration(Path server) throws IOException {
        String flat = "{\"layers\":[{\"block\":\"minecraft:bedrock\",\"height\":1},"
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
                "level-name=world",
                "level-type=minecraft:flat",
                "level-seed=mmSupernaturals",
                "generator-settings=" + flat,
                "gamemode=survival",
                "difficulty=normal",
                "allow-nether=false",
                "max-players=20",
                "view-distance=6",
                "simulation-distance=4",
                "enable-command-block=true") + "\n");
    }

    private static ControlClient awaitControl(Process process, Path console) throws IOException {
        long deadline = System.nanoTime() + STARTUP_TIMEOUT.toNanos();
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
                + STARTUP_TIMEOUT + ":\n" + tail(console));
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
