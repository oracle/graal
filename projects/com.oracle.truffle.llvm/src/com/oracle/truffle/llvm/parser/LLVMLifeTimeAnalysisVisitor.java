/*
 * Copyright (c) 2016, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.parser;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EObject;

import com.intel.llvm.ireditor.lLVM_IR.BasicBlock;
import com.intel.llvm.ireditor.lLVM_IR.FunctionDef;
import com.intel.llvm.ireditor.lLVM_IR.Instruction;
import com.intel.llvm.ireditor.lLVM_IR.Instruction_br;
import com.intel.llvm.ireditor.lLVM_IR.Instruction_indirectbr;
import com.intel.llvm.ireditor.lLVM_IR.Instruction_ret;
import com.intel.llvm.ireditor.lLVM_IR.Instruction_switch;
import com.intel.llvm.ireditor.lLVM_IR.Instruction_unreachable;
import com.intel.llvm.ireditor.lLVM_IR.MiddleInstruction;
import com.intel.llvm.ireditor.lLVM_IR.NamedMiddleInstruction;
import com.intel.llvm.ireditor.lLVM_IR.NamedTerminatorInstruction;
import com.intel.llvm.ireditor.lLVM_IR.TerminatorInstruction;
import com.intel.llvm.ireditor.lLVM_IR.impl.Instruction_brImpl;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.llvm.runtime.LLVMOptions;

/**
 * This class determines which variables are dead after each basic block.
 */
public final class LLVMLifeTimeAnalysisVisitor {

    private static final String FUNCTION_FORMAT = "%s:\n";
    private static final String AFTER_BLOCK_FORMAT = "\t dead after bb %4s:";

    private final FrameDescriptor frameDescriptor;
    private final EList<BasicBlock> basicBlocks;
    private final BasicBlock entryBlock;
    private final Map<BasicBlock, List<FrameSlot>> writtenFrameSlotsPerBlock;

    private LLVMLifeTimeAnalysisVisitor(FunctionDef function, FrameDescriptor frameDescriptor) {
        this.frameDescriptor = frameDescriptor;
        basicBlocks = function.getBasicBlocks();
        entryBlock = basicBlocks.get(0);
        writtenFrameSlotsPerBlock = getWrittenFrameSlotsPerBlock(basicBlocks);
    }

    public static Map<BasicBlock, FrameSlot[]> visit(FunctionDef function, FrameDescriptor frameDescriptor) {
        Map<BasicBlock, FrameSlot[]> mapping = new LLVMLifeTimeAnalysisVisitor(function, frameDescriptor).visit();
        if (LLVMOptions.printLifeTimeAnalysis()) {
            printAnalysisResults(function, mapping);
        }
        return mapping;
    }

    private static void printAnalysisResults(FunctionDef analyzedFunction, Map<BasicBlock, FrameSlot[]> mapping) {
        System.out.print(String.format(FUNCTION_FORMAT, analyzedFunction.getHeader().getName()));
        for (BasicBlock b : mapping.keySet()) {
            System.out.print(String.format(AFTER_BLOCK_FORMAT, b.getName()));
            FrameSlot[] variables = mapping.get(b);
            if (variables.length != 0) {
                System.out.print("\t");
                for (int i = 0; i < variables.length; i++) {
                    if (i != 0) {
                        System.out.print(", ");
                    }
                    System.out.print(variables[i].getIdentifier());
                }
            }
            System.out.println();
        }
    }

    private Map<BasicBlock, FrameSlot[]> visit() {
        Map<BasicBlock, FrameSlot[]> map = new HashMap<>();
        for (BasicBlock block : basicBlocks) {
            List<BasicBlock> successors = getSuccessors(block);
            Deque<BasicBlock> currentQueue = new ArrayDeque<>();
            currentQueue.push(block);
            List<BasicBlock> processed = new ArrayList<>();
            List<FrameSlot> frameSlots = new ArrayList<>();
            while (!currentQueue.isEmpty()) {
                BasicBlock currentBlock = currentQueue.pop();
                processed.add(currentBlock);
                boolean dominatesASuccessor = false;
                for (BasicBlock succ : successors) {
                    if (dominates(currentBlock, succ)) {
                        dominatesASuccessor = true;
                    }
                }
                if (!dominatesASuccessor) {
                    frameSlots.addAll(writtenFrameSlotsPerBlock.get(currentBlock));
                    List<BasicBlock> predecessors = getPredecessors(currentBlock);
                    for (BasicBlock pred : predecessors) {
                        if (!processed.contains(pred)) {
                            currentQueue.push(pred);
                        }
                    }
                }
            }
            map.put(block, frameSlots.toArray(new FrameSlot[frameSlots.size()]));
        }
        return map;
    }

    private List<BasicBlock> getPredecessors(BasicBlock block) {
        List<BasicBlock> blocks = new ArrayList<>();
        for (BasicBlock cur : basicBlocks) {
            if (getSuccessors(cur).contains(block)) {
                blocks.add(cur);
            }
        }
        return blocks;
    }

