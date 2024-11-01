/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.util;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * A stream that can read (trivial) values using their in-band data type information, intended for
 * use with {@link TypedDataOutputStream}.
 */
public class TypedDataInputStream extends DataInputStream {
    public TypedDataInputStream(InputStream in) {
        super(in);
    }

    /**
     * Reads a single value, using the data type encoded in the stream.
     *
     * @return The read value, such as a boxed primitive or a {@link String}.
     * @exception IOException in case of an I/O error.
     */
    public Object readTypedValue() throws IOException {
        return readUntypedValue(readUnsignedByte());
    }

    protected Object readUntypedValue(int type) throws IOException {
        return switch (type) {
            case 'Z' -> readBoolean();
            case 'B' -> readByte();
            case 'S' -> readShort();
            case 'C' -> readChar();
            case 'I' -> readInt();
            case 'J' -> readLong();
            case 'F' -> readFloat();
            case 'D' -> readDouble();
            case 'U' -> readStringValue();
            default -> throw new IOException("Unsupported type: " + Integer.toHexString(type));
        };
    }

    protected String readStringValue() throws IOException {
        int len = readInt();
        byte[] bytes = new byte[len];
        readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
