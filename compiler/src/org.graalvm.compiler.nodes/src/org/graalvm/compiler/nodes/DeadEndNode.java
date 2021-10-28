/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes;

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_UNKNOWN;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_UNKNOWN;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.IterableNodeType;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.spi.Lowerable;

/**
 * Control sink that should be never be reached because the previous node will never follow the edge
 * that leads to this node. This node should be used in cases where the sink is needed to keep the
 * graph valid. For example a {@link InvokeWithExceptionNode} that will always enter the
 * {@link InvokeWithExceptionNode#exceptionEdge()}. In cases where the entire branch should be
 * removed, {@link UnreachableBeginNode} and {@link UnreachableControlSinkNode} might be more
 * appropriate.
 *
 * Use {@link UnreachableNode} to mark a code path in snippets as unreachable.
 */
@NodeInfo(cycles = CYCLES_UNKNOWN, size = SIZE_UNKNOWN, cyclesRationale = DeadEndNode.RATIONALE, sizeRationale = DeadEndNode.RATIONALE)
public final class DeadEndNode extends ControlSinkNode implements Lowerable, IterableNodeType {
    public static final NodeClass<DeadEndNode> TYPE = NodeClass.create(DeadEndNode.class);
    static final String RATIONALE = "Unknown whether this is lowered to a no-op or to code that provides further diagnostics, e.g., via a foreign call";

    public DeadEndNode() {
        super(TYPE, StampFactory.forVoid());
    }
}
