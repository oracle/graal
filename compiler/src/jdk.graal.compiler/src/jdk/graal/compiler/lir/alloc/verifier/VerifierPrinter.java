/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.lir.alloc.verifier;

import jdk.graal.compiler.core.common.cfg.BasicBlock;
import jdk.graal.compiler.debug.LogStream;

import java.io.OutputStream;

public class VerifierPrinter {
    public static int PADDING = 4;
    public static int INDENT = 4;

    protected LogStream out;
    protected RegAllocVerifier verifier;

    protected VerifierPrinter(OutputStream out, RegAllocVerifier verifier) {
        this.out = new LogStream(out);
        this.verifier = verifier;
    }

    public void print() {
        int longestRAVInstruction = 0;
        for (var blockId : verifier.lir.getBlocks()) {
            var block = verifier.lir.getBlockById(blockId);

            for (var instruction : verifier.blockInstructions.get(block)) {
                int instructionLength = instruction.toString().length();
                if (instructionLength > longestRAVInstruction) {
                    longestRAVInstruction = instruction.toString().length();
                }
            }
        }

        for (var blockId : verifier.lir.getBlocks()) {
            var block = verifier.lir.getBlockById(blockId);

            printBlockHeader(block);

            out.adjustIndentation(INDENT);
            printEntryState(block);
            for (var instruction : verifier.blockInstructions.get(block)) {
                var instructionString = instruction.toString();
                var difference = longestRAVInstruction - instructionString.length();

                var space = new String(new char[difference + PADDING]).replace("\0", " ");

                String lirInstrString;
                if (instruction.lirInstruction == null) {
                    // Test cases do this, here we just
                    // indicate that it's not part of LIR
                    // and never was.
                    lirInstrString = "<missing lir instruction>";
                } else {
                    lirInstrString = instruction.lirInstruction.toString();
                }

                out.println(instructionString + space + lirInstrString);
                if (instruction instanceof RAVInstruction.Op op) {
                    out.adjustIndentation(INDENT);
                    if (op.lirInstruction.hasState()) {
                        out.println("State: " + op.stateValues);
                    }

                    if (op.references != null) {
                        out.println("References: " + op.references);
                    }
                    out.adjustIndentation(-INDENT);
                }

            }
            out.adjustIndentation(-INDENT);
            out.println();
        }
    }

    protected void printBlockHeader(BasicBlock<?> block) {
        var blockHeaderSB = new StringBuilder();
        blockHeaderSB.append(block.toString()).append(": ");

        if (block.getPredecessorCount() > 0) {
            blockHeaderSB.append(block);
            blockHeaderSB.append(" <- ");
            for (int i = 0; i < block.getPredecessorCount(); i++) {
                blockHeaderSB.append(block.getPredecessorAt(i)).append(", ");
            }

            if (!blockHeaderSB.isEmpty()) {
                blockHeaderSB.setLength(blockHeaderSB.length() - 2);
            }
        }

        if (block.getSuccessorCount() > 0) {
            if (block.getPredecessorCount() > 0) {
                blockHeaderSB.append(" | ");
            }

            blockHeaderSB.append(block);
            blockHeaderSB.append(" -> ");
            for (int i = 0; i < block.getSuccessorCount(); i++) {
                blockHeaderSB.append(block.getSuccessorAt(i)).append(", ");
            }

            if (block.getSuccessorCount() > 0 && !blockHeaderSB.isEmpty()) {
                blockHeaderSB.setLength(blockHeaderSB.length() - 2);
            }
        }

        if (block.isLoopHeader()) {
            blockHeaderSB.append(" | Loop {");
            var loop = block.getLoop();
            for (var member : loop.getBlocks()) {
                blockHeaderSB.append(member);
                blockHeaderSB.append(", ");
            }
            blockHeaderSB.setLength(blockHeaderSB.length() - 2); // always at least one member
            blockHeaderSB.append("}");
        }

        out.println(blockHeaderSB.toString());
    }

    protected void printEntryState(BasicBlock<?> block) {
        var blockVerifierState = verifier.blockEntryStates.get(block);

        if (block.getId() == 0) {
            return;
        }

        out.println("Entry state:");
        out.adjustIndentation(INDENT);
        for (var location : blockVerifierState.values.internalMap.keySet()) {
            var state = blockVerifierState.values.get(location);
            if (state.isUnknown()) {
                continue;
            }
            out.println(location + " -> " + state);
        }
        out.adjustIndentation(-INDENT);
        out.println();
    }

    protected void printAllocationState(RAValue location, AllocationState state) {
        String stateStr = switch (state) {
            case ValueAllocationState st -> {
                if (st.isUndefinedFromBlock()) {
                    // Undefined value from a certain block.
                    yield "Value unknown from " + st.block;
                } else {
                    yield "Value {" + st.getValue() + "} from " + st.source + " in " + st.block;
                }
            }
            case ConflictedAllocationState st -> {
                StringBuilder str = new StringBuilder();
                str.append("Conflicted: \n");
                for (var valueAllocState : st.getConflictedStates()) {
                    if (valueAllocState.isUndefinedFromBlock()) {
                        str.append(" - Value unknown from ").append(valueAllocState.block);
                        continue;
                    }

                    str.append(" - ").append(valueAllocState.getValue()).append(" from ").append(valueAllocState.source).append(" in ").append(valueAllocState.block).append("\n");
                }
                yield str.toString();
            }
            case UnknownAllocationState st -> "Unknown";
            default -> throw new RAVError("Unexpected value: " + state);
        };

        out.println(location + " -> " + stateStr);
    }

