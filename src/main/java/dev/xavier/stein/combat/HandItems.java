/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.combat;

import dev.xavier.stein.loader.api.Inventory;
import dev.xavier.stein.loader.api.Stacks;

/**
 * What Smart Effect and Auto Protect look for in the inventory: weapons, swords, apples and
 * potions.
 */
final class HandItems {

    static final int SPEED = 1;
    static final int STRENGTH = 5;
    static final int REGENERATION = 10;

    private HandItems() {}

    /** Any sword in the hotbar or the bag ("Low health block" only starts with one). */
    static boolean hasSword() {
        for (int i = 0; i < Inventory.ARMOR_START; i++) {
            if (isSword(Inventory.stack(i))) {
                return true;
            }
        }
        return false;
    }

    static boolean isSword(Object s) {
        String id = s == null ? null : Stacks.id(s);
        return id != null && id.endsWith("_sword");
    }

    static boolean isWeapon(Object s) {
        String id = s == null ? null : Stacks.id(s);
        return id != null && (id.endsWith("_sword") || id.endsWith("_axe") || id.endsWith("bow"));
    }

    static int firstWeapon() {
        for (int i = 0; i < 9; i++) {
            if (isWeapon(Inventory.stack(i))) {
                return i;
            }
        }
        return -1;
    }

    /** The item (hotbar first, then the bag) that gives the effect; -1 with none. */
    static int find(int effect) {
        for (int i = 0; i < Inventory.ARMOR_START; i++) {
            Object s = Inventory.stack(i);
            if (s != null && matches(s, effect)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Does the item give the effect? Regeneration: the enchanted golden apple. Speed and Strength:
     * drinkable potions only, never splash ones.
     */
    static boolean matches(Object s, int effect) {
        String item = Stacks.id(s);
        if (item == null) {
            return false;
        }
        if (effect == REGENERATION) {
            return item.endsWith("golden_apple") && Stacks.damage(s) == 1;
        }
        if (!item.endsWith("potion") || Stacks.isSplashPotion(s)) {
            return false;
        }
        for (int e : Stacks.potionEffects(s)) {
            if (e == effect) {
                return true;
            }
        }
        return false;
    }

    /** The lang key of the effect's item, for the "no item" notice. */
    static String nameKey(int effect) {
        return effect == REGENERATION
                ? "steincombat.effect.gapple"
                : effect == SPEED ? "steincombat.effect.speed" : "steincombat.effect.strength";
    }
}
