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

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateAOT;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.nfi.CallSignatureNodeFactory.OptimizedCallClosureNodeGen;
import com.oracle.truffle.nfi.CallSignatureNodeFactory.OptimizedCallSignatureNodeGen;
import com.oracle.truffle.nfi.ConvertTypeNode.ConvertFromNativeNode;
import com.oracle.truffle.nfi.ConvertTypeNode.ConvertToNativeNode;
import com.oracle.truffle.nfi.ConvertTypeNode.OptimizedConvertTypeNode;
import com.oracle.truffle.nfi.NFISignature.ArgsCachedState;
import com.oracle.truffle.nfi.NFISignature.SignatureCachedState;
import com.oracle.truffle.nfi.NFIType.TypeCachedState;
import com.oracle.truffle.nfi.backend.spi.NFIBackendSignatureLibrary;

@GenerateAOT
abstract class CallSignatureNode extends Node {

    abstract Object execute(NFISignature signature, Object function, Object[] args) throws ArityException, UnsupportedTypeException, UnsupportedMessageException;

    @GenerateUncached
    abstract static class CachedCallSignatureNode extends CallSignatureNode {

        @Specialization(guards = {"cachedState != null", "signature.cachedState == cachedState"})
        Object doOptimizedDirect(NFISignature signature, Object function, Object[] args,
                        @Cached("signature.cachedState") SignatureCachedState cachedState,
                        @Cached("cachedState.createOptimizedSignatureCall()") CallSignatureNode call) throws ArityException, UnsupportedTypeException, UnsupportedMessageException {
            assert cachedState == signature.cachedState;
            return call.execute(signature, function, args);
        }

        @Specialization(replaces = "doOptimizedDirect", guards = "signature.cachedState != null")
        Object doOptimizedIndirect(NFISignature signature, Object function, Object[] args,
                        @Cached IndirectCallNode call) {
            return call.call(signature.cachedState.getPolymorphicSignatureCall(), signature, function, args);
        }

        @Specialization(guards = "signature.cachedState == null")
        Object doSlowPath(NFISignature signature, Object function, Object[] args,
                        @Cached BranchProfile exception,
                        @Cached ConvertToNativeNode convertArg,
                        @Cached ConvertFromNativeNode convertRet,
                        @CachedLibrary(limit = "3") NFIBackendSignatureLibrary nativeLibrary,
                        @Cached BackendSymbolUnwrapNode backendSymbolUnwrapNode) throws ArityException, UnsupportedTypeException, UnsupportedMessageException {
            if (args.length != signature.managedArgCount) {
                exception.enter();
                throw ArityException.create(signature.managedArgCount, signature.managedArgCount, args.length);
            }

            Object[] preparedArgs = new Object[signature.nativeArgCount];
            int argIdx = 0;
            for (int i = 0; i < preparedArgs.length; i++) {
                Object input = null;
                if (signature.argTypes[i].cachedState.managedArgCount == 1) {
                    input = args[argIdx++];
                }
                preparedArgs[i] = convertArg.execute(signature.argTypes[i], input);
            }

            Object ret = nativeLibrary.call(signature.nativeSignature, backendSymbolUnwrapNode.execute(function), preparedArgs);
            return convertRet.execute(signature.retType, ret);
        }
    }

    static CallSignatureNode createOptimizedCall(TypeCachedState retType, ArgsCachedState argsState) {
        return OptimizedCallSignatureNodeGen.create(retType, argsState);
    }

    static CallSignatureNode createOptimizedClosure(TypeCachedState retType, ArgsCachedState argsState) {
        return OptimizedCallClosureNodeGen.create(retType, argsState);
    }

    abstract static class OptimizedCallSignatureNode extends CallSignatureNode {

        @Child OptimizedConvertTypeNode convertRet;
        @Children final OptimizedConvertTypeNode[] convertArgs;

        private final int managedArgCount;

        OptimizedCallSignatureNode(TypeCachedState retType, ArgsCachedState argsState) {
            this.convertRet = retType.createFromNative();

            this.convertArgs = new OptimizedConvertTypeNode[argsState.nativeArgCount];
            this.managedArgCount = argsState.managedArgCount;

            ArgsCachedState cur = argsState;
            for (int i = argsState.nativeArgCount - 1; i >= 0; i--) {
                convertArgs[i] = cur.argType.createToNative();
                cur = cur.prev;
            }
        }

