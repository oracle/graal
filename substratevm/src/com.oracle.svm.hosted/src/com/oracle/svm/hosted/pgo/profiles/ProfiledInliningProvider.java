/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.pgo.profiles;

import java.util.Map;

import com.oracle.svm.hosted.cai.PrefixTree;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.HostedUniverse;
import com.oracle.svm.hosted.phases.priorityinline.SubstrateInliningProvider;
import com.oracle.svm.shared.option.HostedOptionKey;

import jdk.graal.compiler.graph.NodeSourcePosition;
import jdk.graal.compiler.nodes.CallTargetNode;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.common.priorityinline.nodes.CallTreeNode;
import jdk.vm.ci.meta.JavaMethodProfile;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Inlining provider that gives the priority inliner profile-derived hotness: a calling-context
 * cursor per compilation root (so cutoff hotness and sampled callee profiles become available), the
 * larger of the sampled and the call-count hotness of a call edge, and the hot bonus while
 * inlining, which is otherwise zero.
 */
public final class ProfiledInliningProvider extends SubstrateInliningProvider {

    /** The resolved profile, for its {@code callCountProfiles}. */
    private final IprofProfilesLookup profilesLookup;

    /** Options that turn single uses of the profile in the inliner and later phases on or off. */
    static final class Options {
        // @formatter:off
        /** The factor that {@link ProfiledInliningProvider#hotBonusWhileInlining} returns. */
        @Option(help = "Local-benefit multiplier, scaled by root-relative hotness, applied to hot call-tree nodes while inlining. 0 disables.")//
        static final HostedOptionKey<Integer> PGOHotInliningBonus = new HostedOptionKey<>(1);

        /** What {@link ProfiledInliningProvider#shouldApplyProfilesWhileExpanding} returns. */
        @Option(help = "Apply profiles to callee graphs expanded under a hot compilation root.")//
        static final HostedOptionKey<Boolean> PGOApplyProfilesWhileExpanding = new HostedOptionKey<>(true);

        /** Off to measure sampled callee profiles, as the pgo-receivers-disabled gate run does. */
        @Option(help = "Let the inliner prefer sampled callee method profiles at indirect calls over analysis type profiles.")//
        static final HostedOptionKey<Boolean> PGOSamplingMethodProfiles = new HostedOptionKey<>(true);

        /** Read by {@link SamplingHotness#providerFor}. */
        @Option(help = "Report sampled compilation roots as hot callers (enables hot-callee devirtualization and hot-caller profile application).")//
        static final HostedOptionKey<Boolean> PGOSamplingHotCaller = new HostedOptionKey<>(true);

        /** Read by {@link SamplingHotness#providerFor}. */
        @Option(help = "Expose sampled self time to the compiler, which gives hot code larger budgets in duplication and loop unrolling.")//
        static final HostedOptionKey<Boolean> PGOSamplingSelfTime = new HostedOptionKey<>(true);
        // @formatter:on
    }

    /**
     * Creates a provider that finds each compilation root's calling contexts in {@code hotness}.
     *
     * @param universe the image's universe
     * @param hotness the method-rooted tree of sampled stacks
     * @param profilesLookup the resolved profile, for its {@code callCountProfiles}
     */
    public ProfiledInliningProvider(HostedUniverse universe, SamplingHotness hotness, IprofProfilesLookup profilesLookup) {
        super(universe, hotness::cursorFor);
        this.profilesLookup = profilesLookup;
    }

    /**
     * The sampled hotness, or, if larger, the share of the root's calls that went through this call
     * edge according to {@code callCountProfiles}, capped at 1.
     */
    @Override
    public double compilationRootRelativeHotness(HostedMethod compilationRoot, NodeSourcePosition callPosition, ResolvedJavaMethod dispatchedMethod) {
        double sampled = super.compilationRootRelativeHotness(compilationRoot, callPosition, dispatchedMethod);
        long rootCalls = profilesLookup.getCallCountOrZero(compilationRoot);
        if (rootCalls <= 0) {
            return sampled;
        }
        NodeSourcePosition edgeContext = new NodeSourcePosition(callPosition, dispatchedMethod, 0);
        long edgeCalls = profilesLookup.getContextCallCount(edgeContext).orElse(0L);
        if (edgeCalls <= 0) {
            return sampled;
        }
        double callRatio = Math.min(1.0, (double) edgeCalls / rootCalls);
        return Math.max(sampled, callRatio);
    }

    /** The sampled callee profile, or {@code null} if {@code PGOSamplingMethodProfiles} is off. */
    @Override
    public JavaMethodProfile samplingMethodProfiles(Map<CallTreeNode, PrefixTree.Cursor> nodeContextMap, HostedMethod root, CallTreeNode caller, CallTargetNode callee) {
        return Options.PGOSamplingMethodProfiles.getValue() ? super.samplingMethodProfiles(nodeContextMap, root, caller, callee) : null;
    }

    /** The sampled callee profile, or {@code null} if {@code PGOSamplingMethodProfiles} is off. */
    @Override
    public JavaMethodProfile samplingMethodProfiles(HostedMethod compilationRoot, Invoke calleeInvoke) {
        return Options.PGOSamplingMethodProfiles.getValue() ? super.samplingMethodProfiles(compilationRoot, calleeInvoke) : null;
    }

    /** On by default; the base provider never applies profiles while expanding. */
    @Override
    protected boolean shouldApplyProfilesWhileExpanding(OptionValues options) {
        return Options.PGOApplyProfilesWhileExpanding.getValue(options);
    }

    /** {@code PGOHotInliningBonus}; the base provider gives no bonus. */
    @Override
    protected int hotBonusWhileInlining(OptionValues options) {
        return Options.PGOHotInliningBonus.getValue(options);
    }
}
