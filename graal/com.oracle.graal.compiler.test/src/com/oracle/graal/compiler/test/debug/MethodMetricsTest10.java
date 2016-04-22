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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.graal.debug.Debug;
import com.oracle.graal.debug.DebugCloseable;
import com.oracle.graal.debug.DebugConfig;
import com.oracle.graal.debug.DebugConfigScope;
import com.oracle.graal.debug.DebugCounter;
import com.oracle.graal.debug.DebugDumpHandler;
import com.oracle.graal.debug.DebugMemUseTracker;
import com.oracle.graal.debug.DebugMethodMetrics;
import com.oracle.graal.debug.DebugTimer;
import com.oracle.graal.debug.DebugValueFactory;
import com.oracle.graal.debug.DebugVerifyHandler;
import com.oracle.graal.debug.DelegatingDebugConfig;
import com.oracle.graal.debug.DelegatingDebugConfig.Feature;
import com.oracle.graal.debug.GraalDebugConfig;
import com.oracle.graal.debug.internal.DebugScope;
import com.oracle.graal.debug.internal.method.MethodMetricsImpl;
import com.oracle.graal.debug.internal.method.MethodMetricsPrinter;
import com.oracle.graal.options.OptionValue;
import com.oracle.graal.options.OptionValue.OverrideScope;
import com.oracle.graal.phases.Phase;

import jdk.vm.ci.meta.ResolvedJavaMethod;

// intercepting metrics
public class MethodMetricsTest10 extends MethodMetricsTest {

    @Override
    protected Phase additionalPhase() {
        return new MethodMetricPhases.ScopeTestPhase();
    }

    private DebugValueFactory factory;

    public void setFactory() {
        /*
         * setting a custom debug value factory creating a constant timer for checking scope
         * creation and inlining scopes with metric intercepting works
         */
        factory = Debug.getDebugValueFactory();
        Debug.setDebugValueFactory(new DebugValueFactory() {
            @Override
            public DebugTimer createTimer(String name, boolean conditional) {
                // can still use together with real timer
                // TimerImpl realTimer = new TimerImpl(name, conditional, true);
                return new DebugTimer() {
                    int runs = 0;

                    // private DebugCloseable t;

                    @Override
                    public DebugCloseable start() {
                        // t = realTimer.start();
                        return new DebugCloseable() {
                            @Override
                            public void close() {
                                // t.close();
                                runs++;
                                MethodMetricsImpl.addToCurrentScopeMethodMetrics(name, 1);
                            }
                        };
                    }

                    @Override
                    public void setConditional(boolean flag) {

                    }

                    @Override
                    public boolean isConditional() {
                        return false;
                    }

                    @Override
                    public TimeUnit getTimeUnit() {
                        return TimeUnit.MILLISECONDS;
                    }

                    @Override
                    public long getCurrentValue() {
                        return runs;
                    }
                };
            }

            @Override
            public DebugCounter createCounter(String name, boolean conditional) {
                return factory.createCounter(name, conditional);
            }

            @Override
            public DebugMethodMetrics createMethodMetrics(ResolvedJavaMethod method) {
                return factory.createMethodMetrics(method);
            }

            @Override
            public DebugMemUseTracker createMemUseTracker(String name, boolean conditional) {
                return factory.createMemUseTracker(name, conditional);
            }
        });
    }

    @Override
    protected OverrideScope getOScope() {
        Map<OptionValue<?>, Object> mapping = new HashMap<>();
        mapping.put(MethodMetricsPrinter.Options.MethodMeterPrintAscii, true);
        mapping.put(GraalDebugConfig.Options.GlobalMetricsInterceptedByMethodMetrics, "X|X|O");
        return OptionValue.override(mapping);
    }

    @Test
    @Override
    public void test() throws Throwable {
        setFactory();
        super.test();
    }

    @Override
    public void afterTest() {
        super.afterTest();
        Debug.setDebugValueFactory(factory);
    }

