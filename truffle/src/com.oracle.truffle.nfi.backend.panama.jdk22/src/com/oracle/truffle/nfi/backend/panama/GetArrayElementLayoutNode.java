/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.nfi.backend.panama;

import java.lang.foreign.ValueLayout;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.nfi.backend.panama.GetArrayElementLayoutNodeFactory.GetByteArrayElementLayoutNodeGen;
import com.oracle.truffle.nfi.backend.panama.GetArrayElementLayoutNodeFactory.GetDoubleArrayElementLayoutNodeGen;
import com.oracle.truffle.nfi.backend.panama.GetArrayElementLayoutNodeFactory.GetFloatArrayElementLayoutNodeGen;
import com.oracle.truffle.nfi.backend.panama.GetArrayElementLayoutNodeFactory.GetIntArrayElementLayoutNodeGen;
import com.oracle.truffle.nfi.backend.panama.GetArrayElementLayoutNodeFactory.GetLongArrayElementLayoutNodeGen;
import com.oracle.truffle.nfi.backend.panama.GetArrayElementLayoutNodeFactory.GetShortArrayElementLayoutNodeGen;
import com.oracle.truffle.nfi.backend.spi.types.NativeSimpleType;

abstract class GetArrayElementLayoutNode extends Node {

    abstract ValueLayout execute(Object value);

    @Fallback
    ValueLayout doOther(@SuppressWarnings("unused") Object value) {
        return null;
    }

    static GetArrayElementLayoutNode create(NativeSimpleType type) {
        return switch (type) {
            case UINT8, SINT8 -> GetByteArrayElementLayoutNodeGen.create();
            case UINT16, SINT16 -> GetShortArrayElementLayoutNodeGen.create();
            case UINT32, SINT32 -> GetIntArrayElementLayoutNodeGen.create();
            case UINT64, SINT64 -> GetLongArrayElementLayoutNodeGen.create();
            case FLOAT -> GetFloatArrayElementLayoutNodeGen.create();
            case DOUBLE -> GetDoubleArrayElementLayoutNodeGen.create();
            default -> throw CompilerDirectives.shouldNotReachHere(type.name());
        };
    }

    @GenerateInline(false)
    abstract static class GetByteArrayElementLayoutNode extends GetArrayElementLayoutNode {

        @Specialization
        ValueLayout doByteArray(byte[] array) {
            assert array != null;
            return ValueLayout.JAVA_BYTE;
        }
    }

    @GenerateInline(false)
    abstract static class GetShortArrayElementLayoutNode extends GetArrayElementLayoutNode {

        @Specialization
        ValueLayout doShortArray(short[] array) {
            assert array != null;
            return ValueLayout.JAVA_SHORT;
        }

        @Specialization
        ValueLayout doCharArray(char[] array) {
            assert array != null;
            return ValueLayout.JAVA_CHAR;
        }
    }

    @GenerateInline(false)
    abstract static class GetIntArrayElementLayoutNode extends GetArrayElementLayoutNode {

        @Specialization
        ValueLayout doByteArray(int[] array) {
            assert array != null;
            return ValueLayout.JAVA_INT;
        }
    }

    @GenerateInline(false)
    abstract static class GetLongArrayElementLayoutNode extends GetArrayElementLayoutNode {

        @Specialization
        ValueLayout doByteArray(long[] array) {
            assert array != null;
            return ValueLayout.JAVA_LONG;
        }
    }

    @GenerateInline(false)
    abstract static class GetFloatArrayElementLayoutNode extends GetArrayElementLayoutNode {

        @Specialization
        ValueLayout doByteArray(float[] array) {
            assert array != null;
            return ValueLayout.JAVA_FLOAT;
        }
    }

    @GenerateInline(false)
    abstract static class GetDoubleArrayElementLayoutNode extends GetArrayElementLayoutNode {

        @Specialization
        ValueLayout doByteArray(double[] array) {
            assert array != null;
            return ValueLayout.JAVA_DOUBLE;
        }
    }
}
