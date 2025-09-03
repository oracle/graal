/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.fieldfolding;

import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.svm.hosted.code.AnalysisToHostedGraphTransplanter;
import com.oracle.svm.hosted.meta.HostedField;

import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeCycles;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodeinfo.NodeSize;
import jdk.graal.compiler.nodes.AbstractStateSplit;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.java.StoreIndexedNode;
import jdk.graal.compiler.nodes.spi.Simplifiable;
import jdk.graal.compiler.nodes.spi.SimplifierTool;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;

/**
 * Node that marks a static final field as initialized. This is basically just a store of the value
 * true in the {@link StaticFinalFieldFoldingSingleton#fieldInitializationStatus} array. But we
 * cannot immediately emit a {@link StoreIndexedNode} in the bytecode parser because we do not know
 * at the time of parsing if the field can actually be optimized or not. So this node is emitted for
 * every static final field store, and then just removed if the field cannot be optimized.
 */
@NodeInfo(size = NodeSize.SIZE_1, cycles = NodeCycles.CYCLES_1)
public final class MarkStaticFinalFieldInitializedNode extends AbstractStateSplit implements Simplifiable {
    public static final NodeClass<MarkStaticFinalFieldInitializedNode> TYPE = NodeClass.create(MarkStaticFinalFieldInitializedNode.class);

    /**
     * When the node is created, this is an {@link AnalysisField}. After analysis,
     * {@link AnalysisToHostedGraphTransplanter} rewrites it to a {@link HostedField}.
     */
    private final ResolvedJavaField field;

    protected MarkStaticFinalFieldInitializedNode(ResolvedJavaField field) {
        super(TYPE, StampFactory.forVoid());
        this.field = field;
    }

    public ResolvedJavaField getField() {
        return field;
    }

    @Override
    public void simplify(SimplifierTool tool) {
        if (field instanceof AnalysisField) {
            /* Static analysis is still running, we do not know yet which fields are optimized. */
            return;
        }
        assert field instanceof HostedField;

        StaticFinalFieldFoldingSingleton singleton = StaticFinalFieldFoldingSingleton.singleton();
        Integer fieldCheckIndex = singleton.getFieldCheckIndex(field);
        if (fieldCheckIndex != null) {
            ConstantNode fieldInitializationStatusNode = ConstantNode.forConstant(tool.getSnippetReflection().forObject(singleton.fieldInitializationStatus), tool.getMetaAccess(), graph());
            ConstantNode fieldCheckIndexNode = ConstantNode.forInt(fieldCheckIndex, graph());
            ConstantNode trueNode = ConstantNode.forBoolean(true, graph());
            StoreIndexedNode replacementNode = graph().add(new StoreIndexedNode(fieldInitializationStatusNode, fieldCheckIndexNode, null, null, JavaKind.Boolean, trueNode));

            graph().addBeforeFixed(this, replacementNode);
            replacementNode.setStateAfter(stateAfter());
        } else {
            /* Field is not optimized, just remove ourselves. */
        }
        graph().removeFixed(this);
    }
}
