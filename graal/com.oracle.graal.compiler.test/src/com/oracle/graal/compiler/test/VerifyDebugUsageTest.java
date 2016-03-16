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
package com.oracle.graal.compiler.test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.junit.Test;

import com.oracle.graal.api.test.Graal;
import com.oracle.graal.debug.Debug;
import com.oracle.graal.debug.Indent;
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

public class VerifyDebugUsageTest {

    private static class InvalidLogUsagePhase extends Phase {

        @Override
        protected void run(StructuredGraph graph) {
            for (Node n : graph.getNodes()) {
                Debug.log("%s", n.toString());
            }
        }

    }

    private static class InvalidLogAndIndentUsagePhase extends Phase {

        @Override
        @SuppressWarnings("try")
        protected void run(StructuredGraph graph) {
            try (Indent i = Debug.logAndIndent("%s", graph.toString())) {
                for (Node n : graph.getNodes()) {
                    Debug.log("%s", n);
                }
            }
        }

    }

    private static class InvalidDumpUsagePhase extends Phase {

        @Override
        protected void run(StructuredGraph graph) {
            Debug.dump(graph, "%s", graph.toString());
        }

    }

    private static class InvalidVerifyUsagePhase extends Phase {

        @Override
        protected void run(StructuredGraph graph) {
            Debug.verify(graph, "%s", graph.toString());
        }

    }

    private static class InvalidConcatLogUsagePhase extends Phase {

        @Override
        protected void run(StructuredGraph graph) {
            for (Node n : graph.getNodes()) {
                Debug.log("error " + n);
            }
        }

    }

    private static class InvalidConcatLogAndIndentUsagePhase extends Phase {

        @Override
        @SuppressWarnings("try")
        protected void run(StructuredGraph graph) {
            try (Indent i = Debug.logAndIndent("error " + graph)) {
                for (Node n : graph.getNodes()) {
                    Debug.log("%s", n);
                }
            }
        }

    }

    private static class InvalidConcatDumpUsagePhase extends Phase {

        @Override
        protected void run(StructuredGraph graph) {
            Debug.dump(graph, "error " + graph);
        }

    }

    private static class InvalidConcatVerifyUsagePhase extends Phase {

        @Override
        protected void run(StructuredGraph graph) {
            Debug.verify(graph, "error " + graph);
        }

    }

    private static class ValidLogUsagePhase extends Phase {

        @Override
        protected void run(StructuredGraph graph) {
            for (Node n : graph.getNodes()) {
                Debug.log("%s", n);
            }
        }

    }

    private static class ValidLogAndIndentUsagePhase extends Phase {

        @Override
        @SuppressWarnings("try")
        protected void run(StructuredGraph graph) {
            try (Indent i = Debug.logAndIndent("%s", graph)) {
                for (Node n : graph.getNodes()) {
                    Debug.log("%s", n);
                }
            }
        }

    }

    private static class ValidDumpUsagePhase extends Phase {

        @Override
        protected void run(StructuredGraph graph) {
            Debug.dump(graph, "%s", graph);
        }

    }

    private static class ValidVerifyUsagePhase extends Phase {

        @Override
        protected void run(StructuredGraph graph) {
            Debug.verify(graph, "%s", graph);
        }

    }

    @Test(expected = VerificationError.class)
    public void testLogInvalid() {
        testDebugUsageClass(InvalidLogUsagePhase.class);
    }

    @Test(expected = VerificationError.class)
    public void testLogAndIndentInvalid() {
        testDebugUsageClass(InvalidLogAndIndentUsagePhase.class);
    }

    @Test(expected = VerificationError.class)
    public void testVerifyInvalid() {
        testDebugUsageClass(InvalidVerifyUsagePhase.class);
    }

    @Test(expected = VerificationError.class)
    public void testDumpInvalid() {
        testDebugUsageClass(InvalidDumpUsagePhase.class);
    }

    @Test(expected = VerificationError.class)
    public void testLogInvalidConcat() {
        testDebugUsageClass(InvalidConcatLogUsagePhase.class);
    }

    @Test(expected = VerificationError.class)
    public void testLogAndIndentInvalidConcat() {
        testDebugUsageClass(InvalidConcatLogAndIndentUsagePhase.class);
    }

    @Test(expected = VerificationError.class)
    public void testVerifyInvalidConcat() {
        testDebugUsageClass(InvalidConcatVerifyUsagePhase.class);
    }

    @Test(expected = VerificationError.class)
    public void testDumpInvalidConcat() {
        testDebugUsageClass(InvalidConcatDumpUsagePhase.class);
    }

    @Test
    public void testLogValid() {
        testDebugUsageClass(ValidLogUsagePhase.class);
    }

    @Test()
    public void testLogAndIndentValid() {
        testDebugUsageClass(ValidLogAndIndentUsagePhase.class);
    }

    @Test
    public void testVerifyValid() {
        testDebugUsageClass(ValidVerifyUsagePhase.class);
    }

    @Test
    public void testDumpValid() {
        testDebugUsageClass(ValidDumpUsagePhase.class);
    }

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
                new VerifyDebugUsage().apply(graph, context);
            }
        }
    }
}
