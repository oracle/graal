/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.source.Source;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Predicate;

final class InsightSourceFilter implements Predicate<Source> {
    private final InsightInstrument insight;
    private final ThreadLocal<Boolean> querying;
    private final Map<Source, Boolean> cache;
    private final InsightInstrument.Key key;

    InsightSourceFilter(InsightInstrument insight, InsightInstrument.Key key) {
        this.insight = insight;
        this.key = key;
        this.querying = new ThreadLocal<>();
        this.cache = Collections.synchronizedMap(new WeakHashMap<>());
    }

    @CompilerDirectives.TruffleBoundary
    @Override
    public boolean test(Source src) {
        if (src == null) {
            return false;
        }
        Boolean previous = cache.get(src);
        if (previous != null) {
            return previous;
        } else {
            boolean now = realCheck(src);
            cache.put(src, now);
            return now;
        }
    }

    private boolean realCheck(Source src) {
        Boolean prev = this.querying.get();
        try {
            if (Boolean.TRUE.equals(prev)) {
                return false;
            }
            if (key.isClosed()) {
                return false;
            }
            this.querying.set(true);
            final InteropLibrary iop = InteropLibrary.getFactory().getUncached();
            final int len = key.functionsMaxCount();
            InsightPerContext ctx = insight.findCtx();
            for (int i = 0; i < len; i++) {
                InsightFilter.Data data = (InsightFilter.Data) ctx.functionFor(key, i);
                if (data == null || data.sourceFilterFn == null) {
                    continue;
                }
                if (checkSource(iop, data, src)) {
                    return true;
                }
            }
            return false;
        } finally {
            this.querying.set(prev);
        }
    }

    static boolean checkSource(final InteropLibrary iop, InsightFilter.Data data, Source src) {
        final SourceEventObject srcObj = new SourceEventObject(src);
        Object res;
        try {
            res = iop.execute(data.sourceFilterFn, srcObj);
        } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException ex) {
            return false;
        }
        final boolean is = Boolean.TRUE.equals(res);
        return is;
    }
}
