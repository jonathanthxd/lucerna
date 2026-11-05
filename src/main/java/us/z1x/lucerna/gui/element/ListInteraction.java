package us.z1x.lucerna.gui.element;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Reentrant click dispatch: menu callbacks may synchronously rebuild the rows. */
final class ListInteraction<E> {
    private long generation;
    private E focused;

    void invalidate() {
        generation++;
        focused = null;
    }

    boolean hasFocus() {
        return focused != null;
    }

    boolean click(List<E> entries, int index, Predicate<E> callback) {
        focused = null;
        if (index < 0 || index >= entries.size()) return false;
        E entry = entries.get(index);
        long before = generation;
        boolean handled = callback.test(entry);
        if (handled && before == generation) focused = entry;
        return handled;
    }

    void release(Consumer<E> callback) {
        E entry = focused;
        focused = null;
        if (entry != null) callback.accept(entry);
    }
}
