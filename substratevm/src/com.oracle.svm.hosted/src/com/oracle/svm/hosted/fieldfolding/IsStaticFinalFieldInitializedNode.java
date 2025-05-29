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
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.java.LoadIndexedNode;
import jdk.graal.compiler.nodes.spi.Simplifiable;
import jdk.graal.compiler.nodes.spi.SimplifierTool;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;

/**
 * Node that checks if a static final field is initialized. This is basically just a load of the
 * value in the {@link StaticFinalFieldFoldingSingleton#fieldInitializationStatus} array. But we
 * cannot immediately emit a {@link LoadIndexedNode} in the bytecode parser because we do not know
 * at the time of parsing if the declaring class of the field is initialized at image build time.
 */
@NodeInfo(size = NodeSize.SIZE_1, cycles = NodeCycles.CYCLES_1)
public final class IsStaticFinalFieldInitializedNode extends FixedWithNextNode implements Simplifiable {
    public static final NodeClass<IsStaticFinalFieldInitializedNode> TYPE = NodeClass.create(IsStaticFinalFieldInitializedNode.class);

    /**
     * When the node is created, this is an {@link AnalysisField}. After analysis,
     * {@link AnalysisToHostedGraphTransplanter} rewrites it to a {@link HostedField}.
     */
    private final ResolvedJavaField field;

    protected IsStaticFinalFieldInitializedNode(ResolvedJavaField field) {
        super(TYPE, StampFactory.forKind(JavaKind.Boolean));
        this.field = field;
    }

    public ResolvedJavaField getField() {
        return field;
    }

    @Override
    public void simplify(SimplifierTool tool) {
        if (field instanceof AnalysisField) {
            /*
             * Static analysis is still running, we do not know yet if class will get initialized at
             * image build time after static analysis.
             */
            return;
        }
        assert field instanceof HostedField;

        ValueNode replacementNode;
        if (field.getDeclaringClass().isInitialized()) {
            /*
             * The declaring class of the field has been initialized late after static analysis. So
             * we can also constant fold the field now unconditionally.
             */
            replacementNode = ConstantNode.forBoolean(true, graph());

        } else {
            StaticFinalFieldFoldingSingleton singleton = StaticFinalFieldFoldingSingleton.singleton();
            Integer fieldCheckIndex = singleton.getFieldCheckIndex(field);
            assert fieldCheckIndex != null : "Field must be optimizable: " + field;
            ConstantNode fieldInitializationStatusNode = ConstantNode.forConstant(tool.getSnippetReflection().forObject(singleton.fieldInitializationStatus), tool.getMetaAccess(), graph());
            ConstantNode fieldCheckIndexNode = ConstantNode.forInt(fieldCheckIndex, graph());

            replacementNode = graph().addOrUniqueWithInputs(LoadIndexedNode.create(graph().getAssumptions(), fieldInitializationStatusNode, fieldCheckIndexNode,
                            null, JavaKind.Boolean, tool.getMetaAccess(), tool.getConstantReflection()));
        }

        graph().replaceFixed(this, replacementNode);
    }
}
