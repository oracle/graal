/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.phases.common;

import org.graalvm.compiler.core.common.GraalOptions;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.extended.BoxNode;
import org.graalvm.compiler.nodes.spi.CoreProviders;
import org.graalvm.compiler.phases.BasePhase;

import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.MetaAccessProvider;

/**
 * Utility phase to perform canonicalization of box nodes before lowering. We do not perform box
 * canonicalization directly in the node since want virtualization of box nodes. Creating a boxed
 * constant early on inhibits PEA so we do it after PEA before lowering.
 */
public class BoxNodeCanonicalizationPhase extends BasePhase<CoreProviders> {

    @Override
    protected void run(StructuredGraph graph, CoreProviders context) {
        for (Node n : graph.getNodes()) {
            if (n instanceof BoxNode) {
                FloatingNode canonical = canonicalizeBoxing((BoxNode) n, context.getMetaAccess(), context.getConstantReflection());
                if (canonical != null) {
                    n.replaceAtUsages((ValueNode) ((BoxNode) n).getLastLocationAccess(), InputType.Memory);
                    graph.replaceFixedWithFloating((FixedWithNextNode) n, canonical);
                }
            }
        }
    }

    public static FloatingNode canonicalizeBoxing(BoxNode box, MetaAccessProvider metaAccess, ConstantReflectionProvider constantReflection) {
        ValueNode value = box.getValue();
        if (value.isConstant() && !GraalOptions.ImmutableCode.getValue(box.getOptions())) {
            JavaConstant sourceConstant = value.asJavaConstant();
            if (sourceConstant.getJavaKind() != box.getBoxingKind() && sourceConstant.getJavaKind().isNumericInteger()) {
                switch (box.getBoxingKind()) {
                    case Boolean:
                        sourceConstant = JavaConstant.forBoolean(sourceConstant.asLong() != 0L);
                        break;
                    case Byte:
                        sourceConstant = JavaConstant.forByte((byte) sourceConstant.asLong());
                        break;
                    case Char:
                        sourceConstant = JavaConstant.forChar((char) sourceConstant.asLong());
                        break;
                    case Short:
                        sourceConstant = JavaConstant.forShort((short) sourceConstant.asLong());
                        break;
                }
            }
            JavaConstant boxedConstant = constantReflection.boxPrimitive(sourceConstant);
            if (boxedConstant != null && sourceConstant.getJavaKind() == box.getBoxingKind()) {
                return ConstantNode.forConstant(boxedConstant, metaAccess, box.graph());
            }
        }
        return null;
    }
}
