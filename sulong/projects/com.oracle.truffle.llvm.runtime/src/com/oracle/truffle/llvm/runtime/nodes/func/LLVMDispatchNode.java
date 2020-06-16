/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.nodes.func;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.llvm.runtime.CommonNodeFactory;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMFunctionCode;
import com.oracle.truffle.llvm.runtime.LLVMFunctionCode.Intrinsic;
import com.oracle.truffle.llvm.runtime.LLVMFunctionCode.ResolveFunctionNode;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.NFIContextExtension;
import com.oracle.truffle.llvm.runtime.NFIContextExtension.UnsupportedNativeTypeException;
import com.oracle.truffle.llvm.runtime.except.LLVMNativePointerException;
import com.oracle.truffle.llvm.runtime.except.LLVMPolyglotException;
import com.oracle.truffle.llvm.runtime.interop.LLVMDataEscapeNode;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM;
import com.oracle.truffle.llvm.runtime.interop.nfi.LLVMNativeConvertNode;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMAsForeignLibrary;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack.StackPointer;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.nodes.func.LLVMDispatchNodeGen.LLVMLookupDispatchForeignNodeGen;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.types.FunctionType;
import com.oracle.truffle.llvm.runtime.types.VoidType;
import com.oracle.truffle.llvm.spi.NativeTypeLibrary;

public abstract class LLVMDispatchNode extends LLVMNode {

    protected static final int INLINE_CACHE_SIZE = 5;

    protected final FunctionType type;
    @CompilationFinal private String signature;

    protected LLVMDispatchNode(FunctionType type) {
        this.type = type;
    }

    @Override
    public String toString() {
        return getShortString("type", "signature");
    }

    private String getSignature() {
        if (signature == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            try {
                NFIContextExtension nfiContextExtension = lookupContextReference(LLVMLanguage.class).get().getContextExtension(NFIContextExtension.class);
                this.signature = nfiContextExtension.getNativeSignature(type, LLVMCallNode.USER_ARGUMENT_OFFSET);
            } catch (UnsupportedNativeTypeException ex) {
                CompilerDirectives.transferToInterpreter();
                throw new AssertionError(ex);
            }
        }
        return signature;
    }

    /**
     * {@code function} is expected to be either {@link LLVMFunctionDescriptor},
     * {@link LLVMNativePointer} or a foreign object, and it needs to be resolved using
     * {@link LLVMLookupDispatchTargetNode}.
     */
    public abstract Object executeDispatch(Object function, Object[] arguments);

    /*
     * Function is defined in the user program (available as LLVM IR)
     */

    @Specialization(limit = "INLINE_CACHE_SIZE", guards = {"descriptor == cachedDescriptor", "functionCode.isLLVMIRFunction()"})
    protected static Object doDirect(@SuppressWarnings("unused") LLVMFunctionDescriptor descriptor, Object[] arguments,
                    @Cached("descriptor") @SuppressWarnings("unused") LLVMFunctionDescriptor cachedDescriptor,
                    @Cached("cachedDescriptor.getFunctionCode()") @SuppressWarnings("unused") LLVMFunctionCode functionCode,
                    @Cached("create(functionCode.getLLVMIRFunctionSlowPath())") DirectCallNode callNode) {
        try (StackPointer sp = ((StackPointer) arguments[0]).newFrame()) {
            return callNode.call(arguments);
        }
    }

    @Specialization(replaces = "doDirect", guards = "descriptor.getFunctionCode().isLLVMIRFunction()")
    protected static Object doIndirect(LLVMFunctionDescriptor descriptor, Object[] arguments,
                    @Cached ResolveFunctionNode resolve,
                    @Cached("create()") IndirectCallNode callNode) {
        try (StackPointer sp = ((StackPointer) arguments[0]).newFrame()) {
            return callNode.call(descriptor.getFunctionCode().getLLVMIRFunction(resolve), arguments);
        }
    }

    /*
     * Function is not defined in the user program (not available as LLVM IR). This would normally
     * result in a native call BUT there is an intrinsification available
     */

