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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;

import com.oracle.truffle.espresso.descriptors.ByteSequence;
import com.oracle.truffle.espresso.descriptors.Validation;
import com.oracle.truffle.espresso.jni.ModifiedUtf8;

public class ImageStringsReader {
    public static final int HASH_MULTIPLIER = 0x01000193;
    public static final int POSITIVE_MASK = 0x7FFFFFFF;

    private final ByteBuffer strings;

    ImageStringsReader(ByteBuffer strings) {
        this.strings = Objects.requireNonNull(strings);
    }

    public int match(int offset, ByteSequence string, int stringOffset) {
        if (offset < 0 || offset >= strings.limit()) {
            throw new IndexOutOfBoundsException(String.format("offset out of bounds: %d not in [0, %d[", offset, strings.limit()));
        }
        return ImageStringsReader.stringFromByteBufferMatches(strings, offset, string, stringOffset);
    }

    public static int hashCode(ByteSequence s) {
        return hashCode(s, HASH_MULTIPLIER);
    }

    public static int hashCode(ByteSequence s, int seed) {
        return unmaskedHashCode(s, seed) & POSITIVE_MASK;
    }

    public static int hashCode(ByteSequence module, ByteSequence name) {
        return hashCode(module, name, HASH_MULTIPLIER);
    }

    public static int hashCode(ByteSequence module, ByteSequence name, int seed) {
        int value = seed;
        value = (value * HASH_MULTIPLIER) ^ ('/');
        value = unmaskedHashCode(module, value);
        value = (value * HASH_MULTIPLIER) ^ ('/');
        value = unmaskedHashCode(name, value);
        return value & POSITIVE_MASK;
    }

    public static int unmaskedHashCode(ByteSequence s, int seed) {
        assert Validation.validModifiedUTF8(s);
        int value = seed;
        for (int i = 0; i < s.length(); i++) {
            value = (value * HASH_MULTIPLIER) ^ (s.byteAt(i) & 0xff);
        }
        return value;
    }

    static ByteBuffer rawStringFromByteBuffer(ByteBuffer buffer, int startOffset) {
        int limit = buffer.limit();
        int currentOffset = startOffset;
        while (currentOffset < limit) {
            byte ch = buffer.get(currentOffset++);
            if (ch == 0) {
                currentOffset--; // leave out the null byte
                break;
            }
        }
        ByteBuffer stringBuffer = buffer.duplicate();
        stringBuffer.position(startOffset);
        stringBuffer.limit(currentOffset);
        return stringBuffer;
    }

    /* package-private */
    static String stringFromByteBuffer(ByteBuffer buffer, int startOffset) {
        ByteBuffer raw = rawStringFromByteBuffer(buffer, startOffset);
        try {
            return ModifiedUtf8.toJavaString(raw);
        } catch (IOException e) {
            throw new InternalError(e);
        }
    }

    /* package-private */
    static int stringFromByteBufferMatches(ByteBuffer buffer, int offset, ByteSequence string, int stringOffset) {
        int limit = buffer.limit();
        int current = offset;
        int stringCurrent = stringOffset;
        int slen = string.length();
        while (current < limit) {
            byte ch = buffer.get(current);
            if (ch == 0) {
                // Match
                return current - offset;
            }
            if (stringCurrent >= slen || string.byteAt(stringCurrent) != ch) {
                // No match
                break;
            }
            stringCurrent++;
            current++;
        }
        return -1;
    }
}
