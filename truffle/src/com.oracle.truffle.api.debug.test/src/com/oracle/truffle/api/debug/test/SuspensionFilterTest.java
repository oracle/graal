/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Collections;
import java.util.HashMap;
import java.util.function.Predicate;

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
                checkState(event, 8, true, "STATEMENT(CONSTANT(1))", "loopIndex0", String.valueOf(0), "loopResult0", "Null").prepareContinue();
            });
            expectDone();
        }
    }

    @Test
    public void testInternalNoSuspend() throws Exception {
        final Source source = Source.newBuilder(InstrumentationTestLanguage.ID, "ROOT(\n" +
                        "  STATEMENT(EXPRESSION),\n" +
                        "  STATEMENT(CONSTANT(42))\n" +
                        ")\n", "test").internal(true).build();
        // No suspension filter is necessary, internal sources are ignored by default
        try (DebuggerSession session = startSession()) {
            session.suspendNextExecution();
            startEval(source);
            // does not stop in internal source
            expectDone();
        }
    }

    @Test
    public void testInternalStepping() throws Exception {
        final Source internSource = Source.newBuilder(InstrumentationTestLanguage.ID, "ROOT(\n" +
                        "  DEFINE(intern, \n" +
                        "    STATEMENT(EXPRESSION),\n" +
                        "    STATEMENT(CONSTANT(42))\n" +
                        "  ),\n" +
                        "  CALL(intern)\n" +
                        ")\n", "intern").internal(true).build();
        final Source source = testSource("ROOT(\n" +
                        "  STATEMENT(CALL(intern)),\n" +
                        "  STATEMENT(CONSTANT(1)),\n" +
                        "  STATEMENT(CALL(intern))\n" +
                        ")\n");
        SuspensionFilter suspensionFilter = SuspensionFilter.newBuilder().includeInternal(false).build();
        try (DebuggerSession session = startSession()) {
            session.setSteppingFilter(suspensionFilter);
            session.suspendNextExecution();
            startEval(internSource);
            // does not stop in internal source
            expectDone();

            startEval(source);
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 2, true, "STATEMENT(CALL(intern))");
                event.prepareStepInto(1);
            });
            // Step into does not go into the internal source:
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 3, true, "STATEMENT(CONSTANT(1))");
                Breakpoint bp = Breakpoint.newBuilder(getSourceImpl(internSource)).lineIs(3).build();
                session.install(bp);
                event.prepareContinue();
            });
            // Breakpoint does not stop there.
            expectDone();
        }
    }

    @Test
    public void testInternalSteppingChange() throws Exception {
        final Source internSource = Source.newBuilder(InstrumentationTestLanguage.ID, "ROOT(\n" +
                        "  DEFINE(intern, \n" +
                        "    STATEMENT(EXPRESSION),\n" +
                        "    STATEMENT(CONSTANT(42))\n" +
                        "  ),\n" +
                        "  CALL(intern)\n" +
                        ")\n", "intern").internal(true).build();
        final Source source = testSource("ROOT(\n" +
                        "  LOOP(5,\n" +
                        "    STATEMENT(CALL(intern))\n" +
                        "  )\n" +
                        ")\n");
        SuspensionFilter suspensionFilter = SuspensionFilter.newBuilder().includeInternal(true).build();
        try (DebuggerSession session = startSession()) {
            session.setSteppingFilter(suspensionFilter);
            session.suspendNextExecution();
            startEval(internSource);
            // we stop in the internal source as the filter does not ignore internal sources now
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 3, true, "STATEMENT(EXPRESSION)");
                event.prepareStepOver(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 4, true, "STATEMENT(CONSTANT(42))");
                event.prepareContinue();
            });
            expectDone();

            // Ignore internal sources now
            suspensionFilter = SuspensionFilter.newBuilder().includeInternal(false).build();
            session.setSteppingFilter(suspensionFilter);
            session.suspendNextExecution();
            startEval(source);
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 3, true, "STATEMENT(CALL(intern))", "loopIndex0", String.valueOf(0), "loopResult0", "Null");
                event.prepareStepInto(1);
            });
            // Step into does not go into the internal source:
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 3, true, "STATEMENT(CALL(intern))", "loopIndex0", String.valueOf(1), "loopResult0", "42");
                // do not ignore instenal sources again
                session.setSteppingFilter(SuspensionFilter.newBuilder().includeInternal(true).build());
                event.prepareStepInto(1);
            });
            // Stopped in an internal source again
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 3, true, "STATEMENT(EXPRESSION)");
                event.prepareContinue();
            });
            expectDone();
        }
    }

    @Test
    public void testInternalBreakpoints() throws Exception {
        final Source internSource = Source.newBuilder(InstrumentationTestLanguage.ID, "ROOT(\n" +
                        "  DEFINE(intern, \n" +
                        "    STATEMENT(EXPRESSION)\n" +
                        "  ),\n" +
                        "  CALL(intern)\n" +
                        ")\n", "intern").internal(true).build();
        try (DebuggerSession session = startSession()) {
            Breakpoint breakpoint = Breakpoint.newBuilder(getSourceImpl(internSource)).lineIs(3).build();
            session.install(breakpoint);
            startEval(internSource);
            // By default, breakpoint does not stop in the internal source.
            expectDone();

            // When allowed by the suspension filter, it does stop:
            session.setSteppingFilter(SuspensionFilter.newBuilder().includeInternal(true).build());
            startEval(internSource);
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 3, true, "STATEMENT(EXPRESSION)");
                event.prepareContinue();
            });
            expectDone();

            // In another session it does not stop:
            try (DebuggerSession session2 = startSession()) {
                session2.install(breakpoint);
                startEval(internSource);
                // Stops in the `session` only:
                expectSuspended((SuspendedEvent event) -> {
                    Assert.assertSame(session, event.getSession());
                    event.prepareContinue();
                });
                expectDone();

                // Stops in both sessions:
                session2.setSteppingFilter(SuspensionFilter.newBuilder().includeInternal(true).build());
                startEval(internSource);
                expectSuspended((SuspendedEvent event) -> {
                    Assert.assertSame(session, event.getSession());
                    event.prepareContinue();
                });
                expectSuspended((SuspendedEvent event) -> {
                    Assert.assertSame(session2, event.getSession());
                    event.prepareContinue();
                });
                expectDone();
            }
            // Disallow internal sources again:
            session.setSteppingFilter(SuspensionFilter.newBuilder().includeInternal(false).build());
            startEval(internSource);
            expectDone();
        }
    }

    @Test
    public void testSourceFilter() {
        final Source source1 = Source.newBuilder(InstrumentationTestLanguage.ID, "ROOT(\n" +
                        "  DEFINE(foo1,\n" +
                        "    STATEMENT(CONSTANT(43))\n" +
                        "  ))\n", "Source1").buildLiteral();
        final Source source2 = Source.newBuilder(InstrumentationTestLanguage.ID, "ROOT(\n" +
                        "  DEFINE(foo2,\n" +
                        "    STATEMENT(CONSTANT(44))\n" +
                        "  ))\n", "Source2").buildLiteral();
        final Source source3 = testSource("ROOT(\n" +
                        "  CALL(foo1),\n" +
                        "  CALL(foo2),\n" +
                        "  STATEMENT(CALL(foo1)),\n" +
                        "  STATEMENT(CALL(foo2)),\n" +
                        "  STATEMENT(CALL(foo1)),\n" +
                        "  STATEMENT(CALL(foo2)),\n" +
                        "  STATEMENT(CONSTANT(100))\n" +
                        ")\n");
        try (DebuggerSession session = startSession()) {
            // Filter out all sections
            SuspensionFilter suspensionFilter = SuspensionFilter.newBuilder().sourceIs(s -> false).build();
            session.setSteppingFilter(suspensionFilter);
            session.suspendNextExecution();
            startEval(source1);
            expectDone();
            startEval(source2);
            expectDone();
            startEval(source3);
            expectDone();

            Predicate<com.oracle.truffle.api.source.Source> filterSource1 = source -> {
                return source.getName().indexOf("Source1") < 0;
            };
            suspensionFilter = SuspensionFilter.newBuilder().sourceIs(filterSource1).build();
            session.setSteppingFilter(suspensionFilter);
            session.suspendNextExecution();
            startEval(source3);

            // Skip foo1 and suspend in foo2:
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 3, true, "STATEMENT(CONSTANT(44))");
                event.prepareStepOver(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 3, false, "CALL(foo2)");
                event.prepareStepInto(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 4, true, "STATEMENT(CALL(foo1))");
                event.prepareStepInto(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 5, true, "STATEMENT(CALL(foo2))");
                event.prepareStepInto(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 3, true, "STATEMENT(CONSTANT(44))");
                event.prepareStepInto(2);
            });
            // Change the filter to filter Source2 out:
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 6, true, "STATEMENT(CALL(foo1))");
                Predicate<com.oracle.truffle.api.source.Source> filterSource2 = source -> {
                    return source.getName().indexOf("Source2") < 0;
                };
                session.setSteppingFilter(SuspensionFilter.newBuilder().sourceIs(filterSource2).build());
                event.prepareStepInto(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 3, true, "STATEMENT(CONSTANT(43))");
                event.prepareStepOut(1).prepareStepOver(1).prepareStepInto(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 8, true, "STATEMENT(CONSTANT(100))");
                event.prepareContinue();
            });
            expectDone();
        }
    }

    @Test
    public void testSourceFilterBreakpoints() {
        final Source source1 = Source.newBuilder(InstrumentationTestLanguage.ID, "ROOT(\n" +
                        "  DEFINE(foo1,\n" +
                        "    STATEMENT(CONSTANT(43))\n" +
                        "  ))\n", "Source1").buildLiteral();
        final Source source2 = Source.newBuilder(InstrumentationTestLanguage.ID, "ROOT(\n" +
                        "  DEFINE(foo2,\n" +
                        "    STATEMENT(CONSTANT(44))\n" +
                        "  ))\n", "Source2").buildLiteral();
        final Source source3 = testSource("ROOT(\n" +
                        "  CALL(foo1),\n" +
                        "  CALL(foo2)\n" +
                        ")\n");
        try (DebuggerSession session = startSession()) {
            Breakpoint breakpoint1 = Breakpoint.newBuilder(getSourceImpl(source1)).lineIs(3).build();
            Breakpoint breakpoint2 = Breakpoint.newBuilder(getSourceImpl(source2)).lineIs(3).build();
            session.install(breakpoint1);
            session.install(breakpoint2);
            // Filter out all sections
            SuspensionFilter suspensionFilter = SuspensionFilter.newBuilder().sourceIs(s -> false).build();
            session.setSteppingFilter(suspensionFilter);
            startEval(source1);
            expectDone();
            startEval(source2);
            expectDone();
            startEval(source3);
            expectDone();

            session.setSteppingFilter(SuspensionFilter.newBuilder().build()); // Allow all
            startEval(source3);
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 3, true, "STATEMENT(CONSTANT(43))");
                Assert.assertEquals(1, event.getBreakpoints().size());
                Assert.assertSame(breakpoint1, event.getBreakpoints().get(0));
                event.prepareContinue();
            });
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 3, true, "STATEMENT(CONSTANT(44))");
                Assert.assertEquals(1, event.getBreakpoints().size());
                Assert.assertSame(breakpoint2, event.getBreakpoints().get(0));
                event.prepareContinue();
            });
            expectDone();

            // Filter out Source1:
            Predicate<com.oracle.truffle.api.source.Source> filterSource1 = source -> {
                return source.getName().indexOf("Source1") < 0;
            };
            suspensionFilter = SuspensionFilter.newBuilder().sourceIs(filterSource1).build();
            session.setSteppingFilter(suspensionFilter);
            startEval(source3);
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 3, true, "STATEMENT(CONSTANT(44))");
                Assert.assertEquals(1, event.getBreakpoints().size());
                Assert.assertSame(breakpoint2, event.getBreakpoints().get(0));
                event.prepareContinue();
            });
            expectDone();

            // A second session with different filters:
            try (DebuggerSession session2 = startSession()) {
                Predicate<com.oracle.truffle.api.source.Source> filterSource2 = source -> {
                    return source.getName().indexOf("Source2") < 0;
                };
                session2.setSteppingFilter(SuspensionFilter.newBuilder().sourceIs(filterSource2).build());
                session2.install(breakpoint1);
                session2.install(breakpoint2);

                startEval(source3);
                expectSuspended((SuspendedEvent event) -> {
                    checkState(event, 3, true, "STATEMENT(CONSTANT(43))");
                    Assert.assertEquals(1, event.getBreakpoints().size());
                    Assert.assertSame(breakpoint1, event.getBreakpoints().get(0));
                    Assert.assertSame(session2, event.getSession());
                    event.prepareContinue();
                });
                expectSuspended((SuspendedEvent event) -> {
                    checkState(event, 3, true, "STATEMENT(CONSTANT(44))");
                    Assert.assertEquals(1, event.getBreakpoints().size());
                    Assert.assertSame(breakpoint2, event.getBreakpoints().get(0));
                    Assert.assertSame(session, event.getSession());
                    event.prepareContinue();
                });
                expectDone();
            }
        }
    }
}
