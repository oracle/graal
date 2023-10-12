/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022, 2022, Alibaba Group Holding Limited. All rights reserved.
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

package com.oracle.svm.common;

import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.ResolvedJavaType;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.StateSplit;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.WithExceptionNode;
import org.graalvm.compiler.nodes.memory.SingleMemoryKill;
import org.graalvm.compiler.nodes.type.StampTool;
import org.graalvm.word.LocationIdentity;

/**
 * This class is only used for class initializer analyzing. It should never be lowered at compile
 * time.
 */
@NodeInfo
public class ClassInitializationNode extends WithExceptionNode implements SingleMemoryKill, StateSplit {
    public static final NodeClass<ClassInitializationNode> TYPE = NodeClass.create(ClassInitializationNode.class);

    @Input private ValueNode hub;
    @Input(InputType.State) protected FrameState stateAfter;

    public ClassInitializationNode(ValueNode hub, FrameState stateAfter) {
        this(TYPE, hub, stateAfter);
    }

    public ClassInitializationNode(NodeClass<? extends WithExceptionNode> c, ValueNode hub, FrameState stateAfter) {
        super(c, StampFactory.forVoid());
        this.hub = hub;
        assert StampTool.isPointerNonNull(hub) : "Hub must already be null-checked";
        this.stateAfter = stateAfter;
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

    public ResolvedJavaType constantTypeOrNull(ConstantReflectionProvider constantReflection) {
        if (hub.isConstant()) {
            return constantReflection.asJavaType(hub.asConstant());
        } else {
            return null;
        }
    }

    public ValueNode getHub() {
        return hub;
    }
}
