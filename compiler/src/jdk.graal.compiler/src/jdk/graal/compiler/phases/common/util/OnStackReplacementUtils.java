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
package jdk.graal.compiler.phases.common.util;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

import jdk.graal.compiler.core.common.cfg.CFGLoop;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.loop.phases.LoopTransformations;
import jdk.graal.compiler.nodes.EntryMarkerNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.cfg.HIRBlock;
import jdk.graal.compiler.nodes.loop.Loop;
import jdk.graal.compiler.nodes.loop.LoopsData;
import jdk.graal.compiler.nodes.spi.LoopsDataProvider;
import jdk.graal.compiler.nodes.util.GraphUtil;

/**
 * Shared graph-shaping helpers for OSR phases.
 */
public final class OnStackReplacementUtils {

    /**
     * Peels containing loops until the OSR entry marker is no longer inside a loop.
     *
     * Graph building emits an {@link EntryMarkerNode} at the requested OSR bytecode index. If that
     * marker is still inside a loop, OSR needs one peeled iteration so the graph has a legal entry
     * point immediately before the loop body. This helper performs only the shared loop exposure
     * mechanics; each runtime-specific OSR phase still owns start-node replacement and live-state
     * materialization.
     *
     * @param graph graph being rewritten
     * @param loopsDataProvider provider used to recompute loop data after each peel
     * @param entryMarkerSupplier returns the unique current entry marker and preserves caller-specific
     *            error handling
     * @param prePeelCheck called before each peel on the current loop
     * @param excessivePeelingHandler called if peeling exceeds the original loop depth
     * @param debugDumpDescription optional dump label after each peel, or {@code null} to skip dumps
     * @return the entry marker that is outside all containing loops
     */
    public static EntryMarkerNode peelEntryLoops(StructuredGraph graph, LoopsDataProvider loopsDataProvider, Supplier<EntryMarkerNode> entryMarkerSupplier,
                    Consumer<Loop> prePeelCheck, BiConsumer<Integer, Integer> excessivePeelingHandler, String debugDumpDescription) {
        int maxIterations = -1;
        int iterations = 0;
        do {
            EntryMarkerNode osr = entryMarkerSupplier.get();
            LoopsData loops = loopsDataProvider.getLoopsData(graph);
            CFGLoop<HIRBlock> cfgLoop = loops.getCFG().getNodeToBlock().get(osr).getLoop();
            if (cfgLoop == null) {
                return osr;
            }
            iterations++;
            if (maxIterations == -1) {
                maxIterations = cfgLoop.getDepth();
            } else if (iterations > maxIterations) {
                excessivePeelingHandler.accept(iterations, maxIterations);
            }

            Loop loop = loops.loop(cfgLoop.getOutmostLoop());
            prePeelCheck.accept(loop);
            loop.loopBegin().markOsrLoop();
            LoopTransformations.peel(loop);

            osr.prepareDelete();
            GraphUtil.removeFixedWithUnusedInputs(osr);
            if (debugDumpDescription != null) {
                graph.getDebug().dump(DebugContext.DETAILED_LEVEL, graph, debugDumpDescription);
            }
        } while (true); // TERMINATION ARGUMENT: bounded by the original loop depth.
    }

    /**
     * Convenience overload for phases that do not need a loop-specific peel precheck.
     */
    public static EntryMarkerNode peelEntryLoops(StructuredGraph graph, LoopsDataProvider loopsDataProvider, Supplier<EntryMarkerNode> entryMarkerSupplier,
                    BiConsumer<Integer, Integer> excessivePeelingHandler, String debugDumpDescription) {
        return peelEntryLoops(graph, loopsDataProvider, entryMarkerSupplier, loop -> {
        }, excessivePeelingHandler, debugDumpDescription);
    }

}
