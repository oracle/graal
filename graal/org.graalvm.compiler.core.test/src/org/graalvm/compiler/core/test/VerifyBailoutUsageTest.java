/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.compiler.common.CompilationIdentifier.INVALID_COMPILATION_ID;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.junit.Test;

import com.oracle.graal.api.test.Graal;
import com.oracle.graal.common.PermanentBailoutException;
import com.oracle.graal.common.RetryableBailoutException;
import com.oracle.graal.debug.Debug;
import com.oracle.graal.debug.DebugConfigScope;
import com.oracle.graal.debug.GraalError;
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
import com.oracle.graal.phases.verify.VerifyBailoutUsage;
import com.oracle.graal.runtime.RuntimeProvider;

import jdk.vm.ci.code.BailoutException;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class VerifyBailoutUsageTest {

    private static class InvalidBailoutUsagePhase1 extends Phase {
        @Override
        protected void run(StructuredGraph graph) {
            throw new BailoutException("Bailout in graph %s", graph);
        }
    }

    private static class InvalidBailoutUsagePhase2 extends Phase {
        @Override
        protected void run(StructuredGraph graph) {
            throw new BailoutException(new GraalError("other cause"), "Bailout in graph %s", graph);
        }
    }

    private static class InvalidBailoutUsagePhase3 extends Phase {
        @Override
        protected void run(StructuredGraph graph) {
            throw new BailoutException(true/* permanent */, "Bailout in graph %s", graph);
        }
    }

    private static class ValidPermanentBailoutUsage extends Phase {
        @Override
        protected void run(StructuredGraph graph) {
            throw new PermanentBailoutException("Valid permanent bailout %s", graph);
        }
    }

    private static class ValidRetryableBailoutUsage extends Phase {
        @Override
        protected void run(StructuredGraph graph) {
            throw new RetryableBailoutException("Valid retryable bailout %s", graph);
        }
    }

    @Test(expected = VerificationError.class)
    public void testInvalidBailout01() {
        testBailoutUsage(InvalidBailoutUsagePhase1.class);
    }

    @Test(expected = VerificationError.class)
    public void testInvalidBailout02() {
        testBailoutUsage(InvalidBailoutUsagePhase2.class);
    }

    @Test(expected = VerificationError.class)
    public void testInvalidBailout03() {
        testBailoutUsage(InvalidBailoutUsagePhase3.class);
    }

    @Test
    public void testValidPermanentBailout() {
        testBailoutUsage(ValidPermanentBailoutUsage.class);
    }

    @Test
    public void testValidRetryableBailout() {
        testBailoutUsage(ValidRetryableBailoutUsage.class);
    }

    @SuppressWarnings("try")
    private static void testBailoutUsage(Class<?> c) {
        RuntimeProvider rt = Graal.getRequiredCapability(RuntimeProvider.class);
        Providers providers = rt.getHostBackend().getProviders();
        MetaAccessProvider metaAccess = providers.getMetaAccess();
        PhaseSuite<HighTierContext> graphBuilderSuite = new PhaseSuite<>();
        Plugins plugins = new Plugins(new InvocationPlugins(metaAccess));
        GraphBuilderConfiguration config = GraphBuilderConfiguration.getDefault(plugins).withEagerResolving(true);
        graphBuilderSuite.appendPhase(new GraphBuilderPhase(config));
        HighTierContext context = new HighTierContext(providers, graphBuilderSuite, OptimisticOptimizations.NONE);
        for (Method m : c.getDeclaredMethods()) {
            if (!Modifier.isNative(m.getModifiers()) && !Modifier.isAbstract(m.getModifiers())) {
                ResolvedJavaMethod method = metaAccess.lookupJavaMethod(m);
                StructuredGraph graph = new StructuredGraph(method, AllowAssumptions.NO, INVALID_COMPILATION_ID);
                graphBuilderSuite.apply(graph, context);
                try (DebugConfigScope s = Debug.disableIntercept()) {
                    new VerifyBailoutUsage().apply(graph, context);
                }
            }
        }
    }
}
