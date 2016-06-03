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
import java.util.stream.Collectors;

import org.junit.Test;

import com.oracle.graal.debug.Debug;
import com.oracle.graal.debug.DebugConfig;
import com.oracle.graal.debug.DebugCounter;
import com.oracle.graal.debug.DebugDumpHandler;
import com.oracle.graal.debug.DebugMemUseTracker;
import com.oracle.graal.debug.DebugMethodMetrics;
import com.oracle.graal.debug.DebugTimer;
import com.oracle.graal.debug.DebugValueFactory;
import com.oracle.graal.debug.DebugVerifyHandler;
import com.oracle.graal.debug.GraalDebugConfig;
import com.oracle.graal.debug.internal.CounterImpl;
import com.oracle.graal.debug.internal.DebugScope;
import com.oracle.graal.debug.internal.MemUseTrackerImpl;
import com.oracle.graal.debug.internal.TimerImpl;
import com.oracle.graal.debug.internal.method.MethodMetricsImpl;
import com.oracle.graal.debug.internal.method.MethodMetricsPrinter;
import com.oracle.graal.options.OptionValue;
import com.oracle.graal.options.OptionValue.OverrideScope;
import com.oracle.graal.phases.Phase;

import jdk.vm.ci.meta.ResolvedJavaMethod;

// intercepting metrics
public class MethodMetricsTestInterception01 extends MethodMetricsTest {

    @Override
    protected Phase additionalPhase() {
        return new MethodMetricPhases.CountingAddPhase();
    }

    @Override
    DebugConfig getConfig() {
        DebugConfig config = DebugScope.getConfig();
        List<DebugDumpHandler> dumpHandlers = config == null ? new ArrayList<>() : config.dumpHandlers().stream().collect(Collectors.toList());
        List<DebugVerifyHandler> verifyHandlers = config == null ? new ArrayList<>() : config.verifyHandlers().stream().collect(Collectors.toList());
        GraalDebugConfig debugConfig = new GraalDebugConfig(
                        GraalDebugConfig.Options.Log.getValue(),
                        "CountingAddPhase",
                        GraalDebugConfig.Options.TrackMemUse.getValue(),
                        "CountingAddPhase",
                        GraalDebugConfig.Options.Dump.getValue(),
                        GraalDebugConfig.Options.Verify.getValue(),
                        "MethodMetricsTest$TestApplication.*",
                        "CountingAddPhase",
                        System.out, dumpHandlers, verifyHandlers);
        return debugConfig;
    }

    @Override
    protected OverrideScope getOScope() {
        Map<OptionValue<?>, Object> mapping = new HashMap<>();
        mapping.put(MethodMetricsPrinter.Options.MethodMeterPrintAscii, true);
        return OptionValue.override(mapping);
    }

    @Test
    @Override
    @SuppressWarnings("try")
    public void test() throws Throwable {
        Debug.setDebugValueFactory(new DebugValueFactory() {
            @Override
            public DebugTimer createTimer(String name, boolean conditional) {
                return new TimerImpl(name, conditional, true);
            }

            @Override
            public DebugCounter createCounter(String name, boolean conditional) {
                return CounterImpl.create(name, conditional, true);
            }

            @Override
            public DebugMethodMetrics createMethodMetrics(ResolvedJavaMethod method) {
                return MethodMetricsImpl.getMethodMetrics(method);
            }

            @Override
            public DebugMemUseTracker createMemUseTracker(String name, boolean conditional) {
                return new MemUseTrackerImpl(name, conditional, true);
            }
        });
        super.test();

    }

    @Override
    @SuppressWarnings("try")
    void assertValues() throws Throwable {
        assertValues("GlobalMetric", new long[]{1, 1, 1, 1, 1, 1, 1, 1, 1, 1});
    }
}
