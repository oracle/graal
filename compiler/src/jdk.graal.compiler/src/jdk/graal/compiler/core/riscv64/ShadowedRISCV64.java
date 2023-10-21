/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

package jdk.graal.compiler.core.riscv64;

import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterArray;

public class ShadowedRISCV64 {
    public static final Class<?> riscv64OrNull = RISCV64ReflectionUtil.getArch(true);

    public static final Register x0 = getRegister("x0");
    public static final Register x1 = getRegister("x1");
    public static final Register x2 = getRegister("x2");
    public static final Register x3 = getRegister("x3");
    public static final Register x4 = getRegister("x4");
    public static final Register x5 = getRegister("x5");
    public static final Register x6 = getRegister("x6");
    public static final Register x7 = getRegister("x7");
    public static final Register x8 = getRegister("x8");
    public static final Register x9 = getRegister("x9");
    public static final Register x10 = getRegister("x10");
    public static final Register x11 = getRegister("x11");
    public static final Register x12 = getRegister("x12");
    public static final Register x13 = getRegister("x13");
    public static final Register x14 = getRegister("x14");
    public static final Register x15 = getRegister("x15");
    public static final Register x16 = getRegister("x16");
    public static final Register x17 = getRegister("x17");
    public static final Register x18 = getRegister("x18");
    public static final Register x19 = getRegister("x19");
    public static final Register x20 = getRegister("x20");
    public static final Register x21 = getRegister("x21");
    public static final Register x22 = getRegister("x22");
    public static final Register x23 = getRegister("x23");
    public static final Register x24 = getRegister("x24");
    public static final Register x25 = getRegister("x25");
    public static final Register x26 = getRegister("x26");
    public static final Register x27 = getRegister("x27");
    public static final Register x28 = getRegister("x28");
    public static final Register x29 = getRegister("x29");
    public static final Register x30 = getRegister("x30");
    public static final Register x31 = getRegister("x31");

    public static final Register f0 = getRegister("f0");
    public static final Register f1 = getRegister("f1");
    public static final Register f2 = getRegister("f2");
    public static final Register f3 = getRegister("f3");
    public static final Register f4 = getRegister("f4");
    public static final Register f5 = getRegister("f5");
    public static final Register f6 = getRegister("f6");
    public static final Register f7 = getRegister("f7");
    public static final Register f8 = getRegister("f8");
    public static final Register f9 = getRegister("f9");
    public static final Register f10 = getRegister("f10");
    public static final Register f11 = getRegister("f11");
    public static final Register f12 = getRegister("f12");
    public static final Register f13 = getRegister("f13");
    public static final Register f14 = getRegister("f14");
    public static final Register f15 = getRegister("f15");
    public static final Register f16 = getRegister("f16");
    public static final Register f17 = getRegister("f17");
    public static final Register f18 = getRegister("f18");
    public static final Register f19 = getRegister("f19");
    public static final Register f20 = getRegister("f20");
    public static final Register f21 = getRegister("f21");
    public static final Register f22 = getRegister("f22");
    public static final Register f23 = getRegister("f23");
    public static final Register f24 = getRegister("f24");
    public static final Register f25 = getRegister("f25");
    public static final Register f26 = getRegister("f26");
    public static final Register f27 = getRegister("f27");
    public static final Register f28 = getRegister("f28");
    public static final Register f29 = getRegister("f29");
    public static final Register f30 = getRegister("f30");
    public static final Register f31 = getRegister("f31");

    public static final RegisterArray allRegisters = riscv64OrNull == null ? null : RISCV64ReflectionUtil.readStaticField(riscv64OrNull, "allRegisters");

    public static boolean instanceOf(Architecture arch) {
        return ShadowedRISCV64.riscv64OrNull != null && ShadowedRISCV64.riscv64OrNull.isInstance(arch);
    }

    private static Register getRegister(String register) {
        return riscv64OrNull == null ? null : RISCV64ReflectionUtil.readStaticField(riscv64OrNull, register);
    }
}
