/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.core.common.util.UnsafeArrayTypeReader;

import com.oracle.svm.core.config.ConfigurationValues;

/**
 * Provides low-level read access to a byte[] array for signed and unsigned values of size 1, 2, 4,
 * and 8 bytes.
 */
public class ByteArrayReader {

    public static int getS1(byte[] data, long byteIndex) {
        return UnsafeArrayTypeReader.getS1(data, byteIndex);
    }

    public static int getU1(byte[] data, long byteIndex) {
        return UnsafeArrayTypeReader.getU1(data, byteIndex);
    }

    public static int getS2(byte[] data, long byteIndex) {
        return UnsafeArrayTypeReader.getS2(data, byteIndex, supportsUnalignedMemoryAccess());
    }

    public static int getU2(byte[] data, long byteIndex) {
        return UnsafeArrayTypeReader.getU2(data, byteIndex, supportsUnalignedMemoryAccess());
    }

    public static int getS4(byte[] data, long byteIndex) {
        return UnsafeArrayTypeReader.getS4(data, byteIndex, supportsUnalignedMemoryAccess());
    }

    public static long getU4(byte[] data, long byteIndex) {
        return UnsafeArrayTypeReader.getU4(data, byteIndex, supportsUnalignedMemoryAccess());
    }

    public static long getS8(byte[] data, long byteIndex) {
        return UnsafeArrayTypeReader.getS8(data, byteIndex, supportsUnalignedMemoryAccess());
    }

    @Fold
    public static boolean supportsUnalignedMemoryAccess() {
        return ConfigurationValues.getTarget().arch.supportsUnalignedMemoryAccess();
    }
}
