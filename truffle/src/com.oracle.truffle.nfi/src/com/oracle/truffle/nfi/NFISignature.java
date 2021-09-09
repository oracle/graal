/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.nfi;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.nfi.CallSignatureNode.CachedCallSignatureNode;
import com.oracle.truffle.nfi.CallSignatureNode.CallSignatureRootNode;
import com.oracle.truffle.nfi.NFIType.TypeCachedState;
import com.oracle.truffle.nfi.api.SignatureLibrary;
import com.oracle.truffle.nfi.backend.spi.NFIBackendSignatureBuilderLibrary;
import com.oracle.truffle.nfi.backend.spi.NFIBackendSignatureLibrary;
import com.oracle.truffle.nfi.backend.spi.util.ProfiledArrayBuilder;

@ExportLibrary(SignatureLibrary.class)
final class NFISignature implements TruffleObject {

    final String backendId;
    final SignatureCachedState cachedState;

    final Object nativeSignature;

    final NFIType retType;
    final NFIType[] argTypes;

    final int nativeArgCount;
    final int managedArgCount;

    static final NFISignature NO_SIGNATURE = new NFISignature();

    /**
     * Only for NO_SIGNATURE marker object.
     */
    private NFISignature() {
        backendId = null;
        cachedState = null;
        nativeSignature = null;
        retType = null;
        argTypes = null;
        nativeArgCount = -1;
        managedArgCount = -1;
    }

    NFISignature(String backendId, SignatureCachedState cachedState, Object nativeSignature, NFIType retType, NFIType[] argTypes, int nativeArgCount, int managedArgCount) {
        this.backendId = backendId;
        this.cachedState = cachedState;
        this.nativeSignature = nativeSignature;
        this.retType = retType;
        this.argTypes = argTypes;
        this.nativeArgCount = nativeArgCount;
        this.managedArgCount = managedArgCount;
    }

    @ExportMessage
    Object call(Object function, Object[] args,
                    @Cached CachedCallSignatureNode call) throws ArityException, UnsupportedTypeException, UnsupportedMessageException {
        return call.execute(this, function, args);
    }

    @ExportMessage
    static class Bind {

        @Specialization
        static Object doSymbol(NFISignature signature, NFISymbol function) {
            return NFISymbol.createBound(signature.backendId, function.nativeSymbol, signature);
        }

        @Fallback
        static Object doOther(NFISignature signature, Object function) {
            return NFISymbol.createBound(signature.backendId, function, signature);
        }
    }

    @ExportMessage
    @ImportStatic(NFILanguage.class)
    static class CreateClosure {

        static NFIClosure createClosure(Object executable, NFISignature signature) {
            return new NFIClosure(executable, signature);
        }

        @Specialization(guards = {"executable == cachedClosure.executable", "signature == cachedClosure.signature"}, assumptions = "getSingleContextAssumption()")
        @SuppressWarnings("unused")
        static Object doCached(NFISignature signature, Object executable,
                        @Cached("createClosure(executable, signature)") NFIClosure cachedClosure,
                        @CachedLibrary("cachedClosure.signature.nativeSignature") NFIBackendSignatureLibrary lib,
                        @Cached("lib.createClosure(cachedClosure.signature.nativeSignature, cachedClosure)") Object cachedRet) {
            return cachedRet;
        }

        @Specialization(replaces = "doCached")
        static Object doCreate(NFISignature signature, Object executable,
                        @CachedLibrary("signature.nativeSignature") NFIBackendSignatureLibrary lib) {
            NFIClosure closure = new NFIClosure(executable, signature);
            return lib.createClosure(signature.nativeSignature, closure);
        }
    }

    static final class ArgsCachedState {

        static final ArgsCachedState NO_ARGS = new ArgsCachedState();

        final int nativeArgCount;
        final int managedArgCount;
        final TypeCachedState argType;

        final ArgsCachedState prev;

        private ArgsCachedState() {
            this(0, 0, null, null);
        }

        private ArgsCachedState(int nativeArgCount, int managedArgCount, TypeCachedState argType, ArgsCachedState prev) {
            this.nativeArgCount = nativeArgCount;
            this.managedArgCount = managedArgCount;
            this.argType = argType;
            this.prev = prev;
        }

        ArgsCachedState addArg(TypeCachedState type) {
            return new ArgsCachedState(nativeArgCount + 1, managedArgCount + type.managedArgCount, type, this);
        }
    }

    static final class SignatureCachedState {

        final TypeCachedState retType;
        final ArgsCachedState args;

        private CallTarget polymorphicSignatureCall;
        private CallTarget polymorphicClosureCall;

        private SignatureCachedState(TypeCachedState retType, ArgsCachedState args) {
            this.retType = retType;
            this.args = args;
        }

        static SignatureCachedState create(SignatureBuilder builder) {
            return new SignatureCachedState(builder.retTypeState, builder.argsState);
        }

        CallSignatureNode createOptimizedSignatureCall() {
            CompilerAsserts.neverPartOfCompilation("createOptimizedSignatureCall");
            return CallSignatureNode.createOptimizedCall(retType, args);
        }

        CallSignatureNode createOptimizedClosureCall() {
            CompilerAsserts.neverPartOfCompilation("createOptimizedClosureCall");
            return CallSignatureNode.createOptimizedClosure(retType, args);
        }

