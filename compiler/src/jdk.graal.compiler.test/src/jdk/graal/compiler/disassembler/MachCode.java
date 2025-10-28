/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jdk.graal.compiler.disassembler.DisassemblerTool.Instruction;
import jdk.graal.compiler.util.EconomicHashMap;

/**
 * Parses and disassembles "MachCode" blocks in hs_err files.
 *
 * @see "https://bugs.openjdk.java.net/browse/JDK-8213084"
 * @see "https://github.com/openjdk/jdk/blob/3884580591e932536a078f4f138920dcc8139c1a/src/hotspot/share/code/nmethod.cpp#L2958-L3017"
 */
public class MachCode {
    private static final Pattern LOCATION_LINE_RE = Pattern.compile("\\s*0x([0-9a-fA-F]+)(?: \\(\\+0x([0-9a-fA-F]+)\\))?:(.*)");
    private static final Pattern ONE_BYTE_RE = Pattern.compile("[ |]*([0-9a-fA-F][0-9a-fA-F])");

    private long startAddress;
    private final Disassembler dis;
    private final ByteArrayOutputStream buf = new ByteArrayOutputStream();
    private final PrintStream out = new PrintStream(buf);
    private final Map<Long, List<String>> comments = new EconomicHashMap<>();
    private final ByteArrayOutputStream machineCode = new ByteArrayOutputStream();

    /**
     * Opening delimiter for HotSpot abstract assembler.
     */
    public static final String MACHCODE_OPEN = "[MachCode]";
    /**
     * Closing delimiter for HotSpot abstract assembler.
     */
    public static final String MACHCODE_CLOSE = "[/MachCode]";

    private void reset() {
        startAddress = -1;
        buf.reset();
        comments.clear();
        machineCode.reset();
    }

    /**
     * Command line interface.
     *
     * @param args list of hs_err file names
     */
    public static void main(String... args) throws IOException {
        Pattern machCodeRE = Pattern.compile(Pattern.quote(MACHCODE_OPEN) + "(.*?)" + Pattern.quote(MACHCODE_CLOSE), Pattern.DOTALL);
        for (String hsErr : args) {
            Path hsErrFile = Paths.get(hsErr);
            String hsErrContent = new String(Files.readAllBytes(hsErrFile));

            Matcher matcher = machCodeRE.matcher(hsErrContent);
            Disassembler dis = initDisassembler(hsErr, hsErrContent, null, null);
            MachCode machCode = new MachCode(dis);
            FailureReason fr = new FailureReason();
            String newHsErrContent = matcher.replaceAll(mr -> machCode.process(mr.group(1), true, false, fr));
            if (newHsErrContent.equals(hsErrContent)) {
                System.out.printf("No MachCode sections in %s or they could not be disassembled: %s%n", hsErr, fr.getReason());
            } else {
                Path newHsErrFile = Paths.get(hsErrFile + ".dis");
                Files.write(newHsErrFile, newHsErrContent.getBytes(StandardCharsets.UTF_8));
                System.out.println(" In: " + hsErrFile);
                System.out.println("Out: " + newHsErrFile);
            }
        }
    }

    private static Disassembler fromArch(String arch, String message) {
        if (arch.equals("x86_64") || arch.equals("amd64") || arch.equals("x64")) {
            return new HotSpotDisassembler(Disassembler.Architecture.AMD64);
        }
        if (arch.equals("aarch64")) {
            return new HotSpotDisassembler(Disassembler.Architecture.AArch64);
        }
        throw new IllegalArgumentException(String.format("Unsupported architecture %s: %s", arch, message));
    }

