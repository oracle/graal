/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.truffle;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import jdk.graal.compiler.core.common.util.MethodKey;

import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Cache for method or field related data if it cannot be stored directly in the method or field.
 */
public abstract class TruffleElementCache<K, V> {

    private final Map<Object, V> elementCache;

    @SuppressWarnings("serial")
    protected TruffleElementCache(int maxSize) {
        this.elementCache = Collections.synchronizedMap(new LinkedHashMap<>() {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Object, V> eldest) {
                return size() > maxSize;
            }
        });
    }

    /**
     * Either use {@link MethodKey} or {@link ResolvedJavaMethod} directly.
     */
    protected abstract Object createKey(K element);

    public final V get(K element) {
        Object key = createKey(element);

        // It intentionally does not use Map#computeIfAbsent.
        // Collections.SynchronizedMap#computeIfAbsent implementation blocks readers during the
        // creation of the MethodCache.
        V cache = elementCache.get(key);
        if (cache == null) {
            cache = computeValue(element);
            elementCache.putIfAbsent(key, cache);
        }
        return cache;
    }

    protected abstract V computeValue(K element);

    @Override
    public final String toString() {
        return elementCache.toString();
    }

}
