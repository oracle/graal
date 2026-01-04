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

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.Serializable;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A HexCodeFile is a textual format for representing a chunk of machine code along with extra
 * information that can be used to enhance a disassembly of the code.
 * <p>
 * A pseudo grammar for a HexCodeFile is given below.
 *
 * <pre>
 *     HexCodeFile ::= Platform Delim HexCode Delim (OptionalSection Delim)*
 *
 *     OptionalSection ::= Comment | OperandComment | JumpTable | LookupTable
 *
 *     Platform ::= "Platform" ISA WordWidth
 *
 *     HexCode ::= "HexCode" StartAddress HexDigits
 *
 *     Comment ::= "Comment" Position String
 *
 *     OperandComment ::= "OperandComment" Position String
 *
 *     EntryFormat ::= 4 | 8 | "OFFSET" | "KEY2_OFFSET"
 *
 *     JumpTable ::= "JumpTable" Position EntryFormat Low High
 *
 *     LookupTable ::= "LookupTable" Position NPairs KeySize OffsetSize
 *
 *     Position, EntrySize, Low, High, NPairs KeySize OffsetSize ::= int
 *
 *     Delim := "<||@"
 * </pre>
 * <p>
 * There must be exactly one HexCode and Platform part in a HexCodeFile. The length of HexDigits
 * must be even as each pair of digits represents a single byte.
 * <p>
 * Below is an example of a valid Code input:
 *
 * <pre>
 *
 *  Platform AMD64 64  <||@
 *  HexCode 0 e8000000009090904883ec084889842410d0ffff48893c24e800000000488b3c24488bf0e8000000004883c408c3  <||@
 *  Comment 24 frame-ref-map: +0 {0}
 *  at java.lang.String.toLowerCase(String.java:2496) [bci: 1]
 *              |0
 *     locals:  |stack:0:a
 *     stack:   |stack:0:a
 *    <||@
 *  OperandComment 24 {java.util.Locale.getDefault()}  <||@
 *  Comment 36 frame-ref-map: +0 {0}
 *  at java.lang.String.toLowerCase(String.java:2496) [bci: 4]
 *              |0
 *     locals:  |stack:0:a
 *    <||@
 *  OperandComment 36 {java.lang.String.toLowerCase(Locale)}  <||@
 *
 * </pre>
 */
public class HexCodeFile {
    /**
     * Provides extra information about instructions or data at specific positions in the target
     * code. This is optional information that can be used to enhance a disassembly of the code.
     */
    public abstract static class CodeAnnotation implements Serializable {
        private static final long serialVersionUID = 928798596620568715L;
        public final int position;

        public CodeAnnotation(int position) {
            this.position = position;
        }

        public int getPosition() {
            return position;
        }
    }

    /**
     * Describes a table of signed offsets embedded in the code. The offsets are relative to the
     * starting address of the table. This type of table maybe generated when translating a
     * multi-way branch based on a key value from a dense value set (e.g. the {@code tableswitch}
     * JVM instruction).
     * <p>
     * The table is indexed by the contiguous range of integers from {@link #low} to {@link #high}
     * inclusive.
     */
    public static final class JumpTable extends CodeAnnotation {
        private static final long serialVersionUID = -6520017513070589383L;

        /**
         * Constants denoting the format and size of each entry in a jump table.
         */
        public enum EntryFormat {
            /**
             * Each entry is a 4 byte offset. The base of the offset is platform dependent.
             */
            OFFSET(4),

            /**
             * Each entry is a secondary key value followed by a 4 byte offset. The base of the
             * offset is platform dependent.
             */
            KEY2_OFFSET(8);

            EntryFormat(int size) {
                this.size = size;
            }

            /**
             * Gets the size of an entry in bytes.
             */
            public final int size;
        }

        /**
         * The low value in the key range (inclusive).
         */
        public final int low;

        /**
         * The high value in the key range (inclusive).
         */
        public final int high;

        /**
         * The size (in bytes) of each table entry.
         */
        public final EntryFormat entryFormat;

        public JumpTable(int position, int low, int high, EntryFormat entryFormat) {
            super(position);
            if (high <= low) {
                throw new IllegalArgumentException(String.format("low (%d) is not less than high(%d)", low, high));
            }
            this.low = low;
            this.high = high;
            this.entryFormat = entryFormat;
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + "@" + position + ": [" + low + " .. " + high + "]";
        }
    }

