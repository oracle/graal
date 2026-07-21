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

import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.IterableNodeType;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.spi.NodeWithIdentity;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.LoopBeginNode;

@NodeInfo(allowedUsageTypes = {InputType.Anchor, InputType.Guard}, cycles = CYCLES_IGNORED, size = SIZE_IGNORED)
public final class DuplicationAnchorNode extends FixedWithNextNode implements IterableNodeType, NodeWithIdentity {

    public static final NodeClass<DuplicationAnchorNode> TYPE = NodeClass.create(DuplicationAnchorNode.class);

    public enum DuplicationAnchorType {
        SlowPathEntry,
        DeoptSlowPathEntry, // currently unused
        SlowPathExit;

        /**
         * Return SlowPathEntry if at least one is a SlowPathEntry.
         */
        public DuplicationAnchorType meet(DuplicationAnchorType type) {
            assert this != SlowPathExit && type != SlowPathExit : this + " vs " + type;
            return this == SlowPathEntry || type == SlowPathEntry ? SlowPathEntry : DeoptSlowPathEntry;
        }
    }

    @Input(InputType.State) FrameState stateAfter;

    private final DuplicationAnchorNode.DuplicationAnchorType type;

    private Object loop;

    public DuplicationAnchorNode(DuplicationAnchorNode.DuplicationAnchorType type) {
        super(TYPE, StampFactory.forVoid());
        this.type = type;
    }

    public FrameState stateAfter() {
        return stateAfter;
    }

    public void setStateAfter(FrameState x) {
        updateUsages(stateAfter, x);
        stateAfter = x;
    }

    public DuplicationProxyNode getProxy(int index) {
        for (Node usage : usages()) {
            DuplicationProxyNode proxy = (DuplicationProxyNode) usage;
            if (proxy.getIndex() == index) {
                return proxy;
            }
        }
        return null;
    }

    public boolean isEntry() {
        return type == DuplicationAnchorNode.DuplicationAnchorType.SlowPathEntry || type == DuplicationAnchorNode.DuplicationAnchorType.DeoptSlowPathEntry;
    }

    public void setLoop(LoopBeginNode x) {
        this.loop = x;
    }

    public DuplicationAnchorNode.DuplicationAnchorType getType() {
        return type;
    }

    public Object getLoop() {
        return loop;
    }
}
