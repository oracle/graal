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

import static jdk.graal.compiler.code.HexCodeFile.EMBEDDED_HCF_CLOSE;
import static jdk.graal.compiler.code.HexCodeFile.EMBEDDED_HCF_OPEN;
import static jdk.graal.compiler.disassembler.DisassemblerTool.Width.ONE;
import static jdk.graal.compiler.disassembler.MachCode.MACHCODE_CLOSE;
import static jdk.graal.compiler.disassembler.MachCode.MACHCODE_OPEN;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Formatter;
import java.util.List;
import java.util.Map;

import jdk.graal.compiler.code.HexCodeFile;
import jdk.graal.compiler.disassembler.MachCode.FailureReason;

/**
 * Utility to convert a {@link HexCodeFile} or {@link MachCode} to assembly code using an external
 * library.
 */
public class DisassemblerTool {

    /**
     * Disassembles all {@link HexCodeFile} and {@link MachCode} blobs in {@code input}.
     *
     * @param inputName name for the input source to be used in error messages
     * @param startDelim the delimiter just before a code blob in {@code input}
     * @param endDelim the delimiter just after a code blob in {@code input}
     * @param userArch if non-null, user provided architecture to use for {@link MachCode} blobs if
     *            the architecture cannot be derived from {@code input}
     * @param showComments specified whether to show comments in disassembly
     * @param showBytes whether to show the bytes of the instruction
     * @return the value of {@code input} with all embedded code blobs converted to their
     *         disassembled form
     */
    public static String processAll(String input, String inputName, String startDelim, String endDelim, String userArch, PrintStream log, boolean showComments, boolean showBytes) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(input.length() * 2);
        PrintStream out = new PrintStream(baos);
        int count = 0;

        int codeEnd = 0;
        int index;

