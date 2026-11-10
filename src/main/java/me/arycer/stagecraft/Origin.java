package me.arycer.stagecraft;

/** A stable placement frame, independent of subsequent player movement. */
public record Origin(String dimension, double x, double y, double z, float yaw, float pitch) {
    public String wrap(String command) {
        return "execute in " + dimension + " positioned " + x + " " + y + " " + z
                + " rotated " + yaw + " " + pitch + " run " + command;
    }
}
