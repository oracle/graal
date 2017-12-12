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
package com.oracle.truffle.nfi.impl;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.nfi.impl.BindSignatureNodeFactory.PointerBindSignatureNodeGen;
import com.oracle.truffle.nfi.impl.BindSignatureNodeFactory.SignatureCacheNodeGen;
import com.oracle.truffle.nfi.impl.TypeConversion.AsStringNode;
import com.oracle.truffle.nfi.impl.TypeConversionFactory.AsStringNodeGen;
import com.oracle.truffle.nfi.types.NativeSignature;
import com.oracle.truffle.nfi.types.Parser;

abstract class BindSignatureNode extends Node {

    abstract TruffleObject execute(TruffleObject receiver, Object signature);

    abstract static class SignatureCacheNode extends Node {

        private final ContextReference<NFIContext> ctxRef = NFILanguageImpl.getCurrentContextReference();

        protected abstract LibFFISignature execute(String signature);

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
            return LibFFISignature.create(ctxRef.get(), parsed);
        }

        protected static boolean checkSignature(String signature, String cachedSignature) {
            return signature.equals(cachedSignature);
        }
    }

    abstract static class PointerBindSignatureNode extends BindSignatureNode {

        @Child protected SignatureCacheNode signatureCache = SignatureCacheNodeGen.create();
        @Child protected AsStringNode asString = AsStringNodeGen.create(false);

        @Specialization(guards = "checkNull(receiver)")
        TruffleObject doNull(NativePointer receiver, @SuppressWarnings("unused") Object signature) {
            return receiver;
        }

        @Specialization(guards = "!checkNull(receiver)")
        TruffleObject doFunction(NativePointer receiver, Object signature) {
            String sigString = asString.execute(signature);
            LibFFISignature nativeSignature = signatureCache.execute(sigString);
            return new LibFFIFunction(receiver, nativeSignature);
        }

        protected static boolean checkNull(NativePointer pointer) {
            return pointer.nativePointer == 0;
        }
    }

    abstract static class ReBindSignatureNode extends BindSignatureNode {

        @Child protected BindSignatureNode bind = PointerBindSignatureNodeGen.create();

        @Specialization
        TruffleObject rebind(LibFFIFunction function, Object signature) {
            return bind.execute(function.getPointer(), signature);
        }
    }
}
