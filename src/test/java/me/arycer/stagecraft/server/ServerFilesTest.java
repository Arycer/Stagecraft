package me.arycer.stagecraft.server;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ServerFilesTest {
    @TempDir Path root;

    @Test void pathsStayUnderTheRoot() throws Exception {
        ServerFiles files = new ServerFiles(root);
        assertEquals(root.resolve("config/instanced/levels/a.json").toAbsolutePath().normalize(), files.resolve("config/instanced/levels/a.json"));
        assertEquals(root.resolve("a/b").toAbsolutePath().normalize(), files.resolve("a\\b"));
        assertEquals("config/x.json", files.relative(files.resolve("config/x.json")));
        assertThrows(IllegalArgumentException.class, () -> files.resolve("../outside.txt"));
        assertThrows(IllegalArgumentException.class, () -> files.resolve("a/../../outside.txt"));
        assertThrows(IllegalArgumentException.class, () -> files.resolve("/etc/passwd"));
        assertThrows(IllegalArgumentException.class, () -> files.resolve("C:/Windows/win.ini"));
    }

    @Test void configIsWrittenDisabled() throws Exception {
        HostConfig config = HostConfig.load(root.resolve("config"), root);
        assertEquals(false, config.enabled());
        assertEquals(2, config.permissionLevel());
        assertEquals(root.toAbsolutePath().normalize(), config.root());
        Files.writeString(root.resolve("config/" + HostConfig.FILE), "enabled=true\npermission_level=4\nroot=run\nallow_write=false\n");
        config = HostConfig.load(root.resolve("config"), root);
        assertEquals(true, config.enabled());
        assertEquals(4, config.permissionLevel());
        assertEquals(root.resolve("run").toAbsolutePath().normalize(), config.root());
        assertEquals(false, config.allowWrite());
    }
}
