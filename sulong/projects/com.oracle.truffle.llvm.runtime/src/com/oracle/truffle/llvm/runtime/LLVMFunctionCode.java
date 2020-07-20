/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates.
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

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.utilities.AssumedValue;
import com.oracle.truffle.llvm.runtime.LLVMFunctionCodeFactory.ResolveFunctionNodeGen;
import com.oracle.truffle.llvm.runtime.NFIContextExtension.NativeLookupResult;
import com.oracle.truffle.llvm.runtime.debug.type.LLVMSourceFunctionType;
import com.oracle.truffle.llvm.runtime.except.LLVMLinkerException;
import com.oracle.truffle.llvm.runtime.memory.LLVMNativeMemory;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.types.FunctionType;

/**
 * {@link LLVMFunctionCode} represents the callable function of a {@link LLVMFunction}.
 *
 * A call target is generated when a {@link Function} is resolved.
 *
 */
public class LLVMFunctionCode {
    private static final long SULONG_FUNCTION_POINTER_TAG = 0xBADE_FACE_0000_0000L;

    static {
        assert LLVMNativeMemory.isCommonHandleMemory(SULONG_FUNCTION_POINTER_TAG);
        assert !LLVMNativeMemory.isDerefHandleMemory(SULONG_FUNCTION_POINTER_TAG);
    }

    private final AssumedValue<Function> function;
    private final LLVMContext context;
    private final LLVMFunction llvmFunction;

    public LLVMFunctionCode(LLVMContext context, LLVMFunction llvmFunction) {
        this.context = context;
        this.llvmFunction = llvmFunction;
        this.function = new AssumedValue<>("LLVMFunctionRuntime.initialFunction", llvmFunction.getFunction());
    }

    private static long tagSulongFunctionPointer(int id) {
        return id | SULONG_FUNCTION_POINTER_TAG;
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

        abstract TruffleObject createNativeWrapper(LLVMFunctionDescriptor descriptor);

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
        TruffleObject createNativeWrapper(LLVMFunctionDescriptor descriptor) {
            CompilerAsserts.neverPartOfCompilation();

            TruffleObject wrapper = null;
            LLVMNativePointer pointer = null;
            NFIContextExtension nfiContextExtension = descriptor.getContext().getContextExtensionOrNull(NFIContextExtension.class);
            if (nfiContextExtension != null) {
                wrapper = nfiContextExtension.createNativeWrapper(descriptor);
                if (wrapper != null) {
                    try {
                        pointer = LLVMNativePointer.create(InteropLibrary.getFactory().getUncached().asPointer(wrapper));
                    } catch (UnsupportedMessageException e) {
                        CompilerDirectives.transferToInterpreter();
                        throw new AssertionError(e);
                    }
                }
            }

            if (wrapper == null) {
                pointer = LLVMNativePointer.create(tagSulongFunctionPointer(descriptor.getLLVMFunction().getSymbolIndex(false)));
                wrapper = pointer;
            }

            descriptor.getContext().registerFunctionPointer(pointer, descriptor);
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
            final RootCallTarget callTarget = converter.convert();
            final LLVMSourceFunctionType sourceType = converter.getSourceType();
            descriptor.setFunction(new LLVMIRFunction(callTarget, sourceType));
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
        void resolve(LLVMFunctionCode descriptor) {
            CompilerAsserts.neverPartOfCompilation();
            // we already did the initial function resolution after parsing but further native
            // libraries could have been loaded in the meantime
            LLVMContext context = descriptor.getContext();
            synchronized (context) {
                // synchronize on the context: only one thread is allowed to resolve symbols
                if (descriptor.getFunction() != this) {
                    // another thread was faster, nothing to do
                    return;
                }

                NFIContextExtension nfiContextExtension = context.getContextExtensionOrNull(NFIContextExtension.class);
                LLVMIntrinsicProvider intrinsicProvider = context.getLanguage().getCapability(LLVMIntrinsicProvider.class);
                assert !intrinsicProvider.isIntrinsified(descriptor.getLLVMFunction().getName());
                if (nfiContextExtension != null) {
                    NativeLookupResult nativeFunction = nfiContextExtension.getNativeFunctionOrNull(context, descriptor.getLLVMFunction().getName());
                    if (nativeFunction != null) {
                        descriptor.define(nativeFunction.getLibrary(), new LLVMFunctionCode.NativeFunction(nativeFunction.getObject()));
                        return;
                    }
                }
            }
            throw new LLVMLinkerException(String.format("External function %s cannot be found.", descriptor.getLLVMFunction().getName()));
        }

        @Override
        TruffleObject createNativeWrapper(LLVMFunctionDescriptor descriptor) {
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
        private final TruffleObject nativeFunction;

        public NativeFunction(TruffleObject nativeFunction) {
            this.nativeFunction = nativeFunction;
        }

        @Override
        TruffleObject createNativeWrapper(LLVMFunctionDescriptor descriptor) {
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

    public boolean isLLVMIRFunction() {
        final LLVMFunctionCode.Function currentFunction = getFunction();
        return currentFunction instanceof LLVMFunctionCode.LLVMIRFunction || currentFunction instanceof LLVMFunctionCode.LazyLLVMIRFunction;
    }

    public boolean isIntrinsicFunctionSlowPath() {
        CompilerAsserts.neverPartOfCompilation();
        return isIntrinsicFunction(ResolveFunctionNodeGen.getUncached());
    }

    public boolean isIntrinsicFunction(LLVMFunctionCode.ResolveFunctionNode resolve) {
        return resolve.execute(getFunction(), this) instanceof LLVMFunctionCode.IntrinsicFunction;
    }

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

    public void define(LLVMIntrinsicProvider intrinsicProvider, NodeFactory nodeFactory) {
        Intrinsic intrinsification = new Intrinsic(intrinsicProvider, llvmFunction.getName(), nodeFactory);
        define(intrinsicProvider.getLibrary(), new IntrinsicFunction(intrinsification, getFunction().getSourceType()), true);
    }

    public void define(ExternalLibrary lib, Function newFunction) {
        define(lib, newFunction, false);
    }

    private void define(ExternalLibrary lib, Function newFunction, boolean allowReplace) {
        assert lib != null && newFunction != null;
        if (!isDefined() || allowReplace) {
            llvmFunction.setLibrary(lib);
            setFunction(newFunction);
        } else {
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError("Found multiple definitions of function " + llvmFunction.getName() + ".");
        }
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

    public TruffleObject getNativeFunctionSlowPath() {
        CompilerAsserts.neverPartOfCompilation();
        return getNativeFunction(ResolveFunctionNodeGen.getUncached());
    }

    public TruffleObject getNativeFunction(ResolveFunctionNode resolve) {
        Function fn = resolve.execute(getFunction(), this);
        TruffleObject nativeFunction = ((NativeFunction) fn).nativeFunction;
        if (nativeFunction == null) {
            CompilerDirectives.transferToInterpreter();
            throw new LLVMLinkerException("Native function " + fn.toString() + " not found");
        }
        return nativeFunction;
    }

    private void setFunction(Function newFunction) {
        function.set(newFunction);
    }

    public Function getFunction() {
        return function.get();
    }

    public LLVMContext getContext() {
        return context;
    }

    public LLVMFunction getLLVMFunction() {
        return llvmFunction;
    }
}
