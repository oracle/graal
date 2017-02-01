/*
 * Copyright (c) 2017, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.nodes.func;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.llvm.context.LLVMContext;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.ToLLVMNode;
import com.oracle.truffle.llvm.runtime.LLVMFunction;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor.LLVMRuntimeType;
import com.oracle.truffle.llvm.runtime.LLVMFunctionHandle;
import com.oracle.truffle.llvm.runtime.types.Type;

@SuppressWarnings("unused")
public abstract class LLVMLookupDispatchNode extends Node {

    protected static final int INLINE_CACHE_SIZE = 5;

    protected final LLVMContext context;
    protected final LLVMRuntimeType retType;
    protected final Type[] argTypes;

    protected LLVMLookupDispatchNode(LLVMContext context, LLVMRuntimeType retType, Type[] argTypes) {
        this.context = context;
        this.retType = retType;
        this.argTypes = argTypes;
    }

    public abstract Object executeDispatch(VirtualFrame frame, TruffleObject function, Object[] arguments);

    @Specialization
    protected static Object doDirect(VirtualFrame frame, LLVMFunctionDescriptor descriptor, Object[] arguments,
                    @Cached("createCachedDispatch()") LLVMDispatchNode dispatchNode) {
        return dispatchNode.executeDispatch(frame, descriptor, arguments);
    }

    @Specialization(limit = "INLINE_CACHE_SIZE", guards = "handle.getFunctionIndex() == cachedFunction.getFunctionIndex()")
    protected static Object doCached(VirtualFrame frame, LLVMFunctionHandle handle, Object[] arguments,
                    @Cached("lookupFunction(handle)") LLVMFunctionDescriptor cachedFunction,
                    @Cached("createCachedDispatch()") LLVMDispatchNode dispatchNode) {
        return dispatchNode.executeDispatch(frame, cachedFunction, arguments);
    }

    @Specialization(replaces = "doCached")
    protected Object doLookup(VirtualFrame frame, LLVMFunction function, Object[] arguments,
                    @Cached("createCachedDispatch()") LLVMDispatchNode dispatchNode) {
        return dispatchNode.executeDispatch(frame, lookupFunction(function), arguments);
    }

    protected LLVMFunctionDescriptor lookupFunction(LLVMFunction function) {
        if (function instanceof LLVMFunctionDescriptor) {
            return (LLVMFunctionDescriptor) function;
        } else {
            return context.getFunctionRegistry().lookup(function);
        }
    }

    protected LLVMDispatchNode createCachedDispatch() {
        return LLVMDispatchNodeGen.create(context, retType, argTypes);
    }

    @Specialization(guards = "isForeignFunction(function)")
    protected Object doForeign(TruffleObject function, Object[] arguments,
                    @Cached("createCrossLanguageCallNode(arguments)") Node crossLanguageCallNode,
                    @Cached("createToLLVMNode()") ToLLVMNode toLLVMNode) {
        try {
            Object ret = ForeignAccess.sendExecute(crossLanguageCallNode, function, getForeignArguments(arguments));
            return toLLVMNode.executeWithTarget(ret);
        } catch (InteropException e) {
            throw new IllegalStateException(e);
        }
    }

    @ExplodeLoop
    protected Object[] getForeignArguments(Object[] arguments) {
        assert arguments.length == argTypes.length;
        Object[] args = new Object[argTypes.length - 1];
        for (int i = 0; i < argTypes.length - 1; i++) {
            args[i] = arguments[i + 1];
        }
        return args;
    }

    protected static boolean isForeignFunction(TruffleObject function) {
        return !(function instanceof LLVMFunction);
    }

    protected static Node createCrossLanguageCallNode(Object[] arguments) {
        return Message.createExecute(arguments.length).createNode();
    }

    protected ToLLVMNode createToLLVMNode() {
        return ToLLVMNode.createNode(ToLLVMNode.convert(retType));
    }

}
