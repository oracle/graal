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
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMIntrinsic;
import com.oracle.truffle.llvm.runtime.except.LLVMPolyglotException;
import com.oracle.truffle.llvm.runtime.interop.LLVMAsForeignNode;
import com.oracle.truffle.llvm.runtime.interop.LLVMDataEscapeNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;

public final class LLVMTruffleWrite {

    private static void doWrite(Node foreignWrite, TruffleObject value, String name, Object v) throws IllegalAccessError {
        try {
            ForeignAccess.sendWrite(foreignWrite, value, name, v);
        } catch (UnsupportedMessageException e) {
            CompilerDirectives.transferToInterpreter();
            throw new LLVMPolyglotException(foreignWrite, "Can not write member '%s' to polyglot value.", name);
        } catch (UnknownIdentifierException e) {
            CompilerDirectives.transferToInterpreter();
            throw new LLVMPolyglotException(foreignWrite, "Member '%s' does not exist.", e.getUnknownIdentifier());
        } catch (UnsupportedTypeException e) {
            CompilerDirectives.transferToInterpreter();
            throw new LLVMPolyglotException(foreignWrite, "Unsupported type writing member '%s'.", name);
        }
    }

    private static void doWriteIdx(Node foreignWrite, TruffleObject value, int id, Object v) {
        try {
            ForeignAccess.sendWrite(foreignWrite, value, id, v);
        } catch (UnsupportedMessageException e) {
            CompilerDirectives.transferToInterpreter();
            throw new LLVMPolyglotException(foreignWrite, "Can not write to index %d of polyglot value.", id);
        } catch (UnknownIdentifierException e) {
            CompilerDirectives.transferToInterpreter();
            throw new LLVMPolyglotException(foreignWrite, "Index %d does not exist.", id);
        } catch (UnsupportedTypeException e) {
            CompilerDirectives.transferToInterpreter();
            throw new LLVMPolyglotException(foreignWrite, "Unsupported type writing index %d.", id);
        }
    }

    @NodeChildren({@NodeChild(type = LLVMExpressionNode.class), @NodeChild(type = LLVMExpressionNode.class), @NodeChild(type = LLVMExpressionNode.class)})
    public abstract static class LLVMTruffleWriteToName extends LLVMIntrinsic {

        @Child private Node foreignWrite = Message.WRITE.createNode();
        @Child protected LLVMDataEscapeNode prepareValueForEscape;
        @Child private LLVMAsForeignNode asForeign = LLVMAsForeignNode.create();

        public LLVMTruffleWriteToName() {
            this.prepareValueForEscape = LLVMDataEscapeNode.create();
        }

        @SuppressWarnings("unused")
        @Specialization(limit = "2", guards = "cachedId.equals(readStr.executeWithTarget(id))")
        protected Object cached(LLVMManagedPointer value, Object id, Object v,
                        @Cached("createReadString()") LLVMReadStringNode readStr,
                        @Cached("readStr.executeWithTarget(id)") String cachedId) {
            TruffleObject foreign = asForeign.execute(value);
            doWrite(foreignWrite, foreign, cachedId, prepareValueForEscape.executeWithTarget(v));
            return null;
        }

        @Specialization
        protected Object doIntrinsic(LLVMManagedPointer value, Object id, Object v,
                        @Cached("createReadString()") LLVMReadStringNode readStr) {
            TruffleObject foreign = asForeign.execute(value);
            doWrite(foreignWrite, foreign, readStr.executeWithTarget(id), prepareValueForEscape.executeWithTarget(v));
            return null;
        }

        @Fallback
        @TruffleBoundary
        @SuppressWarnings("unused")
        public Object fallback(Object value, Object id, Object v) {
            throw new LLVMPolyglotException(this, "Invalid argument to polyglot builtin.");
        }
    }

    @NodeChildren({@NodeChild(type = LLVMExpressionNode.class), @NodeChild(type = LLVMExpressionNode.class), @NodeChild(type = LLVMExpressionNode.class)})
    public abstract static class LLVMTruffleWriteToIndex extends LLVMIntrinsic {

        @Child private Node foreignWrite = Message.WRITE.createNode();
        @Child protected LLVMDataEscapeNode prepareValueForEscape;
        @Child private LLVMAsForeignNode asForeign = LLVMAsForeignNode.create();

        public LLVMTruffleWriteToIndex() {
            this.prepareValueForEscape = LLVMDataEscapeNode.create();
        }

        @Specialization
        protected Object doIntrinsic(LLVMManagedPointer value, int id, Object v) {
            TruffleObject foreign = asForeign.execute(value);
            doWriteIdx(foreignWrite, foreign, id, prepareValueForEscape.executeWithTarget(v));
            return null;
        }

        @Fallback
        @TruffleBoundary
        @SuppressWarnings("unused")
        public Object fallback(Object value, Object id, Object v) {
            throw new LLVMPolyglotException(this, "Invalid argument to polyglot builtin.");
        }
    }
}
