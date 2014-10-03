/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.nodes.debug;

import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;

/**
 * This node can be used to add a counter to the code that will estimate the dynamic number of calls
 * by adding an increment to the compiled code. This should of course only be used for
 * debugging/testing purposes.
 *
 * A unique counter will be created for each unique name passed to the constructor. Depending on the
 * value of withContext, the name of the root method is added to the counter's name.
 */
@NodeInfo
public class DynamicCounterNode extends FixedWithNextNode implements Lowerable {

    @Input ValueNode increment;

    protected String name;
    protected String group;
    protected boolean withContext;

    public static DynamicCounterNode create(String name, String group, ValueNode increment, boolean withContext) {
        return USE_GENERATED_NODES ? new DynamicCounterNodeGen(name, group, increment, withContext) : new DynamicCounterNode(name, group, increment, withContext);
    }

    protected DynamicCounterNode(String name, String group, ValueNode increment, boolean withContext) {
        super(StampFactory.forVoid());
        this.name = name;
        this.group = group;
        this.increment = increment;
        this.withContext = withContext;
    }

    public ValueNode getIncrement() {
        return increment;
    }

    public String getName() {
        return name;
    }

    public String getGroup() {
        return group;
    }

    public boolean isWithContext() {
        return withContext;
    }

    @Override
    public void lower(LoweringTool tool) {
        tool.getLowerer().lower(this, tool);
    }

    public static void addCounterBefore(String group, String name, long increment, boolean withContext, FixedNode position) {
        StructuredGraph graph = position.graph();
        graph.addBeforeFixed(position, position.graph().add(DynamicCounterNode.create(name, group, ConstantNode.forLong(increment, position.graph()), withContext)));
    }

    @NodeIntrinsic
    public static native void counter(@ConstantNodeParameter String name, @ConstantNodeParameter String group, long increment, @ConstantNodeParameter boolean addContext);

}
