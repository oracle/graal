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
package org.graalvm.compiler.replacements;

import static org.graalvm.compiler.api.directives.GraalDirectives.LIKELY_PROBABILITY;
import static org.graalvm.compiler.api.directives.GraalDirectives.UNLIKELY_PROBABILITY;
import static org.graalvm.compiler.api.directives.GraalDirectives.injectBranchProbability;
import static org.graalvm.compiler.replacements.ReplacementsUtil.byteArrayBaseOffset;
import static org.graalvm.compiler.replacements.ReplacementsUtil.byteArrayIndexScale;
import static org.graalvm.compiler.replacements.StringHelperIntrinsics.getByte;

import org.graalvm.compiler.api.replacements.Fold.InjectedParameter;
import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.core.common.Stride;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.replacements.nodes.ArrayIndexOfNode;
import org.graalvm.compiler.replacements.nodes.ArrayRegionEqualsNode;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;

/**
 * Substitutions for {@code java.lang.StringLatin1} methods.
 */
public class StringLatin1Snippets implements Snippets {

    public static class Templates extends SnippetTemplate.AbstractTemplates {

        public final SnippetTemplate.SnippetInfo indexOf;

        public Templates(OptionValues options, Providers providers) {
            super(options, providers);

            this.indexOf = snippet(providers, StringLatin1Snippets.class, "indexOf");
        }
    }

    /** Marker value for the {@link InjectedParameter} injected parameter. */
    public static final MetaAccessProvider INJECTED = null;

    private static long byteArrayOffset(long offset) {
        return byteArrayBaseOffset(INJECTED) + (offset * byteArrayIndexScale(INJECTED));
    }

    @Snippet
    public static int indexOf(byte[] source, int sourceCount, byte[] target, int targetCount, int fromIndex) {
        ReplacementsUtil.dynamicAssert(fromIndex >= 0, "StringLatin1.indexOf invalid args: fromIndex negative");
        ReplacementsUtil.dynamicAssert(targetCount > 0, "StringLatin1.indexOf invalid args: targetCount <= 0");
        if (injectBranchProbability(UNLIKELY_PROBABILITY, sourceCount - fromIndex < targetCount)) {
            // too few characters to be searched to possibly match target
            return -1;
        }
        if (injectBranchProbability(UNLIKELY_PROBABILITY, targetCount == 1)) {
            return ArrayIndexOfNode.optimizedArrayIndexOf(JavaKind.Byte, Stride.S1, false, false, source, byteArrayOffset(0), sourceCount, fromIndex, Byte.toUnsignedInt(getByte(target, 0)));
        } else {
            int haystackLength = sourceCount - (targetCount - 2);
            int offset = fromIndex;
            if (injectBranchProbability(LIKELY_PROBABILITY, offset < haystackLength)) {
                byte b1 = getByte(target, 0);
                byte b2 = getByte(target, 1);
                do {
                    int indexOfResult = ArrayIndexOfNode.optimizedArrayIndexOf(JavaKind.Byte, Stride.S1, true, false, source, byteArrayOffset(0), haystackLength, offset, Byte.toUnsignedInt(b1),
                                    Byte.toUnsignedInt(b2));
                    if (injectBranchProbability(UNLIKELY_PROBABILITY, indexOfResult < 0)) {
                        return -1;
                    }
                    offset = indexOfResult;
                    if (injectBranchProbability(UNLIKELY_PROBABILITY, targetCount == 2)) {
                        return offset;
                    } else {
                        if (injectBranchProbability(UNLIKELY_PROBABILITY,
                                        ArrayRegionEqualsNode.regionEquals(source, byteArrayOffset(offset), target, byteArrayOffset(0), targetCount, JavaKind.Byte, Stride.S1, Stride.S1))) {
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
