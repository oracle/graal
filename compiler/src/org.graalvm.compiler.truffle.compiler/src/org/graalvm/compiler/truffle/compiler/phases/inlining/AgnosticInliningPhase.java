/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.spi.CoreProviders;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.serviceprovider.GraalServices;
import org.graalvm.compiler.truffle.common.CallNodeProvider;
import org.graalvm.compiler.truffle.common.CompilableTruffleAST;
import org.graalvm.compiler.truffle.compiler.PartialEvaluator;
import org.graalvm.compiler.truffle.compiler.SharedTruffleCompilerOptions;
import org.graalvm.compiler.truffle.compiler.TruffleCompilerOptions;

public final class AgnosticInliningPhase extends BasePhase<CoreProviders> {

    private static final InliningPolicyProvider POLICY_PROVIDER;

    static {
        final Iterable<InliningPolicyProvider> services = GraalServices.load(InliningPolicyProvider.class);
        final ArrayList<InliningPolicyProvider> providers = new ArrayList<>();
        for (InliningPolicyProvider provider : services) {
            providers.add(provider);
        }
        final String policy = TruffleCompilerOptions.getValue(TruffleCompilerOptions.TruffleInliningPolicy);
        POLICY_PROVIDER = policy.equals("") ? maxPriorityProvider(providers) : chosenProvider(providers, policy);
    }

    private final PartialEvaluator partialEvaluator;
    private final CallNodeProvider callNodeProvider;
    private final CompilableTruffleAST compilableTruffleAST;

    public AgnosticInliningPhase(PartialEvaluator partialEvaluator, CallNodeProvider callNodeProvider, CompilableTruffleAST compilableTruffleAST) {
        this.partialEvaluator = partialEvaluator;
        this.callNodeProvider = callNodeProvider;
        this.compilableTruffleAST = compilableTruffleAST;
    }

    private static InliningPolicyProvider chosenProvider(ArrayList<InliningPolicyProvider> providers, String name) {
        for (InliningPolicyProvider provider : providers) {
            if (provider.getName().equals(name)) {
                return provider;
            }
        }
        throw new IllegalStateException("No inlining policy provider with provided name: " + name);
    }

    private static InliningPolicyProvider maxPriorityProvider(ArrayList<InliningPolicyProvider> providers) {
        Collections.sort(providers);
        return providers.get(0);
    }

    @Override
    protected void run(StructuredGraph graph, CoreProviders coreProviders) {
        if (!TruffleCompilerOptions.getValue(SharedTruffleCompilerOptions.TruffleFunctionInlining)) {
            return;
        }
        final InliningPolicy policy = POLICY_PROVIDER.get(coreProviders, graph.getOptions());
        final CallTree tree = new CallTree(partialEvaluator, callNodeProvider, compilableTruffleAST, graph, policy);
        policy.run(tree);
        tree.trace();
        tree.dump();
    }
}
