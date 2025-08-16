/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.LIKELY_PROBABILITY;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.NOT_FREQUENT_PROBABILITY;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.probability;

import java.util.Map;

import com.oracle.svm.core.graal.meta.KnownOffsets;
import com.oracle.svm.core.graal.word.DynamicHubAccess;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.meta.SharedType;
import com.oracle.svm.core.util.DuplicatedInNativeCode;

import jdk.graal.compiler.api.replacements.Snippet;
import jdk.graal.compiler.core.common.calc.UnsignedMath;
import jdk.graal.compiler.core.common.type.TypeReference;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.NamedLocationIdentity;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.SnippetAnchorNode;
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

    public void registerLowerings(Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings, Providers providers) {
        lowerings.put(InstanceOfNode.class, new InstanceOfLowering(options, providers));
        lowerings.put(InstanceOfDynamicNode.class, new InstanceOfDynamicLowering(options, providers));
        lowerings.put(ClassIsAssignableFromNode.class, new ClassIsAssignableFromLowering(options, providers));
    }

    protected final KnownOffsets knownOffsets;

    final SnippetTemplate.SnippetInfo instanceOf;
    final SnippetTemplate.SnippetInfo instanceOfDynamic;
    final SnippetTemplate.SnippetInfo typeEquality;
    final SnippetTemplate.SnippetInfo assignableTypeCheck;

    @SuppressWarnings("this-escape")
    protected TypeSnippets(OptionValues options, Providers providers) {
        super(options, providers);

        this.knownOffsets = KnownOffsets.singleton();
        this.instanceOf = snippet(providers, TypeSnippets.class, "instanceOfSnippet");
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
            assert type.getSingleImplementor() == null : "Canonicalization of InstanceOfNode produces exact type for single implementor";
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
