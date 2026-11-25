package app.browzomje.extension.pinterest;

public final class SavedPinMenu {
    private SavedPinMenu() {}

    public static boolean canRepin(boolean nativeAllowed, Enum<?> surface, boolean canManageBoard, boolean hasBoard) {
        return nativeAllowed && !hideSave(surface, canManageBoard, hasBoard);
    }

    static boolean hideSave(Enum<?> surface, boolean canManageBoard, boolean hasBoard) {
        if (surface == null || !canManageBoard || !hasBoard) return false;
        switch (surface.name()) {
            case "BOARD":
            case "BOARD_SECTION":
            case "USER_PINS":
                return true;
            default:
                return false;
        }
    }
}
