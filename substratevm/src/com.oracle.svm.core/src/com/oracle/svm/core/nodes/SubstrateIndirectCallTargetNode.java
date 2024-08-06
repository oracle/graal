/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.nodes;

import jdk.graal.compiler.core.common.type.StampPair;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.IndirectCallTargetNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.meta.JavaMethodProfile;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaMethod;

@NodeInfo
public class SubstrateIndirectCallTargetNode extends IndirectCallTargetNode {
    public static final NodeClass<SubstrateIndirectCallTargetNode> TYPE = NodeClass.create(SubstrateIndirectCallTargetNode.class);

    private final JavaMethodProfile methodProfile;

    public SubstrateIndirectCallTargetNode(ValueNode computedAddress, ValueNode[] arguments, StampPair returnStamp, JavaType[] signature, ResolvedJavaMethod target,
                    CallingConvention.Type callType, InvokeKind invokeKind, JavaMethodProfile methodProfile) {
        this(TYPE, computedAddress, arguments, returnStamp, signature, target, callType, invokeKind, methodProfile);
    }

    public SubstrateIndirectCallTargetNode(ValueNode computedAddress, ValueNode[] arguments, StampPair returnStamp, JavaType[] signature, ResolvedJavaMethod target,
                    CallingConvention.Type callType, InvokeKind invokeKind) {
        this(TYPE, computedAddress, arguments, returnStamp, signature, target, callType, invokeKind, null);
    }

    protected SubstrateIndirectCallTargetNode(NodeClass<? extends SubstrateIndirectCallTargetNode> type, ValueNode computedAddress, ValueNode[] arguments, StampPair returnStamp,
                    JavaType[] signature, ResolvedJavaMethod target, CallingConvention.Type callType, InvokeKind invokeKind, JavaMethodProfile methodProfile) {
        super(type, computedAddress, arguments, returnStamp, signature, target, callType, invokeKind);
        this.methodProfile = methodProfile;
    }

    public JavaMethodProfile getMethodProfile() {
        return methodProfile;
    }
}
