/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Breno Xavier
 */

package dev.xavier.stein.combat.server;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** A clan: its three-character tag, its leader and its members. */
final class Clan {

    final String tag;
    UUID leader;

    /** Members by UUID, with the last nick seen, in order of joining. */
    final Map<UUID, String> members = new LinkedHashMap<>();

    Clan(String tag, UUID leader) {
        this.tag = tag;
        this.leader = leader;
    }
}