    protected void printIRWithException(RAVException exception) {
        out.println("Register Allocation Verification failure:");
        out.println(exception.getMessage());
        out.println("Sourced from " + exception.getLocationString());
        out.println();

        for (var blockId : verifier.lir.getBlocks()) {
            var block = verifier.lir.getBlockById(blockId);

            printBlockHeader(block);
            if (block.equals(exception.block)) {
                out.println("^------------ Exception thrown here");
                out.println();
            }

            out.adjustIndentation(INDENT);
            printEntryState(block);
            for (var instruction : verifier.blockInstructions.get(block)) {
                if (exception.instruction == instruction) {
                    out.println("");
                }

                printInstruction(instruction);
                if (exception.instruction == instruction) {
                    printExceptionInformation(exception);
                }
            }

            if (exception.instruction == null) {
                printExceptionInformation(exception);
            }

            out.adjustIndentation(-INDENT);
            out.println();
        }
    }

    protected void printIRWithMultiExceptions(RAVFailedVerificationException exception) {
        out.println("Register Allocation Verification failure:");
        out.println(exception.getMessage());
        out.println();

        for (var blockId : verifier.lir.getBlocks()) {
            var block = verifier.lir.getBlockById(blockId);

            printBlockHeader(block);

            boolean inBlock = false;
            for (var e : exception.exceptions) {
                if (block.equals(e.block)) {
                    inBlock = true;
                    break;
                }
            }

            if (inBlock) {
                out.println("^------------ Exception thrown here");
                out.println();
            }

            out.adjustIndentation(INDENT);
            printEntryState(block);
            for (var instruction : verifier.blockInstructions.get(block)) {

                boolean inInstruction = false;
                for (var e : exception.exceptions) {
                    if (e.instruction == instruction) {
                        inInstruction = true;
                        break;
                    }
                }

                if (inInstruction) {
                    out.println("");
                }

                printInstruction(instruction);
                for (var e : exception.exceptions) {
                    if (e.instruction == instruction) {
                        printExceptionInformation(e);
                    }
                }
            }

            for (var e : exception.exceptions) {
                if (e.instruction == null) {
                    printExceptionInformation(e);
                }
            }

            out.adjustIndentation(-INDENT);
            out.println();
        }
    }

    protected void printInstruction(RAVInstruction.Base instruction) {
        out.println(instruction.toString());
        if (instruction instanceof RAVInstruction.Op op) {
            out.adjustIndentation(INDENT);
            if (op.lirInstruction.hasState()) {
                out.println("State: " + op.stateValues);
            }

            if (op.references != null) {
                StringBuilder refString = new StringBuilder("References: ");
                for (var reference : op.references) {
                    refString.append(reference.getValue()).append(", ");
                }

                if (!op.references.isEmpty()) {
                    refString.setLength(refString.length() - 2);
                }

                out.println(refString.toString());
            }

            out.adjustIndentation(-INDENT);
        }
    }

    protected void printExceptionInformation(RAVException exception) {
        out.println("^-------- " + exception.getMessage());

        switch (exception) {
            case ValueNotInRegisterException e -> printOtherStates(e);
            case CalleeSavedRegisterNotRetrievedException e -> printCalleeSavedValues(e);
            case MissingReferenceException e -> printOtherReferences(e);
            default -> {
            }
        }

        out.println();
    }

    private void printOtherStates(ValueNotInRegisterException exception) {
        out.println("Other states:");

        var locations = exception.blockVerifierState.values.getValueLocations(exception.variable);
        out.adjustIndentation(INDENT);
        for (var location : locations) {
            var state = exception.blockVerifierState.values.get(location);
            printAllocationState(location, state);
        }
        out.adjustIndentation(-INDENT);
    }

    private void printCalleeSavedValues(CalleeSavedRegisterNotRetrievedException exception) {
        out.println("Callee-saved values:");
        var registers = verifier.registerAllocationConfig.getRegisterConfig().getCalleeSaveRegisters();
        out.adjustIndentation(INDENT);
        for (var reg : registers) {
            var regValue = new RARegister(reg.asValue());
            var state = exception.blockVerifierState.values.get(regValue);
            printAllocationState(regValue, state);
        }
        out.adjustIndentation(-INDENT);
    }

    private void printOtherReferences(MissingReferenceException exception) {
        out.println("Other references:");
        out.adjustIndentation(INDENT);
        for (var location : exception.blockVerifierState.values.internalMap.keySet()) {
            var state = exception.blockVerifierState.values.get(location);
            if (state.isUnknown() || state.isConflicted()) {
                continue;
            }

            var valueAllocState = (ValueAllocationState) state;
            if (valueAllocState.getRAValue().getLIRKind().isValue()) {
                continue;
            }

            // Print all available references.
            printAllocationState(location, state);
        }
        out.adjustIndentation(-INDENT);
    }
}
