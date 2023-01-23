/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
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
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.nfi.backend.panama.FunctionExecuteNodeGen.SignatureExecuteNodeGen;
import com.oracle.truffle.nfi.backend.spi.NFIBackendSignatureBuilderLibrary;
import com.oracle.truffle.nfi.backend.spi.NFIBackendSignatureLibrary;
import com.oracle.truffle.nfi.backend.spi.types.NativeSimpleType;
import com.oracle.truffle.nfi.backend.spi.util.ProfiledArrayBuilder;
import com.oracle.truffle.nfi.backend.spi.util.ProfiledArrayBuilder.ArrayBuilderFactory;
import com.oracle.truffle.nfi.backend.spi.util.ProfiledArrayBuilder.ArrayFactory;
import com.oracle.truffle.nfi.backend.panama.PanamaClosure.MonomorphicClosureInfo;
import com.oracle.truffle.nfi.backend.panama.PanamaClosure.PolymorphicClosureInfo;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;

import java.lang.foreign.Linker;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemoryAddress;
import java.lang.foreign.MemorySession;

@ExportLibrary(value = NFIBackendSignatureLibrary.class, useForAOT = false)
final class PanamaSignature {

    @TruffleBoundary
    public static PanamaSignature create(PanamaNFIContext context, CachedSignatureInfo info, PanamaType retType, PanamaType[] argTypes, MethodType downcallType, MethodType upcallType) {
        // create Function descriptor
        return new PanamaSignature(info.functionDescriptor, downcallType, upcallType, info);
    }

    private final FunctionDescriptor functionDescriptor;

    final CachedSignatureInfo signatureInfo;

    private final MethodType downcallType;
    private final MethodType upcallType;

    @CompilationFinal private MethodHandle uncachedUpcallHandle;

    PanamaSignature(FunctionDescriptor functionDescriptor, MethodType downcallType, MethodType upcallType, CachedSignatureInfo signatureInfo) {
        this.functionDescriptor = functionDescriptor;
        this.downcallType = downcallType;
        this.upcallType = upcallType;

        this.signatureInfo = signatureInfo;
    }

