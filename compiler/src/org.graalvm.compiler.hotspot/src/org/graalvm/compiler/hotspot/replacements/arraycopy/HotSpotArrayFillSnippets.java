/*
 * Copyright (c) 2022, Alibaba Group Holding Limited. All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
 *
 */
package org.graalvm.compiler.hotspot.replacements.arraycopy;

import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.hotspot.meta.HotSpotForeignCallDescriptor;
import org.graalvm.compiler.hotspot.meta.HotSpotProviders;
import org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil;
import org.graalvm.compiler.nodes.ComputeObjectAddressNode;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.AddNode;
import org.graalvm.compiler.nodes.calc.LeftShiftNode;
import org.graalvm.compiler.nodes.extended.ArrayFillNode;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.replacements.SnippetTemplate;
import org.graalvm.compiler.replacements.arraycopy.ArrayFillSnippets;
import org.graalvm.compiler.word.Word;
import org.graalvm.word.LocationIdentity;
import org.graalvm.word.WordFactory;

import static org.graalvm.compiler.hotspot.GraalHotSpotVMConfig.INJECTED_VMCONFIG;
import static org.graalvm.compiler.hotspot.meta.HotSpotForeignCallDescriptor.Reexecutability.NOT_REEXECUTABLE;
import static org.graalvm.compiler.hotspot.meta.HotSpotForeignCallDescriptor.Transition.LEAF;
import static org.graalvm.word.LocationIdentity.any;

public class HotSpotArrayFillSnippets extends ArrayFillSnippets {
    // Unaligned version
    public static final HotSpotForeignCallDescriptor JINT_FILL_CALL = new HotSpotForeignCallDescriptor(LEAF, NOT_REEXECUTABLE, any(), "jint_fill", void.class, Word.class,
            int.class, int.class);
    public static final HotSpotForeignCallDescriptor JSHORT_FILL_CALL = new HotSpotForeignCallDescriptor(LEAF, NOT_REEXECUTABLE, any(), "jshort_fill", void.class, Word.class,
            int.class, int.class);
    public static final HotSpotForeignCallDescriptor JBYTE_FILL_CALL = new HotSpotForeignCallDescriptor(LEAF, NOT_REEXECUTABLE, any(), "jbyte_fill", void.class, Word.class,
            int.class, int.class);
    // Aligned version
    public static final HotSpotForeignCallDescriptor ARRAYOF_JINT_FILL_CALL = new HotSpotForeignCallDescriptor(LEAF, NOT_REEXECUTABLE, any(), "arrayof_jint_fill", void.class, Word.class,
            int.class, int.class);
    public static final HotSpotForeignCallDescriptor ARRAYOF_JSHORT_FILL_CALL = new HotSpotForeignCallDescriptor(LEAF, NOT_REEXECUTABLE, any(), "arrayof_jshort_fill", void.class, Word.class,
            int.class, int.class);
    public static final HotSpotForeignCallDescriptor ARRAYOF_JBYTE_FILL_CALL = new HotSpotForeignCallDescriptor(LEAF, NOT_REEXECUTABLE, any(), "arrayof_jbyte_fill", void.class, Word.class,
            int.class, int.class);

    @Snippet
    public void jintFillSnippet(Object to, int offset, int value, int count, @Snippet.ConstantParameter JavaKind elementKind) {
        Object toObj = GraalDirectives.guardingNonNull(to);
        Word toAddr = WordFactory.unsigned(ComputeObjectAddressNode.get(toObj, offset));
        jintFill(jintFillCallDescriptor(), toAddr, value, count);
    }

    @Snippet
    public void jshortFillSnippet(Object to, int offset, int value, int count, @Snippet.ConstantParameter JavaKind elementKind) {
        Object toObj = GraalDirectives.guardingNonNull(to);
        Word toAddr = WordFactory.unsigned(ComputeObjectAddressNode.get(toObj, offset));
        jshortFill(jshortFillCallDescriptor(), toAddr, value, count);
    }

    @Snippet
    public void jbyteFillSnippet(Object to, int offset, int value, int count, @Snippet.ConstantParameter JavaKind elementKind) {
        Object toObj = GraalDirectives.guardingNonNull(to);
        Word toAddr = WordFactory.unsigned(ComputeObjectAddressNode.get(toObj, offset));
        jbyteFill(jbyteFillCallDescriptor(), toAddr, value, count);
    }

    @Snippet
    public void arrayofJintFillSnippet(Object to, int offset, int value, int count, @Snippet.ConstantParameter JavaKind elementKind) {
        Object toObj = GraalDirectives.guardingNonNull(to);
        Word toAddr = WordFactory.unsigned(ComputeObjectAddressNode.get(toObj, offset));
        arrayofJintFill(arrayofJintFillCallDescriptor(), toAddr, value, count);
    }

    @Snippet
    public void arrayofJshortFillSnippet(Object to, int offset, int value, int count, @Snippet.ConstantParameter JavaKind elementKind) {
        Object toObj = GraalDirectives.guardingNonNull(to);
        Word toAddr = WordFactory.unsigned(ComputeObjectAddressNode.get(toObj, offset));
        arrayofJshortFill(arrayofJshortFillCallDescriptor(), toAddr, value, count);
    }

