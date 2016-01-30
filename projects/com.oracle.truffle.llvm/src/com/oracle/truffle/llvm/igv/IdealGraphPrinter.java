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
package com.oracle.truffle.llvm.igv;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.xtext.resource.XtextResource;
import org.eclipse.xtext.resource.XtextResourceSet;

import com.google.inject.Injector;
import com.intel.llvm.ireditor.LLVM_IRStandaloneSetup;
import com.intel.llvm.ireditor.lLVM_IR.BasicBlock;
import com.intel.llvm.ireditor.lLVM_IR.FunctionDef;
import com.intel.llvm.ireditor.lLVM_IR.Instruction;
import com.intel.llvm.ireditor.lLVM_IR.Instruction_alloca;
import com.intel.llvm.ireditor.lLVM_IR.Instruction_call_nonVoid;
import com.intel.llvm.ireditor.lLVM_IR.Instruction_load;
import com.intel.llvm.ireditor.lLVM_IR.Instruction_ret;
import com.intel.llvm.ireditor.lLVM_IR.Instruction_store;
import com.intel.llvm.ireditor.lLVM_IR.MiddleInstruction;
import com.intel.llvm.ireditor.lLVM_IR.Model;
import com.intel.llvm.ireditor.lLVM_IR.NamedMiddleInstruction;
import com.intel.llvm.ireditor.lLVM_IR.TerminatorInstruction;

public class IdealGraphPrinter extends IdealGraphPrinterBase {

    private static final int ID_START = 1000;

    public IdealGraphPrinter(PrintStream stream) {
        super(stream);
    }

    public static void main(String[] args) throws FileNotFoundException {
        LLVM_IRStandaloneSetup setup = new LLVM_IRStandaloneSetup();
        Injector injector = setup.createInjectorAndDoEMFRegistration();
        XtextResourceSet resourceSet = injector.getInstance(XtextResourceSet.class);
        resourceSet.addLoadOption(XtextResource.OPTION_RESOLVE_ALL, Boolean.TRUE);
        Resource resource = resourceSet.getResource(URI.createURI(args[0]), true);
        EList<EObject> contents = resource.getContents();
        if (contents.size() == 0) {
            throw new IllegalStateException("empty file?");
        }
        Model model = (Model) contents.get(0);
        List<EObject> objects = model.eContents();
        PrintStream printStream = new PrintStream(new FileOutputStream(new File(args[1])));
        new IdealGraphPrinter(printStream).printGraph(objects);
    }

    private static int id = ID_START;

    private Map<Instruction, Integer> idMap = new HashMap<>();

    private int getId(Instruction instr) {
        if (idMap.containsKey(instr)) {
            return idMap.get(instr);
        } else {
            int newId = id++;
            idMap.put(instr, newId);
            return newId;
        }
    }

    private void visitBlock(BasicBlock block) {
        for (Instruction instr : block.getInstructions()) {
            visitInstruction(instr);
        }
    }

    private void visitInstruction(Instruction instr) {
        beginNode(Integer.toString(getId(instr)));
        beginProperties();
        if (instr instanceof MiddleInstruction) {
            visitMiddleInstruction((MiddleInstruction) instr);
        } else if (instr instanceof TerminatorInstruction) {
            visitTerminatorInstruction((TerminatorInstruction) instr);
        }
        endProperties();
        endNode();
    }

    private void visitTerminatorInstruction(TerminatorInstruction instruction) {
        EObject instr = instruction.getInstruction();
        if (instr instanceof Instruction_ret) {
            visitRetInstruction((Instruction_ret) instr);
        } else {
            throw new AssertionError(instruction);
        }
    }

    private void visitRetInstruction(@SuppressWarnings("unused") Instruction_ret instruction) {
        printInstructionName("ret");
    }

    private void visitMiddleInstruction(MiddleInstruction instr) {
        EObject instruction = instr.getInstruction();
        if (instruction instanceof NamedMiddleInstruction) {
            visitNamedMiddleInstruction((NamedMiddleInstruction) instruction);
        } else if (instruction instanceof Instruction_store) {
            visitStoreInstruction((Instruction_store) instruction);
        } else if (instruction instanceof Instruction_load) {
            visitLoadInstruction((Instruction_load) instruction);
        } else {
            throw new AssertionError(instr);
        }
    }

    private void printInstructionName(String instructionName) {
        printProperty("name", instructionName);
    }

    private void visitLoadInstruction(@SuppressWarnings("unused") Instruction_load instruction) {
        printInstructionName("load");
    }

    private void visitStoreInstruction(@SuppressWarnings("unused") Instruction_store instruction) {
        printInstructionName("store");
    }

    private void visitNamedMiddleInstruction(NamedMiddleInstruction instr) {
        printProperty("register", instr.getName());
        EObject instruction = instr.getInstruction();
        if (instruction instanceof Instruction_alloca) {
            printInstructionName("alloca");
        } else if (instruction instanceof Instruction_load) {
            printInstructionName("load");
        } else if (instruction instanceof Instruction_call_nonVoid) {
            printInstructionName("call");
        } else {
            throw new AssertionError();
        }
    }

    private void printGraph(List<EObject> objects) {
        beginGraphDocument();
        beginGroup();
        beginProperties();
        printProperty("name", "file");
        endProperties();
        for (EObject object : objects) {
            if (object instanceof FunctionDef) {
                visitFunctionDef((FunctionDef) object);
            }
        }
        endGroup();
        endGraphDocument();
    }

    private void visitFunctionDef(FunctionDef function) {
        beginGraph(function.getHeader().getName());
        visitBasicBlocks(function.getBasicBlocks());
        endGraph();
    }

    private void visitBasicBlocks(EList<BasicBlock> basicBlocks) {
        beginNodes();
        for (BasicBlock block : basicBlocks) {
            visitBlock(block);
        }
        endNodes();
    }
}
