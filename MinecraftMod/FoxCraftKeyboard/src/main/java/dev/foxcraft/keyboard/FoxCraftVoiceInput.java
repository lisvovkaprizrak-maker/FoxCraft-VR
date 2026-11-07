package dev.foxcraft.keyboard;

import net.minecraft.client.MinecraftClient;
import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.ArrayList;
import java.util.function.Consumer;

/**
 * Optional Android SpeechRecognizer integration. All Android classes are
 * accessed reflectively so Fabric can still run on desktop JVMs.
 * Dictation inserts into the focused field only; it NEVER sends chat by itself.
 *
 * Depends on QuestCraft exposing an Android Context, Android RECORD_AUDIO
 * permission, and a recognition service. All unsupported states are reported.
 */
public final class FoxCraftVoiceInput {
    private static volatile boolean listening;
    private static Object recognizer;
    private static String bridgeRequestId;
    private static Consumer<String> bridgeResult;
    private static Consumer<String> bridgeStatus;
    private static long bridgeStartedAt;
    private static long bridgeNextPollAt;

    private static Path bridgePath() {
        return FabricLoader.getInstance().getConfigDir().resolve("foxcraft-voice-bridge");
    }

    private FoxCraftVoiceInput() {}

    public static boolean start(boolean russian, Consumer<String> completed,
                                Consumer<String> status) {
        if (listening) {
            postMinecraft(() -> status.accept("Микрофон уже слушает"));
            return false;
        }
        // The QuestCraft/Pojlib JVM generally cannot access Android classes
        // even though it runs inside the Android Unity host. Prefer the real
        // app-side bridge when its live heartbeat is present.
        if (startViaUnityBridge(russian, completed, status)) return true;
        if (!System.getProperty("java.vendor", "").toLowerCase().contains("android") &&
            !isAndroidAvailable()) {
            status.accept("Для микрофона установи APK FoxCraft с голосовым мостом");
            return false;
        }
        try {
            Object context = androidContext();
            if (context == null) {
                status.accept("QuestCraft не предоставил Android-контекст");
                return false;
            }
            Class<?> ctx = Class.forName("android.content.Context");
            String microphone = "android.permission.RECORD_AUDIO";
            int permission = ((Number) ctx.getMethod("checkSelfPermission", String.class)
                .invoke(context, microphone)).intValue();
            if (permission != 0) {
                try {
                    Class<?> activity = Class.forName("android.app.Activity");
                    if (activity.isInstance(context)) {
                        activity.getMethod("requestPermissions", String[].class, int.class)
                            .invoke(context, new String[]{microphone}, 9317);
                        status.accept("Разреши микрофон и нажми ещё раз");
                    } else status.accept("Разреши микрофон в настройках QuestCraft");
                } catch (ReflectiveOperationException e) {
                    status.accept("Разреши микрофон в настройках QuestCraft");
                }
                return false;
            }
            Class<?> speech = Class.forName("android.speech.SpeechRecognizer");
            boolean available = (Boolean) speech.getMethod(
                "isRecognitionAvailable", ctx).invoke(null, context);
            if (!available) {
                status.accept("На устройстве нет службы распознавания речи");
                return false;
            }
            // Recognizer API requires Android's UI thread, not Minecraft's.
            listening = true;
            status.accept("Подключаю микрофон…");
            Object android = context;
            runOnAndroid(() -> begin(android, russian, completed, status));
            return true;
        } catch (Exception | LinkageError e) {
            listening = false;
            status.accept("Голосовой ввод недоступен: " + e.getClass().getSimpleName());
            return false;
        }
    }

    private static boolean startViaUnityBridge(boolean russian,
                                               Consumer<String> completed,
                                               Consumer<String> status) {
        Path folder = bridgePath();
        try {
            Path ready = folder.resolve("ready");
            // A stale readiness file must never make a dead APK look ready.
            if (!Files.isRegularFile(ready) ||
                System.currentTimeMillis() -
                    Files.getLastModifiedTime(ready).toMillis() > 12000)
                return false;
            Files.createDirectories(folder);
            Files.deleteIfExists(folder.resolve("result"));
            String id = Long.toUnsignedString(System.nanoTime());
            Files.writeString(folder.resolve("request"),
                id + "\n" + (russian ? "ru" : "en"), StandardCharsets.UTF_8);
            bridgeRequestId = id;
            bridgeResult = completed;
            bridgeStatus = status;
            bridgeStartedAt = System.currentTimeMillis();
            bridgeNextPollAt = 0;
            listening = true;
            status.accept("Микрофон FoxCraft запускается…");
            return true;
        } catch (IOException | SecurityException e) {
            status.accept("Нет доступа к голосовому мосту FoxCraft");
            return false;
        }
    }

