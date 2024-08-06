/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.replacements;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_UNKNOWN;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_UNKNOWN;

import jdk.graal.compiler.core.common.type.StampPair;
import jdk.graal.compiler.graph.IterableNodeType;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.CallTargetNode;
import jdk.graal.compiler.nodes.ValueNode;

import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Marker node for a call to the enclosing method of an intrinsic.
 */
//@formatter:off
@NodeInfo(cycles = CYCLES_UNKNOWN,
        cyclesRationale = "If this node is not optimized away it will be lowered to a call, which we cannot estimate",
        size = SIZE_UNKNOWN,
        sizeRationale = "If this node is not optimized away it will be lowered to a call, which we cannot estimate")
//@formatter:on
public class PartialIntrinsicCallTargetNode extends CallTargetNode implements IterableNodeType {
    public static final NodeClass<PartialIntrinsicCallTargetNode> TYPE = NodeClass.create(PartialIntrinsicCallTargetNode.class);
    private final String targetName;

    public PartialIntrinsicCallTargetNode(CallTargetNode.InvokeKind invokeKind, ResolvedJavaMethod target, StampPair returnStamp, ValueNode... arguments) {
        super(TYPE, arguments, null, invokeKind, returnStamp);
        this.targetName = format(target);
    }

    @Override
    public String targetName() {
        return targetName;
    }

    public boolean checkName(ResolvedJavaMethod original) {
        assert targetName.equals(format(original)) : "expected " + targetName + " but got " + format(original);
        return true;
    }

    private static String format(ResolvedJavaMethod method) {
        return method.format("%H.%n(%p)");
    }
}
