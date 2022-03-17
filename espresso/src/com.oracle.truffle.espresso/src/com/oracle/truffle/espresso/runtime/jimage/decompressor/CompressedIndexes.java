/*
 * Copyright (c) 2014, 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.runtime.jimage.decompressor;

import java.nio.ByteBuffer;

/**
 * Compressed indexes reader.
 */
public final class CompressedIndexes {
    private static final int COMPRESSED_FLAG = 1 << (Byte.SIZE - 1);
    private static final int HEADER_WIDTH = 3;
    private static final int HEADER_SHIFT = Byte.SIZE - HEADER_WIDTH;
    private static final int HEADER_VALUE_MASK = (1 << HEADER_SHIFT) - 1;

    private CompressedIndexes() {
    }

    public static int readInt(ByteBuffer cr) {
        // Get header byte.
        byte header = cr.get();
        // Determine size.
        int size = getHeaderLength(header);
        // Prepare result.
        int result = getHeaderValue(header);

        // For each value byte
        for (int i = 1; i < size; i++) {
            // Merge byte value.
            result <<= Byte.SIZE;
            result |= cr.get() & 0xFF;
        }

        return result;
    }

    private static boolean isCompressed(byte b) {
        return (b & COMPRESSED_FLAG) != 0;
    }

    private static int getHeaderLength(byte b) {
        return isCompressed(b) ? (b >> HEADER_SHIFT) & HEADER_WIDTH : Integer.BYTES;
    }

    private static int getHeaderValue(byte b) {
        return isCompressed(b) ? b & HEADER_VALUE_MASK : b;
    }
}
