/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.nfi.impl;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;

@GenerateUncached
@ImportStatic(NFILanguageImpl.class)
abstract class FunctionExecuteNode extends Node {

    public abstract Object execute(LibFFIFunction receiver, Object[] args) throws ArityException, UnsupportedTypeException;

    @ExplodeLoop
    @Specialization(guards = "checkSignature(receiver, signature)")
    protected Object cachedSignature(LibFFIFunction receiver, Object[] args, @Cached("receiver.getSignature()") LibFFISignature signature,
            @Cached("getNativeArgumentLibraries(signature)") NativeArgumentLibrary[] argLibs,
            @CachedContext(NFILanguageImpl.class) NFIContext ctx) throws ArityException, UnsupportedTypeException {
        if (args.length != signature.getRealArgCount()) {
            throw ArityException.create(argLibs.length, args.length);
        }

        NativeArgumentBuffer.Array buffer = signature.prepareBuffer();
        LibFFIType[] types = signature.getArgTypes();
        assert argLibs.length == types.length;

        int argIdx = 0;
        for (int i = 0; i < argLibs.length; i++) {
            Object arg = argIdx < args.length ? args[argIdx] : null;
            argLibs[i].serialize(types[i], buffer, arg);
            if (!types[i].injectedArgument) {
                argIdx++;
            }
        }
        assert argIdx == args.length : "SerializeArgumentNodes didn't consume all arguments";

        CompilerDirectives.ensureVirtualized(buffer);
        return signature.execute(ctx, receiver.getAddress(), buffer);
    }

    protected static boolean checkSignature(LibFFIFunction receiver, LibFFISignature signature) {
        return receiver.getSignature() == signature;
    }

    protected static NativeArgumentLibrary[] getNativeArgumentLibraries(LibFFISignature signature) {
        LibFFIType[] argTypes = signature.getArgTypes();
        NativeArgumentLibrary[] ret = new NativeArgumentLibrary[argTypes.length];
        for (int i = 0; i < argTypes.length; i++) {
            ret[i] = NativeArgumentLibrary.getFactory().create(argTypes[i]);
        }
        return ret;
    }

    @ExplodeLoop
    @Specialization(replaces = "cachedSignature", guards = "receiver.getSignature().getArgTypes().length == libs.length")
    protected Object cachedArgCount(LibFFIFunction receiver, Object[] args,
            @Cached("getGenericNativeArgumentLibraries(receiver.getSignature().getArgTypes().length)") NativeArgumentLibrary[] libs,
            @CachedContext(NFILanguageImpl.class) NFIContext ctx) throws ArityException, UnsupportedTypeException {
        LibFFISignature signature = receiver.getSignature();
        LibFFIType[] argTypes = signature.getArgTypes();

        NativeArgumentBuffer.Array buffer = signature.prepareBuffer();
        int argIdx = 0;
        for (int i = 0; i < libs.length; i++) {
            if (argIdx >= args.length) {
                raiseArityException(argTypes, args.length);
            }

            if (argTypes[i].injectedArgument) {
                libs[i].serialize(argTypes[i], buffer, null);
            } else {
                libs[i].serialize(argTypes[i], buffer, args[argIdx++]);
            }
        }

        if (argIdx != args.length) {
            throw ArityException.create(argIdx, args.length);
        }

        return slowPathExecute(ctx, signature, receiver.getAddress(), buffer);
    }

    private static void raiseArityException(LibFFIType[] argTypes, int actualArgCount) throws ArityException {
        CompilerDirectives.transferToInterpreter();
        int expectedArgCount = 0;
        for (LibFFIType argType : argTypes) {
            if (!argType.injectedArgument) {
                expectedArgCount++;
            }
        }
        throw ArityException.create(expectedArgCount, actualArgCount);
    }

    protected static NativeArgumentLibrary[] getGenericNativeArgumentLibraries(int argCount) {
        NativeArgumentLibrary[] ret = new NativeArgumentLibrary[argCount];
        for (int i = 0; i < argCount; i++) {
            ret[i] = NativeArgumentLibrary.getFactory().createDispatched(5);
        }
        return ret;
    }

    @Specialization(replaces = "cachedArgCount")
    static Object genericExecute(LibFFIFunction receiver, Object[] args,
            @CachedLibrary(limit = "5") NativeArgumentLibrary nativeArguments,
            @CachedContext(NFILanguageImpl.class) NFIContext ctx) throws ArityException, UnsupportedTypeException {
        LibFFISignature signature = receiver.getSignature();
        LibFFIType[] argTypes = signature.getArgTypes();

        NativeArgumentBuffer.Array buffer = signature.prepareBuffer();
        int argIdx = 0;
        for (int i = 0; i < argTypes.length; i++) {
            Object arg;
            if (argTypes[i].injectedArgument) {
                arg = null;
            } else {
                if (argIdx >= args.length) {
                    raiseArityException(argTypes, args.length);
                }
                arg = args[argIdx++];
            }
            nativeArguments.serialize(argTypes[i], buffer, arg);
        }

        if (argIdx != args.length) {
            throw ArityException.create(argIdx, args.length);
        }

        return slowPathExecute(ctx, signature, receiver.getAddress(), buffer);
    }

    @TruffleBoundary
    static Object slowPathExecute(NFIContext ctx, LibFFISignature signature, long functionPointer, NativeArgumentBuffer.Array buffer) {
        return signature.execute(ctx, functionPointer, buffer);
    }
}
