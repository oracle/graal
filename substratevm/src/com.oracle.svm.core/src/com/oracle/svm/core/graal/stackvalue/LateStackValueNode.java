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

import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.graal.stackvalue.StackValueNode.StackSlotIdentity;

import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeCycles;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodeinfo.NodeSize;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Similar to {@link StackValueNode} but the allocation size only needs to be constant during
 * compilation (and not during graph building).
 */
@NodeInfo(cycles = NodeCycles.CYCLES_4, size = NodeSize.SIZE_8)
public class LateStackValueNode extends AbstractStackValueNode {
    public static final NodeClass<LateStackValueNode> TYPE = NodeClass.create(LateStackValueNode.class);

    @Input protected ValueNode sizeInBytes;

    protected LateStackValueNode(ValueNode sizeInBytes, int alignmentInBytes, StackSlotIdentity slotIdentity, boolean checkVirtualThread) {
        super(TYPE, alignmentInBytes, slotIdentity, checkVirtualThread);
        this.sizeInBytes = sizeInBytes;
    }

    public static LateStackValueNode create(ValueNode sizeInBytes, ResolvedJavaMethod method, int bci, boolean disallowVirtualThread) {
        int alignmentInBytes = ConfigurationValues.getTarget().stackAlignment;
        StackSlotIdentity slotIdentity = createStackSlotIdentity(method, bci);
        return new LateStackValueNode(sizeInBytes, alignmentInBytes, slotIdentity, needsVirtualThreadCheck(method, disallowVirtualThread));
    }
}
