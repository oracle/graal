/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.compiler.graal.truffle.nodes;

import static jdk.compiler.graal.nodeinfo.NodeCycles.CYCLES_0;
import static jdk.compiler.graal.nodeinfo.NodeSize.SIZE_0;

import jdk.compiler.graal.core.common.type.StampFactory;
import jdk.compiler.graal.graph.Node;
import jdk.compiler.graal.graph.NodeClass;
import jdk.compiler.graal.nodes.spi.Canonicalizable;
import jdk.compiler.graal.nodes.spi.CanonicalizerTool;
import jdk.compiler.graal.nodeinfo.NodeInfo;
import jdk.compiler.graal.nodes.DeoptimizeNode;
import jdk.compiler.graal.nodes.FixedWithNextNode;
import jdk.compiler.graal.truffle.PartialEvaluator;

import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.SpeculationLog;
import jdk.vm.ci.meta.SpeculationLog.Speculation;
import jdk.vm.ci.meta.SpeculationLog.SpeculationReason;

/**
 * A speculation-less node that is inserted into the exception branch of TruffleBoundary calls
 * during parsing (graph encoding). During partial evaluation (graph decoding) when a speculation
 * log is available, it will speculate that TruffleBoundary method will not throw and either becomes
 * a control-flow sink {@link DeoptimizeNode} with the {@link Speculation} in order to off the
 * branch, or if the speculation has already failed for this compilation root, disappears.
 */
@NodeInfo(cycles = CYCLES_0, size = SIZE_0)
public final class SpeculativeExceptionAnchorNode extends FixedWithNextNode implements Canonicalizable {

    public static final NodeClass<SpeculativeExceptionAnchorNode> TYPE = NodeClass.create(SpeculativeExceptionAnchorNode.class);

    private final DeoptimizationReason reason;
    private final DeoptimizationAction action;
    private final ResolvedJavaMethod targetMethod;

    public SpeculativeExceptionAnchorNode(DeoptimizationReason reason, DeoptimizationAction action, ResolvedJavaMethod targetMethod) {
        super(TYPE, StampFactory.forVoid());
        this.reason = reason;
        this.action = action;
        this.targetMethod = targetMethod;
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        SpeculationLog speculationLog = graph().getSpeculationLog();
        if (speculationLog != null) {
            SpeculationReason speculationReason = PartialEvaluator.createTruffleBoundaryExceptionSpeculation(targetMethod);
            if (speculationLog.maySpeculate(speculationReason)) {
                Speculation exceptionSpeculation = speculationLog.speculate(speculationReason);
                return new DeoptimizeNode(action, reason, exceptionSpeculation);
            }
            return null;
        }
        return this;
    }
}
