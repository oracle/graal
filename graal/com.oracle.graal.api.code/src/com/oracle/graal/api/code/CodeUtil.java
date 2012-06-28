/*
 * Copyright (c) 2010, 2011, Oracle and/or its affiliates. All rights reserved.
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

import static java.lang.reflect.Modifier.*;

import java.lang.annotation.*;
import java.util.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.meta.JavaTypeProfile.*;

/**
 * Miscellaneous collection of utility methods used in the {@code CRI} project.
 */
public class CodeUtil {

    public static final String NEW_LINE = String.format("%n");

    /**
     * Gets the annotation of a particular type for a formal parameter of a given method.
     *
     * @param annotationClass the Class object corresponding to the annotation type
     * @param parameterIndex the index of a formal parameter of {@code method}
     * @param method the method for which a parameter annotation is being requested
     * @return the annotation of type {@code annotationClass} for the formal parameter present, else null
     * @throws IndexOutOfBoundsException if {@code parameterIndex} does not denote a formal parameter
     */
    public static <T extends Annotation> T getParameterAnnotation(Class<T> annotationClass, int parameterIndex, ResolvedJavaMethod method) {
        if (parameterIndex >= 0) {
            Annotation[][] parameterAnnotations = method.getParameterAnnotations();
            for (Annotation a : parameterAnnotations[parameterIndex]) {
                if (a.annotationType() == annotationClass) {
                    return annotationClass.cast(a);
                }
            }
        }
        return null;
    }

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
        return val != 0 && (val & val - 1) == 0;
    }

    /**
     * Checks whether the specified long is a power of two.
     *
     * @param val the value to check
     * @return {@code true} if the value is a power of two; {@code false} otherwise
     */
    public static boolean isPowerOf2(long val) {
        return val != 0 && (val & val - 1) == 0;
    }

    /**
     * Computes the log (base 2) of the specified integer, rounding down. (E.g {@code log2(8) = 3}, {@code log2(21) = 4}
     * )
     *
     * @param val the value
     * @return the log base 2 of the value
     */
    public static int log2(int val) {
        assert val > 0 && isPowerOf2(val);
        return 31 - Integer.numberOfLeadingZeros(val);
    }

    /**
     * Computes the log (base 2) of the specified long, rounding down. (E.g {@code log2(8) = 3}, {@code log2(21) = 4})
     *
     * @param val the value
     * @return the log base 2 of the value
     */
    public static int log2(long val) {
        assert val > 0 && isPowerOf2(val);
        return 63 - Long.numberOfLeadingZeros(val);
    }

    /**
     * Gets a string for a given method formatted according to a given format specification. A format specification is
     * composed of characters that are to be copied verbatim to the result and specifiers that denote an attribute of
     * the method that is to be copied to the result. A specifier is a single character preceded by a '%' character. The
     * accepted specifiers and the method attributes they denote are described below:
     *
     * <pre>
     *     Specifier | Description                                          | Example(s)
     *     ----------+------------------------------------------------------------------------------------------
     *     'R'       | Qualified return type                                | "int" "java.lang.String"
     *     'r'       | Unqualified return type                              | "int" "String"
     *     'H'       | Qualified holder                                     | "java.util.Map.Entry"
     *     'h'       | Unqualified holder                                   | "Entry"
     *     'n'       | Method name                                          | "add"
     *     'P'       | Qualified parameter types, separated by ', '         | "int, java.lang.String"
     *     'p'       | Unqualified parameter types, separated by ', '       | "int, String"
     *     'f'       | Indicator if method is unresolved, static or virtual | "unresolved" "static" "virtual"
     *     '%'       | A '%' character                                      | "%"
     * </pre>
     *
     * @param format a format specification
     * @param method the method to be formatted
     * @return the result of formatting this method according to {@code format}
     * @throws IllegalFormatException if an illegal specifier is encountered in {@code format}
     */
    public static String format(String format, JavaMethod method) throws IllegalFormatException {
        final StringBuilder sb = new StringBuilder();
        int index = 0;
        Signature sig = null;
        while (index < format.length()) {
            final char ch = format.charAt(index++);
            if (ch == '%') {
                if (index >= format.length()) {
                    throw new UnknownFormatConversionException("An unquoted '%' character cannot terminate a method format specification");
                }
                final char specifier = format.charAt(index++);
                boolean qualified = false;
                switch (specifier) {
                    case 'R':
                        qualified = true;
                        // fall through
                    case 'r': {
                        if (sig == null) {
                            sig = method.signature();
                        }
                        sb.append(MetaUtil.toJavaName(sig.returnType(null), qualified));
                        break;
                    }
                    case 'H':
                        qualified = true;
                        // fall through
                    case 'h': {
                        sb.append(MetaUtil.toJavaName(method.holder(), qualified));
                        break;
                    }
                    case 'n': {
                        sb.append(method.name());
                        break;
                    }
                    case 'P':
                        qualified = true;
                        // fall through
                    case 'p': {
                        if (sig == null) {
                            sig = method.signature();
                        }
                        for (int i = 0; i < sig.argumentCount(false); i++) {
                            if (i != 0) {
                                sb.append(", ");
                            }
                            sb.append(MetaUtil.toJavaName(sig.argumentTypeAt(i, null), qualified));
                        }
                        break;
                    }
                    case 'f': {
                        sb.append(!(method instanceof ResolvedJavaMethod) ? "unresolved" : isStatic(((ResolvedJavaMethod) method).accessFlags()) ? "static" : "virtual");
                        break;
                    }
                    case '%': {
                        sb.append('%');
                        break;
                    }
                    default: {
                        throw new UnknownFormatConversionException(String.valueOf(specifier));
                    }
                }
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    /**
     * Gets a string for a given field formatted according to a given format specification. A format specification is
     * composed of characters that are to be copied verbatim to the result and specifiers that denote an attribute of
     * the field that is to be copied to the result. A specifier is a single character preceded by a '%' character. The
     * accepted specifiers and the field attributes they denote are described below:
     *
     * <pre>
     *     Specifier | Description                                          | Example(s)
     *     ----------+------------------------------------------------------------------------------------------
     *     'T'       | Qualified type                                       | "int" "java.lang.String"
     *     't'       | Unqualified type                                     | "int" "String"
     *     'H'       | Qualified holder                                     | "java.util.Map.Entry"
     *     'h'       | Unqualified holder                                   | "Entry"
     *     'n'       | Field name                                           | "age"
     *     'f'       | Indicator if field is unresolved, static or instance | "unresolved" "static" "instance"
     *     '%'       | A '%' character                                      | "%"
     * </pre>
     *
     * @param format a format specification
     * @param field the field to be formatted
     * @return the result of formatting this field according to {@code format}
     * @throws IllegalFormatException if an illegal specifier is encountered in {@code format}
     */
    public static String format(String format, JavaField field) throws IllegalFormatException {
        final StringBuilder sb = new StringBuilder();
        int index = 0;
        JavaType type = field.type();
        while (index < format.length()) {
            final char ch = format.charAt(index++);
            if (ch == '%') {
                if (index >= format.length()) {
                    throw new UnknownFormatConversionException("An unquoted '%' character cannot terminate a field format specification");
                }
                final char specifier = format.charAt(index++);
                boolean qualified = false;
                switch (specifier) {
                    case 'T':
                        qualified = true;
                        // fall through
                    case 't': {
                        sb.append(MetaUtil.toJavaName(type, qualified));
                        break;
                    }
                    case 'H':
                        qualified = true;
                        // fall through
                    case 'h': {
                        sb.append(MetaUtil.toJavaName(field.holder(), qualified));
                        break;
                    }
                    case 'n': {
                        sb.append(field.name());
                        break;
                    }
                    case 'f': {
                        sb.append(!(field instanceof ResolvedJavaField) ? "unresolved" : isStatic(((ResolvedJavaField) field).accessFlags()) ? "static" : "instance");
                        break;
                    }
                    case '%': {
                        sb.append('%');
                        break;
                    }
                    default: {
                        throw new UnknownFormatConversionException(String.valueOf(specifier));
                    }
                }
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    /**
     * Converts a Java source-language class name into the internal form.
     *
     * @param className the class name
     * @return the internal name form of the class name
     */
    public static String toInternalName(String className) {
        return "L" + className.replace('.', '/') + ";";
    }

    /**
     * Prepends the String {@code indentation} to every line in String {@code lines}, including a possibly non-empty
     * line following the final newline.
     */
    public static String indent(String lines, String indentation) {
        if (lines.length() == 0) {
            return lines;
        }
        final String newLine = "\n";
        if (lines.endsWith(newLine)) {
            return indentation + (lines.substring(0, lines.length() - 1)).replace(newLine, newLine + indentation) + newLine;
        }
        return indentation + lines.replace(newLine, newLine + indentation);
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
     * Formats a given table as a string. The value of each cell is produced by {@link String#valueOf(Object)}.
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
     * Convenient shortcut for calling {@link #appendLocation(StringBuilder, ResolvedJavaMethod, int)} without having to supply a
     * a {@link StringBuilder} instance and convert the result to a string.
     */
    public static String toLocation(ResolvedJavaMethod method, int bci) {
        return appendLocation(new StringBuilder(), method, bci).toString();
    }

    /**
     * Appends a string representation of a location specified by a given method and bci to a given
     * {@link StringBuilder}. If a stack trace element with a non-null file name and non-negative line number is
     * {@linkplain ResolvedJavaMethod#toStackTraceElement(int) available} for the given method, then the string returned is the
     * {@link StackTraceElement#toString()} value of the stack trace element, suffixed by the bci location. For example:
     *
     * <pre>
     *     java.lang.String.valueOf(String.java:2930) [bci: 12]
     * </pre>
     *
     * Otherwise, the string returned is the value of {@code CiUtil.format("%H.%n(%p)"}, suffixed by the bci location.
     * For example:
     *
     * <pre>
     *     java.lang.String.valueOf(int) [bci: 12]
     * </pre>
     *
     * @param sb
     * @param method
     * @param bci
     */
    public static StringBuilder appendLocation(StringBuilder sb, ResolvedJavaMethod method, int bci) {
        if (method != null) {
            StackTraceElement ste = method.toStackTraceElement(bci);
            if (ste.getFileName() != null && ste.getLineNumber() > 0) {
                sb.append(ste);
            } else {
                sb.append(CodeUtil.format("%H.%n(%p)", method));
            }
        } else {
            sb.append("Null method");
        }
        return sb.append(" [bci: ").append(bci).append(']');
    }

    /**
     * Appends a formatted code position to a {@link StringBuilder}.
     *
     * @param sb the {@link StringBuilder} to append to
     * @param pos the code position to format and append to {@code sb}
     * @return the value of {@code sb}
     */
    public static StringBuilder append(StringBuilder sb, BytecodePosition pos) {
        appendLocation(sb.append("at "), pos.getMethod(), pos.getBCI());
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
        appendLocation(sb.append("at "), frame.getMethod(), frame.getBCI());
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

    /**
     * Formats a location present in a register or frame reference map.
     */
    public static class RefMapFormatter {

        /**
         * The size of a stack slot.
         */
        public final int slotSize;

        /**
         * The register used as the frame pointer.
         */
        public final Register fp;

        public final Architecture arch;

        /**
         * The offset (in bytes) from the slot pointed to by {@link #fp} to the slot corresponding to bit 0 in the frame
         * reference map.
         */
        public final int refMapToFPOffset;

        public RefMapFormatter(Architecture arch, int slotSize, Register fp, int refMapToFPOffset) {
            this.arch = arch;
            this.slotSize = slotSize;
            this.fp = fp;
            this.refMapToFPOffset = refMapToFPOffset;
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
            return arch.registers[regRefMapIndex].toString();
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
        if (info.hasRegisterRefMap()) {
            sb.append("  reg-ref-map:");
            BitSet bm = info.getRegisterRefMap();
            if (formatter != null) {
                for (int reg = bm.nextSetBit(0); reg >= 0; reg = bm.nextSetBit(reg + 1)) {
                    sb.append(" " + formatter.formatRegister(reg));
                }
            }
            sb.append(' ').append(bm).append(nl);
        }
        if (info.hasStackRefMap()) {
            sb.append("frame-ref-map:");
            BitSet bm = info.getFrameRefMap();
            if (formatter != null) {
                for (int i = bm.nextSetBit(0); i >= 0; i = bm.nextSetBit(i + 1)) {
                    sb.append(" " + formatter.formatStackSlot(i));
                }
            }
            sb.append(' ').append(bm).append(nl);
        }
        BytecodeFrame frame = info.frame();
        if (frame != null) {
            append(sb, frame);
        } else if (info.getBytecodePosition() != null) {
            append(sb, info.getBytecodePosition());
        }
        return sb;
    }

    public static Kind[] signatureToKinds(ResolvedJavaMethod method) {
        Kind receiver = isStatic(method.accessFlags()) ? null : method.holder().kind();
        return signatureToKinds(method.signature(), receiver);
    }

    public static Kind[] signatureToKinds(Signature signature, Kind receiverKind) {
        int args = signature.argumentCount(false);
        Kind[] result;
        int i = 0;
        if (receiverKind != null) {
            result = new Kind[args + 1];
            result[0] = receiverKind;
            i = 1;
        } else {
            result = new Kind[args];
        }
        for (int j = 0; j < args; j++) {
            result[i + j] = signature.argumentKindAt(j);
        }
        return result;
    }

    public static Class< ? >[] signatureToTypes(Signature signature, ResolvedJavaType accessingClass) {
        int count = signature.argumentCount(false);
        Class< ? >[] result = new Class< ? >[count];
        for (int i = 0; i < result.length; ++i) {
            result[i] = signature.argumentTypeAt(i, accessingClass).resolve(accessingClass).toJava();
        }
        return result;
    }

    /**
     * Formats some profiling information associated as a string.
     *
     * @param info the profiling info to format
     * @param method an optional method that augments the profile string returned
     * @param sep the separator to use for each separate profile record
     */
    public static String profileToString(ProfilingInfo info, ResolvedJavaMethod method, String sep) {
        StringBuilder buf = new StringBuilder(100);
        if (method != null) {
            buf.append(String.format("canBeStaticallyBound: %b%s", method.canBeStaticallyBound(), sep)).
            append(String.format("invocationCount: %d%s", method.invocationCount(), sep));
        }
        for (int i = 0; i < info.codeSize(); i++) {
            if (info.getExecutionCount(i) != -1) {
                buf.append(String.format("executionCount@%d: %d%s", i, info.getExecutionCount(i), sep));
            }

            if (info.getBranchTakenProbability(i) != -1) {
                buf.append(String.format("branchProbability@%d: %.3f%s", i, info.getBranchTakenProbability(i), sep));
            }

            double[] switchProbabilities = info.getSwitchProbabilities(i);
            if (switchProbabilities != null) {
                buf.append(String.format("switchProbabilities@%d:", i));
                for (int j = 0; j < switchProbabilities.length; j++) {
                    buf.append(String.format(" %.3f", switchProbabilities[j]));
                }
                buf.append(sep);
            }

            if (info.getExceptionSeen(i) != ExceptionSeen.FALSE) {
                buf.append(String.format("exceptionSeen@%d: %s%s", i, info.getExceptionSeen(i).name(), sep));
            }

            JavaTypeProfile typeProfile = info.getTypeProfile(i);
            if (typeProfile != null) {
                ProfiledType[] ptypes = typeProfile.getTypes();
                if (ptypes != null) {
                    buf.append(String.format("types@%d:", i));
                    for (int j = 0; j < ptypes.length; j++) {
                        ProfiledType ptype = ptypes[j];
                        buf.append(String.format(" %.3f (%s)%s", ptype.probability, ptype.type, sep));
                    }
                    buf.append(String.format(" %.3f <not recorded>%s", typeProfile.getNotRecordedProbability(), sep));
                }
            }
        }

        boolean firstDeoptReason = true;
        for (DeoptimizationReason reason: DeoptimizationReason.values()) {
            int count = info.getDeoptimizationCount(reason);
            if (count > 0) {
                if (firstDeoptReason) {
                    buf.append("deoptimization history").append(sep);
                    firstDeoptReason = false;
                }
                buf.append(String.format(" %s: %d%s", reason.name(), count, sep));
            }
        }
        if (buf.length() == 0) {
            return "";
        }
        String s = buf.toString();
        assert s.endsWith(sep);
        return s.substring(0, s.length() - sep.length());
    }
}
