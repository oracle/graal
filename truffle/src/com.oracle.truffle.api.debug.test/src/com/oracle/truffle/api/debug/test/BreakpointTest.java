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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.debug.Breakpoint;
import com.oracle.truffle.api.debug.Debugger;
import com.oracle.truffle.api.debug.DebuggerSession;
import com.oracle.truffle.api.debug.SuspendedEvent;
import com.oracle.truffle.api.instrumentation.test.InstrumentationTestLanguage;
import com.oracle.truffle.api.source.SourceSection;
import org.graalvm.polyglot.Source;

public class BreakpointTest extends AbstractDebugTest {

    @Test
    public void testBreakpointDefaults() {
        Source testSource = testSource("STATEMENT");
        Breakpoint breakpoint = Breakpoint.newBuilder(getSourceImpl(testSource)).lineIs(1).build();
        assertEquals(0, breakpoint.getHitCount());
        assertEquals(0, breakpoint.getIgnoreCount());
        assertFalse(breakpoint.isDisposed());
        assertTrue(breakpoint.isEnabled());
        assertFalse(breakpoint.isResolved());

        // Make some state changes
        breakpoint.setIgnoreCount(9);
        assertEquals(9, breakpoint.getIgnoreCount());

        breakpoint.setEnabled(false);
        assertFalse(breakpoint.isEnabled());

        breakpoint.setCondition("a + b");

        breakpoint.dispose();

        assertTrue(breakpoint.isDisposed());
        assertFalse(breakpoint.isEnabled());
        assertFalse(breakpoint.isResolved());
    }

    @Test
    public void testBreakpointResolve() {
        Source testSource = testSource("ROOT(\n" +
                        "STATEMENT,\n" +
                        "STATEMENT,\n" +
                        "STATEMENT)");

        Breakpoint breakpoint2 = Breakpoint.newBuilder(getSourceImpl(testSource)).lineIs(2).build();
        assertFalse(breakpoint2.isResolved());
        Breakpoint breakpoint3 = Breakpoint.newBuilder(getSourceImpl(testSource)).lineIs(3).build();
        assertFalse(breakpoint3.isResolved());
        try (DebuggerSession session = startSession()) {
            session.install(breakpoint2);
            assertFalse(breakpoint2.isResolved());
            assertFalse(breakpoint3.isResolved());

            startEval(testSource);
            expectSuspended((SuspendedEvent event) -> {
                assertSame(breakpoint2, event.getBreakpoints().iterator().next());
            });
            assertTrue(breakpoint2.isResolved());
            expectDone();

            assertTrue(breakpoint2.isResolved());
            assertFalse(breakpoint3.isResolved());

            // breakpoint3 should be resolved by just installing it
            session.install(breakpoint3);
            assertTrue(breakpoint2.isResolved());
            assertTrue(breakpoint3.isResolved());
        }
    }

    @Test
    public void testBreakpointCondition() {
        Source testSource = testSource("ROOT(\n" +
                        "STATEMENT,\n" +
                        "STATEMENT,\n" +
                        "STATEMENT)");

        try (DebuggerSession session = startSession()) {
            Breakpoint breakpoint = session.install(Breakpoint.newBuilder(getSourceImpl(testSource)).lineIs(2).build());
            breakpoint.setCondition("CONSTANT(true)");

            startEval(testSource);
            expectSuspended((SuspendedEvent event) -> {
                assertSame(breakpoint, event.getBreakpoints().iterator().next());
                assertNull(event.getBreakpointConditionException(breakpoint));
            });
            assertEquals(1, breakpoint.getHitCount());
            expectDone();

            breakpoint.setCondition("CONSTANT(false)");
            startEval(testSource);
            assertEquals(1, breakpoint.getHitCount());
            expectDone();

            breakpoint.setCondition("CONSTANT("); // error by parse exception
            startEval(testSource);
            expectSuspended((SuspendedEvent event) -> {
                assertSame(breakpoint, event.getBreakpoints().iterator().next());
                assertNotNull(event.getBreakpointConditionException(breakpoint));
            });
            assertEquals(2, breakpoint.getHitCount());
            expectDone();
        }
    }

