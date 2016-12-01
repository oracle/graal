/*
 * Copyright (c) 2012, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.replacements.profiling;

import static com.oracle.graal.hotspot.GraalHotSpotVMConfig.INJECTED_VMCONFIG;
import static com.oracle.graal.hotspot.replacements.HotSpotReplacementsUtil.config;
import static com.oracle.graal.nodes.extended.BranchProbabilityNode.SLOW_PATH_PROBABILITY;
import static com.oracle.graal.nodes.extended.BranchProbabilityNode.probability;
import static com.oracle.graal.replacements.SnippetTemplate.DEFAULT_REPLACER;

import com.oracle.graal.api.replacements.Snippet;
import com.oracle.graal.api.replacements.Snippet.ConstantParameter;
import com.oracle.graal.compiler.common.spi.ForeignCallDescriptor;
import com.oracle.graal.debug.GraalError;
import com.oracle.graal.graph.Node.ConstantNodeParameter;
import com.oracle.graal.graph.Node.NodeIntrinsic;
import com.oracle.graal.hotspot.HotSpotBackend;
import com.oracle.graal.hotspot.meta.HotSpotProviders;
import com.oracle.graal.hotspot.nodes.aot.LoadMethodCountersNode;
import com.oracle.graal.hotspot.nodes.profiling.ProfileBranchNode;
import com.oracle.graal.hotspot.nodes.profiling.ProfileInvokeNode;
import com.oracle.graal.hotspot.nodes.profiling.ProfileNode;
import com.oracle.graal.hotspot.word.MethodCountersPointer;
import com.oracle.graal.nodes.ConstantNode;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.extended.ForeignCallNode;
import com.oracle.graal.nodes.spi.LoweringTool;
import com.oracle.graal.nodes.util.GraphUtil;
import com.oracle.graal.replacements.SnippetTemplate;
import com.oracle.graal.replacements.SnippetTemplate.AbstractTemplates;
import com.oracle.graal.replacements.SnippetTemplate.Arguments;
import com.oracle.graal.replacements.SnippetTemplate.SnippetInfo;
import com.oracle.graal.replacements.Snippets;

import jdk.vm.ci.code.TargetDescription;

public class ProbabilisticProfileSnippets implements Snippets {
    @Snippet
    public static boolean shouldProfile(@ConstantParameter int probLog, int random) {
        int probabilityMask = (1 << probLog) - 1;
        return (random & probabilityMask) == 0;
    }

    @Snippet
    public static int notificationMask(int freqLog, int probLog) {
        int probabilityMask = (1 << probLog) - 1;
        int frequencyMask = (1 << freqLog) - 1;
        return frequencyMask & ~probabilityMask;
    }

    @NodeIntrinsic(ForeignCallNode.class)
    public static native void methodInvocationEvent(@ConstantNodeParameter ForeignCallDescriptor descriptor, MethodCountersPointer counters);

    @Snippet
    public static void profileMethodEntryWithProbability(MethodCountersPointer counters, int random, @ConstantParameter int freqLog, @ConstantParameter int probLog) {
        if (probability(1.0 / (1 << probLog), shouldProfile(probLog, random))) {
            int counterValue = counters.readInt(config(INJECTED_VMCONFIG).invocationCounterOffset) + (config(INJECTED_VMCONFIG).invocationCounterIncrement << probLog);
            counters.writeInt(config(INJECTED_VMCONFIG).invocationCounterOffset, counterValue);
            if (freqLog >= 0) {
                int mask = notificationMask(freqLog, probLog);
                if (probability(SLOW_PATH_PROBABILITY, (counterValue & (mask << config(INJECTED_VMCONFIG).invocationCounterShift)) == 0)) {
                    methodInvocationEvent(HotSpotBackend.INVOCATION_EVENT, counters);
                }
            }
        }
    }

    @NodeIntrinsic(ForeignCallNode.class)
    public static native void methodBackedgeEvent(@ConstantNodeParameter ForeignCallDescriptor descriptor, MethodCountersPointer counters, int bci, int targetBci);

    @Snippet
    public static void profileBackedgeWithProbability(MethodCountersPointer counters, int random, @ConstantParameter int freqLog, @ConstantParameter int probLog, int bci, int targetBci) {
        if (probability(1.0 / (1 << probLog), shouldProfile(probLog, random))) {
            int counterValue = counters.readInt(config(INJECTED_VMCONFIG).backedgeCounterOffset) + (config(INJECTED_VMCONFIG).invocationCounterIncrement << probLog);
            counters.writeInt(config(INJECTED_VMCONFIG).backedgeCounterOffset, counterValue);
            int mask = notificationMask(freqLog, probLog);
            if (probability(SLOW_PATH_PROBABILITY, (counterValue & (mask << config(INJECTED_VMCONFIG).invocationCounterShift)) == 0)) {
                methodBackedgeEvent(HotSpotBackend.BACKEDGE_EVENT, counters, bci, targetBci);
            }
        }
    }

    @Snippet
    public static void profileConditionalBackedgeWithProbability(MethodCountersPointer counters, int random, @ConstantParameter int freqLog, @ConstantParameter int probLog, boolean branchCondition,
                    int bci, int targetBci) {
        if (branchCondition) {
            profileBackedgeWithProbability(counters, random, freqLog, probLog, bci, targetBci);
        }
    }

    public static class Templates extends AbstractTemplates {
        private final SnippetInfo profileMethodEntryWithProbability = snippet(ProbabilisticProfileSnippets.class, "profileMethodEntryWithProbability");
        private final SnippetInfo profileBackedgeWithProbability = snippet(ProbabilisticProfileSnippets.class, "profileBackedgeWithProbability");
        private final SnippetInfo profileConditionalBackedgeWithProbability = snippet(ProbabilisticProfileSnippets.class, "profileConditionalBackedgeWithProbability");

        public Templates(HotSpotProviders providers, TargetDescription target) {
            super(providers, providers.getSnippetReflection(), target);
        }

        public void lower(ProfileNode profileNode, LoweringTool tool) {
            assert profileNode.getRandom() != null;

            StructuredGraph graph = profileNode.graph();
            LoadMethodCountersNode counters = graph.unique(new LoadMethodCountersNode(profileNode.getProfiledMethod()));

            if (profileNode instanceof ProfileBranchNode) {
                // Backedge event
                ProfileBranchNode profileBranchNode = (ProfileBranchNode) profileNode;
                SnippetInfo snippet = profileBranchNode.hasCondition() ? profileConditionalBackedgeWithProbability : profileBackedgeWithProbability;
                Arguments args = new Arguments(snippet, graph.getGuardsStage(), tool.getLoweringStage());
                ConstantNode bci = ConstantNode.forInt(profileBranchNode.bci(), graph);
                ConstantNode targetBci = ConstantNode.forInt(profileBranchNode.targetBci(), graph);
                args.add("counters", counters);
                args.add("random", profileBranchNode.getRandom());
                args.addConst("freqLog", profileBranchNode.getNotificationFreqLog());
                args.addConst("probLog", profileBranchNode.getProbabilityLog());
                if (profileBranchNode.hasCondition()) {
                    args.add("branchCondition", profileBranchNode.branchCondition());
                }
                args.add("bci", bci);
                args.add("targetBci", targetBci);

                SnippetTemplate template = template(args);
                template.instantiate(providers.getMetaAccess(), profileNode, DEFAULT_REPLACER, args);
            } else if (profileNode instanceof ProfileInvokeNode) {
                ProfileInvokeNode profileInvokeNode = (ProfileInvokeNode) profileNode;
                // Method invocation event
                Arguments args = new Arguments(profileMethodEntryWithProbability, graph.getGuardsStage(), tool.getLoweringStage());
                args.add("counters", counters);
                args.add("random", profileInvokeNode.getRandom());
                args.addConst("freqLog", profileInvokeNode.getNotificationFreqLog());
                args.addConst("probLog", profileInvokeNode.getProbabilityLog());
                SnippetTemplate template = template(args);
                template.instantiate(providers.getMetaAccess(), profileNode, DEFAULT_REPLACER, args);
            } else {
                throw new GraalError("Unsupported profile node type: " + profileNode);
            }

            assert profileNode.hasNoUsages();
            if (!profileNode.isDeleted()) {
                GraphUtil.killWithUnusedFloatingInputs(profileNode);
            }
        }
    }
}
