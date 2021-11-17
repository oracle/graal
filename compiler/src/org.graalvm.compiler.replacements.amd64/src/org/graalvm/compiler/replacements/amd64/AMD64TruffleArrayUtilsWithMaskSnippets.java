/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.replacements.amd64;

import static org.graalvm.compiler.api.directives.GraalDirectives.LIKELY_PROBABILITY;
import static org.graalvm.compiler.api.directives.GraalDirectives.UNLIKELY_PROBABILITY;
import static org.graalvm.compiler.api.directives.GraalDirectives.injectBranchProbability;

import org.graalvm.collections.UnmodifiableEconomicMap;
import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.AbstractMergeNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.LoopExitNode;
import org.graalvm.compiler.nodes.StateSplit;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.replacements.ReplacementsUtil;
import org.graalvm.compiler.replacements.SnippetTemplate;
import org.graalvm.compiler.replacements.SnippetTemplate.AbstractTemplates;
import org.graalvm.compiler.replacements.SnippetTemplate.Arguments;
import org.graalvm.compiler.replacements.SnippetTemplate.SnippetInfo;
import org.graalvm.compiler.replacements.Snippets;
import org.graalvm.compiler.replacements.nodes.TypePunnedArrayReadIntrinsic;

import jdk.vm.ci.meta.JavaKind;

public class AMD64TruffleArrayUtilsWithMaskSnippets implements Snippets {

    @Snippet
    public static int indexOfByte1WithMask(byte[] array, int arrayLength, int fromIndex, byte v0, byte mask) {
        ReplacementsUtil.dynamicAssert(fromIndex >= 0 && arrayLength >= 0, "illegal arguments");
        byte[] arrayP = GraalDirectives.guardingNonNull(array);
        for (int i = fromIndex; injectBranchProbability(LIKELY_PROBABILITY, i < arrayLength); i++) {
            byte b = (byte) (arrayP[i] | mask);
            if (injectBranchProbability(UNLIKELY_PROBABILITY, b == v0)) {
                return i;
            }
        }
        return -1;
    }

    @Snippet
    public static int indexOfChar1WithMask(char[] array, int arrayLength, int fromIndex, char v0, char mask) {
        ReplacementsUtil.dynamicAssert(fromIndex >= 0 && arrayLength >= 0, "illegal arguments");
        char[] arrayP = GraalDirectives.guardingNonNull(array);
        for (int i = fromIndex; injectBranchProbability(LIKELY_PROBABILITY, i < arrayLength); i++) {
            char c = (char) (arrayP[i] | mask);
            if (injectBranchProbability(UNLIKELY_PROBABILITY, c == v0)) {
                return i;
            }
        }
        return -1;
    }

    @Snippet
    public static int indexOfCharCompact1WithMask(byte[] array, int arrayLength, int fromIndex, char v0, char mask) {
        ReplacementsUtil.dynamicAssert(fromIndex >= 0 && arrayLength >= 0, "illegal arguments");
        byte[] arrayP = GraalDirectives.guardingNonNull(array);
        for (int i = fromIndex; injectBranchProbability(LIKELY_PROBABILITY, i < arrayLength); i++) {
            char element = (char) typePunnedRead(JavaKind.Byte, JavaKind.Char, JavaKind.Char, arrayP, i, arrayLength);
            char c = (char) (element | mask);
            if (injectBranchProbability(UNLIKELY_PROBABILITY, c == v0)) {
                return i;
            }
        }
        return -1;
    }

    @Snippet
    public static int indexOf2ConsecutiveByteWithMask(byte[] array, int arrayLength, int fromIndex, int v0, int mask) {
        ReplacementsUtil.dynamicAssert(fromIndex >= 0 && arrayLength >= 0, "illegal arguments");
        return indexOf2Consecutive(JavaKind.Byte, JavaKind.Byte, JavaKind.Char, GraalDirectives.guardingNonNull(array), arrayLength, fromIndex, v0, mask);
    }

    @Snippet
    public static int indexOf2ConsecutiveCharWithMask(char[] array, int arrayLength, int fromIndex, int v0, int mask) {
        ReplacementsUtil.dynamicAssert(fromIndex >= 0 && arrayLength >= 0, "illegal arguments");
        return indexOf2Consecutive(JavaKind.Char, JavaKind.Char, JavaKind.Int, GraalDirectives.guardingNonNull(array), arrayLength, fromIndex, v0, mask);
    }

