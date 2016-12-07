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

import static com.oracle.graal.nodeinfo.NodeCycles.CYCLES_IGNORED;
import static com.oracle.graal.nodeinfo.NodeSize.SIZE_IGNORED;

import com.oracle.graal.compiler.common.type.StampFactory;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.nodeinfo.InputType;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.AbstractStateSplit;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.spi.LIRLowerable;
import com.oracle.graal.nodes.spi.NodeLIRBuilderTool;

/**
 * The {@code InstrumentationBeginNode} represents the boundary of the instrumentation. It also
 * maintains the target of the instrumentation.
 */
@NodeInfo(cycles = CYCLES_IGNORED, size = SIZE_IGNORED)
public final class InstrumentationBeginNode extends AbstractStateSplit implements LIRLowerable {

    public static final NodeClass<InstrumentationBeginNode> TYPE = NodeClass.create(InstrumentationBeginNode.class);

    @OptionalInput(value = InputType.Unchecked) protected ValueNode target;
    private final boolean anchored;

    public InstrumentationBeginNode(boolean anchored) {
        super(TYPE, StampFactory.forVoid());
        this.anchored = anchored;
        this.target = null;

    }

    public boolean isAnchored() {
        return anchored;
    }

    public ValueNode getTarget() {
        return target;
    }

    public void setTarget(ValueNode target) {
        updateUsages(this.target, target);
        this.target = target;
    }

    @Override
    public void generate(NodeLIRBuilderTool generator) {
        // do nothing
    }

}
