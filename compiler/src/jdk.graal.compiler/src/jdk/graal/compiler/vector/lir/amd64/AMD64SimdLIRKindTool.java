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
package jdk.graal.compiler.vector.lir.amd64;

import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.asm.amd64.AVXKind;
import jdk.vm.ci.amd64.AMD64Kind;

import jdk.graal.compiler.vector.nodes.simd.SimdLIRKindTool;

public interface AMD64SimdLIRKindTool extends SimdLIRKindTool {

    @Override
    default LIRKind getSimdKind(int length, LIRKind element) {
        AMD64Kind vectorKind = AVXKind.getAVXKind((AMD64Kind) element.getPlatformKind(), length);
        return element.repeat(vectorKind);
    }

    @Override
    default LIRKind getMaskKind(int vectorLength) {
        return LIRKind.value(switch (Math.max(vectorLength, 8)) {
            case 8 -> AMD64Kind.MASK8;
            case 16 -> AMD64Kind.MASK16;
            case 32 -> AMD64Kind.MASK32;
            case 64 -> AMD64Kind.MASK64;
            default -> throw GraalError.shouldNotReachHereUnexpectedValue(vectorLength);
        });
    }
}
