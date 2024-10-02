/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Array;

public class ObjectCopierOutputStream extends DataOutputStream {
    public ObjectCopierOutputStream(OutputStream out) {
        super(out);
    }

    public void writeTypedPrimitiveArray(Object value) throws IOException {
        Class<?> compClz = value.getClass().componentType();
        int length = Array.getLength(value);
        this.writeInt(length);
        if (compClz == boolean.class) {
            this.writeByte('Z');
            for (int i = 0; i < length; i++) {
                this.writeBoolean(Array.getBoolean(value, i));
            }
        } else if (compClz == byte.class) {
            this.writeByte('B');
            for (int i = 0; i < length; i++) {
                this.writeByte(Array.getByte(value, i));
            }
        } else if (compClz == short.class) {
            this.writeByte('S');
            for (int i = 0; i < length; i++) {
                this.writeShort(Array.getShort(value, i));
            }
        } else if (compClz == char.class) {
            this.writeByte('C');
            for (int i = 0; i < length; i++) {
                this.writeChar(Array.getChar(value, i));
            }
        } else if (compClz == int.class) {
            this.writeByte('I');
            for (int i = 0; i < length; i++) {
                this.writeInt(Array.getInt(value, i));
            }
        } else if (compClz == long.class) {
            this.writeByte('J');
            for (int i = 0; i < length; i++) {
                this.writeLong(Array.getLong(value, i));
            }
        } else if (compClz == float.class) {
            this.writeByte('F');
            for (int i = 0; i < length; i++) {
                this.writeFloat(Array.getFloat(value, i));
            }
        } else if (compClz == double.class) {
            this.writeByte('D');
            for (int i = 0; i < length; i++) {
                this.writeDouble(Array.getDouble(value, i));
            }
        } else {
            throw new IllegalArgumentException(String.format("Unsupported array: Value: %s, Value type: %s", value, value.getClass()));
        }
    }
}
