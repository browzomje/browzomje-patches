# Home feed and comment controls

## Keep Home feed

Available on Pinterest **14.39.0**. The Morphe setting is enabled by default and
by the recommended preset. It appears only when the required hooks are present.

- Tapping the active Home tab scrolls the existing grid to the top without
  replacing its feed.
- Returning from Search or Saved keeps the feed and its scroll position.
- Pull-to-refresh remains available. Turning the setting off restores Pinterest's
  original Home refresh behavior.

The patch gates three automatic refresh emissions and the separate Home
reselection callback. Each call uses a typed bridge; the shared event bus and
presenter refresh method stay unchanged. The first device test exposed the
separate reselection path, which is covered by the final implementation.

This setting does not promise to preserve a feed across process death, logout,
or a fresh application launch. Older Pinterest versions are not declared
compatible with this new patch.

## Comment visibility

The two settings have different purposes:

- **Hide all comments** removes the section, preview and comment button.
- **Hide comment button** hides only the speech bubble below the Pin.

They now appear together with an explanation. The button-only control is
unavailable while all comments are hidden or image information replaces the
buttons. Its saved choice is preserved and becomes available again when those
conditions are removed. Existing preference keys are retained.

## Validation

Tested on Pinterest 14.39.0, Android 14:

- Active Home reselection returns to the original first Pins without replacing them.
- Search and Saved round trips retain the visible Pins and their positions.
- Search round trips after 40 and 310 seconds retain the same Pins and bounds;
  hook diagnostics confirm automatic refresh suppression.
- Pull-to-refresh loads different Pins with protection enabled.
- Scrolling further shows additional Pins. This is not a claim of unlimited
  remote pagination testing.
- Turning the setting off restores Home refresh, confirmed by changed Pins and
  the `original refresh allowed` diagnostic. The setting was restored to ON.
- Update installation preserved application data and login. No uninstall was used.

These checks used UI snapshots and hook diagnostics, not a full network capture.
Build/lint, patch application and APK signature verification also passed. Device
snapshots and account content remain local and are not included in this repository.

The original APK used for analysis has SHA-256
`4ecc7f9a34c89fd98d0e2133c294517772857b935969847bfd92b3e25b13e78d`.

From this repository root, run the settings regression test:

```bash
python3 tests/pinterest/test_settings_dependencies.py
```

After applying the bundle, verify the generated DEX with a compatible Morphe CLI
JAR containing dexlib2:

```bash
java -cp /path/to/morphe-cli-all.jar tests/pinterest/CheckHomeRefreshDex.java /path/to/original.apk /path/to/patched.apk
```

This checks all four modified calls, caller frames and branches, the typed
bridges, native scroll-to-zero, the unchanged shared bus/presenter, the OFF
path and the settings capability gate. It complements the device tests.
