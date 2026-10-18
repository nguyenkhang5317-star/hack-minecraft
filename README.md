# Hack Minecraft

Fabric client utility mod for Minecraft Java Edition 1.21.1. The automated build installs Temurin Java 21 and creates the mod JAR through GitHub Actions.

## Features and default keys

| Feature | Key |
| --- | --- |
| Auto Totem | P |
| Fullbright gamma setting | L |
| Auto Sprint | G |
| Auto Eat | H |
| Auto Tool | I |
| Auto Respawn | R |
| Coordinates and world-time HUD | U |
| Armor durability HUD | D |
| FPS, food and health HUD | F |
| Ping HUD | N |
| Anti-AFK jump | Y |

Change keys in Minecraft: Options > Controls > Key Binds > Hack Minecraft.

## Build

1. Open the repository's Actions tab.
2. Select **Build Fabric Mod** and run **Run workflow**, or push a commit to `main`.
3. Open the successful workflow run and download the `hack-minecraft-fabric-1.21.1` artifact.
4. Put the JAR into the Minecraft 1.21.1 `mods` folder with Fabric Loader and Fabric API installed.

The workflow installs Java 21 for the build; the JDK itself is not committed to the repository. This project focuses on utility and HUD features and does not include automated crystal or anchor attacks, automatic attack targeting, or anti-cheat bypass code.
