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
package com.oracle.truffle.api.debug.test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.oracle.truffle.api.instrumentation.test.InstrumentationTestLanguage;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;
import org.junit.Test;

import com.oracle.truffle.api.debug.Breakpoint;
import com.oracle.truffle.api.debug.DebugException;
import com.oracle.truffle.api.debug.DebugException.CatchLocation;
import com.oracle.truffle.api.debug.DebugStackFrame;
import com.oracle.truffle.api.debug.DebugStackTraceElement;
import com.oracle.truffle.api.debug.DebuggerSession;
import com.oracle.truffle.api.debug.SuspendAnchor;
import com.oracle.truffle.api.debug.SuspendedEvent;
import com.oracle.truffle.api.source.SourceSection;
import java.util.Iterator;

import org.graalvm.polyglot.Source;

public class DebugExceptionTest extends AbstractDebugTest {

    @Test
    public void testExceptionBuilder() {
        try {
            Breakpoint.newExceptionBuilder(false, false).build();
            fail();
        } catch (IllegalArgumentException ex) {
            // O.K.
        }
        Breakpoint bp = Breakpoint.newExceptionBuilder(false, true).build();
        assertTrue(bp.isEnabled());
        assertFalse(bp.isOneShot());
    }

    @Test
    public void testUncaughtException1() {
        Source testSource = testSource("STATEMENT(THROW(a, b))");
        Breakpoint uncaughtBreakpoint = Breakpoint.newExceptionBuilder(false, true).build();
        try (DebuggerSession session = startSession()) {
            session.install(uncaughtBreakpoint);
            assertTrue(uncaughtBreakpoint.isResolved()); // Exception breakpoints are resolved right
                                                         // away

            startEval(testSource);
            expectSuspended((SuspendedEvent event) -> {
                assertSame(uncaughtBreakpoint, event.getBreakpoints().iterator().next());
                assertSame(SuspendAnchor.AFTER, event.getSuspendAnchor());
                DebugException exception = event.getException();
                assertNull(exception.getCatchLocation());
                assertEquals("b", exception.getMessage());
                SourceSection throwLocation = exception.getThrowLocation();
                assertEquals(10, throwLocation.getCharIndex());
                assertEquals(21, throwLocation.getCharEndIndex());
                assertEquals("a: b", exception.getExceptionObject().toDisplayString());
                assertDebugStackTrace(exception.getDebugStackTrace(), " <1:11, 1:21>");
                assertStack(exception.getStackTrace(), "<instrumentation-test-language>.(Unnamed:1)");
            });
            Throwable t = expectThrowable();
            assertEquals("b", t.getMessage());
        }
    }

    @Test
    public void testUncaughtException2() {
        Source testSource = testSource("ROOT(\n" +
                        "DEFINE(UncaughtThrow,\n" +
                        "  TRY(STATEMENT, STATEMENT(THROW(IllegalState, TestExceptionMessage)),\n" +
                        "      CATCH(a, STATEMENT))\n" +
                        "),\n" +
                        "CALL(UncaughtThrow))");
        Breakpoint uncaughtBreakpoint = Breakpoint.newExceptionBuilder(false, true).build();
        try (DebuggerSession session = startSession()) {
            session.install(uncaughtBreakpoint);
            assertTrue(uncaughtBreakpoint.isResolved());

            startEval(testSource);
            expectSuspended((SuspendedEvent event) -> {
                assertSame(uncaughtBreakpoint, event.getBreakpoints().iterator().next());
                assertSame(SuspendAnchor.AFTER, event.getSuspendAnchor());
                DebugException exception = event.getException();
                assertNull(exception.getCatchLocation());
                assertEquals("TestExceptionMessage", exception.getMessage());
                SourceSection throwLocation = exception.getThrowLocation();
                assertEquals("THROW(IllegalState, TestExceptionMessage)", throwLocation.getCharacters());
                assertEquals("IllegalState: TestExceptionMessage", exception.getExceptionObject().toDisplayString());
                assertDebugStackTrace(exception.getDebugStackTrace(), "UncaughtThrow <3:28, 3:68>", " <6:1, 6:19>");
                assertStack(exception.getStackTrace(), "<instrumentation-test-language>.UncaughtThrow(Unnamed:3)", "<instrumentation-test-language>.(Unnamed:6)");
            });
            Throwable t = expectThrowable();
            assertEquals("TestExceptionMessage", t.getMessage());
        }
    }

