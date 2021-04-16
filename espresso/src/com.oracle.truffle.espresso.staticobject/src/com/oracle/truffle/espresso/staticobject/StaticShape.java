/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.staticobject;

import sun.misc.Unsafe;

import java.lang.reflect.Field;

public abstract class StaticShape<T> {
    protected static final Unsafe UNSAFE = getUnsafe();
    protected final Class<?> storageClass;
    protected T factory;

    StaticShape(Class<?> storageClass) {
        this.storageClass = storageClass;
    }

    public static StaticShapeBuilder newBuilder() {
        return new StaticShapeBuilder();
    }

    protected final void setFactory(T factory) {
        if (this.factory != null) {
            throw new RuntimeException("Attempt to reinitialize the offset of a static property. Was it added to more than one builder?");
        }
        this.factory = factory;
    }

    public final T getFactory() {
        return factory;
    }

    final Class<?> getStorageClass() {
        return storageClass;
    }

    abstract Object getStorage(Object obj, boolean primitive);

    static <T> T cast(Object obj, Class<T> type) {
        return type.cast(obj);
    }

    @SuppressWarnings("unchecked")
    final Class<T> getFactoryInterface() {
        // Builder.validate() makes sure that the factory class implements a single interface
        assert factory.getClass().getInterfaces().length == 1;
        return (Class<T>) factory.getClass().getInterfaces()[0];
    }

    private static Unsafe getUnsafe() {
        try {
            return Unsafe.getUnsafe();
        } catch (SecurityException e) {
        }
        try {
            Field theUnsafeInstance = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafeInstance.setAccessible(true);
            return (Unsafe) theUnsafeInstance.get(Unsafe.class);
        } catch (Exception e) {
            throw new RuntimeException("exception while trying to get Unsafe.theUnsafe via reflection:", e);
        }
    }
}
