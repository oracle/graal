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
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
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
import com.oracle.truffle.llvm.nodes.impl.func.LLVMCallNodeFactory.LLVMFunctionCallChainNodeGen;
import com.oracle.truffle.llvm.nodes.impl.func.LLVMNativeCallConvertNode.LLVMResolvedNative80BitFloatCallNode;
import com.oracle.truffle.llvm.nodes.impl.func.LLVMNativeCallConvertNode.LLVMResolvedNativeAddressCallNode;
import com.oracle.truffle.llvm.nodes.impl.literals.LLVMFunctionLiteralNode;
import com.oracle.truffle.llvm.nodes.impl.literals.LLVMSimpleLiteralNode.LLVMI64LiteralNode;
import com.oracle.truffle.llvm.runtime.LLVMLogger;
import com.oracle.truffle.llvm.runtime.LLVMUnsupportedException;
import com.oracle.truffle.llvm.runtime.LLVMUnsupportedException.UnsupportedReason;
import com.oracle.truffle.llvm.runtime.options.LLVMBaseOptionFacade;
import com.oracle.truffle.llvm.types.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.types.LLVMFunctionDescriptor.LLVMRuntimeType;
import com.oracle.truffle.llvm.types.floating.LLVM80BitFloat;

public abstract class LLVMCallNode {

    public static final int ARG_START_INDEX = 1;

    protected static LLVMExpressionNode[] prepareForNative(LLVMExpressionNode[] originalArgs, LLVMContext context) {
        CompilerAsserts.neverPartOfCompilation();
        LLVMExpressionNode[] newNodes = new LLVMExpressionNode[originalArgs.length - LLVMCallNode.ARG_START_INDEX];
        for (int i = 0; i < newNodes.length; i++) {
            newNodes[i] = getArgNode(originalArgs, i + LLVMCallNode.ARG_START_INDEX, context);
        }
        return newNodes;
    }

    private static LLVMExpressionNode getArgNode(LLVMExpressionNode[] originalArgs, int i, LLVMContext context) {
        if (originalArgs[i] instanceof LLVMAddressNode) {
            return LLVMAddressToI64NodeGen.create((LLVMAddressNode) originalArgs[i]);
        } else if (originalArgs[i] instanceof LLVM80BitFloatNode) {
            throw new AssertionError("foreign function interface does not support 80 bit floats yet");
        } else if (originalArgs[i] instanceof LLVMFunctionNode) {
            LLVMFunctionDescriptor function = ((LLVMFunctionLiteralNode) originalArgs[i]).executeFunction();
            String functionName = function.getName();
            long getNativeSymbol = context.getNativeHandle(functionName);
            if (getNativeSymbol != 0) {
                return new LLVMI64LiteralNode(getNativeSymbol);
            } else {
                throw new LLVMUnsupportedException(UnsupportedReason.FUNCTION_POINTER_ESCAPES_TO_NATIVE);
            }
        } else {
            return originalArgs[i];
        }
    }

    public abstract static class LLVMAbstractCallNode extends LLVMExpressionNode {

        @Children private final LLVMExpressionNode[] args;

        public LLVMAbstractCallNode(LLVMExpressionNode[] args) {
            this.args = args;
        }

        @ExplodeLoop
        Object[] evaluateArgs(VirtualFrame frame) {
            Object[] argValues = new Object[args.length];
            for (int i = 0; i < args.length; i++) {
                argValues[i] = args[i].executeGeneric(frame);
            }
            return argValues;
        }

        public LLVMExpressionNode[] getArgs() {
            return args;
        }
    }

    /**
     * Call node for a Sulong or native function where the target function is still unresolved.
     */
    public static class LLVMUnresolvedCallNode extends LLVMAbstractCallNode {

        @Child private LLVMFunctionNode functionNode;
        private final LLVMContext context;

        public LLVMUnresolvedCallNode(LLVMFunctionNode functionNode, LLVMExpressionNode[] args, LLVMContext context) {
            super(args);
            this.functionNode = functionNode;
            this.context = context;
        }

        @Override
        public Object executeGeneric(VirtualFrame frame) {
            CompilerDirectives.transferToInterpreter();
            if (functionNode instanceof LLVMFunctionLiteralNode) {
                LLVMFunctionDescriptor function = functionNode.executeFunction(frame);
                CallTarget callTarget = context.getFunction(function);
                if (callTarget == null) {
                    NativeFunctionHandle nativeHandle = context.getNativeHandle(function, prepareForNative(getArgs(), context));
                    if (nativeHandle == null) {
                        throw new IllegalStateException("could not find function " + function.getName());
                    }
                    return replace(getResolvedNativeCall(function, nativeHandle, getArgs(), context)).executeGeneric(frame);
                } else {
                    return replace(new LLVMResolvedDirectCallNode(callTarget, getArgs())).executeGeneric(frame);
                }
            } else {
                LLVMFunctionCallChain rootNode = LLVMFunctionCallChainNodeGen.create(context, getArgs());
                return replace(new LLVMFunctionCallChainStartNode(functionNode, rootNode, getArgs())).executeGeneric(frame);
            }
        }

    }

    /**
     * Call node for a Sulong function where the function is constant.
     */
    public static class LLVMResolvedDirectCallNode extends LLVMAbstractCallNode {

        @Child protected DirectCallNode callNode;

        public LLVMResolvedDirectCallNode(CallTarget callTarget, LLVMExpressionNode[] args) {
            super(args);
            this.callNode = Truffle.getRuntime().createDirectCallNode(callTarget);
        }

        @Override
        public Object executeGeneric(VirtualFrame frame) {
            return callNode.call(frame, evaluateArgs(frame));
        }

    }

