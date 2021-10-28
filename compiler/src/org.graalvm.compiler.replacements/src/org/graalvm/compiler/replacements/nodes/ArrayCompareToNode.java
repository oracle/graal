/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.replacements.nodes;

import static org.graalvm.compiler.core.common.GraalOptions.UseGraalStubs;
import static org.graalvm.compiler.nodeinfo.InputType.Memory;
import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_1024;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_1024;

import org.graalvm.compiler.core.common.spi.ForeignCallLinkage;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodes.spi.Canonicalizable;
import org.graalvm.compiler.nodes.spi.CanonicalizerTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.NamedLocationIdentity;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.ValueNodeUtil;
import org.graalvm.compiler.nodes.memory.MemoryAccess;
import org.graalvm.compiler.nodes.memory.MemoryKill;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import org.graalvm.compiler.nodes.spi.Virtualizable;
import org.graalvm.compiler.nodes.spi.VirtualizerTool;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.word.LocationIdentity;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.Value;

// JaCoCo Exclude

/**
 * Compares two arrays lexicographically.
 */
@NodeInfo(cycles = CYCLES_1024, size = SIZE_1024)
public class ArrayCompareToNode extends FixedWithNextNode implements LIRLowerable, Canonicalizable, Virtualizable, MemoryAccess {

    public static final NodeClass<ArrayCompareToNode> TYPE = NodeClass.create(ArrayCompareToNode.class);

    /** {@link JavaKind} of one array to compare. */
    protected final JavaKind kind1;

    /** {@link JavaKind} of the other array to compare. */
    protected final JavaKind kind2;

    /** One array to be tested for equality. */
    @Input protected ValueNode array1;

    /** The other array to be tested for equality. */
    @Input protected ValueNode array2;

    /** Length of one array. */
    @Input protected ValueNode length1;

    /** Length of the other array. */
    @Input protected ValueNode length2;

    @OptionalInput(Memory) protected MemoryKill lastLocationAccess;

    public ArrayCompareToNode(ValueNode array1, ValueNode array2, ValueNode length1, ValueNode length2, @ConstantNodeParameter JavaKind kind1, @ConstantNodeParameter JavaKind kind2) {
        this(TYPE, array1, array2, length1, length2, kind1, kind2);
    }

    protected ArrayCompareToNode(NodeClass<? extends ArrayCompareToNode> c, ValueNode array1, ValueNode array2, ValueNode length1, ValueNode length2, @ConstantNodeParameter JavaKind kind1,
                    @ConstantNodeParameter JavaKind kind2) {
        super(c, StampFactory.forKind(JavaKind.Int));
        this.kind1 = kind1;
        this.kind2 = kind2;
        this.array1 = array1;
        this.array2 = array2;
        this.length1 = length1;
        this.length2 = length2;
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (tool.allUsagesAvailable() && hasNoUsages()) {
            return null;
        }
        ValueNode a1 = GraphUtil.unproxify(array1);
        ValueNode a2 = GraphUtil.unproxify(array2);
        if (a1 == a2) {
            return ConstantNode.forInt(0);
        }
        return this;
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        ValueNode alias1 = tool.getAlias(array1);
        ValueNode alias2 = tool.getAlias(array2);
        if (alias1 == alias2) {
            // the same virtual objects will always have the same contents
            tool.replaceWithValue(ConstantNode.forInt(0, graph()));
        }
    }

    @NodeIntrinsic
    public static native int compareTo(Object array1, Object array2, int length1, int length2, @ConstantNodeParameter JavaKind kind1, @ConstantNodeParameter JavaKind kind2);

    public JavaKind getKind1() {
        return kind1;
    }

    public JavaKind getKind2() {
        return kind2;
    }

    public ValueNode getArray1() {
        return array1;
    }

    public ValueNode getArray2() {
        return array2;
    }

    public ValueNode getLength1() {
        return length1;
    }

    public ValueNode getLength2() {
        return length2;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        if (UseGraalStubs.getValue(graph().getOptions())) {
            ForeignCallLinkage linkage = gen.lookupGraalStub(this);
            if (linkage != null) {
                Value result = gen.getLIRGeneratorTool().emitForeignCall(linkage, null, gen.operand(array1), gen.operand(array2), gen.operand(length1), gen.operand(length2));
                gen.setResult(this, result);
                return;
            }
        }
        generateArrayCompareTo(gen);
    }

    protected void generateArrayCompareTo(NodeLIRBuilderTool gen) {
        MetaAccessProvider metaAccess = gen.getLIRGeneratorTool().getMetaAccess();
        int array1BaseOffset = metaAccess.getArrayBaseOffset(kind1);
        int array2BaseOffset = metaAccess.getArrayBaseOffset(kind2);
        Value result = gen.getLIRGeneratorTool().emitArrayCompareTo(kind1, kind2, array1BaseOffset, array2BaseOffset, gen.operand(array1), gen.operand(array2), gen.operand(length1),
                        gen.operand(length2));
        gen.setResult(this, result);
    }

    @Override
    public LocationIdentity getLocationIdentity() {
        return kind1 != kind2 ? LocationIdentity.ANY_LOCATION : NamedLocationIdentity.getArrayLocation(kind1);
    }

    @Override
    public MemoryKill getLastLocationAccess() {
        return lastLocationAccess;
    }

    @Override
    public void setLastLocationAccess(MemoryKill lla) {
        updateUsages(ValueNodeUtil.asNode(lastLocationAccess), ValueNodeUtil.asNode(lla));
        lastLocationAccess = lla;
    }
}
