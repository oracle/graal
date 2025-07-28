/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.jni;

import java.lang.ref.WeakReference;
import java.util.Objects;
import java.util.WeakHashMap;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

/**
 * Manages a collection of weak references associated to handles.
 */
public final class WeakHandles<T> extends HandleStorage<T, WeakReference<T>> {
    private final WeakHashMap<T, Integer> map;

    @Override
    WeakReference<T> toREF(T object) {
        return new WeakReference<>(object);
    }

    @Override
    T deREF(WeakReference<T> ref) {
        return ref != null ? ref.get() : null;
    }

    public WeakHandles() {
        super(true);
        map = new WeakHashMap<>(DEFAULT_INITIAL_CAPACITY);
    }

    @TruffleBoundary
    @Override
    public synchronized long handlify(T object) {
        Objects.requireNonNull(object);
        Integer handle = map.get(object);
        if (handle != null) {
            return handle;
        }
        handle = (int) super.handlify(object);
        map.put(object, handle);
        return handle;
    }
}
