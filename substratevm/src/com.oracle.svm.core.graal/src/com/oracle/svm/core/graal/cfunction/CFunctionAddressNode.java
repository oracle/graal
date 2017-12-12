/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.svm.core.graal.cfunction;

import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeCycles;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodeinfo.NodeSize;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.java.LoadIndexedNode;
import org.graalvm.compiler.nodes.spi.Lowerable;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.word.PointerBase;
import com.oracle.svm.core.amd64.FrameAccess;
import com.oracle.svm.core.meta.SubstrateObjectConstant;

@NodeInfo(cycles = NodeCycles.CYCLES_1, size = NodeSize.SIZE_1)
public class CFunctionAddressNode extends FixedWithNextNode implements Lowerable {
    public static final NodeClass<CFunctionAddressNode> TYPE = NodeClass.create(CFunctionAddressNode.class);

    private final CFunctionLinkage linkage;

    public CFunctionAddressNode(CFunctionLinkage linkage) {
        super(TYPE, FrameAccess.getWordStamp());
        this.linkage = linkage;
    }

    @Override
    public void lower(LoweringTool tool) {
        PointerBase[] entryPoints = CFunctionLinkages.get().getEntryPoints();
        PointerBase entryPoint = entryPoints[linkage.getIndex()];
        if (entryPoint.isNonNull()) {
            /* Runtime compilation: function has been linked and we know the address. */
            ConstantNode entryPointNode = ConstantNode.forIntegerKind(FrameAccess.getWordKind(), entryPoint.rawValue(), graph());
            graph().replaceFixedWithFloating(this, entryPointNode);

        } else {
            /* Ahead-of-time compilation: function has not been linked yet. */
            ConstantNode entryPointsNode = ConstantNode.forConstant(SubstrateObjectConstant.forObject(entryPoints), tool.getMetaAccess(), graph());
            ConstantNode indexNode = ConstantNode.forInt(linkage.getIndex(), graph());
            ValueNode entryPointNode = graph().add(LoadIndexedNode.create(null, entryPointsNode, indexNode, FrameAccess.getWordKind(), tool.getMetaAccess(), tool.getConstantReflection()));
            graph().replaceFixed(this, entryPointNode);
            tool.getLowerer().lower(entryPointNode, tool);
        }
    }
}
