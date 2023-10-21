/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.replacements.nodes;

import java.util.Arrays;
import java.util.EnumSet;

import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.GenerateStub;
import jdk.graal.compiler.nodeinfo.NodeCycles;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodeinfo.NodeSize;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.NamedLocationIdentity;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.graal.compiler.nodes.spi.Virtualizable;
import jdk.graal.compiler.nodes.spi.VirtualizerTool;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.nodes.virtual.VirtualObjectNode;
import org.graalvm.word.Pointer;

import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;

// JaCoCo Exclude

/**
 * Compares two arrays with the same length.
 */
@NodeInfo(cycles = NodeCycles.CYCLES_UNKNOWN, size = NodeSize.SIZE_16)
public class ArrayEqualsNode extends PureFunctionStubIntrinsicNode implements Canonicalizable, Virtualizable {

    public static final NodeClass<ArrayEqualsNode> TYPE = NodeClass.create(ArrayEqualsNode.class);

    /**
     * {@link JavaKind} of the arrays to compare.
     *
     * The arrays are guaranteed to always have the same kind because the signature of
     * {@link Arrays#equals} only allows arrays of the same kind.
     */
    protected final JavaKind kind;

    /** One array to be tested for equality. */
    @Input protected ValueNode array1;

    /** array base offset of array1. */
    @Input protected ValueNode offset1;

    /** The other array to be tested for equality. */
    @Input protected ValueNode array2;

    /** array base offset of array2. */
    @Input protected ValueNode offset2;

    /** Length of both arrays. */
    @Input protected ValueNode length;

    public ArrayEqualsNode(ValueNode array1, ValueNode offset1, ValueNode array2, ValueNode offset2, ValueNode length,
                    @ConstantNodeParameter JavaKind kind) {
        this(TYPE, array1, offset1, array2, offset2, length, kind, null);
    }

    public ArrayEqualsNode(ValueNode array1, ValueNode offset1, ValueNode array2, ValueNode offset2, ValueNode length,
                    @ConstantNodeParameter JavaKind kind,
                    @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures) {
        this(TYPE, array1, offset1, array2, offset2, length, kind, runtimeCheckedCPUFeatures);
    }

    protected ArrayEqualsNode(NodeClass<? extends ArrayEqualsNode> c, ValueNode array1, ValueNode offset1, ValueNode array2, ValueNode offset2, ValueNode length,
                    @ConstantNodeParameter JavaKind kind,
                    @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures) {
        super(c, StampFactory.forKind(JavaKind.Boolean), runtimeCheckedCPUFeatures, NamedLocationIdentity.getArrayLocation(kind));
        this.kind = kind;
        this.array1 = array1;
        this.offset1 = offset1;
        this.array2 = array2;
        this.offset2 = offset2;
        this.length = length;
    }

    private static boolean isNaNFloat(JavaConstant constant) {
        JavaKind kind = constant.getJavaKind();
        return (kind == JavaKind.Float && Float.isNaN(constant.asFloat())) || (kind == JavaKind.Double && Double.isNaN(constant.asDouble()));
    }

