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
package jdk.graal.compiler.nodes;

import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeCycles;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodeinfo.NodeSize;
import jdk.graal.compiler.nodes.memory.MemoryKill;
import jdk.graal.compiler.nodes.memory.SingleMemoryKill;
import org.graalvm.word.LocationIdentity;

/**
 * Allows to build control flow structures that are syntactically correct (can be processed by all
 * Graal phases) but known to be unreachable, i.e., known to be removed at a later point in the
 * compilation pipeline. Useful together with {@link UnreachableControlSinkNode}.
 */
@NodeInfo(cycles = NodeCycles.CYCLES_IGNORED, size = NodeSize.SIZE_IGNORED)
public final class UnreachableBeginNode extends AbstractBeginNode implements SingleMemoryKill {

    public static final NodeClass<UnreachableBeginNode> TYPE = NodeClass.create(UnreachableBeginNode.class);

    public UnreachableBeginNode() {
        super(TYPE);
    }

    /**
     * Determine which memory location is killed by this node. Since this node can be used on the
     * {@linkplain WithExceptionNode#exceptionEdge() exception edge} of an {@link WithExceptionNode}
     * that might also be a {@linkplain MemoryKill memory kill}, this node must be a memory kill as
     * well. Since the branch is unreachable and will be deleted eventually, killing
     * {@link LocationIdentity#any()} do not cause issues with respect to optimizations.
     */
    @Override
    public LocationIdentity getKilledLocationIdentity() {
        return LocationIdentity.any();
    }
}
