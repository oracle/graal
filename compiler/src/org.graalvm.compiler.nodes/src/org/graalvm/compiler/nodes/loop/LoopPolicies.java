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
package org.graalvm.compiler.nodes.loop;

import java.util.List;

import org.graalvm.compiler.nodes.ControlSplitNode;
import org.graalvm.compiler.nodes.cfg.ControlFlowGraph;
import org.graalvm.compiler.nodes.spi.CoreProviders;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionType;

public interface LoopPolicies {

    class Options {
        @Option(help = "", type = OptionType.Expert) public static final OptionKey<Boolean> PeelALot = new OptionKey<>(false);
    }

    boolean shouldPeel(LoopEx loop, ControlFlowGraph cfg, CoreProviders providers);

    boolean shouldFullUnroll(LoopEx loop);

    boolean shouldPartiallyUnroll(LoopEx loop, CoreProviders providers);

    boolean shouldTryUnswitch(LoopEx loop);

    enum UnswitchingDecision {
        /** This loop should be unswitched. */
        YES,
        /**
         * This loop should be unswitched, and the unswitch is considered trivial. Trivial
         * unswitches are not counted towards the loop's total unswitch count.
         */
        TRIVIAL,
        /** This loop should not be unswitched. */
        NO;

        public boolean shouldUnswitch() {
            return this == YES || this == TRIVIAL;
        }

        public boolean isTrivial() {
            return this == TRIVIAL;
        }
    }

    UnswitchingDecision shouldUnswitch(LoopEx loop, List<ControlSplitNode> controlSplits);
}
