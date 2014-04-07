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
package com.oracle.graal.hotspot.replacements;

import static com.oracle.graal.api.meta.DeoptimizationAction.*;
import static com.oracle.graal.api.meta.DeoptimizationReason.*;
import static com.oracle.graal.hotspot.HotSpotGraalRuntime.*;
import static com.oracle.graal.hotspot.replacements.HotSpotReplacementsUtil.*;
import static com.oracle.graal.hotspot.replacements.InstanceOfSnippets.Options.*;
import static com.oracle.graal.hotspot.replacements.TypeCheckSnippetUtils.*;
import static com.oracle.graal.nodes.extended.BranchProbabilityNode.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.meta.ProfilingInfo.TriState;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.hotspot.nodes.*;
import com.oracle.graal.hotspot.replacements.TypeCheckSnippetUtils.Hints;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.options.*;
import com.oracle.graal.phases.util.*;
import com.oracle.graal.replacements.*;
import com.oracle.graal.replacements.Snippet.ConstantParameter;
import com.oracle.graal.replacements.Snippet.VarargsParameter;
import com.oracle.graal.replacements.SnippetTemplate.Arguments;
import com.oracle.graal.replacements.SnippetTemplate.SnippetInfo;
import com.oracle.graal.replacements.nodes.*;
import com.oracle.graal.word.*;

/**
 * Snippets used for implementing the type test of an instanceof instruction. Since instanceof is a
 * floating node, it is lowered separately for each of its usages.
 *
 * The type tests implemented are described in the paper <a
 * href="http://dl.acm.org/citation.cfm?id=583821"> Fast subtype checking in the HotSpot JVM</a> by
 * Cliff Click and John Rose.
 */
public class InstanceOfSnippets implements Snippets {

    /**
     * Gets the minimum required probability of a profiled instanceof hitting one the profiled types
     * for use of the {@linkplain #instanceofWithProfile deoptimizing} snippet. The value is
     * computed to be an order of magnitude greater than the configured compilation threshold. For
     * example, if a method is compiled after being interpreted 10000 times, the deoptimizing
     * snippet will only be used for an instanceof if its profile indicates that less than 1 in
     * 100000 executions are for an object whose type is not one of the top N profiled types (where
     * {@code N == } {@link Options#TypeCheckMaxHints}).
     */
    public static double hintHitProbabilityThresholdForDeoptimizingSnippet() {
        return 1.0D - (1.0D / (runtime().getConfig().compileThreshold * 10));
    }

    /**
     * A test against a set of hints derived from a profile with very close to 100% precise coverage
     * of seen types. This snippet deoptimizes on hint miss paths.
     *
     * @see #hintHitProbabilityThresholdForDeoptimizingSnippet()
     */
    @Snippet
    public static Object instanceofWithProfile(Object object, @VarargsParameter Word[] hints, @VarargsParameter boolean[] hintIsPositive, Object trueValue, Object falseValue,
                    @ConstantParameter boolean nullSeen) {
        if (probability(NOT_FREQUENT_PROBABILITY, object == null)) {
            isNull.inc();
            if (!nullSeen) {
                // See comment below for other deoptimization path; the
                // same reasoning applies here.
                DeoptimizeNode.deopt(InvalidateReprofile, OptimizedTypeCheckViolated);
            }
            return falseValue;
        }
        GuardingNode anchorNode = SnippetAnchorNode.anchor();
        Word objectHub = loadHubIntrinsic(object, getWordKind(), anchorNode);
        // if we get an exact match: succeed immediately
        ExplodeLoopNode.explodeLoop();
        for (int i = 0; i < hints.length; i++) {
            Word hintHub = hints[i];
            boolean positive = hintIsPositive[i];
            if (probability(NOT_FREQUENT_PROBABILITY, hintHub.equal(objectHub))) {
                hintsHit.inc();
                return positive ? trueValue : falseValue;
            }
        }
        // This maybe just be a rare event but it might also indicate a phase change
        // in the application. Ideally we want to use DeoptimizationAction.None for
        // the former but the cost is too high if indeed it is the latter. As such,
        // we defensively opt for InvalidateReprofile.
        DeoptimizeNode.deopt(DeoptimizationAction.InvalidateReprofile, OptimizedTypeCheckViolated);
        return falseValue;
    }

