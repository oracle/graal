/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.replacements;

import static jdk.graal.compiler.hotspot.GraalHotSpotVMConfig.INJECTED_VMCONFIG;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.KLASS_ACCESS_FLAGS_LOCATION;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.KLASS_MISC_FLAGS_LOCATION;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.jvmAccHasFinalizer;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.klassAccessFlagsOffset;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.klassMiscFlagsOffset;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.shouldUseKlassMiscFlags;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.SLOW_PATH_PROBABILITY;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.probability;

import jdk.graal.compiler.api.replacements.Snippet;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.hotspot.meta.HotSpotProviders;
import jdk.graal.compiler.hotspot.word.KlassPointer;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.java.LoweredRegisterFinalizerNode;
import jdk.graal.compiler.nodes.java.RegisterFinalizerNode;
import jdk.graal.compiler.nodes.spi.LoweringTool;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.replacements.SnippetTemplate;
import jdk.graal.compiler.replacements.SnippetTemplate.AbstractTemplates;
import jdk.graal.compiler.replacements.SnippetTemplate.Arguments;
import jdk.graal.compiler.replacements.SnippetTemplate.SnippetInfo;
import jdk.graal.compiler.replacements.Snippets;

/**
 * Performs conditional finalizer registration via a runtime call. The condition will be constant
 * folded if the exact class is available.
 *
 * @see RegisterFinalizerNode
 */
public class RegisterFinalizerSnippets implements Snippets {

    @Snippet
    public static void registerFinalizerSnippet(final Object thisObj) {
        KlassPointer klass = HotSpotReplacementsUtil.loadHub(thisObj);

        int flags = shouldUseKlassMiscFlags() ? klass.readByte(klassMiscFlagsOffset(INJECTED_VMCONFIG), KLASS_MISC_FLAGS_LOCATION)
                        : klass.readInt(klassAccessFlagsOffset(INJECTED_VMCONFIG), KLASS_ACCESS_FLAGS_LOCATION);
        if (probability(SLOW_PATH_PROBABILITY, (flags & jvmAccHasFinalizer(INJECTED_VMCONFIG)) != 0)) {
            LoweredRegisterFinalizerNode.registerFinalizer(thisObj);
        }
    }

    public static class Templates extends AbstractTemplates {

        private final SnippetInfo registerFinalizerSnippet;

        @SuppressWarnings("this-escape")
        public Templates(OptionValues options, HotSpotProviders providers) {
            super(options, providers);

            this.registerFinalizerSnippet = snippet(providers, RegisterFinalizerSnippets.class, "registerFinalizerSnippet",
                    shouldUseKlassMiscFlags() ? KLASS_MISC_FLAGS_LOCATION : KLASS_ACCESS_FLAGS_LOCATION);
        }

        public void lower(RegisterFinalizerNode node, LoweringTool tool) {
            assert !(node instanceof LoweredRegisterFinalizerNode) : Assertions.errorMessage(node);
            StructuredGraph graph = node.graph();
            Arguments args = new Arguments(registerFinalizerSnippet, graph.getGuardsStage(), tool.getLoweringStage());
            args.add("thisObj", node.getValue());
            SnippetTemplate template = template(tool, node, args);
            template.instantiate(tool.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, args);
        }

    }

}
