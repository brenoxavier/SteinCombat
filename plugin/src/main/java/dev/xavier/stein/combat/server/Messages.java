/*
 * Copyright (c) 2026 Breno Xavier. All rights reserved.
 * Proprietary software: see the LICENSE file at the root of this repository.
 */

package dev.xavier.stein.combat.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * The text players read, from {@code lang/pt_BR.properties} in the plugin jar (the server's players
 * speak Portuguese). {@code {0}}, {@code {1}}... are replaced by the arguments, in order.
 */
final class Messages {

    private static final String FILE = "/lang/pt_BR.properties";

    private final Properties text = new Properties();

    Messages() throws IOException {
        InputStream in = Messages.class.getResourceAsStream(FILE);
        if (in == null) {
            throw new IOException(FILE + " is missing from the plugin jar");
        }
        try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            text.load(r);
        }
    }

    /** The text of the key with its arguments; the key itself if it is missing. */
    String get(String key, Object... args) {
        String s = text.getProperty(key, key);
        for (int i = 0; i < args.length; i++) {
            s = s.replace("{" + i + "}", String.valueOf(args[i]));
        }
        return s;
    }

    /** The same, with the plugin's chat prefix in front. */
    String line(String key, Object... args) {
        return get("prefix") + get(key, args);
    }
}
