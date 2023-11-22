/*
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
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

package jdk.graal.compiler.core.amd64;

import static jdk.vm.ci.code.ValueUtil.isRegister;
import static jdk.graal.compiler.lir.LIRValueUtil.asConstant;
import static jdk.graal.compiler.lir.LIRValueUtil.isConstantValue;
import static jdk.graal.compiler.lir.LIRValueUtil.isStackSlotValue;

import jdk.graal.compiler.asm.amd64.AMD64Assembler;
import jdk.graal.compiler.core.common.type.DataPointerConstant;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.lir.LIRInstruction;
import jdk.graal.compiler.lir.amd64.AMD64AddressValue;
import jdk.graal.compiler.lir.amd64.AMD64LIRInstruction;
import jdk.graal.compiler.lir.amd64.AMD64Move;
import jdk.graal.compiler.lir.amd64.AMD64Move.AMD64StackMove;
import jdk.graal.compiler.lir.amd64.AMD64Move.LeaDataOp;
import jdk.graal.compiler.lir.amd64.AMD64Move.LeaOp;
import jdk.graal.compiler.lir.amd64.AMD64Move.MoveFromConstOp;
import jdk.graal.compiler.lir.amd64.AMD64Move.MoveFromRegOp;
import jdk.graal.compiler.lir.amd64.AMD64Move.MoveToRegOp;

import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.Value;

public abstract class AMD64MoveFactory extends AMD64MoveFactoryBase {

    public AMD64MoveFactory(BackupSlotProvider backupSlotProvider) {
        super(backupSlotProvider);
    }

    @Override
    public boolean canInlineConstant(Constant con) {
        if (con instanceof JavaConstant) {
            JavaConstant c = (JavaConstant) con;
            switch (c.getJavaKind()) {
                case Long:
                    return NumUtil.isInt(c.asLong());
                case Float:
                case Double:
                    return false;
                case Object:
                    return c.isNull();
                default:
                    return true;
            }
        }
        return false;
    }

    @Override
    public boolean mayEmbedConstantLoad(Constant constant) {
        // Only consider not inlineable constants here.
        return constant instanceof PrimitiveConstant && ((PrimitiveConstant) constant).getJavaKind().isNumericFloat();
    }

    @Override
    public boolean allowConstantToStackMove(Constant constant) {
        if (constant instanceof DataPointerConstant) {
            return false;
        }
        if (constant instanceof JavaConstant && !AMD64Move.canMoveConst2Stack(((JavaConstant) constant))) {
            return false;
        }
        return true;
    }

    @Override
    public AMD64LIRInstruction createMove(AllocatableValue dst, Value src) {
        if (src instanceof AMD64AddressValue) {
            return new LeaOp(dst, (AMD64AddressValue) src, AMD64Assembler.OperandSize.QWORD);
        } else if (isConstantValue(src)) {
            return createLoad(dst, asConstant(src));
        } else if (isRegister(src) || isStackSlotValue(dst)) {
            return new MoveFromRegOp((AMD64Kind) dst.getPlatformKind(), dst, (AllocatableValue) src);
        } else {
            return new MoveToRegOp((AMD64Kind) dst.getPlatformKind(), dst, (AllocatableValue) src);
        }
    }

    @Override
    public AMD64LIRInstruction createStackMove(AllocatableValue result, AllocatableValue input, Register scratchRegister, AllocatableValue backupSlot) {
        return new AMD64StackMove(result, input, scratchRegister, backupSlot);
    }

    @Override
    public AMD64LIRInstruction createLoad(AllocatableValue dst, Constant src) {
        if (src instanceof JavaConstant) {
            return new MoveFromConstOp(dst, (JavaConstant) src);
        } else if (src instanceof DataPointerConstant) {
            return new LeaDataOp(dst, (DataPointerConstant) src);
        } else {
            throw GraalError.shouldNotReachHere(String.format("unsupported constant: %s", src)); // ExcludeFromJacocoGeneratedReport
        }
    }

    @Override
    public LIRInstruction createStackLoad(AllocatableValue result, Constant input) {
        if (input instanceof JavaConstant) {
            return new MoveFromConstOp(result, (JavaConstant) input);
        } else {
            throw GraalError.shouldNotReachHere(String.format("unsupported constant for stack load: %s", input)); // ExcludeFromJacocoGeneratedReport
        }
    }
}
