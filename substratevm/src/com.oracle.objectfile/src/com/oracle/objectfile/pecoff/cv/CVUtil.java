/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2020, Red Hat Inc. All rights reserved.
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

package com.oracle.objectfile.pecoff.cv;

import com.oracle.objectfile.io.Utf8;

import static java.nio.charset.StandardCharsets.UTF_8;

abstract class CVUtil {

    static int putByte(byte b, byte[] buffer, int initialPos) {
        if (buffer == null) {
            return initialPos + Byte.BYTES;
        }
        int pos = initialPos;
        buffer[pos++] = b;
        return pos;
    }

    static int putShort(short s, byte[] buffer, int initialPos) {
        if (buffer == null) {
            return initialPos + Short.BYTES;
        }
        int pos = initialPos;
        buffer[pos++] = (byte) (s & 0xff);
        buffer[pos++] = (byte) ((s >> 8) & 0xff);
        return pos;
    }

    static int putInt(int i, byte[] buffer, int initialPos) {
        if (buffer == null) {
            return initialPos + Integer.BYTES;
        }
        int pos = initialPos;
        buffer[pos++] = (byte) (i & 0xff);
        buffer[pos++] = (byte) ((i >> 8) & 0xff);
        buffer[pos++] = (byte) ((i >> 16) & 0xff);
        buffer[pos++] = (byte) ((i >> 24) & 0xff);
        return pos;
    }

    @SuppressWarnings("unused")
    static int putLong(long l, byte[] buffer, int initialPos) {
        if (buffer == null) {
            return initialPos + Long.BYTES;
        }
        int pos = initialPos;
        buffer[pos++] = (byte) (l & 0xff);
        buffer[pos++] = (byte) ((l >> 8) & 0xff);
        buffer[pos++] = (byte) ((l >> 16) & 0xff);
        buffer[pos++] = (byte) ((l >> 24) & 0xff);
        buffer[pos++] = (byte) ((l >> 32) & 0xff);
        buffer[pos++] = (byte) ((l >> 40) & 0xff);
        buffer[pos++] = (byte) ((l >> 48) & 0xff);
        buffer[pos++] = (byte) ((l >> 56) & 0xff);
        return pos;
    }

    static int putBytes(byte[] inbuff, byte[] buffer, int initialPos) {
        if (buffer == null) {
            return initialPos + inbuff.length;
        }
        int pos = initialPos;
        for (byte b : inbuff) {
            buffer[pos++] = b;
        }
        return pos;
    }

    static int putUTF8StringBytes(String s, byte[] buffer, int initialPos) {
        assert !s.contains("\0");
        if (buffer == null) {
            return initialPos + Utf8.utf8Length(s) + 1;
        }
        byte[] buff = s.getBytes(UTF_8);
        int pos = putBytes(buff, buffer, initialPos);
        buffer[pos++] = '\0';
        return pos;
    }

    /**
     * Align on 4 byte boundary.
     *
     * @param initialPos initial unaligned position
     * @return pos aligned on 4 byte boundary
     */
    static int align4(int initialPos) {
        int pos = initialPos;
        while ((pos & 0x3) != 0) {
            pos++;
        }
        return pos;
    }
}
