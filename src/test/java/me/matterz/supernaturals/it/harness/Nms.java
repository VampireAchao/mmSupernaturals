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
import java.lang.reflect.Field;
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
        return spawn(name, Bukkit.getWorlds().get(0));
    }

    /**
     * Places a player of that name into one particular world.
     *
     * <p>The world is worth choosing. The server leaves a clientless player invulnerable after
     * it moves them between worlds - it is waiting for a client to acknowledge a dimension
     * change that never arrives - so a stand-in that is going to be hit has to be created in the
     * world the hitting happens in, not wherever the first world is.
     */
    static Object spawn(String name, World world) {
        return spawn(name, world, null);
    }

    /** The same, wearing one of {@link Skins}'s textures - or nothing, for a default skin. */
    static Object spawn(String name, World world, Skins.Skin skin) {
        try {
            Object bukkit = Bukkit.getServer();
            Object minecraftServer = call(bukkit, "getServer");
            Object serverLevel = call(world, "getHandle");

            Class<?> profileClass = load("com.mojang.authlib.GameProfile");
            Object profile = profileClass
                    .getConstructor(UUID.class, String.class)
                    .newInstance(offlineUuid(name), name);
            wear(profile, skin);

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

    /**
     * Puts a skin on a profile, before the profile is sent anywhere.
     *
     * <p>A player's skin is one property of their profile: "textures", a base64 blob naming a
     * texture on Mojang's own texture server. A profile made by hand has no properties at all,
     * which is why every stand-in in the hall has always drawn as the default Steve or Alex
     * whatever race the room was about. It has to be on the profile <em>before</em>
     * {@code placeNewPlayer} runs - the profile goes out with the player-info packet that call
     * sends, and nothing sends it a second time.
     */
    private static void wear(Object profile, Skins.Skin skin) throws ReflectiveOperationException {
        if (skin == null) {
            return;
        }
        Object properties = profile.getClass().getMethod("getProperties").invoke(profile);
        Class<?> propertyClass = load("com.mojang.authlib.properties.Property");
        Object property = propertyClass
                .getConstructor(String.class, String.class, String.class)
                .newInstance("textures", skin.value(), skin.signature());
        for (Method candidate : properties.getClass().getMethods()) {
            if (candidate.getName().equals("put") && candidate.getParameterCount() == 2) {
                candidate.invoke(properties, "textures", property);
                return;
            }
        }
        throw new IllegalStateException("the profile's properties cannot take a texture");
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

    /**
     * Whether the biome at one spot can have weather at all.
     *
     * <p>Rain needs three things to be seen and a section can only set one of them. The world's
     * storm flag is the one a switch sets; the column has to have nothing solid over it; and the
     * <b>biome</b> has to precipitate, which is what the client is told and what it draws the rain
     * from. In a biome whose precipitation is {@code none} the storm flag changes the plugin's
     * world and nothing on anybody's screen - so a switch that looks dead and a rule that has
     * quietly changed can be the same event. This is the third fact, which nothing else on the
     * server will say out loud.
     */
    static String precipitation(org.bukkit.World world, int x, int z) {
        String biome = String.valueOf(world.getBiome(x, 0, z));
        try {
            Object level = call(world, "getHandle");
            Class<?> posClass = load("net.minecraft.core.BlockPos");
            Object pos = posClass.getConstructor(int.class, int.class, int.class)
                    .newInstance(x, 0, z);
            Object holder = null;
            for (Method candidate : level.getClass().getMethods()) {
                if (!candidate.getName().equals("getBiome") || candidate.getParameterCount() != 1) {
                    continue;
                }
                if (!candidate.getParameterTypes()[0].getName().equals(posClass.getName())) {
                    continue;
                }
                holder = candidate.invoke(level, pos);
                break;
            }
            if (holder == null) {
                return "precipitates=? (no getBiome(BlockPos) on this build) biome=" + biome;
            }
            Object nmsBiome = call(holder, "value");
            for (Method candidate : nmsBiome.getClass().getMethods()) {
                if (candidate.getName().equals("hasPrecipitation")
                        && candidate.getParameterCount() == 0) {
                    return "precipitates=" + candidate.invoke(nmsBiome) + " biome=" + biome;
                }
            }
            return "precipitates=? (no hasPrecipitation on this build) biome=" + biome;
        } catch (ReflectiveOperationException e) {
            return "precipitates=? (" + e.getClass().getSimpleName() + ") biome=" + biome;
        }
    }

    /**
     * Takes the "you have not arrived yet" state off a player.
     *
     * <p>A player whose client has not confirmed the position it was put at is treated as still
     * in transit, and a player in transit cannot be hurt - which is why every step that asks
     * somebody to hit a stand-in did nothing in any world but the one the server logs players
     * into. A stand-in has no client, so nothing ever confirms anything and the state stays on it
     * for good. The fields are cleared by name and each is optional: the ones that do not exist
     * on a build are the ones whose problem that build does not have.
     */
    static void arrive(Object serverPlayer) {
        Object connection = null;
        try {
            connection = call(serverPlayer, "connection");
        } catch (ReflectiveOperationException ignored) {
            // No connection to clear; the fields below are still worth clearing.
        }
        // The name matters and every obvious guess is wrong: on this build the flag that makes a
        // player untouchable is the one that says it is still changing dimension, and it is called
        // isChangingDimension. A stand-in is put straight into the world it belongs in and never
        // changes anything, so nothing ever clears it - and a player mid-change cannot be hurt.
        plant(serverPlayer, "isChangingDimension", false);
        plant(serverPlayer, "changingDimension", false);
        plant(serverPlayer, "permanentlyInvulnerable", false);
        plant(serverPlayer, "invulnerable", false);
        Object abilities = fieldOf(serverPlayer, "abilities");
        if (abilities != null) {
            plant(abilities, "invulnerable", false);
        }
        if (connection != null) {
            plant(connection, "awaitingPositionFromClient", null);
            plant(connection, "awaitingTeleportTime", 0);
        }
    }

    /** The value of a field by name, wherever along the class chain it is declared. */
    private static Object fieldOf(Object target, String field) {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field found = type.getDeclaredField(field);
                found.setAccessible(true);
                return found.get(target);
            } catch (NoSuchFieldException | IllegalAccessException ignored) {
                // Declared further up, or not at all.
            }
        }
        return null;
    }

    /** The same, for a player the harness is holding as a {@code CraftPlayer}. */
    static void arrive(org.bukkit.entity.Player player) {
        try {
            arrive(call(player, "getHandle"));
        } catch (ReflectiveOperationException ignored) {
            // CraftPlayer always has getHandle; if it somehow does not, there is nothing to do.
        }
    }

    /**
     * Tells everybody watching a stand-in what he is holding.
     *
     * <p>Nothing else does. The server ticks the connections it accepted; a connection ticks its
     * packet listener; the listener is the only caller of {@code ServerPlayer#doTick}; that is
     * the only caller of the ordinary entity tick; and the ordinary entity tick is where a living
     * entity compares what it holds against what each watcher was last told and sends the
     * difference. A connection made by hand is not in the list the server ticks, so none of that
     * runs - which leaves a stand-in's equipment reaching a client exactly once, at the moment
     * that client starts tracking him, and never again. Every step that hands him a weapon after
     * he has appeared therefore looked, on screen, like a step that handed him nothing: the
     * tester saw whatever he happened to be holding when he first turned up.
     *
     * <p>So the one step that matters is run by hand: the game's own check, which sends only what
     * has changed. It is not a re-implementation - an item is put in his hand through the
     * ordinary inventory, and this only makes the news travel.
     *
     * @return what he holds now, what the watchers had been told before this call, and what they
     *         are told after it - so a console can see what a client would
     */
    static String pushEquipment(org.bukkit.entity.Player player) {
        String hand = player.getInventory().getItemInMainHand().getType().toString();
        try {
            Object handle = call(player, "getHandle");
            String before = toldHeld(handle);
            Method push = handle.getClass().getMethod("detectEquipmentUpdates");
            push.setAccessible(true);
            push.invoke(handle);
            return "hand=" + hand + " toldBefore=" + before + " toldAfter=" + toldHeld(handle);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return "hand=" + hand + " push failed (" + e + ")";
        }
    }

    /** The main-hand item the watchers were last told about, named the way a player says it. */
    private static String toldHeld(Object handle) {
        Object sent = fieldOf(handle, "lastEquipmentItems");
        if (!(sent instanceof java.util.Map<?, ?> slots)) {
            return "?";
        }
        for (java.util.Map.Entry<?, ?> slot : slots.entrySet()) {
            if (slot.getKey() != null && "MAINHAND".equals(slot.getKey().toString())) {
                return asName(slot.getValue());
            }
        }
        return "?";
    }

    /** A server-side item stack, named through the ordinary API. */
    private static String asName(Object stack) {
        if (stack == null) {
            return "?";
        }
        try {
            Class<?> craft = Class.forName("org.bukkit.craftbukkit.inventory.CraftItemStack");
            Method asBukkit = craft.getMethod("asBukkitCopy", load("net.minecraft.world.item.ItemStack"));
            return String.valueOf(((org.bukkit.inventory.ItemStack) asBukkit.invoke(null, stack)).getType());
        } catch (ReflectiveOperationException | RuntimeException e) {
            return String.valueOf(stack);
        }
    }

    /**
     * Sets a field by name, wherever along the class chain it is declared.
     *
     * <p>Every name here is a guess about a server build that publishes no API for any of this,
     * so a name that is not found is not an error - it is a field this build does not have.
     */
    private static void plant(Object target, String field, Object value) {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field found = type.getDeclaredField(field);
                found.setAccessible(true);
                found.set(target, value);
                return;
            } catch (NoSuchFieldException | IllegalAccessException ignored) {
                // Declared further up, or not at all.
            }
        }
    }

    /**
     * Every field that could be holding a player in transit, with its value.
     *
     * <p>{@code setInvulnerable(false)} is not enough on this build - the flag reads back true -
     * which means the answer is a field nobody publishes, and the only way to find it is to look.
     */
    static String transit(Object bukkitPlayer) {
        // The NMS object, not the Craft one in front of it: the fields are all declared on the
        // server side, and scanning the Bukkit wrapper's own class chain finds nothing at all.
        Object player = bukkitPlayer;
        try {
            player = call(bukkitPlayer, "getHandle");
        } catch (ReflectiveOperationException ignored) {
            // Already a handle, or has none; scan what came in either way.
        }
        StringBuilder out = new StringBuilder();
        Object connection = null;
        try {
            connection = call(player, "connection");
        } catch (ReflectiveOperationException ignored) {
            // No connection on this build.
        }
        scan(player, out);
        Object abilities = fieldOf(player, "abilities");
        if (abilities != null) {
            scan(abilities, out);
        }
        if (connection != null) {
            scan(connection, out);
        }
        return out.length() == 0 ? "(nothing suspicious)" : out.toString();
    }

    private static void scan(Object target, StringBuilder out) {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                String name = field.getName().toLowerCase(java.util.Locale.ROOT);
                if (!name.contains("invuln") && !name.contains("permanent") && !name.contains("changing")
                        && !name.contains("dimension") && !name.contains("awaiting")
                        && !name.contains("confirm")) {
                    continue;
                }
                field.setAccessible(true);
                try {
                    out.append(type.getSimpleName()).append('.').append(field.getName())
                            .append('=').append(field.get(target)).append("  ");
                } catch (IllegalAccessException | RuntimeException ignored) {
                    out.append(type.getSimpleName()).append('.').append(field.getName())
                            .append("=(unreadable)  ");
                }
            }
        }
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
