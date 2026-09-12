/*
 * Copyright (c) 2020, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.snippets;

import static com.oracle.svm.core.graal.snippets.SubstrateIntrinsics.loadHub;
import static com.oracle.svm.core.graal.snippets.SubstrateIntrinsics.loadHubOrNull;
import static jdk.graal.compiler.core.common.GraalOptions.TypeCheckMaxHints;
import static jdk.graal.compiler.core.common.GraalOptions.TypeCheckMinProfileHitProbability;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.LIKELY_PROBABILITY;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.NOT_FREQUENT_PROBABILITY;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.probability;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.unknownProbability;

import java.util.Arrays;
import java.util.Map;

import com.oracle.svm.core.graal.meta.KnownOffsets;
import com.oracle.svm.core.graal.word.DynamicHubAccess;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.meta.SharedType;
import com.oracle.svm.shared.util.DuplicatedInNativeCode;

import jdk.graal.compiler.api.replacements.Snippet;
import jdk.graal.compiler.core.common.calc.UnsignedMath;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.core.common.type.TypeReference;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.NamedLocationIdentity;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.SnippetAnchorNode;
import jdk.graal.compiler.nodes.TypeCheckHints;
import jdk.graal.compiler.nodes.calc.FloatingNode;
import jdk.graal.compiler.nodes.extended.GuardingNode;
import jdk.graal.compiler.nodes.java.ClassIsAssignableFromNode;
import jdk.graal.compiler.nodes.java.InstanceOfDynamicNode;
import jdk.graal.compiler.nodes.java.InstanceOfNode;
import jdk.graal.compiler.nodes.spi.LoweringTool;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.replacements.InstanceOfSnippetsTemplates;
import jdk.graal.compiler.replacements.SnippetTemplate;
import jdk.graal.compiler.replacements.Snippets;
import jdk.graal.compiler.replacements.nodes.ExplodeLoopNode;
import jdk.vm.ci.meta.Assumptions;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaTypeProfile;

public class TypeSnippets extends SubstrateTemplates implements Snippets {

    @Snippet
    protected static SubstrateIntrinsics.Any typeEqualitySnippet(
                    Object object,
                    SubstrateIntrinsics.Any trueValue,
                    SubstrateIntrinsics.Any falseValue,
                    @Snippet.ConstantParameter boolean allowsNull,
                    DynamicHub exactType) {
        if (allowsNull) {
            if (probability(NOT_FREQUENT_PROBABILITY, object == null)) {
                return trueValue;
            }
            GuardingNode guard = SnippetAnchorNode.anchor();
            Object nonNullObject = PiNode.piCastNonNull(object, guard);
            DynamicHub nonNullHub = loadHub(nonNullObject);
            if (probability(NOT_FREQUENT_PROBABILITY, nonNullHub != exactType)) {
                return falseValue;
            }
            return trueValue;
        } else {
            Object hubOrNull = loadHubOrNull(object);
            if (probability(NOT_FREQUENT_PROBABILITY, hubOrNull != exactType)) {
                return falseValue;
            }
            return trueValue;
        }
    }

    @Snippet
    protected static SubstrateIntrinsics.Any instanceOfSnippet(
                    Object object,
                    SubstrateIntrinsics.Any trueValue,
                    SubstrateIntrinsics.Any falseValue,
                    @Snippet.ConstantParameter boolean allowsNull,
                    short start, short range, short slot,
                    @Snippet.ConstantParameter int typeIDSlotOffset) {
        if (probability(NOT_FREQUENT_PROBABILITY, object == null)) {
            if (allowsNull) {
                return trueValue;
            }
            return falseValue;
        }
        GuardingNode guard = SnippetAnchorNode.anchor();
        Object nonNullObject = PiNode.piCastNonNull(object, guard);
        DynamicHub nonNullHub = loadHub(nonNullObject);
        return slotTypeCheck(start, range, slot, typeIDSlotOffset, nonNullHub, trueValue, falseValue);
    }

    /**
     * Performs an inexact type check using exact-hub profile hints before the generic closed-world
     * check.
     */
    @Snippet
    protected static SubstrateIntrinsics.Any instanceOfWithProfileSnippet(
                    @Snippet.VarargsParameter DynamicHub[] hints,
                    @Snippet.VarargsParameter boolean[] hintIsPositive,
                    Object object,
                    SubstrateIntrinsics.Any trueValue,
                    SubstrateIntrinsics.Any falseValue,
                    @Snippet.ConstantParameter boolean allowsNull,
                    short start, short range, short slot,
                    @Snippet.ConstantParameter int typeIDSlotOffset) {
        if (probability(NOT_FREQUENT_PROBABILITY, object == null)) {
            if (allowsNull) {
                return trueValue;
            }
            return falseValue;
        }
        GuardingNode guard = SnippetAnchorNode.anchor();
        Object nonNullObject = PiNode.piCastNonNull(object, guard);
        DynamicHub nonNullHub = loadHub(nonNullObject);
        ExplodeLoopNode.explodeLoop();
        for (int i = 0; i < hints.length; i++) {
            boolean positive = hintIsPositive[i];
            DynamicHub hint = hints[i];
            if (probability(LIKELY_PROBABILITY, nonNullHub == hint)) {
                return unknownProbability(positive) ? trueValue : falseValue;
            }
        }
        return slotTypeCheck(start, range, slot, typeIDSlotOffset, nonNullHub, trueValue, falseValue);
    }

    @Snippet
    protected static SubstrateIntrinsics.Any instanceOfDynamicSnippet(
                    @Snippet.NonNullParameter DynamicHub type,
                    Object object,
                    SubstrateIntrinsics.Any trueValue,
                    SubstrateIntrinsics.Any falseValue,
                    @Snippet.ConstantParameter boolean allowsNull,
                    @Snippet.ConstantParameter int typeIDSlotOffset) {
        if (probability(NOT_FREQUENT_PROBABILITY, object == null)) {
            if (allowsNull) {
                return trueValue;
            }
            return falseValue;
        }
        GuardingNode guard = SnippetAnchorNode.anchor();
        Object nonNullObject = PiNode.piCastNonNull(object, guard);
        DynamicHub nonNullHub = loadHub(nonNullObject);
        return slotTypeCheck(type.getTypeCheckStart(), type.getTypeCheckRange(), type.getTypeCheckSlot(), typeIDSlotOffset, nonNullHub, trueValue, falseValue);
    }

    @Snippet
    protected static SubstrateIntrinsics.Any classIsAssignableFromSnippet(
                    @Snippet.NonNullParameter DynamicHub type,
                    @Snippet.NonNullParameter DynamicHub checkedHub,
                    SubstrateIntrinsics.Any trueValue,
                    SubstrateIntrinsics.Any falseValue,
                    @Snippet.ConstantParameter int typeIDSlotOffset) {
        return slotTypeCheck(type.getTypeCheckStart(), type.getTypeCheckRange(), type.getTypeCheckSlot(), typeIDSlotOffset, checkedHub, trueValue, falseValue);
    }

    @DuplicatedInNativeCode
    protected static SubstrateIntrinsics.Any slotTypeCheck(
                    short start, short range, short slot,
                    int typeIDSlotOffset,
                    DynamicHub checkedHub,
                    SubstrateIntrinsics.Any trueValue,
                    SubstrateIntrinsics.Any falseValue) {
        int typeCheckStart = Short.toUnsignedInt(start);
        int typeCheckRange = Short.toUnsignedInt(range);
        int typeCheckSlot = Short.toUnsignedInt(slot) * 2;
        // No need to guard reading from hub as `checkedHub` is guaranteed to be non-null.
        final GuardingNode guard = null;
        int checkedTypeID = Short.toUnsignedInt(DynamicHubAccess.readShort(checkedHub, typeIDSlotOffset + typeCheckSlot, NamedLocationIdentity.FINAL_LOCATION, guard));
        if (probability(LIKELY_PROBABILITY, UnsignedMath.belowThan(checkedTypeID - typeCheckStart, typeCheckRange))) {
            return trueValue;
        }

        return falseValue;
    }

    /**
     * Substrate counterpart of {@code TypeCheckSnippetUtils#createHints}. It is kept separate because
     * this method materializes runtime {@link DynamicHub} values, whereas the HotSpot helper
     * materializes {@code ConstantNode}s with {@code KlassPointerStamp}.
     */
    static Hints createHints(TypeCheckHints hints, boolean positiveOnly) {
        DynamicHub[] hubs = new DynamicHub[hints.hints.length];
        boolean[] isPositive = new boolean[hints.hints.length];
        int index = 0;
        for (int i = 0; i < hubs.length; i++) {
            if (!positiveOnly || hints.hints[i].positive) {
                hubs[index] = ((SharedType) hints.hints[i].type).getHub();
                isPositive[index] = hints.hints[i].positive;
                index++;
            }
        }
        if (positiveOnly && index != hubs.length) {
            assert index < hubs.length : Assertions.errorMessage(index, hubs);
            hubs = Arrays.copyOf(hubs, index);
            isPositive = Arrays.copyOf(isPositive, index);
        }
        return new Hints(hubs, isPositive);
    }

    /** Exact-hub profile hints and their expected type-check results. */
    record Hints(DynamicHub[] hubs, boolean[] isPositive) {
    }

    /** Returns the minimum useful profile hit probability for closed-world type checks. */
    public static double getTypeCheckMinProfileHitProbability(OptionValues options) {
        if (!TypeCheckMinProfileHitProbability.hasBeenSet(options)) {
            /*
             * Generic type checks under the closed world assumption are very efficient. Only
             * introduce fast paths if it is likely that one of them is reached.
             */
            return 0.7;
        }
        /* Same default as on HotSpot. */
        return TypeCheckMinProfileHitProbability.getValue(options);
    }

    /** Returns the maximum number of exact-hub hints for closed-world type checks. */
    public static int getTypeCheckMaxHints(OptionValues options) {
        if (!TypeCheckMaxHints.hasBeenSet(options)) {
            /*
             * Benchmarks have shown that the generic type check is faster than two consecutive hub
             * checks. Insert only one fast path check per default.
             */
            return 1;
        }
        /* Same default as on HotSpot. */
        return TypeCheckMaxHints.getValue(options);
    }

    public void registerLowerings(Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings, Providers providers) {
        lowerings.put(InstanceOfNode.class, new InstanceOfLowering(options, providers));
        lowerings.put(InstanceOfDynamicNode.class, new InstanceOfDynamicLowering(options, providers));
        lowerings.put(ClassIsAssignableFromNode.class, new ClassIsAssignableFromLowering(options, providers));
    }

    protected final KnownOffsets knownOffsets;

    final SnippetTemplate.SnippetInfo instanceOf;
    final SnippetTemplate.SnippetInfo instanceOfWithProfile;
    final SnippetTemplate.SnippetInfo instanceOfDynamic;
    final SnippetTemplate.SnippetInfo typeEquality;
    final SnippetTemplate.SnippetInfo assignableTypeCheck;

    @SuppressWarnings("this-escape")
    protected TypeSnippets(OptionValues options, Providers providers) {
        super(options, providers);

        this.knownOffsets = KnownOffsets.singleton();
        this.instanceOf = snippet(providers, TypeSnippets.class, "instanceOfSnippet");
        this.instanceOfWithProfile = snippet(providers, TypeSnippets.class, "instanceOfWithProfileSnippet");
        this.instanceOfDynamic = snippet(providers, TypeSnippets.class, "instanceOfDynamicSnippet");
        this.typeEquality = snippet(providers, TypeSnippets.class, "typeEqualitySnippet");
        this.assignableTypeCheck = snippet(providers, TypeSnippets.class, "classIsAssignableFromSnippet");
    }

    protected class InstanceOfLowering extends InstanceOfSnippetsTemplates implements NodeLoweringProvider<FloatingNode> {

        public InstanceOfLowering(OptionValues options, Providers providers) {
            super(options, providers);
        }

        @Override
        public void lower(FloatingNode node, LoweringTool tool) {
            if (tool.getLoweringStage() != LoweringTool.StandardLoweringStage.MID_TIER) {
                return;
            }
            super.lower(node, tool);
        }

        @Override
        protected SnippetTemplate.Arguments makeArguments(InstanceOfUsageReplacer replacer, LoweringTool tool) {
            InstanceOfNode node = (InstanceOfNode) replacer.instanceOf;
            TypeReference typeReference = node.type();
            SharedType type = (SharedType) typeReference.getType();
            DynamicHub hub = type.getHub();

            SnippetTemplate.Arguments args;
            if (typeReference.isExact()) {
                args = new SnippetTemplate.Arguments(typeEquality, node.graph(), tool.getLoweringStage());
                args.add("object", node.getValue());
                args.add("trueValue", replacer.trueValue);
                args.add("falseValue", replacer.falseValue);
                args.add("allowsNull", node.allowsNull());
                args.add("exactType", hub);
            } else {
                args = makeArgumentsForInexactType(replacer, tool, node, type, hub);
            }
            return args;
        }

        protected SnippetTemplate.Arguments makeArgumentsForInexactType(InstanceOfUsageReplacer replacer, LoweringTool tool, InstanceOfNode node, SharedType type, DynamicHub hub) {
            assert !type.isInterface() || type.getSingleImplementor() == null : "Canonicalization of InstanceOfNode produces exact type for single implementor";
            if (node.profile() != null) {
                JavaTypeProfile profile = node.profile();
                OptionValues optionValues = node.getOptions();
                Assumptions assumptions = node.graph().getAssumptions();
                TypeCheckHints hintInfo = new TypeCheckHints(node.type(), profile, assumptions, getTypeCheckMinProfileHitProbability(optionValues), getTypeCheckMaxHints(optionValues));
                Hints hints = createHints(hintInfo, false);
                SnippetTemplate.Arguments args = new SnippetTemplate.Arguments(instanceOfWithProfile, node.graph(), tool.getLoweringStage());
                args.addVarargs("hints", DynamicHub.class, StampFactory.forKind(JavaKind.Object), hints.hubs());
                args.addVarargs("hintIsPositive", boolean.class, StampFactory.forKind(JavaKind.Boolean), hints.isPositive());
                args.add("object", node.getValue());
                args.add("trueValue", replacer.trueValue);
                args.add("falseValue", replacer.falseValue);
                args.add("allowsNull", node.allowsNull());
                args.add("start", hub.getTypeCheckStart());
                args.add("range", hub.getTypeCheckRange());
                args.add("slot", hub.getTypeCheckSlot());
                args.add("typeIDSlotOffset", knownOffsets.getTypeIDSlotsOffset());
                return args;
            }
            SnippetTemplate.Arguments args = new SnippetTemplate.Arguments(instanceOf, node.graph(), tool.getLoweringStage());
            args.add("object", node.getValue());
            args.add("trueValue", replacer.trueValue);
            args.add("falseValue", replacer.falseValue);
            args.add("allowsNull", node.allowsNull());
            args.add("start", hub.getTypeCheckStart());
            args.add("range", hub.getTypeCheckRange());
            args.add("slot", hub.getTypeCheckSlot());
            args.add("typeIDSlotOffset", knownOffsets.getTypeIDSlotsOffset());
            return args;
        }
    }

    protected class InstanceOfDynamicLowering extends InstanceOfSnippetsTemplates implements NodeLoweringProvider<FloatingNode> {

        public InstanceOfDynamicLowering(OptionValues options, Providers providers) {
            super(options, providers);
        }

        @Override
        public void lower(FloatingNode node, LoweringTool tool) {
            if (tool.getLoweringStage() != LoweringTool.StandardLoweringStage.MID_TIER) {
                return;
            }
            super.lower(node, tool);
        }

        @Override
        protected SnippetTemplate.Arguments makeArguments(InstanceOfUsageReplacer replacer, LoweringTool tool) {
            InstanceOfDynamicNode node = (InstanceOfDynamicNode) replacer.instanceOf;

            if (node.isExact()) {
                SnippetTemplate.Arguments args = new SnippetTemplate.Arguments(typeEquality, node.graph(), tool.getLoweringStage());
                args.add("object", node.getObject());
                args.add("trueValue", replacer.trueValue);
                args.add("falseValue", replacer.falseValue);
                args.add("allowsNull", node.allowsNull());
                args.add("exactType", node.getMirrorOrHub());
                return args;

            } else {
                SnippetTemplate.Arguments args = new SnippetTemplate.Arguments(instanceOfDynamic, node.graph(), tool.getLoweringStage());
                args.add("type", node.getMirrorOrHub());
                args.add("object", node.getObject());
                args.add("trueValue", replacer.trueValue);
                args.add("falseValue", replacer.falseValue);
                args.add("allowsNull", node.allowsNull());
                args.add("typeIDSlotOffset", knownOffsets.getTypeIDSlotsOffset());
                return args;
            }
        }
    }

    protected class ClassIsAssignableFromLowering extends InstanceOfSnippetsTemplates implements NodeLoweringProvider<FloatingNode> {

        public ClassIsAssignableFromLowering(OptionValues options, Providers providers) {
            super(options, providers);
        }

        @Override
        public void lower(FloatingNode node, LoweringTool tool) {
            if (tool.getLoweringStage() != LoweringTool.StandardLoweringStage.MID_TIER) {
                return;
            }
            super.lower(node, tool);
        }

        @Override
        protected SnippetTemplate.Arguments makeArguments(InstanceOfUsageReplacer replacer, LoweringTool tool) {
            ClassIsAssignableFromNode node = (ClassIsAssignableFromNode) replacer.instanceOf;

            SnippetTemplate.Arguments args = new SnippetTemplate.Arguments(assignableTypeCheck, node.graph(), tool.getLoweringStage());
            args.add("type", node.getThisClass());
            args.add("checkedHub", node.getOtherClass());
            args.add("trueValue", replacer.trueValue);
            args.add("falseValue", replacer.falseValue);
            args.add("typeIDSlotOffset", knownOffsets.getTypeIDSlotsOffset());
            return args;
        }
    }
}
