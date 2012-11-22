/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.snippets;

import static com.oracle.graal.nodes.MaterializeNode.*;

import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.util.*;
import com.oracle.graal.snippets.SnippetTemplate.AbstractTemplates;
import com.oracle.graal.snippets.SnippetTemplate.Arguments;
import com.oracle.graal.snippets.SnippetTemplate.Key;
import com.oracle.graal.snippets.SnippetTemplate.UsageReplacer;


/**
 * Helper class for lowering {@link InstanceOfNode}s with snippets. The majority of the
 * complexity in such a lowering derives from the fact that {@link InstanceOfNode}
 * is a floating node. A snippet used to lower an {@link InstanceOfNode} will almost always
 * incorporate control flow and replacing a floating node with control flow is not trivial.
 * <p>
 * The mechanism implemented in this class ensures that the graph for an instanceof snippet
 * is instantiated once per {@link InstanceOfNode} being lowered. The result produced the graph
 * is then re-used by all usages of the node. Additionally, if there is a single usage that
 * is an {@link IfNode}, the control flow in the snippet is connected directly to the true
 * and false successors of the {@link IfNode}. This avoids materializating the instanceof
 * test as a boolean which is then retested by the {@link IfNode}.
 */
public abstract class InstanceOfSnippetsTemplates<T extends SnippetsInterface> extends AbstractTemplates<T> {

    public InstanceOfSnippetsTemplates(MetaAccessProvider runtime, Assumptions assumptions, Class<T> snippetsClass) {
        super(runtime, assumptions, snippetsClass);
    }

    /**
     * The key and arguments used to retrieve and instantiate an instanceof snippet template.
     */
    public static class KeyAndArguments {
        public final Key key;
        public final Arguments arguments;
        public KeyAndArguments(Key key, Arguments arguments) {
            this.key = key;
            this.arguments = arguments;
        }

    }

    /**
     * Gets the key and arguments used to retrieve and instantiate an instanceof snippet template.
     */
    protected abstract KeyAndArguments getKeyAndArguments(InstanceOfUsageReplacer replacer, LoweringTool tool);

    public void lower(InstanceOfNode instanceOf, LoweringTool tool) {
        List<Node> usages = instanceOf.usages().snapshot();
        int nUsages = usages.size();

        Instantiation instantiation = new Instantiation();
        for (Node usage : usages) {
            final StructuredGraph graph = (StructuredGraph) usage.graph();

            InstanceOfUsageReplacer replacer = createReplacer(instanceOf, tool, nUsages, instantiation, usage, graph);

            if (instantiation.isInitialized()) {
                // No need to re-instantiate the snippet - just re-use its result
                replacer.replaceUsingInstantiation();
            } else {
                KeyAndArguments keyAndArguments = getKeyAndArguments(replacer, tool);
                SnippetTemplate template = cache.get(keyAndArguments.key, assumptions);
                template.instantiate(runtime, instanceOf, replacer, tool.lastFixedNode(), keyAndArguments.arguments);
            }
        }

        assert instanceOf.usages().isEmpty();
        if (!instanceOf.isDeleted()) {
            GraphUtil.killWithUnusedFloatingInputs(instanceOf);
        }
    }

    /**
     * Gets the specific replacer object used to replace the usage of an instanceof node
     * with the result of an instantiated instanceof snippet.
     */
    protected InstanceOfUsageReplacer createReplacer(InstanceOfNode instanceOf, LoweringTool tool, int nUsages, Instantiation instantiation, Node usage, final StructuredGraph graph) {
        InstanceOfUsageReplacer replacer;
        if (usage instanceof IfNode) {
            replacer = new IfUsageReplacer(instantiation, ConstantNode.forInt(1, graph), ConstantNode.forInt(0, graph), instanceOf, (IfNode) usage, nUsages == 1, tool);
        } else {
            assert usage instanceof ConditionalNode : "unexpected usage of " + instanceOf + ": " + usage;
            ConditionalNode c = (ConditionalNode) usage;
            replacer = new ConditionalUsageReplacer(instantiation, c.trueValue(), c.falseValue(), instanceOf, c);
        }
        return replacer;
    }

    /**
     * The result of an instantiating an instanceof snippet.
     * This enables a snippet instantiation to be re-used which reduces compile time and produces better code.
     */
    public static final class Instantiation {
        private PhiNode result;
        private CompareNode condition;
        private ValueNode trueValue;
        private ValueNode falseValue;

        /**
         * Determines if the instantiation has occurred.
         */
        boolean isInitialized() {
            return result != null;
        }

        void initialize(PhiNode phi, ValueNode t, ValueNode f) {
            assert !isInitialized();
            this.result = phi;
            this.trueValue = t;
            this.falseValue = f;
        }

        /**
         * Gets the result of this instantiation as a condition.
         *
         * @param testValue the returned condition is true if the result is equal to this value
         */
        CompareNode asCondition(ValueNode testValue) {
            assert isInitialized();
            if (condition == null || condition.y() != testValue) {
                // Re-use previously generated condition if the trueValue for the test is the same
                condition = createCompareNode(Condition.EQ, result, testValue);
            }
            return condition;
        }

        /**
         * Gets the result of the instantiation as a materialized value.
         *
         * @param t the true value for the materialization
         * @param f the false value for the materialization
         */
        ValueNode asMaterialization(ValueNode t, ValueNode f) {
            assert isInitialized();
            if (t == this.trueValue && f == this.falseValue) {
                // Can simply use the phi result if the same materialized values are expected.
                return result;
            } else {
                return MaterializeNode.create(asCondition(trueValue), t, f);
            }
        }
    }

