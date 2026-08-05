/*
 * Copyright (c) 2014, 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.nodes.java;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_32;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_32;

import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.Node.NodeIntrinsicFactory;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.BinaryOpLogicNode;
import jdk.graal.compiler.nodes.LogicConstantNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.ConditionalNode;
import jdk.graal.compiler.nodes.extended.GetClassNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.Lowerable;
import jdk.graal.compiler.nodes.type.StampTool;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.TriState;

/**
 * The {@code ClassIsAssignableFromNode} represents a type check against {@link Class} instead of
 * against instances. This is used, for instance, to intrinsify
 * {@link Class#isAssignableFrom(Class)} .
 */
@NodeInfo(cycles = CYCLES_32, size = SIZE_32)
@NodeIntrinsicFactory
public final class ClassIsAssignableFromNode extends BinaryOpLogicNode implements Canonicalizable.Binary<ValueNode>, Lowerable {

    public static final NodeClass<ClassIsAssignableFromNode> TYPE = NodeClass.create(ClassIsAssignableFromNode.class);
    private final boolean outlineSecondaryTypeCheck;

    public ClassIsAssignableFromNode(ValueNode thisClass, ValueNode otherClass) {
        this(thisClass, otherClass, false);
    }

    public ClassIsAssignableFromNode(ValueNode thisClass, ValueNode otherClass, boolean outlineSecondaryTypeCheck) {
        super(TYPE, thisClass, otherClass);
        this.outlineSecondaryTypeCheck = outlineSecondaryTypeCheck;

        /*
         * Values must have been null-checked beforehand, so that the lowering snippets do not need
         * to worry about null values.
         */
        assert StampTool.isPointerNonNull(thisClass);
        assert StampTool.isPointerNonNull(otherClass);
    }

    public ValueNode getThisClass() {
        return getX();
    }

    public ValueNode getOtherClass() {
        return getY();
    }

    /**
     * Returns whether lowering should outline the secondary type check.
     */
    public boolean shouldOutlineSecondaryTypeCheck() {
        return outlineSecondaryTypeCheck;
    }

    @NodeIntrinsic
    public static native boolean isAssignableFrom(Object thisClass, Object otherClass, @ConstantNodeParameter boolean outlineSecondaryTypeCheck);

    public static boolean intrinsify(GraphBuilderContext b, ValueNode thisClass, ValueNode otherClass, boolean outlineSecondaryTypeCheck) {
        ClassIsAssignableFromNode condition = b.append(new ClassIsAssignableFromNode(b.nullCheckedValue(thisClass), b.nullCheckedValue(otherClass), outlineSecondaryTypeCheck));
        b.addPush(JavaKind.Boolean, ConditionalNode.create(condition, NodeView.DEFAULT));
        return true;
    }

    @Override
    public Node canonical(CanonicalizerTool tool, ValueNode forX, ValueNode forY) {
        if (forY instanceof GetClassNode) {
            // Canonicalize `Class.isAssignableFrom(Object.getClass())` to
            // `Class.isInstance(Object)`.
            // For non-leaf types, `Object.getClass()` cannot only return a ValueNode stamped
            // java.lang.Class, which prevents folding to a isNull check when the Object argument is
            // constant and complicates ClassIsAssignableFrom in the other cases.
            return InstanceOfDynamicNode.create(tool.getAssumptions(), tool.getConstantReflection(), forX, ((GetClassNode) forY).getObject(), false);
        }
        if (forX.isConstant() && forY.isConstant()) {
            ConstantReflectionProvider constantReflection = tool.getConstantReflection();
            ResolvedJavaType thisType = constantReflection.asJavaType(forX.asJavaConstant());
            ResolvedJavaType otherType = constantReflection.asJavaType(forY.asJavaConstant());
            if (thisType != null && otherType != null) {
                return LogicConstantNode.forBoolean(thisType.isAssignableFrom(otherType));
            }
        }
        return super.canonical(tool, forX, forY);
    }

    @Override
    public Stamp getSucceedingStampForX(boolean negated, Stamp xStamp, Stamp yStamp) {
        return null;
    }

    @Override
    public Stamp getSucceedingStampForY(boolean negated, Stamp xStamp, Stamp yStamp) {
        return null;
    }

    @Override
    public TriState tryFold(Stamp xStamp, Stamp yStamp) {
        return TriState.UNKNOWN;
    }

}
