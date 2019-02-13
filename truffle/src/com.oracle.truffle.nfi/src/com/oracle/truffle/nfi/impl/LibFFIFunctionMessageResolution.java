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
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.CanResolve;
import com.oracle.truffle.api.interop.KeyInfo;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.nfi.impl.BindSignatureNodeFactory.ReBindSignatureNodeGen;
import com.oracle.truffle.nfi.impl.LibFFIFunctionMessageResolutionFactory.CachedExecuteNodeGen;
import com.oracle.truffle.nfi.impl.TypeConversion.AsStringNode;
import com.oracle.truffle.nfi.impl.TypeConversionFactory.AsStringNodeGen;

@MessageResolution(receiverType = LibFFIFunction.class)
class LibFFIFunctionMessageResolution {

    abstract static class CachedExecuteNode extends Node {

        private final ContextReference<NFIContext> ctxRef = NFILanguageImpl.getCurrentContextReference();

        public abstract Object execute(LibFFIFunction receiver, Object[] args);

        @ExplodeLoop
        @Specialization(guards = "checkSignature(receiver, signature)")
        protected Object cachedSignature(LibFFIFunction receiver, Object[] args, @Cached("receiver.getSignature()") LibFFISignature signature,
                        @Cached(value = "getSerializeArgumentNodes(signature)") SerializeArgumentNode[] serializeArgs) {
            if (args.length != signature.getRealArgCount()) {
                throw ArityException.raise(serializeArgs.length, args.length);
            }

            NativeArgumentBuffer.Array buffer = signature.prepareBuffer();
            int argIdx = 0;
            for (SerializeArgumentNode serializeArg : serializeArgs) {
                Object arg = argIdx < args.length ? args[argIdx] : null;
                if (serializeArg.execute(buffer, arg)) {
                    argIdx++;
                }
            }
            assert argIdx == args.length : "SerializeArgumentNodes didn't consume all arguments";

            CompilerDirectives.ensureVirtualized(buffer);
            return signature.execute(ctxRef.get(), receiver.getAddress(), buffer);
        }

        protected static boolean checkSignature(LibFFIFunction receiver, LibFFISignature signature) {
            return receiver.getSignature() == signature;
        }

        protected static SerializeArgumentNode[] getSerializeArgumentNodes(LibFFISignature signature) {
            LibFFIType[] argTypes = signature.getArgTypes();
            SerializeArgumentNode[] ret = new SerializeArgumentNode[argTypes.length];
            for (int i = 0; i < argTypes.length; i++) {
                ret[i] = argTypes[i].createSerializeArgumentNode();
            }
            return ret;
        }

        @ExplodeLoop
        @Specialization(replaces = "cachedSignature", guards = "receiver.getSignature().getArgTypes().length == serializeArgs.length")
        protected Object cachedArgCount(LibFFIFunction receiver, Object[] args,
                        @Cached("getSlowPathSerializeArgumentNodes(receiver)") SlowPathSerializeArgumentNode[] serializeArgs) {
            LibFFISignature signature = receiver.getSignature();
            LibFFIType[] argTypes = signature.getArgTypes();

            NativeArgumentBuffer.Array buffer = signature.prepareBuffer();
            int argIdx = 0;
            for (int i = 0; i < serializeArgs.length; i++) {
                if (argIdx >= args.length) {
                    raiseArityException(argTypes, args.length);
                }

                if (argTypes[i].injectedArgument) {
                    serializeArgs[i].execute(buffer, argTypes[i], null);
                } else {
                    serializeArgs[i].execute(buffer, argTypes[i], args[argIdx++]);
                }
            }

            if (argIdx != args.length) {
                throw ArityException.raise(argIdx, args.length);
            }

            return slowPathExecute(signature, receiver.getAddress(), buffer);
        }

        private static void raiseArityException(LibFFIType[] argTypes, int actualArgCount) {
            CompilerDirectives.transferToInterpreter();
            int expectedArgCount = 0;
            for (LibFFIType argType : argTypes) {
                if (!argType.injectedArgument) {
                    expectedArgCount++;
                }
            }
            throw ArityException.raise(expectedArgCount, actualArgCount);
        }

