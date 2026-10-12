/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.combat;

import dev.xavier.stein.loader.api.Players;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * A player's clan tag, read from the scoreboard: the {@code sc_ABC} team of this project's
 * SteinCombat server plugin or, on servers with another clan plugin, the team prefix ({@code "[ABC]
 * Nick"} in the Tab list; an option, on by default). On a server that uses the prefix for ranks
 * ({@code "[VIP]"}), the player turns the option off.
 */
final class Clans {

    /** The SteinCombat plugin's teams on the scoreboard: "sc_" + tag. */
    private static final String CLAN_TEAM = "sc_";

    /** How long a looked-up clan stays cached. */
    private static final long CACHE_NANOS = 1_000_000_000L;

    /**
     * A player's clan and <b>where it came from</b>. The difference matters: the plugin's {@code
     * sc_TAG} team is proof (a clan plugin wrote it), while brackets in the name are only a hint —
     * on an ordinary server {@code [VIP]}, {@code [ADM]} and {@code [PRO]} have the same three
     * letters as a clan tag.
     */
    static final class Clan {
        static final Clan NONE = new Clan(null, false);

        final String tag;

        /**
         * It came from this project's clan plugin (an sc_TAG team), not from brackets in the name.
         */
        final boolean proven;

        Clan(String tag, boolean proven) {
            this.tag = tag;
            this.proven = proven;
        }
    }

    /** "No clan" is a valid answer and is cached too (hence containsKey, not computeIfAbsent). */
    private static final Map<String, Clan> CACHE = new HashMap<>();

    private static long cacheAt;

    private Clans() {}

    /**
     * The player's clan tag, cached for a second. The real lookup reads the scoreboard and the Tab
     * list, and finding a nick in the Tab list walks every player: the HUD's Tab list asks for each
     * player's relation and clan every frame, which became one walk per player per frame (with 200
     * on the list, tens of thousands of iterations per frame — that is what froze a held Tab).
     */
    static String of(String nick) {
        return info(nick).tag;
    }

    /** The same, with where it came from (see {@link Clan}). */
    static Clan info(String nick) {
        epoch();
        if (CACHE.containsKey(nick)) {
            return CACHE.get(nick);
        }
        Clan clan = lookup(nick);
        CACHE.put(nick, clan);
        return clan;
    }

    /**
     * Starts a new round of the clan cache if the current one is older than a second, and returns
     * when the current round started: the number changes with every new round, and that is how the
     * relation cache ({@link Teams}) knows the clans may have changed.
     */
    static long epoch() {
        long now = System.nanoTime();
        if (now - cacheAt > CACHE_NANOS) {
            CACHE.clear();
            cacheAt = now;
        }
        return cacheAt;
    }

    /** My clan's tag on this server, or null. */
    static String mine() {
        return Players.self() == null ? null : of(Players.self());
    }

    private static Clan lookup(String nick) {
        if (nick == null || Players.self() == null) {
            return Clan.NONE;
        }
        String team = Players.team(nick);
        SteinCombat mod = CombatApi.mod;
        boolean fromName = mod == null || mod.config.clanFromPrefix;
        if (team != null) {
            if (team.startsWith(CLAN_TEAM)) {
                // This project's clan plugin.
                return new Clan(tag(team.substring(CLAN_TEAM.length())), true);
            }
            if (fromName) {
                String prefix = bracketTag(Players.prefix(nick));
                if (prefix != null) {
                    // Another plugin, through the team prefix.
                    return new Clan(prefix, false);
                }
            }
        }
        // The most common case: the server uses no team and puts the clan in the Tab name itself
        // ("[ABC] Nick"). Players.displayName is null when the server sends no name of its own.
        String named = fromName ? bracketTag(Players.displayName(nick)) : null;
        return named == null ? Clan.NONE : new Clan(named, false);
    }

    /**
     * A typed clan tag ("abc" or "[ABC]") in upper case, or null if it is not a tag.
     *
     * <p><b>Exactly three</b> letters or digits: it is the server's format, and it is what tells a
     * clan apart from anything else in brackets in a name ("[VIP]" has three and counts, but
     * "[ADMIN]" or "[PV]" are not clans).
     */
    static String tag(String raw) {
        if (raw == null) {
            return null;
        }
        String t = raw.trim();
        if (t.startsWith("[") && t.endsWith("]") && t.length() >= 2) {
            t = t.substring(1, t.length() - 1).trim();
        }
        return t.matches("[A-Za-z0-9]{3}") ? t.toUpperCase(Locale.ROOT) : null;
    }

    /** The bracketed tag of a team prefix ("§b[ABC] " -> "ABC"), or null. */
    static String bracketTag(String prefix) {
        if (prefix == null) {
            return null;
        }
        String clean = prefix.replaceAll("§.", "").trim();
        int open = clean.indexOf('[');
        int close = clean.indexOf(']', open + 1);
        return open >= 0 && close > open ? tag(clean.substring(open, close + 1)) : null;
    }

    /** A Minecraft nick: 1 to 16 letters, digits or underscores. */
    static boolean validNick(String nick) {
        return nick != null && nick.matches("[A-Za-z0-9_]{1,16}");
    }
}
