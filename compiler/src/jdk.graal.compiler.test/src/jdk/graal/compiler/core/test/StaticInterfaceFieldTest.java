/*
 * Copyright (c) 2015, 2020, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core.test;

import java.lang.reflect.Method;

import org.junit.Assume;
import org.junit.Test;

import jdk.graal.compiler.api.test.Graal;
import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.DebugContext.Builder;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.OptimisticOptimizations;
import jdk.graal.compiler.phases.PhaseSuite;
import jdk.graal.compiler.phases.VerifyPhase;
import jdk.graal.compiler.phases.tiers.HighTierContext;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.runtime.RuntimeProvider;
import jdk.graal.compiler.test.GraalTest;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;

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
        Plugins plugins = new Plugins(new InvocationPlugins());
        GraphBuilderConfiguration config = GraphBuilderConfiguration.getDefault(plugins).withEagerResolving(true).withUnresolvedIsError(true);
        graphBuilderSuite.appendPhase(new TestGraphBuilderPhase(config));
        HighTierContext context = new HighTierContext(providers, graphBuilderSuite, OptimisticOptimizations.NONE);

        Assume.assumeTrue(VerifyPhase.class.desiredAssertionStatus());

        final Method m = getMethod(clazz, methodName);
        ResolvedJavaMethod method = metaAccess.lookupJavaMethod(m);
        OptionValues options = GraalCompilerTest.getInitialOptions();
        DebugContext debug = new Builder(options).build();
        StructuredGraph graph = new StructuredGraph.Builder(options, debug).method(method).build();
        try (DebugCloseable s = debug.disableIntercept(); DebugContext.Scope ds = debug.scope("GraphBuilding", graph, method)) {
            graphBuilderSuite.apply(graph, context);
        } catch (Throwable e) {
            throw debug.handle(e);
        }
    }
}
