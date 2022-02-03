/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.compiler.phases.inlining;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Objects;

import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.spi.CoreProviders;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.serviceprovider.GraalServices;
import org.graalvm.compiler.truffle.compiler.PartialEvaluator;
import org.graalvm.compiler.truffle.options.PolyglotCompilerOptions;

public final class AgnosticInliningPhase extends BasePhase<CoreProviders> {

    private static final ArrayList<InliningPolicyProvider> POLICY_PROVIDERS;

    static {
        final Iterable<InliningPolicyProvider> services = GraalServices.load(InliningPolicyProvider.class);
        final ArrayList<InliningPolicyProvider> providers = new ArrayList<>();
        for (InliningPolicyProvider provider : services) {
            providers.add(provider);
        }
        Collections.sort(providers);
        POLICY_PROVIDERS = providers;
    }

    private final PartialEvaluator partialEvaluator;
    private final PartialEvaluator.Request request;

    public AgnosticInliningPhase(PartialEvaluator partialEvaluator, PartialEvaluator.Request request) {
        this.partialEvaluator = partialEvaluator;
        this.request = request;
    }

    private static InliningPolicyProvider chosenProvider(String name) {
        for (InliningPolicyProvider provider : AgnosticInliningPhase.POLICY_PROVIDERS) {
            if (provider.getName().equals(name)) {
                return provider;
            }
        }
        throw new IllegalStateException("No inlining policy provider with provided name: " + name);
    }

    private InliningPolicyProvider getInliningPolicyProvider(boolean firstTier) {
        final String policy = request.options.get(firstTier ? PolyglotCompilerOptions.FirstTierInliningPolicy : PolyglotCompilerOptions.InliningPolicy);
        if (Objects.equals(policy, "")) {
            return POLICY_PROVIDERS.get(firstTier ? POLICY_PROVIDERS.size() - 1 : 0);
        } else {
            return chosenProvider(policy);
        }
    }

    @Override
    protected void run(StructuredGraph graph, CoreProviders coreProviders) {
        final InliningPolicy policy = getInliningPolicyProvider(request.isFirstTier()).get(request.options, coreProviders);
        final CallTree tree = new CallTree(partialEvaluator, request, policy);
        tree.dumpBasic("Before Inline");
        if (optionsAllowInlining()) {
            policy.run(tree);
            tree.dumpBasic("After Inline");
            tree.collectTargetsToDequeue(request.task.inliningData());
            tree.updateTracingInfo(request.task.inliningData());
        }
        tree.finalizeGraph();
        tree.trace();
    }

    private boolean optionsAllowInlining() {
        return request.options.get(PolyglotCompilerOptions.Inlining);
    }

    @Override
    public boolean checkContract() {
        // inlining per definition increases graph size a lot
        return false;
    }

}
