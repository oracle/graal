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
package jdk.graal.compiler.nodes.extended;

import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_4;

import jdk.graal.compiler.core.common.memory.BarrierType;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.NodeInputList;
import jdk.graal.compiler.nodeinfo.NodeCycles;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.NamedLocationIdentity;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.AddNode;
import jdk.graal.compiler.nodes.calc.LeftShiftNode;
import jdk.graal.compiler.nodes.calc.MulNode;
import jdk.graal.compiler.nodes.calc.ZeroExtendNode;
import jdk.graal.compiler.nodes.memory.FloatingReadNode;
import jdk.graal.compiler.nodes.memory.address.OffsetAddressNode;
import jdk.graal.compiler.nodes.spi.Lowerable;
import jdk.graal.compiler.nodes.spi.LoweringTool;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;

/**
 * Computes the bytecode-handler stub address used by the Java interpreter switch.
 * <p>
 * Unlike {@link BytecodeHandlerDispatchAddressNode}, this node accepts dynamic template values:
 * the Java switch is the boundary where runtime template state is converted into a concrete stub
 * variant. The target table is indexed by the same mixed-radix template index used by threaded
 * dispatch.
 */
@NodeInfo(cycles = NodeCycles.CYCLES_4, size = SIZE_4)
public final class BytecodeHandlerMainDispatchAddressNode extends FixedWithNextNode implements Lowerable {

    public static final NodeClass<BytecodeHandlerMainDispatchAddressNode> TYPE = NodeClass.create(BytecodeHandlerMainDispatchAddressNode.class);

    @Input NodeInputList<ValueNode> templateValues;

    private final Object bytecodeHandlerTargets;
    private final int[] templateVariants;

    /**
     * Creates a main-dispatch address node.
     *
     * @param templateValues template-variable values in expanded-field order
     * @param templateVariants variant count for each template value
     * @param bytecodeHandlerTargets target method table indexed by flattened template index
     */
    public BytecodeHandlerMainDispatchAddressNode(ValueNode[] templateValues, int[] templateVariants, Object bytecodeHandlerTargets) {
        super(TYPE, StampFactory.forKind(JavaKind.Long));
        this.templateValues = new NodeInputList<>(this, templateValues);
        this.templateVariants = templateVariants.clone();
        this.bytecodeHandlerTargets = bytecodeHandlerTargets;
    }

    @Override
    public void lower(LoweringTool tool) {
        StructuredGraph graph = graph();
        GraalError.guarantee(templateValues.size() == templateVariants.length, "Invalid template value metadata");

        JavaConstant bytecodeHandlerTargetsConstant = tool.getSnippetReflection().forObject(bytecodeHandlerTargets);
        ConstantNode base = ConstantNode.forConstant(bytecodeHandlerTargetsConstant, tool.getMetaAccess(), graph);
        ConstantNode baseOffset = ConstantNode.forLong(tool.getMetaAccess().getArrayBaseOffset(JavaKind.Long), graph);
        ConstantNode indexShift = ConstantNode.forInt(CodeUtil.log2(tool.getMetaAccess().getArrayIndexScale(JavaKind.Long)), graph);
        ValueNode templateIndex = createTemplateIndex(graph);
        ValueNode extendedTemplateIndex = graph.addOrUnique(ZeroExtendNode.create(templateIndex, 64, NodeView.DEFAULT));
        ValueNode offset = graph.addOrUnique(LeftShiftNode.create(extendedTemplateIndex, indexShift, NodeView.DEFAULT));
        ValueNode offsetWithArrayBase = graph.addOrUnique(AddNode.create(offset, baseOffset, NodeView.DEFAULT));
        OffsetAddressNode address = graph.addOrUnique(new OffsetAddressNode(base, offsetWithArrayBase));
        ValueNode read = FloatingReadNode.createRead(graph, address, NamedLocationIdentity.FINAL_LOCATION,
                        StampFactory.forKind(JavaKind.Long), null, BarrierType.NONE, this);

        replaceAtUsages(read);
        GraphUtil.unlinkFixedNode(this);
        safeDelete();
    }

    /**
     * Computes the flattened template index using mixed-radix indexing. For example, template
     * variables with variant counts {@code [3, 2]} are encoded as {@code value0 + value1 * 3}.
     */
    private ValueNode createTemplateIndex(StructuredGraph graph) {
        ValueNode templateIndex = ConstantNode.forInt(0, graph);
        int multiplier = 1;
        for (int i = 0; i < templateValues.size(); i++) {
            int variants = templateVariants[i];
            ValueNode templateValue = templateValues.get(i);
            ValueNode scaledValue = templateValue;
            if (multiplier != 1) {
                scaledValue = graph.addOrUnique(MulNode.create(templateValue, ConstantNode.forInt(multiplier, graph), NodeView.DEFAULT));
            }
            templateIndex = graph.addOrUnique(AddNode.create(templateIndex, scaledValue, NodeView.DEFAULT));
            GraalError.guarantee(multiplier <= Integer.MAX_VALUE / variants, "Template variant count exceeds int range");
            multiplier *= variants;
        }
        return templateIndex;
    }
}
