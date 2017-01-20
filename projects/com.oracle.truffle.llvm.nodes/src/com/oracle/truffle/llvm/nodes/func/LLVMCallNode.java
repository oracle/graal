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
package com.oracle.truffle.llvm.nodes.func;

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
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.llvm.context.LLVMContext;
import com.oracle.truffle.llvm.context.LLVMLanguage;
import com.oracle.truffle.llvm.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.nodes.cast.LLVMToI64NodeFactory.LLVMAnyToI64NodeGen;
import com.oracle.truffle.llvm.nodes.func.LLVMCallNodeFactory.LLVMFunctionCallChainNodeGen;
import com.oracle.truffle.llvm.nodes.func.LLVMNativeCallConvertNode.LLVMResolvedNative80BitFloatCallNode;
import com.oracle.truffle.llvm.nodes.func.LLVMNativeCallConvertNode.LLVMResolvedNativeAddressCallNode;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.ToLLVMNode;
import com.oracle.truffle.llvm.nodes.literals.LLVMFunctionLiteralNode;
import com.oracle.truffle.llvm.nodes.literals.LLVMSimpleLiteralNode.LLVMI64LiteralNode;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor.LLVMRuntimeType;
import com.oracle.truffle.llvm.runtime.LLVMPerformance;
import com.oracle.truffle.llvm.runtime.LLVMUnsupportedException;
import com.oracle.truffle.llvm.runtime.LLVMUnsupportedException.UnsupportedReason;
import com.oracle.truffle.llvm.runtime.floating.LLVM80BitFloat;
import com.oracle.truffle.llvm.runtime.types.LLVMBaseType;
import com.oracle.truffle.llvm.runtime.types.LLVMType;

public abstract class LLVMCallNode {

    public static final int ARG_START_INDEX = 1;

    private static int getFunctionArgumentLength(VirtualFrame frame) {
        return frame.getArguments().length - ARG_START_INDEX;
    }

    /**
     *
     * @param originalArgs the original args to the native function
     * @param context the context needed to retrieve a native function handle if one of the
     *            arguments is a function literal node
     * @return the converted arguments
     * @throws LLVMUnsupportedException if one of the argument nodes is a Truffle function node that
     *             would escape to the native side
     */
    public static LLVMExpressionNode[] convertToPrimitiveNodes(LLVMExpressionNode[] originalArgs, LLVMType[] argsTypes, LLVMContext context) {
        CompilerAsserts.neverPartOfCompilation();
        int realArgsLength = originalArgs.length - LLVMCallNode.ARG_START_INDEX;
        LLVMExpressionNode[] newNodes = new LLVMExpressionNode[realArgsLength];
        for (int i = 0; i < newNodes.length; i++) {
            LLVMExpressionNode originalArg = originalArgs[i + LLVMCallNode.ARG_START_INDEX];
            LLVMType argType = argsTypes[i + LLVMCallNode.ARG_START_INDEX];
            newNodes[i] = convertToPrimitiveNode(originalArg, argType, context);
        }
        return newNodes;
    }

    /**
     * Converts a Sulong Truffle node to a node that returns a primitive that can be used as an
     * argument to Graal's NFI.
     *
     * @param originalArg an argument to the native function
     * @param argType
     * @param context the context needed to retrieve a native function handle if one of the
     *            arguments is a function literal node
     * @return the conversion node that wraps the originalArg or the originalArg node itself
     * @throws LLVMUnsupportedException if one of the argument nodes is a Truffle function node that
     *             would escape to the native side
     */
    public static LLVMExpressionNode convertToPrimitiveNode(LLVMExpressionNode originalArg, LLVMType argType, LLVMContext context) {
        CompilerAsserts.neverPartOfCompilation();
        if (argType.getType() == LLVMBaseType.ADDRESS) {
            return LLVMAnyToI64NodeGen.create(originalArg);
        } else if (argType.getType() == LLVMBaseType.X86_FP80) {
            throw new AssertionError("foreign function interface does not support 80 bit floats yet");
        } else if (argType.getType() == LLVMBaseType.FUNCTION_ADDRESS) {
            LLVMFunctionDescriptor function = ((LLVMFunctionLiteralNode) originalArg).executeFunction();
            if (function.isNullFunction()) {
                return new LLVMI64LiteralNode(0);
            } else {
                String functionName = function.getName();
                long getNativeSymbol = context.getNativeHandle(functionName);
                if (getNativeSymbol != 0) {
                    return new LLVMI64LiteralNode(getNativeSymbol);
                } else {
                    throw new LLVMUnsupportedException(UnsupportedReason.FUNCTION_POINTER_ESCAPES_TO_NATIVE);
                }
            }
        } else {
            return originalArg;
        }
    }

