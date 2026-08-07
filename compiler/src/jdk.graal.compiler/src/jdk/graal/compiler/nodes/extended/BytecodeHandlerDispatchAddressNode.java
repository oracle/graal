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

import java.util.function.IntFunction;

import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.Equivalence;

import jdk.graal.compiler.core.common.memory.BarrierType;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.NodeInputList;
import jdk.graal.compiler.nodeinfo.NodeCycles;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.AbstractMergeNode;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.NamedLocationIdentity;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.ValuePhiNode;
import jdk.graal.compiler.nodes.calc.AddNode;
import jdk.graal.compiler.nodes.calc.LeftShiftNode;
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
 * Computes the address of the next bytecode-handler stub for threaded dispatch.
 * <p>
 * The node treats a selected handler table as a {@code long[]} and returns the table entry for the
 * current opcode. Template-enabled interpreters can provide one or more template values. Each value
 * must be a compile-time constant, or a phi whose inputs recursively resolve to constants. In the
 * phi case, lowering builds a phi of table constants so every control-flow path still selects a
 * statically known table.
 */
@NodeInfo(cycles = NodeCycles.CYCLES_4, size = SIZE_4)
public final class BytecodeHandlerDispatchAddressNode extends FixedWithNextNode implements Lowerable {

    public static final NodeClass<BytecodeHandlerDispatchAddressNode> TYPE = NodeClass.create(BytecodeHandlerDispatchAddressNode.class);

    @Input ValueNode opcode;
    @Input NodeInputList<ValueNode> templateValues;

    private final IntFunction<Object> bytecodeHandlerTableSupplier;
    private final int[] templateVariants;

    /**
     * Creates a dispatch-address node.
     *
     * @param opcode the opcode used to index the selected handler table
     * @param templateValues template-variable values in expanded-field order
     * @param templateVariants variant count for each template value
     * @param bytecodeHandlerTableSupplier maps a flattened template index to its handler table
     */
    public BytecodeHandlerDispatchAddressNode(ValueNode opcode, ValueNode[] templateValues, int[] templateVariants,
                    IntFunction<Object> bytecodeHandlerTableSupplier) {
        super(TYPE, StampFactory.forKind(JavaKind.Long));
        this.opcode = opcode;
        this.templateValues = new NodeInputList<>(this, templateValues);
        this.templateVariants = templateVariants.clone();
        this.bytecodeHandlerTableSupplier = bytecodeHandlerTableSupplier;
    }

    @Override
    public void lower(LoweringTool tool) {
        StructuredGraph graph = graph();
        ValueNode base = createTableBase(tool, graph);
        ConstantNode baseOffset = ConstantNode.forLong(tool.getMetaAccess().getArrayBaseOffset(JavaKind.Long), graph);
        ConstantNode indexShift = ConstantNode.forInt(CodeUtil.log2(tool.getMetaAccess().getArrayIndexScale(JavaKind.Long)), graph);
        ValueNode extendedOpcode = graph.addOrUnique(ZeroExtendNode.create(opcode, 64, NodeView.DEFAULT));
        ValueNode offset = graph.addOrUnique(LeftShiftNode.create(extendedOpcode, indexShift, NodeView.DEFAULT));
        ValueNode offsetWithArrayBase = graph.addOrUnique(AddNode.create(offset, baseOffset, NodeView.DEFAULT));
        OffsetAddressNode address = graph.addOrUnique(new OffsetAddressNode(base, offsetWithArrayBase));
        ValueNode read = FloatingReadNode.createRead(graph, address, NamedLocationIdentity.FINAL_LOCATION,
                        StampFactory.forKind(JavaKind.Long), null, BarrierType.NONE, this);

        replaceAtUsages(read);
        GraphUtil.unlinkFixedNode(this);
        safeDelete();
    }

    private ValueNode createTableBase(LoweringTool tool, StructuredGraph graph) {
        TemplateIndexSelection selection = computeTemplateIndexSelection();
        if (selection.isConstant()) {
            return createTableBaseConstant(tool, graph, selection.constantTemplateIndex());
        }
        return createTableBase(tool, graph, selection);
    }

    private ValueNode createTableBase(LoweringTool tool, StructuredGraph graph, TemplateIndexSelection selection) {
        if (selection.isConstant()) {
            return createTableBaseConstant(tool, graph, selection.constantTemplateIndex());
        }
        TemplateIndexSelection[] inputSelections = selection.inputs();
        ValueNode[] tables = new ValueNode[inputSelections.length];
        boolean allSame = true;
        for (int i = 0; i < inputSelections.length; i++) {
            tables[i] = createTableBase(tool, graph, inputSelections[i]);
            allSame &= i == 0 || tables[i] == tables[0];
        }
        if (allSame) {
            return tables[0];
        }

        ValuePhiNode tablePhi = graph.addWithoutUnique(new ValuePhiNode(tables[0].stamp(NodeView.DEFAULT).unrestricted(),
                        selection.merge(), tables));
        tablePhi.inferStamp();
        return tablePhi;
    }

    private ConstantNode createTableBaseConstant(LoweringTool tool, StructuredGraph graph, int templateIndex) {
        Object bytecodeHandlerTable = bytecodeHandlerTableSupplier.apply(templateIndex);
        JavaConstant bytecodeHandlerTableConstant = tool.getSnippetReflection().forObject(bytecodeHandlerTable);
        return ConstantNode.forConstant(bytecodeHandlerTableConstant, tool.getMetaAccess(), graph);
    }

