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
package org.graalvm.compiler.truffle.compiler;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.graalvm.compiler.core.common.util.MethodKey;

import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Caches expensive idempotent values per {@link ResolvedJavaMethod} without causing leaks.
 */
public abstract class TruffleMethodCache<T> {

    private final Map<MethodKey, T> methodCache;

    @SuppressWarnings("serial")
    protected TruffleMethodCache(int maxSize) {
        methodCache = Collections.synchronizedMap(new LinkedHashMap<>() {
            @Override
            protected boolean removeEldestEntry(Map.Entry<MethodKey, T> eldest) {
                return size() > maxSize;
            }
        });
    }

    public T get(ResolvedJavaMethod method) {
        MethodKey key = new MethodKey(method);
        // It intentionally does not use Map#computeIfAbsent.
        // Collections.SynchronizedMap#computeIfAbsent implementation blocks readers during the
        // creation of the MethodCache.
        T cache = methodCache.get(key);
        if (cache == null) {
            cache = computeValue(method);
            methodCache.putIfAbsent(key, cache);
        }
        return cache;
    }

    protected abstract T computeValue(ResolvedJavaMethod method);

    @Override
    public String toString() {
        return methodCache.toString();
    }

}