        protected static SlowPathSerializeArgumentNode[] getSlowPathSerializeArgumentNodes(LibFFIFunction receiver) {
            int argCount = receiver.getSignature().getArgTypes().length;
            SlowPathSerializeArgumentNode[] ret = new SlowPathSerializeArgumentNode[argCount];
            for (int i = 0; i < argCount; i++) {
                ret[i] = SlowPathSerializeArgumentNodeGen.create();
            }
            return ret;
        }

        @Specialization(replaces = "cachedArgCount")
        protected Object genericExecute(LibFFIFunction receiver, Object[] args,
                        @Cached("createSlowPathSerializeArgumentNode()") SlowPathSerializeArgumentNode serializeArgs) {
            LibFFISignature signature = receiver.getSignature();
            LibFFIType[] argTypes = signature.getArgTypes();

            NativeArgumentBuffer.Array buffer = signature.prepareBuffer();
            int argIdx = 0;
            for (int i = 0; i < argTypes.length; i++) {
                if (argIdx >= args.length) {
                    raiseArityException(argTypes, args.length);
                }

                if (argTypes[i].injectedArgument) {
                    serializeArgs.execute(buffer, argTypes[i], null);
                } else {
                    serializeArgs.execute(buffer, argTypes[i], args[argIdx++]);
                }
            }

            if (argIdx != args.length) {
                throw ArityException.raise(argIdx, args.length);
            }

            return slowPathExecute(signature, receiver.getAddress(), buffer);
        }

        protected static SlowPathSerializeArgumentNode createSlowPathSerializeArgumentNode() {
            return SlowPathSerializeArgumentNodeGen.create();
        }

        @TruffleBoundary
        protected Object slowPathExecute(LibFFISignature signature, long functionPointer, NativeArgumentBuffer.Array buffer) {
            return signature.execute(ctxRef.get(), functionPointer, buffer);
        }
    }

    @Resolve(message = "EXECUTE")
    abstract static class ExecuteLibFFIFunctionNode extends Node {

        @Child CachedExecuteNode cachedNode = CachedExecuteNodeGen.create();

        public Object access(LibFFIFunction receiver, Object[] args) {
            return cachedNode.execute(receiver, args);
        }
    }

    @Resolve(message = "IS_EXECUTABLE")
    abstract static class IsExecutableLibFFIFunctionNode extends Node {

        @SuppressWarnings("unused")
        public boolean access(LibFFIFunction receiver) {
            return true;
        }
    }

    @Resolve(message = "INVOKE")
    abstract static class ReBindNode extends Node {

        @Child private BindSignatureNode bind = ReBindSignatureNodeGen.create();

        public TruffleObject access(LibFFIFunction receiver, String identifier, Object[] args) {
            if (!"bind".equals(identifier)) {
                throw UnknownIdentifierException.raise(identifier);
            }
            if (args.length != 1) {
                throw ArityException.raise(1, args.length);
            }

            return bind.execute(receiver, args[0]);
        }
    }

    @Resolve(message = "TO_NATIVE")
    abstract static class ToNativeNode extends Node {

        public NativePointer access(LibFFIFunction receiver) {
            return receiver.getPointer();
        }
    }

    @Resolve(message = "KEYS")
    abstract static class LibFFIFunctionKeysNode extends Node {

        private static final KeysArray KEYS = new KeysArray(new String[]{"bind"});

        @SuppressWarnings("unused")
        public TruffleObject access(LibFFIFunction receiver) {
            return KEYS;
        }
    }

    @Resolve(message = "KEY_INFO")
    abstract static class LibFFIFunctionKeyInfoNode extends Node {

        @Child AsStringNode asString = AsStringNodeGen.create(true);

        @SuppressWarnings("unused")
        public int access(LibFFIFunction receiver, Object identifier) {
            if ("bind".equals(asString.execute(identifier))) {
                return KeyInfo.INVOCABLE;
            } else {
                return KeyInfo.NONE;
            }
        }
    }

    @CanResolve
    abstract static class CanResolveLibFFIFunctionNode extends Node {

        public boolean test(TruffleObject receiver) {
            return receiver instanceof LibFFIFunction;
        }
    }
}
