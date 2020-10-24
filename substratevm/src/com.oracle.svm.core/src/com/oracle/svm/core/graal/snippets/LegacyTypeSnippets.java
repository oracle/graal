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

import java.util.Map;

import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.core.common.calc.UnsignedMath;
import org.graalvm.compiler.core.common.type.TypeReference;
import org.graalvm.compiler.debug.DebugHandlersFactory;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.NamedLocationIdentity;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.SnippetAnchorNode;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.java.ClassIsAssignableFromNode;
import org.graalvm.compiler.nodes.java.InstanceOfDynamicNode;
import org.graalvm.compiler.nodes.java.InstanceOfNode;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.replacements.InstanceOfSnippetsTemplates;
import org.graalvm.compiler.replacements.SnippetTemplate;
import org.graalvm.compiler.replacements.Snippets;
import org.graalvm.compiler.word.ObjectAccess;

import com.oracle.svm.core.annotate.DuplicatedInNativeCode;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.meta.SharedType;

import jdk.vm.ci.code.TargetDescription;

public final class LegacyTypeSnippets extends SubstrateTemplates implements Snippets {

    @Snippet
    protected static SubstrateIntrinsics.Any typeEqualityTestSnippet(Object object, SubstrateIntrinsics.Any trueValue, SubstrateIntrinsics.Any falseValue,
                    @Snippet.ConstantParameter boolean allowsNull, int fromTypeID) {
        if (object == null) {
            return allowsNull ? trueValue : falseValue;
        }
        Object objectNonNull = PiNode.piCastNonNull(object, SnippetAnchorNode.anchor());
        int typeCheckId = loadHub(objectNonNull).getTypeID();

        if (typeCheckId == fromTypeID) {
            return trueValue;
        }
        return falseValue;
    }

    @Snippet
    protected static SubstrateIntrinsics.Any typeEqualityTestDynamicSnippet(Object object, SubstrateIntrinsics.Any trueValue, SubstrateIntrinsics.Any falseValue,
                    @Snippet.ConstantParameter boolean allowsNull, DynamicHub exactType) {
        if (object == null) {
            return allowsNull ? trueValue : falseValue;
        }
        Object objectNonNull = PiNode.piCastNonNull(object, SnippetAnchorNode.anchor());
        int typeCheckId = loadHub(objectNonNull).getTypeID();

        if (typeCheckId == exactType.getTypeID()) {
            return trueValue;
        }
        return falseValue;
    }

    @Snippet
    protected static SubstrateIntrinsics.Any instanceOfSnippet(Object object, SubstrateIntrinsics.Any trueValue, SubstrateIntrinsics.Any falseValue, @Snippet.ConstantParameter boolean allowsNull,
                    int fromTypeID, int numTypeIDs) {
        if (object == null) {
            return allowsNull ? trueValue : falseValue;
        }
        Object objectNonNull = PiNode.piCastNonNull(object, SnippetAnchorNode.anchor());
        if (numTypeIDs > 0) {
            int typeCheckId = loadHub(objectNonNull).getTypeID();

            if (numTypeIDs == 1) {
                if (typeCheckId == fromTypeID) {
                    return trueValue;
                }
            } else {
                if (UnsignedMath.belowThan(typeCheckId - fromTypeID, numTypeIDs)) {
                    return trueValue;
                }
            }
        }
        return falseValue;
    }

    @Snippet
    protected static SubstrateIntrinsics.Any instanceOfBitTestSnippet(Object object, SubstrateIntrinsics.Any trueValue, SubstrateIntrinsics.Any falseValue,
                    @Snippet.ConstantParameter boolean allowsNull, int bitsOffset, byte bitMask) {
        if (object == null) {
            return allowsNull ? trueValue : falseValue;
        }
        Object objectNonNull = PiNode.piCastNonNull(object, SnippetAnchorNode.anchor());

        /*
         * Check if the type bit is set in the instanceOfBits of the DynamicHub.
         */
        if ((ObjectAccess.readByte(loadHub(objectNonNull), bitsOffset) & bitMask) != 0) {
            return trueValue;
        }
        return falseValue;
    }

    @Snippet
    protected static SubstrateIntrinsics.Any instanceOfDynamicSnippet(DynamicHub type, Object object, SubstrateIntrinsics.Any trueValue, SubstrateIntrinsics.Any falseValue,
                    @Snippet.ConstantParameter boolean allowsNull) {
        if (object == null) {
            return allowsNull ? trueValue : falseValue;
        }
        Object objectNonNull = PiNode.piCastNonNull(object, SnippetAnchorNode.anchor());
        DynamicHub objectHub = loadHub(objectNonNull);

        return isAssignableFrom(type, objectHub, trueValue, falseValue);
    }

    @Snippet
    protected static SubstrateIntrinsics.Any classIsAssignableFromSnippet(@Snippet.NonNullParameter DynamicHub type, @Snippet.NonNullParameter DynamicHub checkedHub, SubstrateIntrinsics.Any trueValue,
                    SubstrateIntrinsics.Any falseValue) {
        return isAssignableFrom(type, checkedHub, trueValue, falseValue);
    }

