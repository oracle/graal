/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.graal.compiler.core.test.VerifyAssertionUsage.ALL_PATHS_MUST_ASSERT_PROPERTY_NAME;
import static jdk.graal.compiler.core.test.VerifyAssertionUsage.ENABLE_BOOLEAN_ASSERTION_CONDITION_CHEKING;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import jdk.graal.compiler.api.test.Graal;
import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.DebugContext.Builder;
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
import jdk.graal.compiler.phases.util.GraphOrder;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.runtime.RuntimeProvider;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class VerifyAssertionUsageTest {

    static boolean oldPathsMustAssert;
    static boolean oldCheckBooleanConditions;

    @BeforeClass
    public static void beforeClass() {
        oldPathsMustAssert = Boolean.getBoolean(ALL_PATHS_MUST_ASSERT_PROPERTY_NAME);

        oldCheckBooleanConditions = Boolean.getBoolean(ENABLE_BOOLEAN_ASSERTION_CONDITION_CHEKING);
        /**
         * For testing purposes we are more strict by forcing that all paths used in callees in
         * assertion calls actually assert something. See
         * {@link VerifyAssertionUsage#allPathsMustAssert}.
         */
        System.setProperty(ALL_PATHS_MUST_ASSERT_PROPERTY_NAME, "true");

        /**
         * Will be fixed as a follow up.
         */
        System.setProperty(ENABLE_BOOLEAN_ASSERTION_CONDITION_CHEKING, "true");
    }

    @AfterClass
    public static void afterClass() {
        System.setProperty(ALL_PATHS_MUST_ASSERT_PROPERTY_NAME, String.valueOf(oldPathsMustAssert));
        System.setProperty(ENABLE_BOOLEAN_ASSERTION_CONDITION_CHEKING, String.valueOf(oldCheckBooleanConditions));
    }

    /**
     * Valid call with an assertion message.
     */
    private static final class ValidAssertUsage1 extends TestPhase {
        @Override
        protected void run(StructuredGraph graph) {
            assert graph.hasLoops() : "Must have loops";
        }
    }

    /**
     * Valid call with a trivial condition. Null check is trivial in that if it fails the error is
     * "clear" to spot by inspecting the code.
     */
    private static final class ValidAssertUsage2 extends TestPhase {
        @Override
        protected void run(StructuredGraph graph) {
            assert graph != null;
        }
    }

    /**
     * Valid usage of known assertion methods that are always safe to call.
     */
    private static final class ValidAssertUsage3 extends TestPhase {
        @Override
        protected void run(StructuredGraph graph) {
            assert GraphOrder.assertSchedulableGraph(graph);
            assert NumUtil.assertNonNegativeInt(graph.getNodeCount());
        }
    }

    /**
     * Invalid assertion call: missing assertion message.
     */
    private static final class InvalidAssertUsage extends TestPhase {
        @Override
        protected void run(StructuredGraph graph) {
            assert graph.hasLoops();
        }
    }

    /**
     * Valid usage of assertion methods down the call graph: run is missing a message on call
     * further but call further eventually calls a method with an assertion error message so the
     * assertion usage is correct several levels down the call graph.
     */
    private static final class ValidCallGraphUsage1 extends TestPhase {
        @Override
        protected void run(StructuredGraph graph) {
            assert verifyGraph(graph);
            assert callFurther(graph);
        }

        private static boolean verifyGraph(StructuredGraph graph) {
            assert graph.hasLoops() : "Graph must have loops";
            return true;
        }

        // second level
        private static boolean callFurther(StructuredGraph g) {
            assert callFurther1(g);
            return true;
        }

        // third level
        private static boolean callFurther1(StructuredGraph g) {
            assert verifyGraph(g);
            return true;
        }
    }

    /**
     * Invalid call: call further is missing the assertion message and run calls callFurther so this
     * is a missing assertion message 1 level down the call graph.
     */
    private static final class InvalidCallGraphUsage1 extends TestPhase {
        @Override
        protected void run(StructuredGraph graph) {
            assert callFurther(graph);
        }

        private static boolean verifyGraph(StructuredGraph graph) {
            assert graph.hasLoops();
            return true;
        }

        // second level
        private static boolean callFurther(StructuredGraph g) {
            assert verifyGraph(g);
            return true;
        }
    }

    /**
     * Invalid call graph usage: multiple calls to methods without an error message a second level
     * down the call graph.
     */
    private static final class InvalidCallGraphUsage2 extends TestPhase {
        @Override
        protected void run(StructuredGraph graph) {
            assert callFurther(graph);
            assert callFurther(graph);
            assert callFurther(graph);
        }

        private static boolean verifyGraph(StructuredGraph graph) {
            assert graph.hasLoops();
            return true;
        }

        // second level
        private static boolean callFurther(StructuredGraph g) {
            assert verifyGraph(g);
            return true;
        }
    }

    /**
     * Invalid assertion usage: the usage of callFurther in run with the error message is
     * superfluous: callFurther does assertion checking and will always return true - thus the error
     * message in run is never used.
     */
    private static final class InvalidCallGraphUsage3 extends TestPhase {
        @Override
        protected void run(StructuredGraph graph) {
            assert callFurther(graph) : "Invalid superfluous message";
            assert callFurther(graph);
        }

        private static boolean verifyGraph(StructuredGraph graph) {
            assert graph.hasLoops() : "Normal message";
            return true;
        }

        // second level
        private static boolean callFurther(StructuredGraph g) {
            assert callFurther1(g); // callee, fine
            return true;
        }

        // third level
        private static boolean callFurther1(StructuredGraph g) {
            assert verifyGraph(g); // callee, fine
            return true;
        }
    }

    /**
     * Same as {@link InvalidCallGraphUsage3} but without multiple levels.
     */
    private static final class InvalidCallGraphUsage31 extends TestPhase {
        @Override
        protected void run(StructuredGraph graph) {
            assert verifyGraph(graph) : "Invalid superfluous message";
        }

        private static boolean verifyGraph(StructuredGraph graph) {
            assert graph.hasLoops() : "Normal message";
            return true;
        }

    }

    /**
     * Invalid assertion usage: somewhere down the call graph a method is used in an assertion that
     * does not return true on all paths and thus may lead to assertion errors without a message in
     * the caller. Specifically assertSth has a return false path that would fire the error in the
     * caller run without a message.
     */
    private static final class InvalidCallGraphUsage4 extends TestPhase {
        @Override
        protected void run(StructuredGraph graph) {
            assert assertSth(graph);
            assert assertSth1(graph);
        }

        // invalid assertion method because some paths return false
        private static boolean assertSth(StructuredGraph g) {
            assert g.hasUnsafeAccess() : "ABCD";
            if (g.hasLoops()) {
                return false;
            }
            return true;
        }

        private static boolean assertSth1(StructuredGraph g) {
            if (g.hasLoops()) {
                return true;
            }
            assert g.hasUnsafeAccess() : "ABCD";
            return true;
        }
    }

    /**
     * Invalid assertion usage: the path doSth() does not assert anything.
     */
    private static final class InvalidCallGraphUsage5 extends TestPhase {
        @Override
        protected void run(StructuredGraph graph) {
            assert assertSth(graph);
        }

        private static boolean assertSth(@SuppressWarnings("unused") StructuredGraph g) {
            doSth();
            return true;
        }

        static void doSth() {
            // some code
        }

    }

    /**
     * Valid assertion usage: though we call methods that do not have assertion checks on all paths
     * - the paths where they are missing them is loop iterations so those are fine. They still
     * return {@code true} on all paths.
     */
    private static final class ValidCallGraphUsage5 extends TestPhase {
        @Override
        protected void run(StructuredGraph graph) {
            assert assertSth(graph);
            assert assertSth1(graph);
            assert assertSth2(graph);
        }

        private static boolean assertSth(StructuredGraph g) {
            for (Node n : g.getNodes()) {
                assert n != null;
            }
            return true;
        }

        private static boolean assertSth1(StructuredGraph g) {
            for (int i = 0; i < g.getNodeCount(); i++) {
                assert g.getNode(0) != null;
            }
            return true;
        }

        private static boolean assertSth2(StructuredGraph g) {
            for (int i = 0; i < 3; i++) {
                assert g.getNode(0) != null;
            }
            return true;
        }

    }

    /**
     * Correct assertion usage: calling another method not via an assert but regularly where that
     * method itself calls an assertion.
     */
    private static final class ValidCallGraphUsage6 extends TestPhase {
        @Override
        protected void run(StructuredGraph graph) {
            assert assertSth(graph);
        }

        private static boolean assertSth(StructuredGraph g) {
            doSth(g);
            return true;
        }

        static void doSth(StructuredGraph g) {
            assert GraphOrder.assertNonCyclicGraph(g);
        }

    }

    @Test
    public void testAssertValid1() {
        testDebugUsageClass(ValidAssertUsage1.class);
    }

    @Test
    public void testAssertValid2() {
        testDebugUsageClass(ValidAssertUsage2.class);
    }

    @Test
    public void testAssertValid3() {
        testDebugUsageClass(ValidAssertUsage3.class);
    }

    @Test(expected = VerificationError.class)
    public void testAssertInvalid() {
        testDebugUsageClass(InvalidAssertUsage.class);
    }

    @Test
    public void testAssertValidCallGraph1() {
        testDebugUsageClass(ValidCallGraphUsage1.class);
    }

    @Test
    public void testValidCallGraph5() {
        testDebugUsageClass(ValidCallGraphUsage5.class);
    }

    @Test
    public void testValidCallGraph6() {
        testDebugUsageClass(ValidCallGraphUsage6.class);
    }

    @Test(expected = VerificationError.class)
    public void testAssertInvalidCallGraph1() {
        testDebugUsageClass(InvalidCallGraphUsage1.class);
    }

    @Test(expected = VerificationError.class)
    public void testAssertInvalidCallGraph2() {
        testDebugUsageClass(InvalidCallGraphUsage2.class);
    }

    @Test(expected = VerificationError.class)
    public void testAssertInvalidCallGraph3() {
        testDebugUsageClass(InvalidCallGraphUsage3.class);
    }

    @Test(expected = VerificationError.class)
    public void testAssertInvalidCallGraph31() {
        testDebugUsageClass(InvalidCallGraphUsage31.class);
    }

    @Test(expected = VerificationError.class)
    public void testAssertInvalidCallGraph4() {
        testDebugUsageClass(InvalidCallGraphUsage4.class);
    }

    @Test(expected = VerificationError.class)
    public void testAssertInvalidCallGraph5() {
        testDebugUsageClass(InvalidCallGraphUsage5.class);
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
        OptionValues options = GraalCompilerTest.getInitialOptions();
        DebugContext debug = new Builder(options).build();
        VerifyAssertionUsage vaU = new VerifyAssertionUsage(metaAccess);
        for (Method m : c.getDeclaredMethods()) {
            if (!Modifier.isNative(m.getModifiers()) && !Modifier.isAbstract(m.getModifiers())) {
                ResolvedJavaMethod method = metaAccess.lookupJavaMethod(m);
                StructuredGraph graph = new StructuredGraph.Builder(options, debug).method(method).build();
                graphBuilderSuite.apply(graph, context);
                try (DebugCloseable _ = debug.disableIntercept()) {
                    vaU.apply(graph, context);
                }
            }
        }
        vaU.postProcess();
    }

}
