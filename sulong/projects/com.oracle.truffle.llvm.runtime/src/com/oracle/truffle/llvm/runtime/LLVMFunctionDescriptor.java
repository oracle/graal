/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates.
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
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.llvm.runtime.LLVMContext.ExternalLibrary;
import com.oracle.truffle.llvm.runtime.NFIContextExtension.NativeLookupResult;
import com.oracle.truffle.llvm.runtime.debug.type.LLVMSourceFunctionType;
import com.oracle.truffle.llvm.runtime.except.LLVMLinkerException;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobal;
import com.oracle.truffle.llvm.runtime.interop.LLVMForeignCallNode;
import com.oracle.truffle.llvm.runtime.interop.LLVMInternalTruffleObject;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.types.FunctionType;

/**
 * Our implementation assumes that there is a 1:1 relationship between callable functions and
 * {@link LLVMFunctionDescriptor}s.
 */
@ExportLibrary(InteropLibrary.class)
@SuppressWarnings("static-method")
public final class LLVMFunctionDescriptor implements LLVMSymbol, LLVMInternalTruffleObject, Comparable<LLVMFunctionDescriptor> {
    private static final long SULONG_FUNCTION_POINTER_TAG = 0xDEAD_FACE_0000_0000L;

    private final FunctionType type;
    private final LLVMContext context;
    private final int functionId;

    private ExternalLibrary library;

    @CompilationFinal private String name;
    @CompilationFinal private Function function;
    @CompilationFinal private Assumption functionAssumption;

    @CompilationFinal private TruffleObject nativeWrapper;
    @CompilationFinal private long nativePointer;

    // used for calls from foreign languages
    // includes boundary conversions
    private CallTarget foreignCallTarget;

    CallTarget getForeignCallTarget() {
        if (foreignCallTarget == null) {
            CompilerDirectives.transferToInterpreter();
            LLVMSourceFunctionType sourceType = getFunction().getSourceType();
            LLVMInteropType interopType = context.getInteropType(sourceType);
            LLVMForeignCallNode foreignCall = new LLVMForeignCallNode(context.getLanguage(), this, interopType);
            foreignCallTarget = Truffle.getRuntime().createCallTarget(foreignCall);
            assert foreignCallTarget != null;
        }
        return foreignCallTarget;
    }

    private static long tagSulongFunctionPointer(int id) {
        return id | SULONG_FUNCTION_POINTER_TAG;
    }

    public static final class Intrinsic {
        private final String intrinsicName;
        private final Map<FunctionType, RootCallTarget> overloadingMap;
        private final LLVMIntrinsicProvider provider;

        public Intrinsic(LLVMIntrinsicProvider provider, String name) {
            this.intrinsicName = name;
            this.overloadingMap = new HashMap<>();
            this.provider = provider;
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
            RootCallTarget newTarget = provider.generateIntrinsicTarget(intrinsicName, type.getArgumentTypes());
            assert newTarget != null;
            overloadingMap.put(type, newTarget);
            return newTarget;
        }

        public LLVMExpressionNode generateNode(FunctionType type, LLVMExpressionNode[] arguments) {
            CompilerAsserts.neverPartOfCompilation();
            LLVMExpressionNode node = provider.generateIntrinsicNode(intrinsicName, arguments, type.getArgumentTypes());
            assert node != null;
            return node;
        }
    }

    public abstract static class Function {
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
            LLVMNativePointer pointer = null;
            NFIContextExtension nfiContextExtension = descriptor.context.getLanguage().getContextExtensionOrNull(NFIContextExtension.class);
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
                pointer = LLVMNativePointer.create(tagSulongFunctionPointer(descriptor.functionId));
                wrapper = pointer;
            }

