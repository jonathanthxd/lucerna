package us.z1x.lucerna.gui.element;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Runs without Minecraft, a GL context, JUnit, or downloaded dependencies. */
public final class ListInteractionRegression {
    private static int checks;
    private static void check(boolean result) {
        checks++;
        if (!result) throw new AssertionError("Check " + checks + " failed");
    }

    public static void main(String[] args) {
        ListInteraction<Integer> state = new ListInteraction<>();
        List<Integer> entries = new ArrayList<>(List.of(0, 1, 2, 3, 4, 5, 6, 7, 8));
        AtomicInteger released = new AtomicInteger();
        check(state.click(entries, 8, entry -> {
            entries.clear();
            state.invalidate();
            entries.addAll(List.of(0, 1, 2, 3, 4));
            return true;
        }));
        check(!state.hasFocus());
        state.release(entry -> released.incrementAndGet());
        check(released.get() == 0);
        check(!state.click(entries, 8, entry -> { throw new AssertionError("stale index"); }));
        check(!state.click(entries, -1, entry -> true));
        check(!state.click(entries, entries.size(), entry -> true));
        check(!state.click(List.of(), 0, entry -> true));
        check(state.click(entries, 3, entry -> true));
        check(state.hasFocus());
        state.release(entry -> released.addAndGet(entry));
        check(released.get() == 3);
        check(!state.hasFocus());
        state.release(entry -> { throw new AssertionError("duplicate release"); });
        check(state.click(entries, 1, entry -> true));
        check(!state.click(entries, 2, entry -> false));
        check(!state.hasFocus());
        for (int i = 0; i < 1000; i++) {
            check(state.click(entries, 0, entry -> {
                state.invalidate(); // profile/pack/submenu/resolution rebuild, even reusing a row
                return true;
            }));
            check(!state.hasFocus());
        }
        for (int invalid : new int[] {Integer.MIN_VALUE, -1, 0, 17, Integer.MAX_VALUE}) {
            check(MenuLayout.columns(invalid, 2) == 2);
        }
        for (int valid = 1; valid <= 16; valid++) check(MenuLayout.columns(valid, 2) == valid);
        System.out.println("List interaction/layout regression checks passed: " + checks);
    }
}
