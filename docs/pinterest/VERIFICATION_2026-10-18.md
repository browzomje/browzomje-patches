# Pinterest 14.39.0 — verification, 2026-10-18

Original APK SHA-256: `4ecc7f9a34c89fd98d0e2133c294517772857b935969847bfd92b3e25b13e78d`.
Local Gradle build/lint, application of 33 Pinterest patches and APK signature verification passed.

Automated checks passed: complete settings translation coverage (83 keys in 14 languages), multilingual search and accent normalization, settings dependencies/defaults, Save tooltip gate, experimental video classification/filtering, native download bridge, menu action filtering, image metadata/cache/download fallbacks and header parsing. DEX checks verify Home refresh gates and the final Save tooltip gate.

On Android 14, an update with the existing signing certificate succeeded without clearing data. The installed APK hash matched the build output. Settings search appearance, accent-free matching, empty state and clearing the query were checked on the device. Original visible preferences were restored after testing.

The reported “Tap to save this Pin for later” bubble was captured before the fix. After the fix, the runtime log confirmed suppression at final tooltip placement; the bubble was absent. Save still opened the board picker, which was dismissed without saving. The earlier attempt based on an experience ID was removed: the shared education renderer now matches the original APK. Only the unconditional Save-anchor tooltip gate remains. This new patch declares compatibility with 14.39.0 only.

The video filter remains experimental, off by default, and has JVM coverage rather than a completed device matrix. Entirely filtered pages are retained for pagination safety. Do not mark issue #51 fully resolved. The startup-tab request (#52) has not been implemented.

“Keep Home feed” is off for fresh installs and in the recommended preset; explicit saved choices remain honored. Previous device coverage of the Home behavior remains recorded separately.

Reproduce the Python checks using `tests/pinterest/test_*.py`. Run `CheckSaveTooltipDex.java` and `CheckHomeRefreshDex.java` with the Morphe CLI JAR on the Java classpath and explicit original/patched APK arguments. These DEX checks supplement device testing; they do not prove all account experiments or every language layout.
