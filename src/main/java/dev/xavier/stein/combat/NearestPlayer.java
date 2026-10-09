/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.combat;

import java.util.List;
import java.util.Locale;
import dev.xavier.stein.loader.api.Entities;
import dev.xavier.stein.loader.api.Lang;
import dev.xavier.stein.loader.api.HudElement;
import dev.xavier.stein.loader.api.Option;
import dev.xavier.stein.loader.api.HudPlacement;
import dev.xavier.stein.loader.api.Render;

/**
 * Distancia do jogador conhecido mais perto de um lado, na grade do HUD: o inimigo mais proximo (quem nao e aliado nem
 * do meu cla, {@link CombatApi}) ou o aliado mais proximo (aliado ou do meu cla) — dois elementos, um de cada. So os
 * que o cliente conhece — o que esta longe demais nao chega ate ele —, e invisivel nao conta. Sem ninguem por perto, o
 * elemento nao aparece.
 */
final class NearestPlayer implements HudElement {

    private final SteinCombat mod;
    /** O do aliado mais proximo; falso, o do inimigo. */
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
        String distance = String.format(Locale.ROOT, "%.1f m", near != null ? best : 12.3F).replace('.', ',');
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
        // O do aliado logo abaixo do do inimigo.
        return new HudPlacement(1, 0, 0, ally ? 54 : 40, 1.0F, true, true);
    }

    @Override
    public float defaultBackground() {
        return 0.5F;
    }

    @Override
    public void options(List<Option> out) {
        if (ally) {
            out.add(Option.toggle(Lang.tr("steincombat.hud.name"), () -> mod.config.nearestAllyName,
                    v -> mod.config.nearestAllyName = v));
        } else {
            out.add(Option.toggle(Lang.tr("steincombat.hud.name"), () -> mod.config.nearestName,
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
