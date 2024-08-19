/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.nodes.extended;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_0;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_0;

import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.TTY;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.iterators.NodeIterable;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.memory.MemoryAccess;
import jdk.graal.compiler.nodes.memory.MemoryKill;
import jdk.graal.compiler.nodes.memory.WriteNode;
import jdk.graal.compiler.nodes.memory.address.AddressNode;
import jdk.graal.compiler.nodes.spi.LIRLowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.graal.compiler.nodes.spi.Simplifiable;
import jdk.graal.compiler.nodes.spi.SimplifierTool;
import jdk.graal.compiler.nodes.spi.ValueProxy;
import jdk.graal.compiler.nodes.util.GraphUtil;

@NodeInfo(cycles = CYCLES_0, size = SIZE_0, allowedUsageTypes = {InputType.Anchor, InputType.Value})
public class PublishWritesNode extends FixedWithNextNode implements LIRLowerable, ValueProxy, GuardingNode, Simplifiable, AnchoringNode {
    public static final NodeClass<PublishWritesNode> TYPE = NodeClass.create(PublishWritesNode.class);

    @Input
    ValueNode allocation;

    public ValueNode allocation() {
        return allocation;
    }

    public PublishWritesNode(ValueNode newObject) {
        super(TYPE, newObject.stamp(NodeView.DEFAULT));
        this.allocation = newObject;
    }

    @Override
    public boolean inferStamp() {
        if (allocation != null) {
            return updateStamp(stamp.join(allocation.stamp(NodeView.DEFAULT)));
        } else {
            return false;
        }
    }

    @Override
    public boolean verifyNode() {
        // Check that the published allocation node is not used by reads directly
        for (AddressNode address : allocation.usages().filter(AddressNode.class)) {
            assertTrue(address.usages().filter(n ->
                    // n is a non-writing access (a.k.a. a read)
                    n instanceof MemoryAccess && !MemoryKill.isMemoryKill(n)
            ).isEmpty(), "%s has unpublished reads", allocation);
        }
        return true;
    }

    private static boolean isUsedByWritesOnly(AddressNode address) {
        return address.usages().filter(n -> !MemoryKill.isMemoryKill(n)).isEmpty();
    }

    private NodeIterable<Node> nonWriteUsages(Node node) {
        return node.usages().filter(usage -> {
            if (usage instanceof AddressNode addressNode) {
                return isUsedByWritesOnly(addressNode);
            }
            return usage != this;
        });
    }

    @Override
    public void simplify(SimplifierTool tool) {
        if (!tool.allUsagesAvailable() || hasUsages()) {
            return;
        }

        if (allocation instanceof PhiNode) {
            // TODO
            return;
        }
        
        // Allocation may be used e.g. as a field value for other allocations
        if (nonWriteUsages(allocation).isNotEmpty()) {
            return;
        }

        if (graph().getGraphState().isAfterStage(GraphState.StageFlag.LOW_TIER_LOWERING)) {
            // TODO
            TTY.println("Shit falls away in low tier");
            return;
        }


        try (DebugContext.Scope scope = getDebug().scope("KillPublishWrites", this)) {
            getDebug().dump(DebugContext.VERBOSE_LEVEL, graph(), "Before killing %s", allocation);

            for (FixedNode node : GraphUtil.predecessorIterable(this)) {
                if (node == this) {
                    GraphUtil.removeFixedWithUnusedInputs(this);
                } else if (node instanceof WriteNode write && write.getAddress().getBase() == allocation) {
                    GraphUtil.removeFixedWithUnusedInputs(write);
                } else if (node == allocation) {
                    GraphUtil.removeFixedWithUnusedInputs((FixedWithNextNode) allocation);
                    break;
                } else {
                    break;
                }
            }

            getDebug().dump(DebugContext.VERBOSE_LEVEL, graph(), "After killing %s", allocation);
        } catch (Throwable t) {
            throw getDebug().handle(t);
        }
    }

    @Override
    public void generate(NodeLIRBuilderTool generator) {
        generator.setResult(this, generator.operand(allocation));
    }

    @Override
    public ValueNode getOriginalNode() {
        return allocation;
    }

    @Override
    public GuardingNode getGuard() {
        return this;
    }
}
