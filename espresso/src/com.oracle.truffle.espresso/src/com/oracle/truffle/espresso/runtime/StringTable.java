/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.espresso.runtime;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.substitutions.JavaType;

/**
 * Used to implement guest String interning.
 *
 * <p>
 * <b>Security note:</b> Sharing strings between contexts is dangerous, the underlying char[] is
 * shared between the guest and host implementations to avoid copying conversions. If one context
 * modifies one string (e.g. via reflection, Unsafe...), other contexts are also affected.
 *
 * Interned guest strings and Espresso symbols are very similar but the Espresso symbol isolation is
 * strict; internals are never accessible to the guest language.
 */
public final class StringTable {

    private final EspressoContext context; // per context

    // TODO(peterssen): Set generous initial capacity, this will be HIT.
    private final ConcurrentHashMap<Symbol<?>, String> cache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, StaticObject> interned = new ConcurrentHashMap<>();

    public StringTable(EspressoContext context) {
        this.context = context;
    }

    public StaticObject intern(Symbol<?> value) {
        // Weak values? Too expensive?
        return interned.computeIfAbsent(cache.computeIfAbsent(value,
                        new Function<Symbol<?>, String>() {
                            @Override
                            public String apply(Symbol<?> value1) {
                                return createStringFromSymbol(value1);
                            }
                        }), new Function<String, StaticObject>() {
                            @Override
                            public StaticObject apply(String value1) {
                                return StringTable.this.createStringObjectFromString(value1);
                            }
                        });
    }

    private StaticObject createStringObjectFromString(String value) {
        return context.getMeta().toGuestString(value);
    }

    private static String createStringFromSymbol(Symbol<?> value) {
        return value.toString();
    }

    @TruffleBoundary
    public @JavaType(String.class) StaticObject intern(@JavaType(String.class) StaticObject guestString) {
        assert StaticObject.notNull(guestString);
        String hostString = context.getMeta().toHostString(guestString);
        return interned.computeIfAbsent(hostString, new Function<String, StaticObject>() {
            @Override
            public StaticObject apply(String k) {
                return guestString;
            }
        });
    }
}
