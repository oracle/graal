/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import com.oracle.truffle.api.object.DynamicObject;

import sun.misc.Unsafe;

public final class EspressoOptions {
    /** Will use reflection to get and set fields in all the objects. */
    public static final boolean OBJECT_ACCESS_VIA_REFLECTION = false;

    /**
     * Will return null on uninitialized array read accesses, even if the array got specialized to
     * some primitive type internally. Introduces additional checking overhead which may decrease
     * performance. This options is only relevant if {@link #USE_DYNAMIC_OBJECT} is enabled.
     */
    public static final boolean COMPLIANT_ARRAY_READ_BEHAVIOUR;

    /**
     * Will replace every java object with an {@link DynamicObject} (expect for some critical
     * classes like {@link Class}, {@link Throwable}, {@link System}, {@link Unsafe} and others).
     */
    public static final boolean USE_DYNAMIC_OBJECT;

    /**
     * prints debug information to {@link System#err} while parsing and executing code. This flag
     * should obviously never be enabled in production code.
     */
    public static final boolean ENABLE_DEBUG_OUTPUT;

    /**
     * The values of static fields which are mentioned in this list will not be converted to dynamic
     * objects ever.
     */
    public static final List<Field> NATIVE_STATICS = new ArrayList<>();

    /** Methods of classes which are mentioned in this list will be inlined. */
    public static final List<String> FORCE_INLINE = new ArrayList<>();

    public static final boolean INTRINSICS_VIA_REFLECTION;

    static {
        USE_DYNAMIC_OBJECT = Boolean.getBoolean("truffle.espresso.dynamic");
        ENABLE_DEBUG_OUTPUT = Boolean.getBoolean("truffle.espresso.debug");

        INTRINSICS_VIA_REFLECTION = Boolean.getBoolean("truffle.espresso.reflection");

        COMPLIANT_ARRAY_READ_BEHAVIOUR = Boolean.getBoolean("truffle.espresso.compliant");

        try {
            NATIVE_STATICS.add(System.class.getField("out"));
        } catch (NoSuchFieldException | SecurityException e) {
            throw new IllegalStateException(e);
        }

        // will increase performance of ArrayList
        FORCE_INLINE.add("java.util.ArrayList");
        // we must create a new ANewArrayNode for each Array.newInstance callsite
        FORCE_INLINE.add("java.lang.reflect.Array");
        // increases unsafe field access performance because we hardly need caches then anymore
        FORCE_INLINE.add("sun.misc.Unsafe");
    }
}