    public abstract static class LLVMAbstractCallNode extends LLVMExpressionNode {

        @Children private final LLVMExpressionNode[] args;
        @CompilationFinal(dimensions = 1) protected final LLVMType[] argsTypes;
        @CompilationFinal(dimensions = 1) protected final LLVMType[] nativeArgsTypes;

        public LLVMAbstractCallNode(LLVMExpressionNode[] args, LLVMType[] argsTypes) {
            this.args = args;
            this.argsTypes = argsTypes;
            this.nativeArgsTypes = new LLVMType[argsTypes.length - LLVMCallNode.ARG_START_INDEX];
            for (int i = LLVMCallNode.ARG_START_INDEX; i < argsTypes.length; i++) {
                nativeArgsTypes[i - LLVMCallNode.ARG_START_INDEX] = argsTypes[i];
            }
        }

        @ExplodeLoop
        protected Object[] evaluateArgs(VirtualFrame frame) {
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

        @Child private LLVMExpressionNode functionNode;
        private final LLVMContext context;
        private final LLVMRuntimeType returnType;

        public LLVMUnresolvedCallNode(LLVMExpressionNode functionNode, LLVMExpressionNode[] args, LLVMType[] argsTypes, LLVMRuntimeType returnType, LLVMContext context) {
            super(args, argsTypes);
            this.functionNode = functionNode;
            this.context = context;
            this.returnType = returnType;
        }

        @Override
        public Object executeGeneric(VirtualFrame frame) {
            CompilerDirectives.transferToInterpreter();
            try {
                if (functionNode instanceof LLVMFunctionLiteralNode) {
                    TruffleObject function = functionNode.executeTruffleObject(frame);
                    LLVMFunctionDescriptor llvmFunction = (LLVMFunctionDescriptor) function;
                    CallTarget callTarget = context.getFunction(llvmFunction);
                    if (callTarget == null) {
                        NativeFunctionHandle nativeHandle = context.getNativeHandle(llvmFunction, nativeArgsTypes);
                        if (nativeHandle == null) {
                            throw new IllegalStateException("could not find function " + llvmFunction.getName());
                        }
                        return replace(getResolvedNativeCall(llvmFunction, nativeHandle, getArgs(), argsTypes, context)).executeGeneric(frame);
                    } else {
                        return replace(new LLVMResolvedDirectCallNode(callTarget, getArgs(), argsTypes)).executeGeneric(frame);
                    }
                } else {
                    LLVMFunctionCallChain rootNode = LLVMFunctionCallChainNodeGen.create(context, getArgs(), argsTypes, returnType);
                    return replace(new LLVMFunctionCallChainStartNode(functionNode, rootNode, getArgs(), argsTypes)).executeGeneric(frame);
                }
            } catch (UnexpectedResultException e) {
                throw new IllegalStateException(e);
            }
        }

    }

    /**
     * Call node for a Sulong function where the function is constant.
     */
    public static class LLVMResolvedDirectCallNode extends LLVMAbstractCallNode {

        @Child protected DirectCallNode callNode;

