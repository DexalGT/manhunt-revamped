# Manhunt Revamped

A completely overhauled, server-side Minecraft Manhunt mod for Fabric 1.21.x.

## Features
- **Server-Side Only**: Runs seamlessly on a Fabric server without clients needing to install it.
- **Smart Swap System**: Automatically rotates the runner based on history, ensuring everyone gets a turn.
- **Native Commands**: Control everything with `/manhunt <command>` via Brigadier.
- **Dynamic Title Announcements**: Big on-screen announcements when Hunters or Runners win!
- **Auto Server Wipe Integration**: Automatically signals external scripts (`[SYSTEM_WIPE_REQUESTED_BY_ADMIN]`) to wipe the world after a match.

## Commands (Requires OP)
- `/manhunt shuffle` - Randomly picks 1 runner.
- `/manhunt shuffle <count>` - Randomly picks `<count>` runners.
- `/manhunt swap` - Smartly rotates the runner to the player who has played it the least.
- `/manhunt start` - Starts the match, giving hunters a 30-second headstart (configurable).
- `/manhunt stop` - Safely aborts a match mid-game.
- `/manhunt reset_history` - Wipes the swap rotation history.

## Downloading
You can always grab the latest compiled `.jar` from the [Releases](https://github.com/DexalGT/manhunt-revamped/releases) page on GitHub. 

## Building from Source
This project uses Gradle and is configured for GitHub Actions CI. To build it locally:
```bash
./gradlew build
```
The compiled mod will be located in `build/libs/`.
