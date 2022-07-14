/*
 * Copyright (c) 2011, 2022, Oracle and/or its affiliates. All rights reserved.
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
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.ReinterpretNode;
import org.graalvm.compiler.nodes.gc.WriteBarrier;
import org.graalvm.compiler.nodes.java.AbstractCompareAndSwapNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import org.graalvm.compiler.nodes.spi.Simplifiable;
import org.graalvm.compiler.nodes.spi.SimplifierTool;
import org.graalvm.word.LocationIdentity;

/**
 * Writes a given {@linkplain #value() value} a {@linkplain FixedAccessNode memory location}.
 */
@NodeInfo(nameTemplate = "Write#{p#location/s}")
public class WriteNode extends AbstractWriteNode implements LIRLowerableAccess, Simplifiable {

    public static final NodeClass<WriteNode> TYPE = NodeClass.create(WriteNode.class);

    private final LocationIdentity killedLocationIdentity;
    private MemoryOrderMode memoryOrder;

    public WriteNode(AddressNode address, LocationIdentity location, ValueNode value, BarrierType barrierType, MemoryOrderMode memoryOrder) {
        this(TYPE, address, location, location, value, barrierType, memoryOrder);
    }

    protected WriteNode(NodeClass<? extends WriteNode> c, AddressNode address, LocationIdentity location, LocationIdentity killedLocationIdentity, ValueNode value, BarrierType barrierType,
                    MemoryOrderMode memoryOrder) {
        super(c, address, location, value, barrierType);
        this.killedLocationIdentity = killedLocationIdentity;
        this.memoryOrder = memoryOrder;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        LIRKind writeKind = gen.getLIRGeneratorTool().getLIRKind(value().stamp(NodeView.DEFAULT));
        gen.getLIRGeneratorTool().getArithmetic().emitStore(writeKind, gen.operand(address), gen.operand(value()), gen.state(this), memoryOrder);
    }

    @Override
    public Stamp getAccessStamp(NodeView view) {
        return value().stamp(view);
    }

    @Override
    public boolean canNullCheck() {
        return true;
    }

    @Override
    public boolean hasSideEffect() {
        /*
         * Writes with memory ordering requirements have visible side-effects
         */
        if (ordersMemoryAccesses()) {
            return true;
        }
        /*
         * Writes to newly allocated objects don't have a visible side-effect to the interpreter
         */
        if (getLocationIdentity().equals(LocationIdentity.INIT_LOCATION)) {
            return false;
        }
        return super.hasSideEffect();
    }

    @Override
    public LocationIdentity getKilledLocationIdentity() {
        if (ordersMemoryAccesses()) {
            return LocationIdentity.any();
        }
        return killedLocationIdentity;
    }

    @Override
    public MemoryOrderMode getMemoryOrder() {
        return memoryOrder;
    }

    @Override
    public void simplify(SimplifierTool tool) {
        if (!ordersMemoryAccesses()) {
            if (tool.canonicalizeReads() && hasExactlyOneUsage() && next() instanceof WriteNode) {
                WriteNode write = (WriteNode) next();
                if (write.lastLocationAccess == this && write.getAddress() == getAddress() && getAccessStamp(NodeView.DEFAULT).isCompatible(write.getAccessStamp(NodeView.DEFAULT))) {
                    write.setLastLocationAccess(getLastLocationAccess());
                    tool.addToWorkList(inputs());
                    tool.addToWorkList(next());
                    tool.addToWorkList(predecessor());
                    graph().removeFixed(this);
                }
            }
            // reinterpret means nothing while writing - we simply write the bytes
            if (value() instanceof ReinterpretNode) {
                tool.addToWorkList(value());
                tool.addToWorkList(((ReinterpretNode) value()).getValue());
                tool.addToWorkList(this);
                setValue(((ReinterpretNode) value()).getValue());
            }
        } else if (tool.trySinkWriteFences() && getMemoryOrder() == VOLATILE) {
            /*
             * If this node is followed by a volatile write, then this write can be converted to a
             * write release since doing so will not allow any illegal reorderings. A write release
             * has the same semantics as a volatile write, except that it allows a following
             * volatile read to be hoisted above it. However, since this write is followed by a
             * volatile write without an intervening read, no volatile reads can be raised above it.
             */
            if (followedByVolatileWrite(this)) {
                memoryOrder = MemoryOrderMode.RELEASE;
            }
        }
    }

    private static boolean followedByVolatileWrite(FixedWithNextNode start) {
        FixedWithNextNode cur = start;
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