    @DuplicatedInNativeCode
    private static SubstrateIntrinsics.Any isAssignableFrom(DynamicHub type, DynamicHub checkedHub, SubstrateIntrinsics.Any trueValue, SubstrateIntrinsics.Any falseValue) {
        int checkedTypeID = checkedHub.getTypeID();
        int[] matches = type.getAssignableFromMatches();

        int le = DynamicHub.fromClass(matches.getClass()).getLayoutEncoding();
        int matchesLength = matches.length;
        for (int i = 0; i < matchesLength; i += 2) {
            /*
             * We cannot use regular array accesses like match[i] because we need to provide a
             * custom LocationIdentity for the read.
             */
            int matchTypeID = ObjectAccess.readInt(matches, LayoutEncoding.getArrayElementOffset(le, i), NamedLocationIdentity.FINAL_LOCATION);
            int matchLength = ObjectAccess.readInt(matches, LayoutEncoding.getArrayElementOffset(le, i + 1), NamedLocationIdentity.FINAL_LOCATION);
            if (UnsignedMath.belowThan(checkedTypeID - matchTypeID, matchLength)) {
                return trueValue;
            }
        }
        return falseValue;
    }

    @SuppressWarnings("unused")
    public static void registerLowerings(RuntimeConfiguration runtimeConfig, OptionValues options, Iterable<DebugHandlersFactory> factories, Providers providers,
                    SnippetReflectionProvider snippetReflection,
                    Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings) {
        new LegacyTypeSnippets(options, runtimeConfig, factories, providers, snippetReflection, lowerings);
    }

    final RuntimeConfiguration runtimeConfig;

    private LegacyTypeSnippets(OptionValues options, RuntimeConfiguration runtimeConfig, Iterable<DebugHandlersFactory> factories, Providers providers, SnippetReflectionProvider snippetReflection,
                    Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings) {
        super(options, factories, providers, snippetReflection);
        this.runtimeConfig = runtimeConfig;

        lowerings.put(InstanceOfNode.class, new InstanceOfLowering(options, factories, providers, snippetReflection,
                        ConfigurationValues.getTarget()));
        lowerings.put(InstanceOfDynamicNode.class, new InstanceOfDynamicLowering(options, factories, providers, snippetReflection,
                        ConfigurationValues.getTarget()));
        lowerings.put(ClassIsAssignableFromNode.class, new ClassIsAssignableFromLowering(options, factories, providers, snippetReflection,
                        ConfigurationValues.getTarget()));
    }

    protected class InstanceOfLowering extends InstanceOfSnippetsTemplates implements NodeLoweringProvider<FloatingNode> {

        private final SnippetTemplate.SnippetInfo typeEqualityTest = snippet(LegacyTypeSnippets.class, "typeEqualityTestSnippet");
        private final SnippetTemplate.SnippetInfo instanceOf = snippet(LegacyTypeSnippets.class, "instanceOfSnippet");
        private final SnippetTemplate.SnippetInfo instanceOfBitTest = snippet(LegacyTypeSnippets.class, "instanceOfBitTestSnippet");

        public InstanceOfLowering(OptionValues options, Iterable<DebugHandlersFactory> factories, Providers providers, SnippetReflectionProvider snippetReflection, TargetDescription target) {
            super(options, factories, providers, snippetReflection, target);
        }

        @Override
        public void lower(FloatingNode node, LoweringTool tool) {
            if (tool.getLoweringStage() == LoweringTool.StandardLoweringStage.HIGH_TIER) {
                return;
            }
            super.lower(node, tool);
        }

