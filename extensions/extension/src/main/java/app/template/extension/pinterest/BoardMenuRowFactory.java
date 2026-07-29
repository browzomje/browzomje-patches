package app.browzomje.extension.pinterest;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Costruisce una riga per il menu della bacheca clonando una riga già presente.
 *
 * <p>Si clona invece di costruire una classe nostra perché la view che disegna quelle righe
 * mostra icona e sottotitolo solo dentro un {@code instanceof} sul tipo concreto di Pinterest.
 *
 * <p>Il costruttore ha una dozzina di parametri con nomi offuscati: si passano valori neutri
 * (null / 0) a tutti tranne quelli che servono, e si verifica il risultato rileggendo l'oggetto
 * appena creato dai suoi stessi getter. Se non torna, si prova la combinazione successiva.
 */
final class BoardMenuRowFactory {

    /**
     * `getIndex()` è l'unico metodo dell'interfaccia delle voci che R8 non rinomina, essendo un
     * getter di proprietà Kotlin con la convenzione JavaBeans. Serve a distinguere i due interi
     * del costruttore: posizione e id della stringa del titolo.
     */
    private static final String INDEX_GETTER = "getIndex";

    /**
     * Costante dell'enum delle icone Gestalt da usare per la riga. È lo stesso nome già usato
     * dalle righe che Morphe costruisce nel menu del pin.
     */
    private static final String ICON_NAME = "DOWNLOAD";

    private BoardMenuRowFactory() {}

    /**
     * @param template una voce già presente nel menu, usata sia come modello di classe sia come
     *     riferimento di stile.
     * @param titleResource id della string resource da mostrare come titolo.
     * @param index posizione della riga nella lista: è il valore con cui Pinterest richiamerà la
     *     callback di click.
     * @return la nuova riga, o null se non è stato possibile costruirne una verificabile.
     */
    static Object cloneWithTitle(Object template, int titleResource, int index) {
        if (template == null) {
            return null;
        }
        Class<?> rowClass = template.getClass();

        Method indexGetter = noArgIntGetter(rowClass, INDEX_GETTER);
        Method titleGetter = titleResourceGetter(rowClass);
        if (indexGetter == null || titleGetter == null) {
            MorpheLog.e(MorpheLog.BOARD_MENU, "la voce " + rowClass.getName()
                    + " non espone i due getter interi attesi (posizione e id del titolo)");
            return null;
        }

        Constructor<?> constructor = widestConstructor(rowClass);
        if (constructor == null) {
            MorpheLog.e(MorpheLog.BOARD_MENU, "nessun costruttore utilizzabile su " + rowClass.getName());
            return null;
        }
        constructor.setAccessible(true);

        Class<?>[] parameterTypes = constructor.getParameterTypes();
        List<Integer> intPositions = new ArrayList<>();
        for (int i = 0; i < parameterTypes.length; i++) {
            if (parameterTypes[i] == int.class) {
                intPositions.add(i);
            }
        }

        // Le posizioni si provano in ordine: nelle versioni viste finora titolo e indice sono i
        // primi due parametri, quindi il primo tentativo è di norma anche l'ultimo.
        for (int titlePosition : intPositions) {
            for (int indexPosition : intPositions) {
                if (titlePosition == indexPosition) {
                    continue;
                }
                Object[] arguments = defaultArguments(parameterTypes);
                arguments[titlePosition] = titleResource;
                arguments[indexPosition] = index;
                applyIcon(arguments, parameterTypes);
                copyStyleFrom(template, arguments, parameterTypes);

                try {
                    Object row = constructor.newInstance(arguments);
                    boolean matches = (Integer) indexGetter.invoke(row) == index
                            && (Integer) titleGetter.invoke(row) == titleResource;
                    if (matches) {
                        return row;
                    }
                } catch (Throwable ignored) {
                    // Combinazione sbagliata: il costruttore può rifiutare i valori o produrre
                    // un oggetto incoerente. Si passa alla successiva.
                }
            }
        }

        MorpheLog.e(MorpheLog.BOARD_MENU, "nessuna combinazione di parametri produce una voce "
                + "coerente per " + rowClass.getName() + ": voce non aggiunta");
        return null;
    }

    /**
     * Mette l'icona di download nel parametro che la ospita.
     *
     * <p>L'icona è una costante di un enum di Pinterest (quello delle icone Gestalt): il nome
     * della classe è offuscato, i nomi delle costanti no. Si cerca quindi fra i parametri quello
     * il cui tipo è un enum che contiene una costante chiamata {@code DOWNLOAD}.
     *
     * <p>Se non la si trova non è un problema: la riga esce senza icona, che è comunque meglio
     * di nessuna riga.
     */
    private static void applyIcon(Object[] arguments, Class<?>[] parameterTypes) {
        for (int i = 0; i < parameterTypes.length; i++) {
            if (!parameterTypes[i].isEnum()) {
                continue;
            }
            Object[] constants = parameterTypes[i].getEnumConstants();
            if (constants == null) {
                continue;
            }
            for (Object constant : constants) {
                if (ICON_NAME.equals(((Enum<?>) constant).name())) {
                    arguments[i] = constant;
                    return;
                }
            }
        }
        MorpheLog.d(MorpheLog.BOARD_MENU, "nessun enum icona con una costante " + ICON_NAME
                + ": la voce resta senza icona");
    }

