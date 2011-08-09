/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.graal.compiler.gen;

import com.oracle.max.graal.compiler.alloc.OperandPool.VariableFlag;
import com.oracle.max.graal.compiler.util.*;
import com.oracle.max.graal.nodes.*;
import com.sun.cri.ci.*;

/**
 * A helper utility for loading the {@linkplain ValueNode#operand() result}
 * of an instruction for use by another instruction. This helper takes
 * into account the specifics of the consuming instruction such as whether
 * it requires the input operand to be in memory or a register, any
 * register size requirements of the input operand, and whether the
 * usage has the side-effect of overwriting the input operand. To satisfy
 * these constraints, an intermediate operand may be created and move
 * instruction inserted to copy the output of the producer instruction
 * into the intermediate operand.
 *
 * @author Marcelo Cintra
 * @author Thomas Wuerthinger
 * @author Doug Simon
 */
public class LIRItem {

    /**
     * The instruction whose usage by another instruction is being modeled by this object.
     * An instruction {@code x} uses instruction {@code y} if the {@linkplain ValueNode#operand() result}
     * of {@code y} is an input operand of {@code x}.
     */
    public ValueNode instruction;

    /**
     * The LIR context of this helper object.
     */
    private final LIRGenerator gen;

    /**
     * The operand holding the result of this item's {@linkplain #instruction}.
     */
    private CiValue resultOperand;

    /**
     * Denotes if the use of the instruction's {@linkplain #resultOperand result operand}
     * overwrites the value in the operand. That is, the use both uses and defines the
     * operand. In this case, an {@linkplain #intermediateOperand intermediate operand}
     * is created for the use so that other consumers of this item's {@linkplain #instruction}
     * are not impacted.
     */
    private boolean destructive;

    /**
     * @see #destructive
     */
    private CiValue intermediateOperand;

    public LIRItem(ValueNode instruction, LIRGenerator gen) {
        this.gen = gen;
        this.instruction = instruction;
        resultOperand = gen.makeOperand(instruction);
        intermediateOperand = CiValue.IllegalValue;
    }

    public void loadForStore(CiKind kind) {
        if (gen.canStoreAsConstant(instruction, kind)) {
            resultOperand = instruction.operand();
            if (!resultOperand.isConstant()) {
                resultOperand = instruction.asConstant();
            }
        } else if (kind == CiKind.Byte || kind == CiKind.Boolean) {
            loadByteItem();
        } else {
            loadItem();
        }
    }

    public CiValue result() {
        assert !destructive || !resultOperand.isRegister() : "shouldn't use setDestroysRegister with physical registers";
        if (destructive && (resultOperand.isVariable() || resultOperand.isConstant())) {
            if (intermediateOperand.isIllegal()) {
                intermediateOperand = gen.newVariable(instruction.kind);
                gen.lir.move(resultOperand, intermediateOperand);
            }
            return intermediateOperand;
        } else {
            return resultOperand;
        }
    }

    public void setDestroysRegister() {
        destructive = true;
    }

    /**
     * Determines if the operand is in a stack slot.
     */
    public boolean isStack() {
        return resultOperand.isAddress() || resultOperand.isStackSlot();
    }

    /**
     * Determines if the operand is in a register or may be
     * resolved to a register by the register allocator.
     */
    public boolean isRegisterOrVariable() {
        return resultOperand.isVariableOrRegister();
    }

    public void loadByteItem() {
        if (gen.compilation.target.arch.isX86()) {
            loadItem();
            CiValue res = result();

            if (!res.isVariable() || !gen.operands.mustBeByteRegister(res)) {
                // make sure that it is a byte register
                assert !instruction.kind.isFloat() && !instruction.kind.isDouble() : "can't load floats in byte register";
                CiValue reg = gen.operands.newVariable(CiKind.Byte, VariableFlag.MustBeByteRegister);
                gen.lir.move(res, reg);
                resultOperand = reg;
            }
        } else if (gen.compilation.target.arch.isSPARC()) {
            loadItem();
        } else {
            Util.shouldNotReachHere();
        }
    }

    public void loadNonconstant() {
        assert gen.compilation.target.arch.isX86();
        CiValue r = instruction.operand();
        if (r.isConstant()) {
            resultOperand = r;
        } else {
            loadItem();
        }
    }

    private void setResult(CiVariable operand) {
        gen.setResult(instruction, operand);
        resultOperand = operand;
    }

    /**
     * Creates an operand containing the result of {@linkplain #instruction input instruction}.
     */
    public void loadItem() {
        if (result().isIllegal()) {
            // update the item's result
            resultOperand = instruction.operand();
        }
        CiValue result = result();
        if (!result.isVariableOrRegister()) {
            CiVariable operand;
            operand = gen.newVariable(instruction.kind);
            gen.lir.move(result, operand);
            if (result.isConstant()) {
                resultOperand = operand;
            } else {
                setResult(operand);
            }
        }
    }

    @Override
    public String toString() {
        return result().toString();
    }
}
