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
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.llvm.nodes.func.LLVMLookupDispatchNodeGen.LLVMLookupDispatchForeignNodeGen;
import com.oracle.truffle.llvm.nodes.memory.load.LLVMDerefHandleGetReceiverNode;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.except.LLVMPolyglotException;
import com.oracle.truffle.llvm.runtime.interop.LLVMDataEscapeNode;
import com.oracle.truffle.llvm.runtime.interop.LLVMTypedForeignObject;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack.StackPointer;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.types.FunctionType;
import com.oracle.truffle.llvm.runtime.types.VoidType;

public abstract class LLVMLookupDispatchNode extends LLVMNode {

    protected static final int INLINE_CACHE_SIZE = 5;

    protected final FunctionType type;
    @CompilationFinal private LLVMMemory llvmMemory;

    @Child private LLVMDerefHandleGetReceiverNode derefHandleGetReceiverNode;

    protected LLVMLookupDispatchNode(FunctionType type) {
        this.type = type;
    }

    public abstract Object executeDispatch(Object function, Object[] arguments);

    @Specialization(limit = "INLINE_CACHE_SIZE", guards = {"isSameObject(pointer.getObject(), cachedDescriptor)", "cachedDescriptor != null", "pointer.getOffset() == 0"})
    protected static Object doDirectCached(@SuppressWarnings("unused") LLVMManagedPointer pointer, Object[] arguments,
                    @Cached("asFunctionDescriptor(pointer.getObject())") LLVMFunctionDescriptor cachedDescriptor,
                    @Cached("createCachedDispatch()") LLVMDispatchNode dispatchNode) {
        return dispatchNode.executeDispatch(cachedDescriptor, arguments);
    }

    @Specialization(guards = {"isFunctionDescriptor(pointer.getObject())", "pointer.getOffset() == 0"}, replaces = "doDirectCached")
    protected static Object doDirect(LLVMManagedPointer pointer, Object[] arguments,
                    @Cached("createCachedDispatch()") LLVMDispatchNode dispatchNode) {
        LLVMFunctionDescriptor descriptor = (LLVMFunctionDescriptor) pointer.getObject();
        return dispatchNode.executeDispatch(descriptor, arguments);
    }

    @Specialization(guards = {"isForeignFunction(pointer.getObject())", "pointer.getOffset() == 0"})
    protected Object doForeign(LLVMManagedPointer pointer, Object[] arguments,
                    @Cached("create(type)") LLVMLookupDispatchForeignNode lookupDispatchForeignNode) {
        LLVMTypedForeignObject foreign = (LLVMTypedForeignObject) pointer.getObject();
        return lookupDispatchForeignNode.execute(foreign.getForeign(), foreign.getType(), arguments);
    }

    @Specialization(limit = "INLINE_CACHE_SIZE", guards = {"pointer.asNative() == cachedAddress", "!isAutoDerefHandle(cachedAddress)", "cachedDescriptor != null"})
    protected static Object doHandleCached(@SuppressWarnings("unused") LLVMNativePointer pointer, Object[] arguments,
                    @Cached("pointer.asNative()") @SuppressWarnings("unused") long cachedAddress,
                    @Cached("lookupFunction(pointer)") LLVMFunctionDescriptor cachedDescriptor,
                    @Cached("createCachedDispatch()") LLVMDispatchNode dispatchNode) {
        return dispatchNode.executeDispatch(cachedDescriptor, arguments);
    }

    @Specialization(limit = "INLINE_CACHE_SIZE", guards = {"pointer.asNative() == cachedAddress", "!isAutoDerefHandle(cachedAddress)", "cachedDescriptor == null"})
    protected static Object doNativeFunctionCached(LLVMNativePointer pointer, Object[] arguments,
                    @Cached("pointer.asNative()") @SuppressWarnings("unused") long cachedAddress,
                    @Cached("lookupFunction(pointer)") @SuppressWarnings("unused") LLVMFunctionDescriptor cachedDescriptor,
                    @Cached("createCachedNativeDispatch()") LLVMNativeDispatchNode dispatchNode) {
        return dispatchNode.executeDispatch(pointer, arguments);
    }

    @Specialization(guards = "!isAutoDerefHandle(pointer.asNative())", replaces = {"doHandleCached", "doNativeFunctionCached"})
    protected Object doLookup(LLVMNativePointer pointer, Object[] arguments,
                    @Cached("createCachedDispatch()") LLVMDispatchNode dispatchNode,
                    @Cached("createCachedNativeDispatch()") LLVMNativeDispatchNode dispatchNativeNode) {
        LLVMFunctionDescriptor descriptor = lookupFunction(pointer);
        if (descriptor != null) {
            return dispatchNode.executeDispatch(descriptor, arguments);
        } else {
            return dispatchNativeNode.executeDispatch(pointer, arguments);
        }
    }

