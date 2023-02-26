/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.agentscript.impl;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.interop.InteropLibrary;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

final class RootNameFilter implements Predicate<String> {
    private final InsightInstrument instrument;
    private final InsightInstrument.Key key;
    private final ThreadLocal<Boolean> querying;
    private final Map<String, Boolean> cache;

    RootNameFilter(InsightInstrument instrument, InsightInstrument.Key key) {
        this.instrument = instrument;
        this.key = key;
        this.querying = new ThreadLocal<>();
        this.cache = Collections.synchronizedMap(new HashMap<>());
    }

    @CompilerDirectives.TruffleBoundary
    @Override
    public boolean test(String rootName) {
        if (rootName == null) {
            return false;
        }
        Boolean computed = cache.get(rootName);
        if (computed != null) {
            return computed;
        }
        Boolean prev = this.querying.get();
        try {
            if (Boolean.TRUE.equals(prev)) {
                computed = false;
            } else {
                this.querying.set(true);
                final InteropLibrary iop = InteropLibrary.getFactory().getUncached();
                computed = false;
                TruffleContext c = instrument.env().getEnteredContext();
                if (c != null) {
                    InsightPerContext ctx = instrument.find(c);
                    final int len = key.functionsMaxCount();
                    for (int i = 0; i < len; i++) {
                        InsightFilter.Data data = (InsightFilter.Data) ctx.functionFor(key, i);
                        if (data == null || data.rootNameFn == null) {
                            continue;
                        }
                        if (rootNameCheck(iop, data, rootName)) {
                            computed = true;
                            break;
                        }
                    }
                }

            }
        } finally {
            this.querying.set(prev);
        }
        cache.put(rootName, computed);
        return computed;
    }

    static boolean rootNameCheck(final InteropLibrary iop, InsightFilter.Data data, String rootName) {
        return FilterExec.checkFilter(iop, data.rootNameFn, rootName);
    }
}
