/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot;

import static com.oracle.graal.graph.UnsafeAccess.*;
import static com.oracle.graal.hotspot.HotSpotGraalRuntime.*;

import java.io.*;

import com.oracle.graal.graph.*;

/**
 * Represents a metaspace {@code Symbol}.
 */
public class HotSpotSymbol {

    private final long metaspaceSymbol;

    public HotSpotSymbol(long metaspaceSymbol) {
        assert metaspaceSymbol != 0;
        this.metaspaceSymbol = metaspaceSymbol;
    }

    /**
     * Decodes this {@code Symbol} and returns the symbol string as {@link java.lang.String}.
     */
    public String asString() {
        return readModifiedUTF8(asByteArray());
    }

    private static String readModifiedUTF8(byte[] buf) {
        try {
            final int length = buf.length;
            byte[] tmp = new byte[length + 2];
            // write modified UTF-8 length as short in big endian
            tmp[0] = (byte) ((length >>> 8) & 0xFF);
            tmp[1] = (byte) ((length >>> 0) & 0xFF);
            // copy the data
            System.arraycopy(buf, 0, tmp, 2, length);
            DataInputStream dis = new DataInputStream(new ByteArrayInputStream(tmp));
            return dis.readUTF();
        } catch (IOException e) {
            // This should never happen so let's fail hard here.
            throw GraalInternalError.shouldNotReachHere("error reading symbol: " + e);
        }
    }

    private byte[] asByteArray() {
        final int length = getLength();
        byte[] result = new byte[length];
        for (int index = 0; index < length; index++) {
            result[index] = getByteAt(index);
        }
        return result;
    }

    private int getLength() {
        return unsafe.getShort(metaspaceSymbol + runtime().getConfig().symbolLengthOffset);
    }

    private byte getByteAt(int index) {
        return unsafe.getByte(metaspaceSymbol + runtime().getConfig().symbolBodyOffset + index);
    }
}
