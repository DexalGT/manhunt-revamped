# ─────────────────────────────────────────────────────────────────────────────
# /function manhunt:shuffle
# /function manhunt:shuffle {count:2}
#
# Randomly assigns roles to all online players.
#   - Picks <count> random runners (defaults to 1 if not provided).
#   - Tags the chosen players as "runner" and adds them to the runners team.
#   - Tags everyone else as "hunter" and adds them to the hunters team.
#   - Saves the runner count to mh_runner_count for use by /function manhunt:swap.
#
# Usage:
#   /function manhunt:shuffle              → 1 runner
#   /function manhunt:shuffle {count:2}   → 2 runners
# ─────────────────────────────────────────────────────────────────────────────

# Default to 1 runner then delegate to the non-macro do_shuffle.
# Calling a macro function from Java context silently fails in 1.21.11,
# so we set the scoreboard directly (scoreboard commands are unaffected).
scoreboard players set $wanted_runners mh_runner_count 1
function manhunt:internal/do_shuffle
