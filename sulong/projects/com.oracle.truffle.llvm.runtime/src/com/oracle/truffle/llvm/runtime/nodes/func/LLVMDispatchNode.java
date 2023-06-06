/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates.
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
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateAOT;
import com.oracle.truffle.api.dsl.Idempotent;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.llvm.runtime.CommonNodeFactory;
import com.oracle.truffle.llvm.runtime.ContextExtension;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMFunction;
import com.oracle.truffle.llvm.runtime.LLVMFunctionCode;
import com.oracle.truffle.llvm.runtime.LLVMFunctionCode.ResolveFunctionNode;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.NativeContextExtension;
import com.oracle.truffle.llvm.runtime.NativeContextExtension.UnsupportedNativeTypeException;
import com.oracle.truffle.llvm.runtime.except.LLVMNativePointerException;
import com.oracle.truffle.llvm.runtime.except.LLVMPolyglotException;
import com.oracle.truffle.llvm.runtime.interop.LLVMDataEscapeNode;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM;
import com.oracle.truffle.llvm.runtime.interop.nfi.LLVMNativeConvertNode;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMAsForeignLibrary;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.nodes.func.LLVMDispatchNodeGen.LLVMLookupDispatchForeignNodeGen;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.types.FunctionType;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.VoidType;
import com.oracle.truffle.llvm.spi.NativeTypeLibrary;
import com.oracle.truffle.nfi.api.NativePointerLibrary;
import com.oracle.truffle.nfi.api.SignatureLibrary;

public abstract class LLVMDispatchNode extends LLVMNode {

    protected static final int INLINE_CACHE_SIZE = 5;

    protected final FunctionType type;
    @CompilationFinal LLVMFunction aotFixedIntrinsicFunction;

    @CompilationFinal protected Source signatureSource;
    @CompilationFinal private ContextExtension.Key<NativeContextExtension> nativeCtxExtKey;

    @Child private AOTInitHelper aotInitHelper;
    @CompilationFinal private boolean aot;

    protected LLVMDispatchNode(FunctionType type, LLVMFunction llvmFunction) {
        this.type = type;

        LLVMContext context = LLVMLanguage.getContext();

        if (context != null && context.isAOTCacheStore()) {
            // We need to pre-initialize an intrinsic function by generating its call target. It
            // cannot be done in the constructor code as it is too early for
            // llvmFunction.getFixedCode() to return the intrinsic.
            // Therefore, the pre-initialization must be postponed to the AOT preparation stage
            // using the AOTInitHelper.
            aotInitHelper = new AOTInitHelper((language, root) -> {
                if (llvmFunction != null) {
                    if (llvmFunction.getFixedCode() != null && llvmFunction.getFixedCode().isIntrinsicFunctionSlowPath()) {
                        LLVMDispatchNode.this.aotFixedIntrinsicFunction = llvmFunction;
                        llvmFunction.getFixedCode().getIntrinsicSlowPath().cachedCallTarget(type);
                    }
                }
                aot = true;
                // Throw the helper AOT init node away as it is used during the AOT preparation
                // stage only
                aotInitHelper = null;
            });

            // Early parsing of the function's signature for the sake of the AOT preparation
            try {
                nativeCtxExtKey = context.getLanguage().lookupContextExtension(NativeContextExtension.class);
                /*
                 * Here, we are interested in native functions only and so it assumed that native
                 * functions have always at least one argument, the stack argument. Of course, it
                 * actually parses signatures of other functions, but the result will not be used.
                 */
                if (nativeCtxExtKey != null && type.getNumberOfArguments() > 0) {
                    NativeContextExtension nativeContextExtension = nativeCtxExtKey.get(context);
                    signatureSource = nativeContextExtension.getNativeSignatureSourceSkipStackArg(type);
                }
            } catch (UnsupportedNativeTypeException e) {
                // ignore it
                throw new RuntimeException(e);
            }
        } else {
            aotInitHelper = new AOTInitHelper((language, root) -> {
                aot = true;
                aotInitHelper = null;
            });
        }
    }

