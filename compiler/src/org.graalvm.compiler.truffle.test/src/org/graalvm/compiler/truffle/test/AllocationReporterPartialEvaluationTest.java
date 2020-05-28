/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.graalvm.compiler.truffle.runtime.GraalTruffleRuntime;
import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotAccess;
import org.junit.Test;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.AllocationEvent;
import com.oracle.truffle.api.instrumentation.AllocationEventFilter;
import com.oracle.truffle.api.instrumentation.AllocationListener;
import com.oracle.truffle.api.instrumentation.AllocationReporter;
import com.oracle.truffle.api.instrumentation.EventBinding;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.nodes.RootNode;

/**
 * Test of consistent behavior of AllocationReporter when individual calls are optimized or
 * deoptimized.
 */
public class AllocationReporterPartialEvaluationTest extends TestWithSynchronousCompiling {

    private static final GraalTruffleRuntime runtime = (GraalTruffleRuntime) Truffle.getRuntime();

    @Test
    public void testConsistentAssertions() {
        // Test that onEnter()/onReturnValue() are not broken
        // when only one of them is compiled with PE.
        Context context = Context.newBuilder(AllocationReporterLanguage.ID).allowPolyglotAccess(PolyglotAccess.ALL).build();
        context.initialize(AllocationReporterLanguage.ID);
        final TestAllocationReporter tester = context.getEngine().getInstruments().get(TestAllocationReporter.ID).lookup(TestAllocationReporter.class);
        assertNotNull(tester);
        final AllocationReporter reporter = (AllocationReporter) context.getPolyglotBindings().getMember(AllocationReporter.class.getSimpleName()).asHostObject();
        final Long[] value = new Long[]{1L};
        OptimizedCallTarget enterTarget = (OptimizedCallTarget) runtime.createCallTarget(new RootNode(null) {
            @Override
            public Object execute(VirtualFrame frame) {
                reporter.onEnter(value[0], 4, 8);
                return null;
            }
        });
        OptimizedCallTarget returnTarget = (OptimizedCallTarget) runtime.createCallTarget(new RootNode(null) {
            @Override
            public Object execute(VirtualFrame frame) {
                reporter.onReturnValue(value[0], 4, 8);
                return null;
            }
        });

        // Interpret both:
        assertNotCompiled(enterTarget);
        enterTarget.call();
        assertNotCompiled(returnTarget);
        returnTarget.call();
        value[0]++;
        enterTarget.compile(true);
        returnTarget.compile(true);
        assertCompiled(enterTarget);
        assertCompiled(returnTarget);
        long expectedCounters = allocCounter(value[0]);
        assertEquals(expectedCounters, tester.getEnterCount());
        assertEquals(expectedCounters, tester.getReturnCount());

        for (int j = 0; j < 2; j++) {
            // Compile both:
            for (int i = 0; i < 5; i++) {
                assertCompiled(enterTarget);
                enterTarget.call();
                assertCompiled(returnTarget);
                returnTarget.call();
                value[0]++;
            }
            expectedCounters = allocCounter(value[0]);
            assertEquals(expectedCounters, tester.getEnterCount());
            assertEquals(expectedCounters, tester.getReturnCount());

            // Deoptimize enter:
            enterTarget.invalidate(this, "test");
            assertNotCompiled(enterTarget);
            enterTarget.call();
            assertCompiled(returnTarget);
            returnTarget.call();
            value[0]++;
            enterTarget.compile(true);
            returnTarget.compile(true);
            assertCompiled(enterTarget);
            assertCompiled(returnTarget);

            // Deoptimize return:
            returnTarget.invalidate(this, "test");
            assertCompiled(enterTarget);
            enterTarget.call();
            assertNotCompiled(returnTarget);
            returnTarget.call();
            value[0]++;
            enterTarget.compile(true);
            returnTarget.compile(true);
            assertCompiled(enterTarget);
            assertCompiled(returnTarget);

            // Deoptimize both:
            enterTarget.invalidate(this, "test");
            returnTarget.invalidate(this, "test");
            assertNotCompiled(enterTarget);
            enterTarget.call();
            assertNotCompiled(returnTarget);
            returnTarget.call();
            value[0]++;
            enterTarget.compile(true);
            returnTarget.compile(true);
            assertCompiled(enterTarget);
            assertCompiled(returnTarget);
        }
        // Check that the allocation calls happened:
        expectedCounters = allocCounter(value[0]);
        assertEquals(expectedCounters, tester.getEnterCount());
        assertEquals(expectedCounters, tester.getReturnCount());
        assertCompiled(enterTarget);
        assertCompiled(returnTarget);

        // Verify that the assertions work in the compiled code:
        value[0] = null;
        boolean expectedFailure = true;
        // Deoptimize for assertions to be active
        enterTarget.invalidate(this, "test");
        try {
            enterTarget.call();
            expectedFailure = false;
        } catch (AssertionError err) {
            // O.K.
        }
        assertTrue("onEnter(null) did not fail!", expectedFailure);

        // Deoptimize for assertions to be active
        returnTarget.invalidate(this, "test");

        value[0] = Long.MIN_VALUE;
        try {
            returnTarget.call();
            expectedFailure = false;
        } catch (AssertionError err) {
            // O.K.
        }
        assertTrue("onReturn(<unseen value>) did not fail!", expectedFailure);

    }

    private static long allocCounter(long n) {
        return n * (n - 1) / 2;
    }

    @TruffleLanguage.Registration(id = AllocationReporterLanguage.ID, name = "Allocation Reporter PE Test Language")
    public static class AllocationReporterLanguage extends TruffleLanguage<AllocationReporter> {

        static final String ID = "truffle-allocation-reporter-pe-test-language";

        @Override
        protected AllocationReporter createContext(TruffleLanguage.Env env) {
            AllocationReporter reporter = env.lookup(AllocationReporter.class);
            env.exportSymbol(AllocationReporter.class.getSimpleName(), env.asGuestValue(reporter));
            return reporter;
        }

    }

    @TruffleInstrument.Registration(id = TestAllocationReporter.ID, services = TestAllocationReporter.class)
    public static class TestAllocationReporter extends TruffleInstrument implements AllocationListener {

        static final String ID = "testAllocationReporterPE";

        private EventBinding<TestAllocationReporter> allocationEventBinding;
        private long enterCounter = 0;
        private long returnCounter = 0;

        @Override
        protected void onCreate(TruffleInstrument.Env env) {
            env.registerService(this);
            LanguageInfo testLanguage = env.getLanguages().get(AllocationReporterLanguage.ID);
            allocationEventBinding = env.getInstrumenter().attachAllocationListener(AllocationEventFilter.newBuilder().languages(testLanguage).build(), this);
        }

        @Override
        protected void onDispose(TruffleInstrument.Env env) {
            allocationEventBinding.dispose();
        }

        EventBinding<TestAllocationReporter> getAllocationEventBinding() {
            return allocationEventBinding;
        }

        long getEnterCount() {
            return enterCounter;
        }

        long getReturnCount() {
            return returnCounter;
        }

        @Override
        public void onEnter(AllocationEvent event) {
            enterCounter += (Long) event.getValue();
        }

        @Override
        public void onReturnValue(AllocationEvent event) {
            returnCounter += (Long) event.getValue();
        }

    }

}