    @Test
    public void testCaughtException1() {
        Source testSource = testSource("TRY(STATEMENT(THROW(NPE, TestExceptionMessage)), CATCH(NPE, STATEMENT))\n");
        Breakpoint caughtBreakpoint = Breakpoint.newExceptionBuilder(true, true).build();
        try (DebuggerSession session = startSession()) {
            session.install(caughtBreakpoint);
            assertTrue(caughtBreakpoint.isResolved());

            startEval(testSource);
            expectSuspended((SuspendedEvent event) -> {
                assertSame(caughtBreakpoint, event.getBreakpoints().iterator().next());
                assertSame(SuspendAnchor.AFTER, event.getSuspendAnchor());
                DebugException exception = event.getException();
                assertEquals("TestExceptionMessage", exception.getMessage());
                CatchLocation catchLocation = exception.getCatchLocation();
                assertEquals("TRY(STATEMENT(THROW(NPE, TestExceptionMessage))", catchLocation.getSourceSection().getCharacters());
                assertEquals(event.getTopStackFrame(), catchLocation.getFrame());
                SourceSection throwLocation = exception.getThrowLocation();
                assertEquals("THROW(NPE, TestExceptionMessage)", throwLocation.getCharacters());
                assertEquals("NPE: TestExceptionMessage", exception.getExceptionObject().toDisplayString());
                assertDebugStackTrace(exception.getDebugStackTrace(), " <1:15, 1:46>");
                assertStack(exception.getStackTrace(), "<instrumentation-test-language>.(Unnamed:1)");
            });
            expectDone();
        }
    }

    @Test
    public void testCaughtException2() {
        Source testSource = testSource("ROOT(\n" +
                        "DEFINE(CaughtThrow,\n" +
                        "  TRY(STATEMENT(CALL(ThrownNPE)),\n" +
                        "      CATCH(NPE, STATEMENT))\n" +
                        "),\n" +
                        "DEFINE(ThrownNPE,\n" +
                        "  STATEMENT(THROW(NPE, TestExceptionMessage))\n" +
                        "),\n" +
                        "CALL(CaughtThrow))");
        Breakpoint caughtBreakpoint = Breakpoint.newExceptionBuilder(true, false).build();
        try (DebuggerSession session = startSession()) {
            session.install(caughtBreakpoint);
            assertTrue(caughtBreakpoint.isResolved());

            startEval(testSource);
            expectSuspended((SuspendedEvent event) -> {
                assertSame(caughtBreakpoint, event.getBreakpoints().iterator().next());
                assertSame(SuspendAnchor.AFTER, event.getSuspendAnchor());
                DebugException exception = event.getException();
                assertEquals("TestExceptionMessage", exception.getMessage());
                CatchLocation catchLocation = exception.getCatchLocation();
                assertEquals("TRY(STATEMENT(CALL(ThrownNPE))", catchLocation.getSourceSection().getCharacters());
                Iterator<DebugStackFrame> stackFrames = event.getStackFrames().iterator();
                stackFrames.next();
                DebugStackFrame nextFrame = stackFrames.next();
                assertEquals(nextFrame, catchLocation.getFrame());
                SourceSection throwLocation = exception.getThrowLocation();
                assertEquals("THROW(NPE, TestExceptionMessage)", throwLocation.getCharacters());
                assertEquals("NPE: TestExceptionMessage", exception.getExceptionObject().toDisplayString());
                assertDebugStackTrace(exception.getDebugStackTrace(), "ThrownNPE <7:13, 7:44>", "CaughtThrow <3:17, 3:31>", " <9:1, 9:17>");
                assertStack(exception.getStackTrace(), "<instrumentation-test-language>.ThrownNPE(Unnamed:7)", "<instrumentation-test-language>.CaughtThrow(Unnamed:3)",
                                "<instrumentation-test-language>.(Unnamed:9)");
            });
            expectDone();
        }
    }

