/*
 * Copyright (c) 2016, 2024, Oracle and/or its affiliates. All rights reserved.
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

import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Source;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.debug.Breakpoint;
import com.oracle.truffle.api.debug.DebugValue;
import com.oracle.truffle.api.debug.Debugger;
import com.oracle.truffle.api.debug.DebuggerSession;
import com.oracle.truffle.api.debug.SourceElement;
import com.oracle.truffle.api.debug.StepConfig;
import com.oracle.truffle.api.debug.SuspendAnchor;
import com.oracle.truffle.api.debug.SuspendedEvent;
import com.oracle.truffle.tck.DebuggerTester;

public class StepTest extends AbstractDebugTest {

    @Test
    public void testBlock() throws Throwable {
        final Source source = testSource("ROOT(\n" +
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
            startEval(source);

            // make javac happy and use the sessiononce.
            session.getDebugger();

            expectDone();
        }
    }

    @Test
    public void testBlockStepIntoOver() throws Throwable {
        final Source source = testSource("ROOT(\n" +
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
            startEval(source);
            session.suspendNextExecution();

            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 2, true, "STATEMENT").prepareStepInto(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 3, true, "STATEMENT").prepareStepInto(2);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 5, true, "STATEMENT").prepareStepOver(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 6, true, "STATEMENT").prepareStepOver(3);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 9, true, "STATEMENT").prepareContinue();
            });

            expectDone();
        }
    }

    @Test
    public void testStepBadArgStepInto() throws Throwable {
        final Source source = testSource("ROOT(\n" +
                        "  STATEMENT\n" +
                        ")\n");
        try (DebuggerSession session = startSession()) {
            startEval(source);
            session.suspendNextExecution();

            expectSuspended((SuspendedEvent event) -> {
                try {
                    checkState(event, 2, true, "STATEMENT").prepareStepInto(0);
                    fail("Exception expected");
                } catch (IllegalArgumentException e) {
                    Assert.assertEquals("Step count must be > 0", e.getMessage());
                }
            });

            expectDone();
        }
    }

    @Test
    public void testStepBadArgStepOver() throws Throwable {
        final Source source = testSource("ROOT(\n" +
                        "  STATEMENT\n" +
                        ")\n");
        try (DebuggerSession session = startSession()) {
            startEval(source);
            session.suspendNextExecution();

            expectSuspended((SuspendedEvent event) -> {
                try {
                    checkState(event, 2, true, "STATEMENT").prepareStepOver(0);
                    fail("Exception expected");
                } catch (IllegalArgumentException e) {
                    Assert.assertEquals("Step count must be > 0", e.getMessage());
                }
            });

            expectDone();
        }
    }

    @Test
    public void testCallLoopStepInto() throws Throwable {
        final Source source = testSource("ROOT(\n" +
                        "  DEFINE(foo,\n" +
                        "    LOOP(3,\n" +
                        "      STATEMENT)\n" +
                        "  ),\n" +
                        "  CALL(foo)\n" +
                        ")\n");

        try (DebuggerSession session = startSession()) {
            startEval(source);
            session.suspendNextExecution();

            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 4, true, "STATEMENT", "loopIndex0", String.valueOf(0), "loopResult0", "Null").prepareStepInto(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 4, true, "STATEMENT", "loopIndex0", String.valueOf(1), "loopResult0", "Null").prepareStepInto(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 4, true, "STATEMENT", "loopIndex0", String.valueOf(2), "loopResult0", "Null").prepareStepInto(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 6, false, "CALL(foo)").prepareContinue();
            });

            expectDone();
        }
    }

    @Test
    public void testCallLoopStepOut() throws Throwable {
        final Source source = testSource("ROOT(\n" +
                        "  DEFINE(foo,\n" +
                        "    LOOP(3,\n" +
                        "      STATEMENT)\n" +
                        "  ),\n" +
                        "  CALL(foo)\n" +
                        ")\n");

        try (DebuggerSession session = startSession()) {
            startEval(source);
            session.suspendNextExecution();

            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 4, true, "STATEMENT", "loopIndex0", String.valueOf(0), "loopResult0", "Null").prepareStepOut(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 6, false, "CALL(foo)").prepareContinue();
            });

            expectDone();
        }
    }

    @Test
    public void testStepOver1() throws Throwable {
        final Source source = testSource("ROOT(\n" +
                        "  DEFINE(bar, STATEMENT),\n" +
                        "  DEFINE(foo, ROOT(STATEMENT(CALL(bar)), \n" +
                        "                   STATEMENT(CALL(bar)))),\n" +
                        "  STATEMENT(CALL(foo)),\n" +
                        "  STATEMENT(CALL(foo)),\n" +
                        "  STATEMENT(CALL(foo))\n" +
                        ")\n");

        try (DebuggerSession session = startSession()) {
            startEval(source);
            session.suspendNextExecution();

            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 5, true, "STATEMENT(CALL(foo))").prepareStepOver(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 6, true, "STATEMENT(CALL(foo))").prepareStepOver(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 7, true, "STATEMENT(CALL(foo))").prepareStepOver(1);
            });

            expectDone();
        }
    }

    @Test
    public void testStepIntoAndOut() throws Throwable {
        final Source source = testSource("ROOT(\n" +
                        "  DEFINE(bar, STATEMENT),\n" +
                        "  DEFINE(foo, ROOT(STATEMENT(CALL(bar)), \n" +
                        "                   STATEMENT(CALL(bar)))),\n" +
                        "  STATEMENT(CALL(foo)),\n" +
                        "  STATEMENT(CALL(foo)),\n" +
                        "  STATEMENT(CALL(foo))\n" +
                        ")\n");

        try (DebuggerSession session = startSession()) {
            startEval(source);
            session.suspendNextExecution();

            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 5, true, "STATEMENT(CALL(foo))").prepareStepInto(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 3, true, "STATEMENT(CALL(bar))").prepareStepOut(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 5, false, "CALL(foo)").prepareStepOver(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 6, true, "STATEMENT(CALL(foo))").prepareStepInto(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 3, true, "STATEMENT(CALL(bar))").prepareStepInto(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 2, true, "STATEMENT").prepareStepOut(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 3, false, "CALL(bar)").prepareStepOut(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 6, false, "CALL(foo)").prepareStepOut(1);
            });

            expectDone();
        }
    }

    @Test
    public void testStepInto() throws Throwable {
        final Source source = testSource("ROOT(\n" +
                        "  DEFINE(bar, STATEMENT),\n" +
                        "  DEFINE(foo, ROOT(STATEMENT(CALL(bar)), \n" +
                        "                   STATEMENT(CALL(bar)))),\n" +
                        "  STATEMENT(CALL(foo)),\n" +
                        "  STATEMENT(CALL(foo))\n" +
                        ")\n");

        try (DebuggerSession session = startSession()) {
            session.suspendNextExecution();
            startEval(source);

            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 5, true, "STATEMENT(CALL(foo))").prepareStepInto(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 3, true, "STATEMENT(CALL(bar))").prepareStepInto(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 2, true, "STATEMENT").prepareStepInto(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 3, false, "CALL(bar)").prepareStepInto(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 4, true, "STATEMENT(CALL(bar))").prepareStepInto(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 2, true, "STATEMENT").prepareStepInto(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 4, false, "CALL(bar)").prepareStepInto(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 5, false, "CALL(foo)").prepareStepInto(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 6, true, "STATEMENT(CALL(foo))").prepareContinue();
            });

            expectDone();
        }
    }

    @Test
    public void testPreserveStepOutAfterSuspend() throws Throwable {
        final Source source = testSource("ROOT(\n" +
                        "  DEFINE(bar, STATEMENT),\n" +
                        "  DEFINE(foo, ROOT(STATEMENT(CALL(bar)), \n" +
                        "                   STATEMENT(CALL(bar)))),\n" +
                        "  STATEMENT(CALL(foo)),\n" +
                        "  STATEMENT(CALL(foo)),\n" +
                        "  STATEMENT(CALL(foo))\n" +
                        ")\n");

        try (DebuggerSession session = startSession()) {
            startEval(source);
            session.suspendNextExecution();

            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 5, true, "STATEMENT(CALL(foo))").prepareStepInto(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 3, true, "STATEMENT(CALL(bar))").prepareStepOut(1);
            });

            // do a few suspend requests to make sure we can handle
            // multiple while still preserving the step out
            session.suspend(getEvalThread());
            session.suspend(getEvalThread());
            session.suspend(getEvalThread());

            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 2, true, "STATEMENT");
            });

            // test that we still preserve stepping after one more suspension cycle
            session.suspend(getEvalThread());

            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 4, true, "STATEMENT(CALL(bar))");
            });

            // finally hit the step out
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 6, true, "STATEMENT(CALL(foo))").prepareContinue();
            });

            expectDone();
        }
    }

    @Test
    public void testMultipleActions() throws Throwable {
        final Source source = testSource("ROOT(\n" +    // 1
                        "  DEFINE(bar, STATEMENT),\n" +
                        "  DEFINE(foo, ROOT(STATEMENT(CALL(bar)), \n" +
                        "                   STATEMENT(CALL(loop)))),\n" +
                        "  DEFINE(loop,\n" +            // 5
                        "    LOOP(3,\n" +
                        "      STATEMENT),\n" +
                        "    STATEMENT\n" +
                        "  ),\n" +
                        "  STATEMENT(CALL(foo)),\n" +   // 10
                        "  STATEMENT(CALL(foo)),\n" +
                        "  STATEMENT,\n" +
                        "  STATEMENT(CALL(loop)),\n" +
                        "  STATEMENT,\n" +
                        "  STATEMENT,\n" +               // 15
                        "  STATEMENT(CALL(loop)),\n" +
                        "  STATEMENT\n" +
                        ")\n");

        try (DebuggerSession session = startSession()) {
            session.suspendNextExecution();
            Breakpoint bp14 = Breakpoint.newBuilder(getSourceImpl(source)).lineIs(14).build();
            Breakpoint bp17 = Breakpoint.newBuilder(getSourceImpl(source)).lineIs(17).build();
            session.install(bp14);
            session.install(bp17);
            startEval(source);

            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 10, true, "STATEMENT(CALL(foo))").prepareStepInto(1).prepareStepInto(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 2, true, "STATEMENT").prepareStepOut(1).prepareStepInto(2).prepareStepOver(3);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 8, true, "STATEMENT", "loopIndex0", String.valueOf(3), "loopResult0", "Null").prepareStepOut(2).prepareStepInto(3);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 2, true, "STATEMENT").prepareStepOver(1).prepareStepInto(2);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 7, true, "STATEMENT", "loopIndex0", String.valueOf(0), "loopResult0", "Null").prepareStepOver(3);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 8, true, "STATEMENT", "loopIndex0", String.valueOf(3), "loopResult0", "Null").prepareStepOut(2).prepareStepOver(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 12, true, "STATEMENT").prepareStepOver(1).prepareContinue();
            });
            // Breakpoint is hit
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 14, true, "STATEMENT").prepareStepInto(5).prepareKill();
            });
            // Breakpoint on line 17 not hit because of the kill
            expectKilled();
            Assert.assertEquals(1, bp14.getHitCount());
            Assert.assertEquals(0, bp17.getHitCount());
        }
    }

    @Test
    public void testNoPreparesAfterContinueOrKill() throws Throwable {
        final Source source = testSource("ROOT(\n" +
                        "  DEFINE(loop,\n" +
                        "    LOOP(3,\n" +
                        "      STATEMENT),\n" +
                        "    STATEMENT\n" +
                        "  ),\n" +
                        "  STATEMENT(CALL(loop))\n" +
                        ")\n");

        try (DebuggerSession session = startSession()) {
            session.suspendNextExecution();
            Breakpoint bp5 = Breakpoint.newBuilder(getSourceImpl(source)).lineIs(5).build();
            session.install(bp5);
            startEval(source);

            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 7, true, "STATEMENT(CALL(loop))").prepareContinue();
                try {
                    event.prepareStepInto(1);
                    Assert.fail("IllegalStateException should have been thrown.");
                } catch (IllegalStateException ex) {
                    // expected
                }
                try {
                    event.prepareStepOver(1);
                    Assert.fail("IllegalStateException should have been thrown.");
                } catch (IllegalStateException ex) {
                    // expected
                }
                try {
                    event.prepareStepOut(1);
                    Assert.fail("IllegalStateException should have been thrown.");
                } catch (IllegalStateException ex) {
                    // expected
                }
                try {
                    event.prepareContinue();
                    Assert.fail("IllegalStateException should have been thrown.");
                } catch (IllegalStateException ex) {
                    // expected
                }
                try {
                    event.prepareKill();
                    Assert.fail("IllegalStateException should have been thrown.");
                } catch (IllegalStateException ex) {
                    // expected
                }
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 5, true, "STATEMENT", "loopIndex0", String.valueOf(3), "loopResult0", "Null").prepareKill();
                try {
                    event.prepareStepInto(1);
                    Assert.fail("IllegalStateException should have been thrown.");
                } catch (IllegalStateException ex) {
                    // expected
                }
                try {
                    event.prepareStepOver(1);
                    Assert.fail("IllegalStateException should have been thrown.");
                } catch (IllegalStateException ex) {
                    // expected
                }
                try {
                    event.prepareStepOut(1);
                    Assert.fail("IllegalStateException should have been thrown.");
                } catch (IllegalStateException ex) {
                    // expected
                }
                try {
                    event.prepareContinue();
                    Assert.fail("IllegalStateException should have been thrown.");
                } catch (IllegalStateException ex) {
                    // expected
                }
                try {
                    event.prepareKill();
                    Assert.fail("IllegalStateException should have been thrown.");
                } catch (IllegalStateException ex) {
                    // expected
                }
            });
            expectKilled();
        }
    }

    @Test
    public void testIncompatibleSourceElements() {
        final Source source = testSource("ROOT(\n" +
                        "  STATEMENT,\n" +
                        "  EXPRESSION\n" +
                        ")\n");
        // No SourceElements, no stepping/suspensions
        try (DebuggerSession session = startSession(new SourceElement[]{})) {
            startEval(source);
            session.suspendNextExecution();
            expectDone();
        }
        try (DebuggerSession session = startSession(new SourceElement[]{})) {
            Breakpoint breakpoint = Breakpoint.newBuilder(DebuggerTester.getSourceImpl(source)).lineIs(2).oneShot().build();
            session.install(breakpoint);
            startEval(source);
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 2, true, "STATEMENT");
                try {
                    event.prepareStepInto(StepConfig.newBuilder().sourceElements(SourceElement.STATEMENT).build());
                    fail();
                } catch (IllegalStateException ex) {
                    // O.K.
                }
                try {
                    event.prepareStepInto(StepConfig.newBuilder().sourceElements(SourceElement.EXPRESSION).build());
                    fail();
                } catch (IllegalStateException ex) {
                    // O.K.
                }
                try {
                    event.prepareStepInto(1);
                    fail();
                } catch (IllegalStateException ex) {
                    // O.K.
                }
            });
            expectDone();
        }
        try (DebuggerSession session = startSession()) {
            startEval(source);
            session.suspendNextExecution();
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 2, true, "STATEMENT");
                try {
                    event.prepareStepInto(StepConfig.newBuilder().sourceElements(SourceElement.EXPRESSION).build());
                    fail();
                } catch (IllegalArgumentException ex) {
                    // O.K.
                }
            });
            expectDone();
        }
        try (DebuggerSession session = startSession(SourceElement.EXPRESSION)) {
            startEval(source);
            session.suspendNextExecution();
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 3, true, "EXPRESSION");
                try {
                    event.prepareStepInto(StepConfig.newBuilder().sourceElements(SourceElement.STATEMENT).build());
                    fail();
                } catch (IllegalArgumentException ex) {
                    // O.K.
                }
                event.prepareStepInto(1); // O.K.
                event.prepareStepInto(StepConfig.newBuilder().sourceElements(SourceElement.EXPRESSION).build()); // O.K.
            });
            expectDone();
        }
    }

    @Test
    public void testExpressionStep() {
        final Source source = testSource("ROOT(\n" +
                        "  DEFINE(inner1, ROOT(\n" +
                        "    STATEMENT,\n" +
                        "    EXPRESSION,\n" +
                        "    EXPRESSION(EXPRESSION(CONSTANT(1)), EXPRESSION(CONSTANT(2)))\n" +
                        "  )),\n" +
                        "  DEFINE(inner2, ROOT(\n" +
                        "    EXPRESSION,\n" +
                        "    CALL(inner2_1),\n" +
                        "    EXPRESSION(CALL(inner2_1))\n" +
                        "  )),\n" +
                        "  DEFINE(inner2_1, ROOT(\n" +
                        "    STATEMENT(EXPRESSION(EXPRESSION))\n" +
                        "  )),\n" +
                        "  EXPRESSION,\n" +
                        "  STATEMENT(EXPRESSION(CALL(inner1)), EXPRESSION(CALL(inner2))),\n" +
                        "  EXPRESSION,\n" +
                        "  STATEMENT\n" +
                        ")\n");
        try (DebuggerSession session = startSession(SourceElement.EXPRESSION)) {
            startEval(source);
            session.suspendNextExecution();

            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 15, true, "EXPRESSION").prepareStepInto(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 15, false, "EXPRESSION").prepareStepInto(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 16, true, "EXPRESSION(CALL(inner1))").prepareStepInto(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 4, true, "EXPRESSION").prepareStepOver(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 4, false, "EXPRESSION").prepareStepOver(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 5, true, "EXPRESSION(EXPRESSION(CONSTANT(1)), EXPRESSION(CONSTANT(2)))").prepareStepInto(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 5, true, "EXPRESSION(CONSTANT(1))").prepareStepOver(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 5, false, "EXPRESSION(CONSTANT(1))").prepareStepOut(1);
                Assert.assertEquals("(1)", event.getReturnValue().toDisplayString());
                Assert.assertEquals(Arrays.asList(event.getInputValues()).toString(), 0, event.getInputValues().length);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 5, false, "EXPRESSION(EXPRESSION(CONSTANT(1)), EXPRESSION(CONSTANT(2)))").prepareStepInto(1);
                Assert.assertEquals("((1)+(2))", event.getReturnValue().toDisplayString());
                DebugValue[] inputValues = event.getInputValues();
                Assert.assertEquals(Arrays.asList(inputValues).toString(), 2, event.getInputValues().length);
                Assert.assertEquals("(1)", inputValues[0].toDisplayString());
                Assert.assertEquals("(2)", inputValues[1].toDisplayString());
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 16, false, "CALL(inner1)").prepareStepInto(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 16, false, "EXPRESSION(CALL(inner1))").prepareStepInto(2);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 8, true, "EXPRESSION").prepareStepOver(2);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 10, true, "EXPRESSION(CALL(inner2_1))").prepareStepInto(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 13, true, "EXPRESSION(EXPRESSION)").prepareStepOut(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 13, false, "EXPRESSION(EXPRESSION)").prepareStepOut(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 10, false, "CALL(inner2_1)").prepareStepOut(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 10, false, "EXPRESSION(CALL(inner2_1))").prepareStepOut(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 16, false, "CALL(inner2)").prepareStepOut(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 16, false, "EXPRESSION(CALL(inner2))").prepareStepOut(1);
            });
            expectDone();
        }
    }

    private static final String ALL_ELEMENTS_SOURCE = "ROOT(\n" +
                    "  DEFINE(inner1, ROOT(\n" +
                    "    STATEMENT,\n" +
                    "    EXPRESSION\n" +
                    "  )),\n" +
                    "  DEFINE(inner2, ROOT(\n" +
                    "    EXPRESSION(STATEMENT), STATEMENT(CONSTANT(1))\n" +
                    "  )),\n" +
                    "  EXPRESSION,\n" +
                    "  STATEMENT(EXPRESSION(CALL(inner1)), EXPRESSION(CALL(inner1)), EXPRESSION(CALL(inner2))),\n" +
                    "  EXPRESSION,\n" +
                    "  STATEMENT\n" +
                    ")\n";

    @Test
    public void testExpressionAndStatementStep() {
        final Source source = testSource(ALL_ELEMENTS_SOURCE);
        try (DebuggerSession session = startSession(SourceElement.EXPRESSION, SourceElement.STATEMENT)) {
            startEval(source);
            session.suspendNextExecution();

            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 9, true, "EXPRESSION").prepareStepOver(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 9, false, "EXPRESSION").prepareStepOver(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 10, true, "STATEMENT(EXPRESSION(CALL(inner1)), EXPRESSION(CALL(inner1)), EXPRESSION(CALL(inner2)))").prepareStepOver(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 10, true, "EXPRESSION(CALL(inner1))").prepareStepInto(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 3, true, "STATEMENT").prepareStepOut(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 10, false, "CALL(inner1)").prepareStepOut(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 10, false, "EXPRESSION(CALL(inner1))").prepareStepOver(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 10, true, "EXPRESSION(CALL(inner1))").prepareStepOver(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 10, false, "EXPRESSION(CALL(inner1))").prepareStepOver(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 10, true, "EXPRESSION(CALL(inner2))").prepareStepInto(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 7, true, "EXPRESSION(STATEMENT)").prepareStepOver(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 7, true, "STATEMENT").prepareStepOver(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 7, false, "EXPRESSION(STATEMENT)").prepareStepOver(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 7, true, "STATEMENT(CONSTANT(1))").prepareStepOut(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 10, false, "CALL(inner2)").prepareStepOut(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 10, false, "EXPRESSION(CALL(inner2))").prepareStepOut(1);
            });
            expectDone();
        }
    }

    @Test
    public void testRootStepOver() {
        final Source source = testSource(ALL_ELEMENTS_SOURCE);
        try (DebuggerSession session = startSession(SourceElement.ROOT)) {
            startEval(source);
            session.suspendNextExecution();

            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 1, true, ALL_ELEMENTS_SOURCE).prepareStepOver(1);
                checkReturn(event, null);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 13, false, ALL_ELEMENTS_SOURCE).prepareStepOver(1);
                checkReturn(event, "()");
            });
            expectDone();
        }
    }

    @Test
    public void testRootStepInto() {
        final Source source = testSource(ALL_ELEMENTS_SOURCE);
        try (DebuggerSession session = startSession(SourceElement.ROOT)) {
            startEval(source);
            session.suspendNextExecution();

            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 1, true, ALL_ELEMENTS_SOURCE).prepareStepInto(1);
            });
            String function1 = " ROOT(\n" +
                            "    STATEMENT,\n" +
                            "    EXPRESSION\n" +
                            "  )";
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 2, true, function1).prepareStepInto(1);
                checkReturn(event, null);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 5, false, function1).prepareStepInto(1);
                checkReturn(event, "()");
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 10, false, "CALL(inner1)").prepareStepInto(1);
                checkReturn(event, "()");
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 2, true, function1).prepareStepInto(1);
                checkReturn(event, null);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 5, false, function1).prepareStepInto(1);
                checkReturn(event, "()");
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 10, false, "CALL(inner1)").prepareStepInto(1);
                checkReturn(event, "()");
            });
            String function2 = " ROOT(\n" +
                            "    EXPRESSION(STATEMENT), STATEMENT(CONSTANT(1))\n" +
                            "  )";
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 6, true, function2).prepareStepInto(1);
                checkReturn(event, null);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 8, false, function2).prepareStepInto(1);
                checkReturn(event, "1");
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 10, false, "CALL(inner2)").prepareStepInto(1);
                checkReturn(event, "1");
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 13, false, ALL_ELEMENTS_SOURCE).prepareStepInto(1);
                checkReturn(event, "()");
            });
            expectDone();
        }
    }

    @Test
    public void testRootStepOut() {
        final Source source = testSource(ALL_ELEMENTS_SOURCE);
        try (DebuggerSession session = startSession(SourceElement.ROOT)) {
            startEval(source);
            session.suspendNextExecution();

            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 1, true, ALL_ELEMENTS_SOURCE).prepareStepInto(1);
            });
            String function1 = " ROOT(\n" +
                            "    STATEMENT,\n" +
                            "    EXPRESSION\n" +
                            "  )";
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 2, true, function1).prepareStepOut(1);
                checkReturn(event, null);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 10, false, "CALL(inner1)").prepareStepOut(1);
                checkReturn(event, "()");
            });
            expectDone();
        }
    }

    @Test
    public void testRootAndStatementStep() {
        final Source source = testSource(ALL_ELEMENTS_SOURCE);
        try (DebuggerSession session = startSession(SourceElement.ROOT, SourceElement.STATEMENT)) {
            startEval(source);
            session.suspendNextExecution();

            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 1, true, ALL_ELEMENTS_SOURCE).prepareStepInto(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 10, true, "STATEMENT(EXPRESSION(CALL(inner1)), EXPRESSION(CALL(inner1)), EXPRESSION(CALL(inner2)))").prepareStepInto(1);
            });
            String function1 = " ROOT(\n" +
                            "    STATEMENT,\n" +
                            "    EXPRESSION\n" +
                            "  )";
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 2, true, function1).prepareStepInto(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 3, true, "STATEMENT").prepareStepInto(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 5, false, function1).prepareStepInto(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 10, false, "CALL(inner1)").prepareStepInto(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 2, true, function1).prepareStepOut(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 10, false, "CALL(inner1)").prepareStepInto(1);
            });
            String function2 = " ROOT(\n" +
                            "    EXPRESSION(STATEMENT), STATEMENT(CONSTANT(1))\n" +
                            "  )";
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 6, true, function2).prepareStepInto(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 7, true, "STATEMENT").prepareStepInto(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 7, true, "STATEMENT(CONSTANT(1))").prepareStepInto(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 8, false, function2).prepareStepInto(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 10, false, "CALL(inner2)").prepareStepInto(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 12, true, "STATEMENT").prepareStepInto(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 13, false, ALL_ELEMENTS_SOURCE).prepareStepInto(1);
            });
            expectDone();
        }
    }

    @Test
    public void testRootAndExpressionAndStatementStep() {
        final Source source = testSource(ALL_ELEMENTS_SOURCE);
        try (DebuggerSession session = startSession(SourceElement.ROOT, SourceElement.EXPRESSION, SourceElement.STATEMENT)) {
            startEval(source);
            session.suspendNextExecution();

            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 1, true, ALL_ELEMENTS_SOURCE).prepareStepInto(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 9, true, "EXPRESSION").prepareStepOver(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 9, false, "EXPRESSION").prepareStepOver(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 10, true, "STATEMENT(EXPRESSION(CALL(inner1)), EXPRESSION(CALL(inner1)), EXPRESSION(CALL(inner2)))").prepareStepOver(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 10, true, "EXPRESSION(CALL(inner1))").prepareStepInto(1);
            });
            String function1 = " ROOT(\n" +
                            "    STATEMENT,\n" +
                            "    EXPRESSION\n" +
                            "  )";
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 2, true, function1).prepareStepInto(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 3, true, "STATEMENT").prepareStepOut(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 10, false, "CALL(inner1)").prepareStepOut(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 10, false, "EXPRESSION(CALL(inner1))").prepareStepOver(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 10, true, "EXPRESSION(CALL(inner1))").prepareStepOver(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 10, false, "EXPRESSION(CALL(inner1))").prepareStepOver(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 10, true, "EXPRESSION(CALL(inner2))").prepareStepInto(1);
            });
            String function2 = " ROOT(\n" +
                            "    EXPRESSION(STATEMENT), STATEMENT(CONSTANT(1))\n" +
                            "  )";
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 6, true, function2).prepareStepInto(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 7, true, "EXPRESSION(STATEMENT)").prepareStepOver(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 7, true, "STATEMENT").prepareStepOver(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 7, false, "EXPRESSION(STATEMENT)").prepareStepOver(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 7, true, "STATEMENT(CONSTANT(1))").prepareStepOut(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 10, false, "CALL(inner2)").prepareStepOut(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 10, false, "EXPRESSION(CALL(inner2))").prepareStepOver(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 11, true, "EXPRESSION").prepareStepOver(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 11, false, "EXPRESSION").prepareStepOver(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 12, true, "STATEMENT").prepareStepInto(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 13, false, ALL_ELEMENTS_SOURCE).prepareStepInto(1);
            });
            expectDone();
        }
    }

    @Test
    public void testStepAnchors() {
        final Source source = testSource(ALL_ELEMENTS_SOURCE);
        try (DebuggerSession session = startSession(SourceElement.ROOT, SourceElement.EXPRESSION, SourceElement.STATEMENT)) {
            startEval(source);
            session.suspendNextExecution();
            StepConfig customAnchors = StepConfig.newBuilder().suspendAnchors(SourceElement.ROOT, SuspendAnchor.AFTER).suspendAnchors(SourceElement.EXPRESSION, SuspendAnchor.BEFORE).build();

            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 1, true, ALL_ELEMENTS_SOURCE).prepareStepInto(customAnchors);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 9, true, "EXPRESSION").prepareStepOver(customAnchors);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 10, true, "STATEMENT(EXPRESSION(CALL(inner1)), EXPRESSION(CALL(inner1)), EXPRESSION(CALL(inner2)))").prepareStepOver(customAnchors);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 10, true, "EXPRESSION(CALL(inner1))").prepareStepInto(customAnchors);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 3, true, "STATEMENT").prepareStepOver(customAnchors);
            });
            String function1 = " ROOT(\n" +
                            "    STATEMENT,\n" +
                            "    EXPRESSION\n" +
                            "  )";
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 4, true, "EXPRESSION").prepareStepInto(customAnchors);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 5, false, function1).prepareStepInto(customAnchors);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 10, false, "CALL(inner1)").prepareStepOver(customAnchors);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 10, true, "EXPRESSION(CALL(inner1))").prepareStepOver(customAnchors);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 10, true, "EXPRESSION(CALL(inner2))").prepareStepInto(customAnchors);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 7, true, "EXPRESSION(STATEMENT)").prepareStepOver(customAnchors);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 7, true, "STATEMENT").prepareStepOver(customAnchors);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 7, true, "STATEMENT(CONSTANT(1))").prepareStepOver(customAnchors);
            });
            String function2 = " ROOT(\n" +
                            "    EXPRESSION(STATEMENT), STATEMENT(CONSTANT(1))\n" +
                            "  )";
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 8, false, function2).prepareStepInto(customAnchors);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 10, false, "CALL(inner2)").prepareStepOver(customAnchors);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 11, true, "EXPRESSION").prepareStepOver(customAnchors);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 12, true, "STATEMENT").prepareStepInto(customAnchors);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 13, false, ALL_ELEMENTS_SOURCE).prepareStepInto(customAnchors);
            });
            expectDone();
        }
    }

    @Test
    public void testSteppingDisabled() {
        final Source source = testSource("ROOT(\n" +
                        "  STATEMENT,\n" +
                        "  STATEMENT\n" +
                        ")\n");
        try (DebuggerSession session = startSession()) {
            session.suspendNextExecution();
            startEval(source);
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 2, true, "STATEMENT").prepareStepInto(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 3, true, "STATEMENT").prepareStepInto(1);
            });
            expectDone();
            startExecute(c -> {
                c.enter();
                tester.getDebugger().disableStepping();
                c.leave();
                return c.asValue(null);
            });
            expectDone();
            startEval(source);
            expectDone();
            startExecute(c -> {
                c.enter();
                tester.getDebugger().restoreStepping();
                c.leave();
                return c.asValue(null);
            });
            expectDone();
            startEval(source);
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 2, true, "STATEMENT").prepareContinue();
            });
            expectDone();
        }
    }

    @Test
    public void testSteppingDisabledOnOneThread() throws Exception {
        final Source source = testSource("ROOT(\n" +
                        "  STATEMENT,\n" +
                        "  STATEMENT\n" +
                        ")\n");
        Engine engine = Engine.create();
        Debugger debugger = Debugger.find(engine);
        List<SuspendedEvent> events = new ArrayList<>();
        try (DebuggerSession session1 = debugger.startSession(event -> {
            events.add(event);
        })) {
            try (DebuggerSession session2 = debugger.startSession(event -> {
                events.add(event);
            })) {
                session1.suspendNextExecution();
                session2.suspendNextExecution();
                CountDownLatch disabledSteppingFinished = new CountDownLatch(1);
                CountDownLatch allFinished = new CountDownLatch(1);
                Thread t1 = new Thread(() -> {
                    org.graalvm.polyglot.Context context1 = org.graalvm.polyglot.Context.newBuilder().engine(engine).build();
                    context1.enter();
                    debugger.disableStepping();
                    context1.eval(source);
                    disabledSteppingFinished.countDown();
                    try {
                        allFinished.await();
                    } catch (InterruptedException ex) {
                    }
                    debugger.restoreStepping();
                    context1.leave();
                });
                List<Throwable> throwables = runWithErrorCheck(t1);
                // Wait for eval with disabled stepping
                disabledSteppingFinished.await();
                Assert.assertEquals(0, events.size());
                Thread t2 = new Thread(() -> {
                    org.graalvm.polyglot.Context context2 = org.graalvm.polyglot.Context.newBuilder().engine(engine).build();
                    context2.eval(source);
                });
                runAndCheckForErrors(t2);
                // A second eval with enabled stepping took place
                Assert.assertEquals(2, events.size());
                allFinished.countDown();
                joinWithErrorCheck(t1, throwables);
                Assert.assertEquals(2, events.size());
            }
        }
    }

    @Test
    public void testSteppingDisabledWithBreakpoint() throws Exception {
        final Source source = testSource("ROOT(\n" +
                        "  STATEMENT,\n" +
                        "  STATEMENT,\n" +
                        "  STATEMENT,\n" +
                        "  STATEMENT\n" +
                        ")\n");
        Engine engine = Engine.create();
        Debugger debugger = Debugger.find(engine);
        List<SuspendedEvent> events = new ArrayList<>();
        try (DebuggerSession session1 = debugger.startSession(event -> {
            // No step events in session1
            Assert.fail();
        })) {
            try (DebuggerSession session2 = debugger.startSession(event -> {
                events.add(event);
                event.prepareStepOver(1);
            })) {
                session1.suspendNextExecution();
                session2.suspendNextExecution();
                Breakpoint bp = Breakpoint.newBuilder(getSourceImpl(source)).lineIs(4).build();
                session2.install(bp);
                Thread t1 = new Thread(() -> {
                    org.graalvm.polyglot.Context context1 = org.graalvm.polyglot.Context.newBuilder().engine(engine).build();
                    context1.enter();
                    debugger.disableStepping();
                    context1.eval(source);
                    debugger.restoreStepping();
                    // Later on, we run the source with disabled stepping again,
                    // but this time with the breakpoint removed:
                    bp.dispose();
                    debugger.disableStepping();
                    context1.eval(source);
                    debugger.restoreStepping();
                    context1.leave();
                });
                runAndCheckForErrors(t1);
                // We have two events from the session2 with the breakpoint
                Assert.assertEquals(2, events.size());
            }
        }
    }

    @Test
    public void testSteppingDisabledRestore() throws Exception {
        try (DebuggerSession session = startSession()) {
            session.suspendNextExecution();
            startExecute(c -> {
                c.enter();
                try {
                    tester.getDebugger().restoreStepping();
                    Assert.fail();
                } catch (IllegalStateException ex) {
                    Assert.assertEquals("restoreStepping() called without a corresponding disabledStepping()", ex.getMessage());
                }
                c.leave();
                return c.asValue(null);
            });
            expectDone();
        }
    }

    @Test
    public void testSteppingDisabledNested() throws Exception {
        final Source source = testSource("ROOT(\n" +
                        "  STATEMENT,\n" +
                        "  STATEMENT\n" +
                        ")\n");
        Engine engine = Engine.create();
        Debugger debugger = Debugger.find(engine);
        List<SuspendedEvent> events = new ArrayList<>();
        try (DebuggerSession session = debugger.startSession(event -> {
            events.add(event);
            event.prepareStepOver(1);
        })) {
            session.suspendNextExecution();
            Thread t1 = new Thread(() -> {
                org.graalvm.polyglot.Context context = org.graalvm.polyglot.Context.newBuilder().engine(engine).build();
                context.enter();
                for (int i = 0; i < 10; i++) {
                    debugger.disableStepping();
                    context.eval(source);
                    for (int j = 0; j < i; j++) {
                        debugger.disableStepping();
                        context.eval(source);
                    }
                    for (int j = 0; j < i; j++) {
                        debugger.restoreStepping();
                        context.eval(source);
                    }
                    debugger.restoreStepping();
                    Assert.assertEquals(0, events.size());
                    context.eval(source);
                    Assert.assertEquals(2, events.size());
                    events.clear();
                }
                context.leave();
            });
            runAndCheckForErrors(t1);
        }
    }

    @Test
    public void testSteppingDisabledNestedWithBreakpoint() throws Exception {
        final Source source = testSource("ROOT(\n" +
                        "  STATEMENT,\n" +
                        "  STATEMENT\n" +
                        ")\n");
        final Source sourceBp = testSource("ROOT(\n" +
                        "  STATEMENT,\n" +
                        "  STATEMENT,\n" +
                        "  STATEMENT,\n" +
                        "  STATEMENT\n" +
                        ")\n");
        Engine engine = Engine.create();
        Debugger debugger = Debugger.find(engine);
        List<SuspendedEvent> events = new ArrayList<>();
        try (DebuggerSession session = debugger.startSession(event -> {
            if (events.isEmpty()) {
                // The first event is the breakpoint
                Assert.assertEquals(1, event.getBreakpoints().size());
            }
            events.add(event);
            event.prepareStepInto(1);
        })) {
            final int n = 10;
            session.suspendNextExecution();
            Thread t1 = new Thread(() -> {
                org.graalvm.polyglot.Context context = org.graalvm.polyglot.Context.newBuilder().engine(engine).build();
                context.enter();
                for (int i = 0; i < n; i++) {
                    Breakpoint bp = null;
                    for (int j = 0; j < n; j++) {
                        debugger.disableStepping();
                        if (j == i) {
                            bp = Breakpoint.newBuilder(getSourceImpl(sourceBp)).lineIs(4).build();
                            session.install(bp);
                            context.eval(sourceBp);
                        } else {
                            context.eval(source);
                        }
                    }
                    Assert.assertEquals("i = " + i, 2, events.size());
                    for (int j = n - 1; j > 0; j--) {
                        debugger.restoreStepping();
                        context.eval(source);
                        if (i == n - 1 || j - 1 > i) {
                            Assert.assertEquals("i = " + i + ", j = " + j, 2, events.size());
                        } else {
                            // When j == i we have stepping enabled after the breakpoint
                            Assert.assertEquals("i = " + i + ", j = " + j, 4, events.size());
                        }
                    }
                    int size = events.size();
                    debugger.restoreStepping();
                    context.eval(source);
                    Assert.assertEquals("i = " + i, size + 2, events.size());
                    events.clear();
                    bp.dispose();
                }
                context.leave();
            });
            runAndCheckForErrors(t1);
        }
    }

    private static void runAndCheckForErrors(Thread thread) throws InterruptedException {
        joinWithErrorCheck(thread, runWithErrorCheck(thread));
    }

    private static List<Throwable> runWithErrorCheck(Thread thread) {
        List<Throwable> throwables = new LinkedList<>();
        thread.setUncaughtExceptionHandler((t, ex) -> {
            throwables.add(ex);
        });
        thread.start();
        return throwables;
    }

    private static void joinWithErrorCheck(Thread thread, List<Throwable> throwables) throws InterruptedException {
        thread.join();
        for (Throwable ex : throwables) {
            ex.printStackTrace();
        }
        Assert.assertTrue(throwables.isEmpty());
    }
}
