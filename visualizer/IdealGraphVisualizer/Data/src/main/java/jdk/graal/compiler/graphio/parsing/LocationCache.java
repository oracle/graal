/*
 * Copyright (c) 2013, 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.graphio.parsing;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.List;
import java.util.WeakHashMap;

/**
 * @author crefice
 */
public class LocationCache {
    private static final class WeakCache<T> {
        WeakHashMap<T, WeakReference<T>> immutableCache = new WeakHashMap<>();

        synchronized T get(T value) {
            WeakReference<T> result = immutableCache.get(value);
            if (result != null) {
                T ref = result.get();
                if (ref != null) {
                    return ref;
                }
            }
            immutableCache.put(value, new WeakReference<>(value));
            return value;
        }
    }

    private static final WeakCache<LocationStratum> stratumCache = new WeakCache<>();
    private static final WeakCache<LocationStackFrame> stackFrameCache = new WeakCache<>();

    public static List<LocationStratum> fileLineStratum(String fileName, int line) {
        return Collections.nCopies(1, createStratum(null, fileName, "Java", line, -1, -1));
    }

    public static LocationStratum createStratum(String uri, String file, String language, int line, int startOffset, int endOffset) {
        return stratumCache.get(new LocationStratum(uri, file, language, line, startOffset, endOffset));
    }

    public static LocationStackFrame createFrame(BinaryReader.Method method, int bci, List<LocationStratum> strata, LocationStackFrame parent) {
        return stackFrameCache.get(new LocationStackFrame(method, bci, strata, parent));
    }

    /**
     * To be used only for tests.
     */
    public static BinaryReader.Method createMethod(String methodName, String className, byte[] bytecode) {
        BinaryReader.Klass clazz = className == null ? null : new BinaryReader.Klass(className);
        return new BinaryReader.Method(methodName, null, bytecode, clazz, 0);
    }
}
