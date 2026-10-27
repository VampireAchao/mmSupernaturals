package me.matterz.supernaturals.it.harness;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Serves the hall's skins from this machine, on a port picked at startup.
 *
 * <p>A profile's skin is a {@code textures} property holding a URL, and the client is what fetches
 * it - so a skin this project draws has to be reachable over HTTP or nobody sees it. That is all
 * this is: a loopback HTTP server over the handful of PNGs in {@link SkinArt}, up for as long as
 * the harness is.
 *
 * <p>Loopback on purpose. The hall runs on a developer's machine and so does the client looking at
 * it; binding a public port would advertise a test fixture to the network for no gain.
 *
 * <p>Nothing here is signed. A signature is Mojang vouching for a texture it hosts, which is
 * exactly what this design stopped needing - the hall sets {@code enforce-secure-profile=false},
 * which is what an unsigned, self-hosted texture needs instead.
 */
final class SkinServer {

    private static final Map<String, byte[]> DRAWN = new ConcurrentHashMap<>();

    private static HttpServer server;
    private static int port = -1;

    private SkinServer() {
    }

    /**
     * The URL of that skin, starting the server if this is the first one asked for.
     *
     * <p>{@code null} when there is no server to serve it - the caller reads that as "this
     * stand-in wears the default skin", which is a duller hall and not a broken one.
     */
    static synchronized String url(String key, int base, int accent) {
        start();
        if (server == null) {
            return null;
        }
        String name = key.toLowerCase(Locale.ROOT);
        DRAWN.computeIfAbsent(name, ignored -> SkinArt.png(base, accent));
        return "http://127.0.0.1:" + port + "/" + name + ".png";
    }

    /** The port it ended up on, or -1 before it has started. */
    static synchronized int port() {
        return port;
    }

    static synchronized void start() {
        if (server != null) {
            return;
        }
        HttpServer started;
        try {
            started = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
            started.createContext("/", SkinServer::serve);
            started.start();
        } catch (IOException e) {
            // A skipped skin is better than a hall that will not start: the profiles go out
            // without a texture and the client draws its own default, which is what a checkout
            // with no skins at all used to do.
            Logger.getLogger("mmSupernaturals").warning(
                    "cannot serve the hall's skins (" + e + "), so every stand-in keeps the default skin");
            return;
        }
        server = started;
        port = started.getAddress().getPort();
        Logger.getLogger("mmSupernaturals").info("the hall's skins are drawn here, and served from "
                + "http://127.0.0.1:" + port + "/");
    }

    static synchronized void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
            port = -1;
            DRAWN.clear();
        }
    }

    private static void serve(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        byte[] png = path.endsWith(".png")
                ? DRAWN.get(path.substring(1, path.length() - ".png".length()))
                : null;
        if (png == null) {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
            return;
        }
        exchange.getResponseHeaders().add("Content-Type", "image/png");
        exchange.sendResponseHeaders(200, png.length);
        try (OutputStream body = exchange.getResponseBody()) {
            body.write(png);
        }
    }
}
