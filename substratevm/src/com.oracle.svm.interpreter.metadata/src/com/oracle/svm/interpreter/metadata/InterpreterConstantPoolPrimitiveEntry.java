/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.interpreter.metadata;

import static com.oracle.svm.espresso.classfile.ConstantPool.CONSTANT_Double;
import static com.oracle.svm.espresso.classfile.ConstantPool.CONSTANT_Float;
import static com.oracle.svm.espresso.classfile.ConstantPool.CONSTANT_Integer;
import static com.oracle.svm.espresso.classfile.ConstantPool.CONSTANT_Long;

/**
 * Internal cache representation for numeric constants, used both when constructing image-heap
 * constant pools and when resolving runtime-loaded classes. Reference constants retain their own
 * representation, so object-valued dynamic constants cannot be mistaken for numeric entries.
 *
 * @param tag the constant-pool tag value, not its enum ordinal
 * @param primitiveValue raw primitive bits; int and float bits are sign-extended from 32 bits, while
 *            long and double bits occupy all 64 bits. Floating-point bits preserve negative zero and
 *            NaN payloads.
 */
public record InterpreterConstantPoolPrimitiveEntry(int tag, long primitiveValue) {

    public InterpreterConstantPoolPrimitiveEntry {
        assert tag == CONSTANT_Integer || tag == CONSTANT_Float || tag == CONSTANT_Long || tag == CONSTANT_Double;
    }

    public int asInt() {
        assert tag == CONSTANT_Integer;
        return (int) primitiveValue;
    }

    public float asFloat() {
        assert tag == CONSTANT_Float;
        return Float.intBitsToFloat((int) primitiveValue);
    }

    public long asLong() {
        assert tag == CONSTANT_Long;
        return primitiveValue;
    }

    public double asDouble() {
        assert tag == CONSTANT_Double;
        return Double.longBitsToDouble(primitiveValue);
    }
}
