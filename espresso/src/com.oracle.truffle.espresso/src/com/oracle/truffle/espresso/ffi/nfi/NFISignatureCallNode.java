/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.espresso.ffi.SignatureCallNode;
import com.oracle.truffle.nfi.api.SignatureLibrary;

public class NFISignatureCallNode extends SignatureCallNode {
    @Child SignatureLibrary signatureLibrary;
    private final Object signature;

    protected NFISignatureCallNode(Object signature) {
        this.signatureLibrary = SignatureLibrary.getFactory().create(signature);
        this.signature = signature;
    }

    public static SignatureCallNode create(Object signature) {
        return new NFISignatureCallNode(signature);
    }

    @Override
    public Object call(Object functionPointer, Object... args) throws ArityException, UnsupportedTypeException, UnsupportedMessageException {
        return signatureLibrary.call(signature, functionPointer, args);
    }
}
