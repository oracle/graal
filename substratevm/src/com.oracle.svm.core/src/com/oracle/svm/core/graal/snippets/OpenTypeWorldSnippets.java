/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.svm.core.hub.DynamicHubTypeCheckUtil.HASHING_INTERFACE_MASK;
import static com.oracle.svm.core.hub.DynamicHubTypeCheckUtil.HASHING_SHIFT_OFFSET;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.NOT_FREQUENT_PROBABILITY;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.probability;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.unknownProbability;

import java.util.Map;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.config.ObjectLayout;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.DynamicHubTypeCheckUtil;
import com.oracle.svm.core.meta.SharedType;
import com.oracle.svm.core.util.DuplicatedInNativeCode;

import jdk.graal.compiler.api.replacements.Snippet;
import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.core.common.type.TypeReference;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.NamedLocationIdentity;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.SnippetAnchorNode;
import jdk.graal.compiler.nodes.calc.FloatingNode;
import jdk.graal.compiler.nodes.extended.BranchProbabilityNode;
import jdk.graal.compiler.nodes.extended.GuardingNode;
import jdk.graal.compiler.nodes.java.ClassIsAssignableFromNode;
import jdk.graal.compiler.nodes.java.InstanceOfDynamicNode;
import jdk.graal.compiler.nodes.java.InstanceOfNode;
import jdk.graal.compiler.nodes.spi.LoweringTool;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.replacements.InstanceOfSnippetsTemplates;
import jdk.graal.compiler.replacements.ReplacementsUtil;
import jdk.graal.compiler.replacements.SnippetTemplate;
import jdk.graal.compiler.replacements.Snippets;
import jdk.graal.compiler.word.ObjectAccess;
import jdk.vm.ci.meta.JavaKind;

/**
 * GR-51603 Once this snippet logic reaches a steady-state merge with {@link TypeSnippets}.
 */
public class OpenTypeWorldSnippets extends SubstrateTemplates implements Snippets {

    @Snippet
    protected static SubstrateIntrinsics.Any typeEqualitySnippet(
                    Object object,
                    SubstrateIntrinsics.Any trueValue,
                    SubstrateIntrinsics.Any falseValue,
                    @Snippet.ConstantParameter boolean allowsNull,
                    @Snippet.NonNullParameter DynamicHub exactType) {
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
                    @Snippet.ConstantParameter int typeID,
                    @Snippet.ConstantParameter int typeIDDepth,
                    @Snippet.ConstantParameter boolean useInterfaceHashing,
                    @Snippet.ConstantParameter int interfaceID) {
        if (probability(NOT_FREQUENT_PROBABILITY, object == null)) {
            if (allowsNull) {
                return trueValue;
            }
            return falseValue;
        }
        GuardingNode guard = SnippetAnchorNode.anchor();
        Object nonNullObject = PiNode.piCastNonNull(object, guard);
        DynamicHub nonNullHub = loadHub(nonNullObject);
        if (typeIDDepth >= 0) {
            return classTypeCheck(typeID, typeIDDepth, nonNullHub, trueValue, falseValue);
        } else {
            return interfaceTypeCheckHelper(interfaceID, nonNullHub, trueValue, falseValue, useInterfaceHashing);
        }
    }

    @Snippet
    protected static SubstrateIntrinsics.Any instanceOfDynamicSnippet(
                    @Snippet.NonNullParameter DynamicHub type,
                    Object object,
                    SubstrateIntrinsics.Any trueValue,
                    SubstrateIntrinsics.Any falseValue,
                    @Snippet.ConstantParameter boolean allowsNull,
                    @Snippet.ConstantParameter boolean useInterfaceHashing) {
        if (probability(NOT_FREQUENT_PROBABILITY, object == null)) {
            if (allowsNull) {
                return trueValue;
            }
            return falseValue;
        }
        GuardingNode guard = SnippetAnchorNode.anchor();
        Object nonNullObject = PiNode.piCastNonNull(object, guard);
        DynamicHub nonNullHub = loadHub(nonNullObject);
        int typeIDDepth = type.getTypeIDDepth();
        if (unknownProbability(typeIDDepth >= 0)) {
            int typeIDToMatch = type.getTypeID();
            return classTypeCheck(typeIDToMatch, typeIDDepth, nonNullHub, trueValue, falseValue);
        } else {
            int interfaceIDToMatch = type.getInterfaceID();
            return interfaceTypeCheckHelper(interfaceIDToMatch, nonNullHub, trueValue, falseValue, useInterfaceHashing);
        }
    }