    @Override
    DebugConfig getConfig() {
        DebugConfig config = DebugScope.getConfig();
        List<DebugDumpHandler> dumpHandlers = config == null ? new ArrayList<>() : config.dumpHandlers().stream().collect(Collectors.toList());
        List<DebugVerifyHandler> verifyHandlers = config == null ? new ArrayList<>() : config.verifyHandlers().stream().collect(Collectors.toList());
        GraalDebugConfig debugConfig = new GraalDebugConfig(
                        GraalDebugConfig.Options.Log.getValue(),
                        ""/* unscoped meter */,
                        GraalDebugConfig.Options.TrackMemUse.getValue(),
                        ""/* unscoped time */,
                        GraalDebugConfig.Options.Dump.getValue(),
                        GraalDebugConfig.Options.Verify.getValue(),
                        null /* no method filter */,
                        "" /* unscoped method metering */,
                        System.out, dumpHandlers, verifyHandlers);
        return debugConfig;
    }

    @Override
    @SuppressWarnings("try")
    void assertValues() throws Throwable {
        try (DebugConfigScope s = Debug.setConfig(new DelegatingDebugConfig().enable(Feature.METHOD_METRICS))) {
            Assert.assertEquals(50,
                            ((MethodMetricsImpl) Debug.methodMetrics(asResolvedJavaMethod(TestApplication.class.getMethod("m01",
                                            testSignature)))).getMetricValueFromCompilationIndex(0, "GlobalTimer4_WithoutInlineEnhancement"));
            Assert.assertEquals(50,
                            ((MethodMetricsImpl) Debug.methodMetrics(asResolvedJavaMethod(TestApplication.class.getMethod("m02",
                                            testSignature)))).getMetricValueFromCompilationIndex(0, "GlobalTimer4_WithoutInlineEnhancement"));
            Assert.assertEquals(50,
                            ((MethodMetricsImpl) Debug.methodMetrics(asResolvedJavaMethod(TestApplication.class.getMethod("m03",
                                            testSignature)))).getMetricValueFromCompilationIndex(0, "GlobalTimer4_WithoutInlineEnhancement"));
            Assert.assertEquals(50,
                            ((MethodMetricsImpl) Debug.methodMetrics(asResolvedJavaMethod(TestApplication.class.getMethod("m04",
                                            testSignature)))).getMetricValueFromCompilationIndex(0, "GlobalTimer4_WithoutInlineEnhancement"));
            Assert.assertEquals(50,
                            ((MethodMetricsImpl) Debug.methodMetrics(asResolvedJavaMethod(TestApplication.class.getMethod("m05",
                                            testSignature)))).getMetricValueFromCompilationIndex(0, "GlobalTimer4_WithoutInlineEnhancement"));
            Assert.assertEquals(50,
                            ((MethodMetricsImpl) Debug.methodMetrics(asResolvedJavaMethod(TestApplication.class.getMethod("m06",
                                            testSignature)))).getMetricValueFromCompilationIndex(0, "GlobalTimer4_WithoutInlineEnhancement"));
            Assert.assertEquals(50,
                            ((MethodMetricsImpl) Debug.methodMetrics(asResolvedJavaMethod(TestApplication.class.getMethod("m07",
                                            testSignature)))).getMetricValueFromCompilationIndex(0, "GlobalTimer4_WithoutInlineEnhancement"));
            Assert.assertEquals(50,
                            ((MethodMetricsImpl) Debug.methodMetrics(asResolvedJavaMethod(TestApplication.class.getMethod("m08",
                                            testSignature)))).getMetricValueFromCompilationIndex(0, "GlobalTimer4_WithoutInlineEnhancement"));
            Assert.assertEquals(50,
                            ((MethodMetricsImpl) Debug.methodMetrics(asResolvedJavaMethod(TestApplication.class.getMethod("m09",
                                            testSignature)))).getMetricValueFromCompilationIndex(0, "GlobalTimer4_WithoutInlineEnhancement"));
            Assert.assertEquals(50,
                            ((MethodMetricsImpl) Debug.methodMetrics(asResolvedJavaMethod(TestApplication.class.getMethod("m10",
                                            testSignature)))).getMetricValueFromCompilationIndex(0, "GlobalTimer4_WithoutInlineEnhancement"));
        }
    }

}
