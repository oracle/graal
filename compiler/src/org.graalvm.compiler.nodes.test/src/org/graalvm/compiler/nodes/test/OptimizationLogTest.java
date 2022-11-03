/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes.test;

import java.util.Optional;
import java.util.function.Supplier;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.graalvm.compiler.core.common.GraalOptions;
import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.DebugOptions;
import org.graalvm.compiler.nodes.GraphState;
import org.graalvm.compiler.nodes.OptimizationLog;
import org.graalvm.compiler.nodes.OptimizationLogImpl;
import org.graalvm.compiler.nodes.ReturnNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.calc.AddNode;
import org.graalvm.compiler.nodes.spi.CoreProviders;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.phases.ClassTypeSequence;
import org.graalvm.compiler.phases.util.Providers;
import org.junit.Assert;
import org.junit.Test;

import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Tests the functionality of the {@link OptimizationLog}.
 */
public class OptimizationLogTest extends GraalCompilerTest {
    /**
     * A mock optimization phase that combines direct optimization reporting and the application of
     * child phases that report their own optimizations.
     */
    private static final class ReportNodePhase extends BasePhase<CoreProviders> {
        private final Supplier<Integer> barPropertySupplier;

        private ReportNodePhase(Supplier<Integer> valueSupplier) {
            this.barPropertySupplier = valueSupplier;
        }

        @Override
        public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
            return ALWAYS_APPLICABLE;
        }

