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
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import com.oracle.truffle.espresso.descriptors.ByteSequence;
import com.oracle.truffle.espresso.descriptors.Validation;

public class ImageStringsReader {
    public static final int HASH_MULTIPLIER = 0x01000193;
    public static final int POSITIVE_MASK = 0x7FFFFFFF;

    private final BasicImageReader reader;

    ImageStringsReader(BasicImageReader reader) {
        this.reader = Objects.requireNonNull(reader);
    }

    public int match(int offset, ByteSequence string, int stringOffset) {
        return reader.match(offset, string, stringOffset);
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

    /**
     * Calculates the number of characters in the String present at the specified offset. As an
     * optimization, the length returned will be positive if the characters are all ASCII, and
     * negative otherwise.
     */
    private static int charsFromByteBufferLength(ByteBuffer buffer, int offset) {
        int length = 0;

        int limit = buffer.limit();
        boolean asciiOnly = true;
        int currentOffset = offset;
        while (currentOffset < limit) {
            byte ch = buffer.get(currentOffset++);

            if (ch < 0) {
                asciiOnly = false;
            } else if (ch == 0) {
                return asciiOnly ? length : -length;
            }

            if ((ch & 0xC0) != 0x80) {
                length++;
            }
        }
        throw new InternalError("No terminating zero byte for modified UTF-8 byte sequence");
    }

    private static void charsFromByteBuffer(char[] chars, ByteBuffer buffer, int offset) {
        int j = 0;

        int limit = buffer.limit();
        int currentOffset = offset;
        while (currentOffset < limit) {
            byte ch = buffer.get(currentOffset++);

            if (ch == 0) {
                return;
            }

            boolean is_unicode = (ch & 0x80) != 0;
            int uch = ch & 0x7F;

            if (is_unicode) {
                int mask = 0x40;

                while ((uch & mask) != 0) {
                    ch = buffer.get(currentOffset++);

                    if ((ch & 0xC0) != 0x80) {
                        throw new InternalError("Bad continuation in " +
                                        "modified UTF-8 byte sequence: " + ch);
                    }

                    uch = ((uch & ~mask) << 6) | (ch & 0x3F);
                    mask <<= 6 - 1;
                }
            }

            if ((uch & 0xFFFF) != uch) {
                throw new InternalError("UTF-32 char in modified UTF-8 " +
                                "byte sequence: " + uch);
            }

            chars[j++] = (char) uch;
        }

        throw new InternalError("No terminating zero byte for modified UTF-8 byte sequence");
    }

    static ByteBuffer rawStringFromByteBuffer(ByteBuffer buffer, int startOffset) {
        int limit = buffer.limit();
        int currentOffset = startOffset;
        while (currentOffset < limit) {
            byte ch = buffer.get(currentOffset++);
            if (ch == 0) {
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
        int offset = startOffset;
        int length = charsFromByteBufferLength(buffer, offset);
        if (length > 0) {
            byte[] asciiBytes = new byte[length];
            // Ideally we could use buffer.get(offset, asciiBytes, 0, length)
            // here, but that was introduced in JDK 13
            for (int i = 0; i < length; i++) {
                asciiBytes[i] = buffer.get(offset++);
            }
            return new String(asciiBytes, StandardCharsets.US_ASCII);
        }
        char[] chars = new char[-length];
        charsFromByteBuffer(chars, buffer, offset);
        return new String(chars);
    }

    /* package-private */
    static int stringFromByteBufferMatches(ByteBuffer buffer, int offset, ByteSequence string, int stringOffset) {
        // ASCII fast-path
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
