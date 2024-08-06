/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.word.WordBase;

import com.oracle.svm.core.FrameAccess;
import com.oracle.svm.core.config.ConfigurationValues;

import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.ConstantValue;
import jdk.graal.compiler.lir.VirtualStackSlot;
import jdk.graal.compiler.nodeinfo.NodeCycles;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodeinfo.NodeSize;
import jdk.graal.compiler.nodes.spi.LIRLowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.vm.ci.meta.JavaConstant;

/**
 * This node is used to reserve memory on the stack. We need to make sure that the stack block is
 * reserved only once, even when compiler optimizations such as loop unrolling duplicate the actual
 * {@link LoweredStackValueNode}. While the node itself is cloned, the {@link #slotIdentity} is not
 * cloned (it is a shallow object copy).
 * <p>
 * We don't track the lifetime of {@link LoweredStackValueNode}s. So, stack slots are not reused
 * very efficiently at the moment. However, we at least ensure that stack slots are reused if a
 * method is inlined multiple times into the same compilation unit. In this context, we must be
 * careful though as recursively inlined methods must not share their stack slots. So, we compute
 * the inlined recursion depth in the {@link StackValueRecursionDepthPhase}.
 * <p>
 * The actual assignment of the {@link LoweredStackValueNode#stackSlotHolder} is done by the
 * {@link StackValueSlotAssignmentPhase} in a way that all nodes with the same identity and
 * recursion depth share a stack slot.
 */
@NodeInfo(cycles = NodeCycles.CYCLES_1, size = NodeSize.SIZE_1)
public final class LoweredStackValueNode extends StackValueNode implements LIRLowerable {
    public static final NodeClass<LoweredStackValueNode> TYPE = NodeClass.create(LoweredStackValueNode.class);

    private int recursionDepth;
    StackSlotHolder stackSlotHolder;

    public static class StackSlotHolder {
        protected VirtualStackSlot slot;
        protected NodeLIRBuilderTool gen;

        public VirtualStackSlot getSlot() {
            return slot;
        }

        public void setSlot(VirtualStackSlot slot) {
            this.slot = slot;
        }
    }

    public StackSlotHolder getStackSlotHolder() {
        return stackSlotHolder;
    }

    protected LoweredStackValueNode(int sizeInBytes, int alignmentInBytes, StackSlotIdentity slotIdentity) {
        super(TYPE, sizeInBytes, alignmentInBytes, slotIdentity, false);
        this.recursionDepth = slotIdentity.shared ? 0 : -1;
    }

    int getRecursionDepth() {
        assert recursionDepth >= 0;
        return recursionDepth;
    }

    void setRecursionDepth(int value) {
        this.recursionDepth = value;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        assert stackSlotHolder != null : "node not processed by StackValuePhase";
        assert stackSlotHolder.gen == null || stackSlotHolder.gen == gen : "Same stack slot holder used during multiple compilations, therefore caching a wrong value";
        stackSlotHolder.gen = gen;

        if (sizeInBytes == 0) {
            gen.setResult(this, new ConstantValue(gen.getLIRGeneratorTool().getLIRKind(FrameAccess.getWordStamp()), JavaConstant.forIntegerKind(ConfigurationValues.getWordKind(), 0)));
        } else {
            VirtualStackSlot slot = stackSlotHolder.slot;
            if (slot == null) {
                slot = gen.getLIRGeneratorTool().allocateStackMemory(sizeInBytes, alignmentInBytes);
                stackSlotHolder.slot = slot;
            }
            gen.setResult(this, gen.getLIRGeneratorTool().emitAddress(slot));
        }
    }

    @NodeIntrinsic
    public static native WordBase loweredStackValue(@ConstantNodeParameter int sizeInBytes, @ConstantNodeParameter int alignmentInBytes, @ConstantNodeParameter StackSlotIdentity slotIdentifier);
}
