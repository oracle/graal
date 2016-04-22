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

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.junit.Test;

import com.oracle.graal.api.test.Graal;
import com.oracle.graal.debug.Debug;
import com.oracle.graal.debug.DebugConfigScope;
import com.oracle.graal.debug.DebugMethodMetrics;
import com.oracle.graal.graph.Node;
import com.oracle.graal.java.GraphBuilderPhase;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.StructuredGraph.AllowAssumptions;
import com.oracle.graal.nodes.graphbuilderconf.GraphBuilderConfiguration;
import com.oracle.graal.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import com.oracle.graal.nodes.graphbuilderconf.InvocationPlugins;
import com.oracle.graal.phases.OptimisticOptimizations;
import com.oracle.graal.phases.Phase;
import com.oracle.graal.phases.PhaseSuite;
import com.oracle.graal.phases.VerifyPhase.VerificationError;
import com.oracle.graal.phases.tiers.HighTierContext;
import com.oracle.graal.phases.util.Providers;
import com.oracle.graal.phases.verify.VerifyDebugUsage;
import com.oracle.graal.runtime.RuntimeProvider;

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 *
 * Tests to verify that the usage of method metrics does not generate compile time overhead through
 * eager evaluation of arguments.
 */
public class VerifyMethodMetricsTest {

    private static class InvalidCCP_ToString01Inc extends Phase {
        @Override
        protected void run(StructuredGraph graph) {
            DebugMethodMetrics m = Debug.methodMetrics(graph.method());
            for (Node n : graph.getNodes()) {
                m.incrementMetric(n.toString());
            }
        }
    }

    private static class InvalidCCP_Concat01Inc extends Phase {
        @Override
        protected void run(StructuredGraph graph) {
            DebugMethodMetrics m = Debug.methodMetrics(graph.method());
            for (Node n : graph.getNodes()) {
                m.incrementMetric("a" + n.toString());
            }
        }
    }

    private static class InvalidCCP_ToString02Inc extends Phase {
        @Override
        protected void run(StructuredGraph graph) {
            DebugMethodMetrics m = Debug.methodMetrics(graph.method());
            for (Node n : graph.getNodes()) {
                m.incrementMetric("%s", n.toString());
            }
        }
    }

    private static class InvalidCCP_Concat02Inc extends Phase {
        private final String s = this.getClass().toGenericString();

        @Override
        protected void run(StructuredGraph graph) {
            DebugMethodMetrics m = Debug.methodMetrics(graph.method());
            for (Node n : graph.getNodes()) {
                m.incrementMetric("%s%s", "a" + s, n);
            }
        }
    }

    private static class ValidCCP_ToStringInc extends Phase {
        @Override
        protected void run(StructuredGraph graph) {
            DebugMethodMetrics m = Debug.methodMetrics(graph.method());
            for (Node n : graph.getNodes()) {
                m.addToMetric(1, "%s", n);
            }
        }
    }

    private static class ValidCCP_ConcatInc extends Phase {
        @Override
        protected void run(StructuredGraph graph) {
            DebugMethodMetrics m = Debug.methodMetrics(graph.method());
            for (Node n : graph.getNodes()) {
                m.incrementMetric("%s%s", "a", n);
            }
        }
    }

    private static class InvalidCCP_ToString01Add extends Phase {
        @Override
        protected void run(StructuredGraph graph) {
            DebugMethodMetrics m = Debug.methodMetrics(graph.method());
            for (Node n : graph.getNodes()) {
                m.addToMetric(1, n.toString());
            }
        }
    }

    private static class InvalidCCP_Concat01Add extends Phase {
        @Override
        protected void run(StructuredGraph graph) {
            DebugMethodMetrics m = Debug.methodMetrics(graph.method());
            for (Node n : graph.getNodes()) {
                m.addToMetric(1, "a" + n.toString());
            }
        }
    }

    private static class InvalidCCP_ToString02Add extends Phase {
        @Override
        protected void run(StructuredGraph graph) {
            DebugMethodMetrics m = Debug.methodMetrics(graph.method());
            for (Node n : graph.getNodes()) {
                m.addToMetric(1, "%s", n.toString());
            }
        }
    }

