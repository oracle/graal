/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates. All rights reserved.
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
import static org.junit.Assert.assertSame;

import org.junit.Test;

import com.oracle.truffle.api.debug.Breakpoint;
import com.oracle.truffle.api.debug.DebuggerSession;
import com.oracle.truffle.api.debug.SuspendedEvent;
import org.graalvm.polyglot.Source;

public class DoubleHaltTest extends AbstractDebugTest {

    @Test
    public void testBreakpointStepping() throws Throwable {
        Source testSource = testSource("ROOT(\n" +
                        "  STATEMENT,\n" +
                        "  STATEMENT,\n" +
                        "  STATEMENT,\n" +
                        "  STATEMENT,\n" +
                        "  STATEMENT,\n" +
                        "  STATEMENT,\n" +
                        "  STATEMENT,\n" +
                        "  STATEMENT\n" +
                        ")\n");

        try (DebuggerSession session = startSession()) {
            Breakpoint breakpoint2 = session.install(Breakpoint.newBuilder(getSourceImpl(testSource)).lineIs(2).build());
            Breakpoint breakpoint3 = session.install(Breakpoint.newBuilder(getSourceImpl(testSource)).lineIs(3).build());
            Breakpoint breakpoint5 = session.install(Breakpoint.newBuilder(getSourceImpl(testSource)).lineIs(5).build());
            Breakpoint breakpoint6 = session.install(Breakpoint.newBuilder(getSourceImpl(testSource)).lineIs(6).build());

            session.suspendNextExecution();
            startEval(testSource);

            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 2, true, "STATEMENT");
                assertEquals(1, event.getBreakpoints().size());
                assertSame(breakpoint2, event.getBreakpoints().iterator().next());
                event.prepareStepInto(1);
            });

            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 3, true, "STATEMENT");
                assertEquals(1, event.getBreakpoints().size());
                assertSame(breakpoint3, event.getBreakpoints().iterator().next());
                event.prepareStepOver(2);
            });

            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 5, true, "STATEMENT");
                assertEquals(1, event.getBreakpoints().size());
                assertSame(breakpoint5, event.getBreakpoints().iterator().next());
                event.prepareStepInto(2);
            });

            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 6, true, "STATEMENT");
                assertEquals(1, event.getBreakpoints().size());
                assertSame(breakpoint6, event.getBreakpoints().iterator().next());
                event.prepareContinue();
            });

            expectDone();

            assertEquals(1, breakpoint2.getHitCount());
            assertEquals(1, breakpoint3.getHitCount());
            assertEquals(1, breakpoint5.getHitCount());
            assertEquals(1, breakpoint6.getHitCount());
        }
    }

    @Test
    public void testCallLoopStepInto() throws Throwable {
        Source testSource = testSource("ROOT(\n" +
                        "  DEFINE(foo,\n" +
                        "    LOOP(3,\n" +
                        "      STATEMENT)\n" +
                        "  ),\n" +
                        "  CALL(foo)\n" +
                        ")\n");

        try (DebuggerSession session = startSession()) {
            Breakpoint breakpoint4 = session.install(Breakpoint.newBuilder(getSourceImpl(testSource)).lineIs(4).build());
            session.suspendNextExecution();
            startEval(testSource);

            for (int i = 0; i < 3; i++) {
                final int modI = i % 3;
                int finalIndex = i;
                expectSuspended((SuspendedEvent event) -> {
                    SuspendedEvent e = checkState(event, 4, true, "STATEMENT", "loopIndex0", String.valueOf(finalIndex), "loopResult0", "Null");
                    assertEquals(1, e.getBreakpoints().size());
                    assertSame(breakpoint4, e.getBreakpoints().iterator().next());
                    switch (modI) {
                        case 0:
                            /*
                             * Note Chumer: breakpoints should always hit independent if we are
                             * currently stepping out or not. that's why step out does not step out
                             * here.
                             */
                            e.prepareStepOut(1);
                            break;
                        case 1:
                            e.prepareStepInto(1);
                            break;
                        case 2:
                            e.prepareStepOver(1);
                            break;
                    }
                });
            }
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 6, false, "CALL(foo)");
            });

            expectDone();
        }
    }

}
