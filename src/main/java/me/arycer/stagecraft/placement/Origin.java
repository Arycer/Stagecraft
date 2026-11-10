package me.arycer.stagecraft.placement;

import java.math.BigDecimal;

/** A stable placement frame, independent of subsequent player movement. */
public record Origin(String dimension, double x, double y, double z, float yaw, float pitch) {
    public String wrap(String command) {
        return "execute in " + dimension + " positioned " + number(x) + " " + number(y) + " " + number(z)
                + " rotated " + number(yaw) + " " + number(pitch) + " run " + command;
    }

    /**
     * Minecraft's command parser only reads plain decimals: {@code Double.toString} switches to scientific
     * notation below 0.001 and from 10^7 ({@code 1.0E-4}), which {@code execute positioned} rejects.
     */
    static String number(double value) {
        String text = Double.toString(value);
        return text.indexOf('E') < 0 ? text : BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    static String number(float value) {
        String text = Float.toString(value);
        return text.indexOf('E') < 0 ? text : new BigDecimal(text).stripTrailingZeros().toPlainString();
    }
}
