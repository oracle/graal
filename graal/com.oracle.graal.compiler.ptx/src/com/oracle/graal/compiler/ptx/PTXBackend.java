/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.compiler.ptx;

import static com.oracle.graal.api.code.ValueUtil.*;

import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.*;
import com.oracle.graal.asm.ptx.*;
import com.oracle.graal.compiler.gen.*;
import com.oracle.graal.compiler.target.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.asm.*;
import com.oracle.graal.lir.ptx.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.cfg.Block;
import com.oracle.graal.lir.LIRInstruction.OperandFlag;
import com.oracle.graal.lir.LIRInstruction.OperandMode;
import com.oracle.graal.lir.LIRInstruction.ValueProcedure;
import com.oracle.graal.lir.StandardOp.LabelOp;
import com.oracle.graal.graph.GraalInternalError;

/**
 * PTX specific backend.
 */
public class PTXBackend extends Backend {

    public PTXBackend(CodeCacheProvider runtime, TargetDescription target) {
        super(runtime, target);
    }

    @Override
    public FrameMap newFrameMap() {
        return new PTXFrameMap(runtime(), target, runtime().lookupRegisterConfig());
    }

    @Override
    public LIRGenerator newLIRGenerator(StructuredGraph graph, FrameMap frameMap, CallingConvention cc, LIR lir) {
        return new PTXLIRGenerator(graph, runtime(), target, frameMap, cc, lir);
    }

    class HotSpotFrameContext implements FrameContext {

        @Override
        public void enter(TargetMethodAssembler tasm) {
            // codeBuffer.emitString(".address_size 32"); // PTX ISA version 2.3
        }

        @Override
        public void leave(TargetMethodAssembler tasm) {
        }
    }

    @Override
    protected AbstractAssembler createAssembler(FrameMap frameMap) {
        return new PTXAssembler(target, frameMap.registerConfig);
    }

    @Override
    public TargetMethodAssembler newAssembler(LIRGenerator lirGen, CompilationResult compilationResult) {
        // Omit the frame of the method:
        // - has no spill slots or other slots allocated during register allocation
        // - has no callee-saved registers
        // - has no incoming arguments passed on the stack
        // - has no instructions with debug info
        FrameMap frameMap = lirGen.frameMap;
        AbstractAssembler masm = createAssembler(frameMap);
        HotSpotFrameContext frameContext = new HotSpotFrameContext();
        TargetMethodAssembler tasm = new PTXTargetMethodAssembler(target, runtime(), frameMap, masm, frameContext, compilationResult);
        tasm.setFrameSize(0);
        return tasm;
    }

    private static void emitKernelEntry(TargetMethodAssembler tasm, LIRGenerator lirGen, ResolvedJavaMethod codeCacheOwner) {
        // Emit PTX kernel entry text based on PTXParameterOp
        // instructions in the start block. Remove the instructions
        // once kernel entry text and directives are emitted to
        // facilitate seemless PTX code generation subsequently.
        assert codeCacheOwner != null : lirGen.getGraph() + " is not associated with a method";
        final String name = codeCacheOwner.getName();
        Buffer codeBuffer = tasm.asm.codeBuffer;

        // Emit initial boiler-plate directives.
        codeBuffer.emitString(".version 1.4");
        codeBuffer.emitString(".target sm_10");
        codeBuffer.emitString0(".entry " + name + " (");
        codeBuffer.emitString("");

        // Get the start block
        Block startBlock = lirGen.lir.cfg.getStartBlock();
        // Keep a list of ParameterOp instructions to delete from the
        // list of instructions in the block.
        ArrayList<LIRInstruction> deleteOps = new ArrayList<>();

        // Emit .param arguments to kernel entry based on ParameterOp
        // instruction.
        for (LIRInstruction op : lirGen.lir.lir(startBlock)) {
            if (op instanceof PTXParameterOp) {
                op.emitCode(tasm);
                deleteOps.add(op);
            }
        }

        // Delete ParameterOp instructions.
        for (LIRInstruction op : deleteOps) {
            lirGen.lir.lir(startBlock).remove(op);
        }

        // Start emiting body of the PTX kernel.
        codeBuffer.emitString0(") {");
        codeBuffer.emitString("");
    }

