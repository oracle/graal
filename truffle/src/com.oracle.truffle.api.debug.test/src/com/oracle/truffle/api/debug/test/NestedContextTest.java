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

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import com.oracle.truffle.api.debug.Breakpoint;
import com.oracle.truffle.api.debug.Debugger;
import com.oracle.truffle.api.debug.DebuggerSession;
import com.oracle.truffle.api.debug.SuspendedCallback;
import com.oracle.truffle.api.debug.SuspendedEvent;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.vm.PolyglotEngine;

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
            Breakpoint breakpoint3 = session.install(Breakpoint.newBuilder(testSource).lineIs(3).build());

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

        final PolyglotEngine engine = PolyglotEngine.newBuilder().build();

        final AtomicInteger suspensionCount = new AtomicInteger(0);
        try (DebuggerSession session = Debugger.find(engine).startSession(new SuspendedCallback() {
            public void onSuspend(SuspendedEvent event) {
                checkState(event, 3, true, "STATEMENT");
                // recursive evaluation should not trigger a suspended event
                engine.eval(testSource);
                suspensionCount.incrementAndGet();
            }
        })) {
            session.install(Breakpoint.newBuilder(testSource).lineIs(3).build());
            engine.eval(testSource);
        }

    }

}
