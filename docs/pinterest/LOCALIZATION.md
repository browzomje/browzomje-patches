# Pinterest localization

The settings screen and the extension's menu actions and notices use the same catalog. Native action labels still come from Pinterest. The catalog maps the 44 Android locale tags declared by Pinterest 14.43.0 to 38 translation tables. Region-specific English and Spanish reuse the corresponding language table. Portuguese for Brazil and Portugal and the two Chinese scripts have separate tables.

Edit the JSON files in `extensions/extension/src/main/l10n/`. Add a key to English and every table when adding an extension string. Keep the meaning of menu and download states distinct. Keep placeholders such as `%d`, `%1`, and `%2`, and preserve command and format names. The locale map uses Android resource tags on the left and translation filenames on the right. Use an APK's decoded locale configuration to check the inventory when porting.

```sh
python3 scripts/pinterest_l10n.py
python3 scripts/pinterest_l10n.py --locale-config /path/to/locales_config.xml
python3 tests/pinterest/test_settings_search.py
git config --local core.hooksPath .githooks
```

The pre-commit hook checks the staged snapshot when relevant files change. It runs without Gradle, an APK, a device, or network access. The JVM suite checks locale selection and multilingual search. Gradle generates Java from the validated catalog, so Python 3 must be available on the build host. The generated Java belongs in the build output.

Use concise UI language. Distinguish loaded Pins from an entire board, queued transfers from completed downloads, and hiding content locally from deleting account data. Supplemental translations use offline machine translation with targeted terminology corrections. Native-speaker and device feedback can be applied to a single JSON table.
