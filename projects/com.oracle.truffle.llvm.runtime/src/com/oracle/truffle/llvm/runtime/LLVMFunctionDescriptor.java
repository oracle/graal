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
package com.oracle.truffle.llvm.runtime;

import java.util.HashMap;
import java.util.Map;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.llvm.runtime.interop.LLVMFunctionMessageResolutionForeign;
import com.oracle.truffle.llvm.runtime.types.FunctionType;

public final class LLVMFunctionDescriptor implements LLVMFunction, TruffleObject, Comparable<LLVMFunctionDescriptor> {

    private final String functionName;
    private final FunctionType type;
    private final long functionId;
    private final LLVMContext context;

    @CompilationFinal private Function function;
    @CompilationFinal private Assumption functionAssumption;

    public static final class Intrinsic {
        private final String name;
        private final Map<FunctionType, RootCallTarget> overloadingMap;
        private final NativeIntrinsicProvider provider;
        private final boolean forceInline;
        private final boolean forceSplit;

        public Intrinsic(NativeIntrinsicProvider provider, String name) {
            this.name = name;
            this.overloadingMap = new HashMap<>();
            this.provider = provider;
            this.forceInline = provider.forceInline(name);
            this.forceSplit = provider.forceSplit(name);
        }

        public boolean forceInline() {
            return forceInline;
        }

        public boolean forceSplit() {
            return forceSplit;
        }

        public RootCallTarget generateCallTarget(FunctionType type) {
            return generate(type);
        }

        public RootCallTarget cachedCallTarget(FunctionType type) {
            if (exists(type)) {
                return get(type);
            } else {
                return generate(type);
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

        private RootCallTarget generate(FunctionType type) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            RootCallTarget newTarget = provider.generateIntrinsic(name, type);
            assert newTarget != null;
            overloadingMap.put(type, newTarget);
            return newTarget;
        }

    }

    abstract static class Function {
        void resolve(@SuppressWarnings("unused") LLVMFunctionDescriptor descriptor) {
            // nothing to do
        }
    }

    static final class LazyLLVMIRFunction extends Function {
        private final LazyToTruffleConverter converter;

        LazyLLVMIRFunction(LazyToTruffleConverter converter) {
            this.converter = converter;
        }

        @Override
        void resolve(LLVMFunctionDescriptor descriptor) {
            descriptor.setFunction(new LLVMIRFunction(converter.convert()));
        }
    }

    static final class LLVMIRFunction extends Function {
        private final RootCallTarget callTarget;

        LLVMIRFunction(RootCallTarget callTarget) {
            this.callTarget = callTarget;
        }
    }

    static final class UnresolvedFunction extends Function {

        @Override
        void resolve(LLVMFunctionDescriptor descriptor) {
            if (descriptor.context.getNativeIntrinsicsProvider().isIntrinsified(descriptor.functionName)) {
                Intrinsic intrinsification = new Intrinsic(descriptor.context.getNativeIntrinsicsProvider(), descriptor.functionName);
                descriptor.setFunction(new NativeIntrinsicFunction(intrinsification));
            } else {
                TruffleObject nativeFunction;
                if (!descriptor.isNullFunction()) {
                    NativeLookup nativeLookup = descriptor.context.getNativeLookup();
                    if (nativeLookup == null) {
                        throw new AssertionError("The NativeLookup is disabled. Failed to look up the function " + descriptor.getName() + ".");
                    }
                    nativeFunction = nativeLookup.getNativeFunction(descriptor.getName());
                } else {
                    nativeFunction = null;
                }
                descriptor.setFunction(new NativeFunction(nativeFunction));
            }
        }
    }

    static final class NativeIntrinsicFunction extends Function {
        private final Intrinsic intrinsic;

        NativeIntrinsicFunction(Intrinsic intrinsic) {
            this.intrinsic = intrinsic;
        }
    }

    static final class NativeFunction extends Function {
        private final TruffleObject nativeFunction;

