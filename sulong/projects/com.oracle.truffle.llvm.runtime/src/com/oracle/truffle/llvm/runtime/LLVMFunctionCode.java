/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime;

import java.util.HashMap;
import java.util.Map;

import org.graalvm.collections.EconomicMap;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Idempotent;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.llvm.runtime.LLVMFunctionCodeFactory.ResolveFunctionNodeGen;
import com.oracle.truffle.llvm.runtime.debug.type.LLVMSourceFunctionType;
import com.oracle.truffle.llvm.runtime.except.LLVMLinkerException;
import com.oracle.truffle.llvm.runtime.interop.LLVMForeignCallNode;
import com.oracle.truffle.llvm.runtime.interop.LLVMForeignConstructorCallNode;
import com.oracle.truffle.llvm.runtime.interop.LLVMForeignFunctionCallNode;
import com.oracle.truffle.llvm.runtime.interop.LLVMForeignIntrinsicCallNode;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType;
import com.oracle.truffle.llvm.runtime.memory.LLVMHandleMemoryBase;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.types.FunctionType;
import com.oracle.truffle.llvm.runtime.types.Type;

/**
 * {@link LLVMFunctionCode} represents the callable function of a {@link LLVMFunction}.
 *
 * A call target is generated when a {@link Function} is resolved.
 *
 */
public final class LLVMFunctionCode {
    private static final long SULONG_FUNCTION_POINTER_TAG = 0xBADE_FACE_0000_0000L;

    static {
        assert LLVMHandleMemoryBase.isCommonHandleMemory(SULONG_FUNCTION_POINTER_TAG);
        assert !LLVMHandleMemoryBase.isDerefHandleMemory(SULONG_FUNCTION_POINTER_TAG);
    }

    @CompilationFinal private Function functionFinal;
    private Function functionDynamic;
    @CompilationFinal private Assumption assumption;
    private final LLVMFunction llvmFunction;

    private volatile CallTarget cachedNativeWrapperFactory;
    private volatile EconomicMap<String, CallTarget> cachedAltBackendNativeWrapperFactory;

    public LLVMFunctionCode(LLVMFunction llvmFunction) {
        this.llvmFunction = llvmFunction;
        this.functionFinal = this.functionDynamic = llvmFunction.getFunction();
        this.assumption = Truffle.getRuntime().createAssumption();
    }

    private static final class TagSulongFunctionPointerNode extends RootNode {

        private final LLVMFunctionCode functionCode;
        private final BranchProfile exceptionBranch;

        TagSulongFunctionPointerNode(LLVMFunctionCode functionCode) {
            super(LLVMLanguage.get(null));
            this.functionCode = functionCode;
            this.exceptionBranch = BranchProfile.create();
        }

        private static long tagSulongFunctionPointer(int id) {
            return id | SULONG_FUNCTION_POINTER_TAG;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            int id = functionCode.getLLVMFunction().getSymbolIndex(exceptionBranch);
            return LLVMNativePointer.create(tagSulongFunctionPointer(id));
        }
    }

    private CallTarget createNativeWrapperFactory(NativeContextExtension nativeExt, String backend) {
        CallTarget ret = null;
        if (nativeExt != null) {
            ret = nativeExt.createNativeWrapperFactory(this, backend);
        }
        if (ret == null) {
            // either no native access, or signature is unsupported
            // fall back to tagged id
            ret = new TagSulongFunctionPointerNode(this).getCallTarget();
        }
        return ret;
    }

    private synchronized void initNativeWrapperFactory(NativeContextExtension nativeExt, String backend) {
        if (backend == null) {
            if (cachedNativeWrapperFactory == null) {
                cachedNativeWrapperFactory = createNativeWrapperFactory(nativeExt, null);
            }
        } else {
            if (cachedAltBackendNativeWrapperFactory == null) {
                cachedAltBackendNativeWrapperFactory = EconomicMap.create();
            }
            if (!cachedAltBackendNativeWrapperFactory.containsKey(backend)) {
                cachedAltBackendNativeWrapperFactory.put(backend, createNativeWrapperFactory(nativeExt, backend));
            }
        }
    }