    @Override
    public String toString() {
        return getShortString("type", "signature");
    }

    @Idempotent
    boolean haveNativeCtxExt() {
        CompilerAsserts.neverPartOfCompilation();
        return getLanguage().lookupContextExtension(NativeContextExtension.class) != null;
    }

    NativeContextExtension getNativeCtxExt() {
        if (nativeCtxExtKey == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            nativeCtxExtKey = getLanguage().lookupContextExtension(NativeContextExtension.class);
        }
        return nativeCtxExtKey.get(getContext());
    }

    private Source getSignatureSource() {
        if (signatureSource == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            try {
                this.signatureSource = getNativeCtxExt().getNativeSignatureSourceSkipStackArg(type);
            } catch (UnsupportedNativeTypeException ex) {
                throw CompilerDirectives.shouldNotReachHere(ex);
            }
        }
        return signatureSource;
    }

    /**
     * {@code function} is expected to be either {@link LLVMFunctionDescriptor},
     * {@link LLVMNativePointer} or a foreign object, and it needs to be resolved using
     * {@link LLVMLookupDispatchTargetNode}.
     */
    public abstract Object executeDispatch(Object function, Object[] arguments);

    /*
     * Function is defined in the user program (available as LLVM IR) or the function is an
     * intrinsic.
     */

    protected DirectCallNode createCallNode(LLVMFunctionCode code) {
        if (code.isLLVMIRFunction()) {
            return DirectCallNode.create(code.getLLVMIRFunctionSlowPath());
        } else if (code.isIntrinsicFunctionSlowPath()) {
            return DirectCallNode.create(code.getIntrinsicSlowPath().cachedCallTarget(type));
        } else {
            return null;
        }
    }

    @Specialization(limit = "INLINE_CACHE_SIZE", guards = {"code == cachedFunctionCode"})
    protected static Object doDirectCodeFast(@SuppressWarnings("unused") LLVMFunctionCode code, Object[] arguments,
                    @Cached("code") @SuppressWarnings("unused") LLVMFunctionCode cachedFunctionCode,
                    @Cached("createCallNode(cachedFunctionCode)") DirectCallNode callNode) {
        assert callNode != null : "inconsistent behavior of LLVMLookupDispatchTargetSymbolNode";
        return callNode.call(arguments);
    }

    @Specialization(replaces = "doDirectCodeFast", guards = "code.isLLVMIRFunction()")
    protected static Object doIndirectCode(LLVMFunctionCode code, Object[] arguments,
                    @Cached ResolveFunctionNode resolve,
                    @Cached("create()") IndirectCallNode callNode) {
        return callNode.call(code.getLLVMIRFunction(resolve), arguments);
    }

    @Specialization(limit = "INLINE_CACHE_SIZE", replaces = "doDirectCodeFast", guards = {"descriptor == cachedDescriptor", "callNode != null", "isSingleContext($node)"})
    protected static Object doDirectFunction(@SuppressWarnings("unused") LLVMFunctionDescriptor descriptor, Object[] arguments,
                    @Cached("descriptor") @SuppressWarnings("unused") LLVMFunctionDescriptor cachedDescriptor,
                    @Cached("cachedDescriptor.getFunctionCode()") @SuppressWarnings("unused") LLVMFunctionCode cachedFunctionCode,
                    @Cached("createCallNode(cachedFunctionCode)") DirectCallNode callNode) {
        return callNode.call(arguments);
    }

    @Specialization(limit = "INLINE_CACHE_SIZE", replaces = "doDirectFunction", guards = {"descriptor.getFunctionCode() == cachedFunctionCode", "callNode != null"})
    protected static Object doDirectCode(@SuppressWarnings("unused") LLVMFunctionDescriptor descriptor, Object[] arguments,
                    @Cached("descriptor.getFunctionCode()") @SuppressWarnings("unused") LLVMFunctionCode cachedFunctionCode,
                    @Cached("createCallNode(cachedFunctionCode)") DirectCallNode callNode) {
        return callNode.call(arguments);
    }