        NativeFunction(TruffleObject nativeFunction) {
            this.nativeFunction = nativeFunction;
        }
    }

    private void setFunction(Function newFunction) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        functionAssumption.invalidate();
        this.function = newFunction;
        this.functionAssumption = Truffle.getRuntime().createAssumption();
    }

    private Function getFunction() {
        if (!functionAssumption.isValid()) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
        }
        return function;
    }

    private LLVMFunctionDescriptor(LLVMContext context, String name, FunctionType type, long functionId) {
        CompilerAsserts.neverPartOfCompilation();
        assert LLVMFunction.isSulongFunctionPointer(functionId);
        this.context = context;
        this.functionName = name;
        this.type = type;
        this.functionId = functionId;
        this.functionAssumption = Truffle.getRuntime().createAssumption();
        this.function = new UnresolvedFunction();
    }

    public static LLVMFunctionDescriptor createDescriptor(LLVMContext context, String name, FunctionType type, long functionId) {
        assert (functionId & LLVMFunction.UPPER_MASK) == 0;
        LLVMFunctionDescriptor func = new LLVMFunctionDescriptor(context, name, type, LLVMFunction.tagSulongFunctionPointer(functionId));
        return func;
    }

    public interface LazyToTruffleConverter {
        RootCallTarget convert();
    }

    public boolean isLLVMIRFunction() {
        return getFunction() instanceof LLVMIRFunction || getFunction() instanceof LazyLLVMIRFunction;
    }

    public boolean isNativeIntrinsicFunction() {
        getFunction().resolve(this);
        return getFunction() instanceof NativeIntrinsicFunction;
    }

    public boolean isNativeFunction() {
        getFunction().resolve(this);
        return getFunction() instanceof NativeFunction;
    }

    public void declareInSulong(LazyToTruffleConverter converter) {
        setFunction(new LazyLLVMIRFunction(converter));
    }

    public void declareInSulong(RootCallTarget callTarget) {
        setFunction(new LLVMIRFunction(callTarget));
    }

    public RootCallTarget getLLVMIRFunction() {
        getFunction().resolve(this);
        assert getFunction() instanceof LLVMIRFunction;
        return ((LLVMIRFunction) getFunction()).callTarget;
    }

    public Intrinsic getNativeIntrinsic() {
        getFunction().resolve(this);
        assert getFunction() instanceof NativeIntrinsicFunction;
        return ((NativeIntrinsicFunction) getFunction()).intrinsic;
    }

    public TruffleObject getNativeFunction() {
        getFunction().resolve(this);
        assert getFunction() instanceof NativeFunction;
        return ((NativeFunction) getFunction()).nativeFunction;
    }

    public String getName() {
        return functionName;
    }

    public FunctionType getType() {
        return type;
    }

    /**
     * Gets an unique index for a function descriptor.
     *
     * @return the function's index
     */
    @Override
    public long getFunctionPointer() {
        return functionId;
    }

    @Override
    public boolean isNullFunction() {
        return LLVMFunction.getSulongFunctionIndex(functionId) == 0;
    }

    @Override
    public String toString() {
        if (functionName != null) {
            return String.format("function@%d '%s'", functionId, functionName);
        } else {
            return String.format("function@%d (anonymous)", functionId);
        }
    }

    @Override
    public int compareTo(LLVMFunctionDescriptor o) {
        return Long.compare(functionId, o.getFunctionPointer());
    }

    @Override
    public int hashCode() {
        return (int) functionId;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof LLVMFunctionDescriptor)) {
            return false;
        } else {
            LLVMFunctionDescriptor other = (LLVMFunctionDescriptor) obj;
            return getFunctionPointer() == other.getFunctionPointer();
        }
    }

    public static boolean isInstance(TruffleObject object) {
        return object instanceof LLVMFunctionDescriptor;
    }

    public LLVMContext getContext() {
        return context;
    }

    @Override
    public ForeignAccess getForeignAccess() {
        return LLVMFunctionMessageResolutionForeign.ACCESS;
    }

}
