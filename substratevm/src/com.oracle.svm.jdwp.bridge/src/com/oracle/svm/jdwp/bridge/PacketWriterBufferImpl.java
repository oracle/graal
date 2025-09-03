/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.jdwp.bridge;

import java.util.Arrays;

final class PacketWriterBufferImpl implements PacketWriterBuffer {
    private static final int INITIAL_CAPACITY = 64;

    PacketWriterBufferImpl() {
        this(INITIAL_CAPACITY);
    }

    PacketWriterBufferImpl(int initialCapacity) {
        this.size = 0;
        this.bytes = new byte[initialCapacity];
    }

    private int size;
    private byte[] bytes;

    private void ensureCapacity(int capacity) {
        if (capacity < bytes.length) {
            return;
        }
        int newSize = size();
        if (newSize == 0) {
            newSize = 1;
        }
        while (newSize < capacity) {
            newSize = newSize * 2;
        }
        this.bytes = Arrays.copyOf(this.bytes, newSize);
    }

    @Override
    public void writeByte(int value) {
        ensureCapacity(size + 1);
        bytes[size++] = (byte) value;
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public byte[] toByteArray() {
        return Arrays.copyOf(this.bytes, size());
    }

    @Override
    public byte byteAt(int index) {
        if (index >= size()) {
            throw new ArrayIndexOutOfBoundsException();
        }
        return this.bytes[index];
    }
}
