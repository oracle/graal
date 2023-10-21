/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021, Arm Limited. All rights reserved.
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
package jdk.graal.compiler.replacements;

import static jdk.graal.compiler.api.directives.GraalDirectives.LIKELY_PROBABILITY;
import static jdk.graal.compiler.api.directives.GraalDirectives.UNLIKELY_PROBABILITY;
import static jdk.graal.compiler.api.directives.GraalDirectives.injectBranchProbability;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.unknownProbability;

import jdk.graal.compiler.api.replacements.Fold.InjectedParameter;
import jdk.graal.compiler.api.replacements.Snippet;
import jdk.graal.compiler.core.common.Stride;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool.ArrayIndexOfVariant;
import jdk.graal.compiler.nodes.extended.JavaReadNode;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.replacements.nodes.ArrayRegionEqualsNode;
import jdk.graal.compiler.replacements.nodes.ArrayIndexOfNode;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;

/**
 * Snippets for for {@code java.lang.StringUTF16} methods.
 */
public class StringUTF16Snippets implements Snippets {
    public static class Templates extends SnippetTemplate.AbstractTemplates {

        public final SnippetTemplate.SnippetInfo indexOfLatin1Unsafe;
        public final SnippetTemplate.SnippetInfo indexOfUnsafe;

        @SuppressWarnings("this-escape")
        public Templates(OptionValues options, Providers providers) {
            super(options, providers);

            this.indexOfLatin1Unsafe = snippet(providers, StringUTF16Snippets.class, "indexOfLatin1Unsafe");
            this.indexOfUnsafe = snippet(providers, StringUTF16Snippets.class, "indexOfUnsafe");
        }
    }

    /**
     * Marker value for the {@link InjectedParameter} injected parameter.
     */
    protected static final MetaAccessProvider INJECTED = null;

    private static int length(byte[] value) {
        return value.length >> 1;
    }

    private static long byteArrayCharOffset(long offset) {
        return ReplacementsUtil.byteArrayBaseOffset(INJECTED) + (offset * ReplacementsUtil.charArrayIndexScale(INJECTED));
    }

    /**
     * Will be intrinsified with an {@link InvocationPlugin} to a {@link JavaReadNode}.
     */
    private static native char getChar(byte[] value, int i);

    @Snippet
    public static int indexOfUnsafe(byte[] source, int sourceCount, byte[] target, int targetCount, int fromIndex) {
        ReplacementsUtil.dynamicAssert(fromIndex >= 0, "StringUTF16.indexOfUnsafe invalid args: fromIndex negative");
        ReplacementsUtil.dynamicAssert(targetCount > 0, "StringUTF16.indexOfUnsafe invalid args: targetCount <= 0");
        ReplacementsUtil.dynamicAssert(targetCount <= length(target), "StringUTF16.indexOfUnsafe invalid args: targetCount > length(target)");
        ReplacementsUtil.dynamicAssert(sourceCount >= targetCount, "StringUTF16.indexOfUnsafe invalid args: sourceCount < targetCount");
        if (unknownProbability(targetCount == 1)) {
            return ArrayIndexOfNode.optimizedArrayIndexOf(JavaKind.Byte, Stride.S2, ArrayIndexOfVariant.MatchAny, source, byteArrayCharOffset(0), sourceCount, fromIndex, getChar(target, 0));
        } else {
            int haystackLength = sourceCount - (targetCount - 2);
            int offset = fromIndex;
            while (injectBranchProbability(LIKELY_PROBABILITY, offset < haystackLength)) {
                int indexOfResult = ArrayIndexOfNode.optimizedArrayIndexOf(JavaKind.Byte, Stride.S2, ArrayIndexOfVariant.FindTwoConsecutive, source, byteArrayCharOffset(0), haystackLength, offset,
                                getChar(target, 0),
                                getChar(target, 1));
                if (injectBranchProbability(UNLIKELY_PROBABILITY, indexOfResult < 0)) {
                    return -1;
                }
                offset = indexOfResult;
                if (injectBranchProbability(UNLIKELY_PROBABILITY, targetCount == 2)) {
                    return offset;
                } else {
                    if (injectBranchProbability(UNLIKELY_PROBABILITY,
                                    ArrayRegionEqualsNode.regionEquals(source, byteArrayCharOffset(offset), target, byteArrayCharOffset(0), targetCount, JavaKind.Byte, Stride.S2, Stride.S2))) {
                        return offset;
                    }
                }
                offset++;
            }
            return -1;
        }
    }

    @Snippet
    public static int indexOfLatin1Unsafe(byte[] source, int sourceCount, byte[] target, int targetCount, int fromIndex) {
        ReplacementsUtil.dynamicAssert(fromIndex >= 0, "StringUTF16.indexOfLatin1Unsafe invalid args: fromIndex negative");
        ReplacementsUtil.dynamicAssert(targetCount > 0, "StringUTF16.indexOfLatin1Unsafe invalid args: targetCount <= 0");
        ReplacementsUtil.dynamicAssert(targetCount <= target.length, "StringUTF16.indexOfLatin1Unsafe invalid args: targetCount > length(target)");
        ReplacementsUtil.dynamicAssert(sourceCount >= targetCount, "StringUTF16.indexOfLatin1Unsafe invalid args: sourceCount < targetCount");
        if (unknownProbability(targetCount == 1)) {
            return ArrayIndexOfNode.optimizedArrayIndexOf(JavaKind.Byte, Stride.S2, ArrayIndexOfVariant.MatchAny, source, byteArrayCharOffset(0), sourceCount, fromIndex,
                            (char) Byte.toUnsignedInt(StringHelperIntrinsics.getByte(target, 0)));
        } else {
            int haystackLength = sourceCount - (targetCount - 2);
            int offset = fromIndex;
            if (injectBranchProbability(LIKELY_PROBABILITY, offset < haystackLength)) {
                char c1 = (char) Byte.toUnsignedInt(StringHelperIntrinsics.getByte(target, 0));
                char c2 = (char) Byte.toUnsignedInt(StringHelperIntrinsics.getByte(target, 1));
                do {
                    int indexOfResult = ArrayIndexOfNode.optimizedArrayIndexOf(JavaKind.Byte, Stride.S2, ArrayIndexOfVariant.FindTwoConsecutive, source, byteArrayCharOffset(0), haystackLength, offset,
                                    c1, c2);
                    if (injectBranchProbability(UNLIKELY_PROBABILITY, indexOfResult < 0)) {
                        return -1;
                    }
                    offset = indexOfResult;
                    if (injectBranchProbability(UNLIKELY_PROBABILITY, targetCount == 2)) {
                        return offset;
                    } else {
                        if (injectBranchProbability(UNLIKELY_PROBABILITY,
                                        ArrayRegionEqualsNode.regionEquals(source, byteArrayCharOffset(offset), target, byteArrayCharOffset(0), targetCount, JavaKind.Byte, Stride.S2, Stride.S1))) {
                            return offset;
                        }
                    }
                    offset++;
                } while (injectBranchProbability(LIKELY_PROBABILITY, offset < haystackLength));
            }
            return -1;
        }
    }
}
