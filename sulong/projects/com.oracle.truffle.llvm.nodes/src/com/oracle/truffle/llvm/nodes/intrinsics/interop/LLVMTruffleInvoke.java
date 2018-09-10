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
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
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
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMIntrinsic;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMGetStackNode;
import com.oracle.truffle.llvm.runtime.interop.LLVMDataEscapeNode;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack.StackPointer;
import com.oracle.truffle.llvm.runtime.memory.LLVMThreadingStack;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;

@NodeChildren({@NodeChild(type = LLVMExpressionNode.class), @NodeChild(type = LLVMExpressionNode.class)})
public abstract class LLVMTruffleInvoke extends LLVMIntrinsic {
    @Children private final LLVMExpressionNode[] args;
    @Children private final LLVMDataEscapeNode[] prepareValuesForEscape;
    @Child private Node foreignInvoke;
    @Child private ForeignToLLVM toLLVM;
    @Child private LLVMAsForeignNode asForeign = LLVMAsForeignNode.create();

    public LLVMTruffleInvoke(ForeignToLLVM toLLVM, LLVMExpressionNode[] args) {
        this.toLLVM = toLLVM;
        this.args = args;
        this.prepareValuesForEscape = new LLVMDataEscapeNode[args.length];
        for (int i = 0; i < prepareValuesForEscape.length; i++) {
            prepareValuesForEscape[i] = LLVMDataEscapeNode.create();
        }
        this.foreignInvoke = Message.INVOKE.createNode();
    }

    @CompilationFinal private LLVMThreadingStack threadingStack = null;

    private LLVMThreadingStack getThreadingStack(LLVMContext context) {
        if (threadingStack == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            threadingStack = context.getThreadingStack();
        }
        return threadingStack;
    }

    @ExplodeLoop
    private Object doInvoke(VirtualFrame frame, TruffleObject value, String id, ContextReference<LLVMContext> contextReference,
                    LLVMGetStackNode getStack) {
        Object[] evaluatedArgs = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            evaluatedArgs[i] = prepareValuesForEscape[i].executeWithTarget(args[i].executeGeneric(frame));
        }
        try {
            LLVMContext context = contextReference.get();
            LLVMStack stack = getStack.executeWithTarget(getThreadingStack(context), Thread.currentThread());
            Object rawValue;
            try (StackPointer save = stack.newFrame()) {
                rawValue = ForeignAccess.sendInvoke(foreignInvoke, value, id, evaluatedArgs);
            }
            return toLLVM.executeWithTarget(rawValue);
        } catch (UnknownIdentifierException | UnsupportedMessageException | UnsupportedTypeException | ArityException e) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings("unused")
    @Specialization(limit = "2", guards = "idStr.equals(readStr.executeWithTarget(id))")
    protected Object cachedId(VirtualFrame frame, LLVMManagedPointer value, Object id,
                    @Cached("createReadString()") LLVMReadStringNode readStr,
                    @Cached("readStr.executeWithTarget(id)") String idStr,
                    @Cached("getContextReference()") ContextReference<LLVMContext> context,
                    @Cached("create()") LLVMGetStackNode getStack) {
        TruffleObject foreign = asForeign.execute(value);
        return doInvoke(frame, foreign, idStr, context, getStack);
    }

    @Specialization(replaces = "cachedId")
    protected Object uncached(VirtualFrame frame, LLVMManagedPointer value, Object id,
                    @Cached("createReadString()") LLVMReadStringNode readStr,
                    @Cached("getContextReference()") ContextReference<LLVMContext> context,
                    @Cached("create()") LLVMGetStackNode getStack) {
        TruffleObject foreign = asForeign.execute(value);
        return doInvoke(frame, foreign, readStr.executeWithTarget(id), context, getStack);
    }

    @Fallback
    @SuppressWarnings("unused")
    public Object fallback(Object value, Object id) {
        CompilerDirectives.transferToInterpreter();
        System.err.println("Invalid arguments to invoke-builtin.");
        throw new IllegalArgumentException();
    }
}
