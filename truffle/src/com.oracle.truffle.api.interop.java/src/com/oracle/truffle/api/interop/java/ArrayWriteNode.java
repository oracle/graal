/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.interop.java;

import java.util.List;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.java.ArrayWriteNodeGen.ArraySetNodeGen;
import com.oracle.truffle.api.nodes.Node;

abstract class ArrayWriteNode extends Node {

    abstract static class ArraySet extends Node {

        protected abstract void execute(Object array, int index, Object value);

        @Specialization
        void doBoolean(boolean[] array, int index, boolean value) {
            array[index] = value;
        }

        @Specialization
        void doByte(byte[] array, int index, byte value) {
            array[index] = value;
        }

        @Specialization
        void doShort(short[] array, int index, short value) {
            array[index] = value;
        }

        @Specialization
        void doChar(char[] array, int index, char value) {
            array[index] = value;
        }

        @Specialization
        void doInt(int[] array, int index, int value) {
            array[index] = value;
        }

        @Specialization
        void doLong(long[] array, int index, long value) {
            array[index] = value;
        }

        @Specialization
        void doFloat(float[] array, int index, float value) {
            array[index] = value;
        }

        @Specialization
        void doDouble(double[] array, int index, double value) {
            array[index] = value;
        }

        @Specialization
        void doObject(Object[] array, int index, Object value) {
            array[index] = value;
        }
    }

    @Child private ToJavaNode toJavaNode = ToJavaNode.create();
    @Child private ArraySet arraySet = ArraySetNodeGen.create();

    protected abstract Object executeWithTarget(JavaObject receiver, Object index, Object value);

    @Specialization(guards = {"receiver.isArray()"})
    protected final Object doArrayIntIndex(JavaObject receiver, int index, Object value) {
        return doArrayAccess(receiver, index, value);
    }

    @Specialization(guards = {"receiver.isArray()", "index.getClass() == clazz"})
    protected final Object doArrayCached(JavaObject receiver, Number index, Object value,
                    @Cached("index.getClass()") Class<? extends Number> clazz) {
        return doArrayAccess(receiver, clazz.cast(index).intValue(), value);
    }

    @Specialization(guards = {"receiver.isArray()"}, replaces = "doArrayCached")
    protected final Object doArrayGeneric(JavaObject receiver, Number index, Object value) {
        return doArrayAccess(receiver, index.intValue(), value);
    }

    @SuppressWarnings("unchecked")
    @TruffleBoundary
    @Specialization(guards = {"isList(receiver)"})
    protected Object doListIntIndex(JavaObject receiver, int index, Object value) {
        final Object javaValue = toJavaNode.execute(value, Object.class, null, receiver.languageContext);
        try {
            List<Object> list = ((List<Object>) receiver.obj);
            if (index == list.size()) {
                list.add(javaValue);
            } else {
                list.set(index, javaValue);
            }
            return value;
        } catch (IndexOutOfBoundsException e) {
            CompilerDirectives.transferToInterpreter();
            throw UnknownIdentifierException.raise(String.valueOf(index));
        }
    }

    @TruffleBoundary
    @Specialization(guards = {"isList(receiver)"}, replaces = "doListIntIndex")
    protected Object doListGeneric(JavaObject receiver, Number index, Object value) {
        return doListIntIndex(receiver, index.intValue(), value);
    }

    @SuppressWarnings("unused")
    @TruffleBoundary
    @Specialization(guards = {"!receiver.isArray()", "!isList(receiver)"})
    protected static Object notArray(JavaObject receiver, Number index, Object value) {
        throw UnsupportedMessageException.raise(Message.WRITE);
    }

    private Object doArrayAccess(JavaObject receiver, int index, Object value) {
        Object obj = receiver.obj;
        assert receiver.isArray();
        final Object javaValue = toJavaNode.execute(value, obj.getClass().getComponentType(), null, receiver.languageContext);
        try {
            arraySet.execute(obj, index, javaValue);
        } catch (ArrayIndexOutOfBoundsException outOfBounds) {
            CompilerDirectives.transferToInterpreter();
            throw UnknownIdentifierException.raise(String.valueOf(index));
        }
        return JavaObject.NULL;
    }

    static boolean isList(JavaObject receiver) {
        return receiver.obj instanceof List;
    }

    static ArrayWriteNode create() {
        return ArrayWriteNodeGen.create();
    }
}
