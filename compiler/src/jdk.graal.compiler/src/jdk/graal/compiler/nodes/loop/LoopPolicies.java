/*
 * Copyright (c) 2015, 2021, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.nodes.loop;

import java.util.List;

import jdk.graal.compiler.nodes.ControlSplitNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import org.graalvm.collections.EconomicMap;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionType;

public interface LoopPolicies {

    class Options {
        @Option(help = "Stress test for the loop peeling optimization by applying it aggressively", type = OptionType.Debug) public static final OptionKey<Boolean> PeelALot = new OptionKey<>(false);
        @Option(help = "Peel only the loop with the specific loop begin node ID for debugging purposes", type = OptionType.Debug) public static final OptionKey<Integer> PeelOnlyLoopWithNodeID = new OptionKey<>(
                        -1);
    }

    boolean shouldPeel(LoopEx loop, ControlFlowGraph cfg, CoreProviders providers, int peelingIteration);

    boolean shouldFullUnroll(LoopEx loop);

    boolean shouldPartiallyUnroll(LoopEx loop, CoreProviders providers);

    boolean shouldTryUnswitch(LoopEx loop);

    /**
     * Models the decision of {@code LoopPolicies::shouldUnswitch}. A decision can be considered
     * trivial. Trivial unswitches are not counted towards the loop's total unswitch count.
     */
    final class UnswitchingDecision {
        public static final UnswitchingDecision NO = new UnswitchingDecision(null, false);

        public static UnswitchingDecision trivial(List<ControlSplitNode> controlSplits) {
            return new UnswitchingDecision(controlSplits, true);
        }

        /**
         * Build a positive unswitching decision. The given control split nodes cannot be none.
         */
        public static UnswitchingDecision yes(List<ControlSplitNode> controlSplits) {
            assert controlSplits != null;
            return new UnswitchingDecision(controlSplits, false);
        }

        private final List<ControlSplitNode> controlSplits;
        private final boolean isTrivial;

        private UnswitchingDecision(List<ControlSplitNode> controlSplits, boolean isTrivial) {
            assert !isTrivial || controlSplits != null : "An unswitching desision cannot be trivial but have not control split node";

            this.controlSplits = controlSplits;
            this.isTrivial = isTrivial;
        }

        public boolean shouldUnswitch() {
            return this.controlSplits != null;
        }

        public boolean isTrivial() {
            return this.isTrivial;
        }

        /**
         * The control split nodes to unswitch.
         *
         * @return the list of control split nodes, {@code null} if {@code shouldUnswitch()} returns
         *         {@code false}.
         */
        public List<ControlSplitNode> getControlSplits() {
            return this.controlSplits;
        }
    }

    /**
     * Decide which control split invariant should be unswitched in the given loop.
     *
     * @param loop the loop to unswitch.
     * @param controlSplits the invariant grouped by their condition.
     * @return the decision to unswitch or not.
     */
    UnswitchingDecision shouldUnswitch(LoopEx loop, EconomicMap<ValueNode, List<ControlSplitNode>> controlSplits);
}
