/*
 * Copyright (c) 2026 Breno Xavier. All rights reserved.
 * Proprietary software: see the LICENSE file at the root of this repository.
 */

package dev.xavier.stein.combat;

import dev.xavier.stein.loader.api.Option;

/**
 * What Combat tells other mods (the HUD's minimap paints players by it). Mods share the Loader's
 * class loader: whoever wants it finds this class by name, and without Combat enabled it does not
 * exist. The class name and its public members are a contract: they never change.
 *
 * <p>Kept for mods compiled against it (the HUD up to 0.1.6). The current way is the Loader's:
 * {@code api.Relations} and {@code api.Players}, which Combat feeds.
 */
public final class CombatApi {

    /** Combat not started, or the player themselves. */
    public static final int NONE = 0;

    /** On the allies list or in an allied clan, but not in my clan. */
    public static final int ALLY = 1;

    /** In my clan. */
    public static final int CLAN = 2;

    /** In another clan that is not allied. */
    public static final int ENEMY = 3;

    /** No clan and no alliance: neither enemy nor ally. Since 0.1.1. */
    public static final int NEUTRAL = 4;

    /** The running Combat, or null before it starts. */
    static volatile SteinCombat mod;

    private CombatApi() {}

    /**
     * The relation of the player with that nick to the local player: {@link #NONE}, {@link #CLAN},
     * {@link #ALLY}, {@link #ENEMY} or {@link #NEUTRAL}.
     *
     * <p><b>Enemy only with proof.</b> Red on screen is a warning, and a wrong warning is worse
     * than none: players trust it to decide whom to hit. So only those marked by hand by the player
     * are enemies, or those the <b>clan plugin</b> puts in a clan other than mine — and even then
     * only if I have a clan, because the fight is between clans and whoever has none is in no
     * fight.
     *
     * <p>Brackets in the name ({@code "[ABC] Nick"}) still serve to <b>recognize my own</b>, and
     * never to paint someone as an enemy: on an ordinary server {@code [VIP]}, {@code [ADM]} and
     * {@code [PRO]} have the same three letters as a clan tag, and that is how half the list became
     * an enemy without being one.
     */
    public static int relation(String nick) {
        SteinCombat m = mod;
        if (m == null || nick == null) {
            return NONE;
        }
        // Cached in Combat while lists, options, my nick and clans do not change (the nametag asks
        // every frame).
        return m.teams.relation(nick);
    }

    /**
     * The clan tag of the player with that nick ("ABC"), or null with no clan. It comes from the
     * plugin's "sc_TAG" team and, with the option on, from the bracketed prefix. It is what lets
     * another mod group by clan (the HUD's Tab list).
     */
    public static String clan(String nick) {
        return mod == null ? null : Clans.of(nick);
    }

    /** The allies' color chosen in Combat (RGB). */
    public static int allyColor() {
        SteinCombat m = mod;
        return m == null ? Option.RGB[10] : rgb(m.config.allyColor);
    }

    /**
     * The color of this player's nick (RGB), by the relation and Combat's four colors: my clan,
     * ally, neutral and enemy. It is the same number the Tab list, the head and the minimap use, so
     * the three agree. With colors off, or without Combat, white. Since 0.1.1.
     */
    public static int colorOf(String nick) {
        SteinCombat m = mod;
        if (m == null || !m.config.colorNames) {
            return 0xFFFFFF;
        }
        return rgb(colorCode(m.config, relation(nick)));
    }

    /** The game's formatting code ('a', '9'...) as RGB. */
    static int rgb(char code) {
        return Option.rgb(String.valueOf(code));
    }

    /**
     * The formatting code of this player's nick, for whoever writes game text (the Tab list and the
     * head).
     */
    static char code(String nick) {
        SteinCombat m = mod;
        if (m == null || !m.config.colorNames) {
            return 'f';
        }
        return colorCode(m.config, relation(nick));
    }

    private static char colorCode(CombatConfig c, int relation) {
        return switch (relation) {
            case CLAN -> c.clanColor;
            case ALLY -> c.allyColor;
            case ENEMY -> c.enemyColor;
            default -> c.neutralColor;
        };
    }
}