    MethodHandle createDowncallHandle(long functionPointer) {
        return Linker.nativeLinker().downcallHandle(MemoryAddress.ofLong(functionPointer), functionDescriptor)
                        .asSpreader(Object[].class, downcallType.parameterCount())
                        .asType(MethodType.methodType(Object.class, Object[].class));
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
                        @Cached BranchProfile isExecutable,
                        @Cached BranchProfile toNative,
                        @Cached BranchProfile error,
                        @Cached.Exclusive @Cached FunctionExecuteNode functionExecute) throws ArityException, UnsupportedTypeException {
            if (interop.isExecutable(functionPointer)) {
                // This branch can be invoked when SignatureLibrary is used to invoke a function
                // pointer without prior engaging the interop to execute executable function
                // pointers. It may happen, for example, in SVM for function substitutes.
                try {
                    isExecutable.enter();
                    return interop.execute(functionPointer, args);
                } catch (UnsupportedMessageException e) {
                    error.enter();
                    throw UnsupportedTypeException.create(new Object[]{functionPointer}, "functionPointer", e);
                }
            }
            if (!interop.isPointer(functionPointer)) {
                toNative.enter();
                interop.toNative(functionPointer);
            }
            long pointer;
            try {
                pointer = interop.asPointer(functionPointer);
            } catch (UnsupportedMessageException e) {
                error.enter();
                throw UnsupportedTypeException.create(new Object[]{functionPointer}, "functionPointer", e);
            }
            return functionExecute.execute(pointer, self, args);
        }
    }

    boolean checkUpcallMethodHandle(MethodHandle handle) {
        return handle.type() == upcallType;
    }

    MethodType getUpcallMethodType() {
        return upcallType;
    }

    @TruffleBoundary
    MemoryAddress bind(MethodHandle cachedHandle, Object receiver) {
        MethodHandle bound = cachedHandle.bindTo(receiver);
        return Linker.nativeLinker().upcallStub(bound, functionDescriptor, MemorySession.global() /* TODO */).address();
    }

    @ExportMessage
    @ImportStatic(PanamaNFILanguage.class)
    static final class CreateClosure {

        @Specialization(guards = {"signature.signatureInfo == cachedSignatureInfo", "executable == cachedExecutable"}, assumptions = "getSingleContextAssumption()")
        static PanamaClosure doCachedExecutable(PanamaSignature signature, Object executable,
                                                @Cached("signature.signatureInfo") CachedSignatureInfo cachedSignatureInfo,
                                                @Cached("executable") Object cachedExecutable,
                                                @CachedLibrary("signature") NFIBackendSignatureLibrary self,
                                                @Cached("create(cachedSignatureInfo, cachedExecutable)") MonomorphicClosureInfo cachedClosureInfo) {
            assert signature.signatureInfo == cachedSignatureInfo && executable == cachedExecutable;
            // no need to cache duplicated allocation in the single-context case
            // the NFI frontend is taking care of that already
            MethodHandle cachedHandle = cachedClosureInfo.cachedHandle.asType(signature.getUpcallMethodType());
            MemoryAddress ret = signature.bind(cachedHandle, cachedExecutable);  // TODO check if this can also be cached
            return new PanamaClosure(ret);
        }

        @Specialization(replaces = "doCachedExecutable", guards = "signature.signatureInfo == cachedSignatureInfo")
        static PanamaClosure doCachedSignature(PanamaSignature signature, Object executable,
                                               @Cached("signature.signatureInfo") CachedSignatureInfo cachedSignatureInfo,
                                               @Cached("create(cachedSignatureInfo)") PolymorphicClosureInfo cachedClosureInfo) {
            assert signature.signatureInfo == cachedSignatureInfo;
            MethodHandle cachedHandle = cachedClosureInfo.cachedHandle.asType(signature.getUpcallMethodType());
            MemoryAddress ret = signature.bind(cachedHandle, executable);
            return new PanamaClosure(ret);
        }

        @TruffleBoundary
        @Specialization(replaces = "doCachedSignature")
        static PanamaClosure createClosure(PanamaSignature signature, Object executable) {
            PolymorphicClosureInfo cachedClosureInfo = PolymorphicClosureInfo.create(signature.signatureInfo);
            MethodHandle cachedHandle = cachedClosureInfo.cachedHandle.asType(signature.getUpcallMethodType());
            MemoryAddress ret = signature.bind(cachedHandle, executable);
            return new PanamaClosure(ret);
        }
    }

    @ExportLibrary(NFIBackendSignatureBuilderLibrary.class)
    static final class PanamaSignatureBuilder {

        FunctionDescriptor descriptor;
        MethodType downcallType;
        MethodType upcallType;
        PanamaType retType;
        ArgsState argsState;
        ProfiledArrayBuilder<PanamaType> argTypes;

        private static final ArrayFactory<PanamaType> FACTORY = new ArrayFactory<>() {
            @Override
            public PanamaType[] create(int size) {
                return new PanamaType[size];
            }
        };

        void addArg(PanamaType arg, ArgsState newState) {
            assert argsState.argCount + 1 == newState.argCount;
            argTypes.add(arg);
            argsState = newState;
        }

        PanamaSignatureBuilder(ArrayBuilderFactory factory) {
            argsState = ArgsState.NO_ARGS;
            argTypes = factory.allocate(FACTORY);
            descriptor = FunctionDescriptor.ofVoid();
            downcallType = MethodType.methodType(void.class);
            upcallType = MethodType.methodType(void.class, Object.class);
        }

        @ExportMessage
        void setReturnType(Object t) {
            PanamaType type = (PanamaType) t;

            if (type.nativeLayout == null) {
                descriptor = descriptor.dropReturnLayout();
            } else {
                descriptor = descriptor.changeReturnLayout(type.nativeLayout);
            }

            retType = type;
            downcallType = downcallType.changeReturnType(type.javaType);
            upcallType = upcallType.changeReturnType(type.javaRetType);
        }

        @ExportMessage
        void addArgument(Object t) {
            PanamaType type = (PanamaType) t;

            ArgsState newState = argsState.addArg(type);
            addArg(type, newState);

            descriptor = descriptor.appendArgumentLayouts(type.nativeLayout);
            downcallType = downcallType.appendParameterTypes(type.javaType);
            upcallType = upcallType.appendParameterTypes(type.javaType);
        }

        @ExportMessage
        void makeVarargs() {
            System.out.println("varargs");
        }

        @ExportMessage
        @ImportStatic(PanamaSignature.class)
        static class Build {

            @Specialization(guards = {"builder.argsState == cachedState", "builder.retType == cachedRetType"})
            static Object doCached(PanamaSignatureBuilder builder,
                       @Cached("builder.retType") PanamaType cachedRetType,
                       @Cached("builder.argsState") ArgsState cachedState,
                       @CachedLibrary("builder") NFIBackendSignatureBuilderLibrary self,
                       @SuppressWarnings("unused") @Cached("builder.descriptor") FunctionDescriptor functionDescriptor,
                       @Cached("prepareSignatureInfo(cachedRetType, cachedState, functionDescriptor)") CachedSignatureInfo cachedSignatureInfo) {

                return create(PanamaNFIContext.get(self), cachedSignatureInfo, cachedRetType, cachedSignatureInfo.argTypes, builder.downcallType, builder.upcallType);
            }

            @Specialization(replaces = "doCached")
            static Object doGeneric(PanamaSignatureBuilder builder,
                            @CachedLibrary("builder") NFIBackendSignatureBuilderLibrary self) {
                CachedSignatureInfo sigInfo = prepareSignatureInfo(builder.retType, builder.argsState, builder.descriptor);

                return create(PanamaNFIContext.get(self), sigInfo, builder.retType, builder.argTypes.getFinalArray(), builder.downcallType, builder.upcallType);
            }
        }
    }

    @TruffleBoundary
    @SuppressWarnings("unused")
    public static CachedSignatureInfo prepareSignatureInfo(PanamaType retType, ArgsState state, FunctionDescriptor functionDescriptor) {
        PanamaType[] argTypes = new PanamaType[state.argCount];
        ArgsState curState = state;
        for (int i = state.argCount - 1; i >= 0; i--) {
            argTypes[i] = curState.lastArg;
            curState = curState.prev;
        }
        return new CachedSignatureInfo(PanamaNFILanguage.get(null), retType, argTypes, functionDescriptor);
    }

    static final class ArgsState {

        static final ArgsState NO_ARGS = new ArgsState(0, null, null);

        final int argCount;

        final PanamaType lastArg;
        final ArgsState prev;

        public ArgsState(int argCount, PanamaType lastArg, ArgsState prev) {
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

        CachedSignatureInfo(PanamaNFILanguage language, PanamaType retType, PanamaType[] argTypes, FunctionDescriptor functionDescriptor) {
            this.retType = retType;
            this.argTypes = argTypes;
            this.functionDescriptor = functionDescriptor;
            this.callTarget = SignatureExecuteNodeGen.create(language, this).getCallTarget();
        }

        PanamaType[] getArgTypes() {
            return argTypes;
        }

        PanamaType getRetType() {
            return retType;
        }

        FunctionDescriptor getFunctionDescriptor() { return functionDescriptor; }

        Object execute(PanamaSignature signature, PanamaNFIContext ctx, Object[] args, MethodHandle handle) {
            assert signature.signatureInfo == this;
            CompilerAsserts.partialEvaluationConstant(retType);

            try {
                Object result = (Object) handle.invokeExact(args);
                if (result == null) {
                    return NativePointer.NULL;
                } else if (retType.type == NativeSimpleType.STRING) {
                    MemoryAddress test = (MemoryAddress) result;
                    return new NativeString(test.toRawLongValue());
                } else if (retType.type == NativeSimpleType.VOID) {
                    return NativePointer.NULL; // TODO check this vs NativePointer.NULL
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
    }
}
