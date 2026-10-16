package me.matterz.supernaturals.it.harness;

import io.netty.channel.Channel;
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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
            Object profile = profile(name, skin);

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
            Object connection = newInMemoryConnection(connectionClass, name);

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
     * A profile of that name, wearing that skin.
     *
     * <p>There is no way to put a skin on a profile afterwards, and that is the whole of it.
     * {@code PropertyMap} is not a mutable multimap: its constructor copies whatever it is
     * handed into an {@code ImmutableMultimap}. Nor is the profile's own map any better - the
     * two-argument {@code GameProfile} constructor fills the field with {@code PropertyMap.EMPTY},
     * which is one of those. A put on either throws; a caller that swallows the throw (as this
     * one did, for a while) ends up with a profile that is simply wearing nothing, and no
     * complaint anywhere. So the texture goes into the map first and the map is frozen second,
     * because there is no third order to try.
     */
    private static Object profile(String name, Skins.Skin skin) throws ReflectiveOperationException {
        Class<?> profileClass = load("com.mojang.authlib.GameProfile");
        Object properties = propertiesWearing(skin);
        return properties == null
                ? profileClass.getConstructor(UUID.class, String.class)
                        .newInstance(offlineUuid(name), name)
                : profileClass
                        .getConstructor(UUID.class, String.class,
                                load("com.mojang.authlib.properties.PropertyMap"))
                        .newInstance(offlineUuid(name), name, properties);
    }

    /**
     * The property map of a profile that is wearing that skin, or {@code null} when this build
     * will not hand one over.
     *
     * <p>Deliberately best-effort. A skin is decoration; a stand-in is not. Throwing here would
     * turn "this server builds its profiles differently" into "the section that needed somebody
     * to hit has nobody", and the second is much worse than a plain face. The warning is what
     * makes a quiet failure visible on a hand-run server; {@code mmp skin <name>} is how to ask
     * on purpose.
     */
    private static Object propertiesWearing(Skins.Skin skin) {
        try {
            Object multimap = load("com.google.common.collect.HashMultimap")
                    .getMethod("create")
                    .invoke(null);
            if (skin != null) {
                Class<?> propertyClass = load("com.mojang.authlib.properties.Property");
                // A texture with no signature is one a client may refuse; the hall sets
                // enforce-secure-profile=false, which is what makes one usable - so the
                // three-argument form is only asked for when there is a signature to give it.
                Object property = skin.signature() == null
                        ? propertyClass.getConstructor(String.class, String.class)
                                .newInstance("textures", skin.value())
                        : propertyClass.getConstructor(String.class, String.class, String.class)
                                .newInstance("textures", skin.value(), skin.signature());
                put(multimap, "textures", property);
            }
            return load("com.mojang.authlib.properties.PropertyMap")
                    .getConstructor(load("com.google.common.collect.Multimap"))
                    .newInstance(multimap);
        } catch (ReflectiveOperationException | RuntimeException e) {
            Bukkit.getLogger().warning("the stand-in keeps the default skin: " + why(e));
            return null;
        }
    }

    /** Puts one entry into a multimap, under whichever {@code put} this build of Guava offers. */
    private static void put(Object multimap, String key, Object value)
            throws ReflectiveOperationException {
        for (Method candidate : multimap.getClass().getMethods()) {
            if (candidate.getName().equals("put") && candidate.getParameterCount() == 2) {
                candidate.invoke(multimap, key, value);
                return;
            }
        }
        throw new NoSuchMethodException("no put on " + multimap.getClass().getName());
    }

    /**
     * What a reflection failure actually complained about.
     *
     * <p>{@code Method#invoke} wraps whatever the method threw, and the wrapper on its own says
     * nothing useful. That is not a small thing here: the warning that first reported this one
     * read "InvocationTargetException" and nothing else, and the answer - an immutable map being
     * written to - was two levels down.
     */
    private static String why(Throwable failure) {
        Throwable real = failure instanceof InvocationTargetException && failure.getCause() != null
                ? failure.getCause()
                : failure;
        return real.getClass().getName()
                + (real.getMessage() == null ? "" : ": " + real.getMessage());
    }

    /**
     * What a player's profile says he is wearing, read back off the server.
     *
     * <p>A skin is a property of the profile and nothing on the server prints it, so "did the
     * skin get there" has no other answer than this: whether the property is on him at all, and
     * what the blob it carries decodes to. A player without one is a player the client will draw
     * as the default, which is the thing worth being able to see rather than assume.
     */
    static String skin(org.bukkit.entity.Player player) {
        try {
            Object handle = call(player, "getHandle");
            Object profile = call(handle, "getGameProfile");
            Object worn = textureOf(propertyMap(profile));
            if (worn == null) {
                return player.getName() + " textures=no (the client draws the default)";
            }
            String value = String.valueOf(read(worn, "value"));
            String decoded = new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
            return player.getName()
                    + " textures=yes signature=" + (read(worn, "signature") == null ? "no" : "yes")
                    + " model=" + firstMatch(decoded, "\"model\"\\s*:\\s*\"([a-z]+)\"")
                    + " url=" + firstMatch(decoded, "\"url\"\\s*:\\s*\"([^\"]+)\"")
                    + " value=" + value.substring(0, Math.min(24, value.length())) + "...";
        } catch (ReflectiveOperationException | IllegalArgumentException e) {
            return player.getName() + " textures=? (" + e + ")";
        }
    }

    /**
     * A profile's property map, under whichever name this build of the auth library gives it.
     *
     * <p>The library turned {@code GameProfile} into a record between the version this was first
     * written against and the one Paper ships now: {@code getProperties()} became
     * {@code properties()}, and the old name is not merely deprecated but gone. Both are tried,
     * because a harness that breaks on the spelling of a getter is a harness that breaks.
     */
    private static Object propertyMap(Object profile) throws ReflectiveOperationException {
        for (String name : new String[] {"properties", "getProperties"}) {
            for (Method candidate : profile.getClass().getMethods()) {
                if (candidate.getName().equals(name) && candidate.getParameterCount() == 0) {
                    return candidate.invoke(profile);
                }
            }
        }
        throw new NoSuchMethodException("no properties accessor on " + profile.getClass().getName());
    }

    /** The first "textures" property in a property map, or {@code null} when there is none. */
    private static Object textureOf(Object properties) {
        for (Method candidate : properties.getClass().getMethods()) {
            if (!candidate.getName().equals("get") || candidate.getParameterCount() != 1) {
                continue;
            }
            try {
                if (candidate.invoke(properties, "textures") instanceof Collection<?> found
                        && !found.isEmpty()) {
                    return found.iterator().next();
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // A get that takes something other than a name - the next candidate is the one.
            }
        }
        return null;
    }

    /** A property's value or signature, under either the record name or the bean one. */
    private static Object read(Object property, String name) {
        String bean = "get" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
        for (String candidate : new String[] {name, bean}) {
            try {
                return call(property, candidate);
            } catch (ReflectiveOperationException ignored) {
                // The other spelling, then.
            }
        }
        return null;
    }

    /** The first group of a pattern in some text, or {@code -} when it is not there. */
    private static String firstMatch(String text, String pattern) {
        Matcher found = Pattern.compile(pattern).matcher(text);
        return found.find() ? found.group(1) : "-";
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
    private static Object newInMemoryConnection(Class<?> connectionClass, String name)
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
        WATCHED.put(channel, name);
        return connection;
    }

    /**
     * Swallows everything the server tries to send a client that is not there.
     *
     * <p>It reads the chat out of them on the way past first - see {@link #said}. Everything else
     * is released and forgotten exactly as before.
     */
    private static final class DiscardOutbound extends ChannelOutboundHandlerAdapter {
        @Override
        public void write(ChannelHandlerContext context, Object message, ChannelPromise promise) {
            remember(context.channel(), message);
            ReferenceCountUtil.release(message);
            promise.setSuccess();
        }
    }

    /** How many of a fake client's messages are kept, so a chatty step cannot fill memory. */
    private static final int SAID_KEPT = 40;

    /** How much of a printed packet is kept: enough for a sentence, not for a whole inventory. */
    private static final int SAID_LONGEST = 1000;

    /**
     * The last few things the server said to each fake client, by name.
     *
     * <p>{@code player.sendMessage} ends in exactly one packet, and every message a plugin sends a
     * player goes through it - which made chat the one part of a plugin's behaviour no scenario
     * could see, since there is nobody to see it. The packet is in the handler on its way out
     * regardless, so its text is kept rather than dropped: a few dozen strings per stand-in, and
     * "the spell announces where it sent him" stops being a claim.
     *
     * <p>Kept for as long as the server runs and never cleared, and only the last {@link
     * #SAID_KEPT} of them per name: a scenario that asserts on a sentence has to read the tail it
     * just caused, not the whole list, because the name it is reading may have been used by an
     * earlier step as well. Nothing clears it on disconnect for the same reason it is not a
     * scenario's business: what a name was told is history, and the count that comes back from
     * {@link #said} says how much of it there is.
     */
    private static final Map<String, Deque<String>> SAID = new ConcurrentHashMap<>();

    /** Which fake client a channel belongs to. */
    private static final Map<Channel, String> WATCHED = new ConcurrentHashMap<>();

    private static void remember(Channel channel, Object packet) {
        String who = WATCHED.get(channel);
        if (who == null) {
            return;
        }
        String text = chatOf(packet);
        if (text == null || text.isEmpty()) {
            return;
        }
        Deque<String> said = SAID.computeIfAbsent(who, name -> new ArrayDeque<>());
        said.addLast(text);
        while (said.size() > SAID_KEPT) {
            said.removeFirst();
        }
    }

    /**
     * The text of a system-chat packet, or {@code null} for anything else.
     *
     * <p>Recognised by name rather than by type, like everything else in this file: the packet
     * classes live in the server jar, which this file cannot see. A record prints its own parts, so
     * the sentence the server was sending is in the printed form, which is all a scenario needs to
     * search for a phrase in. A packet longer than {@link #SAID_LONGEST} is cut off at that point -
     * the printed form carries the component's own formatting around the text, so the cap is a
     * guard on a pathological message rather than a limit a sentence meets.
     */
    private static String chatOf(Object packet) {
        if (!packet.getClass().getSimpleName().contains("SystemChat")) {
            return null;
        }
        String printed = String.valueOf(packet);
        return printed.length() > SAID_LONGEST ? printed.substring(0, SAID_LONGEST) : printed;
    }

    /** What the server has said to one fake client, oldest first. */
    static List<String> said(String name) {
        Deque<String> said = SAID.get(name);
        return said == null ? List.of() : List.copyOf(said);
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

    /**
     * Lands one blow on something that is not alive - a boat, in practice.
     *
     * <p>There is no Bukkit call for this. The boats this server builds are {@code Vehicle} and
     * nothing else: the interface that carries {@code damage(double)} is {@code Damageable}, and a
     * boat is not one, so a punch cannot be asked for through the API at all. What a punch does
     * underneath is hand the boat to the server's own damage entry point, and that is what this
     * does - the same route an arrow or a collision takes, so the events fire exactly as they do
     * when a player swings at one.
     */
    static void hit(org.bukkit.entity.Entity entity, double amount) {
        try {
            Object handle = call(entity, "getHandle");
            Object source = call(call(handle, "damageSources"), "generic");
            handle.getClass()
                    .getMethod("hurt", load("net.minecraft.world.damagesource.DamageSource"),
                            float.class)
                    .invoke(handle, source, (float) amount);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("could not hit " + entity, e);
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
     *
     * <p>There are two gates that read that state, and both are opened below: one wants to know
     * that the player has finished arriving, the other that its client has finished loading. A
     * stand-in fails both forever, for the same reason.
     */
    static void arrive(Object serverPlayer) {
        Object connection = connectionOf(serverPlayer);
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
            // The load window, which is the second half of the same gate and was the half nobody
            // had looked at. `ServerPlayer#isInvulnerableTo` refuses a blow outright while
            // `connection.hasClientLoaded()` is false, and that answers "not yet" while
            // `clientLoadedTimeoutTimer` is still counting down (60 ticks, set when a player is
            // placed) or `waitingForRespawn` is set. No client means nobody ever says "loaded", so
            // a stand-in spends his first three seconds untouchable - and a room that asks one
            // stand-in to hit another hits him in the tick he was spawned, which lands here
            // instead of on him. This is read at the moment of the blow, not at spawn, so it has
            // to be cleared here rather than once at birth.
            plant(connection, "waitingForRespawn", false);
            plant(connection, "clientLoadedTimeoutTimer", 0);
        }
    }

    /**
     * A server player's packet listener, which is where the rest of the "not yet arrived" state
     * lives.
     *
     * <p>It needs its own method because the obvious way to ask is wrong. The field is called
     * {@code connection}, no method is, and a lookup that only knows how to call methods comes
     * back empty and - worse - silent. Everything on the listener was therefore cleared nowhere,
     * for as long as that looked like a miss instead of an answer.
     */
    private static Object connectionOf(Object serverPlayer) {
        Object connection = fieldOf(serverPlayer, "connection");
        if (connection != null) {
            return connection;
        }
        try {
            return call(serverPlayer, "getConnection");
        } catch (ReflectiveOperationException ignored) {
            return null;
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
        Object connection = connectionOf(player);
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
