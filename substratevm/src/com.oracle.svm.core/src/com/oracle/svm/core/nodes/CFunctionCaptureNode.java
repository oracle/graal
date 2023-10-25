/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.nodes;

import static jdk.graal.compiler.nodeinfo.InputType.Memory;
import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_UNKNOWN;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_UNKNOWN;

import java.util.Objects;

import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.extended.FixedValueAnchorNode;
import jdk.graal.compiler.nodes.extended.ForeignCallNode;
import jdk.graal.compiler.nodes.memory.SingleMemoryKill;
import jdk.graal.compiler.nodes.spi.Lowerable;
import jdk.graal.compiler.nodes.spi.LoweringTool;
import org.graalvm.word.LocationIdentity;

/**
 * Allows to capture the call state (i.e. some global variables, e.g. errno) in a provided buffer
 * right after the call, as to prevent the VM from changing it before it could be queried by another
 * downcall, see {@link CFunctionCaptureNode}.
 * <p>
 * This node should typically be placed between the invoke node and the
 * {@link CFunctionEpilogueNode}. This means that this node is executed while in native state. As
 * such, the capture function should be uninterruptible, and never transition to/from Java. Any
 * preprocessing on the arguments (e.g. unboxing a long, or retrieving them from a field) should be
 * done before the prologue; {@link FixedValueAnchorNode} can be used to prevent the computations
 * from floating in between the prologue/epilogue.
 * <p>
 * You need to register the capture method for foreign call and may need to declare it as an
 * analysis root.
 */
@NodeInfo(cycles = CYCLES_UNKNOWN, cyclesRationale = "Depends on capture function.", size = SIZE_UNKNOWN, sizeRationale = "Depends on capture function.", allowedUsageTypes = {Memory})
public class CFunctionCaptureNode extends FixedWithNextNode implements Lowerable, SingleMemoryKill {
    public static final NodeClass<CFunctionCaptureNode> TYPE = NodeClass.create(CFunctionCaptureNode.class);

    ForeignCallDescriptor captureFunction;
    @Node.Input ValueNode statesToCapture;
    @Node.Input ValueNode captureBuffer;

    public CFunctionCaptureNode(ForeignCallDescriptor captureFunction, ValueNode statesToCapture, ValueNode captureBuffer) {
        super(TYPE, StampFactory.forVoid());
        this.captureFunction = Objects.requireNonNull(captureFunction);
        this.statesToCapture = Objects.requireNonNull(statesToCapture);
        this.captureBuffer = Objects.requireNonNull(captureBuffer);
    }

    @Override
    public LocationIdentity getKilledLocationIdentity() {
        return LocationIdentity.any();
    }

    @Override
    public void lower(LoweringTool tool) {
        if (tool.getLoweringStage() != LoweringTool.StandardLoweringStage.LOW_TIER) {
            return;
        }

        final ForeignCallNode call = graph().add(new ForeignCallNode(captureFunction, statesToCapture, captureBuffer));
        graph().replaceFixedWithFixed(this, call);
    }
}
