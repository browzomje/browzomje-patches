# 🧩 browzomje's Morphe Patches

A collection of patches for Android applications, designed for the [Morphe](https://morphe.software) patcher.

## ❓ About

This repository provides custom enhancements and modifications for Android apps, currently supporting: 
- **Pinterest** 

These patches allow you to remove advertisements, block tracking, and add new utility features directly to the apps.

## 🩹 Patches list

<!-- PATCHES_START EXPANDED -->
> **[v1.8.0-dev.8](https://github.com/browzomje/browzomje-patches/releases/tag/v1.8.0-dev.8)**&nbsp;&nbsp;•&nbsp;&nbsp;`dev`&nbsp;&nbsp;•&nbsp;&nbsp;30 patches total
<details open>
<summary>📦 Pinterest&nbsp;&nbsp;•&nbsp;&nbsp;25 patches</summary>
<br>

**🎯 Supported versions:**

| 14.23.0 | 14.28.0 | 14.32.0 |
| :---: | :---: | :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Copy direct link](#copy-direct-link) | Adds a "Copy direct link" option to the pin menu, copying the direct CDN media link instead of the Pinterest web link. |  |
| [Disable AppsFlyer tracking](#disable-appsflyer-tracking) | Neutralises the AppsFlyer attribution SDK, so no install, event or uninstall data leaves the device. |  |
| [Disable Google Engage](#disable-google-engage) | Stops Pinterest publishing your content to Google (Discover, Assistant, Play Store). |  |
| [Disable Google Engage worker](#disable-google-engage-worker) | Blocks the periodic Google Engage job, the other way content reaches Google. |  |
| [Disable ads](#disable-ads) | Removes sponsored pins from the home, search, related and board feeds. Also carries the "Hide product pins" and "Hide board modules in search" filters, both switchable from the Morphe settings screen. |  |
| [Disable email confirmation dialog](#disable-email-confirmation-dialog) | Closes the "confirm your email" modal, and similar ones, when enabled in Morphe settings. |  |
| [Disable third-party trackers](#disable-third-party-trackers) | Turns off the embedded advertising and telemetry SDKs (AdMob, Bugsnag, Firebase, Privacy Sandbox) and strips the Advertising ID permission. Push notifications keep working. |  |
| [Download board](#download-board) | Adds an option to the board's "…" menu to bulk download the images and videos of the loaded pins. |  |
| [Download pin from long press](#download-pin-from-long-press) | Adds a download button to the circular menu you get by long-pressing a pin, so the image can be saved without opening it. |  |
| [Download video](#download-video) | Adds a "Download video" option to the pin menu, saving the clip to the Downloads folder. |  |
| [Hide Create nav button](#hide-create-nav-button) | Hides the "+" (create Pin) navigation button, when enabled in Morphe settings. |  |
| [Hide Notifications nav button](#hide-notifications-nav-button) | Hides the notifications navigation button, when enabled in Morphe settings. |  |
| [Hide Search nav button](#hide-search-nav-button) | Hides the search navigation button, when enabled in Morphe settings. |  |
| [Hide ad views](#hide-ad-views) | Collapses Pinterest's ad-only views, so no ad chrome is drawn even if the app builds one. Follows the "Disable ads" switch. |  |
| [Hide greeting header buttons](#hide-greeting-header-buttons) | Hides the create and inbox buttons in the home feed header, when the matching Morphe settings are enabled. |  |
| [Hide screenshot share menu](#hide-screenshot-share-menu) | Removes the panel Pinterest opens after a screenshot, and stops it watching for screenshots where possible. |  |
| [Hide search history](#hide-search-history) | Hides the "Recent searches" section on the search screen and below the search bar. Pinterest still logs searches server-side. |  |
| [Morphe runtime names](#morphe-runtime-names) | Resolves the class Pinterest builds its toasts with, so Morphe's messages look like the app's own. |  |
| [Morphe settings entry](#morphe-settings-entry) | Adds the "Morphe" item to the Account Settings list to open the toggle screen. |  |
| [Morphe settings screen (label)](#morphe-settings-screen-label) | Renames the reused string resource for the "Morphe" entry in Settings, across all languages. |  |
| [Morphe settings screen (manifest)](#morphe-settings-screen-manifest) | Registers the Morphe settings Activity in the manifest, with an intent-filter for the morphe:// scheme. |  |
| [Neutralize advertising ID](#neutralize-advertising-id) | Returns an empty Google Advertising ID and forces "limit ad tracking", without crashing the app. |  |
| [Sanitize copied links](#sanitize-copied-links) | Turns the pin.it link that "Copy link" copies, whose slug identifies who shared it, into the plain pin link. Switchable from Morphe settings. |  |
| [Sanitize shared links](#sanitize-shared-links) | Strips tracking parameters from the link on the Android share sheet and resolves pin.it short links to the plain pin link. Switchable from Morphe settings. |  |
| [Set pin as wallpaper](#set-pin-as-wallpaper) | Adds a "Set as wallpaper" option to the pin menu, which downloads the image and sets it as the device wallpaper. |  |

</details>

<details open>
<summary>📦 Easy Sudoku&nbsp;&nbsp;•&nbsp;&nbsp;5 patches</summary>
<br>

**🎯 Supported versions:**

| 5.70.0 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Disable ad SDK](#disable-ad-sdk) | Stops the AppLovin MAX mediation stack — and with it Pangle, Amazon APS, Chartboost, InMobi, Mintegral, Fyber, Bigo and the rest — from ever initialising. The game starts faster and makes no ad-network requests at all. Requires "Remove ads": on its own it would only make the app fail to find ads, not stop asking for them. |  |
| [Disable tracking](#disable-tracking) | Hands out an empty Google Advertising ID with "limit ad tracking" turned on, and stops AppsFlyer from sending install attribution and events. The game keeps working normally: nothing in it waits for attribution data. |  |
| [Instant rewards](#instant-rewards) | Grants the reward of every "watch an ad" button — hints, streak repairs, extra puzzles — immediately, without playing a video. This is what keeps those buttons working once "Remove ads" is enabled: without it the game believes no video is available and hands out nothing. |  |
| [Reclaim ad banner space](#reclaim-ad-banner-space) | Collapses the empty strip the banner used to occupy at the bottom of the board, so the puzzle gets the space back instead of staring at a blank rectangle. Only useful together with "Remove ads". |  |
| [Remove ads](#remove-ads) | Turns off every ad Easy Sudoku shows: the home banner, the interstitials between puzzles, the app-open ad and all the background preloading. It works by telling the game its own "ads removed" purchase is already active, which also unlocks the Fun, Ice and Killer Sudoku modes that are otherwise gated behind watching ads. Keep "Instant rewards" enabled too, or the "watch an ad for a hint" buttons stop giving anything. |  |

</details>

<!-- PATCHES_END -->

#### How to use these patches

Click here to add these patches to Morphe: https://morphe.software/add-source?github=browzomje/browzomje-patches

Or manually add this repository url as a patch source in Morphe: https://github.com/browzomje/browzomje-patches

### 📙 Contributing

Thank you for considering contributing to browzomje Morphe Patches.  
You can find the contribution guidelines [here](CONTRIBUTING.md).

### 🛠️ Building

To build browzomje Morphe Patches,
you can follow the [Morphe documentation](https://github.com/MorpheApp/morphe-documentation).

## 📜 License

browzomje Morphe Patches are licensed under the [GNU General Public License v3.0](LICENSE)
