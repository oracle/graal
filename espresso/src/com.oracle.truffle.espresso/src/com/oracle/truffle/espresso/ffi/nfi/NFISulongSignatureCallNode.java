/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.espresso.ffi.nfi;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.espresso.ffi.NativeSignature;
import com.oracle.truffle.espresso.ffi.SignatureCallNode;
import com.oracle.truffle.nfi.api.SignatureLibrary;

public abstract class NFISulongSignatureCallNode extends SignatureCallNode {
    protected static final int LIMIT = 2;
    protected final NativeSignature signature;
    private final NFISulongNativeAccess access;

    protected NFISulongSignatureCallNode(NFISulongNativeAccess access, NativeSignature signature) {
        this.access = access;
        this.signature = signature;
    }

    public static SignatureCallNode create(NFISulongNativeAccess access, NativeSignature signature) {
        return NFISulongSignatureCallNodeGen.create(access, signature);
    }

    protected abstract Object execute(Object functionPointer, Object... args) throws ArityException, UnsupportedTypeException, UnsupportedMessageException;

    @Override
    public Object call(Object functionPointer, Object... args) throws ArityException, UnsupportedTypeException, UnsupportedMessageException {
        return execute(functionPointer, args);
    }

    protected static boolean isFallbackSymbol(Object symbol, InteropLibrary interop) {
        return NFISulongNativeAccess.isFallbackSymbol((TruffleObject) symbol, interop);
    }

    protected Object getCallableSignature(boolean forFallbackSymbol) {
        return access.getCallableSignature(signature, forFallbackSymbol);
    }

    @Specialization(limit = "LIMIT", guards = "isFallbackSymbol(functionPointer, symbolInterop) == isFallbackSymbol")
    static Object doCached(Object functionPointer, Object[] args,
                    @SuppressWarnings("unused") @CachedLibrary("functionPointer") InteropLibrary symbolInterop,
                    @SuppressWarnings("unused") @Cached("isFallbackSymbol(functionPointer, symbolInterop)") boolean isFallbackSymbol,
                    @Cached("getCallableSignature(isFallbackSymbol)") Object callableSignature,
                    @CachedLibrary("signature") SignatureLibrary signatureLibrary) throws ArityException, UnsupportedTypeException, UnsupportedMessageException {
        return signatureLibrary.call(callableSignature, functionPointer, args);
    }

    @Specialization
    Object doUncached(Object functionPointer, Object[] args) throws ArityException, UnsupportedTypeException, UnsupportedMessageException {
        TruffleObject target = (TruffleObject) functionPointer;
        return access.callSignature(getCallableSignature(access.isFallbackSymbol(target)), target, args);
    }
}
