/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.oracle.svm.webimage;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Procedurally generates unique names from a fixed alphabet.
 *
 * The generated name are guaranteed to not be reserved JavaScript keywords.
 *
 * Generates smaller names than just appending an index to a prefix.
 */
public class JSNameGenerator {

    /**
     * Implements a name cache on top of the name generator.
     *
     * @param <T> Type of the identifier
     */
    public static class NameCache<T> {
        private final ConcurrentMap<T, String> cache = new ConcurrentHashMap<>();

        private final JSNameGenerator generator;

        public NameCache(String prefix) {
            generator = new JSNameGenerator(prefix);
        }

        /**
         * Tries to load the name for the given id from the cache.
         *
         * If no cache entry exists, it uses the {@link JSNameGenerator} to generate a new unique
         * name and associates it with the id.
         */
        public String get(T id) {
            return cache.computeIfAbsent(id, key -> generator.nextName());
        }
    }

    private static final char[] CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ_0123456789$".toCharArray();

    /**
     * List of reserved JS keywords.
     *
     * The generator is guaranteed to never generate any of these.
     */
    private static final Set<String> RESERVED_KEYWORDS = new HashSet<>(Arrays.asList(
                    "abstract",
                    "await",
                    "boolean",
                    "break",
                    "byte",
                    "case",
                    "catch",
                    "char",
                    "class",
                    "const",
                    "continue",
                    "debugger",
                    "default",
                    "delete",
                    "do",
                    "double",
                    "else",
                    "enum",
                    "export",
                    "extends",
                    "final",
                    "finally",
                    "float",
                    "for",
                    "function",
                    "goto",
                    "if",
                    "implements",
                    "import",
                    "in",
                    "instanceof",
                    "int",
                    "interface",
                    "let",
                    "long",
                    "native",
                    "new",
                    "package",
                    "private",
                    "protected",
                    "public",
                    "return",
                    "short",
                    "static",
                    "super",
                    "switch",
                    "synchronized",
                    "this",
                    "throw",
                    "throws",
                    "transient",
                    "try",
                    "typeof",
                    "var",
                    "void",
                    "volatile",
                    "while",
                    "with",
                    "yield"));

    /**
     * List of reserved Web Image symbols.
     *
     * The generator is guaranteed to never generate any of these.
     */
    private static final HashSet<String> RESERVED_SYMBOLS = new HashSet<>(Arrays.asList());

    public static String registerReservedSymbol(String name) {
        RESERVED_SYMBOLS.add(name);
        return name;
    }

    public static void registerReservedSymbols(String... names) {
        for (String name : names) {
            registerReservedSymbol(name);
        }
    }

    private final AtomicInteger idx = new AtomicInteger();

    private final String prefix;

    public JSNameGenerator(String prefix) {
        this.prefix = prefix;
    }

    private String nextNameInternal() {
        StringBuilder name = new StringBuilder();
        int numChars = CHARS.length;

        name.append(prefix);

        int i = idx.getAndIncrement();

        int ch = i % numChars;
        i /= numChars;
        name.append(CHARS[ch]);

        /*
         * This is not the same as a base conversion because a string like 'aaaa' is possible which
         * would be the same as 'a' when converting between bases (assuming 'a' represents 0).
         *
         * That's why for each place except for the first one, we subtract one to account for this.
         */
        while (i > 0) {
            i--;
            ch = i % numChars;
            i /= numChars;
            name.append(CHARS[ch]);
        }

        return name.toString();
    }

    public String nextName() {
        String name;
        do {
            name = nextNameInternal();
        } while (RESERVED_KEYWORDS.contains(name) || RESERVED_SYMBOLS.contains(name));

        return name;
    }
}
