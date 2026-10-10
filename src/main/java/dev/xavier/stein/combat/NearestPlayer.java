/*
 * Copyright (c) 2026 Breno Xavier. All rights reserved.
 * Proprietary software: see the LICENSE file at the root of this repository.
 */

package dev.xavier.stein.combat;

import dev.xavier.stein.loader.api.Entities;
import dev.xavier.stein.loader.api.HudElement;
import dev.xavier.stein.loader.api.HudPlacement;
import dev.xavier.stein.loader.api.Lang;
import dev.xavier.stein.loader.api.Option;
import dev.xavier.stein.loader.api.Render;

import java.util.List;
import java.util.Locale;

/**
 * The distance to the nearest known player of one side, on the HUD grid: the nearest enemy (neither
 * ally nor in my clan, {@link CombatApi}) or the nearest ally (ally or in my clan) — two elements,
 * one of each. Only the players the client knows — whoever is too far away does not reach it —, and
 * invisible ones do not count. With nobody around, the element does not show.
 */
final class NearestPlayer implements HudElement {

    private final SteinCombat mod;

    /** The nearest ally's element; false, the nearest enemy's. */
    private final boolean ally;

    private String text = "";

    NearestPlayer(SteinCombat mod, boolean ally) {
        this.mod = mod;
        this.ally = ally;
    }

    @Override
    public String id() {
        return ally ? "steincombat.nearestAlly" : "steincombat.nearest";
    }

    @Override
    public String name() {
        return Lang.tr(ally ? "steincombat.hud.nearestAlly" : "steincombat.hud.nearest");
    }

    private boolean wanted(Object p) {
        int r = CombatApi.relation(Entities.name(p));
        return ally ? r == CombatApi.ALLY || r == CombatApi.CLAN : r == CombatApi.ENEMY;
    }

    @Override
    public boolean layout(boolean preview, float partialTicks) {
        Object me = Entities.self();
        Object near = null;
        float best = Float.MAX_VALUE;
        if (me != null) {
            for (Object p : Entities.players()) {
                if (p == me || Entities.isInvisible(p) || !wanted(p)) {
                    continue;
                }
                float d = Entities.distance(me, p);
                if (d < best) {
                    best = d;
                    near = p;
                }
            }
        }
        if (near == null && !preview) {
            return false;
        }
        String nick = near != null ? Entities.name(near) : Lang.tr("steinloader.hud.sample.player");
        String distance =
                String.format(Locale.ROOT, "%.1f m", near != null ? best : 12.3F).replace('.', ',');
        String color = ally ? "§" + mod.config.allyColor : "§c";
        boolean name = ally ? mod.config.nearestAllyName : mod.config.nearestName;
        text = name ? color + nick + " §f" + distance : color + distance;
        return true;
    }

    @Override
    public int width() {
        return Render.textWidth(text) + 2;
    }

    @Override
    public int height() {
        return 12;
    }

    @Override
    public void draw(boolean alignRight, float partialTicks) {
        Render.text(text, 1, 2, 0xFFFFFFFF, true);
    }

    @Override
    public HudPlacement defaultPlacement() {
        // The ally's right below the enemy's.
        return new HudPlacement(1, 0, 0, ally ? 54 : 40, 1.0F, true, true);
    }

    @Override
    public float defaultBackground() {
        return 0.5F;
    }

    @Override
    public void options(List<Option> out) {
        if (ally) {
            out.add(
                    Option.toggle(
                            Lang.tr("steincombat.hud.name"),
                            () -> mod.config.nearestAllyName,
                            v -> mod.config.nearestAllyName = v));
        } else {
            out.add(
                    Option.toggle(
                            Lang.tr("steincombat.hud.name"),
                            () -> mod.config.nearestName,
                            v -> mod.config.nearestName = v));
        }
    }

    @Override
    public void restoreOptions() {
        CombatConfig d = new CombatConfig();
        if (ally) {
            mod.config.nearestAllyName = d.nearestAllyName;
        } else {
            mod.config.nearestName = d.nearestName;
        }
    }

    @Override
    public void optionsChanged() {
        mod.save();
    }
}
