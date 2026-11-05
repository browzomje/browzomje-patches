# Morphe patch development

## Source and target selection

- Kotlin patches live under `patches/src/main/kotlin/app/browzomje/patches/`. Runtime helpers and settings live under `extensions/extension/src/main/java/`. Read the affected patch, its helpers, and callers before changing a hook.
- Use an original, explicitly versioned APK. Check package name, version, and hash, then scan known anchors. Decompile locally and inspect only the relevant classes. Resolve menu actions from resource names to IDs and call sites. Cross-check jadx aliases against raw DEX descriptors before pinning a class.
- Start with stable strings, resource IDs, non-obfuscated classes, signatures, enum fields, and instruction patterns. Verify both the owner and the behavior of each match. Use a version-specific descriptor only when a stable fingerprint would be ambiguous. Record how to find it again and keep older target mappings working.
- Check shared compatibility and every feature-specific target or version guard. A successful patch application can omit optional hooks or whole code paths. Read warnings and compare required hook coverage with the previous release before advertising support.

## Bytecode and runtime changes

- Reuse the shared injection helpers. Verify register count, wide parameters, parameter liveness, result registers, and instruction format. Capture a constructor receiver immediately after its superclass constructor and defer view work until the view is ready.
- Preserve native inputs, callbacks, and fallback behavior. For media actions, use the current selection supplied by the app and keep the identity tied to the request. Validate data before replacing the native action. Cover old saved state and check persistence and retry paths when changing background work.
- Keep fixture values synthetic or deliberately public. Keep device logs, app dumps, screenshots, credentials, APKs, and extracted upstream source out of commits. Use neutral English for commits and public documentation.

## Test workflow

1. Run `./gradlew build` with the configured JDK, Android SDK, and package dependencies. It compiles the bundle and runs lint.
2. Run the standalone regression tests from this repository root with `for test in tests/pinterest/test_*.py; do python3 "$test" || exit; done`. These run production extension code with JVM Android stubs. Extend the relevant test when fixing a deterministic regression. Use stubbed or local HTTP fixtures and assert output, fallback, and failure behavior.
3. Apply the bundle to the exact original APK. Run `python3 tests/pinterest/check_patch_log.py <patch-log>` for a full Pinterest 14.39.0 or 14.43.0 build. It checks the patch inventory and selected hook counts. Update its version-specific expectations when deliberately adding or retiring a patch. Verify the signature separately.
4. Use the `tests/pinterest/Check*Dex.java` entry points with the Morphe CLI fat JAR on the Java classpath to compare original and patched APKs. Read the entry point's argument list before running it. For example, `java -cp "$MORPHE_CLI_JAR" tests/pinterest/CheckVideoFeedDex.java "$ORIGINAL_APK" "$PATCHED_APK" "$VERSION"`. Check the intended invoke target, argument registers, and preservation of adjacent native code.
5. Run Android smoke tests on a test device when available. See `tests/pinterest/android_image_smoke.py --help` for the opt-in real download test. For carousel fixtures, compare the resulting image bytes against a known selected-page hash. For server experiments, account-dependent screens, or unavailable fixtures, record the scenario as unverified. A green build or DEX check only proves static coverage.

Document relevant anchors, commands, and verified behavior in the patch notes. In review, state separately which tests ran on the JVM, which inspected DEX, and which exercised a device.

## Cross-version fingerprint triage

Rank candidates using stable strings, references, instruction shapes, and call sites. Validate the result against known old-to-new transitions and inspect the original DEX before changing a fingerprint. Similar methods can rank close together, especially append and replace operations. Keep reports with local artifacts.

Run `python3 tests/pinterest/check_settings_locales.py <original-locales-config.xml>` after editing settings text or updating the target. It reports declared Pinterest tags using English fallback and regional variants that share a language table. The JVM catalog test separately checks keys, nonblank strings, placeholders, and search aliases.
See [Pinterest localization](docs/pinterest/LOCALIZATION.md) for the offline catalog validator, staged pre-commit hook, and locale inventory check.
