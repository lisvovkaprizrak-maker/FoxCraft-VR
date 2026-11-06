package test;

import com.questchatkeyboard.bridge.QuestChatKeyboardBridge;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.class_408;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class TestHarness {
    static final class Client { public Object field_1755; }
    static void tick(Client c, int n) {
        for (int i = 0; i < n; i++)
            ClientTickEvents.END_CLIENT_TICK.callback.onEndTick(c);
    }
    static String[] readRequest(Path directory) throws Exception {
        String[] lines = Files.readString(directory.resolve("request.txt"),
            StandardCharsets.UTF_8).split("\\R");
        assertTrue(lines.length >= 3 && "QCKB1".equals(lines[0]), "invalid request");
        return lines;
    }
    static void reply(Path directory, String action, String id, String text) throws Exception {
        String encoded = Base64.getEncoder().encodeToString(
            text.getBytes(StandardCharsets.UTF_8));
        Files.writeString(directory.resolve("response.txt"),
            "QCKB1\n" + action + "\n" + id + "\n" + encoded + "\n",
            StandardCharsets.UTF_8);
    }
    static void assertTrue(boolean ok, String description) {
        if (!ok) throw new AssertionError(description);
    }
    public static void main(String[] args) throws Exception {
        System.setProperty("questchatkeyboard.testdir", args[0]);
        new QuestChatKeyboardBridge().onInitializeClient();
        Path directory = Path.of(args[0], "config", "questchatkeyboard-bridge");
        Client client = new Client();
        tick(client, 1);

        class_408 chat1 = new class_408();
        client.field_1755 = chat1;
        tick(client, 1);
        String[] open1 = readRequest(directory);
        assertTrue("OPEN".equals(open1[1]), "OPEN missing");
        String session1 = open1[2];
        tick(client, 12);
        assertTrue(chat1.field_2382.get().isEmpty(), "unexpected local text injection");

        reply(directory, "SUBMIT", "stale-session", "SHOULD NOT SEND");
        tick(client, 10);
        assertTrue(chat1.field_2382.get().isEmpty() && chat1.enterCalls == 0,
            "stale session was accepted");

        reply(directory, "COMMIT", session1, "Привет, FoxCraft!");
        tick(client, 10);
        assertTrue(chat1.field_2382.get().equals("Привет, FoxCraft!"),
            "UTF-8 Cyrillic COMMIT failed");
        assertTrue(chat1.enterCalls == 0, "COMMIT wrongly triggered Enter");

        // Regression: exactly 256 UTF-16 units may include an entire emoji.
        String intactEmoji = "y".repeat(254) + "🦊";
        reply(directory, "COMMIT", session1, intactEmoji);
        tick(client, 10);
        assertTrue(chat1.field_2382.get().equals(intactEmoji),
            "emoji was lost at exact chat length boundary");

        // Regression: an emoji straddling the 256-unit limit must be dropped
        // as a whole rather than leaving an unpaired high surrogate.
        reply(directory, "COMMIT", session1, "x".repeat(255) + "🦊" + "tail");
        tick(client, 10);
        assertTrue(chat1.field_2382.get().equals("x".repeat(255)),
            "chat was truncated inside an emoji surrogate pair");
        assertTrue(chat1.enterCalls == 0, "truncation unexpectedly submitted chat");

        client.field_1755 = null;
        tick(client, 1);
        assertTrue("CLOSE".equals(readRequest(directory)[1]), "CLOSE missing");
        class_408 chat2 = new class_408();
        client.field_1755 = chat2;
        tick(client, 1);
        String[] open2 = readRequest(directory);
        assertTrue("OPEN".equals(open2[1]) && !open2[2].equals(session1),
            "session not refreshed");

        reply(directory, "SUBMIT", open2[2], "/say Проверка FoxCraft");
        tick(client, 10);
        assertTrue(chat2.field_2382.get().equals("/say Проверка FoxCraft"),
            "SUBMIT content missing");
        assertTrue(chat2.enterCalls == 1, "Enter must be invoked once");
        tick(client, 10);
        assertTrue(chat2.enterCalls == 1, "duplicate Enter");

        client.field_1755 = null;
        tick(client, 1);
        assertTrue("CLOSE".equals(readRequest(directory)[1]), "final CLOSE missing");

        String log = Files.readString(Path.of(args[0], "config",
            "questchatkeyboard-bridge-status.txt"));
        for (String marker : new String[] {
            "BRIDGE_INITIALIZED v" + args[1], "END_CLIENT_TICK_CALLBACK_RECEIVED",
            "CHAT_OPENED REQUEST_SENT", "CHAT_CLOSED REQUEST_SENT",
            "TEXT_APPLIED_TO_CHAT_FIELD", "CHAT_ENTER_HANDLER_CALLED",
            "INVALID_OR_STALE_RESPONSE_SKIPPED" })
            assertTrue(log.contains(marker), "missing log marker " + marker);
        assertTrue(!log.contains("Привет, FoxCraft!") &&
            !log.contains("/say Проверка FoxCraft"), "chat text leaked to log");
        System.out.println("ALL TESTS PASSED: QCKB1 OPEN/CLOSE, session safety, UTF-8, COMMIT, SUBMIT, log privacy");
    }
}
