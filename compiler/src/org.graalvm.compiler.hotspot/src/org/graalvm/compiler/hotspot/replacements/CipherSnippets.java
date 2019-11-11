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
package org.graalvm.compiler.hotspot.replacements;

import static org.graalvm.compiler.hotspot.HotSpotBackend.DECRYPT;
import static org.graalvm.compiler.hotspot.HotSpotBackend.DECRYPT_WITH_ORIGINAL_KEY;;
import static org.graalvm.compiler.hotspot.HotSpotBackend.ENCRYPT;
import static org.graalvm.compiler.replacements.SnippetTemplate.DEFAULT_REPLACER;

import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.debug.DebugHandlersFactory;
import org.graalvm.compiler.graph.Node.ConstantNodeParameter;
import org.graalvm.compiler.graph.Node.NodeIntrinsic;
import org.graalvm.compiler.hotspot.meta.HotSpotProviders;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.extended.ForeignCallNode;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.replacements.SnippetTemplate;
import org.graalvm.compiler.replacements.SnippetTemplate.AbstractTemplates;
import org.graalvm.compiler.replacements.SnippetTemplate.Arguments;
import org.graalvm.compiler.replacements.SnippetTemplate.SnippetInfo;
import org.graalvm.compiler.replacements.Snippets;
import org.graalvm.compiler.word.Word;
import org.graalvm.word.Pointer;

import jdk.vm.ci.code.TargetDescription;

public class CipherSnippets implements Snippets {

    @Snippet
    static void encryptAES(Word inAddr, Word outAddr, Pointer kAddr, Pointer rAddr, int inLength) {
        encryptAESCryptStub(ENCRYPT, inAddr, outAddr, kAddr, rAddr, inLength);
    }

    @Snippet
    static void decryptAES(Word inAddr, Word outAddr, Pointer kAddr, Pointer rAddr, int inLength) {
        decryptAESCryptStub(DECRYPT, inAddr, outAddr, kAddr, rAddr, inLength);
    }

    @Snippet
    static void decryptAESWithOriginalKey(Word in, Word out, Pointer key, Pointer r, int inLength, Pointer originalKey) {
        decryptAESCryptWithOriginalKeyStub(DECRYPT_WITH_ORIGINAL_KEY, in, out, key, r, inLength, originalKey);
    }

    @NodeIntrinsic(ForeignCallNode.class)
    public static native void encryptAESCryptStub(@ConstantNodeParameter ForeignCallDescriptor descriptor, Word in, Word out, Pointer key, Pointer r, int inLength);

    @NodeIntrinsic(ForeignCallNode.class)
    public static native void decryptAESCryptStub(@ConstantNodeParameter ForeignCallDescriptor descriptor, Word in, Word out, Pointer key, Pointer r, int inLength);

    @NodeIntrinsic(ForeignCallNode.class)
    public static native void decryptAESCryptWithOriginalKeyStub(@ConstantNodeParameter ForeignCallDescriptor descriptor, Word in, Word out, Pointer key, Pointer r, int inLength, Pointer originalKey);

    public static class Templates extends AbstractTemplates {

        private final SnippetInfo encryptAES = snippet(CipherSnippets.class, "encryptAES");
        private final SnippetInfo decryptAES = snippet(CipherSnippets.class, "decryptAES");
        private final SnippetInfo decryptAESWithOriginalKey = snippet(CipherSnippets.class, "decryptAESWithOriginalKey");

        public Templates(OptionValues options, Iterable<DebugHandlersFactory> factories, HotSpotProviders providers, TargetDescription target) {
            super(options, factories, providers, providers.getSnippetReflection(), target);
        }

        public void lower(CipherNode cipherNode, LoweringTool tool) {
            StructuredGraph graph = cipherNode.graph();
            Arguments args = null;
            if (cipherNode.encrypt) {
                args = new Arguments(encryptAES, graph.getGuardsStage(), tool.getLoweringStage());
                args.add("inAddr", cipherNode.values.get(0));
                args.add("outAddr", cipherNode.values.get(1));
                args.add("kAddr", cipherNode.values.get(2));
                args.add("rAddr", cipherNode.values.get(3));
                args.add("inLength", cipherNode.values.get(4));
            } else {
                if (cipherNode.withOriginalKey) {
                    args = new Arguments(decryptAESWithOriginalKey, graph.getGuardsStage(), tool.getLoweringStage());
                    args.add("in", cipherNode.values.get(0));
                    args.add("out", cipherNode.values.get(1));
                    args.add("key", cipherNode.values.get(2));
                    args.add("r", cipherNode.values.get(3));
                    args.add("inLength", cipherNode.values.get(4));
                    args.add("originalKey", cipherNode.values.get(5));
                } else {
                    args = new Arguments(decryptAES, graph.getGuardsStage(), tool.getLoweringStage());
                    args.add("inAddr", cipherNode.values.get(0));
                    args.add("outAddr", cipherNode.values.get(1));
                    args.add("kAddr", cipherNode.values.get(2));
                    args.add("rAddr", cipherNode.values.get(3));
                    args.add("inLength", cipherNode.values.get(4));
                }
            }
            SnippetTemplate template = template(cipherNode, args);
            template.instantiate(providers.getMetaAccess(), cipherNode, DEFAULT_REPLACER, args);
        }
    }
}
