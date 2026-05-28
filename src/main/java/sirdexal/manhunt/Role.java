package sirdexal.manhunt;

// A player's persistent Manhunt role. Stored by UUID in ManhuntData so it
// survives a full world reset (the watcher only wipes the world folders).
public enum Role {
    NONE,
    RUNNER,
    HUNTER
}
