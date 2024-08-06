/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.nfi.ConvertTypeNode.ConvertFromNativeNode;
import com.oracle.truffle.nfi.ConvertTypeNode.ConvertToNativeNode;
import com.oracle.truffle.nfi.NFISignature.SignatureCachedState;

@ExportLibrary(InteropLibrary.class)
final class NFIClosure implements TruffleObject {

    final Object executable;
    final NFISignature signature;

    NFIClosure(Object executable, NFISignature signature) {
        this.executable = executable;
        this.signature = signature;
    }

    @ExportMessage
    boolean isExecutable(@CachedLibrary("this.executable") InteropLibrary interop) {
        return interop.isExecutable(executable);
    }

    @ExportMessage
    static class Execute {

        static DirectCallNode createDirectCall(CallTarget target) {
            DirectCallNode ret = DirectCallNode.create(target);
            ret.forceInlining();
            return ret;
        }

        @Specialization(guards = {"receiver.signature.cachedState != null", "receiver.signature.cachedState == cachedState"}, limit = "3")
        static Object doOptimizedDirect(NFIClosure receiver, Object[] args,
                        @Bind("$node") Node node,
                        @Shared @Cached InlinedBranchProfile exception,
                        @Cached("receiver.signature.cachedState") SignatureCachedState cachedState,
                        @Cached("cachedState.createOptimizedClosureCall()") CallSignatureNode call) {
            try {
                assert receiver.signature.cachedState == cachedState;
                return call.execute(receiver.signature, receiver.executable, args);
            } catch (Throwable t) {
                // closures must never actually throw, the native code wouldn't know how to unwind
                exception.enter(node);
                NFILanguage.get(node).setPendingException(t);
                return cachedState.retType.defaultValue;
            }
        }

        @Specialization(replaces = "doOptimizedDirect", guards = "receiver.signature.cachedState != null")
        static Object doOptimizedIndirect(NFIClosure receiver, Object[] args,
                        @Bind("$node") Node node,
                        @Shared @Cached InlinedBranchProfile exception,
                        @Cached IndirectCallNode call) {
            try {
                return call.call(receiver.signature.cachedState.getPolymorphicClosureCall(), receiver.signature, receiver.executable, args);
            } catch (Throwable t) {
                // closures must never actually throw, the native code wouldn't know how to unwind
                exception.enter(node);
                NFILanguage.get(node).setPendingException(t);
                return receiver.signature.retType.cachedState.defaultValue;
            }
        }

        @Specialization(guards = "receiver.signature.cachedState == null")
        static Object doSlowPath(NFIClosure receiver, Object[] args,
                        @Bind("$node") Node node,
                        @Shared @Cached InlinedBranchProfile exception,
                        @Cached ConvertFromNativeNode convertArg,
                        @Cached ConvertToNativeNode convertRet,
                        @CachedLibrary("receiver.executable") InteropLibrary interop) {
            NFISignature signature = receiver.signature;
            try {
                if (args.length != signature.nativeArgCount) {
                    exception.enter(node);
                    throw ArityException.create(signature.nativeArgCount, signature.nativeArgCount, args.length);
                }

                Object[] preparedArgs = new Object[signature.managedArgCount];
                int argIdx = 0;
                for (int i = 0; i < signature.nativeArgCount; i++) {
                    if (signature.argTypes[i].cachedState.managedArgCount == 1) {
                        preparedArgs[argIdx++] = convertArg.executeInlined(node, signature.argTypes[i], args[i]);
                    }
                }

                Object ret = interop.execute(receiver.executable, preparedArgs);
                return convertRet.executeInlined(node, signature.retType, ret);
            } catch (Throwable t) {
                // closures must never actually throw, the native code wouldn't know how to unwind
                exception.enter(node);
                NFILanguage.get(node).setPendingException(t);
                return signature.retType.cachedState.defaultValue;
            }
        }
    }
}
