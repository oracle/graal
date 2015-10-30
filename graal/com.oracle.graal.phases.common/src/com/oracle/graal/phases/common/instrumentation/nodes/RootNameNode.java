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
package com.oracle.graal.phases.common.instrumentation.nodes;

import com.oracle.graal.compiler.common.type.Stamp;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.FixedNode;
import com.oracle.graal.nodes.debug.RuntimeStringNode;
import com.oracle.graal.nodes.spi.Lowerable;
import com.oracle.graal.nodes.spi.LoweringTool;

import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.ResolvedJavaMethod;

@NodeInfo
public final class RootNameNode extends InstrumentationContentNode implements Lowerable {

    public static final NodeClass<RootNameNode> TYPE = NodeClass.create(RootNameNode.class);

    public RootNameNode(Stamp stamp) {
        super(TYPE, stamp);
    }

    public static String genRootName(ResolvedJavaMethod method) {
        if (method == null) {
            return "<unresolved method>";
        }
        return method.getDeclaringClass().toJavaName() + "." + method.getName() + method.getSignature().toMethodDescriptor();
    }

    public void lower(LoweringTool tool) {
        RuntimeStringNode runtimeString = graph().add(new RuntimeStringNode(genRootName(graph().method()), stamp()));
        graph().replaceFixedWithFixed(this, runtimeString);
    }

    @Override
    public void onInlineInstrumentation(InstrumentationNode instrumentation, FixedNode position) {
        throw JVMCIError.shouldNotReachHere("RootNameNode must be replaced before inlining an instrumentation");
    }

}
