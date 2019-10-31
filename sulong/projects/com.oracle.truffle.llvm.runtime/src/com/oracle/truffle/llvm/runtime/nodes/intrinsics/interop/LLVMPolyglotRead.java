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
package com.oracle.truffle.llvm.runtime.nodes.intrinsics.interop;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.LLVMIntrinsic;
import com.oracle.truffle.llvm.runtime.except.LLVMPolyglotException;
import com.oracle.truffle.llvm.runtime.interop.LLVMAsForeignNode;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;

public abstract class LLVMPolyglotRead extends LLVMIntrinsic {

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMPolyglotGetMember extends LLVMPolyglotRead {

        @Child protected ForeignToLLVM toLLVM;

        public LLVMPolyglotGetMember(ForeignToLLVM toLLVM) {
            this.toLLVM = toLLVM;
        }

        @Specialization
        protected Object uncached(LLVMManagedPointer value, Object id,
                        @Cached LLVMAsForeignNode asForeign,
                        @Cached("createReadString()") LLVMReadStringNode readStr,
                        @CachedLibrary(limit = "3") InteropLibrary foreignRead,
                        @Cached BranchProfile exception) {
            Object foreign = asForeign.execute(value);
            String name = readStr.executeWithTarget(id);
            try {
                Object rawValue = foreignRead.readMember(foreign, name);
                return toLLVM.executeWithTarget(rawValue);
            } catch (UnsupportedMessageException e) {
                exception.enter();
                throw new LLVMPolyglotException(foreignRead, "Can not read member '%s' of polyglot value.", name);
            } catch (UnknownIdentifierException e) {
                exception.enter();
                throw new LLVMPolyglotException(foreignRead, "Member '%s' does not exist.", e.getUnknownIdentifier());
            }
        }

        @Fallback
        @SuppressWarnings("unused")
        public Object fallback(Object value, Object id) {
            throw new LLVMPolyglotException(this, "Invalid argument to polyglot builtin.");
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMPolyglotGetArrayElement extends LLVMPolyglotRead {

        @Child protected ForeignToLLVM toLLVM;

        public LLVMPolyglotGetArrayElement(ForeignToLLVM toLLVM) {
            this.toLLVM = toLLVM;
        }

        @Specialization
        protected Object doIntrinsic(LLVMManagedPointer value, int id,
                        @Cached LLVMAsForeignNode asForeign,
                        @CachedLibrary(limit = "3") InteropLibrary foreignRead,
                        @Cached BranchProfile exception) {
            Object foreign = asForeign.execute(value);
            try {
                Object rawValue = foreignRead.readArrayElement(foreign, id);
                return toLLVM.executeWithTarget(rawValue);
            } catch (UnsupportedMessageException e) {
                exception.enter();
                throw new LLVMPolyglotException(foreignRead, "Can not read from index %d of polyglot value.", id);
            } catch (InvalidArrayIndexException e) {
                exception.enter();
                throw new LLVMPolyglotException(foreignRead, "Index %d does not exist.", id);
            }
        }

        @Fallback
        @TruffleBoundary
        @SuppressWarnings("unused")
        public Object fallback(Object value, Object id) {
            throw new LLVMPolyglotException(this, "Invalid argument to polyglot builtin.");
        }
    }
}