    protected DirectCallNode getIntrinsificationCallNode(Intrinsic intrinsic) {
        RootCallTarget target = intrinsic.cachedCallTarget(type);
        DirectCallNode directCallNode = DirectCallNode.create(target);
        return directCallNode;
    }

    @Specialization(limit = "INLINE_CACHE_SIZE", guards = {"descriptor == cachedDescriptor", "functionCode.isIntrinsicFunctionSlowPath()"})
    protected Object doDirectIntrinsic(@SuppressWarnings("unused") LLVMFunctionDescriptor descriptor, Object[] arguments,
                    @Cached("descriptor") @SuppressWarnings("unused") LLVMFunctionDescriptor cachedDescriptor,
                    @Cached("cachedDescriptor.getFunctionCode()") @SuppressWarnings("unused") LLVMFunctionCode functionCode,
                    @Cached("getIntrinsificationCallNode(functionCode.getIntrinsicSlowPath())") DirectCallNode callNode) {
        try (StackPointer sp = ((StackPointer) arguments[0]).newFrame()) {
            return callNode.call(arguments);
        }
    }

    @Specialization(replaces = "doDirectIntrinsic", guards = "descriptor.getFunctionCode().isIntrinsicFunction(resolve)")
    protected Object doIndirectIntrinsic(LLVMFunctionDescriptor descriptor, Object[] arguments,
                    @Cached ResolveFunctionNode resolve,
                    @Cached("create()") IndirectCallNode callNode) {
        try (StackPointer sp = ((StackPointer) arguments[0]).newFrame()) {
            return callNode.call(descriptor.getFunctionCode().getIntrinsic(resolve).cachedCallTarget(type), arguments);
        }
    }

    /*
     * Function is not defined in the user program (not available as LLVM IR). No intrinsic
     * available. We do a native call.
     */

    @Specialization(limit = "10", guards = {"descriptor == cachedDescriptor", "functionCode.isNativeFunctionSlowPath()"})
    protected Object doCachedNative(@SuppressWarnings("unused") LLVMFunctionDescriptor descriptor,
                    Object[] arguments,
                    @Cached("descriptor") LLVMFunctionDescriptor cachedDescriptor,
                    @Cached("cachedDescriptor.getFunctionCode()") @SuppressWarnings("unused") LLVMFunctionCode functionCode,
                    @Cached("createToNativeNodes()") LLVMNativeConvertNode[] toNative,
                    @Cached("createFromNativeNode()") LLVMNativeConvertNode fromNative,
                    @Cached("bindSymbol(functionCode)") Object cachedBoundFunction,
                    @CachedLibrary("cachedBoundFunction") InteropLibrary nativeCall,
                    @CachedContext(LLVMLanguage.class) ContextReference<LLVMContext> context,
                    @Cached("nativeCallStatisticsEnabled(context)") boolean statistics) {

        Object[] nativeArgs = prepareNativeArguments(arguments, toNative);
        Object returnValue;
        try (StackPointer save = ((StackPointer) arguments[0]).newFrame()) {
            returnValue = LLVMNativeCallUtils.callNativeFunction(statistics, context, nativeCall, cachedBoundFunction, nativeArgs, cachedDescriptor);
        }
        return fromNative.executeConvert(returnValue);
    }

    protected Object bindSymbol(LLVMFunctionCode functionCode) {
        CompilerAsserts.neverPartOfCompilation();
        assert functionCode.getNativeFunctionSlowPath() != null : functionCode.getLLVMFunction().getName();
        return LLVMNativeCallUtils.bindNativeSymbol(InteropLibrary.getFactory().getUncached(), functionCode.getNativeFunctionSlowPath(), getSignature());
    }

