/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.criutils;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.regex.*;

import com.sun.cri.ci.*;
import com.sun.cri.ci.CiTargetMethod.CodeAnnotation;
import com.sun.cri.ci.CiTargetMethod.CodeComment;
import com.sun.cri.ci.CiTargetMethod.JumpTable;
import com.sun.cri.ci.CiTargetMethod.LookupTable;


/**
 * A HexCodeFile is a textual format for representing a chunk of machine code along
 * with extra information that can be used to enhance a disassembly of the code.
 *
 * A pseudo grammar for a HexCodeFile is given below.
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
 *     JumpTable ::= "JumpTable" Position EntrySize Low High
 *
 *     LookupTable ::= "LookupTable" Position NPairs KeySize OffsetSize
 *
 *     Position, EntrySize, Low, High, NPairs KeySize OffsetSize ::= int
 *
 *     Delim := "<||@"
 * </pre>
 *
 * There must be exactly one HexCode and Platform part in a HexCodeFile. The length of HexDigits must be even
 * as each pair of digits represents a single byte.
 * <p>
 * Below is an example of a valid Code input:
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

    public static final String NEW_LINE = CiUtil.NEW_LINE;
    public static final String SECTION_DELIM = " <||@";
    public static final Pattern SECTION = Pattern.compile("(\\S+)\\s+(.*)", Pattern.DOTALL);
    public static final Pattern COMMENT = Pattern.compile("(\\d+)\\s+(.*)", Pattern.DOTALL);
    public static final Pattern OPERAND_COMMENT = COMMENT;
    public static final Pattern JUMP_TABLE = Pattern.compile("(\\d+)\\s+(\\d+)\\s+(-{0,1}\\d+)\\s+(-{0,1}\\d+)\\s*");
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
    public final Map<Integer, List<String>> comments = new TreeMap<Integer, List<String>>();

    /**
     * Map from a machine code position to a comment for the operands of the instruction at the position.
     */
    public final Map<Integer, String> operandComments = new TreeMap<Integer, String>();

    public final byte[] code;

    public final ArrayList<JumpTable> jumpTables = new ArrayList<JumpTable>();

    public final ArrayList<LookupTable> lookupTables = new ArrayList<LookupTable>();

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
     * Parses a string in the format produced by {@link #toString()} to produce a {@link HexCodeFile} object.
     */
    public static HexCodeFile parse(String source, String sourceName) {
        return new Parser(source, sourceName).hcf;
    }

    /**
     * Formats this HexCodeFile as a string that can be parsed with {@link #parse(String, String)}.
     */
    @Override
    public String toString() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        writeTo(baos);
        return baos.toString();
    }

    public String toEmbeddedString() {
        return EMBEDDED_HCF_OPEN + NEW_LINE + toString() + EMBEDDED_HCF_CLOSE;
    }

    public void writeTo(OutputStream out) {
        PrintStream ps = out instanceof PrintStream ? (PrintStream) out : new PrintStream(out);
        ps.printf("Platform %s %d %s%n",  isa, wordWidth, SECTION_DELIM);
        ps.printf("HexCode %x %s %s%n", startAddress, HexCodeFile.hexCodeString(code), SECTION_DELIM);

        for (JumpTable table : jumpTables) {
            ps.printf("JumpTable %d %d %d %d %s%n", table.position, table.entrySize, table.low, table.high, SECTION_DELIM);
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

        for (Map.Entry<Integer, String> e : operandComments.entrySet()) {
            ps.printf("OperandComment %d %s %s%n", e.getKey(), e.getValue(), SECTION_DELIM);
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
        List<String> list = comments.get(pos);
        if (list == null) {
            list = new ArrayList<String>();
            comments.put(pos, list);
        }
        list.add(encodeString(comment));
    }

    /**
     * Sets an operand comment for a given position.
     *
     * @return the previous operand comment for {@code pos}
     */
    public String addOperandComment(int pos, String comment) {
        return operandComments.put(pos, encodeString(comment));
    }

    /**
     * Adds any jump tables, lookup tables or code comments from a list of code annotations.
     */
    public static void addAnnotations(HexCodeFile hcf, List<CodeAnnotation> annotations) {
        if (annotations == null || annotations.isEmpty()) {
            return;
        }
        for (CodeAnnotation a : annotations) {
            if (a instanceof JumpTable) {
                JumpTable table = (JumpTable) a;
                hcf.jumpTables.add(table);
            } else if (a instanceof LookupTable) {
                LookupTable table = (LookupTable) a;
                hcf.lookupTables.add(table);
            } else if (a instanceof CodeComment) {
                CodeComment comment = (CodeComment) a;
                hcf.addComment(comment.position, comment.value);
            }
        }
    }

    /**
     * Modifies a string to mangle any substrings matching {@link #SECTION_DELIM}.
     */
    public static String encodeString(String s) {
        int index;
        while ((index = s.indexOf(SECTION_DELIM)) != -1) {
            s = s.substring(0, index) + " < |@" + s.substring(index + SECTION_DELIM.length());
        }
        return s;
    }

    /**
     * Helper class to parse a string in the format produced by {@link HexCodeFile#toString()}
     * and produce a {@link HexCodeFile} object.
     */
    static class Parser {
        private static final Field offsetField = stringField("offset");
        private static final Field valueField = stringField("value");

        static Field stringField(String name) {
            try {
                Field field = String.class.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (Exception e) {
                throw new Error("Could not get reflective access to field " + String.class.getName() + "." + name);
            }
        }

        final String input;
        final String inputSource;
        String isa;
        int wordWidth;
        byte[] code;
        long startAddress;
        HexCodeFile hcf;

        Parser(String source, String sourceName) {
            this.input = storage(source);
            this.inputSource = sourceName;
            parseSections(source);
        }

        void makeHCF() {
            if (hcf == null) {
                if (isa != null && wordWidth != 0 && code != null) {
                    hcf = new HexCodeFile(code, startAddress, isa, wordWidth);
                }
            }
        }

        void checkHCF(String section, String where) {
            check(hcf != null, where, section + " section must be after Platform and HexCode section");
        }

        void check(boolean condition, String where, String message) {
            if (!condition) {
                error(where, message);
            }
        }

        int offset(String s) {
            try {
                return offsetField.getInt(s);
            } catch (Exception e) {
                throw new Error("Could not read value of field " + offsetField, e);
            }
        }

        /**
         * Gets a string corresponding to the storage char array for a given string.
         */
        String storage(String s) {
            try {
                char[] value = (char[]) valueField.get(s);
                if (offset(s) == 0 && value.length == s.length()) {
                    return s;
                }
                return new String(value);
            } catch (Exception e) {
                throw new Error("Could not read value of field " + valueField, e);
            }
        }

        Error error(String where, String message) {
            return error(offset(where), message);
        }

        Error error(int offset, String message) {
            throw new Error(errorMessage(offset, message));
        }

        void warning(String where, String message) {
            System.err.println("Warning: " + errorMessage(offset(where), message));
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
            public InputPos(int line, int col) {
                this.line = line;
                this.col = col;
            }
        }

        InputPos filePos(int index) {
            assert input != null;
            int line = 1;
            int lineEnd = 0;
            int lineStart = 0;
            while ((lineEnd = input.indexOf(HexCodeFile.NEW_LINE, lineStart)) != -1) {
                if (lineEnd < index) {
                    line++;
                    lineStart = lineEnd + HexCodeFile.NEW_LINE.length();
                } else {
                    break;
                }
            }
            return new InputPos(line, index - lineStart);
        }

        void parseSections(String source) {
            int index = 0;
            int endIndex = source.indexOf(SECTION_DELIM);
            while (endIndex != -1) {
                String section = source.substring(index, endIndex).trim();
                parseSection(section);
                index = endIndex + SECTION_DELIM.length();
                endIndex = source.indexOf(SECTION_DELIM, index);
            }
        }

        int parseInt(String value) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                throw error(value, "Not a valid integer: " + value);
            }
        }

        void parseSection(String section) {
            if (section.isEmpty()) {
                return;
            }
            Matcher m = HexCodeFile.SECTION.matcher(section);
            check(m.matches(), section, "Section does not match pattern " + HexCodeFile.SECTION);

            String header = m.group(1);
            String body = m.group(2);

            if (header.equals("Platform")) {
                check(isa == null, body, "Duplicate Platform section found");
                m = HexCodeFile.PLATFORM.matcher(body);
                check(m.matches(), body, "Platform does not match pattern " + HexCodeFile.PLATFORM);
                isa = m.group(1);
                wordWidth = parseInt(m.group(2));
                makeHCF();
            } else if (header.equals("HexCode")) {
                check(code == null, body, "Duplicate Code section found");
                m = HexCodeFile.HEX_CODE.matcher(body);
                check(m.matches(), body, "Code does not match pattern " + HexCodeFile.HEX_CODE);
                String hexAddress = m.group(1);
                startAddress = Long.valueOf(hexAddress, 16);
                String hexCode = m.group(2);
                if (hexCode == null) {
                    code = new byte[0];
                } else {
                    check((hexCode.length() % 2) == 0, body, "Hex code length must be even");
                    code = new byte[hexCode.length() / 2];
                    for (int i = 0; i < code.length; i++) {
                        String hexByte = hexCode.substring(i * 2, (i + 1) * 2);
                        code[i] = (byte) Integer.parseInt(hexByte, 16);
                    }
                }
                makeHCF();
            } else if (header.equals("Comment")) {
                checkHCF("Comment", header);
                m = HexCodeFile.COMMENT.matcher(body);
                check(m.matches(), body, "Comment does not match pattern " + HexCodeFile.COMMENT);
                int pos = parseInt(m.group(1));
                String comment = m.group(2);
                hcf.addComment(pos, comment);
            } else if (header.equals("OperandComment")) {
                checkHCF("OperandComment", header);
                m = HexCodeFile.OPERAND_COMMENT.matcher(body);
                check(m.matches(), body, "OperandComment does not match pattern " + HexCodeFile.OPERAND_COMMENT);
                int pos = parseInt(m.group(1));
                String comment = m.group(2);
                hcf.addOperandComment(pos, comment);
            } else if (header.equals("JumpTable")) {
                checkHCF("JumpTable", header);
                m = HexCodeFile.JUMP_TABLE.matcher(body);
                check(m.matches(), body, "JumpTable does not match pattern " + HexCodeFile.JUMP_TABLE);
                int pos = parseInt(m.group(1));
                int entrySize = parseInt(m.group(2));
                int low = parseInt(m.group(3));
                int high = parseInt(m.group(4));
                hcf.jumpTables.add(new JumpTable(pos, low, high, entrySize));
            } else if (header.equals("LookupTable")) {
                checkHCF("LookupTable", header);
                m = HexCodeFile.LOOKUP_TABLE.matcher(body);
                check(m.matches(), body, "LookupTable does not match pattern " + HexCodeFile.LOOKUP_TABLE);
                int pos = parseInt(m.group(1));
                int npairs = parseInt(m.group(2));
                int keySize = parseInt(m.group(3));
                int offsetSize = parseInt(m.group(4));
                hcf.lookupTables.add(new LookupTable(pos, npairs, keySize, offsetSize));
            } else {
                error(section, "Unknown section header: " + header);
            }
        }
    }
}
