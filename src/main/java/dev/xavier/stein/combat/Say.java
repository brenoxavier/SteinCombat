/*
 * Copyright (c) 2026 Breno Xavier. All rights reserved.
 * Proprietary software: see the LICENSE file at the root of this repository.
 */

package dev.xavier.stein.combat;

import dev.xavier.stein.loader.api.Chat;

/** Where Combat talks to the player: the chat (with its prefix) and the action bar. */
final class Say {

    private static final String CHAT_PREFIX = "§7[Combat] §r";

    private Say() {}

    /** A line in the chat, only on the player's screen. */
    static void chat(String text) {
        Chat.print(CHAT_PREFIX + text);
    }

    /** The text above the hotbar. */
    static void actionBar(String text) {
        Chat.actionBar(text);
    }
}
