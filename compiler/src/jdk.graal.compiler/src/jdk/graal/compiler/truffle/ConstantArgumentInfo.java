/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.truffle;

import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.extended.AbstractBoxingNode;
import jdk.graal.compiler.nodes.extended.BoxNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;

/**
 * Carries the information of a guest parameter that is known to be constant for a specific call
 * site. It can be either a constant reference or a constant primitive value that is boxed. When
 * constructing or optimizing the graph for that specific call site, this information can be used
 * for folding the parameter into a constant.
 */
public sealed interface ConstantArgumentInfo permits ConstantArgumentInfo.ConstantReference, ConstantArgumentInfo.BoxedConstantPrimitive {

    /**
     * Push the constant node to the frame state stack when building the graph.
     *
     * @param b the graph builder context for building the graph
     */
    void pushConstant(GraphBuilderContext b);

    /**
     * Replace an existing node to the constant.
     *
     * @param toReplace the node to be replaced
     * @param metaAccess the meta access provider
     */
    void replaceInGraph(FixedWithNextNode toReplace, MetaAccessProvider metaAccess);

    final class ConstantReference implements ConstantArgumentInfo {
        JavaConstant value;

        ConstantReference(JavaConstant value) {
            this.value = value;
        }

        @Override
        public void pushConstant(GraphBuilderContext b) {
            b.addPush(JavaKind.Object, ConstantNode.forConstant(value, b.getMetaAccess()));
        }

        @Override
        public void replaceInGraph(FixedWithNextNode toReplace, MetaAccessProvider metaAccess) {
            StructuredGraph graph = toReplace.graph();
            ConstantNode constant = graph.addOrUniqueWithInputs(ConstantNode.forConstant(value, metaAccess));
            toReplace.replaceAtUsages(constant);
        }
    }

    final class BoxedConstantPrimitive implements ConstantArgumentInfo {
        JavaConstant primitiveValue;
        Stamp boxType;
        JavaKind boxKind;

        BoxedConstantPrimitive(JavaConstant primitiveValue, Stamp boxType, JavaKind boxKind) {
            this.primitiveValue = primitiveValue;
            this.boxType = boxType;
            this.boxKind = boxKind;
        }

        @Override
        public void pushConstant(GraphBuilderContext b) {
            b.addPush(JavaKind.Object, BoxNode.create(ConstantNode.forPrimitive(primitiveValue), boxType.javaType(b.getMetaAccess()), boxKind));
        }

        @Override
        public void replaceInGraph(FixedWithNextNode toReplace, MetaAccessProvider metaAccess) {
            StructuredGraph graph = toReplace.graph();
            ConstantNode primitiveConstant = graph.addOrUniqueWithInputs(ConstantNode.forPrimitive(primitiveValue));
            BoxNode box = graph.addOrUniqueWithInputs(BoxNode.create(primitiveConstant, boxType.javaType(metaAccess), boxKind));
            graph.addAfterFixed(toReplace, box);
            toReplace.replaceAtUsages(box);
        }
    }

    static ConstantArgumentInfo create(ValueNode argument) {
        if (argument.isJavaConstant()) {
            return new ConstantReference(argument.asJavaConstant());
        }
        if (argument instanceof AbstractBoxingNode box && box.getValue().isJavaConstant()) {
            return new BoxedConstantPrimitive(box.getValue().asJavaConstant(), box.stamp(NodeView.DEFAULT), box.getBoxingKind());
        }
        return null;
    }
}