    /**
     * Describes a table of key and offset pairs. The offset in each table entry is relative to the
     * address of the table. This type of table maybe generated when translating a multi-way branch
     * based on a key value from a sparse value set (e.g. the {@code lookupswitch} JVM instruction).
     */
    public static final class LookupTable extends CodeAnnotation {
        private static final long serialVersionUID = 6797908737446993328L;
        /**
         * The number of entries in the table.
         */
        public final int npairs;

        /**
         * The size (in bytes) of entry's key.
         */
        public final int keySize;

        /**
         * The size (in bytes) of entry's offset value.
         */
        public final int offsetSize;

        public LookupTable(int position, int npairs, int keySize, int offsetSize) {
            super(position);
            this.npairs = npairs;
            this.keySize = keySize;
            this.offsetSize = offsetSize;
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + "@" + position + ": [npairs=" + npairs + ", keySize=" + keySize + ", offsetSize=" + offsetSize + "]";
        }
    }

    public static final String NEW_LINE = "\n";
    public static final String SECTION_DELIM = " <||@";
    public static final Pattern SECTION = Pattern.compile("(\\S+)\\s+(.*)", Pattern.DOTALL);
    public static final Pattern COMMENT = Pattern.compile("(\\d+)\\s+(.*)", Pattern.DOTALL);
    public static final Pattern OPERAND_COMMENT = COMMENT;
    public static final Pattern JUMP_TABLE = Pattern.compile("(\\d+)\\s+(\\S+)\\s+(-{0,1}\\d+)\\s+(-{0,1}\\d+)\\s*");
    public static final Pattern LOOKUP_TABLE = Pattern.compile("(\\d+)\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)\\s*");
    public static final Pattern HEX_CODE = Pattern.compile("(\\p{XDigit}+)(?:\\s+(\\p{XDigit}*))?");
    public static final Pattern PLATFORM = Pattern.compile("(\\S+)\\s+(\\S+)", Pattern.DOTALL);

    /**
     * Delimiter placed before a HexCodeFile when embedded in a string/stream.
     */
    public static final String EMBEDDED_HCF_OPEN = "<<<HexCodeFile";

    /**
     * Delimiter placed after a HexCodeFile when embedded in a string/stream.
     */
    public static final String EMBEDDED_HCF_CLOSE = "HexCodeFile>>>";

    /**
     * Map from a machine code position to a list of comments for the position.
     */
    public final Map<Integer, List<String>> comments = new TreeMap<>();

    /**
     * Map from a machine code position to a comment for the operands of the instruction at the
     * position.
     */
    public final Map<Integer, List<String>> operandComments = new TreeMap<>();

    public final byte[] code;

    public final ArrayList<JumpTable> jumpTables = new ArrayList<>();

    public final ArrayList<LookupTable> lookupTables = new ArrayList<>();

    public final String isa;

    public final int wordWidth;

    public final long startAddress;

    public HexCodeFile(byte[] code, long startAddress, String isa, int wordWidth) {
        this.code = code;
        this.startAddress = startAddress;
        this.isa = isa;
        this.wordWidth = wordWidth;
    }

    /**
     * Parses a string in the format produced by {@link #toString()} to produce a
     * {@link HexCodeFile} object.
     */
    public static HexCodeFile parse(String input, int sourceOffset, String source, String sourceName) {
        return new Parser(input, sourceOffset, source, sourceName).hcf;
    }

