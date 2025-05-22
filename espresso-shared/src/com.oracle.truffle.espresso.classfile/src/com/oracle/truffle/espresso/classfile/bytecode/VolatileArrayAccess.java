/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.classfile.bytecode;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

public class VolatileArrayAccess {
    private static final VarHandle REF_ARRAY_HANDLE = MethodHandles.arrayElementVarHandle(Object[].class);
    private static final VarHandle BYTE_ARRAY_HANDLE = MethodHandles.arrayElementVarHandle(byte[].class);
    private static final VarHandle INT_ARRAY_HANDLE = MethodHandles.arrayElementVarHandle(int[].class);

    public static void volatileWrite(byte[] array, int index, byte value) {
        BYTE_ARRAY_HANDLE.setVolatile(array, index, value);
    }

    public static byte volatileRead(byte[] array, int index) {
        return (byte) BYTE_ARRAY_HANDLE.getVolatile(array, index);
    }

    public static void volatileWrite(int[] array, int index, int value) {
        INT_ARRAY_HANDLE.setVolatile(array, index, value);
    }

    public static int volatileRead(int[] array, int index) {
        return (int) INT_ARRAY_HANDLE.getVolatile(array, index);
    }

    public static <T> void volatileWrite(T[] array, int index, T value) {
        REF_ARRAY_HANDLE.setVolatile(array, index, value);
    }

    @SuppressWarnings("unchecked")
    public static <T> T volatileRead(T[] array, int index) {
        return (T) REF_ARRAY_HANDLE.getVolatile(array, index);
    }

    public static <T> boolean compareAndSet(T[] array, int index, T expected, T value) {
        return REF_ARRAY_HANDLE.compareAndSet(array, index, expected, value);
    }
}