    @Snippet
    public static int indexOf2ConsecutiveCharCompactWithMask(byte[] array, int arrayLength, int fromIndex, int v0, int mask) {
        ReplacementsUtil.dynamicAssert(fromIndex >= 0 && arrayLength >= 0, "illegal arguments");
        return indexOf2Consecutive(JavaKind.Byte, JavaKind.Char, JavaKind.Int, GraalDirectives.guardingNonNull(array), arrayLength, fromIndex, v0, mask);
    }

    private static int indexOf2Consecutive(JavaKind arrayKind, JavaKind valueKind, JavaKind cmpKind, Object array, int arrayLength, int fromIndex, int v0, int mask) {
        for (int i = fromIndex + 1; injectBranchProbability(LIKELY_PROBABILITY, i < arrayLength); i++) {
            long element = typePunnedRead(arrayKind, valueKind, cmpKind, array, i - 1, arrayLength);
            if (injectBranchProbability(UNLIKELY_PROBABILITY, ((element | mask)) == v0)) {
                return i - 1;
            }
        }
        return -1;
    }

    @Snippet
    public static boolean arrayRegionEqualsWithMaskByte(byte[] a1, int fromIndex1, byte[] a2, int fromIndex2, int length, byte[] mask) {
        ReplacementsUtil.dynamicAssert(fromIndex1 >= 0 && fromIndex2 >= 0 && length >= 0, "illegal arguments");
        return arrayRegionEqualsWithMask(JavaKind.Byte, JavaKind.Byte,
                        GraalDirectives.guardingNonNull(a1), fromIndex1, GraalDirectives.guardingNonNull(a2), fromIndex2, length, GraalDirectives.guardingNonNull(mask));
    }

    @Snippet
    public static boolean arrayRegionEqualsWithMaskChar(char[] a1, int fromIndex1, char[] a2, int fromIndex2, int length, char[] mask) {
        ReplacementsUtil.dynamicAssert(fromIndex1 >= 0 && fromIndex2 >= 0 && length >= 0, "illegal arguments");
        return arrayRegionEqualsWithMask(JavaKind.Char, JavaKind.Char,
                        GraalDirectives.guardingNonNull(a1), fromIndex1, GraalDirectives.guardingNonNull(a2), fromIndex2, length, GraalDirectives.guardingNonNull(mask));
    }

    private static boolean arrayRegionEqualsWithMask(JavaKind arrayKind, JavaKind valueKind, Object a1, int fromIndex1, Object a2, int fromIndex2, int length, Object mask) {
        final int stride = JavaKind.Long.getBitCount() / valueKind.getBitCount();
        if (injectBranchProbability(LIKELY_PROBABILITY, length >= stride)) {
            return arrayRegionEqualsTypePunnedLoop(arrayKind, valueKind, a1, fromIndex1, a2, fromIndex2, length, mask, stride);
        } else {
            int byteLength = length << Integer.numberOfTrailingZeros(valueKind.getByteCount());
            if (byteLength == 0) {
                return true;
            } else if (byteLength < 2) {
                return arrayRegionEqualsTypePunned(arrayKind, valueKind, JavaKind.Byte, a1, fromIndex1, a2, fromIndex2, length, mask);
            } else if (byteLength < 4) {
                return arrayRegionEqualsTypePunned(arrayKind, valueKind, JavaKind.Char, a1, fromIndex1, a2, fromIndex2, length, mask);
            } else {
                ReplacementsUtil.dynamicAssert(byteLength < 8, "regionEquals does not cover the given length");
                return arrayRegionEqualsTypePunned(arrayKind, valueKind, JavaKind.Int, a1, fromIndex1, a2, fromIndex2, length, mask);
            }
        }
    }

