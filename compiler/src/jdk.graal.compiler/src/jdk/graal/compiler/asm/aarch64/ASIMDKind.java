/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.asm.aarch64;

import jdk.graal.compiler.debug.GraalError;

import jdk.vm.ci.aarch64.AArch64Kind;

public class ASIMDKind {

    /**
     * Returns the smallest ASIMD kind that is able to hold a value of the specified base and vector
     * length.
     *
     * When a length is specified that results a total data size that is not an exact match for a
     * kind, the smallest kind that can hold the specified value will be returned. As an example, if
     * a base kind of {@code DWORD} and a length of 3 is specified the function will return
     * {@code V128_DWORD} since it is the smallest kind (4 x {@code DWORD}) that can hold the
     * requested data.
     *
     * Calling this function with a length that exceeds the largest supported register is an error.
     *
     * @param base the kind of each element of the vector
     * @param length the length of the vector
     * @return the kind representing the smallest vector register that can hold the requested data
     */
    public static AArch64Kind getASIMDKind(AArch64Kind base, int length) {
        AArch64Kind returnKind = null;
        int returnLength = Integer.MAX_VALUE;
        for (AArch64Kind kind : AArch64Kind.values()) {
            if (kind.getScalar() == base) {
                int kindLength = kind.getVectorLength();
                if (kindLength == length) {
                    return kind;
                } else if (kindLength > length && kindLength < returnLength) {
                    returnKind = kind;
                    returnLength = kindLength;
                }
            }
        }
        if (returnKind == null) {
            throw GraalError.shouldNotReachHere(String.format("unsupported vector kind: %d x %s", length, base)); // ExcludeFromJacocoGeneratedReport
        }
        return returnKind;
    }
}
