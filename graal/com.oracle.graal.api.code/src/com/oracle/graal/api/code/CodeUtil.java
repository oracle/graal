/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.api.code;

import java.lang.reflect.*;
import java.util.*;

import com.oracle.graal.api.meta.*;

/**
 * Miscellaneous collection of utility methods used by {@code com.oracle.graal.api.code} and its
 * clients.
 */
public class CodeUtil {

    public static final String NEW_LINE = String.format("%n");

    public static final int K = 1024;
    public static final int M = 1024 * 1024;

    public static boolean isOdd(int n) {
        return (n & 1) == 1;
    }

    public static boolean isEven(int n) {
        return (n & 1) == 0;
    }

    /**
     * Checks whether the specified integer is a power of two.
     * 
     * @param val the value to check
     * @return {@code true} if the value is a power of two; {@code false} otherwise
     */
    public static boolean isPowerOf2(int val) {
        return val > 0 && (val & val - 1) == 0;
    }

    /**
     * Checks whether the specified long is a power of two.
     * 
     * @param val the value to check
     * @return {@code true} if the value is a power of two; {@code false} otherwise
     */
    public static boolean isPowerOf2(long val) {
        return val > 0 && (val & val - 1) == 0;
    }

    /**
     * Computes the log (base 2) of the specified integer, rounding down. (E.g {@code log2(8) = 3},
     * {@code log2(21) = 4} )
     * 
     * @param val the value
     * @return the log base 2 of the value
     */
    public static int log2(int val) {
        assert val > 0;
        return (Integer.SIZE - 1) - Integer.numberOfLeadingZeros(val);
    }

    /**
     * Computes the log (base 2) of the specified long, rounding down. (E.g {@code log2(8) = 3},
     * {@code log2(21) = 4})
     * 
     * @param val the value
     * @return the log base 2 of the value
     */
    public static int log2(long val) {
        assert val > 0;
        return (Long.SIZE - 1) - Long.numberOfLeadingZeros(val);
    }

    /**
     * Formats the values in a frame as a tabulated string.
     * 
     * @param frame
     * @return the values in {@code frame} as a tabulated string
     */
    public static String tabulateValues(BytecodeFrame frame) {
        int cols = Math.max(frame.numLocals, Math.max(frame.numStack, frame.numLocks));
        assert cols > 0;
        ArrayList<Object> cells = new ArrayList<>();
        cells.add("");
        for (int i = 0; i < cols; i++) {
            cells.add(i);
        }
        cols++;
        if (frame.numLocals != 0) {
            cells.add("locals:");
            cells.addAll(Arrays.asList(frame.values).subList(0, frame.numLocals));
            cells.addAll(Collections.nCopies(cols - frame.numLocals - 1, ""));
        }
        if (frame.numStack != 0) {
            cells.add("stack:");
            cells.addAll(Arrays.asList(frame.values).subList(frame.numLocals, frame.numLocals + frame.numStack));
            cells.addAll(Collections.nCopies(cols - frame.numStack - 1, ""));
        }
        if (frame.numLocks != 0) {
            cells.add("locks:");
            cells.addAll(Arrays.asList(frame.values).subList(frame.numLocals + frame.numStack, frame.values.length));
            cells.addAll(Collections.nCopies(cols - frame.numLocks - 1, ""));
        }
        Object[] cellArray = cells.toArray();
        for (int i = 0; i < cellArray.length; i++) {
            if ((i % cols) != 0) {
                cellArray[i] = "|" + cellArray[i];
            }
        }
        return CodeUtil.tabulate(cellArray, cols, 1, 1);
    }

