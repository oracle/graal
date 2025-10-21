/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates. All rights reserved.
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
/*
 * Copyright (c) 2009, 2021, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */
package com.oracle.max.hcfdis;

import static com.oracle.max.hcfdis.HexCodeFile.EMBEDDED_HCF_CLOSE;
import static com.oracle.max.hcfdis.HexCodeFile.EMBEDDED_HCF_OPEN;
import static com.oracle.max.hcfdis.HexCodeFileDis.Width.ONE;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Formatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.oracle.max.hcfdis.HexCodeFile.JumpTable;
import com.oracle.max.hcfdis.HexCodeFile.JumpTable.EntryFormat;

import capstone.Capstone;

/**
 * Utility for converting a {@link HexCodeFile} to a commented disassembly.
 *
 * The fully qualified name of {@link #processEmbeddedString(String)} must not change as it's called
 * directly by C1Visualizer.
 *
 * @see "https://ol-bitbucket.us.oracle.com/projects/G/repos/c1visualizer/browse/C1Visualizer/Native%20Code%20Editor/src/at/ssw/visualizer/nc/model/HexCodeFileSupport.java#6"
 */
public class HexCodeFileDis {

    public HexCodeFileDis() {
    }

    /**
     * Decoding method called by external tools via reflection.
     */
    public static String processEmbeddedString(String source) {
        if (!source.startsWith(EMBEDDED_HCF_OPEN) || !source.endsWith(EMBEDDED_HCF_CLOSE)) {
            throw new IllegalArgumentException("Input string is not in embedded format");
        }
        String input = source.substring(EMBEDDED_HCF_OPEN.length(), source.length() - EMBEDDED_HCF_CLOSE.length());
        HexCodeFile hcf = HexCodeFile.parse(input, 0, input, "");
        if (hcf == null) {
            throw new InternalError("Malformed HexCodeFile embedded in string");
        }

        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        process(hcf, new PrintStream(buf), true);
        return buf.toString();
    }

    /**
     * Disassembles all HexCodeFiles embedded in a given input string.
     *
     * @param input some input containing 0 or more HexCodeFiles
     * @param inputName name for the input source to be used in error messages
     * @param startDelim the delimiter just before to an embedded HexCodeFile in {@code input}
     * @param endDelim the delimiter just after to an embedded HexCodeFile in {@code input}
     * @return the value of {@code input} with all embedded HexCodeFiles converted to their
     *         disassembled form
     */
    public static String processAll(String input, String inputName, String startDelim, String endDelim, boolean showComments) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(input.length() * 2);
        PrintStream out = new PrintStream(baos);
        int hcfCount = 0;

        int codeEnd = 0;
        int index;
        while ((index = input.indexOf(startDelim, codeEnd)) != -1) {
            int codeStart = index + startDelim.length();

            String copy = input.substring(codeEnd, codeStart);
            if (copy.startsWith(endDelim)) {
                copy = copy.substring(endDelim.length());
            }
            if (copy.endsWith(startDelim)) {
                copy = copy.substring(0, copy.length() - startDelim.length());
            }
            out.println(copy);

            int endIndex = input.indexOf(endDelim, codeStart);
            assert endIndex != -1;

            String source = input.substring(codeStart, endIndex);

            HexCodeFile hcf = HexCodeFile.parse(input, codeStart, source, inputName);
            if (hcf == null) {
                throw new InternalError("Malformed HexCodeFile in " + inputName);
            }
            process(hcf, out, showComments);
            hcfCount++;
            codeEnd = endIndex;
        }

        String copy = input.substring(codeEnd);
        if (copy.startsWith(endDelim)) {
            copy = copy.substring(endDelim.length());
        }

