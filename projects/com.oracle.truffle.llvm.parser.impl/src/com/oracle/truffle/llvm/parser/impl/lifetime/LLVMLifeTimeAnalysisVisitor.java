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
package com.oracle.truffle.llvm.parser.impl.lifetime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import com.intel.llvm.ireditor.lLVM_IR.StartingInstruction;
import com.intel.llvm.ireditor.lLVM_IR.TerminatorInstruction;
import com.intel.llvm.ireditor.lLVM_IR.impl.Instruction_brImpl;
import com.intel.llvm.ireditor.lLVM_IR.impl.LocalValueRefImpl;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.llvm.parser.base.util.LLVMParserAsserts;
import com.oracle.truffle.llvm.parser.impl.LLVMPhiVisitor;
import com.oracle.truffle.llvm.parser.impl.LLVMPhiVisitor.Phi;
import com.oracle.truffle.llvm.parser.impl.LLVMReadVisitor;
import com.oracle.truffle.llvm.runtime.options.LLVMBaseOptionFacade;

/**
 * This class determines which variables are dead after each basic block. It applies an iterative
 * data-flow analysis to determine the lifetimes.
 *
 */
public final class LLVMLifeTimeAnalysisVisitor {

    private static final String FUNCTION_FORMAT = "%s:\n";
    private static final String AFTER_BLOCK_FORMAT = "\t dead after bb %4s:";

    private final FrameDescriptor frameDescriptor;
    private final EList<BasicBlock> basicBlocks;
    private final Map<BasicBlock, List<Phi>> phiRefs;

    private LLVMLifeTimeAnalysisVisitor(FunctionDef function, FrameDescriptor frameDescriptor) {
        this.frameDescriptor = frameDescriptor;
        basicBlocks = function.getBasicBlocks();
        phiRefs = LLVMPhiVisitor.visit(function);
    }

    static class LifeTimeAnalysisResultImpl extends LLVMLifeTimeAnalysisResult {

        LifeTimeAnalysisResultImpl(Map<BasicBlock, FrameSlot[]> beginDead, Map<BasicBlock, FrameSlot[]> endDead) {
            this.beginDead = beginDead;
            this.endDead = endDead;
        }

        @Override
        public Map<BasicBlock, FrameSlot[]> getBeginDead() {
            return beginDead;
        }

        @Override
        public Map<BasicBlock, FrameSlot[]> getEndDead() {
            return endDead;
        }

        private final Map<BasicBlock, FrameSlot[]> beginDead;
        private final Map<BasicBlock, FrameSlot[]> endDead;

    }

    public static LLVMLifeTimeAnalysisResult visit(FunctionDef function, FrameDescriptor frameDescriptor) {
        LLVMParserAsserts.assertNoNullElement(frameDescriptor.getSlots());
        LifeTimeAnalysisResultImpl mapping = new LLVMLifeTimeAnalysisVisitor(function, frameDescriptor).visit();
        if (LLVMBaseOptionFacade.printLifeTimeAnalysis()) {
            printAnalysisResults(function, mapping.getEndDead());
        }
        return mapping;
    }

    /**
     * The variable definitions per instruction (the last instruction can have several.
     */
    private final Map<Instruction, Set<FrameSlot>> defs = new HashMap<>();
    /**
     * The (transitive) inputs of each instruction.
     */
    private final Map<Instruction, Set<FrameSlot>> in = new HashMap<>();
    /**
     * The (transitive) outputs of each instruction.
     */
    private final Map<Instruction, Set<FrameSlot>> out = new HashMap<>();

    /**
     * Instruction visitor that skips phi nodes (StartingInstruction) since we replace phi nodes by
     * writes at the basic blocks that the phi nodes reference.
     */
    static final class LLVMInstructionIterator implements Iterator<Instruction> {

        private int cur;
        private final EList<Instruction> instructions;

        LLVMInstructionIterator(BasicBlock block) {
            this.instructions = block.getInstructions();
        }

        @Override
        public boolean hasNext() {
            return cur < instructions.size();
        }

        @Override
        public Instruction next() {
            Instruction nextInstr;
            do {
                nextInstr = instructions.get(cur++);
            } while (nextInstr instanceof StartingInstruction);
            return nextInstr;
        }

