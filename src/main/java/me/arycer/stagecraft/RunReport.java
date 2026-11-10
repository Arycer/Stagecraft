package me.arycer.stagecraft;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The structured result of one run, written next to its {@code .log} as {@code .json} and JUnit
 * {@code .xml} (one test case per {@code stage.step}; a run without steps is a single case). Steps nest:
 * a step inside another is named {@code outer / inner}.
 */
public final class RunReport {
    public enum Status { PASSED, FAILED, CANCELLED }

    public record Step(String name, Status status, long millis, String error) {}

    private final String scenario;
    private final Origin origin;
    private final Map<String, String> args;
    private final Instant started = Instant.now();
    private final long startedNanos = System.nanoTime();
    private final List<Step> steps = new ArrayList<>();
    private final Deque<String> open = new ArrayDeque<>();
    private final Deque<Long> openedAt = new ArrayDeque<>();
    private Status status;
    private String error;
    private long millis;
    private int commands;

    public RunReport(String scenario, Origin origin, Map<String, String> args) {
        this.scenario = scenario;
        this.origin = origin;
        this.args = Map.copyOf(args);
    }

    public synchronized String beginStep(String name) {
        String full = open.isEmpty() ? name : open.peekLast() + " / " + name;
        open.addLast(full);
        openedAt.addLast(System.nanoTime());
        return full;
    }

    public synchronized void endStep(Status result, String failure) {
        if (open.isEmpty()) return;
        steps.add(new Step(open.removeLast(), result, (System.nanoTime() - openedAt.removeLast()) / 1_000_000, failure));
    }

    public synchronized void command() {
        commands++;
    }

    public synchronized void finish(Status result, String failure) {
        while (!open.isEmpty()) endStep(result, failure);
        status = result;
        error = failure;
        millis = (System.nanoTime() - startedNanos) / 1_000_000;
    }

    public synchronized List<Step> steps() {
        return List.copyOf(steps);
    }

    public synchronized JsonObject json() {
        JsonObject root = new JsonObject();
        root.addProperty("scenario", scenario);
        root.addProperty("status", status == null ? "running" : status.name().toLowerCase(Locale.ROOT));
        if (error != null) root.addProperty("error", error);
        root.addProperty("started", started.toString());
        root.addProperty("millis", millis);
        root.addProperty("commands", commands);
        JsonObject frame = new JsonObject();
        frame.addProperty("dimension", origin.dimension());
        frame.addProperty("x", origin.x());
        frame.addProperty("y", origin.y());
        frame.addProperty("z", origin.z());
        frame.addProperty("yaw", origin.yaw());
        frame.addProperty("pitch", origin.pitch());
        root.add("origin", frame);
        JsonObject arguments = new JsonObject();
        args.forEach(arguments::addProperty);
        root.add("args", arguments);
        JsonArray list = new JsonArray();
        for (Step step : steps) {
            JsonObject item = new JsonObject();
            item.addProperty("name", step.name());
            item.addProperty("status", step.status().name().toLowerCase(Locale.ROOT));
            item.addProperty("millis", step.millis());
            if (step.error() != null) item.addProperty("error", step.error());
            list.add(item);
        }
        root.add("steps", list);
        return root;
    }

    public synchronized String junit() {
        List<Step> cases = new ArrayList<>(steps);
        // A failure outside every step (or a run without steps) still shows up as a case.
        boolean failedOutside = status != Status.PASSED && cases.stream().noneMatch(s -> s.status() != Status.PASSED);
        if (cases.isEmpty() || failedOutside) cases.add(new Step("scenario", status == null ? Status.FAILED : status, millis, error));
        long failures = cases.stream().filter(s -> s.status() == Status.FAILED).count();
        long skipped = cases.stream().filter(s -> s.status() == Status.CANCELLED).count();
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<testsuite name=\"stagecraft.").append(escape(scenario)).append("\" tests=\"").append(cases.size())
                .append("\" failures=\"").append(failures).append("\" errors=\"0\" skipped=\"").append(skipped)
                .append("\" time=\"").append(seconds(millis)).append("\" timestamp=\"").append(started).append("\">\n");
        for (Step step : cases) {
            xml.append("  <testcase classname=\"stagecraft.").append(escape(scenario)).append("\" name=\"").append(escape(step.name()))
                    .append("\" time=\"").append(seconds(step.millis())).append("\"");
            if (step.status() == Status.PASSED) {
                xml.append("/>\n");
                continue;
            }
            xml.append(">\n");
            if (step.status() == Status.FAILED) {
                String message = step.error() == null ? "failed" : step.error();
                xml.append("    <failure message=\"").append(escape(message)).append("\">").append(escape(message)).append("</failure>\n");
            } else {
                xml.append("    <skipped message=\"cancelled\"/>\n");
            }
            xml.append("  </testcase>\n");
        }
        return xml.append("</testsuite>\n").toString();
    }

    /** Writes {@code <base>.json} and {@code <base>.xml}. */
    public void write(Path base) throws IOException {
        String json = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(json());
        Files.writeString(base.resolveSibling(base.getFileName() + ".json"), json + "\n", StandardCharsets.UTF_8);
        Files.writeString(base.resolveSibling(base.getFileName() + ".xml"), junit(), StandardCharsets.UTF_8);
    }

    private static String seconds(long millis) {
        return String.format(Locale.ROOT, "%.3f", millis / 1000.0);
    }

    static String escape(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&apos;");
                default -> {
                    if (c < 0x20 && c != '\n' && c != '\t' && c != '\r') out.append(' ');
                    else out.append(c);
                }
            }
        }
        return out.toString();
    }
}
