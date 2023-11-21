/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.graal.compiler.nodeinfo.InputType.Memory;

import org.graalvm.word.LocationIdentity;

import com.oracle.svm.core.FrameAccess;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.graal.stackvalue.StackValueNode.StackSlotIdentity;

import jdk.graal.compiler.graph.IterableNodeType;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.AbstractStateSplit;
import jdk.graal.compiler.nodes.memory.MemoryAccess;
import jdk.graal.compiler.nodes.memory.MemoryKill;
import jdk.graal.compiler.nodes.spi.Lowerable;
import jdk.vm.ci.meta.ResolvedJavaMethod;

@NodeInfo
public abstract class AbstractStackValueNode extends AbstractStateSplit implements Lowerable, MemoryAccess, IterableNodeType {
    public static final NodeClass<AbstractStackValueNode> TYPE = NodeClass.create(AbstractStackValueNode.class);

    @OptionalInput(Memory) MemoryKill lastLocationAccess;

    protected final int alignmentInBytes;
    protected final StackSlotIdentity slotIdentity;
    protected final boolean checkVirtualThread;

    protected AbstractStackValueNode(NodeClass<? extends AbstractStackValueNode> type, int alignmentInBytes, StackSlotIdentity slotIdentity, boolean checkVirtualThread) {
        super(type, FrameAccess.getWordStamp());
        this.alignmentInBytes = alignmentInBytes;
        this.slotIdentity = slotIdentity;
        this.checkVirtualThread = checkVirtualThread;
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

    protected static boolean needsVirtualThreadCheck(ResolvedJavaMethod method, boolean disallowVirtualThread) {
        /*
         * We should actually not allow @Uninterruptible(calleeMustBe=false) since it enables
         * yielding and blocking in callees, or mayBeInlined=true because things might be shuffled
         * around in a caller, but these are difficult to ensure across multiple callers and
         * callees.
         */
        return disallowVirtualThread && !Uninterruptible.Utils.isUninterruptible(method);
    }

    protected static StackSlotIdentity createStackSlotIdentity(ResolvedJavaMethod method, int bci) {
        String name = method.asStackTraceElement(bci).toString();
        return new StackSlotIdentity(name, false);
    }
}
