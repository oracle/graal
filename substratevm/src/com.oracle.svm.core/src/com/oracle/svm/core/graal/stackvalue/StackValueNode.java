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

import org.graalvm.compiler.core.common.PermanentBailoutException;
import org.graalvm.compiler.core.common.calc.UnsignedMath;
import org.graalvm.compiler.graph.IterableNodeType;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.ConstantValue;
import org.graalvm.compiler.lir.VirtualStackSlot;
import org.graalvm.compiler.nodeinfo.NodeCycles;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodeinfo.NodeSize;
import org.graalvm.compiler.nodes.AbstractStateSplit;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.word.WordBase;

import com.oracle.svm.core.FrameAccess;
import com.oracle.svm.core.config.ConfigurationValues;

import jdk.vm.ci.meta.JavaConstant;

/**
 * This node is used to reserve memory on the stack. We need to make sure that the stack block is
 * reserved only once, even when compiler optimizations such as loop unrolling duplicate the actual
 * {@link StackValueNode}. While the node itself is cloned, the {@link #slotIdentity} is not cloned
 * (it is a shallow object copy).
 * <p>
 * We don't track the lifetime of {@link StackValueNode}s. So, stack slots are not reused very
 * efficiently at the moment. However, we at least ensure that stack slots are reused if a method is
 * inlined multiple times into the same compilation unit. In this context, we must be careful though
 * as recursively inlined methods must not share their stack slots. So, we compute the inlined
 * recursion depth in the {@link StackValueRecursionDepthPhase}.
 * <p>
 * The actual assignment of the {@link #stackSlotHolder} is done by the
 * {@link StackValueSlotAssignmentPhase} in a way that all nodes with the same identity and
 * recursion depth share a stack slot.
 */
@NodeInfo(cycles = NodeCycles.CYCLES_1, size = NodeSize.SIZE_1)
public final class StackValueNode extends AbstractStateSplit implements LIRLowerable, IterableNodeType {
    public static final NodeClass<StackValueNode> TYPE = NodeClass.create(StackValueNode.class);

    /*
     * This is a more or less random high number, to catch stack allocations that most likely lead
     * to a stack overflow anyway.
     */
    private static final int MAX_SIZE = 10 * 1024 * 1024;

    protected final int sizeInBytes;
    protected final int alignmentInBytes;
    protected final StackSlotIdentity slotIdentity;
    private int recursionDepth;
    protected StackSlotHolder stackSlotHolder;

    public static class StackSlotIdentity {
        /**
         * Determines if the same stack slot should be used for all methods in the current
         * compilation unit (this also ignores the recursion depth).
         */
        protected final boolean shared;
        protected final String name;

        public StackSlotIdentity(String name, boolean shared) {
            this.name = name;
            this.shared = shared;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    protected static class StackSlotHolder {
        protected VirtualStackSlot slot;
        protected NodeLIRBuilderTool gen;
    }

    /**
     * Factory method used for intrinsifying {@link StackValue} API methods. This method therefore
     * must follow the API specification.
     */
    public static ValueNode create(long numElements, long elementSize, GraphBuilderContext b) {
        /*
         * Do a careful overflow check, ensuring that the multiplication does not overflow to a
         * small value that seems to be in range.
         */
        String name = b.getGraph().method().asStackTraceElement(b.bci()).toString();
        if (UnsignedMath.aboveOrEqual(numElements, MAX_SIZE) || UnsignedMath.aboveOrEqual(elementSize, MAX_SIZE) || UnsignedMath.aboveOrEqual(numElements * elementSize, MAX_SIZE)) {
            throw new PermanentBailoutException("stack value has illegal size " + numElements + " * " + elementSize + ": " + name);
        }

        int sizeInBytes = (int) (numElements * elementSize);
        /* Alignment is specified by StackValue API methods as "alignment used for stack frames". */
        int alignmentInBytes = ConfigurationValues.getTarget().stackAlignment;
        StackSlotIdentity slotIdentity = new StackSlotIdentity(name, false);

        return new StackValueNode(sizeInBytes, alignmentInBytes, slotIdentity);
    }

    protected StackValueNode(int sizeInBytes, int alignmentInBytes, StackSlotIdentity slotIdentity) {
        super(TYPE, FrameAccess.getWordStamp());
        this.sizeInBytes = sizeInBytes;
        this.alignmentInBytes = alignmentInBytes;
        this.slotIdentity = slotIdentity;
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
            gen.setResult(this, new ConstantValue(gen.getLIRGeneratorTool().getLIRKind(FrameAccess.getWordStamp()), JavaConstant.forIntegerKind(FrameAccess.getWordKind(), 0)));
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
    public static native WordBase stackValue(@ConstantNodeParameter int sizeInBytes, @ConstantNodeParameter int alignmentInBytes, @ConstantNodeParameter StackSlotIdentity slotIdentifier);
}
