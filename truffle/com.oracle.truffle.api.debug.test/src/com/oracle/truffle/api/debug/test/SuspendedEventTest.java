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

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.debug.Breakpoint;
import com.oracle.truffle.api.debug.DebugStackFrame;
import com.oracle.truffle.api.debug.DebuggerSession;
import com.oracle.truffle.api.debug.SuspendedEvent;
import com.oracle.truffle.api.instrumentation.InstrumentationTestLanguage;
import com.oracle.truffle.api.source.Source;

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
                checkState(event, 6, true, "STATEMENT(CALL(foo))\n", "root0", "43").prepareStepInto(1);
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
                checkState(event, 4, true, "STATEMENT(CALL(foo))\n").prepareStepInto(1);
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
        final Source source = Source.newBuilder("ROOT(\n" +
                        "  DEFINE(bar, ROOT(STATEMENT)),\n" +
                        "  DEFINE(foo, STATEMENT, \n" +
                        "              STATEMENT(CALL(bar))),\n" +
                        "  STATEMENT(CALL(foo))\n" +
                        ")\n").mimeType(InstrumentationTestLanguage.MIME_TYPE).internal().name("internal test code").build();

        try (DebuggerSession session = startSession()) {
            session.install(Breakpoint.newBuilder(source).lineIs(2).build());
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

}
