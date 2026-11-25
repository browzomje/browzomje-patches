package app.browzomje.extension.pinterest;

public final class SavedPinMenu {
    private SavedPinMenu() {}

    public static boolean hideSave(Enum<?> surface, boolean canManageBoard, boolean hasBoard) {
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
