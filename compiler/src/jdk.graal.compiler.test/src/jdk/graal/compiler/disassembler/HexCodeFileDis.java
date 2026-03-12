/*
 * Copyright (c) 2009, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.disassembler;

import static jdk.graal.compiler.code.HexCodeFile.EMBEDDED_HCF_CLOSE;
import static jdk.graal.compiler.code.HexCodeFile.EMBEDDED_HCF_OPEN;
import static jdk.graal.compiler.disassembler.HotSpotDisassembler.lookupArchitecture;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import jdk.graal.compiler.code.HexCodeFile;
import jdk.graal.compiler.code.HexCodeFile.JumpTable;
import jdk.graal.compiler.code.HexCodeFile.JumpTable.EntryFormat;
import jdk.graal.compiler.disassembler.DisassemblerTool.Instruction;
import jdk.graal.compiler.disassembler.DisassemblerTool.JumpTableEntry;
import jdk.graal.compiler.disassembler.DisassemblerTool.Width;
import jdk.graal.compiler.util.EconomicHashMap;

/**
 * Utility for converting a {@link HexCodeFile} to a commented disassembly.
 */
public class HexCodeFileDis {

    private final String startDelim;
    private final String endDelim;
    private final boolean showComments;
    private final boolean showBytes;

    public HexCodeFileDis(boolean showComments, boolean showBytes) {
        this.startDelim = EMBEDDED_HCF_OPEN;
        this.endDelim = EMBEDDED_HCF_CLOSE;
        this.showComments = showComments;
        this.showBytes = showBytes;
    }

    /**
     * Disassembles all HexCodeFiles embedded in a given input string.
     *
     * @param input some input containing 0 or more HexCodeFiles
     * @param inputName name for the input source to be used in error messages
     * @return the value of {@code input} with all embedded HexCodeFiles converted to their
     *         disassembled form
     */
    public String processAll(String input, String inputName) {
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
            process(hcf, out);
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
     * Disassembles a given HexCodeFile.
     *
     * @param out where the HexCodeFile disassembly is printed
     */
    public void process(HexCodeFile hcf, PrintStream out) {
        Disassembler dis = new HotSpotDisassembler(lookupArchitecture(hcf.isa));

        // Ordered map from offset to decoded instructions
        Map<Integer, Instruction> instructionMap = new LinkedHashMap<>();

        if (hcf.jumpTables.size() == 0) {
            for (Instruction i : disassemble(hcf, dis)) {
                instructionMap.put(i.getOffset(), i);
            }
        } else {
            // Break the disassembly into chunks separated by jump tables
            int startOffset = 0;
            for (JumpTable jumpTable : hcf.jumpTables) {
                int jumpTableOffset = jumpTable.getPosition();
                List<Instruction> insns = disassembleAtOffset(hcf, dis, startOffset, jumpTableOffset);
                for (Instruction i : insns) {
                    instructionMap.put(i.getOffset(), i);
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
                            targetOffsetRelativeToJumpTable = readInt(dis.isLittleEndian(), hcf.code, entryOffset);
                            break;
                        case KEY2_OFFSET:
                            secondaryKey = readInt(dis.isLittleEndian(), hcf.code, entryOffset);
                            targetOffsetRelativeToJumpTable = readInt(dis.isLittleEndian(), hcf.code, entryOffset + 4);
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
                for (Instruction i : disassembleAtOffset(hcf, dis, startOffset, hcf.code.length)) {
                    instructionMap.put(i.getOffset(), i);
                }
            }
        }

        Width labelWidth = new Width(1);
        Width offsetWidth = new Width(1);

        Instruction[] instructions = instructionMap.values().toArray(new Instruction[0]);
        if (instructions.length > 0) {
            long firstInsnAddress = instructions[0].address();
            long lastInsnAddress = instructions[instructions.length - 1].address();
            Map<Long, Instruction> targets = new EconomicHashMap<>();
            for (Instruction i : instructions) {
                offsetWidth.update(Integer.toHexString(i.getOffset()));
                updateTargets(instructionMap, targets, labelWidth, firstInsnAddress, lastInsnAddress, i);
            }

            // Increase max label width to account for "L:" characters.
            labelWidth.value += 2;

            for (Instruction i : instructionMap.values()) {
                out.println(i.toString(offsetWidth, labelWidth, showBytes, showComments));
            }
        }
        out.flush();
    }

    static void updateTargets(Map<Integer, Instruction> instructionMap,
                    Map<Long, Instruction> targets,
                    Width labelWidth,
                    long firstInsnAddress,
                    long lastInsnAddress,
                    Instruction i) {
        String opStr = i.operands();
        if (opStr != null) {
            opStr = opStr.toLowerCase();
            if (opStr.startsWith("0x")) {
                opStr = opStr.substring(2);
            } else if (opStr.startsWith("#0x")) {
                // aarch64 has a # in front
                opStr = opStr.substring(3);
            }
            if (opStr.length() != 0) {
                char ch = opStr.charAt(0);
                if ((ch >= 'a' && ch <= 'f') || (ch >= '0' && ch <= '9')) {
                    try {
                        i.target = findTarget(instructionMap, targets, labelWidth, firstInsnAddress, lastInsnAddress, Long.parseLong(opStr, 16), i.address());
                    } catch (NumberFormatException e) {
                    }
                }
            }
        }
        for (JumpTableEntry e : i.jumpTable) {
            e.target = findTarget(instructionMap, targets, labelWidth, firstInsnAddress, lastInsnAddress, (long) e.target, e.address);
        }
    }

    private static Instruction findTarget(Map<Integer, Instruction> instructionMap, Map<Long, Instruction> targets, Width labelWidth, long firstInsnAddress, long lastInsnAddress, long address,
                    long selfAddress) {
        if (address >= firstInsnAddress && address <= lastInsnAddress) {
            assert address - firstInsnAddress < Integer.MAX_VALUE;
            int offset = (int) (address - firstInsnAddress);
            Instruction targetInstruction = instructionMap.get(offset);
            if (targetInstruction != null && selfAddress != address) {
                assert targetInstruction.address() == address;
                int label = targetInstruction.label;
                if (label == -1) {
                    targetInstruction.label = label = targets.size();
                }
                labelWidth.update(Integer.toString(label));
                return targets.computeIfAbsent(address, x -> targetInstruction);
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

    private static List<Instruction> disassembleAtOffset(HexCodeFile hcf, Disassembler dis, int startOffset, int endOffset) {
        return DisassemblerTool.disassemble(dis, hcf.startAddress, hcf.code, startOffset, endOffset, hcf.comments, hcf.operandComments);
    }

    private static List<Instruction> disassemble(HexCodeFile hcf, Disassembler dis) {
        return disassembleAtOffset(hcf, dis, 0, hcf.code.length);
    }

    public static void main(String[] args) throws IOException {
        String dirOption = null;
        int firstArg = 0;
        boolean showBytes = false;

        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith("-d=")) {
                dirOption = args[i].substring(3);
            } else if (args[i].equals("--show-bytes")) {
                showBytes = true;
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
            String output = new HexCodeFileDis(true, showBytes).processAll(input, inputSource);

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
        System.err.println("Usage: hcfdis [ -d=dir ] [ --show-bytes ] file [ files ]");
        System.exit(s == null ? 0 : -1);
    }
}