    @Test
    public void testGetRawUncaughtException() {
        Source testSource = testSource("STATEMENT(THROW(a, b))");
        Breakpoint uncaughtBreakpoint = Breakpoint.newExceptionBuilder(false, true).build();
        try (DebuggerSession session = startSession()) {
            session.install(uncaughtBreakpoint);
            assertTrue(uncaughtBreakpoint.isResolved()); // Exception breakpoints are resolved right
            // away

            startEval(testSource);
            expectSuspended((SuspendedEvent event) -> {
                assertSame(uncaughtBreakpoint, event.getBreakpoints().iterator().next());
                assertSame(SuspendAnchor.AFTER, event.getSuspendAnchor());
                DebugException exception = event.getException();
                assertEquals(InstrumentationTestLanguage.ThrowNode.TestLanguageException.class, exception.getRawException(InstrumentationTestLanguage.class).getClass());
            });
            Throwable t = expectThrowable();
            assertEquals("b", t.getMessage());
        }
    }

    @Test
    public void testGetRawUncaughtExceptionRestricted() {
        Source testSource = testSource("STATEMENT(THROW(a, b))");
        Breakpoint uncaughtBreakpoint = Breakpoint.newExceptionBuilder(false, true).build();
        try (DebuggerSession session = startSession()) {
            session.install(uncaughtBreakpoint);
            assertTrue(uncaughtBreakpoint.isResolved()); // Exception breakpoints are resolved right
            // away

            startEval(testSource);
            expectSuspended((SuspendedEvent event) -> {
                assertSame(uncaughtBreakpoint, event.getBreakpoints().iterator().next());
                assertSame(SuspendAnchor.AFTER, event.getSuspendAnchor());
                DebugException exception = event.getException();
                // no access from other languages
                assertEquals(null, exception.getRawException(ProxyLanguage.class));
            });
            Throwable t = expectThrowable();
            assertEquals("b", t.getMessage());
        }
    }

    @Test
    public void testLocationBreakpointOnException() {
        final Source source = testSource("ROOT(\n" +
                        "  TRY(STATEMENT(THROW(a, b)), CATCH(a, EXPRESSION)),\n" +
                        "  STATEMENT\n" +
                        ")\n");
        Breakpoint breakpoint = Breakpoint.newBuilder(getSourceImpl(source)).lineIs(2).suspendAnchor(SuspendAnchor.AFTER).build();
        try (DebuggerSession session = startSession()) {
            session.install(breakpoint);
            startEval(source);
            expectSuspended((SuspendedEvent event) -> {
                assertSame(breakpoint, event.getBreakpoints().iterator().next());
                assertSame(SuspendAnchor.AFTER, event.getSuspendAnchor());
                DebugException exception = event.getException();
                assertEquals("b", exception.getMessage());
                CatchLocation catchLocation = exception.getCatchLocation();
                assertEquals("TRY(STATEMENT(THROW(a, b))", catchLocation.getSourceSection().getCharacters());
                assertEquals(event.getTopStackFrame(), catchLocation.getFrame());
                SourceSection throwLocation = exception.getThrowLocation();
                assertEquals("THROW(a, b)", throwLocation.getCharacters());
                assertEquals("a: b", exception.getExceptionObject().toDisplayString());
            });
            expectDone();

            // Add exception breakpoints to test which breakpoints were hit
            Breakpoint uncaughtBreakpoint = Breakpoint.newExceptionBuilder(false, true).build();
            Breakpoint caughtBreakpoint = Breakpoint.newExceptionBuilder(true, false).build();
            session.install(uncaughtBreakpoint);
            session.install(caughtBreakpoint);
            startEval(source);
            expectSuspended((SuspendedEvent event) -> {
                assertEquals(2, event.getBreakpoints().size());
                assertSame(caughtBreakpoint, event.getBreakpoints().get(0));
                assertSame(breakpoint, event.getBreakpoints().get(1));
                DebugException exception = event.getException();
                assertEquals("b", exception.getMessage());
            });
            expectDone();
        }
    }

