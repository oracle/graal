/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.
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
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.llvm.nodes.func.LLVMLookupDispatchNodeGen.LLVMLookupDispatchForeignNodeGen;
import com.oracle.truffle.llvm.nodes.memory.load.LLVMDerefHandleGetReceiverNode;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.interop.LLVMAsForeignNode;
import com.oracle.truffle.llvm.runtime.interop.LLVMDataEscapeNode;
import com.oracle.truffle.llvm.runtime.interop.LLVMTypedForeignObject;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack.StackPointer;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.types.FunctionType;

public abstract class LLVMLookupDispatchNode extends LLVMNode {

    protected static final int INLINE_CACHE_SIZE = 5;

    protected final FunctionType type;
    @CompilationFinal private LLVMMemory llvmMemory;

    @Child private LLVMDerefHandleGetReceiverNode derefHandleGetReceiverNode;

    protected LLVMLookupDispatchNode(FunctionType type) {
        this.type = type;
    }

    public abstract Object executeDispatch(Object function, Object[] arguments);

    @Specialization(limit = "INLINE_CACHE_SIZE", guards = "descriptor == cachedDescriptor")
    @SuppressWarnings("unused")
    protected static Object doDirectCached(LLVMFunctionDescriptor descriptor, Object[] arguments,
                    @Cached("descriptor") LLVMFunctionDescriptor cachedDescriptor,
                    @Cached("createCachedDispatch()") LLVMDispatchNode dispatchNode) {
        return dispatchNode.executeDispatch(cachedDescriptor, arguments);
    }

    @Specialization(replaces = "doDirectCached")
    protected static Object doDirect(LLVMFunctionDescriptor descriptor, Object[] arguments,
                    @Cached("createCachedDispatch()") LLVMDispatchNode dispatchNode) {
        return dispatchNode.executeDispatch(descriptor, arguments);
    }

    @Specialization(limit = "INLINE_CACHE_SIZE", guards = {"!isAutoDerefHandle(handle)", "cachedFunction != null", "handle.asNative() == cachedHandle.asNative()"})
    @SuppressWarnings("unused")
    protected static Object doCached(LLVMNativePointer handle, Object[] arguments,
                    @Cached("handle") LLVMNativePointer cachedHandle,
                    @Cached("lookupFunction(handle)") LLVMFunctionDescriptor cachedFunction,
                    @Cached("createCachedDispatch()") LLVMDispatchNode dispatchNode) {
        return dispatchNode.executeDispatch(cachedFunction, arguments);
    }

    @Specialization(limit = "INLINE_CACHE_SIZE", guards = {"!isAutoDerefHandle(handle)", "cachedFunction == null", "handle.asNative() == cachedHandle.asNative()"})
    @SuppressWarnings("unused")
    protected static Object doCachedNative(LLVMNativePointer handle, Object[] arguments,
                    @Cached("handle") LLVMNativePointer cachedHandle,
                    @Cached("lookupFunction(cachedHandle)") LLVMFunctionDescriptor cachedFunction,
                    @Cached("createCachedNativeDispatch()") LLVMNativeDispatchNode dispatchNode) {
        return dispatchNode.executeDispatch(handle, arguments);
    }

    @Specialization(guards = "!isAutoDerefHandle(function)", replaces = {"doCached", "doCachedNative"})
    protected Object doLookup(LLVMNativePointer function, Object[] arguments,
                    @Cached("createCachedDispatch()") LLVMDispatchNode dispatchNode,
                    @Cached("createCachedNativeDispatch()") LLVMNativeDispatchNode dispatchNativeNode) {
        LLVMFunctionDescriptor descriptor = lookupFunction(function);
        if (descriptor != null) {
            return dispatchNode.executeDispatch(descriptor, arguments);
        } else {
            return dispatchNativeNode.executeDispatch(function, arguments);
        }
    }

    protected LLVMFunctionDescriptor lookupFunction(LLVMNativePointer function) {
        return getContextReference().get().getFunctionDescriptor(function);
    }

    protected LLVMDispatchNode createCachedDispatch() {
        return LLVMDispatchNodeGen.create(type);
    }

    protected LLVMNativeDispatchNode createCachedNativeDispatch() {
        return LLVMNativeDispatchNodeGen.create(type);
    }

