package app.browzomje.patches.pinterest.pin_menu.copy_link

import app.morphe.patcher.Fingerprint

// Il costruttore del menu "…" del pin sta in OverflowMenuFingerprint.kt, condiviso con
// "Set pin as wallpaper" e "Download video": era triplicato e inchiodato a nomi offuscati.
