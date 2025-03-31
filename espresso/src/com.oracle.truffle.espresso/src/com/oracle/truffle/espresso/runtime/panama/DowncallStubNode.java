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
package com.oracle.truffle.espresso.runtime.panama;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.espresso.ffi.NativeAccess;
import com.oracle.truffle.espresso.ffi.SignatureCallNode;
import com.oracle.truffle.espresso.jni.RawBuffer;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.panama.DowncallStubs.DowncallStub;

public class DowncallStubNode extends Node {
    private final DowncallStub downcallStub;
    @Child SignatureCallNode signatureCall;
    @Child InteropLibrary interop;

    protected DowncallStubNode(DowncallStub downcallStub, SignatureCallNode signatureCall) {
        this.downcallStub = downcallStub;
        this.signatureCall = signatureCall;
        if (downcallStub.hasCapture()) {
            this.interop = InteropLibrary.getFactory().createDispatched(2);
        }
    }

    public static DowncallStubNode create(DowncallStub stub, NativeAccess access) {
        return new DowncallStubNode(stub, access.createSignatureCall(stub.signature));
    }

    @SuppressWarnings("try") // Throwable.addSuppressed blocklisted by SVM.
    public Object call(Object[] args) {
        EspressoContext context = EspressoContext.get(this);
        Object target = downcallStub.getTarget(args, context);
        RawBuffer.Buffers bb = new RawBuffer.Buffers();
        try {
            Object result = signatureCall.call(target, downcallStub.processArgs(args, bb, context));
            if (downcallStub.hasCapture()) {
                downcallStub.captureState(args, interop, context);
            }
            return result;
        } catch (ArityException | UnsupportedTypeException | UnsupportedMessageException e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw EspressoError.shouldNotReachHere(e);
        } finally {
            bb.writeBack(context);
        }
    }
}
