/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.arrayStart;

import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.compiler.api.replacements.ClassSubstitution;
import org.graalvm.compiler.api.replacements.MethodSubstitution;
import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.hotspot.HotSpotBackend;
import org.graalvm.compiler.word.Word;

@ClassSubstitution(className = "java.math.BigInteger", optional = true)
public class BigIntegerSubstitutions {

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

    @MethodSubstitution(isStatic = false)
    static int[] multiplyToLen(@SuppressWarnings("unused") Object receiver, int[] x, int xlen, int[] y, int ylen, int[] zIn) {
        return multiplyToLenStatic(x, xlen, y, ylen, zIn);
    }

    @MethodSubstitution(isStatic = true)
    static int[] multiplyToLenStatic(int[] x, int xlen, int[] y, int ylen, int[] zIn) {
        int[] zResult = zIn;
        int zLen;
        if (zResult == null || zResult.length < (xlen + ylen)) {
            zLen = xlen + ylen;
            zResult = new int[xlen + ylen];
        } else {
            zLen = zIn.length;
        }
        multiplyToLen(arrayStart(x), xlen, arrayStart(y), ylen, arrayStart(zResult), zLen);
        return zResult;
    }

    @MethodSubstitution(isStatic = true)
    static int mulAdd(int[] out, int[] in, int offset, int len, int k) {
        int[] outNonNull = GraalDirectives.guardingNonNull(out);
        int newOffset = outNonNull.length - offset;
        return mulAdd(arrayStart(outNonNull), arrayStart(in), newOffset, len, k);
    }

    @MethodSubstitution(isStatic = true)
    static int implMulAdd(int[] out, int[] in, int offset, int len, int k) {
        int[] outNonNull = GraalDirectives.guardingNonNull(out);
        int newOffset = outNonNull.length - offset;
        return mulAdd(arrayStart(outNonNull), arrayStart(in), newOffset, len, k);
    }

    @MethodSubstitution(isStatic = true)
    static int[] implMontgomeryMultiply(int[] a, int[] b, int[] n, int len, long inv, int[] product) {
        montgomeryMultiply(arrayStart(a), arrayStart(b), arrayStart(n), len, inv, arrayStart(product));
        return product;
    }

    @MethodSubstitution(isStatic = true)
    static int[] implMontgomerySquare(int[] a, int[] n, int len, long inv, int[] product) {
        montgomerySquare(arrayStart(a), arrayStart(n), len, inv, arrayStart(product));
        return product;
    }

    @MethodSubstitution(isStatic = true)
    static int[] implSquareToLen(int[] x, int len, int[] z, int zLen) {
        squareToLen(arrayStart(x), len, arrayStart(z), zLen);
        return z;
    }

}
