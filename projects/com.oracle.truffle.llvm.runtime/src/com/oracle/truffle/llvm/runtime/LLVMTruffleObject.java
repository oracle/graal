/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.CompilerDirectives.ValueType;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.llvm.runtime.debug.LLVMSourceType;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMObjectNativeLibrary;
import com.oracle.truffle.llvm.runtime.types.Type;

@ValueType
public final class LLVMTruffleObject implements LLVMObjectNativeLibrary.Provider {
    private final TruffleObject object;
    private final long offset;
    private final LLVMSourceType baseType;

    private static LLVMSourceType overrideBaseType(LLVMTruffleObject obj, LLVMSourceType newType) {
        if (obj.getOffset() == 0) {
            if (newType != null) {
                return newType;
            } else {
                return obj.getBaseType();
            }
        } else {
            return obj.getBaseType();
        }
    }

    public static LLVMTruffleObject createNullPointer() {
        return createPointer(0L);
    }

    public static LLVMTruffleObject createPointer(long ptr) {
        return new LLVMTruffleObject(null, ptr, null);
    }

    public LLVMTruffleObject(LLVMTruffleObject orig, Type type) {
        this(orig, type.getSourceType());
    }

    public LLVMTruffleObject(LLVMTruffleObject orig, LLVMSourceType type) {
        this(orig.getObject(), orig.getOffset(), overrideBaseType(orig, type));
    }

    public LLVMTruffleObject(TruffleObject object, Type type) {
        this(object, 0, type.getSourceType());
    }

    private LLVMTruffleObject(TruffleObject object, long offset, LLVMSourceType baseType) {
        this.object = object;
        this.offset = offset;
        this.baseType = baseType;
    }

    public long getOffset() {
        return offset;
    }

    public TruffleObject getObject() {
        return object;
    }

    public LLVMTruffleObject increment(long incr) {
        return new LLVMTruffleObject(object, offset + incr, baseType);
    }

    public LLVMSourceType getBaseType() {
        return baseType;
    }

    @TruffleBoundary
    @Override
    public String toString() {
        return getClass().getSimpleName() + "(" + (object == null ? "null" : object.getClass().getSimpleName()) + ":" + getOffset() + ")";
    }

    @Override
    public LLVMObjectNativeLibrary createLLVMObjectNativeLibrary() {
        if (object != null) {
            return new LLVMTruffleObjectNativeLibrary(LLVMObjectNativeLibrary.createCached(object));
        } else {
            return new LLVMTruffleObjectNullPointerNativeLibrary();
        }
    }

    private static final class LLVMTruffleObjectNativeLibrary extends LLVMObjectNativeLibrary {
        private final LLVMObjectNativeLibrary lib;

        private LLVMTruffleObjectNativeLibrary(LLVMObjectNativeLibrary lib) {
            this.lib = lib;
        }

        @Override
        public boolean guard(Object obj) {
            if (obj instanceof LLVMTruffleObject) {
                LLVMTruffleObject object = (LLVMTruffleObject) obj;
                return lib.guard(object.getObject());
            }
            return false;
        }

        @Override
        public boolean isPointer(VirtualFrame frame, Object obj) {
            LLVMTruffleObject object = (LLVMTruffleObject) obj;
            return lib.isPointer(frame, object.getObject());
        }

        @Override
        public long asPointer(VirtualFrame frame, Object obj) throws InteropException {
            LLVMTruffleObject object = (LLVMTruffleObject) obj;
            long base = lib.asPointer(frame, object.getObject());
            return base + object.getOffset();
        }

        @Override
        public Object toNative(VirtualFrame frame, Object obj) throws InteropException {
            LLVMTruffleObject object = (LLVMTruffleObject) obj;
            Object nativeBase = lib.toNative(frame, object.getObject());
            return new LLVMTruffleObject((TruffleObject) nativeBase, object.offset, object.baseType);
        }
    }

    private static final class LLVMTruffleObjectNullPointerNativeLibrary extends LLVMObjectNativeLibrary {
        private LLVMTruffleObjectNullPointerNativeLibrary() {
        }

        @Override
        public boolean guard(Object obj) {
            if (obj instanceof LLVMTruffleObject) {
                LLVMTruffleObject object = (LLVMTruffleObject) obj;
                return object.getObject() == null;
            }
            return false;
        }

        @Override
        public boolean isPointer(VirtualFrame frame, Object obj) {
            return true;
        }

        @Override
        public long asPointer(VirtualFrame frame, Object obj) throws InteropException {
            return ((LLVMTruffleObject) obj).getOffset();
        }

        @Override
        public Object toNative(VirtualFrame frame, Object obj) throws InteropException {
            return obj;
        }
    }
}
