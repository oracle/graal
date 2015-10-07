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

import com.oracle.graal.compiler.common.type.StampFactory;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.ConstantNode;
import com.oracle.graal.nodes.FixedWithNextNode;
import com.oracle.graal.nodes.ValueNode;

import jdk.internal.jvmci.common.JVMCIError;
import jdk.internal.jvmci.meta.JavaConstant;

@NodeInfo
public final class InstrumentationBeginNode extends FixedWithNextNode {

    public static final NodeClass<InstrumentationBeginNode> TYPE = NodeClass.create(InstrumentationBeginNode.class);

    private final int offset;

    public InstrumentationBeginNode(ValueNode offset) {
        super(TYPE, StampFactory.forVoid());

        if (!(offset instanceof ConstantNode)) {
            throw JVMCIError.shouldNotReachHere("should pass constant integer to instrumentationBegin(int)");
        }

        ConstantNode constantNode = (ConstantNode) offset;
        JavaConstant constant = (JavaConstant) constantNode.asConstant();
        this.offset = constant.asInt();
    }

    public int getOffset() {
        return offset;
    }

}
