/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.compiler.graal.hotspot.test;

import static org.junit.Assume.assumeTrue;

import jdk.compiler.graal.debug.DebugContext;
import jdk.compiler.graal.hotspot.GraalHotSpotVMConfig;
import jdk.compiler.graal.hotspot.HotSpotGraalRuntime;
import jdk.compiler.graal.nodes.StructuredGraph;
import jdk.compiler.graal.nodes.StructuredGraph.AllowAssumptions;
import jdk.compiler.graal.nodes.gc.G1PostWriteBarrier;
import jdk.compiler.graal.nodes.gc.G1PreWriteBarrier;
import jdk.compiler.graal.nodes.gc.G1ReferentFieldReadBarrier;
import jdk.compiler.graal.nodes.gc.SerialWriteBarrier;
import jdk.compiler.graal.nodes.java.AbstractNewObjectNode;
import jdk.compiler.graal.options.OptionValues;
import jdk.compiler.graal.phases.OptimisticOptimizations;
import jdk.compiler.graal.phases.common.GuardLoweringPhase;
import jdk.compiler.graal.phases.common.HighTierLoweringPhase;
import jdk.compiler.graal.phases.common.MidTierLoweringPhase;
import jdk.compiler.graal.phases.common.WriteBarrierAdditionPhase;
import jdk.compiler.graal.phases.common.inlining.InliningPhase;
import jdk.compiler.graal.phases.common.inlining.policy.InlineEverythingPolicy;
import jdk.compiler.graal.phases.tiers.HighTierContext;
import jdk.compiler.graal.phases.tiers.MidTierContext;
import jdk.compiler.graal.virtual.phases.ea.PartialEscapePhase;
import org.junit.Assert;
import org.junit.Test;

import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * This tests that barriers which are deferrable because of ReduceInitialCardMarks are properly
 * omitted. The rule is simply that only writes to the very last allocated object can skip the card
 * mark. By creating references between objects only one write can skip the card mark and the other
 * must emit a card mark.
 */
@SuppressWarnings("this-escape")
public class DeferredBarrierAdditionTest extends HotSpotGraalCompilerTest {

    private final GraalHotSpotVMConfig config = runtime().getVMConfig();

    public static Object testCrossReferences() {
        Object[] a = new Object[1];
        Object[] b = new Object[1];
        a[0] = b;
        b[0] = a;
        return a;
    }

    @Test
    public void testGroupAllocation() throws Exception {
        testHelper("testCrossReferences", config.gc == HotSpotGraalRuntime.HotSpotGC.Z ? 0 : 1, getInitialOptions());
    }

    @SuppressWarnings("try")
    protected void testHelper(final String snippetName, final int expectedBarriers, OptionValues options) {
        ResolvedJavaMethod snippet = getResolvedJavaMethod(snippetName);
        DebugContext debug = getDebugContext(options, null, snippet);
        try (DebugContext.Scope s = debug.scope("WriteBarrierAdditionTest", snippet)) {
            StructuredGraph graph = parseEager(snippet, AllowAssumptions.NO, debug);
            HighTierContext highContext = getDefaultHighTierContext();
            MidTierContext midContext = new MidTierContext(getProviders(), getTargetProvider(), OptimisticOptimizations.ALL, graph.getProfilingInfo());
            new InliningPhase(new InlineEverythingPolicy(), createCanonicalizerPhase()).apply(graph, highContext);
            this.createCanonicalizerPhase().apply(graph, highContext);
            new PartialEscapePhase(false, createCanonicalizerPhase(), debug.getOptions()).apply(graph, highContext);
            new HighTierLoweringPhase(createCanonicalizerPhase()).apply(graph, highContext);
            new GuardLoweringPhase().apply(graph, midContext);
            new MidTierLoweringPhase(createCanonicalizerPhase()).apply(graph, midContext);
            graph.getGraphState().setAfterFSA();
            new WriteBarrierAdditionPhase().apply(graph, midContext);
            debug.dump(DebugContext.BASIC_LEVEL, graph, "After Write Barrier Addition");

            checkAssumptions(graph);

            int barriers = 0;
            if (config.useG1GC()) {
                barriers = graph.getNodes().filter(G1ReferentFieldReadBarrier.class).count() + graph.getNodes().filter(G1PreWriteBarrier.class).count() +
                                graph.getNodes().filter(G1PostWriteBarrier.class).count();
            } else {
                barriers = graph.getNodes().filter(SerialWriteBarrier.class).count();
            }
            if (expectedBarriers != barriers) {
                Assert.assertEquals(getScheduledGraphString(graph), expectedBarriers, barriers);
            }
        } catch (Throwable e) {
            throw debug.handle(e);
        }
    }

    protected void checkAssumptions(StructuredGraph graph) {
        assumeTrue(graph.getNodes().filter(AbstractNewObjectNode.class).isNotEmpty());
    }

}
