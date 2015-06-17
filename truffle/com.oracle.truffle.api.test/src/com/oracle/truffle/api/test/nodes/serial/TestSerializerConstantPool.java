/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.nodes.serial;

import java.util.*;

import com.oracle.truffle.api.nodes.serial.*;

class TestSerializerConstantPool implements SerializerConstantPool {

    private final Map<Integer, Object> int2object = new HashMap<>();
    private final Map<Object, Integer> object2int = new HashMap<>();

    private int index;

    public void setIndex(int index) {
        this.index = index;
    }

    public int getIndex() {
        return index;
    }

    @Override
    public double getDouble(int cpi) {
        return (Double) int2object.get(cpi);
    }

    @Override
    public float getFloat(int cpi) {
        return (Float) int2object.get(cpi);
    }

    @Override
    public Object getObject(Class<?> clazz, int cpi) throws UnsupportedConstantPoolTypeException {
        return int2object.get(cpi);
    }

    @Override
    public int putDouble(double value) {
        return put(value);
    }

    public int putFloat(float value) {
        return put(value);
    }

    public int putObject(java.lang.Class<?> clazz, Object value) throws UnsupportedConstantPoolTypeException {
        return put(value);
    }

    @Override
    public int putClass(Class<?> clazz) {
        return put(clazz);
    }

    private int put(Object o) {
        Integer currentIndex = object2int.get(o);
        if (currentIndex == null) {
            int2object.put(index, o);
            object2int.put(o, index);
            return index++;
        } else {
            return currentIndex;
        }
    }

    @Override
    public Class<?> getClass(int idx) {
        return (Class<?>) int2object.get(idx);
    }

    @Override
    public int putInt(int constant) {
        return put(constant);
    }

    @Override
    public int getInt(int idx) {
        return (Integer) int2object.get(idx);
    }

    @Override
    public long getLong(int idx) {
        return (Long) int2object.get(idx);
    }

    @Override
    public int putLong(long value) {
        return put(value);
    }

}
