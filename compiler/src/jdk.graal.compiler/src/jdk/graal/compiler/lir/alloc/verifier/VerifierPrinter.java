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
import jdk.graal.compiler.core.common.cfg.BlockMap;
import jdk.graal.compiler.lir.LIR;

import java.io.PrintStream;
import java.util.List;

public class VerifierPrinter {
    public static int PADDING = 4;

    /**
     * Print human-readable representation of the Verifier IR to an output stream.
     *
     * @param out Output stream
     * @param lir LIR
     * @param instructions Verifier IR
     */
    public static void print(PrintStream out, LIR lir, BlockMap<List<RAVInstruction.Base>> instructions) {
        for (var blockId : lir.getBlocks()) {
            var block = lir.getBlockById(blockId);

            printBlockHeader(out, block);
            for (var instruction : instructions.get(block)) {
                out.println("\t" + instruction.toString() + " | " + instruction.getLIRInstruction().toString());
                if (instruction instanceof RAVInstruction.Op op) {
                    if (op.lirInstruction.hasState()) {
                        out.println("\t\t State: " + op.stateValues);
                    }

                    if (op.references != null) {
                        out.println("\t\t References: " + op.references);
                    }
                }
            }
            out.println();
        }
    }

    public static void printAligned(PrintStream out, LIR lir, BlockMap<List<RAVInstruction.Base>> instructions) {
        int longestRAVInstruction = 0;
        for (var blockId : lir.getBlocks()) {
            var block = lir.getBlockById(blockId);

            for (var instruction : instructions.get(block)) {
                int instructionLength = instruction.toString().length();
                if (instructionLength > longestRAVInstruction) {
                    longestRAVInstruction = instruction.toString().length();
                }
            }
        }

        for (var blockId : lir.getBlocks()) {
            var block = lir.getBlockById(blockId);

            printBlockHeader(out, block);
            for (var instruction : instructions.get(block)) {
                var instructionString = instruction.toString();
                var difference = longestRAVInstruction - instructionString.length();

                var space = new String(new char[difference + PADDING]).replace("\0", " ");

                out.println("\t" + instructionString + space + instruction.lirInstruction.toString());
                if (instruction instanceof RAVInstruction.Op op) {
                    if (op.lirInstruction.hasState()) {
                        out.println("\t\t State: " + op.stateValues);
                    }

                    if (op.references != null) {
                        out.println("\t\t References: " + op.references);
                    }
                }

            }
            out.println();
        }
    }

    public static void printNumbered(PrintStream out, LIR lir, BlockMap<List<RAVInstruction.Base>> instructions) {
        for (var blockId : lir.getBlocks()) {
            var block = lir.getBlockById(blockId);

            printBlockHeader(out, block);
            int n = 1;
            for (var instruction : instructions.get(block)) {
                out.println("\t" + n + "." + instruction.toString());
                if (instruction instanceof RAVInstruction.Op op) {
                    if (op.lirInstruction.hasState()) {
                        out.println("\t\t State: " + op.stateValues);
                    }

                    if (op.references != null) {
                        out.println("\t\t References: " + op.references);
                    }
                }

                n++;
            }

            out.println();

            n = 1;
            for (var instruction : instructions.get(block)) {
                out.println("\t" + n + "." + instruction.lirInstruction.toString());
                n++;
            }
            out.println();
        }
    }

    protected static void printBlockHeader(PrintStream out, BasicBlock<?> block) {
        var blockHeaderSB = new StringBuilder();
        blockHeaderSB.append(block.toString()).append(": ");

        if (block.getPredecessorCount() > 0) {
            blockHeaderSB.append(" <- ");
            for (int i = 0; i < block.getPredecessorCount(); i++) {
                blockHeaderSB.append(block.getPredecessorAt(i)).append(", ");
            }

            if (!blockHeaderSB.isEmpty()) {
                blockHeaderSB.setLength(blockHeaderSB.length() - 2);
            }
        }

        if (block.getSuccessorCount() > 0) {
            blockHeaderSB.append(" -> ");
            for (int i = 0; i < block.getSuccessorCount(); i++) {
                blockHeaderSB.append(block.getSuccessorAt(i)).append(", ");
            }

            if (block.getSuccessorCount() > 0 && !blockHeaderSB.isEmpty()) {
                blockHeaderSB.setLength(blockHeaderSB.length() - 2);
            }
        }

        out.println(blockHeaderSB);
    }
}
