/*
 * Copyright (c) 2015, 2021, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.aarch64;

import static jdk.vm.ci.meta.JavaConstant.INT_0;
import static jdk.vm.ci.meta.JavaConstant.LONG_0;

import jdk.graal.compiler.core.aarch64.AArch64MoveFactory;
import jdk.graal.compiler.lir.aarch64.AArch64LIRInstruction;

import jdk.vm.ci.aarch64.AArch64Kind;
import jdk.vm.ci.hotspot.HotSpotConstant;
import jdk.vm.ci.hotspot.HotSpotMetaspaceConstant;
import jdk.vm.ci.hotspot.HotSpotObjectConstant;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;

public class AArch64HotSpotMoveFactory extends AArch64MoveFactory {

    @Override
    public boolean canInlineConstant(Constant c) {
        if (JavaConstant.isNull(c)) {
            return true;
        } else if (c instanceof HotSpotObjectConstant || c instanceof HotSpotMetaspaceConstant) {
            return false;
        } else {
            return super.canInlineConstant(c);
        }
    }

    @Override
    public AArch64LIRInstruction createLoad(AllocatableValue dst, Constant src) {
        Constant usedSource;
        if (JavaConstant.isNull(src)) {
            /*
             * On HotSpot null values can be represented by the zero value of appropriate length.
             */
            var platformKind = dst.getPlatformKind();
            assert platformKind.equals(AArch64Kind.DWORD) || platformKind.equals(AArch64Kind.QWORD) : String.format("unexpected null value: %s[%s]", platformKind, src);
            usedSource = platformKind.getSizeInBytes() == Integer.BYTES ? INT_0 : LONG_0;
        } else {
            usedSource = src;
        }
        if (usedSource instanceof HotSpotConstant) {
            HotSpotConstant constant = (HotSpotConstant) usedSource;
            if (constant.isCompressed()) {
                return new AArch64HotSpotMove.LoadHotSpotObjectConstantInline(constant, dst);
            } else {
                return new AArch64HotSpotMove.LoadHotSpotObjectConstantInline(constant, dst);
            }
        } else {
            return super.createLoad(dst, usedSource);
        }
    }
}