    protected static boolean arrayEquals(ConstantReflectionProvider constantReflection, JavaConstant a, int startIndexA, JavaConstant b, int startIndexB, int len) {
        for (int i = 0; i < len; i++) {
            JavaConstant aElem = constantReflection.readArrayElement(a, startIndexA + i);
            JavaConstant bElem = constantReflection.readArrayElement(b, startIndexB + i);
            if (!constantReflection.constantEquals(aElem, bElem) && !(isNaNFloat(aElem) && isNaNFloat(bElem))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (tool.allUsagesAvailable() && hasNoUsages()) {
            return null;
        }
        ValueNode a1 = GraphUtil.unproxify(array1);
        ValueNode a2 = GraphUtil.unproxify(array2);
        if (a1 == a2) {
            return ConstantNode.forBoolean(true);
        }
        if (a1.isConstant() && offset1.isConstant() && a2.isConstant() && offset2.isConstant() && length.isConstant()) {
            GraalError.guarantee(offset1.asJavaConstant().asLong() == tool.getMetaAccess().getArrayBaseOffset(kind), "offset must be exactly the array base offset");
            GraalError.guarantee(offset2.asJavaConstant().asLong() == tool.getMetaAccess().getArrayBaseOffset(kind), "offset must be exactly the array base offset");
            ConstantNode c1 = (ConstantNode) a1;
            ConstantNode c2 = (ConstantNode) a2;
            if (c1.getStableDimension() >= 1 && c2.getStableDimension() >= 1) {
                ConstantReflectionProvider constantReflection = tool.getConstantReflection();
                Integer c1Length = constantReflection.readArrayLength(c1.asJavaConstant());
                Integer c2Length = constantReflection.readArrayLength(c2.asJavaConstant());
                if (c1Length != null && c2Length != null && c1Length.equals(c2Length)) {
                    boolean ret = arrayEquals(constantReflection, c1.asJavaConstant(), 0, c2.asJavaConstant(), 0, length.asJavaConstant().asInt());
                    return ConstantNode.forBoolean(ret);
                }
            }
        }
        return this;
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        ValueNode alias1 = tool.getAlias(array1);
        ValueNode alias2 = tool.getAlias(array2);
        if (alias1 == alias2) {
            // the same virtual objects will always have the same contents
            tool.replaceWithValue(ConstantNode.forBoolean(true, graph()));
        } else if (alias1 instanceof VirtualObjectNode && alias2 instanceof VirtualObjectNode) {
            VirtualObjectNode virtual1 = (VirtualObjectNode) alias1;
            VirtualObjectNode virtual2 = (VirtualObjectNode) alias2;

            if (virtual1.entryCount() == virtual2.entryCount()) {
                int entryCount = virtual1.entryCount();
                boolean allEqual = true;
                for (int i = 0; i < entryCount; i++) {
                    ValueNode entry1 = tool.getEntry(virtual1, i);
                    ValueNode entry2 = tool.getEntry(virtual2, i);
                    if (entry1 != entry2) {
                        if (entry1 instanceof ConstantNode && entry2 instanceof ConstantNode) {
                            // Float NaN constants are different constant nodes but treated as
                            // equal in Arrays.equals([F[F) or Arrays.equals([D[D).
                            if (entry1.getStackKind() == JavaKind.Float && entry2.getStackKind() == JavaKind.Float) {
                                float value1 = ((JavaConstant) entry1.asConstant()).asFloat();
                                float value2 = ((JavaConstant) entry2.asConstant()).asFloat();
                                if (Float.floatToIntBits(value1) != Float.floatToIntBits(value2)) {
                                    allEqual = false;
                                }
                            } else if (entry1.getStackKind() == JavaKind.Double && entry2.getStackKind() == JavaKind.Double) {
                                double value1 = ((JavaConstant) entry1.asConstant()).asDouble();
                                double value2 = ((JavaConstant) entry2.asConstant()).asDouble();
                                if (Double.doubleToLongBits(value1) != Double.doubleToLongBits(value2)) {
                                    allEqual = false;
                                }
                            } else {
                                allEqual = false;
                            }
                        } else {
                            // the contents might be different
                            allEqual = false;
                        }
                    }
                    if (entry1.stamp(NodeView.DEFAULT).alwaysDistinct(entry2.stamp(NodeView.DEFAULT))) {
                        // the contents are different
                        tool.replaceWithValue(ConstantNode.forBoolean(false, graph()));
                        return;
                    }
                }
                if (allEqual) {
                    tool.replaceWithValue(ConstantNode.forBoolean(true, graph()));
                }
            }
        }
    }

    @NodeIntrinsic
    @GenerateStub(name = "longArraysEquals", parameters = {"Long"})
    @GenerateStub(name = "floatArraysEquals", parameters = {"Float"})
    @GenerateStub(name = "doubleArraysEquals", parameters = {"Double"})
    public static native boolean equals(Pointer array1, long offset1, Pointer array2, long offset2, int length,
                    @ConstantNodeParameter JavaKind kind);

    @NodeIntrinsic
    public static native boolean equals(Pointer array1, long offset1, Pointer array2, long offset2, int length,
                    @ConstantNodeParameter JavaKind kind,
                    @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures);

    public ValueNode getArray1() {
        return array1;
    }

    public ValueNode getArray2() {
        return array2;
    }

    public ValueNode getLength() {
        return length;
    }

    public JavaKind getKind() {
        return kind;
    }

    @Override
    public ForeignCallDescriptor getForeignCallDescriptor() {
        return ArrayEqualsForeignCalls.getArrayEqualsStub(this);
    }

    @Override
    public ValueNode[] getForeignCallArguments() {
        return new ValueNode[]{array1, offset1, array2, offset2, length};
    }

    @Override
    public void emitIntrinsic(NodeLIRBuilderTool gen) {
        gen.setResult(this, gen.getLIRGeneratorTool().emitArrayEquals(
                        kind,
                        getRuntimeCheckedCPUFeatures(),
                        gen.operand(array1),
                        gen.operand(offset1),
                        gen.operand(array2),
                        gen.operand(offset2),
                        gen.operand(length)));
    }

}
