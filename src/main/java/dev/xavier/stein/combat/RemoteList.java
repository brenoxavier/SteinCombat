/*
 * Copyright (c) 2026 Breno Xavier. All rights reserved.
 * Proprietary software: see the LICENSE file at the root of this repository.
 */

package dev.xavier.stein.combat;

import dev.xavier.stein.loader.api.Lang;
import dev.xavier.stein.loader.api.Server;
import dev.xavier.stein.loader.api.Tasks;

/**
 * The lists from the internet ({@link Feed}), as the game sees them: which server they are for, the
 * fetch, and its status for the Panel. Instead of everyone writing their own, the four lists come
 * from a JSON file at a URL — the clan's, for instance. With URL mode on, the hand-written lists
 * are frozen (it is always one or the other) and the fetch happens when joining a server and on the
 * Refresh button.
 */
final class RemoteList {

    private final SteinCombat mod;

    /**
     * The server the current lists were built for (its address, or empty outside a server). It is
     * the key of the per-server sections: changing server changes the sections, and the lists.
     */
    private String appliedKey = "";

    /** A fetch is running in the background. */
    private boolean fetching;

    /** The reason of the last failure, for the Panel; null when the last fetch worked. */
    private String failed;

    RemoteList(SteinCombat mod) {
        this.mod = mod;
    }

    /**
     * The list in effect here: what the URL delivered, with the current server's section already
     * added — the file itself says which server each name is for. Empty before the first fetch, or
     * without a URL.
     */
    Feed feed() {
        CombatConfig c = mod.config;
        return c.feed(c.remoteUrl).forHost(appliedKey);
    }

    /**
     * The current server's address, as the per-server sections store it (lower case and without the
     * default port); empty outside a server.
     */
    static String host() {
        return Server.address();
    }

    /** Joined a server: the lists are for it from now on. */
    void joined() {
        appliedKey = host();
    }

    /** Changed server since the lists were built? (the sections, and the lists, may be others) */
    boolean serverChanged() {
        String key = host();
        if (key.equals(appliedKey)) {
            return false;
        }
        appliedKey = key;
        return true;
    }

    /** Forgets the last failure (a new URL, or the defaults restored). */
    void clearFailure() {
        failed = null;
    }

    /**
     * Fetches the list of the current URL in the background — the game does not wait for the
     * internet. The result is applied on the game thread.
     *
     * @param manual asked on the button (and not when joining a server): the result goes to the
     *     chat
     */
    void fetch(boolean manual) {
        if (fetching) {
            return;
        }
        String url = mod.config.remoteUrl;
        if (url.isEmpty()) {
            failed = null;
            if (manual) {
                Say.chat(Lang.tr("steincombat.remoteAskUrl"));
            }
            return;
        }
        fetching = true;
        Tasks.async(() -> Feed.fetch(url), f -> fetched(url, f, manual), e -> notFetched(url, e));
    }

    /** On the game thread: keeps what the fetch brought and rebuilds the lists. */
    private void fetched(String url, Feed f, boolean manual) {
        fetching = false;
        failed = null;
        mod.config.feed(url, f);
        mod.save();
        if (manual) {
            Say.chat(Lang.tr("steincombat.remoteOk", String.valueOf(f.size())));
        }
        mod.log().info("list from " + url + " with " + f.size() + " names");
    }

    /** On the game thread: notes why the fetch failed. */
    private void notFetched(String url, Throwable e) {
        fetching = false;
        String why = e.getMessage();
        String err = why == null || why.isEmpty() ? e.getClass().getSimpleName() : why;
        failed = err;
        // Always in the chat, even on the automatic fetch: whoever joined thinking the clan's list
        // applies must know it did not arrive — whom the mod lets them hit depends on it.
        Say.chat(
                Lang.tr(
                        mod.config.feed(url).isEmpty()
                                ? "steincombat.remoteFailNone"
                                : "steincombat.remoteFailKept",
                        err));
        mod.log().info("list from " + url + " did not arrive: " + err);
    }

    /** The remote list's status, on the right of "Refresh now". */
    String status() {
        if (fetching) {
            return "§e" + Lang.tr("steincombat.remoteFetching");
        }
        CombatConfig c = mod.config;
        if (c.remoteUrl.isEmpty()) {
            return "§7" + Lang.tr("steincombat.remoteNoUrl");
        }
        Feed f = feed();
        if (failed != null) {
            return "§c" + Lang.tr("steincombat.remoteFail");
        }
        if (f.at == 0) {
            return "§7" + Lang.tr("steincombat.remoteNever");
        }
        // Green only when the list really applies: with the mode off it exists, but rules nothing.
        return (c.remote ? "§a" : "§7")
                + Lang.tr("steincombat.remoteCount", String.valueOf(f.size()));
    }
}
