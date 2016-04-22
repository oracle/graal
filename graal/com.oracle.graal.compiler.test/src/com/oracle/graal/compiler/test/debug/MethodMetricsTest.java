/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.compiler.test.debug;

import java.io.PrintStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.graal.compiler.test.GraalCompilerTest;
import com.oracle.graal.debug.Debug;
import com.oracle.graal.debug.DebugCloseable;
import com.oracle.graal.debug.DebugConfig;
import com.oracle.graal.debug.DebugConfigScope;
import com.oracle.graal.debug.DebugCounter;
import com.oracle.graal.debug.DebugDumpHandler;
import com.oracle.graal.debug.DebugMethodMetrics;
import com.oracle.graal.debug.DebugTimer;
import com.oracle.graal.debug.DebugVerifyHandler;
import com.oracle.graal.debug.DelegatingDebugConfig;
import com.oracle.graal.debug.DelegatingDebugConfig.Feature;
import com.oracle.graal.debug.GraalDebugConfig;
import com.oracle.graal.debug.internal.DebugScope;
import com.oracle.graal.debug.internal.method.MethodMetricsInlineeScopeInfo;
import com.oracle.graal.debug.internal.method.MethodMetricsImpl;
import com.oracle.graal.debug.internal.method.MethodMetricsPrinter;
import com.oracle.graal.nodes.IfNode;
import com.oracle.graal.nodes.InvokeNode;
import com.oracle.graal.nodes.LoopBeginNode;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.calc.BinaryNode;
import com.oracle.graal.nodes.calc.FixedBinaryNode;
import com.oracle.graal.nodes.calc.IntegerDivNode;
import com.oracle.graal.nodes.calc.MulNode;
import com.oracle.graal.nodes.calc.ShiftNode;
import com.oracle.graal.nodes.calc.SubNode;
import com.oracle.graal.options.OptionValue;
import com.oracle.graal.options.OptionValue.OverrideScope;
import com.oracle.graal.phases.BasePhase;
import com.oracle.graal.phases.Phase;
import com.oracle.graal.phases.PhaseSuite;
import com.oracle.graal.phases.common.CanonicalizerPhase;
import com.oracle.graal.phases.common.ConvertDeoptimizeToGuardPhase;
import com.oracle.graal.phases.schedule.SchedulePhase;
import com.oracle.graal.phases.schedule.SchedulePhase.SchedulingStrategy;
import com.oracle.graal.phases.tiers.HighTierContext;
import com.oracle.graal.phases.tiers.Suites;

import jdk.vm.ci.meta.ResolvedJavaMethod;

public abstract class MethodMetricsTest extends GraalCompilerTest {
    static class TestApplication {
        public static int m01(int x, int y) {
            return x + y;
        }

        public static int m02(int x, int y) {
            return x * y;
        }

        public static int m03(int x, int y) {
            return x ^ y;
        }

        public static int m04(int x, int y) {
            return x >> y;
        }

        public static int m05(int x, int y) {
            return x >>> y;
        }

        public static int m06(int x, int y) {
            return x << y;
        }

        public static int m07(int x, int y) {
            return x > y ? 0 : 1;
        }

        public static int m08(int x, int y) {
            return x % y;
        }

        public static int m09(int x, int y) {
            return x / y;
        }

        public static int m10(int x, int y) {
            return x - y;
        }

    }

    static class CFApplications {

        public static int cf01(int count) {
            int a = 0;
            int b = 0;
            int c = 0;
            l1: for (int i = 0; i <= count; i++) {
                if (i > 5) {
                    for (int j = 0; j < i; j++) {
                        a += i;
                        if (a > 500) {
                            break l1;
                        }
                    }
                } else if (i > 7) {
                    b += i;
                } else {
                    c += i;
                }
            }
            return a + b + c;
        }

        public static int cf02(int arg) {
            int a = 0;
            for (int i = 0; i < arg; i++) {
                a += i;
            }
            return a;
        }

        private static int cnt;

        public static String cf03(int arg) {
            cnt = 0;
            int count = arg;
            for (int i = 0; i < arg; i++) {
                count++;
                foo();
            }
            return "ok" + count + "-" + cnt;
        }

        public static void foo() {
            cnt++;
        }

