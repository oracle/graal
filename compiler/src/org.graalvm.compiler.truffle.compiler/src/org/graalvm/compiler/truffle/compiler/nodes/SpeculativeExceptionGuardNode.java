/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.compiler.nodes;

import static org.graalvm.compiler.nodeinfo.InputType.Guard;
import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_1;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_1;

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.Canonicalizable;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;
import org.graalvm.compiler.graph.spi.SimplifierTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.AbstractFixedGuardNode;
import org.graalvm.compiler.nodes.DeoptimizeNode;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.truffle.compiler.PartialEvaluator;

import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.SpeculationLog;
import jdk.vm.ci.meta.SpeculationLog.Speculation;
import jdk.vm.ci.meta.SpeculationLog.SpeculationReason;

/**
 * A speculation-less guard node that is inserted into the exception branch of TruffleBoundary calls
 * during parsing (graph encoding). During partial evaluation (graph decoding) when a speculation
 * log is available, it will speculate that TruffleBoundary method will not throw and either becomes
 * a control-flow sink {@link DeoptimizeNode} with the {@link Speculation} in order to off the
 * branch, or if the speculation has already failed for this compilation root, disappears.
 */
@NodeInfo(nameTemplate = "SpeculativeExceptionGuard {p#reason/s}", allowedUsageTypes = Guard, cycles = CYCLES_1, size = SIZE_1)
public final class SpeculativeExceptionGuardNode extends AbstractFixedGuardNode implements Canonicalizable {

    public static final NodeClass<SpeculativeExceptionGuardNode> TYPE = NodeClass.create(SpeculativeExceptionGuardNode.class);

    protected ResolvedJavaMethod targetMethod;

    public SpeculativeExceptionGuardNode(LogicNode condition, DeoptimizationReason reason, DeoptimizationAction action, SpeculationLog.Speculation speculation, boolean negated,
                    ResolvedJavaMethod targetMethod) {
        super(TYPE, condition, reason, action, speculation, negated);
        this.targetMethod = targetMethod;
    }

    @Override
    public void simplify(SimplifierTool tool) {
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        SpeculationLog speculationLog = graph().getSpeculationLog();
        if (speculationLog != null) {
            SpeculationReason speculationReason = PartialEvaluator.createTruffleBoundaryExceptionSpeculation(targetMethod);
            if (speculationLog.maySpeculate(speculationReason)) {
                Speculation exceptionSpeculation = speculationLog.speculate(speculationReason);
                return new DeoptimizeNode(getAction(), getReason(), exceptionSpeculation);
            }
            return null;
        }
        return this;
    }
}