    @Specialization(replaces = "doCachedNative", guards = "descriptor.getFunctionCode().isNativeFunction(resolve)")
    protected Object doNative(LLVMFunctionDescriptor descriptor, Object[] arguments,
                    @Cached("createToNativeNodes()") LLVMNativeConvertNode[] toNative,
                    @Cached("createFromNativeNode()") LLVMNativeConvertNode fromNative,
                    @CachedLibrary(limit = "3") InteropLibrary nativeCall,
                    @CachedLibrary(limit = "3") InteropLibrary bind,
                    @CachedContext(LLVMLanguage.class) ContextReference<LLVMContext> context,
                    @Cached ResolveFunctionNode resolve,
                    @Cached("nativeCallStatisticsEnabled(context)") boolean statistics) {

        Object[] nativeArgs = prepareNativeArguments(arguments, toNative);
        Object boundSymbol = LLVMNativeCallUtils.bindNativeSymbol(bind, descriptor.getFunctionCode().getNativeFunction(resolve), getSignature());
        Object returnValue;
        try (StackPointer save = ((StackPointer) arguments[0]).newFrame()) {
            returnValue = LLVMNativeCallUtils.callNativeFunction(statistics, context, nativeCall, boundSymbol, nativeArgs, descriptor);
        }
        return fromNative.executeConvert(returnValue);
    }

    @ExplodeLoop
    private static Object[] prepareNativeArguments(Object[] arguments, LLVMNativeConvertNode[] toNative) {
        Object[] nativeArgs = new Object[arguments.length - LLVMCallNode.USER_ARGUMENT_OFFSET];
        for (int i = LLVMCallNode.USER_ARGUMENT_OFFSET; i < arguments.length; i++) {
            nativeArgs[i - LLVMCallNode.USER_ARGUMENT_OFFSET] = toNative[i - LLVMCallNode.USER_ARGUMENT_OFFSET].executeConvert(arguments[i]);
        }
        return nativeArgs;
    }

    @ExplodeLoop
    protected LLVMNativeConvertNode[] createToNativeNodes() {
        LLVMNativeConvertNode[] ret = new LLVMNativeConvertNode[type.getNumberOfArguments() - LLVMCallNode.USER_ARGUMENT_OFFSET];
        for (int i = LLVMCallNode.USER_ARGUMENT_OFFSET; i < type.getNumberOfArguments(); i++) {
            ret[i - LLVMCallNode.USER_ARGUMENT_OFFSET] = LLVMNativeConvertNode.createToNative(type.getArgumentType(i));
        }
        return ret;
    }

    protected LLVMNativeConvertNode createFromNativeNode() {
        CompilerAsserts.neverPartOfCompilation();
        return LLVMNativeConvertNode.createFromNative(type.getReturnType());
    }

    @Specialization(guards = {"foreigns.isForeign(receiver)"})
    protected Object doForeign(Object receiver, Object[] arguments,
                    @CachedLibrary(limit = "3") LLVMAsForeignLibrary foreigns,
                    @CachedLibrary(limit = "3") NativeTypeLibrary natives,
                    @Cached("create(type)") LLVMLookupDispatchForeignNode lookupDispatchForeignNode) {
        return lookupDispatchForeignNode.execute(foreigns.asForeign(receiver),
                        natives.getNativeType(receiver), arguments);
    }

    @Specialization
    protected static Object doNativeFunction(LLVMNativePointer pointer, Object[] arguments,
                    @Cached("createCachedNativeDispatch()") LLVMNativeDispatchNode dispatchNode) {
        try {
            return dispatchNode.executeDispatch(pointer, arguments);
        } catch (IllegalStateException e) {
            CompilerDirectives.transferToInterpreter();
            throw new LLVMNativePointerException(dispatchNode, "Invalid native function pointer", e);
        }
    }

    protected LLVMDispatchNode createCachedDispatch() {
        return LLVMDispatchNodeGen.create(type);
    }

    protected LLVMNativeDispatchNode createCachedNativeDispatch() {
        return LLVMNativeDispatchNodeGen.create(type);
    }

    abstract static class LLVMLookupDispatchForeignNode extends LLVMNode {

        private final boolean isVoidReturn;
        private final int argumentCount;
        private final FunctionType type;

        LLVMLookupDispatchForeignNode(FunctionType type) {
            this.type = type;
            this.isVoidReturn = type.getReturnType() instanceof VoidType;
            this.argumentCount = type.getNumberOfArguments();
        }

