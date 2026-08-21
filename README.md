# Aechronis Nation Overlay

A Fabric client mod that adds a live nation overlay to Xaero's
Minimap and World Map, built for the [Nodes](https://github.com/Aechronis/nodes)
town/nation plugin running on [Aechronis](https://aechronis.net/)
(`play.aechronis.net`). 

**This mod only does anything on the Aechronis server.** It checks the
server address on join and stays fully inert (no rendering, no network
requests) everywhere else, including singleplayer — safe to keep installed
alongside other servers/modpacks.

## Features

- **Nation territory fills** — every claimed territory tinted by its owning
  nation's color, with the home/core chunk of each territory marked distinctly.
- **Resource node borders & labels** — territory outlines and resource-type
  labels (diamonds, gold, iron, etc.) for nodes with resources. Icons are planned and this is toggleable in mod menu.
- **Town & nation labels** — town names at their spawn point; nation names at
  each nation's capital.
- **Buildings** — colored ring markers per building type (port, factory),
  sourced from `nodes/buildings.json`. Real icons (same technique as the train
  station icon below) are planned next.
- **Train network** — a minecart icon (size adjustable) at each station on
  both the minimap and world map; station labels (id, quality tier, banned
  flag) and route lines between connected stations on the world map.
- **Chunk border relation coloring (F3+G)** — recolors chunk borders by your
  relation to the chunk's owner (own town, own nation, ally, enemy, neutral),
  each color configurable.
- **Auto `/t spawn` on respawn** — optional, off by default.
- **War visuals**, reconciled from `nodes/war.json` and driven live off
  in-game `[War]` chat messages:
  - *Occupied territories* — a diagonal marker across a captured-but-not-yet-annexed
    territory, in the occupier's color, while the base fill still shows the
    original owner.
  - *Per-chunk war stripes* — a highlight + X-mark on chunks captured this
    siege, in the capturing nation's color. Stays until the chunk changes
    hands again, the whole territory is captured/annexed, or a several-hour
    backstop timeout.
  - *Under-attack stripes* — a diagonal on chunks with a flag currently planted.
- All of the above are individually toggleable and opacity/width-adjustable via
  [Mod Menu](https://modrinth.com/mod/modmenu) / [Cloth Config](https://modrinth.com/mod/cloth-config).
  Each text-label category also has its own toggle keybind (unbound by
  default — assign one in the vanilla Controls menu).
- Middle Mouse clicking on a node in your world map will send a chat message with info about the node.
- **World Map search** — type a town name or node id into the search box and press Enter to drop a temporary waypoint at its core.
- **Zoom-based decluttering** — resource labels, node border lines, train
  station labels, and town labels progressively hide as you zoom out on the
  world map (and minimap), so the map stays readable instead of turning into
  a wall of text. Nation labels are always shown.

Factory markers are a placeholder colored ring for now — Aechronis's map server
has no icon art for it yet, and it has never appeared in live `buildings.json`
data, so its exact type-string spelling is unconfirmed. Real icon textures will
replace the ring once artwork exists.

## Requirements

- Fabric Loader, Fabric API
- [XaeroPlus](https://modrinth.com/mod/xaeroplus) 2.35.1+fabric-26.2
- [Xaero's Minimap](https://modrinth.com/mod/xaeros-minimap) 26.4.2
- [Xaero's World Map](https://modrinth.com/mod/xaeros-world-map) 1.44.2
- Cloth Config, Mod Menu

Standard Fabric/Loom build, no manual steps:

```
./gradlew build
```

For IDE setup, see the [Fabric documentation](https://docs.fabricmc.net/develop/getting-started/creating-a-project#setting-up).

## License

CC0-1.0 — see `LICENSE`.
