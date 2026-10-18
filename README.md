# Hack Minecraft

Fabric client mod for Minecraft Java Edition 1.21.1. The automated build installs Temurin Java 21 and produces a JAR through GitHub Actions.

## Features and default keys

| Feature | Key |
| --- | --- |
| ESP entity boxes | O |
| Tracers | T |
| Auto Totem | P |
| Auto Crystal | K |
| Auto Anchor | J |
| Fullbright gamma setting | L |
| Auto Sprint | G |
| Auto Eat | H |
| Auto Tool | I |
| Triggerbot | B |
| Auto Respawn | R |
| Coordinates and player radar HUD | U |
| Anti-AFK jump | Y |

Change keys in Minecraft: Options > Controls > Key Binds > Hack Minecraft.

## Build

1. Open the repository's Actions tab.
2. Select **Build Fabric Mod** and run **Run workflow**, or push a commit to `main`.
3. Open the successful workflow run and download the `hack-minecraft-fabric-1.21.1` artifact.
4. Put the JAR into the Minecraft 1.21.1 `mods` folder with Fabric Loader and Fabric API installed.

The workflow installs Java 21 for the build; the JDK itself is not committed to the repository.

This project does not include anti-cheat bypass or stealth-evasion code. Use client automation only where permitted by the world or server rules.
