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
package com.oracle.truffle.llvm.runtime.pointer;

import java.util.Objects;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.ValueType;
import com.oracle.truffle.api.library.DynamicDispatchLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType;

@ValueType
@ExportLibrary(DynamicDispatchLibrary.class)
class LLVMPointerImpl implements LLVMManagedPointer, LLVMNativePointer {

    static final LLVMPointerImpl NULL = new LLVMPointerImpl(null, 0, null);

    final Object object;
    private final long offset;

    private final LLVMInteropType exportType;

    LLVMPointerImpl(Object object, long offset, LLVMInteropType exportType) {
        this.object = object;
        this.offset = offset;
        this.exportType = exportType;
    }

    @Override
    @Deprecated
    @SuppressWarnings("deprecation")
    public boolean equals(Object obj) {
        CompilerAsserts.neverPartOfCompilation();
        if (!(obj instanceof LLVMPointerImpl)) {
            return false;
        }
        LLVMPointerImpl other = (LLVMPointerImpl) obj;
        return Objects.equals(this.object, other.object) && this.offset == other.offset;
    }

    @Override
    public boolean isSame(LLVMPointer o) {
        LLVMPointerImpl other = (LLVMPointerImpl) o; // can not fail, there is only one subclass
        return this.object == other.object && this.offset == other.offset;
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
    public Object getObject() {
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

    @ExportMessage
    Class<?> dispatch() {
        if (isNative()) {
            return NativePointerLibraries.class;
        } else {
            assert isManaged();
            return ManagedPointerLibraries.class;
        }
    }
}
