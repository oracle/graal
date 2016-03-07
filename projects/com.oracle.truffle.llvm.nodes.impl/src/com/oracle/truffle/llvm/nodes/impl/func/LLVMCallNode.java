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
package com.oracle.truffle.llvm.nodes.impl.func;

import com.oracle.nfi.api.NativeFunctionHandle;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.llvm.nodes.base.LLVMExpressionNode;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMAddressNode;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMContext;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMFunctionNode;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMLanguage;
import com.oracle.truffle.llvm.nodes.impl.base.floating.LLVM80BitFloatNode;
import com.oracle.truffle.llvm.nodes.impl.cast.LLVMToI64NodeFactory.LLVMAddressToI64NodeGen;
import com.oracle.truffle.llvm.nodes.impl.func.LLVMCallNodeFactory.LLVM80BitArgConvertNodeGen;
import com.oracle.truffle.llvm.nodes.impl.func.LLVMCallNodeFactory.LLVMFunctionCallChainNodeGen;
import com.oracle.truffle.llvm.nodes.impl.func.LLVMNativeCallConvertNode.LLVMResolvedNative80BitFloatCallNode;
import com.oracle.truffle.llvm.nodes.impl.func.LLVMNativeCallConvertNode.LLVMResolvedNativeAddressCallNode;
import com.oracle.truffle.llvm.nodes.impl.literals.LLVMFunctionLiteralNode;
import com.oracle.truffle.llvm.nodes.impl.literals.LLVMSimpleLiteralNode.LLVMI64LiteralNode;
import com.oracle.truffle.llvm.runtime.LLVMUnsupportedException;
import com.oracle.truffle.llvm.runtime.LLVMUnsupportedException.UnsupportedReason;
import com.oracle.truffle.llvm.types.LLVMFunction;
import com.oracle.truffle.llvm.types.LLVMFunction.LLVMRuntimeType;
import com.oracle.truffle.llvm.types.floating.LLVM80BitFloat;

public abstract class LLVMCallNode {

    public static final int ARG_START_INDEX = 0;

    /**
     * Call node for a Sulong or native function where the target function is still unresolved.
     */
    public static class LLVMUnresolvedCallNode extends LLVMExpressionNode {

        @Child private LLVMFunctionNode functionNode;
        @Children private final LLVMExpressionNode[] args;
        private final LLVMContext context;

        public LLVMUnresolvedCallNode(LLVMFunctionNode functionNode, LLVMExpressionNode[] args, LLVMContext context) {
            this.functionNode = functionNode;
            this.args = args;
            this.context = context;
        }

        @Override
        public Object executeGeneric(VirtualFrame frame) {
            CompilerDirectives.transferToInterpreter();
            if (functionNode instanceof LLVMFunctionLiteralNode) {
                LLVMFunction function = functionNode.executeFunction(frame);
                CallTarget callTarget = context.getFunction(function);
                if (callTarget == null) {
                    NativeFunctionHandle nativeHandle = context.getNativeHandle(function, args);
                    if (nativeHandle == null) {
                        throw new IllegalStateException("could not find function " + function.getName());
                    }
                    return replace(getResolvedNativeCall(function, nativeHandle, args, context)).executeGeneric(frame);
                } else {
                    return replace(new LLVMResolvedDirectCallNode(callTarget, args)).executeGeneric(frame);
                }
            } else {
                LLVMFunctionCallChain rootNode = LLVMFunctionCallChainNodeGen.create(context, args);
                return replace(new LLVMFunctionCallChainStartNode(functionNode, rootNode, args)).executeGeneric(frame);
            }
        }

    }

    /**
     * Call node for a Sulong function where the function is constant.
     */
    public static class LLVMResolvedDirectCallNode extends LLVMExpressionNode {

        @Children private final LLVMExpressionNode[] args;
        @Child protected DirectCallNode callNode;

