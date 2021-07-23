/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.nfi.backend.panama;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.nodes.RootNode;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

final class PanamaClosureRootNode extends RootNode {

    // Object closure(Object receiver, Object[] args)
    static final MethodType METHOD_TYPE = MethodType.methodType(Object.class, Object.class, Object[].class);

    public static MethodHandle createUpcallHandle(PanamaNFILanguage language) {
        CompilerAsserts.neverPartOfCompilation();

        RootNode upcallRoot = new PanamaClosureRootNode(language);
        CallTarget upcallTarget = upcallRoot.getCallTarget();

        return handle_CallTarget_call.bindTo(upcallTarget).asCollector(Object[].class, 2).asType(METHOD_TYPE).asVarargsCollector(Object[].class);
    }

    @Child InteropLibrary interop;

    PanamaClosureRootNode(PanamaNFILanguage language) {
        super(language);
        this.interop = InteropLibrary.getFactory().createDispatched(3);
    }

    @Override
    public Object execute(VirtualFrame frame) {
        Object receiver = frame.getArguments()[0];
        Object[] args = (Object[]) frame.getArguments()[1];
        try {
            return interop.execute(receiver, args);
        } catch (InteropException ex) {
            throw CompilerDirectives.shouldNotReachHere(ex);
        }
    }

    private static final MethodHandle handle_CallTarget_call;

    static {
        MethodType callType = MethodType.methodType(Object.class, Object[].class);
        try {
            handle_CallTarget_call = MethodHandles.lookup().findVirtual(CallTarget.class, "call", callType);
        } catch (NoSuchMethodException | IllegalAccessException ex) {
            throw CompilerDirectives.shouldNotReachHere(ex);
        }
    }
}