        abstract Object execute(Object function, Object interopType, Object[] arguments);

        @Specialization(guards = "functionType == cachedType", limit = "5")
        protected Object doCachedType(TruffleObject function, @SuppressWarnings("unused") LLVMInteropType.Function functionType, Object[] arguments,
                        @Cached("functionType") LLVMInteropType.Function cachedType,
                        @CachedLibrary("function") InteropLibrary crossLanguageCall,
                        @Cached("createLLVMDataEscapeNodes()") LLVMDataEscapeNode[] dataEscapeNodes,
                        @Cached("createToLLVMNode()") ForeignToLLVM toLLVMNode) {
            return doGeneric(function, cachedType, arguments, crossLanguageCall, dataEscapeNodes, toLLVMNode);
        }

        @Specialization(replaces = "doCachedType", limit = "0")
        protected Object doGeneric(TruffleObject function, LLVMInteropType.Function functionType, Object[] arguments,
                        @CachedLibrary("function") InteropLibrary crossLanguageCall,
                        @Cached("createLLVMDataEscapeNodes()") LLVMDataEscapeNode[] dataEscapeNodes,
                        @Cached("createToLLVMNode()") ForeignToLLVM toLLVMNode) {
            try {
                Object[] args = getForeignArguments(dataEscapeNodes, arguments, functionType);
                Object ret;
                try (StackPointer save = ((StackPointer) arguments[0]).newFrame()) {
                    ret = crossLanguageCall.execute(function, args);
                }
                if (!isVoidReturn && functionType != null) {
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

        boolean isNotFunctionType(Object functionType) {
            return !(functionType instanceof LLVMInteropType.Function);
        }

        @Specialization(guards = "isNotFunctionType(functionType)", limit = "5")
        protected Object doUnknownType(TruffleObject function, @SuppressWarnings("unused") Object functionType, Object[] arguments,
                        @CachedLibrary("function") InteropLibrary crossLanguageCall,
                        @Cached("createLLVMDataEscapeNodes()") LLVMDataEscapeNode[] dataEscapeNodes,
                        @Cached("createToLLVMNode()") ForeignToLLVM toLLVMNode) {
            return doGeneric(function, null, arguments, crossLanguageCall, dataEscapeNodes, toLLVMNode);
        }

        @ExplodeLoop
        private Object[] getForeignArguments(LLVMDataEscapeNode[] dataEscapeNodes, Object[] arguments, LLVMInteropType.Function functionType) {
            assert arguments.length == argumentCount;
            Object[] args = new Object[dataEscapeNodes.length];
            int i = 0;
            if (functionType != null) {
                assert arguments.length == functionType.getNumberOfParameters() + LLVMCallNode.USER_ARGUMENT_OFFSET;
                for (; i < functionType.getNumberOfParameters(); i++) {
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

            // handle remaining arguments (varargs or functionType == null)
            for (; i < args.length; i++) {
                args[i] = dataEscapeNodes[i].executeWithTarget(arguments[i + LLVMCallNode.USER_ARGUMENT_OFFSET]);
            }
            return args;
        }

        protected ForeignToLLVM createToLLVMNode() {
            return CommonNodeFactory.createForeignToLLVM(ForeignToLLVM.convert(type.getReturnType()));
        }

        protected LLVMDataEscapeNode[] createLLVMDataEscapeNodes() {
            CompilerAsserts.neverPartOfCompilation();
            LLVMDataEscapeNode[] args = new LLVMDataEscapeNode[type.getNumberOfArguments() - LLVMCallNode.USER_ARGUMENT_OFFSET];
            for (int i = 0; i < args.length; i++) {
                args[i] = LLVMDataEscapeNode.create(type.getArgumentType(i + LLVMCallNode.USER_ARGUMENT_OFFSET));
            }
            return args;
        }

        public static LLVMLookupDispatchForeignNode create(FunctionType type) {
            return LLVMLookupDispatchForeignNodeGen.create(type);
        }
    }
}
