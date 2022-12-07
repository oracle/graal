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

import static org.graalvm.compiler.nodeinfo.InputType.Memory;

import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.compiler.core.common.PermanentBailoutException;
import org.graalvm.compiler.core.common.calc.UnsignedMath;
import org.graalvm.compiler.graph.IterableNodeType;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeCycles;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodeinfo.NodeSize;
import org.graalvm.compiler.nodes.AbstractStateSplit;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.memory.MemoryAccess;
import org.graalvm.compiler.nodes.memory.MemoryKill;
import org.graalvm.compiler.nodes.spi.Lowerable;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.word.LocationIdentity;

import com.oracle.svm.core.FrameAccess;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.thread.VirtualThreads;

import jdk.vm.ci.meta.ResolvedJavaMethod;

/** @see LoweredStackValueNode */
@NodeInfo(cycles = NodeCycles.CYCLES_4, size = NodeSize.SIZE_8)
public class StackValueNode extends AbstractStateSplit implements MemoryAccess, Lowerable, IterableNodeType {
    public static final NodeClass<StackValueNode> TYPE = NodeClass.create(StackValueNode.class);

    /*
     * This is a more or less random high number, to catch stack allocations that most likely lead
     * to a stack overflow anyway.
     */
    static final int MAX_SIZE = 10 * 1024 * 1024;

    @OptionalInput(Memory) MemoryKill lastLocationAccess;

    protected final int sizeInBytes;
    protected final int alignmentInBytes;
    protected final StackSlotIdentity slotIdentity;
    protected final boolean checkVirtualThread;

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

    protected StackValueNode(int sizeInBytes, int alignmentInBytes, StackSlotIdentity slotIdentity, boolean checkVirtualThread) {
        this(TYPE, sizeInBytes, alignmentInBytes, slotIdentity, checkVirtualThread);
    }

    protected StackValueNode(NodeClass<? extends StackValueNode> type, int sizeInBytes, int alignmentInBytes, StackSlotIdentity slotIdentity, boolean checkVirtualThread) {
        super(type, FrameAccess.getWordStamp());
        this.sizeInBytes = sizeInBytes;
        this.alignmentInBytes = alignmentInBytes;
        this.slotIdentity = slotIdentity;
        this.checkVirtualThread = checkVirtualThread;
    }

    public int getSizeInBytes() {
        return sizeInBytes;
    }

    public int getAlignmentInBytes() {
        return alignmentInBytes;
    }

    @Override
    public LocationIdentity getLocationIdentity() {
        if (checkVirtualThread) {
            return LocationIdentity.any();
        }
        return MemoryKill.NO_LOCATION;
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

    /**
     * Factory method used for intrinsifying {@link StackValue} API methods. This method therefore
     * must follow the API specification.
     */
    public static ValueNode create(long numElements, long elementSize, GraphBuilderContext b, boolean disallowVirtualThread) {
        /*
         * Do a careful overflow check, ensuring that the multiplication does not overflow to a
         * small value that seems to be in range.
         */
        if (UnsignedMath.aboveOrEqual(numElements, MAX_SIZE) || UnsignedMath.aboveOrEqual(elementSize, MAX_SIZE) || UnsignedMath.aboveOrEqual(numElements * elementSize, MAX_SIZE)) {
            throw new PermanentBailoutException("stack value has illegal size " + numElements + " * " + elementSize);
        }

        int sizeInBytes = NumUtil.safeToInt(numElements * elementSize);
        return create(sizeInBytes, b.getGraph().method(), b.bci(), disallowVirtualThread);
    }

    public static StackValueNode create(int sizeInBytes, ResolvedJavaMethod method, int bci, boolean disallowVirtualThread) {
        String name = method.asStackTraceElement(bci).toString();
        if (UnsignedMath.aboveOrEqual(sizeInBytes, MAX_SIZE)) {
            throw new PermanentBailoutException("stack value has illegal size " + sizeInBytes + ": " + name);
        }

        /* Alignment is specified by StackValue API methods as "alignment used for stack frames". */
        int alignmentInBytes = ConfigurationValues.getTarget().stackAlignment;
        StackSlotIdentity slotIdentity = new StackSlotIdentity(name, false);

        /*
         * We should actually not allow @Uninterruptible(calleeMustBe=false) since it enables
         * yielding and blocking in callees, or mayBeInlined=true because things might be shuffled
         * around in a caller, but these are difficult to ensure across multiple callers and
         * callees.
         */
        boolean checkVirtualThread = disallowVirtualThread && VirtualThreads.isSupported() && !Uninterruptible.Utils.isUninterruptible(method);

        return new StackValueNode(sizeInBytes, alignmentInBytes, slotIdentity, checkVirtualThread);
    }
}
