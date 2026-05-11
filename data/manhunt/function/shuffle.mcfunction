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

# Use the saved runner count (set by /manhunt shuffle <N>, default 1 from init).
# Removing the hardcoded set so repeated /manhunt shuffle respects the last count.
function manhunt:internal/do_shuffle
