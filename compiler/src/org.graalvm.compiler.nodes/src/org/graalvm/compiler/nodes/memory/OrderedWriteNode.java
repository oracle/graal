/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, Red Hat Inc. All rights reserved.
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

package org.graalvm.compiler.nodes.memory;

import static org.graalvm.compiler.core.common.memory.MemoryOrderMode.VOLATILE;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.memory.MemoryOrderMode;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.gc.WriteBarrier;
import org.graalvm.compiler.nodes.java.AbstractCompareAndSwapNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import org.graalvm.compiler.nodes.spi.SimplifierTool;
import org.graalvm.word.LocationIdentity;

@NodeInfo(nameTemplate = "OrderedWrite#{p#location/s}")
public class OrderedWriteNode extends WriteNode {
    public static final NodeClass<OrderedWriteNode> TYPE = NodeClass.create(OrderedWriteNode.class);
    private final MemoryOrderMode memoryOrder;

    public OrderedWriteNode(AddressNode address, LocationIdentity location, ValueNode value, BarrierType barrierType, MemoryOrderMode memoryOrder) {
        super(TYPE, address, location, LocationIdentity.any(), value, barrierType);
        // Node is expected to have ordering requirements
        assert MemoryOrderMode.ordersMemoryAccesses(memoryOrder);
        this.memoryOrder = memoryOrder;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        LIRKind writeKind = gen.getLIRGeneratorTool().getLIRKind(value().stamp(NodeView.DEFAULT));
        gen.getLIRGeneratorTool().getArithmetic().emitOrderedStore(writeKind, gen.operand(address), gen.operand(value()), gen.state(this), memoryOrder);
    }

    @Override
    public boolean hasSideEffect() {
        return true;
    }

    @Override
    public MemoryOrderMode getMemoryOrder() {
        return memoryOrder;
    }

    @Override
    public void simplify(SimplifierTool tool) {
        if (tool.trySinkWriteFences() && getMemoryOrder() == VOLATILE) {
            /*
             * If this node is followed by a volatile write, then this can be converted to a write
             * release.
             */
            if (hasFollowingVolatileWrite()) {
                OrderedWriteNode writeRelease = graph().add(new OrderedWriteNode(getAddress(), getLocationIdentity(), value(), getBarrierType(), MemoryOrderMode.RELEASE));
                writeRelease.setLastLocationAccess(writeRelease.getLastLocationAccess());
                graph().replaceFixedWithFixed(this, writeRelease);
                tool.addToWorkList(writeRelease);
            }
        }
    }

    private boolean hasFollowingVolatileWrite() {
        FixedWithNextNode cur = this;
        while (true) {
            // Check the memory usages of the current access
            for (Node usage : cur.usages()) {
                if (!(usage instanceof MemoryAccess) || !(usage instanceof FixedWithNextNode)) {
                    // Other kinds of usages won't be visited in the traversal and likely
                    // invalidates elimination of the barrier instruction.
                    return false;
                }
            }
            FixedNode nextNode = cur.next();
            // We can safely ignore GC barriers
            while (nextNode instanceof WriteBarrier) {
                nextNode = ((WriteBarrier) nextNode).next();
            }

            if (nextNode instanceof OrderedMemoryAccess) {
                if (nextNode instanceof AbstractWriteNode || nextNode instanceof AbstractCompareAndSwapNode) {
                    if (((OrderedMemoryAccess) nextNode).getMemoryOrder() == VOLATILE) {
                        return true;
                    } else {
                        // Since writes are ordered, can check next instruction
                        cur = (FixedWithNextNode) nextNode;
                        continue;
                    }
                }
            }

            return false;
        }
    }
}
