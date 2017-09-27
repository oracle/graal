/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Collections;
import java.util.HashMap;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.debug.Breakpoint;
import com.oracle.truffle.api.debug.DebuggerSession;
import com.oracle.truffle.api.debug.SuspendedEvent;
import com.oracle.truffle.api.debug.SuspensionFilter;
import com.oracle.truffle.api.instrumentation.test.InstrumentationTestLanguage;

import org.graalvm.polyglot.Source;

public class SuspensionFilterTest extends AbstractDebugTest {

    @After
    public void tearDown() {
        InstrumentationTestLanguage.envConfig = null;
    }

    @Test
    public void testSuspendInInitialization() {
        Source initSource = Source.newBuilder(InstrumentationTestLanguage.ID, "STATEMENT(EXPRESSION)", "<init>").buildLiteral();
        InstrumentationTestLanguage.envConfig = Collections.singletonMap("initSource", initSource);
        final Source source = testSource("ROOT(\n" +
                        "  DEFINE(foo, \n" +
                        "    STATEMENT(CONSTANT(42))\n" +
                        "  ), \n" +
                        "  STATEMENT(CALL(foo))\n" +
                        ")\n");

        SuspensionFilter suspensionFilter = SuspensionFilter.newBuilder().build();
        // Empty filter does not filter anything
        try (DebuggerSession session = startSession()) {
            session.setSteppingFilter(suspensionFilter);
            session.suspendNextExecution();
            startEval(source);

            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 1, true, "STATEMENT(EXPRESSION)").prepareContinue();
                Assert.assertFalse(event.isLanguageContextInitialized());
            });
            expectDone();
        }
    }

    @Test
    public void testSuspendAfterInitialization() {
        Source initSource = Source.newBuilder(InstrumentationTestLanguage.ID, "STATEMENT(EXPRESSION)", "<init>").buildLiteral();
        InstrumentationTestLanguage.envConfig = Collections.singletonMap("initSource", initSource);
        final Source source = testSource("ROOT(\n" +
                        "  STATEMENT(CONSTANT(42))\n" +
                        ")\n");

        SuspensionFilter suspensionFilter = SuspensionFilter.newBuilder().ignoreLanguageContextInitialization(true).build();
        try (DebuggerSession session = startSession()) {
            session.setSteppingFilter(suspensionFilter);
            session.suspendNextExecution();
            startEval(source);

            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 2, true, "STATEMENT(CONSTANT(42))").prepareContinue();
                Assert.assertTrue(event.isLanguageContextInitialized());
            });
            expectDone();
        }
    }

    @Test
    public void testSuspendAfterInitialization2() {
        // Suspend after initialization code finishes,
        // but can step into the same code that was executed during initialization, later on.
        Source initSource = Source.newBuilder(InstrumentationTestLanguage.ID, "STATEMENT(EXPRESSION)", "<init>").buildLiteral();
        InstrumentationTestLanguage.envConfig = new HashMap<>();
        InstrumentationTestLanguage.envConfig.put("initSource", initSource);
        InstrumentationTestLanguage.envConfig.put("runInitAfterExec", true);
        final Source source = testSource("ROOT(\n" +
                        "  STATEMENT(CONSTANT(42))\n" +
                        ")\n");

        SuspensionFilter suspensionFilter = SuspensionFilter.newBuilder().ignoreLanguageContextInitialization(true).build();
        try (DebuggerSession session = startSession()) {
            session.setSteppingFilter(suspensionFilter);
            session.suspendNextExecution();
            startEval(source);

            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 2, true, "STATEMENT(CONSTANT(42))").prepareStepOver(1);
                Assert.assertTrue(event.isLanguageContextInitialized());
                session.suspendNextExecution();
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 1, true, "STATEMENT(EXPRESSION)").prepareContinue();
                Assert.assertTrue(event.isLanguageContextInitialized());
            });
            expectDone();
        }
    }

    @Test
    public void testInitializationFilterChange() {
        // Set to skip the initialization, but put two breakpoints there.
        // Verify that step just skips the code to the next breakpoint.
        // After second breakpoint is hit, change the filter to allow stepping
        // in the initialization code.
        String initCode = "ROOT(\n" +
                        "  DEFINE(initFoo, \n" +
                        "    STATEMENT(EXPRESSION),\n" +    // Skipped by suspensionFilter
                        "    STATEMENT(EXPRESSION),\n" +    // l. 4 Breakpoint
                        "    STATEMENT(CONSTANT(2)),\n" +   // Skipped by suspensionFilter
                        "    STATEMENT(EXPRESSION),\n" +    // l. 6 Breakpoint, filter changed
                        "    LOOP(2,\n" +
                        "      STATEMENT(CONSTANT(1)))\n" + // l. 8 Step stops here
                        "  ), \n" +
                        "  STATEMENT(CALL(initFoo))\n" +
                        ")\n";
        Source initSource = Source.newBuilder(InstrumentationTestLanguage.ID, initCode, "<init>").buildLiteral();
        InstrumentationTestLanguage.envConfig = Collections.singletonMap("initSource", initSource);
        final Source source = testSource("ROOT(\n" +
                        "  STATEMENT(CONSTANT(42))\n" +
                        ")\n");

        SuspensionFilter.Builder filterBuilder = SuspensionFilter.newBuilder().ignoreLanguageContextInitialization(true);
        SuspensionFilter suspensionFilter = filterBuilder.build();
        try (DebuggerSession session = startSession()) {
            session.setSteppingFilter(suspensionFilter);
            session.suspendNextExecution();
            Breakpoint bp4 = Breakpoint.newBuilder(getSourceImpl(initSource)).lineIs(4).build();
            Breakpoint bp6 = Breakpoint.newBuilder(getSourceImpl(initSource)).lineIs(6).build();
            session.install(bp4);
            session.install(bp6);
            startEval(source);

            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 4, true, "STATEMENT(EXPRESSION)");
                Assert.assertFalse(event.isLanguageContextInitialized());
                Assert.assertTrue(event.getBreakpoints().contains(bp4));
                event.prepareStepOver(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 6, true, "STATEMENT(EXPRESSION)");
                Assert.assertFalse(event.isLanguageContextInitialized());
                Assert.assertTrue(event.getBreakpoints().contains(bp6));
                filterBuilder.ignoreLanguageContextInitialization(false);
                session.setSteppingFilter(filterBuilder.build());
                event.prepareStepOver(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                Assert.assertFalse(event.isLanguageContextInitialized());
                checkState(event, 8, true, "STATEMENT(CONSTANT(1))").prepareContinue();
            });
            expectDone();
        }
    }

}
