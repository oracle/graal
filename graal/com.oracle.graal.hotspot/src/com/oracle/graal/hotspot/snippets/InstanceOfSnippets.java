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
package com.oracle.graal.hotspot.snippets;
import static com.oracle.graal.hotspot.snippets.CheckCastSnippets.*;
import static com.oracle.graal.hotspot.snippets.CheckCastSnippets.Templates.*;
import static com.oracle.graal.hotspot.snippets.HotSpotSnippetUtils.*;
import static com.oracle.graal.nodes.MaterializeNode.*;
import static com.oracle.graal.snippets.Snippet.Varargs.*;
import static com.oracle.graal.snippets.SnippetTemplate.Arguments.*;

import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.util.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.snippets.*;
import com.oracle.graal.snippets.Snippet.ConstantParameter;
import com.oracle.graal.snippets.Snippet.Parameter;
import com.oracle.graal.snippets.Snippet.VarargsParameter;
import com.oracle.graal.snippets.SnippetTemplate.AbstractTemplates;
import com.oracle.graal.snippets.SnippetTemplate.Arguments;
import com.oracle.graal.snippets.SnippetTemplate.Key;
import com.oracle.graal.snippets.SnippetTemplate.UsageReplacer;
import com.oracle.graal.snippets.nodes.*;

/**
 * Snippets used for implementing the type test of an instanceof instruction.
 * Since instanceof is a floating node, it is lowered separately for each of
 * its usages.
 *
 * The type tests implemented are described in the paper <a href="http://dl.acm.org/citation.cfm?id=583821">
 * Fast subtype checking in the HotSpot JVM</a> by Cliff Click and John Rose.
 */
public class InstanceOfSnippets implements SnippetsInterface {

    /**
     * A test against a final type.
     */
    @Snippet
    public static Object instanceofExact(
                    @Parameter("object") Object object,
                    @Parameter("exactHub") Object exactHub,
                    @Parameter("trueValue") Object trueValue,
                    @Parameter("falseValue") Object falseValue,
                    @ConstantParameter("checkNull") boolean checkNull) {
        if (checkNull && object == null) {
            isNull.inc();
            return falseValue;
        }
        Object objectHub = loadHub(object);
        if (objectHub != exactHub) {
            exactMiss.inc();
            return falseValue;
        }
        exactHit.inc();
        return trueValue;
    }

    /**
     * A test against a primary type.
     */
    @Snippet
    public static Object instanceofPrimary(
                    @Parameter("hub") Object hub,
                    @Parameter("object") Object object,
                    @Parameter("trueValue") Object trueValue,
                    @Parameter("falseValue") Object falseValue,
                    @ConstantParameter("checkNull") boolean checkNull,
                    @ConstantParameter("superCheckOffset") int superCheckOffset) {
        if (checkNull && object == null) {
            isNull.inc();
            return falseValue;
        }
        Object objectHub = loadHub(object);
        if (UnsafeLoadNode.loadObject(objectHub, 0, superCheckOffset, true) != hub) {
            displayMiss.inc();
            return falseValue;
        }
        displayHit.inc();
        return trueValue;
    }

    /**
     * A test against a restricted secondary type type.
     */
    @Snippet
    public static Object instanceofSecondary(
                    @Parameter("hub") Object hub,
                    @Parameter("object") Object object,
                    @Parameter("trueValue") Object trueValue,
                    @Parameter("falseValue") Object falseValue,
                    @VarargsParameter("hints") Object[] hints,
                    @ConstantParameter("checkNull") boolean checkNull) {
        if (checkNull && object == null) {
            isNull.inc();
            return falseValue;
        }
        Object objectHub = loadHub(object);
        // if we get an exact match: succeed immediately
        ExplodeLoopNode.explodeLoop();
        for (int i = 0; i < hints.length; i++) {
            Object hintHub = hints[i];
            if (hintHub == objectHub) {
                hintsHit.inc();
                return trueValue;
            }
        }
        if (!checkSecondarySubType(hub, objectHub)) {
            return falseValue;
        }
        return trueValue;
    }

