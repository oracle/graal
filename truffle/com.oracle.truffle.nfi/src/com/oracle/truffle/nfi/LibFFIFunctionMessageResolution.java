/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.nfi;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.CanResolve;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.nfi.LibFFIFunctionMessageResolutionFactory.CachedExecuteNodeGen;

@MessageResolution(language = NFILanguage.class, receiverType = LibFFIFunction.class)
class LibFFIFunctionMessageResolution {

    abstract static class CachedExecuteNode extends Node {

        public abstract Object execute(LibFFIFunction receiver, Object[] args);

        @ExplodeLoop
        @Specialization(guards = "checkSignature(receiver, signature)")
        protected Object cachedSignature(LibFFIFunction receiver, Object[] args, @Cached("receiver.getSignature()") LibFFISignature signature,
                        @Cached(value = "getSerializeArgumentNodes(signature)") SerializeArgumentNode[] serializeArgs) {
            if (args.length != serializeArgs.length) {
                throw ArityException.raise(serializeArgs.length, args.length);
            }

            NativeArgumentBuffer.Array buffer = signature.prepareBuffer();
            for (int i = 0; i < serializeArgs.length; i++) {
                serializeArgs[i].execute(buffer, args[i]);
            }
            CompilerDirectives.ensureVirtualized(buffer);
            return signature.execute(receiver.getAddress(), buffer);
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
            if (args.length != serializeArgs.length) {
                throw ArityException.raise(serializeArgs.length, args.length);
            }

            LibFFISignature signature = receiver.getSignature();
            LibFFIType[] argTypes = signature.getArgTypes();

            NativeArgumentBuffer.Array buffer = signature.prepareBuffer();
            for (int i = 0; i < serializeArgs.length; i++) {
                serializeArgs[i].execute(buffer, argTypes[i], args[i]);
            }
            return slowPathExecute(signature, receiver.getAddress(), buffer);
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

            if (argTypes.length != args.length) {
                throw ArityException.raise(argTypes.length, args.length);
            }

            NativeArgumentBuffer.Array buffer = signature.prepareBuffer();
            for (int i = 0; i < argTypes.length; i++) {
                serializeArgs.execute(buffer, argTypes[i], args[i]);
            }
            return slowPathExecute(signature, receiver.getAddress(), buffer);
        }

        protected static SlowPathSerializeArgumentNode createSlowPathSerializeArgumentNode() {
            return SlowPathSerializeArgumentNodeGen.create();
        }

        @TruffleBoundary
        protected Object slowPathExecute(LibFFISignature signature, long functionPointer, NativeArgumentBuffer.Array buffer) {
            return signature.execute(functionPointer, buffer);
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

    @CanResolve
    abstract static class CanResolveLibFFIFunctionNode extends Node {

        public boolean test(TruffleObject receiver) {
            return receiver instanceof LibFFIFunction;
        }
    }
}
