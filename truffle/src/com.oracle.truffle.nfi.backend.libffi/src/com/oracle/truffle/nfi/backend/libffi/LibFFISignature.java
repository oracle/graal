/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.nfi.backend.libffi;

import java.util.Arrays;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateAOT;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
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
import com.oracle.truffle.nfi.backend.libffi.FunctionExecuteNode.SignatureExecuteNode;
import com.oracle.truffle.nfi.backend.libffi.LibFFIClosure.MonomorphicClosureInfo;
import com.oracle.truffle.nfi.backend.libffi.LibFFIClosure.PolymorphicClosureInfo;
import com.oracle.truffle.nfi.backend.libffi.LibFFIType.ArrayType;
import com.oracle.truffle.nfi.backend.libffi.LibFFIType.CachedTypeInfo;
import com.oracle.truffle.nfi.backend.libffi.LibFFIType.Direction;
import com.oracle.truffle.nfi.backend.libffi.NativeAllocation.FreeDestructor;
import com.oracle.truffle.nfi.backend.spi.NFIBackendSignatureBuilderLibrary;
import com.oracle.truffle.nfi.backend.spi.NFIBackendSignatureLibrary;
import com.oracle.truffle.nfi.backend.spi.types.NativeSimpleType;
import com.oracle.truffle.nfi.backend.spi.util.ProfiledArrayBuilder;
import com.oracle.truffle.nfi.backend.spi.util.ProfiledArrayBuilder.ArrayBuilderFactory;
import com.oracle.truffle.nfi.backend.spi.util.ProfiledArrayBuilder.ArrayFactory;

/**
 * Runtime object representing native signatures. Instances of this class can not be cached in
 * shared AST nodes, since they contain references to native datastructures of libffi.
 *
 * All information that is context and process independent is collected in a separate object,
 * {@link CachedSignatureInfo}. Two {@link LibFFISignature} objects that have the same
 * {@link CachedSignatureInfo} are guaranteed to behave the same semantically.
 */
@ExportLibrary(value = NFIBackendSignatureLibrary.class, useForAOT = true, useForAOTPriority = 1)
final class LibFFISignature {

    @TruffleBoundary
    @NeverDefault
    public static LibFFISignature create(LibFFIContext context, CachedSignatureInfo info, LibFFIType retType, int argCount, int fixedArgCount, LibFFIType[] argTypes) {
        LibFFIType realRetType = retType;
        if (retType == null) {
            realRetType = context.lookupSimpleType(NativeSimpleType.VOID);
        }

        long cif;
        if (fixedArgCount == SignatureBuilder.NOT_VARARGS) {
            cif = context.prepareSignature(realRetType, argCount, argTypes);
        } else {
            cif = context.prepareSignatureVarargs(realRetType, argCount, fixedArgCount, argTypes);
        }
        if (cif == 0) {
            throw CompilerDirectives.shouldNotReachHere(String.format("invalid signature (ret: %s, argCount: %d, fixedArgCount: %d, args: %s)",
                            retType, argCount, fixedArgCount, Arrays.toString(argTypes)));
        }
        return create(cif, info);
    }

    private static LibFFISignature create(long cif, CachedSignatureInfo info) {
        assert cif > 0;
        LibFFISignature ret = new LibFFISignature(cif, info);
        NativeAllocation.getGlobalQueue().registerNativeAllocation(ret, new FreeDestructor(cif));
        return ret;
    }

    @ExportMessage
    static class Call {

        @Specialization
        static Object callLibFFI(LibFFISignature self, LibFFISymbol functionPointer, Object[] args,
                        @Bind("$node") Node node,
                        @Cached.Exclusive @Cached FunctionExecuteNode functionExecute) throws ArityException, UnsupportedTypeException {
            long pointer = functionPointer.asPointer();
            return functionExecute.execute(node, pointer, self, args);
        }

        @Specialization(limit = "3")
        @GenerateAOT.Exclude
        static Object callGeneric(LibFFISignature self, Object functionPointer, Object[] args,
                        @CachedLibrary("functionPointer") InteropLibrary interop,
                        @Bind("$node") Node node,
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
            return functionExecute.execute(node, pointer, self, args);
        }
    }

    @ExportMessage
    @ImportStatic(LibFFILanguage.class)
    static class CreateClosure {

