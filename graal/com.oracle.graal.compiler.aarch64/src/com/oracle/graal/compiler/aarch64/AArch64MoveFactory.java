/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.graal.compiler.aarch64;

import static com.oracle.graal.lir.LIRValueUtil.asConstant;
import static com.oracle.graal.lir.LIRValueUtil.isConstantValue;
import static com.oracle.graal.lir.LIRValueUtil.isStackSlotValue;

import com.oracle.graal.compiler.aarch64.AArch64LIRGenerator.ConstantTableBaseProvider;
import com.oracle.graal.lir.LIRInstruction;
import com.oracle.graal.lir.aarch64.AArch64AddressValue;
import com.oracle.graal.lir.aarch64.AArch64Move.LoadAddressOp;
import com.oracle.graal.lir.gen.LIRGeneratorTool.MoveFactory;

import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.Value;

public class AArch64MoveFactory implements MoveFactory {

    private final CodeCacheProvider codeCache;
    protected final ConstantTableBaseProvider constantTableBaseProvider;

    public AArch64MoveFactory(CodeCacheProvider codeCache, ConstantTableBaseProvider constantTableBaseProvider) {
        this.codeCache = codeCache;
        this.constantTableBaseProvider = constantTableBaseProvider;
    }

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
                throw JVMCIError.shouldNotReachHere(src.getClass() + " " + dst.getClass());
            } else {
                // return new Move(dst, (AllocatableValue) src);
                throw JVMCIError.unimplemented();
            }
        }
    }

    @Override
    public LIRInstruction createStackMove(AllocatableValue result, AllocatableValue input) {
        // return new AArch64Move.Move(result, input);
        throw JVMCIError.unimplemented();
    }

    @Override
    public LIRInstruction createLoad(AllocatableValue dst, Constant src) {
        if (src instanceof JavaConstant) {
            JavaConstant javaConstant = (JavaConstant) src;
            if (canInlineConstant(javaConstant)) {
                // return new AArch64Move.LoadInlineConstant(javaConstant, dst);
                throw JVMCIError.unimplemented();
            } else {
                // return new AArch64Move.LoadConstantFromTable(javaConstant,
                // constantTableBaseProvider.getConstantTableBase(), dst);
                throw JVMCIError.unimplemented();
            }
        } else {
            throw JVMCIError.shouldNotReachHere(src.getClass().toString());
        }
    }

    @Override
    public boolean canInlineConstant(JavaConstant c) {
        switch (c.getJavaKind()) {
            case Boolean:
            case Byte:
            case Char:
            case Short:
            case Int:
                // return SPARCAssembler.isSimm13(c.asInt()) && !codeCache.needsDataPatch(c);
                boolean x = !codeCache.needsDataPatch(c);
                throw JVMCIError.unimplemented("needsDataPatch=" + x);
            case Long:
                // return SPARCAssembler.isSimm13(c.asLong()) && !codeCache.needsDataPatch(c);
                boolean y = !codeCache.needsDataPatch(c);
                throw JVMCIError.unimplemented("needsDataPatch=" + y);
            case Object:
                return c.isNull();
            default:
                return false;
        }
    }

    @Override
    public boolean allowConstantToStackMove(Constant value) {
        return false;
    }

}
