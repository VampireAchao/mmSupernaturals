package me.matterz.supernaturals.it;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Talks to the harness control channel: one request per line, one response per line.
 * A response starting with {@code err} is turned into an exception, so a scenario
 * cannot silently keep going after the server refused to do something.
 */
public final class ControlClient implements AutoCloseable {

    private static final int RESPONSE_TIMEOUT_MILLIS = 60_000;

    private final Socket socket;
    private final BufferedReader in;
    private final PrintWriter out;

    public ControlClient(int port, int connectTimeoutMillis) throws IOException {
        this.socket = new Socket();
        this.socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), connectTimeoutMillis);
        this.socket.setTcpNoDelay(true);
        this.socket.setSoTimeout(RESPONSE_TIMEOUT_MILLIS);
        this.in = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        this.out = new PrintWriter(
                new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
    }

    /** Sends one request and returns the payload of an {@code ok} response. */
    public String call(String request) {
        try {
            out.println(request);
            String response = in.readLine();
            if (response == null) {
                throw new IllegalStateException("the control channel closed while running: " + request);
            }
            if (!response.startsWith("ok ")) {
                throw new IllegalStateException("the server refused '" + request + "': " + response);
            }
            return response.substring(3);
        } catch (IOException e) {
            throw new IllegalStateException("the control channel failed on '" + request + "'", e);
        }
    }

    @Override
    public void close() {
        try {
            socket.close();
        } catch (IOException ignored) {
            // nothing useful to do while tearing down
        }
    }
}