            descriptor.context.registerFunctionPointer(pointer, descriptor);
            return wrapper;
        }
    }

    public static final class LazyLLVMIRFunction extends ManagedFunction {
        private final LazyToTruffleConverter converter;

        public LazyLLVMIRFunction(LazyToTruffleConverter converter) {
            this.converter = converter;
        }

        @Override
        void resolve(LLVMFunctionDescriptor descriptor) {
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

    static final class UnresolvedFunction extends Function {
        @Override
        void resolve(LLVMFunctionDescriptor descriptor) {
            CompilerAsserts.neverPartOfCompilation();
            // we already did the initial function resolution after parsing but further native
            // libraries could have been loaded in the meantime
            LLVMContext context = descriptor.getContext();
            NFIContextExtension nfiContextExtension = context.getLanguage().getContextExtensionOrNull(NFIContextExtension.class);
            LLVMIntrinsicProvider intrinsicProvider = context.getLanguage().getContextExtensionOrNull(LLVMIntrinsicProvider.class);
            assert intrinsicProvider == null || !intrinsicProvider.isIntrinsified(descriptor.getName());
            if (nfiContextExtension != null) {
                NativeLookupResult nativeFunction = nfiContextExtension.getNativeFunctionOrNull(context, descriptor.getName());
                if (nativeFunction != null) {
                    descriptor.define(nativeFunction.getLibrary(), new LLVMFunctionDescriptor.NativeFunction(nativeFunction.getObject()));
                    return;
                }
            }
            throw new LLVMLinkerException(String.format("External function %s cannot be found.", descriptor.getName()));
        }

        @Override
        TruffleObject createNativeWrapper(LLVMFunctionDescriptor descriptor) {
            resolve(descriptor);
            return descriptor.getFunction().createNativeWrapper(descriptor);
        }
    }

    public static final class IntrinsicFunction extends ManagedFunction {
        private final Intrinsic intrinsic;

        public IntrinsicFunction(Intrinsic intrinsic) {
            this.intrinsic = intrinsic;
        }
    }

    public static final class NativeFunction extends Function {
        private final TruffleObject nativeFunction;

        public NativeFunction(TruffleObject nativeFunction) {
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
            return LLVMNativePointer.createNull();
        }
    }

    private void setFunction(Function newFunction) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        synchronized (this) {
            functionAssumption.invalidate();
            this.function = newFunction;
            this.functionAssumption = Truffle.getRuntime().createAssumption("LLVMFunctionDescriptor.functionAssumption");
        }
    }

    public Function getFunction() {
        if (!functionAssumption.isValid()) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
        }
        return function;
    }

    private LLVMFunctionDescriptor(LLVMContext context, String name, FunctionType type, int functionId, Function function) {
        CompilerAsserts.neverPartOfCompilation();
        this.context = context;
        this.name = name;
        this.type = type;
        this.functionId = functionId;
        this.functionAssumption = Truffle.getRuntime().createAssumption("LLVMFunctionDescriptor.functionAssumption");
        this.function = function;
    }

    public static LLVMFunctionDescriptor createDescriptor(LLVMContext context, String name, FunctionType type, int functionId) {
        return new LLVMFunctionDescriptor(context, name, type, functionId, new UnresolvedFunction());
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

    public void resolveIfLazyLLVMIRFunction() {
        if (getFunction() instanceof LazyLLVMIRFunction) {
            getFunction().resolve(this);
            assert getFunction() instanceof LLVMIRFunction;
        }
    }

    public boolean isLLVMIRFunction() {
        return getFunction() instanceof LLVMIRFunction || getFunction() instanceof LazyLLVMIRFunction;
    }

    public boolean isIntrinsicFunction() {
        getFunction().resolve(this);
        return getFunction() instanceof IntrinsicFunction;
    }

    public boolean isNativeFunction() {
        getFunction().resolve(this);
        return getFunction() instanceof NativeFunction;
    }

    @Override
    public boolean isDefined() {
        return !(function instanceof UnresolvedFunction);
    }

    public void define(LLVMIntrinsicProvider intrinsicProvider) {
        assert intrinsicProvider != null && intrinsicProvider.isIntrinsified(name);
        Intrinsic intrinsification = new Intrinsic(intrinsicProvider, name);
        define(intrinsicProvider.getLibrary(), new LLVMFunctionDescriptor.IntrinsicFunction(intrinsification), true);
    }

    public void define(ExternalLibrary lib, Function newFunction) {
        define(lib, newFunction, false);
    }

    private void define(ExternalLibrary lib, Function newFunction, boolean allowReplace) {
        assert lib != null && newFunction != null;
        if (!isDefined() || allowReplace) {
            this.library = lib;
            setFunction(newFunction);
        } else {
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError("Found multiple definitions of function " + getName() + ".");
        }
    }

    public RootCallTarget getLLVMIRFunction() {
        getFunction().resolve(this);
        assert getFunction() instanceof LLVMIRFunction;
        return ((LLVMIRFunction) getFunction()).callTarget;
    }

    public Intrinsic getIntrinsic() {
        getFunction().resolve(this);
        assert getFunction() instanceof IntrinsicFunction;
        return ((IntrinsicFunction) getFunction()).intrinsic;
    }

    public TruffleObject getNativeFunction() {
        getFunction().resolve(this);
        assert getFunction() instanceof NativeFunction;
        TruffleObject nativeFunction = ((NativeFunction) getFunction()).nativeFunction;
        if (nativeFunction == null) {
            CompilerDirectives.transferToInterpreter();
            throw new LLVMLinkerException("Native function " + getName() + " not found");
        }
        return nativeFunction;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String value) {
        this.name = value;
    }

    @Override
    public ExternalLibrary getLibrary() {
        return library;
    }

    public FunctionType getType() {
        return type;
    }

    @Override
    public String toString() {
        if (name != null) {
            return String.format("function@%d '%s'", functionId, name);
        } else {
            return String.format("function@%d (anonymous)", functionId);
        }
    }

    @Override
    public int compareTo(LLVMFunctionDescriptor o) {
        return Long.compare(functionId, o.functionId);
    }

    public LLVMContext getContext() {
        return context;
    }

    @ExportMessage
    long asPointer() throws UnsupportedMessageException {
        if (isPointer()) {
            return nativePointer;
        }
        CompilerDirectives.transferToInterpreter();
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    boolean isPointer() {
        return nativeWrapper != null;
    }

    /**
     * Gets a pointer to this function that can be stored in native memory.
     */
    @ExportMessage
    LLVMFunctionDescriptor toNative() {
        if (nativeWrapper == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            nativeWrapper = getFunction().createNativeWrapper(this);
            try {
                nativePointer = InteropLibrary.getFactory().getUncached().asPointer(nativeWrapper);
            } catch (UnsupportedMessageException ex) {
                nativePointer = tagSulongFunctionPointer(functionId);
            }
        }
        return this;
    }

    @ExportMessage
    boolean isExecutable() {
        return true;
    }

    @ExportMessage
    static class Execute {

        @Specialization(limit = "5", guards = "self == cachedSelf")
        @SuppressWarnings("unused")
        static Object doCached(LLVMFunctionDescriptor self, Object[] args,
                        @Cached("self") LLVMFunctionDescriptor cachedSelf,
                        @Cached(parameters = "cachedSelf.getForeignCallTarget()") DirectCallNode call) {
            call.forceInlining();
            return call.call(args);
        }

        @Specialization(replaces = "doCached")
        static Object doPolymorphic(LLVMFunctionDescriptor self, Object[] args,
                        @Cached IndirectCallNode call) {
            return call.call(self.getForeignCallTarget(), args);
        }
    }

    @ExportMessage
    boolean hasMembers() {
        return true;
    }

    @ExportLibrary(InteropLibrary.class)
    static final class FunctionMembers implements TruffleObject {

        @ExportMessage
        boolean hasArrayElements() {
            return true;
        }

        @ExportMessage
        long getArraySize() {
            return 1;
        }

        @ExportMessage
        boolean isArrayElementReadable(long index) {
            return index == 0;
        }

        @ExportMessage
        Object readArrayElement(long index) throws InvalidArrayIndexException {
            if (index == 0) {
                return "bind";
            } else {
                throw InvalidArrayIndexException.create(index);
            }
        }
    }

    @ExportMessage
    Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
        return new FunctionMembers();
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    boolean isMemberInvocable(String member) {
        return "bind".equals(member);
    }

    @ExportMessage
    Object invokeMember(String member, @SuppressWarnings("unused") Object[] args) throws UnknownIdentifierException {
        if ("bind".equals(member)) {
            return this;
        } else {
            throw UnknownIdentifierException.create(member);
        }
    }

    @Override
    public boolean isFunction() {
        return true;
    }

    @Override
    public boolean isGlobalVariable() {
        return false;
    }

    @Override
    public LLVMFunctionDescriptor asFunction() {
        return this;
    }

    @Override
    public LLVMGlobal asGlobalVariable() {
        throw new IllegalStateException("Function " + name + " is not a global variable.");
    }
}