    /**
     * Call node for a native function where the function is constant.
     */
    public static class LLVMResolvedDirectNativeCallNode extends LLVMAbstractCallNode {

        private final NativeFunctionHandle functionHandle;

        public LLVMResolvedDirectNativeCallNode(@SuppressWarnings("unused") LLVMFunctionDescriptor function, NativeFunctionHandle nativeFunctionHandle, LLVMExpressionNode[] args,
                        LLVMContext context) {
            super(prepareForNative(args, context));
            functionHandle = nativeFunctionHandle;
        }

        @Override
        public Object executeGeneric(VirtualFrame frame) {
            return functionHandle.call(evaluateArgs(frame));
        }

    }

    public static LLVMResolvedDirectNativeCallNode getResolvedNativeCall(LLVMFunctionDescriptor function, NativeFunctionHandle nativeHandle, LLVMExpressionNode[] args, LLVMContext context) {
        if (function.getReturnType() == LLVMRuntimeType.ADDRESS || function.getReturnType() == LLVMRuntimeType.STRUCT) {
            return new LLVMResolvedNativeAddressCallNode(function, nativeHandle, args, context);
        } else if (function.getReturnType() == LLVMRuntimeType.X86_FP80) {
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

        @Override
        public Object call(Object... arguments) {
            return functionHandle.call(arguments);
        }

    }

    public static class LLVMFunctionCallChainStartNode extends LLVMAbstractCallNode {

        @Child private LLVMFunctionCallChain chain;
        @Child private LLVMFunctionNode functionCallNode;

        public LLVMFunctionCallChainStartNode(LLVMFunctionNode functionCallNode, LLVMFunctionCallChain chain, LLVMExpressionNode[] args) {
            super(args);
            this.functionCallNode = functionCallNode;
            this.chain = chain;
        }

        @Override
        public Object executeGeneric(VirtualFrame frame) {
            Object[] args = evaluateArgs(frame);
            LLVMFunctionDescriptor function = functionCallNode.executeFunction(frame);
            return chain.executeDispatch(frame, function, args);
        }

    }

    public abstract static class LLVMFunctionCallChain extends Node {

        private final LLVMContext context;

        @Children private final LLVMExpressionNode[] nodes;

        @CompilationFinal private boolean printedNativePerformanceWarning;
        @CompilationFinal private boolean printedExceedInlineCacheWarning;

        protected static final int INLINE_CACHE_SIZE = LLVMBaseOptionFacade.getInlineCacheSize();

        public LLVMFunctionCallChain(LLVMContext context, LLVMExpressionNode[] nodes) {
            this.context = context;
            this.nodes = nodes;
        }

        public abstract Object executeDispatch(VirtualFrame frame, LLVMFunctionDescriptor function, Object[] arguments);

        public CallTarget getIndirectCallTarget(LLVMContext currentContext, LLVMFunctionDescriptor function, LLVMExpressionNode[] args) {
            CallTarget callTarget = currentContext.getFunction(function);
            if (callTarget == null) {
                return getNativeCallTarget(currentContext, function, args);
            } else {
                return callTarget;
            }
        }

        @TruffleBoundary
        private CallTarget getNativeCallTarget(LLVMContext currentContext, LLVMFunctionDescriptor function, LLVMExpressionNode[] args) {
            if (CompilerDirectives.inInterpreter() && !printedNativePerformanceWarning) {
                printIndirectNativeCallWarning(function);
            }
            final NativeFunctionHandle nativeHandle = currentContext.getNativeHandle(function, prepareForNative(args, currentContext));
            if (nativeHandle == null) {
                throw new IllegalStateException("could not find function " + function.getName());
            } else {
                return Truffle.getRuntime().createCallTarget(new RootNode(LLVMLanguage.class, null, null) {

                    @Override
                    public Object execute(VirtualFrame frame) {
                        Object[] arguments = frame.getArguments();
                        Object[] newArguments = new Object[arguments.length - LLVMCallNode.ARG_START_INDEX];
                        System.arraycopy(arguments, LLVMCallNode.ARG_START_INDEX, newArguments, 0, newArguments.length);
                        return nativeHandle.call(newArguments);
                    }
                });
            }
        }

        @Specialization(limit = "INLINE_CACHE_SIZE", guards = "function.getFunctionIndex() == cachedFunction.getFunctionIndex()")
        protected Object doDirect(VirtualFrame frame, @SuppressWarnings("unused") LLVMFunctionDescriptor function, Object[] arguments, //
                        @SuppressWarnings("unused") @Cached("function") LLVMFunctionDescriptor cachedFunction, //
                        @Cached("create(getIndirectCallTarget(getContext(), cachedFunction, getNodes()))") DirectCallNode callNode) {
            return callNode.call(frame, arguments);
        }

        @Specialization(contains = "doDirect")
        protected Object doIndirect(VirtualFrame frame, LLVMFunctionDescriptor function, Object[] arguments, //
                        @Cached("create()") IndirectCallNode callNode) {
            if (CompilerDirectives.inInterpreter() && !printedExceedInlineCacheWarning) {
                printExceededInlineCacheWarning(function);
            }
            return callNode.call(frame, getIndirectCallTarget(getContext(), function, getNodes()), arguments);
        }

        private void printIndirectNativeCallWarning(LLVMFunctionDescriptor function) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            printedNativePerformanceWarning = true;
            LLVMLogger.performanceWarning("indirectly calling a native function " + function.getName() + " + is expensive at the moment!");
        }

        private void printExceededInlineCacheWarning(LLVMFunctionDescriptor function) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            LLVMLogger.performanceWarning("exceeded inline cache limit for function " + function);
            printedExceedInlineCacheWarning = true;
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
