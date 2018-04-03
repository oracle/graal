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
import com.oracle.truffle.api.debug.SuspendedEvent;
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
                assertEquals("42", event.getReturnValue().as(String.class));
            });

            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 4, false, "CALL(foo)").prepareContinue();
                assertEquals("42", event.getReturnValue().as(String.class));
            });

            expectDone();
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
                        runExpectIllegalState(() -> value.as(String.class));
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
