/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.hotspot.replacements.query;

import jdk.internal.jvmci.hotspot.HotSpotResolvedObjectType;
import jdk.internal.jvmci.meta.Constant;
import jdk.internal.jvmci.meta.ConstantReflectionProvider;
import jdk.internal.jvmci.meta.ResolvedJavaMethod;

import com.oracle.graal.compiler.common.type.StampFactory;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.ConstantNode;
import com.oracle.graal.nodes.FixedNode;
import com.oracle.graal.phases.common.query.nodes.GraalQueryNode;
import com.oracle.graal.phases.common.query.nodes.InstrumentationNode;

@NodeInfo
public final class GetRootNameNode extends GraalQueryNode {

    public static final NodeClass<GetRootNameNode> TYPE = NodeClass.create(GetRootNameNode.class);

    public GetRootNameNode() {
        super(TYPE, StampFactory.exactNonNull(HotSpotResolvedObjectType.fromObjectClass(String.class)));
    }

    @Override
    public void onInlineICG(InstrumentationNode instrumentation, FixedNode position, ConstantReflectionProvider constantReflection) {
        ResolvedJavaMethod method = graph().method();
        String root = method.getDeclaringClass().toJavaName() + "." + method.getName() + method.getSignature().toMethodDescriptor();
        Constant constant = constantReflection.forObject(root);
        ConstantNode constantNode = graph().unique(new ConstantNode(constant, stamp()));
        graph().replaceFixedWithFloating(this, constantNode);
    }

    @NodeIntrinsic
    public static native String instantiate();

}
