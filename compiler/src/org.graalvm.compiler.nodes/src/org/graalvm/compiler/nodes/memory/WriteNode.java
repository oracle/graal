/*
 * Copyright (c) 2011, 2021, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodes.NamedLocationIdentity;
import org.graalvm.compiler.nodes.calc.ReinterpretNode;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
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

    public WriteNode(AddressNode address, LocationIdentity location, ValueNode value, BarrierType barrierType) {
        this(TYPE, address, location, location, value, barrierType);
    }

    protected WriteNode(NodeClass<? extends WriteNode> c, AddressNode address, LocationIdentity location, LocationIdentity killedLocationIdentity, ValueNode value, BarrierType barrierType) {
        super(c, address, location, value, barrierType);
        this.killedLocationIdentity = killedLocationIdentity;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        LIRKind writeKind = gen.getLIRGeneratorTool().getLIRKind(value().stamp(NodeView.DEFAULT));
        gen.getLIRGeneratorTool().getArithmetic().emitStore(writeKind, gen.operand(address), gen.operand(value()), gen.state(this));
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
         * Writes to newly allocated objects don't have a visible side-effect to the interpreter
         */
        if (getLocationIdentity().equals(LocationIdentity.INIT_LOCATION)) {
            return false;
        }
        return super.hasSideEffect();
    }

    @Override
    public final LocationIdentity getKilledLocationIdentity() {
        return killedLocationIdentity;
    }

    @Override
    public void simplify(SimplifierTool tool) {
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
        // reinterpret means nothing writing to an array - we simply write the bytes
        if (NamedLocationIdentity.isArrayLocation(location) && value() instanceof ReinterpretNode) {
            tool.addToWorkList(value());
            tool.addToWorkList(((ReinterpretNode) value()).getValue());
            tool.addToWorkList(this);
            setValue(((ReinterpretNode) value()).getValue());
        }
    }
}
