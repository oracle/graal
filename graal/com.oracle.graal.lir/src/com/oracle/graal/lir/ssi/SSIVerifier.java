/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.lir.ssi;

import static jdk.internal.jvmci.code.ValueUtil.*;

import java.util.*;

import jdk.internal.jvmci.meta.*;

import com.oracle.graal.compiler.common.cfg.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.debug.Debug.Scope;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.LIRInstruction.OperandFlag;
import com.oracle.graal.lir.LIRInstruction.OperandMode;
import com.oracle.graal.lir.StandardOp.BlockEndOp;
import com.oracle.graal.lir.StandardOp.LabelOp;

public final class SSIVerifier {

    public static boolean verify(LIR lir) {
        return new SSIVerifier(lir).verify();
    }

    private final LIR lir;

    private SSIVerifier(LIR lir) {
        this.lir = lir;
    }

    private boolean verify() {
        try (Scope s = Debug.scope("SSIVerifier", lir)) {
            for (AbstractBlockBase<?> block : lir.getControlFlowGraph().getBlocks()) {
                doBlock(block);
            }
        } catch (Throwable e) {
            throw Debug.handle(e);
        }
        return true;
    }

    private void doBlock(AbstractBlockBase<?> block) {
        for (AbstractBlockBase<?> succ : block.getSuccessors()) {
            verifyEdge(block, succ);
        }
        verifyInstructions(block);
    }

    private void verifyEdge(AbstractBlockBase<?> from, AbstractBlockBase<?> to) {
        BlockEndOp out = SSIUtil.outgoing(lir, from);
        LabelOp in = SSIUtil.incoming(lir, to);
        int outgoingSize = out.getOutgoingSize();
        int incomingSize = in.getIncomingSize();
        assert outgoingSize == incomingSize : String.format("Outgoing size %d and incoming size %d do not match", outgoingSize, incomingSize);

        for (int i = 0; i < outgoingSize; i++) {
            Value incomingValue = in.getIncomingValue(i);
            Value outgoingValue = out.getOutgoingValue(i);
            LIRKind inLIRKind = incomingValue.getLIRKind();
            LIRKind outLIRKind = outgoingValue.getLIRKind();
            assert LIRKind.verifyMoveKinds(inLIRKind, outLIRKind) || incomingValue.equals(Value.ILLEGAL) : String.format("Outgoing LIRKind %s (%s) an and incoming LIRKind %s (%s) do not match",
                            outgoingValue, outLIRKind, incomingValue, inLIRKind);
        }
    }

    private void verifyInstructions(AbstractBlockBase<?> block) {
        List<LIRInstruction> instructions = lir.getLIRforBlock(block);
        HashMap<Value, LIRInstruction> defined = new HashMap<>();

        InstructionValueConsumer useConsumer = new InstructionValueConsumer() {
            public void visitValue(LIRInstruction instruction, Value value, OperandMode mode, EnumSet<OperandFlag> flags) {
                if (checkUsage(value)) {
                    assert defined.containsKey(value) || flags.contains(OperandFlag.UNINITIALIZED) : String.format("Value %s is used by instruction %s in block %s but not defined.", value,
                                    instruction, block);
                }
            }
        };
        InstructionValueConsumer stateConsumer = new InstructionValueConsumer() {
            public void visitValue(LIRInstruction instruction, Value value, OperandMode mode, EnumSet<OperandFlag> flags) {
                if (checkUsage(value)) {
                    /*
                     * TODO (je): There are undefined stack values used for locks. Ignore them for
                     * the time being.
                     */
                    assert defined.containsKey(value) || isVirtualStackSlot(value) : String.format("Value %s is used in state of instruction %s in block %s but not defined.", value, instruction,
                                    block);
                }
            }
        };

        InstructionValueConsumer defConsumer = new InstructionValueConsumer() {
            public void visitValue(LIRInstruction instruction, Value value, OperandMode mode, EnumSet<OperandFlag> flags) {
                if (trackDefinition(value)) {
                    assert !defined.containsKey(value) : String.format("Value %s is redefined by instruction %s in block %s but already defined by %s.", value, instruction, block, defined.get(value));
                    defined.put(value, instruction);
                }
            }
        };

        for (LIRInstruction op : instructions) {
            op.visitEachAlive(useConsumer);
            op.visitEachInput(useConsumer);
            op.visitEachState(stateConsumer);

            op.visitEachTemp(defConsumer);
            op.visitEachOutput(defConsumer);
        }
    }

    private static boolean trackDefinition(Value value) {
        if (isRegister(value)) {
            // registers can be redefined
            return false;
        }
        if (value.equals(Value.ILLEGAL)) {
            // Don't care about illegal values
            return false;
        }
        return true;
    }

    private static boolean checkUsage(Value value) {
        if (value instanceof Constant) {
            // Constants do not need to be defined
            return false;
        }
        if (isRegister(value)) {
            // Assume fixed registers are correct
            return false;
        }
        if (value.equals(Value.ILLEGAL)) {
            // Don't care about illegal values
            return false;
        }
        return true;
    }
}
