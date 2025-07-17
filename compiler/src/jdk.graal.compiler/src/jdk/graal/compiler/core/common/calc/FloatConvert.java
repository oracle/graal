/*
 * Copyright (c) 2014, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core.common.calc;

import jdk.graal.compiler.core.common.NumUtil.Signedness;
import jdk.graal.compiler.core.common.type.FloatStamp;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.PrimitiveStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.debug.GraalError;

public enum FloatConvert {
    F2I(FloatConvertCategory.FloatingPointToInteger, 32),
    D2I(FloatConvertCategory.FloatingPointToInteger, 64),
    F2L(FloatConvertCategory.FloatingPointToInteger, 32),
    D2L(FloatConvertCategory.FloatingPointToInteger, 64),
    I2F(FloatConvertCategory.IntegerToFloatingPoint, 32),
    L2F(FloatConvertCategory.IntegerToFloatingPoint, 64),
    D2F(FloatConvertCategory.FloatingPointToFloatingPoint, 64),
    I2D(FloatConvertCategory.IntegerToFloatingPoint, 32),
    L2D(FloatConvertCategory.IntegerToFloatingPoint, 64),
    F2D(FloatConvertCategory.FloatingPointToFloatingPoint, 32),

    // to or from unsigned integer
    F2UI(FloatConvertCategory.FloatingPointToInteger, 32),
    D2UI(FloatConvertCategory.FloatingPointToInteger, 64),
    F2UL(FloatConvertCategory.FloatingPointToInteger, 32),
    D2UL(FloatConvertCategory.FloatingPointToInteger, 64),
    UI2F(FloatConvertCategory.IntegerToFloatingPoint, 32),
    UL2F(FloatConvertCategory.IntegerToFloatingPoint, 64),
    UI2D(FloatConvertCategory.IntegerToFloatingPoint, 32),
    UL2D(FloatConvertCategory.IntegerToFloatingPoint, 64);

    private final FloatConvertCategory category;
    private final int inputBits;

    FloatConvert(FloatConvertCategory category, int inputBits) {
        this.category = category;
        this.inputBits = inputBits;
    }

    public FloatConvertCategory getCategory() {
        return category;
    }

    public FloatConvert reverse() {
        return switch (this) {
            case D2F -> F2D;
            case D2I -> I2D;
            case D2L -> L2D;
            case F2D -> D2F;
            case F2I -> I2F;
            case F2L -> L2F;
            case I2D -> D2I;
            case I2F -> F2I;
            case L2D -> D2L;
            case L2F -> F2L;
            case F2UI -> UI2F;
            case D2UI -> UI2D;
            case F2UL -> UL2F;
            case D2UL -> UL2D;
            case UI2F -> F2UI;
            case UL2F -> F2UL;
            case UI2D -> D2UI;
            case UL2D -> D2UL;
            default -> throw GraalError.shouldNotReachHereUnexpectedValue(this); // ExcludeFromJacocoGeneratedReport
        };
    }

    public int getInputBits() {
        return inputBits;
    }

    /**
     * Returns the conversion operation corresponding to a conversion from {@code from} to
     * {@code to}. Returns {@code null} if the given stamps don't correspond to a conversion
     * operation.
     */
    public static FloatConvert forStamps(Stamp from, Stamp to) {
        int fromBits = PrimitiveStamp.getBits(from);
        int toBits = PrimitiveStamp.getBits(to);
        if (from instanceof FloatStamp) {
            if (to instanceof FloatStamp) {
                if (fromBits == 32 && toBits == 64) {
                    return F2D;
                } else if (fromBits == 64 && toBits == 32) {
                    return D2F;
                }
            } else if (to instanceof IntegerStamp) {
                if (fromBits == 32 && toBits == 32) {
                    return F2I;
                } else if (fromBits == 32 && toBits == 64) {
                    return F2L;
                } else if (fromBits == 64 && toBits == 32) {
                    return D2I;
                } else if (fromBits == 64 && toBits == 64) {
                    return D2L;
                }
            }
        } else if (from instanceof IntegerStamp && to instanceof FloatStamp) {
            if (fromBits == 32 && toBits == 32) {
                return I2F;
            } else if (fromBits == 32 && toBits == 64) {
                return I2D;
            } else if (fromBits == 64 && toBits == 32) {
                return L2F;
            } else if (fromBits == 64 && toBits == 64) {
                return L2D;
            }
        }
        return null;
    }

    public Signedness signedness() {
        return switch (this) {
            case D2F -> Signedness.SIGNED;
            case D2I -> Signedness.SIGNED;
            case D2L -> Signedness.SIGNED;
            case F2D -> Signedness.SIGNED;
            case F2I -> Signedness.SIGNED;
            case F2L -> Signedness.SIGNED;
            case I2D -> Signedness.SIGNED;
            case I2F -> Signedness.SIGNED;
            case L2D -> Signedness.SIGNED;
            case L2F -> Signedness.SIGNED;
            case F2UI -> Signedness.UNSIGNED;
            case D2UI -> Signedness.UNSIGNED;
            case F2UL -> Signedness.UNSIGNED;
            case D2UL -> Signedness.UNSIGNED;
            case UI2F -> Signedness.UNSIGNED;
            case UL2F -> Signedness.UNSIGNED;
            case UI2D -> Signedness.UNSIGNED;
            case UL2D -> Signedness.UNSIGNED;
            default -> throw GraalError.shouldNotReachHereUnexpectedValue(this); // ExcludeFromJacocoGeneratedReport
        };
    }
}
