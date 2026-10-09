/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.combat;

import java.util.List;
import java.util.function.Predicate;

/**
 * As funcionalidades do Combat que ligam e desligam, na ordem do Painel: o nome (o mesmo no Painel, no aviso de ligar e
 * desligar e na lista do HUD), a categoria (a cor na lista) e se esta ligada agora. Um lugar so: a lista do HUD
 * ({@link ActiveFeatures}) e o "Mostrar na lista" de cada uma saem daqui.
 */
final class Features {

    static final int COMBAT = 0;
    static final int MOVEMENT = 1;
    static final int TEAM = 2;
    static final int AUTOMATION = 3;

    /** A cor de cada categoria na lista: combate vermelho, movimento azul, equipe verde, automacao violeta. */
    static final int[] COLORS = {0xFFFF5F5F, 0xFF5FB4FF, 0xFF5FE08A, 0xFFB98CFF};

    static final class Feature {
        /** O id gravado em {@link CombatConfig#featuresHidden}. */
        final String id;
        final String nameKey;
        final int category;
        final Predicate<SteinCombat> on;

        Feature(String id, String nameKey, int category, Predicate<SteinCombat> on) {
            this.id = id;
            this.nameKey = nameKey;
            this.category = category;
            this.on = on;
        }
    }

    static final List<Feature> ALL = List.of(
            new Feature("noHitDelay", "steincombat.noHitDelay", COMBAT, m -> m.config.noHitDelay),
            new Feature("autoclick", "steincombat.autoclick", COMBAT, m -> m.config.autoclick),
            new Feature("toggleSprint", "steincombat.toggleSprintName", MOVEMENT, m -> m.toggled),
            new Feature("keepSprint", "steincombat.sprintOnHit", MOVEMENT, m -> m.config.sprintOnHit),
            new Feature("omniSprint", "steincombat.allDirections", MOVEMENT, m -> m.config.allDirections),
            new Feature("clanProtect", "steincombat.protect", TEAM, m -> m.config.protectAllies),
            new Feature("hiddenAllies", "steincombat.hide", TEAM, m -> m.hiding),
            new Feature("autoArmor", "steincombat.autoArmor", AUTOMATION, m -> m.config.autoArmor),
            new Feature("autoRefill", "steincombat.autoRefill", AUTOMATION, m -> m.config.autoRefill),
            new Feature("smartEffect", "steincombat.smartEffect", AUTOMATION, m -> m.config.smartEffect),
            new Feature("autoProtect", "steincombat.autoProtect", AUTOMATION, m -> m.config.autoProtect));

    private Features() {
    }

    static Feature byId(String id) {
        for (Feature f : ALL) {
            if (f.id.equals(id)) {
                return f;
            }
        }
        return null;
    }
}
