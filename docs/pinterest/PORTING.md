# Pinterest porting checks

- Trace each user action from its UI entry point to the final request. Check each experimental menu separately. Two screens with the same label can construct different requests.
- Capture media identity at the action boundary. Prefer the explicit selected image when the caller supplies one. If the caller omits it or supplies the cover, read the visible carousel and verify that its Pin ID matches the request before choosing a page.
- Find new targets by comparing method signatures, preserved strings, resource references, and call sites. Validate the replacement against the original bytecode and preserve the native branch for unsupported or ambiguous inputs.
- Add the smallest injection that preserves the existing control flow and result handling. Inspect register widths and branch targets in the patched DEX. A full-method replacement makes future ports harder because every upstream branch becomes part of the patch.
- Compare the patched APK with its exact original. Separate patch application, target coverage, and Android results. For a carousel regression, download the first image, swipe once and download again, then return to the first page and repeat. The first and last files should match, and the second should match the selected page.
- Keep SDK initialization and event suppression separate when investigating tracking. Verify cold launch and the affected network path before adding a broader override.

## Repeatable verification

1. Run the JVM regression suite and the offline locale validator. Include
   fixtures for the normal path, unsupported input and the fallback.
2. Apply the same bundle to the current target and the previous supported
   baseline. Save each patch log and keep all APKs and logs local.
3. Run the static checks on each original and patched pair.

   ```bash
   bash tests/pinterest/verify_apk.sh VERSION ORIGINAL.apk PATCHED.apk PATCH.log MORPHE_CLI.jar APKSIGNER
   ```

   Add `--firebase-registration` before the version when the optional Firebase
   patch is enabled. The command checks the expected patch inventory, APK
   signature and DEX hooks and prints hashes for the input, output and log.
4. Run device checks with the exact candidate APK. The public fixture ID,
   selectors and swipe coordinates are local inputs to
   `tests/pinterest/android_image_smoke.py`. Use `--carousel` with a forward
   `--swipe` to verify first, second and first again in one run. Supply the
   second image's `--sha256` when the fixture and quality setting are pinned.
5. Keep a short coverage record by feature and entry point. Record a missing
   experiment, account or fixture as skipped. A passed bytecode check and a
   passed device test are separate results.

For new hooks, start with negative fixtures for ambiguous matches and changed
register layouts. Keep device automation opt-in and bind every result to the
installed APK. Use synthetic or deliberately public inputs and leave raw
device output in the private workspace.