    CallTarget getNativeWrapperFactory(NativeContextExtension nativeExt) {
        CompilerAsserts.neverPartOfCompilation();
        if (cachedNativeWrapperFactory == null) {
            initNativeWrapperFactory(nativeExt, null);
        }
        assert cachedNativeWrapperFactory != null;
        return cachedNativeWrapperFactory;
    }

    CallTarget getAltBackendNativeWrapperFactory(NativeContextExtension nativeExt, String backend) {
        CompilerAsserts.neverPartOfCompilation();
        if (cachedAltBackendNativeWrapperFactory == null || !cachedAltBackendNativeWrapperFactory.containsKey(backend)) {
            initNativeWrapperFactory(nativeExt, backend);
        }
        assert cachedAltBackendNativeWrapperFactory.containsKey(backend);
        return cachedAltBackendNativeWrapperFactory.get(backend);
    }

    private volatile boolean nativeSignatureInitialized;
    private String nativeSignature;

    @TruffleBoundary
    private synchronized void initNativeSignature() {
        if (!nativeSignatureInitialized) {
            NativeContextExtension nativeContextExtension = LLVMLanguage.getContext().getContextExtensionOrNull(NativeContextExtension.class);
            if (nativeContextExtension == null) {
                nativeSignature = null;
            } else {
                nativeSignature = nativeContextExtension.getNativeSignature(llvmFunction.getType());
            }
            nativeSignatureInitialized = true;
        }
    }

    public String getNativeSignature() {
        if (!nativeSignatureInitialized) {
            initNativeSignature();
        }
        return nativeSignature;
    }

    public static final class Intrinsic {
        private final String intrinsicName;
        private final Map<FunctionType, RootCallTarget> overloadingMap;
        private final LLVMIntrinsicProvider provider;
        private final NodeFactory nodeFactory;

        public Intrinsic(LLVMIntrinsicProvider provider, String name, NodeFactory nodeFactory) {
            this.intrinsicName = name;
            this.overloadingMap = new HashMap<>();
            this.provider = provider;
            this.nodeFactory = nodeFactory;
        }

        public RootCallTarget cachedCallTarget(FunctionType type) {
            if (exists(type)) {
                return get(type);
            } else {
                return generateTarget(type);
            }
        }

        @TruffleBoundary
        public RootCallTarget cachedCallTargetSlowPath(FunctionType type) {
            return cachedCallTarget(type);
        }

        public LLVMExpressionNode createIntrinsicNode(LLVMExpressionNode[] arguments, Type[] argTypes) {
            return provider.generateIntrinsicNode(intrinsicName, arguments, argTypes, nodeFactory);
        }

        @TruffleBoundary
        private boolean exists(FunctionType type) {
            return overloadingMap.containsKey(type);
        }

        @TruffleBoundary
        private RootCallTarget get(FunctionType type) {
            return overloadingMap.get(type);
        }

