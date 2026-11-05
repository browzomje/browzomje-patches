# Pinterest porting checks

- Trace each user action from its UI entry point to the final request. Check each experimental menu separately. Two screens with the same label can construct different requests.
- Capture media identity at the action boundary. Prefer the explicit selected image when the caller supplies one. If the caller omits it or supplies the cover, read the visible carousel and verify that its Pin ID matches the request before choosing a page.
- Find new targets by comparing method signatures, preserved strings, resource references, and call sites. Validate the replacement against the original bytecode and preserve the native branch for unsupported or ambiguous inputs.
- Add the smallest injection that preserves the existing control flow and result handling. Inspect register widths and branch targets in the patched DEX. A full-method replacement makes future ports harder because every upstream branch becomes part of the patch.
- Compare the patched APK with its exact original. Separate patch application, target coverage, and Android results. For a carousel regression, download the first image, swipe once and download again, then return to the first page and repeat. The first and last files should match, and the second should match the selected page.
- Keep SDK initialization and event suppression separate when investigating tracking. Verify cold launch and the affected network path before adding a broader override.
