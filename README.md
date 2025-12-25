# HeadSmith

A Minecraft Paper plugin that adds a comprehensive system for custom decorative head blocks with crafting recipes, stonecutter integration, and special interactive properties.

## Features

- **Head Catalog** - Browse hundreds of custom textured heads through an in-game menu
- **Search** - Find heads by name or tags
- **Crafting Recipes** - Define shaped and shapeless recipes for heads
- **Stonecutter Recipes** - Transform materials into heads via stonecutter
- **Configurable Drops** - Control what drops when heads are broken (silk touch support)
- **Special Properties**:
  - Lightable candles with particle effects
  - Glowing heads (pumpkins)
  - Functional blocks that open crafting tables, anvils, enchanting tables, looms, and more

## Requirements

- Java 21
- Paper 1.21+

## Installation

1. Download the latest `HeadSmith-*.jar` from releases
2. Place the JAR in your server's `plugins/` folder
3. Start the server to generate the default configuration
4. Configure head definitions in `plugins/HeadSmith/config.yml`
5. Restart the server or use `/headsmith reload`

## Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/headsmith show` | Open the head catalog menu | `headsmith.catalog` |
| `/headsmith search <query>` | Search for heads by name or tag | `headsmith.catalog` |
| `/headsmith reload` | Reload configuration and head definitions | `headsmith.admin` |
| `/headsmith give <head_id> [player] [amount]` | Give a head to a player | `headsmith.admin` |

## Configuration

Heads are defined in YAML files and loaded via `config.yml`. Each head can have:

- Custom texture (base64 encoded skin data)
- Display name and lore
- Tags for organization and searching
- Special properties (lightable, glowing, workbench, etc.)
- Crafting recipes (shaped/shapeless)
- Stonecutter recipes
- Drop rules with silk touch conditions

See the `headsmith/src/main/resources/heads/` directory for examples.

## Building from Source

```bash
# Full build (generates configs, counts heads, builds JAR)
make build

# Output JAR will be in bin/

# Run a local test server
make server
```

### Prerequisites

- Java 21
- Gradle
- Python 3 with `uv` (for data generation scripts)

## License

MIT
