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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.nfi.types.NativeSignature;
import com.oracle.truffle.nfi.types.Parser;

@GenerateUncached
abstract class BindSignatureNode extends Node {

    abstract TruffleObject execute(NativePointer receiver, Object signature) throws UnsupportedTypeException;

    @GenerateUncached
    @ImportStatic(NFILanguageImpl.class)
    abstract static class SignatureCacheNode extends Node {

        protected abstract LibFFISignature execute(String signature);

        @Specialization(guards = "checkSignature(signature, cachedSignature)")
        @SuppressWarnings("unused")
        protected LibFFISignature cached(String signature,
                        @Cached("signature") String cachedSignature,
                        @Cached("parse(signature)") LibFFISignature ret) {
            return ret;
        }

        @Specialization(replaces = "cached")
        protected static LibFFISignature uncached(String signature,
                        @CachedContext(NFILanguageImpl.class) NFIContext ctx) {
            return parse(signature, ctx);
        }

        protected LibFFISignature parse(String signature) {
            return parse(signature, getContextSupplier(NFILanguageImpl.class).get());
        }

        @TruffleBoundary
        protected static LibFFISignature parse(String signature, NFIContext ctx) {
            NativeSignature parsed = Parser.parseSignature(signature);
            return LibFFISignature.create(ctx, parsed);
        }

        protected static boolean checkSignature(String signature, String cachedSignature) {
            return signature.equals(cachedSignature);
        }
    }

    @Specialization(guards = "checkNull(receiver)")
    static TruffleObject doNull(NativePointer receiver, @SuppressWarnings("unused") Object signature) {
        return receiver;
    }

    @Specialization(limit = "3", guards = "!checkNull(receiver)")
    static TruffleObject doFunction(NativePointer receiver, Object signature,
                    @Cached SignatureCacheNode signatureCache,
                    @Cached BranchProfile exceptionProfile,
                    @CachedLibrary("signature") InteropLibrary strings) throws UnsupportedTypeException {
        try {
            String sigString = strings.asString(signature);
            LibFFISignature nativeSignature = signatureCache.execute(sigString);
            return new LibFFIFunction(receiver, nativeSignature);
        } catch (UnsupportedMessageException ex) {
            exceptionProfile.enter();
            throw UnsupportedTypeException.create(new Object[]{signature});
        }
    }

    protected static boolean checkNull(NativePointer pointer) {
        return pointer.nativePointer == 0;
    }
}