        System.out.println(inputName + ": disassembled " + hcfCount + " embedded HexCodeFiles");
        out.print(copy);
        out.flush();
        return baos.toString();
    }

    /**
     * Computes the max width of a column based on values to be be printed in the column.
     */
    static class Width {
        static final Width ONE = new Width(1);

        Width(int initialValue) {
            this.value = initialValue;
        }

        void update(int i, int radix) {
            assert this != ONE;
            update(Integer.toString(i, radix));
        }

        void update(String s) {
            assert this != ONE;
            int len = s.length();
            if (len > value) {
                value = len;
            }
        }

        @Override
        public String toString() {
            return String.valueOf(value);
        }

        int value;
    }

    /**
     * An entry in a jump table in the decoded instruction stream.
     */
    static class JumpTableEntry {

        JumpTableEntry(int offset, long address, int primaryKey, Integer secondaryKey, long target) {
            this.offset = offset;
            this.address = address;
            this.primaryKey = primaryKey;
            this.secondaryKey = secondaryKey;
            this.target = target;
        }

        final int offset;
        final long address;
        final int primaryKey;
        final Integer secondaryKey;

        /**
         * Initially a {@code long}, replaced with an {@link Instruction} by
         * {@link HexCodeFileDis#updateTargets}.
         */
        Object target;

        @Override
        public String toString() {
            return toString(ONE, ONE, ONE, ONE);
        }

        String toString(Width offsetWidth, Width labelWidth, Width primaryKeyWidth, Width secondaryKeyWidth) {
            String secondaryKeyString = secondaryKey == null ? "" : String.format(", %-" + secondaryKeyWidth + "s", secondaryKey);
            String targetString;
            if (target instanceof Instruction) {
                Instruction targetInstruction = (Instruction) target;
                targetString = targetInstruction.labeledAddress();
            } else {
                targetString = String.valueOf(target);
            }
            return String.format("0x%08x(+0x%-" + offsetWidth + "x): %" + labelWidth + "s .case %-" + primaryKeyWidth + "d%s -> %s",
                            address,
                            offset,
                            "",
                            primaryKey,
                            secondaryKeyString,
                            targetString);
        }
    }

    /**
     * A decoded instruction.
     */
    static class Instruction {
        Instruction(int offset, Capstone.CsInsn insn, String operandComment, List<String> comments) {
            this.offset = offset;
            this.insn = insn;
            this.operandComment = operandComment;
            this.comments = comments;
        }

        final int offset;
        final Capstone.CsInsn insn;
        final String operandComment;
        final List<String> comments;

        /**
         * The entries of the jump table after this instruction.
         */
        final List<JumpTableEntry> jumpTable = new ArrayList<>();

        /**
         * The label of this instruction if it is targeted by a jump instruction. Otherwise,
         * {@code -1}.
         */
        int label = -1;

        /**
         * The instruction targeted by this instruction if it is a jump.
         */
        Instruction target;

        String labeledAddress() {
            return String.format("L%d: 0x%x", label, insn.address);
        }

        @Override
        public String toString() {
            return toString(ONE, ONE, true);
        }

        String toString(Width offsetWidth, Width labelWidth, boolean showComments) {
            Formatter buf = new Formatter();
            String l = label == -1 ? "" : "L" + label + ":";
            if (showComments && comments != null) {
                for (String comment : comments) {
                    final String commentLinePrefix = ";; ";
                    buf.format("%s%s%n", commentLinePrefix, comment.replace("\n", "\n" + commentLinePrefix));
                }
            }
            Object operand = target == null ? insn.opStr : target.labeledAddress();
            buf.format("0x%08x(+0x%-" + offsetWidth + "x): %" + labelWidth + "s %s\t%s%s", insn.address, offset, l, insn.mnemonic, operand, showComments ? operandComment : "");
            Width primaryKeyWidth = new Width(1);
            Width secondaryKeyWidth = new Width(1);
            for (JumpTableEntry c : jumpTable) {
                primaryKeyWidth.update(c.primaryKey, 10);
                if (c.secondaryKey != null) {
                    secondaryKeyWidth.update(c.secondaryKey, 10);
                }
            }
            for (JumpTableEntry c : jumpTable) {
                buf.format("\n%s", c.toString(offsetWidth, labelWidth, primaryKeyWidth, secondaryKeyWidth));
            }
            return buf.toString();
        }
    }

    /**
     * Disassembles a given HexCodeFile.
     *
     * @param out where the HexCodeFile disassembly is printed
     */
    public static void process(HexCodeFile hcf, PrintStream out, boolean showComments) {
        Capstone cs;
        boolean littleEndian;

        switch (hcf.isa.toLowerCase()) {
            case "amd64":
                littleEndian = true;
                cs = new Capstone(Capstone.CS_ARCH_X86, Capstone.CS_MODE_64);
                break;
            case "aarch64":
                littleEndian = true;
                cs = new Capstone(Capstone.CS_ARCH_ARM64, Capstone.CS_MODE_ARM);
                break;
            default:
                throw new IllegalArgumentException("Unexpected ISA: " + hcf.isa);
        }

        // Ordered map from offset to decoded instructions
        Map<Integer, Instruction> instructionMap = new LinkedHashMap<>();

        if (hcf.jumpTables.size() == 0) {
            for (Instruction i : disassemble(hcf, cs, 0, hcf.code)) {
                instructionMap.put(i.offset, i);
            }
        } else {
            // Break the disassembly into chunks separated by jump tables
            int startOffset = 0;
            for (JumpTable jumpTable : hcf.jumpTables) {
                int jumpTableOffset = jumpTable.getPosition();
                List<Instruction> insns = disassembleAtOffset(hcf, cs, startOffset, jumpTableOffset);
                for (Instruction i : insns) {
                    instructionMap.put(i.offset, i);
                }
                EntryFormat entryFormat = jumpTable.entryFormat;
                int entrySize = entryFormat.size;
                Instruction lastInsn = insns.get(insns.size() - 1);
                long entries = Math.abs(jumpTable.high - (long) jumpTable.low);
                for (int i = 0; i <= entries; i++) {
                    int entryOffset = jumpTable.getPosition() + i * entrySize;
                    Integer secondaryKey;
                    int targetOffsetRelativeToJumpTable;
                    switch (jumpTable.entryFormat) {
                        case OFFSET:
                            secondaryKey = null;
                            targetOffsetRelativeToJumpTable = readInt(littleEndian, hcf.code, entryOffset);
                            break;
                        case KEY2_OFFSET:
                            secondaryKey = readInt(littleEndian, hcf.code, entryOffset);
                            targetOffsetRelativeToJumpTable = readInt(littleEndian, hcf.code, entryOffset + 4);
                            break;
                        default:
                            throw new IllegalArgumentException("Unknown jump table entry format: " + jumpTable.entryFormat);
                    }
                    int primaryKey = jumpTable.low + i;
                    long target = hcf.startAddress + jumpTableOffset + targetOffsetRelativeToJumpTable;
                    lastInsn.jumpTable.add(new JumpTableEntry(entryOffset, hcf.startAddress + entryOffset, primaryKey, secondaryKey, target));

                }
                long newStartOffset = jumpTableOffset + entrySize * (jumpTable.high - (long) jumpTable.low + 1);
                startOffset = (int) newStartOffset;
                assert startOffset == newStartOffset;
            }
            if (startOffset < hcf.code.length) {
                for (Instruction i : disassembleAtOffset(hcf, cs, startOffset, hcf.code.length)) {
                    instructionMap.put(i.offset, i);
                }
            }
        }

        Width labelWidth = new Width(1);
        Width offsetWidth = new Width(1);

        Instruction[] instructions = instructionMap.values().toArray(new Instruction[0]);
        if (instructions.length > 0) {
            long firstInsnAddress = instructions[0].insn.address;
            long lastInsnAddress = instructions[instructions.length - 1].insn.address;
            Map<Long, Instruction> targets = new HashMap<>();
            for (Instruction i : instructions) {
                offsetWidth.update(Integer.toHexString(i.offset));
                updateTargets(instructionMap, targets, labelWidth, firstInsnAddress, lastInsnAddress, i);
            }

            // Increase max label width to account for "L:" characters.
            labelWidth.value += 2;

            for (Instruction i : instructionMap.values()) {
                out.println(i.toString(offsetWidth, labelWidth, showComments));
            }
        }
        out.flush();
    }

    private static void updateTargets(Map<Integer, Instruction> instructionMap,
                    Map<Long, Instruction> targets,
                    Width labelWidth,
                    long firstInsnAddress,
                    long lastInsnAddress,
                    Instruction i) {
        String opStr = i.insn.opStr;
        if (opStr != null) {
            opStr = opStr.toLowerCase();
            if (opStr.startsWith("0x")) {
                opStr = opStr.substring(2);
            }
            if (opStr.length() != 0) {
                char ch = opStr.charAt(0);
                if ((ch >= 'a' && ch <= 'f') || (ch >= '0' && ch <= '9')) {
                    try {
                        i.target = findTarget(instructionMap, targets, labelWidth, firstInsnAddress, lastInsnAddress, Long.parseLong(opStr, 16));
                    } catch (NumberFormatException e) {
                    }
                }
            }
        }
        for (JumpTableEntry e : i.jumpTable) {
            e.target = findTarget(instructionMap, targets, labelWidth, firstInsnAddress, lastInsnAddress, (long) e.target);
        }
    }

    private static Instruction findTarget(Map<Integer, Instruction> instructionMap, Map<Long, Instruction> targets, Width labelWidth, long firstInsnAddress, long lastInsnAddress, long address) {
        if (address >= firstInsnAddress && address <= lastInsnAddress) {
            assert address - firstInsnAddress < Integer.MAX_VALUE;
            int offset = (int) (address - firstInsnAddress);
            Instruction targetInstruction = instructionMap.get(offset);
            if (targetInstruction != null) {
                assert targetInstruction.insn.address == address;
                int label = targetInstruction.label;
                if (label == -1) {
                    targetInstruction.label = label = targets.size();
                }
                labelWidth.update(Integer.toString(label));
                return targets.computeIfAbsent(address, a -> targetInstruction);
            }
        }
        return null;
    }

    private static int readInt(boolean littleEndian, byte[] code, int offset) {
        if (littleEndian) {
            return code[offset + 0] & 0xFF |
                            (code[offset + 1] & 0xFF) << 8 |
                            (code[offset + 2] & 0xFF) << 16 |
                            (code[offset + 3] & 0xFF) << 24;
        }
        return code[offset + 3] & 0xFF |
                        (code[offset + 2] & 0xFF) << 8 |
                        (code[offset + 1] & 0xFF) << 16 |
                        (code[offset + 0] & 0xFF) << 24;
    }

    private static List<Instruction> disassembleAtOffset(HexCodeFile hcf, Capstone cs, int startOffset, int endOffset) {
        byte[] section = Arrays.copyOfRange(hcf.code, startOffset, endOffset);
        return disassemble(hcf, cs, startOffset, section);
    }

    private static List<Instruction> disassemble(HexCodeFile hcf, Capstone cs, int startOffset, byte[] section) {
        Capstone.CsInsn[] allInsn = cs.disasm(section, hcf.startAddress + startOffset);
        List<Instruction> instructions = new ArrayList<>(allInsn.length);
        for (Capstone.CsInsn insn : allInsn) {
            int insnOffset = (int) (insn.address - hcf.startAddress);
            List<String> comments = hcf.comments.get(insnOffset);
            StringBuilder operandComments = null;
            for (int i = 0; i < insn.size; i++) {
                List<String> list = hcf.operandComments.get(insnOffset + i);
                if (list != null) {
                    for (String c : list) {
                        if (operandComments == null) {
                            operandComments = new StringBuilder("\t");
                        } else {
                            operandComments.append(" ");
                        }
                        operandComments.append(c);
                    }
                }
            }
            Instruction instr = new Instruction(insnOffset, insn, operandComments != null ? operandComments.toString() : "", comments);
            instructions.add(instr);
        }
        return instructions;
    }

    public static void main(String[] args) throws IOException {
        String dirOption = null;
        int firstArg = 0;

        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith("-d=")) {
                dirOption = args[i].substring(3);
            } else if (args[i].startsWith("-")) {
                usage(args[i].equals("-h") ? null : "Unexpected option: " + args[i]);
            } else {
                firstArg = i;
                break;
            }
        }

        File outDir = null;
        if (dirOption != null) {
            outDir = new File(dirOption);
            if (!outDir.isDirectory()) {
                if (!outDir.mkdirs()) {
                    throw new Error("Could not create output directory " + outDir.getAbsolutePath());
                }
            }
        }

        for (int i = firstArg; i < args.length; i++) {
            String arg = args[i];
            Path inputFile = Paths.get(arg);
            StringBuilder sb = new StringBuilder();
            try (Stream<String> stream = Files.lines(inputFile)) {
                stream.forEach(s -> sb.append(s).append("\n"));
            }
            String input = sb.toString();

            String inputSource = inputFile.toAbsolutePath().toString();
            String output = processAll(input, inputSource, EMBEDDED_HCF_OPEN, EMBEDDED_HCF_CLOSE, true);

            Path outputFile;
            if (outDir == null) {
                outputFile = inputFile;
            } else {
                outputFile = Paths.get(outDir.toString(), inputFile.toFile().getName());
            }

            if (!outputFile.equals(inputFile) || !output.equals(input)) {
                Files.write(outputFile, output.getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    private static void usage(String s) {
        if (s != null) {
            System.err.println(s);
        }
        System.err.println("Usage: hcfdis [ -d=dir ] file [ files ]");
        System.exit(s == null ? 0 : -1);
    }
}
