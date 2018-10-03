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
package com.oracle.truffle.llvm.runtime.pointer;

import java.util.Objects;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.ValueType;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType;
import com.oracle.truffle.llvm.runtime.interop.export.LLVMPointerMessageResolutionForeign;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMObjectNativeLibrary;

@ValueType
class LLVMPointerImpl implements LLVMManagedPointer, LLVMNativePointer, LLVMObjectNativeLibrary.Provider {

    private final TruffleObject object;
    private final long offset;

    private final LLVMInteropType exportType;

    LLVMPointerImpl(TruffleObject object, long offset, LLVMInteropType exportType) {
        this.object = object;
        this.offset = offset;
        this.exportType = exportType;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof LLVMPointerImpl)) {
            return false;
        }
        LLVMPointerImpl other = (LLVMPointerImpl) obj;
        return Objects.equals(this.object, other.object) && this.offset == other.offset;
    }

    @Override
    public int hashCode() {
        int result = 1;
        result = 31 * result + Objects.hashCode(object);
        result = 31 * result + Long.hashCode(offset);
        return result;
    }

    boolean isNative() {
        return object == null;
    }

    @Override
    public long asNative() {
        assert isNative();
        return offset;
    }

    boolean isManaged() {
        return object != null;
    }

    @Override
    public TruffleObject getObject() {
        assert isManaged();
        return object;
    }

    @Override
    public long getOffset() {
        assert isManaged();
        return offset;
    }

    @Override
    public boolean isNull() {
        return object == null && offset == 0;
    }

    @Override
    public LLVMInteropType getExportType() {
        return exportType;
    }

    @Override
    public LLVMPointerImpl copy() {
        if (CompilerDirectives.inCompiledCode()) {
            return new LLVMPointerImpl(object, offset, exportType);
        } else {
            return this;
        }
    }

    @Override
    public LLVMPointerImpl increment(long incr) {
        // reset type, since the result points to something else now
        return new LLVMPointerImpl(object, offset + incr, null);
    }

    @Override
    public LLVMPointerImpl export(LLVMInteropType newType) {
        return new LLVMPointerImpl(object, offset, newType);
    }

    @Override
    public String toString() {
        CompilerAsserts.neverPartOfCompilation();
        if (isNative()) {
            return String.format("0x%x", asNative());
        } else {
            return String.format("%s+0x%x", getObject().getClass().getSimpleName(), getOffset());
        }
    }

    @Override
    public ForeignAccess getForeignAccess() {
        return LLVMPointerMessageResolutionForeign.ACCESS;
    }

    @Override
    public LLVMObjectNativeLibrary createLLVMObjectNativeLibrary() {
        if (isManaged()) {
            return new LLVMManagedPointerNativeLibrary(LLVMObjectNativeLibrary.createCached(getObject()));
        } else {
            return new LLVMNativePointerNativeLibrary();
        }
    }

    private static final class LLVMManagedPointerNativeLibrary extends LLVMObjectNativeLibrary {

        @Child private LLVMObjectNativeLibrary lib;

        private LLVMManagedPointerNativeLibrary(LLVMObjectNativeLibrary lib) {
            this.lib = lib;
        }

        @Override
        public boolean guard(Object obj) {
            if (LLVMManagedPointer.isInstance(obj)) {
                LLVMManagedPointer pointer = LLVMManagedPointer.cast(obj);
                return lib.guard(pointer.getObject());
            }
            return false;
        }

        @Override
        public boolean isPointer(Object obj) {
            LLVMManagedPointer pointer = LLVMManagedPointer.cast(obj);
            if (lib.isPointer(pointer.getObject())) {
                return true;
            } else {
                return lib.isNull(pointer.getObject());
            }
        }

        @Override
        public boolean isNull(Object obj) {
            LLVMManagedPointer pointer = LLVMManagedPointer.cast(obj);
            return lib.isNull(pointer.getObject());
        }

        @Override
        public long asPointer(Object obj) throws InteropException {
            LLVMManagedPointer pointer = LLVMManagedPointer.cast(obj);
            if (lib.isNull(pointer.getObject())) {
                return pointer.getOffset();
            } else {
                long base = lib.asPointer(pointer.getObject());
                return base + pointer.getOffset();
            }
        }

        @Override
        public Object toNative(Object obj) throws InteropException {
            LLVMManagedPointer pointer = LLVMManagedPointer.cast(obj);
            Object nativeBase = lib.toNative(pointer.getObject());
            // keep exportType, this is still logically pointing to the same thing
            return new LLVMPointerImpl((TruffleObject) nativeBase, pointer.getOffset(), pointer.getExportType());
        }
    }

    private static final class LLVMNativePointerNativeLibrary extends LLVMObjectNativeLibrary {

        private LLVMNativePointerNativeLibrary() {
        }

        @Override
        public boolean guard(Object obj) {
            return LLVMNativePointer.isInstance(obj);
        }

        @Override
        public boolean isPointer(Object obj) {
            return true;
        }

        @Override
        public boolean isNull(Object obj) {
            return LLVMPointer.cast(obj).isNull();
        }

        @Override
        public long asPointer(Object obj) throws InteropException {
            LLVMNativePointer pointer = LLVMNativePointer.cast(obj);
            return pointer.asNative();
        }

        @Override
        public Object toNative(Object obj) throws InteropException {
            return obj;
        }
    }
}
