package me.matterz.supernaturals.it;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;

/**
 * The scratch Paper server the scenarios run against.
 *
 * <p>It is built under {@code target/it}, on its own port, from a generated flat world
 * and a fixed seed: nothing here touches the {@code server/} directory a developer uses
 * to play on, and any two machines produce the same world. Started once per test
 * JVM and stopped when it exits.
 *
 * <p>The server this class starts is a fixture, not a thing you attach to. If you want
 * to watch a scenario while driving a client yourself, run the scenario and keep the
 * client pointed at 25577.
 */
public final class TestServer implements AutoCloseable {

    public static final int GAME_PORT = 25577;
    public static final int CONTROL_PORT = 25578;

    private static final Duration STARTUP_TIMEOUT = Duration.ofMinutes(3);
    private static final String HARNESS_CLASSES = "me/matterz/supernaturals/it/harness";

    private static TestServer shared;

    /** The one server for this JVM, started on first use. */
    public static synchronized TestServer shared() {
        if (shared == null) {
            shared = start();
            Runtime.getRuntime().addShutdownHook(new Thread(shared::close, "it-server"));
        }
        return shared;
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
        try {
            Files.createDirectories(server.resolve("plugins"));
            if (!Boolean.getBoolean("it.keepWorld")) {
                // Both the world and the plugin's own files go: a scenario has to start
                // from defaults, or the second run tests the leftovers of the first.
                deleteRecursively(server.resolve("world"));
                deleteRecursively(server.resolve("plugins/mmSupernaturals"));
            }
            install(project, work, server);
            writeConfiguration(server);

            Path console = server.resolve("console.log");
            Process process = new ProcessBuilder(
                    javaBinary(), "-Xms1G", "-Xmx1G",
                    "-Dharness.port=" + CONTROL_PORT,
                    "-jar", "paper.jar", "--nogui")
                    .directory(server.toFile())
                    .redirectErrorStream(true)
                    .redirectOutput(console.toFile())
                    .start();

            return new TestServer(server, process, awaitControl(process, console), console);
        } catch (IOException e) {
            throw new IllegalStateException("could not start the test server under " + server, e);
        }
    }

    private static void install(Path project, Path work, Path server) throws IOException {
        Path paper = resolvePaper(project, work);
        Path paperInPlace = server.resolve("paper.jar");
        if (!Files.isRegularFile(paperInPlace) || Files.size(paperInPlace) != Files.size(paper)) {
            Files.copy(paper, paperInPlace, StandardCopyOption.REPLACE_EXISTING);
        }

        Path plugin = project.resolve("server/plugins/mmSupernaturals.jar");
        if (!Files.isRegularFile(plugin)) {
            throw new IllegalStateException(plugin + " is missing - run 'mvn package' first");
        }
        Files.copy(plugin, server.resolve("plugins/mmSupernaturals.jar"),
                StandardCopyOption.REPLACE_EXISTING);

        Files.copy(buildHarnessJar(project, work), server.resolve("plugins/mmHarness.jar"),
                StandardCopyOption.REPLACE_EXISTING);
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
            throw new IllegalStateException("run 'mvn test-compile' first");
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

    /** The developer's own server jar if there is one, otherwise the newest build of the pinned version. */
    private static Path resolvePaper(Path project, Path work) throws IOException {
        Path devServer = project.resolve("server/paper.jar");
        if (Files.isRegularFile(devServer)) {
            return devServer;
        }
        Path cache = work.resolve("cache");
        Files.createDirectories(cache);
        String requested = System.getProperty("it.paperVersion", "26.3.build.159-beta");
        String minecraftVersion = requested.split("\\.build\\.")[0];
        try (HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build()) {
            String builds = "https://fill.papermc.io/v3/projects/paper/versions/" + minecraftVersion + "/builds";
            JsonArray releases = JsonParser.parseString(get(http, builds)).getAsJsonArray();
            JsonObject download = releases.get(0).getAsJsonObject()
                    .getAsJsonObject("downloads")
                    .getAsJsonObject("server:default");
            Path target = cache.resolve(download.get("name").getAsString());
            if (!Files.isRegularFile(target)) {
                HttpResponse<Path> response = http.send(
                        HttpRequest.newBuilder(URI.create(download.get("url").getAsString()))
                                .header("User-Agent", "mmSupernaturals-it").build(),
                        HttpResponse.BodyHandlers.ofFile(target));
                if (response.statusCode() != 200) {
                    throw new IOException("downloading " + download.get("name")
                            + " answered HTTP " + response.statusCode());
                }
            }
            return target;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted while downloading the server jar", e);
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

    @Override
    public synchronized void close() {
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
