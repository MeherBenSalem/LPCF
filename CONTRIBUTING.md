# Contributing to LPCF

Thanks for helping improve LPCF.

## Development

1. Fork and clone the repository.
2. Use any JDK 17 or newer. The plugin is compiled against the Paper **1.20.1** API with `--release 17`, so one
   jar runs from 1.20.1 to 26.3. Anything newer than the 1.20.1 API must be looked up at runtime
   (see `TabListService` for an example) and the plugin must keep working without it.
3. Build and test with the Gradle wrapper:

```bash
./gradlew test shadowJar
```

The plugin jar is written to `build/libs/LPCF-<version>.jar`. The version lives in the `VERSION` file.

### Layout

| Package | What |
|---------|------|
| `chat` | Format resolution and placeholders (`ChatFormatPlaceholders`), the player's message (`MessageComponents`), the chat line (`ChatFormatterService`) |
| `display/tag` | Text-display nametags and bubbles (`TextTagService`) and their layout (`TagLayout`) |
| `display/team` | Scoreboard teams: the 1.x nametags (`nametags.style: team`) and hiding the vanilla name |
| `format` | `PlayerFormatter`: any format to a component for one player |
| `tab`, `listener`, `command`, `config` | Tab list, events, `/lpcf`, config loading and `ConfigMigrator` |

Rules of thumb:

* **Folia first.** Touch a player or entity only on its own thread: `SchedulerUtil.run(plugin, entity, ...)`. Paper
  implements the same schedulers, so there is one code path.
* **Never parse what players type** as MiniMessage formats or pass it to PlaceholderAPI. Formats are parsed with the
  name and message inserted as components (`ChatFormatPlaceholders`).
* **Never break configs.** New settings need a default in `config.yml` (with a comment); `ConfigMigrator` adds them to
  existing files. Old keys keep working.
* Logic that does not need a server goes in plain classes with JUnit tests (`src/test`).

## Testing in game

`tools/qa/` runs real servers and real game clients:

| Script | What |
|--------|------|
| `server.py` | Downloads and prepares a Paper, Purpur or Folia server with LuckPerms, PlaceholderAPI and the built jar (`--legacy-config` installs a 1.2.5 config to test the migration) |
| `mcclient.py` | Downloads and launches a vanilla client in offline mode that joins the server |
| `client.py`, `rcon.py` | Drive a client with xdotool and the server over RCON |
| `lpcf_checks.py` | The checks: chat, injection, colours, emoji, mentions, `[ITEM]`, links and hover (JSON), nametags, bubbles, teleports, death, invisibility, spectator, sneaking, live LuckPerms changes, world formats, reload, migration, orphan cleanup, join/quit messages, a clean log |
| `run_matrix.sh` | All of the above on a fresh server per platform and version |
| `marketing_shots.py` | The listing screenshots |

Requirements: Linux with Xvfb, xdotool, ImageMagick (`import`), Mesa (`libgl1-mesa-dri libegl1 libegl-mesa0
libgles2`), Python 3 with PyYAML (and Pillow for `marketing_shots.py`), and JDK 17, 21 and 25 at `/usr/lib/jvm/java-<N>-openjdk-amd64` (servers and clients
run on the Java their Minecraft version needs). About 2 GB of downloads the first time (`$QA_HOME`, default
`/tmp/lpcf-qa`).

```bash
./gradlew shadowJar qaHelperJar             # the plugin and the QA-only helper plugin
tools/qa/run_matrix.sh                      # everything (about an hour)
tools/qa/run_matrix.sh paper:26.3 folia:26.3  # some builds
```

Each build writes `$QA_HOME/results/<platform>-<version>.txt` (one line per check, then `<n> checks, <m> failures`),
the server and client logs, and screenshots. Record the results and the commit in `docs/QA-<version>.md`.

## Pull requests

- Keep changes focused and related to one concern.
- Match the existing package layout, naming and style.
- Run `./gradlew test`, and the QA matrix (or the builds you touched) for anything players see.
- Describe what changed and why in the PR description.

## Issues

Use the issue templates. Include the server software (Paper / Purpur / Folia), Minecraft version, LPCF version, the
output of `/lpcf debug <player>` and relevant logs.

## License

By contributing, you agree that your contributions are licensed under the Apache License, Version 2.0, unless stated
otherwise.
