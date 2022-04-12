/*
 * Copyright (c) 2022, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.nodes.intrinsics.interop;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateAOT;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.llvm.runtime.except.LLVMPolyglotException;
import com.oracle.truffle.llvm.runtime.interop.LLVMAsForeignNode;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMAsForeignLibrary;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.interop.LLVMPolyglotBufferInfoFactory.GetBufferSizeNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.interop.LLVMPolyglotBufferInfoFactory.HasBufferElementsNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.interop.LLVMPolyglotBufferInfoFactory.IsBufferWritableNodeGen;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;

/**
 * For the actual implementation on native buffers see {@link LLVMPolyglotNativeBufferInfo}.
 */
@NodeChild("buffer")
public abstract class LLVMPolyglotBufferInfo extends LLVMExpressionNode {
    protected LLVMPolyglotException notABuffer() {
        return new LLVMPolyglotException(this, "The argument is not a buffer.");
    }

    public abstract static class HasBufferElements extends LLVMPolyglotBufferInfo {

        @GenerateAOT.Exclude
        @Specialization
        public boolean executeNative(LLVMNativePointer pointer,
                        @CachedLibrary(limit = "3") InteropLibrary interop) {
            return interop.hasBufferElements(pointer);
        }

        @GenerateAOT.Exclude
        @Specialization(guards = "!foreignsLib.isForeign(pointer)", limit = "3")
        public boolean nonForeignHasElements(LLVMManagedPointer pointer,
                        @SuppressWarnings("unused") @CachedLibrary("pointer") LLVMAsForeignLibrary foreignsLib,
                        @CachedLibrary(limit = "3") InteropLibrary interop) {
            return interop.hasBufferElements(pointer);
        }

        @GenerateAOT.Exclude
        @Specialization(guards = "foreignsLib.isForeign(pointer)", limit = "3")
        public boolean executeManaged(LLVMManagedPointer pointer,
                        @Cached LLVMAsForeignNode foreign,
                        @SuppressWarnings("unused") @CachedLibrary("pointer") LLVMAsForeignLibrary foreignsLib,
                        @CachedLibrary(limit = "3") InteropLibrary interop) {
            Object object = foreign.execute(pointer);
            return interop.hasBufferElements(object);
        }

        @Fallback
        public boolean unsupported(@SuppressWarnings("unused") Object buffer) {
            return false;
        }

        public static HasBufferElements create(LLVMExpressionNode arg) {
            return HasBufferElementsNodeGen.create(arg);
        }
    }

    public abstract static class IsBufferWritable extends LLVMPolyglotBufferInfo {

        @GenerateAOT.Exclude
        @Specialization
        public boolean isNativeBufferWritable(LLVMNativePointer pointer,
                        @Cached BranchProfile exception,
                        @CachedLibrary(limit = "3") InteropLibrary interop) {
            try {
                return interop.isBufferWritable(pointer);
            } catch (UnsupportedMessageException ex) {
                exception.enter();
                throw notABuffer();
            }
        }

        @GenerateAOT.Exclude
        @Specialization(guards = "!foreignsLib.isForeign(pointer)", limit = "3")
        public boolean nonForeignBufferWritable(LLVMManagedPointer pointer,
                        @Cached BranchProfile exception,
                        @SuppressWarnings("unused") @CachedLibrary("pointer") LLVMAsForeignLibrary foreignsLib,
                        @CachedLibrary(limit = "3") InteropLibrary interop) {
            try {
                return interop.isBufferWritable(pointer);
            } catch (UnsupportedMessageException ex) {
                exception.enter();
                throw notABuffer();
            }

        }

        @GenerateAOT.Exclude
        @Specialization(guards = "foreignsLib.isForeign(pointer)", limit = "3")
        public boolean isManagedBufferWritable(LLVMManagedPointer pointer,
                        @Cached LLVMAsForeignNode foreign,
                        @Cached BranchProfile exception,
                        @SuppressWarnings("unused") @CachedLibrary("pointer") LLVMAsForeignLibrary foreignsLib,
                        @CachedLibrary(limit = "3") InteropLibrary interop) {
            Object object = foreign.execute(pointer);
            try {
                return interop.isBufferWritable(object);
            } catch (UnsupportedMessageException ex) {
                exception.enter();
                throw notABuffer();
            }
        }

        public static IsBufferWritable create(LLVMExpressionNode arg) {
            return IsBufferWritableNodeGen.create(arg);
        }
    }

    public abstract static class GetBufferSize extends LLVMPolyglotBufferInfo {

        @GenerateAOT.Exclude
        @Specialization
        public long getNativeBufferSize(LLVMNativePointer pointer,
                        @Cached BranchProfile exception,
                        @CachedLibrary(limit = "3") InteropLibrary interop) {
            try {
                return interop.getBufferSize(pointer);
            } catch (UnsupportedMessageException ex) {
                exception.enter();
                throw notABuffer();
            }
        }

        @GenerateAOT.Exclude
        @Specialization(guards = "!foreignsLib.isForeign(pointer)", limit = "3")
        public long nonForeignBufferSize(LLVMManagedPointer pointer,
                        @Cached BranchProfile exception,
                        @SuppressWarnings("unused") @CachedLibrary("pointer") LLVMAsForeignLibrary foreignsLib,
                        @CachedLibrary(limit = "3") InteropLibrary interop) {
            try {
                return interop.getBufferSize(pointer);
            } catch (UnsupportedMessageException ex) {
                exception.enter();
                throw notABuffer();
            }

        }

        @GenerateAOT.Exclude
        @Specialization(guards = "foreignsLib.isForeign(pointer)", limit = "3")
        public long getManagedBufferSize(LLVMManagedPointer pointer,
                        @Cached LLVMAsForeignNode foreign,
                        @Cached BranchProfile exception,
                        @SuppressWarnings("unused") @CachedLibrary("pointer") LLVMAsForeignLibrary foreignsLib,
                        @CachedLibrary(limit = "3") InteropLibrary interop) {
            Object object = foreign.execute(pointer);
            try {
                return interop.getBufferSize(object);
            } catch (UnsupportedMessageException ex) {
                exception.enter();
                throw notABuffer();
            }
        }

        public static GetBufferSize create(LLVMExpressionNode arg) {
            return GetBufferSizeNodeGen.create(arg);
        }
    }
}
