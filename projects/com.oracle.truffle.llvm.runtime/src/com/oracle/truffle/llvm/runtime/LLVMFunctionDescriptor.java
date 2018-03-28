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
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.llvm.runtime.LLVMContext.ExternalLibrary;
import com.oracle.truffle.llvm.runtime.debug.LLVMSourceFunctionType;
import com.oracle.truffle.llvm.runtime.interop.LLVMFunctionMessageResolutionForeign;
import com.oracle.truffle.llvm.runtime.interop.LLVMInternalTruffleObject;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMObjectNativeLibrary;
import com.oracle.truffle.llvm.runtime.options.SulongEngineOption;
import com.oracle.truffle.llvm.runtime.types.FunctionType;

public final class LLVMFunctionDescriptor implements LLVMInternalTruffleObject, Comparable<LLVMFunctionDescriptor>, LLVMObjectNativeLibrary.Provider {

    private final String functionName;
    private final FunctionType type;
    private final LLVMContext context;
    private final int functionId;

    private boolean weak;
    private ExternalLibrary library;

    @CompilationFinal private Function function;
    @CompilationFinal private Assumption functionAssumption;

    @CompilationFinal private TruffleObject nativeWrapper;
    @CompilationFinal private long nativePointer;

    private static final long SULONG_FUNCTION_POINTER_TAG = 0xDEAD_FACE_0000_0000L;

    private static long tagSulongFunctionPointer(int id) {
        return id | SULONG_FUNCTION_POINTER_TAG;
    }

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

        abstract TruffleObject createNativeWrapper(LLVMFunctionDescriptor descriptor);

