# Specialization definitions

Specialization definitions are server data resources. Put each definition at
`data/<namespace>/villager_potential/specializations/<name>.json` in either a
mod or a data pack. Reloading server resources atomically replaces the loaded
definition set; malformed or duplicate profession definitions reject the reload.

The version 1 format is independent of Minecraft and loader APIs so the same
files can be consumed by a future Forge 1.20.1 adapter:

```json
{
  "format_version": 1,
  "profession": "minecraft:librarian",
  "general_specialization": "villager_potential:librarian/general",
  "specializations": [
    {
      "id": "villager_potential:librarian/enchanter",
      "trade_categories": {
        "villager_potential:enchanted_books": 2.0,
        "villager_potential:ordinary_books": 0.5
      }
    }
  ]
}
```

All IDs must be explicit lowercase namespaced IDs. Category values are finite,
non-negative multiplicative weight modifiers. An omitted category has the
neutral modifier `1.0`; zero reserves the ability to disable a category. These
definitions are loaded and exposed now, but do not yet affect trade selection.
