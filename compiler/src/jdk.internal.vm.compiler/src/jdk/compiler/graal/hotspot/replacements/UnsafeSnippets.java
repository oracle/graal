/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.compiler.graal.hotspot.replacements;

import static jdk.compiler.graal.hotspot.GraalHotSpotVMConfig.INJECTED_VMCONFIG;
import static jdk.compiler.graal.replacements.SnippetTemplate.DEFAULT_REPLACER;

import jdk.compiler.graal.api.replacements.Snippet;
import jdk.compiler.graal.hotspot.HotSpotBackend;
import jdk.compiler.graal.hotspot.meta.HotSpotProviders;
import jdk.compiler.graal.hotspot.nodes.CurrentJavaThreadNode;
import jdk.compiler.graal.nodes.ComputeObjectAddressNode;
import jdk.compiler.graal.nodes.StructuredGraph;
import jdk.compiler.graal.nodes.spi.LoweringTool;
import jdk.compiler.graal.options.OptionValues;
import jdk.compiler.graal.replacements.SnippetTemplate;
import jdk.compiler.graal.replacements.SnippetTemplate.AbstractTemplates;
import jdk.compiler.graal.replacements.SnippetTemplate.Arguments;
import jdk.compiler.graal.replacements.SnippetTemplate.SnippetInfo;
import jdk.compiler.graal.replacements.Snippets;
import jdk.compiler.graal.word.Word;
import org.graalvm.word.LocationIdentity;
import org.graalvm.word.WordFactory;

public class UnsafeSnippets implements Snippets {

    public static final String copyMemoryName = "copyMemory0";

    @SuppressWarnings("unused")
    @Snippet
    static void copyMemory(Object receiver, Object srcBase, long srcOffset, Object destBase, long destOffset, long bytes) {
        Word srcAddr = WordFactory.unsigned(ComputeObjectAddressNode.get(srcBase, srcOffset));
        Word dstAddr = WordFactory.unsigned(ComputeObjectAddressNode.get(destBase, destOffset));
        Word size = WordFactory.signed(bytes);
        Word javaThread = CurrentJavaThreadNode.get();
        int offset = HotSpotReplacementsUtil.doingUnsafeAccessOffset(INJECTED_VMCONFIG);
        LocationIdentity any = LocationIdentity.any();

        /* Set doingUnsafeAccess to guard and handle unsafe memory access failures */
        javaThread.writeByte(offset, (byte) 1, any);
        HotSpotBackend.unsafeArraycopy(srcAddr, dstAddr, size);
        /* Reset doingUnsafeAccess */
        javaThread.writeByte(offset, (byte) 0, any);
    }

    public static class Templates extends AbstractTemplates {

        private final SnippetInfo copyMemory;

        @SuppressWarnings("this-escape")
        public Templates(OptionValues options, HotSpotProviders providers) {
            super(options, providers);

            this.copyMemory = snippet(providers, UnsafeSnippets.class, "copyMemory");
        }

        public void lower(UnsafeCopyMemoryNode copyMemoryNode, LoweringTool tool) {
            StructuredGraph graph = copyMemoryNode.graph();
            Arguments args = new Arguments(copyMemory, graph.getGuardsStage(), tool.getLoweringStage());
            args.add("receiver", copyMemoryNode.receiver);
            args.add("srcBase", copyMemoryNode.srcBase);
            args.add("srcOffset", copyMemoryNode.srcOffset);
            args.add("destBase", copyMemoryNode.destBase);
            args.add("destOffset", copyMemoryNode.desOffset);
            args.add("bytes", copyMemoryNode.bytes);
            SnippetTemplate template = template(tool, copyMemoryNode, args);
            template.instantiate(tool.getMetaAccess(), copyMemoryNode, DEFAULT_REPLACER, args);
        }
    }
}
