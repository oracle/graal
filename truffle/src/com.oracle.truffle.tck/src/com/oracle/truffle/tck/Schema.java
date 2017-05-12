/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tck;

import java.nio.ByteBuffer;
import java.util.List;

final class Schema {

    enum Type {
        DOUBLE(Double.SIZE / Byte.SIZE),
        INT(Integer.SIZE / Byte.SIZE);

        private final int size;

        Type(int size) {
            this.size = size;
        }
    }

    private final int size;
    private final boolean rowBased;
    private final List<String> names;
    private final List<Type> types;

    Schema(int size, boolean rowBased, List<String> names, List<Type> types) {
        this.size = size;
        this.rowBased = rowBased;
        this.names = names;
        this.types = types;
    }

    public int length() {
        return size;
    }

    public Object get(byte[] buffer, int index, String name) {
        assert names.contains(name);
        int offset = rowBased ? getRowOffset(name, index) : getColumnOffset(name, index);
        if (types.get(names.indexOf(name)) == Type.DOUBLE) {
            byte[] b = new byte[Type.DOUBLE.size];
            for (int i = 0; i < Type.DOUBLE.size; i++) {
                b[i] = buffer[offset + i];
            }
            return ByteBuffer.wrap(b).getDouble();
        } else if (types.get(names.indexOf(name)) == Type.INT) {
            byte[] b = new byte[Type.INT.size];
            for (int i = 0; i < Type.INT.size; i++) {
                b[i] = buffer[offset + i];
            }
            return ByteBuffer.wrap(b).getInt();
        }
        throw new IllegalStateException();
    }

    List<String> getNames() {
        return names;
    }

    private int getRowSize() {
        assert rowBased;
        int rowSize = 0;
        for (Type t : types) {
            rowSize += t.size;
        }
        return rowSize;
    }

    private int getRowOffset(String name, int index) {
        assert rowBased;
        if (names.contains(name)) {
            int offset = 0;
            for (int i = 0; i < names.size(); i++) {
                if (names.get(i).equals(name)) {
                    return index * getRowSize() + offset;
                } else {
                    offset += types.get(i).size;
                }
            }
        } else {
            throw new IllegalArgumentException();
        }
        throw new IllegalStateException();
    }

    private int getColumnOffset(String name, int index) {
        assert !rowBased;
        if (names.contains(name)) {
            int offset = 0;
            for (int i = 0; i < names.size(); i++) {
                if (names.get(i).equals(name)) {
                    return offset + index * types.get(i).size;
                } else {
                    offset += types.get(i).size * size;
                }
            }
        } else {
            throw new IllegalArgumentException();
        }
        throw new IllegalStateException();
    }
}
