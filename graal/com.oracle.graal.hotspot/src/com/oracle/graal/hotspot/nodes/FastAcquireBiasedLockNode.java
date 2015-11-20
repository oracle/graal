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
package com.oracle.graal.hotspot.nodes;

import com.oracle.graal.compiler.common.type.StampFactory;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.FixedWithNextNode;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.spi.LIRLowerable;
import com.oracle.graal.nodes.spi.NodeLIRBuilderTool;

/**
 * Marks the control flow path where an object acquired a biased lock because the lock was already
 * biased to the object on the current thread.
 */
@NodeInfo
public final class FastAcquireBiasedLockNode extends FixedWithNextNode implements LIRLowerable {
    public static final NodeClass<FastAcquireBiasedLockNode> TYPE = NodeClass.create(FastAcquireBiasedLockNode.class);

    @Input ValueNode object;

    public FastAcquireBiasedLockNode(ValueNode object) {
        super(TYPE, StampFactory.forVoid());
        this.object = object;
    }

    public ValueNode object() {
        return object;
    }

    public void generate(NodeLIRBuilderTool generator) {
        // This is just a marker node so it generates nothing
    }

    @NodeIntrinsic
    public static native void mark(Object object);
}
