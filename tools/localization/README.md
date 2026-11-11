# Translations

Hirameki builds on AnkiDroid's community translations (made on
[Crowdin](https://crowdin.com/project/ankidroid)) and adds its own on top.

## Where translations live

Each `AnkiDroid/src/main/res/values-<locale>/` folder holds two kinds of file:

| File | Owner | Edit by hand? |
| --- | --- | --- |
| `01-core.xml` … `20-search-preference.xml` | Generated from upstream AnkiDroid by `sync_upstream_translations.py` | No, the next sync overwrites them |
| `strings.xml` | Hirameki-only strings (the English is in `values/strings.xml`) | Yes |
| `overrides.xml` | Corrections or new translations of strings that also exist upstream. These win over upstream. | Yes |
| `ai-fill.xml` | Unreviewed machine translations. Once upstream translates one of these strings, the sync removes it here and uses upstream's. | Yes |

Android rejects a string defined twice in one folder, so the sync leaves out any string a hand-edited file defines.
Moving a string from `ai-fill.xml` to `overrides.xml` marks it as reviewed, so upstream no longer replaces it.

## Syncing from upstream

```bash
git fetch upstream
python tools/localization/sync_upstream_translations.py --locale fa            # report only
python tools/localization/sync_upstream_translations.py --locale fa --write
python tools/localization/sync_upstream_translations.py --all --write          # every locale
```

The script reads `upstream/main` first, then the upstream commit Hirameki forked from. The fork point still has
translations for strings upstream has since deleted. An upstream translation is only used when:

- Hirameki still has a translatable string with that name,
- upstream's English matches Hirameki's, ignoring case, whitespace and quote style (`--strict` turns this off),
- it is a real translation, not Crowdin's English copy of an untranslated string, and
- it uses the same format arguments (`%s`, `%1$d`) as Hirameki's English.

If the only difference in the English is "AnkiDroid" → "Hirameki", the translation is renamed too when it spells
AnkiDroid in Latin script. Otherwise the report lists it as "only renamed", and the fix belongs in `overrides.xml`.

Useful flags:

- `-v` lists every upstream string that was not used, with the reason, and every string the sync removed.
- `--list-missing` lists what is still in English, with the English text, as a to-do list for `strings.xml` and
  `overrides.xml`.

## Adding a string

Add the English to `values/strings.xml` (for Hirameki features) or the relevant numbered file. If you change the
English of an existing upstream string, its upstream translations stop matching. The next sync drops them until
someone adds translations to `overrides.xml`.
