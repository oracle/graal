/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.polyglot.io;

import java.util.Arrays;

/**
 * Implementation created in {@link ByteSequence#create(byte[])}.
 */
final class ByteArraySequence implements ByteSequence {

    private final byte[] buffer;
    private final int start;
    private final int length;

    ByteArraySequence(byte[] buffer, int start, int length) {
        assert buffer.length >= start + length;
        assert start >= 0;
        assert length >= 0;
        this.buffer = buffer;
        this.start = start;
        this.length = length;
    }

    public int length() {
        return length;
    }

    public byte byteAt(int index) {
        int resolvedIndex = start + index;
        if (resolvedIndex >= start + length) {
            throw new IndexOutOfBoundsException(String.valueOf(index));
        }
        return buffer[resolvedIndex];
    }

    public byte[] toByteArray() {
        return Arrays.copyOfRange(buffer, start, start + length);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (obj instanceof ByteArraySequence) {
            ByteArraySequence other = ((ByteArraySequence) obj);
            if (buffer == other.buffer) {
                return start == other.start && length == other.length;
            }
            if (length != other.length) {
                return false;
            }
            int otherStart = other.start;
            for (int i = 0; i < length; i++) {
                if (buffer[start + i] != other.buffer[otherStart + i]) {
                    return false;
                }
            }
            return true;
        } else if (obj instanceof ByteSequence) {
            ByteSequence other = ((ByteSequence) obj);
            if (length != other.length()) {
                return false;
            }
            for (int i = 0; i < length; i++) {
                if (buffer[start + i] != other.byteAt(i)) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    @Override
    public int hashCode() {
        int result = 1;
        for (int i = start; i < start + length; i++) {
            result = 31 * result + buffer[i];
        }
        return result;
    }

    public ByteSequence subSequence(int startIndex, int endIndex) {
        int l = endIndex - startIndex;
        if (l < 0) {
            throw new IndexOutOfBoundsException(String.valueOf(l));
        }
        final int realStartIndex = start + startIndex;
        if (realStartIndex < 0) {
            throw new IndexOutOfBoundsException(String.valueOf(startIndex));
        }
        if (realStartIndex + l > length()) {
            throw new IndexOutOfBoundsException(String.valueOf(realStartIndex + l));
        }
        return new ByteArraySequence(buffer, realStartIndex, l);
    }

}
