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
package com.oracle.graal.replacements;

import static com.oracle.graal.nodes.calc.CompareNode.*;

import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.replacements.*;
import com.oracle.graal.compiler.common.calc.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.util.*;
import com.oracle.graal.phases.util.*;
import com.oracle.graal.replacements.SnippetTemplate.AbstractTemplates;
import com.oracle.graal.replacements.SnippetTemplate.Arguments;
import com.oracle.graal.replacements.SnippetTemplate.UsageReplacer;

/**
 * Helper class for lowering {@link InstanceOfNode}s with snippets. The majority of the complexity
 * in such a lowering derives from the fact that {@link InstanceOfNode} is a floating node. A
 * snippet used to lower an {@link InstanceOfNode} will almost always incorporate control flow and
 * replacing a floating node with control flow is not trivial.
 * <p>
 * The mechanism implemented in this class ensures that the graph for an instanceof snippet is
 * instantiated once per {@link InstanceOfNode} being lowered. The result produced is then re-used
 * by all usages of the node. Additionally, if there is a single usage that is an {@link IfNode},
 * the control flow in the snippet is connected directly to the true and false successors of the
 * {@link IfNode}. This avoids materializing the instanceof test as a boolean which is then retested
 * by the {@link IfNode}.
 */
public abstract class InstanceOfSnippetsTemplates extends AbstractTemplates {

    public InstanceOfSnippetsTemplates(Providers providers, SnippetReflectionProvider snippetReflection, TargetDescription target) {
        super(providers, snippetReflection, target);
    }

    /**
     * Gets the arguments used to retrieve and instantiate an instanceof snippet template.
     */
    protected abstract Arguments makeArguments(InstanceOfUsageReplacer replacer, LoweringTool tool);

    public void lower(FloatingNode instanceOf, LoweringTool tool) {
        assert instanceOf instanceof InstanceOfNode || instanceOf instanceof InstanceOfDynamicNode;
        List<Node> usages = instanceOf.usages().snapshot();

        Instantiation instantiation = new Instantiation();
        for (Node usage : usages) {
            final StructuredGraph graph = (StructuredGraph) usage.graph();

            InstanceOfUsageReplacer replacer = createReplacer(instanceOf, instantiation, usage, graph);

            if (instantiation.isInitialized()) {
                // No need to re-instantiate the snippet - just re-use its result
                replacer.replaceUsingInstantiation();
            } else {
                Arguments args = makeArguments(replacer, tool);
                template(args).instantiate(providers.getMetaAccess(), instanceOf, replacer, tool, args);
            }
        }

        assert instanceOf.usages().isEmpty();
        if (!instanceOf.isDeleted()) {
            GraphUtil.killWithUnusedFloatingInputs(instanceOf);
        }
    }

    /**
     * Gets the specific replacer object used to replace the usage of an instanceof node with the
     * result of an instantiated instanceof snippet.
     */
    protected InstanceOfUsageReplacer createReplacer(FloatingNode instanceOf, Instantiation instantiation, Node usage, final StructuredGraph graph) {
        InstanceOfUsageReplacer replacer;
        if (usage instanceof IfNode || usage instanceof FixedGuardNode || usage instanceof ShortCircuitOrNode || usage instanceof GuardingPiNode || usage instanceof ConditionAnchorNode) {
            replacer = new NonMaterializationUsageReplacer(instantiation, ConstantNode.forInt(1, graph), ConstantNode.forInt(0, graph), instanceOf, usage);
        } else {
            assert usage instanceof ConditionalNode : "unexpected usage of " + instanceOf + ": " + usage;
            ConditionalNode c = (ConditionalNode) usage;
            replacer = new MaterializationUsageReplacer(instantiation, c.trueValue(), c.falseValue(), instanceOf, c);
        }
        return replacer;
    }

    /**
     * The result of instantiating an instanceof snippet. This enables a snippet instantiation to be
     * re-used which reduces compile time and produces better code.
     */
    public static final class Instantiation {

        private ValueNode result;
        private CompareNode condition;
        private ValueNode trueValue;
        private ValueNode falseValue;

        /**
         * Determines if the instantiation has occurred.
         */
        boolean isInitialized() {
            return result != null;
        }

