/*
 * Copyright (c) 2014, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.runtime.jimage;

import java.nio.ByteBuffer;
import java.util.Objects;

import com.oracle.truffle.espresso.classfile.descriptors.ByteSequence;

public class ImageLocation {
    private static final int ATTRIBUTE_END = 0;
    private static final int ATTRIBUTE_MODULE = 1;
    private static final int ATTRIBUTE_PARENT = 2;
    private static final int ATTRIBUTE_BASE = 3;
    private static final int ATTRIBUTE_EXTENSION = 4;
    private static final int ATTRIBUTE_OFFSET = 5;
    private static final int ATTRIBUTE_COMPRESSED = 6;
    private static final int ATTRIBUTE_UNCOMPRESSED = 7;
    private static final int ATTRIBUTE_COUNT = 8;

    private final long[] attributes;

    public ImageLocation(long[] attributes) {
        this.attributes = Objects.requireNonNull(attributes);
    }

    static ImageLocation decompress(ByteBuffer bytes, int offset) {
        Objects.requireNonNull(bytes);
        long[] attributes = new long[ATTRIBUTE_COUNT];

        int limit = bytes.limit();
        int currentOffset = offset;
        while (currentOffset < limit) {
            int data = bytes.get(currentOffset++) & 0xFF;
            if (data <= 0x7) { // ATTRIBUTE_END
                break;
            }
            int kind = data >>> 3;
            if (kind >= ATTRIBUTE_COUNT) {
                throw new InternalError("Invalid jimage attribute kind: " + kind);
            }

            int length = (data & 0x7) + 1;
            attributes[kind] = readValue(length, bytes, currentOffset, limit);
            currentOffset += length;
        }
        return new ImageLocation(attributes);
    }

    boolean verify(ByteSequence name, ImageStringsReader strings) {
        Objects.requireNonNull(name);
        int length = name.length();
        int index = 0;
        int moduleOffset = (int) attributes[ATTRIBUTE_MODULE];
        if (moduleOffset != 0 && length >= 1) {
            // expected: "/$module/$name"
            int moduleLen = strings.match(moduleOffset, name, 1);
            index = moduleLen + 1;
            if (moduleLen < 0 || index >= length || name.unsignedByteAt(0) != '/' || name.unsignedByteAt(index++) != '/') {
                return false;
            }
        }
        return verifyName(null, name, index, length, 0,
                        (int) attributes[ATTRIBUTE_PARENT],
                        (int) attributes[ATTRIBUTE_BASE],
                        (int) attributes[ATTRIBUTE_EXTENSION],
                        strings);
    }

    private static long readValue(int length, ByteBuffer buffer, int offset, int limit) {
        long value = 0;
        int currentOffset = offset;
        for (int j = 0; j < length; j++) {
            value <<= 8;
            if (currentOffset >= limit) {
                throw new InternalError("Missing jimage attribute data");
            }
            value |= buffer.get(currentOffset++) & 0xFF;
        }
        return value;
    }

    boolean verify(ByteSequence module, ByteSequence name, ImageStringsReader strings) {
        Objects.requireNonNull(module);
        Objects.requireNonNull(name);
        return verifyName(module, name, 0, name.length(),
                        (int) attributes[ATTRIBUTE_MODULE],
                        (int) attributes[ATTRIBUTE_PARENT],
                        (int) attributes[ATTRIBUTE_BASE],
                        (int) attributes[ATTRIBUTE_EXTENSION],
                        strings);
    }

    private static boolean verifyName(ByteSequence module, ByteSequence name, int index, int length,
                    int moduleOffset, int parentOffset, int baseOffset, int extOffset, ImageStringsReader strings) {
        int currentIndex = index;
        if (moduleOffset != 0) {
            if (strings.match(moduleOffset, module, 0) != module.length()) {
                return false;
            }
        }
        if (parentOffset != 0) {
            int parentLen = strings.match(parentOffset, name, currentIndex);
            if (parentLen < 0) {
                return false;
            }
            currentIndex += parentLen;
            if (currentIndex >= length || name.unsignedByteAt(currentIndex++) != '/') {
                return false;
            }
        }
        int baseLen = strings.match(baseOffset, name, currentIndex);
        if (baseLen < 0) {
            return false;
        }
        currentIndex += baseLen;
        if (extOffset != 0) {
            if (currentIndex >= length || name.unsignedByteAt(currentIndex++) != '.') {
                return false;
            }

            int extLen = strings.match(extOffset, name, currentIndex);
            if (extLen < 0) {
                return false;
            }
            currentIndex += extLen;
        }
        return length == currentIndex;
    }

    long getAttribute(int kind) {
        if (kind < ATTRIBUTE_END || kind >= ATTRIBUTE_COUNT) {
            throw new InternalError("Invalid jimage attribute kind: " + kind);
        }
        return attributes[kind];
    }

    public long getContentOffset() {
        return getAttribute(ATTRIBUTE_OFFSET);
    }

    public long getCompressedSize() {
        return getAttribute(ATTRIBUTE_COMPRESSED);
    }

    public long getUncompressedSize() {
        return getAttribute(ATTRIBUTE_UNCOMPRESSED);
    }
}
