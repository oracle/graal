/*
 * Copyright (c) 2015, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.meta;

import java.util.ListIterator;
import java.util.Optional;

import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.hotspot.GraalHotSpotVMConfig;
import jdk.graal.compiler.hotspot.HotSpotGraalRuntime;
import jdk.graal.compiler.hotspot.HotSpotGraalRuntimeProvider;
import jdk.graal.compiler.hotspot.HotSpotGraphBuilderPhase;
import jdk.graal.compiler.hotspot.lir.HotSpotZapRegistersPhase;
import jdk.graal.compiler.hotspot.lir.VerifyMaxRegisterSizePhase;
import jdk.graal.compiler.java.GraphBuilderPhase;
import jdk.graal.compiler.java.SuitesProviderBase;
import jdk.graal.compiler.lir.phases.LIRSuites;
import jdk.graal.compiler.nodes.EncodedGraph;
import jdk.graal.compiler.nodes.GraphEncoder;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.SimplifyingGraphDecoder;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.PhaseSuite;
import jdk.graal.compiler.phases.common.AddressLoweringPhase;
import jdk.graal.compiler.phases.common.BarrierSetVerificationPhase;
import jdk.graal.compiler.phases.common.UseTrappingNullChecksPhase;
import jdk.graal.compiler.phases.common.WriteBarrierAdditionPhase;
import jdk.graal.compiler.phases.tiers.HighTierContext;
import jdk.graal.compiler.phases.tiers.LowTierContext;
import jdk.graal.compiler.phases.tiers.MidTierContext;
import jdk.graal.compiler.phases.tiers.Suites;
import jdk.graal.compiler.phases.tiers.SuitesCreator;
import jdk.vm.ci.code.Architecture;

/**
 * HotSpot implementation of {@link SuitesCreator}.
 */
public class HotSpotSuitesProvider extends SuitesProviderBase {

    protected final GraalHotSpotVMConfig config;
    protected final HotSpotGraalRuntimeProvider runtime;

    protected final SuitesCreator defaultSuitesCreator;

    @SuppressWarnings("this-escape")
    public HotSpotSuitesProvider(SuitesCreator defaultSuitesCreator, GraalHotSpotVMConfig config, HotSpotGraalRuntimeProvider runtime) {
        this.defaultSuitesCreator = defaultSuitesCreator;
        this.config = config;
        this.runtime = runtime;
        this.defaultGraphBuilderSuite = createGraphBuilderSuite();
    }

    @Override
    public Suites createSuites(OptionValues options, Architecture arch) {
        Suites suites = defaultSuitesCreator.createSuites(options, arch);
        if (runtime.getTarget().implicitNullCheckLimit > 0 && !runtime.getCompilerConfigurationName().equalsIgnoreCase("economy")) {
            ListIterator<BasePhase<? super LowTierContext>> position = suites.getLowTier().findPhase(AddressLoweringPhase.class);
            assert position != null : "There should be an " + AddressLoweringPhase.class.getName() + " in low tier.";
            position.previous();
            position.add(new UseTrappingNullChecksPhase());
        }

        if (config.gc == HotSpotGraalRuntime.HotSpotGC.X) {
            ListIterator<BasePhase<? super MidTierContext>> mid = suites.getMidTier().findPhase(WriteBarrierAdditionPhase.class);
            // No write barriers required
            mid.remove();

            if (Assertions.assertionsEnabled()) {
                // Perform some verification that the barrier type on all reads are properly set
                ListIterator<BasePhase<? super LowTierContext>> position = suites.getLowTier().findPhase(AddressLoweringPhase.class);
                position.previous();
                position.add(new BarrierSetVerificationPhase());
            }
        }
        return suites;
    }

    protected PhaseSuite<HighTierContext> createGraphBuilderSuite() {
        PhaseSuite<HighTierContext> suite = defaultSuitesCreator.getDefaultGraphBuilderSuite().copy();
        assert appendGraphEncoderTest(suite);
        return suite;
    }

    /**
     * When assertions are enabled, we encode and decode every parsed graph, to ensure that the
     * encoding and decoding process work correctly. The decoding performs canonicalization during
     * decoding, so the decoded graph can be different than the encoded graph - we cannot check them
     * for equality here. However, the encoder {@link GraphEncoder#verifyEncoding verifies the
     * encoding itself}, i.e., performs a decoding without canonicalization and checks the graphs
     * for equality.
     */
    private boolean appendGraphEncoderTest(PhaseSuite<HighTierContext> suite) {
        if (config.xcompMode) {
            // Do not do this in -Xcomp mode. It adds too much compilation time.
            // Testing coverage is provided by Graal unit testing instead.
            return true;
        }
        suite.appendPhase(new BasePhase<HighTierContext>() {
            @Override
            public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
                return ALWAYS_APPLICABLE;
            }

            @Override
            protected void run(StructuredGraph graph, HighTierContext context) {
                EncodedGraph encodedGraph = GraphEncoder.encodeSingleGraph(graph, runtime.getTarget().arch);

                StructuredGraph targetGraph = new StructuredGraph.Builder(graph.getOptions(), graph.getDebug(), AllowAssumptions.YES).method(graph.method()).trackNodeSourcePosition(
                                graph.trackNodeSourcePosition()).profileProvider(graph.getProfileProvider()).build();
                SimplifyingGraphDecoder graphDecoder = new SimplifyingGraphDecoder(runtime.getTarget().arch, targetGraph, context, true);
                graphDecoder.decode(encodedGraph);
            }

            @Override
            public CharSequence getName() {
                return "VerifyEncodingDecodingPhase";
            }
        });
        return true;
    }

    /**
     * Modifies a given {@link GraphBuilderConfiguration} to record per node source information.
     *
     * @param gbs the current graph builder suite to modify
     */
    public static PhaseSuite<HighTierContext> withNodeSourcePosition(PhaseSuite<HighTierContext> gbs) {
        PhaseSuite<HighTierContext> newGbs = gbs.copy();
        GraphBuilderPhase graphBuilderPhase = (GraphBuilderPhase) newGbs.findPhase(GraphBuilderPhase.class).previous();
        GraphBuilderConfiguration graphBuilderConfig = graphBuilderPhase.getGraphBuilderConfig();
        GraphBuilderPhase newGraphBuilderPhase = new HotSpotGraphBuilderPhase(graphBuilderConfig.withNodeSourcePosition(true));
        newGbs.findPhase(GraphBuilderPhase.class).set(newGraphBuilderPhase);
        return newGbs;
    }

    @Override
    public LIRSuites createLIRSuites(OptionValues options) {
        LIRSuites suites = defaultSuitesCreator.createLIRSuites(options);
        if (Assertions.detailedAssertionsEnabled(options)) {
            suites.getPostAllocationOptimizationStage().appendPhase(new HotSpotZapRegistersPhase());
        }
        // MaxVectorSize < 16 is used by HotSpot to disable vectorization but that
        // doesn't work reliably with Graal because it assumes XMM registers are always
        // available. For now just skip the verification for this case.
        if (Assertions.assertionsEnabled() && config.maxVectorSize >= 16) {
            suites.getFinalCodeAnalysisStage().appendPhase(new VerifyMaxRegisterSizePhase(config.maxVectorSize));
        }
        return suites;
    }

}
