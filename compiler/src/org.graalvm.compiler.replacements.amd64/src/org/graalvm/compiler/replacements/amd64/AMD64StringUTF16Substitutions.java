/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.api.directives.GraalDirectives.SLOWPATH_PROBABILITY;
import static org.graalvm.compiler.api.directives.GraalDirectives.injectBranchProbability;
import static org.graalvm.compiler.replacements.ReplacementsUtil.byteArrayBaseOffset;
import static org.graalvm.compiler.replacements.ReplacementsUtil.byteArrayIndexScale;
import static org.graalvm.compiler.replacements.ReplacementsUtil.charArrayBaseOffset;
import static org.graalvm.compiler.replacements.ReplacementsUtil.charArrayIndexScale;

import org.graalvm.compiler.api.replacements.ClassSubstitution;
import org.graalvm.compiler.api.replacements.MethodSubstitution;
import org.graalvm.compiler.nodes.DeoptimizeNode;
import org.graalvm.compiler.replacements.StringUTF16Substitutions;
import org.graalvm.compiler.word.Word;
import org.graalvm.word.Pointer;

import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaKind;

// JaCoCo Exclude

/**
 * Substitutions for {@code java.lang.StringUTF16} methods.
 * <p>
 * Since JDK 9.
 */
@ClassSubstitution(className = "java.lang.StringUTF16", optional = true)
public class AMD64StringUTF16Substitutions extends StringUTF16Substitutions {
    /**
     * Intrinsic for {@code java.lang.StringUTF16.compress([CI[BII)I}.
     *
     * <pre>
     * &#64;IntrinsicCandidate
     * public static int compress(char[] src, int src_indx, byte[] dst, int dst_indx, int len)
     * </pre>
     */
    @MethodSubstitution
    public static int compress(char[] src, int srcIndex, byte[] dest, int destIndex, int len) {
        checkLimits(src.length, srcIndex, dest.length, destIndex, len);

        Pointer srcPointer = Word.objectToTrackedPointer(src).add(charArrayBaseOffset(INJECTED)).add(srcIndex * charArrayIndexScale(INJECTED));
        Pointer destPointer = Word.objectToTrackedPointer(dest).add(byteArrayBaseOffset(INJECTED)).add(destIndex * byteArrayIndexScale(INJECTED));
        return AMD64StringUTF16CompressNode.compress(srcPointer, destPointer, len, JavaKind.Char);
    }

    /**
     * Intrinsic for {@code }java.lang.StringUTF16.compress([BI[BII)I}.
     *
     * <pre>
     * &#64;IntrinsicCandidate
     * public static int compress(byte[] src, int src_indx, byte[] dst, int dst_indx, int len)
     * </pre>
     * <p>
     * In this variant {@code dest} refers to a byte array containing 2 byte per char so
     * {@code srcIndex} and {@code len} are in terms of char elements and have to be scaled by 2
     * when referring to {@code src}.
     */
    @MethodSubstitution
    public static int compress(byte[] src, int srcIndex, byte[] dest, int destIndex, int len) {
        checkLimits(src.length >> 1, srcIndex, dest.length, destIndex, len);

        Pointer srcPointer = Word.objectToTrackedPointer(src).add(byteArrayBaseOffset(INJECTED)).add(srcIndex * 2 * byteArrayIndexScale(INJECTED));
        Pointer destPointer = Word.objectToTrackedPointer(dest).add(byteArrayBaseOffset(INJECTED)).add(destIndex * byteArrayIndexScale(INJECTED));
        return AMD64StringUTF16CompressNode.compress(srcPointer, destPointer, len, JavaKind.Byte);
    }

    private static void checkLimits(int srcLen, int srcIndex, int destLen, int destIndex, int len) {
        if (injectBranchProbability(SLOWPATH_PROBABILITY, len < 0) ||
                        injectBranchProbability(SLOWPATH_PROBABILITY, srcIndex < 0) ||
                        injectBranchProbability(SLOWPATH_PROBABILITY, srcIndex + len > srcLen) ||
                        injectBranchProbability(SLOWPATH_PROBABILITY, destIndex < 0) ||
                        injectBranchProbability(SLOWPATH_PROBABILITY, destIndex + len > destLen)) {
            DeoptimizeNode.deopt(DeoptimizationAction.None, DeoptimizationReason.BoundsCheckException);
        }
    }
}
