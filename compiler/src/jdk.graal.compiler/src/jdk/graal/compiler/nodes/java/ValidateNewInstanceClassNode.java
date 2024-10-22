/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.word.LocationIdentity;

import jdk.graal.compiler.core.common.type.AbstractPointerStamp;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeCycles;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodeinfo.NodeSize;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.WithExceptionNode;
import jdk.graal.compiler.nodes.memory.SingleMemoryKill;
import jdk.graal.compiler.nodes.spi.Lowerable;
import jdk.graal.compiler.nodes.spi.Simplifiable;
import jdk.graal.compiler.nodes.spi.SimplifierTool;

/**
 * This node represents the class type checks for a {@link DynamicNewInstanceNode} and throws any
 * needed exceptions. An exception can occur if the provided
 * {@link ValidateNewInstanceClassNode#clazz} is null or of a primitive, array, interface, abstract,
 * or {@link Class} type.
 */
@NodeInfo(size = NodeSize.SIZE_8, cycles = NodeCycles.CYCLES_8, cyclesRationale = "Performs multiple checks.")
public final class ValidateNewInstanceClassNode extends WithExceptionNode implements Lowerable, Simplifiable, SingleMemoryKill {

    @Input ValueNode clazz;

    /**
     * Class pointer to class.class needs to be exposed earlier than this node is lowered so that it
     * can be replaced by the AOT machinery. If it's not needed for lowering this input can be
     * ignored.
     */
    @OptionalInput ValueNode classClass;

    public static final NodeClass<ValidateNewInstanceClassNode> TYPE = NodeClass.create(ValidateNewInstanceClassNode.class);

    public ValidateNewInstanceClassNode(ValueNode clazz) {
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
    public LocationIdentity getKilledLocationIdentity() {
        return LocationIdentity.any();
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