    @Snippet
    protected static SubstrateIntrinsics.Any classIsAssignableFromSnippet(
                    @Snippet.NonNullParameter DynamicHub type,
                    @Snippet.NonNullParameter DynamicHub checkedHub,
                    SubstrateIntrinsics.Any trueValue,
                    SubstrateIntrinsics.Any falseValue,
                    @Snippet.ConstantParameter boolean useInterfaceHashing) {
        int typeIDDepth = type.getTypeIDDepth();
        if (unknownProbability(typeIDDepth >= 0)) {
            int typeID = type.getTypeID();
            return classTypeCheck(typeID, typeIDDepth, checkedHub, trueValue, falseValue);
        } else {
            int interfaceID = type.getInterfaceID();
            return interfaceTypeCheckHelper(interfaceID, checkedHub, trueValue, falseValue, useInterfaceHashing);
        }
    }

    @DuplicatedInNativeCode
    protected static SubstrateIntrinsics.Any classTypeCheck(
                    int typeID,
                    int typeIDDepth,
                    DynamicHub checkedHub,
                    SubstrateIntrinsics.Any trueValue,
                    SubstrateIntrinsics.Any falseValue) {
        int numClassTypes = checkedHub.getNumClassTypes();
        if (typeIDDepth >= numClassTypes) {
            return falseValue;
        }
        int[] checkedTypeIds = checkedHub.getOpenTypeWorldTypeCheckSlots();
        // int checkedClassId = checkedTypeIds[typeIDDepth];
        int offset = (int) ImageSingletons.lookup(ObjectLayout.class).getArrayElementOffset(JavaKind.Int, typeIDDepth);
        // GR-51603 can make a floating read
        int checkedClassId = ObjectAccess.readInt(checkedTypeIds, offset, NamedLocationIdentity.FINAL_LOCATION);
        if (checkedClassId == typeID) {
            return trueValue;
        }

        return falseValue;
    }

    /**
     * Dispatches to either the iterative interface type check or the hashing based interface type
     * check. This depends on whether {@link SubstrateOptions#useInterfaceHashing()} is enabled and
     * whether the checked interfaceID is <= {@link SubstrateOptions#InterfaceHashingMaxId}.
     */
    @DuplicatedInNativeCode
    protected static SubstrateIntrinsics.Any interfaceTypeCheckHelper(
                    int interfaceID,
                    DynamicHub checkedHub,
                    SubstrateIntrinsics.Any trueValue,
                    SubstrateIntrinsics.Any falseValue,
                    boolean useInterfaceHashing) {
        if (useInterfaceHashing && probability(BranchProbabilityNode.FAST_PATH_PROBABILITY, interfaceID <= SubstrateOptions.interfaceHashingMaxId())) {
            return hashedInterfaceTypeCheck(interfaceID, checkedHub, trueValue, falseValue);
        }
        return iterativeInterfaceTypeCheck(interfaceID, checkedHub, trueValue, falseValue);
    }

    @DuplicatedInNativeCode
    protected static SubstrateIntrinsics.Any iterativeInterfaceTypeCheck(
                    int interfaceID,
                    DynamicHub checkedHub,
                    SubstrateIntrinsics.Any trueValue,
                    SubstrateIntrinsics.Any falseValue) {
        int numClassTypes = checkedHub.getNumClassTypes();
        int numInterfaceTypes = checkedHub.getNumIterableInterfaceTypes();
        int[] checkedTypeIds = checkedHub.getOpenTypeWorldTypeCheckSlots();
        for (int i = 0; i < numInterfaceTypes * 2; i += 2) {
            // int checkedInterfaceId = checkedTypeIds[numClassTypes + i];
            int offset = (int) ImageSingletons.lookup(ObjectLayout.class).getArrayElementOffset(JavaKind.Int, numClassTypes + i);
            // GR-51603 can make a floating read
            int checkedInterfaceId = ObjectAccess.readInt(checkedTypeIds, offset, NamedLocationIdentity.FINAL_LOCATION);
            if (checkedInterfaceId == interfaceID) {
                return trueValue;
            }
        }
        return falseValue;
    }