    private static class InvalidCCP_Concat02Add extends Phase {
        private final String s = this.getClass().toGenericString();

        @Override
        protected void run(StructuredGraph graph) {
            DebugMethodMetrics m = Debug.methodMetrics(graph.method());
            for (Node n : graph.getNodes()) {
                m.addToMetric(1, "%s%s", "a" + s, n);
            }
        }
    }

    private static class ValidCCP_ToStringAdd extends Phase {
        @Override
        protected void run(StructuredGraph graph) {
            DebugMethodMetrics m = Debug.methodMetrics(graph.method());
            for (Node n : graph.getNodes()) {
                m.addToMetric(1, "%s", n);
            }
        }
    }

    private static class ValidCCP_ConcatAdd extends Phase {
        @Override
        protected void run(StructuredGraph graph) {
            DebugMethodMetrics m = Debug.methodMetrics(graph.method());
            for (Node n : graph.getNodes()) {
                m.addToMetric(1, "%s%s", "a", n);
            }
        }
    }

    @Test(expected = VerificationError.class)
    public void testLogInvalidToString01Add() {
        testDebugUsageClass(InvalidCCP_ToString01Add.class);
    }

    @Test(expected = VerificationError.class)
    public void testLogInvalidConcat01Add() {
        testDebugUsageClass(InvalidCCP_Concat01Add.class);
    }

    @Test(expected = VerificationError.class)
    public void testLogInvalidToString02Add() {
        testDebugUsageClass(InvalidCCP_ToString02Add.class);
    }

    @Test(expected = VerificationError.class)
    public void testLogInvalidConcat02Add() {
        testDebugUsageClass(InvalidCCP_Concat02Add.class);
    }

    @Test
    public void testLogValidToStringAdd() {
        testDebugUsageClass(ValidCCP_ToStringAdd.class);
    }

    @Test
    public void testLogValidConcatAdd() {
        testDebugUsageClass(ValidCCP_ConcatAdd.class);
    }

    @Test(expected = VerificationError.class)
    public void testLogInvalidToString01Inc() {
        testDebugUsageClass(InvalidCCP_ToString01Inc.class);
    }

    @Test(expected = VerificationError.class)
    public void testLogInvalidConcat01Inc() {
        testDebugUsageClass(InvalidCCP_Concat01Inc.class);
    }

    @Test(expected = VerificationError.class)
    public void testLogInvalidToString02Inc() {
        testDebugUsageClass(InvalidCCP_ToString02Inc.class);
    }

    @Test(expected = VerificationError.class)
    public void testLogInvalidConcat02Inc() {
        testDebugUsageClass(InvalidCCP_Concat02Inc.class);
    }

    @Test
    public void testLogValidToStringInc() {
        testDebugUsageClass(ValidCCP_ToStringInc.class);
    }

    @Test
    public void testLogValidConcatInc() {
        testDebugUsageClass(ValidCCP_ConcatInc.class);
    }

    @SuppressWarnings("try")
    private static void testDebugUsageClass(Class<?> c) {
        RuntimeProvider rt = Graal.getRequiredCapability(RuntimeProvider.class);
        Providers providers = rt.getHostBackend().getProviders();
        MetaAccessProvider metaAccess = providers.getMetaAccess();
        PhaseSuite<HighTierContext> graphBuilderSuite = new PhaseSuite<>();
        GraphBuilderConfiguration config = GraphBuilderConfiguration.getEagerDefault(new Plugins(new InvocationPlugins(metaAccess)));
        graphBuilderSuite.appendPhase(new GraphBuilderPhase(config));
        HighTierContext context = new HighTierContext(providers, graphBuilderSuite, OptimisticOptimizations.NONE);
        for (Method m : c.getDeclaredMethods()) {
            if (!Modifier.isNative(m.getModifiers()) && !Modifier.isAbstract(m.getModifiers())) {
                ResolvedJavaMethod method = metaAccess.lookupJavaMethod(m);
                StructuredGraph graph = new StructuredGraph(method, AllowAssumptions.NO);
                graphBuilderSuite.apply(graph, context);
                try (DebugConfigScope s = Debug.disableIntercept()) {
                    new VerifyDebugUsage().apply(graph, context);
                }
            }
        }
    }
}
