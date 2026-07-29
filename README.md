# 🧩 browzomje's Morphe Patches

A collection of patches for Android applications, designed for the [Morphe](https://morphe.software) patcher.

## ❓ About

This repository provides custom enhancements and modifications for Android apps, currently supporting: 
- **Pinterest** 

These patches allow you to remove advertisements, block tracking, and add new utility features directly to the apps.

## 🩹 Patches list

<!-- PATCHES_START EXPANDED -->
> **[v1.4.0-dev.1](https://github.com/browzomje/browzomje-patches/releases/tag/v1.4.0-dev.1)**&nbsp;&nbsp;•&nbsp;&nbsp;`dev`&nbsp;&nbsp;•&nbsp;&nbsp;14 patches total
<details open>
<summary>📦 Pinterest&nbsp;&nbsp;•&nbsp;&nbsp;14 patches</summary>
<br>

**🎯 Supported versions:**

| 14.23.0 | 14.28.0 |
| :---: | :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Copy direct link](#copy-direct-link) | Adds a 'Copy direct link' option to the pin menu to copy a link under the direct CDN media format instead of the standard Pinterest web link. |  |
| [Disable ads](#disable-ads) | Removes sponsored (promoted) pins from the home feed and from search/related/board feeds. |  |
| [Disable email confirmation dialog](#disable-email-confirmation-dialog) | Chiude subito il modale "conferma la tua email" (e simili: collega Google, ecc.) se attivato nelle impostazioni Morphe. |  |
| [Download board](#download-board) | Aggiunge una voce al menu "…" della bacheca per scaricare in blocco le immagini e i video dei pin già caricati. I video disponibili solo in streaming vengono segnalati e saltati. |  |
| [Download video](#download-video) | Adds a 'Download video' option to the pin menu for video pins, saving the clip to the Downloads folder. |  |
| [Hide Create nav button](#hide-create-nav-button) | Aggancia il tasto "+" (crea Pin) della barra di navigazione: nascosto se attivato nelle impostazioni Morphe. |  |
| [Hide Notifications nav button](#hide-notifications-nav-button) | Aggancia il tasto delle notifiche: nascosto se attivato nelle impostazioni Morphe. |  |
| [Hide Search nav button](#hide-search-nav-button) | Aggancia il tasto Ricerca: nascosto se attivato nelle impostazioni Morphe. |  |
| [Hide search history](#hide-search-history) | Nasconde la sezione "Ricerche recenti" sia sulla schermata di ricerca sia nel carosello sotto la barra di ricerca. Non impedisce a Pinterest di registrare le ricerche (lato server), ma fa sì che non vengano più mostrate da nessuna parte nell'app. |  |
| [Morphe settings entry](#morphe-settings-entry) | Aggiunge la voce "Morphe" alla lista delle Impostazioni account, per aprire la schermata dei toggle. |  |
| [Morphe settings screen (label)](#morphe-settings-screen-label) | Rinomina la string resource riusata per la voce "Morphe" nelle Impostazioni, in tutte le lingue. |  |
| [Morphe settings screen (manifest)](#morphe-settings-screen-manifest) | Registra l'Activity delle impostazioni Morphe nel manifest, con un intent-filter per lo scheme morphe://. |  |
| [Neutralize advertising ID](#neutralize-advertising-id) | Returns an empty Google Advertising ID and forces ‘limit ad tracking’, disabling ad tracking without causing the app to crash. |  |
| [Set pin as wallpaper](#set-pin-as-wallpaper) | It adds the ‘Set as wallpaper’ option to the pin menu, which downloads uses the image and sets it as the device’s wallpaper. |  |

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
