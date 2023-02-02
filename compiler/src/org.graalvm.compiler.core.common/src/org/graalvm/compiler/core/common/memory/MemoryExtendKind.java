/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.core.common.memory;

import org.graalvm.compiler.core.common.type.ArithmeticOpTable;
import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.debug.GraalError;

/**
 * Enumerates the different extend kinds which may be able to be folded into an
 * ExtendableMemoryAccess. Note it is always assumed that the {@link #extendedSize} will always be
 * at least as large as the original memory access.
 */
public enum MemoryExtendKind {
    /** Indicates that no extension is currently associated with the memory operation. */
    DEFAULT(false, false, 0),
    ZERO_16(true, false, 16),
    ZERO_32(true, false, 32),
    ZERO_64(true, false, 64),
    SIGN_16(false, true, 16),
    SIGN_32(false, true, 32),
    SIGN_64(false, true, 64);

    private final boolean zeroExtend;
    private final boolean signExtend;
    private final int extendedSize;

    MemoryExtendKind(boolean zeroExtend, boolean signExtend, int extendedSize) {
        this.zeroExtend = zeroExtend;
        this.signExtend = signExtend;
        this.extendedSize = extendedSize;
    }

    public boolean isNotExtended() {
        return !zeroExtend && !signExtend;
    }

    public boolean isExtended() {
        return zeroExtend || signExtend;
    }

    public boolean isZeroExtend() {
        return zeroExtend;
    }

    public boolean isSignExtend() {
        return zeroExtend;
    }

    public int getExtendedBitSize() {
        return extendedSize;
    }

    /**
     * @return a stamp based on the new extended size of the access.
     */
    public Stamp stampFor(IntegerStamp original) {
        assert isExtended();
        int inputBits = original.getBits();
        int resultBits = getExtendedBitSize();
        assert inputBits <= resultBits;
        if (isZeroExtend()) {
            return ArithmeticOpTable.forStamp(original).getZeroExtend().foldStamp(inputBits, resultBits, original);
        } else {
            return ArithmeticOpTable.forStamp(original).getSignExtend().foldStamp(inputBits, resultBits, original);
        }
    }

    public static MemoryExtendKind getZeroExtendKind(int extendSize) {
        switch (extendSize) {
            case 16:
                return MemoryExtendKind.ZERO_16;
            case 32:
                return MemoryExtendKind.ZERO_32;
            case 64:
                return MemoryExtendKind.ZERO_64;
            default:
                throw GraalError.shouldNotReachHere();
        }
    }

    public static MemoryExtendKind getSignExtendKind(int extendSize) {
        switch (extendSize) {
            case 16:
                return MemoryExtendKind.SIGN_16;
            case 32:
                return MemoryExtendKind.SIGN_32;
            case 64:
                return MemoryExtendKind.SIGN_64;
            default:
                throw GraalError.shouldNotReachHere();
        }
    }
}