    @Specialization(replaces = {"doDirectCodeFast", "doDirectCode"}, guards = "descriptor.getFunctionCode().isLLVMIRFunction()")
    protected static Object doIndirect(LLVMFunctionDescriptor descriptor, Object[] arguments,
                    @Cached ResolveFunctionNode resolve,
                    @Cached("create()") IndirectCallNode callNode) {
        return callNode.call(descriptor.getFunctionCode().getLLVMIRFunction(resolve), arguments);
    }

    @Specialization(replaces = {"doDirectCodeFast", "doDirectCode"}, guards = "descriptor.getFunctionCode().isIntrinsicFunction(resolve)")
    protected Object doIndirectIntrinsic(LLVMFunctionDescriptor descriptor, Object[] arguments,
                    @Cached ResolveFunctionNode resolve,
                    @Cached("create()") IndirectCallNode callNode) {
        RootCallTarget intrinsicCallTarget;
        if (aotFixedIntrinsicFunction != null && aotFixedIntrinsicFunction.getFixedCodeAssumption().isValid()) {
            // This branch cannot be factored out to another specialization guarded by
            // aotFixedIntrinsicFunction.getFixedCodeAssumption() since aotFixedIntrinsicFunction
            // is not yet initialized (by this.aotInitHelper) upon the execution of prepareForAOT
            // (see AOTInitHelper). As aotFixedIntrinsicFunction is still null at that point,
            // the respective specialization would not be included in the list of AOT
            // specialization.
            intrinsicCallTarget = aotFixedIntrinsicFunction.getFixedCode().getIntrinsic(resolve).cachedCallTarget(type);
        } else {
            LLVMFunctionCode.Intrinsic intrinsic = descriptor.getFunctionCode().getIntrinsic(resolve);
            // In the AOT mode, the call target must be retrieved via the slow path as it may (is?)
            // not
            // cached yet and caching it would generate a deopt. Most of the code parts in
            // cachedCallTarget are beyond
            // the TB anyway.
            intrinsicCallTarget = aot ? intrinsic.cachedCallTargetSlowPath(type) : intrinsic.cachedCallTarget(type);
        }
        return callNode.call(intrinsicCallTarget, arguments);
    }

    /*
     * Function is not defined in the user program (not available as LLVM IR). No intrinsic
     * available. We do a native call.
     */

    @GenerateAOT.Exclude
    @Specialization(limit = "INLINE_CACHE_SIZE", guards = {"descriptor == cachedDescriptor", "cachedFunctionCode.isNativeFunctionSlowPath()",
                    "haveNativeCtxExt()", "isSingleContext($node)"})
    protected Object doCachedNativeFunction(@SuppressWarnings("unused") LLVMFunctionDescriptor descriptor,
                    Object[] arguments,
                    @Cached("descriptor") LLVMFunctionDescriptor cachedDescriptor,
                    @Cached("cachedDescriptor.getFunctionCode()") @SuppressWarnings("unused") LLVMFunctionCode cachedFunctionCode,
                    @Cached("createToNativeNodes()") LLVMNativeConvertNode[] toNative,
                    @Cached("createFromNativeNode()") LLVMNativeConvertNode fromNative,
                    @Cached("bindSymbol(cachedFunctionCode)") Object cachedBoundFunction,
                    @CachedLibrary("cachedBoundFunction") InteropLibrary nativeCall,
                    @Cached("nativeCallStatisticsEnabled()") boolean statistics) {
        Object[] nativeArgs = prepareNativeArguments(arguments, toNative);
        Object returnValue;
        returnValue = LLVMNativeCallUtils.callNativeFunction(statistics, nativeCall, cachedBoundFunction, nativeArgs, cachedDescriptor);
        return fromNative.executeConvert(returnValue);
    }

