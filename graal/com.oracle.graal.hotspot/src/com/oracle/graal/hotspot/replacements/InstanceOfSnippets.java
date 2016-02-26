/*
 * Copyright (c) 2012, 2015, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.hotspot.replacements.HotSpotReplacementsUtil.PRIMARY_SUPERS_LOCATION;
import static com.oracle.graal.hotspot.replacements.HotSpotReplacementsUtil.SECONDARY_SUPER_CACHE_LOCATION;
import static com.oracle.graal.hotspot.replacements.HotSpotReplacementsUtil.loadHubIntrinsic;
import static com.oracle.graal.hotspot.replacements.HotspotSnippetsOptions.TypeCheckMaxHints;
import static com.oracle.graal.hotspot.replacements.HotspotSnippetsOptions.TypeCheckMinProfileHitProbability;
import static com.oracle.graal.hotspot.replacements.TypeCheckSnippetUtils.checkSecondarySubType;
import static com.oracle.graal.hotspot.replacements.TypeCheckSnippetUtils.checkUnknownSubType;
import static com.oracle.graal.hotspot.replacements.TypeCheckSnippetUtils.createHints;
import static com.oracle.graal.hotspot.replacements.TypeCheckSnippetUtils.displayHit;
import static com.oracle.graal.hotspot.replacements.TypeCheckSnippetUtils.displayMiss;
import static com.oracle.graal.hotspot.replacements.TypeCheckSnippetUtils.exactHit;
import static com.oracle.graal.hotspot.replacements.TypeCheckSnippetUtils.exactMiss;
import static com.oracle.graal.hotspot.replacements.TypeCheckSnippetUtils.hintsHit;
import static com.oracle.graal.hotspot.replacements.TypeCheckSnippetUtils.hintsMiss;
import static com.oracle.graal.hotspot.replacements.TypeCheckSnippetUtils.isNull;
import static com.oracle.graal.nodes.extended.BranchProbabilityNode.LIKELY_PROBABILITY;
import static com.oracle.graal.nodes.extended.BranchProbabilityNode.NOT_FREQUENT_PROBABILITY;
import static com.oracle.graal.nodes.extended.BranchProbabilityNode.NOT_LIKELY_PROBABILITY;
import static com.oracle.graal.nodes.extended.BranchProbabilityNode.probability;
import static jdk.vm.ci.meta.DeoptimizationAction.InvalidateReprofile;
import static jdk.vm.ci.meta.DeoptimizationReason.OptimizedTypeCheckViolated;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.hotspot.HotSpotResolvedObjectType;
import jdk.vm.ci.meta.Assumptions;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.TriState;

import com.oracle.graal.compiler.common.type.StampFactory;
import com.oracle.graal.hotspot.meta.HotSpotProviders;
import com.oracle.graal.hotspot.nodes.SnippetAnchorNode;
import com.oracle.graal.hotspot.nodes.type.KlassPointerStamp;
import com.oracle.graal.hotspot.replacements.TypeCheckSnippetUtils.Hints;
import com.oracle.graal.hotspot.word.KlassPointer;
import com.oracle.graal.nodes.ConstantNode;
import com.oracle.graal.nodes.DeoptimizeNode;
import com.oracle.graal.nodes.PiNode;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.TypeCheckHints;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.extended.BranchProbabilityNode;
import com.oracle.graal.nodes.extended.GuardingNode;
import com.oracle.graal.nodes.java.ClassIsAssignableFromNode;
import com.oracle.graal.nodes.java.InstanceOfDynamicNode;
import com.oracle.graal.nodes.java.InstanceOfNode;
import com.oracle.graal.nodes.java.TypeCheckNode;
import com.oracle.graal.nodes.spi.LoweringTool;
import com.oracle.graal.replacements.InstanceOfSnippetsTemplates;
import com.oracle.graal.replacements.Snippet;
import com.oracle.graal.replacements.Snippet.ConstantParameter;
import com.oracle.graal.replacements.Snippet.VarargsParameter;
import com.oracle.graal.replacements.SnippetTemplate.Arguments;
import com.oracle.graal.replacements.SnippetTemplate.SnippetInfo;
import com.oracle.graal.replacements.Snippets;
import com.oracle.graal.replacements.nodes.ExplodeLoopNode;

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
     * A test against a set of hints derived from a profile with 100% precise coverage of seen
     * types. This snippet deoptimizes on hint miss paths.
     */
    @Snippet
    public static Object instanceofWithProfile(Object object, @VarargsParameter KlassPointer[] hints, @VarargsParameter boolean[] hintIsPositive, Object trueValue, Object falseValue,
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
        KlassPointer objectHub = loadHubIntrinsic(PiNode.piCastNonNull(object, anchorNode));
        // if we get an exact match: succeed immediately
        ExplodeLoopNode.explodeLoop();
        for (int i = 0; i < hints.length; i++) {
            KlassPointer hintHub = hints[i];
            boolean positive = hintIsPositive[i];
            if (probability(LIKELY_PROBABILITY, hintHub.equal(objectHub))) {
                hintsHit.inc();
                return positive ? trueValue : falseValue;
            }
            hintsMiss.inc();
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
    public static Object instanceofExact(Object object, KlassPointer exactHub, Object trueValue, Object falseValue) {
        if (probability(NOT_FREQUENT_PROBABILITY, object == null)) {
            isNull.inc();
            return falseValue;
        }
        GuardingNode anchorNode = SnippetAnchorNode.anchor();
        KlassPointer objectHub = loadHubIntrinsic(PiNode.piCastNonNull(object, anchorNode));
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
    public static Object instanceofPrimary(KlassPointer hub, Object object, @ConstantParameter int superCheckOffset, Object trueValue, Object falseValue) {
        if (probability(NOT_FREQUENT_PROBABILITY, object == null)) {
            isNull.inc();
            return falseValue;
        }
        GuardingNode anchorNode = SnippetAnchorNode.anchor();
        KlassPointer objectHub = loadHubIntrinsic(PiNode.piCastNonNull(object, anchorNode));
        if (probability(NOT_LIKELY_PROBABILITY, objectHub.readKlassPointer(superCheckOffset, PRIMARY_SUPERS_LOCATION).notEqual(hub))) {
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
    public static Object instanceofSecondary(KlassPointer hub, Object object, @VarargsParameter KlassPointer[] hints, @VarargsParameter boolean[] hintIsPositive, Object trueValue, Object falseValue) {
        if (probability(NOT_FREQUENT_PROBABILITY, object == null)) {
            isNull.inc();
            return falseValue;
        }
        GuardingNode anchorNode = SnippetAnchorNode.anchor();
        KlassPointer objectHub = loadHubIntrinsic(PiNode.piCastNonNull(object, anchorNode));
        // if we get an exact match: succeed immediately
        ExplodeLoopNode.explodeLoop();
        for (int i = 0; i < hints.length; i++) {
            KlassPointer hintHub = hints[i];
            boolean positive = hintIsPositive[i];
            if (probability(NOT_FREQUENT_PROBABILITY, hintHub.equal(objectHub))) {
                hintsHit.inc();
                return positive ? trueValue : falseValue;
            }
        }
        hintsMiss.inc();
        if (!checkSecondarySubType(hub, objectHub)) {
            return falseValue;
        }
        return trueValue;
    }

    /**
     * Type test used when the type being tested against is not known at compile time.
     */
    @Snippet
    public static Object instanceofDynamic(Class<?> mirror, Object object, Object trueValue, Object falseValue) {
        if (probability(NOT_FREQUENT_PROBABILITY, object == null)) {
            isNull.inc();
            return falseValue;
        }
        GuardingNode anchorNode = SnippetAnchorNode.anchor();
        KlassPointer hub = ClassGetHubNode.readClass(mirror, anchorNode);
        KlassPointer objectHub = loadHubIntrinsic(PiNode.piCastNonNull(object, anchorNode));
        if (hub.isNull() || !checkUnknownSubType(hub, objectHub)) {
            return falseValue;
        }
        return trueValue;
    }

    @Snippet
    public static Object isAssignableFrom(Class<?> thisClass, Class<?> otherClass, Object trueValue, Object falseValue) {
        if (BranchProbabilityNode.probability(BranchProbabilityNode.NOT_FREQUENT_PROBABILITY, otherClass == null)) {
            DeoptimizeNode.deopt(DeoptimizationAction.InvalidateReprofile, DeoptimizationReason.NullCheckException);
            return false;
        }
        GuardingNode anchorNode = SnippetAnchorNode.anchor();
        KlassPointer thisHub = ClassGetHubNode.readClass(thisClass, anchorNode);
        KlassPointer otherHub = ClassGetHubNode.readClass(otherClass, anchorNode);
        if (thisHub.isNull() || otherHub.isNull()) {
            // primitive types, only true if equal.
            return thisClass == otherClass ? trueValue : falseValue;
        }
        if (!TypeCheckSnippetUtils.checkUnknownSubType(thisHub, otherHub)) {
            return falseValue;
        }
        return trueValue;
    }

    public static class Templates extends InstanceOfSnippetsTemplates {

        private final SnippetInfo instanceofWithProfile = snippet(InstanceOfSnippets.class, "instanceofWithProfile");
        private final SnippetInfo instanceofExact = snippet(InstanceOfSnippets.class, "instanceofExact");
        private final SnippetInfo instanceofPrimary = snippet(InstanceOfSnippets.class, "instanceofPrimary");
        private final SnippetInfo instanceofSecondary = snippet(InstanceOfSnippets.class, "instanceofSecondary", SECONDARY_SUPER_CACHE_LOCATION);
        private final SnippetInfo instanceofDynamic = snippet(InstanceOfSnippets.class, "instanceofDynamic", SECONDARY_SUPER_CACHE_LOCATION);
        private final SnippetInfo isAssignableFrom = snippet(InstanceOfSnippets.class, "isAssignableFrom", SECONDARY_SUPER_CACHE_LOCATION);

        public Templates(HotSpotProviders providers, TargetDescription target) {
            super(providers, providers.getSnippetReflection(), target);
        }

        @Override
        protected Arguments makeArguments(InstanceOfUsageReplacer replacer, LoweringTool tool) {
            if (replacer.instanceOf instanceof InstanceOfNode) {
                InstanceOfNode instanceOf = (InstanceOfNode) replacer.instanceOf;
                ValueNode object = instanceOf.getValue();
                Assumptions assumptions = instanceOf.graph().getAssumptions();
                TypeCheckHints hintInfo = new TypeCheckHints(instanceOf.type().getType(), instanceOf.profile(), assumptions, TypeCheckMinProfileHitProbability.getValue(), TypeCheckMaxHints.getValue());
                final HotSpotResolvedObjectType type = (HotSpotResolvedObjectType) instanceOf.type().getType();
                ConstantNode hub = ConstantNode.forConstant(KlassPointerStamp.klassNonNull(), type.klass(), providers.getMetaAccess(), instanceOf.graph());

                Arguments args;

                StructuredGraph graph = instanceOf.graph();
                if (hintInfo.hintHitProbability >= 1.0 && hintInfo.exact == null) {
                    Hints hints = createHints(hintInfo, providers.getMetaAccess(), false, graph);
                    args = new Arguments(instanceofWithProfile, graph.getGuardsStage(), tool.getLoweringStage());
                    args.add("object", object);
                    args.addVarargs("hints", KlassPointer.class, KlassPointerStamp.klassNonNull(), hints.hubs);
                    args.addVarargs("hintIsPositive", boolean.class, StampFactory.forKind(JavaKind.Boolean), hints.isPositive);
                } else if (hintInfo.exact != null) {
                    args = new Arguments(instanceofExact, graph.getGuardsStage(), tool.getLoweringStage());
                    args.add("object", object);
                    args.add("exactHub", ConstantNode.forConstant(KlassPointerStamp.klassNonNull(), ((HotSpotResolvedObjectType) hintInfo.exact).klass(), providers.getMetaAccess(), graph));
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
                    args.addVarargs("hints", KlassPointer.class, KlassPointerStamp.klassNonNull(), hints.hubs);
                    args.addVarargs("hintIsPositive", boolean.class, StampFactory.forKind(JavaKind.Boolean), hints.isPositive);
                }
                args.add("trueValue", replacer.trueValue);
                args.add("falseValue", replacer.falseValue);
                if (hintInfo.hintHitProbability >= 1.0 && hintInfo.exact == null) {
                    args.addConst("nullSeen", hintInfo.profile.getNullSeen() != TriState.FALSE);
                }
                return args;

            } else if (replacer.instanceOf instanceof TypeCheckNode) {
                TypeCheckNode typeCheck = (TypeCheckNode) replacer.instanceOf;
                ValueNode object = typeCheck.getValue();
                Arguments args = new Arguments(instanceofExact, typeCheck.graph().getGuardsStage(), tool.getLoweringStage());
                args.add("object", object);
                args.add("exactHub", ConstantNode.forConstant(KlassPointerStamp.klassNonNull(), ((HotSpotResolvedObjectType) typeCheck.type()).klass(), providers.getMetaAccess(), typeCheck.graph()));
                args.add("trueValue", replacer.trueValue);
                args.add("falseValue", replacer.falseValue);
                return args;
            } else if (replacer.instanceOf instanceof InstanceOfDynamicNode) {
                InstanceOfDynamicNode instanceOf = (InstanceOfDynamicNode) replacer.instanceOf;
                ValueNode object = instanceOf.object();

                Arguments args = new Arguments(instanceofDynamic, instanceOf.graph().getGuardsStage(), tool.getLoweringStage());
                args.add("mirror", instanceOf.mirror());
                args.add("object", object);
                args.add("trueValue", replacer.trueValue);
                args.add("falseValue", replacer.falseValue);
                return args;
            } else if (replacer.instanceOf instanceof ClassIsAssignableFromNode) {
                ClassIsAssignableFromNode isAssignable = (ClassIsAssignableFromNode) replacer.instanceOf;
                Arguments args = new Arguments(isAssignableFrom, isAssignable.graph().getGuardsStage(), tool.getLoweringStage());
                args.add("thisClass", isAssignable.getThisClass());
                args.add("otherClass", isAssignable.getOtherClass());
                args.add("trueValue", replacer.trueValue);
                args.add("falseValue", replacer.falseValue);
                return args;
            } else {
                throw JVMCIError.shouldNotReachHere();
            }
        }
    }
}
