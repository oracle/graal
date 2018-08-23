/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.api.replacements.ClassSubstitution;
import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.api.replacements.Fold.InjectedParameter;
import org.graalvm.compiler.api.replacements.MethodSubstitution;
import org.graalvm.compiler.core.common.spi.ArrayOffsetProvider;
import org.graalvm.compiler.replacements.nodes.ArrayCompareToNode;
import org.graalvm.compiler.word.Word;
import org.graalvm.word.Pointer;

import jdk.vm.ci.meta.JavaKind;

// JaCoCo Exclude

/**
 * Substitutions for {@code java.lang.StringUTF16} methods.
 *
 * Since JDK 9.
 */
@ClassSubstitution(className = "java.lang.StringUTF16", optional = true)
public class AMD64StringUTF16Substitutions {

    /**
     * @param value is char[]
     * @param other is char[]
     */
    @MethodSubstitution
    public static int compareTo(byte[] value, byte[] other) {
        return ArrayCompareToNode.compareTo(value, other, value.length, other.length, JavaKind.Char, JavaKind.Char);
    }

    /**
     * @param value is char[]
     * @param other is byte[]
     */
    @MethodSubstitution
    public static int compareToLatin1(byte[] value, byte[] other) {
        /*
         * Swapping array arguments because intrinsic expects order to be byte[]/char[] but kind
         * arguments stay in original order.
         */
        return ArrayCompareToNode.compareTo(other, value, other.length, value.length, JavaKind.Char, JavaKind.Byte);
    }

    @MethodSubstitution(optional = true)
    public static int indexOfCharUnsafe(byte[] value, int ch, int fromIndex, int max) {
        Pointer sourcePointer = Word.objectToTrackedPointer(value).add(byteArrayBaseOffset(INJECTED)).add(fromIndex * charArrayIndexScale(INJECTED));
        int result = AMD64ArrayIndexOfNode.optimizedArrayIndexOf(sourcePointer, max - fromIndex, (char) ch, JavaKind.Char);
        if (result != -1) {
            return result + fromIndex;
        }
        return result;
    }

    // @formatter:off

    /* java.lang.StringUTF16.compress([CI[BII)I
     *
     * @HotSpotIntrinsicCandidate
     * public static int compress(char[] src, int src_indx, byte[] dst, int dst_indx, int len)
     */
    @MethodSubstitution
    public static int compress(char[] src, int sndx, byte[] dst, int dndx, int len) {
        int ndx1 = Math.max(0, sndx);
        int ndx2 = Math.max(0, dndx);

        assert ndx1 + len < src.length;
        assert ndx2 + len < dst.length;

        // Checkstyle: stop
        Pointer srcptr = Word.objectToTrackedPointer(src)
                             .add(charArrayBaseOffset(INJECTED))
                             .add(ndx1 * charArrayIndexScale(INJECTED));
        Pointer dstptr = Word.objectToTrackedPointer(dst)
                             .add(byteArrayBaseOffset(INJECTED))
                             .add(ndx2 * byteArrayIndexScale(INJECTED));
        // Checkstyle: resume
        return AMD64StringUTF16CompressNode.compress(srcptr, dstptr, len);
    }

    // @formatter:on

    @Fold
    protected static int charArrayBaseOffset(@InjectedParameter ArrayOffsetProvider aop) {
        return aop.arrayBaseOffset(JavaKind.Char);
    }

    @Fold
    protected static int charArrayIndexScale(@InjectedParameter ArrayOffsetProvider aop) {
        return aop.arrayScalingFactor(JavaKind.Char);
    }

    @Fold
    protected static int byteArrayBaseOffset(@InjectedParameter ArrayOffsetProvider aop) {
        return aop.arrayBaseOffset(JavaKind.Byte);
    }

    @Fold
    protected static int byteArrayIndexScale(@InjectedParameter ArrayOffsetProvider aop) {
        return aop.arrayScalingFactor(JavaKind.Byte);
    }

    /**
     * Marker value for the {@link InjectedParameter} injected parameter.
     */
    private static final ArrayOffsetProvider INJECTED = null;

}
