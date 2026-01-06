/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A disassembler based on the {@code hsdis} library.
 */
public class HotSpotDisassembler implements Disassembler {

    /**
     * The hsdis assembler only supports the architecture it's compiled for so only the current
     * architecture can be disassembled.
     */
    static final Architecture currentArchitecture;

    static {
        currentArchitecture = lookupArchitecture(System.getProperty("os.arch"));
    }

    static Architecture lookupArchitecture(String arch) {
        return switch (arch) {
            case "x86_64", "amd64" -> Architecture.AMD64;
            case "arm64", "aarch64" -> Architecture.AArch64;
            default -> throw new IllegalArgumentException("Unsupported ISA: " + arch);
        };
    }

    private final Architecture architecture;
    final String mach;
    final String options;
    final Pattern branchInstruction;

    public HotSpotDisassembler(Architecture arch) {
        /*
         * hsdis contains all assemblers but the mechanism for selecting alternative disassemblers
         * using "mach=" is currently broken. So throw an exception when trying to disassemble a
         * different architecture.
         */
        if (!currentArchitecture.equals(arch)) {
            throw new IllegalArgumentException("Unsupported ISA: " + arch);
        }
        this.architecture = arch;
        if (arch.equals(Architecture.AMD64)) {
            // Use Intel syntax
            mach = "i386:x86-64";
            options = "intel";
            branchInstruction = Pattern.compile(
                            "^(jmp|ret|je|jne|ja|jae|jb|jbe|jc|jcxz|jecxz|jg|jge|jl|jle|jna|jnae|jnb|jnbe|jnc|jne|jng|jnge|jnl|jnle|jno|jnp|jns|jnz|jo|jp|jpe|jpo|js|jz)\\b");
        } else if (arch.equals(Architecture.AArch64)) {
            mach = "aarch64";
            options = "";
            /*
             * Don't treat bl as a branch since it's a call. Uninitialized call sites look like self
             * calls.
             */
            branchInstruction = Pattern.compile("^b[. \t]");
        } else {
            throw new IllegalArgumentException("Unsupported ISA: " + arch);
        }
    }

    Visitor createVisitor(byte[] section, long startPc) {
        return new PanamaDisassemblerVisitor(this, section, startPc);
    }

    @Override
    public List<DecodedInstruction> disasm(byte[] section, long startPc) {
        Visitor visitor = createVisitor(section, startPc);
        return visitor.visit();
    }

    @Override
    public boolean isLittleEndian() {
        return true;
    }

    @Override
    public Architecture getArchitecture() {
        return architecture;
    }

    abstract static class Visitor {
        protected final byte[] code;
        protected final long startPc;
        protected final HotSpotDisassembler disassembler;
        protected long currentAddress;
        protected Long targetAddress;
        List<DecodedInstruction> instructions = new ArrayList<>();

        StringBuilder disassembly = new StringBuilder();

        Visitor(HotSpotDisassembler disassembler, byte[] code, long startPc) {
            this.disassembler = disassembler;
            this.code = code;
            this.startPc = startPc;
        }

        /**
         * Events from hsdis start with a word and an optional whitespace separate tail. Check for
         * {@code tag} at the beginning of the message.
         */
        protected static boolean matchTag(String event, String tag) {
            if (!event.startsWith(tag)) {
                return false;
            }
            int taglen = tag.length();
            if (taglen == event.length()) {
                return true;
            }
            char delim = event.charAt(taglen);
            return delim == ' ' || delim == '/' || delim == '=';
        }

        protected void notifyArchitecture(String event, String stringArgument) {
            if (stringArgument.equals("unknown") || !stringArgument.equalsIgnoreCase(disassembler.mach)) {
                throw new IllegalArgumentException("Unsupported ISA: " + disassembler.architecture);
            }
        }

        /**
         * Begin decoding an instruction that starts at {@code address}.
         */
        protected void startInstruction(long address) {
            currentAddress = address;
            targetAddress = null;
            disassembly.setLength(0);
        }

        /**
         * Finish a decoded instruction.
         */
        public void endInstruction(long endPc) {
            int size = (int) (endPc - currentAddress);
            byte[] bytes = Arrays.copyOfRange(code, (int) (currentAddress - startPc), (int) (endPc - startPc));
            assert size == bytes.length : size + " " + Arrays.toString(bytes);
            String[] dis = getDisassembly().split("\\s+", 2);
            instructions.add(new DecodedInstruction(currentAddress, size, dis[0], dis.length == 2 ? dis[1] : "", bytes));
        }

        static final Pattern instPattern = Pattern.compile("\\.inst\\s+0x0000([0-9a-fA-F]{4})");

        /**
         * Get the currently collected disassembly output.
         */
        protected String getDisassembly() {
            // Collect any new fprintf output into the current disassembly.
            disassembly.append(getRawOutput());

            // The aarch disassembler emits some unhelpful comments so delete them
            int idx = disassembly.indexOf("//");
            if (idx == -1) {
                idx = disassembly.indexOf(" ; undefined");
            }

            String result;
            if (idx >= 0) {
                result = disassembly.substring(0, idx).trim();
            } else {
                result = disassembly.toString().trim();
            }
            if (currentArchitecture == Architecture.AArch64) {
                // Some binutils versions use udf instead of instead .inst so try to be consistent
                Matcher matcher = instPattern.matcher(result);
                StringBuilder udf = new StringBuilder();
                while (matcher.find()) {
                    String hex = matcher.group(1);
                    int num = Integer.parseUnsignedInt(hex, 16);
                    matcher.appendReplacement(udf, "udf #" + num);
                }
                matcher.appendTail(udf);
                result = udf.toString();
            }
            return result;
        }

        /**
         * Consume any output produced by the hsdis printing. This is collection into
         * {@link #disassembly} to produce the final per instruction disassembly.
         */
        protected abstract String getRawOutput();

        public abstract List<DecodedInstruction> visit();
    }
}
