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
package com.oracle.graal.nodes.debug.instrumentation;

import com.oracle.graal.compiler.common.type.StampFactory;
import com.oracle.graal.debug.GraalError;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.nodeinfo.InputType;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.ConstantNode;
import com.oracle.graal.nodes.FixedWithNextNode;
import com.oracle.graal.nodes.ValueNode;

import jdk.vm.ci.meta.JavaConstant;

/**
 * The {@code InstrumentationBeginNode} represents the boundary of the instrumentation. It also
 * maintains the target of the instrumentation.
 */
@NodeInfo
public final class InstrumentationBeginNode extends FixedWithNextNode {

    public static final NodeClass<InstrumentationBeginNode> TYPE = NodeClass.create(InstrumentationBeginNode.class);

    @OptionalInput(value = InputType.Association) protected ValueNode target;
    private final int offset;

    /**
     * @param offset denotes the bytecode offset between the target and the instrumentation, and is
     *            required to be a ConstantNode.
     */
    public InstrumentationBeginNode(ValueNode offset) {
        super(TYPE, StampFactory.forVoid());
        // resolve the constant integer from the input
        if (!(offset instanceof ConstantNode)) {
            throw GraalError.shouldNotReachHere("should pass constant integer to instrumentationBegin(int)");
        }
        JavaConstant constant = ((ConstantNode) offset).asJavaConstant();
        this.offset = constant == null ? 0 : constant.asInt();
        this.target = null;
    }

    public int offset() {
        return offset;
    }

    public ValueNode target() {
        return target;
    }

    public void setTarget(ValueNode target) {
        this.target = target;
    }

}
