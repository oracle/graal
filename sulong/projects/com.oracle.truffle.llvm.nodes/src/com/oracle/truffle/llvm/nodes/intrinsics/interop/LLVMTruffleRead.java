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
package com.oracle.truffle.llvm.nodes.intrinsics.interop;

import com.oracle.truffle.llvm.runtime.interop.LLVMAsForeignNode;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMIntrinsic;
import com.oracle.truffle.llvm.runtime.except.LLVMPolyglotException;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;

public abstract class LLVMTruffleRead extends LLVMIntrinsic {

    private static Object doRead(TruffleObject value, String name, Node foreignRead, ForeignToLLVM toLLVM) {
        try {
            Object rawValue = ForeignAccess.sendRead(foreignRead, value, name);
            return toLLVM.executeWithTarget(rawValue);
        } catch (UnsupportedMessageException e) {
            CompilerDirectives.transferToInterpreter();
            throw new LLVMPolyglotException(foreignRead, "Can not read member '%s' of polyglot value.", name);
        } catch (UnknownIdentifierException e) {
            CompilerDirectives.transferToInterpreter();
            throw new LLVMPolyglotException(foreignRead, "Member '%s' does not exist.", e.getUnknownIdentifier());
        }
    }

    private static Object doReadIdx(TruffleObject value, int id, Node foreignRead, ForeignToLLVM toLLVM) {
        try {
            Object rawValue = ForeignAccess.sendRead(foreignRead, value, id);
            return toLLVM.executeWithTarget(rawValue);
        } catch (UnsupportedMessageException e) {
            CompilerDirectives.transferToInterpreter();
            throw new LLVMPolyglotException(foreignRead, "Can not read from index %d of polyglot value.", id);
        } catch (UnknownIdentifierException e) {
            CompilerDirectives.transferToInterpreter();
            throw new LLVMPolyglotException(foreignRead, "Index %d does not exist.", id);
        }
    }

    @NodeChildren({@NodeChild(type = LLVMExpressionNode.class), @NodeChild(type = LLVMExpressionNode.class)})
    public abstract static class LLVMTruffleReadFromName extends LLVMTruffleRead {

        @Child protected Node foreignRead = Message.READ.createNode();
        @Child protected ForeignToLLVM toLLVM;
        @Child private LLVMAsForeignNode asForeign = LLVMAsForeignNode.create();

        public LLVMTruffleReadFromName(ForeignToLLVM toLLVM) {
            this.toLLVM = toLLVM;
        }

        @Specialization
        protected Object uncached(LLVMManagedPointer value, Object id,
                        @Cached("createReadString()") LLVMReadStringNode readStr) {
            TruffleObject foreign = asForeign.execute(value);
            return doRead(foreign, readStr.executeWithTarget(id), foreignRead, toLLVM);
        }

        @Fallback
        @TruffleBoundary
        @SuppressWarnings("unused")
        public Object fallback(Object value, Object id) {
            throw new LLVMPolyglotException(this, "Invalid argument to polyglot builtin.");
        }
    }

    @NodeChildren({@NodeChild(type = LLVMExpressionNode.class), @NodeChild(type = LLVMExpressionNode.class)})
    public abstract static class LLVMTruffleReadFromIndex extends LLVMTruffleRead {

        @Child protected Node foreignRead = Message.READ.createNode();
        @Child protected ForeignToLLVM toLLVM;
        @Child private LLVMAsForeignNode asForeign = LLVMAsForeignNode.create();

        public LLVMTruffleReadFromIndex(ForeignToLLVM toLLVM) {
            this.toLLVM = toLLVM;
        }

        @Specialization
        protected Object doIntrinsic(LLVMManagedPointer value, int id) {
            TruffleObject foreign = asForeign.execute(value);
            return doReadIdx(foreign, id, foreignRead, toLLVM);
        }

        @Fallback
        @TruffleBoundary
        @SuppressWarnings("unused")
        public Object fallback(Object value, Object id) {
            throw new LLVMPolyglotException(this, "Invalid argument to polyglot builtin.");
        }
    }
}
