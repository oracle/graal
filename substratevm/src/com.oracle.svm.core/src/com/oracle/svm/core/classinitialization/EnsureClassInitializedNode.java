/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.classinitialization;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.Node.NodeIntrinsicFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.Simplifiable;
import org.graalvm.compiler.graph.spi.SimplifierTool;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.NodeCycles;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodeinfo.NodeSize;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.StateSplit;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.WithExceptionNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.memory.SingleMemoryKill;
import org.graalvm.compiler.nodes.spi.Lowerable;
import org.graalvm.compiler.nodes.type.StampTool;
import org.graalvm.word.LocationIdentity;

import jdk.vm.ci.meta.ResolvedJavaType;

@NodeInfo(size = NodeSize.SIZE_16, cycles = NodeCycles.CYCLES_2, cyclesRationale = "Class initialization only runs at most once at run time, so the amortized cost is only the is-initialized check")
@NodeIntrinsicFactory
public class EnsureClassInitializedNode extends WithExceptionNode implements Simplifiable, StateSplit, SingleMemoryKill, Lowerable {

    public static final NodeClass<EnsureClassInitializedNode> TYPE = NodeClass.create(EnsureClassInitializedNode.class);

    @Input private ValueNode hub;
    @Input(InputType.State) private FrameState stateAfter;

    public static boolean intrinsify(GraphBuilderContext b, ValueNode hub) {
        b.add(new EnsureClassInitializedNode(b.nullCheckedValue(hub)));
        return true;
    }

    public EnsureClassInitializedNode(ValueNode hub) {
        super(TYPE, StampFactory.forVoid());
        this.hub = hub;
        assert StampTool.isPointerNonNull(hub) : "Hub must already be null-checked";
    }

    public ValueNode getHub() {
        return hub;
    }

    @Override
    public LocationIdentity getKilledLocationIdentity() {
        return LocationIdentity.any();
    }

    @Override
    public FrameState stateAfter() {
        return stateAfter;
    }

    @Override
    public void setStateAfter(FrameState stateAfter) {
        updateUsages(this.stateAfter, stateAfter);
        this.stateAfter = stateAfter;
    }

    @Override
    public boolean hasSideEffect() {
        return true;
    }

    @Override
    public void simplify(SimplifierTool tool) {
        if (hub.isConstant()) {
            ResolvedJavaType type = tool.getConstantReflection().asJavaType(hub.asConstant());
            if (type != null && type.isInitialized()) {
                killExceptionEdge();
                graph().removeSplit(this, next());
                return;
            }
        }
    }

    @NodeIntrinsic
    public static native void ensureClassInitialized(Class<?> clazz);
}
