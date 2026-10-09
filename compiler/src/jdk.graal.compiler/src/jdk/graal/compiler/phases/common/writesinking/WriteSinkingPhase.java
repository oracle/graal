/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

package jdk.graal.compiler.phases.common.writesinking;

import java.util.Optional;

import jdk.graal.compiler.phases.common.writesinking.writesink.LoopWriteSinker;

import jdk.graal.compiler.debug.MethodFilter;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.GraphState.StageFlag;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.loop.LoopsData;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionType;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.vector.phases.RemoveEmptyLoopsPhase;

/**
 * Low-tier write sinking for loop-carried stores whose intermediate values are not observable
 * inside the loop.
 * <p>
 * The phase runs after reads have been fixed, so memory reads are represented by fixed nodes in the
 * graph and can directly invalidate movable write-sinking candidates. It still runs before write
 * barriers are inserted, so moving a write preserves the original write's barrier metadata instead
 * of moving already-expanded barrier code.
 * <p>
 * Write sinking first analyzes loop bodies to find stores that can be represented by a movable
 * write state at loop exits. The rewrite pass then removes those stores from the loop body and
 * commits equivalent writes at the exits where the store's value becomes observable. Loops that may
 * execute zero times use temporary conditional writes; later canonicalization lowers those
 * conditional writes to ordinary control flow.
 * <p>
 * See {@link LoopSinkAnalyser} for the legality analysis and {@link LoopWriteSinker} for the graph
 * rewrite.
 */
public class WriteSinkingPhase extends BasePhase<CoreProviders> {
    private final CanonicalizerPhase emptyLoopCanonicalizer;

    /**
     * Options controlling low-tier write sinking.
     */
    public static final class Options {
        // @formatter:off
        /**
         * Debug filter for field locations that should not be write-sunk.
         */
        @Option(help = "Exclude certain fields from write sinking. Fields are specified with the same syntax " +
                "as method filters, minus the signature part.", type = OptionType.Debug)
        public static final OptionKey<String> WriteSinkingExcludeFields = new OptionKey<>(null);
        /**
         * Optional CSV file for per-method/per-loop rejection details. The file name is processed
         * by {@link jdk.graal.compiler.debug.GlobalMetrics#generateFileName(String)}, so {@code %p}
         * is replaced by the execution id and isolate suffixes are added when needed.
         */
        @Option(help = "Write per-method/per-loop write-sinking rejection details to this CSV file. " +
                "The filename may contain %p, matching the Graal metrics filename convention.", type = OptionType.Debug)
        public static final OptionKey<String> WriteSinkingDetailsCSV = new OptionKey<>(null);
        // @formatter:on
    }

    /**
     * Creates a write-sinking phase that only sinks writes. This constructor is used by tests that
     * inspect the transient graph shape immediately after write sinking.
     */
    public WriteSinkingPhase() {
        this(null);
    }

    /**
     * Creates a production write-sinking phase that also removes loops made empty by write sinking.
     */
    public WriteSinkingPhase(CanonicalizerPhase emptyLoopCanonicalizer) {
        this.emptyLoopCanonicalizer = emptyLoopCanonicalizer;
    }

    /**
     * Write sinking requires fixed reads so observable read dependencies are present in the fixed
     * graph, but it must still run before low-tier write barrier insertion. Mid-tier write barrier
     * insertion is deliberately not a cutoff: low-tier write sinking normally runs after mid tier,
     * and the illegal case is moving writes after low-tier barrier nodes have been expanded.
     */
    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return NotApplicable.ifAny(
                        NotApplicable.unlessRunAfter(this, StageFlag.FIXED_READS, graphState),
                        NotApplicable.ifApplied(this, StageFlag.LOW_TIER_BARRIER_ADDITION, graphState),
                        NotApplicable.ifApplied(this, StageFlag.ADDRESS_LOWERING, graphState),
                        NotApplicable.ifApplied(this, StageFlag.FINAL_CANONICALIZATION, graphState));
    }

    @Override
    protected void run(StructuredGraph graph, CoreProviders context) {
        if (!graph.hasLoops()) {
            WriteSinkingDiagnostics.recordProcessedGraph(graph.getDebug(), 0);
            return;
        }

        ControlFlowGraph cfg = ControlFlowGraph.newBuilder(graph).connectBlocks(true).computeLoops(true).computeFrequency(true).build();
        WriteSinkingDiagnostics.recordProcessedGraph(graph.getDebug(), cfg.getLoops().size());
        final LoopsData loopsData = context.getLoopsDataProvider().getLoopsData(cfg);

        String excludeFieldsSpec = Options.WriteSinkingExcludeFields.getValue(graph.getOptions());
        MethodFilter excludeFieldsFilter = null;
        if (excludeFieldsSpec != null) {
            excludeFieldsFilter = MethodFilter.parse(excludeFieldsSpec);
        }

        String detailsCSV = Options.WriteSinkingDetailsCSV.getValue(graph.getOptions());
        LoopWriteSinker.Result result = LoopWriteSinker.apply(context, cfg, loopsData, excludeFieldsFilter, detailsCSV);
        if (result.changed()) {
            if (emptyLoopCanonicalizer != null) {
                new RemoveEmptyLoopsPhase(emptyLoopCanonicalizer).apply(graph, context);
            }
        }
    }

    /**
     * Allows write sinking to duplicate writes on loop exits while still keeping phase-size
     * accounting bounded.
     */
    @Override
    public float codeSizeIncrease() {
        /*
         * Some legitimate applications of the write sinking phase cause noteworthy code size
         * increase. E.g. consider a loop with many exit/end nodes and/or deoptimization points. If
         * a write sinks out of such a loop, it must be duplicated as (conditional) write for all
         * such points.
         *
         * Using such a high value (instead of fully disabling the size check) allows detecting
         * extreme outliers.
         */
        return 10;
    }

}
