/*
 * Copyright (c) 2018, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.runtime.debug.value;

import java.util.Arrays;
import java.util.stream.Collectors;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.llvm.runtime.debug.LLVMDebuggerValue;
import com.oracle.truffle.llvm.runtime.vector.LLVMDoubleVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMFloatVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMFunctionVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI16Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI1Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI32Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI64Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI8Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMPointerVector;

public final class LLVMDebugVector extends LLVMDebuggerValue {

    private static final String NO_TYPE = "";

    private final Object metaObject;
    private final String elementType;
    private final String[] vector;

    private LLVMDebugVector(Object metaObject, String[] vector, String elementType) {
        this.metaObject = metaObject;
        this.elementType = elementType;
        this.vector = vector;
    }

    @TruffleBoundary
    @Override
    public Object getMetaObject() {
        return metaObject == null ? NO_TYPE : String.valueOf(metaObject);
    }

    @Override
    @TruffleBoundary
    public String toString() {
        return String.format("< %d x %s >", vector.length, elementType);
    }

    @Override
    protected int getElementCountForDebugger() {
        return vector.length;
    }

    @Override
    @TruffleBoundary
    protected String[] getKeysForDebugger() {
        if (vector.length <= 0) {
            return NO_KEYS;
        }
        final String[] keys = new String[vector.length];
        for (int i = 0; i < vector.length; i++) {
            keys[i] = String.valueOf(i);
        }
        return keys;
    }

    @Override
    @TruffleBoundary
    protected Object getElementForDebugger(String key) {
        try {
            int i = Integer.parseInt(key);
            if (i < vector.length) {
                return new LLVMDebugGenericValue(vector[i], elementType);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    @TruffleBoundary
    public static LLVMDebugVector create(Object metaObject, Object obj) {
        if (obj instanceof LLVMI64Vector) {
            final String[] elements = Arrays.stream(((LLVMI64Vector) obj).getValues()).mapToObj(String::valueOf).collect(Collectors.toList()).toArray(NO_KEYS);
            return new LLVMDebugVector(metaObject, elements, "i64");

        } else if (obj instanceof LLVMI32Vector) {
            final String[] elements = Arrays.stream(((LLVMI32Vector) obj).getValues()).mapToObj(String::valueOf).collect(Collectors.toList()).toArray(NO_KEYS);
            return new LLVMDebugVector(metaObject, elements, "i32");

        } else if (obj instanceof LLVMI16Vector) {
            final short[] values = ((LLVMI16Vector) obj).getValues();
            final String[] elements = new String[values.length];
            for (int i = 0; i < values.length; i++) {
                elements[i] = String.valueOf(values[i]);
            }
            return new LLVMDebugVector(metaObject, elements, "i16");

        } else if (obj instanceof LLVMI8Vector) {
            final byte[] values = ((LLVMI8Vector) obj).getValues();
            final String[] elements = new String[values.length];
            for (int i = 0; i < values.length; i++) {
                elements[i] = String.valueOf(values[i]);
            }
            return new LLVMDebugVector(metaObject, elements, "i8");

        } else if (obj instanceof LLVMI1Vector) {
            final boolean[] values = ((LLVMI1Vector) obj).getValues();
            final String[] elements = new String[values.length];
            for (int i = 0; i < values.length; i++) {
                elements[i] = String.valueOf(values[i]);
            }
            return new LLVMDebugVector(metaObject, elements, "i1");

        } else if (obj instanceof LLVMDoubleVector) {
            final String[] elements = Arrays.stream(((LLVMDoubleVector) obj).getValues()).mapToObj(String::valueOf).collect(Collectors.toList()).toArray(NO_KEYS);
            return new LLVMDebugVector(metaObject, elements, "double");

        } else if (obj instanceof LLVMFloatVector) {
            final float[] values = ((LLVMFloatVector) obj).getValues();
            final String[] elements = new String[values.length];
            for (int i = 0; i < values.length; i++) {
                elements[i] = String.valueOf(values[i]);
            }
            return new LLVMDebugVector(metaObject, elements, "float");

        } else if (obj instanceof LLVMPointerVector) {
            final String[] elements = Arrays.stream(((LLVMPointerVector) obj).getValues()).mapToObj(String::valueOf).collect(Collectors.toList()).toArray(NO_KEYS);
            return new LLVMDebugVector(metaObject, elements, "void*");

        } else if (obj instanceof LLVMFunctionVector) {
            final String[] elements = Arrays.stream(((LLVMFunctionVector) obj).getValues()).map(String::valueOf).collect(Collectors.toList()).toArray(NO_KEYS);
            return new LLVMDebugVector(metaObject, elements, "void*");

        } else {
            return null;
        }
    }
}
