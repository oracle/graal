/*
 * Copyright (c) 2016, 2023, Oracle and/or its affiliates. All rights reserved.
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
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.net.URI;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.debug.Breakpoint;
import com.oracle.truffle.api.debug.DebugScope;
import com.oracle.truffle.api.debug.DebugValue;
import com.oracle.truffle.api.debug.Debugger;
import com.oracle.truffle.api.debug.DebuggerSession;
import com.oracle.truffle.api.debug.SourceElement;
import com.oracle.truffle.api.debug.SuspendAnchor;
import com.oracle.truffle.api.debug.SuspendedEvent;
import com.oracle.truffle.api.debug.SuspensionFilter;
import com.oracle.truffle.api.instrumentation.test.InstrumentationTestLanguage;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;
import com.oracle.truffle.tck.DebuggerTester;
import com.oracle.truffle.tck.tests.TruffleTestAssumptions;

public class BreakpointTest extends AbstractDebugTest {

    @Test
    public void testBreakpointDefaults() {
        Source testSource = testSource("STATEMENT");
        Breakpoint breakpoint = Breakpoint.newBuilder(getSourceImpl(testSource)).lineIs(1).build();
        assertEquals(0, breakpoint.getHitCount());
        assertEquals(0, breakpoint.getIgnoreCount());
        assertEquals(SuspendAnchor.BEFORE, breakpoint.getSuspendAnchor());
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
                assertSame(SuspendAnchor.BEFORE, event.getSuspendAnchor());
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
    public void testBreakpointAfter() {
        Source testSource = testSource("ROOT(\n" +
                        "STATEMENT,\n" +
                        "STATEMENT(CONSTANT(10)))");
        Breakpoint breakpoint2 = Breakpoint.newBuilder(getSourceImpl(testSource)).lineIs(2).suspendAnchor(SuspendAnchor.AFTER).build();
        Breakpoint breakpoint3a = Breakpoint.newBuilder(getSourceImpl(testSource)).lineIs(3).suspendAnchor(SuspendAnchor.BEFORE).build();
        Breakpoint breakpoint3b = Breakpoint.newBuilder(getSourceImpl(testSource)).lineIs(3).suspendAnchor(SuspendAnchor.AFTER).build();
        assertEquals(SuspendAnchor.AFTER, breakpoint2.getSuspendAnchor());
        try (DebuggerSession session = startSession()) {
            session.install(breakpoint2);
            session.install(breakpoint3a);
            session.install(breakpoint3b);

            startEval(testSource);
            expectSuspended((SuspendedEvent event) -> {
                assertSame(breakpoint2, event.getBreakpoints().iterator().next());
                assertSame(SuspendAnchor.AFTER, event.getSuspendAnchor());
                assertEquals("Null", event.getReturnValue().toDisplayString());
            });
            expectSuspended((SuspendedEvent event) -> {
                assertSame(breakpoint3a, event.getBreakpoints().iterator().next());
                assertSame(SuspendAnchor.BEFORE, event.getSuspendAnchor());
                assertNull(event.getReturnValue());
            });
            expectSuspended((SuspendedEvent event) -> {
                assertSame(breakpoint3b, event.getBreakpoints().iterator().next());
                assertSame(SuspendAnchor.AFTER, event.getSuspendAnchor());
                assertEquals("10", event.getReturnValue().toDisplayString());
            });
        }
        expectDone();
    }

    @Test
    public void testBreakpointCondition() {
        Source testSource = testSource("ROOT(\n" +
                        "STATEMENT,\n" +
                        "STATEMENT,\n" +
                        "STATEMENT)");

        try (DebuggerSession session = startSession()) {
            Breakpoint breakpoint = session.install(Breakpoint.newBuilder(getSourceImpl(testSource)).lineIs(2).build());
            // No condition initially:
            startEval(testSource);
            expectSuspended((SuspendedEvent event) -> {
                assertSame(breakpoint, event.getBreakpoints().iterator().next());
                assertNull(event.getBreakpointConditionException(breakpoint));
            });
            assertEquals(1, breakpoint.getHitCount());
            expectDone();

            breakpoint.setCondition("CONSTANT(true)");

            startEval(testSource);
            expectSuspended((SuspendedEvent event) -> {
                assertSame(breakpoint, event.getBreakpoints().iterator().next());
                assertNull(event.getBreakpointConditionException(breakpoint));
            });
            assertEquals(2, breakpoint.getHitCount());
            expectDone();

            breakpoint.setCondition("CONSTANT(false)");
            startEval(testSource);
            expectDone();
            assertEquals(2, breakpoint.getHitCount());

            breakpoint.setCondition(null); // remove the condition
            startEval(testSource);
            expectSuspended((SuspendedEvent event) -> {
                assertSame(breakpoint, event.getBreakpoints().iterator().next());
                assertNull(event.getBreakpointConditionException(breakpoint));
            });
            assertEquals(3, breakpoint.getHitCount());
            expectDone();

            breakpoint.setCondition("CONSTANT("); // error by parse exception
            startEval(testSource);
            expectSuspended((SuspendedEvent event) -> {
                assertSame(breakpoint, event.getBreakpoints().iterator().next());
                assertNotNull(event.getBreakpointConditionException(breakpoint));
            });
            assertEquals(4, breakpoint.getHitCount());
            expectDone();
        }
    }

    @Test
    public void testNotStepIntoBreakpointCondition() {
        Source defineSource = testSource("ROOT(DEFINE(test, ROOT(\n" +
                        "STATEMENT(EXPRESSION),\n" +
                        "CONSTANT(true))))");
        Source testSource = testSource("ROOT(\n" +
                        "STATEMENT,\n" +
                        "STATEMENT,\n" +
                        "STATEMENT)");
        try (DebuggerSession session = startSession()) {
            startEval(defineSource);
            expectDone();
            Breakpoint breakpoint = session.install(Breakpoint.newBuilder(getSourceImpl(testSource)).lineIs(3).build());
            breakpoint.setCondition("CALL(test)");
            session.suspendNextExecution();
            startEval(testSource);
            expectSuspended((SuspendedEvent event) -> {
                assertEquals("STATEMENT", event.getSourceSection().getCharacters());
                assertEquals(2, event.getSourceSection().getStartLine());
                assertEquals(0, event.getBreakpoints().size());
                event.prepareStepInto(1);
            });
            assertEquals(0, breakpoint.getHitCount());
            expectSuspended((SuspendedEvent event) -> {
                assertEquals("STATEMENT", event.getSourceSection().getCharacters());
                assertEquals(3, event.getSourceSection().getStartLine());
                assertEquals(1, event.getBreakpoints().size());
                event.prepareStepInto(1);
            });
            assertEquals(1, breakpoint.getHitCount());
            expectSuspended((SuspendedEvent event) -> {
                assertEquals("STATEMENT", event.getSourceSection().getCharacters());
                assertEquals(4, event.getSourceSection().getStartLine());
                assertEquals(0, event.getBreakpoints().size());
                event.prepareStepInto(1);
            });
            expectDone();
            assertEquals(1, breakpoint.getHitCount());
        }
    }

    @Test
    public void testBreakpointConditionExecutedOnce() {
        Source testSource = testSource("ROOT(\n" +
                        "STATEMENT,\n" +
                        "STATEMENT,\n" +
                        "STATEMENT)");

        try (DebuggerSession session = startSession()) {
            Breakpoint breakpoint = session.install(Breakpoint.newBuilder(getSourceImpl(testSource)).lineIs(3).build());
            breakpoint.setCondition("ROOT(PRINT(OUT, CONSTANT(\"Hi\")), CONSTANT(true))");
            // Breakpoint only:
            startEval(testSource);
            expectSuspended((SuspendedEvent event) -> {
                assertSame(breakpoint, event.getBreakpoints().iterator().next());
                assertNull(event.getBreakpointConditionException(breakpoint));
            });
            assertEquals(1, breakpoint.getHitCount());
            expectDone();
            assertEquals("Hi", getOutput());

            // Breakpoint with step and an other breakpoint:
            Breakpoint breakpoint2 = session.install(Breakpoint.newBuilder(getSourceImpl(testSource)).lineIs(3).build());
            session.suspendNextExecution();
            startEval(testSource);
            expectSuspended((SuspendedEvent event) -> {
                assertTrue(event.getBreakpoints().isEmpty());
                event.prepareStepOver(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                assertEquals(2, event.getBreakpoints().size());
                assertTrue(event.getBreakpoints().contains(breakpoint));
                assertTrue(event.getBreakpoints().contains(breakpoint2));
                assertNull(event.getBreakpointConditionException(breakpoint));
                assertNull(event.getBreakpointConditionException(breakpoint2));
                event.prepareStepOver(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                assertTrue(event.getBreakpoints().isEmpty());
                event.prepareStepOver(1);
            });
            expectDone();
            assertEquals("HiHi", getOutput());
            assertEquals(2, breakpoint.getHitCount());
            assertEquals(1, breakpoint2.getHitCount());
        }
    }

    @Test
    public void testBreakpointsAtSamePlaceHitCorrectly() {
        Source testSource = testSource("ROOT(\n" +
                        "  LOOP(4,\n" +
                        "    STATEMENT\n" +
                        "  )\n" +
                        ")\n");
        String conditionTrue = "ROOT(PRINT(OUT, CONSTANT(\"CT\")), CONSTANT(true))";
        String conditionFalse = "ROOT(PRINT(OUT, CONSTANT(\"CF\")), CONSTANT(false))";
        boolean isBefore = true;
        String prefix = "";
        do {
            SuspendAnchor anchor = isBefore ? SuspendAnchor.BEFORE : SuspendAnchor.AFTER;
            try (DebuggerSession session = startSession()) {
                Breakpoint breakpoint1 = session.install(Breakpoint.newBuilder(getSourceImpl(testSource)).lineIs(3).suspendAnchor(anchor).build());
                Breakpoint breakpoint2 = session.install(Breakpoint.newBuilder(getSourceImpl(testSource)).lineIs(3).suspendAnchor(anchor).build());
                breakpoint1.setCondition(conditionFalse);
                breakpoint2.setCondition(conditionTrue);
                startEval(testSource);

                final String out1 = prefix + ((isBefore) ? "CFCT" : "CTCF");
                expectSuspended((SuspendedEvent event) -> {
                    assertEquals(1, event.getBreakpoints().size());
                    Breakpoint hit = event.getBreakpoints().get(0);
                    assertSame(breakpoint2, hit);
                    assertEquals(out1, getOutput());
                });
                final String out2 = out1 + ((isBefore) ? "CFCT" : "CTCF");
                expectSuspended((SuspendedEvent event) -> {
                    assertEquals(1, event.getBreakpoints().size());
                    Breakpoint hit = event.getBreakpoints().get(0);
                    assertSame(breakpoint2, hit);
                    breakpoint1.setCondition(conditionTrue);
                    breakpoint2.setCondition(conditionFalse);
                    assertEquals(out2, getOutput());
                });
                final String out3 = out2 + ((isBefore) ? "CTCF" : "CFCT");
                expectSuspended((SuspendedEvent event) -> {
                    assertEquals(1, event.getBreakpoints().size());
                    Breakpoint hit = event.getBreakpoints().get(0);
                    assertSame(breakpoint1, hit);
                    breakpoint1.setCondition(null);
                    breakpoint2.setCondition(null);
                    assertEquals(out3, getOutput());
                });
                expectSuspended((SuspendedEvent event) -> {
                    assertEquals(2, event.getBreakpoints().size());
                });
                expectDone();
                prefix += out3;
                assertEquals(out3, getOutput());
                assertEquals(2, breakpoint1.getHitCount());
                assertEquals(3, breakpoint2.getHitCount());
            }
        } while (!(isBefore = !isBefore));
    }

    @Test
    public void testMultiSessionBreakpointConditionExecutedOnce() {
        Source testSource = testSource("ROOT(\n" +
                        "STATEMENT,\n" +
                        "STATEMENT,\n" +
                        "STATEMENT)");

        try (DebuggerSession session1 = startSession()) {
            Breakpoint breakpoint1 = session1.install(Breakpoint.newBuilder(getSourceImpl(testSource)).lineIs(3).build());
            breakpoint1.setCondition("ROOT(PRINT(OUT, CONSTANT(\"Hi1\")), CONSTANT(true))");
            try (DebuggerSession session2 = startSession()) {
                session2.install(breakpoint1);
                try (DebuggerSession session3 = startSession()) {
                    Breakpoint breakpoint3 = session3.install(Breakpoint.newBuilder(getSourceImpl(testSource)).lineIs(3).build());
                    breakpoint3.setCondition("ROOT(PRINT(OUT, CONSTANT(\"Hi3\")), CONSTANT(true))");
                    session3.suspendNextExecution();
                    startEval(testSource);
                    expectSuspended((SuspendedEvent event) -> {
                        assertSame(session3, event.getSession());
                        assertTrue(event.getBreakpoints().isEmpty());
                        event.prepareStepOver(1);
                    });
                    expectSuspended((SuspendedEvent event) -> {
                        assertSame(session3, event.getSession());
                        assertEquals(1, event.getBreakpoints().size());
                        assertSame(breakpoint3, event.getBreakpoints().get(0));
                    });
                    expectSuspended((SuspendedEvent event) -> {
                        assertSame(session1, event.getSession());
                        assertEquals(1, event.getBreakpoints().size());
                        assertSame(breakpoint1, event.getBreakpoints().get(0));
                    });
                    expectSuspended((SuspendedEvent event) -> {
                        assertSame(session2, event.getSession());
                        assertEquals(1, event.getBreakpoints().size());
                        assertSame(breakpoint1, event.getBreakpoints().get(0));
                    });
                    expectDone();
                    assertEquals("Hi3Hi1", getOutput());
                    assertEquals(1, breakpoint1.getHitCount());
                    assertEquals(1, breakpoint3.getHitCount());
                }
            }
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
                int finalIndex = i;
                expectSuspended((SuspendedEvent event) -> {
                    checkState(event, 4, true, "STATEMENT", "loopIndex0", String.valueOf(finalIndex), "loopResult0", "Null").prepareContinue();
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
                int finalIndex = i;
                expectSuspended((SuspendedEvent event) -> {
                    checkState(event, 4, true, "STATEMENT", "loopIndex0", String.valueOf(finalIndex), "loopResult0", "Null").prepareContinue();
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
                checkState(event, 3, true, "STATEMENT", "loopIndex0", "0", "loopResult0", "Null");
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
            Assert.assertTrue(session.isBreakpointsActive(Breakpoint.Kind.SOURCE_LOCATION));
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
                session.setBreakpointsActive(Breakpoint.Kind.SOURCE_LOCATION, false);
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
            Assert.assertTrue(session.isBreakpointsActive(Breakpoint.Kind.SOURCE_LOCATION));
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
                session.setBreakpointsActive(Breakpoint.Kind.SOURCE_LOCATION, false);
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
            Assert.assertTrue(session.isBreakpointsActive(Breakpoint.Kind.SOURCE_LOCATION));
            session.setBreakpointsActive(Breakpoint.Kind.SOURCE_LOCATION, false);
            Assert.assertFalse(session.isBreakpointsActive(Breakpoint.Kind.SOURCE_LOCATION));
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
                session.setBreakpointsActive(Breakpoint.Kind.SOURCE_LOCATION, true);
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
                assertSame(session1, event.getSession());
                checkState(event, 2, true, "STATEMENT").prepareContinue();
            });
            expectSuspended((SuspendedEvent event) -> {
                assertSame(session2, event.getSession());
                checkState(event, 2, true, "STATEMENT").prepareContinue();
            });
            // We close session2 after the next BP:
            expectSuspended((SuspendedEvent event) -> {
                assertSame(session1, event.getSession());
                checkState(event, 4, true, "STATEMENT");
            });
        }

        expectSuspended((SuspendedEvent event) -> {
            assertNotSame(session1, event.getSession());
            checkState(event, 4, true, "STATEMENT").prepareContinue();
        });

        // The last breakpoint is hit once only in the session1:
        expectSuspended((SuspendedEvent event) -> {
            assertSame(session1, event.getSession());
            checkState(event, 6, true, "STATEMENT").prepareStepOver(1);
        });
        expectSuspended((SuspendedEvent event) -> {
            assertSame(session1, event.getSession());
            checkState(event, 7, true, "STATEMENT");
            session1.close();
            event.prepareContinue();
        });
        // Breakpoint at line 8 was not hit at all, the session was closed right before continue
        // from line 7.
        expectDone();

    }

    /**
     * Runs repeated evaluations of the provided Source in a separate thread, until join() is
     * called.
     */
    private static final class EvalLoop {

        private final AtomicBoolean evaluating = new AtomicBoolean(true);
        private final Thread evalLoop;
        private final AtomicReference<Throwable> evalThrowable = new AtomicReference<>();

        EvalLoop(DebuggerTester tester, Source source) {
            evalLoop = new Thread(() -> {
                while (evaluating.get()) {
                    tester.startEval(source);
                    boolean suspended;
                    do {
                        suspended = false;
                        try {
                            tester.expectDone();
                        } catch (AssertionError ae) {
                            if (ae.getCause() != null && ae.getCause().getMessage().startsWith("Expected done but got event Suspended at")) {
                                // We've hit the breakpoint. We'll resume automatically.
                                suspended = true;
                            } else {
                                throw ae;
                            }
                        }
                    } while (suspended);
                }
            });
            evalLoop.setUncaughtExceptionHandler((thread, thrwbl) -> evalThrowable.set(thrwbl));
            evalLoop.start();
        }

        void join() throws InterruptedException, ExecutionException {
            evaluating.set(false);
            evalLoop.join();
            if (evalThrowable.get() != null) {
                throw new ExecutionException(evalThrowable.get());
            }
        }
    }

    @Test
    public void testBreakpointInstallDuringDispose() throws InterruptedException, ExecutionException {
        final Source source = testSource("ROOT(\n" +
                        "  LOOP(1000,\n" +
                        "    STATEMENT)\n" + // break here
                        ")\n");
        try (DebuggerSession session = startSession()) {
            EvalLoop evalLoop = new EvalLoop(tester, source);
            ExecutorService exec = Executors.newSingleThreadExecutor();
            for (int repeatCount = 0; repeatCount <= 1000; repeatCount++) {
                Breakpoint breakpoint = Breakpoint.newBuilder(getSourceImpl(source)).lineIs(3).build();
                breakpoint.setEnabled(false);
                session.install(breakpoint);
                Future<?> installation = exec.submit(() -> {
                    breakpoint.setEnabled(true);
                });
                Thread.sleep(0, repeatCount % 100);
                breakpoint.dispose();
                installation.get();
            }
            exec.shutdown();
            // We want the test infrastructure to interrupt this to see the full thread dump.
            exec.awaitTermination(1, TimeUnit.DAYS);
            evalLoop.join();
        }
    }

    @Test
    public void testBreakpointParallelInstallDispose() throws InterruptedException, ExecutionException {
        // Install and dispose of breakpoints parallel with the execution.
        final Source source = testSource("ROOT(\n" +
                        "  LOOP(1000,\n" +
                        "    STATEMENT)\n" + // break here
                        ")\n");
        try (DebuggerSession session = startSession()) {
            EvalLoop evalLoop = new EvalLoop(tester, source);
            for (int repeatCount = 0; repeatCount <= 100; repeatCount++) {
                Breakpoint breakpoint = Breakpoint.newBuilder(getSourceImpl(source)).lineIs(3).build();
                session.install(breakpoint);
                breakpoint.dispose();
            }
            evalLoop.join();
        }
    }

    @Test
    public void testResolveListener() {
        final Source source = testSource("ROOT(\n" +
                        "  STATEMENT,\n" +
                        "  STATEMENT,\n" +
                        "  STATEMENT,\n" + // break here
                        "  STATEMENT,\n" +
                        "  STATEMENT\n" +
                        ")\n");
        Breakpoint sessionBreakpoint = null;
        try (DebuggerSession session = startSession()) {
            Breakpoint[] resolvedBp = new Breakpoint[1];
            SourceSection[] resolvedSection = new SourceSection[1];
            Breakpoint.ResolveListener bpResolveListener = (Breakpoint breakpoint, SourceSection section) -> {
                resolvedBp[0] = breakpoint;
                resolvedSection[0] = section;
            };
            Breakpoint breakpoint = session.install(Breakpoint.newBuilder(getSourceImpl(source)).lineIs(4).resolveListener(bpResolveListener).build());
            Assert.assertNull(resolvedBp[0]);
            Assert.assertNull(resolvedSection[0]);
            sessionBreakpoint = breakpoint;
            startEval(source);
            expectSuspended((SuspendedEvent event) -> {
                Assert.assertSame(breakpoint, resolvedBp[0]);
                Assert.assertEquals(event.getSourceSection(), resolvedSection[0]);
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
    public void testBreakAtExpressions() {
        final Source source = testSource("ROOT(\n" +
                        "  STATEMENT,\n" +
                        "  EXPRESSION,\n" +
                        "  STATEMENT\n" +
                        ")\n");
        try (DebuggerSession session = startSession()) {
            Breakpoint breakpoint = Breakpoint.newBuilder(getSourceImpl(source)).lineIs(3).sourceElements(SourceElement.EXPRESSION).build();
            session.install(breakpoint);
            startEval(source);
            expectSuspended((SuspendedEvent event) -> {
                Assert.assertSame(breakpoint, event.getBreakpoints().get(0));
                checkState(event, 3, true, "EXPRESSION");
            });
            expectDone();
        }
        try (DebuggerSession session = startSession()) {
            Breakpoint breakpoint = Breakpoint.newBuilder(getSourceImpl(source)).lineIs(2).sourceElements(SourceElement.EXPRESSION).build();
            // Will be moved from line 2 to the expression at line 3.
            session.install(breakpoint);
            startEval(source);
            expectSuspended((SuspendedEvent event) -> {
                Assert.assertSame(breakpoint, event.getBreakpoints().get(0));
                checkState(event, 3, true, "EXPRESSION");
            });
            expectDone();
        }
    }

    @Test
    public void testBreakAtMultipleSourceElements() {
        final Source source = testSource("ROOT(\n" +
                        "  STATEMENT,EXPRESSION,\n" +
                        "  EXPRESSION,STATEMENT,\n" +
                        "  STATEMENT,EXPRESSION\n" +
                        ")\n");
        try (DebuggerSession session = startSession()) {
            Breakpoint breakpoint = Breakpoint.newBuilder(getSourceImpl(source)).lineIs(3).sourceElements(SourceElement.STATEMENT, SourceElement.EXPRESSION).build();
            session.install(breakpoint);
            startEval(source);
            expectSuspended((SuspendedEvent event) -> {
                Assert.assertSame(breakpoint, event.getBreakpoints().get(0));
                checkState(event, 3, true, "EXPRESSION");
            });
            expectSuspended((SuspendedEvent event) -> {
                Assert.assertSame(breakpoint, event.getBreakpoints().get(0));
                checkState(event, 3, true, "STATEMENT");
            });
            expectDone();
        }
    }

    @Test
    public void testMisplacedLineBreakpoints() throws Exception {
        final String source = "ROOT(\n" +
                        "  DEFINE(foo,\n" +
                        "    R2-3_STATEMENT,\n" +
                        "    EXPRESSION,\n" +
                        "    DEFINE(fooinner,\n" +
                        "      VARIABLE(n, 10),\n" +
                        "      \n" +
                        "      R5-9_STATEMENT\n" +
                        "    ),\n" +
                        "    R4_R10-12_STATEMENT(EXPRESSION),\n" +
                        "    CALL(fooinner)\n" +
                        "  ),\n" +
                        "  \n" +
                        "  R1_R13-16_STATEMENT,\n" +
                        "  CALL(foo)\n" +
                        ")\n";
        tester.assertLineBreakpointsResolution(source, "R", InstrumentationTestLanguage.ID);
    }

    @Test
    public void testMisplacedLineBreakpoints2() throws Exception {
        // Test that breakpoints are moved to suspendable positions when we need to dive into
        // surrounding nodes.
        final String source = "ROOT(\n" +
                        "  LOOP(2,\n" +
                        "    EXPRESSION),\n" +
                        "\n" +
                        "  LOOP(3,\n" +
                        "    EXPRESSION),\n" +
                        "\n" +
                        "  LOOP(1,\n" +
                        "    R1-9_STATEMENT),\n" +
                        "\n" +
                        "  LOOP(3,\n" +
                        "    EXPRESSION),\n" +
                        "\n" +
                        "  LOOP(2,\n" +
                        "    EXPRESSION),\n" +
                        "\n" +
                        "  LOOP(1, LOOP(1,\n" +
                        "    R10-25_STATEMENT)),\n" +
                        "\n" +
                        "  LOOP(3,\n" +
                        "    EXPRESSION),\n" +
                        "\n" +
                        "  LOOP(2,\n" +
                        "    EXPRESSION)\n" +
                        ")\n";
        tester.assertLineBreakpointsResolution(source, "R", InstrumentationTestLanguage.ID);
    }

    @Test
    public void testMisplacedLineBreakpointsComplex() throws Exception {
        // Test that breakpoints are moved to suspendable positions when we need to dive into
        // surrounding nodes.
        final String source = "ROOT(\n" +
                        "  EXPRESSION,\n" +
                        "  EXPRESSION(\n" +
                        "    R1-4_STATEMENT,\n" +
                        "    EXPRESSION,\n" +       // 5
                        "    R5-7_STATEMENT,\n" +
                        "    EXPRESSION),\n" +
                        "\n" +
                        "  R8-9_STATEMENT,\n" +
                        "  LOOP(1,\n" +             // 10
                        "    EXPRESSION,\n" +
                        "    LOOP(1,\n" +
                        "      R10-14_STATEMENT),\n" +
                        "    EXPRESSION),\n" +
                        "\n" +                      // 15
                        "  LOOP(1,\n" +
                        "    LOOP(1,\n" +
                        "      EXPRESSION)),\n" +
                        "  EXPRESSION(\n" +
                        "    LOOP(1,\n" +           // 20
                        "      R15-21_STATEMENT,\n" +
                        "      LOOP(1,\n" +
                        "        EXPRESSION,\n" +
                        "        R22-24_STATEMENT,\n" +
                        "        EXPRESSION(\n" +   // 25
                        "          R25-29_STATEMENT),\n" +
                        "        EXPRESSION),\n" +
                        "      EXPRESSION),\n" +
                        "    EXPRESSION),\n" +
                        "  EXPRESSION,\n" +         // 30
                        "  LOOP(1,\n" +
                        "    R30-32_STATEMENT),\n" +
                        "\n" +
                        "  LOOP(1,\n" +
                        "    LOOP(1,\n" +           // 35
                        "      EXPRESSION(\n" +
                        "        R33-37_STATEMENT),\n" +
                        "      R38_STATEMENT),\n" +
                        "    R39-43_STATEMENT),\n" +
                        "\n" +                      // 40
                        "  LOOP(1,\n" +
                        "    EXPRESSION)\n" +
                        ")\n";
        tester.assertLineBreakpointsResolution(source, "R", InstrumentationTestLanguage.ID);
    }

    @Test
    public void testMisplacedBreakpointPositions() throws Exception {
        String source = " B1_{} R1-2_{S B2_}B3_\n" +
                        "R3_[SFR ]\n" +
                        "{F{R\n" +
                        "  B4_{I B5_ } R4-5_[SFIR B6_ R6-7_{S}B7_] B8_\n" +
                        "  {}\n" +
                        "  R8-11_{S}\n" +
                        "B9_}B10_}B11_\n";
        assertColumnPositionsTest(source);
    }

    private void assertColumnPositionsTest(String source) throws Exception {
        tester.assertColumnBreakpointsResolution(source, "B", "R", InstrumentablePositionsTestLanguage.ID);
        tester.close();
        // Different materialization changes the order of nodes that are processed during search for
        // the nearest suspendable location of a breakpoint.
        tester = new DebuggerTester(org.graalvm.polyglot.Context.newBuilder().allowExperimentalOptions(true).option(InstrumentablePositionsTestLanguage.ID + ".PreMaterialize", "1"));
        tester.assertColumnBreakpointsResolution(source, "B", "R", InstrumentablePositionsTestLanguage.ID);
        tester.close();
        tester = new DebuggerTester(org.graalvm.polyglot.Context.newBuilder().allowExperimentalOptions(true).option(InstrumentablePositionsTestLanguage.ID + ".PreMaterialize", "2"));
        tester.assertColumnBreakpointsResolution(source, "B", "R", InstrumentablePositionsTestLanguage.ID);
        // Source without content, with a relative source path
        tester = new DebuggerTester(org.graalvm.polyglot.Context.newBuilder().allowExperimentalOptions(true).option(InstrumentablePositionsTestLanguage.ID + ".SourceRoot", "a_relative/path"));
        tester.assertColumnBreakpointsResolution(source, "B", "R", InstrumentablePositionsTestLanguage.ID, URI.create("a_relative/path"));
    }

    @Test
    public void testOutOfRootBreakpointPositions() {
        String source = "  \n" +
                        " B1_     < {F R1_[SRB]\n" +
                        "B2_R2_{S}  } \n" +
                        "\n";
        tester.assertColumnBreakpointsResolution(source, "B", "R", InstrumentablePositionsTestLanguage.ID);
    }

    @Test
    public void testFunctionSensitiveBreakpoints1() throws Exception {
        // Test ROOT breakpoint on foo0
        Source source = testSource("ROOT(\n" +
                        "  DEFINE(foo0, ROOT()),\n" +
                        "  DEFINE(foo1, ROOT()),\n" +
                        "  STATEMENT,\n" +
                        "  CALL(foo0),\n" +
                        "  CALL(foo1)\n" +
                        ")\n");
        try (DebuggerSession session = startSession()) {
            session.suspendNextExecution();
            startEval(source);
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 4, true, "STATEMENT");
                // Retrieve the function instance
                DebugScope functionScope = session.getTopScope(source.getLanguage());
                DebugValue foo0 = functionScope.getDeclaredValue("foo0");
                Breakpoint breakpoint = Breakpoint.newBuilder(getSourceImpl(source)).rootInstance(foo0).sourceElements(SourceElement.ROOT).build();
                session.install(breakpoint);
            });
            expectSuspended((SuspendedEvent event) -> {
                // Suspend in foo0 only, not in foo1:
                checkState(event, 2, true, " ROOT()");
                assertEquals(1, event.getBreakpoints().size());
            });
            expectDone();
        }
    }

    @Test
    public void testFunctionSensitiveBreakpoints2() throws Exception {
        // Test all elements breakpoints on both foo0 and foo1.
        // Both breakpoints are hit just once in every function.
        Source source = testSource("ROOT(\n" +
                        "  DEFINE(\n" +
                        "    foo0, ROOT(\n" +
                        "      STATEMENT,\n" +
                        "      EXPRESSION)\n" +
                        "  ),\n" +
                        "  DEFINE(\n" +
                        "    foo1, ROOT(\n" +
                        "      STATEMENT,\n" +
                        "      EXPRESSION)\n" +
                        "  ),\n" +
                        "  STATEMENT,\n" +
                        "  CALL(foo0),\n" +
                        "  CALL(foo1)\n" +
                        ")\n");
        try (DebuggerSession session = startSession()) {
            session.suspendNextExecution();
            startEval(source);
            String[] functions = new String[]{"foo0", "foo1"};
            final Breakpoint[] breakpoints = new Breakpoint[2];
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 12, true, "STATEMENT");
                // Retrieve the function instances
                DebugScope functionScope = session.getTopScope(source.getLanguage());
                for (int f = 0; f < functions.length; f++) {
                    DebugValue foo = functionScope.getDeclaredValue(functions[f]);
                    // Create breakpoints for all source elements:
                    breakpoints[f] = Breakpoint.newBuilder(getSourceImpl(source)).rootInstance(foo).sourceElements(SourceElement.values()).build();
                    session.install(breakpoints[f]);
                }
            });
            for (int f = 0; f < functions.length; f++) {
                int ff = f;
                for (int iElem = 0; iElem < SourceElement.values().length; iElem++) {
                    int expectedLine = 5 * f + 3 + iElem;
                    expectSuspended((SuspendedEvent event) -> {
                        assertEquals(expectedLine, event.getSourceSection().getStartLine());
                        List<Breakpoint> bpHit = event.getBreakpoints();
                        assertEquals(1, bpHit.size());
                        assertSame(breakpoints[ff], bpHit.get(0));
                    });
                }
            }
            expectDone();
        }
    }

    @Test
    public void testFunctionSensitiveBreakpoints3() throws Exception {
        // Test line breakpoints in foo0 and foo1.
        // Only 2 out of 4 breakpoints are hit.
        Source source = testSource("ROOT(\n" +
                        "  DEFINE(foo0,\n" +
                        "    ROOT(\n" +
                        "      STATEMENT,\n" +
                        "      EXPRESSION)\n" +
                        "  ),\n" +
                        "  DEFINE(foo1,\n" +
                        "    ROOT(\n" +
                        "      STATEMENT,\n" +
                        "      EXPRESSION)\n" +
                        "  ),\n" +
                        "  STATEMENT,\n" +
                        "  CALL(foo0),\n" +
                        "  CALL(foo1)\n" +
                        ")\n");
        try (DebuggerSession session = startSession()) {
            session.suspendNextExecution();
            startEval(source);
            String[] functions = new String[]{"foo0", "foo1"};
            final Breakpoint[][] breakpoints = new Breakpoint[2][2];
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 12, true, "STATEMENT");
                // Retrieve the function instances
                DebugScope functionScope = session.getTopScope(source.getLanguage());
                for (int f = 0; f < functions.length; f++) {
                    DebugValue foo = functionScope.getDeclaredValue(functions[f]);
                    // Create breakpoints for two lines in the two functions:
                    breakpoints[f][0] = Breakpoint.newBuilder(getSourceImpl(source)).rootInstance(foo).lineIs(4).build();
                    breakpoints[f][1] = Breakpoint.newBuilder(getSourceImpl(source)).rootInstance(foo).lineIs(9).build();
                    for (Breakpoint b : breakpoints[f]) {
                        session.install(b);
                    }
                }
            });
            for (int f = 0; f < functions.length; f++) {
                int ff = f;
                int expectedLine = 5 * f + 4;
                expectSuspended((SuspendedEvent event) -> {
                    assertEquals(expectedLine, event.getSourceSection().getStartLine());
                    List<Breakpoint> bpHit = event.getBreakpoints();
                    assertEquals(1, bpHit.size());
                    assertSame(breakpoints[ff][ff], bpHit.get(0));
                });
            }
            expectDone();
        }
    }

    @Test
    public void testFunctionSensitiveBreakpointsInternal() throws Exception {
        // Test breakpoints hit on functions in internal source
        Source source = testSource("ROOT(\n" +
                        "  STATEMENT,\n" +
                        "  CALL(foo0),\n" +
                        "  CALL(foo1),\n" +
                        "  CALL(foo2)\n" +
                        ")\n");
        Source internalSource = Source.newBuilder(InstrumentationTestLanguage.ID, "ROOT(\n" +
                        "  DEFINE(foo0,\n" +
                        "    ROOT(\n" +
                        "      STATEMENT,\n" +
                        "      EXPRESSION)\n" +
                        "  ),\n" +
                        "  DEFINE(foo1,\n" +
                        "    ROOT(\n" +
                        "      STATEMENT,\n" +
                        "      EXPRESSION)\n" +
                        "  ),\n" +
                        "  DEFINE(foo2,\n" +
                        "    ROOT(\n" +
                        "      CALL(foo0),\n" +
                        "      CALL(foo1))\n" +
                        "  )\n" +
                        ")\n", "SLInternal.sl").internal(true).buildLiteral();
        boolean internalSession = false;
        do {
            try (DebuggerSession session = startSession()) {
                session.setSteppingFilter(SuspensionFilter.newBuilder().includeInternal(internalSession).build());
                startEval(internalSource);
                expectDone();
                session.suspendNextExecution();
                startEval(source);
                String[] functions = new String[]{"foo0", "foo1"};
                final Breakpoint[] breakpoints = new Breakpoint[2];
                expectSuspended((SuspendedEvent event) -> {
                    checkState(event, 2, true, "STATEMENT");
                    // Retrieve the function instances
                    DebugScope functionScope = session.getTopScope(source.getLanguage());
                    for (int f = 0; f < functions.length; f++) {
                        DebugValue foo = functionScope.getDeclaredValue(functions[f]);
                        assertTrue(foo.getSourceLocation().getSource().isInternal());
                        // Create breakpoint for the function, use the source section, or nothing:
                        Breakpoint.Builder builder;
                        if (f == 0) {
                            builder = Breakpoint.newBuilder(foo.getSourceLocation());
                        } else {
                            builder = Breakpoint.newBuilder((URI) null);
                        }
                        breakpoints[f] = builder.rootInstance(foo).sourceElements(SourceElement.ROOT).build();
                        session.install(breakpoints[f]);
                    }
                });
                if (!internalSession) {
                    // Breakpoints hit in the non-internal source only
                    for (int f = 0; f < functions.length; f++) {
                        int ff = f;
                        int expectedLine = 3 + f;
                        expectSuspended((SuspendedEvent event) -> {
                            assertFalse(event.getSourceSection().getSource().isInternal());
                            assertEquals(expectedLine, event.getSourceSection().getStartLine());
                            List<Breakpoint> bpHit = event.getBreakpoints();
                            assertEquals(1, bpHit.size());
                            assertSame(breakpoints[ff], bpHit.get(0));
                        });
                    }
                } else {
                    // Only in the internal session
                    // breakpoints are hit in the internal source in their bodies
                    for (int f = 0; f < functions.length * 2; f++) {
                        int ff = f % 2;
                        int expectedLine = 2 + 5 * ff;
                        expectSuspended((SuspendedEvent event) -> {
                            assertTrue(event.getSourceSection().getSource().isInternal());
                            assertEquals(expectedLine, event.getSourceSection().getStartLine());
                            List<Breakpoint> bpHit = event.getBreakpoints();
                            assertEquals(1, bpHit.size());
                            assertSame(breakpoints[ff], bpHit.get(0));
                        });
                    }
                }
                expectDone();
            }
        } while ((internalSession = !internalSession) == true);
    }

    @Test
    public void testStepOverBreakpoint() {
        final Source source = testSource("ROOT(\n" +
                        "  STATEMENT,\n" +
                        "  STATEMENT,\n" +
                        "  STATEMENT,\n" +
                        "  STATEMENT\n" +
                        ")\n");
        try (DebuggerSession session = startSession()) {
            session.suspendNextExecution();
            Breakpoint breakpoint3 = Breakpoint.newBuilder(getSourceImpl(source)).lineIs(3).build();
            Breakpoint breakpoint4 = Breakpoint.newBuilder(getSourceImpl(source)).lineIs(4).build();
            Breakpoint breakpoint5 = Breakpoint.newBuilder(getSourceImpl(source)).lineIs(5).build();
            session.install(breakpoint3);
            session.install(breakpoint4);
            session.install(breakpoint5);
            breakpoint4.setEnabled(false);

            startEval(source);
            expectSuspended((SuspendedEvent event) -> {
                // No breakpoints set at line 2
                assertEquals(0, event.getBreakpoints().size());
                checkState(event, 2, true, "STATEMENT").prepareStepOver(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                // Enabled breakpoint at line 3
                assertEquals(1, event.getBreakpoints().size());
                assertSame(breakpoint3, event.getBreakpoints().get(0));
                checkState(event, 3, true, "STATEMENT").prepareStepOver(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                // Disabled breakpoint at line 4
                assertEquals(0, event.getBreakpoints().size());
                checkState(event, 4, true, "STATEMENT").prepareStepOver(1);
                session.setBreakpointsActive(Breakpoint.Kind.SOURCE_LOCATION, false);
            });
            expectSuspended((SuspendedEvent event) -> {
                // Deactivated breakpoints
                assertEquals(0, event.getBreakpoints().size());
                checkState(event, 5, true, "STATEMENT").prepareStepOver(1);
            });
            expectDone();
        }
    }

    @Test
    public void testRelativeSourceBreak() throws Exception {
        String sourceContent = "relative source\nVarA";
        String relativePath = "relative/test.file";
        TestDebugNoContentLanguage language = new TestDebugNoContentLanguage(relativePath, true, true);
        ProxyLanguage.setDelegate(language);
        try (DebuggerSession session = tester.startSession()) {
            Breakpoint breakpoint = Breakpoint.newBuilder(new URI(null, null, relativePath, null)).lineIs(1).build();
            session.install(breakpoint);
            Source source = Source.create(ProxyLanguage.ID, sourceContent);
            tester.startEval(source);
            expectSuspended((SuspendedEvent event) -> {
                assertSame(breakpoint, event.getBreakpoints().get(0));
                SourceSection sourceSection = event.getSourceSection();
                Assert.assertTrue(sourceSection.isAvailable());
                Assert.assertTrue(sourceSection.hasLines());
                Assert.assertTrue(sourceSection.hasColumns());
                Assert.assertFalse(sourceSection.hasCharIndex());
                Assert.assertFalse(sourceSection.getSource().hasCharacters());

                URI uri = sourceSection.getSource().getURI();
                Assert.assertFalse(uri.toString(), uri.isAbsolute());
                Assert.assertEquals(relativePath, uri.getPath());

                event.prepareContinue();
            });
        }
        expectDone();
    }

    private static boolean checkBreakpointsResolved(Breakpoint[] breakpoints, boolean resolved) {
        for (Breakpoint breakpoint : breakpoints) {
            assert breakpoint.isResolved() == resolved : breakpoint.toString();
            return false;
        }
        return true;
    }

    @Test
    public void testLazyParsingBreak() throws Exception {
        ProxyLanguage.setDelegate(new TestLazyParsingLanguage());
        Source source = Source.create(ProxyLanguage.ID, "" +
                        "main\n" +
                        "\n" +
                        "foo\n" +
                        "\n" +
                        "foo2\n" +
                        "\n");
        final int lineCount = source.getLineCount();
        final Breakpoint[] breakpoints = new Breakpoint[lineCount];
        final int[] resolvedLines = new int[lineCount];
        try (DebuggerSession session = tester.startSession()) {
            for (int l = 1; l <= lineCount; l++) {
                final int line = l;
                Breakpoint breakpoint = Breakpoint.newBuilder(getSourceImpl(source)).lineIs(line).resolveListener((Breakpoint b, SourceSection section) -> {
                    resolvedLines[line - 1] = section.getStartLine();
                }).build();
                breakpoints[line - 1] = breakpoint;
                session.install(breakpoint);
            }
            checkBreakpointsResolved(breakpoints, false);
            tester.startEval(source);
            for (int l = 1; l <= lineCount; l += 2) {
                final int line = l;
                expectSuspended((SuspendedEvent event) -> {
                    assertEquals(line, event.getSourceSection().getStartLine());
                    List<Breakpoint> hitBreakpoints = event.getBreakpoints();
                    assertEquals("Hit breakpoints at line " + line, 2, event.getBreakpoints().size());
                    assertTrue("Breakpoint at line " + line, hitBreakpoints.contains(breakpoints[line - 1]));
                    assertTrue("Breakpoint at line " + (line + 1), hitBreakpoints.contains(breakpoints[line]));
                    // This and the next breakpoints are both resolved to this line
                    assertEquals(line, resolvedLines[line - 1]);
                    assertEquals(line, resolvedLines[line]);
                    // Breakpoints on further lines are not resolved yet:
                    for (int l2 = line + 2; l2 < lineCount; l2++) {
                        assertFalse(breakpoints[l2 - 1].isResolved());
                        assertEquals(0, resolvedLines[l2 - 1]);
                    }
                    event.prepareContinue();
                });
            }
            checkBreakpointsResolved(breakpoints, true);
        }
        expectDone();
    }

    @Test
    public void testBreakpointInRunningApp() throws Exception {
        Source testSource = testSource("ROOT(DEFINE(test, ROOT(\n" +
                        "STATEMENT,\n" +
                        "STATEMENT,\n" +
                        "STATEMENT)))");

        final int numChecks = 1000;
        Context.Builder builder = Context.newBuilder();
        try (Context context = (TruffleTestAssumptions.isOptimizingRuntime() ? builder.option("engine.MaximumCompilations", "-1") : builder).build()) {
            Debugger debugger = context.getEngine().getInstruments().get("debugger").lookup(Debugger.class);
            try (DebuggerSession session = debugger.startSession(event -> {
            })) {
                context.eval(testSource);
                com.oracle.truffle.api.source.Source breakpointSource = getSourceImpl(testSource);
                ExecutorService instrumentationExecutor = Executors.newSingleThreadExecutor();
                for (int i = 0; i < numChecks; i++) {
                    checkParallelBreakpoint(context, session, instrumentationExecutor, breakpointSource);
                }
            }
        }
    }

    private static void checkParallelBreakpoint(Context context, DebuggerSession session, ExecutorService instrumentationExecutor, com.oracle.truffle.api.source.Source breakpointSource)
                    throws Exception {
        AtomicBoolean resolved = new AtomicBoolean(false);
        Breakpoint breakpoint = Breakpoint.newBuilder(breakpointSource).lineIs(2).resolveListener((b, section) -> {
            resolved.set(true);
        }).build();
        Future<?> instrumentFuture = instrumentationExecutor.submit(() -> {
            session.install(breakpoint);
            assertTrue(breakpoint.isResolved());
        });
        Value test = context.getBindings(InstrumentationTestLanguage.ID).getMember("test");
        test.execute();
        instrumentFuture.get();
        instrumentationExecutor.submit(() -> {
            breakpoint.dispose();
        }).get();
        assertTrue(resolved.get());
    }
}