    /** Poll once per render interval on the Minecraft client thread. */
    public static void poll() {
        if (bridgeRequestId == null) return;
        long now = System.currentTimeMillis();
        if (now < bridgeNextPollAt) return;
        bridgeNextPollAt = now + 180;
        String expected = bridgeRequestId;
        Path response = bridgePath().resolve("result");
        try {
            if (Files.isRegularFile(response)) {
                String[] parts = Files.readString(response, StandardCharsets.UTF_8)
                    .split("\\n", 3);
                if (parts.length >= 3 && expected.equals(parts[0])) {
                    Files.deleteIfExists(response);
                    String received = new String(Base64.getDecoder().decode(parts[2]),
                        StandardCharsets.UTF_8);
                    Consumer<String> done = bridgeResult;
                    Consumer<String> notice = bridgeStatus;
                    clearBridge();
                    if ("OK".equals(parts[1]) && !received.isBlank())
                        done.accept(received);
                    else notice.accept(received.isBlank()
                        ? "Голосовой ввод не распознал речь" : received);
                    return;
                }
            }
            if (now - bridgeStartedAt > 45000) {
                Consumer<String> notice = bridgeStatus;
                clearBridge();
                Files.deleteIfExists(bridgePath().resolve("request"));
                notice.accept("Микрофон не ответил. Проверь доступ в Android.");
            }
        } catch (IOException | IllegalArgumentException e) {
            Consumer<String> notice = bridgeStatus;
            clearBridge();
            notice.accept("Не удалось прочитать результат микрофона");
        }
    }

    private static void clearBridge() {
        bridgeRequestId = null;
        bridgeResult = null;
        bridgeStatus = null;
        listening = false;
    }

    private static boolean isAndroidAvailable() {
        try { Class.forName("android.content.Context"); return true; }
        catch (ClassNotFoundException | LinkageError e) { return false; }
    }

    private static Object androidContext() {
        // QuestCraft is normally a Unity host. A few Pojav-based launchers
        // expose their Activity elsewhere, so try those alternatives too.
        String[] owners = {
            "com.unity3d.player.UnityPlayer",
            "net.kdt.pojavlaunch.Tools",
            "net.kdt.pojavlaunch.MainActivity"
        };
        String[] fields = {"currentActivity", "mActivity", "activity"};
        for (String owner : owners) {
            try {
                Class<?> type = Class.forName(owner);
                for (String field : fields) {
                    try {
                        Field f = type.getField(field);
                        Object value = f.get(null);
                        if (value != null) return value;
                    } catch (NoSuchFieldException ignored) {}
                }
            } catch (ReflectiveOperationException | LinkageError ignored) {}
        }
        try {
            Class<?> thread = Class.forName("android.app.ActivityThread");
            return thread.getMethod("currentApplication").invoke(null);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    private static void runOnAndroid(Runnable task) throws Exception {
        Class<?> looper = Class.forName("android.os.Looper");
        Object main = looper.getMethod("getMainLooper").invoke(null);
        Class<?> handler = Class.forName("android.os.Handler");
        Object h = handler.getConstructor(looper).newInstance(main);
        handler.getMethod("post", Runnable.class).invoke(h, task);
    }

    private static void begin(Object context, boolean russian,
                              Consumer<String> result, Consumer<String> status) {
        try {
            Class<?> ctx = Class.forName("android.content.Context");
            Class<?> speech = Class.forName("android.speech.SpeechRecognizer");
            Class<?> listener = Class.forName("android.speech.RecognitionListener");
            Object service = speech.getMethod("createSpeechRecognizer", ctx)
                .invoke(null, context);
            recognizer = service;
            Object events = Proxy.newProxyInstance(
                FoxCraftVoiceInput.class.getClassLoader(),
                new Class<?>[]{listener}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "onReadyForSpeech" ->
                            postMinecraft(() -> status.accept("Говори…"));
                        case "onResults" -> {
                            String phrase = "";
                            try {
                                Object bundle = args[0];
                                Object raw = bundle.getClass()
                                    .getMethod("getStringArrayList", String.class)
                                    .invoke(bundle,
                                        "results_recognition");
                                if (raw instanceof ArrayList<?> words &&
                                    !words.isEmpty() && words.get(0) instanceof String text)
                                    phrase = text;
                            } catch (Exception ignored) {}
                            String spoken = phrase;
                            finish();
                            postMinecraft(() -> {
                                if (!spoken.isBlank()) result.accept(spoken);
                                else status.accept("Речь не распознана");
                            });
                        }
                        case "onError" -> {
                            int code = args != null && args.length > 0 &&
                                args[0] instanceof Integer n ? n : -1;
                            finish();
                            postMinecraft(() -> status.accept(
                                "Ошибка распознавания (" + code + ")"));
                        }
                        default -> { }
                    }
                    return null;
                });
            speech.getMethod("setRecognitionListener", listener)
                .invoke(service, events);
            Class<?> intentType = Class.forName("android.content.Intent");
            Object intent = intentType.getConstructor(String.class)
                .newInstance("android.speech.action.RECOGNIZE_SPEECH");
            Method extra = intentType.getMethod("putExtra", String.class, String.class);
            extra.invoke(intent, "android.speech.extra.LANGUAGE_MODEL", "free_form");
            extra.invoke(intent, "android.speech.extra.LANGUAGE",
                russian ? "ru-RU" : "en-US");
            speech.getMethod("startListening", intentType).invoke(service, intent);
        } catch (Exception | LinkageError e) {
            finish();
            postMinecraft(() -> status.accept(
                "Ошибка микрофона: " + e.getClass().getSimpleName()));
        }
    }

    private static void finish() {
        listening = false;
        Object old = recognizer;
        recognizer = null;
        if (old != null) {
            try {
                runOnAndroid(() -> {
                    try { old.getClass().getMethod("destroy").invoke(old); }
                    catch (Exception ignored) {}
                });
            } catch (Exception ignored) {}
        }
    }

    public static boolean isListening() { return listening; }

    private static void postMinecraft(Runnable callback) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) client.execute(callback);
    }
}
