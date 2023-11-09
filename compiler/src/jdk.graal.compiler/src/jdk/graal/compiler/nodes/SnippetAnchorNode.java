/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.graal.compiler.nodeinfo.InputType.Anchor;
import static jdk.graal.compiler.nodeinfo.InputType.Guard;
import static jdk.graal.compiler.nodeinfo.InputType.Value;
import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_0;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_0;

import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.extended.AnchoringNode;
import jdk.graal.compiler.nodes.extended.GuardingNode;
import jdk.graal.compiler.nodes.spi.Simplifiable;
import jdk.graal.compiler.nodes.spi.SimplifierTool;

@NodeInfo(allowedUsageTypes = {Value, Anchor, Guard}, cycles = CYCLES_0, size = SIZE_0)
public final class SnippetAnchorNode extends FixedWithNextNode implements Simplifiable, GuardingNode, AnchoringNode {
    public static final NodeClass<SnippetAnchorNode> TYPE = NodeClass.create(SnippetAnchorNode.class);

    public SnippetAnchorNode() {
        super(TYPE, StampFactory.object());
    }

    @Override
    public void simplify(SimplifierTool tool) {
        AbstractBeginNode prevBegin = AbstractBeginNode.prevBegin(this);
        replaceAtUsages(prevBegin, Anchor);
        replaceAtUsages(prevBegin, Guard);
        if (tool.allUsagesAvailable() && hasNoUsages()) {
            graph().removeFixed(this);
        }
    }

    @NodeIntrinsic
    public static native GuardingNode anchor();
}
