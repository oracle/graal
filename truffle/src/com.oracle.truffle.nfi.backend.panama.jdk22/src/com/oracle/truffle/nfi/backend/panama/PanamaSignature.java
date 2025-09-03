/*
 * Copyright (c) 2017, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.nfi.backend.panama;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateAOT;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.nfi.backend.panama.FunctionExecuteNodeGen.SignatureExecuteNodeGen;
import com.oracle.truffle.nfi.backend.panama.PanamaClosure.MonomorphicClosureInfo;
import com.oracle.truffle.nfi.backend.panama.PanamaClosure.PolymorphicClosureInfo;
import com.oracle.truffle.nfi.backend.spi.NFIBackendSignatureBuilderLibrary;
import com.oracle.truffle.nfi.backend.spi.NFIBackendSignatureLibrary;
import com.oracle.truffle.nfi.backend.spi.NFIState;
import com.oracle.truffle.nfi.backend.spi.types.NativeSimpleType;
import com.oracle.truffle.nfi.backend.spi.util.ProfiledArrayBuilder;
import com.oracle.truffle.nfi.backend.spi.util.ProfiledArrayBuilder.ArrayBuilderFactory;
import com.oracle.truffle.nfi.backend.spi.util.ProfiledArrayBuilder.ArrayFactory;

@ExportLibrary(value = NFIBackendSignatureLibrary.class, useForAOT = false)
final class PanamaSignature {

    @TruffleBoundary
    public static PanamaSignature create(CachedSignatureInfo info, MethodType upcallType) {
        return new PanamaSignature(info.functionDescriptor, upcallType, info);
    }

    private final FunctionDescriptor functionDescriptor;

    final CachedSignatureInfo signatureInfo;

    private final MethodType upcallType;

    PanamaSignature(FunctionDescriptor functionDescriptor, MethodType upcallType, CachedSignatureInfo signatureInfo) {
        this.functionDescriptor = functionDescriptor;
        this.upcallType = upcallType;

        this.signatureInfo = signatureInfo;
    }

    @ExportMessage
    static final class Call {

        @Specialization
        static Object callPanama(PanamaSignature self, PanamaSymbol functionPointer, Object[] args,
                        @Cached.Exclusive @Cached FunctionExecuteNode functionExecute) throws ArityException, UnsupportedTypeException {
            long pointer = functionPointer.asPointer();
            return functionExecute.execute(pointer, self, args);
        }

        @Specialization(limit = "3")
        @GenerateAOT.Exclude
        static Object callGeneric(PanamaSignature self, Object functionPointer, Object[] args,
                        @CachedLibrary("functionPointer") InteropLibrary interop,
                        @Bind Node node,
                        @Cached InlinedBranchProfile isExecutable,
                        @Cached InlinedBranchProfile toNative,
                        @Cached InlinedBranchProfile error,
                        @Cached.Exclusive @Cached FunctionExecuteNode functionExecute) throws ArityException, UnsupportedTypeException {
            if (interop.isExecutable(functionPointer)) {
                // This branch can be invoked when SignatureLibrary is used to invoke a function
                // pointer without prior engaging the interop to execute executable function
                // pointers. It may happen, for example, in SVM for function substitutes.
                try {
                    isExecutable.enter(node);
                    return interop.execute(functionPointer, args);
                } catch (UnsupportedMessageException e) {
                    error.enter(node);
                    throw UnsupportedTypeException.create(new Object[]{functionPointer}, "functionPointer was executable but threw UnsupportedMessageException on execute()");
                }
            }
            if (!interop.isPointer(functionPointer)) {
                toNative.enter(node);
                interop.toNative(functionPointer);
            }
            long pointer;
            try {
                pointer = interop.asPointer(functionPointer);
            } catch (UnsupportedMessageException e) {
                error.enter(node);
                throw UnsupportedTypeException.create(new Object[]{functionPointer}, "functionPointer is not executable and not a pointer");
            }
            return functionExecute.execute(pointer, self, args);
        }
    }

    MethodType getUpcallMethodType() {
        return upcallType;
    }

    @TruffleBoundary
    @SuppressWarnings("restricted")
    MemorySegment bind(MethodHandle cachedHandle, Object receiver, Node location) {
        MethodHandle bound = cachedHandle.bindTo(receiver);
        Arena arena = PanamaNFIContext.get(null).getContextArena();
        try {
            return Linker.nativeLinker().upcallStub(bound, functionDescriptor, arena);
        } catch (IllegalCallerException ic) {
            throw NFIError.illegalNativeAccess(location);
        }
    }

    @ExportMessage
    @ImportStatic(PanamaNFILanguage.class)
    static final class CreateClosure {

        @Specialization(guards = {"signature.signatureInfo == cachedSignatureInfo", "executable == cachedExecutable"}, assumptions = "getSingleContextAssumption()", limit = "3")
        static PanamaClosure doCachedExecutable(PanamaSignature signature, Object executable,
                        @Bind Node node,
                        @Cached("signature.signatureInfo") CachedSignatureInfo cachedSignatureInfo,
                        @Cached("executable") Object cachedExecutable,
                        @Cached("create(cachedSignatureInfo, cachedExecutable)") MonomorphicClosureInfo cachedClosureInfo) {
            assert signature.signatureInfo == cachedSignatureInfo && executable == cachedExecutable;
            // no need to cache duplicated allocation in the single-context case
            // the NFI frontend is taking care of that already
            MemorySegment ret = createClosureHandle(cachedClosureInfo.handle, signature, cachedExecutable, node);

            return new PanamaClosure(ret);
        }

        @Specialization(replaces = "doCachedExecutable", guards = "signature.signatureInfo == cachedSignatureInfo", limit = "3")
        static PanamaClosure doCachedSignature(PanamaSignature signature, Object executable,
                        @Bind Node node,
                        @Cached("signature.signatureInfo") CachedSignatureInfo cachedSignatureInfo,
                        @Cached("create(cachedSignatureInfo)") PolymorphicClosureInfo cachedClosureInfo) {
            assert signature.signatureInfo == cachedSignatureInfo;
            MemorySegment ret = createClosureHandle(cachedClosureInfo.handle, signature, executable, node);
            return new PanamaClosure(ret);
        }

        @TruffleBoundary
        @Specialization(replaces = "doCachedSignature")
        static PanamaClosure createClosure(PanamaSignature signature, Object executable,
                        @Bind Node node) {
            PolymorphicClosureInfo cachedClosureInfo = PolymorphicClosureInfo.create(signature.signatureInfo);
            MemorySegment ret = createClosureHandle(cachedClosureInfo.handle, signature, executable, node);
            return new PanamaClosure(ret);
        }

        @TruffleBoundary
        private static MemorySegment createClosureHandle(MethodHandle cachedClosureInfo, PanamaSignature signature, Object executable, Node node) {
            MethodHandle cachedHandle = cachedClosureInfo.asType(signature.getUpcallMethodType());
            return signature.bind(cachedHandle, executable, node);
        }
    }

    @ExportLibrary(NFIBackendSignatureBuilderLibrary.class)
    static final class PanamaSignatureBuilder {

        MethodType downcallType;
        MethodType upcallType;
        PanamaType retType;
        ArgsState argsState;
        ProfiledArrayBuilder<PanamaType> argTypes;

        private static final ArrayFactory<PanamaType> FACTORY = PanamaType[]::new;

        void addArg(PanamaType arg, ArgsState newState) {
            assert argsState.argCount + 1 == newState.argCount;
            argTypes.add(arg);
            argsState = newState;
        }

        PanamaSignatureBuilder(ArrayBuilderFactory factory) {
            argsState = ArgsState.NO_ARGS;
            argTypes = factory.allocate(FACTORY);
            downcallType = MethodType.methodType(void.class);
            upcallType = MethodType.methodType(void.class, Object.class);
        }

        @TruffleBoundary
        @ExportMessage
        void setReturnType(Object t) {
            PanamaType type = (PanamaType) t;
            retType = type;
            downcallType = downcallType.changeReturnType(type.javaType);
            upcallType = upcallType.changeReturnType(type.javaRetType);
        }

        @ExportMessage
        static class AddArgument {

            @Specialization(guards = {"builder.argsState == oldState", "type == cachedType"}, limit = "1")
            static void doCached(PanamaSignatureBuilder builder, PanamaType type,
                            @Cached("type") PanamaType cachedType,
                            @Cached("builder.argsState") ArgsState oldState,
                            @Cached("oldState.addArg(cachedType)") ArgsState newState) {
                assert builder.argsState == oldState && type == cachedType;
                builder.addArg(cachedType, newState);

                appendParameterTypes(builder, type);
            }

            @Specialization(replaces = "doCached")
            static void doGeneric(PanamaSignatureBuilder builder, PanamaType type) {
                ArgsState newState = builder.argsState.addArg(type);
                builder.addArg(type, newState);
            }

            @TruffleBoundary
            private static void appendParameterTypes(PanamaSignatureBuilder builder, PanamaType type) {
                builder.downcallType = builder.downcallType.appendParameterTypes(type.javaType);
                builder.upcallType = builder.upcallType.appendParameterTypes(type.javaType);
            }
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        void makeVarargs() {
            throw new UnsupportedOperationException("Cannot make varargs because varargs are not implemented.");
        }

        @ExportMessage
        @ImportStatic(PanamaSignature.class)
        static class Build {

            @Specialization(guards = {"builder.argsState == cachedState", "builder.retType == cachedRetType"}, limit = "3")
            static Object doCached(PanamaSignatureBuilder builder,
                            @Bind @SuppressWarnings("unused") Node node,
                            @Cached("builder.retType") @SuppressWarnings("unused") PanamaType cachedRetType,
                            @Cached("builder.argsState") @SuppressWarnings("unused") ArgsState cachedState,
                            @Cached("prepareSignatureInfo(cachedRetType, cachedState, node)") CachedSignatureInfo cachedSignatureInfo) {
                return create(cachedSignatureInfo, builder.upcallType);
            }

            @Specialization(replaces = "doCached")
            static Object doGeneric(PanamaSignatureBuilder builder,
                            @Bind Node node) {
                CachedSignatureInfo sigInfo = prepareSignatureInfo(builder.retType, builder.argsState, node);

                return create(sigInfo, builder.upcallType);
            }
        }
    }

    @TruffleBoundary
    @SuppressWarnings("unused")
    public static CachedSignatureInfo prepareSignatureInfo(PanamaType retType, ArgsState state, Node location) {
        PanamaType[] argTypes = new PanamaType[state.argCount];
        ArgsState curState = state;
        for (int i = state.argCount - 1; i >= 0; i--) {
            argTypes[i] = curState.lastArg;
            curState = curState.prev;
        }
        FunctionDescriptor descriptor = createDescriptor(argTypes, retType);
        MethodHandle downcallHandle = createDowncallHandle(descriptor, location);
        return new CachedSignatureInfo(PanamaNFILanguage.get(null), retType, argTypes, descriptor, downcallHandle);
    }

    private static FunctionDescriptor createDescriptor(PanamaType[] argTypes, PanamaType retType) {
        FunctionDescriptor descriptor = FunctionDescriptor.ofVoid();
        if (retType.nativeLayout == null) {
            descriptor = descriptor.dropReturnLayout();
        } else {
            descriptor = descriptor.changeReturnLayout(retType.nativeLayout);
        }
        for (PanamaType argType : argTypes) {
            descriptor = descriptor.appendArgumentLayouts(argType.nativeLayout);
        }
        return descriptor;
    }

    static MethodHandle createDowncallHandle(FunctionDescriptor descriptor, Node location) {
        int parameterCount = descriptor.argumentLayouts().size();
        try {
            @SuppressWarnings("restricted")
            MethodHandle handle = Linker.nativeLinker().downcallHandle(descriptor).asSpreader(Object[].class, parameterCount).asType(
                            MethodType.methodType(Object.class, new Class<?>[]{MemorySegment.class, Object[].class}));
            return handle;
        } catch (IllegalCallerException ic) {
            throw NFIError.illegalNativeAccess(location);
        }
    }

    static final class ArgsState {

        static final ArgsState NO_ARGS = new ArgsState(0, null, null);

        final int argCount;

        final PanamaType lastArg;
        final ArgsState prev;

        ArgsState(int argCount, PanamaType lastArg, ArgsState prev) {
            this.argCount = argCount;
            this.lastArg = lastArg;
            this.prev = prev;
        }

        ArgsState addArg(PanamaType type) {
            return new ArgsState(argCount + 1, type, this);
        }
    }

    static final class CachedSignatureInfo {
        final PanamaType retType;
        final PanamaType[] argTypes;
        final FunctionDescriptor functionDescriptor;
        final CallTarget callTarget;
        final MethodHandle downcallHandle;

        CachedSignatureInfo(PanamaNFILanguage language, PanamaType retType, PanamaType[] argTypes, FunctionDescriptor functionDescriptor, MethodHandle downcallHandle) {
            this.retType = retType;
            this.argTypes = argTypes;
            this.functionDescriptor = functionDescriptor;
            this.downcallHandle = downcallHandle;
            this.callTarget = SignatureExecuteNodeGen.create(language, this).getCallTarget();
        }

        PanamaType[] getArgTypes() {
            return argTypes;
        }

        PanamaType getRetType() {
            return retType;
        }

        Object execute(PanamaSignature signature, Object[] args, MemorySegment segment, Node node) {
            assert signature.signatureInfo == this;
            CompilerAsserts.partialEvaluationConstant(retType);

            PanamaNFILanguage language = PanamaNFILanguage.get(node);
            NFIState nfiState = language.getNFIState();
            ErrorContext ctx = (ErrorContext) language.errorContext.get();
            try {
                // We need to set and capture native errno as close as possible to the native call
                // to avoid the JVM clobbering it
                ctx.setNativeErrno(nfiState.getNFIErrno());
                Object result = downcall(args, segment);
                nfiState.setNFIErrno(ctx.getNativeErrno());

                if (result == null) {
                    return NativePointer.NULL;
                } else if (retType.type == NativeSimpleType.STRING) {
                    long pointer = ((MemorySegment) result).address();
                    return new NativeString(pointer);
                } else if (retType.type == NativeSimpleType.POINTER) {
                    return NativePointer.create((long) result);
                } else {
                    return result;
                }
            } catch (Throwable t) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new IllegalStateException(t);
            }
        }

        @TruffleBoundary(allowInlining = true)
        private Object downcall(Object[] args, MemorySegment segment) throws Throwable {
            return downcallHandle.invokeExact(segment, args);
        }
    }
}