    /**
     * If {@link SubstrateOptions#useInterfaceHashing()} is enabled, interfaceIDs and itable
     * starting offsets are stored in a hash table (see TypeCheckBuilder for a general
     * documentation). This snippet does a hash table lookup and returns true if the provided
     * interfaceID matches the interfaceID in the hash table, false otherwise. See
     * {@link DynamicHubTypeCheckUtil#hashParam(int[])} for details on the hashing function and
     * hashing parameter.
     */
    @DuplicatedInNativeCode
    protected static SubstrateIntrinsics.Any hashedInterfaceTypeCheck(
                    int interfaceID,
                    DynamicHub checkedHub,
                    SubstrateIntrinsics.Any trueValue,
                    SubstrateIntrinsics.Any falseValue) {
        ReplacementsUtil.dynamicAssert(NumUtil.isUShort(interfaceID), "InterfaceIDs must fit in a short to be used for hashing.");

        // The upper byte of the hashParam holds the shift value, the lower three bytes hold p
        // which is used for bitwise "and": hashParam = shift << HASHING_SHIFT_OFFSET | p.
        int hashParam = checkedHub.getOpenTypeWorldInterfaceHashParam();
        int shift = hashParam >>> HASHING_SHIFT_OFFSET;
        int[] hashTable = checkedHub.getOpenTypeWorldInterfaceHashTable();

        // No need to mask hashParam to get "p". interfaceID fits in a short -> the two upper
        // bytes are 0.
        int hash = (interfaceID >>> shift) & hashParam;
        int offset = (int) ImageSingletons.lookup(ObjectLayout.class).getArrayElementOffset(JavaKind.Int, hash);
        int hashTableEntry = ObjectAccess.readInt(hashTable, offset, NamedLocationIdentity.FINAL_LOCATION);

        // Hashtable entries contain integers which hold the iTableOffset and the interfaceID:
        // hashTableEntry = iTableOffset << HASHING_ITABLE_SHIFT | interfaceID
        if ((hashTableEntry & HASHING_INTERFACE_MASK) == interfaceID) {
            return trueValue;
        } else {
            return falseValue;
        }
    }

    public void registerLowerings(Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings, Providers providers) {
        lowerings.put(InstanceOfNode.class, new InstanceOfLowering(options, providers));
        lowerings.put(InstanceOfDynamicNode.class, new InstanceOfDynamicLowering(options, providers));
        lowerings.put(ClassIsAssignableFromNode.class, new ClassIsAssignableFromLowering(options, providers));
    }

    final SnippetTemplate.SnippetInfo instanceOf;
    final SnippetTemplate.SnippetInfo instanceOfDynamic;
    final SnippetTemplate.SnippetInfo typeEquality;
    final SnippetTemplate.SnippetInfo assignableTypeCheck;

    @SuppressWarnings("this-escape")
    protected OpenTypeWorldSnippets(OptionValues options, Providers providers) {
        super(options, providers);

        this.instanceOf = snippet(providers, OpenTypeWorldSnippets.class, "instanceOfSnippet");
        this.instanceOfDynamic = snippet(providers, OpenTypeWorldSnippets.class, "instanceOfDynamicSnippet");
        this.typeEquality = snippet(providers, OpenTypeWorldSnippets.class, "typeEqualitySnippet");
        this.assignableTypeCheck = snippet(providers, OpenTypeWorldSnippets.class, "classIsAssignableFromSnippet");
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
            args.add("typeID", hub.getTypeID());
            args.add("typeIDDepth", hub.getTypeIDDepth());
            args.add("useInterfaceHashing", SubstrateOptions.useInterfaceHashing());
            args.add("interfaceID", hub.getInterfaceID());
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
                args.add("useInterfaceHashing", SubstrateOptions.useInterfaceHashing());
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
            args.add("useInterfaceHashing", SubstrateOptions.useInterfaceHashing());
            return args;
        }
    }
}