        @Override
        protected SnippetTemplate.Arguments makeArguments(InstanceOfUsageReplacer replacer, LoweringTool tool) {
            InstanceOfNode node = (InstanceOfNode) replacer.instanceOf;
            TypeReference typeReference = node.type();
            SharedType type = (SharedType) typeReference.getType();
            int fromTypeID = type.getInstanceOfFromTypeID();
            int numTypeIDs = type.getInstanceOfNumTypeIDs();

            if (typeReference.isExact()) {
                /*
                 * We do a type check test.
                 */
                SnippetTemplate.Arguments args = new SnippetTemplate.Arguments(typeEqualityTest, node.graph().getGuardsStage(), tool.getLoweringStage());
                args.add("object", node.getValue());
                args.add("trueValue", replacer.trueValue);
                args.add("falseValue", replacer.falseValue);
                args.addConst("allowsNull", node.allowsNull());
                args.add("fromTypeID", type.getHub().getTypeID());
                return args;
            }

            if (fromTypeID == -1) {
                /*
                 * We do not have instanceOf type information, so fall back on assignableFrom
                 * information.
                 */
                SnippetTemplate.Arguments args = new SnippetTemplate.Arguments(instanceOfDynamic, node.graph().getGuardsStage(), tool.getLoweringStage());
                args.add("type", type.getHub());
                args.add("object", node.getValue());
                args.add("trueValue", replacer.trueValue);
                args.add("falseValue", replacer.falseValue);
                args.addConst("allowsNull", node.allowsNull());
                return args;
            } else if (numTypeIDs >= 0) {
                /*
                 * Make the instance-of check with a type-ID range check.
                 */
                SnippetTemplate.Arguments args = new SnippetTemplate.Arguments(instanceOf, node.graph().getGuardsStage(), tool.getLoweringStage());
                args.add("object", node.getValue());
                args.add("trueValue", replacer.trueValue);
                args.add("falseValue", replacer.falseValue);
                args.addConst("allowsNull", node.allowsNull());
                args.add("fromTypeID", fromTypeID);
                args.add("numTypeIDs", numTypeIDs);
                return args;
            } else {
                /*
                 * Make the instance-of check with bit test.
                 */
                assert numTypeIDs == -1 : "type not expected in type check: " + type + ", " + node;
                SnippetTemplate.Arguments args = new SnippetTemplate.Arguments(instanceOfBitTest, node.graph().getGuardsStage(), tool.getLoweringStage());
                args.add("object", node.getValue());
                args.add("trueValue", replacer.trueValue);
                args.add("falseValue", replacer.falseValue);
                args.addConst("allowsNull", node.allowsNull());
                args.add("bitsOffset", runtimeConfig.getInstanceOfBitOffset(fromTypeID));
                args.add("bitMask", 1 << (fromTypeID % 8));
                return args;
            }
        }
    }

    protected final SnippetTemplate.SnippetInfo instanceOfDynamic = snippet(LegacyTypeSnippets.class, "instanceOfDynamicSnippet");

    protected class InstanceOfDynamicLowering extends InstanceOfSnippetsTemplates implements NodeLoweringProvider<FloatingNode> {

        private final SnippetTemplate.SnippetInfo typeEqualityTestDynamic = snippet(LegacyTypeSnippets.class, "typeEqualityTestDynamicSnippet");

        public InstanceOfDynamicLowering(OptionValues options, Iterable<DebugHandlersFactory> factories, Providers providers, SnippetReflectionProvider snippetReflection,
                        TargetDescription target) {
            super(options, factories, providers, snippetReflection, target);
        }

        @Override
        public void lower(FloatingNode node, LoweringTool tool) {
            if (tool.getLoweringStage() == LoweringTool.StandardLoweringStage.HIGH_TIER) {
                return;
            }
            super.lower(node, tool);
        }

        @Override
        protected SnippetTemplate.Arguments makeArguments(InstanceOfUsageReplacer replacer, LoweringTool tool) {
            InstanceOfDynamicNode node = (InstanceOfDynamicNode) replacer.instanceOf;

            if (node.isExact()) {
                /*
                 * We do a type check test.
                 */
                SnippetTemplate.Arguments args = new SnippetTemplate.Arguments(typeEqualityTestDynamic, node.graph().getGuardsStage(), tool.getLoweringStage());
                args.add("object", node.getObject());
                args.add("trueValue", replacer.trueValue);
                args.add("falseValue", replacer.falseValue);
                args.addConst("allowsNull", node.allowsNull());
                args.add("exactType", node.getMirrorOrHub());
                return args;
            }

            SnippetTemplate.Arguments args = new SnippetTemplate.Arguments(instanceOfDynamic, node.graph().getGuardsStage(), tool.getLoweringStage());
            args.add("type", node.getMirrorOrHub());
            args.add("object", node.getObject());
            args.add("trueValue", replacer.trueValue);
            args.add("falseValue", replacer.falseValue);
            args.addConst("allowsNull", node.allowsNull());
            return args;
        }
    }

    protected class ClassIsAssignableFromLowering extends InstanceOfSnippetsTemplates implements NodeLoweringProvider<FloatingNode> {

        private final SnippetTemplate.SnippetInfo classIsAssignableFrom = snippet(LegacyTypeSnippets.class, "classIsAssignableFromSnippet");

        public ClassIsAssignableFromLowering(OptionValues options, Iterable<DebugHandlersFactory> factories, Providers providers, SnippetReflectionProvider snippetReflection,
                        TargetDescription target) {
            super(options, factories, providers, snippetReflection, target);
        }

        @Override
        public void lower(FloatingNode node, LoweringTool tool) {
            if (tool.getLoweringStage() == LoweringTool.StandardLoweringStage.HIGH_TIER) {
                return;
            }
            super.lower(node, tool);
        }

        @Override
        protected SnippetTemplate.Arguments makeArguments(InstanceOfUsageReplacer replacer, LoweringTool tool) {
            ClassIsAssignableFromNode node = (ClassIsAssignableFromNode) replacer.instanceOf;

            SnippetTemplate.Arguments args = new SnippetTemplate.Arguments(classIsAssignableFrom, node.graph().getGuardsStage(), tool.getLoweringStage());
            args.add("type", node.getThisClass());
            args.add("checkedHub", node.getOtherClass());
            args.add("trueValue", replacer.trueValue);
            args.add("falseValue", replacer.falseValue);
            return args;
        }
    }
}