    /**
     * Replaces a usage of an {@link InstanceOfNode}.
     */
    public abstract static class InstanceOfUsageReplacer implements UsageReplacer {
        public final Instantiation instantiation;
        public final InstanceOfNode instanceOf;
        public final ValueNode trueValue;
        public final ValueNode falseValue;

        public InstanceOfUsageReplacer(Instantiation instantiation, InstanceOfNode instanceOf, ValueNode trueValue, ValueNode falseValue) {
            this.instantiation = instantiation;
            this.instanceOf = instanceOf;
            this.trueValue = trueValue;
            this.falseValue = falseValue;
        }

        /**
         * Does the replacement based on a previously snippet instantiation.
         */
        public abstract void replaceUsingInstantiation();
    }

    /**
     * Replaces an {@link IfNode} usage of an {@link InstanceOfNode}.
     */
    public static class IfUsageReplacer extends InstanceOfUsageReplacer {

        private final boolean solitaryUsage;
        private final IfNode usage;
        private final boolean sameBlock;

        public IfUsageReplacer(Instantiation instantiation, ValueNode trueValue, ValueNode falseValue, InstanceOfNode instanceOf, IfNode usage, boolean solitaryUsage, LoweringTool tool) {
            super(instantiation, instanceOf, trueValue, falseValue);
            this.sameBlock = tool.getBlockFor(usage) == tool.getBlockFor(instanceOf);
            this.solitaryUsage = solitaryUsage;
            this.usage = usage;
        }

        @Override
        public void replaceUsingInstantiation() {
            usage.replaceFirstInput(instanceOf, instantiation.asCondition(trueValue));
        }

        @Override
        public void replace(ValueNode oldNode, ValueNode newNode) {
            assert newNode instanceof PhiNode;
            assert oldNode == instanceOf;
            if (sameBlock && solitaryUsage) {
                removeIntermediateMaterialization(newNode);
            } else {
                newNode.inferStamp();
                instantiation.initialize((PhiNode) newNode, trueValue, falseValue);
                usage.replaceFirstInput(oldNode, instantiation.asCondition(trueValue));
            }
        }

        /**
         * Directly wires the incoming edges of the merge at the end of the snippet to
         * the outgoing edges of the IfNode that uses the materialized result.
         */
        private void removeIntermediateMaterialization(ValueNode newNode) {
            IfNode ifNode = usage;
            PhiNode phi = (PhiNode) newNode;
            MergeNode merge = phi.merge();
            assert merge.stateAfter() == null;

            List<EndNode> mergePredecessors = merge.cfgPredecessors().snapshot();
            assert phi.valueCount() == mergePredecessors.size();

            List<EndNode> falseEnds = new ArrayList<>(mergePredecessors.size());
            List<EndNode> trueEnds = new ArrayList<>(mergePredecessors.size());

            int endIndex = 0;
            for (EndNode end : mergePredecessors) {
                ValueNode endValue = phi.valueAt(endIndex++);
                if (endValue == trueValue) {
                    trueEnds.add(end);
                } else {
                    assert endValue == falseValue;
                    falseEnds.add(end);
                }
            }

            BeginNode trueSuccessor = ifNode.trueSuccessor();
            BeginNode falseSuccessor = ifNode.falseSuccessor();
            ifNode.setTrueSuccessor(null);
            ifNode.setFalseSuccessor(null);

            connectEnds(merge, trueEnds, trueSuccessor);
            connectEnds(merge, falseEnds, falseSuccessor);

            GraphUtil.killCFG(merge);
            GraphUtil.killCFG(ifNode);

            assert !merge.isAlive() : merge;
            assert !phi.isAlive() : phi;
        }

        private static void connectEnds(MergeNode merge, List<EndNode> ends, BeginNode successor) {
            if (ends.size() == 0) {
                // InstanceOf has been lowered to always true or always false - this successor is therefore unreachable.
                GraphUtil.killCFG(successor);
            } else if (ends.size() == 1) {
                EndNode end = ends.get(0);
                ((FixedWithNextNode) end.predecessor()).setNext(successor);
                merge.removeEnd(end);
                GraphUtil.killCFG(end);
            } else {
                assert ends.size() > 1;
                MergeNode newMerge = merge.graph().add(new MergeNode());

                for (EndNode end : ends) {
                    newMerge.addForwardEnd(end);
                }
                newMerge.setNext(successor);
            }
        }
    }

    /**
     * Replaces a {@link ConditionalNode} usage of an {@link InstanceOfNode}.
     */
    public static class ConditionalUsageReplacer extends InstanceOfUsageReplacer {

        public final ConditionalNode usage;

        public ConditionalUsageReplacer(Instantiation instantiation, ValueNode trueValue, ValueNode falseValue, InstanceOfNode instanceOf, ConditionalNode usage) {
            super(instantiation, instanceOf, trueValue, falseValue);
            this.usage = usage;
        }

        @Override
        public void replaceUsingInstantiation() {
            ValueNode newValue = instantiation.asMaterialization(trueValue, falseValue);
            usage.replaceAtUsages(newValue);
            usage.clearInputs();
            assert usage.usages().isEmpty();
            GraphUtil.killWithUnusedFloatingInputs(usage);
        }

        @Override
        public void replace(ValueNode oldNode, ValueNode newNode) {
            assert newNode instanceof PhiNode;
            assert oldNode == instanceOf;
            newNode.inferStamp();
            instantiation.initialize((PhiNode) newNode, trueValue, falseValue);
            usage.replaceAtUsages(newNode);
            usage.clearInputs();
            assert usage.usages().isEmpty();
            GraphUtil.killWithUnusedFloatingInputs(usage);
        }
    }
}