    @Snippet
    public void arrayofJbyteFillSnippet(Object to, int offset, int value, int count, @Snippet.ConstantParameter JavaKind elementKind) {
        Object toObj = GraalDirectives.guardingNonNull(to);
        Word toAddr = WordFactory.unsigned(ComputeObjectAddressNode.get(toObj, offset));
        arrayofJbyteFill(arrayofJbyteFillCallDescriptor(), toAddr, value, count);
    }

    @Override
    protected ForeignCallDescriptor jintFillCallDescriptor() {
        return JINT_FILL_CALL;
    }

    @Override
    protected ForeignCallDescriptor jshortFillCallDescriptor() {
        return JSHORT_FILL_CALL;
    }

    @Override
    protected ForeignCallDescriptor jbyteFillCallDescriptor() {
        return JBYTE_FILL_CALL;
    }

    @Override
    protected ForeignCallDescriptor arrayofJintFillCallDescriptor() {
        return ARRAYOF_JINT_FILL_CALL;
    }

    @Override
    protected ForeignCallDescriptor arrayofJshortFillCallDescriptor() {
        return ARRAYOF_JSHORT_FILL_CALL;
    }

    @Override
    protected ForeignCallDescriptor arrayofJbyteFillCallDescriptor() {
        return ARRAYOF_JBYTE_FILL_CALL;
    }

    public static class Templates extends SnippetTemplate.AbstractTemplates {
        private final SnippetTemplate.SnippetInfo jintFillSnippet;
        private final SnippetTemplate.SnippetInfo jshortFillSnippet;
        private final SnippetTemplate.SnippetInfo jbyteFillSnippet;
        private final SnippetTemplate.SnippetInfo arrayofJintFillSnippet;
        private final SnippetTemplate.SnippetInfo arrayofJshortFillSnippet;
        private final SnippetTemplate.SnippetInfo arrayofJbyteFillSnippet;

        public Templates(OptionValues options, HotSpotProviders providers) {
            super(options, providers);
            HotSpotArrayFillSnippets receiver = new HotSpotArrayFillSnippets();
            jintFillSnippet = snippet(HotSpotArrayFillSnippets.class, "jintFillSnippet", null, receiver, LocationIdentity.any());
            jshortFillSnippet = snippet(HotSpotArrayFillSnippets.class, "jshortFillSnippet", null, receiver, LocationIdentity.any());
            jbyteFillSnippet = snippet(HotSpotArrayFillSnippets.class, "jbyteFillSnippet", null, receiver, LocationIdentity.any());
            arrayofJintFillSnippet = snippet(HotSpotArrayFillSnippets.class, "arrayofJintFillSnippet", null, receiver, LocationIdentity.any());
            arrayofJshortFillSnippet = snippet(HotSpotArrayFillSnippets.class, "arrayofJshortFillSnippet", null, receiver, LocationIdentity.any());
            arrayofJbyteFillSnippet = snippet(HotSpotArrayFillSnippets.class, "arrayofJbyteFillSnippet", null, receiver, LocationIdentity.any());
        }

        public void lower(ArrayFillNode fillNode, LoweringTool tool) {
            SnippetTemplate.SnippetInfo snippetInfo;
            final int arrayBaseOffset = getMetaAccess().getArrayBaseOffset(fillNode.getElementType());
            final int elementSize = tool.getMetaAccess().getArrayIndexScale(fillNode.getElementType());
            boolean aligned = false;
            if (fillNode.getDstOffset().isConstant()) {
                Constant ciConst = fillNode.getDst().asConstant();
                if (ciConst instanceof JavaConstant) {
                    int offsetConst = ((JavaConstant) ciConst).asInt();
                    final int heapWordSize = HotSpotReplacementsUtil.getHeapWordSize(INJECTED_VMCONFIG);
                    if (((offsetConst * elementSize + arrayBaseOffset) % heapWordSize) == 0) {
                        aligned = true;
                    }
                }
            }
            switch (fillNode.getElementType()) {
                case Byte:
                case Boolean:
                    snippetInfo = aligned ? arrayofJbyteFillSnippet : jbyteFillSnippet;
                    break;
                case Char:
                case Short:
                    snippetInfo = aligned ? arrayofJshortFillSnippet : jshortFillSnippet;
                    break;
                case Int:
                    snippetInfo = aligned ? arrayofJintFillSnippet : jintFillSnippet;
                    break;
                default:
                    throw new GraalError("no matched filling stubs");
            }
            final StructuredGraph graph = fillNode.graph();
            int shift = CodeUtil.log2(elementSize);
            ValueNode shiftNode = graph.unique(new LeftShiftNode(fillNode.getDstOffset(), ConstantNode.forInt(shift, graph)));
            ValueNode offset = graph.unique(new AddNode(shiftNode, ConstantNode.forInt(arrayBaseOffset, graph)));
            SnippetTemplate.Arguments args = new SnippetTemplate.Arguments(snippetInfo, graph.getGuardsStage(), tool.getLoweringStage());
            args.add("to", fillNode.getDst());
            args.add("offset", offset);
            args.add("value", fillNode.getValue());
            args.add("count", fillNode.getCount());
            args.addConst("elementKind", fillNode.getElementType());
            template(fillNode, args).instantiate(getMetaAccess(), fillNode, SnippetTemplate.DEFAULT_REPLACER, args);
        }
    }
}