    // Emit .reg space declarations
    private static void emitRegisterDecl(TargetMethodAssembler tasm, LIRGenerator lirGen,
                                         ResolvedJavaMethod codeCacheOwner) {
        assert codeCacheOwner != null : lirGen.getGraph() + " is not associated with a method";
        Buffer codeBuffer = tasm.asm.codeBuffer;

        final SortedSet<Integer> signed32 = new TreeSet<>();
        final SortedSet<Integer> signed64 = new TreeSet<>();
        final SortedSet<Integer> unsigned64 = new TreeSet<>();
        final SortedSet<Integer> float32 = new TreeSet<>();
        final SortedSet<Integer> float64 = new TreeSet<>();

        ValueProcedure trackRegisterKind = new ValueProcedure() {

            @Override
            public Value doValue(Value value, OperandMode mode, EnumSet<OperandFlag> flags) {
                if (isRegister(value)) {
                    RegisterValue regVal = (RegisterValue) value;
                    Kind regKind = regVal.getKind();
                    switch (regKind) {
                        case Int:
                            // If the register was used as a wider signed type
                            // do not add it here
                            if (!signed64.contains(regVal.getRegister().encoding())) {
                                signed32.add(regVal.getRegister().encoding());
                            }
                            break;
                        case Long:
                            // If the register was used as a narrower signed type
                            // remove it from there and add it to wider type.
                            if (signed32.contains(regVal.getRegister().encoding())) {
                                signed32.remove(regVal.getRegister().encoding());
                            }
                            signed64.add(regVal.getRegister().encoding());
                            break;
                        case Float:
                            // If the register was used as a wider signed type
                            // do not add it here
                            if (!float64.contains(regVal.getRegister().encoding())) {
                                float32.add(regVal.getRegister().encoding());
                            }
                            break;
                        case Double:
                            // If the register was used as a narrower signed type
                            // remove it from there and add it to wider type.
                            if (float32.contains(regVal.getRegister().encoding())) {
                                float32.remove(regVal.getRegister().encoding());
                            }
                            float64.add(regVal.getRegister().encoding());
                            break;
                        case Object:
                            unsigned64.add(regVal.getRegister().encoding());
                            break;
                        default:
                            throw GraalInternalError.shouldNotReachHere("unhandled register type " + value.toString());
                    }
                }
                return value;
            }
        };

        for (Block b : lirGen.lir.codeEmittingOrder()) {
            for (LIRInstruction op : lirGen.lir.lir(b)) {
                if (op instanceof LabelOp) {
                    // Don't consider this as a definition
                } else {
                    op.forEachTemp(trackRegisterKind);
                    op.forEachOutput(trackRegisterKind);
                }
            }
        }

        for (Integer i : signed32) {
            codeBuffer.emitString(".reg .s32 %r" + i.intValue() + ";");
        }
        for (Integer i : signed64) {
            codeBuffer.emitString(".reg .s64 %r" + i.intValue() + ";");
        }
        for (Integer i : unsigned64) {
            codeBuffer.emitString(".reg .u64 %r" + i.intValue() + ";");
        }
        for (Integer i : float32) {
            codeBuffer.emitString(".reg .f32 %r" + i.intValue() + ";");
        }
        for (Integer i : float64) {
            codeBuffer.emitString(".reg .f64 %r" + i.intValue() + ";");
        }
        // emit predicate register declaration
        int maxPredRegNum = ((PTXLIRGenerator) lirGen).getNextPredRegNumber();
        if (maxPredRegNum > 0) {
            codeBuffer.emitString(".reg .pred %p<" + maxPredRegNum + ">;");
        }
    }

    @Override
    public void emitCode(TargetMethodAssembler tasm, LIRGenerator lirGen, ResolvedJavaMethod codeCacheOwner) {
        assert codeCacheOwner != null : lirGen.getGraph() + " is not associated with a method";
        Buffer codeBuffer = tasm.asm.codeBuffer;
        // Emit the prologue
        emitKernelEntry(tasm, lirGen, codeCacheOwner);

        // Emit register declarations
        try {
            emitRegisterDecl(tasm, lirGen, codeCacheOwner);
        } catch (GraalInternalError e) {
            e.printStackTrace();
            // TODO : Better error handling needs to be done once
            //        all types of parameters are handled.
            codeBuffer.setPosition(0);
            codeBuffer.close(false);
            return;
        }
        // Emit code for the LIR
        try {
            lirGen.lir.emitCode(tasm);
        } catch (GraalInternalError e) {
            e.printStackTrace();
            // TODO : Better error handling needs to be done once
            //        all types of parameters are handled.
            codeBuffer.setPosition(0);
            codeBuffer.close(false);
            return;
        }

        // Emit the epilogue
        codeBuffer.emitString0("}");
        codeBuffer.emitString("");
    }
}
