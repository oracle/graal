/*
 * Copyright (c) 2012, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.compiler.graal.hotspot.nodes;

import static jdk.compiler.graal.nodeinfo.NodeSize.SIZE_1;

import jdk.compiler.graal.core.common.type.StampFactory;
import jdk.compiler.graal.graph.Node;
import jdk.compiler.graal.graph.NodeClass;
import jdk.compiler.graal.lir.VirtualStackSlot;
import jdk.compiler.graal.nodeinfo.NodeCycles;
import jdk.compiler.graal.nodeinfo.NodeInfo;
import jdk.compiler.graal.nodes.calc.FloatingNode;
import jdk.compiler.graal.nodes.spi.LIRLowerable;
import jdk.compiler.graal.nodes.spi.NodeLIRBuilderTool;
import jdk.compiler.graal.word.Word;
import jdk.compiler.graal.word.WordTypes;

import jdk.vm.ci.meta.Value;

/**
 * Node that is used to maintain a stack based counter of how many locks are currently held.
 */
@NodeInfo(cycles = NodeCycles.CYCLES_2, size = SIZE_1)
public final class MonitorCounterNode extends FloatingNode implements LIRLowerable, Node.ValueNumberable {
    public static final NodeClass<MonitorCounterNode> TYPE = NodeClass.create(MonitorCounterNode.class);

    public MonitorCounterNode(@InjectedNodeParameter WordTypes wordTypes) {
        super(TYPE, StampFactory.forKind(wordTypes.getWordKind()));
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        assert graph().getNodes().filter(MonitorCounterNode.class).count() == 1 : "monitor counters not canonicalized to single instance";
        VirtualStackSlot counter = gen.getLIRGeneratorTool().allocateStackMemory(Integer.BYTES, Integer.BYTES);
        Value result = gen.getLIRGeneratorTool().emitAddress(counter);
        gen.setResult(this, result);
    }

    @NodeIntrinsic
    public static native Word counter();
}