        @TruffleBoundary
        private synchronized void initPolymorphicSignatureCall() {
            if (polymorphicSignatureCall == null) {
                CallSignatureNode call = createOptimizedSignatureCall();
                CallSignatureRootNode rootNode = new CallSignatureRootNode(NFILanguage.get(null), call);
                polymorphicSignatureCall = Truffle.getRuntime().createCallTarget(rootNode);
            }
        }

        CallTarget getPolymorphicSignatureCall() {
            if (polymorphicSignatureCall == null) {
                initPolymorphicSignatureCall();
            }
            assert polymorphicSignatureCall != null;
            return polymorphicSignatureCall;
        }

        @TruffleBoundary
        private synchronized void initPolymorphicClosureCall() {
            if (polymorphicClosureCall == null) {
                CallSignatureNode call = createOptimizedClosureCall();
                CallSignatureRootNode rootNode = new CallSignatureRootNode(NFILanguage.get(null), call);
                polymorphicClosureCall = Truffle.getRuntime().createCallTarget(rootNode);
            }
        }

        CallTarget getPolymorphicClosureCall() {
            if (polymorphicClosureCall == null) {
                initPolymorphicClosureCall();
            }
            assert polymorphicClosureCall != null;
            return polymorphicClosureCall;
        }
    }

    @ExportLibrary(NFIBackendSignatureBuilderLibrary.class)
    static final class SignatureBuilder {

        final String backendId;
        final Object backendBuilder;

        NFIType retType;
        ProfiledArrayBuilder<NFIType> argTypes;

        TypeCachedState retTypeState;
        ArgsCachedState argsState;

        SignatureBuilder(String backendId, Object backendBuilder, ProfiledArrayBuilder<NFIType> argTypes) {
            this.backendId = backendId;
            this.backendBuilder = backendBuilder;
            this.argsState = ArgsCachedState.NO_ARGS;
            this.argTypes = argTypes;
        }

        @ExportMessage
        void makeVarargs(
                        @CachedLibrary("this.backendBuilder") NFIBackendSignatureBuilderLibrary backendLibrary) {
            /*
             * Just forward to the NFI backend. The NFI frontend does not distinguish between
             * regular arguments and varargs arguments.
             */
            backendLibrary.makeVarargs(backendBuilder);
        }

        @ExportMessage
        static class AddArgument {

            @Specialization(guards = {"builder.argsState == prevArgsState", "type.cachedState == argState"})
            static void doCached(SignatureBuilder builder, NFIType type,
                            @Cached("builder.argsState") ArgsCachedState prevArgsState,
                            @Cached("type.cachedState") TypeCachedState argState,
                            @Cached("prevArgsState.addArg(argState)") ArgsCachedState newArgsState,
                            @CachedLibrary("builder.backendBuilder") NFIBackendSignatureBuilderLibrary backendLibrary) {
                assert builder.argsState == prevArgsState && type.cachedState == argState;
                builder.argsState = newArgsState;

                backendLibrary.addArgument(builder.backendBuilder, type.backendType);
                builder.argTypes.add(type);
            }

            @Specialization(replaces = "doCached")
            static void doGeneric(SignatureBuilder builder, NFIType type,
                            @CachedLibrary("builder.backendBuilder") NFIBackendSignatureBuilderLibrary backendLibrary) {
                builder.argsState = builder.argsState.addArg(type.cachedState);

                backendLibrary.addArgument(builder.backendBuilder, type.backendType);
                builder.argTypes.add(type);
            }
        }

        @ExportMessage
        static class SetReturnType {

            @Specialization
            static void doSet(SignatureBuilder builder, NFIType type,
                            @CachedLibrary("builder.backendBuilder") NFIBackendSignatureBuilderLibrary backendLibrary) {
                builder.retType = type;
                builder.retTypeState = type.cachedState;
                backendLibrary.setReturnType(builder.backendBuilder, type.backendType);
            }
        }

        @ExportMessage
        static class Build {

            @Specialization(guards = {"builder.argsState == cachedState.args", "builder.retTypeState == cachedState.retType"})
            static NFISignature doCached(SignatureBuilder builder,
                            @Cached("create(builder)") SignatureCachedState cachedState,
                            @CachedLibrary("builder.backendBuilder") NFIBackendSignatureBuilderLibrary backendLibrary) {
                Object nativeSignature = backendLibrary.build(builder.backendBuilder);
                return new NFISignature(builder.backendId, cachedState, nativeSignature, builder.retType,
                                builder.argTypes.getFinalArray(), cachedState.args.nativeArgCount, cachedState.args.managedArgCount);
            }

            @Specialization(replaces = "doCached")
            static NFISignature doGeneric(SignatureBuilder builder,
                            @CachedLibrary("builder.backendBuilder") NFIBackendSignatureBuilderLibrary backendLibrary) {
                Object nativeSignature = backendLibrary.build(builder.backendBuilder);
                return new NFISignature(builder.backendId, null, nativeSignature, builder.retType,
                                builder.argTypes.getFinalArray(), builder.argsState.nativeArgCount, builder.argsState.managedArgCount);
            }
        }
    }
}
