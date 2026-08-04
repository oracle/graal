/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.phases;

import jdk.graal.compiler.hotspot.GraalHotSpotVMConfig;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.common.priorityinline.DefaultInliningProvider;
import jdk.graal.compiler.phases.common.priorityinline.DefaultPolicyFactory;
import jdk.graal.compiler.phases.common.priorityinline.Expander;
import jdk.graal.compiler.phases.common.priorityinline.PolicyFactory;
import jdk.graal.compiler.phases.common.priorityinline.nodes.CutoffNode;
import jdk.graal.compiler.phases.tiers.HighTierContext;
import jdk.vm.ci.meta.ProfilingInfo;

/**
 * Use a special policy that mimics what HotSpot does when running with -Xcomp.
 */
public class HotSpotDefaultInliningProvider extends DefaultInliningProvider {

    private final GraalHotSpotVMConfig config;

    public HotSpotDefaultInliningProvider(GraalHotSpotVMConfig config) {
        this.config = config;
    }

    @Override
    public PolicyFactory policy(OptionValues options) {
        if (config.xcompMode) {
            return new XCompPolicyFactory();
        }
        return super.policy(options);
    }

    /**
     * Inlining policy that more closely resembles the HotSpot inlining policy when running with
     * -Xcomp.
     */
    private static final class XCompPolicyFactory extends DefaultPolicyFactory {

        // Value taken from the default value of InlineFrequencyRatio from HotSpot
        public static final double INLINE_FREQUENCY_RATIO = 0.25;
        // Value taken from the default value of MaxInlineSize from HotSpot
        public static final int MAX_INLINE_SIZE = 35;
        // Value taken from the overridden value of FreqInlineSize from HotSpot
        public static final int FREQ_INLINE_SIZE = 325;

        @Override
        public Expander.Policy createExpanderPolicy(OptionValues options, HighTierContext context) {
            return new Expander.DefaultPolicy() {
                @Override
                public boolean shouldExpand(CutoffNode node) {
                    // C2 computes invocation frequency by dividing call site count by invocation
                    // count of the enclosing method. In Xcomp mode, methods are first compiled
                    // without profile, and thus the frequency will always initially be zero.
                    // Therefore, we use the cold MaxInlineSize to mimic the C2 first-compile
                    // behavior.
                    StructuredGraph graph = node.invoke().asNode().graph();
                    ProfilingInfo profilingInfo = graph.getProfilingInfo(graph.getCallerContext(), node.invoke().stateAfter().getMethod());
                    int sizeLimit = profilingInfo != null && profilingInfo.isMature() && node.getFrequency() > INLINE_FREQUENCY_RATIO ? FREQ_INLINE_SIZE : MAX_INLINE_SIZE;
                    if (node.targetMethod().getCodeSize() < sizeLimit) {
                        return super.shouldExpand(node);
                    }
                    return node.isForceInlined();
                }
            };
        }
    }
}
