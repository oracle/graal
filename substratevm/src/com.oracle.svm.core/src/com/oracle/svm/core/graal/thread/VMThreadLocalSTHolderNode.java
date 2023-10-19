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

import jdk.compiler.graal.api.replacements.SnippetReflectionProvider;
import jdk.compiler.graal.core.common.LIRKind;
import jdk.compiler.graal.core.common.type.StampFactory;
import jdk.compiler.graal.graph.NodeClass;
import jdk.compiler.graal.nodeinfo.NodeCycles;
import jdk.compiler.graal.nodeinfo.NodeInfo;
import jdk.compiler.graal.nodeinfo.NodeSize;
import jdk.compiler.graal.nodes.FixedWithNextNode;
import jdk.compiler.graal.nodes.NodeView;
import jdk.compiler.graal.nodes.spi.LIRLowerable;
import jdk.compiler.graal.nodes.spi.NodeLIRBuilderTool;
import jdk.compiler.graal.phases.util.Providers;
import org.graalvm.nativeimage.ImageSingletons;

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

    public VMThreadLocalInfo getThreadLocalInfo() {
        return threadLocalInfo;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        Object holder;
        if (threadLocalInfo.isObject) {
            holder = ImageSingletons.lookup(VMThreadLocalSTSupport.class).objectThreadLocals;
        } else {
            holder = ImageSingletons.lookup(VMThreadLocalSTSupport.class).primitiveThreadLocals;
        }
        SnippetReflectionProvider snippetReflection = ((Providers) gen.getLIRGeneratorTool().getProviders()).getSnippetReflection();
        LIRKind kind = gen.getLIRGeneratorTool().getLIRKind(stamp(NodeView.DEFAULT));
        gen.setResult(this, gen.getLIRGeneratorTool().emitLoadConstant(kind, snippetReflection.forObject(holder)));
    }
}
