# Identity

Identity is a Spigot/Bukkit plugin for Minecraft Java 1.12.2. It targets the 1.12.2 NMS package `v1_12_R1` and uses ProtocolLib packet interception for server-side control routing and clone creation.

This project is built for operators and server admins who need two separate admin systems:

- Control mode: an admin routes their input to a live target player while the target is blocked from acting normally.
- Clone mode: an admin creates a player-like clone with its own UUID and controlled visual state, without affecting the original player.

> Important: this is not a fake Bukkit-only `Player` reference trick. The plugin intentionally uses NMS/packet-level routing and documents the limitations honestly.

## Requirements

- Spigot/Bukkit 1.12.2
- Java 8
- ProtocolLib
- Bukkit-compatible server runtime with plugin loading enabled

## Installation

1. Build the Spigot 1.12.2 server jar with BuildTools if needed.
2. Install ProtocolLib on the server.
3. Compile this project with Maven.
4. Copy the generated jar from `target/` into the server's `plugins` folder.
5. Restart the server.

## Commands

Main command:

- `/identity`
- `/identity control <player>`
- `/identity release <player>`
- `/identity releaseall`
- `/identity clone <player> [clone-id]`
- `/identity clones`
- `/identity removeclone <clone-id>`
- `/identity removeclones`
- `/identity clonechat <clone-id> <message>`
- `/identity info <player>`
- `/identity reload`

Aliases:

- `/id`
- `/ident`

## Control mode

Permission:

- `identity.control`

What it does:

- The admin controls the real target player while the target remains online.
- The target stays on the same account, with their same username, skin, inventory, and location.
- The target's own movement, commands, chat, attacks, and interactions are blocked while controlled.
- The admin's movement and interaction packets are forwarded to the target as best as possible through NMS packet routing.
- Other players still see the target as themselves.
- Chat from the controller can appear as the controlled player.

Example:

- Admin controls Steve.
- Admin types: `yo`
- Players see: `<Steve> yo`

Release commands:

- `/identity release <player>`
- `/identity releaseall`

Automatic cleanup:

- controller disconnects
- target disconnects
- plugin disables
- packet-routing errors

## Clone mode

Permission:

- `identity.clone`

What it does:

- Spawns a separate player-like clone based on the target player.
- Clone keeps its own UUID, never reusing the original player's identity.
- Clone copies the target's visual state:
  - skin
  - display name appearance
  - armor
  - held item
  - player model state
- The original player continues normal play.
- The clone is not AI and does not pathfind, imitate, or make decisions.
- The admin directly controls the clone's movement, look direction, sprinting, sneaking, combat, item usage, and chat.

Example:

- `/identity clone Steve SteveClone`
- `/identity clonechat SteveClone hello`
- Players see: `<Steve> hello`
- Console logs: `[Identity] Admin -> Clone(SteveClone): hello`

Clone management:

- `/identity clones`
- `/identity removeclone <clone-id>`
- `/identity removeclones`

## Permissions

All default to OP:

- `identity.admin`
- `identity.control`
- `identity.release`
- `identity.clone`
- `identity.clone.remove`
- `identity.chat`

`identity.admin` grants the full set.

## Config

The plugin includes a config file with messages and toggles. You can customize the behavior for blocking chat, commands, interactions, clone copying, or logging.

Example values:

```yaml
control:
  block-chat: true
  block-commands: true
  block-interactions: true

clone:
  copy-skin: true
  copy-armor: true
  copy-held-item: true
  allow-chat: true

logging:
  enabled: true

messages:
  prefix: "&8[&bIdentity&8] &r"
  no-permission: "&cYou do not have permission."
  player-not-found: "&cThat player is not online."
  clone-not-found: "&cNo active clone has that ID."
  control-started: "&aYou now control &f{player}&a."
  control-released: "&aReleased control of &f{player}&a."
  clone-created: "&aCreated clone &f{id}&a based on &f{player}&a."
  clone-removed: "&aRemoved clone &f{id}&a."
  reloaded: "&aConfiguration reloaded."
```

## Technical limitations and safety

Minecraft 1.12.2 does not natively allow one connected player's client input to be transferred to another online player. This plugin uses best-effort packet forwarding and event blocking on the server side. That means:

- it is not a true client takeover
- it is not a perfect emulation of native player control
- control can vary with packet timing, plugin behavior, and server state
- some edge cases involving vehicles, custom GUIs, or unusual plugins may behave inconsistently

The plugin intentionally documents those limits instead of claiming perfect realism.

Safety rules implemented:

- uses UUIDs internally
- never alters real account authentication
- never changes real UUIDs, permissions, ranks, or operator status
- cleans up control sessions on disconnect and shutdown
- cleans up clone entities and packet listeners on disable
- prevents duplicate or stale ownership maps for clones

## Build and packaging

From the project root:

```bash
mvn package
```

The final jar is generated in:

```text
target/Identity.jar
```

## Notes

This plugin is meant for controlled admin workflows and should be used carefully in a trusted environment. It is designed to be operationally transparent and to avoid modifying real accounts or permissions.
