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
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.llvm.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMIntrinsic;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.LLVMTruffleObject;

@NodeChildren({@NodeChild(type = LLVMExpressionNode.class), @NodeChild(type = LLVMExpressionNode.class)})
public abstract class LLVMTruffleInvoke extends LLVMIntrinsic {

    @Children private final LLVMExpressionNode[] args;
    @Child private Node foreignInvoke;
    @Child private ToLLVMNode toLLVM;

    public LLVMTruffleInvoke(ToLLVMNode toLLVM, LLVMExpressionNode[] args) {
        this.toLLVM = toLLVM;
        this.args = args;
        this.foreignInvoke = Message.createInvoke(args.length).createNode();
    }

    private static void checkLLVMTruffleObject(LLVMTruffleObject value) {
        if (value.getOffset() != 0 || value.getName() != null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IllegalAccessError("Pointee must be unmodified");
        }
    }

    @ExplodeLoop
    private Object doInvoke(VirtualFrame frame, TruffleObject value, String id) {
        Object[] evaluatedArgs = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            evaluatedArgs[i] = args[i].executeGeneric(frame);
        }
        try {
            Object rawValue = ForeignAccess.sendInvoke(foreignInvoke, frame, value, id, evaluatedArgs);
            return toLLVM.executeWithTarget(frame, rawValue);
        } catch (UnknownIdentifierException | UnsupportedMessageException | UnsupportedTypeException | ArityException e) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException(e);
        }
    }

    private Object doInvoke(VirtualFrame frame, TruffleObject value, LLVMAddress id) {
        String name = LLVMTruffleIntrinsicUtil.readString(id);
        return doInvoke(frame, value, name);
    }

    @SuppressWarnings("unused")
    @Specialization(limit = "2", guards = {"constantPointer(id, cachedPtr)", "value == cachedValue"})
    public Object doIntrinsicReceiverCachedTruffleObjectCached(VirtualFrame frame, TruffleObject value, LLVMAddress id,
                    @Cached("value") TruffleObject cachedValue,
                    @Cached("pointerOf(id)") long cachedPtr,
                    @Cached("readString(id)") String cachedId) {
        return doInvoke(frame, cachedValue, cachedId);
    }

    @SuppressWarnings("unused")
    @Specialization(limit = "2", guards = "constantPointer(id, cachedPtr)", contains = "doIntrinsicReceiverCachedTruffleObjectCached")
    public Object doIntrinsicTruffleObjectCached(VirtualFrame frame, TruffleObject value, LLVMAddress id, @Cached("pointerOf(id)") long cachedPtr,
                    @Cached("readString(id)") String cachedId) {
        return doInvoke(frame, value, cachedId);
    }

    @Specialization
    public Object doIntrinsicTruffleObject(VirtualFrame frame, TruffleObject value, LLVMAddress id) {
        return doInvoke(frame, value, id);
    }

    @SuppressWarnings("unused")
    @Specialization(limit = "2", guards = {"constantPointer(id, cachedPtr)", "value == cachedValue"})
    public Object doIntrinsicReceiverCachedLLVMTruffleObjectCached(VirtualFrame frame, LLVMTruffleObject value, LLVMAddress id,
                    @Cached("value") LLVMTruffleObject cachedValue,
                    @Cached("pointerOf(id)") long cachedPtr,
                    @Cached("readString(id)") String cachedId) {
        checkLLVMTruffleObject(cachedValue);
        return doInvoke(frame, cachedValue.getObject(), cachedId);
    }

    @SuppressWarnings("unused")
    @Specialization(limit = "2", guards = "constantPointer(id, cachedPtr)", contains = "doIntrinsicReceiverCachedLLVMTruffleObjectCached")
    public Object doIntrinsicLLVMTruffleObjectCached(VirtualFrame frame, LLVMTruffleObject value, LLVMAddress id, @Cached("pointerOf(id)") long cachedPtr,
                    @Cached("readString(id)") String cachedId) {
        checkLLVMTruffleObject(value);
        return doInvoke(frame, value.getObject(), cachedId);
    }

    @Specialization
    public Object doIntrinsicLLVMTruffleObject(VirtualFrame frame, LLVMTruffleObject value, LLVMAddress id) {
        checkLLVMTruffleObject(value);
        return doInvoke(frame, value.getObject(), id);
    }
}