        public Instruction peek() {
            Instruction peekInstr;
            int i = cur;
            do {
                peekInstr = instructions.get(i++);
            } while (peekInstr instanceof StartingInstruction);
            return peekInstr;
        }

    }

    private LifeTimeAnalysisResultImpl visit() {
        initializeInstructionReads();
        initializeInstructionInOuts();
        initializeVariableDefinitions();
        findFixpoint();
        getInstructionKills(bbEndKills);
        Map<BasicBlock, FrameSlot[]> endKills = convertInstructionKillsToBasicBlockKills();
        Map<BasicBlock, FrameSlot[]> beginKills = new HashMap<>();
        for (BasicBlock block : basicBlocks) {
            Set<FrameSlot> bbBegin = bbBeginKills.get(block);
            beginKills.put(block, bbBegin.toArray(new FrameSlot[bbBegin.size()]));
        }
        return new LifeTimeAnalysisResultImpl(beginKills, endKills);
    }

    private Map<Instruction, List<FrameSlot>> instructionReads = new HashMap<>();
    private Map<TerminatorInstruction, List<BasicBlock>> successorBlocks = new HashMap<>();
    private Map<Instruction, Set<FrameSlot>> bbEndKills;
    private Map<BasicBlock, Set<FrameSlot>> bbBeginKills;

    private void initializeInstructionReads() {
        for (BasicBlock bb : basicBlocks) {
            LLVMInstructionIterator it = new LLVMInstructionIterator(bb);
            while (it.hasNext()) {
                Instruction instr = it.next();
                if (instr instanceof TerminatorInstruction) {
                    successorBlocks.put((TerminatorInstruction) instr, getSuccessorBlocks((TerminatorInstruction) instr));
                }
                List<FrameSlot> currentInstructionReads = new LLVMReadVisitor().getReads(instr, frameDescriptor, false);
                instructionReads.put(instr, currentInstructionReads);
            }
        }
    }

    private void initializeInstructionInOuts() {
        bbEndKills = new HashMap<>();
        bbBeginKills = new HashMap<>();
        for (BasicBlock bb : basicBlocks) {
            bbBeginKills.put(bb, new HashSet<>());
            List<Phi> bbPhis = phiRefs.get(bb);
            EList<Instruction> bbInstructions = bb.getInstructions();
            for (Instruction instr : bbInstructions) {
                bbEndKills.put(instr, new HashSet<>());
            }
            LLVMInstructionIterator it = new LLVMInstructionIterator(bb);
            while (it.hasNext()) {
                Instruction instr = it.next();
                // in[n] = use[n]
                // variables inside phi instructions do not have usages since they are actually
                // written before
                Set<FrameSlot> uses = new HashSet<>(instructionReads.get(instr));
                // so we have to add the usage of the phi instructions were they are written (at the
                // last instruction of a block)
                if (!it.hasNext()) {
                    for (Phi phi : bbPhis) {
                        LocalValueRefImpl localVariablesInPhi = phi.getLocalVariablesInPhi(bb);
                        if (localVariablesInPhi != null) {
                            uses.add(frameDescriptor.findOrAddFrameSlot(localVariablesInPhi.getRef().getName()));
                        }
                    }
                }
                in.put(instr, uses);
                out.put(instr, new HashSet<>());
            }
        }
    }

    private void initializeVariableDefinitions() {
        // update def
        for (BasicBlock bb : basicBlocks) {
            LLVMInstructionIterator it = new LLVMInstructionIterator(bb);
            while (it.hasNext()) {
                Instruction instr = it.next();
                Set<FrameSlot> instructionDefs = new HashSet<>();
                if (instr instanceof MiddleInstruction) {
                    if (((MiddleInstruction) instr).getInstruction() instanceof NamedMiddleInstruction) {
                        NamedMiddleInstruction namedMiddleInstruction = (NamedMiddleInstruction) ((MiddleInstruction) instr).getInstruction();
                        assert !(namedMiddleInstruction instanceof StartingInstruction) : "do not handle phis here!";
                        FrameSlot defFrameSlot = frameDescriptor.findOrAddFrameSlot(namedMiddleInstruction.getName());
                        instructionDefs.add(defFrameSlot);
                    }
                } else if (instr instanceof StartingInstruction) {
                    StartingInstruction startInstr = (StartingInstruction) instr;
                    instructionDefs.add(frameDescriptor.findOrAddFrameSlot(startInstr.getName()));
                }
                defs.put(instr, instructionDefs);
            }
        }
    }

