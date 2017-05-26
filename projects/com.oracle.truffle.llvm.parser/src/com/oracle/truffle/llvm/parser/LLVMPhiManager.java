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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.oracle.truffle.llvm.parser.model.ModelModule;
import com.oracle.truffle.llvm.parser.model.blocks.InstructionBlock;
import com.oracle.truffle.llvm.parser.model.functions.FunctionDeclaration;
import com.oracle.truffle.llvm.parser.model.functions.FunctionDefinition;
import com.oracle.truffle.llvm.parser.model.globals.GlobalAlias;
import com.oracle.truffle.llvm.parser.model.globals.GlobalConstant;
import com.oracle.truffle.llvm.parser.model.globals.GlobalVariable;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.PhiInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.TerminatingInstruction;
import com.oracle.truffle.llvm.parser.model.visitors.FunctionVisitor;
import com.oracle.truffle.llvm.parser.model.visitors.InstructionVisitorAdapter;
import com.oracle.truffle.llvm.parser.model.visitors.ModelVisitor;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.runtime.types.symbols.Symbol;
import com.oracle.truffle.llvm.runtime.types.symbols.ValueSymbol;

public final class LLVMPhiManager implements ModelVisitor {

    static LLVMPhiManager generate(ModelModule model) {
        LLVMPhiManager visitor = new LLVMPhiManager();

        model.accept(visitor);

        return visitor;
    }

    private final Map<String, Map<InstructionBlock, List<Phi>>> edges = new HashMap<>();

    private LLVMPhiManager() {
    }

    public Map<InstructionBlock, List<Phi>> getPhiMap(String method) {
        Map<InstructionBlock, List<Phi>> references = edges.get(method);
        if (references == null) {
            return Collections.emptyMap();
        } else {
            return references;
        }
    }

    @Override
    public void visit(GlobalAlias alias) {
    }

    @Override
    public void visit(GlobalConstant constant) {
    }

    @Override
    public void visit(GlobalVariable variable) {
    }

    @Override
    public void visit(FunctionDeclaration method) {
    }

    @Override
    public void visit(FunctionDefinition method) {
        LLVMPhiManagerFunctionVisitor visitor = new LLVMPhiManagerFunctionVisitor();

        method.accept(visitor);

        edges.put(method.getName(), visitor.getEdges());
    }

    @Override
    public void visit(Type type) {
    }

    private static class LLVMPhiManagerFunctionVisitor implements FunctionVisitor, InstructionVisitorAdapter {

        private final Map<InstructionBlock, List<Phi>> edges = new HashMap<>();

        private InstructionBlock currentBlock = null;

        LLVMPhiManagerFunctionVisitor() {
        }

        public Map<InstructionBlock, List<Phi>> getEdges() {
            return edges;
        }

        @Override
        public void visit(InstructionBlock block) {
            this.currentBlock = block;
            block.accept(this);
        }

        @Override
        public void visit(PhiInstruction phi) {
            for (int i = 0; i < phi.getSize(); i++) {
                InstructionBlock blk = phi.getBlock(i);
                List<Phi> references = edges.computeIfAbsent(blk, k -> new ArrayList<>());
                references.add(new Phi(currentBlock, phi, phi.getValue(i)));
            }
        }
    }

    static final class Phi {

        private final InstructionBlock block;

        private final ValueSymbol phi;

        private final Symbol value;

        private Phi(InstructionBlock block, ValueSymbol phi, Symbol value) {
            this.block = block;
            this.phi = phi;
            this.value = value;
        }

        public InstructionBlock getBlock() {
            return block;
        }

        public ValueSymbol getPhiValue() {
            return phi;
        }

        public Symbol getValue() {
            return value;
        }
    }

    public static ArrayList<Phi>[] getPhisForSuccessors(TerminatingInstruction terminatingInstruction, List<Phi> phis) {
        assert phis != null;

        @SuppressWarnings("unchecked")
        ArrayList<Phi>[] phisPerSuccessor = new ArrayList[terminatingInstruction.getSuccessorCount()];
        for (int i = 0; i < phisPerSuccessor.length; i++) {
            phisPerSuccessor[i] = new ArrayList<>();
        }

        for (Phi phi : phis) {
            assignPhiToSuccessor(terminatingInstruction, phi, phisPerSuccessor);
        }
        return phisPerSuccessor;
    }

    private static void assignPhiToSuccessor(TerminatingInstruction terminatingInstruction, Phi phi, ArrayList<Phi>[] phisPerSuccessor) {
        for (int i = 0; i < terminatingInstruction.getSuccessorCount(); i++) {
            if (terminatingInstruction.getSuccessor(i) == phi.getBlock()) {
                ArrayList<Phi> phis = phisPerSuccessor[i];
                if (!hasMatchingPhi(phis, phi)) {
                    phis.add(phi);
                    return;
                }
            }
        }
        throw new RuntimeException("Could not find a matching successor for a phi.");
    }

    private static boolean hasMatchingPhi(ArrayList<Phi> possiblePhiList, Phi phi) {
        for (int j = 0; j < possiblePhiList.size(); j++) {
            if (possiblePhiList.get(j).getPhiValue() == phi.getPhiValue()) {
                // this successor already has a phi that corresponds to the same phi symbol -> it
                // can't be for that successor. this case happens when we have the same successor
                // block multiple times in the list of the successors.
                return true;
            }
        }
        return false;
    }
}
