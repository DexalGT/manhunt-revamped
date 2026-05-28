package sirdexal.manhunt;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Persistent team / role storage for Manhunt.
 *
 * <p>This is the whole point of turning the datapack into a mod: the file lives
 * at {@code <game-dir>/manhunt/teams.json}, which sits OUTSIDE the world folders
 * ({@code world}, {@code world_nether}, {@code world_the_end}) that the reset
 * watcher wipes. So when the server is reset the roles, the rotation history
 * ({@code timesRunner}) and the wanted-runner count all survive, and a single
 * {@code /manhunt swap} after the reset keeps the rotation going.</p>
 */
public class ManhuntData {
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // How many runners the next shuffle/swap should pick. Persisted.
    public int wantedRunners = 1;

    // Keyed by player UUID string.
    public Map<String, Entry> players = new HashMap<>();

    public static class Entry {
        public String name = "";
        public String role = Role.NONE.name();
        public int timesRunner = 0;
    }

    // ── File handling ───────────────────────────────────────────────────────────
    private transient Path file;

    public static Path defaultPath() {
        return FabricLoader.getInstance().getGameDir().resolve("manhunt").resolve("teams.json");
    }

    public static ManhuntData load() {
        Path path = defaultPath();
        ManhuntData data;
        try {
            if (Files.exists(path)) {
                String json = Files.readString(path);
                data = GSON.fromJson(json, ManhuntData.class);
                if (data == null) data = new ManhuntData();
                ManhuntLog.info("Loaded team data from {}", path.toAbsolutePath());
            } else {
                data = new ManhuntData();
                ManhuntLog.info("No existing team data at {} — starting fresh.", path.toAbsolutePath());
            }
        } catch (Exception e) {
            ManhuntLog.error("Failed to read " + path + " — starting fresh", e);
            data = new ManhuntData();
        }
        if (data.players == null) data.players = new HashMap<>();
        data.file = path;
        return data;
    }

    public synchronized void save() {
        if (file == null) file = defaultPath();
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(this));
            ManhuntLog.debug("Saved team data ({} players, wantedRunners={}) to {}",
                    players.size(), wantedRunners, file);
        } catch (IOException e) {
            ManhuntLog.error("Failed to save " + file, e);
        }
    }

    // ── Accessors ───────────────────────────────────────────────────────────────
    public Entry entry(UUID uuid, String name) {
        Entry e = players.computeIfAbsent(uuid.toString(), k -> new Entry());
        if (name != null && !name.isEmpty()) e.name = name;
        return e;
    }

    public Role getRole(UUID uuid) {
        Entry e = players.get(uuid.toString());
        if (e == null) return Role.NONE;
        try {
            return Role.valueOf(e.role);
        } catch (IllegalArgumentException ex) {
            return Role.NONE;
        }
    }

    public int getTimesRunner(UUID uuid) {
        Entry e = players.get(uuid.toString());
        return e == null ? 0 : e.timesRunner;
    }

    public void setRole(UUID uuid, String name, Role role) {
        entry(uuid, name).role = role.name();
    }

    public void incrementTimesRunner(UUID uuid, String name) {
        entry(uuid, name).timesRunner++;
    }

    public void resetHistory() {
        for (Entry e : players.values()) e.timesRunner = 0;
    }
}