        private RootCallTarget generateTarget(FunctionType type) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            RootCallTarget newTarget = provider.generateIntrinsicTarget(intrinsicName, type.getArgumentTypes(), nodeFactory);
            assert newTarget != null;
            overloadingMap.put(type, newTarget);
            return newTarget;
        }

    }

    public interface LazyToTruffleConverter {
        RootCallTarget convert();

        /**
         * Get an {@link com.oracle.truffle.llvm.runtime.debug.type.LLVMSourceFunctionType} for the
         * already converted function. Can be null if no debug information is available in the
         * bitcode file.
         *
         * @return the function's source-level type
         */
        LLVMSourceFunctionType getSourceType();
    }

    public abstract static class Function {
        void resolve(@SuppressWarnings("unused") LLVMFunctionCode descriptor) {
            // nothing to do
            CompilerAsserts.neverPartOfCompilation();
        }

        abstract Object createNativeWrapper(LLVMFunctionDescriptor descriptor);

        LLVMSourceFunctionType getSourceType() {
            return null;
        }
    }

    @GenerateUncached
    public abstract static class ResolveFunctionNode extends LLVMNode {

        abstract Function execute(Function function, LLVMFunctionCode descriptor);

        @Specialization
        @TruffleBoundary
        Function doLazyLLVMIRFunction(LazyLLVMIRFunction function, LLVMFunctionCode descriptor) {
            function.resolve(descriptor);
            return descriptor.getFunction();
        }

        @Specialization
        @TruffleBoundary
        Function doUnresolvedFunction(UnresolvedFunction function, LLVMFunctionCode descriptor) {
            function.resolve(descriptor);
            return descriptor.getFunction();
        }

        private static boolean resolveDoesNothing(Function function, LLVMFunctionCode descriptor) {
            function.resolve(descriptor);
            return descriptor.getFunction() == function;
        }

        @Fallback
        Function doOther(Function function, LLVMFunctionCode descriptor) {
            assert resolveDoesNothing(function, descriptor);
            // nothing to do
            return function;
        }
    }

    abstract static class ManagedFunction extends Function {
        @Override
        Object createNativeWrapper(LLVMFunctionDescriptor descriptor) {
            CompilerAsserts.neverPartOfCompilation();
            LLVMContext context = LLVMLanguage.getContext();
            Object wrapper = null;
            LLVMNativePointer pointer = null;
            NativeContextExtension nativeContextExtension = context.getContextExtensionOrNull(NativeContextExtension.class);
            CallTarget nativeWrapperFactory = descriptor.getFunctionCode().getNativeWrapperFactory(nativeContextExtension);
            wrapper = nativeWrapperFactory.call();
            try {
                pointer = LLVMNativePointer.create(InteropLibrary.getFactory().getUncached().asPointer(wrapper));
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }

            context.registerFunctionPointer(pointer, descriptor);
            return wrapper;
        }
    }

    public static final class LazyLLVMIRFunction extends ManagedFunction {
        private final LazyToTruffleConverter converter;

        public LazyLLVMIRFunction(LazyToTruffleConverter converter) {
            this.converter = converter;
        }

        @Override
        void resolve(LLVMFunctionCode descriptor) {
            CompilerAsserts.neverPartOfCompilation();

            // These two calls synchronize themselves on the converter to avoid duplicate work.
            final RootCallTarget callTarget = converter.convert();
            final LLVMSourceFunctionType sourceType = converter.getSourceType();

            synchronized (descriptor) {
                if (descriptor.getFunction() == this) {
                    descriptor.setFunction(new LLVMIRFunction(callTarget, sourceType));
                } else {
                    // concurrent resolve call in another thread, nothing to do
                    assert descriptor.getFunction() instanceof LLVMIRFunction;
                }
            }
        }

        @Override
        LLVMSourceFunctionType getSourceType() {
            return converter.getSourceType();
        }
    }

    public static final class LLVMIRFunction extends ManagedFunction {
        private final RootCallTarget callTarget;
        private final LLVMSourceFunctionType sourceType;

        public LLVMIRFunction(RootCallTarget callTarget, LLVMSourceFunctionType sourceType) {
            this.callTarget = callTarget;
            this.sourceType = sourceType;
        }

        @Override
        LLVMSourceFunctionType getSourceType() {
            return sourceType;
        }
    }

    public static final class UnresolvedFunction extends Function {

        @Override
        void resolve(LLVMFunctionCode functionCode) {
            throw new LLVMLinkerException(String.format("Unresolved external function %s cannot be found.", functionCode.getLLVMFunction().getName()));
        }

        @Override
        Object createNativeWrapper(LLVMFunctionDescriptor descriptor) {
            CompilerAsserts.neverPartOfCompilation();
            resolve(descriptor.getFunctionCode());
            return descriptor.getFunctionCode().getFunction().createNativeWrapper(descriptor);
        }
    }

    public static final class IntrinsicFunction extends LLVMFunctionCode.ManagedFunction {
        private final LLVMFunctionCode.Intrinsic intrinsic;
        private final LLVMSourceFunctionType sourceType;

        public IntrinsicFunction(LLVMFunctionCode.Intrinsic intrinsic, LLVMSourceFunctionType sourceType) {
            this.intrinsic = intrinsic;
            this.sourceType = sourceType;
        }

        @Override
        LLVMSourceFunctionType getSourceType() {
            return this.sourceType;
        }
    }

    public static final class NativeFunction extends LLVMFunctionCode.Function {
        private final Object nativeFunction;

        public NativeFunction(Object nativeFunction) {
            this.nativeFunction = nativeFunction;
        }

        @Override
        Object createNativeWrapper(LLVMFunctionDescriptor descriptor) {
            return nativeFunction;
        }
    }

    public void resolveIfLazyLLVMIRFunction() {
        CompilerAsserts.neverPartOfCompilation();
        if (getFunction() instanceof LLVMFunctionCode.LazyLLVMIRFunction) {
            getFunction().resolve(this);
            assert getFunction() instanceof LLVMFunctionCode.LLVMIRFunction;
        }
    }

    @Idempotent
    public boolean isLLVMIRFunction() {
        final LLVMFunctionCode.Function currentFunction = getFunction();
        return currentFunction instanceof LLVMFunctionCode.LLVMIRFunction || currentFunction instanceof LLVMFunctionCode.LazyLLVMIRFunction;
    }

    @Idempotent
    public boolean isIntrinsicFunctionSlowPath() {
        CompilerAsserts.neverPartOfCompilation();
        return isIntrinsicFunction(ResolveFunctionNodeGen.getUncached());
    }

    public boolean isIntrinsicFunction(LLVMFunctionCode.ResolveFunctionNode resolve) {
        return resolve.execute(getFunction(), this) instanceof LLVMFunctionCode.IntrinsicFunction;
    }

    @Idempotent
    public boolean isNativeFunctionSlowPath() {
        CompilerAsserts.neverPartOfCompilation();
        return isNativeFunction(ResolveFunctionNodeGen.getUncached());
    }

    public boolean isNativeFunction(LLVMFunctionCode.ResolveFunctionNode resolve) {
        return resolve.execute(getFunction(), this) instanceof LLVMFunctionCode.NativeFunction;
    }

    public boolean isDefined() {
        return !(getFunction() instanceof LLVMFunctionCode.UnresolvedFunction);
    }

    @TruffleBoundary
    public void define(LLVMIntrinsicProvider intrinsicProvider, NodeFactory nodeFactory) {
        Intrinsic intrinsification = new Intrinsic(intrinsicProvider, llvmFunction.getName(), nodeFactory);
        define(new IntrinsicFunction(intrinsification, getFunction().getSourceType()));
    }

    public void define(Function newFunction) {
        setFunction(newFunction);
    }

    public RootCallTarget getLLVMIRFunctionSlowPath() {
        CompilerAsserts.neverPartOfCompilation();
        return getLLVMIRFunction(ResolveFunctionNodeGen.getUncached());
    }

    public RootCallTarget getLLVMIRFunction(ResolveFunctionNode resolve) {
        Function fn = resolve.execute(getFunction(), this);
        return ((LLVMIRFunction) fn).callTarget;
    }

    public Intrinsic getIntrinsicSlowPath() {
        CompilerAsserts.neverPartOfCompilation();
        return getIntrinsic(ResolveFunctionNodeGen.getUncached());
    }

    public Intrinsic getIntrinsic(ResolveFunctionNode resolve) {
        Function fn = resolve.execute(getFunction(), this);
        return ((IntrinsicFunction) fn).intrinsic;
    }

    public Object getNativeFunctionSlowPath() {
        CompilerAsserts.neverPartOfCompilation();
        Function fn = ResolveFunctionNodeGen.getUncached().execute(getFunction(), this);
        Object nativeFunction = ((NativeFunction) fn).nativeFunction;
        if (nativeFunction == null) {
            throw new LLVMLinkerException("Native function " + fn.toString() + " not found");
        }
        return nativeFunction;
    }

    public Object getNativeFunction(ResolveFunctionNode resolveFunctionNode) {
        Function fn = resolveFunctionNode.execute(getFunction(), this);
        Object nativeFunction = ((NativeFunction) fn).nativeFunction;
        if (nativeFunction == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new LLVMLinkerException("Native function " + fn.toString() + " not found");
        }
        return nativeFunction;
    }

    // used for calls from foreign languages
    // includes boundary conversions
    private CallTarget foreignFunctionCallTarget;
    private CallTarget foreignConstructorCallTarget;

    @TruffleBoundary
    private void initForeignCallTarget() {
        synchronized (this) {
            if (foreignFunctionCallTarget != null) {
                return;
            }

            LLVMLanguage language = LLVMLanguage.get(null);
            LLVMSourceFunctionType sourceType = getFunction().getSourceType();
            LLVMInteropType interopType = language.getInteropType(sourceType);

            RootNode foreignCall;
            if (isIntrinsicFunctionSlowPath()) {
                FunctionType type = getLLVMFunction().getType();
                foreignCall = LLVMForeignIntrinsicCallNode.create(language, getIntrinsicSlowPath(), type, (LLVMInteropType.Function) interopType);
            } else {
                foreignCall = LLVMForeignFunctionCallNode.create(language, this, interopType, sourceType);
            }

            foreignFunctionCallTarget = foreignCall.getCallTarget();
        }
    }

    CallTarget getForeignCallTarget() {
        if (foreignFunctionCallTarget == null) {
            initForeignCallTarget();
            assert foreignFunctionCallTarget != null;
        }
        return foreignFunctionCallTarget;
    }

    @TruffleBoundary
    private void initForeignConstructorCallTarget() {
        synchronized (this) {
            if (foreignConstructorCallTarget != null) {
                return;
            }

            LLVMLanguage language = LLVMLanguage.get(null);
            LLVMSourceFunctionType sourceType = getFunction().getSourceType();
            LLVMInteropType interopType = language.getInteropType(sourceType);
            LLVMInteropType extractedType = ((LLVMInteropType.Function) interopType).getParameter(0);
            if (extractedType instanceof LLVMInteropType.Value) {
                LLVMInteropType.Structured structured = ((LLVMInteropType.Value) extractedType).baseType;
                LLVMForeignCallNode foreignCall = LLVMForeignConstructorCallNode.create(
                                language, this, interopType, sourceType, structured);
                foreignConstructorCallTarget = foreignCall.getCallTarget();
            }
        }
    }

    CallTarget getForeignConstructorCallTarget() {
        if (foreignConstructorCallTarget == null) {
            initForeignConstructorCallTarget();
            assert foreignConstructorCallTarget != null;
        }
        return foreignConstructorCallTarget;
    }

    private void setFunction(Function newFunction) {
        this.functionDynamic = this.functionFinal = newFunction;
        this.assumption.invalidate();
        this.assumption = Truffle.getRuntime().createAssumption();
    }

    public Function getFunction() {
        if (CompilerDirectives.isPartialEvaluationConstant(this)) {
            if (!assumption.isValid()) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                return functionDynamic;
            }
            return functionFinal;
        } else {
            return functionDynamic;
        }
    }

    public LLVMFunction getLLVMFunction() {
        return llvmFunction;
    }
}
