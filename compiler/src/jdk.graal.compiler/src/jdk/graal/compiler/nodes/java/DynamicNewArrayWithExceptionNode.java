/*
 * Copyright (c) 2024, 2026, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_8;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_8;

import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Dynamic array allocation with an explicit exception edge. This is used when the element type is
 * computed at run time and allocation OOMEs must remain observable by a local handler. If the
 * element type becomes a legal constant array type, canonicalization can replace this node with a
 * typed {@link NewArrayWithExceptionNode}.
 */
@NodeInfo(cycles = CYCLES_8, cyclesRationale = "tlab alloc + header init", size = SIZE_8)
public class DynamicNewArrayWithExceptionNode extends AllocateWithExceptionNode implements Canonicalizable {

    public static final NodeClass<DynamicNewArrayWithExceptionNode> TYPE = NodeClass.create(DynamicNewArrayWithExceptionNode.class);

    /**
     * Runtime value that determines the number of array elements to allocate.
     */
    @Input protected ValueNode length;

    /**
     * Runtime value that resolves to the component type of the array to allocate.
     */
    @Input ValueNode elementType;

    /**
     * Specifies whether the allocated array contents must be initialized to the Java default value.
     */
    private final boolean fillContents;

    /**
     * Class pointer to void.class needs to be exposed earlier than this node is lowered so that it
     * can be replaced by the AOT machinery. If it's not needed for lowering this input can be
     * ignored.
     */
    @OptionalInput ValueNode voidClass;

    public DynamicNewArrayWithExceptionNode(ValueNode elementType, ValueNode length) {
        this(elementType, length, true);
    }

    public DynamicNewArrayWithExceptionNode(ValueNode elementType, ValueNode length, boolean fillContents) {
        this(elementType, length, fillContents, null);
    }

    public DynamicNewArrayWithExceptionNode(ValueNode elementType, ValueNode length, boolean fillContents, FrameState stateBefore) {
        this(elementType, length, fillContents, stateBefore, true);
    }

    public DynamicNewArrayWithExceptionNode(ValueNode elementType, ValueNode length, boolean fillContents, FrameState stateBefore, boolean mustHaveStateAfter) {
        this(elementType, length, fillContents, stateBefore, null, mustHaveStateAfter);
    }

    public DynamicNewArrayWithExceptionNode(ValueNode elementType, ValueNode length, boolean fillContents, FrameState stateBefore, FrameState stateAfter) {
        this(elementType, length, fillContents, stateBefore, stateAfter, true);
    }

    public DynamicNewArrayWithExceptionNode(ValueNode elementType, ValueNode length, boolean fillContents, FrameState stateBefore, FrameState stateAfter, boolean mustHaveStateAfter) {
        super(TYPE, StampFactory.objectNonNull(), mustHaveStateAfter);
        this.length = length;
        this.elementType = elementType;
        this.fillContents = fillContents;
        this.stateBefore = stateBefore;
        this.stateAfter = stateAfter;
    }

    public static boolean throwsIllegalArgumentException(Class<?> elementType, Class<?> voidClass) {
        return elementType == voidClass;
    }

    public static boolean throwsIllegalArgumentException(ResolvedJavaType elementType) {
        return elementType.getJavaKind() == JavaKind.Void;
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (elementType.isConstant()) {
            ResolvedJavaType type = tool.getConstantReflection().asJavaType(elementType.asConstant());
            if (type != null && !throwsIllegalArgumentException(type) && tool.getMetaAccessExtensionProvider().canConstantFoldDynamicAllocation(type.getArrayClass())) {
                return new NewArrayWithExceptionNode(type, length, fillContents, stateBefore, stateAfter(), mustHaveStateAfter());
            }
        }
        return this;
    }

    public ValueNode getVoidClass() {
        return voidClass;
    }

    public void setVoidClass(ValueNode newVoidClass) {
        updateUsages(voidClass, newVoidClass);
        voidClass = newVoidClass;
    }

    public ValueNode getElementType() {
        return elementType;
    }

    public ValueNode length() {
        return length;
    }

    @Override
    public boolean fillContents() {
        return fillContents;
    }

    @Override
    public FixedNode replaceWithNonThrowing() {
        killExceptionEdge();
        DynamicNewArrayNode newArray = graph().add(new DynamicNewArrayNode(elementType, length, fillContents));
        newArray.setStateBefore(stateBefore);
        graph().replaceSplitWithFixed(this, newArray, this.next());
        // copy across any original node source position
        newArray.setNodeSourcePosition(getNodeSourcePosition());
        return newArray;
    }
}
