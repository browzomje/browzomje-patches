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
public final class BoardMenuRowFactory {

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
            MorpheLog.e(MorpheLog.BOARD_MENU, "entry " + rowClass.getName()
                    + " does not expose the two expected int getters (position and title id)");
            return null;
        }

        Constructor<?> constructor = widestConstructor(rowClass);
        if (constructor == null) {
            MorpheLog.e(MorpheLog.BOARD_MENU, "no usable constructor on " + rowClass.getName());
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
                copyTypographyFrom(template, arguments, parameterTypes);

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

        MorpheLog.e(MorpheLog.BOARD_MENU, "no combination of parameters yields a consistent entry "
                + "for " + rowClass.getName() + ": entry not added");
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
        MorpheLog.d(MorpheLog.BOARD_MENU, "no icon enum has a " + ICON_NAME + " constant"
                + ": the entry stays without an icon");
    }

    private static void copyTypographyFrom(Object template, Object[] arguments, Class<?>[] parameterTypes) {
        for (int index = 0; index < parameterTypes.length; index++) {
            Class<?> type = parameterTypes[index];
            if (arguments[index] != null || !type.isEnum()) continue;
            Object[] constants = type.getEnumConstants();
            if (constants == null || !containsEnumConstant(constants, "UI_300")) continue;
            for (Method method : template.getClass().getDeclaredMethods()) {
                if (method.getParameterCount() != 0 || method.getReturnType() != type) continue;
                try {
                    method.setAccessible(true);
                    arguments[index] = method.invoke(template);
                } catch (ReflectiveOperationException ignored) {
                    // Pinterest can render the row with its default typography.
                }
                break;
            }
        }
    }

    private static boolean containsEnumConstant(Object[] constants, String name) {
        for (Object constant : constants) {
            if (name.equals(((Enum<?>) constant).name())) return true;
        }
        return false;
    }

    public static Object boardOptionIcon(Enum<?> option, Class<?> iconType) {
        String name;
        switch (option.name()) {
            case "Edit": name = "PENCIL"; break;
            case "Send":
            case "Share": name = "SHARE_ANDROID"; break;
            case "Archive": name = "BOX"; break;
            case "Unarchive": name = "ARROW_UP"; break;
            case "Merge": name = "ARROWS_HORIZONTAL"; break;
            case "Follow": name = "PERSON_PLUS"; break;
            case "Unfollow": name = "PERSON_MINUS"; break;
            case "Report": name = "FLAG"; break;
            case "PreviewBoard": name = "EYE"; break;
            default: return null;
        }
        Object[] constants = iconType.getEnumConstants();
        if (constants != null) {
            for (Object constant : constants) {
                if (name.equals(((Enum<?>) constant).name())) return constant;
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
