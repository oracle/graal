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
package com.oracle.truffle.llvm.nodes.op;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.llvm.nodes.op.ToComparableValueNodeGen.ManagedToComparableValueNodeGen;
import com.oracle.truffle.llvm.nodes.op.ToComparableValueNodeGen.NativeToComparableValueNodeGen;
import com.oracle.truffle.llvm.runtime.LLVMBoxedPrimitive;
import com.oracle.truffle.llvm.runtime.LLVMVirtualAllocationAddress;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM.ForeignToLLVMType;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMObjectNativeLibrary;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;

public abstract class ToComparableValue extends LLVMNode {
    public abstract long executeWithTarget(Object obj);

    @Specialization(guards = "lib.guard(obj)")
    protected long doNativeCached(Object obj,
                    @Cached("createCached(obj)") LLVMObjectNativeLibrary lib,
                    @Cached("createToComparable()") NativeToComparableValue toComparable) {
        return doNative(obj, lib, toComparable);
    }

    @Specialization(replaces = "doNativeCached", guards = "lib.guard(obj)")
    protected long doNative(Object obj,
                    @Cached("createGeneric()") LLVMObjectNativeLibrary lib,
                    @Cached("createToComparable()") NativeToComparableValue toComparable) {
        return toComparable.executeWithTarget(obj, lib);
    }

    protected static NativeToComparableValue createToComparable() {
        return NativeToComparableValueNodeGen.create();
    }

    @TruffleBoundary
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
        protected long doManagedMalloc(LLVMVirtualAllocationAddress address) {
            long result;
            if (address.isNull()) {
                result = 0L;
            } else {
                result = getHashCode(address.getObject());
            }

            if (includeOffset) {
                result += address.getOffset();
            }
            return result;
        }

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

        @Specialization
        protected long doLLVMBoxedPrimitive(LLVMBoxedPrimitive address,
                        @Cached("createForeignToI64()") ForeignToLLVM toLLVM) {
            return (long) toLLVM.executeWithTarget(address.getValue());
        }

        public static ManagedToComparableValue createIgnoreOffset() {
            return ManagedToComparableValueNodeGen.create(false);
        }

        public static ManagedToComparableValue createUseOffset() {
            return ManagedToComparableValueNodeGen.create(true);
        }

        @TruffleBoundary
        protected ForeignToLLVM createForeignToI64() {
            return getNodeFactory().createForeignToLLVM(ForeignToLLVMType.I64);
        }
    }

    protected abstract static class NativeToComparableValue extends LLVMNode {

        protected abstract long executeWithTarget(Object obj, LLVMObjectNativeLibrary lib);

        @Specialization(guards = "lib.isPointer(obj)")
        protected long doPointer(Object obj, LLVMObjectNativeLibrary lib) {
            try {
                return lib.asPointer(obj);
            } catch (InteropException ex) {
                throw ex.raise();
            }
        }

        @Specialization(guards = "!lib.isPointer(obj)")
        @SuppressWarnings("unused")
        protected long doManaged(Object obj, LLVMObjectNativeLibrary lib,
                        @Cached("createUseOffset()") ManagedToComparableValue toComparable) {
            return toComparable.executeWithTarget(obj);
        }
    }
}
