/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.compiler.phases;

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.java.LoadFieldNode;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.phases.PhaseSuite;
import org.graalvm.compiler.phases.common.HighTierLoweringPhase;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.truffle.common.TruffleCompilerRuntime;

import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * This phase should ideally already be done using a node plugin when creating the graph. However
 * during PE field plugins are currently broken.
 */
public class TruffleInjectImmutableFrameFieldsPhase extends BasePhase<HighTierContext> {

    public static class Options {
        @Option(help = "Whether Truffle should mark final frame fields as immutable.")//
        public static final OptionKey<Boolean> TruffleImmutableFrameFields = new OptionKey<>(true);
    }

    TruffleInjectImmutableFrameFieldsPhase() {
    }

    @Override
    protected void run(StructuredGraph graph, HighTierContext context) {
        TruffleCompilerRuntime runtime = TruffleCompilerRuntime.getRuntimeIfAvailable();
        if (runtime == null) {
            return;
        }

        ResolvedJavaType frameType = runtime.resolveType(context.getMetaAccess(), "com.oracle.truffle.api.impl.FrameWithoutBoxing");
        for (Node node : graph.getNodes()) {
            if (node instanceof LoadFieldNode) {
                LoadFieldNode fieldNode = (LoadFieldNode) node;
                if (isForcedImmutable(fieldNode.field(), frameType) && !fieldNode.getLocationIdentity().isImmutable()) {
                    graph.replaceFixedWithFixed(fieldNode, graph.add(LoadFieldNode.createOverrideImmutable(fieldNode)));
                }
            }
        }
    }

    private static boolean isForcedImmutable(ResolvedJavaField field, ResolvedJavaType frameType) {
        TruffleCompilerRuntime runtime = TruffleCompilerRuntime.getRuntimeIfAvailable();
        if (runtime == null) {
            return false;
        }
        if (field.isStatic()) {
            return false;
        }
        if (!field.isFinal()) {
            return false;
        }
        if (field.isVolatile()) {
            /*
             * Do not handle volatile fields.
             */
            return false;
        }
        if (!field.getDeclaringClass().equals(frameType)) {
            return false;
        }
        return true;
    }

    public static void install(PhaseSuite<HighTierContext> highTier, OptionValues options) {
        // before lowering phase.
        if (Options.TruffleImmutableFrameFields.getValue(options) && TruffleCompilerRuntime.getRuntimeIfAvailable() != null) {
            var phase = highTier.findPhase(HighTierLoweringPhase.class);
            phase.previous();
            phase.add(new TruffleInjectImmutableFrameFieldsPhase());
        }
    }
}
