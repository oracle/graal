/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.nodes;

import jdk.compiler.graal.core.common.type.StampFactory;
import jdk.compiler.graal.graph.NodeClass;
import jdk.compiler.graal.nodeinfo.InputType;
import jdk.compiler.graal.nodeinfo.NodeCycles;
import jdk.compiler.graal.nodeinfo.NodeInfo;
import jdk.compiler.graal.nodeinfo.NodeSize;
import jdk.compiler.graal.nodes.AbstractStateSplit;
import jdk.compiler.graal.nodes.DeoptimizingNode;
import jdk.compiler.graal.nodes.memory.SingleMemoryKill;
import jdk.compiler.graal.nodes.spi.Lowerable;
import org.graalvm.word.LocationIdentity;

import com.oracle.svm.core.graal.snippets.DeoptTester;

/**
 * Inserted for deoptimization stress test. This node is lowered to a runtime call which calls
 * {@link DeoptTester#deoptTest()}.
 */
@NodeInfo(allowedUsageTypes = InputType.Memory, cycles = NodeCycles.CYCLES_IGNORED, size = NodeSize.SIZE_IGNORED)
public final class DeoptTestNode extends AbstractStateSplit implements Lowerable, DeoptimizingNode.DeoptAfter, SingleMemoryKill {
    public static final NodeClass<DeoptTestNode> TYPE = NodeClass.create(DeoptTestNode.class);

    public DeoptTestNode() {
        super(TYPE, StampFactory.forVoid());
    }

    @Override
    public LocationIdentity getKilledLocationIdentity() {
        return LocationIdentity.any();
    }

    @Override
    public boolean canDeoptimize() {
        return true;
    }
}
