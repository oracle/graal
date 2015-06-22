/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.compiler.common.GraalOptions.*;
import jdk.internal.jvmci.hotspot.*;
import jdk.internal.jvmci.options.*;
import jdk.internal.jvmci.options.DerivedOptionValue.*;

import com.oracle.graal.graphbuilderconf.*;
import com.oracle.graal.graphbuilderconf.GraphBuilderConfiguration.DebugInfoMode;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.phases.*;
import com.oracle.graal.java.*;
import com.oracle.graal.lir.phases.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.StructuredGraph.AllowAssumptions;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.common.*;
import com.oracle.graal.phases.common.AddressLoweringPhase.AddressLowering;
import com.oracle.graal.phases.tiers.*;

/**
 * HotSpot implementation of {@link SuitesProvider}.
 */
public class HotSpotSuitesProvider implements SuitesProvider {

    protected final DerivedOptionValue<Suites> defaultSuites;
    protected final PhaseSuite<HighTierContext> defaultGraphBuilderSuite;
    private final DerivedOptionValue<LIRSuites> defaultLIRSuites;
    protected final HotSpotGraalRuntimeProvider runtime;

    private final AddressLowering addressLowering;
    private final SuitesProvider defaultSuitesProvider;

    private class SuitesSupplier implements OptionSupplier<Suites> {

        private static final long serialVersionUID = -3444304453553320390L;

        public Suites get() {
            return createSuites();
        }

    }

    private class LIRSuitesSupplier implements OptionSupplier<LIRSuites> {

        private static final long serialVersionUID = -1558586374095874299L;

        public LIRSuites get() {
            return createLIRSuites();
        }

    }

    public HotSpotSuitesProvider(SuitesProvider defaultSuitesProvider, HotSpotGraalRuntimeProvider runtime, AddressLowering addressLowering) {
        this.runtime = runtime;
        this.addressLowering = addressLowering;
        this.defaultSuitesProvider = defaultSuitesProvider;
        this.defaultGraphBuilderSuite = createGraphBuilderSuite();
        this.defaultSuites = new DerivedOptionValue<>(new SuitesSupplier());
        this.defaultLIRSuites = new DerivedOptionValue<>(new LIRSuitesSupplier());
    }

    public Suites getDefaultSuites() {
        return defaultSuites.getValue();
    }

    public PhaseSuite<HighTierContext> getDefaultGraphBuilderSuite() {
        return defaultGraphBuilderSuite;
    }

    public Suites createSuites() {
        Suites ret = defaultSuitesProvider.createSuites();

        if (ImmutableCode.getValue()) {
            // lowering introduces class constants, therefore it must be after lowering
            ret.getHighTier().appendPhase(new LoadJavaMirrorWithKlassPhase(runtime.getConfig().classMirrorOffset, runtime.getConfig().useCompressedOops ? runtime.getConfig().getOopEncoding() : null));
            if (VerifyPhases.getValue()) {
                ret.getHighTier().appendPhase(new AheadOfTimeVerificationPhase());
            }
        }

        ret.getMidTier().appendPhase(new WriteBarrierAdditionPhase(runtime.getConfig()));
        if (VerifyPhases.getValue()) {
            ret.getMidTier().appendPhase(new WriteBarrierVerificationPhase());
        }

        ret.getLowTier().findPhase(ExpandLogicPhase.class).add(new AddressLoweringPhase(addressLowering));

        return ret;
    }

    protected PhaseSuite<HighTierContext> createGraphBuilderSuite() {
        PhaseSuite<HighTierContext> suite = defaultSuitesProvider.getDefaultGraphBuilderSuite().copy();
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

                SimplifyingGraphDecoder graphDecoder = new SimplifyingGraphDecoder(context.getMetaAccess(), context.getConstantReflection(), context.getStampProvider(), !ImmutableCode.getValue(),
                                runtime.getTarget().arch);
                StructuredGraph targetGraph = new StructuredGraph(graph.method(), AllowAssumptions.YES);
                graphDecoder.decode(targetGraph, encodedGraph);
            }
        });
        return true;
    }

    /**
     * Modifies the {@link GraphBuilderConfiguration} to build extra
     * {@linkplain DebugInfoMode#Simple debug info} if the VM
     * {@linkplain CompilerToVM#shouldDebugNonSafepoints() requests} it.
     *
     * @param gbs the current graph builder suite
     * @return a possibly modified graph builder suite
     */
    public static PhaseSuite<HighTierContext> withSimpleDebugInfoIfRequested(PhaseSuite<HighTierContext> gbs) {
        if (HotSpotGraalRuntime.runtime().getCompilerToVM().shouldDebugNonSafepoints()) {
            PhaseSuite<HighTierContext> newGbs = gbs.copy();
            GraphBuilderPhase graphBuilderPhase = (GraphBuilderPhase) newGbs.findPhase(GraphBuilderPhase.class).previous();
            GraphBuilderConfiguration graphBuilderConfig = graphBuilderPhase.getGraphBuilderConfig();
            GraphBuilderPhase newGraphBuilderPhase = new GraphBuilderPhase(graphBuilderConfig.withDebugInfoMode(DebugInfoMode.Simple));
            newGbs.findPhase(GraphBuilderPhase.class).set(newGraphBuilderPhase);
            return newGbs;
        }
        return gbs;
    }

    public LIRSuites getDefaultLIRSuites() {
        return defaultLIRSuites.getValue();
    }

    public LIRSuites createLIRSuites() {
        LIRSuites suites = defaultSuitesProvider.createLIRSuites();
        String profileInstructions = HotSpotBackend.Options.ASMInstructionProfiling.getValue();
        if (profileInstructions != null) {
            suites.getPostAllocationOptimizationStage().appendPhase(new HotSpotInstructionProfiling(profileInstructions));
        }
        return suites;
    }
}
