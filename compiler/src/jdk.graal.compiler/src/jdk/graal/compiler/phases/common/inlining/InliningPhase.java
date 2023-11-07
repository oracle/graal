/*
 * Copyright (c) 2011, 2018, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.phases.common.inlining;

import java.util.LinkedList;

import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionType;
import jdk.graal.compiler.phases.common.AbstractInliningPhase;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.inlining.policy.InliningPolicy;
import jdk.graal.compiler.phases.common.inlining.walker.InliningData;
import jdk.graal.compiler.phases.tiers.HighTierContext;

public class InliningPhase extends AbstractInliningPhase {

    public static class Options {

        @Option(help = "Unconditionally inline intrinsics", type = OptionType.Debug)//
        public static final OptionKey<Boolean> AlwaysInlineIntrinsics = new OptionKey<>(false);

        /**
         * This is a defensive measure against known pathologies of the inliner where the breadth of
         * the inlining call tree exploration can be wide enough to prevent inlining from completing
         * in reasonable time.
         */
        @Option(help = "Per-compilation method inlining exploration limit before giving up (use 0 to disable)", type = OptionType.Debug)//
        public static final OptionKey<Integer> MethodInlineBailoutLimit = new OptionKey<>(5000);
    }

    private final InliningPolicy inliningPolicy;
    private final CanonicalizerPhase canonicalizer;
    private final LinkedList<Invoke> rootInvokes;

    private final int maxMethodPerInlining = Integer.MAX_VALUE;

    public InliningPhase(InliningPolicy policy, CanonicalizerPhase canonicalizer) {
        this(policy, canonicalizer, null);
    }

    public InliningPhase(InliningPolicy policy, CanonicalizerPhase canonicalizer, LinkedList<Invoke> rootInvokes) {
        this.inliningPolicy = policy;
        this.canonicalizer = canonicalizer;
        this.rootInvokes = rootInvokes;
    }

    public CanonicalizerPhase getCanonicalizer() {
        return canonicalizer;
    }

    @Override
    public float codeSizeIncrease() {
        return 10_000f;
    }

    /**
     *
     * This method sets in motion the inlining machinery.
     *
     * @see InliningData
     * @see InliningData#moveForward()
     *
     */
    @Override
    protected void run(final StructuredGraph graph, final HighTierContext context) {
        final InliningData data = new InliningData(graph, context, maxMethodPerInlining, canonicalizer, inliningPolicy, rootInvokes);

        int count = 0;
        assert data.repOK();
        int limit = Options.MethodInlineBailoutLimit.getValue(graph.getOptions());
        while (data.hasUnprocessedGraphs()) {
            boolean wasInlined = data.moveForward();
            assert data.repOK();
            count++;
            if (!wasInlined) {
                if (limit > 0 && count == limit) {
                    // Limit the amount of exploration which is done
                    break;
                }
            }
        }

        assert data.inliningDepth() == 0 || count == limit : data.inliningDepth() + " " + count + " " + limit;
        assert data.graphCount() == 0 || count == limit : data.graphCount() + " " + count + " " + limit;
    }

}
