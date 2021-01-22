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

import static org.graalvm.compiler.options.OptionType.Debug;

import java.util.EnumSet;

import org.graalvm.compiler.core.common.GraalOptions;
import org.graalvm.compiler.core.common.cfg.AbstractControlFlowGraph;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.graph.Graph;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.cfg.Block;
import org.graalvm.compiler.nodes.cfg.ControlFlowGraph;
import org.graalvm.compiler.nodes.extended.BoxNode;
import org.graalvm.compiler.nodes.extended.BoxNode.OptimizedAllocatingBoxNode;
import org.graalvm.compiler.nodes.extended.UnboxNode;
import org.graalvm.compiler.nodes.spi.CoreProviders;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.phases.BasePhase;

import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;

/**
 * Utility phase to perform canonicalization of box nodes before lowering. We do not perform box
 * canonicalization directly in the node since want virtualization of box nodes. Creating a boxed
 * constant early on inhibits PEA so we do it after PEA but before lowering.
 */
public class BoxNodeCanonicalizationPhase extends BasePhase<CoreProviders> {

    public static class Options {
        //@formatter:off
        @Option(help = "", type = Debug)
        public static final OptionKey<Boolean> ReuseOufOutOfCacheBoxes = new OptionKey<>(true);
        //@formatter:on
    }

    public static final EnumSet<JavaKind> OptimizedBoxVersions = EnumSet.of(JavaKind.Int, JavaKind.Long, JavaKind.Char, JavaKind.Short);

    @Override
    protected void run(StructuredGraph graph, CoreProviders context) {
        ControlFlowGraph cfg = null;
        Graph.Mark before = graph.getMark();
        boxLoop: for (BoxNode box : graph.getNodes(BoxNode.TYPE)) {
            FloatingNode canonical = canonicalizeBoxing(box, context.getMetaAccess(), context.getConstantReflection());
            if (canonical != null) {
                box.replaceAtUsages((ValueNode) box.getLastLocationAccess(), InputType.Memory);
                graph.replaceFixedWithFloating(box, canonical);
            }
            if (box.isAlive() && OptimizedBoxVersions.contains(box.getBoxingKind())) {
                if (box instanceof OptimizedAllocatingBoxNode) {
                    continue;
                }
                if (Options.ReuseOufOutOfCacheBoxes.getValue(graph.getOptions())) {

                    ValueNode boxedVal = box.getValue();
                    assert boxedVal != null : "Box " + box + " has no value";

                    // try to optimize with dominating unbox of the same value
                    if (boxedVal instanceof UnboxNode && ((UnboxNode) boxedVal).getBoxingKind() == box.getBoxingKind()) {
                        optimziBoxed(box, ((UnboxNode) boxedVal).getValue());
                        continue boxLoop;
                    }

                    // try to optimize with dominating box of the same value
                    if (box.isAlive()) {
                        boxedValUsageLoop: for (Node usage : boxedVal.usages().snapshot()) {
                            if (usage == box) {
                                continue;
                            }
                            if (usage instanceof OptimizedAllocatingBoxNode) {
                                continue;
                            }
                            if (usage instanceof BoxNode) {
                                final BoxNode boxUsageOnBoxedVal = (BoxNode) usage;
                                if (boxUsageOnBoxedVal.getBoxingKind() == box.getBoxingKind()) {
                                    if (cfg == null) {
                                        cfg = ControlFlowGraph.compute(graph, true, true, true, false);
                                    }
                                    if (graph.isNew(before, boxUsageOnBoxedVal) || graph.isNew(before, box)) {
                                        continue boxedValUsageLoop;
                                    }
                                    Block boxUsageOnBoxedValBlock = cfg.blockFor(boxUsageOnBoxedVal);
                                    Block originalBoxBlock = cfg.blockFor(box);
                                    if (AbstractControlFlowGraph.dominates(boxUsageOnBoxedValBlock, originalBoxBlock)) {
                                        if (boxUsageOnBoxedValBlock == originalBoxBlock) {
                                            // check dominance within one block
                                            for (FixedNode f : boxUsageOnBoxedValBlock.getNodes()) {
                                                if (f == boxUsageOnBoxedVal) {
                                                    // we found the usage first, it dominates "box"
                                                    break;
                                                } else if (f == box) {
                                                    // they are within the same block but the
                                                    // usage block does not dominate the box
                                                    // block, that scenario will still be
                                                    // optimizable but for the usage block node
                                                    // later in the outer box loop
                                                    continue boxedValUsageLoop;
                                                }
                                            }
                                        }
                                        optimziBoxed(box, boxUsageOnBoxedVal);
                                        continue boxLoop;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private static void optimziBoxed(BoxNode toBeOptimzied, ValueNode boxedDominatingValueToUse) {
        ValueNode other = toBeOptimzied.createOptimizedBox(boxedDominatingValueToUse);
        if (other != toBeOptimzied) {
            final StructuredGraph graph = toBeOptimzied.graph();
            graph.replaceFixed(toBeOptimzied, graph.add(other));
            graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After replacing %s with %s", toBeOptimzied, other);
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