    static boolean checkSecondarySubType(Object t, Object s) {
        // if (S.cache == T) return true
        if (UnsafeLoadNode.loadObject(s, 0, secondarySuperCacheOffset(), true) == t) {
            cacheHit.inc();
            return true;
        }

        // if (T == S) return true
        if (s == t) {
            T_equals_S.inc();
            return true;
        }

        // if (S.scan_s_s_array(T)) { S.cache = T; return true; }
        Object[] secondarySupers = UnsafeCastNode.cast(UnsafeLoadNode.loadObject(s, 0, secondarySupersOffset(), true), Object[].class);

        for (int i = 0; i < secondarySupers.length; i++) {
            if (t == loadNonNullObjectElement(secondarySupers, i)) {
                DirectObjectStoreNode.storeObject(s, secondarySuperCacheOffset(), 0, t);
                secondariesHit.inc();
                return true;
            }
        }
        secondariesMiss.inc();
        return false;
    }

    public static class Templates extends AbstractTemplates<InstanceOfSnippets> {

        private final ResolvedJavaMethod instanceofExact;
        private final ResolvedJavaMethod instanceofPrimary;
        private final ResolvedJavaMethod instanceofSecondary;

        public Templates(CodeCacheProvider runtime) {
            super(runtime, InstanceOfSnippets.class);
            instanceofExact = snippet("instanceofExact", Object.class, Object.class, Object.class, Object.class, boolean.class);
            instanceofPrimary = snippet("instanceofPrimary", Object.class, Object.class, Object.class, Object.class, boolean.class, int.class);
            instanceofSecondary = snippet("instanceofSecondary", Object.class, Object.class, Object.class, Object.class, Object[].class, boolean.class);
        }

        public void lower(InstanceOfNode instanceOf, LoweringTool tool) {
            ValueNode object = instanceOf.object();
            TypeCheckHints hintInfo = new TypeCheckHints(instanceOf.type(), instanceOf.profile(), tool.assumptions(), GraalOptions.CheckcastMinHintHitProbability, GraalOptions.CheckcastMaxHints);
            final HotSpotResolvedJavaType type = (HotSpotResolvedJavaType) instanceOf.type();
            ConstantNode hub = ConstantNode.forObject(type.klassOop(), runtime, instanceOf.graph());
            boolean checkNull = !object.stamp().nonNull();

            List<Node> usages = instanceOf.usages().snapshot();
            int nUsages = usages.size();

            Instantiation instantiation = new Instantiation();
            for (Node usage : usages) {
                final StructuredGraph graph = (StructuredGraph) usage.graph();

                InstanceOfUsageReplacer replacer;
                ValueNode trueValue;
                ValueNode falseValue;
                if (usage instanceof IfNode) {
                    trueValue = ConstantNode.forInt(1, graph);
                    falseValue = ConstantNode.forInt(0, graph);
                    replacer = new IfUsageReplacer(instantiation, trueValue, falseValue, instanceOf, (IfNode) usage, nUsages == 1, tool);
                } else {
                    assert usage instanceof ConditionalNode : "unexpected usage of " + instanceOf + ": " + usage;
                    ConditionalNode conditional = (ConditionalNode) usage;
                    trueValue = conditional.trueValue();
                    falseValue = conditional.falseValue();
                    replacer = new ConditionalUsageReplacer(instantiation, trueValue, falseValue, instanceOf, conditional);
                }

                if (instantiation.isInitialized()) {
                    // No need to re-instantiate the snippet - just re-use its result
                    replacer.replaceUsingInstantiation();
                } else {
                    Arguments arguments;
                    Key key;
                    if (hintInfo.exact) {
                        HotSpotKlassOop[] hints = createHints(hintInfo);
                        assert hints.length == 1;
                        key = new Key(instanceofExact).add("checkNull", checkNull);
                        arguments = arguments("object", object).add("exactHub", hints[0]).add("trueValue", trueValue).add("falseValue", falseValue);
                    } else if (type.isPrimaryType()) {
                        key = new Key(instanceofPrimary).add("checkNull", checkNull).add("superCheckOffset", type.superCheckOffset());
                        arguments = arguments("hub", hub).add("object", object).add("trueValue", trueValue).add("falseValue", falseValue);
                    } else {
                        HotSpotKlassOop[] hints = createHints(hintInfo);
                        key = new Key(instanceofSecondary).add("hints", vargargs(Object.class, hints.length)).add("checkNull", checkNull);
                        arguments = arguments("hub", hub).add("object", object).add("hints", hints).add("trueValue", trueValue).add("falseValue", falseValue);
                    }

                    SnippetTemplate template = cache.get(key);
                    template.instantiate(runtime, instanceOf, replacer, tool.lastFixedNode(), arguments);
                }
            }

            assert instanceOf.usages().isEmpty();
            if (!instanceOf.isDeleted()) {
                GraphUtil.killWithUnusedFloatingInputs(instanceOf);
            }
        }

