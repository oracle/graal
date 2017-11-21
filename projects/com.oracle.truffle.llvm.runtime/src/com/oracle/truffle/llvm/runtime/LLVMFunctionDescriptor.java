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
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeVisitor;
import com.oracle.truffle.llvm.runtime.interop.LLVMFunctionMessageResolutionForeign;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack.NeedsStack;
import com.oracle.truffle.llvm.runtime.options.SulongEngineOption;
import com.oracle.truffle.llvm.runtime.types.FunctionType;

public final class LLVMFunctionDescriptor implements LLVMFunction, TruffleObject, Comparable<LLVMFunctionDescriptor> {

    private final String functionName;
    private final FunctionType type;
    private final int functionId;
    private final LLVMContext context;

    @CompilationFinal private Function function;
    @CompilationFinal private Assumption functionAssumption;

    @CompilationFinal private TruffleObject nativeWrapper;
    @CompilationFinal private long nativePointer;

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

        final boolean weak;

        Function(boolean weak) {
            this.weak = weak;
        }

        void resolve(@SuppressWarnings("unused") LLVMFunctionDescriptor descriptor) {
            // nothing to do
        }

        abstract TruffleObject createNativeWrapper(LLVMFunctionDescriptor descriptor);
    }

    abstract static class ManagedFunction extends Function {

        ManagedFunction(boolean weak) {
            super(weak);
        }

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
                pointer = LLVMAddress.fromLong(LLVMFunction.tagSulongFunctionPointer(descriptor.getFunctionId()));
                wrapper = new LLVMTruffleAddress(pointer, descriptor.getType(), descriptor.context);
            }

            descriptor.context.registerFunctionPointer(pointer, descriptor);
            return wrapper;
        }
    }

    static final class LazyLLVMIRFunction extends ManagedFunction {
        private final LazyToTruffleConverter converter;

        LazyLLVMIRFunction(LazyToTruffleConverter converter, boolean weak) {
            super(weak);
            this.converter = converter;
        }

        @Override
        void resolve(LLVMFunctionDescriptor descriptor) {
            descriptor.setFunction(new LLVMIRFunction(converter.convert(), weak));
        }
    }

    static final class LLVMIRFunction extends ManagedFunction {
        private final RootCallTarget callTarget;

        LLVMIRFunction(RootCallTarget callTarget, boolean weak) {
            super(weak);
            this.callTarget = callTarget;
        }
    }

    static final class UnresolvedFunction extends Function {

        UnresolvedFunction() {
            super(true);
        }

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
            super(false);
            this.intrinsic = intrinsic;
        }
    }

    static final class NativeFunction extends Function {
        private final TruffleObject nativeFunction;

        NativeFunction(TruffleObject nativeFunction) {
            super(false);
            this.nativeFunction = nativeFunction;
        }

        @Override
        TruffleObject createNativeWrapper(LLVMFunctionDescriptor descriptor) {
            return nativeFunction;
        }
    }

    static final class NullFunction extends Function {

        NullFunction() {
            super(false);
        }

        @Override
        TruffleObject createNativeWrapper(LLVMFunctionDescriptor descriptor) {
            return new LLVMTruffleAddress(LLVMAddress.nullPointer(), descriptor.type, descriptor.context);
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

    private LLVMFunctionDescriptor(LLVMContext context, String name, FunctionType type, int functionId, Function function) {
        CompilerAsserts.neverPartOfCompilation();
        this.context = context;
        this.functionName = name;
        this.type = type;
        this.functionId = functionId;
        this.functionAssumption = Truffle.getRuntime().createAssumption();
        this.function = function;
    }

    public int getFunctionId() {
        return functionId;
    }

    public static LLVMFunctionDescriptor createDescriptor(LLVMContext context, String name, FunctionType type, int functionId) {
        return new LLVMFunctionDescriptor(context, name, type, functionId, new UnresolvedFunction());
    }

    public interface LazyToTruffleConverter {
        RootCallTarget convert();
    }

    public boolean isLLVMIRFunction() {
        return getFunction() instanceof LLVMIRFunction || getFunction() instanceof LazyLLVMIRFunction;
    }

    private static class NeedsStackPointer {
        boolean condition = false;
    }

    public boolean needsStackPointer() {
        if (isLLVMIRFunction()) {
            final NeedsStackPointer needsStackPointer = new NeedsStackPointer();

            getLLVMIRFunction().getRootNode().accept(new NodeVisitor() {

                @Override
                public boolean visit(Node node) {
                    Class<?> nodeClass = node.getClass();
                    while (nodeClass != null) {
                        if (nodeClass.isAnnotationPresent(NeedsStack.class)) {
                            needsStackPointer.condition = true;
                            break;
                        }
                        nodeClass = nodeClass.getSuperclass();
                    }
                    return true;
                }
            });
            return needsStackPointer.condition;
        } else {
            return false;
        }
    }

    public boolean isNativeIntrinsicFunction() {
        getFunction().resolve(this);
        return getFunction() instanceof NativeIntrinsicFunction;
    }

    public boolean isNativeFunction() {
        getFunction().resolve(this);
        return getFunction() instanceof NativeFunction;
    }

    private void declareInSulong(Function newFunction) {
        if (function.weak) {
            // existing function is weak (or undefined)
            setFunction(newFunction);
        } else {
            // existing function is strong
            if (!newFunction.weak) {
                throw new AssertionError("Found multiple strong declarations of function " + getName() + ".");
            }
        }
    }

    public void declareInSulong(LazyToTruffleConverter converter, boolean weak) {
        declareInSulong(new LazyLLVMIRFunction(converter, weak));
    }

    public void declareInSulong(RootCallTarget callTarget, boolean weak) {
        declareInSulong(new LLVMIRFunction(callTarget, weak));
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

    public FunctionType getType() {
        return type;
    }

    /**
     * Gets a pointer to this function that can be stored in native memory.
     */
    @Override
    public long getFunctionPointer() {
        if (nativeWrapper == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            nativeWrapper = getFunction().createNativeWrapper(this);
            if (nativeWrapper == null) {
                return 0;
            }
            try {
                nativePointer = ForeignAccess.sendAsPointer(Message.AS_POINTER.createNode(), nativeWrapper);
            } catch (UnsupportedMessageException ex) {
                nativePointer = LLVMFunction.tagSulongFunctionPointer(functionId);
            }
        }
        return nativePointer;
    }

    @Override
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

}
