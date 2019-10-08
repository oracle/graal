/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.nodes.op;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.llvm.runtime.CommonNodeFactory;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM.ForeignToLLVMType;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMNativeLibrary;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.nodes.op.ToComparableValueNodeGen.ManagedToComparableValueNodeGen;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;

public abstract class ToComparableValue extends LLVMNode {

    public abstract long executeWithTarget(Object obj);

    @Specialization(guards = "lib.isPointer(obj)", limit = "3", rewriteOn = UnsupportedMessageException.class)
    protected long doPointer(Object obj,
                    @CachedLibrary("obj") LLVMNativeLibrary lib) throws UnsupportedMessageException {
        return lib.asPointer(obj);
    }

    @Specialization(guards = "lib.isPointer(obj)", limit = "3")
    protected long doPointerException(Object obj,
                    @CachedLibrary("obj") LLVMNativeLibrary lib,
                    @Cached("createUseOffset()") ManagedToComparableValue toComparable) {
        try {
            return lib.asPointer(obj);
        } catch (UnsupportedMessageException ex) {
            return doManaged(obj, lib, toComparable);
        }
    }

    @Specialization(guards = "!lib.isPointer(obj)", limit = "3")
    @SuppressWarnings("unused")
    protected long doManaged(Object obj,
                    @CachedLibrary("obj") LLVMNativeLibrary lib,
                    @Cached("createUseOffset()") ManagedToComparableValue toComparable) {
        return toComparable.executeWithTarget(obj);
    }

    @TruffleBoundary(allowInlining = true)
    private static long getHashCode(Object obj) {
        // if we ever switch to a more robust implementation, we can simplify the
        // LLVMManagedCompareNode
        return ((long) obj.hashCode()) << 8;
    }

    @ImportStatic(ForeignToLLVMType.class)
    public abstract static class ManagedToComparableValue extends LLVMNode {
        private final boolean includeOffset;

        public ManagedToComparableValue(boolean includeOffset) {
            this.includeOffset = includeOffset;
        }

        abstract long executeWithTarget(Object obj);

        @Specialization
        protected long doManaged(LLVMManagedPointer address) {
            // TODO (chaeubl): this code path is also used for pointers to global variables and
            // functions
            long result = getHashCode(address.getObject());
            if (includeOffset) {
                result += address.getOffset();
            }
            return result;
        }

        public static ManagedToComparableValue createIgnoreOffset() {
            return ManagedToComparableValueNodeGen.create(false);
        }

        public static ManagedToComparableValue createUseOffset() {
            return ManagedToComparableValueNodeGen.create(true);
        }

        protected ForeignToLLVM createForeignToI64() {
            return CommonNodeFactory.createForeignToLLVM(ForeignToLLVMType.I64);
        }
    }
}
