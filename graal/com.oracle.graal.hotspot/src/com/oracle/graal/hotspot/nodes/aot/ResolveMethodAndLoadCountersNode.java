/*
 * Copyright (c) 2009, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.nodes.aot;

import jdk.vm.ci.meta.ResolvedJavaMethod;

import static com.oracle.graal.nodeinfo.NodeCycles.CYCLES_3;
import static com.oracle.graal.nodeinfo.NodeSize.SIZE_20;

import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.hotspot.nodes.type.MethodCountersPointerStamp;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.calc.FloatingNode;
import com.oracle.graal.nodes.spi.Lowerable;
import com.oracle.graal.nodes.spi.LoweringTool;

@NodeInfo(cycles = CYCLES_3, size = SIZE_20)
public class ResolveMethodAndLoadCountersNode extends FloatingNode implements Lowerable {
    public static final NodeClass<ResolveMethodAndLoadCountersNode> TYPE = NodeClass.create(ResolveMethodAndLoadCountersNode.class);

    ResolvedJavaMethod method;
    @Input ValueNode hub;

    public ResolveMethodAndLoadCountersNode(ResolvedJavaMethod method, ValueNode hub) {
        super(TYPE, MethodCountersPointerStamp.methodCountersNonNull());
        this.method = method;
        this.hub = hub;
    }

    @Override
    public void lower(LoweringTool tool) {
        tool.getLowerer().lower(this, tool);
    }

    public ResolvedJavaMethod getMethod() {
        return method;
    }

    public ValueNode getHub() {
        return hub;
    }
}