        public LLVMResolvedDirectCallNode(CallTarget callTarget, LLVMExpressionNode[] args) {
            this.callNode = Truffle.getRuntime().createDirectCallNode(callTarget);
            this.args = args;
        }

        @Override
        @ExplodeLoop
        public Object executeGeneric(VirtualFrame frame) {
            Object[] argValues = new Object[args.length];
            for (int i = ARG_START_INDEX; i < args.length; i++) {
                argValues[i] = args[i].executeGeneric(frame);
            }
            return callNode.call(frame, argValues);
        }

    }

    /**
     * Call node for a native function where the function is constant.
     */
    public static class LLVMResolvedDirectNativeCallNode extends LLVMExpressionNode {

        private final NativeFunctionHandle functionHandle;
        @Children private final LLVMExpressionNode[] args;

        public LLVMResolvedDirectNativeCallNode(@SuppressWarnings("unused") LLVMFunction function, NativeFunctionHandle nativeFunctionHandle, LLVMExpressionNode[] args, LLVMContext context) {
            functionHandle = nativeFunctionHandle;
            this.args = prepareForNative(args, context);
        }

        protected static LLVMExpressionNode[] prepareForNative(LLVMExpressionNode[] originalArgs, LLVMContext context) {
            CompilerAsserts.neverPartOfCompilation();
            LLVMExpressionNode[] newNodes = new LLVMExpressionNode[originalArgs.length];
            for (int i = 0; i < newNodes.length; i++) {
                if (originalArgs[i] instanceof LLVMAddressNode) {
                    newNodes[i] = LLVMAddressToI64NodeGen.create((LLVMAddressNode) originalArgs[i]);
                } else if (originalArgs[i] instanceof LLVM80BitFloatNode) {
                    newNodes[i] = LLVM80BitArgConvertNodeGen.create((LLVM80BitFloatNode) originalArgs[i]);
                    throw new AssertionError("foreign function interface does not support 80 bit floats yet");
                } else if (originalArgs[i] instanceof LLVMFunctionNode) {
                    LLVMFunction function = ((LLVMFunctionLiteralNode) originalArgs[i]).executeFunction();
                    String functionName = function.getName();
                    long getNativeSymbol = context.getNativeHandle(functionName);
                    if (getNativeSymbol != 0) {
                        newNodes[i] = new LLVMI64LiteralNode(getNativeSymbol);
                    } else {
                        throw new LLVMUnsupportedException(UnsupportedReason.FUNCTION_POINTER_ESCAPES_TO_NATIVE);
                    }
                } else {
                    newNodes[i] = originalArgs[i];
                }
            }
            return newNodes;
        }

        @Override
        @ExplodeLoop
        public Object executeGeneric(VirtualFrame frame) {
            Object[] argValues = new Object[args.length];
            for (int i = 0; i < args.length; i++) {
                argValues[i] = args[i].executeGeneric(frame);
            }
            return functionHandle.call(argValues);
        }

    }

    public static LLVMResolvedDirectNativeCallNode getResolvedNativeCall(LLVMFunction function, NativeFunctionHandle nativeHandle, LLVMExpressionNode[] args, LLVMContext context) {
        if (function.getLlvmReturnType() == LLVMRuntimeType.ADDRESS || function.getLlvmReturnType() == LLVMRuntimeType.STRUCT) {
            return new LLVMResolvedNativeAddressCallNode(function, nativeHandle, args, context);
        } else if (function.getLlvmReturnType() == LLVMRuntimeType.X86_FP80) {
            return new LLVMResolvedNative80BitFloatCallNode(function, nativeHandle, args, context);
        } else {
            return new LLVMResolvedDirectNativeCallNode(function, nativeHandle, args, context);
        }
    }

    public static class NativeCallTarget implements CallTarget {

        private final NativeFunctionHandle functionHandle;

        public NativeCallTarget(NativeFunctionHandle nativeFunctionHandle) {
            functionHandle = nativeFunctionHandle;
        }

        public Object call(Object... arguments) {
            return functionHandle.call(arguments);
        }

    }