    @GenerateAOT.Exclude
    @Specialization(replaces = "doCachedNativeFunction", guards = {"descriptor.getFunctionCode() == cachedFunctionCode",
                    "cachedFunctionCode.isNativeFunctionSlowPath()", "isSingleContext($node)"})
    protected Object doCachedNativeCode(@SuppressWarnings("unused") LLVMFunctionDescriptor descriptor,
                    Object[] arguments,
                    @Cached("descriptor.getFunctionCode()") @SuppressWarnings("unused") LLVMFunctionCode cachedFunctionCode,
                    @Cached("createToNativeNodes()") LLVMNativeConvertNode[] toNative,
                    @Cached("createFromNativeNode()") LLVMNativeConvertNode fromNative,
                    @Cached("bindSymbol(cachedFunctionCode)") Object cachedBoundFunction,
                    @CachedLibrary("cachedBoundFunction") InteropLibrary nativeCall,
                    @Cached("nativeCallStatisticsEnabled()") boolean statistics) {

        Object[] nativeArgs = prepareNativeArguments(arguments, toNative);
        Object returnValue;
        returnValue = LLVMNativeCallUtils.callNativeFunction(statistics, nativeCall, cachedBoundFunction, nativeArgs, descriptor);
        return fromNative.executeConvert(returnValue);
    }

    @CompilerDirectives.TruffleBoundary
    private static Object doBind(NativeContextExtension ctxExt, LLVMFunctionCode functionCode, Source signatureSource) {
        return ctxExt.bindSignature(functionCode, signatureSource);
    }

    protected final Object bindSymbol(LLVMFunctionCode functionCode) {
        assert functionCode.getNativeFunctionSlowPath() != null : functionCode.getLLVMFunction().getName();
        return doBind(getNativeCtxExt(), functionCode, getSignatureSource());
    }

    @Specialization(replaces = "doCachedNativeCode", guards = {"descriptor.getFunctionCode().isNativeFunction(resolve)", "haveNativeCtxExt()"})
    @GenerateAOT.Exclude
    protected Object doNative(LLVMFunctionDescriptor descriptor, Object[] arguments,
                    @Cached("createToNativeNodes()") LLVMNativeConvertNode[] toNative,
                    @Cached("createFromNativeNode()") LLVMNativeConvertNode fromNative,
                    @CachedLibrary(limit = "3") InteropLibrary interop,
                    @Cached @SuppressWarnings("unused") ResolveFunctionNode resolve,
                    @Cached("nativeCallStatisticsEnabled()") boolean statistics) {

        Object[] nativeArgs = prepareNativeArguments(arguments, toNative);
        Object boundSymbol = bindSymbol(descriptor.getFunctionCode());
        Object returnValue;
        returnValue = LLVMNativeCallUtils.callNativeFunction(statistics, interop, boundSymbol, nativeArgs, descriptor);
        return fromNative.executeConvert(returnValue);
    }

    boolean isPointerReturnType() {
        return type.getReturnType() instanceof PointerType;
    }

    @Specialization(guards = {"signatureSource != null", "descriptor.getFunctionCode().isNativeFunction(resolve)"})
    protected Object doNativeAOT(LLVMFunctionDescriptor descriptor, Object[] arguments,
                    @Cached("createToNativeNodes()") LLVMNativeConvertNode[] toNative,
                    @Cached("createFromNativeNode()") LLVMNativeConvertNode fromNative,
                    @Cached("isPointerReturnType()") boolean isPointerReturnType,
                    @CachedLibrary(limit = "1") SignatureLibrary signatureLibrary,
                    @CachedLibrary(limit = "1") NativePointerLibrary nativePointerLibrary,
                    @Cached @SuppressWarnings("unused") ResolveFunctionNode resolve) {
        try {
            Object signature = getNativeCtxExt().createSignature(signatureSource);
            Object nativeFunction = descriptor.getFunctionCode().getNativeFunction(resolve);
            Object[] nativeArgs = prepareNativeArguments(arguments, toNative);
            Object returnValue = signatureLibrary.call(signature, nativeFunction, nativeArgs);
            if (isPointerReturnType && nativePointerLibrary.isPointer(returnValue)) {
                // By using the raw long value we can avoid using interop in the subsequent
                // conversion
                returnValue = nativePointerLibrary.asPointer(returnValue);
            }
            return fromNative.executeConvert(returnValue);
        } catch (ArityException | UnsupportedTypeException | UnsupportedMessageException e) {
            throw CompilerDirectives.shouldNotReachHere(e);
        }
    }

