/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.replacements;

import static jdk.graal.compiler.nodeinfo.InputType.Memory;
import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_64;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_16;

import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.AbstractStateSplit;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.memory.MemoryAccess;
import jdk.graal.compiler.nodes.memory.MemoryKill;
import jdk.graal.compiler.nodes.memory.SingleMemoryKill;
import jdk.graal.compiler.nodes.spi.Lowerable;
import org.graalvm.word.LocationIdentity;

@NodeInfo(cycles = CYCLES_64, size = SIZE_16, allowedUsageTypes = {Memory})
public class UnsafeCopyMemoryNode extends AbstractStateSplit implements Lowerable, SingleMemoryKill, MemoryAccess {

    public static final NodeClass<UnsafeCopyMemoryNode> TYPE = NodeClass.create(UnsafeCopyMemoryNode.class);

    @Input ValueNode receiver;
    @Input ValueNode srcBase;
    @Input ValueNode srcOffset;
    @Input ValueNode destBase;
    @Input ValueNode desOffset;
    @Input ValueNode bytes;

    @OptionalInput(Memory) MemoryKill lastLocationAccess;

    public UnsafeCopyMemoryNode(ValueNode receiver, ValueNode srcBase, ValueNode srcOffset, ValueNode destBase, ValueNode desOffset,
                    ValueNode bytes) {
        super(TYPE, StampFactory.forVoid());
        this.receiver = receiver;
        this.srcBase = srcBase;
        this.srcOffset = srcOffset;
        this.destBase = destBase;
        this.desOffset = desOffset;
        this.bytes = bytes;
    }

    @Override
    public LocationIdentity getKilledLocationIdentity() {
        return LocationIdentity.any();
    }

    @Override
    public LocationIdentity getLocationIdentity() {
        return getKilledLocationIdentity();
    }

    @Override
    public MemoryKill getLastLocationAccess() {
        return lastLocationAccess;
    }

    @Override
    public void setLastLocationAccess(MemoryKill lla) {
        updateUsagesInterface(lastLocationAccess, lla);
        lastLocationAccess = lla;
    }
}
