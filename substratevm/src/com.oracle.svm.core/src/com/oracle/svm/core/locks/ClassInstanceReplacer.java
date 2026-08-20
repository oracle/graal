/*
 * Copyright (c) 2015, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.locks;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.util.HeapObjectReplacer;

@Platforms(Platform.HOSTED_ONLY.class)
public final class ClassInstanceReplacer<S, T> extends HeapObjectReplacer {

    private final Class<S> sourceClass;
    private final Function<S, T> createReplacement;
    private final Consumer<T> objectReachableCallback;
    private final Map<S, T> replacements = Collections.synchronizedMap(new IdentityHashMap<>());
    private final Map<T, Boolean> reachableEntries = Collections.synchronizedMap(new IdentityHashMap<>());

    public ClassInstanceReplacer(Class<S> sourceClass, Function<S, T> createReplacement, Consumer<T> objectReachableCallback) {
        this.sourceClass = sourceClass;
        this.createReplacement = createReplacement;
        this.objectReachableCallback = objectReachableCallback;
    }

    /**
     * Create a replacement for the source object, or access it from a cache.
     */
    @Override
    public Object replaceObject(Object object, boolean reachable) {
        if (object == null || object.getClass() != sourceClass) {
            return object;
        }
        S source = sourceClass.cast(object);
        T replacement = getOrCreateReplacement(source);
        if (reachable) {
            onObjectReachable(replacement);
        }
        return replacement;
    }

    public void onObjectReachable(T replacement) {
        reachableEntries.computeIfAbsent(replacement, _ -> {
            objectReachableCallback.accept(replacement);
            return true;
        });
    }

    public T getOrCreateReplacement(S object) {
        return replacements.computeIfAbsent(object, source -> {
            T replacement = createReplacement.apply(source);
            assert replacement.getClass() != sourceClass : "leads to recursive replacement";
            return replacement;
        });
    }
}
