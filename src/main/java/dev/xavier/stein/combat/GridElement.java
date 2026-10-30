/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.combat;

import dev.xavier.stein.loader.api.HudElement;
import dev.xavier.stein.loader.api.HudPlacement;
import dev.xavier.stein.loader.api.Log;
import dev.xavier.stein.loader.api.Option;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;

/**
 * An element on the Stein Loader's HUD grid: the Loader keeps the place, the scale and whether it
 * is on, and opens the editor; the content and the options live here.
 *
 * <p>The id stays the one the element had in the HUD, whichever mod it lives in: the player's place
 * for it is stored under that id, in the Loader's file.
 */
abstract class GridElement implements HudElement {

    private final String id;

    /** The element's options. */
    final TargetConfig cfg;

    private final SteinCombat mod;

    /** Content size, without scale, computed in layout. */
    int w;

    int h;

    GridElement(String id, TargetConfig cfg, SteinCombat mod) {
        this.id = id;
        this.cfg = cfg;
        this.mod = mod;
    }

    // layout(preview, partialTicks), from the API: builds the content and sets w/h (preview: in the
    // editor, with a sample where data is missing).

    /** Draws at (0, 0), already translated and scaled. */
    abstract void draw(boolean alignRight);

    @Override
    public final void draw(boolean alignRight, float partialTicks) {
        draw(alignRight);
    }

    /** The element's own options, on the options screen. */
    abstract void addOptions(List<Option> out);

    final Log log() {
        return mod.log();
    }

    /** See {@link SteinCombat#textEpoch}. */
    final int textEpoch() {
        return mod.textEpoch;
    }

    @Override
    public final String id() {
        return id;
    }

    @Override
    public final int width() {
        return w;
    }

    @Override
    public final int height() {
        return h;
    }

    @Override
    public final HudPlacement defaultPlacement() {
        return new HudPlacement(
                cfg.anchorX, cfg.anchorY, cfg.x, cfg.y, cfg.scale, cfg.enabled, cfg.showInChat);
    }

    @Override
    public final HudPlacement savedPlacement() {
        return defaultPlacement();
    }

    @Override
    public final void options(List<Option> out) {
        addOptions(out);
    }

    /** The options back to the defaults. Place, scale and enabled are the Loader's to restore. */
    @Override
    public final void restoreOptions() {
        TargetConfig fresh = new TargetConfig();
        for (Field f : TargetConfig.class.getDeclaredFields()) {
            int m = f.getModifiers();
            if (Modifier.isStatic(m) || Modifier.isFinal(m)) {
                continue;
            }
            try {
                f.set(cfg, f.get(fresh));
            } catch (ReflectiveOperationException e) {
                log().warn(id + ": could not restore " + f.getName() + ": " + e);
            }
        }
    }

    @Override
    public final void optionsChanged() {
        mod.save();
    }
}
