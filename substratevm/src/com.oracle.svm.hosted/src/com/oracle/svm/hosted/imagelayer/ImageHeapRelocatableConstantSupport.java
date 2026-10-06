/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.imagelayer;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.graal.pointsto.heap.ImageHeapRelocatableConstant;

import jdk.graal.compiler.core.common.type.AbstractObjectStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeCycles;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodeinfo.NodeSize;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.FloatingNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.spi.Lowerable;
import jdk.graal.compiler.nodes.spi.LoweringTool;

/**
 * {@link ImageHeapRelocatableConstant}s registered via this support are allowed to be directly
 * referenced within graphs.
 */
public abstract class ImageHeapRelocatableConstantSupport {

    static ImageHeapRelocatableConstantSupport singleton() {
        return ImageSingletons.lookup(ImageHeapRelocatableConstantSupport.class);
    }

    abstract void registerLoadableConstant(ImageHeapRelocatableConstant constant);

    final FloatingNode emitLoadConstant(StructuredGraph graph, LoweringTool tool, ImageHeapRelocatableConstant constant) {
        return emitLoadConstant(graph, tool, constant, (AbstractObjectStamp) StampFactory.forConstant(constant, tool.getMetaAccess()));
    }

    abstract FloatingNode emitLoadConstant(StructuredGraph graph, LoweringTool tool, ImageHeapRelocatableConstant constant, AbstractObjectStamp stamp);

    final ValueNode createLoad(GraphBuilderContext b, ImageHeapRelocatableConstant constant) {
        return createLoad(b, constant, StampFactory.forConstant(constant, b.getMetaAccess()));
    }

    final ValueNode createLoad(GraphBuilderContext b, ImageHeapRelocatableConstant constant, Stamp stamp) {
        registerLoadableConstant(constant);
        return b.add(new LoadImageHeapRelocatableConstantNode(constant, stamp));
    }

    /**
     * Keeps a relocatable constant in the graph until low-tier lowering, when the shared array that
     * contains all loadable relocatable constants has been finalized.
     */
    @NodeInfo(cycles = NodeCycles.CYCLES_2, size = NodeSize.SIZE_1)
    private static final class LoadImageHeapRelocatableConstantNode extends FloatingNode implements Lowerable {
        private static final NodeClass<LoadImageHeapRelocatableConstantNode> TYPE = NodeClass.create(LoadImageHeapRelocatableConstantNode.class);

        private final ImageHeapRelocatableConstant constant;

        protected LoadImageHeapRelocatableConstantNode(ImageHeapRelocatableConstant constant, Stamp stamp) {
            super(TYPE, stamp);
            this.constant = constant;
        }

        @Override
        public void lower(LoweringTool tool) {
            if (tool.getLoweringStage() == LoweringTool.StandardLoweringStage.LOW_TIER) {
                FloatingNode replacement = singleton().emitLoadConstant(graph(), tool, constant, (AbstractObjectStamp) stamp(NodeView.DEFAULT));
                replaceAndDelete(graph().addOrUniqueWithInputs(replacement));
            }
        }
    }
}