        @Override
        protected void run(StructuredGraph graph, CoreProviders context) {
            graph.getOptimizationLog().withProperty("foo", 42).withLazyProperty("bar", barPropertySupplier).report(getClass(), "StartNodeReported", graph.start());
            new ReportAddNodePhase().apply(graph, context);
            new ReportReturnNodePhase().apply(graph, context);
        }
    }

    private static final class ReportAddNodePhase extends BasePhase<CoreProviders> {
        @Override
        public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
            return ALWAYS_APPLICABLE;
        }

        @Override
        protected void run(StructuredGraph graph, CoreProviders context) {
            for (AddNode addNode : graph.getNodes().filter(AddNode.class)) {
                graph.getOptimizationLog().report(getClass(), "AddNodeReported", addNode);
            }
        }
    }

    private static final class ReportReturnNodePhase extends BasePhase<CoreProviders> {
        @Override
        public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
            return ALWAYS_APPLICABLE;
        }

        @Override
        protected void run(StructuredGraph graph, CoreProviders context) {
            for (ReturnNode returnNode : graph.getNodes().filter(ReturnNode.class)) {
                graph.getOptimizationLog().report(getClass(), "ReturnNodeReported", returnNode);
            }
        }
    }

    public static int addSnippet(int x, int y) {
        return x + y;
    }

    /**
     * Tests that the optimization tree is correctly built when the mock {@link ReportNodePhase} is
     * applied to the graph of {@link #addSnippet(int, int)}.
     */
    @Test
    public void buildOptimizationTreeWhenEnabled() {
        StructuredGraph graph = parseGraph("addSnippet", true);
        OptimizationLog optimizationLog = graph.getOptimizationLog();
        Assert.assertTrue(optimizationLog.isOptimizationLogEnabled());

        graph.getDebug().setCompilationListener(optimizationLog);
        new ReportNodePhase(() -> 43).apply(graph, getProviders());

        OptimizationLogImpl.OptimizationPhaseScope rootPhase = graph.getOptimizationLog().getCurrentPhase();
        Assert.assertEquals("RootPhase", rootPhase.getPhaseName());

        OptimizationLogImpl.OptimizationPhaseScope reportNodePhaseScope = (OptimizationLogImpl.OptimizationPhaseScope) rootPhase.getChildren().get(0);
        Assert.assertEquals(new ClassTypeSequence(ReportNodePhase.class).toString(), reportNodePhaseScope.getPhaseName().toString());
        Assert.assertEquals(3, reportNodePhaseScope.getChildren().count());

        OptimizationLogImpl.OptimizationEntryImpl startNodeLog = (OptimizationLogImpl.OptimizationEntryImpl) reportNodePhaseScope.getChildren().get(0);
        Assert.assertEquals("ReportNode", startNodeLog.getOptimizationName());
        Assert.assertEquals("StartNodeReported", startNodeLog.getEventName());
        Assert.assertEquals(EconomicMap.of("foo", 42, "bar", 43).toString(), startNodeLog.getProperties().toString());

        OptimizationLogImpl.OptimizationPhaseScope reportAddNodePhase = (OptimizationLogImpl.OptimizationPhaseScope) reportNodePhaseScope.getChildren().get(1);
        Assert.assertEquals(new ClassTypeSequence(ReportAddNodePhase.class).toString(), reportAddNodePhase.getPhaseName().toString());
        Assert.assertEquals(1, reportAddNodePhase.getChildren().count());
        OptimizationLogImpl.OptimizationEntryImpl addNodeLog = (OptimizationLogImpl.OptimizationEntryImpl) reportAddNodePhase.getChildren().get(0);
        Assert.assertEquals("AddNodeReported", addNodeLog.getEventName());

        OptimizationLogImpl.OptimizationPhaseScope reportReturnNodePhase = (OptimizationLogImpl.OptimizationPhaseScope) reportNodePhaseScope.getChildren().get(2);
        Assert.assertEquals(new ClassTypeSequence(ReportReturnNodePhase.class).toString(), reportReturnNodePhase.getPhaseName().toString());
        Assert.assertEquals(1, reportReturnNodePhase.getChildren().count());
        OptimizationLogImpl.OptimizationEntryImpl returnNodeLog = (OptimizationLogImpl.OptimizationEntryImpl) reportReturnNodePhase.getChildren().get(0);
        Assert.assertEquals("ReturnNodeReported", returnNodeLog.getEventName());
    }

    /**
     * Tests that there is no reporting going on when the optimization log is disabled. In
     * particular, the optimization tree shall not be built and
     * {@link org.graalvm.compiler.nodes.OptimizationLog.OptimizationEntry#withLazyProperty(String, Supplier)
     * lazy properties} shall not be consumed.
     */
    @Test
    public void noReportingWhenDisabled() {
        StructuredGraph graph = parseGraph("addSnippet", false);
        OptimizationLog optimizationLog = graph.getOptimizationLog();
        Assert.assertFalse(optimizationLog.isOptimizationLogEnabled());

        Providers providers = getProviders();
        ReportNodePhase reportNodePhase = new ReportNodePhase(() -> {
            Assert.fail("The property supplier should not be executed.");
            return null;
        });
        reportNodePhase.apply(graph, providers);

        Assert.assertNull(optimizationLog.getOptimizationTree());
        Assert.assertNull(optimizationLog.getCurrentPhase());
    }

    /**
     * Parses the graph {@link #parseEager eagerly} and potentially enables the optimization log.
     * Always enables {@link GraalOptions#TrackNodeSourcePosition node source position tracking}.
     *
     * @param methodName the name of the method to be parsed
     * @param enableOptimizationLog whether the {@link DebugOptions#OptimizationLog} should be
     *            enabled
     * @return the parsed graph
     */
    private StructuredGraph parseGraph(String methodName, boolean enableOptimizationLog) {
        ResolvedJavaMethod method = getResolvedJavaMethod(methodName);
        EconomicMap<OptionKey<?>, Object> extraOptions = EconomicMap.create();
        EconomicSet<DebugOptions.OptimizationLogTarget> optimizationLogTargets = EconomicSet.create();
        if (enableOptimizationLog) {
            optimizationLogTargets.add(DebugOptions.OptimizationLogTarget.Stdout);
        }
        extraOptions.put(DebugOptions.OptimizationLog, optimizationLogTargets);
        extraOptions.put(GraalOptions.TrackNodeSourcePosition, true);
        OptionValues options = new OptionValues(getInitialOptions(), extraOptions);
        DebugContext debugContext = getDebugContext(options, null, method);
        StructuredGraph.Builder builder = new StructuredGraph.Builder(options, debugContext, null).method(method).compilationId(getCompilationId(method));
        return parse(builder, getEagerGraphBuilderSuite());
    }
}
