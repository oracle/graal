/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.debug.test;

import static org.junit.Assert.fail;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.debug.Breakpoint;
import com.oracle.truffle.api.debug.DebuggerSession;
import com.oracle.truffle.api.debug.SuspendedEvent;
import com.oracle.truffle.api.source.Source;

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
                    Assert.assertEquals("stepCount must be > 0", e.getMessage());
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
                    Assert.assertEquals("stepCount must be > 0", e.getMessage());
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
                checkState(event, 4, true, "STATEMENT").prepareStepInto(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 4, true, "STATEMENT").prepareStepInto(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 4, true, "STATEMENT").prepareStepInto(1);
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
                checkState(event, 4, true, "STATEMENT").prepareStepOut(1);
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
            Breakpoint bp14 = Breakpoint.newBuilder(source).lineIs(14).build();
            Breakpoint bp17 = Breakpoint.newBuilder(source).lineIs(17).build();
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
                checkState(event, 8, true, "STATEMENT").prepareStepOut(2).prepareStepInto(3);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 2, true, "STATEMENT").prepareStepOver(1).prepareStepInto(2);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 7, true, "STATEMENT").prepareStepOver(3);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 8, true, "STATEMENT").prepareStepOut(2).prepareStepOver(1);
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
            Breakpoint bp5 = Breakpoint.newBuilder(source).lineIs(5).build();
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
                checkState(event, 5, true, "STATEMENT").prepareKill();
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
}
