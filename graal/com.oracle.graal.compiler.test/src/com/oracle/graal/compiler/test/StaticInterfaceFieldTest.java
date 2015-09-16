/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.debug.DelegatingDebugConfig.Feature.INTERCEPT;

import java.lang.reflect.Method;

import jdk.internal.jvmci.meta.MetaAccessProvider;
import jdk.internal.jvmci.meta.ResolvedJavaMethod;

import org.junit.Assume;
import org.junit.Test;

import com.oracle.graal.api.runtime.Graal;
import com.oracle.graal.debug.Debug;
import com.oracle.graal.debug.DebugConfigScope;
import com.oracle.graal.debug.DelegatingDebugConfig;
import com.oracle.graal.graphbuilderconf.GraphBuilderConfiguration;
import com.oracle.graal.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import com.oracle.graal.graphbuilderconf.InvocationPlugins;
import com.oracle.graal.java.GraphBuilderPhase;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.StructuredGraph.AllowAssumptions;
import com.oracle.graal.phases.OptimisticOptimizations;
import com.oracle.graal.phases.PhaseSuite;
import com.oracle.graal.phases.VerifyPhase;
import com.oracle.graal.phases.tiers.HighTierContext;
import com.oracle.graal.phases.util.Providers;
import com.oracle.graal.runtime.RuntimeProvider;
import com.oracle.graal.test.GraalTest;

/**
 * Test that interfaces are correctly initialized by a static field resolution during eager graph
 * building.
 */
public class StaticInterfaceFieldTest extends GraalTest {

    private interface I {
        Object CONST = new Object() {
        };

    }

    private static final class C implements I {
        @SuppressWarnings({"static-method", "unused"})
        public Object test() {
            return CONST;
        }
    }

    @Test
    public void test() {
        eagerlyParseMethod(C.class, "test");

    }

    @SuppressWarnings("try")
    private void eagerlyParseMethod(Class<C> clazz, String methodName) {
        RuntimeProvider rt = Graal.getRequiredCapability(RuntimeProvider.class);
        Providers providers = rt.getHostBackend().getProviders();
        MetaAccessProvider metaAccess = providers.getMetaAccess();

        PhaseSuite<HighTierContext> graphBuilderSuite = new PhaseSuite<>();
        GraphBuilderConfiguration config = GraphBuilderConfiguration.getEagerDefault(new Plugins(new InvocationPlugins(metaAccess)));
        graphBuilderSuite.appendPhase(new GraphBuilderPhase(config));
        HighTierContext context = new HighTierContext(providers, graphBuilderSuite, OptimisticOptimizations.NONE);

        Assume.assumeTrue(VerifyPhase.class.desiredAssertionStatus());

        final Method m = getMethod(clazz, methodName);
        ResolvedJavaMethod method = metaAccess.lookupJavaMethod(m);
        StructuredGraph graph = new StructuredGraph(method, AllowAssumptions.NO);
        try (DebugConfigScope s = Debug.setConfig(new DelegatingDebugConfig().disable(INTERCEPT)); Debug.Scope ds = Debug.scope("GraphBuilding", graph, method)) {
            graphBuilderSuite.apply(graph, context);
        } catch (Throwable e) {
            throw Debug.handle(e);
        }
    }
}
