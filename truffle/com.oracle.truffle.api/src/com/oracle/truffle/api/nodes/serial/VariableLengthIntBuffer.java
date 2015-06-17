/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.nodes.serial;

import java.nio.*;

/**
 * Experimental API. May change without notice. Simple variable length unsigned int buffer backed by
 * a byte buffer.
 */
public class VariableLengthIntBuffer {

    public static final int NULL = -1;

    private ByteBuffer buffer;

    public VariableLengthIntBuffer(ByteBuffer buffer) {
        this.buffer = buffer;
    }

    public VariableLengthIntBuffer(byte[] array) {
        buffer = ByteBuffer.wrap(array);
    }

    /**
     * Returns the backing byte buffer.
     */
    public ByteBuffer getBuffer() {
        return buffer;
    }

    public byte[] getBytes() {
        int pos = buffer.position();
        byte[] bytes = new byte[buffer.position()];
        buffer.rewind();
        buffer.get(bytes);
        buffer.position(pos);
        return bytes;
    }

    public int get() {
        byte peekByte = buffer.get(buffer.position());
        if ((peekByte & 0x80) == 0) {
            // single byte encoding with prefix 0 (range 127)
            return buffer.get(); // no bit to be masked
        } else {
            if (peekByte == (byte) 0xFF) {
                buffer.get(); // skip one byte
                return NULL;
            }
            int result = buffer.getInt() & 0x7FFF_FFFF; // first bit masked out
            assert (result & 0x4000_0000) == 0;
            return result;
        }
    }

    public void put(int i) {
        ensureCapacity();
        if (i == NULL) {
            buffer.put((byte) 0xFF);
        } else if ((i & 0xFFFF_FF80) == 0) { // 7 bits data
            buffer.put((byte) i);
        } else if ((i & 0xC000_0000) == 0) { // 32 bits data
            buffer.putInt(i | 0x8000_0000); // append leading 1
        } else {
            throw new IllegalArgumentException("Integer out of encodeable " + i);
        }
    }

    private void ensureCapacity() {
        if (buffer.position() + 4 > buffer.capacity()) {
            ByteBuffer newBuffer = ByteBuffer.allocate(buffer.capacity() * 2);

            int pos = buffer.position();
            buffer.rewind();
            newBuffer.put(buffer);
            newBuffer.position(pos);

            buffer = newBuffer;
        }
    }

    public boolean hasRemaining() {
        return buffer.hasRemaining();
    }

}
