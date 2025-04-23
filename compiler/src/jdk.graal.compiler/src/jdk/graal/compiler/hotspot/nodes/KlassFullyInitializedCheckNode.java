/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.nodes;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_4;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_16;

import org.graalvm.word.LocationIdentity;

import jdk.graal.compiler.core.common.type.AbstractPointerStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.DeoptimizingFixedWithNextNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.memory.SingleMemoryKill;
import jdk.graal.compiler.nodes.spi.Lowerable;

/**
 * Checks that the input {@link Class}'s hub is either null or has been fully initialized, or
 * deoptimizes otherwise.
 */
@NodeInfo(allowedUsageTypes = {InputType.Memory}, cycles = CYCLES_4, size = SIZE_16)
public class KlassFullyInitializedCheckNode extends DeoptimizingFixedWithNextNode implements Lowerable, SingleMemoryKill {
    public static final NodeClass<KlassFullyInitializedCheckNode> TYPE = NodeClass.create(KlassFullyInitializedCheckNode.class);

    @Input protected ValueNode klass;

    public KlassFullyInitializedCheckNode(ValueNode klassNonNull) {
        super(TYPE, StampFactory.forVoid());
        Stamp inputStamp = klassNonNull.stamp(NodeView.DEFAULT);
        assert inputStamp instanceof AbstractPointerStamp : klassNonNull + " has wrong input stamp type for klass init state check: " + inputStamp;
        assert ((AbstractPointerStamp) inputStamp).nonNull() : klassNonNull + " must have non-null stamp: " + inputStamp;
        this.klass = klassNonNull;
    }

    @Override
    public LocationIdentity getKilledLocationIdentity() {
        /*
         * Since JDK-8338379, reading the class init state requires an ACQUIRE barrier, which orders
         * memory accesses
         */
        return LocationIdentity.ANY_LOCATION;
    }

    public ValueNode getKlass() {
        return klass;
    }

    @Override
    public boolean canDeoptimize() {
        return true;
    }

}
