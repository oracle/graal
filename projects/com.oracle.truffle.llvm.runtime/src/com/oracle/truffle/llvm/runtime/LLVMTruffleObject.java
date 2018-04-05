/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates.
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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.CompilerDirectives.ValueType;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType;
import com.oracle.truffle.llvm.runtime.interop.export.LLVMTruffleObjectMessageResolutionForeign;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMObjectNativeLibrary;

@ValueType
public final class LLVMTruffleObject implements LLVMObjectNativeLibrary.Provider, TruffleObject {

    private final TruffleObject object;
    private final long offset;

    private final LLVMInteropType exportType;

    public static LLVMTruffleObject createNullPointer() {
        return createPointer(0L);
    }

    public static LLVMTruffleObject createPointer(long ptr) {
        return new LLVMTruffleObject(null, ptr, null);
    }

    public LLVMTruffleObject(TruffleObject object) {
        this(object, 0, null);
    }

    private LLVMTruffleObject(TruffleObject object, long offset, LLVMInteropType exportType) {
        this.object = object;
        this.offset = offset;
        this.exportType = exportType;
    }

    public long getOffset() {
        return offset;
    }

    public TruffleObject getObject() {
        return object;
    }

    public boolean isNative() {
        return object == null;
    }

    public LLVMAddress asNative() {
        assert isNative();
        return LLVMAddress.fromLong(offset);
    }

    public boolean isManaged() {
        return object != null;
    }

    public LLVMInteropType getExportType() {
        return exportType;
    }

    public LLVMTruffleObject increment(long incr) {
        // reset type, since the result points to something else now
        return new LLVMTruffleObject(object, offset + incr, null);
    }

    public LLVMTruffleObject export(LLVMInteropType newType) {
        return new LLVMTruffleObject(object, offset, newType);
    }

    @Override
    public ForeignAccess getForeignAccess() {
        return LLVMTruffleObjectMessageResolutionForeign.ACCESS;
    }

    public static boolean isInstance(TruffleObject obj) {
        return obj instanceof LLVMTruffleObject;
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
        @Child private Node isNull;

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
        public boolean isPointer(Object obj) {
            LLVMTruffleObject object = (LLVMTruffleObject) obj;
            if (lib.isPointer(object.getObject())) {
                return true;
            } else {
                if (isNull == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    isNull = insert(Message.IS_NULL.createNode());
                }
                return ForeignAccess.sendIsNull(isNull, object.getObject());
            }
        }

        @Override
        public long asPointer(Object obj) throws InteropException {
            LLVMTruffleObject object = (LLVMTruffleObject) obj;
            if (isNull == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                isNull = insert(Message.IS_NULL.createNode());
            }
            if (ForeignAccess.sendIsNull(isNull, object.getObject())) {
                return object.getOffset();
            } else {
                long base = lib.asPointer(object.getObject());
                return base + object.getOffset();
            }
        }

        @Override
        public Object toNative(Object obj) throws InteropException {
            LLVMTruffleObject object = (LLVMTruffleObject) obj;
            Object nativeBase = lib.toNative(object.getObject());
            // keep exportType, this is still logically pointing to the same thing
            return new LLVMTruffleObject((TruffleObject) nativeBase, object.offset, object.exportType);
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
        public boolean isPointer(Object obj) {
            return true;
        }

        @Override
        public long asPointer(Object obj) throws InteropException {
            return ((LLVMTruffleObject) obj).getOffset();
        }

        @Override
        public Object toNative(Object obj) throws InteropException {
            return obj;
        }
    }
}