        public LLVMResolvedDirectCallNode(CallTarget callTarget, LLVMExpressionNode[] args, LLVMType[] argsTypes) {
            super(args, argsTypes);
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

        public LLVMResolvedDirectNativeCallNode(@SuppressWarnings("unused") LLVMFunctionDescriptor function, NativeFunctionHandle nativeFunctionHandle, LLVMExpressionNode[] args, LLVMType[] argsTypes,
                        LLVMContext context) {
            super(convertToPrimitiveNodes(args, argsTypes, context), argsTypes);
            functionHandle = nativeFunctionHandle;
        }

        @Override
        public Object executeGeneric(VirtualFrame frame) {
            return functionHandle.call(evaluateArgs(frame));
        }

    }

    public static LLVMResolvedDirectNativeCallNode getResolvedNativeCall(LLVMFunctionDescriptor function, NativeFunctionHandle nativeHandle, LLVMExpressionNode[] args, LLVMType[] argsTypes,
                    LLVMContext context) {
        switch (function.getReturnType()) {
            case ADDRESS:
            case STRUCT:
            case I1_POINTER:
            case I8_POINTER:
            case I16_POINTER:
            case I32_POINTER:
            case I64_POINTER:
            case HALF_POINTER:
            case FLOAT_POINTER:
            case DOUBLE_POINTER:
                return new LLVMResolvedNativeAddressCallNode(function, nativeHandle, args, argsTypes, context);
            case X86_FP80:
                return new LLVMResolvedNative80BitFloatCallNode(function, nativeHandle, args, argsTypes, context);
            default:
                return new LLVMResolvedDirectNativeCallNode(function, nativeHandle, args, argsTypes, context);
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
        @Child private LLVMExpressionNode functionCallNode;
        @Child private ToLLVMNode toLLVM;
        @Child private Node foreignExecute;

        public LLVMFunctionCallChainStartNode(LLVMExpressionNode functionCallNode, LLVMFunctionCallChain chain, LLVMExpressionNode[] args, LLVMType[] argsTypes) {
            super(args, argsTypes);
            this.functionCallNode = functionCallNode;
            this.chain = chain;
            this.toLLVM = ToLLVMNode.createNode(ToLLVMNode.convert(chain.returnType));
        }

        @Override
        public Object executeGeneric(VirtualFrame frame) {
            try {
                Object[] args = evaluateArgs(frame);
                TruffleObject function = functionCallNode.executeTruffleObject(frame);
                if (function instanceof LLVMFunctionDescriptor) {
                    return chain.executeDispatch(frame, (LLVMFunctionDescriptor) function, args);
                } else {
                    if (foreignExecute == null) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        foreignExecute = insert(Message.createExecute(getFunctionArgumentLength(frame)).createNode());
                    }
                    return doExecute(frame, foreignExecute, function, args, toLLVM);
                }
            } catch (UnexpectedResultException e) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException(e);
            }
        }

        @ExplodeLoop
        private static Object doExecute(VirtualFrame frame, Node foreignExecute, TruffleObject value, Object[] rawArgs, ToLLVMNode toLLVM) {
            int argsLength = rawArgs.length - ARG_START_INDEX;
            Object[] args = new Object[argsLength];
            CompilerAsserts.partialEvaluationConstant(rawArgs.length);
            for (int i = ARG_START_INDEX, j = 0; i < rawArgs.length; i++, j++) {
                args[j] = rawArgs[i];
            }
            try {
                Object rawValue = ForeignAccess.sendExecute(foreignExecute, frame, value, args);
                return toLLVM.executeWithTarget(frame, rawValue);
            } catch (UnsupportedMessageException | UnsupportedTypeException | ArityException e) {
                throw new IllegalStateException(e);
            }
        }

    }

    public abstract static class LLVMFunctionCallChain extends Node {

        private final LLVMContext context;

        @Children private final LLVMExpressionNode[] nodes;

        @CompilationFinal private boolean printedNativePerformanceWarning;
        @CompilationFinal private boolean printedExceedInlineCacheWarning;
        @CompilationFinal(dimensions = 1) private final LLVMType[] argsTypes;
        @CompilationFinal(dimensions = 1) private final LLVMType[] nativeArgsTypes;

        private final LLVMRuntimeType returnType;

