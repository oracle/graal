/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.test;

import org.graalvm.compiler.truffle.test.nodes.AbstractTestNode;
import org.graalvm.compiler.truffle.test.nodes.RootTestNode;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ExceptionType;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.Node;

public class TruffleExceptionPartialEvaluationTest extends PartialEvaluationTest {

    public static Object constant42() {
        return 42;
    }

    public static Object constant0() {
        return 0;
    }

    @Test
    public void testTruffleException() {
        assertPartialEvalEquals(TruffleExceptionPartialEvaluationTest::constant42, createCallerChain(0, 0));
        assertPartialEvalEquals(TruffleExceptionPartialEvaluationTest::constant42, createCallerChain(3, 0));
        assertPartialEvalEquals(TruffleExceptionPartialEvaluationTest::constant42, createCallerChain(0, 3));
        assertPartialEvalEquals(TruffleExceptionPartialEvaluationTest::constant42, createCallerChain(4, 4));
    }

    @Test
    public void testIsException() {
        FrameDescriptor fd = new FrameDescriptor();
        Object receiver = new TestTruffleException(TestTruffleException.UNLIMITED_STACK_TRACE, null, true);
        RootTestNode rootNode = new RootTestNode(fd, "isException", new IsExceptionNode(receiver, ExceptionType.RUNTIME_ERROR));
        assertPartialEvalEquals(TruffleExceptionPartialEvaluationTest::constant42, rootNode);

        fd = new FrameDescriptor();
        receiver = new TruffleObject() {
        };
        rootNode = new RootTestNode(fd, "isException", new IsExceptionNode(receiver, ExceptionType.RUNTIME_ERROR));
        assertPartialEvalEquals(TruffleExceptionPartialEvaluationTest::constant0, rootNode);
    }

    static RootTestNode createCallerChain(int framesAbove, int framesBelow) {
        FrameDescriptor fd = new FrameDescriptor();
        AbstractTestNode calleeNode = new ThrowTruffleExceptionTestNode(-1, true);
        RootTestNode calleeRoot = new RootTestNode(fd, "testTruffleException", calleeNode);
        for (int i = 0; i < framesAbove; i++) {
            AbstractTestNode call = new CallTestNode(calleeRoot.getCallTarget());
            calleeRoot = new RootTestNode(fd, "testTruffleException", call);
        }
        AbstractTestNode callerNode = new CallTestNode(calleeRoot.getCallTarget());
        AbstractTestNode catchNode = new CatchTruffleExceptionTestNode(callerNode);
        RootTestNode callerRoot = new RootTestNode(fd, "testTruffleException", catchNode);
        for (int i = 0; i < framesBelow; i++) {
            AbstractTestNode call = new CallTestNode(callerRoot.getCallTarget());
            callerRoot = new RootTestNode(fd, "testTruffleException", call);
        }
        return callerRoot;
    }

    @ExportLibrary(InteropLibrary.class)
    static final class TestTruffleException extends AbstractTruffleException {

        private static final long serialVersionUID = -6105288741119318027L;

        private final boolean property;

        TestTruffleException(int stackTraceElementLimit, Node location, boolean property) {
            super(null, null, stackTraceElementLimit, location);
            this.property = property;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean hasMembers() {
            return true;
        }

        @ExportMessage
        @SuppressWarnings({"unused", "static-method"})
        Object getMembers(boolean includeInternal) throws UnsupportedMessageException {
            throw UnsupportedMessageException.create();
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean isMemberReadable(String member) {
            return member.equals("property");
        }

        @ExportMessage
        Object readMember(String member) throws UnknownIdentifierException {
            if (member.equals("property")) {
                return property;
            }
            throw UnknownIdentifierException.create(member);
        }
    }

    private static class CatchTruffleExceptionTestNode extends AbstractTestNode {

        @Child private AbstractTestNode child;
        private final InteropLibrary exceptions;

        CatchTruffleExceptionTestNode(AbstractTestNode child) {
            this.child = child;
            this.exceptions = InteropLibrary.getFactory().createDispatched(3);
        }

        @Override
        public int execute(VirtualFrame frame) {
            try {
                return child.execute(frame);
            } catch (AbstractTruffleException e) {
                try {
                    if ((boolean) exceptions.readMember(e, "property")) {
                        return 42;
                    }
                } catch (UnsupportedMessageException | UnknownIdentifierException interopException) {
                    throw CompilerDirectives.shouldNotReachHere(interopException);
                }
                throw e;
            }
        }
    }

    private static class ThrowTruffleExceptionTestNode extends AbstractTestNode {

        private final int limit;
        private final boolean property;

        ThrowTruffleExceptionTestNode(int limit, boolean property) {
            this.limit = limit;
            this.property = property;
        }

        @Override
        public int execute(VirtualFrame frame) {
            throw new TestTruffleException(limit, this, property);
        }
    }

    private static class CallTestNode extends AbstractTestNode {
        @Child private DirectCallNode callNode;

        CallTestNode(CallTarget callTarget) {
            this.callNode = Truffle.getRuntime().createDirectCallNode(callTarget);
            this.callNode.forceInlining();
        }

        @Override
        public int execute(VirtualFrame frame) {
            return (int) callNode.call(new Object[0]);
        }
    }

    public static class IsExceptionNode extends AbstractTestNode {

        private final Object receiver;
        private final ExceptionType exceptionType;
        private final InteropLibrary exceptions;

        IsExceptionNode(Object receiver, ExceptionType exceptionType) {
            this.receiver = receiver;
            this.exceptionType = exceptionType;
            this.exceptions = InteropLibrary.getFactory().createDispatched(3);
        }

        @Override
        public int execute(VirtualFrame frame) {
            try {
                if (exceptions.isException(receiver) && exceptionType == exceptions.getExceptionType(receiver)) {
                    return 42;
                }
            } catch (UnsupportedMessageException e) {
                // pass
            }
            return 0;
        }
    }
}
