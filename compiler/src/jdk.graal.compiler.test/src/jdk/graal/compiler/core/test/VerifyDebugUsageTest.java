/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.graal.compiler.core.test.GraalCompilerTest.getInitialOptions;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.junit.Test;

import jdk.graal.compiler.api.test.Graal;
import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.DebugContext.Builder;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.debug.Indent;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.OptimisticOptimizations;
import jdk.graal.compiler.phases.PhaseSuite;
import jdk.graal.compiler.phases.VerifyPhase.VerificationError;
import jdk.graal.compiler.phases.tiers.HighTierContext;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.runtime.RuntimeProvider;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class VerifyDebugUsageTest {

    private static final class InvalidLogUsagePhase extends TestPhase {
        @Override
        protected void run(StructuredGraph graph) {
            DebugContext debug = graph.getDebug();
            for (Node n : graph.getNodes()) {
                debug.log("%s", n.toString());
            }
        }

    }

    private static final class InvalidLogAndIndentUsagePhase extends TestPhase {
        @Override
        protected void run(StructuredGraph graph) {
            DebugContext debug = graph.getDebug();
            try (Indent _ = debug.logAndIndent("%s", graph.toString())) {
                for (Node n : graph.getNodes()) {
                    debug.log("%s", n);
                }
            }
        }

    }

    static class InvalidDumpUsagePhase extends TestPhase {
        @Override
        protected void run(StructuredGraph graph) {
            DebugContext debug = graph.getDebug();
            debug.dump(DebugContext.BASIC_LEVEL, graph, "%s", graph.toString());
        }
    }

    static class InvalidDumpLevelPhase extends TestPhase {
        @Override
        protected void run(StructuredGraph graph) {
            DebugContext debug = graph.getDebug();
            debug.dump(DebugContext.VERY_DETAILED_LEVEL + 1, graph, "%s", graph);
        }
    }

    static class NonConstantDumpLevelPhase extends TestPhase {
        @Override
        protected void run(StructuredGraph graph) {
            DebugContext debug = graph.getDebug();
            debug.dump(getLevel(), graph, "%s", graph);
        }

        int getLevel() {
            return 10;
        }
    }

    private static final class InvalidConcatLogUsagePhase extends TestPhase {
        @Override
        protected void run(StructuredGraph graph) {
            DebugContext debug = graph.getDebug();
            for (Node n : graph.getNodes()) {
                debug.log("error " + n);
            }
        }

    }

    private static final class InvalidConcatLogAndIndentUsagePhase extends TestPhase {
        @Override
        protected void run(StructuredGraph graph) {
            DebugContext debug = graph.getDebug();
            try (Indent _ = debug.logAndIndent("error " + graph)) {
                for (Node n : graph.getNodes()) {
                    debug.log("%s", n);
                }
            }
        }

    }

    static class InvalidConcatDumpUsagePhase extends TestPhase {
        @Override
        protected void run(StructuredGraph graph) {
            DebugContext debug = graph.getDebug();
            debug.dump(DebugContext.BASIC_LEVEL, graph, "error " + graph);
        }

    }

    static class ValidLogUsagePhase extends TestPhase {
        @Override
        protected void run(StructuredGraph graph) {
            DebugContext debug = graph.getDebug();
            for (Node n : graph.getNodes()) {
                debug.log("%s", n);
            }
        }

    }

    static class ValidLogAndIndentUsagePhase extends TestPhase {
        @Override
        protected void run(StructuredGraph graph) {
            DebugContext debug = graph.getDebug();
            try (Indent _ = debug.logAndIndent("%s", graph)) {
                for (Node n : graph.getNodes()) {
                    debug.log("%s", n);
                }
            }
        }

    }

    static class ValidDumpUsagePhase extends TestPhase {
        @Override
        protected void run(StructuredGraph graph) {
            DebugContext debug = graph.getDebug();
            debug.dump(DebugContext.BASIC_LEVEL, graph, "%s", graph);
        }

    }

    static class InvalidGraalErrorGuaranteePhase extends TestPhase {
        @Override
        protected void run(StructuredGraph graph) {
            GraalError.guarantee(graph.getNodes().count() > 0, "Graph must contain nodes %s %s %s", graph, graph, graph.toString());
        }
    }

    static class ValidGraalErrorGuaranteePhase extends TestPhase {
        @Override
        protected void run(StructuredGraph graph) {
            GraalError.guarantee(graph.getNodes().count() > 0, "Graph must contain nodes %s", graph);
        }
    }

    public static Object sideEffect;

    private static final class InvalidGraalErrorCtorPhase extends TestPhase {
        @Override
        protected void run(StructuredGraph graph) {
            sideEffect = new GraalError("No Error %s", graph.toString());
        }
    }

    private static final class ValidGraalErrorCtorPhase extends TestPhase {
        @Override
        protected void run(StructuredGraph graph) {
            sideEffect = new GraalError("Error %s", graph);
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
    public void testDumpInvalid() {
        testDebugUsageClass(InvalidDumpUsagePhase.class);
    }

    @Test(expected = VerificationError.class)
    public void testDumpLevelInvalid() {
        testDebugUsageClass(InvalidDumpLevelPhase.class);
    }

    @Test(expected = VerificationError.class)
    public void testDumpNonConstantLevelInvalid() {
        testDebugUsageClass(NonConstantDumpLevelPhase.class);
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
    public void testDumpValid() {
        testDebugUsageClass(ValidDumpUsagePhase.class);
    }

    @Test(expected = VerificationError.class)
    public void testGraalGuaranteeInvalid() {
        testDebugUsageClass(InvalidGraalErrorGuaranteePhase.class);
    }

    @Test
    public void testGraalGuaranteeValid() {
        testDebugUsageClass(ValidGraalErrorGuaranteePhase.class);
    }

    @Test(expected = VerificationError.class)
    public void testGraalCtorInvalid() {
        testDebugUsageClass(InvalidGraalErrorCtorPhase.class);
    }

    @Test
    public void testGraalCtorValid() {
        testDebugUsageClass(ValidGraalErrorCtorPhase.class);
    }

    private static void testDebugUsageClass(Class<?> c) {
        RuntimeProvider rt = Graal.getRequiredCapability(RuntimeProvider.class);
        Providers providers = rt.getHostBackend().getProviders();
        MetaAccessProvider metaAccess = providers.getMetaAccess();
        PhaseSuite<HighTierContext> graphBuilderSuite = new PhaseSuite<>();
        Plugins plugins = new Plugins(new InvocationPlugins());
        GraphBuilderConfiguration config = GraphBuilderConfiguration.getDefault(plugins).withEagerResolving(true).withUnresolvedIsError(true);
        graphBuilderSuite.appendPhase(new TestGraphBuilderPhase(config));
        HighTierContext context = new HighTierContext(providers, graphBuilderSuite, OptimisticOptimizations.NONE);
        OptionValues options = getInitialOptions();
        DebugContext debug = new Builder(options).build();
        for (Method m : c.getDeclaredMethods()) {
            if (!Modifier.isNative(m.getModifiers()) && !Modifier.isAbstract(m.getModifiers())) {
                ResolvedJavaMethod method = metaAccess.lookupJavaMethod(m);
                StructuredGraph graph = new StructuredGraph.Builder(options, debug).method(method).build();
                graphBuilderSuite.apply(graph, context);
                try (DebugCloseable _ = debug.disableIntercept()) {
                    new VerifyDebugUsage().apply(graph, context);
                }
            }
        }
    }
}
