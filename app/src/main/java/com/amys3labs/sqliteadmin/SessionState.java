package com.amys3labs.sqliteadmin;

/**
 * Tracks whether a database editor is open with unsaved changes,
 * so opening another file from a file manager can prompt the user.
 */
public final class SessionState {
    private SessionState() {}

    public static volatile boolean editorOpen = false;
    public static volatile boolean dirty = false;
    public static volatile String dbPath = null;
    public static volatile String originalUri = null;

    public static void clear() {
        editorOpen = false;
        dirty = false;
        dbPath = null;
        originalUri = null;
    }
}
