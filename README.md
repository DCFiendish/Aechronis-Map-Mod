# Aechronis Nation Overlay

A Fabric client mod that adds a live nation/territory overlay to Xaero's
Minimap and World Map, built for the [Nodes](https://github.com/Aechronis/nodes)
town/nation plugin running on [Aechronis](https://aechronis.net/)
(`play.aechronis.net`). It polls the server's public map-data endpoints
(`https://map.aechronis.net/nodes/`) and Minecraft chat to keep the overlay in
sync in near real time — nation-colored territory fills, resource node
borders/labels, town and nation name labels, port markers, and live war
visuals during a siege.

**This mod only does anything on the Aechronis server.** It checks the
server address on join and stays fully inert (no rendering, no network
requests) everywhere else, including singleplayer — safe to keep installed
alongside other servers/modpacks.

## Features

- **Nation territory fills** — every claimed territory tinted by its owning
  nation's color, with the home/core chunk of each territory marked distinctly.
- **Resource node borders & labels** — territory outlines and resource-type
  labels (diamonds, gold, iron, etc.) for nodes with resources.
- **Town & nation labels** — town names at their spawn point; nation names at
  each nation's capital.
- **Buildings** — colored ring markers per building type (port, factory, train
  station), sourced from `nodes/buildings.json`.
- **War visuals**, reconciled from `nodes/war.json` and driven live off
  in-game `[War]` chat messages:
  - *Occupied territories* — a diagonal marker across a captured-but-not-yet-annexed
    territory, in the occupier's color, while the base fill still shows the
    original owner.
  - *Per-chunk war stripes* — a temporary highlight + X-mark on chunks captured
    within the last 90 seconds.
  - *Under-attack stripes* — a diagonal on chunks with a flag currently planted.
- All of the above are individually toggleable and opacity/width-adjustable via
  [Mod Menu](https://modrinth.com/mod/modmenu) / [Cloth Config](https://modrinth.com/mod/cloth-config).

Factory and train station markers are placeholder colored rings for now —
Aechronis's map server has no icon art for these yet, and neither type has
appeared in live `buildings.json` data, so their exact type-string spelling
is unconfirmed. Real icon textures will replace the rings once artwork exists.

## Requirements

- Fabric Loader, Fabric API
- [XaeroPlus](https://modrinth.com/mod/xaeroplus) 2.35.1+fabric-26.2
- [Xaero's Minimap](https://modrinth.com/mod/xaeros-minimap) 26.4.2
- [Xaero's World Map](https://modrinth.com/mod/xaeros-world-map) 1.44.2
- Cloth Config, Mod Menu

## Building

Standard Fabric/Loom build, no manual steps:

```
./gradlew build
```

For IDE setup, see the [Fabric documentation](https://docs.fabricmc.net/develop/getting-started/creating-a-project#setting-up).

## License

CC0-1.0 — see `LICENSE`.
