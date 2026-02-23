<p align="center"><img src="logo.png"></p>

# OreAnnouncer
<b>OreAnnouncer</b> is a plugin for Minecraft servers that collects data whenever a player digs an ore (can be set with any block).  
You can send an alert to users/admins or just store every block they destroy to easily track x-ray players.

[Official project page here!](https://alessiodp.com/oreannouncer/)

## Downloads
[Download from Spigot](http://www.spigotmc.org/resources/oreannouncer.33464/)  
[Download from Bukkit](https://dev.bukkit.org/projects/oreannouncer)

## Documentation
[Documentation page here](https://alessiodp.com/docs/oreannouncer).

## PRDX Fork

This is a fork maintained by [digitaldrugstech](https://github.com/digitaldrugstech) for use on the [prdx.so](https://prdx.so) Minecraft network.

### Changes from upstream

- **Folia support** — runtime detection with reflection-based scheduler, thread-safe block tracking via packed coordinates (replaces Bukkit metadata API)
- **Java 17** — updated from Java 8
- **Spigot API 1.21.4** — updated from 1.20.1 (minimum server version: 1.21.4)
- **Bug fixes:**
  - Fixed `parseMessage()` placeholder chaining (light/height level placeholders were applied to wrong string)
  - Fixed `%maxpages%` placeholder missing closing `%` in `/oa top`
  - Thread-safe `PlayerManager` (`ConcurrentHashMap` instead of `HashMap`)
  - Safe bounds check in `getLastTwoTargetBlocks()`
  - Removed MC 1.8 dead code (`getItemInHand()` fallback)

### Building

```bash
# Requires Java 17
mvn clean package

# Output JAR
ls output/target/OreAnnouncer-*.jar
```

### Testing with Folia

```bash
mvn clean package
docker compose -f docker-compose.folia-test.yml up
```

## License
[AGPL-3.0](https://github.com/AlessioDP/OreAnnouncer/blob/master/LICENSE) — same as upstream.