    @Test
    public void testInactive() throws Throwable {
        final Source source = testSource("ROOT(\n" +
                        "  TRY(STATEMENT(THROW(a, b)), CATCH(a, EXPRESSION)),\n" +
                        "  STATEMENT(THROW(c, d))\n" +
                        ")\n");

        try (DebuggerSession session = startSession()) {
            assertTrue(session.isBreakpointsActive(Breakpoint.Kind.EXCEPTION));
            Breakpoint uncaughtBreakpoint = Breakpoint.newExceptionBuilder(false, true).build();
            Breakpoint caughtBreakpoint = Breakpoint.newExceptionBuilder(true, false).build();
            session.install(uncaughtBreakpoint);
            session.install(caughtBreakpoint);

            startEval(source);
            expectSuspended((SuspendedEvent event) -> {
                assertEquals(1, event.getBreakpoints().size());
                assertSame(caughtBreakpoint, event.getBreakpoints().get(0));
                assertNotNull(event.getException());
            });
            expectSuspended((SuspendedEvent event) -> {
                assertEquals(1, event.getBreakpoints().size());
                assertSame(uncaughtBreakpoint, event.getBreakpoints().get(0));
                assertNotNull(event.getException());
            });
            expectThrowable();

            // Deactivate exception breakpoints
            session.setBreakpointsActive(Breakpoint.Kind.EXCEPTION, false);
            startEval(source);
            expectThrowable();

            // Activate exception breakpoints and disable caught
            session.setBreakpointsActive(Breakpoint.Kind.EXCEPTION, true);
            caughtBreakpoint.setEnabled(false);
            startEval(source);
            expectSuspended((SuspendedEvent event) -> {
                assertEquals(1, event.getBreakpoints().size());
                assertSame(uncaughtBreakpoint, event.getBreakpoints().get(0));
                assertNotNull(event.getException());
            });
            expectThrowable();

            // Disable uncaught
            caughtBreakpoint.setEnabled(true);
            uncaughtBreakpoint.setEnabled(false);
            startEval(source);
            expectSuspended((SuspendedEvent event) -> {
                assertEquals(1, event.getBreakpoints().size());
                assertSame(caughtBreakpoint, event.getBreakpoints().get(0));
                assertNotNull(event.getException());
            });
            expectThrowable();
        }
    }

    @Test
    public void testEvalExceptional() {
        Source testSource = testSource("ROOT(\n" +
                        "  DEFINE(foo,\n" +
                        "    STATEMENT\n" +
                        "  ),\n" +
                        "  CALL(foo)\n" +
                        ")\n");
        try (DebuggerSession session = startSession()) {
            session.suspendNextExecution();
            startEval(testSource);
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 3, true, "STATEMENT");
                try {
                    event.getTopStackFrame().eval("STATEMENT(THROW(NPE, TestExceptionMessage))");
                    fail();
                } catch (DebugException dex) {
                    SourceSection throwLocation = dex.getThrowLocation();
                    assertEquals("THROW(NPE, TestExceptionMessage)", throwLocation.getCharacters());
                    assertNull(dex.getCatchLocation());
                    assertEquals("NPE: TestExceptionMessage", dex.getExceptionObject().toDisplayString());
                    List<DebugStackTraceElement> debugStackTrace = dex.getDebugStackTrace();
                    assertEquals(2, debugStackTrace.size());
                    assertEquals("THROW(NPE, TestExceptionMessage)", debugStackTrace.get(0).getSourceSection().getCharacters());
                    assertEquals("CALL(foo)", debugStackTrace.get(1).getSourceSection().getCharacters());
                }
            });
        }
    }

    private static void assertDebugStackTrace(List<DebugStackTraceElement> debugStackTrace, String... elements) {
        assertEquals(elements.length, debugStackTrace.size());
        int n = debugStackTrace.size();
        for (int i = 0; i < n; i++) {
            DebugStackTraceElement frame = debugStackTrace.get(i);
            SourceSection ss = frame.getSourceSection();
            assertEquals(elements[i], frame.getName() + " <" + ss.getStartLine() + ":" + ss.getStartColumn() + ", " + ss.getEndLine() + ":" + ss.getEndColumn() + ">");
        }
    }

    private static void assertStack(StackTraceElement[] stackTrace, String... elements) {
        assertEquals(elements.length, stackTrace.length);
        for (int i = 0; i < elements.length; i++) {
            assertEquals(elements[i], stackTrace[i].toString());
        }
    }
}
