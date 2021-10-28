/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.debug.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.graalvm.polyglot.Source;
import org.junit.Test;

import com.oracle.truffle.api.debug.DebugScope;
import com.oracle.truffle.api.debug.DebugStackFrame;
import com.oracle.truffle.api.debug.DebugValue;
import com.oracle.truffle.api.debug.DebuggerSession;
import com.oracle.truffle.api.debug.SuspendedEvent;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;
import java.util.Iterator;

public class DebugScopeTest extends AbstractDebugTest {

    // Local scopes are well tested by DebugStackFrameTest and others.
    @Test
    public void testTopScope() {
        final Source source = testSource("ROOT(DEFINE(function1,ROOT(\n" +
                        "  EXPRESSION()\n" +
                        "  )\n" +
                        "),\n" +
                        "DEFINE(g,ROOT(\n" +
                        "  EXPRESSION()\n" +
                        "  )\n" +
                        "),\n" +
                        "STATEMENT())\n");
        try (DebuggerSession session = startSession()) {
            session.suspendNextExecution();
            startEval(source);

            expectSuspended((SuspendedEvent event) -> {
                DebugScope topScope = session.getTopScope(event.getSourceSection().getSource().getLanguage());
                assertNotNull(topScope);
                DebugValue function1 = topScope.getDeclaredValue("function1");
                assertNotNull(function1);
                assertTrue(function1.toDisplayString().contains("Function"));
                DebugValue functionType = function1.getMetaObject();
                assertEquals("Function", functionType.toDisplayString());
                assertEquals(function1.getOriginalLanguage(), functionType.getOriginalLanguage());
                DebugValue g = topScope.getDeclaredValue("g");
                assertNotNull(g);
                assertTrue(g.toDisplayString().contains("Function"));
            });

            expectDone();
        }
    }

    @Test
    public void testNoReceiver() {
        final Source source = testSource("ROOT(DEFINE(a,ROOT(\n" +
                        "  STATEMENT())\n" +
                        "),\n" +
                        "CALL(a))\n");
        try (DebuggerSession session = startSession()) {
            session.suspendNextExecution();
            startEval(source);

            expectSuspended((SuspendedEvent event) -> {
                DebugStackFrame frame = event.getTopStackFrame();
                assertNull(frame.getScope().getReceiver());
            });
            expectDone();
        }
    }

    @Test
    public void testReceiver() {
        final Source source = testSource("ROOT(DEFINE(a,ROOT(\n" +
                        "  STATEMENT())\n" +
                        "),\n" +
                        "CALL_WITH(a, 42))\n");
        try (DebuggerSession session = startSession()) {
            session.suspendNextExecution();
            startEval(source);

            expectSuspended((SuspendedEvent event) -> {
                DebugStackFrame frame = event.getTopStackFrame();
                DebugValue receiver = frame.getScope().getReceiver();
                assertEquals("THIS", receiver.getName());
                assertEquals(42, receiver.asInt());
                assertNull("Receiver is not a declared value", frame.getScope().getDeclaredValue("THIS"));
                checkStack(frame);
            });
            expectDone();
        }
    }

    @Test
    public void testMultipleReceivers1() {
        ProxyLanguage.setDelegate(new TestReceiverLanguage());
        Source source = Source.create(ProxyLanguage.ID, "thisReceiver\n" +
                        "a 1 b 2\n" +
                        "c 3 thisReceiver 4\n" +
                        "d 5 e 6");
        try (DebuggerSession session = startSession()) {
            session.suspendNextExecution();
            startEval(source);

            expectSuspended((SuspendedEvent event) -> {
                DebugStackFrame frame = event.getTopStackFrame();
                DebugScope scope = frame.getScope();
                DebugValue receiver = scope.getReceiver();
                assertNull(receiver);

                scope = scope.getParent();
                receiver = scope.getReceiver();
                assertEquals("thisReceiver", receiver.getName());
                assertEquals("4", receiver.toDisplayString());

                scope = scope.getParent();
                receiver = scope.getReceiver();
                assertNull(receiver);
            });
            expectDone();
        }
    }

    @Test
    public void testMultipleReceivers2() {
        ProxyLanguage.setDelegate(new TestReceiverLanguage());
        Source source = Source.create(ProxyLanguage.ID, "thisReceiver\n" +
                        "thisReceiver 1\n" +
                        "a 2 b 3\n" +
                        "c 4 thisReceiver 5");
        try (DebuggerSession session = startSession()) {
            session.suspendNextExecution();
            startEval(source);

            expectSuspended((SuspendedEvent event) -> {
                DebugStackFrame frame = event.getTopStackFrame();
                DebugScope scope = frame.getScope();
                DebugValue receiver = scope.getReceiver();
                assertEquals("thisReceiver", receiver.getName());
                assertEquals("1", receiver.toDisplayString());

                scope = scope.getParent();
                receiver = scope.getReceiver();
                assertNull(receiver);

                scope = scope.getParent();
                receiver = scope.getReceiver();
                assertEquals("thisReceiver", receiver.getName());
                assertEquals("5", receiver.toDisplayString());
            });
            expectDone();
        }
    }

    @Test
    public void testVariables() {
        final Source source = testSource("ROOT(DEFINE(foo,ROOT(\n" +
                        "  VARIABLE(x, 10),\n" +
                        "  VARIABLE(y, 20),\n" +
                        "  STATEMENT())\n" +
                        "),\n" +
                        "CALL_WITH(foo, 42))\n");
        try (DebuggerSession session = startSession()) {
            session.suspendNextExecution();
            startEval(source);

            expectSuspended((SuspendedEvent event) -> {
                DebugStackFrame frame = event.getTopStackFrame();
                DebugValue receiver = frame.getScope().getReceiver();
                assertEquals("THIS", receiver.getName());
                assertEquals(42, receiver.asInt());
                assertNull("Receiver is not a declared value", frame.getScope().getDeclaredValue("THIS"));
                checkStack(frame, "x", "10", "y", "20");
                assertEquals("foo", frame.getScope().getName());
                assertEquals("foo", frame.getName());
            });
            expectDone();
        }
    }

    @Test
    public void testNoCallNode() {
        Source source = Source.create(ProxyLanguage.ID, "abcde");
        ProxyLanguage.setDelegate(new NoCallNodeTestLanguage());

        try (DebuggerSession session = startSession()) {
            session.suspendNextExecution();
            startEval(source);

            expectSuspended((SuspendedEvent event) -> {
                checkNoCallFrames(event, 1);
                event.prepareStepInto(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkNoCallFrames(event, 2);
                event.prepareStepInto(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkNoCallFrames(event, 3);
                event.prepareStepInto(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkNoCallFrames(event, 4);
                event.prepareStepInto(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkNoCallFrames(event, 5);
                event.prepareStepInto(1);
            });
            expectDone();
        }
    }

    private static void checkNoCallFrames(SuspendedEvent event, int depth) {
        DebugScope scope = event.getTopStackFrame().getScope();
        assertNotNull(scope);
        assertFalse(scope.getDeclaredValues().iterator().hasNext());

        Iterator<DebugStackFrame> iterator = event.getStackFrames().iterator();
        iterator.next(); // top frame
        for (int d = 1; d < depth; d++) {
            assertTrue("No next frame at dept " + d, iterator.hasNext());
            DebugStackFrame frame = iterator.next();
            assertEquals("abcde".substring(depth - d - 1), frame.getName());
            assertNull(frame.getScope());
        }
    }
}
