/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.nodes.memory;

import jdk.graal.compiler.core.common.memory.BarrierType;
import jdk.graal.compiler.core.common.memory.MemoryOrderMode;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.memory.address.AddressNode;
import org.graalvm.word.LocationIdentity;

/**
 * This is a special form of write node that does not have a side effect to the interpreter, i.e.,
 * it does not modify memory that is visible to other threads or modifies state beyond what is
 * captured in {@link FrameState} nodes. Thus is should only be used with caution in suitable
 * scenarios.
 */
@NodeInfo(nameTemplate = "SideEffectFreeWrite#{p#location/s}")
public class SideEffectFreeWriteNode extends WriteNode {

    public static final NodeClass<SideEffectFreeWriteNode> TYPE = NodeClass.create(SideEffectFreeWriteNode.class);

    public SideEffectFreeWriteNode(AddressNode address, LocationIdentity location, ValueNode value, BarrierType barrierType) {
        super(TYPE, address, location, location, value, barrierType, MemoryOrderMode.PLAIN);
    }

    public static WriteNode createWithoutSideEffect(AddressNode address, LocationIdentity location, ValueNode value) {
        return new SideEffectFreeWriteNode(address, location, value, BarrierType.NONE);
    }

    @Override
    public boolean hasSideEffect() {
        return false;
    }

}