    private static boolean arrayRegionEqualsTypePunnedLoop(JavaKind arrayKind, JavaKind valueKind, Object a1, int fromIndex1, Object a2, int fromIndex2, int length, Object mask, final int stride) {
        int max = length - stride;
        for (int i = 0; injectBranchProbability(LIKELY_PROBABILITY, i < max); i += stride) {
            if (injectBranchProbability(UNLIKELY_PROBABILITY,
                            !arrayRegionEqualsTypePunnedCmp(arrayKind, valueKind, JavaKind.Long, a1, fromIndex1, a2, fromIndex2, length, mask, i, fromIndex1 + i, fromIndex2 + i))) {
                return false;
            }
        }
        return arrayRegionEqualsTypePunnedCmp(arrayKind, valueKind, JavaKind.Long, a1, fromIndex1, a2, fromIndex2, length, mask, max, fromIndex1 + max, fromIndex2 + max);
    }

    private static boolean arrayRegionEqualsTypePunned(JavaKind arrayKind, JavaKind valueKind, JavaKind cmpKind, Object a1, int fromIndex1, Object a2, int fromIndex2, int length, Object mask) {
        int tailOffset = length - (cmpKind.getByteCount() / valueKind.getByteCount());
        return arrayRegionEqualsTypePunnedCmp(arrayKind, valueKind, cmpKind, a1, fromIndex1, a2, fromIndex2, length, mask, 0, fromIndex1, fromIndex2) &&
                        arrayRegionEqualsTypePunnedCmp(arrayKind, valueKind, cmpKind, a1, fromIndex1, a2, fromIndex2, length, mask, tailOffset, fromIndex1 + tailOffset,
                                        fromIndex2 + tailOffset);
    }

    private static boolean arrayRegionEqualsTypePunnedCmp(JavaKind arrayKind, JavaKind valueKind, JavaKind cmpKind,
                    Object a1, int fromIndex1, Object a2, int fromIndex2, int length, Object mask, int iMsk, int iA1, int iA2) {
        long vMsk = typePunnedRead(arrayKind, valueKind, cmpKind, mask, iMsk, length);
        long vA1 = typePunnedRead(arrayKind, valueKind, cmpKind, a1, iA1, fromIndex1 + length);
        long vA2 = typePunnedRead(arrayKind, valueKind, cmpKind, a2, iA2, fromIndex2 + length);
        return (vA1 | vMsk) == vA2;
    }

    @Snippet
    public static boolean arrayRegionEqualsWithMaskCBB(byte[] a1, int fromIndex1, byte[] a2, int fromIndex2, int length, byte[] mask) {
        ReplacementsUtil.dynamicAssert(fromIndex1 >= 0 && fromIndex2 >= 0 && length >= 0, "illegal arguments");
        return arrayRegionEqualsDifferentKinds(JavaKind.Char, JavaKind.Byte, JavaKind.Byte,
                        GraalDirectives.guardingNonNull(a1), fromIndex1, GraalDirectives.guardingNonNull(a2), fromIndex2, length, GraalDirectives.guardingNonNull(mask));
    }

    @Snippet
    public static boolean arrayRegionEqualsWithMaskBCB(byte[] a1, int fromIndex1, byte[] a2, int fromIndex2, int length, byte[] mask) {
        ReplacementsUtil.dynamicAssert(fromIndex1 >= 0 && fromIndex2 >= 0 && length >= 0, "illegal arguments");
        return arrayRegionEqualsDifferentKinds(JavaKind.Byte, JavaKind.Char, JavaKind.Byte,
                        GraalDirectives.guardingNonNull(a1), fromIndex1, GraalDirectives.guardingNonNull(a2), fromIndex2, length, GraalDirectives.guardingNonNull(mask));
    }

    @Snippet
    public static boolean arrayRegionEqualsWithMaskCCB(byte[] a1, int fromIndex1, byte[] a2, int fromIndex2, int length, byte[] mask) {
        ReplacementsUtil.dynamicAssert(fromIndex1 >= 0 && fromIndex2 >= 0 && length >= 0, "illegal arguments");
        return arrayRegionEqualsDifferentKinds(JavaKind.Char, JavaKind.Char, JavaKind.Byte,
                        GraalDirectives.guardingNonNull(a1), fromIndex1, GraalDirectives.guardingNonNull(a2), fromIndex2, length, GraalDirectives.guardingNonNull(mask));
    }

