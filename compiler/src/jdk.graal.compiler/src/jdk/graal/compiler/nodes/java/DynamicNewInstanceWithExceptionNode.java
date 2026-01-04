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
import static jdk.graal.compiler.nodes.java.DynamicNewInstanceNode.tryConvertToNonDynamic;

import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.vm.ci.meta.ResolvedJavaType;

@NodeInfo(cycles = CYCLES_8, cyclesRationale = "tlab alloc + header init", size = SIZE_8)
public class DynamicNewInstanceWithExceptionNode extends AllocateWithExceptionNode implements Canonicalizable {
    public static final NodeClass<DynamicNewInstanceWithExceptionNode> TYPE = NodeClass.create(DynamicNewInstanceWithExceptionNode.class);

    @Input ValueNode clazz;
    protected boolean fillContents;
    private final boolean originUnsafeAllocateInstance;

    public DynamicNewInstanceWithExceptionNode(ValueNode clazz, boolean fillContents) {
        this(clazz, fillContents, false);
    }

    public DynamicNewInstanceWithExceptionNode(ValueNode clazz, boolean fillContents, boolean originUnsafeAllocateInstance) {
        super(TYPE, StampFactory.objectNonNull());
        this.fillContents = fillContents;
        this.clazz = clazz;
        this.originUnsafeAllocateInstance = originUnsafeAllocateInstance;
    }

    public ValueNode getInstanceType() {
        return clazz;
    }

    public boolean isOriginUnsafeAllocateInstance() {
        return originUnsafeAllocateInstance;
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        ResolvedJavaType type = tryConvertToNonDynamic(clazz, tool);
        if (type != null) {
            return new NewInstanceWithExceptionNode(type, fillContents, stateBefore, stateAfter);
        }
        return this;
    }

    @Override
    public FixedNode replaceWithNonThrowing() {
        killExceptionEdge();
        DynamicNewInstanceNode newInstance = graph().add(new DynamicNewInstanceNode(clazz, fillContents, originUnsafeAllocateInstance));
        newInstance.setStateBefore(stateBefore);
        graph().replaceSplitWithFixed(this, newInstance, this.next());
        // copy across any original node source position
        newInstance.setNodeSourcePosition(getNodeSourcePosition());
        return newInstance;
    }
}
