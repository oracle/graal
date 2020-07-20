/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.stackvalue;

import static org.graalvm.compiler.core.common.NumUtil.roundUp;

import org.graalvm.compiler.core.common.PermanentBailoutException;
import org.graalvm.compiler.core.common.calc.UnsignedMath;
import org.graalvm.compiler.graph.IterableNodeType;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.ConstantValue;
import org.graalvm.compiler.lir.VirtualStackSlot;
import org.graalvm.compiler.nodeinfo.NodeCycles;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodeinfo.NodeSize;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import org.graalvm.word.WordBase;

import com.oracle.svm.core.FrameAccess;

import jdk.vm.ci.meta.JavaConstant;

@NodeInfo(cycles = NodeCycles.CYCLES_1, size = NodeSize.SIZE_1)
public final class StackValueNode extends FixedWithNextNode implements LIRLowerable, IterableNodeType {
    public static final NodeClass<StackValueNode> TYPE = NodeClass.create(StackValueNode.class);

    /*
     * This is a more or less random high number, to catch stack allocations that most likely lead
     * to a stack overflow anyway.
     */
    private static final int MAX_SIZE = 10 * 1024 * 1024;

    protected final int size;

    /** All nodes with the same identity get the same stack slot assigned. */
    protected final StackSlotIdentity slotIdentity;

    /**
     * We need to make sure that the stack block is reserved only once, even when compiler
     * optimizations such as loop unrolling duplicate the actual {@link StackValueNode}. While the
     * node itself is cloned, this holder object is not cloned (it is a shallow object copy). The
     * holders are created by the {@link StackValuePhase}.
     */
    protected StackSlotHolder stackSlotHolder;

    public static class StackSlotIdentity {
        protected final String name;

        public StackSlotIdentity(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    protected static class StackSlotHolder {
        protected VirtualStackSlot slot;
        protected final int size;
        protected NodeLIRBuilderTool gen;

        public StackSlotHolder(int size) {
            this.size = size;
        }
    }

    public StackValueNode(long numElements, long elementSize, StackSlotIdentity slotIdentity) {
        super(TYPE, FrameAccess.getWordStamp());

        /*
         * Do a careful overflow check, ensuring that the multiplication does not overflow to a
         * small value that seems to be in range.
         */
        if (UnsignedMath.aboveOrEqual(numElements, MAX_SIZE) || UnsignedMath.aboveOrEqual(elementSize, MAX_SIZE) || UnsignedMath.aboveOrEqual(numElements * elementSize, MAX_SIZE)) {
            throw new PermanentBailoutException("stack value has illegal size " + numElements + " * " + elementSize + ": " + slotIdentity.name);
        }
        this.size = (int) (numElements * elementSize);
        this.slotIdentity = slotIdentity;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        assert stackSlotHolder != null : "node not processed by StackValuePhase";
        assert stackSlotHolder.gen == null || stackSlotHolder.gen == gen : "Same stack slot holder used during multiple compilations, therefore caching a wrong value";
        stackSlotHolder.gen = gen;

        if (size == 0) {
            gen.setResult(this, new ConstantValue(gen.getLIRGeneratorTool().getLIRKind(FrameAccess.getWordStamp()), JavaConstant.forIntegerKind(FrameAccess.getWordKind(), 0)));
        } else {
            VirtualStackSlot slot = stackSlotHolder.slot;
            if (slot == null) {
                int wordSize = gen.getLIRGeneratorTool().target().wordSize;
                int slots = roundUp(size, wordSize) / wordSize;
                slot = gen.getLIRGeneratorTool().allocateStackSlots(slots);
                stackSlotHolder.slot = slot;
            }
            gen.setResult(this, gen.getLIRGeneratorTool().emitAddress(slot));
        }
    }

    @NodeIntrinsic
    public static native WordBase stackValue(@ConstantNodeParameter long numElements, @ConstantNodeParameter long elementSize, @ConstantNodeParameter StackSlotIdentity slotIdentifier);
}
