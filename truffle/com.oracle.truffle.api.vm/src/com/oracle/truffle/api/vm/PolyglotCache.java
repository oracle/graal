/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.vm;

import java.util.HashMap;
import java.util.Map;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.nodes.RootNode;

final class PolyglotCache {

    private final Map<Class<?>, Cache> cachedTargets = new HashMap<>();
    private final PolyglotEngine engine;

    PolyglotCache(PolyglotEngine engine) {
        this.engine = engine;
    }

    CallTarget lookupAsJava(Class<?> type) {
        Cache cache = lookupCache(type);
        if (cache.asJava == null) {
            cache.asJava = PolyglotRootNode.createAsJava(engine, type);
        }
        return cache.asJava;
    }

    CallTarget lookupExecute(Class<?> type) {
        Cache cache = lookupCache(type);
        if (cache.execute == null) {
            cache.execute = PolyglotRootNode.createExecuteSymbol(engine, type);
        }
        return cache.execute;
    }

    CallTarget lookupComputation(RootNode computation) {
        Cache cache = lookupCache(void.class);
        if (cache.computation == null && computation != null) {
            cache.computation = Truffle.getRuntime().createCallTarget(computation);
        }
        return cache.computation;
    }

    private Cache lookupCache(Class<?> clazz) {
        Cache cache = cachedTargets.get(clazz);
        if (cache == null) {
            cache = new Cache();
            cachedTargets.put(clazz, cache);
        }
        return cache;
    }

    private static class Cache {

        CallTarget asJava;
        CallTarget execute;
        CallTarget computation;
    }

}