    @Specialization(guards = "isAutoDerefHandle(handle)")
    protected Object doDerefHandle(LLVMNativePointer handle, Object[] arguments,
                    @Cached("create(type)") LLVMLookupDispatchForeignNode lookupDispatchForeignNode) {
        LLVMManagedPointer foreignFunction = getDerefHandleGetReceiverNode().execute(handle);
        return doForeign(foreignFunction, arguments, lookupDispatchForeignNode);
    }

    @Specialization(guards = "isForeignFunction(function)")
    protected Object doForeign(LLVMManagedPointer function, Object[] arguments,
                    @Cached("create(type)") LLVMLookupDispatchForeignNode lookupDispatchForeignNode) {
        return lookupDispatchForeignNode.execute(function, arguments);
    }

    protected static boolean isForeignFunction(LLVMManagedPointer function) {
        return function.getOffset() == 0 && function.getObject() instanceof LLVMTypedForeignObject;
    }

    abstract static class LLVMLookupDispatchForeignNode extends LLVMNode {
        private final FunctionType type;

        LLVMLookupDispatchForeignNode(FunctionType type) {
            this.type = type;
        }

        abstract Object execute(LLVMManagedPointer function, Object[] arguments);

        @Specialization
        protected Object doForeign(LLVMManagedPointer function, Object[] arguments,
                        @Cached("create()") LLVMAsForeignNode asForeign,
                        @Cached("createCrossLanguageCallNode(arguments)") Node crossLanguageCallNode,
                        @Cached("createLLVMDataEscapeNodes()") LLVMDataEscapeNode[] dataEscapeNodes,
                        @Cached("createToLLVMNode()") ForeignToLLVM toLLVMNode) {
            try {
                Object ret;
                try (StackPointer save = ((StackPointer) arguments[0]).newFrame()) {
                    ret = ForeignAccess.sendExecute(crossLanguageCallNode, asForeign.execute(function), getForeignArguments(dataEscapeNodes, arguments));
                }
                return toLLVMNode.executeWithTarget(ret);
            } catch (InteropException e) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException(e);
            }
        }

        @ExplodeLoop
        private Object[] getForeignArguments(LLVMDataEscapeNode[] dataEscapeNodes, Object[] arguments) {
            assert arguments.length == type.getArgumentTypes().length;
            Object[] args = new Object[type.getArgumentTypes().length - LLVMCallNode.USER_ARGUMENT_OFFSET];
            for (int i = 0; i < type.getArgumentTypes().length - LLVMCallNode.USER_ARGUMENT_OFFSET; i++) {
                args[i] = dataEscapeNodes[i].executeWithTarget(arguments[i + LLVMCallNode.USER_ARGUMENT_OFFSET]);
            }
            return args;
        }

        protected static Node createCrossLanguageCallNode(Object[] arguments) {
            return Message.createExecute(arguments.length).createNode();
        }

        protected ForeignToLLVM createToLLVMNode() {
            return ForeignToLLVM.create(type.getReturnType());
        }

        protected LLVMDataEscapeNode[] createLLVMDataEscapeNodes() {
            LLVMDataEscapeNode[] args = new LLVMDataEscapeNode[type.getArgumentTypes().length - LLVMCallNode.USER_ARGUMENT_OFFSET];
            for (int i = 0; i < type.getArgumentTypes().length - LLVMCallNode.USER_ARGUMENT_OFFSET; i++) {
                args[i] = LLVMDataEscapeNode.create();
            }
            return args;
        }

        public static LLVMLookupDispatchForeignNode create(FunctionType type) {
            return LLVMLookupDispatchForeignNodeGen.create(type);
        }
    }

    protected final LLVMMemory getLLVMMemoryCached() {
        if (llvmMemory == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            llvmMemory = getLLVMMemory();
        }
        return llvmMemory;
    }

    protected boolean isAutoDerefHandle(LLVMNativePointer addr) {
        return getLLVMMemoryCached().isDerefMemory(addr);
    }

    protected LLVMDerefHandleGetReceiverNode getDerefHandleGetReceiverNode() {
        if (derefHandleGetReceiverNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            derefHandleGetReceiverNode = insert(LLVMDerefHandleGetReceiverNode.create());
        }
        return derefHandleGetReceiverNode;
    }
}