    private boolean dominates(BasicBlock dominator, BasicBlock n) {
        if (dominator.equals(n)) {
            return true;
        }
        List<BasicBlock> notDominatedBlocks = notDominatedBlocks(dominator);
        boolean dominates = !notDominatedBlocks.contains(n);
        return dominates;
    }

    private List<BasicBlock> notDominatedBlocks(BasicBlock dominator) {
        List<BasicBlock> notDominatedBlocks = new ArrayList<>();
        if (!dominator.equals(entryBlock)) {
            notDominatedBlocks(dominator, entryBlock, notDominatedBlocks);
        }
        return notDominatedBlocks;
    }

    private void notDominatedBlocks(BasicBlock dominator, BasicBlock currentBlock, List<BasicBlock> notDominatedBlocks) {
        List<BasicBlock> successors = getSuccessors(currentBlock);
        for (BasicBlock d : successors) {
            if (!notDominatedBlocks.contains(d) && !dominator.equals(d)) {
                notDominatedBlocks.add(d);
                notDominatedBlocks(dominator, d, notDominatedBlocks);
            }
        }
    }

    private Map<BasicBlock, List<BasicBlock>> succesors = new HashMap<>();

    private List<BasicBlock> getSuccessors(BasicBlock block) {
        if (succesors.containsKey(block)) {
            return succesors.get(block);
        } else {
            List<BasicBlock> successors = new ArrayList<>();
            EList<Instruction> instructions = block.getInstructions();
            List<TerminatorInstruction> terminatorInstructions = instructions.stream().filter(i -> i instanceof TerminatorInstruction).map(i -> (TerminatorInstruction) i).collect(Collectors.toList());
            for (TerminatorInstruction termInstr : terminatorInstructions) {
                List<BasicBlock> successorBlocks = getSuccessors(termInstr);
                successors.addAll(successorBlocks);
            }
            succesors.put(block, successors);
            return successors;
        }

    }

    private static List<BasicBlock> getSuccessors(TerminatorInstruction termInstr) {
        List<BasicBlock> bbs = new ArrayList<>();
        EObject realTermInstr = termInstr.getInstruction();
        if (realTermInstr instanceof Instruction_br) {
            Instruction_brImpl br = (Instruction_brImpl) realTermInstr;
            if (br.getUnconditional() != null) {
                bbs.add(br.getUnconditional().getRef());
            } else {
                assert br.getTrue().getRef() != null && br.getFalse().getRef() != null;
                bbs.add(br.getTrue().getRef());
                bbs.add(br.getFalse().getRef());
            }
        } else if (realTermInstr instanceof Instruction_switch) {
            Instruction_switch switchInstr = (Instruction_switch) realTermInstr;
            bbs.add(switchInstr.getDefaultDest().getRef());
            bbs.addAll(switchInstr.getDestinations().stream().map(d -> d.getRef()).collect(Collectors.toList()));
        } else if (realTermInstr instanceof Instruction_indirectbr) {
            Instruction_indirectbr indirectBr = (Instruction_indirectbr) realTermInstr;
            bbs.addAll(indirectBr.getDestinations().stream().map(d -> d.getRef()).collect(Collectors.toList()));
        } else if (realTermInstr instanceof NamedTerminatorInstruction) {
            NamedTerminatorInstruction invoke = (NamedTerminatorInstruction) realTermInstr;
            bbs.add(invoke.getInstruction().getExceptionLabel().getRef());
            bbs.add(invoke.getInstruction().getToLabel().getRef());
        } else if (!hasBasicBlockSuccessor(realTermInstr)) {
            throw new AssertionError(realTermInstr);
        }
        return bbs;
    }

    private static boolean hasBasicBlockSuccessor(EObject realTermInstr) {
        return realTermInstr instanceof Instruction_ret || realTermInstr instanceof Instruction_unreachable;
    }

    private Map<BasicBlock, List<FrameSlot>> getWrittenFrameSlotsPerBlock(EList<BasicBlock> bbs) {
        Map<BasicBlock, List<FrameSlot>> writes = new HashMap<>();
        for (BasicBlock bb : bbs) {
            List<FrameSlot> slots = getWrittenVariables(bb);
            writes.put(bb, slots);
        }
        return writes;
    }

    private List<FrameSlot> getWrittenVariables(BasicBlock block) {
        List<FrameSlot> slots = new ArrayList<>();
        for (Instruction instr : block.getInstructions()) {
            FrameSlot slot = getWrites(instr);
            if (slot != null) {
                slots.add(slot);
            }
        }
        return slots;
    }

    private FrameSlot getWrites(Instruction instr) {
        if (instr instanceof MiddleInstruction) {
            return getWrites((MiddleInstruction) instr);
        } else {
            return null;
        }
    }

    private FrameSlot getWrites(MiddleInstruction instr) {
        EObject realInstr = instr.getInstruction();
        if (realInstr instanceof NamedMiddleInstruction) {
            return getWrites((NamedMiddleInstruction) realInstr);
        } else {
            return null;
        }
    }

    private FrameSlot getWrites(NamedMiddleInstruction namedMiddleInstr) {
        String name = namedMiddleInstr.getName();
        FrameSlot frameSlot = frameDescriptor.findOrAddFrameSlot(name);
        return frameSlot;
    }

}