    /**
     * Computes the flattened template-table index using mixed-radix indexing. For example, template
     * variables with variant counts {@code [3, 2]} are encoded as {@code value0 + value1 * 3}.
     */
    private TemplateIndexSelection computeTemplateIndexSelection() {
        GraalError.guarantee(templateValues.size() == templateVariants.length, "Invalid template value metadata");
        TemplateIndexSelection selection = TemplateIndexSelection.constant(0);
        int multiplier = 1;
        for (int i = 0; i < templateValues.size(); i++) {
            ValueNode templateValue = templateValues.get(i);
            int variants = templateVariants[i];
            EconomicSet<ValueNode> activePhis = EconomicSet.create(Equivalence.IDENTITY);
            TemplateIndexSelection templateSelection = computeTemplateValueSelection(templateValue, variants, activePhis).scale(multiplier);
            selection = selection.add(templateSelection);
            multiplier *= variants;
        }
        return selection;
    }

    /**
     * Converts one template value into either a constant selection or a path-sensitive phi
     * selection. Dynamic values are rejected because the dispatch table must remain statically known
     * on each path.
     */
    private static TemplateIndexSelection computeTemplateValueSelection(ValueNode templateValue, int variants,
                    EconomicSet<ValueNode> activePhis) {
        if (templateValue.isConstant()) {
            return TemplateIndexSelection.constant(asTemplateValue(templateValue, variants));
        } else if (templateValue instanceof ValuePhiNode phi) {
            GraalError.guarantee(activePhis.add(phi), "Template phi %s has a cycle", phi);
            try {
                TemplateIndexSelection[] inputs = new TemplateIndexSelection[phi.valueCount()];
                for (int path = 0; path < phi.valueCount(); path++) {
                    inputs[path] = computeTemplateValueSelection(phi.valueAt(path), variants, activePhis);
                }
                return TemplateIndexSelection.phi(phi.merge(), inputs);
            } finally {
                activePhis.remove(phi);
            }
        } else {
            GraalError.guarantee(false, "%s is not constant or a phi of constants", templateValue);
            return null;
        }
    }

    private static int asTemplateValue(ValueNode templateValue, int variants) {
        int value = templateValue.asJavaConstant().asInt();
        GraalError.guarantee(0 <= value && value < variants, "Template value %d is outside [0, %d)", value, variants);
        return value;
    }

    /**
     * Path-sensitive representation of a flattened template-table index.
     * <p>
     * A constant selection lowers to one table constant. A phi selection lowers to a phi of table
     * constants at {@link #merge}. Arithmetic on selections is used while combining multiple
     * template variables into a mixed-radix index. Multiple non-constant selections are supported
     * only when their phis share the same merge, so the mixed-radix index can be combined
     * path-by-path. Independent branch merges for different template variables are intentionally
     * rejected.
     */
    private record TemplateIndexSelection(int constantTemplateIndex, AbstractMergeNode merge, TemplateIndexSelection[] inputs) {
        static TemplateIndexSelection constant(int templateIndex) {
            return new TemplateIndexSelection(templateIndex, null, null);
        }

        static TemplateIndexSelection phi(AbstractMergeNode merge, TemplateIndexSelection[] inputs) {
            GraalError.guarantee(inputs.length > 0, "Template phi must have inputs");
            if (allInputsSameConstant(inputs)) {
                return inputs[0];
            }
            return new TemplateIndexSelection(-1, merge, inputs);
        }

        boolean isConstant() {
            return inputs == null;
        }

        TemplateIndexSelection add(TemplateIndexSelection other) {
            if (isConstant()) {
                return other.addConstant(constantTemplateIndex);
            } else if (other.isConstant()) {
                return addConstant(other.constantTemplateIndex);
            }

            GraalError.guarantee(merge == other.merge && inputs.length == other.inputs.length,
                            "Template phis must share the same merge: %s vs %s", merge, other.merge);
            TemplateIndexSelection[] addedInputs = new TemplateIndexSelection[inputs.length];
            for (int i = 0; i < inputs.length; i++) {
                addedInputs[i] = inputs[i].add(other.inputs[i]);
            }
            return phi(merge, addedInputs);
        }

        TemplateIndexSelection scale(int factor) {
            if (factor == 1) {
                return this;
            } else if (isConstant()) {
                return constant(constantTemplateIndex * factor);
            }

            TemplateIndexSelection[] scaledInputs = new TemplateIndexSelection[inputs.length];
            for (int i = 0; i < inputs.length; i++) {
                scaledInputs[i] = inputs[i].scale(factor);
            }
            return phi(merge, scaledInputs);
        }

        private TemplateIndexSelection addConstant(int value) {
            if (value == 0) {
                return this;
            } else if (isConstant()) {
                return constant(constantTemplateIndex + value);
            }

            TemplateIndexSelection[] addedInputs = new TemplateIndexSelection[inputs.length];
            for (int i = 0; i < inputs.length; i++) {
                addedInputs[i] = inputs[i].addConstant(value);
            }
            return phi(merge, addedInputs);
        }

        private static boolean allInputsSameConstant(TemplateIndexSelection[] inputs) {
            if (!inputs[0].isConstant()) {
                return false;
            }
            int constant = inputs[0].constantTemplateIndex;
            for (int i = 1; i < inputs.length; i++) {
                if (!inputs[i].isConstant() || inputs[i].constantTemplateIndex != constant) {
                    return false;
                }
            }
            return true;
        }
    }
}
