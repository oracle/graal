/*
 * Copyright (c) 2016, Oracle and/or its affiliates.
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
import com.oracle.truffle.api.dsl.Cached;
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
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMPerformance;
import com.oracle.truffle.llvm.runtime.LLVMTruffleObject;
import com.oracle.truffle.llvm.runtime.interop.LLVMDataEscapeNode;
import com.oracle.truffle.llvm.runtime.interop.LLVMDataEscapeNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.types.Type;

public final class LLVMTruffleWrite {

    private static void checkLLVMTruffleObject(LLVMTruffleObject value) {
        if (value.getOffset() != 0 || value.getName() != null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IllegalAccessError("Pointee must be unmodified");
        }
    }

    private static void doWrite(Node foreignWrite, TruffleObject value, LLVMAddress id, Object v) {
        String name = LLVMTruffleIntrinsicUtil.readString(id);
        doWrite(foreignWrite, value, name, v);
    }

    private static void doWrite(Node foreignWrite, TruffleObject value, String name, Object v) throws IllegalAccessError {
        try {
            ForeignAccess.sendWrite(foreignWrite, value, name, v);
        } catch (UnknownIdentifierException | UnsupportedMessageException | UnsupportedTypeException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void doWriteIdx(Node foreignWrite, TruffleObject value, int id, Object v) {
        try {
            ForeignAccess.sendWrite(foreignWrite, value, id, v);
        } catch (UnknownIdentifierException | UnsupportedMessageException | UnsupportedTypeException e) {
            throw new IllegalStateException(e);
        }
    }

    @NodeChildren({@NodeChild(type = LLVMExpressionNode.class), @NodeChild(type = LLVMExpressionNode.class), @NodeChild(type = LLVMExpressionNode.class)})
    public abstract static class LLVMTruffleWriteToName extends LLVMIntrinsic {

        @Child private Node foreignWrite = Message.WRITE.createNode();
        @Child protected LLVMDataEscapeNode prepareValueForEscape;

        public LLVMTruffleWriteToName(Type typeOfValue) {
            this.prepareValueForEscape = LLVMDataEscapeNodeGen.create(typeOfValue);
        }

        @SuppressWarnings("unused")
        @Specialization(limit = "2", guards = "constantPointer(id, cachedPtr)")
        public Object executeIntrinsicCached(LLVMTruffleObject value, LLVMAddress id, Object v, @Cached("pointerOf(id)") long cachedPtr,
                        @Cached("readString(id)") String cachedId, @Cached("getContext()") LLVMContext context) {
            checkLLVMTruffleObject(value);
            doWrite(foreignWrite, value.getObject(), cachedId, prepareValueForEscape.executeWithTarget(v, context));
            return null;
        }

        @Specialization
        public Object executeIntrinsic(LLVMTruffleObject value, LLVMAddress id, Object v, @Cached("getContext()") LLVMContext context) {
            LLVMPerformance.warn(this);
            checkLLVMTruffleObject(value);
            doWrite(foreignWrite, value.getObject(), id, prepareValueForEscape.executeWithTarget(v, context));
            return null;
        }

        @SuppressWarnings("unused")
        @Specialization(limit = "2", guards = "constantPointer(id, cachedPtr)")
        public Object executeIntrinsicTruffleObjectCached(TruffleObject value, LLVMAddress id, Object v, @Cached("pointerOf(id)") long cachedPtr,
                        @Cached("readString(id)") String cachedId, @Cached("getContext()") LLVMContext context) {
            doWrite(foreignWrite, value, cachedId, prepareValueForEscape.executeWithTarget(v, context));
            return null;
        }

        @Specialization
        public Object executeIntrinsicTruffleObject(TruffleObject value, LLVMAddress id, Object v, @Cached("getContext()") LLVMContext context) {
            LLVMPerformance.warn(this);
            doWrite(foreignWrite, value, id, prepareValueForEscape.executeWithTarget(v, context));
            return null;
        }
    }

    @NodeChildren({@NodeChild(type = LLVMExpressionNode.class), @NodeChild(type = LLVMExpressionNode.class), @NodeChild(type = LLVMExpressionNode.class)})
    public abstract static class LLVMTruffleWriteToIndex extends LLVMIntrinsic {

        @Child private Node foreignWrite = Message.WRITE.createNode();
        @Child protected LLVMDataEscapeNode prepareValueForEscape;

        public LLVMTruffleWriteToIndex(Type typeOfValue) {
            this.prepareValueForEscape = LLVMDataEscapeNodeGen.create(typeOfValue);
        }

        @Specialization
        public Object executeIntrinsic(LLVMTruffleObject value, int id, Object v, @Cached("getContext()") LLVMContext context) {
            checkLLVMTruffleObject(value);
            doWriteIdx(foreignWrite, value.getObject(), id, prepareValueForEscape.executeWithTarget(v, context));
            return null;
        }

        @Specialization
        public Object executeIntrinsic(TruffleObject value, int id, Object v, @Cached("getContext()") LLVMContext context) {
            doWriteIdx(foreignWrite, value, id, prepareValueForEscape.executeWithTarget(v, context));
            return null;
        }
    }
}