        /**
         * The result of an instantiating an instanceof snippet.
         * This enables a snippet instantiation to be re-used which reduces compile time and produces better code.
         */
        static final class Instantiation {
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
        abstract static class InstanceOfUsageReplacer implements UsageReplacer {
            protected final Instantiation instantiation;
            protected final InstanceOfNode instanceOf;
            protected final ValueNode trueValue;
            protected final ValueNode falseValue;

            public InstanceOfUsageReplacer(Instantiation instantiation, InstanceOfNode instanceOf, ValueNode trueValue, ValueNode falseValue) {
                this.instantiation = instantiation;
                this.instanceOf = instanceOf;
                this.trueValue = trueValue;
                this.falseValue = falseValue;
            }

            /**
             * Does the replacement based on a previously snippet instantiation.
             */
            abstract void replaceUsingInstantiation();
        }

        /**
         * Replaces an {@link IfNode} usage of an {@link InstanceOfNode}.
         */
        static final class IfUsageReplacer extends InstanceOfUsageReplacer {

            private final boolean solitaryUsage;
            private final IfNode usage;
            private final boolean sameBlock;

            private IfUsageReplacer(Instantiation instantiation, ValueNode trueValue, ValueNode falseValue, InstanceOfNode instanceOf, IfNode usage, boolean solitaryUsage, LoweringTool tool) {
                super(instantiation, instanceOf, trueValue, falseValue);
                this.sameBlock = tool.getBlockFor(usage) == tool.getBlockFor(instanceOf);
                this.solitaryUsage = solitaryUsage;
                this.usage = usage;
            }

            @Override
            void replaceUsingInstantiation() {
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
                if (ends.size() == 1) {
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
        static final class ConditionalUsageReplacer extends InstanceOfUsageReplacer {

            private final ConditionalNode usage;

            private ConditionalUsageReplacer(Instantiation instantiation, ValueNode trueValue, ValueNode falseValue, InstanceOfNode instanceOf, ConditionalNode usage) {
                super(instantiation, instanceOf, trueValue, falseValue);
                this.usage = usage;
            }

            @Override
            void replaceUsingInstantiation() {
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

    private static final SnippetCounter.Group counters = GraalOptions.SnippetCounters ? new SnippetCounter.Group("Checkcast") : null;
    private static final SnippetCounter hintsHit = new SnippetCounter(counters, "hintsHit", "hit a hint type");
    private static final SnippetCounter exactHit = new SnippetCounter(counters, "exactHit", "exact type test succeeded");
    private static final SnippetCounter exactMiss = new SnippetCounter(counters, "exactMiss", "exact type test failed");
    private static final SnippetCounter isNull = new SnippetCounter(counters, "isNull", "object tested was null");
    private static final SnippetCounter cacheHit = new SnippetCounter(counters, "cacheHit", "secondary type cache hit");
    private static final SnippetCounter secondariesHit = new SnippetCounter(counters, "secondariesHit", "secondaries scan succeeded");
    private static final SnippetCounter secondariesMiss = new SnippetCounter(counters, "secondariesMiss", "secondaries scan failed");
    private static final SnippetCounter displayHit = new SnippetCounter(counters, "displayHit", "primary type test succeeded");
    private static final SnippetCounter displayMiss = new SnippetCounter(counters, "displayMiss", "primary type test failed");
    private static final SnippetCounter T_equals_S = new SnippetCounter(counters, "T_equals_S", "object type was equal to secondary type");
}
