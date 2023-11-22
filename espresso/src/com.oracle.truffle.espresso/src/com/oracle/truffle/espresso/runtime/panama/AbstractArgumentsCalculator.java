/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.runtime.panama;

import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.meta.EspressoError;

public abstract class AbstractArgumentsCalculator implements ArgumentsCalculator {
    protected final Platform platform;
    protected final VMStorage[] callIntRegs;
    protected final VMStorage[] callFloatRegs;
    protected final VMStorage intReturn;
    protected final VMStorage floatReturn;

    protected AbstractArgumentsCalculator(Platform platform, VMStorage[] callIntRegs, VMStorage[] callFloatRegs, VMStorage intReturn, VMStorage floatReturn) {
        this.platform = platform;
        this.callIntRegs = callIntRegs;
        this.callFloatRegs = callFloatRegs;
        this.intReturn = intReturn;
        this.floatReturn = floatReturn;
    }

    protected static boolean isInt(Klass type) {
        return switch (type.getJavaKind()) {
            case Boolean, Byte, Char, Short, Int, Long -> true;
            case Float, Double -> false;
            case Void, Illegal, Object, ReturnAddress -> throw EspressoError.shouldNotReachHere(type.getJavaKind().toString());
        };
    }

    protected static boolean isFloat(Klass type) {
        return switch (type.getJavaKind()) {
            case Boolean, Byte, Char, Short, Int, Long -> false;
            case Float, Double -> true;
            case Void, Illegal, Object, ReturnAddress -> throw EspressoError.shouldNotReachHere(type.getJavaKind().toString());
        };
    }
}
