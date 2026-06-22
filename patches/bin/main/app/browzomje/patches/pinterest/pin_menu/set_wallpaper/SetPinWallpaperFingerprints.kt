package app.template.patches.pinterest.pin_menu.set_wallpaper

import app.morphe.patcher.Fingerprint

/*
 * Tutti i nomi offuscati qui sotto sono validi per com.pinterest 14.23.0 (14238020),
 * la stessa build estratta in pinterest_extracted/. Le patch sono pinnate a questa
 * versione (vedi Constants.COMPATIBILITY_PINTEREST), quindi è corretto ancorarsi ai
 * nomi offuscati specifici di questo dump.
 *
 * Come sono stati trovati (da pinterest_extracted/sources/):
 *
 *   # il provider del menu overflow del pin
 *   rg -n "new m\(|OverflowMenuModalProviderImpl" \
 *      com/pinterest/feature/gridactions/modal/view/OverflowMenuModalProviderImpl.java
 *   # -> create() ritorna new gp1.m(...), il cui createPresenter() ritorna new tz0.g(...)
 *
 *   # il presenter che POPOLA le voci: costruisce ArrayList<qz0.b> e fa overflowMenu.addView(...)
 *   rg -n "new qz0\.b\(|OverflowMenu|addView" tz0/g.java
 *
 * Modello voce di menu (verificato):
 *   qz0.b(int textResId, int iconResId, qz0.a selectionListener)   // qz0.a = interface { void m(); }
 * Render (in tz0.g.o3): per ogni qz0.b crea una riga e fa overflowMenu.addView(riga).
 *
 * La nostra iniezione NON crea un qz0.b (servirebbe un qz0.a): aggiunge direttamente una
 * riga alla OverflowMenu (che è una LinearLayout) tramite l'extension. Vedi RECAP.md.
 */

/**
 * uz0.z constructor (il menu view che aggiunge direttamente tutte le voci tramite addView).
 *
 * Perché il costruttore e non onModalContentContainerCreated:
 *   - Le voci del menu (Copia link, Scarica immagine, ecc.) vengono aggiunte a uz0.z
 *     direttamente nel costruttore (ciclo while su una lista di sz0.a).
 *   - onModalContentContainerCreated viene chiamato DOPO che il ModalViewWrapper ha già
 *     wrappato uz0.z in un ScrollView e chiamato bind() sul presenter; aggiungere lì
 *     potrebbe non triggerare correttamente il re-layout del bottom sheet.
 *   - Iniettando alla FINE del costruttore (prima del return-void), aggiungiamo la nostra
 *     voce DOPO tutte le voci standard, con p0 già completamente inizializzato.
 *
 * Il costruttore ha 28 parametri — usato per disambiguare rispetto ad eventuali costruttori
 * ausiliari generati da Kotlin.
 */
object OverflowMenuBuilderFingerprint : Fingerprint(
    custom = { method, classDef ->
        classDef.type == "Luz0/z;" &&
            method.name == "<init>" &&
            method.parameters.size == 28
    }
)

/*
 * ---------------------------------------------------------------------------------------
 * Hook di cattura immagine del pin.
 *
 * Sul closeup il bitmap viene caricato tramite l'interfaccia/metodo e0(Bitmap, a0)
 * ereditato da GenericWebImageView. Intercettiamo questo metodo per catturare il
 * bitmap e passarlo a WallpaperUtils.setCurrentPinBitmap(...).
 * ---------------------------------------------------------------------------------------
 */
object PinCloseupBitmapFingerprint : Fingerprint(
    parameters = listOf("Landroid/graphics/Bitmap;", "Ld12/a0;"),
    returnType = "V",
    custom = { method, classDef ->
        classDef.type == "Lcom/pinterest/ui/imageview/GenericWebImageView;" &&
            method.name == "e0"
    }
)
