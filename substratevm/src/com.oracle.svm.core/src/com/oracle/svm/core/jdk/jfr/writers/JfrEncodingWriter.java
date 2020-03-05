/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, Red Hat Inc. All rights reserved.
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

package com.oracle.svm.core.jdk.jfr.writers;

import java.nio.ByteBuffer;

public class JfrEncodingWriter {
    public interface Encoder {
        int encode(ByteBuffer buf, int size, long value);
    }

    public static int writeBE(ByteBuffer buf, int size, long value) {
        switch (size) {
            case Byte.BYTES:
                buf.put((byte) value);
                break;
            case Short.BYTES:
                buf.putShort((short) value);
                break;
            case Integer.BYTES:
                buf.putInt((int) value);
                break;
            case Long.BYTES:
                buf.putLong(value);
                break;
            default:
                // We should never get here
                throw new IllegalArgumentException("Invalid size");
        }
        return size;
    }

    public static int writeCompressed(ByteBuffer buf, int size, long value) {
        for (int i = 1; i <= 8; i++) {
            if ((value & ~0x7FL) == 0L) {
                buf.put((byte) (value & 0x7fL));
                return i;
            } else {
                buf.put((byte) (value & 0x7fL | 0x80L));
            }
            value >>>= 7;
        }
        buf.put((byte) value);
        return 9;
    }

    public static int writePaddedCompressed(ByteBuffer buf, int size, long value) {
        switch (size) {
            case Byte.BYTES:
                buf.put((byte) value);
                break;
            case Short.BYTES:
                buf.put((byte) (value | 0x80));
                buf.put((byte) (value >>> 7));
                break;
            case Integer.BYTES:
                buf.put((byte) (value | 0x80));
                buf.put((byte) (value >>> 7 | 0x80));
                buf.put((byte) (value >>> 14 | 0x80));
                buf.put((byte) (value >>> 21));
                break;
            case Long.BYTES:
                buf.put((byte) (value | 0x80));
                buf.put((byte) (value >>> 7 | 0x80));
                buf.put((byte) (value >>> 14 | 0x80));
                buf.put((byte) (value >>> 21 | 0x80));
                buf.put((byte) (value >>> 28 | 0x80));
                buf.put((byte) (value >>> 35 | 0x80));
                buf.put((byte) (value >>> 42 | 0x80));
                buf.put((byte) (value >>> 49));
                break;
            default:
                // We should never get here
                throw new IllegalArgumentException("Invalid size");
        }
        return size;
    }
}
