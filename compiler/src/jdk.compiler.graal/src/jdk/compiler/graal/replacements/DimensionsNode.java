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
package jdk.compiler.graal.replacements;

import static jdk.compiler.graal.nodeinfo.NodeCycles.CYCLES_2;
import static jdk.compiler.graal.nodeinfo.NodeSize.SIZE_1;

import jdk.compiler.graal.core.common.type.StampFactory;
import jdk.compiler.graal.graph.NodeClass;
import jdk.compiler.graal.lir.VirtualStackSlot;
import jdk.compiler.graal.lir.gen.LIRGeneratorTool;
import jdk.compiler.graal.nodeinfo.NodeInfo;
import jdk.compiler.graal.nodes.FixedWithNextNode;
import jdk.compiler.graal.nodes.spi.LIRLowerable;
import jdk.compiler.graal.nodes.spi.NodeLIRBuilderTool;
import jdk.compiler.graal.word.Word;
import jdk.compiler.graal.word.WordTypes;

import jdk.vm.ci.meta.Value;

/**
 * Intrinsic for allocating an on-stack array of integers to hold the dimensions of a multianewarray
 * instruction.
 */
@NodeInfo(cycles = CYCLES_2, size = SIZE_1)
public final class DimensionsNode extends FixedWithNextNode implements LIRLowerable {

    public static final NodeClass<DimensionsNode> TYPE = NodeClass.create(DimensionsNode.class);
    protected final int rank;

    public DimensionsNode(@InjectedNodeParameter WordTypes wordTypes, int rank) {
        super(TYPE, StampFactory.forKind(wordTypes.getWordKind()));
        this.rank = rank;
    }

    public int getRank() {
        return rank;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        LIRGeneratorTool lirGen = gen.getLIRGeneratorTool();
        int sizeInBytes = rank * Integer.BYTES;
        VirtualStackSlot array = lirGen.allocateStackMemory(sizeInBytes, Integer.BYTES);
        Value result = lirGen.emitAddress(array);
        gen.setResult(this, result);
    }

    @NodeIntrinsic
    public static native Word allocaDimsArray(@ConstantNodeParameter int rank);
}
