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
import jdk.graal.compiler.nodes.FixedNode;
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
    private static final int[] EMPTY_TEMPLATE_VARIANTS = {};

    @Input ValueNode opcode;
    @Input NodeInputList<ValueNode> templateValues;

    private final IntFunction<Object> bytecodeHandlerTableSupplier;
    private final int[] templateVariants;

    /**
     * Creates a dispatch-address node without template specialization.
     */
    public BytecodeHandlerDispatchAddressNode(ValueNode opcode, IntFunction<Object> bytecodeHandlerTableSupplier) {
        super(TYPE, StampFactory.forKind(JavaKind.Long));
        this.opcode = opcode;
        this.templateValues = new NodeInputList<>(this);
        this.templateVariants = EMPTY_TEMPLATE_VARIANTS;
        this.bytecodeHandlerTableSupplier = bytecodeHandlerTableSupplier;
    }

    /**
     * Creates a dispatch-address node.
     *
     * @param opcode the opcode used to index the selected handler table
     * @param templateValues template-variable values in expanded-field order
     * @param templateVariants variant count for each template value
     * @param bytecodeHandlerTableSupplier maps a flattened template index to its handler table;
     *            lookup is deferred because stub graphs can be built before the tables are
     *            initialized
     */
    public BytecodeHandlerDispatchAddressNode(ValueNode opcode, ValueNode[] templateValues, int[] templateVariants,
                    IntFunction<Object> bytecodeHandlerTableSupplier) {
        super(TYPE, StampFactory.forKind(JavaKind.Long));
        GraalError.guarantee(templateValues.length != 0 && templateValues.length == templateVariants.length, "Invalid template value metadata");
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
        ValueNode tableBase;
        if (templateValues.isEmpty()) {
            tableBase = createTableBaseConstant(tool, graph, 0, this);
        } else {
            GraalError.guarantee(templateValues.size() == templateVariants.length, "Invalid template value metadata");
            EconomicSet<ValueNode> activePhis = EconomicSet.create(Equivalence.IDENTITY);
            tableBase = createTableBase(tool, graph, templateValues.toArray(ValueNode.EMPTY_ARRAY), activePhis, this);
        }
        /* Allow the backend to fold a single table constant into the indexed address. */
        if (tableBase instanceof BytecodeHandlerTableLoadNode load) {
            JavaConstant tableConstant = load.tableConstant();
            GraphUtil.unlinkFixedNode(load);
            load.safeDelete();
            return ConstantNode.forConstant(tableConstant, tool.getMetaAccess(), graph);
        }
        return tableBase;
    }

    /**
     * Resolves template values directly to a table constant or a phi of table constants. All
     * non-constant template values at one recursion level must be phis from the same merge, so their
     * inputs can be selected path-by-path.
     */
    private ValueNode createTableBase(LoweringTool tool, StructuredGraph graph, ValueNode[] values,
                    EconomicSet<ValueNode> activePhis, FixedNode insertionPoint) {
        int constantTemplateIndex = tryComputeConstantTemplateIndex(values);
        if (constantTemplateIndex >= 0) {
            return createTableBaseConstant(tool, graph, constantTemplateIndex, insertionPoint);
        }

        AbstractMergeNode merge = null;
        int pathCount = 0;
        /* Find the shared merge that selects every non-constant template value. */
        for (ValueNode value : values) {
            if (value.isConstant()) {
                continue;
            }
            GraalError.guarantee(value instanceof ValuePhiNode, "%s is not constant or a phi of constants", value);
            ValuePhiNode phi = (ValuePhiNode) value;
            GraalError.guarantee(!activePhis.contains(phi), "Template phi %s has a cycle", phi);
            if (merge == null) {
                merge = phi.merge();
                pathCount = phi.valueCount();
            } else {
                GraalError.guarantee(merge == phi.merge() && pathCount == phi.valueCount(),
                                "Template phis must share the same merge: %s vs %s", merge, phi.merge());
            }
        }
        GraalError.guarantee(merge != null, "Template values must contain a non-constant phi");
        /* Mark this recursion level active only after all of its phis have been validated. */
        for (ValueNode value : values) {
            if (value instanceof ValuePhiNode) {
                activePhis.add(value);
            }
        }
        /* Resolve each merge predecessor to a table constant while tracking nested phi cycles. */
        try {
            ValueNode[] tables = new ValueNode[pathCount];
            boolean allSame = true;
            for (int path = 0; path < pathCount; path++) {
                ValueNode[] pathValues = values.clone();
                for (int i = 0; i < pathValues.length; i++) {
                    if (pathValues[i] instanceof ValuePhiNode phi) {
                        pathValues[i] = phi.valueAt(path);
                    }
                }
                tables[path] = createTableBase(tool, graph, pathValues, activePhis, merge.forwardEndAt(path));
                allSame &= path == 0 || sameTable(tables[path], tables[0]);
            }
            if (allSame) {
                JavaConstant tableConstant = ((BytecodeHandlerTableLoadNode) tables[0]).tableConstant();
                for (ValueNode table : tables) {
                    BytecodeHandlerTableLoadNode load = (BytecodeHandlerTableLoadNode) table;
                    GraphUtil.unlinkFixedNode(load);
                    load.safeDelete();
                }
                return createTableBaseLoad(tool, graph, tableConstant, insertionPoint);
            }

            ValuePhiNode tablePhi = graph.addWithoutUnique(new ValuePhiNode(tables[0].stamp(NodeView.DEFAULT).unrestricted(), merge, tables));
            tablePhi.inferStamp();
            return tablePhi;
        } finally {
            for (ValueNode value : values) {
                if (value instanceof ValuePhiNode) {
                    activePhis.remove(value);
                }
            }
        }
    }

    private ValueNode createTableBaseConstant(LoweringTool tool, StructuredGraph graph, int templateIndex, FixedNode insertionPoint) {
        Object bytecodeHandlerTable = bytecodeHandlerTableSupplier.apply(templateIndex);
        JavaConstant bytecodeHandlerTableConstant = tool.getSnippetReflection().forObject(bytecodeHandlerTable);
        return createTableBaseLoad(tool, graph, bytecodeHandlerTableConstant, insertionPoint);
    }

    private static ValueNode createTableBaseLoad(LoweringTool tool, StructuredGraph graph, JavaConstant tableConstant, FixedNode insertionPoint) {
        BytecodeHandlerTableLoadNode load = graph.add(new BytecodeHandlerTableLoadNode(tableConstant, StampFactory.forConstant(tableConstant, tool.getMetaAccess())));
        graph.addBeforeFixed(insertionPoint, load);
        return load;
    }

    private static boolean sameTable(ValueNode a, ValueNode b) {
        return a instanceof BytecodeHandlerTableLoadNode loadA && b instanceof BytecodeHandlerTableLoadNode loadB && loadA.tableConstant().equals(loadB.tableConstant());
    }

    /**
     * Returns the mixed-radix index when every value is constant, or {@code -1} otherwise.
     */
    private int tryComputeConstantTemplateIndex(ValueNode[] values) {
        int templateIndex = 0;
        int multiplier = 1;
        for (int i = 0; i < values.length; i++) {
            ValueNode value = values[i];
            if (!value.isConstant()) {
                return -1;
            }
            templateIndex += asTemplateValue(value, templateVariants[i]) * multiplier;
            multiplier *= templateVariants[i];
        }
        return templateIndex;
    }

    private static int asTemplateValue(ValueNode templateValue, int variants) {
        int value = templateValue.asJavaConstant().asInt();
        GraalError.guarantee(0 <= value && value < variants, "Template value %d is outside [0, %d)", value, variants);
        return value;
    }
}
