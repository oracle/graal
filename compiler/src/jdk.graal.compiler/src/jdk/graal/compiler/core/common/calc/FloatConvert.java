/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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
    F2D(FloatConvertCategory.FloatingPointToFloatingPoint, 32);

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
        switch (this) {
            case D2F:
                return F2D;
            case D2I:
                return I2D;
            case D2L:
                return L2D;
            case F2D:
                return D2F;
            case F2I:
                return I2F;
            case F2L:
                return L2F;
            case I2D:
                return D2I;
            case I2F:
                return F2I;
            case L2D:
                return D2L;
            case L2F:
                return F2L;
            default:
                throw GraalError.shouldNotReachHereUnexpectedValue(this); // ExcludeFromJacocoGeneratedReport
        }
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
}
