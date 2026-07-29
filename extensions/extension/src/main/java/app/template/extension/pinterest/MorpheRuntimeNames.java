package app.browzomje.extension.pinterest;

/**
 * Nomi offuscati risolti <b>a build time</b> dalle patch e riscritti dentro questa classe.
 *
 * <p>Qualche hook ha bisogno di costruire un oggetto di una classe di Pinterest il cui nome è
 * offuscato (per esempio la riga "link esterno" delle Impostazioni: {@code i1} su 14.24,
 * {@code j1} su 14.28). Scriverlo qui a mano significherebbe rompere la patch a ogni release.
 *
 * <p>La patch lo trova leggendo il dex dell'APK che sta patchando e lo comunica qui chiamando
 * {@link #setSettingsRowClass(String)}: la chiamata viene iniettata <b>in testa</b> al metodo
 * di Pinterest che costruisce la lista delle Impostazioni, dove il registro v0 è certamente
 * libero (nessuna istruzione lo precede). A runtime l'extension legge solo il campo: nessun
 * nome offuscato è mai hardcodato nel codice Java.
 *
 * <p>Si è scelto un setter invece di far scrivere alla patch direttamente il {@code <clinit>}
 * di questa classe perché il setter è verificabile e non dipende da come il patcher tratta il
 * bytecode delle classi dell'extension in fase di riscrittura del dex.
 *
 * <p>Se un campo resta vuoto significa che quella patch non è stata applicata (o non ha trovato
 * il target): chi lo legge deve trattarlo come errore e loggarlo.
 */
public final class MorpheRuntimeNames {

    /**
     * Classe della riga "link esterno" delle Impostazioni account, quella con costruttore
     * {@code (String url)}. Valorizzata da "Morphe settings entry".
     */
    public static volatile String settingsRowClass = "";

    /** Destinazione della voce "Morphe": lo schema gestito da {@link MorpheSettingsActivity}. */
    public static final String SETTINGS_URI = "morphe://settings";

    private MorpheRuntimeNames() {}

    /** Chiamata iniettata dalla patch "Morphe settings entry". */
    public static void setSettingsRowClass(String className) {
        settingsRowClass = className;
        MorpheLog.i(MorpheLog.SETTINGS_ENTRY, "classe della riga risolta dalla patch: " + className);
    }

    /** Log di controllo: mostra cosa la patch è riuscita a risolvere. */
    public static String describe() {
        return "settingsRowClass=" + (settingsRowClass.isEmpty() ? "<NON RISOLTO>" : settingsRowClass)
                + ", settingsUri=" + SETTINGS_URI;
    }
}
