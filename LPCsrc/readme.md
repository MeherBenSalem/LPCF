# LuckPermsChatFormatterFolia

Folia-compatible LuckPerms chat formatter by NightBeam.

## Features

- MiniMessage chat formatting
- Legacy Minecraft ampersand formatting (`&0-9`, `&a-f`, `&k-o`, `&r`, `&x` hex, and `&#RRGGBB`)
- Group-specific chat formats
- Track-specific chat formats
- Optional PlaceholderAPI support
- [ITEM] placeholder support
- Folia-safe scheduler-based chat handling

## Commands

- `/lpcf reload` reloads the plugin configuration

## Permissions

- `lpcf.reload`
- `lpcf.colorcodes`
- `lpcf.itemplaceholder`

## Build

Run from the project directory:

```bash
gradle shadowJar
```

Output jar:

- `build/libs/LuckPermsChatFormatterFolia.jar`
