/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.lir.aarch64;

import java.util.EnumSet;

import jdk.graal.compiler.asm.aarch64.AArch64Assembler;

import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.aarch64.AArch64.CPUFeature;
import jdk.vm.ci.aarch64.AArch64.Flag;

public class AArch64LIRFlags {
    public static boolean useLSE(EnumSet<CPUFeature> features, EnumSet<Flag> flags) {
        return features.contains(CPUFeature.LSE) && flags.contains(Flag.UseLSE);
    }

    public static boolean useLSE(AArch64Assembler asm) {
        return useLSE(asm.getFeatures(), asm.getFlags());
    }

    public static boolean useLSE(AArch64 arch) {
        return useLSE(arch.getFeatures(), arch.getFlags());
    }
}
