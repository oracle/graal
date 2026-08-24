/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jdk.strings;

import com.oracle.svm.shared.util.BasedOnJDKFile;

public final class HalfSipHash {
    private HalfSipHash() {
    }

    /** Computes a 32-bit HalfSipHash-2-4 for the given string. */
    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25+36/src/hotspot/share/classfile/altHashing.cpp#L80-L126")
    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25+36/src/hotspot/share/classfile/altHashing.cpp#L188-L216")
    public static int hash(long seed, String value) {
        int v0 = (int) seed;
        int v1 = (int) (seed >>> 32);
        int v2 = 0x6c796765 ^ v0;
        int v3 = 0x74656462 ^ v1;

        int length = value.length();
        int fullBlockCount = length >>> 1;
        for (int block = 0; block <= fullBlockCount; block++) {
            int input;
            if (block < fullBlockCount) {
                int offset = block << 1;
                input = value.charAt(offset) | value.charAt(offset + 1) << 16;
            } else {
                input = length * Character.BYTES << 24;
                if ((length & 1) != 0) {
                    input |= value.charAt(length - 1);
                }
            }

            v3 ^= input;
            for (int round = 0; round < 2; round++) {
                v0 += v1;
                v1 = Integer.rotateLeft(v1, 5);
                v1 ^= v0;
                v0 = Integer.rotateLeft(v0, 16);
                v2 += v3;
                v3 = Integer.rotateLeft(v3, 8);
                v3 ^= v2;
                v0 += v3;
                v3 = Integer.rotateLeft(v3, 7);
                v3 ^= v0;
                v2 += v1;
                v1 = Integer.rotateLeft(v1, 13);
                v1 ^= v2;
                v2 = Integer.rotateLeft(v2, 16);
            }
            v0 ^= input;
        }

        v2 ^= 0xff;
        for (int round = 0; round < 4; round++) {
            v0 += v1;
            v1 = Integer.rotateLeft(v1, 5);
            v1 ^= v0;
            v0 = Integer.rotateLeft(v0, 16);
            v2 += v3;
            v3 = Integer.rotateLeft(v3, 8);
            v3 ^= v2;
            v0 += v3;
            v3 = Integer.rotateLeft(v3, 7);
            v3 ^= v0;
            v2 += v1;
            v1 = Integer.rotateLeft(v1, 13);
            v1 ^= v2;
            v2 = Integer.rotateLeft(v2, 16);
        }
        return v1 ^ v3;
    }
}
