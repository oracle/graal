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
package org.graalvm.compiler.hotspot;

import static org.graalvm.compiler.nodeinfo.InputType.Memory;
import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_64;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_16;

import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.NodeInputList;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.AbstractStateSplit;
import org.graalvm.compiler.nodes.NamedLocationIdentity;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.ValueNodeUtil;
import org.graalvm.compiler.nodes.memory.MemoryAccess;
import org.graalvm.compiler.nodes.memory.MemoryCheckpoint;
import org.graalvm.compiler.nodes.memory.MemoryNode;
import org.graalvm.compiler.nodes.spi.Lowerable;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.word.Word;
import org.graalvm.word.LocationIdentity;

import jdk.vm.ci.meta.JavaKind;

@NodeInfo(cycles = CYCLES_64, size = SIZE_16, allowedUsageTypes = {Memory})
public class BigIntegerSubstitutionNode extends AbstractStateSplit implements Lowerable, MemoryCheckpoint.Single, MemoryAccess {

    public static final NodeClass<BigIntegerSubstitutionNode> TYPE = NodeClass.create(BigIntegerSubstitutionNode.class);

    public enum BigIntegerOp {
        MUL_TO_LEN,
        MUL_ADD,
        MONTGOMERY_MUL,
        MONTOGEMRY_SQUARE,
        SQUARE_TO_LEN
    }

    @OptionalInput(Memory) Node lastLocationAccess;
    @Input NodeInputList<ValueNode> values;

    public final BigIntegerOp op;

    public BigIntegerSubstitutionNode(@InjectedNodeParameter Stamp stamp, BigIntegerOp op, ValueNode... bigIntegerArgs) {
        super(TYPE, stamp);
        this.op = op;
        values = new NodeInputList<>(this, bigIntegerArgs);
    }

    public NodeInputList<ValueNode> getValues() {
        return values;
    }

    @Override
    public LocationIdentity getKilledLocationIdentity() {
        return NamedLocationIdentity.getArrayLocation(JavaKind.Int);
    }

    @Override
    public void lower(LoweringTool tool) {
        tool.getLowerer().lower(this, tool);
    }

    @Override
    public LocationIdentity getLocationIdentity() {
        return getKilledLocationIdentity();
    }

    @Override
    public MemoryNode getLastLocationAccess() {
        return (MemoryNode) lastLocationAccess;
    }

    @Override
    public void setLastLocationAccess(MemoryNode lla) {
        Node newLla = ValueNodeUtil.asNode(lla);
        updateUsages(lastLocationAccess, newLla);
        lastLocationAccess = newLla;
    }

    @NodeIntrinsic
    public static native void multiplyToLen(@ConstantNodeParameter BigIntegerOp op, Word xAddr, int xlen, Word yAddr, int ylen, Word zAddr, int zLen);

    @NodeIntrinsic
    public static native int mulAdd(@ConstantNodeParameter BigIntegerOp op, Word inAddr, Word outAddr, int newOffset, int len, int k);

    @NodeIntrinsic
    public static native void squareToLen(@ConstantNodeParameter BigIntegerOp op, Word xAddr, int len, Word zAddr, int zLen);

    @NodeIntrinsic
    public static native void montgomerySquare(@ConstantNodeParameter BigIntegerOp op, Word aAddr, Word nAddr, int len, long inv, Word productAddr);

    @NodeIntrinsic
    public static native void montgomeryMultiply(@ConstantNodeParameter BigIntegerOp op, Word aAddr, Word bAddr, Word nAddr, int len, long inv, Word productAddr);
}
