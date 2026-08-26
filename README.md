# LPCF

LuckPerms chat and nametag formatter for **Paper**, **Folia**, and **Purpur** (Minecraft **1.20.1–26.2**).

Requires [LuckPerms](https://luckperms.net/). [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) is optional.

## Features

- MiniMessage and legacy (`&` / `&#RRGGBB`) chat formats
- Group- and track-specific formats
- Nametag / tab list formatting from LuckPerms meta
- Optional PlaceholderAPI placeholders
- `[ITEM]` chat placeholder
- Folia-safe region scheduling
- bStats metrics
- Optional Modrinth update check

## Commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/lpcf reload` | `lpcf.reload` | Reload configuration |

## Permissions

- `lpcf.reload` — reload config (also receives update notices)
- `lpcf.colorcodes` — MiniMessage in chat messages
- `lpcf.itemplaceholder` — use `[ITEM]` in chat

## Installation

1. Install LuckPerms (and optionally PlaceholderAPI).
2. Drop `LuckPermsChatFormatterFolia.jar` into `plugins/`.
3. Restart the server and edit `plugins/LuckPermsChatFormatterFolia/config.yml`.

Download releases from [Modrinth](https://modrinth.com/plugin/lpcf-chat-formatter) (project id `cq7XqCTD`).

## Build

JDK 21 required:

```bash
./gradlew shadowJar
```

Output: `build/libs/LuckPermsChatFormatterFolia.jar`

## Publishing (maintainers)

GitHub Actions publish on tags `v*.*.*` needs secrets `MODRINTH_TOKEN`, `CURSEFORGE_TOKEN`, `CURSEFORGE_API_KEY` and variables `MODRINTH_ID=cq7XqCTD`, `CURSEFORGE_ID`.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) and the [Code of Conduct](CODE_OF_CONDUCT.md).

## Security

See [SECURITY.md](.github/SECURITY.md).

## License

Licensed under the [Apache License, Version 2.0](LICENSE).
