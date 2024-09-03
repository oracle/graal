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

import org.graalvm.nativeimage.StackValue;

import com.oracle.svm.core.config.ConfigurationValues;

import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.core.common.PermanentBailoutException;
import jdk.graal.compiler.core.common.calc.UnsignedMath;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeCycles;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodeinfo.NodeSize;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/** @see LoweredStackValueNode */
@NodeInfo(cycles = NodeCycles.CYCLES_4, size = NodeSize.SIZE_8)
public class StackValueNode extends AbstractStackValueNode {
    public static final NodeClass<StackValueNode> TYPE = NodeClass.create(StackValueNode.class);

    /*
     * This is a more or less random high number, to catch stack allocations that most likely lead
     * to a stack overflow anyway.
     */
    static final int MAX_SIZE = 10 * 1024 * 1024;

    protected final int sizeInBytes;

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
        super(type, alignmentInBytes, slotIdentity, checkVirtualThread);
        this.sizeInBytes = sizeInBytes;
    }

    public int getSizeInBytes() {
        return sizeInBytes;
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
            throw new PermanentBailoutException("Stack value has illegal size " + numElements + " * " + elementSize);
        }

        int sizeInBytes = NumUtil.safeToInt(numElements * elementSize);
        return create(sizeInBytes, b.getGraph().method(), b.bci(), disallowVirtualThread);
    }

    public static StackValueNode create(int sizeInBytes, ResolvedJavaMethod method, int bci, boolean disallowVirtualThread) {
        StackSlotIdentity slotIdentity = createStackSlotIdentity(method, bci);
        return create(sizeInBytes, slotIdentity, needsVirtualThreadCheck(method, disallowVirtualThread));
    }

    public static StackValueNode create(int sizeInBytes, StackSlotIdentity slotIdentity, boolean checkVirtualThread) {
        if (UnsignedMath.aboveOrEqual(sizeInBytes, MAX_SIZE)) {
            throw new PermanentBailoutException("Stack value has illegal size " + sizeInBytes + ": " + slotIdentity.name);
        }

        /* Alignment is specified by StackValue API methods as "alignment used for stack frames". */
        int alignmentInBytes = ConfigurationValues.getTarget().stackAlignment;

        return new StackValueNode(sizeInBytes, alignmentInBytes, slotIdentity, checkVirtualThread);
    }
}