    /**
     * Formats a given table as a string. The value of each cell is produced by
     * {@link String#valueOf(Object)}.
     * 
     * @param cells the cells of the table in row-major order
     * @param cols the number of columns per row
     * @param lpad the number of space padding inserted before each formatted cell value
     * @param rpad the number of space padding inserted after each formatted cell value
     * @return a string with one line per row and each column left-aligned
     */
    public static String tabulate(Object[] cells, int cols, int lpad, int rpad) {
        int rows = (cells.length + (cols - 1)) / cols;
        int[] colWidths = new int[cols];
        for (int col = 0; col < cols; col++) {
            for (int row = 0; row < rows; row++) {
                int index = col + (row * cols);
                if (index < cells.length) {
                    Object cell = cells[index];
                    colWidths[col] = Math.max(colWidths[col], String.valueOf(cell).length());
                }
            }
        }
        StringBuilder sb = new StringBuilder();
        String nl = NEW_LINE;
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                int index = col + (row * cols);
                if (index < cells.length) {
                    for (int i = 0; i < lpad; i++) {
                        sb.append(' ');
                    }
                    Object cell = cells[index];
                    String s = String.valueOf(cell);
                    int w = s.length();
                    sb.append(s);
                    while (w < colWidths[col]) {
                        sb.append(' ');
                        w++;
                    }
                    for (int i = 0; i < rpad; i++) {
                        sb.append(' ');
                    }
                }
            }
            sb.append(nl);
        }
        return sb.toString();
    }

    /**
     * Appends a formatted code position to a {@link StringBuilder}.
     * 
     * @param sb the {@link StringBuilder} to append to
     * @param pos the code position to format and append to {@code sb}
     * @return the value of {@code sb}
     */
    public static StringBuilder append(StringBuilder sb, BytecodePosition pos) {
        MetaUtil.appendLocation(sb.append("at "), pos.getMethod(), pos.getBCI());
        if (pos.getCaller() != null) {
            sb.append(NEW_LINE);
            append(sb, pos.getCaller());
        }
        return sb;
    }

    /**
     * Appends a formatted frame to a {@link StringBuilder}.
     * 
     * @param sb the {@link StringBuilder} to append to
     * @param frame the frame to format and append to {@code sb}
     * @return the value of {@code sb}
     */
    public static StringBuilder append(StringBuilder sb, BytecodeFrame frame) {
        MetaUtil.appendLocation(sb.append("at "), frame.getMethod(), frame.getBCI());
        if (frame.values != null && frame.values.length > 0) {
            sb.append(NEW_LINE);
            String table = tabulateValues(frame);
            String[] rows = table.split(NEW_LINE);
            for (int i = 0; i < rows.length; i++) {
                String row = rows[i];
                if (!row.trim().isEmpty()) {
                    sb.append("  ").append(row);
                    if (i != rows.length - 1) {
                        sb.append(NEW_LINE);
                    }
                }
            }
        }
        if (frame.caller() != null) {
            sb.append(NEW_LINE);
            append(sb, frame.caller());
        } else if (frame.getCaller() != null) {
            sb.append(NEW_LINE);
            append(sb, frame.getCaller());
        }
        return sb;
    }

    public interface RefMapFormatter {

        String formatStackSlot(int frameRefMapIndex);

        String formatRegister(int regRefMapIndex);
    }

    /**
     * Formats a location present in a register or frame reference map.
     */
    public static class DefaultRefMapFormatter implements RefMapFormatter {

        /**
         * The size of a stack slot.
         */
        public final int slotSize;

        /**
         * The register used as the frame pointer.
         */
        public final Register fp;

        public final Architecture arch;

        private final Register[] registers;

        /**
         * The offset (in bytes) from the slot pointed to by {@link #fp} to the slot corresponding
         * to bit 0 in the frame reference map.
         */
        public final int refMapToFPOffset;

        public DefaultRefMapFormatter(Architecture arch, int slotSize, Register fp, int refMapToFPOffset) {
            this.arch = arch;
            this.slotSize = slotSize;
            this.fp = fp;
            this.refMapToFPOffset = refMapToFPOffset;
            this.registers = arch.getRegisters();
        }

        public String formatStackSlot(int frameRefMapIndex) {
            int refMapOffset = frameRefMapIndex * slotSize;
            int fpOffset = refMapOffset + refMapToFPOffset;
            if (fpOffset >= 0) {
                return fp + "+" + fpOffset;
            }
            return fp.name + fpOffset;
        }

        public String formatRegister(int regRefMapIndex) {
            return registers[regRefMapIndex].toString();
        }
    }

    /**
     * Appends a formatted debug info to a {@link StringBuilder}.
     * 
     * @param sb the {@link StringBuilder} to append to
     * @param info the debug info to format and append to {@code sb}
     * @return the value of {@code sb}
     */
    public static StringBuilder append(StringBuilder sb, DebugInfo info, RefMapFormatter formatter) {
        String nl = NEW_LINE;
        ReferenceMap refMap = info.getReferenceMap();
        if (refMap != null && refMap.hasRegisterRefMap()) {
            sb.append("  reg-ref-map:");
            refMap.appendRegisterMap(sb, formatter);
            sb.append(nl);
        }
        if (refMap != null && refMap.hasFrameRefMap()) {
            sb.append("frame-ref-map:");
            refMap.appendFrameMap(sb, formatter);
            sb.append(nl);
        }
        RegisterSaveLayout calleeSaveInfo = info.getCalleeSaveInfo();
        if (calleeSaveInfo != null) {
            sb.append("callee-save-info:").append(nl);
            Map<Integer, Register> map = calleeSaveInfo.slotsToRegisters(true);
            for (Map.Entry<Integer, Register> e : map.entrySet()) {
                sb.append("    ").append(e.getValue()).append(" -> ").append(formatter.formatStackSlot(e.getKey())).append(nl);
            }
        }
        BytecodeFrame frame = info.frame();
        if (frame != null) {
            append(sb, frame);
        } else if (info.getBytecodePosition() != null) {
            append(sb, info.getBytecodePosition());
        }
        return sb;
    }

    /**
     * Create a calling convention from a {@link ResolvedJavaMethod}.
     */
    public static CallingConvention getCallingConvention(CodeCacheProvider codeCache, CallingConvention.Type type, ResolvedJavaMethod method, boolean stackOnly) {
        Signature sig = method.getSignature();
        JavaType retType = sig.getReturnType(null);
        int sigCount = sig.getParameterCount(false);
        JavaType[] argTypes;
        int argIndex = 0;
        if (!Modifier.isStatic(method.getModifiers())) {
            argTypes = new JavaType[sigCount + 1];
            argTypes[argIndex++] = method.getDeclaringClass();
        } else {
            argTypes = new JavaType[sigCount];
        }
        for (int i = 0; i < sigCount; i++) {
            argTypes[argIndex++] = sig.getParameterType(i, null);
        }

        RegisterConfig registerConfig = codeCache.getRegisterConfig();
        return registerConfig.getCallingConvention(type, retType, argTypes, codeCache.getTarget(), stackOnly);
    }
}
