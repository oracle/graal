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
import static org.graalvm.compiler.replacements.JDK9StringSubstitutions.getByte;
import static org.graalvm.compiler.replacements.ReplacementsUtil.byteArrayBaseOffset;
import static org.graalvm.compiler.replacements.ReplacementsUtil.charArrayIndexScale;

import org.graalvm.compiler.api.replacements.ClassSubstitution;
import org.graalvm.compiler.api.replacements.Fold.InjectedParameter;
import org.graalvm.compiler.api.replacements.MethodSubstitution;
import org.graalvm.compiler.nodes.extended.JavaReadNode;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.replacements.nodes.ArrayRegionEqualsNode;
import org.graalvm.compiler.word.Word;
import org.graalvm.word.Pointer;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;

/**
 * Substitutions for {@code java.lang.StringUTF16} methods.
 *
 * Since JDK 9.
 */
@ClassSubstitution(className = "java.lang.StringUTF16", optional = true)
public class StringUTF16Substitutions {
    /**
     * Marker value for the {@link InjectedParameter} injected parameter.
     */
    protected static final MetaAccessProvider INJECTED = null;

    private static int length(byte[] value) {
        return value.length >> 1;
    }

    private static Word pointer(byte[] target) {
        return Word.objectToTrackedPointer(target).add(byteArrayBaseOffset(INJECTED));
    }

    private static Word charOffsetPointer(byte[] value, int offset) {
        return pointer(value).add(offset * charArrayIndexScale(INJECTED));
    }

    /**
     * Will be intrinsified with an {@link InvocationPlugin} to a {@link JavaReadNode}.
     */
    private static native char getChar(byte[] value, int i);

    @MethodSubstitution
    public static int indexOfUnsafe(byte[] source, int sourceCount, byte[] target, int targetCount, int fromIndex) {
        ReplacementsUtil.dynamicAssert(fromIndex >= 0, "StringUTF16.indexOfUnsafe invalid args: fromIndex negative");
        ReplacementsUtil.dynamicAssert(targetCount > 0, "StringUTF16.indexOfUnsafe invalid args: targetCount <= 0");
        ReplacementsUtil.dynamicAssert(targetCount <= length(target), "StringUTF16.indexOfUnsafe invalid args: targetCount > length(target)");
        ReplacementsUtil.dynamicAssert(sourceCount >= targetCount, "StringUTF16.indexOfUnsafe invalid args: sourceCount < targetCount");
        if (targetCount == 1) {
            return ArrayIndexOf.indexOf1CharCompact(source, sourceCount, fromIndex, getChar(target, 0));
        } else {
            int haystackLength = sourceCount - (targetCount - 2);
            int offset = fromIndex;
            while (injectBranchProbability(LIKELY_PROBABILITY, offset < haystackLength)) {
                int indexOfResult = ArrayIndexOf.indexOfTwoConsecutiveChars(source, haystackLength, offset, getChar(target, 0),
                                getChar(target, 1));
                if (injectBranchProbability(UNLIKELY_PROBABILITY, indexOfResult < 0)) {
                    return -1;
                }
                offset = indexOfResult;
                if (injectBranchProbability(UNLIKELY_PROBABILITY, targetCount == 2)) {
                    return offset;
                } else {
                    Pointer cmpSourcePointer = charOffsetPointer(source, offset);
                    Pointer targetPointer = pointer(target);
                    if (injectBranchProbability(UNLIKELY_PROBABILITY, ArrayRegionEqualsNode.regionEquals(cmpSourcePointer, targetPointer, targetCount, JavaKind.Char))) {
                        return offset;
                    }
                }
                offset++;
            }
            return -1;
        }
    }

    @MethodSubstitution
    public static int indexOfLatin1Unsafe(byte[] source, int sourceCount, byte[] target, int targetCount, int fromIndex) {
        ReplacementsUtil.dynamicAssert(fromIndex >= 0, "StringUTF16.indexOfLatin1Unsafe invalid args: fromIndex negative");
        ReplacementsUtil.dynamicAssert(targetCount > 0, "StringUTF16.indexOfLatin1Unsafe invalid args: targetCount <= 0");
        ReplacementsUtil.dynamicAssert(targetCount <= target.length, "StringUTF16.indexOfLatin1Unsafe invalid args: targetCount > length(target)");
        ReplacementsUtil.dynamicAssert(sourceCount >= targetCount, "StringUTF16.indexOfLatin1Unsafe invalid args: sourceCount < targetCount");
        if (targetCount == 1) {
            return ArrayIndexOf.indexOf1CharCompact(source, sourceCount, fromIndex, (char) Byte.toUnsignedInt(getByte(target, 0)));
        } else {
            int haystackLength = sourceCount - (targetCount - 2);
            int offset = fromIndex;
            if (injectBranchProbability(LIKELY_PROBABILITY, offset < haystackLength)) {
                char c1 = (char) Byte.toUnsignedInt(getByte(target, 0));
                char c2 = (char) Byte.toUnsignedInt(getByte(target, 1));
                do {
                    int indexOfResult = ArrayIndexOf.indexOfTwoConsecutiveChars(source, haystackLength, offset, c1, c2);
                    if (injectBranchProbability(UNLIKELY_PROBABILITY, indexOfResult < 0)) {
                        return -1;
                    }
                    offset = indexOfResult;
                    if (injectBranchProbability(UNLIKELY_PROBABILITY, targetCount == 2)) {
                        return offset;
                    } else {
                        Pointer cmpSourcePointer = charOffsetPointer(source, offset);
                        Pointer targetPointer = pointer(target);
                        if (injectBranchProbability(UNLIKELY_PROBABILITY, ArrayRegionEqualsNode.regionEquals(cmpSourcePointer, targetPointer, targetCount, JavaKind.Char, JavaKind.Byte))) {
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
