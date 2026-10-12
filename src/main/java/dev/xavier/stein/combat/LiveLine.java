/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.combat;

import dev.xavier.stein.loader.api.Option;

import java.util.function.Supplier;

/**
 * A Panel row with live text: the label on the left, whatever {@code value} says at the moment on
 * the right, and the click runs {@code run}. It is "Refresh now", which must show the fetch's
 * status while it runs — {@link Option#action} keeps a fixed text, chosen when the page was built.
 */
final class LiveLine extends Option {

    private final Supplier<String> value;
    private final Runnable run;

    LiveLine(String label, Supplier<String> value, Runnable run) {
        super(label);
        this.value = value;
        this.run = run;
    }

    @Override
    public String value() {
        return value.get();
    }

    @Override
    public void click(boolean forward) {
        run.run();
    }
}
