/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.nodes.spi;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.FloatConvertNode.FloatConvert;

/**
 * This interface can be used to generate LIR for arithmetic operations.
 */
public interface ArithmeticLIRGenerator {
    /**
     * TODO remove reference to {@link Stamp}.
     */
    PlatformKind getPlatformKind(Stamp stamp);

    Value emitNegate(Value input);

    Value emitAdd(Value a, Value b);

    Value emitSub(Value a, Value b);

    Value emitMul(Value a, Value b);

    /**
     * TODO remove {@link DeoptimizeNode}.
     */
    Value emitDiv(Value a, Value b, DeoptimizingNode deopting);

    /**
     * TODO remove {@link DeoptimizeNode}.
     */
    Value emitRem(Value a, Value b, DeoptimizingNode deopting);

    /**
     * TODO remove {@link DeoptimizeNode}.
     */
    Value emitUDiv(Value a, Value b, DeoptimizingNode deopting);

    /**
     * TODO remove {@link DeoptimizeNode}.
     */
    Value emitURem(Value a, Value b, DeoptimizingNode deopting);

    Value emitNot(Value input);

    Value emitAnd(Value a, Value b);

    Value emitOr(Value a, Value b);

    Value emitXor(Value a, Value b);

    Value emitShl(Value a, Value b);

    Value emitShr(Value a, Value b);

    Value emitUShr(Value a, Value b);

    Value emitFloatConvert(FloatConvert op, Value inputVal);

    Value emitReinterpret(PlatformKind to, Value inputVal);

    Value emitNarrow(Value inputVal, int bits);

    Value emitSignExtend(Value inputVal, int fromBits, int toBits);

    Value emitZeroExtend(Value inputVal, int fromBits, int toBits);

    Value emitMathAbs(Value input);

    Value emitMathSqrt(Value input);

    Value emitMathLog(Value input, boolean base10);

    Value emitMathCos(Value input);

    Value emitMathSin(Value input);

    Value emitMathTan(Value input);
}
