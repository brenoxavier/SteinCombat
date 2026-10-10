/*
 * Copyright (c) 2026 Breno Xavier. All rights reserved.
 * Proprietary software: see the LICENSE file at the root of this repository.
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
