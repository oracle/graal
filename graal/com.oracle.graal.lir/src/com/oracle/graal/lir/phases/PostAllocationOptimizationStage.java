/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.lir.phases;

import static com.oracle.graal.lir.phases.LIRPhase.Options.*;

import com.oracle.graal.lir.*;
import com.oracle.graal.lir.phases.PostAllocationOptimizationPhase.PostAllocationOptimizationContext;
import com.oracle.graal.lir.profiling.*;
import com.oracle.graal.options.*;

public class PostAllocationOptimizationStage extends LIRPhaseSuite<PostAllocationOptimizationContext> {
    public static class Options {
        // @formatter:off
        @Option(help = "", type = OptionType.Debug)
        public static final NestedBooleanOptionValue LIROptEdgeMoveOptimizer = new NestedBooleanOptionValue(LIROptimization, true);
        @Option(help = "", type = OptionType.Debug)
        public static final NestedBooleanOptionValue LIROptControlFlowOptmizer = new NestedBooleanOptionValue(LIROptimization, true);
        @Option(help = "", type = OptionType.Debug)
        public static final NestedBooleanOptionValue LIROptRedundantMoveElimination = new NestedBooleanOptionValue(LIROptimization, true);
        @Option(help = "", type = OptionType.Debug)
        public static final NestedBooleanOptionValue LIROptNullCheckOptimizer = new NestedBooleanOptionValue(LIROptimization, true);
        @Option(help = "", type = OptionType.Debug)
        public static final OptionValue<Boolean> LIROptMoveProfiling = new OptionValue<>(false);
        // @formatter:on
    }

    public PostAllocationOptimizationStage() {
        if (Options.LIROptEdgeMoveOptimizer.getValue()) {
            appendPhase(new EdgeMoveOptimizer());
        }
        if (Options.LIROptControlFlowOptmizer.getValue()) {
            appendPhase(new ControlFlowOptimizer());
        }
        if (Options.LIROptRedundantMoveElimination.getValue()) {
            appendPhase(new RedundantMoveElimination());
        }
        if (Options.LIROptNullCheckOptimizer.getValue()) {
            appendPhase(new NullCheckOptimizer());
        }
        if (Options.LIROptMoveProfiling.getValue()) {
            appendPhase(new MoveProfiling());
        }
    }
}
