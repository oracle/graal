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
                expectSuspended((SuspendedEvent event) -> {
                    SuspendedEvent e = checkState(event, 4, true, "STATEMENT");
                    assertEquals(1, e.getBreakpoints().size());
                    assertSame(breakpoint4, e.getBreakpoints().iterator().next());
                    switch (modI) {
                        case 0:
                            /*
                             * Note Chumer: breakpoints should always hit independent if we are
                             * currently stepping out or not. thats why step out does not step out
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
