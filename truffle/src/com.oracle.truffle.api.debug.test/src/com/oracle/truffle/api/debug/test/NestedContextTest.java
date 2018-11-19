/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import com.oracle.truffle.api.debug.Breakpoint;
import com.oracle.truffle.api.debug.Debugger;
import com.oracle.truffle.api.debug.DebuggerSession;
import com.oracle.truffle.api.debug.SuspendedCallback;
import com.oracle.truffle.api.debug.SuspendedEvent;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;

public class NestedContextTest extends AbstractDebugTest {

    @Test
    public void testNestedRun() throws Throwable {
        testNestedStepping(2);
        testNestedStepping(5);
    }

    private void testNestedStepping(int depth) {
        if (depth == 0) {
            return;
        }
        Source testSource = testSource("ROOT(\n" +
                        "  STATEMENT,\n" +
                        "  STATEMENT\n" +
                        ")\n");
        pushContext();
        try (DebuggerSession session = startSession()) {
            Breakpoint breakpoint3 = session.install(Breakpoint.newBuilder(getSourceImpl(testSource)).lineIs(3).build());

            session.suspendNextExecution();
            startEval(testSource);

            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 2, true, "STATEMENT");
                assertEquals(0, event.getBreakpoints().size());
                testNestedStepping(depth - 1);
                event.prepareStepInto(1);
            });

            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 3, true, "STATEMENT");
                assertEquals(1, event.getBreakpoints().size());
                assertSame(breakpoint3, event.getBreakpoints().iterator().next());
                testNestedStepping(depth - 1);
                event.prepareStepInto(1);
            });

            expectDone();
        }
        popContext();
    }

    @Test
    public void testRecursiveEval() throws Exception {
        final Source testSource = testSource("ROOT(\n" +
                        "  STATEMENT,\n" +
                        "  STATEMENT\n" +
                        ")\n");

        final Context context = Context.create();

        final AtomicInteger suspensionCount = new AtomicInteger(0);
        Debugger debugger = context.getEngine().getInstruments().get("debugger").lookup(Debugger.class);
        try (DebuggerSession session = debugger.startSession(new SuspendedCallback() {
            public void onSuspend(SuspendedEvent event) {
                checkState(event, 3, true, "STATEMENT");
                // recursive evaluation should not trigger a suspended event
                context.eval(testSource);
                suspensionCount.incrementAndGet();
            }
        })) {
            session.install(Breakpoint.newBuilder(getSourceImpl(testSource)).lineIs(3).build());
            context.eval(testSource);
        }

    }

}