    @Snippet
    public static boolean arrayRegionEqualsWithMaskBCC(byte[] a1, int fromIndex1, byte[] a2, int fromIndex2, int length, byte[] mask) {
        ReplacementsUtil.dynamicAssert(fromIndex1 >= 0 && fromIndex2 >= 0 && length >= 0, "illegal arguments");
        return arrayRegionEqualsDifferentKinds(JavaKind.Byte, JavaKind.Char, JavaKind.Char,
                        GraalDirectives.guardingNonNull(a1), fromIndex1, GraalDirectives.guardingNonNull(a2), fromIndex2, length, GraalDirectives.guardingNonNull(mask));
    }

    @Snippet
    public static boolean arrayRegionEqualsWithMaskCCC(byte[] a1, int fromIndex1, byte[] a2, int fromIndex2, int length, byte[] mask) {
        ReplacementsUtil.dynamicAssert(fromIndex1 >= 0 && fromIndex2 >= 0 && length >= 0, "illegal arguments");
        return arrayRegionEqualsWithMask(JavaKind.Byte, JavaKind.Char,
                        GraalDirectives.guardingNonNull(a1), fromIndex1, GraalDirectives.guardingNonNull(a2), fromIndex2, length, GraalDirectives.guardingNonNull(mask));
    }

    private static boolean arrayRegionEqualsDifferentKinds(JavaKind valueKind1, JavaKind valueKind2, JavaKind valueKindMask, byte[] a1, int fromIndex1, byte[] a2, int fromIndex2, int length,
                    byte[] mask) {
        for (int i = 0; injectBranchProbability(LIKELY_PROBABILITY, i < length); i++) {
            char c1 = readChar(valueKind1, a1, fromIndex1 + i);
            if (injectBranchProbability(UNLIKELY_PROBABILITY, ((c1 | readChar(valueKindMask, mask, i))) != readChar(valueKind2, a2, fromIndex2 + i))) {
                return false;
            }
        }
        return true;
    }

    private static char readChar(JavaKind valueKind, byte[] array, int i) {
        if (valueKind == JavaKind.Byte) {
            return (char) Byte.toUnsignedInt(array[i]);
        }
        ReplacementsUtil.dynamicAssert(valueKind == JavaKind.Char, "unexpected valueKind");
        return (char) typePunnedRead(JavaKind.Byte, JavaKind.Byte, valueKind, array, i << 1, array.length);
    }

    private static long typePunnedRead(JavaKind arrayKind, JavaKind valueKind, JavaKind cmpKind, Object array, long index, int arrayLength) {
        ReplacementsUtil.dynamicAssert(index >= 0 && index + (cmpKind.getByteCount() / valueKind.getByteCount()) <= arrayLength, "type punned read is out of bounds!");
        return cmpKind == JavaKind.Long ? TypePunnedArrayReadIntrinsic.ReadLong.read(arrayKind, valueKind, array, index)
                        : TypePunnedArrayReadIntrinsic.read(arrayKind, valueKind, cmpKind, array, index);
    }

    public static class Templates extends AbstractTemplates {

        private final SnippetInfo[] indexOfWithMaskSnippets;
        private final SnippetInfo[] regionEqualsSnippets;

