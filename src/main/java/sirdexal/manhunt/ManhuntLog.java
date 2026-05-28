package sirdexal.manhunt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Manhunt's logging hub. Every message is written to BOTH:
 *   1. the server console / latest.log (via SLF4J, prefixed "[Manhunt]"), and
 *   2. a dedicated, timestamped file at {@code <gameDir>/manhunt/logs.txt}.
 *
 * <p>Each server start ("instance") rotates the previous {@code logs.txt} to
 * {@code logs-<yyyyMMdd-HHmmss>.txt} and begins a fresh file, so you always have
 * a clean per-instance log plus the full history of older runs.</p>
 */
public final class ManhuntLog {
    private static final Logger SLF4J = LoggerFactory.getLogger("manhunt-revamped");
    private static final Object LOCK = new Object();
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final DateTimeFormatter FULL = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static PrintWriter file;

    private ManhuntLog() {}

    public static Logger slf4j() {
        return SLF4J;
    }

    /** Open (and rotate) the per-instance log file. Safe to call once at mod init. */
    public static void init(Path manhuntDir, String modVersion) {
        synchronized (LOCK) {
            try {
                Files.createDirectories(manhuntDir);
                Path log = manhuntDir.resolve("logs.txt");
                if (Files.exists(log) && Files.size(log) > 0) {
                    String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
                    Path archive = manhuntDir.resolve("logs-" + stamp + ".txt");
                    try {
                        Files.move(log, archive, StandardCopyOption.REPLACE_EXISTING);
                    } catch (Exception ignored) {
                        // if we can't rotate, we'll just truncate below
                    }
                }
                file = new PrintWriter(Files.newBufferedWriter(log, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING), true);
                raw("==========================================================================");
                raw("  Manhunt Revamped v" + modVersion + "   session start " + LocalDateTime.now().format(FULL));
                raw("  log file: " + log.toAbsolutePath());
                raw("==========================================================================");
                SLF4J.info("[Manhunt] Logging to {}", log.toAbsolutePath());
            } catch (Exception e) {
                SLF4J.error("[Manhunt] Could not open logs.txt — file logging disabled: {}", e.toString());
                file = null;
            }
        }
    }

    public static void close() {
        synchronized (LOCK) {
            if (file != null) {
                raw("  session end " + LocalDateTime.now().format(FULL));
                file.flush();
                file.close();
                file = null;
            }
        }
    }

    // ── Public log levels (SLF4J-style "{}" placeholders) ────────────────────────
    public static void info(String fmt, Object... args) {
        String msg = format(fmt, args);
        SLF4J.info("[Manhunt] {}", msg);
        line("INFO ", msg);
    }

    public static void warn(String fmt, Object... args) {
        String msg = format(fmt, args);
        SLF4J.warn("[Manhunt] {}", msg);
        line("WARN ", msg);
    }

    public static void debug(String fmt, Object... args) {
        String msg = format(fmt, args);
        SLF4J.debug("[Manhunt] {}", msg);
        line("DEBUG", msg); // always recorded in the file, even when console debug is off
    }

    public static void error(String fmt, Object... args) {
        String msg = format(fmt, args);
        SLF4J.error("[Manhunt] {}", msg);
        line("ERROR", msg);
    }

    /** Error with a full stack trace, written to console and the file. */
    public static void error(String msg, Throwable t) {
        SLF4J.error("[Manhunt] {}", msg, t);
        line("ERROR", msg + (t != null ? "  ::  " + t : ""));
        if (t != null) {
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            line("ERROR", sw.toString().trim());
        }
    }

    // ── Internals ────────────────────────────────────────────────────────────────
    private static void line(String level, String msg) {
        if (file == null) return;
        synchronized (LOCK) {
            if (file != null) file.println("[" + LocalDateTime.now().format(TS) + "] [" + level + "] " + msg);
        }
    }

    private static void raw(String s) {
        if (file == null) return;
        synchronized (LOCK) {
            if (file != null) file.println(s);
        }
    }

    private static String format(String fmt, Object... args) {
        if (args == null || args.length == 0 || fmt == null) return fmt;
        StringBuilder sb = new StringBuilder(fmt.length() + 16 * args.length);
        int ai = 0;
        for (int i = 0; i < fmt.length(); i++) {
            char c = fmt.charAt(i);
            if (c == '{' && i + 1 < fmt.length() && fmt.charAt(i + 1) == '}') {
                sb.append(ai < args.length ? String.valueOf(args[ai++]) : "{}");
                i++;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
