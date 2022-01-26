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
package org.graalvm.compiler.nodes.java;

import org.graalvm.compiler.core.common.type.AbstractPointerStamp;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodes.spi.Simplifiable;
import org.graalvm.compiler.nodes.spi.SimplifierTool;
import org.graalvm.compiler.nodeinfo.NodeCycles;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodeinfo.NodeSize;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.WithExceptionNode;
import org.graalvm.compiler.nodes.spi.Lowerable;

/**
 * This node represents the class type checks for a {@link DynamicNewInstanceNode} and throws any
 * needed exceptions. An exception can occur if the provided
 * {@link ValidateNewInstanceClassNode#clazz} is null or of a primitive, array, interface, abstract,
 * or {@link Class} type.
 */
@NodeInfo(size = NodeSize.SIZE_8, cycles = NodeCycles.CYCLES_8, cyclesRationale = "Performs multiple checks.")
public final class ValidateNewInstanceClassNode extends WithExceptionNode implements Lowerable, Simplifiable {

    @Input ValueNode clazz;

    /**
     * Class pointer to class.class needs to be exposed earlier than this node is lowered so that it
     * can be replaced by the AOT machinery. If it's not needed for lowering this input can be
     * ignored.
     */
    @OptionalInput ValueNode classClass;

    public static final NodeClass<ValidateNewInstanceClassNode> TYPE = NodeClass.create(ValidateNewInstanceClassNode.class);

    protected ValidateNewInstanceClassNode(ValueNode clazz) {
        super(TYPE, AbstractPointerStamp.pointerNonNull(clazz.stamp(NodeView.DEFAULT)));
        this.clazz = clazz;
    }

    public ValueNode getInstanceType() {
        return clazz;
    }

    public ValueNode getClassClass() {
        return classClass;
    }

    public void setClassClass(ValueNode newClassClass) {
        updateUsages(classClass, newClassClass);
        classClass = newClassClass;
    }

    @Override
    public void simplify(SimplifierTool tool) {
        if (DynamicNewInstanceNode.tryConvertToNonDynamic(clazz, tool) != null) {
            killExceptionEdge();
            tool.addToWorkList(usages());
            replaceAtUsages(clazz);
            graph().removeSplit(this, next());
        }
    }
}
