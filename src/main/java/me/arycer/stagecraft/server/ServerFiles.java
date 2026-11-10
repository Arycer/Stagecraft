package me.arycer.stagecraft.server;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

/** Resolves script paths under the host root: relative, normalized, never outside it (symlinks included). */
public final class ServerFiles {
    private final Path root;

    public ServerFiles(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    public Path root() {
        return root;
    }

    /** {@code relative} under the root; throws if it is absolute or leaves the root. */
    public Path resolve(String relative) throws IOException {
        if (relative == null || relative.isBlank()) return root;
        String clean = relative.replace(File.separatorChar, '/').replace("\\", "/");
        if (clean.startsWith("/") || clean.matches("^[A-Za-z]:.*")) throw new IllegalArgumentException("Paths are relative to the host root: " + relative);
        Path path = root.resolve(clean).normalize();
        if (!path.startsWith(root)) throw new IllegalArgumentException("Path leaves the host root: " + relative);
        // The deepest existing ancestor must also be inside once links are followed.
        Path existing = path;
        while (existing != null && !Files.exists(existing, LinkOption.NOFOLLOW_LINKS)) existing = existing.getParent();
        if (existing != null && !existing.toRealPath().startsWith(root.toRealPath())) throw new IllegalArgumentException("Path leaves the host root through a link: " + relative);
        return path;
    }

    public String relative(Path path) {
        return root.relativize(path).toString().replace(File.separatorChar, '/');
    }
}