        @Specialization(guards = {"signature.signatureInfo == cachedSignatureInfo", "executable == cachedExecutable"}, assumptions = "getSingleContextAssumption()", limit = "3")
        static LibFFIClosure doCachedExecutable(LibFFISignature signature, Object executable,
                        @Cached("signature.signatureInfo") CachedSignatureInfo cachedSignatureInfo,
                        @Cached("executable") Object cachedExecutable,
                        @CachedLibrary("signature") NFIBackendSignatureLibrary self,
                        @Cached("create(cachedSignatureInfo, cachedExecutable)") MonomorphicClosureInfo cachedClosureInfo) {
            assert signature.signatureInfo == cachedSignatureInfo && executable == cachedExecutable;
            // no need to cache duplicated allocation in the single-context case
            // the NFI frontend is taking care of that already
            ClosureNativePointer nativePointer = cachedClosureInfo.allocateClosure(LibFFIContext.get(self), signature);
            return LibFFIClosure.newClosureWrapper(nativePointer);
        }

        @Specialization(replaces = "doCachedExecutable", guards = "signature.signatureInfo == cachedSignatureInfo", limit = "3")
        static LibFFIClosure doCachedSignature(LibFFISignature signature, Object executable,
                        @Cached("signature.signatureInfo") CachedSignatureInfo cachedSignatureInfo,
                        @CachedLibrary("signature") NFIBackendSignatureLibrary self,
                        @Cached("create(cachedSignatureInfo)") PolymorphicClosureInfo cachedClosureInfo) {
            assert signature.signatureInfo == cachedSignatureInfo;
            ClosureNativePointer nativePointer = cachedClosureInfo.allocateClosure(LibFFIContext.get(self), signature, executable);
            return LibFFIClosure.newClosureWrapper(nativePointer);
        }

        @TruffleBoundary
        @Specialization(replaces = "doCachedSignature")
        static LibFFIClosure createClosure(LibFFISignature signature, Object executable,
                        @CachedLibrary("signature") NFIBackendSignatureLibrary self) {
            PolymorphicClosureInfo cachedClosureInfo = signature.signatureInfo.getCachedClosureInfo();
            ClosureNativePointer nativePointer = cachedClosureInfo.allocateClosure(LibFFIContext.get(self), signature, executable);
            return LibFFIClosure.newClosureWrapper(nativePointer);
        }
    }

    @TruffleBoundary
    @NeverDefault
    public static CachedSignatureInfo prepareSignatureInfo(CachedTypeInfo retTypeInfo, ArgsState state) {
        if (retTypeInfo instanceof ArrayType) {
            throw new IllegalArgumentException("array type as return value is not supported");
        }

        boolean allowJavaToNativeCall = state.allowJavaToNativeCall;
        boolean allowNativeToJavaCall = state.allowNativeToJavaCall;

        switch (retTypeInfo.allowedDataFlowDirection) {
            /*
             * If the call goes from Java to native, the return value flows from native-to-Java, and
             * vice-versa.
             */
            case JAVA_TO_NATIVE_ONLY:
                allowJavaToNativeCall = false;
                break;
            case NATIVE_TO_JAVA_ONLY:
                allowNativeToJavaCall = false;
                break;
        }

        Direction allowedCallDirection;
        if (allowNativeToJavaCall) {
            if (allowJavaToNativeCall) {
                allowedCallDirection = Direction.BOTH;
            } else {
                allowedCallDirection = Direction.NATIVE_TO_JAVA_ONLY;
            }
        } else {
            if (allowJavaToNativeCall) {
                allowedCallDirection = Direction.JAVA_TO_NATIVE_ONLY;
            } else {
                throw new IllegalArgumentException("invalid signature");
            }
        }

        CachedTypeInfo[] argTypesInfo = new CachedTypeInfo[state.argCount];
        ArgsState curState = state;
        for (int i = state.argCount - 1; i >= 0; i--) {
            argTypesInfo[i] = curState.lastArg;
            curState = curState.prev;
        }

        return new CachedSignatureInfo(LibFFILanguage.get(null), retTypeInfo, argTypesInfo, state.primitiveSize, state.objectCount, allowedCallDirection);
    }

    private final long cif; // native pointer
    final CachedSignatureInfo signatureInfo;

    private LibFFISignature(long cif, CachedSignatureInfo signatureInfo) {
        this.cif = cif;
        this.signatureInfo = signatureInfo;
    }

    /**
     * This class contains all information about native signatures that can be shared across
     * contexts. Instances of this class can safely be cached in shared AST. This contains a shared
     * {@link CallTarget} that can be used to call functions of that signature.
     */
    static final class CachedSignatureInfo {

        final CachedTypeInfo retType;
        @CompilationFinal(dimensions = 1) final CachedTypeInfo[] argTypes;

        final int primitiveSize;
        final int objectCount;

        final Direction allowedCallDirection;

        final CallTarget callTarget;

        PolymorphicClosureInfo cachedClosureInfo;

