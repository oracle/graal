/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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
import static jdk.graal.compiler.replacements.SnippetTemplate.DEFAULT_REPLACER;

import org.graalvm.word.LocationIdentity;

import jdk.graal.compiler.api.replacements.Snippet;
import jdk.graal.compiler.hotspot.HotSpotBackend;
import jdk.graal.compiler.hotspot.meta.HotSpotProviders;
import jdk.graal.compiler.hotspot.nodes.CurrentJavaThreadNode;
import jdk.graal.compiler.nodes.ComputeObjectAddressNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.spi.LoweringTool;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.replacements.SnippetTemplate;
import jdk.graal.compiler.replacements.SnippetTemplate.AbstractTemplates;
import jdk.graal.compiler.replacements.SnippetTemplate.Arguments;
import jdk.graal.compiler.replacements.SnippetTemplate.SnippetInfo;
import jdk.graal.compiler.replacements.Snippets;
import jdk.graal.compiler.word.Word;

public class UnsafeSnippets implements Snippets {

    @SuppressWarnings("unused")
    @Snippet
    static void copyMemory(Object receiver, Object srcBase, long srcOffset, Object destBase, long destOffset, long bytes) {
        Word srcAddr = Word.unsigned(ComputeObjectAddressNode.get(srcBase, srcOffset));
        Word dstAddr = Word.unsigned(ComputeObjectAddressNode.get(destBase, destOffset));
        Word size = Word.signed(bytes);
        Word javaThread = CurrentJavaThreadNode.get();
        int offset = HotSpotReplacementsUtil.doingUnsafeAccessOffset(INJECTED_VMCONFIG);
        LocationIdentity any = LocationIdentity.any();

        /* Set doingUnsafeAccess to guard and handle unsafe memory access failures */
        javaThread.writeByte(offset, (byte) 1, any);
        HotSpotBackend.unsafeArraycopy(srcAddr, dstAddr, size);
        /* Reset doingUnsafeAccess */
        javaThread.writeByte(offset, (byte) 0, any);
    }

    @SuppressWarnings("unused")
    @Snippet
    static void setMemory(Object receiver, Object objBase, long objOffset, long bytes, byte value) {
        Word objAddr = Word.unsigned(ComputeObjectAddressNode.get(objBase, objOffset));
        Word size = Word.signed(bytes);
        Word javaThread = CurrentJavaThreadNode.get();
        int offset = HotSpotReplacementsUtil.doingUnsafeAccessOffset(INJECTED_VMCONFIG);
        LocationIdentity any = LocationIdentity.any();

        /* Set doingUnsafeAccess to guard and handle unsafe memory access failures */
        javaThread.writeByte(offset, (byte) 1, any);
        HotSpotBackend.unsafeSetMemory(objAddr, size, value);
        /* Reset doingUnsafeAccess */
        javaThread.writeByte(offset, (byte) 0, any);
    }

    public static class Templates extends AbstractTemplates {

        private final SnippetInfo copyMemory;
        private final SnippetInfo setMemory;

        @SuppressWarnings("this-escape")
        public Templates(OptionValues options, HotSpotProviders providers) {
            super(options, providers);

            this.copyMemory = snippet(providers, UnsafeSnippets.class, "copyMemory");
            this.setMemory = snippet(providers, UnsafeSnippets.class, "setMemory");
        }

        public void lower(UnsafeCopyMemoryNode copyMemoryNode, LoweringTool tool) {
            StructuredGraph graph = copyMemoryNode.graph();
            Arguments args = new Arguments(copyMemory, graph, tool.getLoweringStage());
            args.add("receiver", copyMemoryNode.receiver);
            args.add("srcBase", copyMemoryNode.srcBase);
            args.add("srcOffset", copyMemoryNode.srcOffset);
            args.add("destBase", copyMemoryNode.destBase);
            args.add("destOffset", copyMemoryNode.desOffset);
            args.add("bytes", copyMemoryNode.bytes);
            SnippetTemplate template = template(tool, copyMemoryNode, args);
            template.instantiate(tool.getMetaAccess(), copyMemoryNode, DEFAULT_REPLACER, args);
        }

        public void lower(UnsafeSetMemoryNode setMemoryNode, LoweringTool tool) {
            StructuredGraph graph = setMemoryNode.graph();
            Arguments args = new Arguments(setMemory, graph, tool.getLoweringStage());
            args.add("receiver", setMemoryNode.receiver);
            args.add("objBase", setMemoryNode.obj);
            args.add("objOffset", setMemoryNode.offset);
            args.add("bytes", setMemoryNode.bytes);
            args.add("value", setMemoryNode.value);
            SnippetTemplate template = template(tool, setMemoryNode, args);
            template.instantiate(tool.getMetaAccess(), setMemoryNode, DEFAULT_REPLACER, args);
        }
    }
}