    @Test
    public void testBreakURI1() throws Throwable {
        final Source source = testSource("ROOT(\n" +
                        "  STATEMENT,\n" +
                        "  STATEMENT,\n" +
                        "  STATEMENT,\n" + // break here
                        "  STATEMENT,\n" +
                        "  STATEMENT\n" +
                        ")\n");
        Breakpoint sessionBreakpoint = null;
        try (DebuggerSession session = startSession()) {
            Breakpoint breakpoint = session.install(Breakpoint.newBuilder(source.getURI()).lineIs(4).build());
            sessionBreakpoint = breakpoint;
            startEval(source);
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 4, true, "STATEMENT");
                Assert.assertEquals(1, event.getBreakpoints().size());
                Assert.assertSame(breakpoint, event.getBreakpoints().get(0));
            });
            Assert.assertEquals(1, breakpoint.getHitCount());
            Assert.assertEquals(true, breakpoint.isEnabled());
            Assert.assertEquals(true, breakpoint.isResolved());
            expectDone();
        }
        Assert.assertEquals(false, sessionBreakpoint.isResolved());
    }

    @Test
    public void testBreakURI2() throws Throwable {
        File testFile = testFile("ROOT(\n" +
                        "  DEFINE(foo,\n" +
                        "    LOOP(3,\n" +
                        "      STATEMENT)\n" +
                        "  ),\n" +
                        "  CALL(foo)\n" +
                        ")\n");

        try (DebuggerSession session = startSession()) {
            Breakpoint breakpoint = session.install(Breakpoint.newBuilder(testFile.toURI()).lineIs(4).build());
            session.suspendNextExecution();
            startEval(Source.newBuilder(InstrumentationTestLanguage.ID, testFile).build());
            for (int i = 0; i < 3; i++) {
                expectSuspended((SuspendedEvent event) -> {
                    checkState(event, 4, true, "STATEMENT").prepareContinue();
                });
            }
            Assert.assertEquals(3, breakpoint.getHitCount());
            expectDone();
        }
    }

    @Test
    public void testDisableBreakpointsDuringSuspend() throws Throwable {
        Source source = testSource("ROOT(\n" +
                        "  DEFINE(foo,\n" +
                        "    LOOP(3,\n" +
                        "      STATEMENT)\n" +
                        "  ),\n" +
                        "  CALL(foo)\n" +
                        ")\n");

        try (DebuggerSession session = startSession()) {
            Breakpoint breakpoint1 = session.install(Breakpoint.newBuilder(getSourceImpl(source)).lineIs(4).build());
            Breakpoint breakpoint2 = session.install(Breakpoint.newBuilder(getSourceImpl(source)).lineIs(4).build());
            Breakpoint breakpoint3 = session.install(Breakpoint.newBuilder(getSourceImpl(source)).lineIs(4).build());
            startEval(source);
            for (int i = 0; i < 3; i++) {
                expectSuspended((SuspendedEvent event) -> {
                    checkState(event, 4, true, "STATEMENT").prepareContinue();
                });
                if (i == 0) {
                    breakpoint3.dispose();
                }
                if (i == 1) {
                    breakpoint1.dispose();
                }
            }
            Assert.assertEquals(2, breakpoint1.getHitCount());
            Assert.assertEquals(3, breakpoint2.getHitCount());
            Assert.assertEquals(1, breakpoint3.getHitCount());

            expectDone();
        }
    }

    @Test
    public void testBreakSource() throws Throwable {
        final Source source = testSource("ROOT(\n" +
                        "  STATEMENT,\n" +
                        "  STATEMENT,\n" +
                        "  STATEMENT,\n" + // break here
                        "  STATEMENT,\n" +
                        "  STATEMENT\n" +
                        ")\n");
        Breakpoint sessionBreakpoint = null;
        try (DebuggerSession session = startSession()) {
            Breakpoint breakpoint = session.install(Breakpoint.newBuilder(getSourceImpl(source)).lineIs(4).build());
            sessionBreakpoint = breakpoint;
            startEval(source);
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 4, true, "STATEMENT");
                Assert.assertEquals(1, event.getBreakpoints().size());
                Assert.assertSame(breakpoint, event.getBreakpoints().get(0));
            });
            Assert.assertEquals(1, breakpoint.getHitCount());
            Assert.assertEquals(true, breakpoint.isEnabled());
            Assert.assertEquals(true, breakpoint.isResolved());
            expectDone();
        }
        Assert.assertEquals(false, sessionBreakpoint.isResolved());
    }

    @Test
    public void testChangeDuringSuspension() throws Throwable {
        final Source source = testSource("ROOT(\n" +
                        "  DEFINE(foo,\n" +
                        "    STATEMENT\n" +
                        "  ),\n" +
                        "  STATEMENT,\n" +
                        "  CALL(foo)\n" +
                        ")\n");
        try (DebuggerSession session = startSession()) {
            session.suspendNextExecution();
            startEval(source);
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 5, true, "STATEMENT");
                Assert.assertEquals(0, event.getBreakpoints().size());
                session.install(Breakpoint.newBuilder(getSourceImpl(source)).lineIs(3).build());
                event.prepareContinue();
            });

            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 3, true, "STATEMENT");
                event.prepareContinue();
            });

            expectDone();
        }
    }

    @Test
    public void testOneShot() throws Throwable {
        final Source source = testSource("ROOT(\n" +
                        "  STATEMENT,\n" +
                        "  LOOP(3, STATEMENT),\n" +
                        "  STATEMENT\n" +
                        ")\n");
        try (DebuggerSession session = startSession()) {
            Breakpoint breakpoint = session.install(Breakpoint.newBuilder(getSourceImpl(source)).lineIs(3).oneShot().build());

            startEval(source);

            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 3, true, "STATEMENT");
                Assert.assertEquals(1, event.getBreakpoints().size());
                Assert.assertSame(breakpoint, event.getBreakpoints().iterator().next());
                Assert.assertFalse(breakpoint.isEnabled());
                Assert.assertEquals(1, breakpoint.getHitCount());

                breakpoint.setEnabled(true); // reenable breakpoint to hit again
                event.prepareContinue();
            });

            expectSuspended((SuspendedEvent event) -> {
                Assert.assertEquals(1, event.getBreakpoints().size());
                Assert.assertSame(breakpoint, event.getBreakpoints().iterator().next());
                Assert.assertFalse(breakpoint.isEnabled());
                Assert.assertEquals(2, breakpoint.getHitCount());
                event.prepareContinue();
            });

            // we don't reenable the breakpoint so we should not hit it again
            expectDone();
        }
    }

    @Test
    public void testBreakSourceSection() throws Throwable {
        final Source source = testSource("ROOT(STATEMENT, STATEMENT, STATEMENT)\n");
        try (DebuggerSession session = startSession()) {
            SourceSection sourceSection = getSourceImpl(source).createSection(16, 9);
            Breakpoint breakpoint = session.install(Breakpoint.newBuilder(sourceSection).build());

            startEval(source);

            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 1, true, "STATEMENT");
                Assert.assertEquals(sourceSection, event.getSourceSection());
                assertSame(breakpoint, event.getBreakpoints().iterator().next());
                event.prepareContinue();
            });

            expectDone();
        }
    }

    @Test
    public void testDisableDispose() throws Throwable {
        final Source source = testSource("ROOT(\n" +
                        "  STATEMENT,\n" +
                        "  STATEMENT,\n" +
                        "  STATEMENT,\n" +
                        "  STATEMENT,\n" +
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
            // test normal breakpoint should hit
            Breakpoint breakpoint4 = session.install(Breakpoint.newBuilder(getSourceImpl(source)).lineIs(4).build());

            // test disposed breakpoint should not hit
            Breakpoint breakpoint6 = session.install(Breakpoint.newBuilder(getSourceImpl(source)).lineIs(6).build());
            breakpoint6.dispose();

            // test disabled breakpoint should not hit
            Breakpoint breakpoint8 = session.install(Breakpoint.newBuilder(getSourceImpl(source)).lineIs(8).build());
            breakpoint8.setEnabled(false);

            // test re-enabled breakpoint should hit
            Breakpoint breakpoint10 = session.install(Breakpoint.newBuilder(getSourceImpl(source)).lineIs(10).build());
            breakpoint10.setEnabled(false);
            breakpoint10.setEnabled(true);

            // Breakpoints are in the install order:
            List<Breakpoint> breakpoints = session.getBreakpoints();
            Assert.assertSame(breakpoint4, breakpoints.get(0));
            Assert.assertSame(breakpoint8, breakpoints.get(1));
            Assert.assertSame(breakpoint10, breakpoints.get(2));

            session.suspendNextExecution();
            startEval(source);

            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 2, true, "STATEMENT").prepareContinue();
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 4, true, "STATEMENT").prepareContinue();
            });

            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 10, true, "STATEMENT").prepareContinue();
            });

            expectDone();

            Assert.assertEquals(1, breakpoint4.getHitCount());
            Assert.assertTrue(breakpoint4.isEnabled());
            Assert.assertEquals(0, breakpoint6.getHitCount());
            Assert.assertFalse(breakpoint6.isEnabled());
            Assert.assertEquals(0, breakpoint8.getHitCount());
            Assert.assertFalse(breakpoint8.isEnabled());
            Assert.assertEquals(1, breakpoint10.getHitCount());
            Assert.assertTrue(breakpoint10.isEnabled());
        }
    }

    @Test
    public void testInactive() throws Throwable {
        final Source source = testSource("ROOT(\n" +
                        "  STATEMENT,\n" +
                        "  STATEMENT,\n" +
                        "  STATEMENT,\n" +
                        "  STATEMENT,\n" +
                        "  STATEMENT\n" +
                        ")\n");

        // Breakpoints deactivated after the first suspend - no breakpoints are hit
        try (DebuggerSession session = startSession()) {
            Assert.assertTrue(session.isBreakpointsActive());
            // normal breakpoint
            Breakpoint breakpoint3 = session.install(Breakpoint.newBuilder(getSourceImpl(source)).lineIs(3).build());

            // disabled breakpoint
            Breakpoint breakpoint4 = session.install(Breakpoint.newBuilder(getSourceImpl(source)).lineIs(4).build());
            breakpoint4.setEnabled(false);

            // re-enabled breakpoint
            Breakpoint breakpoint5 = session.install(Breakpoint.newBuilder(getSourceImpl(source)).lineIs(5).build());
            breakpoint5.setEnabled(false);
            breakpoint5.setEnabled(true);

            session.suspendNextExecution();
            startEval(source);

            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 2, true, "STATEMENT");
                session.setBreakpointsActive(false);
            });
            expectDone();

            Assert.assertEquals(0, breakpoint3.getHitCount());
            Assert.assertEquals(0, breakpoint4.getHitCount());
            Assert.assertEquals(0, breakpoint5.getHitCount());
            Assert.assertTrue(breakpoint3.isEnabled());
            Assert.assertFalse(breakpoint4.isEnabled());
            Assert.assertTrue(breakpoint5.isEnabled());
        }

        // Breakpoints deactivated after the first one is hit - the others are not
        try (DebuggerSession session = startSession()) {
            Assert.assertTrue(session.isBreakpointsActive());
            // normal breakpoint
            Breakpoint breakpoint2 = session.install(Breakpoint.newBuilder(getSourceImpl(source)).lineIs(2).build());

            // disabled breakpoint
            Breakpoint breakpoint4 = session.install(Breakpoint.newBuilder(getSourceImpl(source)).lineIs(4).build());
            breakpoint4.setEnabled(false);

            // re-enabled breakpoint
            Breakpoint breakpoint5 = session.install(Breakpoint.newBuilder(getSourceImpl(source)).lineIs(5).build());
            breakpoint5.setEnabled(false);
            breakpoint5.setEnabled(true);

            session.suspendNextExecution();
            startEval(source);

            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 2, true, "STATEMENT");
                session.setBreakpointsActive(false);
            });
            expectDone();

            Assert.assertEquals(1, breakpoint2.getHitCount());
            Assert.assertEquals(0, breakpoint4.getHitCount());
            Assert.assertEquals(0, breakpoint5.getHitCount());
            Assert.assertTrue(breakpoint2.isEnabled());
            Assert.assertFalse(breakpoint4.isEnabled());
            Assert.assertTrue(breakpoint5.isEnabled());
        }

        // Breakpoints initially deactivated, they are activated before the last one is hit.
        try (DebuggerSession session = startSession()) {
            Assert.assertTrue(session.isBreakpointsActive());
            session.setBreakpointsActive(false);
            Assert.assertFalse(session.isBreakpointsActive());
            // normal breakpoint
            Breakpoint breakpoint2 = session.install(Breakpoint.newBuilder(getSourceImpl(source)).lineIs(2).build());

            // disabled breakpoint
            Breakpoint breakpoint4 = session.install(Breakpoint.newBuilder(getSourceImpl(source)).lineIs(4).build());
            breakpoint4.setEnabled(false);

            // re-enabled breakpoint
            Breakpoint breakpoint5 = session.install(Breakpoint.newBuilder(getSourceImpl(source)).lineIs(5).build());
            breakpoint5.setEnabled(false);
            breakpoint5.setEnabled(true);

            session.suspendNextExecution();
            startEval(source);

            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 2, true, "STATEMENT").prepareStepOver(2);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 4, true, "STATEMENT");
                session.setBreakpointsActive(true);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 5, true, "STATEMENT");
            });
            expectDone();

            Assert.assertEquals(0, breakpoint2.getHitCount());
            Assert.assertEquals(0, breakpoint4.getHitCount());
            Assert.assertEquals(1, breakpoint5.getHitCount());
            Assert.assertTrue(breakpoint2.isEnabled());
            Assert.assertFalse(breakpoint4.isEnabled());
            Assert.assertTrue(breakpoint5.isEnabled());
        }
    }

    @Test
    @SuppressWarnings("try") // auto-closeable resource session is never referenced in body of
                             // corresponding try statement
    public void testGlobalBreakpoints() throws Throwable {
        try {
            Class.forName("java.beans.PropertyChangeListener");
        } catch (ClassNotFoundException ex) {
            // skip the test if running only with java.base JDK9 module
            return;
        }

        final Source source = testSource("ROOT(\n" +
                        "  STATEMENT,\n" +
                        "  STATEMENT\n" +
                        ")\n");

        Debugger debugger = getDebugger();
        assertTrue(debugger.getBreakpoints().isEmpty());
        Breakpoint globalBreakpoint = Breakpoint.newBuilder(getSourceImpl(source)).lineIs(2).build();
        boolean[] notified = new boolean[]{false};
        BreakpointListener newBPListener = BreakpointListener.register(notified, debugger, globalBreakpoint);
        debugger.install(globalBreakpoint);
        Assert.assertTrue(notified[0]);
        Assert.assertEquals(1, debugger.getBreakpoints().size());
        Breakpoint newBP = debugger.getBreakpoints().get(0);
        Assert.assertTrue(globalBreakpoint.isModifiable());
        Assert.assertFalse(newBP.isModifiable());
        try {
            newBP.dispose();
            Assert.fail("Public dispose must not be possible for global breakpoints.");
        } catch (IllegalStateException ex) {
            // O.K.
        }
        try {
            newBP.setCondition("Something");
            Assert.fail();
        } catch (IllegalStateException ex) {
            // O.K.
        }
        try {
            newBP.setCondition(null);
            Assert.fail();
        } catch (IllegalStateException ex) {
            // O.K.
        }
        try {
            newBP.setEnabled(false);
            Assert.fail();
        } catch (IllegalStateException ex) {
            // O.K.
        }
        try {
            newBP.setIgnoreCount(10);
            Assert.fail();
        } catch (IllegalStateException ex) {
            // O.K.
        }
        Assert.assertNull(newBP.getCondition());
        Assert.assertEquals(0, newBP.getHitCount());
        Assert.assertTrue(newBP.isEnabled());
        Assert.assertFalse(newBP.isDisposed());
        Assert.assertFalse(newBP.isResolved());

        try (DebuggerSession session = startSession()) {
            // global breakpoint is not among session breakpoints
            Assert.assertTrue(session.getBreakpoints().isEmpty());

            startEval(source);

            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 2, true, "STATEMENT");
                assertEquals(1, event.getBreakpoints().size());
                Breakpoint eventBP = event.getBreakpoints().get(0);
                try {
                    eventBP.dispose();
                    Assert.fail("Public dispose must not be possible for global breakpoints.");
                } catch (IllegalStateException ex) {
                    // O.K.
                }
                Assert.assertTrue(eventBP.isResolved());
                event.prepareContinue();
            });
        }

        assertFalse(newBP.isDisposed());
        assertFalse(newBP.isResolved());
        expectDone();

        try (DebuggerSession session = startSession()) {
            startEval(source);
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 2, true, "STATEMENT");
                event.prepareContinue();
            });
        }
        expectDone();

        newBPListener.unregister();
        notified[0] = false;
        BreakpointDisposeListener.register(notified, debugger, globalBreakpoint);
        globalBreakpoint.dispose();
        Assert.assertTrue(notified[0]);
        Assert.assertEquals(0, debugger.getBreakpoints().size());
    }

    @Test
    public void testGlobalBreakpointsInMultipleSessions() throws Throwable {
        try {
            Class.forName("java.beans.PropertyChangeListener");
        } catch (ClassNotFoundException ex) {
            // skip the test if running only with java.base JDK9 module
            return;
        }

        final Source source = testSource("ROOT(\n" +
                        "  STATEMENT,\n" +
                        "  STATEMENT,\n" +
                        "  STATEMENT,\n" +
                        "  STATEMENT,\n" +
                        "  STATEMENT,\n" +
                        "  STATEMENT,\n" +
                        "  STATEMENT,\n" +
                        "  STATEMENT,\n" +
                        "  STATEMENT,\n" +
                        "  STATEMENT\n" +
                        ")\n");
        Debugger debugger = getDebugger();
        Breakpoint globalBreakpoint1 = Breakpoint.newBuilder(getSourceImpl(source)).lineIs(2).build();
        debugger.install(globalBreakpoint1);
        Breakpoint globalBreakpoint2 = Breakpoint.newBuilder(getSourceImpl(source)).lineIs(4).build();
        debugger.install(globalBreakpoint2);
        Breakpoint globalBreakpoint3 = Breakpoint.newBuilder(getSourceImpl(source)).lineIs(6).build();
        debugger.install(globalBreakpoint3);
        Breakpoint globalBreakpoint4 = Breakpoint.newBuilder(getSourceImpl(source)).lineIs(8).build();
        debugger.install(globalBreakpoint4);
        // Breakpoints are in the install order:
        List<Breakpoint> breakpoints = debugger.getBreakpoints();
        Assert.assertEquals(4, breakpoints.size());
        Assert.assertTrue(breakpoints.get(0).getLocationDescription().contains("line=2"));
        Assert.assertTrue(breakpoints.get(1).getLocationDescription().contains("line=4"));
        Assert.assertTrue(breakpoints.get(2).getLocationDescription().contains("line=6"));

        DebuggerSession session1 = startSession();
        // global breakpoints are not among session breakpoints
        Assert.assertTrue(session1.getBreakpoints().isEmpty());

        try (DebuggerSession session2 = startSession()) {
            // global breakpoints are not among session breakpoints
            Assert.assertTrue(session2.getBreakpoints().isEmpty());

            startEval(source);

            // Both sessions should break here:
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 2, true, "STATEMENT").prepareContinue();
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 2, true, "STATEMENT").prepareContinue();
            });
            // We close session2 after the next BP:
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 4, true, "STATEMENT");
            });
        }

        expectSuspended((SuspendedEvent event) -> {
            checkState(event, 4, true, "STATEMENT").prepareContinue();
        });

        // The last breakpoint is hit once only in the session1:
        expectSuspended((SuspendedEvent event) -> {
            checkState(event, 6, true, "STATEMENT").prepareStepOver(1);
        });
        expectSuspended((SuspendedEvent event) -> {
            checkState(event, 7, true, "STATEMENT");
            session1.close();
            event.prepareContinue();
        });
        // Breakpoint at line 8 was not hit at all, the session was closed right before continue
        // from line 7.
        expectDone();

    }

}
