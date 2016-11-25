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
package com.oracle.graal.hotspot.meta;

import static com.oracle.graal.compiler.common.CompilationIdentifier.INVALID_COMPILATION_ID;
import static com.oracle.graal.compiler.common.GraalOptions.ImmutableCode;
import static com.oracle.graal.compiler.common.GraalOptions.VerifyPhases;

import com.oracle.graal.hotspot.HotSpotBackend;
import com.oracle.graal.hotspot.HotSpotGraalRuntimeProvider;
import com.oracle.graal.hotspot.HotSpotInstructionProfiling;
import com.oracle.graal.hotspot.GraalHotSpotVMConfig;
import com.oracle.graal.hotspot.phases.AheadOfTimeVerificationPhase;
import com.oracle.graal.hotspot.phases.LoadJavaMirrorWithKlassPhase;
import com.oracle.graal.hotspot.phases.WriteBarrierAdditionPhase;
import com.oracle.graal.hotspot.phases.WriteBarrierVerificationPhase;
import com.oracle.graal.java.GraphBuilderPhase;
import com.oracle.graal.java.SuitesProviderBase;
import com.oracle.graal.lir.phases.LIRSuites;
import com.oracle.graal.nodes.EncodedGraph;
import com.oracle.graal.nodes.GraphEncoder;
import com.oracle.graal.nodes.SimplifyingGraphDecoder;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.StructuredGraph.AllowAssumptions;
import com.oracle.graal.nodes.graphbuilderconf.GraphBuilderConfiguration;
import com.oracle.graal.phases.BasePhase;
import com.oracle.graal.phases.PhaseSuite;
import com.oracle.graal.phases.common.AddressLoweringPhase;
import com.oracle.graal.phases.common.AddressLoweringPhase.AddressLowering;
import com.oracle.graal.phases.common.ExpandLogicPhase;
import com.oracle.graal.phases.tiers.HighTierContext;
import com.oracle.graal.phases.tiers.Suites;
import com.oracle.graal.phases.tiers.SuitesCreator;

/**
 * HotSpot implementation of {@link SuitesCreator}.
 */
public class HotSpotSuitesProvider extends SuitesProviderBase {

    protected final GraalHotSpotVMConfig config;
    protected final HotSpotGraalRuntimeProvider runtime;

    private final AddressLowering addressLowering;
    private final SuitesCreator defaultSuitesCreator;

    public HotSpotSuitesProvider(SuitesCreator defaultSuitesCreator, GraalHotSpotVMConfig config, HotSpotGraalRuntimeProvider runtime, AddressLowering addressLowering) {
        this.defaultSuitesCreator = defaultSuitesCreator;
        this.config = config;
        this.runtime = runtime;
        this.addressLowering = addressLowering;
        this.defaultGraphBuilderSuite = createGraphBuilderSuite();
    }

    @Override
    public Suites createSuites() {
        Suites ret = defaultSuitesCreator.createSuites();

        if (ImmutableCode.getValue()) {
            // lowering introduces class constants, therefore it must be after lowering
            ret.getHighTier().appendPhase(new LoadJavaMirrorWithKlassPhase(config.classMirrorOffset, config.useCompressedOops ? config.getOopEncoding() : null));
            if (VerifyPhases.getValue()) {
                ret.getHighTier().appendPhase(new AheadOfTimeVerificationPhase());
            }
        }

        ret.getMidTier().appendPhase(new WriteBarrierAdditionPhase(config));
        if (VerifyPhases.getValue()) {
            ret.getMidTier().appendPhase(new WriteBarrierVerificationPhase(config));
        }

        ret.getLowTier().findPhase(ExpandLogicPhase.class).add(new AddressLoweringPhase(addressLowering));

        return ret;
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
     * encoding itself}, i.e., performs a decoding without canoncialization and checks the graphs
     * for equality.
     */
    private boolean appendGraphEncoderTest(PhaseSuite<HighTierContext> suite) {
        suite.appendPhase(new BasePhase<HighTierContext>("VerifyEncodingDecoding") {
            @Override
            protected void run(StructuredGraph graph, HighTierContext context) {
                EncodedGraph encodedGraph = GraphEncoder.encodeSingleGraph(graph, runtime.getTarget().arch);

                SimplifyingGraphDecoder graphDecoder = new SimplifyingGraphDecoder(context.getMetaAccess(), context.getConstantReflection(), context.getConstantFieldProvider(),
                                context.getStampProvider(), !ImmutableCode.getValue(), runtime.getTarget().arch);
                StructuredGraph targetGraph = new StructuredGraph(graph.method(), AllowAssumptions.YES, INVALID_COMPILATION_ID);
                graphDecoder.decode(targetGraph, encodedGraph);
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
        GraphBuilderPhase newGraphBuilderPhase = new GraphBuilderPhase(graphBuilderConfig.withNodeSourcePosition(true));
        newGbs.findPhase(GraphBuilderPhase.class).set(newGraphBuilderPhase);
        return newGbs;
    }

    @Override
    public LIRSuites createLIRSuites() {
        LIRSuites suites = defaultSuitesCreator.createLIRSuites();
        String profileInstructions = HotSpotBackend.Options.ASMInstructionProfiling.getValue();
        if (profileInstructions != null) {
            suites.getPostAllocationOptimizationStage().appendPhase(new HotSpotInstructionProfiling(profileInstructions));
        }
        return suites;
    }
}
