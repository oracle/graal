/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.vector.lir.aarch64;

import jdk.graal.compiler.asm.aarch64.ASIMDKind;
import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.vector.nodes.simd.SimdLIRKindTool;
import jdk.vm.ci.aarch64.AArch64Kind;

public interface AArch64SimdLIRKindTool extends SimdLIRKindTool {

    @Override
    default LIRKind getSimdKind(int length, LIRKind element) {
        AArch64Kind elementKind = (AArch64Kind) element.getPlatformKind();
        AArch64Kind vectorKind = ASIMDKind.getASIMDKind(elementKind, length);
        return element.repeat(vectorKind);
    }

    @Override
    default LIRKind getMaskKind(int vectorLength) {
        throw GraalError.unimplementedOverride();
    }
}
