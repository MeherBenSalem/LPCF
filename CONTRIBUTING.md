# Contributing to LPCF

Thanks for helping improve LPCF (LuckPermsChatFormatterFolia).

## Development

1. Fork and clone the repository.
2. Use JDK 21.
3. Build with the Gradle wrapper:

```bash
./gradlew shadowJar
```

On Windows:

```bat
gradlew.bat shadowJar
```

The shaded plugin jar is written to `build/libs/LuckPermsChatFormatterFolia.jar`.

## Pull requests

- Keep changes focused and related to one concern.
- Match existing package layout, naming, and style.
- Test on Paper or Folia when touching chat, nametags, or scheduling.
- Describe what changed and why in the PR description.

## Issues

Use the issue templates for bugs and feature requests. Include server software (Paper / Folia / Purpur), Minecraft version, plugin version, and relevant logs.

## License

By contributing, you agree that your contributions are licensed under the Apache License, Version 2.0, unless stated otherwise.
