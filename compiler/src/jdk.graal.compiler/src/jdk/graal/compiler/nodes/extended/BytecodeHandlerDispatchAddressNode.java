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

import java.util.List;
import java.util.function.IntFunction;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.Equivalence;

import jdk.graal.compiler.core.common.memory.BarrierType;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.NodeInputList;
import jdk.graal.compiler.nodeinfo.NodeCycles;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.AbstractMergeNode;
import jdk.graal.compiler.nodes.BeginNode;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.ControlSplitNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.GraphState.StageFlag;
import jdk.graal.compiler.nodes.LoopExitNode;
import jdk.graal.compiler.nodes.NamedLocationIdentity;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ProxyNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.ValuePhiNode;
import jdk.graal.compiler.nodes.ValueProxyNode;
import jdk.graal.compiler.nodes.calc.AddNode;
import jdk.graal.compiler.nodes.calc.ConditionalNode;
import jdk.graal.compiler.nodes.calc.IntegerEqualsNode;
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
 * is expected to be a compile-time constant, or a phi whose inputs recursively resolve to constants. In the
 * phi case, lowering builds a phi of table constants so every control-flow path still selects a
 * statically known table. Loop-exit proxies are preserved by selecting the table inside the loop
 * and proxying the selected table across the exit. When final escape analysis has not run, dynamic
 * template values select among the existing tables at runtime.
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
            for (ValueNode value : templateValues) {
                if (!resolvesToConstants(value, activePhis)) {
                    // Native Image can run an early escape-analysis pass even with -Ob. Only the
                    // final pass is expected to specialize template values before lowering.
                    assert !graph.isAfterStage(StageFlag.FINAL_PARTIAL_ESCAPE) : "Template dispatch remained dynamic after final escape analysis";
                    return createDynamicTableBase(tool, graph);
                }
            }
            tableBase = createTableBase(tool, graph, templateValues.toArray(ValueNode.EMPTY_ARRAY), activePhis, this, EconomicMap.create());
        }
        /* Allow the backend to fold a single table constant into the indexed address. */
        if (tableBase instanceof BytecodeHandlerTableLoadNode load) {
            JavaConstant tableConstant = load.tableConstant();
            if (load.hasNoUsages()) {
                GraphUtil.unlinkFixedNode(load);
                load.safeDelete();
            }
            return ConstantNode.forConstant(tableConstant, tool.getMetaAccess(), graph);
        }
        /*
         * A phi or loop-exit proxy selects between tables along different control-flow paths.
         * Keep it and its fixed table loads so each path materializes its table before the merge
         * or loop exit, rather than materializing every table at the dispatch.
         */
        return tableBase;
    }

    private static boolean resolvesToConstants(ValueNode input, EconomicSet<ValueNode> activePhis) {
        ValueNode value = GraphUtil.unproxifyExceptLoopProxies(input);
        if (value.isConstant()) {
            return true;
        }
        if (value instanceof ValueProxyNode proxy) {
            return resolvesToConstants(proxy.value(), activePhis);
        }
        if (!(value instanceof ValuePhiNode phi) || !activePhis.add(phi)) {
            return false;
        }
        try {
            for (ValueNode phiInput : phi.values()) {
                if (!resolvesToConstants(phiInput, activePhis)) {
                    return false;
                }
            }
            return true;
        } finally {
            activePhis.remove(phi);
        }
    }

    private ValueNode createDynamicTableBase(LoweringTool tool, StructuredGraph graph) {
        ValueNode templateIndex = BytecodeHandlerMainDispatchAddressNode.createTemplateIndex(graph, templateValues.toArray(ValueNode.EMPTY_ARRAY), templateVariants);
        int tableCount = 1;
        for (int variants : templateVariants) {
            // createTemplateIndex has already checked the product for overflow.
            tableCount *= variants;
        }
        // Reuse registered tables: lowering is too late to introduce a table-of-tables in the image heap.
        ValueNode table = ConstantNode.forConstant(tool.getSnippetReflection().forObject(bytecodeHandlerTableSupplier.apply(0)), tool.getMetaAccess(), graph);
        for (int i = 1; i < tableCount; i++) {
            ValueNode candidate = ConstantNode.forConstant(tool.getSnippetReflection().forObject(bytecodeHandlerTableSupplier.apply(i)), tool.getMetaAccess(), graph);
            table = graph.addOrUniqueWithInputs(ConditionalNode.create(
                            IntegerEqualsNode.create(templateIndex, ConstantNode.forInt(i, graph), NodeView.DEFAULT), candidate, table, NodeView.DEFAULT));
        }
        return table;
    }

    private record TableBaseKey(List<ValueNode> values, FixedNode insertionPoint) {
    }

    /**
     * Resolves template values directly to a table constant or a phi of table constants. All
     * non-constant template values at one recursion level must be phis from the same merge or
     * proxies from the same loop exit. Resolve proxies inside the loop and phi inputs path-by-path.
     */
    private ValueNode createTableBase(LoweringTool tool, StructuredGraph graph, ValueNode[] values,
                    EconomicSet<ValueNode> activePhis, FixedNode insertionPoint, EconomicMap<TableBaseKey, ValueNode> tableBases) {
        for (int i = 0; i < values.length; i++) {
            values[i] = GraphUtil.unproxifyExceptLoopProxies(values[i]);
        }
        // State phis form a DAG, not a tree. Reuse selections at the same control-flow position;
        // a table load from another predecessor need not dominate this use.
        TableBaseKey key = new TableBaseKey(List.of(values), insertionPoint);
        ValueNode existing = tableBases.get(key);
        if (existing != null && existing.isAlive()) {
            return existing;
        }
        ValueNode result = createUncachedTableBase(tool, graph, values, activePhis, insertionPoint, tableBases);
        tableBases.put(key, result);
        return result;
    }

    private ValueNode createUncachedTableBase(LoweringTool tool, StructuredGraph graph, ValueNode[] values,
                    EconomicSet<ValueNode> activePhis, FixedNode insertionPoint, EconomicMap<TableBaseKey, ValueNode> tableBases) {
        // A negative result means path selection is still needed; it is not an invalid index.
        // Resolve loop-exit proxies and phis below until every path has constant template values.
        int constantTemplateIndex = computeConstantTemplateIndexOrUnresolved(values);
        if (constantTemplateIndex >= 0) {
            return createTableBaseConstant(tool, graph, constantTemplateIndex, insertionPoint);
        }

        ValueNode proxiedTable = createProxiedTableBase(tool, graph, values, activePhis, tableBases);
        if (proxiedTable != null) {
            return proxiedTable;
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
                tables[path] = createTableBase(tool, graph, pathValues, activePhis, merge.phiPredecessorAt(path), tableBases);
                allSame &= path == 0 || sameTable(tables[path], tables[0]);
            }
            if (allSame) {
                JavaConstant tableConstant = ((BytecodeHandlerTableLoadNode) tables[0]).tableConstant();
                for (ValueNode table : tables) {
                    BytecodeHandlerTableLoadNode load = (BytecodeHandlerTableLoadNode) table;
                    if (load.isAlive() && load.hasNoUsages()) {
                        GraphUtil.unlinkFixedNode(load);
                        load.safeDelete();
                    }
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

    /**
     * Resolves table selection across a loop exit, or returns {@code null} if no value is proxied.
     * A loop-exit proxy marks an availability boundary: its input is only available inside the
     * loop. Select the table there and proxy the table into the outer scope instead. For example:
     *
     * <pre>
     * selectTable(proxy(a, exit), proxy(b, exit), constant)
     *     becomes proxy(selectTable(a, b, constant), exit)
     * </pre>
     *
     * All non-constant values must cross the same exit; constants remain available on both sides.
     */
    private ValueNode createProxiedTableBase(LoweringTool tool, StructuredGraph graph, ValueNode[] values,
                    EconomicSet<ValueNode> activePhis, EconomicMap<TableBaseKey, ValueNode> tableBases) {
        ValueProxyNode loopProxy = null;
        for (ValueNode value : values) {
            if (value.isConstant()) {
                continue;
            }
            if (value instanceof ValueProxyNode proxy) {
                if (loopProxy == null) {
                    loopProxy = proxy;
                } else {
                    GraalError.guarantee(loopProxy.proxyPoint() == proxy.proxyPoint(),
                                    "Template proxies must share the same loop exit: %s vs %s", loopProxy.proxyPoint(), proxy.proxyPoint());
                }
            } else {
                GraalError.guarantee(loopProxy == null, "Template values must cross a loop exit together: %s", value);
            }
        }
        if (loopProxy != null) {
            ValueNode[] proxiedValues = values.clone();
            for (int i = 0; i < proxiedValues.length; i++) {
                if (proxiedValues[i] instanceof ValueProxyNode proxy) {
                    proxiedValues[i] = proxy.value();
                } else {
                    GraalError.guarantee(proxiedValues[i].isConstant(), "Template values must cross a loop exit together: %s", proxiedValues[i]);
                }
            }
            ValueNode table = createTableBase(tool, graph, proxiedValues, activePhis, loopProxy.proxyPoint(), tableBases);
            return ProxyNode.forValue(table, loopProxy.proxyPoint());
        }
        return null;
    }

    private ValueNode createTableBaseConstant(LoweringTool tool, StructuredGraph graph, int templateIndex, FixedNode insertionPoint) {
        Object bytecodeHandlerTable = bytecodeHandlerTableSupplier.apply(templateIndex);
        JavaConstant bytecodeHandlerTableConstant = tool.getSnippetReflection().forObject(bytecodeHandlerTable);
        return createTableBaseLoad(tool, graph, bytecodeHandlerTableConstant, insertionPoint);
    }

    private static ValueNode createTableBaseLoad(LoweringTool tool, StructuredGraph graph, JavaConstant tableConstant, FixedNode insertionPoint) {
        // Separate dispatch nodes can select the same table at this predecessor as well.
        for (Node previous = insertionPoint.predecessor(); previous instanceof BytecodeHandlerTableLoadNode load; previous = previous.predecessor()) {
            if (load.tableConstant().equals(tableConstant)) {
                return load;
            }
        }
        BytecodeHandlerTableLoadNode load = graph.add(new BytecodeHandlerTableLoadNode(tableConstant, StampFactory.forConstant(tableConstant, tool.getMetaAccess())));
        if (insertionPoint instanceof LoopExitNode && insertionPoint.predecessor() instanceof ControlSplitNode) {
            // A split requires a begin successor. Keep the load on this edge, inside the loop,
            // so its value is available to the proxy at the loop exit.
            BeginNode begin = graph.add(new BeginNode());
            insertionPoint.replaceAtPredecessor(begin);
            begin.setNext(insertionPoint);
        }
        graph.addBeforeFixed(insertionPoint, load);
        return load;
    }

    private static boolean sameTable(ValueNode a, ValueNode b) {
        return a instanceof BytecodeHandlerTableLoadNode loadA && b instanceof BytecodeHandlerTableLoadNode loadB && loadA.tableConstant().equals(loadB.tableConstant());
    }

    /**
     * Returns the mixed-radix table index when every template value is constant and in range.
     * Returns {@code -1} when a non-constant value still needs resolution through a phi or a
     * loop-exit proxy. The caller recursively resolves those inputs and retries for each path;
     * {@code -1} must never be used to look up a table. Unsupported non-constant nodes and cyclic
     * phis are rejected by the caller, and out-of-range constants are rejected here.
     */
    private int computeConstantTemplateIndexOrUnresolved(ValueNode[] values) {
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
