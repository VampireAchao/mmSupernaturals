package me.matterz.supernaturals.it.harness;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

/**
 * Creates a real {@code ServerPlayer} with no client behind it, so a scenario can
 * act on the game the way a player would.
 *
 * <p>The calls into {@code net.minecraft.*} and the CraftBukkit implementation
 * classes are reflective on purpose. Those classes live in the server jar, and
 * Paper does not publish one that Maven can depend on (only {@code dev-bundle},
 * which is a zip meant to be unpacked by Gradle), so importing them would tie the
 * test build to a jar every machine has to produce by hand. Reflection keeps
 * {@code mvn test-compile} working with nothing but {@code paper-api} on the
 * classpath.
 *
 * <p>If a future Paper release moves one of these signatures the failure is a
 * single {@link IllegalStateException} naming what is missing, instead of a
 * compile error spread across the test sources.
 */
final class Nms {

    /**
     * The pipeline stage {@code Connection#configureInMemoryPipeline} installs for
     * the outbound direction. Nothing is attached to it, so it has to be replaced
     * (see {@link #newInMemoryConnection}).
     */
    private static final String OUTBOUND_STAGE = "outbound_config";

    private Nms() {
    }

    /** Places a player of that name into the first world, through the normal join path. */
    static Object spawn(String name) {
        try {
            Object bukkit = Bukkit.getServer();
            Object minecraftServer = call(bukkit, "getServer");
            World world = Bukkit.getWorlds().get(0);
            Object serverLevel = call(world, "getHandle");

            Class<?> profileClass = load("com.mojang.authlib.GameProfile");
            Object profile = profileClass
                    .getConstructor(UUID.class, String.class)
                    .newInstance(offlineUuid(name), name);

            Class<?> clientInformationClass = load("net.minecraft.server.level.ClientInformation");
            Object clientInformation = clientInformationClass.getMethod("createDefault").invoke(null);

            Class<?> serverPlayerClass = load("net.minecraft.server.level.ServerPlayer");
            Constructor<?> playerConstructor = serverPlayerClass.getConstructor(
                    load("net.minecraft.server.MinecraftServer"),
                    load("net.minecraft.server.level.ServerLevel"),
                    profileClass,
                    clientInformationClass);
            Object player = playerConstructor.newInstance(
                    minecraftServer, serverLevel, profile, clientInformation);

            Class<?> connectionClass = load("net.minecraft.network.Connection");
            Object connection = newInMemoryConnection(connectionClass);

            Class<?> cookieClass = load("net.minecraft.server.network.CommonListenerCookie");
            Object cookie = cookieClass
                    .getMethod("createInitial", profileClass, boolean.class)
                    .invoke(null, profile, false);

            Object playerList = call(minecraftServer, "getPlayerList");
            playerList.getClass()
                    .getMethod("placeNewPlayer", connectionClass, serverPlayerClass, cookieClass)
                    .invoke(playerList, connection, player, cookie);
            return player;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("could not create a fake player named " + name, e);
        }
    }

    /** Takes a fake player back out, the same way a quitting client would. */
    static void disconnect(Object serverPlayer) {
        try {
            Object playerList = call(call(Bukkit.getServer(), "getServer"), "getPlayerList");
            playerList.getClass()
                    .getMethod("remove", load("net.minecraft.server.level.ServerPlayer"))
                    .invoke(playerList, serverPlayer);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("could not disconnect the fake player", e);
        }
    }

    /**
     * A {@code Connection} with no socket behind it.
     *
     * <p>{@code placeNewPlayer} runs the ordinary join path, which goes through
     * {@code connection.channel} - a bare {@code new Connection(flow)} throws a
     * NullPointerException inside {@code setupInboundProtocol}. The channel field is
     * public, and the server ships {@code configureInMemoryPipeline} for connections
     * that never leave the JVM (the integrated server uses it), so pointing that
     * field at an EmbeddedChannel with that pipeline is the whole trick.
     *
     * <p>The second half matters just as much: that pipeline leaves the outbound side
     * unconfigured, so the first packet the server tries to send this player throws
     * {@code EncoderException: Pipeline has no outbound protocol configured} the
     * moment the chunk map starts tracking them. There is nobody to send anything to,
     * so the outbound stage is replaced with a sink.
     */
    private static Object newInMemoryConnection(Class<?> connectionClass)
            throws ReflectiveOperationException {
        Class<?> packetFlowClass = load("net.minecraft.network.protocol.PacketFlow");
        Object serverbound = enumConstant(packetFlowClass, "SERVERBOUND");

        EmbeddedChannel channel = new EmbeddedChannel();
        connectionClass
                .getMethod("configureInMemoryPipeline", ChannelPipeline.class, packetFlowClass)
                .invoke(null, channel.pipeline(), serverbound);

        if (channel.pipeline().get(OUTBOUND_STAGE) != null) {
            channel.pipeline().replace(OUTBOUND_STAGE, OUTBOUND_STAGE, new DiscardOutbound());
        }

        Object connection = connectionClass.getConstructor(packetFlowClass).newInstance(serverbound);
        connectionClass.getField("channel").set(connection, channel);
        connectionClass.getField("address").set(connection, new InetSocketAddress("127.0.0.1", 0));
        return connection;
    }

    /** Swallows everything the server tries to send a client that is not there. */
    private static final class DiscardOutbound extends ChannelOutboundHandlerAdapter {
        @Override
        public void write(ChannelHandlerContext context, Object message, ChannelPromise promise) {
            ReferenceCountUtil.release(message);
            promise.setSuccess();
        }
    }

    /** The same derivation an offline-mode server uses, so ops.json can pre-authorise. */
    private static UUID offlineUuid(String name) {
        try {
            byte[] digest = MessageDigest.getInstance("MD5")
                    .digest(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
            digest[6] = (byte) ((digest[6] & 0x0F) | 0x30);
            digest[8] = (byte) ((digest[8] & 0x3F) | 0x80);
            long most = 0;
            long least = 0;
            for (int i = 0; i < 8; i++) {
                most = (most << 8) | (digest[i] & 0xFF);
                least = (least << 8) | (digest[i + 8] & 0xFF);
            }
            return new UUID(most, least);
        } catch (Exception e) {
            throw new IllegalStateException("no MD5 available", e);
        }
    }

    private static Class<?> load(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(
                    "the server no longer provides " + name
                            + " - this test harness needs updating for this Paper version", e);
        }
    }

    private static Object call(Object target, String method) throws ReflectiveOperationException {
        Method found = target.getClass().getMethod(method);
        found.setAccessible(true);
        return found.invoke(target);
    }

    private static Object enumConstant(Class<?> type, String name) {
        for (Object constant : type.getEnumConstants()) {
            if (((Enum<?>) constant).name().equals(name)) {
                return constant;
            }
        }
        throw new IllegalStateException(name + " is not a constant of " + type.getName());
    }
}
