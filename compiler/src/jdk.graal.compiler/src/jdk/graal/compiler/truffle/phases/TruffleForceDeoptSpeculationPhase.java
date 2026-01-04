/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.truffle.phases;

import com.oracle.truffle.compiler.TruffleCompilable;

import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.phases.common.ForceDeoptSpeculationPhase;
import jdk.graal.compiler.truffle.TruffleCompilation;
import jdk.graal.compiler.truffle.TruffleCompilerOptions;

public class TruffleForceDeoptSpeculationPhase extends ForceDeoptSpeculationPhase {
    public TruffleForceDeoptSpeculationPhase() {
        super(0);
    }

    @Override
    protected void run(StructuredGraph graph, CoreProviders context) {
        TruffleCompilable truffleCompilable = TruffleCompilation.lookupCompilable(graph);
        int deoptCycleDetectionThreshold = TruffleCompilerOptions.DeoptCycleDetectionThreshold.getValue(graph.getOptions());
        if (deoptCycleDetectionThreshold >= 0 && truffleCompilable.getSuccessfulCompilationCount() >= deoptCycleDetectionThreshold) {
            super.run(graph, context);
        }
    }

    @Override
    protected int getMaximumDeoptCount(StructuredGraph graph) {
        return Math.max(0, TruffleCompilerOptions.DeoptCycleDetectionAllowedRepeats.getValue(graph.getOptions())) + 1;
    }

    @Override
    protected GraalError reportTooManySpeculationFailures(ValueNode deopt) {
        StackTraceElement[] elements = GraphUtil.approxSourceStackTraceElement(deopt);
        String additionalMessage = elements.length != 0 ? ""
                        : " The node source position of the deoptimization is not available. " +
                                        "In a native image, this usually means the image was not built with -H:+IncludeNodeSourcePositions, " +
                                        "otherwise the missing source position typically indicates a compiler bug. Please file an issue.";
        throw GraphUtil.createBailoutException(
                        "Deopt taken too many times. Deopt Node: " + deopt + ". This could indicate a deopt cycle, which typically hints at a bug in the language implementation or Truffle." +
                                        additionalMessage,
                        null, elements);
    }
}
