<!-- ░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░ -->

<a name="top"></a>

<p align="center">
  <img src="https://capsule-render.vercel.app/api?type=waving&color=0:8B0000,50:FF4500,100:FFA500&height=220&section=header&text=Manhunt%20Revamped&fontSize=62&fontColor=ffffff&fontAlignY=38&desc=A%20server-side%20Minecraft%20Manhunt%20mod%20for%20Fabric%201.21.11&descSize=18&descAlignY=60" alt="Manhunt Revamped" />
</p>

<p align="center">
  <img src="https://readme-typing-svg.demolab.com?font=Fira+Code&weight=600&size=22&duration=3200&pause=900&color=FF5555&center=true&vCenter=true&width=700&lines=Runners+flee.+Hunters+track.+The+dragon+decides.;Teams+survive+a+full+world+reset.;Tracking+compass.+Lead+phase.+Swap+rotation." alt="Typing SVG" />
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Minecraft-1.21.11-62B47A?style=for-the-badge&logo=minecraft&logoColor=white" alt="Minecraft 1.21.11" />
  <img src="https://img.shields.io/badge/Mod_Loader-Fabric-DBD0B4?style=for-the-badge" alt="Fabric" />
  <img src="https://img.shields.io/badge/Requires-Fabric_API-5C5C5C?style=for-the-badge" alt="Requires Fabric API" />
  <img src="https://img.shields.io/badge/Java-21-orange?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java 21" />
</p>

<p align="center">
  <img src="https://img.shields.io/github/actions/workflow/status/DexalGT/manhunt-revamped/build.yml?branch=main&style=flat-square&label=CI%20build" alt="Build status" />
  <img src="https://img.shields.io/badge/side-server--only-blue?style=flat-square" alt="Server only" />
  <img src="https://img.shields.io/badge/mixins-none-success?style=flat-square" alt="No mixins" />
  <img src="https://img.shields.io/github/last-commit/DexalGT/manhunt-revamped/main?style=flat-square" alt="Last commit" />
</p>

---

> **Manhunt Revamped** is a fully native Java rewrite of the classic Manhunt minigame.
> Runners try to beat the game (kill the Ender Dragon) while Hunters chase them down with a
> magic tracking compass. Everything — roles, the tracking compass, the lead phase, win
> detection, pause/resume, and a fair runner rotation — runs in the mod. **No datapack, no
> mixins, no client mod required.**

## ✨ Highlights

| | Feature | What it does |
|---|---|---|
| 🧭 | **Tracking compass** | Hunters get one compass that always points at the nearest runner — even across the Nether (coordinates are scaled 8:1). It updates in place; it is **never** duplicated or re-handed every tick. |
| 💾 | **Reset-proof teams** | Roles and the swap-rotation history are saved **outside** the world folders, so a `/manhunt reset` wipes the map but **keeps your teams**. Just `/manhunt swap` and play again. |
| ⏱️ | **Lead phase** | Hunters are blinded, slowed and weakened for a 45-second head start with an on-screen countdown. |
| ❄️ | **Real pause** | `/manhunt stop` genuinely freezes players (no more "tick freeze does nothing"); `/manhunt resume` runs a 5-second countdown. |
| 🔀 | **Smart swap** | Rotates the runner role to whoever has been runner the **fewest** times, so everyone gets a turn. |
| 🐉 | **Auto win detection** | Hunters win when every runner is dead; runners win if the Ender Dragon falls or no hunters remain. |
| 🧨 | **Safe world reset** | `/manhunt reset` is a **10-second cancellable countdown** wired to an external watcher — no accidental wipes. |

## 🎮 Commands

> All commands require **OP** (permission level 2+).

| Command | Description |
|---|---|
| `/manhunt shuffle` | Randomly pick **1** runner; everyone else becomes a hunter. |
| `/manhunt shuffle <count>` | Randomly pick `<count>` runners. The count is remembered. |
| `/manhunt swap` | Rotate the runner role to the players who've been runner the least. |
| `/manhunt start` | Begin a round (clears inventories, heals, starts the 45s lead phase). |
| `/manhunt stop` | Pause the game and freeze every player. |
| `/manhunt resume` | Resume a paused game after a 5-second countdown. |
| `/manhunt manual runner <player>` | Force a specific player to be a runner. |
| `/manhunt manual hunter <player>` | Force a specific player to be a hunter. |
| `/manhunt manual clear` | Reset everyone to hunter. |
| `/manhunt reset_history` | Wipe the swap-rotation history. |
| `/manhunt abort` | Immediately end the current round (no world reset). |
| `/manhunt reset` | Start a **10-second** world-reset countdown. |
| `/manhunt reset cancel` | Cancel the world-reset countdown. |

## 📦 Installation

This is a **server-side** mod. Players do **not** need to install anything.

**Requirements on the server:**

1. **Minecraft 1.21.11**
2. **[Fabric Loader](https://fabricmc.net/use/server/)** `0.19.2`+
3. **[Fabric API](https://modrinth.com/mod/fabric-api)** — ⚠️ **required**, drop it in the `mods/` folder
4. **Java 21**

**Steps:**

1. Install a Fabric server for 1.21.11.
2. Put **Fabric API** and **`manhunt-revamped-1.0.0.jar`** in the server's `mods/` folder.
3. Start the server. You'll see `[Manhunt] ... loading (mod build)` in the log.
4. (Optional) For the auto world-reset feature, set up the [watcher](#-world-reset--the-watcher).

Grab the jar from the [Releases](https://github.com/DexalGT/manhunt-revamped/releases) page or the latest green [Actions](https://github.com/DexalGT/manhunt-revamped/actions) build.

## 🕹️ A typical match

```text
/manhunt shuffle 1     →  pick a random runner
/manhunt start         →  45s lead phase, hunters blinded
   ...the hunt...       →  hunters' compasses track the runner
                           runner dies      → HUNTERS WIN
                           dragon dies      → RUNNERS WIN
/manhunt swap          →  rotate to a fresh runner
/manhunt start         →  go again — teams are remembered even after a reset
```

## 💾 Why teams survive a reset

Vanilla scoreboard teams and tags live in the **world save**, so wiping the world wipes them.
Manhunt Revamped stores roles + history in **`<server-root>/manhunt/teams.json`**, which sits
outside the `world` / `world_nether` / `world_the_end` folders that get deleted. On the next
join, each player's role is re-applied automatically.

## 🧨 World reset & the watcher

`/manhunt reset` doesn't wipe anything itself — it logs a secret token after a 10-second
countdown. An external Python **watcher** tails the server log, and when it sees the token it
stops the server, deletes the world folders, and restarts with a fresh map. The token is read
from `<server-root>/manhunt-reset-token.txt`.

## 🛠️ Building from source

> See **[DEVELOPMENT.md](DEVELOPMENT.md)** for the full architecture and contributor guide.

```bash
./gradlew build
```

The compiled mod lands in `build/libs/manhunt-revamped-1.0.0.jar`. Builds also run
automatically via GitHub Actions on every push to `main`.

<p align="center"><sub>Built with ☕ Java 21 · Fabric Loom · Made by <b>SirDexal</b></sub></p>

<p align="right"><a href="#top">⬆ back to top</a></p>