    /**
     * A test against a final type.
     */
    @Snippet
    public static Object instanceofExact(Object object, Word exactHub, Object trueValue, Object falseValue) {
        if (probability(NOT_FREQUENT_PROBABILITY, object == null)) {
            isNull.inc();
            return falseValue;
        }
        GuardingNode anchorNode = SnippetAnchorNode.anchor();
        Word objectHub = loadHubIntrinsic(object, getWordKind(), anchorNode);
        if (probability(LIKELY_PROBABILITY, objectHub.notEqual(exactHub))) {
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
    public static Object instanceofPrimary(Word hub, Object object, @ConstantParameter int superCheckOffset, Object trueValue, Object falseValue) {
        if (probability(NOT_FREQUENT_PROBABILITY, object == null)) {
            isNull.inc();
            return falseValue;
        }
        GuardingNode anchorNode = SnippetAnchorNode.anchor();
        Word objectHub = loadHubIntrinsic(object, getWordKind(), anchorNode);
        if (probability(NOT_LIKELY_PROBABILITY, objectHub.readWord(superCheckOffset, LocationIdentity.FINAL_LOCATION).notEqual(hub))) {
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
    public static Object instanceofSecondary(Word hub, Object object, @VarargsParameter Word[] hints, @VarargsParameter boolean[] hintIsPositive, Object trueValue, Object falseValue) {
        if (probability(NOT_FREQUENT_PROBABILITY, object == null)) {
            isNull.inc();
            return falseValue;
        }
        GuardingNode anchorNode = SnippetAnchorNode.anchor();
        Word objectHub = loadHubIntrinsic(object, getWordKind(), anchorNode);
        // if we get an exact match: succeed immediately
        ExplodeLoopNode.explodeLoop();
        for (int i = 0; i < hints.length; i++) {
            Word hintHub = hints[i];
            boolean positive = hintIsPositive[i];
            if (probability(NOT_FREQUENT_PROBABILITY, hintHub.equal(objectHub))) {
                hintsHit.inc();
                return positive ? trueValue : falseValue;
            }
        }
        if (!checkSecondarySubType(hub, objectHub)) {
            return falseValue;
        }
        return trueValue;
    }

    /**
     * Type test used when the type being tested against is not known at compile time.
     */
    @Snippet
    public static Object instanceofDynamic(Class mirror, Object object, Object trueValue, Object falseValue) {
        if (probability(NOT_FREQUENT_PROBABILITY, object == null)) {
            isNull.inc();
            return falseValue;
        }
        GuardingNode anchorNode = SnippetAnchorNode.anchor();
        Word hub = loadWordFromObject(mirror, klassOffset());
        Word objectHub = loadHubIntrinsic(object, getWordKind(), anchorNode);
        if (hub.equal(0) || !checkUnknownSubType(hub, objectHub)) {
            return falseValue;
        }
        return trueValue;
    }

    static class Options {

        // @formatter:off
        @Option(help = "If the probability that a type check will hit one the profiled types (up to " +
                       "TypeCheckMaxHints) is below this value, the type check will be compiled without profiling info")
        static final OptionValue<Double> TypeCheckMinProfileHitProbability = new OptionValue<>(0.5);

        @Option(help = "The maximum number of profiled types that will be used when compiling a profiled type check. " +
                        "Note that TypeCheckMinProfileHitProbability also influences whether profiling info is used in compiled type checks.")
        static final OptionValue<Integer> TypeCheckMaxHints = new OptionValue<>(2);
        // @formatter:on
    }

    public static class Templates extends InstanceOfSnippetsTemplates {

        private final SnippetInfo instanceofWithProfile = snippet(InstanceOfSnippets.class, "instanceofWithProfile");
        private final SnippetInfo instanceofExact = snippet(InstanceOfSnippets.class, "instanceofExact");
        private final SnippetInfo instanceofPrimary = snippet(InstanceOfSnippets.class, "instanceofPrimary");
        private final SnippetInfo instanceofSecondary = snippet(InstanceOfSnippets.class, "instanceofSecondary");
        private final SnippetInfo instanceofDynamic = snippet(InstanceOfSnippets.class, "instanceofDynamic");

        public Templates(Providers providers, TargetDescription target) {
            super(providers, target);
        }

        @Override
        protected Arguments makeArguments(InstanceOfUsageReplacer replacer, LoweringTool tool) {
            if (replacer.instanceOf instanceof InstanceOfNode) {
                InstanceOfNode instanceOf = (InstanceOfNode) replacer.instanceOf;
                ValueNode object = instanceOf.object();
                TypeCheckHints hintInfo = new TypeCheckHints(instanceOf.type(), instanceOf.profile(), tool.assumptions(), TypeCheckMinProfileHitProbability.getValue(), TypeCheckMaxHints.getValue());
                final HotSpotResolvedObjectType type = (HotSpotResolvedObjectType) instanceOf.type();
                ConstantNode hub = ConstantNode.forConstant(type.klass(), providers.getMetaAccess(), instanceOf.graph());

                Arguments args;

                StructuredGraph graph = instanceOf.graph();
                if (hintInfo.hintHitProbability >= hintHitProbabilityThresholdForDeoptimizingSnippet() && hintInfo.exact == null) {
                    Hints hints = createHints(hintInfo, providers.getMetaAccess(), false, graph);
                    args = new Arguments(instanceofWithProfile, graph.getGuardsStage(), tool.getLoweringStage());
                    args.add("object", object);
                    Kind wordKind = providers.getCodeCache().getTarget().wordKind;
                    args.addVarargs("hints", Word.class, StampFactory.forKind(wordKind), hints.hubs);
                    args.addVarargs("hintIsPositive", boolean.class, StampFactory.forKind(Kind.Boolean), hints.isPositive);
                } else if (hintInfo.exact != null) {
                    args = new Arguments(instanceofExact, graph.getGuardsStage(), tool.getLoweringStage());
                    args.add("object", object);
                    args.add("exactHub", ConstantNode.forConstant(((HotSpotResolvedObjectType) hintInfo.exact).klass(), providers.getMetaAccess(), graph));
                } else if (type.isPrimaryType()) {
                    args = new Arguments(instanceofPrimary, graph.getGuardsStage(), tool.getLoweringStage());
                    args.add("hub", hub);
                    args.add("object", object);
                    args.addConst("superCheckOffset", type.superCheckOffset());
                } else {
                    Hints hints = createHints(hintInfo, providers.getMetaAccess(), false, graph);
                    args = new Arguments(instanceofSecondary, graph.getGuardsStage(), tool.getLoweringStage());
                    args.add("hub", hub);
                    args.add("object", object);
                    args.addVarargs("hints", Word.class, StampFactory.forKind(getWordKind()), hints.hubs);
                    args.addVarargs("hintIsPositive", boolean.class, StampFactory.forKind(Kind.Boolean), hints.isPositive);
                }
                args.add("trueValue", replacer.trueValue);
                args.add("falseValue", replacer.falseValue);
                if (hintInfo.hintHitProbability >= hintHitProbabilityThresholdForDeoptimizingSnippet() && hintInfo.exact == null) {
                    args.addConst("nullSeen", hintInfo.profile.getNullSeen() != TriState.FALSE);
                }
                return args;

            } else {
                assert replacer.instanceOf instanceof InstanceOfDynamicNode;
                InstanceOfDynamicNode instanceOf = (InstanceOfDynamicNode) replacer.instanceOf;
                ValueNode object = instanceOf.object();

                Arguments args = new Arguments(instanceofDynamic, instanceOf.graph().getGuardsStage(), tool.getLoweringStage());
                args.add("mirror", instanceOf.mirror());
                args.add("object", object);
                args.add("trueValue", replacer.trueValue);
                args.add("falseValue", replacer.falseValue);
                return args;
            }
        }
    }
}