    @ExplodeLoop
    private static Object[] prepareNativeArguments(Object[] arguments, LLVMNativeConvertNode[] toNative) {
        Object[] nativeArgs = new Object[arguments.length - LLVMCallNode.USER_ARGUMENT_OFFSET];
        for (int i = LLVMCallNode.USER_ARGUMENT_OFFSET; i < arguments.length; i++) {
            nativeArgs[i - LLVMCallNode.USER_ARGUMENT_OFFSET] = toNative[i - LLVMCallNode.USER_ARGUMENT_OFFSET].executeConvert(arguments[i]);
        }
        return nativeArgs;
    }

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

    @Specialization(guards = {"foreigns.isForeign(receiver)", "interopLibrary.isExecutable(foreignFunction)"})
    @GenerateAOT.Exclude
    protected Object doForeignExecutable(Object receiver, Object[] arguments,
                    @SuppressWarnings("unused") @CachedLibrary(limit = "3") LLVMAsForeignLibrary foreigns,
                    @CachedLibrary(limit = "3") NativeTypeLibrary natives,
                    @SuppressWarnings("unused") @CachedLibrary(limit = "3") InteropLibrary interopLibrary,
                    @Bind("foreigns.asForeign(receiver)") Object foreignFunction,
                    @Cached("create(type)") LLVMLookupDispatchForeignNode lookupDispatchForeignNode) {
        return lookupDispatchForeignNode.execute(foreignFunction, natives.getNativeType(receiver), arguments);
    }

    @Specialization(guards = {"foreigns.isForeign(receiver)", "!interopLibrary.isExecutable(foreignFunction)",
                    "interopLibrary.isPointer(foreignFunction)"})
    @GenerateAOT.Exclude
    protected Object doForeignPointer(@SuppressWarnings("unused") Object receiver, Object[] arguments,
                    @SuppressWarnings("unused") @CachedLibrary(limit = "3") LLVMAsForeignLibrary foreigns,
                    @SuppressWarnings("unused") @CachedLibrary(limit = "3") InteropLibrary interopLibrary,
                    @Bind("foreigns.asForeign(receiver)") Object foreignFunction,
                    @Cached("createCachedNativeDispatch()") LLVMNativeDispatchNode dispatchNode,
                    @Cached BranchProfile exception) {
        try {
            return dispatchNode.executeDispatch(LLVMNativePointer.create(interopLibrary.asPointer(foreignFunction)), arguments);
        } catch (UnsupportedMessageException | IllegalStateException e) {
            exception.enter();
            throw new LLVMNativePointerException(dispatchNode, "Invalid native function pointer", e);
        }
    }

    @Specialization(guards = "haveNativeCtxExt()")
    @GenerateAOT.Exclude
    protected static Object doNativeFunction(LLVMNativePointer pointer, Object[] arguments,
                    @Cached("createCachedNativeDispatch()") LLVMNativeDispatchNode dispatchNode,
                    @Cached BranchProfile exception) {
        try {
            return dispatchNode.executeDispatch(pointer, arguments);
        } catch (IllegalStateException e) {
            exception.enter();
            throw new LLVMNativePointerException(dispatchNode, "Invalid native function pointer", e);
        }
    }

    @Specialization(guards = "!haveNativeCtxExt()")
    @GenerateAOT.Exclude
    protected Object doInvalidNativeFunction(@SuppressWarnings("unused") LLVMNativePointer pointer, @SuppressWarnings("unused") Object[] arguments) {
        throw new LLVMNativePointerException(this, "Invalid native function pointer", null);
    }

