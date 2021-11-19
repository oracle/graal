/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot.replacements;

import static org.graalvm.compiler.hotspot.GraalHotSpotVMConfig.INJECTED_VMCONFIG;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.KLASS_ACCESS_FLAGS_LOCATION;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.jvmAccHasFinalizer;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.klassAccessFlagsOffset;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.loadHub;
import static org.graalvm.compiler.nodes.extended.BranchProbabilityNode.SLOW_PATH_PROBABILITY;
import static org.graalvm.compiler.nodes.extended.BranchProbabilityNode.probability;

import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.hotspot.meta.HotSpotProviders;
import org.graalvm.compiler.hotspot.word.KlassPointer;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.java.LoweredRegisterFinalizerNode;
import org.graalvm.compiler.nodes.java.RegisterFinalizerNode;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.replacements.SnippetTemplate;
import org.graalvm.compiler.replacements.SnippetTemplate.AbstractTemplates;
import org.graalvm.compiler.replacements.SnippetTemplate.Arguments;
import org.graalvm.compiler.replacements.SnippetTemplate.SnippetInfo;
import org.graalvm.compiler.replacements.Snippets;

/**
 * Performs conditional finalizer registration via a runtime call. The condition will be constant
 * folded if the exact class is available.
 *
 * @see RegisterFinalizerNode
 */
public class RegisterFinalizerSnippets implements Snippets {

    @Snippet
    public static void registerFinalizerSnippet(final Object thisObj) {
        KlassPointer klass = loadHub(thisObj);
        if (probability(SLOW_PATH_PROBABILITY, (klass.readInt(klassAccessFlagsOffset(INJECTED_VMCONFIG), KLASS_ACCESS_FLAGS_LOCATION) & jvmAccHasFinalizer(INJECTED_VMCONFIG)) != 0)) {
            LoweredRegisterFinalizerNode.registerFinalizer(thisObj);
        }
    }

    public static class Templates extends AbstractTemplates {

        private final SnippetInfo registerFinalizerSnippet = snippet(RegisterFinalizerSnippets.class, "registerFinalizerSnippet", HotSpotReplacementsUtil.KLASS_ACCESS_FLAGS_LOCATION);

        public Templates(OptionValues options, HotSpotProviders providers) {
            super(options, providers);
        }

        public void lower(RegisterFinalizerNode node, LoweringTool tool) {
            assert !(node instanceof LoweredRegisterFinalizerNode);
            StructuredGraph graph = node.graph();
            Arguments args = new Arguments(registerFinalizerSnippet, graph.getGuardsStage(), tool.getLoweringStage());
            args.add("thisObj", node.getValue());
            SnippetTemplate template = template(node, args);
            template.instantiate(providers.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, args);
        }

    }

}
