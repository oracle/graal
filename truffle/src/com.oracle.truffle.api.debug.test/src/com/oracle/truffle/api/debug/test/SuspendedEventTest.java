/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates. All rights reserved.
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
import static org.junit.Assert.assertTrue;

import java.util.Iterator;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.debug.Breakpoint;
import com.oracle.truffle.api.debug.DebugStackFrame;
import com.oracle.truffle.api.debug.DebugValue;
import com.oracle.truffle.api.debug.DebuggerSession;
import com.oracle.truffle.api.debug.SuspendAnchor;
import com.oracle.truffle.api.debug.SuspendedEvent;
import com.oracle.truffle.api.debug.SuspensionFilter;
import com.oracle.truffle.api.instrumentation.test.InstrumentationTestLanguage;
import org.graalvm.polyglot.Source;

public class SuspendedEventTest extends AbstractDebugTest {

    @Test
    public void testStackFrames() throws Throwable {
        final Source source = testSource("ROOT(\n" +
                        "  DEFINE(bar, VARIABLE(bar0, 41), VARIABLE(bar1, 40), STATEMENT),\n" +
                        "  DEFINE(foo, ROOT(VARIABLE(foo0, 42), \n" +
                        "                   STATEMENT(CALL(bar)))),\n" +
                        "  STATEMENT(VARIABLE(root0, 43)),\n" +
                        "  STATEMENT(CALL(foo))\n" +
                        ")\n");
        try (DebuggerSession session = startSession()) {
            session.suspendNextExecution();
            startEval(source);

            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 5, true, "STATEMENT(VARIABLE(root0, 43))").prepareStepOver(1);
                Iterator<DebugStackFrame> frameIterator = event.getStackFrames().iterator();
                checkStack(frameIterator.next());
                Assert.assertFalse(frameIterator.hasNext());
            });

            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 6, true, "STATEMENT(CALL(foo))", "root0", "43").prepareStepInto(1);
                Iterator<DebugStackFrame> frameIterator = event.getStackFrames().iterator();
                checkStack(frameIterator.next(), "root0", "43");
                Assert.assertFalse(frameIterator.hasNext());
            });

            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 4, true, "STATEMENT(CALL(bar))", "foo0", "42").prepareStepInto(1);
                Iterator<DebugStackFrame> frameIterator = event.getStackFrames().iterator();
                checkStack(frameIterator.next(), "foo0", "42");
                checkStack(frameIterator.next(), "root0", "43");
                Assert.assertFalse(frameIterator.hasNext());
            });

            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 2, true, "STATEMENT", "bar0", "41", "bar1", "40").prepareContinue();
                Iterator<DebugStackFrame> frameIterator = event.getStackFrames().iterator();
                checkStack(frameIterator.next(), "bar0", "41", "bar1", "40");
                checkStack(frameIterator.next(), "foo0", "42");
                checkStack(frameIterator.next(), "root0", "43");
                Assert.assertFalse(frameIterator.hasNext());
            });

            expectDone();
        }
    }

    @Test
    public void testReturnValue() throws Throwable {
        final Source source = testSource("ROOT(\n" +
                        "  DEFINE(bar, STATEMENT(CONSTANT(42))), \n" +
                        "  DEFINE(foo, CALL(bar)), \n" +
                        "  STATEMENT(CALL(foo))\n" +
                        ")\n");

        try (DebuggerSession session = startSession()) {
            session.suspendNextExecution();
            startEval(source);

            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 4, true, "STATEMENT(CALL(foo))").prepareStepInto(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 2, true, "STATEMENT(CONSTANT(42))").prepareStepInto(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 3, false, "CALL(bar)").prepareStepInto(1);
                assertEquals("42", event.getReturnValue().toDisplayString());
            });

            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 4, false, "CALL(foo)").prepareContinue();
                assertEquals("42", event.getReturnValue().toDisplayString());
            });

            assertEquals("42", expectDone());
        }
    }

    @Test
    public void testReturnValueChanged() throws Throwable {
        final Source source = testSource("ROOT(\n" +
                        "  DEFINE(bar, VARIABLE(a, 41), STATEMENT(CONSTANT(42))), \n" +
                        "  DEFINE(foo, VARIABLE(b, 40), CALL(bar)), \n" +
                        "  VARIABLE(c, 24), STATEMENT(CALL(foo))\n" +
                        ")\n");

        try (DebuggerSession session = startSession()) {
            Breakpoint breakpoint = Breakpoint.newBuilder(getSourceImpl(source)).lineIs(2).suspendAnchor(SuspendAnchor.AFTER).build();
            session.install(breakpoint);
            startEval(source);

            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 2, false, "STATEMENT(CONSTANT(42))", "a", "41").prepareStepInto(1);
                assertEquals("42", event.getReturnValue().toDisplayString());
                DebugValue a = event.getTopStackFrame().getScope().getDeclaredValues().iterator().next();
                assertEquals("a", a.getName());
                event.setReturnValue(a);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 3, false, "CALL(bar)", "b", "40").prepareStepInto(1);
                assertEquals("41", event.getReturnValue().toDisplayString());
                DebugValue b = event.getTopStackFrame().getScope().getDeclaredValues().iterator().next();
                assertEquals("b", b.getName());
                event.setReturnValue(b);
            });

            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 4, false, "CALL(foo)", "c", "24").prepareContinue();
                assertEquals("40", event.getReturnValue().toDisplayString());
                DebugValue c = event.getTopStackFrame().getScope().getDeclaredValues().iterator().next();
                assertEquals("c", c.getName());
                event.setReturnValue(c);
            });

            assertEquals("24", expectDone());
        }
    }

    @Test
    public void testIsInternal() throws Throwable {
        final Source source = Source.newBuilder(InstrumentationTestLanguage.ID,
                        "ROOT(\n" +
                                        "  DEFINE(bar, ROOT(STATEMENT)),\n" +
                                        "  DEFINE(foo, STATEMENT, \n" +
                                        "              STATEMENT(CALL(bar))),\n" +
                                        "  STATEMENT(CALL(foo))\n" +
                                        ")\n",
                        "internal test code").internal(true).build();

        try (DebuggerSession session = startSession()) {
            session.setSteppingFilter(SuspensionFilter.newBuilder().includeInternal(true).build());
            session.install(Breakpoint.newBuilder(getSourceImpl(source)).lineIs(2).build());
            startEval(source);

            expectSuspended((SuspendedEvent event) -> {
                Iterator<DebugStackFrame> frameIterator = event.getStackFrames().iterator();
                DebugStackFrame frame = frameIterator.next();
                assertTrue(frame.isInternal());

                frame = frameIterator.next();
                assertTrue(frame.isInternal());

                frame = frameIterator.next();
                assertTrue(frame.isInternal());
            });

            expectDone();
        }
    }

    @Test
    public void testOtherThreadAccess() throws Throwable {
        final Source source = testSource("ROOT(\n" +
                        "  DEFINE(bar, VARIABLE(bar0, 41), VARIABLE(bar1, 40), STATEMENT),\n" +
                        "  DEFINE(foo, ROOT(VARIABLE(foo0, 42), \n" +
                        "                   STATEMENT(CALL(bar)))),\n" +
                        "  STATEMENT(VARIABLE(root0, 43)),\n" +
                        "  STATEMENT(CALL(foo))\n" +
                        ")\n");

        try (DebuggerSession session = startSession()) {
            final Breakpoint breakpoint = session.install(Breakpoint.newBuilder(getSourceImpl(source)).lineIs(4).build());
            startEval(source);

            expectSuspended((SuspendedEvent event) -> {

                run(() -> event.getBreakpointConditionException(breakpoint));
                run(() -> event.getSession());
                run(() -> event.getSourceSection());
                run(() -> event.getBreakpoints());
                run(() -> event.getSuspendAnchor());
                run(() -> event.toString());

                run(() -> {
                    event.prepareStepInto(1);
                    return null;
                });
                run(() -> {
                    event.prepareStepOut(1);
                    return null;
                });
                run(() -> {
                    event.prepareStepOver(1);
                    return null;
                });
                run(() -> {
                    event.prepareContinue();
                    return null;
                });
                run(() -> {
                    event.prepareKill();
                    return null;
                });

                runExpectIllegalState(() -> event.getStackFrames());
                runExpectIllegalState(() -> event.getTopStackFrame());
                runExpectIllegalState(() -> event.getReturnValue());

                for (DebugStackFrame frame : event.getStackFrames()) {

                    for (DebugValue value : frame.getScope().getDeclaredValues()) {
                        runExpectIllegalState(() -> value.toDisplayString());
                        runExpectIllegalState(() -> {
                            value.set(null);
                            return null;
                        });
                        value.getName(); // Name is known
                        runExpectIllegalState(() -> value.isReadable());
                        runExpectIllegalState(() -> value.isWritable());
                    }

                    run(() -> frame.getName());
                    run(() -> frame.getSourceSection());
                    run(() -> frame.isInternal());
                    run(() -> frame.toString());

                    runExpectIllegalState(() -> frame.getScope().getDeclaredValue(""));
                    runExpectIllegalState(() -> frame.getScope().getDeclaredValues().iterator());
                    runExpectIllegalState(() -> frame.eval(""));
                }

            });

            expectDone();
        }
    }

    private static <T> T run(Callable<T> callable) {
        Future<T> future = Executors.newSingleThreadExecutor().submit(callable);
        try {
            return future.get();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static <T> void runExpectIllegalState(Callable<T> callable) {
        Future<T> future = Executors.newSingleThreadExecutor().submit(callable);
        try {
            future.get();
            Assert.fail();
        } catch (ExecutionException e) {
            if (!(e.getCause() instanceof IllegalStateException)) {
                throw new AssertionError(e);
            }
        } catch (InterruptedException e) {
            throw new AssertionError(e);
        }
    }

}
