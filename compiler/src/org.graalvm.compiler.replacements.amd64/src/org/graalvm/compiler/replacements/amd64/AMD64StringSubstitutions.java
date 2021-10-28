/*
 * Copyright (c) 2013, 2021, Oracle and/or its affiliates. All rights reserved.
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
import static org.graalvm.compiler.replacements.ReplacementsUtil.charArrayBaseOffset;
import static org.graalvm.compiler.replacements.ReplacementsUtil.charArrayIndexScale;

import org.graalvm.compiler.api.replacements.ClassSubstitution;
import org.graalvm.compiler.api.replacements.Fold.InjectedParameter;
import org.graalvm.compiler.api.replacements.MethodSubstitution;
import org.graalvm.compiler.graph.Node.ConstantNodeParameter;
import org.graalvm.compiler.replacements.ArrayIndexOf;
import org.graalvm.compiler.replacements.StringSubstitutions;
import org.graalvm.compiler.replacements.nodes.ArrayRegionEqualsNode;
import org.graalvm.compiler.word.Word;
import org.graalvm.word.Pointer;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;

// JaCoCo Exclude

/**
 * Substitutions for {@link java.lang.String} methods.
 */
@ClassSubstitution(String.class)
public class AMD64StringSubstitutions {

    /** Marker value for the {@link InjectedParameter} injected parameter. */
    static final MetaAccessProvider INJECTED = null;

    // Only exists in JDK <= 8
    @MethodSubstitution(isStatic = true, optional = true)
    public static int indexOf(char[] source, int sourceOffset, int sourceCount,
                    @ConstantNodeParameter char[] target, int targetOffset, int targetCount,
                    int origFromIndex) {
        int fromIndex = origFromIndex;
        if (injectBranchProbability(UNLIKELY_PROBABILITY, fromIndex >= sourceCount)) {
            return (targetCount == 0 ? sourceCount : -1);
        }
        if (injectBranchProbability(UNLIKELY_PROBABILITY, fromIndex < 0)) {
            fromIndex = 0;
        }
        if (injectBranchProbability(UNLIKELY_PROBABILITY, targetCount == 0)) {
            // The empty string is in every string.
            return fromIndex;
        }

        int totalOffset = sourceOffset + fromIndex;
        if (injectBranchProbability(UNLIKELY_PROBABILITY, sourceCount - fromIndex < targetCount)) {
            // The empty string contains nothing except the empty string.
            return -1;
        }

        if (injectBranchProbability(UNLIKELY_PROBABILITY, targetCount == 1)) {
            return ArrayIndexOf.indexOf1Char(source, sourceCount, totalOffset, target[targetOffset]);
        } else {
            int haystackLength = sourceCount - (targetCount - 2);
            while (injectBranchProbability(LIKELY_PROBABILITY, totalOffset < haystackLength)) {
                int indexOfResult = ArrayIndexOf.indexOfTwoConsecutiveChars(source, haystackLength, totalOffset, target[targetOffset], target[targetOffset + 1]);
                if (injectBranchProbability(UNLIKELY_PROBABILITY, indexOfResult < 0)) {
                    return -1;
                }
                totalOffset = indexOfResult;
                if (injectBranchProbability(UNLIKELY_PROBABILITY, targetCount == 2)) {
                    return totalOffset;
                } else {
                    Pointer cmpSourcePointer = Word.objectToTrackedPointer(source).add(charArrayBaseOffset(INJECTED)).add(totalOffset * charArrayIndexScale(INJECTED));
                    Pointer targetPointer = Word.objectToTrackedPointer(target).add(charArrayBaseOffset(INJECTED)).add(targetOffset * charArrayIndexScale(INJECTED));
                    if (injectBranchProbability(UNLIKELY_PROBABILITY, ArrayRegionEqualsNode.regionEquals(cmpSourcePointer, targetPointer, targetCount, JavaKind.Char))) {
                        return totalOffset;
                    }
                }
                totalOffset++;
            }
            return -1;
        }
    }

    // Only exists in JDK <= 8
    @MethodSubstitution(isStatic = false, optional = true)
    public static int indexOf(String source, int ch, int origFromIndex) {
        int fromIndex = origFromIndex;
        char[] sourceArray = StringSubstitutions.getValue(source);
        final int sourceCount = sourceArray.length;
        if (injectBranchProbability(UNLIKELY_PROBABILITY, fromIndex >= sourceCount)) {
            // Note: fromIndex might be near -1>>>1.
            return -1;
        }
        if (injectBranchProbability(UNLIKELY_PROBABILITY, fromIndex < 0)) {
            fromIndex = 0;
        }

        if (injectBranchProbability(LIKELY_PROBABILITY, ch < Character.MIN_SUPPLEMENTARY_CODE_POINT)) {
            return ArrayIndexOf.indexOf1Char(sourceArray, sourceCount, fromIndex, (char) ch);
        } else {
            return indexOf(source, ch, origFromIndex);
        }
    }
}