        LLVMSourceFunctionType getSourceType() {
            return null;
        }
    }

    abstract static class ManagedFunction extends Function {
        @Override
        TruffleObject createNativeWrapper(LLVMFunctionDescriptor descriptor) {
            CompilerAsserts.neverPartOfCompilation();

            TruffleObject wrapper = null;
            LLVMAddress pointer = null;
            if (UnresolvedFunction.isNFIAvailable(descriptor)) {
                NFIContextExtension nfiContextExtension = descriptor.context.getContextExtension(NFIContextExtension.class);
                wrapper = nfiContextExtension.createNativeWrapper(descriptor);
                if (wrapper != null) {
                    try {
                        pointer = LLVMAddress.fromLong(ForeignAccess.sendAsPointer(Message.AS_POINTER.createNode(), wrapper));
                    } catch (UnsupportedMessageException e) {
                        throw new AssertionError(e);
                    }
                }
            }

            if (wrapper == null) {
                pointer = LLVMAddress.fromLong(tagSulongFunctionPointer(descriptor.functionId));
                wrapper = new LLVMTruffleAddress(pointer, descriptor.getType(), descriptor.context);
            }

            descriptor.context.registerFunctionPointer(pointer, descriptor);
            return wrapper;
        }
    }

    static final class LazyLLVMIRFunction extends ManagedFunction {
        private final LazyToTruffleConverter converter;

        LazyLLVMIRFunction(LazyToTruffleConverter converter) {
            this.converter = converter;
        }

        @Override
        void resolve(LLVMFunctionDescriptor descriptor) {
            final RootCallTarget callTarget = converter.convert();
            final LLVMSourceFunctionType sourceType = converter.getSourceType();
            descriptor.setFunction(new LLVMIRFunction(callTarget, sourceType));
        }
    }

    static final class LLVMIRFunction extends ManagedFunction {
        private final RootCallTarget callTarget;
        private final LLVMSourceFunctionType sourceType;

        LLVMIRFunction(RootCallTarget callTarget, LLVMSourceFunctionType sourceType) {
            this.callTarget = callTarget;
            this.sourceType = sourceType;
        }

        @Override
        LLVMSourceFunctionType getSourceType() {
            return sourceType;
        }
    }

    static final class UnresolvedFunction extends Function {
        @Override
        void resolve(LLVMFunctionDescriptor descriptor) {
            if (descriptor.isNullFunction()) {
                descriptor.setFunction(new NullFunction());
            } else if (canIntrinsify(descriptor)) {
                NativeIntrinsicProvider nativeIntrinsicProvider = descriptor.getContext().getContextExtension(NativeIntrinsicProvider.class);
                Intrinsic intrinsification = new Intrinsic(nativeIntrinsicProvider, descriptor.functionName);
                descriptor.setFunction(new NativeIntrinsicFunction(intrinsification));
            } else if (isNFIAvailable(descriptor)) {
                NFIContextExtension nfiContextExtension = descriptor.getContext().getContextExtension(NFIContextExtension.class);
                TruffleObject nativeFunction = nfiContextExtension.getNativeFunction(descriptor.getContext(), descriptor.getName());
                descriptor.setFunction(new NativeFunction(nativeFunction));
            } else {
                throw new AssertionError("Failed to look up the function " + descriptor.getName() + ".");
            }
        }

        private static boolean isNFIAvailable(LLVMFunctionDescriptor descriptor) {
            return descriptor.getContext().hasContextExtension(NFIContextExtension.class) && descriptor.getContext().getEnv().getOptions().get(SulongEngineOption.ENABLE_NFI);
        }

        private static boolean canIntrinsify(LLVMFunctionDescriptor descriptor) {
            return descriptor.getContext().hasContextExtension(NativeIntrinsicProvider.class) &&
                            descriptor.getContext().getContextExtension(NativeIntrinsicProvider.class).isIntrinsified(descriptor.functionName);
        }

        @Override
        TruffleObject createNativeWrapper(LLVMFunctionDescriptor descriptor) {
            resolve(descriptor);
            return descriptor.getFunction().createNativeWrapper(descriptor);
        }
    }

    static final class NativeIntrinsicFunction extends ManagedFunction {
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

        @Override
        TruffleObject createNativeWrapper(LLVMFunctionDescriptor descriptor) {
            return nativeFunction;
        }
    }

    static final class NullFunction extends Function {
        @Override
        TruffleObject createNativeWrapper(LLVMFunctionDescriptor descriptor) {
            return new LLVMTruffleAddress(LLVMAddress.nullPointer(), descriptor.type, descriptor.context);
        }
    }

    private void setFunction(Function newFunction) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        functionAssumption.invalidate();
        this.function = newFunction;
        this.functionAssumption = Truffle.getRuntime().createAssumption("LLVMFunctionDescriptor.functionAssumption");
    }

    public Function getFunction() {
        if (!functionAssumption.isValid()) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
        }
        return function;
    }

    public boolean isWeak() {
        return weak;
    }

    private LLVMFunctionDescriptor(LLVMContext context, ExternalLibrary library, String name, FunctionType type, int functionId, Function function, boolean weak) {
        CompilerAsserts.neverPartOfCompilation();
        this.context = context;
        this.library = library;
        this.functionName = name;
        this.type = type;
        this.functionId = functionId;
        this.functionAssumption = Truffle.getRuntime().createAssumption("LLVMFunctionDescriptor.functionAssumption");
        this.function = function;
        this.weak = weak;
    }

    public static LLVMFunctionDescriptor createDescriptor(LLVMContext context, ExternalLibrary library, String name, FunctionType type, int functionId) {
        return new LLVMFunctionDescriptor(context, library, name, type, functionId, new UnresolvedFunction(), true);
    }

    public interface LazyToTruffleConverter {
        RootCallTarget convert();

        /**
         * Get an {@link com.oracle.truffle.llvm.runtime.debug.LLVMSourceFunctionType} for the
         * already converted function. Can be null if no debug information is available in the
         * bitcode file.
         *
         * @return the function's source-level type
         */
        LLVMSourceFunctionType getSourceType();
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

    public void declareInSulong(Function newFunction, boolean newWeak, boolean replaceExistingFunction) {
        if (weak || replaceExistingFunction) {
            // existing function is weak, undefined, or we are allowed to replace it
            setFunction(newFunction);
            weak = newWeak;
        } else {
            // existing function is strong
            if (!newWeak) {
                throw new AssertionError("Found multiple strong declarations of function " + getName() + ".");
            }
        }
    }

    public void declareInSulong(LazyToTruffleConverter converter, boolean newWeak, boolean replaceExistingFunction) {
        declareInSulong(new LazyLLVMIRFunction(converter), newWeak, replaceExistingFunction);
    }

    public void declareInSulong(RootCallTarget callTarget, boolean newWeak) {
        declareInSulong(new LLVMIRFunction(callTarget, null), newWeak, false);
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
        TruffleObject nativeFunction = ((NativeFunction) getFunction()).nativeFunction;
        if (nativeFunction == null) {
            CompilerDirectives.transferToInterpreter();
            throw new LinkageError("Native function " + getName() + " not found");
        }
        return nativeFunction;
    }

    public String getName() {
        return functionName;
    }

    public ExternalLibrary getLibrary() {
        return library;
    }

    public void setLibrary(ExternalLibrary library) {
        this.library = library;
    }

    public FunctionType getType() {
        return type;
    }

    public boolean isNullFunction() {
        return functionId == 0;
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
        return Long.compare(functionId, o.functionId);
    }

    @Override
    public int hashCode() {
        return functionId;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof LLVMFunctionDescriptor)) {
            return false;
        } else {
            LLVMFunctionDescriptor other = (LLVMFunctionDescriptor) obj;
            return functionId == other.functionId;
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

    @Override
    public LLVMObjectNativeLibrary createLLVMObjectNativeLibrary() {
        return new LLVMFunctionDescriptorNativeLibrary();
    }

    private static final class LLVMFunctionDescriptorNativeLibrary extends LLVMObjectNativeLibrary {

        @Override
        public boolean guard(Object obj) {
            return obj instanceof LLVMFunctionDescriptor;
        }

        @Override
        public boolean isPointer(Object obj) {
            LLVMFunctionDescriptor fd = (LLVMFunctionDescriptor) obj;
            return fd.isPointer();
        }

        @Override
        public long asPointer(Object obj) throws InteropException {
            LLVMFunctionDescriptor fd = (LLVMFunctionDescriptor) obj;
            return fd.asPointer();
        }

        @Override
        public LLVMFunctionDescriptor toNative(Object obj) throws InteropException {
            LLVMFunctionDescriptor fd = (LLVMFunctionDescriptor) obj;
            return fd.toNative();
        }
    }

    /*
     * TODO: make this function private
     */
    public long asPointer() {
        if (isNullFunction()) {
            return 0;
        }
        if (isPointer()) {
            return nativePointer;
        }
        CompilerDirectives.transferToInterpreter();
        throw UnsupportedMessageException.raise(Message.AS_POINTER);
    }

    /*
     * TODO: make this function private
     */
    public boolean isPointer() {
        if (isNullFunction()) {
            return true;
        }
        return nativeWrapper != null;
    }

    /*
     * TODO: make this function private
     */
    /**
     * Gets a pointer to this function that can be stored in native memory.
     */
    public LLVMFunctionDescriptor toNative() {
        if (nativeWrapper == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            nativeWrapper = getFunction().createNativeWrapper(this);
            try {
                nativePointer = ForeignAccess.sendAsPointer(Message.AS_POINTER.createNode(), nativeWrapper);
            } catch (UnsupportedMessageException ex) {
                nativePointer = tagSulongFunctionPointer(functionId);
            }
        }
        return this;
    }
}
