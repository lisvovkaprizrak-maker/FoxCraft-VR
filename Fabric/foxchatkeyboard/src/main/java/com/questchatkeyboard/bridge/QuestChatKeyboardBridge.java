package com.questchatkeyboard.bridge;

import net.fabricmc.api.ClientModInitializer;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

/**
 * Java/Fabric half of QuestChatKeyboard. Requires a separately integrated Unity
 * component to display Meta's system keyboard. No user chat text is logged.
 * Uses a reflection-based Fabric tick callback, matching QuestCraft 6.0.0.
 */
public final class QuestChatKeyboardBridge implements ClientModInitializer {
    private static final String VERSION = "0.6.0";
    private static final String MAGIC = "QCKB1";
    private static final int RESPONSE_LIMIT_BYTES = 8192;

    private static Path directory;
    private static Path status;
    private static Field screenField;
    private static boolean prevOpen;
    private static boolean tickSeen;
    private static boolean failedScreen;
    private static String session = "";
    private static int tick;

    @Override public void onInitializeClient() {
        try {
            Path gameDir = locateGameDirectory();
            Path config = gameDir.resolve("config");
            directory = config.resolve("questchatkeyboard-bridge");
            Files.createDirectories(directory);
            Files.deleteIfExists(directory.resolve("request.txt"));
            Files.deleteIfExists(directory.resolve("response.txt"));
            status = config.resolve("questchatkeyboard-bridge-status.txt");
            Files.writeString(status, "QuestChatKeyboard Bridge v" + VERSION + "\n" +
                    "Unity keyboard component must be integrated into the QuestCraft APK.\n" +
                    "No input text is logged; responses match chat-session IDs.\n" +
                    "Bridge directory: " + directory + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Throwable e) {
            System.err.println("[QuestChatKeyboard] STARTUP_FAILED " + e.getClass().getSimpleName());
            return;
        }
        log("BRIDGE_INITIALIZED v" + VERSION);
        try {
            attachFabricTick();
            log("END_CLIENT_TICK_REGISTERED");
        } catch (Throwable e) {
            log("END_CLIENT_TICK_REGISTRATION_FAILED " + e.getClass().getName());
            e.printStackTrace(System.err);
        }
    }

    private static Path locateGameDirectory() throws Exception {
        Class<?> loader = Class.forName("net.fabricmc.loader.api.FabricLoader");
        Object instance = loader.getMethod("getInstance").invoke(null);
        return (Path) loader.getMethod("getGameDir").invoke(instance);
    }

    private static void attachFabricTick() throws Exception {
        Class<?> type = Class.forName("net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents");
        Object event = type.getField("END_CLIENT_TICK").get(null);
        Class<?> handler = Class.forName("net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents$EndTick");
        Object proxy = Proxy.newProxyInstance(handler.getClassLoader(), new Class<?>[]{handler},
                (self, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "toString" -> "QuestChatKeyboardBridgeTick";
                            case "hashCode" -> System.identityHashCode(self);
                            case "equals" -> args != null && self == args[0];
                            default -> null;
                        };
                    }
                    if (method.getName().equals("onEndTick") && args != null && args.length == 1)
                        tick(args[0]);
                    return null;
                });
        Class<?> eventApi = Class.forName("net.fabricmc.fabric.api.event.Event");
        Method register = eventApi.getMethod("register", Object.class);
        try { register.invoke(event, proxy); }
        catch (InvocationTargetException e) {
            if (e.getCause() instanceof Exception ex) throw ex;
            throw e;
        }
    }

    private static void tick(Object client) {
        if (!tickSeen) { tickSeen = true; log("END_CLIENT_TICK_CALLBACK_RECEIVED"); }
        if (client == null || directory == null) return;
        Object screen;
        try {
            if (screenField == null) {
                screenField = findField(client.getClass(), "field_1755", "currentScreen");
                log("SCREEN_FIELD_FOUND " + screenField.getName());
            }
            screen = screenField.get(client);
        } catch (Throwable e) {
            if (!failedScreen) { failedScreen = true; log("SCREEN_POLL_FAILED " + e.getClass().getSimpleName()); }
            return;
        }

        boolean open = isChatScreen(screen);
        if (open != prevOpen) {
            if (open) {
                session = UUID.randomUUID().toString();
                publishRequest("OPEN", session);
                log("CHAT_OPENED REQUEST_SENT");
            } else {
                publishRequest("CLOSE", session);
                log("CHAT_CLOSED REQUEST_SENT");
                session = "";
                try { Files.deleteIfExists(directory.resolve("response.txt")); } catch (Exception ignored) { }
            }
            prevOpen = open;
        }
        // Called on the Minecraft client tick thread: GUI updates remain here.
        if (open && (++tick % 8 == 0)) receiveResponse(screen);
    }

    private static void receiveResponse(Object screen) {
        Path path = directory.resolve("response.txt");
        try {
            if (!Files.isRegularFile(path)) return;
            if (Files.size(path) > RESPONSE_LIMIT_BYTES) {
                Files.deleteIfExists(path);
                log("OVERSIZE_RESPONSE_REJECTED");
                return;
            }
            String[] lines = Files.readString(path, StandardCharsets.UTF_8).split("\\R", -1);
            Files.deleteIfExists(path);
            if (lines.length < 3 || !MAGIC.equals(lines[0]) || !session.equals(lines[2])) {
                log("INVALID_OR_STALE_RESPONSE_SKIPPED");
                return;
            }
            if ("CANCEL".equals(lines[1])) { log("KEYBOARD_CANCELLED"); return; }
            if ("ERROR".equals(lines[1])) { log("UNITY_KEYBOARD_UNAVAILABLE"); return; }
            if (!("COMMIT".equals(lines[1]) || "SUBMIT".equals(lines[1])) || lines.length < 4) {
                log("UNKNOWN_RESPONSE_SKIPPED"); return;
            }
            String text = new String(Base64.getDecoder().decode(lines[3]), StandardCharsets.UTF_8);
            text = text.replace('\n', ' ').replace('\r', ' ');
            if (text.length() > 256) text = text.substring(0, 256);
            Field input = findField(screen.getClass(), "field_2382", "chatField");
            Object textField = input.get(screen);
            if (textField == null) { log("CHAT_FIELD_NOT_READY"); return; }
            Method setText;
            try { setText = textField.getClass().getMethod("method_1852", String.class); }
            catch (NoSuchMethodException e) { setText = textField.getClass().getMethod("setText", String.class); }
            setText.invoke(textField, text);
            log("TEXT_APPLIED_TO_CHAT_FIELD");

            if ("SUBMIT".equals(lines[1])) {
                try {
                    Method keyPressed;
                    try { keyPressed = screen.getClass().getMethod("method_25404", int.class, int.class, int.class); }
                    catch (NoSuchMethodException e) {
                        keyPressed = screen.getClass().getMethod("keyPressed", int.class, int.class, int.class);
                    }
                    keyPressed.invoke(screen, 257, 0, 0); // GLFW_KEY_ENTER
                    log("CHAT_ENTER_HANDLER_CALLED");
                } catch (Throwable e) {
                    log("CHAT_SUBMIT_FALLBACK_MANUAL_ENTER " + e.getClass().getSimpleName());
                }
            }
        } catch (Throwable e) {
            log("RESPONSE_PROCESS_FAILED " + e.getClass().getSimpleName());
        }
    }

    private static Field findField(Class<?> type, String... names) throws NoSuchFieldException {
        for (Class<?> cls = type; cls != null; cls = cls.getSuperclass()) {
            for (String name : names) {
                try {
                    Field field = cls.getDeclaredField(name);
                    field.setAccessible(true);
                    return field;
                } catch (NoSuchFieldException ignored) { }
            }
        }
        throw new NoSuchFieldException(type.getName());
    }

    private static boolean isChatScreen(Object screen) {
        for (Class<?> cls = screen == null ? null : screen.getClass(); cls != null; cls = cls.getSuperclass()) {
            if (cls.getName().equals("net.minecraft.class_408") ||
                    cls.getName().equals("net.minecraft.client.gui.screen.ChatScreen")) return true;
        }
        return false;
    }

    private static void publishRequest(String action, String id) {
        Path target = directory.resolve("request.txt");
        Path temp = directory.resolve("request.tmp");
        try {
            Files.writeString(temp, MAGIC + "\n" + action + "\n" + id + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            try { Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Throwable e) { log("REQUEST_WRITE_FAILED " + e.getClass().getSimpleName()); }
    }

    private static synchronized void log(String info) {
        String text = LocalDateTime.now() + " | " + info;
        System.out.println("[QuestChatKeyboard/Bridge] " + text);
        if (status == null) return;
        try { Files.writeString(status, text + "\n", StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND); }
        catch (Exception e) { System.err.println("[QuestChatKeyboard/Bridge] LOG_FAILED " + e.getClass().getSimpleName()); }
    }
}
