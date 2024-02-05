/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.thread;

import com.oracle.svm.core.FrameAccess;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.graal.nodes.FloatingWordCastNode;
import com.oracle.svm.core.threadlocal.VMThreadLocalInfo;

import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeCycles;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodeinfo.NodeSize;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.AddNode;
import jdk.graal.compiler.nodes.calc.FloatingNode;
import jdk.graal.compiler.nodes.spi.Lowerable;
import jdk.graal.compiler.nodes.spi.LoweringTool;
import jdk.vm.ci.meta.JavaKind;

@NodeInfo(cycles = NodeCycles.CYCLES_1, size = NodeSize.SIZE_1)
public class AddressOfVMThreadLocalNode extends FloatingNode implements VMThreadLocalAccess, Lowerable {
    public static final NodeClass<AddressOfVMThreadLocalNode> TYPE = NodeClass.create(AddressOfVMThreadLocalNode.class);

    protected final VMThreadLocalInfo threadLocalInfo;
    @Input protected ValueNode holder;

    public AddressOfVMThreadLocalNode(VMThreadLocalInfo threadLocalInfo, ValueNode holder) {
        super(TYPE, FrameAccess.getWordStamp());
        this.threadLocalInfo = threadLocalInfo;
        this.holder = holder;
    }

    @Override
    public void lower(LoweringTool tool) {
        assert threadLocalInfo.offset >= 0;

        ValueNode base = holder;
        if (base.getStackKind() == JavaKind.Object) {
            base = graph().unique(new FloatingWordCastNode(FrameAccess.getWordStamp(), base));
        }
        assert base.getStackKind() == ConfigurationValues.getWordKind();

        ConstantNode offset = ConstantNode.forIntegerKind(ConfigurationValues.getWordKind(), threadLocalInfo.offset, graph());
        ValueNode address = graph().unique(new AddNode(base, offset));
        replaceAtUsagesAndDelete(address);
    }
}
