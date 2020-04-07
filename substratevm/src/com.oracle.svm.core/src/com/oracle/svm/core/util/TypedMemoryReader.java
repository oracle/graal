/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.util;

import org.graalvm.word.Pointer;

public class TypedMemoryReader {
    public static int getS1(Pointer ptr) {
        return ptr.readByte(0);
    }

    public static int getS2(Pointer ptr) {
        return ptr.readShort(0);
    }

    public static int getS4(Pointer ptr) {
        return ptr.readInt(0);
    }

    public static long getS8(Pointer ptr) {
        return ptr.readLong(0);
    }

    public static int getU1(Pointer ptr) {
        return getS1(ptr) & 0xFF;
    }

    public static int getU2(Pointer ptr) {
        return getS2(ptr) & 0xFFFF;
    }

    public static long getU4(Pointer ptr) {
        return getS4(ptr) & 0xFFFFFFFFL;
    }
}