        boolean processingHotSpotAbstractAssembly = startDelim.equals(MACHCODE_OPEN);
        Disassembler dis = null;
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
            if (endIndex == -1) {
                codeEnd = codeStart;
            } else {

                String source = input.substring(codeStart, endIndex);

                if (processingHotSpotAbstractAssembly) {
                    if (dis == null) {
                        dis = MachCode.initDisassembler(inputName, input, log, userArch);
                    }
                    String machCode = source.strip();
                    FailureReason fr = new FailureReason();
                    if (!new MachCode(dis).process(machCode, out, showComments, showBytes, fr)) {
                        out.println("# Could not disassemble MachCode: " + fr.getReason());
                        out.println(MACHCODE_OPEN);
                        out.println(machCode);
                        out.println(MACHCODE_CLOSE);
                    }
                } else {
                    HexCodeFile hcf = HexCodeFile.parse(input, codeStart, source, inputName);
                    if (hcf == null) {
                        throw new IllegalArgumentException("Malformed HexCodeFile in " + inputName);
                    }
                    new HexCodeFileDis(showComments, showBytes).process(hcf, out);
                }
                count++;
                codeEnd = endIndex;
            }
        }

        String copy = input.substring(codeEnd);
        if (copy.startsWith(endDelim)) {
            copy = copy.substring(endDelim.length());
        }

        if (log != null) {
            log.println(inputName + ": disassembled " + count + (processingHotSpotAbstractAssembly ? " MachCode sections" : " HexCodeFiles"));
        }
        out.print(copy);
        out.flush();
        return baos.toString();
    }

    private static String extractCodeBlobs(String input) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(input.length() * 2);
        PrintStream out = new PrintStream(baos);
        int index = 0;
        while (index < input.length()) {
            String closeDelim;
            String startDelim;
            int search;
            if ((search = input.indexOf(EMBEDDED_HCF_OPEN, index)) != -1) {
                index = search;
                startDelim = EMBEDDED_HCF_OPEN;
                closeDelim = EMBEDDED_HCF_CLOSE;
            } else if ((search = input.indexOf(MACHCODE_OPEN, index)) != -1) {
                index = search;
                startDelim = MACHCODE_OPEN;
                closeDelim = MACHCODE_CLOSE;
            } else {
                break;
            }

            int endIndex = input.indexOf(closeDelim, index + startDelim.length());
            if (endIndex == -1) {
                break;
            }
            assert endIndex != -1;
            endIndex += closeDelim.length();
            String codeBlob = input.substring(index, endIndex);
            index = endIndex;
            out.println(codeBlob);
        }
        out.flush();
        return baos.toString();
    }

    /**
     * Computes the max width of a column based on values to be be printed in the column.
     */
    public static class Width {
        public static final Width ONE = new Width(1);

        public Width(int initialValue) {
            this.value = initialValue;
        }

        public void update(int i, int radix) {
            assert this != ONE;
            update(Integer.toString(i, radix));
        }

        public void update(String s) {
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

        public int value;
    }

    /**
     * An entry in a jump table in the decoded instruction stream.
     */
    public static class JumpTableEntry {

        public JumpTableEntry(int offset, long address, int primaryKey, Integer secondaryKey, long target) {
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
        public Object target;

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
     * A {@link DecodedInstruction} with associated comments.
     */
    public static class Instruction {
        public Instruction(int offset, DecodedInstruction instruction, String operandComment, List<String> comments) {
            this.offset = offset;
            this.operandComment = operandComment;
            this.comments = comments;
            this.instruction = instruction;
        }

        private final DecodedInstruction instruction;
        private final int offset;
        private final String operandComment;
        private final List<String> comments;

        public long address() {
            return instruction.address();
        }

        public int size() {
            return instruction.size();
        }

        public String operands() {
            return instruction.operands();
        }

        public String op() {
            return instruction.op();
        }

        public String disassembly() {
            return instruction.op() + "\t" + instruction.operands();
        }

        public byte[] bytes() {
            return instruction.bytes();
        }

        public int getOffset() {
            return offset;
        }

        /**
         * The entries of the jump table after this instruction.
         */
        public final List<JumpTableEntry> jumpTable = new ArrayList<>();

        /**
         * The label of this instruction if it is targeted by a jump instruction. Otherwise,
         * {@code -1}.
         */
        public int label = -1;

        /**
         * The instruction targeted by this instruction if it is a jump.
         */
        public Instruction target;

        public String labeledAddress() {
            return String.format("L%d: 0x%x", label, instruction.address());
        }

        @Override
        public String toString() {
            return toString(ONE, ONE, false, false);
        }

        public String toString(Width offsetWidth, Width labelWidth, boolean showBytes, boolean showComments) {
            Formatter buf = new Formatter();
            String l = label == -1 ? "" : "L" + label + ":";
            final String commentLinePrefix = ";; ";
            if (showBytes) {
                buf.format("%sbytes ", commentLinePrefix);
                for (byte b : instruction.bytes()) {
                    buf.format("%02x ", b);
                }
                buf.format("%n");
            }
            if (showComments && comments != null) {
                for (String comment : comments) {
                    buf.format("%s%s%n", commentLinePrefix, comment.replace("\n", "\n" + commentLinePrefix));
                }
            }
            Object branchLabel = target == null ? "" : "\t" + target.labeledAddress();
            buf.format("0x%08x(+0x%-" + offsetWidth + "x): %" + labelWidth + "s %s\t%s%s%s", instruction.address(), offset, l, op(), operands(), branchLabel, showComments ? operandComment : "");
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

    public static List<DisassemblerTool.Instruction> disassemble(Disassembler dis,
                    long startAddress,
                    byte[] code,
                    int startOffset,
                    int endOffset,
                    Map<Integer, List<String>> comments,
                    Map<Integer, List<String>> operandComments) {
        List<DisassemblerTool.Instruction> instructions = new ArrayList<>();
        int currentOffset = startOffset;
        while (true) {
            byte[] section = Arrays.copyOfRange(code, currentOffset, endOffset);
            List<DecodedInstruction> allInsn = dis.disasm(section, startAddress + currentOffset);
            for (DecodedInstruction insn : allInsn) {
                int insnOffset = (int) (insn.address() - startAddress);
                instructions.add(new DisassemblerTool.Instruction(insnOffset, insn, getOperandComments(operandComments, insn.size(), insnOffset), getComments(comments, insnOffset)));
                currentOffset = insnOffset + insn.size();
            }
            if (currentOffset >= endOffset) {
                break;
            } else {
                int size;
                // Broken instruction
                if (dis.getArchitecture().equals(Disassembler.Architecture.AArch64)) {
                    size = 4;
                } else if (dis.getArchitecture().equals(Disassembler.Architecture.AMD64)) {
                    // This could potentially be handled better by splitting the machine code into
                    // chunks in between comments and decoding those chunks independently. That
                    // would naturally resynchronize the disassembly if problems are encountered.
                    size = 1;
                } else {
                    throw new InternalError("Unable to decode some instructions in " + dis.getArchitecture());
                }
                byte[] bytes = Arrays.copyOfRange(code, currentOffset, currentOffset + size);
                Formatter buf = new Formatter();
                for (byte b : bytes) {
                    buf.format("%02x ", b);
                }
                String encoding = (size == 1 ? ".byte\t" : ".bytes\t") + buf.toString().trim();
                DecodedInstruction insn = new DecodedInstruction(startAddress + currentOffset, size, encoding, null, bytes);
                instructions.add(new DisassemblerTool.Instruction(currentOffset, insn,
                                getOperandComments(operandComments, size, currentOffset), getComments(comments, currentOffset + startOffset)));
                currentOffset += size;
            }
        }
        return instructions;
    }

    private static List<String> getComments(Map<Integer, List<String>> comments, int insnOffset) {
        if (comments != null) {
            return comments.get(insnOffset);
        }
        return null;
    }

    private static String getOperandComments(Map<Integer, List<String>> allOperandComments, int size, int insnOffset) {
        StringBuilder operandComments = null;
        if (allOperandComments != null) {
            for (int i = 0; i < size; i++) {
                List<String> list = allOperandComments.get(insnOffset + i);
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
        }
        return operandComments == null ? "" : operandComments.toString();
    }

    private static void usage(String errMsg) {
        if (errMsg != null) {
            System.err.println(errMsg);
        }
        System.err.printf("Usage: %s [ options ] [files...]%n%n", DisassemblerTool.class.getName());
        System.err.printf("Process HexCodeFile and MachCode blobs in files (or stdin if files is omitted) to either extract them%n");
        System.err.printf("or decode them using the disassembler. A file can be a http:// or https:// URL.%n%n");
        System.err.printf("options:%n");
        System.err.printf("  --extract-only      filter out all non-code blob content in files%n");
        System.err.printf("                      instead of disassembling the code blobs%n");
        System.err.printf("  --arch=<arch>       architecture to use when it cannot be derived from the input%n");
        System.err.printf("  --out-ext=<ext>     extension to use when writing output for a file. Default is to overwrite%n");
        System.err.printf("                      the input file. Specifying \"-\" causes the output to be sent to stdout.%n");
        System.err.printf("                      Ignored if reading from stdin.%n");
        System.err.printf("  --quiet             do not show details of disassembly progress%n");
        System.err.printf("  --no-comments       do not show comments in disassembly%n");
        System.err.printf("  --show-bytes        show instruction bytes as comment in disassembly%n");
        System.err.printf("  --help              print this help message%n");
    }

    public static void main(String... args) throws IOException {

        String ext = "";
        boolean quiet = false;
        boolean extractOnly = false;
        boolean showComments = true;
        boolean showBytes = false;
        int i = 0;
        String userArch = null;
        while (i < args.length) {
            if (args[i].equals("--extract-only")) {
                extractOnly = true;
            } else if (args[i].equals("--quiet")) {
                quiet = true;
            } else if (args[i].startsWith("--arch=")) {
                userArch = args[i].substring("--arch=".length());
            } else if (args[i].equals("--no-comments")) {
                showComments = false;
            } else if (args[i].equals("--show-bytes")) {
                showBytes = true;
            } else if (args[i].equals("--help")) {
                usage(null);
                return;
            } else if (args[i].startsWith("--out-ext=")) {
                ext = args[i].substring("--out-ext=".length());
            } else if (args[i].startsWith("-")) {
                usage("Unexpected option: " + args[i]);
                System.exit(-1);
            } else {
                break;
            }
            i++;
        }
        String[] files = Arrays.copyOfRange(args, i, args.length);

        if (files.length == 0) {
            String input = new String(new DataInputStream(System.in).readAllBytes());
            System.out.println(process(extractOnly, null, "<stdin>", input, userArch, showComments, showBytes));
        } else {
            PrintStream log = ext.equals("-") || quiet ? null : System.out;
            for (String rawFile : files) {
                String file = resolveInputFile(rawFile);
                Path inputFile = Paths.get(file);
                Path outputFile = Paths.get(file + ext);
                String input = new String(Files.readAllBytes(inputFile));
                String output = process(extractOnly, log, file, input, userArch, showComments, showBytes);

                if (ext.equals("-")) {
                    System.out.println(output);
                } else {
                    if (!output.equals(input) || !inputFile.equals(outputFile)) {
                        Files.write(outputFile, output.getBytes(StandardCharsets.UTF_8));
                        if (log != null) {
                            log.println(" In: " + inputFile);
                            log.println("Out: " + outputFile);
                        }
                    } else if (log != null) {
                        log.println(" In: " + inputFile + " <no code blobs found>");
                    }
                }
            }
        }
    }

    /**
     * Resolves {@code file} to a local file path if it starts with {@code "http://"} or
     * {@code "https://"} by downloading it and writing it to a local file based on the URL.
     *
     * @return the path to the local file containing the downloaded content
     */
    private static String resolveInputFile(String file) throws IOException {
        if (file.startsWith("http://") || file.startsWith("https://")) {
            try {
                Proxy proxy = Proxy.NO_PROXY;
                String proxyEnv = System.getenv("HTTPS_PROXY");

                if (proxyEnv != null && !"".equals(proxyEnv)) {
                    URI proxyURI = new URI(proxyEnv);
                    proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyURI.getHost(), proxyURI.getPort()));
                }

                URL url = new URI(file).toURL();
                Path localFile = Path.of(url.getPath()).getFileName();
                HttpURLConnection connection = (HttpURLConnection) url.openConnection(proxy);
                StringBuilder content = new StringBuilder();
                try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    String inputLine;
                    while ((inputLine = in.readLine()) != null) {
                        content.append(inputLine).append("\n");
                    }
                } finally {
                    connection.disconnect();
                }
                Files.writeString(localFile, content.toString());
                return localFile.toAbsolutePath().toString();
            } catch (URISyntaxException e) {
                throw new IOException(e);
            }
        }
        return file;
    }

    private static String process(boolean extractOnly, PrintStream log, String file, String input, String userArch, boolean showComments, boolean showBytes) {
        if (extractOnly) {
            return extractCodeBlobs(input);
        } else {
            String inputSource = file.toString();
            String output = processAll(input, inputSource, EMBEDDED_HCF_OPEN, EMBEDDED_HCF_CLOSE, userArch, log, showComments, showBytes);
            return processAll(output, inputSource, MACHCODE_OPEN, MACHCODE_CLOSE, userArch, log, showComments, showBytes);
        }
    }
}
