# 🧩 browzomje's Morphe Patches

A collection of patches for Android applications, designed for the [Morphe](https://morphe.software) patcher.

## ❓ About

This repository provides custom enhancements and modifications for Android apps, currently supporting: 
- **Pinterest** 

These patches allow you to remove advertisements, block tracking, and add new utility features directly to the apps.

## 🩹 Patches list

<!-- PATCHES_START EXPANDED -->
> **[v1.5.2](https://github.com/browzomje/browzomje-patches/releases/tag/v1.5.2)**&nbsp;&nbsp;•&nbsp;&nbsp;`main`&nbsp;&nbsp;•&nbsp;&nbsp;19 patches total
<details open>
<summary>📦 Pinterest&nbsp;&nbsp;•&nbsp;&nbsp;14 patches</summary>
<br>

**🎯 Supported versions:**

| 14.23.0 | 14.28.0 |
| :---: | :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Copy direct link](#copy-direct-link) | Adds a 'Copy direct link' option to the pin menu to copy a link under the direct CDN media format instead of the standard Pinterest web link. |  |
| [Disable ads](#disable-ads) | Removes sponsored (promoted) pins from the home feed and from search/related/board feeds. Also carries the optional "Hide product pins" filter, which is off by default and is turned on from the Morphe settings screen. |  |
| [Disable email confirmation dialog](#disable-email-confirmation-dialog) | Immediately closes the "confirm your email" modal (and similar ones: connect Google, etc.) if enabled in Morphe settings. |  |
| [Download board](#download-board) | Adds an option to the board's "…" menu to bulk download images and videos from already loaded pins. Streaming-only videos are reported and skipped. |  |
| [Download video](#download-video) | Adds a 'Download video' option to the pin menu for video pins, saving the clip to the Downloads folder. |  |
| [Hide Create nav button](#hide-create-nav-button) | Hooks the '+' (create Pin) navigation bar button: hidden if enabled in Morphe settings. |  |
| [Hide Notifications nav button](#hide-notifications-nav-button) | Hooks the notifications button: hidden if enabled in Morphe settings. |  |
| [Hide Search nav button](#hide-search-nav-button) | Hooks the search button: hidden if enabled in Morphe settings. |  |
| [Hide search history](#hide-search-history) | Hides the "Recent searches" section both on the search screen and in the carousel below the search bar. It does not prevent Pinterest from logging searches (server-side), but stops them from being shown anywhere in the app. |  |
| [Morphe settings entry](#morphe-settings-entry) | Adds the "Morphe" item to the Account Settings list to open the toggle screen. |  |
| [Morphe settings screen (label)](#morphe-settings-screen-label) | Renames the reused string resource for the "Morphe" entry in Settings, across all languages. |  |
| [Morphe settings screen (manifest)](#morphe-settings-screen-manifest) | Registers the Morphe settings Activity in the manifest, with an intent-filter for the morphe:// scheme. |  |
| [Neutralize advertising ID](#neutralize-advertising-id) | Returns an empty Google Advertising ID and forces ‘limit ad tracking’, disabling ad tracking without causing the app to crash. |  |
| [Set pin as wallpaper](#set-pin-as-wallpaper) | It adds the ‘Set as wallpaper’ option to the pin menu, which downloads uses the image and sets it as the device’s wallpaper. |  |

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