    /**
     * Ricopia dalla voce modello i valori non nulli dei parametri che non stiamo impostando noi.
     *
     * <p>Serve per la <b>variante tipografica</b>: senza, la nostra riga usciva con un carattere
     * diverso da quello delle voci accanto, perché quel parametro restava null e la view cadeva
     * sul proprio default. Copiandolo dalla voce sorella la riga diventa indistinguibile dalle
     * altre — che è esattamente il motivo per cui si clona invece di costruire da zero.
     *
     * <p>In questo menu l'unico valore non nullo è appunto quello: le altre voci non hanno né
     * icona né sottotitolo. Se una versione futura ne aggiungesse, al più erediteremmo qualcosa
     * di cosmetico da una voce vicina — visibile subito e mai un crash.
     */
    private static void copyStyleFrom(Object template, Object[] arguments, Class<?>[] parameterTypes) {
        for (int i = 0; i < parameterTypes.length; i++) {
            if (arguments[i] != null || parameterTypes[i].isPrimitive()) {
                continue; // già impostato da noi, oppure è un numero/booleano
            }
            Object value = firstNonNullValueOfType(template, parameterTypes[i]);
            if (value != null) {
                arguments[i] = value;
            }
        }
    }

    /**
     * @return il valore restituito dal primo getter senza argomenti del modello il cui tipo di
     *     ritorno è esattamente {@code type}, se non è null.
     */
    private static Object firstNonNullValueOfType(Object template, Class<?> type) {
        for (Method method : template.getClass().getDeclaredMethods()) {
            if (method.getParameterTypes().length != 0 || method.getReturnType() != type) {
                continue;
            }
            try {
                method.setAccessible(true);
                Object value = method.invoke(template);
                if (value != null) {
                    return value;
                }
            } catch (Throwable ignored) {
                // Getter non invocabile: si prova il prossimo.
            }
        }
        return null;
    }

    /**
     * @return il getter dell'id di risorsa del titolo: l'unico metodo senza argomenti che
     *     restituisce un {@code int} e non è né {@code getIndex()} né {@code hashCode()}.
     */
    private static Method titleResourceGetter(Class<?> rowClass) {
        for (Method method : rowClass.getDeclaredMethods()) {
            if (method.getParameterTypes().length == 0
                    && method.getReturnType() == int.class
                    && !INDEX_GETTER.equals(method.getName())
                    && !"hashCode".equals(method.getName())) {
                method.setAccessible(true);
                return method;
            }
        }
        return null;
    }

    private static Method noArgIntGetter(Class<?> rowClass, String name) {
        for (Method method : rowClass.getDeclaredMethods()) {
            if (name.equals(method.getName())
                    && method.getParameterTypes().length == 0
                    && method.getReturnType() == int.class) {
                method.setAccessible(true);
                return method;
            }
        }
        return null;
    }

    private static Constructor<?> widestConstructor(Class<?> rowClass) {
        Constructor<?> widest = null;
        for (Constructor<?> candidate : rowClass.getDeclaredConstructors()) {
            if (widest == null || candidate.getParameterTypes().length > widest.getParameterTypes().length) {
                widest = candidate;
            }
        }
        return widest;
    }

    /**
     * Valori neutri per ogni parametro: 0 per i numeri, false per i booleani, null per gli
     * oggetti.
     *
     * <p>Sui costruttori generati da Kotlin per i parametri con valore di default, uno degli
     * interi è la maschera "quali parametri sono stati omessi". Lasciandola a 0 si dichiara di
     * fornirli tutti esplicitamente, ed è ciò che vogliamo: i null che passiamo restano null,
     * esattamente come nelle righe che Pinterest costruisce per questo stesso menu.
     */
    private static Object[] defaultArguments(Class<?>[] parameterTypes) {
        Object[] arguments = new Object[parameterTypes.length];
        for (int i = 0; i < parameterTypes.length; i++) {
            Class<?> type = parameterTypes[i];
            if (!type.isPrimitive()) {
                arguments[i] = null;
            } else if (type == boolean.class) {
                arguments[i] = false;
            } else if (type == int.class) {
                arguments[i] = 0;
            } else if (type == long.class) {
                arguments[i] = 0L;
            } else if (type == float.class) {
                arguments[i] = 0f;
            } else if (type == double.class) {
                arguments[i] = 0d;
            } else if (type == short.class) {
                arguments[i] = (short) 0;
            } else if (type == byte.class) {
                arguments[i] = (byte) 0;
            } else if (type == char.class) {
                arguments[i] = (char) 0;
            }
        }
        return arguments;
    }
}