        @ExplodeLoop
        Object[] prepareArgs(NFISignature signature, Object[] args) throws UnsupportedTypeException {
            Object[] preparedArgs = new Object[convertArgs.length];
            int argIdx = 0;
            for (int i = 0; i < convertArgs.length; i++) {
                Object input = null;
                if (convertArgs[i].typeState.managedArgCount == 1) {
                    input = args[argIdx++];
                }
                preparedArgs[i] = convertArgs[i].execute(signature.argTypes[i], input);
            }
            assert argIdx == managedArgCount;
            return preparedArgs;
        }

        @Specialization
        Object doCall(NFISignature signature, Object function, Object[] args,
                        @Cached BranchProfile exception,
                        @CachedLibrary(limit = "1") NFIBackendSignatureLibrary backendLibrary,
                        @Cached BackendSymbolUnwrapNode backendSymbolUnwrapNode) throws ArityException, UnsupportedTypeException, UnsupportedMessageException {
            if (args.length != managedArgCount) {
                exception.enter();
                throw ArityException.create(managedArgCount, managedArgCount, args.length);
            }
            Object[] preparedArgs = prepareArgs(signature, args);
            Object ret = backendLibrary.call(signature.nativeSignature, backendSymbolUnwrapNode.execute(function), preparedArgs);
            return convertRet.execute(signature.retType, ret);
        }
    }

    abstract static class OptimizedCallClosureNode extends CallSignatureNode {

        @Child OptimizedConvertTypeNode convertRet;
        @Children final OptimizedConvertTypeNode[] convertArgs;

        final int managedArgCount;

        OptimizedCallClosureNode(TypeCachedState retType, ArgsCachedState argsState) {
            this.convertRet = retType.createToNative();

            this.convertArgs = new OptimizedConvertTypeNode[argsState.nativeArgCount];
            this.managedArgCount = argsState.managedArgCount;

            ArgsCachedState cur = argsState;
            for (int i = argsState.nativeArgCount - 1; i >= 0; i--) {
                convertArgs[i] = cur.argType.createFromNative();
                cur = cur.prev;
            }
        }

        @ExplodeLoop
        Object[] prepareArgs(NFISignature signature, Object[] args) throws UnsupportedTypeException {
            Object[] preparedArgs = new Object[managedArgCount];
            int argIdx = 0;
            for (int i = 0; i < convertArgs.length; i++) {
                if (convertArgs[i].typeState.managedArgCount == 1) {
                    preparedArgs[argIdx++] = convertArgs[i].execute(signature.argTypes[i], args[i]);
                }
            }
            assert argIdx == managedArgCount;
            return preparedArgs;
        }

        @Specialization(limit = "1")
        @GenerateAOT.Exclude
        Object doCall(NFISignature signature, Object function, Object[] args,
                        @Cached BranchProfile exception,
                        @CachedLibrary("function") InteropLibrary interop) throws ArityException, UnsupportedTypeException, UnsupportedMessageException {
            if (args.length != convertArgs.length) {
                exception.enter();
                throw ArityException.create(convertArgs.length, convertArgs.length, args.length);
            }
            Object[] preparedArgs = prepareArgs(signature, args);
            Object ret = interop.execute(function, preparedArgs);
            return convertRet.execute(signature.retType, ret);
        }
    }

    static final class CallSignatureRootNode extends RootNode {

        @Child CallSignatureNode callSignature;

        CallSignatureRootNode(NFILanguage language, CallSignatureNode callSignature) {
            super(language);
            this.callSignature = callSignature;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            NFISignature signature = (NFISignature) frame.getArguments()[0];
            Object function = frame.getArguments()[1];
            Object[] args = (Object[]) frame.getArguments()[2];
            try {
                return callSignature.execute(signature, function, args);
            } catch (ArityException | UnsupportedTypeException | UnsupportedMessageException e) {
                // The caller of the resulting CallTarget is always CachedCallSignatureNode.
                // These exceptions are declared to be thrown there.
                throw silenceException(RuntimeException.class, e);
            }
        }

        @SuppressWarnings({"unchecked", "unused"})
        static <E extends Exception> RuntimeException silenceException(Class<E> type, Exception ex) throws E {
            throw (E) ex;
        }
    }

    @GenerateAOT
    @GenerateUncached
    abstract static class BackendSymbolUnwrapNode extends Node {
        abstract Object execute(Object symbol);

        @Specialization
        Object unwrapNFISymbol(NFISymbol symbol) {
            return symbol.nativeSymbol;
        }

        @Fallback
        Object noUnwrap(Object symbol) {
            return symbol;
        }
    }
}