        protected static final int INLINE_CACHE_SIZE = 5;

        public LLVMFunctionCallChain(LLVMContext context, LLVMExpressionNode[] nodes, LLVMType[] argsTypes, LLVMRuntimeType returnType) {
            this.context = context;
            this.nodes = nodes;
            this.returnType = returnType;
            this.argsTypes = argsTypes;
            this.nativeArgsTypes = new LLVMType[argsTypes.length - LLVMCallNode.ARG_START_INDEX];
            for (int i = LLVMCallNode.ARG_START_INDEX; i < argsTypes.length; i++) {
                nativeArgsTypes[i - LLVMCallNode.ARG_START_INDEX] = argsTypes[i];
            }
        }

        public abstract Object executeDispatch(VirtualFrame frame, LLVMFunctionDescriptor function, Object[] arguments);

        public CallTarget getIndirectCallTarget(LLVMContext currentContext, LLVMFunctionDescriptor function) {
            CallTarget callTarget = currentContext.getFunction(function);
            if (callTarget == null) {
                return getNativeCallTarget(currentContext, function);
            } else {
                return callTarget;
            }
        }

        @TruffleBoundary
        private CallTarget getNativeCallTarget(LLVMContext currentContext, LLVMFunctionDescriptor function) {
            LLVMPerformance.warn(this, "Indirect native call");
            final NativeFunctionHandle nativeHandle = currentContext.getNativeHandle(function, nativeArgsTypes);
            if (nativeHandle == null) {
                throw new IllegalStateException("could not find function " + function.getName());
            } else {
                return Truffle.getRuntime().createCallTarget(new RootNode(LLVMLanguage.class, null, null) {

                    @Override
                    public Object execute(VirtualFrame frame) {
                        Object[] arguments = frame.getArguments();
                        return nativeHandle.call(convertToPrimitiveArgs(arguments));
                    }
                });
            }
        }

        @ExplodeLoop
        private static Object[] convertToPrimitiveArgs(Object[] arguments) {
            CompilerAsserts.compilationConstant(arguments.length);
            Object[] newArguments = new Object[arguments.length - LLVMCallNode.ARG_START_INDEX];
            for (int i = LLVMCallNode.ARG_START_INDEX; i < arguments.length; i++) {
                newArguments[i - LLVMCallNode.ARG_START_INDEX] = arguments[i];
            }
            for (int i = 0; i < newArguments.length; i++) {
                if (newArguments[i] instanceof LLVMAddress) {
                    newArguments[i] = ((LLVMAddress) newArguments[i]).getVal();
                }
            }
            return newArguments;
        }

        @Specialization(limit = "INLINE_CACHE_SIZE", guards = "function.getFunctionIndex() == cachedFunction.getFunctionIndex()")
        protected Object doDirect(VirtualFrame frame, @SuppressWarnings("unused") LLVMFunctionDescriptor function, Object[] arguments, //
                        @SuppressWarnings("unused") @Cached("function") LLVMFunctionDescriptor cachedFunction, //
                        @Cached("create(getIndirectCallTarget(getContext(), cachedFunction))") DirectCallNode callNode) {
            return callNode.call(frame, arguments);
        }

        @Specialization(replaces = "doDirect")
        protected Object doIndirect(VirtualFrame frame, LLVMFunctionDescriptor function, Object[] arguments, //
                        @Cached("create()") IndirectCallNode callNode) {
            LLVMPerformance.warn(this, "Exceeded inline cache limit");
            return callNode.call(frame, getIndirectCallTarget(getContext(), function), arguments);
        }

        public LLVMContext getContext() {
            return context;
        }

        public LLVMExpressionNode[] getNodes() {
            return nodes;
        }

    }

    @NodeChild(value = "fromNode", type = LLVMExpressionNode.class)
    public abstract static class LLVM80BitArgConvertNode extends LLVMExpressionNode {

        @Specialization
        public byte[] executeByteArray(LLVM80BitFloat from) {
            return from.getBytes();
        }
    }

}