    /**
     * Gets a {@link Disassembler} instance that can disassemble machine code for the CPU type
     * specified heuristic probing of {@code input} based on typical patterns found in hs_err logs
     * or .jtr files. If heuristic probing fails and {@code userArch != null}, then it is used
     * otherwise the {@code machcode.arch} or {@code os.arch} system property is used.
     *
     * @param log if non-null, log messages are printed here
     */
    public static Disassembler initDisassembler(String inputName, String input, PrintStream log, String userArch) {
        if (userArch != null) {
            return fromArch(userArch, "User requested");
        }

        Pattern osArch = Pattern.compile("os\\.arch\\s*=\\s*(\\w+)");
        Pattern amd64 = Pattern.compile("\\W(amd64|x86_64|x64)(\\W|$)");
        Pattern aarch64 = Pattern.compile("\\Waarch64(\\W|$)");
        String bestGuess = null;
        String bestGuessLine = null;
        for (String line : input.split("\n")) {
            Matcher matcher = osArch.matcher(line);
            if (matcher.find()) {
                return fromArch(matcher.group(1), line);
            }
            String guess = null;
            if (amd64.matcher(line).find()) {
                guess = "amd64";
            }
            if (aarch64.matcher(line).find()) {
                guess = "aarch64";
            }
            if (line.startsWith("vm_info:")) {
                if (guess != null) {
                    return fromArch(guess, String.format("Selected from input line \"%s\" in %s", line, inputName));
                }
                throw new IllegalArgumentException(String.format("Could not determine CPU from vm_info in %s:%n%s", inputName, input));
            }
            if (line.contains(" built on ")) {
                // Xinternalversion line from the normal HotSpot log
                if (guess != null) {
                    return fromArch(guess, String.format("Selected from input line \"%s\" in %s", line, inputName));
                }
            }
            if (bestGuess == null) {
                if (guess != null) {
                    bestGuess = guess;
                    bestGuessLine = line;
                }
            } else {
                if (!bestGuess.equals(guess)) {
                    bestGuess = "unknown";
                }
            }
        }
        if (bestGuess != null && !bestGuess.equals("unknown")) {
            if (log != null) {
                log.printf("Guessing architecture %s from input line \"%s\" in %s. Use --arch option to specify a different architecture%n", bestGuess, inputName, bestGuessLine);
            }
            return fromArch(bestGuess, bestGuessLine);
        }

        // Fallback to os.arch system property.
        String prop = "machcode.arch";
        String arch = System.getProperty(prop);
        if (arch == null) {
            prop = "os.arch";
            arch = System.getProperty(prop);
        }
        if (log != null) {
            log.printf("Cannot determine architecture from input. Using value of %s property: %s. Use --arch option to specify a different architecture%n", prop, arch);
        }
        return fromArch(arch, prop);
    }

    public static class FailureReason implements Consumer<String> {
        private String reason;

        @Override
        public void accept(String t) {
            reason = t;
        }

        public String getReason() {
            return reason;
        }
    }

    public MachCode(Disassembler dis) {
        this.dis = dis;
    }

    /**
     * Returns the currently decoded output. Useful for debugging the decoding in an IDE.
     */
    @Override
    public String toString() {
        return buf.toString();
    }

