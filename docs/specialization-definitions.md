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
definitions are loaded and exposed now, but their weights do not yet affect
trade selection.
When a villager first enters a profession, one named specialization is selected
and stored on that profession's career. An empty `specializations` array uses
the definition's `general_specialization`; a profession with no definition uses
`villager_potential:general`.

## Built-in vanilla trade categories

On NeoForge 1.21.1, the live vanilla `ItemListing` candidates are wrapped with
classification metadata while the original listing remains the offer-producing
delegate. This preserves deferred behavior such as enchanted-book tag and level
selection. Entries which cannot be matched safely, including unknown mod-added
factories, use `villager_potential:general`.

The built-in stable category keys are:

| Area | Keys |
| --- | --- |
| Farmer and butcher | `crops`, `prepared_food`, `raw_meat`, `cooked_food` |
| Fisherman | `fish`, `fishing_supplies` |
| Shepherd | `wool`, `dyes`, `decor` |
| Fletcher | `archery_supplies`, `arrows`, `bows`, `crossbows` |
| Librarian | `ordinary_books`, `enchanted_books` |
| Cartographer | `maps`, `cartography_supplies`, `decor` |
| Cleric | `alchemy` |
| Smiths | `armor`, `weapons`, `tools`, `smithing_materials` |
| Leatherworker | `leather_materials`, `leather_goods` |
| Mason | `stonework`, `terracotta`, `quartz` |

Every key above is in the `villager_potential` namespace. Miscellaneous vanilla
entries such as bells, name tags, and inputs that do not belong safely to a
specialization category are explicitly `villager_potential:general`.
