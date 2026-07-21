/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.phases.common.priorityinline.nodes;

import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.NodeSourcePosition;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.Invoke;

@NodeInfo(nameTemplate = "Indirect #{p#targetMethod/s}")
public class IndirectNode extends CallTreeNode {
    public static final NodeClass<IndirectNode> TYPE = NodeClass.create(IndirectNode.class);

    public IndirectNode(NodeSourcePosition compilationRootPosition, Invoke invoke, double frequency) {
        super(TYPE, compilationRootPosition, invoke, frequency);
    }

    @Override
    public boolean enhanceParameters() {
        Invoke invoke = invoke();
        if (invoke == null || !invoke.asNode().isAlive()) {
            return false;
        }

        CallTreeNode direct = callTree().replaceWithDirectIfApplicable(this);
        if (direct != this) {
            callTree().restoreSubtreeInvariants(direct, false);
            return true;
        }

        return callTree().getPolicy().enhanceIndirectNode(this);
    }

    @Override
    public final void initializeCounts() {
        setCutoffCount(0);
        setActiveCutoffCount(0);
        setDeadendCount(1);
    }
}
