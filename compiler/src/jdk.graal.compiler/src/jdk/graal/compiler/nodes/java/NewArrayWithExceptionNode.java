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
package jdk.graal.compiler.nodes.java;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_8;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_8;

import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.core.common.type.TypeReference;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.vm.ci.meta.ResolvedJavaType;

@NodeInfo(cycles = CYCLES_8, cyclesRationale = "tlab alloc + header init", size = SIZE_8)
public class NewArrayWithExceptionNode extends AllocateWithExceptionNode {

    public static final NodeClass<NewArrayWithExceptionNode> TYPE = NodeClass.create(NewArrayWithExceptionNode.class);
    private final ResolvedJavaType elementType;
    final boolean fillContents;

    public NewArrayWithExceptionNode(ResolvedJavaType elementType, ValueNode length, boolean fillContents) {
        super(TYPE, StampFactory.objectNonNull(TypeReference.createExactTrusted(elementType.getArrayClass())));
        this.elementType = elementType;
        this.length = length;
        this.fillContents = fillContents;
    }

    public NewArrayWithExceptionNode(ResolvedJavaType elementType, ValueNode length, boolean fillContents, FrameState stateBefore, FrameState stateAfter) {
        super(TYPE, StampFactory.objectNonNull(TypeReference.createExactTrusted(elementType.getArrayClass())));
        this.elementType = elementType;
        this.length = length;
        this.fillContents = fillContents;
        this.stateBefore = stateBefore;
        this.stateAfter = stateAfter;
    }

    @Input protected ValueNode length;

    public ValueNode length() {
        return length;
    }

    public ResolvedJavaType elementType() {
        return elementType;
    }

    @Override
    public boolean fillContents() {
        return fillContents;
    }

    @Override
    public FixedNode replaceWithNonThrowing() {
        killExceptionEdge();
        NewArrayNode newArray = graph().add(new NewArrayNode(elementType, length, fillContents, stateBefore));
        graph().replaceSplitWithFixed(this, newArray, this.next());
        // copy across any original node source position
        newArray.setNodeSourcePosition(getNodeSourcePosition());
        return newArray;
    }
}
