/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.nfi.spi;

import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.GenerateLibrary;
import com.oracle.truffle.api.library.GenerateLibrary.Abstract;
import com.oracle.truffle.api.library.Library;
import com.oracle.truffle.nfi.spi.types.NativeSignature;

/**
 * Library that specifies the protocol between the Truffle NFI and its backend implementations.
 * Symbols contained in native libraries should implement this library.
 *
 * @see NFIBackendTools#createBindableSymbol
 */
@GenerateLibrary
@SuppressWarnings("unused")
public abstract class NativeSymbolLibrary extends Library {

    /**
     * Returns <code>true</code> if the receiver is a native symbol that can be bound to a signature
     * and subsequently called.
     */
    @Abstract
    public boolean isBindable(Object receiver) {
        return false;
    }

    /**
     * Prepare a signature for use by the NFI backend. This message is sent when binding a signature
     * to a receiver. The NFI backend should allocate any data structures needed for calling symbols
     * with this signature.
     *
     * The signature returned by this method will be cached, and may be reused for different
     * receivers.
     */
    @Abstract
    public Object prepareSignature(Object receiver, NativeSignature signature) throws UnsupportedMessageException, UnsupportedTypeException {
        throw UnsupportedMessageException.create();
    }

    /**
     * Call a native function with a specified signature. The signature is guaranteed to be an
     * object produced by the {@link #prepareSignature} message of this library, but not necessarily
     * with the same receiver symbol.
     */
    @Abstract
    public Object call(Object receiver, Object signature, Object... args) throws ArityException, UnsupportedMessageException, UnsupportedTypeException {
        throw UnsupportedMessageException.create();
    }
}
