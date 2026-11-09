# Third-party code

AI Builders is licensed under the **GNU General Public License v3.0** (see `LICENSE`).
Parts of it are copied or adapted from the projects below. Every adapted file starts with a
header naming its source file and original license. The original copyright notices apply to
those parts; our modifications are released under GPL-3.0.

| Project | Revision used | License | Used for |
|---|---|---|---|
| [cabaletta/baritone](https://github.com/cabaletta/baritone) | `bbaa520` (branch 1.21.4) | LGPL-3.0 | A* path finder, movement cost model, builder goal selection |
| [ldtteam/structurize](https://github.com/ldtteam/structurize) | `f66c19c` (version/main) | GPL-3.0 | Placement handlers (per-block item cost and placement rules) |
| [ldtteam/minecolonies](https://github.com/ldtteam/minecolonies) | `b2fb29ec` (version/main) | GPL-3.0 | Staged building order, resource buckets and availability states |
| [maruohon/litematica](https://github.com/maruohon/litematica) | `dc426db` (pre-rewrite/fabric/1.21.1-masa) | LGPL-3.0 | .litematic block entities/entities/ticks, verifier result types, placement JSON |
| [kolbytn/mindcraft](https://github.com/kolbytn/mindcraft) | `e1c1fc3` (develop) | MIT | Agent memory summary, goal loop, bot-to-bot conversation rules, prompt texts |

Code was adapted to Minecraft 26.x (Mojang names, `ValueInput`/`ValueOutput`), to a server-side
mob instead of a player, and with client, Forge/NeoForge and malilib dependencies removed.

Mindcraft (MIT): Copyright (c) 2024 Kolby Nottingham. Permission is hereby granted, free of
charge, to any person obtaining a copy of this software and associated documentation files, to
deal in the Software without restriction, subject to the following condition: the above
copyright notice and this permission notice shall be included in all copies or substantial
portions of the Software. THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND.
