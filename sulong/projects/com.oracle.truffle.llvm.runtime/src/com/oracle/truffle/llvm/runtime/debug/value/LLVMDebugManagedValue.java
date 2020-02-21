/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates.
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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.llvm.runtime.debug.LLVMDebuggerValue;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.types.VectorType;
import com.oracle.truffle.llvm.runtime.vector.LLVMVector;

public abstract class LLVMDebugManagedValue extends LLVMDebuggerValue {

    final Object llvmType;

    private LLVMDebugManagedValue(Object llvmType) {
        this.llvmType = llvmType;
    }

    @ExportMessage
    public final Object getMetaObject() throws UnsupportedMessageException {
        if (llvmType == null) {
            throw UnsupportedMessageException.create();
        }
        return new LLVMDebugManagedType(llvmType);
    }

    @ExportMessage
    public final boolean hasMetaObject() {
        return llvmType != null;
    }

    public static LLVMDebugManagedValue create(Object llvmType, Object value) {
        if (LLVMManagedPointer.isInstance(value)) {
            return new Pointer(llvmType, LLVMManagedPointer.cast(value));

        } else if (value instanceof LLVMVector) {
            final LLVMVector vector = (LLVMVector) value;
            if (vector.getLength() <= 0) {
                return new GenericVector(llvmType, NO_KEYS, String.valueOf(vector.getElementType()));
            }
            final String[] elements = new String[vector.getLength()];
            for (int i = 0; i < elements.length; i++) {
                elements[i] = String.valueOf(vector.getElement(i));
            }
            return new GenericVector(llvmType, elements, String.valueOf(vector.getElementType()));

        } else {
            return new Generic(llvmType, value);
        }
    }

    private static final class Pointer extends LLVMDebugManagedValue {

        private static final String VALUE = "<managed pointer>";
        private static final String[] DEFAULT_KEYS = new String[]{"<target>"};

        private final LLVMManagedPointer pointer;

        private Pointer(Object llvmType, LLVMManagedPointer pointer) {
            super(llvmType);
            this.pointer = pointer;
        }

        @Override
        public String toString() {
            return VALUE;
        }

        @TruffleBoundary
        private String[] getTargetKeys() {
            if (pointer.getOffset() == 0) {
                return DEFAULT_KEYS;
            } else {
                return new String[]{"<target (offset " + pointer.getOffset() + " ignored)>"};
            }
        }

        @Override
        protected int getElementCountForDebugger() {
            return getTargetKeys().length;
        }

        @Override
        protected String[] getKeysForDebugger() {
            return getTargetKeys();
        }

        @Override
        protected Object getElementForDebugger(String key) {
            assert getTargetKeys()[0].equals(key);
            return pointer.getObject();
        }
    }

    private static final class GenericVector extends LLVMDebugManagedValue {

        private final String elementType;
        private final String[] vector;

        private GenericVector(Object llvmType, String[] vector, String elementType) {
            super(llvmType);
            this.elementType = elementType;
            this.vector = vector;
        }

        @Override
        @TruffleBoundary
        public String toString() {
            if (llvmType instanceof VectorType && ((VectorType) llvmType).getNumberOfElements() != 0 && vector.length == 0) {
                return "(optimized away)";
            }

            return "< " + vector.length + " x " + elementType + " >";
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
                keys[i] = String.format("[%d]", i);
            }
            return keys;
        }

        @Override
        @TruffleBoundary
        protected Object getElementForDebugger(String key) {
            int i;

            try {
                i = Integer.parseInt(key.substring(1, key.length() - 1));
            } catch (NumberFormatException | StringIndexOutOfBoundsException e) {
                throw new IllegalArgumentException("Vector has no member named " + key);
            }

            if (i < vector.length) {
                return new Generic(elementType, vector[i]);
            }

            throw new IllegalArgumentException("Cannot access index " + i + " in vector of length " + vector.length);
        }
    }

    private static final class Generic extends LLVMDebugManagedValue {

        private static final String[] INTEROP_KEYS = new String[]{"<value>"};

        private final Object value;

        private Generic(Object llvmType, Object value) {
            super(llvmType);
            this.value = value;
        }

        @Override
        @TruffleBoundary
        public String toString() {
            return String.valueOf(value);
        }

        @Override
        protected int getElementCountForDebugger() {
            return getKeysForDebugger().length;
        }

        @Override
        protected String[] getKeysForDebugger() {
            return value instanceof TruffleObject ? INTEROP_KEYS : NO_KEYS;
        }

        @Override
        protected Object getElementForDebugger(String key) {
            assert INTEROP_KEYS[0].equals(key);
            return value;
        }

    }
}