    public static class LLVMFunctionCallChainStartNode extends LLVMExpressionNode {

        @Child private LLVMFunctionCallChain chain;
        @Children private final LLVMExpressionNode[] args;
        @Child private LLVMFunctionNode functionCallNode;

        public LLVMFunctionCallChainStartNode(LLVMFunctionNode functionCallNode, LLVMFunctionCallChain chain, LLVMExpressionNode[] args) {
            this.functionCallNode = functionCallNode;
            this.chain = chain;
            this.args = args;
        }

        @Override
        @ExplodeLoop
        public Object executeGeneric(VirtualFrame frame) {
            Object[] argValues = new Object[args.length];
            for (int i = ARG_START_INDEX; i < args.length; i++) {
                argValues[i] = args[i].executeGeneric(frame);
            }
            LLVMFunction function = functionCallNode.executeFunction(frame);
            return chain.executeDispatch(frame, function, argValues);
        }

    }

    public abstract static class LLVMFunctionCallChain extends Node {

        private final LLVMContext context;

        @Children private final LLVMExpressionNode[] nodes;

        public LLVMFunctionCallChain(LLVMContext context, LLVMExpressionNode[] nodes) {
            this.context = context;
            this.nodes = nodes;
        }

        protected static final int INLINE_CACHE_SIZE = 2;

        public abstract Object executeDispatch(VirtualFrame frame, LLVMFunction function, Object[] arguments);

        public static CallTarget getIndirectCallTarget(LLVMContext context, LLVMFunction function, LLVMExpressionNode[] args) {
            CallTarget callTarget = context.getFunction(function);
            if (callTarget == null) {
                return getNativeCallTarget(context, function, args);
            } else {
                return callTarget;
            }
        }

        @TruffleBoundary
        private static CallTarget getNativeCallTarget(LLVMContext context, LLVMFunction function, LLVMExpressionNode[] args) {
            final NativeFunctionHandle nativeHandle = context.getNativeHandle(function, args);
            if (nativeHandle == null) {
                throw new IllegalStateException("could not find function " + function.getName());
            } else {
                return Truffle.getRuntime().createCallTarget(new RootNode(LLVMLanguage.class, null, null) {

                    @Override
                    public Object execute(VirtualFrame frame) {
                        return nativeHandle.call(frame.getArguments());
                    }
                });
            }
        }

        @Specialization(limit = "INLINE_CACHE_SIZE", guards = "function.getFunctionAddress() == cachedFunction.getFunctionAddress()")
        protected Object doDirect(VirtualFrame frame, @SuppressWarnings("unused") LLVMFunction function, Object[] arguments, //
                        @SuppressWarnings("unused") @Cached("function") LLVMFunction cachedFunction, //
                        @Cached("create(getIndirectCallTarget(getContext(), cachedFunction, getNodes()))") DirectCallNode callNode) {
            return callNode.call(frame, arguments);
        }

        @Specialization(contains = "doDirect")
        protected Object doIndirect(VirtualFrame frame, LLVMFunction function, Object[] arguments, //
                        @Cached("create()") IndirectCallNode callNode) {
            return callNode.call(frame, getIndirectCallTarget(getContext(), function, getNodes()), arguments);
        }

        public LLVMContext getContext() {
            return context;
        }

        public LLVMExpressionNode[] getNodes() {
            return nodes;
        }

    }

    public abstract static class LLVMByteArrayNode extends LLVMExpressionNode {

        public abstract byte[] executeByteArray();

        @Override
        public Object executeGeneric(VirtualFrame frame) {
            return executeByteArray();
        }

    }

    @NodeChild(value = "fromNode", type = LLVM80BitFloatNode.class)
    public abstract static class LLVM80BitArgConvertNode extends LLVMByteArrayNode {

        @Specialization
        public byte[] executeByteArray(LLVM80BitFloat from) {
            return from.getBytes();
        }
    }

}
