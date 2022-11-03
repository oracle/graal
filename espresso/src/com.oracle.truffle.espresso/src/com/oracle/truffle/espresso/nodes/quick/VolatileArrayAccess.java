/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.espresso.nodes.quick;

import com.oracle.truffle.espresso.vm.UnsafeAccess;

import sun.misc.Unsafe;

public class VolatileArrayAccess {
    private static final Unsafe U = UnsafeAccess.get();

    public static void volatileWrite(byte[] array, int index, byte value) {
        U.putByteVolatile(array, offsetFor(array, index), value);
    }

    public static byte volatileRead(byte[] array, int index) {
        return U.getByteVolatile(array, offsetFor(array, index));
    }

    public static void volatileWrite(int[] array, int index, int value) {
        U.putIntVolatile(array, offsetFor(array, index), value);
    }

    public static int volatileRead(int[] array, int index) {
        return U.getIntVolatile(array, offsetFor(array, index));
    }

    public static <T> void volatileWrite(T[] array, int index, Object value) {
        U.putObjectVolatile(array, offsetFor(array, index), value);
    }

    @SuppressWarnings("unchecked")
    public static <T> T volatileRead(T[] array, int index) {
        return (T) U.getObjectVolatile(array, offsetFor(array, index));
    }

    @SuppressWarnings("unused")
    private static long offsetFor(byte[] array, int index) {
        return Unsafe.ARRAY_BYTE_BASE_OFFSET + ((long) index * Unsafe.ARRAY_BYTE_INDEX_SCALE);
    }

    @SuppressWarnings("unused")
    private static long offsetFor(int[] array, int index) {
        return Unsafe.ARRAY_INT_BASE_OFFSET + ((long) index * Unsafe.ARRAY_INT_INDEX_SCALE);
    }

    @SuppressWarnings("unused")
    private static <T> long offsetFor(T[] array, int index) {
        return Unsafe.ARRAY_OBJECT_BASE_OFFSET + ((long) index * Unsafe.ARRAY_OBJECT_INDEX_SCALE);
    }
}
