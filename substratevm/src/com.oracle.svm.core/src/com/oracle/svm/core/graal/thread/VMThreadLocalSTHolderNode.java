/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeCycles;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodeinfo.NodeSize;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.core.threadlocal.VMThreadLocalInfo;
import com.oracle.svm.core.threadlocal.VMThreadLocalSTSupport;

@NodeInfo(cycles = NodeCycles.CYCLES_0, size = NodeSize.SIZE_1)
public class VMThreadLocalSTHolderNode extends FixedWithNextNode implements LIRLowerable {
    public static final NodeClass<VMThreadLocalSTHolderNode> TYPE = NodeClass.create(VMThreadLocalSTHolderNode.class);

    protected final VMThreadLocalInfo threadLocalInfo;

    public VMThreadLocalSTHolderNode(VMThreadLocalInfo threadLocalInfo) {
        super(TYPE, StampFactory.objectNonNull());
        this.threadLocalInfo = threadLocalInfo;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        Object holder;
        if (threadLocalInfo.isObject) {
            holder = ImageSingletons.lookup(VMThreadLocalSTSupport.class).objectThreadLocals;
        } else {
            holder = ImageSingletons.lookup(VMThreadLocalSTSupport.class).primitiveThreadLocals;
        }
        LIRKind kind = gen.getLIRGeneratorTool().getLIRKind(stamp(NodeView.DEFAULT));
        gen.setResult(this, gen.getLIRGeneratorTool().emitLoadConstant(kind, SubstrateObjectConstant.forObject(holder)));
    }
}