        public static int cf04(int count) {
            int i1 = 1;
            int i2 = 2;
            int i3 = 3;
            int i4 = 4;

            for (int i = 0; i < count; i++) {
                i1 = i2;
                i2 = i3;
                i3 = i4;
                i4 = i1;
            }
            return i1 + i2 * 10 + i3 * 100 + i4 * 1000;
        }
    }

    public static final Class<?>[] testSignature = new Class<?>[]{int.class, int.class};
    public static final Object[] testArgs = new Object[]{10, 10};

    static class MethodMetricPhases {
        static class CountingAddPhase extends Phase {

            // typically those global metrics would be static final, but we need new timers every
            // invocation if we override the debugvaluefactory
            private final DebugCounter globalCounter = Debug.counter("GlobalMetric");
            private final DebugTimer globalTimer = Debug.timer("GlobalTimer");

            @Override
            @SuppressWarnings("try")
            protected void run(StructuredGraph graph) {
                try (DebugCloseable d = globalTimer.start()) {
                    ResolvedJavaMethod method = graph.method();
                    DebugMethodMetrics mm = Debug.methodMetrics(method);
                    mm.addToMetric(graph.getNodes().filter(InvokeNode.class).count(), "Invokes");
                    mm.incrementMetric("PhaseRunsOnMethod");
                    globalCounter.increment();
                }
            }
        }

        static class CountingShiftPhase extends Phase {
            @Override
            protected void run(StructuredGraph graph) {
                Debug.methodMetrics(graph.method()).addToMetric(graph.getNodes().filter(ShiftNode.class).count(), "Shifts");
            }
        }

        static class CountingMulPhase extends Phase {
            @Override
            protected void run(StructuredGraph graph) {
                Debug.methodMetrics(graph.method()).addToMetric(graph.getNodes().filter(MulNode.class).count(), "Muls");
            }
        }

        static class CountingSubPhase extends Phase {
            @Override
            protected void run(StructuredGraph graph) {
                Debug.methodMetrics(graph.method()).addToMetric(graph.getNodes().filter(SubNode.class).count(), "Subs");
            }
        }

        static class CountingDivPhase extends Phase {
            @Override
            protected void run(StructuredGraph graph) {
                Debug.methodMetrics(graph.method()).addToMetric(graph.getNodes().filter(IntegerDivNode.class).count(), "Divs");
            }
        }

        static class CountingBinOpPhase extends Phase {
            @Override
            protected void run(StructuredGraph graph) {
                Debug.methodMetrics(graph.method()).addToMetric(graph.getNodes().filter(x -> x instanceof BinaryNode || x instanceof FixedBinaryNode).count(), "BinOps");
            }
        }

        static class CountingLoopBeginsAndIfs extends Phase {
            @Override
            protected void run(StructuredGraph graph) {
                ResolvedJavaMethod method = graph.method();
                DebugMethodMetrics mm = Debug.methodMetrics(method);
                mm.addToMetric(graph.getNodes().filter(LoopBeginNode.class).count(), "LoopBegins");
                mm.addToMetric(graph.getNodes().filter(IfNode.class).count(), "Ifs");
            }
        }

        static class CountingMorePhase extends Phase {
            @Override
            protected void run(StructuredGraph graph) {
                ResolvedJavaMethod method = graph.method();
                DebugMethodMetrics mm = Debug.methodMetrics(method);
                mm.addToMetric(graph.getNodes().filter(LoopBeginNode.class).count(), "LoopBegins");
                mm.addToMetric(graph.getNodes().filter(IfNode.class).count(), "Ifs");
                mm.addToMetric(graph.getNodes().filter(x -> x instanceof BinaryNode || x instanceof FixedBinaryNode).count(), "BinOps");
                mm.addToMetric(graph.getNodes().filter(SubNode.class).count(), "Subs");
                mm.addToMetric(graph.getNodes().filter(InvokeNode.class).count(), "Invokes");
                mm.incrementMetric("PhaseRunsOnMethod");
            }
        }

        static class ScopeTestPhase extends Phase {
            // typically those global metrics would be static final, but we need new timers every
            // invocation if we override the debugvaluefactory
            private final DebugTimer timer = Debug.timer("GlobalTimer1");
            private final DebugTimer scopedTimer = Debug.timer("GlobalTimer2");
            private final DebugTimer scopedScopedTimer = Debug.timer("GlobalTimer3");
            private final DebugTimer scopedScopedScopeTimer = Debug.timer("GlobalTimer4");