        public Templates(OptionValues options, Providers providers) {
            super(options, providers);

            indexOfWithMaskSnippets = new SnippetInfo[6];
            indexOfWithMaskSnippets[0] = snippet(AMD64TruffleArrayUtilsWithMaskSnippets.class, "indexOfByte1WithMask");
            indexOfWithMaskSnippets[1] = snippet(AMD64TruffleArrayUtilsWithMaskSnippets.class, "indexOf2ConsecutiveByteWithMask");
            indexOfWithMaskSnippets[2] = snippet(AMD64TruffleArrayUtilsWithMaskSnippets.class, "indexOfCharCompact1WithMask");
            indexOfWithMaskSnippets[3] = snippet(AMD64TruffleArrayUtilsWithMaskSnippets.class, "indexOf2ConsecutiveCharCompactWithMask");
            indexOfWithMaskSnippets[4] = snippet(AMD64TruffleArrayUtilsWithMaskSnippets.class, "indexOfChar1WithMask");
            indexOfWithMaskSnippets[5] = snippet(AMD64TruffleArrayUtilsWithMaskSnippets.class, "indexOf2ConsecutiveCharWithMask");

            regionEqualsSnippets = new SnippetInfo[8];
            regionEqualsSnippets[0] = snippet(AMD64TruffleArrayUtilsWithMaskSnippets.class, "arrayRegionEqualsWithMaskByte");
            regionEqualsSnippets[1] = snippet(AMD64TruffleArrayUtilsWithMaskSnippets.class, "arrayRegionEqualsWithMaskChar");
            regionEqualsSnippets[2] = snippet(AMD64TruffleArrayUtilsWithMaskSnippets.class, "arrayRegionEqualsWithMaskBCB");
            regionEqualsSnippets[3] = snippet(AMD64TruffleArrayUtilsWithMaskSnippets.class, "arrayRegionEqualsWithMaskCCB");
            regionEqualsSnippets[4] = regionEqualsSnippets[0];
            regionEqualsSnippets[5] = snippet(AMD64TruffleArrayUtilsWithMaskSnippets.class, "arrayRegionEqualsWithMaskCBB");
            regionEqualsSnippets[6] = snippet(AMD64TruffleArrayUtilsWithMaskSnippets.class, "arrayRegionEqualsWithMaskBCC");
            regionEqualsSnippets[7] = snippet(AMD64TruffleArrayUtilsWithMaskSnippets.class, "arrayRegionEqualsWithMaskCCC");
        }

        private static final LoweringTool.StandardLoweringStage LOWERING_STAGE = LoweringTool.StandardLoweringStage.HIGH_TIER;

        public void lower(AMD64ArrayIndexOfWithMaskNode write) {
            SnippetInfo snippet = indexOfWithMaskSnippets[(write.getArrayKind().equals(JavaKind.Byte) ? (write.getValueKind().equals(JavaKind.Byte) ? 0 : 2) : 4) +
                            (write.isFindTwoConsecutive() ? 1 : 0)];
            Arguments args = new Arguments(snippet, write.graph().getGuardsStage(), LOWERING_STAGE);
            args.add("array", write.getArrayPointer());
            args.add("arrayLength", write.getArrayLength());
            args.add("fromIndex", write.getFromIndex());
            args.add("v0", write.getSearchValue());
            args.add("mask", write.getMask());
            instantiate(args, write);
        }

        public void lower(AMD64ArrayRegionEqualsWithMaskNode write) {
            SnippetInfo snippet = regionEqualsSnippets[write.sameKinds() ? kind2IndexB(write.getArrayKind(), 1)
                            : 2 + kind2IndexB(write.getKindMask(), 4) + kind2IndexC(write.getKind2(), 2) + kind2IndexB(write.getKind1(), 1)];
            Arguments args = new Arguments(snippet, write.graph().getGuardsStage(), LOWERING_STAGE);
            args.add("a1", write.getArray1());
            args.add("fromIndex1", write.getFromIndex1());
            args.add("a2", write.getArray2());
            args.add("fromIndex2", write.getFromIndex2());
            args.add("length", write.getLength());
            args.add("mask", write.getArrayMask());
            instantiate(args, write);
        }

        private void instantiate(Arguments args, FixedWithNextNode arrayOperation) {
            StructuredGraph graph = arrayOperation.graph();
            SnippetTemplate template = template(arrayOperation, args);
            FrameState beforeState = SnippetTemplate.findLastFrameState(arrayOperation);
            UnmodifiableEconomicMap<Node, Node> duplicates = template.instantiate(providers.getMetaAccess(), arrayOperation, SnippetTemplate.DEFAULT_REPLACER, args, false);
            for (Node originalNode : duplicates.getKeys()) {
                if (originalNode instanceof AbstractMergeNode || originalNode instanceof LoopExitNode) {
                    StateSplit s = (StateSplit) duplicates.get(originalNode);
                    assert s.asNode().graph() == graph;
                    if (s.stateAfter() == null) {
                        s.setStateAfter(beforeState.duplicateWithVirtualState());
                    }
                }
            }
            GraphUtil.killCFG(arrayOperation);
        }

        private static int kind2IndexB(JavaKind kind, int i) {
            return kind.equals(JavaKind.Byte) ? 0 : i;
        }

        private static int kind2IndexC(JavaKind kind, int i) {
            return kind.equals(JavaKind.Char) ? 0 : i;
        }
    }
}
