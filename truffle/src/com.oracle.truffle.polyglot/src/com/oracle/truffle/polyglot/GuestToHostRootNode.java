/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.polyglot;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.nodes.EncapsulatingNodeReference;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

abstract class GuestToHostRootNode extends RootNode {

    protected static final int ARGUMENT_OFFSET = 2;

    private final String boundaryName;

    protected GuestToHostRootNode(Class<?> targetType, String methodName) {
        super(null);
        this.boundaryName = targetType.getName() + "." + methodName;
    }

    @Override
    protected boolean isInstrumentable() {
        return false;
    }

    @Override
    public boolean isCloningAllowed() {
        return false;
    }

    @Override
    public final String getName() {
        return boundaryName;
    }

    @SuppressWarnings("deprecation")
    @Override
    public Object execute(VirtualFrame frame) {
        Object[] arguments = frame.getArguments();
        try {
            return executeImpl(arguments[1], arguments);
        } catch (InteropException e) {
            throw silenceException(RuntimeException.class, e);
        } catch (Throwable e) {
            throw PolyglotImpl.hostToGuestException((PolyglotLanguageContext) arguments[0], e);
        }
    }

    @SuppressWarnings({"unchecked", "unused"})
    static <E extends Throwable> RuntimeException silenceException(Class<E> type, Throwable ex) throws E {
        throw (E) ex;
    }

    protected abstract Object executeImpl(Object receiver, Object[] arguments) throws InteropException;

    static CallTarget createGuestToHost(GuestToHostRootNode rootNode) {
        return Truffle.getRuntime().createCallTarget(rootNode);
    }

    static Object guestToHostCall(Node node, CallTarget target, Object... arguments) {
        Node encapsulatingNode;
        if (node.isAdoptable()) {
            encapsulatingNode = node;
        } else {
            encapsulatingNode = EncapsulatingNodeReference.getCurrent().get();
        }
        return EngineAccessor.RUNTIME.callInlined(encapsulatingNode, target, arguments);
    }

}