    protected LLVMNativeDispatchNode createCachedNativeDispatch() {
        return LLVMNativeDispatchNodeGen.create(type, getSignatureSource());
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
        @GenerateAOT.Exclude
        protected Object doCachedType(Object function, @SuppressWarnings("unused") LLVMInteropType.Function functionType, Object[] arguments,
                        @Cached("functionType") LLVMInteropType.Function cachedType,
                        @CachedLibrary("function") InteropLibrary crossLanguageCall,
                        @Cached("createLLVMDataEscapeNodes()") LLVMDataEscapeNode[] dataEscapeNodes,
                        @Cached("createToLLVMNode()") ForeignToLLVM toLLVMNode,
                        @Cached BranchProfile exception) {
            return doGeneric(function, cachedType, arguments, crossLanguageCall, dataEscapeNodes, toLLVMNode, exception);
        }

        @Specialization(replaces = "doCachedType", limit = "0")
        @GenerateAOT.Exclude
        protected Object doGeneric(Object function, LLVMInteropType.Function functionType, Object[] arguments,
                        @CachedLibrary("function") InteropLibrary crossLanguageCall,
                        @Cached("createLLVMDataEscapeNodes()") LLVMDataEscapeNode[] dataEscapeNodes,
                        @Cached("createToLLVMNode()") ForeignToLLVM toLLVMNode,
                        @Cached BranchProfile exception) {
            try {
                Object[] args = getForeignArguments(dataEscapeNodes, arguments, functionType, exception);
                Object ret;

                ret = crossLanguageCall.execute(function, args);
                if (!isVoidReturn && functionType != null) {
                    LLVMInteropType retType = functionType.getReturnType();
                    if (retType instanceof LLVMInteropType.Value) {
                        return toLLVMNode.executeWithType(ret, ((LLVMInteropType.Value) retType).baseType);
                    } else {
                        exception.enter();
                        throw new LLVMPolyglotException(this, "Cannot call polyglot function with structured return type.");
                    }
                } else {
                    return toLLVMNode.executeWithTarget(ret);
                }
            } catch (InteropException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }

        boolean isNotFunctionType(Object functionType) {
            return !(functionType instanceof LLVMInteropType.Function);
        }

        @Specialization(guards = "isNotFunctionType(functionType)", limit = "5")
        @GenerateAOT.Exclude
        protected Object doUnknownType(Object function, @SuppressWarnings("unused") Object functionType, Object[] arguments,
                        @CachedLibrary("function") InteropLibrary crossLanguageCall,
                        @Cached("createLLVMDataEscapeNodes()") LLVMDataEscapeNode[] dataEscapeNodes,
                        @Cached("createToLLVMNode()") ForeignToLLVM toLLVMNode,
                        @Cached BranchProfile exception) {
            return doGeneric(function, null, arguments, crossLanguageCall, dataEscapeNodes, toLLVMNode, exception);
        }

        @ExplodeLoop
        private Object[] getForeignArguments(LLVMDataEscapeNode[] dataEscapeNodes, Object[] arguments, LLVMInteropType.Function functionType, BranchProfile exception) {
            assert arguments.length == argumentCount;
            Object[] args = new Object[dataEscapeNodes.length];
            int i = 0;
            if (functionType != null) {
                assert arguments.length == functionType.getNumberOfParameters() + LLVMCallNode.USER_ARGUMENT_OFFSET;
                for (; i < functionType.getNumberOfParameters(); i++) {
                    LLVMInteropType argType = functionType.getParameter(i);
                    if (argType instanceof LLVMInteropType.Value) {
                        LLVMInteropType.Structured baseType = ((LLVMInteropType.Value) argType).baseType;
                        args[i] = dataEscapeNodes[i].executeWithType(arguments[i + LLVMCallNode.USER_ARGUMENT_OFFSET], baseType);
                    } else {
                        exception.enter();
                        throw new LLVMPolyglotException(this, "Cannot call polyglot function with structured argument type.");
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
