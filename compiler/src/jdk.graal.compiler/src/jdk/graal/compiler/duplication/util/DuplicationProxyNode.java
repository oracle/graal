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
package jdk.graal.compiler.duplication.util;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_IGNORED;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_IGNORED;

import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.FloatingNode;
import jdk.graal.compiler.nodes.extended.GuardingNode;

@NodeInfo(cycles = CYCLES_IGNORED, size = SIZE_IGNORED)
public final class DuplicationProxyNode extends FloatingNode implements GuardingNode {

    public static final NodeClass<DuplicationProxyNode> TYPE = NodeClass.create(DuplicationProxyNode.class);

    @Input(InputType.Association) DuplicationAnchorNode anchor;
    @Input(InputType.Unchecked) ValueNode original;

    private final boolean isGuard;
    private final int index;

    public DuplicationProxyNode(ValueNode original, DuplicationAnchorNode anchor, boolean isGuard) {
        super(TYPE, original.stamp(NodeView.DEFAULT));
        this.index = anchor.getUsageCount();
        this.original = original;
        this.anchor = anchor;
        this.isGuard = isGuard;
    }

    public boolean isGuard() {
        return isGuard;
    }

    public int getIndex() {
        return index;
    }

    public DuplicationAnchorNode getAnchor() {
        return anchor;
    }

    public ValueNode getOriginal() {
        return original;
    }
}
