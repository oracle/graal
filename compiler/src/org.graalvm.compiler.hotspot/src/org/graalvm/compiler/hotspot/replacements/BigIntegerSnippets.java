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

import static org.graalvm.compiler.replacements.SnippetTemplate.DEFAULT_REPLACER;

import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.debug.DebugHandlersFactory;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.NodeInputList;
import org.graalvm.compiler.hotspot.BigIntegerSubstitutionNode;
import org.graalvm.compiler.hotspot.HotSpotBackend;
import org.graalvm.compiler.hotspot.meta.HotSpotProviders;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.replacements.SnippetTemplate;
import org.graalvm.compiler.replacements.SnippetTemplate.AbstractTemplates;
import org.graalvm.compiler.replacements.SnippetTemplate.Arguments;
import org.graalvm.compiler.replacements.SnippetTemplate.SnippetInfo;
import org.graalvm.compiler.replacements.Snippets;
import org.graalvm.compiler.word.Word;

import jdk.vm.ci.code.TargetDescription;

public class BigIntegerSnippets implements Snippets {

    @Snippet
    public static void multiplyToLen(Word xAddr, int xlen, Word yAddr, int ylen, Word zAddr, int zLen) {
        HotSpotBackend.multiplyToLenStub(xAddr, xlen, yAddr, ylen, zAddr, zLen);
    }

    @Snippet
    public static int mulAdd(Word inAddr, Word outAddr, int newOffset, int len, int k) {
        return HotSpotBackend.mulAddStub(inAddr, outAddr, newOffset, len, k);
    }

    @Snippet
    public static void squareToLen(Word xAddr, int len, Word zAddr, int zLen) {
        HotSpotBackend.implSquareToLen(xAddr, len, zAddr, zLen);
    }

    @Snippet
    public static void montgomerySquare(Word aAddr, Word nAddr, int len, long inv, Word productAddr) {
        HotSpotBackend.implMontgomerySquare(aAddr, nAddr, len, inv, productAddr);
    }

    @Snippet
    public static void montgomeryMultiply(Word aAddr, Word bAddr, Word nAddr, int len, long inv, Word productAddr) {
        HotSpotBackend.implMontgomeryMultiply(aAddr, bAddr, nAddr, len, inv, productAddr);
    }

    public static class Templates extends AbstractTemplates {

        private final SnippetInfo mulToLen = snippet(BigIntegerSnippets.class, "multiplyToLen");
        private final SnippetInfo mullAdd = snippet(BigIntegerSnippets.class, "mulAdd");
        private final SnippetInfo montgomeryMul = snippet(BigIntegerSnippets.class, "montgomeryMultiply");
        private final SnippetInfo montogmerySquare = snippet(BigIntegerSnippets.class, "montgomerySquare");
        private final SnippetInfo squareToLen = snippet(BigIntegerSnippets.class, "squareToLen");

        public Templates(OptionValues options, Iterable<DebugHandlersFactory> factories, HotSpotProviders providers, TargetDescription target) {
            super(options, factories, providers, providers.getSnippetReflection(), target);
        }

        public void lower(BigIntegerSubstitutionNode bigIntegerNode, LoweringTool tool) {
            StructuredGraph graph = bigIntegerNode.graph();
            Arguments args = null;
            NodeInputList<ValueNode> values = bigIntegerNode.getValues();
            switch (bigIntegerNode.op) {
                case MUL_TO_LEN:
                    args = new Arguments(mulToLen, graph.getGuardsStage(), tool.getLoweringStage());
                    args.add("xAddr", values.get(0));
                    args.add("xlen", values.get(1));
                    args.add("yAddr", values.get(2));
                    args.add("ylen", values.get(3));
                    args.add("zAddr", values.get(4));
                    args.add("zLen", values.get(5));
                    break;
                case MUL_ADD:
                    args = new Arguments(mullAdd, graph.getGuardsStage(), tool.getLoweringStage());
                    args.add("inAddr", values.get(0));
                    args.add("outAddr", values.get(1));
                    args.add("newOffset", values.get(2));
                    args.add("len", values.get(3));
                    args.add("k", values.get(4));
                    break;
                case MONTGOMERY_MUL:
                    args = new Arguments(montgomeryMul, graph.getGuardsStage(), tool.getLoweringStage());
                    args.add("aAddr", values.get(0));
                    args.add("bAddr", values.get(1));
                    args.add("nAddr", values.get(2));
                    args.add("len", values.get(3));
                    args.add("inv", values.get(4));
                    args.add("productAddr", values.get(5));
                    break;
                case MONTOGEMRY_SQUARE:
                    args = new Arguments(montogmerySquare, graph.getGuardsStage(), tool.getLoweringStage());
                    args.add("aAddr", values.get(0));
                    args.add("nAddr", values.get(1));
                    args.add("len", values.get(2));
                    args.add("inv", values.get(3));
                    args.add("productAddr", values.get(4));
                    break;
                case SQUARE_TO_LEN:
                    args = new Arguments(squareToLen, graph.getGuardsStage(), tool.getLoweringStage());
                    args.add("xAddr", values.get(0));
                    args.add("len", values.get(1));
                    args.add("zAddr", values.get(2));
                    args.add("zLen", values.get(3));
                    break;
                default:
                    GraalError.shouldNotReachHere();
                    break;
            }
            SnippetTemplate template = template(bigIntegerNode, args);
            template.instantiate(providers.getMetaAccess(), bigIntegerNode, DEFAULT_REPLACER, args);
        }
    }

}