    /**
     * Decode the abstract assembly in {@code machCode}.
     *
     * @param failureReason if non-null and disassembling fails, the reason is sent to this object
     * @return the disassembled abstract assembly or {@code machCode} if disassembling failed
     */
    public String process(String machCode, boolean showComments, boolean showBytes, Consumer<String> failureReason) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(machCode.length() * 2);
        PrintStream ps = new PrintStream(baos);
        if (process(machCode, ps, showComments, showBytes, failureReason)) {
            return baos.toString();
        }
        return machCode;
    }

    /**
     * Decode the abstract assembly in {@code machCode} and print it to {@code ps}.
     *
     * @param failureReason if non-null and disassembling fails, the reason is sent to this object
     * @return {@code true} if the abstract assembly was disassembled to {@code ps}, {@code false}
     *         if disassembling failed and nothing was written to {@code ps}
     */
    boolean process(String machCode, PrintStream ps, boolean showComments, boolean showBytes, Consumer<String> failureReason) {
        reset();
        String[] lines = machCode.split(System.lineSeparator());
        String header = null;
        Map<Long, String> headers = new EconomicHashMap<>();
        Long commentAddress = null;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].strip();
            if (line.isEmpty()) {
                continue;
            }
            Matcher matcher = LOCATION_LINE_RE.matcher(line);
            if (matcher.matches()) {
                long address = Long.parseLong(matcher.group(1), 16);
                if (header != null) {
                    headers.put(address, header);
                    header = null;
                }
                if (startAddress == -1) {
                    startAddress = address;
                    commentAddress = address;
                }
                String rest = matcher.group(3).strip();
                if (rest.startsWith(";")) {
                    commentAddress = address;
                    comments.computeIfAbsent(commentAddress, _ -> new ArrayList<>()).add(rest);
                } else {
                    Matcher byteMatcher = ONE_BYTE_RE.matcher(rest);
                    int lastMatchEnd = 0;
                    while (byteMatcher.find()) {
                        String hexByte = byteMatcher.group(1);
                        lastMatchEnd = byteMatcher.end(1);
                        machineCode.write(Integer.parseInt(hexByte, 16) & 0xFF);
                    }
                    String nonBytes = rest.substring(lastMatchEnd).strip();
                    if (!nonBytes.isEmpty()) {
                        if (failureReason != null) {
                            int nonBytesStart = matcher.start(3) + lastMatchEnd;
                            failureReason.accept("line " + (i + 1) + " has non-bytes at offset " + nonBytesStart + ": " + line);
                        }
                        return false;
                    }
                }
            } else if (line.startsWith(";") && !line.startsWith(";;") && commentAddress != null) {
                comments.computeIfAbsent(commentAddress, _ -> new ArrayList<>()).add(line);
            } else {
                if (header == null) {
                    header = line;
                } else {
                    header += System.lineSeparator() + line;
                }
            }
        }

        if (machineCode.size() != 0) {
            String suffix = header;
            byte[] section = machineCode.toByteArray();
            machineCode.reset();

            List<Instruction> disasm = disassemble(section);
            if (disasm.isEmpty()) {
                if (failureReason != null) {
                    failureReason.accept("Disassembler returned empty array of instructions");
                }
                return false;
            }
            long codeLength = 0;
            for (Instruction i : disasm) {
                codeLength += i.size();
            }
            int offsetWidth = (codeLength < (1 << 8)) ? 2 : (codeLength < (1 << 16)) ? 4 : (codeLength < (1 << 24)) ? 6 : 8;
            Instruction previousInstruction = null;
            for (Instruction instruction : disasm) {
                header = headers.remove(instruction.address());
                if (header != null) {
                    out.println(header);
                }
                long offset = instruction.address() - startAddress;
                String prefix = String.format("  0x%016x (+0x%0" + offsetWidth + "x):   %s", instruction.address(), offset, instruction.disassembly());

                boolean printed = false;
                if (showBytes) {
                    out.printf("%-60s ; bytes ", prefix);
                    for (byte b : instruction.bytes()) {
                        out.printf("%02x ", b);
                    }
                    out.printf("%n");
                    prefix = "";
                    printed = true;
                }
                if (showComments) {
                    List<String> instructionComments = commentsFor(instruction, previousInstruction);
                    for (String comment : instructionComments) {
                        out.printf("%-60s %s%n", prefix, comment);
                        prefix = "";
                        printed = true;
                    }
                }
                if (!printed) {
                    out.println(prefix);
                }
                previousInstruction = instruction;

            }
            if (suffix != null) {
                out.println(suffix);
            }
        } else if (header != null) {
            out.println(header);
        }
        ps.print(buf);
        return true;
    }

    private List<Instruction> disassemble(byte[] section) {
        return DisassemblerTool.disassemble(dis, startAddress, section, 0, section.length, null, null);
    }

    /**
     * Gets the code comments that apply to {@code instruction}.
     *
     * Until <a href="https://bugs.openjdk.java.net/browse/JDK-8274197">JDK-8274197</a> is resolved,
     * code comments may be slightly off in terms of the instructions to which they apply. The
     * heuristic employed here is to associate comment C with instruction I if the address of C
     * equals the start address of I or is in the middle of the instruction prior to I.
     *
     * @param instruction
     * @param previousInstruction
     */
    private List<String> commentsFor(Instruction instruction, Instruction previousInstruction) {
        List<String> res = new ArrayList<>();
        for (Map.Entry<Long, List<String>> e : comments.entrySet()) {
            long address = e.getKey();
            if (address <= instruction.address() && (previousInstruction == null || address > previousInstruction.address())) {
                res.addAll(e.getValue());
            }
        }
        return res;
    }
}