        CachedSignatureInfo(LibFFILanguage language, CachedTypeInfo retType, CachedTypeInfo[] argTypes, int primitiveSize, int objectCount, Direction allowedCallDirection) {
            this.retType = retType;
            this.argTypes = argTypes;
            this.primitiveSize = primitiveSize;
            this.objectCount = objectCount;
            this.allowedCallDirection = allowedCallDirection;

            this.callTarget = new SignatureExecuteNode(language, this).getCallTarget();
        }

        NativeArgumentBuffer.Array prepareBuffer() {
            return new NativeArgumentBuffer.Array(primitiveSize, objectCount);
        }

        CachedTypeInfo[] getArgTypes() {
            return argTypes;
        }

        CachedTypeInfo getRetType() {
            return retType;
        }

        Direction getAllowedCallDirection() {
            return allowedCallDirection;
        }

        Object execute(Node node, LibFFISignature signature, LibFFIContext ctx, long functionPointer, NativeArgumentBuffer.Array argBuffer) {
            assert signature.signatureInfo == this;
            CachedTypeInfo localRetType = retType;
            CompilerAsserts.partialEvaluationConstant(localRetType);
            if (localRetType == null) {
                throw CompilerDirectives.shouldNotReachHere();
            } else if (localRetType instanceof LibFFIType.ObjectType) {
                Object ret = ctx.executeObject(signature.cif, functionPointer, argBuffer.prim, argBuffer.getPatchCount(), argBuffer.patches, argBuffer.objects);
                if (ret == null) {
                    return NativePointer.NULL;
                } else {
                    return ret;
                }
            } else if (localRetType instanceof LibFFIType.SimpleType) {
                LibFFIType.SimpleType simpleType = (LibFFIType.SimpleType) localRetType;
                long ret = ctx.executePrimitive(signature.cif, functionPointer, argBuffer.prim, argBuffer.getPatchCount(), argBuffer.patches, argBuffer.objects);
                return simpleType.fromPrimitive(ret);
            } else {
                NativeArgumentBuffer.Array retBuffer = new NativeArgumentBuffer.Array(localRetType.size, localRetType.objectCount);
                ctx.executeNative(signature.cif, functionPointer, argBuffer.prim, argBuffer.getPatchCount(), argBuffer.patches, argBuffer.objects, retBuffer.prim);
                return localRetType.deserializeRet(node, retBuffer);
            }
        }

        @TruffleBoundary
        private synchronized void initCachedClosureInfo() {
            if (cachedClosureInfo == null) {
                cachedClosureInfo = PolymorphicClosureInfo.create(this);
            }
        }

        @NeverDefault
        PolymorphicClosureInfo getCachedClosureInfo() {
            if (cachedClosureInfo == null) {
                initCachedClosureInfo();
            }
            assert cachedClosureInfo != null;
            return cachedClosureInfo;
        }
    }

    static final class ArgsState {

        static final ArgsState NO_ARGS = new ArgsState(0, 0, 0, true, true, null, null);

        final int argCount;

        final int primitiveSize;
        final int objectCount;

        final boolean allowJavaToNativeCall;
        final boolean allowNativeToJavaCall;

        final CachedTypeInfo lastArg;
        final ArgsState prev;

        ArgsState(int argCount, int primitiveSize, int objectCount, boolean allowJavaToNativeCall, boolean allowNativeToJavaCall, CachedTypeInfo lastArg, ArgsState prev) {
            this.argCount = argCount;
            this.primitiveSize = primitiveSize;
            this.objectCount = objectCount;
            this.allowJavaToNativeCall = allowJavaToNativeCall;
            this.allowNativeToJavaCall = allowNativeToJavaCall;
            this.lastArg = lastArg;
            this.prev = prev;
        }

        @NeverDefault
        ArgsState addArg(CachedTypeInfo typeInfo) {
            if (typeInfo instanceof LibFFIType.VoidType) {
                throw new IllegalArgumentException("void is not a valid argument type");
            }

            boolean newAllowNativeToJavaCall = this.allowNativeToJavaCall;
            boolean newAllowJavaToNativeCall = this.allowJavaToNativeCall;
            switch (typeInfo.allowedDataFlowDirection) {
                case JAVA_TO_NATIVE_ONLY:
                    newAllowNativeToJavaCall = false;
                    break;
                case NATIVE_TO_JAVA_ONLY:
                    newAllowJavaToNativeCall = false;
                    break;
            }

            int align = typeInfo.alignment;
            int newPrimitiveSize = this.primitiveSize;
            if (primitiveSize % align != 0) {
                newPrimitiveSize += align - (primitiveSize % align);
            }
            newPrimitiveSize += typeInfo.size;

            int newObjectCount = this.objectCount + typeInfo.objectCount;

            return new ArgsState(argCount + 1, newPrimitiveSize, newObjectCount, newAllowJavaToNativeCall, newAllowNativeToJavaCall, typeInfo, this);
        }
    }