    @Specialization(guards = "isAutoDerefHandle(pointer.asNative())")
    protected Object doDerefHandle(LLVMNativePointer pointer, Object[] arguments,
                    @Cached("create(type)") LLVMLookupDispatchForeignNode lookupDispatchForeignNode) {
        LLVMManagedPointer foreignFunction = getDerefHandleGetReceiverNode().execute(pointer);
        return doForeign(foreignFunction, arguments, lookupDispatchForeignNode);
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

    protected static boolean isForeignFunction(TruffleObject object) {
        return object instanceof LLVMTypedForeignObject;
    }

    abstract static class LLVMLookupDispatchForeignNode extends LLVMNode {
        private final FunctionType type;

        LLVMLookupDispatchForeignNode(FunctionType type) {
            this.type = type;
        }

        abstract Object execute(TruffleObject function, LLVMInteropType.Structured interopType, Object[] arguments);

        @Specialization(guards = "functionType == cachedType")
        protected Object doCachedType(TruffleObject function, @SuppressWarnings("unused") LLVMInteropType.Function functionType, Object[] arguments,
                        @Cached("functionType") LLVMInteropType.Function cachedType,
                        @Cached("createCrossLanguageCallNode()") Node crossLanguageCallNode,
                        @Cached("createLLVMDataEscapeNodes()") LLVMDataEscapeNode[] dataEscapeNodes,
                        @Cached("createToLLVMNode()") ForeignToLLVM toLLVMNode) {
            return doGeneric(function, cachedType, arguments, crossLanguageCallNode, dataEscapeNodes, toLLVMNode);
        }

        @Specialization(replaces = "doCachedType")
        protected Object doGeneric(TruffleObject function, LLVMInteropType.Function functionType, Object[] arguments,
                        @Cached("createCrossLanguageCallNode()") Node crossLanguageCallNode,
                        @Cached("createLLVMDataEscapeNodes()") LLVMDataEscapeNode[] dataEscapeNodes,
                        @Cached("createToLLVMNode()") ForeignToLLVM toLLVMNode) {
            try {
                Object[] args = getForeignArguments(dataEscapeNodes, arguments, functionType);
                Object ret;
                try (StackPointer save = ((StackPointer) arguments[0]).newFrame()) {
                    ret = ForeignAccess.sendExecute(crossLanguageCallNode, function, args);
                }
                if (!(type.getReturnType() instanceof VoidType) && functionType != null) {
                    LLVMInteropType retType = functionType.getReturnType();
                    if (retType instanceof LLVMInteropType.Value) {
                        return toLLVMNode.executeWithType(ret, ((LLVMInteropType.Value) retType).getBaseType());
                    } else {
                        CompilerDirectives.transferToInterpreter();
                        throw new LLVMPolyglotException(this, "Can not call polyglot function with structured return type.");
                    }
                } else {
                    return toLLVMNode.executeWithTarget(ret);
                }
            } catch (InteropException e) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException(e);
            }
        }

        @Specialization(guards = "functionType == null")
        protected Object doUnknownType(TruffleObject function, @SuppressWarnings("unused") LLVMInteropType.Structured functionType, Object[] arguments,
                        @Cached("createCrossLanguageCallNode()") Node crossLanguageCallNode,
                        @Cached("createLLVMDataEscapeNodes()") LLVMDataEscapeNode[] dataEscapeNodes,
                        @Cached("createToLLVMNode()") ForeignToLLVM toLLVMNode) {
            return doGeneric(function, null, arguments, crossLanguageCallNode, dataEscapeNodes, toLLVMNode);
        }

        @ExplodeLoop
        private Object[] getForeignArguments(LLVMDataEscapeNode[] dataEscapeNodes, Object[] arguments, LLVMInteropType.Function functionType) {
            assert arguments.length == type.getArgumentTypes().length;
            Object[] args = new Object[dataEscapeNodes.length];
            if (functionType == null) {
                for (int i = 0; i < args.length; i++) {
                    args[i] = dataEscapeNodes[i].executeWithTarget(arguments[i + LLVMCallNode.USER_ARGUMENT_OFFSET]);
                }
            } else {
                assert arguments.length == functionType.getParameterLength() + LLVMCallNode.USER_ARGUMENT_OFFSET;
                for (int i = 0; i < args.length; i++) {
                    LLVMInteropType argType = functionType.getParameter(i);
                    if (argType instanceof LLVMInteropType.Value) {
                        LLVMInteropType.Structured baseType = ((LLVMInteropType.Value) argType).getBaseType();
                        args[i] = dataEscapeNodes[i].executeWithType(arguments[i + LLVMCallNode.USER_ARGUMENT_OFFSET], baseType);
                    } else {
                        CompilerDirectives.transferToInterpreter();
                        throw new LLVMPolyglotException(this, "Can not call polyglot function with structured argument type.");
                    }
                }
            }
            return args;
        }

        @TruffleBoundary
        protected static Node createCrossLanguageCallNode() {
            return Message.EXECUTE.createNode();
        }

        @TruffleBoundary
        protected ForeignToLLVM createToLLVMNode() {
            return getNodeFactory().createForeignToLLVM(ForeignToLLVM.convert(type.getReturnType()));
        }

        @TruffleBoundary
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

    protected boolean isAutoDerefHandle(long addr) {
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