        void initialize(ValueNode r, ValueNode t, ValueNode f) {
            assert !isInitialized();
            this.result = r;
            this.trueValue = t;
            this.falseValue = f;
        }

        /**
         * Gets the result of this instantiation as a condition.
         *
         * @param testValue the returned condition is true if the result is equal to this value
         */
        LogicNode asCondition(ValueNode testValue) {
            assert isInitialized();
            if (result.isConstant()) {
                assert testValue.isConstant();
                return LogicConstantNode.forBoolean(result.asConstant().equals(testValue.asConstant()), result.graph());
            }
            if (condition == null || condition.y() != testValue) {
                // Re-use previously generated condition if the trueValue for the test is the same
                condition = createCompareNode(result.graph(), Condition.EQ, result, testValue);
            }
            return condition;
        }

        /**
         * Gets the result of the instantiation as a materialized value.
         *
         * @param t the true value for the materialization
         * @param f the false value for the materialization
         */
        ValueNode asMaterialization(StructuredGraph graph, ValueNode t, ValueNode f) {
            assert isInitialized();
            if (t == this.trueValue && f == this.falseValue) {
                // Can simply use the phi result if the same materialized values are expected.
                return result;
            } else {
                return graph.unique(new ConditionalNode(asCondition(trueValue), t, f));
            }
        }
    }

    /**
     * Replaces a usage of an {@link InstanceOfNode} or {@link InstanceOfDynamicNode}.
     */
    public abstract static class InstanceOfUsageReplacer implements UsageReplacer {

        public final Instantiation instantiation;
        public final FloatingNode instanceOf;
        public final ValueNode trueValue;
        public final ValueNode falseValue;

        public InstanceOfUsageReplacer(Instantiation instantiation, FloatingNode instanceOf, ValueNode trueValue, ValueNode falseValue) {
            assert instanceOf instanceof InstanceOfNode || instanceOf instanceof InstanceOfDynamicNode;
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
     * Replaces the usage of an {@link InstanceOfNode} or {@link InstanceOfDynamicNode} that does
     * not materialize the result of the type test.
     */
    public static class NonMaterializationUsageReplacer extends InstanceOfUsageReplacer {

        private final Node usage;

        public NonMaterializationUsageReplacer(Instantiation instantiation, ValueNode trueValue, ValueNode falseValue, FloatingNode instanceOf, Node usage) {
            super(instantiation, instanceOf, trueValue, falseValue);
            this.usage = usage;
        }

        @Override
        public void replaceUsingInstantiation() {
            usage.replaceFirstInput(instanceOf, instantiation.asCondition(trueValue));
        }

        @Override
        public void replace(ValueNode oldNode, ValueNode newNode, MemoryMapNode mmap) {
            assert newNode instanceof PhiNode;
            assert oldNode == instanceOf;
            newNode.inferStamp();
            instantiation.initialize(newNode, trueValue, falseValue);
            usage.replaceFirstInput(oldNode, instantiation.asCondition(trueValue));
        }
    }

    /**
     * Replaces the usage of an {@link InstanceOfNode} or {@link InstanceOfDynamicNode} that does
     * materializes the result of the type test.
     */
    public static class MaterializationUsageReplacer extends InstanceOfUsageReplacer {

        public final ConditionalNode usage;

        public MaterializationUsageReplacer(Instantiation instantiation, ValueNode trueValue, ValueNode falseValue, FloatingNode instanceOf, ConditionalNode usage) {
            super(instantiation, instanceOf, trueValue, falseValue);
            this.usage = usage;
        }

        @Override
        public void replaceUsingInstantiation() {
            ValueNode newValue = instantiation.asMaterialization(usage.graph(), trueValue, falseValue);
            usage.replaceAtUsages(newValue);
            assert usage.usages().isEmpty();
            GraphUtil.killWithUnusedFloatingInputs(usage);
        }

        @Override
        public void replace(ValueNode oldNode, ValueNode newNode, MemoryMapNode mmap) {
            assert newNode instanceof PhiNode;
            assert oldNode == instanceOf;
            newNode.inferStamp();
            instantiation.initialize(newNode, trueValue, falseValue);
            usage.replaceAtUsages(newNode);
            assert usage.usages().isEmpty();
            GraphUtil.killWithUnusedFloatingInputs(usage);
        }
    }
}