            private final DebugTimer timer1 = Debug.timer("GlobalTimer1_WithoutInlineEnhancement");
            private final DebugTimer scopedTimer1 = Debug.timer("GlobalTimer2_WithoutInlineEnhancement");
            private final DebugTimer scopedScopedTimer1 = Debug.timer("GlobalTimer3_WithoutInlineEnhancement");
            private final DebugTimer scopedScopedScopeTimer1 = Debug.timer("GlobalTimer4_WithoutInlineEnhancement");

            @Override
            @SuppressWarnings("try")
            protected void run(StructuredGraph graph) {
                // we are in an enhanced debug scope from graal compiler
                // no we open multiple inlining scopes, record their time
                try (DebugCloseable c1 = timer.start()) {
                    try (DebugCloseable c2 = scopedTimer.start()) {
                        try (DebugCloseable c3 = scopedScopedTimer.start()) {
                            // do sth unnecessary long allocating many inlinee scopes
                            for (int i = 0; i < 50; i++) {
                                try (Debug.Scope s1 = Debug.methodMetricsScope("InlineEnhancement1", MethodMetricsInlineeScopeInfo.create(graph.method()), false)) {
                                    try (DebugCloseable c4 = scopedScopedScopeTimer.start()) {
                                        new SchedulePhase(SchedulingStrategy.LATEST_OUT_OF_LOOPS).apply(graph);
                                        // double scoped inlinee scopes should not make problems
                                        // with the data
                                        try (Debug.Scope s2 = Debug.methodMetricsScope("InlineEnhancement2", MethodMetricsInlineeScopeInfo.create(graph.method()),
                                                        false)) {
                                            new SchedulePhase(SchedulingStrategy.LATEST_OUT_OF_LOOPS).apply(graph);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // no lets try different counters without the inline enhancement
                try (DebugCloseable c1 = timer1.start()) {
                    try (DebugCloseable c2 = scopedTimer1.start()) {
                        try (DebugCloseable c3 = scopedScopedTimer1.start()) {
                            // do sth unnecessary long allocating many inlinee scopes
                            for (int i = 0; i < 50; i++) {
                                try (DebugCloseable c4 = scopedScopedScopeTimer1.start()) {
                                    new SchedulePhase(SchedulingStrategy.LATEST_OUT_OF_LOOPS).apply(graph);
                                    new SchedulePhase(SchedulingStrategy.LATEST_OUT_OF_LOOPS).apply(graph);
                                }
                            }
                        }
                    }
                }
            }

        }
    }

    static DebugConfig overrideGraalDebugConfig(PrintStream log, String methodFilter, String methodMeter) {
        DebugConfig config = DebugScope.getConfig();
        List<DebugDumpHandler> dumpHandlers = config == null ? new ArrayList<>() : config.dumpHandlers().stream().collect(Collectors.toList());
        List<DebugVerifyHandler> verifyHandlers = config == null ? new ArrayList<>() : config.verifyHandlers().stream().collect(Collectors.toList());
        GraalDebugConfig debugConfig = new GraalDebugConfig(
                        GraalDebugConfig.Options.Log.getValue(),
                        GraalDebugConfig.Options.Count.getValue(),
                        GraalDebugConfig.Options.TrackMemUse.getValue(),
                        GraalDebugConfig.Options.Time.getValue(),
                        GraalDebugConfig.Options.Dump.getValue(),
                        GraalDebugConfig.Options.Verify.getValue(),
                        methodFilter,
                        methodMeter,
                        log, dumpHandlers, verifyHandlers);
        return debugConfig;
    }

    private static OverrideScope overrideMetricPrinterConfig() {
        Map<OptionValue<?>, Object> mapping = new HashMap<>();
        mapping.put(MethodMetricsPrinter.Options.MethodMeterPrintAscii, true);
        return OptionValue.override(mapping);
    }

    abstract Phase additionalPhase();

    @Override
    protected Suites createSuites() {
        Suites ret = super.createSuites();
        ListIterator<BasePhase<? super HighTierContext>> iter = ret.getHighTier().findPhase(ConvertDeoptimizeToGuardPhase.class);
        PhaseSuite.findNextPhase(iter, CanonicalizerPhase.class);
        iter.add(additionalPhase());
        return ret;
    }

    @Test
    @SuppressWarnings("try")
    public void test() throws Throwable {
        try (DebugConfigScope s = Debug.setConfig(getConfig()); OverrideScope o = getOScope();) {
            executeMethod(TestApplication.class.getMethod("m01", testSignature), null, testArgs);
            executeMethod(TestApplication.class.getMethod("m02", testSignature), null, testArgs);
            executeMethod(TestApplication.class.getMethod("m03", testSignature), null, testArgs);
            executeMethod(TestApplication.class.getMethod("m04", testSignature), null, testArgs);
            executeMethod(TestApplication.class.getMethod("m05", testSignature), null, testArgs);
            executeMethod(TestApplication.class.getMethod("m06", testSignature), null, testArgs);
            executeMethod(TestApplication.class.getMethod("m07", testSignature), null, testArgs);
            executeMethod(TestApplication.class.getMethod("m08", testSignature), null, testArgs);
            executeMethod(TestApplication.class.getMethod("m09", testSignature), null, testArgs);
            executeMethod(TestApplication.class.getMethod("m10", testSignature), null, testArgs);
            assertValues();
        }
    }

    @After
    public void clearMMCache() {
        MethodMetricsImpl.clearMM();
    }

    abstract DebugConfig getConfig();

    OverrideScope getOScope() {
        return overrideMetricPrinterConfig();
    }

    abstract void assertValues() throws Throwable;

    @SuppressWarnings("try")
    void assertValues(String metricName, long[] vals) {
        try (DebugConfigScope s = Debug.setConfig(new DelegatingDebugConfig().enable(Feature.METHOD_METRICS))) {
            Assert.assertEquals(vals[0], ((MethodMetricsImpl) Debug.methodMetrics(asResolvedJavaMethod(TestApplication.class.getMethod("m01",
                            testSignature)))).getMetricValueFromCompilationIndex(0, metricName));
            Assert.assertEquals(vals[1], ((MethodMetricsImpl) Debug.methodMetrics(asResolvedJavaMethod(TestApplication.class.getMethod("m02",
                            testSignature)))).getMetricValueFromCompilationIndex(0, metricName));
            Assert.assertEquals(vals[2], ((MethodMetricsImpl) Debug.methodMetrics(asResolvedJavaMethod(TestApplication.class.getMethod("m03",
                            testSignature)))).getMetricValueFromCompilationIndex(0, metricName));
            Assert.assertEquals(vals[3], ((MethodMetricsImpl) Debug.methodMetrics(asResolvedJavaMethod(TestApplication.class.getMethod("m04",
                            testSignature)))).getMetricValueFromCompilationIndex(0, metricName));
            Assert.assertEquals(vals[4], ((MethodMetricsImpl) Debug.methodMetrics(asResolvedJavaMethod(TestApplication.class.getMethod("m05",
                            testSignature)))).getMetricValueFromCompilationIndex(0, metricName));
            Assert.assertEquals(vals[5], ((MethodMetricsImpl) Debug.methodMetrics(asResolvedJavaMethod(TestApplication.class.getMethod("m06",
                            testSignature)))).getMetricValueFromCompilationIndex(0, metricName));
            Assert.assertEquals(vals[6], ((MethodMetricsImpl) Debug.methodMetrics(asResolvedJavaMethod(TestApplication.class.getMethod("m07",
                            testSignature)))).getMetricValueFromCompilationIndex(0, metricName));
            Assert.assertEquals(vals[7], ((MethodMetricsImpl) Debug.methodMetrics(asResolvedJavaMethod(TestApplication.class.getMethod("m08",
                            testSignature)))).getMetricValueFromCompilationIndex(0, metricName));
            Assert.assertEquals(vals[8], ((MethodMetricsImpl) Debug.methodMetrics(asResolvedJavaMethod(TestApplication.class.getMethod("m09",
                            testSignature)))).getMetricValueFromCompilationIndex(0, metricName));
            Assert.assertEquals(vals[9], ((MethodMetricsImpl) Debug.methodMetrics(asResolvedJavaMethod(TestApplication.class.getMethod("m10",
                            testSignature)))).getMetricValueFromCompilationIndex(0, metricName));
        } catch (Throwable t) {
            Assert.fail(t.getMessage());
        }
    }

    void executeMethod(Method m, Object receiver, Object... args) {
        test(asResolvedJavaMethod(m), receiver, args);
    }

}
