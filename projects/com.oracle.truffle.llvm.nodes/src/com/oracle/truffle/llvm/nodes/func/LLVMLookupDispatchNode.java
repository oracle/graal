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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMFunction;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.LLVMFunctionHandle;
import com.oracle.truffle.llvm.runtime.interop.LLVMDataEscapeNode;
import com.oracle.truffle.llvm.runtime.interop.LLVMDataEscapeNodeGen;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM;
import com.oracle.truffle.llvm.runtime.memory.LLVMThreadingStack;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.types.FunctionType;

@SuppressWarnings("unused")
public abstract class LLVMLookupDispatchNode extends LLVMNode {

    protected static final int INLINE_CACHE_SIZE = 5;

    private final FunctionType type;

    protected LLVMLookupDispatchNode(FunctionType type) {
        this.type = type;
    }

    public abstract Object executeDispatch(VirtualFrame frame, Object function, Object[] arguments);

    @Specialization(limit = "INLINE_CACHE_SIZE", guards = "descriptor == cachedDescriptor")
    protected static Object doDirectCached(VirtualFrame frame, LLVMFunctionDescriptor descriptor, Object[] arguments,
                    @Cached("descriptor") LLVMFunctionDescriptor cachedDescriptor,
                    @Cached("createCachedDispatch()") LLVMDispatchNode dispatchNode) {
        return dispatchNode.executeDispatch(frame, cachedDescriptor, arguments);
    }

    @Specialization(replaces = "doDirectCached")
    protected static Object doDirect(VirtualFrame frame, LLVMFunctionDescriptor descriptor, Object[] arguments,
                    @Cached("createCachedDispatch()") LLVMDispatchNode dispatchNode) {
        return dispatchNode.executeDispatch(frame, descriptor, arguments);
    }

    @Specialization(limit = "INLINE_CACHE_SIZE", guards = {"cachedFunction != null", "handle.getFunctionPointer() == cachedFunction.getFunctionPointer()"})
    protected static Object doCached(VirtualFrame frame, LLVMFunctionHandle handle, Object[] arguments,
                    @Cached("lookupFunction(handle)") LLVMFunctionDescriptor cachedFunction,
                    @Cached("createCachedDispatch()") LLVMDispatchNode dispatchNode) {
        return dispatchNode.executeDispatch(frame, cachedFunction, arguments);
    }

    @Specialization(limit = "INLINE_CACHE_SIZE", guards = {"cachedFunction == null", "handle.getFunctionPointer() == cachedHandle.getFunctionPointer()"})
    protected static Object doCachedNative(VirtualFrame frame, LLVMFunctionHandle handle, Object[] arguments,
                    @Cached("handle") LLVMFunctionHandle cachedHandle,
                    @Cached("lookupFunction(cachedHandle)") LLVMFunctionDescriptor cachedFunction,
                    @Cached("createCachedNativeDispatch()") LLVMNativeDispatchNode dispatchNode) {
        return dispatchNode.executeDispatch(frame, cachedHandle, arguments);
    }

    @Specialization(replaces = {"doCached", "doCachedNative"})
    protected Object doLookup(VirtualFrame frame, LLVMFunctionHandle function, Object[] arguments,
                    @Cached("createCachedDispatch()") LLVMDispatchNode dispatchNode,
                    @Cached("createCachedNativeDispatch()") LLVMNativeDispatchNode dispatchNativeNode) {
        LLVMFunctionDescriptor descriptor = lookupFunction(function);
        if (descriptor != null) {
            return dispatchNode.executeDispatch(frame, descriptor, arguments);
        } else {
            return dispatchNativeNode.executeDispatch(frame, function, arguments);
        }
    }

    protected LLVMFunctionDescriptor lookupFunction(LLVMFunctionHandle function) {
        return getContext().getFunctionDescriptor(function);
    }

    protected LLVMDispatchNode createCachedDispatch() {
        return LLVMDispatchNodeGen.create(type);
    }

    protected LLVMNativeDispatchNode createCachedNativeDispatch() {
        return LLVMNativeDispatchNodeGen.create(type);
    }

    @CompilationFinal private LLVMThreadingStack threadingStack = null;

    private LLVMThreadingStack getThreadingStack(LLVMContext context) {
        if (threadingStack == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            threadingStack = context.getThreadingStack();
        }
        return threadingStack;
    }

    @Specialization(guards = "isForeignFunction(function)")
    protected Object doForeign(TruffleObject function, Object[] arguments,
                    @Cached("createCrossLanguageCallNode(arguments)") Node crossLanguageCallNode,
                    @Cached("createLLVMDataEscapeNodes()") LLVMDataEscapeNode[] dataEscapeNodes,
                    @Cached("createToLLVMNode()") ForeignToLLVM toLLVMNode,
                    @Cached("getContext()") LLVMContext context) {
        try {
            getThreadingStack(context).getStack().setStackPointer((long) arguments[0]);
            Object ret = ForeignAccess.sendExecute(crossLanguageCallNode, function, getForeignArguments(dataEscapeNodes, arguments, context));
            getThreadingStack(context).getStack().setStackPointer((long) arguments[0]);
            return toLLVMNode.executeWithTarget(ret);
        } catch (InteropException e) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException(e);
        }
    }

    @ExplodeLoop
    private Object[] getForeignArguments(LLVMDataEscapeNode[] dataEscapeNodes, Object[] arguments, LLVMContext context) {
        assert arguments.length == type.getArgumentTypes().length;
        Object[] args = new Object[type.getArgumentTypes().length - LLVMCallNode.USER_ARGUMENT_OFFSET];
        for (int i = 0; i < type.getArgumentTypes().length - LLVMCallNode.USER_ARGUMENT_OFFSET; i++) {
            args[i] = dataEscapeNodes[i].executeWithTarget(arguments[i + LLVMCallNode.USER_ARGUMENT_OFFSET], context);
        }
        return args;
    }

    protected LLVMDataEscapeNode[] createLLVMDataEscapeNodes() {
        LLVMDataEscapeNode[] args = new LLVMDataEscapeNode[type.getArgumentTypes().length - LLVMCallNode.USER_ARGUMENT_OFFSET];
        for (int i = 0; i < type.getArgumentTypes().length - LLVMCallNode.USER_ARGUMENT_OFFSET; i++) {
            args[i] = LLVMDataEscapeNodeGen.create(type.getArgumentTypes()[i + LLVMCallNode.USER_ARGUMENT_OFFSET]);
        }
        return args;
    }

    protected static boolean isForeignFunction(TruffleObject function) {
        return !(function instanceof LLVMFunction);
    }

    protected static Node createCrossLanguageCallNode(Object[] arguments) {
        return Message.createExecute(arguments.length).createNode();
    }

    protected ForeignToLLVM createToLLVMNode() {
        return ForeignToLLVM.create(type.getReturnType());
    }

}