    /**
     * Formats this HexCodeFile as a string that can be parsed with {@link #parse}.
     */
    @Override
    public String toString() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        writeTo(baos);
        return baos.toString();
    }

    public void writeTo(OutputStream out) {
        PrintStream ps = out instanceof PrintStream ? (PrintStream) out : new PrintStream(out);
        ps.printf("Platform %s %d %s%n", isa, wordWidth, SECTION_DELIM);
        ps.printf("HexCode %x %s %s%n", startAddress, HexCodeFile.hexCodeString(code), SECTION_DELIM);

        for (JumpTable table : jumpTables) {
            ps.printf("JumpTable %d %d %d %d %s%n", table.position, table.entryFormat, table.low, table.high, SECTION_DELIM);
        }

        for (LookupTable table : lookupTables) {
            ps.printf("LookupTable %d %d %d %d %s%n", table.position, table.npairs, table.keySize, table.keySize, SECTION_DELIM);
        }

        for (Map.Entry<Integer, List<String>> e : comments.entrySet()) {
            int pos = e.getKey();
            for (String comment : e.getValue()) {
                ps.printf("Comment %d %s %s%n", pos, comment, SECTION_DELIM);
            }
        }

        for (Map.Entry<Integer, List<String>> e : operandComments.entrySet()) {
            int pos = e.getKey();
            for (String comment : e.getValue()) {
                ps.printf("OperandComment %d %s %s%n", pos, comment, SECTION_DELIM);
            }
        }
        ps.flush();
    }

    /**
     * Formats a byte array as a string of hex digits.
     */
    public static String hexCodeString(byte[] code) {
        StringBuilder sb = new StringBuilder(code.length * 2);
        for (int b : code) {
            String hex = Integer.toHexString(b & 0xff);
            if (hex.length() == 1) {
                sb.append('0');
            }
            sb.append(hex);
        }
        return sb.toString();
    }

    /**
     * Adds a comment to the list of comments for a given position.
     */
    public void addComment(int pos, String comment) {
        List<String> list = comments.computeIfAbsent(pos, k -> new ArrayList<>());
        list.add(encodeString(comment));
    }

    /**
     * Sets an operand comment for a given position.
     */
    public void addOperandComment(int pos, String comment) {
        List<String> list = operandComments.computeIfAbsent(pos, k -> new ArrayList<>());
        list.add(encodeString(comment));
    }

    /**
     * Modifies a string to mangle any substrings matching {@link #SECTION_DELIM}.
     */
    public static String encodeString(String string) {
        int index;
        String s = string;
        while ((index = s.indexOf(SECTION_DELIM)) != -1) {
            s = s.substring(0, index) + " < |@" + s.substring(index + SECTION_DELIM.length());
        }
        return s;
    }

    /**
     * Helper class to parse a string in the format produced by {@link HexCodeFile#toString()} and
     * produce a {@link HexCodeFile} object.
     */
    static class Parser {

        final String input;
        final String inputSource;
        String isa;
        int wordWidth;
        byte[] code;
        long startAddress;
        HexCodeFile hcf;

        Parser(String input, int sourceOffset, String source, String sourceName) {
            this.input = input;
            this.inputSource = sourceName;
            parseSections(sourceOffset, source);
        }

        void makeHCF() {
            if (hcf == null) {
                if (isa != null && wordWidth != 0 && code != null) {
                    hcf = new HexCodeFile(code, startAddress, isa, wordWidth);
                }
            }
        }

        void checkHCF(String section, int offset) {
            check(hcf != null, offset, section + " section must be after Platform and HexCode section");
        }

        void check(boolean condition, int offset, String message) {
            if (!condition) {
                throw error(offset, message);
            }
        }

        Error error(int offset, String message) {
            throw new Error(errorMessage(offset, message));
        }

        String errorMessage(int offset, String message) {
            assert offset < input.length();
            InputPos inputPos = filePos(offset);
            int lineEnd = input.indexOf(HexCodeFile.NEW_LINE, offset);
            int lineStart = offset - inputPos.col;
            String line = lineEnd == -1 ? input.substring(lineStart) : input.substring(lineStart, lineEnd);
            return String.format("%s:%d: %s%n%s%n%" + (inputPos.col + 1) + "s", inputSource, inputPos.line, message, line, "^");
        }

        static class InputPos {
            final int line;
            final int col;

            InputPos(int line, int col) {
                this.line = line;
                this.col = col;
            }
        }

        InputPos filePos(int index) {
            assert input != null;
            int lineStart = input.lastIndexOf(HexCodeFile.NEW_LINE, index) + 1;

            int pos = input.indexOf(HexCodeFile.NEW_LINE);
            int line = 1;
            while (pos > 0 && pos < index) {
                line++;
                pos = input.indexOf(HexCodeFile.NEW_LINE, pos + 1);
            }
            return new InputPos(line, index - lineStart);
        }

        void parseSections(int offset, String source) {
            assert input.startsWith(source, offset);
            int index = 0;
            int endIndex = source.indexOf(SECTION_DELIM);
            while (endIndex != -1) {
                while (source.charAt(index) <= ' ') {
                    index++;
                }
                String section = source.substring(index, endIndex).trim();
                parseSection(offset + index, section);
                index = endIndex + SECTION_DELIM.length();
                endIndex = source.indexOf(SECTION_DELIM, index);
            }
        }

        int parseInt(int offset, String value) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                throw error(offset, "Not a valid integer: " + value);
            }
        }

        void parseSection(int offset, String section) {
            if (section.isEmpty()) {
                return;
            }
            assert input.startsWith(section, offset);
            Matcher m = HexCodeFile.SECTION.matcher(section);
            check(m.matches(), offset, "Section does not match pattern " + HexCodeFile.SECTION);

            String header = m.group(1);
            String body = m.group(2);
            int headerOffset = offset + m.start(1);
            int bodyOffset = offset + m.start(2);

            switch (header) {
                case "Platform":
                    check(isa == null, bodyOffset, "Duplicate Platform section found");
                    m = HexCodeFile.PLATFORM.matcher(body);
                    check(m.matches(), bodyOffset, "Platform does not match pattern " + HexCodeFile.PLATFORM);
                    isa = m.group(1);
                    wordWidth = parseInt(bodyOffset + m.start(2), m.group(2));
                    makeHCF();
                    break;
                case "HexCode":
                    check(code == null, bodyOffset, "Duplicate Code section found");
                    m = HexCodeFile.HEX_CODE.matcher(body);
                    check(m.matches(), bodyOffset, "Code does not match pattern " + HexCodeFile.HEX_CODE);
                    String hexAddress = m.group(1);
                    startAddress = new BigInteger(hexAddress, 16).longValue();
                    String hexCode = m.group(2);
                    if (hexCode == null) {
                        code = new byte[0];
                    } else {
                        check((hexCode.length() % 2) == 0, bodyOffset, "Hex code length must be even");
                        code = new byte[hexCode.length() / 2];
                        for (int i = 0; i < code.length; i++) {
                            String hexByte = hexCode.substring(i * 2, (i + 1) * 2);
                            code[i] = (byte) Integer.parseInt(hexByte, 16);
                        }
                    }
                    makeHCF();
                    break;
                case "Comment": {
                    checkHCF("Comment", headerOffset);
                    m = HexCodeFile.COMMENT.matcher(body);
                    check(m.matches(), bodyOffset, "Comment does not match pattern " + HexCodeFile.COMMENT);
                    int pos = parseInt(bodyOffset + m.start(1), m.group(1));
                    String comment = m.group(2);
                    hcf.addComment(pos, comment);
                    break;
                }
                case "OperandComment": {
                    checkHCF("OperandComment", headerOffset);
                    m = HexCodeFile.OPERAND_COMMENT.matcher(body);
                    check(m.matches(), bodyOffset, "OperandComment does not match pattern " + HexCodeFile.OPERAND_COMMENT);
                    int pos = parseInt(bodyOffset + m.start(1), m.group(1));
                    String comment = m.group(2);
                    hcf.addOperandComment(pos, comment);
                    break;
                }
                case "JumpTable": {
                    checkHCF("JumpTable", headerOffset);
                    m = HexCodeFile.JUMP_TABLE.matcher(body);
                    check(m.matches(), bodyOffset, "JumpTable does not match pattern " + HexCodeFile.JUMP_TABLE);
                    int pos = parseInt(bodyOffset + m.start(1), m.group(1));
                    JumpTable.EntryFormat entryFormat = parseJumpTableEntryFormat(m, bodyOffset);
                    int low = parseInt(bodyOffset + m.start(3), m.group(3));
                    int high = parseInt(bodyOffset + m.start(4), m.group(4));
                    hcf.jumpTables.add(new JumpTable(pos, low, high, entryFormat));
                    break;
                }
                case "LookupTable": {
                    checkHCF("LookupTable", headerOffset);
                    m = HexCodeFile.LOOKUP_TABLE.matcher(body);
                    check(m.matches(), bodyOffset, "LookupTable does not match pattern " + HexCodeFile.LOOKUP_TABLE);
                    int pos = parseInt(bodyOffset + m.start(1), m.group(1));
                    int npairs = parseInt(bodyOffset + m.start(2), m.group(2));
                    int keySize = parseInt(bodyOffset + m.start(3), m.group(3));
                    int offsetSize = parseInt(bodyOffset + m.start(4), m.group(4));
                    hcf.lookupTables.add(new LookupTable(pos, npairs, keySize, offsetSize));
                    break;
                }
                default:
                    throw error(offset, "Unknown section header: " + header);
            }
        }

        private JumpTable.EntryFormat parseJumpTableEntryFormat(Matcher m, int bodyOffset) throws Error {
            String entryFormatName = m.group(2);
            JumpTable.EntryFormat entryFormat;
            if ("4".equals(entryFormatName)) {
                entryFormat = JumpTable.EntryFormat.OFFSET;
            } else if ("8".equals(entryFormatName)) {
                entryFormat = JumpTable.EntryFormat.KEY2_OFFSET;
            } else {
                try {
                    entryFormat = JumpTable.EntryFormat.valueOf(entryFormatName);
                } catch (IllegalArgumentException e) {
                    throw error(bodyOffset + m.start(2), "Not a valid " + JumpTable.EntryFormat.class.getSimpleName() + " value: " + entryFormatName);
                }
            }
            return entryFormat;
        }

    }
}
