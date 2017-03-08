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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.CanResolve;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.nfi.NativePointerMessageResolutionFactory.SignatureCacheNodeGen;
import com.oracle.truffle.nfi.types.NativeSignature;
import com.oracle.truffle.nfi.types.Parser;

@MessageResolution(language = NFILanguage.class, receiverType = NativePointer.class)
class NativePointerMessageResolution {

    abstract static class SignatureCacheNode extends Node {

        protected abstract LibFFISignature execute(Object signature);

        @Specialization(guards = "checkSignature(signature, cachedSignature)")
        @SuppressWarnings("unused")
        protected LibFFISignature cached(String signature,
                        @Cached("signature") String cachedSignature,
                        @Cached("parse(signature)") LibFFISignature ret) {
            return ret;
        }

        @Specialization(replaces = "cached")
        @TruffleBoundary
        protected LibFFISignature parse(String signature) {
            NativeSignature parsed = Parser.parseSignature(signature);
            return LibFFISignature.create(parsed);
        }

        protected static boolean checkSignature(String signature, String cachedSignature) {
            return signature.equals(cachedSignature);
        }
    }

    @Resolve(message = "INVOKE")
    abstract static class BindNode extends Node {

        @Child protected SignatureCacheNode signatureCache = SignatureCacheNodeGen.create();

        public TruffleObject access(NativePointer receiver, String method, Object[] args) {
            if (!"bind".equals(method)) {
                throw UnknownIdentifierException.raise(method);
            }
            if (args.length != 1) {
                throw ArityException.raise(1, args.length);
            }

            LibFFISignature signature = signatureCache.execute(args[0]);
            if (receiver.nativePointer == 0) {
                // cannot bind null function
                return receiver;
            } else {
                return new LibFFIFunction(receiver, signature);
            }
        }
    }

    @Resolve(message = "UNBOX")
    abstract static class UnboxNativePointerNode extends Node {

        public long access(NativePointer receiver) {
            return receiver.nativePointer;
        }
    }

    @Resolve(message = "IS_BOXED")
    abstract static class IsBoxedNativePointerNode extends Node {

        @SuppressWarnings("unused")
        public boolean access(NativePointer receiver) {
            return true;
        }
    }

    @Resolve(message = "IS_NULL")
    abstract static class IsNullNativePointerNode extends Node {

        public boolean access(NativePointer receiver) {
            return receiver.nativePointer == 0;
        }
    }

    @CanResolve
    abstract static class CanResolveNativePointerNode extends Node {

        public boolean test(TruffleObject receiver) {
            return receiver instanceof NativePointer;
        }
    }
}
