/*
 * Copyright (c) 2015, 2022, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.compiler.core.aarch64;

import static org.graalvm.compiler.lir.LIRValueUtil.asConstant;
import static org.graalvm.compiler.lir.LIRValueUtil.isConstantValue;
import static org.graalvm.compiler.lir.LIRValueUtil.isStackSlotValue;

import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.compiler.core.common.type.DataPointerConstant;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.aarch64.AArch64AddressValue;
import org.graalvm.compiler.lir.aarch64.AArch64LIRInstruction;
import org.graalvm.compiler.lir.aarch64.AArch64Move;
import org.graalvm.compiler.lir.aarch64.AArch64Move.LoadAddressOp;
import org.graalvm.compiler.lir.gen.MoveFactory;

import jdk.vm.ci.aarch64.AArch64Kind;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.Value;

public class AArch64MoveFactory extends MoveFactory {

    @Override
    public LIRInstruction createMove(AllocatableValue dst, Value src) {
        boolean srcIsSlot = isStackSlotValue(src);
        boolean dstIsSlot = isStackSlotValue(dst);
        if (isConstantValue(src)) {
            return createLoad(dst, asConstant(src));
        } else if (src instanceof AArch64AddressValue) {
            return new LoadAddressOp(dst, (AArch64AddressValue) src);
        } else {
            assert src instanceof AllocatableValue;
            if (srcIsSlot && dstIsSlot) {
                throw GraalError.shouldNotReachHere(src.getClass() + " " + dst.getClass());
            } else {
                return new AArch64Move.Move((AArch64Kind) dst.getPlatformKind(), dst, (AllocatableValue) src);
            }
        }
    }

    @Override
    public LIRInstruction createStackMove(AllocatableValue result, AllocatableValue input) {
        return new AArch64Move.Move((AArch64Kind) result.getPlatformKind(), result, input);
    }

    @Override
    public AArch64LIRInstruction createLoad(AllocatableValue dst, Constant src) {
        if (src instanceof JavaConstant) {
            JavaConstant javaConstant = (JavaConstant) src;
            return new AArch64Move.LoadInlineConstant(javaConstant, dst);
        } else if (src instanceof DataPointerConstant) {
            return new AArch64Move.LoadDataOp(dst, (DataPointerConstant) src);
        } else {
            throw GraalError.unimplemented();
        }
    }

    @Override
    public LIRInstruction createStackLoad(AllocatableValue result, Constant input) {
        return createLoad(result, input);
    }

    @Override
    public boolean canInlineConstant(Constant con) {
        /*
         * Note canInlineConstant is merely an approximation of whether a constant is likely to be
         * able to be represented as an immediate within compare, logic, and arithmetic operations.
         *
         * On AArch64, the immediate format for these different types of operations varies. Hence,
         * currently we make an optimistic assumption. In cases where this assumption is false, then
         * the backend must make sure to load the constant into a register.
         */
        if (con instanceof JavaConstant) {
            JavaConstant c = (JavaConstant) con;
            switch (c.getJavaKind()) {
                case Boolean:
                case Byte:
                case Char:
                case Short:
                case Int:
                case Long:
                    /*
                     * Assume 16-bits or less of data (not including sign bits) can be inlined. This
                     * is optimistic for comparison, addition, and subtraction operations, which can
                     * hold 12-bits. However, for logical operations, the compatibility varies, as
                     * immediates are encoded as bitmasks (see
                     * AArch64Assembler.LogicalBitmaskImmediateEncoding).
                     */
                    return NumUtil.isSignedNbit(17, c.asLong());
                case Object:
                    return c.isNull();
                case Float:
                case Double:
                    /*
                     * Only comparisons against 0 can be represented as an immediate.
                     */
                    return false;
                default:
                    return false;
            }
        }
        return false;
    }

    @Override
    public boolean allowConstantToStackMove(Constant value) {
        return false;
    }

}
