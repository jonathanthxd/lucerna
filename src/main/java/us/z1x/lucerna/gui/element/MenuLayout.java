package us.z1x.lucerna.gui.element;

final class MenuLayout {
    private MenuLayout() {}

    static int columns(int requested, int fallback) {
        return requested >= 1 && requested <= 16 ? requested : fallback;
    }
}
