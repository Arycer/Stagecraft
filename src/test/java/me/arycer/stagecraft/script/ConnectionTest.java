package me.arycer.stagecraft.script;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ConnectionTest {
    @Test
    void independentOperationsKeepTheirOwnDisconnectScopes() {
        Connection connection = new Connection();
        connection.expectDisconnect("restart-a", true, 10_000);
        connection.expectDisconnect("transfer", true, 10_000);
        connection.expectDisconnect("restart-a", false, 10_000);
        assertTrue(connection.expectingDisconnect());
        connection.expectDisconnect("transfer", false, 10_000);
        assertFalse(connection.expectingDisconnect());
    }

    @Test
    void anExpiredReconnectFailsUntilTheClientHasActuallyJoined() throws Exception {
        Connection connection = new Connection();
        connection.expectDisconnect("restart", true, 1);
        Thread.sleep(10);
        assertTrue(connection.reconnectExpired());
        assertFalse(connection.expectingDisconnect());
        connection.joined("localhost:25565");
        assertFalse(connection.reconnectExpired());
        assertEquals("localhost:25565", connection.lastAddress());
    }

    @Test
    void anotherRunClearsRepliesAndDisconnectExpectations() {
        Connection connection = new Connection();
        connection.expectDisconnect(true);
        connection.reply("operation", "done", null, Map.of("server", "B"));
        assertEquals("B", connection.reply("operation", "done").values().get("server"));
        connection.beginRun();
        assertFalse(connection.expectingDisconnect());
        assertNull(connection.reply("operation", "done"));
    }

    @Test
    void completedRequestsReleaseAllTheirReplies() {
        Connection connection = new Connection();
        connection.reply("one", "ready", null);
        connection.reply("one", "done", null);
        connection.reply("other", "done", null);
        connection.forget("one");
        assertNull(connection.reply("one", new String[]{"ready", "done"}));
        assertNotNull(connection.reply("other", "done"));
    }
}