    @ExportLibrary(NFIBackendSignatureBuilderLibrary.class)
    static final class SignatureBuilder {

        static final int NOT_VARARGS = -1;

        @NeverDefault ArgsState state;
        CachedTypeInfo retTypeInfo;

        LibFFIType retType;
        ProfiledArrayBuilder<LibFFIType> argTypes;

        int fixedArgCount;

        private static final ArrayFactory<LibFFIType> FACTORY = new ArrayFactory<>() {

            @Override
            public LibFFIType[] create(int size) {
                return new LibFFIType[size];
            }
        };

        SignatureBuilder(ArrayBuilderFactory factory) {
            this.state = ArgsState.NO_ARGS;
            this.fixedArgCount = NOT_VARARGS;
            this.argTypes = factory.allocate(FACTORY);
        }

        void addArg(LibFFIType arg, ArgsState newState) {
            assert state.argCount + 1 == newState.argCount;
            argTypes.add(arg);
            state = newState;
        }

        LibFFIType maybePromote(Node node, LibFFIType argType) {
            if (fixedArgCount == NOT_VARARGS) {
                return argType;
            } else {
                return argType.varargsPromoteType(node);
            }
        }

        @ExportMessage
        static class SetReturnType {

            @Specialization
            static void doSet(SignatureBuilder builder, LibFFIType retType) {
                builder.retType = retType;
                builder.retTypeInfo = retType.typeInfo;
            }
        }

        @ExportMessage
        static class AddArgument {

            static boolean isSame(CachedTypeInfo v0, CachedTypeInfo v1) {
                return v0 == v1;
            }

            @Specialization(guards = {"builder.state == oldState", "isSame(promotedType.typeInfo, cachedTypeInfo)"}, limit = "1")
            static void doCached(SignatureBuilder builder, @SuppressWarnings("unused") LibFFIType argType,
                            @SuppressWarnings("unused") @CachedLibrary("builder") NFIBackendSignatureBuilderLibrary self,
                            @Bind("builder.maybePromote(self, argType)") LibFFIType promotedType,
                            @Cached("builder.state") ArgsState oldState,
                            @Cached("promotedType.typeInfo") CachedTypeInfo cachedTypeInfo,
                            @Cached("oldState.addArg(cachedTypeInfo)") ArgsState newState) {
                assert builder.state == oldState && promotedType.typeInfo == cachedTypeInfo;
                builder.addArg(promotedType, newState);
            }

            @Specialization(replaces = "doCached")
            static void doGeneric(SignatureBuilder builder, LibFFIType argType,
                            @CachedLibrary("builder") NFIBackendSignatureBuilderLibrary self) {
                LibFFIType promotedType = builder.maybePromote(self, argType);
                ArgsState newState = builder.state.addArg(promotedType.typeInfo);
                builder.addArg(promotedType, newState);
            }
        }

        @ExportMessage
        void makeVarargs() {
            fixedArgCount = state.argCount;
        }

        @ExportMessage
        @ImportStatic(LibFFISignature.class)
        static class Build {

            @Specialization(guards = {"builder.state == cachedState", "builder.retTypeInfo == cachedRetType"}, limit = "1")
            static Object doCached(SignatureBuilder builder,
                            @Cached("builder.state") ArgsState cachedState,
                            @SuppressWarnings("unused") @Cached("builder.retType.typeInfo") CachedTypeInfo cachedRetType,
                            @CachedLibrary("builder") NFIBackendSignatureBuilderLibrary self,
                            @Cached("prepareSignatureInfo(cachedRetType, cachedState)") CachedSignatureInfo cachedSigInfo) {
                return create(LibFFIContext.get(self), cachedSigInfo, builder.retType, cachedState.argCount, builder.fixedArgCount, builder.argTypes.getFinalArray());
            }

            @Specialization(replaces = "doCached")
            static Object doGeneric(SignatureBuilder builder,
                            @CachedLibrary("builder") NFIBackendSignatureBuilderLibrary self) {
                CachedSignatureInfo sigInfo = prepareSignatureInfo(builder.retType.typeInfo, builder.state);
                return create(LibFFIContext.get(self), sigInfo, builder.retType, builder.state.argCount, builder.fixedArgCount, builder.argTypes.getFinalArray());
            }
        }
    }
}