    private Map<BasicBlock, FrameSlot[]> convertInstructionKillsToBasicBlockKills() {
        Map<BasicBlock, FrameSlot[]> convertedMap = new HashMap<>();
        for (BasicBlock bb : basicBlocks) {
            List<FrameSlot> blockKills = new ArrayList<>();
            LLVMInstructionIterator it = new LLVMInstructionIterator(bb);
            while (it.hasNext()) {
                blockKills.addAll(bbEndKills.get(it.next()));
            }
            FrameSlot[] blockKillArr = blockKills.toArray(new FrameSlot[blockKills.size()]);
            convertedMap.put(bb, blockKillArr);
        }
        return convertedMap;
    }

    private void getInstructionKills(Map<Instruction, Set<FrameSlot>> kills) {
        for (BasicBlock bb : basicBlocks) {
            LLVMInstructionIterator it = new LLVMInstructionIterator(bb);
            while (it.hasNext()) {
                Instruction instr = it.next();
                Set<FrameSlot> inSlots = in.get(instr);
                Set<FrameSlot> outSlots = out.get(instr);
                Set<FrameSlot> instructionKills = new HashSet<>(inSlots);
                instructionKills.removeAll(outSlots);
                if (instr instanceof TerminatorInstruction) {
                    for (BasicBlock bas : successorBlocks.get(instr)) {
                        Instruction firstInstruction = new LLVMInstructionIterator(bas).next();
                        Set<FrameSlot> deadAtBegin = new HashSet<>(out.get(instr));
                        deadAtBegin.removeAll(in.get(firstInstruction));
                        bbBeginKills.put(bas, deadAtBegin);
                    }
                }
                kills.put(instr, instructionKills);
            }
        }
    }

    /**
     * Applies an iterative data-flow analysis for analyzing the lifetimes.
     */
    private void findFixpoint() {
        boolean changed;
        List<BasicBlock> reversedBlocks = new ArrayList<>(basicBlocks);
        Collections.reverse(reversedBlocks);
        do {
            changed = false;
            for (BasicBlock block : reversedBlocks) {
                LLVMInstructionIterator it = new LLVMInstructionIterator(block);
                while (it.hasNext()) {
                    Instruction instr = it.next();
                    // update out
                    if (instr instanceof TerminatorInstruction) {
                        // non sequential successor
                        // out[n] = in[n+1, n+2, ...]
                        List<BasicBlock> nextInstructions = successorBlocks.get(instr);
                        for (BasicBlock nextBlock : nextInstructions) {
                            Instruction nextInstr = new LLVMInstructionIterator(nextBlock).next();
                            Set<FrameSlot> nextIn = in.get(nextInstr);
                            Set<FrameSlot> addTo = out.get(instr);
                            changed |= addFrameSlots(addTo, nextIn);
                        }
                    } else {
                        // out[n] = in[n + 1]
                        Instruction nextInstr = it.peek();
                        Set<FrameSlot> nextIn = in.get(nextInstr);
                        Set<FrameSlot> addTo = out.get(instr);
                        changed |= addFrameSlots(addTo, nextIn);
                    }
                    // update in
                    Set<FrameSlot> outWithoutDefs = new HashSet<>(out.get(instr));
                    List<FrameSlot> realDefs = new ArrayList<>(defs.get(instr));
                    outWithoutDefs.removeAll(realDefs);
                    changed |= addFrameSlots(in.get(instr), outWithoutDefs);
                }
            }
        } while (changed);
    }

    private static boolean addFrameSlots(Set<FrameSlot> addTo, Set<FrameSlot> add) {
        return addTo.addAll(add);
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

    private static List<BasicBlock> getSuccessorBlocks(TerminatorInstruction termInstr) {
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

}
