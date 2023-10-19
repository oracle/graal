/*
 * Copyright (c) 2014, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.compiler.graal.nodes.memory;

import static jdk.compiler.graal.nodeinfo.InputType.Memory;
import static jdk.compiler.graal.nodeinfo.NodeCycles.CYCLES_0;
import static jdk.compiler.graal.nodeinfo.NodeSize.SIZE_0;

import jdk.compiler.graal.core.common.type.StampFactory;
import jdk.compiler.graal.graph.Node;
import jdk.compiler.graal.graph.NodeClass;
import jdk.compiler.graal.nodeinfo.NodeInfo;
import jdk.compiler.graal.nodeinfo.StructuralInput.Memory;
import jdk.compiler.graal.nodes.spi.Canonicalizable;
import jdk.compiler.graal.nodes.spi.CanonicalizerTool;
import jdk.compiler.graal.nodes.spi.LIRLowerable;
import jdk.compiler.graal.nodes.spi.NodeLIRBuilderTool;
import jdk.compiler.graal.nodes.FixedWithNextNode;
import jdk.compiler.graal.nodes.GraphState.StageFlag;
import org.graalvm.word.LocationIdentity;

@NodeInfo(allowedUsageTypes = Memory, cycles = CYCLES_0, size = SIZE_0)
public final class MemoryAnchorNode extends FixedWithNextNode implements LIRLowerable, MultiMemoryKill, Canonicalizable {

    public static final NodeClass<MemoryAnchorNode> TYPE = NodeClass.create(MemoryAnchorNode.class);

    private final LocationIdentity[] locations;

    public MemoryAnchorNode(LocationIdentity... locations) {
        super(TYPE, StampFactory.forVoid());
        this.locations = locations == null ? new LocationIdentity[0] : locations;
    }

    @Override
    public void generate(NodeLIRBuilderTool generator) {
        // Nothing to emit, since this node is used for structural purposes only.
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (tool.allUsagesAvailable() && hasNoUsages()) {
            // GR-27194
            if (this.graph().isBeforeStage(StageFlag.FIXED_READS)) {
                return null;
            }
        }
        return this;
    }

    @Override
    public LocationIdentity[] getKilledLocationIdentities() {
        return locations;
    }

    @NodeIntrinsic
    public static native Memory anchor();
}
