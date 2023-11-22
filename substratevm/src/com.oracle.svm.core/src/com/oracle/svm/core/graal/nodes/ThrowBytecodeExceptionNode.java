/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.nodes;

import static jdk.graal.compiler.nodeinfo.InputType.State;
import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_2;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_2;

import java.util.List;

import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.NodeInputList;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ControlSinkNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.UnwindNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.extended.BytecodeExceptionNode;
import jdk.graal.compiler.nodes.extended.BytecodeExceptionNode.BytecodeExceptionKind;
import jdk.graal.compiler.nodes.spi.Lowerable;
import jdk.graal.compiler.nodes.spi.NodeWithState;

/**
 * Throw an implicit exception. In contrast to {@link BytecodeExceptionNode}, this node does not
 * return the exception. Instead it throws the exception, i.e., this node does not have a successor.
 * It is used to simplify the graph structure when the result of a {@link BytecodeExceptionNode}
 * would directly feed into a {@link UnwindNode} anyway.
 */
@NodeInfo(cycles = CYCLES_2, cyclesRationale = "Node will be lowered to a foreign call.", size = SIZE_2)
public final class ThrowBytecodeExceptionNode extends ControlSinkNode implements NodeWithState, Lowerable {
    public static final NodeClass<ThrowBytecodeExceptionNode> TYPE = NodeClass.create(ThrowBytecodeExceptionNode.class);

    private final BytecodeExceptionKind exceptionKind;
    private @Input NodeInputList<ValueNode> arguments;
    private @OptionalInput(State) FrameState stateBefore;

    public ThrowBytecodeExceptionNode(BytecodeExceptionKind exceptionKind, List<ValueNode> arguments) {
        super(TYPE, StampFactory.forVoid());
        this.exceptionKind = exceptionKind;
        this.arguments = new NodeInputList<>(this, arguments);
    }

    public BytecodeExceptionKind getExceptionKind() {
        return exceptionKind;
    }

    public NodeInputList<ValueNode> getArguments() {
        return arguments;
    }

    public FrameState stateBefore() {
        return stateBefore;
    }

    public void setStateBefore(FrameState f) {
        updateUsages(stateBefore, f);
        stateBefore = f;
    }
}
