/*
 * Copyright (c) 2013, 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Arrays;

import org.graalvm.compiler.core.common.spi.ForeignCallLinkage;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeCycles;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodeinfo.NodeSize;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.NamedLocationIdentity;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.ValueNodeUtil;
import org.graalvm.compiler.nodes.memory.MemoryAccess;
import org.graalvm.compiler.nodes.memory.MemoryKill;
import org.graalvm.compiler.nodes.spi.Canonicalizable;
import org.graalvm.compiler.nodes.spi.CanonicalizerTool;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import org.graalvm.compiler.nodes.spi.Virtualizable;
import org.graalvm.compiler.nodes.spi.VirtualizerTool;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.nodes.virtual.VirtualObjectNode;
import org.graalvm.word.LocationIdentity;

import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;

// JaCoCo Exclude

/**
 * Compares two arrays with the same length.
 */
@NodeInfo(cycles = NodeCycles.CYCLES_UNKNOWN, size = NodeSize.SIZE_128)
public class ArrayEqualsNode extends FixedWithNextNode implements LIRLowerable, Canonicalizable, Virtualizable, MemoryAccess {

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

    /** The other array to be tested for equality. */
    @Input protected ValueNode array2;

    /** Length of both arrays. */
    @Input protected ValueNode length;

    @OptionalInput(Memory) MemoryKill lastLocationAccess;

    public ArrayEqualsNode(ValueNode array1, ValueNode array2, ValueNode length, @ConstantNodeParameter JavaKind kind) {
        this(TYPE, array1, array2, length, kind);
    }

    protected ArrayEqualsNode(NodeClass<? extends ArrayEqualsNode> c, ValueNode array1, ValueNode array2, ValueNode length, @ConstantNodeParameter JavaKind kind) {
        super(c, StampFactory.forKind(JavaKind.Boolean));
        this.kind = kind;
        this.array1 = array1;
        this.array2 = array2;
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
        if (a1.isConstant() && a2.isConstant() && length.isConstant()) {
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
    public static native boolean equals(Object array1, Object array2, int length, @ConstantNodeParameter JavaKind kind);

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
    public void generate(NodeLIRBuilderTool gen) {
        if (length.isJavaConstant() && kind.isNumericInteger()) {
            // Full-unroll opportunity in LIR.
        } else if (UseGraalStubs.getValue(graph().getOptions())) {
            ForeignCallLinkage linkage = gen.lookupGraalStub(this);
            if (linkage != null) {
                Value result = gen.getLIRGeneratorTool().emitForeignCall(linkage, null, gen.operand(array1), gen.operand(array2), gen.operand(length));
                gen.setResult(this, result);
                return;
            }
        }
        generateArrayEquals(gen);
    }

    protected void generateArrayEquals(NodeLIRBuilderTool gen) {
        int array1BaseOffset = gen.getLIRGeneratorTool().getMetaAccess().getArrayBaseOffset(kind);
        int array2BaseOffset = gen.getLIRGeneratorTool().getMetaAccess().getArrayBaseOffset(kind);
        Value result = gen.getLIRGeneratorTool().emitArrayEquals(kind, array1BaseOffset, array2BaseOffset, gen.operand(array1), gen.operand(array2),
                        gen.operand(length), false);
        gen.setResult(this, result);
    }

    @Override
    public LocationIdentity getLocationIdentity() {
        return NamedLocationIdentity.getArrayLocation(kind);
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
